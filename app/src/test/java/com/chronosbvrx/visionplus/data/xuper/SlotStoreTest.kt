package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El slot propio persiste cifrado por instalación: guardar → leer → limpiar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SlotStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun saveLoadAndClearRoundTrip() {
        val plain = { ctx: Context ->
            ctx.getSharedPreferences("slot_test", Context.MODE_PRIVATE)
        }
        SlotStore.backend = plain
        try {
            SlotStore.clear(context)
            assertNull(SlotStore.load(context))
            SlotStore.save(context, DeviceSlot(sn = "SN-PROP-9", userId = "U-9"))
            assertEquals(DeviceSlot("SN-PROP-9", "U-9"), SlotStore.load(context))
            SlotStore.clear(context)
            assertNull(SlotStore.load(context))
        } finally {
            SlotStore.backend = { SecretKeystore.get(it) }
        }
    }
}
