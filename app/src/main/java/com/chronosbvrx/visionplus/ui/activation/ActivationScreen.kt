package com.chronosbvrx.visionplus.ui.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.gate.GateClient
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import kotlinx.coroutines.launch

/**
 * Activation through the Telegram bot, the flow the app itself verifies end-to-end: mint a
 * nonce, open the bot chat (the person approves there), type back the code the bot prints,
 * and the returned token unlocks the app. Nothing else is asked of the person.
 */
@Composable
fun ActivationScreen(isTv: Boolean, onActivated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf("idle") } // idle | waiting | validating
    var nonce by rememberSaveable { mutableStateOf("") }
    var botLink by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 48.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Activa Vision+",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                when (step) {
                    "idle" -> "Un mensaje al bot y el app queda listo. Tarda menos de un minuto."
                    "waiting" -> "Toca Iniciar en el bot y escribe aquí el código que te envía."
                    else -> "Validando el código…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            when (step) {
                "idle" -> {
                    Button(
                        enabled = !working,
                        modifier = Modifier.widthIn(max = 340.dp),
                        onClick = {
                            working = true
                            scope.launch {
                                GateClient.start(context).fold(
                                    onSuccess = { link ->
                                        nonce = link.nonce
                                        botLink = link.botLink
                                        step = "waiting"
                                    },
                                    onFailure = { error = it.message ?: "No pudimos abrir la sesión." },
                                )
                                working = false
                            }
                        },
                    ) { Text(if (working) "Conectando…" else "Activar por el bot") }
                }
                "waiting" -> {
                    OutlinedButton(onClick = { GateClient.openBot(context, botLink) }) {
                        Text("Abrir el bot", color = VisionBlue)
                    }
                    Text("Código del bot: $nonce", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 8) code = it.filter { ch -> ch.isLetterOrDigit() } },
                        label = { Text("Código que llegó en el chat") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    )
                    Button(
                        enabled = !working && code.isNotBlank(),
                        modifier = Modifier.widthIn(max = 340.dp),
                        onClick = {
                            working = true
                            error = null
                            scope.launch {
                                GateClient.validate(context, code.trim()).fold(
                                    onSuccess = { answered ->
                                        if (answered.valid) onActivated()
                                        else error = "El bot no validó el código (${
                                            when (answered.reason) {
                                                "expired" -> "expiró; toca Activar de nuevo"
                                                "malformed" -> "no coincide con el código del chat"
                                                else -> answered.reason ?: "intenta otra vez"
                                            }
                                        })"
                                    },
                                    onFailure = { f -> error = f.message ?: "No pudimos validar." },
                                )
                                working = false
                            }
                        },
                    ) { Text(if (working) "Validando…" else "Activar") }
                    TextButton(enabled = !working, onClick = { step = "idle"; code = "" }) {
                        Text("Usar otro código", color = VisionBlue)
                    }
                }
                else -> CircularProgressIndicator()
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
