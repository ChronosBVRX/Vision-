package com.chronosbvrx.visionplus.data

import android.os.SystemClock

/**
 * «Acaba de arrancar una reproducción».
 *
 * En una caja de TV el motor necesita la CPU justo cuando negocia la señal. Nuestro propio
 * trabajo de fondo (parsear el índice de varios MB, pedir las filas del portal) corre en el
 * proceso principal y se lo come: en una TCL de 2 GB se midió `cacheMs=12436` (12.4 s solo
 * para leer el índice guardado) mientras el reproductor intentaba abrir.
 *
 * Esta marca permite que ese trabajo espere a que el motor ya esté pintando.
 */
internal fun recentlyLaunchedPlay(lastLaunchAtMs: Long, nowMs: Long, windowMs: Long): Boolean {
    if (lastLaunchAtMs <= 0L) return false
    val elapsed = nowMs - lastLaunchAtMs
    // Un reloj que va hacia atrás no debe bloquear el catálogo para siempre.
    if (elapsed < 0L) return false
    return elapsed < windowMs
}

object PlaybackFocus {

    /** Ventana en la que el motor está arrancando (primer cuadro + buffer inicial). */
    internal const val QUIET_MS = 20_000L

    @Volatile
    private var lastLaunchAtMs = 0L

    /** Reloj inyectable: las pruebas JVM no tienen `SystemClock`. */
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    /** Lo llama el lanzador del reproductor, justo antes de abrir la pantalla. */
    fun launched() {
        lastLaunchAtMs = clock()
    }

    /** ¿Hay una reproducción recién arrancada que merece la CPU? */
    fun busy(): Boolean = recentlyLaunchedPlay(lastLaunchAtMs, clock(), QUIET_MS)

    internal fun resetForTests() {
        lastLaunchAtMs = 0L
        clock = { SystemClock.elapsedRealtime() }
    }
}
