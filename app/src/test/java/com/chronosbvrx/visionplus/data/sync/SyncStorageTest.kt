package com.chronosbvrx.visionplus.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.UserLibrary
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * El puente entre la biblioteca local y la sincronización: JSON estable de una ficha y la
 * lectura FRESCA del XML (el reproductor marca vistos desde `:x4k`; la copia en memoria del
 * proceso principal puede quedarse vieja).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vision_user", Context.MODE_PRIVATE).edit().clear().commit()
        xmlFile().delete()
    }

    @Test
    fun filmJsonRoundTripKeepsWhatTheCardsNeed() {
        val film = Film("xtv:A", "Moana", 2016, 107, "sinopsis", "p", "xtv:A", "xtv")
        val back = UserLibrary.filmFromJson(UserLibrary.filmToJson(film))
        assertEquals(film.id, back.id)
        assertEquals(film.title, back.title)
        assertEquals(film.year, back.year)
        assertEquals(film.durationMin, back.durationMin)
        assertEquals(film.posterUrl, back.posterUrl)
        assertEquals(film.videoUrl, back.videoUrl)
        assertEquals(film.sourceUrl, back.sourceUrl)
    }

    @Test
    fun replaceFavoritesAndWatchedSurviveTheRoundTrip() {
        UserLibrary.replaceFavorites(context, listOf(Film("a", "A", 0, 0, "", "", "xtv:a", "xtv")))
        assertEquals(listOf("a"), UserLibrary.favorites(context).map { it.id })

        UserLibrary.replaceWatched(context, setOf("s1|1", "s1|2"))
        assertEquals(setOf("s1|1", "s1|2"), UserLibrary.watchedEpisodes(context))
    }

    @Test
    fun watchedFreshReadsTheXmlLeftByThePlayerProcess() {
        xmlFile().parentFile?.mkdirs()
        xmlFile().writeText(
            """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <set name="watched_episodes">
                    <string>s1|1</string>
                    <string>s1|2</string>
                </set>
            </map>
            """.trimIndent(),
        )
        assertEquals(setOf("s1|1", "s1|2"), UserLibrary.watchedEpisodesFresh(context))
    }

    @Test
    fun aMissingOrBrokenXmlFallsBackToTheInMemoryCopy() {
        assertEquals(emptySet<String>(), UserLibrary.watchedEpisodesFresh(context))
        UserLibrary.replaceWatched(context, setOf("s1|3"))
        xmlFile().writeText("<map><set name=\"watched_episodes\">roto")
        assertEquals(setOf("s1|3"), UserLibrary.watchedEpisodesFresh(context))
    }

    private fun xmlFile(): File =
        File(File(context.applicationContext.dataDir, "shared_prefs"), "vision_user.xml")
}
