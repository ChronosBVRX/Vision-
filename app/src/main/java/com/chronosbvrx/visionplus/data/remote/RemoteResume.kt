package com.chronosbvrx.visionplus.data.remote

import android.content.Context
import com.chronosbvrx.visionplus.data.PlaybackHistory

/**
 * El minuto que el teléfono tiene guardado para la referencia que se va a mandar a la tele.
 *
 * El historial guarda el playCode XTV pelado (`abc`), pero el control remoto maneja la
 * referencia con su prefijo (`xtv:abc`): aquí se prueban las dos formas. Para una URL directa
 * la clave ES la URL. Devuelve 0 cuando no hay recuerdo (se abre desde el principio), nunca
 * lanza.
 */
internal fun resumeKeyCandidates(ref: String): List<String> {
    val clean = ref.trim()
    if (clean.isEmpty()) return emptyList()
    val stripped = if (clean.startsWith("xtv:")) clean.removePrefix("xtv:") else ""
    return listOfNotNull(clean, stripped.takeIf { it.isNotBlank() && it != clean })
}

internal fun resumePositionFor(context: Context, ref: String): Long =
    resumeKeyCandidates(ref).firstNotNullOfOrNull { key ->
        PlaybackHistory.get(context, key)?.positionMs?.takeIf { it > 0L }
    } ?: 0L
