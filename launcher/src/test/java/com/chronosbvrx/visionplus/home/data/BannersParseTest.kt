package com.chronosbvrx.visionplus.home.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El JSON del VPS se traduce a diapositivas sin tocar Android (probado en JVM). */
class BannersParseTest {

    @Test
    fun aMovieBannerGetsTitleArtAndTheTwoDeepLinks() {
        val json = """
            {"generatedAt": 1, "items": [
              {"id":"movie:xtv:6950","kind":"movie","title":"Spider-Man: un nuevo día",
               "poster":"https://img/p.jpg","backdrop":"https://img/b.jpg","synopsis":"Peta",
               "year":2026,"rating":8.2,"durationMin":128,"series":false,
               "ref":"xtv:6950","videoUrl":"xtv:6950","badge":"DESTACADO HOY",
               "eyebrow":"PELÍCULA DESTACADA"}
            ]}
        """.trimIndent()
        val slides = parseBanners(json)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals("Spider-Man: un nuevo día", slide.title)
        assertEquals("https://img/b.jpg", slide.backdrop)
        assertEquals(8.2, slide.rating, 0.01)
        assertFalse(slide.series)
        assertTrue(slide.openLink.startsWith("visionplus://title/"))
        assertTrue(slide.openLink.contains("visionplus%3A6950") || slide.openLink.contains("xtv%3A6950"))
        assertTrue(slide.playLink.startsWith("visionplus://watch/"))
        assertTrue(slide.playLink.contains("c=6950"))
    }

    @Test
    fun aSeriesBannerOpensTheFicheAndNeverAutoplays() {
        val json = """
            {"items": [
              {"id":"movie:xtv:HOUSE","kind":"movie","title":"House","poster":"p",
               "backdrop":"","series":true,"ref":"xtv:HOUSE","videoUrl":"xtv:HOUSE"}
            ]}
        """.trimIndent()
        val slide = parseBanners(json).single()
        assertTrue(slide.series)
        assertEquals("", slide.playLink)
        assertEquals("p", slide.backdrop) // sin arte panorámico se usa el póster
        assertTrue(slide.openLink.startsWith("visionplus://title/"))
    }

    @Test
    fun aSportsBannerKeepsTheScoreboardAndTheEventLink() {
        val json = """
            {"items": [
              {"id":"sports:evt-1","kind":"sports","title":"América vs Cruz Azul",
               "league":"Liga MX","homeName":"América","awayName":"Cruz Azul",
               "homeScore":2,"awayScore":1,"live":true,"status":"in","detail":"67'",
               "startMs":1761530400000,"eventId":"evt-1","badge":"EN VIVO",
               "eyebrow":"DEPORTES · EN VIVO"}
            ]}
        """.trimIndent()
        val slide = parseBanners(json).single()
        assertTrue(slide.sports)
        assertTrue(slide.live)
        assertEquals(2, slide.homeScore)
        assertEquals(1, slide.awayScore)
        assertEquals("67'", slide.detail)
        assertEquals("visionplus://open/sports?event=evt-1", slide.openLink)
    }

    @Test
    fun garbageNeverBreaksTheHome() {
        assertTrue(parseBanners("").isEmpty())
        assertTrue(parseBanners("{}").isEmpty())
        assertTrue(parseBanners("{\"items\":[null,3,{\"title\":\"sin kind\"}]}").isNotEmpty())
        assertTrue(parseBanners("no es json").isEmpty())
    }

    @Test
    fun theCacheExpiresWhenTheServerWillRefresh() {
        val next = 1_000_000L
        assertEquals(next + 5 * 60 * 1000L, parseBannersExpiry("""{"nextRefreshAt":$next}"""))
    }

    @Test
    fun anOldServerWithoutNextRefreshFallsBackToSixHours() {
        val fileAt = 1_000_000L
        assertEquals(fileAt + 6 * 60 * 60 * 1000L, parseBannersExpiry("{}", fallbackAt = fileAt))
        assertEquals(0L, parseBannersExpiry("{}"))
    }

    @Test
    fun readingTheCacheDoesNotMakeItLookNewer() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/home/data/BannersRepository.kt").readText()
        assertTrue(source.contains("target.lastModified()"))
        assertTrue(source.contains("parseBannersExpiry(raw, target.lastModified())"))
        assertFalse(source.contains("now to slides"))
        assertTrue(source.contains("Mutex()"))
        assertTrue(source.contains("withLock"))
    }

    @Test
    fun theArtworkKeyIgnoresTheDailyCdnVersion() {
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/abc.jpg",
            bannerArtCacheKey("https://image.tmdb.org/t/p/w1280/abc.jpg?v=123456"),
        )
        assertEquals("https://img/b.jpg", bannerArtCacheKey("https://img/b.jpg"))
    }
}
