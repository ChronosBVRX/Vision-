package com.chronosbvrx.visionplus

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.gate.GateClient
import com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext
import com.chronosbvrx.visionplus.data.remote.RemoteBus
import com.chronosbvrx.visionplus.data.remote.RemoteKeys
import com.chronosbvrx.visionplus.data.remote.RemoteProto
import com.chronosbvrx.visionplus.data.remote.RemoteServer
import com.chronosbvrx.visionplus.data.remote.remoteLiveChannel
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.activation.ActivationScreen
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.Navigator
import com.chronosbvrx.visionplus.ui.PhoneApp
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.TvApp
import com.chronosbvrx.visionplus.ui.brand.VisionSplash
import com.chronosbvrx.visionplus.ui.launcherRouteFor
import com.chronosbvrx.visionplus.ui.systemAnimationsOff
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTheme
import com.chronosbvrx.visionplus.ui.tv.FocusRescue
import kotlinx.coroutines.delay
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
        launcherIntentRoute(intent)?.let { nav.push(it) }
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
        dispatchRemoteKey(code, action)
    }

    override fun onResume() {
        super.onResume()
        RemoteBus.attach(remoteSink)
        // Google TV: al volver a la app, «Continuar viendo» se sincroniza con el historial.
        LauncherWatchNext.syncAsync(this, delayMs = 3_000L)
    }

    override fun onPause() {
        RemoteBus.detach(remoteSink)
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            remoteServer?.stop()
            remoteServer = null
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        bootStartedMs = android.os.SystemClock.uptimeMillis()
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
        com.chronosbvrx.visionplus.ui.settings.VisionSettings.init(this)
        com.chronosbvrx.visionplus.data.offline.OfflineRegistry.init(this)
        com.chronosbvrx.visionplus.data.offline.NetworkMonitor.init(this)
        // Control remoto por LAN: solo la tele escucha (el teléfono es el que manda órdenes).
        if (isTv) {
            remoteServer = RemoteServer(this).also { runCatching { it.start() } }
        }
        // Tarjeta del launcher en arranque en frío: la película/capítulo entra en la ruta base.
        if (savedInstanceState == null) {
            launcherIntentRoute(intent)?.let { nav.push(it) }
        }
        val deepLinked = nav.stack.size > 1

        setContent {
            VisionTheme {
                CompositionLocalProvider(LocalReducedEffects provides DevicePerformance.shouldReduceEffects(this, com.chronosbvrx.visionplus.ui.settings.VisionSettings.effectsMode)) {
                    var needsActivation by rememberSaveable {
                        mutableStateOf(ACTIVATION_REQUIRED && !GateClient.isActivated(this@MainActivity))
                    }
                    var splashVisible by rememberSaveable { mutableStateOf(true) }
                    var bootReady by remember { mutableStateOf(false) }
                    val navigator = remember { nav }
                    val catalog = remember { XuperCatalog(this@MainActivity) }
                    val isOnline by com.chronosbvrx.visionplus.data.offline.NetworkMonitor.isOnline.collectAsState()

                    // El reloj del menú (móvil y TV): hora del centro de México en toda la
                    // navegación. Se apaga en los reproductores (ahí manda el contenido) y en
                    // el buscador (el teclado ocupa la parte superior).
                    val clockRoute = navigator.current
                    val showClock = !needsActivation &&
                        clockRoute !is Route.Player &&
                        clockRoute !is Route.LivePlayer &&
                        clockRoute !is Route.Search

                    // Salida con confirmación desde la pantalla principal: el primer Atrás
                    // avisa («presiona de nuevo»), el segundo dentro de la ventana cierra la
                    // app de verdad — llevándose al reproductor residente para que el sistema
                    // no lo traiga al frente (el bucle reportado: Atrás → reproductor → Atrás…).
                    val exitGuard = remember { com.chronosbvrx.visionplus.ui.BackExitGuard() }
                    var exitHint by remember { mutableStateOf(false) }
                    var exitPresses by remember { mutableIntStateOf(0) }
                    BackHandler(enabled = !needsActivation && navigator.stack.size <= 1) {
                        if (exitGuard.press(System.currentTimeMillis())) {
                            exitHint = false
                            com.chronosbvrx.visionplus.ui.exitAppCleanly(this@MainActivity)
                        } else {
                            exitHint = true
                            exitPresses++
                        }
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
                        if (com.chronosbvrx.visionplus.data.offline.NetworkMonitor.refresh(this@MainActivity)) {
                            if (deepLinked) {
                                // La tarjeta del launcher manda: el catálogo carga detrás, sin
                                // hacer esperar a la película que la persona ya eligió.
                                launch { runCatching { catalog.load() } }
                            } else {
                                hitCap = withTimeoutOrNull(BOOT_CAP_MS) {
                                    catalog.load()
                                } == null
                            }
                        }
                        minSplash.join()
                        bootReady = true
                        // Telemetría del arranque en frío: si abrir el Inicio tardó más de lo
                        // justo se reporta con los ms de cada fase (el diagnóstico aclarará si
                        // la lentitud es del arranque del sistema o de nuestra carga). El
                        // handicap del reloj: el primer frame del splash ya contaba.
                        val bootMs = android.os.SystemClock.uptimeMillis() - bootStartedMs
                        if (bootMs > BOOT_SLOW_TRACE_MS) {
                            runCatching {
                                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                                    "BOOT_SLOW",
                                    "bootMs=$bootMs hitCap=$hitCap ${catalog.lastLoadTimings.take(300)}",
                                )
                            }
                        }
                        if (com.chronosbvrx.visionplus.data.offline.NetworkMonitor.isOnline.value) {
                            launch(kotlinx.coroutines.Dispatchers.IO) {
                                delay(4_000L)
                                AppUpdate.check(this@MainActivity, force = true)
                            }
                        }
                    }

                    // Cuando regresa la conexión a internet, cargamos el catálogo automáticamente
                    LaunchedEffect(isOnline) {
                        if (isOnline && bootReady && catalog.rows.isEmpty()) {
                            catalog.load()
                        }
                    }

                    // Al salir de un reproductor interno (ExoPlayer vive en esta actividad) y al
                    // navegar, el canal «Continuar viendo» del launcher se pone al día solo.
                    LaunchedEffect(nav.stack.size) {
                        if (nav.current !is Route.Player && nav.current !is Route.LivePlayer) {
                            LauncherWatchNext.syncAsync(this@MainActivity, delayMs = 1_500L)
                        }
                    }
                    Box(Modifier.fillMaxSize().background(VisionBlack)) {
                        if (needsActivation) {
                            ActivationScreen(
                                isTv = isTv,
                                onActivated = { needsActivation = false },
                            )
                        } else if (!splashVisible || bootReady) {
                            if (isTv) {
                                TvApp(catalog, navigator)
                            } else {
                                PhoneApp(catalog, navigator)
                            }
                        }
                        if (splashVisible) {
                            VisionSplash(
                                isTv = isTv,
                                canExit = bootReady,
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
                        // El reloj de la casa, arriba al centro de TODO el menú: hora del
                        // centro de México, sin foco (el D-pad nunca cae en él).
                        if (showClock) {
                            com.chronosbvrx.visionplus.ui.VisionClockBadge(
                                isTv = isTv,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (isTv) 14.dp else 56.dp),
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
        // El control remoto abre canales con `visionplus://live/<código>`.
        if ("live".equals(uri.host, ignoreCase = true)) return liveRouteFor(uri)
        return launcherRouteFor(this, uri)
    }

    /** Un canal que el teléfono mandó a abrir: del catálogo cacheado, o mínimo con su código. */
    private fun liveRouteFor(uri: Uri): Route? {
        val code = uri.pathSegments.lastOrNull().orEmpty()
        val name = uri.getQueryParameter("t").orEmpty()
        val logo = uri.getQueryParameter("p")
        val cached = runCatching { X4KApi.cachedLiveChannels(this) }.getOrDefault(emptyList())
        val channel = remoteLiveChannel(code, name, logo, cached) ?: return null
        return Route.LivePlayer(channel)
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
