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

    /**
     * El contexto del partido que el reproductor pinta en su aviso de cable, con la MISMA
     * interfaz que la TV en vivo: liga, marcador y dónde se transmite.
     */
    data class EventInfo(
        val title: String,
        val league: String = "",
        /** «1 - 0 · 37'» cuando la agenda trae marcador; vacío si no. */
        val scoreLine: String = "",
        /** La parrilla oficial del partido («Telemundo · TUDN · ViX Deportes»). */
        val guide: List<String> = emptyList(),
        /** Cuántas señales trae el evento por la fuente deportiva. */
        val signals: Int = 0,
        val source: String = "Deportes en vivo",
        /**
         * El respaldo AUTOMÁTICO del partido: una cadena de su parrilla que sí tenemos en el
         * catálogo de la casa y que la fuente deportiva no puede servir. Solo se usa cuando ya
         * no queda ninguna señal suya (y nunca se enseña en la lista: la lista es solo de la
         * fuente deportiva).
         */
        val fallback: LiveChannel? = null,
    )

    private var armedUrl: String? = null
    private var signals: List<LiveChannel> = emptyList()
    private var info: EventInfo? = null

    /**
     * El respaldo PEDIDO por el reproductor cuando se queda sin señales: `LivePlayerScreen` lo
     * observa y cambia al canal confirmado de la casa (el reproductor nativo). Objeto de proceso,
     * como el resto del puente: nada nuevo viaja por la pila de navegación y la firma del
     * reproductor no crece (ART rechaza las composables con demasiados parámetros).
     */
    var fallbackRequest = androidx.compose.runtime.mutableStateOf<LiveChannel?>(null)
        private set

    /** El reproductor pide el respaldo: solo cuando ya no le queda ninguna señal. */
    fun requestFallback(channel: LiveChannel) {
        if (fallbackRequest.value?.url != channel.url) fallbackRequest.value = channel
    }

    /** Quien lo atiende lo consume (una sola vez). */
    fun consumeFallback(): LiveChannel? {
        val out = fallbackRequest.value
        fallbackRequest.value = null
        return out
    }

    /** El modal lo llama justo antes de `onPlay(picked)`. */
    fun arm(current: LiveChannel, ordered: List<LiveChannel>, eventInfo: EventInfo? = null) {
        armedUrl = current.url
        signals = ordered
        info = eventInfo
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

    /**
     * El contexto del partido para el canal que se está abriendo (una sola vez, misma validación
     * que las señales). Se lee ANTES que [takeFor]: el aviso de cable lo necesita al montar.
     */
    fun takeInfoFor(channel: LiveChannel): EventInfo? {
        val out = if (armedUrl == channel.url) info else null
        info = null
        return out
    }
}
