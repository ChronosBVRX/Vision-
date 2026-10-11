package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/** How many times, [RETRY_MS] apart, a screen asks for its landing focus before giving up (about 3 s). */
private const val ATTEMPTS = 60
private const val RETRY_MS = 50L

/**
 * Where a TV screen puts the D-pad focus when it opens. `FocusRequester.requestFocus()` reports
 * nothing: on a box whose window is not focused yet (the first frames after launch) the request is
 * silently dropped. So success is the target's own focus state, and the request repeats until the
 * target really holds focus.
 */
@Stable
class LandingFocus internal constructor() {
    val requester = FocusRequester()
    var focused by mutableStateOf(false)
        internal set

    fun request() {
        runCatching { requester.requestFocus() }
    }
}

/**
 * The screen's landing focus, requested on entry (and again whenever [key] changes). The target is
 * also registered with [FocusRescue], so a D-pad press that finds nothing focused lands there.
 * Con [key] `null` el objetivo queda de respaldo pero NO pide el foco solo: lo usa Inicio cuando
 * el riel ya tiene su propio destino (evita la pelea de dos focos al volver).
 */
@Composable
fun rememberLandingFocus(key: Any? = Unit): LandingFocus {
    val landing = remember { LandingFocus() }
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        repeat(ATTEMPTS) {
            if (landing.focused) return@LaunchedEffect
            landing.request()
            delay(RETRY_MS)
        }
    }
    DisposableEffect(landing) {
        FocusRescue.pushTarget(landing)
        onDispose { FocusRescue.popTarget(landing) }
    }
    return landing
}

/** Marks the element [landing] puts the focus on. Goes before the element's own focusable. */
fun Modifier.landingFocus(landing: LandingFocus): Modifier =
    focusRequester(landing.requester).onFocusChanged { landing.focused = it.isFocused }

/**
 * Confinamiento de foco (focus trap) para ventanas modales y diálogos:
 * impide que la navegación con D-pad escape del modal hacia el contenido que está por debajo.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.modalFocusTrap(): Modifier = this
    .focusGroup()
    .focusProperties {
        exit = { FocusRequester.Cancel }
    }

/**
 * The safety net behind [rememberLandingFocus]: when a D-pad key reaches the activity unhandled and
 * nothing on screen holds focus, the current screen's landing target takes it.
 */
object FocusRescue {
    private val targets = ArrayList<LandingFocus>()

    var target: LandingFocus?
        get() = synchronized(targets) { targets.lastOrNull() }
        set(value) {
            synchronized(targets) {
                if (value == null) {
                    targets.clear()
                } else {
                    targets.remove(value)
                    targets.add(value)
                }
            }
        }

    fun pushTarget(landing: LandingFocus) {
        synchronized(targets) {
            targets.remove(landing)
            targets.add(landing)
        }
    }

    fun popTarget(landing: LandingFocus) {
        synchronized(targets) {
            targets.remove(landing)
        }
    }

    /** Whether anything inside the TV interface holds focus; kept by the root's `onFocusChanged`. */
    var screenHasFocus = false

    /** How many TV dialogs are open: while there is one, the keys and the focus belong to it. */
    var dialogsOpen = 0

    /** Returns true when it moved the focus. */
    fun rescue(): Boolean {
        if (dialogsOpen > 0) return false
        if (screenHasFocus) return false
        val t = target ?: return false
        t.request()
        return true
    }
}
