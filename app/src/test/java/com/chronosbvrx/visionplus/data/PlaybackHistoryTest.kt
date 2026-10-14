package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El historial de reproducción: qué se guarda, qué se borra y cómo se pinta.
 * La fila «Seguir viendo» depende de esta lógica pura.
 */
class PlaybackHistoryTest {

    private fun entry(
        key: String = "c1",
        positionMs: Long = 120_000L,
        durationMs: Long = 600_000L,
        updatedAt: Long = 1_000_000L,
    ) = ResumeEntry(
        key = key, title = "T", positionMs = positionMs,
        durationMs = durationMs, updatedAt = updatedAt,
    )

    @Test
    fun keepNeedsProgressAndRoomToTheEnd() {
        assertTrue(shouldKeep(120_000, 600_000))
        assertFalse(shouldKeep(5_000, 600_000))
        assertFalse(shouldKeep(590_000, 600_000))
        assertTrue(shouldKeep(120_000, 0))
    }

    @Test
    fun finishedMeansStartOver() {
        assertTrue(isFinished(585_000, 600_000))
        assertFalse(isFinished(120_000, 600_000))
        assertFalse(isFinished(120_000, 0))
    }

    @Test
    fun pruneKeepsRecentUpToMax() {
        val entries = (1..25).map { entry("c$it", updatedAt = it.toLong()) }
        val pruned = prune(entries, 50L)
        assertEquals(PlaybackHistory.MAX_ENTRIES, pruned.size)
        assertEquals("c25", pruned.first().key)
        // Momias de más de 90 días fuera.
        val old = entry("viejo", updatedAt = 0L)
        assertTrue(prune(listOf(old) + entries, 100L * 24 * 60 * 60 * 1000).none { it.key == "viejo" })
    }

    @Test
    fun formatResumeClock() {
        assertEquals("02:00", formatResume(120_000))
        assertEquals("00:00", formatResume(0))
        assertEquals("1:02:03", formatResume(3_723_000))
    }

    @Test
    fun jsonRoundTrip() {
        val full = ResumeEntry(
            key = "xtv:abc", title = "Serie · C3", posterUrl = "p", videoUrl = "",
            xtvCode = "abc", seriesId = "s", episodeIds = listOf("a", "b"), episodeIndex = 2,
            seasonIds = listOf("s"), seasonNames = listOf("T1"), seasonIndex = 0,
            positionMs = 120_000, durationMs = 600_000, updatedAt = 999,
        )
        val back = PlaybackHistory.entryFromJson(PlaybackHistory.entryToJson(full))
        assertEquals(full, back)
    }

    @Test
    fun matchCardsFindsXtvFilmsByCode() {
        val film = Film(
            id = "abc", title = "Serie", year = 2024, durationMin = 40, synopsis = "s",
            posterUrl = "p", videoUrl = "xtv:abc", sourceUrl = "xtv",
        )
        val cards = matchCards(
            listOf(entry("abc").copy(xtvCode = "abc", title = "Serie · C3")),
            listOf(film),
        )
        assertEquals(film, cards.single().film)
        assertEquals(0.2f, cards.single().progress, 0.001f)
    }

    @Test
    fun matchCardsKeepsOrphansWithoutFilm() {
        val cards = matchCards(listOf(entry("perdida")), emptyList())
        assertEquals(null, cards.single().film)
        assertEquals("perdida", cards.single().entry.key)
    }

    // ---- Una sola tarjeta por serie en «Seguir viendo» (el último capítulo) ----

    @Test
    fun latestBySeriesKeepsOnlyTheNewestEpisode() {
        val c1 = entry("e1", updatedAt = 10L)
            .copy(title = "Serie · T1 · C1", episodeIds = listOf("x", "y"), episodeIndex = 0)
        val c2 = entry("e2", updatedAt = 30L)
            .copy(title = "Serie · T1 · C2", episodeIds = listOf("x", "y"), episodeIndex = 1)
        val movie = entry("m1", updatedAt = 20L)
        val other = entry("o1", updatedAt = 5L)
            .copy(title = "Otra · C1", episodeIds = listOf("a", "b"))
        // Como los entrega list(): recientes primero.
        val kept = latestBySeries(listOf(c2, movie, c1, other))
        assertEquals(listOf("e2", "m1", "o1"), kept.map { it.key })
    }

    @Test
    fun latestBySeriesSurvivesTheSeasonIdSwitch() {
        // Al cruzar de temporada el reproductor guarda el id de la temporada nueva: la misma
        // serie no debe partirse en dos tarjetas.
        val s1 = entry("e10", updatedAt = 10L).copy(
            title = "Serie · T1 · C10", seriesId = "serie-1",
            episodeIds = listOf("x", "y"), seasonIds = listOf("t1", "t2"),
        )
        val s2 = entry("e21", updatedAt = 20L).copy(
            title = "Serie · T2 · C1", seriesId = "temporada-2",
            episodeIds = listOf("x", "y"), seasonIds = listOf("t1", "t2"),
        )
        assertEquals(listOf("e21"), latestBySeries(listOf(s2, s1)).map { it.key })
    }

    @Test
    fun latestBySeriesNeverCollapsesLooseFilms() {
        val a = entry("a", updatedAt = 1L)
        val b = entry("b", updatedAt = 2L)
        assertEquals(listOf("b", "a"), latestBySeries(listOf(b, a)).map { it.key })
    }

    @Test
    fun skippingNearTheEndCountsWatched() {
        assertTrue(shouldCountWatchedOnSkip(2_280_000, 2_400_000))   // 95%: créditos al frente
        assertFalse(shouldCountWatchedOnSkip(2_000_000, 2_400_000))  // 83%: le falta mucho
        assertFalse(shouldCountWatchedOnSkip(100_000, 0))            // sin duración no hay juicio
    }
}
