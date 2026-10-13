package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El portal solo entiende el contentId pelado: el índice del servidor guarda `xtv:<cid>`
 * y las repisas `<cid>`. Si esto se rompe, las fichas abiertas desde Categorías/Búsqueda
 * pierden capítulos, ficha y play ("No se pudo abrir") mientras que las de Inicio siguen bien.
 */
class X4KApiIdTest {

    @Test
    fun bareIdPassesThrough() {
        assertEquals("abc123", xtvPortalId("abc123"))
    }

    @Test
    fun prefixedIdIsStripped() {
        assertEquals("abc123", xtvPortalId("xtv:abc123"))
    }

    @Test
    fun prefixIsCaseInsensitiveAndTrimmed() {
        assertEquals("abc123", xtvPortalId("  XTV:abc123  "))
    }

    @Test
    fun liveRefsPassThroughUntouched() {
        assertEquals("xtvlive:ABC", xtvPortalId("xtvlive:ABC"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", xtvPortalId("   "))
    }

    @Test
    fun cleanShelfTitleNormalizesCommonShelves() {
        assertEquals("Recién agregadas", X4KApi.cleanShelfTitle("Películas recién agregadas"))
        assertEquals("Estrenos", X4KApi.cleanShelfTitle("Estrenos 2025"))
        assertEquals("Novedades", X4KApi.cleanShelfTitle("Grandes novedades"))
        assertEquals("Últimos lanzamientos", X4KApi.cleanShelfTitle("últimos lanzamientos de cine"))
        assertEquals("Acción", X4KApi.cleanShelfTitle("Acción"))
    }

    @Test
    fun portalScoreHydratesRanking() {
        val withScore = org.json.JSONObject().put("score", "7.8")
        assertEquals(7.8, X4KApi.portalScore(withScore), 0.001)
        val blank = org.json.JSONObject().put("score", "")
        assertEquals(0.0, blank.optDouble("score", 0.0), 0.001)
        assertEquals(0.0, X4KApi.portalScore(blank), 0.001)
        val zero = org.json.JSONObject().put("score", "0")
        assertEquals(0.0, X4KApi.portalScore(zero), 0.001)
    }

    @Test
    fun portalYearParsesReleaseTime() {
        val dated = org.json.JSONObject().put("releaseTime", "2024-05-01 00:00:00")
        assertEquals(2024, X4KApi.portalYear(dated))
        val empty = org.json.JSONObject()
        assertEquals(0, X4KApi.portalYear(empty))
    }
}

