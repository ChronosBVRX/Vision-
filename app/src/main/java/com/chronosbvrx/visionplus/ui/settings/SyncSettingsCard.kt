package com.chronosbvrx.visionplus.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.remote.RemoteClient
import com.chronosbvrx.visionplus.data.remote.RemoteStatus
import com.chronosbvrx.visionplus.data.sync.SyncStore
import com.chronosbvrx.visionplus.data.sync.SyncUiState
import com.chronosbvrx.visionplus.data.sync.formatSyncCode
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Continuidad entre equipos» en Ajustes: muestra el código compartido, permite vincular
 * este equipo con el de la tele y dispara una sincronización a mano. El código es lo único
 * que viaja entre equipos; aquí no hay cuentas.
 */
@Composable
internal fun SyncSettingsSection() {
    val context = LocalContext.current
    val state by SyncStore.state
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { SyncStore.load(context) }

    SettingsSection("Continuidad entre equipos") {
        SettingsBlock {
            Text("Seguir viendo y Mi lista en todos tus equipos", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Un código de 8 caracteres comparte el minuto donde te quedaste y lo guardado. " +
                    "El vivo no se sincroniza.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            if (state.code.isBlank()) {
                Button(
                    onClick = {
                        SyncStore.ensureCode(context)
                        SyncStore.syncAsync(context, force = true)
                    },
                ) { Text("Generar código") }
            } else {
                Text(
                    formatSyncCode(state.code),
                    style = MaterialTheme.typography.headlineSmall,
                    color = VisionBlue,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { copyCode(context, state.code) }) { Text("Copiar") }
                    TextButton(onClick = { shareCode(context, state.code) }) { Text("Compartir") }
                }
                if (RemoteClient.state.value.status == RemoteStatus.CONNECTED) {
                    TextButton(onClick = {
                        RemoteClient.sendSyncCode(state.code)
                        Toast.makeText(context, "Código enviado a la tele", Toast.LENGTH_SHORT).show()
                    }) { Text("Enviar a la tele") }
                }
            }
        }

        SettingsBlock {
            Text("Vincular este equipo", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Escribe el código que muestra la tele (o el de tu otro equipo).",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(24) },
                singleLine = true,
                label = { Text("Código") },
                placeholder = { Text("ABCD-EFGH") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (SyncStore.setCode(context, input)) {
                            input = ""
                            SyncStore.syncAsync(context, force = true)
                            Toast.makeText(context, "Equipo vinculado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Código inválido", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Vincular") }
                if (state.code.isNotBlank()) {
                    TextButton(onClick = { SyncStore.syncAsync(context, force = true) }) {
                        Text("Sincronizar ahora")
                    }
                }
            }
        }

        SettingsRow(
            title = "Estado",
            supporting = syncStatusText(state),
        )

        if (state.code.isNotBlank()) {
            SettingsDestructiveRow(
                title = "Olvidar este código",
                supporting = "Deja de sincronizar en este equipo; tus datos locales se conservan",
                onClick = {
                    SyncStore.forget(context)
                    Toast.makeText(context, "Sincronización olvidada", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

internal fun syncStatusText(state: SyncUiState): String = when {
    state.busy -> "Sincronizando…"
    state.code.isBlank() -> "Sin código todavía"
    state.error != null -> "Sin conexión: ${state.error}"
    state.lastSyncAt <= 0L -> "Aún no se ha sincronizado"
    else -> "Última sincronización: ${lastSyncLabel(state.lastSyncAt)}"
}

private fun lastSyncLabel(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000L
    return when {
        minutes < 1 -> "ahora mismo"
        minutes < 60 -> "hace $minutes min"
        else -> SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("es-MX")).format(Date(at))
    }
}

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Código Vision+", formatSyncCode(code)))
    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
}

private fun shareCode(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Mi código de Vision+ es ${formatSyncCode(code)}")
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Compartir código")) }
}
