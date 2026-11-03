package com.chronosbvrx.visionplus.ui.tv

import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.ResumeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Seguir viendo»: nombre de la obra y capítulo/minuto, no solo «Siguiente capítulo». */
class TvContinueMetaTest {

    private fun card(
        index: Int = 3,
        positionMs: Long = 20 * 60_000L + 31_000L,
        completed: Boolean = false,
    ) = ResumeCard(
        entry = ResumeEntry(
            key = "xtv:abc",
            title = "The Boys · T1",
            seasonNames = listOf("T1", "T2"),
            seasonIds = listOf("s1", "s2"),
            episodeIds = listOf("e1", "e2", "e3", "e4", "e5", "e6"),
            seasonIndex = 0,
            episodeIndex = index,
            positionMs = positionMs,
            updatedAt = 1L,
        ),
        film = null,
        progress = 0.3f,
        completed = completed,
        hasNext = true,
    )

    @Test
    fun titleIsTheWorkNameAndTheMetaShowsSeasonEpisodeAndMinute() {
        assertEquals("The Boys", continueTitle(card()))
        assertEquals("T1:E4 • Continuar en 20:31", continueMetaLine(card(), opensSeries = true, nextUp = false))
    }

    @Test
    fun theNextChapterLineUsesTheNextEpisodeNumber() {
        assertEquals(
            "Siguiente capítulo: T1:E5",
            continueMetaLine(card(completed = true), opensSeries = true, nextUp = true),
        )
    }

    @Test
    fun aMovieKeepsItsTitleAndContinueMinute() {
        val movie = ResumeCard(
            entry = ResumeEntry(
                key = "movie", title = "Spider-Man", positionMs = 12 * 60_000L, updatedAt = 1L,
            ),
            film = null,
            progress = 0.2f,
        )
        assertEquals("Spider-Man", continueTitle(movie))
        assertEquals("Continuar en 12:00", continueMetaLine(movie, opensSeries = false, nextUp = false))
    }
}
