package com.chronosbvrx.visionplus.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Inactivity after which the overlay hides itself while the video plays. */
private const val INACTIVITY_MS = 4500L

/** Whether the controls overlay is on screen, and its auto-hide. Starts hidden: the video first. */
@Stable
internal class ControlsState {
    var visible by mutableStateOf(false)
        private set

    /** Bumps with each activity signal; relaunches the auto-hide countdown. */
    var activityTick by mutableIntStateOf(0)
        private set

    /** There was activity: brings the overlay and resets the countdown. */
    fun bump() {
        visible = true
        activityTick++
    }

    /** Only resets the countdown (D-pad moves inside the open overlay). */
    fun keepAlive() {
        activityTick++
    }

    fun hide() {
        visible = false
    }

    /** A tap on the video: hides the overlay if it is up, brings it if not. */
    fun toggle() {
        if (visible) hide() else bump()
    }

    /**
     * Presionar atrás con la navegación visible la oculta;
     * si ya está oculta, ejecuta la salida del reproductor.
     * Retorna true si consumió el evento (ocultó navegación).
     */
    fun handleBack(onExit: () -> Unit): Boolean {
        return if (visible) {
            hide()
            true
        } else {
            onExit()
            false
        }
    }
}

internal enum class PlayerBackAction {
    DISMISS_PICKER,
    HIDE_NAVIGATION,
    EXIT_PLAYER,
}

internal fun resolvePlayerBackAction(
    isPicking: Boolean,
    isNavigationVisible: Boolean,
): PlayerBackAction = when {
    isPicking -> PlayerBackAction.DISMISS_PICKER
    isNavigationVisible -> PlayerBackAction.HIDE_NAVIGATION
    else -> PlayerBackAction.EXIT_PLAYER
}

internal enum class LiveDpadAction {
    LIST_NAVIGATION,
    SHOW_NAVIGATION,
    SWITCH_CHANNEL,
}

internal fun resolveLiveDpadAction(
    isPicking: Boolean,
    isNavigationVisible: Boolean,
): LiveDpadAction = when {
    isPicking -> LiveDpadAction.LIST_NAVIGATION
    !isNavigationVisible -> LiveDpadAction.SHOW_NAVIGATION
    else -> LiveDpadAction.SWITCH_CHANNEL
}

internal fun calculateSeekTarget(currentPosMs: Long, deltaMs: Long, durationMs: Long): Long {
    val total = if (durationMs > 0) durationMs else Long.MAX_VALUE
    return (currentPosMs + deltaMs).coerceIn(0L, total)
}

/**
 * Calcula el salto en milisegundos para el scrubber según la duración total
 * y acelera exponencialmente cuando la tecla se mantiene presionada en el mando.
 */
internal fun calculateScrubStepMs(totalMs: Long, repeatCount: Int = 0): Long {
    val baseStep = when {
        totalMs > 90 * 60_000L -> 30_000L
        totalMs > 30 * 60_000L -> 20_000L
        else -> 10_000L
    }
    return when {
        repeatCount >= 30 -> 300_000L
        repeatCount >= 15 -> 120_000L
        repeatCount >= 5  -> 60_000L
        else -> baseStep
    }
}

internal fun resolveDismissFocusTarget(lastPickerSource: String?, isLive: Boolean): String {
    return lastPickerSource ?: if (isLive) "channels" else "playPause"
}

internal enum class PlayerKeyDownAction {
    /** El primer clic con menú oculto (o sin foco en controles) SOLO abre el menú y enfoca sin ejecutar acción. */
    WAKE_CONTROLS_ONLY,
    /** El menú ya está visible y enfocado: en TV en vivo Arriba/Abajo cambia al canal siguiente. */
    LIVE_SWITCH_NEXT_CHANNEL,
    /** El menú ya está visible y enfocado: en TV en vivo Abajo/CH- cambia al canal anterior. */
    LIVE_SWITCH_PREV_CHANNEL,
    /** El menú ya está visible y un control tiene el foco: el sistema de foco mueve el cursor o activa el botón enfocado. */
    PASS_TO_FOCUSED_CONTROL,
    /** Hay un modal, pre-roll de intro o pantalla de error activa: el evento pasa al modal/intro/error. */
    PASS_TO_MODAL_OR_SYSTEM,
    /** Tecla multimedia física dedicada (Play/Pause/Rewind/FastForward de control remoto multimedia). */
    DEDICATED_MEDIA_KEY,
}

