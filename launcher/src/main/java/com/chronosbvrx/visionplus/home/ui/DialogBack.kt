package com.chronosbvrx.visionplus.home.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Cierra el diálogo con Atrás desde la raíz enfocada (el `BackHandler` del launcher no basta). */
internal fun Modifier.dismissOnBack(onDismiss: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
        onDismiss()
        true
    } else {
        false
    }
}

/**
 * En la raíz de un diálogo: si el menú salió con OK largo, el KeyUp que la persona está
 * soltando no puede activar su primera acción («Abrir» en una app). También registra los
 * KeyDown para que solo un OK nuevo, después de soltar de verdad, vuelva a hacer click.
 *
 * Se pone en TODAS las ventanas del launcher: cuesta nada y evita que un cambio de foco
 * deje una tecla a medio soltar sobre el control equivocado.
 */
internal fun Modifier.consumeLongPressRelease(): Modifier = onPreviewKeyEvent { event ->
    val isOk = isOkKeyCode(event.nativeKeyEvent.keyCode)
    if (isOk && event.type == KeyEventType.KeyDown) {
        TvPressGuard.noteKeyDown(event.nativeKeyEvent.repeatCount)
    }
    event.type == KeyEventType.KeyUp && isOk && TvPressGuard.takeKeyUp()
}
