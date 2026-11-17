package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Plan de la barra superior de la tele, calculado sin Compose.
 *
 * El ancho de la barra no se decide por resolución ni por `smallestScreenWidthDp`: en una TV
 * 16:9, `sw540` suele ser 960×540 dp (720p a tvdpi o 1080p a xhdpi) y `sw720` suele ser
 * 1280×720 dp. La decisión real es **ancho disponible + fontScale**.
 *
 * La prioridad de colapso es la acordada en la auditoría:
 *   1. ocultar el sufijo «TV» de la marca (variantes Compact/Narrow);
 *   2. ocultar el reloj;
 *   3. colapsar «Buscar» a icono;
 *   4. colapsar el nombre del perfil a avatar;
 *   5. (válvula futura) agrupar recarga/ajustes en «⋯»;
 *   6. (válvula futura) agrupar pestañas en «Más».
 *
 * En esta entrega no se usan las válvulas 5/6: las 6 pestañas se conservan y se desplazan.
 * Los anchos son estimaciones tipográficas conservadoras (±5–8 %); la política sirve para
 * comparar escenarios y para que la UI elija variante, no para medir píxeles exactos.
 */
internal enum class TvTopBarTier { FULL, COMPACT, NARROW }

internal enum class TvTopBarBrandMode { FULL, SHORT }

internal enum class TvTopBarProfileMode { NAME, AVATAR }

internal enum class TvTopBarSearchMode { PILL, ICON }

/** Mínimo de ancho que conserva la tira de pestañas antes de aplicar la siguiente válvula. */
internal const val TV_TOP_BAR_MIN_TAB_STRIP_DP = 240f

/** Techo del escalado del chrome; el contenido y el Hero conservan el escalado completo. */
internal const val TV_TOP_BAR_MAX_FONT_SCALE = 1.5f

internal const val TV_TOP_BAR_MIN_FONT_SCALE = 0.85f

internal val TV_TOP_BAR_FULL_HEIGHT = 64.dp
internal val TV_TOP_BAR_COMPACT_HEIGHT = 60.dp
internal val TV_TOP_BAR_NARROW_HEIGHT = 56.dp

internal data class TvTopBarPlan(
    val tier: TvTopBarTier,
    val brand: TvTopBarBrandMode,
    val showClock: Boolean,
    val profile: TvTopBarProfileMode,
    val search: TvTopBarSearchMode,
    val height: Dp,
    val horizontalPadding: Dp,
    val controlHeight: Dp,
    val iconButtonSize: Dp,
    val tabPadding: Dp,
    val tabSpacing: Dp,
    val profileLabelMaxWidth: Dp,
    /** Escala realmente aplicada al chrome (ya recortada a [TV_TOP_BAR_MIN_FONT_SCALE]..[TV_TOP_BAR_MAX_FONT_SCALE]). */
    val fontScale: Float,
    /** Ancho que le queda a la tira de pestañas después de marca + utilidades + reloj/divisor. */
    val tabStripWidth: Float,
    /** ¿Las 6 pestañas caben completas sin desplazarse? */
    val allTabsFit: Boolean,
    /** ¿La tira conserva al menos [TV_TOP_BAR_MIN_TAB_STRIP_DP]? */
    val minTabStripMet: Boolean,
    /** Lo que mediría la barra si las pestañas no se desplazaran (diagnóstico). */
    val estimatedWidth: Float,
)

/** La barra lee el plan desde aquí; así los hijos no arrastran seis parámetros cada uno. */
internal val LocalTvTopBarPlan = compositionLocalOf {
    tvTopBarPlan(widthDp = 960f, fontScale = 1f, profileNameLength = 9)
}

