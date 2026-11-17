package com.chronosbvrx.visionplus.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El Hero usa una sola columna: 46 %/440 dp en Compact/Narrow y 50 %/560 dp en Full. */
class TvHeroLayoutTest {

    @Test
    fun compactUses46PercentWithA440Cap() {
        assertEquals(397.44f, tvHeroTextWidthDp(864f, full = false), 0.01f)
        assertEquals(287.04f, tvHeroTextWidthDp(624f, full = false), 0.01f)
        assertEquals(TV_HERO_TEXT_MAX_COMPACT_DP, tvHeroTextWidthDp(1920f, full = false), 0.01f)
    }

    @Test
    fun fullUses50PercentWithA560Cap() {
        assertEquals(432f, tvHeroTextWidthDp(864f, full = true), 0.01f)
        assertEquals(TV_HERO_TEXT_MAX_FULL_DP, tvHeroTextWidthDp(1184f, full = true), 0.01f)
        assertEquals(TV_HERO_TEXT_MAX_FULL_DP, tvHeroTextWidthDp(1920f, full = true), 0.01f)
    }

    @Test
    fun theTextNeverExceedsTheViewportOrGoesNegative() {
        listOf(0f, 320f, 480f, 720f, 960f, 1280f, 1920f).forEach { width ->
            listOf(false, true).forEach { full ->
                val text = tvHeroTextWidthDp(width, full)
                assertTrue("ancho negativo en $width/$full", text >= 0f)
                assertTrue("el texto no puede ser más ancho que el Hero", text <= width)
            }
        }
    }

    @Test
    fun compactIsAlwaysNarrowerThanFullForTheSameWidth() {
        listOf(640f, 720f, 864f, 1184f).forEach { width ->
            assertTrue(
                "Compact debe reservar menos ancho que Full en $width",
                tvHeroTextWidthDp(width, full = false) <= tvHeroTextWidthDp(width, full = true),
            )
        }
    }
}
