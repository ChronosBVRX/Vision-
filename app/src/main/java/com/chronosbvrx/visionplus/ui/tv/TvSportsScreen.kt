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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.sports.GeeTiles
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.sports.geeCategoryLabel
import com.chronosbvrx.visionplus.ui.sports.geeEventSubtitle
import com.chronosbvrx.visionplus.ui.sports.geeEventTitle
import com.chronosbvrx.visionplus.ui.sports.geeScoreLine
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
 * bloques EN VIVO → COMIENZAN PRONTO → TERMINADOS, ahora como MOSAICO de
 * partidos (escudos y marcador) con el área de filtros COMPACTA: encabezado con
 * el conteo en línea y días + ligas en una sola fila. OK sobre una tarjeta abre
 * el selector de transmisiones; todo con el D-pad.
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
    // Destino de aterrizaje SIEMPRE compuesto (el primer chip del deporte, o «Canales»):
    // con la agenda vacía o en camino no hay mosaico que enfocar y la pantalla se quedaba
    // sin destino (el mando parecía muerto hasta Atrás).
    val landing = rememberLandingFocus("tv_sports")
    val gridState = rememberLazyGridState()
    val context = LocalContext.current
    // El contexto del partido que viaja al reproductor (su aviso de cable usa la misma interfaz
    // que la TV en vivo: liga, marcador y dónde se transmite). La lista de fuentes es
    // exclusivamente de GeeSports.
    val pickingInfo = remember(picking) {
        val event = picking
        if (event == null) {
            null
        } else {
            GeeZapSignals.EventInfo(
                title = geeEventTitle(event),
                league = GeeAgenda.leagueLabel(event),
                scoreLine = geeScoreLine(event),
                guide = event.broadcasts,
                signals = event.links.size,
            )
        }
    }

    val events = gee.selectedEvents
    val isFootball = (gee.selectedCategory ?: "Football").equals("Football", ignoreCase = true)
    val leagues = remember(events, isFootball) {
        if (!isFootball) emptyList() else GeeAgenda.leagueNames(events)
    }
    val filtered = remember(events, league, isFootball) {
        if (!isFootball) events else events.filter { GeeAgenda.matchesLeague(it, league) }
    }
    // El reloj entra en la llave: al pasar el tiempo los bloques se rearman solos (el
    // candado de «aún no comienza» se abre al llegar la hora).
    val now = gee.clockMs
    val sections = remember(filtered, now) { GeeAgenda.sections(filtered, now) }
    val days = remember { (0..7).map { SportsSource.shift(SportsSource.todayIso(), it) } }
    val liveCount = remember(filtered, now) {
        filtered.count { it.live || GeeAgenda.phaseOf(it, now) == GeeAgenda.Phase.Live }
    }
    val countText = when {
        gee.loading && filtered.isEmpty() -> "Cargando eventos…"
        filtered.isEmpty() -> "Sin eventos por ahora"
        liveCount > 0 -> "${filtered.size} eventos · $liveCount en vivo"
        else -> "${filtered.size} eventos"
    }

    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(30_000)
            // El reloj de la agenda avanza cada 30 s (el candado se abre al llegar la hora)
            // y los datos se refrescan cada 2 minutos, como siempre.
            gee.tick()
            ticks++
            if (ticks % 4 == 0) gee.refresh()
        }
    }
    // El vigilante de vida: mientras la pantalla está abierta, comprueba sin parar las señales
    // de los partidos EN VIVO (una caída se ve al momento y un levantamiento también).
    LaunchedEffect(Unit) { gee.watchSignals() }
    // Aterrizaje: la primera tarjeta del mosaico (o el primer canal en «Canales»); si aún no
    // existe —agenda vacía o en camino—, el foco cae en un chip compuesto. La llave incluye
    // «hay eventos»: cuando la agenda llega tarde, el intento se re-arma solo y el mosaico
    // queda enfocado sin que el usuario toque nada.
    LaunchedEffect(gee.selectedCategory, league, channels, filtered.isEmpty()) {
        runCatching { gridState.scrollToItem(0) }
        delay(140)
        if (!requestFocusSafely(geeEntryFocus)) landing.request()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(VisionBlack)
                .padding(horizontal = 48.dp, vertical = 18.dp),
        ) {
            // Encabezado compacto: el conteo vive aquí (antes era una fila propia).
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Deportes en vivo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TvRoundIcon(Icons.Default.Refresh, "Actualizar", { gee.refresh(force = true) })
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                itemsIndexed(gee.categories, key = { _, it -> it }) { index, category ->
                    TvChoiceChip(
                        label = geeCategoryLabel(category),
                        selected = !channels && gee.selectedCategory == category,
                        compact = true,
                        onClick = {
                            channels = false
                            gee.selectCategory(category)
                        },
                        // El primer chip del deporte es el destino de respaldo (siempre compuesto).
                        modifier = (if (index == 0) Modifier.landingFocus(landing) else Modifier)
                            .softFocusDown(geeEntryFocus),
                    )
                }
                item(key = "canales") {
                    TvChoiceChip(
                        label = "Canales",
                        selected = channels,
                        compact = true,
                        onClick = { channels = true },
                        // Sin deportes listados, «Canales» (siempre pintado) sostiene el rescate.
                        modifier = (if (gee.categories.isEmpty()) Modifier.landingFocus(landing) else Modifier)
                            .softFocusDown(geeEntryFocus),
                    )
                }
            }
            if (channels) {
                Spacer(Modifier.height(10.dp))
                TvGeeChannelsTab(
                    state = gee,
                    onPlay = onPlay,
                    entryRequester = geeEntryFocus,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                // Filtros en UNA sola fila: días y luego las ligas (con separador).
                LazyRow(
                    modifier = Modifier.fillMaxWidth().focusGroup().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(days, key = { it }) { date ->
                        TvChoiceChip(
                            label = SportsSource.dayLabel(date),
                            selected = gee.selectedDay == date,
                            compact = true,
                            onClick = { gee.pickDay(date) },
                            modifier = Modifier.softFocusDown(geeEntryFocus),
                        )
                    }
                    if (isFootball && leagues.isNotEmpty()) {
                        item(key = "filtro_divisor") {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(Color.White.copy(alpha = 0.16f)),
                            )
                        }
                        item(key = "liga_all") {
                            TvChoiceChip(
                                label = "Todas",
                                selected = league == null,
                                compact = true,
                                onClick = { league = null },
                                modifier = Modifier.softFocusDown(geeEntryFocus),
                            )
                        }
                        items(leagues, key = { "liga_$it" }) { name ->
                            TvChoiceChip(
                                label = name,
                                selected = league == name,
                                compact = true,
                                onClick = { league = if (league == name) null else name },
                                modifier = Modifier.softFocusDown(geeEntryFocus),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Text(
                        "No hay eventos de este deporte en este momento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                    )
                } else {
                    val firstEventId = sections.firstOrNull()?.events?.firstOrNull()?.id
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        sections.forEach { section ->
                            item(key = "sec_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                                TvGeeSectionHeader(section.title, section.events.size)
                            }
                            gridItems(section.events, key = { it.id }) { event ->
                                TvGeeEventTile(
                                    event = event,
                                    nowMs = now,
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
                playable = GeeAgenda.playable(event, now),
                blockNote = GeeAgenda.blockNote(event, now),
                checks = gee.checks,
                eventInfo = pickingInfo,
            )
        }
    }
}

@Composable
private fun TvGeeSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == "En vivo") {
            Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (title == "En vivo") Color.White else VisionTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
    }
}

/**
 * La tarjeta-mosaico de un partido: escudos grandes, marcador real, estado y el
 * pie con la guía de TV / señal. Misma lógica pura que la del móvil ([GeeTiles]).
 */
@Composable
private fun TvGeeEventTile(
    event: GeeSportsSource.GeeEvent,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (home, away) = GeeTiles.teams(event)
    val hasTeams = home.isNotBlank()
    val shape = RoundedCornerShape(14.dp)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(150.dp),
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
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                TvGeeStateBadge(event, nowMs = nowMs)
            }
            Spacer(Modifier.height(8.dp))
            if (hasTeams) {
                TvGeeTileTeamLine(event.homeLogo, home, event.scoreHome, event.live)
                Spacer(Modifier.height(6.dp))
                TvGeeTileTeamLine(event.awayLogo, away, event.scoreAway, event.live)
                Spacer(Modifier.weight(1f))
            } else {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (event.logo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(event.logo)
                                .size(56, 56)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        event.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TvGeeTileFooter(event, nowMs)
        }
    }
}

@Composable
private fun TvGeeTileTeamLine(logo: String, name: String, score: Int?, live: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logo)
                        .size(68, 68)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    GeeTiles.monogram(name),
                    style = MaterialTheme.typography.labelMedium,
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
        Spacer(Modifier.width(8.dp))
        Text(
            score?.toString() ?: "-",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (live) VisionBlueBright else Color.White,
        )
    }
}

