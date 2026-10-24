package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El buscador del launcher busca de verdad (a través del provider de Vision+, con el mismo
 * buscador del servidor) y pinta los resultados aquí; solo al elegir uno se abre la ficha.
 * La pestaña Deportes manda el evento exacto a Vision+.
 */
class LauncherSearchTest {

    @Test
    fun theSearchLooksInTheProviderAndShowsTheResultsHere() {
        val repo = require(
            "src/main/java/com/chronosbvrx/visionplus/home/data/FeedRepository.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/data/FeedRepository.kt",
        )
        assertTrue("usa la ruta de búsqueda del contrato", repo.contains("HomeContract.SEARCH_URI"))
        assertTrue("expone una búsqueda suspendida", repo.contains("suspend fun search("))

        val ui = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/SearchSurface.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/SearchSurface.kt",
        )
        assertTrue("consulta al provider", ui.contains("repo.search("))
        assertTrue("pinta las tarjetas en el launcher", ui.contains("PosterCard("))
        assertTrue("al elegir, abre la ficha", ui.contains("onOpen(item)"))
        assertTrue("la tele tiene teclado de mando", ui.contains("TvKeyboard("))
        assertTrue("hay dictado por voz", ui.contains("RecognizerIntent.ACTION_RECOGNIZE_SPEECH"))
    }

    @Test
    fun theEventsTabOpensTheExactEventInVisionPlus() {
        val screen = require(
            "src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt",
        )
        assertTrue(screen.contains("HomeTab.EVENTS -> EventsSurface("))
        assertTrue(screen.contains("VisionLinks.sports()"))
        assertTrue(screen.contains("event.openLink"))

        val surfaces = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
        )
        assertTrue("dibuja los equipos", surfaces.contains("TeamSide("))
        assertTrue("muestra el marcador", surfaces.contains("scoreLabel("))
        assertTrue("distingue el vivo", surfaces.contains("Badge(text = \"EN VIVO\""))
    }

    @Test
    fun theLiveTabPrefersMexicoAndCanFilterIt() {
        val surfaces = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
        )
        assertTrue("filtro México", surfaces.contains("label = \"México\""))
        assertTrue("el canal sabe si es mexicano", surfaces.contains("it.mexico"))
    }

    @Test
    fun theLiveMosaicShowsTheChannelNameAndItsNowGuide() {
        val cards = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/Cards.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/Cards.kt",
        )
        assertTrue("pinta el nombre del canal", cards.contains("item.name"))
        assertTrue("pinta la guía AHORA", cards.contains("currentProgram(item, nowMs)"))
        assertTrue("pinta el avance real", cards.contains("liveProgress(item, nowMs)"))
    }

    @Test
    fun sportsUseTheClockAndNotTheAliveSignalCount() {
        val phase = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/SportsPhase.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/SportsPhase.kt",
        )
        assertTrue("estado post cierra el partido", phase.contains("event.status.equals(\"post\""))
        assertTrue("estado in lo abre", phase.contains("event.status.equals(\"in\""))
        assertTrue("ventana de 3 h", phase.contains("SPORTS_LIVE_WINDOW_MS"))
        assertTrue("alive no decide la fase", !phase.contains("event.alive"))
    }

    @Test
    fun theLauncherNoLongerSimulatesTenDownPresses() {
        val screen = require(
            "src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt",
        )
        assertTrue("sin el bucle de Abajo", !screen.contains("moveFocus(FocusDirection.Down)"))
        assertTrue("un solo aterrizaje", screen.contains("landingRequest"))
        assertTrue("rescate de foco", screen.contains("requestFocusSafely"))
        val focus = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/TvFocus.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/TvFocus.kt",
        )
        assertTrue("scroll mínimo", focus.contains("MinimalScrollBringIntoView"))
        assertTrue("abajo dirigido", focus.contains("softFocusDown"))
        val surfaces = require(
            "src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
            "launcher/src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt",
        )
        assertTrue("filas agrupadas", surfaces.contains("focusGroup"))
        assertTrue("scroll mínimo aplicado", surfaces.contains("LocalBringIntoViewSpec provides MinimalScrollBringIntoView"))
    }

    private fun require(vararg candidates: String): String {
        val found = candidates.map(::File).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: ${candidates.joinToString()}", found)
        return found!!.readText()
    }
}
