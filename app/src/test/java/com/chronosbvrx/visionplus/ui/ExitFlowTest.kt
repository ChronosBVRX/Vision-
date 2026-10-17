package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La política del cierre con confirmación («dos veces Atrás») del menú principal: el primer
 * toque arma la salida y muestra el aviso; el segundo, dentro de la ventana, cierra. Jamás
 * cierra sin aviso fresco (pasada la ventana, el siguiente toque vuelve a armar).
 */
class ExitFlowTest {

    @Test
    fun firstBackArmsAndShowsTheHint() {
        val guard = BackExitGuard(windowMs = 2_500L)
        assertFalse("el primer Atrás no cierra", guard.press(1_000L))
        assertTrue("el aviso queda vigente", guard.hintVisible(1_000L))
        assertTrue("el aviso sigue vigente dentro de la ventana", guard.hintVisible(3_499L))
    }

    @Test
    fun secondBackWithinTheWindowCloses() {
        val guard = BackExitGuard(windowMs = 2_500L)
        assertFalse(guard.press(1_000L))
        assertTrue("el segundo Atrás en ventana cierra", guard.press(2_000L))
        assertFalse("tras cerrar, el aviso se apaga", guard.hintVisible(2_000L))
    }

    @Test
    fun secondBackAfterTheWindowJustReArms() {
        val guard = BackExitGuard(windowMs = 2_500L)
        assertFalse(guard.press(1_000L))
        assertFalse("fuera de la ventana el toque vuelve a armar, no cierra", guard.press(4_000L))
        assertTrue("el aviso fresco vuelve a estar vigente", guard.hintVisible(4_000L))
        assertTrue("y el siguiente dentro de la nueva ventana sí cierra", guard.press(5_000L))
    }

    @Test
    fun confirmedExitLeavesACleanState() {
        val guard = BackExitGuard(windowMs = 1_000L)
        assertFalse(guard.press(0L))
        assertTrue(guard.press(500L))
        // Tras confirmar, un estado limpio vuelve a pedir dos toques.
        assertFalse(guard.press(600L))
        assertTrue(guard.hintVisible(600L))
    }
}
