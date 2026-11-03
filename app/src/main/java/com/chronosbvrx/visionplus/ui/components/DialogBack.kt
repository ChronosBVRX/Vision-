package com.chronosbvrx.visionplus.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Cierra un diálogo con la tecla Atrás del mando/teclado.
 *
 * En la tele hay pantallas con su propio `BackHandler` (Atrás en dos pasos) y el
 * diálogo puede quedar por encima sin recibir el `onDismissRequest`. Este modificador
 * se pone en la RAÍZ del contenido del diálogo: al ser un `onPreviewKeyEvent` del
 * ancestro del foco, la tecla se atiende antes que cualquier hijo y el diálogo se
 * cierra siempre.
 */
fun Modifier.dismissOnBack(onDismiss: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
        onDismiss()
        true
    } else {
        false
    }
}
