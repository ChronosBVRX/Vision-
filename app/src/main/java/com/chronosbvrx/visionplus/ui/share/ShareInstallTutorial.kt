package com.chronosbvrx.visionplus.ui.share

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder

/**
 * Constantes y utilidades para compartir Vision+ e instalarlo en Smart TV / Firestick.
 */
object ShareTutorialConfig {
    const val DOWNLOADER_CODE = "4999823"
    const val APK_DOWNLOAD_URL = "https://github.com/ChronosBVRX/Vision-Releases/releases/latest/download/VisionPlus.apk"

    fun buildShareMessage(): String = """
¡Hola! Te recomiendo instalar Vision+ en tu tele para ver películas, series y canales en vivo gratis.

📺 Cómo instalarlo en cualquier Smart TV o Firestick:
1. Abre la app Downloader en tu tele.
2. Escribe el código: $DOWNLOADER_CODE y pulsa Go.
3. ¡Instala y listo!

📥 Enlace directo de descarga:
$APK_DOWNLOAD_URL
""".trimIndent()

    fun buildWhatsAppUrl(): String {
        val encoded = URLEncoder.encode(buildShareMessage(), "UTF-8").replace("+", "%20")
        return "https://wa.me/?text=$encoded"
    }

    /** Genera el mapa de bits del QR para abrir WhatsApp con el mensaje ya redactado. */
    fun generateWhatsAppQr(sizePx: Int = 480): Bitmap? = runCatching {
        val size = sizePx.coerceIn(240, 1024)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(buildWhatsAppUrl(), BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
