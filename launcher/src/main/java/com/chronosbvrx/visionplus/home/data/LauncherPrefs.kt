package com.chronosbvrx.visionplus.home.data

import android.content.Context

/**
 * Lo que el launcher recuerda entre aperturas: qué apps se usaron (para ordenar el Inicio),
 * cuáles se fijaron con OK largo y cuál fue la última pestaña. Nada sale del equipo.
 */
class LauncherPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("vision_home", Context.MODE_PRIVATE)

    fun recentApps(limit: Int = 8): List<String> =
        prefs.getString(KEY_RECENT, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .take(limit)

    fun noteLaunch(packageName: String) {
        if (packageName.isBlank()) return
        val next = (listOf(packageName) + recentApps(limit = 24).filterNot { it == packageName })
            .take(24)
        prefs.edit().putString(KEY_RECENT, next.joinToString("\n")).apply()
    }

    fun favorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    /** Devuelve true si quedó fijada. */
    fun toggleFavorite(packageName: String): Boolean {
        val current = favorites().toMutableSet()
        val added = if (current.remove(packageName)) false else {
            current += packageName
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return added
    }

    var lastTab: String
        get() = prefs.getString(KEY_TAB, "home").orEmpty()
        set(value) = prefs.edit().putString(KEY_TAB, value).apply()

    var dismissedUpdate: Int
        get() = prefs.getInt(KEY_DISMISSED_UPDATE, 0)
        set(value) = prefs.edit().putInt(KEY_DISMISSED_UPDATE, value).apply()

    /** Última versión de Vision+ vista en el release (caché de 24 h). */
    var lastReleaseTag: String
        get() = prefs.getString(KEY_LAST_TAG, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_TAG, value).apply()

    var lastReleaseAt: Long
        get() = prefs.getLong(KEY_LAST_TAG_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_TAG_AT, value).apply()

    private companion object {
        const val KEY_RECENT = "recent_apps"
        const val KEY_FAVORITES = "favorite_apps"
        const val KEY_TAB = "last_tab"
        const val KEY_DISMISSED_UPDATE = "dismissed_update"
        const val KEY_LAST_TAG = "last_release_tag"
        const val KEY_LAST_TAG_AT = "last_release_tag_at"
    }
}
