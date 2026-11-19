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
 * por `tmdbId`, ni por grupo de temporadas—, no se mezcla cine con series y, con
 * género conocido, **no se rellena con otros géneros**: una película infantil sigue
 * en animación/familia y una de terror sigue en terror/suspenso.
 */
object Related {

    private val GENRE_SPLIT = Regex("[,/|·]")
    private val ACCENTS = Regex("\\p{M}+")

    /**
     * Claves canónicas de género. El portal, TMDB y las hileras del servidor escriben
     * el mismo género en inglés, español o con variantes («Sci-Fi & Fantasy»,
     * «Thriller», «Bélica»…). Comparar por clave —y no por subcadena— permite cruzar
     * «Animation» con la hilera «Animación» y evita falsos positivos.
     */
    private val GENRE_KEYS: Map<String, List<String>> = mapOf(
        "accion" to listOf("accion"),
        "action" to listOf("accion"),
        "action & adventure" to listOf("accion", "aventuras"),
        "action adventure" to listOf("accion", "aventuras"),
        "aventura" to listOf("aventuras"),
        "aventuras" to listOf("aventuras"),
        "adventure" to listOf("aventuras"),
        "adventures" to listOf("aventuras"),
        "animacion" to listOf("animacion"),
        "animation" to listOf("animacion"),
        "cartoon" to listOf("animacion"),
        "dibujos animados" to listOf("animacion"),
        "anime" to listOf("anime"),
        "animacion japonesa" to listOf("anime"),
        "comedia" to listOf("comedia"),
        "comedy" to listOf("comedia"),
        "crimen" to listOf("crimen"),
        "crime" to listOf("crimen"),
        "documental" to listOf("documental"),
        "documentary" to listOf("documental"),
        "drama" to listOf("drama"),
        "familia" to listOf("familia"),
        "family" to listOf("familia"),
        "fantasia" to listOf("fantasia"),
        "fantasy" to listOf("fantasia"),
        "guerra" to listOf("guerra"),
        "war" to listOf("guerra"),
        "belica" to listOf("guerra"),
        "war & politics" to listOf("guerra"),
        "historia" to listOf("historia"),
        "history" to listOf("historia"),
        "biografia" to listOf("historia"),
        "biography" to listOf("historia"),
        "infantil" to listOf("infantil"),
        "kids" to listOf("infantil"),
        "children" to listOf("infantil"),
        "children's" to listOf("infantil"),
        "kids & family" to listOf("infantil", "familia"),
        "family & kids" to listOf("infantil", "familia"),
        "misterio" to listOf("misterio"),
        "mystery" to listOf("misterio"),
        "musica" to listOf("musica"),
        "music" to listOf("musica"),
        "musical" to listOf("musica"),
        "occidental" to listOf("occidental"),
        "western" to listOf("occidental"),
        "reality" to listOf("reality"),
        "reality-tv" to listOf("reality"),
        "reality tv" to listOf("reality"),
        "romance" to listOf("romance"),
        "romantico" to listOf("romance"),
        "romantica" to listOf("romance"),
        "ciencia ficcion" to listOf("ciencia ficcion"),
        "ciencia-ficcion" to listOf("ciencia ficcion"),
        "sci fi" to listOf("ciencia ficcion"),
        "sci-fi" to listOf("ciencia ficcion"),
        "science fiction" to listOf("ciencia ficcion"),
        "sci fi & fantasy" to listOf("ciencia ficcion", "fantasia"),
        "sci-fi & fantasy" to listOf("ciencia ficcion", "fantasia"),
        "science fiction & fantasy" to listOf("ciencia ficcion", "fantasia"),
        "terror" to listOf("terror"),
        "horror" to listOf("terror"),
        "suspenso" to listOf("suspenso"),
        "suspense" to listOf("suspenso"),
        "thriller" to listOf("suspenso"),
        "telenovela" to listOf("telenovela"),
        "novela" to listOf("telenovela"),
        "novelas" to listOf("telenovela"),
        "soap" to listOf("telenovela"),
        "soap opera" to listOf("telenovela"),
        "dorama" to listOf("telenovela"),
        "deporte" to listOf("deporte"),
        "sport" to listOf("deporte"),
        "sports" to listOf("deporte"),
    )

    /** Orden del género primario (el mismo del índice): lo específico antes que el paraguas. */
    private val PRIMARY_ORDER = listOf(
        "telenovela", "terror", "suspenso", "accion", "ciencia ficcion", "animacion",
        "crimen", "misterio", "guerra", "occidental", "aventuras", "fantasia", "musica",
        "deporte", "documental", "reality", "historia", "romance", "familia", "infantil",
        "comedia", "drama",
    )

    /** En modo infantil jamás se cuelan estos géneros, aunque compartan hilera. */
    private val UNSAFE_FOR_KIDS = setOf("terror", "suspenso", "crimen", "guerra")

    /** Géneros que hacen que una tarjeta sea apta para una semilla infantil. */
    private val KIDS_SAFE = setOf("infantil", "familia", "animacion")

