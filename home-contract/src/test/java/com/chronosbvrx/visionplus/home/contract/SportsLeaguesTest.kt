package com.chronosbvrx.visionplus.home.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La lista principal es una sola y no se hereda por subcadena. */
class SportsLeaguesTest {

    @Test
    fun secondaryCompetitionsAreNotTheirParentLeague() {
        assertTrue(SportsLeagues.isMainLeague("LaLiga"))
        assertFalse(SportsLeagues.isMainLeague("LaLiga 2"))
        assertFalse(SportsLeagues.isMainLeague("Serie A Women"))
        assertFalse(SportsLeagues.isMainLeague("2. Bundesliga"))
        assertFalse(SportsLeagues.isMainLeague("Russian Premier League"))
        assertFalse(SportsLeagues.isMainLeague("Kenya Premier League"))
        assertFalse(SportsLeagues.isMainLeague("Championship"))
        assertFalse(SportsLeagues.isMainLeague("Liga MX Femenil"))
    }

    @Test
    fun aliasesAndTournamentsAreCanonical() {
        assertEquals("Liga MX", SportsLeagues.canonical("Liga MX, Apertura"))
        assertEquals("UEFA Champions League", SportsLeagues.canonical("Champions League"))
        assertEquals("Liga Profesional Argentina", SportsLeagues.canonical("Liga Profesional de Fútbol"))
        assertEquals("Eredivisie", SportsLeagues.canonical("VriendenLoterij Eredivisie"))
        assertEquals("Primeira Liga", SportsLeagues.canonical("Liga Portugal Betclic"))
        assertEquals("MMA", SportsLeagues.canonical("Mixed Martial Arts"))
    }

    @Test
    fun tierSeparatesMainSecondaryAndNiche() {
        assertEquals(1, SportsLeagues.tier("LaLiga", "Football", "esp.1"))
        assertEquals(1, SportsLeagues.tier("Liga de Expansión MX", "Football"))
        assertEquals(2, SportsLeagues.tier("LaLiga 2", "Football"))
        assertEquals(2, SportsLeagues.tier("U17 Women's World Championship", "Football"))
        assertEquals(3, SportsLeagues.tier("Six Nations", "Rugby"))
        assertEquals(3, SportsLeagues.tier("Snooker", "Other"))
    }

    @Test
    fun mainLeaguesSortBeforeSecondaryOnes() {
        assertTrue(SportsLeagues.leaguePriority("Liga MX") < SportsLeagues.leaguePriority("LaLiga"))
        assertTrue(SportsLeagues.leaguePriority("LaLiga") < SportsLeagues.leaguePriority("LaLiga 2"))
        assertTrue(SportsLeagues.leaguePriority("Premier League") < SportsLeagues.leaguePriority("Championship"))
    }
}
