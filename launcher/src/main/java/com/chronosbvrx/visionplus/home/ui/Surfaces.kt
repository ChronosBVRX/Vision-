package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.home.HomeMetrics
import com.chronosbvrx.visionplus.home.contract.ContinueItem
import com.chronosbvrx.visionplus.home.contract.FeedItem
import com.chronosbvrx.visionplus.home.contract.FeedRow
import com.chronosbvrx.visionplus.home.contract.HeroItem
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.LiveItem
import com.chronosbvrx.visionplus.home.contract.MediaKind
import com.chronosbvrx.visionplus.home.contract.RowKind
import com.chronosbvrx.visionplus.home.contract.SportsItem
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.HomeUpdate

/**
 * El Inicio: hero + «Seguir viendo» + TV en vivo + apps + las filas del catálogo.
 *
 * Todo lo que se pinta viene del feed de Vision+; si el feed aún no llegó, la pantalla sigue
 * siendo un launcher (hero de marca + apps), nunca un hueco.
 */
@Composable
fun HomeSurface(
    feed: HomeFeed?,
    apps: List<AppEntry>,
    favorites: Set<String>,
    metrics: HomeMetrics,
    reduced: Boolean,
    compact: Boolean,
    landing: FocusRequester,
    onHeroPlay: (HeroItem) -> Unit,
    onHeroOpen: (HeroItem) -> Unit,
    onItem: (FeedItem) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onLive: (LiveItem) -> Unit,
    onApp: (AppEntry) -> Unit,
    onAppFavorite: (AppEntry) -> Unit,
    onSeeLive: () -> Unit,
    onSeeApps: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenVision: () -> Unit,
    onSystemSettings: () -> Unit,
    onPrimaryFocus: (Boolean) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("hero") {
            if (feed?.hero.isNullOrEmpty()) {
                BrandHero(
                    metrics = metrics,
                    landing = landing,
                    onOpenVision = onOpenVision,
                    onSystemSettings = onSystemSettings,
                    onPrimaryFocus = onPrimaryFocus,
                )
            } else {
                HeroStage(
                    items = feed.hero,
                    metrics = metrics,
                    reduced = reduced,
                    landing = landing,
                    onPlay = onHeroPlay,
                    onOpen = onHeroOpen,
                    onPrimaryFocus = onPrimaryFocus,
                )
            }
        }
        val continueItems = feed?.continueWatching.orEmpty()
        if (continueItems.isNotEmpty()) {
            item("continue") {
                ContinueRow(
                    items = continueItems,
                    metrics = metrics,
                    onOpen = onContinue,
                    onSeeAll = onOpenHistory,
                )
            }
        }
        val liveItems = feed?.live.orEmpty().take(12)
        if (liveItems.isNotEmpty()) {
            item("live") {
                LiveRow(
                    items = liveItems,
                    metrics = metrics,
                    onOpen = onLive,
                    onSeeAll = onSeeLive,
                )
            }
        }
        if (apps.isNotEmpty()) {
            item("apps") {
                AppsRow(
                    apps = orderedApps(apps, favorites, emptyList()).take(if (compact) 8 else 10),
                    metrics = metrics,
                    onOpen = onApp,
                    onLongClick = onAppFavorite,
                    onSeeAll = onSeeApps,
                )
            }
        }
        feed?.rows?.forEach { row ->
            if (row.items.isNotEmpty()) {
                item("row-${row.id}") {
                    PosterRow(
                        row = row,
                        metrics = metrics,
                        onOpen = onItem,
                        onSeeAll = null,
                    )
                }
            }
        }
    }
}

/**
 * Cuando el feed todavía no llega (o Vision+ no está): la marca manda. No es una pantalla de
 * error, es el Inicio de un sistema recién encendido.
 */
