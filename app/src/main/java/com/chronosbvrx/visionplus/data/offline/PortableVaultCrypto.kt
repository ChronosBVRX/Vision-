package com.chronosbvrx.visionplus.data.offline

import android.os.Build
import android.util.Base64
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Formato portátil de Vision+ (`VPPACK02`).
 *
 * Es un contenedor paralelo al `.vpack` de siempre. No lo reemplaza ni lo modifica:
 * las descargas viejas siguen usando [OfflineCryptoVault] y su llave del chip.
 *
 * Cada archivo portátil tiene su propia llave de contenido AES-256. Esa llave se guarda
 * dos veces:
 *  - envuelta con una llave GCM del dispositivo de origen, para que la TV que descargó
 *    pueda reproducir sin PIN;
 *  - envuelta con la llave de la biblioteca (`library.vkey`), para que otra TV pueda
 *    abrirla después de introducir el PIN de la USB.
 *
 * El video se cifra con AES-256-CTR para conservar el seek O(1) del reproductor.
 */
object PortableVaultCrypto {

    val MAGIC = "VPPACK02".toByteArray(Charsets.US_ASCII)
    const val VERSION: Byte = 2
    const val NONCE_LEN = 12
    const val GCM_TAG_BITS = 128
    const val CONTENT_KEY_LEN = 32
    const val LIBRARY_KEY_LEN = 32
    const val SALT_LEN = 16
    const val DEFAULT_PIN_ITERATIONS = 600_000

    /** Magic(8) + version(1) + baseIV(16) + plainLength(8) + keyBlobLen(2). */
    const val FIXED_HEADER_SIZE = 8 + 1 + 16 + 8 + 2

    private const val WRAP_KEY_ALIAS = "VisionPlusPortableWrapKey2"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val CIPHER_TRANSFORMATION = "AES/CTR/NoPadding"
    private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_BLOCK_SIZE = 16

    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedWrapKey: SecretKey? = null

    data class PortableHeader(
        val baseIv: ByteArray,
        val plainLength: Long,
        val payloadOffset: Long,
        val deviceWrappedKey: ByteArray,
    )

