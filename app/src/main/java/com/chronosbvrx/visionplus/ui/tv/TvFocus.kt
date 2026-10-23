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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement
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
     * Si el destino está compuesto (y puede tomar el foco). Lo mantiene [landingFocus]
     * mientras su nodo viva: al desmontarse (fila reciclada, modo que cambió, control
     * ausente por configuración) queda en false, y el rescate jamás intenta un destino
     * muerto. [RescueAnchor] lo maneja para anclas que no llevan [landingFocus]
     * (el héroe del Inicio).
     */
    var attached by mutableStateOf(false)
        internal set

    fun request() {
        runCatching { requester.requestFocus() }
    }
}

/**
 * Nodo que mantiene [LandingFocus.attached] atado al ciclo de vida real del elemento:
 * alta cuando el nodo entra al árbol, baja (y foco limpio) cuando sale. Es la bandera
 * que usa el rescate para no comerse una tecla en un destino que ya no existe.
 */
private class LandingAttachNode(var landing: LandingFocus) : Modifier.Node() {
    override fun onAttach() {
        landing.attached = true
    }

    override fun onDetach() {
        landing.attached = false
        landing.focused = false
    }
}

private data class LandingAttachElement(val landing: LandingFocus) : ModifierNodeElement<LandingAttachNode>() {
    override fun create() = LandingAttachNode(landing)

    override fun update(node: LandingAttachNode) {
        node.landing = landing
    }
}

private fun Modifier.landingAttached(landing: LandingFocus): Modifier =
    this then LandingAttachElement(landing)

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
 * Candidatos del rescate, del último al primero: los destinos compuestos ([LandingFocus.attached])
 * que el estado actual permite consumir. Puro y probado; el rescate además VERIFICA que el foco
 * aterrizó antes de comerse la tecla.
 */
internal fun rescueCandidates(
    targets: List<LandingFocus>,
    dialogsOpen: Int,
    screenHasFocus: Boolean,
): List<LandingFocus> =
    targets.asReversed().filter { shouldConsumeKey(dialogsOpen, screenHasFocus, it.attached) }

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
    focusRequester(landing.requester)
        .onFocusChanged { landing.focused = it.isFocused }
        .landingAttached(landing)

/** Intentos del regreso a una ficha (y su espera): cubren el rato en que el contenido tarda en pintar. */
private const val RETURN_ATTEMPTS = 15
private const val RETURN_RETRY_MS = 100L

/** Cada cuánto vigila el regreso que el foco no se quede vacío (una fila que se recarga). */
private const val RETURN_KEEPER_MS = 500L

