package com.chronosbvrx.visionplus.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dónde quedó cada reproducción: la app recuerda el minuto y ofrece continuar después,
 * en la ficha y en la fila «Seguir viendo» del inicio. Vive en SharedPreferences.
 */
data class ResumeEntry(
    /** Clave estable: el playCode XTV del capítulo/película, o el videoUrl en el resto. */
    val key: String,
    /** Para mostrar ("Serie · C3"). */
    val title: String,
    val posterUrl: String = "",
    /** Replay directo no-XTV (vacío en XTV: ahí manda [xtvCode] + contexto). */
    val videoUrl: String = "",
    val xtvCode: String = "",
    val seriesId: String = "",
    val episodeIds: List<String> = emptyList(),
    val episodeIndex: Int = 0,
    val seasonIds: List<String> = emptyList(),
    val seasonNames: List<String> = emptyList(),
    val seasonIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
)

/** Una entrada lista para pintar: su ficha del catálogo cuando sigue ahí (o null). */
data class ResumeCard(
    val entry: ResumeEntry,
    val film: Film?,
    val progress: Float,
    /** Llegó al final (o a los créditos): el historial lo muestra como «Visto». */
    val completed: Boolean = false,
)

/** Lo mínimo para guardar/continuar en el reproductor ExoPlayer (no-XTV). */
data class ResumeInfo(val key: String, val title: String, val posterUrl: String, val videoUrl: String)

object PlaybackHistory {
    private const val PREFS = "playback_history"
    private const val K_ENTRIES = "entries"

    /** Máximo de recuerdos (los viejos se podan solos). */
    internal const val MAX_ENTRIES = 60

    /** Se guarda a partir de este avance (menos es "apenas lo abrí"). */
    internal const val MIN_KEEP_MS = 15_000L

    /** Con este margen al final se da por terminada (no se ofrece continuar). */
    internal const val FINISH_MARGIN_MS = 60_000L

    /** Al terminar del todo (créditos), con este margen menor también se borra. */
    internal const val END_MARGIN_MS = 30_000L

    /** Al saltar a otro capítulo con este margen o menos del final, el saliente cuenta como visto. */
    internal const val WATCHED_SKIP_MARGIN_MS = 120_000L

    /** Bajo esta duración no se cree el margen (nada de avisos por un clip de un minuto). */
    internal const val WATCHED_MIN_DURATION_MS = 60_000L

    /** El XML de SharedPreferences donde vive el historial (lo lee [listFresh] directo del disco). */
    internal const val PREFS_XML_FILE = "playback_history.xml"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(context: Context): List<ResumeEntry> =
        prune(listFresh(context), System.currentTimeMillis())

    /**
     * El historial tal como está en disco. El reproductor vive en `:x4k` y escribe ahí el minuto:
     * las SharedPreferences no se sincronizan entre procesos, así que la copia en memoria del
     * proceso principal puede quedarse vieja (tarjetas sin el último capítulo o con un minuto
     * atrasado). Se lee el XML directo y solo se cae a la copia en memoria si no se puede leer.
     */
    internal fun listFresh(context: Context): List<ResumeEntry> =
        entriesFromXml(runCatching { prefsFile(context).readText() }.getOrNull())
            ?: read(prefs(context))

    fun get(context: Context, key: String): ResumeEntry? =
        list(context).firstOrNull { it.key == key }

    /**
     * Guarda el recuerdo, también cuando terminó. [immediate] usa commit: para el onDestroy
     * del player. De una serie vive un solo recuerdo: guardar un capítulo retira los
     * anteriores (el historial muestra el último estado de la serie; «Seguir viendo» filtra
     * los terminados).
     */
    fun save(context: Context, entry: ResumeEntry, immediate: Boolean = false) {
        val p = prefs(context)
        val series = seriesKeyOf(entry)
        // Base fresca del disco: reescribir sobre la copia en memoria del proceso tiraría lo
        // que el reproductor (:x4k) acaba de guardar.
        val entries = listFresh(context).filterNot {
            it.key == entry.key || (series != null && seriesKeyOf(it) == series)
        }
        val next = prune(listOf(entry) + entries, entry.updatedAt)
        write(p, next, immediate)
    }

    fun delete(context: Context, key: String) {
        val p = prefs(context)
        write(p, listFresh(context).filterNot { it.key == key })
    }

    /** Borra todas las entradas guardadas del historial de reproducción. */
    fun clear(context: Context) {
        val p = prefs(context)
        p.edit().remove(K_ENTRIES).apply()
    }

