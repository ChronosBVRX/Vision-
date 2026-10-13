package com.chronosbvrx.visionplus.data.offline

import android.content.Context
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

        val eligibility = OfflineStoragePolicy.evaluateEligibility(context)
        val targetVolume = when (eligibility) {
            is StorageEligibility.DisallowedTvNeedsExternal -> {
                throw TvStorageRequiredException(
                    "Para proteger el rendimiento y la memoria de tu Smart TV, las descargas requieren una memoria USB o disco externo conectado.",
                )
            }
            is StorageEligibility.DisallowedInsufficientSpace -> {
                Log.w(TAG, "Espacio insuficiente para iniciar la descarga")
                return false
            }
            is StorageEligibility.Allowed -> eligibility.target
        }

        val item = buildDownloadItem(film, episode, targetVolume, quality)

        if (OfflineRegistry.isDownloaded(item.id, context)) return true
        if (OfflineRegistry.isDownloading(item.id, context)) return true

        OfflineRegistry.put(item, context)
        dispatchWorker(context.applicationContext, item)
        return true
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
        val existingJob = activeJobs[item.id]
        if (existingJob != null && existingJob.isActive) return
        if (delegatedIds.contains(item.id)) return

        val job = scope.launch {
            try {
                var resolvedUrl = item.videoUrl
                if (resolvedUrl.contains('|')) {
                    resolvedUrl = runCatching { PlaybackResolve.urlFor(resolvedUrl) }.getOrDefault(resolvedUrl)
                }

                if (!needsRemoteResolve(resolvedUrl)) {
                    downloadDirectHttp(context, item, resolvedUrl)
                } else {
                    delegateToX4kService(context, item)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.i(TAG, "Descarga pausada o cancelada: ${item.id}")
                } else {
                    Log.e(TAG, "Fallo al procesar descarga: ${item.id} - ${e.message}", e)
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

    /** Delega refs xtv:/magisvod: al servicio `:x4k` (único con Ranger). */
    private fun delegateToX4kService(context: Context, item: OfflineDownload) {
        try {
            OfflineRegistry.updateState(item.id, DownloadState.DOWNLOADING)
            delegatedIds.add(item.id)
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
        } finally {
            activeJobs.remove(item.id)
        }
    }

    fun pauseDownload(id: String, context: Context? = null) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val wasDelegated = delegatedIds.remove(id)
        val item = OfflineRegistry.get(id, context)
        if (context != null && item != null && (wasDelegated || needsRemoteResolve(item.videoUrl))) {
            // Para refs `xtv:` el trabajo real vive en el servicio :x4k: sin este aviso,
            // "pausar" solo cambiaba la etiqueta y la descarga seguía por detrás.
            runCatching {
                val app = context.applicationContext
                app.startService(X4KDownloadService.pauseIntent(app, id, item.vaultFilePath))
            }
        }
        OfflineRegistry.updateState(id, DownloadState.PAUSED)
    }

    /**
     * Descargas que ocupan el motor nativo (refs `xtv:`/`magisvod:` en DOWNLOADING):
     * mientras existan, el reproductor en línea no puede arrancar (mismo motor, mismo
     * proceso `:x4k`). Puro para testear.
     */
    internal fun engineDownloadsToPause(items: List<OfflineDownload>): List<OfflineDownload> =
        items.filter { it.state == DownloadState.DOWNLOADING && needsRemoteResolve(it.videoUrl) }

    /** La primera descarga que bloquea el motor nativo, o null si el motor está libre. */
    fun activeEngineDownload(context: Context): OfflineDownload? =
        engineDownloadsToPause(OfflineRegistry.getAll(context)).firstOrNull()

    /**
     * Pausa TODA descarga que esté ocupando el motor antes de reproducir en línea y
     * espera el ack del servicio (`.paused`): al volver, `stopHeadless` ya corrió y el
     * reproductor puede arrancar sin cruzarse. Devuelve true si el motor quedó libre.
     */
    suspend fun pauseEngineDownloadsForPlayback(context: Context, timeoutMs: Long = 12_000L): Boolean =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            var allFree = true
            for (item in engineDownloadsToPause(OfflineRegistry.getAll(app))) {
                if (!pauseForPlayback(app, item, timeoutMs)) allFree = false
            }
            allFree
        }

    private suspend fun pauseForPlayback(app: Context, item: OfflineDownload, timeoutMs: Long): Boolean {
        val dir = runCatching { File(item.vaultFilePath).parentFile }.getOrNull()
        activeJobs[item.id]?.cancel()
        activeJobs.remove(item.id)
        delegatedIds.remove(item.id)
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
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        if (delegatedIds.remove(id)) {
            context?.let {
                runCatching {
                    it.applicationContext.startService(X4KDownloadService.cancelIntent(it.applicationContext, id))
                }
            }
        }
        val doomed = OfflineRegistry.get(id, context)
        if (doomed != null) {
            runCatching {
                File(doomed.vaultFilePath).parentFile?.let { OfflineProgressFile.clear(it, id) }
            }
            // El parcial cifrado no sirve tras borrar (no hay reanudación por offset).
            runCatching { File("${doomed.vaultFilePath}.downloading").delete() }
        }
        OfflineRegistry.remove(id, context)
    }

    fun deleteDownload(context: Context, id: String) {
        cancelDownload(id, context)
    }
}
