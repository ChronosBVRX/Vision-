package com.chronosbvrx.visionplus.ui

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * El reloj de la casa: SIEMPRE el horario del centro de México (el mismo de la agenda
 * deportiva), aunque el equipo esté en otra zona horaria. Probado contra instantes UTC
 * fijos — en octubre el centro está en UTC−6 (sin horario de verano desde 2022).
 *
 * También fija DÓNDE vive el reloj: en el móvil nunca flota encima de la navegación
 * (va como texto pequeño dentro de la barra superior); la píldora es cosa de la tele.
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

    @Test
    fun thePhoneNeverFloatsTheClock() {
        menuRoutes().forEach { route ->
            assertFalse(
                "el móvil no debe flotar el reloj en $route",
                showsFloatingClock(isTv = false, route = route),
            )
        }
    }

    @Test
    fun theTvFloatsItOnTheMenuButNeverOnAPlayerOrTheSearch() {
        assertTrue(showsFloatingClock(isTv = true, route = Route.Home))
        assertTrue(showsFloatingClock(isTv = true, route = Route.Title(film())))
        assertTrue(showsFloatingClock(isTv = true, route = Route.Sports))
        assertTrue(showsFloatingClock(isTv = true, route = Route.Live))
        // Ahí manda el contenido (o el teclado): la píldora se apaga.
        assertFalse(showsFloatingClock(isTv = true, route = Route.Player(film())))
        assertFalse(
            showsFloatingClock(
                isTv = true,
                route = Route.LivePlayer(LiveChannel("Canal", "http://x")),
            ),
        )
        assertFalse(showsFloatingClock(isTv = true, route = Route.Search))
    }

    private fun menuRoutes(): List<Route> = listOf(
        Route.Home,
        Route.Categories,
        Route.Library,
        Route.Downloads,
        Route.Live,
        Route.Sports,
        Route.Settings,
        Route.About,
        Route.Remote,
        Route.Title(film()),
        Route.Category("movies", "Películas"),
        Route.Section("series", "Series"),
        Route.Search,
        Route.Player(film()),
        Route.LivePlayer(LiveChannel("Canal", "http://x")),
    )

    private fun film() = Film(
        id = "xtv:test",
        title = "Prueba",
        year = 2026,
        durationMin = 90,
        synopsis = "",
        posterUrl = "",
        videoUrl = "xtv:test",
        sourceUrl = "xtv",
    )
}
