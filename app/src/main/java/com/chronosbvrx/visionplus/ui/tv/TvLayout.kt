package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cuántas filas del catálogo tienen que caber a la vez en la tele. La altura de la tarjeta se
 * calcula desde la altura REAL de la pantalla (una TV 1080p a 320 dpi reporta 540 dp, donde las
 * tarjetas fijas de 140 dp dejaban ver solo 2 filas).
 */
internal const val TV_VISIBLE_CATALOG_ROWS = 1

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
    val cardHeight: Dp,
    val topPadding: Dp,
    val labelHeight: Dp,
    val titleGap: Dp,
    val titleHeight: Dp,
    val bottomGap: Dp,
) {
    val rowHeight: Dp
        get() = topPadding + labelHeight + cardHeight + titleGap + titleHeight + bottomGap
}

/**
 * Medidas de una fila de portada premium. El póster manda (148 × 220 dp) y la
 * pantalla desplaza; ya no se encoge para forzar tres filas.
 */
internal fun tvRowMetrics(screenHeightDp: Int): TvRowMetrics {
    val compact = screenHeightDp < COMPACT_HEIGHT_DP
    // Diseño de portada premium: 148 × 220 dp (6–7 pósters por fila en una tele
    // ancha) y 24 dp de aire entre filas. La fila ya no se encoge para meter 3.
    return TvRowMetrics(
        compact = compact,
        cardWidth = 148.dp,
        cardHeight = 220.dp,
        topPadding = 24.dp,
        labelHeight = 30.dp,
        titleGap = 6.dp,
        titleHeight = 40.dp,
        bottomGap = 10.dp,
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