    /**
     * Reemplaza el historial entero con la lista ya fusionada (la sincronización). Se escribe
     * con `commit` para que el proceso `:x4k` lo vea en su siguiente lectura del XML.
     */
    internal fun replaceAll(context: Context, entries: List<ResumeEntry>) {
        val clean = prune(entries.filter { it.key.isNotBlank() && it.positionMs > 0 }, System.currentTimeMillis())
        write(prefs(context), clean, immediate = true)
    }

    /** Entradas + fichas del catálogo como tarjetas, solo lo reanudable y una por serie. */
    fun continueCards(context: Context, films: List<Film>, limit: Int = 10): List<ResumeCard> =
        matchCards(
            latestBySeries(list(context).filter { shouldKeep(it.positionMs, it.durationMs) }),
            films,
        ).take(limit)

    /**
     * Historial completo (en curso y terminados), recientes primero y una entrada por serie.
     * Alimenta la pestaña «Historial» de Mi lista; los terminados llevan [ResumeCard.completed].
     */
    fun historyCards(context: Context, films: List<Film>, limit: Int = MAX_ENTRIES): List<ResumeCard> =
        matchCards(latestBySeries(list(context)), films).take(limit)

    private fun read(p: android.content.SharedPreferences): List<ResumeEntry> {
        val raw = p.getString(K_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> entryFromJson(arr.optJSONObject(i) ?: JSONObject()) }
                .filter { it.key.isNotBlank() && it.positionMs > 0 }
        }.getOrDefault(emptyList())
    }

    private fun write(p: android.content.SharedPreferences, entries: List<ResumeEntry>, immediate: Boolean = false) {
        val arr = JSONArray()
        entries.forEach { arr.put(entryToJson(it)) }
        val edit = p.edit().putString(K_ENTRIES, arr.toString())
        if (immediate) edit.commit() else edit.apply()
    }

    internal fun entryToJson(e: ResumeEntry): JSONObject = JSONObject()
        .put("key", e.key)
        .put("title", e.title)
        .put("posterUrl", e.posterUrl)
        .put("videoUrl", e.videoUrl)
        .put("xtvCode", e.xtvCode)
        .put("seriesId", e.seriesId)
        .put("episodeIds", JSONArray(e.episodeIds))
        .put("episodeIndex", e.episodeIndex)
        .put("seasonIds", JSONArray(e.seasonIds))
        .put("seasonNames", JSONArray(e.seasonNames))
        .put("seasonIndex", e.seasonIndex)
        .put("positionMs", e.positionMs)
        .put("durationMs", e.durationMs)
        .put("updatedAt", e.updatedAt)

    internal fun entryFromJson(o: JSONObject): ResumeEntry = ResumeEntry(
        key = o.optString("key"),
        title = normalizeDisplayName(o.optString("title")),
        posterUrl = o.optString("posterUrl"),
        videoUrl = o.optString("videoUrl"),
        xtvCode = o.optString("xtvCode"),
        seriesId = o.optString("seriesId"),
        episodeIds = jsonStrings(o.optJSONArray("episodeIds")),
        episodeIndex = o.optInt("episodeIndex"),
        seasonIds = jsonStrings(o.optJSONArray("seasonIds")),
        seasonNames = jsonStrings(o.optJSONArray("seasonNames")),
        seasonIndex = o.optInt("seasonIndex"),
        positionMs = o.optLong("positionMs"),
        durationMs = o.optLong("durationMs"),
        updatedAt = o.optLong("updatedAt"),
    )

    private fun jsonStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
    }

    /**
     * El XML de preferencias → entradas del historial. null cuando no se pudo leer (el archivo
     * puede estar a medio escribir por el otro proceso); lista vacía cuando aún no hay historial.
     */
    internal fun entriesFromXml(xml: String?): List<ResumeEntry>? {
        if (xml.isNullOrEmpty()) return null
        val open = "<string name=\"entries\">"
        val start = xml.indexOf(open)
        if (start < 0) return emptyList()
        val valueStart = start + open.length
        val end = xml.indexOf("</string>", valueStart)
        if (end < 0) return null
        val json = unescapeXmlEntities(xml.substring(valueStart, end))
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i -> entryFromJson(arr.optJSONObject(i) ?: JSONObject()) }
                .filter { it.key.isNotBlank() && it.positionMs > 0 }
        }.getOrNull()
    }

    private fun prefsFile(context: Context): File =
        File(File(context.applicationContext.dataDir, "shared_prefs"), PREFS_XML_FILE)
}

