package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matriz de la barra superior: el caso 960×540 dp (720p/1080p típicas) debe conservar las
 * 6 pestañas aun con escalado 1.5×; las variantes solo cambian etiquetas/reloj/perfil, nunca
 * la navegación primaria. El chrome se recorta a 1.5× y el contenido conserva su escala.
 */
class TvTopBarPlanTest {

    @Test
    fun fullTvKeepsEveryLabelAndTheClock() {
        val plan = tvTopBarPlan(widthDp = 1280f, fontScale = 1f, profileNameLength = 9)
        assertEquals(TvTopBarTier.FULL, plan.tier)
        assertEquals(TvTopBarBrandMode.FULL, plan.brand)
        assertTrue("Full conserva el reloj", plan.showClock)
        assertEquals(TvTopBarProfileMode.NAME, plan.profile)
        assertEquals(TvTopBarSearchMode.PILL, plan.search)
        assertEquals(64.dp, plan.height)
        assertEquals(48.dp, plan.horizontalPadding)
        assertEquals(120.dp, plan.profileLabelMaxWidth)
        assertTrue("las 6 pestañas deben caber", plan.allTabsFit)
    }

    @Test
    fun compactTvHidesClockAndUsesAvatarToProtectTheTabs() {
        val plan = tvTopBarPlan(widthDp = 960f, fontScale = 1f, profileNameLength = 9)
        assertEquals(TvTopBarTier.COMPACT, plan.tier)
        assertEquals(TvTopBarBrandMode.SHORT, plan.brand)
        assertFalse("Compact no pinta el reloj", plan.showClock)
        assertEquals("Compact usa solo avatar", TvTopBarProfileMode.AVATAR, plan.profile)
        assertEquals(TvTopBarSearchMode.ICON, plan.search)
        assertEquals(0.dp, plan.profileLabelMaxWidth)
        assertEquals(60.dp, plan.height)
        assertTrue("las 6 pestañas deben caber a 1.0×", plan.allTabsFit)
    }

    @Test
    fun compactTvKeepsAllSixTabsAtTheMaximumChromeScale() {
        val plan = tvTopBarPlan(widthDp = 960f, fontScale = 1.5f, profileNameLength = 9)
        assertEquals(TvTopBarTier.COMPACT, plan.tier)
        assertEquals(1.5f, plan.fontScale)
        assertTrue("960 dp a 1.5× debe conservar las 6", plan.allTabsFit)
        assertTrue("y dejar margen sobre el mínimo", plan.tabStripWidth >= 240f)
    }

    @Test
    fun theChromeScaleIsClampedButThePlanStaysStable() {
        val atMax = tvTopBarPlan(widthDp = 960f, fontScale = 1.5f, profileNameLength = 9)
        val aboveMax = tvTopBarPlan(widthDp = 960f, fontScale = 2f, profileNameLength = 9)
        assertEquals(1.5f, aboveMax.fontScale)
        assertEquals(atMax, aboveMax)
    }

    @Test
    fun narrowTvKeepsTheTabStripAboveTheMinimumAt720() {
        val plan = tvTopBarPlan(widthDp = 720f, fontScale = 1.5f, profileNameLength = 9)
        assertEquals(TvTopBarTier.NARROW, plan.tier)
        assertEquals(56.dp, plan.height)
        assertEquals(40.dp, plan.horizontalPadding)
        assertEquals(40.dp, plan.controlHeight)
        assertFalse("a 720 dp las 6 no caben completas", plan.allTabsFit)
        assertTrue("pero la tira conserva su mínimo", plan.minTabStripMet)
        assertTrue(plan.tabStripWidth >= TV_TOP_BAR_MIN_TAB_STRIP_DP)
    }

    @Test
    fun fullCapsALongProfileNameInsteadOfStealingTheTabs() {
        val plan = tvTopBarPlan(widthDp = 1280f, fontScale = 1.5f, profileNameLength = 40)
        assertEquals(TvTopBarTier.FULL, plan.tier)
        assertEquals(TvTopBarProfileMode.NAME, plan.profile)
        assertEquals(120.dp, plan.profileLabelMaxWidth)
        assertTrue("el nombre largo no puede dejar la tira sin mínimo", plan.minTabStripMet)
    }

    @Test
    fun compactAlwaysUsesAvatarEvenWithAShortName() {
        val plan = tvTopBarPlan(widthDp = 960f, fontScale = 1f, profileNameLength = 4)
        assertEquals(TvTopBarProfileMode.AVATAR, plan.profile)
        assertEquals(0.dp, plan.profileLabelMaxWidth)
    }

    @Test
    fun badgeDoesNotCollapseTheTabStripAtTheLimit() {
        val plan = tvTopBarPlan(widthDp = 960f, fontScale = 1.5f, profileNameLength = 9, hasBadge = true)
        assertTrue(plan.minTabStripMet)
        assertTrue("el badge no debe empujar las pestañas fuera", plan.allTabsFit)
    }

    @Test
    fun theWholeMatrixKeepsAUsableTabStrip() {
        val widths = listOf(720f, 840f, 960f, 1280f)
        val scales = listOf(1f, 1.15f, 1.3f, 1.5f, 2f)
        widths.forEach { width ->
            scales.forEach { scale ->
                val plan = tvTopBarPlan(width, scale, profileNameLength = 12)
                assertTrue(
                    "ancho=$width scale=$scale dejó la tira en ${plan.tabStripWidth}",
                    plan.tabStripWidth >= TV_TOP_BAR_MIN_TAB_STRIP_DP,
                )
                assertTrue("targets de 40 dp o más", plan.controlHeight.value >= 40f)
                assertTrue("iconos de 40 dp o más", plan.iconButtonSize.value >= 40f)
                assertTrue("alto razonable", plan.height.value in 56f..64f)
            }
        }
    }

    @Test
    fun theActiveEntryIsTheLandingTarget() {
        assertEquals(0, tvTopEntryIndex(null))
        assertEquals(0, tvTopEntryIndex(TvTopTab.HOME))
        assertEquals(1, tvTopEntryIndex(TvTopTab.LIVE))
        assertEquals(1, tvTopEntryIndex(TvTopTab.SPORTS))
        assertEquals(2, tvTopEntryIndex(TvTopTab.GAMES))
        assertEquals(3, tvTopEntryIndex(TvTopTab.MOVIES))
        assertEquals(4, tvTopEntryIndex(TvTopTab.SERIES))
        assertEquals(5, tvTopEntryIndex(TvTopTab.LIBRARY))
    }

    @Test
    fun theLiveHubCoversLiveAndSportsAndTheLibraryHubCoversLibrary() {
        assertTrue(tvTopEntryActive(TvTopEntry.LiveHub, TvTopTab.LIVE))
        assertTrue(tvTopEntryActive(TvTopEntry.LiveHub, TvTopTab.SPORTS))
        assertFalse(tvTopEntryActive(TvTopEntry.LiveHub, TvTopTab.HOME))
        assertTrue(tvTopEntryActive(TvTopEntry.LibraryHub, TvTopTab.LIBRARY))
        assertTrue(tvTopEntryActive(TvTopEntry.MoviesHub, TvTopTab.MOVIES))
        assertTrue(tvTopEntryActive(TvTopEntry.SeriesHub, TvTopTab.SERIES))
    }

    @Test
    fun everyEntryHasAStableKey() {
        val keys = TV_TOP_ENTRIES.map(::tvTopEntryKey)
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.all { it.startsWith("tab:") || it.startsWith("hub:") })
    }
}
