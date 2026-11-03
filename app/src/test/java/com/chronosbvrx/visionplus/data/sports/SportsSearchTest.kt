package com.chronosbvrx.visionplus.data.sports

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsSearchTest {

    private fun event(
        home: String = "Chivas",
        away: String = "América",
        league: String = "Liga MX",
        category: String = "Football",
        broadcasts: List<String> = listOf("TUDN", "Canal 5"),
    ) = GeeSportsSource.GeeEvent(
        id = "e1",
        category = category,
        name = "$home vs $away",
        logo = "",
        teamA = home,
        teamB = away,
        dateIso = "2026-10-29",
        dateText = "Hoy",
        time = "20:00:00",
        alive = 1,
        total = 1,
        links = emptyList(),
        homeName = home,
        awayName = away,
        leagueLabel = league,
        broadcasts = broadcasts,
    )

    @Test
    fun blankQueryShowsEverything() {
        assertTrue(SportsSearch.matches(event(), ""))
        assertTrue(SportsSearch.matches(event(), "   "))
    }

    @Test
    fun findsByTeamWithoutAccentsOrCase() {
        assertTrue(SportsSearch.matches(event(), "america"))
        assertTrue(SportsSearch.matches(event(), "CHIVAS"))
        assertTrue(SportsSearch.matches(event(), "América"))
    }

    @Test
    fun findsByLeagueAndBroadcast() {
        assertTrue(SportsSearch.matches(event(), "liga mx"))
        assertTrue(SportsSearch.matches(event(), "TUDN"))
    }

    @Test
    fun allWordsMustMatch() {
        assertTrue(SportsSearch.matches(event(), "chivas liga mx"))
        assertFalse(SportsSearch.matches(event(), "chivas premier"))
    }

    @Test
    fun saysNoWhenNothingMatches() {
        assertFalse(SportsSearch.matches(event(), "bayern"))
    }
}
