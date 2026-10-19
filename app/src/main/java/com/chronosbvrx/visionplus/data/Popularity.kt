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

/** Votos mínimos para que una novedad sin popularidad TMDB todavía cuente. */
internal const val MIN_NOVELTY_VOTES = 10

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

    /** La ref limpia del título (`xtv:<cid>` → `<cid>`); si no hay, su id. */
    private fun cleanCidOf(film: Film): String {
        val cleanCid = film.videoUrl.substringAfter(':').substringBefore('|')
        return if (cleanCid.isNotBlank()) cleanCid.lowercase() else film.id.lowercase()
    }

    /**
     * La palabra fuerte del nombre: la más larga de 4+ letras (singularizada).
     * «Link Click» → link, «Los Simpson - Español Latino» → simpson.
     */
    private fun strongToken(title: String): String {
        val words = java.text.Normalizer
            .normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").split(' ')
            .filter { it.isNotBlank() }
            .map { w -> if (w.length > 4 && w.endsWith("s")) w.dropLast(1) else w }
        val strong = words.filter { it.length >= 4 }
        return strong.maxByOrNull { it.length } ?: words.joinToString("")
    }

    /**
     * Identidad de una obra para no repetirla en el Inicio: manda el id de TMDB junto a
     * la palabra fuerte del nombre. Así dos copias del MISMO show (temporadas T1..T4,
     * doblajes «Los Simpson»/«Os Simpsons») caen en una sola clave, mientras un match
     * TMDB equivocado con nombre ajeno NO fusiona títulos distintos. Sin TMDB queda el
     * contentId, como siempre.
     */
    internal fun identityKey(film: Film): String =
        if (film.tmdbId > 0) "tmdb:${film.tmdbId}:${strongToken(film.title)}" else "cid:${cleanCidOf(film)}"

    /** Clave de repetición laxa: con TMDB la identidad; sin él, título+año (Top 10). */
    internal fun repeatKey(film: Film): String =
        if (film.tmdbId > 0) identityKey(film) else nearDuplicateKey(film)

    /** Marcas de una copia en español (doblaje) frente a una variante extranjera del portal. */
    private val SPANISH_TITLE = Regex("\\b(espa[nñ]ol|latino|latina|castellano)\\b", RegexOption.IGNORE_CASE)
    private val PORTUGUESE_TITLE = Regex("\\b(os|as|dos|das|um|uma|voce|voc[eê]|n[aã]o)\\b", RegexOption.IGNORE_CASE)

    private fun languageRank(film: Film): Int = when {
        SPANISH_TITLE.containsMatchIn(film.title) -> 0
        PORTUGUESE_TITLE.containsMatchIn(film.title) -> 2
        else -> 1
    }

    /**
     * De dos copias de la MISMA obra, la que conviene pintar: primero la doblada al
     * español (el portal rotula «Os Simpsons» y «Los Simpson - Español Latino»), luego
     * la de más popularidad/votos y, al final, la que tiene arte.
     */
    private fun betterCopy(current: Film, candidate: Film): Boolean {
        val language = languageRank(candidate).compareTo(languageRank(current))
        if (language != 0) return language < 0
        if (candidate.popularity != current.popularity) return candidate.popularity > current.popularity
        if (candidate.voteCount != current.voteCount) return candidate.voteCount > current.voteCount
        if (current.posterUrl.isBlank() && candidate.posterUrl.isNotBlank()) return true
        if (candidate.score != current.score) return candidate.score > current.score
        // Misma obra sin más señal: manda la copia más vieja (la primera temporada),
        // igual que el representante del servidor; un año 0 es peor que uno real.
        if (candidate.year == current.year) return false
        if (current.year <= 0) return true
        if (candidate.year <= 0) return false
        return candidate.year < current.year
    }

    /**
     * Dedup del Inicio en una pasada: la misma obra (mismo TMDB + misma palabra fuerte,
     * o mismo contentId `xtv:<cid>`) deja UNA tarjeta. El portal repite la ficha en
     * varias temporadas/doblajes y esa era la fuente de «Link Click» ×4. Cuando hay
     * varias copias se elige la mejor (español > extranjera; luego popularidad/votos).
     */
    internal fun dedupe(films: List<Film>): List<Film> {
        if (films.size < 2) return films
        val byWork = LinkedHashMap<String, Film>(films.size)
        for (film in films) {
            val key = repeatKey(film)
            val current = byWork[key]
            if (current == null) byWork[key] = film
            else if (betterCopy(current, film)) byWork[key] = film
        }
        // Un mismo contentId puede venir con títulos/años distintos: tampoco se repite.
        val seenCid = HashSet<String>(byWork.size)
        return byWork.values.filter { film ->
            val cid = cleanCidOf(film)
            cid.isBlank() || seenCid.add(cid)
        }
    }

    /**
     * Una fila sin lo que ya mostraron las filas de arriba. Si al quitar repetidos la
     * fila queda demasiado corta (catálogo chico), se conserva la original deduplicada:
     * es preferible una repetición a una fila mutilada.
     */
    internal fun withoutRepeats(films: List<Film>, already: List<Film>, minKeep: Int = 6): List<Film> {
        if (films.isEmpty() || already.isEmpty()) return dedupe(films)
        val used = already.map { repeatKey(it) }.toHashSet()
        val fresh = dedupe(films).filterNot { repeatKey(it) in used }
        return if (fresh.size >= minKeep) fresh else dedupe(films)
    }

    /**
     * Puntaje de una novedad: manda la popularidad TMDB; el rating solo pesa con votos
     * (confianza `votos/(votos+100)`), así que un 10.0 con 4 votos no corona la fila; la
     * recencia apenas desempata entre lo del año y lo del anterior.
     */
    internal fun noveltyScore(film: Film, yearNow: Int = nowYear()): Double {
        val pop = film.popularity.coerceAtLeast(0.0)
        val votes = film.voteCount.coerceAtLeast(0)
        val rating = film.score.coerceIn(0.0, 10.0)
        val popPoints = if (pop > 0.0) (ln(pop + 1.0) / ln(501.0) * 10.0).coerceIn(0.0, 10.0) else 0.0
        val confidence = votes.toDouble() / (votes + 100.0)
        val ratingPoints = rating / 10.0 * 8.0 * confidence
        val recency = when {
            film.year >= yearNow -> 2.0
            film.year == yearNow - 1 -> 1.0
            else -> 0.0
        }
        return popPoints + ratingPoints + recency
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
     * Novedades populares de verdad: solo año actual y anterior, con señal real (la
     * popularidad de TMDB manda; los votos cuentan cuando el rating no tiene respaldo)
     * y ordenadas por [noveltyScore]. Un éxito 2025 supera a un oscuro 2026, y un
     * «10.0» de 4 votos ya no le gana a un blockbuster.
     */
    internal fun realPremieres(films: List<Film>, limit: Int = 15, yearNow: Int = nowYear()): List<Film> =
        films
            .filter { it.videoUrl.isNotBlank() && it.posterUrl.isNotBlank() && it.year >= yearNow - 1 }
            .filter { it.popularity > 0.0 || it.voteCount >= MIN_NOVELTY_VOTES }
            .sortedByDescending { noveltyScore(it, yearNow) }
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
        val ranked = dedupe(
            (ranking + pool)
                .filter { it.videoUrl.isNotBlank() && it.popularity > 0.0 }
                .sortedWith(
                    compareByDescending<Film> { it.popularity }
                        .thenByDescending { it.voteCount }
                        .thenByDescending { it.score }
                        .thenByDescending { it.year },
                ),
        )
        if (ranked.size >= limit) return ranked.take(limit)
        // Relleno (catálogo aún sin clasificar por TMDB): el motor de siempre, sin
        // volver a pintar lo que la popularidad ya puso arriba.
        val fill = topTrending(pool, limit = limit + ranked.size, yearNow = yearNow)
            .filterNot { it in ranked }
        return dedupe(ranked + fill).take(limit)
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
        val ranked = dedupe(reorderRow(films.filter { it.kind == kind }, yearNow))
        val withSignal = ranked.filter { it.voteCount > 0 || it.popularity > 0 }
        if (withSignal.size >= limit) return withSignal.take(limit)
        val rest = ranked.filterNot { it.voteCount > 0 || it.popularity > 0 }
        return withSignal + rest.take(limit - withSignal.size)
    }

    /** Las filas propias por tipo del Inicio, en el orden en que se pintan. */
    internal val HOME_KIND_ROWS = listOf(
        "movie" to "Películas populares",
        "series" to "Series populares",
        "anime" to "Anime",
        "kids" to "Para chicos",
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
        // «Novedades» no repite lo que ya está en el Top 10: es la queja concreta de
        // «las mismas películas en las dos filas». Si el catálogo reciente es muy chico,
        // se permite el traslape antes que dejar la fila a medias.
        val top10Keys = top10.map { repeatKey(it) }.toHashSet()
        val freshRecent = pool.filterNot { repeatKey(it) in top10Keys }
        val premieres = realPremieres(freshRecent, limit = 15, yearNow = yearNow)
            .takeIf { it.size >= 6 }
            ?: realPremieres(pool, limit = 15, yearNow = yearNow)
        val premieresFinal = premieres.ifEmpty {
            rows.firstOrNull { it.id == "estrenos" }?.films?.take(15).orEmpty()
        }
        // Las filas por tipo tampoco repiten lo ya visto arriba; con catálogo pequeño
        // (M3U/seed) se conserva la fila completa para no dejarla mutilada.
        val usedByTopRows = (top10 + premieresFinal).map { repeatKey(it) }.toMutableSet()
        val kindRows = HOME_KIND_ROWS.mapNotNull { (kind, label) ->
            // Se miran 40 candidatos para poder saltar los 25 que ya usaron las filas
            // de arriba y aun así llenar la hilera con 20 tarjetas nuevas.
            val base = popularByKind(pool, kind, limit = 40, yearNow = yearNow)
            val fresh = base.filterNot { repeatKey(it) in usedByTopRows }
            val films = (if (fresh.size >= 4) fresh else base).take(20)
            if (films.size >= 4) {
                usedByTopRows += films.map { repeatKey(it) }
                CatalogRow("home-kind-$kind", label, films)
            } else {
                null
            }
        }
        return HomeCurated(
            pool = pool,
            top10 = top10,
            premieres = premieresFinal,
            kindRows = kindRows,
            hero = pickHero(top10, premieresFinal, pool.firstOrNull()),
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
