package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.launch

/** Las pestañas del shell de la tele (el mismo lenguaje que Vision+ Home, con las de la app). */
internal enum class TvTopTab(val label: String, val description: String, val target: Route) {
    HOME("Inicio", "Inicio", Route.Home),
    LIVE("TV en vivo", "En vivo", Route.Live),
    SPORTS("Deportes", "Deportes en vivo", Route.Sports),
    GAMES("Juegos", "Vision+ Party", Route.Games),
    MOVIES("Películas", "Películas", Route.Section("movies", "Películas")),
    SERIES("Series", "Series", Route.Section("series", "Series")),
    LIBRARY("Mi lista", "Mi lista", Route.Library),
}

/** La barra superior se pinta en estas rutas (las del launcher) y no en fichas/reproductores. */
internal fun tvTopBarVisible(route: Route): Boolean =
    route == Route.Home || route == Route.Live || route == Route.Sports ||
        // Vision+ Party tiene su propio plató a pantalla completa: sin barra del shell.
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
    is Route.Category -> when (route.key) {
        "kids-movies" -> TvTopTab.MOVIES
        "kids-series" -> TvTopTab.SERIES
        else -> null
    }
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
    data object MoviesHub : TvTopEntry
    data object SeriesHub : TvTopEntry
    data object LibraryHub : TvTopEntry
}

internal val TV_TOP_ENTRIES: List<TvTopEntry> = listOf(
    TvTopEntry.Tab(TvTopTab.HOME),
    TvTopEntry.LiveHub,
    TvTopEntry.Tab(TvTopTab.GAMES),
    TvTopEntry.MoviesHub,
    TvTopEntry.SeriesHub,
    TvTopEntry.LibraryHub,
)

internal fun liveHubSelected(active: TvTopTab?): Boolean =
    active == TvTopTab.LIVE || active == TvTopTab.SPORTS

/** Los cuatro menús desplegables de la barra. Solo puede haber uno abierto. */
private enum class TvTopHub { LIVE, MOVIES, SERIES, LIBRARY }

/**
 * Ancla de un hub: guarda su `FocusRequester` y si de verdad tiene el foco. La restauración
 * verifica el aterrizaje (no basta con que `requestFocus` no lance) y reintenta mientras el
 * popup termina de cerrarse.
 */
private class HubAnchor {
    val requester = FocusRequester()
    var focused by mutableStateOf(false)
}

