package com.chronosbvrx.visionplus.ui.player

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardián del dispatch táctil del reproductor móvil — la regresión real de v2.5.77:
 * `GestureDetector.onTouchEvent` solo consume el ACTION_DOWN si `onDown` devuelve true.
 * Con el default (false) la vista no reclama el gesto, el ACTION_UP nunca llega al
 * detector y el toque muere («los controles ya no aparecen»). Este test despacha un
 * DOWN/UP REAL por la jerarquía de vistas: si alguien quita el `onDown` o el enlace
 * (`attachMobileTapGestures`), falla.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MobileTapDispatchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class Counters {
        var shows = 0
        var hidesDelayed = 0
        var seeks = 0
        var cancels = 0
    }

    private fun wire(
        controlsVisible: Boolean,
        blocked: Boolean = false,
        width: Int = 1080,
        counters: Counters,
    ): FrameLayout {
        val root = FrameLayout(context)
        val catcher = View(context)
        val gestures = MobileTapGestures(
            isBlocked = { blocked },
            controlsVisible = { controlsVisible },
            screenWidth = { width },
            onShowControls = { counters.shows++ },
            onHideControlsDelayed = { counters.hidesDelayed++ },
            onCancelPendingHide = { counters.cancels++ },
            onSeekBy = { counters.seeks++ },
            onSeekToast = {},
        )
        attachMobileTapGestures(catcher, gestures)
        root.addView(
            catcher,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        // Sin layout no hay hit-test: el dispatch de Robolectric necesita medidas reales.
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        return root
    }

    private fun tap(root: FrameLayout, x: Float = 100f, y: Float = 100f) {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, x, y, 0)
        root.dispatchTouchEvent(down)
        root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    @Test
    fun singleTapWithHiddenControlsShowsThem() {
        val counters = Counters()
        val root = wire(controlsVisible = false, counters = counters)
        tap(root)
        assertEquals("el toque debe mostrar los controles al instante", 1, counters.shows)
        assertEquals(0, counters.hidesDelayed)
    }

    @Test
    fun singleTapWithVisibleControlsSchedulesTheDelayedHide() {
        val counters = Counters()
        val root = wire(controlsVisible = true, counters = counters)
        tap(root)
        assertEquals(0, counters.shows)
        assertEquals("ocultar espera el timeout del doble toque", 1, counters.hidesDelayed)
    }

    @Test
    fun blockedTapDoesNothing() {
        val counters = Counters()
        val root = wire(controlsVisible = false, blocked = true, counters = counters)
        tap(root)
        assertEquals(0, counters.shows)
        assertEquals(0, counters.hidesDelayed)
    }

    @Test
    fun doubleTapOnTheLeftSeeksBackAndDoesNotHide() {
        val counters = Counters()
        val root = wire(controlsVisible = false, counters = counters)
        // Primer toque (mostraría) y segundo DOWN dentro del timeout del doble toque.
        val down1 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        val up1 = MotionEvent.obtain(0L, 30L, MotionEvent.ACTION_UP, 100f, 100f, 0)
        val down2 = MotionEvent.obtain(0L, 120L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        val up2 = MotionEvent.obtain(0L, 150L, MotionEvent.ACTION_UP, 100f, 100f, 0)
        root.dispatchTouchEvent(down1)
        root.dispatchTouchEvent(up1)
        root.dispatchTouchEvent(down2)
        root.dispatchTouchEvent(up2)
        assertEquals("un doble toque en el lado izquierdo salta atrás", 1, counters.seeks)
        assertEquals("el doble toque cancela el ocultado pendiente", 1, counters.cancels)
        down1.recycle(); up1.recycle(); down2.recycle(); up2.recycle()
    }

    @Test
    fun withoutConsumingTheDownTheTapNeverArrives_documentingTheV2577Bug() {
        // La trampa exacta del bug v2.5.77: un GestureDetector con onDown=false (el default
        // de SimpleOnGestureListener) sobre una vista que no lo consume. El padre no le
        // entrega el ACTION_UP y onSingleTapUp JAMÁS corre. Si este test deja de fallar en
        // el futuro, la plataforma cambió y el blindaje de MobileTapGestures puede revisarse.
        val root = FrameLayout(context)
        val catcher = View(context)
        var tapped = 0
        val buggy = object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                tapped++
                return true
            }
            // onDown queda en false a propósito: el bug original.
        }
        val detector = android.view.GestureDetector(context, buggy)
        catcher.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        root.addView(
            catcher,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        tap(root)
        assertEquals("sin consumir el DOWN el tap muere (el bug v2.5.77)", 0, tapped)
    }
}
