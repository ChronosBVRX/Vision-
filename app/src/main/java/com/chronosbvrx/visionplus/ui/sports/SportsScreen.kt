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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

private val LiveRed = Color(0xFFD32F2F)

/**
 * "Deportes en vivo": la fuente única (GeeSports) con los deportes agrupados —
 * fútbol al frente y, dentro de él, las ligas grandes con Liga MX primero.
 * Los bloques son EN VIVO → COMIENZAN PRONTO → TERMINADOS, con escudos y
 * marcadores reales (cruzados en el servidor con la agenda en vivo).
 */
@Composable
fun SportsScreen(
    contentPadding: PaddingValues,
    onPlay: (LiveChannel) -> Unit,
) {
    val gee = rememberGeeSportsState()
    var picking by remember { mutableStateOf<GeeSportsSource.GeeEvent?>(null) }
    var league by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf(false) }

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

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Deportes en vivo",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { gee.refresh(force = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        ) {
            items(gee.categories, key = { it }) { category ->
                FilterChip(
                    selected = !channels && gee.selectedCategory == category,
                    onClick = {
                        channels = false
                        gee.selectCategory(category)
                    },
                    label = { Text(geeCategoryLabel(category)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VisionBlue,
                        selectedLabelColor = Color.White,
                        containerColor = VisionSurface,
                        labelColor = VisionTextSecondary,
                    ),
                    border = null,
                )
            }
            item(key = "canales") {
                FilterChip(
                    selected = channels,
                    onClick = { channels = true },
                    label = { Text("Canales") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VisionBlue,
                        selectedLabelColor = Color.White,
                        containerColor = VisionSurface,
                        labelColor = VisionTextSecondary,
                    ),
                    border = null,
                )
            }
        }
        if (channels) {
            GeeChannelsTab(
                state = gee,
                onPlay = onPlay,
                bottomPadding = contentPadding.calculateBottomPadding(),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                items(days, key = { it }) { date ->
                    FilterChip(
                        selected = gee.selectedDay == date,
                        onClick = { gee.pickDay(date) },
                        label = { Text(SportsSource.dayLabel(date)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VisionBlue,
                            selectedLabelColor = Color.White,
                            containerColor = VisionSurface,
                            labelColor = VisionTextSecondary,
                        ),
                        border = null,
                    )
                }
            }
            if (isFootball && leagues.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    item(key = "liga_all") {
                        FilterChip(
                            selected = league == null,
                            onClick = { league = null },
                            label = { Text("Todas") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VisionBlue,
                                selectedLabelColor = Color.White,
                                containerColor = VisionSurface,
                                labelColor = VisionTextSecondary,
                            ),
                            border = null,
                        )
                    }
                    items(leagues, key = { "liga_$it" }) { name ->
                        FilterChip(
                            selected = league == name,
                            onClick = { league = if (league == name) null else name },
                            label = { Text(name, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VisionBlue,
                                selectedLabelColor = Color.White,
                                containerColor = VisionSurface,
                                labelColor = VisionTextSecondary,
                            ),
                            border = null,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val live = filtered.count { it.live || GeeAgenda.phaseOf(it) == GeeAgenda.Phase.Live }
                val text = when {
                    gee.loading && filtered.isEmpty() -> "Cargando eventos…"
                    filtered.isEmpty() -> "Sin eventos por ahora"
                    live > 0 -> "${filtered.size} eventos · $live en vivo"
                    else -> "${filtered.size} eventos"
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
            when {
                gee.loading && filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando…", color = VisionTextSecondary)
                }
                filtered.isEmpty() -> EmptyState("Sin eventos", subtitle = "No hay eventos de este deporte en este momento.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 2.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    sections.forEach { section ->
                        item(key = "sec_${section.title}") {
                            GeeSectionHeader(section.title, section.events.size)
                        }
                        items(section.events, key = { it.id }) { event ->
                            GeeEventCard(event) { picking = event }
                        }
                    }
                }
            }
        }
    }

    picking?.let { event ->
        GeeLinkPickDialog(
            title = geeEventTitle(event),
            subtitle = geeEventSubtitle(event),
            links = event.links,
            onDismiss = { picking = null },
            onPlay = onPlay,
        )
    }
}

/** "América vs Guadalajara"; los eventos sin equipos usan el nombre del cuadro. */
internal fun geeEventTitle(event: GeeSportsSource.GeeEvent): String {
    val home = event.homeName.ifBlank { event.teamA }
    val away = event.awayName.ifBlank { event.teamB }
    if (home.isBlank() || away.isBlank() || home.equals(event.name, true) || away.equals(event.name, true)) {
        return event.name
    }
    return "$home vs $away"
}

/** "Liga MX · Hoy 18:30" para el encabezado del selector de transmisiones. */
internal fun geeEventSubtitle(event: GeeSportsSource.GeeEvent): String {
    val today = SportsSource.todayIso()
    val whenText = if (event.dateIso == today) "Hoy" else event.dateText.ifBlank { event.dateIso }
    return listOf(GeeAgenda.leagueLabel(event), "$whenText ${geeTimeLabel(event.time)}".trim())
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

@Composable
private fun GeeSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
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

/** La tarjeta de un evento: escudos, marcador real y el estado (en vivo/final/hora). */
@Composable
private fun GeeEventCard(event: GeeSportsSource.GeeEvent, onClick: () -> Unit) {
    val home = event.homeName.ifBlank { event.teamA }
    val away = event.awayName.ifBlank { event.teamB }
    val hasTeams = home.isNotBlank() && away.isNotBlank() &&
        !home.equals(event.name, true) && !away.equals(event.name, true)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = VisionSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                GeeStateBadge(event)
            }
            Spacer(Modifier.height(8.dp))
            if (hasTeams) {
                GeeTeamLine(event.homeLogo, home, event.scoreHome, event.live)
                Spacer(Modifier.height(6.dp))
                GeeTeamLine(event.awayLogo, away, event.scoreAway, event.live)
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.alive > 0) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (event.alive == 1) "1 transmisión en vivo" else "${event.alive} transmisiones en vivo",
                        style = MaterialTheme.typography.labelLarge,
                        color = VisionBlueBright,
                    )
                } else {
                    Text("Sin señal por ahora", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun GeeTeamLine(logo: String, name: String, score: Int?, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(VisionSurfaceHigh),
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
            } else {
                Text(
                    name.trim().split(' ').filter { it.length > 2 }.take(2)
                        .map { it.first().uppercaseChar() }.joinToString("").ifBlank { name.take(2).uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
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

/** EN VIVO con minuto, Final, o la hora con el día (Hoy/mañana/fecha). */
@Composable
private fun GeeStateBadge(event: GeeSportsSource.GeeEvent) {
    when {
        event.live -> Surface(color = LiveRed, shape = RoundedCornerShape(50)) {
            val detail = event.detail.trim()
            Text(
                if (detail.isBlank() || detail.equals("in", true)) "EN VIVO" else "EN VIVO · $detail",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                listOf(whenText, geeTimeLabel(event.time)).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}
