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
    private const val RELEASES_URL = "https://api.github.com/repos/ChronosBVRX/Vision-Releases/releases/latest"

    /**
     * El asset estable del release: la descarga directa evita la API de GitHub (límite por IP)
     * y es exactamente el APK que se publica para el launcher.
     */
    private const val LAUNCHER_APK_URL =
        "https://github.com/ChronosBVRX/Vision-Releases/releases/latest/download/VisionPlusHome.apk"

    /** La comprobación baja el APK del launcher (1.5 MB): se cachea 6 h. */
    private const val LAUNCHER_CHECK_MS = 6L * 60 * 60 * 1000

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        /** La versión del release no es más nueva que la instalada: no hay nada que bajar. */
        data object UpToDate : State
        data class Failed(val message: String) : State
    }

    /** La última versión del launcher publicada en el release (leída del propio APK). */
    data class LauncherRelease(
        val versionName: String,
        val versionCode: Long,
        val assetUrl: String,
        val notes: String? = null,
    )

    /** Estado de la actualización del launcher. */
    var state: State by mutableStateOf(State.Idle)
        private set

    /** Lo que publica el release para el launcher; null hasta la primera comprobación. */
    var launcherLatest by mutableStateOf<LauncherRelease?>(null)
        private set

    /** Hay una comprobación en curso (la barra superior/ajustes lo dicen sin mentir). */
    var launcherChecking by mutableStateOf(false)
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

    /**
     * Actualiza el launcher, pero SOLO si el release publica una versión MÁS NUEVA que la
     * instalada. La comprobación se hace aquí mismo (no solo en la interfaz): un botón viejo,
     * una caché rancia o un toque repetido jamás pueden degradar el equipo.
     */
    suspend fun installLatest(context: Context) {
        val app = context.applicationContext
        if (!canInstall(app)) {
            // Mejor pedir el permiso ANTES de bajar el APK.
            openInstallPermission(app)
            state = State.Failed("Permite «Instalar apps desconocidas» para Vision+ Home y vuelve a intentarlo")
            return
        }
        state = State.Checking
        val latest = checkLauncher(app, force = true)
        if (latest == null) {
            state = State.Failed("No se pudo comprobar la última versión del launcher")
            return
        }
        if (!launcherNeedsUpdate(latest.versionCode, latest.versionName)) {
            state = State.UpToDate
            return
        }
        // La comprobación ya dejó el APK en caché: se instala ESE archivo (sin bajar dos veces).
        val cached = launcherApkFile(app)
        if (cached.isFile && archiveVersionCode(app, cached) == latest.versionCode) {
            state = if (install(app, cached)) State.Ready else State.Failed("No se pudo abrir el instalador")
            return
        }
        installAsset(app, HomeContract.LAUNCHER_ASSET) { state = it }
    }

    /**
     * Lee el `VisionPlusHome.apk` del último release (1.5 MB) y guarda su versión real.
     * Se cachea [LAUNCHER_CHECK_MS] para no bajar el APK en cada apertura del launcher.
     */
    suspend fun checkLauncher(context: Context, force: Boolean = false): LauncherRelease? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val prefs = LauncherPrefs(app)
            val now = System.currentTimeMillis()
            val fresh = !force && prefs.lastLauncherAt > 0 &&
                now - prefs.lastLauncherAt < LAUNCHER_CHECK_MS
            if (fresh) {
                cachedLauncher(prefs)?.let {
                    launcherLatest = it
                    return@withContext it
                }
            }
            launcherChecking = true
            try {
                val release = fetchLauncher(app, prefs)
                if (release != null) {
                    launcherLatest = release
                } else {
                    cachedLauncher(prefs)?.let { launcherLatest = it }
                }
                release ?: cachedLauncher(prefs)
            } finally {
                launcherChecking = false
            }
        }

    /** ¿El release trae una versión más nueva que ESTE launcher? */
    fun launcherPending(): Boolean {
        val latest = launcherLatest ?: return false
        return launcherNeedsUpdate(latest.versionCode, latest.versionName)
    }

    /**
     * Comparación pura por versionCode (el número que Android usa para instalar) y, si el APK
     * no lo declara, por nombre. Nunca se ofrece una versión igual o menor.
     */
    internal fun launcherNeedsUpdate(
        latestCode: Long,
        latestName: String,
        currentCode: Long = BuildConfig.VERSION_CODE.toLong(),
        currentName: String = BuildConfig.VERSION_NAME,
    ): Boolean {
        if (latestCode > 0 && currentCode > 0) return latestCode > currentCode
        return isOlder(currentName, latestName)
    }

    private fun cachedLauncher(prefs: LauncherPrefs): LauncherRelease? {
        val name = prefs.lastLauncherVersion
        val code = prefs.lastLauncherCode
        if (name.isBlank() && code <= 0) return null
        return LauncherRelease(name, code, prefs.lastLauncherUrl)
    }

    private fun launcherApkFile(context: Context): File =
        File(File(context.cacheDir, "updates"), HomeContract.LAUNCHER_ASSET)

    private fun archiveVersionCode(context: Context, file: File): Long = runCatching {
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return@runCatching 0L
        if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
    }.getOrDefault(0L)

    private fun fetchLauncher(context: Context, prefs: LauncherPrefs): LauncherRelease? {
        // Descarga directa del asset estable: no usa la API de GitHub (sin límite por IP) y es
        // el MISMO archivo que publica el release para el launcher.
        val file = download(context, LAUNCHER_APK_URL, HomeContract.LAUNCHER_ASSET, minBytes = 200_000L) {}
        if (file == null) {
            android.util.Log.i("VisionHome", "OTA launcher: no se pudo bajar el APK")
            return null
        }
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (archive == null) {
            android.util.Log.i("VisionHome", "OTA launcher: el APK no se pudo leer")
            return null
        }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val name = archive.versionName.orEmpty()
        if (name.isBlank() && code <= 0) return null
        android.util.Log.i(
            "VisionHome",
            "OTA launcher: release=$name ($code) · actual=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        prefs.lastLauncherVersion = name
        prefs.lastLauncherCode = code
        prefs.lastLauncherUrl = LAUNCHER_APK_URL
        prefs.lastLauncherAt = System.currentTimeMillis()
        return LauncherRelease(
            versionName = name,
            versionCode = code,
            assetUrl = LAUNCHER_APK_URL,
        )
    }

    /** Descarga el último `VisionPlus.apk` y abre el instalador (launcher sin la app). */
    suspend fun installVisionPlus(context: Context) =
        installAsset(context, HomeContract.APP_ASSET) { visionState = it }

    fun reset() {
        state = State.Idle
    }

    fun resetVision() {
        visionState = State.Idle
    }

    /**
     * La versión de Vision+ del último release (el tag, p. ej. «2.5.130»). Se cachea 24 h:
     * el launcher la usa para avisar si la app instalada se quedó atrás.
     */
    suspend fun latestAppVersion(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = LauncherPrefs(context)
        val now = System.currentTimeMillis()
        val cached = prefs.lastReleaseTag
        if (cached.isNotBlank() && now - prefs.lastReleaseAt < 24L * 60 * 60 * 1000) {
            return@withContext cached
        }
        val fresh = runCatching { fetchTag() }.getOrNull()
        if (fresh.isNullOrBlank()) return@withContext cached.takeIf { it.isNotBlank() }
        prefs.lastReleaseTag = fresh
        prefs.lastReleaseAt = now
        fresh
    }

    /** Compara versiones «2.5.9» < «2.5.130» sin caer en el orden alfabético. */
    internal fun isOlder(current: String, latest: String): Boolean {
        fun parts(value: String) = value.trim().removePrefix("v")
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        val a = parts(current)
        val b = parts(latest)
        if (a.isEmpty() || b.isEmpty()) return false
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left < right
        }
        return false
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
        val body = releaseJson() ?: return null
        return assetUrl(body, assetName)
    }

    private fun assetUrl(body: String, assetName: String): String? {
        val assets = JSONObject(body).optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").equals(assetName, ignoreCase = true)) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** El tag del último release, sin la «v» («v2.5.130» → «2.5.130»). */
    private fun fetchTag(): String? {
        val body = releaseJson() ?: return null
        return JSONObject(body).optString("tag_name")
            .trim()
            .removePrefix("v")
            .takeIf { it.isNotBlank() }
    }

    private fun releaseJson(): String? {
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
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(
        context: Context,
        link: String,
        assetName: String,
        minBytes: Long = 1_000_000L,
        onProgress: (Int) -> Unit,
    ): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Se reemplaza SOLO este APK: el de Vision+ y el del launcher pueden convivir en caché.
        val target = File(dir, assetName)
        if (target.exists()) target.delete()
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
        target.takeIf { it.length() > minBytes }
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
