package com.chronosbvrx.visionplus.data

import android.content.Context
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * «Relacionadas»: títulos del mismo género o estilo para la ficha y para el final
 * de una película.
 *
 * El índice del servidor ya trae las hileras por género (`Acción`, `Comedia`, …);
 * aquí solo se eligen las tarjetas. La obra actual nunca se repite —ni por id, ni
 * por `tmdbId`, ni por grupo de temporadas— y no se mezcla cine con series.
 */
object Related {

    private val GENRE_SPLIT = Regex("[,/|·]")
    private val ACCENTS = Regex("\\p{M}+")

    /** Alias que el portal y las hileras del servidor escriben distinto. */
    private val EQUIVALENT = mapOf(
        "suspenso" to "suspense",
        "thriller" to "suspense",
        "terror" to "horror",
        "horror" to "terror",
        "sci fi" to "ciencia ficcion",
        "science fiction" to "ciencia ficcion",
        "animacion" to "anime",
    )

    /** La sección del índice que corresponde al tipo de la ficha. */
    internal fun sectionFor(film: Film): String = when {
        film.kind.equals("movie", ignoreCase = true) -> "movies"
        film.kind.equals("series", ignoreCase = true) -> "series"
        film.kind.equals("anime", ignoreCase = true) -> "anime"
        film.kind.equals("kids", ignoreCase = true) -> "anime"
        film.kind.equals("novela", ignoreCase = true) ||
            film.kind.equals("novelas", ignoreCase = true) -> "series"
        film.seasons.isNotEmpty() -> "series"
        else -> "movies"
    }

    /** Palabras de género normalizadas (sin acentos, sin signos, sin ruido corto). */
    internal fun genreTokens(raw: String): List<String> =
        raw.split(GENRE_SPLIT)
            .map { fold(it) }
            .filter { it.length >= 3 }
            .distinct()

    private fun fold(raw: String): String =
        Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

    private fun rowMatches(rowTitle: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val row = fold(rowTitle)
        return tokens.any { token ->
            val equivalent = EQUIVALENT[token]
            matches(row, token) || (equivalent != null && matches(row, equivalent))
        }
    }

    private fun matches(row: String, token: String): Boolean =
        row == token || row.contains(token) || token.contains(row)

    private fun sameWork(current: Film, other: Film): Boolean {
        if (other.id == current.id) return true
        if (current.tmdbId > 0 && other.tmdbId == current.tmdbId) return true
        if (current.groupId.isNotBlank() && other.groupId == current.groupId) return true
        val a = normalizeDisplayName(current.title)
        val b = normalizeDisplayName(other.title)
        return a.isNotBlank() && b.isNotBlank() && a.equals(b, ignoreCase = true)
    }

    private fun isCandidate(current: Film, candidate: Film): Boolean {
        if (sameWork(current, candidate)) return false
        if (candidate.videoUrl.isBlank()) return false
        return true
    }

    /**
     * Género de la ficha cuando no hay metadatos del portal: las hileras del índice
     * que contienen la obra ya dicen a qué género pertenece («Drama», «Acción»…).
     */
    internal fun inferGenreHint(film: Film, rows: List<CatalogRow>): String =
        rows.filter { row -> row.films.any { sameWork(film, it) } }
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

    /**
     * Elige hasta [limit] títulos. Si el género coincide con una hilera, esos van
     * primero; el resto se rellena con el mismo tipo y lo más popular. Nunca lanza.
     */
    internal fun pickRelated(
        film: Film,
        rows: List<CatalogRow>,
        genreHint: String = "",
        limit: Int = 12,
    ): List<Film> {
        if (rows.isEmpty() || limit <= 0) return emptyList()
        val tokens = genreTokens(genreHint.ifBlank { film.genres.joinToString(", ") })
        val seen = HashSet<String>()
        val ranked = ArrayList<Pair<Film, Int>>()
        for (row in rows) {
            val rowMatch = rowMatches(row.title, tokens)
            for (candidate in row.films) {
                if (!isCandidate(film, candidate)) continue
                val key = if (candidate.tmdbId > 0) "tmdb:${candidate.tmdbId}" else candidate.id
                if (!seen.add(key)) continue
                var score = if (rowMatch) 120 else 0
                if (candidate.kind.isNotBlank() && candidate.kind == film.kind) score += 25
                if (candidate.year > 0 && film.year > 0) {
                    score += (10 - abs(candidate.year - film.year)).coerceIn(0, 10)
                }
                ranked += candidate to score
            }
        }
        if (ranked.isEmpty()) return emptyList()
        return ranked
            .sortedWith(
                compareByDescending<Pair<Film, Int>> { it.second }
                    .thenByDescending { it.first.popularity }
                    .thenByDescending { it.first.score }
                    .thenBy { it.first.title.lowercase(Locale.ROOT) },
            )
            .map { it.first }
            .take(limit)
    }

    /**
     * Carga la sección que toca y devuelve las relacionadas. Sin red o sin índice
     * devuelve vacío: la pantalla simplemente no pinta la hilera.
     */
    suspend fun forFilm(
        context: Context,
        film: Film,
        genreHint: String = "",
        limit: Int = 12,
    ): List<Film> {
        val rows = runCatching {
            RemoteIndexSource.section(context, sectionFor(film), limit = 40)
        }.getOrNull().orEmpty()
        if (rows.isEmpty()) return emptyList()
        // Si la ficha no trae géneros (título viejo o sin metadatos), las hileras del
        // índice que la contienen los revelan: «Drama», «Acción»…
        val hint = genreHint.ifBlank { inferGenreHint(film, rows) }
        val picked = pickRelated(film, rows, hint, limit)
        if (picked.isNotEmpty()) return picked
        // Último recurso: mismo tipo, lo más popular.
        return pickRelated(film, rows, genreHint = "", limit = limit)
    }
}
