package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tarjeta de espera del reproductor nativo debe hablar del contenido real:
 * película, serie con su temporada/capítulo exacto, o canal en vivo. Antes decía
 * «Preparando tu película…» incluso al abrir una serie y el título se quedaba
 * congelado en el primer capítulo de la sesión (4x3 aunque se viera el 4x8).
 */
class PlayerLoadingCardTest {

    @Test
    fun movieKeepsTheMovieWordingAndItsOwnTitle() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = false,
            baseTitle = "",
            rawTitle = "Spider-Man 2",
            seasonLabel = "",
            episodeIndex = 0,
        )
        assertEquals("Spider-Man 2", card.title)
        assertEquals("Preparando tu película…", card.subtitle)
        assertEquals("", card.episodeName)
        assertFalse(card.accent)
    }

    @Test
    fun seriesShowsTheExactSeasonAndEpisodeBeingOpened() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "La Casa de las Flores",
            rawTitle = "La Casa de las Flores · T4 · C3",
            seasonLabel = "T4",
            episodeIndex = 7,
        )
        assertEquals("La Casa de las Flores", card.title)
        assertEquals("T4 · Capítulo 8", card.subtitle)
        assertTrue(card.accent)
    }

    @Test
    fun seriesShowsTheRealEpisodeNameWhenThePortalHasOne() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "La Casa de las Flores",
            rawTitle = "La Casa de las Flores · T4 · C3",
            seasonLabel = "T4",
            episodeIndex = 2,
            episodeName = "  La otra boda  ",
        )
        assertEquals("T4 · Capítulo 3", card.subtitle)
        assertEquals("La otra boda", card.episodeName)
    }

    @Test
    fun genericEpisodeNamesAreSuppressed() {
        val generic = listOf("Capítulo 3", "Capitulo 3", "Episodio 3", "3", "capítulo 3")
        for (name in generic) {
            val card = resolveLoadingCardText(
                isLive = false,
                isSeries = true,
                baseTitle = "Serie",
                rawTitle = "Serie",
                seasonLabel = "T4",
                episodeIndex = 2,
                episodeName = name,
            )
            assertEquals("", card.episodeName)
        }
    }

    @Test
    fun singleSeasonSeriesShowsOnlyTheEpisode() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "La Casa de las Flores",
            rawTitle = "La Casa de las Flores · C3",
            seasonLabel = "",
            episodeIndex = 2,
        )
        assertEquals("La Casa de las Flores", card.title)
        assertEquals("Capítulo 3", card.subtitle)
        assertTrue(card.accent)
    }

    @Test
    fun liveKeepsItsSignalLine() {
        val card = resolveLoadingCardText(
            isLive = true,
            isSeries = false,
            baseTitle = "",
            rawTitle = "TUDN MX",
            seasonLabel = "",
            episodeIndex = 0,
        )
        assertEquals("TUDN MX", card.title)
        assertEquals("Preparando la señal…", card.subtitle)
        assertEquals("", card.episodeName)
        assertFalse(card.accent)
    }

    @Test
    fun blankBaseTitleFallsBackToTheRawTitle() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "   ",
            rawTitle = "Serie · T4 · C3",
            seasonLabel = "T4",
            episodeIndex = 2,
        )
        assertEquals("Serie · T4 · C3", card.title)
        assertEquals("T4 · Capítulo 3", card.subtitle)
    }

    @Test
    fun episodeIndexNeverShowsChapterZero() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "Anime",
            rawTitle = "Anime",
            seasonLabel = "T1",
            episodeIndex = -3,
        )
        assertEquals("T1 · Capítulo 1", card.subtitle)
    }

    @Test
    fun portalMarkedEpisodeNamesAreSuppressedAndNeverShowUnderscores() {
        // Caso real del portal (verificado 2026-10-16): «Ley y orden: … Especiales T1_11».
        // La marca técnica no es un nombre: la espera se queda con «T1 · Capítulo 11».
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "Ley y orden: Unidad de Víctimas Especiales",
            rawTitle = "Ley y orden: Unidad de Víctimas Especiales · T1 · C11",
            seasonLabel = "T1",
            episodeIndex = 10,
            episodeName = "Ley y orden: Unidad de Víctimas Especiales T1_11",
        )
        assertEquals("T1 · Capítulo 11", card.subtitle)
        assertEquals("", card.episodeName)
    }

    @Test
    fun realEpisodeNamesWithUnderscoresShowNormalized() {
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "Serie",
            rawTitle = "Serie",
            seasonLabel = "T2",
            episodeIndex = 4,
            episodeName = "El_robo_del_siglo",
        )
        assertEquals("El robo del siglo", card.episodeName)
    }

    @Test
    fun seasonSuffixedSeriesTitleAlsoSuppressesTheMarkedName() {
        // La ficha de una temporada suelta llega como «Cielo T1»: igual debe reconocer la marca.
        val card = resolveLoadingCardText(
            isLive = false,
            isSeries = true,
            baseTitle = "Cielo T1",
            rawTitle = "Cielo T1 · C2",
            seasonLabel = "T1",
            episodeIndex = 1,
            episodeName = "Cielo T1_2",
        )
        assertEquals("", card.episodeName)
        assertEquals("T1 · Capítulo 2", card.subtitle)
    }

    @Test
    fun episodeRowLabelFallsBackToTheChapterNumberWhenThereIsNoRealName() {
        assertEquals(
            "Capítulo 11",
            episodeRowLabel("Ley y orden: Unidad de Víctimas Especiales T1_11", 11, "Ley y orden: Unidad de Víctimas Especiales"),
        )
        assertEquals("El robo del siglo", episodeRowLabel("El_robo_del_siglo", 5, "Serie"))
        assertEquals("Capítulo 3", episodeRowLabel("Episodio 3", 3, "Serie"))
        assertEquals("Capítulo 1", episodeRowLabel("", 1, "Serie"))
    }
}
