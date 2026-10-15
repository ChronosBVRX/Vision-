package com.chronosbvrx.visionplus.data.subs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El almacén de subtítulos frente al proceso `:x4k`: la elección por título vive en un archivo
 * (las SharedPreferences no cruzan procesos) y el SRT cacheado se lee tal cual del disco.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SubsStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun selectionRemembersProgramAndFallsBackToTheSeries() {
        SubsStore.saveSelection(context, "ep1", "serie-1", "es")
        assertEquals("es", SubsStore.selection(context, "ep1", "serie-1"))
        assertEquals("es", SubsStore.selection(context, "ep2", "serie-1"))
        assertEquals("", SubsStore.selection(context, "otro", ""))
    }

    @Test
    fun turningSubtitlesOffOnAnEpisodeIsAuthoritative() {
        SubsStore.saveSelection(context, "ep1", "serie-2", "en")
        SubsStore.saveSelection(context, "ep2", "serie-2", "")
        assertEquals("", SubsStore.selection(context, "ep2", "serie-2"))
        // El siguiente capítulo hereda el «apagados», no el inglés del anterior.
        assertEquals("", SubsStore.selection(context, "ep3", "serie-2"))
        // Y volver a elegir un idioma reenciende el lote.
        SubsStore.saveSelection(context, "ep3", "serie-2", "es")
        assertEquals("es", SubsStore.selection(context, "ep4", "serie-2"))
    }

    @Test
    fun readsCuesFromTheCachedSrtFile() {
        val entry = SubEntry("es", "http://example.invalid/x.srt", "srt", 0)
        val file = SubsStore.srtFile(context, "A8120912", entry)
        file.parentFile?.mkdirs()
        file.writeText("1\r\n00:00:01,000 --> 00:00:02,000\r\nHola, mamá\r\n")
        val cues = SubsStore.readCues(file)
        assertEquals(1, cues.size)
        assertEquals("Hola, mamá", cues.first().text)
        assertEquals(1_000L, cues.first().startMs)
    }

    @Test
    fun ensureWithoutUrlNeverTouchesTheNetwork() {
        assertNull(SubsStore.ensureSrtFile(context, "cid", SubEntry("es", "", "srt", 0)))
    }

    @Test
    fun cachedSrtWinsOverDownload() {
        // Archivo ya presente: ensure lo devuelve sin red (la URL no resuelve en tests).
        val entry = SubEntry("es", "http://example.invalid/x.srt", "srt", 0)
        val file = SubsStore.srtFile(context, "cid2", entry)
        file.parentFile?.mkdirs()
        file.writeText("1\r\n00:00:01,000 --> 00:00:02,000\r\nCacheado\r\n")
        val ensured = SubsStore.ensureSrtFile(context, "cid2", entry)
        assertTrue(ensured != null && ensured.exists())
        assertEquals("Cacheado", SubsStore.readCues(ensured!!).first().text)
    }

    /** El índice tal como lo deja el teléfono (escapes `\/` de org.json) se sirve de la caché. */
    @Test
    fun deviceShapedIndexIsServedFromCache() {
        val cid = "0E59E1FDB47E4D8682F87A5FDE974C6A"
        val listJson = "[{\"language\":\"es\",\"file\":[{\"url\":\"http://vpnwqk.k9ijj2.com/" +
            "public/subs/a3374de5-2a62-4c00-b07f-3eaa1f92f7dc.srt\",\"fileType\":\"srt\",\"md5\":\"\"}]}," +
            "{\"language\":\"en\",\"file\":[{\"url\":\"http://vpnwqk.k9ijj2.com/" +
            "public/subs/a1027abc-361d-4aee-a928-3e1a47410f71.srt\",\"fileType\":\"srt\",\"md5\":\"\"}]}]"
        val root = JSONObject().put(
            cid,
            JSONObject().put("at", System.currentTimeMillis()).put("list", listJson),
        )
        val file = File(File(context.filesDir, "subs"), "index.json")
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
        val list = SubsStore.loadList(context, cid)
        assertEquals(listOf("es", "en"), list.map { it.language })
        assertEquals(
            "http://vpnwqk.k9ijj2.com/public/subs/a3374de5-2a62-4c00-b07f-3eaa1f92f7dc.srt",
            list.first().url,
        )
    }
}
