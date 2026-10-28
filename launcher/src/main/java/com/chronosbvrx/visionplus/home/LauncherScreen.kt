@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.chronosbvrx.visionplus.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.LiveItem
import com.chronosbvrx.visionplus.home.contract.VisionLinks
import com.chronosbvrx.visionplus.home.data.AppEntry
import com.chronosbvrx.visionplus.home.data.AppsRepository
import com.chronosbvrx.visionplus.home.data.BannersRepository
import com.chronosbvrx.visionplus.home.data.DisplayConfig
import com.chronosbvrx.visionplus.home.data.FeedRepository
import com.chronosbvrx.visionplus.home.data.HeroSlide
import com.chronosbvrx.visionplus.home.data.HomeConfig
import com.chronosbvrx.visionplus.home.data.HomeRoleAction
import com.chronosbvrx.visionplus.home.data.HomeUpdate
import com.chronosbvrx.visionplus.home.data.LauncherPrefs
import com.chronosbvrx.visionplus.home.data.LauncherPreset
import com.chronosbvrx.visionplus.home.data.TvInputSource
import com.chronosbvrx.visionplus.home.data.TvInputsRepository
import com.chronosbvrx.visionplus.home.data.composeHeroSlides
import com.chronosbvrx.visionplus.home.data.homeRowEntries
import com.chronosbvrx.visionplus.home.data.tunerOf
import com.chronosbvrx.visionplus.home.ui.AppsSurface
import com.chronosbvrx.visionplus.home.ui.CatalogSurface
import com.chronosbvrx.visionplus.home.ui.CommandCenterDialog
import com.chronosbvrx.visionplus.home.ui.DisplaySettingsDialog
import com.chronosbvrx.visionplus.home.ui.EventsSurface
import com.chronosbvrx.visionplus.home.ui.HomeColors
import com.chronosbvrx.visionplus.home.ui.HomeGuideDialog
import com.chronosbvrx.visionplus.home.ui.HomeTab
import com.chronosbvrx.visionplus.home.ui.HomeSurface
import com.chronosbvrx.visionplus.home.ui.InputsSurface
import com.chronosbvrx.visionplus.home.ui.ItemActionsDialog
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsActions
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsDialog
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsSection
import com.chronosbvrx.visionplus.home.ui.LauncherSystemInfo
import com.chronosbvrx.visionplus.home.ui.LauncherSystemPage
import com.chronosbvrx.visionplus.home.ui.LiveSurface
import com.chronosbvrx.visionplus.home.ui.ManageAppsDialog
import com.chronosbvrx.visionplus.home.ui.ManageChannelsDialog
import com.chronosbvrx.visionplus.home.ui.ManageRowsDialog
import com.chronosbvrx.visionplus.home.ui.ManageTabsDialog
import com.chronosbvrx.visionplus.home.ui.MenuAction
import com.chronosbvrx.visionplus.home.ui.MissingVisionBanner
import com.chronosbvrx.visionplus.home.ui.OutdatedVisionBanner
import com.chronosbvrx.visionplus.home.ui.PersonalizeHubDialog
import com.chronosbvrx.visionplus.home.ui.PresetsDialog
import com.chronosbvrx.visionplus.home.ui.SearchSurface
import com.chronosbvrx.visionplus.home.ui.TabEntry
import com.chronosbvrx.visionplus.home.ui.TopChrome
import com.chronosbvrx.visionplus.home.ui.currentProgram
import com.chronosbvrx.visionplus.home.ui.requestFocusSafely
import com.chronosbvrx.visionplus.home.ui.softFocusDown
import com.chronosbvrx.visionplus.home.ui.startTabFor
import com.chronosbvrx.visionplus.home.ui.visibleTabs
import com.chronosbvrx.visionplus.home.rememberHomeMetrics
import com.chronosbvrx.visionplus.home.ui.LocalReducedEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PersonalizePage { HUB, CHANNELS, APPS, ROWS, TABS, PRESETS, DISPLAY }

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LauncherPrefs(context) }
    val feeds = remember { FeedRepository(context) }
    val appsRepo = remember { AppsRepository(context) }
    val tvInputs = remember { TvInputsRepository(context) }

    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var recentApps by remember { mutableStateOf(prefs.recentApps()) }
    var heroBanners by remember { mutableStateOf<List<HeroSlide>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<TvInputSource>>(emptyList()) }
    var inputsLoaded by remember { mutableStateOf(false) }
    var system by remember { mutableStateOf(LauncherSystemInfo()) }
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
    // Una TV box no tiene sintonizador ni entradas HDMI: la pestaña «Entradas» solo existe si
    // el equipo reporta al menos una fuente real. Nunca se inventa «TV abierta».
    val inputsAvailable = inputs.isNotEmpty()
    val availableTabs = remember(config.tabOrder, config.hiddenTabs, inputsAvailable) {
        visibleTabs(config, inputsAvailable)
    }
    // Hero: banners diarios del VPS (arte panorámico) + partidos en vivo de la agenda.
    // Se recalcula cuando llega el feed o los banners; no cada minuto para no reiniciar
    // el carrusel mientras el usuario lo está mirando.
    val heroSlides = remember(feed?.hero, feed?.sports, heroBanners) {
        composeHeroSlides(
            banners = heroBanners,
            feedHero = feed?.hero.orEmpty(),
            sports = feed?.sports.orEmpty(),
            nowMs = System.currentTimeMillis(),
        )
    }

    // Un Inicio de sistema siempre abre donde el usuario lo dejó configurado (o en Inicio):
    // el botón Home, en cambio, siempre vuelve al Inicio para no sorprender.
    var tab by rememberSaveable { mutableStateOf(startTabFor(config.startTab, prefs.lastTab)) }
    var commandCenter by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf<LauncherSettingsSection?>(null) }
    // Se recalcula al abrir el panel o al volver del sistema: si acaba de aceptar el rol,
    // muestra el estado correcto y deja la puerta manual para cambiar al otro launcher.
    var resumeTick by remember { mutableIntStateOf(0) }
    val homeDefault = remember(commandCenter, settingsSection, resumeTick) {
        if (commandCenter || settingsSection != null) appsRepo.isVisionHomeDefault() else false
    }
    // Quién abre la tele HOY (para decirlo sin mentir) y la guía de 3 pasos si el firmware no
    // tiene selector de Inicio.
    val currentHome = remember(commandCenter, settingsSection, resumeTick) {
        if (commandCenter || settingsSection != null) appsRepo.currentHomeLabel() else null
    }
    var homeGuide by remember { mutableStateOf<String?>(null) }
    fun requestHome() {
        commandCenter = false
        if (appsRepo.planHomeChange() == HomeRoleAction.OPEN_CURRENT_LAUNCHER) {
            homeGuide = appsRepo.currentHomeLabel() ?: "el launcher actual"
        } else {
            appsRepo.requestHomeRole()
        }
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
        heroBanners = BannersRepository.cached(context)
        // Las entradas del televisor son del sistema, no de la red: se leen ya.
        inputs = tvInputs.inputs()
        inputsLoaded = true
        system = LauncherSystemInfo(
            device = appsRepo.deviceLabel(),
            network = appsRepo.networkLabel(),
            storage = appsRepo.storageLabel(),
        )
        feeds.refresh()?.let { feed = it }
        feedAttempted = true
        // La versión del release (cacheada 24 h) decide si Vision+ se quedó atrás.
        latestApp = HomeUpdate.latestAppVersion(context)
    }

    // La pestaña se recuerda para quien elija «la última que usaste» al encender.
    LaunchedEffect(tab) {
        prefs.lastTab = tab.key
    }

    // Si la preferencia apuntaba a una pestaña que este equipo no tiene (TV box sin entradas)
    // o que la persona ocultó, el Inicio se resuelve a Inicio en vez de quedar en una pestaña
    // que no se ve. No pelea con el usuario: solo actúa cuando la pestaña dejó de existir.
    LaunchedEffect(inputsLoaded, inputsAvailable, availableTabs, tab) {
        if (!inputsLoaded) return@LaunchedEffect
        if (tab !in availableTabs) {
            tab = HomeTab.HOME
            userHasMoved = false
            landingRequest++
        }
    }

    // Los banners diarios no esperan al feed: cargan en paralelo para que el hero
    // tenga el arte del VPS desde el primer momento.
    LaunchedEffect(Unit) {
        BannersRepository.load(context)?.let { heroBanners = it }
    }

    // Vision+ avisa cuando cambia el historial/perfil y el Inicio se refresca solo.
    DisposableEffect(Unit) {
        val observer = feeds.observe {
            scope.launch { feeds.refresh()?.let { feed = it } }
        }
        onDispose { feeds.stopObserving(observer) }
    }

    // Instalar o desinstalar una app se ve al instante: un launcher no puede seguir mostrando
    // lo que ya no existe (ni esconder lo que acaba de llegar).
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                scope.launch {
                    apps = appsRepo.load(force = true)
                    recentApps = prefs.recentApps()
                    visionInstalled = appsRepo.visionPlusInstalled()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Al volver de una app o del reproductor, el minuto y el perfil son los de ahora.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeTick++
        scope.launch {
            feeds.refresh()?.let { feed = it }
            BannersRepository.load(context)?.let { heroBanners = it }
            apps = appsRepo.load(force = true)
            recentApps = prefs.recentApps()
            visionInstalled = appsRepo.visionPlusInstalled()
            canInstall = HomeUpdate.canInstall(context)
            // Un HDMI recién conectado aparece sin reiniciar el launcher.
            inputs = tvInputs.inputs()
            inputsLoaded = true
            system = LauncherSystemInfo(
                device = appsRepo.deviceLabel(),
                network = appsRepo.networkLabel(),
                storage = appsRepo.storageLabel(),
            )
            // Si el instalador se canceló, el botón vuelve a ofrecer la instalación.
            if (!visionInstalled && HomeUpdate.visionState is HomeUpdate.State.Ready) {
                HomeUpdate.resetVision()
            }
        }
    }

    // El Inicio también envejece (marcador en vivo, minutos, banners): se refresca solo
    // mientras está AL FRENTE. Al abrir una app o el reproductor, el ciclo de vida corta
    // estas llamadas y no se gasta red en segundo plano.
    // HOME cada 5 min (el provider tiene su propio freno) y TV/Deportes cada 2 min.
    LaunchedEffect(tab, lifecycleOwner) {
        val every = when (tab) {
            HomeTab.HOME -> 300_000L
            HomeTab.LIVE, HomeTab.EVENTS -> 120_000L
            else -> return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(every)
                feeds.refresh()?.let { feed = it }
                BannersRepository.load(context)?.let { heroBanners = it }
            }
        }
    }

    // El botón Home ya estando en el launcher: vuelve al Inicio, cierra paneles y sube al hero.
    LaunchedEffect(homeSignal) {
        if (homeSignal == 0) return@LaunchedEffect
        tab = HomeTab.HOME
        commandCenter = false
        settingsSection = null
        searchOpen = false
        userHasMoved = false
        landingRequest++
        runCatching { homeListState.scrollToItem(0) }
    }

    // Volver al hero con el mando: el minimal bring-into-view solo alcanzaba los botones y
    // dejaba la lista a medio scroll, así que el banner se veía cortado. Al enfocar el hero,
    // la lista se ancla al tope y se ve completo.
    LaunchedEffect(heroFocused) {
        if (heroFocused) runCatching { homeListState.scrollToItem(0) }
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

    // Red proactiva de foco: si el contenido cambia debajo del cursor (una fila que se
    // refresca, un canal que se oculta) el control enfocado desaparece y el Inicio se queda
    // sin foco. En vez de comerse la siguiente tecla, se devuelve el foco al destino de la
    // pestaña en cuanto se detecta. Nunca pelea: solo actúa si NADA tiene el foco.
    LaunchedEffect(tab, commandCenter, settingsSection, searchOpen, personalizePage, editTarget, homeGuide) {
        val panelOpen = commandCenter || settingsSection != null || searchOpen ||
            personalizePage != null || editTarget != null || homeGuide != null
        if (panelOpen) return@LaunchedEffect
        val target = if (tab == HomeTab.HOME) landing else sectionLanding
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(200)
                if (!screenHasFocus) requestFocusSafely(target)
            }
        }
    }

    // Atrás nunca cierra el sistema: como mucho vuelve a Inicio.
    BackHandler(enabled = true) {
        when {
            settingsSection != null -> settingsSection = null
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

    fun openSystemPage(opened: Boolean, name: String) {
        if (!opened) LauncherActions.toast(context, "Este equipo no tiene la pantalla de $name")
    }

    fun requestUninstall(app: AppEntry) {
        if (!appsRepo.requestUninstall(app.packageName)) {
            LauncherActions.toast(context, "No se pudo abrir el desinstalador")
        }
    }

    fun openInput(item: TvInputSource) {
        if (!tvInputs.open(item)) {
            LauncherActions.toast(context, "No se pudo abrir ${item.label}")
        }
    }

    fun refreshInputs() {
        scope.launch {
            inputs = tvInputs.inputs()
            LauncherActions.toast(
                context,
                if (inputs.isEmpty()) "Este equipo no reporta entradas de TV" else "${inputs.size} entradas encontradas",
            )
        }
    }

    fun openSystemPage(page: LauncherSystemPage) {
        val opened = when (page) {
            LauncherSystemPage.WIFI -> appsRepo.openWifiSettings()
            LauncherSystemPage.DISPLAY -> appsRepo.openDisplaySettings()
            LauncherSystemPage.SOUND -> appsRepo.openSoundSettings()
            LauncherSystemPage.STORAGE -> appsRepo.openStorageSettings()
            LauncherSystemPage.DATE -> appsRepo.openDateTimeSettings()
            LauncherSystemPage.LANGUAGE -> appsRepo.openLanguageSettings()
            LauncherSystemPage.ACCESSIBILITY -> appsRepo.openAccessibilitySettings()
            LauncherSystemPage.SCREENSAVER -> appsRepo.openScreensaverSettings()
            LauncherSystemPage.APP_LIST -> appsRepo.openAppListSettings()
            LauncherSystemPage.DEFAULT_APPS -> appsRepo.openDefaultAppsSettings()
            LauncherSystemPage.SETTINGS -> appsRepo.openSystemSettings()
        }
        if (!opened) LauncherActions.toast(context, "Este equipo no tiene la pantalla de ${page.label}")
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
    // El aviso de instalar/actualizar Vision+ vive ENTRE la barra superior y el contenido.
    // Sin un destino propio, el «abajo» de las pestañas saltaba al héroe y el «arriba» del
    // héroe volvía a la pestaña: sus botones quedaban inalcanzables con el mando.
    val bannerVisible = !visionInstalled || appOutdated
    val bannerFocus = remember { FocusRequester() }
    val contentLanding = if (tab == HomeTab.HOME) landing else sectionLanding
    val contentUpTarget: FocusRequester? = if (bannerVisible) bannerFocus else tabLanding
    val bannerPrimaryModifier = if (bannerVisible) {
        Modifier
            .focusRequester(bannerFocus)
            .focusProperties { up = tabLanding }
            .softFocusDown(contentLanding)
    } else {
        Modifier
    }

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
                downTarget = if (bannerVisible) bannerFocus else contentLanding,
                clock24h = config.display.clock24h,
                showClock = config.display.showClock,
                availableTabs = availableTabs,
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
                    primaryModifier = bannerPrimaryModifier,
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
                    primaryModifier = bannerPrimaryModifier,
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    HomeTab.HOME -> HomeSurface(
                        feed = feed,
                        hero = heroSlides,
                        apps = apps,
                        recentApps = recentApps,
                        inputs = inputs,
                        config = config,
                        metrics = metrics,
                        heroReduced = profile.lowRam,
                        compact = compact,
                        heroPaused = heroFocused,
                        landing = landing,
                        listState = homeListState,
                        tabLanding = contentUpTarget,
                        onHeroPlay = { slide -> openLink(slide.playLink.ifBlank { slide.openLink }) },
                        onHeroOpen = { slide -> openLink(slide.openLink.ifBlank { slide.playLink }) },
                        onOpenSports = { openLink(VisionLinks.sports()) },
                        onItem = { item -> openLink(item.openLink) },
                        onContinue = { item -> openLink(item.playLink) },
                        onLive = { item, channels, scope ->
                            // El canal viaja con SU menú: el reproductor hace zapping (y abre
                            // «Canales») solo dentro de la fila desde la que se abrió.
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
                        onChannelLongClick = { item -> editTarget = EditTarget.Channel(item) },
                        onApp = { app -> launchApp(app) },
                        onAppLongClick = { app -> editTarget = EditTarget.App(app) },
                        onInput = { item -> openInput(item) },
                        onSeeInputs = {
                            tab = HomeTab.INPUTS
                            userHasMoved = false
                            landingRequest++
                        },
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
                        upTarget = contentUpTarget,
                        landing = sectionLanding,
                    )

                    HomeTab.INPUTS -> InputsSurface(
                        inputs = inputs,
                        config = config,
                        metrics = metrics,
                        onOpen = { item -> openInput(item) },
                        onRefresh = { refreshInputs() },
                        onManage = {
                            settingsSection = LauncherSettingsSection.INPUTS
                        },
                        onOpenLive = {
                            tab = HomeTab.LIVE
                            userHasMoved = false
                            landingRequest++
                        },
                        upTarget = contentUpTarget,
                        landing = sectionLanding,
                    )

                    HomeTab.EVENTS -> EventsSurface(
                        events = feed?.sports.orEmpty(),
                        metrics = metrics,
                        onOpen = { event -> openLink(event.openLink) },
                        onOpenFull = { openLink(VisionLinks.sports()) },
                        upTarget = contentUpTarget,
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
                        upTarget = contentUpTarget,
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
                        onLauncherSettings = {
                            settingsSection = LauncherSettingsSection.STARTUP
                        },
                        onWifi = { appsRepo.openWifiSettings() },
                        onDisplay = {
                            openSystemPage(appsRepo.openDisplaySettings(), "pantalla")
                        },
                        onSound = {
                            openSystemPage(appsRepo.openSoundSettings(), "sonido")
                        },
                        onStorage = {
                            openSystemPage(appsRepo.openStorageSettings(), "almacenamiento")
                        },
                        onDate = {
                            openSystemPage(appsRepo.openDateTimeSettings(), "fecha y hora")
                        },
                        onAbout = {
                            openSystemPage(appsRepo.openAboutSettings(), "acerca del equipo")
                        },
                        onAppList = {
                            openSystemPage(appsRepo.openAppListSettings(), "apps del sistema")
                        },
                        onHomeSettings = { appsRepo.openHomeSettings() },
                        onRequestHome = { requestHome() },
                        onManage = { personalizePage = PersonalizePage.APPS },
                        landing = sectionLanding,
                        upTarget = contentUpTarget,
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
            onLauncherSettings = {
                commandCenter = false
                settingsSection = LauncherSettingsSection.STARTUP
            },
            onWatchTv = tunerOf(inputs)?.let { tuner ->
                {
                    commandCenter = false
                    openInput(tuner)
                }
            },
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
                requestHome()
            },
            homeDefault = homeDefault,
            currentHome = currentHome,
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

    settingsSection?.let { section ->
        LauncherSettingsDialog(
            section = section,
            profileName = feed?.meta?.profile.orEmpty(),
            config = config,
            inputs = inputs,
            homeVersion = HomeUpdate.versionName,
            visionInstalled = visionInstalled,
            visionVersion = feed?.meta?.appVersion.orEmpty(),
            latestVisionVersion = latestApp.orEmpty(),
            homeDefault = homeDefault,
            currentHome = currentHome,
            system = system,
            updateState = HomeUpdate.state,
            visionState = HomeUpdate.visionState,
            actions = LauncherSettingsActions(
                onDismiss = { settingsSection = null },
                onSection = { settingsSection = it },
                onStartTab = { start -> updateConfig { it.setStartTab(start) } },
                onToggleInputsRow = { updateConfig { it.toggleRow("inputs") } },
                onToggleInput = { id -> updateConfig { it.toggleInputHidden(id) } },
                onRefreshInputs = { refreshInputs() },
                onOpenInput = { item -> openInput(item) },
                onOpenLive = {
                    settingsSection = null
                    tab = HomeTab.LIVE
                    userHasMoved = false
                    landingRequest++
                },
                onPersonalize = {
                    settingsSection = null
                    personalizePage = PersonalizePage.HUB
                },
                onSystemPage = { page -> openSystemPage(page) },
                onRequestHome = {
                    settingsSection = null
                    requestHome()
                },
                onHomeSettings = {
                    settingsSection = null
                    appsRepo.openHomeSettings()
                },
                onUpdateHome = { scope.launch { HomeUpdate.installLatest(context) } },
                onUpdateVision = {
                    if (HomeUpdate.canInstall(context)) {
                        scope.launch { HomeUpdate.installVisionPlus(context) }
                    } else {
                        HomeUpdate.openInstallPermission(context)
                    }
                },
            ),
        )
    }

    homeGuide?.let { launcher ->
        HomeGuideDialog(
            currentLauncher = launcher,
            onOpenCurrentLauncher = {
                homeGuide = null
                appsRepo.openCurrentLauncherDetails()
            },
            onDismiss = { homeGuide = null },
        )
    }

    if (searchOpen) {
        SearchSurface(
            isTv = profile.isTv,
            repo = feeds,
            apps = apps,
            onApp = { app ->
                searchOpen = false
                launchApp(app)
            },
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
                val canOpen = appsRepo.launchIntent(app.packageName) != null
                ItemActionsDialog(
                    title = app.label,
                    subtitle = buildString {
                        append(app.packageName)
                        if (app.versionName.isNotBlank()) append(" · v").append(app.versionName)
                        append(if (app.isSystem) " · app del sistema" else " · instalada")
                    },
                    actions = buildList {
                        if (canOpen) {
                            add(
                                MenuAction(
                                    title = "Abrir",
                                    detail = "Abrir ahora",
                                    onClick = {
                                        editTarget = null
                                        launchApp(app)
                                    },
                                ),
                            )
                        }
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
                        // Las opciones de verdad de un launcher: la ficha del sistema y
                        // desinstalar sin salir a buscar Ajustes.
                        add(
                            MenuAction(
                                title = "Información de la app",
                                detail = "Permisos, almacenamiento, versión y forzar detención",
                                onClick = {
                                    editTarget = null
                                    if (!appsRepo.openAppInfo(app.packageName)) {
                                        LauncherActions.toast(context, "No se pudo abrir la información de ${app.label}")
                                    }
                                },
                            ),
                        )
                        if (!app.isSystem) {
                            add(
                                MenuAction(
                                    title = "Cerrar app",
                                    detail = "La detiene; se abre de nuevo cuando la uses",
                                    onClick = {
                                        editTarget = null
                                        if (appsRepo.stopApp(app.packageName)) {
                                            LauncherActions.toast(context, "${app.label} cerrada")
                                        } else {
                                            LauncherActions.toast(context, "No se pudo cerrar ${app.label}")
                                        }
                                    },
                                ),
                            )
                        }
                        if (app.canUninstall) {
                            add(
                                MenuAction(
                                    title = if (app.isSystem) "Quitar actualización" else "Desinstalar",
                                    detail = if (app.isSystem) {
                                        "Vuelve a la versión de fábrica de esa app"
                                    } else {
                                        "Abre el desinstalador de Android"
                                    },
                                    danger = true,
                                    onClick = {
                                        editTarget = null
                                        if (!appsRepo.requestUninstall(app.packageName)) {
                                            LauncherActions.toast(context, "No se pudo abrir el desinstalador")
                                        }
                                    },
                                ),
                            )
                        }
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
            onTabs = { personalizePage = PersonalizePage.TABS },
            onPresets = { personalizePage = PersonalizePage.PRESETS },
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
            onUninstall = { app -> requestUninstall(app) },
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
                onMove = { id, delta ->
                    updateConfig { cfg ->
                        // Las filas del feed (Top 10, Novedades…) no viven en la configuración
                        // hasta que se mueven: sin esto, sus flechas no hacían nada.
                        val withId = if (id in cfg.rowOrder) cfg else cfg.copy(rowOrder = cfg.rowOrder + id)
                        withId.moveRow(id, delta)
                    }
                },
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

        PersonalizePage.TABS -> {
            val tabs = remember(config.tabOrder, config.hiddenTabs) {
                val known = HomeTab.entries.associateBy { it.key }
                (config.tabOrder + HomeTab.entries.map { it.key }).distinct()
                    .mapNotNull { id -> known[id]?.let { TabEntry(it.key, it.label) } }
            }
            ManageTabsDialog(
                tabs = tabs,
                config = config,
                inputsAvailable = inputsAvailable,
                onMove = { id, delta ->
                    updateConfig { cfg ->
                        val withId = if (id in cfg.tabOrder) cfg else cfg.copy(tabOrder = cfg.tabOrder + id)
                        withId.moveTab(id, delta)
                    }
                },
                onToggle = { id -> updateConfig { it.toggleTab(id) } },
                onReset = {
                    updateConfig { it.resetTabs() }
                    LauncherActions.toast(context, "Pestañas restablecidas")
                },
                onDismiss = { personalizePage = PersonalizePage.HUB },
            )
        }

        PersonalizePage.PRESETS -> PresetsDialog(
            onApply = { preset ->
                updateConfig { it.withPreset(preset) }
                personalizePage = null
                LauncherActions.toast(context, "Plantilla «${preset.label}» aplicada")
            },
            onDismiss = { personalizePage = PersonalizePage.HUB },
        )

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
