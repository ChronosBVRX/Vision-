package com.chronosbvrx.visionplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.about.TvAboutScreen
import com.chronosbvrx.visionplus.ui.games.GamesComingSoonScreen
import com.chronosbvrx.visionplus.ui.player.LivePlayerScreen
import com.chronosbvrx.visionplus.ui.player.PlayerScreen
import com.chronosbvrx.visionplus.ui.tv.FocusRescue
import com.chronosbvrx.visionplus.ui.tv.LandingFocus
import com.chronosbvrx.visionplus.ui.tv.TvCategoriesScreen
import com.chronosbvrx.visionplus.ui.tv.TvHomeScreen
 
import com.chronosbvrx.visionplus.ui.tv.TvLiveScreen
import com.chronosbvrx.visionplus.ui.tv.TvRailItem
import com.chronosbvrx.visionplus.ui.tv.TvSearchScreen
import com.chronosbvrx.visionplus.ui.tv.TvSettingsScreen
import com.chronosbvrx.visionplus.ui.tv.TvSportsScreen
import com.chronosbvrx.visionplus.ui.tv.TvCategoryScreen
import com.chronosbvrx.visionplus.ui.tv.TvLibraryScreen
import com.chronosbvrx.visionplus.ui.tv.TvDownloadsScreen
import com.chronosbvrx.visionplus.ui.tv.TvAppUpdatesDialog
import com.chronosbvrx.visionplus.ui.tv.TvTitleScreen
import com.chronosbvrx.visionplus.ui.plugins.appUpdateBadgeCount
import com.chronosbvrx.visionplus.ui.tv.landingFocus
import com.chronosbvrx.visionplus.ui.kids.rememberLockedIndexRows
import com.chronosbvrx.visionplus.ui.tv.rememberLandingFocus

/** Un pilar del menú lateral de la TV. */
internal data class TvRailSpec(val label: String, val icon: ImageVector, val target: Route)

/**
 * Los pilares del menú lateral de la TV: **los mismos que el del teléfono** (Buscar, Inicio,
 * Películas, Series, En vivo, **Deportes en vivo**, Mi lista) para que los eventos deportivos
 * sean alcanzables desde cualquier pantalla de la tele, no solo por el acceso rápido del Inicio.
 */
internal val TV_RAIL_SPECS = listOf(
    TvRailSpec("Buscar", Icons.Default.Search, Route.Search),
    TvRailSpec("Inicio", Icons.Default.Home, Route.Home),
    TvRailSpec("Películas", Icons.Default.Movie, Route.Section("movies", "Películas")),
    TvRailSpec("Series", Icons.Default.Theaters, Route.Section("series", "Series")),
    TvRailSpec("En vivo", Icons.Default.LiveTv, Route.Live),
    TvRailSpec("Deportes en vivo", Icons.Default.SportsSoccer, Route.Sports),
    TvRailSpec("Juegos", Icons.Default.Casino, Route.Games),
    TvRailSpec("Mi lista", Icons.Default.Bookmark, Route.Library),
)

