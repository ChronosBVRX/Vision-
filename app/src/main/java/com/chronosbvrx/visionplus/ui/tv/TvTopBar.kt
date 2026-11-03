package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.VisionClock
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/** Las pestañas del shell de la tele (el mismo lenguaje que Vision+ Home, con las de la app). */
internal enum class TvTopTab(val label: String, val description: String, val target: Route) {
    HOME("Inicio", "Inicio", Route.Home),
    LIVE("TV en vivo", "En vivo", Route.Live),
    SPORTS("Deportes", "Deportes en vivo", Route.Sports),
    GAMES("Juegos", "Vision+ Play", Route.Games),
    MOVIES("Películas", "Películas", Route.Section("movies", "Películas")),
    SERIES("Series", "Series", Route.Section("series", "Series")),
    LIBRARY("Mi lista", "Mi lista", Route.Library),
}

/** La barra superior se pinta en estas rutas (las del launcher) y no en fichas/reproductores. */
internal fun tvTopBarVisible(route: Route): Boolean =
    route == Route.Home || route == Route.Live || route == Route.Sports ||
        // Vision+ Play tiene su propio plató a pantalla completa: sin barra del shell.
        route is Route.Section || route == Route.Library ||
        route == Route.Categories || route is Route.Category ||
        // Ajustes y Buscar son parte del mismo shell: si no, parecen otra app.
        route == Route.Settings || route == Route.Search

/** Qué pestaña se pinta activa para la ruta actual (null = ninguna). */
internal fun tvTopTabFor(route: Route): TvTopTab? = when (route) {
    Route.Home -> TvTopTab.HOME
    Route.Live -> TvTopTab.LIVE
    Route.Sports -> TvTopTab.SPORTS
    Route.Games -> TvTopTab.GAMES
    Route.Library -> TvTopTab.LIBRARY
    Route.Downloads -> TvTopTab.LIBRARY
    is Route.Section -> when (route.name) {
        "movies" -> TvTopTab.MOVIES
        "series", "anime", "novelas" -> TvTopTab.SERIES
        else -> null
    }
    else -> null
}

/**
 * Lo que se pinta en la barra: la mayoría son pestañas directas, pero **TV en vivo** y
 * **Deportes** comparten un solo destino «En vivo» con un menú desplegable. Así la barra no
 * pierde ancho y las dos secciones siguen a un OK de distancia.
 */
internal sealed interface TvTopEntry {
    data class Tab(val tab: TvTopTab) : TvTopEntry
    data object LiveHub : TvTopEntry
    data object SeriesHub : TvTopEntry
    data object LibraryHub : TvTopEntry
}

internal val TV_TOP_ENTRIES: List<TvTopEntry> = listOf(
    TvTopEntry.Tab(TvTopTab.HOME),
    TvTopEntry.LiveHub,
    TvTopEntry.Tab(TvTopTab.GAMES),
    TvTopEntry.Tab(TvTopTab.MOVIES),
    TvTopEntry.SeriesHub,
    TvTopEntry.LibraryHub,
)

internal fun liveHubSelected(active: TvTopTab?): Boolean =
    active == TvTopTab.LIVE || active == TvTopTab.SPORTS

