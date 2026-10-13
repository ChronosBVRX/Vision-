package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.Film
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OfflineRegistryAndStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OfflineRegistry.resetForTesting(context)
    }

    @Test
    fun testOfflineDownloadJsonRoundtrip() {
        val download = OfflineDownload(
            id = "xtv:12345",
            contentId = "12345",
            title = "Inception",
            subtitle = "1080p FHD",
            posterUrl = "https://example.com/poster.jpg",
            videoUrl = "xtv:12345",
            durationMs = 148 * 60_000L,
            quality = "1080p",
            state = DownloadState.COMPLETED,
            bytesDownloaded = 1_500_000_000L,
            totalBytes = 1_500_000_000L,
            vaultFilePath = "/data/user/0/app/vault/12345.vpack",
            storageLabel = "USB Kingston 64GB",
            isRemovable = true,
            isSeries = false,
        )

        val json = download.toJson()
        val parsed = OfflineDownload.fromJson(json)

        assertEquals(download.id, parsed.id)
        assertEquals(download.contentId, parsed.contentId)
        assertEquals(download.title, parsed.title)
        assertEquals(download.quality, parsed.quality)
        assertEquals(download.state, parsed.state)
        assertEquals(download.bytesDownloaded, parsed.bytesDownloaded)
        assertEquals(download.storageLabel, parsed.storageLabel)
        assertTrue(parsed.isRemovable)
        assertEquals("1.4 GB", download.formattedSize)
    }

    @Test
    fun testToFilmProducesVaultScheme() {
        val download = OfflineDownload(
            id = "xtv:movie99",
            contentId = "movie99",
            title = "Interestelar",
            posterUrl = "https://example.com/interstellar.jpg",
            durationMs = 169 * 60_000L,
            state = DownloadState.COMPLETED,
        )

        val film = download.toFilm()
        assertEquals("vault:xtv:movie99", film.id)
        assertEquals("vault:xtv:movie99", film.videoUrl)
        assertEquals("Interestelar", film.title)
        assertEquals(169, film.durationMin)
    }

    @Test
    fun testRegistryCrudOperations() {
        val testFile = File(context.filesDir, "test.vpack").apply { writeText("dummy") }
        val item = OfflineDownload(
            id = "xtv:film_a",
            contentId = "film_a",
            title = "Test Film A",
            vaultFilePath = testFile.absolutePath,
            state = DownloadState.QUEUED,
        )

        OfflineRegistry.put(item, context)
        assertEquals(1, OfflineRegistry.getAll(context).size)
        assertNotNull(OfflineRegistry.get("xtv:film_a", context))

        // Actualizar progreso
        OfflineRegistry.updateProgress("xtv:film_a", 500_000L, 1_000_000L)
        val inProgress = OfflineRegistry.get("xtv:film_a", context)
        assertEquals(DownloadState.DOWNLOADING, inProgress?.state)
        assertEquals(500_000L, inProgress?.bytesDownloaded)
        assertEquals(50, inProgress?.progressPercent)

        // Completar
        OfflineRegistry.updateState("xtv:film_a", DownloadState.COMPLETED)
        assertTrue(OfflineRegistry.isDownloaded("xtv:film_a", context))
        assertEquals(1, OfflineRegistry.getCompleted(context).size)

        // Borrar
        OfflineRegistry.remove("xtv:film_a", context)
        assertNull(OfflineRegistry.get("xtv:film_a", context))
        assertFalse(testFile.exists()) // Debe eliminar el archivo del almacenamiento
    }

    @Test
    fun testByteFormatting() {
        assertEquals("0 MB", OfflineStoragePolicy.formatBytes(0L))
        assertEquals("500 KB", OfflineStoragePolicy.formatBytes(500L * 1024L))
        assertEquals("450 MB", OfflineStoragePolicy.formatBytes(450L * 1024L * 1024L))
        assertEquals("1.5 GB", OfflineStoragePolicy.formatBytes((1.5 * 1024.0 * 1024.0 * 1024.0).toLong()))
    }

    @Test
    fun testSimulatedStubPurgedOnInit() {
        val fakeFile = File(context.filesDir, "fake_stub.vpack").apply { writeText("VISION_PLUS_PROTECTED_ASSET:stub") }
        val realFile = File(context.filesDir, "real_movie.vpack").apply { writeBytes(ByteArray(32 * 1024)) }

        val fakeItem = OfflineDownload(
            id = "xtv:fake_stub",
            contentId = "fake_stub",
            title = "Stub Movie",
            state = DownloadState.COMPLETED,
            bytesDownloaded = 64L,
            totalBytes = 64L,
            vaultFilePath = fakeFile.absolutePath,
        )
        val realItem = OfflineDownload(
            id = "xtv:real_movie",
            contentId = "real_movie",
            title = "Real Movie",
            state = DownloadState.COMPLETED,
            bytesDownloaded = 250_000_000L,
            totalBytes = 250_000_000L,
            vaultFilePath = realFile.absolutePath,
        )

        OfflineRegistry.put(fakeItem, context)
        OfflineRegistry.put(realItem, context)

        // Simular reinicio de la app recargando desde SharedPreferences
        OfflineRegistry.resetForTesting(null)
        OfflineRegistry.init(context)

        assertNull("El stub simulado <= 512B debe purgarse al iniciar", OfflineRegistry.get("xtv:fake_stub", context))
        assertFalse("El archivo .vpack del stub debe eliminarse", fakeFile.exists())
        assertNotNull("La descarga real debe conservarse", OfflineRegistry.get("xtv:real_movie", context))
        realFile.delete()
    }

    @Test
    fun testReconcileAdoptsValidVaultOnDisk() {
        val vault = File(context.filesDir, "adopted.vpack").apply { writeBytes(ByteArray(64 * 1024)) }
        val stale = OfflineDownload(
            id = "xtv:stale",
            contentId = "stale",
            title = "Stale",
            state = DownloadState.DOWNLOADING,
            bytesDownloaded = 10L,
            totalBytes = 999L,
            vaultFilePath = vault.absolutePath,
        )
        val adopted = OfflineRegistry.reconcileWithDisk(stale)
        assertNotNull(adopted)
        assertEquals(DownloadState.COMPLETED, adopted?.state)
        assertEquals(vault.length(), adopted?.bytesDownloaded)
        vault.delete()
    }

    @Test
    fun testReconcileDropsGhostCompleted() {
        val ghost = OfflineDownload(
            id = "xtv:ghost",
            contentId = "ghost",
            title = "Ghost",
            state = DownloadState.COMPLETED,
            bytesDownloaded = 9000L,
            totalBytes = 9000L,
            vaultFilePath = File(context.filesDir, "noexiste.vpack").absolutePath,
        )
        assertNull(OfflineRegistry.reconcileWithDisk(ghost))
    }

    @Test
    fun testProgressFileRoundtripAndSync() {
        val vaultDir = File(context.filesDir, "vaultsync").apply { mkdirs() }
        val item = OfflineDownload(
            id = "xtv:syncme",
            contentId = "syncme",
            title = "Sync",
            state = DownloadState.DOWNLOADING,
            bytesDownloaded = 0L,
            totalBytes = 1000L,
            vaultFilePath = File(vaultDir, "x.vpack").absolutePath,
        )
        OfflineRegistry.put(item, context)
        assertNull(OfflineProgressFile.read(vaultDir, item.id))
        // Escrituras dentro del mismo segundo se fusionan (throttle 1/s): el sync
        // adopta la primera y la segunda tras 1 s.
        OfflineProgressFile.write(vaultDir, item.id, 400L, 1000L)
        OfflineProgressFile.write(vaultDir, item.id, 700L, 1000L)
        assertEquals(1, OfflineProgressSync.syncOnce(context))
        assertEquals(400L, OfflineRegistry.get(item.id, context)?.bytesDownloaded)
        Thread.sleep(1100L)
        OfflineProgressFile.write(vaultDir, item.id, 700L, 1000L)
        assertEquals(1, OfflineProgressSync.syncOnce(context))
        assertEquals(700L, OfflineRegistry.get(item.id, context)?.bytesDownloaded)
        OfflineProgressFile.clear(vaultDir, item.id)
        assertNull(OfflineProgressFile.read(vaultDir, item.id))
        vaultDir.deleteRecursively()
    }

    @Test
    fun testNetworkMonitorConnectivityTransitions() {
        try {
            NetworkMonitor.setOnlineForTesting(false)
            assertFalse(NetworkMonitor.refresh(context))
            assertFalse(NetworkMonitor.isOnline.value)

            NetworkMonitor.setOnlineForTesting(true)
            assertTrue(NetworkMonitor.refresh(context))
            assertTrue(NetworkMonitor.isOnline.value)
        } finally {
            NetworkMonitor.setOnlineForTesting(null)
        }
    }
}
