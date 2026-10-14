package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.SeasonRef
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.Navigator
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.TvApp
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every TV screen lands the D-pad focus on something, and the D-pad moves it. Runs the real TV
 * shell on Robolectric, over rows any source could give.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val rows = listOf(
        CatalogRow("destacadas", "Destacadas", (1..6).map { film(it) }),
        CatalogRow("series", "Series", (7..9).map { film(it) }),
    )

    private fun film(i: Int) = Film(
        id = "f$i",
        title = "Título $i",
        year = 1950 + i,
        durationMin = 60,
        synopsis = "Sinopsis $i",
        posterUrl = "",
        videoUrl = "https://example.com/v$i.m3u8",
        sourceUrl = "test",
    )

    /** Una serie real (2+ temporadas): la ficha muestra el panel de temporadas. */
    private fun seriesFilm(i: Int) = film(i).copy(
        seasons = listOf(
            SeasonRef(1, "s${i}1", "https://example.com/s${i}1"),
            SeasonRef(2, "s${i}2", "https://example.com/s${i}2"),
        ),
    )
    private val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)

    private lateinit var nav: Navigator

    private fun start(vararg stack: Route): Navigator = startWith(rows, *stack)

    private lateinit var catalog: XuperCatalog

    private fun startWith(seedRows: List<CatalogRow>, vararg stack: Route): Navigator {
        nav = Navigator(stack.toList())
        catalog = XuperCatalog(ApplicationProvider.getApplicationContext())
        catalog.seed(seedRows)
        compose.setContent { VisionTheme { TvApp(catalog, nav) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return nav
    }

    private fun focusedCount() = compose.onAllNodes(focused, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertLanded(where: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("$where: nothing focused", focusedCount() >= 1)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun back() {
        // Igual que el BackHandler de TvApp (misma recomposición y rescate de foco).
        compose.runOnIdle { nav.pop() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    @org.junit.Before
    fun setUp() {
        FocusRescue.target = null
        FocusRescue.screenHasFocus = false
        FocusRescue.dialogsOpen = 0
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testLiveChannels = listOf(
            LiveChannel("Noticias 24", "xtvlive:c1", null, "Noticias"),
            LiveChannel("Deportes Total", "xtvlive:c2", null, "Deportes"),
            LiveChannel("Cine Accion", "xtvlive:c3", null, "Cine"),
            LiveChannel("Kids Club", "xtvlive:c4", null, "Infantil"),
        )
        com.chronosbvrx.visionplus.data.LiveStore(app).cacheChannels(
            com.chronosbvrx.visionplus.data.MAGIS_PLAYLIST_ID,
            testLiveChannels,
        )
    }

    @Test
    fun homeToFilmToPlayerAndBack() {
        val nav = start(Route.Home)
        assertLanded("Inicio")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue(nav.current is Route.Title)
        assertLanded("Ficha")
        press(Key.DirectionCenter)
        assertTrue(nav.current is Route.Player)
        assertLanded("Reproductor")
        back()
        assertTrue(nav.current is Route.Title)
        assertLanded("Ficha tras reproductor")
    }

    @Test
    fun homeSeriesBackWithLateContentStillKeepsFocus() {
        // En gama baja el Inicio pinta sus filas en segundo plano (curado + pintado) y al
        // volver de una ficha el contenido puede tardar más que los reintentos: el mando no
        // puede quedarse muerto por eso.
        val seriesRows = listOf(
            CatalogRow("destacadas", "Destacadas", (1..6).map { seriesFilm(it) }),
        )
        val nav = startWith(seriesRows, Route.Home)
        assertLanded("Inicio")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)

        // Al volver, el Inicio todavía no tiene sus filas (como cuando el curado sigue corriendo).
        compose.runOnIdle { catalog.seed(emptyList()) }
        back()
        assertEquals("Al volver debe regresar a Home", Route.Home, nav.current)
        compose.mainClock.advanceTimeBy(6_000) // se agotan los reintentos de antaño
        compose.waitForIdle()

        // Llegan las filas: el foco debe caer solo, sin tocar el mando.
        compose.runOnIdle { catalog.seed(seriesRows) }
        compose.waitUntil(timeoutMillis = 5_000) { focusedCount() >= 1 }
        assertTrue("screenHasFocus debe reflejar el foco real", FocusRescue.screenHasFocus)
        assertLanded("Inicio con filas llegadas tarde tras volver de la serie")
        press(Key.DirectionDown)
        assertTrue("Inicio tras serie: el foco se perdió al bajar", focusedCount() >= 1)
        press(Key.DirectionRight)
        assertTrue("Inicio tras serie: el foco se perdió al navegar", focusedCount() >= 1)
    }

    @Test
    fun filmBackKeepsFocus() {
        val nav = start(Route.Home)
        assertLanded("Inicio")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue(nav.current is Route.Title)
        assertLanded("Ficha")
        back()
        assertTrue("back should return Home, is ${nav.current}", nav.current == Route.Home)
        assertLanded("Inicio tras ficha")
        press(Key.DirectionDown)
        assertTrue("Inicio: focus lost after Down", focusedCount() >= 1)
    }

    @Test
    fun homeToSeriesFichaAndBackKeepsFocus() {
        // El caso reportado: abrir la ficha de una SERIE desde el Inicio y volver con Atrás
        // dejaba el mando sin foco. Se recorre el flujo real (el OK de la tarjeta es el que
        // registra el id de retorno), con todas las fichas siendo series con temporadas.
        val seriesRows = listOf(
            CatalogRow("destacadas", "Destacadas", (1..6).map { seriesFilm(it) }),
            CatalogRow("series", "Series", (7..9).map { seriesFilm(it) }),
        )
        val nav = startWith(seriesRows, Route.Home)
        assertLanded("Inicio")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)
        assertLanded("Ficha de serie")
        back()
        assertEquals("Al volver debe regresar a Home", Route.Home, nav.current)
        assertLanded("Inicio tras ficha de serie")
        press(Key.DirectionDown)
        assertTrue("Inicio tras serie: el foco se perdió al bajar", focusedCount() >= 1)
        press(Key.DirectionRight)
        assertTrue("Inicio tras serie: el foco se perdió al navegar", focusedCount() >= 1)
    }

    @Test
    fun homeDownToFilmAndBackKeepsFocus() {
        val nav = start(Route.Home)
        assertLanded("Inicio")
        // Bajamos del Billboard Hero hacia las filas de contenido y abrimos
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir ficha o ruta seleccionada", nav.current != Route.Home)
        back()
        assertEquals("Al volver debe regresar a Home", Route.Home, nav.current)
        assertLanded("Inicio tras volver de contenido")
        press(Key.DirectionRight)
        assertTrue("Inicio: debe mantener foco interactivo al navegar tras volver", focusedCount() >= 1)
    }

    @Test
    fun categoriesBackKeepsFocus() {
        val nav = start(Route.Home, Route.Categories)
        assertLanded("Categorías")
        press(Key.DirectionCenter)
        assertTrue("Debe abrir ficha de película", nav.current is Route.Title)
        back()
        assertEquals("Al volver debe regresar a Categorías", Route.Categories, nav.current)
        assertLanded("Categorías tras volver de ficha")
        press(Key.DirectionRight)
        assertTrue("Categorías: foco no debe perderse", focusedCount() >= 1)
    }

    @Test
    fun seriesBackKeepsFocus() {
        // Ficha de serie con temporadas (no-XTV para no tocar red): al salir con
        // Atrás el mando sigue en algo, aunque la tarjeta exacta no componga.
        val series = film(7).copy(
            seasons = listOf(
                SeasonRef(1, "s71", "https://example.com/s71"),
                SeasonRef(2, "s72", "https://example.com/s72"),
            ),
        )
        val nav = start(Route.Home, Route.Search)
        assertLanded("Buscar")
        compose.runOnIdle { nav.push(Route.Title(series)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue(nav.current is Route.Title)
        assertLanded("Ficha de serie")
        back()
        assertTrue("back should return Search, is ${nav.current}", nav.current is Route.Search)
        assertLanded("Buscar tras ficha")
        press(Key.DirectionDown)
        assertTrue("Buscar: focus lost after Down", focusedCount() >= 1)
    }

    @Test
    fun railOpensFromHome() {
        val nav = start(Route.Home)
        assertLanded("Inicio")
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        assertTrue("rail should open a section, is ${nav.current}", nav.current != Route.Home)
    }

    @Test
    fun everyPlaceholderScreenLands() {
        val nav = start(Route.Home)
        listOf(Route.Search, Route.Categories, Route.Library, Route.Live, Route.Sports, Route.Settings, Route.About).forEach { route ->
            compose.runOnIdle { nav.push(route) }
            assertLanded(route.toString())
            press(Key.DirectionDown)
            assertTrue("$route: focus lost after Down", focusedCount() >= 1)
            compose.runOnIdle { nav.pop() }
            assertLanded("Inicio back from $route")
        }
    }

    @Test
    fun liveRailListAndTabsKeepFocus() {
        start(Route.Home, Route.Live)
        assertLanded("En vivo")
        // Left from the list reaches the category rail; OK picks a category without losing focus.
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        assertTrue("En vivo: focus lost after picking a category", focusedCount() >= 1)
        // Up through the rail reaches the provider tabs; Right walks them.
        repeat(8) { press(Key.DirectionUp) }
        press(Key.DirectionRight)
        assertTrue("En vivo: focus lost in the tabs row", focusedCount() >= 1)
        press(Key.DirectionDown)
        assertTrue("En vivo: focus lost coming back down", focusedCount() >= 1)
    }

    @Test
    fun settingsTabsAllRender() {
        start(Route.Home, Route.Settings)
        assertLanded("Ajustes")
        compose.onAllNodesWithText("Ajustes").assertCountEquals(1)
    }

    @Test
    fun focusRescueStackRestoresParentTargetOnDismiss() {
        val parentLanding = LandingFocus()
        val modalLanding = LandingFocus()

        FocusRescue.pushTarget(parentLanding)
        assertEquals(parentLanding, FocusRescue.target)

        FocusRescue.pushTarget(modalLanding)
        assertEquals(modalLanding, FocusRescue.target)

        FocusRescue.popTarget(modalLanding)
        assertEquals("Al cerrar modal, el target debe regresar al landing de la pantalla padre", parentLanding, FocusRescue.target)

        FocusRescue.popTarget(parentLanding)
        org.junit.Assert.assertNull(FocusRescue.target)
    }

    @Test
    fun sportsModalDismissRestoresDpadFocus() {
        val parentLanding = LandingFocus()
        val modalLanding = LandingFocus()

        // Simulamos la apertura del modal de canales sobre Fútbol
        FocusRescue.pushTarget(parentLanding)
        FocusRescue.pushTarget(modalLanding)
        FocusRescue.dialogsOpen++
        FocusRescue.screenHasFocus = true
        assertEquals(modalLanding, FocusRescue.target)
        assertTrue("Con modal abierto, rescue no debe intervenir", !FocusRescue.rescue())

        // Simulamos el descarte del modal mediante tecla Atrás
        FocusRescue.popTarget(modalLanding)
        FocusRescue.dialogsOpen--
        FocusRescue.screenHasFocus = false

        assertEquals("El target activo debe ser el landing padre de la pantalla", parentLanding, FocusRescue.target)
        assertTrue("Al descartar modal, FocusRescue debe poder rescatar el foco inmediatamente", FocusRescue.rescue())
    }
}
