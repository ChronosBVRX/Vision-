package com.chronosbvrx.visionplus.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Inactivity after which the overlay hides itself while the video plays. */
private const val INACTIVITY_MS = 4500L

/** Whether the controls overlay is on screen, and its auto-hide. Starts hidden: the video first. */
@Stable
internal class ControlsState {
    var visible by mutableStateOf(false)
        private set

    /** Bumps with each activity signal; relaunches the auto-hide countdown. */
    var activityTick by mutableIntStateOf(0)
        private set

    /** There was activity: brings the overlay and resets the countdown. */
    fun bump() {
        visible = true
        activityTick++
    }

    /** Only resets the countdown (D-pad moves inside the open overlay). */
    fun keepAlive() {
        activityTick++
    }

    fun hide() {
        visible = false
    }

    /** A tap on the video: hides the overlay if it is up, brings it if not. */
    fun toggle() {
        if (visible) hide() else bump()
    }

    /**
     * Presionar atrás con la navegación visible la oculta;
     * si ya está oculta, ejecuta la salida del reproductor.
     * Retorna true si consumió el evento (ocultó navegación).
     */
    fun handleBack(onExit: () -> Unit): Boolean {
        return if (visible) {
            hide()
            true
        } else {
            onExit()
            false
        }
    }
}

internal enum class PlayerBackAction {
    DISMISS_PICKER,
    HIDE_NAVIGATION,
    EXIT_PLAYER,
}

internal fun resolvePlayerBackAction(
    isPicking: Boolean,
    isNavigationVisible: Boolean,
): PlayerBackAction = when {
    isPicking -> PlayerBackAction.DISMISS_PICKER
    isNavigationVisible -> PlayerBackAction.HIDE_NAVIGATION
    else -> PlayerBackAction.EXIT_PLAYER
}

internal enum class LiveDpadAction {
    LIST_NAVIGATION,
    SHOW_NAVIGATION,
    SWITCH_CHANNEL,
}

internal fun resolveLiveDpadAction(
    isPicking: Boolean,
    isNavigationVisible: Boolean,
): LiveDpadAction = when {
    isPicking -> LiveDpadAction.LIST_NAVIGATION
    !isNavigationVisible -> LiveDpadAction.SHOW_NAVIGATION
    else -> LiveDpadAction.SWITCH_CHANNEL
}

internal fun calculateSeekTarget(currentPosMs: Long, deltaMs: Long, durationMs: Long): Long {
    val total = if (durationMs > 0) durationMs else Long.MAX_VALUE
    return (currentPosMs + deltaMs).coerceIn(0L, total)
}

internal fun resolveDismissFocusTarget(lastPickerSource: String?, isLive: Boolean): String {
    return lastPickerSource ?: if (isLive) "channels" else "playPause"
}

/**
 * En Smart TV, cuando el menú de reproducción está oculto (o visible pero sin ningún botón enfocado)
 * y no hay modales ni intro activos, cualquier pulsación del D-pad (OK/Centro, Izquierda, Derecha,
 * Arriba, Abajo) debe únicamente abrir el menú de reproducción y posicionar el foco sin ejecutar
 * ninguna acción por sí sola (ni pausar, ni adelantar/atrasar, ni cambiar de canal).
 */
internal fun shouldWakeControlsWithoutAction(
    keyCode: Int,
    isTv: Boolean,
    isControlsVisible: Boolean,
    hasControlFocus: Boolean = isControlsVisible,
    isModalActive: Boolean = false,
    isIntroActive: Boolean = false,
    isErrorShown: Boolean = false,
): Boolean {
    if (!isTv || isModalActive || isIntroActive || isErrorShown) return false
    if (isControlsVisible && hasControlFocus) return false
    return isDpadWakeupKeyCode(keyCode)
}

internal fun isDpadWakeupKeyCode(keyCode: Int): Boolean = when (keyCode) {
    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
    android.view.KeyEvent.KEYCODE_ENTER,
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
    android.view.KeyEvent.KEYCODE_DPAD_UP,
    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
    android.view.KeyEvent.KEYCODE_MENU,
    -> true
    else -> false
}

@Composable
internal fun rememberControlsState(): ControlsState = remember { ControlsState() }

/** Hides the overlay after [INACTIVITY_MS] without activity, only while playing: paused, it stays. */
@Composable
internal fun AutoHideEffect(state: ControlsState, playing: Boolean) {
    LaunchedEffect(state.activityTick, playing, state.visible) {
        if (state.visible && playing) {
            delay(INACTIVITY_MS)
            state.hide()
        }
    }
}