/** The TV's shell: Home with its rail, and every other place full screen with Back to return. */
@Composable
fun TvApp(
    catalog: XuperCatalog,
    nav: Navigator,
    onExitToLauncher: () -> Unit = {},
    /** Arma/confirma el cierre con aviso del Inicio (lo provee MainActivity). */
    onExitRequest: () -> Unit = {},
) {
    val route = nav.current
    LaunchedEffect(route) { com.chronosbvrx.visionplus.data.diag.Diagnostics.screen(route.toString()) }
    // Atrás en dos pasos: en las pantallas con barra superior (Inicio, Películas, Series, TV en
    // vivo, Mi lista…) el primer Atrás sube el foco a la selección de arriba en vez de salir; el
    // segundo, dentro de la ventana, sí sale (vuelve atrás o cierra la app en el Inicio). Si el
    // mando ya está en la barra, el Atrás sale de una vez, como siempre. La política pura vive
    // en `BackToTopGuard`.
    val backToTop = remember { com.chronosbvrx.visionplus.ui.BackToTopGuard() }
    val topBarLanding = remember { com.chronosbvrx.visionplus.ui.tv.LandingFocus() }
    var topBarFocused by remember { mutableStateOf(false) }
    val hasTopBar = com.chronosbvrx.visionplus.ui.tv.tvTopBarVisible(route)
    val atRoot = nav.stack.size <= 1
    // Cambiar de pantalla invalida el armado: el Atrás de la pantalla nueva empieza de cero.
    LaunchedEffect(route) { backToTop.disarm() }
    BackHandler(enabled = route !is Route.Player && route !is Route.LivePlayer) {
        val now = android.os.SystemClock.uptimeMillis()
        if (hasTopBar && !topBarFocused && backToTop.press(now)) {
            val composed = topBarLanding.attached
            topBarLanding.request()
            // Si la barra no está compuesta en este frame (Modo Sin Conexión, por ejemplo) la
            // tecla NO se come: sigue su curso normal.
            if (composed || topBarLanding.focused || topBarFocused) {
                // En el Inicio este mismo toque ya arma la salida: el siguiente Atrás cierra.
                if (atRoot) onExitRequest()
                return@BackHandler
            }
        }
        if (atRoot) {
            onExitRequest()
        } else {
            backToTop.disarm()
            nav.pop()
        }
    }
    // Each place keeps its saved state (scroll, the card Back returns to) while another one is on top.
    val saved = rememberSaveableStateHolder()
    // Al volver: el riel recuerda su último botón y las secciones, la última tarjeta abierta.
    var railReturn by rememberSaveable { mutableStateOf("") }
    var filmReturn by rememberSaveable { mutableStateOf<String?>(null) }
    // El regreso a la TV en vivo recuerda el último canal abierto (igual que las fichas).
    var liveReturn by rememberSaveable { mutableStateOf<String?>(null) }
    // Red definitiva contra el foco huérfano al volver: la pantalla recompuesta ya re-pide
    // su foco sola, pero si a los 300 ms el mando sigue sin nada enfocado, el rescate
    // verificado (solo destinos compuestos) lo devuelve. Solo actúa sin foco (nunca pelea
    // con el usuario).
    LaunchedEffect(nav.stack.size, route) {
        delay(300)
        repeat(10) {
            if (FocusRescue.screenHasFocus) return@LaunchedEffect
            FocusRescue.rescue()
            delay(100)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged { FocusRescue.screenHasFocus = it.hasFocus }
            // El regreso de una ficha distingue una tecla real del `screenHasFocus` viejo
            // que deja la pantalla anterior al cerrarse (ver `rememberReturnFocus`).
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) FocusRescue.noteUserKey()
                false
            },
    ) {
        saved.SaveableStateProvider(route.toString()) {
            TvRoute(
                catalog = catalog,
                nav = nav,
                route = route,
                railReturn = railReturn,
                onRailPick = { railReturn = it },
                filmReturn = filmReturn,
                onFilmPick = { filmReturn = it },
                // El regreso pendiente se invalida al salir del Inicio por un hub/otra sección
                // (el onDone del efecto de regreso queda apagado: ver nota en TvHomeScreen).
                onClearFilmReturn = { filmReturn = null },
                liveReturn = liveReturn,
                onChannelPick = { liveReturn = it },
                onExitToLauncher = onExitToLauncher,
                topBarLanding = topBarLanding,
                onTopBarFocusChanged = { topBarFocused = it },
            )
        }
    }
}

