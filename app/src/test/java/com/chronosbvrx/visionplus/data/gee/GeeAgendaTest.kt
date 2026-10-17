package com.chronosbvrx.visionplus.data.gee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La agenda de "Deportes en vivo": bloques EN VIVO → COMIENZAN PRONTO →
 * TERMINADOS, ligas grandes al frente (Liga MX primero) y alias de liga del CMS.
 * Si esto se rompe, la pantalla vuelve a mezclar terminados con lo que se juega.
 */
class GeeAgendaTest {

    private fun event(
        id: String,
        name: String,
        category: String = "Football",
        dateIso: String = "2026-10-17",
        time: String = "19:00:00",
        league: String = "",
        live: Boolean = false,
        status: String = "",
        scoreHome: Int? = null,
        scoreAway: Int? = null,
    ) = GeeSportsSource.GeeEvent(
        id = id,
        category = category,
        name = name,
        logo = "",
        teamA = "",
        teamB = "",
        dateIso = dateIso,
        dateText = "",
        time = time,
        alive = 0,
        total = 0,
        links = emptyList(),
        leagueLabel = league,
        scoreHome = scoreHome,
        scoreAway = scoreAway,
        live = live,
        status = status,
    )

    @Test
    fun foldStripsAccentsForLeagueComparison() {
        assertEquals("brasileirao", GeeAgenda.fold("Brasileirão"))
        assertEquals("liga de expansion mx", GeeAgenda.fold("Liga de Expansión MX"))
        assertEquals("champions league", GeeAgenda.fold("CHAMPIONS LEAGUE"))
    }

    @Test
    fun leagueLabelUsesAgendaNameThenCmsAliases() {
        assertEquals("Liga MX", GeeAgenda.leagueLabel(event("a", "Liga MX, Apertura", league = "Liga MX")))
        assertEquals("Liga MX", GeeAgenda.leagueLabel(event("b", "Liga MX, Apertura")))
        // El CMS rotula la argentina con su nombre largo: se canoniza.
        assertEquals("Liga Profesional Argentina", GeeAgenda.leagueLabel(event("c", "Liga Profesional de Fútbol")))
        assertEquals("UEFA Champions League", GeeAgenda.leagueLabel(event("d", "Champions League")))
        assertEquals("Serie B", GeeAgenda.leagueLabel(event("e", "Serie B")))
    }

    @Test
    fun ligaMxLeadsThePriorityOrder() {
        assertEquals(0, GeeAgenda.leaguePriority("Liga MX"))
        assertEquals(1, GeeAgenda.leaguePriority("UEFA Champions League"))
        assertTrue(GeeAgenda.leaguePriority("Serie B") > GeeAgenda.leaguePriority("Premier League"))
    }

    @Test
    fun leagueNamesPutTheBigOnesFirst() {
        val events = listOf(
            event("1", "Serie B", league = "Serie B"),
            event("2", "Premier League", league = "Premier League"),
            event("3", "Liga MX, Apertura", league = "Liga MX"),
        )
        assertEquals(listOf("Liga MX", "Premier League", "Serie B"), GeeAgenda.leagueNames(events))
    }

    @Test
    fun phaseFollowsTheAgendaWhenThereIsACross() {
        assertEquals(GeeAgenda.Phase.Live, GeeAgenda.phaseOf(event("a", "X", live = true, status = "in")))
        assertEquals(GeeAgenda.Phase.Done, GeeAgenda.phaseOf(event("b", "X", status = "post", scoreHome = 2, scoreAway = 1)))
        assertEquals(GeeAgenda.Phase.Soon, GeeAgenda.phaseOf(event("c", "X", status = "pre")))
    }

    @Test
    fun phaseFallsBackToTheClockWithoutACross() {
        val sample = event("d", "Torneo", dateIso = "2026-10-17", time = "19:00:00")
        val start = GeeAgenda.startMs(sample)!!
        assertEquals(GeeAgenda.Phase.Soon, GeeAgenda.phaseOf(sample, start - 60_000))
        assertEquals(GeeAgenda.Phase.Live, GeeAgenda.phaseOf(sample, start + 30 * 60_000))
        assertEquals(GeeAgenda.Phase.Done, GeeAgenda.phaseOf(sample, start + 4 * 3_600_000))
    }

    @Test
    fun sectionsOrderLiveThenSoonThenFinished() {
        val liveMx = event("l1", "Liga MX", league = "Liga MX", live = true, status = "in", scoreHome = 0, scoreAway = 1)
        val liveB = event("l2", "Serie B", league = "Serie B", live = true, status = "in", scoreHome = 1, scoreAway = 1)
        val soon = event("s1", "Bundesliga", league = "Bundesliga", status = "pre", dateIso = "2026-10-17", time = "21:00:00")
        val done = event("d1", "Premier League", league = "Premier League", status = "post", scoreHome = 3, scoreAway = 0)
        val out = GeeAgenda.sections(listOf(done, soon, liveB, liveMx), nowMs = 0)
        assertEquals(listOf("En vivo", "Comienzan pronto", "Terminados"), out.map { it.title })
        // Dentro de "En vivo" manda la liga (Liga MX antes que Serie B).
        assertEquals(listOf("l1", "l2"), out[0].events.map { it.id })
        assertEquals(listOf("s1"), out[1].events.map { it.id })
        assertEquals(listOf("d1"), out[2].events.map { it.id })
    }

    @Test
    fun emptySectionsAreOmitted() {
        val out = GeeAgenda.sections(listOf(event("s1", "X", status = "pre")), nowMs = 0)
        assertEquals(listOf("Comienzan pronto"), out.map { it.title })
    }

    @Test
    fun matchesLeagueIgnoresCaseAndAccents() {
        val mx = event("a", "Liga MX", league = "Liga MX")
        assertTrue(GeeAgenda.matchesLeague(mx, null))
        assertTrue(GeeAgenda.matchesLeague(mx, "liga mx"))
        assertFalse(GeeAgenda.matchesLeague(mx, "Premier League"))
    }
}
