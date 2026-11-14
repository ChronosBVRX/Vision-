package com.chronosbvrx.visionplus.data.xuper

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El portal XTV solo firma la licencia libre en la COLUMNA donde vive el título. Pedirla sin
 * columna (v9) devuelve 480p; con su columna Free (v10) entrega 1080p. Estos guardianes fijan
 * el parseo de columnas, el orden de la mejor pista y el cuerpo del play v10.
 */
class FreeQualityTest {

    private fun media(name: String, quality: String) = JSONObject().apply {
        put("n", name)
        put("l", "license")
        put("q", quality)
    }

    @Test
    fun freeColumnsArePositiveAndDeduplicated() {
        val result = JSONObject().apply {
            put("recommendList", JSONArray().apply {
                put(JSONObject().put("columnId", 87887))
                put(JSONObject().put("columnId", 87888))
                put(JSONObject().put("columnId", 87887))
                put(JSONObject().put("columnId", 0))
                put(JSONObject().put("columnId", -3))
                put(JSONObject().put("name", "sin id"))
            })
        }
        assertEquals(listOf(87887, 87888), X4KApi.freeColumnsFrom(result))
    }

    @Test
    fun freeColumnsTolerateAnEmptyResponse() {
        assertTrue(X4KApi.freeColumnsFrom(JSONObject()).isEmpty())
        assertTrue(X4KApi.freeColumnsFrom(JSONObject().put("recommendList", JSONArray())).isEmpty())
    }

    @Test
    fun theColumnMapIndexesEveryAssetOfAValidShelf() {
        val result = JSONObject().apply {
            put("recommendList", JSONArray().apply {
                put(JSONObject().put("columnId", 77116).put("assetList", JSONArray().apply {
                    put(JSONObject().put("contentId", "A"))
                    put(JSONObject().put("contentId", "B"))
                }))
                put(JSONObject().put("columnId", 0).put("assetList", JSONArray().apply {
                    put(JSONObject().put("contentId", "C"))
                }))
                put(JSONObject().put("columnId", 5))
            })
        }
        val map = X4KApi.indexColumnMap(result)
        assertEquals(77116, map.getInt("A"))
        assertEquals(77116, map.getInt("B"))
        assertEquals(2, map.length())
    }

    @Test
    fun qualityRankUnderstandsPortalLabels() {
        assertEquals(2160, X4KApi.qualityRank("4K"))
        assertEquals(2160, X4KApi.qualityRank("2160p"))
        assertEquals(1080, X4KApi.qualityRank("1080p"))
        assertEquals(1080, X4KApi.qualityRank("FHD"))
        assertEquals(720, X4KApi.qualityRank("720p"))
        assertEquals(480, X4KApi.qualityRank("SD"))
        assertEquals(0, X4KApi.qualityRank("Auto"))
    }

    @Test
    fun bestQualityGoesFirstAndThePortalOrderIsKeptInsideIt() {
        val medias = JSONArray().apply {
            put(media("h264-480", "480p"))
            put(media("h265-1080", "1080p"))
            put(media("h264-1080", "1080p"))
            put(media("h264-720", "720p"))
        }
        val ordered = X4KApi.sortMediasBestFirst(medias)
        assertEquals(listOf("h265-1080", "h264-1080", "h264-720", "h264-480"), names(ordered))
    }

    @Test
    fun v10BodyCarriesTheFreeColumnAndThePlayTenant() {
        val body = X4KApi.licenseColumnBody("token", "uid", "cid", "sid", 87887)
        assertEquals("masmobile", body.getString("portalCode"))
        assertEquals("token", body.getString("userToken"))
        assertEquals("uid", body.getString("userId"))
        assertEquals("cid", body.getString("contentId"))
        assertEquals("sid", body.getString("seriesContentId"))
        assertEquals(87887, body.getInt("columnId"))
        assertEquals("0", body.getString("type"))
        assertEquals("1", body.getString("authType"))
        assertEquals(1, body.getJSONArray("episodeNumberList").getInt(0))

        val movieBody = X4KApi.licenseColumnBody("token", "uid", "cid", "", 87887)
        assertEquals("cid", movieBody.getString("seriesContentId"))
        assertEquals("", movieBody.getString("authType"))
        assertTrue(!movieBody.has("episodeNumberList"))
    }

    private fun names(array: JSONArray): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until array.length()) out.add(array.getJSONObject(i).getString("n"))
        return out
    }
}
