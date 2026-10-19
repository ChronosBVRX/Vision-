package com.chronosbvrx.visionplus

import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.PLUGIN_PACKAGE
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.akaLine
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.normalizeDisplayName
import com.chronosbvrx.visionplus.data.searchFilms
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.vodLabel
import com.chronosbvrx.visionplus.data.xuperRows
import com.chronosbvrx.visionplus.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun film(id: String, title: String) = Film(
        id = id,
        title = title,
        year = 1920,
        durationMin = 51,
        synopsis = "Expresionismo alemán",
        posterUrl = "p",
        videoUrl = "https://example.com/v.m3u8",
        sourceUrl = PLUGIN_PACKAGE,
    )

    private fun channel(name: String, group: String) = LiveChannel(
        name = name,
        url = "https://example.com/$name.m3u8",
        logo = "https://logo.example/$name.png",
        group = group,
    )

    @Test
    fun vodOnlyFromTheSourceGroups() {
        val channels = listOf(
            channel("Una Película HD", "Películas"),
            channel("Cine en Casa", "Películas"),
            channel("Mi Serie HD", "Series"),
            channel("Anime Clásico", "Anime"),
            channel("Canal Uno", "En vivo"),
            channel("Sin grupo", ""),
        )
        assertEquals("Películas", vodLabel("PELÍCULAS 1080p"))
        assertEquals("Series", vodLabel("novela turca"))
        assertEquals("Anime", vodLabel("anime en latino"))
        assertNull(vodLabel("Noticias"))
        assertNull(vodLabel("Sin categoría"))

        val rows = xuperRows(channels)
        assertEquals(listOf("peliculas", "series", "anime"), rows.map { it.id })
        assertEquals(2, rows.first().films.size)
        assertTrue(rows.flatMap { it.films }.all { it.sourceUrl == PLUGIN_PACKAGE })
        assertTrue(allFilms(rows).none { it.title == "Canal Uno" })
    }

    @Test
    fun vodFilmsReuseTheChannelsStreamAndLogo() {
        val rows = xuperRows(listOf(channel("Una Película HD", "Películas")))
        val film = rows.single().films.single()
        assertEquals("Una Película HD", film.title)
        assertEquals("https://logo.example/Una Película HD.png", film.posterUrl)
        assertEquals("https://example.com/Una Película HD.m3u8", film.videoUrl)
        assertEquals("peliculas-una_pelicula_hd", film.id)
    }

    @Test
    fun duplicatedEntriesShowOnce() {
        val rows = xuperRows(listOf(channel("Una Película HD", "Películas"), channel("Una Película HD", "Películas")))
        assertEquals(1, rows.single().films.size)
        val rows2 = listOf(CatalogRow("r", "R", listOf(film("f", "A"), film("f", "A"))))
        assertEquals(listOf("f"), allFilms(rows2).map { it.id })
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        val rows = listOf(CatalogRow("a", "A", listOf(film("f1", "El gabinete del doctor Caligari"))))
        assertEquals(listOf("f1"), searchFilms(rows, "ALEMAN caligari").map { it.id })
        assertEquals(1, searchFilms(rows, "  ").size)
        assertTrue(searchFilms(rows, "zzz").isEmpty())
    }

    @Test
    fun metaLineJoinsYearAndDuration() {
        assertEquals("1920 · 51 min", film("f", "A").metaLine())
        val film2 = film("f", "A").copy(year = 1940, durationMin = 92)
        assertEquals("1940 · 1 h 32 min", film2.metaLine())
    }

    @Test
    fun parsesTheRemoteIndex() {
        val json = """{"version": 7, "upToDate": false, "categories": [
            {"id":"estrenos","title":"Estrenos","kind":"special","total":1,"films":[
              {"id":"xtv:1","title":"Una","year":2026,"durationMin":90,"synopsis":"s",
               "posterUrl":"p","videoUrl":"xtv:1","sourceUrl":"xtv"}
            ]},
            {"id":"genero-accion","title":"Acción","kind":"genre","total":0,"films":[]}
        ]}"""
        val payload = RemoteIndexSource.parseIndex(json)
        assertEquals(7, payload.version)
        assertEquals(listOf("estrenos", "genero-accion"), payload.rows.map { it.id })
        assertEquals("xtv:1", payload.rows.first().films.single().videoUrl)
        assertEquals(2026, payload.rows.first().films.single().year)

        val upToDate = RemoteIndexSource.parseIndex("""{"version":7,"upToDate":true,"categories":[]}""")
        assertTrue(upToDate.upToDate)
        assertTrue(upToDate.rows.isEmpty())
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }

    @Test
    fun displayNamesDropUnderscoresAndCollapseSpaces() {
        // El portal usa guiones bajos como separador ("Rebbeca_ …", "SAC_2045"): fuera.
        assertEquals("Rebbeca Un documental con Becky G", normalizeDisplayName("Rebbeca_ Un documental con Becky G"))
        assertEquals("Ghost in the Shell SAC 2045", normalizeDisplayName("Ghost in the Shell SAC_2045"))
        assertEquals("A B", normalizeDisplayName("  A__B  "))
        assertEquals("", normalizeDisplayName("_"))
        // Sin guiones bajos el texto queda intacto (el caso común no cambia).
        assertEquals("Spider-Man 2", normalizeDisplayName(" Spider-Man 2 "))
        assertEquals("", normalizeDisplayName("   "))
        // El sufijo de doblaje del portal no es parte del nombre que ve la persona.
        assertEquals("Los Simpson", normalizeDisplayName("Los Simpson - Español Latino"))
        assertEquals("Los Simpson", normalizeDisplayName("Los Simpson (Español Latino)"))
        assertEquals("Una película", normalizeDisplayName("Una película - Castellano"))
    }

    @Test
    fun akaLineShowsTheOriginalNameOnlyWhenItDiffers() {
        // «vaiana» → Moana: el buscador del servidor encuentra por el nombre regional y
        // manda originalTitle; sin la línea el resultado parecía de otro título.
        val film = film("xtv:1", "Moana").copy(originalTitle = "Moana")
        assertEquals("", film.akaLine())
        assertEquals("También: Finding Nemo", film("xtv:2", "Buscando a Nemo").copy(originalTitle = "Finding Nemo").akaLine())
        assertEquals("También: Daddy's Home", film("xtv:3", "Guerra de Papás").copy(originalTitle = "Daddy's Home").akaLine())
        // Los guiones bajos del portal no se filtran a la línea.
        assertEquals("También: Rápidos y furiosos", film("xtv:4", "Rápidos y furiosos_").copy(originalTitle = "Rápidos y furiosos").akaLine())
        // Sin dato (payload viejo) no inventa nada.
        assertEquals("", film("xtv:5", "Guerra de Papás").akaLine())
    }
}
