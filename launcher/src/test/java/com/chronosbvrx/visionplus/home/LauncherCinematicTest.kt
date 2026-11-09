package com.chronosbvrx.visionplus.home

import com.chronosbvrx.visionplus.home.data.HomeConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes del rediseño cinematográfico del Inicio: fondo a sangre, hero sin etiquetas
 * redundantes, tarjetas con foco de 10 pies, barra de progreso integrada, barra superior de
 * vidrio y tipografía propia. Son comprobaciones de cableado: el launcher no arrastra
 * Robolectric y estas reglas se rompen justo en los sitios que se tocan a mano.
 */
class LauncherCinematicTest {

    @Test
    fun theDashboardKeepsTheFeaturedArtInsideItsCard() {
        val dashboard = ui("Dashboard.kt")
        assertTrue("fondo de escritorio, no arte a sangre", dashboard.contains("fun DashboardBackdrop("))
        assertTrue("retícula tenue de sistema", dashboard.contains("Canvas("))
        assertTrue("el Bento encierra el hero", dashboard.contains("fun BentoHeroCard("))
        assertTrue("borde visible de tarjeta", dashboard.contains("border(1.dp, Color.White.copy(alpha = 0.14f)"))
        assertTrue("el hero pinta su arte DENTRO de la tarjeta", dashboard.contains("showArt = true"))
        assertTrue("badge de acceso a la app", dashboard.contains("ABRIR EN VISION+ TV"))

        val hero = ui("Hero.kt")
        assertTrue("el hero conserva el arte encapsulado", hero.contains("if (showArt)"))
        val screen = source("LauncherScreen.kt")
        assertTrue("el launcher usa el fondo de escritorio", screen.contains("DashboardBackdrop()"))
        assertFalse("ya no pinta arte a sangre", screen.contains("CinematicBackdrop("))
        assertFalse("ya no hay una diapositiva global", screen.contains("activeHero"))
    }

    @Test
    fun theSportsHeroHasOneLivePillAndTransparentCrests() {
        val hero = ui("Hero.kt")
        assertTrue("etiqueta compacta EN VIVO", hero.contains("if (slide.live) \"EN VIVO\" else \"PRÓXIMO\""))
        assertTrue("temporada junto al evento", hero.contains("seasonLabel(slide.startMs)"))
        assertTrue("sin DEPORTES · EN VIVO duplicado", !hero.contains("\"DEPORTES · EN VIVO\""))
        assertTrue("hora con números tabulares", hero.contains("fontFeatureSettings = \"tnum\""))
        assertTrue("los equipos se dibujan sin caja", hero.contains("Sin cuadro gris"))
        assertFalse(
            "el bloque de equipo ya no usa el cuadro gris",
            hero.contains("background(Color.White.copy(alpha = 0.08f))"),
        )
        assertTrue("evento individual sin VS falso", hero.contains("SingleEventScoreboard("))
        assertTrue("título sin duplicar «X vs X»", hero.contains("heroDisplayTitle(slide)"))
    }

    @Test
    fun theFocusStateIsCinematicAndDimsItsSiblings() {
        val components = ui("Components.kt")
        assertTrue("escala de 1.06", components.contains("focusedScale: Float = 1.06f"))
        assertTrue("transición de 200 ms", components.contains("durationMillis = 200"))
        assertTrue("anillo degradado de 2 px al enfocar", components.contains("focused -> 2.dp"))
        assertTrue("el anillo usa el acento de la marca", components.contains("HomeColors.Cyan.copy(alpha = 0.95f)"))
        assertTrue("sombra ambiental", components.contains("Modifier.shadow("))
        assertTrue("las demás tarjetas bajan al 75 %", components.contains("0.75f"))
        assertTrue("la fila sabe dónde está el foco", components.contains("fun CardFocusScope("))
        assertTrue("el botón primario tiene texto oscuro", components.contains("if (primary) Color(0xFF05070C) else Color.White"))
        assertTrue("el botón ya no es una cápsula", components.contains("HomeShapes.button"))
        assertTrue("borde/relleno degradado del botón", components.contains("val outerBrush") && components.contains("Brush.linearGradient"))
        assertTrue("vidrio del secundario", components.contains("0.11f") && components.contains("0.055f"))
    }

    @Test
    fun anAppWithoutCoverUsesABundledSportReference() {
        val art = ui("SportArt.kt")
        assertTrue("moto", art.contains("R.drawable.sport_motor"))
        assertTrue("fútbol", art.contains("R.drawable.sport_soccer"))
        assertTrue("básquet", art.contains("R.drawable.sport_basketball"))
        assertTrue("tenis", art.contains("R.drawable.sport_tennis"))
        assertTrue("americano", art.contains("R.drawable.sport_american_football"))
        assertTrue("béisbol", art.contains("R.drawable.sport_baseball"))
        assertTrue("boxeo", art.contains("R.drawable.sport_boxing"))
        assertTrue("voleibol", art.contains("R.drawable.sport_volleyball"))
        assertTrue("golf", art.contains("R.drawable.sport_golf"))
        assertTrue("hockey", art.contains("R.drawable.sport_hockey"))
        assertTrue("respaldo genérico", art.contains("R.drawable.sport_default"))

        val backdrop = ui("Backdrop.kt")
        assertTrue("el fondo usa el arte local", backdrop.contains("val localArt = if (slide.sports) sportBackdropFor(slide) else 0"))
        assertTrue("lo pinta sin red", backdrop.contains("painterResource(localArt)"))
        val hero = ui("Hero.kt")
        assertTrue("el hero también lo usa", hero.contains("sportBackdropFor(slide)"))
    }

