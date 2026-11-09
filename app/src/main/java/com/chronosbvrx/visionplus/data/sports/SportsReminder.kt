package com.chronosbvrx.visionplus.data.sports

import android.content.Context
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.notify.ReminderKit

/**
 * Avisos de partido: el mismo [ReminderKit] local de Vision+ (sin push ni cuentas),
 * con una clave estable por evento y la hora de inicio real de la agenda.
 *
 * La UI ofrece 10/30/60 minutos; reprogramar reemplaza el aviso anterior del partido.
 */
object SportsReminder {

    val OPTIONS = listOf(10, 30, 60)

    fun key(event: GeeSportsSource.GeeEvent): String = "match:${event.id}"

    fun title(event: GeeSportsSource.GeeEvent): String {
        val home = event.homeName.ifBlank { event.teamA }
        val away = event.awayName.ifBlank { event.teamB }
        return if (home.isNotBlank() && away.isNotBlank()) "$home vs $away" else event.name
    }

    fun isActive(context: Context, event: GeeSportsSource.GeeEvent): Boolean =
        ReminderKit.scheduled(context).containsKey(key(event))

    /** Programa el aviso; false si el partido no tiene hora o el aviso ya pasó. */
    fun schedule(context: Context, event: GeeSportsSource.GeeEvent, minutesBefore: Int): Boolean {
        val start = GeeAgenda.startMs(event) ?: return false
        val at = ReminderKit.remindAtMs(start, minutesBefore)
        if (at <= System.currentTimeMillis()) return false
        return ReminderKit.schedule(
            context = context,
            key = key(event),
            atMs = at,
            title = title(event),
            text = "Empieza en $minutesBefore minutos",
            uri = com.chronosbvrx.visionplus.home.contract.VisionLinks.sports(event.id),
        )
    }

    fun clear(context: Context, event: GeeSportsSource.GeeEvent) {
        ReminderKit.cancel(context, key(event))
    }
}
