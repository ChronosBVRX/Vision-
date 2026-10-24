package com.chronosbvrx.visionplus.data

import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La TCL BeyondTV (Android 9, 2 GB) reportó `cacheMs=12436` y `portalMs=31042` en la misma
 * sesión en la que el reproductor tardó 16.5 s en el primer cuadro: nuestro trabajo de fondo
 * (índice de 5.8 MB + filas del portal) le estaba quitando la CPU al motor.
 *
 * Dos remedios: el índice de TV baja de 100 a 24 títulos por fila (~1/4 de JSON) y el trabajo
 * pesado espera si acaba de arrancar una reproducción.
 */
class PlaybackFocusTest {

    // ----- ventana de silencio -----

    @Test
    fun nothingHasBeenLaunchedMeansNoWait() {
        assertFalse(recentlyLaunchedPlay(0L, 1_000L, 20_000L))
    }

    @Test
    fun aFreshLaunchKeepsTheBackgroundWorkWaiting() {
        assertTrue(recentlyLaunchedPlay(1_000L, 1_001L, 20_000L))
        assertTrue(recentlyLaunchedPlay(1_000L, 20_999L, 20_000L))
    }

    @Test
    fun onceTheWindowPassesTheCatalogCanWorkAgain() {
        assertFalse(recentlyLaunchedPlay(1_000L, 21_000L, 20_000L))
        assertFalse(recentlyLaunchedPlay(1_000L, 60_000L, 20_000L))
    }

    @Test
    fun aClockGoingBackwardsNeverBlocksForever() {
        assertFalse(recentlyLaunchedPlay(5_000L, 1_000L, 20_000L))
    }

    @Test
    fun theRealClockIsWired() {
        PlaybackFocus.resetForTests()
        var now = 1_000L
        PlaybackFocus.clock = { now }
        assertFalse("sin reproducción no debe esperar", PlaybackFocus.busy())
        PlaybackFocus.launched()
        assertTrue("justo después de lanzar, sí", PlaybackFocus.busy())
        now += PlaybackFocus.QUIET_MS
        assertFalse("pasada la ventana, el catálogo puede trabajar", PlaybackFocus.busy())
        PlaybackFocus.resetForTests()
    }

    // ----- índice ligero en TV -----

    @Test
    fun televisionsAndLowEndBoxesGetTheLightIndex() {
        assertEquals(24, RemoteIndexSource.filmsPerCategoryFor(isTelevision = true, lowEnd = false))
        assertEquals(24, RemoteIndexSource.filmsPerCategoryFor(isTelevision = false, lowEnd = true))
        assertEquals(24, RemoteIndexSource.filmsPerCategoryFor(isTelevision = true, lowEnd = true))
        // Un teléfono con recursos sigue trayendo el índice completo.
        assertEquals(100, RemoteIndexSource.filmsPerCategoryFor(isTelevision = false, lowEnd = false))
    }

    // ----- cableado -----

    @Test
    fun thePlayerLaunchMarksTheFocus() {
        val api = File("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt").readText()
        val launch = api.substringAfter("private suspend fun launch(")
        assertTrue("el lanzador no marca la reproducción", launch.contains("PlaybackFocus.launched()"))
        // Se marca antes de construir/enviar el intent, no después.
        assertTrue(launch.indexOf("PlaybackFocus.launched()") < launch.indexOf("context.startActivity(intent)"))
    }

    @Test
    fun theCatalogWaitsForThePlayer() {
        val catalog = File("src/main/java/com/chronosbvrx/visionplus/data/XuperCatalog.kt").readText()
        assertTrue("falta el guardián de silencio", catalog.contains("private suspend fun awaitPlaybackIdle()"))
        // Las tres fases pesadas lo consultan: índice en caché, filas del portal y la sync remota.
        assertTrue(catalog.contains("private suspend fun cachedIndexRows() {\n        awaitPlaybackIdle()"))
        assertTrue(catalog.contains("awaitPlaybackIdle()\n            val started"))
        assertTrue(catalog.contains("kotlinx.coroutines.delay(3_500L)\n                awaitPlaybackIdle()"))
    }

    @Test
    fun theIndexOfATelevisionIsSmallerAndRefetchedOnce() {
        val source = File("src/main/java/com/chronosbvrx/visionplus/data/sources/RemoteIndexSource.kt").readText()
        assertTrue("sin límite por equipo", source.contains("private fun effectiveLimit(context: Context)"))
        assertTrue("la URL no usa el límite efectivo", source.contains("limitFilms=\$limit&since=\$since"))
        assertTrue("no se recuerda la medida guardada", source.contains("private const val K_LIMIT"))
        // Cambió la medida (o se actualizó la app): se pide completo para no quedarse con 100/fila.
        assertTrue("sin refetch al cambiar la medida", source.contains("if (storedLimit == limit) prefs.getInt(K_VERSION, 0) else 0"))
    }
}
