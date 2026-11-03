package com.chronosbvrx.visionplus.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OK largo en la app principal: una tarjeta abre su menú contextual (ficha, favoritos, «Ver
 * más tarde») sin abrir además la ficha al soltar, aunque la persona siga apretando mientras
 * lee las opciones. Es la misma lección del launcher: el candado no puede vencer por tiempo.
 */
class PosterActionsTest {

    @Test
    fun theReleaseOfALongPressIsSwallowedEvenIfItTakesLong() {
        val guard = PressReleaseGuard()
        guard.expectRelease()
        repeat(60) { guard.noteKeyDown(repeatCount = it + 1) }
        assertTrue("el KeyUp tardío no activa la primera acción", guard.consumeRelease())
        assertTrue("los duplicados tampoco", guard.consumeRelease())
    }

    @Test
    fun aNewPressRestoresTheNormalClick() {
        val guard = PressReleaseGuard()
        guard.expectRelease()
        assertTrue(guard.consumeRelease())
        guard.noteKeyDown(repeatCount = 0)
        assertFalse("un OK nuevo y deliberado ya hace click", guard.consumeRelease())
    }

    @Test
    fun aNewPressWorksEvenIfTheOpeningReleaseWasLost() {
        val guard = PressReleaseGuard()
        guard.expectRelease()
        guard.noteKeyDown(repeatCount = 0)
        assertFalse("el primer OK dentro del menú funciona aunque el KeyUp se haya perdido", guard.consumeRelease())
    }

    @Test
    fun theGuardDoesNothingWhenNoMenuWasOpened() {
        val guard = PressReleaseGuard()
        assertFalse(guard.consumeRelease())
        guard.noteKeyDown(repeatCount = 0)
        assertFalse(guard.consumeRelease())
    }

    @Test
    fun thePosterCardsOfferTheContextMenu() {
        val tv = source("ui/tv/TvComponents.kt")
        assertTrue("la tarjeta de TV acepta OK largo", tv.contains("onLongClick: (() -> Unit)? = null"))
        assertTrue("y consume la pulsación completa", tv.contains("posterLongPressKeys(onClick = onClick, onLongClick = onLongClick)"))

        val mobile = source("ui/components/Components.kt")
        assertTrue("la tarjeta móvil acepta pulsación larga", mobile.contains("onLongClick: (() -> Unit)? = null"))
        assertTrue("y la usa con el dedo", mobile.contains("combinedClickable(onClick = onClick, onLongClick = onLongClick)"))
    }

    @Test
    fun theMenuOffersFavoritesAndWatchLater() {
        val dialog = source("ui/components/PosterActions.kt")
        assertTrue("favoritos", dialog.contains("UserLibrary.toggleFavorite"))
        assertTrue("ver más tarde", dialog.contains("UserLibrary.toggleWatchLater"))
        assertTrue("abre la ficha", dialog.contains("onOpenFilm"))
        assertTrue("se cierra con Atrás", dialog.contains("dismissOnBack(onDismiss)"))
        assertTrue("abre con foco en la primera acción", dialog.contains("focus.requestFocus()"))
    }

    @Test
    fun theMobileLiveChannelsAlsoUseLongPress() {
        val live = source("ui/phone/LiveScreen.kt")
        assertTrue("mantener pulsado un canal lo marca favorito", live.contains("combinedClickable("))
        assertTrue(live.contains("LiveFavorites.toggle(channel.url)"))
    }

    @Test
    fun everyPosterRowIsWiredToTheMenu() {
        listOf(
            "ui/home/HomeScreen.kt",
            "ui/phone/SectionScreen.kt",
            "ui/phone/LibraryScreen.kt",
            "ui/phone/CategoriesScreen.kt",
            "ui/phone/SearchScreen.kt",
            "ui/title/TitleScreen.kt",
            "ui/tv/TvHomeScreen.kt",
            "ui/tv/TvCategoryScreen.kt",
            "ui/tv/TvLibraryScreen.kt",
            "ui/tv/TvDownloadsScreen.kt",
            "ui/tv/TvTitleScreen.kt",
        ).forEach { path ->
            assertTrue("$path ofrece el menú con OK largo", source(path).contains("rememberPosterLongClick("))
        }

        val activity = source("MainActivity.kt")
        assertTrue("el menú vive una sola vez en la raíz", activity.contains("LocalPosterActions provides posterActions"))
        assertTrue("y abre la ficha desde ahí", activity.contains("PosterActionsDialog("))
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
