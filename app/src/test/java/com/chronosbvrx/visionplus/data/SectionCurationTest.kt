package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clasificación de los submenús: Top 10, estrenos, tendencias, en emisión, mejor
 * valoradas y géneros. Son funciones puras: no hay red, Compose ni servidor.
 */
class SectionCurationTest {

    private fun film(
        id: String,
        year: Int = 2026,
        score: Double = 8.0,
        popularity: Double = 100.0,
        votes: Int = 1_000,
        genres: List<String> = listOf("Animación", "Aventura"),
        tmdbId: Int = 0,
    ) = Film(
        id = id,
        title = "Título $id",
        year = year,
        durationMin = 90,
        synopsis = "Sinopsis de $id",
        posterUrl = "https://example.com/$id.webp",
        videoUrl = "xtv:$id",
        sourceUrl = "xtv",
        score = score,
        popularity = popularity,
        voteCount = votes,
        tmdbId = tmdbId,
        kind = "kids",
        genres = genres,
    )

    @Test
    fun kidsFeedBringsTopTenPremieresTrendingGenresAndAllRow() {
        val films = (1..30).map { index ->
            val genres = when (index % 3) {
                0 -> listOf("Animación", "Aventura")
                1 -> listOf("Animación", "Comedia")
                else -> listOf("Animación", "Familia")
            }
            film(
                id = "k$index",
                year = if (index <= 10) 2026 else 2018,
                score = 7.0 + (index % 3),
                popularity = 50.0 + index,
                genres = genres,
            )
        }

        val rows = SectionCuration.build(
            key = "kids-movies",
            title = "Películas Infantiles",
            pool = films,
            kidsOnly = true,
            yearNow = 2026,
        )
        val ids = rows.map { it.id }

        assertTrue("Top 10 infantil", ids.contains("kids-movies-top10"))
        assertTrue("tendencias infantiles", ids.contains("kids-movies-tendencias"))
        assertTrue("mejor valoradas infantiles", ids.contains("kids-movies-mejor-valoradas"))
        assertTrue("fila de género", ids.any { it.startsWith("kids-movies-genero-") })
        assertTrue("fila con todo el catálogo", ids.contains("kids-movies-all"))
        assertFalse("las películas no llevan En emisión", ids.contains("kids-movies-en-emision"))

        val top = rows.first { it.id == "kids-movies-top10" }
        assertEquals(10, top.films.size)
        assertEquals(10, top.films.map { it.id }.distinct().size)

        val all = rows.first { it.id == "kids-movies-all" }
        assertEquals(30, all.films.size)
        assertEquals(30, all.total)
    }

    @Test
    fun localKidsGenresDoNotCollapseEverythingIntoAnimation() {
        val films = (1..24).map { index ->
            val genre = if (index % 2 == 0) "Aventura" else "Comedia"
            film("g$index", genres = listOf("Animación", "Familia", genre))
        }

        val rows = SectionCuration.build(
            key = "kids-series",
            title = "Series Infantiles",
            pool = films,
            kidsOnly = true,
            yearNow = 2026,
        )
        val genres = rows.filter { it.id.startsWith("kids-series-genero-") }.map { it.title }

        assertTrue("hay género Aventura", genres.contains("Aventura"))
        assertTrue("hay género Comedia", genres.contains("Comedia"))
        assertFalse("no todo cae en Animación", genres == listOf("Animación"))
    }

    @Test
    fun kidsWithoutCurrentPremieresFallsBackToNovelties() {
        val films = (1..20).map { index ->
            film(
                id = "old$index",
                year = 2019 + (index % 4),
                score = 7.0,
                popularity = 10.0,
                genres = listOf("Animación", "Aventura"),
            )
        }

        val rows = SectionCuration.build(
            key = "kids-movies",
            title = "Películas Infantiles",
            pool = films,
            kidsOnly = true,
            yearNow = 2026,
        )
        val novelties = rows.firstOrNull { it.id == "kids-movies-novedades" }

        assertNotNull("el catálogo infantil viejo cae a Novedades", novelties)
        assertTrue("no se etiqueta Estrenos sin estrenos", rows.none { it.id == "kids-movies-estrenos" })
        assertTrue("Novedades usa lo más reciente", novelties!!.films.all { it.year < 2025 })
    }

