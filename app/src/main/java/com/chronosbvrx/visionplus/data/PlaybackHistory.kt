package com.chronosbvrx.visionplus.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
data class ResumeCard(val entry: ResumeEntry, val film: Film?, val progress: Float)

/** Lo mínimo para guardar/continuar en el reproductor ExoPlayer (no-XTV). */
data class ResumeInfo(val key: String, val title: String, val posterUrl: String, val videoUrl: String)

object PlaybackHistory {
    private const val PREFS = "playback_history"
    private const val K_ENTRIES = "entries"

    /** Máximo de recuerdos (los viejos se podan solos). */
    internal const val MAX_ENTRIES = 20

    /** Se guarda a partir de este avance (menos es "apenas lo abrí"). */
    internal const val MIN_KEEP_MS = 15_000L

    /** Con este margen al final se da por terminada (no se ofrece continuar). */
    internal const val FINISH_MARGIN_MS = 60_000L

    /** Al terminar del todo (créditos), con este margen menor también se borra. */
    internal const val END_MARGIN_MS = 30_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(context: Context): List<ResumeEntry> =
        prune(read(prefs(context)), System.currentTimeMillis())

    fun get(context: Context, key: String): ResumeEntry? =
        list(context).firstOrNull { it.key == key }

    /** Guarda (o borra si terminó). [immediate] usa commit: para el onDestroy del player. */
    fun save(context: Context, entry: ResumeEntry, immediate: Boolean = false) {
        val p = prefs(context)
        val entries = read(p).filterNot { it.key == entry.key }
        val next = if (isFinished(entry.positionMs, entry.durationMs)) {
            entries
        } else {
            prune(listOf(entry) + entries, entry.updatedAt)
        }
        write(p, next, immediate)
    }

    fun delete(context: Context, key: String) {
        val p = prefs(context)
        write(p, read(p).filterNot { it.key == key })
    }

    /** Entradas + fichas del catálogo como tarjetas, recientes primero. */
    fun continueCards(context: Context, films: List<Film>, limit: Int = 10): List<ResumeCard> =
        matchCards(list(context), films).take(limit)

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
        title = o.optString("title"),
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
}

/** Vale la pena recordarla: arrancada y lejos del final. */
internal fun shouldKeep(positionMs: Long, durationMs: Long): Boolean =
    positionMs >= PlaybackHistory.MIN_KEEP_MS &&
        (durationMs <= 0 || positionMs <= durationMs - PlaybackHistory.FINISH_MARGIN_MS)

/** Terminada: al reabrirla se empieza de cero. */
internal fun isFinished(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= durationMs - PlaybackHistory.END_MARGIN_MS

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
        ResumeCard(entry, film, progress)
    }
}
