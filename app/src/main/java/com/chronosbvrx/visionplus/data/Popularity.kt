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
private val DIACRITICS = Regex("\\p{Mn}+")

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

    /**
     * El Top de popularidad real: los más populares del catálogo entero, sin importar
     * el año. La hilera «tendencias» del servidor llega ya rankeada (todo el catálogo);
     * el pool local solo rellena cuando un servidor viejo aún no la sirve.
     */
    internal fun topPopular(ranking: List<Film>, pool: List<Film>, limit: Int = 10, yearNow: Int = nowYear()): List<Film> {
        val ranked = (ranking + pool)
            .filter { it.videoUrl.isNotBlank() && it.popularity > 0.0 }
            .sortedWith(
                compareByDescending<Film> { it.popularity }
                    .thenByDescending { it.voteCount }
                    .thenByDescending { it.score }
                    .thenByDescending { it.year },
            )
            .let(::dedupe)
        val seen = HashSet<String>(limit * 2)
        val top = ArrayList<Film>(limit)
        for (film in ranked) {
            if (seen.add(nearDuplicateKey(film))) top += film
            if (top.size == limit) return top
        }
        // Relleno (catálogo aún sin clasificar por TMDB): el motor de siempre.
        val fill = topTrending(pool, limit = limit + ranked.size, yearNow = yearNow)
            .filterNot { it in ranked }
            .filter { seen.add(nearDuplicateKey(it)) }
        return (top + fill).take(limit)
    }

    /**
     * Clave laxa solo para el Top 10: el portal a veces duplica la misma ficha cambiando
     * el plural («Los Simpson»/«Los Simpsons»). Se igualan títulos que difieren en una
     * «s» final de una palabra y comparten año; el resto del catálogo no se toca.
     */
    internal fun nearDuplicateKey(film: Film): String {
        val folded = java.text.Normalizer
            .normalize(film.title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
        val words = folded.map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").split(' ').filter { it.isNotBlank() }
        val base = words.joinToString(" ") { w -> if (w.length > 4 && w.endsWith("s")) w.dropLast(1) else w }
        return "$base:${film.year}"
    }

    /**
     * El pool curado del Inicio: filas propias (estrenos, TV en vivo, listas personales)
     * + hileras curadas del índice propio (tendencias, estrenos, recomendados y géneros).
     * Las colecciones de marca del portal (xtv-*) no entran: Inicio es catálogo de Vision+.
     */
    internal fun homePool(rows: List<CatalogRow>, indexRows: List<CatalogRow>): List<Film> {
        val curatedIndex = indexRows.filter {
            it.id == "tendencias" || it.id == "estrenos" || it.id == "recomendados" || it.id.startsWith("genero-")
        }
        val ownRows = rows.filterNot { it.id.startsWith("xtv-masnew") }
        val all = (ownRows + curatedIndex).flatMap { it.films }
        val nonSeed = all.filter { !it.id.contains("seed_") && it.videoUrl.isNotBlank() }
        val candidates = if (nonSeed.isNotEmpty()) nonSeed else all
        return dedupe(candidates)
    }

    /**
     * Una fila propia del Inicio para un tipo del catálogo (movie/series/anime/kids):
     * impacto real primero; el contenido sin señal TMDB (aún sin clasificar) rellena
     * al final para que la fila nunca se vea vacía durante el backfill.
     */
    internal fun popularByKind(films: List<Film>, kind: String, limit: Int = 20, yearNow: Int = nowYear()): List<Film> {
        val ranked = reorderRow(films.filter { it.kind == kind }, yearNow)
        val withSignal = ranked.filter { it.voteCount > 0 || it.popularity > 0 }
        if (withSignal.size >= limit) return withSignal.take(limit)
        val rest = ranked.filterNot { it.voteCount > 0 || it.popularity > 0 }
        return withSignal + rest.take(limit - withSignal.size)
    }

    /** Las filas propias por tipo del Inicio, en el orden en que se pintan. */
    internal val HOME_KIND_ROWS = listOf(
        "movie" to "🎬 Películas populares",
        "series" to "📺 Series populares",
        "anime" to "🌸 Anime",
        "kids" to "🧸 Para chicos",
    )

    /**
     * Todo lo curado del Inicio en una sola pasada: pool, Top 10, Novedades, filas por
     * tipo y héroe. Puro y pesado a propósito: las pantallas lo calculan fuera del hilo
     * de composición (con miles de films del índice, hacerlo al componer causaba jank).
     */
    internal fun homeCurated(rows: List<CatalogRow>, indexRows: List<CatalogRow>, yearNow: Int = nowYear()): HomeCurated {
        val pool = homePool(rows, indexRows)
        // «Top 10»: popularidad real de todo el catálogo (la hilera «tendencias» del
        // servidor). Antes salía del mismo pool reciente que «Novedades y Estrenos»,
        // así que ambas filas mostraban casi los mismos títulos.
        val serverTrending = indexRows.firstOrNull { it.id == "tendencias" }?.films.orEmpty()
        val top10 = topPopular(serverTrending, pool, limit = 10, yearNow = yearNow)
        val premieres = realPremieres(pool, limit = 15, yearNow = yearNow).ifEmpty {
            rows.firstOrNull { it.id == "estrenos" }?.films?.take(15).orEmpty()
        }
        val kindRows = HOME_KIND_ROWS.mapNotNull { (kind, label) ->
            popularByKind(pool, kind, limit = 20, yearNow = yearNow)
                .takeIf { it.size >= 4 }
                ?.let { CatalogRow("home-kind-$kind", label, it) }
        }
        return HomeCurated(
            pool = pool,
            top10 = top10,
            premieres = premieres,
            kindRows = kindRows,
            hero = pickHero(top10, premieres, pool.firstOrNull()),
        )
    }
}

/** El contenido curado del Inicio, calculado de una vez fuera del hilo de composición. */
internal data class HomeCurated(
    val pool: List<Film> = emptyList(),
    val top10: List<Film> = emptyList(),
    val premieres: List<Film> = emptyList(),
    val kindRows: List<CatalogRow> = emptyList(),
    val hero: Film? = null,
)