    /**
     * Llave GCM del dispositivo para envolver llaves de contenido. Es distinta de la
     * llave maestra CTR de [OfflineCryptoVault]: AndroidKeyStore no permite usar una
     * llave generada para CTR en GCM.
     */
    @Synchronized
    fun getOrCreateWrapKey(): SecretKey {
        cachedWrapKey?.let { return it }

        val created = runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(WRAP_KEY_ALIAS)) {
                val generator = KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE,
                )
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    WRAP_KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // Usamos nonce propio (12 bytes) y lo guardamos junto al blob.
                    .setRandomizedEncryptionRequired(false)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }
            keyStore.getKey(WRAP_KEY_ALIAS, null) as? SecretKey
        }.getOrNull()

        if (created != null) {
            cachedWrapKey = created
            return created
        }

        // Robolectric/JVM de pruebas no tiene AndroidKeyStore real. En un equipo de
        // producción NO se usa una llave determinista: si el Keystore falla, la función
        // portátil falla de forma honesta en vez de dejar el contenido abrible en otra TV.
        val testRuntime = Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
            Build.MODEL.contains("robolectric", ignoreCase = true)
        if (!testRuntime) {
            throw IllegalStateException("AndroidKeyStore no disponible para la llave portátil")
        }
        val seed = "VisionPlusPortableWrapKey2026TestFallback!".toByteArray(Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-256").digest(seed)
        val fallback = SecretKeySpec(sha, "AES")
        cachedWrapKey = fallback
        return fallback
    }

    fun randomKey(size: Int = CONTENT_KEY_LEN): ByteArray =
        ByteArray(size).also { secureRandom.nextBytes(it) }

    /** PIN de 4 dígitos, con ceros a la izquierda. */
    fun generatePin(random: SecureRandom = secureRandom): String =
        String.format(Locale.US, "%04d", random.nextInt(10_000))

    /**
     * Cifra un flujo al contenedor portátil. La llave de contenido se genera aquí y
     * se envuelve con la llave del dispositivo; el archivo queda listo para que
     * [PortableLibrary] escriba su `.pvkey` y sus metadatos.
     */
    fun encryptStreamToFile(
        input: InputStream,
        totalLength: Long,
        destinationFile: File,
        bufferSize: Int = 64 * 1024,
        onProgress: ((bytesWritten: Long, total: Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
    ): Long = encryptStreamToFileInternal(
        input = input,
        totalLength = totalLength,
        destinationFile = destinationFile,
        bufferSize = bufferSize,
        deviceWrapKeyOverride = null,
        onContentKey = null,
        onProgress = onProgress,
        isCancelled = isCancelled,
    )

    /**
     * Variante de pruebas: permite cifrar con una llave de dispositivo que NO es la
     * de este proceso (simula la TV de origen) y observar la llave de contenido para
     * construir el `.pvkey` de destino.
     */
    internal fun encryptStreamToFileForTest(
        input: InputStream,
        totalLength: Long,
        destinationFile: File,
        deviceWrapKey: SecretKey,
        onContentKey: (ByteArray) -> Unit,
    ): Long = encryptStreamToFileInternal(
        input = input,
        totalLength = totalLength,
        destinationFile = destinationFile,
        bufferSize = 64 * 1024,
        deviceWrapKeyOverride = deviceWrapKey,
        onContentKey = onContentKey,
        onProgress = null,
        isCancelled = null,
    )

    private fun encryptStreamToFileInternal(
        input: InputStream,
        totalLength: Long,
        destinationFile: File,
        bufferSize: Int,
        deviceWrapKeyOverride: SecretKey?,
        onContentKey: ((ByteArray) -> Unit)?,
        onProgress: ((bytesWritten: Long, total: Long) -> Unit)?,
        isCancelled: (() -> Boolean)?,
    ): Long {
        val contentKeyBytes = randomKey()
        val contentKey = SecretKeySpec(contentKeyBytes, "AES")
        val baseIv = ByteArray(16).also { secureRandom.nextBytes(it) }
        onContentKey?.invoke(contentKeyBytes.copyOf())
        val deviceWrapped = if (deviceWrapKeyOverride != null) {
            wrapAesGcm(deviceWrapKeyOverride, contentKeyBytes)
        } else {
            wrapWithDeviceKey(contentKeyBytes)
        }
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.downloading")
        tempFile.parentFile?.mkdirs()

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, contentKey, javax.crypto.spec.IvParameterSpec(baseIv))

        var bytesWritten = 0L
        tempFile.outputStream().buffered(bufferSize).use { outStream ->
            writeHeader(outStream, baseIv, totalLength, deviceWrapped)

            val inBuf = ByteArray(bufferSize)
            val outBuf = ByteArray(bufferSize + AES_BLOCK_SIZE)
            var lastProgressUpdate = 0L

            while (true) {
                if (isCancelled?.invoke() == true) {
                    tempFile.delete()
                    throw java.util.concurrent.CancellationException("Descarga cancelada por el usuario")
                }
                val read = input.read(inBuf)
                if (read == -1) break

                val encLen = cipher.update(inBuf, 0, read, outBuf, 0)
                if (encLen > 0) {
                    outStream.write(outBuf, 0, encLen)
                    bytesWritten += encLen
                }

                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate >= 300L) {
                    lastProgressUpdate = now
                    onProgress?.invoke(bytesWritten, totalLength)
                }
            }

            val finalLen = cipher.doFinal(outBuf, 0)
            if (finalLen > 0) {
                outStream.write(outBuf, 0, finalLen)
                bytesWritten += finalLen
            }
            outStream.flush()
        }

        if (totalLength <= 0L || bytesWritten != totalLength) {
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.seek(MAGIC.size + 1L + 16L)
                raf.writeLong(bytesWritten)
            }
        }

        if (destinationFile.exists()) destinationFile.delete()
        if (!tempFile.renameTo(destinationFile)) {
            tempFile.copyTo(destinationFile, overwrite = true)
            tempFile.delete()
        }

        onProgress?.invoke(bytesWritten, bytesWritten)
        return bytesWritten
    }

    /** Lee la cabecera si el archivo es `VPPACK02`; null si es un `.vpack` clásico o basura. */
    fun readHeader(input: RandomAccessFile): PortableHeader? {
        if (input.length() < FIXED_HEADER_SIZE) return null
        input.seek(0L)
        val magic = ByteArray(MAGIC.size)
        runCatching { input.readFully(magic) }.getOrElse { return null }
        if (!magic.contentEquals(MAGIC)) return null
        val version = runCatching { input.readByte() }.getOrElse { return null }
        if (version != VERSION) return null
        val baseIv = ByteArray(16)
        runCatching { input.readFully(baseIv) }.getOrElse { return null }
        val plainLength = runCatching { input.readLong() }.getOrElse { return null }
        val blobLen = runCatching {
            val b = ByteArray(2)
            input.readFully(b)
            ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        }.getOrElse { return null }
        if (blobLen <= 0 || blobLen > 4_096) return null
        val wrapped = ByteArray(blobLen)
        runCatching { input.readFully(wrapped) }.getOrElse { return null }
        return PortableHeader(
            baseIv = baseIv,
            plainLength = plainLength,
            payloadOffset = FIXED_HEADER_SIZE + blobLen.toLong(),
            deviceWrappedKey = wrapped,
        )
    }

    fun isPortable(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC.size) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(MAGIC.size)
                raf.readFully(magic)
                magic.contentEquals(MAGIC)
            }
        }.getOrDefault(false)
    }

    fun unwrapDeviceContentKey(header: PortableHeader): SecretKey? {
        val raw = unwrapWithDeviceKey(header.deviceWrappedKey) ?: return null
        if (raw.size != CONTENT_KEY_LEN) return null
        return SecretKeySpec(raw, "AES")
    }

    /** Devuelve un blob `nonce + ciphertext + tag` (Base64) envuelto con la llave del chip. */
    fun wrapWithDeviceKey(rawKey: ByteArray): ByteArray =
        wrapAesGcm(getOrCreateWrapKey(), rawKey)

    fun unwrapWithDeviceKey(blob: ByteArray): ByteArray? =
        unwrapAesGcm(getOrCreateWrapKey(), blob)

    /** Envuelve la llave de contenido con la llave de biblioteca. */
    fun wrapWithLibraryKey(rawKey: ByteArray, libraryKey: SecretKey): String =
        Base64.encodeToString(wrapAesGcm(libraryKey, rawKey), Base64.NO_WRAP)

    fun unwrapFromLibrary(wrappedBase64: String, libraryKey: SecretKey): SecretKey? {
        val blob = runCatching { Base64.decode(wrappedBase64, Base64.NO_WRAP) }.getOrNull() ?: return null
        val raw = unwrapAesGcm(libraryKey, blob) ?: return null
        if (raw.size != CONTENT_KEY_LEN) return null
        return SecretKeySpec(raw, "AES")
    }

    /**
     * Envuelve la llave de biblioteca con una llave derivada del PIN.
     * `iterations` es inyectable para pruebas; en producción se usa [DEFAULT_PIN_ITERATIONS].
     */
    fun wrapLibraryForPin(
        libraryKey: SecretKey,
        pin: String,
        iterations: Int = DEFAULT_PIN_ITERATIONS,
    ): JSONObject {
        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val pinKey = derivePinKey(pin, salt, iterations)
        val wrapped = wrapAesGcm(pinKey, libraryKey.encoded)
        return JSONObject()
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iterations", iterations)
            .put("wrapped", Base64.encodeToString(wrapped, Base64.NO_WRAP))
    }

    fun unwrapLibraryFromPin(envelope: JSONObject, pin: String): SecretKey? {
        val salt = runCatching {
            Base64.decode(envelope.optString("salt"), Base64.NO_WRAP)
        }.getOrNull() ?: return null
        val iterations = envelope.optInt("iterations", DEFAULT_PIN_ITERATIONS)
        if (iterations < 1_000 || iterations > 2_000_000) return null
        val wrapped = runCatching {
            Base64.decode(envelope.optString("wrapped"), Base64.NO_WRAP)
        }.getOrNull() ?: return null
        val pinKey = derivePinKey(pin, salt, iterations)
        val raw = unwrapAesGcm(pinKey, wrapped) ?: return null
        if (raw.size != LIBRARY_KEY_LEN) return null
        return SecretKeySpec(raw, "AES")
    }

    fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(text: String): ByteArray? =
        runCatching { Base64.decode(text, Base64.NO_WRAP) }.getOrNull()

    private fun derivePinKey(pin: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            // PBKDF2-HMAC-SHA1 está disponible desde API 24; SHA-256 exige API 26.
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun wrapAesGcm(wrappingKey: SecretKey, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    private fun unwrapAesGcm(wrappingKey: SecretKey, blob: ByteArray): ByteArray? {
        if (blob.size <= NONCE_LEN) return null
        val nonce = blob.copyOfRange(0, NONCE_LEN)
        val ciphertext = blob.copyOfRange(NONCE_LEN, blob.size)
        return runCatching {
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    private fun writeHeader(
        output: OutputStream,
        baseIv: ByteArray,
        plainLength: Long,
        deviceWrappedKey: ByteArray,
    ) {
        require(baseIv.size == 16) { "El BaseIV debe tener 16 bytes" }
        output.write(MAGIC)
        output.write(byteArrayOf(VERSION))
        output.write(baseIv)
        output.write(ByteBuffer.allocate(8).putLong(plainLength).array())
        val len = ByteBuffer.allocate(2).putShort(deviceWrappedKey.size.toShort()).array()
        output.write(len)
        output.write(deviceWrappedKey)
    }
}
