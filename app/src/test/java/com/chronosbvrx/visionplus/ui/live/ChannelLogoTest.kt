package com.chronosbvrx.visionplus.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Los logos de los canales del proveedor miden ~90×60 px y se dibujaban al tamaño de la tarjeta
 * (3-4× de aumento): se veían borrosos, enormes, y los que ya vienen recortados parecían rotos.
 * `logoDisplaySize` decide el tamaño real respetando la proporción y sin pasar de `maxUpscale`.
 */
class ChannelLogoTest {

    @Test
    fun aSmallProviderLogoIsNotStretchedToTheCard() {
        // 90×60 px en un teléfono a 2.75 de densidad = 32.7×21.8 dp, en una tarjeta de 134×89 dp.
        val boxW = 134f
        val boxH = 89f
        val (w, h) = logoDisplaySize(
            intrinsicW = 90f / 2.75f,
            intrinsicH = 60f / 2.75f,
            boxW = boxW,
            boxH = boxH,
            maxUpscale = LOGO_MAX_UPSCALE,
        )
        assertEquals(32.727f * LOGO_MAX_UPSCALE, w, 0.05f)
        assertEquals(21.818f * LOGO_MAX_UPSCALE, h, 0.05f)
        assertTrue("no puede llenar la tarjeta", w < boxW * 0.75f)
        assertTrue("ni tocarla por alto", h < boxH * 0.75f)
    }

    @Test
    fun aBigLogoStillFillsTheAvailableBox() {
        val (w, h) = logoDisplaySize(200f, 120f, boxW = 134f, boxH = 89f, maxUpscale = LOGO_MAX_UPSCALE)
        assertEquals("se limita al ancho disponible", 134f, w, 0.05f)
        assertEquals("conserva la proporción (134 / 200 * 120)", 80.4f, h, 0.05f)
    }

    @Test
    fun theAspectRatioIsAlwaysKept() {
        val (w, h) = logoDisplaySize(90f, 60f, 400f, 400f, 2.5f)
        assertEquals(1.5f, w / h, 0.001f)
    }

    @Test
    fun unknownSizesFallBackToTheWholeBox() {
        assertEquals(100f to 50f, logoDisplaySize(0f, 0f, 100f, 50f))
        // Una caja sin ancho no puede inventar tamaño: devuelve la caja tal cual.
        assertEquals(0f to 50f, logoDisplaySize(90f, 60f, 0f, 50f))
    }

    // --- Cableado: las dos pantallas de TV en vivo usan la pieza compartida ---

    @Test
    fun bothLiveScreensUseTheSharedLogo() {
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/phone/LiveScreen.kt")
        assertTrue("la tarjeta móvil pinta el logo con ChannelLogo", phone.contains("ChannelLogo("))
        assertTrue("y ya no pide el póster agrandado a 480×270", !phone.contains(".size(480, 270)"))

        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvLiveParts.kt")
        assertTrue("la fila de la tele pinta el logo con ChannelLogo", tv.contains("ChannelLogo("))
        assertTrue("y el preview también", tv.contains("maxUpscale = LOGO_PREVIEW_UPSCALE"))
        assertTrue("ya no quedar logos dibujados a mano", !tv.contains(".size(640, 360)"))
    }

    private fun requireSource(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
