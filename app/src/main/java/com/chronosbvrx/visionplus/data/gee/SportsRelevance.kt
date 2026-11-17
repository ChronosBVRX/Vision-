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

    /**
     * Cuántos canales/señales reales puede ofrecer el evento. No es solo `alive`:
     * un evento puede traer enlaces resolubles, canales XTV de la casa o una guía
     * de TV. `alive` manda (es salud del enlace); los demás suman como respaldo.
     */
    fun availableChannelCount(event: GeeSportsSource.GeeEvent): Int {
        val healthy = maxOf(event.alive.coerceAtLeast(0), event.links.count { it.alive == true })
        val known = event.links.count { link ->
            link.alive != false &&
                (link.link.isNotBlank() || link.resolvable || link.tokenApi.isNotBlank())
        }
        return maxOf(healthy, known, event.xtv.size)
    }

    /**
     * Peso de disponibilidad para desempatar eventos del mismo tier. Los enlaces
     * vivos pesan más que las ranuras por resolver; los canales de la casa y la
     * guía de TV suman porque suelen acompañar a los partidos estelares.
     */
    fun channelScore(event: GeeSportsSource.GeeEvent): Int {
        val healthy = maxOf(event.alive.coerceAtLeast(0), event.links.count { it.alive == true })
        val known = event.links.count { link ->
            link.alive != false &&
                (link.link.isNotBlank() || link.resolvable || link.tokenApi.isNotBlank())
        }
        return healthy * 8 + known * 3 + event.xtv.size * 4 + event.broadcasts.size
    }

    /** ¿Hay algo que reproducir o un canal de la casa? La guía de TV sola no basta. */
    fun hasPlayableChannel(event: GeeSportsSource.GeeEvent): Boolean =
        availableChannelCount(event) > 0

    /**
     * Orden para las listas de Deportes, en todos los deportes:
     *
     * 1. Primero lo que tiene canal reproducible.
     * 2. Luego el `tier` del servidor (1 = principal, 2 = secundario, 3 = nicho).
     * 3. Dentro del mismo tier, más canales disponibles primero.
     * 4. Después la prioridad de liga (Liga MX, Champions… NBA, NFL…).
     *
     * Es el mismo criterio para fútbol, básquet, motor o boxeo: no hay una lista
     * aparte que se olvide de los deportes que no son fútbol.
     */
    val bestFirst: Comparator<GeeSportsSource.GeeEvent> =
        compareBy<GeeSportsSource.GeeEvent>(
            { if (hasPlayableChannel(it)) 0 else 1 },
            { tier(it) },
            { -channelScore(it) },
            { leaguePriority(it) },
        )

    /** Orden estable para listas: principales primero y luego por hora. */
    fun mainFirst(events: List<GeeSportsSource.GeeEvent>): List<GeeSportsSource.GeeEvent> =
        events.sortedWith(
            bestFirst
                .thenBy { it.dateIso }
                .thenBy { it.time },
        )
}
