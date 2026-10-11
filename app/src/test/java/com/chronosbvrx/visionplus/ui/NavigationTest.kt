package com.chronosbvrx.visionplus.ui

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión de navegación: el back stack es la única forma de moverse entre
 * Home/Ficha/Reproductor/TV. Si esto se rompe, el D-pad o el botón atrás
 * dejan pantallas colgadas o duplicadas.
 */
class NavigationTest {

    private fun film(id: String = "f1") = Film(
        id = id, title = "T", year = 2026, durationMin = 90, synopsis = "s",
        posterUrl = "p", videoUrl = "xtv:$id", sourceUrl = "xtv",
    )

    @Test
    fun pushIgnoresDuplicates() {
        val nav = Navigator(listOf(Route.Home))
        nav.push(Route.Home)
        assertEquals(1, nav.stack.size)
        nav.push(Route.Search)
        nav.push(Route.Search)
        assertEquals(listOf(Route.Home, Route.Search), nav.stack.toList())
    }

    @Test
    fun popKeepsAtLeastHome() {
        val nav = Navigator(listOf(Route.Home))
        assertFalse(nav.pop())
        assertEquals(Route.Home, nav.current)
        nav.push(Route.Title(film()))
        assertTrue(nav.pop())
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun goTabResetsToHomePlusSection() {
        val nav = Navigator(listOf(Route.Home, Route.Title(film()), Route.Player(film())))
        nav.goTab(Route.Live)
        assertEquals(listOf(Route.Home, Route.Live), nav.stack.toList())
        nav.goTab(Route.Home)
        assertEquals(listOf(Route.Home), nav.stack.toList())
    }

    @Test
    fun titlePlayerAndLivePlayerCarryTheirPayload() {
        val f = film("xtv:9")
        val nav = Navigator(listOf(Route.Home, Route.Title(f)))
        assertEquals(Route.Title(f), nav.current)
        nav.push(Route.Player(f))
        assertEquals(f.videoUrl, (nav.current as Route.Player).film.videoUrl)
        val ch = LiveChannel("TUDN", "xtvlive:tudn", null, "Deportes")
        nav.push(Route.LivePlayer(ch, channelCodes = listOf("tudn", "espn"), groupTitle = "Deportes"))
        val liveRoute = nav.current as Route.LivePlayer
        assertEquals("xtvlive:tudn", liveRoute.channel.url)
        assertEquals(listOf("tudn", "espn"), liveRoute.channelCodes)
        assertEquals("Deportes", liveRoute.groupTitle)
    }

    @Test
    fun playerCarriesEpisodeContext() {
        val f = film("xtv:9")
        val ep = EpisodePlay(
            code = "e2", titleSuffix = " · C2", seriesId = "xtv:9",
            episodeIds = listOf("e1", "e2"), episodeIndex = 1,
            seasonIds = listOf("xtv:9"), seasonNames = listOf("T1"), seasonIndex = 0,
        )
        val nav = Navigator(listOf(Route.Home, Route.Title(f)))
        nav.push(Route.Player(f, ep))
        val cur = nav.current as Route.Player
        assertEquals("e2", cur.episode?.code)
        assertEquals(1, cur.episode?.episodeIndex)
        assertEquals(listOf("e1", "e2"), cur.episode?.episodeIds)
        // Sin capítulo: el play normal de película.
        nav.push(Route.Player(f))
        assertEquals(null, (nav.current as Route.Player).episode)
    }

    @Test
    fun sectionCarriesNameAndTitle() {
        val nav = Navigator(listOf(Route.Home))
        nav.push(Route.Section("series", "Series"))
        val cur = nav.current as Route.Section
        assertEquals("series", cur.name)
        assertEquals("Series", cur.title)
    }

    @Test
    fun resumeRouteKeepsEpisodeContext() {
        val card = com.chronosbvrx.visionplus.data.ResumeCard(
            entry = com.chronosbvrx.visionplus.data.ResumeEntry(
                key = "e2", title = "Serie · C2", xtvCode = "e2", seriesId = "s",
                episodeIds = listOf("e1", "e2"), episodeIndex = 1, positionMs = 120_000,
            ),
            film = null,
            progress = 0.2f,
        )
        val route = resumeRoute(card)
        assertEquals("xtv:e2", route.film.videoUrl)
        assertEquals("e2", route.episode?.code)
        assertEquals(120_000L, route.episode?.resumeMs)
        assertEquals(1, route.episode?.episodeIndex)
    }

    @Test
    fun resumeRouteReopensDirectUrls() {
        val card = com.chronosbvrx.visionplus.data.ResumeCard(
            entry = com.chronosbvrx.visionplus.data.ResumeEntry(
                key = "https://x/v.mp4", title = "Doc", videoUrl = "https://x/v.mp4",
                positionMs = 60_000,
            ),
            film = null,
            progress = 0.1f,
        )
        val route = resumeRoute(card)
        assertEquals("https://x/v.mp4", route.film.videoUrl)
        assertEquals(60_000L, route.resumeMs)
        assertEquals(null, route.episode)
    }
}