/** Vale la pena recordarla: arrancada y lejos del final. */
internal fun shouldKeep(positionMs: Long, durationMs: Long): Boolean =
    positionMs >= PlaybackHistory.MIN_KEEP_MS &&
        (durationMs <= 0 || positionMs <= durationMs - PlaybackHistory.FINISH_MARGIN_MS)

/** Terminada: al reabrirla se empieza de cero. */
internal fun isFinished(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= durationMs - PlaybackHistory.END_MARGIN_MS

/** Un recuerdo de capítulo: con 2+ capítulos o temporadas es serie (la misma regla de la app). */
internal fun isEpisodeEntry(e: ResumeEntry): Boolean =
    e.episodeIds.size > 1 || e.seasonIds.size > 1

/**
 * La serie de un capítulo tal como el usuario la ve: el nombre base del título («Serie · T2 · C3»).
 * El id queda de respaldo cuando el título no trae la marca; jamás se usa como clave principal
 * porque al cruzar de temporada el reproductor guarda el id de la temporada nueva y la misma
 * serie se partiría en dos tarjetas. null en películas y sueltos.
 */
internal fun seriesKeyOf(e: ResumeEntry): String? {
    if (!isEpisodeEntry(e)) return null
    val base = e.title.substringBefore(" · T").substringBefore(" · C").trim().lowercase()
    if (base.isNotBlank()) return "t:$base"
    val sid = e.seriesId.trim()
    return if (sid.isNotBlank()) "s:$sid" else null
}

/** De cada serie solo el capítulo más reciente (las entradas ya vienen ordenadas); lo demás pasa. */
internal fun latestBySeries(entries: List<ResumeEntry>): List<ResumeEntry> {
    val seen = HashSet<String>(entries.size * 2)
    return entries.filter { e ->
        val key = seriesKeyOf(e)
        key == null || seen.add(key)
    }
}

/** Saltar a este margen o menos del final cuenta como visto (los créditos al frente). */
internal fun shouldCountWatchedOnSkip(positionMs: Long, durationMs: Long): Boolean =
    durationMs > PlaybackHistory.WATCHED_MIN_DURATION_MS &&
        positionMs >= durationMs - PlaybackHistory.WATCHED_SKIP_MARGIN_MS

/** Recientes primero, tope y sin momias de más de 90 días. */
internal fun prune(entries: List<ResumeEntry>, nowMs: Long): List<ResumeEntry> {
    val fresh = nowMs - 90L * 24 * 60 * 60 * 1000
    return entries.filter { it.updatedAt >= fresh }
        .sortedByDescending { it.updatedAt }
        .take(PlaybackHistory.MAX_ENTRIES)
}

/** "12:34" o "1:02:03". */
internal fun formatResume(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "$h:%02d:%02d".format(m, s) else "%02d:%02d".format(m, s)
}

/** Cada recuerdo con su ficha (cuando el catálogo aún la trae). Puro, testeable con lookup O(1). */
internal fun matchCards(entries: List<ResumeEntry>, films: List<Film>): List<ResumeCard> {
    if (entries.isEmpty()) return emptyList()
    val byXtvCode = HashMap<String, Film>(films.size)
    val byUrl = HashMap<String, Film>(films.size)
    val byId = HashMap<String, Film>(films.size)
    for (f in films) {
        byId.putIfAbsent(f.id, f)
        if (f.videoUrl.isNotBlank()) byUrl.putIfAbsent(f.videoUrl, f)
        if (f.videoUrl.startsWith("xtv:")) {
            byXtvCode.putIfAbsent(f.videoUrl.removePrefix("xtv:"), f)
        }
    }
    return entries.map { entry ->
        val film = if (entry.xtvCode.isNotBlank()) {
            byXtvCode[entry.xtvCode] ?: byId[entry.xtvCode] ?: byId["xtv:${entry.xtvCode}"]
        } else {
            byUrl[entry.videoUrl] ?: byId[entry.key]
        }
        val progress = if (entry.durationMs > 0) {
            (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        ResumeCard(entry, film, progress, completed = isFinished(entry.positionMs, entry.durationMs))
    }
}

/** El texto del XML escapa `&`, `<`, `>` (y comillas): al leer hay que devolverlos. */
private fun unescapeXmlEntities(text: String): String = text
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
