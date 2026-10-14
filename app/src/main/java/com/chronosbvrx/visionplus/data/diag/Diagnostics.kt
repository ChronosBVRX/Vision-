package com.chronosbvrx.visionplus.data.diag

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.DeviceKind
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Diagnóstico de campo (Fase 1): crashes, ANRs y anomalías hacia el VPS.
 *
 * - Nunca cambia el comportamiento: los crashes se re-lanzan al handler previo.
 * - Sin datos personales: no viajan tokens, SN, userId ni títulos; el id de
 *   dispositivo va hasheado (SHA-256) solo para contar dispositivos distintos.
 * - Cola en disco con reintentos: sin red, el reporte espera y sube después.
 * - El proceso `:x4k` también captura (crashes del reproductor) y encola; la subida
 *   la hace el proceso principal para no duplicar envíos.
 */
object Diagnostics {

    private const val TAG = "Diagnostics"
    private const val QUEUE_DIR = "diag_queue"
    private const val MAX_QUEUE = 40
    private const val MAX_DETAIL_CHARS = 40_000
    private const val UPLOAD_EVERY_MS = 60_000L
    private const val ANR_PING_MS = 2_000L
    private const val ANR_THRESHOLD_MS = 5_000L
    private const val ANR_COOLDOWN_MS = 60_000L

    @Volatile private var enabled = true
    @Volatile private var screen = ""
    @Volatile private var phase = ""
    @Volatile private var engineState = "sin-iniciar"
    @Volatile private var app: Application? = null
    private val started = AtomicLong(0L)
    private val flushing = AtomicBoolean(false)

    fun init(application: Application) {
        if (started.getAndSet(1L) != 0L) return
        app = application
        enabled = com.chronosbvrx.visionplus.ui.settings.VisionSettings.isDiagnosticsEnabled(application)
        installCrashGuard()
        startAnrWatchdog()
        if (isMainProcess(application)) {
            thread(name = "diag-upload", isDaemon = true) { uploadLoop() }
            flush()
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) flush()
    }

    /** Pantalla/ruta actual (contexto del reporte). */
    fun screen(route: String) {
        screen = route.take(60)
    }

    /** Fase/flujo en curso («reproductor», «descarga», «arranque»…). */
    fun phase(name: String) {
        phase = name.take(60)
    }

    /** Estado del motor nativo («cargado», «sin-sdk», «error»…). */
    fun engine(state: String) {
        engineState = state.take(40)
    }

    /** Anomalía no-fatal: código corto y estable + detalle técnico. */
    fun anomaly(code: String, detail: String = "") {
        enqueue(build(kind = "anomaly", code = code, detail = detail))
    }

    /** Reporte manual del usuario desde Ajustes. */
    fun feedback(text: String) {
        enqueue(build(kind = "feedback", code = "USER_FEEDBACK", detail = text))
    }

    internal fun reportCrash(t: Thread, e: Throwable) {
        enqueue(build(kind = "crash", code = e.javaClass.simpleName, detail = stackOf(e), thread = t.name))
    }

    internal fun reportAnr(blockedMs: Long) {
        val frames = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { frame ->
            "${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
        }
        enqueue(build(kind = "anr", code = "MAIN_BLOCKED_${blockedMs / 1000}s", detail = frames))
    }

    // ----- internos -----

