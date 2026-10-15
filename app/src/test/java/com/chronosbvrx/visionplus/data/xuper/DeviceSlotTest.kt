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

    @Test
    fun sharedSnIsDetected() {
        // El SN compartido de siempre NO es identidad propia (una sola sesión por
        // SN en el portal: quien lo usa sufre patadas aaa100083).
        assertTrue(DeviceSlot(sn = "2392fe5186d83d73508a2c8c60a26ef6").isShared())
        assertTrue(DeviceSlot(sn = " 2392FE5186D83D73508A2C8C60A26EF6 ").isShared())
    }

    @Test
    fun derivedSnIsNotShared() {
        assertFalse(DeviceSlot(sn = "a3df93ab9276c11341e5cb3176a78a94", userId = "969752184").isShared())
        assertFalse(DeviceSlot(sn = "SN-PROP-1", userId = "U1").isShared())
        assertFalse(DeviceSlot(sn = "").isShared())
    }
}
