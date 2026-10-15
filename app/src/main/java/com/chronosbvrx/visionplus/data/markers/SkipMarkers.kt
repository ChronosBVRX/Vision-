package com.chronosbvrx.visionplus.data.markers

import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Marcadores de salto por episodio: dónde acaba el intro y dónde empiezan los créditos.
 *
 * Todo sale del servidor de Vision+ (`/v1/markers`), que a su vez cachea las bases
 * comunitarias: TheIntroDB (por tmdb), IntroDB.app (por imdb) y AniSkip (por MyAnimeList,
 * para anime). Lo que no está en esas bases simplemente no muestra nada: sin botones ni
 * correcciones manuales; la experiencia solo aparece donde hay datos de verdad.
 *
 * El reproductor nativo (`:x4k`) recibe el resultado como JSON en el intent: las
 * SharedPreferences no cruzan procesos y este dato se necesita al abrir el capítulo.
 */

/** `intro`: [introStartMs] puede faltar (solo se conoce el fin); `credits`: solo el inicio. */
data class EpisodeMarkers(
    val introStartMs: Long = -1L,
    val introEndMs: Long = -1L,
    val creditsStartMs: Long = -1L,
) {
    val hasIntro: Boolean get() = introEndMs > 0L
    val hasCredits: Boolean get() = creditsStartMs > 0L
    val isEmpty: Boolean get() = !hasIntro && !hasCredits
}

/** Ventana en la que se ofrece «Omitir intro» ([start] = 0 cuando solo se conoce el fin). */
internal fun skipIntroWindow(markers: EpisodeMarkers): LongRange? {
    if (!markers.hasIntro) return null
    val start = if (markers.introStartMs >= 0L) markers.introStartMs else 0L
    val end = markers.introEndMs - 2000L
    return if (end < start) null else start..end
}

internal fun inIntroWindow(positionMs: Long, markers: EpisodeMarkers): Boolean {
    val window = skipIntroWindow(markers) ?: return false
    return positionMs in window
}

/** Con marcador de créditos: al llegar al inicio de los créditos el capítulo ya terminó. */
internal fun shouldOfferCredits(positionMs: Long, markers: EpisodeMarkers): Boolean =
    markers.hasCredits && positionMs >= markers.creditsStartMs

/** JSON del servidor (`{"intro":{"s":…,"e":…},"credits":{"s":…}}`) → marcadores. */
internal fun parseMarkers(json: String?): EpisodeMarkers {
    if (json.isNullOrBlank()) return EpisodeMarkers()
    return runCatching {
        val root = JSONObject(json)
        var intro = -1L
        var introEnd = -1L
        var credits = -1L
        root.optJSONObject("intro")?.let { seg ->
            if (!seg.isNull("s")) intro = seg.optLong("s", -1L)
            introEnd = seg.optLong("e", -1L)
        }
        root.optJSONObject("credits")?.let { seg ->
            if (!seg.isNull("s")) credits = seg.optLong("s", -1L)
        }
        if (introEnd <= 0L) intro = -1L
        if (credits <= 0L) credits = -1L
        EpisodeMarkers(intro, introEnd, credits)
    }.getOrDefault(EpisodeMarkers())
}

/** JSON compacto para los extras del reproductor (omite lo que no exista). */
internal fun toPlayerJson(markers: EpisodeMarkers): String {
    val root = JSONObject()
    if (markers.hasIntro) {
        val intro = JSONObject()
        if (markers.introStartMs >= 0L) intro.put("s", markers.introStartMs)
        intro.put("e", markers.introEndMs)
        root.put("intro", intro)
    }
    if (markers.hasCredits) {
        root.put("credits", JSONObject().put("s", markers.creditsStartMs))
    }
    return root.toString()
}

/** Fila de temporada canónica para la consulta (`xtv:…` como la conoce el servidor). */
internal fun normalizeMarkersRef(ref: String): String {
    val clean = ref.trim()
    if (clean.isEmpty()) return ""
    return if (clean.startsWith("xtv:") || clean.startsWith("magis:") || clean.startsWith("magisvod:")) clean
    else "xtv:$clean"
}

object MarkersStore {
    private const val REMOTE_TTL_MS = 6 * 60 * 60 * 1000L

    private val memory = ConcurrentHashMap<String, EpisodeMarkers>()
    private val memoryAt = ConcurrentHashMap<String, Long>()

    /** Para el reproductor: el JSON de marcadores del episodio ("" cuando no hay datos). */
    suspend fun fetchForPlayer(ref: String, ep: Int): String {
        val key = normalizeMarkersRef(ref)
        if (key.isEmpty() || ep < 1 || ep > 400) return ""
        val markers = runCatching { remoteMarkers(key, ep) }.getOrDefault(EpisodeMarkers())
        return if (markers.isEmpty) "" else toPlayerJson(markers)
    }

    private suspend fun remoteMarkers(ref: String, ep: Int): EpisodeMarkers {
        val cacheKey = "$ref:$ep"
        val at = memoryAt[cacheKey] ?: 0L
        if (System.currentTimeMillis() - at < REMOTE_TTL_MS) {
            return memory[cacheKey] ?: EpisodeMarkers()
        }
        return withContext(Dispatchers.IO) {
            val url = URL(
                "${RemoteIndexSource.BASE_URL}/v1/markers?ref=${URLEncoder.encode(ref, "UTF-8")}&ep=$ep"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_500
                readTimeout = 4_500
                setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val markers = parseMarkers(text)
                memory[cacheKey] = markers
                memoryAt[cacheKey] = System.currentTimeMillis()
                markers
            } finally {
                connection.disconnect()
            }
        }
    }
}
