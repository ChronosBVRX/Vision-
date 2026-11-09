package com.chronosbvrx.visionplus.ui.donation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text as TvText
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import com.chronosbvrx.visionplus.ui.openLink
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.chronosbvrx.visionplus.ui.tv.TvActionOption
import com.chronosbvrx.visionplus.ui.tv.TvCompactAction
import com.chronosbvrx.visionplus.ui.tv.TvDialog
import com.chronosbvrx.visionplus.ui.tv.dialogFocus
import com.chronosbvrx.visionplus.ui.tv.rememberDialogFocus
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Página pública de apoyo. Los botones de Stripe viven en el servidor, no en el APK. */
const val DONATION_URL = "https://visionplusv2.duckdns.org/apoyar"

/** QR de alto contraste para la página de apoyo. Se genera fuera de la composición. */
internal fun donationQrBitmap(sizePx: Int, payload: String = DONATION_URL): Bitmap? = runCatching {
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
fun DonationQr(
    size: Dp = 200.dp,
    modifier: Modifier = Modifier,
    contentDescription: String = "Código QR para apoyar Vision+",
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, sizePx) {
        value = withContext(Dispatchers.Default) { donationQrBitmap(sizePx) }
    }
    val qr = bitmap
    if (qr != null) {
        Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(8.dp),
        )
    } else {
        Spacer(Modifier.size(size))
    }
}

/** Recordatorio discreto: QR en la tele, texto en el teléfono. */
@Composable
fun DonationLoadingHint(isTv: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!RemoteConfig.flag(context, "donation_prompt", false)) return
    if (isTv) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DonationQr(size = 92.dp)
            Column {
                Text(
                    "Apoya Vision+",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Escanea el QR y dona",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        Text(
            modifier = modifier.clickable { openLink(context, DONATION_URL) },
            text = "Apoyar Vision+ · toca aquí",
            style = MaterialTheme.typography.labelMedium,
            color = VisionBlueBright,
            textAlign = TextAlign.Center,
        )
    }
}

/** Una vez al día, descartable y sin bloquear el arranque. */
object DonationPrompt {
    private const val PREFS = "vision_donation"
    private const val K_NEVER = "never"
    private const val K_LAST_DAY = "last_day"

    private fun today(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR)
    }

    fun shouldShow(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(K_NEVER, false)) return false
        return prefs.getInt(K_LAST_DAY, 0) != today()
    }

    fun markShown(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(K_LAST_DAY, today()).apply()
    }

    fun never(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(K_NEVER, true).apply()
    }
}

@Composable
fun DonationPromptDialog(
    isTv: Boolean,
    onDismiss: () -> Unit,
    onNever: () -> Unit,
    allowNever: Boolean = true,
) {
    val context = LocalContext.current
    val open = {
        openLink(context, DONATION_URL)
        onDismiss()
    }
    if (isTv) {
        val focus = rememberDialogFocus()
        TvDialog(title = "Apoya Vision+", onDismiss = onDismiss) {
            TvText(
                "Detrás de Vision+ hay una sola persona. No hay anuncios ni suscripciones, y la app sigue gratis para todos. Si te ha acompañado, si te ha hecho reír o te ha ayudado a desconectarte, puedes invitarme un café o unas chelas desde tu teléfono. Cada apoyo se va a los servidores y a seguir puliendo la app. Gracias por estar aquí.",
                color = Color.White.copy(alpha = 0.88f),
                style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
            )
            DonationQr(size = 240.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            TvText(
                "Escanea el QR con la cámara de tu teléfono. Todo el pago se hace allá, sin navegar en la tele.",
                color = VisionTextSecondary,
                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvCompactAction(
                    label = "Entendido",
                    primary = true,
                    modifier = Modifier.dialogFocus(focus),
                    onClick = onDismiss,
                )
            }
            if (allowNever) {
                TvActionOption("No volver a mostrar") { onNever() }
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = VisionSurface,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Apoya Vision+",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Detrás de Vision+ hay una sola persona. No hay anuncios ni suscripciones, y la app sigue gratis para todos. Si te ha acompañado, si te ha hecho reír o te ha ayudado a desconectarte, puedes invitarme un café o unas chelas. Cada apoyo se va a los servidores y a seguir puliendo la app. Gracias por estar aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = open,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VisionBlue,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Apoyar Vision+") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Ahora no", color = VisionTextSecondary)
                        }
                        if (allowNever) {
                            TextButton(onClick = onNever) {
                                Text("No volver a mostrar", color = VisionTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
