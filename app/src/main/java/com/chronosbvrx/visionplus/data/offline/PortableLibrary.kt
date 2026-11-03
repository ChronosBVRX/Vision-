package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.content.SharedPreferences
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import org.json.JSONObject

/**
 * Biblioteca portátil de Vision+.
 *
 * Una USB puede tener una carpeta `vision_vault/portable` con:
 *  - `library.vkey`: llave de biblioteca envuelta con el PIN y con la llave del chip de origen.
 *  - `<id>.pvpack`: video cifrado con llave propia.
 *  - `<id>.pvkey`: llave de contenido envuelta con la llave de biblioteca.
 *  - `<id>.pvmeta`: metadatos para que otra TV muestre el título sin internet.
 *  - `<id>.poster.jpg`: póster copiado para el modo offline.
 *
 * El PIN es de 4 dígitos (decisión de producto). La protección real contra un atacante
 * con la USB es limitada; el objetivo es acceso casual y que el video nunca viaje en claro.
 */
object PortableLibrary {

    const val PORTABLE_DIR = "portable"
    const val LIBRARY_FILE = "library.vkey"
    private const val PREFS = "vision_portable"
    private const val KEY_PINS = "pins"
    private const val KEY_LIBRARY_KEYS = "library_keys"
    private const val KEY_FAIL_PREFIX = "fail_"
    private const val KEY_LOCK_PREFIX = "lock_until_"
    private const val MAX_ATTEMPTS = 5
    private const val MAX_POSTER_BYTES = 8L * 1024L * 1024L
    private const val LIBRARY_VERSION = 2
    private const val META_VERSION = 2

    private val lock = Any()
    private val random = SecureRandom()

    data class PortableItem(
        val libraryId: String,
        val downloadId: String,
        val registryId: String,
        val title: String,
        val subtitle: String,
        val posterPath: String,
        val pvpackPath: String,
        val pvkeyPath: String,
        val metaPath: String,
        val bytes: Long,
        val durationMs: Long,
        val quality: String,
        val isSeries: Boolean,
        val seriesId: String,
        val seasonIndex: Int,
        val episodeIndex: Int,
        val episodeName: String,
        val volumeId: String,
        val volumeLabel: String,
        val unlocked: Boolean,
    )

    data class LibraryInfo(
        val libraryId: String,
        val pin: String,
        val libraryKey: SecretKey,
        val volume: StorageVolumeInfo,
        val directory: File,
    )

    sealed interface UnlockResult {
        data class Unlocked(val imported: Int) : UnlockResult
        data class Wrong(val attemptsLeft: Int, val lockSeconds: Long) : UnlockResult
        data class Locked(val seconds: Long) : UnlockResult
        data class Error(val message: String) : UnlockResult
    }

    /** Activa/desactiva la función sin publicar otra app (kill-switch remoto). */
    fun isEnabled(context: Context): Boolean =
        RemoteConfig.flag(context, "vault_portable", true)

    fun portableDir(volume: StorageVolumeInfo): File =
        File(volume.vaultDir, PORTABLE_DIR).apply { mkdirs() }

    fun libraryFile(volume: StorageVolumeInfo): File =
        File(portableDir(volume), LIBRARY_FILE)

    fun pvKeyFile(pvpack: File): File = File(pvpack.parentFile, "${pvpack.name.removeSuffix(".pvpack")}.pvkey")

    fun pvMetaFile(pvpack: File): File = File(pvpack.parentFile, "${pvpack.name.removeSuffix(".pvpack")}.pvmeta")

    fun posterFile(pvpack: File): File = File(pvpack.parentFile, "${pvpack.name.removeSuffix(".pvpack")}.poster.jpg")

    fun isPortableDownload(item: OfflineDownload): Boolean =
        item.vaultFilePath.endsWith(".pvpack", ignoreCase = true)

    fun isPortableReady(item: OfflineDownload): Boolean {
        if (!isPortableDownload(item)) return false
        val pack = File(item.vaultFilePath)
        return pack.exists() && pvKeyFile(pack).exists() && pvMetaFile(pack).exists()
    }

    /** La TV que descarga: crea o recupera la biblioteca de esa USB. */
    fun ensureLibrary(context: Context, volume: StorageVolumeInfo): LibraryInfo? =
        runCatching { ensureLibraryInternal(context, volume) }.getOrNull()

