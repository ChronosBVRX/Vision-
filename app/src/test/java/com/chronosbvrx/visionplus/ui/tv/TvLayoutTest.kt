package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El Inicio de la tele usa pósters grandes de portada (148 × 220 dp) y deja más aire
 * entre filas. La pantalla desplaza; ya no se encoge el póster para meter 3 filas.
 */
class TvLayoutTest {

    @Test
    fun thePosterIsLargeAndPremiumOnEveryTv() {
        listOf(480, 540, 600, 720, 800, 1080).forEach { height ->
            val metrics = tvRowMetrics(height)
            assertEquals("ancho del póster en $height dp", 148.dp, metrics.cardWidth)
            assertEquals("alto del póster en $height dp", 220.dp, metrics.cardHeight)
            assertEquals("aire superior en $height dp", 24.dp, metrics.topPadding)
        }
    }

    @Test
    fun oneFullRowFitsWithBreathingRoomOnA1080pTv() {
        val metrics = tvRowMetrics(540)
        assertTrue("540 dp debe usar la densidad compacta", metrics.compact)
        assertTrue(
            "una fila de ${metrics.rowHeight} no cabe en 540 dp",
            metrics.rowHeight.value <= 540f,
        )
        assertEquals(330.dp, metrics.rowHeight)
    }

    @Test
    fun tallScreensKeepTheSameLargePoster() {
        assertEquals(148.dp, tvRowMetrics(1080).cardWidth)
        assertEquals(148.dp, tvRowMetrics(720).cardWidth)
        // La proporción del póster se mantiene estable (148 × 220, 2:3 aproximado).
        listOf(480, 540, 720, 1080).forEach { height ->
            val metrics = tvRowMetrics(height)
            assertTrue(metrics.cardHeight.value / metrics.cardWidth.value in 1.45f..1.52f)
        }
    }

    @Test
    fun theRowHasMoreAirThanTheOldCompactLayout() {
        val compact = tvRowMetrics(540)
        assertTrue("la fila debe respirar: ${compact.topPadding}", compact.topPadding >= 24.dp)
        assertTrue("el título debe leerse: ${compact.titleHeight}", compact.titleHeight >= 40.dp)
    }
}
