package com.chronosbvrx.visionplus.data.xuper

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-regresión del 1080p libre: la licencia vive en `v10/startPlayVOD` con
 * `type=0` y la columna Free del tenant `masmobile`. v9 solo daba 720p/480p.
 */
class X4KApiFreeV10Test {

    @Test
    fun freeColumnsComeFromMasnewFreePortada() {
        val recommend = JSONArray()
            .put(JSONObject().put("columnId", 87_887).put("name", "Free.Movies"))
            .put(JSONObject().put("columnId", 87_888).put("name", "Free.TV Series"))
            .put(JSONObject().put("columnId", 87_887).put("name", "duplicado"))
            .put(JSONObject().put("name", "sin id"))
        assertEquals(listOf(87_887, 87_888), X4KApi.freeColumnIdsFrom(recommend))
    }

    @Test
    fun freeColumnsIgnoreMissingOrBrokenPortada() {
        assertTrue(X4KApi.freeColumnIdsFrom(null).isEmpty())
        assertTrue(X4KApi.freeColumnIdsFrom(JSONArray()).isEmpty())
    }

    @Test
    fun v10BodyUsesPlayTenantAndFreeColumn() {
        val body = X4KApi.v10LicenseBody(
            userToken = "tok",
            userId = "42",
            contentId = "CID",
            seriesContentId = "SID",
            columnId = 87_887,
        )
        assertEquals("masmobile", body.getString("portalCode"))
        assertEquals("0", body.getString("type"))
        assertEquals(87_887, body.getInt("columnId"))
        assertEquals("CID", body.getString("contentId"))
        assertEquals("SID", body.getString("seriesContentId"))
        assertEquals("0", body.getString("authType"))
        assertEquals(1, body.getJSONArray("episodeNumberList").getInt(0))
    }

    @Test
    fun collectsFree1080pTracksAndDiscardsNonFree() {
        val result = JSONObject(
            """
            {"episodeList":[{"totalMovieList":[
              {"quality":"1080p","movieList":[
                {"contentId":"h265","encodeFormat":"h265","videoFormat":"ts","audioInfo":"spa",
                 "licenseList":[{"tag":"free","license":"L1"}]},
                {"contentId":"h264","encodeFormat":"h264","videoFormat":"mp4","audioInfo":"spa",
                 "licenseList":[{"tag":"free","license":"L2"}]}
              ]},
              {"quality":"1080p","movieList":[
                {"contentId":"vip","encodeFormat":"h264","videoFormat":"mp4","audioInfo":"spa",
                 "licenseList":[{"tag":"vip","license":"L3"}]}
              ]}
            ]}]}
            """.trimIndent(),
        )
        val tracks = X4KApi.collectMedias(result)
        assertEquals(2, tracks.length())
        assertEquals("1080p", tracks.getJSONObject(0).getString("q"))
        assertEquals("h264", tracks.getJSONObject(0).getString("e"))
        assertEquals("1080p", tracks.getJSONObject(1).getString("q"))
        assertEquals("h265", tracks.getJSONObject(1).getString("e"))
    }

    @Test
    fun viaIsFreeOnlyWhenThe1080pGroupCarriesAFreeLicense() {
        val free = JSONObject(
            """
            {"episodeList":[{"totalMovieList":[
              {"quality":"1080p","movieList":[
                {"contentId":"h264","licenseList":[{"tag":"free","license":"L"}]}
              ]}
            ]}]}
            """.trimIndent(),
        )
        val vip = JSONObject(
            """
            {"episodeList":[{"totalMovieList":[
              {"quality":"1080p","movieList":[
                {"contentId":"h264","licenseList":[{"tag":"vip","license":"L"}]}
              ]}
            ]}]}
            """.trimIndent(),
        )
        val no1080 = JSONObject(
            """
            {"episodeList":[{"totalMovieList":[
              {"quality":"480p","movieList":[
                {"contentId":"h264","licenseList":[{"tag":"free","license":"L"}]}
              ]}
            ]}]}
            """.trimIndent(),
        )
        assertTrue(X4KApi.hasFree1080(free))
        assertTrue(!X4KApi.hasFree1080(vip))
        assertTrue(!X4KApi.hasFree1080(no1080))
    }

    @Test
    fun playbackPrefers1080pAndH264WithinSameResolution() {
        val medias = JSONArray()
            .put(media("480-h264", "480p", "h264"))
            .put(media("1080-h265", "1080p", "h265"))
            .put(media("720-h264", "720p", "h264"))
            .put(media("1080-h264", "1080p", "h264"))
        val sorted = X4KApi.sortMediaArrayForPlayback(medias)
        assertEquals(
            listOf("1080-h264", "1080-h265", "720-h264", "480-h264"),
            (0 until sorted.length()).map { sorted.getJSONObject(it).getString("n") },
        )
    }

    private fun media(name: String, quality: String, codec: String): JSONObject =
        JSONObject()
            .put("n", name)
            .put("l", "license")
            .put("a", "spa")
            .put("q", quality)
            .put("e", codec)
            .put("f", "mp4")
}
