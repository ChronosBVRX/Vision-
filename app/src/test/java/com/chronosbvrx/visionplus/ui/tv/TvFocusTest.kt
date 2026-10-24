package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertFalse
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

    private fun focusedLabel(): String? {
        fun labelOf(node: androidx.compose.ui.semantics.SemanticsNode): String? {
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()?.let { return it }
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.let { return it }
            node.children.forEach { child -> labelOf(child)?.let { return it } }
            return null
        }
        return compose.onAllNodes(focused, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.let(::labelOf)
    }

    /** La etiqueta de fila inmediatamente arriba de [y], si esa fila sigue compuesta. */
    private fun rowLabelAbove(y: Float, labels: List<String>): String? =
        labels.mapNotNull { label ->
            compose.onAllNodesWithText(label).fetchSemanticsNodes().firstOrNull()
                ?.boundsInRoot?.top?.let { label to it }
        }.filter { it.second < y }.maxByOrNull { it.second }?.first

    /** Centro del nodo enfocado (para confirmar que el foco vuelve a la MISMA tarjeta). */
    private fun focusedCenter(): androidx.compose.ui.geometry.Offset? =
        compose.onAllNodes(focused, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.center

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
        // La caché en memoria de la fuente deportiva cruza clases de test: se limpia para
        // que el estado inicial de Deportes sea el de la tele en frío (sin datos).
        com.chronosbvrx.visionplus.data.gee.GeeSportsSource.resetMemoryForTests()
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
    fun theTvRailPaintsTheSportsPillar() {
        // El riel de la tele PINTA «Deportes en vivo» (cerrado, cada icono lleva su etiqueta como
        // descripción). Se mide sobre la app TV real: es la ruta estable en la suite completa.
        start(Route.Home)
        assertLanded("Inicio")
        val pintado = compose
            .onAllNodes(androidx.compose.ui.test.hasContentDescription("Deportes en vivo", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("el riel de la tele no pinta «Deportes en vivo»", pintado >= 1)
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
    fun homeReloadAfterAReturnKeepsThePadAlive() {
        // Flujo del caso reportado: se vuelve al Inicio con un regreso pendiente y el catálogo
        // se recarga ENTERO con ids nuevos (la película terminó y su fila se rehízo): la
        // tarjeta que tenía el foco desaparece y el mando no puede quedarse muerto. El tamaño
        // del catálogo no cambia: la llave vieja del regreso —el tamaño del pool— no se mueve.
        val films = (1..20).map {
            film(it).copy(
                posterUrl = "https://example.com/p$it.jpg",
                score = 7.0 + (it % 5) * 0.5,
                voteCount = 500 + it * 10,
            )
        }
        val rows = listOf(CatalogRow("destacadas", "Destacadas", films))
        val target = com.chronosbvrx.visionplus.data.Popularity
            .homeCurated(rows, emptyList()).top10.getOrNull(1)?.id
        assertTrue("el Top 10 debe tener al menos 2 tarjetas", target != null)
        catalog = XuperCatalog(ApplicationProvider.getApplicationContext())
        catalog.seed(rows)
        compose.setContent {
            VisionTheme {
                CompositionLocalProvider(com.chronosbvrx.visionplus.ui.LocalReducedEffects provides true) {
                    TvHomeScreen(
                        catalog = catalog,
                        railItems = listOf(TvRailItem(Icons.Default.Home, "Inicio", {})),
                        returnFilmId = target,
                        onOpenFilm = {},
                        onOpenLive = {},
                        onOpenCategories = {},
                        onResume = {},
                    )
                }
            }
        }
        // El curado llega en segundo plano y el regreso asienta: el mando queda vivo.
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        assertTrue("el mando debe estar vivo antes de la recarga", focusedCount() >= 1)
        // El catálogo se recarga entero con ids nuevos (la película terminó y su fila se rehízo):
        // la tarjeta que tenía el foco desaparece. Mismo tamaño: la llave vieja no se mueve.
        compose.runOnIdle {
            catalog.seed(listOf(CatalogRow("destacadas", "Destacadas", (1..20).map { film(200 + it) })))
        }
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertTrue("el mando se quedó sin foco al desaparecer la tarjeta enfocada", focusedCount() >= 1)
        press(Key.DirectionRight)
        assertTrue("el D-pad murió al desaparecer la tarjeta enfocada", focusedCount() >= 1)
    }

    @Test
    fun categoriesDeepRowSeriesBackRestoresSameCard() {
        // Fila lejana con scroll horizontal: la tarjeta que abrió la serie NO está compuesta
        // si el scroll no se restaura. El foco debe volver a esa misma tarjeta (no a otra).
        val indexLike = listOf(
            CatalogRow("tendencias", "Tendencias", (1..30).map { seriesFilm(it) }),
            CatalogRow("recomendados", "Recomendados", (31..60).map { seriesFilm(it) }),
        )
        val nav = startWith(indexLike, Route.Home, Route.Categories)
        assertLanded("Categorías")
        // Nos vamos a la derecha lo suficiente para que la fila tenga scroll horizontal
        repeat(12) { press(Key.DirectionRight) }
        val before = focusedCenter()
        assertTrue("Debe haber una tarjeta enfocada antes de abrir", before != null)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)
        back()
        assertEquals("Al volver debe regresar a Categorías", Route.Categories, nav.current)
        assertLanded("Categorías tras volver")
        val after = focusedCenter()
        assertTrue("Categorías: el foco se perdió al volver", after != null)
        assertTrue(
            "El foco debe volver a la MISMA tarjeta (antes=$before, después=$after)",
            after != null && before != null && (after - before).getDistance() < 4f,
        )
    }

    @Test
    fun categoriesRecomendadosSeriesBackKeepsFocus() {
        // El caso reportado: serie abierta desde una fila del «catálogo» (Categorías → Recomendados)
        // con el mando; al volver con Atrás la navegación debe seguir viva.
        val indexLike = listOf(
            CatalogRow("tendencias", "Tendencias", (1..5).map { seriesFilm(it) }),
            CatalogRow("recomendados", "Recomendados", (6..9).map { seriesFilm(it) }),
        )
        val nav = startWith(indexLike, Route.Home, Route.Categories)
        assertLanded("Categorías")
        press(Key.DirectionDown)   // de la primera fila a «Recomendados»
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)
        back()
        assertEquals("Al volver debe regresar a Categorías", Route.Categories, nav.current)
        assertLanded("Categorías tras volver de la serie")
        press(Key.DirectionRight)
        assertTrue("Categorías: el foco se perdió al navegar", focusedCount() >= 1)
        press(Key.DirectionUp)
        assertTrue("Categorías: el foco se perdió al subir", focusedCount() >= 1)
    }

    /** Escribe la caché de una sección (el archivo que lee `RemoteIndexSource.cachedSection`). */
    private fun writeSectionCache(name: String, rows: List<CatalogRow>) {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rowsJson = org.json.JSONArray()
        rows.forEach { row ->
            val filmsJson = org.json.JSONArray()
            row.films.forEach { f ->
                filmsJson.put(org.json.JSONObject().apply {
                    put("id", f.id)
                    put("title", f.title)
                    put("year", f.year)
                    put("durationMin", f.durationMin)
                    put("synopsis", f.synopsis)
                    put("posterUrl", f.posterUrl)
                    put("videoUrl", f.videoUrl)
                    put("sourceUrl", f.sourceUrl)
                    put("score", f.score)
                    put("popularity", f.popularity)
                    put("voteCount", f.voteCount)
                    if (f.seasons.isNotEmpty()) {
                        val seasonsJson = org.json.JSONArray()
                        f.seasons.forEach { s ->
                            seasonsJson.put(org.json.JSONObject().apply {
                                put("n", s.n)
                                put("titleId", s.titleId)
                                put("videoUrl", s.videoUrl)
                            })
                        }
                        put("seasons", seasonsJson)
                    }
                })
            }
            rowsJson.put(org.json.JSONObject().apply {
                put("id", row.id)
                put("title", row.title)
                put("films", filmsJson)
            })
        }
        java.io.File(app.filesDir, "remote_section_$name.json").writeText(rowsJson.toString())
    }

    @Test
    fun seriesFromSectionRecommendedRowBackKeepsTheSameCard() {
        // El caso reportado en la tele: serie abierta desde la fila «Recomendado para ti» de la
        // pantalla de sección (Series). Esa fila se dibujaba SIN objetivo de regreso, así que al
        // volver con Atrás el foco caía en el héroe (o se perdía): ahora debe volver a la MISMA
        // tarjeta. El nombre de sección es propio para que el refresco de red no pise el fixture.
        val sectionRows = listOf(
            CatalogRow("xtv-series-estrenos", "Estrenos de Series", (1..8).map { seriesFilm(it) }),
            CatalogRow("xtv-series-genero-drama", "Drama", (11..18).map { seriesFilm(it) }),
            CatalogRow("xtv-series-genero-accion", "Acción", (21..28).map { seriesFilm(it) }),
        )
        writeSectionCache("pruebaregreso", sectionRows)
        val nav = startWith(emptyList(), Route.Home, Route.Section("pruebaregreso", "Series"))
        assertLanded("Series")

        // Baja hasta que aparezca la fila «Recomendado para ti» y el foco esté en su tarjeta.
        var labelTop: Float? = null
        var guard = 0
        while (guard++ < 12) {
            val labels = compose.onAllNodesWithText("Recomendado para ti").fetchSemanticsNodes()
            val c = focusedCenter()
            if (labels.isNotEmpty()) {
                labelTop = labels.first().boundsInRoot.top
                if (c != null && c.y > labelTop) break
            }
            press(Key.DirectionDown)
        }
        val top = labelTop
        assertTrue("Debe existir la fila «Recomendado para ti»", top != null)
        val before = focusedCenter()
        val beforeLabel = focusedLabel()
        assertTrue("Debe haber una tarjeta enfocada en «Recomendado para ti»", before != null && top != null && before.y > top)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)
        assertLanded("Ficha de serie")
        back()
        assertEquals("Al volver debe regresar a Series", Route.Section("pruebaregreso", "Series"), nav.current)
        assertLanded("Series tras volver de la serie")
        val after = focusedCenter()
        assertTrue(
            "El foco debe volver a la MISMA tarjeta (antes=$before, $beforeLabel, después=$after, etiqueta=${focusedLabel()})",
            after != null && before != null && (after - before).getDistance() < 8f,
        )
        press(Key.DirectionRight)
        assertTrue("Series: el mando debe seguir vivo", focusedCount() >= 1)
    }

    @Test
    fun seriesFromSectionRowDeepRightBackKeepsTheSameCard() {
        // Variante del caso reportado: la serie estaba al final de la fila (scroll horizontal).
        // Al volver, la fila se desplaza sola y el foco cae en ESA tarjeta, no en la primera.
        val sectionRows = listOf(
            CatalogRow("xtv-series-estrenos", "Estrenos de Series", (1..15).map { seriesFilm(it) }),
            CatalogRow("xtv-series-genero-drama", "Drama", (21..40).map { seriesFilm(it) }),
        )
        writeSectionCache("pruebaregreso2", sectionRows)
        val nav = startWith(emptyList(), Route.Home, Route.Section("pruebaregreso2", "Series"))
        assertLanded("Series")

        // Baja a la fila «Recomendado para ti» y avanza a la derecha hasta el final de la fila.
        var labelTop: Float? = null
        var guard = 0
        while (guard++ < 12) {
            val labels = compose.onAllNodesWithText("Recomendado para ti").fetchSemanticsNodes()
            val c = focusedCenter()
            if (labels.isNotEmpty()) {
                labelTop = labels.first().boundsInRoot.top
                if (c != null && c.y > labelTop) break
            }
            press(Key.DirectionDown)
        }
        repeat(10) { press(Key.DirectionRight) }
        val before = focusedCenter()
        assertTrue("Debe haber una tarjeta enfocada", before != null)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)
        back()
        assertEquals(Route.Section("pruebaregreso2", "Series"), nav.current)
        assertLanded("Series tras volver")
        val after = focusedCenter()
        assertTrue(
            "El foco debe volver a la MISMA tarjeta (antes=$before, después=$after, etiqueta=${focusedLabel()})",
            after != null && before != null && (after - before).getDistance() < 8f,
        )
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
    fun homeBackRestoresSameCardAfterLateContent() {
        // El caso real de la tele: al volver de la ficha, el Inicio rearma sus filas tarde
        // (curado en segundo plano sobre otra estructura) y la tarjeta puede quedar fuera de
        // pantalla. El regreso debe revelar su fila verticalmente y enfocar LA MISMA tarjeta,
        // no "cualquier foco", y el mando debe seguir vivo después.
        val seriesRows = listOf(
            CatalogRow("destacadas", "Destacadas", (1..6).map { seriesFilm(it) }),
            CatalogRow("series", "Series", (7..12).map { seriesFilm(it) }),
        )
        val nav = startWith(seriesRows, Route.Home)
        assertLanded("Inicio")
        // Bajar hasta la fila «Destacadas» y abrir una tarjeta que NO sea el héroe (f2):
        // la del héroe cae en su botón por diseño; aquí se prueba la tarjeta de la fila.
        var labelTop: Float? = null
        var guard = 0
        while (guard++ < 14) {
            val labels = compose.onAllNodesWithText("Destacadas").fetchSemanticsNodes()
            val c = focusedCenter()
            if (labels.isNotEmpty()) {
                labelTop = labels.first().boundsInRoot.top
                if (c != null && c.y > labelTop) break
            }
            press(Key.DirectionDown)
        }
        assertTrue("Debe verse la fila «Destacadas»", labelTop != null)
        press(Key.DirectionRight)
        val before = focusedCenter()
        val beforeLabel = focusedLabel()
        val rowLabels = listOf(
            "Top 10 en Vision+ Hoy", "Novedades y Estrenos", "Populares de la semana",
            "Seguir viendo", "Destacadas", "Series",
        )
        val beforeRow = before?.let { rowLabelAbove(it.y, rowLabels) }
        assertTrue("Debe haber una tarjeta enfocada antes de abrir", before != null)
        press(Key.DirectionCenter)
        assertTrue("Debe abrir la ficha de la serie", nav.current is Route.Title)

        // Al volver, el Inicio todavía no tiene sus filas (curado en curso en gama baja).
        compose.runOnIdle { catalog.seed(emptyList()) }
        back()
        assertEquals("Al volver debe regresar a Home", Route.Home, nav.current)
        // Llegan las filas tarde: el foco debe caer solo en LA MISMA tarjeta. La fila puede
        // quedar revelada en otra posición vertical (antes 2 filas, ahora 3), así que la
        // identidad se comprueba por título —y por fila cuando su etiqueta sigue compuesta—,
        // no por el píxel exacto.
        compose.runOnIdle { catalog.seed(seriesRows) }
        compose.waitUntil(timeoutMillis = 5_000) { focusedCount() >= 1 }
        val after = focusedCenter()
        val afterLabel = focusedLabel()
        val afterRow = after?.let { rowLabelAbove(it.y, rowLabels) }
        assertTrue(
            "El foco debe volver a la MISMA tarjeta (antes=$before/$beforeLabel/$beforeRow, " +
                "después=$after/$afterLabel/$afterRow)",
            after != null && beforeLabel != null && afterLabel == beforeLabel &&
                (beforeRow == null || afterRow == null || beforeRow == afterRow),
        )
        press(Key.DirectionDown)
        assertTrue("Inicio: el foco se perdió al bajar", focusedCount() >= 1)
        press(Key.DirectionUp)
        assertTrue("Inicio: el foco se perdió al subir", focusedCount() >= 1)
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
        // La 2.5.143 cambió el título por un riel de categorías dentro del shell (barra superior).
        assertLanded("Reproducción")
        compose.onAllNodesWithText("AJUSTES").assertCountEquals(1)
        compose.onAllNodesWithText("Reproducción").assertCountEquals(1)
        compose.onAllNodesWithText("Sistema y Rendimiento").assertCountEquals(1)
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
        val parentLanding = LandingFocus().apply { attached = true }
        val modalLanding = LandingFocus().apply { attached = true }

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
        // Al descartar el modal el rescate ya puede intervenir: el candidato es el padre
        // (la verificación del aterrizaje real se cubre componiendo, en los tests de arriba).
        assertEquals(
            "Al descartar modal, el rescate debe tener como candidato al landing padre",
            listOf(parentLanding),
            rescueCandidates(listOf(parentLanding), FocusRescue.dialogsOpen, FocusRescue.screenHasFocus),
        )
        assertFalse("Sin composición real no se come la tecla", FocusRescue.rescue())
        FocusRescue.popTarget(parentLanding)
    }

    @Test
    fun rescueSkipsADeadTargetAndLandsOnARealOne() {
        // El caso que dejaba el mando muerto: en la pila del rescate había un destino
        // marcado como compuesto cuyo nodo ya no existía (el riel en una pantalla que no
        // es Inicio, el héroe con la lista desplazada). El rescate debe SALTARLO, caer en
        // el destino compuesto de abajo y consumir la tecla solo por haber movido el foco.
        start(Route.Home)
        assertLanded("Inicio")
        val dead = LandingFocus().apply { attached = true } // nunca compuesto
        compose.runOnIdle { FocusRescue.pushTarget(dead) }
        var rescued = false
        compose.runOnIdle {
            FocusRescue.screenHasFocus = false
            rescued = FocusRescue.rescue()
        }
        assertTrue("el rescate debe caer en el destino compuesto de abajo", rescued)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertTrue("algo debe quedar enfocado tras el rescate", focusedCount() >= 1)
        compose.runOnIdle { FocusRescue.popTarget(dead) }
    }

    @Test
    fun categoryChipEntersTheGridWithThePadAlive() {
        // Pulsar OK en un chip cambia al modo cuadrícula: la fila de chips se RE-CREA y el
        // héroe desaparece, así que el foco moría ahí (el landing del héroe ya no compone).
        // Con el aterrizaje del modo nuevo, el mando sigue vivo al entrar y al volver.
        writeSectionCache("movies", rows)
        val nav = startWith(rows, Route.Home, Route.Section("movies", "Películas"))
        assertLanded("Sección")
        // Del héroe a la fila de chips; camina los chips hasta un género y entra a la cuadrícula.
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertTrue("al entrar a la cuadrícula el mando quedó muerto", focusedCount() >= 1)
        press(Key.DirectionDown)
        assertTrue("cuadrícula: el foco se perdió al navegar", focusedCount() >= 1)
        press(Key.DirectionRight)
        assertTrue("cuadrícula: el foco se perdió al moverse", focusedCount() >= 1)
        // Atrás vuelve al feed de destacados sin dejar el mando muerto.
        compose.runOnIdle { nav.pop() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertLanded("Inicio tras la sección")
    }

    @Test
    fun sportsEntryKeepsThePadAlive() {
        // Deportes en vivo es la pantalla más expuesta: en frío la agenda llega tarde y su
        // mosaico puede estar vacío. Antes se quedaba sin destino de foco (el mando parecía
        // muerto); ahora el aterrizaje cae en el primer chip compuesto y, cuando la agenda
        // llega, se re-arma solo. Aquí se exige lo invariante: el mando SIEMPRE está vivo.
        val nav = start(Route.Home)
        compose.runOnIdle { nav.push(Route.Sports) }
        assertLanded("Deportes")
        press(Key.DirectionDown)
        assertTrue("Deportes: el foco se perdió al bajar", focusedCount() >= 1)
        press(Key.DirectionRight)
        assertTrue("Deportes: el foco se perdió al navegar", focusedCount() >= 1)
    }
}
