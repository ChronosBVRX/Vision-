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
    fun externalOpenReplacesThePlayerInProgressInsteadOfStacking() {
        // El caso real: estás viendo algo y el control remoto manda otro título. El play
        // viejo YA NO se apila (si no, al volver se re-disparaba y cada orden dejaba basura).
        val nav = Navigator(listOf(Route.Home))
        val first = Route.Player(film("old"))
        nav.pushPlayer(first)
        assertEquals(listOf(Route.Home, first), nav.stack.toList())
        val second = Route.Player(film("new"))
        nav.pushPlayer(second)
        assertEquals("el reproductor nuevo reemplaza al viejo", listOf(Route.Home, second), nav.stack.toList())
        assertTrue("el cierre del nuevo vuelve a Inicio, jamás al play viejo", nav.close(second))
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun externalOpenReplacesALivePlayerTooAndKeepsTheScreensBelow() {
        val nav = Navigator(listOf(Route.Home, Route.Title(film("t"))))
        val ch = LiveChannel("TUDN", "xtvlive:tudn", null, "Deportes")
        nav.pushPlayer(Route.LivePlayer(ch, channelCodes = listOf("tudn"), groupTitle = "Deportes"))
        val moved = Route.Player(film("movie"))
        nav.pushPlayer(moved)
        assertEquals(
            "la pantalla de abajo (la ficha) se conserva; solo el reproductor se reemplaza",
            listOf(Route.Home, Route.Title(film("t")), moved),
            nav.stack.toList(),
        )
    }

    @Test
    fun staleHandoffCloseCannotUndoTheNewPlay() {
        // El «el reproductor se cerró» de la pantalla de espera VIEJA llegaba también cuando
        // el deep link del teléfono traía el menú al frente: sin la guarda, deshacía la
        // reproducción nueva y dejaba el menú con el audio viejo de fondo.
        val nav = Navigator(listOf(Route.Home))
        val old = Route.Player(film("old"))
        nav.pushPlayer(old)
        val fresh = Route.Player(film("new"))
        nav.pushPlayer(fresh)
        assertFalse("el cierre del handoff viejo no toca la pantalla nueva", nav.close(old))
        assertEquals(fresh, nav.current)
        assertTrue("solo el dueño de la pantalla puede cerrarla", nav.close(fresh))
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun aPlayerOpenedFromTheLauncherExitsTheAppInsteadOfShowingTheMenu() {
        val nav = Navigator(listOf(Route.Home))
        val player = Route.Player(film("home"), launcherOrigin = true)
        nav.pushPlayer(player)
        var exited = false
        assertTrue(closePlayback(player, nav) { exited = true })
        assertTrue("la app debe soltar su tarea para que vuelva el launcher", exited)
        assertEquals(
            "la ruta del reproductor no se desapila: la tarea entera se va",
            listOf(Route.Home, player),
            nav.stack.toList(),
        )
    }

    @Test
    fun aLivePlayerOpenedFromTheLauncherAlsoReturnsToTheLauncher() {
        val nav = Navigator(listOf(Route.Home))
        val ch = LiveChannel("Canal 5", "xtvlive:cyx_5", null, "México")
        val live = Route.LivePlayer(ch, channelCodes = listOf("cyx_5"), launcherOrigin = true)
        nav.pushPlayer(live)
        var exited = false
        assertTrue(closePlayback(live, nav) { exited = true })
        assertTrue(exited)
    }

    @Test
    fun aPlayerOpenedInsideTheAppKeepsItsNormalBackBehavior() {
        val nav = Navigator(listOf(Route.Home))
        val player = Route.Player(film("inside"))
        nav.pushPlayer(player)
        var exited = false
        assertTrue(closePlayback(player, nav) { exited = true })
        assertFalse("los players de la propia app no cierran la tarea", exited)
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun closeNeverEmptiesTheStackBelowHome() {
        val nav = Navigator(listOf(Route.Home))
        assertFalse(nav.close(Route.Home))
        assertFalse(nav.close(Route.Player(film("x"))))
        assertEquals(Route.Home, nav.current)
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
