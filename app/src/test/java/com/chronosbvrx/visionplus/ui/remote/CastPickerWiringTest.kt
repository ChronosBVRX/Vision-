package com.chronosbvrx.visionplus.ui.remote

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado del selector de temporada/capítulo, que no se puede componer en pruebas sin red:
 * - El buscador del control remoto abre el selector para series (jamás manda la serie «en
 *   general», que abría en la tele sin el capítulo que uno está eligiendo).
 * - La ficha móvil también lo abre desde «Ver en la TV».
 * - La tele pasa el contexto de la orden remota al deep link que abre el capítulo exacto.
 */
class CastPickerWiringTest {

    @Test
    fun theRemoteSearchOpensThePickerForSeriesAndSendsTheExactEpisode() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/remote/RemoteScreen.kt")
        assertTrue(text.contains("isCastSeries(vod.film)"))
        assertTrue(text.contains("CastEpisodeDialog("))
        assertTrue(text.contains("seriesId = ep.seriesId"))
        assertTrue("la fila avisa que hay capítulos por elegir", text.contains("\"Capítulos\""))
    }

    @Test
    fun thePhoneFichaAlsoPicksASeasonAndEpisodeBeforeCasting() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt")
        assertTrue(text.contains("CastEpisodeDialog("))
        assertTrue(text.contains("if (isSeriesTitle) {"))
        assertTrue(text.contains("seriesId = ep.seriesId"))
    }

    @Test
    fun theTvPassesTheEpisodeContextToTheDeepLink() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/remote/RemoteServer.kt")
        assertTrue(text.contains("seriesId = msg.series.orEmpty()"))
        assertTrue(text.contains("seasonIds = msg.seasonIds.orEmpty()"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
