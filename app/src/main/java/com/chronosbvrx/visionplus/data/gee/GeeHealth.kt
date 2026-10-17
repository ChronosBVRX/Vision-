package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource.GeeEvent

/**
 * El vigilante de vida de las señales deportivas.
 *
 * Mientras la pantalla de **Deportes en vivo** está abierta, las señales de los partidos que se
 * están jugando se comprueban sin parar (un GET corto al manifiesto, con sus cabeceras): así el
 * selector sabe al instante si una opción está caída —y ofrece el respaldo— o si **se levantó**,
 * para volver a la fuente deportiva como primera opción.
 *
 * Se comprueban TODAS las señales del partido, vivas y caídas: una caída puede volver.
 */
object GeeHealth {

    /** El resultado de una comprobación hecha por la app. */
    data class Check(val alive: Boolean, val atMs: Long)

    /** Tope de señales por pasada: nunca debe competir con la reproducción. */
    const val MAX_PER_PASS = 16

    /** Cada cuánto se revisa todo. */
    const val INTERVAL_MS = 30_000L

    /**
     * Las señales que hay que comprobar: las de los partidos que el reloj ya abrió (los que están
     * en el bloque «En vivo») —tanto sus transmisiones directas como los canales 24/7 que su
     * parrilla resuelve—, sin repetir y con tope.
     */
    fun urlsToWatch(
        events: List<GeeEvent>,
        groups: List<GeeSportsSource.GeeGroup> = emptyList(),
        nowMs: Long = GeeAgenda.currentNow(),
    ): List<String> = events.asSequence()
        .filter { GeeAgenda.phaseOf(it, nowMs) == GeeAgenda.Phase.Live }
        .flatMap { event ->
            val direct = event.links.asSequence().map { it.link.trim() }
            val guide = if (event.broadcasts.isEmpty() || groups.isEmpty()) {
                emptySequence()
            } else {
                GeeGuide.channelsFor(event.broadcasts, groups).asSequence().map { it.link.link.trim() }
            }
            direct + guide
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_PER_PASS)
        .toList()

    /**
     * El estado efectivo de una señal: lo que la app comprobó AHORA manda sobre el dato del
     * servidor (que puede tener minutos), y si no hay comprobación se usa el del servidor.
     */
    fun effective(serverAlive: Boolean?, check: Check?): Boolean? = check?.alive ?: serverAlive
}
