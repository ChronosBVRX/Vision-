package com.chronosbvrx.visionplus.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El conteo de la descontinuación no debe depender del reloj del equipo ni de la zona horaria. */
class MigrationNoticeTest {

    private val deadline = 1_794_290_399_000L // 2026-11-09 23:59:59 -06:00

    @Test
    fun fiveDaysBeforeShowsFive() {
        assertEquals(5, MigrationNotice.remainingDaysFor(deadline, deadline - 5 * MigrationNotice.DAY_MS))
    }

    @Test
    fun roundsPartialDaysUp() {
        assertEquals(5, MigrationNotice.remainingDaysFor(deadline, deadline - 4 * MigrationNotice.DAY_MS - 1))
        assertEquals(4, MigrationNotice.remainingDaysFor(deadline, deadline - 4 * MigrationNotice.DAY_MS + 1))
    }

    @Test
    fun theLastDayShowsOneAndExpiredShowsZero() {
        assertEquals(1, MigrationNotice.remainingDaysFor(deadline, deadline - 1))
        assertEquals(0, MigrationNotice.remainingDaysFor(deadline, deadline))
        assertEquals(0, MigrationNotice.remainingDaysFor(deadline, deadline + 10 * MigrationNotice.DAY_MS))
    }

    @Test
    fun noDeadlineMeansNoNotice() {
        assertNull(MigrationNotice.remainingDaysFor(0L, System.currentTimeMillis()))
        assertNull(MigrationNotice.remainingDaysFor(-1L, System.currentTimeMillis()))
    }

    @Test
    fun headlineUsesDaysAndTheLastDay() {
        assertEquals(
            "Esta aplicación quedará descontinuada en 5 días",
            MigrationNotice.headlineFor(deadline, deadline - 5 * MigrationNotice.DAY_MS),
        )
        assertEquals(
            "Esta aplicación quedará descontinuada en 1 día",
            MigrationNotice.headlineFor(deadline, deadline - MigrationNotice.DAY_MS),
        )
        assertEquals(
            "Esta aplicación quedará descontinuada hoy",
            MigrationNotice.headlineFor(deadline, deadline),
        )
    }
}
