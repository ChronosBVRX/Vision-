package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cableado de las dos filas personales del Inicio. Los guardianes puros viven en
 * `HomePersonalTest`; este vigila que las pantallas las usen de verdad y que la
 * agenda siga cacheada (sin caché, cada apertura del Inicio pedía la agenda).
 */
class HomePersonalWiringTest {

    @Test
    fun bothHomesPaintThePersonalRows() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/home/HomeScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvHomeScreen.kt")

        for ((name, text) in listOf("móvil" to phone, "TV" to tv)) {
            assertTrue("$name calcula «Porque viste X»", text.contains("HomePersonal.becauseYouWatched"))
            assertTrue("$name calcula la agenda de seguidas", text.contains("HomePersonal.weeklyFollowed"))
            assertTrue("$name pide la agenda cacheada", text.contains("schedule(7)"))
            assertTrue("$name respeta el modo niños en la semilla", text.contains("kidsOnly = locked"))
            assertTrue("$name dibuja la fila de recomendación", text.contains("home_because"))
            assertTrue("$name dibuja la fila de la semana", text.contains("home_follow"))
            assertTrue("$name usa el título único de la agenda", text.contains("HomePersonal.FOLLOW_ROW_TITLE"))
        }

        // En TV las filas entran por la fuente única del orden: sin slot, el regreso del
        // D-pad se desincroniza (el fallo de «Populares de la semana»).
        assertTrue("slot de Porque viste", tv.contains("HomeRowSlot.Because"))
        assertTrue("slot de Tus series", tv.contains("HomeRowSlot.FollowWeek"))
        assertTrue("la agenda pinta su fila propia", tv.contains("HomeAiringRow"))
    }

    @Test
    fun theScheduleIsCachedSoTheHomeDoesNotHammerTheServer() {
        val remote = requireSource("src/main/java/com/chronosbvrx/visionplus/data/sources/RemoteIndexSource.kt")
        assertTrue("TTL único en HomePersonal", remote.contains("HomePersonal.SCHEDULE_TTL_MS"))
        assertTrue("caché en memoria", remote.contains("memoryScheduleDays"))
        assertTrue("la copia ancha sirve a la corta", remote.contains("memoryScheduleDays >= window"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
