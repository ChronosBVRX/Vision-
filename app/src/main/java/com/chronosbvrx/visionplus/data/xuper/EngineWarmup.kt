package com.chronosbvrx.visionplus.data.xuper

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.chronosbvrx.visionplus.DeviceKind
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import com.chronosbvrx.visionplus.home.contract.EnginePrewarmContract
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Precalentamiento del motor de video (`:x4k`) fuera del toque del usuario.
 *
 * Qué SÍ deja listo: proceso `:x4k`, DEX/libs (ya van en el APK), `boot` del motor,
 * `Env`/`Entries` si están cacheados y la lista de canales en memoria.
 *
 * Qué NO hace: acuñar sesión, reproducir, crear la vista/SurfaceView ni pedir el primer
 * cuadro. Eso se sigue pagando al dar Play; el ahorro es el arranque en frío.
 *
 * Dos caminos independientes:
 *  - La app, desde su splash, si el equipo tiene RAM suficiente (2 GB o más).
 *  - El launcher, vinculándose al [EngineWarmService] de `:x4k` al arrancar la tele.
 *
 * Los equipos de 1.5 GB conservan el camino diferido de siempre (`XuperCatalog` a los
 * ~7.5 s); no se arranca `:x4k` durante su arranque en frío para no provocar LMK.
 */
object EngineWarmup {

    private const val TAG = "EngineWarmup"
    private const val PREFS = "x4k_api"
    private const val K_ENTRIES = "entries_json"
    private const val K_PARAMS = "play_params"

    private val warming = AtomicBoolean(false)

    @Volatile private var lastResult: Boolean? = null

    /** Estado leído del archivo compartido entre procesos. */
    data class Ready(val ok: Boolean, val at: Long, val pid: Int, val source: String)

    enum class Result { READY, TIMEOUT, SKIPPED }

    data class WarmOutcome(
        val result: Result,
        val source: String,
        val alreadyWarm: Boolean,
        val elapsedMs: Long,
    )

    // ------------------------------------------------------------------ política

    /**
     * Pura y testeable: el precalentamiento "completo" solo en TV con RAM suficiente.
     * Los 1.5 GB quedan fuera a propósito; el umbral se puede mover desde `/v1/config`.
     */
    internal fun shouldWarmFor(
        totalRamMb: Long,
        minRamMb: Int,
        isTv: Boolean,
        enabled: Boolean,
    ): Boolean = enabled && isTv && totalRamMb >= minRamMb

    fun shouldWarm(context: Context): Boolean {
        val app = context.applicationContext
        val enabled = runCatching { RemoteConfig.flag(app, "engine_prewarm", true) }.getOrDefault(true)
        if (!enabled) return false
        if (!DeviceKind.isTelevision(app)) return false
        val minRam = runCatching {
            RemoteConfig.intFlag(app, "engine_prewarm_min_ram_mb", EnginePrewarmContract.MIN_RAM_MB.toInt())
        }.getOrDefault(EnginePrewarmContract.MIN_RAM_MB.toInt())
        val totalRam = DevicePerformance.totalMemoryMb(app)
        return shouldWarmFor(totalRam, minRam, isTv = true, enabled = true)
    }

    /** El splash solo espera si además está permitido el warmup de arranque. */
    fun shouldWarmOnSplash(context: Context): Boolean {
        val app = context.applicationContext
        if (!shouldWarm(app)) return false
        return runCatching { RemoteConfig.flag(app, "engine_prewarm_splash", true) }.getOrDefault(true)
    }

    fun splashTimeoutMs(context: Context): Long = runCatching {
        RemoteConfig.intFlag(context, "engine_prewarm_max_ms", 5_000)
            .coerceIn(0, 8_000)
            .toLong()
    }.getOrDefault(5_000L)

    // ------------------------------------------------------------------ estado compartido

    internal fun parseReady(text: String): Ready? = runCatching {
        val props = Properties()
        props.load(text.reader())
        val at = props.getProperty("at")?.trim()?.toLongOrNull() ?: return null
        val pid = props.getProperty("pid")?.trim()?.toIntOrNull() ?: return null
        val ok = props.getProperty("ok")?.trim() == "1"
        val source = props.getProperty("source")?.trim().orEmpty().ifBlank { "unknown" }
        Ready(ok = ok, at = at, pid = pid, source = source)
    }.getOrNull()

    internal fun isFresh(
        ready: Ready?,
        nowMs: Long,
        ttlMs: Long,
        processAlive: (Int) -> Boolean,
    ): Boolean {
        if (ready == null || !ready.ok || ready.pid <= 0 || ready.at <= 0L) return false
        val age = nowMs - ready.at
        if (age < -60_000L || age > ttlMs) return false
        return processAlive(ready.pid)
    }

    fun isWarmFresh(context: Context): Boolean {
        val app = context.applicationContext
        val file = File(app.filesDir, EnginePrewarmContract.READY_FILE)
        if (!file.exists()) return false
        val ready = parseReady(runCatching { file.readText() }.getOrDefault("")) ?: return false
        return isFresh(
            ready = ready,
            nowMs = System.currentTimeMillis(),
            ttlMs = EnginePrewarmContract.READY_TTL_MS,
            processAlive = { pid -> isProcessAlive(app, pid) },
        )
    }

    fun readySource(context: Context): String {
        val file = File(context.applicationContext.filesDir, EnginePrewarmContract.READY_FILE)
        return parseReady(runCatching { file.readText() }.getOrDefault(""))?.source ?: "unknown"
    }

