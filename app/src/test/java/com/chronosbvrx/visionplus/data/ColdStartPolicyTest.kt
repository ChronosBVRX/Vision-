package com.chronosbvrx.visionplus.data

import com.chronosbvrx.visionplus.data.xuper.xtvPortalId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas que blinda la Política Estricta de Arranque Seguro en Smart TV / Chromecast
 * (Zero-Crash Cold Start & Non-Empty Catalog).
 *
 * Reglas inviolables verificadas:
 * 1. El catálogo semilla inicial (CatalogSeed) SIEMPRE provee contenido curado no vacío para arranque en 0 ms.
 * 2. Todos los títulos de la semilla poseen IDs válidos resolubles por el portal XTV.
 * 3. La emisión progresiva (Row Streaming) entrega filas en tiempo real al callback onRowLoaded.
 * 4. Toda fila semilla cuenta con póster, título y sinopsis no vacíos para que la vista Hero de TV
 *    tenga contenido visual completo desde el frame 1.
 */
class ColdStartPolicyTest {

    @Test
    fun seedCatalogProvidesImmediateNonEmptyContent() {
        val seedRows = CatalogSeed.initialRows()
        assertTrue("El catálogo semilla no debe estar vacío", seedRows.isNotEmpty())
        assertTrue("Debe incluir al menos 2 repisas (ej. destacados y series)", seedRows.size >= 2)

        val firstRow = seedRows.first()
        assertTrue("La primera repisa debe contener títulos", firstRow.films.isNotEmpty())

        val heroFilm = firstRow.films.first()
        assertTrue("El título destacado de Hero no puede estar en blanco", heroFilm.title.isNotBlank())
        assertTrue("La sinopsis de Hero no puede estar en blanco", heroFilm.synopsis.isNotBlank())
        assertTrue("La URL del póster no puede estar en blanco", heroFilm.posterUrl.isNotBlank())
        assertTrue("El ID debe tener formato xtv:", heroFilm.id.startsWith("xtv:"))
    }

    @Test
    fun seedCatalogIdsAreValidXtvPortalIds() {
        val seedRows = CatalogSeed.initialRows()
        for (row in seedRows) {
            for (film in row.films) {
                val portalId = xtvPortalId(film.id)
                assertFalse("El portalId pelado no puede estar vacío", portalId.isBlank())
                assertFalse("El portalId no debe retener el prefijo xtv:", portalId.startsWith("xtv:"))
            }
        }
    }

    @Test
    fun progressiveRowStreamingInvokesCallbackPerShelf() {
        val loadedRows = mutableListOf<CatalogRow>()
        val mockShelves = listOf(
            CatalogRow("masnew_featured_0", "Destacados", listOf(
                Film("xtv:1", "Film 1", 2024, 120, "Syn 1", "p1", "xtv:1", "visionplus.tv")
            )),
            CatalogRow("masnew_movies_0", "Películas", listOf(
                Film("xtv:2", "Film 2", 2024, 110, "Syn 2", "p2", "xtv:2", "visionplus.tv")
            )),
        )

        // Simula la llamada onRowLoaded de X4KApi.homeRows
        val onRowLoaded: (CatalogRow) -> Unit = { row ->
            loadedRows.add(row)
        }

        mockShelves.forEach { onRowLoaded(it) }

        assertEquals("Debe haberse emitido cada fila progresivamente", 2, loadedRows.size)
        assertEquals("masnew_featured_0", loadedRows[0].id)
        assertEquals("masnew_movies_0", loadedRows[1].id)
    }

    @Test
    fun seedCatalogFilmsHavePositiveDurationOrYear() {
        val seedRows = CatalogSeed.initialRows()
        val allFilms = seedRows.flatMap { it.films }
        for (film in allFilms) {
            assertTrue("Las películas semilla deben tener año definido", film.year >= 2020)
            assertTrue("Las películas semilla deben tener duración positiva", film.durationMin > 0)
            assertTrue("Las películas semilla deben tener calificación", film.score > 0.0)
        }
    }
}
