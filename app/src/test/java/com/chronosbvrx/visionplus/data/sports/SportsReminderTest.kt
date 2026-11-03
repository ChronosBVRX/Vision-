package com.chronosbvrx.visionplus.data.sports

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SportsReminderTest {

    private fun event() = GeeSportsSource.GeeEvent(
        id = "espn:401",
        category = "Football",
        name = "Chivas vs América",
        logo = "",
        teamA = "Chivas",
        teamB = "América",
        dateIso = "2026-10-29",
        dateText = "Hoy",
        time = "20:00:00",
        alive = 1,
        total = 1,
        links = emptyList(),
        homeName = "Chivas",
        awayName = "América",
    )

    @Test
    fun offersTenThirtyAndSixtyMinutes() {
        assertEquals(listOf(10, 30, 60), SportsReminder.OPTIONS)
    }

    @Test
    fun theKeyIsStablePerEvent() {
        assertEquals("match:espn:401", SportsReminder.key(event()))
    }

    @Test
    fun theTitleUsesTheTwoTeams() {
        assertEquals("Chivas vs América", SportsReminder.title(event()))
    }
}
