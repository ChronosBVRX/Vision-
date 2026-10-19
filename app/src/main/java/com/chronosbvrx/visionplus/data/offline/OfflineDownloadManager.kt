package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.sources.PlaybackResolve
import com.chronosbvrx.visionplus.ui.EpisodePlay
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Excepción lanzada cuando una Smart TV intenta descargar sin un medio de almacenamiento externo.
 */
class TvStorageRequiredException(message: String) : Exception(message)

/**
 * Gestor del Modo Offline en Vision+ (proceso principal):
 *
 * REGLA INVIOLABLE: este objeto JAMÁS bootea ni toca el motor nativo Ranger
 * (`X4KTitan`). El motor vive solo en el proceso `:x4k` junto a Magis del
 * principal colisionarían y crashearían la app (SIGSEGV). Por eso:
 * - URLs http directas (Internet Archive): se descargan aquí con
 *   [OfflineHttpDownloader] (100% seguro, sin nativo).
 * - Refs `xtv:` / `magisvod:`: se delegan al [X4KDownloadService] (`:x4k`),
 *   que resuelve el stream real y reporta vía [OfflineProgressBus].
 *
 * La reproducción no pasa por aquí.
 */
object OfflineDownloadManager {

    private const val TAG = "OfflineDownloadManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val delegatedIds = ConcurrentHashMap.newKeySet<String>()

    // ---------------------------------------------------------------------------
    // Cola del motor nativo: UNA descarga `xtv:`/`magisvod:` a la vez.
    // El motor solo mantiene un programa: dos descargas simultáneas se pelearían
    // (la segunda mataría a la primera). El manager despacha una, y al llegar el
    // aviso DONE/ERROR del servicio `:x4k` arranca la siguiente. Las pendientes
    // quedan QUEUED en el registro y la UI las muestra «En cola».
    // ---------------------------------------------------------------------------
    private val engineLock = Any()
    private val remoteQueue = ArrayDeque<String>()
    private var activeRemoteId: String? = null
    private var appContext: Context? = null
    /** Vigilante de la descarga activa: proceso `:x4k` muerto o sin bytes por mucho rato. */
    private var watchdogJob: Job? = null

    /**
     * Inicia la descarga de una película o episodio.
     * Retorna `true` si fue puesta en cola, o lanza [TvStorageRequiredException] en TV sin USB.
     */
    fun enqueueDownload(
        context: Context,
        film: Film,
        episode: EpisodePlay? = null,
        quality: String = "1080p",
    ): Boolean {
        OfflineRegistry.init(context)
        appContext = context.applicationContext
        if (!com.chronosbvrx.visionplus.data.config.RemoteConfig.flag(context, "downloads_enabled", true)) {
            Log.w(TAG, "Descargas desactivadas por configuración remota")
            return false
        }

        val targetVolume = resolveTargetVolume(context) ?: return false

        val item = buildDownloadItem(film, episode, targetVolume, quality)

        if (OfflineRegistry.isDownloaded(item.id, context)) return true
        if (OfflineRegistry.isDownloading(item.id, context)) return true

        OfflineRegistry.put(item, context)
        dispatchWorker(context.applicationContext, item)
        return true
    }