/**
 * La barra superior de la tele: marca, pestañas, hora de CDMX, perfil, buscador y ajustes.
 * Es la misma idea que la de Vision+ Home, con las secciones de la app (sin Apps ni sistema).
 *
 * La variante sale de [tvTopBarPlan]: Full conserva reloj, nombre de perfil y etiquetas;
 * Compact/Narrow los colapsan (avatar e iconos) para que las 6 pestañas conserven su ancho.
 * Nunca se ocultan pestañas primarias en 960 dp: la tira se desplaza con auto-scroll a la activa.
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
    /** Perfil activo que pinta el chip; si no se pasa, la barra lo lee del almacén. */
    profileName: String? = null,
    profileAvatar: String = "",
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
    val storedProfile = remember(context) {
        runCatching { ProfileStore.active(context) }.getOrNull()
    }
    val profile = profileName ?: storedProfile?.name ?: "Principal"
    val avatar = if (profileName != null) profileAvatar else storedProfile?.avatar.orEmpty()
    val clock = rememberTopClock()
    val scope = rememberCoroutineScope()
    var openHub by remember { mutableStateOf<TvTopHub?>(null) }
    var barFocused by remember { mutableStateOf(false) }
    val tabListState = rememberLazyListState()
    val anyHubOpen = openHub != null
    val activeIndex = remember(active) { tvTopEntryIndex(active) }

    // Un hub es una ventana de foco propia: mientras vive, el rescate de la pantalla de fondo
    // no debe pelear por el foco. El contador se libera al desmontar el popup.
    DisposableEffect(anyHubOpen) {
        if (anyHubOpen) FocusRescue.dialogsOpen++
        onDispose { if (anyHubOpen) FocusRescue.dialogsOpen-- }
    }

    // Al cambiar de sección el menú no puede quedarse abierto encima de la pantalla nueva.
    LaunchedEffect(active) {
        val hub = openHub ?: return@LaunchedEffect
        val stillOpen = when (hub) {
            TvTopHub.LIVE -> liveHubSelected(active)
            TvTopHub.MOVIES -> active == TvTopTab.MOVIES
            TvTopHub.SERIES -> active == TvTopTab.SERIES
            TvTopHub.LIBRARY -> active == TvTopTab.LIBRARY
        }
        if (!stillOpen) openHub = null
    }

    /**
     * Cierra un hub y devuelve el foco a su píldora. No basta con `requestFocus`: el popup
     * tarda unos cuadros en soltar su ventana, así que se verifica el aterrizaje y se reintenta.
     */
    fun dismissHub(anchor: HubAnchor, useLanding: Boolean, close: () -> Unit) {
        close()
        scope.launch {
            repeat(20) {
                val landingFocused = useLanding && landing?.focused == true
                if (anchor.focused || landingFocused) return@launch
                runCatching { anchor.requester.requestFocus() }
                if (useLanding) landing?.request()
                delay(50)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            // Grupo de foco: `hasFocus` es true mientras cualquier control de la barra tenga el
            // foco. Lo usa el Atrás de TvApp para saber si ya está «arriba» y puede salir.
            .focusGroup()
            .onFocusChanged {
                barFocused = it.hasFocus
                onFocusChanged(it.hasFocus)
            },
    ) {
        val systemFontScale = LocalDensity.current.fontScale
        val plan = remember(maxWidth, systemFontScale, profile.trim().length, updatesBadge > 0) {
            tvTopBarPlan(
                widthDp = maxWidth.value,
                fontScale = systemFontScale,
                profileNameLength = profile.trim().length,
                hasBadge = updatesBadge > 0,
            )
        }
        val baseDensity = LocalDensity.current
        val barDensity = remember(baseDensity.density, plan.fontScale) {
            Density(baseDensity.density, plan.fontScale)
        }

        // La LazyRow solo compone lo visible. Cuando el mando está en el contenido, la tira
        // vuelve a la pestaña activa para que el Atrás siempre encuentre compuesto su destino.
        // Con un hub abierto no se mueve: el popup está anclado a esa píldora.
        LaunchedEffect(activeIndex, barFocused, plan) {
            // `anyHubOpen` se consulta pero no es clave: al cerrar un hub no queremos mover
            // la tira antes de que termine la restauración de foco (bailaría la píldora).
            if (!barFocused && !anyHubOpen) {
                runCatching { tabListState.scrollToItem(activeIndex) }
            }
        }

        CompositionLocalProvider(
            LocalTvTopBarPlan provides plan,
            LocalDensity provides barDensity,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(plan.height)
                    .padding(horizontal = plan.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Marca: «TV» es decorativo y se cae primero en Compact/Narrow.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vision",
                        color = Color.White,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                    )
                    Text(
                        "+",
                        color = VisionBlueBright,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    if (plan.brand == TvTopBarBrandMode.FULL) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "TV",
                            color = VisionTextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.2.sp,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(if (plan.brand == TvTopBarBrandMode.FULL) 20.dp else 16.dp))

                Box(Modifier.weight(1f)) {
                    LazyRow(
                        state = tabListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(plan.tabSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(TV_TOP_ENTRIES, key = { tvTopEntryKey(it) }) { entry ->
                            val entryActive = tvTopEntryActive(entry, active)
                            // Dónde aterriza el Atrás: la pestaña de la sección actual. En las
                            // pantallas sin pestaña propia (Ajustes, Buscar, Categorías) la primera.
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

                                TvTopEntry.MoviesHub -> {
                                    val anchor = remember(entry) { HubAnchor() }
                                    Box {
                                        TopTabPill(
                                            label = "Películas",
                                            description = "Películas e infantil",
                                            selected = active == TvTopTab.MOVIES,
                                            onClick = { openHub = TvTopHub.MOVIES },
                                            modifier = Modifier
                                                .focusRequester(anchor.requester)
                                                .onFocusChanged { anchor.focused = it.isFocused }
                                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                                        )
                                        if (openHub == TvTopHub.MOVIES) {
                                            val density = LocalDensity.current
                                            val below = with(density) { (plan.controlHeight + 6.dp).roundToPx() }
                                            Popup(
                                                alignment = Alignment.TopStart,
                                                offset = IntOffset(0, below),
                                                onDismissRequest = {
                                                    dismissHub(anchor, lands) { openHub = null }
                                                },
                                                properties = PopupProperties(
                                                    focusable = true,
                                                    dismissOnBackPress = true,
                                                    dismissOnClickOutside = true,
                                                ),
                                            ) {
                                                MoviesHubPanel(
                                                    active = active,
                                                    onPick = { route ->
                                                        openHub = null
                                                        onRoute(route)
                                                    },
                                                    onDismiss = {
                                                        dismissHub(anchor, lands) { openHub = null }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                TvTopEntry.LiveHub -> {
                                    val anchor = remember(entry) { HubAnchor() }
                                    Box {
                                        TopTabPill(
                                            label = "En vivo",
                                            description = "TV en vivo y Deportes",
                                            selected = liveHubSelected(active),
                                            onClick = { openHub = TvTopHub.LIVE },
                                            modifier = Modifier
                                                .focusRequester(anchor.requester)
                                                .onFocusChanged { anchor.focused = it.isFocused }
                                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                                        )
                                        if (openHub == TvTopHub.LIVE) {
                                            val density = LocalDensity.current
                                            val below = with(density) { (plan.controlHeight + 6.dp).roundToPx() }
                                            Popup(
                                                alignment = Alignment.TopStart,
                                                offset = IntOffset(0, below),
                                                onDismissRequest = {
                                                    dismissHub(anchor, lands) { openHub = null }
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
                                                        openHub = null
                                                        onTab(tab)
                                                    },
                                                    onDismiss = {
                                                        dismissHub(anchor, lands) { openHub = null }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                TvTopEntry.SeriesHub -> {
                                    val anchor = remember(entry) { HubAnchor() }
                                    Box {
                                        TopTabPill(
                                            label = "Series",
                                            description = "Series, anime y novelas",
                                            selected = active == TvTopTab.SERIES,
                                            onClick = { openHub = TvTopHub.SERIES },
                                            modifier = Modifier
                                                .focusRequester(anchor.requester)
                                                .onFocusChanged { anchor.focused = it.isFocused }
                                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                                        )
                                        if (openHub == TvTopHub.SERIES) {
                                            val density = LocalDensity.current
                                            val below = with(density) { (plan.controlHeight + 6.dp).roundToPx() }
                                            Popup(
                                                alignment = Alignment.TopStart,
                                                offset = IntOffset(0, below),
                                                onDismissRequest = {
                                                    dismissHub(anchor, lands) { openHub = null }
                                                },
                                                properties = PopupProperties(
                                                    focusable = true,
                                                    dismissOnBackPress = true,
                                                    dismissOnClickOutside = true,
                                                ),
                                            ) {
                                                SeriesHubPanel(
                                                    active = active,
                                                    onPick = { route ->
                                                        openHub = null
                                                        onRoute(route)
                                                    },
                                                    onDismiss = {
                                                        dismissHub(anchor, lands) { openHub = null }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                TvTopEntry.LibraryHub -> {
                                    val anchor = remember(entry) { HubAnchor() }
                                    Box {
                                        TopTabPill(
                                            label = "Mi lista",
                                            description = "Mi lista y descargas",
                                            selected = active == TvTopTab.LIBRARY,
                                            onClick = { openHub = TvTopHub.LIBRARY },
                                            modifier = Modifier
                                                .focusRequester(anchor.requester)
                                                .onFocusChanged { anchor.focused = it.isFocused }
                                                .then(if (lands) Modifier.landingFocus(landing!!) else Modifier),
                                        )
                                        if (openHub == TvTopHub.LIBRARY) {
                                            val density = LocalDensity.current
                                            val below = with(density) { (plan.controlHeight + 6.dp).roundToPx() }
                                            Popup(
                                                alignment = Alignment.TopStart,
                                                offset = IntOffset(0, below),
                                                onDismissRequest = {
                                                    dismissHub(anchor, lands) { openHub = null }
                                                },
                                                properties = PopupProperties(
                                                    focusable = true,
                                                    dismissOnBackPress = true,
                                                    dismissOnClickOutside = true,
                                                ),
                                            ) {
                                                LibraryHubPanel(
                                                    active = active,
                                                    onPick = { route ->
                                                        openHub = null
                                                        onRoute(route)
                                                    },
                                                    onDismiss = {
                                                        dismissHub(anchor, lands) { openHub = null }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // El degradado solo aparece cuando de verdad hay más pestañas a ese lado.
                    val canScrollBack = tabListState.firstVisibleItemIndex > 0 ||
                        tabListState.firstVisibleItemScrollOffset > 0
                    val lastVisible = tabListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                        ?: TV_TOP_ENTRIES.lastIndex
                    val canScrollForward = lastVisible < TV_TOP_ENTRIES.lastIndex
                    if (canScrollBack) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent),
                                    ),
                                ),
                        )
                    }
                    if (canScrollForward) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                    ),
                                ),
                        )
                    }
                }

                if (plan.showClock) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        clock,
                        color = VisionTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(max = 72.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                    Spacer(Modifier.width(8.dp))
                }

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
                ProfileChip(profile, avatar, onProfile)
                Spacer(Modifier.width(6.dp))
                TvSearchPill(onSearch)
                Spacer(Modifier.width(4.dp))
                TopBarIcon(Icons.Default.Settings, "Ajustes", onSettings)
            }
        }
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
    val plan = LocalTvTopBarPlan.current
    val background = when {
        focused && selected -> VisionBlue.copy(alpha = 0.32f)
        focused -> Color.White.copy(alpha = 0.14f)
        selected -> VisionBlue.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .heightIn(min = plan.controlHeight)
            .background(background)
            .then(
                if (focused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
            .padding(horizontal = plan.tabPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected || focused) Color.White else VisionTextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoviesHubPanel(
    active: TvTopTab?,
    onPick: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogFocus()
    Column(
        Modifier.width(256.dp).modalFocusTrap().dismissOnBack(onDismiss)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF20B1220))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RouteHubOption("Películas", "Catálogo de películas", Icons.Default.Movie, active == TvTopTab.MOVIES, Modifier.dialogFocus(first)) { onPick(Route.Section("movies", "Películas")) }
        RouteHubOption("Películas Infantiles", "Películas para chicos y familia", Icons.Default.ChildCare, false) { onPick(Route.Category("kids-movies", "Películas Infantiles")) }
    }
}

@Composable
private fun SeriesHubPanel(
    active: TvTopTab?,
    onPick: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogFocus()
    Column(
        Modifier.width(256.dp).modalFocusTrap().dismissOnBack(onDismiss)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF20B1220))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RouteHubOption("Series", "Series de televisión", Icons.Default.Tv, active == TvTopTab.SERIES, Modifier.dialogFocus(first)) { onPick(Route.Section("series", "Series")) }
        RouteHubOption("Anime", "Animación japonesa", Icons.Default.Animation, false) { onPick(Route.Section("anime", "Anime")) }
        RouteHubOption("Novelas", "Telenovelas y dramas", Icons.Default.AutoStories, false) { onPick(Route.Section("novelas", "Novelas")) }
        RouteHubOption("Series Infantiles", "Dibujos animados y para chicos", Icons.Default.ChildCare, false) { onPick(Route.Category("kids-series", "Series Infantiles")) }
    }
}

@Composable
private fun LibraryHubPanel(
    active: TvTopTab?,
    onPick: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogFocus()
    Column(
        Modifier.width(246.dp).modalFocusTrap().dismissOnBack(onDismiss)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF20B1220))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RouteHubOption("Mi lista", "Favoritos y ver más tarde", Icons.Default.Bookmark, active == TvTopTab.LIBRARY, Modifier.dialogFocus(first)) { onPick(Route.Library) }
        RouteHubOption("Descargas", "Contenido sin conexión", Icons.Default.Download, false) { onPick(Route.Downloads) }
    }
}

@Composable
private fun RouteHubOption(
    label: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    focusModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .then(focusModifier)
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
                Text(
                    label,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = VisionTextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val firstOption = rememberDialogFocus()
    Column(
        Modifier
            .width(246.dp)
            .modalFocusTrap()
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
            focusModifier = Modifier.dialogFocus(firstOption),
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
    focusModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(focusModifier)
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
                Text(
                    label,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = VisionTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Sección actual", tint = VisionBlueBright, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProfileChip(name: String, avatar: String, onClick: () -> Unit) {
    val label = name.trim().ifBlank { "Principal" }
    val plan = LocalTvTopBarPlan.current
    val showName = plan.profile == TvTopBarProfileMode.NAME
    // El chip abre «Perfiles» (como en Netflix): elegir quién eres desde cualquier pantalla.
    // En Compact/Narrow es solo avatar: el nombre completo vive en Full y en el selector.
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = plan.controlHeight)
            .then(
                if (showName) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = "Perfil: $label" }
                },
            ),
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
            modifier = Modifier
                .heightIn(min = plan.controlHeight)
                .padding(start = 4.dp, end = if (showName) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (showName) 7.dp else 0.dp),
        ) {
            ProfileAvatar(name = label, size = if (showName) 22.dp else 26.dp, avatar = avatar)
            if (showName) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = plan.profileLabelMaxWidth),
                )
            }
        }
    }
}

@Composable
private fun TvSearchPill(onSearch: () -> Unit) {
    val plan = LocalTvTopBarPlan.current
    if (plan.search == TvTopBarSearchMode.ICON) {
        // En Compact/Narrow la lupa es la misma acción, sin etiqueta: gana ancho para las pestañas.
        TopBarIcon(Icons.Default.Search, "Buscar", onSearch)
        return
    }
    // Píldora «Buscar» en Full: la acción principal de la barra, con su etiqueta.
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
            modifier = Modifier
                .heightIn(min = plan.controlHeight)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Text("Buscar", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: Int = 0,
    size: Dp = LocalTvTopBarPlan.current.iconButtonSize,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
            .semantics { contentDescription = if (badge > 0) "$label, $badge" else label }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (focused) Color.White else VisionTextSecondary
        if (badge > 0) {
            BadgedBox(
                badge = {
                    Badge(containerColor = VisionBlue, contentColor = Color.White) {
                        Text(
                            if (badge > 9) "9+" else "$badge",
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                        )
                    }
                },
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
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
