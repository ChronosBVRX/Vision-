package com.chronosbvrx.visionplus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.adb.TvHomeSetup
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.launch

/**
 * «Inicio en la TV» en Ajustes del TELÉFONO: empareja ADB por Wi-Fi con la tele (una vez) y
 * deja Vision+ Home como Inicio sin necesidad de una PC. En la tele no se activa nada.
 */
@Composable
internal fun TvHomeSetupSection() {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { TvHomeSetup.load(context) }

    SettingsSection("Inicio en la TV") {
        SettingsBlock {
            Text("Vision+ Home como Inicio, sin PC", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Este teléfono se conecta a la tele por ADB inalámbrico y hace el cambio. " +
                    "Solo se configura desde aquí; la tele no pide nada extra.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            if (state.paired) {
                Text(
                    "Tele emparejada${if (state.applied) " · Vision+ Home es su Inicio" else ""}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionBlue,
                )
            }
            if (state.disabled.isNotEmpty()) {
                Text(
                    "Retirado de la tele para que no tape el Inicio: ${state.disabled.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    dialog = true
                    if (!state.paired) scope.launch { TvHomeSetup.search(context) }
                }) {
                    Text(if (state.paired) "Aplicar otra vez" else "Configurar la tele")
                }
                if (state.paired || state.disabled.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { TvHomeSetup.restore(context) } }) {
                        Text("Restaurar la tele")
                    }
                }
            }
        }
    }

    if (dialog) {
        TvHomeSetupDialog(onDismiss = { dialog = false })
    }
}

@Composable
private fun TvHomeSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    val phase = state.phase
    val readyToPair = phase == TvHomeSetup.Phase.READY && state.pairingPort > 0
    val readyToApply = phase == TvHomeSetup.Phase.READY && state.pairingPort == 0

    AlertDialog(
        onDismissRequest = { if (phase != TvHomeSetup.Phase.WORKING) onDismiss() },
        title = { Text("Inicio en la TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (phase) {
                    TvHomeSetup.Phase.IDLE,
                    TvHomeSetup.Phase.SEARCHING,
                    -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                state.message.ifBlank { "Buscando la tele en tu red…" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "En la tele: Ajustes → Sistema → Información → pulsa 7 veces «Versión» para " +
                                "activar Desarrollador, entra en Depuración inalámbrica y deja abierta " +
                                "«Emparejar dispositivo con código».",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                    }

                    TvHomeSetup.Phase.READY -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        if (readyToPair) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { value ->
                                    code = value.filter(Char::isDigit).take(6)
                                },
                                label = { Text("Código de 6 dígitos") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                            )
                        }
                        Text(
                            "Google TV y algunas cajas no respetan la elección: si la tele sigue abriendo su " +
                                "propio Inicio, la app retira ESE Inicio para que abra Vision+ Home. Se puede " +
                                "devolver con «Restaurar la tele».",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                    }

                    TvHomeSetup.Phase.WORKING -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    TvHomeSetup.Phase.DONE,
                    TvHomeSetup.Phase.ERROR,
                    -> Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            when {
                readyToPair -> Button(
                    onClick = { scope.launch { TvHomeSetup.pairAndApply(context, code) } },
                    enabled = code.length == 6,
                ) { Text("Emparejar y aplicar") }

                readyToApply -> Button(
                    onClick = { scope.launch { TvHomeSetup.apply(context) } },
                ) { Text("Aplicar") }

                phase == TvHomeSetup.Phase.ERROR -> Button(
                    onClick = { scope.launch { TvHomeSetup.search(context) } },
                ) { Text("Buscar otra vez") }

                else -> Unit
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (phase != TvHomeSetup.Phase.WORKING) {
                        TvHomeSetup.resetMessage()
                        onDismiss()
                    }
                },
            ) { Text("Cerrar") }
        },
    )
}
