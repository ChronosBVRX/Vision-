package com.chronosbvrx.visionplus.data.sports

import android.content.Context
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.profile.ProfileStore

/**
 * Equipos favoritos de Deportes, por perfil y en el equipo (sin cuentas ni servidor).
 *
 * Se guardan las claves normalizadas de los equipos del partido. Al marcar un partido
 * como favorito se siguen sus DOS equipos, para que el filtro «Favoritos» muestre
 * también sus próximos partidos, no solo esa confrontación concreta.
 */
object SportsFavorites {

    private const val PREFS = "vision_sports_favorites"
    private const val KEY = "teams"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        ProfileStore.prefsName(context, PREFS),
        Context.MODE_PRIVATE,
    )

    fun teams(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty().toSet()

    /** Claves de equipo sin el prefijo de club («Club América» y «América» son el mismo). */
    private val CLUB_PREFIXES = listOf("club ", "fc ", "cf ", "cd ", "sc ", "ac ", "deportivo ")

    private fun teamKey(name: String): String {
        var key = GeeAgenda.fold(name).trim()
        CLUB_PREFIXES.forEach { prefix -> if (key.startsWith(prefix)) key = key.removePrefix(prefix) }
        return key.trim()
    }

    /** Las claves normalizadas que representan al evento (sus equipos, o su nombre si no hay). */
    fun keys(event: GeeSportsSource.GeeEvent): List<String> {
        val home = event.homeName.ifBlank { event.teamA }
        val away = event.awayName.ifBlank { event.teamB }
        val hasTeams = home.isNotBlank() && away.isNotBlank() &&
            !home.equals(event.name, ignoreCase = true) &&
            !away.equals(event.name, ignoreCase = true)
        return if (hasTeams) {
            listOf(teamKey(home), teamKey(away)).filter { it.isNotBlank() }.distinct()
        } else {
            listOf(GeeAgenda.fold(event.name)).filter { it.isNotBlank() }
        }
    }

    /** ¿El evento toca alguno de los equipos favoritos? Puro, para probar sin Android. */
    fun matches(event: GeeSportsSource.GeeEvent, favorites: Set<String>): Boolean =
        favorites.isNotEmpty() && keys(event).any { it in favorites }

    /**
     * Marca o desmarca el evento. Si ya toca un favorito, quita TODAS sus claves;
     * si no, agrega los dos equipos. Puro: la persistencia la hace [toggle].
     */
    fun toggle(event: GeeSportsSource.GeeEvent, favorites: Set<String>): Set<String> {
        val keys = keys(event)
        if (keys.isEmpty()) return favorites
        val next = favorites.toMutableSet()
        if (keys.any { it in next }) {
            keys.forEach { next.remove(it) }
        } else {
            next.addAll(keys)
        }
        return next
    }

    fun isFavorite(context: Context, event: GeeSportsSource.GeeEvent): Boolean =
        matches(event, teams(context))

    /** Alterna y devuelve el conjunto nuevo (la UI lo refleja al instante). */
    fun toggle(context: Context, event: GeeSportsSource.GeeEvent): Set<String> {
        val next = toggle(event, teams(context))
        prefs(context).edit().putStringSet(KEY, next).apply()
        return next
    }
}
