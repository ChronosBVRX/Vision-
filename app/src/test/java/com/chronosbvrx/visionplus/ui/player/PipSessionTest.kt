package com.chronosbvrx.visionplus.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.ui.settings.VisionSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * La compuerta del búnker: el PiP de una descarga está apagado por defecto y solo se abre
 * cuando el usuario lo habilita explícitamente. El catálogo normal no depende del ajuste.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PipSessionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vision_settings", Context.MODE_PRIVATE).edit().clear().commit()
        VisionSettings.init(context)
        PipSession.clear()
    }

    @After
    fun tearDown() {
        PipSession.clear()
    }

    @Test
    fun catalogPlaybackCanAlwaysUsePip() {
        attach(isVault = false)
        assertTrue(PipSession.canEnter(context))
    }

    @Test
    fun downloadsNeedTheExplicitOptIn() {
        attach(isVault = true)
        assertFalse(PipSession.canEnter(context))
        VisionSettings.setPipDownloads(context, true)
        assertTrue(PipSession.canEnter(context))
    }

    @Test
    fun withoutASessionThereIsNothingToFloat() {
        assertFalse(PipSession.canEnter(context))
        assertFalse(PipSession.inPip)
    }

    private fun attach(isVault: Boolean) {
        PipSession.attach(
            PipSession.Active(
                isVault = isVault,
                isLive = false,
                title = "Prueba",
                playing = { true },
                toggle = {},
                close = {},
            ),
        )
    }
}
