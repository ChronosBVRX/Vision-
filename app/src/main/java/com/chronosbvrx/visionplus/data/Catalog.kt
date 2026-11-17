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
    /** Reparto normalizado por TMDB (nombres propios; nunca se traducen). */
    val cast: List<String> = emptyList(),
    /** Países de producción ya en español (TMDB por código ISO). */
    val countries: List<String> = emptyList(),
    /** Idioma original TMDB (`ja` = anime real): separa la animación japonesa del resto. */
    val originalLanguage: String = "",
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
    val index = spanishOnlySynopsis(indexSynopsis)
    if (index.isNotBlank()) return index
    return spanishOnlySynopsis(portalSynopsis)
}

/**
 * Sinopsis que la app puede mostrar: la española tal cual y la que está
 * claramente en inglés se descarta. Así una ficha nunca cae al texto inglés del
 * portal cuando TMDB no tiene traducción: prefiere no mostrar sinopsis.
 * Lo que no se reconoce como inglés se conserva (puede ser español corto).
 */
internal fun spanishOnlySynopsis(text: String): String {
    val clean = text.trim()
    if (clean.isBlank()) return ""
    // El inglés manda cuando hay varias palabras funcionales inglesas: un nombre
    // con tilde («Inés») no convierte una sinopsis inglesa en española.
    if (looksEnglish(clean)) return ""
    if (looksSpanish(clean)) return clean
    return clean
}

private val SPANISH_WORDS = setOf(
    "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "que", "y",
    "en", "con", "por", "para", "como", "mas", "pero", "sus", "se", "al", "lo", "es",
    "son", "esta", "estan", "cuando", "donde", "mientras", "tras", "sobre", "entre",
    "desde", "hasta", "sin", "tambien", "todo", "todos", "este", "estos", "estas",
)

