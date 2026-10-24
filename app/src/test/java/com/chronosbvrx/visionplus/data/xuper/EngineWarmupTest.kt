package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El warmup del motor es la pieza que mueve el costo del toque al arranque. Estos
 * guardianes fijan las dos reglas que no se pueden romper:
 *
 *  - un estado "caliente" solo vale si el proceso `:x4k` sigue vivo y es reciente;
 *  - los equipos de 1.5 GB no entran en el camino completo (se quedan con el diferido).
 */
class EngineWarmupTest {

    @Test
    fun readyStateParsesTheSharedFile() {
        val ready = EngineWarmup.parseReady(
            "ok=1\nat=1700000000000\npid=4242\nsource=launcher\n",
        )
        assertNotNull(ready)
        assertTrue(ready!!.ok)
        assertTrue(ready.at == 1_700_000_000_000L)
        assertTrue(ready.pid == 4242)
        assertTrue(ready.source == "launcher")
    }

    @Test
    fun brokenOrIncompleteStateIsNotTrusted() {
        assertTrue(EngineWarmup.parseReady("") == null)
        assertTrue(EngineWarmup.parseReady("ok=1\nat=1\n") == null) // sin pid
        assertTrue(EngineWarmup.parseReady("ok=1\npid=7\n") == null) // sin at
    }

    @Test
    fun freshRequiresOkPidAgeAndLiveProcess() {
        val now = 1_700_000_000_000L
        val ready = EngineWarmup.Ready(ok = true, at = now - 1_000L, pid = 42, source = "splash")
        assertTrue(EngineWarmup.isFresh(ready, now, 15 * 60_000L) { it == 42 })
        assertFalse("proceso muerto", EngineWarmup.isFresh(ready, now, 15 * 60_000L) { false })
        assertFalse("ok=0", EngineWarmup.isFresh(ready.copy(ok = false), now, 15 * 60_000L) { true })
        assertFalse("vencido", EngineWarmup.isFresh(ready, now, 500L) { true })
        assertFalse("pid inválido", EngineWarmup.isFresh(ready.copy(pid = 0), now, 15 * 60_000L) { true })
        assertFalse(
            "reloj muy adelantado",
            EngineWarmup.isFresh(ready.copy(at = now + 120_000L), now, 15 * 60_000L) { true },
        )
    }

    @Test
    fun fullWarmupOnlyOnTvWithTwoGigabytesOrMore() {
        // Chromecast HD/4K y Fire TV Stick: 1.5 GB, se quedan con el camino diferido.
        assertFalse(EngineWarmup.shouldWarmFor(1_536L, 1_800, isTv = true, enabled = true))
        // Teles de 2 GB: sí.
        assertTrue(EngineWarmup.shouldWarmFor(1_900L, 1_800, isTv = true, enabled = true))
        assertTrue(EngineWarmup.shouldWarmFor(2_048L, 1_800, isTv = true, enabled = true))
        // Móvil o kill-switch apagado: no.
        assertFalse(EngineWarmup.shouldWarmFor(8_192L, 1_800, isTv = false, enabled = true))
        assertFalse(EngineWarmup.shouldWarmFor(8_192L, 1_800, isTv = true, enabled = false))
    }
}
