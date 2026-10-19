package com.chronosbvrx.visionplus.data.markers

import android.content.Context
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Marcadores de salto por episodio: dónde acaba el intro y dónde empiezan los créditos.
 *
 * Todo sale del servidor de Vision+ (`/v1/markers`), que a su vez cachea las bases
 * comunitarias: TheIntroDB (por tmdb), IntroDB.app (por imdb) y AniSkip (por MyAnimeList,
 * para anime). Lo que no está en esas bases simplemente no muestra nada: sin botones ni
 * correcciones manuales; la experiencia solo aparece donde hay datos de verdad.
 *
 * El reproductor nativo (`:x4k`) recibe el resultado como JSON en el intent. Además hay
 * caché en disco (filesDir/markers_cache) porque las SharedPreferences no cruzan procesos:
 * así el reproductor puede leer el plan del capítulo actual y de los siguientes sin esperar
 * a la red y sin bloquear el arranque del video.
 */

data class EpisodeMarkers(
    val introStartMs: Long = -1L,
    val introEndMs: Long = -1L,
    val recapStartMs: Long = -1L,
    val recapEndMs: Long = -1L,
    val creditsStartMs: Long = -1L,
    val creditsEndMs: Long = -1L,
    val postCreditsStartMs: Long = -1L,
    val postCreditsEndMs: Long = -1L,
    val introSource: String = "",
    val recapSource: String = "",
    val creditsSource: String = "",
    val postCreditsSource: String = "",
) {
    val hasIntro: Boolean get() = introEndMs > 0L
    val hasRecap: Boolean get() = recapEndMs > 0L
    val hasCredits: Boolean get() = creditsStartMs > 0L
    val hasPostCredits: Boolean get() = postCreditsStartMs > 0L
    val isEmpty: Boolean get() = !hasIntro && !hasRecap && !hasCredits && !hasPostCredits
    val introHasKnownStart: Boolean get() = introStartMs >= 0L
    val recapHasKnownStart: Boolean get() = recapStartMs >= 0L
    val creditsHasKnownEnd: Boolean get() = creditsEndMs > creditsStartMs
    val postCreditsHasKnownEnd: Boolean get() = postCreditsEndMs > postCreditsStartMs
}

/** Un salto automático decidido por la política pura (no por la vista). */
internal data class AutoSkipTarget(val kind: String, val startMs: Long, val endMs: Long)

/** Ventana del botón «Omitir intro» ([start] = 0 cuando solo se conoce el fin). */
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

/** Ventana del resumen («Anteriormente…»): misma regla que el intro. */
internal fun skipRecapWindow(markers: EpisodeMarkers): LongRange? {
    if (!markers.hasRecap) return null
    val start = if (markers.recapStartMs >= 0L) markers.recapStartMs else 0L
    val end = markers.recapEndMs - 2000L
    return if (end < start) null else start..end
}

internal fun inRecapWindow(positionMs: Long, markers: EpisodeMarkers): Boolean {
    val window = skipRecapWindow(markers) ?: return false
    return positionMs in window
}

/**
 * Ventana para el modo AUTOMÁTICO. A diferencia del botón, aquí NO se inventa un inicio:
 * si la fuente solo conoce el fin del intro, auto-saltar desde 0 cortaría un cold open.
 * Tampoco se acepta un segmento que no quepa en la duración real del capítulo.
 */
internal fun autoIntroWindow(markers: EpisodeMarkers, durationMs: Long): LongRange? {
    if (!markers.hasIntro || !markers.introHasKnownStart) return null
    val start = markers.introStartMs
    val end = markers.introEndMs
    if (end <= start + 3_000L) return null
    val cap = if (durationMs > 0L) minOf(12 * 60_000L, durationMs / 2) else 12 * 60_000L
    if (end > cap) return null
    if (durationMs > 0L && end > durationMs) return null
    return start until end
}

internal fun autoRecapWindow(markers: EpisodeMarkers, durationMs: Long): LongRange? {
    if (!markers.hasRecap || !markers.recapHasKnownStart) return null
    val start = markers.recapStartMs
    val end = markers.recapEndMs
    if (end <= start + 2_000L) return null
    val cap = if (durationMs > 0L) minOf(5 * 60_000L, durationMs / 2) else 5 * 60_000L
    if (end > cap) return null
    if (durationMs > 0L && end > durationMs) return null
    return start until end
}

