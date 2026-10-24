package com.chronosbvrx.visionplus.home.ui

import com.chronosbvrx.visionplus.home.contract.SportsItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * La fase real de un evento de Deportes. La MISMA regla que la app:
 * estado real de la agenda, y cuando falta, el reloj con una ventana de 3 h.
 *
 * `alive` (señales que responden) no participa: un partido de mañana puede tener
 * enlaces vivos y no se está jugando.
 */
internal enum class SportsPhase { Live, Soon, Done }

internal const val SPORTS_LIVE_WINDOW_MS = 3L * 60 * 60 * 1_000L

private val MX_ZONE: TimeZone = TimeZone.getTimeZone("America/Mexico_City")
private val STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
    timeZone = MX_ZONE
    isLenient = false
}

internal fun sportsPhase(event: SportsItem, nowMs: Long): SportsPhase {
    // El estado de la agenda manda cuando existe; si no, el reloj. El feed viejo marcaba
    // `live` con las señales vivas, así que NO se usa antes de la ventana de 3 h.
    if (event.status.equals("post", ignoreCase = true)) return SportsPhase.Done
    if (event.status.equals("in", ignoreCase = true)) return SportsPhase.Live
    val start = sportsStartMs(event)
    if (start != null) {
        return when {
            nowMs >= start && nowMs < start + SPORTS_LIVE_WINDOW_MS -> SportsPhase.Live
            nowMs < start -> SportsPhase.Soon
            else -> SportsPhase.Done
        }
    }
    if (event.live) return SportsPhase.Live
    return SportsPhase.Soon
}

/** Inicio epoch en hora de México; primero el campo ya resuelto del feed, luego la fecha/hora. */
internal fun sportsStartMs(event: SportsItem): Long? {
    if (event.startMs > 0L) return event.startMs
    if (event.dateIso.isBlank()) return null
    val time = sportsClock(event.time).ifBlank { "00:00:00" }
    val stamp = "${event.dateIso} ${if (time.length == 5) "$time:00" else time}"
    return runCatching { STAMP.parse(stamp)?.time }.getOrNull()?.takeIf { it > 0L }
}

/** «14:00:00» o «14:00» → «14:00». */
internal fun sportsClock(time: String): String = when {
    time.isBlank() -> ""
    time.length >= 5 -> time.take(5)
    else -> time
}

internal fun sportsPhaseOrder(phase: SportsPhase): Int = when (phase) {
    SportsPhase.Live -> 0
    SportsPhase.Soon -> 1
    SportsPhase.Done -> 2
}

internal fun sportsPhaseTitle(phase: SportsPhase): String = when (phase) {
    SportsPhase.Live -> "En vivo"
    SportsPhase.Soon -> "Comienzan pronto"
    SportsPhase.Done -> "Terminados"
}

internal fun sportsPhaseSubtitle(phase: SportsPhase, count: Int): String = when (phase) {
    SportsPhase.Live -> if (count == 1) "1 partido ahora" else "$count partidos ahora"
    SportsPhase.Soon -> if (count == 1) "1 próximamente" else "$count próximamente"
    SportsPhase.Done -> if (count == 1) "1 finalizado" else "$count finalizados"
}

/** Lo que va en la esquina/cabecera de la tarjeta según la fase. */
internal fun sportsDetailLabel(event: SportsItem, phase: SportsPhase): String = when (phase) {
    SportsPhase.Live -> event.detail.ifBlank { "EN VIVO" }
    SportsPhase.Done -> event.detail.ifBlank { "Final" }
    SportsPhase.Soon -> sportsClock(event.time).ifBlank { event.dateText }
}

/** El pie: minuto en vivo, «Final» al terminar, hora cuando aún no empieza. */
internal fun sportsFooterLabel(event: SportsItem, phase: SportsPhase): String = when (phase) {
    SportsPhase.Live -> event.detail.ifBlank { "En juego" }
    SportsPhase.Done -> event.detail.ifBlank { "Final" }
    SportsPhase.Soon -> sportsClock(event.time).ifBlank { event.dateText }
}