    /**
     * Encola una TEMPORADA completa: cada capítulo es una descarga propia que se
     * descarga una por una (misma cola del motor). Los capítulos ya completados,
     * descargándose o en cola se omiten; los pausados o con error se reanudan.
     * Retorna cuántos capítulos quedaron encolados/reanudados en esta llamada.
     * Lanza [TvStorageRequiredException] en Smart TV sin memoria externa.
     */
    fun enqueueSeason(
        context: Context,
        film: Film,
        seasonId: String,
        seasonIndex: Int,
        episodeCodes: List<String>,
        durationMs: Long = 0L,
        quality: String = "1080p",
    ): Int {
        if (episodeCodes.isEmpty()) return 0
        OfflineRegistry.init(context)
        appContext = context.applicationContext
        if (!com.chronosbvrx.visionplus.data.config.RemoteConfig.flag(context, "downloads_enabled", true)) {
            Log.w(TAG, "Descargas desactivadas por configuración remota")
            return 0
        }
        val targetVolume = resolveTargetVolume(context) ?: return 0

        var accepted = 0
        episodeCodes.forEachIndexed { index, code ->
            if (code.isBlank()) return@forEachIndexed
            val ep = EpisodePlay(
                code = code,
                titleSuffix = "T${seasonIndex + 1} · C${index + 1}",
                seriesId = seasonId,
                episodeIndex = index,
                seasonIndex = seasonIndex,
                durationMs = durationMs,
            )
            val item = buildDownloadItem(film, ep, targetVolume, quality)
            when (OfflineRegistry.get(item.id, context)?.state) {
                DownloadState.COMPLETED, DownloadState.DOWNLOADING, DownloadState.QUEUED -> Unit
                DownloadState.PAUSED, DownloadState.ERROR -> {
                    delegatedIds.remove(item.id)
                    OfflineRegistry.updateState(item.id, DownloadState.QUEUED)
                    accepted++
                    OfflineRegistry.get(item.id, context)?.let {
                        dispatchWorker(context.applicationContext, it)
                    }
                }
                else -> {
                    OfflineRegistry.put(item, context)
                    accepted++
                    dispatchWorker(context.applicationContext, item)
                }
            }
        }
        Log.i(TAG, "temporada encolada: $accepted de ${episodeCodes.size} capítulos")
        return accepted
    }

    /** Volumen destino según la política (o lanza/nulo si no se puede). */
    private fun resolveTargetVolume(context: Context): StorageVolumeInfo? =
        when (val eligibility = OfflineStoragePolicy.evaluateEligibility(context)) {
            is StorageEligibility.DisallowedTvNeedsExternal -> {
                throw TvStorageRequiredException(
                    "Para proteger el rendimiento y la memoria de tu Smart TV, las descargas requieren una memoria USB o disco externo conectado.",
                )
            }
            is StorageEligibility.DisallowedInsufficientSpace -> {
                Log.w(TAG, "Espacio insuficiente para iniciar la descarga")
                null
            }
            is StorageEligibility.Allowed -> eligibility.target
        }

    /**
     * Construye el item de descarga (lógica pura, testeable): el episodio manda
     * sobre la película para que las series descarguen el C# elegido y no la serie.
     */
    internal fun buildDownloadItem(
        film: Film,
        episode: EpisodePlay?,
        target: StorageVolumeInfo,
        quality: String,
    ): OfflineDownload {
        val isEpisode = episode != null && episode.code.isNotBlank()
        val epCode = episode?.code.orEmpty()
        val downloadId = if (isEpisode) "${film.id}:ep:$epCode" else film.id

        val safeFileName = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val vaultFile = File(target.vaultDir, "$safeFileName.vpack")

        val duration = if (isEpisode && (episode?.durationMs ?: 0L) > 0L) {
            episode!!.durationMs
        } else if (film.durationMin > 0) {
            film.durationMin * 60_000L
        } else {
            0L
        }

        val episodeSubtitle = if (isEpisode) {
            val seasonLabel = if ((episode?.seasonIndex ?: -1) >= 0) "T${episode!!.seasonIndex + 1}" else ""
            val epLabel = "C${(episode?.episodeIndex ?: 0) + 1}"
            listOfNotNull(seasonLabel.ifBlank { null }, epLabel).joinToString(" · ")
        } else {
            ""
        }

        val rawStreamUrl = if (isEpisode) {
            if (epCode.startsWith("xtv:") || epCode.startsWith("http")) epCode else "xtv:$epCode"
        } else {
            film.videoUrl.ifBlank { "xtv:${film.id}" }
        }

        val seriesId = episode?.seriesId.orEmpty()
        return OfflineDownload(
            id = downloadId,
            contentId = film.id,
            title = film.title,
            subtitle = episodeSubtitle,
            posterUrl = film.posterUrl,
            videoUrl = rawStreamUrl,
            durationMs = duration,
            quality = quality,
            state = DownloadState.QUEUED,
            bytesDownloaded = 0L,
            totalBytes = estimateTotalBytes(duration, quality),
            vaultFilePath = vaultFile.absolutePath,
            storageLabel = target.label,
            isRemovable = target.isRemovable,
            isSeries = isEpisode || film.seasons.isNotEmpty(),
            seriesId = seriesId,
            seasonIndex = episode?.seasonIndex ?: 0,
            episodeIndex = episode?.episodeIndex ?: 0,
            episodeName = episode?.titleSuffix?.ifBlank { episodeSubtitle } ?: episodeSubtitle,
        )
    }