    @Test
    fun englishGenresAreNormalizedToSpanish() {
        val films = (1..12).map { index ->
            val genres = if (index % 2 == 0) {
                listOf("Animation", "Sci-Fi & Fantasy")
            } else {
                listOf("Animation", "Action & Adventure")
            }
            film("en$index", genres = genres)
        }

        val rows = SectionCuration.build(
            key = "kids-series",
            title = "Series Infantiles",
            pool = films,
            kidsOnly = true,
            yearNow = 2026,
        )
        val genres = rows.filter { it.id.startsWith("kids-series-genero-") }.map { it.title }

        assertTrue("ciencia ficción en español", genres.contains("Ciencia ficción"))
        assertTrue("acción en español", genres.contains("Acción"))
        assertFalse("sin géneros en inglés", genres.any { it.contains("Sci-Fi") || it.contains("Action") })
    }

    @Test
    fun seriesFeedAddsOnAirRowInScheduleOrder() {
        val films = (1..40).map { index ->
            film(
                id = "s$index",
                year = 2026,
                score = 7.5,
                popularity = 1_000.0 - index,
                genres = listOf("Drama"),
            )
        }.toMutableList()
        // Estas fichas no tienen señal: no entran al Top 10, Tendencias ni Mejor
        // valoradas, así que «En emisión» conserva el orden exacto de la agenda.
        val onAir = listOf("s36", "s33", "s39", "s31", "s38")
        onAir.forEach { id ->
            films += film(id, year = 2010, score = 0.0, popularity = 0.0, votes = 0, genres = listOf("Drama"))
        }

        val rows = SectionCuration.build(
            key = "series",
            title = "Series",
            pool = films,
            onAirIds = onAir,
            yearNow = 2026,
        )
        val row = rows.firstOrNull { it.id == "series-en-emision" }

        assertNotNull("la fila En emisión existe", row)
        assertEquals(onAir, row!!.films.map { it.id })
    }

    @Test
    fun emptyScheduleHidesTheOnAirRowAndOffersActiveSeries() {
        val films = (1..20).map { film("e$it", genres = listOf("Drama")) }

        val rows = SectionCuration.build(
            key = "anime",
            title = "Anime",
            pool = films,
            onAirIds = emptyList(),
            yearNow = 2026,
        )

        assertFalse("sin agenda no se inventa En emisión", rows.any { it.id == "anime-en-emision" })
        assertTrue("se usa el respaldo Series activas", rows.any { it.id == "anime-activas" })
    }

    @Test
    fun serverPremiereRowKeepsItsOrderAndCategoryId() {
        val strong = (1..20).map {
            film("strong$it", score = 9.0, popularity = 5_000.0)
        }
        val premieres = (1..4).map {
            film("premiere$it", year = 2015, score = 0.0, popularity = 0.0, votes = 0)
        }
        val genre = (1..6).map {
            film("genre$it", year = 2016, score = 0.0, popularity = 0.0, votes = 0, genres = listOf("Drama"))
        }
        val serverRows = listOf(
            CatalogRow("xtv-movies-estrenos", "Estrenos de Películas", premieres, premieres.size),
            CatalogRow("xtv-movies-genero-drama", "Drama", genre, genre.size),
        )

        val rows = SectionCuration.build(
            key = "movies",
            title = "Películas",
            pool = strong + premieres + genre,
            serverRows = serverRows,
            yearNow = 2026,
        )
        val row = rows.firstOrNull { it.id == "xtv-movies-estrenos" }

        assertNotNull("se usa la fila curada del servidor", row)
        assertEquals(premieres.map { it.id }, row!!.films.map { it.id })
        assertEquals(premieres.size, row.total)
    }
}
