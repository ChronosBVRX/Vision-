package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {
    private val sample = """
        #EXTM3U
        #EXTINF:-1 tvg-id="CineUno.mx" tvg-logo="https://logo.example/1.png" group-title="Cine",Cine Uno
        #EXTVLCOPT:http-user-agent=Mozilla/5.0
        #EXTVLCOPT:http-referrer=https://www.example.com/
        http://example.com/stream1.m3u8
        #EXTGRP:Noticias
        #EXTINF:-1,Canal Dos
        http://example.com/stream2.m3u8
        #EXTINF:-1 tvg-name="Tres HD",
        https://example.com/stream3.m3u8
        #EXTINF:-1,Sin dirección
        #EXTINF:-1,Radio por rtsp
        rtsp://example.com/radio
        #EXTINF:-1,Cine Uno (repetido)
        http://example.com/stream1.m3u8
    """.trimIndent()

    @Test
    fun `parses names, logos, groups and VLC options`() {
        val channels = parseM3u(sample)
        assertEquals(3, channels.size)

        val first = channels[0]
        assertEquals("Cine Uno", first.name)
        assertEquals("Cine", first.group)
        assertEquals("https://logo.example/1.png", first.logo)
        assertEquals("Mozilla/5.0", first.userAgent)
        assertEquals("https://www.example.com/", first.referrer)

        val second = channels[1]
        assertEquals("Canal Dos", second.name)
        assertEquals("Noticias", second.group)
        assertNull(second.logo)
        assertNull(second.userAgent)

        assertEquals("Tres HD", channels[2].name)
        assertEquals("Sin categoría", channels[2].group)
    }

    @Test
    fun `ignores entries without a URL, non-http URLs and duplicates`() {
        val channels = parseM3u(sample)
        assertTrue(channels.none { it.url.startsWith("rtsp://") })
        assertEquals(1, channels.count { it.url == "http://example.com/stream1.m3u8" })
    }

    @Test
    fun `reads an EXTINF through its URL line only`() {
        val channels = parseM3u("#EXTM3U\n#EXTINF:-1,Sola\n#EXTINF:-1,Otra\nhttp://example.com/a.m3u8")
        assertEquals(1, channels.size)
        assertEquals("Otra", channels[0].name)
    }
}
