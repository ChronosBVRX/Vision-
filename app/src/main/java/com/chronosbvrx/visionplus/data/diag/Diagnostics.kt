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
    /** Los latidos de sesión van a su PROPIA cola: nunca deben desalojar un crash. */
    private const val SESSION_QUEUE_DIR = "diag_sessions"
    private const val MAX_QUEUE = 40
    private const val MAX_SESSION_QUEUE = 5
    private const val MAX_DETAIL_CHARS = 40_000
    private const val UPLOAD_EVERY_MS = 60_000L
    private const val ANR_PING_MS = 2_000L
    private const val ANR_THRESHOLD_MS = 5_000L
    private const val ANR_COOLDOWN_MS = 60_000L

    /** Una anomalía del mismo código no se repite antes de este silencio (con su conteo). */
    private const val ANOMALY_MIN_GAP_MS = 60_000L

    /** Archivo que comparte el id de sesión con el proceso `:x4k` (que no ve nuestras prefs). */
    private const val SESSION_FILE = "diag_session"

    /** Un id de sesión más viejo que esto ya no describe la apertura actual. */
    private const val SESSION_MAX_AGE_MS = 6L * 60 * 60 * 1000

    @Volatile private var enabled = true
    @Volatile private var screen = ""
    @Volatile private var phase = ""
    @Volatile private var engineState = "sin-iniciar"
    @Volatile private var app: Application? = null

    /**
     * Actividades al frente en ESTE proceso. El vigilante de ANR no mide mientras la app
     * está en segundo plano: Android congela el proceso (cached app freezer) y
     * `uptimeMillis` sigue corriendo, así que el hueco se reportaba como un bloqueo de
     * horas que nunca existió (era el 74 % de los ANR del VPS).
     */
    private val resumedActivities = java.util.concurrent.atomic.AtomicInteger(0)

    /** Id de la sesión de uso actual (uno por apertura); vacío hasta [init]. */
    @Volatile private var session = ""
    private val sessionReported = AtomicBoolean(false)
    private val started = AtomicLong(0L)
    private val flushing = AtomicBoolean(false)
    private val anomalyLastAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val anomalySkipped = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * La suite de unit tests corre en este mismo proceso y llegaba a subir reportes
     * (`FOCUS_LOST` de `robolectric`) al VPS de producción, contaminando la telemetría
     * de campo. Robolectric solo existe como dependencia de tests: en el APK real esta
     * clase no existe y los reportes fluyen normal.
     */
    private val isTestRuntime by lazy {
        runCatching { Class.forName("org.robolectric.RobolectricTestRunner"); true }.getOrDefault(false)
    }

    fun init(application: Application) {
        if (isTestRuntime) return
        if (started.getAndSet(1L) != 0L) return
        app = application
        // El id de sesión se crea al abrir la app (proceso principal) y viaja a `:x4k` por
        // archivo: así el crash del reproductor se cuenta contra ESA apertura.
        session = startSession(application, owner = isMainProcess(application))
        enabled = com.chronosbvrx.visionplus.ui.settings.VisionSettings.isDiagnosticsEnabled(application)
        installCrashGuard()
        startAnrWatchdog()
        if (isMainProcess(application)) {
            thread(name = "diag-upload", isDaemon = true) { uploadLoop() }
            flush()
        }
    }

    /**
     * Un latido por apertura de la app (`MainActivity.onCreate`). Es el denominador del
     * «% de sesiones sin crash»: sin él solo se ven los fallos y no la proporción real.
     */
    fun sessionStart() {
        if (isTestRuntime) return
        if (!sessionReported.compareAndSet(false, true)) return
        enqueue(build(kind = "session", code = "APP_START", detail = ""), SESSION_QUEUE_DIR, MAX_SESSION_QUEUE)
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
        if (isTestRuntime) return
        // Un bucle (red caída, portal que rechaza la sesión) puede llamar aquí cientos de
        // veces por minuto: se emite una y se cuentan las demás para el mismo código.
        val now = SystemClock.elapsedRealtime()
        val last = anomalyLastAt[code] ?: 0L
        if (!anomalyGateOpen(now, last)) {
            anomalySkipped.merge(code, 1, Int::plus)
            return
        }
        val skipped = anomalySkipped.remove(code) ?: 0
        anomalyLastAt[code] = now
        enqueue(build(kind = "anomaly", code = code, detail = detail, suppressed = skipped))
    }

    /** Reporte manual del usuario desde Ajustes. */
    fun feedback(text: String) {
        enqueue(build(kind = "feedback", code = "USER_FEEDBACK", detail = text))
    }

    internal fun reportCrash(t: Thread, e: Throwable) {
        // El mensaje del sistema (p. ej. «Key X was already used») viaja aparte: la huella
        // se calcula del stack (estable aunque cambie la redacción), pero sin el mensaje
        // el diagnóstico en campo no puede distinguir entre causas parecidas.
        enqueue(build(kind = "crash", code = e.javaClass.simpleName, detail = stackOf(e), thread = t.name, message = e.message.orEmpty()))
    }

    internal fun reportAnr(blockedMs: Long) {
        val frames = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { frame ->
            "${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
        }
        // La duración exacta va en el mensaje; el CÓDIGO va por rango para que la huella
        // no cambie con cada segundo (51s/52s/53s creaban una alerta de Telegram cada una).
        enqueue(build(kind = "anr", code = anrCode(blockedMs), detail = frames, message = "blockedMs=$blockedMs"))
    }

    /** Código estable del ANR por rango de duración (la huella no cambia por segundos). */
    internal fun anrCode(blockedMs: Long): String {
        val ms = blockedMs.coerceAtLeast(0L)
        val bucket = when {
            ms < 10_000L -> "5-9s"
            ms < 60_000L -> "10-59s"
            ms < 600_000L -> "1-10min"
            else -> "mas-de-10min"
        }
        return "MAIN_BLOCKED_$bucket"
    }

    /**
     * Bloqueo del hilo principal SOLO si la app estaba al frente durante todo el intervalo.
     * Con la app en segundo plano el proceso puede estar congelado y el reloj sigue: el
     * hueco no es un ANR real.
     */
    internal fun anrBlockedMs(prevTickMs: Long, nowMs: Long, foregroundBefore: Boolean, foregroundNow: Boolean): Long {
        if (!foregroundBefore || !foregroundNow || prevTickMs <= 0L) return 0L
        return (nowMs - prevTickMs).coerceAtLeast(0L)
    }

    /** Una actividad de este proceso quedó visible/interactiva. */
    fun activityResumed() {
        resumedActivities.incrementAndGet()
    }

    /** Una actividad de este proceso dejó de estar al frente. */
    fun activityPaused() {
        resumedActivities.updateAndGet { value -> if (value > 0) value - 1 else 0 }
    }

    private fun isForeground(): Boolean = resumedActivities.get() > 0

    // ----- internos -----

    private fun build(
        kind: String,
        code: String,
        detail: String,
        thread: String = "",
        message: String = "",
        suppressed: Int = 0,
    ): JSONObject? {
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
                // La sesión une el crash con la apertura en la que ocurrió: es lo que permite
                // calcular «% de sesiones sin crash» en el servidor.
                if (session.isNotBlank()) put("session", session)
                if (suppressed > 0) put("suppressed", suppressed)
                if (message.isNotBlank()) put("message", message.take(400))
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

    private fun enqueue(
        report: JSONObject?,
        dirName: String = QUEUE_DIR,
        max: Int = MAX_QUEUE,
    ) {
        if (!enabled || report == null) return
        val ctx = app ?: return
        runCatching {
            val dir = File(ctx.filesDir, dirName).apply { mkdirs() }
            File(dir, "r-${System.currentTimeMillis()}-${(0..9999).random()}.json").writeText(report.toString())
            trimQueue(dir, max)
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
        if (isTestRuntime) return
        val ctx = app ?: return
        if (!enabled || !isMainProcess(ctx)) return
        if (!flushing.compareAndSet(false, true)) return
        thread(name = "diag-flush", isDaemon = true) {
            try {
                // Primero los reportes reales; los latidos de sesión, al final.
                for (dirName in listOf(QUEUE_DIR, SESSION_QUEUE_DIR)) {
                    val files = File(ctx.filesDir, dirName).listFiles()?.sortedBy { it.name } ?: continue
                    for (file in files) {
                        val sent = runCatching { upload(file.readText()) }.getOrDefault(false)
                        if (sent) file.delete() else break // sin red: se reintenta en el próximo ciclo
                    }
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
                val foregroundBefore = isForeground()
                if (foregroundBefore) main.post { tick.set(SystemClock.uptimeMillis()) }
                Thread.sleep(ANR_PING_MS)
                val now = SystemClock.uptimeMillis()
                val elapsed = anrBlockedMs(tick.get(), now, foregroundBefore, isForeground())
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

    private fun deviceHash(ctx: Context): String = deviceHashOf(ctx)

    /** El id de este equipo (mismo hash que viaja en la telemetría). */
    fun deviceId(context: Context): String = deviceHashOf(context.applicationContext)

    private fun deviceHashOf(ctx: Context): String = runCatching {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    // ----- sesiones y freno de anomalías (puros, con guardián) -----

    /** ¿Toca emitir una anomalía de este código? Tras emitir, el mismo código calla [gapMs]. */
    internal fun anomalyGateOpen(nowMs: Long, lastAtMs: Long, gapMs: Long = ANOMALY_MIN_GAP_MS): Boolean =
        lastAtMs <= 0L || nowMs - lastAtMs >= gapMs

    /** Id de sesión nuevo: 12 caracteres hex (corto, sin datos del equipo). */
    internal fun newSessionId(random: java.util.Random = java.util.Random()): String = buildString(12) {
        repeat(12) { append("0123456789abcdef"[random.nextInt(16)]) }
    }

    /** Formato del archivo que comparte la sesión con `:x4k`: `id` y epoch en dos líneas. */
    internal fun encodeSession(id: String, atMs: Long): String = "$id\n$atMs"

    /**
     * Lee el id de sesión compartido. Devuelve null si falta, es ilegible o es de una
     * apertura demasiado vieja (otra sesión de uso).
     */
    internal fun decodeSession(text: String, nowMs: Long, maxAgeMs: Long = SESSION_MAX_AGE_MS): String? {
        val lines = text.trim().lineSequence().filter { it.isNotBlank() }.take(2).toList()
        if (lines.size < 2) return null
        val id = lines[0].trim()
        val at = lines[1].trim().toLongOrNull() ?: return null
        if (id.length < 6) return null
        val age = nowMs - at
        if (age < -300_000L || age > maxAgeMs) return null
        return id
    }

    /** Crea la sesión (proceso principal) o adopta la del archivo (`:x4k`). */
    private fun startSession(ctx: Context, owner: Boolean): String {
        val file = File(ctx.filesDir, SESSION_FILE)
        val now = System.currentTimeMillis()
        if (owner) {
            val id = newSessionId()
            runCatching { file.writeText(encodeSession(id, now)) }
            return id
        }
        return runCatching { decodeSession(file.readText(), now) }.getOrNull() ?: newSessionId()
    }
}
