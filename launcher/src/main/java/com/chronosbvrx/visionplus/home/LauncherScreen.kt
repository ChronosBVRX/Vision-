package com.chronosbvrx.visionplus.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.VisionLinks
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.AppsRepository
import com.chronosbvrx.visionplus.home.data.FeedRepository
import com.chronosbvrx.visionplus.home.data.HomeUpdate
import com.chronosbvrx.visionplus.home.data.LauncherPrefs
import com.chronosbvrx.visionplus.home.ui.AppsSurface
import com.chronosbvrx.visionplus.home.ui.CatalogSurface
import com.chronosbvrx.visionplus.home.ui.CommandCenterDialog
import com.chronosbvrx.visionplus.home.ui.EventsSurface
import com.chronosbvrx.visionplus.home.ui.HomeColors
import com.chronosbvrx.visionplus.home.ui.HomeTab
import com.chronosbvrx.visionplus.home.ui.HomeSurface
import com.chronosbvrx.visionplus.home.ui.LiveSurface
import com.chronosbvrx.visionplus.home.ui.MissingVisionBanner
import com.chronosbvrx.visionplus.home.ui.OutdatedVisionBanner
import com.chronosbvrx.visionplus.home.ui.SearchSurface
import com.chronosbvrx.visionplus.home.ui.TopChrome
import com.chronosbvrx.visionplus.home.rememberHomeMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La pantalla de inicio del sistema. Todo lo que se ve es o del feed de Vision+ o del propio
 * Android (apps instaladas); aquí no se reproduce ni se descarga nada.
 */
