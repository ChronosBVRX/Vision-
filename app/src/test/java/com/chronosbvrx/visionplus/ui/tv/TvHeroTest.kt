package com.chronosbvrx.visionplus.ui.tv

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.banners.HomeBanner
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El hero de Vision TV mezcla la foto del VPS con los partidos de GeeSports. */
class TvHeroTest {

    private fun banner(
        id: String = "banner:1",
        title: String = "Spider-Man: Un nuevo día",
        series: Boolean = false,
        ref: String = "xtv:1",
        videoUrl: String = "xtv:1",
    ) = HomeBanner(
        id = id,
        title = title,
        poster = "https://img/$id.jpg",
        backdrop = "https://img/$id-bd.jpg",
        synopsis = "sinopsis",
        year = 2026,
        rating = 8.4,
        durationMin = 120,
        series = series,
        ref = ref,
        videoUrl = videoUrl,
        badge = "DESTACADO HOY",
        eyebrow = "PELÍCULA DESTACADA",
    )

    private fun film(id: String = "xtv:1", title: String = "Spider-Man: Un nuevo día") = Film(
        id = id,
        title = title,
        year = 2026,
        durationMin = 120,
        synopsis = "sinopsis del catálogo",
        posterUrl = "https://img/poster.jpg",
        videoUrl = id,
        sourceUrl = "xtv",
        score = 8.4,
        kind = "movie",
    )

    private fun event(
        id: String = "evt-1",
        live: Boolean = true,
        status: String = "in",
        detail: String = "67'",
        league: String = "Liga MX",
        dateIso: String = "2026-10-26",
        time: String = "20:00:00",
    ) = GeeSportsSource.GeeEvent(
        id = id,
        category = "Football",
        name = "América vs Cruz Azul",
        logo = "",
        teamA = "América",
        teamB = "Cruz Azul",
        dateIso = dateIso,
        dateText = "Hoy",
        time = time,
        alive = 2,
        total = 3,
        links = emptyList(),
        homeName = "América",
        awayName = "Cruz Azul",
        homeLogo = "https://img/america.png",
        awayLogo = "https://img/cruzazul.png",
        leagueLabel = league,
        leagueLogo = "https://img/ligamx.png",
        leagueId = "mex.1",
        scoreHome = 2,
        scoreAway = 1,
        live = live,
        detail = detail,
        status = status,
    )

    @Test
    fun aLiveMatchOpensTheTvHeroAndTheMovieFollows() {
        val live = event()
        val now = geeStartMs(live) + 30 * 60 * 1000L
        val items = buildTvHeroItems(
            banners = listOf(banner()),
            events = listOf(live),
            catalogFilms = listOf(film()),
            nowMs = now,
        )
        assertEquals("sports:evt-1", items.first().key)
        assertTrue(items.first().live)
        assertTrue(items.any { it.film != null && it.title == "Spider-Man: Un nuevo día" })
    }

    @Test
    fun aFinishedMatchNeverStaysInTheTvHero() {
        val done = event(live = false, status = "post", detail = "FT")
        val now = geeStartMs(done) + 30 * 60 * 1000L
        val items = buildTvHeroItems(
            banners = listOf(banner()),
            events = listOf(done),
            catalogFilms = listOf(film()),
            nowMs = now,
        )
        assertFalse(items.any { it.event != null })
        assertEquals(1, items.size)
    }

    @Test
    fun aSeriesBannerIsNotUsedInTheTvHero() {
        val items = buildTvHeroItems(
            banners = listOf(banner(id = "banner:series", series = true, ref = "xtv:H", videoUrl = "xtv:H")),
            events = emptyList(),
            catalogFilms = emptyList(),
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun theBannerUsesTheCatalogFilmWhenItExists() {
        val items = buildTvHeroItems(
            banners = listOf(banner()),
            events = emptyList(),
            catalogFilms = listOf(film()),
        )
        val movie = items.single()
        assertEquals("sinopsis del catálogo", movie.film?.synopsis)
        assertEquals("https://img/banner:1-bd.jpg", movie.imageUrl)
        assertTrue(movie.meta.contains("2026"))
        assertTrue(movie.meta.contains("8.4"))
        assertTrue(movie.meta.contains("2 h"))
    }

    @Test
    fun withoutCatalogTheBannerStillBuildsAMinimalFilm() {
        val items = buildTvHeroItems(
            banners = listOf(banner(ref = "xtv:NEW", videoUrl = "xtv:NEW")),
            events = emptyList(),
            catalogFilms = emptyList(),
        )
        val movie = items.single()
        assertEquals("xtv:NEW", movie.film?.id)
        assertEquals("xtv:NEW", movie.film?.videoUrl)
        assertEquals("PELÍCULA DESTACADA", movie.eyebrow)
    }

    @Test
    fun ligaMxRanksAboveEuropeanLeagues() {
        assertTrue(tvLeagueRank("Liga MX") < tvLeagueRank("LaLiga"))
        assertTrue(tvLeagueRank("UEFA Champions League") < tvLeagueRank("Premier League"))
    }

    @Test
    fun theMatchStartUsesMexicoCityTime() {
        val start = geeStartMs(event(dateIso = "2026-10-26", time = "20:00:00"))
        assertTrue(start > 0)
        assertEquals(30 * 60 * 1000L, geeStartMs(event(dateIso = "2026-10-26", time = "20:30:00")) - start)
    }
}