/**
 * Política universal anti-regresión para TODOS los reproductores de Vision+
 * (`X4KPlayerAty` Live/VOD, `PlayerCore` Live/VOD, `TitanLivePlayer`, `TitanVodPlayerScreen`):
 *
 * Regla inmutable:
 * Cuando el menú de navegación está oculto (`!isControlsVisible`) o ningún control del menú tiene
 * el foco (`!hasControlFocus`), el PRIMER clic en cualquier tecla de navegación del D-pad
 * (`OK/Center`, `Enter`, `Izquierda`, `Derecha`, `Arriba`, `Abajo`, `Menu`, `CH+`, `CH-`)
 * DEBE ÚNICAMENTE hacer visible el menú de navegación y posicionar el foco (`WAKE_CONTROLS_ONLY`).
 * JAMÁS debe pausar, hacer seek ±10s, saltar intro, cambiar de canal ni salir del reproductor en el primer clic.
 */
internal fun resolvePlayerKeyDownAction(
    keyCode: Int,
    isLive: Boolean,
    isControlsVisible: Boolean,
    hasControlFocus: Boolean = isControlsVisible,
    isModalActive: Boolean = false,
    isIntroActive: Boolean = false,
    isErrorShown: Boolean = false,
): PlayerKeyDownAction {
    if (isModalActive || isIntroActive || isErrorShown) {
        return PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM
    }
    if (isDedicatedMediaKeyCode(keyCode)) {
        return PlayerKeyDownAction.DEDICATED_MEDIA_KEY
    }
    if (isDpadWakeupKeyCode(keyCode)) {
        if (!isControlsVisible || !hasControlFocus) {
            return PlayerKeyDownAction.WAKE_CONTROLS_ONLY
        }
        if (isLive) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_UP) {
                return PlayerKeyDownAction.LIVE_SWITCH_NEXT_CHANNEL
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN || keyCode == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN) {
                return PlayerKeyDownAction.LIVE_SWITCH_PREV_CHANNEL
            }
        }
        return PlayerKeyDownAction.PASS_TO_FOCUSED_CONTROL
    }
    return PlayerKeyDownAction.PASS_TO_MODAL_OR_SYSTEM
}

internal const val CONTROL_ACTION_COOLDOWN_MS = 220L

/**
 * Evita que un mismo evento de tecla (por rebote o repetición rápida de hardware)
 * ejecute inmediatamente una acción en el control recién enfocado tras abrir el menú.
 */
internal fun canExecuteControlAction(
    nowMs: Long,
    lastWakeupMs: Long,
    cooldownMs: Long = CONTROL_ACTION_COOLDOWN_MS,
): Boolean = (nowMs - lastWakeupMs) >= cooldownMs

internal fun shouldWakeControlsWithoutAction(
    keyCode: Int,
    isTv: Boolean = true,
    isControlsVisible: Boolean,
    hasControlFocus: Boolean = isControlsVisible,
    isModalActive: Boolean = false,
    isIntroActive: Boolean = false,
    isErrorShown: Boolean = false,
): Boolean {
    if (!isTv) return false
    return resolvePlayerKeyDownAction(
        keyCode = keyCode,
        isLive = false,
        isControlsVisible = isControlsVisible,
        hasControlFocus = hasControlFocus,
        isModalActive = isModalActive,
        isIntroActive = isIntroActive,
        isErrorShown = isErrorShown,
    ) == PlayerKeyDownAction.WAKE_CONTROLS_ONLY
}

