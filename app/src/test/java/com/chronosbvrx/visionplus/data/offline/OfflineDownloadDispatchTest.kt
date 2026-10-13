package com.chronosbvrx.visionplus.data.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.ui.EpisodePlay
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardianes anti-regresión v2.5.51:
 * 1. El principal JAMÁS resuelve Ranger (solo http directo o delegación a :x4k).
 * 2. Las series descargan el C# elegido, no el seriesId.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OfflineDownloadDispatchTest {

    private fun fakeVolume(): StorageVolumeInfo = StorageVolumeInfo(
        id = "internal_primary",
        label = "Almacenamiento interno",
        rootDir = File("/tmp"),
        vaultDir = File("/tmp/vision_vault"),
        isRemovable = false,
        totalBytes = 8L * 1024 * 1024 * 1024,
        freeBytes = 4L * 1024 * 1024 * 1024,
        usableBytes = 4L * 1024 * 1024 * 1024,
    )

    private fun movieFilm(): Film = Film(
        id = "xtv:movie1",
        title = "Película Test",
        year = 2025,
        durationMin = 100,
        synopsis = "",
        posterUrl = "",
        videoUrl = "xtv:movie1",
        sourceUrl = "xtv",
    )

    @Test
    fun `http directo no necesita resolve remoto`() {
        assertFalse(OfflineDownloadManager.needsRemoteResolve("https://cdn.example.com/video.mp4"))
        assertFalse(OfflineDownloadManager.needsRemoteResolve("http://127.0.0.1:8080/proxy.m3u8"))
    }

    @Test
    fun `refs xtv y magisvod delegan a x4k`() {
        assertTrue(OfflineDownloadManager.needsRemoteResolve("xtv:abc123"))
        assertTrue(OfflineDownloadManager.needsRemoteResolve("magisvod:abc123"))
        assertTrue(OfflineDownloadManager.needsRemoteResolve("playCodeSinHttp"))
    }

    @Test
    fun `pelicula construye item con su propio id`() {
        val item = OfflineDownloadManager.buildDownloadItem(movieFilm(), null, fakeVolume(), "1080p")
        assertEquals("xtv:movie1", item.id)
        assertEquals("xtv:movie1", item.videoUrl)
        assertFalse(item.isSeries && item.subtitle.isNotBlank())
    }

    @Test
    fun `serie usa el codigo del capitulo elegido`() {
        val seriesFilm = movieFilm().copy(id = "xtv:serie9", videoUrl = "xtv:serie9")
        val ep = EpisodePlay(
            code = "epcode777",
            seriesId = "serie9",
            episodeIndex = 2,
            seasonIndex = 1,
            durationMs = 45 * 60_000L,
        )
        val item = OfflineDownloadManager.buildDownloadItem(seriesFilm, ep, fakeVolume(), "720p")
        assertEquals("xtv:serie9:ep:epcode777", item.id)
        assertEquals("xtv:epcode777", item.videoUrl)
        assertEquals("serie9", item.seriesId)
        assertEquals("T2 · C3", item.subtitle)
        assertTrue(item.isSeries)
    }

    @Test
    fun `episodio con prefijo xtv se conserva`() {
        val seriesFilm = movieFilm().copy(id = "xtv:serie9", videoUrl = "xtv:serie9")
        val ep = EpisodePlay(code = "xtv:epcode5", seriesId = "serie9")
        val item = OfflineDownloadManager.buildDownloadItem(seriesFilm, ep, fakeVolume(), "1080p")
        assertEquals("xtv:epcode5", item.videoUrl)
    }

    @Test
    fun `manager principal no referencia al motor nativo`() {
        // Si alguien reintroduce X4KTitan/X4KApi en el manager, este test lo delata
        // revisando el fuente: el principal solo delega vía Intent al servicio :x4k.
        val src = java.io.File(
            "app/src/main/java/com/chronosbvrx/visionplus/data/offline/OfflineDownloadManager.kt",
        )
        if (src.exists()) {
            val text = src.readText()
            assertFalse(
                "OfflineDownloadManager (principal) no debe importar X4KTitan: bootearía Ranger fuera de :x4k",
                text.contains("import com.chronosbvrx.visionplus.data.xuper.X4KTitan"),
            )
            assertFalse(
                "OfflineDownloadManager (principal) no debe llamar prepareVod: es trabajo del servicio :x4k",
                text.contains("prepareVod("),
            )
        }
    }
}
