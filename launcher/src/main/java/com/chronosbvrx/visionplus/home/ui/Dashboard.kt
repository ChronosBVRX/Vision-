package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.home.HomeMetrics
import com.chronosbvrx.visionplus.home.R
import com.chronosbvrx.visionplus.home.contract.ContinueItem
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.HeroSlide
import com.chronosbvrx.visionplus.home.data.TvInputSource

/**
 * El fondo del escritorio. A diferencia del Inicio de Vision+ —que abre con arte a sangre—
 * aquí todo vive dentro de tarjetas: un pizarra casi negro, una retícula muy tenue y dos
 * luces de sistema. Es el marco que distingue «estoy en la tele» de «estoy en la app».
 */
@Composable
fun DashboardBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1019), Color(0xFF070A10), HomeColors.Black),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 72.dp.toPx()
            val line = Color.White.copy(alpha = 0.016f)
            var x = 0f
            while (x <= size.width) {
                drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                y += step
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(HomeColors.Cyan.copy(alpha = 0.055f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to HomeColors.Black.copy(alpha = 0.42f),
                    ),
                ),
        )
    }
}

/**
 * La tarjeta destacada del Bento. El hero conserva su carrusel, sus botones y su foco, pero
 * ahora está ENCERRADO: el arte no toca los bordes de la pantalla y el badge de acceso a la
 * app principal deja claro que esto es el escritorio, no el catálogo.
 */
@Composable
internal fun BentoHeroCard(
    hero: List<HeroSlide>,
    metrics: HomeMetrics,
    height: Dp,
    reduced: Boolean,
    landing: FocusRequester,
    upTarget: FocusRequester?,
    paused: Boolean,
    onPlay: (HeroSlide) -> Unit,
    onOpen: (HeroSlide) -> Unit,
    onOpenVision: () -> Unit,
    onPrimaryFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardMetrics = metrics.copy(
        heroHeight = height,
        horizontalPadding = 22.dp,
        titleSize = if (height < 280.dp) 25 else 30,
        synopsisSize = 11,
    )
    Box(
        modifier
            .fillMaxHeight()
            .clip(HomeShapes.panel)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0E2030), Color(0xFF091019), HomeColors.Surface),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), HomeShapes.panel),
    ) {
        if (hero.isEmpty()) {
            BentoBrandCard(
                metrics = cardMetrics,
                landing = landing,
                upTarget = upTarget,
                onOpenVision = onOpenVision,
                onPrimaryFocus = onPrimaryFocus,
            )
        } else {
            HeroStage(
                items = hero,
                metrics = cardMetrics,
                reduced = reduced,
                landing = landing,
                upTarget = upTarget,
                paused = paused,
                onPlay = onPlay,
                onOpen = onOpen,
                onControlFocus = onPrimaryFocus,
                showArt = true,
                forceCompact = true,
                eyebrowOverride = "AHORA EN VISION+",
                secondaryLabel = "Abrir catálogo Vision+",
                onSecondary = onOpenVision,
                modifier = Modifier.fillMaxSize(),
            )
            Badge(
                text = "ABRIR EN VISION+ TV",
                color = Color.Black.copy(alpha = 0.58f),
                textColor = HomeColors.Cyan,
                dot = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun BentoBrandCard(
    metrics: HomeMetrics,
    landing: FocusRequester,
    upTarget: FocusRequester?,
    onOpenVision: () -> Unit,
    onPrimaryFocus: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D2233), Color(0xFF091019), HomeColors.Surface),
                ),
            )
            .padding(horizontal = metrics.horizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Badge(text = "VISION+ HOME")
        Spacer(Modifier.height(10.dp))
        Text(
            "Tu tele,\nlista para empezar",
            color = Color.White,
            fontSize = (metrics.titleSize + 2).sp,
            lineHeight = (metrics.titleSize + 7).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "TV en vivo, tus apps y las entradas del televisor, en un solo escritorio.",
            color = HomeColors.TextDim,
            fontSize = metrics.synopsisSize.sp,
            lineHeight = (metrics.synopsisSize + 4).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeButton(
                label = "Abrir Vision+",
                onClick = onOpenVision,
                primary = true,
                onFocusChanged = onPrimaryFocus,
                modifier = Modifier
                    .focusRequester(landing)
                    .then(if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier),
            )
        }
    }
}