private val ENGLISH_WORDS = setOf(
    "the", "and", "of", "to", "in", "is", "are", "was", "were", "with", "for",
    "from", "his", "her", "their", "they", "when", "after", "before", "but",
    "not", "who", "what", "this", "that", "into", "on", "at", "by", "as", "it",
    "she", "he", "we", "you", "has", "have", "had", "will", "would", "can",
    "could", "about", "while", "an", "been", "there", "which", "its", "our",
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

/** Heurística contraria: descarta el texto que es claramente inglés. */
private fun looksEnglish(text: String): Boolean {
    val words = text.lowercase(java.util.Locale.ROOT)
        .split(Regex("[^a-z]+")).filter { it.length >= 2 }
    if (words.size < 5) return false
    val hits = words.count { it in ENGLISH_WORDS }
    return hits >= 3 || (words.size <= 12 && hits >= 2)
}

/**
 * País en español para la ficha/reproductor cuando el dato viene del portal
 * («United States», «USA», «Reino Unido»…). El servidor ya manda los países de
 * TMDB resueltos por ISO; esto cubre el respaldo del portal. Lo desconocido se
 * devuelve tal cual (mejor un nombre que un hueco).
 */
fun spanishCountry(raw: String): String {
    val clean = raw.trim()
    if (clean.isBlank()) return ""
    val key = clean.lowercase(java.util.Locale.ROOT).replace('.', ' ').replace(Regex("\\s+"), " ").trim()
    return COUNTRY_ES[key] ?: clean
}

private val COUNTRY_ES = mapOf(
    "us" to "Estados Unidos", "usa" to "Estados Unidos",
    "united states" to "Estados Unidos", "united states of america" to "Estados Unidos",
    "uk" to "Reino Unido", "united kingdom" to "Reino Unido",
    "great britain" to "Reino Unido", "england" to "Reino Unido",
    "france" to "Francia", "spain" to "España", "mexico" to "México",
    "argentina" to "Argentina", "colombia" to "Colombia", "chile" to "Chile",
    "peru" to "Perú", "brazil" to "Brasil", "canada" to "Canadá",
    "australia" to "Australia", "germany" to "Alemania", "deutschland" to "Alemania",
    "italy" to "Italia", "japan" to "Japón", "south korea" to "Corea del Sur",
    "korea" to "Corea del Sur", "china" to "China", "hong kong" to "Hong Kong",
    "taiwan" to "Taiwán", "india" to "India", "russia" to "Rusia",
    "turkey" to "Turquía", "sweden" to "Suecia", "denmark" to "Dinamarca",
    "norway" to "Noruega", "netherlands" to "Países Bajos", "holland" to "Países Bajos",
    "belgium" to "Bélgica", "switzerland" to "Suiza", "austria" to "Austria",
    "poland" to "Polonia", "portugal" to "Portugal", "ireland" to "Irlanda",
    "new zealand" to "Nueva Zelanda", "south africa" to "Sudáfrica",
    "egypt" to "Egipto", "israel" to "Israel", "thailand" to "Tailandia",
    "indonesia" to "Indonesia", "philippines" to "Filipinas",
    "czech republic" to "República Checa", "czechia" to "República Checa",
    "slovakia" to "Eslovaquia", "hungary" to "Hungría", "romania" to "Rumania",
    "greece" to "Grecia", "finland" to "Finlandia", "iceland" to "Islandia",
    "ukraine" to "Ucrania", "nigeria" to "Nigeria", "kenya" to "Kenia",
    "morocco" to "Marruecos", "cuba" to "Cuba",
    "dominican republic" to "República Dominicana", "puerto rico" to "Puerto Rico",
    "venezuela" to "Venezuela", "ecuador" to "Ecuador", "bolivia" to "Bolivia",
    "paraguay" to "Paraguay", "uruguay" to "Uruguay", "guatemala" to "Guatemala",
    "honduras" to "Honduras", "el salvador" to "El Salvador",
    "nicaragua" to "Nicaragua", "costa rica" to "Costa Rica", "panama" to "Panamá",
    "united arab emirates" to "Emiratos Árabes Unidos", "saudi arabia" to "Arabia Saudita",
    "iran" to "Irán", "iraq" to "Irak", "pakistan" to "Pakistán",
    "bangladesh" to "Bangladés", "vietnam" to "Vietnam", "malaysia" to "Malasia",
    "singapore" to "Singapur", "sri lanka" to "Sri Lanka", "nepal" to "Nepal",
    "kazakhstan" to "Kazajistán", "georgia" to "Georgia", "armenia" to "Armenia",
    "azerbaijan" to "Azerbaiyán", "serbia" to "Serbia", "croatia" to "Croacia",
    "slovenia" to "Eslovenia", "bosnia and herzegovina" to "Bosnia y Herzegovina",
    "bulgaria" to "Bulgaria", "lithuania" to "Lituania", "latvia" to "Letonia",
    "estonia" to "Estonia", "belarus" to "Bielorrusia", "moldova" to "Moldavia",
    "albania" to "Albania", "macedonia" to "Macedonia del Norte", "malta" to "Malta",
    "luxembourg" to "Luxemburgo", "cyprus" to "Chipre", "qatar" to "Catar",
    "kuwait" to "Kuwait", "jordan" to "Jordania", "lebanon" to "Líbano",
    "syria" to "Siria", "afghanistan" to "Afganistán", "ethiopia" to "Etiopía",
    "ghana" to "Ghana", "senegal" to "Senegal", "ivory coast" to "Costa de Marfil",
    "cameroon" to "Camerún", "tanzania" to "Tanzania", "uganda" to "Uganda",
    "zimbabwe" to "Zimbabue", "mozambique" to "Mozambique", "angola" to "Angola",
    "tunisia" to "Túnez", "algeria" to "Argelia", "libya" to "Libia",
    "soviet union" to "Unión Soviética", "yugoslavia" to "Yugoslavia",
    "czechoslovakia" to "Checoslovaquia", "east germany" to "Alemania del Este",
)

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
