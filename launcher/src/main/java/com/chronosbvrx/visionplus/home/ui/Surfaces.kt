@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.focusRestorer
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
import com.chronosbvrx.visionplus.home.data.HomeConfig
import com.chronosbvrx.visionplus.home.data.favoriteChannels
import com.chronosbvrx.visionplus.home.data.homeRowOrder
import com.chronosbvrx.visionplus.home.data.visibleApps
import com.chronosbvrx.visionplus.home.data.visibleChannels
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
    recentApps: List<String>,
    config: HomeConfig,
    metrics: HomeMetrics,
    reduced: Boolean,
    compact: Boolean,
    landing: FocusRequester,
    listState: LazyListState,
    tabLanding: FocusRequester? = null,
    onHeroPlay: (HeroItem) -> Unit,
    onHeroOpen: (HeroItem) -> Unit,
    onItem: (FeedItem) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onLive: (LiveItem) -> Unit,
    onChannelLongClick: (LiveItem) -> Unit,
    onApp: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onSeeLive: () -> Unit,
    onSeeApps: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenVision: () -> Unit,
    onSystemSettings: () -> Unit,
    onPrimaryFocus: (Boolean) -> Unit = {},
) {
    val feedRows = feed?.rows.orEmpty()
    val feedById = remember(feedRows) { feedRows.associateBy { it.id } }
    val rowIds = remember(config.rowOrder, feedRows) { homeRowOrder(config, feedRows) }
    val liveItems = remember(feed?.live, config) {
        visibleChannels(feed?.live.orEmpty(), config)
    }
    val favChannels = remember(feed?.live, config) {
        favoriteChannels(feed?.live.orEmpty(), config)
    }
    val appsView = remember(apps, config, recentApps) {
        visibleApps(apps, config, recentApps)
    }
    val continueItems = feed?.continueWatching.orEmpty()
    val limit = config.display.itemsPerRow
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item("hero") {
                if (feed?.hero.isNullOrEmpty()) {
                    BrandHero(
                        metrics = metrics,
                        landing = landing,
                        upTarget = tabLanding,
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
                        upTarget = tabLanding,
                        onPlay = onHeroPlay,
                        onOpen = onHeroOpen,
                        onPrimaryFocus = onPrimaryFocus,
                    )
                }
            }
            rowIds.forEach { id ->
                if (id in config.hiddenRows) return@forEach
                when (id) {
                    "continue" -> if (continueItems.isNotEmpty()) {
                        item("continue") {
                            ContinueRow(
                                items = continueItems.take(limit),
                                metrics = metrics,
                                onOpen = onContinue,
                                onSeeAll = onOpenHistory,
                            )
                        }
                    }

                    "channels" -> if (favChannels.isNotEmpty()) {
                        item("channels") {
                            LiveRow(
                                items = favChannels,
                                metrics = metrics,
                                title = "Mis canales",
                                subtitle = "Tus favoritos, en tu orden",
                                limit = limit,
                                showEpg = config.display.showEpg,
                                onOpen = onLive,
                                onSeeAll = onSeeLive,
                                onLongClick = onChannelLongClick,
                            )
                        }
                    }

                    "live" -> if (liveItems.isNotEmpty()) {
                        item("live") {
                            LiveRow(
                                items = liveItems,
                                metrics = metrics,
                                title = "TV en vivo",
                                subtitle = "Ahora en tu programación",
                                limit = limit,
                                showEpg = config.display.showEpg,
                                onOpen = onLive,
                                onSeeAll = onSeeLive,
                                onLongClick = onChannelLongClick,
                            )
                        }
                    }

                    "apps" -> if (appsView.isNotEmpty()) {
                        item("apps") {
                            AppsRow(
                                apps = appsView.take(limit),
                                metrics = metrics,
                                onOpen = onApp,
                                onLongClick = onAppLongClick,
                                onSeeAll = onSeeApps,
                            )
                        }
                    }

                    else -> feedById[id]?.let { row ->
                        if (row.items.isNotEmpty()) {
                            item("row-${row.id}") {
                                PosterRow(
                                    row = row,
                                    metrics = metrics,
                                    limit = limit,
                                    onOpen = onItem,
                                    onSeeAll = null,
                                )
                            }
                        }
                    }
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
    upTarget: FocusRequester? = null,
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
                    modifier = Modifier
                        .focusRequester(landing)
                        .then(if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier),
                    onFocusChanged = onPrimaryFocus,
                )
                HomeButton(
                    label = "Ajustes de la tele",
                    onClick = onSystemSettings,
                    icon = Icons.Rounded.Settings,
                    modifier = if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier,
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
    limit: Int = 18,
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
            modifier = Modifier.focusGroup().focusRestorer(),
            contentPadding = PaddingValues(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.Top,
        ) {
            rowItemsIndexed(row.items.take(limit), key = { _, item -> item.ref.ifBlank { item.title } }) { index, item ->
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
            modifier = Modifier.focusGroup().focusRestorer(),
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
    title: String,
    subtitle: String,
    limit: Int,
    showEpg: Boolean,
    onOpen: (LiveItem) -> Unit,
    onSeeAll: () -> Unit,
    onLongClick: (LiveItem) -> Unit,
) {
    val nowMs = rememberNowMs()
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            actionLabel = "Ver todos",
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.focusGroup().focusRestorer(),
            contentPadding = PaddingValues(horizontal = metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            verticalAlignment = Alignment.Top,
        ) {
            rowItems(items.take(limit), key = { it.code }) { item ->
                ChannelCard(
                    item = item,
                    height = metrics.channelHeight,
                    modifier = Modifier.width(metrics.channelWidth),
                    nowMs = nowMs,
                    showEpg = showEpg,
                    onLongClick = { onLongClick(item) },
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
            subtitle = "Mantén OK para opciones",
            actionLabel = "Ver todas",
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.focusGroup().focusRestorer(),
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

/** La pestaña TV en vivo: mosaico con nombre y guía AHORA de cada canal. */
private enum class LiveFilter { ALL, MEXICO, FAVORITES }

/** Rótulo que viaja al reproductor para el aviso de cable y el menú de zapping. */
private fun liveScopeLabel(filter: LiveFilter): String = when (filter) {
    LiveFilter.ALL -> "Todos los canales"
    LiveFilter.MEXICO -> "Canales de México"
    LiveFilter.FAVORITES -> "Mis canales"
}

@Composable
fun LiveSurface(
    items: List<LiveItem>,
    config: HomeConfig,
    metrics: HomeMetrics,
    onOpen: (LiveItem, List<LiveItem>, String) -> Unit,
    onOpenFull: () -> Unit,
    onLongClick: (LiveItem) -> Unit,
    onManageChannels: () -> Unit,
    upTarget: FocusRequester? = null,
    landing: FocusRequester? = null,
) {
    var filter by remember { mutableStateOf(LiveFilter.ALL) }
    val base = remember(items, config) { visibleChannels(items, config) }
    val favorites = remember(items, config) { favoriteChannels(items, config) }
    val visible = when (filter) {
        LiveFilter.ALL -> base
        LiveFilter.MEXICO -> base.filter { it.mexico }
        LiveFilter.FAVORITES -> favorites
    }
    val nowMs = rememberNowMs()
    val entry = remember { FocusRequester() }
    val firstCode = visible.firstOrNull()?.code
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
            val columns = when {
                maxWidth >= 1200.dp -> 6
                maxWidth >= 960.dp -> 5
                maxWidth >= 720.dp -> 4
                else -> 3
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    top = 8.dp,
                    bottom = 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(metrics.gap),
                verticalArrangement = Arrangement.spacedBy(if (metrics.channelHeight < 110.dp) 12.dp else 16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = "TV en vivo",
                            subtitle = when (filter) {
                                LiveFilter.FAVORITES -> "${visible.size} favoritos · tu orden"
                                LiveFilter.MEXICO -> "${visible.size} canales de México"
                                LiveFilter.ALL -> "${visible.size} canales · nombre y guía AHORA"
                            },
                            actionLabel = "Gestionar",
                            actionModifier = upTarget?.let { Modifier.focusProperties { up = it } } ?: Modifier,
                            onAction = onManageChannels,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LiveFilterChip(
                                label = "Todos",
                                selected = filter == LiveFilter.ALL,
                                onClick = { filter = LiveFilter.ALL },
                                modifier = Modifier
                                    .then(if (landing != null) Modifier.focusRequester(landing) else Modifier)
                                    .softFocusDown(entry),
                            )
                            LiveFilterChip(
                                label = "México",
                                selected = filter == LiveFilter.MEXICO,
                                onClick = { filter = LiveFilter.MEXICO },
                                modifier = Modifier.softFocusDown(entry),
                            )
                            LiveFilterChip(
                                label = "Favoritos",
                                selected = filter == LiveFilter.FAVORITES,
                                onClick = { filter = LiveFilter.FAVORITES },
                                modifier = Modifier.softFocusDown(entry),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val emptyTitle = when {
                            filter == LiveFilter.FAVORITES -> "Aún no tienes canales favoritos"
                            filter == LiveFilter.MEXICO -> "No hay canales de México en la lista"
                            items.isEmpty() -> "Aún no hay canales en el Inicio"
                            else -> "No hay canales visibles con tus ajustes"
                        }
                        val emptyBody = when {
                            filter == LiveFilter.FAVORITES ->
                                "Mantén OK en un canal del mosaico para fijarlo, o gestiónalos aquí."
                            items.isEmpty() ->
                                "Abre TV en vivo en Vision+ una vez: en cuanto cargue la lista, aparece aquí."
                            else -> "Puedes volver a mostrar los canales que ocultaste."
                        }
                        val emptyAction = if (items.isNotEmpty()) "Gestionar canales" else "Abrir TV en vivo"
                        EmptyHint(
                            title = emptyTitle,
                            body = emptyBody,
                            action = emptyAction,
                            onAction = if (items.isNotEmpty()) onManageChannels else onOpenFull,
                        )
                    }
                }
                gridItems(visible, key = { it.code }) { item ->
                    ChannelCard(
                        item = item,
                        height = metrics.channelHeight,
                        nowMs = nowMs,
                        showEpg = config.display.showEpg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (item.code == firstCode) Modifier.focusRequester(entry) else Modifier),
                        onLongClick = { onLongClick(item) },
                        onClick = { onOpen(item, visible, liveScopeLabel(filter)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.pill,
        modifier = modifier.height(34.dp),
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
    upTarget: FocusRequester? = null,
    landing: FocusRequester? = null,
) {
    val nowMs = rememberNowMs()
    val today = todayIsoMx(nowMs)
    val days = remember(events, today) { eventDays(events, today) }
    var day by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var league by remember { mutableStateOf<String?>(null) }

    val categories = remember(events) { eventCategories(events) }
    val leagues = remember(events, day, category) { eventLeagues(events, day, category) }
    LaunchedEffect(days) {
        if (day.isBlank() || days.none { it.first == day }) {
            val liveToday = events.any {
                (it.dateIso.ifBlank { today } == today) && sportsPhase(it, nowMs) == SportsPhase.Live
            }
            day = if (liveToday) today else days.firstOrNull()?.first ?: today
        }
    }
    LaunchedEffect(categories) {
        if (category != null && category !in categories) category = null
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
    val phased = remember(visible, nowMs) { visible.map { it to sportsPhase(it, nowMs) } }
    val live = remember(phased) { phased.filter { it.second == SportsPhase.Live }.map { it.first } }
    val soon = remember(phased) { phased.filter { it.second == SportsPhase.Soon }.map { it.first } }
    val done = remember(phased) { phased.filter { it.second == SportsPhase.Done }.map { it.first } }
    val entry = remember { FocusRequester() }
    val firstId = live.firstOrNull()?.id ?: soon.firstOrNull()?.id ?: done.firstOrNull()?.id

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        // Encabezado y filtros FUERA de la rejilla: así no se superponen al hacer scroll.
        Column(Modifier.padding(horizontal = metrics.horizontalPadding)) {
            SectionHeader(
                title = "Deportes en vivo",
                subtitle = if (events.isEmpty()) {
                    "La agenda de Vision+ aparecerá aquí"
                } else {
                    "${visible.size} eventos · ${live.size} en vivo"
                },
                actionLabel = "Abrir en Vision+",
                actionModifier = upTarget?.let { Modifier.focusProperties { up = it } } ?: Modifier,
                onAction = onOpenFull,
            )
            Spacer(Modifier.height(10.dp))
            FilterRow(
                allLabel = "Todos los deportes",
                options = categories.map { it to sportLabel(it) },
                selected = category,
                onSelect = { category = it },
                landing = landing,
                upTarget = upTarget,
            )
            Spacer(Modifier.height(8.dp))
            FilterRow(
                allLabel = null,
                options = days,
                selected = day,
                onSelect = { day = it ?: today },
                downTarget = if (leagues.isEmpty()) entry else null,
            )
            if (leagues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FilterRow(
                    allLabel = "Todas las ligas",
                    options = leagues.map { it to it },
                    selected = league,
                    onSelect = { league = it },
                    downTarget = entry,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val columns = when {
                    maxWidth >= 1200.dp -> 3
                    maxWidth >= 860.dp -> 2
                    else -> 1
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize().focusGroup(),
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
                    } else {
                        if (live.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SportsGroupHeader(SportsPhase.Live, live.size, metrics)
                            }
                            gridItems(live, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    phase = SportsPhase.Live,
                                    modifier = if (event.id == firstId) Modifier.focusRequester(entry) else Modifier,
                                    onClick = { onOpen(event) },
                                )
                            }
                        }
                        if (soon.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SportsGroupHeader(SportsPhase.Soon, soon.size, metrics)
                            }
                            gridItems(soon, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    phase = SportsPhase.Soon,
                                    modifier = if (event.id == firstId) Modifier.focusRequester(entry) else Modifier,
                                    onClick = { onOpen(event) },
                                )
                            }
                        }
                        if (done.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SportsGroupHeader(SportsPhase.Done, done.size, metrics)
                            }
                            gridItems(done, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    phase = SportsPhase.Done,
                                    modifier = if (event.id == firstId) Modifier.focusRequester(entry) else Modifier,
                                    onClick = { onOpen(event) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsGroupHeader(phase: SportsPhase, count: Int, metrics: HomeMetrics) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (phase == SportsPhase.Live) 4.dp else 8.dp, bottom = 2.dp),
    ) {
        Text(
            sportsPhaseTitle(phase),
            color = if (phase == SportsPhase.Live) HomeColors.Live else HomeColors.Text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            sportsPhaseSubtitle(phase, count),
            color = HomeColors.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Una fila de chips deslizable (deporte / día / liga). */
@Composable
private fun FilterRow(
    allLabel: String?,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    landing: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    upTarget: FocusRequester? = null,
) {
    var first = true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .focusGroup()
            .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allLabel != null) {
            FilterPill(
                label = allLabel,
                selected = selected == null,
                modifier = chipModifier(first, landing, downTarget, upTarget),
            ) { onSelect(null) }
            first = false
        }
        options.forEach { option ->
            FilterPill(
                label = option.second,
                selected = selected == option.first,
                modifier = chipModifier(first, landing, downTarget, upTarget),
            ) {
                onSelect(if (selected == option.first && allLabel != null) null else option.first)
            }
            first = false
        }
    }
}

private fun chipModifier(
    first: Boolean,
    landing: FocusRequester?,
    downTarget: FocusRequester?,
    upTarget: FocusRequester?,
): Modifier {
    var modifier: Modifier = Modifier
    if (first && landing != null) modifier = modifier.focusRequester(landing)
    if (first && upTarget != null) modifier = modifier.focusProperties { up = upTarget }
    if (downTarget != null) modifier = modifier.softFocusDown(downTarget)
    return modifier
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.pill,
        modifier = modifier.height(34.dp),
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

/** Los días de la agenda (iso → etiqueta), del más cercano al más lejano. «Hoy» siempre está. */
internal fun eventDays(events: List<SportsItem>, todayIso: String): List<Pair<String, String>> {
    val dates = events.asSequence()
        .map { it.dateIso }
        .filter { it.isNotBlank() && it >= todayIso }
        .distinct()
        .toMutableSet()
    dates.add(todayIso)
    return dates.sorted().map { it to dayLabelMx(it, todayIso) }
}

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
private fun EventCard(
    event: SportsItem,
    phase: SportsPhase,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        shape = HomeShapes.card,
        modifier = modifier.fillMaxWidth(),
    ) {
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
                if (phase == SportsPhase.Live) {
                    Badge(text = "EN VIVO", dot = true, color = HomeColors.Live)
                } else {
                    Text(
                        sportsDetailLabel(event, phase),
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
                        color = if (phase == SportsPhase.Live) HomeColors.Text else HomeColors.BlueBright,
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
                    sportsFooterLabel(event, phase),
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
    landing: FocusRequester? = null,
) {
    val items = catalogItems(feed, tab)
    val firstRef = items.firstOrNull()?.ref
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
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
                modifier = Modifier.fillMaxSize().focusGroup(),
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
                    Column(Modifier.fillMaxWidth()) {
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
                        modifier = if (item.ref == firstRef && landing != null) {
                            Modifier.focusRequester(landing)
                        } else {
                            Modifier
                        },
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

/** La pestaña Apps: todas las apps instaladas y los accesos del sistema. */
@Composable
fun AppsSurface(
    apps: List<AppEntry>,
    recentApps: List<String>,
    config: HomeConfig,
    metrics: HomeMetrics,
    onOpen: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    onSystemSettings: () -> Unit,
    onWifi: () -> Unit,
    onHomeSettings: () -> Unit,
    onManage: () -> Unit,
    landing: FocusRequester? = null,
    upTarget: FocusRequester? = null,
) {
    val visible = remember(apps, config, recentApps) { visibleApps(apps, config, recentApps) }
    val firstPackage = visible.firstOrNull()?.packageName
    val hidden = config.hiddenApps.size
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth >= 1200.dp -> 8
                maxWidth >= 960.dp -> 7
                maxWidth >= 720.dp -> 6
                else -> 5
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().focusGroup(),
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
                    Column(Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = "Apps",
                            subtitle = buildString {
                                append("${visible.size} visibles")
                                if (hidden > 0) append(" · $hidden ocultas")
                                append(" · mantén OK para opciones")
                            },
                            actionLabel = "Gestionar",
                            actionModifier = upTarget?.let { Modifier.focusProperties { up = it } } ?: Modifier,
                            onAction = onManage,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHint(
                            title = "Todas las apps están ocultas",
                            body = "Puedes volver a mostrar las apps que quitaste desde Gestionar.",
                            action = "Gestionar apps",
                            onAction = onManage,
                        )
                    }
                }
                gridItems(visible, key = { it.packageName }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppTile(
                            app = app,
                            tile = metrics.appTile,
                            modifier = Modifier
                                .then(if (app.packageName == firstPackage && landing != null) Modifier.focusRequester(landing) else Modifier)
                                .then(if (app.packageName == firstPackage && upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier),
                            onClick = { onOpen(app) },
                            onLongClick = { onLongClick(app) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(10.dp))
                        SectionHeader(title = "Sistema")
                        Spacer(Modifier.height(2.dp))
                    }
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
