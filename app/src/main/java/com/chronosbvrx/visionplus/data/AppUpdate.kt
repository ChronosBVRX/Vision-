package com.chronosbvrx.visionplus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vision+ distributes its own updates from its releases at its repository
 * (ChronosBVRX/Vision- on GitHub). This check only reads the newest release of that repository:
 * no mirrors, no stores, no third-party channel.
 */
object AppUpdate {
    private const val RELEASES_URL = "https://api.github.com/repos/ChronosBVRX/Vision-/releases/latest"
    /** Canal de pruebas: release marcada como *pre-release* con tag fijo `beta`. */
    private const val RELEASES_BETA_URL = "https://api.github.com/repos/ChronosBVRX/Vision-/releases/tags/beta"
    private const val RELEASES_PAGE = "https://github.com/ChronosBVRX/Vision-/releases/latest"

    /**
     * `version.json` que el publicador mete en CADA release (estable y beta). Es una descarga
     * normal del release (sin la API de GitHub): sin límite por IP y sin depender de qué asset
     * venga primero. Si un release viejo no lo trae, se cae a la API.
     */
    private const val MANIFEST_STABLE =
        "https://github.com/ChronosBVRX/Vision-/releases/latest/download/version.json"
    private const val MANIFEST_BETA =
        "https://github.com/ChronosBVRX/Vision-/releases/download/beta/version.json"
    private const val APK_STABLE =
        "https://github.com/ChronosBVRX/Vision-/releases/latest/download/VisionPlus.apk"
    private const val APK_BETA =
        "https://github.com/ChronosBVRX/Vision-/releases/download/beta/VisionPlus.apk"
    private const val RECHECK_MINUTES = 10

    private const val PREFS = "vision_updates"
    private const val K_CHANNEL = "channel"
    internal const val CHANNEL_STABLE = "stable"
    internal const val CHANNEL_BETA = "beta"

    /** Canal efectivo de este equipo: preferencia local, o servidor (`beta_device_ids`). */
    fun channel(context: Context): String {
        val local = prefs(context).getString(K_CHANNEL, null)?.trim().orEmpty()
        if (local == CHANNEL_BETA || local == CHANNEL_STABLE) return local
        val id = com.chronosbvrx.visionplus.data.diag.Diagnostics.deviceId(context)
        val testers = RemoteConfig.stringListFlag(context, "beta_device_ids")
        return if (id.isNotEmpty() && testers.any { it.equals(id, ignoreCase = true) }) CHANNEL_BETA else CHANNEL_STABLE
    }

