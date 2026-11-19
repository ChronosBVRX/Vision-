package com.chronosbvrx.visionplus.data.external

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El addon declara la resolución en el nombre/título/filename. Solo se aceptan
 * pistas 4K; un HLS sin etiqueta se deja pasar para que el repositorio compruebe
 * su manifiesto, pero un MP4 1080p no.
 */
class External4KParserTest {

    private val now = 1_800_000_000_000L

    private fun stream(name: String, title: String, url: String, filename: String = "") = JSONObject().apply {
        put("name", name)
        put("title", title)
        put("url", url)
        if (filename.isNotBlank()) put("behaviorHints", JSONObject().put("filename", filename))
    }

    private fun payload(vararg streams: JSONObject) = JSONObject().put(
        "streams",
        org.json.JSONArray().apply { streams.forEach { put(it) } },
    ).toString()

    @Test
    fun aLabeled4kStreamIsAcceptedWithProviderAndLatinoAudio() {
        val json = payload(
            stream("[? KRONOS Cloud (Respaldo)]\nLatino", "AUDIO LATINO · Respaldo", "http://192.168.1.101:7015/x.mkv", "Peli (2026) [2160p] 4K HDR10.mkv"),
        )
        val out = External4KParser.parseStreams(json, now, 6 * 60 * 60 * 1000L)
        assertEquals(1, out.size)
        assertEquals("Latino", out[0].language)
        assertTrue(out[0].lan)
        assertTrue(out[0].url.contains("192.168.1.101"))
    }

    @Test
    fun spanishAudioWinsOverASmallerOriginalFile() {
        val original = JSONObject().apply {
            put("name", "4KHDHub 4K")
            put("description", "[5.00 GB] 2160p WEB-DL Hindi + English")
            put("url", "https://cdn.example/hindi.mkv")
            put("behaviorHints", JSONObject().put("videoSize", 5_000_000_000L))
        }
        val latino = JSONObject().apply {
            put("name", "4KHDHub 4K")
            put("description", "[12.00 GB] 2160p WEB-DL Latino")
            put("url", "https://cdn.example/latino.mkv")
            put("behaviorHints", JSONObject().put("videoSize", 12_000_000_000L))
        }
        val out = External4KParser.parseStreams(payload(original, latino), now, 6 * 60 * 60 * 1000L)
        assertEquals("https://cdn.example/latino.mkv", out[0].url)
        assertEquals("Latino", out[0].language)
    }

    @Test
    fun spanishAndLatamLabelsAreDetectedAndHindiIsNotSpanish() {
        val out = External4KParser.parseStreams(
            payload(
                stream("[A]\nEspañol", "2160p", "https://cdn.example/es.mkv"),
                stream("[B]\nLATAM", "2160p", "https://cdn.example/latam.mkv"),
                stream("[C]\nHindi + English", "2160p", "https://cdn.example/hindi.mkv"),
            ),
            now,
            6 * 60 * 60 * 1000L,
        )
        val byUrl = out.associateBy { it.url }
        assertEquals("Castellano", byUrl["https://cdn.example/es.mkv"]?.language)
        assertEquals("Latino", byUrl["https://cdn.example/latam.mkv"]?.language)
        assertEquals("Original", byUrl["https://cdn.example/hindi.mkv"]?.language)
    }

    @Test
    fun theServerLanguageFieldIsHonored() {
        val stream = JSONObject().apply {
            put("name", "4KHDHub 4K")
            put("title", "2160p")
            put("language", "Latino")
            put("url", "https://cdn.example/latino.mkv")
        }
        val out = External4KParser.parseStreams(payload(stream), now, 6 * 60 * 60 * 1000L)
        assertEquals("Latino", out[0].language)
    }

    @Test
    fun aPublicHdHubR2StreamIsAcceptedAndTheSmallerOneWins() {
        val big = JSONObject().apply {
            put("name", "4KHDHub 4K")
            put("description", "[84.47 GB] 2160p UHD REMUX HDR")
            put("url", "https://r2.example/big.mkv?X-Amz-Expires=18000")
            put("behaviorHints", JSONObject().put("videoSize", 90_000_000_000L))
        }
        val small = JSONObject().apply {
            put("name", "4KHDHub 4K")
            put("description", "[27.74 GB] 2160p WEB-DL HDR")
            put("url", "https://r2.example/small.mkv?X-Amz-Expires=18000")
            put("behaviorHints", JSONObject().put("videoSize", 27_000_000_000L))
        }
        val out = External4KParser.parseStreams(payload(small, big), now, 6 * 60 * 60 * 1000L)
        assertEquals(2, out.size)
        assertEquals("4KHDHub", out[0].provider)
        assertEquals("https://r2.example/small.mkv?X-Amz-Expires=18000", out[0].url)
        assertEquals(now + 18_000_000L, out[0].expiresAt)
        assertFalse(out[0].lan)
    }

