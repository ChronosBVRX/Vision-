package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La lógica pura del mosaico de "Deportes en vivo" (móvil y TV): equipos,
 * marcador, estado y pie. Si esto se rompe, las dos parrillas mienten igual.
 */
class GeeTilesTest {

    private fun event(
        name: String = "Liga MX, Apertura",
        teamA: String = "",
        teamB: String = "",
        homeName: String = "",
        awayName: String = "",
        homeLogo: String = "",
        awayLogo: String = "",
        dateIso: String = "2026-10-17",
        dateText: String = "17/10/2026",
        time: String = "19:00:00",
        alive: Int = 0,
        live: Boolean = false,
        detail: String = "",
        status: String = "",
        scoreHome: Int? = null,
        scoreAway: Int? = null,
    ) = GeeSportsSource.GeeEvent(
        id = "e_$name",
        category = "Football",
        name = name,
        logo = "",
        teamA = teamA,
        teamB = teamB,
        dateIso = dateIso,
        dateText = dateText,
        time = time,
        alive = alive,
        total = 0,
        links = emptyList(),
        homeName = homeName,
        awayName = awayName,
        homeLogo = homeLogo,
        awayLogo = awayLogo,
        scoreHome = scoreHome,
        scoreAway = scoreAway,
        live = live,
        detail = detail,
        status = status,
    )

    @Test
    fun teamsPreferTheAgendaNamesAndFallBackToTheCmsFields() {
        assertEquals(
            "América" to "Guadalajara",
            GeeTiles.teams(event(homeName = "América", awayName = "Guadalajara")),
        )
        assertEquals(
            "Tigres" to "Rayados",
            GeeTiles.teams(event(teamA = "Tigres", teamB = "Rayados")),
        )
    }

    @Test
    fun teamsAreEmptyWhenTheEventIsNotAConfrontation() {
        // El CMS manda el nombre del cuadro como "equipo": no es un partido de dos.
        assertEquals("" to "", GeeTiles.teams(event(name = "Gran Premio de México")))
        assertEquals("" to "", GeeTiles.teams(event(name = "X", homeName = "X")))
        assertEquals("" to "", GeeTiles.teams(event(homeName = "Solo local")))
    }

    @Test
    fun scoreOnlyWhenBothSidesHaveIt() {
        assertEquals("2 - 1", GeeTiles.score(event(scoreHome = 2, scoreAway = 1)))
        assertEquals("0 - 0", GeeTiles.score(event(scoreHome = 0, scoreAway = 0)))
        assertEquals("VS", GeeTiles.score(event(scoreHome = 2)))
        assertEquals("VS", GeeTiles.score(event(scoreAway = 1)))
        assertEquals("VS", GeeTiles.score(event()))
    }

    @Test
    fun liveLabelShowsTheMinuteWhenTheAgendaProvidesIt() {
        assertEquals("EN VIVO", GeeTiles.liveLabel(event(live = true)))
        assertEquals("EN VIVO", GeeTiles.liveLabel(event(live = true, detail = "in")))
        assertEquals("EN VIVO · 45+2'", GeeTiles.liveLabel(event(live = true, detail = "45+2'")))
    }

    @Test
    fun statePrefersLiveThenFinalThenDayAndTime() {
        val today = "2026-10-17"
        assertEquals("EN VIVO · 23'", GeeTiles.state(event(live = true, detail = "23'"), today))
        assertEquals("Final", GeeTiles.state(event(status = "post", scoreHome = 3, scoreAway = 0), today))
        assertEquals("Hoy 19:00", GeeTiles.state(event(dateIso = today), today))
        assertEquals("18/10/2026 21:30", GeeTiles.state(event(dateIso = "2026-10-18", dateText = "18/10/2026", time = "21:30:00"), today))
        // Sin fecha de calendario: al menos la hora.
        assertEquals("19:00", GeeTiles.state(event(dateIso = "", dateText = ""), today))
        // El reloj ya abrió el partido aunque la agenda siga en «pre»: dice EN VIVO, no la hora.
        assertEquals("EN VIVO", GeeTiles.state(event(dateIso = today), today, liveByClock = true))
    }

    @Test
    fun streamsNameTheSignalNeverTheMatchState() {
        // `alive` cuenta enlaces que responden: se dice «señal», jamás «en vivo»
        // (esa palabra es solo para partidos que de verdad se están jugando).
        assertEquals("Sin señal por ahora", GeeTiles.streams(event(alive = 0)))
        assertEquals("1 señal disponible", GeeTiles.streams(event(alive = 1)))
        assertEquals("3 señales disponibles", GeeTiles.streams(event(alive = 3)))
    }

    @Test
    fun monogramFallsBackToInitials() {
        assertEquals("RM", GeeTiles.monogram("Real Madrid"))
        assertEquals("LM", GeeTiles.monogram("Liga MX"))
        assertEquals("FB", GeeTiles.monogram("FC Barcelona"))
        assertEquals("GU", GeeTiles.monogram("Guadalajara"))
        assertEquals("?", GeeTiles.monogram("  "))
    }
}
