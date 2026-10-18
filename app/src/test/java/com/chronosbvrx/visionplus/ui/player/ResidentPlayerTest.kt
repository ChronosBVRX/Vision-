package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Camino B» (reproductor residente): política de salida. Al salir normalmente la actividad
 * se queda viva detrás con su vista del motor —la próxima reproducción reutiliza la MISMA
 * vista (stop → play in-place) sin arranque en frío—; solo el auto-reparado (motor rotado a
 * una generación nueva) pide cerrar y reiniciar el proceso para estrenar la carpeta limpia.
 */
class ResidentPlayerTest {

    @Test
    fun exitActionKeepsTheResidentPlayerAliveByDefault() {
        assertEquals(PlayerExitAction.HIDE_RESIDENT, exitActionFor(hardReset = false))
    }

    @Test
    fun exitActionFinishesAndRelaunchesOnlyForTheSelfHealReset() {
        assertEquals(PlayerExitAction.FINISH_AND_RELAUNCH, exitActionFor(hardReset = true))
    }

    @Test
    fun exitingAndIdleMustFinishTheTaskInsteadOfStayingBlack() {
        // El caso reportado: el residente vuelve al frente sin reproducción y Atrás
        // era un no-op. Debe cerrar la tarea.
        assertTrue(shouldFinishIdleResidentOnExit(exiting = true, isPlayerActive = false))
    }

    @Test
    fun exitingWhilePlayingDoesNotForceFinish() {
        assertFalse(shouldFinishIdleResidentOnExit(exiting = true, isPlayerActive = true))
    }

    @Test
    fun firstExitIsNeverForcedToFinish() {
        assertFalse(shouldFinishIdleResidentOnExit(exiting = false, isPlayerActive = false))
        assertFalse(shouldFinishIdleResidentOnExit(exiting = false, isPlayerActive = true))
    }

    @Test
    fun playbackParksWhenThePlayerGoesBehindWhilePlaying() {
        // El caso reportado: suena la reproducción de fondo mientras el menú está al frente.
        // Si la actividad quedó detrás con reproducción viva, el motor se suelta.
        assertTrue(shouldParkPlaybackOnStop(isPlayerActive = true, exiting = false))
    }

    @Test
    fun aCleanResidentExitDoesNotParkTwice() {
        // Salida normal: exitPlayer ya detuvo el motor; el onStop posterior no repite trabajo.
        assertFalse(shouldParkPlaybackOnStop(isPlayerActive = true, exiting = true))
        assertFalse(shouldParkPlaybackOnStop(isPlayerActive = false, exiting = false))
        assertFalse(shouldParkPlaybackOnStop(isPlayerActive = false, exiting = true))
    }
}
