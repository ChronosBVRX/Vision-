package com.chronosbvrx.visionplus

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings

/** Is this device a TV (driven by a remote)? Picks the TV or the phone interface. */
object DeviceKind {
    /**
     * Interruptor de QA: `adb shell settings put global vision_force_tv 1` fuerza el shell de
     * tele en un teléfono/emulador (para probar la interfaz sin una TV). Apagado por defecto.
     */
    fun isForcedTv(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.applicationContext.contentResolver, "vision_force_tv", 0) == 1
    }.getOrDefault(false)

    fun isTelevision(context: Context): Boolean {
        if (isForcedTv(context)) return true
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("amazon.hardware.fire_tv")) return true
        // No touchscreen: a remote-driven box running tablet firmware.
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return true
        return context.resources.configuration.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH
    }
}
