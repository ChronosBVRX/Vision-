package com.chronosbvrx.visionplus.data.gee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Relevancia de Deportes: primero lo reproducible, luego el tier del servidor y
 * después la disponibilidad de canales. El mismo criterio para fútbol y otros deportes.
 */
class SportsRelevanceTest {

    private fun link(url: String = "https://example.com/live.m3u8", alive: Boolean? = true) =
        GeeSportsSource.GeeLink(
            name = "Señal",
            link = url,
            headers = emptyMap(),
            type = "hls",
            drm = "",
            alive = alive,
        )

    private fun house(name: String) = GeeSportsSource.XtvOption(name = name, code = name.lowercase())

    private fun event(
        id: String,
        tier: Int = 0,
        category: String = "Football",
        league: String = "Liga MX",
        alive: Int = 0,
        links: List<GeeSportsSource.GeeLink> = emptyList(),
        xtv: List<GeeSportsSource.XtvOption> = emptyList(),
        broadcasts: List<String> = emptyList(),
    ) = GeeSportsSource.GeeEvent(
        id = id,
        category = category,
        name = id,
        logo = "",
        teamA = "",
        teamB = "",
        dateIso = "2026-10-17",
        dateText = "",
        time = "19:00:00",
        alive = alive,
        total = links.size,
        links = links,
        leagueLabel = league,
        broadcasts = broadcasts,
        tier = tier,
        xtv = xtv,
    )

    @Test
    fun availabilityCountsHealthyLinksAndHouseChannels() {
        val byAlive = event("a", alive = 2)
        val byLink = event("b", links = listOf(link(alive = true), link(alive = false)))
        val byHouse = event("c", xtv = listOf(house("FOX One"), house("TUDN")))
        val dead = event("d", links = listOf(link(alive = false)))

        assertEquals(2, SportsRelevance.availableChannelCount(byAlive))
        assertEquals(1, SportsRelevance.availableChannelCount(byLink))
        assertEquals(2, SportsRelevance.availableChannelCount(byHouse))
        assertEquals(0, SportsRelevance.availableChannelCount(dead))
        assertTrue(SportsRelevance.hasPlayableChannel(byHouse))
        assertFalse(SportsRelevance.hasPlayableChannel(dead))
    }

    @Test
    fun aTvGuideNameAloneIsNotAPlayableChannel() {
        val guideOnly = event("g", broadcasts = listOf("ESPN", "Canal 5"))
        assertFalse("la guía de TV no es un enlace reproducible", SportsRelevance.hasPlayableChannel(guideOnly))
        assertEquals(0, SportsRelevance.availableChannelCount(guideOnly))
        assertTrue("pero sí pesa para desempatar", SportsRelevance.channelScore(guideOnly) > 0)
    }

    @Test
    fun bestFirstPutsPlayableAboveNoSignalEvenIfTheTierIsHigher() {
        val tier1NoSignal = event("t1", tier = 1, alive = 0)
        val tier2Playable = event("t2", tier = 2, league = "Serie B", alive = 1)
        assertEquals(
            listOf("t2", "t1"),
            listOf(tier1NoSignal, tier2Playable).sortedWith(SportsRelevance.bestFirst).map { it.id },
        )
    }

    @Test
    fun withinTheSameTierMoreChannelsLead() {
        val few = event("few", tier = 1, alive = 1)
        val many = event("many", tier = 1, alive = 4)
        assertEquals(
            listOf("many", "few"),
            listOf(few, many).sortedWith(SportsRelevance.bestFirst).map { it.id },
        )
    }

    @Test
    fun theServerTierStillBeatsChannelCount() {
        val mainFew = event("main", tier = 1, alive = 1)
        val secondaryMany = event("secondary", tier = 2, league = "Serie B", alive = 8)
        assertEquals(
            listOf("main", "secondary"),
            listOf(secondaryMany, mainFew).sortedWith(SportsRelevance.bestFirst).map { it.id },
        )
    }

    @Test
    fun otherSportsUseTheSameComparator() {
        val nba = event("nba", tier = 1, category = "Basketball", league = "NBA", alive = 2)
        val serieB = event("sb", tier = 2, league = "Serie B", alive = 6)
        assertEquals(
            listOf("nba", "sb"),
            listOf(serieB, nba).sortedWith(SportsRelevance.bestFirst).map { it.id },
        )
    }
}
