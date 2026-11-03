package com.chronosbvrx.visionplus.data.sports

import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource

/**
 * Búsqueda local de Deportes: equipo, liga, deporte o cadena. No pega al servidor:
 * filtra los eventos que ya están en memoria mientras la persona escribe.
 *
 * Insensible a mayúsculas y acentos (misma normalización que la agenda), y por
 * palabras: «chivas liga mx» encuentra el partido si todas las palabras están.
 */
object SportsSearch {

    fun matches(event: GeeSportsSource.GeeEvent, query: String): Boolean {
        val wanted = GeeAgenda.fold(query).trim()
        if (wanted.isEmpty()) return true
        val home = event.homeName.ifBlank { event.teamA }
        val away = event.awayName.ifBlank { event.teamB }
        val haystack = GeeAgenda.fold(
            listOf(
                event.name,
                home,
                away,
                event.leagueLabel,
                event.category,
                event.broadcasts.joinToString(" "),
            ).joinToString(" "),
        )
        return wanted.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .all { haystack.contains(it) }
    }
}
