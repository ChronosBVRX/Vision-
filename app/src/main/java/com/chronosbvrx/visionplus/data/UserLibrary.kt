package com.chronosbvrx.visionplus.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        context.applicationContext.getSharedPreferences(
            com.chronosbvrx.visionplus.data.profile.ProfileStore.prefsName(context, PREFS),
            Context.MODE_PRIVATE,
        )

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
    /** Marcas explícitas del usuario (OK largo): mandan sobre el progreso y sobre las viejas. */
    private const val K_MANUAL_EPS = "watched_episodes_manual"
    /** «No visto» explícito: manda aunque el historial haya llegado al final. */
    private const val K_UNWATCHED_EPS = "watched_episodes_unwatched"

    /**
     * A partir de este porcentaje un capítulo cuenta como visto aunque falten minutos para
     * que terminen los créditos. 85 % cubre el caso normal (si el episodio dura 42 min, se
     * marca a los ~35:42) y deja margen para una salida antes del final.
     */
    internal const val WATCHED_FRACTION = 0.85f

    /** Progreso 0..1 de un recuerdo; null cuando la duración no se conoce. */
    internal fun watchedProgressOf(entry: com.chronosbvrx.visionplus.data.ResumeEntry?): Float? {
        val duration = entry?.durationMs ?: 0L
        if (entry == null || duration <= 0L) return null
        return (entry.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    private fun episodeKey(seriesId: String, episode: Int) = "$seriesId|$episode"

    fun watchedEpisodes(context: Context): Set<String> =
        prefs(context).getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet()

    /** Capítulos que la persona marcó explícitamente como «no visto» (manda sobre todo). */
    fun unwatchedEpisodes(context: Context): Set<String> =
        prefs(context).getStringSet(K_UNWATCHED_EPS, emptySet()) ?: emptySet()

    fun isEpisodeWatched(context: Context, seriesId: String, episode: Int): Boolean =
        isEpisodeWatched(context, seriesId, episode, progress = null)

    /**
     * ¿Cuenta como visto? Prioridad:
     * 1. «No visto» explícito del usuario.
     * 2. Marca manual del usuario.
     * 3. Progreso real del historial (≥ [WATCHED_FRACTION]).
     * 4. Marca automática/legacy (solo cuando no hay progreso conocido).
     *
     * El orden importa: un capítulo que se abrió y solo avanzó 3 minutos no puede quedar
     * «Visto» por una marca vieja de la app, pero una marca manual del usuario sí manda.
     */
    fun isEpisodeWatched(context: Context, seriesId: String, episode: Int, progress: Float?): Boolean {
        val key = episodeKey(seriesId, episode)
        val p = prefs(context)
        if (key in (p.getStringSet(K_UNWATCHED_EPS, emptySet()) ?: emptySet())) return false
        if (key in (p.getStringSet(K_MANUAL_EPS, emptySet()) ?: emptySet())) return true
        if (progress != null) return progress >= WATCHED_FRACTION
        return key in (p.getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet())
    }

    /**
     * Estado de una temporada completa leído UNA vez: auto, manual, «no visto» y el historial
     * freso (que escribe `:x4k`). La ficha pinta la lista sin abrir preferencias por capítulo.
     */
    fun episodeWatchStates(
        context: Context,
        seriesId: String,
        episodeCodes: List<String>,
        history: List<com.chronosbvrx.visionplus.data.ResumeEntry>,
    ): List<Boolean> {
        val p = prefs(context)
        val auto = p.getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet()
        val manual = p.getStringSet(K_MANUAL_EPS, emptySet()) ?: emptySet()
        val unwatched = p.getStringSet(K_UNWATCHED_EPS, emptySet()) ?: emptySet()
        val progressByCode = history.associate { it.key to watchedProgressOf(it) }
        return episodeCodes.mapIndexed { index, code ->
            val key = episodeKey(seriesId, index + 1)
            when {
                key in unwatched -> false
                key in manual -> true
                else -> progressByCode[code]?.let { it >= WATCHED_FRACTION } ?: (key in auto)
            }
        }
    }

    fun markEpisodeWatched(context: Context, seriesId: String, episode: Int, watched: Boolean = true) {
        val p = prefs(context)
        val key = episodeKey(seriesId, episode)
        val auto = (p.getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet()).toMutableSet()
        val manual = (p.getStringSet(K_MANUAL_EPS, emptySet()) ?: emptySet()).toMutableSet()
        val unwatched = (p.getStringSet(K_UNWATCHED_EPS, emptySet()) ?: emptySet()).toMutableSet()
        if (watched) {
            auto.add(key)
            manual.add(key)
            unwatched.remove(key)
        } else {
            auto.remove(key)
            manual.remove(key)
            unwatched.add(key)
        }
        p.edit()
            .putStringSet(K_WATCHED_EPS, auto)
            .putStringSet(K_MANUAL_EPS, manual)
            .putStringSet(K_UNWATCHED_EPS, unwatched)
            .apply()
    }

    /**
     * Marca automática por progreso (la llama el reproductor cada poco). Es idempotente y
     * no escribe si el capítulo ya estaba marcado. Devuelve true solo la primera vez.
     */
    fun markEpisodeProgressWatched(
        context: Context,
        seriesId: String,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (durationMs <= 0L || positionMs <= 0L) return false
        if (positionMs.toFloat() / durationMs.toFloat() < WATCHED_FRACTION) return false
        val p = prefs(context)
        val key = episodeKey(seriesId, episode)
        val auto = (p.getStringSet(K_WATCHED_EPS, emptySet()) ?: emptySet()).toMutableSet()
        val unwatched = (p.getStringSet(K_UNWATCHED_EPS, emptySet()) ?: emptySet()).toMutableSet()
        val wasMarked = key in auto && key !in unwatched
        if (wasMarked) return false
        auto.add(key)
        unwatched.remove(key)
        p.edit()
            .putStringSet(K_WATCHED_EPS, auto)
            .putStringSet(K_UNWATCHED_EPS, unwatched)
            .apply()
        return true
    }

    /** Solo para pruebas: deja el estado de capítulos como recién instalado. */
    internal fun clearAllEpisodeWatchState(context: Context) {
        prefs(context).edit()
            .remove(K_WATCHED_EPS)
            .remove(K_MANUAL_EPS)
            .remove(K_UNWATCHED_EPS)
            .apply()
    }


    private fun toggle(p: SharedPreferences, key: String, film: Film) {
        val list = read(p, key)
        val next = if (list.any { it.id == film.id }) list.filterNot { it.id == film.id } else listOf(film) + list
        write(p, key, next)
    }

    private fun read(p: SharedPreferences, key: String): List<Film> = runCatching {
        val arr = JSONArray(p.getString(key, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let(::filmFromJson)
        }
    }.getOrDefault(emptyList())

    private fun write(p: SharedPreferences, key: String, films: List<Film>) {
        val arr = JSONArray()
        films.forEach { arr.put(filmToJson(it)) }
        p.edit().putString(key, arr.toString()).apply()
    }

    /** La ficha tal como la guarda la biblioteca (y la sincronización). */
    internal fun filmToJson(f: Film): JSONObject = JSONObject()
        .put("id", f.id).put("t", f.title).put("y", f.year).put("d", f.durationMin)
        .put("s", f.synopsis).put("p", f.posterUrl).put("v", f.videoUrl).put("src", f.sourceUrl)

    /** Inverso de [filmToJson]; los campos ausentes quedan en sus defaults. */
    internal fun filmFromJson(o: JSONObject): Film = Film(
        id = o.optString("id"),
        title = normalizeDisplayName(o.optString("t")),
        year = o.optInt("y"),
        durationMin = o.optInt("d"),
        synopsis = o.optString("s"),
        posterUrl = o.optString("p"),
        videoUrl = o.optString("v"),
        sourceUrl = o.optString("src"),
    )

    /** Reemplaza la lista entera (la usa la sincronización tras fusionar). */
    internal fun replaceFavorites(context: Context, films: List<Film>) {
        write(prefs(context), K_FAVORITES, films.distinctBy { it.id })
    }

    internal fun replaceWatchLater(context: Context, films: List<Film>) {
        write(prefs(context), K_WATCH, films.distinctBy { it.id })
    }

    internal fun replaceWatched(context: Context, episodes: Set<String>) {
        prefs(context).edit().putStringSet(K_WATCHED_EPS, episodes.toSet()).apply()
    }

    /** Reemplaza el set de «no visto» tras una sync. */
    internal fun replaceUnwatched(context: Context, episodes: Set<String>) {
        prefs(context).edit().putStringSet(K_UNWATCHED_EPS, episodes.toSet()).apply()
    }

    /**
     * La copia FRESCA del XML: el reproductor vive en `:x4k` y sus marcas de visto no se ven
     * en la copia en memoria del proceso principal. Se lee el archivo directo, como hace
     * `PlaybackHistory.listFresh`; si no se puede, se cae a la copia normal.
     */
    internal fun watchedEpisodesFresh(context: Context): Set<String> {
        val xml = runCatching {
            File(File(context.applicationContext.dataDir, "shared_prefs"), "vision_user.xml").readText()
        }.getOrNull() ?: return watchedEpisodes(context)
        val open = "<set name=\"$K_WATCHED_EPS\">"
        val start = xml.indexOf(open)
        if (start < 0) return watchedEpisodes(context)
        val end = xml.indexOf("</set>", start)
        if (end < 0) return watchedEpisodes(context)
        val body = xml.substring(start + open.length, end)
        val values = Regex("<string>(.*?)</string>").findAll(body)
            .map { unescapeXml(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toSet()
        return values
    }

    private fun unescapeXml(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    /**
     * Recomendado para ti: cuenta en qué categorías del índice vive lo ya visto y propone títulos
     * de las más afines. Lo reciente pesa más (lo de ayer manda sobre lo de hace meses) y lo ya
     * visto nunca se propone. Devuelve (categoría ancla, títulos) o null si aún no hay señales.
     */
    fun recommendations(indexRows: List<CatalogRow>, played: List<Film>, limit: Int = 40): Pair<String, List<Film>>? {
        if (played.isEmpty() || indexRows.isEmpty()) return null
        val playedIds = played.map { it.id }.toSet()
        val skip = setOf("estrenos", "recomendados", "tv-en-vivo")
        // Índice filmId -> hileras (una sola pasada): antes era O(15 × filas × films) por llamada.
        val rowsById = HashMap<String, CatalogRow>(indexRows.size)
        val filmRows = HashMap<String, MutableList<String>>()
        indexRows.forEach { row ->
            rowsById[row.id] = row
            if (row.id in skip) return@forEach
            row.films.forEach { film ->
                filmRows.getOrPut(film.id) { ArrayList(2) }.add(row.id)
            }
        }
        val hits = LinkedHashMap<String, Int>()
        val recent = played.take(15)
        recent.forEachIndexed { idx, film ->
            // `played` va de lo más reciente a lo más viejo: lo de ayer pesa triple.
            val weight = 3 - (idx * 2 / recent.size.coerceAtLeast(1))
            filmRows[film.id]?.forEach { rowId -> hits[rowId] = (hits[rowId] ?: 0) + weight }
        }
        val top = hits.entries.sortedByDescending { it.value }.take(4).map { it.key }
        if (top.isEmpty()) return null
        val sources = top.mapNotNull { rowsById[it] }
        val anchor = sources.firstOrNull()?.title.orEmpty()
        val films = sources.flatMap { it.films }.distinctBy { it.id }
            .filter { it.id !in playedIds }
            .sortedByDescending { Popularity.rankScore(it) }
            .take(limit)
        return if (films.isEmpty()) null else anchor to films
    }
}
