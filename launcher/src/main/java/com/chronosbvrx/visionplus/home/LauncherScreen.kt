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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.LiveItem
import com.chronosbvrx.visionplus.home.contract.VisionLinks
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.AppsRepository
import com.chronosbvrx.visionplus.home.data.DisplayConfig
import com.chronosbvrx.visionplus.home.data.FeedRepository
import com.chronosbvrx.visionplus.home.data.HomeConfig
import com.chronosbvrx.visionplus.home.data.HomeUpdate
import com.chronosbvrx.visionplus.home.data.LauncherPrefs
import com.chronosbvrx.visionplus.home.data.homeRowEntries
import com.chronosbvrx.visionplus.home.ui.AppsSurface
import com.chronosbvrx.visionplus.home.ui.CatalogSurface
import com.chronosbvrx.visionplus.home.ui.CommandCenterDialog
import com.chronosbvrx.visionplus.home.ui.DisplaySettingsDialog
import com.chronosbvrx.visionplus.home.ui.EventsSurface
import com.chronosbvrx.visionplus.home.ui.HomeColors
import com.chronosbvrx.visionplus.home.ui.HomeTab
import com.chronosbvrx.visionplus.home.ui.HomeSurface
import com.chronosbvrx.visionplus.home.ui.ItemActionsDialog
import com.chronosbvrx.visionplus.home.ui.LiveSurface
import com.chronosbvrx.visionplus.home.ui.ManageAppsDialog
import com.chronosbvrx.visionplus.home.ui.ManageChannelsDialog
import com.chronosbvrx.visionplus.home.ui.ManageRowsDialog
import com.chronosbvrx.visionplus.home.ui.MenuAction
import com.chronosbvrx.visionplus.home.ui.MissingVisionBanner
import com.chronosbvrx.visionplus.home.ui.OutdatedVisionBanner
import com.chronosbvrx.visionplus.home.ui.PersonalizeHubDialog
import com.chronosbvrx.visionplus.home.ui.SearchSurface
import com.chronosbvrx.visionplus.home.ui.TopChrome
import com.chronosbvrx.visionplus.home.ui.currentProgram
import com.chronosbvrx.visionplus.home.ui.requestFocusSafely
import com.chronosbvrx.visionplus.home.rememberHomeMetrics
import com.chronosbvrx.visionplus.home.ui.LocalReducedEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PersonalizePage { HUB, CHANNELS, APPS, ROWS, DISPLAY }

