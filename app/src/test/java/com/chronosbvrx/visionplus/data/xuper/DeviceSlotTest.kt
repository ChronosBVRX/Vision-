package com.chronosbvrx.visionplus.data.xuper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Réplica Hydra: el slot propio (sn + user_id) decide la identidad; sin slot,
 * la app sigue con la compartida. Cada normalización firma su forma.
 */
class DeviceSlotTest {

    @Test
    fun validSlotSurvives() {
        val slot = DeviceSlot(sn = "SN-PROP-1", userId = "U1")
        assertTrue(slot.isValid())
    }

    @Test
    fun blankSnIsNoSlot() {
        assertFalse(DeviceSlot(sn = "  ").isValid())
        assertFalse(DeviceSlot(sn = "").isValid())
    }

    @Test
    fun normalizeCodeIsStrict() {
        assertEquals("AB12CD34", DeviceSlot.normalizeCode(" ab12 cd34 "))
        assertEquals("", DeviceSlot.normalizeCode("corto"))
        assertEquals("", DeviceSlot.normalizeCode("con-guion1"))
        assertEquals("", DeviceSlot.normalizeCode(null))
    }

    @Test
    fun normalizeDeviceKeyIsStrict() {
        assertEquals("abc-123xy", DeviceSlot.normalizeDeviceKey(" AbC-123_xy! "))
        assertEquals("", DeviceSlot.normalizeDeviceKey(""))
        assertEquals("", DeviceSlot.normalizeDeviceKey(null))
    }

    @Test
    fun parseResponseReadsRedeemAndClaim() {
        val slot = DeviceSlot.parseResponse("""{"sn":"SN-X","user_id":"U-X"}""")
        assertEquals(DeviceSlot("SN-X", "U-X"), slot)
    }

    @Test
    fun parseResponseRejectsGarbage() {
        assertNull(DeviceSlot.parseResponse("""{"sn":""}"""))
        assertNull(DeviceSlot.parseResponse("no-json"))
        assertNull(DeviceSlot.parseResponse(""))
    }
}