/**
 * Inicio válido de la tarjeta de créditos. Los datos comunitarios a veces traen un «outro»
 * a los 5 minutos de un capítulo de 45: el cliente lo descarta aunque el servidor no tuviera
 * la duración para hacerlo.
 */
internal fun creditsCardStartMs(markers: EpisodeMarkers, durationMs: Long): Long? {
    if (!markers.hasCredits) return null
    val start = markers.creditsStartMs
    if (start <= 0L) return null
    val floor = maxOf(5 * 60_000L, if (durationMs > 0L) durationMs / 2 else 0L)
    if (start < floor) return null
    if (durationMs > 0L && start > durationMs - 15_000L) return null
    return start
}

/** Momento en el que el modo rápido puede arrancar el siguiente (fin de créditos). */
internal fun creditsAutoAtMs(markers: EpisodeMarkers, durationMs: Long): Long? {
    val start = creditsCardStartMs(markers, durationMs) ?: return null
    val end = markers.creditsEndMs
    if (end > start && (durationMs <= 0L || end <= durationMs)) return end
    val fallback = start + 10_000L
    return if (durationMs > 0L && fallback >= durationMs) null else fallback
}

/** El post-créditos solo se respeta si cae después del inicio de créditos y dentro del archivo. */
internal fun validPostCredits(markers: EpisodeMarkers, durationMs: Long): Boolean {
    if (!markers.hasPostCredits) return false
    val start = markers.postCreditsStartMs
    if (start <= 0L) return false
    if (markers.hasCredits && start < markers.creditsStartMs) return false
    if (durationMs > 0L && start > durationMs + 60_000L) return false
    return true
}

/**
 * Objetivo del auto-skip (recap primero, luego intro) para la posición actual.
 * `lateGraceMs` evita cortar contenido cuando los marcadores llegan tarde: si el usuario
 * ya lleva más de esa gracia dentro de la ventana, se prefiere no saltar.
 */
internal fun autoSkipTarget(
    markers: EpisodeMarkers,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    lateGraceMs: Long = 15_000L,
): AutoSkipTarget? {
    if (!playing) return null
    autoRecapWindow(markers, durationMs)?.let { window ->
        if (positionMs in window && positionMs - window.first <= lateGraceMs) {
            return AutoSkipTarget("recap", window.first, window.last + 1L)
        }
    }
    autoIntroWindow(markers, durationMs)?.let { window ->
        if (positionMs in window && positionMs - window.first <= lateGraceMs) {
            return AutoSkipTarget("intro", window.first, window.last + 1L)
        }
    }
    return null
}

/**
 * Guardián contra el salto repetido: el seek puede caer en el keyframe anterior y el
 * siguiente tic volvería a ver la posición dentro del intro. Cada tipo se salta UNA vez.
 */
internal fun shouldApplyAutoSkip(kind: String, recapSkipped: Boolean, introSkipped: Boolean): Boolean =
    when (kind) {
        "recap" -> !recapSkipped
        "intro" -> !introSkipped
        else -> false
    }

/** El botón «Omitir intro» no puede pintarse durante la pantalla de carga ni sobre el pre-roll. */
internal fun canShowSkipPill(
    hasTarget: Boolean,
    buttonMode: Boolean,
    videoReady: Boolean,
    blocked: Boolean,
): Boolean = hasTarget && buttonMode && videoReady && !blocked

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
        var recap = -1L
        var recapEnd = -1L
        var credits = -1L
        var creditsEnd = -1L
        var postCredits = -1L
        var postCreditsEnd = -1L
        var introSource = ""
        var recapSource = ""
        var creditsSource = ""
        var postCreditsSource = ""
        root.optJSONObject("intro")?.let { seg ->
            if (!seg.isNull("s")) intro = seg.optLong("s", -1L)
            introEnd = seg.optLong("e", -1L)
            introSource = seg.optString("src", "")
        }
        root.optJSONObject("recap")?.let { seg ->
            if (!seg.isNull("s")) recap = seg.optLong("s", -1L)
            recapEnd = seg.optLong("e", -1L)
            recapSource = seg.optString("src", "")
        }
        root.optJSONObject("credits")?.let { seg ->
            if (!seg.isNull("s")) credits = seg.optLong("s", -1L)
            creditsEnd = seg.optLong("e", -1L)
            creditsSource = seg.optString("src", "")
        }
        root.optJSONObject("postCredits")?.let { seg ->
            if (!seg.isNull("s")) postCredits = seg.optLong("s", -1L)
            postCreditsEnd = seg.optLong("e", -1L)
            postCreditsSource = seg.optString("src", "")
        }
        if (introEnd <= 0L) intro = -1L
        if (recapEnd <= 0L) recap = -1L
        if (credits <= 0L) creditsEnd = -1L
        if (postCredits <= 0L) postCreditsEnd = -1L
        EpisodeMarkers(
            introStartMs = intro,
            introEndMs = introEnd,
            recapStartMs = recap,
            recapEndMs = recapEnd,
            creditsStartMs = credits,
            creditsEndMs = creditsEnd,
            postCreditsStartMs = postCredits,
            postCreditsEndMs = postCreditsEnd,
            introSource = introSource,
            recapSource = recapSource,
            creditsSource = creditsSource,
            postCreditsSource = postCreditsSource,
        )
    }.getOrDefault(EpisodeMarkers())
}

