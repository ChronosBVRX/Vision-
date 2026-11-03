package com.chronosbvrx.visionplus.data.remote

import android.content.Intent
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El puente completo `:x4k` → proceso principal → teléfono, sin red: el reproductor publica su
 * estado por Messenger, el servidor lo ve y las órdenes tipadas regresan al reproductor.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteBridgeFlowTest {

    private lateinit var link: RemoteBridge.PlayerLink
    private val commands = ArrayList<String>()
    private val seeks = ArrayList<Long>()

    @Before
    fun setUp() {
        RemoteBus.clearPlayback()
        link = RemoteBridge.PlayerLink(
            object : RemoteBridge.PlayerLink.Listener {
                override fun onSeek(positionMs: Long) {
                    seeks += positionMs
                }

                override fun onCommand(command: String) {
                    commands += command
                }
            },
        )
        link.attach(Intent().putExtra(RemoteBridge.EXTRA_HOST_MESSENGER, RemoteBridge.hostMessenger))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        link.detach()
        shadowOf(Looper.getMainLooper()).idle()
        RemoteBus.clearPlayback()
    }

    @Test
    fun thePlayerStateReachesTheMainProcess() {
        assertTrue(link.attached)
        val state = NowPlaying(
            kind = RemoteProto.KIND_VOD,
            screen = "Tierra de Mafia · T2 · C5",
            title = "Tierra de Mafia",
            subtitle = "T2 · C5",
            playing = true,
            positionMs = 123_000L,
            durationMs = 3_600_000L,
            canSeek = true,
            hasNext = true,
            updatedAt = System.currentTimeMillis(),
        )
        link.publish(state)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("el proceso principal ya conoce al reproductor", RemoteBridge.hasPlayer())
        assertEquals(state.kind, RemoteBus.playback.value.kind)
        assertEquals(state.title, RemoteBus.playback.value.title)
        assertEquals(state.positionMs, RemoteBus.playback.value.positionMs)
        assertTrue(RemoteBus.playback.value.playing)
        assertTrue(RemoteBus.isPlayingNow())
    }

    @Test
    fun typedCommandsComeBackToThePlayer() {
        link.publish(
            NowPlaying(
                kind = RemoteProto.KIND_VOD,
                title = "Tierra de Mafia",
                playing = true,
                positionMs = 1_000L,
                durationMs = 3_600_000L,
                canSeek = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()

        RemoteBridge.sendSeek(900_000L)
        RemoteBridge.sendCommand(RemoteProto.CMD_PLAY_PAUSE)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(900_000L), seeks)
        assertEquals(listOf(RemoteProto.CMD_PLAY_PAUSE), commands)
    }

    @Test
    fun leavingThePlayerClearsTheTvState() {
        link.publish(
            NowPlaying(
                kind = RemoteProto.KIND_LIVE,
                title = "Canal 5",
                playing = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(RemoteBus.isPlayingNow())

        link.detach()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(RemoteBridge.hasPlayer())
        assertFalse(RemoteBus.isPlayingNow())
        assertEquals(RemoteProto.KIND_IDLE, RemoteBus.playback.value.kind)
    }
}
