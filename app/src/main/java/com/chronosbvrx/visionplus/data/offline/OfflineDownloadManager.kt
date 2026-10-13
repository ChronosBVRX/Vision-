package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.sources.PlaybackResolve
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.data.xuper.X4KTitan
import com.chronosbvrx.visionplus.ui.EpisodePlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Excepción lanzada cuando una Smart TV intenta descargar sin un medio de almacenamiento externo.
 */
class TvStorageRequiredException(message: String) : Exception(message)

/**
 * Gestor maestro del Modo Offline en Vision+:
 *
 * - Aplica de forma estricta las políticas de almacenamiento según dispositivo (Móvil vs Smart TV).
 * - Resuelve streams reales del catálogo XTV (vía X4KApi + motor nativo X4KTitan en modo exo HTTP)
 *   así como flujos progresivos MP4/TS y manifiestos HLS (.m3u8).
 * - Canaliza los bytes reales por el búnker criptográfico AES-256-CTR (OfflineCryptoVault) con
 *   progreso real byte a byte.
 */
object OfflineDownloadManager {

    private const val TAG = "OfflineDownloadManager"
    private const val MIN_VALID_VIDEO_BYTES = 16 * 1024L // Mínimo 16 KB para descartar respuestas vacías o truncadas

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Inicia la descarga de una película o episodio.
     * Retorna `true` si la descarga fue puesta en cola exitosamente, o lanza [TvStorageRequiredException] en TV sin USB.
     */
    fun enqueueDownload(
        context: Context,
        film: Film,
        episode: EpisodePlay? = null,
        quality: String = "1080p",
    ): Boolean {
        OfflineRegistry.init(context)

        // 1. Evaluar elegibilidad según dispositivo
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

        // 2. Construir metadatos de la descarga
        val isEpisode = episode != null
        val downloadId = if (isEpisode) {
            "${film.id}:ep:${episode.code}"
        } else {
            film.id
        }

        // Si ya está descargada o en curso, no duplicar
        if (OfflineRegistry.isDownloaded(downloadId, context)) return true
        if (OfflineRegistry.isDownloading(downloadId, context)) return true

        val safeFileName = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val vaultFile = File(targetVolume.vaultDir, "$safeFileName.vpack")

        val duration = if (isEpisode && episode.durationMs > 0L) {
            episode.durationMs
        } else if (film.durationMin > 0) {
            film.durationMin * 60_000L
        } else {
            0L
        }

        val episodeSubtitle = if (isEpisode) {
            val seasonLabel = if (episode.seasonIndex >= 0) "T${episode.seasonIndex + 1}" else ""
            val epLabel = "C${episode.episodeIndex + 1}"
            listOfNotNull(seasonLabel.ifBlank { null }, epLabel).joinToString(" · ")
        } else {
            ""
        }

        val rawStreamUrl = if (isEpisode) {
            if (episode.code.startsWith("xtv:") || episode.code.startsWith("http")) episode.code else "xtv:${episode.code}"
        } else {
            film.videoUrl.ifBlank { "xtv:${film.id}" }
        }

        val estimatedTotal = estimateTotalBytes(duration, quality)

        val item = OfflineDownload(
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
            totalBytes = estimatedTotal,
            vaultFilePath = vaultFile.absolutePath,
            storageLabel = targetVolume.label,
            isRemovable = targetVolume.isRemovable,
            isSeries = isEpisode || film.seasons.isNotEmpty(),
            seriesId = episode?.seriesId.orEmpty(),
            seasonIndex = episode?.seasonIndex ?: 0,
            episodeIndex = episode?.episodeIndex ?: 0,
            episodeName = episodeSubtitle,
        )

        OfflineRegistry.put(item, context)
        startWorkerJob(context.applicationContext, item)
        return true
    }

    private fun estimateTotalBytes(durationMs: Long, quality: String): Long {
        val effectiveDurationSec = if (durationMs > 60_000L) (durationMs / 1000L) else (90L * 60L)
        val bytesPerSec = when {
            quality.contains("1080") -> 240_000L // ~1.9 Mbps
            quality.contains("720") -> 140_000L  // ~1.1 Mbps
            else -> 80_000L                      // ~640 Kbps
        }
        return effectiveDurationSec * bytesPerSec
    }

