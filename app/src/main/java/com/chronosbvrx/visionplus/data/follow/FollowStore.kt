package com.chronosbvrx.visionplus.data.follow

import android.content.Context
import org.json.JSONObject

/**
 * Series que sigue el usuario («avísame del capítulo nuevo»): ficha propia → título.
 * prefs locales (sin cuentas): la agenda del servidor ya trae la ficha casada.
 */
object FollowStore {
    private const val PREFS = "vision_follow"
    private const val KEY = "followed_json"
    private const val MAX_FOLLOWS = 200

    fun followed(context: Context): Map<String, String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return parseFollows(raw)
    }

    fun isFollowing(context: Context, titleId: String): Boolean {
        val id = titleId.trim()
        return id.isNotBlank() && followed(context).containsKey(id)
    }

    /** Alterna el seguimiento; devuelve el estado nuevo (true = siguiendo). */
    fun toggle(context: Context, titleId: String, title: String): Boolean {
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONObject(current as Map<*, *>).toString()).apply()
        return now
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
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
            if (com.chronosbvrx.visionplus.data.notify.ReminderKit.schedule(
                    app, "follow:${a.titleId}:${a.season}:${a.episode}", whenMs, title, text,
                )
            ) {
                keep += "${a.titleId}|${a.season}|${a.episode}"
            }
        }
        prefs.edit().putStringSet("notified", keep.toList().takeLast(60).toSet()).apply()
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
