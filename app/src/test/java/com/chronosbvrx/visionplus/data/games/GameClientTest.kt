package com.chronosbvrx.visionplus.data.games

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GameClientTest {

    @Before
    fun setUp() {
        GameClient.resetForTests()
        GameClient.connectTimeoutMs = 500
        GameClient.readTimeoutMs = 800
    }

    @After
    fun tearDown() {
        GameClient.resetForTests()
    }

    @Test
    fun createParsesTheRoomAndTheTwoTokens() {
        OneShotServer(
            200,
            """{"code":"ABC234","displayCode":"ABC-234","hostToken":"host","playerToken":"player","playerId":"p1","hostId":"p1","expiresAt":99,"snapshot":null}""",
        ).use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.create("Ana")) {
                is GameClient.Result.Ok -> {
                    assertEquals("ABC234", result.value.code)
                    assertEquals("ABC-234", result.value.displayCode)
                    assertEquals("host", result.value.hostToken)
                    assertEquals("player", result.value.playerToken)
                }
                is GameClient.Result.Error -> fail("error ${result.code}: ${result.message}")
            }
        }
    }

    @Test
    fun stateParsesAQuestionWithoutTheAnswer() {
        val body = """
            {"code":"ABC234","displayCode":"ABC-234","mode":"remote","pack":"general",
             "phase":"question","rev":5,"serverNow":1000,"phaseEndsAt":21000,
             "round":2,"rounds":8,"hostId":"p1",
             "players":[{"id":"p1","name":"Ana","color":0,"score":10,"connected":true,"isHost":true}],
             "question":{"id":"q1","kind":"year","prompt":"¿En qué año?","options":["A","B","C","D"],"art":"","difficulty":2},
             "answer":null,
             "you":{"id":"p1","name":"Ana","score":10,"streak":1,"color":0,"answered":false,"selected":-1,"points":0,"correct":false},
             "reactions":[]}
        """.trimIndent()
        OneShotServer(200, body).use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.state("ABC234", 0, "player")) {
                is GameClient.StateResult.Snapshot -> {
                    assertEquals("question", result.value.phase)
                    assertEquals(2, result.value.round)
                    assertEquals(4, result.value.question?.options?.size)
                    assertEquals(10, result.value.you?.score)
                }
                else -> fail("no era un snapshot: $result")
            }
        }
    }

    @Test
    fun stateParsesThePlayersPaceAndReadiness() {
        val body = """
            {"code":"ABC234","displayCode":"ABC-234","mode":"display","pack":"general",
             "phase":"reveal","rev":8,"serverNow":1000,"phaseEndsAt":0,
             "round":2,"rounds":8,"hostId":"p1","playersPace":true,
             "answeredCount":2,"readyCount":1,"allReady":false,
             "players":[{"id":"p1","name":"Ana","color":0,"score":10,"connected":true,"isHost":true,"ready":true}],
             "question":{"id":"q1","kind":"year","prompt":"¿En qué año?","options":["A","B"],"art":"","difficulty":2},
             "answer":{"option":0,"text":"A"},
             "you":{"id":"p1","name":"Ana","score":10,"streak":1,"color":0,"answered":true,"selected":0,"points":10,"correct":true,"ready":true},
             "reactions":[]}
        """.trimIndent()
        OneShotServer(200, body).use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.state("ABC234", 0, "player")) {
                is GameClient.StateResult.Snapshot -> {
                    assertTrue(result.value.playersPace)
                    assertEquals(2, result.value.answeredCount)
                    assertEquals(1, result.value.readyCount)
                    assertTrue(!result.value.allReady)
                    assertTrue(result.value.players.single().ready)
                    assertTrue(result.value.you?.ready == true)
                }
                else -> fail("no era un snapshot: $result")
            }
        }
    }

    @Test
    fun readyParsesTheAck() {
        OneShotServer(200, """{"ok":true,"ready":true}""").use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.ready("ABC234", "player", 2)) {
                is GameClient.Result.Ok -> assertTrue(result.value)
                is GameClient.Result.Error -> fail("error ${result.code}: ${result.message}")
            }
        }
    }

    @Test
    fun wagerAndCharadaParseTheirAcks() {
        OneShotServer(200, """{"accepted":true,"wager":500}""").use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.wager("ABC234", "player", 2, 500)) {
                is GameClient.Result.Ok -> assertTrue(result.value)
                is GameClient.Result.Error -> fail("error ${result.code}: ${result.message}")
            }
        }
        OneShotServer(200, """{"accepted":true}""").use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.charada("ABC234", "player", "correct")) {
                is GameClient.Result.Ok -> assertTrue(result.value)
                is GameClient.Result.Error -> fail("error ${result.code}: ${result.message}")
            }
        }
    }

    @Test
    fun packsParseTheSectionsAndCounts() {
        val body = """{"packs":[{"id":"cine","title":"Cine","active":true,"questions":12},{"id":"anime","title":"Anime","active":false,"questions":0}]}"""
        OneShotServer(200, body).use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            when (val result = GameClient.packs()) {
                is GameClient.Result.Ok -> {
                    assertEquals(2, result.value.size)
                    assertEquals("cine", result.value.first().id)
                    assertTrue(result.value.first().active)
                    assertEquals(12, result.value.first().questions)
                    assertTrue(!result.value.last().active)
                }
                is GameClient.Result.Error -> fail("error ${result.code}: ${result.message}")
            }
        }
    }

    @Test
    fun stateReturnsUnchangedOn204() {
        OneShotServer(204, "").use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            assertEquals(GameClient.StateResult.Unchanged, GameClient.state("ABC234", 5, "player"))
        }
    }

    @Test
    fun stateMapsTheServersError() {
        OneShotServer(404, """{"detail":"la sala se cerró"}""").use { server ->
            GameClient.baseUrl = "http://127.0.0.1:${server.port}"
            val result = GameClient.state("ABC234", 0, "player")
            assertTrue(result is GameClient.StateResult.Error)
            val error = result as GameClient.StateResult.Error
            assertEquals(404, error.code)
            assertEquals("la sala se cerró", error.message)
        }
    }

    @Test
    fun aClosedPortNeverThrows() {
        val port = ServerSocket(0).use { it.localPort }
        GameClient.baseUrl = "http://127.0.0.1:$port"
        val result = GameClient.state("ABC234", 0, "player")
        assertTrue(result is GameClient.StateResult.Error)
        val error = result as GameClient.StateResult.Error
        assertEquals(0, error.code)
    }

    private class OneShotServer(
        private val status: Int,
        private val response: String,
    ) : Closeable {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort
        private val thread = Thread {
            try {
                server.accept().use { socket -> serve(socket, status, response) }
            } catch (_: Exception) {
                // El test cierra el socket al terminar; no es un fallo.
            } finally {
                runCatching { server.close() }
            }
        }.apply {
            isDaemon = true
            start()
        }

        private fun serve(socket: Socket, status: Int, response: String) {
            socket.soTimeout = 2_000
            val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            var length = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            repeat(length) { input.read() }
            val body = response.toByteArray(Charsets.UTF_8)
            val reason = if (status == 204) "No Content" else "OK"
            val header = "HTTP/1.1 $status $reason\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(header.toByteArray(Charsets.UTF_8))
                if (body.isNotEmpty()) write(body)
                flush()
            }
        }

        override fun close() {
            runCatching { server.close() }
            thread.join(1_500)
        }
    }
}
