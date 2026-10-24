package com.chronosbvrx.visionplus.data.search

import android.content.Context
import java.text.Normalizer

/**
 * Autocompletar del buscador: a partir de lo que se lleva escrito propone **títulos completos**
 * (no palabras sueltas), para no teclear todo. Es puro y sin red: se alimenta de los títulos que
 * ya están en memoria (los resultados que van llegando y el catálogo cargado).
 */
private val DIACRITICS = Regex("\\p{Mn}+")

/** Minúsculas y sin acentos: «Guerra de Papás» y «guerra de papas» se comparan igual. */
internal fun foldQuery(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Sugerencias para [query]: primero los títulos que **empiezan** con lo escrito, luego los que
 * tienen una palabra que empieza así y al final los que solo lo contienen. Sin repetidos (por
 * forma plegada) y sin devolver exactamente lo ya escrito (no aporta nada).
 */
internal fun suggestTitles(query: String, titles: List<String>, limit: Int = 6): List<String> {
    val q = foldQuery(query)
    if (q.isEmpty()) return emptyList()
    val seen = HashSet<String>()
    val prefix = ArrayList<String>()
    val wordStart = ArrayList<String>()
    val contains = ArrayList<String>()
    for (raw in titles) {
        val title = raw.trim()
        if (title.isEmpty()) continue
        val folded = foldQuery(title)
        if (folded.isEmpty() || folded == q) continue
        if (!seen.add(folded)) continue
        when {
            folded.startsWith(q) -> prefix.add(title)
            folded.contains(" $q") -> wordStart.add(title)
            folded.contains(q) -> contains.add(title)
        }
    }
    val out = ArrayList<String>(limit)
    for (bucket in listOf(prefix, wordStart, contains)) {
        for (title in bucket) {
            if (out.size >= limit) return out
            out.add(title)
        }
    }
    return out
}

/** Filtros del buscador: todo, películas o series (series, anime, kids y novelas). */
internal enum class SearchFilter(val label: String) {
    ALL("Todo"),
    MOVIES("Películas"),
    SERIES("Series"),
}

/** ¿Este título entra en el filtro? Los títulos sin clasificar se quedan en «Todo» y «Series». */
internal fun matchesSearchFilter(kind: String, filter: SearchFilter): Boolean = when (filter) {
    SearchFilter.ALL -> true
    SearchFilter.MOVIES -> kind.equals("movie", ignoreCase = true)
    SearchFilter.SERIES -> !kind.equals("movie", ignoreCase = true)
}

/**
 * Búsquedas recientes (locales, sin red): se guardan las últimas [MAX] consultas ya asentadas y
 * se muestran como atajos cuando el campo está vacío.
 */
internal object RecentSearches {
    private const val PREFS = "vision_search"
    private const val KEY = "recent"
    internal const val MAX = 8

    /** Pone [query] al frente sin duplicar (comparando sin acentos ni mayúsculas). */
    internal fun merge(existing: List<String>, query: String, max: Int = MAX): List<String> {
        val clean = query.replace('\n', ' ').trim()
        if (clean.isEmpty()) return existing.take(max)
        val folded = foldQuery(clean)
        val out = ArrayList<String>(max)
        out.add(clean)
        for (item in existing) {
            if (out.size >= max) break
            if (foldQuery(item) == folded) continue
            out.add(item)
        }
        return out
    }

    fun list(context: Context): List<String> {
        val raw = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }.getOrDefault("")
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)
    }

    fun add(context: Context, query: String) {
        if (query.trim().isEmpty()) return
        save(context, merge(list(context), query))
    }

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, items: List<String>) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, items.joinToString("\n"))
                .apply()
        }
    }
}
