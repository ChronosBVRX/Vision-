package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El toque móvil del reproductor (regresión reportada en v2.5.81: «los controles ya no
 * aparecen» al tocar la pantalla):
 *
 *  - Mostrar SIEMPRE es inmediato (`SHOW_NOW`): el aviso no puede esperar al timeout del
 *    doble toque ni cancelarse cuando llega un segundo toque.
 *  - Ocultar espera el timeout (`HIDE_DELAYED`): el doble toque de ∓10 s no quita la barra.
 *  - Con modal, pre-roll o error el toque no toca los controles (`IGNORE`).
 */
class MobileTapActionTest {

    @Test
    fun tapWithControlsHiddenShowsThemImmediately() {
        assertEquals(
            MobileTapAction.SHOW_NOW,
            resolveMobileTapAction(controlsVisible = false, errorShown = false, picking = false, introVisible = false),
        )
    }

    @Test
    fun tapWithControlsVisibleHidesThemDelayed() {
        assertEquals(
            MobileTapAction.HIDE_DELAYED,
            resolveMobileTapAction(controlsVisible = true, errorShown = false, picking = false, introVisible = false),
        )
    }

    @Test
    fun errorModalAndIntroIgnoreTheTap() {
        assertEquals(
            MobileTapAction.IGNORE,
            resolveMobileTapAction(controlsVisible = false, errorShown = true, picking = false, introVisible = false),
        )
        assertEquals(
            MobileTapAction.IGNORE,
            resolveMobileTapAction(controlsVisible = true, errorShown = false, picking = true, introVisible = false),
        )
        assertEquals(
            MobileTapAction.IGNORE,
            resolveMobileTapAction(controlsVisible = false, errorShown = false, picking = false, introVisible = true),
        )
    }
}
