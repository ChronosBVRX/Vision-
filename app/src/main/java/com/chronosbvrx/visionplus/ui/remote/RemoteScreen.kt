package com.chronosbvrx.visionplus.ui.remote

import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.layout.ContentScale
import android.os.SystemClock
import android.widget.Toast
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.remote.NowPlaying
import com.chronosbvrx.visionplus.data.remote.RemoteClient
import com.chronosbvrx.visionplus.data.remote.RemoteKeys
import com.chronosbvrx.visionplus.data.remote.RemoteProto
import com.chronosbvrx.visionplus.data.remote.RemoteStatus
import com.chronosbvrx.visionplus.data.remote.RemoteTv
import com.chronosbvrx.visionplus.data.remote.resumePositionFor
import com.chronosbvrx.visionplus.data.remote.tvRowLabel
import com.chronosbvrx.visionplus.data.searchFilms
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.formatDuration
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * El control remoto del teléfono: encuentra la tele de la misma red, se empareja una vez con el
 * PIN que la tele muestra y a partir de ahí la maneja (D-pad, reproducción, volumen) y le abre
 * contenido sin bucear en el catálogo con el mando.
 */
@Composable
fun RemoteScreen(catalog: XuperCatalog, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by RemoteClient.state

    // Al abrir: si ya hubo una tele vinculada, se intenta sola; si no, se busca en la red.
    LaunchedEffect(Unit) {
        if (RemoteClient.state.value.status == RemoteStatus.IDLE) {
            val last = RemoteClient.lastTv(context)
            val remembered = RemoteClient.rememberedTvs(context).firstOrNull()
            when {
                last != null && RemoteClient.isPaired(context, last) -> RemoteClient.connect(context, last)
                remembered != null -> RemoteClient.connect(context, remembered)
                else -> RemoteClient.search()
            }
        }
    }
    // La conexión se queda viva al salir: así «Ver en la TV» desde una ficha funciona al instante.
    DisposableEffect(Unit) { onDispose { } }

    if (state.status == RemoteStatus.NEED_PIN) {
        PinDialog(
            wrong = state.error != null,
            onSubmit = { RemoteClient.submitPin(it) },
            onDismiss = { RemoteClient.disconnect() },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        RemoteHeader(tv = state.tv, connected = state.status == RemoteStatus.CONNECTED, onBack = onBack)
        Spacer(Modifier.height(12.dp))

        when (state.status) {
            RemoteStatus.CONNECTED -> {
                if (state.nowPlaying.active) {
                    NowPlayingCard(state.nowPlaying, state.nowReceivedAt)
                    Spacer(Modifier.height(14.dp))
                } else if (state.now.isNotBlank()) {
                    Text(
                        "En la tele: ${state.now}",
                        style = MaterialTheme.typography.titleSmall,
                        color = VisionBlueBright,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                RemoteControls()
                Spacer(Modifier.height(18.dp))
                CastPanel(catalog)
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { RemoteClient.disconnect() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Desconectar", color = VisionTextSecondary)
                }
            }

            RemoteStatus.CONNECTING, RemoteStatus.NEED_PIN -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VisionBlue)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (state.status == RemoteStatus.NEED_PIN) "Escribe el PIN que muestra la tele" else "Conectando…",
                            color = VisionTextSecondary,
                        )
                    }
                }
            }

            RemoteStatus.SEARCHING -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VisionBlue)
                        Spacer(Modifier.height(14.dp))
                        Text("Buscando teles en esta red…", color = VisionTextSecondary)
                    }
                }
            }

            RemoteStatus.IDLE, RemoteStatus.ERROR -> DiscoveryPanel(state.error)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * La tarjeta «Ahora en la tele»: arte, título, minuto, barra arrastrable y transporte.
 * En vivo no hay barra (no se puede buscar hacia atrás); se muestran canal y play/pausa.
 */
