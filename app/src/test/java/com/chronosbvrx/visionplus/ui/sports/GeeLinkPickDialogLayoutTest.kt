package com.chronosbvrx.visionplus.ui.sports

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Al volver de una señal en vivo, el selector reabre la fila marcada como
 * «Seleccionada». Esa fila suma una segunda píldora («Disponible»); si las dos
 * van en horizontal se comen el nombre del canal y la etiqueta «DASH ·
 * ClearKey» se apila letra por letra. La prueba fija el ancho mínimo del nombre
 * y que la etiqueta quepa en una línea.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeeLinkPickDialogLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSelectedRowKeepsTheChannelNameWideAndTheTypeOnOneLine() {
        val name = "FANCORP TELEVISION LATINOAMERICA HD"
        val link = GeeSportsSource.GeeLink(
            name = name,
            link = "https://example.test/dash.mpd",
            headers = emptyMap(),
            type = "dash",
            drm = "clearkey",
            alive = true,
        )
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Fútbol Club Barcelona vs Servette FC Chênois Féminin",
                    subtitle = "UEFA Women's Champions League · Empezó 14:00 · Termina ~16:00",
                    links = listOf(link),
                    onDismiss = {},
                    onPlay = {},
                    playingUrl = link.link,
                )
            }
        }
        compose.waitForIdle()

        val density = compose.density.density
        val title = compose.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode()
        val titleWidthDp = title.boundsInRoot.width / density
        assertTrue(
            "el nombre del canal se queda sin ancho: $titleWidthDp dp",
            titleWidthDp >= 120f,
        )

        val type = compose.onNodeWithText("DASH · ClearKey", useUnmergedTree = true).fetchSemanticsNode()
        val typeHeightDp = type.boundsInRoot.height / density
        assertTrue(
            "la etiqueta DASH se apila: $typeHeightDp dp",
            typeHeightDp <= 24f,
        )
    }
}
