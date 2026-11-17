package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * En una tele ancha (1280×720 dp) se recuperan las etiquetas: nombre del perfil, «TV» y la
 * píldora «Buscar». Las 6 pestañas siguen ahí.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-land-television")
class TvTopBarFullTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fullTvShowsTheLabelsAndKeepsEveryTab() {
        compose.setContent {
            VisionTheme {
                TvTopBar(
                    active = TvTopTab.HOME,
                    onTab = {},
                    onRoute = {},
                    onSearch = {},
                    onSettings = {},
                    profileName = "Principal",
                )
            }
        }
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("Inicio", "En vivo", "Juegos", "Películas", "Series", "Mi lista").forEach { label ->
            val nodes = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("la pestaña «$label» no está compuesta en Full", nodes.isNotEmpty())
            nodes.forEach { node ->
                assertTrue(
                    "«$label» se sale de la barra: ${node.boundsInRoot} vs $root",
                    node.boundsInRoot.right <= root.right + 1f,
                )
            }
        }
        listOf("TV", "Principal", "Buscar").forEach { label ->
            assertTrue(
                "Full debe mostrar «$label»",
                compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }
}
