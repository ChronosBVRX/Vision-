package com.chronosbvrx.visionplus.data.sources

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del índice remoto: Categorías y Búsqueda dependen de este parseo.
 * Si se rompe, las pantallas salen vacías aunque el servidor responda bien.
 */
class RemoteIndexTest {

    @Test
    fun blankIsEmptyNotACrash() {
        val payload = RemoteIndexSource.parseIndex("")
        assertEquals(0, payload.version)
        assertTrue(payload.rows.isEmpty())
    }

    @Test
    fun upToDateKeepsTheCachedCopy() {
        val payload = RemoteIndexSource.parseIndex("""{"version":9,"upToDate":true,"categories":[]}""")
        assertEquals(9, payload.version)
        assertTrue(payload.upToDate)
        assertTrue(payload.rows.isEmpty())
    }

    @Test
    fun rowsSkipEntriesWithoutId() {
        val categories = JSONArray(
            """[
              {"id":"","title":"Sin id","films":[]},
              {"title":"Tampoco trae id","films":[]},
              {"id":"ok","title":"Buena","films":[
                {"id":"","title":"Sin id"},
                {"id":"f1","title":"Una","year":2026,"durationMin":90,"synopsis":"s",
                 "posterUrl":"p","videoUrl":"xtv:f1","sourceUrl":"xtv","score":7.5}
              ]}
            ]""",
        )
        val rows = RemoteIndexSource.parseRows(categories)
        assertEquals(listOf("ok"), rows.map { it.id })
        assertEquals("f1", rows.single().films.single().id)
        assertEquals(7.5, rows.single().films.single().score, 0.001)
    }

    @Test
    fun seasonsDedupeAndSort() {
        val arr = JSONArray(
            """[{"n":2,"titleId":"b","videoUrl":"xtv:b"},
                {"n":1,"titleId":"a","videoUrl":"xtv:a"},
                {"n":2,"titleId":"b2","videoUrl":"xtv:b2"}]""",
        )
        val seasons = RemoteIndexSource.parseSeasons(arr)
        assertEquals(listOf(1, 2), seasons.map { it.n })
        assertEquals("xtv:a", seasons.first().videoUrl)
        assertEquals("xtv:b", seasons.last().videoUrl)
        assertTrue(RemoteIndexSource.parseSeasons(null).isEmpty())
    }

    @Test
    fun seasonsPreferXtvMember() {
        // Grupo mixto (T1 XTV + T1 Magis): la ficha solo resuelve el XTV por el portal.
        val arr = JSONArray(
            """[{"n":1,"titleId":"magis:a","videoUrl":"magisvod:a"},
                {"n":1,"titleId":"xtv:b","videoUrl":"xtv:b"},
                {"n":2,"titleId":"magis:c","videoUrl":"magisvod:c"}]""",
        )
        val seasons = RemoteIndexSource.parseSeasons(arr)
        assertEquals(listOf(1, 2), seasons.map { it.n })
        assertEquals("xtv:b", seasons[0].titleId)
        // Temporada solo-Magis se conserva (antes vacía que rota).
        assertEquals("magis:c", seasons[1].titleId)
    }

    @Test
    fun groupIdSurvivesTheParse() {
        val categories = JSONArray(
            """[{"id":"series","title":"Series","films":[
                  {"id":"g","title":"Grupo","year":2024,"durationMin":45,"synopsis":"s",
                   "posterUrl":"p","videoUrl":"xtv:g","sourceUrl":"xtv",
                   "groupId":"grupo-1",
                   "seasons":[{"n":1,"titleId":"a","videoUrl":"xtv:a"}]}
                ]}]""",
        )
        val rows = RemoteIndexSource.parseRows(categories)
        val film = rows.single().films.single()
        assertEquals("grupo-1", film.groupId)
        assertEquals(1, film.seasons.size)
        assertEquals(1, film.seasons.single().n)
    }

    @Test
    fun missingFilmsArrayIsAnEmptyRow() {
        val rows = RemoteIndexSource.parseRows(JSONArray("""[{"id":"vacia","title":"Vacía"}]"""))
        assertEquals(1, rows.size)
        assertTrue(rows.single().films.isEmpty())
        // Un objeto roto no tumba al resto.
        val mixed = JSONArray("[null,{\"id\":\"ok\",\"title\":\"Ok\",\"films\":[]}]")
        assertEquals(listOf("ok"), RemoteIndexSource.parseRows(mixed).map { it.id })
        assertTrue(RemoteIndexSource.parseRows(JSONObject().optJSONArray("nope") ?: JSONArray()).isEmpty())
    }
}
