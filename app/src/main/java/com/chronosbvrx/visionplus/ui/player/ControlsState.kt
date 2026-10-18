package com.chronosbvrx.visionplus.ui.player

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.data.normalizeDisplayName
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

/**
 * Salida del reproductor residente («Camino B»): normalmente la actividad se queda viva
 * detrás con su vista del motor (la app principal vuelve al frente y el próximo play
 * reutiliza la misma vista); solo el auto-reparado —estado del motor rotado a una
 * generación nueva— pide cerrar y reiniciar el proceso para estrenar carpeta limpia.
 */
internal enum class PlayerExitAction {
    HIDE_RESIDENT,
    FINISH_AND_RELAUNCH,
}

internal fun exitActionFor(hardReset: Boolean): PlayerExitAction =
    if (hardReset) PlayerExitAction.FINISH_AND_RELAUNCH else PlayerExitAction.HIDE_RESIDENT

/**
 * El reproductor residente ya salió (`exiting = true`) y la actividad vuelve a
 * primer plano SIN reproducción: no puede quedarse en un fondo negro sin salida.
 * En ese estado Atrás (y el propio regreso al frente) deben cerrar la tarea.
 */
internal fun shouldFinishIdleResidentOnExit(exiting: Boolean, isPlayerActive: Boolean): Boolean =
    exiting && !isPlayerActive

/**
 * El reproductor quedó DETRÁS mientras reproducía (algo lo tapó: el control remoto trajo el
 * menú al frente para abrir otro título, el usuario salió a la pantalla de inicio, la tele se
 * apagó): el video no puede seguir sonando invisible — es el «se sigue escuchando de fondo
 * pero ya estamos en el menú». Se suelta el motor con el minuto ya guardado y la actividad
 * queda residente para el siguiente play. Tras una salida normal (`exiting`) no hay nada que
 * soltar: el motor ya quedó detenido por `exitPlayer`.
 */
internal fun shouldParkPlaybackOnStop(isPlayerActive: Boolean, exiting: Boolean): Boolean =
    isPlayerActive && !exiting

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

/** Qué hace un toque en la pantalla del reproductor móvil (decisión pura, testeable). */
internal enum class MobileTapAction {
    /** Mostrar los controles YA: un toque nunca debe sentirse muerto. */
    SHOW_NOW,
    /** Ocultar tras el timeout del doble toque: si llega el segundo (∓10 s), la barra no parpadea. */
    HIDE_DELAYED,
    /** Hay modal, pre-roll o error: el toque no toca los controles. */
    IGNORE,
}

/**
 * Política del toque móvil en el reproductor XTV (regresión v2.5.77→2.5.82: con
 * `onSingleTapConfirmed` se esperaba el timeout del doble toque y un segundo toque cancelaba
 * el aviso — el usuario sentía que «los controles ya no aparecen»). Mostrar SIEMPRE es
 * inmediato; ocultar sí espera, para que el doble toque de ∓10 s no quite la barra.
 */
internal fun resolveMobileTapAction(controlsVisible: Boolean, blocked: Boolean): MobileTapAction = when {
    blocked -> MobileTapAction.IGNORE
    !controlsVisible -> MobileTapAction.SHOW_NOW
    else -> MobileTapAction.HIDE_DELAYED
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
 * Un «trago» de tecla de despertar (swallow) que nunca recibió su UP —mandos que no lo
 * envían, o el UP perdido en el aire— no puede dejar esa tecla muerta para siempre: pasado
 * el tope se libera y la siguiente pulsación vuelve a procesarse normal. Las repeticiones
 * de una tecla sostenida refrescan la marca, así el tope solo salta cuando la tecla dejó
 * de llegar sin cerrar el toque.
 */
internal const val SWALLOW_WAKEUP_TIMEOUT_MS = 1_200L

internal fun shouldClearSwallowedWakeup(
    nowMs: Long,
    wakeupAtMs: Long,
    timeoutMs: Long = SWALLOW_WAKEUP_TIMEOUT_MS,
): Boolean = nowMs - wakeupAtMs > timeoutMs

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
    val raw = normalizeDisplayName(rawTitle)
    if (isLive) return LoadingCardText(raw, "Preparando la señal…", "", accent = false)
    if (!isSeries) return LoadingCardText(raw, "Preparando tu película…", "", accent = false)
    val episode = "Capítulo ${episodeIndex.coerceAtLeast(0) + 1}"
    val season = seasonLabel.trim()
    val title = normalizeDisplayName(baseTitle).ifBlank { raw }
    return LoadingCardText(
        title = title,
        subtitle = if (season.isBlank()) episode else "$season · $episode",
        episodeName = cleanEpisodeName(episodeName, episodeIndex, seriesTitle = title),
        accent = true,
    )
}

