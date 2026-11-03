package com.chronosbvrx.visionplus.home

import com.chronosbvrx.visionplus.home.ui.LauncherSettingsSection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El launcher es un escritorio de Vision+, no un panel del televisor: Inicio siempre es el
 * dashboard, sin pestañas, sin entradas HDMI y sin pantallas de configuración del equipo.
 */
class LauncherSettingsTest {

    @Test
    fun theLauncherAlwaysStartsAtTheDashboard() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue("Inicio es el único destino inicial", screen.contains("mutableStateOf(HomeTab.HOME)"))
        assertFalse("ya no hay pestaña inicial configurable", screen.contains("startTabFor("))
        assertFalse("ya no se recuerda la última pestaña", screen.contains("prefs.lastTab ="))
    }

    @Test
    fun theLauncherNoLongerExposesTvInputsOrSystemPages() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertFalse(screen.contains("TvInputsRepository(context)"))
        assertFalse(screen.contains("HomeTab.INPUTS -> InputsSurface("))
        assertFalse(screen.contains("openSystemPage("))
        assertFalse(screen.contains("inputsAvailable"))
        assertFalse("el centro de control ya no recibe secciones viejas", screen.contains("sections = availableTabs"))

        val settings = require("src/main/java/com/chronosbvrx/visionplus/home/ui/LauncherSettings.kt")
        assertFalse("fuera entradas de TV", settings.contains("Entradas de la tele"))
        assertFalse("fuera pantallas del sistema", settings.contains("Wi-Fi y red"))
        assertFalse(settings.contains("LauncherSystemPage"))

        val overlays = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Overlays.kt")
        assertFalse("fuera Ver TV abierta", overlays.contains("onWatchTv"))
        assertFalse("fuera Ajustes de la tele", overlays.contains("Ajustes de la tele"))
    }

    @Test
    fun theLauncherSettingsOnlyKeepTheNewSections() {
        val settings = require("src/main/java/com/chronosbvrx/visionplus/home/ui/LauncherSettings.kt")
        assertTrue("existe el panel", settings.contains("fun LauncherSettingsDialog("))
        assertTrue("riel de secciones", settings.contains("enum class LauncherSettingsSection"))
        assertTrue("inicio del sistema", settings.contains("Modo persistente"))
        assertTrue("personalizar", settings.contains("Personalizar Inicio"))
        assertTrue("actualizaciones", settings.contains("Actualizaciones"))
        assertTrue("acerca de", settings.contains("Acerca de"))
        assertFalse("fuera pestaña al abrir", settings.contains("Pestaña al abrir"))
        assertEquals(3, LauncherSettingsSection.entries.size)
    }

    @Test
    fun theControlCenterOpensTheLauncherAndVisionSettings() {
        val overlays = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Overlays.kt")
        assertTrue(overlays.contains("onLauncherSettings"))
        assertTrue(overlays.contains("Ajustes de Vision+ Home"))
        assertTrue(overlays.contains("Ajustes de Vision+"))

        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("LauncherSettingsDialog("))
        assertTrue(screen.contains("LauncherSettingsActions("))
        assertTrue(screen.contains("settingsSection = LauncherSettingsSection.STARTUP"))
        assertTrue("las filas del feed también se mueven", screen.contains("cfg.copy(rowOrder = cfg.rowOrder + id)"))
    }

    @Test
    fun everyShortcutOpensTheExactVisionSection() {
        val dashboard = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Dashboard.kt")
        assertTrue("tarjeta de accesos", dashboard.contains("fun VisionShortcutsCard("))
        listOf(
            "VisionDestination.LIVE",
            "VisionDestination.MOVIES",
            "VisionDestination.SERIES",
            "VisionDestination.SPORTS",
            "VisionDestination.GAMES",
            "VisionDestination.LIBRARY",
            "VisionDestination.DOWNLOADS",
            "VisionDestination.ANIME",
            "VisionDestination.NOVELAS",
        ).forEach { destination ->
            assertTrue("falta $destination", dashboard.contains(destination))
        }

        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("VisionLinks.liveHub()"))
        assertTrue(screen.contains("VisionLinks.section(\"movies\")"))
        assertTrue(screen.contains("VisionLinks.section(\"series\")"))
        assertTrue(screen.contains("VisionLinks.sports()"))
        assertTrue(screen.contains("VisionLinks.games()"))
        assertTrue(screen.contains("VisionLinks.library()"))
        assertTrue(screen.contains("VisionLinks.downloads()"))
        assertTrue(screen.contains("VisionLinks.section(\"anime\")"))
        assertTrue(screen.contains("VisionLinks.section(\"novelas\")"))
    }

    @Test
    fun theDashboardDoesNotOfferSystemSettingsAsAnApp() {
        val dashboard = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Dashboard.kt")
        assertTrue(dashboard.contains("fun dashboardAppAllowed("))
        assertTrue(dashboard.contains("\"settings\" !in pkg"))
        assertTrue(dashboard.contains("com.google.android.play.games"))
        val surfaces = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt")
        assertTrue(surfaces.contains("dashboardAppAllowed(it.packageName)"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
