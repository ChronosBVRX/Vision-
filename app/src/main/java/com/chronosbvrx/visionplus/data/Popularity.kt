package com.chronosbvrx.visionplus.data

import java.util.Calendar
import kotlin.math.ln
import kotlin.math.log10

/**
 * Motor de popularidad de Vision+: ordena el catálogo como una app profesional
 * (tendencias y estrenos reales primero, relleno oscuro al fondo).
 *
 * El problema anterior: todo se ordenaba por `score` solo (rating 0-10 sin votos).
 * Un título oscuro con 9.0 (5 votos) le ganaba a un blockbuster con 7.7 (200k votos),
 * y "Estrenos" era `año DESC` puro (un oscuro 2026 score 0 vencía a un éxito 2025).
 *
 * Señales (todas opcionales, con fallback cuando el servidor aún no las sirve):
 * - `popularity` TMDB (0 = desconocida): manda como prueba de impacto real.
 * - `voteCount` TMDB (0 = desconocido): pondera la confianza del rating.
 * - `score` (0 = sin rating): vale menos cuando viene huérfano (solo portal, sin votos/pop).
 * - `year`: bono suave de recencia (no aplasta a la calidad como antes).
 * - `posterUrl`: micro-bono para que el Top/Hero nunca lo corone un hueco sin arte.
 *
 * Funciones puras (`internal`) para los tests anti-regresión.
 */
object Popularity {

    internal fun nowYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /**
     * Puntuación de impacto: rating ponderado por confianza + popularidad + recencia.
     * Rango típico 0..30 (rating aporta hasta 20, popularidad hasta 10, recencia hasta 3).
     */
    internal fun rankScore(film: Film, yearNow: Int = nowYear()): Double {
        val rating = film.score.coerceIn(0.0, 10.0)
        val pop = film.popularity.coerceAtLeast(0.0)
        val votes = film.voteCount.coerceAtLeast(0)

        val popPoints = when {
            pop > 0.0 -> (ln(pop + 1.0) / ln(501.0) * 10.0).coerceIn(0.0, 10.0)
            votes > 0 -> ((log10(votes + 10.0) - 1.0) * 3.0).coerceIn(0.0, 10.0)
            else -> 0.0
        }
        val confidence = when {
            pop > 0.0 || votes > 0 -> 1.0
            rating > 0.0 -> 0.6
            else -> 0.0
        }
        val ratingPoints = rating * 2.0 * confidence
        val recency = when {
            film.year <= 0 -> 0.0
            film.year >= yearNow -> 3.0
            film.year == yearNow - 1 -> 2.0
            film.year == yearNow - 2 -> 1.0
            film.year >= 2015 -> 0.5
            else -> 0.0
        }
        val posterBonus = if (film.posterUrl.isNotBlank()) 0.3 else 0.0
        return ratingPoints + popPoints + recency + posterBonus
    }

    /** Dedup por contentId real (`xtv:<cid>`), como ya hacen Home móvil y TV. */
    internal fun dedupe(films: List<Film>): List<Film> = films.distinctBy {
        val cleanCid = it.videoUrl.substringAfter(':').substringBefore('|')
        if (cleanCid.isNotBlank()) cleanCid.lowercase() else it.id.lowercase()
    }

    /**
     * Tendencias: lo más popular con alguna señal (rating, popularidad o votos),
     * ordenado por impacto. El relleno sin ninguna señal no entra.
     */
    internal fun topTrending(films: List<Film>, limit: Int = 10, yearNow: Int = nowYear()): List<Film> =
        films
            .filter { it.videoUrl.isNotBlank() && (it.score > 0.0 || it.popularity > 0.0 || it.voteCount > 0) }
            .sortedByDescending { rankScore(it, yearNow) }
            .let(::dedupe)
            .take(limit)

    /**
     * Estrenos de verdad: solo año actual y anterior, pero ordenados por impacto
     * (popular primero), no por año puro. Un éxito 2025 supera a un oscuro 2026.
     */
    internal fun realPremieres(films: List<Film>, limit: Int = 15, yearNow: Int = nowYear()): List<Film> =
        films
            .filter { it.videoUrl.isNotBlank() && it.year >= yearNow - 1 }
            .sortedByDescending { rankScore(it, yearNow) }
            .let(::dedupe)
            .take(limit)

    /** Reordena cualquier hilera de sección por impacto (robusto ante caché vieja del servidor). */
    internal fun reorderRow(films: List<Film>, yearNow: Int = nowYear()): List<Film> =
        films.sortedByDescending { rankScore(it, yearNow) }

    /** Héroe: el #1 de tendencias con póster; si no hay, el primer estreno con póster. */
    internal fun pickHero(trending: List<Film>, premieres: List<Film>, fallback: Film?): Film? =
        trending.firstOrNull { it.posterUrl.isNotBlank() }
            ?: premieres.firstOrNull { it.posterUrl.isNotBlank() }
            ?: trending.firstOrNull()
            ?: premieres.firstOrNull()
            ?: fallback
}