    private fun ensureLibraryInternal(context: Context, volume: StorageVolumeInfo): LibraryInfo? {
        val file = libraryFile(volume)
        if (file.exists()) {
            val obj = readJson(file) ?: return null
            val libraryId = obj.optString("libraryId")
            if (libraryId.isBlank()) return null
            val deviceBlob = PortableVaultCrypto.fromBase64(obj.optString("device"))
                ?: return null
            val rawLibraryKey = PortableVaultCrypto.unwrapWithDeviceKey(deviceBlob)
                ?: return null
            if (rawLibraryKey.size != PortableVaultCrypto.LIBRARY_KEY_LEN) return null
            val libraryKey = SecretKeySpec(rawLibraryKey, "AES")
            var pin = getPin(context, libraryId)
            if (pin.isNullOrBlank()) {
                pin = PortableVaultCrypto.generatePin(random)
                obj.put("pin", PortableVaultCrypto.wrapLibraryForPin(libraryKey, pin))
                writeJsonAtomic(file, obj)
            }
            putPin(context, libraryId, pin)
            return LibraryInfo(libraryId, pin, libraryKey, volume, portableDir(volume))
        }

        val libraryKey = SecretKeySpec(PortableVaultCrypto.randomKey(PortableVaultCrypto.LIBRARY_KEY_LEN), "AES")
        val libraryId = UUID.randomUUID().toString()
        val pin = PortableVaultCrypto.generatePin(random)
        val obj = JSONObject()
            .put("v", LIBRARY_VERSION)
            .put("libraryId", libraryId)
            .put("createdAt", System.currentTimeMillis())
            .put("device", PortableVaultCrypto.base64(PortableVaultCrypto.wrapWithDeviceKey(libraryKey.encoded)))
            .put("pin", PortableVaultCrypto.wrapLibraryForPin(libraryKey, pin))
        if (!writeJsonAtomic(file, obj)) return null
        putPin(context, libraryId, pin)
        return LibraryInfo(libraryId, pin, libraryKey, volume, portableDir(volume))
    }

    /** La TV de origen recupera el PIN (y, si hace falta, lo regenera desde su chip). */
    fun pinForLibrary(context: Context, libraryId: String): String? {
        getPin(context, libraryId)?.let { return it }
        for (volume in OfflineStoragePolicy.getAvailableVolumes(context)) {
            val file = libraryFile(volume)
            if (!file.exists()) continue
            val obj = readJson(file) ?: continue
            if (obj.optString("libraryId") != libraryId) continue
            return ensureLibrary(context, volume)?.pin
        }
        return null
    }

    /** Busca la biblioteca de una descarga portátil y devuelve su PIN para mostrarlo. */
    fun pinForDownload(context: Context, item: OfflineDownload): String? {
        val libraryId = item.portableLibraryId.ifBlank {
            readJson(pvMetaFile(File(item.vaultFilePath)))?.optString("libraryId").orEmpty()
        }
        if (libraryId.isBlank()) return null
        return pinForLibrary(context, libraryId)
    }

    /**
     * La TV de destino: desbloquea la biblioteca con el PIN y guarda la llave de
     * biblioteca cifrada con el chip de ESTA TV. El PIN no se guarda.
     */
    fun unlock(
        context: Context,
        item: PortableItem,
        pin: String,
        volumeOverride: StorageVolumeInfo? = null,
    ): UnlockResult = runCatching {
        unlockInternal(context, item, pin, volumeOverride)
    }.getOrElse { error ->
        UnlockResult.Error(error.message ?: "No se pudo validar el PIN")
    }

    private fun unlockInternal(
        context: Context,
        item: PortableItem,
        pin: String,
        volumeOverride: StorageVolumeInfo?,
    ): UnlockResult {
        val libraryId = item.libraryId
        val cleanPin = pin.filter { it.isDigit() }.take(4)
        if (cleanPin.length != 4) {
            return UnlockResult.Wrong(MAX_ATTEMPTS - currentFailures(context, libraryId), 0L)
        }

        val now = System.currentTimeMillis()
        val lockUntil = prefs(context).getLong(KEY_LOCK_PREFIX + libraryId, 0L)
        if (lockUntil > now) {
            return UnlockResult.Locked(((lockUntil - now) / 1000L).coerceAtLeast(1L))
        }

        val libraryFile = File(File(item.pvpackPath).parentFile, LIBRARY_FILE)
        val obj = readJson(libraryFile)
            ?: return UnlockResult.Error("No se encontró la biblioteca de esta USB")
        if (obj.optString("libraryId") != libraryId) {
            return UnlockResult.Error("La biblioteca de la USB no coincide")
        }
        val envelope = obj.optJSONObject("pin")
            ?: return UnlockResult.Error("Esta USB no tiene PIN configurado")
        val libraryKey = PortableVaultCrypto.unwrapLibraryFromPin(envelope, cleanPin)
        if (libraryKey == null) {
            return registerFailure(context, libraryId)
        }

        val saved = runCatching { putLibraryKey(context, libraryId, libraryKey) }.isSuccess
        if (!saved) {
            return UnlockResult.Error("No se pudo guardar la llave en esta TV")
        }
        clearFailures(context, libraryId)
        val items = volumeOverride?.let { scanVolume(context, it) } ?: scanVolumes(context)
        val imported = importUnlocked(context, items)
        return UnlockResult.Unlocked(imported)
    }