@Composable
private fun NowPlayingCard(np: NowPlaying, receivedAt: Long) {
    var drag by remember(np.ref) { mutableStateOf<Long?>(null) }
    var pendingSeek by remember(np.ref) { mutableStateOf<Long?>(null) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.updatedAt, np.playing) {
        while (np.playing) {
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek != null) {
            delay(3_000)
            pendingSeek = null
        }
    }
    LaunchedEffect(np.updatedAt, np.positionMs) {
        val target = pendingSeek
        if (target != null && kotlin.math.abs(np.positionMs - target) < 3_000L) pendingSeek = null
    }

    val duration = np.durationMs.coerceAtLeast(0L)
    val elapsed = if (np.playing && receivedAt > 0L) {
        (tick - receivedAt).coerceAtLeast(0L)
    } else {
        0L
    }
    val base = if (np.playing) np.positionMs + elapsed else np.positionMs
    val shown = (drag ?: pendingSeek ?: base).coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VisionSurface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (np.poster.isNotBlank()) {
                AsyncImage(
                    model = np.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(58.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VisionSurfaceHigh),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (np.live) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(VisionLiveRed)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "EN VIVO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        np.title.ifBlank { "En la tele" },
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (np.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        np.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (np.live) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TransportButton(Icons.Default.KeyboardArrowDown, "Canal anterior") {
                    RemoteClient.sendKey(RemoteKeys.CHANNEL_DOWN)
                }
                TransportButton(
                    if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (np.playing) "Pausar" else "Reproducir",
                    emphasized = true,
                ) { RemoteClient.sendCommand(RemoteProto.CMD_PLAY_PAUSE) }
                TransportButton(Icons.Default.KeyboardArrowUp, "Canal siguiente") {
                    RemoteClient.sendKey(RemoteKeys.CHANNEL_UP)
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Slider(
                value = if (duration > 0L) shown.toFloat() else 0f,
                onValueChange = { if (duration > 0L) drag = it.toLong() },
                onValueChangeFinished = {
                    val target = drag
                    if (target != null) {
                        pendingSeek = target
                        RemoteClient.sendSeek(target)
                    }
                    drag = null
                },
                valueRange = 0f..(if (duration > 0L) duration.toFloat() else 1f),
                enabled = np.canSeek && duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = VisionBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (duration > 0L) formatDuration(shown) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (duration > 0L) formatDuration((duration - shown).coerceAtLeast(0L)) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TransportButton(Icons.Default.SkipPrevious, "Capítulo anterior", enabled = np.hasPrev) {
                    RemoteClient.sendCommand(RemoteProto.CMD_PREVIOUS)
                }
                TransportButton(Icons.Default.Replay10, "Atrasar 10 segundos") {
                    RemoteClient.sendSeek((shown - 10_000L).coerceAtLeast(0L))
                }
                TransportButton(
                    if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (np.playing) "Pausar" else "Reproducir",
                    emphasized = true,
                ) { RemoteClient.sendCommand(RemoteProto.CMD_PLAY_PAUSE) }
                TransportButton(Icons.Default.Forward10, "Adelantar 10 segundos") {
                    RemoteClient.sendSeek(
                        (shown + 10_000L).coerceAtMost(if (duration > 0L) duration else shown + 10_000L),
                    )
                }
                TransportButton(Icons.Default.SkipNext, "Capítulo siguiente", enabled = np.hasNext) {
                    RemoteClient.sendCommand(RemoteProto.CMD_NEXT)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(
                onClick = { RemoteClient.sendCommand(RemoteProto.CMD_AUDIO) },
                colors = ButtonDefaults.textButtonColors(contentColor = VisionBlueBright),
            ) {
                Text("Audio")
            }
            if (!np.live) {
                TextButton(
                    onClick = { RemoteClient.sendCommand(RemoteProto.CMD_SUBTITLES) },
                    colors = ButtonDefaults.textButtonColors(contentColor = VisionBlueBright),
                ) {
                    Text("Subtítulos")
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(if (emphasized) 52.dp else 46.dp)
            .clip(CircleShape)
            .background(if (emphasized) VisionBlue else VisionSurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(if (emphasized) 30.dp else 24.dp),
        )
    }
}

@Composable
private fun RemoteHeader(tv: RemoteTv?, connected: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(VisionSurfaceHigh)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VisionIcons.Back, contentDescription = "Atrás", tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Control remoto", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (tv != null) tv.name.ifBlank { tv.host } else "Ninguna tele conectada",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connected) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(VisionSuccess.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Conectado", style = MaterialTheme.typography.labelMedium, color = VisionSuccess)
            }
        }
    }
}

@Composable
private fun DiscoveryPanel(error: String?) {
    val context = LocalContext.current
    val state by RemoteClient.state
    // Las teles que el teléfono ya vinculó: entran solas (sin PIN) y funcionan aunque el
    // descubrimiento no las vea (dirección cambiada, difusión filtrada por el router).
    val remembered = remember(state.status, state.tvs) { RemoteClient.rememberedTvs(context) }
    Column(Modifier.fillMaxWidth()) {
        Icon(VisionIcons.Globe, contentDescription = null, tint = VisionBlue, modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text(
            "Pon el teléfono y la tele en la misma red Wi-Fi. La tele debe tener Vision+ abierto.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { RemoteClient.search() },
            colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Buscar teles en mi red")
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (remembered.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Tus teles vinculadas", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
            remembered.forEach { tv ->
                TvRow(tv = tv, paired = true, onClick = { RemoteClient.connect(context, tv) })
            }
        }
        if (state.tvs.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Encontradas (${state.tvs.size})", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
            state.tvs.forEach { tv ->
                TvRow(
                    tv = tv,
                    paired = RemoteClient.isPaired(context, tv),
                    onClick = { RemoteClient.connect(context, tv) },
                )
            }
        }
    }
}

@Composable
private fun TvRow(tv: RemoteTv, paired: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VisionSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Tv, contentDescription = null, tint = VisionBlueBright)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tv.name.ifBlank { "Vision+ TV" }, color = VisionTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tv.model.ifBlank { tv.host }, color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            tvRowLabel(paired),
            color = if (paired) VisionSuccess else VisionBlueBright,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** El D-pad, la reproducción y el volumen. */
@Composable
private fun RemoteControls() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // D-pad
        DpadRow {
            Spacer(Modifier.size(64.dp))
            RoundKey(Icons.Default.KeyboardArrowUp, "Arriba") { RemoteClient.sendKey(RemoteKeys.UP) }
            Spacer(Modifier.size(64.dp))
        }
        Spacer(Modifier.height(10.dp))
        DpadRow {
            RoundKey(Icons.Default.KeyboardArrowLeft, "Izquierda") { RemoteClient.sendKey(RemoteKeys.LEFT) }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VisionBlue)
                    .clickable { RemoteClient.sendKey(RemoteKeys.CENTER) },
                contentAlignment = Alignment.Center,
            ) { Text("OK", color = Color.White, fontWeight = FontWeight.Bold) }
            RoundKey(Icons.Default.KeyboardArrowRight, "Derecha") { RemoteClient.sendKey(RemoteKeys.RIGHT) }
        }
        Spacer(Modifier.height(10.dp))
        DpadRow {
            Spacer(Modifier.size(64.dp))
            RoundKey(Icons.Default.KeyboardArrowDown, "Abajo") { RemoteClient.sendKey(RemoteKeys.DOWN) }
            Spacer(Modifier.size(64.dp))
        }

        Spacer(Modifier.height(18.dp))
        // Navegación de la tele
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey(Icons.Default.SkipPrevious, "Atrás", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.BACK) }
            WideKey(Icons.Default.Menu, "Menú", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.MENU) }
            WideKey(Icons.Default.Info, "Info", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.INFO) }
        }

        Spacer(Modifier.height(14.dp))
        // Reproducción
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey(Icons.Default.SkipPrevious, "Retroceder", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.REWIND) }
            WideKey(Icons.Default.Pause, "Play/Pausa", Modifier.weight(1f), emphasized = true) { RemoteClient.sendKey(RemoteKeys.PLAY_PAUSE) }
            WideKey(Icons.Default.SkipNext, "Adelantar", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.FAST_FORWARD) }
        }

        Spacer(Modifier.height(14.dp))
        // Canal (en vivo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey(Icons.Default.KeyboardArrowDown, "Canal anterior", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.CHANNEL_DOWN) }
            Text(
                "Canales en vivo",
                color = VisionTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp),
            )
            WideKey(Icons.Default.KeyboardArrowUp, "Canal siguiente", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.CHANNEL_UP) }
        }

        Spacer(Modifier.height(14.dp))
        // Volumen
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey(Icons.Default.VolumeDown, "Bajar volumen", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.VOLUME_DOWN) }
            WideKey(Icons.Default.VolumeOff, "Silenciar", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.MUTE) }
            WideKey(Icons.Default.VolumeUp, "Subir volumen", Modifier.weight(1f)) { RemoteClient.sendKey(RemoteKeys.VOLUME_UP) }
        }
    }
}

