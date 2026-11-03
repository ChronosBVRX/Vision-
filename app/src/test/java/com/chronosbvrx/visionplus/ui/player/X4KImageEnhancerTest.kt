package com.chronosbvrx.visionplus.ui.player

import android.content.Context
import android.graphics.ColorMatrix
import android.view.TextureView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El Nivel 1 de imagen es una matriz de color real sobre la TextureView del motor. Estos
 * guardianes fijan lo que promete: negros más profundos, medios sin lavar, saturación
 * controlada y una salida limpia cuando el usuario elige «Original».
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class X4KImageEnhancerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun originalMeansNoMatrix() {
        assertNull(X4KImageEnhancer.colorMatrix(X4KImagePreset.OFF))
        assertNull(X4KImageEnhancer.colorMatrix(X4KImagePreset.CINE, strength = 0f))
    }

    @Test
    fun cineDeepensShadowsWithoutWashingMidtones() {
        val matrix = X4KImageEnhancer.colorMatrix(X4KImagePreset.CINE)!!
        val black = gray(matrix, 0f)
        val shadow = gray(matrix, 16f)
        val middle = gray(matrix, 128f)

        assertEquals(0f, black, 0.01f)
        assertTrue("las sombras bajan de 16", shadow < 16f)
        assertTrue("el gris medio no se lava", middle in 124f..134f)
    }

    @Test
    fun nightIsStrongerThanCineInTheShadows() {
        val cine = gray(X4KImageEnhancer.colorMatrix(X4KImagePreset.CINE)!!, 16f)
        val night = gray(X4KImageEnhancer.colorMatrix(X4KImagePreset.NIGHT)!!, 16f)
        assertTrue("Noche debe apretar más que Cine", night < cine)
    }

    @Test
    fun vividRaisesSaturation() {
        val matrix = X4KImageEnhancer.colorMatrix(X4KImagePreset.VIVID)!!
        val values = matrix.array
        // La diagonal de saturación sube y las cruces bajan respecto a la identidad.
        assertTrue(values[0] > 1f)
        assertTrue(values[1] < 0f)
    }

    @Test
    fun strengthScalesTheGrade() {
        val soft = X4KImageEnhancer.colorMatrix(X4KImagePreset.CINE, strength = 0.5f)!!.array
        val full = X4KImageEnhancer.colorMatrix(X4KImagePreset.CINE, strength = 1f)!!.array
        assertTrue("media fuerza baja menos el negro", soft[4] > full[4])
    }

    @Test
    fun theEngineTextureViewIsFoundInsideThePlayerContainer() {
        val root = FrameLayout(context)
        val texture = TextureView(context)
        root.addView(texture, FrameLayout.LayoutParams(1920, 801))
        assertNotNull(X4KImageEnhancer.findTextureView(root))
        assertEquals(texture, X4KImageEnhancer.findTextureView(root))
    }

    @Test
    fun applyingAndClearingTheFilterIsSafe() {
        val root = FrameLayout(context)
        root.addView(TextureView(context), FrameLayout.LayoutParams(1920, 801))
        assertEquals(X4KImageEnhancer.ApplyResult.APPLIED, X4KImageEnhancer.apply(root, X4KImagePreset.CINE))
        assertEquals(X4KImageEnhancer.ApplyResult.OFF, X4KImageEnhancer.apply(root, X4KImagePreset.OFF))
    }

    @Test
    fun theNativePlayerWiresThePillAndTheEnhancer() {
        val player = source("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        val settings = source("src/main/java/com/chronosbvrx/visionplus/ui/settings/VisionSettings.kt")
        assertTrue(player.contains("showImagePicker"))
        assertTrue(player.contains("imageBtn"))
        assertTrue(player.contains("X4KImageEnhancer.apply"))
        assertTrue(player.contains("setOnHierarchyChangeListener"))
        assertTrue(player.contains("scheduleImageEnhancement()"))
        assertTrue("imagen y pantalla comparten un solo menú", player.contains("Imagen y pantalla"))
        assertTrue("la proporción se aplica desde el mismo menú", player.contains("applyAspect("))
        assertTrue("Pantalla ya no es una píldora aparte", !player.contains("pill(\"Pantalla\""))
        assertTrue(settings.contains("KEY_IMAGE_PRESET"))
        assertTrue(settings.contains("imagePresetOf("))
        assertTrue(settings.contains("setImagePreset("))
    }

    private fun gray(matrix: ColorMatrix, value: Float): Float {
        val values = matrix.array
        // Entrada gris: R=G=B=value. La fila roja resume la salida roja.
        val out = values[0] * value + values[1] * value + values[2] * value + values[4]
        return out.coerceIn(0f, 255f)
    }

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("../$relative"), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("No encontré $relative desde ${File(".").absolutePath}")
    }
}
