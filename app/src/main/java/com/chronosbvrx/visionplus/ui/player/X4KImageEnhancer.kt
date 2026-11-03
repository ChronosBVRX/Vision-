package com.chronosbvrx.visionplus.ui.player

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import java.util.EnumMap
import java.util.WeakHashMap

/**
 * Nivel 1 de mejora de imagen para el reproductor nativo X4K.
 *
 * El motor X4K dibuja el vídeo en una `TextureView` (`ed.c`) dentro del contenedor `ed.e`.
 * Una `TextureView` es una vista normal: su `Paint` de capa acepta un `ColorMatrixColorFilter`
 * y Android lo aplica al contenido del vídeo antes de componerlo. No es un velo translúcido:
 * cambia el contraste, el punto de negro, la saturación y el tono de cada píxel.
 *
 * No se toca el motor, sus DEX ni sus licencias. Si el motor cambiara a `SurfaceView`, esta
 * vía no aplica y se devuelve [ApplyResult.NO_TEXTURE_VIEW] para que la app no prometa de más.
 */
internal enum class X4KImagePreset(
    val key: String,
    val label: String,
    val detail: String,
) {
    CINE("cine", "Cine", "Negros más profundos, contraste suave y color cálido"),
    NITIDO("nitido", "Nítido", "Definición y detalle para fuentes suaves"),
    NIGHT("night", "Noche", "Más contraste para escenas oscuras"),
    VIVID("vivid", "Vivo", "Más saturación y luminosidad"),
    CALIDO("calido", "Cálido", "Tono más cálido, ideal para piel y exteriores"),
    FRIO("frio", "Frío", "Tono más neutro/frío para ciencia ficción"),
    OFF("off", "Original", "Sin procesado de imagen"),

    ;

    companion object {
        /** Orden del menú: primero las mejoras, al final la salida limpia. */
        val menuOrder: List<X4KImagePreset> =
            listOf(CINE, NITIDO, NIGHT, VIVID, CALIDO, FRIO, OFF)

        fun fromKey(raw: String?): X4KImagePreset {
            val clean = raw.orEmpty().trim().lowercase()
            return values().firstOrNull { it.key == clean } ?: OFF
        }
    }
}

internal object X4KImageEnhancer {
    private const val TAG = "X4KImageEnhancer"

    // El listener de jerarquía puede llamar muchas veces durante el arranque. Reutilizar
    // el Paint y no volver a tocar la capa si el preset no cambió evita invalidaciones
    // innecesarias que, en equipos modestos, pueden costar frames y desfasar el audio.
    private val paints = EnumMap<X4KImagePreset, Paint>(X4KImagePreset::class.java)
    private val applied = WeakHashMap<TextureView, X4KImagePreset>()

    enum class ApplyResult {
        APPLIED,
        OFF,
        NO_TEXTURE_VIEW,
        FAILED,
    }

