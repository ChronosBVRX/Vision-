package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.ui.sports.GeeLinkPickDialog
import com.chronosbvrx.visionplus.ui.player.AudioAndSubtitlesDialog
import com.chronosbvrx.visionplus.ui.player.PlayerMenuState
import com.chronosbvrx.visionplus.ui.player.PlayerTrackChoice
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Transmisiones de GeeSports:")
        assertAbsent("Canales posibles:")
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
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Transmisiones de GeeSports:")
        assertAbsent("Canales posibles:")
    }

    @Test
    fun phonePickerBlocksAStartedLaterMatchAndOnlyShowsWhereItAirs() {
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Liverpool vs Arsenal",
                    subtitle = "Premier League · Hoy 21:00",
                    links = listOf(link("ESPN")),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    playable = false,
                    blockNote = "Aún no comienza · podrás verlo cuando el partido esté en vivo.",
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // Con el partido aún cerrado no se ofrecen señales; solo el aviso.
        assertText("podrás verlo cuando el partido esté en vivo")
        assertAbsent("Transmisiones de GeeSports:")
        assertAbsent("Canales posibles:")
    }

    @Test
    fun tvPickerBlocksAStartedLaterMatchAndOnlyShowsWhereItAirs() {
        compose.setContent {
            VisionTheme {
                TvGeeLinkPickDialog(
                    title = "Liverpool vs Arsenal",
                    subtitle = "Premier League · Hoy 21:00",
                    links = listOf(link("ESPN")),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    playable = false,
                    blockNote = "Aún no comienza · podrás verlo cuando el partido esté en vivo.",
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("podrás verlo cuando el partido esté en vivo")
        assertAbsent("Transmisiones de GeeSports:")
        assertAbsent("Canales posibles:")
    }

    /**
     * La tarjeta de Perfiles (Ajustes móvil) se compone sin truenos y ofrece sus acciones.
     * Se prueba aquí para reusar este sandbox de Compose: una clase nueva con su propio
     * sandbox ha tumbado la suite completa en este proyecto.
     */
    @Test
    fun theProfilesCardComposesWithItsActions() {
        compose.setContent {
            VisionTheme {
                com.chronosbvrx.visionplus.ui.settings.ProfilesSettingsSection()
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("PERFILES")
        assertText("Crear perfil")
        assertText("Principal")
    }

    @Test
    fun onlyGeeSportsLinksAreListedAndThePlayedSignalIsMarked() {
        val espn = link("ESPN")
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Sevilla vs Osasuna",
                    subtitle = "LaLiga · Hoy 14:00",
                    links = listOf(link("FANCODE FHD"), espn),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    playingUrl = espn.link,
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // Solo los enlaces de GeeSports: sin la línea/canal de la guía.
        assertText("FANCODE FHD")
        assertText("ESPN")
        assertText("Viendo")
        assertAbsent("Se transmite en: ESPN")
        assertAbsent("Canal en vivo · ESPN")
        assertAbsent("Canales posibles:")
    }

    private fun assertText(text: String) {
        val matches = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("no aparece «$text»", matches >= 1)
    }

    private fun assertAbsent(text: String) {
        val matches = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("no debía aparecer «$text»", matches == 0)
    }

    /** Texto EXACTO: distingue la sección «Audio» del título «Audio, subtítulos y calidad». */
    private fun assertExactAbsent(text: String) {
        val matches = compose.onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("no debía aparecer la sección «$text»", matches == 0)
    }

    // ----- menú honesto de audio/subtítulos/calidad ------------------------------------------

    private fun trackMenu(audio: Int, subs: Int, heights: List<Int>) = PlayerMenuState().apply {
        audioChoices = (1..audio).map { PlayerTrackChoice("a$it", "Audio pista $it") }
        subtitleChoices = (1..subs).map { PlayerTrackChoice("s$it", "Subtítulo pista $it") }
        videoHeights = heights
    }

    @Test
    fun aVideoWithoutRealTrackOptionsShowsNoSections() {
        compose.setContent {
            VisionTheme {
                AudioAndSubtitlesDialog(trackMenu(0, 0, emptyList()), {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertExactAbsent("Calidad de video")
        assertExactAbsent("Audio")
        assertExactAbsent("Subtítulos")
        assertAbsent("Desactivados")
        assertText("Esta transmisión se reproduce con un solo audio, sin subtítulos y en su resolución nativa.")
    }

    @Test
    fun theTrackMenuShowsTheRealAudioSubtitlesAndResolutions() {
        compose.setContent {
            VisionTheme {
                AudioAndSubtitlesDialog(trackMenu(2, 1, listOf(1080, 720)), {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Calidad de video")
        assertText("1080p FHD")
        assertText("720p HD")
        assertText("Audio pista 2")
        assertText("Subtítulos")
        assertText("Desactivados")
        assertText("Subtítulo pista 1")
    }

    @Test
    fun oneAudioAndOneResolutionAreNotOffered() {
        compose.setContent {
            VisionTheme {
                AudioAndSubtitlesDialog(trackMenu(1, 0, listOf(1080)), {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertExactAbsent("Calidad de video")
        assertExactAbsent("Audio")
        assertExactAbsent("Subtítulos")
        assertAbsent("1080p FHD")
        assertAbsent("Desactivados")
    }

    // ----- reloj del menú (lote «reloj discreto») ---------------------------------------------

    @Test
    fun thePhoneClockIsBareTextWithoutTheZoneTag() {
        // En el móvil la hora va como texto pequeño dentro de la barra: sin la etiqueta «CDMX»
        // (viaja en la descripción accesible) y sin la píldora de la tele.
        val now = com.chronosbvrx.visionplus.ui.VisionClock.timeText()
        compose.setContent {
            VisionTheme { com.chronosbvrx.visionplus.ui.VisionClockText() }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertText(now)
        assertAbsent("CDMX")
    }

    @Test
    fun theTvBadgeKeepsNamingTheZone() {
        val now = com.chronosbvrx.visionplus.ui.VisionClock.timeText()
        compose.setContent {
            VisionTheme { com.chronosbvrx.visionplus.ui.VisionClockBadge() }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertText(now)
        assertText("CDMX")
    }

    private fun deadChannel(name: String): GeeSportsSource.GeeLink = GeeSportsSource.GeeLink(
        name = name,
        link = "https://cdn.test/$name.m3u8",
        headers = emptyMap(),
        type = "hls",
        drm = "",
        alive = false,
    )

    @Test
    fun onlyGeeSportsSourcesAreListedAndADeadOnePlaysItsOwnLink() {
        var played: LiveChannel? = null
        val dead = deadChannel("TUDN MEXICO FHD")
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Guadalajara vs Tigres UANL",
                    subtitle = "Liga MX · Hoy 17:07",
                    links = listOf(dead),
                    onDismiss = {},
                    onPlay = { played = it },
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Transmisiones de GeeSports:")
        assertText("TUDN MEXICO FHD")
        assertText("Caído")
        assertAbsent("Canales posibles:")
        assertAbsent("respaldo automático")
        compose.onNodeWithText("TUDN MEXICO FHD").performClick()
        compose.waitForIdle()
        assertEquals(dead.link, played?.url)
    }

    @Test
    fun aDeclaredNameWithoutUrlIsNotOfferedAtAll() {
        val pending = GeeSportsSource.GeeLink(
            name = "TUDN US",
            link = "",
            headers = emptyMap(),
            type = "hls",
            drm = "",
            alive = null,
        )
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "León vs América",
                    subtitle = "Liga MX · Hoy 19:00",
                    links = listOf(pending),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Sin enlaces disponibles")
        assertAbsent("TUDN US")
        assertAbsent("La fuente aún no publica su señal")
    }

    @Test
    fun theListShowsTheGeeLinkAndTheZappingHint() {
        val live = GeeSportsSource.GeeLink(
            name = "TUDN MEXICO FHD",
            link = "https://otte.cache.aiv-cdn.net/bom-nitro/live/clients/dash/enc/x/out/v1/abc/cenc.mpd|user-agent=UA",
            headers = emptyMap(),
            type = "dash",
            drm = "kid:key",
            alive = true,
        )
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Cruz Azul vs FC Juárez",
                    subtitle = "Liga MX · Hoy 21:10",
                    links = listOf(live),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Transmisiones de GeeSports:")
        assertText("otte.cache.aiv-cdn.net")
        assertAbsent("user-agent=")
        assertText("cambia entre ellas")
        assertText("Arriba/Abajo durante la reproducción")
    }

    @Test
    fun aFreshCheckBeatsTheServersStaleVerdict() {
        // El servidor lo dio por caído hace minutos y la app acaba de comprobarlo vivo.
        val reported = GeeSportsSource.GeeLink(
            name = "TUDN MEXICO FHD",
            link = "https://cdn.test/vivo.m3u8",
            headers = emptyMap(),
            type = "dash",
            drm = "",
            alive = false,
        )
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Santos vs Querétaro",
                    subtitle = "Liga MX · Hoy 17:00",
                    links = listOf(reported),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    checks = mapOf("https://cdn.test/vivo.m3u8" to GeeHealth.Check(alive = true, atMs = 1L)),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Disponible")
        assertAbsent("Caído")
    }

    @Test
    fun aFreshCheckThatFoundItDeadBeatsAHealthyServerVerdict() {
        val reported = GeeSportsSource.GeeLink(
            name = "TUDN MEXICO FHD",
            link = "https://cdn.test/muerto.m3u8",
            headers = emptyMap(),
            type = "dash",
            drm = "",
            alive = true,
        )
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Santos vs Querétaro",
                    subtitle = "Liga MX · Hoy 17:00",
                    links = listOf(reported),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    checks = mapOf("https://cdn.test/muerto.m3u8" to GeeHealth.Check(alive = false, atMs = 1L)),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Caído")
        assertAbsent("Disponible")
    }
}
