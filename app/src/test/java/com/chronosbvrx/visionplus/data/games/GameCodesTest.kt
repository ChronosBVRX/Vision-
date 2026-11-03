package com.chronosbvrx.visionplus.data.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCodesTest {

    @Test
    fun acceptsTheThreeFormsPeopleType() {
        assertEquals("ABC234", GameCodes.normalize("ABC234"))
        assertEquals("ABC234", GameCodes.normalize("abc-234"))
        assertEquals("ABC234", GameCodes.normalize(" ABC 234 "))
        assertEquals("ABC234", GameCodes.normalize("ABC_234"))
    }

    @Test
    fun rejectsAmbiguousOrIncompleteCodes() {
        assertNull(GameCodes.normalize(null))
        assertNull(GameCodes.normalize(""))
        assertNull(GameCodes.normalize("ABC23"))
        assertNull(GameCodes.normalize("ABC2345"))
        assertNull(GameCodes.normalize("ABC123")) // el 1 no existe
        assertNull(GameCodes.normalize("ABCI23")) // la I no existe
        assertNull(GameCodes.normalize("ABC-2O4")) // la O no existe
    }

    @Test
    fun formatsWithADashForReadingAloud() {
        assertEquals("ABC-234", GameCodes.format("ABC234"))
        assertEquals("ABC-234", GameCodes.format("abc-234"))
        assertEquals("", GameCodes.format("no"))
    }

    @Test
    fun alphabetHasNoLookalikes() {
        assertEquals(31, GameCodes.ALPHABET.length)
        assertFalse(GameCodes.ALPHABET.any { it in "ILO01" })
        assertTrue(GameCodes.isValid("XYZ789"))
    }
}
