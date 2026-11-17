package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Regresión del cierre de la app al abrir una serie (2.6.55).
 *
 * El reporte de campo era `IllegalArgumentException: Cannot coerce value to an empty
 * range: maximum -1 is less than minimum 0` en `screen=Title(...)`: la ficha pedía el
 * capítulo con `nextEpisodeIndex.coerceIn(0, episodes.lastIndex)` y, cuando la serie
 * tiene 2+ temporadas pero sus capítulos aún no habían llegado, `lastIndex` era `-1`.
 */
class EpisodeIndexTest {

    @Test
    fun anEmptySeasonNeverBuildsAnEmptyRange() {
        assertEquals(0, episodeIndexOrZero(0, 0))
        assertEquals(0, episodeIndexOrZero(7, 0))
        assertEquals(0, episodeIndexOrZero(3, 1))
    }

    @Test
    fun anIndexInsideTheSeasonIsKept() {
        assertEquals(0, episodeIndexOrZero(0, 12))
        assertEquals(5, episodeIndexOrZero(5, 12))
        assertEquals(11, episodeIndexOrZero(11, 12))
    }

    @Test
    fun anIndexOutOfBoundsIsClampedToTheSeason() {
        assertEquals(11, episodeIndexOrZero(99, 12))
        assertEquals(0, episodeIndexOrZero(-4, 12))
    }

    @Test
    fun neitherTitleClampsWithAnEmptyRange() {
        for (rel in listOf("ui/title/TitleScreen.kt", "ui/tv/TvTitleScreen.kt")) {
            val source = source(rel)
            assertFalse(
                "$rel no puede acotar con lastIndex (es -1 sin capítulos)",
                source.contains("coerceIn(0, episodes.lastIndex)"),
            )
            assertTrue("$rel usa la guarda compartida", source.contains("episodeIndexOrZero("))
        }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }

    private fun source(rel: String): String {
        val path = "src/main/java/com/chronosbvrx/visionplus/$rel"
        val found = listOf(File(path), File("app/$path"), File("../app/$path")).firstOrNull { it.exists() }
        org.junit.Assert.assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
