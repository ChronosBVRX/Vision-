package com.chronosbvrx.visionplus.data.external

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Parseo puro del addon de Kronos (formato Stremio `{ streams: [...] }`).
 *
 * Solo interesan las pistas que declaran 2160p/4K/UHD. El addon mezcla
 * proveedores gratuitos (Vimeus, HackStore, UnLimPlay, PelisPlus) con respaldos
 * Torbox/DeepFlix; los últimos pueden venir en una IP privada del motor, así que
 * se marcan como `lan` y el reproductor decide si puede alcanzarlos.
 */
internal object External4KParser {

    private val IS_4K = Regex("""(?i)(?:\b2160p?\b|\b4k\b|\buhd\b)""")
    private val CAM = Regex("""(?i)(?:\bcam\b|telesync|\bts\b|hdcam)""")
    private val PRIVATE_HOST = Regex("""(?i)^https?://(?:192\.168\.|10\.|172\.(?:1[6-9]|2\d|3[01])\.)""")
    /** Pista de audio latino: «Latino», «LATAM», «Dual Lat», «es-MX» o «es-419». */
    private val LATINO_HINT = Regex(
        """(?i)(?:\blatino\b|\blatina\b|\blatam\b|\bdual[ ._-]?lat\b|\bes[- ]?419\b|\bes[- ]?mx\b)""",
    )
    /** Español genérico o castellano; nunca debe confundirse con subtítulos en inglés. */
    private val SPANISH_HINT = Regex("""(?i)(?:\bcastellano\b|\bespa[nñ]ol\b|\bspanish\b|\besp\b)""")

    private val PROVIDER = Regex("""^\[([^\]]*)]""", RegexOption.MULTILINE)

    /** `{ metas: [...] }` de Cinemeta → IMDb exacto por TMDB o por título/año. */
    fun imdbFromCinemeta(json: String, tmdbId: Int, title: String, year: Int): String? {
        val metas = runCatching { JSONObject(json).optJSONArray("metas") }.getOrNull() ?: return null
        val wantedTitle = normalizeTitle(title)
        var byTitle: String? = null
        for (i in 0 until metas.length()) {
            val meta = metas.optJSONObject(i) ?: continue
            val imdb = meta.optString("imdb_id").trim()
            if (!imdb.startsWith("tt")) continue
            if (tmdbId > 0 && meta.optInt("moviedb_id", 0) == tmdbId) return imdb
            val name = normalizeTitle(meta.optString("name"))
            if (name != wantedTitle || name.isBlank()) continue
            val metaYear = meta.optInt("year", 0)
            if (year > 0 && metaYear > 0 && kotlin.math.abs(metaYear - year) > 1) continue
            if (byTitle == null) byTitle = imdb
        }
        return byTitle
    }

    /**
     * Pistas 4K del addon. `ttlMs` es el techo de vida de una fuente; si la URL
     * ya trae `e=<segundos>` se respeta ese vencimiento (menor entre ambos).
     */
    fun parseStreams(json: String, nowMs: Long, ttlMs: Long): List<FourKSource> {
        val streams = runCatching { JSONObject(json).optJSONArray("streams") }.getOrNull() ?: return emptyList()
        val explicit = ArrayList<FourKSource>(4)
        val fallback = ArrayList<FourKSource>(8)
        val seen = HashSet<String>()
        for (i in 0 until streams.length()) {
            val stream = streams.optJSONObject(i) ?: continue
            val url = stream.optString("url").trim()
            if (!url.startsWith("http")) continue
            if (!seen.add(url)) continue
            val hints = stream.optJSONObject("behaviorHints")
            val filename = hints?.optString("filename").orEmpty()
            val sizeBytes = hints?.optLong("videoSize", 0L) ?: 0L
            val blob = "${stream.optString("name")} ${stream.optString("title")} " +
                "${stream.optString("description")} $filename"
            // Un HLS sin etiqueta puede ser 4K en su manifiesto: va a la lista de
            // respaldo y el repositorio lo comprueba. Un MP4 1080p no interesa.
            val hls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            // «4KHDHub» lleva 4K en el nombre del proveedor: no debe marcar como 4K
            // una pista 1080p. Se quita antes de buscar la calidad real.
            val qualityBlob = blob.replace("4khdhub", "", ignoreCase = true)
            val labeled = IS_4K.containsMatchIn(qualityBlob)
            if (!labeled && !hls) continue
            // Un 4K CAM sigue siendo peor que un 1080p limpio; se descarta.
            if (CAM.containsMatchIn(qualityBlob) && !qualityBlob.contains("2160p", ignoreCase = true)) continue
            val provider = providerOf(stream.optString("name"), stream.optString("title"))
            val language = languageOf(stream, blob)
            val expiry = urlExpiryMs(url, nowMs)?.let { minOf(it, nowMs + ttlMs) } ?: (nowMs + ttlMs)
            val source = FourKSource(
                url = url,
                provider = provider,
                language = language,
                filename = filename,
                lan = PRIVATE_HOST.containsMatchIn(url),
                sizeBytes = sizeBytes,
                checkedAt = nowMs,
                expiresAt = expiry,
                height = heightOf(qualityBlob),
            )
            if (labeled) explicit += source else fallback += source
            if (explicit.size >= 6) break
        }
        val out = explicit + fallback
        // Primero las que funcionan fuera de su red; dentro, latino antes que castellano
        // y, a igualdad, el archivo más ligero (un REMUX de 85 GB tarda más en arrancar).
        return out.sortedWith(
            compareBy<FourKSource> { it.lan }
                .thenBy { if (it.language == "Latino") 0 else if (it.language == "Castellano") 1 else 2 }
                .thenBy { if (it.sizeBytes > 0L) it.sizeBytes else Long.MAX_VALUE },
        ).take(4)
    }

