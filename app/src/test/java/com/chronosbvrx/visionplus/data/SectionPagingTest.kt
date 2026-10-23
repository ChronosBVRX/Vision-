package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Ver más» de una sección: lotes y techo según el equipo, y páginas que continúan
 * la lista sin saltarse el hueco entre el tope de la sección (40) y la página 1.
 */
class SectionPagingTest {

    @Test
    fun batchesAndCapsFollowTheDevice() {
        assertEquals(24, SectionPaging.batch(lowEnd = true))
        assertEquals(48, SectionPaging.batch(lowEnd = false))
        assertEquals(180, SectionPaging.cap(lowEnd = true))
        assertEquals(480, SectionPaging.cap(lowEnd = false))
    }

    @Test
    fun nextPageFillsTheInitialGapAndThenAdvances() {
        // La sección trae 40; la página 1 (48) rellena el hueco, no salta al 48.
        assertEquals(1, SectionPaging.nextPage(loaded = 40, batch = 48))
        assertEquals(2, SectionPaging.nextPage(loaded = 48, batch = 48))
        assertEquals(3, SectionPaging.nextPage(loaded = 96, batch = 48))
        // En low-end el mismo razonamiento con lotes de 24.
        assertEquals(2, SectionPaging.nextPage(loaded = 40, batch = 24))
        assertEquals(3, SectionPaging.nextPage(loaded = 48, batch = 24))
        assertEquals(1, SectionPaging.nextPage(loaded = 0, batch = 0))
    }

    @Test
    fun canLoadMoreStopsAtTheTotalAndAtTheDeviceCap() {
        assertTrue(SectionPaging.canLoadMore(loaded = 40, total = 500, cap = 180))
        assertTrue(SectionPaging.canLoadMore(loaded = 40, total = 100, cap = 180))
        assertFalse(SectionPaging.canLoadMore(loaded = 100, total = 100, cap = 180))
        assertFalse(SectionPaging.canLoadMore(loaded = 180, total = 500, cap = 180))
    }
}
