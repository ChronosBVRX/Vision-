package com.chronosbvrx.visionplus.ui.games

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chronosbvrx.visionplus.DeviceKind

/**
 * Modo inmersivo real: en el teléfono se ocultan las barras del sistema mientras
 * dura el juego, para que solo se vea la interfaz de Vision+ Play. En la tele las
 * barras ya las esconde la actividad del shell; no se toca nada para no pelear con
 * el foco del mando.
 */
@Composable
fun GameFullscreenEffect() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val activity = context.findGameActivity()
        val tv = DeviceKind.isTelevision(context)
        if (activity == null || tv) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.systemBarsBehavior = previousBehavior
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findGameActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findGameActivity()
    else -> null
}
