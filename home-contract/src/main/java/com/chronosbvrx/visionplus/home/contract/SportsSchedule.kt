package com.chronosbvrx.visionplus.home.contract

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Horario visible de un evento deportivo: la hora real de inicio y una hora de fin
 * estimada por deporte.
 *
 * Ni la agenda de ESPN ni el CMS de GeeSports publican la hora de fin. Para que las
 * tarjetas EN VIVO digan a qué hora empezaron y a qué hora terminan, se usa la
 * duración típica de cada deporte. La app y el launcher pintan la MISMA etiqueta.
 */
object SportsSchedule {

    private val MX_ZONE: TimeZone = TimeZone.getTimeZone("America/Mexico_City")

    /** Duración típica de la ventana de juego, en minutos. */
    fun durationMinutes(category: String): Int = when (fold(category)) {
        "football", "soccer", "futbol" -> 120
        "american football", "futbol americano" -> 195
        "basketball", "basquetbol", "basquet" -> 150
        "baseball", "beisbol" -> 180
        "hockey", "tennis", "tenis" -> 150
        "fighting", "boxing", "mma", "mixed martial arts", "pelea", "peleas", "boxeo" -> 180
        "motorsports", "motorsport", "formula 1", "formula one", "motogp", "moto gp", "nascar" -> 120
        "rugby" -> 120
        "cricket" -> 240
        else -> 120
    }

    /** El fin estimado: inicio + duración típica del deporte. */
    fun endMs(startMs: Long, category: String): Long =
        if (startMs <= 0L) 0L else startMs + durationMinutes(category) * 60_000L

    /** «19:00» en hora del centro de México. */
    fun clock(startMs: Long): String {
        if (startMs <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-MX"))
            .apply { timeZone = MX_ZONE }
            .format(Date(startMs))
    }

    /** «19:00–21:00»; vacío si no hay hora de inicio usable. */
    fun range(startMs: Long, category: String): String {
        val start = clock(startMs)
        if (start.isBlank()) return ""
        val end = clock(endMs(startMs, category))
        return if (end.isBlank()) start else "$start–$end"
    }

    /**
     * La píldora roja de una tarjeta en vivo: conserva el minuto cuando la agenda lo
     * trae y añade SIEMPRE el horario. Sin inicio usable cae al texto de siempre.
     */
    fun liveBadge(startMs: Long?, category: String, detail: String): String {
        val fallback = "EN VIVO" + detailSuffix(detail)
        if (startMs == null || startMs <= 0L) return fallback
        val window = range(startMs, category)
        if (window.isBlank()) return fallback
        return "EN VIVO · $window" + detailSuffix(detail)
    }

    /**
     * Horario compacto para la fila de la tarjeta: «19:00–21:00 · 67'». Va junto a
     * una píldora EN VIVO, así que no repite esa palabra.
     */
    fun liveRangeDetail(startMs: Long?, category: String, detail: String): String {
        if (startMs == null || startMs <= 0L) return detail.ifBlank { "EN VIVO" }
        val window = range(startMs, category)
        if (window.isBlank()) return detail.ifBlank { "EN VIVO" }
        return window + detailSuffix(detail)
    }

    /**
     * Texto explícito para el hero y las fichas: «Empezó 19:00 · Termina ~21:00 · 67'».
     */
    fun liveDetail(startMs: Long?, category: String, detail: String): String {
        if (startMs == null || startMs <= 0L) return detail.ifBlank { "EN VIVO" }
        val start = clock(startMs)
        if (start.isBlank()) return detail.ifBlank { "EN VIVO" }
        val end = clock(endMs(startMs, category))
        val window = if (end.isBlank()) "Empezó $start" else "Empezó $start · Termina ~$end"
        return window + detailSuffix(detail)
    }

    private fun detailSuffix(detail: String): String {
        val clean = detail.trim()
        if (clean.isBlank() || clean.equals("in", true) || clean.equals("EN VIVO", true)) return ""
        return " · $clean"
    }

    private fun fold(text: String): String {
        val lowered = text.lowercase(Locale.ROOT)
        val out = StringBuilder(lowered.length)
        for (ch in lowered) {
            when (ch) {
                'á', 'à', 'ä', 'â', 'ã', 'å' -> out.append('a')
                'é', 'è', 'ë', 'ê' -> out.append('e')
                'í', 'ì', 'ï', 'î' -> out.append('i')
                'ó', 'ò', 'ö', 'ô', 'õ' -> out.append('o')
                'ú', 'ù', 'ü', 'û' -> out.append('u')
                'ñ' -> out.append('n')
                'ç' -> out.append('c')
                else -> out.append(ch)
            }
        }
        return out.toString().trim()
    }
}
