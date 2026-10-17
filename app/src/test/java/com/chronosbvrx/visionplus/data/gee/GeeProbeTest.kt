package com.chronosbvrx.visionplus.data.gee

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El vigilante comprueba de VERDAD: contra un servidor HTTP real en este equipo (sockets, no un
 * doble). El blindaje: un manifiesto que responde 2xx está vivo —y lleva las cabeceras del enlace—,
 * un error o un puerto muerto están caídos, la basura no truena y el lote informa cada señal.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GeeProbeTest {

    private lateinit var server: TinyHttp
    private val seen = ConcurrentHashMap<String, String>()

    @Before
    fun setUp() {
        seen.clear()
        server = TinyHttp(seen)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun aManifestThatAnswersIsAliveAndCarriesItsHeaders() {
        val link = "http://127.0.0.1:${server.port}/live.m3u8" +
            "|origin=https://cinecalidad.tv&referer=https://cinecalidad.tv/&user-agent=PruebaUA/1.0"
        assertTrue("un 2xx con cuerpo es señal viva", GeeProbe.pingOne(link))
        assertEquals("PruebaUA/1.0", seen["user-agent"])
        assertEquals("https://cinecalidad.tv/", seen["referer"])
        assertEquals("https://cinecalidad.tv", seen["origin"])
    }

    @Test
    fun anErrorAnswerIsDown() {
        assertFalse(GeeProbe.pingOne("http://127.0.0.1:${server.port}/caido.m3u8"))
    }

    @Test
    fun aDeadPortIsDown() {
        // Un puerto que se acaba de soltar: la conexión se rechaza al instante.
        val free = ServerSocket(0).use { it.localPort }
        assertFalse(GeeProbe.pingOne("http://127.0.0.1:$free/live.m3u8"))
    }

    @Test
    fun rubbishIsDownAndNeverThrows() {
        assertFalse(GeeProbe.pingOne(""))
        assertFalse(GeeProbe.pingOne("no-es-una-url"))
    }

    @Test
    fun theWholeBatchReportsEverySignal() = runBlocking {
        val vivo = "http://127.0.0.1:${server.port}/live.m3u8"
        val caido = "http://127.0.0.1:${server.port}/caido.m3u8"
        val out = GeeProbe.pingAll(listOf(vivo, caido, vivo))
        assertEquals("repetir no duplica", 2, out.size)
        assertEquals(true, out[vivo]?.alive)
        assertEquals(false, out[caido]?.alive)
        assertTrue("cada resultado viene con su hora", (out[vivo]?.atMs ?: 0L) > 0L)
    }

    /** Un servidor HTTP mínimo sobre sockets: `/live*` responde 200 con manifiesto, el resto 503. */
    private class TinyHttp(private val seen: MutableMap<String, String>) {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        @Volatile private var running = true
        val port: Int get() = server.localPort

        init {
            thread(name = "tiny-http", isDaemon = true) {
                while (running) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { handle(socket) }
                }
            }
        }

        private fun handle(socket: Socket) {
            runCatching {
                socket.use { s ->
                    val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                    val requestLine = input.readLine() ?: return@use
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        val idx = line.indexOf(':')
                        if (idx > 0) {
                            seen[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                        }
                    }
                    val path = requestLine.split(' ').getOrNull(1) ?: "/"
                    val ok = path.startsWith("/live")
                    val body = if (ok) "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n" else ""
                    val bytes = body.toByteArray()
                    val head = buildString {
                        append("HTTP/1.1 ").append(if (ok) "200 OK" else "503 Service Unavailable").append("\r\n")
                        append("Content-Type: application/vnd.apple.mpegurl\r\n")
                        append("Content-Length: ").append(bytes.size).append("\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    val out = s.getOutputStream()
                    out.write(head.toByteArray())
                    out.write(bytes)
                    out.flush()
                }
            }
        }

        fun stop() {
            running = false
            runCatching { server.close() }
        }
    }
}
