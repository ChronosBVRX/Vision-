package com.chronosbvrx.visionplus.ui.player

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Los gestos táctiles del reproductor móvil, extraídos para poder probarlos de verdad
 * (con un dispatch táctil completo en Robolectric).
 *
 * REGRESIÓN REAL (v2.5.77 → 2.5.82): `GestureDetector.onTouchEvent` solo consume el
 * ACTION_DOWN si `onDown` devuelve true; `SimpleOnGestureListener` devuelve false por
 * defecto. Sin ese consumo la vista NO reclama el gesto, el ACTION_UP jamás llega al
 * detector y `onSingleTapUp`/`onSingleTapConfirmed` no se disparan nunca: «los controles
 * ya no aparecen» al tocar la pantalla. Aquí `onDown` devuelve true SIEMPRE.
 */
internal class MobileTapGestures(
    private val isBlocked: () -> Boolean,
    private val controlsVisible: () -> Boolean,
    private val screenWidth: () -> Int,
    private val onShowControls: () -> Unit,
    private val onHideControlsDelayed: () -> Unit,
    private val onCancelPendingHide: () -> Unit,
    private val onSeekBy: (Long) -> Unit,
    private val onSeekToast: (String) -> Unit,
) : GestureDetector.SimpleOnGestureListener() {

    /** CRÍTICO: sin consumir el DOWN la vista no reclama el gesto y el tap muere. */
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        when (resolveMobileTapAction(controlsVisible(), isBlocked())) {
            MobileTapAction.SHOW_NOW -> onShowControls()
            MobileTapAction.HIDE_DELAYED -> onHideControlsDelayed()
            MobileTapAction.IGNORE -> Unit
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        // Un doble toque nunca oculta la barra: el primer toque deja de contar.
        onCancelPendingHide()
        if (isBlocked()) return true
        val third = screenWidth() / 3f
        when {
            e.x < third -> {
                onSeekBy(-10_000L)
                onSeekToast("−10 s")
            }
            e.x > third * 2 -> {
                onSeekBy(10_000L)
                onSeekToast("+10 s")
            }
            // Centro: el primer toque ya mostró los controles; no repetir.
            else -> Unit
        }
        return true
    }
}

/**
 * Enlaza los gestos al [catcher] del reproductor con la red de seguridad de la casa: la
 * vista se marca clickable (reclama el gesto aunque un callback devuelva false) y el
 * listener recibe el stream táctil completo.
 */
internal fun attachMobileTapGestures(
    catcher: View,
    listener: MobileTapGestures,
): GestureDetector {
    val detector = GestureDetector(catcher.context, listener)
    catcher.isClickable = true
    catcher.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    return detector
}
