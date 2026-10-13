package com.chronosbvrx.visionplus.data.offline

import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Descarga HTTP pura (MP4/MPEG-TS progresivo + HLS .m3u8) con cifrado al vuelo
 * hacia el búnker [OfflineCryptoVault]. Sin motor nativo: seguro en cualquier proceso.
 *
 * La reproducción no usa este objeto.
 */
object OfflineHttpDownloader {
    private const val TAG = "OfflineHttpDownloader"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun downloadUrlToVault(
        url: String,
        destinationFile: File,
        fallbackTotalBytes: Long,
        preferredQuality: String = "1080p",
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
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
                    destinationFile = destinationFile,
                    fallbackTotalBytes = fallbackTotalBytes,
                    preferredQuality = preferredQuality,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
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
                    onProgress?.invoke(written, dynamicTotal)
                },
                isCancelled = isCancelled,
            )
        }
    }

    internal data class HlsSegment(
        val url: String,
        val keyUrl: String? = null,
        val ivHex: String? = null,
        val sequence: Long = 0L,
    )

    private fun downloadHlsToVault(
        baseUrl: String,
        manifestText: String,
        destinationFile: File,
        fallbackTotalBytes: Long,
        preferredQuality: String,
        onProgress: ((written: Long, total: Long) -> Unit)?,
        isCancelled: (() -> Boolean)?,
    ): Long {
        var mediaPlaylistUrl = baseUrl
        var mediaPlaylistText = manifestText

        if (mediaPlaylistText.contains("#EXT-X-STREAM-INF")) {
            val variantUrl = selectHlsVariantUrl(baseUrl, mediaPlaylistText, preferredQuality)
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
                if (isCancelled?.invoke() == true) {
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
                    val segDone = currentSegmentIndex.coerceAtLeast(1)
                    val estimatedTotal = if (segDone >= totalSegments) {
                        written.coerceAtLeast(1L)
                    } else {
                        ((written.toDouble() / segDone.toDouble()) * totalSegments.toDouble()).toLong()
                            .coerceAtLeast(written + 1024L)
                    }
                    onProgress?.invoke(written, estimatedTotal)
                },
                isCancelled = isCancelled,
            )
        }
    }

    internal fun selectHlsVariantUrl(baseUrl: String, masterText: String, preferredQuality: String): String? {
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

    internal fun parseHlsSegments(baseUrl: String, playlistText: String): List<HlsSegment> {
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

    internal fun parseHlsIv(ivHex: String?, sequence: Long): ByteArray {
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

    internal fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
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
}
