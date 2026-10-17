package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes del puente del zapping: el modal de opciones del partido arma la lista ordenada
 * de señales y el reproductor en línea la recoge SOLO si el canal que se abre es el armado.
 * Por otro camino (Inicio, En vivo, búsqueda) el holder queda obsoleto: lista vacía y
 * comportamiento de siempre.
 */
class GeeZapSignalsTest {

    private fun ch(url: String, name: String = url) = LiveChannel(name = name, url = url)

    @Test
    fun takeReturnsTheRemainingSignalsWithoutCurrent() {
        val a = ch("http://a/hls.m3u8", "ESPN")
        val b = ch("http://b/hls.m3u8", "FOX")
        val c = ch("http://c/hls.m3u8", "TUDN")
        GeeZapSignals.arm(b, listOf(a, b, c))
        assertEquals(listOf(a, c), GeeZapSignals.takeFor(b))
    }

    @Test
    fun foreignChannelGetsNothing() {
        GeeZapSignals.arm(ch("http://a/hls.m3u8"), listOf(ch("http://b/hls.m3u8")))
        assertTrue(GeeZapSignals.takeFor(ch("http://home/live.m3u8")).isEmpty())
    }

    @Test
    fun duplicateUrlsCollapseToTheFirstKeepingOrder() {
        val a = ch("http://a/hls.m3u8", "ESPN")
        val b1 = ch("http://b/hls.m3u8", "FOX 1")
        val b2 = ch("http://b/hls.m3u8", "FOX 1 (repetido)")
        GeeZapSignals.arm(a, listOf(b1, b2))
        // La limpieza de duplicados y del canal en curso ocurre en `takeFor`.
        assertEquals(listOf(b1), GeeZapSignals.takeFor(a))
    }

    @Test
    fun repeatedTakesDrainTheHolder() {
        val a = ch("http://a/hls.m3u8")
        val b = ch("http://b/hls.m3u8")
        GeeZapSignals.arm(a, listOf(a, b, b))
        assertEquals(listOf(b), GeeZapSignals.takeFor(a))
        // Después de consumir, otro canal (o el mismo) ya no recibe nada: sin armadas.
        assertTrue(GeeZapSignals.takeFor(a).isEmpty())
        assertTrue(GeeZapSignals.takeFor(b).isEmpty())
    }
}
