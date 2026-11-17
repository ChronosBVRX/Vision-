package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Bounds del Hero en Full (1280×720): una sola columna de 50 % (tope 560 dp), título de
 * 36 sp y sinopsis de 14 sp, sin invadir la zona visual derecha.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-land-television")
class TvHeroFullBoundsTest {

    @get:Rule
    val compose = createComposeRule()

    private val synopsis = "Sinopsis larga de prueba para medir el ancho de la columna del Hero."

    private fun film(i: Int) = Film(
        id = "hero-$i",
        title = "Hero Film $i",
        year = 2026,
        durationMin = 100,
        synopsis = synopsis,
        posterUrl = "",
        videoUrl = "https://example.com/v$i.m3u8",
        sourceUrl = "test",
        score = 8.0 + i * 0.1,
        voteCount = 1_000 + i,
    )

    @Test
    fun theHeroTextColumnStaysInsideTheLeft50Percent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val catalog = XuperCatalog(context)
        catalog.seed(listOf(CatalogRow("destacadas", "Destacadas", (1..6).map(::film))))
        compose.setContent {
            VisionTheme {
                TvHomeScreen(
                    catalog = catalog,
                    railItems = emptyList(),
                    onOpenFilm = {},
                    onOpenLive = {},
                    onOpenCategories = {},
                    onResume = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        val expectedWidth = tvHeroTextWidthDp(1280f - 96f, full = true) * density
        val maxRight = root.width * 0.50f

        val title = compose.onAllNodes(hasText("Hero Film", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .minByOrNull { it.boundsInRoot.top }
        assertTrue("no se compuso el título del Hero", title != null)
        assertTrue(
            "el título mide ${title!!.boundsInRoot.width}px y debía caber en ${expectedWidth}px",
            title.boundsInRoot.width <= expectedWidth + 2f,
        )
        assertTrue(
            "el título termina en ${title.boundsInRoot.right}px y el límite es ${maxRight}px",
            title.boundsInRoot.right <= maxRight,
        )
        val synopsisNode = compose.onAllNodesWithText(synopsis, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
        assertTrue("no se compuso la sinopsis del Hero", synopsisNode != null)
        assertTrue(
            "la sinopsis mide ${synopsisNode!!.boundsInRoot.width}px y debía caber en ${expectedWidth}px",
            synopsisNode.boundsInRoot.width <= expectedWidth + 2f,
        )
        assertEquals(
            "la columna de texto debe empezar en el padding de 48 dp",
            48f * density,
            synopsisNode.boundsInRoot.left,
            2f,
        )

        assertTrue(
            "Full debe conservar la etiqueta «Favorito»",
            compose.onAllNodesWithText("Favorito", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