@Composable
fun LauncherScreen(profile: DeviceProfile, homeSignal: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LauncherPrefs(context) }
    val feeds = remember { FeedRepository(context) }
    val appsRepo = remember { AppsRepository(context) }
    val metrics = rememberHomeMetrics()
    val reduced = profile.reducedEffects
    val compact = metrics.posterWidth < 110.dp
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var favorites by remember { mutableStateOf(prefs.favorites()) }
    // Un Inicio de sistema siempre abre en Inicio (no en la última pestaña que se miró).
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var commandCenter by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var visionInstalled by remember { mutableStateOf(appsRepo.visionPlusInstalled()) }
    var canInstall by remember { mutableStateOf(HomeUpdate.canInstall(context)) }
    var latestApp by remember { mutableStateOf<String?>(null) }
    var feedAttempted by remember { mutableStateOf(false) }
    var homeKey by remember { mutableIntStateOf(0) }
    var forceLanding by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    val landing = remember { FocusRequester() }
    val tabLanding = remember { FocusRequester() }

    // Primer frame: caché propia y apps del sistema. Nada de red esperando.
    LaunchedEffect(Unit) {
        feed = feeds.cached()
        apps = appsRepo.load()
        visionInstalled = appsRepo.visionPlusInstalled()
        feeds.refresh()?.let { feed = it }
        feedAttempted = true
        // La versión del release (cacheada 24 h) decide si Vision+ se quedó atrás.
        latestApp = HomeUpdate.latestAppVersion(context)
    }

    // Vision+ avisa cuando cambia el historial/perfil y el Inicio se refresca solo.
    DisposableEffect(Unit) {
        val observer = feeds.observe {
            scope.launch { feeds.refresh()?.let { feed = it } }
        }
        onDispose { feeds.stopObserving(observer) }
    }

    // Al volver de una app o del reproductor, el minuto y el perfil son los de ahora.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            feeds.refresh()?.let { feed = it }
            visionInstalled = appsRepo.visionPlusInstalled()
            canInstall = HomeUpdate.canInstall(context)
            // Si el instalador se canceló, el botón vuelve a ofrecer la instalación.
            if (!visionInstalled && HomeUpdate.visionState is HomeUpdate.State.Ready) {
                HomeUpdate.resetVision()
            }
        }
    }

    // El botón Home ya estando en el launcher: vuelve al Inicio y cierra paneles.
    LaunchedEffect(homeSignal) {
        if (homeSignal == 0) return@LaunchedEffect
        tab = HomeTab.HOME
        commandCenter = false
        searchOpen = false
        homeKey++
        forceLanding++
    }

    // El foco inicial: el botón principal del hero. Reintenta hasta que el hero REAL está
    // compuesto y enfocado (el primer frame puede traer el hero de marca). El estado se
    // reinicia cuando cambia el feed, porque al sustituir el hero el nodo enfocado se
    // destruye sin avisar y el foco se quedaría en ninguna parte.
    LaunchedEffect(feed?.meta?.generatedAt) { heroFocused = false }
    LaunchedEffect(tab, forceLanding, feed?.meta?.generatedAt, apps.size, visionInstalled) {
        if (tab != HomeTab.HOME) return@LaunchedEffect
        repeat(10) {
            if (heroFocused && forceLanding == 0) return@LaunchedEffect
            delay(180)
            // La misma búsqueda geométrica del D-pad: desde la barra baja al hero. El
            // requestFocus directo no aterriza mientras el hero se está recomponiendo.
            if (!heroFocused) runCatching { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
        }
    }

    // Atrás nunca cierra el sistema: como mucho vuelve a Inicio.
    BackHandler(enabled = true) {
        when {
            commandCenter -> commandCenter = false
            searchOpen -> searchOpen = false
            tab != HomeTab.HOME -> tab = HomeTab.HOME
            else -> Unit
        }
    }

    fun openLink(link: String) {
        if (!LauncherActions.openDeepLink(context, link)) {
            LauncherActions.toast(context, "No se pudo abrir Vision+")
        }
    }

    fun openVision() {
        if (!appsRepo.launchVisionPlus()) {
            LauncherActions.toast(context, "Vision+ no está instalado")
        }
    }

    fun launchApp(app: AppEntry) {
        prefs.noteLaunch(app.packageName)
        if (!appsRepo.launch(app.packageName)) {
            LauncherActions.toast(context, "No se pudo abrir ${app.label}")
        }
    }

    fun toggleFavorite(app: AppEntry) {
        val added = prefs.toggleFavorite(app.packageName)
        favorites = prefs.favorites()
        LauncherActions.toast(
            context,
            if (added) "${app.label} fijada en el Inicio" else "${app.label} quitada del Inicio",
        )
    }

    // Vision+ instalado pero sin feed (canal nuevo) o más viejo que el release: se ofrece
    // actualizarlo desde aquí; el usuario no tiene que abrir la app para descubrirlo.
    val appOutdated = visionInstalled && feedAttempted && (
        feed == null ||
            (latestApp != null && HomeUpdate.isOlder(feed?.meta?.appVersion.orEmpty(), latestApp!!))
        )

    Box(
        Modifier
            .fillMaxSize()
            .background(HomeColors.Black),
    ) {
        // Luz ambiental de la casa (sin animación infinita: es un degradado fijo).
        Box(
            Modifier
                .fillMaxWidth()
                .height(560.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1A2E).copy(alpha = 0.85f), Color.Transparent),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize()) {
            TopChrome(
                tab = tab,
                profile = feed?.meta?.profile.orEmpty(),
                onTab = { tab = it },
                onSearch = { searchOpen = true },
                onCommandCenter = { commandCenter = true },
                compact = compact,
                activeTabRequester = tabLanding,
            )
            if (!visionInstalled) {
                MissingVisionBanner(
                    state = HomeUpdate.visionState,
                    canInstall = canInstall,
                    onInstall = {
                        if (HomeUpdate.canInstall(context)) {
                            scope.launch { HomeUpdate.installVisionPlus(context) }
                        } else {
                            HomeUpdate.openInstallPermission(context)
                        }
                    },
                    onSettings = { appsRepo.openSystemSettings() },
                    modifier = Modifier.padding(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = 4.dp,
                        bottom = 8.dp,
                    ),
                )
            } else if (appOutdated) {
                OutdatedVisionBanner(
                    current = feed?.meta?.appVersion.orEmpty(),
                    latest = latestApp.orEmpty(),
                    state = HomeUpdate.visionState,
                    canInstall = canInstall,
                    onUpdate = {
                        if (HomeUpdate.canInstall(context)) {
                            scope.launch { HomeUpdate.installVisionPlus(context) }
                        } else {
                            HomeUpdate.openInstallPermission(context)
                        }
                    },
                    onSettings = { appsRepo.openSystemSettings() },
                    modifier = Modifier.padding(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = 4.dp,
                        bottom = 8.dp,
                    ),
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    HomeTab.HOME -> key(homeKey) {
                        HomeSurface(
                            feed = feed,
                            apps = apps,
                            favorites = favorites,
                            metrics = metrics,
                            reduced = reduced,
                            compact = compact,
                            landing = landing,
                            onHeroPlay = { item -> openLink(item.playLink.ifBlank { item.openLink }) },
                            onHeroOpen = { item -> openLink(item.openLink) },
                            onItem = { item -> openLink(item.openLink) },
                            onContinue = { item -> openLink(item.playLink) },
                            onLive = { item -> openLink(item.playLink) },
                            onApp = { app -> launchApp(app) },
                            onAppFavorite = { app -> toggleFavorite(app) },
                            onSeeLive = { tab = HomeTab.LIVE },
                            onSeeApps = { tab = HomeTab.APPS },
                            onOpenHistory = { openLink(VisionLinks.library()) },
                            onOpenVision = { if (visionInstalled) openVision() else scope.launch { HomeUpdate.installVisionPlus(context) } },
                            onSystemSettings = { appsRepo.openSystemSettings() },
                            onPrimaryFocus = { heroFocused = it },
                        )
                    }

                    HomeTab.LIVE -> LiveSurface(
                        items = feed?.live.orEmpty(),
                        metrics = metrics,
                        onOpen = { item -> openLink(item.playLink) },
                        onOpenFull = { openLink(VisionLinks.liveHub()) },
                        upTarget = tabLanding,
                    )

                    HomeTab.EVENTS -> EventsSurface(
                        events = feed?.sports.orEmpty(),
                        metrics = metrics,
                        onOpen = { event -> openLink(event.openLink) },
                        onOpenFull = { openLink(VisionLinks.sports()) },
                    )

                    HomeTab.MOVIES, HomeTab.SERIES -> CatalogSurface(
                        tab = tab,
                        feed = feed,
                        metrics = metrics,
                        onOpen = { item -> openLink(item.openLink) },
                        onOpenFull = {
                            openLink(VisionLinks.section(if (tab == HomeTab.MOVIES) "movies" else "series"))
                        },
                        upTarget = tabLanding,
                    )

                    HomeTab.APPS -> AppsSurface(
                        apps = apps,
                        metrics = metrics,
                        onOpen = { app -> launchApp(app) },
                        onLongClick = { app -> toggleFavorite(app) },
                        onSystemSettings = { appsRepo.openSystemSettings() },
                        onWifi = { appsRepo.openWifiSettings() },
                        onHomeSettings = { appsRepo.openHomeSettings() },
                    )
                }
            }
        }
    }

    if (commandCenter) {
        CommandCenterDialog(
            profileName = feed?.meta?.profile.orEmpty(),
            profiles = feed?.meta?.profiles ?: 1,
            onDismiss = { commandCenter = false },
            onOpenVisionSettings = {
                commandCenter = false
                openLink(VisionLinks.settings())
            },
            onSystemSettings = {
                commandCenter = false
                appsRepo.openSystemSettings()
            },
            onHomeSettings = {
                commandCenter = false
                appsRepo.openHomeSettings()
            },
            onUpdate = {
                scope.launch { HomeUpdate.installLatest(context) }
            },
            visionInstalled = visionInstalled,
            visionInstallState = HomeUpdate.visionState,
            onInstallVision = {
                if (HomeUpdate.canInstall(context)) {
                    scope.launch { HomeUpdate.installVisionPlus(context) }
                } else {
                    HomeUpdate.openInstallPermission(context)
                }
            },
            appOutdated = appOutdated,
            appVersion = feed?.meta?.appVersion.orEmpty(),
            latestAppVersion = latestApp.orEmpty(),
            onUpdateVision = {
                if (HomeUpdate.canInstall(context)) {
                    scope.launch { HomeUpdate.installVisionPlus(context) }
                } else {
                    HomeUpdate.openInstallPermission(context)
                }
            },
        )
    }

    if (searchOpen) {
        SearchSurface(
            isTv = profile.isTv,
            repo = feeds,
            onDismiss = { searchOpen = false },
            onOpen = { item ->
                searchOpen = false
                openLink(item.openLink)
            },
        )
    }
}
