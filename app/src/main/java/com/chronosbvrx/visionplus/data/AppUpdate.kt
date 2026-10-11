package com.chronosbvrx.visionplus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private const val RELEASES_PAGE = "https://github.com/ChronosBVRX/Vision-/releases/latest"
    private const val RECHECK_MINUTES = 10

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
        // Evita bucle: si las versiones son iguales o la remota es más vieja, no hay actualización.
        // También evita intentar instalar debug sobre release (firma distinta).
        if (remote <= current) return false
        // Heurística: si el APK del release es debug (no firmado de release), no lo ofrezcas.
        val apkUrl = release.apkUrl ?: ""
        if (apkUrl.contains("-debug", ignoreCase = true) || apkUrl.contains("-unsigned", ignoreCase = true)) {
            return false
        }
        return true
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
                val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "visionplus/${currentVersion(context)} (android)")
                }
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) error("GitHub respondió $code")
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(body)
                    val release = Release(
                        version = json.optString("tag_name").removePrefix("v").removePrefix("V").ifBlank { json.optString("name") },
                        url = json.optString("html_url").ifBlank { RELEASES_PAGE },
                        apkUrl = apkAsset(json),
                        notes = json.optString("body").takeIf { it.isNotBlank() },
                    )
                    if (release.version.isBlank()) error("la respuesta no trae versión")
                    latest = release
                    lastCheckAt = nowMinutes()
                    release
                } finally {
                    connection.disconnect()
                }
            } catch (fail: Exception) {
                failed = true
                null
            } finally {
                checking = false
            }
        }
    }

    private fun nowMinutes(): Long = System.currentTimeMillis() / 60_000

    /** The release's .apk asset when it carries one, else null (then only the page is offered). */
    private fun apkAsset(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val link = asset.optString("browser_download_url")
            if (link.isNotBlank() && name.endsWith(".apk", ignoreCase = true)) return link
        }
        return null
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
