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
    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val CINEMETA = "https://v3-cinemeta.strem.io"
    private const val UA = "Vision+/Android External4K"

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Fuente fresca en caché, sin tocar la red. */
    fun fresh(context: Context, titleId: String): FourKSource? =
        External4KStore.fresh(context, titleId).firstOrNull()

    fun hasFresh(context: Context, titleId: String): Boolean = fresh(context, titleId) != null

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
        if (!inFlight.add(film.id)) return@withContext External4KStore.fresh(context, film.id)
        External4KStore.putFilm(context, film)
        try {
            val imdb = resolveImdb(context, film)
            if (imdb.isNullOrBlank()) {
                External4KStore.put(context, film.id, emptyList())
                return@withContext emptyList()
            }
            val body = getText("$BASE/u/$TOKEN/stream/movie/$imdb.json") ?: run {
                External4KStore.put(context, film.id, emptyList())
                return@withContext emptyList()
            }
            val now = System.currentTimeMillis()
            val candidates = External4KParser.parseStreams(body, now, TTL_MS)
            val verified = ArrayList<FourKSource>(candidates.size)
            for (candidate in candidates.take(4)) {
                if (!candidate.isHls || isReally4k(candidate.url)) verified += candidate
            }
            val final = verified.take(3)
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
        film.id.startsWith("xtv:") && film.kind == "movie"

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

    private fun isReally4k(url: String): Boolean = runCatching {
        val text = getText(url, 6_000) ?: return false
        External4KParser.maxHeightFromMaster(text) >= 2000
    }.getOrDefault(false)

    private fun getText(url: String, timeoutMs: Int = 8_000): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, application/vnd.apple.mpegurl, */*")
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