    /** True si la URL necesita resolución remota en `:x4k` (no es http directa). */
    internal fun needsRemoteResolve(videoUrl: String): Boolean {
        val u = videoUrl.substringBefore('|').trim()
        return !(u.startsWith("http://") || u.startsWith("https://"))
    }

    internal fun estimateTotalBytes(durationMs: Long, quality: String): Long {
        val effectiveDurationSec = if (durationMs > 60_000L) (durationMs / 1000L) else (90L * 60L)
        val bytesPerSec = when {
            quality.contains("1080") -> 240_000L
            quality.contains("720") -> 140_000L
            else -> 80_000L
        }
        return effectiveDurationSec * bytesPerSec
    }

    private fun dispatchWorker(context: Context, item: OfflineDownload) {
        appContext = context.applicationContext
        // El búnker también omite intro/créditos: deja el plan del capítulo en la caché
        // de disco mientras hay red (el reproductor offline no puede pedirlo después).
        if (item.isSeries && item.seriesId.isNotBlank()) {
            scope.launch {
                runCatching {
                    com.chronosbvrx.visionplus.data.markers.MarkersStore.prefetch(
                        context = context.applicationContext,
                        ref = item.seriesId,
                        episodes = listOf(item.episodeIndex + 1, item.episodeIndex + 2),
                        lengthSeconds = (item.durationMs / 1000L).toInt().coerceAtLeast(0),
                    )
                }
            }
        }
        val existingJob = activeJobs[item.id]
        if (existingJob != null && existingJob.isActive) return

        val app = context.applicationContext
        val job = scope.launch {
            com.chronosbvrx.visionplus.data.diag.Diagnostics.phase("descarga")
            try {
                var resolvedUrl = item.videoUrl
                if (resolvedUrl.contains('|')) {
                    resolvedUrl = runCatching { PlaybackResolve.urlFor(resolvedUrl) }.getOrDefault(resolvedUrl)
                }

                if (!needsRemoteResolve(resolvedUrl)) {
                    downloadDirectHttp(app, item, resolvedUrl)
                } else {
                    // El motor es uno: entra a la cola y el pump decide cuándo toca su turno.
                    queueRemote(app, item.id)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.i(TAG, "Descarga pausada o cancelada: ${item.id}")
                } else {
                    Log.e(TAG, "Fallo al procesar descarga: ${item.id} - ${e.message}", e)
                    com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                        "DOWNLOAD_FAIL", "${e.javaClass.simpleName}: ${e.message}",
                    )
                    OfflineRegistry.updateState(item.id, DownloadState.ERROR, e.message ?: "Error de red al descargar")
                }
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    /** Descarga http directa en el principal (sin nativo, sin crash). */
    private suspend fun downloadDirectHttp(context: Context, item: OfflineDownload, url: String) {
        val app = context.applicationContext
        try {
            OfflineRegistry.updateState(item.id, DownloadState.DOWNLOADING)
            val destinationFile = File(item.vaultFilePath)
            val fallbackTotal = item.totalBytes.takeIf { it > 0L }
                ?: estimateTotalBytes(item.durationMs, item.quality)
            val downloaded = OfflineHttpDownloader.downloadUrlToVault(
                url = url,
                destinationFile = destinationFile,
                fallbackTotalBytes = fallbackTotal,
                preferredQuality = item.quality,
                onProgress = { written, total ->
                    OfflineRegistry.updateProgress(item.id, written, total)
                },
                isCancelled = { activeJobs[item.id]?.isActive != true },
            )
            if (downloaded < MIN_VALID_VAULT_BYTES) {
                destinationFile.delete()
                throw IllegalStateException("Flujo de video incompleto ($downloaded bytes)")
            }
            OfflineRegistry.updateProgress(item.id, downloaded, downloaded)
            OfflineRegistry.updateState(item.id, DownloadState.COMPLETED)
            Log.i(TAG, "Descarga directa completada: ${item.title} ($downloaded bytes)")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        } finally {
            activeJobs.remove(item.id)
        }
    }

    // ---------------------------------------------------------------------- cola

    /** Mete una ref remota en la cola del motor y arranca la primera si no hay activa. */
    private fun queueRemote(app: Context, id: String) {
        synchronized(engineLock) {
            if (id != activeRemoteId && !remoteQueue.contains(id)) remoteQueue.addLast(id)
        }
        pumpRemote(app)
    }

    /**
     * Despacha la siguiente descarga de la cola (una sola por vez). Se salta las que ya no
     * estén en QUEUED (pausadas, canceladas o completadas por otro camino). Bucle, no
     * recursión: una cola de 50 capítulos con fallas no debe apilar 50 marcos de pila.
     */
    private fun pumpRemote(app: Context) {
        while (true) {
            val next: OfflineDownload
            synchronized(engineLock) {
                if (activeRemoteId != null) return
                var candidate: OfflineDownload? = null
                while (remoteQueue.isNotEmpty()) {
                    val id = remoteQueue.removeFirst()
                    val item = OfflineRegistry.get(id, app) ?: continue
                    if (item.state == DownloadState.QUEUED) {
                        candidate = item
                        break
                    }
                }
                if (candidate == null) return
                activeRemoteId = candidate.id
                next = candidate
            }
            try {
                dispatchRemoteNow(app, next)
                startWatchdog(app, next.id)
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Fallo al delegar descarga: ${next.id} - ${e.message}", e)
                OfflineRegistry.updateState(next.id, DownloadState.ERROR, e.message ?: "Error de red al descargar")
                synchronized(engineLock) {
                    if (activeRemoteId == next.id) activeRemoteId = null
                }
                watchdogJob?.cancel()
                // Sigue con la siguiente de la cola.
            }
        }
    }

    /**
     * El servicio `:x4k` terminó una descarga (DONE/ERROR por el bus): la activa queda
     * libre y arranca la siguiente de la cola. Lo llama [OfflineProgressReceiver].
     */
    internal fun onWorkerFinished(id: String) {
        val app = appContext ?: return
        delegatedIds.remove(id)
        scope.launch {
            val wasActive: Boolean
            synchronized(engineLock) {
                wasActive = id == activeRemoteId
                if (wasActive) activeRemoteId = null
            }
            if (!wasActive) return@launch
            Log.i(TAG, "fin del trabajador $id; siguiente de la cola")
            watchdogJob?.cancel()
            pumpRemote(app)
        }
    }

    /** Delega una ref `xtv:`/`magisvod:` al servicio `:x4k` (único con Ranger). */
    private fun dispatchRemoteNow(context: Context, item: OfflineDownload) {
        try {
            OfflineRegistry.updateState(item.id, DownloadState.DOWNLOADING)
            delegatedIds.add(item.id)
            // Corrida nueva: fuera marcas de resultado de corridas viejas (si el app murió
            // antes de consumirlas, envenenarían este intento).
            runCatching {
                File(item.vaultFilePath).parentFile?.let { OfflineProgressFile.clearOutcome(it, item.id) }
            }
            // «Camino B» (reproductor residente): si el proceso `:x4k` ya reprodujo con su
            // vista del motor, un programa headless ahí mezclaría historias del motor. Se
            // devuelve el proceso a limpio ANTES de encargar la descarga — solo desde el
            // principal: desde `:x4k` jamás se mata el proceso propio.
            resetX4kProcessForDownloadsIfNeeded(context)
            val code = item.videoUrl.substringBefore('|').trim()
                .removePrefix("xtv:").removePrefix("magisvod:").removePrefix("magis:")
                .substringBefore('?').trim()
            // Para series sin code de capítulo (ficha vieja), el servicio resuelve el C1 vía portal.
            val intent = X4KDownloadService.downloadIntent(
                context = context,
                id = item.id,
                code = code.ifBlank { item.contentId },
                title = item.title,
                seriesId = item.seriesId,
                quality = item.quality,
                vaultPath = item.vaultFilePath,
                durationMs = item.durationMs,
                totalBytes = item.totalBytes,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Descarga delegada a :x4k: ${item.id}")
        } catch (e: Exception) {
            delegatedIds.remove(item.id)
            throw e
        }
    }

    /**
     * Vigilante de la descarga activa: si el proceso `:x4k` murió (sin DONE/ERROR) o la
     * descarga lleva 5 minutos sin mover un byte, se marca ERROR honesto y la cola
     * continúa con el siguiente capítulo en vez de quedarse esperando para siempre.
     * Respaldo del bus: en equipos donde el broadcast DONE jamás llega (lección POCO/
     * HyperOS), el `.vpack` final válido y sin sidecar de progreso se adopta COMPLETED.
     */
    private fun startWatchdog(context: Context, id: String) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var lastBytes = OfflineRegistry.get(id, context)?.bytesDownloaded ?: 0L
            var idleTicks = 0
            var deadTicks = 0
            while (isActive) {
                delay(30_000L)
                val item = OfflineRegistry.get(id, context) ?: break
                if (item.state != DownloadState.DOWNLOADING) break
                val vault = File(item.vaultFilePath)
                val vaultDir = runCatching { vault.parentFile }.getOrNull()
                // Respaldo del bus de progreso: el archivo final ya está y su sidecar
                // desapareció (el servicio lo limpia al completar). Sin sidecar nuevo y
                // con el archivo quieto un minuto, la descarga terminó de verdad.
                if (vaultDir != null && OfflineProgressFile.read(vaultDir, id) == null &&
                    vault.exists() && vault.length() >= MIN_VALID_VAULT_BYTES &&
                    System.currentTimeMillis() - vault.lastModified() > 60_000L
                ) {
                    Log.i(TAG, "descarga $id completada por archivo (el bus no avisó)")
                    val len = vault.length()
                    OfflineRegistry.updateProgress(id, len, len)
                    OfflineRegistry.updateState(id, DownloadState.COMPLETED)
                    synchronized(engineLock) {
                        if (activeRemoteId == id) activeRemoteId = null
                    }
                    pumpRemote(context)
                    break
                }
                val bytes = item.bytesDownloaded
                if (bytes > lastBytes) {
                    lastBytes = bytes
                    idleTicks = 0
                } else {
                    idleTicks++
                }
                if (isX4kProcessAlive(context)) deadTicks = 0 else deadTicks++
                if (deadTicks >= 2 || idleTicks >= 10) {
                    Log.w(TAG, "descarga $id estancada (proceso muerto=$deadTicks, sin bytes=$idleTicks); se libera la cola")
                    runCatching {
                        context.startService(X4KDownloadService.pauseIntent(context, id, item.vaultFilePath))
                    }
                    OfflineRegistry.updateState(id, DownloadState.ERROR, "Descarga interrumpida · toca reintentar")
                    synchronized(engineLock) {
                        if (activeRemoteId == id) activeRemoteId = null
                    }
                    pumpRemote(context)
                    break
                }
            }
        }
    }

    private fun isX4kProcessAlive(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.runningAppProcesses?.any { it.processName.endsWith(":x4k") } == true
    }.getOrDefault(true)

    /** Saca de la cola una pendiente (sin tocar a la activa). */
    private fun dropQueuedRemote(id: String) {
        synchronized(engineLock) {
            remoteQueue.remove(id)
            if (id == activeRemoteId) {
                activeRemoteId = null
                watchdogJob?.cancel()
            }
        }
    }

    /**
     * Reproductor residente vs descargas: el motor headless exige un proceso en el que la
     * vista del reproductor no haya reproducido (una sola historia de motor por proceso).
     * La marca `x4k_view_played` la deja `:x4k` como ARCHIVO al crear su vista — archivo y
     * no SharedPreferences: la copia cacheada del principal puede no ver un valor recién
     * escrito por el otro proceso (misma lección que PlaybackHistory en 2.5.64). Con la
     * marca, el proceso `:x4k` se reinicia ENTERO (killBackgroundProcesses no toca procesos
     * percibibles: el propio proceso se apaga con la orden) y se espera a que muera para
     * que la descarga nazca en un proceso nuevo y limpio, como siempre fue.
     */
    private fun resetX4kProcessForDownloadsIfNeeded(context: Context) {
        val app = context.applicationContext
        val procName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()
        } else {
            ""
        }
        if (procName.endsWith(":x4k")) return
        val marker = java.io.File(app.filesDir, "x4k_view_played")
        if (!marker.exists()) return
        Log.i(TAG, "descarga: el proceso :x4k ya reprodujo con su vista; se reinicia limpio")
        runCatching { marker.delete() }
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        fun x4kAlive(): Boolean = runCatching {
            am?.runningAppProcesses?.any { it.processName.endsWith(":x4k") } == true
        }.getOrDefault(false)
        // Solo se ordena morir a un proceso vivo: un broadcast a un proceso muerto lo
        // despertaría… para que se mate (carrera absurda con el arranque del servicio).
        if (x4kAlive()) {
            runCatching {
                val die = Intent(app, com.chronosbvrx.visionplus.data.xuper.X4KWarmupReceiver::class.java).apply {
                    action = com.chronosbvrx.visionplus.data.xuper.X4KWarmupReceiver.ACTION_DIE_FOR_DOWNLOADS
                }
                app.sendBroadcast(die)
            }
            // Esperar a que muera (el servicio debe nacer en un proceso nuevo y limpio).
            val deadline = System.currentTimeMillis() + 4_000L
            while (System.currentTimeMillis() < deadline) {
                if (!x4kAlive()) break
                Thread.sleep(120L)
            }
        }
    }

