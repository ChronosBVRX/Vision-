package com.chronosbvrx.visionplus.home.ui

import com.chronosbvrx.visionplus.home.contract.SportsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La agenda de Deportes del launcher: los mismos ejes que la app (deporte, liga y día),
 * con Fútbol primero y los días en orden.
 */
class EventsAgendaTest {

    private fun event(
        id: String,
        category: String = "Football",
        dateIso: String = "2026-10-23",
        league: String = "Liga MX",
        live: Boolean = false,
    ) = SportsItem(
        id = id,
        name = "A vs B",
        category = category,
        dateIso = dateIso,
        league = league,
        live = live,
        openLink = "visionplus://open/sports?event=$id",
    )

    @Test
    fun theDayStripIsOrderedAndLabelled() {
        val today = "2026-10-23"
        val days = eventDays(
            listOf(
                event("a", dateIso = "2026-10-25"),
                event("b", dateIso = "2026-10-23"),
                event("c", dateIso = "2026-10-24"),
                event("d", dateIso = ""),
            ),
            today,
        )
        assertEquals(listOf("2026-10-23", "2026-10-24", "2026-10-25"), days.map { it.first })
        assertEquals("Hoy", days[0].second)
        assertEquals("Mañana", days[1].second)
        assertTrue(days[2].second.isNotBlank())
    }

    @Test
    fun theClockOfTheAgendaIsMexicoCity() {
        // 1970-01-01T00:00:00Z = 31-dic-1969 18:00 en el centro de México.
        assertEquals("1969-12-31", todayIsoMx(0L))
    }

    @Test
    fun footballComesFirstInTheSportsStrip() {
        val categories = eventCategories(
            listOf(
                event("b", category = "Basketball"),
                event("f1", category = "Football"),
                event("f2", category = "Football"),
                event("t", category = "Tennis"),
            ),
        )
        assertEquals("Football", categories.first())
        assertEquals("Fútbol", sportLabel("Football"))
        assertEquals("Básquet", sportLabel("Basketball"))
        assertEquals("Motor", sportLabel("Formula 1"))
        assertEquals("Motor", sportLabel("Motorsports"))
        assertEquals("Béisbol", sportLabel("Baseball"))
        assertEquals("Americano", sportLabel("American Football"))
    }

    @Test
    fun leaguesBelongToTheSelectedDayAndSport() {
        val events = listOf(
            event("1", league = "Liga MX"),
            event("2", league = "Liga MX"),
            event("3", league = "Champions"),
            event("4", dateIso = "2026-10-24", league = "Liga MX"),
            event("5", category = "Basketball", league = "NBA"),
        )
        assertEquals(listOf("Liga MX", "Champions"), eventLeagues(events, "2026-10-23", "Football"))
        assertEquals(listOf("Liga MX", "Champions", "NBA"), eventLeagues(events, "2026-10-23", null))
        assertEquals(listOf("Liga MX"), eventLeagues(events, "2026-10-24", "Football"))
    }

    @Test
    fun aDayWithoutEventsHasNoLeagues() {
        assertTrue(eventLeagues(listOf(event("1")), "2026-10-30", null).isEmpty())
    }
}
