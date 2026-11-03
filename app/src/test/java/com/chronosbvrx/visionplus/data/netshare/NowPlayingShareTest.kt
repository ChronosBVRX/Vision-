package com.chronosbvrx.visionplus.data.netshare

import com.chronosbvrx.visionplus.data.remote.NowPlaying
import com.chronosbvrx.visionplus.data.remote.RemoteProto
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El anuncio de «qué está reproduciendo»: mDNS `_visionplus._tcp` + HTTP de solo lectura.
 * El JSON sale DIRECTAMENTE del estado que ya publica el reproductor (canal, programa de la
 * EPG, minuto, duración): no se inventan datos.
 */
class NowPlayingShareTest {

    @Test
    fun idleSaysIdleAndDoesNotInventChannelOrProgram() {
        val json = JSONObject(NowPlayingJson.build(NowPlaying(), "Chromecast HD", nowMs = 1_000L))
        assertEquals("Vision+", json.getString("app"))
        assertEquals("Chromecast HD", json.getString("device"))
        assertEquals("idle", json.getString("state"))
        assertEquals("", json.getString("channel"))
        assertEquals("", json.getString("program"))
        assertEquals("", json.getString("description"))
        assertEquals(0L, json.getLong("position"))
        assertEquals(0L, json.getLong("duration"))
        assertEquals(1_000L, json.getLong("updated"))
        assertEquals("", json.getString("artwork"))
        assertEquals("", json.getString("artwork_type"))
    }

    @Test
    fun aLiveChannelCarriesItsEpgProgramAndState() {
        val now = NowPlaying(
            kind = RemoteProto.KIND_LIVE,
            screen = "ECDF · En vivo · Fútbol",
            title = "ECDF",
            subtitle = "En vivo · Fútbol · Deportes",
            channel = "ECDF",
            channelLogo = "https://cdn.example/ecdf.png",
            program = "Fútbol en vivo",
            description = "Partido de la jornada",
            playing = true,
            updatedAt = 2_000L,
        )
        val json = JSONObject(NowPlayingJson.build(now, "Sala", nowMs = 3_000L))
        assertEquals("playing", json.getString("state"))
        assertEquals("ECDF", json.getString("channel"))
        assertEquals("https://cdn.example/ecdf.png", json.getString("artwork"))
        assertEquals("logo", json.getString("artwork_type"))
        assertEquals("Fútbol en vivo", json.getString("program"))
        assertEquals("Partido de la jornada", json.getString("description"))
        assertEquals(0L, json.getLong("position"))
        assertEquals(0L, json.getLong("duration"))
        assertEquals(2_000L, json.getLong("updated"))
        assertTrue(json.getBoolean("live"))
    }

    @Test
    fun aLiveChannelWithoutEpgDoesNotInventAProgram() {
        val now = NowPlaying(
            kind = RemoteProto.KIND_LIVE,
            title = "La Granja VIP",
            subtitle = "En vivo",
            playing = true,
        )
        val json = JSONObject(NowPlayingJson.build(now, "Sala", nowMs = 6_000L))
        assertEquals("La Granja VIP", json.getString("channel"))
        assertEquals("", json.getString("program"))
        assertEquals("", json.getString("description"))
    }

    @Test
    fun aPausedMovieReportsSecondsAndItsTitle() {
        val now = NowPlaying(
            kind = RemoteProto.KIND_VOD,
            title = "Spider-Man",
            subtitle = "2026 · 2 h 25 min",
            program = "Spider-Man",
            description = "2026 · 2 h 25 min",
            poster = "https://cdn.example/spiderman.jpg",
            playing = false,
            positionMs = 125_000L,
            durationMs = 8_700_000L,
            updatedAt = 4_000L,
        )
        val json = JSONObject(NowPlayingJson.build(now, "Sala", nowMs = 5_000L))
        assertEquals("paused", json.getString("state"))
        assertEquals("", json.getString("channel"))
        assertEquals("Spider-Man", json.getString("program"))
        assertEquals("https://cdn.example/spiderman.jpg", json.getString("artwork"))
        assertEquals("poster", json.getString("artwork_type"))
        assertEquals(125L, json.getLong("position"))
        assertEquals(8_700L, json.getLong("duration"))
        assertFalse(json.getBoolean("live"))
    }

