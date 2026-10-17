package com.chronosbvrx.visionplus.ui.remote

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El buscador del control remoto: solo ofrece lo que de verdad se puede abrir en la tele
 * (una referencia reproducible) y el filtro de canales no se choca con acentos ni mayúsculas.
 */
class RemoteCastTest {

    private fun film(id: String, title: String, videoUrl: String, year: Int = 2024, kind: String = "movie") =
        Film(
            id = id,
            title = title,
            year = year,
            durationMin = 100,
            synopsis = "",
            posterUrl = "http://art/$id.jpg",
            videoUrl = videoUrl,
            sourceUrl = "xtv",
            kind = kind,
        )

    @Test
    fun onlyReproducibleRefsAreCastable() {
        assertTrue(castableRef("xtv:ABC"))
        assertTrue(castableRef("xtvlive:7"))
        assertTrue(castableRef("https://cdn.example/v.mp4"))
        assertTrue(castableRef("http://cdn.example/v.mp4"))
        assertFalse(castableRef(""))
        assertFalse(castableRef("xtv:"))
        assertFalse(castableRef("xtvlive:"))
        assertFalse(castableRef("magis:ABC"))
        assertFalse(castableRef("ftp://x"))
    }

    @Test
    fun shortQueryReturnsNothingSoTheListStaysQuiet() {
        val films = listOf(film("1", "Moana", "xtv:1"))
        assertTrue(buildCastResults(films, emptyList(), "").isEmpty())
        assertTrue(buildCastResults(films, emptyList(), "m").isEmpty())
    }

    @Test
    fun vodResultsComeFirstWithPosterAndKindLabel() {
        val films = listOf(film("1", "Moana", "xtv:1", kind = "movie"))
        val channels = listOf(LiveChannel(name = "Moana TV", url = "xtvlive:9", logo = "http://l.jpg"))
        val results = buildCastResults(films, channels, "moana")
        assertEquals(2, results.size)
        val vod = results[0] as CastItem.Vod
        assertEquals("Moana", vod.title)
        assertEquals("xtv:1", vod.ref)
        assertEquals("http://art/1.jpg", vod.art)
        assertTrue("la película se rotula", vod.subtitle.contains("Película"))
        val ch = results[1] as CastItem.Channel
        assertEquals("Moana TV", ch.title)
        assertEquals("xtvlive:9", ch.ref)
    }

    @Test
    fun titlesWeCannotOpenAreDropped() {
        val films = listOf(
            film("1", "Moana", "magis:1"),
            film("2", "Moana 2", "xtv:2"),
        )
        val results = buildCastResults(films, emptyList(), "moana")
        assertEquals(1, results.size)
        assertEquals("Moana 2", results[0].title)
    }

    @Test
    fun repeatedReferencesAppearOnce() {
        val films = listOf(
            film("1", "Moana", "xtv:1"),
            film("2", "Moana", "xtv:1"),
        )
        assertEquals(1, buildCastResults(films, emptyList(), "moana").size)
    }

    @Test
    fun channelSearchIgnoresAccentsAndCase() {
        val channels = listOf(
            LiveChannel(name = "México TV", url = "xtvlive:1"),
            LiveChannel(name = "Otra", url = "xtvlive:2"),
        )
        val results = buildCastResults(emptyList(), channels, "MEXICO")
        assertEquals(1, results.size)
        assertEquals("México TV", results[0].title)
    }

    @Test
    fun foldForSearchStripsAccents() {
        assertEquals("mexico", foldForSearch("México"))
        assertEquals("espanol", foldForSearch("Español"))
        assertEquals("accion", foldForSearch("Acción"))
    }
}
