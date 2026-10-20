package com.chronosbvrx.visionplus.data.mirror

import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.xuper.xtvPortalId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Espejo de capítulos/temporadas — cliente de la app.
 *
 * El worker del servidor baja UNA vez (con SU sesión del portal) la lista de
 * capítulos de cada temporada y el API la sirve en `/v1/episodes`. Aquí solo se
 * consulta esa copia: ~3 ms en lugar de los 0.2–0.7 s del portal por cada
 * aparato que abre la ficha. La promesa es dura: **el espejo solo puede
 * mejorar, jamás empeorar** — cualquier fallo, ausencia o copia vacía devuelve
 * `null` y `X4KApi.episodeProgramsFor` cae al portal EXACTO de siempre.
 *
 * Vive bien en el proceso `:x4k` (HttpURLConnection, sin estado compartido) y
 * sin tocar el arranque en frío: solo se usa cuando una ficha/reproductor pide
 * capítulos, que es justo lo que hoy golpea el portal.
 */
object EpisodeMirror {

    private const val TAG = "EpisodeMirror"

    /** Tope de espera: si el servidor no contesta enseguida, manda el portal. */
    internal const val CONNECT_TIMEOUT_MS = 2_000
    internal const val READ_TIMEOUT_MS = 2_500

    /** TTL de la copia en RAM: la ficha recién vista no vuelve a tocar la red. */
    internal const val CACHE_TTL_MS = 30L * 60 * 1000
    internal const val CACHE_MAX_ENTRIES = 12

    /** LRU mínima: la ficha abierta y la siguiente capítulo de la serie quedan calientes. */
    private val cache = object : LinkedHashMap<String, Pair<Long, JSONArray>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JSONArray>>?): Boolean =
            size > CACHE_MAX_ENTRIES
    }

    /** Ref del espejo: `xtv:<cid>` (misma regla del índice; el servidor acepta ambas). */
    internal fun mirrorRef(seriesId: String): String = "xtv:${xtvPortalId(seriesId)}"

    /** ¿Sigue viva la copia en RAM? Puro, para tests. */
    internal fun cacheFresh(stampMs: Long, nowMs: Long, ttlMs: Long = CACHE_TTL_MS): Boolean =
        stampMs > 0 && nowMs - stampMs < ttlMs

    /**
     * Cuerpo del endpoint → lista cruda. Solo `status:"ok"` con 2+ filas vale la
     * pena (los campos ya viajan pelados: contentId/name/seriesNumber/episodeNumber);
     * PERO la desdedupe/orden la hace `X4KApi.parseEpisodePrograms` — si el JSON
     * trae filas, dejanlo pasar tal cual y que el parser decida (una sola
     * implementación para ambas rutas).
     */
    internal fun parseMirrorBody(body: String?): JSONArray? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(body)
            if (obj.optString("status") != "ok") return null
            obj.optJSONArray("episodes")
        }.getOrNull()?.takeIf { it.length() > 0 }
    }

    /** Nombre real y sinopsis corta del capítulo (TMDB, guardados en el VPS). */
    data class EpisodeInfo(val title: String, val overview: String, val still: String)

    /**
     * Extrae `title/overview/still` de la fila del capítulo (puro). El espejo los
     * manda ya cruzados por número; si esa temporada aún no tiene metadatos, son
     * cadenas vacías y la tarjeta se queda como siempre (nunca empeora).
     */
    internal fun infoFrom(array: JSONArray?, contentId: String): EpisodeInfo? {
        if (array == null || contentId.isBlank()) return null
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (o.optString("contentId").trim() != contentId.trim()) continue
            val info = EpisodeInfo(
                title = o.optString("title").trim(),
                overview = o.optString("overview").trim(),
                still = o.optString("still").trim(),
            )
            return info.takeIf { it.title.isNotBlank() || it.overview.isNotBlank() || it.still.isNotBlank() }
        }
        return null
    }

    /**
     * Metadatos del capítulo `contentId` de esa temporada (`xtv:<cid>` de la fila).
     * Usa la copia en RAM del espejo; si está fría, la red una vez (~50 ms servida
     * por el VPS) y `null` ante cualquier fallo.
     */
    suspend fun info(seasonId: String, contentId: String): EpisodeInfo? =
        runCatching { episodes(seasonId) }.getOrNull()?.let { infoFrom(it, contentId) }

    /**
     * La lista cruda de capítulos del espejo, o `null` si no hay copia servible
     * (falla de red, ref fuera del catálogo, ficha sin capítulos, TTL vencido…).
     * Jamás lanza: el caller cae al portal sin historias.
     */
    suspend fun episodes(seriesId: String): JSONArray? = withContext(Dispatchers.IO) {        val ref = mirrorRef(seriesId)
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val hit = cache[ref]
            if (hit != null && cacheFresh(hit.first, now)) return@withContext hit.second
        }
        val url = "${RemoteIndexSource.BASE_URL}/v1/episodes?ref=${URLEncoder.encode(ref, "UTF-8")}"
        val fetched = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
                setRequestProperty("Accept", "application/json")
            }
            val text = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
            conn.disconnect()
            parseMirrorBody(text)
        }.getOrNull()
        if (fetched != null) {
            synchronized(cache) { cache[ref] = System.currentTimeMillis() to fetched }
        }
        fetched
    }

    /** Visibilidad de la caché para tests (Robolectric/jvm puro). */
    internal fun cachePeek(seriesId: String): Pair<Long, JSONArray>? = synchronized(cache) { cache[mirrorRef(seriesId)] }

    internal fun cachePutForTests(seriesId: String, stampMs: Long, episodes: JSONArray) {
        synchronized(cache) { cache[mirrorRef(seriesId)] = stampMs to episodes }
    }

    internal fun cacheClearForTests() = synchronized(cache) { cache.clear() }
}
