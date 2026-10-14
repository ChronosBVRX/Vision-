package com.chronosbvrx.visionplus.data.markers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardianes de los marcadores de salto: la ventana del botón «Omitir intro», el disparo
 * de la tarjeta de créditos, la fusión de capas (lo aprendido manda) y el contrato JSON
 * con el servidor y con los extras del reproductor.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
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

    // ------------------------------------------------------------ fusión de capas

    @Test
    fun learnedMarkersOverrideGlobalOnes() {
        val global = EpisodeMarkers(90_000L, 150_000L, 2_527_000L)
        val learned = EpisodeMarkers(-1L, 120_000L, -1L)
        val merged = mergeMarkers(global, learned)
        assertEquals(120_000L, merged.introEndMs)
        assertEquals(-1L, merged.introStartMs)              // la corrección manda completa
        assertEquals(2_527_000L, merged.creditsStartMs)     // lo no tocado se conserva
    }

    @Test
    fun refsNormalizeToTheServerKey() {
        assertEquals("xtv:ABC", normalizeMarkersRef("ABC"))
        assertEquals("xtv:ABC", normalizeMarkersRef(" xtv:ABC "))
        assertEquals("magis:ABC", normalizeMarkersRef("magis:ABC"))
        assertEquals("", normalizeMarkersRef("   "))
    }

    // ------------------------------------------------------------ almacén aprendido

    @Test
    fun learnedMarksPersistAndClearPerSeason() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val ref = "xtv:T1ABC"
        MarkersStore.clearLocal(context, ref, null)
        assertTrue(MarkersStore.localMarkers(context, ref).isEmpty)

        MarkersStore.saveLocal(context, ref, "intro", -1L, 82_000L)
        MarkersStore.saveLocal(context, ref, "credits", 2_527_000L, -1L)
        val saved = MarkersStore.localMarkers(context, ref)
        assertEquals(82_000L, saved.introEndMs)
        assertEquals(2_527_000L, saved.creditsStartMs)

        MarkersStore.clearLocal(context, ref, "credits")
        val afterClear = MarkersStore.localMarkers(context, ref)
        assertEquals(82_000L, afterClear.introEndMs)
        assertFalse(afterClear.hasCredits)

        MarkersStore.clearLocal(context, ref, null)
        assertTrue(MarkersStore.localMarkers(context, ref).isEmpty)
    }

    @Test
    fun bareIdsLandOnTheSameSeasonRowKey() {
        val context: Context = ApplicationProvider.getApplicationContext()
        MarkersStore.clearLocal(context, "ABC", null)
        MarkersStore.saveLocal(context, "ABC", "intro", -1L, 60_000L)
        assertEquals(60_000L, MarkersStore.localMarkers(context, "xtv:ABC").introEndMs)
        MarkersStore.clearLocal(context, "xtv:ABC", null)
    }

    @Test
    fun learnedMarksLiveInAFileNotSharedPreferences() {
        // El reproductor (:x4k) escribe y el proceso principal lee: archivo compartido,
        // jamás prefs cacheadas por proceso (la lección del historial).
        val context: Context = ApplicationProvider.getApplicationContext()
        MarkersStore.clearLocal(context, "xtv:FILE", null)
        MarkersStore.saveLocal(context, "xtv:FILE", "intro", -1L, 30_000L)
        val file = java.io.File(context.filesDir, "skip_markers.json")
        assertTrue(file.exists())
        assertTrue(file.readText().contains("xtv:FILE"))
        MarkersStore.clearLocal(context, "xtv:FILE", null)
    }
}
