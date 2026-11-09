package com.chronosbvrx.visionplus.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * «Visto» de capítulos: el progreso real manda. Un capítulo abierto y abandonado a los
 * 3 minutos no puede quedar como visto; al 85 % sí, aunque falten minutos de créditos.
 * El OK largo del usuario siempre puede corregirlo.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WatchedEpisodesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        UserLibrary.clearAllEpisodeWatchState(context)
    }

    @After
    fun tearDown() {
        UserLibrary.clearAllEpisodeWatchState(context)
    }

    @Test
    fun progressMarksWatchedOnlyFromEightyFivePercent() {
        assertFalse(
            UserLibrary.markEpisodeProgressWatched(context, "serie-a", 1, positionMs = 3 * 60_000, durationMs = 42 * 60_000),
        )
        assertFalse(UserLibrary.isEpisodeWatched(context, "serie-a", 1, progress = 3f / 42f))

        assertTrue(
            UserLibrary.markEpisodeProgressWatched(context, "serie-a", 1, positionMs = 36 * 60_000, durationMs = 42 * 60_000),
        )
        assertTrue(UserLibrary.isEpisodeWatched(context, "serie-a", 1, progress = 36f / 42f))
        // Idempotente: el reproductor lo consulta cada 500 ms.
        assertFalse(
            UserLibrary.markEpisodeProgressWatched(context, "serie-a", 1, positionMs = 36 * 60_000, durationMs = 42 * 60_000),
        )
    }

    @Test
    fun aLegacyAutoMarkDoesNotHideALowProgressEpisode() {
        // Simula una marca vieja de la app (antes se marcaba al abrir el capítulo).
        UserLibrary.replaceWatched(context, setOf("serie-a|1"))
        assertFalse(
            "con progreso conocido por debajo del 85 % no cuenta",
            UserLibrary.isEpisodeWatched(context, "serie-a", 1, progress = 0.12f),
        )
        assertTrue(
            "al llegar al umbral sí cuenta",
            UserLibrary.isEpisodeWatched(context, "serie-a", 1, progress = 0.86f),
        )
        // Sin historial conocido se conserva la marca vieja (no se borra nada a ciegas).
        assertTrue(UserLibrary.isEpisodeWatched(context, "serie-a", 1))
    }

    @Test
    fun longPressCanMarkWithoutAndWithProgress() {
        // Marcar a mano aunque el historial esté a medias.
        UserLibrary.markEpisodeWatched(context, "serie-a", 2, watched = true)
        assertTrue(UserLibrary.isEpisodeWatched(context, "serie-a", 2, progress = 0.10f))

        // «No visto» explícito manda aunque el progreso haya llegado al final.
        UserLibrary.markEpisodeWatched(context, "serie-a", 2, watched = false)
        assertFalse(UserLibrary.isEpisodeWatched(context, "serie-a", 2, progress = 0.99f))

        // Volver a marcarlo a mano limpia el «no visto».
        UserLibrary.markEpisodeWatched(context, "serie-a", 2, watched = true)
        assertTrue(UserLibrary.isEpisodeWatched(context, "serie-a", 2, progress = 0.10f))
    }

    @Test
    fun theSeasonStateUsesTheHistoryAndManualOverrides() {
        val history = listOf(
            com.chronosbvrx.visionplus.data.ResumeEntry(
                key = "cap-1",
                title = "Serie · C1",
                positionMs = 38 * 60_000,
                durationMs = 42 * 60_000,
            ),
            com.chronosbvrx.visionplus.data.ResumeEntry(
                key = "cap-2",
                title = "Serie · C2",
                positionMs = 5 * 60_000,
                durationMs = 42 * 60_000,
            ),
        )
        // Una marca automática vieja del capítulo 2 no debe tapar el 12 % real.
        UserLibrary.replaceWatched(context, setOf("serie-a|2"))
        val states = UserLibrary.episodeWatchStates(
            context,
            "serie-a",
            listOf("cap-1", "cap-2", "cap-3"),
            history,
        )
        assertEquals(listOf(true, false, false), states)

        UserLibrary.markEpisodeWatched(context, "serie-a", 3, watched = true)
        assertEquals(
            listOf(true, false, true),
            UserLibrary.episodeWatchStates(context, "serie-a", listOf("cap-1", "cap-2", "cap-3"), history),
        )
    }

    @Test
    fun neitherTheSheetNorThePlayerMarksEpisodesJustBecauseTheyWereOpened() {
        val mobile = java.io.File("src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt").readText()
        val tv = java.io.File("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt").readText()
        val player = java.io.File("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt").readText()
        listOf("móvil" to mobile, "tele" to tv).forEach { (label, text) ->
            val block = text.substringAfter("onSelectEpisode = {", "").substringBefore("onPlay(", "")
            assertFalse("$label marca el capítulo al abrirlo", block.contains("markEpisodeWatched"))
        }
        val clickBlock = player.substringAfter("setOnItemClickListener", "").substringBefore("setOnItemLongClickListener", "")
        assertFalse("el selector del reproductor marca al abrir", clickBlock.contains("markEpisodeWatched"))
        assertTrue("falta el marcado automático por porcentaje", player.contains("markEpisodeProgressWatched("))
        assertTrue("falta el estado progresivo en la ficha", mobile.contains("episodeWatchStates("))
        assertTrue("falta el estado progresivo en la tele", tv.contains("episodeWatchStates("))
        assertTrue("falta el OK largo en la ficha móvil", mobile.contains("combinedClickable("))
        assertTrue("falta el OK largo en la ficha de tele", tv.contains("onLongClick = { onToggleWatched(index) }"))
    }
}