@Composable
private fun BrandHero(
    metrics: HomeMetrics,
    landing: FocusRequester,
    onOpenVision: () -> Unit,
    onSystemSettings: () -> Unit,
    onPrimaryFocus: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(metrics.heroHeight)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0D1D33), Color(0xFF0A1120), HomeColors.Black),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to HomeColors.Black.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = metrics.horizontalPadding, bottom = 28.dp)
                .widthIn(max = 580.dp),
        ) {
            Badge(text = "VISION+ HOME")
            Spacer(Modifier.height(12.dp))
            Text(
                "Todo tu entretenimiento,\nen un solo lugar",
                color = Color.White,
                fontSize = (metrics.titleSize + 2).sp,
                lineHeight = (metrics.titleSize + 8).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "TV en vivo, películas, series y tus apps, con el mando de la tele.",
                color = HomeColors.TextDim,
                fontSize = metrics.synopsisSize.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeButton(
                    label = "Abrir Vision+",
                    onClick = onOpenVision,
                    primary = true,
                    modifier = Modifier.focusRequester(landing),
                    onFocusChanged = onPrimaryFocus,
                )
                HomeButton(
                    label = "Ajustes de la tele",
                    onClick = onSystemSettings,
                    icon = Icons.Rounded.Settings,
                )
            }
        }
    }
}

/** Aviso honesto cuando Vision+ está instalado pero se quedó atrás (o no responde). */
@Composable
fun OutdatedVisionBanner(
    current: String,
    latest: String,
    state: HomeUpdate.State,
    canInstall: Boolean,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state is HomeUpdate.State.Checking || state is HomeUpdate.State.Downloading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HomeShapes.panel)
            .background(HomeColors.Surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.Download, null, tint = HomeColors.BlueBright, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Vision+ está desactualizado",
                color = HomeColors.Text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when {
                    !canInstall ->
                        "Permite «Instalar apps desconocidas» para Vision+ Home (un paso del sistema) y vuelve aquí."
                    state is HomeUpdate.State.Checking -> "Buscando la última versión en el release oficial…"
                    state is HomeUpdate.State.Downloading -> "Descargando Vision+… ${state.percent} %"
                    state is HomeUpdate.State.Ready -> "Descarga lista: confirma la instalación en la pantalla del sistema."
                    state is HomeUpdate.State.Failed -> "No se pudo: ${state.message}"
                    current.isBlank() -> "Vision+ no responde al Inicio; actualízalo para recuperar el catálogo."
                    latest.isBlank() -> "Hay una versión nueva de Vision+ en el release."
                    else -> "v$current → v$latest"
                },
                color = HomeColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state is HomeUpdate.State.Downloading) {
                Spacer(Modifier.height(6.dp))
                ThinProgress(state.percent)
            }
        }
        HomeButton(
            label = when {
                !canInstall -> "Permitir instalación"
                state is HomeUpdate.State.Downloading -> "${state.percent} %"
                state is HomeUpdate.State.Checking -> "Buscando…"
                state is HomeUpdate.State.Ready -> "Instalando…"
                state is HomeUpdate.State.Failed -> "Reintentar"
                else -> "Actualizar Vision+"
            },
            onClick = { if (!busy) onUpdate() },
            icon = if (canInstall) Icons.Rounded.Download else Icons.Rounded.Lock,
            primary = true,
        )
        HomeButton(label = "Ajustes", onClick = onSettings, icon = Icons.Rounded.Settings, primary = false)
    }
}