    @Test
    fun aLocalArtworkIsServedByTheSameHttpServer() {
        val svg = File.createTempFile("vision-art", ".svg").apply {
            writeText("""<svg xmlns="http://www.w3.org/2000/svg"><rect width="8" height="8"/></svg>""")
            deleteOnExit()
        }
        val now = NowPlaying(kind = RemoteProto.KIND_VOD, title = "Local", poster = svg.absolutePath)
        val server = NowPlayingHttpServer(stateProvider = { now }, device = "Test TV")
        val port = server.start()
        assertTrue("el servidor debe abrir un puerto", port > 0)
        try {
            val json = JSONObject(get("http://127.0.0.1:$port/now-playing").second)
            val artwork = json.getString("artwork")
            assertTrue(
                "el arte local debe ser una URL absoluta del servidor",
                artwork.startsWith("http://127.0.0.1:$port/artwork/"),
            )
            val image = get(artwork)
            assertEquals(200, image.first)
            assertTrue("el servidor entrega el archivo local", image.second.contains("<svg"))
            assertEquals(
                "el arte sí puede cachearse",
                "public, max-age=3600",
                header(artwork, "Cache-Control"),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun theHttpServerServesOnlyNowPlayingAndOnlyTheLan() {
        val server = NowPlayingHttpServer(
            stateProvider = { NowPlaying(kind = RemoteProto.KIND_VOD, title = "House", playing = true) },
            device = "Test TV",
            token = "",
        )
        val port = server.start()
        assertTrue("el servidor debe abrir un puerto", port > 0)
        try {
            val ok = get("http://127.0.0.1:$port/now-playing")
            assertEquals(200, ok.first)
            assertEquals(
                "el estado debe ser no-store (cambia cada segundo)",
                "no-store",
                header("http://127.0.0.1:$port/now-playing", "Cache-Control"),
            )
            val json = JSONObject(ok.second)
            assertEquals("Test TV", json.getString("device"))
            assertEquals("playing", json.getString("state"))
            assertEquals("House", json.getString("program"))

            val notFound = get("http://127.0.0.1:$port/control")
            assertEquals(404, notFound.first)

            assertTrue("loopback es LAN", server.isLanAddress(InetAddress.getByName("127.0.0.1")))
            assertTrue("ULA IPv6 es LAN", server.isLanAddress(InetAddress.getByName("fd00::1")))
            assertTrue("link-local IPv6 es LAN", server.isLanAddress(InetAddress.getByName("fe80::1")))
            assertFalse("una IP pública no ve nada", server.isLanAddress(InetAddress.getByName("8.8.8.8")))
        } finally {
            server.stop()
        }
    }

    @Test
    fun theOptionalTokenIsRequiredWhenConfigured() {
        val server = NowPlayingHttpServer(
            stateProvider = { NowPlaying(kind = RemoteProto.KIND_VOD, title = "House") },
            device = "Test TV",
            token = "secreto",
        )
        val port = server.start()
        assertTrue(port > 0)
        try {
            assertEquals(401, get("http://127.0.0.1:$port/now-playing").first)
            assertEquals(200, get("http://127.0.0.1:$port/now-playing?token=secreto").first)
            assertEquals(200, get("http://127.0.0.1:$port/now-playing", header = "secreto").first)
        } finally {
            server.stop()
        }
    }

    @Test
    fun theAppAdvertisesAndServesItFromAForegroundService() {
        val manifest = source("AndroidManifest.xml")
        assertTrue("servicio declarado", manifest.contains(".data.netshare.NowPlayingService"))
        assertTrue("primer plano dataSync", manifest.contains("android:foregroundServiceType=\"dataSync\""))

        val advertiser = source("data/netshare/NowPlayingAdvertiser.kt")
        assertTrue("tipo mDNS", advertiser.contains("NowPlayingShare.SERVICE_TYPE"))
        assertTrue("registro NsdManager", advertiser.contains("registerService"))
        assertTrue("TXT app", advertiser.contains("setAttribute(\"app\""))
        assertTrue("TXT version", advertiser.contains("setAttribute(\"version\""))
        assertTrue("TXT path", advertiser.contains("setAttribute(\"path\""))

        val share = source("data/netshare/NowPlayingShare.kt")
        assertTrue("tipo exacto", share.contains("_visionplus._tcp"))
        assertTrue("nombre del dispositivo", share.contains("\"Vision+ - "))

        val service = source("data/netshare/NowPlayingService.kt")
        assertTrue("primer plano", service.contains("startForeground("))
        assertTrue("red nueva = re-anuncio", service.contains("registerDefaultNetworkCallback"))
        assertTrue("lee el estado real", service.contains("RemoteBus.playback.value"))

        val server = source("data/netshare/NowPlayingHttpServer.kt")
        assertTrue("solo GET", server.contains("method == \"GET\""))
        assertTrue("solo /now-playing", server.contains("path == NowPlayingShare.PATH"))
        assertTrue("sin ruta de control", !server.contains("/control"))
        assertTrue("solo LAN", server.contains("isSiteLocalAddress"))

        val activity = source("MainActivity.kt")
        assertTrue("se arranca desde la actividad", activity.contains("NowPlayingService::class.java"))
    }

    private fun header(url: String, name: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "HEAD"
        }
        try {
            return connection.getHeaderField(name)
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String, header: String? = null): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "GET"
            if (header != null) setRequestProperty("X-Vision-Token", header)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun source(rel: String): String {
        val found = listOf(
            File("src/main/$rel"),
            File("app/src/main/$rel"),
            File("../app/src/main/$rel"),
            File("src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("app/src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("../app/src/main/java/com/chronosbvrx/visionplus/$rel"),
        ).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
