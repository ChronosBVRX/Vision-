package com.chronosbvrx.visionplus

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardianes de la capa de estado de UX:
 *
 *  - «Seguir viendo»/Historial tienen acciones reales (no solo un póster).
 *  - El siguiente capítulo cruza temporada y el launcher publica el siguiente.
 *  - Las notificaciones abren el contenido que anuncian.
 *  - «Mis series» y los canales recientes no vuelven a ser funciones a medias.
 *
 * Son comprobaciones de cableado: la lógica pura vive en PlaybackHistoryTest,
 * PlaybackHistoryStoreTest y WatchNextPlanTest.
 */
class StateUxWiringTest {

    private fun source(rel: String): String {
        val found = listOf(
            File("src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("app/src/main/java/com/chronosbvrx/visionplus/$rel"),
            File("../app/src/main/java/com/chronosbvrx/visionplus/$rel"),
        ).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }

    @Test
    fun continueAndHistoryExposePlaybackStateActions() {
        val actions = source("ui/components/PosterActions.kt")
        assertTrue("hay menú de estado para una tarjeta", actions.contains("fun rememberPlaybackLongClick"))
        assertTrue("se puede reanudar", actions.contains("fun resume("))
        assertTrue("se puede marcar visto/no visto", actions.contains("fun markWatched("))
        assertTrue("se puede quitar una entrada suelta", actions.contains("PlaybackHistory.delete"))
        assertTrue("se puede saltar al siguiente", actions.contains("nextEpisodeEntry"))
        assertTrue("el terminado manual se conserva", actions.contains("PlaybackHistory.markCompleted"))
    }

    @Test
    fun continueAndHistoryCardsUseTheStateMenu() {
        for (path in listOf(
            "ui/home/HomeScreen.kt",
            "ui/tv/TvHomeScreen.kt",
            "ui/phone/LibraryScreen.kt",
            "ui/tv/TvLibraryScreen.kt",
        )) {
            val text = source(path)
            assertTrue("$path enlaza el menú de estado", text.contains("rememberPlaybackLongClick("))
        }
    }

    @Test
    fun nextEpisodeCrossSeasonIsOneSharedResolver() {
        val history = source("data/PlaybackHistory.kt")
        assertTrue(history.contains("fun nextEpisodeOf"))
        assertTrue(history.contains("crossesSeason"))
        assertTrue(history.contains("fun resumeTargetSeasonIndex"))
        assertTrue("la ficha no puede volver a la temporada terminada", source("ui/title/TitleScreen.kt").contains("resumeTargetSeasonIndex(entry)"))
        assertTrue(source("ui/tv/TvTitleScreen.kt").contains("resumeTargetSeasonIndex(entry)"))
        assertTrue("el launcher también publica el siguiente", source("data/launcher/WatchNextPlan.kt").contains("nextEpisodeEntry(entry)"))
    }

    @Test
    fun notificationsOpenTheContentNotJustHome() {
        val reminders = source("data/notify/ReminderKit.kt")
        assertTrue("el aviso lleva un deep link", reminders.contains("putExtra(\"uri\""))
        assertTrue("el receiver lo abre", reminders.contains("Intent.ACTION_VIEW"))
        assertTrue("una llave distinta por aviso", reminders.contains("val requestCode ="))
        assertTrue("los capítulos nuevos abren la serie", source("data/follow/FollowStore.kt").contains("VisionLinks.title"))
        assertTrue("los partidos abren el evento", source("data/sports/SportsReminder.kt").contains("VisionLinks.sports"))
    }

    @Test
    fun mySeriesAndRecentLiveAreRealScreens() {
        for (path in listOf("ui/phone/LibraryScreen.kt", "ui/tv/TvLibraryScreen.kt")) {
            val text = source(path)
            assertTrue("$path tiene Mis series", text.contains("Mis series"))
            assertTrue("$path usa la lista de seguidas", text.contains("followedFilms"))
            assertTrue("$path permite dejar de seguir", text.contains("FollowStore.unfollow"))
            assertTrue("$path filtra el historial", text.contains("HistoryFilter"))
        }
        val live = source("ui/phone/LiveScreen.kt")
        assertTrue("el filtro de favoritos ya no está muerto", live.contains("favoritesOnly = !favoritesOnly"))
        assertFalse("no queda el chip con onClick vacío", live.contains("CategoryChip(\"Favoritos\", selected = false, onClick = { })"))
        assertTrue("los recientes se muestran", live.contains("LiveHistory.recent"))
        assertTrue("el último canal se recuerda", source("data/xuper/X4KApi.kt").contains("LiveHistory.remember"))
    }

    @Test
    fun profileGateAndPerProfileSearchKeepSharedTvsOrdered() {
        val activity = source("MainActivity.kt")
        assertTrue("la tele abre el selector de perfiles al arrancar", activity.contains("TvProfilesOverlay("))
        assertTrue("el selector lleva el título corto", activity.contains("title = \"Perfiles\""))
        assertTrue("solo interrumpe en TV", activity.contains("savedInstanceState == null && isTv"))
        val search = source("data/search/SearchSuggest.kt")
        assertTrue("los recientes usan el perfil activo", search.contains("ProfileStore.prefsName(context, PREFS)"))
        assertTrue("el perfil limpia los recientes", source("data/profile/ProfileStore.kt").contains("\"vision_search\""))
    }
}
