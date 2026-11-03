package com.chronosbvrx.visionplus.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * El viaje al reproductor y la vuelta: al salir de una transmisión, la pantalla tiene que
 * recordar la SELECCIÓN (deporte, día, liga, pestaña) y el enlace que se estaba viendo.
 * `DeportesReturn` es esa memoria de proceso; si se limpiara o no se consumiera una sola
 * vez, el menú volvería al principio y el usuario perdería el punto donde estaba.
 */
class DeportesReturnTest {

    @Before
    fun reset() {
        DeportesReturn.resetForTests()
    }

    @Test
    fun theWatchedEventIsConsumedOnceAndTheSignalStaysForTheModal() {
        DeportesReturn.rememberWatching("espn:123", "https://cdn.test/tudn.m3u8")
        assertEquals("espn:123", DeportesReturn.consumeWatchingEvent())
        // La pantalla ya lo consumió: una recomposición no debe reabrir el modal otra vez.
        assertNull(DeportesReturn.consumeWatchingEvent())
        // El enlace sigue disponible para marcar la opción «Seleccionada» en el modal.
        assertEquals("https://cdn.test/tudn.m3u8", DeportesReturn.watchingUrl)
    }

    @Test
    fun zappingInsideThePlayerUpdatesTheMarkedSignal() {
        DeportesReturn.rememberWatching("espn:9", "https://cdn.test/uno.m3u8")
        DeportesReturn.consumeWatchingEvent()
        DeportesReturn.updateWatchingUrl("https://cdn.test/dos.m3u8")
        assertEquals("https://cdn.test/dos.m3u8", DeportesReturn.watchingUrl)
        // Sin un partido recordado, un cambio suelto no inventa una selección.
        DeportesReturn.forgetWatching()
        DeportesReturn.updateWatchingUrl("https://cdn.test/tres.m3u8")
        assertNull(DeportesReturn.watchingUrl)
    }

    @Test
    fun closingTheModalForgetsTheSelection() {
        DeportesReturn.rememberWatching("espn:1", "https://cdn.test/a.m3u8")
        DeportesReturn.pickingId = "espn:1"
        DeportesReturn.forgetWatching()
        assertNull(DeportesReturn.watchingUrl)
        assertNull(DeportesReturn.consumeWatchingEvent())
        // El partido abierto también se limpia al cerrar (lo hace el onDismiss).
        DeportesReturn.pickingId = null
        assertNull(DeportesReturn.pickingId)
    }

    @Test
    fun aDayThatAlreadyPassedFallsBackToToday() {
        DeportesReturn.day = "2026-10-01"
        assertEquals("2026-10-26", DeportesReturn.validDay(today = "2026-10-26"))
    }

    @Test
    fun todayAndTomorrowAreRespectedAndLaterDaysClampToTomorrow() {
        DeportesReturn.day = "2026-10-26"
        assertEquals("2026-10-26", DeportesReturn.validDay(today = "2026-10-26"))
        DeportesReturn.day = "2026-10-27"
        assertEquals("2026-10-27", DeportesReturn.validDay(today = "2026-10-26"))
        // La agenda ya solo muestra hoy y mañana: un día viejo del proceso se recorta.
        DeportesReturn.day = "2026-10-29"
        assertEquals("2026-10-27", DeportesReturn.validDay(today = "2026-10-26"))
        DeportesReturn.day = null
        assertNull(DeportesReturn.validDay(today = "2026-10-26"))
    }

    @Test
    fun theScreenSelectionIsRememberedAcrossThePlayer() {
        DeportesReturn.category = "Basketball"
        DeportesReturn.categoryChosen = true
        DeportesReturn.day = "2026-10-27"
        DeportesReturn.league = "Liga MX"
        DeportesReturn.channels = false
        assertEquals("Basketball", DeportesReturn.category)
        assertEquals("2026-10-27", DeportesReturn.day)
        assertEquals("Liga MX", DeportesReturn.league)
        assertEquals(false, DeportesReturn.channels)
    }
}
