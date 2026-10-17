package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource.GeeEvent
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource.GeeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El vigilante de vida: quÃ© seÃ±ales se comprueban, cada cuÃ¡nto y quiÃ©n manda cuando la app ya
 * comprobÃ³ algo (lo de ahora, no el dato viejo del servidor).
 */
class GeeHealthTest {

    private fun link(url: String, alive: Boolean? = null) =
        GeeLink(name = url, link = url, headers = emptyMap(), type = "hls", drm = "", alive = alive)

    private fun event(id: String, live: Boolean = false, status: String = "", links: List<GeeLink> = emptyList()) =
        GeeEvent(
            id = id,
            category = "Football",
            name = id,
            logo = "",
            teamA = "",
            teamB = "",
            dateIso = "2026-10-17",
            dateText = "",
            time = "19:00:00",
            alive = links.size,
            total = links.size,
            links = links,
            live = live,
            status = status,
        )

    private val day = GeeAgenda.startMs(event("x"))!!

    @Test
    fun onlyTheMatchesBeingPlayedAreWatched() {
        val playing = event("live", live = true, status = "in", links = listOf(link("https://a/1.m3u8")))
        val later = event("soon", links = listOf(link("https://b/2.m3u8")), ).copy(time = "23:00:00")
        val played = event("done", status = "post", links = listOf(link("https://c/3.m3u8")))
        val watched = GeeHealth.urlsToWatch(listOf(playing, later, played), emptyList(), day)
        assertEquals(listOf("https://a/1.m3u8"), watched)
    }

    @Test
    fun theWholeMatchIsWatchedIncludingTheSignalsTheServerGaveAsDown() {
        // Una caÃ­da puede levantarse: se comprueba igual.
        val playing = event(
            "live",
            live = true,
            links = listOf(link("https://a/1.m3u8", alive = true), link("https://b/2.m3u8", alive = false)),
        )
        assertEquals(
            listOf("https://a/1.m3u8", "https://b/2.m3u8"),
            GeeHealth.urlsToWatch(listOf(playing), emptyList(), day),
        )
    }

    @Test
    fun theGuideChannelsOfTheMatchAreWatchedTooSoARecoveryIsNoticed() {
        // Â«TelemundoÂ» es un canal 24/7 de la parrilla del partido: si se levanta, hay que verlo.
        val telemundo = GeeSportsSource.GeeLink(
            name = "TELEMUNDO EAST HD",
            link = "https://grupo/telemundo.m3u8",
            headers = emptyMap(),
            type = "hls",
            drm = "",
            alive = false,
        )
        val group = GeeSportsSource.GeeGroup(
            id = "g1",
            name = "Telemundo",
            logo = "",
            alive = 0,
            total = 1,
            channels = listOf(GeeSportsSource.GeeChannel(name = "Telemundo", logo = "", links = listOf(telemundo))),
        )
        val playing = event("live", live = true, links = listOf(link("https://a/1.m3u8")))
            .copy(broadcasts = listOf("Telemundo"))
        assertEquals(
            listOf("https://a/1.m3u8", "https://grupo/telemundo.m3u8"),
            GeeHealth.urlsToWatch(listOf(playing), listOf(group), day),
        )
    }

    @Test
    fun repeatedSignalsAreWatchedOnceAndTheListStaysBounded() {
        val many = (1..(GeeHealth.MAX_PER_PASS + 8)).map { link("https://x/$it.m3u8") } + link("https://x/1.m3u8")
        val playing = event("live", live = true, links = many)
        val watched = GeeHealth.urlsToWatch(listOf(playing), emptyList(), day)
        assertEquals(GeeHealth.MAX_PER_PASS, watched.size)
        assertEquals(watched.size, watched.distinct().size)
    }

    @Test
    fun whatTheAppCheckedNowBeatsTheServersOwnData() {
        val stale = GeeHealth.Check(alive = false, atMs = 1L)
        assertEquals(false, GeeHealth.effective(serverAlive = true, check = stale))
        val recovered = GeeHealth.Check(alive = true, atMs = 2L)
        assertEquals(true, GeeHealth.effective(serverAlive = false, check = recovered))
        // Sin comprobaciÃ³n propia, manda el servidor (y sin nada, no se sabe).
        assertEquals(true, GeeHealth.effective(serverAlive = true, check = null))
        assertNull(GeeHealth.effective(serverAlive = null, check = null))
    }

    @Test
    fun theTickIsThirtySeconds() {
        assertTrue("el ciclo del vigilante no debe ser agresivo", GeeHealth.INTERVAL_MS >= 20_000L)
    }
}
