package com.chronosbvrx.visionplus.data.remote

import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El deep link `visionplus://live/<código>` que manda el teléfono: se prefiere el canal del
 * catálogo cacheado (logo y grupo reales) y, si no está, se arma con lo que viajó en el enlace.
 */
class RemoteDeepLinkTest {

    @Test
    fun prefersTheCachedChannelWithItsRealArt() {
        val cached = listOf(
            LiveChannel(name = "Azteca 7", url = "xtvlive:100", logo = "http://real.png", group = "México"),
            LiveChannel(name = "Otra", url = "xtvlive:200"),
        )
        val channel = remoteLiveChannel(" 100 ", "nombre viejo", "http://viejo.png", cached)
        assertEquals("Azteca 7", channel?.name)
        assertEquals("http://real.png", channel?.logo)
        assertEquals("xtvlive:100", channel?.url)
    }

    @Test
    fun fallsBackToTheLinkFieldsSoTheButtonNeverDies() {
        val channel = remoteLiveChannel("7", "Canal 5", "http://c5.png", emptyList())
        assertEquals("Canal 5", channel?.name)
        assertEquals("xtvlive:7", channel?.url)
        assertEquals("http://c5.png", channel?.logo)
    }

    @Test
    fun blankNameBecomesAGenericChannel() {
        val channel = remoteLiveChannel("9", "   ", null, emptyList())
        assertEquals("Canal", channel?.name)
        assertNull(channel?.logo)
    }

    @Test
    fun withoutCodeThereIsNoChannel() {
        assertNull(remoteLiveChannel("", "Canal", null, emptyList()))
        assertNull(remoteLiveChannel("   ", "Canal", null, emptyList()))
    }
}
