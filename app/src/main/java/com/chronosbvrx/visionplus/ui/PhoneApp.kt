package com.chronosbvrx.visionplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.about.AboutScreen
import com.chronosbvrx.visionplus.ui.brand.VisionWordmark
import com.chronosbvrx.visionplus.ui.home.HomeScreen
import com.chronosbvrx.visionplus.ui.phone.CategoriesScreen
import com.chronosbvrx.visionplus.ui.phone.LibraryScreen
import com.chronosbvrx.visionplus.ui.phone.LiveScreen
import com.chronosbvrx.visionplus.ui.phone.SearchScreen
import com.chronosbvrx.visionplus.ui.phone.SectionScreen
import com.chronosbvrx.visionplus.ui.phone.SettingsScreen
import com.chronosbvrx.visionplus.ui.player.LivePlayerScreen
import com.chronosbvrx.visionplus.ui.player.PlayerScreen
import com.chronosbvrx.visionplus.ui.plugins.AppUpdatesDialog
import com.chronosbvrx.visionplus.ui.plugins.appUpdateBadgeCount
import com.chronosbvrx.visionplus.ui.plugins.CountBadgedIcon
import com.chronosbvrx.visionplus.ui.plugins.PluginUpdatesBellIcon
import com.chronosbvrx.visionplus.ui.remote.RemoteMiniPlayer
import com.chronosbvrx.visionplus.ui.sports.SportsScreen
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.title.TitleScreen
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Download
import com.chronosbvrx.visionplus.ui.phone.DownloadsScreen
import com.chronosbvrx.visionplus.ui.remote.RemoteScreen

internal data class Tab(val route: Route, val label: String, val icon: ImageVector)

/** El menú lateral del móvil/tablet (drawer y riel en apaisado), en orden de aparición. */
internal val TABS = listOf(
    Tab(Route.Home, "Inicio", Icons.Default.Home),
    Tab(Route.Section("movies", "Películas"), "Películas", Icons.Default.Movie),
    Tab(Route.Section("series", "Series"), "Series", Icons.Default.Theaters),
    Tab(Route.Live, "En vivo", Icons.Default.LiveTv),
    Tab(Route.Sports, "Deportes en vivo", Icons.Default.SportsSoccer),
    Tab(Route.Library, "Mi lista", Icons.Default.Bookmark),
    Tab(Route.Downloads, "Descargas", Icons.Default.Download),
    Tab(Route.Remote, "Control remoto", Icons.Default.Cast),
    Tab(Route.Settings, "Ajustes", Icons.Default.Settings),
    Tab(Route.About, "Acerca de", Icons.Default.Info),
)