/** La puerta grande a la app principal: siempre la primera tarjeta de la columna de sistema. */
@Composable
internal fun VisionAccessCard(
    installed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = HomeShapes.panel,
        focusedScale = 1.025f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF123A5A),
                            Color(0xFF0B2035),
                            Color(0xFF08131F),
                        ),
                    ),
                )
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (installed) "APLICACIÓN PRINCIPAL" else "INSTALAR EN LA TELE",
                    color = HomeColors.Cyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Image(
                    painter = painterResource(R.drawable.ic_vision_wordmark),
                    contentDescription = "Vision+ TV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(28.dp).width(138.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (installed) {
                        "Ir a Películas, Series y TV en vivo"
                    } else {
                        "Se descarga del release oficial"
                    },
                    color = HomeColors.TextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HomeColors.Cyan.copy(alpha = if (focused) 0.30f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (focused) Color.White else HomeColors.Cyan,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

/**
 * La fila de apps del escritorio es para contenido y utilidades, no para las pantallas de
 * configuración del televisor ni las tiendas del sistema. La app principal tiene su tarjeta.
 */
internal fun dashboardAppAllowed(packageName: String): Boolean {
    val pkg = packageName.lowercase(java.util.Locale.ROOT)
    return pkg.isNotBlank() &&
        "settings" !in pkg &&
        "setupwraith" !in pkg &&
        "provision" !in pkg &&
        pkg != "com.google.android.play.games" &&
        pkg != "com.android.vending"
}

/** Los destinos reales de Vision+ a los que el launcher lleva directo. */
enum class VisionDestination {
    LIVE,
    MOVIES,
    SERIES,
    SPORTS,
    GAMES,
    LIBRARY,
    DOWNLOADS,
    ANIME,
    NOVELAS,
}

/**
 * La columna de accesos directos: cada baldosa abre UNA sección específica de Vision+.
 * No hay HDMI, sintonizador ni ajustes del televisor: el launcher es la puerta de la app.
 */
@Composable
internal fun VisionShortcutsCard(
    onOpen: (VisionDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortcuts = listOf(
        VisionShortcut("TV en vivo", Icons.Rounded.LiveTv, VisionDestination.LIVE),
        VisionShortcut("Películas", Icons.Rounded.Movie, VisionDestination.MOVIES),
        VisionShortcut("Series", Icons.Rounded.Theaters, VisionDestination.SERIES),
        VisionShortcut("Deportes", Icons.Rounded.SportsSoccer, VisionDestination.SPORTS),
        VisionShortcut("Juegos", Icons.Rounded.SportsEsports, VisionDestination.GAMES),
        VisionShortcut("Mi lista", Icons.Rounded.Bookmark, VisionDestination.LIBRARY),
        VisionShortcut("Descargas", Icons.Rounded.Download, VisionDestination.DOWNLOADS),
        VisionShortcut("Anime", Icons.Rounded.AutoAwesome, VisionDestination.ANIME),
        VisionShortcut("Novelas", Icons.Rounded.Tv, VisionDestination.NOVELAS),
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(HomeShapes.panel)
            .background(HomeColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, HomeColors.Stroke, HomeShapes.panel)
            .padding(11.dp),
    ) {
        Text(
            "IR A VISION+",
            color = HomeColors.Cyan,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(7.dp))
        shortcuts.chunked(3).forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { shortcut ->
                    VisionShortcutTile(
                        shortcut = shortcut,
                        onClick = { onOpen(shortcut.destination) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            if (index < 2) Spacer(Modifier.height(6.dp))
        }
    }
}

private data class VisionShortcut(
    val label: String,
    val icon: ImageVector,
    val destination: VisionDestination,
)

@Composable
private fun VisionShortcutTile(
    shortcut: VisionShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = HomeShapes.tile,
        focusedScale = 1.04f,
        showIdleBorder = false,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    if (focused) {
                        Brush.linearGradient(listOf(HomeColors.SurfaceHigh, HomeColors.Deep))
                    } else {
                        Brush.linearGradient(
                            listOf(HomeColors.SurfaceHigh.copy(alpha = 0.65f), HomeColors.Black.copy(alpha = 0.30f)),
                        )
                    },
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                shortcut.icon,
                contentDescription = null,
                tint = if (focused) Color.White else HomeColors.Cyan,
                modifier = Modifier.size(13.dp),
            )
            Text(
                shortcut.label,
                color = if (focused) Color.White else HomeColors.Text,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La fila de apps del sistema: Vision+ TV primero y después lo instalado, sin duplicarlo. */
@Composable
internal fun DashboardAppsSection(
    apps: List<AppEntry>,
    installed: Boolean,
    onVision: () -> Unit,
    onOpen: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    tileWidth: Dp,
    tileHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Mis aplicaciones",
            subtitle = "Mantén OK para opciones",
        )
        Spacer(Modifier.height(8.dp))
        CardFocusScope(Modifier.fillMaxWidth()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item("dashboard-vision") {
                    VisionAppTile(
                        installed = installed,
                        width = tileWidth + 22.dp,
                        height = tileHeight,
                        onClick = onVision,
                    )
                }
                items(apps, key = { it.packageName }) { app ->
                    DashboardAppTile(
                        app = app,
                        width = tileWidth,
                        height = tileHeight,
                        onClick = { onOpen(app) },
                        onLongClick = { onLongClick(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionAppTile(
    installed: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(width).height(height),
        shape = HomeShapes.tile,
        focusedScale = 1.07f,
        showIdleBorder = false,
    ) { focused ->
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (focused) HomeColors.Cyan.copy(alpha = 0.40f) else HomeColors.Blue.copy(alpha = 0.54f),
                            Color(0xFF172C64),
                            Color(0xFF0C1730),
                        ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_vision_wordmark),
                contentDescription = "Vision+ TV",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(22.dp).width(108.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (installed) "Abrir la app principal" else "Instalar Vision+ TV",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DashboardAppTile(
    app: AppEntry,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.width(width).height(height),
        shape = HomeShapes.tile,
        focusedScale = 1.07f,
        showIdleBorder = false,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (focused) {
                            listOf(HomeColors.SurfaceHigh, HomeColors.Deep)
                        } else {
                            listOf(HomeColors.SurfaceHigh.copy(alpha = 0.70f), HomeColors.Surface)
                        },
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = app.icon
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        app.label.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                app.label,
                color = if (focused) Color.White else HomeColors.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * «Seguir viendo» como widget compacto al lado de las apps. No repite la fila completa:
 * el historial largo vive en Vision+ y aquí solo está la última reproducción.
 */
@Composable
internal fun DashboardContinueWidget(
    item: ContinueItem,
    onOpen: (ContinueItem) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Reanudar reproducción",
            subtitle = "Donde te quedaste",
            actionLabel = "Historial",
            onAction = onOpenHistory,
        )
        Spacer(Modifier.height(8.dp))
        DashboardContinueCard(item = item, onClick = { onOpen(item) })
    }
}

@Composable
private fun DashboardContinueCard(
    item: ContinueItem,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(82.dp),
        shape = HomeShapes.tile,
        focusedScale = 1.025f,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(HomeColors.SurfaceHigh, HomeColors.Surface),
                    ),
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(102.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(HomeColors.Black),
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.60f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress.coerceIn(0, 100) / 100f)
                            .background(HomeColors.Cyan),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = HomeColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.episode.isNotBlank()) {
                    Text(
                        item.episode,
                        color = HomeColors.TextDim,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                val remaining = remainingMinutes(item)
                Text(
                    if (remaining > 0) "Quedan $remaining min" else "Continuar en el reproductor",
                    color = HomeColors.TextFaint,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * La franja inferior del Bento: apps a la izquierda y reanudar a la derecha, en una sola
 * pantalla. En pantallas angostas se apilan, pero una tele 1080p siempre entra completa.
 */
@Composable
internal fun DashboardAppsAndContinue(
    apps: List<AppEntry>,
    appsVisible: Boolean,
    installed: Boolean,
    continueItem: ContinueItem?,
    tileWidth: Dp,
    tileHeight: Dp,
    onVision: () -> Unit,
    onApp: (AppEntry) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showApps = appsVisible
    val showContinue = continueItem != null
    if (!showApps && !showContinue) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                if (showApps) {
                    DashboardAppsSection(
                        apps = apps,
                        installed = installed,
                        onVision = onVision,
                        onOpen = onApp,
                        onLongClick = onAppLongClick,
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                        modifier = Modifier.weight(if (showContinue) 0.64f else 1f),
                    )
                }
                if (showContinue) {
                    DashboardContinueWidget(
                        item = continueItem!!,
                        onOpen = onContinue,
                        onOpenHistory = onOpenHistory,
                        modifier = Modifier.weight(if (showApps) 0.36f else 1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showApps) {
                    DashboardAppsSection(
                        apps = apps,
                        installed = installed,
                        onVision = onVision,
                        onOpen = onApp,
                        onLongClick = onAppLongClick,
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                    )
                }
                if (continueItem != null) {
                    DashboardContinueWidget(
                        item = continueItem,
                        onOpen = onContinue,
                        onOpenHistory = onOpenHistory,
                    )
                }
            }
        }
    }
}

/** Minutos restantes redondeados; 0 si el feed no trae duración. */
internal fun remainingMinutes(item: ContinueItem): Int {
    if (item.durationMs <= 0L || item.positionMs < 0L) return 0
    val left = (item.durationMs - item.positionMs).coerceAtLeast(0L)
    return ((left + 59_999L) / 60_000L).toInt()
}

