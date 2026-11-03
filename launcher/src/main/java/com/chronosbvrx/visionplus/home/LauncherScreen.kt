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
import androidx.compose.foundation.layout.padding
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
import com.chronosbvrx.visionplus.home.data.composeHeroSlides
import com.chronosbvrx.visionplus.home.data.homeRowEntries
import com.chronosbvrx.visionplus.home.ui.AppsSurface
import com.chronosbvrx.visionplus.home.ui.CatalogSurface
import com.chronosbvrx.visionplus.home.ui.CommandCenterDialog
import com.chronosbvrx.visionplus.home.ui.DashboardBackdrop
import com.chronosbvrx.visionplus.home.ui.DisplaySettingsDialog
import com.chronosbvrx.visionplus.home.ui.EventsSurface
import com.chronosbvrx.visionplus.home.ui.HomeColors
import com.chronosbvrx.visionplus.home.ui.HomeGuideDialog
import com.chronosbvrx.visionplus.home.ui.HomeTab
import com.chronosbvrx.visionplus.home.ui.HomeSurface
import com.chronosbvrx.visionplus.home.ui.ItemActionsDialog
import com.chronosbvrx.visionplus.home.ui.LauncherBackToTopGuard
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsActions
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsDialog
import com.chronosbvrx.visionplus.home.ui.LauncherSettingsSection
import com.chronosbvrx.visionplus.home.ui.LauncherUpdateBanner
import com.chronosbvrx.visionplus.home.ui.LiveSurface
import com.chronosbvrx.visionplus.home.ui.VisionDestination
import com.chronosbvrx.visionplus.home.ui.ManageAppsDialog
import com.chronosbvrx.visionplus.home.ui.ManageChannelsDialog
import com.chronosbvrx.visionplus.home.ui.ManageRowsDialog
import com.chronosbvrx.visionplus.home.ui.MenuAction
import com.chronosbvrx.visionplus.home.ui.MissingVisionBanner
import com.chronosbvrx.visionplus.home.ui.OutdatedVisionBanner
import com.chronosbvrx.visionplus.home.ui.PersistentModeDialog
import com.chronosbvrx.visionplus.home.ui.PersonalizeHubDialog
import com.chronosbvrx.visionplus.home.ui.SearchSurface
import com.chronosbvrx.visionplus.home.ui.TopChrome
import com.chronosbvrx.visionplus.home.ui.backToTopNow
import com.chronosbvrx.visionplus.home.ui.currentProgram
import com.chronosbvrx.visionplus.home.ui.requestFocusSafely
import com.chronosbvrx.visionplus.home.ui.softFocusDown
import com.chronosbvrx.visionplus.home.rememberHomeMetrics
import com.chronosbvrx.visionplus.home.ui.LocalReducedEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LauncherPrefs(context) }
    val feeds = remember { FeedRepository(context) }
    val appsRepo = remember { AppsRepository(context) }

    var feed by remember { mutableStateOf<HomeFeed?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var recentApps by remember { mutableStateOf(prefs.recentApps()) }
    var heroBanners by remember { mutableStateOf<List<HeroSlide>>(emptyList()) }
    var network by remember { mutableStateOf("") }
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
    // El launcher es un escritorio, no un catálogo con pestañas: Inicio siempre es Inicio.
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
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
    var persistentPrompt by remember { mutableStateOf(false) }
    var persistentGuardian by remember { mutableStateOf(GuardianAccessibilityService.isEnabled(context)) }
    fun requestHome() {
        commandCenter = false
        if (appsRepo.planHomeChange() == HomeRoleAction.OPEN_CURRENT_LAUNCHER) {
            homeGuide = appsRepo.currentHomeLabel() ?: "el launcher actual"
        } else {
            appsRepo.requestHomeRole()
        }
        // Elegir el Inicio no siempre basta en Google TV: al volver se ofrece el guardián.
        if (!persistentGuardian) persistentPrompt = true
    }
    var searchOpen by remember { mutableStateOf(false) }
    var visionInstalled by remember { mutableStateOf(appsRepo.visionPlusInstalled()) }
    var canInstall by remember { mutableStateOf(HomeUpdate.canInstall(context)) }
    var latestApp by remember { mutableStateOf<String?>(null) }
    // Aviso de la actualización del PROPIO launcher: se descarta por versión (no vuelve a
    // molestar hasta que el release publique una versión más nueva).
    var dismissedLauncher by remember { mutableIntStateOf(prefs.dismissedUpdate) }
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
    // Atrás en dos pasos: el primero sube el foco a la barra superior (donde vive la
    // navegación) y el segundo, dentro de la ventana, vuelve a Inicio. Si el mando ya está en
    // la barra, Atrás actúa de una vez.
    val backToTop = remember { LauncherBackToTopGuard() }
    var topBarFocused by remember { mutableStateOf(false) }

    // Primer frame: caché propia y apps del sistema. Nada de red esperando.
    LaunchedEffect(Unit) {
        feed = feeds.cached()
        apps = appsRepo.load()
        visionInstalled = appsRepo.visionPlusInstalled()
        heroBanners = BannersRepository.cached(context)
        network = appsRepo.networkLabel()
        feeds.refresh()?.let { feed = it }
        feedAttempted = true
        // La versión del release (cacheada 24 h) decide si Vision+ se quedó atrás.
        latestApp = HomeUpdate.latestAppVersion(context)
    }

    // El launcher también se actualiza solo: lee la versión del APK publicado (1.5 MB,
    // cacheado 12 h) y solo ofrece instalar si es MÁS NUEVA que la que está corriendo.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2_500L)
        HomeUpdate.checkLauncher(context)
    }

    // La sección de Actualizaciones se comprueba al abrirla: la caché de 6 h puede ser de
    // antes de que se publicara una versión y la pantalla diría «última publicada» una vieja.
    LaunchedEffect(settingsSection) {
        if (settingsSection == LauncherSettingsSection.UPDATES) {
            HomeUpdate.checkLauncher(context, force = true)
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
            persistentGuardian = GuardianAccessibilityService.isEnabled(context)
            network = appsRepo.networkLabel()
            // Si el instalador se canceló, el botón vuelve a ofrecer la instalación.
            if (!visionInstalled && HomeUpdate.visionState is HomeUpdate.State.Ready) {
                HomeUpdate.resetVision()
            }
        }
    }

    // El reproductor escribe el minuto en el proceso :x4k al salir; el launcher puede
    // reanudarse unos milisegundos antes del commit. Un segundo refresco corto recoge ese
    // último cambio y evita que «Seguir viendo» se quede con la tarjeta anterior.
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(1_200L)
        feeds.refresh()?.let { feed = it }
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
        backToTop.disarm()
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

    // Atrás nunca cierra el sistema: el primer toque sube el foco a la barra superior (donde
    // vive la navegación) y el segundo, dentro de la ventana, vuelve a Inicio. Con el mando ya
    // en la barra, Atrás vuelve a Inicio de una vez (misma dinámica que Vision+).
    BackHandler(enabled = true) {
        when {
            settingsSection != null -> {
                settingsSection = null
                backToTop.disarm()
            }
            commandCenter -> {
                commandCenter = false
                backToTop.disarm()
            }
            searchOpen -> {
                searchOpen = false
                backToTop.disarm()
            }
            else -> {
                val now = backToTopNow()
                if (!topBarFocused && backToTop.press(now)) {
                    // La barra nueva es de sistema: Atrás sube al botón de Inicio del
                    // escritorio. Si no está compuesto, el mismo Atrás devuelve el Inicio
                    // en vez de comerse la tecla.
                    if (requestFocusSafely(tabLanding)) return@BackHandler
                    backToTop.disarm()
                }
                backToTop.disarm()
                if (tab != HomeTab.HOME) {
                    tab = HomeTab.HOME
                    userHasMoved = false
                    landingRequest++
                }
            }
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

    /** Cada baldosa del Bento abre la sección EXACTA de Vision+. */
    fun openVisionDestination(destination: VisionDestination) {
        val link = when (destination) {
            VisionDestination.LIVE -> VisionLinks.liveHub()
            VisionDestination.MOVIES -> VisionLinks.section("movies")
            VisionDestination.SERIES -> VisionLinks.section("series")
            VisionDestination.SPORTS -> VisionLinks.sports()
            VisionDestination.GAMES -> VisionLinks.games()
            VisionDestination.LIBRARY -> VisionLinks.library()
            VisionDestination.DOWNLOADS -> VisionLinks.downloads()
            VisionDestination.ANIME -> VisionLinks.section("anime")
            VisionDestination.NOVELAS -> VisionLinks.section("novelas")
        }
        openLink(link)
    }

    fun launchApp(app: AppEntry) {
        prefs.noteLaunch(app.packageName)
        recentApps = prefs.recentApps()
        if (!appsRepo.launch(app.packageName)) {
            LauncherActions.toast(context, "No se pudo abrir ${app.label}")
        }
    }

    fun requestUninstall(app: AppEntry) {
        if (!appsRepo.requestUninstall(app.packageName)) {
            LauncherActions.toast(context, "No se pudo abrir el desinstalador")
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
    // El aviso de instalar/actualizar Vision+ vive ENTRE la barra superior y el contenido.
    // Sin un destino propio, el «abajo» de las pestañas saltaba al héroe y el «arriba» del
    // héroe volvía a la pestaña: sus botones quedaban inalcanzables con el mando.
    val launcherLatest = HomeUpdate.launcherLatest
    val launcherUpdate = launcherLatest != null && HomeUpdate.launcherPending() &&
        launcherLatest.versionCode.toInt() != dismissedLauncher
    val bannerVisible = !visionInstalled || appOutdated || launcherUpdate
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
        // Fondo de escritorio: pizarra, retícula y luces de sistema. Nada de arte a sangre:
        // el destacado vive dentro de su tarjeta Bento.
        DashboardBackdrop()
        Column(Modifier.fillMaxSize()) {
            // La barra sabe si el mando está en ella: con el foco arriba, Atrás vuelve a Inicio
            // de una vez; desde el contenido, el primer Atrás sube aquí.
            Box(Modifier.fillMaxWidth().onFocusChanged { topBarFocused = it.hasFocus }) {
                TopChrome(
                    tab = tab,
                    profile = feed?.meta?.profile.orEmpty(),
                    network = network,
                    onTab = {
                        backToTop.disarm()
                        if (it != tab) {
                            tab = it
                            userHasMoved = false
                            landingRequest++
                        }
                    },
                    onSearch = { searchOpen = true },
                    onCommandCenter = { commandCenter = true },
                    onVisionSettings = { openLink(VisionLinks.settings()) },
                    compact = compact,
                    activeTabRequester = tabLanding,
                    downTarget = if (bannerVisible) bannerFocus else contentLanding,
                    clock24h = config.display.clock24h,
                    showClock = config.display.showClock,
                    hasNotifications = bannerVisible,
                )
            }
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
            } else if (launcherUpdate && launcherLatest != null) {
                LauncherUpdateBanner(
                    current = HomeUpdate.versionName,
                    latest = launcherLatest.versionName,
                    state = HomeUpdate.state,
                    canInstall = canInstall,
                    onUpdate = {
                        if (HomeUpdate.canInstall(context)) {
                            scope.launch { HomeUpdate.installLatest(context) }
                        } else {
                            HomeUpdate.openInstallPermission(context)
                        }
                    },
                    onDismiss = {
                        dismissedLauncher = launcherLatest.versionCode.toInt()
                        prefs.dismissedUpdate = dismissedLauncher
                    },
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
            HomeSurface(
                    feed = feed,
                    hero = heroSlides,
                    apps = apps,
                    recentApps = recentApps,
                    config = config,
                    metrics = metrics,
                    heroReduced = profile.lowRam,
                    compact = compact,
                    heroPaused = heroFocused,
                    landing = landing,
                    listState = homeListState,
                    tabLanding = contentUpTarget,
                    visionInstalled = visionInstalled,
                    onHeroPlay = { slide -> openLink(slide.playLink.ifBlank { slide.openLink }) },
                    onHeroOpen = { slide -> openLink(slide.openLink.ifBlank { slide.playLink }) },
                    onItem = { item -> openLink(item.openLink) },
                    onContinue = { item -> openLink(item.openLink.ifBlank { item.playLink }) },
                    onLive = { item, channels, scope ->
                        // El canal viaja con SU menú: el reproductor hace zapping (y abre
                        // «Canales») solo dentro de la fila desde la que se abrió.
                        prefs.rememberLive(item)
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
                    onVisionDestination = { openVisionDestination(it) },
                    onOpenHistory = { openLink(VisionLinks.library()) },
                    onOpenVision = { if (visionInstalled) openVision() else scope.launch { HomeUpdate.installVisionPlus(context) } },
                    onPrimaryFocus = { heroFocused = it },
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
            onLauncherSettings = {
                commandCenter = false
                settingsSection = LauncherSettingsSection.STARTUP
            },
            onPersonalize = {
                commandCenter = false
                personalizePage = PersonalizePage.HUB
            },
            onOpenVisionSettings = {
                commandCenter = false
                openLink(VisionLinks.settings())
            },
            onRequestHome = {
                requestHome()
            },
            homeDefault = homeDefault,
            currentHome = currentHome,
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
            homeVersion = HomeUpdate.versionName,
            visionInstalled = visionInstalled,
            visionVersion = feed?.meta?.appVersion.orEmpty(),
            latestVisionVersion = latestApp.orEmpty(),
            homeDefault = homeDefault,
            currentHome = currentHome,
            persistentActive = persistentGuardian,
            updateState = HomeUpdate.state,
            visionState = HomeUpdate.visionState,
            actions = LauncherSettingsActions(
                onDismiss = { settingsSection = null },
                onSection = { settingsSection = it },
                onOpenPersistent = { GuardianAccessibilityService.openSettings(context) },
                onPersonalize = {
                    settingsSection = null
                    personalizePage = PersonalizePage.HUB
                },
                onRequestHome = {
                    settingsSection = null
                    requestHome()
                },
                onHomeSettings = {
                    settingsSection = null
                    appsRepo.openHomeSettings()
                },
                onUpdateHome = { scope.launch { HomeUpdate.installLatest(context) } },
                onCheckHome = { scope.launch { HomeUpdate.checkLauncher(context, force = true) } },
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

    if (persistentPrompt) {
        PersistentModeDialog(
            onEnable = {
                persistentPrompt = false
                GuardianAccessibilityService.openSettings(context)
            },
            onDismiss = { persistentPrompt = false },
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
