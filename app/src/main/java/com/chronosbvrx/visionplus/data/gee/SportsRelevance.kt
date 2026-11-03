package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.home.contract.SportsLeagues

/**
 * Puente entre el `tier` que calcula el servidor y la app.
 *
 * El servidor es la autoridad: si el payload trae `tier` 1..3 se usa tal cual.
 * Si viene de una caché vieja (0) o de un servidor anterior se recalcula con
 * [SportsLeagues], que compara nombres exactos y alias, nunca por subcadena.
 */
object SportsRelevance {

    fun tier(event: GeeSportsSource.GeeEvent): Int {
        if (event.tier in 1..3) return event.tier
        val label = leagueName(event)
        return SportsLeagues.tier(label, event.category, event.leagueId)
    }

    fun isMain(event: GeeSportsSource.GeeEvent): Boolean = tier(event) == 1

    fun leagueName(event: GeeSportsSource.GeeEvent): String =
        event.leagueLabel.ifBlank { event.name.substringBefore(',').trim() }

    fun canonicalLeague(event: GeeSportsSource.GeeEvent): String =
        SportsLeagues.canonical(leagueName(event))

    fun leaguePriority(event: GeeSportsSource.GeeEvent): Int =
        SportsLeagues.leaguePriority(leagueName(event))

    /** Orden estable para listas: principales primero y luego por hora. */
    fun mainFirst(events: List<GeeSportsSource.GeeEvent>): List<GeeSportsSource.GeeEvent> =
        events.sortedWith(
            compareBy<GeeSportsSource.GeeEvent> { tier(it) }
                .thenBy { leaguePriority(it) }
                .thenBy { it.dateIso }
                .thenBy { it.time },
        )
}
