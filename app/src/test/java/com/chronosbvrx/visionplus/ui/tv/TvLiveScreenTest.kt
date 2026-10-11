package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveStore
import com.chronosbvrx.visionplus.data.MAGIS_PLAYLIST_ID
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.Navigator
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.TvApp
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvLiveScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)
    private lateinit var nav: Navigator

    private fun start(vararg stack: Route): Navigator {
        nav = Navigator(stack.toList())
        val catalog = XuperCatalog(ApplicationProvider.getApplicationContext())
        catalog.seed(emptyList())
        compose.setContent { VisionTheme { TvApp(catalog, nav) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return nav
    }

    private fun focusedCount() = compose.onAllNodes(focused, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertLanded(where: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("$where: nothing focused", focusedCount() >= 1)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    @Test
    fun liveWith1000ChannelsNavigation() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LiveStore(app)
        val channels = (1..1000).map { i ->
            LiveChannel(
                name = when (i % 4) {
                    0 -> "TUDN MX $i HD"
                    1 -> "HBO CINEMA $i USA"
                    2 -> "DISNEY CHANNEL $i ESP"
                    else -> "CNN NOTICIAS $i COL"
                },
                url = "xtvlive:c$i",
                logo = "https://example.com/logo$i.png",
                group = "",
            )
        }
        store.cacheChannels(MAGIS_PLAYLIST_ID, channels)

        start(Route.Home, Route.Live)
        assertLanded("TvLiveScreen")
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        assertLanded("TvLiveScreen category picked")
    }

    @Test
    fun duplicateChannelUrlsDoNotCrashLazyColumn() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LiveStore(app)
        val channels = listOf(
            LiveChannel(name = "Canal A", url = "xtvlive:dup1", logo = null, group = ""),
            LiveChannel(name = "Canal A Copia", url = "xtvlive:dup1", logo = null, group = ""),
            LiveChannel(name = "Canal B", url = "xtvlive:dup2", logo = null, group = ""),
            LiveChannel(name = "Canal B Copia", url = "xtvlive:dup2", logo = null, group = ""),
        )
        store.cacheChannels(MAGIS_PLAYLIST_ID, channels)

        start(Route.Home, Route.Live)
        assertLanded("TvLiveScreen with duplicates")
    }

    @org.junit.After
    fun tearDown() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        LiveStore(app).cacheChannels(MAGIS_PLAYLIST_ID, emptyList())
    }
}