/** El pie: la guía de TV («TV: …») o cuántas señales hay. La píldora roja es SOLO para partidos en vivo. */
@Composable
private fun TvGeeTileFooter(event: GeeSportsSource.GeeEvent, nowMs: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val tvLine = event.broadcasts.take(2).joinToString(" · ")
        Text(
            if (tvLine.isNotBlank()) "TV: $tvLine" else GeeTiles.streams(event),
            style = MaterialTheme.typography.labelSmall,
            color = if (tvLine.isBlank() && event.alive > 0) VisionBlueBright else VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // «en vivo» nunca en un partido que aún no comienza o ya terminó: `alive`
        // solo dice si el enlace responde, no que se esté jugando.
        if (GeeAgenda.playable(event, nowMs) && event.alive > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = LiveRed)) {
                Text(
                    "${event.alive} en vivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else if (tvLine.isNotBlank() && event.alive == 0) {
            Spacer(Modifier.width(8.dp))
            Text("Sin señal", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        }
    }
}

/** EN VIVO con minuto, Final, o la hora con el día (Hoy/mañana/fecha). */
@Composable
private fun TvGeeStateBadge(event: GeeSportsSource.GeeEvent, nowMs: Long = GeeAgenda.currentNow()) {
    when {
        event.live || GeeAgenda.phaseOf(event, nowMs) == GeeAgenda.Phase.Live -> Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = LiveRed)) {
            Text(
                GeeTiles.liveLabel(event),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        event.status == "post" -> Text("Final", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
        else -> Text(
            GeeTiles.state(event, SportsSource.todayIso()),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
