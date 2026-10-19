package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado del buscador (que no se puede componer en pruebas sin red):
 * - Las tarjetas muestran «También: …» cuando el nombre original difiere del mostrado
 *   (el índice del servidor encuentra por nombres es-MX/regionales: «vaiana» → «Moana»).
 * - El buscador del control remoto pregunta también al servidor (el mismo catálogo del
 *   Inicio), conservando el respaldo local instantáneo.
 */
class SearchWiringTest {

    @Test
    fun bothSearchesShowTheOriginalTitleWhenItDiffers() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/phone/SearchScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSearchScreen.kt")
        assertTrue("la búsqueda móvil pinta el nombre original", phone.contains("film.akaLine()"))
        assertTrue("la búsqueda de TV pinta el nombre original", tv.contains("film.akaLine()"))
    }

    @Test
    fun theRemoteSearchAlsoAsksTheServer() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/remote/RemoteScreen.kt")
        assertTrue("el buscador remoto usa el servidor", text.contains("RemoteIndexSource.search("))
        assertTrue("el respaldo local se mantiene", text.contains("searchFilms(rows, q)"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