/**
 * The phone's shell: a drawer with the sections (a rail on a landscape tablet), a top bar with the
 * logo on section screens (on Inicio also the plugin-updates bell), and full-screen pages for a film, the player, search and the assistant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(catalog: XuperCatalog, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val route = nav.current
    LaunchedEffect(route) { com.chronosbvrx.visionplus.data.diag.Diagnostics.screen(route.toString()) }
    val rows = catalog.rows
    val isTab = TABS.any { it.route == route }
    val isWide = isLandscapeTablet()
    var showPluginUpdates by rememberSaveable { mutableStateOf(false) }
    val updatesBadge = appUpdateBadgeCount()
    // The manager speaks up by itself once per version: if this release was not skipped, the
    // sheet opens so the update can be taken without leaving the app. La versión mínima
    // forzada (kill-switch remoto) ignora el "skip": la oferta vuelve en cada apertura.
    LaunchedEffect(updatesBadge) {
        if (updatesBadge > 0) {
            val latest = AppUpdate.latest?.version.orEmpty()
            val skipped = context.getSharedPreferences("updates", 0).getString("skipped", "")
            if (latest.isNotBlank() && (AppUpdate.forced(context) || skipped != latest)) showPluginUpdates = true
        }
    }
    if (showPluginUpdates) {
        AppUpdatesDialog(
            release = AppUpdate.latest,
            onDismiss = {
                showPluginUpdates = false
                // Con actualización forzada NO se recuerda el "skip": la próxima apertura vuelve a pedirla.
                if (!AppUpdate.forced(context)) {
                    AppUpdate.latest?.version?.let { v ->
                        context.getSharedPreferences("updates", 0).edit().putString("skipped", v).apply()
                    }
                }
            },
        )
    }

    val isOnline by com.chronosbvrx.visionplus.data.offline.NetworkMonitor.isOnline.collectAsState()
    // Modo niños: las secciones sensibles piden el PIN (pase de 15 min, sin desbloquear).
    var pinRoute by remember { mutableStateOf<Route?>(null) }
    fun goTabGuarded(route: Route) {
        val gated = route == Route.Live || route == Route.Library || route == Route.Downloads ||
            route is Route.Section || route == Route.Categories || route == Route.Sports
        if (gated && com.chronosbvrx.visionplus.data.kids.KidsMode.gateClosed(
                com.chronosbvrx.visionplus.data.kids.KidsMode.locked,
                com.chronosbvrx.visionplus.data.kids.KidsMode.hasPass(context),
            )
        ) {
            pinRoute = route
            return
        }
        nav.goTab(route)
    }
    if (pinRoute != null) {
        com.chronosbvrx.visionplus.ui.kids.MobileKidsPinDialog(
            setup = false,
            onDismiss = { pinRoute = null },
            onOk = { pin ->
                if (com.chronosbvrx.visionplus.data.kids.KidsMode.verify(context, pin)) {
                    com.chronosbvrx.visionplus.data.kids.KidsMode.pass(context)
                    val dest = pinRoute
                    pinRoute = null
                    if (dest != null) nav.goTab(dest)
                } else {
                    android.widget.Toast.makeText(context, "PIN incorrecto", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && nav.stack.size > 1 && route !is Route.Player && route !is Route.LivePlayer) { nav.pop() }

    when (route) {
        is Route.Player -> { PlayerScreen(route.film, isTv = false, onBack = { nav.close(route) }, episode = route.episode, resumeMs = route.resumeMs); return }
        else -> Unit
    }

    if (!isOnline) {
        DownloadsScreen(
            isOfflineMode = true,
            onRetryConnection = {
                if (com.chronosbvrx.visionplus.data.offline.NetworkMonitor.refresh(context)) {
                    scope.launch { catalog.load() }
                }
            },
            onOpenFilm = { nav.push(Route.Player(it)) },
        )
        return
    }

    when (route) {
        is Route.LivePlayer -> {
            LivePlayerScreen(
                channel = route.channel,
                isTv = false,
                channelCodes = route.channelCodes,
                groupTitle = route.groupTitle,
                onBack = { nav.close(route) },
            )
            return
        }
        is Route.Title -> {
            TitleScreen(route.film, onBack = { nav.pop() }, onPlay = { ep -> nav.push(Route.Player(route.film, ep)) })
            return
        }
        Route.Search -> {
            val searchRows = catalog.indexRows.ifEmpty { rows }
            val kidSafe = if (com.chronosbvrx.visionplus.data.kids.KidsMode.locked) {
                com.chronosbvrx.visionplus.data.kids.KidsMode.kidsRows(searchRows)
            } else {
                searchRows
            }
            SearchScreen(kidSafe, onBack = { nav.pop() }, onOpenFilm = { nav.push(Route.Title(it)) })
            return
        }
        else -> Unit
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isTab && !isWide,
        drawerContent = {            ModalDrawerSheet(drawerContainerColor = VisionBlack) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(24.dp))
                    VisionWordmark(height = 30.dp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    TABS.forEach { tab ->
                        NavigationDrawerItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = tab.route == route,
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = VisionBlue.copy(alpha = 0.15f),
                                selectedIconColor = VisionBlue,
                                selectedTextColor = VisionBlue,
                                unselectedIconColor = Color.White,
                                unselectedTextColor = Color.White,
                            ),
                            onClick = {
                                scope.launch { drawerState.close() }
                                goTabGuarded(tab.route)
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            }
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (isWide && isTab) {
                NavigationRail(containerColor = VisionBlack) {
                    TABS.forEach { tab ->
                        NavigationRailItem(
                            selected = tab.route == route,
                            onClick = { goTabGuarded(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = VisionBlack,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (!isWide) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    // The plugin updates waiting for approval show on the menu too, where Plugins is.
                                    CountBadgedIcon(Icons.Default.Menu, "Menú", updatesBadge)
                                }
                            }
                        },
                        title = {
                            // Off Inicio the logo is the quick way home.
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = route != Route.Home, onClickLabel = "Ir al inicio") { nav.goTab(Route.Home) },
                                contentAlignment = Alignment.CenterStart,
                            ) { VisionWordmark(height = 24.dp) }
                        },
                        actions = {
                            if (route == Route.Home) {
                                IconButton(onClick = { showPluginUpdates = true }) { PluginUpdatesBellIcon(updatesBadge) }
                                IconButton(onClick = { scope.launch { catalog.load() } }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Recargar catálogo", tint = Color.White)
                                }
                                IconButton(onClick = { nav.push(Route.Search) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = VisionBlack),
                    )
                },
                // The linked TV's remote, floating over the section screens while it plays something.
                bottomBar = { RemoteMiniPlayer(rows) },
            ) { padding ->
                // Transición suave entre secciones (solo móvil: en TV el foco manda al instante).
                androidx.compose.animation.Crossfade(
                    targetState = route,
                    animationSpec = androidx.compose.animation.core.tween(140),
                    label = "ruta",
                ) { current ->
                    when (current) {
                        Route.Home -> HomeScreen(
                            catalog = catalog,
                            contentPadding = padding,
                            onOpenFilm = { nav.push(Route.Title(it)) },
                            onPlayFilm = { nav.push(Route.Player(it)) },
                            onGoSection = { goTabGuarded(it) },
                            onResume = { nav.push(resumeRoute(it)) },
                        )
                        Route.Categories -> CategoriesScreen(
                            catalog, padding,
                            onOpenFilm = { nav.push(Route.Title(it)) },
                            onOpenSection = { nav.push(it) },
                        )
                        is Route.Section -> SectionScreen(
                            name = current.name,
                            title = current.title,
                            contentPadding = padding,
                            onBack = { nav.pop() },
                            onOpenFilm = { nav.push(Route.Title(it)) },
                        )
                        Route.Sports -> SportsScreen(padding, onPlay = { nav.push(Route.LivePlayer(it)) })
                        Route.Library -> LibraryScreen(catalog, padding, onOpenFilm = { nav.push(Route.Title(it)) })
                        Route.Downloads -> DownloadsScreen(
                            contentPadding = padding,
                            onOpenFilm = { nav.push(Route.Player(it)) },
                            onBack = { nav.pop() },
                        )
                        Route.Live -> LiveScreen(padding, onPlay = { ch, codes, scope ->
                            nav.push(Route.LivePlayer(ch, channelCodes = codes, groupTitle = scope))
                        })
                        Route.Settings -> SettingsScreen(padding)
                        Route.Remote -> RemoteScreen(catalog, onBack = { nav.pop() })
                        Route.About -> AboutScreen(padding)
                        else -> Unit
                    }
                }
            }
        }
    }
}
