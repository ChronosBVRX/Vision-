package com.chronosbvrx.visionplus.data.sports

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * El cruce de la agenda con los eventos de GeeSports (fuente principal): por
 * equipos —con alias del portal («Chivas» = Guadalajara)—, del mismo día, y con
 * respaldo si no hay evento exacto. Si esto se rompe, los partidos pierden sus
 * enlaces directos al instante.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SportsLinkerGeeTest {

    private fun match(home: String, away: String, kickoffMs: Long): SportMatch {
        fun team(name: String) = SportTeam(name.lowercase(), name, name.take(3).uppercase(), "", null)
        return SportMatch(
            id = "m1",
            league = SportLeague("mex.1", "Liga MX", ""),
            home = team(home),
            away = team(away),
            kickoffMs = kickoffMs,
            status = "in",
            detail = "",
            venue = "",
            channels = listOf("TUDN"),
            broadcasts = emptyList(),
        )
    }

    private fun link(name: String, alive: Boolean? = true) = GeeSportsSource.GeeLink(
        name = name,
        link = "https://cdn/$name.m3u8",
        headers = emptyMap(),
        type = "hls",
        drm = "",
        alive = alive,
    )

    private fun event(
        id: String,
        name: String,
        teamA: String,
        teamB: String,
        dateIso: String,
        links: List<GeeSportsSource.GeeLink>,
        category: String = "Football",
    ) = GeeSportsSource.GeeEvent(
        id = id,
        category = category,
        name = name,
        logo = "",
        teamA = teamA,
        teamB = teamB,
        dateIso = dateIso,
        dateText = "",
        time = "19:00:00",
        alive = links.count { it.alive == true },
        total = links.size,
        links = links,
    )

    private fun channel(name: String) = com.chronosbvrx.visionplus.data.LiveChannel(
        name = name,
        url = "xtvlive:$name",
        group = "Deportes",
    )

    @Test
    fun matchesTeamsWithPortalAliasesOnTheSameDay() {
        val kickoff = Instant.parse("2026-10-17T02:00:00Z").toEpochMilli() // 16/10 20:00 en México
        val day = SportsLinker.dayIsoOf(kickoff)
        val m = match("Guadalajara", "América", kickoff)
        val events = listOf(
            event("e1", "Liga MX: Chivas vs America", "", "", day, listOf(link("azteca"), link("tudn"))),
        )
        val channels = SportsLinker.geeChannelsFor(m, events)
        assertEquals(2, channels.size)
        assertEquals("https://cdn/azteca.m3u8", channels[0].url)
        assertEquals("Deportes en vivo", channels[0].group)
    }

    @Test
    fun matchesThroughTheEventsTeamFields() {
        val kickoff = Instant.parse("2026-10-17T02:00:00Z").toEpochMilli()
        val day = SportsLinker.dayIsoOf(kickoff)
        val m = match("Bayern Munich", "Borussia Dortmund", kickoff)
        val events = listOf(
            event("e1", "Bundesliga", "Bayern Munich", "Dortmund", day, listOf(link("sky"))),
        )
        assertEquals(1, SportsLinker.geeChannelsFor(m, events).size)
    }

    @Test
    fun fallsBackToAnotherDayWhenNoSameDayEventExists() {
        val kickoff = Instant.parse("2026-10-17T02:00:00Z").toEpochMilli()
        val m = match("Guadalajara", "América", kickoff)
        val events = listOf(
            event("e1", "Chivas vs America", "", "", "1999-01-01", listOf(link("viejito"))),
        )
        assertEquals(1, SportsLinker.geeChannelsFor(m, events).size)
    }

    @Test
    fun ignoresOtherSportsMissingTeamsAndEmptyLinks() {
        val kickoff = Instant.parse("2026-10-17T02:00:00Z").toEpochMilli()
        val day = SportsLinker.dayIsoOf(kickoff)
        val m = match("Guadalajara", "América", kickoff)
        val events = listOf(
            event("basket", "Chivas vs America", "", "", day, listOf(link("a")), category = "Basketball"),
            event("empty", "Chivas vs America", "", "", day, emptyList()),
            event("other", "Barcelona vs Madrid", "", "", day, listOf(link("b"))),
        )
        assertTrue(SportsLinker.geeChannelsFor(m, events).isEmpty())
    }

    @Test
    fun capsTheAlternatesAtTwelve() {
        val kickoff = Instant.parse("2026-10-17T02:00:00Z").toEpochMilli()
        val day = SportsLinker.dayIsoOf(kickoff)
        val m = match("Guadalajara", "América", kickoff)
        val links = (1..20).map { link("senal$it") }
        val events = listOf(event("e1", "Chivas vs America", "", "", day, links))
        assertEquals(12, SportsLinker.geeChannelsFor(m, events).size)
    }

    @Test
    fun dayIsoOfUsesMexicoCityTime() {
        assertEquals("", SportsLinker.dayIsoOf(0))
        // 02:00 UTC del 17 sigue siendo 16 en México (UTC-6).
        assertEquals("2026-10-16", SportsLinker.dayIsoOf(Instant.parse("2026-10-17T02:00:00Z").toEpochMilli()))
        assertEquals("2026-10-17", SportsLinker.dayIsoOf(Instant.parse("2026-10-17T20:00:00Z").toEpochMilli()))
    }

    @Test
    fun foxBrandsAreKeptApart() {
        // Conflicto real de México: el canal general «Fox» (Disney) no es
        // «Fox Sports» (Grupo Lauman) ni «FOX DEPORTES» (señal de EE. UU.).
        assertEquals("fox", SportsLinker.extractBrandInfo("Fox").brand)
        assertEquals("fox", SportsLinker.extractBrandInfo("Fox channel").brand)
        assertEquals("fox deportes", SportsLinker.extractBrandInfo("FOX DEPORTES").brand)
        val sports = SportsLinker.extractBrandInfo("Fox Sports 1 MX")
        assertEquals("fox sports", sports.brand)
        assertEquals("1", sports.edition)
        assertEquals("México", sports.targetCountry)
    }

    @Test
    fun foxSportsGuideNeverPicksTheGeneralFoxChannel() {
        val channels = listOf(
            channel("Fox"),
            channel("Fox channel"),
            channel("Fox Sports 1 MX"),
            channel("Fox Sports 2 MX"),
        )
        val resolved = SportsLinker.resolveChannels(listOf("Fox Sports"), channels)
        assertEquals("xtvlive:Fox Sports 1 MX", resolved.firstOrNull()?.url)
        assertTrue(resolved.none { it.name == "Fox" || it.name == "Fox channel" })
    }
}
