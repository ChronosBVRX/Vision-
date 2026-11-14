package com.chronosbvrx.visionplus.ui.tv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.ui.share.ShareTutorialConfig
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

private val WhatsAppGreen = Color(0xFF25D366)

@Composable
fun TvInstallTab() {
    val context = LocalContext.current
    val qrBitmap = remember { ShareTutorialConfig.generateWhatsAppQr(sizePx = 512) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvSectionTitle("Instalar en otra TV y Compartir")
        Text(
            "Cualquier amigo o familiar puede instalar Vision+ en su pantalla en 1 minuto. Puedes dictarles el código o escanear el QR para enviárselo por WhatsApp.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Tarjeta 1: Tutorial de Downloader
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VisionSurfaceHigh)
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "CÓDIGO DE DESCARGA RÁPIDA",
                        style = MaterialTheme.typography.labelMedium,
                        color = VisionBlueBright,
                        fontWeight = FontWeight.Bold,
                    )

                    // Bloque con el código grande
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VisionSurface)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "App Downloader:",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTextSecondary,
                            )
                            Text(
                                ShareTutorialConfig.DOWNLOADER_CODE,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 2.sp,
                            )
                        }
                    }

                    Text(
                        "Pasos para instalar en otra pantalla:",
                        style = MaterialTheme.typography.titleSmall,
                        color = VisionTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )

                    StepRow(number = "1", text = "Abre la app Downloader (ícono naranja) en la otra tele o Firestick.")
                    StepRow(number = "2", text = "En la casilla de búsqueda escribe ${ShareTutorialConfig.DOWNLOADER_CODE} y pulsa Go.")
                    StepRow(number = "3", text = "La descarga iniciará sola. Al terminar selecciona Instalar y ¡listo!")

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Compatible con Fire TV Stick, Google TV, Android TV, Chromecast y TV Boxes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                }
            }

            // Tarjeta 2: Compartir por WhatsApp mediante QR
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VisionSurfaceHigh)
                    .padding(20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "ENVIAR POR WHATSAPP",
                        style = MaterialTheme.typography.labelMedium,
                        color = WhatsAppGreen,
                        fontWeight = FontWeight.Bold,
                    )

                    // Código QR de WhatsApp
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(10.dp),
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR para compartir por WhatsApp",
                                modifier = Modifier.size(170.dp),
                            )
                        }
                    }

                    Text(
                        "Apunta la cámara de tu celular",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    Text(
                        "Se abrirá WhatsApp en tu teléfono con el tutorial y el enlace directo listos para enviar a quien tú elijas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Botón interactivo para foco de D-pad
                    Surface(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Vision+ Tutorial", ShareTutorialConfig.buildShareMessage())
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Tutorial copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = VisionSurface,
                            focusedContainerColor = VisionBlue,
                            pressedContainerColor = VisionBlue,
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border.None,
                            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp)),
                        ),
                    ) {
                        Text(
                            "Copiar texto del tutorial",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VisionBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
    }
}
