package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeGuide
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.sports.geeCategoryLabel
import com.chronosbvrx.visionplus.ui.sports.geeEventSubtitle
import com.chronosbvrx.visionplus.ui.sports.geeEventTitle
import com.chronosbvrx.visionplus.ui.sports.rememberGeeSportsState
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

private val LiveRed = Color(0xFFD32F2F)

/**
 * "Deportes en vivo" en la TV: la fuente única (GeeSports) con los deportes
 * agrupados — fútbol al frente, ligas grandes con Liga MX primero — y los
 * bloques EN VIVO → COMIENZAN PRONTO → TERMINADOS. OK sobre una tarjeta abre el
 * selector de transmisiones; todo con el D-pad.
 */
@Composable
fun TvSportsScreen(onPlay: (LiveChannel) -> Unit) {
    val gee = rememberGeeSportsState()
    var league by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf(false) }
    var pickingId by remember { mutableStateOf(com.chronosbvrx.visionplus.ui.sports.DeportesReturn.pickingId) }
    val picking = remember(pickingId, gee.events) {
        pickingId?.let { id -> gee.events.firstOrNull { it.id == id } }
    }
    val geeEntryFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val pickingGuide = remember(picking) { picking?.broadcasts.orEmpty() }
    val pickingTv = remember(picking, gee.groups) {
        val event = picking
        if (event == null) emptyList()
        else runCatching { GeeGuide.channelsFor(event.broadcasts, gee.groups) }.getOrDefault(emptyList())
    }

    val events = gee.selectedEvents
    val isFootball = (gee.selectedCategory ?: "Football").equals("Football", ignoreCase = true)
    val leagues = remember(events, isFootball) {
        if (!isFootball) emptyList() else GeeAgenda.leagueNames(events)
    }
    val filtered = remember(events, league, isFootball) {
        if (!isFootball) events else events.filter { GeeAgenda.matchesLeague(it, league) }
    }
    val sections = remember(filtered) { GeeAgenda.sections(filtered) }
    val days = remember { (0..7).map { SportsSource.shift(SportsSource.todayIso(), it) } }

    LaunchedEffect(Unit) {
        while (true) {
            delay(120_000)
            gee.refresh()
        }
    }
    LaunchedEffect(gee.selectedCategory, league, channels) {
        runCatching { listState.scrollToItem(0) }
        FocusRescue.screenHasFocus = false
        delay(140)
        runCatching { geeEntryFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(VisionBlack)
                .padding(horizontal = 48.dp, vertical = 27.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(TAB_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                Text("Deportes en vivo", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                TvRoundIcon(Icons.Default.Refresh, "Actualizar", { gee.refresh(force = true) })
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(gee.categories, key = { it }) { category ->
                    TvChoiceChip(
                        label = geeCategoryLabel(category),
                        selected = !channels && gee.selectedCategory == category,
                        onClick = {
                            channels = false
                            gee.selectCategory(category)
                        },
                        modifier = Modifier.focusProperties { down = geeEntryFocus },
                    )
                }
                item(key = "canales") {
                    TvChoiceChip(
                        label = "Canales",
                        selected = channels,
                        onClick = { channels = true },
                        modifier = Modifier.focusProperties { down = geeEntryFocus },
                    )
                }
            }
            if (channels) {
                Spacer(Modifier.height(12.dp))
                TvGeeChannelsTab(
                    state = gee,
                    onPlay = onPlay,
                    entryRequester = geeEntryFocus,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusGroup().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(days, key = { it }) { date ->
                        TvChoiceChip(
                            label = SportsSource.dayLabel(date),
                            selected = gee.selectedDay == date,
                            onClick = { gee.pickDay(date) },
                            modifier = Modifier.focusProperties { down = geeEntryFocus },
                        )
                    }
                }
                if (isFootball && leagues.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().focusGroup().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        item(key = "liga_all") {
                            TvChoiceChip(
                                label = "Todas",
                                selected = league == null,
                                onClick = { league = null },
                                modifier = Modifier.focusProperties { down = geeEntryFocus },
                            )
                        }
                        items(leagues, key = { "liga_$it" }) { name ->
                            TvChoiceChip(
                                label = name,
                                selected = league == name,
                                onClick = { league = if (league == name) null else name },
                                modifier = Modifier.focusProperties { down = geeEntryFocus },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val live = filtered.count { it.live || GeeAgenda.phaseOf(it) == GeeAgenda.Phase.Live }
                Text(
                    when {
                        gee.loading && filtered.isEmpty() -> "Cargando eventos…"
                        filtered.isEmpty() -> "Sin eventos por ahora"
                        live > 0 -> "${filtered.size} eventos · $live en vivo"
                        else -> "${filtered.size} eventos"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Text("No hay eventos de este deporte en este momento.", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                } else {
                    val firstEventId = sections.firstOrNull()?.events?.firstOrNull()?.id
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        sections.forEach { section ->
                            item(key = "sec_${section.title}") {
                                TvGeeSectionHeader(section.title, section.events.size)
                            }
                            items(section.events, key = { it.id }) { event ->
                                TvGeeEventCard(
                                    event = event,
                                    modifier = if (event.id == firstEventId) Modifier.focusRequester(geeEntryFocus) else Modifier,
                                    onClick = {
                                        com.chronosbvrx.visionplus.ui.sports.DeportesReturn.pickingId = event.id
                                        pickingId = event.id
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        picking?.let { event ->
            TvGeeLinkPickDialog(
                title = geeEventTitle(event),
                subtitle = geeEventSubtitle(event),
                links = event.links,
                onDismiss = {
                    com.chronosbvrx.visionplus.ui.sports.DeportesReturn.pickingId = null
                    pickingId = null
                },
                onPlay = onPlay,
                guide = pickingGuide,
                geeTv = pickingTv,
            )
        }    }
}

@Composable
private fun TvGeeSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == "En vivo") {
            Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (title == "En vivo") Color.White else VisionTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    }
}

/** La tarjeta de un evento con escudos, marcador y estado (en vivo/final/hora). */
@Composable
private fun TvGeeEventCard(
    event: GeeSportsSource.GeeEvent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val home = event.homeName.ifBlank { event.teamA }
    val away = event.awayName.ifBlank { event.teamB }
    val hasTeams = home.isNotBlank() && away.isNotBlank() &&
        !home.equals(event.name, true) && !away.equals(event.name, true)
    val shape = RoundedCornerShape(12.dp)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = VisionSurfaceHigh,
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
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.leagueLogo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(event.leagueLogo)
                                .size(40, 40)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        GeeAgenda.leagueLabel(event),
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TvGeeStateBadge(event)
                }
                if (hasTeams) {
                    TvGeeTeamLine(event.homeLogo, home, event.scoreHome, event.live)
                    TvGeeTeamLine(event.awayLogo, away, event.scoreAway, event.live)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (event.logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(event.logo)
                                    .size(56, 56)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            event.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (event.broadcasts.isNotEmpty()) {
                    Text(
                        "TV: " + event.broadcasts.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (event.alive > 0) {
                    Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = LiveRed)) {
                        Text(
                            "${event.alive} en vivo",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Text("Sin señal", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun TvGeeTeamLine(logo: String, name: String, score: Int?, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logo)
                        .size(56, 56)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            score?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (live) VisionBlueBright else Color.White,
        )
    }
}

@Composable
private fun TvGeeStateBadge(event: GeeSportsSource.GeeEvent) {
    when {
        event.live -> Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = LiveRed)) {
            val detail = event.detail.trim()
            Text(
                if (detail.isBlank() || detail.equals("in", true)) "EN VIVO" else "EN VIVO · $detail",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        event.status == "post" -> Text("Final", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
        else -> {
            val today = SportsSource.todayIso()
            val whenText = when {
                event.dateIso == today -> "Hoy"
                event.dateIso.isBlank() -> ""
                else -> event.dateText.ifBlank { event.dateIso }
            }
            Text(
                listOf(whenText, event.time.take(5)).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}
