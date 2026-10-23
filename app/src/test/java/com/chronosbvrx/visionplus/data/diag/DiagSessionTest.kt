package com.chronosbvrx.visionplus.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Anti-regresión del «% de sesiones sin crash»:
 *
 * - El id de sesión se crea al abrir la app y viaja a `:x4k` por archivo, para que un crash
 *   del reproductor cuente contra la apertura en la que ocurrió.
 * - Los latidos van a su PROPIA cola: una apertura nunca puede desalojar un crash pendiente.
 * - Una anomalía en bucle (portal que rechaza la sesión) se reporta una vez y se cuenta el
 *   resto, en vez de inundar la telemetría (121 avisos en 5 minutos, caso real).
 */
class DiagSessionTest {

    @Test
    fun sessionIdsAreShortHexAndDifferent() {
        val id = Diagnostics.newSessionId(Random(7))
        assertEquals(12, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(Diagnostics.newSessionId() != Diagnostics.newSessionId())
    }

    @Test
    fun theSessionFileRoundTripsAndExpires() {
        val now = 1_700_000_000_000L
        val text = Diagnostics.encodeSession("a1b2c3d4e5f6", now)
        assertEquals("a1b2c3d4e5f6", Diagnostics.decodeSession(text, now))
        // Dentro de la ventana de 6 h sigue sirviendo (el `:x4k` arranca después).
        assertNotNull(Diagnostics.decodeSession(text, now + 60 * 60 * 1000))
        // Pasada la ventana ya es otra sesión de uso.
        assertNull(Diagnostics.decodeSession(text, now + 7 * 60 * 60 * 1000))
        // Reloj movido hacia atrás: no se acepta un archivo del futuro.
        assertNull(Diagnostics.decodeSession(text, now - 10 * 60 * 1000))
    }

    @Test
    fun aBrokenSessionFileIsIgnoredInsteadOfCrashing() {
        assertNull(Diagnostics.decodeSession("", 1L))
        assertNull(Diagnostics.decodeSession("solo-id", 1L))
        assertNull(Diagnostics.decodeSession("abc\n123", 123L))
        assertNull(Diagnostics.decodeSession("a1b2c3d4e5f6\nno-es-numero", 1L))
    }

    @Test
    fun anomaliesOfTheSameCodeAreThrottledForAMinute() {
        assertTrue(Diagnostics.anomalyGateOpen(10_000L, 0L))
        assertFalse(Diagnostics.anomalyGateOpen(10_000L, 9_500L))
        assertFalse(Diagnostics.anomalyGateOpen(69_999L, 10_000L))
        assertTrue(Diagnostics.anomalyGateOpen(70_000L, 10_000L))
    }

    @Test
    fun theHeartbeatHasItsOwnQueueAndTheCrashCarriesTheSession() {
        val diag = File("src/main/java/com/chronosbvrx/visionplus/data/diag/Diagnostics.kt").readText()
        assertTrue("cola propia para latidos", diag.contains("SESSION_QUEUE_DIR"))
        assertTrue("tope propio (no compite con los crash)", diag.contains("MAX_SESSION_QUEUE"))
        assertTrue("el payload lleva la sesión", diag.contains("put(\"session\", session)"))
        assertTrue("latido por apertura", diag.contains("fun sessionStart()"))

        // La app avisa al abrir (MainActivity), no desde un proceso de fondo.
        val main = File("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt").readText()
        assertTrue("MainActivity debe latir al abrir", main.contains("Diagnostics.sessionStart()"))
    }
}
