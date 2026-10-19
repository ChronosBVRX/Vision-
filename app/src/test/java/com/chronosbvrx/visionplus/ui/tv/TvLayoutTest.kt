package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El Inicio de la tele tiene que dejar ver al menos 3 filas del catálogo. Las medidas salen de la
 * altura real de la pantalla (una TV 1080p a 320 dpi reporta 540 dp), así que estas pruebas fijan
 * el contrato sin componer.
 */
class TvLayoutTest {

    @Test
    fun threeRowsFitOnA1080pTv() {
        val metrics = tvRowMetrics(540)
        assertTrue("540 dp debe usar la densidad compacta", metrics.compact)
        assertTrue(
            "3 filas de ${metrics.rowHeight} no caben en 540 dp",
            metrics.rowHeight.value * TV_VISIBLE_CATALOG_ROWS <= 540f,
        )
        // El póster se achica, pero no a un tamaño de juguete.
        assertTrue("póster demasiado chico: ${metrics.cardWidth}", metrics.cardWidth >= 60.dp)
        assertTrue("póster demasiado grande: ${metrics.cardWidth}", metrics.cardWidth <= 140.dp)
    }

    @Test
    fun everyTvHeightKeepsThreeCompleteRows() {
        listOf(480, 540, 600, 720, 800, 1080).forEach { height ->
            val metrics = tvRowMetrics(height)
            assertTrue(
                "en $height dp solo caben ${height / metrics.rowHeight.value} filas",
                metrics.rowHeight.value * TV_VISIBLE_CATALOG_ROWS <= height,
            )
        }
    }

    @Test
    fun tallScreensKeepTheFullPosterAndShortOnesShrinkIt() {
        assertEquals(140.dp, tvRowMetrics(1080).cardWidth)
        assertTrue(tvRowMetrics(720).cardWidth > tvRowMetrics(540).cardWidth)
        // La proporción del póster nunca cambia (2:3).
        listOf(480, 540, 720, 1080).forEach { height ->
            val metrics = tvRowMetrics(height)
            assertEquals(metrics.cardWidth.value * 1.5f, metrics.cardHeight.value, 0.01f)
        }
    }

    @Test
    fun theCompactRowIsShorterThanTheOldFixedRow() {
        // Antes: 18 de margen + 26 de etiqueta + 210 de póster + 6 + 20 de título + 10 = 290 dp.
        val compact = tvRowMetrics(540)
        assertTrue("la fila compacta debe bajar de 290 dp", compact.rowHeight.value < 290f)
        // Y en una pantalla alta conserva el tamaño de siempre.
        val tall = tvRowMetrics(1080)
        assertEquals(140.dp, tall.cardWidth)
        assertTrue(tall.rowHeight.value > compact.rowHeight.value)
    }
}
