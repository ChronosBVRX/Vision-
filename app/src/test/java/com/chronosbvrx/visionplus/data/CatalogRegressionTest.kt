package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del catálogo local: metaLine, búsqueda y dedup se usan en Home,
 * Categorías y Búsqueda (móvil y TV). Pequeños cambios aquí se notan en
 * todas las pantallas.
 */
class CatalogRegressionTest {

    private fun film(
        id: String = "f",
        title: String = "T",
        year: Int = 2026,
        durationMin: Int = 90,
        synopsis: String = "s",
    ) = Film(
        id = id, title = title, year = year, durationMin = durationMin,
        synopsis = synopsis, posterUrl = "p",
        videoUrl = "xtv:$id", sourceUrl = "xtv",
    )

    @Test
    fun metaLineOmitsUnknownParts() {
        assertEquals("", film(year = 0, durationMin = 0).metaLine())
        assertEquals("2026", film(durationMin = 0).metaLine())
        assertEquals("45 min", film(year = 0, durationMin = 45).metaLine())
        assertEquals("2 h", film(year = 0, durationMin = 120).metaLine())
        assertEquals("2026 · 1 h 32 min", film(durationMin = 92).metaLine())
    }

    @Test
    fun allFilmsDedupesAcrossRows() {
        val rows = listOf(
            CatalogRow("a", "A", listOf(film("f1"), film("f2"))),
            CatalogRow("b", "B", listOf(film("f1"), film("f3"))),
        )
        assertEquals(listOf("f1", "f2", "f3"), allFilms(rows).map { it.id })
    }

    @Test
    fun searchNeedsEveryWordAndIgnoresAccents() {
        val rows = listOf(
            CatalogRow("a", "A", listOf(film("f1", "El fútbol mexicano", synopsis = "Clásico nacional"))),
        )
        assertEquals(listOf("f1"), searchFilms(rows, "futbol mexicano").map { it.id })
        assertTrue(searchFilms(rows, "futbol europeo").isEmpty())
        // Búsqueda vacía = todo (lo usa Búsqueda para el estado inicial).
        assertEquals(1, searchFilms(rows, "   ").size)
    }

    @Test
    fun scoreLabelPrefersPortalThenIndex() {
        assertEquals("8.2", scoreLabel("8.2", 7.5))
        // El "0" del portal no cuenta: la ficha del índice muestra su calificación.
        assertEquals("7.5", scoreLabel("0", 7.5))
        assertEquals("7.5", scoreLabel("0.0", 7.5))
        assertEquals("", scoreLabel("0.0", 0.0))
        assertEquals("", scoreLabel(null, 0.0))
        assertEquals("", scoreLabel("  ", 0.0))
        // Enteros sin ".0" colgando.
        assertEquals("8", scoreLabel("", 8.0))
        assertEquals("6.3", scoreLabel(null, 6.3))
    }
}
