package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * «Mi lista → Historial»: lo visto se conserva con su avance y se puede reanudar.
 *
 * Los guardianes puros viven en PlaybackHistoryStoreTest/WatchNextPlanTest; este vigila
 * que las dos pantallas, los reproductores y los espejos (launcher/control remoto) usen
 * la pieza correcta y que «Seguir viendo» no se llene de cosas terminadas.
 */
class LibraryHistoryWiringTest {

    @Test
    fun bothLibrariesShowHistoryWithProgressAndResume() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/phone/LibraryScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvLibraryScreen.kt")
        for ((name, text) in listOf("móvil" to phone, "TV" to tv)) {
            assertTrue("$name tiene la pestaña Historial", text.contains("Historial"))
            assertTrue("$name lee el historial completo", text.contains("PlaybackHistory.historyCards"))
            assertTrue("$name reanuda", text.contains("onResume"))
            assertTrue("$name marca los terminados", text.contains("VISTO"))
            assertTrue("$name pinta el avance", text.contains("card.progress"))
        }
        val phoneApp = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        val tvApp = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/TvApp.kt")
        assertTrue(phoneApp.contains("onResume = { nav.push(resumeRoute(it)) }"))
        assertTrue(tvApp.contains("resumeRoute(it)"))
    }

    @Test
    fun theHistoryStoreKeepsFinishedAndContinueFiltersThem() {
        val data = requireSource("src/main/java/com/chronosbvrx/visionplus/data/PlaybackHistory.kt")
        assertTrue(data.contains("fun historyCards"))
        assertTrue(data.contains("shouldKeep(it.positionMs, it.durationMs)"))
        assertTrue("los terminados ya no se borran", data.contains("val next = prune(listOf(entry) + entries"))
        assertFalse("la fila Seguir viendo no debe recibir terminados", data.contains("if (finished) { entries }"))
    }

    @Test
    fun playersSaveFinishedEntriesAndMirrorsSkipThem() {
        val native = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        val exo = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("el nativo guarda al terminar", native.contains("forceFinished = true"))
        assertTrue("el nativo no degrada un terminado en el stop", native.contains("finishedSaved"))
        assertFalse("el nativo ya no borra al terminar", native.contains("PlaybackHistory.delete(this, curCode)"))
        assertTrue("el ExoPlayer guarda el fin del búnker", exo.contains("saveHistory(finished = true)"))
        assertTrue("el ExoPlayer no degrada un terminado", exo.contains("finishedSaved"))

        val watchNext = requireSource("src/main/java/com/chronosbvrx/visionplus/data/launcher/WatchNextPlan.kt")
        assertTrue("el launcher solo espeja lo reanudable", watchNext.contains("shouldKeep(entry.positionMs, entry.durationMs)"))
        val remote = requireSource("src/main/java/com/chronosbvrx/visionplus/data/remote/RemoteResume.kt")
        assertTrue("el control remoto no manda el final", remote.contains("shouldKeep(it.positionMs, it.durationMs)"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