/** Aviso honesto cuando Vision+ no está instalado: el launcher se pone completo él solo. */
@Composable
fun MissingVisionBanner(
    state: HomeUpdate.State,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state is HomeUpdate.State.Checking || state is HomeUpdate.State.Downloading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HomeShapes.panel)
            .background(HomeColors.Surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.SettingsRemote, null, tint = HomeColors.BlueBright, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Vision+ no está instalado",
                color = HomeColors.Text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when {
                    !canInstall ->
                        "Permite «Instalar apps desconocidas» para Vision+ Home (un paso del sistema) y vuelve aquí."
                    state is HomeUpdate.State.Checking -> "Buscando la última versión en el release oficial…"
                    state is HomeUpdate.State.Downloading -> "Descargando Vision+… ${state.percent} %"
                    state is HomeUpdate.State.Ready -> "Descarga lista: confirma la instalación en la pantalla del sistema."
                    state is HomeUpdate.State.Failed -> "No se pudo: ${state.message}"
                    else -> "Instálalo desde aquí mismo (se baja del release oficial). Las apps ya funcionan."
                },
                color = HomeColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state is HomeUpdate.State.Downloading) {
                Spacer(Modifier.height(6.dp))
                ThinProgress(state.percent)
            }
        }
        HomeButton(
            label = when {
                !canInstall -> "Permitir instalación"
                state is HomeUpdate.State.Downloading -> "${state.percent} %"
                state is HomeUpdate.State.Checking -> "Buscando…"
                state is HomeUpdate.State.Ready -> "Instalando…"
                state is HomeUpdate.State.Failed -> "Reintentar"
                else -> "Instalar Vision+"
            },
            onClick = { if (!busy) onInstall() },
            icon = if (canInstall) Icons.Rounded.Download else Icons.Rounded.Lock,
            primary = true,
        )
        HomeButton(label = "Ajustes", onClick = onSettings, icon = Icons.Rounded.Settings, primary = false)
    }
}

