package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource

/**
 * Lógica pura del mosaico de "Deportes en vivo" (móvil y TV): los equipos de un
 * evento, el marcador, la etiqueta de estado y el pie de transmisiones. Las dos
 * pantallas pintan EXACTAMENTE lo que esta capa decide (una sola fuente).
 */
internal object GeeTiles {

    /**
     * (local, visitante) reales del evento; ("","") cuando el CMS solo trae un
     * nombre de cuadro (carreras, torneos) y no una confrontación de equipos.
     */
    fun teams(event: GeeSportsSource.GeeEvent): Pair<String, String> {
        val home = event.homeName.ifBlank { event.teamA }
        val away = event.awayName.ifBlank { event.teamB }
        val valid = home.isNotBlank() && away.isNotBlank() &&
            !home.equals(event.name, true) && !away.equals(event.name, true)
        return if (valid) home to away else "" to ""
    }

    /** "2 - 1" con marcador de la agenda; "VS" cuando aún no hay goles/puntos. */
    fun score(event: GeeSportsSource.GeeEvent): String {
        val home = event.scoreHome
        val away = event.scoreAway
        return if (home != null && away != null) "$home - $away" else "VS"
    }

    /** "EN VIVO" o "EN VIVO · 23'" cuando la agenda trae el minuto/periodo. */
    fun liveLabel(event: GeeSportsSource.GeeEvent): String {
        val detail = event.detail.trim()
        return if (detail.isBlank() || detail.equals("in", true)) "EN VIVO" else "EN VIVO · $detail"
    }

    /** El estado del partido: en vivo (con minuto), Final, o el día y la hora. */
    fun state(event: GeeSportsSource.GeeEvent, todayIso: String): String = when {
        event.live -> liveLabel(event)
        event.status == "post" -> "Final"
        else -> {
            val whenText = when {
                event.dateIso == todayIso -> "Hoy"
                event.dateIso.isBlank() -> ""
                else -> event.dateText.ifBlank { event.dateIso }
            }
            listOf(whenText, geeTimeLabel(event.time)).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    /** El pie cuando no hay guía de TV: si el partido se puede ver o no. */
    fun streams(event: GeeSportsSource.GeeEvent): String = when {
        event.alive <= 0 -> "Sin señal por ahora"
        event.alive == 1 -> "1 transmisión en vivo"
        else -> "${event.alive} transmisiones en vivo"
    }

    /** Letras de respaldo de un escudo que no carga ("Guadalajara" → "GU", "Real Madrid" → "RM"). */
    fun monogram(name: String): String {
        val words = name.trim().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return "?"
        val meaningful = words.filter { it.length > 2 }
        val base = (if (meaningful.size >= 2) meaningful else words).take(2)
        val letters = base.map { it.first().uppercaseChar() }.joinToString("")
        return if (letters.length >= 2) letters else name.trim().take(2).uppercase().ifBlank { "?" }
    }
}
