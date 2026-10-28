package com.chronosbvrx.visionplus.data.adb

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.home.contract.HomeContract
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Un APK listo para instalar en una tele, con la versión leída del propio archivo. */
internal data class Apk(
    val file: File,
    val versionName: String,
    val versionCode: Long,
    /** `true` si vino del release; `false` si es el respaldo incluido en la app. */
    val fromRelease: Boolean,
)

internal sealed interface ApkState {
    data object Idle : ApkState
    data object Looking : ApkState
    data class Downloading(val percent: Int) : ApkState
    data class Ready(val versionName: String, val versionCode: Long, val fromRelease: Boolean) : ApkState
    data class Failed(val message: String) : ApkState
}

/**
 * Una descarga del release oficial con respaldo local.
 *
 * Orden: 1) la copia descargada si sigue fresca, 2) una descarga nueva del `releases/latest`,
 * 3) la copia vieja que ya tuviera el teléfono y 4) el APK incluido en la app (si existe). Así
 * la tele recibe la última versión publicada sin que nadie baje nada a mano, y sin red el flujo
 * sigue funcionando con lo que haya.
 *
 * El archivo vive en `filesDir` (nunca en caché: el sistema puede borrarla justo antes de
 * instalar) y se escribe a un `.part` que solo reemplaza la copia buena al terminar.
 */
internal class ReleaseApkStore(
    private val asset: String,
    private val folder: String,
    private val minBytes: Long,
    private val userAgent: String,
    private val bundledAsset: String? = null,
) {

    var state: ApkState by mutableStateOf(ApkState.Idle)
        private set

    fun reset() {
        state = ApkState.Idle
    }

    /** Lo que ya está en el teléfono, sin tocar la red. */
    suspend fun cached(context: Context): Apk? = withContext(Dispatchers.IO) {
        info(context, target(context), fromRelease = true)
    }

    /**
     * El APK a instalar: descarga la última versión si la copia guardada venció y, si no hay red,
     * cae a lo que ya hubiera o al respaldo de la app. Devuelve `null` si no hay nada usable.
     */
    suspend fun resolve(context: Context): Apk? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val target = target(app)
        val previous = info(app, target, fromRelease = true)
        if (previous != null && TvLauncherPackage.isFresh(target.lastModified(), System.currentTimeMillis())) {
            state = ready(previous)
            return@withContext previous
        }

        state = ApkState.Looking
        val downloaded = runCatching { download(app, target) }
            .getOrNull()
            ?.let { info(app, it, fromRelease = true) }
        if (downloaded != null) {
            state = ready(downloaded)
            return@withContext downloaded
        }
        // El release no respondió: sirve la copia anterior si existe…
        if (previous != null) {
            state = ready(previous)
            return@withContext previous
        }
        // …y si no, el APK que viaja dentro de la app.
        val bundled = runCatching { bundled(app) }
            .getOrNull()
            ?.let { info(app, it, fromRelease = false) }
        state = if (bundled != null) {
            ready(bundled)
        } else {
            ApkState.Failed("No hay $asset disponible")
        }
        bundled
    }

    // --- interno ---

    private fun ready(apk: Apk) = ApkState.Ready(apk.versionName, apk.versionCode, apk.fromRelease)

    private fun target(context: Context): File =
        File(File(context.filesDir, folder).apply { mkdirs() }, asset)

    private fun info(context: Context, file: File, fromRelease: Boolean): Apk? {
        if (!file.isFile || file.length() <= minBytes) return null
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        return Apk(
            file = file,
            versionName = archive.versionName.orEmpty(),
            versionCode = code,
            fromRelease = fromRelease,
        )
    }

    /** Copia el APK de respaldo a disco una vez por versión de la app (para leer su versión). */
    private fun bundled(context: Context): File? {
        val name = bundledAsset ?: return null
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val target = File(dir, "${name.substringBeforeLast('.')}_bundled_${BuildConfig.VERSION_CODE}.apk")
        if (target.isFile && target.length() > minBytes) return target
        context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun download(context: Context, target: File): File? {
        val part = File(target.parentFile, "${target.name}.part")
        runCatching { part.delete() }
        val connection = (URL(TvLauncherPackage.releaseUrl(asset)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            // El APK de Vision+ pesa ~82 MB: un Wi-Fi lento no puede cortar la descarga a los 45 s.
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val total = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = input.read(buffer)
                    var done = 0L
                    var last = -1
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != last) {
                                last = percent
                                state = ApkState.Downloading(percent)
                            }
                        }
                        read = input.read(buffer)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (part.length() <= minBytes) {
            runCatching { part.delete() }
            return null
        }
        runCatching { target.delete() }
        if (!part.renameTo(target)) {
            runCatching { part.delete() }
            return null
        }
        return target
    }
}

