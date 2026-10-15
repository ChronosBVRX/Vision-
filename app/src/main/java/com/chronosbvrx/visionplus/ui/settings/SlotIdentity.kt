package com.chronosbvrx.visionplus.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.gate.SlotClient
import com.chronosbvrx.visionplus.data.xuper.DeviceSlot
import kotlinx.coroutines.launch

/**
 * Identidad propia del portal (réplica Hydra): el operador da UN código por equipo y
 * aquí se canjea por el slot propio. Sin slot, la app usa la identidad compartida.
 */
@Composable
fun SlotCodeDialog(onDismiss: () -> Unit, onRedeemed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Identidad del equipo") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "La identidad se asigna sola al arrancar. Si este equipo no la tomó, " +
                        "escribe aquí el código de un solo uso del operador.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12)
                    },
                    label = { Text("Código (8 letras y números)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !working && DeviceSlot.normalizeCode(code).isNotEmpty(),
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        SlotClient.redeem(context, code.trim()).fold(
                            onSuccess = { onRedeemed() },
                            onFailure = { error = it.message ?: "No se pudo canjear" },
                        )
                        working = false
                    }
                },
            ) { if (working) CircularProgressIndicator() else Text("Canjear") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
