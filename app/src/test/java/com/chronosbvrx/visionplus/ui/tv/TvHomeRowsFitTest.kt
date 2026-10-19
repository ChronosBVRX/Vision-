package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compone tres filas reales del Inicio con el tamaño de una tele 1080p (540 dp) y comprueba que la
 * tercera queda DENTRO de la pantalla. Es el guardián del pedido: al menos 3 filas visibles.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvHomeRowsFitTest {
    @get:Rule
    val compose = createComposeRule()

    private fun film(row: Int) = Film(
        id = "fila-$row",
        title = "Pelicula $row",
        year = 2026,
        durationMin = 90,
        synopsis = "Sinopsis $row",
        posterUrl = "https://example.com/poster-$row.jpg",
        videoUrl = "https://example.com/video-$row.m3u8",
        sourceUrl = "test",
    )

    @Test
    fun threeCatalogRowsFitOnTheTvScreen() {
        compose.setContent {
            VisionTheme {
                Column(Modifier.fillMaxSize()) {
                    repeat(TV_VISIBLE_CATALOG_ROWS) { row ->
                        HomePosterRow(
                            title = "Fila $row",
                            films = listOf(film(row)),
                            onFocusFilm = {},
                            onOpenFilm = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val rows = (0 until TV_VISIBLE_CATALOG_ROWS).map { row ->
            compose.onAllNodesWithText("Pelicula $row", useUnmergedTree = true).fetchSemanticsNodes()
        }
        rows.forEachIndexed { row, nodes ->
            assertEquals("la fila $row debe estar compuesta", 1, nodes.size)
        }

        val density = ApplicationProvider.getApplicationContext<android.content.Context>()
            .resources.displayMetrics.density
        val screenBottom = 540f * density
        val rowBottomGap = tvRowMetrics(540).bottomGap.value * density
        val thirdRowBottom = rows.last().single().boundsInRoot.bottom + rowBottomGap
        assertTrue(
            "la tercera fila termina en ${thirdRowBottom}px y la pantalla en ${screenBottom}px",
            thirdRowBottom <= screenBottom,
        )
    }
}
