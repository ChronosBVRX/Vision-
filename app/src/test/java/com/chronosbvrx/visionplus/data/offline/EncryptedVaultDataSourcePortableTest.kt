package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedVaultDataSourcePortableTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OfflineRegistry.resetForTesting(context)
    }

    @Test
    fun streamsAndSeeksPortableContainer() {
        val original = ByteArray(96 * 1024).also { Random(31).nextBytes(it) }
        val vault = tempFolder.newFolder("vision_vault")
        val pack = File(vault, "movie.pvpack")
        PortableVaultCrypto.encryptStreamToFile(
            input = ByteArrayInputStream(original),
            totalLength = original.size.toLong(),
            destinationFile = pack,
        )
        val item = OfflineDownload(
            id = "xtv:portable-source",
            contentId = "xtv:portable-source",
            title = "Portable",
            state = DownloadState.COMPLETED,
            bytesDownloaded = pack.length(),
            totalBytes = pack.length(),
            vaultFilePath = pack.absolutePath,
            portable = true,
        )
        OfflineRegistry.put(item, context)

        val source = EncryptedVaultDataSource(context)
        source.open(
            DataSpec.Builder()
                .setUri(Uri.parse("vault:${item.id}"))
                .build(),
        )
        val all = ByteArray(original.size)
        var read = 0
        while (read < all.size) {
            val n = source.read(all, read, all.size - read)
            if (n == C.RESULT_END_OF_INPUT) break
            read += n
        }
        source.close()
        assertEquals(original.size, read)
        assertArrayEquals(original, all)

        // Seek dentro del contenedor portátil (misma ruta de descifrado O(1)).
        val seekSource = EncryptedVaultDataSource(context)
        val offset = 4096
        val length = 1024
        seekSource.open(
            DataSpec.Builder()
                .setUri(Uri.parse("vault:${item.id}"))
                .setPosition(offset.toLong())
                .setLength(length.toLong())
                .build(),
        )
        val slice = ByteArray(length)
        var sliceRead = 0
        while (sliceRead < length) {
            val n = seekSource.read(slice, sliceRead, length - sliceRead)
            if (n == C.RESULT_END_OF_INPUT) break
            sliceRead += n
        }
        seekSource.close()
        assertTrue(sliceRead > 0)
        assertArrayEquals(original.copyOfRange(offset, offset + sliceRead), slice.copyOfRange(0, sliceRead))
    }
}
