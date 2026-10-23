package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.home.BuildConfig
import com.chronosbvrx.visionplus.home.contract.HomeContract
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * El launcher se actualiza solo desde el MISMO release que Vision+ (`VisionPlusHome.apk`).
 *
 * No hay tienda: se descarga el asset, el instalador del sistema lo confirma y listo. Si el
 * release no trae el asset, se dice y no se toca nada.
 */
object HomeUpdate {
    private const val RELEASES_URL = "https://api.github.com/repos/ChronosBVRX/Vision-/releases/latest"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set

    val versionName: String get() = BuildConfig.VERSION_NAME

    /** Descarga el último `VisionPlusHome.apk` y abre el instalador. */
    suspend fun installLatest(context: Context) {
        val app = context.applicationContext
        state = State.Checking
        try {
            val link = withContext(Dispatchers.IO) { assetUrl() }
            if (link.isNullOrBlank()) {
                state = State.Failed("El release no trae ${HomeContract.LAUNCHER_ASSET}")
                return
            }
            val file = withContext(Dispatchers.IO) { download(app, link) }
            if (file == null) {
                state = State.Failed("No se pudo descargar la actualización")
                return
            }
            state = if (install(app, file)) State.Ready else State.Failed("No se pudo abrir el instalador")
        } catch (t: Throwable) {
            state = State.Failed(t.message ?: "Error de red")
        }
    }

    fun reset() {
        state = State.Idle
    }

    private fun assetUrl(): String? {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "visionplus-home/${BuildConfig.VERSION_NAME}")
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
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(context: Context, link: String): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, HomeContract.LAUNCHER_ASSET)
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "visionplus-home/${BuildConfig.VERSION_NAME}")
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

    private fun install(context: Context, file: File): Boolean = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
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
