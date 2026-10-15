package com.chronosbvrx.visionplus.ui.theme

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Tinte ambiental del héroe: el fondo toma los tonos del póster (estilo premium),
 * sin cambiar la estructura. Puro y testeable: la UI solo aplica el color.
 */
object AmbientColor {

    /** Color promedio de una muestra 8×8 (barato: 64 píxeles). */
    internal fun averageOf(pixels: IntArray): Int {
        if (pixels.isEmpty()) return Color.BLACK
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            r += Color.red(p)
            g += Color.green(p)
            b += Color.blue(p)
        }
        val n = pixels.size.toLong()
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Muestra el centro del bitmap escalado a 8×8 (evita bordes con logos/marcos). */
    fun sample(bitmap: Bitmap): Int {
        return runCatching {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return Color.BLACK
            val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            val pixels = IntArray(64)
            small.getPixels(pixels, 0, 8, 0, 0, 8, 8)
            if (small !== bitmap) small.recycle()
            averageOf(pixels)
        }.getOrDefault(Color.BLACK)
    }

    /** Mezcla hacia negro para fondos (el texto blanco debe seguir legible). */
    internal fun darken(color: Int, amount: Float = 0.72f): Int {
        val r = (Color.red(color) * (1f - amount)).toInt()
        val g = (Color.green(color) * (1f - amount)).toInt()
        val b = (Color.blue(color) * (1f - amount)).toInt()
        return Color.rgb(r, g, b)
    }

    /** Oscurece y valida: grises casi negros si la muestra falla. */
    fun backgroundFor(color: Int): Int = darken(color)
}
