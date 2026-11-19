package com.chronosbvrx.visionplus.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Los tamaños de Deportes se deciden por VENTANA: el teléfono no debe recibir
 * la rejilla de tablet ni la tablet la de teléfono. También fija el techo de 8
 * columnas para que una pantalla grande no llene la vista de tarjetas ilegibles.
 */
class SportsLayoutTest {

    @Test
    fun phonePortraitKeepsTwoReadableColumns() {
        val layout = sportsLayout(heightDp = 853, smallestWidthDp = 394, isLandscape = false)
        assertEquals(16f, layout.horizontalPadding, 0.001f)
        assertEquals(126f, layout.cardHeight, 0.001f)
        assertEquals(2, sportsGridColumns(394f, layout))
        // Un teléfono grande (500 dp) ya gana la tercera columna sin volver miniaturas.
        assertEquals(3, sportsGridColumns(500f, layout))
    }

    @Test
    fun phoneLandscapeTightensTheHeaderAndKeepsFiveColumns() {
        val layout = sportsLayout(heightDp = 394, smallestWidthDp = 394, isLandscape = true)
        assertEquals(4f, layout.headerVerticalPadding, 0.001f)
        assertEquals(5, sportsGridColumns(853f, layout))
    }

    @Test
    fun tabletPortraitGrowsMarginsAndCards() {
        val layout = sportsLayout(heightDp = 1280, smallestWidthDp = 800, isLandscape = false)
        assertEquals(24f, layout.horizontalPadding, 0.001f)
        assertEquals(170f, layout.gridMinCardWidth, 0.001f)
        assertEquals(3, sportsGridColumns(600f, layout))
        assertEquals(4, sportsGridColumns(800f, layout))
    }

    @Test
    fun tabletLandscapeUsesTheWidestCards() {
        val layout = sportsLayout(heightDp = 800, smallestWidthDp = 800, isLandscape = true)
        assertEquals(32f, layout.horizontalPadding, 0.001f)
        assertEquals(192f, layout.gridMinCardWidth, 0.001f)
        // 880 dp = 960 dp de ventana menos el riel de tablet.
        assertEquals(4, sportsGridColumns(880f, layout))
        // 1200 dp = 1280 dp de ventana menos el riel de tablet.
        assertEquals(5, sportsGridColumns(1200f, layout))
    }

    @Test
    fun hugeWindowsCapTheGridAtEightColumns() {
        val layout = sportsLayout(heightDp = 800, smallestWidthDp = 800, isLandscape = true)
        assertEquals(8, sportsGridColumns(6000f, layout))
    }
}