    /** Escanea todas las USB conectadas. No necesita internet. */
    fun scanVolumes(context: Context): List<PortableItem> {
        val result = ArrayList<PortableItem>()
        for (volume in OfflineStoragePolicy.getAvailableVolumes(context)) {
            result += scanVolume(context, volume)
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    /** Escanea un volumen concreto (inyectable en pruebas sin USB física). */
    internal fun scanVolume(context: Context, volume: StorageVolumeInfo): List<PortableItem> {
        val result = ArrayList<PortableItem>()
        val dir = portableDir(volume)
        val metas = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".pvmeta", ignoreCase = true)
        }.orEmpty()
        for (meta in metas) {
            val obj = readJson(meta) ?: continue
            val libraryId = obj.optString("libraryId")
            val downloadId = obj.optString("downloadId").ifBlank {
                meta.name.removeSuffix(".pvmeta")
            }
            if (libraryId.isBlank() || downloadId.isBlank()) continue
            val packName = obj.optString("pvpack").ifBlank { "$downloadId.pvpack" }
            val pack = safeChild(dir, packName) ?: continue
            if (!pack.exists() || pack.length() < MIN_VALID_VAULT_BYTES) continue
            val key = safeChild(dir, obj.optString("pvkey").ifBlank { "$downloadId.pvkey" })
            if (key == null || !key.exists()) continue
            val poster = obj.optString("poster").takeIf { it.isNotBlank() }
                ?.let { safeChild(dir, it) }
                ?.takeIf { it.exists() && it.length() > 0L }
            val registryId = portableRegistryId(libraryId, downloadId)
            val alreadyHere = OfflineRegistry.get(registryId, context) != null ||
                OfflineRegistry.getAll(context).any { it.vaultFilePath == pack.absolutePath }
            val unlocked = alreadyHere || hasLibraryKey(context, libraryId)
            result += PortableItem(
                libraryId = libraryId,
                downloadId = downloadId,
                registryId = registryId,
                title = obj.optString("title").ifBlank { downloadId },
                subtitle = obj.optString("subtitle"),
                posterPath = poster?.absolutePath.orEmpty(),
                pvpackPath = pack.absolutePath,
                pvkeyPath = key.absolutePath,
                metaPath = meta.absolutePath,
                bytes = obj.optLong("bytes", pack.length()),
                durationMs = obj.optLong("durationMs", 0L),
                quality = obj.optString("quality", "1080p"),
                isSeries = obj.optBoolean("isSeries", false),
                seriesId = obj.optString("seriesId"),
                seasonIndex = obj.optInt("seasonIndex", 0),
                episodeIndex = obj.optInt("episodeIndex", 0),
                episodeName = obj.optString("episodeName"),
                volumeId = volume.id,
                volumeLabel = volume.label,
                unlocked = unlocked,
            )
        }
        return result
    }

    /**
     * Mete en [OfflineRegistry] los títulos ya desbloqueados para que el reproductor
     * actual los trate como descargas normales. No duplica los de la TV de origen.
     */
    fun importUnlocked(context: Context, items: List<PortableItem> = scanVolumes(context)): Int {
        var imported = 0
        val all = OfflineRegistry.getAll(context)
        for (item in items) {
            if (!item.unlocked) continue
            if (all.any { it.vaultFilePath == item.pvpackPath }) continue
            if (OfflineRegistry.get(item.registryId, context) != null) continue
            val download = OfflineDownload(
                id = item.registryId,
                contentId = item.downloadId,
                title = item.title,
                subtitle = item.subtitle,
                posterUrl = item.posterPath.takeIf { it.isNotBlank() }
                    ?.let { File(it).toURI().toString() }
                    .orEmpty(),
                videoUrl = "vault:${item.registryId}",
                durationMs = item.durationMs,
                quality = item.quality,
                state = DownloadState.COMPLETED,
                bytesDownloaded = item.bytes,
                totalBytes = item.bytes.coerceAtLeast(1L),
                vaultFilePath = item.pvpackPath,
                storageLabel = item.volumeLabel,
                isRemovable = true,
                isSeries = item.isSeries,
                seriesId = item.seriesId,
                seasonIndex = item.seasonIndex,
                episodeIndex = item.episodeIndex,
                episodeName = item.episodeName,
                portable = true,
                portableLibraryId = item.libraryId,
            )
            OfflineRegistry.put(download, context)
            imported++
        }
        return imported
    }

