package com.chronosbvrx.visionplus.ui.games

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GameQrTest {

    @Test
    fun thePayloadOpensTheJoinDeepLink() {
        assertEquals("visionplus://play/join?code=ABC234", qrPayload("abc-234"))
        assertEquals("visionplus://play/join?code=XYZ789", qrPayload("XYZ789"))
    }

    @Test
    fun theQrIsSquareAndHasBothColors() {
        val bitmap = qrBitmap(qrPayload("ABC234"), 240)
        assertNotNull(bitmap)
        bitmap as Bitmap
        assertEquals(240, bitmap.width)
        assertEquals(240, bitmap.height)
        var black = 0
        var white = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                when (bitmap.getPixel(x, y)) {
                    Color.BLACK -> black++
                    Color.WHITE -> white++
                }
            }
        }
        assertTrue("faltan módulos negros", black > 500)
        assertTrue("falta el fondo blanco", white > 500)
    }

    @Test
    fun theQrAcceptsAReasonableSize() {
        val bitmap = qrBitmap(qrPayload("ABC234"), 120)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width in 240..1200) // se acota para no reventar la TV
    }
}