private sealed interface EditTarget {
    data class Channel(val item: LiveItem) : EditTarget
    data class App(val app: AppEntry) : EditTarget
}

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

    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var recentApps by remember { mutableStateOf(prefs.recentApps()) }
    val profileKey = feed?.meta?.profileId?.takeIf { it.isNotBlank() }
        ?: feed?.meta?.profile?.lowercase()?.takeIf { it.isNotBlank() }
        ?: "principal"
    val profileAliases = listOf(feed?.meta?.profile.orEmpty(), "principal")
    var config by remember(profileKey, feed?.meta?.profile, feed?.meta?.profileId) {
        mutableStateOf(prefs.homeConfig(profileKey, profileAliases))
    }
    var personalizePage by remember { mutableStateOf<PersonalizePage?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    val reduced = when (config.display.animations) {
        com.chronosbvrx.visionplus.home.data.LauncherAnimations.AUTO -> profile.reducedEffects
        com.chronosbvrx.visionplus.home.data.LauncherAnimations.LIGHT -> true
        com.chronosbvrx.visionplus.home.data.LauncherAnimations.FULL -> false
    }
    val metrics = rememberHomeMetrics(config.display.density)
    val compact = metrics.posterWidth < 110.dp

    // Un Inicio de sistema siempre abre en Inicio (no en la última pestaña que se miró).
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var commandCenter by remember { mutableStateOf(false) }
    // Se recalcula al abrir el centro de control: si acaba de aceptar el rol, muestra el estado
    // correcto y deja la puerta manual para cambiar al otro launcher.
    val homeDefault = remember(commandCenter) {
        if (commandCenter) appsRepo.isVisionHomeDefault() else false
    }
    var searchOpen by remember { mutableStateOf(false) }
    var visionInstalled by remember { mutableStateOf(appsRepo.visionPlusInstalled()) }
    var canInstall by remember { mutableStateOf(HomeUpdate.canInstall(context)) }
    var latestApp by remember { mutableStateOf<String?>(null) }
    var feedAttempted by remember { mutableStateOf(false) }
    var heroFocused by remember { mutableStateOf(false) }
    var screenHasFocus by remember { mutableStateOf(false) }
    var userHasMoved by remember { mutableStateOf(false) }
    var landingRequest by remember { mutableIntStateOf(0) }
    var handledLandingRequest by remember { mutableIntStateOf(0) }
    val landing = remember { FocusRequester() }
    val tabLanding = remember { FocusRequester() }
    val sectionLanding = remember { FocusRequester() }
    val homeListState = rememberLazyListState()

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

    // TV en vivo y Deportes cambian con el reloj aunque nadie toque el mando: se vuelve a
    // pedir el feed (y con él la guía EPG y la fase de cada partido) cada dos minutos.
    LaunchedEffect(tab) {
        if (tab != HomeTab.LIVE && tab != HomeTab.EVENTS) return@LaunchedEffect
        while (true) {
            delay(120_000L)
            feeds.refresh()?.let { feed = it }
        }
    }

    // El botón Home ya estando en el launcher: vuelve al Inicio, cierra paneles y sube al hero.
    LaunchedEffect(homeSignal) {
        if (homeSignal == 0) return@LaunchedEffect
        tab = HomeTab.HOME
        commandCenter = false
        searchOpen = false
        userHasMoved = false
        landingRequest++
        runCatching { homeListState.scrollToItem(0) }
    }

    // Un SOLO aterrizaje verificado. Ya no se simulan diez «Abajo»: el foco se pide al héroe
    // (o al primer control de la pestaña) y se reintenta mientras el contenido termina de
    // componerse. Si el usuario ya movió el mando, no se vuelve a robar el foco.
    LaunchedEffect(
        tab,
        landingRequest,
        feed?.hero?.firstOrNull()?.ref,
        feed?.live?.size,
        feed?.sports?.size,
        feed?.rows?.size,
        apps.size,
    ) {
        val forced = landingRequest != handledLandingRequest
        if (forced) {
            handledLandingRequest = landingRequest
            userHasMoved = false
        } else if (userHasMoved) {
            return@LaunchedEffect
        }
        if (tab == HomeTab.HOME && heroFocused) return@LaunchedEffect
        val target = if (tab == HomeTab.HOME) landing else sectionLanding
        repeat(6) {
            delay(120)
            if (tab == HomeTab.HOME && heroFocused) return@LaunchedEffect
            if (userHasMoved) return@LaunchedEffect
            requestFocusSafely(target)
        }
    }

    // Atrás nunca cierra el sistema: como mucho vuelve a Inicio.
    BackHandler(enabled = true) {
        when {
            commandCenter -> commandCenter = false
            searchOpen -> searchOpen = false
            tab != HomeTab.HOME -> {
                tab = HomeTab.HOME
                userHasMoved = false
                landingRequest++
            }
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
        recentApps = prefs.recentApps()
        if (!appsRepo.launch(app.packageName)) {
            LauncherActions.toast(context, "No se pudo abrir ${app.label}")
        }
    }

    fun updateConfig(change: (HomeConfig) -> HomeConfig) {
        val next = change(config)
        if (next == config) return
        config = next
        prefs.saveHomeConfig(profileKey, next, profileAliases)
    }

    fun toggleAppFavorite(app: AppEntry) {
        val adding = app.packageName !in config.appFavorites
        updateConfig { it.toggleAppFavorite(app.packageName) }
        LauncherActions.toast(
            context,
            if (adding) "${app.label} fijada en el Inicio" else "${app.label} quitada de favoritas",
        )
    }

    fun toggleAppHidden(app: AppEntry) {
        val hiding = app.packageName !in config.hiddenApps
        updateConfig { it.toggleAppHidden(app.packageName) }
        LauncherActions.toast(
            context,
            if (hiding) "${app.label} oculta · restáurala en Personalizar > Apps" else "${app.label} visible otra vez",
        )
    }

    fun toggleChannelFavorite(item: LiveItem) {
        val adding = item.code !in config.channelFavorites
        updateConfig { it.toggleChannelFavorite(item.code) }
        LauncherActions.toast(
            context,
            if (adding) "${item.name} en Mis canales" else "${item.name} fuera de Mis canales",
        )
    }

    fun toggleChannelHidden(item: LiveItem) {
        val hiding = item.code !in config.hiddenChannels
        updateConfig { it.toggleChannelHidden(item.code) }
        LauncherActions.toast(
            context,
            if (hiding) "${item.name} oculto · restáuralo en Personalizar > Canales" else "${item.name} visible otra vez",
        )
    }

    // Vision+ instalado pero sin feed (canal nuevo) o más viejo que el release: se ofrece
    // actualizarlo desde aquí; el usuario no tiene que abrir la app para descubrirlo.
    val appOutdated = visionInstalled && feedAttempted && (
        feed == null ||
            (latestApp != null && HomeUpdate.isOlder(feed?.meta?.appVersion.orEmpty(), latestApp!!))
        )

    CompositionLocalProvider(LocalReducedEffects provides reduced) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HomeColors.Black)
            .onFocusChanged { screenHasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    userHasMoved = true
                    // Si el foco quedó huérfano, la primera tecla lo devuelve a un control
                    // real en vez de perderse en silencio.
                    if (!screenHasFocus) {
                        val target = if (tab == HomeTab.HOME) landing else sectionLanding
                        if (requestFocusSafely(target)) return@onPreviewKeyEvent true
                    }
                }
                false
            },
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
                onTab = {
                    if (it != tab) {
                        tab = it
                        userHasMoved = false
                        landingRequest++
                    }
                },
                onSearch = { searchOpen = true },
                onCommandCenter = { commandCenter = true },
                compact = compact,
                activeTabRequester = tabLanding,
                downTarget = if (tab == HomeTab.HOME) landing else sectionLanding,
                clock24h = config.display.clock24h,
                showClock = config.display.showClock,
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
                    HomeTab.HOME -> HomeSurface(
                        feed = feed,
                        apps = apps,
                        recentApps = recentApps,
                        config = config,
                        metrics = metrics,
                        reduced = reduced,
                        compact = compact,
                        landing = landing,
                        listState = homeListState,
                        tabLanding = tabLanding,
                        onHeroPlay = { item -> openLink(item.playLink.ifBlank { item.openLink }) },
                        onHeroOpen = { item -> openLink(item.openLink) },
                        onItem = { item -> openLink(item.openLink) },
                        onContinue = { item -> openLink(item.playLink) },
                        onLive = { item -> openLink(item.playLink) },
                        onChannelLongClick = { item -> editTarget = EditTarget.Channel(item) },
                        onApp = { app -> launchApp(app) },
                        onAppLongClick = { app -> editTarget = EditTarget.App(app) },
                        onSeeLive = {
                            tab = HomeTab.LIVE
                            userHasMoved = false
                            landingRequest++
                        },
                        onSeeApps = {
                            tab = HomeTab.APPS
                            userHasMoved = false
                            landingRequest++
                        },
                        onOpenHistory = { openLink(VisionLinks.library()) },
                        onOpenVision = { if (visionInstalled) openVision() else scope.launch { HomeUpdate.installVisionPlus(context) } },
                        onSystemSettings = { appsRepo.openSystemSettings() },
                        onPrimaryFocus = { heroFocused = it },
                    )

                    HomeTab.LIVE -> LiveSurface(
                        items = feed?.live.orEmpty(),
                        config = config,
                        metrics = metrics,
                        onOpen = { item, channels, scope ->
                            openLink(
                                VisionLinks.live(
                                    code = item.code,
                                    name = item.name,
                                    logo = item.logo,
                                    codes = channels.map { it.code },
                                    scope = scope,
                                ),
                            )
                        },
                        onOpenFull = { openLink(VisionLinks.liveHub()) },
                        onLongClick = { item -> editTarget = EditTarget.Channel(item) },
                        onManageChannels = { personalizePage = PersonalizePage.CHANNELS },
                        upTarget = tabLanding,
                        landing = sectionLanding,
                    )

                    HomeTab.EVENTS -> EventsSurface(
                        events = feed?.sports.orEmpty(),
                        metrics = metrics,
                        onOpen = { event -> openLink(event.openLink) },
                        onOpenFull = { openLink(VisionLinks.sports()) },
                        upTarget = tabLanding,
                        landing = sectionLanding,
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
                        landing = sectionLanding,
                    )

                    HomeTab.APPS -> AppsSurface(
                        apps = apps,
                        recentApps = recentApps,
                        config = config,
                        metrics = metrics,
                        onOpen = { app -> launchApp(app) },
                        onLongClick = { app -> editTarget = EditTarget.App(app) },
                        onSystemSettings = { appsRepo.openSystemSettings() },
                        onWifi = { appsRepo.openWifiSettings() },
                        onHomeSettings = { appsRepo.openHomeSettings() },
                        onRequestHome = { appsRepo.requestHomeRole() },
                        onManage = { personalizePage = PersonalizePage.APPS },
                        landing = sectionLanding,
                        upTarget = tabLanding,
                    )
                }
            }
        }
    }
    }

    if (commandCenter) {
        CommandCenterDialog(
            profileName = feed?.meta?.profile.orEmpty(),
            profiles = feed?.meta?.profiles ?: 1,
            onDismiss = { commandCenter = false },
            onPersonalize = {
                commandCenter = false
                personalizePage = PersonalizePage.HUB
            },
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
            onRequestHome = {
                commandCenter = false
                appsRepo.requestHomeRole()
            },
            homeDefault = homeDefault,
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

    editTarget?.let { target ->
        when (target) {
            is EditTarget.Channel -> {
                val item = target.item
                val favorite = item.code in config.channelFavorites
                val hidden = item.code in config.hiddenChannels
                ItemActionsDialog(
                    title = item.name,
                    subtitle = currentProgram(item, System.currentTimeMillis()).ifBlank { "Canal en vivo" },
                    actions = buildList {
                        add(
                            MenuAction(
                                title = if (favorite) "Quitar de Mis canales" else "Fijar en Mis canales",
                                detail = if (favorite) "Deja de aparecer en la fila de favoritos" else "Aparece en «Mis canales» del Inicio",
                                onClick = {
                                    toggleChannelFavorite(item)
                                    editTarget = null
                                },
                            ),
                        )
                        add(
                            MenuAction(
                                title = if (hidden) "Mostrar canal" else "Ocultar canal",
                                detail = if (hidden) "Vuelve al mosaico y a las filas" else "Desaparece del mosaico; puedes restaurarlo aquí",
                                onClick = {
                                    toggleChannelHidden(item)
                                    editTarget = null
                                },
                            ),
                        )
                        if (favorite) {
                            add(
                                MenuAction(
                                    title = "Mover al inicio de Mis canales",
                                    detail = "Queda como el primer favorito",
                                    onClick = {
                                        updateConfig { it.promoteChannelFavorite(item.code) }
                                        editTarget = null
                                        LauncherActions.toast(context, "${item.name} al inicio de Mis canales")
                                    },
                                ),
                            )
                        }
                        add(
                            MenuAction(
                                title = "Gestionar canales",
                                detail = "Favoritos, ocultos y orden",
                                onClick = {
                                    editTarget = null
                                    personalizePage = PersonalizePage.CHANNELS
                                },
                            ),
                        )
                    },
                    onDismiss = { editTarget = null },
                )
            }

            is EditTarget.App -> {
                val app = target.app
                val favorite = app.packageName in config.appFavorites
                val hidden = app.packageName in config.hiddenApps
                ItemActionsDialog(
                    title = app.label,
                    subtitle = app.packageName,
                    actions = buildList {
                        add(
                            MenuAction(
                                title = if (favorite) "Quitar de favoritas" else "Fijar app",
                                detail = if (favorite) "Deja de ir primero en el Inicio" else "Aparece primero en Apps y en el Inicio",
                                onClick = {
                                    toggleAppFavorite(app)
                                    editTarget = null
                                },
                            ),
                        )
                        add(
                            MenuAction(
                                title = if (hidden) "Mostrar app" else "Ocultar app",
                                detail = if (hidden) "Vuelve al Inicio y a Apps" else "No se desinstala: solo se oculta del launcher",
                                onClick = {
                                    toggleAppHidden(app)
                                    editTarget = null
                                },
                            ),
                        )
                        if (favorite) {
                            add(
                                MenuAction(
                                    title = "Mover al inicio de Apps",
                                    detail = "Queda como la primera favorita",
                                    onClick = {
                                        updateConfig { it.promoteAppFavorite(app.packageName) }
                                        editTarget = null
                                        LauncherActions.toast(context, "${app.label} al inicio de Apps")
                                    },
                                ),
                            )
                        }
                        add(
                            MenuAction(
                                title = "Gestionar apps",
                                detail = "Favoritas, ocultas y orden",
                                onClick = {
                                    editTarget = null
                                    personalizePage = PersonalizePage.APPS
                                },
                            ),
                        )
                    },
                    onDismiss = { editTarget = null },
                )
            }
        }
    }

    when (personalizePage) {
        PersonalizePage.HUB -> PersonalizeHubDialog(
            profileName = feed?.meta?.profile.orEmpty(),
            config = config,
            channelCount = feed?.live?.size ?: 0,
            appCount = apps.size,
            onChannels = { personalizePage = PersonalizePage.CHANNELS },
            onApps = { personalizePage = PersonalizePage.APPS },
            onRows = { personalizePage = PersonalizePage.ROWS },
            onAppearance = { personalizePage = PersonalizePage.DISPLAY },
            onReset = {
                updateConfig { it.resetAll() }
                personalizePage = null
                LauncherActions.toast(context, "Personalización restablecida")
            },
            onDismiss = { personalizePage = null },
        )

        PersonalizePage.CHANNELS -> ManageChannelsDialog(
            channels = feed?.live.orEmpty(),
            config = config,
            onToggleFavorite = { code -> updateConfig { it.toggleChannelFavorite(code) } },
            onToggleHidden = { code -> updateConfig { it.toggleChannelHidden(code) } },
            onMoveFavorite = { code, delta -> updateConfig { it.moveChannelFavorite(code, delta) } },
            onSort = { sort -> updateConfig { it.setChannelSort(sort) } },
            onReset = {
                updateConfig { it.resetChannels() }
                LauncherActions.toast(context, "Canales restablecidos")
            },
            onDismiss = { personalizePage = PersonalizePage.HUB },
        )

        PersonalizePage.APPS -> ManageAppsDialog(
            apps = apps,
            config = config,
            onToggleFavorite = { pkg -> updateConfig { it.toggleAppFavorite(pkg) } },
            onToggleHidden = { pkg -> updateConfig { it.toggleAppHidden(pkg) } },
            onMoveFavorite = { pkg, delta -> updateConfig { it.moveAppFavorite(pkg, delta) } },
            onReset = {
                updateConfig { it.resetApps() }
                LauncherActions.toast(context, "Apps restablecidas")
            },
            onDismiss = { personalizePage = PersonalizePage.HUB },
        )

        PersonalizePage.ROWS -> {
            val rows = homeRowEntries(config, feed?.rows.orEmpty())
            ManageRowsDialog(
                rows = rows,
                config = config,
                onMove = { id, delta -> updateConfig { it.moveRow(id, delta) } },
                onToggle = { id ->
                    val visible = rows.count { it.id !in config.hiddenRows }
                    if (id !in config.hiddenRows && visible <= 1) {
                        LauncherActions.toast(context, "Debe quedar al menos una fila visible")
                    } else {
                        updateConfig { it.toggleRow(id) }
                    }
                },
                onReset = {
                    updateConfig { it.resetRows() }
                    LauncherActions.toast(context, "Filas restablecidas")
                },
                onDismiss = { personalizePage = PersonalizePage.HUB },
            )
        }

        PersonalizePage.DISPLAY -> DisplaySettingsDialog(
            config = config,
            onChange = { display -> updateConfig { it.withDisplay(display) } },
            onReset = {
                updateConfig { it.withDisplay(DisplayConfig()) }
                LauncherActions.toast(context, "Apariencia restablecida")
            },
            onDismiss = { personalizePage = PersonalizePage.HUB },
        )

        null -> Unit
    }
}
