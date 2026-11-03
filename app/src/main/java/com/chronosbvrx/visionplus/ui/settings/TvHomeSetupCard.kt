package com.chronosbvrx.visionplus.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.data.adb.ApkState
import com.chronosbvrx.visionplus.data.adb.TvAppPackage
import com.chronosbvrx.visionplus.data.adb.TvCandidate
import com.chronosbvrx.visionplus.data.adb.TvHomeSetup
import com.chronosbvrx.visionplus.data.adb.TvLauncherPackage
import com.chronosbvrx.visionplus.data.adb.TvRecord
import com.chronosbvrx.visionplus.data.debloat.AppLabels
import com.chronosbvrx.visionplus.data.debloat.CleanupCandidate
import com.chronosbvrx.visionplus.data.debloat.CleanupGroup
import com.chronosbvrx.visionplus.data.debloat.CleanupPlan
import com.chronosbvrx.visionplus.data.debloat.CleanupSource
import com.chronosbvrx.visionplus.data.debloat.TvStorage
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HeroShape = RoundedCornerShape(22.dp)
private val TileShape = RoundedCornerShape(18.dp)
private val FieldShape = RoundedCornerShape(14.dp)

private sealed interface SetupRequest {
    data object New : SetupRequest
    data class Saved(val id: String) : SetupRequest
}

/**
 * «Mi tele» en Ajustes del teléfono. Una sola entrada clara: la persona pulsa «Mi tele está
 * lista», la app busca las teles de la red y las enseña como tarjetas. Al elegir una, revisa
 * la limpieza y deja Vision+ con su Inicio. Sin párrafos técnicos ni pasos escondidos.
 */