    fun setChannel(context: Context, channel: String) {
        prefs(context).edit()
            .putString(K_CHANNEL, if (channel == CHANNEL_BETA) CHANNEL_BETA else CHANNEL_STABLE)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Qué release ofrecer (puro). En el canal de pruebas se comparan beta y oficial y gana la
     * más nueva: cuando la beta se convierte en oficial, los equipos de prueba la reciben igual.
     */
    internal fun pickRelease(stable: Release?, beta: Release?, channel: String): Release? {
        if (channel != CHANNEL_BETA) return stable
        if (stable == null) return beta
        if (beta == null) return stable
        val stableNumber = parseReleaseNumber(stable.version)
        val betaNumber = parseReleaseNumber(beta.version)
        return if (betaNumber >= stableNumber) beta else stable
    }

    /** The newest release of the repository, after the person looks for it. */
    var latest by mutableStateOf<Release?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var lastCheckAt = 0L

    /** The version this build carries ("2.0.0"). */
    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** The symbol used to compare releases numerically ("v2.1.0" -> 02010000-ish numerals). */
    private fun parseReleaseNumber(version: String): Int {
        val digits = version.trimStart('v', 'V').split('.').mapNotNull { seg ->
            val digitsPortion = seg.takeWhile { it.isDigit() }
            digitsPortion.toIntOrNull()
        }
        return when (digits.size) {
            0 -> 0
            1 -> digits[0] * 1_000_000
            2 -> digits[0] * 1_000_000 + digits[1] * 1_000
            else -> digits[0] * 1_000_000 + digits[1] * 1_000 + digits[2]
        }
    }

    /** True when the repository has a release newer than what this build carries. */
    fun pending(context: Context): Boolean {
        val release = latest ?: return false
        val current = parseReleaseNumber(currentVersion(context))
        val remote = parseReleaseNumber(release.version)
        // Heurística: si el APK del release es debug (no firmado de release), no lo ofrezcas.
        val apkUrl = release.apkUrl ?: ""
        val apkInstallable = !(apkUrl.contains("-debug", ignoreCase = true) || apkUrl.contains("-unsigned", ignoreCase = true))
        return updateOffer(
            forced = forced(context),
            paused = paused(context),
            remoteNewer = remote > current,
            apkInstallable = apkInstallable,
        )
    }

    /**
     * La versión de una release: del tag cuando lo lleva (`v2.5.140`) y, si el tag es fijo
     * (`beta`), del título (`Vision+ 2.5.141 (pruebas)`). Puro y testeable.
     */
    internal fun releaseVersion(tag: String, name: String): String {
        val clean = tag.trim().removePrefix("v").removePrefix("V")
        if (clean.isNotEmpty() && clean.first().isDigit() && clean.none { it.isLetter() }) return clean
        val match = Regex("""\d+(?:\.\d+)+""").find(name) ?: return ""
        return match.value
    }

    /** El versionCode de ESTE build (contra él se mide `min_version_code`). */
    fun currentVersionCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    /** ¿La actualización está pausada? Kill-switch remoto `ota_paused` (build malo: se frena la oferta). */
    fun paused(context: Context): Boolean = RemoteConfig.flag(context, "ota_paused", false)

    /**
     * ¿Este build quedó por debajo de la versión mínima exigida? Kill-switch remoto
     * `min_version_code`: si el operador lo sube, los builds viejos se fuerzan a actualizar
     * sin publicar nada.
     */
    fun forced(context: Context): Boolean =
        shouldForce(RemoteConfig.intFlag(context, "min_version_code", 0), currentVersionCode(context).toInt())

    internal fun shouldForce(minVersionCode: Int, ownVersionCode: Int): Boolean =
        minVersionCode > 0 && ownVersionCode < minVersionCode

    /**
     * La política de la oferta de actualización (pura, probada): la forzada SIEMPRE gana
     * (aunque esté pausada o la remota no se vea más nueva), la pausa bloquea las ofertas
     * normales, y lo normal exige remota más nueva + APK instalable.
     */
    internal fun updateOffer(forced: Boolean, paused: Boolean, remoteNewer: Boolean, apkInstallable: Boolean): Boolean =
        when {
            forced -> true
            paused -> false
            else -> remoteNewer && apkInstallable
        }

    /**
     * Asks the one source's repository for its latest release. Quiet callers (Home, every launch)
     * reuse the last answer for [RECHECK_MINUTES] minutes; `force` asks again no matter what.
     */
    suspend fun check(context: Context, force: Boolean = false): Release? {
        if (checking) return latest
        val fresh = latest != null && nowMinutes() - lastCheckAt < RECHECK_MINUTES
        if (fresh && !force) return latest
        checking = true
        failed = false
        return withContext(Dispatchers.IO) {
            try {
                val channel = channel(context)
                val stable = manifest(context, MANIFEST_STABLE, APK_STABLE) ?: fetch(context, RELEASES_URL)
                // En pruebas se mira también la beta (pre-release) y gana la más nueva.
                val beta = if (channel == CHANNEL_BETA) {
                    manifest(context, MANIFEST_BETA, APK_BETA) ?: fetch(context, RELEASES_BETA_URL)
                } else {
                    null
                }
                val chosen = pickRelease(stable, beta, channel)
                if (chosen != null) {
                    latest = chosen
                    lastCheckAt = nowMinutes()
                } else {
                    failed = true
                }
                chosen
            } finally {
                checking = false
            }
        }
    }

    /** Una descarga de texto con los timeouts de siempre; null si no responde. */
    private fun downloadText(context: Context, url: String): String? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "visionplus/${currentVersion(context)} (android)")
        }
        try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    } catch (fail: Exception) {
        null
    }

    /** El `version.json` del release (canal estable o beta). */
    private fun manifest(context: Context, manifestUrl: String, apkUrl: String): Release? {
        val text = downloadText(context, manifestUrl) ?: return null
        return try {
            val json = JSONObject(text)
            val version = json.optString("version").ifBlank { releaseVersion(json.optString("tag_name"), "") }
            if (version.isBlank()) null else Release(
                version = version,
                url = RELEASES_PAGE,
                apkUrl = apkUrl,
                notes = json.optString("notes").takeIf { it.isNotBlank() },
            )
        } catch (fail: Exception) {
            null
        }
    }

    /** Una release del repositorio; null si GitHub no responde o no trae versión. */
    private fun fetch(context: Context, url: String): Release? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "visionplus/${currentVersion(context)} (android)")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                null
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                Release(
                    version = releaseVersion(json.optString("tag_name"), json.optString("name")),
                    url = json.optString("html_url").ifBlank { RELEASES_PAGE },
                    apkUrl = apkAsset(json),
                    notes = json.optString("body").takeIf { it.isNotBlank() },
                ).takeIf { it.version.isNotBlank() }
            }
        } finally {
            connection.disconnect()
        }
    } catch (fail: Exception) {
        null
    }

    private fun nowMinutes(): Long = System.currentTimeMillis() / 60_000

    /** The release's .apk asset when it carries one, else null (then only the page is offered). */
    private fun apkAsset(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        var fallback: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val link = asset.optString("browser_download_url")
            if (link.isBlank() || !name.endsWith(".apk", ignoreCase = true)) continue
            // La app es SIEMPRE VisionPlus.apk: los releases también traen VisionPlusHome.apk.
            if (name.equals("VisionPlus.apk", ignoreCase = true)) return link
            if (fallback == null) fallback = link
        }
        return fallback
    }

    /** Opens the latest release (its APK asset when there is one, else its page). */
    fun openDownload(context: Context, release: Release?): Boolean {
        val link = release?.apkUrl ?: release?.url ?: RELEASES_PAGE
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Downloads the release's .apk inside the app, reporting 0..100 progress. The file lands in
     * the private cache and is handed to the system installer afterwards: the person never has
     * to leave Vision+ to get the new version.
     */
    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): java.io.File? =
        withContext(Dispatchers.IO) {
            val link = release.apkUrl ?: return@withContext null
            runCatching {
                val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = java.io.File(dir, "VisionPlus-${release.version}.apk")
                val connection = (URL(link).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "visionplus/${currentVersion(context)} (android)")
                }
                try {
                    connection.connect()
                    val total = connection.contentLength.toLong()
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read = input.read(buffer)
                            var done = 0L
                            var lastPercent = -1
                            while (read >= 0) {
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                                read = input.read(buffer)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (target.length() > 1_000_000) target else null
            }.getOrNull()
        }

    /** Hands the downloaded file to the system installer, right over the app. */
    fun installDownloaded(context: Context, file: java.io.File): Boolean = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.updates", file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

/** One release of the one source: its version, where to get it, and its notes. */
data class Release(
    val version: String,
    val url: String,
    val apkUrl: String? = null,
    val notes: String? = null,
)
