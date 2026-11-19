package com.chronosbvrx.visionplus.data.external

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guardianes de cableado: módulo, ficha, reproductor y worker. */
class External4KWiringTest {

    @Test
    fun thePlayerUsesAFreshExternalSourceAndFallsBackToNative() {
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(player.contains("External4KRepository.fresh"))
        assertTrue(player.contains("External4KPlayback"))
        assertTrue(player.contains("skipExternal = true"))
        assertTrue(player.contains("\"EXTERNAL_4K_FALLBACK\""))
        assertTrue(player.contains("onExternalFatal"))
    }

    @Test
    fun bothTitleSheetsRefreshAndAnnounceFourK() {
        for (path in listOf(
            "app/src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt",
            "app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt",
        )) {
            val src = source(path)
            assertTrue("$path refresca", src.contains("External4KRepository.refreshIfStale"))
            assertTrue("$path anuncia", src.contains("4K"))
        }
    }

    @Test
    fun theAppSchedulesThePeriodicWorkerAndBundlesWorkManager() {
        val app = source("app/src/main/java/com/chronosbvrx/visionplus/VisionApp.kt")
        assertTrue(app.contains("External4KWorker.ensure"))
        val gradle = source("app/build.gradle.kts")
        assertTrue(gradle.contains("androidx.work:work-runtime-ktx"))
        val worker = source("app/src/main/java/com/chronosbvrx/visionplus/data/external/External4KWorker.kt")
        assertTrue(worker.contains("enqueueUniquePeriodicWork"))
        assertTrue(worker.contains("6, TimeUnit.HOURS"))
    }

    private fun source(rel: String): String {
        val found = listOf(File(rel), File("../$rel"), File("Vision-/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
