package com.chronosbvrx.visionplus.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** El estado que cruza de `:x4k` al proceso principal por Messenger. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteBridgeTest {

    @Test
    fun theStructuredStateSurvivesTheMessengerBundle() {
        val original = NowPlaying(
            kind = RemoteProto.KIND_VOD,
            screen = "Tierra de Mafia · T2 · C5",
            title = "Tierra de Mafia",
            subtitle = "T2 · C5",
            poster = "https://img/p.jpg",
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
        val back = RemoteBridge.nowPlayingFromBundle(RemoteBridge.nowPlayingToBundle(original))
        assertEquals(original, back)
    }

    @Test
    fun anEmptyBundleIsAnIdleState() {
        val idle = RemoteBridge.nowPlayingFromBundle(null)
        assertEquals(RemoteProto.KIND_IDLE, idle.kind)
        assertFalse(idle.active)
        assertFalse(idle.live)
    }

    @Test
    fun aLiveStateHasNoSeekAndNoDuration() {
        val live = NowPlaying(
            kind = RemoteProto.KIND_LIVE,
            screen = "Canal 5",
            title = "Canal 5",
            subtitle = "En vivo",
            playing = true,
            canSeek = false,
            updatedAt = 1L,
        )
        val back = RemoteBridge.nowPlayingFromBundle(RemoteBridge.nowPlayingToBundle(live))
        assertEquals(RemoteProto.KIND_LIVE, back.kind)
        assertTrue(back.live)
        assertFalse(back.canSeek)
        assertEquals(0L, back.durationMs)
    }
}
