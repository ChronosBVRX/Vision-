package com.chronosbvrx.visionplus.data.mirror

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El espejo de capítulos solo puede mejorar: sin copia fresca todo es `null`
 * y `X4KApi.episodeProgramsFor` cae al portal EXACTO de siempre. Estos tests
 * fijan el contrato del cliente (refs, cuerpo del endpoint, ttl de RAM) y el
 * cableado en `X4KApi`.
 */
class EpisodeMirrorTest {

    // ----- refs (misma regla que `xtvPortalId`) -----

    @Test
    fun mirrorRefNormalizesTheWayTheIndexDoes() {
        assertEquals("xtv:ABC123", EpisodeMirror.mirrorRef("xtv:ABC123"))
        assertEquals("xtv:ABC123", EpisodeMirror.mirrorRef("XTV: ABC123"))
        assertEquals("xtv:ABC123", EpisodeMirror.mirrorRef(" ABC123 "))
    }

    // ----- cuerpo del endpoint -----

    @Test
    fun okBodyWithRowsYieldsTheRawArray() {
        val body = JSONObject()
            .put("status", "ok")
            .put("ref", "xtv:111")
            .put(
                "episodes",
                JSONArray()
                    .put(JSONObject().put("contentId", "a").put("name", "C1").put("seriesNumber", 1).put("episodeNumber", 1))
                    .put(JSONObject().put("contentId", "b").put("name", "C2").put("seriesNumber", 1).put("episodeNumber", 2)),
            )
            .toString()
        val array = requireNotNull(EpisodeMirror.parseMirrorBody(body))
        assertEquals(2, array.length())
        assertEquals("a", array.optJSONObject(0)?.optString("contentId"))
        assertEquals("C2", array.optJSONObject(1)?.optString("name"))
    }

    @Test
    fun missAndGarbageAndEmptyAreNull() {
        assertNull(EpisodeMirror.parseMirrorBody(null))
        assertNull(EpisodeMirror.parseMirrorBody(""))
        assertNull(EpisodeMirror.parseMirrorBody("   "))
        assertNull(EpisodeMirror.parseMirrorBody("{roto"))
        // miss honesto del servidor (fila vencida/rotura de red del worker)
        assertNull(EpisodeMirror.parseMirrorBody(JSONObject().put("status", "miss").put("episodes", JSONArray()).toString()))
        // ok sin filas → no vale la pena: el parser del portal hace lo mismo
        assertNull(EpisodeMirror.parseMirrorBody(JSONObject().put("status", "ok").put("episodes", JSONArray()).toString()))
        assertNull(EpisodeMirror.parseMirrorBody(JSONObject().put("status", "ok").toString()))
    }

    // ----- caché en RAM -----

    @Test
    fun cacheFreshRespectsTheTtl() {
        assertTrue(EpisodeMirror.cacheFresh(1_000, 1_000 + EpisodeMirror.CACHE_TTL_MS - 1))
        assertFalse(EpisodeMirror.cacheFresh(1_000, 1_000 + EpisodeMirror.CACHE_TTL_MS))
        assertFalse(EpisodeMirror.cacheFresh(0, 5_000)) // sin marca, nunca
    }

    @Test
    fun freshCacheIsServedWithoutTouchingTheNetwork() {
        EpisodeMirror.cacheClearForTests()
        try {
            val array = JSONArray().put(JSONObject().put("contentId", "x"))
            EpisodeMirror.cachePutForTests("ctv:999", System.currentTimeMillis() + 5_000, array)
            // Si tocara la red esperaríamos 2 s (timeouts); este test caducaría.
            val hit = kotlinx.coroutines.runBlocking { EpisodeMirror.episodes("ctv:999") }
            assertEquals(1, hit?.length())
        } finally {
            EpisodeMirror.cacheClearForTests()
        }
    }

    @Test
    fun staleCacheIsIgnored() {
        EpisodeMirror.cacheClearForTests()
        try {
            EpisodeMirror.cachePutForTests(
                "ctv:777",
                System.currentTimeMillis() - EpisodeMirror.CACHE_TTL_MS - 1_000,
                JSONArray().put(JSONObject().put("contentId", "viejo")),
            )
            // Con la copia vencida va a red (falla aquí: no hay servidor en tests) → null. Que tarde < 4 s.
            val hit = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(5_000) { EpisodeMirror.episodes("ctv:777") }
            }
            assertNull("la copia vencida se ignora y sin red el espejo es null", hit)
        } finally {
            EpisodeMirror.cacheClearForTests()
        }
    }

    // ----- cableado en X4KApi -----

    @Test
    fun x4kApiFallsBackToThePortalAfterTheMirror() {
        val source = requireSource("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt")
        assertTrue(
            "episodeProgramsFor pregunta al espejo PRIMERO",
            source.contains("EpisodeMirror.episodes(seriesId)"),
        )
        val start = source.indexOf("suspend fun episodeProgramsFor(")
        val mirrorAt = source.indexOf("EpisodeMirror.episodes(", start)
        val portalAt = source.indexOf("/api/portalCore/v4/getItemData", start)
        assertTrue("el espejo va antes del portal", mirrorAt in 0 until portalAt)
        assertTrue(
            "el espejo se parsea con el MISMO parser del portal (una sola implementación)",
            source.contains("parseEpisodePrograms(it)"),
        )
    }

    private fun requireSource(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
