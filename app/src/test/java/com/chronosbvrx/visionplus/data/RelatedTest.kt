package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Relacionadas»: misma obra nunca, mismo género primero, mismo tipo después y
 * sin mezclar cine con series. La selección es pura para poder blindarla.
 */
class RelatedTest {

    private fun film(
        id: String,
        title: String,
        kind: String = "movie",
        year: Int = 2020,
        tmdbId: Int = 0,
        groupId: String = "",
        popularity: Double = 0.0,
        score: Double = 0.0,
        videoUrl: String = "xtv:$id",
    ) = Film(
        id = id,
        title = title,
        year = year,
        durationMin = 100,
        synopsis = "",
        posterUrl = "p",
        videoUrl = videoUrl,
        sourceUrl = "xtv",
        score = score,
        popularity = popularity,
        tmdbId = tmdbId,
        groupId = groupId,
        kind = kind,
    )

    private fun row(title: String, vararg films: Film) =
        CatalogRow(id = title.lowercase().replace(' ', '-'), title = title, films = films.toList())

    @Test
    fun picksTheSameGenreAndSkipsTheCurrentTitle() {
        val current = film("1", "La actual", tmdbId = 10)
        val sameGenre = film("2", "Misma acción", tmdbId = 11, popularity = 5.0)
        val duplicate = film("3", "Otra copia", tmdbId = 10)
        val otherGenre = film("4", "Comedia", kind = "movie", popularity = 99.0)
        val rows = listOf(
            row("Acción", current, sameGenre, duplicate),
            row("Comedia", otherGenre),
        )

        val picked = Related.pickRelated(current, rows, genreHint = "Acción", limit = 10)

        assertEquals("la del mismo género va primero", "2", picked.first().id)
        assertFalse("la obra actual no se repite", picked.any { it.id == "1" })
        assertFalse("su otra copia (mismo tmdb) tampoco", picked.any { it.id == "3" })
    }

    @Test
    fun prefersTheSameKindBeforeTheRest() {
        val current = film("1", "Serie actual", kind = "series")
        val movie = film("2", "Película de acción", kind = "movie", popularity = 99.0)
        val series = film("3", "Serie de acción", kind = "series", popularity = 1.0)
        val rows = listOf(row("Acción", movie, series))

        val picked = Related.pickRelated(current, rows, genreHint = "Acción", limit = 10)

        assertEquals("3", picked.first().id)
    }

    @Test
    fun excludesOtherSeasonsOfTheSameGroup() {
        val current = film("1", "La casa de papel", kind = "series", groupId = "g1")
        val season2 = film("2", "La casa de papel T2", kind = "series", groupId = "g1")
        val other = film("3", "Otra serie", kind = "series", groupId = "g2")
        val rows = listOf(row("Acción", current, season2, other))

        val picked = Related.pickRelated(current, rows, genreHint = "Acción", limit = 10)

        assertEquals(listOf("3"), picked.map { it.id })
    }

    @Test
    fun fallsBackToPopularWhenGenreIsUnknown() {
        val current = film("1", "Actual", popularity = 0.0)
        val popular = film("2", "Popular", popularity = 50.0)
        val meh = film("3", "Menos popular", popularity = 1.0)
        val rows = listOf(row("Acción", current, meh), row("Comedia", popular))

        val picked = Related.pickRelated(current, rows, genreHint = "", limit = 10)

        assertEquals("2", picked.first().id)
        assertEquals(listOf("2", "3"), picked.map { it.id })
    }

    @Test
    fun mapsTheKindToItsIndexSection() {
        assertEquals("movies", Related.sectionFor(film("1", "A", kind = "movie")))
        assertEquals("series", Related.sectionFor(film("2", "B", kind = "series")))
        assertEquals("series", Related.sectionFor(film("3", "C", kind = "novela")))
        assertEquals("anime", Related.sectionFor(film("4", "D", kind = "anime")))
        assertEquals("anime", Related.sectionFor(film("5", "E", kind = "kids")))
        assertEquals(
            "series",
            Related.sectionFor(film("6", "F", kind = "", videoUrl = "xtv:6").copy(seasons = listOf(SeasonRef(1, "xtv:6", "xtv:6")))),
        )
    }

    @Test
    fun genreTokensIgnoreAccentsAndNoise() {
        assertEquals(listOf("accion", "aventura"), Related.genreTokens("Acción, Aventura"))
        assertEquals(listOf("ciencia ficcion"), Related.genreTokens("  Ciencia ficción  "))
        assertEquals(emptyList<String>(), Related.genreTokens(" , "))
    }

    @Test
    fun usesTheFilmGenresWhenThereIsNoPortalHint() {
        val current = film("1", "Actual").copy(genres = listOf("Drama"))
        val drama = film("2", "Drama dos", kind = "movie", popularity = 1.0)
        val comedy = film("3", "Comedia", kind = "movie", popularity = 99.0)
        val rows = listOf(row("Drama", drama), row("Comedia", comedy))

        val picked = Related.pickRelated(current, rows, genreHint = "", limit = 10)

        assertEquals("2", picked.first().id)
    }

    @Test
    fun infersTheGenreFromTheRowsThatContainTheTitle() {
        val current = film("1", "The Last of Us", kind = "series")
        val drama = film("2", "Otra serie drama", kind = "series")
        val comedy = film("3", "Comedia", kind = "series", popularity = 99.0)
        val rows = listOf(
            row("Drama", current, drama),
            row("Comedia", comedy),
        )

        assertEquals("Drama", Related.inferGenreHint(current, rows))
        val picked = Related.pickRelated(current, rows, Related.inferGenreHint(current, rows), limit = 10)
        assertEquals("2", picked.first().id)
    }

    @Test
    fun noRowsMeansNoSection() {
        assertTrue(Related.pickRelated(film("1", "A"), emptyList(), "Acción").isEmpty())
    }

    @Test
    fun unplayableCandidatesAreNotOffered() {
        val current = film("1", "Actual")
        val broken = film("2", "Sin URL", videoUrl = "")
        val rows = listOf(row("Acción", broken))
        assertTrue(Related.pickRelated(current, rows, "Acción").isEmpty())
    }
}
