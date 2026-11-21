package com.chronosbvrx.visionplus.ui.sports

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.home.contract.SportsSchedule
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsReminder
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.notify.rememberNotifyGate
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    var searchOpen by remember { mutableStateOf(false) }
    var reminderTick by remember { mutableStateOf(0) }
    val picking = remember(pickingId, gee.events) {
        pickingId?.let { id -> gee.events.firstOrNull { it.id == id } }
    }
    // Al abrir el selector se comprueban AHORA las señales de ESE partido: el
    // vigilante global tiene tope y un «Caído» viejo del servidor no debe tapar
    // una señal que sí responde.
    LaunchedEffect(pickingId, picking?.links?.size) {
        picking?.let { gee.refreshChecksFor(it) }
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
    val notifyGate = rememberNotifyGate()
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
                homeName = event.homeName.ifBlank { event.teamA },
                awayName = event.awayName.ifBlank { event.teamB },
                homeLogo = event.homeLogo,
                awayLogo = event.awayLogo,
                homeScore = event.scoreHome,
                awayScore = event.scoreAway,
                dateIso = event.dateIso,
                leagueId = event.leagueId,
                category = event.category,
            )
        }
    }

    val events = gee.selectedEvents
    // Ligas/series de CUALQUIER deporte: sirve para fútbol (Liga MX), básquet (NBA)
    // y motor (NASCAR, WRC, MotoGP…). El chip viejo se ignora si ya no existe.
    val leagues = remember(events) { GeeAgenda.leagueNames(events) }
    val leagueLogos = remember(events) { GeeAgenda.leagueLogos(events) }
    val activeLeague = remember(league, leagues) { league?.takeIf { it in leagues } }
    val filtered = remember(events, activeLeague) {
        events.filter { GeeAgenda.matchesLeague(it, activeLeague) }
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
                // Fresco al entrar (y al volver del reproductor), luego cada 5 min.
                gee.refreshLinks(force = true)
                delay(GEE_LINKS_REFRESH_MS)
            }
        }
    }
    // El vigilante de vida: mientras la pantalla está abierta, comprueba sin parar las señales
    // de los partidos EN VIVO (una caída se ve al momento y un levantamiento también).
    LaunchedEffect(Unit) { gee.watchSignals() }
    // El reloj entra en la llave: al pasar el tiempo los bloques se rearman solos.
    val now = gee.clockMs
    val sections = remember(filtered, now) { GeeAgenda.sections(filtered, now) }
    val days = remember { (0..1).map { SportsSource.shift(SportsSource.todayIso(), it) } }
    val liveCount = remember(filtered, now) {
        filtered.count { it.live || GeeAgenda.phaseOf(it, now) == GeeAgenda.Phase.Live }
    }
    val countText = when {
        gee.loading && filtered.isEmpty() -> "Cargando eventos…"
        filtered.isEmpty() -> "Sin eventos por ahora"
        liveCount > 0 -> "${filtered.size} eventos · $liveCount en vivo"
        else -> "${filtered.size} eventos"
    }
    val config = LocalConfiguration.current
    val layout = remember(
        config.screenHeightDp,
        config.smallestScreenWidthDp,
        config.orientation,
    ) {
        sportsLayout(
            heightDp = config.screenHeightDp,
            smallestWidthDp = config.smallestScreenWidthDp,
            isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layout.horizontalPadding.dp,
                    vertical = layout.headerVerticalPadding.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Deportes en vivo",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = layout.titleSize.sp,
                        lineHeight = (layout.titleSize * 1.25f).sp,
                    ),
                    color = Color.White,
                )
                Text(
                    countText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = layout.subtitleSize.sp,
                        lineHeight = (layout.subtitleSize * 1.35f).sp,
                    ),
                    color = VisionTextSecondary,
                )
            }
            IconButton(
                onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) {
                        gee.clearSearch()
                    } else {
                        channels = false
                        DeportesReturn.channels = false
                    }
                },
                modifier = Modifier.size((48f * layout.cardScale).coerceAtLeast(48f).dp),
            ) {
                Icon(
                    if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchOpen) "Cerrar búsqueda" else "Buscar partido",
                    tint = Color.White,
                    modifier = Modifier.size((24f * layout.cardScale).coerceAtLeast(24f).dp),
                )
            }
            IconButton(
                onClick = { gee.refresh(force = true) },
                modifier = Modifier.size((48f * layout.cardScale).coerceAtLeast(48f).dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = Color.White,
                    modifier = Modifier.size((24f * layout.cardScale).coerceAtLeast(24f).dp),
                )
            }
        }
        if (searchOpen) {
            OutlinedTextField(
                value = gee.searchQuery,
                onValueChange = gee::updateSearchQuery,
                placeholder = { Text("Buscar equipo, liga o canal") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VisionTextSecondary) },
                trailingIcon = {
                    if (gee.searchQuery.isNotBlank()) {
                        IconButton(onClick = { gee.clearSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = Color.White)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = VisionSurface,
                    unfocusedContainerColor = VisionSurface,
                    focusedBorderColor = VisionBlue,
                    unfocusedBorderColor = VisionSurfaceHigh,
                    cursorColor = VisionBlue,
                    focusedPlaceholderColor = VisionTextSecondary,
                    unfocusedPlaceholderColor = VisionTextSecondary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.horizontalPadding.dp, vertical = 2.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(layout.chipGap.dp),
            contentPadding = PaddingValues(horizontal = layout.horizontalPadding.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        ) {
            item(key = "scope") {
                GeeFilterChip(
                    label = if (gee.showAll) "Todas" else "Principal",
                    selected = !gee.showAll,
                    layout = layout,
                    onClick = {
                        channels = false
                        DeportesReturn.channels = false
                        league = null
                        DeportesReturn.league = null
                        gee.toggleShowAll()
                    },
                )
            }
            items(gee.categories, key = { it }) { category ->
                GeeFilterChip(
                    label = geeCategoryLabel(category),
                    selected = !channels && gee.selectedCategory == category,
                    layout = layout,
                    onClick = {
                        channels = false
                        DeportesReturn.channels = false
                        league = null
                        DeportesReturn.league = null
                        gee.selectCategory(category)
                    },
                )
            }
            item(key = "favoritos") {
                GeeFilterChip(
                    label = "Favoritos",
                    selected = gee.favoritesOnly,
                    layout = layout,
                    onClick = {
                        channels = false
                        DeportesReturn.channels = false
                        gee.toggleFavoritesOnly()
                    },
                )
            }
            item(key = "canales") {
                GeeFilterChip(
                    label = "Canales",
                    selected = channels,
                    layout = layout,
                    onClick = {
                        channels = true
                        DeportesReturn.channels = true
                        searchOpen = false
                        gee.clearSearch()
                    },
                )
            }
        }
        if (channels) {
            GeeChannelsTab(
                state = gee,
                onPlay = onPlay,
                bottomPadding = contentPadding.calculateBottomPadding(),
                horizontalPadding = layout.horizontalPadding.dp,
                scale = layout.cardScale,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(layout.chipGap.dp),
                contentPadding = PaddingValues(horizontal = layout.horizontalPadding.dp),
                modifier = Modifier.fillMaxWidth().padding(top = layout.chipRowGap.dp),
            ) {
                items(days, key = { it }) { date ->
                    GeeFilterChip(
                        label = SportsSource.dayLabel(date),
                        selected = gee.selectedDay == date,
                        layout = layout,
                        onClick = { gee.pickDay(date) },
                    )
                }
            }
            if (leagues.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(layout.chipGap.dp),
                    contentPadding = PaddingValues(horizontal = layout.horizontalPadding.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = layout.chipRowGap.dp),
                ) {
                    item(key = "liga_all") {
                        GeeFilterChip(
                            label = "Todas",
                            selected = activeLeague == null,
                            layout = layout,
                            onClick = {
                                league = null
                                DeportesReturn.league = null
                            },
                        )
                    }
                    items(leagues, key = { "liga_$it" }) { name ->
                        GeeFilterChip(
                            label = name,
                            selected = activeLeague == name,
                            layout = layout,
                            onClick = {
                                league = if (activeLeague == name) null else name
                                DeportesReturn.league = league
                            },
                            leading = leagueLogos[name]?.let { logo ->
                                {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(logo)
                                            .size(
                                                (28f * layout.cardScale).roundToInt(),
                                                (28f * layout.cardScale).roundToInt(),
                                            )
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(layout.chipIconSize.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            when {
                gee.loading && filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando…", color = VisionTextSecondary)
                }
                filtered.isEmpty() -> {
                    val subtitle = when {
                        gee.favoritesOnly && gee.favoriteTeams.isEmpty() ->
                            "Abre un partido y marca «Favorito» para seguirlo aquí."
                        gee.favoritesOnly ->
                            "No hay partidos de tus equipos favoritos en este día."
                        gee.searchQuery.isNotBlank() ->
                            "No hay partidos que coincidan con \"${gee.searchQuery}\"."
                        else -> "No hay eventos de este deporte en este momento."
                    }
                    EmptyState("Sin eventos", subtitle = subtitle)
                }
                else -> {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val columns = sportsGridColumns(maxWidth.value, layout)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = layout.horizontalPadding.dp,
                                end = layout.horizontalPadding.dp,
                                top = layout.gridTopPadding.dp,
                                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(layout.gridGap.dp),
                            verticalArrangement = Arrangement.spacedBy(layout.gridGap.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            sections.forEach { section ->
                                item(key = "sec_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                                    GeeSectionHeader(section.title, section.events.size, layout)
                                }
                                gridItems(section.events, key = { it.id }) { event ->
                                    GeeEventTile(
                                        event = event,
                                        nowMs = now,
                                        favorite = gee.isFavorite(event),
                                        layout = layout,
                                    ) {
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
            favorite = gee.isFavorite(event),
            onToggleFavorite = { gee.toggleFavorite(event) },
            canRemind = GeeAgenda.phaseOf(event, now) == GeeAgenda.Phase.Soon &&
                GeeAgenda.startMs(event) != null,
            reminderActive = remember(event.id, reminderTick) {
                SportsReminder.isActive(context, event)
            },
            onRemind = { minutes ->
                notifyGate {
                    val ok = SportsReminder.schedule(context, event, minutes)
                    reminderTick++
                    Toast.makeText(
                        context,
                        if (ok) "Te avisaremos $minutes min antes" else "Ya no da tiempo para ese aviso",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onClearReminder = {
                SportsReminder.clear(context, event)
                reminderTick++
                Toast.makeText(context, "Aviso quitado", Toast.LENGTH_SHORT).show()
            },
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
    val live = event.live || GeeAgenda.phaseOf(event) == GeeAgenda.Phase.Live
    val whenText = if (live) {
        SportsSchedule.liveDetail(GeeAgenda.startMs(event).takeIf { it != null && it > 0L }, event.category, event.detail)
    } else {
        val day = if (event.dateIso == today) "Hoy" else event.dateText.ifBlank { event.dateIso }
        "$day ${geeTimeLabel(event.time)}".trim()
    }
    return listOf(GeeAgenda.leagueLabel(event), whenText)
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
private fun GeeFilterChip(
    label: String,
    selected: Boolean,
    layout: SportsLayout,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = layout.chipTextSize.sp,
                lineHeight = (layout.chipTextSize * 1.3f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leading,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VisionBlue,
            selectedLabelColor = Color.White,
            containerColor = VisionSurface,
            labelColor = VisionTextSecondary,
        ),
        border = null,
        modifier = Modifier.heightIn(min = layout.chipHeight.dp),
    )
}

@Composable
private fun GeeSectionHeader(title: String, count: Int, layout: SportsLayout) {
    Row(
        Modifier.fillMaxWidth().padding(top = layout.sectionTopPadding.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == "En vivo") {
            Box(
                Modifier
                    .size((8f * layout.cardScale).dp)
                    .clip(CircleShape)
                    .background(LiveRed),
            )
            Spacer(Modifier.width((8f * layout.cardScale).dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = layout.sectionTitleSize.sp,
                lineHeight = (layout.sectionTitleSize * 1.25f).sp,
            ),
            color = if (title == "En vivo") Color.White else VisionTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = layout.subtitleSize.sp,
                lineHeight = (layout.subtitleSize * 1.35f).sp,
            ),
            color = VisionTextSecondary,
        )
    }
}

/**
 * La tarjeta-mosaico de un partido: escudos grandes, marcador real y estado.
 * Mismo ADN visual que la de Smart TV (misma lógica pura en [GeeTiles]).
 *
 * La liga y la píldora de estado van en dos filas: en una tarjeta de 160-240 dp
 * no caben juntas, y antes la píldora se comía el nombre de la liga y el alto
 * fijo recortaba el pie. Así las tres cosas se leen y la tarjeta no se estira.
 */
@Composable
private fun GeeEventTile(
    event: GeeSportsSource.GeeEvent,
    nowMs: Long,
    favorite: Boolean,
    layout: SportsLayout,
    onClick: () -> Unit,
) {
    val (home, away) = GeeTiles.teams(event)
    val hasTeams = home.isNotBlank()
    val s = layout.cardScale
    val leagueStyle = MaterialTheme.typography.labelSmall.scaled(11f, 13f, s)
    val eventStyle = MaterialTheme.typography.labelLarge.scaled(14f, 17f, s)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(layout.cardRadius.dp),
        color = VisionSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = layout.cardHeight.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.cardPaddingH.dp, vertical = layout.cardPaddingV.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    geeSportIcon(event.category),
                    contentDescription = null,
                    tint = VisionBlueBright,
                    modifier = Modifier.size((13f * s).dp),
                )
                Spacer(Modifier.width((5f * s).dp))
                val headerLogo = event.leagueLogo.ifBlank { event.logo }
                if (headerLogo.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(headerLogo)
                            .size((32f * s).roundToInt(), (32f * s).roundToInt())
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size((13f * s).dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width((5f * s).dp))
                }
                Text(
                    GeeAgenda.leagueLabel(event),
                    style = leagueStyle,
                    color = VisionTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (favorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorito",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size((14f * s).dp),
                    )
                    Spacer(Modifier.width((4f * s).dp))
                }
            }
            Spacer(Modifier.height((4f * s).dp))
            GeeStateBadge(event = event, nowMs = nowMs, scale = s)
            Spacer(Modifier.height((6f * s).dp))
            if (hasTeams) {
                GeeTileTeamLine(event.homeLogo, event.logo, home, event.scoreHome, event.live, s)
                Spacer(Modifier.height((4f * s).dp))
                GeeTileTeamLine(event.awayLogo, event.logo, away, event.scoreAway, event.live, s)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (event.logo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(event.logo)
                                .size((56f * s).roundToInt(), (56f * s).roundToInt())
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size((28f * s).dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width((8f * s).dp))
                    }
                    Text(
                        event.name,
                        style = eventStyle,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height((6f * s).dp))
            GeeTileFooter(event, nowMs, s)
        }
    }
}

@Composable
private fun GeeTileTeamLine(
    logo: String,
    fallbackLogo: String = "",
    name: String,
    score: Int?,
    live: Boolean,
    scale: Float,
) {
    val effectiveLogo = logo.ifBlank { fallbackLogo }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size((22f * scale).dp)
                .clip(CircleShape)
                .background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (effectiveLogo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(effectiveLogo)
                        .size((44f * scale).roundToInt(), (44f * scale).roundToInt())
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding((2f * scale).dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    GeeTiles.monogram(name),
                    style = MaterialTheme.typography.labelSmall.scaled(10f, 12f, scale),
                    color = VisionTextSecondary,
                )
            }
        }
        Spacer(Modifier.width((8f * scale).dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.scaled(12f, 14f, scale),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width((6f * scale).dp))
        Text(
            score?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium.scaled(16f, 18f, scale),
            fontWeight = FontWeight.Bold,
            color = if (live) VisionBlueBright else Color.White,
        )
    }
}

/** El pie: la guía de TV («TV: …») o cuántas señales hay. La píldora roja es SOLO para partidos en vivo. */
@Composable
private fun GeeTileFooter(event: GeeSportsSource.GeeEvent, nowMs: Long, scale: Float) {
    val footerStyle = MaterialTheme.typography.labelSmall.scaled(11f, 13f, scale)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val tvLine = event.broadcasts.take(2).joinToString(" · ")
        Text(
            if (tvLine.isNotBlank()) "TV: $tvLine" else GeeTiles.streams(event),
            style = footerStyle,
            color = if (tvLine.isBlank() && event.alive > 0) VisionBlueBright else VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // «en vivo» nunca en un partido que aún no comienza o ya terminó: `alive`
        // solo dice si el enlace responde, no que se esté jugando.
        if (GeeAgenda.playable(event, nowMs) && event.alive > 0) {
            Spacer(Modifier.width((6f * scale).dp))
            Surface(color = LiveRed, shape = RoundedCornerShape(50)) {
                Text(
                    "${event.alive} en vivo",
                    style = footerStyle,
                    color = Color.White,
                    modifier = Modifier.padding(
                        horizontal = (6f * scale).dp,
                        vertical = (1f * scale).dp,
                    ),
                )
            }
        }
    }
}

/** EN VIVO con minuto, Final, o la hora con el día (Hoy/mañana/fecha). */
@Composable
private fun GeeStateBadge(
    event: GeeSportsSource.GeeEvent,
    nowMs: Long,
    scale: Float,
) {
    val badgeStyle = MaterialTheme.typography.labelSmall.scaled(11f, 13f, scale)
    when {
        event.live || GeeAgenda.phaseOf(event, nowMs) == GeeAgenda.Phase.Live -> Surface(
            color = LiveRed,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                GeeTiles.liveLabel(event),
                style = badgeStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = (7f * scale).dp,
                    vertical = (2f * scale).dp,
                ),
            )
        }
        event.status == "post" -> Text(
            "Final",
            style = badgeStyle,
            color = VisionTextSecondary,
            maxLines = 1,
        )
        else -> Text(
            GeeTiles.state(event, SportsSource.todayIso()),
            style = badgeStyle,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
