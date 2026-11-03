package com.chronosbvrx.visionplus

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián del lote de cierre:
 *
 * 1. **Juegos** queda en «Próximamente»: la pestaña ya no abre el tablero ni una sala.
 * 2. Los menús del reproductor nativo son contextuales (nacen del botón, con degradado y
 *    pestaña) y las opciones de imagen son una cuadrícula de iconos con nombre pequeño.
 * 3. El VOD de XTV pasa SIEMPRE por el mismo reproductor nativo, en tele y en teléfono.
 */
class ClosingPolishWiringTest {
    private fun source(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }

    @Test
    fun gamesTabIsDisabledWithAClearMessage() {
        val comingSoon = source("src/main/java/com/chronosbvrx/visionplus/ui/games/GamesComingSoon.kt")
        assertTrue(comingSoon.contains("Próximamente"))
        assertTrue(comingSoon.contains("VISION+ PLAY"))

        val tv = source("src/main/java/com/chronosbvrx/visionplus/ui/TvApp.kt")
        assertTrue("la tele entra al aviso", tv.contains("Route.Games, is Route.GameRoom -> GamesComingSoonScreen("))
        assertFalse("la tele ya no abre el tablero", tv.contains("Route.Games -> TvGamesScreen"))

        val phone = source("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        assertTrue("el teléfono entra al aviso", phone.contains("Route.Games, is Route.GameRoom -> GamesComingSoonScreen("))
        assertFalse("el teléfono ya no abre el tablero", phone.contains("Route.Games -> GamesHomeScreen"))
    }

    @Test
    fun nativeMenusAreAnchoredToThePressedPill() {
        val player = source("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("menú contextual anclado", player.contains("showAnchoredMenu("))
        assertTrue("pestaña que señala el botón", player.contains("caretAtBottom"))
        assertTrue("degradado del panel", player.contains("GradientDrawable.Orientation.TL_BR"))
        assertTrue("imagen en cuadrícula", player.contains("menuGrid("))
        assertTrue("fichas con icono y nombre", player.contains("menuTile("))
        assertTrue("iconos propios de imagen", player.contains("imagePresetIcon("))
        assertTrue("iconos de proporción", player.contains("aspectIcon("))
        assertFalse("fuera la tarjeta gigante de imagen", player.contains("Color y negros del vídeo"))
    }

    @Test
    fun allXtvVodGoesThroughTheSameNativePlayer() {
        val screen = source("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("el VOD XTV se resuelve por el motor nativo", screen.contains("if (film.videoUrl.startsWith(\"xtv:\")"))
        assertTrue("el handoff abre el reproductor X4K", screen.contains("XtvHandoff("))
        assertTrue("la licencia VOD es la misma para todos", screen.contains("X4KApi.playVod("))
        val tv = source("src/main/java/com/chronosbvrx/visionplus/ui/TvApp.kt")
        val phone = source("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        assertTrue("la tele usa el mismo reproductor", tv.contains("PlayerScreen(route.film, isTv = true"))
        assertTrue("el teléfono usa el mismo reproductor", phone.contains("PlayerScreen(route.film, isTv = false"))
    }

    @Test
    fun theComposeMenusShareTheSameGlassSurface() {
        val dialogs = source("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerDialogs.kt")
        assertTrue("panel de vidrio reutilizable", dialogs.contains("PlayerMenuSurface("))
        assertTrue("fichas de cuadrícula", dialogs.contains("MenuTileOption("))
        assertTrue("foco sutil, no píldora sólida", dialogs.contains("focused -> Color.White.copy(alpha = 0.10f)"))
        assertTrue("panel con transparencia", dialogs.contains("Color(0x991E3454)"))
        assertTrue("subtítulos con icono", dialogs.contains("Icons.Default.Subtitles"))
        assertTrue("proporciones con icono", dialogs.contains("Icons.Default.AspectRatio"))
    }

    @Test
    fun everyNativePlayerModalUsesTheSameGlass() {
        val player = source("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("cristal reutilizable", player.contains("private fun glassCard("))
        assertTrue("velo suave", player.contains("private fun glassScrim("))
        // Las tarjetas que ve el usuario durante la reproducción.
        assertTrue("siguiente capítulo", player.contains("background = glassCard(18)"))
        assertTrue("capítulos", player.contains("background = glassCard(18)"))
        assertTrue("sugerencias", player.contains("background = glassCard(18)"))
        assertTrue("selección de canal", player.contains("background = glassCard(16)"))
        assertTrue("información del canal", player.contains("background = glassCard(16)"))
        assertTrue("reanudar", player.contains("background = glassCard(16)"))
        assertTrue("modal más pequeño", player.contains("if (isTv) 0.66f else 0.9f"))
        assertTrue("iconos discretos", player.contains("dp(17), dp(17)"))
    }
}
