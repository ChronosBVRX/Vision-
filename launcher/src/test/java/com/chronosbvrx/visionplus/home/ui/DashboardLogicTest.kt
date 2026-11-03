package com.chronosbvrx.visionplus.home.ui

import com.chronosbvrx.visionplus.home.contract.ContinueItem
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reglas puras del escritorio Bento que no dependen de Android ni de la red. */
class DashboardLogicTest {

    private val cdmx = TimeZone.getTimeZone("America/Mexico_City")

    private fun at(hour: Int): Long = Calendar.getInstance(cdmx).apply {
        set(2026, Calendar.NOVEMBER, 3, hour, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun theGreetingFollowsTheHourOfMexicoCity() {
        assertEquals("Buenos días", greetingFor(at(8), cdmx))
        assertEquals("Buenas tardes", greetingFor(at(13), cdmx))
        assertEquals("Buenas tardes", greetingFor(at(18), cdmx))
        assertEquals("Buenas noches", greetingFor(at(21), cdmx))
    }

    @Test
    fun theContinueWidgetRoundsUpTheRemainingMinutes() {
        assertEquals(
            35,
            remainingMinutes(
                ContinueItem(
                    key = "nahuala",
                    title = "La leyenda de la Nahuala",
                    positionMs = 55 * 60_000L,
                    durationMs = 90 * 60_000L,
                ),
            ),
        )
    }

    @Test
    fun theContinueWidgetIsHonestWhenThereIsNoDuration() {
        assertEquals(0, remainingMinutes(ContinueItem(key = "sin-duracion", title = "Título")))
        assertEquals(
            1,
            remainingMinutes(
                ContinueItem(
                    key = "casi",
                    title = "Casi termina",
                    positionMs = 89 * 60_000L + 50_000L,
                    durationMs = 90 * 60_000L,
                ),
            ),
        )
    }
}