    @Test
    fun favoritesAndDirectShortcutsLiveInTheBentoDashboard() {
        val surfaces = ui("Surfaces.kt")
        assertTrue(
            "sin favoritos se descubre TV en vivo",
            surfaces.contains("\"live\" -> if (favChannels.isEmpty() && liveItems.isNotEmpty())"),
        )
        assertTrue("el Inicio entrega los accesos directos", surfaces.contains("onVisionDestination: (VisionDestination) -> Unit"))
        assertTrue("el Bento usa la tarjeta de accesos", surfaces.contains("VisionShortcutsCard("))
        assertTrue("la fila de canales sigue abriendo la parrilla de Vision+", surfaces.contains("onSeeAll = { onVisionDestination(VisionDestination.LIVE) }"))

        val dashboard = ui("Dashboard.kt")
        assertTrue("la tarjeta de accesos existe", dashboard.contains("fun VisionShortcutsCard("))
        assertTrue("destinos reales", dashboard.contains("IR A VISION+") && dashboard.contains("VisionDestination.DOWNLOADS"))
        assertFalse("fuera las entradas de TV", dashboard.contains("ENTRADAS DE LA TELE"))
        assertFalse("fuera el último canal", dashboard.contains("onTvLive"))

        val screen = source("LauncherScreen.kt")
        assertTrue("enlaces directos", screen.contains("VisionLinks.games()") && screen.contains("VisionLinks.downloads()"))
        assertFalse("fuera el atajo de TV del sistema", screen.contains("openTvEnVivo"))
    }

    @Test
    fun appsComeBeforeLiveTvAndAfterFavorites() {
        val order = HomeConfig().rowOrder
        val channels = order.indexOf("channels")
        val apps = order.indexOf("apps")
        val live = order.indexOf("live")
        assertTrue("Mis canales primero", channels in 0 until apps)
        assertTrue("apps antes que TV en vivo", apps in 0 until live)
    }

    @Test
    fun appIconsFillTheirTileInsteadOfSittingInADrawnBox() {
        val cards = ui("Cards.kt")
        assertTrue("la baldosa no dibuja un borde interno", cards.contains("showIdleBorder = false"))
        assertTrue("el icono llena la baldosa", cards.contains("contentScale = ContentScale.Crop"))
        assertFalse("ya no hay un logo pequeño con padding", cards.contains("tile * 0.2f"))
    }

    @Test
    fun channelsAndEpisodesShareTheSixteenByNineRatio() {
        for (height in listOf(420, 540, 900)) {
            val metrics = homeMetrics(height)
            assertEquals(
                "canales en 16:9 a $height dp",
                16f / 9f,
                metrics.channelWidth.value / metrics.channelHeight.value,
                0.02f,
            )
            assertEquals(
                "seguir viendo en 16:9 a $height dp",
                16f / 9f,
                metrics.continueWidth.value / metrics.continueHeight.value,
                0.02f,
            )
        }
    }

    @Test
    fun theContinueCardHasTheProgressBarAtTheVeryBottom() {
        val cards = ui("Cards.kt")
        assertTrue("barra de 3 px pegada al borde", cards.contains(".height(3.dp)"))
        assertTrue("acento cian", cards.contains(".background(HomeColors.Cyan)"))
        assertTrue("carril oscuro translúcido", cards.contains("Color.Black.copy(alpha = 0.65f)"))
        assertTrue("velo parejo para miniaturas claras", cards.contains("Color.Black.copy(alpha = 0.22f)"))
    }

    @Test
    fun theChromeLooksLikeASystemBarNotAnAppTabBar() {
        val chrome = ui("Chrome.kt")
        assertTrue("el imagotipo oficial de Vision+", chrome.contains("ic_vision_wordmark"))
        assertTrue("saludo de la persona", chrome.contains("greetingFor("))
        assertTrue("sin pestañas de catálogo", !chrome.contains("items(entries)"))
        assertFalse("ya no hay pestañas de texto", chrome.contains("TabPill("))
        assertTrue("indicador de red", chrome.contains("NetworkChip("))
        assertTrue("centro de control", chrome.contains("Icons.Rounded.Notifications"))
        assertTrue("ajustes del sistema", chrome.contains("Icons.Rounded.Settings"))
        assertTrue("reloj con peso visual", chrome.contains("fontSize = if (compact) 27.sp else 32.sp"))
        assertTrue("reloj tabular", chrome.contains("fontFeatureSettings = \"tnum\""))
        assertTrue("avatar con marco circular", chrome.contains("border(1.5.dp, Color.White.copy(alpha = 0.28f)"))

        val theme = ui("Theme.kt")
        assertTrue("tipografía Plus Jakarta Sans", theme.contains("plus_jakarta_sans"))
        assertTrue("pizarra neutro ultra oscuro", theme.contains("0xFF07090E") && theme.contains("0xFF0B0F19"))
        assertTrue("gris claro para texto secundario", theme.contains("0xFF94A3B8"))
    }

    private fun source(rel: String): String = require("src/main/java/com/chronosbvrx/visionplus/home/$rel")

    private fun ui(name: String): String = source("ui/$name")

    private fun require(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
