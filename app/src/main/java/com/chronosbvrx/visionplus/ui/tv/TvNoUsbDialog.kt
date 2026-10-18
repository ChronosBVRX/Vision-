package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.ui.theme.VisionBlue

/**
 * Diálogo modal para Smart TV cuando el usuario intenta descargar sin una memoria USB conectada.
 * Explica amigablemente la política estricta de protección de memoria de la TV.
 */
@Composable
fun TvNoUsbDialog(onDismiss: () -> Unit) {
    val landing = rememberLandingFocus("tv_no_usb_dialog")
    // Mientras el diálogo exista, el rescate de la pantalla de atrás no debe robarle las teclas.
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(min = 480.dp, max = 560.dp)
                .background(Color(0xFF101726), RoundedCornerShape(16.dp))
                .border(1.5.dp, VisionBlue.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(VisionBlue.copy(alpha = 0.15f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Usb,
                        contentDescription = "USB Requerido",
                        tint = VisionBlue,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Memoria USB Requerida",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Para proteger el almacenamiento interno de tu Smart TV y mantenerla rápida y fluida, las descargas para ver sin internet se guardan exclusivamente en una memoria USB o disco externo conectado por puerto USB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Conecta una memoria USB a tu televisor y vuelve a pulsar Descargar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionBlue,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = VisionBlue,
                        focusedContainerColor = Color.White,
                        contentColor = Color.White,
                        focusedContentColor = Color.Black,
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .landingFocus(landing),
                ) {
                    Text(
                        "Entendido",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
