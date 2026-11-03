package com.chronosbvrx.visionplus.data.games

/**
 * Puntaje de Vision+ Play. La MISMA tabla vive en `server/api/games.py` y hay un
 * test de paridad en `GameScoreTest` (mismos casos, mismos números). Si cambia un
 * número, se cambian los dos.
 */
object GameScore {
    const val STREAK_STEP = 100
    const val STREAK_CAP = 500
    const val WAGER_MIN = 200
    const val WAGER_MAX = 1000

    data class Result(val points: Int, val streak: Int)

    fun baseFor(difficulty: Int): Int = when (difficulty) {
        1 -> 1000
        2 -> 1500
        3 -> 2000
        else -> 1000
    }

    /** Bono de velocidad topado al 25 % de la base, redondeado hacia arriba. */
    fun speedBonus(base: Int, remainingMs: Long, totalMs: Long): Int {
        if (totalMs <= 0L || remainingMs <= 0L) return 0
        val remaining = remainingMs.coerceAtMost(totalMs)
        return ((base.toLong() * 25L * remaining + totalMs * 50L) / (totalMs * 100L)).toInt()
    }

    fun answerPoints(
        difficulty: Int,
        remainingMs: Long,
        totalMs: Long,
        streak: Int,
        correct: Boolean,
        fair: Boolean = false,
    ): Result {
        if (!correct) return Result(0, 0)
        val base = baseFor(difficulty)
        val newStreak = streak.coerceAtLeast(0) + 1
        val speed = if (fair) 0 else speedBonus(base, remainingMs, totalMs)
        val streakBonus = minOf(STREAK_STEP * (newStreak - 1), STREAK_CAP)
        return Result(base + speed + streakBonus, newStreak)
    }

    fun clampWager(value: Int): Int = value.coerceIn(WAGER_MIN, WAGER_MAX)

    /** Apuesta (Fase 3): nunca deja puntos negativos. */
    fun applyWager(points: Int, stake: Int, correct: Boolean): Int {
        val current = points.coerceAtLeast(0)
        val amount = clampWager(stake)
        return if (correct) current + amount else (current - minOf(amount, current)).coerceAtLeast(0)
    }
}
