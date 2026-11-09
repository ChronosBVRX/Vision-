package com.chronosbvrx.visionplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.CatalogSeed
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.config.RemoteConfig
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.gate.GateClient
import com.chronosbvrx.visionplus.data.homefeed.HomeFeedStore
import com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext
import com.chronosbvrx.visionplus.data.remote.RemoteBus
import com.chronosbvrx.visionplus.data.remote.RemoteKeys
import com.chronosbvrx.visionplus.data.remote.RemoteProto
import com.chronosbvrx.visionplus.data.remote.RemoteServer
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.xuper.EngineWarmup
import com.chronosbvrx.visionplus.ui.SearchSeed
import com.chronosbvrx.visionplus.ui.activation.ActivationScreen
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.Navigator
import com.chronosbvrx.visionplus.ui.PhoneApp
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.TvApp
import com.chronosbvrx.visionplus.ui.brand.VisionSplash
import com.chronosbvrx.visionplus.ui.components.LocalPosterActions
import com.chronosbvrx.visionplus.ui.components.PosterActionsController
import com.chronosbvrx.visionplus.ui.components.PosterActionsDialog
import com.chronosbvrx.visionplus.ui.donation.DonationPrompt
import com.chronosbvrx.visionplus.ui.donation.DonationPromptDialog
import com.chronosbvrx.visionplus.ui.launcherRouteFor
import com.chronosbvrx.visionplus.ui.resumeRoute
import com.chronosbvrx.visionplus.ui.liveRouteFor
import com.chronosbvrx.visionplus.ui.player.PipMode
import com.chronosbvrx.visionplus.ui.player.PipSession
import com.chronosbvrx.visionplus.ui.showsFloatingClock
import com.chronosbvrx.visionplus.ui.systemAnimationsOff
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import com.chronosbvrx.visionplus.ui.tv.FocusRescue
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long the boot screen waits at most for the first catalogue answer. */
private const val BOOT_CAP_MS = 9_000L

/** A partir de este ms de arranque en frío se reporta `BOOT_SLOW` con los ms por fase. */
private const val BOOT_SLOW_TRACE_MS = 6_000L

class MainActivity : ComponentActivity() {
    private var isTv = false

    /** Inicio del arranque en frío milisegundos de reloj estable (telemetría `BOOT_SLOW`). */
    private var bootStartedMs = 0L

    /** El back stack vive aquí: la tarjeta del launcher entra como ruta desde el intent. */
    private val nav = Navigator(listOf(Route.Home))

    /** El servidor del control remoto; solo existe en la televisión. */
    private var remoteServer: RemoteServer? = null

    /** PiP del reproductor ExoPlayer (deportes, archivo/M3U y búnker): acción play/pausa. */
    private var pipReceiver: BroadcastReceiver? = null

    /** True cuando el Inicio ya se pintó al menos una vez (no adelantar el motor en el arranque). */
    @Volatile private var appBooted = false

    /**
     * A D-pad key no view handled (onKeyDown only sees those), with nothing focused on the TV screen: the screen's landing
     * element takes the focus, so the remote never ends up doing nothing.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isTv && keyCode in DPAD_KEYS && FocusRescue.rescue()) return true
        return super.onKeyDown(keyCode, event)
    }

    /** La tarjeta del launcher (`visionplus://watch/…`) mientras la app ya estaba abierta. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // `pushPlayer` reemplaza el reproductor en curso: si el teléfono manda algo mientras
        // se está viendo otra cosa, el play viejo no se queda apilado (ni revive al volver).
        launcherIntentRoute(intent)?.let { nav.pushPlayer(it) }
    }

    /** El control remoto solo le habla a la pantalla que está al frente. */
    private val remoteSink = object : RemoteBus.Sink {
        override fun onRemoteKey(code: Int, action: String) {
            // El servidor atiende en un hilo de red: la tecla se entrega en el hilo de la UI.
            runOnUiThread { deliverRemoteKey(code, action) }
        }
    }

