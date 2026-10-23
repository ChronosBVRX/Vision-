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
    /**
     * Identificador TMDB de la obra (0 = sin match): dos fichas del portal que son el
     * MISMO show (temporadas, doblajes) comparten este id. El Inicio lo usa para no
     * pintar la misma tarjeta dos veces («Link Click» ×4, «Os Simpsons»/«Los Simpson»).
     */
    val tmdbId: Int = 0,
    /** Grupo de temporadas del servidor («La Casa de Papel T1/T2…» = una sola entrada). */
    val groupId: String = "",
    /** Las temporadas del grupo (vacío cuando no hay varias). */
    val seasons: List<SeasonRef> = emptyList(),
    /** Tipo del catálogo en el servidor: movie/series/anime/kids (vacío si no lo trae). */
    val kind: String = "",
    /** Géneros TMDB/portal («Acción», «Drama»…): alimentan «Relacionadas» y el final. */
    val genres: List<String> = emptyList(),
    /**
     * Nombre original del título («también conocida como…»): el servidor lo manda cuando el
     * buscador encontró la ficha por otro nombre («buscando a nemo» → «Finding Nemo»). Vacío
     * cuando no aplica o el servidor no lo trae (compatible con payloads viejos).
     */
    val originalTitle: String = "",
)

/** Una temporada dentro de un grupo: su número (0 = serie completa) y su ref de reproducción. */
data class SeasonRef(val n: Int, val titleId: String, val videoUrl: String)

/** A titled row of films on Home. [total] es el total del servidor (> [films].size cuando hay más). */
data class CatalogRow(
    val id: String,
    val title: String,
    val films: List<Film>,
    val total: Int = 0,
)

private val WHITESPACE_RUN = Regex("\\s+")

/** Sufijos de doblaje del portal («… - Español Latino»): se ocultan al pintar. */
private val DUB_SUFFIX = Regex(
    "\\s*[-–—(]\\s*(?:espa[nñ]ol\\s+latino|espa[nñ]ol|latino|castellano)\\s*\\)?\\s*$",
    RegexOption.IGNORE_CASE,
)

/**
 * Nombre listo para pintar. El portal y algunas fuentes usan guiones bajos como separador
 * («Rebbeca_ Un documental», «Ghost in the Shell SAC_2045», «…Especiales T1_11»): la UI
 * muestra siempre espacios simples. También se retira el sufijo de doblaje
 * («Los Simpson - Español Latino» → «Los Simpson»), que solo servía para distinguir
 * copias del portal. Sin esos adornos el texto queda igual que antes.
 */
fun normalizeDisplayName(raw: String): String {
    val spaced = if (raw.indexOf('_') < 0) raw.trim()
    else raw.replace('_', ' ').replace(WHITESPACE_RUN, " ").trim()
    return DUB_SUFFIX.replace(spaced, "").trim()
}

/**
 * Una sola sinopsis por ficha: la del índice (TMDB es-MX) manda y el portal solo
 * rellena cuando la ficha no trae ninguna. Antes la ficha se pintaba con la del
 * índice y, al llegar `getItemData`, saltaba a la del portal (a menudo en inglés):
 * el texto cambiaba a media carga y mezclaba dos fuentes.
 */
internal fun resolveSynopsis(indexSynopsis: String, portalSynopsis: String): String {
    val index = indexSynopsis.trim()
    val portal = portalSynopsis.trim()
    if (index.isBlank()) return portal
    if (portal.isBlank()) return index
    // La del índice (TMDB es-MX) manda cuando ya está en español: no se cambia a media
    // carga. Si la del índice viniera en inglés y la del portal en español, se prefiere
    // la española (una sola vez) para que la ficha nunca se quede en inglés.
    if (looksSpanish(index)) return index
    if (looksSpanish(portal)) return portal
    return index
}

private val SPANISH_WORDS = setOf(
    "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "que", "y",
    "en", "con", "por", "para", "como", "mas", "pero", "sus", "se", "al", "lo", "es",
    "son", "esta", "estan", "cuando", "donde", "mientras", "tras", "sobre", "entre",
    "desde", "hasta", "sin", "tambien", "todo", "todos", "este", "estos", "estas",
)

/** Heurística de idioma para elegir la sinopsis: acentos o palabras funcionales. */
private fun looksSpanish(text: String): Boolean {
    val lower = text.lowercase(java.util.Locale.ROOT)
    if (lower.any { it in "áéíóúñü¿¡" }) return true
    val words = lower.split(Regex("[^a-záéíóúñü]+")).filter { it.length >= 2 }
    if (words.isEmpty()) return false
    val hits = words.count { it in SPANISH_WORDS }
    return hits >= 3 || (words.size <= 12 && hits >= 2)
}

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

/**
 * «También: …» para los resultados de búsqueda: el nombre original cuando difiere del
 * mostrado. El índice del servidor guarda los nombres es-MX/regionales y el buscador
 * encuentra por ellos («vaiana» → «Moana», «buscando a nemo» → «Finding Nemo»): sin esta
 * línea, el resultado parecía de otro título. Vacío cuando no hay nada que contar
 * (mismo nombre o payload viejo sin el campo).
 */
fun Film.akaLine(): String {
    val original = normalizeDisplayName(originalTitle)
    if (original.isBlank()) return ""
    if (original.equals(title.trim(), ignoreCase = true)) return ""
    return "También: $original"
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
 * A blank query matches everything. Optimized for TV and low-end CPU: the catalogue's films and
 * their folded titles are computed once per rows set (antes se normalizaba todo en cada tecla).
 */
fun searchFilms(rows: List<CatalogRow>, query: String): List<Film> {
    val cleanQuery = fold(query).trim()
    val words = cleanQuery.split(' ').filter { it.isNotBlank() }
    val corpus = searchCorpus(rows)
    if (words.isEmpty()) return corpus.films
    return corpus.films.filterIndexed { index, film ->
        val titleNorm = corpus.titles[index]
        if (words.all { it in titleNorm }) return@filterIndexed true
        val haystack = "$titleNorm ${fold(film.synopsis)} ${corpus.years[index]}"
        words.all { it in haystack }
    }
}

/** The films of [rows] with their folded titles precomputed; rebuilt only when rows change. */
private class SearchCorpus(val films: List<Film>, val titles: List<String>, val years: List<Int>)

private var searchCorpusRows: List<CatalogRow>? = null
private var searchCorpusCache = SearchCorpus(emptyList(), emptyList(), emptyList())

@Synchronized
private fun searchCorpus(rows: List<CatalogRow>): SearchCorpus {
    if (searchCorpusRows == rows) return searchCorpusCache
    val films = allFilms(rows)
    val built = SearchCorpus(films, films.map { fold(it.title) }, films.map { it.year })
    searchCorpusRows = rows
    searchCorpusCache = built
    return built
}

private val DIACRITICS = Regex("\\p{Mn}+")

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")