internal fun isDpadWakeupKeyCode(keyCode: Int): Boolean = when (keyCode) {
    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
    android.view.KeyEvent.KEYCODE_ENTER,
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
    android.view.KeyEvent.KEYCODE_DPAD_UP,
    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
    android.view.KeyEvent.KEYCODE_MENU,
    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
    android.view.KeyEvent.KEYCODE_INFO,
    android.view.KeyEvent.KEYCODE_GUIDE,
    -> true
    else -> false
}

internal fun isDedicatedMediaKeyCode(keyCode: Int): Boolean = when (keyCode) {
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
    android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    android.view.KeyEvent.KEYCODE_HEADSETHOOK,
    android.view.KeyEvent.KEYCODE_SPACE,
    -> true
    else -> false
}

internal fun hasNextEpisodeAvailable(
    isLive: Boolean,
    curEpIndex: Int,
    epCount: Int,
    curSeries: String,
    curSeasonIndex: Int,
    seasonCount: Int,
): Boolean {
    if (isLive) return false
    if (!isSeriesContent(epCount, seasonCount)) return false
    val hasEp = curEpIndex + 1 < epCount
    val hasSeason = curSeries.isNotBlank() && curSeasonIndex + 1 < seasonCount
    return hasEp || hasSeason
}

/**
 * Fuente única de verdad serie vs película en Vision+:
 * solo hay serie cuando existen 2+ capítulos o 2+ temporadas.
 * Un `seriesId` suelto, un único capítulo o una única temporada
 * (típico de películas contaminadas por el portal) es película.
 */
internal fun isSeriesContent(epCount: Int, seasonCount: Int): Boolean =
    epCount > 1 || seasonCount > 1

/**
 * Textos de la pantalla de espera del reproductor nativo. Antes decía «Preparando tu
 * película…» incluso al abrir una serie y el título quedaba congelado en el primer
 * capítulo de la sesión: con el contexto real del capítulo la espera se siente premium.
 */
internal data class LoadingCardText(
    val title: String,
    val subtitle: String,
    /** Nombre real del capítulo cuando el portal trae uno ("" si es genérico o no hay). */
    val episodeName: String,
    /** true cuando el subtítulo es el contexto temporada/capítulo (se pinta en azul de marca). */
    val accent: Boolean,
)

internal fun resolveLoadingCardText(
    isLive: Boolean,
    isSeries: Boolean,
    baseTitle: String,
    rawTitle: String,
    seasonLabel: String,
    episodeIndex: Int,
    episodeName: String = "",
): LoadingCardText {
    val raw = rawTitle.trim()
    if (isLive) return LoadingCardText(raw, "Preparando la señal…", "", accent = false)
    if (!isSeries) return LoadingCardText(raw, "Preparando tu película…", "", accent = false)
    val episode = "Capítulo ${episodeIndex.coerceAtLeast(0) + 1}"
    val season = seasonLabel.trim()
    return LoadingCardText(
        title = baseTitle.trim().ifBlank { raw },
        subtitle = if (season.isBlank()) episode else "$season · $episode",
        episodeName = cleanEpisodeName(episodeName, episodeIndex),
        accent = true,
    )
}

/**
 * Un nombre de capítulo que solo repite el número («Capítulo 8», «Episodio 8», «8») no
 * aporta nada a la espera: se descarta y queda solo el contexto temporada/capítulo.
 */
internal fun cleanEpisodeName(name: String, episodeIndex: Int): String {
    val candidate = name.trim()
    if (candidate.isBlank()) return ""
    val n = episodeIndex.coerceAtLeast(0) + 1
    return if (candidate.lowercase() in setOf("capítulo $n", "capitulo $n", "episodio $n", "$n")) "" else candidate
}

internal fun shouldPromptResume(
    isLive: Boolean,
    isSeries: Boolean,
    hasEntry: Boolean,
    shouldKeepEntry: Boolean,
    resumeExplicitlyRequested: Boolean,
): Boolean {
    if (isLive) return false
    if (!hasEntry || !shouldKeepEntry) return false
    // Si el usuario pidió explícitamente reanudar (ej. clic en «Seguir viendo»), se aplica directo sin preguntar
    if (resumeExplicitlyRequested) return false
    // En series, novelas y anime (al dar play a un episodio o pasar al siguiente), nunca interrumpe con el diálogo
    if (isSeries) return false
    // En películas sueltas iniciadas por primera vez sin resume previo explícito, muestra la opción si hay historial previo
    return true
}