    private fun deliverRemoteKey(code: Int, action: String) {
        if (RemoteKeys.isVolume(code)) {
            applyVolume(code)
            return
        }
        // Atrás usa el despachador real (respeta los dos pasos y el diálogo de cierre).
        if (code == KeyEvent.KEYCODE_BACK) {
            runCatching { onBackPressedDispatcher.onBackPressed() }
            return
        }
        // Si esta actividad ya no está al frente (el reproductor tomó la pantalla), la tecla
        // viaja al proceso :x4k: jamás a un menú invisible.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            RemoteBus.broadcastKey(this, code, action)
            return
        }
        dispatchRemoteKey(code, action)
    }

    override fun onResume() {
        super.onResume()
        com.chronosbvrx.visionplus.data.diag.Diagnostics.activityResumed()
        RemoteBus.attach(remoteSink)
        // Al volver del sistema (otra app, pantalla apagada) la red pudo cambiar sin que el
        // callback lo alcance: se revalida antes de pintar, para entrar/salir del Modo Sin
        // Conexión sin un frame de catálogo condenado a fallar.
        com.chronosbvrx.visionplus.data.offline.NetworkMonitor.refresh(this)
        // Google TV: al volver a la app, «Continuar viendo» se sincroniza con el historial.
        LauncherWatchNext.syncAsync(this, delayMs = 3_000L)
        // Vision+ Home (el launcher): el feed se refresca con lo último visto, el perfil y
        // el EPG. Con freno propio y en segundo plano; jamás frena este onResume.
        HomeFeedStore.refreshAsync(this)
        // Continuidad entre equipos: empuja el minuto/favoritos y trae lo del otro equipo.
        com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(this)
        // Si el proceso `:x4k` murió mientras la app estaba en segundo plano (o el launcher
        // ya soltó su vínculo), se vuelve a calentar en segundo plano. En 1.5 GB la política
        // lo omite y se conserva el camino diferido.
        if (isTv && appBooted && EngineWarmup.shouldWarm(this) && !EngineWarmup.isWarmFresh(this)) {
            EngineWarmup.requestWarm(this)
        }
        updatePipParams()
    }

    override fun onPause() {
        com.chronosbvrx.visionplus.data.diag.Diagnostics.activityPaused()
        RemoteBus.detach(remoteSink)
        super.onPause()
    }

    /** Android 8–11: al salir de la app con un reproductor ExoPlayer activo. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT < 31 &&
            !isTv && PipSession.canEnter(this) && !PipMode.isActivityInPip(this)
        ) {
            val playing = runCatching { PipSession.current()?.playing?.invoke() == true }.getOrDefault(false)
            runCatching { PipMode.enter(this, playing) }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipSession.markInPip(isInPictureInPictureMode)
        updatePipParams()
    }

    /**
     * Refleja la sesión del reproductor en los parámetros del sistema. En Android 12+ con
     * `autoEnter` el usuario solo tiene que salir de la app; en 8–11 lo cubre `onUserLeaveHint`.
     */
    private fun updatePipParams() {
        if (isTv || !PipMode.isSupported(this)) return
        val canEnter = runCatching { PipSession.canEnter(this) }.getOrDefault(false)
        val playing = runCatching { PipSession.current()?.playing?.invoke() == true }.getOrDefault(false)
        runCatching { PipMode.update(this, playing, autoEnter = canEnter) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isTv) hideSystemBars()
    }

    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            remoteServer?.stop()
            remoteServer = null
        }
        PipMode.unregister(this, pipReceiver)
        pipReceiver = null
        if (isFinishing) PipSession.clear()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        bootStartedMs = android.os.SystemClock.uptimeMillis()
        if (com.chronosbvrx.visionplus.BuildConfig.DEBUG) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Identidad propia automática (cero códigos para el usuario): si el servidor
        // ya conoce al equipo lo reclama; si hay pool libre, le asigna el más viejo.
        // Alcance manual en IO (sin lifecycle-runtime-ktx en el proyecto).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                com.chronosbvrx.visionplus.data.gate.SlotClient.ensureSlot(this@MainActivity)
            }
        }
        enableEdgeToEdge()
        isTv = DeviceKind.isTelevision(this)
        // En la tele no hay barra de estado/navegación: el Inicio va a pantalla completa
        // (igual que Vision+ Home). En el móvil se conservan.
        if (isTv) hideSystemBars()
        // Anuncio LAN de «qué está reproduciendo» (mDNS + HTTP de solo lectura). Se arranca
        // desde la actividad (en primer plano) y el servicio lo mantiene si la app pasa atrás.
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(
                    this,
                    com.chronosbvrx.visionplus.data.netshare.NowPlayingService::class.java,
                ),
            )
        }
        // Latido de la apertura: denominador del «% de sesiones sin crash» en la telemetría.
        com.chronosbvrx.visionplus.data.diag.Diagnostics.sessionStart()
        com.chronosbvrx.visionplus.ui.settings.VisionSettings.init(this)
        com.chronosbvrx.visionplus.data.offline.OfflineRegistry.init(this)
        com.chronosbvrx.visionplus.data.offline.NetworkMonitor.init(this)
        // Control remoto por LAN: solo la tele escucha (el teléfono es el que manda órdenes).
        if (isTv) {
            remoteServer = RemoteServer(this).also { runCatching { it.start() } }
        }
        // PiP solo en móvil: el reproductor ExoPlayer vive dentro de esta actividad y necesita
        // un receptor para el play/pausa del cuadro. En la tele no se registra nada.
        if (!isTv && PipMode.isSupported(this)) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == PipMode.ACTION_TOGGLE) PipSession.toggle()
                }
            }
            pipReceiver = receiver
            runCatching { PipMode.register(this, receiver) }
        }
        // Tarjeta del launcher en arranque en frío: la película/capítulo entra en la ruta base.
        if (savedInstanceState == null) {
            launcherIntentRoute(intent)?.let { nav.pushPlayer(it) }
        }
        val deepLinked = nav.stack.size > 1

        setContent {
            VisionTheme {
                CompositionLocalProvider(LocalReducedEffects provides DevicePerformance.shouldReduceEffects(this, com.chronosbvrx.visionplus.ui.settings.VisionSettings.effectsMode)) {
                    // Menú contextual de pósters (OK largo): una sola instancia para toda la app.
                    val posterActions = remember { PosterActionsController() }
                    // Tele con varias personas: una vez por arranque en frío, preguntar quién ve.
                    // En móvil no se interrumpe: el chip de perfil del menú basta.
                    var showProfileGate by rememberSaveable {
                        mutableStateOf(
                            savedInstanceState == null && isTv &&
                                com.chronosbvrx.visionplus.data.profile.ProfileStore
                                    .profiles(this@MainActivity).size > 1,
                        )
                    }
                    // Apoyo voluntario: una vez al día, nunca encima del splash, la activación,
                    // el selector de perfiles ni un deep link que ya viene a reproducir.
                    var showDonation by rememberSaveable { mutableStateOf(false) }
                    // Editar el perfil desde el selector del arranque también refresca el chip
                    // de la barra superior (el diálogo interno ya lo hacía con su propio tick).
                    var profileRevision by remember { mutableStateOf(0) }
                    var needsActivation by rememberSaveable {
                        mutableStateOf(ACTIVATION_REQUIRED && !GateClient.isActivated(this@MainActivity))
                    }
                    var splashVisible by rememberSaveable { mutableStateOf(true) }
                    var bootReady by remember { mutableStateOf(false) }
                    // Un respiro extra tras cargar los datos: el Inicio ya se compone detrás del
                    // splash y las portadas/hero alcanzan a pintarse antes de que el logo suba.
                    var splashExitReady by remember { mutableStateOf(false) }
                    var warming by remember { mutableStateOf(false) }
                    val navigator = remember { nav }
                    val catalog = remember { XuperCatalog(this@MainActivity) }
                    val isOnline by com.chronosbvrx.visionplus.data.offline.NetworkMonitor.isOnline.collectAsState()

                    // El reloj del menú: la tele lo lleva como píldora flotante arriba al
                    // centro; el móvil lo lleva como texto pequeño dentro de su barra superior
                    // (`PhoneApp`), para que no estorbe ni se monte sobre la navegación.
                    val clockRoute = navigator.current
                    // El reloj flotante ya no se monta sobre la barra superior: la hora vive
                    // DENTRO de la barra (como en Vision+ Home). Solo queda en las pantallas
                    // que no llevan barra (ficha, ajustes).
                    val showClock = !needsActivation && !showProfileGate && showsFloatingClock(isTv, clockRoute) &&
                        !com.chronosbvrx.visionplus.ui.tv.tvTopBarVisible(clockRoute)

                    // Salida con confirmación desde la pantalla principal: el primer Atrás
                    // avisa («presiona de nuevo»), el segundo dentro de la ventana cierra la
                    // app de verdad — llevándose al reproductor residente para que el sistema
                    // no lo traiga al frente (el bucle reportado: Atrás → reproductor → Atrás…).
                    val exitGuard = remember { com.chronosbvrx.visionplus.ui.BackExitGuard() }
                    var exitHint by remember { mutableStateOf(false) }
                    var exitPresses by remember { mutableIntStateOf(0) }
                    // La salida con aviso es la MISMA para el Atrás del shell (TvApp) y para el
                    // respaldo de aquí: en el Inicio el primer Atrás sube el foco a la barra y
                    // arma el aviso; el segundo cierra. En el móvil se comporta como siempre.
                    val requestExit: () -> Unit = {
                        if (exitGuard.press(System.currentTimeMillis())) {
                            exitHint = false
                            com.chronosbvrx.visionplus.ui.exitAppCleanly(this@MainActivity)
                        } else {
                            exitHint = true
                            exitPresses++
                        }
                    }
                    BackHandler(enabled = !needsActivation && navigator.stack.size <= 1) {
                        requestExit()
                    }
                    LaunchedEffect(exitPresses) {
                        if (exitPresses == 0) return@LaunchedEffect
                        delay(com.chronosbvrx.visionplus.ui.BACK_EXIT_WINDOW_MS)
                        if (!exitGuard.hintVisible(System.currentTimeMillis())) exitHint = false
                    }

                    // Si el dispositivo arranca sin internet, abrimos en 600 ms directo a la interfaz
                    // especial de Modo Sin Conexión sin esperar los 9 segundos de timeout de red.
                    LaunchedEffect(Unit) {
                        val minSplash = launch { delay(600L) }
                        var hitCap = false
                        var warmOutcome: EngineWarmup.WarmOutcome? = null
                        // El motor se calienta EN PARALELO con todo lo demás, desde el primer
                        // frame del splash: no espera a la sonda de red ni al catálogo. Solo en
                        // TV con RAM suficiente (2 GB+); los equipos de 1.5 GB conservan el
                        // camino diferido de siempre. Si Vision+ Home ya lo calentó,
                        // `awaitReadyOrWarm` vuelve al instante y no se espera nada.
                        val warmJob = if (!deepLinked && !needsActivation) {
                            val shouldWait = EngineWarmup.shouldWarmOnSplash(this@MainActivity) &&
                                !EngineWarmup.isWarmFresh(this@MainActivity)
                            async {
                                if (shouldWait) warming = true
                                val outcome = runCatching {
                                    EngineWarmup.awaitReadyOrWarm(
                                        this@MainActivity,
                                        EngineWarmup.splashTimeoutMs(this@MainActivity),
                                    )
                                }.getOrNull()
                                warming = false
                                outcome ?: EngineWarmup.WarmOutcome(
                                    result = EngineWarmup.Result.SKIPPED,
                                    source = "error",
                                    alreadyWarm = false,
                                    elapsedMs = 0L,
                                )
                            }
                        } else null
                        // La sonda de salida corre en IO (nunca frena el hilo principal): con una
                        // red que dice «internet» pero no llega a ningún lado, se abre directo el
                        // Modo Sin Conexión en vez de un Inicio vacío.
                        val onlineAtBoot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.chronosbvrx.visionplus.data.offline.NetworkMonitor.verify(this@MainActivity)
                        }
                        if (onlineAtBoot) {
                            if (deepLinked) {
                                // La tarjeta del launcher manda: el catálogo carga detrás, sin
                                // hacer esperar a la película que la persona ya eligió.
                                launch { runCatching { catalog.load() } }
                            } else {
                                hitCap = withTimeoutOrNull(BOOT_CAP_MS) {
                                    catalog.load()
                                } == null
                            }
                            warmOutcome = warmJob?.await()
                            // Con el índice fresco, Vision+ Home estrena filas reales.
                            HomeFeedStore.refreshAsync(this@MainActivity)
                        }
                        minSplash.join()
                        bootReady = true
                        appBooted = true
                        // Telemetría del arranque en frío: si abrir el Inicio tardó más de lo
                        // justo se reporta con los ms de cada fase (el diagnóstico aclarará si
                        // la lentitud es del arranque del sistema o de nuestra carga). El
                        // handicap del reloj: el primer frame del splash ya contaba.
                        val bootMs = android.os.SystemClock.uptimeMillis() - bootStartedMs
                        val warmMs = warmOutcome?.elapsedMs ?: 0L
                        if (bootMs > BOOT_SLOW_TRACE_MS) {
                            runCatching {
                                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                    "BOOT_SLOW",
                                    "bootMs=$bootMs hitCap=$hitCap warm=${warmOutcome?.result?.name ?: "none"} " +
                                        "warmMs=$warmMs warmSource=${warmOutcome?.source ?: "-"} " +
                                        catalog.lastLoadTimings.take(300),
                                )
                            }
                        }
                        if (warmOutcome?.result == EngineWarmup.Result.TIMEOUT) {
                            runCatching {
                                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                    "ENGINE_WARM_TIMEOUT",
                                    "ms=$warmMs ramMb=${DevicePerformance.totalMemoryMb(this@MainActivity)} " +
                                        "source=${warmOutcome?.source ?: "-"}",
                                )
                            }
                        } else if (warmOutcome?.let { it.result == EngineWarmup.Result.READY && !it.alreadyWarm } == true) {
                            android.util.Log.i(
                                "EngineWarmup",
                                "splash warm ready ms=$warmMs source=${warmOutcome?.source ?: "-"}",
                            )
                            if (warmMs > 4_000L) {
                                runCatching {
                                    com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                        "ENGINE_WARM_SLOW",
                                        "ms=$warmMs source=${warmOutcome?.source ?: "-"} " +
                                            "ramMb=${DevicePerformance.totalMemoryMb(this@MainActivity)}",
                                    )
                                }
                            }
                        }
                        if (com.chronosbvrx.visionplus.data.offline.NetworkMonitor.isOnline.value) {
                            launch(kotlinx.coroutines.Dispatchers.IO) {
                                delay(4_000L)
                                AppUpdate.check(this@MainActivity, force = true)
                            }
                        }
                    }

                    // El Inicio ya está compuesto detrás del splash: le damos un instante a las
                    // imágenes para que el logo se deslice y las portadas estén ahí, no un esqueleto.
                    LaunchedEffect(bootReady) {
                        if (!bootReady) return@LaunchedEffect
                        delay(if (deepLinked) 0L else 420L)
                        splashExitReady = true
                    }

                    // Al cerrar el selector del arranque, el Inicio retoma el mando sin exigir una
                    // pulsación extra: el rescate verificado enfoca su destino real.
                    LaunchedEffect(showProfileGate) {
                        if (!showProfileGate && bootReady) {
                            delay(180)
                            runCatching { FocusRescue.rescue() }
                        }
                    }

                    // Cuando regresa la conexión a internet, cargamos el catálogo automáticamente
                    LaunchedEffect(isOnline) {
                        if (isOnline && bootReady && catalog.rows.isEmpty()) {
                            catalog.load()
                        }
                    }

                    // Apoyo voluntario: una sola vez al día y con la app ya usable. No aparece
                    // sobre el splash, la activación, el selector de perfiles ni un deep link.
                    LaunchedEffect(bootReady, splashVisible, needsActivation, showProfileGate, deepLinked) {
                        if (!bootReady || splashVisible || needsActivation || showProfileGate || deepLinked) {
                            return@LaunchedEffect
                        }
                        // Se enciende desde el servidor cuando los Payment Links ya están
                        // publicados; así una OTA nunca enseña una página de pagos vacía.
                        if (!RemoteConfig.flag(this@MainActivity, "donation_prompt", false)) {
                            return@LaunchedEffect
                        }
                        delay(2_500L)
                        if (DonationPrompt.shouldShow(this@MainActivity)) {
                            DonationPrompt.markShown(this@MainActivity)
                            showDonation = true
                        }
                    }

                    // La sesión del reproductor (alta/baja o play/pausa) actualiza las acciones
                    // y la entrada automática al PiP, sin crecer la firma de PlayerCore.
                    LaunchedEffect(PipSession.revision) { updatePipParams() }

                    // Al salir de un reproductor interno (ExoPlayer vive en esta actividad) y al
                    // navegar, el canal «Continuar viendo» del launcher se pone al día solo.
                    LaunchedEffect(nav.stack.size) {
                        if (nav.current !is Route.Player && nav.current !is Route.LivePlayer) {
                            LauncherWatchNext.syncAsync(this@MainActivity, delayMs = 1_500L)
                        }
                    }
                    CompositionLocalProvider(LocalPosterActions provides posterActions) {
                    Box(Modifier.fillMaxSize().background(VisionBlack)) {
                        // El selector de perfiles del arranque vive en la misma composición que el
                        // Inicio (no en otra ventana): el splash se desliza hacia arriba y deja
                        // ver las portadas con los perfiles encima, sin salto de pantalla.
                        val profileGateOpen = showProfileGate && bootReady && !needsActivation
                        if (needsActivation) {
                            ActivationScreen(
                                isTv = isTv,
                                onActivated = { needsActivation = false },
                            )
                        } else if (!splashVisible || bootReady) {
                            // En Android 12+ el Inicio se ve ligeramente desenfocado detrás del
                            // cristal; en equipos modestos solo queda el velo del 75 %.
                            val blurRadius by animateDpAsState(
                                targetValue = if (
                                    isTv && profileGateOpen &&
                                    android.os.Build.VERSION.SDK_INT >= 31 &&
                                    !DevicePerformance.isLowEndDevice(this@MainActivity)
                                ) {
                                    16.dp
                                } else {
                                    0.dp
                                },
                                animationSpec = tween(280),
                                label = "profiles-gate-blur",
                            )
                            val backgroundBlur = if (
                                android.os.Build.VERSION.SDK_INT >= 31 && blurRadius > 0.dp
                            ) {
                                Modifier.blur(radius = blurRadius)
                            } else {
                                Modifier
                            }
                            Box(Modifier.fillMaxSize().then(backgroundBlur)) {
                                if (isTv) {
                                    TvApp(catalog, navigator, onExitToLauncher = {
                                        com.chronosbvrx.visionplus.ui.exitAppCleanly(this@MainActivity)
                                    }, onExitRequest = requestExit, profileRevision = profileRevision)
                                } else {
                                    PhoneApp(catalog, navigator) {
                                        com.chronosbvrx.visionplus.ui.exitAppCleanly(this@MainActivity)
                                    }
                                }
                            }
                        }
                        // Se dibuja ANTES del splash para que el splash lo revele al subir.
                        if (profileGateOpen) {
                            com.chronosbvrx.visionplus.ui.tv.TvProfilesOverlay(
                                onDismiss = { showProfileGate = false },
                                onProfileChanged = { profileRevision++ },
                                title = "Perfiles",
                            )
                        }
                        if (splashVisible) {
                            VisionSplash(
                                isTv = isTv,
                                canExit = splashExitReady,
                                status = if (warming) "Preparando el reproductor..." else null,
                                onFinished = { splashVisible = false },
                            )
                        }
                        // El aviso del cierre con confirmación: sin foco y sin toques (jamás
                        // se roba el D-pad), encima de lo que esté en pantalla.
                        AnimatedVisibility(
                            visible = exitHint,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isTv) 56.dp else 120.dp),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.86f))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(50))
                                    .padding(horizontal = 22.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    "Presiona atrás de nuevo para cerrar la aplicación",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                )
                            }
                        }
                        // La píldora del reloj de la Smart TV, arriba al centro: hora del
                        // centro de México, sin foco (el D-pad nunca cae en él). En el móvil
                        // la hora vive dentro de la barra superior, no flotando encima.
                        if (showClock) {
                            com.chronosbvrx.visionplus.ui.VisionClockBadge(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 14.dp),
                            )
                        }
                        // Lo que la tele muestra, para que el control remoto lo refleje.
                        val remoteRoute = navigator.current
                        LaunchedEffect(remoteRoute) { RemoteBus.publishScreen(remoteRouteLabel(remoteRoute)) }
                        // El PIN de emparejamiento: solo aparece cuando un teléfono lo pidió
                        // desde la red local. Sin foco, para no robarle el D-pad a nadie.
                        RemoteBus.pairingPin.value?.let { pin ->
                            RemotePairingPin(pin, isTv)
                        }
                        // Menú contextual de una tarjeta (OK largo / pulsación larga): ficha,
                        // favoritos y «Ver más tarde» desde donde esté mirando.
                        PosterActionsDialog(
                            film = posterActions.film,
                            card = posterActions.resumeCard,
                            onDismiss = { posterActions.dismiss() },
                            onOpenFilm = { film ->
                                posterActions.dismiss()
                                navigator.push(Route.Title(film))
                            },
                            onResume = { card ->
                                posterActions.dismiss()
                                navigator.push(resumeRoute(card))
                            },
                            onChanged = { catalog.refreshPersonal() },
                        )
                        if (showDonation && !needsActivation && !splashVisible && bootReady && !showProfileGate) {
                            DonationPromptDialog(
                                isTv = isTv,
                                onDismiss = { showDonation = false },
                                onNever = {
                                    DonationPrompt.never(this@MainActivity)
                                    showDonation = false
                                },
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    /**
     * The catalog, remembered for the activity; the screens read its observable state, so they
     * fill in as soon as the source answers. Its load happens behind the splash.
     */
    @Composable
    private fun rememberCatalog(): XuperCatalog = remember { XuperCatalog(this) }

    /** La tarjeta del launcher llega como ACTION_VIEW con esquema `visionplus://watch/…`. */
    private fun launcherIntentRoute(intent: Intent?): Route? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        // El QR/puente abre el mando dentro de la app antes que el navegador.
        if ("play".equals(uri.host, ignoreCase = true)) {
            val room = uri.getQueryParameter("room") ?: uri.pathSegments.getOrNull(1)
            if (!room.isNullOrBlank()) {
                return Route.PartyController(room.trim().uppercase().take(4))
            }
        }
        // Vision+ Home: destinos del sistema (ajustes, buscador, Inicio) y fichas.
        if ("open".equals(uri.host, ignoreCase = true)) return openRouteFor(uri)
        if ("title".equals(uri.host, ignoreCase = true)) return titleRouteFor(uri)
        // El control remoto y nuestro propio launcher abren canales con `visionplus://live/<código>`.
        if ("live".equals(uri.host, ignoreCase = true)) return liveRouteFor(this, uri)
        return launcherRouteFor(this, uri)
    }

    /** `visionplus://open/{home|settings|search|live|library|section/<name>}`: hubs del launcher. */
    private fun openRouteFor(uri: Uri): Route? = when (uri.pathSegments.firstOrNull()?.lowercase()) {
        "settings" -> Route.Settings
        "search" -> {
            SearchSeed.seed(uri.getQueryParameter("q").orEmpty())
            Route.Search
        }
        "live" -> Route.Live
        "library" -> Route.Library
        "games" -> Route.Games
        "downloads" -> Route.Downloads
        "sports" -> {
            // El launcher manda el partido: la pantalla de Deportes abre su selector.
            uri.getQueryParameter("event")?.takeIf { it.isNotBlank() }?.let { event ->
                com.chronosbvrx.visionplus.ui.sports.DeportesReturn.pickingId = event
            }
            Route.Sports
        }
        "section" -> uri.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { name ->
            Route.Section(name, sectionTitle(name))
        }
        "home" -> Route.Home
        else -> null
    }

    /** El título que la app le da a cada sección del launcher. */
    private fun sectionTitle(name: String): String = when (name) {
        "movies" -> "Películas"
        "series" -> "Series"
        "anime" -> "Anime"
        "novelas" -> "Novelas"
        "kids" -> "Para chicos"
        else -> name.replaceFirstChar { it.uppercase() }
    }

    /**
     * `visionplus://title/<ref>?t=&p=&u=`: la ficha de una tarjeta del launcher. Se resuelve
     * contra el índice cacheado; si aún no está (arranque en frío), se arma con lo que trae el
     * enlace y la ficha se hidrata sola al abrir.
     */
    private fun titleRouteFor(uri: Uri): Route? {
        val ref = uri.pathSegments.lastOrNull().orEmpty().trim()
        if (ref.isBlank()) return null
        val cached = runCatching { RemoteIndexSource.cached(this) }.getOrDefault(emptyList())
        val hit = cached.asSequence()
            .flatMap { it.films.asSequence() }
            .firstOrNull { it.id == ref || it.videoUrl == ref }
        if (hit != null) return Route.Title(hit)
        val title = uri.getQueryParameter("t").orEmpty()
        if (title.isBlank()) return null
        val videoUrl = uri.getQueryParameter("u").orEmpty().ifBlank { ref }
        val film = Film(
            id = ref,
            title = title,
            year = 0,
            durationMin = 0,
            synopsis = "",
            posterUrl = uri.getQueryParameter("p").orEmpty(),
            videoUrl = videoUrl,
            sourceUrl = if (videoUrl.startsWith("xtv:")) "xtv" else "",
        )
        return Route.Title(film)
    }

    /** Entrega una tecla remota al sistema como si viniera del mando. */
    private fun dispatchRemoteKey(code: Int, action: String) {
        val down = SystemClock.uptimeMillis()
        fun ev(a: Int) = KeyEvent(down, SystemClock.uptimeMillis(), a, code, 0)
        runCatching {
            when (action) {
                RemoteProto.ACTION_DOWN -> dispatchKeyEvent(ev(KeyEvent.ACTION_DOWN))
                RemoteProto.ACTION_UP -> dispatchKeyEvent(ev(KeyEvent.ACTION_UP))
                else -> {
                    dispatchKeyEvent(ev(KeyEvent.ACTION_DOWN))
                    dispatchKeyEvent(ev(KeyEvent.ACTION_UP))
                }
            }
        }
    }

    /** El volumen lo mueve el sistema (el motor de video no lo necesita). */
    private fun applyVolume(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            val direction = when (code) {
                KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                else -> AudioManager.ADJUST_TOGGLE_MUTE
            }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }
}

