package com.chronosbvrx.visionplus

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.gate.GateClient
import com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext
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

class MainActivity : ComponentActivity() {
    private var isTv = false

    /** El back stack vive aquí: la tarjeta del launcher entra como ruta desde el intent. */
    private val nav = Navigator(listOf(Route.Home))

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

    override fun onResume() {
        super.onResume()
        // Google TV: al volver a la app, «Continuar viendo» se sincroniza con el historial.
        LauncherWatchNext.syncAsync(this, delayMs = 3_000L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isTv = DeviceKind.isTelevision(this)
        com.chronosbvrx.visionplus.ui.settings.VisionSettings.init(this)
        com.chronosbvrx.visionplus.data.offline.OfflineRegistry.init(this)
        com.chronosbvrx.visionplus.data.offline.NetworkMonitor.init(this)
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

                    // Si el dispositivo arranca sin internet, abrimos en 600 ms directo a la interfaz
                    // especial de Modo Sin Conexión sin esperar los 9 segundos de timeout de red.
                    LaunchedEffect(Unit) {
                        val minSplash = launch { delay(600L) }
                        if (com.chronosbvrx.visionplus.data.offline.NetworkMonitor.refresh(this@MainActivity)) {
                            if (deepLinked) {
                                // La tarjeta del launcher manda: el catálogo carga detrás, sin
                                // hacer esperar a la película que la persona ya eligió.
                                launch { runCatching { catalog.load() } }
                            } else {
                                withTimeoutOrNull(BOOT_CAP_MS) {
                                    catalog.load()
                                }
                            }
                        }
                        minSplash.join()
                        bootReady = true
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
    private fun launcherIntentRoute(intent: Intent?): Route? =
        if (intent != null && intent.action == Intent.ACTION_VIEW) launcherRouteFor(this, intent.data) else null
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