    private fun startWorkerJob(context: Context, item: OfflineDownload) {
        val existingJob = activeJobs[item.id]
        if (existingJob != null && existingJob.isActive) return

        val job = scope.launch {
            var activeTitanCode = ""
            try {
                OfflineRegistry.updateState(item.id, DownloadState.DOWNLOADING)
                val destinationFile = File(item.vaultFilePath)

                // 1. Resolver lista de URLs candidatas de descarga
                val candidateUrls = ArrayList<String>()
                var resolvedUrl = item.videoUrl
                if (resolvedUrl.contains('|')) {
                    resolvedUrl = PlaybackResolve.urlFor(resolvedUrl)
                }

                if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
                    candidateUrls.add(resolvedUrl)
                } else {
                    val code = resolvedUrl
                        .removePrefix("xtv:")
                        .removePrefix("magisvod:")
                        .removePrefix("magis:")
                        .substringBefore('?')
                        .trim()
                    activeTitanCode = code

                    var metaMedia = runCatching {
                        X4KApi.prepareVod(
                            context = context,
                            contentId = code,
                            title = item.title,
                            seriesId = item.seriesId,
                            fallbackDurationMs = item.durationMs,
                        )
                    }.getOrNull()

                    if (metaMedia == null) {
                        delay(450L)
                        metaMedia = runCatching {
                            X4KApi.prepareVod(
                                context = context,
                                contentId = code,
                                title = item.title,
                                seriesId = item.seriesId,
                                fallbackDurationMs = item.durationMs,
                            )
                        }.getOrNull()
                    }

                    val mediasJson = metaMedia?.mediasJson.orEmpty()
                    if (mediasJson.isBlank()) {
                        throw IllegalStateException("No se encontraron fuentes de descarga para este título en el catálogo")
                    }

                    val arr = JSONArray(mediasJson)
                    val mediaSpecs = ArrayList<X4KTitan.MediaSpec>(arr.length())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val n = o.optString("n")
                        if (n.isBlank()) continue
                        if (n.startsWith("http://") || n.startsWith("https://")) {
                            candidateUrls.add(n)
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

                    if (candidateUrls.isEmpty() && mediaSpecs.isNotEmpty()) {
                        val resolvedStream = X4KTitan.resolveVodStream(
                            context = context,
                            code = code,
                            title = item.title,
                            medias = mediaSpecs,
                            preferredQuality = item.quality,
                        )
                        if (resolvedStream != null) {
                            if (resolvedStream.playUrl.startsWith("http")) {
                                candidateUrls.add(resolvedStream.playUrl)
                            }
                            resolvedStream.links.forEach { link ->
                                if (link.startsWith("http") && !candidateUrls.contains(link)) {
                                    candidateUrls.add(link)
                                }
                            }
                        }
                    }
                }

                if (candidateUrls.isEmpty()) {
                    throw IllegalStateException("No se pudo establecer enlace con el servidor de descarga")
                }

                // 2. Ejecutar descarga real desde la primera URL candidata funcional
                var lastErr: Exception? = null
                var downloadedBytes = 0L
                val fallbackEstimatedTotal = item.totalBytes.takeIf { it > 0L }
                    ?: estimateTotalBytes(item.durationMs, item.quality)

                for (url in candidateUrls) {
                    try {
                        downloadedBytes = downloadUrlToVault(
                            url = url,
                            item = item,
                            destinationFile = destinationFile,
                            fallbackTotalBytes = fallbackEstimatedTotal,
                        )
                        if (downloadedBytes >= MIN_VALID_VIDEO_BYTES) {
                            break
                        } else {
                            destinationFile.delete()
                            throw IllegalStateException("Flujo de video incompleto ($downloadedBytes bytes)")
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Intento de descarga falló en $url: ${e.message}")
                        lastErr = e
                    }
                }

                if (downloadedBytes < MIN_VALID_VIDEO_BYTES) {
                    throw lastErr ?: IllegalStateException("No se pudo descargar el flujo de video")
                }

                OfflineRegistry.updateProgress(item.id, downloadedBytes, downloadedBytes)
                OfflineRegistry.updateState(item.id, DownloadState.COMPLETED)
                Log.i(TAG, "Descarga offline completada exitosamente: ${item.title} (${item.id}, $downloadedBytes bytes)")
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.i(TAG, "Descarga pausada o cancelada: ${item.id}")
                } else {
                    Log.e(TAG, "Fallo al procesar descarga: ${item.id} - ${e.message}", e)
                    OfflineRegistry.updateState(item.id, DownloadState.ERROR, e.message ?: "Error de red al descargar")
                }
            } finally {
                if (activeTitanCode.isNotBlank()) {
                    X4KTitan.stopBackgroundVod(activeTitanCode)
                }
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    /**
     * Descarga desde una URL HTTP/HTTPS (soportando tanto flujos directos MP4/MPEG-TS como listas HLS .m3u8)
     * y cifra al vuelo hacia [destinationFile].
     */
    private fun downloadUrlToVault(
        url: String,
        item: OfflineDownload,
        destinationFile: File,
        fallbackTotalBytes: Long,
    ): Long {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Lavf/58.29.100")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "keep-alive")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error del servidor CDN: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Cuerpo de respuesta vacío del servidor")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val finalUrl = response.request.url.toString()

            val buffered = BufferedInputStream(body.byteStream(), 64 * 1024)
            buffered.mark(16)
            val peek = ByteArray(7)
            val peekRead = buffered.read(peek)
            buffered.reset()
            val startsWithExtM3u = peekRead >= 7 && String(peek, 0, 7, Charsets.US_ASCII) == "#EXTM3U"

            if (startsWithExtM3u || contentType.contains("mpegurl") || finalUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                val manifestText = buffered.bufferedReader(Charsets.UTF_8).readText()
                return downloadHlsToVault(
                    baseUrl = finalUrl,
                    manifestText = manifestText,
                    item = item,
                    destinationFile = destinationFile,
                    fallbackTotalBytes = fallbackTotalBytes,
                )
            }

            val contentLength = body.contentLength()
            val initialTotal = if (contentLength > 0L) contentLength else fallbackTotalBytes

            return OfflineCryptoVault.encryptStreamToFile(
                input = buffered,
                totalLength = initialTotal,
                destinationFile = destinationFile,
                onProgress = { written, total ->
                    val dynamicTotal = if (contentLength > 0L) {
                        contentLength
                    } else if (written >= (total * 92L) / 100L) {
                        (written * 115L) / 100L
                    } else {
                        total.coerceAtLeast(written + 1024L)
                    }
                    OfflineRegistry.updateProgress(item.id, written, dynamicTotal)
                },
                isCancelled = {
                    activeJobs[item.id]?.isActive != true
                },
            )
        }
    }

    private data class HlsSegment(
        val url: String,
        val keyUrl: String? = null,
        val ivHex: String? = null,
        val sequence: Long = 0L,
    )

    private fun downloadHlsToVault(
        baseUrl: String,
        manifestText: String,
        item: OfflineDownload,
        destinationFile: File,
        fallbackTotalBytes: Long,
    ): Long {
        var mediaPlaylistUrl = baseUrl
        var mediaPlaylistText = manifestText

        // 1. Si es un Master Playlist (#EXT-X-STREAM-INF), elegir la variante que coincida con item.quality
        if (mediaPlaylistText.contains("#EXT-X-STREAM-INF")) {
            val variantUrl = selectHlsVariantUrl(baseUrl, mediaPlaylistText, item.quality)
                ?: throw IllegalStateException("No se encontró variante de video en el manifiesto HLS")
            val req = Request.Builder()
                .url(variantUrl)
                .addHeader("User-Agent", "Lavf/58.29.100")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} al leer playlist HLS")
                mediaPlaylistUrl = resp.request.url.toString()
                mediaPlaylistText = resp.body?.string().orEmpty()
            }
        }

