package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mosaico de TV en vivo del launcher no puede abrir el canal "suelto": tiene que viajar
 * con el menú filtrado para que Arriba/Abajo en el reproductor hagan zapping dentro de ese
 * menú (México, Favoritos o Todos) y no sobre los 1,000 canales.
 */
class LauncherLiveScopeTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/home/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun theLiveGridSendsTheVisibleMenuAndItsLabel() {
        val surfaces = source("ui/Surfaces.kt")
        assertTrue(
            "LiveSurface no recibe la lista visible",
            surfaces.contains("onOpen: (LiveItem, List<LiveItem>, String) -> Unit"),
        )
        assertTrue(
            "el clic no entrega la lista y el rótulo",
            surfaces.contains("onOpen(item, visible, liveScopeLabel(filter))"),
        )
        assertTrue("faltan los rótulos del menú", surfaces.contains("Canales de México"))
        assertTrue("falta el rótulo de favoritos", surfaces.contains("Mis canales"))
    }

    @Test
    fun theLauncherBuildsTheLinkWithTheFilteredCodes() {
        val screen = source("LauncherScreen.kt")
        assertTrue("no se construye el enlace del canal", screen.contains("VisionLinks.live("))
        assertTrue("no viajan los códigos del menú", screen.contains("codes = channels.map { it.code }"))
        assertTrue("no viaja el rótulo del menú", screen.contains("scope = scope"))
    }
}
