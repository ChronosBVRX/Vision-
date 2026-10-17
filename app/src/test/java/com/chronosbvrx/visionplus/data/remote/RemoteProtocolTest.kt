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
