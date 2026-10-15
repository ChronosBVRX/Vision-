package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La identidad efectiva: con slot manda el propio; sin slot, la compartida de
 * siempre (ningún equipo existente cambia de comportamiento al actualizar).
 */
class X4KApiSlotTest {

    @Test
    fun withoutSlotUsesTheSharedIdentity() {
        X4KApi.slotSn = null
        X4KApi.slotUserId = null
        try {
            assertEquals(X4KApi.LEGACY_USER_ID, X4KApi.userIdOf())
            assertEquals("2392fe5186d83d73508a2c8c60a26ef6", X4KApi.snOf())
        } finally {
            X4KApi.slotSn = null
            X4KApi.slotUserId = null
        }
    }

    @Test
    fun withSlotUsesItsOwnIdentity() {
        X4KApi.slotSn = "SN-PROPIO-1"
        X4KApi.slotUserId = "U-PROPIA-1"
        try {
            assertEquals("SN-PROPIO-1", X4KApi.snOf())
            assertEquals("U-PROPIA-1", X4KApi.userIdOf())
        } finally {
            X4KApi.slotSn = null
            X4KApi.slotUserId = null
        }
    }

    @Test
    fun derivedSnMatchesThePortalVector() {
        // Vector medido contra el portal: MD5("557dc675-…-911b293789ec" + SALT).
        assertEquals(
            "61c3c10ffbe8e07e8e57695d7968ddfe",
            X4KApi.deriveSn("557dc675-2217-47c7-aea8-911b293789ec"),
        )
    }

    @Test
    fun blankSlotFallsBackToShared() {        X4KApi.slotSn = "  "
        X4KApi.slotUserId = ""
        try {
            assertEquals("2392fe5186d83d73508a2c8c60a26ef6", X4KApi.snOf())
            assertEquals(X4KApi.LEGACY_USER_ID, X4KApi.userIdOf())
        } finally {
            X4KApi.slotSn = null
            X4KApi.slotUserId = null
        }
    }

    @Test
    fun liveStubsRebuildEntriesFromCodes() {
        val arr = X4KApi.liveStubsForCodes(listOf("cyx-AMC", "ST☆R+HD_cyx", "  "))
        assertEquals(2, arr.length())
        assertEquals("cyx-AMC", arr.optJSONObject(0)?.optString("channelCode"))
        assertEquals("cyx-AMC", arr.optJSONObject(0)?.optString("name"))
        // El nombre camuflado se traduce igual que en el catálogo.
        assertEquals("ESPN / Star+ HD", arr.optJSONObject(1)?.optString("name"))
    }

    @Test
    fun liveStubsEmptyWithoutCodes() {
        assertEquals(0, X4KApi.liveStubsForCodes(emptyList()).length())
        assertEquals(0, X4KApi.liveStubsForCodes(listOf("   ")).length())
    }
}