/**
 * La barra superior de la tele: marca, pestañas, hora de CDMX, perfil, buscador y ajustes.
 * Es la misma idea que la de Vision+ Home, con las secciones de la app (sin Apps ni sistema).
 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
internal fun TvTopBar(
    active: TvTopTab?,
    onTab: (TvTopTab) -> Unit,
    onRoute: (Route) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit = {},
    /** Campana y recarga dentro de la barra (antes flotaban sobre los pósters). */
    updatesBadge: Int = 0,
    onNotifications: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    /**
     * Destino del Atrás en dos pasos: el primer Atrás desde el contenido sube el foco aquí, a
     * la pestaña de la sección actual (o a la primera si la barra no tiene pestaña activa).
     */
    landing: LandingFocus? = null,
    /** Avisa si el mando está dentro de la barra (así el Atrás de ahí ya sí sale). */
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profile = remember(context) {
        runCatching { ProfileStore.active(context).name }.getOrDefault("Principal")
    }
    val clock = rememberTopClock()
    var liveMenuOpen by remember { mutableStateOf(false) }
    var seriesMenuOpen by remember { mutableStateOf(false) }
    var libraryMenuOpen by remember { mutableStateOf(false) }
    val liveHubFocus = remember { FocusRequester() }
    val seriesHubFocus = remember { FocusRequester() }
    val libraryHubFocus = remember { FocusRequester() }
    // Al cambiar de sección el menú no puede quedarse abierto encima de la pantalla nueva.
    LaunchedEffect(active) {
        if (active != TvTopTab.LIVE && active != TvTopTab.SPORTS) liveMenuOpen = false
        if (active != TvTopTab.SERIES) seriesMenuOpen = false
        if (active != TvTopTab.LIBRARY && active != null) libraryMenuOpen = false
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            // Grupo de foco: `hasFocus` es true mientras cualquier control de la barra tenga el
            // foco. Lo usa el Atrás de TvApp para saber si ya está «arriba» y puede salir.
            .focusGroup()
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Marca
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Vision",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp,
            )
            Text("+", color = VisionBlueBright, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                "TV",
                color = VisionTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
            )
        }
        Spacer(Modifier.width(20.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(TV_TOP_ENTRIES) { entry ->
                val entryActive = when (entry) {
                    is TvTopEntry.Tab -> entry.tab == active
                    TvTopEntry.LiveHub -> liveHubSelected(active)
                    TvTopEntry.SeriesHub -> active == TvTopTab.SERIES
                    TvTopEntry.LibraryHub -> active == TvTopTab.LIBRARY
                }
                // Dónde aterriza el Atrás: la pestaña de la sección actual. En las pantallas sin
                // pestaña propia (Ajustes, Buscar, Categorías) la primera, que siempre existe.
                val lands = landing != null &&
                    (if (active == null) entry == TV_TOP_ENTRIES.first() else entryActive)
                when (entry) {
                    is TvTopEntry.Tab -> TopTabPill(
                        label = entry.tab.label,
                        description = entry.tab.description,
                        selected = entry.tab == active,
                        onClick = { onTab(entry.tab) },
                        modifier = if (lands) Modifier.landingFocus(landing!!) else Modifier,
                    )
                    TvTopEntry.LiveHub -> Box {
                        TopTabPill(
                            label = "En vivo",
                            description = "TV en vivo y Deportes",
                            selected = liveHubSelected(active),
                            onClick = { liveMenuOpen = true },
                            modifier = Modifier
                                .focusRequester(liveHubFocus)
                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                        )
                        if (liveMenuOpen) {
                            val density = LocalDensity.current
                            val below = with(density) { 46.dp.roundToPx() }
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = IntOffset(0, below),
                                onDismissRequest = {
                                    liveMenuOpen = false
                                    runCatching { liveHubFocus.requestFocus() }
                                },
                                properties = PopupProperties(
                                    focusable = true,
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = true,
                                ),
                            ) {
                                LiveHubPanel(
                                    active = active,
                                    onPick = { tab ->
                                        liveMenuOpen = false
                                        onTab(tab)
                                    },
                                    onDismiss = { liveMenuOpen = false },
                                )
                            }
                        }
                    }
                    TvTopEntry.SeriesHub -> Box {
                        TopTabPill(
                            label = "Series",
                            description = "Series, anime y novelas",
                            selected = active == TvTopTab.SERIES,
                            onClick = { seriesMenuOpen = true },
                            modifier = Modifier
                                .focusRequester(seriesHubFocus)
                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                        )
                        if (seriesMenuOpen) {
                            val density = LocalDensity.current
                            val below = with(density) { 46.dp.roundToPx() }
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = IntOffset(0, below),
                                onDismissRequest = { seriesMenuOpen = false },
                                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
                            ) {
                                SeriesHubPanel(
                                    active = active,
                                    onPick = { route -> seriesMenuOpen = false; onRoute(route) },
                                    onDismiss = { seriesMenuOpen = false },
                                )
                            }
                        }
                    }
                    TvTopEntry.LibraryHub -> Box {
                        TopTabPill(
                            label = "Mi lista",
                            description = "Mi lista y descargas",
                            selected = active == TvTopTab.LIBRARY,
                            onClick = { libraryMenuOpen = true },
                            modifier = Modifier
                                .focusRequester(libraryHubFocus)
                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                        )
                        if (libraryMenuOpen) {
                            val density = LocalDensity.current
                            val below = with(density) { 46.dp.roundToPx() }
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = IntOffset(0, below),
                                onDismissRequest = { libraryMenuOpen = false },
                                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
                            ) {
                                LibraryHubPanel(
                                    active = active,
                                    onPick = { route -> libraryMenuOpen = false; onRoute(route) },
                                    onDismiss = { libraryMenuOpen = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            clock,
            color = VisionTextSecondary,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.width(8.dp))
        if (onNotifications != null) {
            TopBarIcon(
                icon = Icons.Default.Notifications,
                label = "Notificaciones",
                onClick = onNotifications,
                badge = updatesBadge,
            )
            Spacer(Modifier.width(6.dp))
        }
        if (onReload != null) {
            TopBarIcon(Icons.Default.Refresh, "Recargar", onReload)
            Spacer(Modifier.width(8.dp))
        }
        ProfileChip(profile, onProfile)
        Spacer(Modifier.width(6.dp))
        TvSearchPill(onSearch)
        Spacer(Modifier.width(4.dp))
        TopBarIcon(Icons.Default.Settings, "Ajustes", onSettings)
    }
}

@Composable
private fun TopTabPill(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused && selected -> VisionBlue.copy(alpha = 0.32f)
        focused -> Color.White.copy(alpha = 0.14f)
        selected -> VisionBlue.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(
                if (focused) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected || focused) Color.White else VisionTextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SeriesHubPanel(
    active: TvTopTab?,
    onPick: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    Column(
        Modifier.width(246.dp).dismissOnBack(onDismiss)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF20B1220))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RouteHubOption("Series", "Series de televisión", Icons.Default.Tv, active == TvTopTab.SERIES, first) { onPick(Route.Section("series", "Series")) }
        RouteHubOption("Anime", "Animación japonesa", Icons.Default.Animation, false) { onPick(Route.Section("anime", "Anime")) }
        RouteHubOption("Novelas", "Telenovelas y dramas", Icons.Default.AutoStories, false) { onPick(Route.Section("novelas", "Novelas")) }
    }
}

@Composable
private fun LibraryHubPanel(
    active: TvTopTab?,
    onPick: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    Column(
        Modifier.width(246.dp).dismissOnBack(onDismiss)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF20B1220))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RouteHubOption("Mi lista", "Favoritos y ver más tarde", Icons.Default.Bookmark, active == TvTopTab.LIBRARY, first) { onPick(Route.Library) }
        RouteHubOption("Descargas", "Contenido sin conexión", Icons.Default.Download, false) { onPick(Route.Downloads) }
    }
}

