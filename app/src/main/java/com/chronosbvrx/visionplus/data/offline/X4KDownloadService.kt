package com.chronosbvrx.visionplus.data.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.data.xuper.X4KTitan
import com.chronosbvrx.visionplus.ui.player.X4KPlayerAty
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

/**
 * Servicio de descargas offline en el proceso `:x4k` (el único donde el motor
 * nativo Ranger puede operar sin colisionar con Magis del proceso principal).
 *
 * v2.5.52: resolución por la VÍA DEL REPRODUCTOR (vista `ed.e` + `view.A`), que es
 * la única secuencia probada que no crashea. La vía anterior (`NativeJni.o/g`
 * directo en instance=1 con programJson manual) provocaba SIGSEGV en
 * `libranger-jni` (`star_file_cache.cpp`) y se eliminó de `X4KTitan`.
 *
 * Flujo: `prepareVod` (portal) → `playHeadless` (facade x(0,…), la misma llamada que
 * `ed.e.A()` hace al abrir) → sondeo read-only de `GetStatus` hasta ver `play_url`
 * http → descarga +cifrado con [OfflineHttpDownloader] → reporte al principal vía
 * [OfflineProgressBus]. La reproducción normal no pasa por aquí.
 */
class X4KDownloadService : Service() {

    companion object {
        private const val TAG = "X4KDownloadService"

        const val ACTION_DOWNLOAD = "com.chronosbvrx.visionplus.action.OFFLINE_DOWNLOAD"
        const val ACTION_CANCEL = "com.chronosbvrx.visionplus.action.OFFLINE_CANCEL"
        const val ACTION_PAUSE = "com.chronosbvrx.visionplus.action.OFFLINE_PAUSE"

        const val EXTRA_ID = "download_id"
        const val EXTRA_CODE = "portal_code"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_VAULT_PATH = "vault_path"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_ISSUED_AT = "issued_at"

        const val MIN_VALID_VIDEO_BYTES = MIN_VALID_VAULT_BYTES
        private const val RESOLVE_TIMEOUT_MS = 25_000L

        private const val CHANNEL_ID = "vision_downloads"
        private const val NOTIF_ID = 4101

        fun downloadIntent(
            context: Context,
            id: String,
            code: String,
            title: String,
            seriesId: String,
            quality: String,
            vaultPath: String,
            durationMs: Long,
            totalBytes: Long,
        ): Intent = Intent(context, X4KDownloadService::class.java).apply {
            action = ACTION_DOWNLOAD
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_CODE, code)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SERIES_ID, seriesId)
            putExtra(EXTRA_QUALITY, quality)
            putExtra(EXTRA_VAULT_PATH, vaultPath)
            putExtra(EXTRA_DURATION_MS, durationMs)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
            // Marca de emisión: si una pausa llega DESPUÉS de este momento, gana la
            // pausa aunque los intents se procesen en otro orden (carrera cerrada).
            putExtra(EXTRA_ISSUED_AT, System.currentTimeMillis())
        }

        fun cancelIntent(context: Context, id: String): Intent =
            Intent(context, X4KDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
            }

        /**
         * Pausa coordinada con la reproducción: el principal la pide antes de abrir el
         * reproductor nativo (mismo proceso `:x4k`, mismo motor) y espera el marcador
         * `.paused` que el servicio deja al soltar el motor.
         */
        fun pauseIntent(context: Context, id: String, vaultPath: String): Intent =
            Intent(context, X4KDownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_VAULT_PATH, vaultPath)
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    /** Ids con corrida en curso: segunda corrida concurrente del mismo id sale sin pelear. */
    private val runningIds = ConcurrentHashMap.newKeySet<String>()
    /** Código headless en curso por id: permite soltar el motor al instante al pausar. */
    private val activeCodes = ConcurrentHashMap<String, String>()
    /** vaultDir de cada corrida: dónde queda el ack `.paused` que el principal espera. */
    private val activeVaultDirs = ConcurrentHashMap<String, File>()
    /**
     * Momento (wall clock) de la última pausa pedida por id. Una corrida captura la
     * marca de emisión de su intent: si la pausa llegó después, gana la pausa (aunque
     * los intents se procesen en otro orden). Sin marcas pegadas: una reanudación
     * futura emite una marca mayor y arranca normal.
     */
    private val pauseRequestedAtMs = ConcurrentHashMap<String, Long>()
    /** Candado anti-carrera entre "pausar" y "encender el motor": jamás se cruzan. */
    private val pauseLock = Any()
    private var startedForeground = false

    private fun pauseRequestedAt(id: String): Long = pauseRequestedAtMs[id] ?: 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID).orEmpty()
                activeJobs[id]?.cancel()
                activeJobs.remove(id)
                // Soltar el motor ya (el reproductor vive en este mismo proceso).
                releaseHeadlessNow(id)
                if (activeJobs.isEmpty()) stopSelfResult(startId)
            }
            ACTION_PAUSE -> {
                val id = intent.getStringExtra(EXTRA_ID).orEmpty()
                if (id.isNotBlank()) {
                    requestPause(id, intent.getStringExtra(EXTRA_VAULT_PATH).orEmpty())
                }
                if (activeJobs.isEmpty()) stopSelfResult(startId)
            }
            ACTION_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_ID).orEmpty()
                if (id.isBlank()) return START_NOT_STICKY
                if (activeJobs[id]?.isActive == true) return START_NOT_STICKY
                ensureForeground("Preparando descarga…")
                activeJobs[id] = scope.launch {
                    runDownload(intent)
                }
            }
        }
        // NOT_STICKY a propósito (regresión Chromecast v2.5.57): un servicio STICKY
        // relanza el proceso `:x4k` solo tras un kill/reboot = doble proceso en boot
        // y muerte por LMK en 1.5 GB. Si el sistema mata una descarga, el registro la
        // deja en PAUSED (reconcile) y el usuario la reanuda con un tap.
        return START_NOT_STICKY
    }

    private suspend fun runDownload(intent: Intent) {
        val app = applicationContext
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
            .removePrefix("xtv:").removePrefix("magisvod:").removePrefix("magis:")
            .substringBefore('?').trim()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID).orEmpty()
        val quality = intent.getStringExtra(EXTRA_QUALITY).orEmpty().ifBlank { "1080p" }
        val vaultPath = intent.getStringExtra(EXTRA_VAULT_PATH).orEmpty()
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val fallbackTotal = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L).takeIf { it > 0L }
            ?: estimateTotalBytes(durationMs, quality)
        val dest = File(vaultPath)
        dest.parentFile?.let { activeVaultDirs[id] = it }
        // Marca de emisión del intent: la pausa gana si llegó después de este momento.
        val myIssuedAt = intent.getLongExtra(EXTRA_ISSUED_AT, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        fun pauseWins() = pauseRequestedAt(id) >= myIssuedAt
        // Sin doble corrida: si ya hay una en curso para este id, esta sale en silencio.
        if (!runningIds.add(id)) {
            Log.i(TAG, "Descarga $id ya en curso, se omite duplicada")
            activeJobs.remove(id)
            return
        }
        try {
            if (code.isBlank() || vaultPath.isBlank()) {
                throw IllegalStateException("Datos de descarga incompletos")
            }
            // El motor no acepta un segundo programa con vista nueva en el mismo
            // proceso: si el reproductor está activo, no competir (error honesto).
            if (X4KPlayerAty.isPlayerActive) {
                throw IllegalStateException("Reproducción en curso: pausa el video e inténtalo de nuevo")
            }
            // Pausa pedida antes de encender nada: el ack ya lo escribió requestPause.
            if (pauseWins()) {
                Log.i(TAG, "Descarga $id pausada antes de arrancar el motor")
                return
            }
            updateNotif("Resolviendo $title…")

            // Fail-fast acotado: el portal puede tardar minutos con red mala; mejor un
            // ERROR honesto y reintento que un 0% infinito (congelaba la UI en POCO).
            var metaMedia = runCatching {
                withTimeout(60_000L) {
                    X4KApi.prepareVod(app, code, title, seriesId, durationMs)
                }
            }.getOrNull()
            if (metaMedia == null) {
                delay(450L)
                metaMedia = runCatching {
                    withTimeout(60_000L) {
                        X4KApi.prepareVod(app, code, title, seriesId, durationMs)
                    }
                }.getOrNull()
            }
            val mediasJson = metaMedia?.mediasJson.orEmpty()
            if (mediasJson.isBlank()) {
                throw IllegalStateException("Sin fuentes de descarga para este título en el catálogo")
            }

            val mediaSpecs = ArrayList<X4KTitan.MediaSpec>()
            val arr = JSONArray(mediasJson)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val n = o.optString("n")
                if (n.isBlank()) continue
                // Si el portal ya da http directo, sin motor de por medio.
                if (n.startsWith("http://") || n.startsWith("https://")) {
                    downloadCandidates(app, id, title, quality, dest, fallbackTotal, listOf(n))
                    return
                }
                mediaSpecs.add(
                    X4KTitan.MediaSpec(
                        name = n,
                        license = o.optString("l"),
                        audio = o.optString("a"),
                        quality = o.optString("q"),
                        encodeFormat = o.optString("e"),
                        videoFormat = o.optString("f"),
                    ),
                )
            }
            if (mediaSpecs.isEmpty()) {
                throw IllegalStateException("No se pudo establecer enlace con el servidor de descarga")
            }

            // Vía del reproductor a nivel facade: x(0, code, program) — la misma llamada
            // que ed.e.A() hace al abrir. Sin vista, sin Surface, sin audio.
            // Candado anti-carrera: si la pausa llegó justo ahora, el motor jamás se
            // enciende (y si ya estaba, requestPause lo suelta detrás de este candado).
            val started = synchronized(pauseLock) {
                if (pauseWins()) {
                    false
                } else {
                    resolveWithHeadlessEngine(app, code, title, mediaSpecs).also { ok ->
                        if (ok) activeCodes[id] = code
                    }
                }
            }
            if (!started) {
                if (pauseWins()) throw CancellationException("Descarga pausada: el motor pasa al reproductor")
                throw IllegalStateException("No se pudo resolver el stream (motor ocupado, inténtalo de nuevo)")
            }
            try {
                updateNotif("Conectando stream de $title…")
                val candidateUrls = pollPlayUrls(code, RESOLVE_TIMEOUT_MS)
                if (candidateUrls.isEmpty()) {
                    throw IllegalStateException("El servidor no entregó dirección de descarga (inténtalo de nuevo)")
                }
                downloadCandidates(app, id, title, quality, dest, fallbackTotal, candidateUrls)
            } finally {
                runCatching { X4KTitan.stopHeadless(code) }
            }
        } catch (e: Exception) {
            if (e is CancellationException || pauseWins()) {
                Log.i(TAG, "Descarga pausada o cancelada: $id")
                // El parcial cifrado no sirve (no hay reanudación por offset): fuera,
                // no dejes GB tirados cuando la pausa corta el stream antes del check.
                runCatching { File("$vaultPath.downloading").delete() }
            } else {
                Log.e(TAG, "Fallo descarga $id: ${e.message}", e)
                OfflineProgressBus.sendError(app, id, e.message ?: "Error de red al descargar")
                runCatching { dest.parentFile?.let { OfflineProgressFile.clear(it, id) } }
                runCatching { File("$vaultPath.downloading").delete() }
            }
        } finally {
            runningIds.remove(id)
            activeJobs.remove(id)
            activeCodes.remove(id)
            activeVaultDirs.remove(id)
            if (activeJobs.isEmpty()) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
        }
    }

    /**
     * Pausa pedida por el principal antes de reproducir en línea: cancela el job,
     * suelta el motor nativo DE INMEDIATO (el reproductor vive en este mismo proceso)
     * y confirma con el marcador `.paused` que el principal espera. Sin este aviso,
     * "pausar" dejaba al servicio bajando por detrás.
     */
    private fun requestPause(id: String, vaultPath: String) {
        // La marca gana antes que nada: si el intent de descarga se procesa después
        // (orden alterado), su corrida verá la pausa y no encenderá el motor.
        pauseRequestedAtMs[id] = System.currentTimeMillis()
        activeJobs.remove(id)?.cancel()
        synchronized(pauseLock) {
            releaseHeadlessLocked(id)
            val dir = activeVaultDirs[id]
                ?: vaultPath.takeIf { it.isNotBlank() }?.let { File(it).parentFile }
            if (dir != null) {
                OfflineProgressFile.clear(dir, id)
                OfflineProgressFile.writePauseFlag(dir, id)
            }
        }
    }

    /** Suelta el programa headless ya, sin esperar a que el job en vuelo se desmonte. */
    private fun releaseHeadlessNow(id: String) {
        synchronized(pauseLock) { releaseHeadlessLocked(id) }
    }

    private fun releaseHeadlessLocked(id: String) {
        val code = activeCodes[id].orEmpty()
        if (code.isNotBlank()) {
            runCatching { X4KTitan.stopHeadless(code) }
        }
    }

    /**
     * Configura el motor y arranca el programa vía facade (x(0, …)), sin vista.
     * Devuelve true si la orden se encoló; la URL real se sondea con GetStatus.
     * Bloqueante a propósito (sin suspensiones): puede correr bajo el candado de pausa.
     */
    private fun resolveWithHeadlessEngine(
        app: Context,
        code: String,
        title: String,
        medias: List<X4KTitan.MediaSpec>,
    ): Boolean {
        val apiPrefs = app.getSharedPreferences("x4k_api", Context.MODE_PRIVATE)
        val entriesJson = apiPrefs.getString("entries_json", null)
        val playParams = apiPrefs.getString("play_params", "").orEmpty()
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")
        val configured = runCatching {
            X4KTitan.ensureConfigured(
                context = app,
                userId = com.chronosbvrx.visionplus.data.xuper.X4KApi.userIdOf(),
                devId = com.chronosbvrx.visionplus.data.xuper.X4KApi.snOf(),
                androidId = androidId,
                entriesJson = entriesJson,
                playParams = playParams,
            )
        }.getOrDefault(false)
        if (!configured) return false
        if (X4KPlayerAty.isPlayerActive) return false
        return runCatching { X4KTitan.playHeadless(code, "vod", title, medias) }.getOrDefault(false)
    }

    /** Sondeo read-only de GetStatus hasta que Ranger publique URLs http. */
    private suspend fun pollPlayUrls(code: String, timeoutMs: Long): List<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val (playUrl, links) = runCatching { X4KTitan.queryStatusUrls(code) }
                .getOrDefault("" to emptyList())
            val out = ArrayList<String>()
            if (playUrl.startsWith("http")) out.add(playUrl)
            links.forEach { if (it.startsWith("http") && !out.contains(it)) out.add(it) }
            if (out.isNotEmpty()) return out
            delay(400L)
        }
        return emptyList()
    }

    private suspend fun downloadCandidates(
        app: Context,
        id: String,
        title: String,
        quality: String,
        dest: File,
        fallbackTotal: Long,
        candidateUrls: List<String>,
    ) {
        var lastErr: Exception? = null
        var downloadedBytes = 0L
        for (url in candidateUrls) {
            try {
                downloadedBytes = OfflineHttpDownloader.downloadUrlToVault(
                    url = url,
                    destinationFile = dest,
                    fallbackTotalBytes = fallbackTotal,
                    preferredQuality = quality,
                    onProgress = { written, total ->
                        OfflineProgressBus.sendProgress(app, id, written, total)
                        runCatching {
                            dest.parentFile?.let { OfflineProgressFile.write(it, id, written, total) }
                        }
                        updateNotifThrottled(title, written, total)
                    },
                    isCancelled = { activeJobs[id]?.isActive != true },
                )
                if (downloadedBytes >= MIN_VALID_VIDEO_BYTES) break
                dest.delete()
                throw IllegalStateException("Flujo de video incompleto ($downloadedBytes bytes)")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Intento falló en $url: ${e.message}")
                lastErr = e
            }
        }
        if (downloadedBytes < MIN_VALID_VIDEO_BYTES) {
            throw lastErr ?: IllegalStateException("No se pudo descargar el flujo de video")
        }
        OfflineProgressBus.sendDone(app, id, downloadedBytes)
        runCatching { dest.parentFile?.let { OfflineProgressFile.clear(it, id) } }
        Log.i(TAG, "Descarga completada: $title ($downloadedBytes bytes)")
    }

    private fun estimateTotalBytes(durationMs: Long, quality: String): Long {
        val secs = if (durationMs > 60_000L) (durationMs / 1000L) else (90L * 60L)
        val bps = when {
            quality.contains("1080") -> 240_000L
            quality.contains("720") -> 140_000L
            else -> 80_000L
        }
        return secs * bps
    }

    private fun ensureForeground(text: String) {
        createChannel()
        val notif = buildNotif(text, -1, -1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            startedForeground = true
        } catch (e: Exception) {
            Log.w(TAG, "foreground omitido: ${e.message}")
        }
    }

    private var lastNotifMs = 0L
    private fun updateNotifThrottled(title: String, written: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastNotifMs < 1500L) return
        lastNotifMs = now
        updateNotif("$title · ${OfflineStoragePolicy.formatBytes(written)} / ${OfflineStoragePolicy.formatBytes(total)}", written, total)
    }

    private fun updateNotif(text: String, written: Long = -1L, total: Long = -1L) {
        if (!startedForeground) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIF_ID, buildNotif(text, written, total))
    }

    private fun buildNotif(text: String, written: Long, total: Long): Notification {
        val percent = if (written >= 0 && total > 0) (written * 100L / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100) else -1
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (percent >= 0) "Vision+ · Descargando $percent%" else "Vision+ · Descarga offline")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (written >= 0 && total > 0) {
            builder.setProgress(total.toInt().coerceAtLeast(1), written.toInt().coerceAtLeast(0), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Descargas offline", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
