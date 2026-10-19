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
        val original = EpisodeMarkers(
            introStartMs = 90_000L, introEndMs = 150_000L,
            recapStartMs = 5_000L, recapEndMs = 60_000L,
            creditsStartMs = 2_527_000L, postCreditsStartMs = 2_640_000L,
        )
        assertEquals(original, parseMarkers(toPlayerJson(original)))
    }

    @Test
    fun recapWindowMirrorsTheIntroRules() {
        val markers = EpisodeMarkers(recapStartMs = 1_000L, recapEndMs = 64_000L)
        assertTrue(inRecapWindow(30_000L, markers))
        assertFalse(inRecapWindow(63_000L, markers))
        assertFalse(inRecapWindow(5_000L, EpisodeMarkers()))
        assertTrue(parseMarkers("""{"recap":{"s":1000,"e":64000}}""").hasRecap)
        assertTrue(parseMarkers("""{"recap":{"e":64000}}""").hasRecap)
    }

    @Test
    fun postCreditsSurvivesThePlayerJson() {
        val markers = parseMarkers("""{"postCredits":{"s":2600000}}""")
        assertTrue(markers.hasPostCredits)
        assertEquals(2_600_000L, markers.postCreditsStartMs)
        assertFalse(parseMarkers("""{"postCredits":{"s":0}}""").hasPostCredits)
    }

    @Test
    fun refsNormalizeToTheServerKey() {
        assertEquals("xtv:ABC", normalizeMarkersRef("ABC"))
        assertEquals("xtv:ABC", normalizeMarkersRef(" xtv:ABC "))
        assertEquals("magis:ABC", normalizeMarkersRef("magis:ABC"))
        assertEquals("xtv:ABC", normalizeMarkersRef("xtv:xtv:ABC"))
        assertEquals("", normalizeMarkersRef("   "))
    }

    // ------------------------------------------------------------ modo automático

    @Test
    fun autoSkipNeverInventsAnIntroStart() {
        val onlyEnd = EpisodeMarkers(introStartMs = -1L, introEndMs = 90_000L)
        assertNull(autoIntroWindow(onlyEnd, 44 * 60_000L))
        assertNull(autoSkipTarget(onlyEnd, 10_000L, 44 * 60_000L, playing = true))

        val known = EpisodeMarkers(introStartMs = 60_000L, introEndMs = 90_000L)
        assertEquals(60_000L..89_999L, autoIntroWindow(known, 44 * 60_000L))
    }

    @Test
    fun automaticSkipUsesEachEpisodesOwnWindow() {
        // AoT T2: E1 3:29-4:59 y E2 1:26-2:56. Reutilizar E1 cortaba 90 s de E2.
        val e1 = EpisodeMarkers(introStartMs = 209_391L, introEndMs = 299_391L)
        val e2 = EpisodeMarkers(introStartMs = 86_061L, introEndMs = 176_061L)
        val duration = 24 * 60_000L
        assertEquals("intro", autoSkipTarget(e1, 210_000L, duration, true)?.kind)
        assertNull(autoSkipTarget(e1, 90_000L, duration, true))
        assertEquals("intro", autoSkipTarget(e2, 90_000L, duration, true)?.kind)
        assertNull(autoSkipTarget(e2, 210_000L, duration, true))
    }

    @Test
    fun lateMarkersDoNotCutContent() {
        val markers = EpisodeMarkers(introStartMs = 60_000L, introEndMs = 150_000L)
        // Llegó el plan cuando el usuario ya llevaba 40 s dentro del intro: no cortar.
        assertNull(autoSkipTarget(markers, 100_000L, 44 * 60_000L, playing = true, lateGraceMs = 15_000L))
        // Recién entrando (5 s): sí.
        assertEquals("intro", autoSkipTarget(markers, 65_000L, 44 * 60_000L, true)?.kind)
    }

    @Test
    fun automaticSkipWaitsWhilePaused() {
        val markers = EpisodeMarkers(introStartMs = 60_000L, introEndMs = 150_000L)
        assertNull(autoSkipTarget(markers, 65_000L, 44 * 60_000L, playing = false))
    }

    @Test
    fun eachKindIsAutoSkippedOnlyOnce() {
        assertTrue(shouldApplyAutoSkip("recap", recapSkipped = false, introSkipped = false))
        assertFalse(shouldApplyAutoSkip("recap", recapSkipped = true, introSkipped = false))
        assertTrue(shouldApplyAutoSkip("intro", recapSkipped = true, introSkipped = false))
        assertFalse(shouldApplyAutoSkip("intro", recapSkipped = false, introSkipped = true))
        assertFalse(shouldApplyAutoSkip("otro", recapSkipped = false, introSkipped = false))
    }

    @Test
    fun creditsMustFitTheRealDuration() {
        val early = EpisodeMarkers(creditsStartMs = 300_000L)
        assertNull(creditsCardStartMs(early, 44 * 60_000L))
        val good = EpisodeMarkers(creditsStartMs = 2_289_000L, creditsEndMs = 2_582_000L)
        assertEquals(2_289_000L, creditsCardStartMs(good, 44 * 60_000L))
        assertEquals(2_582_000L, creditsAutoAtMs(good, 44 * 60_000L))
    }

    @Test
    fun postCreditsAreRecognizedAndBlockTheFastAdvance() {
        val markers = EpisodeMarkers(
            creditsStartMs = 2_289_000L,
            creditsEndMs = 2_582_000L,
            postCreditsStartMs = 2_600_000L,
            postCreditsEndMs = 2_655_000L,
        )
        assertTrue(validPostCredits(markers, 44 * 60_000L))
        // La tarjeta puede salir, pero el avance rápido debe esperar: la política
        // del reproductor lo consulta antes de arrancar.
        assertTrue(markers.hasPostCredits)
    }

    @Test
    fun skipPillWaitsForTheVideoToBeDrawn() {
        assertFalse(canShowSkipPill(true, true, videoReady = false, blocked = false))
        assertTrue(canShowSkipPill(true, true, videoReady = true, blocked = false))
        assertFalse(canShowSkipPill(true, false, videoReady = true, blocked = false))
        assertFalse(canShowSkipPill(true, true, videoReady = true, blocked = true))
    }

    @Test
    fun serverEndsSurviveThePlayerContract() {
        val markers = parseMarkers(
            """{"credits":{"s":2289000,"e":2582000,"src":"introdb"},""" +
                """"postCredits":{"s":2600000,"e":2655000,"src":"theintrodb"}}""",
        )
        assertEquals(2_582_000L, markers.creditsEndMs)
        assertEquals(2_655_000L, markers.postCreditsEndMs)
        assertEquals("introdb", markers.creditsSource)
        val json = toPlayerJson(markers)
        assertEquals(2_582_000L, parseMarkers(json).creditsEndMs)
    }

    @Test
    fun cacheNamesAreStableAndDifferentPerEpisode() {
        val first = markersCacheName("xtv:ABC", 1)
        assertEquals(first, markersCacheName("xtv:ABC", 1))
        assertFalse(first == markersCacheName("xtv:ABC", 2))
        assertTrue(first.endsWith(".json"))
    }
}