@Composable
private fun DpadRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun RoundKey(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(VisionSurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun WideKey(icon: ImageVector, desc: String, modifier: Modifier = Modifier, emphasized: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (emphasized) VisionBlue else VisionSurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** El buscador para abrir contenido en la tele sin pelear con el mando. */
@Composable
private fun CastPanel(catalog: XuperCatalog) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // La serie cuyo temporada/capítulo se está eligiendo (null = sin selector abierto).
    var pickingFilm by remember { mutableStateOf<Film?>(null) }
    val rows = catalog.indexRows.ifEmpty { catalog.rows }
    val channels = remember { runCatching { X4KApi.cachedLiveChannels(context) }.getOrDefault(emptyList()) }
    var results by remember { mutableStateOf<List<CastItem>>(emptyList()) }
    // 1) El catálogo local responde al instante (todo el índice en memoria). 2) En paralelo (con
    // debounce) se pregunta al servidor: es el mismo buscador del Inicio y cubre el catálogo
    // completo, así que encontrar aquí es tan seguro como buscar en la app de la tele.
    LaunchedEffect(query, rows, channels) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        val localFilms = withContext(Dispatchers.Default) { searchFilms(rows, q) }
        results = withContext(Dispatchers.Default) { buildCastResults(localFilms, channels, q) }
        delay(250)
        val serverFilms = runCatching { RemoteIndexSource.search(q, limit = 60) }.getOrDefault(emptyList())
        if (serverFilms.isNotEmpty()) {
            val merged = withContext(Dispatchers.Default) {
                buildCastResults(serverFilms + localFilms, channels, q)
            }
            if (merged.isNotEmpty()) results = merged
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cast, contentDescription = null, tint = VisionBlueBright)
            Spacer(Modifier.width(8.dp))
            Text("Abrir en la tele", style = MaterialTheme.typography.titleMedium, color = VisionTextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Película, serie o canal…", color = VisionTextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                if (query.isNotBlank()) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Borrar",
                        tint = VisionTextSecondary,
                        modifier = Modifier.clickable { query = "" },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        results.forEach { item ->
            CastRow(item) {
                // Una serie no se manda «en general»: primero se elige temporada y capítulo.
                val vod = item as? CastItem.Vod
                if (vod != null && isCastSeries(vod.film)) {
                    pickingFilm = vod.film
                } else {
                    RemoteClient.sendOpen(
                        item.ref,
                        item.title,
                        item.art.orEmpty(),
                        positionMs = resumePositionFor(context, item.ref),
                    )
                }
            }
        }
        if (query.trim().length >= 2 && results.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Nada que se pueda abrir con eso.", color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }

    pickingFilm?.let { film ->
        CastEpisodeDialog(
            film = film,
            onDismiss = { pickingFilm = null },
            onSend = { ep ->
                RemoteClient.sendOpen(
                    ep.ref,
                    ep.title,
                    ep.poster,
                    seriesId = ep.seriesId,
                    seasonIndex = ep.seasonIndex,
                    seasonIds = ep.seasonIds,
                    seasonNames = ep.seasonNames,
                    positionMs = resumePositionFor(context, ep.ref),
                )
                Toast.makeText(context, "Abriendo ${ep.title} en la tele…", Toast.LENGTH_SHORT).show()
                pickingFilm = null
            },
            onSendTitle = {
                RemoteClient.sendOpen(
                    film.videoUrl,
                    film.title,
                    film.posterUrl,
                    positionMs = resumePositionFor(context, film.videoUrl),
                )
                Toast.makeText(context, "Abriendo en la tele…", Toast.LENGTH_SHORT).show()
                pickingFilm = null
            },
        )
    }
}

@Composable
private fun CastRow(item: CastItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VisionSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.art.isNullOrBlank()) {
                AsyncImage(model = item.art, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Tv, contentDescription = null, tint = VisionTextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = VisionTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            when {
                item is CastItem.Channel -> "Canal"
                item is CastItem.Vod && isCastSeries(item.film) -> "Capítulos"
                else -> "Reproducir"
            },
            color = VisionBlueBright,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PinDialog(wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dismissOnBack { onDismiss() },
        containerColor = VisionSurface,
        titleContentColor = VisionTextPrimary,
        textContentColor = VisionTextSecondary,
        title = { Text("Emparejar con la tele") },
        text = {
            Column {
                Text("Escribe el PIN de 4 dígitos que muestra la pantalla de la tele.")
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4) },
                    singleLine = true,
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text("PIN incorrecto, inténtalo de nuevo.", color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (pin.length == 4) onSubmit(pin) }, enabled = pin.length == 4) {
                Text("Emparejar", color = VisionBlueBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = VisionTextSecondary) }
        },
    )
}
