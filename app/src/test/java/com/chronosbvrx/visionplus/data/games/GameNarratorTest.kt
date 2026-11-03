package com.chronosbvrx.visionplus.data.games

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GameNarratorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun everyEventHasCopyAndAudioVariants() {
        GameNarrator.resetCuesForTests()
        val party = setOf(
            NarratorEvent.ImpostorSecret, NarratorEvent.ImpostorVote, NarratorEvent.ImpostorCaught,
            NarratorEvent.DrawStart, NarratorEvent.DrawVote, NarratorEvent.AuctionStart,
            NarratorEvent.AuctionReveal, NarratorEvent.FibbageWrite, NarratorEvent.FibbageVote,
            NarratorEvent.ImpostorQuestions, NarratorEvent.ImpostorEscaped,
            NarratorEvent.PartyTimeout, NarratorEvent.AuctionTie, NarratorEvent.LieSweep,
            NarratorEvent.NobodyGuessed, NarratorEvent.FinalRoundDouble,
        )
        NarratorEvent.values().forEach { event ->
            val variants = GameNarrator.variantsFor(event)
            val expected = if (event in party) 3 else 10
            assertEquals("el evento $event debe tener $expected variantes", expected, variants.size)
            variants.forEach { variant ->
                assertTrue("texto vacío en $event", variant.text.isNotBlank())
                assertNotEquals("audio en cero en $event", 0, variant.audio)
            }
            assertEquals(expected, variants.map { it.text }.distinct().size)
        }
        assertNotEquals(0, context.resources.getIdentifier("narrator_welcome_0", "raw", context.packageName))
    }

    @Test
    fun variantIdsMatchTheVoiceFiles() {
        val ids = NarratorEvent.values().map { GameNarrator.variantId(it) }
        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { id ->
            val count = GameNarrator.variantsFor(NarratorEvent.values().first { GameNarrator.variantId(it) == id }).size
            (0 until count).forEach { n ->
                assertNotEquals(
                    "falta el audio narrator_${id}_$n",
                    0, context.resources.getIdentifier("narrator_${id}_$n", "raw", context.packageName),
                )
            }
        }
    }

    @Test
    fun theSameMomentKeepsItsVariantAndTheNextOneChanges() {
        GameNarrator.resetCuesForTests()
        val first = GameNarrator.cue(NarratorEvent.Correct, "trivia:reveal:2:0")
        assertEquals(first, GameNarrator.cue(NarratorEvent.Correct, "trivia:reveal:2:0"))
        val seen = mutableSetOf(first.text)
        repeat(12) { round ->
            seen += GameNarrator.cue(NarratorEvent.Correct, "trivia:reveal:$round:0").text
        }
        assertTrue("las variantes no rotan", seen.size >= 5)
    }

    @Test
    fun pickVariantNeverRepeatsImmediately() {
        val random = kotlin.random.Random(7)
        var last = -1
        repeat(50) {
            val next = GameNarrator.pickVariant(NarratorEvent.Wrong, last, random)
            assertTrue("repetición inmediata", next != last)
            last = next
        }
    }

    @Test
    fun theFourIdleFramesAndTheAvatarExist() {
        val frames = GameNarrator.frames()
        assertEquals(4, frames.size)
        assertEquals(4, frames.distinct().size)
        frames.forEach { assertNotEquals(0, it) }
        assertNotEquals(0, GameNarrator.staticFrame())
        assertNotEquals(0, GameNarrator.avatar())
        NarratorMood.values().forEach { mood ->
            assertNotEquals("falta la pose de $mood", 0, GameNarrator.moodFrame(mood))
        }
    }

    @Test
    fun moodsMatchTheGameMoment() {
        assertEquals(NarratorMood.Welcome, GameNarrator.moodFor(NarratorEvent.Welcome))
        assertEquals(NarratorMood.Happy, GameNarrator.moodFor(NarratorEvent.Correct))
        assertEquals(NarratorMood.Oops, GameNarrator.moodFor(NarratorEvent.Wrong))
        assertEquals(NarratorMood.Celebrate, GameNarrator.moodFor(NarratorEvent.Podium))
        assertEquals(NarratorMood.Thinking, GameNarrator.moodFor(NarratorEvent.Reveal))
    }

    @Test
    fun theHostSpeaksDifferentlyForEachGameMoment() {
        assertTrue(GameNarrator.hostLine("charadas", "turn", "Ana").contains("Ana"))
        assertTrue(GameNarrator.hostLine("wager", "wager").contains("apuestas"))
        assertTrue(GameNarrator.hostLine("wager", "clue").contains("pista"))
        assertTrue(GameNarrator.hostLine("charadas", "reveal", correct = 2).contains("2"))
    }

    @Test
    fun revealEventDistinguishesCorrectWrongAndNoAnswer() {
        assertEquals(NarratorEvent.Correct, GameNarrator.revealEvent(answered = true, correct = true))
        assertEquals(NarratorEvent.Wrong, GameNarrator.revealEvent(answered = true, correct = false))
        assertEquals(NarratorEvent.TimeUp, GameNarrator.revealEvent(answered = false, correct = false))
        assertEquals(NarratorEvent.Started, GameNarrator.phaseEvent("countdown"))
        assertEquals(NarratorEvent.Podium, GameNarrator.phaseEvent("podium"))
        assertEquals(null, GameNarrator.phaseEvent("question"))
    }
}
