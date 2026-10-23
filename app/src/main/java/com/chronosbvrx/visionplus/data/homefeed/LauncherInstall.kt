package com.chronosbvrx.visionplus.data.homefeed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.home.contract.HomeContract
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Instala y abre **Vision+ Home**, el launcher (APK aparte) desde la propia tele.
 *
 * El launcher se publica en el MISMO release que Vision+ (`VisionPlusHome.apk`), así que no
 * hay tienda ni enlace que copiar: se descarga aquí y el instalador del sistema hace el resto.
 * Después se abre el selector de Inicio del sistema para que la persona lo elija.
 */
object LauncherInstall {
    private const val RELEASES_URL = "https://api.github.com/repos/ChronosBVRX/Vision-/releases/latest"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    data class Status(
        val installed: Boolean = false,
        val versionCode: Long = 0L,
        val versionName: String = "",
    )

    /** Lo observa Ajustes para pintar el botón y el avance. */
    var state: State by mutableStateOf(State.Idle)
        private set

    fun status(context: Context): Status = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(HomeContract.LAUNCHER_PACKAGE, 0)
        Status(
            installed = true,
            versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            versionName = info.versionName.orEmpty(),
        )
    }.getOrDefault(Status())

    /** Descarga el APK del último release y abre el instalador del sistema. */
    suspend fun installLatest(context: Context) {
        val app = context.applicationContext
        state = State.Checking
        try {
            val url = withContext(Dispatchers.IO) { assetUrl() }
            if (url.isNullOrBlank()) {
                state = State.Failed("El release no trae ${HomeContract.LAUNCHER_ASSET}")
                return
            }
            val file = withContext(Dispatchers.IO) { download(app, url) }
            if (file == null) {
                state = State.Failed("No se pudo descargar el launcher")
                return
            }
            val opened = installDownloaded(app, file)
            state = if (opened) State.Ready else State.Failed("No se pudo abrir el instalador")
        } catch (t: Throwable) {
            state = State.Failed(t.message ?: "Error de red")
        }
    }

    /** El selector de Inicio del sistema: aquí se elige Vision+ Home como pantalla de inicio. */
    fun openHomeSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
        val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /** Abre el launcher instalado (sin cambiar el Inicio del equipo). */
    fun openLauncher(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(HomeContract.LAUNCHER_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun resetState() {
        state = State.Idle
    }

    // --- Red ---

    /** El asset del launcher en el último release (el mismo tag que la OTA de Vision+). */
    private fun assetUrl(): String? {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "visionplus-home-installer")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val assets = JSONObject(body).optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").equals(HomeContract.LAUNCHER_ASSET, ignoreCase = true)) {
                    return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                }
            }
            // Respaldo: cualquier APK que no sea el de Vision+.
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val link = asset.optString("browser_download_url")
                if (link.isNotBlank() && name.endsWith(".apk", true) && !name.equals("VisionPlus.apk", true)) {
                    return link
                }
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(context: Context, link: String): File? = runCatching {
        val dir = File(context.cacheDir, "updates_home").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, HomeContract.LAUNCHER_ASSET)
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "visionplus-home-installer")
        }
        try {
            connection.connect()
            val total = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
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
                                state = State.Downloading(percent)
                            }
                        }
                        read = input.read(buffer)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        target.takeIf { it.length() > 1_000_000 }
    }.getOrNull()

    private fun installDownloaded(context: Context, file: File): Boolean = runCatching {
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
