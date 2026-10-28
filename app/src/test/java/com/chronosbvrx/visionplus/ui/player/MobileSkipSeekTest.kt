package com.chronosbvrx.visionplus.ui.player

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El detalle del móvil que reportó el usuario: «Omitir intro» y elegir un minuto en la barra.
 *
 * - La píldora se VE sobre el video con los controles cerrados, así que el toque tiene que
 *   funcionar al primero (antes el candado del mando la bloqueaba con el menú cerrado).
 * - Elegir un minuto no puede perderse: el ocultado programado por un toque previo no puede
 *   vencer a mitad del arrastre y el sondeo no puede devolver el pulgar mientras el motor
 *   procesa el seek.
 */
class MobileSkipSeekTest {

    @Test
    fun theSkipPillAnswersATouchEvenWithTheControlsClosed() {
        // Móvil: siempre se puede tocar (no hay candado de tecla que proteger).
        assertTrue(
            canPressSkipPill(isTv = false, controlsVisible = false, nowMs = 10_000L, lastWakeupMs = 9_990L),
        )
        assertTrue(canPressSkipPill(isTv = false, controlsVisible = true, nowMs = 10_000L, lastWakeupMs = 10_000L))
        // Tele: el primer OK solo abre el menú; el candado de 220 ms sigue vigente.
        assertFalse(canPressSkipPill(isTv = true, controlsVisible = false, nowMs = 10_000L, lastWakeupMs = 0L))
        assertFalse(canPressSkipPill(isTv = true, controlsVisible = true, nowMs = 10_000L, lastWakeupMs = 9_900L))
        assertTrue(canPressSkipPill(isTv = true, controlsVisible = true, nowMs = 10_000L, lastWakeupMs = 9_000L))
    }

    @Test
    fun thePillIsVisibleWithTheControlsClosedOnlyOnThePhone() {
        val base = { isTv: Boolean, controls: Boolean ->
            skipPillVisibleFor(
                hasTarget = true,
                buttonMode = true,
                videoReady = true,
                blocked = false,
                isTv = isTv,
                controlsVisible = controls,
            )
        }
        assertTrue("móvil con barra cerrada", base(false, false))
        assertTrue("móvil con barra abierta", base(false, true))
        assertFalse("tele con barra cerrada (el primer OK despierta el menú)", base(true, false))
        assertTrue("tele con barra abierta", base(true, true))
        assertFalse(
            "sin objetivo no hay píldora",
            skipPillVisibleFor(false, true, true, false, isTv = false, controlsVisible = false),
        )
        assertFalse(
            "en la pantalla de carga no hay píldora",
            skipPillVisibleFor(true, true, false, false, isTv = false, controlsVisible = false),
        )
    }

    @Test
    fun theChosenMinuteStaysOnTheBarWhileTheEngineSeeks() {
        val target = 60 * 60_000L
        val until = 10_000L
        // El motor aún está en el minuto viejo: la barra se queda en el elegido.
        assertTrue(seekHoldActive(target, until, nowMs = 9_000L, enginePosMs = 5 * 60_000L))
        // El motor ya llegó (±2 s): manda la posición real.
        assertFalse(seekHoldActive(target, until, nowMs = 9_000L, enginePosMs = target - 1_500L))
        // Venció el margen: manda el motor (un seek rechazado no deja la barra mintiendo).
        assertFalse(seekHoldActive(target, until, nowMs = until, enginePosMs = 5 * 60_000L))
        // Sin seek pedido no hay nada que sostener.
        assertFalse(seekHoldActive(-1L, until, nowMs = 9_000L, enginePosMs = 5 * 60_000L))
    }

    @Test
    fun theNativePlayerWiresTheTouchFixes() {
        val player = source("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue(
            "la píldora no exige controles abiertos en el móvil",
            player.contains("if (!isTv || canExecuteControlAction()) skipIntroNow()"),
        )
        assertTrue("sostiene la barra en el minuto elegido", player.contains("holdSeekBarAt("))
        assertTrue("el sondeo respeta la espera", player.contains("!scrubbing && !seekBarHoldActive(pos)"))
        assertTrue(
            "elegir un minuto cancela el ocultado programado",
            player.contains("handler.removeCallbacks(pendingControlHide)"),
        )
        assertTrue("el ocultado no vence con el dedo en la barra", player.contains("if (!scrubbing && !errorShown"))
        assertFalse(
            "el overlay ya no se come los toques vacíos",
            player.contains("overlay.setOnClickListener { }"),
        )
        // Sin duración conocida no se puede elegir un minuto: no se manda el video al segundo 0.
        assertTrue("guarda de duración", player.contains("if (total > 0 && sb != null)"))
    }

    @Test
    fun theComposePlayerUsesTheSameRules() {
        val screen = source("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("píldora por la política única (móvil sin controles)", screen.contains("skipPillVisibleFor("))
        assertTrue("toque del móvil sin candado de mando", screen.contains("canPressSkipPill("))
        assertTrue("la barra no elige un minuto sin duración", screen.contains("if (target != null && totalDur > 0)"))
        assertTrue("no se registra un arrastre sin duración", screen.contains("if (totalDur > 0) {"))
    }

    private fun source(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