    fun pauseDownload(id: String, context: Context? = null) {
        val app = context?.applicationContext
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val wasDelegated = delegatedIds.remove(id)
        // Cola del motor: pausar la descarga ACTIVA detiene la temporada entera (las
        // pendientes pasan a pausa); pausar una pendiente solo la saca de la cola.
        var wasActive = false
        val dropped = ArrayList<String>()
        synchronized(engineLock) {
            wasActive = id == activeRemoteId
            if (wasActive) {
                activeRemoteId = null
                while (remoteQueue.isNotEmpty()) dropped.add(remoteQueue.removeFirst())
            } else {
                while (remoteQueue.remove(id)) dropped.add(id)
            }
        }
        if (wasActive) watchdogJob?.cancel()
        val item = OfflineRegistry.get(id, context)
        if (app != null && item != null && (wasDelegated || wasActive)) {
            // Para refs `xtv:` el trabajo real vive en el servicio :x4k: sin este aviso,
            // "pausar" solo cambiaba la etiqueta y la descarga seguía por detrás.
            runCatching {
                app.startService(X4KDownloadService.pauseIntent(app, id, item.vaultFilePath))
            }
        }
        OfflineRegistry.updateState(id, DownloadState.PAUSED)
        dropped.forEach { OfflineRegistry.updateState(it, DownloadState.PAUSED) }
    }

