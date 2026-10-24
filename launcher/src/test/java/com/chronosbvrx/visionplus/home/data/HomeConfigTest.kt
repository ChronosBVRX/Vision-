package com.chronosbvrx.visionplus.home.data

import com.chronosbvrx.visionplus.home.contract.FeedRow
import com.chronosbvrx.visionplus.home.contract.LiveItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La personalización es local y no puede romper el Inicio: defaults iguales a la vista
 * original, JSON tolerante, favoritos y ocultos excluyentes y orden estable.
 */
class HomeConfigTest {

    private fun channel(code: String, name: String = code, mexico: Boolean = false) =
        LiveItem(code = code, name = name, mexico = mexico)

    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label)

    @Test
    fun theDefaultConfigKeepsTheCurrentHome() {
        val config = HomeConfig()
        assertEquals(HomeConfig.DEFAULT_ROW_ORDER, config.rowOrder)
        assertTrue(config.hiddenRows.isEmpty())
        assertTrue(config.channelFavorites.isEmpty())
        assertTrue(config.hiddenChannels.isEmpty())
        assertEquals(ChannelSort.AUTO, config.channelSort)
        assertEquals(DisplayConfig(), config.display)
    }

    @Test
    fun theConfigSurvivesTheJsonRoundTrip() {
        val original = HomeConfig(
            rowOrder = listOf("channels", "continue", "top10"),
            hiddenRows = setOf("apps"),
            channelFavorites = listOf("c5", "tudn"),
            hiddenChannels = setOf("axn"),
            channelSort = ChannelSort.FAVORITES,
            appFavorites = listOf("com.netflix.ninja"),
            hiddenApps = setOf("com.foo"),
            display = DisplayConfig(
                density = LauncherDensity.CINEMA,
                animations = LauncherAnimations.LIGHT,
                clock24h = false,
                showClock = false,
                showEpg = false,
                itemsPerRow = 18,
            ),
        )
        assertEquals(original, HomeConfig.fromJson(original.toJson()))
    }

    @Test
    fun aBrokenJsonFallsBackToTheDefaultInsteadOfBreakingTheHome() {
        assertEquals(HomeConfig(), HomeConfig.fromJson("{no es json"))
        assertEquals(HomeConfig(), HomeConfig.fromJson(null))
        assertEquals(HomeConfig(), HomeConfig.fromJson(""))
    }

    @Test
    fun theOldFavoriteAppsAreMigratedOnce() {
        val config = HomeConfig.fromJson(null, listOf("com.netflix.ninja", "com.disney.disneyplus"))
        assertEquals(listOf("com.netflix.ninja", "com.disney.disneyplus"), config.appFavorites)
    }

    @Test
    fun favoriteAndHiddenAreMutuallyExclusiveForChannels() {
        val fav = HomeConfig().toggleChannelFavorite("canal5")
        assertTrue("canal5" in fav.channelFavorites)
        val hidden = fav.toggleChannelHidden("canal5")
        assertTrue("canal5" in hidden.hiddenChannels)
        assertFalse("canal5" in hidden.channelFavorites)
        val shownAndFav = hidden.toggleChannelFavorite("canal5")
        assertFalse("canal5" in shownAndFav.hiddenChannels)
        assertTrue("canal5" in shownAndFav.channelFavorites)
    }

    @Test
    fun favoriteAndHiddenAreMutuallyExclusiveForApps() {
        val fav = HomeConfig().toggleAppFavorite("com.netflix.ninja")
        val hidden = fav.toggleAppHidden("com.netflix.ninja")
        assertFalse("com.netflix.ninja" in hidden.appFavorites)
        assertTrue("com.netflix.ninja" in hidden.hiddenApps)
        val shown = hidden.toggleAppFavorite("com.netflix.ninja")
        assertFalse("com.netflix.ninja" in shown.hiddenApps)
        assertTrue("com.netflix.ninja" in shown.appFavorites)
    }

    @Test
    fun theChannelsSortRespectsThePreference() {
        val channels = listOf(channel("b", "Beta"), channel("a", "Alfa"), channel("c", "Canal 5"))
        val auto = visibleChannels(channels, HomeConfig())
        assertEquals(listOf("b", "a", "c"), auto.map { it.code })

        val alpha = visibleChannels(channels, HomeConfig(channelSort = ChannelSort.ALPHA))
        assertEquals(listOf("a", "b", "c"), alpha.map { it.code })

        val fav = HomeConfig(
            channelFavorites = listOf("c", "b"),
            channelSort = ChannelSort.FAVORITES,
        )
        assertEquals(listOf("c", "b", "a"), visibleChannels(channels, fav).map { it.code })
    }

    @Test
    fun hiddenChannelsAreGoneButFavoritesKeepTheirManualOrder() {
        val channels = listOf(channel("a"), channel("b"), channel("c"), channel("d"))
        val config = HomeConfig(
            channelFavorites = listOf("d", "b", "a"),
            hiddenChannels = setOf("b"),
        )
        assertEquals(listOf("d", "a"), favoriteChannels(channels, config).map { it.code })
        assertEquals(listOf("a", "c", "d"), visibleChannels(channels, config).map { it.code })
    }

    @Test
    fun movingAFavoriteChangesOnlyThatPosition() {
        val config = HomeConfig(channelFavorites = listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), config.moveChannelFavorite("b", -1).channelFavorites)
        assertEquals(listOf("a", "c", "b"), config.moveChannelFavorite("b", 1).channelFavorites)
        assertEquals(listOf("c", "a", "b"), config.promoteChannelFavorite("c").channelFavorites)
    }

    @Test
    fun appsHideAndKeepTheFavoritesOrder() {
        val apps = listOf(app("com.a", "Alfa"), app("com.b", "Beta"), app("com.c", "Canal"))
        val config = HomeConfig(
            appFavorites = listOf("com.c", "com.a"),
            hiddenApps = setOf("com.b"),
        )
        assertEquals(listOf("Canal", "Alfa"), visibleApps(apps, config).map { it.label })
    }

    @Test
    fun recentAppsBreakTheTieOutsideFavorites() {
        val apps = listOf(app("com.a", "Alfa"), app("com.b", "Beta"), app("com.c", "Canal"))
        val config = HomeConfig(appFavorites = listOf("com.c"))
        val ordered = visibleApps(apps, config, recent = listOf("com.b"))
        assertEquals(listOf("Canal", "Beta", "Alfa"), ordered.map { it.label })
    }

    @Test
    fun rowsHaveAStableOrderAndLabels() {
        val feedRows = listOf(
            FeedRow(id = "top10", label = "Top 10"),
            FeedRow(id = "premieres", label = "Novedades"),
        )
        val config = HomeConfig(rowOrder = listOf("channels", "top10", "apps"))
        assertEquals(listOf("channels", "top10", "apps", "premieres", "continue", "live"), homeRowOrder(config, feedRows))
        val entries = homeRowEntries(config, feedRows)
        assertEquals("Mis canales", entries.first { it.id == "channels" }.label)
        assertEquals("Top 10", entries.first { it.id == "top10" }.label)
        assertEquals("Novedades", entries.first { it.id == "premieres" }.label)
    }

    @Test
    fun theDisplayOptionsAreClamped() {
        assertEquals(8, DisplayConfig(itemsPerRow = 1).normalized().itemsPerRow)
        assertEquals(18, DisplayConfig(itemsPerRow = 99).normalized().itemsPerRow)
        assertEquals(12, DisplayConfig(itemsPerRow = 12).normalized().itemsPerRow)
    }

    @Test
    fun resettingASectionDoesNotTouchTheOthers() {
        val config = HomeConfig(
            rowOrder = listOf("apps"),
            hiddenRows = setOf("top10"),
            channelFavorites = listOf("c5"),
            appFavorites = listOf("com.netflix.ninja"),
            display = DisplayConfig(density = LauncherDensity.CINEMA),
        )
        val channelsReset = config.resetChannels()
        assertTrue(channelsReset.channelFavorites.isEmpty())
        assertEquals(listOf("com.netflix.ninja"), channelsReset.appFavorites)
        assertEquals(LauncherDensity.CINEMA, channelsReset.display.density)

        val all = config.resetAll()
        assertEquals(HomeConfig(), all)
    }

    @Test
    fun aLegacyConfigWithDataRescuesTheEmptyPrimary() {
        val primary = HomeConfig()
        val legacy = HomeConfig(channelFavorites = listOf("canal5"))
        assertEquals(legacy, chooseConfig(primary, legacy))
    }

    @Test
    fun aPrimaryConfigWithDataIsNeverOverwrittenByAnOldAlias() {
        val primary = HomeConfig(channelFavorites = listOf("tudn"))
        val legacy = HomeConfig(channelFavorites = listOf("canal5"))
        assertEquals(primary, chooseConfig(primary, legacy))
    }

    @Test
    fun theLegacyAliasStillRescuesChannelDataWhenThePrimaryOnlyHasApps() {
        val primary = HomeConfig(appFavorites = listOf("com.netflix.ninja"))
        val legacy = HomeConfig(
            appFavorites = listOf("com.netflix.ninja"),
            channelFavorites = listOf("canal5"),
        )
        assertEquals(legacy, chooseConfig(primary, legacy))
    }
}
