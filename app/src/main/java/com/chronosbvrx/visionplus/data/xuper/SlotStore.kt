package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.content.SharedPreferences

/**
 * El slot propio sobrevive en el almacén cifrado (por instalación: cada equipo
 * reclama el suyo). Tras borrar datos/reinstalar, el reclamo por `deviceKey`
 * (estable: android_id) lo recupera sin gastar otro código.
 */
object SlotStore {

    private const val K_SN = "slot_sn"
    private const val K_USER_ID = "slot_user_id"
    private const val K_AT = "slot_assigned_at"

    /** Puerta trasera solo para tests (Robolectric no tiene AndroidKeyStore). */
    @Volatile
    internal var backend: (Context) -> SharedPreferences = { SecretKeystore.get(it) }

    fun load(context: Context): DeviceSlot? {
        val prefs = backend(context)
        val slot = DeviceSlot(
            sn = DeviceSlot.normalizeSn(prefs.getString(K_SN, null)),
            userId = prefs.getString(K_USER_ID, null)?.trim()?.take(32).orEmpty(),
        )
        return slot.takeIf { it.isValid() }
    }

    fun save(context: Context, slot: DeviceSlot) {
        require(slot.isValid())
        backend(context).edit()
            .putString(K_SN, slot.sn)
            .putString(K_USER_ID, slot.userId)
            .putLong(K_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        backend(context).edit()
            .remove(K_SN).remove(K_USER_ID).remove(K_AT)
            .apply()
    }
}
