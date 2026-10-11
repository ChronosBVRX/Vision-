package com.chronosbvrx.visionplus.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo guardado y lo aprendido: «Ver más tarde», «Favoritos» y el historial de lo reproducido.
 * Vive en SharedPreferences (sobrevive reinicios) y alimenta las recomendaciones del inicio:
 * las categorías donde vive lo que ya viste mandan, sin repetir lo guardado ni lo visto.
 */
object UserLibrary {
    private const val PREFS = "vision_user"
    private const val K_WATCH = "watch_later"
    private const val K_FAVORITES = "favorites"
    private const val K_PLAYED = "played"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun watchLater(context: Context): List<Film> = read(prefs(context), K_WATCH)
    fun favorites(context: Context): List<Film> = read(prefs(context), K_FAVORITES)
    fun played(context: Context): List<Film> = read(prefs(context), K_PLAYED)

    fun isWatchLater(context: Context, id: String): Boolean = watchLater(context).any { it.id == id }
    fun isFavorite(context: Context, id: String): Boolean = favorites(context).any { it.id == id }

    fun toggleWatchLater(context: Context, film: Film) = toggle(prefs(context), K_WATCH, film)
    fun toggleFavorite(context: Context, film: Film) = toggle(prefs(context), K_FAVORITES, film)

    /** Al abrir una película: alimenta las recomendaciones (máximo 40 recuerdos). */
    fun recordPlay(context: Context, film: Film) {
        val p = prefs(context)
        val rest = read(p, K_PLAYED).filterNot { it.id == film.id }
        write(p, K_PLAYED, (listOf(film) + rest).take(40))
    }

    // ---- Capítulos vistos (series, novelas, anime): local, clave "serieId|capítulo". ----

    private const val K_WATCHED_EPS = "watched_episodes"

    fun watchedEpisodes(context: Context): Set<String> =
        prefs(context).getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet()

    fun isEpisodeWatched(context: Context, seriesId: String, episode: Int): Boolean =
        "$seriesId|$episode" in watchedEpisodes(context)

    fun markEpisodeWatched(context: Context, seriesId: String, episode: Int, watched: Boolean = true) {
        val p = prefs(context)
        val set = (p.getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet()).toMutableSet()
        val key = "$seriesId|$episode"
        if (watched) set.add(key) else set.remove(key)
        p.edit().putStringSet(K_WATCHED_EPS, set).apply()
    }

    private fun toggle(p: SharedPreferences, key: String, film: Film) {
        val list = read(p, key)
        val next = if (list.any { it.id == film.id }) list.filterNot { it.id == film.id } else listOf(film) + list
        write(p, key, next)
    }

    private fun read(p: SharedPreferences, key: String): List<Film> = runCatching {
        val arr = JSONArray(p.getString(key, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Film(
                id = o.optString("id"),
                title = o.optString("t"),
                year = o.optInt("y"),
                durationMin = o.optInt("d"),
                synopsis = o.optString("s"),
                posterUrl = o.optString("p"),
                videoUrl = o.optString("v"),
                sourceUrl = o.optString("src"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(p: SharedPreferences, key: String, films: List<Film>) {
        val arr = JSONArray()
        films.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id).put("t", f.title).put("y", f.year).put("d", f.durationMin)
                    .put("s", f.synopsis).put("p", f.posterUrl).put("v", f.videoUrl).put("src", f.sourceUrl),
            )
        }
        p.edit().putString(key, arr.toString()).apply()
    }

    /**
     * Recomendado para ti: cuenta en qué categorías del índice vive lo ya visto y propone títulos
     * de las más afines. Devuelve (categoría ancla, títulos) o null si aún no hay señales.
     */
    fun recommendations(indexRows: List<CatalogRow>, played: List<Film>, limit: Int = 40): Pair<String, List<Film>>? {
        if (played.isEmpty() || indexRows.isEmpty()) return null
        val playedIds = played.map { it.id }.toSet()
        val skip = setOf("estrenos", "recomendados", "tv-en-vivo")
        val hits = LinkedHashMap<String, Int>()
        played.take(15).forEach { film ->
            indexRows.forEach { row ->
                if (row.id !in skip && row.films.any { it.id == film.id }) {
                    hits[row.id] = (hits[row.id] ?: 0) + 1
                }
            }
        }
        val top = hits.entries.sortedByDescending { it.value }.take(4).map { it.key }
        if (top.isEmpty()) return null
        val sources = top.mapNotNull { id -> indexRows.firstOrNull { it.id == id } }
        val anchor = sources.firstOrNull()?.title.orEmpty()
        val films = sources.flatMap { it.films }.distinctBy { it.id }.filter { it.id !in playedIds }.take(limit)
        return if (films.isEmpty()) null else anchor to films
    }
}
