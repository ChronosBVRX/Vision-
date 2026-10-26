package com.chronosbvrx.visionplus.data.banners

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El JSON de `/v1/banners` se traduce a la foto del Inicio de Vision TV. */
class BannerSourceTest {

    @Test
    fun aMovieBannerKeepsItsArtworkAndReference() {
        val json = """
            {"items": [
              {"id":"movie:xtv:6950","kind":"movie","title":"Spider-Man: Un nuevo día",
               "poster":"https://img/p.jpg","backdrop":"https://img/b.jpg",
               "synopsis":"Peta","year":2026,"rating":8.4,"durationMin":145,
               "series":false,"ref":"xtv:6950","videoUrl":"xtv:6950",
               "badge":"DESTACADO HOY","eyebrow":"PELÍCULA DESTACADA"}
            ]}
        """.trimIndent()
        val banners = parseHomeBanners(json)
        assertEquals(1, banners.size)
        val banner = banners.single()
        assertEquals("Spider-Man: Un nuevo día", banner.title)
        assertEquals("https://img/b.jpg", banner.backdrop)
        assertEquals("xtv:6950", banner.ref)
        assertEquals("xtv:6950", banner.videoUrl)
        assertEquals(8.4, banner.rating, 0.01)
        assertFalse(banner.series)
    }

    @Test
    fun aSeriesBannerIsKeptButTheTvHeroFiltersItLater() {
        val json = """
            {"items": [
              {"id":"movie:xtv:H","kind":"movie","title":"House","poster":"p",
               "series":true,"ref":"xtv:H","videoUrl":"xtv:H"}
            ]}
        """.trimIndent()
        assertTrue(parseHomeBanners(json).single().series)
    }

    @Test
    fun garbageNeverBreaksTheHome() {
        assertTrue(parseHomeBanners("").isEmpty())
        assertTrue(parseHomeBanners("{}").isEmpty())
        assertTrue(parseHomeBanners("no es json").isEmpty())
        assertTrue(parseHomeBanners("{\"items\":[null,3,{\"title\":\"\"}]}").isEmpty())
    }

    @Test
    fun theCacheExpiresWhenTheServerWillRefresh() {
        // 20 h del VPS + 5 min de margen, no el reloj de la tele.
        val next = 1_000_000L
        assertEquals(next + 5 * 60 * 1000L, parseBannerExpiry("""{"nextRefreshAt":$next}"""))
    }

    @Test
    fun anOldServerWithoutNextRefreshFallsBackToSixHours() {
        val fileAt = 1_000_000L
        assertEquals(fileAt + 6 * 60 * 60 * 1000L, parseBannerExpiry("{}", fallbackAt = fileAt))
        assertEquals(0L, parseBannerExpiry("{}"))
    }

    @Test
    fun readingTheCacheDoesNotMakeItLookNewer() {
        // El bug que dejaba la foto pegada: `cached()` marcaba el archivo viejo como recién
        // leído y `load()` ya no pedía el del día. La caducidad debe salir del archivo.
        val source = File("src/main/java/com/chronosbvrx/visionplus/data/banners/BannerSource.kt").readText()
        assertTrue(source.contains("target.lastModified()"))
        assertTrue(source.contains("parseBannerExpiry(raw, target.lastModified())"))
        assertFalse(source.contains("now to banners"))
        assertTrue(source.contains("Mutex()"))
        assertTrue(source.contains("withLock"))
    }

    @Test
    fun theArtworkKeyIgnoresTheDailyCdnVersion() {
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/abc.jpg",
            bannerArtCacheKey("https://image.tmdb.org/t/p/w1280/abc.jpg?v=123456"),
        )
        assertEquals("https://img/p.jpg", bannerArtCacheKey("https://img/p.jpg"))
    }
}
