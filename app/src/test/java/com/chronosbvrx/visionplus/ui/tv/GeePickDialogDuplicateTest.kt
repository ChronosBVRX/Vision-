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
import com.chronosbvrx.visionplus.data.gee.GeeBackstop
import com.chronosbvrx.visionplus.data.gee.GeeGuide
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
        assertText("Canales posibles:")
        assertText("Posibles transmisiones:")
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
        assertText("Canales posibles:")
        assertText("Posibles transmisiones:")
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
                    guide = listOf("ESPN"),
                    playable = false,
                    blockNote = "Aún no comienza · podrás verlo cuando el partido esté en vivo.",
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // La guía se conserva (dónde se transmite)…
        assertText("Se transmite en: ESPN")
        assertText("podrás verlo cuando el partido esté en vivo")
        // …pero NO se ofrecen señales para reproducir.
        assertAbsent("Posibles transmisiones:")
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
                    guide = listOf("ESPN"),
                    playable = false,
                    blockNote = "Aún no comienza · podrás verlo cuando el partido esté en vivo.",
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Se transmite en: ESPN")
        assertText("podrás verlo cuando el partido esté en vivo")
        assertAbsent("Posibles transmisiones:")
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

    // ----- respaldo automático ---------------------------------------------------------------

    private val telemundo = LiveChannel(name = "TELEMUNDO EAST HD", url = "xtvlive:tm-east")

    private fun backstop() = GeeBackstop.Backstop(guideName = "Telemundo", channel = telemundo)

    private fun deadChannel(name: String): GeeSportsSource.GeeLink = GeeSportsSource.GeeLink(
        name = name,
        link = "https://cdn.test/$name.m3u8",
        headers = emptyMap(),
        type = "hls",
        drm = "",
        alive = false,
    )

    private fun healthyChannel(name: String): GeeSportsSource.GeeLink = GeeSportsSource.GeeLink(
        name = name,
        link = "https://cdn.test/$name.m3u8",
        headers = emptyMap(),
        type = "hls",
        drm = "",
        alive = true,
    )

    @Test
    fun thePhonePickerShowsOnlySportsSourcesAndTapsADeadOneIntoTheBackstop() {
        var played: LiveChannel? = null
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Guadalajara vs Tigres UANL",
                    subtitle = "Liga MX · Hoy 17:07",
                    links = listOf(link("TUDN MEXICO FHD")),
                    onDismiss = {},
                    onPlay = { played = it },
                    guide = emptyList(),
                    geeTv = listOf(
                        GeeGuide.TvOption(guideName = "Telemundo", title = "Telemundo", link = deadChannel("Telemundo"), score = 100),
                    ),
                    backstops = listOf(backstop()),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // La lista enseña SOLO lo de la fuente deportiva: nada de respaldos a la vista.
        assertText("Canales posibles:")
        assertText("Telemundo")
        assertAbsent("TELEMUNDO EAST HD")
        assertAbsent("Respaldo en tu lista:")
        // Pero la fila caída avisa que su respaldo es automático…
        assertText("respaldo automático")
        // …y al tocarla se reproduce la cadena confirmada de la casa, sin preguntar.
        compose.onNodeWithText("Telemundo").performClick()
        compose.waitForIdle()
        assertEquals("xtvlive:tm-east", played?.url)
    }

    @Test
    fun theTvPickerShowsOnlySportsSourcesAndSendsADeadOneToTheBackstop() {
        var played: LiveChannel? = null
        compose.setContent {
            VisionTheme {
                TvGeeLinkPickDialog(
                    title = "Guadalajara vs Tigres UANL",
                    subtitle = "Liga MX · Hoy 17:07",
                    links = listOf(link("TUDN MEXICO FHD")),
                    onDismiss = {},
                    onPlay = { played = it },
                    guide = emptyList(),
                    geeTv = listOf(
                        GeeGuide.TvOption(guideName = "Telemundo", title = "Telemundo", link = deadChannel("Telemundo"), score = 100),
                    ),
                    backstops = listOf(backstop()),
                )
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        assertText("Canales posibles:")
        assertAbsent("TELEMUNDO EAST HD")
        assertAbsent("Respaldo en tu lista:")
        // La fila caída queda en el foco de entrada y es accionable (OK la manda al respaldo).
        compose.onNodeWithText("Telemundo").assert(hasClickAction())
        assertNull("todavía no se sintoniza sola", played)
    }

    @Test
    fun theSameChannelNeverAppearsTwiceWithOppositeStates() {
        // El caso del Cruz Azul: arriba «TUDN MX · Caído» y abajo «TUDN MEXICO FHD · Disponible»
        // son el MISMO canal. Se enseña una sola vez: la que sí sirve.
        val deadTudn = GeeSportsSource.GeeLink(
            name = "TUDN MX",
            link = "https://cdn.test/tudn-mx.m3u8",
            headers = emptyMap(),
            type = "hls",
            drm = "",
            alive = false,
        )
        val liveFeed = GeeSportsSource.GeeLink(
            name = "TUDN MEXICO FHD",
            link = "https://cdn.test/tudn-mexico.mpd",
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
                    links = listOf(liveFeed),
                    onDismiss = {},
                    onPlay = { _: LiveChannel -> },
                    guide = emptyList(),
                    geeTv = listOf(GeeGuide.TvOption(guideName = "TUDN", title = "TUDN MX", link = deadTudn, score = 100)),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // La transmisión viva se queda; la copia caída del mismo canal, no.
        assertText("TUDN MEXICO FHD")
        assertText("Disponible")
        assertAbsent("TUDN MX")
        assertAbsent("Caído")
    }

    @Test
    fun theListShowsTheLinkItselfAndRecommendsZappingUntilTheMatchIsFound() {
        // Sin falsa expectativa: la sección se llama «Posibles transmisiones», cada una enseña su
        // enlace y se recomienda intercalar hasta encontrar la que esté dando el partido.
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
                    guide = emptyList(),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Posibles transmisiones:")
        assertText("otte.cache.aiv-cdn.net")           // el enlace a la vista (sin las cabeceras)
        assertAbsent("user-agent=")
        assertText("cambia entre ellas")
        assertText("Arriba/Abajo durante la reproducción")
    }

    @Test
    fun aDeclaredNameWithoutUrlIsListedAndTappingItUsesTheBackstopForThatName() {
        // El caso de León-América: la fuente declara «TUDN US» sin URL. Se lista (como la app
        // original) y al tocarla se reproduce NUESTRO canal confirmado de ese nombre.
        val pending = GeeSportsSource.GeeLink(
            name = "TUDN US",
            link = "",
            headers = emptyMap(),
            type = "hls",
            drm = "",
            alive = null,
        )
        val tudnUsa = LiveChannel(name = "TUDN USA HD", url = "xtvlive:tudn-usa")
        var played: LiveChannel? = null
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "León vs América",
                    subtitle = "Liga MX · Hoy 19:00",
                    links = listOf(pending),
                    onDismiss = {},
                    onPlay = { played = it },
                    backstops = listOf(GeeBackstop.Backstop(guideName = "TUDN US", channel = tudnUsa)),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("TUDN US")
        assertText("La fuente aún no publica su señal")
        assertText("respaldo automático")
        compose.onNodeWithText("TUDN US").performClick()
        compose.waitForIdle()
        assertEquals("xtvlive:tudn-usa", played?.url)
    }

    @Test
    fun aHealthySportsSourceAlwaysWinsEvenIfABackstopExists() {
        // La fuente deportiva manda SIEMPRE: con su señal viva se reproduce la suya, no el respaldo.
        var played: LiveChannel? = null
        compose.setContent {
            VisionTheme {
                GeeLinkPickDialog(
                    title = "Guadalajara vs Tigres UANL",
                    subtitle = "Liga MX · Hoy 17:07",
                    links = emptyList(),
                    onDismiss = {},
                    onPlay = { played = it },
                    guide = emptyList(),
                    geeTv = listOf(
                        GeeGuide.TvOption(guideName = "Telemundo", title = "Telemundo", link = healthyChannel("Telemundo"), score = 100),
                    ),
                    backstops = listOf(backstop()),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertText("Disponible")
        assertAbsent("respaldo automático")
        compose.onNodeWithText("Telemundo").performClick()
        compose.waitForIdle()
        assertEquals("https://cdn.test/Telemundo.m3u8", played?.url)
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
