package com.chronosbvrx.visionplus.data.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.data.xuper.X4KTitan
import com.chronosbvrx.visionplus.ui.player.X4KPlayerAty
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Flujo: `prepareVod` (portal) → vista headless + `X4KTitan.play` (idéntico al
 * player) → sondeo read-only de `GetStatus` hasta ver `play_url` http → descarga
 * +cifrado con [OfflineHttpDownloader] → reporte al principal vía
 * [OfflineProgressBus]. La reproducción normal no pasa por aquí.
 */
class X4KDownloadService : Service() {

    companion object {
        private const val TAG = "X4KDownloadService"

        const val ACTION_DOWNLOAD = "com.chronosbvrx.visionplus.action.OFFLINE_DOWNLOAD"
        const val ACTION_CANCEL = "com.chronosbvrx.visionplus.action.OFFLINE_CANCEL"

        const val EXTRA_ID = "download_id"
        const val EXTRA_CODE = "portal_code"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_VAULT_PATH = "vault_path"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_TOTAL_BYTES = "total_bytes"

        const val MIN_VALID_VIDEO_BYTES = 16 * 1024L
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
        }

        fun cancelIntent(context: Context, id: String): Intent =
            Intent(context, X4KDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var startedForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID).orEmpty()
                activeJobs[id]?.cancel()
                activeJobs.remove(id)
                if (activeJobs.isEmpty()) stopSelfResult(startId)
            }
            ACTION_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_ID).orEmpty()
                if (id.isBlank()) return START_NOT_STICKY
                if (activeJobs[id]?.isActive == true) return START_STICKY
                ensureForeground("Preparando descarga…")
                activeJobs[id] = scope.launch {
                    runDownload(intent)
                }
            }
        }
        return START_STICKY
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
        var headlessView: View? = null
        try {
            if (code.isBlank() || vaultPath.isBlank()) {
                throw IllegalStateException("Datos de descarga incompletos")
            }
            // El motor no acepta un segundo programa con vista nueva en el mismo
            // proceso: si el reproductor está activo, no competir (error honesto).
            if (X4KPlayerAty.isPlayerActive) {
                throw IllegalStateException("Reproducción en curso: pausa el video e inténtalo de nuevo")
            }
            updateNotif("Resolviendo $title…")

            var metaMedia = runCatching {
                X4KApi.prepareVod(app, code, title, seriesId, durationMs)
            }.getOrNull()
            if (metaMedia == null) {
                delay(450L)
                metaMedia = runCatching {
                    X4KApi.prepareVod(app, code, title, seriesId, durationMs)
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

            // Vía del reproductor: vista headless + view.A (la secuencia que sí funciona).
            val (view, started) = resolveWithHeadlessView(app, code, title, mediaSpecs)
            headlessView = view
            if (!started || view == null) {
                throw IllegalStateException("No se pudo resolver el stream (motor ocupado, inténtalo de nuevo)")
            }
            updateNotif("Conectando stream de $title…")
            val candidateUrls = pollPlayUrls(code, RESOLVE_TIMEOUT_MS)
            if (candidateUrls.isEmpty()) {
                throw IllegalStateException("El servidor no entregó dirección de descarga (inténtalo de nuevo)")
            }
            downloadCandidates(app, id, title, quality, dest, fallbackTotal, candidateUrls)
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.i(TAG, "Descarga cancelada: $id")
            } else {
                Log.e(TAG, "Fallo descarga $id: ${e.message}", e)
                OfflineProgressBus.sendError(app, id, e.message ?: "Error de red al descargar")
                runCatching { File("$vaultPath.downloading").delete() }
            }
        } finally {
            headlessView?.let { v ->
                runOnMain { runCatching { X4KTitan.stop(v) } }
            }
            activeJobs.remove(id)
            if (activeJobs.isEmpty()) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
        }
    }

    /**
     * Crea la vista en el hilo principal de `:x4k` (las vistas exigen Looper) y
     * lanza el programa con la misma llamada que el reproductor. Todo lo nativo
     * queda dentro de `view.A`, ya probado en producción.
     */
    private suspend fun resolveWithHeadlessView(
        app: Context,
        code: String,
        title: String,
        medias: List<X4KTitan.MediaSpec>,
    ): Pair<View?, Boolean> {
        val apiPrefs = app.getSharedPreferences("x4k_api", Context.MODE_PRIVATE)
        val entriesJson = apiPrefs.getString("entries_json", null)
        val playParams = apiPrefs.getString("play_params", "").orEmpty()
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")
        val configured = runCatching {
            X4KTitan.ensureConfigured(
                context = app,
                userId = "967111326",
                devId = "2392fe5186d83d73508a2c8c60a26ef6",
                androidId = androidId,
                entriesJson = entriesJson,
                playParams = playParams,
            )
        }.getOrDefault(false)
        if (!configured) return null to false
        if (X4KPlayerAty.isPlayerActive) return null to false

        val view = runOnMainSync { runCatching { X4KTitan.newVideoView(app) }.getOrNull() }
            ?: return null to false
        val started = runOnMainSync {
            runCatching { X4KTitan.play(view, "vod", code, title, medias) }.getOrDefault(false)
        } == true
        return view to started
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

    /** Ejecuta en el hilo principal de `:x4k` y espera el resultado (las vistas lo exigen). */
    private fun <T> runOnMainSync(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching { block() }.getOrNull()
        var result: T? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            result = runCatching { block() }.getOrNull()
            latch.countDown()
        }
        latch.await(15, TimeUnit.SECONDS)
        return result
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { block() }
        } else {
            Handler(Looper.getMainLooper()).post { runCatching { block() } }
        }
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vision+ · Descarga offline")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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
