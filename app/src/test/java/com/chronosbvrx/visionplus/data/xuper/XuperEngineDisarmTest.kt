package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Anti-regresión del desarme del origen legado: con una sesión rancia, sus rotaciones
 * (auto-login/bootstrap) martillan el portal compartido y tiran la reproducción XTV de la
 * misma cuenta (la «caja atascada»: el motor descarga pero nunca arranca el video).
 * Desarmado, el origen queda marcado de forma permanente y ni siquiera construye cliente.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class XuperEngineDisarmTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun disarmingMarksTheSourcePermanently() {
        assertFalse(XuperEngine.isDisabled(context))
        XuperEngine.disable(context)
        assertTrue(XuperEngine.isDisabled(context))
        // Idempotente: repetirlo no truena ni cambia el estado.
        XuperEngine.disable(context)
        assertTrue(XuperEngine.isDisabled(context))
    }

    @Test
    fun disarmedSourceIsNotRunning() {
        XuperEngine.disable(context)
        assertFalse(XuperEngine.isRunning())
    }
}
