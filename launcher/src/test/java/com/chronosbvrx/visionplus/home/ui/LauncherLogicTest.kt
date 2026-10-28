package com.chronosbvrx.visionplus.home.ui

import com.chronosbvrx.visionplus.home.contract.FeedItem
import com.chronosbvrx.visionplus.home.contract.FeedRow
import com.chronosbvrx.visionplus.home.contract.HeroItem
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.MediaKind
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.HomeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLogicTest {

    private val netflix = AppEntry("com.netflix.ninja", "Netflix")
    private val youtube = AppEntry("com.google.android.youtube.tv", "YouTube")
    private val disney = AppEntry("com.disney.disneyplus", "Disney+")
    private val prime = AppEntry("com.amazon.amazonvideo.livingroom", "Prime Video")

    @Test
    fun favoritesGoFirstNoMatterHowLongAgoTheyWereUsed() {
        val ordered = orderedApps(
            apps = listOf(netflix, youtube, disney, prime),
            favorites = setOf("com.disney.disneyplus"),
            recent = listOf("com.netflix.ninja"),
        )
        assertEquals("Disney+", ordered.first().label)
    }

    @Test
    fun thenTheRecentlyUsedAndFinallyAlphabetical() {
        val ordered = orderedApps(
            apps = listOf(netflix, youtube, disney, prime),
            favorites = emptySet(),
            recent = listOf("com.amazon.amazonvideo.livingroom", "com.netflix.ninja"),
        )
        assertEquals(listOf("Prime Video", "Netflix", "Disney+", "YouTube"), ordered.map { it.label })
    }

    @Test
    fun aRecentThatIsNoLongerInstalledIsIgnored() {
        val ordered = orderedApps(
            apps = listOf(netflix, disney),
            favorites = emptySet(),
            recent = listOf("com.uninstalled.app"),
        )
        assertEquals(listOf("Disney+", "Netflix"), ordered.map { it.label })
    }

    @Test
    fun moviesAndSeriesTabsDoNotMixCatalogKinds() {
        val feed = HomeFeed(
            rows = listOf(
                FeedRow(
                    id = "top10",
                    label = "Top 10",
                    items = listOf(
                        item("m1", "Película", MediaKind.MOVIE),
                        item("s1", "Serie", MediaKind.SERIES),
                        item("a1", "Anime", MediaKind.ANIME),
                        item("x1", "Infantil", MediaKind.KIDS),
                    ),
                ),
            ),
            hero = listOf(
                HeroItem(ref = "m2", title = "Otra película", kind = MediaKind.MOVIE),
                HeroItem(ref = "m1", title = "Repetida", kind = MediaKind.MOVIE),
            ),
        )
        assertEquals(listOf("m1", "m2"), catalogItems(feed, HomeTab.MOVIES).map { it.ref })
        assertEquals(listOf("s1", "a1", "x1"), catalogItems(feed, HomeTab.SERIES).map { it.ref })
    }

    @Test
    fun anEmptyFeedNeverCrashesATab() {
        assertEquals(emptyList<FeedItem>(), catalogItems(null, HomeTab.MOVIES))
        assertEquals(emptyList<FeedItem>(), catalogItems(HomeFeed(), HomeTab.SERIES))
    }

    @Test
    fun theClockSpeaksMexicoCityEvenOnAnotherTimeZone() {
        // 1970-01-01T00:00:00Z = 18:00 del 31-dic-1969 en el centro de México (UTC-6).
        assertEquals("18:00", clockText(0L))
        assertEquals("6:00 PM", clockText(0L, clock24h = false))
    }

    @Test
    fun theClockWakesExactlyAtTheNextMinute() {
        assertEquals(30_000L, msToNextMinute(30_000L))
        assertEquals(250L, msToNextMinute(59_999L))
        assertEquals(60_000L, msToNextMinute(0L))
    }

    @Test
    fun everyTabHasAStableKeyForThePreference() {
        assertEquals(HomeTab.HOME, HomeTab.of("home"))
        assertEquals(HomeTab.APPS, HomeTab.of("apps"))
        assertEquals(HomeTab.EVENTS, HomeTab.of("events"))
        assertEquals(HomeTab.HOME, HomeTab.of("basura"))
        assertEquals(HomeTab.HOME, HomeTab.of(null))
    }

    @Test
    fun theTabsKeepAStableOrderAndSportsStaysAfterLiveTv() {
        val keys = HomeTab.entries.map { it.key }
        assertEquals(listOf("home", "live", "inputs", "events", "movies", "series", "apps"), keys)
        assertTrue(keys.indexOf("events") > keys.indexOf("live"))
        assertTrue(keys.indexOf("events") < keys.indexOf("movies"))
    }

    @Test
    fun theTabBarFollowsTheConfiguredOrderAndHidesWhatTheUserHid() {
        val config = HomeConfig(
            tabOrder = listOf("home", "apps", "movies", "series", "live", "events", "inputs"),
            hiddenTabs = setOf("events"),
        )
        assertEquals(
            listOf("home", "apps", "movies", "series", "live", "inputs"),
            visibleTabs(config, inputsAvailable = true).map { it.key },
        )
        // Un equipo sin sintonizador ni HDMI no enseña «Entradas» aunque esté en el orden.
        assertTrue(visibleTabs(config, inputsAvailable = false).none { it == HomeTab.INPUTS })
        // Inicio siempre va primero y visible, aunque el orden guardado diga otra cosa.
        val weird = HomeConfig(tabOrder = listOf("apps", "home", "live"), hiddenTabs = setOf("home"))
        val tabs = visibleTabs(weird, inputsAvailable = true)
        assertEquals(HomeTab.HOME, tabs.first())
        assertEquals(1, tabs.count { it == HomeTab.HOME })
    }

    private fun item(ref: String, title: String, kind: String) = FeedItem(
        ref = ref,
        title = title,
        poster = "https://img/$ref.jpg",
        kind = kind,
        openLink = "visionplus://title/$ref",
    )
}
