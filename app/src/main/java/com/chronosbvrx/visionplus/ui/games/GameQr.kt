package com.chronosbvrx.visionplus.ui.games

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.games.GameCodes
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** URL que lleva el QR: abre directamente la pantalla de unirse en el teléfono. */
internal fun qrPayload(code: String): String {
    val normalized = GameCodes.normalize(code) ?: code.trim().uppercase()
    return "visionplus://play/join?code=$normalized"
}

/** QR de alto contraste, generado UNA vez por sala (nunca dentro de la composición). */
internal fun qrBitmap(payload: String, sizePx: Int): Bitmap? = runCatching {
    val size = sizePx.coerceIn(240, 1200)
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

@Composable
fun GameQr(
    code: String,
    modifier: Modifier = Modifier,
    size: Dp = 340.dp,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, code, sizePx) {
        value = withContext(Dispatchers.Default) { qrBitmap(qrPayload(code), sizePx) }
    }
    bitmap?.let { qr ->
        Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = "QR para unirse a la sala",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(14.dp)),
        )
    }
}