/** Cómo se llama cada pantalla para el «En la tele: …» del control remoto. */
private fun remoteRouteLabel(route: Route): String = when (route) {
    Route.Home -> "Inicio"
    Route.Categories -> "Categorías"
    Route.Library -> "Mi lista"
    Route.Downloads -> "Descargas"
    Route.Live -> "TV en vivo"
    Route.Sports -> "Deportes en vivo"
    Route.Settings -> "Ajustes"
    Route.About -> "Acerca de"
    Route.Search -> "Buscador"
    Route.Assistant -> "Asistente"
    Route.Remote -> "Control remoto"
    Route.Games -> "Juegos"
    is Route.PartyHost -> "Juegos"
    is Route.PartyController -> "Juegos"
    is Route.Title -> route.film.title
    is Route.Category -> route.title
    is Route.Section -> route.title
    is Route.Player -> route.film.title
    is Route.LivePlayer -> route.channel.name
}

/** El PIN de emparejamiento del control remoto, en grande y sin foco. */
@Composable
private fun BoxScope.RemotePairingPin(pin: String, isTv: Boolean) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .background(com.chronosbvrx.visionplus.ui.theme.VisionSurface)
                .border(
                    BorderStroke(1.dp, com.chronosbvrx.visionplus.ui.theme.VisionBlue.copy(alpha = 0.5f)),
                    androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Emparejar control remoto",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text(
                "Escribe este PIN en tu teléfono",
                style = MaterialTheme.typography.bodyLarge,
                color = com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(if (isTv) 20.dp else 16.dp))
            Text(
                pin.toCharArray().joinToString(" "),
                style = MaterialTheme.typography.displayMedium,
                color = com.chronosbvrx.visionplus.ui.theme.VisionBlueBright,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

private val DPAD_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
)

/** La activación por bot queda disponible (ActivationScreen) pero no se exige: Vision+ entra directo. */
private const val ACTIVATION_REQUIRED = false
