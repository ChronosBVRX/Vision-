package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-regresión del auto-reparado del motor (cajas atascadas): la generación 1 conserva
 * las carpetas históricas, una generación nueva jamás reusa la carpeta posiblemente
 * envenenada, y el disparo exige dos fallos silenciosos consecutivos.
 */
class PlayerRecoveryTest {

    @Test
    fun generationOneKeepsHistoricalDirNames() {
        assertEquals("x4k-luna", PlayerRecovery.dirNameFor(mainProcess = false, gen = 1))
        assertEquals("x4k-luna-main", PlayerRecovery.dirNameFor(mainProcess = true, gen = 1))
    }

    @Test
    fun healedGenerationUsesAFreshDir() {
        assertEquals("x4k-luna-gen2", PlayerRecovery.dirNameFor(mainProcess = false, gen = 2))
        assertEquals("x4k-luna-main-gen7", PlayerRecovery.dirNameFor(mainProcess = true, gen = 7))
        // La carpeta nueva nunca es la histórica: el estado viejo no se hereda jamás.
        assertFalse(
            PlayerRecovery.dirNameFor(false, 2) == PlayerRecovery.dirNameFor(false, 1),
        )
    }

    @Test
    fun healTriggersOnSecondConsecutiveSilentFailure() {
        assertFalse(PlayerRecovery.shouldHeal(0))
        assertFalse(PlayerRecovery.shouldHeal(1))
        assertTrue(PlayerRecovery.shouldHeal(2))
        assertTrue(PlayerRecovery.shouldHeal(5))
    }
}
