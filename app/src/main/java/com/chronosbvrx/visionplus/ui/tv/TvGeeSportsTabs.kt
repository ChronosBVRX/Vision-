package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsReminder
import com.chronosbvrx.visionplus.ui.sports.GeeEmbedResolveDialog
import com.chronosbvrx.visionplus.ui.sports.GeePickRow
import com.chronosbvrx.visionplus.ui.sports.resolvedWith
import com.chronosbvrx.visionplus.ui.sports.GeeSportsState
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.sports.ZAP_HINT
import com.chronosbvrx.visionplus.ui.sports.geePickRows
import com.chronosbvrx.visionplus.ui.sports.xtvPickRows
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/**
 * El badge de la SEÑAL de un enlace (si responde o no). Habla del enlace, NO de
 * si el partido está en vivo: por eso dice «Disponible» y no «EN VIVO».
 */
@Composable
private fun TvGeeSignalBadge(alive: Boolean?, playable: Boolean = true, resolvable: Boolean = false) {
    if (!playable) {
        Text("Sin señal", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
        return
    }
    if (resolvable) {
        Text("Al tocar", style = MaterialTheme.typography.bodySmall, color = VisionBlueBright)
        return
    }
    when (alive) {
        true -> Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = VisionSuccess)) {
            Text(
                "Disponible",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF06170F),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        null -> Text("Sin verificar", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
        else -> Text("Caído", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    }
}

/**
 * "Canales" en la TV: los grupos 24/7 de GeeSports. Cada canal abre sus enlaces.
 */
@Composable
internal fun TvGeeChannelsTab(
    state: GeeSportsState,
    onPlay: (LiveChannel) -> Unit,
    entryRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<GeeSportsSource.GeeChannel?>(null) }
    var pickingGroup by remember { mutableStateOf("") }
    var focusReturn by remember { mutableStateOf<FocusRequester?>(null) }
    val firstChannel = state.groups.firstOrNull()?.channels?.firstOrNull()
    val firstGroupId = state.groups.firstOrNull()?.id

    Column(modifier) {
        if (state.groups.isEmpty()) {
            Text("No se pudo cargar el catálogo de canales deportivos.", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "g_${group.id}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (group.logo.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(group.logo)
                                        .size(48, 48)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(group.name, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${group.alive}/${group.total} disponibles", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                        }
                    }
                    items(
                        group.channels,
                        key = { "c_${group.id}_${it.name}_${it.links.firstOrNull()?.link?.hashCode() ?: 0}" },
                    ) { channel ->
                        val isFirst = group.id == firstGroupId && channel.name == firstChannel?.name
                        val own = remember { FocusRequester() }
                        TvGeeChannelRow(
                            channel = channel,
                            modifier = Modifier.focusRequester(if (isFirst) entryRequester else own),
                            onClick = {
                                if (channel.links.size == 1) {
                                    onPlay(GeeSportsSource.toChannel(channel.links.first(), channel.name, "Deportes en vivo"))
                                } else {
                                    focusReturn = if (isFirst) entryRequester else own
                                    picking = channel
                                    pickingGroup = group.name
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(picking) {
        if (picking == null) {
            delay(60)
            focusReturn?.let { runCatching { it.requestFocus() } }
        }
    }

    picking?.let { channel ->
        TvGeeLinkPickDialog(
            title = channel.name,
            subtitle = pickingGroup,
            links = channel.links,
            onDismiss = { picking = null },
            onPlay = { picked ->
                picking = null
                onPlay(picked)
            },
        )
    }
}

@Composable
private fun TvGeeChannelRow(
    channel: GeeSportsSource.GeeChannel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val alive = channel.links.count { it.alive == true }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(TAB_HEIGHT),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurfaceHigh,
            focusedContainerColor = VisionBlue,
            pressedContainerColor = VisionBlue.copy(alpha = 0.4f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logo)
                        .size(56, 56)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(channel.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            if (alive > 0) {
                Text("$alive disponibles", style = MaterialTheme.typography.labelLarge, color = VisionSuccess)
            } else {
                Text("${channel.links.size} enlaces", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * El selector de transmisiones en la TV: UNA sola lista. Primero la cadena de la **guía
 * oficial** del partido resuelta en el catálogo deportivo (ESPN, TUDN, Fox Sports…), luego
 * las señales directas de la fuente. OK sintoniza esa fuente.
 *
 * Con [playable] en false (el partido aún no comienza o ya terminó) NO se ofrecen
 * señales: solo se muestra el aviso [blockNote].
 *
 * [checks] son las comprobaciones de vida que la app acaba de hacer (mandan sobre el
 * dato del servidor). No hay respaldo al catálogo de la casa: si una fuente está caída,
 * se ve caída. [playingUrl] marca y enfoca la opción que se estaba viendo al volver.
 */
@Composable
internal fun TvGeeLinkPickDialog(
    title: String,
    subtitle: String,
    links: List<GeeSportsSource.GeeLink>,
    onDismiss: () -> Unit,
    onPlay: (LiveChannel) -> Unit,
    playable: Boolean = true,
    blockNote: String = "",
    checks: Map<String, GeeHealth.Check> = emptyMap(),
    eventInfo: GeeZapSignals.EventInfo? = null,
    playingUrl: String? = null,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    canRemind: Boolean = false,
    reminderActive: Boolean = false,
    onRemind: ((Int) -> Unit)? = null,
    onClearReminder: (() -> Unit)? = null,
    xtv: List<GeeSportsSource.XtvOption> = emptyList(),
) {
    // Primero la segunda opción propia (XTV, ya confirmada por el servidor); debajo,
    // las señales que publica GeeSports. No se toca la agenda ni sus enlaces.
    val geeRows = remember(title, links, checks, playingUrl) {
        geePickRows(title, links, checks, playingUrl)
    }
    // GeeSports manda: es la fuente que está entregando los partidos. La
    // segunda opción de la casa solo aparece como RESPALDO cuando GeeSports no
    // publicó ninguna señal sintonizable; además, la casa abre el motor nativo
    // (sin el HUD de estadísticas).
    val xtvRows = remember(xtv, playingUrl, geeRows) {
        if (geeRows.any { it.playable }) emptyList() else xtvPickRows(xtv, playingUrl)
    }
    val rows = remember(geeRows, xtvRows) { geeRows + xtvRows }
    var resolving by remember { mutableStateOf<GeePickRow?>(null) }
    // Zapping dentro del bloque de opciones: la lista ORDENADA del evento queda armada
    // para el reproductor en línea, que la recoge al montar. Solo entran las señales que
    // la lista muestra como disponibles; el ciclo nunca sale de las de ESTE partido.
    val zapOrder = remember(rows) {
        rows.filter { it.type != "xtv" && it.playable && it.alive != false && it.channel.url.isNotBlank() }
            .map { it.channel }
            .distinctBy { it.url }
    }
    val liveRows = remember(rows) { rows.filter { it.alive == true && it.type != "xtv" } }
    val listState = rememberLazyListState()
    val playingIndex = rows.indexOfFirst { it.playing }
    val landingIndex = if (playingIndex >= 0) playingIndex else 0
    val rowFocus = remember(rows) { List(rows.size) { FocusRequester() } }
    val closeFocus = remember { FocusRequester() }
    // La opción que se estaba viendo queda a la vista (y con el foco) al volver.
    LaunchedEffect(rows.size, playingIndex) {
        if (playingIndex > 0) runCatching { listState.scrollToItem(playingIndex + 1) }
    }

    fun playZap(picked: LiveChannel) {
        // Un canal XTV no entra al zapping de GeeSports: lo abre el reproductor XTV.
        if (!picked.url.startsWith("xtvlive:", ignoreCase = true)) {
            GeeZapSignals.arm(picked, zapOrder, eventInfo)
        }
        onPlay(picked)
    }
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    val modalLanding = rememberLandingFocus("tv_gee_pick_$title")
    LaunchedEffect(title, rows.size, landingIndex) {
        delay(120)
        rowFocus.getOrNull(landingIndex)?.let { runCatching { it.requestFocus() } }
            ?: modalLanding.request()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .modalFocusTrap()
                .dismissOnBack(onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                colors = SurfaceDefaults.colors(containerColor = VisionSurface),
                border = Border(BorderStroke(1.dp, VisionBlue.copy(alpha = 0.4f)), shape = RoundedCornerShape(18.dp)),
                modifier = Modifier.width(560.dp).modalFocusTrap(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    if (!playable) {
                        Text(
                            blockNote.ifBlank { "Aún no comienza · podrás verlo cuando el partido esté en vivo." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = VisionTextSecondary,
                        )
                        if (canRemind && onRemind != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Avisarme antes:",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTextSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SportsReminder.OPTIONS.forEach { minutes ->
                                    TvCompactAction("$minutes min", onClick = { onRemind(minutes) })
                                }
                                if (reminderActive && onClearReminder != null) {
                                    TvCompactAction("Quitar", onClick = onClearReminder)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        TvPickFooter(
                            favorite = favorite,
                            onToggleFavorite = onToggleFavorite,
                            closeLabel = "Volver",
                            onClose = onDismiss,
                            closeModifier = Modifier.landingFocus(modalLanding),
                        )
                    } else if (rows.isEmpty()) {
                        Text("Sin enlaces disponibles", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                        Spacer(Modifier.height(14.dp))
                        TvPickFooter(
                            favorite = favorite,
                            onToggleFavorite = onToggleFavorite,
                            closeLabel = "Volver",
                            onClose = onDismiss,
                            closeModifier = Modifier.landingFocus(modalLanding),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        ) {
                            item(key = "links_label") {
                                Text(
                                    "Transmisiones disponibles:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VisionTextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                Text(
                                    if (xtvRows.isNotEmpty()) {
                                        "GeeSports no publicó una señal utilizable; opciones de la casa como respaldo."
                                    } else {
                                        "Usa el mando para elegir una señal."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionBlueBright.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            itemsIndexed(rows, key = { index, row -> "link_${index}_${row.link}_${row.title}" }) { index, row ->
                                TvGeeLinkItem(
                                    row = row,
                                    onClick = {
                                        if (row.playable) {
                                            if (row.type == "xtv") {
                                                playZap(row.channel)
                                            } else if (row.link.isBlank() && row.tokenApi.isNotBlank()) {
                                                resolving = row
                                            } else {
                                                playZap(row.channel)
                                            }
                                        }
                                    },
                                    // Navegación direccional POR DEFECTO. Antes se apuntaba a
                                    // `rowFocus[index±1]`/`closeFocus`: si la fila destino aún
                                    // no estaba compuesta (LazyColumn) o el pie no se había
                                    // montado, el mando lanzaba «FocusRequester is not
                                    // initialized» al bajar rápido. El LazyColumn ya lleva el
                                    // foco a la siguiente fila visible y al pie.
                                    modifier = Modifier.focusRequester(
                                        rowFocus.getOrElse(index) { FocusRequester() },
                                    ),
                                )
                            }
                            if (liveRows.isNotEmpty()) {
                                item(key = "links_note") {
                                    Text(
                                        ZAP_HINT,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VisionBlueBright,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        TvPickFooter(
                            favorite = favorite,
                            onToggleFavorite = onToggleFavorite,
                            closeLabel = "Cerrar",
                            onClose = onDismiss,
                            closeModifier = Modifier.focusRequester(closeFocus),
                        )
                    }
                }
            }
        }
    }

    resolving?.let { row ->
        GeeEmbedResolveDialog(
            tokenApi = row.tokenApi,
            title = row.title,
            onResolved = { stream ->
                resolving = null
                playZap(row.resolvedWith(stream))
            },
            onDismiss = { resolving = null },
        )
    }
}

@Composable
private fun TvPickFooter(
    favorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    closeLabel: String,
    onClose: () -> Unit,
    closeModifier: Modifier = Modifier,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleFavorite != null) {
            TvCompactAction(
                label = if (favorite) "Quitar favorito" else "Favorito",
                onClick = onToggleFavorite,
            )
            Spacer(Modifier.width(8.dp))
        }
        TvCompactAction(closeLabel, onClick = onClose, modifier = closeModifier)
    }
}

@Composable
private fun TvGeeLinkItem(
    row: GeePickRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (row.playing) Color(0xFF173A66) else VisionSurfaceHigh,
            focusedContainerColor = VisionBlue,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (row.playing) Border(BorderStroke(2.dp, VisionBlueBright), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (row.playing) VisionBlueBright else Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.type == "xtv") VisionBlueBright else VisionTextSecondary,
                )
                if (row.note.isNotBlank()) {
                    Text(
                        row.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Solo el nombre del canal y su formato: la dirección técnica no se muestra.
                if (!row.playable) {
                    Text(
                        "La fuente aún no publica su señal",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.playing) {
                Spacer(Modifier.width(10.dp))
                TvGeePlayingBadge()
            }
            Spacer(Modifier.width(10.dp))
            TvGeeSignalBadge(
                row.alive,
                playable = row.playable,
                resolvable = row.link.isBlank() && row.tokenApi.isNotBlank(),
            )
        }
    }
}

/** La opción que se estaba viendo: al volver del reproductor el modal la trae marcada. */
@Composable
private fun TvGeePlayingBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        colors = SurfaceDefaults.colors(containerColor = VisionBlueBright),
        border = Border(
            BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(50),
        ),
    ) {
        Text(
            "Seleccionada",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = Color(0xFF06121F),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}
