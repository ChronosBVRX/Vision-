package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A 720 dp las 6 pestañas no caben. Si la sección activa es «Mi lista», la tira debe
 * desplazarse sola hasta ella cuando el mando está en el contenido: así el `landing` del
 * Atrás encuentra un nodo compuesto y el mando no se queda muerto.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w720dp-h480dp-land-television")
class TvTopBarNarrowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theActiveTabIsScrolledIntoViewAndCanTakeTheFocus() {
        val landing = LandingFocus()
        compose.setContent {
            VisionTheme {
                TvTopBar(
                    active = TvTopTab.LIBRARY,
                    onTab = {},
                    onRoute = {},
                    onSearch = {},
                    onSettings = {},
                    profileName = "Principal",
                    landing = landing,
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val library = compose.onAllNodesWithText("Mi lista", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("la pestaña activa no quedó compuesta", library.isNotEmpty())
        library.forEach { node ->
            assertTrue(
                "«Mi lista» se sale de la barra: ${node.boundsInRoot} vs $root",
                node.boundsInRoot.right <= root.right + 1f,
            )
        }
        compose.runOnIdle {
            assertTrue("el landing de la pestaña activa no quedó adjunto", landing.attached)
        }

        compose.onNodeWithContentDescription("Mi lista y descargas", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertTrue(
            "la pestaña activa no pudo tomar el foco",
            compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