    /**
     * La sección del índice que corresponde al tipo de la ficha. El portal etiqueta
     * «Anime» a cualquier animación (Shrek, Monsters Inc…): solo es anime de verdad
     * cuando el idioma original es japonés o no se conoce.
     */
    internal fun sectionFor(film: Film): String = when {
        film.kind.equals("anime", ignoreCase = true) &&
            (film.originalLanguage.isBlank() || film.originalLanguage.equals("ja", ignoreCase = true)) -> "anime"
        film.kind.equals("kids", ignoreCase = true) -> if (film.seasons.isNotEmpty()) "series" else "movies"
        film.kind.equals("series", ignoreCase = true) -> "series"
        film.kind.equals("movie", ignoreCase = true) -> "movies"
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

    /** Pista única: géneros de la ficha + tags del portal (coma, barra, tubería o punto medio). */
    internal fun genreHint(film: Film, portalTags: String = ""): String =
        (film.genres + portalTags.split(GENRE_SPLIT).map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

    /** Claves canónicas de una lista de géneros («Animation, Family» → animacion, familia). */
    internal fun genreKeys(raw: String): List<String> {
        val keys = LinkedHashSet<String>()
        raw.split(GENRE_SPLIT).forEach { part ->
            val text = fold(part)
            if (text.isNotBlank()) keys.addAll(GENRE_KEYS[text].orEmpty())
        }
        return keys.toList()
    }

    /** El primario de la ficha: el primero según el orden del índice. */
    private fun primaryKey(keys: List<String>): String? =
        PRIMARY_ORDER.firstOrNull { it in keys } ?: keys.firstOrNull()

    private fun rowMatches(rowKeys: Set<String>, genreKeys: Set<String>): Boolean =
        genreKeys.isNotEmpty() && rowKeys.any { it in genreKeys }

    /** Semilla infantil: la recomendación debe seguir en el mismo público. */
    private fun isKidsTone(film: Film, keys: Set<String>): Boolean =
        film.kind.equals("kids", ignoreCase = true) ||
            "infantil" in keys ||
            ("animacion" in keys && "familia" in keys)

    /** Una tarjeta apta para una semilla infantil nunca es de terror/suspenso/crimen/guerra. */
    private fun isKidsSafe(candidate: Film, rowKeys: Set<String>): Boolean {
        if (candidate.kind.equals("kids", ignoreCase = true)) return true
        val keys = genreKeys(candidate.genres.joinToString(", ")).ifEmpty { rowKeys.toList() }
        if (keys.any { it in UNSAFE_FOR_KIDS }) return false
        return keys.any { it in KIDS_SAFE } || rowKeys.any { it in KIDS_SAFE }
    }

    private fun sameWork(current: Film, other: Film): Boolean {
        if (other.id == current.id) return true
        if (current.tmdbId > 0 && other.tmdbId == current.tmdbId) return true
        val a = normalizeDisplayName(current.title)
        val b = normalizeDisplayName(other.title)
        if (a.isNotBlank() && b.isNotBlank() && a.equals(b, ignoreCase = true)) return true
        // En series, mismo grupo = misma obra (temporadas). En películas se permite
        // sugerir hermanas de saga (mismo grupo, título distinto).
        if (current.kind != "movie" && current.groupId.isNotBlank() && other.groupId == current.groupId) return true
        return false
    }

    /** Raíz de saga: primeras dos palabras (o hasta separador) del título normalizado. */
    private fun sagaRoot(title: String): String {
        val t = normalizeDisplayName(title).lowercase(Locale.ROOT)
        val base = t.split(Regex("[:\\-–—]")).firstOrNull()?.trim().orEmpty()
        val words = base.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(2).joinToString(" ")
    }

    /** Coincidencia aproximada de títulos (prefijo compartido de al menos 4 letras). */
    private fun approxTitle(a: String, b: String): Boolean {
        val na = normalizeDisplayName(a).lowercase(Locale.ROOT)
        val nb = normalizeDisplayName(b).lowercase(Locale.ROOT)
        if (na.isBlank() || nb.isBlank()) return false
        val minLen = minOf(na.length, nb.length)
        if (minLen < 4) return false
        var common = 0
        for (i in 0 until minLen) {
            if (na[i] == nb[i]) common++ else break
        }
        return common >= 4
    }

    private fun isCandidate(current: Film, candidate: Film): Boolean {
        if (sameWork(current, candidate)) return false
        if (candidate.videoUrl.isBlank()) return false
        return true
    }

    /** ¿Es cine? Ni series ni novelas: habilita el empuje de sagas. */
    private fun isMovieKind(film: Film): Boolean =
        film.kind.equals("movie", ignoreCase = true) ||
            film.kind.equals("kids", ignoreCase = true) ||
            (film.kind.isBlank() && film.seasons.isEmpty())

    /**
     * Género de la ficha cuando no hay metadatos externos: primero los géneros que
     * el catálogo ya trae para la obra (TMDB, en español) y, si no, las hileras del
     * índice que la contienen («Drama», «Acción»…).
     */
    internal fun inferGenreHint(film: Film, rows: List<CatalogRow>): String {
        val match = rows.asSequence()
            .flatMap { it.films.asSequence() }
            .firstOrNull { sameWork(film, it) && it.genres.isNotEmpty() }
        if (match != null) return match.genres.joinToString(", ")
        return rows.filter { row -> row.films.any { sameWork(film, it) } }
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }

    /**
     * Elige hasta [limit] títulos del mismo género. Con género conocido **no** se
     * rellena con otras hileras: es mejor no sugerir nada que mandar terror después
     * de una película infantil. Solo si el género es desconocido se usa el mismo
     * tipo y la popularidad como último recurso.
     */
    internal fun pickRelated(
        film: Film,
        rows: List<CatalogRow>,
        genreHint: String = "",
        limit: Int = 12,
    ): List<Film> {
        if (rows.isEmpty() || limit <= 0) return emptyList()
        val hint = genreHint.ifBlank { film.genres.joinToString(", ") }
        val keys = genreKeys(hint)
        val keySet = keys.toSet()
        val primary = primaryKey(keys)
        val strictGenre = keySet.isNotEmpty()
        val kidsTone = isKidsTone(film, keySet)
        // En infantil, «Infantil», «Familia» y «Animación» son el mismo público:
        // si la hilera exacta no existe, las otras dos siguen siendo afines.
        val matchKeys = if (kidsTone) keySet + KIDS_SAFE else keySet
        // La mejor puntuación de cada obra: si aparece en dos hileras afines, manda
        // la del género primario (160) y no se duplica.
        val best = LinkedHashMap<String, Pair<Film, Int>>()
        for (row in rows) {
            val rowKeys = genreKeys(row.title).toSet()
            val rowMatch = rowMatches(rowKeys, matchKeys)
            if (strictGenre && !rowMatch) continue
            for (candidate in row.films) {
                if (!isCandidate(film, candidate)) continue
                if (kidsTone && !isKidsSafe(candidate, rowKeys)) continue
                val key = if (candidate.tmdbId > 0) "tmdb:${candidate.tmdbId}" else candidate.id
                var score = when {
                    !strictGenre -> 0
                    primary != null && primary in rowKeys -> 160
                    else -> 120
                }
                if (candidate.kind.isNotBlank() && candidate.kind == film.kind) score += 25
                if (candidate.year > 0 && film.year > 0) {
                    score += (10 - abs(candidate.year - film.year)).coerceIn(0, 10)
                }
                // Saga: títulos con la misma raíz se priorizan (y el siguiente capítulo más).
                val sameSaga = isMovieKind(film) &&
                    sagaRoot(candidate.title) == sagaRoot(film.title) &&
                    sagaRoot(film.title).isNotBlank()
                if (sameSaga) {
                    score += 200
                    if (film.year > 0 && candidate.year > film.year) score += 60
                }
                // Títulos aproximados (mismo nombre/variante).
                if (approxTitle(candidate.title, film.title) && !sameSaga) score += 80
                val previous = best[key]
                if (previous == null || score > previous.second) best[key] = candidate to score
            }
        }
        if (best.isEmpty()) return emptyList()
        return best.values
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
     * El nombre de la obra no siempre está en la primera página de su género (el
     * índice topa cada hilera). La búsqueda del servidor sí devuelve sus géneros.
     */
    private suspend fun genresFromSearch(context: Context, film: Film): String {
        val query = normalizeDisplayName(film.title).trim()
        if (query.isBlank()) return ""
        val found = runCatching { RemoteIndexSource.search(query, limit = 20) }
            .getOrDefault(emptyList())
        val match = found.firstOrNull { sameWork(film, it) && it.genres.isNotEmpty() }
            ?: found.firstOrNull { approxTitle(it.title, film.title) && it.genres.isNotEmpty() }
        return match?.genres?.joinToString(", ").orEmpty()
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
        if (limit <= 0) return emptyList()
        val rows = runCatching {
            RemoteIndexSource.section(context, sectionFor(film), limit = 40)
        }.getOrNull().orEmpty()
        if (rows.isEmpty()) return emptyList()
        // 1) La ficha (o quien la abre) ya trae el género. 2) La hilera que la
        // contiene lo revela. 3) La búsqueda del servidor lo recupera aunque la
        // obra no esté en la primera página de su género.
        var hint = genreHint.ifBlank { film.genres.joinToString(", ") }
        if (hint.isBlank()) hint = inferGenreHint(film, rows)
        if (hint.isBlank()) hint = genresFromSearch(context, film)
        val picked = pickRelated(film, rows, hint, limit)
        if (picked.isNotEmpty()) return picked
        // Con género conocido, cambiar de género para "llenar" la hilera es peor
        // que no mostrar sugerencias.
        if (genreKeys(hint).isNotEmpty()) return emptyList()
        return pickRelated(film, rows, genreHint = "", limit = limit)
    }
}