/**
 * Determina si la duración reportada por el motor multimedia (IjkPlayer / ExoPlayer)
 * corresponde a la ventana temporal del búfer demuxer deslizante (buffer window)
 * en lugar de la duración total real del video (película/serie).
 *
 * En transmisiones MPEG-TS / HLS sin cabecera de duración fija en el contenedor,
 * IjkPlayer evalúa la duración como (posición_actual + audio_cached_duration),
 * retornando exactamente ~120 s (2 minutos) por encima del minuto actual.
 *
 * Si engineDur está dentro de 180s de la posición actual, o si es sustancialmente menor
 * a la duración declarada conocida (ej. < 75% de declaredDurationMs), debe descartarse.
 */
internal fun isBufferWindowDuration(
    engineDur: Long,
    currentPos: Long,
    declaredDurationMs: Long,
    isLive: Boolean = false,
): Boolean {
    if (isLive) return false
    if (engineDur <= 0L) return true
    if (engineDur <= currentPos + 180_000L) return true
    if (declaredDurationMs > 0L && engineDur < (declaredDurationMs * 0.75f).toLong()) return true
    return false
}

/**
 * Resuelve la duración canónica inmutable que debe mostrar la barra de reproducción
 * y el contador de tiempo total, protegiendo la UI contra alternancias y parpadeos
 * causados por el búfer deslizante del decodificador.
 */
internal fun resolveStableTotalDuration(
    declaredDurationMs: Long,
    engineDurationMs: Long,
    currentPos: Long,
    isLive: Boolean = false,
): Long {
    if (isLive) return 0L
    if (declaredDurationMs > 0L) return declaredDurationMs
    if (!isBufferWindowDuration(engineDurationMs, currentPos, declaredDurationMs, isLive)) {
        return engineDurationMs
    }
    return 0L
}

/**
 * Formato corto para etiquetas de píldoras y botones de calidad (ej. "1080p", "720p", "480p", "4K", "Auto").
 */
internal fun formatQualityShort(raw: String): String {
    val q = raw.trim().lowercase()
    return when {
        q.contains("1080") || q.contains("fhd") -> "1080p"
        q.contains("720") || q.contains("hd") -> "720p"
        q.contains("480") || q.contains("sd") -> "480p"
        q.contains("4k") || q.contains("2160") || q.contains("uhd") -> "4K"
        q.contains("360") -> "360p"
        q.isNotBlank() -> raw.trim()
        else -> "Auto"
    }
}

/**
 * Formato legible para el menú y selector de calidad (ej. "1080p FHD", "720p HD", "480p SD", "4K Ultra HD").
 */
internal fun formatQualityLabel(raw: String): String {
    val q = raw.trim().lowercase()
    return when {
        q.contains("1080") || q.contains("fhd") -> "1080p FHD"
        q.contains("720") || q.contains("hd") -> "720p HD"
        q.contains("480") || q.contains("sd") -> "480p SD"
        q.contains("4k") || q.contains("2160") || q.contains("uhd") -> "4K Ultra HD"
        q.contains("360") -> "360p SD"
        q.isNotBlank() -> raw.trim().uppercase()
        else -> "Automática (Alta definición)"
    }
}

@Composable
internal fun rememberControlsState(): ControlsState = remember { ControlsState() }

/** Hides the overlay after [INACTIVITY_MS] without activity, only while playing: paused, it stays. */
@Composable
internal fun AutoHideEffect(state: ControlsState, playing: Boolean) {
    LaunchedEffect(state.activityTick, playing, state.visible) {
        if (state.visible && playing) {
            delay(INACTIVITY_MS)
            state.hide()
        }
    }
}
