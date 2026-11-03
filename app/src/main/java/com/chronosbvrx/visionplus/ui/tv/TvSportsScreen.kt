package com.chronosbvrx.visionplus.ui.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsReminder
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import com.chronosbvrx.visionplus.ui.notify.rememberNotifyGate
import com.chronosbvrx.visionplus.ui.sports.DeportesReturn
import com.chronosbvrx.visionplus.ui.sports.GEE_LINKS_REFRESH_MS
import com.chronosbvrx.visionplus.ui.sports.GeeTiles
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.sports.geeCategoryLabel
import com.chronosbvrx.visionplus.ui.sports.geeSportIcon
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
    var league by remember { mutableStateOf(DeportesReturn.league) }
    var channels by remember { mutableStateOf(DeportesReturn.channels) }
    var searchOpen by remember { mutableStateOf(false) }
    var reminderTick by remember { mutableStateOf(0) }
    // Al volver del reproductor, el partido que se estaba viendo manda: su menú reaparece
    // con la opción sintonizada marcada. Si no venimos de ahí, se respeta el partido que el
    // launcher haya pedido por enlace (`DeportesReturn.pickingId`).
    val returnedFromPlayer = remember { DeportesReturn.consumeWatchingEvent() }
    var pickingId by remember { mutableStateOf(returnedFromPlayer ?: DeportesReturn.pickingId) }
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
    val geeEntryFocus = remember { FocusRequester() }
    // Destino de aterrizaje SIEMPRE compuesto (el primer chip del deporte, o «Canales»):
    // con la agenda vacía o en camino no hay mosaico que enfocar y la pantalla se quedaba
    // sin destino (el mando parecía muerto hasta Atrás).
    val landing = rememberLandingFocus("tv_sports")
    val gridState = rememberLazyGridState()
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
    // El reloj entra en la llave: al pasar el tiempo los bloques se rearman solos (el
    // candado de «aún no comienza» se abre al llegar la hora).
    val now = gee.clockMs
    val sections = remember(filtered, now) { GeeAgenda.sections(filtered, now) }
    // El hero de Deportes sigue la misma regla que el de TV en vivo: primero el
    // partido en vivo y, si no hay, el próximo. Nunca un evento de liga secundaria.
    val featured = remember(sections) {
        sections.firstOrNull { it.title == "En vivo" }?.events?.firstOrNull()
            ?: sections.firstOrNull { it.title == "Comienzan pronto" }?.events?.firstOrNull()
    }
    val days = remember { (0..1).map { SportsSource.shift(SportsSource.todayIso(), it) } }

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
    // Enlaces siempre frescos: pase periódico de GeeSports cada 5 min mientras
    // Deportes está al frente (no espera al TTL de 8 min de la copia en disco).
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
    // Aterrizaje: la primera tarjeta del mosaico (o el primer canal en «Canales»); si aún no
    // existe —agenda vacía o en camino—, el foco cae en un chip compuesto. La llave incluye
    // «hay eventos»: cuando la agenda llega tarde, el intento se re-arma solo y el mosaico
    // queda enfocado sin que el usuario toque nada.
    //
    // Al volver del reproductor con el menú del partido abierto NO se reinicia el scroll ni se
    // roba el foco: la persona regresa donde estaba y el modal recupera su opción.
    var skipLanding by remember { mutableStateOf(pickingId != null) }
    LaunchedEffect(gee.selectedCategory, league, channels, gee.showAll, filtered.isEmpty()) {
        if (skipLanding) {
            skipLanding = false
            return@LaunchedEffect
        }
        runCatching { gridState.scrollToItem(0) }
        delay(140)
        if (!requestFocusSafely(geeEntryFocus)) landing.request()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(VisionBlack)
                .padding(horizontal = 48.dp, vertical = 12.dp),
        ) {
            // En agendas muy cortas el hero se come el mosaico: se muestra cuando
            // hay suficiente contenido para que siga viéndose más de un bloque.
            if (!channels && featured != null && filtered.size >= 3) {
                TvSportsHero(
                    event = featured,
                    nowMs = now,
                    modifier = Modifier.fillMaxWidth().height(106.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                item(key = "scope") {
                    TvSportsChip(
                        label = if (gee.showAll) "Todas las ligas" else "Principal",
                        selected = !gee.showAll,
                        icon = Icons.Default.Tune,
                        onClick = { gee.toggleShowAll() },
                    )
                }
                itemsIndexed(gee.categories, key = { _, it -> it }) { index, category ->
                    TvSportsChip(
                        label = geeCategoryLabel(category),
                        selected = !channels && gee.selectedCategory == category,
                        icon = geeSportIcon(category),
                        onClick = {
                            channels = false
                            DeportesReturn.channels = false
                            league = null
                            DeportesReturn.league = null
                            gee.selectCategory(category)
                        },
                        // El primer chip del deporte es el destino de respaldo (siempre compuesto).
                        modifier = (if (index == 0) Modifier.landingFocus(landing) else Modifier)
                            .softFocusDown(geeEntryFocus),
                    )
                }
                item(key = "favoritos") {
                    TvSportsChip(
                        label = "Favoritos",
                        selected = gee.favoritesOnly,
                        icon = Icons.Default.Star,
                        onClick = {
                            channels = false
                            DeportesReturn.channels = false
                            gee.toggleFavoritesOnly()
                        },
                        modifier = Modifier.softFocusDown(geeEntryFocus),
                    )
                }
                item(key = "buscar") {
                    TvSportsChip(
                        label = "Buscar",
                        selected = gee.searchQuery.isNotBlank(),
                        icon = Icons.Default.Search,
                        onClick = {
                            channels = false
                            DeportesReturn.channels = false
                            searchOpen = true
                        },
                        modifier = Modifier.softFocusDown(geeEntryFocus),
                    )
                }
                item(key = "canales") {
                    TvSportsChip(
                        label = "Canales",
                        selected = channels,
                        icon = Icons.Default.Tv,
                        onClick = {
                            channels = true
                            DeportesReturn.channels = true
                            searchOpen = false
                            gee.clearSearch()
                        },
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
                    modifier = Modifier.fillMaxWidth().focusGroup().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(days, key = { it }) { date ->
                        TvSportsChip(
                            label = SportsSource.dayLabel(date),
                            selected = gee.selectedDay == date,
                            onClick = { gee.pickDay(date) },
                            modifier = Modifier.softFocusDown(geeEntryFocus),
                        )
                    }
                    if (leagues.isNotEmpty()) {
                        item(key = "filtro_divisor") {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(Color.White.copy(alpha = 0.16f)),
                            )
                        }
                        item(key = "liga_all") {
                            TvSportsChip(
                                label = "Todas",
                                selected = activeLeague == null,
                                onClick = {
                                    league = null
                                    DeportesReturn.league = null
                                },
                                modifier = Modifier.softFocusDown(geeEntryFocus),
                            )
                        }
                        items(leagues, key = { "liga_$it" }) { name ->
                            TvSportsChip(
                                label = name,
                                selected = activeLeague == name,
                                logo = leagueLogos[name],
                                onClick = {
                                    league = if (activeLeague == name) null else name
                                    DeportesReturn.league = league
                                },
                                modifier = Modifier.softFocusDown(geeEntryFocus),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (filtered.isEmpty()) {
                    Text(
                        when {
                            gee.favoritesOnly && gee.favoriteTeams.isEmpty() ->
                                "Abre un partido y marca «Favorito» para seguirlo aquí."
                            gee.favoritesOnly ->
                                "No hay partidos de tus equipos favoritos en este día."
                            gee.searchQuery.isNotBlank() ->
                                "No hay partidos que coincidan con \"${gee.searchQuery}\"."
                            else -> "No hay eventos de este deporte en este momento."
                        },
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        sections.forEach { section ->
                            item(key = "sec_${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                                TvSportsSectionHeader(section.title, section.events.size)
                            }
                            gridItems(section.events, key = { it.id }) { event ->
                                TvSportsEventCard(
                                    event = event,
                                    nowMs = now,
                                    favorite = gee.isFavorite(event),
                                    modifier = if (event.id == firstEventId) Modifier.focusRequester(geeEntryFocus) else Modifier,
                                    onClick = {
                                        // Abrir el menú de otro partido ya no es «volver a lo que veías».
                                        DeportesReturn.forgetWatching()
                                        DeportesReturn.pickingId = event.id
                                        pickingId = event.id
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (searchOpen) {
            TvSportsSearchDialog(
                query = gee.searchQuery,
                onQueryChange = gee::updateSearchQuery,
                onDismiss = { searchOpen = false },
            )
        }

        picking?.let { event ->
            TvGeeLinkPickDialog(
                title = geeEventTitle(event),
                subtitle = geeEventSubtitle(event),
                links = event.links,
                xtv = event.xtv,
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
}

@Composable
private fun TvSportsSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DisposableEffect(Unit) {
        FocusRescue.dialogsOpen++
        onDispose { FocusRescue.dialogsOpen-- }
    }
    val landing = rememberLandingFocus("tv_sports_search")
    LaunchedEffect(Unit) {
        delay(120)
        landing.request()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .modalFocusTrap()
                .dismissOnBack(onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                colors = SurfaceDefaults.colors(containerColor = VisionSurface),
                border = Border(BorderStroke(1.dp, VisionBlue.copy(alpha = 0.4f)), shape = RoundedCornerShape(18.dp)),
                modifier = Modifier.width(620.dp).modalFocusTrap(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "Buscar partido",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        query.ifBlank { "Equipo, liga o canal" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (query.isBlank()) VisionTextSecondary else VisionBlueBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    TvKeyboard(
                        text = query,
                        onTextChange = onQueryChange,
                        firstKey = Modifier.landingFocus(landing),
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TvCompactAction("Limpiar", onClick = { onQueryChange("") })
                        Spacer(Modifier.width(8.dp))
                        TvCompactAction("Cerrar", onClick = onDismiss)
                    }
                }
            }
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
    favorite: Boolean,
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
                androidx.tv.material3.Icon(
                    geeSportIcon(event.category),
                    contentDescription = null,
                    tint = VisionBlueBright,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
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
                if (favorite) {
                    androidx.tv.material3.Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorito",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
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
