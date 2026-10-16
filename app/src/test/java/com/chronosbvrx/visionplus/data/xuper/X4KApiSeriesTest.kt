package com.chronosbvrx.visionplus.data.xuper

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-regresión película vs serie: el portal devuelve la ficha de una película
 * como único "capítulo" en simpleProgramList. Sin 2+ capítulos no es serie y el
 * reproductor no debe mostrar "Siguiente episodio" ni selector de temporadas.
 */
class X4KApiSeriesTest {

    @Test
    fun singleProgramIsMovieNotSeries() {
        val list = JSONArray().put(
            JSONObject().apply {
                put("contentId", "MOVIE123")
                put("name", "La película")
                put("seriesNumber", 1)
            },
        )
        assertTrue(
            "Un único programa del portal es película, no serie",
            X4KApi.parseEpisodePrograms(list).isEmpty(),
        )
    }

    @Test
    fun twoProgramsIsSeries() {
        val list = JSONArray()
            .put(JSONObject().apply { put("contentId", "EP1"); put("name", "Capítulo 1"); put("seriesNumber", 1) })
            .put(JSONObject().apply { put("contentId", "EP2"); put("name", "Capítulo 2"); put("seriesNumber", 2) })
        val programs = X4KApi.parseEpisodePrograms(list)
        assertEquals(2, programs.size)
        assertEquals("EP1", programs[0].contentId)
        assertEquals("EP2", programs[1].contentId)
    }

    @Test
    fun emptyOrNullIsMovie() {
        assertTrue(X4KApi.parseEpisodePrograms(null).isEmpty())
        assertTrue(X4KApi.parseEpisodePrograms(JSONArray()).isEmpty())
    }

    @Test
    fun portalEpisodeNamesAreNormalizedWithoutUnderscores() {
        // Los capítulos de SVU llegan como «…Especiales T1_11» (contrato real del portal):
        // el guion bajo jamás debe llegar a la UI; el número se sigue leyendo del nombre crudo.
        val list = JSONArray()
            .put(JSONObject().apply { put("contentId", "EP1"); put("name", "Ley y orden: Unidad de Víctimas Especiales T1_1") })
            .put(JSONObject().apply { put("contentId", "EP2"); put("name", "Ley y orden: Unidad de Víctimas Especiales T1_2"); put("seriesNumber", 2) })
        val programs = X4KApi.parseEpisodePrograms(list)
        assertEquals(2, programs.size)
        assertEquals("Ley y orden: Unidad de Víctimas Especiales T1 1", programs[0].name)
        assertFalse(programs.any { it.name.contains('_') })
    }
}
