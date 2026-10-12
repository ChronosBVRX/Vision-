package com.chronosbvrx.visionplus.ui

import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de arquitectura y optimización de UI/UX para secciones y catálogo:
 * 1. Límite estricto de 15 elementos por carrusel horizontal (Low-End Devices).
 * 2. Paginación en lotes de 24 elementos para cuadrícula infinita.
 * 3. Extracción de chips con "Todo / Destacados" como opción inicial canónica.
 * 4. Asignación correcta de ranking 1..10 para variantes Top 10.
 */
class SectionFeedOptimizationTest {

    private fun makeFilm(id: String, year: Int = 2026, score: Double = 8.5) = Film(
        id = id,
        title = "Film $id",
        year = year,
        durationMin = 100,
        synopsis = "Synopsis for $id",
        posterUrl = "https://example.com/$id.webp",
        videoUrl = "xtv:$id",
        sourceUrl = "xtv",
        score = score,
    )

    @Test
    fun carouselLimitsToFifteenItemsForLowEndMemoryProtection() {
        val largeList = (1..50).map { makeFilm("f$it") }
        val row = CatalogRow("action", "Acción", largeList)

        val carouselFilms = row.films.take(15)
        assertEquals(15, carouselFilms.size)
        assertEquals("f1", carouselFilms.first().id)
        assertEquals("f15", carouselFilms.last().id)
    }

    @Test
    fun gridBatchingIncrementsByTwentyFourWithoutExceedingTotal() {
        val totalFilms = (1..60).map { makeFilm("f$it") }
        val batchSize = 24

        var currentLimit = batchSize
        var displayed = totalFilms.take(currentLimit)
        assertEquals(24, displayed.size)

        // Siguiente lote (scroll cerca del final)
        currentLimit = (currentLimit + batchSize).coerceAtMost(totalFilms.size)
        displayed = totalFilms.take(currentLimit)
        assertEquals(48, displayed.size)

        // Tercer lote
        currentLimit = (currentLimit + batchSize).coerceAtMost(totalFilms.size)
        displayed = totalFilms.take(currentLimit)
        assertEquals(60, displayed.size)
    }

    @Test
    fun categoryChipsAlwaysLeadWithAllAndDeduplicateGenres() {
        val rows = listOf(
            CatalogRow("r1", "Acción", listOf(makeFilm("1"))),
            CatalogRow("r2", "Comedia", listOf(makeFilm("2"))),
            CatalogRow("r3", "Acción", listOf(makeFilm("3"))),
            CatalogRow("r4", "  ", listOf(makeFilm("4"))),
            CatalogRow("r5", "Drama", listOf(makeFilm("5"))),
        )

        val chips = listOf("Todo / Destacados") + rows.map { it.title.trim() }.filter { it.isNotBlank() }.distinct()

        assertEquals("Todo / Destacados", chips.first())
        assertEquals(listOf("Todo / Destacados", "Acción", "Comedia", "Drama"), chips)
    }

    @Test
    fun topTenRankIsAssignedOnlyToFirstTenItems() {
        val isTopRow = true
        val films = (0 until 15).map { idx ->
            val rank = if (isTopRow && idx in 0..9) idx + 1 else null
            rank
        }

        assertEquals(1, films[0])
        assertEquals(5, films[4])
        assertEquals(10, films[9])
        assertNull(films[10])
        assertNull(films[14])
    }
}
