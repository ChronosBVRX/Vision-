package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PortableLibraryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var volume: StorageVolumeInfo
    private lateinit var pack: File
    private lateinit var item: OfflineDownload

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OfflineRegistry.resetForTesting(context)
        val root = tempFolder.newFolder("usb")
        val vault = File(root, "vision_vault").apply { mkdirs() }
        volume = StorageVolumeInfo(
            id = "usb-test",
            label = "USB de prueba",
            rootDir = root,
            vaultDir = vault,
            isRemovable = true,
            totalBytes = 8L * 1024L * 1024L * 1024L,
            freeBytes = 4L * 1024L * 1024L * 1024L,
            usableBytes = 4L * 1024L * 1024L * 1024L,
        )
        val portable = PortableLibrary.portableDir(volume)
        pack = File(portable, "movie.pvpack")
        val payload = ByteArray(64 * 1024).also { java.util.Random(11).nextBytes(it) }
        PortableVaultCrypto.encryptStreamToFile(
            input = ByteArrayInputStream(payload),
            totalLength = payload.size.toLong(),
            destinationFile = pack,
        )
        item = OfflineDownload(
            id = "xtv:movie",
            contentId = "xtv:movie",
            title = "Película de prueba",
            subtitle = "",
            videoUrl = "xtv:movie",
            state = DownloadState.COMPLETED,
            bytesDownloaded = pack.length(),
            totalBytes = pack.length(),
            vaultFilePath = pack.absolutePath,
            storageLabel = volume.label,
            isRemovable = true,
            portable = true,
        )
    }

    @Test
    fun portableFieldsSurviveTheRegistryJsonRoundTrip() {
        val parsed = OfflineDownload.fromJson(
            item.copy(portableLibraryId = "lib-123").toJson(),
        )
        assertTrue(parsed.portable)
        assertEquals("lib-123", parsed.portableLibraryId)
    }

    @Test
    fun finalizeWritesKeyMetaAndPin() {
        assertTrue(PortableLibrary.finalizeDownload(context, item, volume))
        assertTrue(PortableLibrary.pvKeyFile(pack).exists())
        assertTrue(PortableLibrary.pvMetaFile(pack).exists())
        assertTrue(PortableLibrary.libraryFile(volume).exists())

        val pin = PortableLibrary.pinForDownload(context, item)
        assertNotNull(pin)
        assertEquals(4, pin!!.length)
        assertTrue(pin.all { it.isDigit() })
        assertTrue(PortableLibrary.isPortableReady(item))
    }

    @Test
    fun scanIsLockedUntilPinUnlocksIt() {
        assertTrue(PortableLibrary.finalizeDownload(context, item, volume))
        val pin = PortableLibrary.pinForDownload(context, item)!!

        // Simula la TV de destino: su registro no conoce la descarga de origen.
        OfflineRegistry.resetForTesting(context)
        val locked = PortableLibrary.scanVolume(context, volume).single()
        assertFalse(locked.unlocked)

        val wrong = PortableLibrary.unlock(context, locked, "0000", volume)
        assertTrue(wrong is PortableLibrary.UnlockResult.Wrong)
        assertNull(OfflineRegistry.get(locked.registryId, context))

        val ok = PortableLibrary.unlock(context, locked, pin, volume)
        assertTrue(ok is PortableLibrary.UnlockResult.Unlocked)
        assertNotNull(OfflineRegistry.get(locked.registryId, context))
        assertTrue(PortableLibrary.scanVolume(context, volume).single().unlocked)
    }

    @Test
    fun targetTvOpensWithPinAndLibraryKey() {
        // Simula que el `.pvpack` lo cifró OTRA TV: la cabecera lleva una llave de
        // dispositivo que esta TV no puede desenvolver. El único camino es el PIN.
        OfflineRegistry.resetForTesting(context)
        val library = PortableLibrary.ensureLibrary(context, volume)!!
        val remoteDeviceKey = SecretKeySpec(PortableVaultCrypto.randomKey(), "AES")
        var contentKey = ByteArray(0)
        val payload = ByteArray(48 * 1024).also { java.util.Random(21).nextBytes(it) }
        PortableVaultCrypto.encryptStreamToFileForTest(
            input = ByteArrayInputStream(payload),
            totalLength = payload.size.toLong(),
            destinationFile = pack,
            deviceWrapKey = remoteDeviceKey,
            onContentKey = { contentKey = it },
        )
        PortableLibrary.pvKeyFile(pack).writeText(
            JSONObject()
                .put("v", 2)
                .put("libraryId", library.libraryId)
                .put("downloadId", "movie")
                .put("wrappedKey", PortableVaultCrypto.wrapWithLibraryKey(contentKey, library.libraryKey))
                .toString(),
        )
        PortableLibrary.pvMetaFile(pack).writeText(
            JSONObject()
                .put("v", 2)
                .put("libraryId", library.libraryId)
                .put("downloadId", "movie")
                .put("title", "Película remota")
                .put("pvpack", pack.name)
                .put("pvkey", PortableLibrary.pvKeyFile(pack).name)
                .put("bytes", pack.length())
                .toString(),
        )

        val locked = PortableLibrary.scanVolume(context, volume).single()
        assertFalse(locked.unlocked)
        val result = PortableLibrary.unlock(context, locked, library.pin, volume)
        assertTrue(result is PortableLibrary.UnlockResult.Unlocked)
        val imported = OfflineRegistry.getAll(context).single()
        assertEquals("pv:${library.libraryId}:movie", imported.id)

        val source = EncryptedVaultDataSource(context)
        source.open(
            DataSpec.Builder().setUri(Uri.parse("vault:${imported.id}")).build(),
        )
        val read = ByteArray(payload.size)
        var total = 0
        while (total < read.size) {
            val n = source.read(read, total, read.size - total)
            if (n == C.RESULT_END_OF_INPUT) break
            total += n
        }
        source.close()
        assertEquals(payload.size, total)
        assertArrayEquals(payload, read)
    }

    @Test
    fun deletingPortableDownloadRemovesSidecarsButNotOtherTitles() {
        assertTrue(PortableLibrary.finalizeDownload(context, item, volume))
        val key = PortableLibrary.pvKeyFile(pack)
        val meta = PortableLibrary.pvMetaFile(pack)
        assertTrue(key.exists() && meta.exists())

        OfflineRegistry.remove(item.id, context)
        assertFalse(pack.exists())
        assertFalse(key.exists())
        assertFalse(meta.exists())
        assertTrue(PortableLibrary.libraryFile(volume).exists())
    }
}
