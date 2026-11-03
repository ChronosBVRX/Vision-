package com.chronosbvrx.visionplus.ui.sports

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
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
import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsLinker
import com.chronosbvrx.visionplus.data.sports.SportsReminder
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** "Football" → "Fútbol" (y los demás deportes tal cual). */
internal fun geeCategoryLabel(category: String): String = when (category.lowercase()) {
    "football" -> "Fútbol"
    "basketball" -> "Básquet"
    "american football" -> "Americano"
    "motorsport", "motorsports" -> "Motor"
    "baseball" -> "Béisbol"
    "hockey" -> "Hockey"
    "tennis" -> "Tenis"
    "fighting", "pelea", "peleas" -> "Peleas"
    "boxing", "boxeo" -> "Boxeo"
    "mma" -> "MMA"
    "rugby" -> "Rugby"
    "volleyball" -> "Voleibol"
    "handball" -> "Balonmano"
    "cricket" -> "Cricket"
    "aussie rules" -> "Aussie rules"
    else -> category
}

/** "19:00:00" → "19:00". */
internal fun geeTimeLabel(time: String): String = time.take(5)

/**
 * La recomendación del intercalado (móvil y TV): la fuente declara canales que pueden estar dando
 * OTRO partido, así que se dice claro y se explica cómo cambiar hasta encontrar el bueno.
 */
internal const val ZAP_HINT =
    "Arriba/Abajo cambia solo entre las señales disponibles de este partido. " +
        "Si alguna no lo está dando, elige otra de la lista."

/**
 * El badge de estado de un enlace según el sondeo del servidor. Habla de la
 * SEÑAL (si el enlace responde), no de si el partido está en vivo: por eso dice
 * «Disponible» y no «EN VIVO» (esa palabra queda solo para partidos jugándose).
 */
@Composable
internal fun GeeAliveBadge(alive: Boolean?, playable: Boolean = true, resolvable: Boolean = false) {
    if (!playable) {
        Text("Sin señal", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
        return
    }
    if (resolvable) {
        Text("Al tocar", style = MaterialTheme.typography.bodySmall, color = VisionBlueBright)
        return
    }
    val (label, color) = when (alive) {
        true -> "Disponible" to VisionSuccess
        null -> "Sin verificar" to VisionTextSecondary
        else -> "Caído" to VisionTextSecondary
    }
    if (alive == true) {
        Surface(color = color, shape = RoundedCornerShape(50)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF06170F),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    } else {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** La opción que se estaba viendo: al volver del reproductor el modal la trae marcada. */
@Composable
internal fun GeePlayingBadge() {
    Surface(
        color = VisionBlueBright,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
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
        Text("${group.alive}/${group.total} disponibles", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
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
                Box(Modifier.size(7.dp).clip(CircleShape).background(VisionSuccess))
                Spacer(Modifier.width(5.dp))
                Text("$alive disponibles", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
 * El selector de transmisión: UNA sola lista con las señales del partido. Primero la
 * cadena de la **guía oficial** que sí lo transmite (resuelta en el catálogo deportivo:
 * ESPN, TUDN, Fox Sports…), después las transmisiones directas de la fuente (vivas
 * primero, el servidor ya las ordena). Las ranuras sin URL no se ofrecen y no hay
 * respaldo al catálogo de la casa: si una fuente está caída, se ve caída.
 *
 * Con [playable] en false (el partido aún no comienza o ya terminó) NO se ofrecen
 * señales para reproducir: solo se muestra el aviso [blockNote].
 *
 * [checks] son las comprobaciones de vida que la app acaba de hacer (mandan sobre el
 * dato del servidor: una caída y un levantamiento se ven al momento). [playingUrl] es la
 * señal que se estaba viendo al salir: al volver del reproductor el modal reabre con
 * esa opción marcada y enfocada.
 */
@Composable
internal fun GeeLinkPickDialog(
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
    // la lista muestra como disponibles (las caídas y las ranuras sin dirección no se
    // sintonizan); el ciclo nunca sale de las transmisiones de ESTE partido.
    val zapOrder = remember(rows) {
        rows.filter { it.type != "xtv" && it.playable && it.alive != false && it.channel.url.isNotBlank() }
            .map { it.channel }
            .distinctBy { it.url }
    }
    val liveRows = remember(rows) { rows.filter { it.alive == true && it.type != "xtv" } }
    val listState = rememberLazyListState()
    val playingIndex = rows.indexOfFirst { it.playing }
    // La opción que se estaba viendo queda a la vista al volver del reproductor.
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
    Dialog(onDismissRequest = onDismiss) {
        BackHandler(onBack = onDismiss)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = VisionSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).dismissOnBack(onDismiss),
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
                Spacer(Modifier.height(14.dp))
                if (!playable) {
                    Text(
                        blockNote.ifBlank { "Aún no comienza · podrás verlo cuando el partido esté en vivo." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                    )
                    if (canRemind && onRemind != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Avisarme antes:",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SportsReminder.OPTIONS.forEach { minutes ->
                                TextButton(onClick = { onRemind(minutes) }) {
                                    Text("$minutes min")
                                }
                            }
                            if (reminderActive && onClearReminder != null) {
                                TextButton(onClick = onClearReminder) { Text("Quitar") }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                } else if (rows.isEmpty()) {
                    Text(
                        "Sin enlaces disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        item(key = "links_label") {
                            Text(
                                "Transmisiones disponibles:",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTextSecondary,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                            if (xtvRows.isNotEmpty()) {
                                Text(
                                    "GeeSports no publicó una señal utilizable; opciones de la casa como respaldo.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionBlueBright.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        itemsIndexed(rows, key = { index, row -> "link_${index}_${row.link}_${row.title}" }) { _, row ->
                            val selected = row.playing
                            Surface(
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
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    selected -> Color(0xFF173A66)
                                    row.playable -> VisionSurfaceHigh
                                    else -> VisionSurfaceHigh.copy(alpha = 0.55f)
                                },
                                border = if (selected) {
                                    androidx.compose.foundation.BorderStroke(2.dp, VisionBlueBright)
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (selected) VisionBlueBright else VisionBlue,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            row.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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
                                        // La dirección de la fuente no se enseña: solo el
                                        // nombre del canal y su formato. Las ranuras sin señal
                                        // explican que la fuente aún no publica su dirección.
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
                                        Spacer(Modifier.width(8.dp))
                                        GeePlayingBadge()
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    GeeAliveBadge(
                                        row.alive,
                                        playable = row.playable,
                                        resolvable = row.link.isBlank() && row.tokenApi.isNotBlank(),
                                    )
                                }
                            }
                        }
                        if (liveRows.isNotEmpty()) {
                            item(key = "links_note") {
                                Text(
                                    ZAP_HINT,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VisionBlueBright,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onToggleFavorite != null) {
                        TextButton(onClick = onToggleFavorite) {
                            Text(if (favorite) "Quitar favorito" else "Favorito")
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
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
