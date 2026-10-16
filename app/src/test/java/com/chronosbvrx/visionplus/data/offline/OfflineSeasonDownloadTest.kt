package com.chronosbvrx.visionplus.data.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardianes del lote «descargas de series»:
 * 1. Resumen y etiqueta de la temporada (puro).
 * 2. Encadenado offline: el «siguiente descargado» de la misma serie.
 * 3. La cola de un solo motor existe en el manager (una descarga `xtv:` por vez) y el
 *    servicio la respalda con su mutex (los intents en carrera se ordenan).
 * 4. Las dos fichas ofrecen descargar temporada y descargar por capítulo.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OfflineSeasonDownloadTest {

    private fun download(
        id: String,
        state: DownloadState = DownloadState.COMPLETED,
        seriesId: String = "",
        season: Int = 0,
        episode: Int = 0,
        isSeries: Boolean = seriesId.isNotBlank(),
        videoUrl: String = "xtv:abc",
    ) = OfflineDownload(
        id = id,
        contentId = "xtv:serie",
        title = "Serie",
        subtitle = "T${season + 1} · C${episode + 1}",
        videoUrl = videoUrl,
        state = state,
        vaultFilePath = "/tmp/vision_vault/$id.vpack",
        isSeries = isSeries,
        seriesId = seriesId,
        seasonIndex = season,
        episodeIndex = episode,
    )

    // ------------------------------------------------------ resumen de temporada

    @Test
    fun `resumen cuenta completados activos pendientes y faltantes`() {
        val items = listOf(
            download("a"),
            download("b", DownloadState.DOWNLOADING),
            download("c", DownloadState.QUEUED),
            download("d", DownloadState.PAUSED),
            download("e", DownloadState.ERROR),
            null,
            null,
        )
        val s = seasonDownloadSummary(items)
        assertEquals(7, s.total)
        assertEquals(1, s.completed)
        assertEquals(2, s.active)
        assertEquals(2, s.pending)
        assertEquals(2, s.missing)
        assertEquals(4, s.actionable)
        assertFalse(s.allDone)
    }

    @Test
    fun `temporada entera completada`() {
        val s = seasonDownloadSummary(listOf(download("a"), download("b")))
        assertTrue(s.allDone)
        assertEquals("Temporada descargada", seasonDownloadLabel(s))
        assertFalse("con todo bajado el botón no hace nada", seasonDownloadActionable(s))
    }

    @Test
    fun `etiqueta de temporada segun el estado`() {
        assertEquals(
            "Descargar temporada · 10 de 10",
            seasonDownloadLabel(SeasonDownloadSummary(total = 10, completed = 0, active = 0, pending = 0, missing = 10)),
        )
        assertEquals(
            "Descargando · 2 de 10",
            seasonDownloadLabel(SeasonDownloadSummary(total = 10, completed = 2, active = 2, pending = 0, missing = 6)),
        )
        assertEquals(
            "Reanudar temporada · 3",
            seasonDownloadLabel(SeasonDownloadSummary(total = 10, completed = 7, active = 0, pending = 3, missing = 0)),
        )
        assertEquals(
            "Completar temporada · 5 de 10",
            seasonDownloadLabel(SeasonDownloadSummary(total = 10, completed = 5, active = 0, pending = 2, missing = 3)),
        )
    }

    @Test
    fun `el boton de temporada se activa solo cuando hay que trabajar`() {
        assertTrue(
            seasonDownloadActionable(SeasonDownloadSummary(total = 4, completed = 1, active = 0, pending = 0, missing = 3)),
        )
        assertTrue(
            seasonDownloadActionable(SeasonDownloadSummary(total = 4, completed = 2, active = 0, pending = 2, missing = 0)),
        )
        assertFalse("con descargas en curso no se re-encola", seasonDownloadActionable(SeasonDownloadSummary(total = 4, completed = 1, active = 1, pending = 0, missing = 2)))
        assertFalse(seasonDownloadActionable(SeasonDownloadSummary(0, 0, 0, 0, 0)))
    }

    // ------------------------------------------------------ siguiente descargado

    @Test
    fun `siguiente es el capitulo inmediato de la misma temporada`() {
        val season = (0 until 5).map { download("ep$it", seriesId = "s1", season = 0, episode = it) }
        val next = nextDownloadedEpisode(season[1], season)
        assertEquals("ep2", next?.id)
    }

    @Test
    fun `si el inmediato no esta descargado no salta a otro`() {
        val items = listOf(
            download("ep0", seriesId = "s1", season = 0, episode = 0),
            download("ep1", seriesId = "s1", season = 0, episode = 1),
            // ep2 pausado: no cuenta
            download("ep2", DownloadState.PAUSED, seriesId = "s1", season = 0, episode = 2),
            download("ep3", seriesId = "s1", season = 0, episode = 3),
        )
        assertNull("el hueco de ep2 detiene la cadena", nextDownloadedEpisode(items[1], items))
    }

    @Test
    fun `cruce de temporada va al primer capitulo disponible`() {
        val items = listOf(
            download("t1c3", seriesId = "s1", season = 0, episode = 2),
            download("t2c1", seriesId = "s1", season = 1, episode = 0),
            download("t2c2", seriesId = "s1", season = 1, episode = 1),
        )
        assertEquals("t2c1", nextDownloadedEpisode(items[0], items)?.id)
    }

    @Test
    fun `otra serie o pelicula no encadena`() {
        val current = download("ep0", seriesId = "s1", season = 0, episode = 0)
        val others = listOf(
            download("otro", seriesId = "s2", season = 0, episode = 1),
            download("peli", isSeries = false),
        )
        assertNull(nextDownloadedEpisode(current, others))
        assertNull(
            "una película descargada no tiene siguiente",
            nextDownloadedEpisode(download("peli", seriesId = "", isSeries = false), others),
        )
    }

    // ------------------------------------------------------ guardianes de fuente

    @Test
    fun `el manager serializa el motor con una cola`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/OfflineDownloadManager.kt")
        assertTrue("debe existir la cola de refs remotas", text.contains("remoteQueue"))
        assertTrue("una sola descarga activa a la vez", text.contains("activeRemoteId"))
        assertTrue("debe existir el pump de la cola", text.contains("pumpRemote("))
        assertTrue("el fin del trabajador arranca la siguiente", text.contains("onWorkerFinished("))
        assertTrue("debe existir la API de temporada", text.contains("fun enqueueSeason("))
        assertTrue("el vigilante libera la cola si el proceso muere", text.contains("startWatchdog("))
        assertTrue("las pendientes cuentan como motor ocupado", text.contains("DownloadState.QUEUED"))
    }

    @Test
    fun `el servicio ordena los intents en carrera con un mutex`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/X4KDownloadService.kt")
        assertTrue(text.contains("engineMutex"))
        assertTrue(text.contains("runEngineWork("))
        assertTrue("el trabajo del motor corre bajo el candado", text.contains("engineMutex.withLock"))
    }

    @Test
    fun `el fin del servicio avisa al manager para seguir la cola`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/OfflineProgressReceiver.kt")
        val done = Regex("OfflineDownloadManager\\.onWorkerFinished\\(").findAll(text).count()
        assertTrue("DONE y ERROR deben continuar la cola (hay $done)", done >= 2)
    }

    @Test
    fun `marcas de resultado se leen y se limpian`() {
        val dir = java.nio.file.Files.createTempDirectory("vp-outcome").toFile()
        val id = "xtv:serie:ep:abc"
        assertNull(OfflineProgressFile.readDone(dir, id))
        OfflineProgressFile.writeDone(dir, id, 123L)
        assertEquals(123L, OfflineProgressFile.readDone(dir, id))
        OfflineProgressFile.clearOutcome(dir, id)
        assertNull(OfflineProgressFile.readDone(dir, id))
        OfflineProgressFile.writeError(dir, id, "sin red")
        assertEquals("sin red", OfflineProgressFile.readError(dir, id))
        OfflineProgressFile.clearOutcome(dir, id)
        assertNull(OfflineProgressFile.readError(dir, id))
        dir.deleteRecursively()
    }

    @Test
    fun `el resultado final viaja por archivo (respaldo del bus)`() {
        val service = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/X4KDownloadService.kt")
        assertTrue("el servicio deja la marca .done al completar", service.contains("writeDone("))
        assertTrue("el servicio deja la marca .error al fallar", service.contains("writeError("))
        val sync = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/OfflineProgressSync.kt")
        assertTrue("el ticker adopta el resultado y arranca la cola", sync.contains("onWorkerFinished("))
        assertTrue("el ticker lee las marcas", sync.contains("readDone(") && sync.contains("readError("))
        val manager = requireSource("src/main/java/com/chronosbvrx/visionplus/data/offline/OfflineDownloadManager.kt")
        assertTrue("una corrida nueva limpia marcas viejas", manager.contains("clearOutcome("))
    }

    @Test
    fun `las dos fichas ofrecen descargar temporada y por capitulo`() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt")
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt")
        for ((name, text) in listOf("TitleScreen" to phone, "TvTitleScreen" to tv)) {
            assertTrue("$name debe encolar la temporada", text.contains("enqueueSeason("))
            assertTrue("$name debe ofrecer la descarga por capítulo", text.contains("onDownloadEpisode"))
            assertTrue("$name debe mostrar el resumen de temporada", text.contains("seasonDownloadSummary("))
            assertTrue("$name debe poder reproducir la descarga del capítulo", text.contains("onPlayDownloaded"))
        }
    }

    @Test
    fun `la ficha movil no ofrece descargas si el servidor las apago`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt")
        assertTrue(text.contains("downloads_enabled"))
    }

    @Test
    fun `el reproductor offline es congruente con la ficha y con el encadenado`() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(
            "la ficha puede pedir un capítulo descargado (EpisodePlay con code vault:)",
            text.contains("episode?.code?.startsWith(\"vault:\")"),
        )
        assertTrue(
            "al terminar un capítulo se ofrece el siguiente descargado",
            text.contains("handleVaultEnded(") && text.contains("playNextVault("),
        )
        assertTrue("la cadena usa el siguiente descargado real", text.contains("nextDownloadedEpisode("))
        assertTrue("el búnker conserva el bloqueo de capturas", text.contains("FLAG_SECURE"))
        assertTrue("el capítulo terminado cuenta como visto", text.contains("markEpisodeWatched("))
        assertTrue("honra «Reanudar automáticamente»", text.contains("autoResumeOf("))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel"))
            .firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
