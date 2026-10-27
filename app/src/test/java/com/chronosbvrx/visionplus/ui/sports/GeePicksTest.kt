package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lista del selector de un partido: SOLO los enlaces que GeeSports publica para
 * ese evento. Si la fuente no manda ninguno, la lista queda vacía y la UI muestra
 * «Sin enlaces disponibles».
 *
 * Es el guardián de «la única fuente de verdad es GeeSports»: nada de guía/parrilla
 * ni de canales del catálogo colándose como si fueran señales del partido.
 */
class GeePicksTest {

    private fun link(
        name: String,
        url: String = "https://cdn.test/${name.lowercase().replace(' ', '_')}.m3u8",
        alive: Boolean? = true,
        type: String = "hls",
        drm: String = "",
    ) = GeeSportsSource.GeeLink(
        name = name,
        link = url,
        headers = emptyMap(),
        type = type,
        drm = drm,
        alive = alive,
    )

    @Test
    fun onlyTheLinksGeeSportsPublishesAreOffered() {
        val rows = geePickRows(
            title = "Sevilla vs Osasuna",
            links = listOf(link("FANCODE FHD"), link("Cazé TV FHD")),
        )
        assertEquals(listOf("FANCODE FHD", "Cazé TV FHD"), rows.map { it.title })
    }

    @Test
    fun aSignalWithoutAddressIsNeverOffered() {
        val rows = geePickRows(
            title = "León vs América",
            links = listOf(link("TUDN US", url = ""), link("CANAL MX FHD")),
        )
        assertEquals(listOf("CANAL MX FHD"), rows.map { it.title })
    }

    @Test
    fun aDuplicateUrlWithoutHeadersIsListedOnlyOnce() {
        val rows = geePickRows(
            title = "Sevilla vs Osasuna",
            links = listOf(
                link("ESPN", "https://cdn.test/espn.m3u8"),
                link("ESPN", "https://cdn.test/espn.m3u8|user-agent=UA"),
            ),
        )
        assertEquals(1, rows.size)
    }

    @Test
    fun theSignalThatWasPlayingIsMarkedByItsAddressWithoutHeaders() {
        val rows = geePickRows(
            title = "Sevilla vs Osasuna",
            links = listOf(
                link("FANCODE FHD", "https://cdn.test/fancode.m3u8"),
                link("Cazé TV FHD", "https://cdn.test/caze.m3u8|user-agent=UA"),
            ),
            playingUrl = "https://cdn.test/caze.m3u8",
        )
        assertEquals(listOf(false, true), rows.map { it.playing })
    }

    @Test
    fun theFreshCheckWinsOverTheServersOldVerdict() {
        val url = "https://cdn.test/tudn.m3u8"
        val rows = geePickRows(
            title = "Santos vs Querétaro",
            links = listOf(link("TUDN MEXICO FHD", url, alive = false)),
            checks = mapOf(url to GeeHealth.Check(alive = true, atMs = 1L)),
        )
        assertEquals(true, rows[0].alive)
    }

    @Test
    fun theSelectorNeverGrowsWithoutLimit() {
        val links = (1..MAX_PICK_ROWS + 10).map { link("Señal $it") }
        val rows = geePickRows(title = "Partido", links = links)
        assertEquals(MAX_PICK_ROWS, rows.size)
    }

    @Test
    fun noLinksLeavesTheListEmptyForTheLegend() {
        assertTrue(geePickRows(title = "Partido").isEmpty())
        assertTrue(geePickRows(title = "Partido", links = listOf(link("Sin URL", url = ""))).isEmpty())
    }

    @Test
    fun theTypeLabelTellsDashAndClearKeyApart() {
        val row = GeePickRow(
            channel = com.chronosbvrx.visionplus.data.LiveChannel(name = "ESPN", url = "https://cdn.test/e.mpd"),
            title = "ESPN",
            link = "https://cdn.test/e.mpd",
            type = "dash",
            alive = true,
            playing = false,
        )
        assertEquals("DASH", row.typeLabel)
        val clearKey = row.copy(channel = row.channel.copy(drmKey = "kid:key"))
        assertEquals("DASH · ClearKey", clearKey.typeLabel)
        assertEquals("HLS", row.copy(type = "hls").typeLabel)
    }
}
