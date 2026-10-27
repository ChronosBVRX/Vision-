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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.delay

private val LiveRed = Color(0xFFD32F2F)

/**
 * "Deportes en vivo": la fuente única (GeeSports) con los deportes agrupados —
 * fútbol al frente y, dentro de él, las ligas grandes con Liga MX primero.
 * Los partidos se muestran como MOSAICO (parrilla de tarjetas con escudos y
 * marcador): bloques EN VIVO → COMIENZAN PRONTO → TERMINADOS.
 */
@Composable
fun SportsScreen(
    contentPadding: PaddingValues,
    onPlay: (LiveChannel) -> Unit,
) {
    val gee = rememberGeeSportsState()
    // Al volver del reproductor, el partido que se estaba viendo manda: su menú reaparece
    // con la opción sintonizada marcada. Si no venimos de ahí, se respeta el partido que el
    // launcher haya pedido por enlace (`DeportesReturn.pickingId`).
    val returnedFromPlayer = remember { DeportesReturn.consumeWatchingEvent() }
    var pickingId by remember { mutableStateOf(returnedFromPlayer ?: DeportesReturn.pickingId) }
    var league by remember { mutableStateOf(DeportesReturn.league) }
    var channels by remember { mutableStateOf(DeportesReturn.channels) }
    val picking = remember(pickingId, gee.events) {
        pickingId?.let { id -> gee.events.firstOrNull { it.id == id } }
    }
    // Un partido pedido desde el héroe/launcher se abre en SU deporte y SU día; al volver
    // del reproductor no se toca la selección (se regresa a la que la persona dejó).
    LaunchedEffect(pickingId, gee.events) {
        if (returnedFromPlayer != null) return@LaunchedEffect
        val event = picking ?: return@LaunchedEffect
        if (gee.selectedDay != event.dateIso ||
            !event.category.equals(gee.selectedCategory.orEmpty(), ignoreCase = true)
        ) {
            gee.showEvent(event)
        }
    }
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
    // El reloj de la agenda: cada 30 s las fases se recalculan solas (el candado de
    // «aún no comienza» se abre al llegar la hora, sin tocar nada).
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            gee.tick()
        }
    }
    // Los enlaces de los partidos también se actualizan solos cada 5 min mientras
    // Deportes está al frente: el servidor tiene un pase caliente y la app lo recoge.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(GEE_LINKS_REFRESH_MS)
                gee.refreshLinks(force = true)
            }
        }
    }
    // El vigilante de vida: mientras la pantalla está abierta, comprueba sin parar las señales
    // de los partidos EN VIVO (una caída se ve al momento y un levantamiento también).
    LaunchedEffect(Unit) { gee.watchSignals() }
    // El reloj entra en la llave: al pasar el tiempo los bloques se rearman solos.
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
            Column(Modifier.weight(1f)) {
                Text(
                    "Deportes en vivo",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(countText, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
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
                        DeportesReturn.channels = false
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
                    onClick = {
                        channels = true
                        DeportesReturn.channels = true
                    },
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
                            onClick = {
                                league = null
                                DeportesReturn.league = null
                            },
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
                            onClick = {
                                league = if (league == name) null else name
                                DeportesReturn.league = league
                            },
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
            when {
                gee.loading && filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando…", color = VisionTextSecondary)
                }
                filtered.isEmpty() -> EmptyState("Sin eventos", subtitle = "No hay eventos de este deporte en este momento.")
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = contentPadding.calculateBottomPadding() + 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        sections.forEach { section ->
                            item(key = "sec_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                                GeeSectionHeader(section.title, section.events.size)
                            }
                            gridItems(section.events, key = { it.id }) { event ->
                                GeeEventTile(event, now) {
                                    // Abrir el menú de otro partido ya no es «volver a lo que veías».
                                    DeportesReturn.forgetWatching()
                                    DeportesReturn.pickingId = event.id
                                    pickingId = event.id
                                }
                            }
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
            onDismiss = {
                DeportesReturn.pickingId = null
                DeportesReturn.forgetWatching()
                pickingId = null
            },
            onPlay = { picked ->
                // Al volver del reproductor, este partido reaparece con ESA opción marcada.
                DeportesReturn.rememberWatching(event.id, picked.url)
                onPlay(picked)
            },
            playable = GeeAgenda.playable(event, now),
            blockNote = GeeAgenda.blockNote(event, now),
            checks = gee.checks,
            eventInfo = pickingInfo,
            playingUrl = DeportesReturn.watchingUrl,
        )
    }
}

/** "América vs Guadalajara"; los eventos sin equipos usan el nombre del cuadro. */
internal fun geeEventTitle(event: GeeSportsSource.GeeEvent): String {
    val (home, away) = GeeTiles.teams(event)
    if (home.isBlank() || away.isBlank()) return event.name
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

/**
 * «1 - 0 · 37'» con lo que la agenda traiga (marcador y minuto). Vacío cuando el partido aún no
 * tiene nada que contar: el aviso del reproductor simplemente omite la línea.
 */
internal fun geeScoreLine(event: GeeSportsSource.GeeEvent): String {
    val home = event.scoreHome
    val away = event.scoreAway
    val score = if (home != null && away != null) "$home - $away" else ""
    return listOf(score, event.detail.trim()).filter { it.isNotBlank() }.joinToString(" · ")
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

/**
 * La tarjeta-mosaico de un partido: escudos grandes, marcador real y estado.
 * Mismo ADN visual que la de Smart TV (misma lógica pura en [GeeTiles]).
 */
@Composable
private fun GeeEventTile(event: GeeSportsSource.GeeEvent, nowMs: Long, onClick: () -> Unit) {
    val (home, away) = GeeTiles.teams(event)
    val hasTeams = home.isNotBlank()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = VisionSurface,
        modifier = Modifier.fillMaxWidth().height(126.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.leagueLogo.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(event.leagueLogo)
                            .size(32, 32)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    GeeAgenda.leagueLabel(event),
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GeeStateBadge(event, compact = true, nowMs = nowMs)
            }
            Spacer(Modifier.height(8.dp))
            if (hasTeams) {
                GeeTileTeamLine(event.homeLogo, home, event.scoreHome, event.live)
                Spacer(Modifier.height(6.dp))
                GeeTileTeamLine(event.awayLogo, away, event.scoreAway, event.live)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (event.logo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(event.logo)
                                .size(56, 56)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        event.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            GeeTileFooter(event, nowMs)
        }
    }
}

@Composable
private fun GeeTileTeamLine(logo: String, name: String, score: Int?, live: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logo)
                        .size(44, 44)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    GeeTiles.monogram(name),
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            score?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (live) VisionBlueBright else Color.White,
        )
    }
}

/** El pie: la guía de TV («TV: …») o cuántas señales hay. La píldora roja es SOLO para partidos en vivo. */
@Composable
private fun GeeTileFooter(event: GeeSportsSource.GeeEvent, nowMs: Long) {
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
            Spacer(Modifier.width(6.dp))
            Surface(color = LiveRed, shape = RoundedCornerShape(50)) {
                Text(
                    "${event.alive} en vivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** EN VIVO con minuto, Final, o la hora con el día (Hoy/mañana/fecha). */
@Composable
private fun GeeStateBadge(event: GeeSportsSource.GeeEvent, compact: Boolean = false, nowMs: Long = GeeAgenda.currentNow()) {
    when {
        event.live || GeeAgenda.phaseOf(event, nowMs) == GeeAgenda.Phase.Live -> Surface(color = LiveRed, shape = RoundedCornerShape(50)) {
            Text(
                GeeTiles.liveLabel(event),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(
                    horizontal = if (compact) 7.dp else 10.dp,
                    vertical = if (compact) 2.dp else 4.dp,
                ),
            )
        }
        event.status == "post" -> Text(
            "Final",
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = VisionTextSecondary,
        )
        else -> Text(
            GeeTiles.state(event, SportsSource.todayIso()),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
    }
}
