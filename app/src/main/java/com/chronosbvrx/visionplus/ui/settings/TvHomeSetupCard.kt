package com.chronosbvrx.visionplus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.chronosbvrx.visionplus.data.adb.TvRecord
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.launch

/**
 * «Inicio en la TV» en Ajustes del TELÉFONO: empareja ADB por Wi-Fi con cada tele (una vez) y
 * deja Vision+ Home como Inicio sin necesidad de una PC.
 *
 * Se pueden tener VARIAS teles: cada una guarda su Inicio original y lo que se le retiró, y
 * puede reaplicarse, restaurarse o quitarse de la lista por separado.
 */
@Composable
internal fun TvHomeSetupSection() {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<TvRecord?>(null) }

    LaunchedEffect(Unit) { TvHomeSetup.load(context) }

    SettingsSection("Inicio en la TV") {
        SettingsBlock {
            Text("Vision+ Home como Inicio, sin PC", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Este teléfono se conecta a cada tele por ADB inalámbrico. Solo se configura desde aquí; " +
                    "en el televisor no hay nada que instalar.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )

            if (state.tvs.isEmpty()) {
                Text(
                    "Aún no hay teles emparejadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            state.tvs.forEach { tv ->
                TvHomeRow(
                    tv = tv,
                    busy = state.busyId == tv.id,
                    onApply = { scope.launch { TvHomeSetup.apply(context, tv.id) } },
                    onRestore = { scope.launch { TvHomeSetup.restore(context, tv.id) } },
                    onForget = { forgetting = tv },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = {
                    adding = true
                    scope.launch { TvHomeSetup.search(context) }
                }) { Text(if (state.tvs.isEmpty()) "Configurar la tele" else "Agregar otra tele") }
            }

            if (state.message.isNotBlank()) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == TvHomeSetup.Phase.ERROR) MaterialTheme.colorScheme.error else VisionBlue,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.log.isNotEmpty()) {
                Text(
                    "Detalles",
                    style = MaterialTheme.typography.labelMedium,
                    color = VisionTextSecondary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.log.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionTextSecondary,
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        TvHomeSetupDialog(onDismiss = { adding = false })
    }

    forgetting?.let { tv ->
        ForgetTvDialog(
            tv = tv,
            onRestoreAndForget = {
                scope.launch {
                    TvHomeSetup.restore(context, tv.id)
                    TvHomeSetup.forget(context, tv.id)
                }
                forgetting = null
            },
            onForgetAnyway = {
                TvHomeSetup.forget(context, tv.id)
                forgetting = null
            },
            onDismiss = { forgetting = null },
        )
    }
}

@Composable
private fun TvHomeRow(
    tv: TvRecord,
    busy: Boolean,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onForget: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tv.displayName, style = MaterialTheme.typography.bodyLarge)
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(
            when {
                tv.applied -> "Vision+ Home es su Inicio"
                tv.paired -> "Emparejada"
                else -> "Sin configurar"
            } + if (tv.disabled.isNotEmpty()) " · ${tv.disabled.size} app(s) retiradas" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (tv.applied) VisionBlue else VisionTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onApply, enabled = !busy) { Text("Aplicar") }
            TextButton(onClick = onRestore, enabled = !busy) { Text("Restaurar") }
            TextButton(onClick = onForget, enabled = !busy) { Text("Olvidar") }
        }
    }
}

@Composable
private fun ForgetTvDialog(
    tv: TvRecord,
    onRestoreAndForget: () -> Unit,
    onForgetAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasChanges = tv.disabled.isNotEmpty() || tv.applied
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Olvidar ${tv.displayName}") },
        text = {
            Text(
                if (hasChanges) {
                    "Esta tele tiene cambios hechos desde aquí (${tv.disabled.size} app(s) retiradas). " +
                        "Si la olvidas sin restaurar, ya no podrás devolverlos desde el teléfono."
                } else {
                    "Se quitará de la lista. No cambia nada en el televisor."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (hasChanges) {
                TextButton(onClick = onRestoreAndForget) { Text("Restaurar y olvidar") }
            } else {
                TextButton(onClick = onForgetAnyway) { Text("Olvidar") }
            }
        },
        dismissButton = {
            Row {
                if (hasChanges) {
                    TextButton(onClick = onForgetAnyway) { Text("Olvidar igual") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun TvHomeSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    val phase = state.phase
    val readyToPair = phase == TvHomeSetup.Phase.READY && state.pairingPort > 0

    AlertDialog(
        onDismissRequest = { if (phase != TvHomeSetup.Phase.WORKING) onDismiss() },
        title = { Text("Agregar una tele") },
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
                            "En el televisor: Ajustes → Sistema → Información → pulsa 7 veces «Versión» para " +
                                "activar Desarrollador, entra en Depuración inalámbrica y deja abierta " +
                                "«Emparejar dispositivo con código».",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                        Text(
                            "En teles Android 9/10 activa «ADB por red» y vincula antes el control remoto " +
                                "para que el teléfono sepa su dirección.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                    }

                    TvHomeSetup.Phase.READY -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        if (readyToPair) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
                                label = { Text("Código de 6 dígitos") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                            )
                        }
                        Text(
                            "Si el televisor sigue abriendo su launcher de fábrica, la app retira ESE Inicio para " +
                                "que abra Vision+ Home. Se puede devolver con «Restaurar».",
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
                    -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        if (state.log.isNotEmpty()) {
                            Text(
                                "Detalles",
                                style = MaterialTheme.typography.labelMedium,
                                color = VisionTextSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                state.log.forEach { line ->
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VisionTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                readyToPair -> Button(
                    onClick = { scope.launch { TvHomeSetup.pairAndApply(context, code) } },
                    enabled = code.length == 6,
                ) { Text("Emparejar y aplicar") }

                phase == TvHomeSetup.Phase.ERROR -> Button(
                    onClick = { scope.launch { TvHomeSetup.search(context) } },
                ) { Text("Buscar otra vez") }

                phase == TvHomeSetup.Phase.DONE -> Button(
                    onClick = { TvHomeSetup.resetMessage(); onDismiss() },
                ) { Text("Listo") }

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
