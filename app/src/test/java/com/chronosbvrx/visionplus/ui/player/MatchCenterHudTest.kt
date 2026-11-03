package com.chronosbvrx.visionplus.ui.player

import com.chronosbvrx.visionplus.data.sports.MatchEvent
import com.chronosbvrx.visionplus.data.sports.MatchEventType
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura del Match Center HUD: pestañas, marcador, nombres y cronología. */
class MatchCenterHudTest {

    private fun event(scoreLine: String = "", home: Int? = null, away: Int? = null) =
        GeeZapSignals.EventInfo(
            title = "Liverpool vs Arsenal",
            league = "Premier League",
            scoreLine = scoreLine,
            homeName = "Liverpool",
            awayName = "Arsenal",
            homeScore = home,
            awayScore = away,
        )

    @Test
    fun theFourPagesCycleWithLeftAndRight() {
        assertEquals(MatchHudPage.STATS, matchHudPageAfter(MatchHudPage.SUMMARY, 1))
        assertEquals(MatchHudPage.TIMELINE, matchHudPageAfter(MatchHudPage.STATS, 1))
        assertEquals(MatchHudPage.LINEUPS, matchHudPageAfter(MatchHudPage.TIMELINE, 1))
        assertEquals(MatchHudPage.SUMMARY, matchHudPageAfter(MatchHudPage.LINEUPS, 1))
        assertEquals(MatchHudPage.LINEUPS, matchHudPageAfter(MatchHudPage.SUMMARY, -1))
        assertEquals(MatchHudPage.STATS, matchHudPageAfter(MatchHudPage.LINEUPS, 2))
        assertEquals(MatchHudPage.SUMMARY, matchHudPageAfter(MatchHudPage.SUMMARY, 0))
    }

    @Test
    fun theScoreKeepsItsMinuteApart() {
        assertEquals(HudScore("1 - 0", "75'"), hudScore(event("1 - 0 · 75'")))
        assertEquals(HudScore("1 - 0", ""), hudScore(event("1 - 0")))
        assertEquals(HudScore("2 - 1", "HT"), hudScore(event("2 - 1 · HT")))
    }

    @Test
    fun theScoreFallsBackToTheAgendaNumbers() {
        assertEquals(HudScore("3 - 2", ""), hudScore(event(home = 3, away = 2)))
        assertEquals(HudScore("VS", ""), hudScore(event()))
    }

    @Test
    fun theScorerNameFitsTheHeader() {
        assertEquals("K. Havertz", hudPlayerName("Kai Havertz"))
        assertEquals("L. Di Lollo", hudPlayerName("Luis Di Lollo"))
        assertEquals("", hudPlayerName("  "))
        assertEquals("Ronaldo", hudPlayerName("Ronaldo"))
    }

    @Test
    fun theTimelineUsesAClearIconPerEvent() {
        assertEquals(
            "GOL 16' K. Havertz",
            hudEventLine(MatchEvent("Arsenal", MatchEventType.GOAL, "Kai Havertz", "16'")),
        )
        assertEquals(
            "TA 55' N. Madueke",
            hudEventLine(MatchEvent("Arsenal", MatchEventType.YELLOW, "Noni Madueke", "55'")),
        )
        assertEquals(
            "TR 72' M. Lewis-Skelly",
            hudEventLine(MatchEvent("Arsenal", MatchEventType.RED, "Myles Lewis-Skelly", "72'")),
        )
        assertEquals(
            "CAM 59' D. Szoboszlai · por Trey Nyoni",
            hudEventLine(
                MatchEvent(
                    "Liverpool",
                    MatchEventType.SUBSTITUTION,
                    "Dominik Szoboszlai",
                    "59'",
                    "por Trey Nyoni",
                ),
            ),
        )
    }

    @Test
    fun theTeamComparisonToleratesSuffixes() {
        assertTrue(hudSameTeam("Arsenal", "Arsenal FC"))
        assertTrue(hudSameTeam("Liverpool FC", "Liverpool"))
        assertTrue(hudSameTeam("Tijuana", "tijuana"))
        assertFalse(hudSameTeam("Arsenal", "Liverpool"))
        assertFalse(hudSameTeam("", "Arsenal"))
    }

    @Test
    fun theStatPagesCarryTheFifaOrder() {
        assertEquals(listOf("Posesión", "Tiros", "Tiros a puerta", "Córners"), HUD_SUMMARY_STATS)
        assertEquals(listOf("Faltas", "Amarillas", "Rojas", "Fueras de juego"), HUD_DISCIPLINE_STATS)
    }
}
