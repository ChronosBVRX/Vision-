package com.chronosbvrx.visionplus.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.EffectsMode
import com.chronosbvrx.visionplus.data.PlaybackHistory
import java.io.File

/**
 * Gestor de configuraciones y preferencias reales de Vision+.
 * Persistido en SharedPreferences y consumido de forma transversal por la UI y reproductores.
 */
object VisionSettings {
    private const val PREFS_NAME = "vision_settings"
    private const val KEY_DEFAULT_QUALITY = "default_quality"
    private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
    private const val KEY_AUTO_RESUME = "auto_resume"
    private const val KEY_DEFAULT_ASPECT = "default_aspect"
    private const val KEY_LIVE_BUFFER = "live_buffer"
    private const val KEY_DIAGNOSTICS = "diagnostics_enabled"

    val qualityOptions = listOf("Auto", "1080p", "720p", "480p")
    val aspectOptions = listOf("Ajustar (16:9)", "Rellenar pantalla")
    val liveBufferOptions = listOf("Rápido (0.5s)", "Estable (3s)")

    var defaultQuality by mutableStateOf("Auto")
    var autoPlayNext by mutableStateOf(true)
    var autoResume by mutableStateOf(true)
    var defaultAspect by mutableStateOf(0) // 0: Fit (16:9 original), 1: Fill / Zoom
    var liveBuffer by mutableStateOf("Rápido (0.5s)")
    var diagnosticsEnabled by mutableStateOf(true)
    var cacheSizeBytes by mutableLongStateOf(0L)

    var effectsMode by mutableStateOf(EffectsMode.AUTO)

    var effects: Boolean
        get() = effectsMode != EffectsMode.LIGHT
        set(value) {
            effectsMode = if (value) EffectsMode.FULL else EffectsMode.LIGHT
        }

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        defaultQuality = p.getString(KEY_DEFAULT_QUALITY, "Auto") ?: "Auto"
        autoPlayNext = p.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        autoResume = p.getBoolean(KEY_AUTO_RESUME, true)
        defaultAspect = p.getInt(KEY_DEFAULT_ASPECT, 0)
        liveBuffer = p.getString(KEY_LIVE_BUFFER, "Rápido (0.5s)") ?: "Rápido (0.5s)"
        diagnosticsEnabled = p.getBoolean(KEY_DIAGNOSTICS, true)
        effectsMode = DevicePerformance.getEffectsMode(context)
        refreshCacheSize(context)
    }

    /** Lectura directa (sin depender de [init]) para el arranque del diagnóstico. */
    fun isDiagnosticsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DIAGNOSTICS, true)

    fun setDiagnosticsEnabled(context: Context, enabled: Boolean) {
        diagnosticsEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DIAGNOSTICS, enabled).apply()
        com.chronosbvrx.visionplus.data.diag.Diagnostics.setEnabled(enabled)
    }

    fun setDefaultQuality(context: Context, quality: String) {
        defaultQuality = quality
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEFAULT_QUALITY, quality).apply()
    }

    fun setAutoPlayNext(context: Context, enabled: Boolean) {
        autoPlayNext = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
    }

    fun setAutoResume(context: Context, enabled: Boolean) {
        autoResume = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    fun setDefaultAspect(context: Context, aspect: Int) {
        defaultAspect = aspect
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DEFAULT_ASPECT, aspect).apply()
    }

    fun setLiveBuffer(context: Context, buffer: String) {
        liveBuffer = buffer
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIVE_BUFFER, buffer).apply()
    }

    fun cycleEffectsMode(context: Context) {
        val next = when (effectsMode) {
            EffectsMode.AUTO -> EffectsMode.LIGHT
            EffectsMode.LIGHT -> EffectsMode.FULL
            EffectsMode.FULL -> EffectsMode.AUTO
        }
        effectsMode = next
        DevicePerformance.setEffectsMode(context, next)
    }

    fun refreshCacheSize(context: Context) {
        cacheSizeBytes = calculateCacheSize(context)
    }

    private fun calculateCacheSize(context: Context): Long {
        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var size = 0L
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) dirSize(file) else file.length()
            }
            return size
        }
        return dirSize(context.cacheDir) + dirSize(context.externalCacheDir)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
        bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(context: Context): Boolean {
        return runCatching {
            context.cacheDir?.deleteRecursively()
            context.cacheDir?.mkdirs()
            context.externalCacheDir?.deleteRecursively()
            context.externalCacheDir?.mkdirs()
            runCatching { coil.Coil.imageLoader(context).diskCache?.clear() }
            runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
            refreshCacheSize(context)
            true
        }.getOrDefault(false)
    }

    fun clearPlaybackHistory(context: Context) {
        PlaybackHistory.clear(context)
        // El canal «Continuar viendo» del launcher también se vacía de inmediato.
        com.chronosbvrx.visionplus.data.launcher.LauncherWatchNext.syncAsync(context, force = true)
    }

    fun getNetworkInfo(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Desconocido"
        val active = cm.activeNetwork ?: return "Sin conexión"
        val caps = cm.getNetworkCapabilities(active) ?: return "Sin conexión"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
            else -> "Conectado"
        }
    }
}
