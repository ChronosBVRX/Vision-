package com.chronosbvrx.visionplus.data

import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.Airing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Filas personales del Inicio:
 * - «Porque viste X» reusa Related con el último recuerdo y nunca repite la semilla,
 *   lo de «Seguir viendo» ni mezcla cine con series.
 * - «Tus series · Esta semana» cruza la agenda con las series seguidas, una por serie,
 *   en orden y sin inventar fichas que el catálogo no tiene.
 */
class HomePersonalTest {

    private fun film(
        id: String,
        title: String = id,
        kind: String = "movie",
        genres: List<String> = emptyList(),
        tmdbId: Int = 0,
        popularity: Double = 0.0,
        year: Int = 2020,
        videoUrl: String = "xtv:$id",
    ) = Film(
        id = id,
        title = title,
        year = year,
        durationMin = 100,
        synopsis = "",
        posterUrl = "p-$id",
        videoUrl = videoUrl,
        sourceUrl = "xtv",
        popularity = popularity,
        tmdbId = tmdbId,
        kind = kind,
        genres = genres,
    )

    private fun row(title: String, vararg films: Film) =
        CatalogRow(id = title.lowercase().replace(' ', '-'), title = title, films = films.toList())

    private fun seed(code: String, title: String) = ResumeEntry(
        key = code,
        title = title,
        xtvCode = code,
        positionMs = 60_000,
        durationMs = 3_600_000,
        updatedAt = 1,
    )

    private fun airing(
        show: String,
        titleId: String,
        season: Int = 1,
        episode: Int = 1,
        airtimeMs: Long,
        imdb: String = "",
    ) = Airing(
        show = show,
        season = season,
        episode = episode,
        title = "",
        airtimeMs = airtimeMs,
        imdb = imdb,
        titleId = titleId,
    )

    /** Jueves 22 de octubre de 2026, 18:00 en el centro de México (00:00 UTC del 23). */
    private val now: Long =
        OffsetDateTime.parse("2026-10-22T18:00:00-06:00").toInstant().toEpochMilli()

    // ------------------------------------------------------------ «Porque viste X»

    @Test
    fun becauseUsesTheLastWatchedAndPicksTheSameGenre() {
        val current = film("1", "Shōgun", kind = "series", genres = listOf("Drama"), tmdbId = 10)
        val related = (2..8).map {
            film("$it", "Drama $it", kind = "series", tmdbId = 100 + it, popularity = it.toDouble())
        }
        val comedy = film("9", "Comedia", kind = "series", popularity = 99.0)
        val rows = listOf(
            row("Drama", current, *related.toTypedArray()),
            row("Comedia", comedy),
        )
        val all = rows.flatMap { it.films }

        val out = HomePersonal.becauseYouWatched(
            seed = seed("1", "Shōgun · T1 · C1"),
            films = all,
            rows = rows,
        )

        assertEquals("el nombre de la serie, no el del capítulo", "Shōgun", out?.title)
        assertTrue(
            "la del mismo género va primero",
            out?.films?.firstOrNull()?.id in related.map { it.id },
        )
        assertEquals("la comedia queda al final", "9", out?.films?.lastOrNull()?.id)
        assertFalse("la semilla no se repite", out?.films?.any { it.id == "1" } == true)
        assertTrue("hay tarjetas suficientes", (out?.films?.size ?: 0) >= HomePersonal.MIN_CARDS)
    }

    @Test
    fun becauseDisappearsWithoutASeedOrEnoughCandidates() {
        val current = film("1", "Actual", tmdbId = 10)
        val two = listOf(film("2", "Dos", tmdbId = 11), film("3", "Tres", tmdbId = 12))
        val rows = listOf(row("Drama", current, *two.toTypedArray()))
        val all = rows.flatMap { it.films }

        assertNull("sin historial no hay fila", HomePersonal.becauseYouWatched(null, all, rows))
        assertNull(
            "con menos de 6 candidatos no se pinta",
            HomePersonal.becauseYouWatched(seed("1", "Actual"), all, rows),
        )
    }

    @Test
    fun becauseHonorsKidsModeAndExcludesWhatIsAlreadyOnScreen() {
        val kids = film("1", "Infantil", kind = "kids", tmdbId = 10)
        val candidates = (2..10).map { film("$it", "Niños $it", kind = "kids", tmdbId = 100 + it) }
        val adult = film("20", "Adulto", kind = "movie", tmdbId = 200)
        val rows = listOf(row("Infantil", kids, *candidates.toTypedArray()), row("Acción", adult))
        val all = rows.flatMap { it.films }

        val kidsRow = HomePersonal.becauseYouWatched(
            seed = seed("1", "Infantil"),
            films = all,
            rows = rows,
            kidsOnly = true,
        )
        assertTrue("solo tarjetas infantiles", kidsRow?.films?.all { it.kind == "kids" } == true)

        val adultSeed = film("30", "Serie adulta", kind = "series", tmdbId = 300)
        val adultRows = listOf(row("Drama", adultSeed, *candidates.toTypedArray()))
        assertNull(
            "en modo niños una semilla adulta no genera fila",
            HomePersonal.becauseYouWatched(
                seed = seed("30", "Serie adulta"),
                films = adultRows.flatMap { it.films },
                rows = adultRows,
                kidsOnly = true,
            ),
        )

        // «Seguir viendo» no se repite: ni por id ni por la misma obra (tmdbId).
        val excluded = HomePersonal.becauseYouWatched(
            seed = seed("1", "Infantil"),
            films = all,
            rows = rows,
            excludeIds = setOf("2"),
            excludeTmdb = setOf(103),
            kidsOnly = true,
        )
        assertFalse(excluded?.films?.any { it.id == "2" } == true)
        assertFalse(excluded?.films?.any { it.tmdbId == 103 } == true)
    }

