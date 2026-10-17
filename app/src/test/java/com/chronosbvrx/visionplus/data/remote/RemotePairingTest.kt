package com.chronosbvrx.visionplus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** El emparejamiento: PIN de 4 dígitos la primera vez, token permanente después. */
class RemotePairingTest {

    /** Generador siempre cero: prueba que el PIN conserva los ceros a la izquierda. */
    private class ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    @Test
    fun pinIsAlwaysFourDigitsEvenWithLeadingZeros() {
        val pin = RemotePairing.newPin(ZeroRandom())
        assertEquals("0000", pin)
        assertEquals(RemotePairing.PIN_LEN, pin.length)
        assertTrue("el PIN debe ser solo dígitos: $pin", pin.all { it.isDigit() })
    }

    @Test
    fun pinMatchingNormalizesSpacesAndDigits() {
        assertTrue(RemotePairing.pinMatches("0342", "0342"))
        assertTrue(RemotePairing.pinMatches("0342", " 0 3 4 2 "))
        assertFalse(RemotePairing.pinMatches("0342", "0343"))
        assertFalse(RemotePairing.pinMatches("0342", ""))
        assertFalse(RemotePairing.pinMatches("0342", null))
        // Un PIN de sesión vacío jamás debe coincidir con nada.
        assertFalse(RemotePairing.pinMatches("", "0042"))
    }

    @Test
    fun tokenIsValidOnlyWhenTheTvRemembersIt() {
        val tokens = listOf("aa", "bb")
        assertTrue(RemotePairing.isValidToken(tokens, "aa"))
        assertFalse(RemotePairing.isValidToken(tokens, "cc"))
        assertFalse(RemotePairing.isValidToken(tokens, null))
        assertFalse(RemotePairing.isValidToken(emptyList(), "aa"))
    }

    @Test
    fun newTokenIsStableLengthHex() {
        val token = RemotePairing.newToken(Random(seed = 42))
        assertEquals(32, token.length)
        assertTrue(token.all { it in "0123456789abcdef" })
        // Y con bytes en cero también son 32 hex (nada de perder los ceros).
        assertEquals("0".repeat(32), RemotePairing.newToken(ZeroRandom()))
    }

    @Test
    fun addTokenMovesToFrontAndDropsTheOldest() {
        val list = RemotePairing.addToken(listOf("a", "b"), "c")
        assertEquals(listOf("c", "a", "b"), list)
        // Re-alta del que ya está al frente: sin cambios ni duplicados.
        assertEquals(listOf("c", "a", "b"), RemotePairing.addToken(listOf("c", "a", "b"), "c"))
        // Re-alta de uno en medio: sube al frente y no se duplica.
        assertEquals(listOf("b", "c", "a"), RemotePairing.addToken(listOf("c", "a", "b"), "b"))
        // Tope: se suelta el más viejo.
        val capped = RemotePairing.addToken(listOf("1", "2", "3"), "4", max = 3)
        assertEquals(listOf("4", "1", "2"), capped)
        // Token vacío no entra.
        assertEquals(listOf("a"), RemotePairing.addToken(listOf("a"), "  "))
    }

    @Test
    fun removeTokenForgetsOnlyThatPhone() {
        assertEquals(listOf("b", "c"), RemotePairing.removeToken(listOf("a", "b", "c"), "a"))
        assertEquals(listOf("a", "c"), RemotePairing.removeToken(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a", "b", "c"), RemotePairing.removeToken(listOf("a", "b", "c"), "z"))
    }
}
