package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.player.MatchCenterHud
import com.chronosbvrx.visionplus.ui.player.MatchHudPage
import com.chronosbvrx.visionplus.ui.sports.DeportesReturn
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * El mosaico de "Deportes en vivo" en la TV: parrilla de partidos con escudos y
 * foco de D-pad asentado al entrar. Se siembra la copia en archivo (sin red): la
 * pantalla la lee al instante, igual que en la tele.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvSportsMosaicTest {
    @get:Rule
    val compose = createComposeRule()

    private val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)

    private fun seed() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val today = SportsSource.todayIso()
        val events = """
            {"events":[
              {"id":"e1","event":"Liga MX, Apertura","category":"Football",
               "date":"Hoy","date_iso":"$today","time":"19:00:00","alive":3,"total":3,
               "home_name":"América","away_name":"Guadalajara","league_label":"Liga MX","league_id":"mex.1",
               "score":{"home":2,"away":1,"live":true,"detail":"23'","status":"in"}},
              {"id":"e2","event":"Premier League","category":"Football",
               "date":"Hoy","date_iso":"$today","time":"21:00:00","alive":2,"total":2,
               "home_name":"Liverpool","away_name":"Arsenal","league_label":"Premier League","league_id":"eng.1",
               "score":{"live":false,"status":"pre"}},
              {"id":"e3","event":"Gran Premio de México","category":"Motorsports",
               "date":"Hoy","date_iso":"$today","time":"16:00:00","alive":0,"total":0}
            ]}
        """.trimIndent()
        val groups = """
            {"groups":[{"name":"TNT Sports","alive":1,"total":1,"channels":[
              {"name":"TNT SPORTS MX","links":[{"name":"TNT SPORTS MX","link":"https://example.test/a.m3u8","type":"hls","alive":true}]}
            ]}]}
        """.trimIndent()
        File(app.filesDir, "gee38_events.json").writeText(events)
        // El nombre real del archivo de grupos del producto:
        File(app.filesDir, "gee38_sports.json").writeText(groups)
    }

    private fun start() {
        compose.setContent { VisionTheme { TvSportsScreen(onPlay = {}) } }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun focusedCount() = compose.onAllNodes(focused, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertText(where: String, text: String) {
        val matches = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("$where: no aparece «$text»", matches >= 1)
    }

    private fun assertAbsent(text: String) {
        val matches = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertTrue("no debía aparecer «$text»", matches == 0)
    }

    @Before
    fun setUp() {
        FocusRescue.target = null
        FocusRescue.screenHasFocus = false
        FocusRescue.dialogsOpen = 0
        DeportesReturn.resetForTests()
        // La caché en memoria cruza clases de test (Robolectric no reinicia los estáticos):
        // aquí se siembra el archivo, así que la memoria arranca limpia y se lee del disco.
        com.chronosbvrx.visionplus.data.gee.GeeSportsSource.resetMemoryForTests()
        // Reloj fijo (18:00 CDMX del día sembrado): las fases del mosaico no dependen de la
        // hora real a la que corra la suite (el partido de las 21:00 siempre está «pronto»).
        GeeAgenda.clockForTestsMs = pinnedNow()
        seed()
    }

    @After
    fun tearDown() {
        GeeAgenda.clockForTestsMs = null
    }

    /** 18:00 CDMX del día de hoy, en el formato del CMS. */
    private fun pinnedNow(): Long {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("America/Mexico_City")
        fmt.isLenient = false
        return fmt.parse("${SportsSource.todayIso()} 18:00:00")!!.time
    }

    @Test
    fun mosaicShowsTheMatchesWithLogosAndKeepsFocus() {
        start()
        // Los partidos del mosaico están a la vista (equipos y estado).
        assertText("mosaico", "América")
        assertText("mosaico", "Guadalajara")
        assertText("mosaico", "EN VIVO")
        // El héroe compacto deja ver al menos DOS líneas del mosaico: el siguiente
        // partido (fila 2, bajo el héroe y la fila de "En vivo") ya está en pantalla.
        compose.onNodeWithText("Liverpool", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Al entrar, algo debe tener el foco", focusedCount() >= 1)

        // La navegación con el mando sigue viva (sin crashes ni foco muerto).
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue("Tras mover el mando, algo sigue enfocado", focusedCount() >= 1)
    }

    @Test
    fun onlyTheLiveMatchGetsTheRedLivePill() {
        start()
        // e1 está en vivo con 3 señales: la píldora roja es correcta.
        assertText("en vivo", "3 en vivo")
        // e2 tiene 2 señales en pie pero AÚN NO COMIENZA: jamás se rotula «en vivo».
        assertAbsent("2 en vivo")
        // Su señal sí se cuenta, pero como «señal», no como «en vivo».
        assertText("señal", "2 señales disponibles")
    }

    @Test
    fun theHudScoreIsNotSqueezedToTheLeftNumber() {
        compose.setContent {
            VisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    MatchCenterHud(
                        info = GeeZapSignals.EventInfo(
                            title = "Liverpool vs Arsenal",
                            league = "Premier League",
                            scoreLine = "2 - 1 · 78'",
                            homeName = "Liverpool",
                            awayName = "Arsenal",
                            dateIso = "",
                        ),
                        page = MatchHudPage.SUMMARY,
                        currentSignal = "PREMIER SPORTS FHD",
                        signalType = "DASH · ClearKey",
                        signalIndex = 1,
                        signalCount = 3,
                        isTv = true,
                        onPageChange = {},
                        onSourceClick = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        compose.waitForIdle()
        val text = compose.onNodeWithTag("match_hud_score_text").fetchSemanticsNode().boundsInRoot
        val box = compose.onNodeWithTag("match_hud_score").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val hud = compose.onNodeWithTag("match_hud").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "el marcador no debe recortarse al número izquierdo (ancho=${text.width} caja=${box.width} hud=$hud root=$root)",
            text.width >= 50f,
        )
        assertTrue("la caja del marcador debe tener ancho propio (caja=${box.width})", box.width >= 120f)
        assertTrue("la caja del marcador debe dar aire al texto (caja=${box.width}, texto=${text.width})", box.width > text.width)
    }

    @Test
    fun dayAndLeagueFiltersAndScopeShareCompactRows() {
        start()
        // El alcance («Principal»/«Todas las ligas») vive en la fila de chips, sin encabezado
        // ni conteo que empujen el mosaico hacia abajo.
        assertText("alcance", "Principal")
        // Días y ligas comparten fila.
        assertText("filtros", "Hoy")
        assertText("filtros", "Todas")
        assertText("filtros", "Liga MX")
    }
}
