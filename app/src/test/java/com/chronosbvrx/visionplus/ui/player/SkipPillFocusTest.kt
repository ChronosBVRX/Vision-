package com.chronosbvrx.visionplus.ui.player

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guardián del foco D-pad de la píldora «Omitir intro / resumen» (regresión 2.5.83):
 * la píldora se volvía enfocable en TV pero NINGÚN control apuntaba a ella (el enlace de
 * buildControls corría con `introPill` todavía null), así que el mando jamás podía llegar
 * y el usuario no podía saltar el intro. Este test fija el enlace real: «arriba» desde la
 * barra entra a la píldora mientras esté visible y enfocable; oculta, la barra se queda en
 * sí misma; «abajo» en la píldora regresa a play/pausa.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SkipPillFocusTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun view(): View = View(context).apply { id = View.generateViewId() }

    @Test
    fun seekUpEntersThePillWhileItIsShown() {
        val seek = view()
        val pill = view()
        wireSkipPillFocus(seek, pill, pillFocusable = true, playPauseId = 42)
        assertEquals("arriba desde la barra va a la píldora", pill.id, seek.nextFocusUpId)
    }

    @Test
    fun hiddenPillLeavesTheBarLockedOnItself() {
        val seek = view()
        val pill = view()
        wireSkipPillFocus(seek, pill, pillFocusable = false, playPauseId = 42)
        assertEquals("sin píldora, arriba se queda en la barra", seek.id, seek.nextFocusUpId)
    }

    @Test
    fun pillLoopsOnItselfAndExitsThroughPlayPause() {
        val seek = view()
        val pill = view()
        wireSkipPillFocus(seek, pill, pillFocusable = true, playPauseId = 42)
        assertEquals(pill.id, pill.nextFocusLeftId)
        assertEquals(pill.id, pill.nextFocusRightId)
        assertEquals(pill.id, pill.nextFocusUpId)
        assertEquals("abajo vuelve a play/pausa", 42, pill.nextFocusDownId)
    }

    @Test
    fun nullViewsOrMissingIdsAreSafe() {
        wireSkipPillFocus(null, null, pillFocusable = true, playPauseId = View.NO_ID)
        val pill = view()
        pill.id = View.NO_ID
        wireSkipPillFocus(null, pill, pillFocusable = true, playPauseId = View.NO_ID)
    }

    @Test
    fun focusSearchUpFromTheSeekLandsOnThePill() {
        // Lo mismo que hace el sistema al pulsar Arriba con el mando: focusSearch(FOCUS_UP) del
        // control enfocado. Robolectric vive en touch mode (no hay window real): aquí los dos
        // controles se marcan focusable-in-touch SOLO para poder expresar el foco; en la TV real
        // aplica la variante con isFocusable (un equipo con mando no está en touch mode).
        val root = FrameLayout(context)
        val seek = View(context).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val pill = View(context).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(seek, FrameLayout.LayoutParams(480, 96).apply { leftMargin = 40; topMargin = 900 })
        root.addView(pill, FrameLayout.LayoutParams(320, 80).apply { leftMargin = 40; topMargin = 700 })
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        seek.requestFocus()
        wireSkipPillFocus(seek, pill, pillFocusable = true, playPauseId = View.NO_ID)
        val next = android.view.FocusFinder.getInstance().findNextFocus(root, seek, View.FOCUS_UP)
        assertEquals(
            "el foco sube de la barra a la píldora (upId=${seek.nextFocusUpId} pill=${pill.id} focused=${seek.isFocused})",
            pill,
            next,
        )
    }
}
