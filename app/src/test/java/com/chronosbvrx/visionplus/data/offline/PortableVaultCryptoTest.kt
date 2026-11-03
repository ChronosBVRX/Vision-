package com.chronosbvrx.visionplus.data.offline

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Random
import org.junit.After
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
class PortableVaultCryptoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun pinIsAlwaysFourDigits() {
        repeat(200) {
            val pin = PortableVaultCrypto.generatePin()
            assertEquals(4, pin.length)
            assertTrue(pin.all { it.isDigit() })
        }
    }

    @Test
    fun deviceWrapRoundTrip() {
        val raw = PortableVaultCrypto.randomKey()
        val blob = PortableVaultCrypto.wrapWithDeviceKey(raw)
        assertArrayEquals(raw, PortableVaultCrypto.unwrapWithDeviceKey(blob))
    }

    @Test
    fun portableFileWritesHeaderAndDecryptsAtOffsets() {
        val original = ByteArray(128 * 1024).also { Random(7).nextBytes(it) }
        val dest = tempFolder.newFile("movie.pvpack")

        val written = PortableVaultCrypto.encryptStreamToFile(
            input = ByteArrayInputStream(original),
            totalLength = original.size.toLong(),
            destinationFile = dest,
        )
        assertEquals(original.size.toLong(), written)
        assertTrue(PortableVaultCrypto.isPortable(dest))

        RandomAccessFile(dest, "r").use { raf ->
            val header = PortableVaultCrypto.readHeader(raf)
            assertNotNull(header)
            header!!
            assertEquals(original.size.toLong(), header.plainLength)
            assertTrue(header.payloadOffset > PortableVaultCrypto.FIXED_HEADER_SIZE)

            val contentKey = PortableVaultCrypto.unwrapDeviceContentKey(header)
            assertNotNull(contentKey)

            val offsets = listOf(0L, 7L, 16L, 1024L, 4096L + 13L, original.size - 100L)
            for (offset in offsets) {
                val length = 64
                val (cipher, _) = OfflineCryptoVault.createSeekableDecryptCipher(
                    key = contentKey!!,
                    baseIv = header.baseIv,
                    byteOffset = offset,
                )
                raf.seek(header.payloadOffset + offset)
                val encrypted = ByteArray(length)
                val read = raf.read(encrypted)
                assertTrue(read > 0)
                val decrypted = ByteArray(read)
                cipher.update(encrypted, 0, read, decrypted, 0)
                val expected = original.copyOfRange(offset.toInt(), offset.toInt() + read)
                assertArrayEquals("offset $offset", expected, decrypted)
            }
        }
    }

    @Test
    fun libraryKeyEnvelopeUsesPinAndRejectsWrongPin() {
        val libraryKey = javax.crypto.spec.SecretKeySpec(
            PortableVaultCrypto.randomKey(PortableVaultCrypto.LIBRARY_KEY_LEN),
            "AES",
        )
        val pin = "0427"
        val envelope = PortableVaultCrypto.wrapLibraryForPin(libraryKey, pin, iterations = 1_000)

        val opened = PortableVaultCrypto.unwrapLibraryFromPin(envelope, pin)
        assertNotNull(opened)
        assertArrayEquals(libraryKey.encoded, opened!!.encoded)
        assertNull(PortableVaultCrypto.unwrapLibraryFromPin(envelope, "9999"))
    }

    @Test
    fun contentKeyCanBeRewrappedForLibrary() {
        val contentKey = PortableVaultCrypto.randomKey()
        val libraryKey = javax.crypto.spec.SecretKeySpec(
            PortableVaultCrypto.randomKey(PortableVaultCrypto.LIBRARY_KEY_LEN),
            "AES",
        )
        val wrapped = PortableVaultCrypto.wrapWithLibraryKey(contentKey, libraryKey)
        val opened = PortableVaultCrypto.unwrapFromLibrary(wrapped, libraryKey)
        assertNotNull(opened)
        assertArrayEquals(contentKey, opened!!.encoded)

        val wrong = javax.crypto.spec.SecretKeySpec(PortableVaultCrypto.randomKey(), "AES")
        assertNull(PortableVaultCrypto.unwrapFromLibrary(wrapped, wrong))
        assertFalse(PortableVaultCrypto.isPortable(tempFolder.newFile("empty.bin")))
    }

    @Test
    fun legacyVpackIsNotMistakenForPortable() {
        val dest = tempFolder.newFile("legacy.vpack")
        OfflineCryptoVault.encryptStreamToFile(
            input = ByteArrayInputStream(ByteArray(64 * 1024)),
            totalLength = 64L * 1024L,
            destinationFile = dest,
        )
        assertFalse(PortableVaultCrypto.isPortable(dest))
        RandomAccessFile(dest, "r").use { raf ->
            assertNull(PortableVaultCrypto.readHeader(raf))
        }
    }
}
