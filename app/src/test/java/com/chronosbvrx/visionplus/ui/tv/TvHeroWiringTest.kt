package com.chronosbvrx.visionplus.ui.tv

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vision TV usa la misma foto diaria del VPS y la mezcla con GeeSports. El billboard
 * rota solo cuando el usuario no está recorriendo las filas y el partido en vivo tiene
 * sus propios botones.
 */
class TvHeroWiringTest {

    @Test
    fun visionTvLoadsTheDailyBannersAndTheSportsAgenda() {
        val screen = source("ui/tv/TvHomeScreen.kt")
        assertTrue(screen.contains("BannerSource.cached(context)"))
        assertTrue(screen.contains("BannerSource.load(context)"))
        assertTrue(screen.contains("GeeSportsSource.cachedEvents(context)"))
        assertTrue(screen.contains("GeeSportsSource.refreshEvents(context)"))
        assertTrue(screen.contains("buildTvHeroItems("))
        assertTrue(screen.contains("heroIndex"))
        assertTrue(screen.contains("heroItems.size <= 1"))
    }

    @Test
    fun theLiveMatchChangesTheBillboardAndOpensTheAgenda() {
        val screen = source("ui/tv/TvHomeScreen.kt")
        assertTrue(screen.contains("TvSportsScoreboard("))
        assertTrue(screen.contains("TvSportsHeroBackdrop("))
        assertTrue(screen.contains("Ver en vivo"))
        assertTrue(screen.contains("Agenda"))
        assertTrue(screen.contains("DeportesReturn.pickingId = event.id"))
        assertTrue(screen.contains("onGoRoute(Route.Sports)"))

        val hero = source("ui/tv/TvHero.kt")
        assertTrue(hero.contains("buildTvHeroItems("))
        assertTrue(hero.contains("pickTvSports("))
        assertTrue(hero.contains("geeStartMs("))
        assertTrue(hero.contains("DEPORTES · EN VIVO"))
        assertTrue(hero.contains("EN VIVO"))
    }

    @Test
    fun theBannerClientUsesTheVpsAndKeepsTheFallback() {
        val source = source("data/banners/BannerSource.kt")
        assertTrue(source.contains("/v1/banners"))
        assertTrue(source.contains("X-Api-Key"))
        assertTrue(source.contains("vision_home_banners.json"))
        assertTrue(source.contains("parseHomeBanners("))
        assertTrue(source.contains("readCache(app)"))
        assertTrue(source.contains("parseBannerExpiry"))
        assertTrue(source.contains("Mutex()"))
        assertTrue(source.contains("FAIL_BACKOFF_MS"))
        assertTrue(source.contains("nextRefreshAt"))
    }

    @Test
    fun theHeroReusesTheArtworkByItsStablePathAndPreparesTheNextSlide() {
        val source = source("data/banners/BannerSource.kt")
        assertTrue(source.contains("bannerArtCacheKey"))
        val screen = source("ui/tv/TvHomeScreen.kt")
        assertTrue(screen.contains("bannerArtCacheKey"))
        assertTrue(screen.contains("diskCacheKey"))
        assertTrue(screen.contains("heroImageRequest("))
        assertTrue(screen.contains("coil.Coil.imageLoader(context).enqueue"))
        assertTrue(screen.contains("LaunchedEffect(heroItems, heroIndex, screenPx)"))
    }

    @Test
    fun returningToTheHeroAnchorsTheFullBanner() {
        val screen = source("ui/tv/TvHomeScreen.kt")
        assertTrue(screen.contains("heroActionTick"))
        assertTrue(screen.contains("rowsListState.scrollToItem(0)"))
        assertTrue(screen.contains("onHeroActionFocus"))
        assertTrue(screen.contains("onFocusChanged = { focused -> if (focused) onHeroActionFocus() }"))
        val widgets = source("ui/tv/TvWidgets.kt")
        assertTrue(widgets.contains("onFocusChanged: (Boolean) -> Unit"))
        assertTrue(widgets.contains("LaunchedEffect(focused)"))
    }

    private fun source(rel: String): String {
        val found = listOf(
            File("src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("app/src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("../app/src/main/java/com/chronosbvrx/visionplus/$rel"),
        ).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
