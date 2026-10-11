package com.chronosbvrx.visionplus

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * Screen and UI animation mode for TV and mobile.
 */
enum class EffectsMode(val label: String, val note: String) {
    AUTO(
        "Automático",
        "Detecta tu dispositivo. Activa el modo ligero en Chromecast y equipos de 2 GB de RAM o menos.",
    ),
    LIGHT(
        "Modo ligero (TV lenta)",
        "Sin animaciones de fondo, sin zoom en tarjetas y carga optimizada. Máxima fluidez.",
    ),
    FULL(
        "Efectos completos",
        "Fondo animado, zoom en tarjetas y transiciones completas para equipos potentes.",
    );

    companion object {
        fun fromKey(key: String?): EffectsMode = when (key?.lowercase()) {
            "light", "performance", "off" -> LIGHT
            "full", "quality", "on" -> FULL
            else -> AUTO
        }
    }
}

/**
 * Hardware capability detector and performance tuning.
 * Automatically identifies constrained devices (e.g. Chromecast with Google TV, Fire TV sticks,
 * devices with <= 2.2 GB RAM or <= 4 weak CPU cores) to render a clean, lightweight UI without jank.
 */
object DevicePerformance {
    private const val PREFS_NAME = "vision_performance"
    private const val KEY_EFFECTS_MODE = "effects_mode"

    /** Returns total RAM in megabytes, or -1 if unavailable. */
    fun totalMemoryMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return -1L
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        return mem.totalMem / (1024L * 1024L)
    }

    /**
     * Checks if this device has modest/constrained resources (e.g. Chromecast with Google TV,
     * Fire TV Stick, Xiaomi Mi Box, Android TV box with <= 2.2 GB RAM or <= 4 weak CPU cores).
     */
    fun isLowEndDevice(
        context: Context,
        totalRamMb: Long = totalMemoryMb(context),
        isLowRamFlag: Boolean = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true,
        model: String = Build.MODEL ?: "",
        hardware: String = Build.HARDWARE ?: "",
        cpuCores: Int = Runtime.getRuntime().availableProcessors(),
        isTv: Boolean = DeviceKind.isTelevision(context),
    ): Boolean = evaluateLowEnd(
        totalRamMb = totalRamMb,
        isLowRamFlag = isLowRamFlag,
        model = model,
        hardware = hardware,
        cpuCores = cpuCores,
        isTv = isTv,
    )

    /** Pure function for easy and thorough unit testing. */
    internal fun evaluateLowEnd(
        totalRamMb: Long,
        isLowRamFlag: Boolean,
        model: String,
        hardware: String,
        cpuCores: Int,
        isTv: Boolean,
    ): Boolean {
        if (isLowRamFlag) return true
        if (totalRamMb in 1..2250L) return true

        val m = model.lowercase()
        val h = hardware.lowercase()
        if (m.contains("chromecast") || m.contains("aft") || m.contains("mibox") || m.contains("fire tv")) {
            return true
        }
        if (h.contains("amlogic") || h.contains("realtek") || h.contains("sunxi")) {
            return true
        }

        if (isTv && cpuCores in 1..4) {
            return true
        }

        return false
    }

    fun getEffectsMode(context: Context): EffectsMode {
        val prefs = prefs(context)
        val key = prefs.getString(KEY_EFFECTS_MODE, null)
        return EffectsMode.fromKey(key)
    }

    fun setEffectsMode(context: Context, mode: EffectsMode) {
        prefs(context).edit().putString(KEY_EFFECTS_MODE, mode.name.lowercase()).apply()
    }

    /**
     * Evaluates whether reduced effects / clean UI should be active based on user preference,
     * system accessibility, and hardware detection.
     */
    fun shouldReduceEffects(
        context: Context,
        mode: EffectsMode = getEffectsMode(context),
        systemAnimationsDisabled: Boolean = com.chronosbvrx.visionplus.ui.systemAnimationsOff(context),
    ): Boolean = evaluateShouldReduceEffects(
        mode = mode,
        systemAnimationsDisabled = systemAnimationsDisabled,
        isLowEnd = isLowEndDevice(context),
    )

    /** Pure function for unit testing. */
    internal fun evaluateShouldReduceEffects(
        mode: EffectsMode,
        systemAnimationsDisabled: Boolean,
        isLowEnd: Boolean,
    ): Boolean {
        if (systemAnimationsDisabled) return true
        return when (mode) {
            EffectsMode.LIGHT -> true
            EffectsMode.FULL -> false
            EffectsMode.AUTO -> isLowEnd
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
