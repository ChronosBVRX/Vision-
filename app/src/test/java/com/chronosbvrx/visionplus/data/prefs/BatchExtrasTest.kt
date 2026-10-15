package com.chronosbvrx.visionplus.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.follow.FollowStore
import com.chronosbvrx.visionplus.data.kids.KidsMode
import com.chronosbvrx.visionplus.data.notify.ReminderKit
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.stats.formatMinutes
import com.chronosbvrx.visionplus.data.stats.kindLabel
import com.chronosbvrx.visionplus.data.stats.summarize
import com.chronosbvrx.visionplus.data.subs.formatSubsOffset
import com.chronosbvrx.visionplus.data.subs.lookupWithOffset
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import com.chronosbvrx.visionplus.ui.theme.AmbientColor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardianes del lote (OTA 2.5.77): ajustes de subtítulos, seguimiento y avisos,
 * modo niños, estadísticas locales, color ambiental y parseos nuevos del índice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BatchExtrasTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun film(id: String, kind: String = "series") = Film(
        id = id, title = id, year = 2020, durationMin = 30, synopsis = "",
        posterUrl = "", videoUrl = "xtv:$id", sourceUrl = "xtv", kind = kind,
    )

    // ------------------------------------------------------------ ajustes subtítulos

    @Test
    fun subSizeScalesFromSaneBases() {
        assertEquals(26f, VisionSettings.subSizeSp("normal", true))
        assertEquals(16f, VisionSettings.subSizeSp("normal", false))
        assertEquals(26f * 0.8f, VisionSettings.subSizeSp("small", true))
        assertEquals(16f * 1.3f, VisionSettings.subSizeSp("large", false))
        assertEquals(26f, VisionSettings.subSizeSp("raro", true))
        assertEquals("Chico", VisionSettings.subSizeLabel("small"))
    }

    @Test
    fun subOffsetClampsToSteps() {
        assertEquals(0, VisionSettings.clampSubOffset(0))
        assertEquals(500, VisionSettings.clampSubOffset(700))
        assertEquals(-500, VisionSettings.clampSubOffset(-700))
        assertEquals(5_000, VisionSettings.clampSubOffset(99_000))
        assertEquals(-5_000, VisionSettings.clampSubOffset(-99_000))
    }

    @Test
    fun subsOffsetFormatsAndApplies() {
        assertEquals("0 s", formatSubsOffset(0))
        assertEquals("+1.5 s", formatSubsOffset(1500))
        assertEquals("−0.5 s", formatSubsOffset(-500))
        assertEquals("+5 s", formatSubsOffset(5000))
        assertEquals(9_500L, lookupWithOffset(10_000L, 500))
        assertEquals(0L, lookupWithOffset(200L, 500))
    }

    @Test
    fun audioLangKeysAreSafeAndRoundTrip() {
        assertEquals("audio_lang_abc123", VisionSettings.audioLangKey("abc123"))
        assertTrue(VisionSettings.audioLangKey("a/b c").matches(Regex("audio_lang_[A-Za-z0-9._-]+")))
        VisionSettings.setAudioLang(context, "serie-1", "Japonés")
        assertEquals("Japonés", VisionSettings.audioLangOf(context, "serie-1"))
        assertEquals("", VisionSettings.audioLangOf(context, ""))
        VisionSettings.setAudioLang(context, "", "x")
    }

    // ------------------------------------------------------------ seguimiento y avisos

    @Test
    fun followsToggleAndSurviveReload() {
        FollowStore.toggle(context, "tt1", "Serie Uno")
        assertTrue(FollowStore.isFollowing(context, "tt1"))
        assertEquals("Serie Uno", FollowStore.followed(context)["tt1"])
        assertFalse(FollowStore.toggle(context, "tt1", "Serie Uno"))
        assertTrue(FollowStore.followed(context).isEmpty())
        assertFalse(FollowStore.toggle(context, "  ", "X"))
        assertFalse(FollowStore.isFollowing(context, ""))
    }

    @Test
    fun upcomingMatchesFollowedInsideTheWindow() {
        val now = 1_000_000_000_000L
        val airings = listOf(
            RemoteIndexSource.Airing("A", 1, 1, "", now + 3_600_000L, "tt1"),
            RemoteIndexSource.Airing("B", 1, 1, "", now + 3_600_000L, "tt-otro"),
            RemoteIndexSource.Airing("C", 1, 1, "", now - 3_600_000L, "tt1"),
            RemoteIndexSource.Airing("D", 1, 1, "", now + 72 * 3_600_000L, "tt1"),
            RemoteIndexSource.Airing("E", 2, 3, "", now + 3_600_000L, "", "xtv:EEE"),
        )
        val out = FollowStore.upcomingForFollowed(setOf("tt1", "xtv:EEE"), airings, now)
        assertEquals(listOf("A", "E"), out.map { it.show })
    }

    @Test
    fun remindTimeSubtractsMinutes() {
        assertEquals(1_800_000L - 30 * 60_000L, ReminderKit.remindAtMs(1_800_000L, 30))
        assertEquals(1_800_000L, ReminderKit.remindAtMs(1_800_000L, 0))
        assertEquals(1_800_000L - 24 * 60 * 60_000L, ReminderKit.remindAtMs(1_800_000L, 9999))
    }

    @Test
    fun airingsParseToleratesBadDates() {
        val arr = JSONArray().apply {
            put(JSONObject().apply {
                put("show", "X")
                put("season", 1)
                put("episode", 2)
                put("title", "C2")
                put("airtime", "2026-10-20T02:00:00+00:00")
                put("imdb", "tt9")
            })
            put(JSONObject().apply { put("show", "Y") })
            put(JSONObject())
        }
        val out = RemoteIndexSource.parseAirings(arr)
        assertEquals(2, out.size)
        assertEquals("tt9", out[0].imdb)
        assertTrue(out[0].airtimeMs > 0)
        assertEquals(0L, out[1].airtimeMs)
        assertTrue(RemoteIndexSource.parseAirings(null).isEmpty())
    }

    @Test
    fun filmObjectsParseAndNormalize() {
        val f = RemoteIndexSource.parseFilmObject(
            JSONObject().apply {
                put("id", "magis:ABC")
                put("videoUrl", "magisvod:ABC")
                put("title", "T")
            },
        )!!
        assertEquals("xtv:ABC", f.id)
        assertEquals("xtv:ABC", f.videoUrl)
        assertEquals(null, RemoteIndexSource.parseFilmObject(JSONObject()))
    }

    // ------------------------------------------------------------ modo niños

    @Test
    fun kidsPinHashesAndVerifies() {
        assertTrue(KidsMode.isValidPin("1234"))
        assertFalse(KidsMode.isValidPin("123"))
        assertFalse(KidsMode.isValidPin("abcd"))
        assertTrue(KidsMode.setPin(context, "2468"))
        assertFalse(KidsMode.setPin(context, "12"))
        assertTrue(KidsMode.verify(context, "2468"))
        assertFalse(KidsMode.verify(context, "0000"))
        assertTrue(KidsMode.sha256("1") != KidsMode.sha256("2"))
        assertEquals(64, KidsMode.sha256("1").length)
    }

    @Test
    fun kidsGateNeedsLockWithoutPass() {
        assertTrue(KidsMode.gateClosed(lockedNow = true, hasPassNow = false))
        assertFalse(KidsMode.gateClosed(lockedNow = true, hasPassNow = true))
        assertFalse(KidsMode.gateClosed(lockedNow = false, hasPassNow = false))
    }

    @Test
    fun kidsPassGrantsTemporaryEntry() {
        KidsMode.pass(context, minutes = 15)
        assertTrue(KidsMode.hasPass(context))
    }

    @Test
    fun kidsRowsKeepOnlyKids() {        val rows = listOf(
            CatalogRow("a", "A", listOf(film("f1", "movie"), film("f2", "kids"))),
            CatalogRow("b", "B", listOf(film("f3", "series"))),
        )
        val out = KidsMode.kidsRows(rows)
        assertEquals(1, out.size)
        assertEquals(listOf("f2"), out[0].films.map { it.id })
        assertTrue(KidsMode.kidsRows(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------ estadísticas

    @Test
    fun activitySummarizesLocalHistory() {
        val films = mapOf("f1" to film("f1", "series"), "f2" to film("f2", "movie"))
        val entries = listOf(
            ResumeEntry(key = "f1", title = "Serie · T1 · C1", positionMs = 40 * 60_000L, durationMs = 42 * 60_000L, updatedAt = 0L, episodeIds = listOf("a", "b")),
            ResumeEntry(key = "f2", title = "Peli", positionMs = 90 * 60_000L, durationMs = 90 * 60_000L, updatedAt = 0L),
            ResumeEntry(key = "f9", title = "Rara", positionMs = 10 * 60_000L, durationMs = 0L, updatedAt = 0L),
        )
        val stats = summarize(entries, films)
        assertEquals(1, stats.episodesFinished)
        assertEquals(1, stats.filmsFinished)
        assertEquals(1, stats.seriesCount)
        assertEquals(142L, stats.minutesWatched)
        assertFalse(stats.isEmpty)
        assertTrue(summarize(emptyList(), emptyMap()).isEmpty)
    }

    @Test
    fun minutesFormatReadsNaturally() {
        assertEquals("45 min", formatMinutes(45))
        assertEquals("2 h", formatMinutes(120))
        assertEquals("2 h 5 min", formatMinutes(125))
        assertEquals("Series", kindLabel("series"))
        assertEquals("", kindLabel("raro"))
    }

    @Test
    fun recommendationsPreferRecentTaste() {
        // `played` va de lo más reciente a lo más viejo (como lo guarda recordPlay).
        val played = listOf(
            film("d9").copy(id = "d9"),
            film("a1").copy(id = "a1"),
        )
        val rows = listOf(
            CatalogRow("accion", "Acción", listOf(film("a1"), film("a9"))),
            CatalogRow("drama", "Drama", listOf(film("d9"), film("d1"))),
        )
        val rec = com.chronosbvrx.visionplus.data.UserLibrary.recommendations(rows, played, limit = 10)
        assertTrue(rec != null)
        assertEquals("Drama", rec!!.first)
        assertTrue(rec.second.any { it.id == "d1" })
        assertTrue(rec.second.none { it.id == "a1" || it.id == "d9" })
        assertEquals(null, com.chronosbvrx.visionplus.data.UserLibrary.recommendations(rows, emptyList()))
        assertEquals(null, com.chronosbvrx.visionplus.data.UserLibrary.recommendations(emptyList(), played))
    }

    // ------------------------------------------------------------ color ambiental

    @Test
    fun ambientAveragesAndDarkens() {
        val red = android.graphics.Color.RED
        val avg = AmbientColor.averageOf(intArrayOf(red, red, red, red))
        assertEquals(red, avg)
        assertEquals(android.graphics.Color.BLACK, AmbientColor.averageOf(intArrayOf()))
        val dark = AmbientColor.backgroundFor(android.graphics.Color.WHITE)
        assertTrue(android.graphics.Color.red(dark) < 100)
    }
}
