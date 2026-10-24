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

    @Test
    fun theDayStripAlwaysOffersToday() {
        val days = eventDays(listOf(event("a", dateIso = "2026-10-25")), "2026-10-23")
        assertEquals(listOf("2026-10-23", "2026-10-25"), days.map { it.first })
        assertEquals("Hoy", days.first().second)
    }

    @Test
    fun thePhaseUsesTheClockAndNotTheAliveSignalCount() {
        val start = 1_760_000_000_000L
        val future = SportsItem(
            id = "future",
            name = "A vs B",
            dateIso = "2026-10-24",
            live = true, // feed viejo: `live` venía de las señales, no del partido
            alive = 5,
            startMs = start + 4 * 60 * 60_000L,
        )
        assertEquals(SportsPhase.Soon, sportsPhase(future, start))
    }

    @Test
    fun aStartedMatchOpensByClockEvenIfTheAgendaSaysPre() {
        val start = 1_760_000_000_000L
        val lagging = SportsItem(
            id = "lag",
            name = "A vs B",
            dateIso = "2026-10-24",
            status = "pre",
            startMs = start - 60 * 60_000L,
        )
        assertEquals(SportsPhase.Live, sportsPhase(lagging, start))
    }

    @Test
    fun aFinishedMatchStaysFinishedEvenWithLiveSignals() {
        val start = 1_760_000_000_000L
        val finished = SportsItem(
            id = "done",
            name = "A vs B",
            dateIso = "2026-10-24",
            status = "post",
            live = true,
            alive = 3,
            startMs = start - 60 * 60_000L,
        )
        assertEquals(SportsPhase.Done, sportsPhase(finished, start))
        assertEquals("Final", sportsDetailLabel(finished.copy(detail = ""), SportsPhase.Done))
    }

    @Test
    fun theAgendaStatusWinsWhenItExists() {
        val start = 1_760_000_000_000L
        val inPlay = SportsItem(
            id = "in",
            name = "A vs B",
            dateIso = "2026-10-24",
            status = "in",
            startMs = start - 6 * 60 * 60_000L,
        )
        assertEquals(SportsPhase.Live, sportsPhase(inPlay, start))
    }

    @Test
    fun aClockOlderThanTheWindowIsFinished() {
        val start = 1_760_000_000_000L
        val old = SportsItem(
            id = "old",
            name = "A vs B",
            dateIso = "2026-10-24",
            startMs = start - 4 * 60 * 60_000L,
        )
        assertEquals(SportsPhase.Done, sportsPhase(old, start))
    }

    @Test
    fun theMexicanClockIsFormattedWithoutSeconds() {
        assertEquals("14:00", sportsClock("14:00:00"))
        assertEquals("14:00", sportsClock("14:00"))
        assertEquals("", sportsClock(""))
    }
}
