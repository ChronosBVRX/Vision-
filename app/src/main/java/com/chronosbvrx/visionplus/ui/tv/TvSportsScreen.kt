package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.sports.SportMatch
import com.chronosbvrx.visionplus.data.sports.SportTeam
import com.chronosbvrx.visionplus.data.sports.SportsLinker
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.sports.rememberSportsState
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

private val LiveRed = Color(0xFFD32F2F)

/**
 * "Fútbol" en la TV: los partidos del día con el D-pad. OK sobre un partido abre
 * una ventana modal con los canales disponibles para sintonizar.
 */
@Composable
fun TvSportsScreen(onPlay: (LiveChannel) -> Unit) {
    val state = rememberSportsState()
    val landing = rememberLandingFocus(state.dateIso)
    var selectedMatchForPicker by remember { mutableStateOf<SportMatch?>(null) }
    var lastSelectedMatchId by remember { mutableStateOf<String?>(null) }
    val cardFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val daysRowFocusRequester = remember { FocusRequester() }
    val filterRowFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.dateIso) {
        while (true) {
            delay(120_000)
            state.refresh()
        }
    }
    LaunchedEffect(state.selectedLeagueId, state.dateIso) {
        listState.scrollToItem(0)
        lastSelectedMatchId = null
    }
    // El foco solo aterriza al cambiar fecha o al llegar los primeros partidos,
    // NUNCA mientras el usuario ya está navegando la lista con el D-pad (evita robos de foco).
    LaunchedEffect(state.dateIso, state.matches.isNotEmpty()) {
        delay(150)
        if (!FocusRescue.screenHasFocus && state.matches.isNotEmpty() && selectedMatchForPicker == null) landing.request()
    }
    // Al cerrar el modal de canales sin elegir, devolvemos el foco a la tarjeta seleccionada.
    LaunchedEffect(selectedMatchForPicker) {
        if (selectedMatchForPicker == null && lastSelectedMatchId != null) {
            FocusRescue.screenHasFocus = false
            delay(50)
            repeat(15) {
                var ok = runCatching { cardFocusRequester.requestFocus() }.isSuccess
                if (!ok) {
                    ok = runCatching { landing.request() }.isSuccess
                }
                if (!ok) {
                    runCatching { filterRowFocusRequester.requestFocus() }
                }
                delay(40)
            }
        }
    }
    val days = remember { (-1..SportsSource.DAYS_AHEAD).map { SportsSource.shift(SportsSource.todayIso(), it) } }
    val byLeague = remember(state.matches, state.selectedLeagueId) { state.byLeague }
    // El foco inicial cae en la PRIMERA tarjeta de la lista (aunque no tenga canal): así el
    // mando siempre tiene dónde estar y nunca se queda sin foco en esta pantalla.
    val firstCard = byLeague.firstOrNull()?.second?.firstOrNull()?.id

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(VisionBlack)
                .padding(horizontal = 48.dp, vertical = 27.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(TAB_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                Text("Fútbol", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(end = 16.dp))
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(days, key = { it }) { date ->
                        val isSelectedDay = date == state.dateIso
                        TvChoiceChip(
                            label = SportsSource.dayLabel(date),
                            selected = isSelectedDay,
                            onClick = { state.pick(date) },
                            modifier = if (isSelectedDay) Modifier.focusRequester(daysRowFocusRequester) else Modifier,
                        )
                    }
                }
                TvRoundIcon(Icons.Default.Refresh, "Actualizar", { state.refresh(force = true) })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    state.loading && state.matches.isEmpty() -> "Cargando partidos…"
                    state.matches.isEmpty() -> "Sin encuentros para este día"
                    state.selectedLeagueId != null -> "${byLeague.firstOrNull()?.second?.size ?: 0} partidos en esta liga"
                    state.liveCount > 0 -> "${state.matches.size} partidos · ${state.liveCount} en vivo"
                    else -> "${state.matches.size} partidos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            if (state.matches.isNotEmpty() && state.availableLeagues.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    item(key = "tv_league_all") {
                        val isAllSelected = state.selectedLeagueId == null
                        TvLeagueFilterChip(
                            label = "Todas (${state.matches.size})",
                            logoUrl = null,
                            selected = isAllSelected,
                            onClick = { state.selectLeague(null) },
                            modifier = (if (isAllSelected) Modifier.focusRequester(filterRowFocusRequester) else Modifier)
                                .focusProperties {
                                    down = firstCardFocusRequester
                                    up = daysRowFocusRequester
                                },
                        )
                    }
                    items(state.availableLeagues, key = { it.id.ifBlank { it.name } }) { league ->
                        val isSelected = state.selectedLeagueId == league.id
                        val count = state.leagueMatchCount(league.id)
                        TvLeagueFilterChip(
                            label = "${league.name} ($count)",
                            logoUrl = league.logo.takeIf { it.isNotBlank() },
                            selected = isSelected,
                            onClick = { state.selectLeague(league.id) },
                            modifier = (if (isSelected) Modifier.focusRequester(filterRowFocusRequester) else Modifier)
                                .focusProperties {
                                    down = firstCardFocusRequester
                                    up = daysRowFocusRequester
                                },
                        )
                    }
                }
            }
            if (state.matches.isEmpty()) {
                if (state.error != null) {
                    Column {
                        Text("No se pudo cargar la agenda", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                        Spacer(Modifier.height(12.dp))
                        TvCompactAction("Reintentar") { state.refresh(force = true) }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    byLeague.forEachIndexed { leagueIndex, (leagueId, leagueMatches) ->
                        val firstMatch = leagueMatches.firstOrNull()
                        if (firstMatch != null) {
                            val headerKey = "league_${leagueId.ifBlank { "idx_$leagueIndex" }}_$leagueIndex"
                            item(key = headerKey) {
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val logo = firstMatch.league.logo
                                    if (logo.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(logo)
                                                .size(48, 48)
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(
                                        firstMatch.league.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            items(leagueMatches.distinctBy { it.id }, key = { "m_${it.id}" }) { match ->
                                val isFirst = match.id == firstCard
                                val isSelected = match.id == lastSelectedMatchId
                                var cardModifier: Modifier = Modifier
                                if (isFirst) {
                                    cardModifier = cardModifier
                                        .focusRequester(firstCardFocusRequester)
                                        .focusProperties { up = filterRowFocusRequester }
                                        .landingFocus(landing)
                                }
                                if (isSelected) {
                                    cardModifier = cardModifier.focusRequester(cardFocusRequester)
                                }
                                val cardLink = state.links[match.id]
                                TvMatchCard(
                                    match = match,
                                    link = cardLink,
                                    onSelectMatch = {
                                        lastSelectedMatchId = it.id
                                        selectedMatchForPicker = it
                                    },
                                    modifier = cardModifier,
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedMatchForPicker?.let { match ->
            val context = LocalContext.current
            TvMatchChannelsModal(
                match = match,
                link = state.links[match.id] ?: SportsLinker.quickResolve(context, match),
                onDismiss = { selectedMatchForPicker = null },
                onPlay = onPlay,
            )
        }
    }
}

/** Un partido al aire o que arranca en los próximos 30 minutos. */
private fun liveWindow(match: SportMatch): Boolean {
    if (match.playing) return true
    if (match.status != "pre" || match.kickoffMs <= 0) return false
    val delta = match.kickoffMs - System.currentTimeMillis()
    return delta in -5 * 60_000..30 * 60_000
}

@Composable
private fun TvMatchCard(
    match: SportMatch,
    link: SportsLinker.Link?,
    onSelectMatch: (SportMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val confirmed = link?.confirmed
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(0.44f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TvTeamLine(match.home, match)
                TvTeamLine(match.away, match)
            }
            Box(Modifier.weight(0.18f), contentAlignment = Alignment.Center) { TvStatus(match) }
            Column(Modifier.weight(0.38f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvChannelInfo(match, link, confirmed)
            }
        }
    }
    Surface(
        onClick = { onSelectMatch(match) },
        modifier = modifier.fillMaxWidth().height(86.dp),
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
    ) { content() }
}

@Composable
private fun TvMatchChannelsModal(
    match: SportMatch,
    link: SportsLinker.Link?,
    onDismiss: () -> Unit,
    onPlay: (LiveChannel) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    val context = LocalContext.current
    val effectiveLink = link ?: SportsLinker.quickResolve(context, match)
    val candidates = effectiveLink.candidates
    val confirmed = effectiveLink.confirmed
    val modalLanding = rememberLandingFocus("tv_match_modal_${match.id}")
    LaunchedEffect(match.id) {
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
            colors = SurfaceDefaults.colors(
                containerColor = VisionSurface,
            ),
            border = Border(BorderStroke(1.dp, VisionBlue.copy(alpha = 0.4f)), shape = RoundedCornerShape(18.dp)),
            modifier = Modifier
                .width(560.dp)
                .modalFocusTrap(),
        ) {
            Column(Modifier.padding(24.dp)) {
                // Encabezado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.league.logo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(match.league.logo)
                                .size(48, 48)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        match.league.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TvStatus(match)
                }

                Spacer(Modifier.height(14.dp))

                // Equipos
                Row(
                    Modifier.fillMaxWidth().background(VisionSurfaceHigh, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (match.home.logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(match.home.logo)
                                    .size(56, 56)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(match.home.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val scoreText = if (match.home.score != null && match.away.score != null) "${match.home.score} - ${match.away.score}" else "VS"
                    Text(
                        scoreText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (match.playing) VisionBlueBright else Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        Text(match.away.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (match.away.logo.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(match.away.logo)
                                    .size(56, 56)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Opciones de transmisión",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    if (candidates.isNotEmpty()) "Elige el canal donde deseas ver el partido:" else "Disponibilidad en catálogo:",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                if (candidates.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    ) {
                        items(candidates, key = { it.url }) { channel ->
                            val isConf = confirmed?.url == channel.url
                            val isFirst = candidates.first().url == channel.url
                            val programSubtitle = link?.epgPrograms?.get(channel.url)
                            TvChannelPickItem(
                                channel = channel,
                                confirmed = isConf,
                                programSubtitle = programSubtitle,
                                onClick = { onPlay(channel); onDismiss() },
                                modifier = if (isFirst) Modifier.landingFocus(modalLanding) else Modifier,
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            "No se encontró este canal en tu lista de canales de Vision+.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VisionTextSecondary,
                        )
                        if (match.channels.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Transmisión oficial: " + match.channels.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionBlueBright,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        TvCompactAction("Volver", onClick = onDismiss, modifier = Modifier.landingFocus(modalLanding))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (match.status == "pre" && match.kickoffMs > System.currentTimeMillis()) {
                        com.chronosbvrx.visionplus.ui.notify.TvRemindAction(
                            matchId = match.id,
                            kickoffMs = match.kickoffMs,
                            title = "${match.home.name} vs ${match.away.name}",
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TvCompactAction("Cerrar", onClick = onDismiss)
                }
            }
        }
    }
}
}

@Composable
private fun TvChannelPickItem(
    channel: LiveChannel,
    confirmed: Boolean,
    programSubtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val country = LiveTaxonomy.countryFor(channel.name)
    val flag = LiveTaxonomy.countryFlag(country)

    Surface(
        onClick = onClick,
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
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logo)
                        .size(48, 48)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        colors = SurfaceDefaults.colors(containerColor = VisionSurface),
                    ) {
                        Text(
                            "$flag $country",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (!programSubtitle.isNullOrBlank()) {
                    Text(
                        programSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (confirmed) VisionBlueBright else VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (confirmed) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    colors = SurfaceDefaults.colors(containerColor = VisionBlueBright.copy(alpha = 0.25f)),
                ) {
                    Text(
                        "★ Confirmado EPG",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Sintonizar ▶",
                style = MaterialTheme.typography.labelMedium,
                color = if (confirmed) VisionBlueBright else VisionTextSecondary,
            )
        }
    }
}

@Composable
private fun TvTeamLine(team: SportTeam, match: SportMatch) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                teamInitials(team.name),
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
            )
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
            style = MaterialTheme.typography.bodyMedium,
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
private fun TvStatus(match: SportMatch) {
    when {
        match.playing -> Surface(
            shape = RoundedCornerShape(50),
            colors = SurfaceDefaults.colors(containerColor = LiveRed, contentColor = Color.White),
        ) {
            Text(
                listOf("EN VIVO", match.detail).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun TvChannelInfo(match: SportMatch, link: SportsLinker.Link?, confirmed: LiveChannel?) {
    val candidates = link?.candidates.orEmpty()
    when {
        confirmed != null -> {
            Text(
                if (match.playing) "EN VIVO EN" else "AL AIRE EN",
                style = MaterialTheme.typography.labelSmall,
                color = VisionBlueBright,
            )
            Text(
                confirmed.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("OK para ver", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        }
        candidates.isNotEmpty() -> {
            Text("POSIBLE · SIN CONFIRMAR", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            Text(
                candidates.take(2).joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (liveWindow(match)) "OK abre el canal" else "Aún sin confirmar",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
            )
        }
        match.channels.isNotEmpty() -> {
            Text("PROGRAMACIÓN", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            Text(
                match.channels.take(2).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("No está en tu lista", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        }
        else -> {
            Text("SIN CONFIRMAR", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            Text("Sin datos de emisión", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Consulta más tarde", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
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
private fun TvLeagueFilterChip(
    label: String,
    logoUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) VisionBlue.copy(alpha = 0.35f) else VisionSurface,
            focusedContainerColor = VisionSurfaceHigh,
            pressedContainerColor = VisionBlue.copy(alpha = 0.5f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, VisionBlueBright), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .size(36, 36)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else VisionTextSecondary,
                maxLines = 1,
            )
        }
    }
}
