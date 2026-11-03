package com.chronosbvrx.visionplus.data.netshare

import com.chronosbvrx.visionplus.data.remote.NowPlaying
import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor HTTP mínimo (sin dependencias) para el estado de reproducción.
 *
 * - Solo responde `GET /now-playing` (y `HEAD`); todo lo demás es 404.
 * - Es de SOLO LECTURA: no existe ninguna ruta de control.
 * - Acepta únicamente clientes de la LAN (privada, loopback o link-local); una IP pública
 *   recibe 403.
 * - Si se configuró un token ([NowPlayingShare.setToken]), exige `?token=...` o la cabecera
 *   `X-Vision-Token`. Sin token configurado (default) responde a cualquiera de la LAN.
 * - Corre en su propio hilo y un pool pequeño: jamás bloquea al reproductor ni al hilo de UI.
 */
internal class NowPlayingHttpServer(
    private val stateProvider: () -> NowPlaying,
    private val device: String,
    private val token: String = "",
    preferredPort: Int = NowPlayingShare.DEFAULT_PORT,
    private val context: Context? = null,
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "vision-now-playing-http").apply { isDaemon = true }
    }
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val preferred = preferredPort

    /** Puerto real (8765 si estaba libre; uno efímero si no). 0 si no arrancó. */
    val boundPort: Int get() = server?.localPort ?: 0

    fun start(): Int {
        if (running.get()) return boundPort
        val socket = runCatching { ServerSocket(preferred, 50) }
            .recoverCatching { ServerSocket(0, 50) }
            .getOrElse { return 0 }
        server = socket
        running.set(true)
        acceptThread = Thread({ acceptLoop(socket) }, "vision-now-playing-accept").apply {
            isDaemon = true
            start()
        }
        return socket.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        runCatching { acceptThread?.interrupt() }
        acceptThread = null
        runCatching { pool.shutdownNow() }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get() && !socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            runCatching { pool.execute { serve(client) } }
                .onFailure { runCatching { client.close() } }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 4_000
            val remote = client.inetAddress
            if (!isLanAddress(remote)) {
                respond(client, 403, """{"error":"solo LAN"}""")
                return
            }
            val input = BufferedInputStream(client.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty().uppercase()
            val target = parts.getOrNull(1).orEmpty()
            val headers = readHeaders(input)
            if (token.isNotBlank()) {
                val provided = headers["x-vision-token"] ?: queryParam(target, "token")
                if (provided != token) {
                    respond(client, 401, """{"error":"token requerido"}""")
                    return
                }
            }
            val path = target.substringBefore('?').trimEnd('/').ifBlank { "/" }
            val hostHeader = headers["host"]?.trim()
                ?.takeIf { it.matches(Regex("[A-Za-z0-9.:\\-\\[\\]]+")) }
            val localHost = client.localAddress?.hostAddress.orEmpty()
            val fallbackHost = if (localHost.contains(':')) {
                "[$localHost]:${client.localPort}"
            } else {
                "$localHost:${client.localPort}"
            }
            val baseUrl = "http://${hostHeader ?: fallbackHost}"
            when {
                (method == "GET" || method == "HEAD") && path == NowPlayingShare.PATH -> {
                    val body = NowPlayingJson.build(stateProvider(), device, baseUrl)
                    respond(client, 200, body, headOnly = method == "HEAD")
                }
                (method == "GET" || method == "HEAD") && path.startsWith("/artwork/") -> {
                    serveArtwork(client, path.removePrefix("/artwork/"), method == "HEAD")
                }
                else -> respond(client, 404, """{"error":"no encontrado"}""")
            }
        } catch (_: Throwable) {
            // Un cliente que se va o manda basura no puede tumbar el anuncio.
        } finally {
            runCatching { client.close() }
        }
    }

    private fun serveArtwork(client: Socket, key: String, headOnly: Boolean) {
        val raw = ArtworkStore.get(key)
        if (raw.isNullOrBlank()) {
            respond(client, 404, """{"error":"arte no encontrado"}""")
            return
        }
        val bytes = openArtwork(raw)?.use { stream ->
            runCatching { stream.readBytes() }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty() || bytes.size > 8_000_000) {
            respond(client, 404, """{"error":"arte no disponible"}""")
            return
        }
        respondBytes(client, 200, bytes, contentTypeFor(raw), headOnly, "public, max-age=3600")
    }

    private fun openArtwork(raw: String): InputStream? = runCatching {
        when {
            raw.startsWith("content://", ignoreCase = true) ->
                context?.contentResolver?.openInputStream(Uri.parse(raw))
            raw.startsWith("file://", ignoreCase = true) ->
                Uri.parse(raw).path?.let { FileInputStream(File(it)) }
            else -> FileInputStream(File(raw))
        }
    }.getOrNull()

    private fun contentTypeFor(raw: String): String {
        val clean = raw.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".webp") -> "image/webp"
            clean.endsWith(".gif") -> "image/gif"
            clean.endsWith(".svg") -> "image/svg+xml"
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun respond(client: Socket, code: Int, body: String, headOnly: Boolean = false) =
        respondBytes(client, code, body.toByteArray(Charsets.UTF_8), "application/json; charset=utf-8", headOnly, "no-store")

    private fun respondBytes(
        client: Socket,
        code: Int,
        bytes: ByteArray,
        contentType: String,
        headOnly: Boolean = false,
        cacheControl: String = "no-store",
    ) {
        val reason = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val cache = if (code == 200) cacheControl else "no-store"
        val header = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Cache-Control: ").append(cache).append("\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)
        val out: OutputStream = client.getOutputStream()
        out.write(header)
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String> {
        val headers = HashMap<String, String>()
        var line = readLine(input) ?: return headers
        var guard = 0
        while (line.isNotEmpty() && guard++ < 64) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
            line = readLine(input) ?: break
        }
        return headers
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = StringBuilder(96)
        var byte = input.read()
        while (byte >= 0) {
            if (byte == '\n'.code) return buffer.toString().trimEnd('\r')
            if (buffer.length >= 2_048) return null
            buffer.append(byte.toChar())
            byte = input.read()
        }
        return if (buffer.isEmpty()) null else buffer.toString()
    }

    private fun queryParam(target: String, key: String): String? {
        val query = target.substringAfter('?', "").takeIf { it.isNotBlank() } ?: return null
        return query.split('&').firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
    }

    /** LAN privada, loopback, link-local o ULA IPv6 (fc00::/7). Una IP pública no ve nada. */
    internal fun isLanAddress(address: InetAddress?): Boolean {
        if (address == null) return false
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) {
            return true
        }
        // IPv6 ULA (fc00::/7): la LAN de casa también puede usar direcciones únicas locales.
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }
}
