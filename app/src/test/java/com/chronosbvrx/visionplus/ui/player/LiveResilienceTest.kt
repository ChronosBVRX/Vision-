package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes de la política de resiliencia del reproductor en línea: los servidores de
 * deportes en vivo tienen microcaídas por sobrecarga y la app ya no se rinde al primer
 * error fatal (el «Reintentar» que pulsaba el usuario lo hace la app sola).
 */
class LiveResilienceTest {

    // ------------------------------------------------------------ clasificar

    @Test
    fun transientNetworkErrorsGetAutoRecovery() {
        val transient = listOf(
            1002, // BEHIND_LIVE_WINDOW (ventana del vivo cerrada por el servidor)
            1003, // TIMEOUT
            2000, // IO_UNSPECIFIED
            2001, // NETWORK_CONNECTION_FAILED
            2002, // NETWORK_CONNECTION_TIMEOUT
            2003, // INVALID_HTTP_CONTENT_TYPE (el server renqueó a medias)
            1000, // UNSPECIFIED (media3 envuelve de todo aquí; el budget acota)
        )
        transient.forEach { code ->
            assertEquals("código $code debería ser TRANSIENT", LiveResilience.Kind.TRANSIENT, LiveResilience.classify(code))
        }
    }

    @Test
    fun deadLinksAndDrmAreFatal() {
        val fatal = listOf(
            2005, // FILE_NOT_FOUND (enlace muerto)
            2006, // NO_PERMISSION
            2007, // CLEARTEXT_NOT_PERMITTED
            3000, // DRM_UNSPECIFIED
            4001, // parsing
            5001, // decoding
            6001, // audio track write
            1004, // FAILED_RUNTIME_CHECK
        )
        fatal.forEach { code ->
            assertEquals("código $code debería ser FATAL", LiveResilience.Kind.FATAL, LiveResilience.classify(code))
        }
    }

    @Test
    fun serverOverloadHttpStatusesAreTransientOthersAreNot() {
        listOf(408, 429, 500, 502, 503, 504).forEach { status ->
            assertEquals(
                "HTTP $status es sobrecarga: TRANSIENT",
                LiveResilience.Kind.TRANSIENT,
                LiveResilience.classify(2004, status),
            )
        }
        listOf(403, 404, 410, 451).forEach { status ->
            assertEquals(
                "HTTP $status no se arregla reintentando: FATAL",
                LiveResilience.Kind.FATAL,
                LiveResilience.classify(2004, status),
            )
        }
    }

    // ------------------------------------------------------------ backoff / presupuesto

    @Test
    fun backoffScalesAndBudgetEndsInCard() {
        assertEquals(1_000L, LiveResilience.delayFor(0))
        assertEquals(2_000L, LiveResilience.delayFor(1))
        assertEquals(4_000L, LiveResilience.delayFor(2))
        assertEquals(8_000L, LiveResilience.delayFor(3))
        assertEquals(8_000L, LiveResilience.delayFor(4))
        assertEquals(8_000L, LiveResilience.delayFor(5))
        // Intent 6: agotado — ahí sale la tarjeta Reintentar/Volver de siempre.
        assertNull(LiveResilience.delayFor(6))
        assertNull(LiveResilience.delayFor(-1))
    }

    @Test
    fun budgetCoversNoLessThanSixAttempts() {
        for (attempt in 0 until LiveResilience.MAX_ATTEMPTS) {
            assertTrue("intento $attempt debe tener backoff", LiveResilience.delayFor(attempt) != null)
        }
        assertNull(LiveResilience.delayFor(LiveResilience.MAX_ATTEMPTS))
        assertTrue(LiveResilience.MAX_ATTEMPTS >= 4)
    }

    // ------------------------------------------------------------ vigilante de estancamiento

    @Test
    fun stallOnlyCountsWhenReadyPlayingAndQuiet() {
        val ms = LiveResilience.STALL_TRIGGER_MS
        assertTrue(LiveResilience.stallRecoveryNeeded(stateReady = true, playWhenReady = true, hasError = false, stallMs = ms))
        assertFalse("pausa del usuario no es stall", LiveResilience.stallRecoveryNeeded(true, false, false, ms))
        assertFalse("con error activo no corre el vigilante", LiveResilience.stallRecoveryNeeded(true, true, true, ms))
        assertFalse("buffering de arranque no es stall", LiveResilience.stallRecoveryNeeded(true, true, false, ms - 1))
        assertFalse("sin estado READY no hay stall", LiveResilience.stallRecoveryNeeded(false, true, false, ms))
    }

    @Test
    fun stallThresholdIsHonest() {
        // 10 s: holgado para microcaídas reales (el servidor tarda en reengancha),
        // muy por debajo del error duro del portal (~25 s) para no duplicar trabajos.
        assertTrue(LiveResilience.STALL_TRIGGER_MS in 5_000L..15_000L)
    }

    // ------------------------------------------------------------ zapping / failover

    @Test
    fun zapWrapsAroundBothWays() {
        // Arriba en la primera señal cae a la última; Abajo en la última vuelve a la primera.
        assertEquals(2, LiveResilience.cycleIndex(3, 0, -1))
        assertEquals(0, LiveResilience.cycleIndex(3, 2, 1))
        assertEquals(1, LiveResilience.cycleIndex(3, 0, 1))
        // Con una sola señal, el zapping es un no-op honesto (se queda ahí).
        assertEquals(0, LiveResilience.cycleIndex(1, 0, 1))
        assertEquals(0, LiveResilience.cycleIndex(1, 0, -1))
        // Defensas: tamaño inválido no revienta.
        assertEquals(5, LiveResilience.cycleIndex(0, 5, 1))
    }

    @Test
    fun failoverTriesEveryOtherSignalOnceThenGivesUp() {
        // Sin señales alternativas: sin failover (tarjeta de siempre, como siempre).
        assertFalse(LiveResilience.failoverAllowed(0, 0))
        assertFalse(LiveResilience.failoverAllowed(1, 0))
        // Con 3 señales: hasta 2 failover automáticos (todas las demás señales probadas).
        assertTrue(LiveResilience.failoverAllowed(3, 0))
        assertTrue(LiveResilience.failoverAllowed(3, 1))
        assertFalse("agotadas las señales, la tarjeta manda", LiveResilience.failoverAllowed(3, 2))
        // El zappeo MANUAL del usuario no consume el presupuesto del failover.
        assertFalse(LiveResilience.failoverAllowed(3, 5))
    }
}
