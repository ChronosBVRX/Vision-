package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.LiveChannel

/**
 * Puente del zapping: el modal de opciones de un partido (móvil y TV) deja aquí la lista
 * ORDENADA de señales del evento justo antes de pedir la reproducción, y `LivePlayerScreen`
 * la recoge al montar el reproductor en línea para que Arriba/Abajo muevan el canal dentro
 * del mismo bloque de opciones (patrón de `DeportesReturn`: objeto de proceso, nada nuevo
 * viaja por la pila de navegación).
 *
 * Anti-basura: `takeFor` solo entrega la lista si el ÚLTIMO canal armado es el que se está
 * abriendo; si el usuario llegó al reproductor por otro camino (Inicio, En vivo, búsqueda),
 * el holder queda obsoleto y devuelve vacío (zapping desactivado, cero regresiones).
 */
internal object GeeZapSignals {

    private var armedUrl: String? = null
    private var signals: List<LiveChannel> = emptyList()

    /** El modal lo llama justo antes de `onPlay(picked)`. */
    fun arm(current: LiveChannel, ordered: List<LiveChannel>) {
        armedUrl = current.url
        signals = ordered
    }

    /**
     * Señales restantes para [channel] (sin él mismo, deduplicadas por URL, conservando el
     * orden del modal). Vacío si no hay nada armado para este canal.
     */
    fun takeFor(channel: LiveChannel): List<LiveChannel> {
        val out = when (armedUrl) {
            channel.url -> signals
                .filter { it.url != channel.url }
                .distinctBy { it.url }
            else -> emptyList()
        }
        armedUrl = null
        signals = emptyList()
        return out
    }
}
