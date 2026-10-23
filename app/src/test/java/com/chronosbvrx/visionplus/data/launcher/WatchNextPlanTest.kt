package com.chronosbvrx.visionplus.data.launcher

import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeEntry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El espejo de «Seguir viendo» en el canal Watch Next del launcher: qué tarjetas se publican,
 * qué se actualiza, qué se retira y qué se queda descartado. Lógica pura (sin Android).
 */
class WatchNextPlanTest {

    private fun entry(
        key: String = "c1",
        title: String = "Película",
        positionMs: Long = 120_000L,
        durationMs: Long = 600_000L,
        updatedAt: Long = 1_000L,
        xtvCode: String = key,
        seriesId: String = "",
        episodeIds: List<String> = emptyList(),
        episodeIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonIndex: Int = 0,
    ) = ResumeEntry(
        key = key, title = title, posterUrl = "https://poster/$key.jpg",
        xtvCode = xtvCode, seriesId = seriesId,
        episodeIds = episodeIds, episodeIndex = episodeIndex,
        seasonIds = seasonIds, seasonIndex = seasonIndex,
        positionMs = positionMs, durationMs = durationMs, updatedAt = updatedAt,
    )

    private fun spec(key: String, updatedAt: Long, isEpisode: Boolean = false) = WatchNextSpec(
        key = key, isEpisode = isEpisode, title = "T", episodeTitle = "", seasonNumber = 0,
        episodeNumber = 0, seriesId = "", positionMs = 100_000L, durationMs = 600_000L,
        updatedAt = updatedAt, posterUrl = "p", code = key, videoUrl = "", displayTitle = "T",
    )

    @Test
    fun specsMirrorHistoryNewestFirstAndCapTheRow() {
        val entries = (1..12).map { entry(key = "c$it", updatedAt = it.toLong()) }
        val specs = watchNextSpecs(entries)
        assertEquals(WATCH_NEXT_LIMIT, specs.size)
        assertEquals("c12", specs.first().key)
        assertEquals("c3", specs.last().key)
    }

    @Test
    fun completedEntriesDoNotReachTheLauncher() {
        val done = entry(key = "m", positionMs = 600_000L, durationMs = 600_000L)
        assertTrue(watchNextSpecs(listOf(done)).isEmpty())
    }

    @Test
    fun specsSplitSeriesTitleFromTheEpisodeMark() {
        val spec = watchNextSpecs(
            listOf(
                entry(
                    key = "e3", title = "La Casa de Papel · T2 · C3",
                    seriesId = "s1", episodeIds = listOf("e1", "e2", "e3"), episodeIndex = 2,
                    seasonIds = listOf("t1", "t2"), seasonIndex = 1,
                ),
            ),
        ).single()
        assertTrue(spec.isEpisode)
        assertEquals("La Casa de Papel", spec.title)
        assertEquals("T2 · C3", spec.episodeTitle)
        assertEquals(2, spec.seasonNumber)
        assertEquals(3, spec.episodeNumber)
        assertEquals("s1", spec.seriesId)
        assertEquals("La Casa de Papel · T2 · C3", spec.displayTitle)
    }

    @Test
    fun aLonePortalCardStaysAMovie() {
        val spec = watchNextSpecs(listOf(entry(key = "m1", title = "Toy Story (1995)"))).single()
        assertEquals(false, spec.isEpisode)
        assertEquals("Toy Story (1995)", spec.title)
        assertEquals("", spec.episodeTitle)
    }

    @Test
    fun specsKeepOnlyOneEpisodePerSeries() {
        val older = entry(key = "e1", title = "Serie · C1", seriesId = "s1", episodeIds = listOf("x", "y"), updatedAt = 10L)
        val newer = entry(key = "e2", title = "Serie · C2", seriesId = "s1", episodeIds = listOf("x", "y"), updatedAt = 20L)
        val movie = entry(key = "m", updatedAt = 15L)
        val specs = watchNextSpecs(listOf(older, newer, movie))
        assertEquals(listOf("e2", "m"), specs.map { it.key })
    }

