package com.chronosbvrx.visionplus.ui.sports

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.sports.SportLeague
import com.chronosbvrx.visionplus.data.sports.SportMatch
import com.chronosbvrx.visionplus.data.sports.SportTeam
import com.chronosbvrx.visionplus.data.sports.SportsLinker
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.CountryIsoChip
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

private val LiveRed = Color(0xFFD32F2F)

/**
 * La agenda de fútbol en el móvil: partidos del día organizados por liga, con
 * logos, marcador en vivo y los canales de Vision+ que transmiten cada encuentro.
 */
@Composable
fun SportsScreen(
    contentPadding: PaddingValues,
    onPlay: (LiveChannel) -> Unit,
) {
    val state = rememberSportsState()
    var selectedMatch by remember { mutableStateOf<SportMatch?>(null) }
    val days = remember { (-1..SportsSource.DAYS_AHEAD).map { SportsSource.shift(SportsSource.todayIso(), it) } }
    val byLeague = remember(state.matches, state.selectedLeagueId) { state.byLeague }

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
                "Agenda de Fútbol",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { state.refresh(force = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(days, key = { it }) { date ->
                val label = SportsSource.dayLabel(date)
                FilterChip(
                    selected = date == state.dateIso,
                    onClick = { state.pick(date) },
                    label = { Text(label) },
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
        if (state.matches.isNotEmpty() && state.availableLeagues.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                item(key = "league_all") {
                    FilterChip(
                        selected = state.selectedLeagueId == null,
                        onClick = { state.selectLeague(null) },
                        label = { Text("Todas (${state.matches.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VisionBlue,
                            selectedLabelColor = Color.White,
                            containerColor = VisionSurface,
                            labelColor = VisionTextSecondary,
                        ),
                        border = null,
                    )
                }
                items(state.availableLeagues, key = { it.id.ifBlank { it.name } }) { league ->
                    val isSelected = state.selectedLeagueId == league.id
                    val matchCount = state.leagueMatchCount(league.id)
                    FilterChip(
                        selected = isSelected,
                        onClick = { state.selectLeague(league.id) },
                        leadingIcon = if (league.logo.isNotBlank()) {
                            {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(league.logo)
                                        .size(36, 36)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        } else null,
                        label = { Text("${league.name} ($matchCount)") },
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
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val countText = when {
                state.loading && state.matches.isEmpty() -> "Cargando partidos…"
                state.matches.isEmpty() -> "Sin partidos para este día"
                state.selectedLeagueId != null -> "${byLeague.firstOrNull()?.second?.size ?: 0} partidos en esta liga"
                state.liveCount > 0 -> "${state.matches.size} partidos · ${state.liveCount} en vivo"
                else -> "${state.matches.size} partidos programados"
            }
            Text(countText, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
        }
        when {
            state.loading && state.matches.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VisionBlue)
            }
            state.error != null && state.matches.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No se pudo cargar la agenda", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 6.dp))
                Button(onClick = { state.refresh(force = true) }, modifier = Modifier.padding(top = 16.dp)) { Text("Reintentar") }
            }
            state.matches.isEmpty() -> EmptyState("Sin partidos", subtitle = "No hay encuentros de fútbol para este día.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                byLeague.forEachIndexed { leagueIndex, (leagueId, leagueMatches) ->
                    val firstMatch = leagueMatches.firstOrNull()
                    if (firstMatch != null) {
                        val headerKey = "league_${leagueId.ifBlank { "idx_$leagueIndex" }}_$leagueIndex"
                        item(key = headerKey) { LeagueHeader(firstMatch.league, leagueMatches.size) }
                        items(leagueMatches.distinctBy { it.id }, key = { "m_${it.id}" }) { match ->
                            MatchCard(
                                match = match,
                                link = state.links[match.id],
                                onSelectMatch = { selectedMatch = it },
                                onPlay = onPlay,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedMatch?.let { match ->
        val context = LocalContext.current
        MobileMatchChannelsDialog(
            match = match,
            link = state.links[match.id] ?: SportsLinker.quickResolve(context, match),
            onDismiss = { selectedMatch = null },
            onPlay = onPlay,
        )
    }
}

@Composable
private fun LeagueHeader(league: SportLeague, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (league.logo.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(league.logo)
                    .size(48, 48)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(league.name, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    }
}

@Composable
private fun MatchCard(
    match: SportMatch,
    link: SportsLinker.Link?,
    onSelectMatch: (SportMatch) -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    Surface(
        onClick = { onSelectMatch(match) },
        shape = RoundedCornerShape(14.dp),
        color = VisionSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    match.venue.ifBlank { match.league.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (match.status == "pre" && match.kickoffMs > System.currentTimeMillis()) {
                    com.chronosbvrx.visionplus.ui.notify.MobileRemindBell(
                        matchId = match.id,
                        kickoffMs = match.kickoffMs,
                        title = "${match.home.name} vs ${match.away.name}",
                    )
                }
                StatusBadge(match)
            }
            Spacer(Modifier.height(8.dp))
            TeamLine(match.home, match)
            Spacer(Modifier.height(6.dp))
            TeamLine(match.away, match)
            Spacer(Modifier.height(10.dp))
            ChannelRow(match, link, onPlay)
        }
    }
}

@Composable
private fun TeamLine(team: SportTeam, match: SportMatch) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(teamInitials(team.name), style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            if (team.logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(team.logo)
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
            team.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val score = team.score
        if (score != null) {
            Text(
                "$score",
                style = MaterialTheme.typography.titleMedium,
                color = if (match.playing) VisionBlueBright else Color.White,
            )
        }
    }
}

@Composable
private fun StatusBadge(match: SportMatch) {
    when {
        match.playing -> Surface(color = LiveRed, shape = RoundedCornerShape(50)) {
            Text(
                listOf("EN VIVO", match.detail).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        match.finished -> Text("Final", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
        else -> Text(
            SportsSource.timeLabel(match.kickoffMs),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun ChannelRow(match: SportMatch, link: SportsLinker.Link?, onPlay: (LiveChannel) -> Unit) {
    val confirmed = link?.confirmed
    val others = link?.candidates.orEmpty().filter { it.url != confirmed?.url }
    when {
        confirmed != null -> {
            Text("Se está transmitiendo en", style = MaterialTheme.typography.labelLarge, color = VisionBlueBright)
            Spacer(Modifier.height(6.dp))
            ChannelChip(confirmed, highlighted = true, onPlay = onPlay)
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(others, key = { it.url }) { ChannelChip(it, highlighted = false, onPlay = onPlay) }
                }
            }
        }
        others.isNotEmpty() -> {
            Text("Canales posibles", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(others, key = { it.url }) { ChannelChip(it, highlighted = false, onPlay = onPlay) }
            }
        }
        match.channels.isNotEmpty() -> Text(
            "Sin canal en tu lista · por " + match.channels.take(3).joinToString(" / "),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        else -> Text("Sin datos de transmisión", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    }
}

@Composable
private fun ChannelChip(channel: LiveChannel, highlighted: Boolean, onPlay: (LiveChannel) -> Unit) {
    Surface(
        onClick = { onPlay(channel) },
        shape = RoundedCornerShape(50),
        color = if (highlighted) VisionBlue else VisionSurfaceHigh,
        contentColor = Color.White,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (highlighted) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(7.dp))
            }
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logo)
                        .size(48, 48)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

private fun teamInitials(name: String): String =
    name.trim().split(' ')
        .filter { it.isNotBlank() && it.length > 2 }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { name.take(2).uppercase() }

@Composable
private fun MobileMatchChannelsDialog(
    match: SportMatch,
    link: SportsLinker.Link?,
    onDismiss: () -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    val context = LocalContext.current
    val effectiveLink = link ?: SportsLinker.quickResolve(context, match)
    val candidates = effectiveLink.candidates
    val confirmed = effectiveLink.confirmed
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = VisionSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                // Liga
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.league.logo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(match.league.logo)
                                .size(48, 48)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(match.league.name, style = MaterialTheme.typography.titleSmall, color = VisionTextSecondary, modifier = Modifier.weight(1f))
                    StatusBadge(match)
                }

                Spacer(Modifier.height(14.dp))

                // Equipos
                TeamLine(match.home, match)
                Spacer(Modifier.height(6.dp))
                TeamLine(match.away, match)

                Spacer(Modifier.height(16.dp))

                Text("Opciones de transmisión", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    if (candidates.isNotEmpty()) "Selecciona el canal para sintonizar:" else "Disponibilidad en catálogo:",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                if (candidates.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    ) {
                        items(candidates, key = { it.url }) { channel ->
                            val isConf = confirmed?.url == channel.url
                            val country = LiveTaxonomy.countryFor(channel.name)
                            val code = LiveTaxonomy.countryCode(country)
                            val programSubtitle = effectiveLink.epgPrograms[channel.url]
                            Surface(
                                onClick = { onPlay(channel); onDismiss() },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isConf) VisionBlue.copy(alpha = 0.25f) else VisionSurfaceHigh,
                                border = if (isConf) BorderStroke(1.dp, VisionBlueBright) else null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!channel.logo.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(channel.logo)
                                                .size(56, 56)
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                channel.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            CountryIsoChip(code)
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                country,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.85f),
                                                maxLines = 1,
                                            )
                                        }
                                        if (!programSubtitle.isNullOrBlank()) {
                                            Text(
                                                programSubtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isConf) VisionBlueBright else VisionTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        } else if (isConf) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(
                                                    VisionIcons.CheckCircle,
                                                    contentDescription = null,
                                                    tint = VisionBlueBright,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Text("Confirmado en vivo", style = MaterialTheme.typography.labelSmall, color = VisionBlueBright)
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text("Ver", style = MaterialTheme.typography.labelLarge, color = VisionBlueBright)
                                        Icon(
                                            VisionIcons.Play,
                                            contentDescription = null,
                                            tint = VisionBlueBright,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("No se encontró este canal en tu lista de canales de Vision+.", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                        if (match.channels.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Se transmite por: " + match.channels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = VisionBlueBright)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (match.status == "pre" && match.kickoffMs > System.currentTimeMillis()) {
                        com.chronosbvrx.visionplus.ui.notify.MobileRemindBell(
                            matchId = match.id,
                            kickoffMs = match.kickoffMs,
                            title = "${match.home.name} vs ${match.away.name}",
                        )
                    }
                    Button(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}
