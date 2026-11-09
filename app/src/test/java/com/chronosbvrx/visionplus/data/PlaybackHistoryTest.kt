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
        // Los títulos finales pueden durar más de 30 s: con 2 min de margen, salir en los
        // títulos ya cuenta como terminado y no deja la película en «Seguir viendo».
        assertTrue(isFinished(500_000, 600_000))
        assertFalse(shouldKeep(500_000, 600_000))
    }

    @Test
    fun leavingDuringCreditsIsFinishedEvenWithALongCreditSequence() {
        assertTrue(isFinishedAtExit(480_000, 600_000, creditsStartMs = 480_000))
        assertTrue(isFinishedAtExit(475_000, 600_000, creditsStartMs = 480_000))
        assertFalse(isFinishedAtExit(300_000, 600_000, creditsStartMs = 480_000))
        assertTrue(isFinishedAtExit(500_000, 600_000))
        assertFalse(isFinishedAtExit(300_000, 600_000))
    }

    @Test
    fun pruneKeepsRecentUpToMax() {
        val total = PlaybackHistory.MAX_ENTRIES + 5
        val entries = (1..total).map { entry("c$it", updatedAt = it.toLong()) }
        val pruned = prune(entries, 100L)
        assertEquals(PlaybackHistory.MAX_ENTRIES, pruned.size)
        assertEquals("c$total", pruned.first().key)
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

    // ---- «Seguir viendo» de series: ficha, capítulo y minuto exacto ----

    private fun series() = Film(
        id = "serie-grupo", title = "Ley y orden: UVE", year = 2024, durationMin = 45,
        synopsis = "s", posterUrl = "p", videoUrl = "xtv:serie-1", sourceUrl = "xtv",
        seasons = listOf(SeasonRef(1, "serie-1", "xtv:serie-1"), SeasonRef(2, "serie-2", "xtv:serie-2")),
    )

    @Test
    fun resumeEntryForSeriesFindsTheLastChapterBySeasonIdOrTitle() {
        val bySeason = entry("c1", updatedAt = 10L).copy(
            title = "Ley y orden: UVE · T2 · C3", seriesId = "serie-2",
            episodeIds = listOf("a", "b"), seasonIds = listOf("serie-1", "serie-2"),
            seasonNames = listOf("T1", "T2"), seasonIndex = 1, episodeIndex = 2,
        )
        val byTitle = entry("c2", updatedAt = 5L).copy(
            title = "Ley y orden: UVE · T1 · C1", seriesId = "temporada-vieja",
            episodeIds = listOf("a", "b"), episodeIndex = 0,
        )
        assertEquals("c1", resumeEntryForSeries(listOf(bySeason, byTitle), series())?.key)
        // Una película suelta con el mismo nombre no se confunde con un capítulo.
        val movie = entry("m", updatedAt = 9L).copy(title = "Ley y orden: UVE")
        assertEquals("c1", resumeEntryForSeries(listOf(movie, bySeason), series())?.key)
    }

    @Test
    fun continueOpensSeriesOnlyForRealEpisodes() {
        val film = series()
        val episode = ResumeCard(
            entry = entry("c1").copy(
                title = "Ley y orden: UVE · T2 · C3", seriesId = "serie-2",
                episodeIds = listOf("a", "b"), episodeIndex = 2,
                seasonIds = listOf("serie-1", "serie-2"), seasonNames = listOf("T1", "T2"),
                seasonIndex = 1,
            ),
            film = film, progress = 0.4f,
        )
        val movie = ResumeCard(entry = entry("m").copy(title = "Una película"), film = film, progress = 0.4f)
        assertTrue(continueOpensSeries(episode))
        assertFalse(continueOpensSeries(movie))
        assertTrue("sin ficha del catálogo la serie igual abre su menú", continueOpensSeries(episode.copy(film = null)))
        assertEquals("T2 · C3", episodeLabelOf(episode.entry))
        assertEquals("", episodeLabelOf(movie.entry))
        val fallback = resumeSeriesFilm(episode.entry)
        assertEquals("Ley y orden: UVE", fallback.title)
        assertEquals("serie-2", fallback.id)
        assertEquals("xtv:serie-2", fallback.videoUrl)
    }

    @Test
    fun matchCardsFindsTheSeriesByItsActiveSeasonAndMarksTheNextChapter() {
        val film = series()
        val finishedFirst = entry("a", positionMs = 2_700_000, durationMs = 2_700_000).copy(
            title = "Ley y orden: UVE · T2 · C1", seriesId = "serie-2",
            episodeIds = listOf("a", "b"), episodeIndex = 0,
            seasonIds = listOf("serie-1", "serie-2"), seasonNames = listOf("T1", "T2"),
            seasonIndex = 1,
        )
        val card = matchCards(listOf(finishedFirst), listOf(film)).single()
        assertEquals("la ficha se encuentra por la temporada activa", film, card.film)
        assertTrue("el capítulo terminó", card.completed)
        assertTrue("pero queda el siguiente", card.hasNext)
        assertEquals("T2 · C1", episodeLabelOf(card.entry))
    }

    @Test
    fun nextEpisodeCrossesTheSeasonBoundaryOnlyAfterFinishing() {
        val lastOfSeason = entry("e10", positionMs = 2_700_000, durationMs = 2_700_000).copy(
            xtvCode = "e10", title = "Serie · T1 · C10", seriesId = "s1",
            episodeIds = listOf("e9", "e10"), episodeIndex = 1,
            seasonIds = listOf("s1", "s2"), seasonNames = listOf("T1", "T2"), seasonIndex = 0,
        )
        val next = nextEpisodeOf(lastOfSeason)!!
        assertTrue(next.crossesSeason)
        assertEquals(1, next.seasonIndex)
        assertEquals(0, next.episodeIndex)
        assertEquals("s2", next.seasonId)
        assertEquals(null, next.episodeCode)
        assertEquals("la ficha aterriza en la temporada nueva", 1, resumeTargetSeasonIndex(lastOfSeason))

        val unfinished = lastOfSeason.copy(positionMs = 120_000, completed = false)
        assertEquals("si no terminó, se queda en su temporada", 0, resumeTargetSeasonIndex(unfinished))
    }

    @Test
    fun theNextEpisodeOfTheSameSeasonCanPlayDirectly() {
        val first = entry("e1").copy(
            xtvCode = "e1", title = "Serie · T1 · C1", seriesId = "s1",
            episodeIds = listOf("e1", "e2"), episodeIndex = 0,
            seasonIds = listOf("s1"), seasonNames = listOf("T1"), seasonIndex = 0,
        )
        val next = nextEpisodeOf(first)!!
        assertFalse(next.crossesSeason)
        assertEquals("e2", next.episodeCode)
        val playable = nextEpisodeEntry(first)!!
        assertEquals("e2", playable.key)
        assertEquals(1, playable.episodeIndex)
        assertEquals(0L, playable.positionMs)
        assertFalse(playable.completed)
    }

    @Test
    fun manualCompletedEntryIsFinishedWithoutDurationMath() {
        val card = matchCards(
            listOf(entry("m", positionMs = 1L, durationMs = 0L).copy(completed = true)),
            emptyList(),
        ).single()
        assertTrue(card.completed)
        assertEquals(1f, card.progress, 0.001f)
    }

    @Test
    fun resumeEpisodePlayCarriesTheExactMinuteAndContext() {
        val entry = entry("c1", positionMs = 1_260_000, durationMs = 2_700_000).copy(
            xtvCode = "c1", title = "Ley y orden: UVE · T2 · C3", seriesId = "serie-2",
            episodeIds = listOf("a", "b"), episodeIndex = 2,
            seasonIds = listOf("serie-1", "serie-2"), seasonNames = listOf("T1", "T2"),
            seasonIndex = 1,
        )
        val play = com.chronosbvrx.visionplus.ui.resumeEpisodePlay(
            entry = entry,
            film = series(),
            seasonNames = listOf("T1", "T2"),
            episodeNames = listOf("A", "B"),
            episodeNumbers = listOf(10, 11),
        )
        assertEquals("c1", play.code)
        assertEquals(" · T2 · C3", play.titleSuffix)
        assertEquals("serie-2", play.seriesId)
        assertEquals(2, play.episodeIndex)
        assertEquals(1_260_000, play.resumeMs)
        assertEquals(2_700_000, play.durationMs)
        assertEquals(listOf("A", "B"), play.episodeNames)
    }
}
