package com.chronosbvrx.visionplus.ui.player

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El menú de reproducción (OSD) debe verse en teles de 720p/1080p con overscan sin que el
 * título quede recortado y sin barras opacas que se coman el video. Estos guardianes fijan
 * las decisiones que se probaron a mano: título con peso + elipsis, velo degradado y
 * controles compactos en la tele.
 */
class PlayerOsdLayoutTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theNativeTitleNeverClipsAndLeavesTheSafeArea() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("el título no recorta con elipsis", player.contains("ellipsize = TextUtils.TruncateAt.END"))
        assertTrue("en la tele el título debe poder usar dos líneas", player.contains("maxLines = if (isTv) 2 else 1"))
        assertTrue(
            "el título debe ocupar el ancho disponible con peso, no desbordar la pantalla",
            player.contains("LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)"),
        )
        assertTrue("falta el margen mínimo contra el overscan", player.contains("topSafe = maxOf(statusBarHeight(), if (isTv) dp(24) else 0)"))
    }

    @Test
    fun theNativeBarsUseSoftScrimsAndCompactTvMetrics() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("la barra superior debe ser un degradado, no una franja opaca", player.contains("verticalScrim(0xC0070B13.toInt(), 0x00070B13)"))
        assertTrue("la barra inferior debe ser un degradado", player.contains("verticalScrim(0x99070B13.toInt(), 0xE6070B13.toInt())"))
        assertTrue("el margen lateral de la tele debe ser compacto", player.contains("val hPad = if (isTv) dp(36) else dp(16)"))
        assertTrue("el margen inferior debe quedar fuera del panel", player.contains("val bottomSafe = if (isTv) dp(22) else dp(10)"))
        assertTrue("el panel inferior debe envolverse para dejar libre el overscan", player.contains("val bottomWrap = FrameLayout(this)"))
        assertTrue("el botón de reproducción de la tele debe ser más pequeño", player.contains("val playSize = if (isTv) 52 else 58"))
        assertTrue("las píldoras de la tele deben ser compactas", player.contains("textSize = if (isTv) 11f else 12f"))
        assertTrue("el velo inferior debe cubrir el borde", player.contains("verticalScrim(0x99070B13.toInt(), 0xE6070B13.toInt())"))
    }

    @Test
    fun languageAndSubtitlesAreAlwaysVisibleAndExplicit() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("el idioma debe mostrarse aunque solo haya español", player.contains("audioBtn?.visibility = View.VISIBLE"))
        assertTrue("los subtítulos deben mostrarse aunque no existan", player.contains("subsBtn?.visibility = View.VISIBLE"))
        assertTrue("falta el aviso de un solo idioma", player.contains("Idioma solo disponible en español"))
        assertTrue("falta el aviso sin subtítulos", player.contains("Sin subtítulos"))
    }

    @Test
    fun thePlayFocusRingAndSeekTrackAreReadableFromTheCouch() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("el play enfocado debe tener un aro claro", player.contains("0xFFE2F3FF.toInt()"))
        assertTrue(
            "la barra de progreso debe verse contra el velo del OSD",
            player.contains("progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x5CFFFFFF)"),
        )
        assertTrue(
            "la barra de progreso debe tener color visible",
            player.contains("progressTintList = android.content.res.ColorStateList.valueOf(0xFF4C9BFF.toInt())"),
        )
    }

    @Test
    fun theComposePlayerKeepsItsTitleInsideTheViewport() {
        val player = source("ui/player/PlayerScreen.kt")
        assertTrue("el título debe tener un ancho seguro y no pegarse al borde", player.contains(".fillMaxWidth(0.72f)"))
        assertTrue("la tipografía de la tele debe ser contenida", player.contains("fontSize = if (isTv) 36.sp else 30.sp"))
        assertTrue("los controles inferiores deben tener respiro compacto", player.contains("padding(horizontal = 12.dp, vertical = 6.dp)"))
        assertTrue("las píldoras deben ser más compactas", player.contains("padding(horizontal = 12.dp, vertical = 7.dp)"))
        assertTrue("audio/subtítulos deben estar siempre en VOD", player.contains("val showTracksButton = !isLive && !introPlaying"))
        assertTrue("el botón debe dibujarse aunque no haya opciones", player.contains("if (showTracksButton)"))
    }
}