/** El launcher: `VisionPlusHome.apk` (1.5 MB) con respaldo dentro de la app. */
internal object TvLauncherPackage {

    /** El APK del launcher que viaja dentro de la app (respaldo sin red). */
    const val BUNDLED = "vision_home.apk"

    /** Seis horas: si el release cambia, se recoge solo en la siguiente instalación. */
    internal const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

    private val store = ReleaseApkStore(
        asset = HomeContract.LAUNCHER_ASSET,
        folder = "tv_launcher",
        minBytes = 500_000L,
        userAgent = "visionplus-tv-installer/${BuildConfig.VERSION_NAME}",
        bundledAsset = BUNDLED,
    )

    val state: ApkState get() = store.state

    fun reset() = store.reset()

    suspend fun cached(context: Context): Apk? = store.cached(context)

    suspend fun resolve(context: Context): Apk? = store.resolve(context)

    // --- puro (con tests) ---

    /** Una copia guardada vale mientras no pase el TTL y el reloj no se haya ido para atrás. */
    internal fun isFresh(savedAtMs: Long, nowMs: Long, ttlMs: Long = CACHE_TTL_MS): Boolean =
        savedAtMs > 0 && nowMs - savedAtMs in 0 until ttlMs

    /** ¿La versión de `candidate` es más nueva que la que ya tiene la tele? Puro y con tests. */
    internal fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(value: String) = value.trim()
            .removePrefix("v")
            .substringBefore('-')
            .substringBefore(' ')
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        val left = parts(candidate)
        val right = parts(installed)
        if (left.isEmpty()) return false
        if (right.isEmpty()) return true
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** «1.3.1 (vc24)» para la interfaz. */
    internal fun versionLine(versionName: String, versionCode: Long): String {
        val name = versionName.trim().ifBlank { "?" }
        return if (versionCode > 0) "$name (vc$versionCode)" else name
    }

    internal fun releaseUrl(asset: String): String =
        "https://github.com/ChronosBVRX/Vision-/releases/latest/download/$asset"
}

/**
 * La app: `VisionPlus.apk` (~82 MB). Solo se baja si de verdad hace falta, porque para saber si
 * la tele está al día primero se lee el `version.json` del mismo release (unos cientos de bytes).
 */
internal object TvAppPackage {

    private const val VERSION_URL =
        "https://github.com/ChronosBVRX/Vision-/releases/latest/download/version.json"
    private const val PREFS = "vision_tv_app"
    private const val KEY_VERSION = "release_version"
    private const val KEY_AT = "release_at"
    private const val VERSION_TTL_MS = 60L * 60 * 1000

    private val store = ReleaseApkStore(
        asset = HomeContract.APP_ASSET,
        folder = "tv_app",
        minBytes = 20_000_000L,
        userAgent = "visionplus-tv-installer/${BuildConfig.VERSION_NAME}",
    )

    val state: ApkState get() = store.state

    fun reset() = store.reset()

    suspend fun cached(context: Context): Apk? = store.cached(context)

    suspend fun resolve(context: Context): Apk? = store.resolve(context)

    /** La versión de Vision+ del último release («2.5.171»), cacheada una hora. */
    suspend fun latestVersion(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val saved = prefs.getString(KEY_VERSION, null).orEmpty()
        val savedAt = prefs.getLong(KEY_AT, 0L)
        if (saved.isNotBlank() && TvLauncherPackage.isFresh(savedAt, now, VERSION_TTL_MS)) {
            return@withContext saved
        }
        val fresh = runCatching { fetchVersion() }.getOrNull()
        if (!fresh.isNullOrBlank()) {
            prefs.edit().putString(KEY_VERSION, fresh).putLong(KEY_AT, now).apply()
            return@withContext fresh
        }
        saved.takeIf { it.isNotBlank() }
    }

    private fun fetchVersion(): String? {
        val connection = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "visionplus-tv-installer/${BuildConfig.VERSION_NAME}")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).optString("version")
                .trim()
                .removePrefix("v")
                .takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }
}
