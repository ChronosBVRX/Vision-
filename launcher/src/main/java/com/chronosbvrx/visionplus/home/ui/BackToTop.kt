package com.chronosbvrx.visionplus.home.ui

import android.os.SystemClock

/**
 * Ventana del «Atrás en dos pasos» del launcher. Es la misma idea que en Vision+ (y el mismo
 * tiempo): si el segundo toque llega a tiempo, el Atrás hace su trabajo (volver a Inicio);
 * si llega tarde, vuelve a subir el foco a la barra superior en vez de saltar por sorpresa.
 */
internal const val LAUNCHER_BACK_TO_TOP_MS = 2_500L

/**
 * Política pura del Atrás del launcher: **el primer toque sube el foco a la barra superior**
 * (donde vive la navegación) y el segundo, dentro de la ventana, hace lo de siempre —volver
 * a Inicio—. Si el mando ya está en la barra, el Atrás actúa de una vez. Nunca se siente como
 * un salto accidental al estar recorriendo una fila con el D-pad.
 *
 * El estado vive solo en memoria: reiniciar el launcher empieza de cero, que es lo seguro.
 */
internal class LauncherBackToTopGuard(private val windowMs: Long = LAUNCHER_BACK_TO_TOP_MS) {
    private var armedUntilMs = Long.MIN_VALUE

    /** `true` cuando este toque debe subir el foco a la barra (y NO navegar todavía). */
    fun press(nowMs: Long): Boolean {
        if (nowMs <= armedUntilMs) {
            armedUntilMs = Long.MIN_VALUE
            return false
        }
        armedUntilMs = nowMs + windowMs
        return true
    }

    fun armed(nowMs: Long): Boolean = nowMs <= armedUntilMs

    /** Se cambió de pestaña/panel o ya se navegó: el armado viejo no confirma nada. */
    fun disarm() {
        armedUntilMs = Long.MIN_VALUE
    }
}

/** Reloj del guardián, aislado para que la política se pruebe sin Android. */
internal fun backToTopNow(): Long = SystemClock.uptimeMillis()
