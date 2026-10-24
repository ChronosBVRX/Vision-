package com.chronosbvrx.visionplus.home

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chronosbvrx.visionplus.home.contract.EnginePrewarmContract
import com.chronosbvrx.visionplus.home.ui.HomeTheme

/**
 * La pantalla de inicio.
 *
 * Declara `HOME + DEFAULT` en el manifest: el sistema la ofrece como Inicio y, si la persona
 * la elige, cada pulsación de Home vuelve aquí. `stateNotNeeded` evita que Android intente
 * restaurar un estado que ya no importa (el feed se relee solo).
 *
 * Además, si la tele tiene 2 GB o más, pide a Vision+ que caliente el motor de video
 * ([EnginePrewarmContract]). Es opcional: si Vision+ no está o es viejo, no hace nada y la
 * app se calienta sola desde su splash.
 */
class LauncherActivity : ComponentActivity() {

    private val homeSignal = mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    private var engineConnection: ServiceConnection? = null
    private var engineBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val profile = DeviceProfile(this)
        setContent {
            HomeTheme(reducedEffects = profile.reducedEffects) {
                LauncherScreen(profile = profile, homeSignal = homeSignal.intValue)
            }
        }
        // El launcher vive en su propia tarea y no debe recrearse por orientación.
        android.util.Log.i(TAG, "Inicio listo (tv=${profile.isTv}, reducido=${profile.reducedEffects})")
        scheduleEnginePrewarm(profile)
    }

    /** Home otra vez estando ya en el launcher: vuelve al Inicio y cierra paneles. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal.intValue++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unbindEnginePrewarm("launcher destruido")
        super.onDestroy()
    }

    /**
     * Espera al primer frame y un respiro (para no competir con el arranque del sistema)
     * y recién entonces se vincula. En 1.5 GB no se hace nada: el camino diferido de
     * Vision+ sigue siendo el seguro.
     */
    private fun scheduleEnginePrewarm(profile: DeviceProfile) {
        if (!profile.isTv) {
            android.util.Log.i(TAG, "prewarm omitido: no es TV")
            return
        }
        val ramMb = totalRamMb()
        if (!EnginePrewarmPolicy.shouldPrewarm(ramMb, profile.isTv)) {
            android.util.Log.i(TAG, "prewarm omitido: ${ramMb}MB (se queda el camino diferido)")
            return
        }
        handler.postDelayed({
            bindEnginePrewarm()
        }, EnginePrewarmContract.BIND_DELAY_MS)
    }

    private fun bindEnginePrewarm() {
        if (engineBound || isFinishing || isDestroyed) return
        val intent = Intent()
            .setClassName(EnginePrewarmContract.APP_PACKAGE, EnginePrewarmContract.SERVICE_CLASS)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                // El servicio ya está calentando en :x4k; no hay nada que llamar.
                android.util.Log.i(TAG, "prewarm del motor conectado")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                engineBound = false
                engineConnection = null
                android.util.Log.i(TAG, "prewarm del motor desconectado")
            }
        }
        val bound = runCatching {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse { error ->
            android.util.Log.i(TAG, "prewarm no disponible: ${error.javaClass.simpleName}: ${error.message}")
            false
        }
        if (!bound) {
            android.util.Log.i(TAG, "Vision+ no expone el servicio de prewarm (viejo o no instalado)")
            return
        }
        engineConnection = connection
        engineBound = true
        android.util.Log.i(TAG, "prewarm solicitado a Vision+ (${totalRamMb()}MB)")
        // La ventana útil es el arranque de la tele; después se suelta para no retener RAM.
        handler.postDelayed({
            unbindEnginePrewarm("ventana cumplida")
        }, EnginePrewarmContract.BIND_WINDOW_MS)
    }

    private fun unbindEnginePrewarm(reason: String) {
        val connection = engineConnection ?: return
        engineConnection = null
        engineBound = false
        runCatching { unbindService(connection) }
        android.util.Log.i(TAG, "prewarm liberado ($reason)")
    }

    private fun totalRamMb(): Long = runCatching {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem / (1024L * 1024L)
    }.getOrDefault(0L)

    /**
     * Un Inicio de tele no lleva barra de gestos ni de estado: en la tele no existen y en un
     * teléfono (probando el launcher en horizontal) estorban y se comen el borde. Se pueden
     * sacar deslizando desde el borde (modo inmersivo pegajoso), así nadie queda atrapado.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** El launcher nunca se cierra con Atrás; Compose lo maneja y aquí no se deja salir. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // No-op a propósito: la navegación (pestañas y paneles) la resuelve LauncherScreen.
    }

    private companion object {
        const val TAG = "VisionHome"
    }
}
