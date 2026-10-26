package com.chronosbvrx.visionplus.data.banners

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
}
