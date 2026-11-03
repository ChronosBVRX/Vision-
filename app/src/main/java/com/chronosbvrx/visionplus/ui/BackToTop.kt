package com.chronosbvrx.visionplus.ui

/**
 * Ventana del «Atrás en dos pasos» de la barra superior. Es la misma idea (y el mismo tiempo)
 * que el cierre con confirmación del Inicio: si el segundo toque llega a tiempo, sale de
 * verdad; si llega tarde, el Atrás vuelve a subir a la barra en vez de salir por sorpresa.
 */
internal const val BACK_TO_TOP_WINDOW_MS = 2_500L

/**
 * Política pura del Atrás en las pantallas con barra superior (Inicio, Películas, Series, TV en
 * vivo, Mi lista…): **el primer toque sube el foco a la selección de arriba** y el segundo,
 * dentro de la ventana, deja salir — volver a la pantalla anterior o cerrar la app si es el
 * Inicio. Nunca se sale de la pantalla con un solo toque, que es lo que se sentía como un
 * salto accidental al estar recorriendo una sección con el mando.
 *
 * Igual que [BackExitGuard], el estado vive solo en memoria: arrancar de cero es lo seguro.
 */
internal class BackToTopGuard(private val windowMs: Long = BACK_TO_TOP_WINDOW_MS) {
    private var armedUntilMs = Long.MIN_VALUE

    /** `true` cuando este toque debe subir el foco a la barra (y NO salir de la pantalla). */
    fun press(nowMs: Long): Boolean {
        if (nowMs <= armedUntilMs) {
            armedUntilMs = Long.MIN_VALUE
            return false
        }
        armedUntilMs = nowMs + windowMs
        return true
    }

    /** ¿El regreso sigue armado? (el siguiente Atrás ya sale). */
    fun armed(nowMs: Long): Boolean = nowMs <= armedUntilMs

    /** Se cambió de pantalla o ya se salió: el armado viejo no puede confirmar nada. */
    fun disarm() {
        armedUntilMs = Long.MIN_VALUE
    }
}