    /**
     * Se llama cuando una descarga portátil termina. Escribe `.pvkey` y `.pvmeta`,
     * copia el póster y actualiza el registro. Si algo falla, la descarga sigue siendo
     * reproducible en la TV de origen (su llave va en la cabecera).
     */
    fun finalizeDownload(
        context: Context,
        item: OfflineDownload,
        volumeOverride: StorageVolumeInfo? = null,
    ): Boolean {
        if (!isEnabled(context) || !isPortableDownload(item)) return false
        val pack = File(item.vaultFilePath)
        if (!pack.exists() || pack.length() < MIN_VALID_VAULT_BYTES) return false

        val volume = volumeOverride ?: volumeFor(context, pack) ?: return false
        val library = ensureLibrary(context, volume) ?: return false
        val header = runCatching {
            java.io.RandomAccessFile(pack, "r").use { PortableVaultCrypto.readHeader(it) }
        }.getOrNull() ?: return false
        val contentKey = PortableVaultCrypto.unwrapDeviceContentKey(header) ?: return false

        val dir = portableDir(volume)
        val keyFile = pvKeyFile(pack)
        val metaFile = pvMetaFile(pack)
        val keyJson = JSONObject()
            .put("v", META_VERSION)
            .put("libraryId", library.libraryId)
            .put("downloadId", item.id)
            .put("wrappedKey", PortableVaultCrypto.wrapWithLibraryKey(contentKey.encoded, library.libraryKey))
        if (!writeJsonAtomic(keyFile, keyJson)) return false

        val poster = posterFile(pack)
        val posterName = if (downloadPoster(item.posterUrl, poster)) poster.name else ""
        val metaJson = JSONObject()
            .put("v", META_VERSION)
            .put("libraryId", library.libraryId)
            .put("downloadId", item.id)
            .put("title", item.title)
            .put("subtitle", item.subtitle)
            .put("poster", posterName)
            .put("pvpack", pack.name)
            .put("pvkey", keyFile.name)
            .put("bytes", pack.length())
            .put("durationMs", item.durationMs)
            .put("quality", item.quality)
            .put("isSeries", item.isSeries)
            .put("seriesId", item.seriesId)
            .put("seasonIndex", item.seasonIndex)
            .put("episodeIndex", item.episodeIndex)
            .put("episodeName", item.episodeName)
            .put("createdAt", System.currentTimeMillis())
        if (!writeJsonAtomic(metaFile, metaJson)) return false

        val updated = item.copy(portable = true, portableLibraryId = library.libraryId)
        OfflineRegistry.put(updated, context)
        return true
    }

    /** Repara sidecars que hayan quedado pendientes (app cerrada justo al terminar). */
    fun finalizePending(context: Context) {
        if (!isEnabled(context)) return
        for (item in OfflineRegistry.getAll(context)) {
            if (item.state != DownloadState.COMPLETED) continue
            if (!item.portable || isPortableReady(item)) continue
            runCatching { finalizeDownload(context, item) }
        }
    }

    /** Borra los sidecars de una descarga portátil (el `.pvpack` lo borra el registro). */
    fun deleteSidecars(item: OfflineDownload) {
        if (!isPortableDownload(item)) return
        val pack = File(item.vaultFilePath)
        runCatching { pvKeyFile(pack).delete() }
        runCatching { pvMetaFile(pack).delete() }
        runCatching { posterFile(pack).delete() }
    }

    fun portableRegistryId(libraryId: String, downloadId: String): String =
        "pv:$libraryId:$downloadId"

    private fun volumeFor(context: Context, pack: File): StorageVolumeInfo? {
        val parent = pack.parentFile?.absolutePath.orEmpty()
        return OfflineStoragePolicy.getAvailableVolumes(context)
            .firstOrNull { parent.startsWith(it.vaultDir.absolutePath) }
    }

