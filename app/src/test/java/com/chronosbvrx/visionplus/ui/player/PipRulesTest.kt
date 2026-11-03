package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Política pura de Picture-in-Picture (sin Android): versiones, TV y búnker. */
class PipRulesTest {

    @Test
    fun pipNeedsAndroid8AndAPhone() {
        assertFalse("Android 7 no tiene PiP en celular", PipRules.isPipSupported(25, isTv = false))
        assertTrue("Android 8 ya lo tiene", PipRules.isPipSupported(26, isTv = false))
        assertTrue("Android 16 también", PipRules.isPipSupported(36, isTv = false))
        assertFalse("la tele no lo usa", PipRules.isPipSupported(34, isTv = true))
    }

    @Test
    fun downloadsOnlyEnterWhenTheUserOptsIn() {
        assertTrue("el catálogo normal siempre puede", PipRules.canUsePipFor(isVault = false, offlinePipAllowed = false))
        assertFalse("descarga con ajuste apagado: no", PipRules.canUsePipFor(isVault = true, offlinePipAllowed = false))
        assertTrue("descarga con permiso explícito: sí", PipRules.canUsePipFor(isVault = true, offlinePipAllowed = true))
    }

    @Test
    fun theActionFollowsThePlaybackState() {
        assertEquals("Pausar", PipRules.toggleTitle(playing = true))
        assertEquals("Reproducir", PipRules.toggleTitle(playing = false))
    }

    @Test
    fun theWindowKeepsTheVideoAspect() {
        // 16:9 es la proporción de casi todo el catálogo y de los canales lineales.
        assertEquals(16, PipRules.ASPECT_WIDTH)
        assertEquals(9, PipRules.ASPECT_HEIGHT)
    }
}
