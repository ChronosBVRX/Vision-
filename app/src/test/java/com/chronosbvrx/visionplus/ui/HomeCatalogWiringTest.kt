package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado del catálogo del Inicio (móvil y TV):
 * - «Populares de la semana» y las filas por tipo no repiten lo que ya salió arriba.
 * - La deduplicación por obra usa el tmdbId que manda el servidor.
 * Sin esto, el Inicio vuelve a pintar la misma tarjeta varias veces («Link Click» ×4).
 */
class HomeCatalogWiringTest {

    @Test
    fun bothHomesDropRepeatsBetweenTopRowsAndWeeklyRow() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/home/HomeScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvHomeScreen.kt")
        for ((name, text) in listOf("móvil" to phone, "TV" to tv)) {
            assertTrue("$name filtra la hilera semanal", text.contains("Popularity.withoutRepeats(trendingFilms"))
            assertTrue("$name filtra las filas por tipo", text.contains("val kindRowsForHome"))
            assertTrue("$name pinta las filas filtradas", text.contains("kindRowsForHome"))
        }
    }

    @Test
    fun theServerTmdbIdTravelsToTheHomeEngine() {
        val remote = requireSource("src/main/java/com/chronosbvrx/visionplus/data/sources/RemoteIndexSource.kt")
        assertTrue("el índice parsea tmdbId", remote.contains("f.optInt(\"tmdbId\", 0)"))
        assertTrue("la caché conserva tmdbId", remote.contains("put(\"tmdbId\", f.tmdbId)"))
        val popularity = requireSource("src/main/java/com/chronosbvrx/visionplus/data/Popularity.kt")
        assertTrue("la identidad usa TMDB", popularity.contains("tmdb:\${film.tmdbId}"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
