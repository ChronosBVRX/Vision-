package com.chronosbvrx.visionplus.home.contract

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/** El horario visible de un evento deportivo: inicio real y fin estimado. */
class SportsScheduleTest {

    private val startMs: Long = Calendar.getInstance(TimeZone.getTimeZone("America/Mexico_City")).apply {
        set(2026, Calendar.NOVEMBER, 14, 19, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun durationIsSportSpecific() {
        assertEquals(120, SportsSchedule.durationMinutes("Football"))
        assertEquals(195, SportsSchedule.durationMinutes("American Football"))
        assertEquals(150, SportsSchedule.durationMinutes("Basketball"))
        assertEquals(180, SportsSchedule.durationMinutes("Baseball"))
        assertEquals(180, SportsSchedule.durationMinutes("Boxing"))
        assertEquals(120, SportsSchedule.durationMinutes("Motorsports"))
        assertEquals(120, SportsSchedule.durationMinutes("Something else"))
    }

    @Test
    fun clockAndRangeUseMexicoCityTime() {
        assertEquals("19:00", SportsSchedule.clock(startMs))
        assertEquals("19:00–21:00", SportsSchedule.range(startMs, "Football"))
        assertEquals(startMs + 120 * 60_000L, SportsSchedule.endMs(startMs, "Football"))
    }

    @Test
    fun liveBadgeAlwaysCarriesTheSchedule() {
        assertEquals(
            "EN VIVO · 19:00–21:00 · 45+2'",
            SportsSchedule.liveBadge(startMs, "Football", "45+2'"),
        )
        assertEquals(
            "EN VIVO · 19:00–21:00",
            SportsSchedule.liveBadge(startMs, "Football", "in"),
        )
        assertEquals("EN VIVO", SportsSchedule.liveBadge(null, "Football", ""))
        assertEquals("EN VIVO", SportsSchedule.liveBadge(0L, "Football", ""))
    }

    @Test
    fun liveDetailStatesStartAndEstimatedEnd() {
        assertEquals(
            "Empezó 19:00 · Termina ~21:00 · 67'",
            SportsSchedule.liveDetail(startMs, "Football", "67'"),
        )
        assertEquals(
            "19:00–21:00 · 67'",
            SportsSchedule.liveRangeDetail(startMs, "Football", "67'"),
        )
        assertEquals("EN VIVO", SportsSchedule.liveDetail(null, "Football", ""))
    }
}
