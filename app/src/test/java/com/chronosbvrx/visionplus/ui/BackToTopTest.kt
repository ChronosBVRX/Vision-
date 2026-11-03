package com.chronosbvrx.visionplus.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Atrás en dos pasos (UX con mando): en las pantallas con barra superior el primer Atrás sube
 * el foco a la selección de arriba en vez de salir, y el segundo —dentro de la ventana— sí sale
 * (volver atrás o cerrar la app desde el Inicio).
 */
class BackToTopTest {

    @Test
    fun theFirstBackRaisesTheTopBarInsteadOfLeaving() {
        val guard = BackToTopGuard(windowMs = 2_500L)
        assertTrue("el primer Atrás debe subir a la barra", guard.press(nowMs = 1_000L))
        assertTrue("y quedar armado para el siguiente", guard.armed(nowMs = 1_200L))
    }

    @Test
    fun theSecondQuickBackLeaves() {
        val guard = BackToTopGuard(windowMs = 2_500L)
        assertTrue(guard.press(nowMs = 1_000L))
        assertFalse("el segundo Atrás dentro de la ventana ya sale", guard.press(nowMs = 2_400L))
        assertFalse("el armado se consume una sola vez", guard.armed(nowMs = 2_500L))
    }

    @Test
    fun aLateSecondBackRaisesAgainInsteadOfLeaving() {
        val guard = BackToTopGuard(windowMs = 2_500L)
        assertTrue(guard.press(nowMs = 1_000L))
        assertTrue("pasada la ventana nunca se sale de sorpresa", guard.press(nowMs = 9_000L))
    }

    @Test
    fun changingScreensDisarmsThePendingBack() {
        val guard = BackToTopGuard(windowMs = 2_500L)
        assertTrue(guard.press(nowMs = 1_000L))
        guard.disarm()
        assertTrue("la pantalla nueva empieza con su propio Atrás", guard.press(nowMs = 1_500L))
    }

    @Test
    fun theTvShellWiresTheTwoStepBackToTheTopBar() {
        val shell = source("ui/TvApp.kt")
        assertTrue(shell.contains("BackToTopGuard()"))
        assertTrue(shell.contains("topBarLanding"))
        assertTrue(shell.contains("topBarFocused"))
        assertTrue("el Inicio comparte la salida con aviso", shell.contains("onExitRequest"))
        assertTrue(shell.contains("landing = topBarLanding"))
        assertTrue(shell.contains("onFocusChanged = onTopBarFocusChanged"))
        assertTrue("la tecla no se come si la barra no existe", shell.contains("topBarLanding.attached"))

        val bar = source("ui/tv/TvTopBar.kt")
        assertTrue("la barra expone su destino de foco", bar.contains("landing: LandingFocus? = null"))
        assertTrue(bar.contains("Modifier.landingFocus(landing!!)"))
        assertTrue("y avisa cuando el mando está dentro", bar.contains("onFocusChanged(it.hasFocus)"))
        assertTrue(bar.contains("focusGroup()"))

        val activity = source("MainActivity.kt")
        assertTrue("la salida del Inicio es una sola", activity.contains("onExitRequest = requestExit"))
    }

    private fun source(rel: String): String {
        val found = listOf(
            File("src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("app/src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("../app/src/main/java/com/chronosbvrx/visionplus/$rel"),
        ).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
