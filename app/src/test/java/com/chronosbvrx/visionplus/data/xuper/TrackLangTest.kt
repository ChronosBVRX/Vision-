package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El motor reporta el códec ("aac") como idioma: la lista debe mostrar el idioma real
 * (el del motor o el declarado por el portal) y sin repetir etiquetas.
 */
class TrackLangTest {

    @Test
    fun realEngineLangWins() {
        assertEquals("spa", trackDisplayLang("spa", "eng"))
        assertEquals("en", trackDisplayLang("EN", null))
    }

    @Test
    fun codecFallsBackToDeclared() {
        // "aac, aac" del motor + "spa, eng" del portal = Español, Inglés.
        assertEquals("spa", trackDisplayLang("aac", "spa"))
        assertEquals("eng", trackDisplayLang("AAC", "eng"))
        assertEquals("spa", trackDisplayLang("", "spa"))
    }

    @Test
    fun unknownStaysBlank() {
        assertEquals("", trackDisplayLang("aac", null))
        assertEquals("", trackDisplayLang("", null))
        assertEquals("", trackDisplayLang("und", "  "))
    }

    @Test
    fun duplicatesGetTrackSuffix() {
        assertEquals(
            listOf("Español", "Español · Pista 4", "Inglés"),
            dedupeTrackLabels(listOf("Español", "Español", "Inglés"), listOf(2, 4, 5)),
        )
    }

    @Test
    fun uniqueLabelsPassThrough() {
        val labels = listOf("Español", "Inglés")
        assertEquals(labels, dedupeTrackLabels(labels, listOf(1, 2)))
    }
}
