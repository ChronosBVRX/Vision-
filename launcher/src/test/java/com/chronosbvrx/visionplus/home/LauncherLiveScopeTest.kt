package com.chronosbvrx.visionplus.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El menú desde el que se abre un canal viaja CON el enlace: el reproductor hace zapping y abre
 * «Canales» solo dentro de ese menú. Las filas del Inicio («Mis canales» y «TV en vivo») armaban
 * el enlace con el `playLink` del feed, que no lleva ni la lista ni el rótulo, así que el
 * reproductor caía al catálogo entero. La pestaña TV en vivo ya mandaba su mosaico filtrado.
 */
class LauncherLiveScopeTest {

    private fun source(rel: String): String {
        val found = listOf(File(rel), File("launcher/$rel"), File("../launcher/$rel"))
            .firstOrNull { it.exists() }
        require(found != null) { "no encuentro $rel" }
        return found!!.readText()
    }

    @Test
    fun theHomeRowsPassTheirWholeSegment() {
        val surfaces = source("src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt")
        assertTrue(
            "la fila entrega el canal, su menú completo y el rótulo",
            surfaces.contains("onLive: (LiveItem, List<LiveItem>, String) -> Unit"),
        )
        assertTrue(
            "«Mis canales» viaja con sus favoritos",
            surfaces.contains("onLive(item, favChannels, \"Mis canales\")"),
        )
        assertTrue(
            "«TV en vivo» viaja con todos los canales",
            surfaces.contains("onLive(item, liveItems, \"Todos los canales\")"),
        )
        assertFalse("ya no se abre la fila con el playLink suelto", surfaces.contains("onOpen = onLive,"))
    }

    @Test
    fun theScreenBuildsTheLinkWithTheMenu() {
        val screen = source("src/main/java/com/chronosbvrx/visionplus/home/LauncherScreen.kt")
        assertTrue("el enlace lleva el menú de la fila", screen.contains("codes = channels.map { it.code }"))
        assertTrue("y su rótulo visible", screen.contains("scope = scope"))
        assertFalse(
            "el Inicio ya no usa el playLink sin menú",
            screen.contains("onLive = { item -> openLink(item.playLink) }"),
        )
    }

    @Test
    fun theLiveTabKeepsPassingItsFilter() {
        val surfaces = source("src/main/java/com/chronosbvrx/visionplus/home/ui/Surfaces.kt")
        // La pestaña TV en vivo ya mandaba el mosaico filtrado (Todos/México/Favoritos).
        assertTrue(surfaces.contains("onOpen(item, visible, liveScopeLabel(filter))"))
        listOf("Todos los canales", "Canales de México", "Mis canales").forEach { scope ->
            assertTrue("falta el rótulo $scope", surfaces.contains(scope))
        }
    }
}
