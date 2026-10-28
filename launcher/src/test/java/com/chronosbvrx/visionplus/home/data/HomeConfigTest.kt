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
        assertEquals(
            listOf("channels", "top10", "apps", "premieres", "continue", "live", "inputs"),
            homeRowOrder(config, feedRows),
        )
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

    @Test
    fun theStartTabAndTheTvInputsSurviveTheJsonRoundTrip() {
        val original = HomeConfig(
            startTab = StartTab.INPUTS,
            hiddenInputs = setOf("hw:1", "hw:2"),
        )
        val restored = HomeConfig.fromJson(original.toJson())
        assertEquals(StartTab.INPUTS, restored.startTab)
        assertEquals(setOf("hw:1", "hw:2"), restored.hiddenInputs)
        assertEquals(original, restored)
    }

    @Test
    fun aConfigFromVersionOneReceivesTheInputsRowInPlace() {
        // Lo que guardó la versión anterior (sin entradas y sin pestaña inicial).
        val old = """{"version":1,"rowOrder":["continue","channels","live","apps","top10"],"hiddenRows":[]}"""
        val migrated = HomeConfig.fromJson(old)
        assertEquals(
            listOf("continue", "channels", "live", "inputs", "apps", "top10"),
            migrated.rowOrder,
        )
        // La preferencia nueva arranca en su default y no pisa lo que ya había.
        assertEquals(StartTab.HOME, migrated.startTab)
        assertTrue(migrated.hiddenInputs.isEmpty())
    }

    @Test
    fun theCurrentVersionNeverReinsertsARowTheUserRemoved() {
        val raw = """{"version":${HomeConfig.VERSION},"rowOrder":["channels","apps"]}"""
        assertEquals(listOf("channels", "apps"), HomeConfig.fromJson(raw).rowOrder)
    }

    @Test
    fun hidingATvInputIsLocalAndReversible() {
        val hidden = HomeConfig().toggleInputHidden("hw:2")
        assertTrue("hw:2" in hidden.hiddenInputs)
        assertTrue(hidden.hasCustomization())
        assertTrue(hidden.toggleInputHidden("hw:2").hiddenInputs.isEmpty())
        assertTrue(hidden.resetInputs().hiddenInputs.isEmpty())
        assertEquals(HomeConfig(), hidden.resetAll())
    }

    @Test
    fun theTabsKeepTheirOrderAndHomeCanNeverBeHidden() {
        val moved = HomeConfig().moveTab("apps", -1)
        assertTrue("apps" in moved.tabOrder)
        assertTrue(moved.tabOrder.indexOf("apps") < HomeConfig.DEFAULT_TAB_ORDER.indexOf("apps"))
        // Inicio es la puerta de vuelta: ni se mueve ni se oculta.
        assertEquals(HomeConfig(), HomeConfig().moveTab("home", 1))
        assertEquals(HomeConfig(), HomeConfig().toggleTab("home"))
        // El resto sí se oculta y se restaura.
        val hidden = HomeConfig().toggleTab("movies")
        assertTrue("movies" in hidden.hiddenTabs)
        assertTrue(hidden.toggleTab("movies").hiddenTabs.isEmpty())
        assertEquals(HomeConfig(), hidden.resetTabs())
    }

    @Test
    fun theTabOrderSurvivesTheJsonRoundTrip() {
        val original = HomeConfig(
            tabOrder = HomeConfig.DEFAULT_TAB_ORDER,
            hiddenTabs = setOf("events", "series"),
        )
        val restored = HomeConfig.fromJson(original.toJson())
        assertEquals(original.tabOrder, restored.tabOrder)
        assertEquals(setOf("events", "series"), restored.hiddenTabs)
        assertEquals(original, restored)
    }

    @Test
    fun thePresetsPutTheRightRowsFirstAndCanBeUndone() {
        val tv = HomeConfig().withPreset(LauncherPreset.TV_FIRST)
        assertEquals(listOf("continue", "channels", "live", "inputs", "apps"), tv.rowOrder.take(5))
        assertEquals("home", tv.tabOrder.first())
        assertTrue("live" !in tv.hiddenRows)

        val cinema = HomeConfig(channelFavorites = listOf("c5")).withPreset(LauncherPreset.CINEMA)
        assertEquals(listOf("continue", "top10", "premieres"), cinema.rowOrder.take(3))
        assertTrue("live" in cinema.hiddenRows)
        assertTrue("inputs" in cinema.hiddenRows)
        assertEquals("movies", cinema.tabOrder.getOrNull(1))
        // La plantilla no toca los favoritos de canales ni la pestaña inicial.
        assertEquals(listOf("c5"), cinema.channelFavorites)
        assertEquals(StartTab.HOME, cinema.startTab)

        val mine = HomeConfig().withPreset(LauncherPreset.JUST_MINE)
        assertEquals(listOf("continue", "channels", "apps"), mine.rowOrder.take(3))
        assertTrue("home-kind-movie" in mine.hiddenRows)
        assertTrue("live" in mine.hiddenTabs)

        val kids = HomeConfig().withPreset(LauncherPreset.KIDS)
        assertEquals(listOf("home-kind-kids", "home-kind-anime"), kids.rowOrder.take(2))
        assertTrue("home-kind-movie" in kids.hiddenRows)
        assertTrue("movies" in kids.hiddenTabs)

        // Deshacer es el reset de siempre.
        assertEquals(HomeConfig(), kids.resetAll())
    }
}
