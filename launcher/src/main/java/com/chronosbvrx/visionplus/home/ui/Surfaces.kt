package com.chronosbvrx.visionplus.home.ui

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
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.chronosbvrx.visionplus.home.data.AppEntry

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
    visionInstalled: Boolean,
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
        if (!visionInstalled) {
            item("missing") {
                MissingVisionBanner(
                    onRetry = onOpenVision,
                    onSettings = onSystemSettings,
                    modifier = Modifier.padding(horizontal = metrics.horizontalPadding),
                )
            }
        }
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

/** Aviso honesto cuando Vision+ no está instalado: el launcher sigue siendo útil. */
@Composable
private fun MissingVisionBanner(
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                "Instálalo para tener TV en vivo, películas y «Seguir viendo» en este Inicio. Las apps ya funcionan.",
                color = HomeColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HomeButton(label = "Reintentar", onClick = onRetry, icon = null)
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

/** La pestaña TV en vivo: la rejilla completa de canales del feed. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveSurface(
    items: List<LiveItem>,
    metrics: HomeMetrics,
    onOpen: (LiveItem) -> Unit,
    onOpenFull: () -> Unit,
    upTarget: FocusRequester? = null,
) {
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
                    subtitle = "${items.size} canales en tu lista · guía del centro de México",
                    actionLabel = "Abrir en Vision+",
                    actionModifier = upTarget?.let { Modifier.focusProperties { up = it } } ?: Modifier,
                    onAction = onOpenFull,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint(
                        title = "Aún no hay canales en el Inicio",
                        body = "Abre TV en vivo en Vision+ una vez: en cuanto cargue la lista, aparece aquí.",
                        action = "Abrir TV en vivo",
                        onAction = onOpenFull,
                    )
                }
            }
            gridItems(items, key = { it.code }) { item ->
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
