package com.chronosbvrx.visionplus.data.external

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.Film
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Busca, valida y cachea fuentes 4K para una película.
 *
 * Flujo: TMDB/título → IMDb (Cinemeta) → addon de Kronos → pistas 2160p →
 * comprobación real del manifiesto HLS → caché. Todo en el dispositivo; si algo
 * falla se guarda un negativo corto y la ficha sigue con Xuper.
 */
internal object External4KRepository {
    private const val TAG = "External4K"
    private const val BASE = "https://addons.4rever.cc"
    private const val TOKEN = "kronos_master"
    /** HTTP directo y público (R2 firmado). Es la vía que funciona fuera de una LAN. */
    private const val HDHUB =
        "https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyJ9"
    private val PROVIDERS = listOf(HDHUB, "$BASE/u/$TOKEN")
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val CINEMETA = "https://v3-cinemeta.strem.io"
    private const val UA = "Vision+/Android External4K"

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lowerCache = ConcurrentHashMap<String, Pair<Long, List<FourKSource>>>()
    private const val LOWER_TTL_MS = 30 * 60 * 1000L

    /** Fuente fresca en caché, sin tocar la red. */
    fun fresh(context: Context, titleId: String): FourKSource? =
        External4KStore.fresh(context, titleId).firstOrNull()

    /** La fuente 4K real (2160p) en cach??: las calidades bajas no encienden el badge. */
    fun fresh4k(context: Context, titleId: String): FourKSource? {
        val all = External4KStore.fresh(context, titleId).filter { it.height >= 2000 }
        // El CDN (R2) abre mas rapido que los hosts de archivos; si no hay, se usa el resto.
        return all.firstOrNull { !it.url.contains("pixeldrain", ignoreCase = true) }
            ?: all.firstOrNull()
    }

    fun hasFresh(context: Context, titleId: String): Boolean = fresh4k(context, titleId) != null

    /** Uso desde la ficha: refresca solo si la entrada venció. */
    suspend fun refreshIfStale(context: Context, film: Film): List<FourKSource> {
        if (!supports(film)) return emptyList()
        val cached = External4KStore.fresh(context, film.id)
        if (cached.isNotEmpty()) return cached
        if (External4KStore.checkedRecently(context, film.id)) return emptyList()
        return refreshNow(context, film)
    }