/** JSON compacto para los extras del reproductor (omite lo que no exista). */
internal fun toPlayerJson(markers: EpisodeMarkers): String {
    val root = JSONObject()
    if (markers.hasIntro) {
        val intro = JSONObject()
        if (markers.introStartMs >= 0L) intro.put("s", markers.introStartMs)
        intro.put("e", markers.introEndMs)
        if (markers.introSource.isNotBlank()) intro.put("src", markers.introSource)
        root.put("intro", intro)
    }
    if (markers.hasRecap) {
        val recap = JSONObject()
        if (markers.recapStartMs >= 0L) recap.put("s", markers.recapStartMs)
        recap.put("e", markers.recapEndMs)
        if (markers.recapSource.isNotBlank()) recap.put("src", markers.recapSource)
        root.put("recap", recap)
    }
    if (markers.hasCredits) {
        val credits = JSONObject().put("s", markers.creditsStartMs)
        if (markers.creditsEndMs > markers.creditsStartMs) credits.put("e", markers.creditsEndMs)
        if (markers.creditsSource.isNotBlank()) credits.put("src", markers.creditsSource)
        root.put("credits", credits)
    }
    if (markers.hasPostCredits) {
        val post = JSONObject().put("s", markers.postCreditsStartMs)
        if (markers.postCreditsEndMs > markers.postCreditsStartMs) post.put("e", markers.postCreditsEndMs)
        if (markers.postCreditsSource.isNotBlank()) post.put("src", markers.postCreditsSource)
        root.put("postCredits", post)
    }
    return root.toString()
}

/** Fila de temporada canónica para la consulta (`xtv:…` como la conoce el servidor). */
internal fun normalizeMarkersRef(ref: String): String {
    var clean = ref.trim()
    while (clean.startsWith("xtv:xtv:")) clean = clean.removePrefix("xtv:")
    if (clean.isEmpty()) return ""
    return if (clean.startsWith("xtv:") || clean.startsWith("magis:") || clean.startsWith("magisvod:")) clean
    else "xtv:$clean"
}

/** La API solo sirve episodios dentro de este rango (One Piece y series largas incluidas). */
internal const val MARKERS_EPISODE_MAX = 5_000

/** Nombre estable de la entrada de caché en disco (sin caracteres raros del ref). */
internal fun markersCacheName(ref: String, ep: Int): String {
    val key = "$ref:$ep"
    val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + ".json"
}

private data class CachedMarkers(val json: String, val atMs: Long)

object MarkersStore {
    private const val REMOTE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val STALE_MAX_MS = 30L * 24 * 60 * 60 * 1000L
    private const val MAX_FILES = 3_000

    private val memory = ConcurrentHashMap<String, CachedMarkers>()

    /** Lo que ya está en memoria o disco, sin tocar la red (para arrancar el video al instante). */
    fun cachedJson(context: Context, ref: String, ep: Int): String {
        val key = cacheKey(ref, ep) ?: return ""
        val now = System.currentTimeMillis()
        memory[key]?.let { if (now - it.atMs <= STALE_MAX_MS) return it.json }
        val disk = readDisk(context, key) ?: return ""
        memory[key] = disk
        return if (now - disk.atMs <= STALE_MAX_MS) disk.json else ""
    }

