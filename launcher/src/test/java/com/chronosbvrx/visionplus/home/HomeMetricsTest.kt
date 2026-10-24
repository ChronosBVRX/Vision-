package com.chronosbvrx.visionplus.home

import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.home.data.LauncherDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMetricsTest {

    @Test
    fun a1080pTvGetsThreeFullRowsOfBreathingRoom() {
        val metrics = homeMetrics(540)
        // El hero no debe comerse la pantalla: barra (62) + hero + una fila de póster + aire.
        assertTrue("hero demasiado alto para 540 dp", metrics.heroHeight.value <= 280f)
        assertTrue("hero demasiado bajo", metrics.heroHeight.value >= 200f)
        assertEquals(1.5f, metrics.posterHeight.value / metrics.posterWidth.value, 0.01f)
    }

    @Test
    fun tallScreensKeepTheLargePoster() {
        val metrics = homeMetrics(900)
        assertEquals(140.dp, metrics.posterWidth)
        assertEquals(340.dp, metrics.heroHeight)
        assertTrue(metrics.appTile >= 96.dp)
    }

    @Test
    fun compactTvsShrinkThePosterAndTheType() {
        val small = homeMetrics(420)
        val big = homeMetrics(800)
        assertTrue(small.posterWidth < big.posterWidth)
        assertTrue(small.titleSize < big.titleSize)
        assertTrue(small.horizontalPadding < big.horizontalPadding)
    }

    @Test
    fun theLiveCardReservesRoomForNameAndGuide() {
        assertTrue("la tarjeta compacta necesita nombre + AHORA", homeMetrics(540).channelHeight >= 100.dp)
        assertTrue("la tarjeta grande también", homeMetrics(900).channelHeight >= 120.dp)
    }

    @Test
    fun theDensityChangesTheWholeHomeWithoutBreakingTheTv() {
        val compact = homeMetrics(540, LauncherDensity.COMPACT)
        val comfort = homeMetrics(540, LauncherDensity.COMFORT)
        val cinema = homeMetrics(540, LauncherDensity.CINEMA)
        assertTrue(compact.posterWidth < comfort.posterWidth)
        assertTrue(comfort.posterWidth < cinema.posterWidth)
        assertTrue(compact.heroHeight <= comfort.heroHeight)
        assertTrue(comfort.heroHeight < cinema.heroHeight)
        assertTrue("la tarjeta nunca queda inservible", compact.channelHeight >= 92.dp)
        assertTrue("la tarjeta de cine tampoco crece sin control", cinema.channelHeight < 180.dp)
    }

    @Test
    fun absurdHeightsAreClampedInsteadOfOverflowing() {
        val tiny = homeMetrics(120)
        assertEquals(tiny, homeMetrics(360))
        val huge = homeMetrics(5000)
        assertEquals(huge, homeMetrics(1200))
    }

    @Test
    fun theHeroNeverEatsTheWholeScreenAtAnyRealHeight() {
        for (height in listOf(360, 400, 480, 540, 600, 720, 800, 1080)) {
            val metrics = homeMetrics(height)
            assertTrue(
                "hero a $height dp deja sin sitio a las filas",
                metrics.heroHeight.value <= height * 0.58f,
            )
        }
    }
}
