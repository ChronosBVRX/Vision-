package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EpgTest {

    @Test
    fun epgItemWindowCalculation() {
        val now = 1791690000000L
        val start = now - 30 * 60 * 1000L // 30 min ago
        val end = now + 30 * 60 * 1000L   // in 30 min
        val item = X4KApi.EpgItem("Programa en curso", "Descripción del programa", start, end)

        val total = item.endMs - item.startMs
        val elapsed = now - item.startMs
        val pct = ((elapsed * 100) / total).toInt().coerceIn(0, 100)

        assertEquals(50, pct)
        assertEquals("Programa en curso", item.name)
        assertEquals("Descripción del programa", item.description)
    }

    @Test
    fun epgFilterCurrentAndNext() {
        val now = 10000L
        val items = listOf(
            X4KApi.EpgItem("Pasado", "Terminado", 1000L, 5000L),
            X4KApi.EpgItem("Actual", "Al aire", 5000L, 15000L),
            X4KApi.EpgItem("Siguiente", "A continuación", 15000L, 25000L),
            X4KApi.EpgItem("Futuro 2", "Más tarde", 25000L, 35000L),
        )

        val current = items.firstOrNull { it.startMs in 1..now && now < it.endMs }
        val next = items.firstOrNull { it.startMs > now }
        val upcoming = items.filter { it.startMs > now }.take(2)

        assertNotNull(current)
        assertEquals("Actual", current?.name)

        assertNotNull(next)
        assertEquals("Siguiente", next?.name)

        assertEquals(2, upcoming.size)
        assertEquals("Siguiente", upcoming[0].name)
        assertEquals("Futuro 2", upcoming[1].name)
    }

    @Test
    fun epgEmptyWhenNoWindowMatches() {
        val now = 50000L
        val items = listOf(
            X4KApi.EpgItem("Pasado", "Terminado", 1000L, 5000L),
        )

        val current = items.firstOrNull { it.startMs in 1..now && now < it.endMs }
        assertNull(current)
    }
}
