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

/**
 * Calcula el salto en milisegundos para el scrubber según la duración total
 * y acelera exponencialmente cuando la tecla se mantiene presionada en el mando.
 */
internal fun calculateScrubStepMs(totalMs: Long, repeatCount: Int = 0): Long {
    val baseStep = when {
        totalMs > 90 * 60_000L -> 30_000L
        totalMs > 30 * 60_000L -> 20_000L
        else -> 10_000L
    }
    return when {
        repeatCount >= 30 -> 300_000L
        repeatCount >= 15 -> 120_000L
        repeatCount >= 5  -> 60_000L
        else -> baseStep
    }
}

internal fun resolveDismissFocusTarget(lastPickerSource: String?, isLive: Boolean): String {
    return lastPickerSource ?: if (isLive) "channels" else "playPause"
}

internal enum class PlayerKeyDownAction {
    /** El primer clic con menú oculto (o sin foco en controles) SOLO abre el menú y enfoca sin ejecutar acción. */
    WAKE_CONTROLS_ONLY,
    /** El menú ya está visible y enfocado: en TV en vivo Arriba/Abajo cambia al canal siguiente. */
    LIVE_SWITCH_NEXT_CHANNEL,
    /** El menú ya está visible y enfocado: en TV en vivo Abajo/CH- cambia al canal anterior. */
    LIVE_SWITCH_PREV_CHANNEL,
    /** El menú ya está visible y un control tiene el foco: el sistema de foco mueve el cursor o activa el botón enfocado. */
    PASS_TO_FOCUSED_CONTROL,
    /** Hay un modal, pre-roll de intro o pantalla de error activa: el evento pasa al modal/intro/error. */
    PASS_TO_MODAL_OR_SYSTEM,
    /** Tecla multimedia física dedicada (Play/Pause/Rewind/FastForward de control remoto multimedia). */
    DEDICATED_MEDIA_KEY,
}

/**
 * Política universal anti-regresión para TODOS los reproductores de Vision+
 * (`X4KPlayerAty` Live/VOD, `PlayerCore` Live/VOD, `TitanLivePlayer`, `TitanVodPlayerScreen`):
 *
 * Regla inmutable:
 * Cuando el menú de navegación está oculto (`!isControlsVisible`) o ningún control del menú tiene
 * el foco (`!hasControlFocus`), el PRIMER clic en cualquier tecla de navegación del D-pad
 * (`OK/Center`, `Enter`, `Izquierda`, `Derecha`, `Arriba`, `Abajo`, `Menu`, `CH+`, `CH-`)
 * DEBE ÚNICAMENTE hacer visible el menú de navegación y posicionar el foco (`WAKE_CONTROLS_ONLY`).
 * JAMÁS debe pausar, hacer seek ±10s, saltar intro, cambiar de canal ni salir del reproductor en el primer clic.
 */
internal fun resolvePlayerKeyDownAction(
    keyCode: Int,
    isLive: Boolean,
    isControlsVisible: Boolean,
    hasControlFocus: Boolean = isControlsVisible,
    isModalActive: Boolean = false,
    isIntroActive: Boolean = false,
    isErrorShown: Boolean = false,
): PlayerKeyDownAction {
    if (isModalActive || isIntroActive || isErrorShown) {
        return PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM
    }
    if (isDedicatedMediaKeyCode(keyCode)) {
        return PlayerKeyDownAction.DEDICATED_MEDIA_KEY
    }
    if (isDpadWakeupKeyCode(keyCode)) {
        if (!isControlsVisible || !hasControlFocus) {
            return PlayerKeyDownAction.WAKE_CONTROLS_ONLY
        }
        if (isLive) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_UP) {
                return PlayerKeyDownAction.LIVE_SWITCH_NEXT_CHANNEL
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN || keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN) {
                return PlayerKeyDownAction.LIVE_SWITCH_PREV_CHANNEL
            }
        }
        return PlayerKeyDownAction.PASS_TO_FOCUSED_CONTROL
    }
    return PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM
}

internal const val CONTROL_ACTION_COOLDOWN_MS = 220L

/**
 * Evita que un mismo evento de tecla (por rebote o repetición rápida de hardware)
 * ejecute inmediatamente una acción en el control recién enfocado tras abrir el menú.
 */
internal fun canExecuteControlAction(
    nowMs: Long,
    lastWakeupMs: Long,
    cooldownMs: Long = CONTROL_ACTION_COOLDOWN_MS,
): Boolean = (nowMs - lastWakeupMs) >= cooldownMs

internal fun shouldWakeControlsWithoutAction(
    keyCode: Int,
    isTv: Boolean = true,
    isControlsVisible: Boolean,
    hasControlFocus: Boolean = isControlsVisible,
    isModalActive: Boolean = false,
    isIntroActive: Boolean = false,
    isErrorShown: Boolean = false,
): Boolean {
    if (!isTv) return false
    return resolvePlayerKeyDownAction(
        keyCode = keyCode,
        isLive = false,
        isControlsVisible = isControlsVisible,
        hasControlFocus = hasControlFocus,
        isModalActive = isModalActive,
        isIntroActive = isIntroActive,
        isErrorShown = isErrorShown,
    ) == PlayerKeyDownAction.WAKE_CONTROLS_ONLY
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
    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
    android.view.KeyEvent.KEYCODE_INFO,
    android.view.KeyEvent.KEYCODE_GUIDE,
    -> true
    else -> false
}

internal fun isDedicatedMediaKeyCode(keyCode: Int): Boolean = when (keyCode) {
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
    android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    android.view.KeyEvent.KEYCODE_HEADSETHOOK,
    android.view.KeyEvent.KEYCODE_SPACE,
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