    /**
     * Respaldos HLS 1080p/720p/480p del mismo addon. El 4K de R2 no siempre arranca;
     * estas pistas latinas permiten bajar de calidad sin salir del reproductor.
     */
    fun parseHlsQualities(json: String, nowMs: Long, ttlMs: Long): List<FourKSource> {
        val streams = runCatching { JSONObject(json).optJSONArray("streams") }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, FourKSource>()
        for (i in 0 until streams.length()) {
            val stream = streams.optJSONObject(i) ?: continue
            val url = stream.optString("url").trim()
            if (!url.startsWith("http")) continue
            if (!url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) continue
            val blob = "${stream.optString("name")} ${stream.optString("title")} ${stream.optString("description")}"
            val qualityBlob = blob.replace("4khdhub", "", ignoreCase = true)
            val height = heightOf(qualityBlob)
            if (height !in 480..1080) continue
            out[url] = FourKSource(
                url = url,
                provider = providerOf(stream.optString("name"), stream.optString("title")),
                language = languageOf(stream, blob),
                lan = PRIVATE_HOST.containsMatchIn(url),
                checkedAt = nowMs,
                expiresAt = urlExpiryMs(url, nowMs)?.let { minOf(it, nowMs + ttlMs) } ?: (nowMs + ttlMs),
                height = height,
            )
        }
        return out.values.sortedWith(
            compareByDescending<FourKSource> { it.height }
                .thenBy { if (it.language == "Latino") 0 else if (it.language == "Castellano") 1 else 2 }
                .thenBy { it.provider },
        ).take(4)
    }

    private fun languageOf(stream: JSONObject, blob: String): String {
        val declared = stream.optString("language").trim()
        return when {
            declared.equals("Latino", ignoreCase = true) -> "Latino"
            declared.equals("Castellano", ignoreCase = true) -> "Castellano"
            LATINO_HINT.containsMatchIn(blob) -> "Latino"
            SPANISH_HINT.containsMatchIn(blob) -> "Castellano"
            else -> "Original"
        }
    }

    private fun heightOf(text: String): Int {
        val q = text.lowercase(Locale.ROOT)
        return when {
            q.contains("2160") || q.contains("4k") || q.contains("uhd") -> 2160
            q.contains("1080") || q.contains("fhd") || q.contains("full hd") -> 1080
            q.contains("720") -> 720
            q.contains("480") -> 480
            else -> 0
        }
    }

    /** Nombre del proveedor: primera línea del `name` sin corchetes ni emojis. */
    fun providerOf(name: String, title: String): String {
        val cleanName = name.lineSequence().firstOrNull().orEmpty()
        if (cleanName.contains("4kHDHub", ignoreCase = true) || title.contains("4KHDHub", ignoreCase = true)) {
            return "4KHDHub"
        }
        PROVIDER.find(cleanName)?.groupValues?.getOrNull(1)
            ?.replace(Regex("""[^A-Za-z0-9 ._-]"""), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length >= 2 }
            ?.let { return it.take(32) }
        val fromTitle = title.lineSequence().lastOrNull().orEmpty()
            .substringAfterLast('·', "")
            .trim()
        return fromTitle.takeIf { it.isNotBlank() }?.take(32) ?: "4K externo"
    }

    /** Resolución declarada en un manifiesto HLS (0 si no hay). */
    fun maxHeightFromMaster(playlist: String): Int {
        var max = 0
        Regex("""(?i)RESOLUTION=\d+x(\d+)""").findAll(playlist).forEach { m ->
            m.groupValues.getOrNull(1)?.toIntOrNull()?.let { if (it > max) max = it }
        }
        Regex("""(?i)(?:^|[^0-9])(2160|1080|720|480)p?(?:$|[^0-9])""").findAll(playlist).forEach { m ->
            m.groupValues.getOrNull(1)?.toIntOrNull()?.let { if (it > max) max = it }
        }
        return max
    }

    fun normalizeTitle(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()

    /**
     * `e=` del CDN. En Kronos es un TTL en segundos (`e=43200` = 12 h); si algún
     * día llega como epoch, se respeta tal cual.
     */
    private fun urlExpiryMs(url: String, nowMs: Long): Long? {
        // URLs firmadas de R2 (HdHub): X-Amz-Expires son segundos desde ahora.
        Regex("""[?&]X-Amz-Expires=(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { return nowMs + it * 1000L }
        val e = Regex("""[?&]e=(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
        if (e <= 0L) return null
        return if (e >= 1_000_000_000_000L) e else nowMs + e * 1000L
    }

    @Suppress("unused")
    private fun arraysOf(json: String): JSONArray? = runCatching { JSONObject(json).optJSONArray("metas") }.getOrNull()
}
