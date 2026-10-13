package com.chronosbvrx.visionplus.data.offline

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Fuente de datos segura para ExoPlayer (Media3):
 *
 * Lee directamente desde los archivos protegidos .vpack y descifra en tiempo real
 * hacia el búfer de memoria RAM de ExoPlayer.
 *
 * Características:
 * - Soporte de búsqueda instantánea O(1) (Random-Access / Seek).
 * - Cero archivos temporales descifrados en el almacenamiento del dispositivo.
 * - Desempeño a 60 FPS sin tirones ni sobrecarga.
 */
@OptIn(UnstableApi::class)
class EncryptedVaultDataSource(
    private val context: Context,
) : BaseDataSource(/* isNetwork = */ false) {

    private var currentUri: Uri? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var cipher: Cipher? = null
    private var bytesRemaining: Long = 0L
    private var isOpen = false

    private val masterKey: SecretKey by lazy { OfflineCryptoVault.getOrCreateMasterKey() }

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)

        val targetFile = resolveTargetFile(dataSpec.uri)
            ?: throw FileNotFoundException("No se encontró el contenedor seguro para la URI: ${dataSpec.uri}")

        if (!targetFile.exists() || !targetFile.canRead()) {
            throw FileNotFoundException("El archivo contenedor no existe o no tiene permisos de lectura: ${targetFile.absolutePath}")
        }

        val raf = RandomAccessFile(targetFile, "r")
        randomAccessFile = raf

        // 1. Leer y validar cabecera de 33 bytes
        val (baseIv, plainLength) = OfflineCryptoVault.readHeader(raf)

        // 2. Posicionar en el offset solicitado por ExoPlayer
        val requestedPosition = dataSpec.position
        if (requestedPosition > plainLength) {
            throw java.io.EOFException("Posición solicitada ($requestedPosition) excede el tamaño del video ($plainLength)")
        }

        val fileOffsetToSeek = OfflineCryptoVault.HEADER_SIZE + requestedPosition
        raf.seek(fileOffsetToSeek)

        // 3. Inicializar el descifrador en el punto exacto de la reproducción
        val (seekCipher, _) = OfflineCryptoVault.createSeekableDecryptCipher(masterKey, baseIv, requestedPosition)
        cipher = seekCipher

        // 4. Determinar cuántos bytes deben leerse en esta solicitud
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            plainLength - requestedPosition
        }

        isOpen = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val activeCipher = cipher ?: return C.RESULT_END_OF_INPUT

        // Leer del archivo cifrado
        val tempEncrypted = ByteArray(bytesToRead)
        val readCount = raf.read(tempEncrypted, 0, bytesToRead)
        if (readCount <= 0) return C.RESULT_END_OF_INPUT

        // Descifrar al vuelo directamente en el búfer de destino de ExoPlayer
        activeCipher.update(tempEncrypted, 0, readCount, buffer, offset)

        bytesRemaining -= readCount
        bytesTransferred(readCount)
        return readCount
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        cipher = null
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {
        } finally {
            randomAccessFile = null
            if (isOpen) {
                isOpen = false
                transferEnded()
            }
        }
    }

    /**
     * Resuelve la URI a un archivo físico .vpack:
     * - "vault:film_id" -> Busca en el registro offline o en el vault activo
     * - "vault:///path/to/file.vpack" -> Archivo directo
     * - "file:///path/to/file.vpack" -> Archivo directo
     */
    private fun resolveTargetFile(uri: Uri): File? {
        val scheme = uri.scheme
        val uriStr = uri.toString()

        // 1. Archivo con ruta directa
        if (scheme == "file") {
            return uri.path?.let { File(it) }
        }

        val cleanId = uriStr.removePrefix("vault://").removePrefix("vault:").substringBefore('?')

        // 2. Si es una ruta absoluta directa
        if (cleanId.startsWith("/") || cleanId.contains(File.separator)) {
            val directFile = File(cleanId)
            if (directFile.exists()) return directFile
        }

        // 3. Buscar en el registro offline por ID
        val item = OfflineRegistry.get(cleanId, context)
        if (item != null && item.vaultFilePath.isNotBlank()) {
            val f = File(item.vaultFilePath)
            if (f.exists()) return f
        }

        // 4. Buscar en el vault directory por nombre generado
        val vaultDir = OfflineStoragePolicy.getVaultDirectory(context)
        val safeFileName = cleanId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val candidate = File(vaultDir, "$safeFileName.vpack")
        if (candidate.exists()) return candidate

        return null
    }

    /**
     * Fábrica para inyectar en ExoPlayer (Media3).
     */
    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedVaultDataSource(context)
    }
}
