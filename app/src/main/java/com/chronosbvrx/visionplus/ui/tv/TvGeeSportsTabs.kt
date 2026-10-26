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
import com.chronosbvrx.visionplus.ui.sports.GeeSportsState
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.sports.ZAP_HINT
import com.chronosbvrx.visionplus.ui.sports.linkDisplay
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
private fun TvGeeSignalBadge(alive: Boolean?) {
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
 * El selector de transmisiones en la TV: SOLO las señales que GeeSports publica para el
 * partido (con dirección real), vivos primero. OK sintoniza esa fuente.
 *
 * Con [playable] en false (el partido aún no comienza o ya terminó) NO se ofrecen
 * señales: solo se muestra el aviso [blockNote].
 *
 * [checks] son las comprobaciones de vida que la app acaba de hacer (mandan sobre el
 * dato del servidor). No hay respaldo al catálogo: si una fuente está caída, se ve caída.
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
) {
    // El estado REAL de cada señal: lo comprobado por la app ahora mismo manda sobre el
    // dato del servidor (que puede tener minutos).
    fun aliveOf(link: GeeSportsSource.GeeLink): Boolean? =
        GeeHealth.effective(link.alive, checks[link.link.trim()])
    // Solo las señales que GeeSports publica con una dirección real. Las ranuras que la
    // fuente declara sin URL no se ofrecen: la lista es de fuentes, no de promesas.
    val linkRows = remember(links) {
        links.filter { it.link.isNotBlank() }.distinctBy { it.link }
    }
    // Zapping dentro del bloque de opciones: la lista ORDENADA del evento queda armada
    // para el reproductor en línea, que la recoge al montar.
    val zapOrder = remember(linkRows) {
        linkRows.map { link ->
            GeeSportsSource.toChannel(link, link.name.ifBlank { title }, "Deportes en vivo")
        }.distinctBy { it.url }
    }
    val liveLinks = remember(linkRows, checks) { linkRows.filter { aliveOf(it) == true } }

    fun playZap(picked: LiveChannel) {
        GeeZapSignals.arm(picked, zapOrder, eventInfo)
        onPlay(picked)
    }
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    val modalLanding = rememberLandingFocus("tv_gee_pick_$title")
    LaunchedEffect(title) {
        delay(120)
        modalLanding.request()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .modalFocusTrap(),
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
                        Spacer(Modifier.height(14.dp))
                        TvCompactAction("Volver", onClick = onDismiss, modifier = Modifier.landingFocus(modalLanding))
                    } else if (linkRows.isEmpty()) {
                        Text("Sin transmisiones disponibles por ahora.", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                        Spacer(Modifier.height(14.dp))
                        TvCompactAction("Volver", onClick = onDismiss, modifier = Modifier.landingFocus(modalLanding))
                    } else {
                        val firstLink = linkRows.firstOrNull()
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        ) {
                            item(key = "links_label") {
                                Text(
                                    "Transmisiones de GeeSports:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VisionTextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            itemsIndexed(linkRows, key = { index, link -> "link_${index}_${link.link}" }) { _, link ->
                                TvGeeLinkItem(
                                    link = link,
                                    alive = aliveOf(link),
                                    fallbackTitle = title,
                                    onClick = {
                                        playZap(GeeSportsSource.toChannel(link, link.name.ifBlank { title }, "Deportes en vivo"))
                                    },
                                    modifier = if (link == firstLink) Modifier.landingFocus(modalLanding) else Modifier,
                                )
                            }
                            if (liveLinks.isNotEmpty()) {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TvCompactAction("Cerrar", onClick = onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvGeeLinkItem(
    link: GeeSportsSource.GeeLink,
    alive: Boolean?,
    fallbackTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val typeText = when {
        link.link.isBlank() -> "La fuente aún no publica su señal"
        link.type == "dash" && link.drm.isNotBlank() -> "DASH · ClearKey"
        link.type == "dash" -> "DASH"
        else -> "HLS"
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurfaceHigh,
            focusedContainerColor = VisionBlue,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(link.name.ifBlank { fallbackTitle }, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    typeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                // El enlace a la vista: la fuente declara canales que pueden estar dando otro partido.
                if (link.link.isNotBlank()) {
                    Text(
                        linkDisplay(link.link),
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            TvGeeSignalBadge(alive)
        }
    }
}
