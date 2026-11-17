package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * La barra real a 960×540 dp (el caso 720p/1080p más común) con el chrome al máximo (1.5×):
 * las 6 pestañas siguen compuestas, el sufijo «TV» y el nombre del perfil no se pintan, y
 * Buscar/Perfil quedan como icono/avatar accesibles.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvTopBarCompactTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setBar(
        active: TvTopTab = TvTopTab.HOME,
        landing: LandingFocus? = null,
    ) {
        compose.setContent {
            VisionTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
                    TvTopBar(
                        active = active,
                        onTab = {},
                        onRoute = {},
                        onSearch = {},
                        onSettings = {},
                        profileName = "Principal",
                        onNotifications = {},
                        onReload = {},
                        updatesBadge = 3,
                        landing = landing,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun everyPrimaryTabIsComposedAtTheMaximumChromeScale() {
        setBar()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("Inicio", "En vivo", "Juegos", "Películas", "Series", "Mi lista").forEach { label ->
            val nodes = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("la pestaña «$label» no está compuesta a 960 dp / 1.5×", nodes.isNotEmpty())
            nodes.forEach { node ->
                assertTrue(
                    "«$label» se sale de la barra: ${node.boundsInRoot} vs $root",
                    node.boundsInRoot.right <= root.right + 1f,
                )
            }
        }
    }

    @Test
    fun compactDropsTheDecorativeLabelsButKeepsTheActions() {
        setBar()
        assertEquals(
            "Compact no debe pintar el sufijo TV",
            0,
            compose.onAllNodesWithText("TV", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertEquals(
            "Compact no debe pintar el nombre del perfil",
            0,
            compose.onAllNodesWithText("Principal", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertTrue(
            "el perfil debe seguir accesible como avatar",
            compose.onAllNodes(hasContentDescription("Perfil: Principal"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Buscar debe seguir accesible como icono",
            compose.onAllNodes(hasContentDescription("Buscar"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "la campana debe seguir accesible",
            compose.onAllNodes(hasContentDescription("Notificaciones, 3"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun closingTheLiveHubWithBackReturnsTheFocusToItsPill() {
        setBar()
        compose.onNodeWithContentDescription("TV en vivo y Deportes", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertTrue(
            "el hub no abrió",
            compose.onAllNodesWithText("Agenda y partidos", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )

        compose.onNodeWithText("Agenda y partidos", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertTrue(
            "el hub no se cerró",
            compose.onAllNodesWithText("Agenda y partidos", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "el foco no volvió a la píldora «En vivo»",
            compose.onAllNodes(
                hasContentDescription("TV en vivo y Deportes") and isFocused(),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun closingTheActiveHubAlsoRestoresTheLandingAnchor() {
        val landing = LandingFocus()
        setBar(active = TvTopTab.LIVE, landing = landing)
        compose.runOnIdle {
            assertTrue("el landing no quedó adjunto a la píldora activa", landing.attached)
        }

        compose.onNodeWithContentDescription("TV en vivo y Deportes", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        compose.onNodeWithText("Agenda y partidos", useUnmergedTree = true)
            .performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertTrue(
            "el foco no volvió al landing de la píldora activa",
            compose.onAllNodes(
                hasContentDescription("TV en vivo y Deportes") and isFocused(),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
