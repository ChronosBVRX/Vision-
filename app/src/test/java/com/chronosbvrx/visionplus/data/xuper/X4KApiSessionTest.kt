package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Anti-regresión de la sesión XTV: una patada (aaa100083) debe permitir re-acuñar de
 * inmediato aunque el silencio anti-tormenta siga activo; el silencio solo frena ráfagas.
 */
class X4KApiSessionTest {

    @Test
    fun kickedTokenAllowsImmediateForcedMint() {
        val now = 1_000_000L
        // Acuñado hace 5 s: el silencio normal (30 s) lo bloquearía, pero la patada lo salta.
        assertFalse(X4KApi.mintAllowed(now, lastMintAt = now - 5_000, lastForcedMintAt = 0, force = false))
        assertTrue(X4KApi.mintAllowed(now, lastMintAt = now - 5_000, lastForcedMintAt = 0, force = true))
    }

    @Test
    fun forcedMintHasItsOwnShortCooldown() {
        val now = 1_000_000L
        // Un mint forzado reciente (5 s) frena la ráfaga; pasados 10 s vuelve a permitirse.
        assertFalse(X4KApi.mintAllowed(now, lastMintAt = now - 60_000, lastForcedMintAt = now - 5_000, force = true))
        assertTrue(X4KApi.mintAllowed(now, lastMintAt = now - 60_000, lastForcedMintAt = now - 11_000, force = true))
    }

    @Test
    fun normalMintWaitsTheStormSilence() {
        val now = 1_000_000L
        assertFalse(X4KApi.mintAllowed(now, lastMintAt = now - 10_000, lastForcedMintAt = 0, force = false))
        assertTrue(X4KApi.mintAllowed(now, lastMintAt = now - 31_000, lastForcedMintAt = 0, force = false))
    }

    @Test
    fun sessionFailureDetectsKicksAndFullRanges() {
        assertTrue(X4KApi.isSessionFailure(IOException("portal aaa100083")))
        assertTrue(X4KApi.isSessionFailure(IOException("portal 100001 expired")))
        assertTrue(X4KApi.isSessionFailure(IOException("portal 100004")))
        assertTrue(X4KApi.isSessionFailure(IOException("portal 100008")))
        assertTrue(X4KApi.isSessionFailure(IOException("portal 200002")))
        assertTrue(X4KApi.isSessionFailure(IOException("portal 300002")))
        assertTrue(X4KApi.isSessionFailure(IOException("HTTP 403")))
    }

    @Test
    fun businessErrorsAreNotSessionFailures() {
        assertFalse(X4KApi.isSessionFailure(IOException("portal 999999 licencia no disponible")))
        assertFalse(X4KApi.isSessionFailure(IOException("respuesta ilegible")))
    }
}
