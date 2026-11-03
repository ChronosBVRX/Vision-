package com.chronosbvrx.visionplus.data.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameDeepLinkTest {

    @Test
    fun parsesTheJoinLink() {
        assertEquals(
            "ABC234",
            GameDeepLink.joinCodeFrom("play", listOf("join"), "abc-234"),
        )
        assertEquals(
            "XYZ789",
            GameDeepLink.joinCodeFrom("PLAY", listOf("JOIN"), "XYZ-789"),
        )
    }

    @Test
    fun readsTheQrPayloadAndThePlainCode() {
        assertEquals("ABC234", GameDeepLink.codeFromScan("visionplus://play/join?code=ABC-234"))
        assertEquals("ABC234", GameDeepLink.codeFromScan("https://visionplusv2.duckdns.org/x?code=ABC234&x=1"))
        assertEquals("ABC234", GameDeepLink.codeFromScan("  ABC-234  "))
        assertNull(GameDeepLink.codeFromScan("https://example.com/sin-codigo"))
        assertNull(GameDeepLink.codeFromScan("ABC123"))
        assertNull(GameDeepLink.codeFromScan(""))
    }

    @Test
    fun rejectsOtherHostsAndPaths() {
        assertNull(GameDeepLink.joinCodeFrom("watch", listOf("join"), "ABC234"))
        assertNull(GameDeepLink.joinCodeFrom("play", listOf("open"), "ABC234"))
        assertNull(GameDeepLink.joinCodeFrom("play", emptyList(), "ABC234"))
        assertNull(GameDeepLink.joinCodeFrom("play", listOf("join"), null))
        assertNull(GameDeepLink.joinCodeFrom("play", listOf("join"), "ABC123"))
    }
}