@Composable
private fun RouteHubOption(
    label: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) VisionBlue.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = VisionBlue.copy(alpha = 0.42f),
            focusedContentColor = Color.White,
            pressedContainerColor = VisionBlue.copy(alpha = 0.58f),
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.dp, Color.White), shape = RoundedCornerShape(10.dp)),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Icon(icon, contentDescription = null, tint = if (focused || selected) Color.White else VisionTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = VisionTextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun LiveHubPanel(
    active: TvTopTab?,
    onPick: (TvTopTab) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstOption = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        repeat(4) {
            if (runCatching { firstOption.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
    Column(
        Modifier
            .width(246.dp)
            .dismissOnBack(onDismiss)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF20B1220))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LiveHubOption(
            label = "TV en vivo",
            detail = "Canales y guía",
            icon = Icons.Default.LiveTv,
            selected = active == TvTopTab.LIVE,
            focusRequester = firstOption,
            onClick = { onPick(TvTopTab.LIVE) },
        )
        LiveHubOption(
            label = "Deportes en vivo",
            detail = "Agenda y partidos",
            icon = Icons.Default.SportsSoccer,
            selected = active == TvTopTab.SPORTS,
            onClick = { onPick(TvTopTab.SPORTS) },
        )
    }
}

@Composable
private fun LiveHubOption(
    label: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) VisionBlue.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = VisionBlue.copy(alpha = 0.42f),
            focusedContentColor = Color.White,
            pressedContainerColor = VisionBlue.copy(alpha = 0.58f),
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.dp, Color.White), shape = RoundedCornerShape(10.dp)),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected || focused) Color.White else VisionTextSecondary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(detail, color = VisionTextSecondary, fontSize = 11.sp, maxLines = 1)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Sección actual", tint = VisionBlueBright, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProfileChip(name: String, onClick: () -> Unit) {
    val label = name.trim().ifBlank { "Principal" }
    // El chip abre «Perfiles» (como en Netflix): elegir quién eres desde cualquier pantalla.
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = VisionTextSecondary,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ProfileAvatar(name = label, size = 22.dp)
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvSearchPill(onSearch: () -> Unit) {
    // Píldora «Buscar» en vez de una lupa pelada: es la acción principal de la barra.
    Surface(
        onClick = onSearch,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Text("Buscar", color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, Color.White, RoundedCornerShape(50)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (focused) Color.White else VisionTextSecondary
        if (badge > 0) {
            BadgedBox(
                badge = {
                    Badge(containerColor = VisionBlue, contentColor = Color.White) {
                        Text("$badge", fontSize = 9.sp)
                    }
                },
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** La hora del centro de México, despertando justo al cambiar el minuto. */
@Composable
private fun rememberTopClock(): String {
    var text by remember { mutableStateOf(VisionClock.timeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(VisionClock.msToNextMinute() + 50L)
            text = VisionClock.timeText()
        }
    }
    return text
}
