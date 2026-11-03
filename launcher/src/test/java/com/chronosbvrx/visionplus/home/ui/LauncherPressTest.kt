package com.chronosbvrx.visionplus.home.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas puras de dos gestos que se sienten mal cuando fallan:
 *
 * - **OK largo**: el KeyUp de la pulsación que abrió el menú jamás debe activar la primera
 *   acción, aunque la persona siga apretando mientras lee el menú (antes vencía a los 2.5 s y
 *   la app se abría sola).
 * - **Atrás**: el primero sube el foco a la barra superior; el segundo, dentro de la ventana,
 *   navega. Nunca un salto accidental.
 */
class LauncherPressTest {

    @Test
    fun theReleaseOfALongPressIsSwallowedEvenIfItTakesLong() {
        var now = 1_000L
        val guard = PressReleaseGuard(clock = { now })
        guard.expectRelease()
        // La persona sigue apretando mucho más de 2.5 s: las repeticiones del mando no
        // desarman el candado.
        repeat(50) {
            now += 80L
            guard.noteKeyDown(repeatCount = it + 1)
        }
        assertTrue("el KeyUp tardío no activa la primera acción", guard.consumeRelease())
        // Los duplicados del mismo KeyUp tampoco.
        assertTrue(guard.consumeRelease())
    }

    @Test
    fun aRemoteThatReportsEveryRepeatAsANewPressDoesNotDisarmTheGuard() {
        // Caso real de muchos mandos de TV: cada repetición llega como KeyDown repeatCount=0.
        // Antes eso desarmaba el candado a media pulsación y al soltar se abría la app.
        var now = 1_000L
        val guard = PressReleaseGuard(clock = { now })
        guard.expectRelease()
        repeat(40) {
            now += 70L
            guard.noteKeyDown(repeatCount = 0)
        }
        assertTrue("se sigue comiendo el KeyUp mientras la tecla no se suelte", guard.consumeRelease())
    }

    @Test
    fun aNewPressRestoresTheNormalClick() {
        var now = 1_000L
        val guard = PressReleaseGuard(clock = { now })
        guard.expectRelease()
        assertTrue("se come el KeyUp que abrió el menú", guard.consumeRelease())
        // El mando se calla: la persona soltó y volvió a pulsar.
        now += 600L
        guard.noteKeyDown(repeatCount = 0)
        assertFalse("un OK nuevo y deliberado ya hace click", guard.consumeRelease())
    }

    @Test
    fun aNewPressWorksEvenIfTheOpeningReleaseWasLost() {
        // La ventana del diálogo puede no recibir el KeyUp que abrió el menú (se pierde al
        // cambiar de ventana). Tras el silencio, el KeyDown con repeatCount 0 demuestra que
        // la tecla ya se soltó: el primer OK dentro del menú debe funcionar, no comerse.
        var now = 1_000L
        val guard = PressReleaseGuard(clock = { now })
        guard.expectRelease()
        now += 600L
        guard.noteKeyDown(repeatCount = 0)
        assertFalse(guard.consumeRelease())
    }

    @Test
    fun whileTheGuardIsArmedNoClickCanOpenTheApp() {
        var now = 1_000L
        val guard = PressReleaseGuard(clock = { now })
        guard.expectRelease()
        assertTrue("la cola del OK largo no puede abrir la app", guard.isArmed())
        now += 600L
        guard.noteKeyDown(repeatCount = 0)
        assertFalse("un OK nuevo ya deja hacer click", guard.isArmed())
    }

    @Test
    fun theGuardDoesNothingWhenNoMenuWasOpened() {
        val guard = PressReleaseGuard(clock = { 1_000L })
        assertFalse(guard.consumeRelease())
        guard.noteKeyDown(repeatCount = 0)
        assertFalse(guard.consumeRelease())
    }

    @Test
    fun theFirstBackGoesToTheTopBarAndTheSecondNavigates() {
        val guard = LauncherBackToTopGuard(windowMs = 2_500L)
        assertTrue("primer Atrás: sube a la barra", guard.press(1_000L))
        assertTrue("sigue armado", guard.armed(1_100L))
        assertFalse("segundo Atrás dentro de la ventana: navega", guard.press(1_200L))
        assertFalse("el armado se consumió", guard.armed(1_300L))
    }

    @Test
    fun aLateBackGoesToTheTopBarAgainInsteadOfNavigating() {
        val guard = LauncherBackToTopGuard(windowMs = 2_500L)
        assertTrue(guard.press(1_000L))
        assertFalse("vencido", guard.armed(3_600L))
        assertTrue("vuelve a subir a la barra", guard.press(3_600L))
    }

    @Test
    fun changingScreenDisarmsTheOldBack() {
        val guard = LauncherBackToTopGuard(windowMs = 2_500L)
        assertTrue(guard.press(1_000L))
        guard.disarm()
        assertTrue("el siguiente Atrás empieza de cero", guard.press(1_200L))
    }
}
