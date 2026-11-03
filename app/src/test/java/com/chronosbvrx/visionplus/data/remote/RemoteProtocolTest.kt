package com.chronosbvrx.visionplus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cable del control remoto (un JSON por línea) y la puerta de seguridad de las teclas.
 * Todo puro: el servidor jamás inyecta una tecla que no pase por [RemoteKeys].
 */
class RemoteProtocolTest {

    @Test
    fun roundTripKeepsTheFieldsItNeeds() {
        val msg = RemoteMsg(
            t = RemoteProto.KEY,
            code = RemoteKeys.CENTER,
            action = RemoteProto.ACTION_TAP,
        )
        val back = RemoteProto.decode(RemoteProto.encode(msg))
        assertEquals(RemoteProto.KEY, back?.t)
        assertEquals(RemoteKeys.CENTER, back?.code)
        assertEquals(RemoteProto.ACTION_TAP, back?.action)
    }

    @Test
    fun encodeSkipsNullsSoTheWireStaysShort() {
        val line = RemoteProto.encode(RemoteMsg(t = RemoteProto.PING))
        assertFalse("los campos opcionales en null no deben viajar", line.contains("name"))
        assertFalse(line.contains("token"))
    }

    @Test
    fun decodeIsSafeWithNetworkNoise() {
        assertNull(RemoteProto.decode(null))
        assertNull(RemoteProto.decode(""))
        assertNull(RemoteProto.decode("   "))
        assertNull(RemoteProto.decode("hola, no soy json"))
        assertNull(RemoteProto.decode("""{"otra":"cosa"}"""))
        // Una línea nuestro pero sin tipo tampoco pasa.
        assertNull(RemoteProto.decode("""{"name":"TV"}"""))
    }

    @Test
    fun decodeIgnoresUnknownFieldsFromNewerBuilds() {
        val msg = RemoteProto.decode("""{"t":"key","code":20,"futuro":true}""")
        assertEquals(RemoteProto.KEY, msg?.t)
        assertEquals(20, msg?.code)
    }

    @Test
    fun episodeContextTravelsOnTheWire() {
        val msg = RemoteMsg(
            t = RemoteProto.OPEN,
            ref = "xtv:EP5",
            title = "La Casa de Papel · T2 · C5",
            series = "t2ref",
            seasonIndex = 1,
            seasonIds = listOf("t1ref", "t2ref"),
            seasonNames = listOf("T1", "T2"),
        )
        val back = RemoteProto.decode(RemoteProto.encode(msg))
        assertEquals("t2ref", back?.series)
        assertEquals(1, back?.seasonIndex)
        assertEquals(listOf("t1ref", "t2ref"), back?.seasonIds)
        assertEquals(listOf("T1", "T2"), back?.seasonNames)
    }

    @Test
    fun thePhoneMinuteTravelsOnTheWire() {
        val msg = RemoteMsg(t = RemoteProto.OPEN, ref = "xtv:ABC", pos = 754_000L)
        assertEquals(754_000L, RemoteProto.decode(RemoteProto.encode(msg))?.pos)
    }

    @Test
    fun theSyncCodeTravelsOnTheWire() {
        val msg = RemoteMsg(t = RemoteProto.SYNC, syncCode = "ABCD-EFGH")
        assertEquals("ABCD-EFGH", RemoteProto.decode(RemoteProto.encode(msg))?.syncCode)
    }

    @Test
    fun aMovieOpenStaysShortOnTheWire() {
        val line = RemoteProto.encode(RemoteMsg(t = RemoteProto.OPEN, ref = "xtv:M1", title = "Moana"))
        assertFalse("una película no arrastra contexto de serie", line.contains("series"))
        assertFalse(line.contains("seasonIds"))
        assertFalse("sin minuto no viaja un pos vacío", line.contains("pos"))
    }

