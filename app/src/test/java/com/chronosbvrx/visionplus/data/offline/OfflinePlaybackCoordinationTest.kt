package com.chronosbvrx.visionplus.data.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardianes v2.5.62: descargar y reproducir en línea NUNCA se cruzan en el motor nativo.
 * La regresión reportada: con una descarga XTV activa el reproductor abría un segundo
 * programa en el mismo motor (`:x4k`) y la película se buggeaba (o moría la descarga).
 *
 * Reglas cubiertas:
 * 1. Solo una descarga XTV en curso (ref `xtv:`/`magisvod:`) bloquea la reproducción;
 *    una descarga http directa del principal no toca el motor.
 * 2. El reproductor se abre únicamente cuando el servicio confirmó la pausa con el
 *    marcador `.paused` (ack tras `stopHeadless`).
 * 3. "Pausar" de verdad detiene al servicio `:x4k` (antes solo cambiaba la etiqueta).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OfflinePlaybackCoordinationTest {

    private fun item(id: String, state: DownloadState, url: String) = OfflineDownload(
        id = id,
        contentId = id,
        title = "Título $id",
        videoUrl = url,
        state = state,
        bytesDownloaded = 42L,
        totalBytes = 100L,
        vaultFilePath = "/tmp/vision_vault/$id.vpack",
    )

    @Test
    fun `solo las descargas XTV activas bloquean el motor`() {
        val items = listOf(
            item("http1", DownloadState.DOWNLOADING, "https://cdn.example.com/a.mp4"),
            item("xtv1", DownloadState.DOWNLOADING, "xtv:abc"),
            item("mv1", DownloadState.DOWNLOADING, "magisvod:def"),
            item("xtv2", DownloadState.PAUSED, "xtv:pausada"),
            item("xtv3", DownloadState.COMPLETED, "xtv:completa"),
            item("xtv4", DownloadState.ERROR, "xtv:error"),
        )
        assertEquals(
            listOf("xtv1", "mv1"),
            OfflineDownloadManager.engineDownloadsToPause(items).map { it.id },
        )
    }

    @Test
    fun `marcador de pausa convive con el progreso sin chocar`() {
        val dir = java.nio.file.Files.createTempDirectory("vp-pause").toFile()
        val id = "xtv:movie1"
        OfflineProgressFile.write(dir, id, 50L, 100L)
        assertFalse(OfflineProgressFile.hasPauseFlag(dir, id))
        OfflineProgressFile.writePauseFlag(dir, id)
        assertTrue(OfflineProgressFile.hasPauseFlag(dir, id))
        assertEquals(50L to 100L, OfflineProgressFile.read(dir, id))
        OfflineProgressFile.clearPauseFlag(dir, id)
        assertFalse(OfflineProgressFile.hasPauseFlag(dir, id))
        assertEquals(50L to 100L, OfflineProgressFile.read(dir, id))
    }

    @Test
    fun `servicio pausa suelta el motor y confirma con marcador`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/X4KDownloadService.kt")
        assertTrue("ACTION_PAUSE debe existir", text.contains("ACTION_PAUSE"))
        assertTrue("pauseIntent debe existir", text.contains("fun pauseIntent("))
        assertTrue("la pausa debe soltar el motor al instante", text.contains("releaseHeadlessLocked"))
        assertTrue("la pausa debe confirmar con el marcador .paused", text.contains("writePauseFlag"))
        assertTrue("el arranque del motor va bajo el candado anti-carrera", text.contains("synchronized(pauseLock)"))
        assertTrue("la pausa se arbitra por marca temporal (sin marcas pegadas)", text.contains("pauseRequestedAtMs"))
        val partialCleanups = Regex(Regex.escape("\$vaultPath.downloading")).findAll(text).count()
        assertTrue("pausa y error deben borrar el parcial .downloading (hay $partialCleanups)", partialCleanups >= 2)
    }

    @Test
    fun `gestor pausa de verdad y espera el ack del servicio`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/OfflineDownloadManager.kt")
        assertTrue(text.contains("fun pauseEngineDownloadsForPlayback("))
        assertTrue("pausar debe avisar al servicio :x4k", text.contains("pauseIntent("))
        assertTrue("la espera del ack es por archivo", text.contains("hasPauseFlag("))
        assertTrue("borrar una descarga debe limpiar el parcial", text.contains("\${doomed.vaultFilePath}.downloading"))
    }

    @Test
    fun `la puerta cubre las dos ramas nativas del reproductor`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        val uses = Regex("EngineDownloadGate\\(").findAll(text).count()
        assertTrue("EngineDownloadGate debe envolver VOD y vivo (definición + 2 usos, hay $uses)", uses >= 3)
        assertTrue(
            "el fallback Titan→XTV también debe checar descargas activas",
            text.contains("activeEngineDownload(context)"),
        )
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
