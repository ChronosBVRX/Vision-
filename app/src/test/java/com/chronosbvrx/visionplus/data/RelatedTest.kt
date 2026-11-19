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
        genres: List<String> = emptyList(),
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
        genres = genres,
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
        assertEquals("movies", Related.sectionFor(film("5", "E", kind = "kids")))
        assertEquals(
            "animación occidental mal etiquetada «anime»",
            "movies",
            Related.sectionFor(film("7", "G", kind = "anime").copy(originalLanguage = "en")),
        )
        assertEquals(
            "anime japonés de verdad",
            "anime",
            Related.sectionFor(film("8", "H", kind = "anime").copy(originalLanguage = "ja")),
        )
        assertEquals(
            "series",
            Related.sectionFor(film("6", "F", kind = "", videoUrl = "xtv:6").copy(seasons = listOf(SeasonRef(1, "xtv:6", "xtv:6")))),
        )
    }

    @Test
    fun genreHintMergesPortalTagsWithAnySeparator() {
        val current = film("1", "Actual", genres = listOf("Animación"))
        assertEquals(
            "la pista conserva los tags; el cruce los normaliza después",
            "Animación, Family, Comedy",
            Related.genreHint(current, "Family / Comedy"),
        )
        assertEquals(
            "Animación, Kids, Adventure",
            Related.genreHint(current, "Kids|Adventure"),
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
    fun englishGenresMatchTheSpanishRowsAndNeverMixHorror() {
        val current = film("1", "Monsters, Inc.", kind = "kids")
        val animation = (2..8).map {
            film(
                "a$it", "Animada $it", popularity = it.toDouble(),
                genres = listOf("Animación", "Familia"),
            )
        }
        val horror = film("20", "Terror", popularity = 999.0, genres = listOf("Terror"))
        val rows = listOf(
            row("Animación", *animation.toTypedArray()),
            row("Terror", horror),
        )

        val picked = Related.pickRelated(
            current, rows, genreHint = "Animation, Family, Comedy", limit = 8,
        )

        assertEquals("solo las de animación", animation.map { it.id }.toSet(), picked.map { it.id }.toSet())
        assertFalse("jamás terror tras una infantil", picked.any { it.id == "20" })
    }

    @Test
    fun neverFillsWithAnotherGenreWhenTheKnownGenreRunsOut() {
        val current = film("1", "Actual", genres = listOf("Documental"))
        val documentary = film("2", "Documental dos", genres = listOf("Documental"))
        val horror = film("3", "Terror", popularity = 99.0, genres = listOf("Terror"))
        val rows = listOf(row("Documental", documentary), row("Terror", horror))

        val picked = Related.pickRelated(current, rows, genreHint = "Documental", limit = 10)

        assertEquals("mejor una sola que una de otro género", listOf("2"), picked.map { it.id })
    }

    @Test
    fun aKidsSeedWithoutGenreStillCannotGetHorror() {
        val current = film("1", "Infantil", kind = "kids")
        val kid = film("2", "Animada", kind = "kids", popularity = 1.0, genres = listOf("Familia"))
        val horror = film("3", "Terror", popularity = 999.0, genres = listOf("Terror"))
        val rows = listOf(row("Terror", horror), row("Animación", kid))

        val picked = Related.pickRelated(current, rows, genreHint = "", limit = 10)

        assertEquals("solo lo infantil", listOf("2"), picked.map { it.id })
    }

    @Test
    fun thePrimaryGenreGoesFirstEvenIfAnotherIsMorePopular() {
        val current = film("1", "Actual", genres = listOf("Animación", "Comedia"))
        val comedy = film("2", "Comedia", popularity = 999.0, genres = listOf("Comedia"))
        val animation = film("3", "Animada", popularity = 1.0, genres = listOf("Animación"))
        val rows = listOf(row("Animación", animation), row("Comedia", comedy))

        val picked = Related.pickRelated(current, rows, genreHint = "", limit = 10)

        assertEquals("manda el género primario", "3", picked.first().id)
    }

    @Test
    fun inferGenreHintUsesTheCatalogGenresBeforeTheRowName() {
        val current = film("1", "Monstros S.A.", kind = "kids")
        val catalog = film(
            "1", "Monstros S.A.", kind = "kids",
            genres = listOf("Animación", "Familia", "Comedia"),
        )
        val rows = listOf(row("Aventuras", catalog))

        assertEquals(
            "los géneros de la ficha son más precisos que la hilera",
            "Animación, Familia, Comedia",
            Related.inferGenreHint(current, rows),
        )
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