        // 2. Extraer segmentos de la Media Playlist
        val segments = parseHlsSegments(mediaPlaylistUrl, mediaPlaylistText)
        if (segments.isEmpty()) {
            throw IllegalStateException("La lista HLS no contiene segmentos de video descargables")
        }

        val totalSegments = segments.size
        var currentSegmentIndex = 0
        val keyCache = HashMap<String, ByteArray>()

        val hlsInputStream = object : InputStream() {
            private var segIndex = 0
            private var currentStream: InputStream? = null
            private var currentCallResponse: okhttp3.Response? = null

            private fun openNextSegment(): Boolean {
                closeCurrent()
                if (segIndex >= segments.size) return false
                if (activeJobs[item.id]?.isActive != true) {
                    throw CancellationException("Descarga cancelada por el usuario")
                }
                val seg = segments[segIndex]
                currentSegmentIndex = segIndex + 1
                segIndex++

                val req = Request.Builder()
                    .url(seg.url)
                    .addHeader("User-Agent", "Lavf/58.29.100")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    throw IllegalStateException("Error HTTP ${resp.code} en segmento $currentSegmentIndex/$totalSegments")
                }
                currentCallResponse = resp
                val rawStream = resp.body?.byteStream() ?: throw IllegalStateException("Segmento vacío")

                currentStream = if (!seg.keyUrl.isNullOrBlank()) {
                    val keyBytes = keyCache.getOrPut(seg.keyUrl) {
                        val kReq = Request.Builder().url(seg.keyUrl).addHeader("User-Agent", "Lavf/58.29.100").build()
                        httpClient.newCall(kReq).execute().use { kResp ->
                            kResp.body?.bytes() ?: throw IllegalStateException("No se pudo obtener clave HLS")
                        }
                    }
                    val ivBytes = parseHlsIv(seg.ivHex, seg.sequence)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
                    CipherInputStream(rawStream, cipher)
                } else {
                    rawStream
                }
                return true
            }

