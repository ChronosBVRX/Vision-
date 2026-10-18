package com.chronosbvrx.visionplus.ui.remote

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.SeasonRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El selector de temporada/capítulo del control remoto: qué títulos abren el selector y qué
 * viaja al cable cuando se elige un capítulo (la ref del capítulo + TODO el contexto de su
 * serie, para que la tele abra ESE capítulo y pueda seguir con el siguiente).
 */
class RemoteCastEpisodeTest {

    private fun film(kind: String = "series", seasons: List<SeasonRef> = emptyList()) = Film(
        id = "xtv:S1",
        title = "La Casa de Papel",
        year = 2024,
        durationMin = 45,
        synopsis = "",
        posterUrl = "http://art/s.jpg",
        videoUrl = "xtv:S1",
        sourceUrl = "xtv",
        kind = kind,
        seasons = seasons,
    )

    @Test
    fun seriesKindsOpenThePickerAndMoviesGoStraightIn() {
        assertTrue(isCastSeries(film(kind = "series")))
        assertTrue(isCastSeries(film(kind = "teleplay")))
        assertTrue(isCastSeries(film(kind = "anime")))
        assertTrue("el tipo del servidor puede venir capitalizado", isCastSeries(film(kind = "Series")))
        assertTrue(
            "con temporadas no importa el tipo",
            isCastSeries(film(kind = "movie", seasons = listOf(SeasonRef(1, "t1", "xtv:t1")))),
        )
        assertFalse(isCastSeries(film(kind = "movie")))
        assertFalse(isCastSeries(film(kind = "")))
        assertFalse(isCastSeries(film(kind = "kids")))
    }

    @Test
    fun episodeOrderCarriesTheWholeSeriesContext() {
        val f = film(
            seasons = listOf(
                SeasonRef(1, "t1ref", "xtv:t1ref"),
                SeasonRef(2, "t2ref", "xtv:t2ref"),
            ),
        )
        val ep = castEpisodeFor(f, seasonIndex = 1, episodeCode = "EP5", episodeNumber = 5)
        assertEquals("xtv:EP5", ep.ref)
        assertEquals("La Casa de Papel · T2 · C5", ep.title)
        assertEquals("t2ref", ep.seriesId)
        assertEquals(1, ep.seasonIndex)
        assertEquals(listOf("t1ref", "t2ref"), ep.seasonIds)
        assertEquals(listOf("T1", "T2"), ep.seasonNames)
        assertEquals("http://art/s.jpg", ep.poster)
    }

    @Test
    fun singleSeasonSeriesHasNoSeasonListAndCoercesTheIndex() {
        val ep = castEpisodeFor(film(), seasonIndex = 3, episodeCode = "xtv:EP1", episodeNumber = 0)
        assertEquals("xtv:EP1", ep.ref)
        assertEquals("La Casa de Papel · C1", ep.title)
        assertEquals("El id de la serie manda cuando no hay lista de temporadas", "xtv:S1", ep.seriesId)
        assertEquals(0, ep.seasonIndex)
        assertTrue(ep.seasonIds.isEmpty())
        assertTrue(ep.seasonNames.isEmpty())
    }

    @Test
    fun seasonLabelsMatchTheCatalogConvention() {
        assertEquals("T3", castSeasonLabel(3))
        assertEquals("Completa", castSeasonLabel(0))
    }

    @Test
    fun episodeRefIsAlwaysPrefixed() {
        assertEquals("xtv:ABC", castEpisodeRef("ABC"))
        assertEquals("xtv:ABC", castEpisodeRef(" xtv:ABC "))
        // Un código vacío no es abrible: el cable lo rechaza más adelante (jamás abre a medias).
        assertEquals("xtv:", castEpisodeRef(""))
    }

    @Test
    fun episodeTitleFallsBackToTheNumber() {
        assertEquals("Serie · C1", castEpisodeTitle("Serie", null, 1))
        assertEquals("Serie · T2 · C5", castEpisodeTitle("  Serie ", "T2", 5))
        assertEquals("Serie · C1", castEpisodeTitle("Serie", "  ", 0))
    }
}
