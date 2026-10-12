package com.chronosbvrx.visionplus.data.offline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Random

class OfflineCryptoVaultTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMasterKeyGeneration() {
        val key = OfflineCryptoVault.getOrCreateMasterKey()
        assertNotNull(key)
        assertEquals("AES", key.algorithm)
    }

    @Test
    fun testEncryptionAndFullDecryption() {
        val destFile = tempFolder.newFile("test_video.vpack")
        val random = Random(42)
        val originalData = ByteArray(256 * 1024) // 256 KB
        random.nextBytes(originalData)

        var reportedTotal = 0L
        val written = OfflineCryptoVault.encryptStreamToFile(
            input = ByteArrayInputStream(originalData),
            totalLength = originalData.size.toLong(),
            destinationFile = destFile,
            onProgress = { w, t -> reportedTotal = t },
        )

        assertEquals(originalData.size.toLong(), written)
        assertEquals(originalData.size.toLong(), reportedTotal)
        assertTrue(destFile.exists())
        assertEquals(OfflineCryptoVault.HEADER_SIZE + originalData.size.toLong(), destFile.length())

        // Validar lectura completa usando RandomAccessFile y el Cipher de descifrado
        RandomAccessFile(destFile, "r").use { raf ->
            val (baseIv, plainLength) = OfflineCryptoVault.readHeader(raf)
            assertEquals(originalData.size.toLong(), plainLength)

            val (cipher, _) = OfflineCryptoVault.createSeekableDecryptCipher(
                key = OfflineCryptoVault.getOrCreateMasterKey(),
                baseIv = baseIv,
                byteOffset = 0L,
            )

            val encryptedBytes = ByteArray(originalData.size)
            raf.seek(OfflineCryptoVault.HEADER_SIZE.toLong())
            raf.readFully(encryptedBytes)

            val decryptedBytes = ByteArray(originalData.size)
            cipher.update(encryptedBytes, 0, encryptedBytes.size, decryptedBytes, 0)

            assertArrayEquals(originalData, decryptedBytes)
        }
    }

    @Test
    fun testSeekableDecryptionAtArbitraryOffsets() {
        val destFile = tempFolder.newFile("test_seek.vpack")
        val random = Random(12345)
        val originalData = ByteArray(128 * 1024) // 128 KB
        random.nextBytes(originalData)

        OfflineCryptoVault.encryptStreamToFile(
            input = ByteArrayInputStream(originalData),
            totalLength = originalData.size.toLong(),
            destinationFile = destFile,
        )

        RandomAccessFile(destFile, "r").use { raf ->
            val (baseIv, _) = OfflineCryptoVault.readHeader(raf)
            val masterKey = OfflineCryptoVault.getOrCreateMasterKey()

            // Probar saltos (seek) a diferentes offsets dentro del archivo:
            val testOffsets = listOf(
                0L,
                7L, // Offset desalineado dentro del primer bloque AES
                16L, // Exactamente en límite de bloque
                33L,
                1024L,
                4096L + 13L,
                65536L,
                originalData.size - 100L,
            )

            for (offset in testOffsets) {
                val lengthToRead = 64
                val (cipher, _) = OfflineCryptoVault.createSeekableDecryptCipher(masterKey, baseIv, offset)

                raf.seek(OfflineCryptoVault.HEADER_SIZE + offset)
                val encBuf = ByteArray(lengthToRead)
                val readCount = raf.read(encBuf)
                assertTrue(readCount > 0)

                val decBuf = ByteArray(readCount)
                cipher.update(encBuf, 0, readCount, decBuf, 0)

                val expectedSlice = originalData.copyOfRange(offset.toInt(), offset.toInt() + readCount)
                assertArrayEquals("Fallo en offset $offset", expectedSlice, decBuf)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidHeaderThrows() {
        val corruptedFile = tempFolder.newFile("corrupted.vpack")
        corruptedFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

        RandomAccessFile(corruptedFile, "r").use { raf ->
            OfflineCryptoVault.readHeader(raf)
        }
    }
}
