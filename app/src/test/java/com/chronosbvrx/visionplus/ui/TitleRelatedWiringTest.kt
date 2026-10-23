package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cableado que no se puede componer en la suite:
 * - la ficha (móvil y tele) carga «Relacionadas» y abre la ficha de la elegida;
 * - la sinopsis se queda con la del índice (TMDB es-MX) y no salta a la del portal;
 * - al terminar una película el reproductor nativo abre el modal de sugerencias;
 * - el selector de temporadas/capítulos es compacto en las dos pantallas.
 */
class TitleRelatedWiringTest {

    @Test
    fun theMobileTitleLoadsAndPaintsRelatedTitles() {
        val title = source("ui/title/TitleScreen.kt")
        assertTrue("carga relacionadas", title.contains("Related.forFilm("))
        assertTrue("pinta la hilera", title.contains("RelatedSection(related, onOpenFilm)"))
        assertTrue("abre la ficha elegida", title.contains("onOpenFilm: (Film) -> Unit"))
    }

    @Test
    fun theTvTitleLoadsAndPaintsRelatedTitles() {
        val tv = source("ui/tv/TvTitleScreen.kt")
        assertTrue("carga relacionadas", tv.contains("Related.forFilm("))
        assertTrue("pinta la hilera", tv.contains("TvRelatedSection(related, onOpenFilm)"))
        assertTrue("abre la ficha elegida", tv.contains("onOpenFilm: (Film) -> Unit"))
        assertTrue("el contenido ahora se puede desplazar", tv.contains("heightIn(min = minHeight)"))
    }

    @Test
    fun neitherTitleOverwritesTheIndexSynopsisWithThePortalOne() {
        val mobile = source("ui/title/TitleScreen.kt")
        val tv = source("ui/tv/TvTitleScreen.kt")
        for (screen in listOf(mobile, tv)) {
            assertTrue("usa la política única", screen.contains("resolveSynopsis("))
            assertFalse(
                "ya no asigna la descripción del portal a ciegas",
                screen.contains("item.optString(\"description\").takeIf"),
            )
        }
    }

    @Test
    fun theNativePlayerShowsSuggestionsWhenAMovieEnds() {
        val player = source("ui/player/X4KPlayerAty.kt")
        assertTrue("dispara el modal al terminar", player.contains("showMovieSuggestions()"))
        assertTrue("solo para películas sueltas", player.contains("if (curSeries.isBlank()) showMovieSuggestions()"))
        assertTrue("pide las relacionadas", player.contains("Related.forFilm("))
        assertTrue("el modal entra en el bloqueo de foco", player.contains("|| suggestVisible"))
        assertTrue("se cierra con Atrás", player.contains("if (suggestVisible) {"))
    }

    @Test
    fun navigationOpensTheRelatedTitle() {
        val phone = source("ui/PhoneApp.kt")
        val tv = source("ui/TvApp.kt")
        assertTrue("móvil", phone.contains("onOpenFilm = { nav.push(Route.Title(it)) }"))
        assertTrue("tele", tv.contains("onOpenFilm = { nav.push(Route.Title(it)) }"))
    }

    @Test
    fun theSeriesSelectorWasCompacted() {
        val mobile = source("ui/title/TitleScreen.kt")
        assertTrue("temporada compacta", mobile.contains("padding(horizontal = 10.dp, vertical = 5.dp)"))
        assertTrue("miniatura compacta", mobile.contains(".width(40.dp)"))
        val tv = source("ui/tv/TvTitleScreen.kt")
        assertTrue("tarjeta de capítulo compacta", tv.contains("Column(Modifier.width(168.dp))"))
        assertTrue("chip de temporada compacto", tv.contains("padding(horizontal = 11.dp, vertical = 5.dp)"))
    }

    private fun source(rel: String): String {
        val path = "src/main/java/com/chronosbvrx/visionplus/$rel"
        val found = listOf(File(path), File("app/$path"), File("../app/$path")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
