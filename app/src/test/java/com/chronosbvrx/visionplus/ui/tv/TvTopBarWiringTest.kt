package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes del cableado de la barra: el plan puro decide la variante, los hubs son modales
 * de verdad (foco verificado + contador para el rescate) y la tira conserva su auto-scroll.
 */
class TvTopBarWiringTest {

    @Test
    fun theBarReadsThePurePlanAndClampsTheChrome() {
        val bar = source("ui/tv/TvTopBar.kt")
        assertTrue("la barra no lee el plan", bar.contains("LocalTvTopBarPlan provides plan"))
        assertTrue("no usa el ancho real", bar.contains("BoxWithConstraints("))
        assertTrue("no hay auto-scroll de pestañas", bar.contains("rememberLazyListState()"))
        assertTrue("la tira no vuelve a la activa", bar.contains("tabListState.scrollToItem(activeIndex)"))
        assertTrue("las píldoras no fijan interlineado", bar.contains("lineHeight = 18.sp"))
    }

    @Test
    fun theHubsAreRealFocusModals() {
        val bar = source("ui/tv/TvTopBar.kt")
        assertTrue("falta el primer foco verificado", bar.contains("rememberDialogFocus()"))
        assertTrue("el panel no atrapa el foco", bar.contains("modalFocusTrap()"))
        assertTrue("el popup no cuenta para el rescate", bar.contains("FocusRescue.dialogsOpen++"))
        assertTrue("el popup no libera el contador", bar.contains("FocusRescue.dialogsOpen--"))
        assertTrue("no hay cierre con restauración", bar.contains("fun dismissHub("))
        assertTrue("no se reintenta el foco", bar.contains("anchor.requester.requestFocus()"))
        assertTrue("no se verifica el aterrizaje", bar.contains("anchor.focused"))
    }

    @Test
    fun thePlanKeepsTheSixTabsAndTheCompactAvatar() {
        val plan = source("ui/tv/TvTopBarPlan.kt")
        assertTrue("el chrome no se recorta", plan.contains("coerceIn(TV_TOP_BAR_MIN_FONT_SCALE, TV_TOP_BAR_MAX_FONT_SCALE)"))
        assertTrue("Compact no fuerza avatar", plan.contains("TvTopBarProfileMode.AVATAR"))
        assertTrue("falta el tope del nombre", plan.contains("PROFILE_LABEL_MAX_DP"))
        assertTrue("falta el mínimo de la tira", plan.contains("TV_TOP_BAR_MIN_TAB_STRIP_DP"))
    }

    private fun source(rel: String): String {
        val found = listOf(
            File("src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("app/src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("../app/src/main/java/com/chronosbvrx/visionplus/$rel"),
        ).firstOrNull { it.exists() }
        assertTrue("fuente no encontrada: $rel", found != null && found.isFile)
        return found!!.readText()
    }
}
