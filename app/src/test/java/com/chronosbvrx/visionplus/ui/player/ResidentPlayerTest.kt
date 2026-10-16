package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
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
}
