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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.adb.Apk
import com.chronosbvrx.visionplus.data.adb.ApkState
import com.chronosbvrx.visionplus.data.adb.TvAppPackage
import com.chronosbvrx.visionplus.data.adb.TvHomeSetup
import com.chronosbvrx.visionplus.data.adb.TvLauncherPackage
import com.chronosbvrx.visionplus.data.adb.TvRecord
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * «Instalar en TV» en Ajustes del TELÉFONO: deja el televisor listo para usarse, sin PC.
 *
 * Por cada tele: baja del release oficial la última Vision+ y el último Vision+ Home, los instala
 * por ADB inalámbrico, fija el launcher como Inicio y solo retira el Inicio del fabricante si
 * sigue robándose la pantalla. Cada tele guarda su Inicio original y lo retirado para poder
 * restaurarla; se pueden tener varias y cada una se maneja por separado.
 */
@Composable
internal fun TvHomeSetupSection() {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<TvRecord?>(null) }
    var cachedLauncher by remember { mutableStateOf<Apk?>(null) }
    val launcherState = TvLauncherPackage.state
    val appState = TvAppPackage.state

    LaunchedEffect(Unit) {
        TvHomeSetup.load(context)
        cachedLauncher = TvLauncherPackage.cached(context)
    }

    SettingsSection("Vision+ Home en tu tele") {
        SettingsBlock {
            Text("Instala Vision+ y deja su Inicio, sin PC", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Tu teléfono baja la última versión de Vision+ y de Vision+ Home del release oficial, " +
                    "las instala en la tele por ADB inalámbrico y deja el launcher como pantalla de inicio. " +
                    "No hay que descargar nada a mano ni conectar una computadora.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "Android 11 o más: se empareja con el código de la tele. Android 8/9: escribe la dirección " +
                    "del televisor (Ajustes → Red) con «ADB por red» encendido.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )

            Text(
                when (val s = launcherState) {
                    ApkState.Looking -> "Buscando Vision+ Home en el release…"
                    is ApkState.Downloading -> "Descargando Vision+ Home… ${s.percent} %"
                    is ApkState.Ready -> "Vision+ Home " +
                        TvLauncherPackage.versionLine(s.versionName, s.versionCode) +
                        if (s.fromRelease) " · última del release" else " · incluida en la app"
                    is ApkState.Failed -> "No se pudo bajar Vision+ Home: ${s.message}"
                    ApkState.Idle -> cachedLauncher?.let {
                        "Vision+ Home " + TvLauncherPackage.versionLine(it.versionName, it.versionCode) +
                            " listo en el teléfono"
                    } ?: "Vision+ Home se descarga del release al instalar."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (launcherState is ApkState.Failed) MaterialTheme.colorScheme.error else VisionBlue,
                modifier = Modifier.padding(top = 6.dp),
            )

            when (val s = appState) {
                is ApkState.Downloading -> Text(
                    "Descargando Vision+… ${s.percent} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionBlue,
                    modifier = Modifier.padding(top = 2.dp),
                )
                is ApkState.Ready -> Text(
                    "Vision+ " + TvLauncherPackage.versionLine(s.versionName, s.versionCode) + " instalado",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionBlue,
                    modifier = Modifier.padding(top = 2.dp),
                )
                is ApkState.Failed -> Text(
                    "No se pudo instalar Vision+: ${s.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
                else -> Unit
            }

            if (launcherState is ApkState.Idle && cachedLauncher == null) {
                TextButton(
                    onClick = { scope.launch { cachedLauncher = TvLauncherPackage.resolve(context) } },
                ) { Text("Buscar la última versión") }
            }

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
                    onInstall = {
                        scope.launch {
                            TvHomeSetup.install(context, tv.id)
                            cachedLauncher = TvLauncherPackage.cached(context)
                        }
                    },
                    onApply = { scope.launch { TvHomeSetup.apply(context, tv.id) } },
                    onRestore = { scope.launch { TvHomeSetup.restore(context, tv.id) } },
                    onForget = { forgetting = tv },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { adding = true }) {
                    Text(if (state.tvs.isEmpty()) "Configurar la tele" else "Agregar otra tele")
                }
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
    onInstall: () -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onForget: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
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
        if (tv.appVersion.isNotBlank() || tv.launcherVersion.isNotBlank()) {
            Text(
                listOfNotNull(
                    tv.appVersion.takeIf { it.isNotBlank() }?.let { "Vision+ $it" },
                    tv.launcherVersion.takeIf { it.isNotBlank() }?.let { "Home $it" },
                ).joinToString(" · ") + " en la tele",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
            )
        }
        Button(
            onClick = onInstall,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Text(
                if (tv.launcherVersion.isBlank()) {
                    "Instalar Vision+ y su Inicio"
                } else {
                    "Actualizar Vision+ y su Inicio"
                },
            )
        }
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
    var job by remember { mutableStateOf<Job?>(null) }
    val phase = state.phase
    val readyToPair = phase == TvHomeSetup.Phase.READY && state.pairingPort > 0

    fun close() {
        job?.cancel()
        TvHomeSetup.resetMessage()
        onDismiss()
    }

    // Una sola búsqueda/conexión a la vez: la anterior se cancela para que no pise el resultado.
    fun run(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    // La búsqueda con código vive DENTRO del diálogo: al cerrarlo se cancela y no deja mensajes
    // de error sueltos en la pantalla de atrás.
    LaunchedEffect(Unit) {
        job = scope.launch { TvHomeSetup.search(context) }
    }

    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Agregar una tele") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                            "En teles Android 8/9 no hay código: no hace falta tener nada instalado en el " +
                                "televisor, solo «ADB por red» encendido; escribe su dirección abajo.",
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
                            "Se instalará en la tele la última versión de Vision+ (unos 82 MB) y de Vision+ Home. " +
                                "Después, Vision+ Home quedará como Inicio y, si el launcher de fábrica sigue " +
                                "abriéndose, se retira ESE Inicio (puedes devolverlo con «Restaurar»).",
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
                ManualTvSection(
                    busy = phase == TvHomeSetup.Phase.WORKING || phase == TvHomeSetup.Phase.SEARCHING,
                    onConnect = { host -> run { TvHomeSetup.connectByAddress(context, host) } },
                )
            }
        },
        confirmButton = {
            when {
                readyToPair -> Button(
                    onClick = { run { TvHomeSetup.pairAndApply(context, code) } },
                    enabled = code.length == 6,
                ) { Text("Emparejar y aplicar") }

                phase == TvHomeSetup.Phase.ERROR && state.legacyHost.isNotBlank() -> Button(
                    onClick = { run { TvHomeSetup.connectByAddress(context, state.legacyHost) } },
                ) { Text("Reintentar conexión") }

                phase == TvHomeSetup.Phase.ERROR -> Button(
                    onClick = { run { TvHomeSetup.search(context) } },
                ) { Text("Buscar otra vez") }

                phase == TvHomeSetup.Phase.DONE -> Button(
                    onClick = { TvHomeSetup.resetMessage(); onDismiss() },
                ) { Text("Listo") }

                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = { close() }) {
                Text(if (phase == TvHomeSetup.Phase.WORKING) "Cancelar" else "Cerrar")
            }
        },
    )
}

/**
 * La puerta para las teles Android 8/9: no tienen código de emparejamiento, así que se escribe
 * la dirección del televisor (Ajustes → Red) y se conecta por ADB. No hace falta tener Vision+
 * instalada en la tele: esa era justo la trampa del flujo viejo.
 */
@Composable
private fun ManualTvSection(
    busy: Boolean,
    onConnect: (String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    Text(
        "¿Tele Android 8/9? Esas no usan código: escribe su dirección (Ajustes → Red del televisor) " +
            "con «ADB por red» encendido.",
        style = MaterialTheme.typography.labelMedium,
        color = VisionTextSecondary,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = address,
            onValueChange = { value -> address = value.trim().take(18) },
            label = { Text("Dirección (192.168.1.x)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onConnect(address) },
            enabled = !busy && address.isNotBlank(),
        ) { Text("Conectar") }
    }
}
