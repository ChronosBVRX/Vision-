package com.chronosbvrx.visionplus.home

import com.chronosbvrx.visionplus.home.data.StartTab
import com.chronosbvrx.visionplus.home.ui.HomeTab
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsSection
import com.chronosbvrx.visionplus.home.ui.nextStartTab
import com.chronosbvrx.visionplus.home.ui.startTabFor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un launcher de verdad tiene SUS ajustes y enseña las entradas de video del televisor.
 * Estos guardianes fijan las dos cosas: que existan y que estén cableadas a algo real.
 */
class LauncherSettingsTest {

    @Test
    fun theStartTabIsRespectedOnBootAndHomeAlwaysGoesHome() {
        assertEquals(HomeTab.HOME, startTabFor(StartTab.HOME, "inputs"))
        assertEquals(HomeTab.LIVE, startTabFor(StartTab.LIVE, "home"))
        assertEquals(HomeTab.INPUTS, startTabFor(StartTab.INPUTS, "home"))
        assertEquals(HomeTab.APPS, startTabFor(StartTab.APPS, "home"))
        // «La última que usaste» cae al Inicio si nunca hubo una guardada.
        assertEquals(HomeTab.APPS, startTabFor(StartTab.LAST, "apps"))
        assertEquals(HomeTab.HOME, startTabFor(StartTab.LAST, ""))
        assertEquals(HomeTab.HOME, startTabFor(StartTab.LAST, "pestaña-inventada"))
    }

    @Test
    fun theStartTabOptionCyclesThroughEveryChoice() {
        var current = StartTab.HOME
        val seen = mutableSetOf(current)
        repeat(StartTab.entries.size - 1) {
            current = nextStartTab(current)
            seen += current
        }
        assertEquals(StartTab.entries.toSet(), seen)
        assertEquals(StartTab.HOME, nextStartTab(current))
    }

    @Test
    fun aTvBoxWithoutInputsNeverOffersTheInputsTab() {
        // Sin sintonizador ni HDMI de entrada, «Entradas» no se ofrece ni como pestaña inicial.
        var current = StartTab.HOME
        repeat(StartTab.entries.size * 2) {
            current = nextStartTab(current, inputsAvailable = false)
            assertTrue("no debe ofrecer Entradas", current != StartTab.INPUTS)
        }
        assertEquals(StartTab.HOME, nextStartTab(StartTab.INPUTS, inputsAvailable = false))
    }

