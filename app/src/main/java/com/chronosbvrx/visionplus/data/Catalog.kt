package com.chronosbvrx.visionplus.data

/**
 * One film of the catalog. Its rows come from the one source (XuperCatalog): an entry of the
 * provider's playlist, where the poster is the entry's logo and the video its stream URL.
 */
data class Film(
    val id: String,
    val title: String,
    val year: Int,
    val durationMin: Int,
    val synopsis: String,
    val posterUrl: String,
    val videoUrl: String,
    val sourceUrl: String,
    /** Puntuación del portal (TMDB-style); 0 cuando no la trae. */
    val score: Double = 0.0,
    /** Popularidad TMDB (0 cuando el servidor aún no la sirve): manda sobre el rating solo. */
    val popularity: Double = 0.0,
    /** Votos TMDB (0 = desconocido): ponderan la confianza del rating. */
    val voteCount: Int = 0,
    /** Grupo de temporadas del servidor («La Casa de Papel T1/T2…» = una sola entrada). */
    val groupId: String = "",
    /** Las temporadas del grupo (vacío cuando no hay varias). */
    val seasons: List<SeasonRef> = emptyList(),
    /** Tipo del catálogo en el servidor: movie/series/anime/kids (vacío si no lo trae). */
    val kind: String = "",
)

/** Una temporada dentro de un grupo: su número (0 = serie completa) y su ref de reproducción. */
data class SeasonRef(val n: Int, val titleId: String, val videoUrl: String)

/** A titled row of films on Home. */
data class CatalogRow(val id: String, val title: String, val films: List<Film>)

/** "1940 · 1 h 32 min": the year and the running time, omitting what is unknown. */
fun Film.metaLine(): String {
    val duration = when {
        durationMin <= 0 -> ""
        durationMin < 60 -> "$durationMin min"
        durationMin % 60 == 0 -> "${durationMin / 60} h"
        else -> "${durationMin / 60} h ${durationMin % 60} min"
    }
    return listOf(if (year > 0) year.toString() else "", duration).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Every film of the catalog once, in row order (a film can sit in several rows). */
fun allFilms(rows: List<CatalogRow>): List<Film> = rows.flatMap { it.films }.distinctBy { it.id }

/**
 * La calificación de la ficha: el score del portal cuando lo trae; si no, el del índice
 * del servidor (las fichas del índice no siempre están en las repisas del portal).
 * Vacío cuando no hay ninguno ("0"/"0.0" del portal no cuentan).
 */
internal fun scoreLabel(portalScore: String?, fallbackScore: Double): String {
    val portal = portalScore?.trim().orEmpty()
    if (portal.isNotBlank() && portal != "0" && portal != "0.0") return portal
    if (fallbackScore > 0) {
        return if (fallbackScore % 1.0 == 0.0) fallbackScore.toInt().toString() else fallbackScore.toString()
    }
    return ""
}

/**
 * The films whose title or synopsis contains every word of [query], ignoring case and accents.
 * A blank query matches everything. Optimized for TV and low-end CPU: checks title first.
 */
fun searchFilms(rows: List<CatalogRow>, query: String): List<Film> {
    val cleanQuery = fold(query).trim()
    val words = cleanQuery.split(' ').filter { it.isNotBlank() }
    val films = allFilms(rows)
    if (words.isEmpty()) return films
    return films.filter { film ->
        val titleNorm = fold(film.title)
        if (words.all { it in titleNorm }) return@filter true
        val haystack = "$titleNorm ${fold(film.synopsis)} ${film.year}"
        words.all { it in haystack }
    }
}

private val DIACRITICS = Regex("\\p{Mn}+")

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")
