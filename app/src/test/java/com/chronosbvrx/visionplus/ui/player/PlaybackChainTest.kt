package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La cadena de respaldo no es "el orden del portal con la preferida al frente":
 * si el usuario eligió 720p y esa pista muere, el siguiente intento debe ser 480p,
 * no un 4K que su equipo no puede sostener. Las resoluciones superiores quedan al
 * final, como último recurso.
 */
class PlaybackChainTest {

    private data class Media(val name: String, val quality: String)

    private val medias = listOf(
        Media("4k-a", "4K"),
        Media("1080-a", "1080p"),
        Media("720-a", "720p"),
        Media("720-b", "720p"),
        Media("480-a", "480p"),
    )

    @Test
    fun thePreferredTierGoesFirstThenLowerTiersAndHigherTiersLast() {
        val chain = playbackChain(medias, { it.quality }, "720p")
        assertEquals(listOf("720-a", "720-b", "480-a", "1080-a", "4k-a"), chain.map { it.name })
    }

    @Test
    fun aFullHdPreferenceFallsBackToHdBeforeSdAndLeaves4kForLast() {
        val chain = playbackChain(medias, { it.quality }, "1080p")
        assertEquals(listOf("1080-a", "720-a", "720-b", "480-a", "4k-a"), chain.map { it.name })
    }

    @Test
    fun autoAndUnknownPreferencesKeepTheRankedOrder() {
        assertEquals(medias, playbackChain(medias, { it.quality }, "Auto"))
        assertEquals(medias, playbackChain(medias, { it.quality }, "auto"))
        assertEquals(medias, playbackChain(medias, { it.quality }, ""))
        assertEquals(medias, playbackChain(medias, { it.quality }, "8K"))
    }

    @Test
    fun theFirstMediaOfTheChainIsTheOneThePreferredOrderWouldStart() {
        val oldStart = orderMediasByQuality(medias, { it.quality }, "720p").first()
        val newStart = playbackChain(medias, { it.quality }, "720p").first()
        assertEquals(oldStart, newStart)
    }

    @Test
    fun unknownQualitiesStayAtTheEndAndNeverLeapfrogAKnownTier() {
        val mixed = listOf(
            Media("unknown", ""),
            Media("4k", "4K"),
            Media("1080", "1080p"),
            Media("720", "720p"),
            Media("480", "480p"),
        )
        val chain = playbackChain(mixed, { it.quality }, "720p")
        assertEquals(listOf("720", "480", "1080", "4k", "unknown"), chain.map { it.name })
    }

    @Test
    fun aSingleMediaOrAnEmptyListIsLeftAlone() {
        val only = listOf(Media("only", "1080p"))
        assertEquals(only, playbackChain(only, { it.quality }, "720p"))
        assertEquals(emptyList<Media>(), playbackChain(emptyList<Media>(), { it.quality }, "720p"))
    }
}