internal fun tvTopBarPlan(
    widthDp: Float,
    fontScale: Float,
    profileNameLength: Int,
    hasBadge: Boolean = false,
): TvTopBarPlan {
    val scale = fontScale.coerceIn(TV_TOP_BAR_MIN_FONT_SCALE, TV_TOP_BAR_MAX_FONT_SCALE)
    val tier = when {
        widthDp >= 1200f -> TvTopBarTier.FULL
        widthDp >= 840f -> TvTopBarTier.COMPACT
        else -> TvTopBarTier.NARROW
    }
    val height = when (tier) {
        TvTopBarTier.FULL -> TV_TOP_BAR_FULL_HEIGHT
        TvTopBarTier.COMPACT -> TV_TOP_BAR_COMPACT_HEIGHT
        TvTopBarTier.NARROW -> TV_TOP_BAR_NARROW_HEIGHT
    }
    val horizontalPadding = when (tier) {
        TvTopBarTier.FULL, TvTopBarTier.COMPACT -> 48.dp
        TvTopBarTier.NARROW -> 40.dp
    }
    val controlHeight = when (tier) {
        TvTopBarTier.FULL, TvTopBarTier.COMPACT -> 44.dp
        TvTopBarTier.NARROW -> 40.dp
    }
    val tabPadding = if (tier == TvTopBarTier.FULL) 11.dp else 9.dp
    val tabSpacing = if (tier == TvTopBarTier.FULL) 4.dp else 3.dp
    val brand = if (tier == TvTopBarTier.FULL) TvTopBarBrandMode.FULL else TvTopBarBrandMode.SHORT
    val brandGap = if (tier == TvTopBarTier.FULL) 20f else 16f

    // En Compact/Narrow el perfil es solo avatar y el reloj no se pinta: el margen se
    // reserva para las pestañas. El nombre completo vive en Full (cap 120 dp) y en el
    // selector de perfiles.
    var showClock = tier == TvTopBarTier.FULL
    var profile = if (tier == TvTopBarTier.FULL) TvTopBarProfileMode.NAME else TvTopBarProfileMode.AVATAR
    var search = if (tier == TvTopBarTier.FULL) TvTopBarSearchMode.PILL else TvTopBarSearchMode.ICON

    fun fixedWidth(): Float {
        val brandText = if (brand == TvTopBarBrandMode.FULL) BRAND_FULL_TEXT_DP else BRAND_SHORT_TEXT_DP
        val brandWidth = brandText * scale + BRAND_TEXT_GAP_DP
        val clockBlock = if (showClock) CLOCK_TEXT_DP * scale + CLOCK_BLOCK_DP else DIVIDER_BLOCK_DP
        val profileExtra = if (profile == TvTopBarProfileMode.NAME) {
            PROFILE_NAME_EXTRA_BASE_DP +
                minOf(PROFILE_LABEL_MAX_DP, profileNameLength.coerceAtLeast(0) * PROFILE_CHAR_DP * scale)
        } else {
            0f
        }
        val searchExtra = if (search == TvTopBarSearchMode.PILL) {
            SEARCH_PILL_EXTRA_BASE_DP + SEARCH_PILL_TEXT_DP * scale
        } else {
            0f
        }
        return horizontalPadding.value * 2f + brandWidth + brandGap + UTILITIES_BASE_DP +
            (if (hasBadge) BADGE_SAFETY_DP else 0f) + clockBlock + profileExtra + searchExtra
    }

    var fixed = fixedWidth()
    var tabStrip = (widthDp - fixed).coerceAtLeast(0f)

    // Full es la única variante que puede permitirse etiquetas; si el presupuesto aprieta,
    // primero cae el reloj, luego la etiqueta de Buscar y por último el nombre del perfil.
    if (tier == TvTopBarTier.FULL) {
        if (tabStrip < TV_TOP_BAR_MIN_TAB_STRIP_DP && showClock) {
            showClock = false
            fixed = fixedWidth()
            tabStrip = (widthDp - fixed).coerceAtLeast(0f)
        }
        if (tabStrip < TV_TOP_BAR_MIN_TAB_STRIP_DP && search == TvTopBarSearchMode.PILL) {
            search = TvTopBarSearchMode.ICON
            fixed = fixedWidth()
            tabStrip = (widthDp - fixed).coerceAtLeast(0f)
        }
        if (tabStrip < TV_TOP_BAR_MIN_TAB_STRIP_DP && profile == TvTopBarProfileMode.NAME) {
            profile = TvTopBarProfileMode.AVATAR
            fixed = fixedWidth()
            tabStrip = (widthDp - fixed).coerceAtLeast(0f)
        }
    }

    val tabsWidth = TABS_TEXT_DP * scale +
        TAB_COUNT * 2f * tabPadding.value +
        (TAB_COUNT - 1) * tabSpacing.value

    return TvTopBarPlan(
        tier = tier,
        brand = brand,
        showClock = showClock,
        profile = profile,
        search = search,
        height = height,
        horizontalPadding = horizontalPadding,
        controlHeight = controlHeight,
        iconButtonSize = 40.dp,
        tabPadding = tabPadding,
        tabSpacing = tabSpacing,
        profileLabelMaxWidth = if (profile == TvTopBarProfileMode.NAME) PROFILE_LABEL_MAX_DP.dp else 0.dp,
        fontScale = scale,
        tabStripWidth = tabStrip,
        allTabsFit = tabStrip >= tabsWidth,
        minTabStripMet = tabStrip >= TV_TOP_BAR_MIN_TAB_STRIP_DP,
        estimatedWidth = fixed + tabsWidth,
    )
}

/** Índice de la pestaña activa en [TV_TOP_ENTRIES]; 0 si ninguna coincide. */
internal fun tvTopEntryIndex(active: TvTopTab?): Int =
    TV_TOP_ENTRIES.indexOfFirst { tvTopEntryActive(it, active) }.coerceAtLeast(0)

internal fun tvTopEntryActive(entry: TvTopEntry, active: TvTopTab?): Boolean = when (entry) {
    is TvTopEntry.Tab -> entry.tab == active
    TvTopEntry.LiveHub -> liveHubSelected(active)
    TvTopEntry.MoviesHub -> active == TvTopTab.MOVIES
    TvTopEntry.SeriesHub -> active == TvTopTab.SERIES
    TvTopEntry.LibraryHub -> active == TvTopTab.LIBRARY
}

/** Clave estable para la `LazyRow` (y para no perder el ítem al cambiar de variante). */
internal fun tvTopEntryKey(entry: TvTopEntry): String = when (entry) {
    is TvTopEntry.Tab -> "tab:${entry.tab.name}"
    TvTopEntry.LiveHub -> "hub:live"
    TvTopEntry.MoviesHub -> "hub:movies"
    TvTopEntry.SeriesHub -> "hub:series"
    TvTopEntry.LibraryHub -> "hub:library"
}

private const val TAB_COUNT = 6

// Anchos base a fontScale = 1.0, medidos con métricas tipo Roboto/Segoe.
private const val BRAND_FULL_TEXT_DP = 81.2f
private const val BRAND_SHORT_TEXT_DP = 68f
private const val BRAND_TEXT_GAP_DP = 8f
private const val TABS_TEXT_DP = 247f
private const val CLOCK_TEXT_DP = 34.2f
private const val CLOCK_BLOCK_DP = 20f
private const val DIVIDER_BLOCK_DP = 17f
private const val UTILITIES_BASE_DP = 222f
private const val BADGE_SAFETY_DP = 4f
private const val PROFILE_NAME_EXTRA_BASE_DP = 7f
private const val PROFILE_LABEL_MAX_DP = 120f
private const val PROFILE_CHAR_DP = 5f
private const val SEARCH_PILL_EXTRA_BASE_DP = 10f
private const val SEARCH_PILL_TEXT_DP = 36f
