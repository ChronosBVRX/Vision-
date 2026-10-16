package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La política del toque móvil del reproductor (regresión reportada en v2.5.81/82: «los
 * controles ya no aparecen» al tocar la pantalla):
 *
 *  - Mostrar SIEMPRE es inmediato (`SHOW_NOW`): el aviso no puede esperar al timeout del
 *    doble toque ni cancelarse cuando llega un segundo toque.
 *  - Ocultar espera el timeout (`HIDE_DELAYED`): el doble toque de ∓10 s no quita la barra.
 *  - Con modal, pre-roll o error (`blocked`) el toque no toca los controles (`IGNORE`).
 */
class MobileTapActionTest {

    @Test
    fun tapWithControlsHiddenShowsThemImmediately() {
        assertEquals(
            MobileTapAction.SHOW_NOW,
            resolveMobileTapAction(controlsVisible = false, blocked = false),
        )
    }

    @Test
    fun tapWithControlsVisibleHidesThemDelayed() {
        assertEquals(
            MobileTapAction.HIDE_DELAYED,
            resolveMobileTapAction(controlsVisible = true, blocked = false),
        )
    }

    @Test
    fun blockedTapIsIgnored() {
        assertEquals(
            MobileTapAction.IGNORE,
            resolveMobileTapAction(controlsVisible = false, blocked = true),
        )
        assertEquals(
            MobileTapAction.IGNORE,
            resolveMobileTapAction(controlsVisible = true, blocked = true),
        )
    }
}
