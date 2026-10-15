package com.chronosbvrx.visionplus.data.stats

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.ResumeEntry

/**
 * «Mi actividad»: estadísticas 100% locales (historial + biblioteca del equipo).
 * Puro y testeable: la UI solo pinta el resultado.
 */
data class ActivityStats(
    val minutesWatched: Long,
    val episodesFinished: Int,
    val filmsFinished: Int,
    val topKind: String,
    val seriesCount: Int,
) {
    val isEmpty: Boolean get() = minutesWatched <= 0 && episodesFinished <= 0 && filmsFinished <= 0
}

/** Minutos vistos ≈ posiciones guardadas + duraciones de lo terminado. */
internal fun summarize(
    entries: List<ResumeEntry>,
    filmsById: Map<String, Film>,
): ActivityStats {
    if (entries.isEmpty()) return ActivityStats(0, 0, 0, "", 0)
    var minutes = 0L
    var episodes = 0
    var films = 0
    val kinds = LinkedHashMap<String, Int>()
    val series = HashSet<String>()
    for (e in entries) {
        val film = filmsById[e.key] ?: filmsById[e.xtvCode]
        val kind = film?.kind.orEmpty()
        if (kind.isNotBlank()) kinds[kind] = (kinds[kind] ?: 0) + 1
        val total = e.durationMs
        val pos = e.positionMs
        val finished = total > 0 && pos >= total - 2 * 60_000L
        minutes += ((if (finished) total else pos).coerceAtLeast(0L) / 60_000L)
        val isSeries = (film?.seasons?.size ?: 0) > 1 || e.episodeIds.size > 1 || e.seasonIds.size > 1
        if (finished) {
            if (isSeries) {
                episodes += 1
                val base = e.title.substringBefore(" · T").substringBefore(" · C").trim()
                if (base.isNotBlank()) series += base.lowercase()
            } else {
                films += 1
            }
        }
    }
    val topKind = kinds.maxByOrNull { it.value }?.key.orEmpty()
    return ActivityStats(minutes, episodes, films, topKind, series.size)
}

/** «12 h 30 min» / «45 min». Puro, testeable. */
internal fun formatMinutes(minutes: Long): String {
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "$h h" else "$h h $m min"
}

internal fun kindLabel(kind: String): String = when (kind) {
    "movie" -> "Películas"
    "series" -> "Series"
    "anime" -> "Anime"
    "kids" -> "Infantil"
    else -> ""
}
