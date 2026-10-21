package com.chronosbvrx.visionplus.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodeTest {

    @Test
    fun normalizesDashesSpacesAndCase() {
        assertEquals("ABCDEFGH", normalizeSyncCode(" abcd-efgh "))
        assertEquals("ABCDEFGH", normalizeSyncCode("ABCD EFGH"))
        assertEquals("", normalizeSyncCode(""))
        assertEquals("", normalizeSyncCode("corto"))
        assertEquals("", normalizeSyncCode("con!simbolo"))
    }

    @Test
    fun generatesEightUnambiguousCharacters() {
        val random = java.util.Random(7)
        val code = generateSyncCode(random)
        assertEquals(8, code.length)
        assertTrue(code.all { it in SYNC_ALPHABET })
    }

    @Test
    fun formatsInGroupsOfFour() {
        assertEquals("ABCD-EFGH", formatSyncCode("ABCDEFGH"))
        assertEquals("ABCD-EFGH-JK", formatSyncCode("ABCDEFGHJK"))
    }
}