    /** Aplica (o quita) el filtro sobre la primera/gran `TextureView` del contenedor del motor. */
    fun apply(root: View?, preset: X4KImagePreset): ApplyResult {
        if (root == null) return ApplyResult.NO_TEXTURE_VIEW
        val texture = findTextureView(root) ?: return ApplyResult.NO_TEXTURE_VIEW
        if (preset == X4KImagePreset.OFF) {
            if (applied.remove(texture) == null) return ApplyResult.OFF
            return runCatching {
                texture.setLayerPaint(null)
                ApplyResult.OFF
            }.getOrElse {
                Log.w(TAG, "no se pudo quitar el filtro: ${it.javaClass.simpleName}: ${it.message}")
                ApplyResult.FAILED
            }
        }
        if (applied[texture] == preset) return ApplyResult.APPLIED
        val matrix = colorMatrix(preset) ?: return ApplyResult.OFF
        val paint = paints.getOrPut(preset) {
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
                isAntiAlias = true
                isDither = true
                isFilterBitmap = true
            }
        }
        return runCatching {
            texture.setLayerPaint(paint)
            applied[texture] = preset
            ApplyResult.APPLIED
        }.getOrElse {
            applied.remove(texture)
            Log.w(TAG, "no se pudo aplicar ${preset.key}: ${it.javaClass.simpleName}: ${it.message}")
            ApplyResult.FAILED
        }
    }

    /**
     * Busca la `TextureView` del vídeo. En el contenedor del motor suele ser la única, pero
     * se elige la de mayor área para no colar el filtro en una vista de anuncio o de espera.
     */
    internal fun findTextureView(root: View): TextureView? {
        val found = ArrayList<TextureView>(2)
        collectTextureViews(root, found)
        if (found.isEmpty()) return null
        return found.maxByOrNull { view ->
            view.width.toLong() * view.height.toLong()
        }
    }

    private fun collectTextureViews(view: View, out: MutableList<TextureView>) {
        if (view is TextureView) {
            out.add(view)
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTextureViews(view.getChildAt(index), out)
            }
        }
    }

    /**
     * Matriz 4x5 de Android. Los offsets van en la escala 0..255.
     *
     * Se construye a mano para que el orden sea predecible: saturación -> contraste/punto
     * de negro -> ganancia por canal. `strength` permite bajar la intensidad sin cambiar de
     * preset (0 = identidad, 1 = preset completo).
     */
    internal fun colorMatrix(
        preset: X4KImagePreset,
        strength: Float = 1f,
    ): ColorMatrix? {
        if (preset == X4KImagePreset.OFF || strength <= 0f) return null
        val grade = gradeFor(preset)
        val s = strength.coerceIn(0f, 1f)
        val sat = 1f + (grade.saturation - 1f) * s
        val contrast = 1f + (grade.contrast - 1f) * s
        val black = grade.blackOffset * s
        val red = 1f + (grade.red - 1f) * s
        val green = 1f + (grade.green - 1f) * s
        val blue = 1f + (grade.blue - 1f) * s

        val inverse = 1f - sat
        val sr = 0.213f * inverse
        val sg = 0.715f * inverse
        val sb = 0.072f * inverse
        val values = floatArrayOf(
            contrast * (sr + sat) * red, contrast * sg * red, contrast * sb * red, 0f, black * red,
            contrast * sr * green, contrast * (sg + sat) * green, contrast * sb * green, 0f, black * green,
            contrast * sr * blue, contrast * sg * blue, contrast * (sb + sat) * blue, 0f, black * blue,
            0f, 0f, 0f, 1f, 0f,
        )
        return ColorMatrix(values)
    }

    internal data class Grade(
        val saturation: Float,
        val contrast: Float,
        val blackOffset: Float,
        val red: Float = 1f,
        val green: Float = 1f,
        val blue: Float = 1f,
        /** Solo se usa en el renderer GL/AGSL: la matriz de color no puede hacer nitidez. */
        val sharpness: Float = 0f,
        /** Dither/deband suave en el renderer GL/AGSL. */
        val deband: Float = 0f,
        /** Curva S suave en el renderer GL/AGSL. */
        val curve: Float = 0f,
    )

    internal fun gradeFor(preset: X4KImagePreset): Grade = when (preset) {
        // Deliberadamente suave: la tele ya hace su propio procesado y no queremos aplastar
        // las sombras de una película bien masterizada.
        X4KImagePreset.CINE -> Grade(
            saturation = 1.05f,
            contrast = 1.05f,
            blackOffset = -5f,
            red = 1.005f,
            green = 1f,
            blue = 0.995f,
            sharpness = 0.08f,
            deband = 0.35f,
            curve = 0.10f,
        )
        X4KImagePreset.NITIDO -> Grade(
            saturation = 1.03f,
            contrast = 1.04f,
            blackOffset = -3f,
            sharpness = 0.26f,
            deband = 0.20f,
            curve = 0.04f,
        )
        X4KImagePreset.NIGHT -> Grade(
            saturation = 0.98f,
            contrast = 1.09f,
            blackOffset = -8f,
            red = 0.995f,
            green = 1f,
            blue = 1.005f,
            sharpness = 0.05f,
            deband = 0.55f,
            curve = 0.18f,
        )
        X4KImagePreset.VIVID -> Grade(
            saturation = 1.12f,
            contrast = 1.03f,
            blackOffset = -2f,
            sharpness = 0.12f,
            deband = 0.15f,
            curve = 0.06f,
        )
        X4KImagePreset.CALIDO -> Grade(
            saturation = 1.04f,
            contrast = 1.03f,
            blackOffset = -2f,
            red = 1.045f,
            green = 1.005f,
            blue = 0.955f,
            sharpness = 0.06f,
            deband = 0.20f,
            curve = 0.05f,
        )
        X4KImagePreset.FRIO -> Grade(
            saturation = 1.02f,
            contrast = 1.04f,
            blackOffset = -3f,
            red = 0.965f,
            green = 1f,
            blue = 1.045f,
            sharpness = 0.07f,
            deband = 0.20f,
            curve = 0.05f,
        )
        X4KImagePreset.OFF -> Grade(1f, 1f, 0f)
    }
}