@Composable
internal fun TvHomeSetupSection() {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<SetupRequest?>(null) }
    var forgetting by remember { mutableStateOf<TvRecord?>(null) }

    LaunchedEffect(Unit) { TvHomeSetup.load(context) }

    Column(Modifier.fillMaxWidth()) {
        TvHeroCard(
            cleanupEnabled = state.cleanupEnabled,
            onCleanupChange = { TvHomeSetup.setCleanupEnabled(context, it) },
            onReady = { request = SetupRequest.New },
        )
        if (state.tvs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            state.tvs.forEach { tv ->
                SavedTvCard(
                    tv = tv,
                    busy = state.busyId == tv.id,
                    onUpdate = { request = SetupRequest.Saved(tv.id) },
                    onSpace = { scope.launch { TvHomeSetup.openSpace(context, tv.id) } },
                    onCheck = { scope.launch { TvHomeSetup.checkTv(context, tv.id) } },
                    onAds = { scope.launch { TvHomeSetup.openAds(context, tv.id) } },
                    onRestore = {
                        scope.launch {
                            TvHomeSetup.restore(context, tv.id)
                            Toast.makeText(
                                context,
                                if (TvHomeSetup.state.value.phase == TvHomeSetup.Phase.ERROR) {
                                    "No se pudo restaurar. Revisa la tele."
                                } else {
                                    "Tele restaurada"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onForget = { forgetting = tv },
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "Android TV y Google TV. Sin PC.",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        } else {
            Text(
                "Android TV y Google TV. Sin PC.",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
    }

    request?.let { req ->
        TvSetupDialog(req, onDismiss = {
            request = null
            TvHomeSetup.cancel()
        })
    }

    state.tool?.let { tool ->
        TvToolDialog(
            tool = tool,
            onClear = { scope.launch { TvHomeSetup.clearSpace(context) } },
            onEnableAds = { family -> scope.launch { TvHomeSetup.enableAds(context, family) } },
            onDisableAds = { scope.launch { TvHomeSetup.disableAds(context) } },
            onFix = { id ->
                TvHomeSetup.closeTool()
                request = SetupRequest.Saved(id)
            },
            onDismiss = { TvHomeSetup.closeTool() },
        )
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
private fun TvHeroCard(
    cleanupEnabled: Boolean,
    onCleanupChange: (Boolean) -> Unit,
    onReady: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF12346B), VisionSurfaceHigh),
                ),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(VisionBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mi tele", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
                Text(
                    "Limpia el bloatware y deja Vision+ listo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onReady,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mi tele está lista", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Limpiar antes de instalar", style = MaterialTheme.typography.bodySmall, color = VisionTextPrimary)
                Text(
                    "Solo anuncios, rastreo y apps preinstaladas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
            Switch(checked = cleanupEnabled, onCheckedChange = onCleanupChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedTvCard(
    tv: TvRecord,
    busy: Boolean,
    onUpdate: () -> Unit,
    onSpace: () -> Unit,
    onCheck: () -> Unit,
    onAds: () -> Unit,
    onRestore: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(VisionSurfaceHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.displayName, style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary)
                Text(
                    when {
                        tv.applied -> "Vision+ Home es su Inicio"
                        tv.paired -> "Lista para instalar"
                        else -> "Sin configurar"
                    } + if (tv.cleaned.isNotEmpty()) " · ${tv.cleaned.size} apps apagadas" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tv.applied) VisionSuccess else VisionTextSecondary,
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (tv.appVersion.isNotBlank() || tv.launcherVersion.isNotBlank()) {
            Text(
                listOfNotNull(
                    tv.appVersion.takeIf { it.isNotBlank() }?.let { "Vision+ $it" },
                    tv.launcherVersion.takeIf { it.isNotBlank() }?.let { "Home $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onUpdate,
                enabled = !busy,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = FieldShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            ) {
                Text(if (tv.launcherVersion.isBlank()) "Instalar" else "Actualizar")
            }
            OutlinedButton(
                onClick = onRestore,
                enabled = !busy,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = FieldShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            ) {
                Text("Restaurar")
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextButton(onClick = onSpace, enabled = !busy, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Rounded.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Espacio")
            }
            TextButton(onClick = onCheck, enabled = !busy, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Revisar")
            }
            TextButton(onClick = onAds, enabled = !busy, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Anuncios")
            }
            TextButton(onClick = onForget, enabled = !busy, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                Text("Olvidar")
            }
        }
    }
}

@Composable
private fun TvSetupDialog(request: SetupRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by TvHomeSetup.state
    val scope = rememberCoroutineScope()
    var code by remember(state.pairingPort) { mutableStateOf("") }
    var showIp by remember { mutableStateOf(false) }
    var showLiveLog by remember { mutableStateOf(true) }

    LaunchedEffect(request) {
        when (request) {
            SetupRequest.New -> TvHomeSetup.discover(context)
            is SetupRequest.Saved -> TvHomeSetup.install(context, request.id)
        }
    }

    val title = when (request) {
        SetupRequest.New -> "Preparar mi tele"
        is SetupRequest.Saved -> state.tvs.firstOrNull { it.id == request.id }?.displayName ?: "Actualizar tele"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = VisionTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = VisionTextSecondary)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    when {
                        state.phase == TvHomeSetup.Phase.IDLE || state.phase == TvHomeSetup.Phase.SEARCHING ->
                            SearchContent(
                                showIp = showIp,
                                onShowIp = { showIp = !showIp },
                                onRetry = { scope.launch { TvHomeSetup.discover(context) } },
                            )

                        state.phase == TvHomeSetup.Phase.READY && state.pairingPort > 0 ->
                            PairingContent(
                                code = code,
                                onCode = { code = it },
                                onConnect = { scope.launch { TvHomeSetup.pairAndApply(context, code) } },
                                onBack = { TvHomeSetup.backToCandidates() },
                            )

                        state.phase == TvHomeSetup.Phase.READY ->
                            CandidatesContent(
                                candidates = state.candidates,
                                showIp = showIp,
                                onShowIp = { showIp = !showIp },
                                onPick = { host -> scope.launch { TvHomeSetup.connectCandidate(context, host) } },
                                onRetry = { scope.launch { TvHomeSetup.discover(context) } },
                            )

                        state.phase == TvHomeSetup.Phase.REVIEW ->
                            ReviewContent(
                                plan = state.plan,
                                selected = state.cleanupSelected,
                                onToggleGroup = { group, enabled -> TvHomeSetup.toggleGroup(context, group, enabled) },
                                onToggleApp = { pkg -> TvHomeSetup.toggleApp(context, pkg) },
                                onConfirm = { scope.launch { TvHomeSetup.confirm(context) } },
                                onCancel = onDismiss,
                            )

                        state.phase == TvHomeSetup.Phase.WORKING ->
                            WorkingContent(
                                message = state.message,
                                onCancel = onDismiss,
                            )

                        state.phase == TvHomeSetup.Phase.DONE ->
                            DoneContent(
                                state = state,
                                onDone = onDismiss,
                            )

                        else -> ErrorContent(
                            message = state.message,
                            request = request,
                            onRetry = {
                                scope.launch {
                                    when (request) {
                                        SetupRequest.New -> TvHomeSetup.discover(context)
                                        is SetupRequest.Saved -> TvHomeSetup.install(context, request.id)
                                    }
                                }
                            },
                            onWriteIp = { showIp = true },
                            onDismiss = onDismiss,
                        )
                    }
                    if (showIp && (
                            state.phase == TvHomeSetup.Phase.IDLE ||
                                state.phase == TvHomeSetup.Phase.SEARCHING ||
                                state.phase == TvHomeSetup.Phase.ERROR ||
                                (state.phase == TvHomeSetup.Phase.READY && state.pairingPort == 0)
                            )
                    ) {
                        ManualIpField(context)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                LiveLog(
                    log = state.log,
                    phase = state.phase,
                    lastLogAt = state.lastLogAt,
                    expanded = showLiveLog,
                    onToggle = { showLiveLog = !showLiveLog },
                )
            }
        }
    }
}

@Composable
private fun SearchContent(
    showIp: Boolean,
    onShowIp: () -> Unit,
    onRetry: () -> Unit,
) {
    Spacer(Modifier.height(18.dp))
    Box(
        Modifier.size(58.dp).clip(CircleShape).background(VisionBlue.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = VisionBlue)
    }
    Spacer(Modifier.height(14.dp))
    Text("Buscando tu tele…", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
    Text(
        "Misma Wi-Fi que el teléfono.",
        style = MaterialTheme.typography.bodyMedium,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        "En la tele: activa Depuración inalámbrica en Ajustes → Sistema → Desarrollador.",
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        "Si la tele pide permiso, acepta «Permitir depuración».",
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(Modifier.padding(top = 14.dp)) {
        TextButton(onClick = onShowIp) { Text(if (showIp) "Ocultar IP" else "Escribir la IP") }
        TextButton(onClick = onRetry) { Text("Buscar otra vez") }
    }
}

@Composable
private fun CandidatesContent(
    candidates: List<TvCandidate>,
    showIp: Boolean,
    onShowIp: () -> Unit,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Text("Elige tu tele", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
    Text(
        "Toca la que quieres preparar.",
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
    )
    candidates.forEach { candidate ->
        TvCandidateCard(candidate, onPick)
        Spacer(Modifier.height(8.dp))
    }
    if (candidates.any { !it.hasAdb }) {
        Text(
            "Las que dicen «Activa depuración» necesitan la depuración encendida en la tele.",
            style = MaterialTheme.typography.labelSmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(Modifier.padding(top = 8.dp)) {
        TextButton(onClick = onRetry) { Text("Buscar otra vez") }
        TextButton(onClick = onShowIp) { Text(if (showIp) "Ocultar IP" else "Escribir la IP") }
    }
}

@Composable
private fun TvCandidateCard(candidate: TvCandidate, onPick: (String) -> Unit) {
    val enabled = candidate.hasAdb
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(VisionSurfaceHigh)
            .clickable(enabled = enabled) { onPick(candidate.host) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(VisionBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.title, style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary)
            Text(
                candidate.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    candidate.needsCode -> VisionBlueBright
                    candidate.hasAdb -> VisionSuccess
                    else -> VisionTextSecondary
                },
            )
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = VisionTextSecondary,
            )
        } else {
            Text("Falta depuración", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        }
    }
}

@Composable
private fun PairingContent(
    code: String,
    onCode: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Escribe el código de la tele", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
    Text(
        "Está en «Emparejar dispositivo con código».",
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
    )
    CodeBoxes(code, onCode)
    Button(
        onClick = onConnect,
        enabled = code.length == 6,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
    ) {
        Text("Conectar")
    }
    TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
        Text("Elegir otra tele")
    }
}

@Composable
private fun CodeBoxes(code: String, onCode: (String) -> Unit) {
    BasicTextField(
        value = code,
        onValueChange = { onCode(it.filter(Char::isDigit).take(6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (index in 0 until 6) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VisionSurfaceHigh)
                            .border(
                                width = 2.dp,
                                color = if (index == code.length) VisionBlue else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            code.getOrNull(index)?.toString() ?: "–",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (index < code.length) VisionTextPrimary else VisionTextSecondary,
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewContent(
    plan: CleanupPlan?,
    selected: Set<String>,
    onToggleGroup: (CleanupGroup, Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (plan == null) {
        Text("No pude preparar la limpieza.", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary)
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            shape = FieldShape,
        ) { Text("Instalar Vision+") }
        TextButton(onClick = onCancel) { Text("Cancelar") }
        return
    }
    val matched = plan.candidates.filter { it.source != CleanupSource.OTHER }
    val others = plan.candidates.filter { it.source == CleanupSource.OTHER }
    SelectionSummary(selected.size, plan.candidates.size)
    Text(plan.tv.displayName.ifBlank { "Esta tele" }, style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
    Text(
        "Android ${plan.tv.release.ifBlank { "?" }} · ${plan.tv.model.ifBlank { "TV" }}",
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
    )
    when {
        plan.match == null -> Text(
            "No hay lista para este modelo. Abajo puedes elegir apps de otras listas.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
        plan.tested -> Text(
            "Lista probada en este modelo",
            style = MaterialTheme.typography.labelLarge,
            color = VisionSuccess,
            modifier = Modifier.padding(top = 10.dp),
        )
        else -> Text(
            "Modelo parecido: elige solo lo que conozcas.",
            style = MaterialTheme.typography.labelLarge,
            color = VisionRatingGold,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    CleanupGroup.entries.forEach { group ->
        val apps = matched.filter { it.group == group }
        if (apps.isNotEmpty()) CleanupGroupCard(group, apps, selected, onToggleGroup, onToggleApp)
    }
    if (others.isNotEmpty()) ExtraAppsCard(others, selected, onToggleApp)
    Text(
        if (selected.isEmpty()) {
            "No se apagará ninguna app."
        } else {
            "Se apagarán ${selected.size} apps. Nada se desinstala."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected.isEmpty()) VisionTextSecondary else VisionBlueBright,
        modifier = Modifier.padding(top = 14.dp),
    )
    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
    ) {
        Text(if (selected.isEmpty()) "Instalar Vision+" else "Limpiar e instalar (${selected.size})")
    }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
}

@Composable
private fun SelectionSummary(selected: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(Brush.linearGradient(listOf(Color(0xFF153C78), VisionSurfaceHigh)))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (total == 0) 0f else selected.toFloat() / total },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 5.dp,
                color = VisionBlueBright,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Text("$selected", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                if (selected == 1) "1 app seleccionada" else "$selected apps seleccionadas",
                style = MaterialTheme.typography.titleMedium,
                color = VisionTextPrimary,
            )
            Text(
                "$total disponibles · nada se desinstala",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanupGroupCard(
    group: CleanupGroup,
    apps: List<CleanupCandidate>,
    selected: Set<String>,
    onToggleGroup: (CleanupGroup, Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
) {
    val main = apps.filter { it.source == CleanupSource.MATCHED }
    val allOn = main.isNotEmpty() && main.all { it.pkg in selected }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(TileShape)
            .background(VisionSurfaceHigh)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupBadge(group)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${group.title} (${apps.size})", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary)
                Text(
                    groupHint(group),
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
            if (main.isNotEmpty()) {
                Switch(checked = allOn, onCheckedChange = { onToggleGroup(group, it) })
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            apps.forEach { app -> AppChoiceChip(app, app.pkg in selected, onToggleApp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraAppsCard(
    apps: List<CleanupCandidate>,
    selected: Set<String>,
    onToggleApp: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) apps else apps.take(24)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(TileShape)
            .background(VisionSurfaceHigh)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(VisionBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Más apps (${apps.size})", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary)
                Text(
                    "De otras listas · ninguna marcada",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visible.forEach { app -> AppChoiceChip(app, app.pkg in selected, onToggleApp) }
        }
        if (apps.size > 24) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ver menos" else "Ver las ${apps.size} apps")
            }
        }
    }
}

@Composable
private fun AppChoiceChip(app: CleanupCandidate, picked: Boolean, onToggle: (String) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (picked) VisionBlue else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (picked) VisionBlueBright else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(50),
            )
            .clickable { onToggle(app.pkg) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(app.pkg, app.label, 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (picked) Color.White else VisionTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        if (app.source == CleanupSource.OPTIONAL) {
            Text(" ·", style = MaterialTheme.typography.labelSmall, color = if (picked) Color.White.copy(alpha = 0.7f) else VisionTextSecondary)
        }
    }
}

@Composable
private fun GroupBadge(group: CleanupGroup) {
    val tint = groupTint(group)
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(groupIcon(group), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun groupIcon(group: CleanupGroup): ImageVector = when (group) {
    CleanupGroup.ADS -> Icons.Rounded.Campaign
    CleanupGroup.PRELOADED -> Icons.Rounded.Apps
    CleanupGroup.VOICE -> Icons.Rounded.Mic
    CleanupGroup.CASTING -> Icons.Rounded.Cast
    CleanupGroup.UPDATES -> Icons.Rounded.SystemUpdate
    CleanupGroup.STREAMING -> Icons.Rounded.PlayArrow
}

private fun groupTint(group: CleanupGroup): Color = when (group) {
    CleanupGroup.ADS -> VisionLiveRed
    CleanupGroup.PRELOADED -> VisionRatingGold
    CleanupGroup.VOICE -> Color(0xFFA98BFF)
    CleanupGroup.CASTING -> Color(0xFF16C7D9)
    CleanupGroup.UPDATES -> VisionTextSecondary
    CleanupGroup.STREAMING -> VisionSuccess
}

private fun groupHint(group: CleanupGroup): String = when (group) {
    CleanupGroup.ADS -> "Anuncios, rastreo y telemetría"
    CleanupGroup.PRELOADED -> "Apps que no necesitas"
    CleanupGroup.VOICE -> "Puede quitar el micrófono del mando"
    CleanupGroup.CASTING -> "Puede quitar casting y espejo"
    CleanupGroup.UPDATES -> "Puede detener actualizaciones"
    CleanupGroup.STREAMING -> "Solo si no las usas"
}

private val AVATAR_COLORS = listOf(
    Color(0xFF2D7DF6),
    Color(0xFF8B5CF6),
    Color(0xFF16A3C9),
    Color(0xFFF59E0B),
    Color(0xFF10B981),
    Color(0xFFEF4444),
    Color(0xFFEC4899),
)

private fun avatarColor(pkg: String): Color =
    AVATAR_COLORS[(pkg.hashCode() and Int.MAX_VALUE) % AVATAR_COLORS.size]

private fun initials(label: String): String =
    label.split(Regex("[^\\p{L}\\p{N}+]+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

@Composable
private fun AppAvatar(pkg: String, label: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColor(pkg)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (size.value * 0.38f).sp),
            color = Color.White,
        )
    }
}

@Composable
private fun LiveLog(
    log: List<String>,
    phase: TvHomeSetup.Phase,
    lastLogAt: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex)
    }
    val active = phase == TvHomeSetup.Phase.SEARCHING || phase == TvHomeSetup.Phase.WORKING
    val idle = if (lastLogAt <= 0L) 0L else ((now - lastLogAt) / 1_000L).coerceAtLeast(0L)
    Column(
        Modifier
            .fillMaxWidth()
            .background(VisionBlack.copy(alpha = 0.55f))
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = VisionBlueBright)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(VisionTextSecondary))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Registro en vivo",
                style = MaterialTheme.typography.labelLarge,
                color = VisionTextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                Text(
                    if (idle >= 30) "sin pasos hace ${idle}s" else "último paso hace ${idle}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (idle >= 30) VisionRatingGold else VisionTextSecondary,
                )
            }
            TextButton(
                onClick = onToggle,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(if (expanded) "Ocultar" else "Registro (${log.size})")
            }
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(8.dp),
            ) {
                if (log.isEmpty()) {
                    Text(
                        "Esperando el primer paso…",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                    )
                } else {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(log) { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (line.contains("!!")) VisionLiveRed else VisionTextSecondary,
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (log.isNotEmpty()) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(log.joinToString("\n"))) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Copiar") }
                }
            }
        }
    }
}

@Composable
private fun WorkingContent(
    message: String,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = VisionBlue)
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = VisionTextPrimary)
    }
    Spacer(Modifier.height(14.dp))
    ApkProgress("Vision+", TvAppPackage.state)
    ApkProgress("Vision+ Home", TvLauncherPackage.state)
    Text(
        "Esto puede tardar unos minutos.",
        style = MaterialTheme.typography.labelSmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancelar") }
}

@Composable
private fun DoneContent(
    state: com.chronosbvrx.visionplus.data.adb.TvHomeSetup.UiState,
    onDone: () -> Unit,
) {
    Spacer(Modifier.height(20.dp))
    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VisionSuccess, modifier = Modifier.size(58.dp))
    Spacer(Modifier.height(12.dp))
    Text("Tu tele ya usa Vision+", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
    Text(
        "Vision+ Home es su Inicio.",
        style = MaterialTheme.typography.bodyMedium,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    val tv = state.tvs.maxByOrNull { it.updatedAt }
    if (tv != null && tv.cleaned.isNotEmpty()) {
        Text(
            "Apagué ${tv.cleaned.size} apps. Puedes restaurarlas cuando quieras.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionBlueBright,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
    ) {
        Text("Listo")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    request: SetupRequest,
    onRetry: () -> Unit,
    onWriteIp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Spacer(Modifier.height(18.dp))
    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = VisionLiveRed, modifier = Modifier.size(52.dp))
    Spacer(Modifier.height(10.dp))
    Text("No se pudo", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
    Text(
        message.ifBlank { "Revisa que la tele esté despierta y en la misma Wi-Fi." },
        style = MaterialTheme.typography.bodyMedium,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
    ) {
        Text("Reintentar")
    }
    if (request is SetupRequest.New) {
        TextButton(onClick = onWriteIp, modifier = Modifier.fillMaxWidth()) { Text("Escribir la IP de la tele") }
    }
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
}

@Composable
private fun ManualIpField(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    val valid = address.count { it == '.' } == 3 && address.all { it.isDigit() || it == '.' }
    Column(Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
            label = { Text("IP de la tele") },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { scope.launch { TvHomeSetup.connectByAddress(context, address) } },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp),
            shape = FieldShape,
        ) {
            Text("Conectar")
        }
        Text(
            "La IP está en Ajustes → Red de la tele.",
            style = MaterialTheme.typography.labelSmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TvToolDialog(
    tool: TvHomeSetup.ToolState,
    onClear: () -> Unit,
    onEnableAds: (Boolean) -> Unit,
    onDisableAds: () -> Unit,
    onFix: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (tool.kind) {
                            TvHomeSetup.ToolKind.SPACE -> "Espacio"
                            TvHomeSetup.ToolKind.CHECK -> "Revisar mi tele"
                            TvHomeSetup.ToolKind.ADS -> "Anuncios"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = VisionTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = VisionTextSecondary)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (tool.loading) {
                        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = VisionBlue)
                            Spacer(Modifier.width(10.dp))
                            Text(tool.message.ifBlank { "Leyendo la tele…" }, color = VisionTextSecondary)
                        }
                    } else when (tool.kind) {
                        TvHomeSetup.ToolKind.SPACE -> SpaceToolBody(tool, onClear)
                        TvHomeSetup.ToolKind.CHECK -> CheckToolBody(tool, onFix)
                        TvHomeSetup.ToolKind.ADS -> AdsToolBody(tool, onEnableAds, onDisableAds)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceToolBody(tool: TvHomeSetup.ToolState, onClear: () -> Unit) {
    val st = tool.storage
    if (st == null) {
        Text(tool.message.ifBlank { "No pude leer el espacio." }, color = VisionTextSecondary, modifier = Modifier.padding(top = 12.dp))
        return
    }
    val total = st.totalKb ?: 0L
    val free = st.freeKb ?: 0L
    val used = (total - free).coerceAtLeast(0L)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(TileShape)
            .background(Brush.linearGradient(listOf(Color(0xFF153C78), VisionSurfaceHigh)))
            .padding(14.dp),
    ) {
        Text(
            if (total > 0) "${formatGb(free)} GB libres de ${formatGb(total)} GB" else "Espacio del televisor",
            style = MaterialTheme.typography.titleMedium,
            color = VisionTextPrimary,
        )
        if (total > 0) {
            LinearProgressIndicator(
                progress = { used.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(10.dp).clip(RoundedCornerShape(50)),
                color = VisionBlue,
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            "Caché estimada: ${st.cacheMb} MB",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Text(
        "Caché por app",
        style = MaterialTheme.typography.titleMedium,
        color = VisionTextPrimary,
        modifier = Modifier.padding(top = 14.dp),
    )
    if (st.top.isEmpty()) {
        Text("Android no tiene caches contabilizadas.", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    } else {
        st.top.forEach { (pkg, mb) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(pkg, AppLabels.label(pkg), 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    AppLabels.label(pkg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("$mb MB", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
            }
        }
    }
    Button(
        onClick = onClear,
        enabled = !tool.loading,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
    ) {
        Text(if (tool.message.startsWith("Caché liberada")) "Liberar otra vez" else "Liberar caché")
    }
    if (tool.message.isNotBlank()) {
        Text(
            tool.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (tool.message.startsWith("Caché liberada")) VisionSuccess else VisionTextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CheckToolBody(tool: TvHomeSetup.ToolState, onFix: (String) -> Unit) {
    val check = tool.check
    if (check == null) {
        Text(tool.message.ifBlank { "No pude revisar la tele." }, color = VisionTextSecondary, modifier = Modifier.padding(top = 12.dp))
        return
    }
    val ok = check.backOn.isEmpty() && !check.homeChanged
    if (ok) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = VisionSuccess, modifier = Modifier.size(46.dp))
        Text("Todo en orden", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary, modifier = Modifier.padding(top = 8.dp))
        Text(
            "${check.stillOff} apps siguen apagadas y Vision+ Home sigue siendo el Inicio.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = VisionRatingGold, modifier = Modifier.size(46.dp))
        Text("La tele cambió", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary, modifier = Modifier.padding(top = 8.dp))
        if (check.backOn.isNotEmpty()) {
            Text(
                "${check.backOn.size} apps se volvieron a encender.",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (check.homeChanged) {
            Text("El Inicio ya no es Vision+ Home.", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Button(
            onClick = { onFix(tool.tvId) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
        ) {
            Text("Revisar limpieza")
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckStat("Apagadas", check.stillOff, VisionSuccess)
        CheckStat("Encendidas otra vez", check.backOn.size, if (check.backOn.isEmpty()) VisionTextSecondary else VisionRatingGold)
    }
}

@Composable
private fun RowScope.CheckStat(label: String, value: Int, tint: Color) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(VisionSurfaceHigh)
            .padding(12.dp),
    ) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
    }
}

private fun formatGb(kb: Long): String =
    String.format(java.util.Locale.US, "%.1f", kb / 1024.0 / 1024.0)

@Composable
private fun AdsToolBody(
    tool: TvHomeSetup.ToolState,
    onEnable: (Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    val supported = !tool.message.contains("demasiado vieja", ignoreCase = true)
    Icon(
        Icons.Rounded.Block,
        contentDescription = null,
        tint = if (tool.dnsOn) VisionSuccess else VisionBlueBright,
        modifier = Modifier.size(46.dp),
    )
    Text(
        if (tool.dnsOn) "Bloqueo activo" else "Bloquear anuncios",
        style = MaterialTheme.typography.titleMedium,
        color = VisionTextPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "Filtra anuncios y rastreadores con Private DNS de AdGuard.",
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        "No bloquea los anuncios dentro de YouTube, Netflix o Prime Video.",
        style = MaterialTheme.typography.labelSmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (!supported) {
        Text(tool.message, style = MaterialTheme.typography.bodyMedium, color = VisionRatingGold, modifier = Modifier.padding(top = 12.dp))
        return
    }
    if (tool.dnsOn) {
        if (tool.dnsHost.isNotBlank()) {
            Text(
                "DNS: ${tool.dnsHost}",
                style = MaterialTheme.typography.labelSmall,
                color = VisionSuccess,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OutlinedButton(
            onClick = onDisable,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
            shape = FieldShape,
        ) { Text("Desactivar bloqueo") }
    } else {
        Button(
            onClick = { onEnable(false) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
            shape = FieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
        ) { Text("Activar bloqueo") }
        TextButton(onClick = { onEnable(true) }, modifier = Modifier.fillMaxWidth()) {
            Text("Versión familiar (también bloquea contenido adulto)")
        }
    }
    if (tool.message.isNotBlank()) {
        Text(
            tool.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (tool.dnsOn) VisionSuccess else VisionTextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ForgetTvDialog(
    tv: TvRecord,
    onRestoreAndForget: () -> Unit,
    onForgetAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasChanges = tv.disabled.isNotEmpty() || tv.cleaned.isNotEmpty() || tv.applied
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dismissOnBack { onDismiss() },
        title = { Text("Olvidar ${tv.displayName}") },
        text = {
            Text(
                if (hasChanges) {
                    "Se quitará del teléfono. La tele no cambia; si quieres devolverle su Inicio, usa «Restaurar»."
                } else {
                    "Se quitará de la lista. La tele no cambia."
                },
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
                if (hasChanges) TextButton(onClick = onForgetAnyway) { Text("Olvidar igual") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun ApkProgress(label: String, state: ApkState) {
    when (state) {
        ApkState.Idle -> Unit
        ApkState.Looking -> Text(
            "Buscando $label…",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        is ApkState.Downloading -> {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = VisionBlue,
            )
            Text(
                "Descargando $label… ${state.percent} %",
                style = MaterialTheme.typography.bodySmall,
                color = VisionBlueBright,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is ApkState.Ready -> Text(
            "$label listo",
            style = MaterialTheme.typography.bodySmall,
            color = VisionSuccess,
            modifier = Modifier.padding(top = 6.dp),
        )
        is ApkState.Failed -> Text(
            "No se pudo bajar $label",
            style = MaterialTheme.typography.bodySmall,
            color = VisionLiveRed,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
