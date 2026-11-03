package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.ui.TV_RAIL_SPECS
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El riel de la tele tiene que caber completo en 960×540 dp (1080p a 320 dpi): con la entrada
 * «Juegos» son ocho destinos y, si se recortan, la sección queda inalcanzable con el mando.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvRailFitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyRailDestinationIsOnScreenAt540dp() {
        compose.setContent {
            VisionTheme {
                Box(Modifier.fillMaxSize()) {
                    TvSideRail(
                        items = TV_RAIL_SPECS.map { TvRailItem(it.icon, it.label, onClick = {}) },
                        bottomItems = listOf(TvRailItem(Icons.Default.Settings, "Opciones", onClick = {})),
                    )
                }
            }
        }
        compose.waitForIdle()
        TV_RAIL_SPECS.forEach { spec ->
            compose.onNodeWithContentDescription(spec.label, substring = true, useUnmergedTree = true)
                .assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Opciones", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