    @Test
    fun theNowPlayingStateRoundTripsWithItsProgress() {
        val now = NowPlaying(
            kind = RemoteProto.KIND_VOD,
            screen = "Tierra de Mafia · T2 · C5 · El ajuste",
            title = "Tierra de Mafia",
            subtitle = "T2 · C5 · El ajuste",
            poster = "https://img/poster.jpg",
            ref = "xtv:EP5",
            playing = true,
            positionMs = 754_000L,
            durationMs = 3_600_000L,
            canSeek = true,
            seasonIndex = 1,
            episodeIndex = 4,
            hasNext = true,
            hasPrev = true,
            updatedAt = 1_729_000_000_000L,
        )
        val back = NowPlaying.fromMsg(RemoteProto.decode(RemoteProto.encode(now.asMsg()))!!)
        assertEquals(RemoteProto.KIND_VOD, back.kind)
        assertEquals("Tierra de Mafia", back.title)
        assertEquals("T2 · C5 · El ajuste", back.subtitle)
        assertEquals("https://img/poster.jpg", back.poster)
        assertEquals(754_000L, back.positionMs)
        assertEquals(3_600_000L, back.durationMs)
        assertTrue(back.playing)
        assertTrue(back.canSeek)
        assertTrue(back.hasNext)
        assertTrue(back.hasPrev)
        assertEquals(1, back.seasonIndex)
        assertEquals(4, back.episodeIndex)
    }

    @Test
    fun aMenuStateHasNoPlaybackFields() {
        val msg = NowPlaying(kind = RemoteProto.KIND_IDLE, screen = "Inicio").asMsg()
        assertNull(msg.kind)
        assertNull(msg.playing)
        assertNull(msg.positionMs)
        assertEquals("Inicio", msg.screen)
        assertEquals(RemoteProto.KIND_IDLE, NowPlaying.fromMsg(msg).kind)
    }

    @Test
    fun seekAndTypedCommandsTravelOnTheWire() {
        val seek = RemoteProto.decode(RemoteProto.encode(RemoteMsg(t = RemoteProto.SEEK, pos = 125_000L)))
        assertEquals(RemoteProto.SEEK, seek?.t)
        assertEquals(125_000L, seek?.pos)

        val cmd = RemoteProto.decode(
            RemoteProto.encode(RemoteMsg(t = RemoteProto.CMD, cmd = RemoteProto.CMD_PLAY_PAUSE)),
        )
        assertEquals(RemoteProto.CMD, cmd?.t)
        assertEquals(RemoteProto.CMD_PLAY_PAUSE, cmd?.cmd)
    }

    @Test
    fun allowlistBlocksTheDangerousKeys() {
        // El D-pad, la reproducción y el volumen entran.
        assertTrue(RemoteKeys.isAllowed(RemoteKeys.UP))
        assertTrue(RemoteKeys.isAllowed(RemoteKeys.BACK))
        assertTrue(RemoteKeys.isAllowed(RemoteKeys.PLAY_PAUSE))
        assertTrue(RemoteKeys.isAllowed(RemoteKeys.CHANNEL_UP))
        assertTrue(RemoteKeys.isAllowed(RemoteKeys.VOLUME_UP))
        // Salir de la app o apagar la tele, jamás: no están en la lista blanca.
        assertFalse(RemoteKeys.isAllowed(android.view.KeyEvent.KEYCODE_HOME))
        assertFalse(RemoteKeys.isAllowed(android.view.KeyEvent.KEYCODE_POWER))
        assertFalse(RemoteKeys.isAllowed(android.view.KeyEvent.KEYCODE_APP_SWITCH))
        assertFalse(RemoteKeys.isAllowed(0))
    }

    @Test
    fun volumeKeysAreHandledByTheSystemNotTheUi() {
        assertTrue(RemoteKeys.isVolume(RemoteKeys.VOLUME_UP))
        assertTrue(RemoteKeys.isVolume(RemoteKeys.VOLUME_DOWN))
        assertTrue(RemoteKeys.isVolume(RemoteKeys.MUTE))
        assertFalse(RemoteKeys.isVolume(RemoteKeys.CENTER))
        assertFalse(RemoteKeys.isVolume(RemoteKeys.BACK))
    }
}
