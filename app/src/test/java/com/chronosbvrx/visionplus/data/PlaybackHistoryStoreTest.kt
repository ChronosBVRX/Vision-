package com.chronosbvrx.visionplus.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El almacén del historial frente al proceso `:x4k`: un solo recuerdo por serie —el último
 * capítulo— y lectura del archivo fresco, porque la copia en memoria del proceso principal
 * puede quedarse vieja (el caso «a veces no se guarda el avance»).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaybackHistoryStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearHistory() {
        PlaybackHistory.clear(context)
    }

    private fun episode(
        key: String,
        title: String,
        updatedAt: Long,
        positionMs: Long = 120_000L,
        durationMs: Long = 600_000L,
    ) = ResumeEntry(
        key = key, title = title, xtvCode = key, seriesId = "serie-1",
        episodeIds = listOf("a", "b"), episodeIndex = 0,
        seasonIds = listOf("t1"), seasonNames = listOf("T1"), seasonIndex = 0,
        positionMs = positionMs, durationMs = durationMs, updatedAt = updatedAt,
    )

    private fun movie(
        key: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
    ) = ResumeEntry(
        key = key, title = title, videoUrl = "", positionMs = positionMs,
        durationMs = durationMs, updatedAt = updatedAt,
    )

    private fun film(id: String) = Film(
        id = id, title = id, year = 2020, durationMin = 10, synopsis = "",
        posterUrl = "", videoUrl = "", sourceUrl = "",
    )

    @Test
    fun savingAnEpisodeReplacesTheOlderOneOfTheSeries() {
        val now = System.currentTimeMillis()
        PlaybackHistory.save(context, episode("e1", "Serie · T1 · C1", now - 1_000L), immediate = true)
        PlaybackHistory.save(context, episode("e2", "Serie · T1 · C2", now), immediate = true)
        assertEquals(listOf("e2"), PlaybackHistory.list(context).map { it.key })
    }

    @Test
    fun finishedMovieStaysInHistoryButNotInContinue() {
        val now = System.currentTimeMillis()
        PlaybackHistory.save(
            context,
            movie("m1", "Película", positionMs = 600_000L, durationMs = 600_000L, updatedAt = now),
            immediate = true,
        )
        val films = listOf(film("m1"))

        assertEquals("el terminado se conserva", listOf("m1"), PlaybackHistory.list(context).map { it.key })
        assertTrue(
            "«Seguir viendo» no ofrece una película terminada",
            PlaybackHistory.continueCards(context, films).isEmpty(),
        )
        val history = PlaybackHistory.historyCards(context, films)
        assertEquals(1, history.size)
        assertTrue("el historial la marca como vista", history[0].completed)
        assertEquals(1f, history[0].progress, 0.001f)
    }

    @Test
    fun finishedEpisodeKeepsOneSeriesEntryAsCompleted() {
        val now = System.currentTimeMillis()
        PlaybackHistory.save(context, episode("e1", "Serie · T1 · C1", now - 1_000L), immediate = true)
        PlaybackHistory.save(
            context,
            episode("e2", "Serie · T1 · C2", now, positionMs = 600_000L),
            immediate = true,
        )
        val films = listOf(film("e2"))

        assertEquals(
            "la serie conserva solo el último capítulo",
            listOf("e2"),
            PlaybackHistory.list(context).map { it.key },
        )
        assertTrue(PlaybackHistory.continueCards(context, films).isEmpty())
        assertTrue(PlaybackHistory.historyCards(context, films).single().completed)
    }

    @Test
    fun listReadsTheHistoryFileNotTheStaleMemoryCopy() {
        val now = System.currentTimeMillis()
        PlaybackHistory.save(context, episode("e1", "Serie · T1 · C1", now - 1_000L), immediate = true)
        // Otra escritura del proceso del reproductor (:x4k) deja el archivo más nuevo que la
        // copia en memoria: la fila «Seguir viendo» y el deep link deben ver el archivo.
        writeHistoryXml("e9", "Serie · T1 · C9", now)
        assertEquals(listOf("e9"), PlaybackHistory.list(context).map { it.key })
        assertNull(PlaybackHistory.get(context, "e1"))
        assertEquals("e9", PlaybackHistory.get(context, "e9")?.key)
    }

    /** El XML tal como lo deja SharedPreferences (texto de la entrada + escapes de `&`). */
    private fun writeHistoryXml(key: String, title: String, updatedAt: Long) {
        val json = JSONArray()
            .put(PlaybackHistory.entryToJson(episode(key, title, updatedAt)))
            .toString()
            .replace("&", "&amp;")
        val file = File(File(context.dataDir, "shared_prefs"), PlaybackHistory.PREFS_XML_FILE)
        file.parentFile?.mkdirs()
        file.writeText(
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" +
                "    <string name=\"entries\">$json</string>\n</map>",
        )
        assertTrue(file.exists())
    }
}