    internal fun isProcessAlive(context: Context, pid: Int): Boolean {
        if (pid <= 0) return false
        if (runCatching { File("/proc/$pid").exists() }.getOrDefault(false)) return true
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.any { it.pid == pid } == true
        }.getOrDefault(false)
    }

    private fun writeReady(context: Context, ok: Boolean, source: String) {
        runCatching {
            val file = File(context.filesDir, EnginePrewarmContract.READY_FILE)
            val tmp = File(context.filesDir, "${EnginePrewarmContract.READY_FILE}.part")
            val props = Properties().apply {
                setProperty("ok", if (ok) "1" else "0")
                setProperty("at", System.currentTimeMillis().toString())
                setProperty("pid", android.os.Process.myPid().toString())
                setProperty("source", source)
            }
            FileOutputStream(tmp).use { out ->
                props.store(out, null)
                out.flush()
                runCatching { out.fd.sync() }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }
        }.onFailure { Log.w(TAG, "no se pudo escribir ${EnginePrewarmContract.READY_FILE}: ${it.message}") }
    }

    // ------------------------------------------------------------------ caminos

    /** Pide el warmup al proceso `:x4k` (mismo APK, receptor no exportado). */
    fun requestWarm(context: Context) {
        runCatching {
            val app = context.applicationContext
            app.sendBroadcast(
                Intent(app, X4KWarmupReceiver::class.java)
                    .setAction(X4KWarmupReceiver.ACTION_PREWARM),
            )
        }.onFailure { Log.w(TAG, "no se pudo pedir el warmup: ${it.message}") }
    }

    /**
     * Espera (con tope) a que el proceso `:x4k` quede caliente. Si el launcher ya lo
     * calentó, vuelve de inmediato; si el equipo es modesto, no arranca nada nuevo.
     */
    suspend fun awaitReadyOrWarm(context: Context, timeoutMs: Long = splashTimeoutMs(context)): WarmOutcome {
        val started = SystemClock.uptimeMillis()
        if (isWarmFresh(context)) {
            return WarmOutcome(
                result = Result.READY,
                source = readySource(context),
                alreadyWarm = true,
                elapsedMs = SystemClock.uptimeMillis() - started,
            )
        }
        if (!shouldWarmOnSplash(context)) {
            return WarmOutcome(Result.SKIPPED, source = "skipped", alreadyWarm = false, elapsedMs = 0L)
        }
        // Deja respirar al primer frame del splash antes de levantar `:x4k` (en una tele
        // de 2 GB el arranque del motor compite con el catálogo y con la UI recién pintada).
        delay(200L)
        requestWarm(context)
        while (SystemClock.uptimeMillis() - started < timeoutMs) {
            delay(100L)
            if (isWarmFresh(context)) {
                return WarmOutcome(
                    result = Result.READY,
                    source = readySource(context),
                    alreadyWarm = false,
                    elapsedMs = SystemClock.uptimeMillis() - started,
                )
            }
        }
        return WarmOutcome(
            result = Result.TIMEOUT,
            source = readySource(context),
            alreadyWarm = false,
            elapsedMs = SystemClock.uptimeMillis() - started,
        )
    }

    /**
     * Trabajo real del `:x4k`. No acuña sesión, no reproduce y no crea la vista.
     * Se llama desde el receptor (splash/deferred) y desde el servicio (launcher).
     *
     * No se filtra por RAM aquí a propósito: el camino diferido de los equipos de 1.5 GB
     * ya existía y debe seguir funcionando. El filtro vive en [shouldWarm]/[shouldWarmOnSplash].
     */
    fun warmBlocking(
        context: Context,
        entriesOverride: String? = null,
        paramsOverride: String = "",
        includeLive: Boolean = true,
        source: String = "deferred",
        playerActive: () -> Boolean = { false },
    ): Boolean {
        if (playerActive()) {
            Log.i(TAG, "warmup omitido: reproductor activo")
            return false
        }
        if (!warming.compareAndSet(false, true)) {
            return lastResult == true
        }
        val app = context.applicationContext
        return try {
            if (!runCatching { RemoteConfig.flag(app, "engine_prewarm", true) }.getOrDefault(true)) {
                Log.i(TAG, "warmup $source omitido por configuración remota")
                lastResult = false
                return false
            }
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val entries = entriesOverride?.takeIf { it.isNotBlank() }
                ?: prefs.getString(K_ENTRIES, null)
            val params = paramsOverride.takeIf { it.isNotBlank() }
                ?: prefs.getString(K_PARAMS, "").orEmpty()
            val androidId = runCatching {
                Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            }.getOrDefault("")
            val ok = X4KTitan.ensureConfigured(
                context = app,
                userId = X4KApi.userIdOf(),
                devId = X4KApi.snOf(),
                androidId = androidId,
                entriesJson = entries,
                playParams = params,
            )
            writeReady(app, ok, source)
            lastResult = ok
            Log.i(TAG, "warmup $source ok=$ok entries=${!entries.isNullOrBlank()} params=${params.isNotBlank()}")
            if (ok && includeLive) {
                // Deja el índice de canales listo para el zapper. Es CPU local: no pide red.
                runCatching { X4KApi.cachedLive(app) }
            }
            ok
        } catch (e: Throwable) {
            writeReady(app, ok = false, source = source)
            lastResult = false
            Log.w(TAG, "warmup $source falló: ${e.javaClass.simpleName}: ${e.message}")
            false
        } finally {
            warming.set(false)
        }
    }
}