/** Respiro del vigilante para que un pedido de foco recién hecho se refleje. */
private const val RETURN_KEEPER_SETTLE_MS = 120L

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
        var tookOver = false
        val startedAt = android.os.SystemClock.uptimeMillis()
        repeat(RETURN_ATTEMPTS) {
            if (tookOver) return@repeat
            // Se respeta al usuario: si ya pulsó el mando desde que empezó el regreso,
            // se deja de pelear el foco. El `screenHasFocus` viejo de la pantalla que
            // se acaba de cerrar NO cuenta como elección (dejaba el mando muerto):
            // solo una tecla real lo demuestra.
            if (FocusRescue.lastUserKeyAtMs > startedAt) {
                onDone()
                tookOver = true
                return@repeat
            }
            attempts++
            val ok = runCatching { requester.requestFocus() }.isSuccess
            if (ok) attached = true
            delay(RETURN_RETRY_MS)
        }
        if (!tookOver) {
            // Último recurso: cualquier tarjeta visible y, si no, el destino de la pantalla.
            // OJO: aquí NO se avisa [onDone]: si el contenido sigue llegando, el efecto se
            // re-arma con la nueva [contentKey] y reintenta la tarjeta exacta; limpiar ahora
            // es lo que dejaba el foco en el héroe aunque la tarjeta llegara un segundo después.
            val rescued = entryRequester != null && requestFocusSafely(entryRequester)
            // Nada enfocado todavía y la tarjeta exacta no compuso (índice de fila viejo,
            // contenido cambiado): la columna vuelve al inicio para que el destino de la
            // pantalla (héroe) EXISTA. Sin esto, un regreso a una fila que ya no está dejaba
            // el mando muerto sin que ni el rescate pudiera actuar (héroe descompuesto).
            if (!FocusRescue.screenHasFocus && columnState != null) {
                runCatching { columnState.scrollToItem(0) }
                delay(80)
            }
            if (!FocusRescue.screenHasFocus) landing.request()
            if (!FocusRescue.screenHasFocus) {
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "FOCUS_LOST",
                    "id=$filmId intentos=$attempts adjunta=$attached entrada=$rescued clave=$contentKey",
                )
            }
        }
        // El regreso queda VIGILANDO mientras la pantalla viva: si más tarde la tarjeta que
        // tenía el foco desaparece —«Seguir viendo» se recarga al terminar una película— el
        // foco se queda vacío sin que nadie lo retome (el usuario reportó el mando muerto
        // justo así: película terminada y vuelta al Inicio). Aquí vuelve a caer en algo
        // visible, sin pelear jamás con un foco que sí exista ni comer una tecla en el aire.
        while (true) {
            delay(RETURN_KEEPER_MS)
            // Con un diálogo abierto el foco pertenece a su ventana: aquí no se toca nada.
            if (FocusRescue.dialogsOpen > 0) continue
            if (FocusRescue.screenHasFocus) continue
            runCatching { requester.requestFocus() }
            delay(RETURN_KEEPER_SETTLE_MS)
            if (FocusRescue.screenHasFocus) continue
            if (entryRequester == null || !requestFocusSafely(entryRequester)) landing.request()
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
 * Pide el foco SIN poder tumbar la pantalla. `FocusRequester.requestFocus()` lanza
 * `IllegalStateException` («FocusRequester is not initialized») cuando su nodo ya no
 * está adjunto: la tarjeta ancla se recicló al desplazarse (LazyColumn) o el control
 * no existe por configuración (kill-switch). Devuelve `false` para que el llamador
 * deje seguir la navegación estándar en vez de morir.
 *
 * OJO: en esta versión de Compose `requestFocus()` no reporta nada (Unit): «éxito»
 * aquí es simplemente que no lanzó.
 */
internal fun requestFocusSafely(requester: FocusRequester): Boolean =
    try {
        requester.requestFocus()
        true
    } catch (_: Throwable) {
        false
    }

/**
 * «Abajo» dirigido a un control que puede no existir: si el destino está adjunto el
 * foco salta a él (el atajo de siempre); si no, la tecla sigue su curso normal hacia
 * el focusable más cercano — nunca un crash.
 *
 * OJO: no usar `focusProperties { down = requester }` con destinos que puedan
 * desmontarse; esa propiedad revienta al pedir el foco de un requester huérfano.
 */
fun Modifier.softFocusDown(requester: FocusRequester): Modifier = onPreviewKeyEvent { event ->
    event.type == KeyEventType.KeyDown &&
        event.key == Key.DirectionDown &&
        requestFocusSafely(requester)
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

    /**
     * Última tecla del usuario (`uptimeMillis`). El regreso de una ficha mira esto en vez
     * de `screenHasFocus`: la pantalla anterior puede dejar ese flag en true al cerrarse y
     * el regreso se rendía sin enfocar nada (mando muerto). Una tecla real sí es una elección.
     */
    @Volatile
    var lastUserKeyAtMs = 0L

    fun noteUserKey() {
        lastUserKeyAtMs = android.os.SystemClock.uptimeMillis()
    }

    /** How many TV dialogs are open: while there is one, the keys and the focus belong to it. */
    var dialogsOpen = 0

    /** Freno del reporte `FOCUS_RESCUE_MISS`: una pantalla muerta no inunda el diagnóstico. */
    private var lastMissReportMs = 0L

    /**
     * Rescata el foco moviéndolo a un destino COMPUESTO. Se prueban del último al primero
     * y se VERIFICA que el foco aterrizó ([LandingFocus.focused]): un destino muerto —el
     * nodo se recicló, el modo cambió, el control no existe— no se come la tecla, se
     * prueba el siguiente y, si ninguno puede, la tecla sigue su curso (jamás una tecla
     * comida en el aire). Devuelve true solo cuando consumió y movió el foco.
     */
    fun rescue(): Boolean {
        val candidates = synchronized(targets) {
            rescueCandidates(targets.toList(), dialogsOpen, screenHasFocus)
        }
        for (candidate in candidates) {
            candidate.request()
            if (candidate.focused) return true
        }
        if (candidates.isNotEmpty()) reportMiss(candidates.size)
        return false
    }

    /**
     * El rescate existe pero NINGÚN destino compuesto pudo tomar el foco: esa pantalla
     * dejaría el mando comido por su propia red de seguridad. Se reporta (con freno de
     * 30 s) para que el caso aparezca en el diagnóstico de la tele real con su pantalla.
     */
    private fun reportMiss(count: Int) {
        val now = java.lang.System.currentTimeMillis()
        if (now - lastMissReportMs < MISS_REPORT_MS) return
        lastMissReportMs = now
        runCatching {
            com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                "FOCUS_RESCUE_MISS",
                "el rescate no pudo aterrizar en ${count} destino(s) compuestos",
            )
        }
    }
}

/** Freno del reporte de rescate fallido (una pantalla muerta reporta, no inunda). */
private const val MISS_REPORT_MS = 30_000L
