package com.chronosbvrx.visionplus.data.follow

import android.content.Context
import com.chronosbvrx.visionplus.data.Film
import org.json.JSONObject

/**
 * Series que sigue el usuario («avísame del capítulo nuevo»): ficha propia → título.
 * prefs locales (sin cuentas): la agenda del servidor ya trae la ficha casada.
 */
object FollowStore {
    private const val PREFS = "vision_follow"
    private const val KEY = "followed_json"
    /** Metadatos de la ficha: permite pintar «Mis series» aunque el índice aún no cargue. */
    private const val KEY_META = "followed_meta_json"
    private const val MAX_FOLLOWS = 200

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        com.chronosbvrx.visionplus.data.profile.ProfileStore.prefsName(context, PREFS),
        Context.MODE_PRIVATE,
    )

    fun followed(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY, null)
        return parseFollows(raw)
    }

    fun isFollowing(context: Context, titleId: String): Boolean {
        val id = titleId.trim()
        return id.isNotBlank() && followed(context).containsKey(id)
    }

    /** Alterna el seguimiento; devuelve el estado nuevo (true = siguiendo). */
    fun toggle(context: Context, titleId: String, title: String): Boolean =
        toggle(context, titleId, title, posterUrl = "", videoUrl = "")

    /**
     * Alterna el seguimiento de una ficha completa. Guarda el póster y la URL de la serie
     * para que «Mis series» pueda abrirla incluso cuando el índice todavía no la trae.
     */
    fun toggle(context: Context, film: Film): Boolean =
        toggle(context, film.id, film.title, film.posterUrl, film.videoUrl)

    private fun toggle(
        context: Context,
        titleId: String,
        title: String,
        posterUrl: String,
        videoUrl: String,
    ): Boolean {
        val id = titleId.trim()
        if (id.isBlank()) return false
        val current = followed(context).toMutableMap()
        val now = if (current.containsKey(id)) {
            current.remove(id)
            false
        } else {
            if (current.size >= MAX_FOLLOWS) current.remove(current.keys.firstOrNull())
            current[id] = title.trim().take(120)
            true
        }
        val prefs = context.getSharedPreferences(
            com.chronosbvrx.visionplus.data.profile.ProfileStore.prefsName(context, PREFS),
            Context.MODE_PRIVATE,
        )
        val edit = prefs.edit().putString(KEY, JSONObject(current as Map<*, *>).toString())
        val meta = meta(context).toMutableMap()
        if (now) {
            meta[id] = Film(
                id = id,
                title = title.trim().take(120),
                year = 0,
                durationMin = 0,
                synopsis = "",
                posterUrl = posterUrl.trim(),
                videoUrl = videoUrl.trim(),
                sourceUrl = if (videoUrl.startsWith("xtv:")) "xtv" else "",
            )
        } else {
            meta.remove(id)
        }
        edit.putString(KEY_META, encodeMeta(meta)).apply()
        return now
    }

    /** Deja de seguir sin pasar por la ficha (se usa desde «Mis series → Gestionar»). */
    fun unfollow(context: Context, titleId: String): Boolean {
        val id = titleId.trim()
        if (id.isBlank()) return false
        val current = followed(context).toMutableMap()
        val removed = current.remove(id) != null
        val prefs = context.getSharedPreferences(
            com.chronosbvrx.visionplus.data.profile.ProfileStore.prefsName(context, PREFS),
            Context.MODE_PRIVATE,
        )
        prefs.edit()
            .putString(KEY, JSONObject(current as Map<*, *>).toString())
            .putString(KEY_META, encodeMeta(meta(context) - id))
            .apply()
        return removed
    }

    /**
     * Las series seguidas listas para una cuadrícula: primero la ficha del catálogo (la más
     * fresca) y, si no está cargada, la copia guardada al seguirla. Nunca inventa títulos
     * sin póster ni URL: esas filas se omiten.
     */
    fun followedFilms(context: Context, films: List<Film>): List<Film> {
        val followedIds = followed(context).keys
        if (followedIds.isEmpty()) return emptyList()
        val byId = HashMap<String, Film>(films.size)
        films.forEach { byId.putIfAbsent(it.id, it) }
        val saved = meta(context)
        return followedIds.mapNotNull { id ->
            byId[id] ?: saved[id]?.takeIf { it.videoUrl.isNotBlank() || it.posterUrl.isNotBlank() }
        }.distinctBy { it.id }
    }

    /** De la agenda, los estrenos de series seguidas en las próximas [withinMs]. Puro. */
    fun upcomingForFollowed(
        followedIds: Set<String>,
        airings: List<com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.Airing>,
        nowMs: Long,
        withinMs: Long = 36L * 60 * 60 * 1000,
    ): List<com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.Airing> =
        airings.filter { a ->
            (a.titleId.isNotBlank() && followedIds.contains(a.titleId) ||
                a.imdb.isNotBlank() && followedIds.contains(a.imdb)) &&
                a.airtimeMs > nowMs - 60_000L && a.airtimeMs <= nowMs + withinMs
        }.sortedBy { it.airtimeMs }

    /**
     * Revisa estrenos (una vez al día como máximo) y programa avisos 60 min antes.
     * Llamar desde el Inicio en segundo plano; si no hay red o nada nuevo, no hace nada.
     */
    suspend fun checkNewEpisodes(context: Context) {
        val app = context.applicationContext
        val prefs = prefs(app)
        val last = prefs.getLong("last_check", 0L)
        if (System.currentTimeMillis() - last < 20L * 60 * 60 * 1000) return
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        val followed = followed(app)
        if (followed.isEmpty()) return
        val upcoming = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.schedule(2)
            }
        }.getOrDefault(emptyList())
        if (upcoming.isEmpty()) return
        val notified = prefs.getStringSet("notified", emptySet()).orEmpty()
        val fresh = upcomingForFollowed(followed.keys, upcoming, System.currentTimeMillis())
            .filter { a -> notified.none { it == "${a.titleId}|${a.season}|${a.episode}" } }
            .take(5)
        if (fresh.isEmpty()) return
        val keep = notified.toMutableSet()
        for (a in fresh) {
            val whenMs = com.chronosbvrx.visionplus.data.notify.ReminderKit.remindAtMs(a.airtimeMs, 60)
            if (whenMs <= System.currentTimeMillis()) continue
            val title = "Nuevo capítulo: ${followed[a.titleId] ?: a.show}"
            val text = "T${a.season} · C${a.episode}" + (if (a.title.isNotBlank()) " · ${a.title}" else "")
            // El aviso abre la ficha de ESA serie, no el Inicio: el usuario ya sabe qué
            // quiere ver y no debería buscarlo otra vez.
            val uri = if (a.titleId.isNotBlank()) {
                com.chronosbvrx.visionplus.home.contract.VisionLinks.title(
                    ref = a.titleId,
                    title = followed[a.titleId] ?: a.show,
                )
            } else {
                com.chronosbvrx.visionplus.home.contract.VisionLinks.search(a.show)
            }
            if (com.chronosbvrx.visionplus.data.notify.ReminderKit.schedule(
                    app, "follow:${a.titleId}:${a.season}:${a.episode}", whenMs, title, text, uri,
                )
            ) {
                keep += "${a.titleId}|${a.season}|${a.episode}"
            }
        }
        prefs.edit().putStringSet("notified", keep.toList().takeLast(60).toSet()).apply()
    }

    private fun meta(context: Context): Map<String, Film> {
        val raw = prefs(context).getString(KEY_META, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            LinkedHashMap<String, Film>().also { out ->
                o.keys().forEach { id ->
                    val f = o.optJSONObject(id) ?: return@forEach
                    val title = f.optString("t").trim()
                    if (title.isNotBlank()) {
                        out[id] = Film(
                            id = id,
                            title = title,
                            year = f.optInt("y"),
                            durationMin = f.optInt("d"),
                            synopsis = f.optString("s"),
                            posterUrl = f.optString("p"),
                            videoUrl = f.optString("v"),
                            sourceUrl = f.optString("src"),
                        )
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeMeta(films: Map<String, Film>): String {
        val o = JSONObject()
        films.forEach { (id, f) ->
            o.put(
                id,
                JSONObject()
                    .put("t", f.title)
                    .put("y", f.year)
                    .put("d", f.durationMin)
                    .put("s", f.synopsis)
                    .put("p", f.posterUrl)
                    .put("v", f.videoUrl)
                    .put("src", f.sourceUrl),
            )
        }
        return o.toString()
    }

    internal fun parseFollows(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            LinkedHashMap<String, String>().also { out ->
                o.keys().forEach { k ->
                    val v = o.optString(k)
                    if (k.isNotBlank() && v.isNotBlank()) out[k] = v
                }
            }
        }.getOrDefault(emptyMap())
    }
}
