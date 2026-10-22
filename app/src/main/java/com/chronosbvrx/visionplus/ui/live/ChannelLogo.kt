package com.chronosbvrx.visionplus.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * CuÃ¡nto puede crecer un logo sobre su tamaÃ±o nativo antes de empezar a verse borroso.
 *
 * Los logos del proveedor miden ~90Ã—60 px. Dibujados a `fillMaxSize` en una tarjeta de ~158 dp
 * se agrandaban 3-4Ã—: se veÃ­an enormes, pixelados, y los que el proveedor ya entrega recortados
 * parecÃ­an rotos. A 2Ã— siguen legibles sin inventar pÃ­xeles.
 */
internal const val LOGO_MAX_UPSCALE = 2.5f

/** El preview de la tele es un panel grande: 3Ã— deja el logo con presencia sin verse sucio. */
internal const val LOGO_PREVIEW_UPSCALE = 3f

/** PÃ­xeles a los que se decodifica el logo (los del proveedor son 90Ã—60; esto no inventa nada). */
private const val LOGO_DECODE_PX = 256

/**
 * TamaÃ±o en dp que debe ocupar el logo: el nativo (ya convertido a dp) sin pasarse de [maxUpscale]
 * veces ni del espacio disponible [boxW]Ã—[boxH]. Conserva la proporciÃ³n.
 *
 * Ejemplo real: logo de 90Ã—60 px en un telÃ©fono a 2.75 de densidad = 32.7Ã—21.8 dp; en una tarjeta
 * de 134Ã—89 dp el resultado es 65.4Ã—43.6 dp (2Ã—), no 134Ã—89.
 */
internal fun logoDisplaySize(
    intrinsicW: Float,
    intrinsicH: Float,
    boxW: Float,
    boxH: Float,
    maxUpscale: Float = LOGO_MAX_UPSCALE,
): Pair<Float, Float> {
    if (intrinsicW <= 0f || intrinsicH <= 0f || boxW <= 0f || boxH <= 0f) return boxW to boxH
    val scale = minOf(boxW / intrinsicW, boxH / intrinsicH, maxUpscale)
    return (intrinsicW * scale) to (intrinsicH * scale)
}

/**
 * El logo de un canal, legible y centrado.
 *
 * - Nunca se agranda mÃ¡s de [maxUpscale] veces su tamaÃ±o nativo ni mÃ¡s que la tarjeta.
 * - Mientras carga (o si falla) se ven las iniciales del canal como respaldo.
 * - Si no hay logo, solo las iniciales.
 */
@Composable
internal fun ChannelLogo(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    maxUpscale: Float = LOGO_MAX_UPSCALE,
    initialsStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    if (url.isNullOrBlank()) {
        ChannelInitials(name, modifier, initialsStyle)
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(0)
            .size(LOGO_DECODE_PX)
            .build(),
    )
    val loaded = painter.state is AsyncImagePainter.State.Success
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) ChannelInitials(name, Modifier.fillMaxSize(), initialsStyle)
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        val native = painter.intrinsicSize
        // OJO: `Size.width` lanza IllegalStateException si el tamaÃ±o es *unspecified* (el painter
        // aÃºn no tiene drawable). Hay que preguntar antes de leerlo.
        val intrinsicW = if (native.isSpecified) native.width / density else 0f
        val intrinsicH = if (native.isSpecified) native.height / density else 0f
        val (w, h) = if (loaded) {
            logoDisplaySize(intrinsicW, intrinsicH, boxW, boxH, maxUpscale)
        } else {
            // Mientras carga no reservamos el tamaÃ±o final: se ocultan las iniciales al llegar.
            boxW to boxH
        }
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(w.dp, h.dp),
        )
    }
}

/** Las iniciales del canal como respaldo (sin logo o mientras carga). */
@Composable
internal fun ChannelInitials(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            channelInitials(name),
            style = style,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}
