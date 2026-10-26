package com.chronosbvrx.visionplus.home.data

import com.chronosbvrx.visionplus.home.contract.HeroItem
import com.chronosbvrx.visionplus.home.contract.MediaKind
import com.chronosbvrx.visionplus.home.contract.SportsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El hero mezcla dos fuentes: los banners diarios del VPS y los partidos de la agenda.
 * Un partido en vivo manda; el arte de cine llena el resto y nada se repite.
 */
class HeroSlideTest {

    private val now = 1_761_530_400_000L // 2026-10-26 20:00 en CDMX (UTC-6)

    private fun movie(id: String, title: String = id) = HeroSlide(
        id = id,
        title = title,
        backdrop = "https://img/$id.jpg",
        poster = "https://img/$id-poster.jpg",
        playLink = "visionplus://watch/$id",
        openLink = "visionplus://title/$id",
    )

    private fun sport(
        id: String,
        startMs: Long,
        live: Boolean = false,
        status: String = "",
        league: String = "Liga MX",
    ) = SportsItem(
        id = id,
        name = "América vs Cruz Azul",
        category = "Football",
        league = league,
        homeName = "América",
        awayName = "Cruz Azul",
        homeScore = 2,
        awayScore = 1,
        detail = if (live) "67'" else "Hoy 20:00",
        live = live,
        status = status,
        startMs = startMs,
    )

    @Test
    fun aLiveMatchOpensTheHeroAndTheMoviesFollow() {
        val live = sport("live-1", now - 30 * 60 * 1000L, live = true)
        val slides = composeHeroSlides(
            banners = listOf(movie("m1"), movie("m2"), movie("m3")),
            feedHero = emptyList(),
            sports = listOf(live),
            nowMs = now,
        )
        assertEquals("sports:live-1", slides.first().id)
        assertTrue(slides.first().live)
        assertTrue(slides.any { it.id == "m1" })
        assertTrue(slides.any { it.id == "m2" })
    }

    @Test
    fun aFinishedMatchNeverStaysInTheHero() {
        val old = sport("old", now - 4 * 60 * 60 * 1000L, status = "post")
        val slides = composeHeroSlides(
            banners = listOf(movie("m1")),
            feedHero = emptyList(),
            sports = listOf(old),
            nowMs = now,
        )
        assertFalse(slides.any { it.id == "sports:old" })
        assertEquals(listOf("m1"), slides.map { it.id })
    }

    @Test
    fun theUpcomingMatchGoesAtTheEnd() {
        val soon = sport("soon", now + 2 * 60 * 60 * 1000L)
        val slides = composeHeroSlides(
            banners = listOf(movie("m1"), movie("m2")),
            feedHero = emptyList(),
            sports = listOf(soon),
            nowMs = now,
        )
        assertEquals("sports:soon", slides.last().id)
        assertFalse(slides.last().live)
    }

    @Test
    fun withoutServerBannersTheFeedHeroStillFillsTheCarousel() {
        val feed = listOf(
            HeroItem(
                ref = "xtv:1",
                title = "Spider-Man",
                poster = "https://img/spider.jpg",
                kind = MediaKind.MOVIE,
                playLink = "visionplus://watch/xtv%3A1",
            ),
            HeroItem(ref = "xtv:2", title = "House", kind = MediaKind.SERIES, series = true),
        )
        val slides = composeHeroSlides(
            banners = emptyList(),
            feedHero = feed,
            sports = emptyList(),
            nowMs = now,
        )
        assertEquals(listOf("feed:xtv:1", "feed:xtv:2"), slides.map { it.id })
        assertEquals("DESTACADO HOY", slides.first().eyebrow)
        assertEquals("SERIE DESTACADA", slides.last().eyebrow)
        assertEquals("Spider-Man", slides.first().title)
    }

    @Test
    fun noSourceLeavesAnEmptyHeroInsteadOfAFakeCard() {
        assertTrue(composeHeroSlides(emptyList(), emptyList(), emptyList(), now).isEmpty())
    }

    @Test
    fun theTwoLiveMatchesAreSeparedByMovies() {
        val live1 = sport("live-1", now - 10 * 60 * 1000L, live = true)
        val live2 = sport("live-2", now - 20 * 60 * 1000L, live = true, league = "LaLiga")
        val slides = composeHeroSlides(
            banners = listOf(movie("m1"), movie("m2"), movie("m3")),
            feedHero = emptyList(),
            sports = listOf(live2, live1),
            nowMs = now,
        )
        assertEquals("sports:live-1", slides[0].id) // Liga MX primero
        assertEquals("m1", slides[1].id)
        assertEquals("m2", slides[2].id)
        assertEquals("sports:live-2", slides[3].id)
    }

    @Test
    fun theMatchCarriesTheScoreboardAndTheEventLink() {
        val slide = sportsSlide(sport("m:1", now - 10 * 60 * 1000L, live = true), now)!!
        assertTrue(slide.sports)
        assertEquals("EN VIVO", slide.badge)
        assertEquals("67'", slide.detail)
        assertEquals(2, slide.homeScore)
        assertEquals(1, slide.awayScore)
        assertEquals(
            "visionplus://open/sports?event=m%3A1",
            slide.openLink,
        )
        assertTrue(slide.eyebrow.contains("EN VIVO"))
    }

    @Test
    fun theDayAndTimeSpeakMexicoCity() {
        assertEquals("20:00", heroTimeLabel(now, now))
        assertEquals("Hoy", heroDayLabel(now, now))
        assertEquals("Mañana", heroDayLabel(now + 24 * 60 * 60 * 1000L, now))
    }

    @Test
    fun ligaMxRanksAboveTheEuropeanLeaguesForTheHero() {
        assertTrue(leagueRank("Liga MX") < leagueRank("LaLiga"))
        assertTrue(leagueRank("UEFA Champions League") < leagueRank("Premier League"))
    }
}
