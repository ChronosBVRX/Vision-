package com.chronosbvrx.visionplus.ui.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.sync.SyncStore
import com.chronosbvrx.visionplus.data.sync.formatSyncCode
import com.chronosbvrx.visionplus.ui.settings.syncStatusText
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * «Continuidad entre equipos» en la tele: muestra el código para escribirlo UNA vez en el
 * teléfono (Ajustes → Continuidad entre equipos) y permite empujar/traer el historial ahora.
 * La tele no pide teclear nada: el teléfono tiene el teclado.
 */
@Composable
internal fun TvSyncDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by SyncStore.state
    val focus = rememberDialogFocus()
    LaunchedEffect(Unit) {
        SyncStore.load(context)
        SyncStore.ensureCode(context)
    }
    TvDialog("Continuidad entre equipos", onDismiss = onDismiss) {
        Text(
            "Sigue viendo y Mi lista en tu teléfono y esta tele.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        Text(
            "Escribe este código en el teléfono, en Ajustes → Continuidad entre equipos:",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        Text(
            formatSyncCode(state.code),
            style = MaterialTheme.typography.headlineMedium,
            color = VisionBlue,
        )
        Text(
            syncStatusText(state),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { SyncStore.syncAsync(context, force = true) },
                modifier = Modifier.dialogFocus(focus),
            ) {
                Text("Sincronizar ahora")
            }
            TextButton(onClick = {
                SyncStore.forget(context)
                Toast.makeText(context, "Sincronización olvidada", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Olvidar", color = VisionTextSecondary) }
            TextButton(onClick = onDismiss) { Text("Cerrar", color = VisionTextSecondary) }
        }
    }
}
