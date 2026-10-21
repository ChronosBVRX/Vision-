package com.chronosbvrx.visionplus.data.sync

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.ResumeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas de la continuidad entre equipos: qué gana en cada conflicto, cómo se colapsa una
 * serie y cómo viajan los borrados. Puro: sin red ni Android.
 */
class SyncMergeTest {

    private fun film(id: String, title: String = id): Film =
        Film(id, title, 2020, 90, "", "", "xtv:$id", "xtv")

    private fun entry(
        key: String,
        at: Long,
        title: String = "Serie · T1 · C1",
        series: Boolean = false,
    ): ResumeEntry = ResumeEntry(
        key = key,
        title = title,
        xtvCode = key,
        seriesId = if (series) "s1" else "",
        episodeIds = if (series) listOf("e1", "e2") else emptyList(),
        episodeIndex = 0,
        seasonIds = if (series) listOf("t1") else emptyList(),
        seasonNames = if (series) listOf("T1") else emptyList(),
        positionMs = 60_000,
        durationMs = 600_000,
        updatedAt = at,
    )

    @Test
    fun firstSyncUnionsFavoritesInsteadOfOverwritingThem() {
        val local = SyncSnapshot(favorites = listOf(film("A")))
        val remote = listOf(RemoteSyncItem(KIND_FAV, "B", 1000, film = film("B")))
        val result = mergeSync(local, remote, emptyMap(), emptyMap(), nowMs = 5000)
        assertEquals(setOf("A", "B"), result.snapshot.favorites.map { it.id }.toSet())
        assertTrue(result.times.containsKey(stampKey(KIND_FAV, "A")))
        assertTrue(result.times.containsKey(stampKey(KIND_FAV, "B")))
    }

    @Test
    fun theNewestResumeEntryWins() {
        val local = SyncSnapshot(resume = listOf(entry("A", 1000)))
        val remote = listOf(RemoteSyncItem(KIND_RESUME, "A", 2000, resume = entry("A", 2000)))
        val result = mergeSync(local, remote, emptyMap(), emptyMap(), nowMs = 5000)
        assertEquals(1, result.snapshot.resume.size)
        assertEquals(2000L, result.snapshot.resume.first().updatedAt)
    }

    @Test
    fun anOlderRemoteResumeDoesNotGoBack() {
        val local = SyncSnapshot(resume = listOf(entry("A", 4000)))
        val remote = listOf(RemoteSyncItem(KIND_RESUME, "A", 2000, resume = entry("A", 2000)))
        val result = mergeSync(local, remote, emptyMap(), emptyMap(), nowMs = 5000)
        assertEquals(4000L, result.snapshot.resume.first().updatedAt)
    }

    @Test
    fun oneSeriesKeepsOnlyItsMostRecentChapter() {
        val local = SyncSnapshot(resume = listOf(entry("E1", 1000, "Serie · T1 · C1", series = true)))
        val remote = listOf(
            RemoteSyncItem(KIND_RESUME, "E2", 2000, resume = entry("E2", 2000, "Serie · T1 · C2", series = true)),
        )
        val result = mergeSync(local, remote, emptyMap(), emptyMap(), nowMs = 5000)
        assertEquals(listOf("E2"), result.snapshot.resume.map { it.key })
    }

    @Test
    fun aNewerTombstoneRemovesTheLocalItemAndIsRemembered() {
        val local = SyncSnapshot(favorites = listOf(film("A")))
        val result = mergeSync(
            local,
            listOf(RemoteSyncItem(KIND_FAV, "A", 2000, deleted = true)),
            mapOf(stampKey(KIND_FAV, "A") to 1000L),
            emptyMap(),
            nowMs = 5000,
        )
        assertTrue(result.snapshot.favorites.isEmpty())
        assertEquals(2000L, result.removed[stampKey(KIND_FAV, "A")])
    }

    @Test
    fun anOlderTombstoneCannotRemoveSomethingNewer() {
        val local = SyncSnapshot(favorites = listOf(film("A")))
        val result = mergeSync(
            local,
            listOf(RemoteSyncItem(KIND_FAV, "A", 1000, deleted = true)),
            mapOf(stampKey(KIND_FAV, "A") to 3000L),
            emptyMap(),
            nowMs = 5000,
        )
        assertEquals(listOf("A"), result.snapshot.favorites.map { it.id })
        assertFalse(result.removed.containsKey(stampKey(KIND_FAV, "A")))
    }

    @Test
    fun aPresentItemRevivesWhatWasTombstoned() {
        val result = mergeSync(
            SyncSnapshot(),
            listOf(RemoteSyncItem(KIND_FAV, "A", 3000, film = film("A"))),
            emptyMap(),
            mapOf(stampKey(KIND_FAV, "A") to 2000L),
            nowMs = 5000,
        )
        assertEquals(listOf("A"), result.snapshot.favorites.map { it.id })
        assertFalse(result.removed.containsKey(stampKey(KIND_FAV, "A")))
    }

    @Test
    fun watchedIsAUnionAndIgnoresRemoteDeletes() {
        val local = SyncSnapshot(watched = setOf("s1|1"))
        val remote = listOf(
            RemoteSyncItem(KIND_WATCHED, "s1", 1000, watched = listOf(2)),
            RemoteSyncItem(KIND_WATCHED, "s1", 2000, deleted = true),
        )
        val result = mergeSync(local, remote, emptyMap(), emptyMap(), nowMs = 5000)
        assertEquals(setOf("s1|1", "s1|2"), result.snapshot.watched)
    }

    @Test
    fun watchedTravelsOneItemPerSeriesNotOnePerEpisode() {
        val local = SyncSnapshot(watched = (1..500).map { "s1|$it" }.toSet())
        assertEquals(setOf(stampKey(KIND_WATCHED, "s1")), snapshotKeys(local))
    }

    @Test
    fun detectRemovalsStampsTheFirstTimeAndDoesNotRefreshIt() {
        val times = mapOf(stampKey(KIND_FAV, "A") to 1000L)
        val (afterTimes, firstRemoved) = detectRemovals(times, emptySet(), emptyMap(), nowMs = 5000)
        assertFalse(afterTimes.containsKey(stampKey(KIND_FAV, "A")))
        assertEquals(5000L, firstRemoved[stampKey(KIND_FAV, "A")])

        val (_, secondRemoved) = detectRemovals(emptyMap(), emptySet(), firstRemoved, nowMs = 9000)
        assertEquals("un reintento no refresca la hora del borrado", 5000L, secondRemoved[stampKey(KIND_FAV, "A")])
    }

    @Test
    fun aReAddedItemDropsItsTombstone() {
        val (_, removed) = detectRemovals(
            emptyMap(),
            setOf(stampKey(KIND_FAV, "A")),
            mapOf(stampKey(KIND_FAV, "A") to 1000L),
            nowMs = 5000,
        )
        assertFalse(removed.containsKey(stampKey(KIND_FAV, "A")))
    }

    @Test
    fun oldTombstonesArePruned() {
        val now = 200L * 24 * 60 * 60 * 1000
        val removed = mapOf(
            "fav|viejo" to now - 181L * 24 * 60 * 60 * 1000,
            "fav|nuevo" to now - 10L * 24 * 60 * 60 * 1000,
        )
        assertEquals(setOf("fav|nuevo"), pruneRemoved(removed, now).keys)
    }
}
