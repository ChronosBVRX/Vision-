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
        assertTrue(screen.contains("DisplaySettingsDialog("))
        assertTrue(screen.contains("ItemActionsDialog("))
        assertTrue(screen.contains("updateConfig"))
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
