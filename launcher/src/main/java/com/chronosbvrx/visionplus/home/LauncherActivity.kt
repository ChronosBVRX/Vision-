package com.chronosbvrx.visionplus.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chronosbvrx.visionplus.home.ui.HomeTheme

/**
 * La pantalla de inicio.
 *
 * Declara `HOME + DEFAULT` en el manifest: el sistema la ofrece como Inicio y, si la persona
 * la elige, cada pulsación de Home vuelve aquí. `stateNotNeeded` evita que Android intente
 * restaurar un estado que ya no importa (el feed se relee solo).
 */
class LauncherActivity : ComponentActivity() {

    private val homeSignal = mutableIntStateOf(0)

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
        android.util.Log.i("VisionHome", "Inicio listo (tv=${profile.isTv}, reducido=${profile.reducedEffects})")
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
}
