package com.chronosbvrx.visionplus.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Android 7 no tiene Picture-in-Picture: cargar la fachada no puede tocar las clases de API
 * 26 (por eso viven aisladas en `PipApi26`). Es el guardián de compatibilidad de minSdk 24.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [24])
class PipLegacyTest {

    @Test
    fun android7SimplyDoesNotOfferPip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(PipMode.isSupported(context))
        assertFalse(PipMode.isActivityInPip(null))
        assertFalse(PipMode.update(android.app.Activity(), playing = false, autoEnter = false))
    }
}
