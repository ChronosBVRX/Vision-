package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Cuántas filas del catálogo tienen que caber a la vez en la tele. La altura de la tarjeta se
 * calcula desde la altura REAL de la pantalla (una TV 1080p a 320 dpi reporta 540 dp, donde las
 * tarjetas fijas de 140 dp dejaban ver solo 2 filas).
 */
internal const val TV_VISIBLE_CATALOG_ROWS = 3

/** Aire que se deja libre para que la tercera fila no quede pegada al borde inferior. */
private const val ROW_SAFETY_DP = 6f

/** Debajo de esta altura el Inicio usa la densidad compacta (TV 1080p/720p típicas: 540 dp). */
private const val COMPACT_HEIGHT_DP = 700f

/**
 * Medidas de una fila del catálogo de TV. [rowHeight] es exactamente lo que ocupa la fila en la
 * columna (etiqueta + póster + título + aire), así que la suma de [TV_VISIBLE_CATALOG_ROWS] filas
 * nunca rebasa la pantalla.
 */
internal data class TvRowMetrics(
    val compact: Boolean,
    val cardWidth: Dp,
    val topPadding: Dp,
    val labelHeight: Dp,
    val titleGap: Dp,
    val titleHeight: Dp,
    val bottomGap: Dp,
) {
    /** Alto del póster 2:3 derivado del ancho (la proporción no se toca). */
    val cardHeight: Dp get() = cardWidth * 1.5f

    val rowHeight: Dp
        get() = topPadding + labelHeight + cardHeight + titleGap + titleHeight + bottomGap
}

/**
 * Calcula las medidas de fila para [screenHeightDp]. En pantallas cortas (la TV de 1080p reporta
 * 540 dp) achica el póster y aprieta los márgenes lo justo para que entren 3 filas completas; en
 * pantallas altas conserva el póster de 140 dp de siempre. Devuelve medidas puras: los tests las
 * verifican sin componer.
 */
internal fun tvRowMetrics(screenHeightDp: Int): TvRowMetrics {
    val height = screenHeightDp.toFloat().coerceIn(360f, 1600f)
    val compact = height < COMPACT_HEIGHT_DP
    val top = if (compact) 8f else 14f
    val label = 26f
    val titleGap = if (compact) 2f else 5f
    val title = if (compact) 16f else 20f
    val bottom = if (compact) 4f else 10f
    val chrome = top + label + titleGap + title + bottom
    val perRow = (height - ROW_SAFETY_DP) / TV_VISIBLE_CATALOG_ROWS
    // El póster es 2:3; el ancho se redondea hacia abajo para que el alto real nunca exceda
    // el presupuesto de la fila. Los topes conservan el tamaño de siempre en pantallas grandes
    // y evitan pósters de juguete si algún equipo reporta una altura absurda.
    val posterWidth = ((perRow - chrome) * 2f / 3f).coerceIn(60f, 140f)
    return TvRowMetrics(
        compact = compact,
        cardWidth = floor(posterWidth).dp,
        topPadding = top.dp,
        labelHeight = label.dp,
        titleGap = titleGap.dp,
        titleHeight = title.dp,
        bottomGap = bottom.dp,
    )
}

/** Las medidas de fila de esta pantalla, recalculadas solo si cambia la altura disponible. */
internal const val TV_TOP_BAR_DP = 62

@Composable
internal fun rememberTvRowMetrics(): TvRowMetrics {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return remember(screenHeightDp) {
        // La barra superior se lleva su alto: las filas se calculan sobre lo que queda.
        tvRowMetrics((screenHeightDp - TV_TOP_BAR_DP).coerceAtLeast(360))
    }
}
