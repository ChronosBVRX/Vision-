package com.chronosbvrx.visionplus.ui.kids

import android.widget.Toast
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chronosbvrx.visionplus.data.CatalogRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.follow.FollowStore
import com.chronosbvrx.visionplus.data.kids.KidsMode
import com.chronosbvrx.visionplus.data.stats.formatMinutes
import com.chronosbvrx.visionplus.data.stats.kindLabel
import com.chronosbvrx.visionplus.data.stats.summarize
import com.chronosbvrx.visionplus.ui.settings.SettingsBlock
import com.chronosbvrx.visionplus.ui.settings.SettingsSwitchRow
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.chronosbvrx.visionplus.ui.tv.TvDialog
import com.chronosbvrx.visionplus.ui.tv.dialogFocus
import com.chronosbvrx.visionplus.ui.tv.rememberDialogFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hileras filtradas a infantil cuando el bloqueo está activo (recompone al cambiar). */
@Composable
fun rememberLockedIndexRows(rows: List<CatalogRow>): List<CatalogRow> {
    val locked = KidsMode.locked
    return remember(rows, locked) { if (locked) KidsMode.kidsRows(rows) else rows }
}

/** Diálogo de PIN (crear o verificar). `setup=true` crea; si no, verifica. */
@Composable
fun MobileKidsPinDialog(setup: Boolean, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setup) "Crear PIN de niños" else "PIN de niños") },
        text = {
            Column {
                Text(
                    if (setup) "De 4 a 8 dígitos. Solo tú podrás salir del modo niños."
                    else "Escribe el PIN para continuar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = "" },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotBlank()) {
                    Text(error, color = androidx.compose.ui.graphics.Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!KidsMode.isValidPin(pin)) {
                    error = "El PIN lleva de 4 a 8 dígitos."
                    return@TextButton
                }
                onOk(pin)
            }) { Text("Aceptar", color = VisionBlue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Bloque de Modo niños para Ajustes (móvil): crear PIN, bloquear y cambiar PIN. */
@Composable
fun KidsSettingsBlock() {
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(KidsMode.hasPin(context)) }
    var lockedTick by remember { mutableStateOf(0) }
    var askSetup by remember { mutableStateOf(false) }
    var askVerifyForUnlock by remember { mutableStateOf(false) }
    var askVerifyForChange by remember { mutableStateOf(false) }
    remember(lockedTick) { KidsMode.locked }

    if (askSetup) {
        MobileKidsPinDialog(setup = true, onDismiss = { askSetup = false }, onOk = { pin ->
            if (KidsMode.setPin(context, pin)) {
                hasPin = true
                askSetup = false
                Toast.makeText(context, "PIN creado", Toast.LENGTH_SHORT).show()
            }
        })
    }
    if (askVerifyForUnlock) {
        MobileKidsPinDialog(setup = false, onDismiss = { askVerifyForUnlock = false }, onOk = { pin ->
            if (KidsMode.unlock(context, pin)) {
                askVerifyForUnlock = false
                lockedTick++
            } else {
                Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            }
        })
    }
    if (askVerifyForChange) {
        MobileKidsPinDialog(setup = false, onDismiss = { askVerifyForChange = false }, onOk = { pin ->
            if (KidsMode.verify(context, pin)) {
                askVerifyForChange = false
                askSetup = true
            } else {
                Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            }
        })
    }

    SettingsBlock {
        Text("Modo niños", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Solo muestra contenido infantil. En vivo, deportes, lista y descargas piden el PIN.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        if (!hasPin) {
            OutlinedButton(onClick = { askSetup = true }) { Text("Crear PIN") }
        } else {
            Row {
                OutlinedButton(onClick = { askVerifyForChange = true }) { Text("Cambiar PIN") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    if (KidsMode.locked) askVerifyForUnlock = true
                    else {
                        KidsMode.lock(context)
                        lockedTick++
                    }
                }) { Text(if (KidsMode.locked) "Desbloquear" else "Bloquear") }
            }
        }
    }
    SettingsSwitchRow(
        title = "Bloqueo infantil activo",
        checked = KidsMode.locked,
        onCheckedChange = { want ->
            if (want) {
                if (!hasPin) askSetup = true
                else {
                    KidsMode.lock(context)
                    lockedTick++
                }
            } else {
                askVerifyForUnlock = true
            }
        },
        supporting = "Con el bloqueo, el Inicio y las secciones solo muestran infantil.",
    )
}

/** «Mi actividad» para Ajustes (móvil): todo calculado en el equipo, nada se sube. */
@Composable
fun ActivitySettingsBlock() {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            val entries = PlaybackHistory.list(context)
            val films = UserLibrary.played(context).associateBy { it.id }
            val stats = summarize(entries, films)
            val following = FollowStore.followed(context).size
            buildList {
                if (stats.isEmpty && following == 0) return@buildList
                if (stats.minutesWatched > 0) add("Tiempo visto" to formatMinutes(stats.minutesWatched))
                if (stats.episodesFinished > 0) add("Capítulos terminados" to "${stats.episodesFinished}")
                if (stats.filmsFinished > 0) add("Películas terminadas" to "${stats.filmsFinished}")
                if (stats.topKind.isNotBlank()) add("Lo que más ves" to kindLabel(stats.topKind))
                if (following > 0) add("Series que sigues" to "$following")
            }
        }
        lines = result
        loaded = true
    }
    SettingsBlock {
        Text("Mi actividad", style = MaterialTheme.typography.bodyLarge)
        if (!loaded) {
            Text("Calculando…", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
        } else if (lines.isEmpty()) {
            Text(
                "Aún no hay actividad: reproduce algo y aquí verás tu resumen.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
        } else {
            lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

/** Diálogo de PIN para TV (mismo flujo que en móvil). */
@Composable
fun TvKidsPinDialog(setup: Boolean, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val focus = rememberDialogFocus()
    TvDialog(title = if (setup) "Crear PIN de niños" else "PIN de niños", onDismiss = onDismiss) {
        androidx.tv.material3.Text(
            if (setup) "De 4 a 8 dígitos." else "Escribe el PIN para continuar.",
            style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        androidx.tv.material3.Text(
            "•".repeat(pin.length).ifBlank { "····" },
            style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
        )
        if (error.isNotBlank()) {
            androidx.tv.material3.Text(error, style = androidx.tv.material3.MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        // Teclado en DOS filas de cinco: una sola fila de diez no cabe en el panel y dejaba
        // teclas fuera de la pantalla (el «0» era el primero en perderse).
        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        digits.chunked(5).forEach { rowDigits ->
            Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowDigits.forEachIndexed { index, d ->
                    androidx.tv.material3.Button(
                        onClick = {
                            if (pin.length < 8) pin += d
                            error = ""
                        },
                        modifier = if (d == "1") Modifier.dialogFocus(focus) else Modifier,
                    ) { androidx.tv.material3.Text(d) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.tv.material3.Button(onClick = {
                if (!KidsMode.isValidPin(pin)) {
                    error = "El PIN lleva de 4 a 8 dígitos."
                    return@Button
                }
                onOk(pin)
            }) { androidx.tv.material3.Text("Aceptar") }
            androidx.tv.material3.Button(onClick = {
                pin = pin.dropLast(1)
            }) { androidx.tv.material3.Text("Borrar") }
        }
    }
}
