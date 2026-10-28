package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La personalización no puede quedarse en un modelo lindo: el cableado del launcher debe
 * abrir el hub, los gestores, el menú de OK largo y guardar por perfil.
 */
class PersonalizationWiringTest {

    @Test
    fun theControlCenterOpensThePersonalizationHub() {
        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("PersonalizeHubDialog("))
        assertTrue(screen.contains("PersonalizePage.HUB"))
        assertTrue(screen.contains("ManageChannelsDialog("))
        assertTrue(screen.contains("ManageAppsDialog("))
        assertTrue(screen.contains("ManageRowsDialog("))
        assertTrue(screen.contains("ManageTabsDialog("))
        assertTrue(screen.contains("PresetsDialog("))
        assertTrue(screen.contains("DisplaySettingsDialog("))
        assertTrue(screen.contains("ItemActionsDialog("))
        assertTrue(screen.contains("updateConfig"))
    }

    @Test
    fun theLauncherAlsoCustomizesItsTabBarAndShipsPresets() {
        val config = require("src/main/java/com/chronosbvrx/visionplus/home/data/HomeConfig.kt")
        assertTrue("orden de pestañas", config.contains("tabOrder"))
        assertTrue("pestañas ocultas", config.contains("hiddenTabs"))
        assertTrue("plantillas", config.contains("LauncherPreset"))
        assertTrue("TV primero", config.contains("TV_FIRST"))
        assertTrue("Cine", config.contains("CINEMA"))
        assertTrue("Solo lo mío", config.contains("JUST_MINE"))
        assertTrue("Niños", config.contains("KIDS"))

        val personalize = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Personalize.kt")
        assertTrue(personalize.contains("ManageTabsDialog"))
        assertTrue(personalize.contains("PresetsDialog"))
        assertTrue(personalize.contains("Pestañas"))
        assertTrue(personalize.contains("Plantillas"))
        assertTrue("la pestaña sin fuentes se explica", personalize.contains("Sin fuentes en este equipo"))

        val screen = require("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue(screen.contains("PersonalizePage.TABS"))
        assertTrue(screen.contains("PersonalizePage.PRESETS"))
        assertTrue(screen.contains("it.withPreset(preset)"))
        assertTrue(screen.contains("visibleTabs(config, inputsAvailable)"))
        assertTrue(screen.contains("inputsAvailable = inputsAvailable,"))
    }

    @Test
    fun preferencesAreStoredPerProfile() {
        val prefs = require("src/main/java/com/chronosbvrx/visionplus/home/data/LauncherPrefs.kt")
        assertTrue(prefs.contains("home_config_"))
        assertTrue(prefs.contains("homeConfig("))
        assertTrue(prefs.contains("saveHomeConfig("))
        val config = require("src/main/java/com/chronosbvrx/visionplus/home/data/HomeConfig.kt")
        assertTrue(config.contains("channelFavorites"))
        assertTrue(config.contains("hiddenChannels"))
        assertTrue(config.contains("appFavorites"))
        assertTrue(config.contains("hiddenApps"))
        assertTrue(config.contains("rowOrder"))
    }

    @Test
    fun theHomeUsesTheConfiguredRowsAndChannelFavorites() {
        val surfaces = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt")
        assertTrue(surfaces.contains("homeRowOrder(config, feedRows)"))
        assertTrue(surfaces.contains("favoriteChannels("))
        assertTrue(surfaces.contains("onChannelLongClick"))
        assertTrue(surfaces.contains("onAppLongClick"))
        assertTrue(surfaces.contains("config.display.showEpg"))
        val cards = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Cards.kt")
        assertTrue(cards.contains("onLongClick"))
        assertTrue(cards.contains("showEpg"))
    }

    @Test
    fun displayPreferencesReachMetricsAndTheClock() {
        val profile = require("src/main/java/com/chronosbvrx/visionplus/home/DeviceProfile.kt")
        assertTrue(profile.contains("LauncherDensity"))
        assertTrue(profile.contains("rememberHomeMetrics(density"))
        val chrome = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Chrome.kt")
        assertTrue(chrome.contains("clock24h"))
        assertTrue(chrome.contains("showClock"))
        val personalize = require("src/main/java/com/chronosbvrx/visionplus/home/ui/Personalize.kt")
        assertTrue(personalize.contains("Densidad"))
        assertTrue(personalize.contains("Animaciones"))
        assertTrue(personalize.contains("Guía AHORA"))
    }

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