    @Test
    fun becauseTitleStripsTheEpisodeMark() {
        assertEquals("Shōgun", HomePersonal.becauseTitle(seed("x", "Shōgun · T2 · C5")))
        assertEquals("Spider-Man", HomePersonal.becauseTitle(seed("y", "Spider-Man")))
        assertEquals(
            "Relacionada",
            HomePersonal.becauseTitle(seed("z", "Relacionada · T1")),
        )
    }

    @Test
    fun becauseFallsBackToTheTitleWhenTheOldIdIsGone() {
        val current = film("nuevo", "Película X", tmdbId = 10)
        val related = (2..8).map { film("$it", "Otra $it", tmdbId = 100 + it) }
        val rows = listOf(row("Drama", current, *related.toTypedArray()))

        val out = HomePersonal.becauseYouWatched(
            seed = seed("id-viejo", "Película X"),
            films = rows.flatMap { it.films },
            rows = rows,
        )

        assertNotNull("el nombre exacto sigue siendo semilla", out)
        assertEquals("Película X", out?.title)
    }

    @Test
    fun becauseDoesNotMixMoviesWithSeries() {
        val series = film("1", "Serie", kind = "series", tmdbId = 10)
        val seriesCandidates = (2..7).map { film("$it", "Serie $it", kind = "series", tmdbId = 100 + it) }
        val movie = film("20", "Película", kind = "movie", tmdbId = 200)
        val rows = listOf(row("Drama", series, *seriesCandidates.toTypedArray(), movie))

        val out = HomePersonal.becauseYouWatched(
            seed = seed("1", "Serie · T1 · C1"),
            films = rows.flatMap { it.films },
            rows = rows,
        )

        assertNotNull(out)
        assertTrue("solo series para una semilla de serie", out!!.films.all { it.kind == "series" })
    }

    // ------------------------------------------------- «Tus series · Esta semana»

    @Test
    fun weeklyFollowedKeepsOnePerSeriesInDateOrderAndSkipsUnknown() {
        val s1 = film("s1", "Serie uno", kind = "series")
        val s2 = film("s2", "Serie dos", kind = "series")
        val films = listOf(s1, s2)
        val soon = now + 2L * 60 * 60 * 1000       // hoy 20:00
        val tomorrow = now + 26L * 60 * 60 * 1000  // mañana 20:00
        val airings = listOf(
            airing("Serie dos", "s2", 1, 3, tomorrow, imdb = "tt2"),
            airing("Serie uno", "s1", 1, 1, soon),
            airing("Serie uno", "s1", 1, 2, soon + 60 * 60 * 1000),
            airing("Desconocida", "s9", 1, 1, soon),
        )

        val out = HomePersonal.weeklyFollowed(
            followed = setOf("s1", "tt2"),
            airings = airings,
            films = films,
            nowMs = now,
        )

        assertEquals("una tarjeta por serie, en orden", listOf("s1", "s2"), out.map { it.film.id })
        assertEquals("T1 · C1 · Hoy 20:00", out[0].label)
        assertTrue(out[0].today)
        assertEquals("T1 · C3 · Mañana 20:00", out[1].label)
        assertFalse(out[1].today)
    }

    @Test
    fun weeklyFollowedIgnoresPastFarAwayAndUnscheduledAirings() {
        val s1 = film("s1", "Serie", kind = "series")
        val airings = listOf(
            airing("Serie", "s1", 1, 1, now - 3L * 60 * 60 * 1000),
            airing("Serie", "s1", 1, 2, now + 8L * 24 * 60 * 60 * 1000),
            airing("Serie", "s1", 1, 3, 0L),
        )
        assertTrue(
            HomePersonal.weeklyFollowed(setOf("s1"), airings, listOf(s1), now).isEmpty(),
        )
        assertTrue(
            "sin series seguidas no hay consulta",
            HomePersonal.weeklyFollowed(emptySet(), airings, listOf(s1), now).isEmpty(),
        )
        assertTrue(
            "sin fichas no se inventan tarjetas",
            HomePersonal.weeklyFollowed(setOf("s1"), airings, emptyList(), now).isEmpty(),
        )
    }

    @Test
    fun airingLabelFallsBackToWeekdayAndEpisodeOnly() {
        val threeDays = now + 3L * 24 * 60 * 60 * 1000
        val label = HomePersonal.airingLabel(
            airing("Serie", "s1", 2, 4, threeDays),
            now,
        )
        assertTrue("temporada y capítulo", label.startsWith("T2 · C4 · "))
        assertTrue("hora de México", label.endsWith("18:00"))
        assertFalse("ya no dice Hoy", label.startsWith("Hoy"))
        assertFalse(label.startsWith("Mañana"))

        val noTime = HomePersonal.airingLabel(airing("Serie", "s1", 2, 4, 0L), now)
        assertEquals("T2 · C4", noTime)
    }
}
