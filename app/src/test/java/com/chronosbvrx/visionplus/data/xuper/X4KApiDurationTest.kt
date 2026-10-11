package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias para el parseo y normalización de duración VOD en X4KApi.
 * Valida que la duración total real sea extraída y fijada correctamente sin
 * depender de los fragmentos de red parciales HLS.
 */
class X4KApiDurationTest {

    @Test
    fun parsesClockThreeParts() {
        // "01:30:00" = 90 minutos = 5_400_000 ms
        assertEquals(5_400_000L, X4KApi.parseDurationMs("01:30:00"))
        // "02:15:30" = 2h 15m 30s = 8_130_000 ms
        assertEquals(8_130_000L, X4KApi.parseDurationMs("02:15:30"))
    }

    @Test
    fun parsesClockTwoParts() {
        // "45:00" = 45 minutos = 2_700_000 ms
        assertEquals(2_700_000L, X4KApi.parseDurationMs("45:00"))
        // "12:30" = 12m 30s = 750_000 ms
        assertEquals(750_000L, X4KApi.parseDurationMs("12:30"))
    }

    @Test
    fun parsesMinutesWithTextUnits() {
        // "90 min" = 5_400_000 ms
        assertEquals(5_400_000L, X4KApi.parseDurationMs("90 min"))
        // "45m" = 2_700_000 ms
        assertEquals(2_700_000L, X4KApi.parseDurationMs("45m"))
        // "120 minutos" = 7_200_000 ms
        assertEquals(7_200_000L, X4KApi.parseDurationMs("120 minutos"))
    }

    @Test
    fun parsesHoursAndMinutesCombined() {
        // "1h 30m" = 90 min = 5_400_000 ms
        assertEquals(5_400_000L, X4KApi.parseDurationMs("1h 30m"))
        // "2 h 15 min" = 135 min = 8_100_000 ms
        assertEquals(8_100_000L, X4KApi.parseDurationMs("2 h 15 min"))
    }

    @Test
    fun parsesRawNumericSeconds() {
        // "5400" (> 300) = 5400 segundos = 90 minutos = 5_400_000 ms
        assertEquals(5_400_000L, X4KApi.parseDurationMs("5400"))
    }

    @Test
    fun parsesRawNumericMinutes() {
        // "90" (<= 300) = 90 minutos = 5_400_000 ms
        assertEquals(5_400_000L, X4KApi.parseDurationMs("90"))
        // "45" = 45 minutos = 2_700_000 ms
        assertEquals(2_700_000L, X4KApi.parseDurationMs("45"))
    }

    @Test
    fun parsesRawNumericMilliseconds() {
        // "5400000" (>= 3_600_000) = 5_400_000 ms
        assertEquals(5_400_000L, X4KApi.parseDurationMs("5400000"))
    }

    @Test
    fun handlesBlankAndInvalidStrings() {
        assertEquals(0L, X4KApi.parseDurationMs(""))
        assertEquals(0L, X4KApi.parseDurationMs("   "))
        assertEquals(0L, X4KApi.parseDurationMs("0"))
        assertEquals(0L, X4KApi.parseDurationMs("desconocido"))
    }

    @Test
    fun parseEpisodeProgramsSortsBySeriesNumberAndDeduplicates() {
        val raw = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("contentId", "ep_3").put("seriesNumber", 3).put("name", "El tercer paso"))
            put(org.json.JSONObject().put("contentId", "ep_1").put("seriesNumber", 1).put("name", "El comienzo"))
            put(org.json.JSONObject().put("contentId", "ep_2").put("seriesNumber", 2).put("name", "La búsqueda"))
            put(org.json.JSONObject().put("contentId", "ep_1").put("seriesNumber", 1).put("name", "Duplicado"))
        }
        val ordered = X4KApi.parseEpisodePrograms(raw)
        assertEquals(listOf("ep_1", "ep_2", "ep_3"), ordered.map { it.contentId })
        assertEquals(listOf(1, 2, 3), ordered.map { it.number })
        assertEquals(listOf("El comienzo", "La búsqueda", "El tercer paso"), ordered.map { it.name })
    }

    @Test
    fun parseEpisodeProgramsExtractsNumberFromNameWhenSeriesNumberMissing() {
        val raw = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("contentId", "c10").put("name", "Capítulo 10"))
            put(org.json.JSONObject().put("contentId", "c2").put("name", "Capítulo 2"))
            put(org.json.JSONObject().put("contentId", "c1").put("name", "Capítulo 1"))
        }
        val ordered = X4KApi.parseEpisodePrograms(raw)
        assertEquals(listOf("c1", "c2", "c10"), ordered.map { it.contentId })
        assertEquals(listOf(1, 2, 10), ordered.map { it.number })
    }

    @Test
    fun parsePlayEpisodeProgramsSortsPlayFallbackEpisodes() {
        val play = org.json.JSONObject().apply {
            val epList = org.json.JSONArray()
            epList.put(
                org.json.JSONObject()
                    .put("seriesNumber", 2)
                    .put("name", "Episodio Dos")
                    .put(
                        "totalMovieList",
                        org.json.JSONArray().put(
                            org.json.JSONObject().put(
                                "movieList",
                                org.json.JSONArray().put(org.json.JSONObject().put("contentId", "play_2")),
                            ),
                        ),
                    ),
            )
            epList.put(
                org.json.JSONObject()
                    .put("seriesNumber", 1)
                    .put("name", "Episodio Uno")
                    .put(
                        "totalMovieList",
                        org.json.JSONArray().put(
                            org.json.JSONObject().put(
                                "movieList",
                                org.json.JSONArray().put(org.json.JSONObject().put("contentId", "play_1")),
                            ),
                        ),
                    ),
            )
            put("episodeList", epList)
        }
        val ordered = X4KApi.parsePlayEpisodePrograms(play)
        assertEquals(listOf("play_1", "play_2"), ordered.map { it.contentId })
        assertEquals(listOf("Episodio Uno", "Episodio Dos"), ordered.map { it.name })
    }
}
