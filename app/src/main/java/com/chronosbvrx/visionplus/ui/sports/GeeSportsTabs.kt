package com.chronosbvrx.visionplus.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

private val GeeLiveRed = Color(0xFFD32F2F)

/** "Football" → "Fútbol" (y los demás deportes tal cual). */
internal fun geeCategoryLabel(category: String): String = when (category.lowercase()) {
    "football" -> "Fútbol"
    "basketball" -> "Básquet"
    "american football" -> "Americano"
    "motorsports" -> "F1"
    "baseball" -> "Béisbol"
    "tennis" -> "Tenis"
    "aussie rules" -> "Aussie rules"
    else -> category
}

/** "19:00:00" → "19:00". */
internal fun geeTimeLabel(time: String): String = time.take(5)

/** El badge de estado de un enlace según el sondeo del servidor. */
@Composable
internal fun GeeAliveBadge(alive: Boolean?) {
    val (label, color) = when (alive) {
        true -> "EN VIVO" to GeeLiveRed
        null -> "Sin verificar" to VisionTextSecondary
        else -> "Caído" to VisionTextSecondary
    }
    if (alive == true) {
        Surface(color = color, shape = RoundedCornerShape(50)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    } else {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * "Canales": los grupos 24/7 de GeeSports (TNT Sports, FOX, BeIN…). Cada canal
 * muestra cuántos enlaces tiene; al abrir se eligen las opciones.
 */
@Composable
fun GeeChannelsTab(
    state: GeeSportsState,
    onPlay: (LiveChannel) -> Unit,
    bottomPadding: Dp,
) {
    var pickingChannel by remember { mutableStateOf<GeeSportsSource.GeeChannel?>(null) }
    var pickingGroup by remember { mutableStateOf("") }

    when {
        state.groups.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando canales…", color = VisionTextSecondary)
        }
        state.groups.isEmpty() -> EmptyState("Sin canales", subtitle = "No se pudo cargar el catálogo de canales deportivos.")
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.groups.forEach { group ->
                item(key = "g_${group.id}") { GeeGroupHeader(group) }
                items(
                    group.channels,
                    key = { "c_${group.id}_${it.name}_${it.links.firstOrNull()?.link?.hashCode() ?: 0}" },
                ) { channel ->
                    GeeChannelRow(channel) {
                        if (channel.links.size == 1) {
                            onPlay(GeeSportsSource.toChannel(channel.links.first(), channel.name, "Deportes en vivo"))
                        } else {
                            pickingChannel = channel
                            pickingGroup = group.name
                        }
                    }
                }
            }
        }
    }

    pickingChannel?.let { channel ->
        GeeLinkPickDialog(
            title = channel.name,
            subtitle = pickingGroup,
            links = channel.links,
            onDismiss = { pickingChannel = null },
            onPlay = { picked ->
                pickingChannel = null
                onPlay(picked)
            },
        )
    }
}

@Composable
private fun GeeGroupHeader(group: GeeSportsSource.GeeGroup) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
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
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(group.name, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${group.alive}/${group.total} en vivo", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    }
}

@Composable
private fun GeeChannelRow(channel: GeeSportsSource.GeeChannel, onClick: () -> Unit) {
    val alive = channel.links.count { it.alive == true }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = VisionSurfaceHigh.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logo)
                        .size(56, 56)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (alive > 0) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(GeeLiveRed))
                Spacer(Modifier.width(5.dp))
                Text("$alive", style = MaterialTheme.typography.labelLarge, color = Color.White)
            } else {
                Text(
                    "${channel.links.size} enlaces",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.PlayArrow, contentDescription = "Ver", tint = VisionBlue, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * El selector de transmisión: los canales de TV reales de la lista (resueltos de
 * la guía del partido) y las transmisiones directas (vivas primero, el servidor ya
 * las ordena). La guía («se transmite en: TUDN · ViX») siempre va visible.
 */
@Composable
fun GeeLinkPickDialog(
    title: String,
    subtitle: String,
    links: List<GeeSportsSource.GeeLink>,
    onDismiss: () -> Unit,
    onPlay: (LiveChannel) -> Unit,
    guide: List<String> = emptyList(),
    tvChannels: List<LiveChannel> = emptyList(),
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = VisionSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                if (guide.isNotEmpty()) {
                    Text(
                        "Se transmite en: " + guide.take(4).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionBlueBright,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (tvChannels.isEmpty() && links.isEmpty()) {
                    Text(
                        "Sin transmisiones disponibles por ahora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        if (tvChannels.isNotEmpty()) {
                            item(key = "tv_label") {
                                Text(
                                    "Canales de TV (tu lista):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VisionTextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            items(tvChannels, key = { "tv_" + it.url }) { channel ->
                                Surface(
                                    onClick = { onPlay(channel) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = VisionSurfaceHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VisionBlue, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        if (!channel.logo.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(channel.logo)
                                                    .size(56, 56)
                                                    .crossfade(false)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                contentScale = ContentScale.Fit,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                channel.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text("Canal de TV en vivo", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                                        }
                                    }
                                }
                            }
                            item(key = "tv_gap") { Spacer(Modifier.height(2.dp)) }
                        }
                        if (links.isNotEmpty()) {
                            item(key = "links_label") {
                                Text(
                                    "Transmisiones en vivo:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VisionTextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            items(links, key = { it.name + "|" + it.link }) { link ->
                                Surface(
                                    onClick = {
                                        onPlay(GeeSportsSource.toChannel(link, link.name.ifBlank { title }, "Deportes en vivo"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = VisionSurfaceHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VisionBlue, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                link.name.ifBlank { title },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val typeText = when {
                                                link.type == "dash" && link.drm.isNotBlank() -> "DASH · ClearKey"
                                                link.type == "dash" -> "DASH"
                                                else -> "HLS"
                                            }
                                            Text(typeText, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        GeeAliveBadge(link.alive)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
            }
        }
    }
}