    private fun build(kind: String, code: String, detail: String, thread: String = ""): JSONObject? {
        val ctx = app ?: return null
        return runCatching {
            val payload = JSONObject().apply {
                put("process", processName(ctx))
                put("thread", thread)
                put("screen", screen)
                put("phase", phase)
                put("engine", engineState)
                put("ramMb", ramMb(ctx))
                put("tv", DeviceKind.isTelevision(ctx))
                put("network", networkType(ctx))
                put("freeStorageMb", ctx.filesDir.usableSpace / (1024 * 1024))
                put("uptimeMin", SystemClock.elapsedRealtime() / 60_000)
                put("deviceId", deviceHash(ctx))
                if (detail.isNotBlank()) put("detail", detail.take(MAX_DETAIL_CHARS))
            }
            JSONObject().apply {
                put("fingerprint", fingerprint(kind, code, detail))
                put("kind", kind)
                put("code", code.take(64))
                put("appVersion", BuildConfig.VERSION_NAME)
                put("versionCode", BuildConfig.VERSION_CODE)
                put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
                put("android", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                put("payload", payload)
            }
        }.getOrNull()
    }

    /**
     * Huella estable para agrupar: tipo + código + primeras líneas del stack sin
     * números de línea (así el mismo error agrupa aunque el código se mueva).
     */
    internal fun fingerprint(kind: String, code: String, detail: String): String {
        val frames = detail.lineSequence()
            .map { it.substringBefore("(").trim() }
            .filter { it.isNotBlank() }
            .take(6)
            .joinToString("\n")
        val base = "$kind|$code|$frames".take(600)
        val digest = MessageDigest.getInstance("SHA-256").digest(base.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun enqueue(report: JSONObject?) {
        if (!enabled || report == null) return
        val ctx = app ?: return
        runCatching {
            val dir = File(ctx.filesDir, QUEUE_DIR).apply { mkdirs() }
            File(dir, "r-${System.currentTimeMillis()}-${(0..9999).random()}.json").writeText(report.toString())
            trimQueue(dir)
        }.onFailure { Log.w(TAG, "no se pudo encolar: ${it.message}") }
        flush()
    }

    /** Conserva los [max] reportes más nuevos (los nombres ordenan por tiempo). */
    internal fun trimQueue(dir: File, max: Int = MAX_QUEUE) {
        val files = dir.listFiles()?.sortedBy { it.name } ?: return
        if (files.size <= max) return
        files.take(files.size - max).forEach { it.delete() }
    }

    private fun uploadLoop() {
        while (true) {
            runCatching { flush() }
            Thread.sleep(UPLOAD_EVERY_MS)
        }
    }

    fun flush() {
        val ctx = app ?: return
        if (!enabled || !isMainProcess(ctx)) return
        if (!flushing.compareAndSet(false, true)) return
        thread(name = "diag-flush", isDaemon = true) {
            try {
                val files = File(ctx.filesDir, QUEUE_DIR).listFiles()?.sortedBy { it.name } ?: return@thread
                for (file in files) {
                    val sent = runCatching { upload(file.readText()) }.getOrDefault(false)
                    if (sent) file.delete() else break // sin red: se reintenta en el próximo ciclo
                }
            } finally {
                flushing.set(false)
            }
        }
    }

    private fun upload(json: String): Boolean {
        val connection = (URL("${RemoteIndexSource.BASE_URL}/v1/diag").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { reportCrash(t, e) }
            previous?.uncaughtException(t, e)
        }
    }

    private fun startAnrWatchdog() {
        val main = Handler(Looper.getMainLooper())
        thread(name = "diag-anr", isDaemon = true) {
            var lastReport = 0L
            while (true) {
                val tick = AtomicLong(0L)
                main.post { tick.set(SystemClock.uptimeMillis()) }
                Thread.sleep(ANR_PING_MS)
                val elapsed = if (tick.get() == 0L) 0L else SystemClock.uptimeMillis() - tick.get()
                val now = SystemClock.uptimeMillis()
                if (elapsed > ANR_THRESHOLD_MS && now - lastReport > ANR_COOLDOWN_MS) {
                    lastReport = now
                    runCatching { reportAnr(elapsed) }
                }
            }
        }
    }

    private fun isMainProcess(ctx: Context): Boolean {
        val proc = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else ""
        }.getOrDefault("")
        return proc.isEmpty() || proc == ctx.packageName
    }

    private fun processName(ctx: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
        else ctx.packageName
    }.getOrDefault(ctx.packageName)

    private fun ramMb(ctx: Context): Int = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).toInt()
    }.getOrDefault(0)

    private fun networkType(ctx: Context): String = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return@runCatching "sin-red"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "datos"
            else -> "otra"
        }
    }.getOrDefault("desconocida")

    private fun stackOf(e: Throwable): String = e.stackTrace.take(40).joinToString("\n") { frame ->
        "${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
    }

    private fun deviceHash(ctx: Context): String = runCatching {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}
