package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeGuide
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.ui.sports.GeeLinkPickDialog
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El selector de transmisiones es el punto donde la 2.5.93/2.5.94 tumbaba la
 * pantalla de Deportes: la guía trae entradas distintas («Canal 5» y «Canal 5
 * Televisa», «ESPN» y «ESPN+») que resuelven al MISMO canal, y el `LazyColumn`
 * reventaba con clave repetida. Este guardián compone el modal con duplicados
 * directos: si alguna clave vuelve a colisionar, truena y el test falla.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class GeePickDialogDuplicateTest {

    @get:Rule
    val compose = createComposeRule()

    private fun link(name: String) = GeeSportsSource.GeeLink(
        name = name,
        link = "https://cdn.test/$name.m3u8",
        headers = emptyMap(),
        type = "hls",
        drm = "",
        alive = true,
    )

    private fun option(title: String, score: Int = 100) = GeeGuide.TvOption(
        guideName = title,
        title = title,
        link = link(title),
        score = score,
    )

    @Before
    fun setUp() {
        FocusRescue.target = null
        FocusRescue.screenHasFocus = false
        FocusRescue.dialogsOpen = 0
    }

    @Test
    fun tvPickerSurvivesDuplicateChannelsAndLinks() {
        val duplicated = link("Canal 5")
        compose.setContent {
            VisionTheme {
                TvGeeLinkPickDialog(
                    title = "América vs Guadalajara",
                    subtitle = "Liga MX · Hoy 19:00",
                    links = listOf(duplicated, duplicated),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    guide = listOf("Canal 5", "Canal 5 Televisa"),
                    geeTv = listOf(option("Canal 5"), option("Canal 5")),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Canales en vivo:")
        assertText("Transmisiones en vivo:")
    }

    @Test
    fun phonePickerSurvivesDuplicateChannelsAndLinks() {
        val duplicated = link("ESPN")
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "LaLiga",
                    subtitle = "Hoy 21:00",
                    links = listOf(duplicated, duplicated),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    guide = listOf("ESPN", "ESPN+"),
                    geeTv = listOf(option("ESPN"), option("ESPN")),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Canales en vivo:")
        assertText("Transmisiones en vivo:")
    }

    private fun assertText(text: String) {
        val matches = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("no aparece «$text»", matches >= 1)
    }
}
