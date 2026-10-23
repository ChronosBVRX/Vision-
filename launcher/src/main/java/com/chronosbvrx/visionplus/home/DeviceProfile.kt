package com.chronosbvrx.visionplus.home

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * El tamaño de todo lo que se pinta, calculado desde la altura REAL de la pantalla.
 *
 * Una tele 1080p suele reportar 540 dp de alto (320 dpi): ahí el hero no puede medir 400 dp
 * ni el póster 140. En pantallas altas (monitores, 4K con densidad baja) se conserva el
 * tamaño grande. El objetivo es que el Inicio se sienta lleno, no recortado.
 */
data class HomeMetrics(
    val heroHeight: Dp,
    val posterWidth: Dp,
    val posterHeight: Dp,
    val continueWidth: Dp,
    val continueHeight: Dp,
    val channelWidth: Dp,
    val channelHeight: Dp,
    val appTile: Dp,
    val gap: Dp,
    val horizontalPadding: Dp,
    val titleSize: Int,
    val synopsisSize: Int,
)

/** Pura y testeable: la altura en dp manda sobre cualquier suposición del fabricante. */
internal fun homeMetrics(screenHeightDp: Int): HomeMetrics {
    val h = screenHeightDp.coerceIn(360, 1200)
    val compact = h < 700
    val posterWidth = when {
        h < 480 -> 78.dp
        h < 620 -> 96.dp
        h < 760 -> 116.dp
        else -> 140.dp
    }
    return HomeMetrics(
        heroHeight = if (compact) (h * 0.46f).dp.coerceIn(196.dp, 300.dp) else 340.dp,
        posterWidth = posterWidth,
        posterHeight = posterWidth * 1.5f,
        continueWidth = if (compact) 196.dp else 248.dp,
        continueHeight = if (compact) 110.dp else 140.dp,
        channelWidth = if (compact) 150.dp else 190.dp,
        channelHeight = if (compact) 84.dp else 107.dp,
        appTile = if (compact) 76.dp else 96.dp,
        gap = if (compact) 12.dp else 16.dp,
        horizontalPadding = if (compact) 36.dp else 52.dp,
        titleSize = if (compact) 30 else 38,
        synopsisSize = if (compact) 12 else 14,
    )
}

/** La clase de equipo: en un teléfono el launcher se ve igual, pero sin animaciones caras. */
class DeviceProfile(context: Context) {
    val isTv: Boolean = context.resources.configuration.uiMode and
        Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        !context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)

    val lowRam: Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.isLowRamDevice
    }.getOrDefault(false)

    /** Sin animaciones infinitas ni escalados en equipos de 1.5-2 GB (Chromecast HD). */
    val reducedEffects: Boolean = lowRam || !isTv
}

@Composable
fun rememberHomeMetrics(): HomeMetrics {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenHeightDp) {
        homeMetrics(configuration.screenHeightDp)
    }
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    return remember(context) { DeviceProfile(context) }
}
