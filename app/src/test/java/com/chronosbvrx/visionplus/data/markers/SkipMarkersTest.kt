package com.chronosbvrx.visionplus.data.markers

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes de los marcadores de salto: la ventana del botón «Omitir intro», el disparo
 * de la tarjeta de créditos y el contrato JSON con el servidor y con los extras del
 * reproductor. Sin capa manual: donde no hay datos, no se muestra nada.
 */
class SkipMarkersTest {

    // ------------------------------------------------------------ ventanas del intro

    @Test
    fun introWindowStartsAtZeroWhenOnlyTheEndIsKnown() {
        val markers = EpisodeMarkers(introStartMs = -1L, introEndMs = 82_000L)
        val window = skipIntroWindow(markers)!!
        assertEquals(0L, window.first)
        assertEquals(80_000L, window.last) // se oculta 2 s antes del fin (no se salta al vacío)
        assertTrue(inIntroWindow(44_000L, markers))
        assertFalse(inIntroWindow(81_000L, markers))
        assertFalse(inIntroWindow(90_000L, markers))
    }

    @Test
    fun introWindowUsesTheKnownStart() {
        val markers = EpisodeMarkers(introStartMs = 90_000L, introEndMs = 150_000L)
        val window = skipIntroWindow(markers)!!
        assertEquals(90_000L, window.first)
        assertEquals(148_000L, window.last)
        assertFalse(inIntroWindow(89_000L, markers))
        assertTrue(inIntroWindow(140_000L, markers))
    }

    @Test
    fun aWindowThatWouldBeEmptyIsNotOffered() {
        // Fin declarado antes de poder mostrarse (p. ej. 17 s): ventana nula, nunca un botón muerto.
        val tiny = EpisodeMarkers(introStartMs = -1L, introEndMs = 1_000L)
        assertNull(skipIntroWindow(tiny))
        assertNull(skipIntroWindow(EpisodeMarkers()))
        assertFalse(inIntroWindow(10_000L, EpisodeMarkers(creditsStartMs = 2_500_000L)))
    }

    // ------------------------------------------------------------ créditos

    @Test
    fun creditsOnlyTriggerFromTheirStart() {
        val markers = EpisodeMarkers(creditsStartMs = 2_527_000L)
        assertFalse(shouldOfferCredits(2_000_000L, markers))
        assertTrue(shouldOfferCredits(2_527_000L, markers))
        assertTrue(shouldOfferCredits(2_600_000L, markers))
        assertFalse(shouldOfferCredits(2_600_000L, EpisodeMarkers()))
    }

    // ------------------------------------------------------------ contrato JSON

    @Test
    fun parseUnderstandsTheServerContract() {
        val markers = parseMarkers("""{"intro":{"s":44200,"e":82000},"credits":{"s":2527000}}""")
        assertTrue(markers.hasIntro)
        assertEquals(44_200L, markers.introStartMs)
        assertEquals(82_000L, markers.introEndMs)
        assertTrue(markers.hasCredits)
        assertEquals(2_527_000L, markers.creditsStartMs)
    }

    @Test
    fun parseToleratesNullsAndGarbage() {
        val markers = parseMarkers("""{"intro":{"s":null,"e":17000},"credits":null}""")
        assertEquals(-1L, markers.introStartMs)
        assertEquals(17_000L, markers.introEndMs)
        assertFalse(markers.hasCredits)
        assertTrue(parseMarkers("nope").isEmpty)
        assertTrue(parseMarkers(null).isEmpty)
        assertTrue(parseMarkers("").isEmpty)
        // Un «intro» sin fin útil no cuenta: el botón jamás saldría.
        assertTrue(parseMarkers("""{"intro":{"s":100}}""").isEmpty)
    }

    @Test
    fun playerJsonOmitsMissingSegmentsAndPreservesNumbers() {
        val json = toPlayerJson(EpisodeMarkers(introEndMs = 82_000L, creditsStartMs = 2_527_000L))
        val root = JSONObject(json)
        assertFalse(root.getJSONObject("intro").has("s"))
        assertEquals(82_000L, root.getJSONObject("intro").getLong("e"))
        assertEquals(2_527_000L, root.getJSONObject("credits").getLong("s"))
    }

    @Test
    fun jsonRoundTripKeepsTheSameWindow() {
        val original = EpisodeMarkers(90_000L, 150_000L, 2_527_000L)
        assertEquals(original, parseMarkers(toPlayerJson(original)))
    }

    @Test
    fun refsNormalizeToTheServerKey() {
        assertEquals("xtv:ABC", normalizeMarkersRef("ABC"))
        assertEquals("xtv:ABC", normalizeMarkersRef(" xtv:ABC "))
        assertEquals("magis:ABC", normalizeMarkersRef("magis:ABC"))
        assertEquals("", normalizeMarkersRef("   "))
    }
}
