package com.chronosbvrx.visionplus.home.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
 * Las dos descargas del launcher, desde el MISMO release de GitHub:
 * - [installLatest]: se actualiza a sí mismo (`VisionPlusHome.apk`).
 * - [installVisionPlus]: instala la app si el equipo solo tiene el launcher (`VisionPlus.apk`).
 *
 * No hay tienda: se baja el asset, el instalador del sistema lo confirma y listo. Cada una
 * tiene su propio estado para que el progreso de una no pise a la otra.
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

    /** Estado de la actualización del launcher. */
    var state: State by mutableStateOf(State.Idle)
        private set

    /** Estado de la instalación de Vision+ (cuando el launcher está solo). */
    var visionState: State by mutableStateOf(State.Idle)
        private set

    val versionName: String get() = BuildConfig.VERSION_NAME

    /**
     * Android 8+ exige que el usuario permita «instalar apps desconocidas» para ESTA app.
     * Sin esto el instalador se abre y se cierra con un aviso sin salida.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Abre la pantalla exacta de «instalar apps desconocidas» para Vision+ Home. */
    fun openInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 26) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    /** Descarga el último `VisionPlusHome.apk` y abre el instalador. */
    suspend fun installLatest(context: Context) =
        installAsset(context, HomeContract.LAUNCHER_ASSET) { state = it }

    /** Descarga el último `VisionPlus.apk` y abre el instalador (launcher sin la app). */
    suspend fun installVisionPlus(context: Context) =
        installAsset(context, HomeContract.APP_ASSET) { visionState = it }

    fun reset() {
        state = State.Idle
    }

    fun resetVision() {
        visionState = State.Idle
    }

    private suspend fun installAsset(
        context: Context,
        assetName: String,
        setState: (State) -> Unit,
    ) {
        val app = context.applicationContext
        if (!canInstall(app)) {
            // Mejor pedir el permiso ANTES de bajar 101 MB.
            openInstallPermission(app)
            setState(State.Failed("Permite «Instalar apps desconocidas» para Vision+ Home y vuelve a intentarlo"))
            return
        }
        setState(State.Checking)
        try {
            val link = withContext(Dispatchers.IO) { assetUrl(assetName) }
            if (link.isNullOrBlank()) {
                setState(State.Failed("El release no trae $assetName"))
                return
            }
            val file = withContext(Dispatchers.IO) {
                download(app, link, assetName) { percent -> setState(State.Downloading(percent)) }
            }
            if (file == null) {
                setState(State.Failed("No se pudo descargar $assetName"))
                return
            }
            setState(if (install(app, file)) State.Ready else State.Failed("No se pudo abrir el instalador"))
        } catch (t: Throwable) {
            setState(State.Failed(t.message ?: "Error de red"))
        }
    }

    private fun assetUrl(assetName: String): String? {
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
                if (asset.optString("name").equals(assetName, ignoreCase = true)) {
                    return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                }
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(
        context: Context,
        link: String,
        assetName: String,
        onProgress: (Int) -> Unit,
    ): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, assetName)
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
