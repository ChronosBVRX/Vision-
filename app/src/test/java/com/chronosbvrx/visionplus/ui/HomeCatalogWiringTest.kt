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
            assertTrue(
                "$name filtra la hilera semanal",
                text.contains("Popularity.withoutRepeats(") && text.contains("trendingFilms"),
            )
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

    @Test
    fun bothHomesKeepTheServerCuratedOrder() {
        val popularity = requireSource("src/main/java/com/chronosbvrx/visionplus/data/Popularity.kt")
        // El servidor rankea tendencias/estrenos con señales que la app no tiene
        // (popularidad refrescada, trending semanal, recién llegados). Reordenar aquí
        // era lo que dejaba el Top 10 y «Novedades» siempre iguales.
        assertTrue(
            "el Top 10 respeta el orden del servidor",
            popularity.contains("val ranked = dedupe(ranking.filter"),
        )
        assertTrue(
            "Novedades usa la fila curada del servidor",
            popularity.contains("firstOrNull { it.id == \"estrenos\" }"),
        )
        // Más variedad en la hilera semanal.
        for (rel in listOf(
            "src/main/java/com/chronosbvrx/visionplus/ui/home/HomeScreen.kt",
            "src/main/java/com/chronosbvrx/visionplus/ui/tv/TvHomeScreen.kt",
        )) {
            assertTrue(
                "la hilera semanal pide 40 títulos",
                requireSource(rel).contains("RemoteIndexSource.trending(40)"),
            )
        }
    }

    @Test
    fun sectionScreensDoNotRepeatACardAcrossGenres() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/phone/SectionScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvCategoryScreen.kt")
        assertTrue("móvil deduplica entre filas", phone.contains("Popularity.disjointRows"))
        assertTrue("TV deduplica entre filas", tv.contains("Popularity.disjointRows"))
        // «Recomendado para ti» tampoco repite lo que ya salió en los géneros.
        assertTrue("TV no repite en el recomendado", tv.contains("Popularity.withoutRepeats("))
        // Las filas curadas del servidor conservan su orden (lo que las hace vivir).
        assertTrue("móvil respeta estrenos/tendencias", phone.contains("row.id.endsWith(\"-estrenos\")"))
    }

    @Test
    fun bothHomesRenderTheThematicCollections() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/home/HomeScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvHomeScreen.kt")
        val feed = requireSource("src/main/java/com/chronosbvrx/visionplus/data/homefeed/HomeFeedStore.kt")
        assertTrue("móvil lee las temáticas del curado", phone.contains("curated.themeRows"))
        assertTrue("TV lee las temáticas del curado", tv.contains("curated.themeRows"))
        assertTrue("TV las pinta en el Inicio", tv.contains("themeRows = themeRows"))
        assertTrue("el launcher también las publica", feed.contains("curated.themeRows"))
        val popularity = requireSource("src/main/java/com/chronosbvrx/visionplus/data/Popularity.kt")
        assertTrue("el Inicio las separa de las filas por tipo", popularity.contains("themeRows = themeRows"))
        assertTrue("Tendencias del mundo no se duplica en el Inicio", popularity.contains("tema-tendencias-mundo"))
        val categories = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/phone/CategoriesScreen.kt")
        assertTrue("Categorías separa las temáticas", categories.contains("Temáticas"))
        assertTrue("Categorías conserva el resto", categories.contains("Géneros y colecciones"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