/** Marca técnica del portal al final del nombre del capítulo: «… T1_11» (ya normalizado: «… T1 11»). */
private val EPISODE_MARK_REGEX = Regex("""(?:\s*[-–—·:]\s*)?[Tt]\d{1,2}\s+\d{1,3}\s*$""")

/** Prefijo de temporada pegado al nombre de la serie: «Ley y orden: UVE T1». */
private val SERIES_SEASON_SUFFIX = Regex("""\s*[Tt]\d{1,2}\s*$""")

private fun stripEpisodeMark(name: String): String =
    EPISODE_MARK_REGEX.replace(name, "").trim().trim('-', '–', '—', '·', ':').trim()

/** El nombre base de la serie sin su marca de temporada (para comparar, no para pintar). */
private fun seriesBase(name: String): String =
    normalizeDisplayName(name).replace(SERIES_SEASON_SUFFIX, "").trim()

/**
 * Nombre de capítulo listo para pintar. El portal rotula sus capítulos con la marca
 * técnica «<Serie> T<temp>_<cap>» («Ley y orden: UVE T1_11»): el guion bajo y la marca
 * no son un nombre. Se normaliza el texto, se quita la marca y, si lo que queda solo
 * repite el número o el nombre de la serie, se devuelve "" (la UI cae al genérico o
 * esconde la línea, como con cualquier capítulo sin nombre propio).
 */
internal fun cleanEpisodeName(name: String, episodeIndex: Int, seriesTitle: String = ""): String {
    val candidate = stripEpisodeMark(normalizeDisplayName(name))
    if (candidate.isBlank()) return ""
    val n = episodeIndex.coerceAtLeast(0) + 1
    if (candidate.lowercase() in setOf("capítulo $n", "capitulo $n", "episodio $n", "episode $n", "$n")) return ""
    if (seriesTitle.isNotBlank() && seriesBase(candidate).equals(seriesBase(seriesTitle), ignoreCase = true)) return ""
    return candidate
}

/**
 * Fila de capítulo de una ficha o de un selector: el nombre real normalizado; cuando el
 * portal no trae nombre propio (o solo su marca técnica) cae al clásico «Capítulo N».
 */
internal fun episodeRowLabel(rawName: String, epNumber: Int, seriesTitle: String = ""): String {
    val clean = cleanEpisodeName(rawName, (epNumber - 1).coerceAtLeast(0), seriesTitle)
    return clean.ifBlank { "Capítulo ${epNumber.coerceAtLeast(1)}" }
}

/**
 * Enlaza el foco del D-pad con la píldora «Omitir intro / resumen». Regresión real (2.5.83):
 * la píldora se volvía enfocable pero NINGÚN control apuntaba a ella, así que el mando jamás
 * podía llegar. Entrada: «arriba» desde la barra (la píldora vive sobre ella) mientras esté
 * visible y enfocable. Salida: «abajo» vuelve a play/pausa; izquierda/derecha/arriba se quedan
 * en la píldora (no se escapa). Con la píldora oculta, «arriba» se queda en la barra.
 */
internal fun wireSkipPillFocus(seek: View?, pill: View?, pillFocusable: Boolean, playPauseId: Int) {
    val pillId = pill?.id ?: View.NO_ID
    if (pill != null && pillId != View.NO_ID) {
        pill.nextFocusLeftId = pillId
        pill.nextFocusRightId = pillId
        pill.nextFocusUpId = pillId
        pill.nextFocusDownId = if (playPauseId != View.NO_ID) playPauseId else pillId
    }
    val seekId = seek?.id ?: View.NO_ID
    if (seek != null && seekId != View.NO_ID && pillId != View.NO_ID) {
        seek.nextFocusUpId = if (pillFocusable) pillId else seekId
    }
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