    @Test
    fun theInputsTabIsHiddenWhenTheDeviceReportsNothing() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("val inputsAvailable = inputs.isNotEmpty()"))
        assertTrue(screen.contains("visibleTabs(config, inputsAvailable)"))
        assertTrue(screen.contains("availableTabs = availableTabs"))

        val chrome = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Chrome.kt")
        assertTrue(chrome.contains("availableTabs: List<HomeTab> = HomeTab.entries"))
        assertTrue(chrome.contains("items(availableTabs.toList())"))
        assertTrue("oculta Entradas sin fuentes", chrome.contains("(tab != HomeTab.INPUTS || inputsAvailable)"))
        assertTrue("Inicio siempre primero", chrome.contains("listOf(HomeTab.HOME) + visible"))
    }

    @Test
    fun theLauncherHasItsOwnSettingsPanelApartFromPersonalizeHome() {
        val settings = require("src/main/java/com/chronosbvrx/visionplus/home/ui/LauncherSettings.kt")
        assertTrue("existe el panel", settings.contains("fun LauncherSettingsDialog("))
        assertTrue("riel de secciones", settings.contains("enum class LauncherSettingsSection"))
        assertTrue("inicio", settings.contains("Pestaña al abrir"))
        assertTrue("entradas", settings.contains("Entradas de la tele"))
        assertTrue("sistema", settings.contains("Wi-Fi y red"))
        assertTrue("idioma", settings.contains("Idioma y región"))
        assertTrue("accesibilidad", settings.contains("Accesibilidad"))
        assertTrue("protector de pantalla", settings.contains("Protector de pantalla"))
        assertTrue("actualizaciones", settings.contains("Actualizaciones"))
        assertTrue("acerca de", settings.contains("Acerca de"))
        assertTrue("no duplica personalizar", settings.contains("Personalizar Inicio"))
        // El caso honesto de la TV box y de la PC queda explicado.
        assertTrue(settings.contains("no tiene entradas de TV"))
        assertTrue(settings.contains("TV box"))
        assertEquals(5, LauncherSettingsSection.entries.size)
    }

    @Test
    fun theControlCenterOpensTheLauncherSettings() {
        val overlays = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Overlays.kt")
        assertTrue(overlays.contains("onLauncherSettings"))
        assertTrue(overlays.contains("Ajustes de Vision+ Home"))
        assertTrue(overlays.contains("onWatchTv"))
        assertTrue("atajo a TV abierta", overlays.contains("Ver TV abierta"))

        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("LauncherSettingsDialog("))
        assertTrue(screen.contains("LauncherSettingsActions("))
        assertTrue(screen.contains("settingsSection = LauncherSettingsSection.STARTUP"))
        assertTrue(screen.contains("it.setStartTab(start)"))
        assertTrue(screen.contains("it.toggleInputHidden(id)"))
        assertTrue(screen.contains("openSystemPage(page)"))
    }

    @Test
    fun theTvInputsAreARealSectionOfTheLauncher() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue("lee el sistema", screen.contains("TvInputsRepository(context)"))
        assertTrue("carga las entradas", screen.contains("tvInputs.inputs()"))
        assertTrue("las abre", screen.contains("tvInputs.open(item)"))
        assertTrue("pestaña propia", screen.contains("HomeTab.INPUTS -> InputsSurface("))
        assertTrue("fila del Inicio", screen.contains("inputs = inputs,"))
        assertTrue("se refresca al volver", screen.contains("inputs = tvInputs.inputs()"))
        assertTrue("atajo al sintonizador", screen.contains("tunerOf(inputs)"))
        assertTrue("la fila se puede ocultar", screen.contains("it.toggleRow(\"inputs\")"))

        val chrome = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Chrome.kt")
        assertTrue(chrome.contains("INPUTS(\"inputs\", \"Entradas\")"))

        val surfaces = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt")
        assertTrue(surfaces.contains("fun InputsSurface("))
        assertTrue(surfaces.contains("private fun InputsRow("))
        assertTrue(surfaces.contains("Entradas de la tele"))

        val cards = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Cards.kt")
        assertTrue(cards.contains("fun InputCard("))
        assertTrue("TV abierta se distingue", cards.contains("TV ABIERTA"))
    }

    @Test
    fun theInputsAreReadFromTheSystemAndOpenedTheWayTheFirmwareUnderstands() {
        val repo = require("src/main/java/com/chronosbvrx/visionplus/home/data/TvInputs.kt")
        assertTrue("TvInputManager", repo.contains("Context.TV_INPUT_SERVICE"))
        assertTrue("passthrough oficial", repo.contains("buildChannelUriForPassthroughInput"))
        assertTrue("abre la app de la entrada", repo.contains("getLeanbackLaunchIntentForPackage"))
        assertTrue("nunca deja el botón muerto", repo.contains("Settings.ACTION_SETTINGS"))
        assertTrue("orden de tele", repo.contains("fun orderTvInputs("))
        assertTrue("ocultas sin salir del launcher", repo.contains("fun visibleInputs("))
    }

    @Test
    fun theSystemPagesAreReachableFromTheLauncherSettings() {
        val repo = require("src/main/java/com/chronosbvrx/visionplus/home/data/AppsRepository.kt")
        assertTrue(repo.contains("ACTION_LOCALE_SETTINGS"))
        assertTrue(repo.contains("ACTION_ACCESSIBILITY_SETTINGS"))
        assertTrue(repo.contains("ACTION_DREAM_SETTINGS"))
        assertTrue(repo.contains("ACTION_MANAGE_DEFAULT_APPS_SETTINGS"))
        assertTrue(repo.contains("fun networkLabel()"))
        assertTrue(repo.contains("fun deviceLabel()"))
        assertTrue(repo.contains("fun storageLabel()"))
    }

    @Test
    fun theLauncherStillUsesItsConfiguredTabAndTheRowManagerMovesFeedRows() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("startTabFor(config.startTab, prefs.lastTab)"))
        assertTrue("recuerda la última pestaña", screen.contains("prefs.lastTab = tab.key"))
        assertTrue("las filas del feed también se mueven", screen.contains("cfg.copy(rowOrder = cfg.rowOrder + id)"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