            override fun read(): Int {
                val b = ByteArray(1)
                val n = read(b, 0, 1)
                return if (n == -1) -1 else (b[0].toInt() and 0xFF)
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                while (true) {
                    val s = currentStream
                    if (s == null) {
                        if (!openNextSegment()) return -1
                        continue
                    }
                    val n = s.read(b, off, len)
                    if (n >= 0) return n
                    if (!openNextSegment()) return -1
                }
            }

            private fun closeCurrent() {
                runCatching { currentStream?.close() }
                runCatching { currentCallResponse?.close() }
                currentStream = null
                currentCallResponse = null
            }

            override fun close() {
                closeCurrent()
                super.close()
            }
        }

        hlsInputStream.use { stream ->
            return OfflineCryptoVault.encryptStreamToFile(
                input = stream,
                totalLength = fallbackTotalBytes,
                destinationFile = destinationFile,
                onProgress = { written, _ ->
                    // Calcular total estimado proporcional al avance de segmentos HLS
                    val segDone = currentSegmentIndex.coerceAtLeast(1)
                    val estimatedTotal = if (segDone >= totalSegments) {
                        written.coerceAtLeast(1L)
                    } else {
                        ((written.toDouble() / segDone.toDouble()) * totalSegments.toDouble()).toLong()
                            .coerceAtLeast(written + 1024L)
                    }
                    OfflineRegistry.updateProgress(item.id, written, estimatedTotal)
                },
                isCancelled = {
                    activeJobs[item.id]?.isActive != true
                },
            )
        }
    }

    private fun selectHlsVariantUrl(baseUrl: String, masterText: String, preferredQuality: String): String? {
        val lines = masterText.lines().map { it.trim() }
        val targetHeight = when {
            preferredQuality.contains("1080") -> 1080
            preferredQuality.contains("720") -> 720
            preferredQuality.contains("480") -> 480
            else -> 1080
        }
        data class Variant(val url: String, val height: Int, val bandwidth: Long)
        val variants = ArrayList<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val resMatch = Regex("""RESOLUTION=\d+x(\d+)""").find(line)
                val height = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(line)
                val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) {
                    j++
                }
                if (j < lines.size) {
                    variants.add(Variant(resolveRelativeUrl(baseUrl, lines[j]), height, bw))
                }
                i = j
            }
            i++
        }
        if (variants.isEmpty()) return null
        return variants.minByOrNull { v ->
            val hDiff = if (v.height > 0) kotlin.math.abs(v.height - targetHeight) else 500
            hDiff * 1_000_000L - (v.bandwidth / 1000L)
        }?.url
    }

    private fun parseHlsSegments(baseUrl: String, playlistText: String): List<HlsSegment> {
        val segments = ArrayList<HlsSegment>()
        var currentKeyUrl: String? = null
        var currentIvHex: String? = null
        var mediaSequence = 0L
        var segOffset = 0L

        for (rawLine in playlistText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSequence = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                    val mapUri = uriMatch?.groupValues?.get(1)
                    if (!mapUri.isNullOrBlank()) {
                        segments.add(
                            HlsSegment(
                                url = resolveRelativeUrl(baseUrl, mapUri),
                                keyUrl = currentKeyUrl,
                                ivHex = currentIvHex,
                                sequence = mediaSequence,
                            ),
                        )
                    }
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val methodMatch = Regex("""METHOD=([^,]+)""").find(line)
                    val method = methodMatch?.groupValues?.get(1)?.trim().orEmpty()
                    if (method.equals("AES-128", ignoreCase = true)) {
                        val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                        val keyUri = uriMatch?.groupValues?.get(1)
                        currentKeyUrl = if (!keyUri.isNullOrBlank()) resolveRelativeUrl(baseUrl, keyUri) else null
                        val ivMatch = Regex("""IV=0[xX]([0-9a-fA-F]+)""").find(line)
                        currentIvHex = ivMatch?.groupValues?.get(1)
                    } else {
                        currentKeyUrl = null
                        currentIvHex = null
                    }
                }
                !line.startsWith("#") -> {
                    segments.add(
                        HlsSegment(
                            url = resolveRelativeUrl(baseUrl, line),
                            keyUrl = currentKeyUrl,
                            ivHex = currentIvHex,
                            sequence = mediaSequence + segOffset,
                        ),
                    )
                    segOffset++
                }
            }
        }
        return segments
    }

    private fun parseHlsIv(ivHex: String?, sequence: Long): ByteArray {
        if (!ivHex.isNullOrBlank()) {
            val padded = ivHex.padStart(32, '0').takeLast(32)
            val out = ByteArray(16)
            for (i in 0 until 16) {
                out[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return out
        }
        val buf = java.nio.ByteBuffer.allocate(16)
        buf.putLong(0L)
        buf.putLong(sequence)
        return buf.array()
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        return runCatching {
            URI(baseUrl).resolve(relativeOrAbsolute).toString()
        }.getOrElse {
            val baseDir = baseUrl.substringBefore('?').substringBeforeLast('/')
            if (relativeOrAbsolute.startsWith("/")) {
                val u = URI(baseUrl)
                "${u.scheme}://${u.authority}$relativeOrAbsolute"
            } else {
                "$baseDir/$relativeOrAbsolute"
            }
        }
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        OfflineRegistry.updateState(id, DownloadState.PAUSED)
    }

    fun resumeDownload(context: Context, id: String) {
        val item = OfflineRegistry.get(id, context) ?: return
        if (item.state == DownloadState.PAUSED || item.state == DownloadState.ERROR) {
            OfflineRegistry.updateState(id, DownloadState.QUEUED)
            startWorkerJob(context.applicationContext, item)
        }
    }

    fun cancelDownload(id: String, context: Context? = null) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        OfflineRegistry.remove(id, context)
    }

    fun deleteDownload(context: Context, id: String) {
        cancelDownload(id, context)
    }
}
