package com.chronosbvrx.visionplus

import com.chronosbvrx.visionplus.data.diag.Diagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ruido de la telemetría que llegó al VPS (2026-10-25):
 *
 *  - 336 de 464 «ANR» eran `nativePollOnce`: el proceso estaba congelado en segundo plano y
 *    `uptimeMillis` seguía corriendo. Además la huella incluía los segundos exactos
 *    (`MAIN_BLOCKED_51s`, `52s`…), así que cada duración disparaba una alerta nueva.
 *  - Un prefetch de licencia cancelado al salir de la ficha se reportaba como
 *    `PLAYBACK_LICENSE_FAIL` (una falsa alarma de reproducción).
 */
class DiagnosticsNoiseTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun anrCodeGroupsByRangeInsteadOfExactSeconds() {
        assertEquals("MAIN_BLOCKED_5-9s", Diagnostics.anrCode(5_000))
        assertEquals("MAIN_BLOCKED_5-9s", Diagnostics.anrCode(9_999))
        assertEquals("MAIN_BLOCKED_10-59s", Diagnostics.anrCode(10_000))
        assertEquals("MAIN_BLOCKED_10-59s", Diagnostics.anrCode(59_999))
        assertEquals("MAIN_BLOCKED_1-10min", Diagnostics.anrCode(60_000))
        assertEquals("MAIN_BLOCKED_1-10min", Diagnostics.anrCode(599_999))
        assertEquals("MAIN_BLOCKED_mas-de-10min", Diagnostics.anrCode(600_000))
        assertEquals("MAIN_BLOCKED_mas-de-10min", Diagnostics.anrCode(5_121_000))
        // 51s y 52s son la MISMA huella: una sola alerta, no una por segundo.
        assertEquals(Diagnostics.anrCode(51_000), Diagnostics.anrCode(52_000))
    }

    @Test
    fun aFrozenBackgroundProcessIsNotAnAnr() {
        // Al frente de punta a punta: el hueco sí cuenta.
        assertEquals(12_000, Diagnostics.anrBlockedMs(1_000, 13_000, foregroundBefore = true, foregroundNow = true))
        // El proceso estaba congelado en segundo plano (el caso de los 85 minutos).
        assertEquals(0, Diagnostics.anrBlockedMs(1_000, 5_122_000, foregroundBefore = false, foregroundNow = false))
        // Se fue a segundo plano durante el intervalo: tampoco.
        assertEquals(0, Diagnostics.anrBlockedMs(1_000, 9_000, foregroundBefore = true, foregroundNow = false))
        // Volvió al frente a mitad del hueco: no se puede atribuir al hilo principal.
        assertEquals(0, Diagnostics.anrBlockedMs(1_000, 9_000, foregroundBefore = false, foregroundNow = true))
        // Sin tick (el post no llegó a correr) no hay medida.
        assertEquals(0, Diagnostics.anrBlockedMs(0, 9_000, foregroundBefore = true, foregroundNow = true))
    }

    @Test
    fun theWatchdogOnlyMeasuresWhileTheAppIsInFront() {
        val diag = source("data/diag/Diagnostics.kt")
        assertTrue("el vigilante no consulta el primer plano", diag.contains("val foregroundBefore = isForeground()"))
        assertTrue("el vigilante no usa la medida filtrada", diag.contains("anrBlockedMs(tick.get(), now, foregroundBefore, isForeground())"))
        assertTrue("la duración exacta se pierde", diag.contains("message = \"blockedMs=\$blockedMs\""))
    }

    @Test
    fun bothActivitiesReportWhenTheyAreInFront() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("Diagnostics.activityResumed()"))
        assertTrue(main.contains("Diagnostics.activityPaused()"))
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue(player.contains("Diagnostics.activityResumed()"))
        assertTrue(player.contains("Diagnostics.activityPaused()"))
    }

    @Test
    fun aCancelledPrefetchIsNotAPlaybackFailure() {
        val api = source("data/xuper/X4KApi.kt")
        assertTrue("falta la marca de cancelación", api.contains("var cancelledLicense = false"))
        assertTrue(
            "la cancelación no se distingue",
            api.contains("if (it is CancellationException) cancelledLicense = true"),
        )
        assertTrue("la cancelación se reporta como fallo", api.contains("if (medias == null && !cancelledLicense)"))
        // Los tres caminos de licencia (local + dos del bucle) marcan la cancelación.
        val marks = Regex("if \\(it is CancellationException\\) cancelledLicense = true").findAll(api).count()
        assertTrue("solo $marks caminos distinguen la cancelación", marks >= 3)
    }
}
