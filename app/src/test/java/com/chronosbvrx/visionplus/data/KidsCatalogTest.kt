package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El bloque infantil debe apuntar a las categorías que el broker sí puede firmar.
 * `xtv-kids-all` mezcla títulos sin licencia: volver a él dejaba las fichas sin
 * reproducción (regresión 2.6.37/2.6.38).
 */
class KidsCatalogTest {

    private fun film(id: String, kind: String, language: String = "") = Film(
        id = id,
        title = id,
        year = 2025,
        durationMin = 0,
        synopsis = "",
        posterUrl = "",
        videoUrl = "xtv:$id",
        sourceUrl = "test",
        kind = kind,
        originalLanguage = language,
    )

    @Test
    fun `movies and series point to the playable daughters`() {
        assertEquals("xtv-kids-76805", KidsCatalog.MOVIES)
        assertEquals("xtv-kids-76806", KidsCatalog.SERIES)
        assertEquals("xtv-kids-76806", KidsCatalog.categoryFor(series = true))
        assertEquals("xtv-kids-76805", KidsCatalog.categoryFor(series = false))
    }

    @Test
    fun `kids keys are recognised without catching plain sections`() {
        assertTrue(KidsCatalog.isKidsKey("kids"))
        assertTrue(KidsCatalog.isKidsKey("kids-movies"))
        assertTrue(KidsCatalog.isKidsKey("kids-series"))
        assertFalse(KidsCatalog.isKidsKey("movies"))
        assertFalse(KidsCatalog.isKidsKey("series"))
    }

    @Test
    fun `unknown language trusts the portal kids collection instead of dropping it`() {
        val films = listOf(
            film("1", "kids"),
            film("2", "anime"),
            film("3", "movie"),
            film("4", ""),
        )
        val kept = KidsCatalog.keep(films)
        assertEquals(listOf("1", "2", "3", "4"), kept.map { it.id })
    }

    @Test
    fun `western animation tagged anime stays in kids and japanese does not`() {
        val films = listOf(
            film("shrek", "anime", "en"),
            film("barbie", "anime", "en"),
            film("dragonball", "anime", "ja"),
            film("anime-kids-tag", "kids", "ja"),
            film("paw-patrol", "kids", "en"),
        )
        val kept = KidsCatalog.keep(films)
        assertEquals(listOf("shrek", "barbie", "paw-patrol"), kept.map { it.id })
    }
}