@Composable
private fun TvRoute(
    catalog: XuperCatalog,
    nav: Navigator,
    route: Route,
    railReturn: String,
    onRailPick: (String) -> Unit,
    filmReturn: String?,
    onFilmPick: (String) -> Unit,
    onClearFilmReturn: () -> Unit = {},
    liveReturn: String? = null,
    onChannelPick: (String) -> Unit = {},
    onExitToLauncher: () -> Unit = {},
    topBarLanding: LandingFocus? = null,
    onTopBarFocusChanged: (Boolean) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Campana y recarga del Inicio viven ahora en la barra superior, no flotando
    // encima de los pósters. El estado es de la pantalla completa de la tele.
    var showPluginUpdates by rememberSaveable { mutableStateOf(false) }
    val updatesBadge = appUpdateBadgeCount()
    LaunchedEffect(route, updatesBadge) {
        if (route == Route.Home && updatesBadge > 0) {
            val latest = com.chronosbvrx.visionplus.data.AppUpdate.latest?.version.orEmpty()
            val skipped = context.getSharedPreferences("updates", 0).getString("skipped", "")
            if (latest.isNotBlank() &&
                (com.chronosbvrx.visionplus.data.AppUpdate.forced(context) || skipped != latest)
            ) {
                showPluginUpdates = true
            }
        }
    }
    if (showPluginUpdates) {
        TvAppUpdatesDialog(onDismiss = {
            showPluginUpdates = false
            if (!com.chronosbvrx.visionplus.data.AppUpdate.forced(context)) {
                com.chronosbvrx.visionplus.data.AppUpdate.latest?.version?.let { version ->
                    context.getSharedPreferences("updates", 0).edit()
                        .putString("skipped", version).apply()
                }
            }
        })
    }
    val isOnline by com.chronosbvrx.visionplus.data.offline.NetworkMonitor.isOnline.collectAsState()
    val rows = catalog.rows
    // El índice remoto manda en Categorías y Búsqueda; Home conserva las filas curadas.
    // Modo niños: Inicio y Buscar solo muestran infantil (el resto pide el PIN).
    val indexRows = rememberLockedIndexRows(catalog.indexRows.ifEmpty { rows })
    // El foco del riel se re-pide al volver (la clave cambia con la ruta). El riel SOLO
    // existe en Inicio: en las demás pantallas su destino no debe quedar registrado — un
    // destino sin nodo compuesto convertía el rescate en una tecla comida (el mando
    // parecía muerto hasta Atrás).
    val railLanding = if (route == Route.Home) rememberLandingFocus("$railReturn|$route") else null
    var pinTarget by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Route?>(null) }
    fun guardedGo(target: Route, viaTab: Boolean) {
        val gated = target == Route.Live || target == Route.Library || target == Route.Downloads ||
            target is Route.Section || target is Route.Category || target == Route.Categories ||
            target == Route.Sports
        if (gated && com.chronosbvrx.visionplus.data.kids.KidsMode.gateClosed(
                com.chronosbvrx.visionplus.data.kids.KidsMode.locked,
                com.chronosbvrx.visionplus.data.kids.KidsMode.hasPass(context),
            )
        ) {
            pinTarget = target
            return
        }
        // Salir por un hub invalida el regreso pendiente (si no, cada vuelta lo re-dispara).
        onClearFilmReturn()
        if (viaTab && target == Route.Home) nav.goTab(Route.Home)
        else nav.push(target)
    }
    if (pinTarget != null) {
        com.chronosbvrx.visionplus.ui.kids.TvKidsPinDialog(
            setup = false,
            onDismiss = { pinTarget = null },
            onOk = { pin ->
                if (com.chronosbvrx.visionplus.data.kids.KidsMode.verify(context, pin)) {
                    com.chronosbvrx.visionplus.data.kids.KidsMode.pass(context)
                    val dest = pinTarget
                    pinTarget = null
                    if (dest != null) nav.push(dest)
                } else {
                    android.widget.Toast.makeText(context, "PIN incorrecto", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    fun rail(icon: ImageVector, label: String, target: Route): TvRailItem = TvRailItem(
        icon,
        label,
        onClick = {
            onRailPick(label)
            guardedGo(target, viaTab = true)
        },
        modifier = if (label == railReturn && railLanding != null) Modifier.landingFocus(railLanding) else Modifier,
    )

    if (shouldShowOfflineMode(isOnline, route)) {
        TvDownloadsScreen(
            isOfflineMode = true,
            onRetryConnection = {
                if (com.chronosbvrx.visionplus.data.offline.NetworkMonitor.refresh(context)) {
                    scope.launch {
                        catalog.load()
                    }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Aún no hay conexión a internet",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onPlayFilm = { nav.push(Route.Player(it)) },
            onBack = {},
        )
        return
    }

    val showTopBar = com.chronosbvrx.visionplus.ui.tv.tvTopBarVisible(route)
    var showProfiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showProfiles) {
        com.chronosbvrx.visionplus.ui.tv.TvProfilesDialog(onDismiss = { showProfiles = false })
    }
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) {
            com.chronosbvrx.visionplus.ui.tv.TvTopBar(
                active = com.chronosbvrx.visionplus.ui.tv.tvTopTabFor(route),
                onTab = { tab -> guardedGo(tab.target, viaTab = true) },
                onRoute = { route -> guardedGo(route, viaTab = true) },
                onSearch = { guardedGo(Route.Search, viaTab = false) },
                onSettings = { guardedGo(Route.Settings, viaTab = false) },
                // El chip del perfil abre «Perfiles» desde cualquier pantalla.
                onProfile = { showProfiles = true },
                updatesBadge = updatesBadge,
                onNotifications = { showPluginUpdates = true },
                onReload = { scope.launch { catalog.load() } },
                landing = topBarLanding,
                onFocusChanged = onTopBarFocusChanged,
            )
        }
        Box(Modifier.weight(1f)) {
    when (route) {
        Route.Home -> TvHomeScreen(
            catalog = catalog,
            railItems = TV_RAIL_SPECS.map { rail(it.icon, it.label, it.target) },
            bottomItems = listOf(rail(Icons.Default.Settings, "Opciones", Route.Settings)),
            railReturn = railReturn,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                onRailPick("")
                nav.push(Route.Title(it))
            },
            onOpenLive = {
                // Salir del Inicio por un hub invalida el regreso pendiente: si no, cada vuelta
                // re-dispara el regreso viejo (reveal del scroll + robo de foco del D-pad).
                onClearFilmReturn()
                nav.push(Route.Live)
            },
            onOpenCategories = {
                onClearFilmReturn()
                nav.push(Route.Categories)
            },
            onResume = {
                onClearFilmReturn()
                nav.push(resumeRoute(it))
            },
            onGoRoute = { guardedGo(it, viaTab = false) },
            // El «regreso vale una sola vez» del onDone queda APAGADO a propósito: el foco
            // transitorio de una tarjeta reciclada cuenta como «usuario» y limpiar ahí rompe
            // el regreso con contenido tardío (homeBackRestoresSameCardAfterLateContent lo
            // caza). La invalidación buena es por navegación: guardedGo (riel y hubs) y las
            // salidas de arriba (En vivo, Categorías, Continuar).
        )
        is Route.Title -> TvTitleScreen(
            route.film,
            onPlay = { ep -> nav.push(Route.Player(route.film, ep)) },
            onOpenFilm = { nav.push(Route.Title(it)) },
        )
        is Route.Player -> PlayerScreen(route.film, isTv = true, onBack = { closePlayback(route, nav, onExitToLauncher) }, episode = route.episode, resumeMs = route.resumeMs)
        is Route.LivePlayer -> LivePlayerScreen(
            channel = route.channel,
            isTv = true,
            channelCodes = route.channelCodes,
            groupTitle = route.groupTitle,
            onBack = { closePlayback(route, nav, onExitToLauncher) },
        )
        Route.Search -> TvSearchScreen(
            indexRows,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
            returnFilmId = filmReturn,
        )
        Route.Categories -> TvCategoriesScreen(
            indexRows,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
        )
        is Route.Category -> TvCategoryScreen(
            indexRows,
            route.key,
            route.title,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
        )
        is Route.Section -> TvCategoryScreen(
            indexRows,
            "",
            route.title,
            section = route.name,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
        )
        Route.Library -> TvLibraryScreen(
            catalog,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
            onResume = {
                onClearFilmReturn()
                nav.push(resumeRoute(it))
            },
        )
        Route.Downloads -> TvDownloadsScreen(
            onPlayFilm = { nav.push(Route.Player(it)) },
            onBack = { nav.pop() },
        )
        Route.Live -> TvLiveScreen(
            onPlay = { ch, codes, scope ->
                onChannelPick(ch.url.removePrefix("xtvlive:"))
                nav.push(Route.LivePlayer(ch, channelCodes = codes, groupTitle = scope))
            },
            returnChannelCode = liveReturn,
        )
        Route.Sports -> TvSportsScreen(onPlay = { nav.push(Route.LivePlayer(it)) })
        // Vision+ Play sigue en preparación: la pestaña no abre el tablero de juegos.
        Route.Games, is Route.GameRoom -> GamesComingSoonScreen(
            isTv = true,
            onBack = { nav.pop() },
        )
        Route.Settings -> TvSettingsScreen()
        Route.About -> TvAboutScreen()
        else -> Unit
    }
        }
    }
}
