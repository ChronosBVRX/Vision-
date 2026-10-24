package com.chronosbvrx.visionplus.home.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionLinksTest {

    @Test
    fun watchCarriesEveryPieceOfContext() {
        val link = VisionLinks.watch(
            key = "abc 123",
            title = "Serie · T2 · C3",
            poster = "https://img/p.jpg",
            code = "555",
            videoUrl = "",
            seriesId = "s1",
            seasonIndex = 1,
            seasonIds = listOf("s1", "s2"),
            seasonNames = listOf("T1", "T2"),
            resumeMs = 1_500_000,
        )
        assertTrue(link.startsWith("visionplus://watch/abc%20123?"))
        assertTrue(link.contains("t=Serie%20%C2%B7%20T2%20%C2%B7%20C3"))
        assertTrue(link.contains("c=555"))
        assertTrue(link.contains("s=s1"))
        assertTrue(link.contains("sx=1"))
        assertTrue(link.contains("sl=s1%7Cs2"))
        assertTrue(link.contains("sn=T1%7CT2"))
        assertTrue(link.contains("r=1500000"))
    }

    @Test
    fun spacesTravelAsPercentTwentySoAndroidReadsThemBack() {
        // Uri.getQueryParameter NO convierte '+' en espacio: el launcher debe mandar %20.
        assertTrue(VisionLinks.title("El juego del calamar").contains("El%20juego%20del%20calamar"))
        assertFalse(VisionLinks.title("El juego del calamar").contains("+"))
    }

    @Test
    fun moviesDoNotDragEmptySeriesParameters() {
        val link = VisionLinks.watch(key = "xtv:9", title = "Moana", code = "9")
        assertFalse(link.contains("s="))
        assertFalse(link.contains("sx="))
        assertFalse(link.contains("sl="))
        assertFalse(link.contains("r="))
    }

    @Test
    fun liveCarriesTheNameAndLogoSoThePlayerPaintsInstantly() {
        val link = VisionLinks.live("cyx_5", "Canal 5", "https://img/5.png")
        assertEquals("visionplus://live/cyx_5?t=Canal%205&p=https%3A%2F%2Fimg%2F5.png", link)
    }

    @Test
    fun searchOnlyAddsTheQueryWhenThereIsOne() {
        assertEquals("visionplus://open/search", VisionLinks.search())
        assertEquals("visionplus://open/search?q=tarantino", VisionLinks.search("tarantino"))
        assertTrue(VisionLinks.search("guerra de papas").endsWith("q=guerra%20de%20papas"))
    }

    @Test
    fun theSportsSectionCanOpenASpecificEvent() {
        assertEquals("visionplus://open/sports", VisionLinks.sports())
        assertEquals("visionplus://open/sports?event=event%3A7", VisionLinks.sports("event:7"))
    }

    @Test
    fun theOtherDestinationsAreStable() {
        assertEquals("visionplus://open/settings", VisionLinks.settings())
        assertEquals("visionplus://open/home", VisionLinks.home())
        assertEquals("visionplus://open/library", VisionLinks.library())
        assertEquals("visionplus://open/live", VisionLinks.liveHub())
        assertEquals("visionplus://open/section/movies", VisionLinks.section("movies"))
        assertEquals(
            "visionplus://title/xtv%3A77?t=Moana&p=https%3A%2F%2Fimg%2Fm.jpg&u=xtv%3A77",
            VisionLinks.title("xtv:77", "Moana", "https://img/m.jpg", "xtv:77"),
        )
        assertEquals("visionplus://title/xtv%3A77", VisionLinks.title("xtv:77"))
    }
}
