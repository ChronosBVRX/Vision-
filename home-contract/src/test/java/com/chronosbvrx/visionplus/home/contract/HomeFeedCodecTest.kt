package com.chronosbvrx.visionplus.home.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedCodecTest {

    @Test
    fun sportsAndMexicanChannelsSurviveTheRoundTrip() {
        val feed = HomeFeed(
            live = listOf(
                LiveItem(code = "c5", name = "Canal 5", mexico = true, now = "Ana de nadie"),
                LiveItem(code = "axn", name = "AXN", mexico = false),
            ),
            sports = listOf(
                SportsItem(
                    id = "event:1",
                    name = "América vs Cruz Azul",
                    category = "Football",
                    dateIso = "2026-10-23",
                    league = "Liga MX",
                    homeName = "América",
                    awayName = "Cruz Azul",
                    homeScore = 1,
                    awayScore = 0,
                    detail = "45'",
                    live = true,
                    alive = 3,
                    total = 3,
                    openLink = VisionLinks.sports("event:1"),
                ),
                SportsItem(id = "event:2", name = "GP de México", dateText = "Hoy", time = "14:00"),
            ),
        )
        assertEquals(feed, HomeFeedCodec.decode(HomeFeedCodec.encode(feed)))
    }

    @Test
    fun aScoreOfZeroIsNotConfusedWithNoScore() {
        val raw = """{"meta":{"version":${HomeContract.VERSION}},"sports":[{"id":"e","name":"A vs B","homeScore":0,"awayScore":2}]}"""
        val event = HomeFeedCodec.decode(raw)!!.sports.single()
        assertEquals(0, event.homeScore)
        assertEquals(2, event.awayScore)
    }

    private fun sampleFeed() = HomeFeed(
        meta = FeedMeta(
            version = HomeContract.VERSION,
            generatedAt = 1_760_000_000_000L,
            profile = "Sala",
            profiles = 3,
            online = true,
            appVersion = "2.5.126",
        ),
        hero = listOf(
            HeroItem(
                ref = "xtv:100",
                title = "Spider-Man: un nuevo día",
                poster = "https://img/pos.jpg",
                year = 2026,
                rating = 7.6,
                kind = MediaKind.MOVIE,
                synopsis = "Peter Parker vuelve…",
                durationMin = 118,
                badge = "TENDENCIA DE HOY",
                openLink = VisionLinks.title("xtv:100"),
                playLink = VisionLinks.watch(key = "xtv:100", code = "100"),
            ),
        ),
        continueWatching = listOf(
            ContinueItem(
                key = "555",
                title = "Ley y orden: UVE · T1 · C3",
                poster = "https://img/serie.jpg",
                progress = 42,
                positionMs = 1_260_000,
                durationMs = 3_000_000,
                episode = "T1 · C3",
                playLink = VisionLinks.watch(
                    key = "555",
                    code = "555",
                    seriesId = "serie-1",
                    seasonIndex = 0,
                    seasonIds = listOf("serie-1"),
                    seasonNames = listOf("T1"),
                    resumeMs = 1_260_000,
                ),
            ),
        ),
        live = listOf(
            LiveItem(
                code = "cyx_AZTECA7",
                name = "Azteca 7",
                logo = "https://img/logo.png",
                group = "México",
                now = "La rosa de Guadalupe",
                next = "Noticiero",
                progress = 31,
                playLink = VisionLinks.live("cyx_AZTECA7", "Azteca 7", "https://img/logo.png"),
            ),
        ),
        rows = listOf(
            FeedRow(
                id = "top10",
                label = "Top 10 en Vision+",
                kind = RowKind.TOP10,
                items = listOf(
                    FeedItem(
                        ref = "xtv:200",
                        title = "Intensa Mente 2",
                        poster = "https://img/i2.jpg",
                        year = 2024,
                        rating = 8.1,
                        kind = MediaKind.MOVIE,
                        badge = "NUEVO",
                        openLink = VisionLinks.title("xtv:200"),
                        playLink = VisionLinks.watch(key = "xtv:200", code = "200"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun everyFieldSurvivesTheRoundTrip() {
        val original = sampleFeed()
        val decoded = HomeFeedCodec.decode(HomeFeedCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun anEmptyFeedIsStillAFeed() {
        val decoded = HomeFeedCodec.decode(HomeFeedCodec.encode(HomeFeed()))
        assertEquals(HomeFeed(), decoded)
        assertTrue(decoded!!.isEmpty)
    }

    @Test
    fun anUnknownSchemaVersionIsRejectedInsteadOfGuessed() {
        val raw = HomeFeedCodec.encode(sampleFeed())
            .replace("\"version\":${HomeContract.VERSION}", "\"version\":${HomeContract.VERSION + 1}")
        assertNull(HomeFeedCodec.decode(raw))
    }

    @Test
    fun missingFieldsFallBackToSafeDefaults() {
        val minimal = """{"meta":{"version":${HomeContract.VERSION}},"rows":[{"id":"r","label":"R","items":[{"title":"Solo título"}]}]}"""
        val feed = HomeFeedCodec.decode(minimal)!!
        assertEquals("Solo título", feed.rows.single().items.single().title)
        assertEquals(RowKind.POSTERS, feed.rows.single().kind)
        assertEquals(0.0, feed.rows.single().items.single().rating, 0.0)
        assertEquals(1, feed.meta.profiles)
    }

    @Test
    fun garbageNeverThrows() {
        assertNull(HomeFeedCodec.decode(""))
        assertNull(HomeFeedCodec.decode(null))
        assertNull(HomeFeedCodec.decode("{"))
        assertNull(HomeFeedCodec.decode("\"feed\""))
        assertNull(HomeFeedCodec.decode("[]"))
    }

    @Test
    fun brokenItemsAreSkippedWithoutLosingTheRest() {
        val raw = """{"meta":{"version":${HomeContract.VERSION}},"hero":[{"ref":"a","title":"A"},"basura",{"ref":"b","title":"B"}]}"""
        val feed = HomeFeedCodec.decode(raw)!!
        assertEquals(listOf("a", "b"), feed.hero.map { it.ref })
    }

    @Test
    fun progressIsClampedToTheBarRange() {
        val raw = """{"meta":{"version":${HomeContract.VERSION}},"live":[{"name":"X","progress":480}]}"""
        assertEquals(100, HomeFeedCodec.decode(raw)!!.live.single().progress)
    }

    @Test
    fun durationLabelReadsLikeATvGuide() {
        assertEquals("", durationLabel(0))
        assertEquals("48 min", durationLabel(48))
        assertEquals("1 h", durationLabel(60))
        assertEquals("1 h 42 min", durationLabel(102))
    }
}
