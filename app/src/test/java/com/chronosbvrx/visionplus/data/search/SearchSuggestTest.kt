package com.chronosbvrx.visionplus.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Autocompletar del buscador (2026-10-24): títulos completos a partir de lo escrito para no
 * teclear toda la palabra, filtros Todo/Películas/Series y recientes locales.
 */
class SearchSuggestTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/chronosbvrx/visionplus/$path")
        assertTrue("falta $path", file.exists())
        return file.readText()
    }

    @Test
    fun completesWithWholeTitlesThatStartWithWhatWasTyped() {
        val titles = listOf("Guerra de Papás", "Guerra Mundial Z", "La Guerra de los Mundos", "Nemo")
        val out = suggestTitles("guerra", titles)
        // Primero los que empiezan con lo escrito, luego los que lo contienen.
        assertEquals(listOf("Guerra de Papás", "Guerra Mundial Z", "La Guerra de los Mundos"), out)
    }

    @Test
    fun theCompletionIgnoresAccentsAndCase() {
        val out = suggestTitles("PAPAS", listOf("Guerra de Papás"))
        assertEquals(listOf("Guerra de Papás"), out)
        // Y al revés: escribir sin acento encuentra el título con acento.
        assertEquals(listOf("Guerra de Papás"), suggestTitles("papa", listOf("Guerra de Papás")))
    }

    @Test
    fun itNeverRepeatsTheExactQueryNorDuplicates() {
        val out = suggestTitles("nemo", listOf("Nemo", "Nemo", "Buscando a Nemo", "nemo"))
        // «Nemo» es exactamente lo escrito: no aporta como sugerencia.
        assertEquals(listOf("Buscando a Nemo"), out)
    }

    @Test
    fun aWordStartBeatsAPlainContains() {
        val out = suggestTitles("casa", listOf("La casa de papel", "Casadentro", "Mi casa"), limit = 3)
        // Empieza (Casadentro), luego palabra que empieza (La casa…), luego contiene (Mi casa).
        assertEquals(listOf("Casadentro", "La casa de papel", "Mi casa"), out)
    }

    @Test
    fun itRespectsTheLimitAndBlankQueries() {
        val titles = (1..20).map { "Saga $it" }
        assertEquals(6, suggestTitles("saga", titles).size)
        assertEquals(2, suggestTitles("saga", titles, limit = 2).size)
        assertTrue(suggestTitles("   ", titles).isEmpty())
        assertTrue(suggestTitles("", titles).isEmpty())
    }

    @Test
    fun filtersSplitMoviesFromSeries() {
        assertTrue(matchesSearchFilter("movie", SearchFilter.ALL))
        assertTrue(matchesSearchFilter("movie", SearchFilter.MOVIES))
        assertFalse(matchesSearchFilter("series", SearchFilter.MOVIES))
        // Series, anime, kids y novelas caen del lado de «Series».
        assertTrue(matchesSearchFilter("series", SearchFilter.SERIES))
        assertTrue(matchesSearchFilter("anime", SearchFilter.SERIES))
        assertTrue(matchesSearchFilter("kids", SearchFilter.SERIES))
    }

    @Test
    fun recentsGoToTheFrontWithoutDuplicates() {
        val list = listOf("shogun", "nemo", "casa")
        assertEquals(listOf("guerra", "shogun", "nemo", "casa"), RecentSearches.merge(list, "guerra"))
        // Repetir (con otra caja o acento) no duplica: sube al frente.
        assertEquals(listOf("Shogun", "nemo", "casa"), RecentSearches.merge(list, "  Shogun "))
        assertEquals(listOf("shogun", "nemo", "casa"), RecentSearches.merge(list, "   "))
    }

    @Test
    fun recentsKeepOnlyTheNewestEight() {
        var list = emptyList<String>()
        for (i in 1..12) list = RecentSearches.merge(list, "consulta $i")
        assertEquals(RecentSearches.MAX, list.size)
        assertEquals("consulta 12", list.first())
    }

    @Test
    fun bothScreensUseTheAutocompleteAndRecents() {
        val phone = source("ui/phone/SearchScreen.kt")
        assertTrue("el móvil no autocompleta", phone.contains("suggestTitles("))
        assertTrue("el móvil no guarda recientes", phone.contains("RecentSearches.add("))
        assertTrue("el móvil no pinta recientes", phone.contains("RecentSearches.list("))
        // Filtros Todo / Películas / Series y campo integrado (sin header «Buscar» aparte).
        assertTrue(phone.contains("SearchFilter.entries"))
        assertTrue(phone.contains("BasicTextField("))

        val tv = source("ui/tv/TvSearchScreen.kt")
        assertTrue("la tele no autocompleta", tv.contains("suggestTitles("))
        assertTrue("la tele no guarda recientes", tv.contains("RecentSearches.add("))
        assertTrue("la tele no pinta atajos", tv.contains("private fun TvSearchChip("))
        // Teclado compacto: la tecla ya no crece hasta llenar la columna.
        assertTrue(tv.contains("minOf(byWidth, byHeight, 46.dp)"))
    }
}
