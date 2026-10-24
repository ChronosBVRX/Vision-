package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.chronosbvrx.visionplus.home.contract.LiveItem
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * En una tele, Compose coloca el elemento enfocado al 30 % del contenedor con una animación
 * de 125 ms INCLUSO si ya estaba visible. En un launcher con varias filas eso se siente como
 * que la pantalla camina sola. Este spec solo desplaza cuando el elemento quedó fuera de vista.
 */
@OptIn(ExperimentalFoundationApi::class)
internal val MinimalScrollBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val bottomEdge = offset + size
        return when {
            offset >= 0f && bottomEdge <= containerSize -> 0f
            offset < 0f && bottomEdge > containerSize -> 0f
            abs(offset) < abs(bottomEdge - containerSize) -> offset
            else -> bottomEdge - containerSize
        }
    }
}

/**
 * Pide el foco sin poder tumbar la pantalla: un FocusRequester huérfano (tarjeta reciclada)
 * lanza IllegalStateException. Devuelve true solo cuando la petición no reventó.
 */
internal fun requestFocusSafely(requester: FocusRequester): Boolean =
    try {
        requester.requestFocus()
        true
    } catch (_: Throwable) {
        false
    }

/** «Abajo» dirigido a un control que puede no existir; si no está, la tecla sigue su curso. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.softFocusDown(requester: FocusRequester): Modifier = onPreviewKeyEvent { event ->
    event.type == KeyEventType.KeyDown &&
        event.key == Key.DirectionDown &&
        requestFocusSafely(requester)
}

/** Un reloj de UI que despierta al cambiar el minuto (EPG y fases de Deportes). */
@Composable
fun rememberNowMs(intervalMs: Long = 30_000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Programa que el canal está dando AHORA, incluso si el feed acaba de cruzar el fin del anterior. */
internal fun currentProgram(item: LiveItem, nowMs: Long): String =
    if (item.startMs > 0L && item.endMs > item.startMs && nowMs >= item.endMs && item.next.isNotBlank()) {
        item.next
    } else {
        item.now
    }

/** Avance del programa en curso: usa la ventana real y cae al valor del feed si no la hay. */
internal fun liveProgress(item: LiveItem, nowMs: Long): Int {
    if (item.startMs <= 0L || item.endMs <= item.startMs) return item.progress.coerceIn(0, 100)
    if (nowMs >= item.endMs) return 0
    val elapsed = (nowMs - item.startMs).coerceAtLeast(0L)
    return ((elapsed.toDouble() / (item.endMs - item.startMs)) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}
