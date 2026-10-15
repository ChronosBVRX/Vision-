package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
    /**
     * Si el destino está compuesto (y puede tomar el foco). Por defecto se asume que
     * sí —los destinos fijos siempre lo están—; el héroe del Inicio lo apaga al salir
     * de composición ([RescueAnchor]), porque pedirle foco entonces solo come teclas.
     */
    var attached by mutableStateOf(true)
        internal set

    fun request() {
        runCatching { requester.requestFocus() }
    }
}

/**
 * Marca que el destino de rescate está compuesto: mientras este efecto viva, el
 * rescate puede intentar ese foco; al salir de composición (p. ej. el héroe con
 * scroll), el rescate deja pasar las teclas en vez de comérselas.
 */
@Composable
fun RescueAnchor(landing: LandingFocus) {
    DisposableEffect(landing) {
        landing.attached = true
        onDispose { landing.attached = false }
    }
}

/** Decisión pura del rescate: solo consume la tecla si puede caer en algo compuesto. */
internal fun shouldConsumeKey(dialogsOpen: Int, screenHasFocus: Boolean, targetAttached: Boolean): Boolean =
    dialogsOpen <= 0 && !screenHasFocus && targetAttached

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

/** Intentos del regreso a una ficha (y su espera): cubren el rato en que el contenido tarda en pintar. */
private const val RETURN_ATTEMPTS = 15
private const val RETURN_RETRY_MS = 100L

/**
 * Restauración del foco al volver de una ficha: pide la tarjeta exacta ([filmId]) una y otra
 * vez. Se re-arma cuando llega contenido nuevo ([contentKey], las filas se pintan en segundo
 * plano en gama baja) y no se rinde hasta que la pantalla tenga un foco elegido por el usuario:
 * el único foco que corrige es el automático ([landing]); apenas el usuario toma el mando con
 * otra cosa, deja de intervenir. Si la tarjeta no aparece —fila fuera de pantalla, contenido que
 * cambió— el foco cae a la primera tarjeta visible ([entryRequester]) o al destino de la
 * pantalla: el mando nunca se queda muerto, y si de plano nada tomó el foco se reporta
 * `FOCUS_LOST` con el contexto (diagnóstico en la tele real).
 *
 * Con [columnState]/[columnIndex] primero revela verticalmente la fila de la tarjeta (el
 * Inicio rearma sus filas tarde y la tarjeta puede no estar compuesta) y [onDone] avisa
 * cuando restaura o el usuario toma el mando (vale una sola vez) — pero no cuando se
 * rinde: si el contenido sigue llegando, el efecto se re-arma y lo reintenta.
 */
@Composable
fun rememberReturnFocus(
    filmId: String?,
    requester: FocusRequester,
    landing: LandingFocus,
    contentKey: Any? = null,
    entryRequester: FocusRequester? = null,
    columnState: LazyListState? = null,
    columnIndex: Int = -1,
    onDone: () -> Unit = {},
) {
    LaunchedEffect(filmId, contentKey) {
        if (filmId == null) return@LaunchedEffect
        if (columnIndex >= 0) {
            runCatching { columnState?.revealReturnIndex(columnIndex) }
        }
        var attempts = 0
        var attached = false
        // Si el foco ya lo eligió alguien (el usuario moviéndose), se respeta. El único foco que
        // se puede corregir es el automático de la pantalla ([landing], el héroe): ese no lo
        // eligió nadie, así que seguimos intentando la tarjeta exacta en cuanto componga.
        repeat(RETURN_ATTEMPTS) {
            if (FocusRescue.screenHasFocus && !landing.focused) {
                onDone()
                return@LaunchedEffect
            }
            attempts++
            val ok = runCatching { requester.requestFocus() }.isSuccess
            if (ok) attached = true
            if (FocusRescue.screenHasFocus && !landing.focused) {
                onDone()
                return@LaunchedEffect
            }
            delay(RETURN_RETRY_MS)
        }
        if (FocusRescue.screenHasFocus && !landing.focused) {
            onDone()
            return@LaunchedEffect
        }
        // Último recurso: cualquier tarjeta visible y, si no, el destino de la pantalla.
        // OJO: aquí NO se avisa [onDone]: si el contenido sigue llegando, el efecto se
        // re-arma con la nueva [contentKey] y reintenta la tarjeta exacta; limpiar ahora
        // es lo que dejaba el foco en el héroe aunque la tarjeta llegara un segundo después.
        val rescued = entryRequester != null && runCatching { entryRequester.requestFocus() }.isSuccess
        if (!FocusRescue.screenHasFocus) landing.request()
        if (!FocusRescue.screenHasFocus) {
            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                "FOCUS_LOST",
                "id=$filmId intentos=$attempts adjunta=$attached entrada=$rescued clave=$contentKey",
            )
        }
    }
}

/** Deja un par de cuadros para que el scroll restaurado del regreso se asiente antes de decidir. */
private const val REVEAL_SETTLE_MS = 60L

/**
 * Si la tarjeta del regreso quedó fuera de la ventana (el scroll no se restauró), la lista se
 * desplaza hasta dejarla a la vista: el foco solo puede caer en algo ya dibujado. Si ya está
 * visible no toca nada (se respeta la posición restaurada).
 */
suspend fun LazyListState.revealReturnIndex(index: Int) {
    if (index <= 0) return
    delay(REVEAL_SETTLE_MS)
    if (layoutInfo.visibleItemsInfo.any { it.index == index }) return
    scrollToItem((index - 1).coerceAtLeast(0))
}

/** Igual, para las cuadrículas. */
suspend fun LazyGridState.revealReturnIndex(index: Int) {
    if (index <= 0) return
    delay(REVEAL_SETTLE_MS)
    if (layoutInfo.visibleItemsInfo.any { it.index == index }) return
    scrollToItem(index)
}

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
        if (!shouldConsumeKey(dialogsOpen, screenHasFocus, target?.attached ?: false)) return false
        target?.request()
        return true
    }
}
