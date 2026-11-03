package com.chronosbvrx.visionplus

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cableado de la ola de mejoras: Inicio, Series, Ajustes, Juegos y proporción de pantalla. */
class ExperienceV3WiringTest {
    private fun source(path: String) = File(path).readText()

    @Test
    fun homeRemovesQuickHubsAndSectionsDropForYou() {
        val home = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvHomeScreen.kt")
        assertFalse("Colecciones y Especiales ya no se pinta", home.contains("add(HomeRowSlot.Hubs)"))
        val tvSection = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvCategoryScreen.kt")
        assertFalse("Series/Películas ya no muestran Recomendado para ti", tvSection.contains("label = \"Recomendado para ti\""))
        val phoneSection = source("src/main/java/com/chronosbvrx/visionplus/ui/phone/SectionScreen.kt")
        assertTrue(phoneSection.contains("filterNot { it.title.contains(\"Recomendado para ti\""))
    }

    @Test
    fun topBarHasSeriesAndLibraryDropdowns() {
        val top = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTopBar.kt")
        assertTrue(top.contains("data object SeriesHub"))
        assertTrue(top.contains("data object LibraryHub"))
        assertTrue(top.contains("Route.Section(\"anime\""))
        assertTrue(top.contains("Route.Downloads"))
    }

    @Test
    fun settingsAreSplitIntoCategories() {
        val settings = source("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt")
        assertTrue(settings.contains("SCREEN(\"Pantalla y rendimiento\")"))
        assertTrue(settings.contains("ACCOUNT(\"Cuenta y perfiles\")"))
        assertTrue(settings.contains("UPDATES(\"Actualizaciones\")"))
    }

    @Test
    fun gamesHaveIntroMusicAndLayeredBack() {
        val tv = source("src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt")
        assertTrue(tv.contains("FiestaGamesIntro"))
        assertTrue(tv.contains("showIntro"))
        assertTrue(tv.contains("created != null -> leaveRoom()"))
        assertTrue(tv.contains("initialGameId = lastGame.id"))
        val ambient = source("src/main/java/com/chronosbvrx/visionplus/data/games/NarratorAudio.kt")
        assertTrue(ambient.contains("R.raw.game_theme"))
        assertTrue(ambient.contains("R.raw.game_intro"))
        assertTrue(File("src/main/res/raw/game_theme.mp3").isFile)
    }

    @Test
    fun everyPlayerExposesAspectRatio() {
        val media3 = source("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(media3.contains("showAspect"))
        assertTrue(media3.contains("AspectRatioDialog("))
        assertTrue(media3.contains("MatchStatsPanel("))
        assertTrue(media3.contains("SportsStatsSource.load("))
        val x4k = source("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue(x4k.contains("showImagePicker()"))
        assertTrue(x4k.contains("imageBtn"))
        assertTrue(x4k.contains("applyAspect("))
        val settings = source("src/main/java/com/chronosbvrx/visionplus/ui/settings/VisionSettings.kt")
        assertTrue(settings.contains("val aspectModes = listOf(0, 1, 4, 5)"))
    }
}
