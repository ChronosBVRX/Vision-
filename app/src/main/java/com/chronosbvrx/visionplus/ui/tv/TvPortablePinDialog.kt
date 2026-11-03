package com.chronosbvrx.visionplus.ui.tv

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.offline.PortableLibrary.PortableItem
import com.chronosbvrx.visionplus.data.offline.PortableLibrary
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PIN de 4 dígitos para abrir una biblioteca portátil en una TV que no descargó
 * el contenido. La validación es 100% local; no se llama al servidor.
 */
@Composable
fun TvPortablePinDialog(
    item: PortableItem,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val landing = rememberLandingFocus("tv_portable_pin_dialog")
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    fun submit() {
        if (checking || pin.length != 4) return
        checking = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PortableLibrary.unlock(context, item, pin)
            }
            checking = false
            when (result) {
                is PortableLibrary.UnlockResult.Unlocked -> onUnlocked()
                is PortableLibrary.UnlockResult.Wrong -> {
                    pin = ""
                    error = if (result.lockSeconds > 0L) {
                        "Demasiados intentos. Espera ${result.lockSeconds} s."
                    } else {
                        "PIN incorrecto. Te quedan ${result.attemptsLeft} intentos."
                    }
                }
                is PortableLibrary.UnlockResult.Locked -> {
                    pin = ""
                    error = "Espera ${result.seconds} s antes de volver a intentar."
                }
                is PortableLibrary.UnlockResult.Error -> {
                    pin = ""
                    error = result.message
                }
            }
        }
    }

    TvDialog(title = "Contenido portátil", onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = VisionBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                }
            }
        }

        Text(
            "Introduce el PIN de 4 dígitos que generó la TV donde descargaste esta USB.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(width = 52.dp, height = 64.dp)
                        .background(VisionSurfaceHigh, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        pin.getOrNull(index)?.toString() ?: "•",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (index < pin.length) Color.White else VisionTextSecondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (error != null) {
            Text(
                error!!,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF8A80),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { digit ->
                        PinKey(
                            label = digit,
                            modifier = Modifier.weight(1f).then(
                                if (rowIndex == 0 && digit == "1") Modifier.landingFocus(landing) else Modifier,
                            ),
                            onClick = {
                                if (pin.length < 4) {
                                    pin += digit
                                    error = null
                                }
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PinKey(
                    label = "Borrar",
                    modifier = Modifier.weight(1f),
                    onClick = { pin = pin.dropLast(1); error = null },
                )
                PinKey(
                    label = "0",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (pin.length < 4) {
                            pin += "0"
                            error = null
                        }
                    },
                )
                PinKey(
                    label = if (checking) "…" else "Abrir",
                    modifier = Modifier.weight(1f),
                    primary = true,
                    enabled = pin.length == 4 && !checking,
                    onClick = { submit() },
                )
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = if (primary) VisionBlue else VisionSurfaceHigh,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        modifier = modifier.height(46.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/** La TV de origen muestra el PIN de la USB para que el usuario lo anote. */
@Composable
fun TvPortablePinShowDialog(
    title: String,
    pin: String,
    onDismiss: () -> Unit,
) {
    TvDialog(title = "PIN de esta USB", onDismiss = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Escribe este PIN en la otra TV cuando conectes la USB. La TV de origen puede estar apagada.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VisionSurfaceHigh, RoundedCornerShape(14.dp))
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pin,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Guárdalo. Si lo pierdes, puedes volver a esta TV para generar uno nuevo mientras la biblioteca siga en esta USB.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.colors(
                containerColor = VisionBlue,
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Entendido", fontWeight = FontWeight.Bold)
        }
    }
}
