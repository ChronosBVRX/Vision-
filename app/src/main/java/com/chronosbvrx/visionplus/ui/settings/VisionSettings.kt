package com.chronosbvrx.visionplus.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private const val KEY_NEXT_ON_CREDITS = "next_on_credits"
    private const val KEY_AUTO_RESUME = "auto_resume"
    private const val KEY_DEFAULT_ASPECT = "default_aspect"
    private const val KEY_LIVE_BUFFER = "live_buffer"
    private const val KEY_DIAGNOSTICS = "diagnostics_enabled"
    private const val KEY_SKIP_INTRO_MODE = "skip_intro_mode"
    private const val KEY_SUB_SIZE = "sub_size"
    private const val KEY_SUB_POSITION = "sub_position"
    private const val KEY_SUB_OFFSET_MS = "sub_offset_ms"
    private const val KEY_AUTO_DOWNLOAD_NEXT = "auto_download_next"
    private const val KEY_PIP_DOWNLOADS = "pip_downloads"
    private const val KEY_IMAGE_PRESET = "x4k_image_preset"
    private const val KEY_IMAGE_PRESET_USER_SET = "x4k_image_preset_user_set"
    private const val KEY_ADVANCED_RENDERER = "x4k_advanced_renderer"

    val qualityOptions = listOf("Auto", "1080p", "720p", "480p")
    /** Modos del motor X4K: 0 original, 1 rellenar, 4 16:9, 5 4:3. */
    val aspectModes = listOf(0, 1, 4, 5)
    val aspectOptions = listOf("Original (ajustar)", "Rellenar pantalla", "16:9", "4:3")
    val liveBufferOptions = listOf("Rápido (0.5s)", "Estable (3s)")

    /** «Omitir intro»: botón durante la entrada (como Netflix), automático o desactivado. */
    const val SKIP_INTRO_BUTTON = "button"
    const val SKIP_INTRO_AUTO = "auto"
    const val SKIP_INTRO_NEVER = "never"
    /**
     * Por defecto se PREGUNTA: el capítulo no se mueve solo. El ajuste se guarda solo al
     * tocarlo, así que este default manda en toda instalación que nunca lo cambió — y
     * «Automático» queda como elección explícita de quien lo activa.
     */
    const val SKIP_INTRO_DEFAULT = SKIP_INTRO_BUTTON
    val skipIntroModeOptions = listOf(SKIP_INTRO_BUTTON, SKIP_INTRO_AUTO, SKIP_INTRO_NEVER)

    var defaultQuality by mutableStateOf("Auto")
    var autoPlayNext by mutableStateOf(true)
    /** Modo rápido: empieza el siguiente en créditos. Apagado = espera al final (seguro). */
    var nextOnCredits by mutableStateOf(false)
    var autoResume by mutableStateOf(true)
    var defaultAspect by mutableStateOf(0) // 0: Fit (16:9 original), 1: Fill / Zoom
    var liveBuffer by mutableStateOf("Rápido (0.5s)")
    var diagnosticsEnabled by mutableStateOf(true)
    var skipIntroMode by mutableStateOf(SKIP_INTRO_DEFAULT)
    /** Subtítulos: tamaño y posición reales (ahora los pinta la app, no el motor). */
    const val SUB_SIZE_SMALL = "small"
    const val SUB_SIZE_NORMAL = "normal"
    const val SUB_SIZE_LARGE = "large"
    const val SUB_POS_BOTTOM = "bottom"
    const val SUB_POS_TOP = "top"
    var subSize by mutableStateOf(SUB_SIZE_NORMAL)
    var subPosition by mutableStateOf(SUB_POS_BOTTOM)
    /** Desfase de sincronía en ms (+ retrasa el subtítulo): pasos de 500, tope ±5 s. */
    var subOffsetMs by mutableIntStateOf(0)
    /** Con WiFi, al terminar un capítulo baja solo el siguiente (un paso de maratón). */
    var autoDownloadNext by mutableStateOf(false)
    /**
     * Picture-in-picture en descargas: apagado por defecto. El búnker usa `FLAG_SECURE`
     * para impedir capturas/grabación; habilitar esto quita esa protección SOLO mientras
     * el cuadro flotante está visible (decisión explícita del usuario, no del sistema).
     */
    var pipDownloads by mutableStateOf(false)
    var cacheSizeBytes by mutableLongStateOf(0L)

    // Mejora de imagen del reproductor nativo X4K (Nivel 1: matriz de color sobre TextureView).
    const val IMAGE_OFF = "off"
    const val IMAGE_CINE = "cine"
    const val IMAGE_NITIDO = "nitido"
    const val IMAGE_NIGHT = "night"
    const val IMAGE_VIVID = "vivid"
    const val IMAGE_CALIDO = "calido"
    const val IMAGE_FRIO = "frio"
    const val IMAGE_DEFAULT = IMAGE_OFF
    val imagePresetOptions =
        listOf(IMAGE_CINE, IMAGE_NITIDO, IMAGE_NIGHT, IMAGE_VIVID, IMAGE_CALIDO, IMAGE_FRIO, IMAGE_OFF)
    var imagePreset by mutableStateOf(IMAGE_DEFAULT)
    /** Renderer GL propio (experimental) para shaders en todas las versiones. */
    var advancedRenderer by mutableStateOf(false)

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
        nextOnCredits = p.getBoolean(KEY_NEXT_ON_CREDITS, false)
        autoResume = p.getBoolean(KEY_AUTO_RESUME, true)
        defaultAspect = p.getInt(KEY_DEFAULT_ASPECT, 0)
        liveBuffer = p.getString(KEY_LIVE_BUFFER, "Rápido (0.5s)") ?: "Rápido (0.5s)"
        diagnosticsEnabled = p.getBoolean(KEY_DIAGNOSTICS, true)
        skipIntroMode = p.getString(KEY_SKIP_INTRO_MODE, SKIP_INTRO_DEFAULT) ?: SKIP_INTRO_DEFAULT
        subSize = p.getString(KEY_SUB_SIZE, SUB_SIZE_NORMAL) ?: SUB_SIZE_NORMAL
        subPosition = p.getString(KEY_SUB_POSITION, SUB_POS_BOTTOM) ?: SUB_POS_BOTTOM
        subOffsetMs = p.getInt(KEY_SUB_OFFSET_MS, 0)
        autoDownloadNext = p.getBoolean(KEY_AUTO_DOWNLOAD_NEXT, false)
        pipDownloads = p.getBoolean(KEY_PIP_DOWNLOADS, false)
        imagePreset = imagePresetOf(context)
        advancedRenderer = p.getBoolean(KEY_ADVANCED_RENDERER, false)
        effectsMode = DevicePerformance.getEffectsMode(context)
        refreshCacheSize(context)
    }

    /** Lectura directa (sin depender de [init]) para el proceso del reproductor `:x4k`. */
    fun skipIntroModeOf(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SKIP_INTRO_MODE, SKIP_INTRO_DEFAULT) ?: SKIP_INTRO_DEFAULT

    /** Lectura directa del auto-avance (el reproductor nativo lo honra de verdad). */
    fun autoPlayNextOf(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PLAY_NEXT, true)

    /** Modo rápido: el siguiente arranca en créditos; apagado espera al final. */
    fun nextOnCreditsOf(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NEXT_ON_CREDITS, false)

    /** Lectura directa de «reanudar automáticamente» (el búnker también lo honra). */
    fun autoResumeOf(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RESUME, true)

    fun skipIntroModeLabel(mode: String): String = when (mode) {
        SKIP_INTRO_AUTO -> "Automático"
        SKIP_INTRO_NEVER -> "Nunca"
        else -> "Botón"
    }

    fun nextSkipIntroMode(mode: String): String = when (mode) {
        SKIP_INTRO_BUTTON -> SKIP_INTRO_AUTO
        SKIP_INTRO_AUTO -> SKIP_INTRO_NEVER
        else -> SKIP_INTRO_BUTTON
    }

    fun setSkipIntroMode(context: Context, mode: String) {
        skipIntroMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIP_INTRO_MODE, mode).apply()
    }

    fun subSizeLabel(size: String): String = when (size) {
        SUB_SIZE_SMALL -> "Chico"
        SUB_SIZE_LARGE -> "Grande"
        else -> "Normal"
    }

    fun nextSubSize(size: String): String = when (size) {
        SUB_SIZE_SMALL -> SUB_SIZE_NORMAL
        SUB_SIZE_NORMAL -> SUB_SIZE_LARGE
        else -> SUB_SIZE_SMALL
    }

    /** Tamaño real en sp según equipo (base TV 26 / móvil 16). Puro, testeable. */
    fun subSizeSp(size: String, isTv: Boolean): Float {
        val base = if (isTv) 26f else 16f
        return when (size) {
            SUB_SIZE_SMALL -> base * 0.8f
            SUB_SIZE_LARGE -> base * 1.3f
            else -> base
        }
    }

    fun setSubSize(context: Context, size: String) {
        subSize = size
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SUB_SIZE, size).apply()
    }

    fun setSubPosition(context: Context, position: String) {
        subPosition = position
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SUB_POSITION, position).apply()
    }

    /** Sincronía con tope ±5 s y pasos de 500 ms. Puro, testeable. */
    fun clampSubOffset(ms: Int): Int = ms.coerceIn(-5_000, 5_000) / 500 * 500

    fun setSubOffsetMs(context: Context, ms: Int) {
        subOffsetMs = clampSubOffset(ms)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SUB_OFFSET_MS, subOffsetMs).apply()
    }

    /** Lecturas directas (sin depender de [init]) para el proceso `:x4k`. */
    fun subSizeOf(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUB_SIZE, SUB_SIZE_NORMAL) ?: SUB_SIZE_NORMAL

    fun subPositionOf(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUB_POSITION, SUB_POS_BOTTOM) ?: SUB_POS_BOTTOM

    fun subOffsetMsOf(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SUB_OFFSET_MS, 0)

    fun autoDownloadNextOf(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_DOWNLOAD_NEXT, false)

    fun setAutoDownloadNext(context: Context, enabled: Boolean) {
        autoDownloadNext = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_DOWNLOAD_NEXT, enabled).apply()
    }

    /** Lectura directa para el proceso `:x4k` y para el reproductor ExoPlayer. */
    fun pipDownloadsAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PIP_DOWNLOADS, false)

    fun setPipDownloads(context: Context, enabled: Boolean) {
        pipDownloads = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PIP_DOWNLOADS, enabled).apply()
    }

    /** Normaliza el preset guardado; cualquier valor desconocido vuelve al default seguro. */
    fun normalizeImagePreset(raw: String?): String = when (raw?.trim()?.lowercase()) {
        IMAGE_OFF -> IMAGE_OFF
        IMAGE_NITIDO -> IMAGE_NITIDO
        IMAGE_NIGHT -> IMAGE_NIGHT
        IMAGE_VIVID -> IMAGE_VIVID
        IMAGE_CALIDO -> IMAGE_CALIDO
        IMAGE_FRIO -> IMAGE_FRIO
        IMAGE_CINE -> IMAGE_CINE
        else -> IMAGE_DEFAULT
    }

    /** Lectura directa para el proceso `:x4k`, sin depender de [init]. */
    fun imagePresetOf(context: Context): String {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 2.5.235 aplicaba Cine por defecto. Un filtro de color en la TextureView puede
        // hacer que el vídeo pierda frames en equipos modestos y el audio se adelante.
        // A partir de 2.5.236 solo se activa si la persona lo eligió explícitamente.
        if (!p.getBoolean(KEY_IMAGE_PRESET_USER_SET, false)) return IMAGE_OFF
        return normalizeImagePreset(p.getString(KEY_IMAGE_PRESET, IMAGE_DEFAULT))
    }

    fun setImagePreset(context: Context, preset: String) {
        val clean = normalizeImagePreset(preset)
        imagePreset = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMAGE_PRESET, clean)
            .putBoolean(KEY_IMAGE_PRESET_USER_SET, true)
            .apply()
    }

    fun advancedRendererOf(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ADVANCED_RENDERER, false)

    fun setAdvancedRenderer(context: Context, enabled: Boolean) {
        advancedRenderer = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ADVANCED_RENDERER, enabled).apply()
    }

    fun imagePresetLabel(preset: String): String = when (normalizeImagePreset(preset)) {
        IMAGE_OFF -> "Original"
        IMAGE_NITIDO -> "Nítido"
        IMAGE_NIGHT -> "Noche"
        IMAGE_VIVID -> "Vivo"
        IMAGE_CALIDO -> "Cálido"
        IMAGE_FRIO -> "Frío"
        else -> "Original"
    }

    // ------------------------------------------------- idioma de audio por serie
    // Se recuerda la última elección por serie (el anime en japonés no se re-elige
    // en cada capítulo). Mismo prefs: lo escribe y lo lee el proceso `:x4k`.

    private const val KEY_AUDIO_PREFIX = "audio_lang_"

    fun audioLangKey(seriesId: String): String =
        KEY_AUDIO_PREFIX + seriesId.trim().take(64).replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun audioLangOf(context: Context, seriesId: String): String {
        if (seriesId.isBlank()) return ""
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(audioLangKey(seriesId), "").orEmpty()
    }

    fun setAudioLang(context: Context, seriesId: String, lang: String) {
        if (seriesId.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(audioLangKey(seriesId), lang.trim().take(64)).apply()
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

    fun setNextOnCredits(context: Context, enabled: Boolean) {
        nextOnCredits = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NEXT_ON_CREDITS, enabled).apply()
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

    fun aspectLabel(aspect: Int): String {
        val index = aspectModes.indexOf(aspect)
        return aspectOptions.getOrElse(if (index >= 0) index else 0) { aspectOptions[0] }
    }

    fun nextAspect(aspect: Int): Int {
        val index = aspectModes.indexOf(aspect).coerceAtLeast(0)
        return aspectModes[(index + 1) % aspectModes.size]
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