    @Test
    fun aHdHub1080pIsNotMistakenFor4kByTheProviderName() {
        val stream = JSONObject().apply {
            put("name", "4KHDHub 1080p")
            put("description", "[3.5 GB] 1080p WEB-DL")
            put("url", "https://r2.example/normal.mkv?X-Amz-Expires=18000")
        }
        assertEquals(0, External4KParser.parseStreams(payload(stream), now, 1000L).size)
    }

    @Test
    fun a1080pMp4IsDiscarded() {
        val json = payload(
            stream("[? VIMEUS]\nLatino FHD", "HLS Directo", "https://cdn.example/master.m3u8", "Peli.1080p.mkv"),
            stream("[? VIMEUS]\nLatino FHD", "HLS Directo", "https://cdn.example/peli.mp4", "Peli.1080p.mp4"),
        )
        // El HLS sin 4K queda como candidato (se verifica después); el MP4 1080p no.
        val out = External4KParser.parseStreams(json, now, 6 * 60 * 60 * 1000L)
        assertEquals(1, out.size)
        assertTrue(out[0].isHls)
    }

    @Test
    fun publicSourcesRankBeforeLanAndLatinoBeforeCastellano() {
        val json = payload(
            stream("[? LAN]\nCastellano", "2160p", "http://192.168.1.50/a.mkv"),
            stream("[? PUBLIC]\nCastellano", "2160p", "https://cdn.example/b.mkv"),
            stream("[? PUBLIC]\nLatino", "2160p", "https://cdn.example/c.mkv"),
        )
        val out = External4KParser.parseStreams(json, now, 6 * 60 * 60 * 1000L)
        assertEquals("https://cdn.example/c.mkv", out[0].url)
        assertEquals("https://cdn.example/b.mkv", out[1].url)
        assertEquals("http://192.168.1.50/a.mkv", out[2].url)
    }

    @Test
    fun duplicateUrlsAppearOnceAndTheCdnExpiryIsRespected() {
        val url = "https://cdn.example/x.mkv?e=3600"
        val json = payload(
            stream("[? A]\nLatino", "2160p", url),
            stream("[? B]\nLatino", "2160p", url),
        )
        val out = External4KParser.parseStreams(json, now, 6 * 60 * 60 * 1000L)
        assertEquals(1, out.size)
        assertEquals(now + 3_600_000L, out[0].expiresAt)
    }

    @Test
    fun aCamCopyIsDiscardedEvenIfItSays4k() {
        val json = payload(
            stream("[? CAM]\nLatino", "4K CAM", "https://cdn.example/cam.mkv"),
        )
        assertEquals(0, External4KParser.parseStreams(json, now, 6 * 60 * 60 * 1000L).size)
    }

    @Test
    fun imdbIsResolvedByTmdbFirstAndNeverByAnotherYear() {
        val json = """
            {"metas":[
              {"name":"Otra peli","year":2020,"imdb_id":"tt1111111","moviedb_id":1},
              {"name":"Peli","year":2026,"imdb_id":"tt2222222","moviedb_id":42}
            ]}
        """.trimIndent()
        assertEquals("tt2222222", External4KParser.imdbFromCinemeta(json, 42, "Peli", 2026))
        assertEquals("tt2222222", External4KParser.imdbFromCinemeta(json, 0, "Peli", 2026))
    }

    @Test
    fun aDifferentYearDoesNotMatchWithoutTmdb() {
        val json = """{"metas":[{"name":"Peli","year":1999,"imdb_id":"tt3333333"}]}"""
        assertEquals(null, External4KParser.imdbFromCinemeta(json, 0, "Peli", 2026))
    }

    @Test
    fun masterHeightReadsResolutionAndIgnores1080() {
        assertEquals(2160, External4KParser.maxHeightFromMaster("#EXT-X-STREAM-INF:RESOLUTION=3840x2160"))
        assertEquals(1080, External4KParser.maxHeightFromMaster("#EXT-X-STREAM-INF:RESOLUTION=1920x1080"))
        assertEquals(2160, External4KParser.maxHeightFromMaster("#EXT-X-STREAM-INF:RESOLUTION=1920x1080\n# 2160p"))
    }

    @Test
    fun aFileWithout4kOrHlsIsIgnored() {
        assertEquals(0, External4KParser.parseStreams("""{"streams":[{"name":"x","title":"y","url":"https://a/b.mp4"}]}""", now, 1000L).size)
        assertFalse(External4KParser.parseStreams("", now, 1000L).isNotEmpty())
    }
}
