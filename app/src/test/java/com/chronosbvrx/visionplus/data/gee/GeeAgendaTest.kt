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
        // «pre» con el reloj antes del arranque: bloqueado (se fija el reloj para que la
        // prueba no dependa de la hora a la que corra la suite).
        val soon = event("c", "X", status = "pre", dateIso = "2026-10-17", time = "19:00:00")
        assertEquals(GeeAgenda.Phase.Soon, GeeAgenda.phaseOf(soon, GeeAgenda.startMs(soon)!! - 60_000))
    }

    @Test
    fun theLockOpensByTheClockEvenIfTheAgendaStillSaysPre() {
        // El sincronizado de la agenda llega unos minutos tarde: un partido anunciado a las
        // 19:00 que a las 19:05 sigue en «pre» DEBE abrirse. Era el candado que no se
        // desbloqueaba a pesar de que ya era la hora.
        val stale = event("k", "X", status = "pre", dateIso = "2026-10-17", time = "19:00:00")
        val start = GeeAgenda.startMs(stale)!!
        assertEquals(GeeAgenda.Phase.Soon, GeeAgenda.phaseOf(stale, start - 60_000))
        assertFalse(GeeAgenda.playable(stale, start - 60_000))
        assertEquals(GeeAgenda.Phase.Live, GeeAgenda.phaseOf(stale, start + 5 * 60_000))
        assertTrue("la hora abre el candado aunque la agenda siga en «pre»", GeeAgenda.playable(stale, start + 5 * 60_000))
        assertEquals("", GeeAgenda.blockNote(stale, start + 5 * 60_000))
        // Y la ventana se cierra sola: pasadas las tres horas vuelve a estar terminado.
        assertEquals(GeeAgenda.Phase.Done, GeeAgenda.phaseOf(stale, start + 4 * 3_600_000))
        assertFalse(GeeAgenda.playable(stale, start + 4 * 3_600_000))
    }

    @Test
    fun aFinishedMatchNeverReopensWithTheClock() {
        val done = event("f", "X", status = "post", scoreHome = 1, scoreAway = 0, dateIso = "2026-10-17", time = "19:00:00")
        val start = GeeAgenda.startMs(done)!!
        assertEquals(GeeAgenda.Phase.Done, GeeAgenda.phaseOf(done, start + 60_000))
        assertFalse(GeeAgenda.playable(done, start + 60_000))
        assertTrue(GeeAgenda.blockNote(done, start + 60_000).contains("terminó"))
    }

    @Test
    fun aCorrectedScheduleIsNotFrozenByTheCache() {
        // El CMS corrige una hora: no puede quedarse pegada la primera lectura.
        val original = event("z", "X", dateIso = "2026-10-18", time = "10:00:00")
        val corrected = event("z", "X", dateIso = "2026-10-18", time = "12:00:00")
        assertEquals(2 * 3_600_000L, GeeAgenda.startMs(corrected)!! - GeeAgenda.startMs(original)!!)
    }

    @Test
    fun anEventWithoutAScheduleStillFollowsTheAgenda() {
        // Sin fecha usable no hay reloj que valga: manda el estado.
        val pre = event("nd", "X", dateIso = "", status = "pre")
        assertEquals(GeeAgenda.Phase.Soon, GeeAgenda.phaseOf(pre, 0))
        assertFalse(GeeAgenda.playable(pre, 0))
        val playing = event("nd2", "X", dateIso = "", status = "in", live = true)
        assertEquals(GeeAgenda.Phase.Live, GeeAgenda.phaseOf(playing, 0))
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
    fun onlyLiveMatchesArePlayable() {
        // En vivo: se puede reproducir. Próximo o terminado: se bloquea (solo se
        // muestra dónde se transmite) y con un aviso claro.
        val live = event("l", "X", live = true, status = "in")
        val soon = event("s", "X", status = "pre")
        val done = event("d", "X", status = "post", scoreHome = 2, scoreAway = 1)
        assertTrue(GeeAgenda.playable(live, nowMs = 0))
        assertFalse(GeeAgenda.playable(soon, nowMs = 0))
        assertFalse(GeeAgenda.playable(done, nowMs = 0))
        assertEquals("", GeeAgenda.blockNote(live, nowMs = 0))
        assertTrue(GeeAgenda.blockNote(soon, nowMs = 0).contains("no comienza"))
        assertTrue(GeeAgenda.blockNote(done, nowMs = 0).contains("terminó"))
    }

    @Test
    fun playableFollowsTheSameClockWindowAsTheLiveSection() {
        // Sin cruce de agenda decide el reloj: mismo criterio que el bloque «En vivo».
        val sample = event("x", "Torneo", dateIso = "2026-10-17", time = "19:00:00")
        val start = GeeAgenda.startMs(sample)!!
        assertFalse(GeeAgenda.playable(sample, nowMs = start - 60_000))
        assertTrue(GeeAgenda.playable(sample, nowMs = start + 30 * 60_000))
        assertFalse(GeeAgenda.playable(sample, nowMs = start + 4 * 3_600_000))
    }

    @Test
    fun matchesLeagueIgnoresCaseAndAccents() {
        val mx = event("a", "Liga MX", league = "Liga MX")
        assertTrue(GeeAgenda.matchesLeague(mx, null))
        assertTrue(GeeAgenda.matchesLeague(mx, "liga mx"))
        assertFalse(GeeAgenda.matchesLeague(mx, "Premier League"))
    }
}
