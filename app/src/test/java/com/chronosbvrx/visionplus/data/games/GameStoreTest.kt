package com.chronosbvrx.visionplus.data.games

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GameStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("vision_games", Context.MODE_PRIVATE).edit().clear().commit()
        GameStore.resetMemoryForTests()
    }

    @Test
    fun nicknameFallsBackToTheActiveProfileAndIsBounded() {
        val fallback = GameStore.nickname(context)
        assertTrue(fallback.isNotBlank())
        assertTrue(fallback.length <= 14)
        GameStore.saveNickname(context, "  Ana  ")
        assertEquals("Ana", GameStore.nickname(context))
        GameStore.saveNickname(context, "x".repeat(20))
        assertEquals(14, GameStore.nickname(context).length)
    }

    @Test
    fun tokensRoundTripAndForgetOnlyThatRoom() {
        GameStore.savePlayer(context, "abc-234", "player-token")
        GameStore.saveHost(context, "ABC234", "host-token")
        GameStore.savePlayer(context, "XYZ789", "otro")
        assertEquals("player-token", GameStore.playerToken(context, "ABC-234"))
        assertEquals("host-token", GameStore.hostToken(context, "abc234"))
        GameStore.forget(context, "ABC-234")
        assertNull(GameStore.playerToken(context, "ABC234"))
        assertNull(GameStore.hostToken(context, "ABC234"))
        assertEquals("otro", GameStore.playerToken(context, "XYZ789"))
    }

    @Test
    fun recentRoomsAreDedupedAndCapped() {
        assertEquals(listOf("ABC234", "XYZ789"), GameStore.normalizeRecent("abc-234, ABC234, no, XYZ-789"))
        listOf("ABC234", "XYZ789", "DEF234", "GHJ234", "MNP234", "NPQ234").forEach {
            GameStore.remember(context, it)
        }
        val recent = GameStore.recent(context)
        assertEquals(5, recent.size)
        assertEquals("NPQ234", recent.first())
        assertTrue(recent.none { it == "ABC234" })
    }

    @Test
    fun narratorIsOnByDefaultAndCanBeTurnedOff() {
        assertTrue(GameStore.narratorEnabled(context))
        GameStore.setNarratorEnabled(context, false)
        assertFalse(GameStore.narratorEnabled(context))
        GameStore.setNarratorEnabled(context, true)
        assertTrue(GameStore.narratorEnabled(context))
    }
}