    suspend fun refreshNow(context: Context, film: Film): List<FourKSource> = withContext(Dispatchers.IO) {
        if (!supports(film)) return@withContext emptyList()
        Log.i(TAG, "refresh id=${film.id} kind=${film.kind} tmdb=${film.tmdbId} title=${film.title}")
        if (!inFlight.add(film.id)) return@withContext External4KStore.fresh(context, film.id)
        External4KStore.putFilm(context, film)
        try {
            var knownImdb = External4KStore.imdb(context, film.id)
                .takeIf { it.startsWith("tt") }
                .orEmpty()
            val now = System.currentTimeMillis()
            val verified = ArrayList<FourKSource>(4)
            // El VPS resuelve el IMDb (TMDB) y HdHub con su IP: el addon limita por IP
            // y el movil no debe depender de su propia cuota.
            fun queryFor(imdb: String): String = buildString {
                append(com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.BASE_URL)
                append("/v1/4k?")
                if (imdb.isNotBlank()) append("imdb=").append(imdb).append('&')
                append("tmdb=").append(film.tmdbId).append('&')
                append("title=").append(URLEncoder.encode(film.title, "UTF-8")).append('&')
                append("original=").append(URLEncoder.encode(film.originalTitle, "UTF-8")).append('&')
                append("year=").append(film.year)
            }
            fun fetchCandidates(imdb: String): List<FourKSource> {
                val body = getText(
                    queryFor(imdb),
                    timeoutMs = 22_000,
                    headers = mapOf(
                        "X-Api-Key" to com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.API_KEY,
                    ),
                ) ?: return emptyList()
                return External4KParser.parseStreams(body, now, TTL_MS)
            }
            var candidates = fetchCandidates(knownImdb)
            if (candidates.isEmpty() && knownImdb.isNotBlank()) {
                // Un IMDb guardado de una ficha anterior (el portal reusa codigos) hace
                // que el VPS devuelva 0. Se reintenta por TMDB/titulo y se limpia el viejo.
                Log.i(TAG, "4K: IMDb $knownImdb sin fuentes; reintento por tmdb/titulo")
                candidates = fetchCandidates("")
                if (candidates.isNotEmpty()) {
                    External4KStore.clearImdb(context, film.id)
                    knownImdb = ""
                }
            }
            Log.i(TAG, "4K VPS: ${candidates.size} candidatos")
            for (candidate in candidates.take(6)) {
                if (isUsable(candidate)) {
                    verified += candidate.verified4k()
                    if (verified.size >= 3) break
                }
            }
            val imdb = knownImdb.ifBlank { resolveImdb(context, film) }
            if (verified.isEmpty() && imdb.isNullOrBlank()) {
                Log.i(TAG, "sin IMDb para ${film.id} (${film.title})")
                External4KStore.put(context, film.id, emptyList())
                return@withContext emptyList()
            }
            for (base in PROVIDERS) {
                if (verified.size >= 2) break
                val body = getText("$base/stream/movie/$imdb.json") ?: continue
                val candidates = External4KParser.parseStreams(body, now, TTL_MS)
                for (candidate in candidates.take(4)) {
                    if (verified.any { it.url == candidate.url }) continue
                    if (isUsable(candidate)) verified += candidate.verified4k()
                    if (verified.size >= 3) break
                }
            }
            val final = verified.take(3)
            Log.i(TAG, "4K ${film.id}: ${final.size} fuentes (${final.count { it.lan }} LAN)")
            External4KStore.put(context, film.id, final)
            if (final.isNotEmpty()) {
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "EXTERNAL_4K_OK",
                    "id=${film.id} pistas=${final.size} lan=${final.count { it.lan }}",
                )
            }
            final
        } catch (e: Exception) {
            Log.w(TAG, "fallo 4K para ${film.id}: ${e.message}")
            External4KStore.put(context, film.id, emptyList())
            emptyList()
        } finally {
            inFlight.remove(film.id)
        }
    }

    private fun supports(film: Film): Boolean =
        film.id.startsWith("xtv:") && (film.kind.isBlank() || film.kind == "movie")

    private suspend fun resolveImdb(context: Context, film: Film): String? {
        External4KStore.imdb(context, film.id).takeIf { it.startsWith("tt") }?.let { return it }
        val titles = listOf(film.originalTitle, film.title).filter { it.isNotBlank() }.distinct()
        for (query in titles) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val body = getText("$CINEMETA/catalog/movie/top/search=$encoded.json") ?: continue
            val imdb = External4KParser.imdbFromCinemeta(body, film.tmdbId, query, film.year) ?: continue
            External4KStore.putImdb(context, film.id, imdb)
            return imdb
        }
        return null
    }

    /**
     * Respaldos HLS 1080p/720p/480p del addon de Kronos (audio latino). Se piden en
     * segundo plano al abrir el 4K para que el menú de calidad tenga a dónde bajar
     * si la carga se traba. Caché de 30 min por ficha.
     */
    suspend fun lowerQualities(context: Context, film: Film): List<FourKSource> = withContext(Dispatchers.IO) {
        if (!supports(film)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        lowerCache[film.id]?.let { (at, cached) ->
            if (now - at < LOWER_TTL_MS) return@withContext cached
        }
        val imdb = External4KStore.imdb(context, film.id)
            .takeIf { it.startsWith("tt") }
            ?: resolveImdb(context, film)
            ?: return@withContext emptyList()
        val body = getText("$BASE/stream/movie/$imdb.json", timeoutMs = 10_000)
            ?: return@withContext emptyList()
        val qualities = External4KParser.parseHlsQualities(body, now, TTL_MS)
            .mapNotNull { source ->
                // La etiqueta del addon (FHD/720P) no siempre coincide con el master:
                // se lee el manifiesto y se usa la altura REAL de sus renditiones.
                val master = getText(source.url, 5_000) ?: return@mapNotNull null
                val height = External4KParser.maxHeightFromMaster(master)
                    .takeIf { it in 480..1080 } ?: source.height
                source.copy(height = height)
            }
            .filter { it.height in 480..1080 }
            .distinctBy { it.height }
            .take(3)
        Log.i(TAG, "respaldos ${film.id}: ${qualities.joinToString { "${it.height}p/${it.provider}" }}")
        lowerCache[film.id] = now to qualities
        qualities
    }

    /**
     * Un candidato sirve si es LAN o si responde desde esta caja. Un HLS ademas tiene
     * que declarar 2160p en su manifiesto. Se registra el motivo para diagnosticar TVs
     * que no alcanzan ciertos CDN.
     */
    private fun isUsable(candidate: FourKSource): Boolean {
        if (candidate.lan) {
            Log.i(TAG, "4K candidato LAN ${candidate.provider} ${candidate.height}p usable=true")
            return true
        }
        val hlsOk = !candidate.isHls || isReally4k(candidate.url)
        val reachable = hlsOk && isReachable(candidate.url)
        Log.i(TAG, "4K candidato ${candidate.provider} ${candidate.height}p hls=${candidate.isHls} hlsOk=$hlsOk reachable=$reachable url=${candidate.url.take(90)}")
        return hlsOk && reachable
    }

    private fun FourKSource.verified4k(): FourKSource =
        if (height >= 2000) this else copy(height = 2160)

    /**
     * Comprueba que el CDN responda. R2 a veces rechaza HEAD en URLs firmadas, así
     * que se reintenta con un GET de 1 KB (el mismo método que usará el player).
     */
    private fun isReachable(url: String, timeoutMs: Int = 5_000): Boolean =
        probe(url, "HEAD", timeoutMs) || probe(url, "GET", timeoutMs)

    private fun probe(url: String, method: String, timeoutMs: Int = 5_000): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            if (method == "GET") setRequestProperty("Range", "bytes=0-1024")
        }
        try {
            val code = conn.responseCode
            Log.i(TAG, "probe $method ${url.take(80)} -> $code")
            val ok = code in 200..299
            if (ok) runCatching { conn.inputStream?.close() }
            ok
        } finally {
            conn.disconnect()
        }
    }.getOrElse { e ->
        Log.i(TAG, "probe $method ${url.take(80)} -> ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    private fun isReally4k(url: String): Boolean = runCatching {
        val text = getText(url, 6_000) ?: return false
        External4KParser.maxHeightFromMaster(text) >= 2000
    }.getOrDefault(false)

    private fun getText(
        url: String,
        timeoutMs: Int = 8_000,
        headers: Map<String, String> = emptyMap(),
    ): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, application/vnd.apple.mpegurl, */*")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    @Suppress("unused")
    private fun sanity(json: String): Boolean = runCatching {
        JSONObject(json).has("streams")
    }.getOrDefault(false)
}
