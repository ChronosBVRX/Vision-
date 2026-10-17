package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * El reloj de la casa: SIEMPRE el horario del centro de México (el mismo de la agenda
 * deportiva), aunque el equipo esté en otra zona horaria. Probado contra instantes UTC
 * fijos — en octubre el centro está en UTC−6 (sin horario de verano desde 2022).
 */
class VisionClockTest {

    private fun utcMs(iso: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso)!!
            .time

    @Test
    fun timeTextAlwaysShowsMexicoCentral() {
        assertEquals("07:19", VisionClock.timeText(utcMs("2026-10-17T13:19:00")))
        // 02:00 UTC del día 18 = 20:00 del 17 en el centro.
        assertEquals("20:00", VisionClock.timeText(utcMs("2026-10-18T02:00:00")))
        assertEquals("00:00", VisionClock.timeText(utcMs("2026-10-17T06:00:00")))
    }

    @Test
    fun msToNextMinuteTicksAtTheChange() {
        val base = utcMs("2026-10-17T13:19:00") // :00.000
        assertEquals(60_000L, VisionClock.msToNextMinute(base))
        assertEquals(29_750L, VisionClock.msToNextMinute(base + 30_250L))
        // Cerca del cambio queda el piso de seguridad (nunca 0 y nunca negativo).
        assertEquals(250L, VisionClock.msToNextMinute(base + 59_900L))
    }
}