    @Test
    fun specsCollapseOneSeriesAcrossSeasons() {
        // Al cruzar de temporada el reproductor guarda el id de la temporada nueva: la tarjeta
        // sigue siendo una sola porque la clave por serie es el nombre visible del título.
        val s1 = entry(
            key = "e10", title = "Serie · T1 · C10", seriesId = "serie-1",
            episodeIds = listOf("x", "y"), updatedAt = 10L,
        )
        val s2 = entry(
            key = "e21", title = "Serie · T2 · C1", seriesId = "temporada-2",
            episodeIds = listOf("x", "y"), updatedAt = 20L,
        )
        assertEquals(listOf("e21"), watchNextSpecs(listOf(s1, s2)).map { it.key })
    }

    @Test
    fun reconcileInsertsUpdatesAndDeletes() {
        val desired = listOf(spec("a", updatedAt = 200L), spec("nueva", updatedAt = 150L))
        val existing = listOf(
            ExistingWatchNext(rowId = 7, key = "a", positionMs = 10, durationMs = 100, updatedAt = 100),
            ExistingWatchNext(rowId = 9, key = "fuera", positionMs = 10, durationMs = 100, updatedAt = 100),
        )
        val plan = reconcileWatchNext(desired, existing)
        assertEquals(listOf("nueva"), plan.inserts.map { it.key })
        assertEquals(listOf(7L to desired[0]), plan.updates)
        assertEquals(listOf(9L), plan.deletes)
    }

    @Test
    fun reconcileNeverRegressesANewerCardFromAnotherWriter() {
        // El proceso principal puede traer una copia atrasada del historial: jamás debe
        // retroceder una tarjeta que el reproductor ya publicó más nueva.
        val stale = spec("a", updatedAt = 100L)
        val existing = listOf(ExistingWatchNext(rowId = 1, key = "a", positionMs = 500_000, durationMs = 600_000, updatedAt = 500L))
        val plan = reconcileWatchNext(listOf(stale), existing)
        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun dismissalsFileRoundTripsAndKeepsLastLine() {
        val file = File.createTempFile("watch-next-dismissed", ".txt").apply { delete() }
        try {
            appendDismissed(file, "k1", 111L)
            appendDismissed(file, "k2", 222L)
            appendDismissed(file, "k1", 333L)
            val map = readDismissed(file)
            assertEquals(333L, map["k1"])
            assertEquals(222L, map["k2"])
        } finally {
            file.delete()
        }
    }

    @Test
    fun filterDismissedHidesUntilANewerTouch() {
        val s = spec("a", updatedAt = 100L)
        assertTrue(filterDismissed(listOf(s), mapOf("a" to 100L)).isEmpty())
        assertTrue(filterDismissed(listOf(s), mapOf("a" to 99L)).isNotEmpty())
        assertTrue(filterDismissed(listOf(s), emptyMap()).isNotEmpty())
    }

    @Test
    fun prefsXmlYieldsTheHistoryDespiteEscapes() {
        val saved = entry(key = "abc", title = "Tom & Jerry", updatedAt = 5L)
        val json = org.json.JSONArray().put(PlaybackHistory.entryToJson(saved)).toString()
        val xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
            "<map>\n    <string name=\"entries\">${json.replace("&", "&amp;")}</string>\n</map>"
        assertEquals(listOf(saved), entriesFromPrefsXml(xml))
    }

    @Test
    fun prefsXmlWithoutHistoryIsEmptyAndBrokenIsNull() {
        assertTrue(entriesFromPrefsXml("<map></map>").orEmpty().isEmpty())
        assertNull(entriesFromPrefsXml(null))
        assertNull(entriesFromPrefsXml("<map><string name=\"entries\">[{\"key\""))
    }
}