    /**
     * Descargas que ocupan el motor nativo (refs `xtv:`/`magisvod:` en DOWNLOADING o esperando
     * su turno en la cola): mientras existan, el reproductor en línea no puede arrancar (mismo
     * motor, mismo proceso `:x4k`). Puro para testear.
     */
    internal fun engineDownloadsToPause(items: List<OfflineDownload>): List<OfflineDownload> =
        items.filter {
            (it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED) &&
                needsRemoteResolve(it.videoUrl)
        }

    /** La primera descarga que bloquea el motor nativo, o null si el motor está libre. */
    fun activeEngineDownload(context: Context): OfflineDownload? =
        engineDownloadsToPause(OfflineRegistry.getAll(context)).firstOrNull()

    /**
     * Pausa TODA descarga que esté ocupando (o espere ocupar) el motor antes de reproducir en
     * línea y espera el ack del servicio (`.paused`): al volver, `stopHeadless` ya corrió y el
     * reproductor puede arrancar sin cruzarse. Las pendientes de la cola se sueltan sin tocar
     * el servicio (aún no lo usaban). Devuelve true si el motor quedó libre.
     */
    suspend fun pauseEngineDownloadsForPlayback(context: Context, timeoutMs: Long = 12_000L): Boolean =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            var allFree = true
            for (item in engineDownloadsToPause(OfflineRegistry.getAll(app))) {
                if (item.state == DownloadState.QUEUED) {
                    // Todavía no toca el motor: basta con soltarla de la cola y pausarla.
                    dropQueuedRemote(item.id)
                    OfflineRegistry.updateState(item.id, DownloadState.PAUSED)
                } else {
                    if (!pauseForPlayback(app, item, timeoutMs)) allFree = false
                }
            }
            allFree
        }

    private suspend fun pauseForPlayback(app: Context, item: OfflineDownload, timeoutMs: Long): Boolean {
        val dir = runCatching { File(item.vaultFilePath).parentFile }.getOrNull()
        activeJobs[item.id]?.cancel()
        activeJobs.remove(item.id)
        delegatedIds.remove(item.id)
        synchronized(engineLock) {
            if (activeRemoteId == item.id) {
                activeRemoteId = null
                remoteQueue.clear()
            } else {
                remoteQueue.remove(item.id)
            }
        }
        watchdogJob?.cancel()
        // Marcador viejo fuera: se espera el NUEVO ack de esta pausa.
        runCatching { dir?.let { OfflineProgressFile.clearPauseFlag(it, item.id) } }
        OfflineRegistry.updateState(item.id, DownloadState.PAUSED)
        if (!needsRemoteResolve(item.videoUrl)) return true
        runCatching {
            app.startService(X4KDownloadService.pauseIntent(app, item.id, item.vaultFilePath))
        }
        if (dir == null) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (OfflineProgressFile.hasPauseFlag(dir, item.id)) {
                OfflineProgressFile.clearPauseFlag(dir, item.id)
                return true
            }
            delay(150L)
        }
        runCatching { OfflineProgressFile.clearPauseFlag(dir, item.id) }
        return false
    }

    fun resumeDownload(context: Context, id: String) {
        val item = OfflineRegistry.get(id, context) ?: return
        if (item.state == DownloadState.PAUSED || item.state == DownloadState.ERROR) {
            delegatedIds.remove(id)
            activeJobs.remove(id)
            OfflineRegistry.updateState(id, DownloadState.QUEUED)
            dispatchWorker(context.applicationContext, item)
        }
    }

    fun cancelDownload(id: String, context: Context? = null) {
        val app = context?.applicationContext
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val wasDelegated = delegatedIds.remove(id)
        var wasActive = false
        synchronized(engineLock) {
            wasActive = id == activeRemoteId
            remoteQueue.remove(id)
            if (wasActive) activeRemoteId = null
        }
        if ((wasDelegated || wasActive) && app != null) {
            runCatching {
                app.startService(X4KDownloadService.cancelIntent(app, id))
            }
        }
        val doomed = OfflineRegistry.get(id, context)
        if (doomed != null) {
            runCatching {
                File(doomed.vaultFilePath).parentFile?.let {
                    OfflineProgressFile.clear(it, id)
                    OfflineProgressFile.clearOutcome(it, id)
                }
            }
            // El parcial cifrado no sirve tras borrar (no hay reanudación por offset).
            runCatching { File("${doomed.vaultFilePath}.downloading").delete() }
        }
        OfflineRegistry.remove(id, context)
        if (wasActive) {
            // Borrar el capítulo en curso no tumba la temporada: sigue el siguiente.
            watchdogJob?.cancel()
            if (app != null) scope.launch { pumpRemote(app) }
        }
    }

    fun deleteDownload(context: Context, id: String) {
        cancelDownload(id, context)
    }
}
