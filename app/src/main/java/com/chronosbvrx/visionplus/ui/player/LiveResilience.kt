package com.chronosbvrx.visionplus.ui.player

/**
 * Política de resiliencia del reproductor en línea (deportes en vivo sobre todo): los servidores
 * de estos streams suelen tener microcaídas por sobrecarga y el error fatal de ExoPlayer salía
 * al primer fallo, dejando al usuario pulsar «Reintentar» a mano para que todo volviera a
 * reproducir. Esta política decide (en puro, a prueba de regresiones):
 *
 *  1. Si un error merece un intento de reconexión automática (o es terminal y sale la tarjeta).
 *  2. Cuánto esperar entre intentos (backoff escalonado) y cuándo rendirse de verdad.
 *  3. Cuánto buffering sin avanzar cuenta como «se quedó congelado» y también merece reconectar.
 *
 * El budget de intentos es compartido entre errores fatales y estancamientos: tras ÉXITO
 * real (la posición avanza de nuevo) el contador vuelve a cero; si se agota, la UI muestra
 * la tarjeta «Reintentar/Volver» de siempre.
 */
internal object LiveResilience {

    /** Intentos de reconexión automática por sesión de reproducción (errores + stalls). */
    const val MAX_ATTEMPTS = 6

    /** Backoff escalonado en ms para el intento N (0-based); null = agotado. */
    private val BACKOFF_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L, 8_000L)

    /** Buffering real sin avanzar la posición que ya cuenta como «congelado». */
    const val STALL_TRIGGER_MS = 10_000L

    /** Códigos HTTP que indican sobrecarga/latido del servidor: volver más tarde funciona. */
    private val RETRY_HTTP_STATUS = intArrayOf(408, 429, 500, 502, 503, 504)

    /**
     * Qué clase de error nos llegó. Media3 los enumera en [androidx.media3.common.PlaybackException];
     * aquí van por familia para no depender de constantes frágiles una a una.
     */
    enum class Kind { TRANSIENT, FATAL }

    /**
     * [errorCode] es `PlaybackException.errorCode`. Estado HTTP (`-` si no es HTTP): solo lo
     * miramos en ERROR_CODE_IO_BAD_HTTP_STATUS.
     */
    fun classify(errorCode: Int, httpStatus: Int = -1): Kind {
        // 1002 BEHIND_LIVE_WINDOW: la ventana del vivo fue cerrada/reabierta por el servidor;
        // la solución documentada de media3 es re-preparar (¡exactamente una reconexión!).
        if (errorCode == 1002) return Kind.TRANSIENT
        // 1003 TIMEOUT y la familia de I/O 2000-2008: red, conexión, lecturas.
        if (errorCode == 1003) return Kind.TRANSIENT
        if (errorCode in 2000..2008) {
            if (errorCode == 2004) {
                // BAD_HTTP_STATUS: sobrecarga (408/429/5xx) sí recupera; 403/404/etc no.
                return if (httpStatus in RETRY_HTTP_STATUS) Kind.TRANSIENT else Kind.FATAL
            }
            // 2005 FILE_NOT_FOUND, 2006 NO_PERMISSION y 2007 CLEARTEXT_NOT_PERMITTED:
            // enlaces muertos o configuración, los reintentos no los arreglan. El resto
            // de la familia de I/O sí (lecturas cortadas, DNS).
            return if (errorCode in 2005..2007) Kind.FATAL else Kind.TRANSIENT
        }
        // Todo lo demás (DRM 3000-3007, parseo 4001-4006, decodificación 5001-5007, audio 6000+,
        // runtime checks, remoto…): los reintentos no lo arreglan. El UNSPECIFIED (1000) sí se
        // intenta: media3 envuelve en él errores varios y el budget de intentos acota el daño.
        return if (errorCode == 1000) Kind.TRANSIENT else Kind.FATAL
    }

    /** Backoff para el intento dado (0-based). null significa «agotemos»: mostrar la tarjeta. */
    fun delayFor(attempt: Int): Long? =
        if (attempt < 0 || attempt >= BACKOFF_MS.size) {
            null
        } else {
            BACKOFF_MS[attempt]
        }

    /**
     * El vigilante de estancamiento: en estado READY, con intención de reproducir y SIN
     * avanzar la posición [stallMs] seguidos, hay que reconectar. Al avanzar o pausar,
     * se apaga.
     */
    fun stallRecoveryNeeded(
        stateReady: Boolean,
        playWhenReady: Boolean,
        hasError: Boolean,
        stallMs: Long,
    ): Boolean =
        stateReady && playWhenReady && !hasError && stallMs >= STALL_TRIGGER_MS

    /** El zapping no debe morir en el borde: Arriba en la primera señal cae a la última. */
    fun cycleIndex(size: Int, current: Int, delta: Int): Int {
        if (size <= 0) return current
        return (current + delta).mod(size)
    }

    /** Failover permitido: con más de una señal, 1 intento automático por cada otra señal. */
    fun failoverAllowed(size: Int, failoversUsed: Int): Boolean =
        size > 1 && failoversUsed < size - 1
}

