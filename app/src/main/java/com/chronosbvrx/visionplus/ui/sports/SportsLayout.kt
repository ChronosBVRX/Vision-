package com.chronosbvrx.visionplus.ui.sports

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Tamaños de la agenda de Deportes calculados por VENTANA, no por aparato.
 *
 * Un teléfono horizontal y una tablet comparten el mismo código: manda el ancho
 * disponible. La tablet se reconoce por su lado menor (`smallestWidthDp >= 600`),
 * que no cambia al girar; la orientación solo decide qué tan generosa es la rejilla.
 *
 * El teléfono usa una tarjeta compacta (alto 126 dp) y la tablet sube tipografía,
 * márgenes y alto de tarjeta sin llegar nunca a los tamaños de la TV.
 */
internal data class SportsLayout(
    val horizontalPadding: Float,
    val gridMinCardWidth: Float,
    val gridGap: Float,
    val gridTopPadding: Float,
    val cardHeight: Float,
    val cardPaddingH: Float,
    val cardPaddingV: Float,
    val cardRadius: Float,
    /** Escala interna de la tarjeta (texto, escudos e iconos). */
    val cardScale: Float,
    val titleSize: Float,
    val subtitleSize: Float,
    val sectionTitleSize: Float,
    val chipHeight: Float,
    val chipTextSize: Float,
    val chipIconSize: Float,
    val chipGap: Float,
    val chipRowGap: Float,
    val headerVerticalPadding: Float,
    val sectionTopPadding: Float,
)

internal fun sportsLayout(
    heightDp: Int,
    smallestWidthDp: Int,
    isLandscape: Boolean,
): SportsLayout {
    val tablet = smallestWidthDp >= 600
    // Horizontal de teléfono: manda la poca altura, no el ancho.
    val short = heightDp < 520
    return when {
        tablet && isLandscape -> SportsLayout(
            horizontalPadding = 32f,
            gridMinCardWidth = 192f,
            gridGap = 16f,
            gridTopPadding = 8f,
            cardHeight = 150f,
            cardPaddingH = 16f,
            cardPaddingV = 12f,
            cardRadius = 17f,
            cardScale = 1.15f,
            titleSize = 26f,
            subtitleSize = 15f,
            sectionTitleSize = 20f,
            chipHeight = 42f,
            chipTextSize = 15f,
            chipIconSize = 16f,
            chipGap = 10f,
            chipRowGap = 10f,
            headerVerticalPadding = 12f,
            sectionTopPadding = 14f,
        )
        tablet -> SportsLayout(
            horizontalPadding = 24f,
            gridMinCardWidth = 170f,
            gridGap = 12f,
            gridTopPadding = 6f,
            cardHeight = 142f,
            cardPaddingH = 14f,
            cardPaddingV = 10f,
            cardRadius = 16f,
            cardScale = 1.08f,
            titleSize = 24f,
            subtitleSize = 14f,
            sectionTitleSize = 18f,
            chipHeight = 40f,
            chipTextSize = 15f,
            chipIconSize = 15f,
            chipGap = 8f,
            chipRowGap = 8f,
            headerVerticalPadding = 10f,
            sectionTopPadding = 12f,
        )
        else -> SportsLayout(
            horizontalPadding = 16f,
            gridMinCardWidth = 148f,
            gridGap = 8f,
            gridTopPadding = if (short) 2f else 4f,
            cardHeight = 126f,
            cardPaddingH = 10f,
            cardPaddingV = 8f,
            cardRadius = 13f,
            cardScale = 0.97f,
            titleSize = 20f,
            subtitleSize = 12f,
            sectionTitleSize = 15f,
            chipHeight = 34f,
            chipTextSize = 13f,
            chipIconSize = 13f,
            chipGap = 6f,
            chipRowGap = if (short) 3f else 5f,
            headerVerticalPadding = if (short) 4f else 8f,
            sectionTopPadding = if (short) 6f else 10f,
        )
    }
}

/**
 * Columnas visibles para un ancho real de contenido.
 *
 * Se usa `BoxWithConstraints`, así que [availableWidthDp] ya descuenta el riel de
 * tablet o una ventana dividida. Nunca pasa de 8: más tarjetas que eso dejan de
 * leerse en una pantalla de 10-13 pulgadas.
 */
internal fun sportsGridColumns(availableWidthDp: Float, layout: SportsLayout): Int {
    val usable = availableWidthDp - layout.horizontalPadding * 2f
    if (usable <= 0f) return 1
    val columns = ((usable + layout.gridGap) / (layout.gridMinCardWidth + layout.gridGap)).toInt()
    return columns.coerceIn(1, 8)
}

/** Tipografía de tarjeta/tarjeta de canal: crece con la ventana, no con el aparato. */
internal fun TextStyle.scaled(size: Float, lineHeight: Float, scale: Float): TextStyle =
    copy(fontSize = (size * scale).sp, lineHeight = (lineHeight * scale).sp)
