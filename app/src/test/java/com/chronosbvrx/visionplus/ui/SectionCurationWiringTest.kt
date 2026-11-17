package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cableado de la clasificación de submenús: TV y móvil usan la misma curaduría,
 * los infantiles abren el feed (no la cuadrícula plana) y «En emisión» bebe de
 * la agenda TVMaze.
 */
class SectionCurationWiringTest {

    @Test
    fun tvAndPhoneOpenKidsWithTheClassifiedFeed() {
        val tv = source("ui/tv/TvCategoryScreen.kt")
        assertTrue("TV usa la curaduría compartida", tv.contains("SectionCuration.build("))
        assertFalse(
            "TV ya no abre infantiles en la cuadrícula plana",
            tv.contains("mutableStateOf<String?>(if (KidsCatalog.isKidsKey(key)) \"Infantil\" else null)"),
        )

        val phone = source("ui/phone/SectionScreen.kt")
        assertTrue("móvil usa la curaduría compartida", phone.contains("SectionCuration.build("))
        assertTrue("móvil distingue la ruta infantil", phone.contains("kidsOnly: Boolean = false"))

        val app = source("ui/PhoneApp.kt")
        assertTrue(
            "el menú pasa kidsOnly en vez de forzar el chip Infantil",
            app.contains("kidsOnly = current.key.startsWith(\"kids\")"),
        )
        assertFalse(
            "el menú ya no fuerza initialCategory Infantil",
            app.contains("initialCategory = if (current.key.startsWith(\"kids\")) \"Infantil\""),
        )
    }

    @Test
    fun bothScreensLoadTheOnAirScheduleAndPaintItsBadge() {
        for (rel in listOf(
            "ui/tv/TvCategoryScreen.kt",
            "ui/phone/SectionScreen.kt",
        )) {
            val screen = source(rel)
            assertTrue("$rel pide la agenda", screen.contains("RemoteIndexSource.schedule(7)"))
            assertTrue("$rel marca En emisión", screen.contains("\"EN EMISIÓN\""))
        }
    }

    @Test
    fun topTenAndOnAirRowsAreRecognizedByTheirIds() {
        val curation = source("data/SectionCuration.kt")
        assertTrue("Top 10 por id", curation.contains("row.id.endsWith(\"-top10\")"))
        assertTrue("En emisión por id", curation.contains("row.id.endsWith(\"-en-emision\")"))
        assertTrue("fila Todo de infantiles", curation.contains("row.id.endsWith(\"-all\")"))
        assertTrue("respaldo cuando TVMaze no cubre", curation.contains("Series activas"))
    }

    private fun source(rel: String): String {
        val path = "src/main/java/com/chronosbvrx/visionplus/$rel"
        val found = listOf(File(path), File("app/$path"), File("../app/$path")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
