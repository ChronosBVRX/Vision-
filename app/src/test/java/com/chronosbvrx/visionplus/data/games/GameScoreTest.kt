package com.chronosbvrx.visionplus.data.games

import org.junit.Assert.assertEquals
import org.junit.Test

/** La MISMA tabla que `server/tests/test_games.py::test_score_table_is_the_same_on_both_sides`. */
class GameScoreTest {

    @Test
    fun answerTableMatchesTheServer() {
        assertEquals(GameScore.Result(1250, 1), GameScore.answerPoints(1, 20_000, 20_000, 0, true))
        assertEquals(GameScore.Result(1350, 2), GameScore.answerPoints(1, 20_000, 20_000, 1, true))
        assertEquals(GameScore.Result(2075, 3), GameScore.answerPoints(2, 20_000, 20_000, 2, true))
        assertEquals(GameScore.Result(3000, 6), GameScore.answerPoints(3, 20_000, 20_000, 5, true))
        assertEquals(GameScore.Result(1688, 1), GameScore.answerPoints(2, 10_000, 20_000, 0, true))
        assertEquals(GameScore.Result(0, 0), GameScore.answerPoints(2, 20_000, 20_000, 3, false))
        assertEquals(GameScore.Result(1250, 1), GameScore.answerPoints(99, 20_000, 20_000, 0, true))
    }

    @Test
    fun fairModeRemovesOnlyTheSpeedBonus() {
        assertEquals(GameScore.Result(1800, 4), GameScore.answerPoints(2, 20_000, 20_000, 3, true, fair = true))
    }

    @Test
    fun speedBonusIsCappedAtAQuarterOfTheBase() {
        assertEquals(250, GameScore.speedBonus(1000, 20_000, 20_000))
        assertEquals(125, GameScore.speedBonus(1000, 10_000, 20_000))
        assertEquals(188, GameScore.speedBonus(1500, 10_000, 20_000))
        assertEquals(0, GameScore.speedBonus(2000, 0, 20_000))
        assertEquals(500, GameScore.speedBonus(2000, 99_000, 20_000))
        assertEquals(0, GameScore.speedBonus(1000, 10_000, 0))
    }

    @Test
    fun streakBonusGrowsByHundredAndCapsAtFiveHundred() {
        assertEquals(1000, GameScore.baseFor(1))
        assertEquals(1500, GameScore.baseFor(2))
        assertEquals(2000, GameScore.baseFor(3))
        assertEquals(1000, GameScore.baseFor(99))
    }

    @Test
    fun wagersNeverLeaveNegativePoints() {
        assertEquals(200, GameScore.clampWager(50))
        assertEquals(1000, GameScore.clampWager(5000))
        assertEquals(1500, GameScore.applyWager(1000, 500, true))
        assertEquals(500, GameScore.applyWager(1000, 500, false))
        assertEquals(0, GameScore.applyWager(100, 500, false))
    }
}