@Composable
private fun PosterRow(
    row: FeedRow,
    metrics: HomeMetrics,
    onOpen: (FeedItem) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = row.label,
            subtitle = if (row.kind == RowKind.TOP10) "Lo más visto de la semana" else null,
            actionLabel = if (onSeeAll != null) "Ver todo" else null,
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.Top,
        ) {
            rowItemsIndexed(row.items, key = { _, item -> item.ref.ifBlank { item.title } }) { index, item ->
                PosterCard(
                    item = item,
                    width = metrics.posterWidth,
                    height = metrics.posterHeight,
                    rank = if (row.kind == RowKind.TOP10) index + 1 else null,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun ContinueRow(
    items: List<ContinueItem>,
    metrics: HomeMetrics,
    onOpen: (ContinueItem) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Seguir viendo",
            subtitle = "Donde te quedaste",
            actionLabel = "Ver historial",
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.Top,
        ) {
            rowItems(items, key = { it.key }) { item ->
                ContinueCard(
                    item = item,
                    width = metrics.continueWidth,
                    height = metrics.continueHeight,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun LiveRow(
    items: List<LiveItem>,
    metrics: HomeMetrics,
    onOpen: (LiveItem) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "TV en vivo",
            subtitle = "Ahora en tu programación",
            actionLabel = "Ver todos",
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.Top,
        ) {
            rowItems(items, key = { it.code }) { item ->
                ChannelCard(
                    item = item,
                    height = metrics.channelHeight,
                    modifier = Modifier.width(metrics.channelWidth),
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun AppsRow(
    apps: List<AppEntry>,
    metrics: HomeMetrics,
    onOpen: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Apps",
            subtitle = "Mantén OK para fijar una app en el Inicio",
            actionLabel = "Ver todas",
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
        ) {
            rowItems(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    tile = metrics.appTile,
                    onClick = { onOpen(app) },
                    onLongClick = { onLongClick(app) },
                )
            }
        }
    }
}

/** La pestaña TV en vivo: la rejilla completa de canales del feed (México primero). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveSurface(
    items: List<LiveItem>,
    metrics: HomeMetrics,
    onOpen: (LiveItem) -> Unit,
    onOpenFull: () -> Unit,
    upTarget: FocusRequester? = null,
) {
    var onlyMexico by remember { mutableStateOf(false) }
    val visible = if (onlyMexico) items.filter { it.mexico } else items
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
        val columns = when {
            maxWidth >= 1200.dp -> 6
            maxWidth >= 960.dp -> 5
            maxWidth >= 720.dp -> 4
            else -> 3
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalArrangement = Arrangement.spacedBy(if (metrics.channelHeight < 90.dp) 12.dp else 16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    title = "TV en vivo",
                    subtitle = "${visible.size} canales · México primero, luego los populares",
                    actionLabel = "Abrir en Vision+",
                    actionModifier = upTarget?.let { Modifier.focusProperties { up = it } } ?: Modifier,
                    onAction = onOpenFull,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveFilterChip("Todos", selected = !onlyMexico) { onlyMexico = false }
                    LiveFilterChip("México", selected = onlyMexico) { onlyMexico = true }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint(
                        title = if (onlyMexico) "No hay canales de México en la lista" else "Aún no hay canales en el Inicio",
                        body = "Abre TV en vivo en Vision+ una vez: en cuanto cargue la lista, aparece aquí.",
                        action = "Abrir TV en vivo",
                        onAction = onOpenFull,
                    )
                }
            }
            gridItems(visible, key = { it.code }) { item ->
                ChannelCard(
                    item = item,
                    height = metrics.channelHeight,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun LiveFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.pill,
        modifier = Modifier.height(34.dp),
        focusedScale = 1.05f,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .background(if (selected) HomeColors.Blue else HomeColors.Surface)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) Color.White else HomeColors.TextDim,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

/** La pestaña Deportes: deporte, liga y día como la agenda de Vision+, y abre el evento. */
@Composable
fun EventsSurface(
    events: List<SportsItem>,
    metrics: HomeMetrics,
    onOpen: (SportsItem) -> Unit,
    onOpenFull: () -> Unit,
) {
    val today = remember { todayIsoMx() }
    val days = remember(events, today) { eventDays(events, today) }
    var day by remember(events) { mutableStateOf(days.firstOrNull()?.first ?: today) }
    var category by remember { mutableStateOf<String?>(null) }
    var league by remember { mutableStateOf<String?>(null) }

    val categories = remember(events) { eventCategories(events) }
    val leagues = remember(events, day, category) { eventLeagues(events, day, category) }
    LaunchedEffect(days) {
        if (days.isNotEmpty() && days.none { it.first == day }) day = days.first().first
    }
    LaunchedEffect(leagues) {
        if (league != null && league !in leagues) league = null
    }

    val visible = remember(events, day, category, league, today) {
        events.filter { event ->
            val eventDay = event.dateIso.ifBlank { today }
            (eventDay == day) &&
                (category == null || event.category.equals(category, ignoreCase = true)) &&
                (league == null || event.league == league)
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        // Encabezado y filtros FUERA de la rejilla: así no se superponen al hacer scroll.
        Column(Modifier.padding(horizontal = metrics.horizontalPadding)) {
            SectionHeader(
                title = "Deportes en vivo",
                subtitle = if (events.isEmpty()) {
                    "La agenda de Vision+ aparecerá aquí"
                } else {
                    "${visible.size} eventos · ${visible.count { it.live }} en vivo"
                },
                actionLabel = "Abrir en Vision+",
                onAction = onOpenFull,
            )
            Spacer(Modifier.height(10.dp))
            FilterRow(
                allLabel = "Todos los deportes",
                options = categories.map { it to sportLabel(it) },
                selected = category,
                onSelect = { category = it },
            )
            Spacer(Modifier.height(8.dp))
            FilterRow(
                allLabel = null,
                options = days,
                selected = day,
                onSelect = { day = it ?: today },
            )
            if (leagues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FilterRow(
                    allLabel = "Todas las ligas",
                    options = leagues.map { it to it },
                    selected = league,
                    onSelect = { league = it },
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columns = when {
                maxWidth >= 1200.dp -> 3
                maxWidth >= 860.dp -> 2
                else -> 1
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (events.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHint(
                            title = "No hay eventos ahora",
                            body = "Abre Deportes en vivo en Vision+ para cargar la agenda; en cuanto llegue, se pinta aquí.",
                            action = "Abrir Deportes",
                            onAction = onOpenFull,
                        )
                    }
                } else if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHint(
                            title = "Sin eventos con esos filtros",
                            body = "Prueba con otro deporte, otra liga u otro día de la agenda.",
                            action = "Quitar filtros",
                            onAction = {
                                category = null
                                league = null
                            },
                        )
                    }
                }
                gridItems(visible, key = { it.id }) { event ->
                    EventCard(event = event, onClick = { onOpen(event) })
                }
            }
        }
    }
}

/** Una fila de chips deslizable (deporte / día / liga). */
@Composable
private fun FilterRow(
    allLabel: String?,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allLabel != null) {
            FilterPill(label = allLabel, selected = selected == null) { onSelect(null) }
        }
        options.forEach { option ->
            FilterPill(label = option.second, selected = selected == option.first) {
                onSelect(if (selected == option.first && allLabel != null) null else option.first)
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.pill,
        modifier = Modifier.height(34.dp),
        focusedScale = 1.05f,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .background(if (selected) HomeColors.Blue else HomeColors.Surface)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) Color.White else HomeColors.TextDim,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

// --- Agenda: piezas puras (con guardián) ---

private val MX_ZONE = java.util.TimeZone.getTimeZone("America/Mexico_City")

internal fun todayIsoMx(nowMs: Long = System.currentTimeMillis()): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .apply { timeZone = MX_ZONE }
        .format(java.util.Date(nowMs))

internal fun dayLabelMx(dateIso: String, todayIso: String): String {
    if (dateIso == todayIso) return "Hoy"
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .apply { timeZone = MX_ZONE }
    val date = runCatching { fmt.parse(dateIso) }.getOrNull() ?: return dateIso
    val today = runCatching { fmt.parse(todayIso) }.getOrNull() ?: return dateIso
    val diff = ((date.time - today.time) / 86_400_000L).toInt()
    if (diff == 1) return "Mañana"
    val label = java.text.SimpleDateFormat("EEE d", java.util.Locale.forLanguageTag("es-MX"))
        .apply { timeZone = MX_ZONE }
        .format(date)
    return label.replaceFirstChar { it.uppercase() }
}

/** Los días de la agenda (iso → etiqueta), del más cercano al más lejano. */
internal fun eventDays(events: List<SportsItem>, todayIso: String): List<Pair<String, String>> =
    events.asSequence()
        .map { it.dateIso }
        .filter { it.isNotBlank() && it >= todayIso }
        .distinct()
        .sorted()
        .map { it to dayLabelMx(it, todayIso) }
        .toList()

/** Los deportes presentes, con Fútbol primero y luego por cantidad. */
internal fun eventCategories(events: List<SportsItem>): List<String> =
    events.asSequence()
        .map { it.category }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.key.equals("Football", ignoreCase = true) }
                .thenByDescending { it.value }
                .thenBy { it.key },
        )
        .map { it.key }
        .toList()

/** Las ligas con eventos en el día (y deporte) elegidos, por cantidad. */
internal fun eventLeagues(events: List<SportsItem>, dayIso: String, category: String?): List<String> =
    events.asSequence()
        .filter { it.dateIso.ifBlank { dayIso } == dayIso }
        .filter { category == null || it.category.equals(category, ignoreCase = true) }
        .map { it.league }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.key.contains("liga mx", ignoreCase = true) }
                .thenByDescending { it.value }
                .thenBy { it.key },
        )
        .map { it.key }
        .toList()

internal fun sportLabel(category: String): String = when (category.trim().lowercase()) {
    "football", "soccer" -> "Fútbol"
    "basketball" -> "Básquet"
    "tennis" -> "Tenis"
    "baseball" -> "Béisbol"
    "boxing" -> "Box"
    "f1", "formula 1", "motorsport", "motorsports", "racing", "motor", "nascar", "motogp" -> "Motor"
    "american football", "nfl" -> "Americano"
    "hockey" -> "Hockey"
    "rugby" -> "Rugby"
    "volleyball" -> "Vóley"
    "handball" -> "Balonmano"
    "golf" -> "Golf"
    "cricket" -> "Cricket"
    "mma", "ufc" -> "MMA"
    else -> category.replaceFirstChar { it.uppercase() }
}

@Composable
private fun EventCard(event: SportsItem, onClick: () -> Unit) {
    FocusableSurface(onClick = onClick, shape = HomeShapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(HomeColors.Surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.leagueLogo.isNotBlank()) {
                    AsyncImage(
                        model = event.leagueLogo,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    event.league.ifBlank { event.dateText },
                    color = HomeColors.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (event.live) {
                    Badge(text = "EN VIVO", dot = true, color = HomeColors.Live)
                } else {
                    Text(
                        event.detail.ifBlank { event.time },
                        color = HomeColors.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val hasTeams = event.homeName.isNotBlank() && event.awayName.isNotBlank() &&
                !event.homeName.equals(event.awayName, ignoreCase = true)
            if (hasTeams) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamSide(event.homeName, event.homeLogo, Modifier.weight(1f))
                    Text(
                        scoreLabel(event),
                        color = if (event.live) HomeColors.Text else HomeColors.BlueBright,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    TeamSide(event.awayName, event.awayLogo, Modifier.weight(1f), alignEnd = true)
                }
            } else {
                Text(
                    displayName(event),
                    color = HomeColors.Text,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    footerLabel(event),
                    color = HomeColors.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (event.total > 0) {
                    Text(
                        "${event.alive} de ${event.total} señales",
                        color = HomeColors.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamSide(name: String, logo: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (alignEnd) Spacer(Modifier.weight(1f))
        if (logo.isNotBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            name,
            color = HomeColors.Text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!alignEnd) Spacer(Modifier.weight(1f))
    }
}

private fun scoreLabel(event: SportsItem): String {
    val home = event.homeScore
    val away = event.awayScore
    return if (home != null && away != null) "$home - $away" else "vs"
}

/** «MotorGP vs MotorGP» (deporte individual) se muestra una sola vez. */
internal fun displayName(event: SportsItem): String {
    val parts = event.name.split(" vs ", " VS ", " Vs ")
    return if (parts.size == 2 && parts[0].trim().equals(parts[1].trim(), ignoreCase = true)) {
        parts[0].trim()
    } else {
        event.name
    }
}

/** En vivo con minuto → el minuto; en vivo sin minuto → la fecha; si no → la hora. */
internal fun footerLabel(event: SportsItem): String = when {
    event.live && event.detail.isNotBlank() && !event.detail.equals("EN VIVO", ignoreCase = true) -> event.detail
    event.live -> event.dateText.ifBlank { event.time }
    else -> event.time.ifBlank { event.dateText }
}

/** La pestaña Películas / Series: la selección del feed, en rejilla. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CatalogSurface(
    tab: HomeTab,
    feed: HomeFeed?,
    metrics: HomeMetrics,
    onOpen: (FeedItem) -> Unit,
    onOpenFull: () -> Unit,
    upTarget: FocusRequester? = null,
) {
    val items = catalogItems(feed, tab)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1200.dp -> 7
            maxWidth >= 960.dp -> 6
            maxWidth >= 720.dp -> 5
            else -> 4
        }
        val usable = maxWidth - metrics.horizontalPadding * 2 - metrics.gap * (columns - 1)
        val cellWidth = usable / columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    title = tab.label,
                    subtitle = if (items.isEmpty()) {
                        "El catálogo completo se explora en Vision+"
                    } else {
                        "Selección de Vision+ · ${items.size} títulos"
                    },
                    actionLabel = "Ver todo en Vision+",
                    actionModifier = upTarget?.let { Modifier.focusProperties { up = it } } ?: Modifier,
                    onAction = onOpenFull,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint(
                        title = "Aún no hay títulos aquí",
                        body = "Abre Vision+ para explorar el catálogo completo; en cuanto veas algo, aparece en este Inicio.",
                        action = "Abrir Vision+",
                        onAction = onOpenFull,
                    )
                }
            }
            gridItems(items, key = { it.ref }) { item ->
                PosterCard(
                    item = item,
                    width = cellWidth,
                    height = cellWidth * 1.5f,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

/** La pestaña Apps: todas las apps instaladas y los accesos del sistema. */
@Composable
fun AppsSurface(
    apps: List<AppEntry>,
    metrics: HomeMetrics,
    onOpen: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    onSystemSettings: () -> Unit,
    onWifi: () -> Unit,
    onHomeSettings: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1200.dp -> 8
            maxWidth >= 960.dp -> 7
            maxWidth >= 720.dp -> 6
            else -> 5
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    title = "Apps",
                    subtitle = "${apps.size} instaladas · mantén OK para fijar en el Inicio",
                )
                Spacer(Modifier.height(6.dp))
            }
            gridItems(apps, key = { it.packageName }) { app ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppTile(
                        app = app,
                        tile = metrics.appTile,
                        onClick = { onOpen(app) },
                        onLongClick = { onLongClick(app) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(10.dp))
                SectionHeader(title = "Sistema")
                Spacer(Modifier.height(2.dp))
            }
            gridItems(
                listOf(
                    SystemAction("settings", "Ajustes de la tele", Icons.Rounded.Settings, onSystemSettings),
                    SystemAction("wifi", "Wi-Fi y red", Icons.Rounded.Wifi, onWifi),
                    SystemAction("home", "Cambiar el Inicio", Icons.Rounded.Home, onHomeSettings),
                ),
                key = { it.id },
            ) { action ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ActionTile(
                        icon = action.icon,
                        label = action.label,
                        tile = metrics.appTile,
                        onClick = action.onClick,
                    )
                }
            }
        }
    }
}

private data class SystemAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ActionTile(icon: ImageVector, label: String, tile: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(tile + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.size(tile),
            shape = HomeShapes.tile,
            focusedScale = 1.07f,
        ) {
            Box(
                Modifier.fillMaxSize().background(HomeColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = HomeColors.TextDim, modifier = Modifier.size(tile * 0.42f))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = HomeColors.TextDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyHint(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HomeShapes.panel)
            .background(HomeColors.Surface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = HomeColors.Text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = HomeColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        HomeButton(label = action, onClick = onAction)
    }
}

// --- Lógica pura ---

/** Favoritas primero, luego las usadas hace poco, luego alfabético. */
internal fun orderedApps(
    apps: List<AppEntry>,
    favorites: Set<String>,
    recent: List<String>,
): List<AppEntry> {
    val recency = HashMap<String, Int>(recent.size * 2)
    recent.forEachIndexed { index, pkg -> recency[pkg] = index }
    return apps.sortedWith(
        compareByDescending<AppEntry> { it.packageName in favorites }
            .thenBy { recency[it.packageName] ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
    )
}

/** Los títulos de una pestaña de catálogo, sin repetir y con tope. */
internal fun catalogItems(feed: HomeFeed?, tab: HomeTab): List<FeedItem> {
    if (feed == null) return emptyList()
    val kinds = when (tab) {
        HomeTab.MOVIES -> setOf(MediaKind.MOVIE)
        HomeTab.SERIES -> setOf(MediaKind.SERIES, MediaKind.ANIME, MediaKind.KIDS)
        else -> return emptyList()
    }
    val fromRows = feed.rows.flatMap { it.items }
    val fromHero = feed.hero.map { hero ->
        FeedItem(
            ref = hero.ref,
            title = hero.title,
            poster = hero.poster,
            year = hero.year,
            rating = hero.rating,
            kind = hero.kind,
            badge = hero.badge,
            series = hero.series,
            openLink = hero.openLink,
            playLink = hero.playLink,
        )
    }
    return (fromRows + fromHero)
        .filter { it.kind in kinds }
        .distinctBy { it.ref }
        .take(60)
}