    /**
     * Plan del episodio para el reproductor: memoria → disco fresco → red. Si la red falla,
     * devuelve lo último bueno que hubiera (stale-while-revalidate) en vez de dejar sin botón.
     */
    suspend fun fetchForPlayer(
        context: Context,
        ref: String,
        ep: Int,
        lengthSeconds: Int = 0,
    ): String {
        val key = cacheKey(ref, ep) ?: return ""
        val now = System.currentTimeMillis()
        memory[key]?.takeIf { now - it.atMs < REMOTE_TTL_MS }?.let { return it.json }
        val disk = readDisk(context, key)
        if (disk != null && now - disk.atMs < REMOTE_TTL_MS) {
            memory[key] = disk
            return disk.json
        }
        val stale = memory[key]?.json ?: disk?.json ?: ""
        val fresh = withTimeoutOrNull(12_000L) {
            remoteMarkers(ref, ep, lengthSeconds)
        }
        if (fresh != null) {
            val cached = CachedMarkers(fresh, System.currentTimeMillis())
            memory[key] = cached
            writeDisk(context, key, cached)
            return fresh
        }
        return stale
    }

    /** Deja listos los siguientes capítulos (máximo 4) sin bloquear al llamador. */
    suspend fun prefetch(
        context: Context,
        ref: String,
        episodes: Iterable<Int>,
        lengthSeconds: Int = 0,
    ) {
        val clean = normalizeMarkersRef(ref)
        if (clean.isEmpty()) return
        coroutineScope {
            episodes.asSequence()
                .filter { it in 1..MARKERS_EPISODE_MAX }
                .distinct()
                .take(4)
                .map { ep -> async { fetchForPlayer(context, clean, ep, lengthSeconds) } }
                .toList()
                .awaitAll()
        }
    }

    private fun cacheKey(ref: String, ep: Int): String? {
        val clean = normalizeMarkersRef(ref)
        if (clean.isEmpty() || ep < 1 || ep > MARKERS_EPISODE_MAX) return null
        return "$clean:$ep"
    }

    private suspend fun remoteMarkers(
        ref: String,
        ep: Int,
        lengthSeconds: Int,
    ): String = withContext(Dispatchers.IO) {
        val clean = normalizeMarkersRef(ref)
        val base = "${RemoteIndexSource.BASE_URL}/v1/markers?ref=${URLEncoder.encode(clean, "UTF-8")}&ep=$ep"
        val url = URL(if (lengthSeconds > 0) "$base&len=$lengthSeconds" else base)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 8_000
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val markers = parseMarkers(text)
            if (markers.isEmpty) "" else toPlayerJson(markers)
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "markers_cache").apply { runCatching { mkdirs() } }

    private fun cacheFile(context: Context, key: String): File =
        // La clave completa (`ref:ep`) ya identifica la entrada; el 0 solo alimenta el hash.
        File(cacheDir(context), markersCacheName(key, 0))

    private fun readDisk(context: Context, key: String): CachedMarkers? {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            CachedMarkers(root.optString("j", ""), root.optLong("at", 0L))
        }.getOrElse {
            runCatching { file.delete() }
            null
        }
    }

    private fun writeDisk(context: Context, key: String, cached: CachedMarkers) {
        runCatching {
            val file = cacheFile(context, key)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(JSONObject().put("at", cached.atMs).put("j", cached.json).toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            prune(context)
        }
    }

    private fun prune(context: Context) {
        runCatching {
            val files = cacheDir(context).listFiles()?.filter { it.isFile } ?: return
            if (files.size <= MAX_FILES) return
            files.sortedBy { it.lastModified() }
                .take(files.size - (MAX_FILES * 4 / 5))
                .forEach { it.delete() }
        }
    }

    // ---- ganchos de prueba (no usados por la app) ----
    internal fun writeCacheForTests(context: Context, ref: String, ep: Int, json: String, atMs: Long) {
        val key = cacheKey(ref, ep) ?: return
        val cached = CachedMarkers(json, atMs)
        memory[key] = cached
        writeDisk(context, key, cached)
    }

    internal fun clearCacheForTests(context: Context) {
        memory.clear()
        runCatching { cacheDir(context).listFiles()?.forEach { it.delete() } }
    }
}
