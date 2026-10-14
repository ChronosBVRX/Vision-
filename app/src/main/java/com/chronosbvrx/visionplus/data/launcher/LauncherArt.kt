package com.chronosbvrx.visionplus.data.launcher

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * El póster de cada tarjeta del launcher, guardado en el sandbox de la app y servido por
 * [LauncherArtProvider] como `content://…launcherart/<archivo>`.
 *
 * El launcher pide las imágenes por URI y las cachea; un archivo local garantiza que la
 * tarjeta se vea completa a la primera (y sin red). Si la descarga falla, el publicador cae
 * a la URL remota del póster.
 */
internal object LauncherArt {
    private const val TAG = "LauncherArt"
    private const val DIR = "launcher_art"
    private const val MAX_BYTES = 4L * 1024 * 1024

    /** Archivos que se conservan (los más viejos se podan). */
    private const val KEEP_FILES = 24

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** La autoridad del provider que sirve estos archivos (declarada en el manifest). */
    fun authority(context: Context): String = "${context.packageName}.launcherart"

    fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /**
     * El `content://` del póster de [url], descargándolo si aún no está. Null cuando no hay
     * póster o no se pudo bajar (el publicador decide el respaldo).
     */
    suspend fun ensure(context: Context, url: String): Uri? = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (clean.isEmpty()) return@withContext null
        runCatching {
            val hash = sha1(clean)
            existingFile(context, hash)?.let { return@withContext uriFor(context, it) }
            val bytes = download(clean) ?: return@withContext null
            val file = File(dir(context), "$hash.${extensionFor(bytes)}")
            file.writeBytes(bytes)
            prune(context)
            uriFor(context, file)
        }.onFailure { Log.w(TAG, "póster no cacheado: ${it.message}") }.getOrNull()
    }

    private fun existingFile(context: Context, hash: String): File? =
        listOf("jpg", "png", "webp")
            .map { File(dir(context), "$hash.$it") }
            .firstOrNull { it.exists() && it.length() > 0 }

    private fun uriFor(context: Context, file: File): Uri = Uri.Builder()
        .scheme("content")
        .authority(authority(context))
        .appendPath(file.name)
        .build()

    private fun download(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "image/*")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            // Lectura acotada: un servidor no puede hacer que nos traguemos GBs de "póster".
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            body.byteStream().use { stream ->
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    out.write(chunk, 0, read)
                    if (out.size() > MAX_BYTES) return null
                }
            }
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) null else bytes
        }
    }

    /** JPEG/PNG/WEBP por cabecera; jpg como último recurso. */
    private fun extensionFor(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> "jpg"
    }

    private fun prune(context: Context) {
        val files = dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP_FILES).forEach { runCatching { it.delete() } }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