    private fun registerFailure(context: Context, libraryId: String): UnlockResult {
        val p = prefs(context)
        val failures = p.getInt(KEY_FAIL_PREFIX + libraryId, 0) + 1
        val lockIndex = max(0, failures - MAX_ATTEMPTS)
        val lockSeconds = when {
            failures < MAX_ATTEMPTS -> 0L
            lockIndex == 0 -> 30L
            lockIndex == 1 -> 120L
            lockIndex == 2 -> 600L
            else -> 1_800L
        }
        p.edit().putInt(KEY_FAIL_PREFIX + libraryId, failures).apply()
        if (lockSeconds > 0L) {
            p.edit().putLong(KEY_LOCK_PREFIX + libraryId, System.currentTimeMillis() + lockSeconds * 1000L).apply()
        }
        val left = (MAX_ATTEMPTS - failures).coerceAtLeast(0)
        return if (lockSeconds > 0L) UnlockResult.Wrong(0, lockSeconds)
        else UnlockResult.Wrong(left, 0L)
    }

    private fun currentFailures(context: Context, libraryId: String): Int =
        prefs(context).getInt(KEY_FAIL_PREFIX + libraryId, 0)

    private fun clearFailures(context: Context, libraryId: String) {
        prefs(context).edit()
            .remove(KEY_FAIL_PREFIX + libraryId)
            .remove(KEY_LOCK_PREFIX + libraryId)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun putPin(context: Context, libraryId: String, pin: String) {
        val encrypted = PortableVaultCrypto.wrapWithDeviceKey(pin.toByteArray(Charsets.UTF_8))
        val all = JSONObject(prefs(context).getString(KEY_PINS, "{}") ?: "{}")
        all.put(libraryId, PortableVaultCrypto.base64(encrypted))
        prefs(context).edit().putString(KEY_PINS, all.toString()).apply()
    }

    private fun getPin(context: Context, libraryId: String): String? {
        val all = runCatching {
            JSONObject(prefs(context).getString(KEY_PINS, "{}") ?: "{}")
        }.getOrNull() ?: return null
        val value = all.optString(libraryId)
        if (value.isBlank()) return null
        val blob = PortableVaultCrypto.fromBase64(value) ?: return null
        val raw = PortableVaultCrypto.unwrapWithDeviceKey(blob) ?: return null
        return raw.toString(Charsets.UTF_8).takeIf { it.length == 4 }
    }

    private fun putLibraryKey(context: Context, libraryId: String, key: SecretKey) {
        val encrypted = PortableVaultCrypto.wrapWithDeviceKey(key.encoded)
        val all = JSONObject(prefs(context).getString(KEY_LIBRARY_KEYS, "{}") ?: "{}")
        all.put(libraryId, PortableVaultCrypto.base64(encrypted))
        prefs(context).edit().putString(KEY_LIBRARY_KEYS, all.toString()).apply()
    }

    private fun hasLibraryKey(context: Context, libraryId: String): Boolean =
        getLibraryKey(context, libraryId) != null

    fun getLibraryKey(context: Context, libraryId: String): SecretKey? {
        val all = runCatching {
            JSONObject(prefs(context).getString(KEY_LIBRARY_KEYS, "{}") ?: "{}")
        }.getOrNull() ?: return null
        val value = all.optString(libraryId)
        if (value.isBlank()) return null
        val blob = PortableVaultCrypto.fromBase64(value) ?: return null
        val raw = PortableVaultCrypto.unwrapWithDeviceKey(blob) ?: return null
        if (raw.size != PortableVaultCrypto.LIBRARY_KEY_LEN) return null
        return SecretKeySpec(raw, "AES")
    }

    private fun safeChild(dir: File, name: String): File? {
        if (name.isBlank()) return null
        return runCatching {
            val child = File(dir, name).canonicalFile
            val root = dir.canonicalFile
            if (child.path.startsWith(root.path + File.separator)) child else null
        }.getOrNull()
    }

    private fun readJson(file: File): JSONObject? = runCatching {
        if (!file.exists() || file.length() <= 0L) return null
        JSONObject(file.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun writeJsonAtomic(file: File, obj: JSONObject): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(obj.toString(), Charsets.UTF_8)
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        true
    }.getOrDefault(false)

    private fun downloadPoster(url: String, destination: File): Boolean {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return false
        if (destination.exists() && destination.length() > 0L) return true
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Vision+/2.6")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return false
                val length = conn.contentLengthLong
                if (length > MAX_POSTER_BYTES) return false
                destination.parentFile?.mkdirs()
                conn.inputStream.use { input ->
                    FileOutputStream(destination).use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_POSTER_BYTES) {
                                destination.delete()
                                return false
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                }
                destination.exists() && destination.length() > 0L
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
}
