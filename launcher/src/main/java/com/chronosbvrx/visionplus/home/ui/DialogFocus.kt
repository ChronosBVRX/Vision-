package com.chronosbvrx.visionplus.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * El foco de un diálogo del launcher, con dos garantías que el mando necesita:
 *
 * 1. **Abre con foco.** La ventana del `Dialog` tarda unos cuadros en estar enfocada y una
 *    petición hecha antes se descarta en silencio. Se reintenta hasta que el destino lo tiene
 *    de verdad (`focused`), no «una vez y a ver qué pasa».
 * 2. **Nunca se queda sin foco.** Cuando el contenido cambia debajo del cursor (el «¿Restablecer
 *    todo?» que reemplaza la fila, un canal que desaparece al ocultarlo, la lista que se filtra),
 *    el nodo enfocado sale del árbol y la ventana se queda SIN foco: las flechas del mando
 *    dejarían de hacer nada. [dialogRootFocus] avisa cuando ningún control tiene el foco y el
 *    vigilante devuelve el foco al destino del diálogo.
 */
@Stable
internal class DialogFocus {
    val requester = FocusRequester()

    /**
     * Destino de respaldo: un control que SIEMPRE está compuesto en el diálogo (la fila de
     * filtros o el botón Cerrar). Si el contenido cambió y el destino principal ya no existe
     * (la lista quedó vacía al ocultar el último elemento), el foco cae aquí en vez de morir.
     */
    val fallbackRequester = FocusRequester()

    /** El destino del diálogo tiene el foco. */
    var focused by mutableStateOf(false)
        internal set

    /** Algo dentro de la ventana del diálogo tiene el foco (no necesariamente el destino). */
    var rootHasFocus by mutableStateOf(false)
        internal set

    internal fun request() {
        requestFocusSafely(requester)
    }

    internal fun requestFallback() {
        requestFocusSafely(fallbackRequester)
    }
}

@Composable
internal fun rememberDialogFocus(): DialogFocus {
    val focus = remember { DialogFocus() }
    // Apertura: la ventana del diálogo todavía no tiene foco en los primeros cuadros.
    LaunchedEffect(focus) {
        repeat(40) {
            if (focus.focused) return@LaunchedEffect
            focus.request()
            delay(50)
        }
    }
    // Vigilante: solo actúa cuando la ventana se quedó sin ningún control enfocado. Si el
    // usuario está en otra acción del diálogo (`rootHasFocus`) jamás se le quita el foco.
    LaunchedEffect(focus) {
        while (true) {
            delay(140)
            if (focus.rootHasFocus || focus.focused) continue
            focus.request()
            // El destino principal pudo desaparecer con el contenido (lista filtrada vacía,
            // fila de confirmación reemplazada): cae en el control que siempre existe.
            delay(70)
            if (!focus.rootHasFocus) focus.requestFallback()
        }
    }
    return focus
}

/** Marca el control donde abre el diálogo. Debe ir antes del `clickable`/`combinedClickable`. */
internal fun Modifier.dialogFocus(focus: DialogFocus): Modifier = this
    .focusRequester(focus.requester)
    .onFocusChanged { focus.focused = it.isFocused }

/** Marca el control de respaldo (siempre compuesto) del diálogo. */
internal fun Modifier.dialogFallbackFocus(focus: DialogFocus): Modifier =
    focusRequester(focus.fallbackRequester)

/** En la raíz del diálogo: mantiene [DialogFocus.rootHasFocus] atado a lo que el usuario ve. */
internal fun Modifier.dialogRootFocus(focus: DialogFocus): Modifier =
    onFocusChanged { focus.rootHasFocus = it.hasFocus }
