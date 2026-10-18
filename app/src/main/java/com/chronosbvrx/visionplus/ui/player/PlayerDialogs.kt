package com.chronosbvrx.visionplus.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs

/** The copies of a video the "Servidor" section lists. */
internal val SERVERS = listOf("Opción 1", "Opción 2", "Opción 3")

/** One step of "Sincronizar subtítulos", and the bigger one. */
internal const val SUBTITLE_STEP_MS = 100
internal const val SUBTITLE_BIG_STEP_MS = 500

internal val QUALITIES = listOf("Auto", "1080p", "720p", "480p")

/** What the "Audio y subtítulos" menu shows for this video: the subtitle offset, copy and video quality. */
@Stable
internal class PlayerMenuState {
    var subtitleDelayMs by mutableIntStateOf(0)
    var server by mutableIntStateOf(0)
    var quality by mutableStateOf("Auto")

    /** The copy being opened after a pick, while its note shows; null when none is. */
    var trying by mutableStateOf<Int?>(null)
}

@Composable
internal fun rememberPlayerMenuState(
    initialQuality: String = com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality,
): PlayerMenuState = remember { PlayerMenuState().apply { quality = initialQuality } }

/** "+0,5 s", "−1,2 s", "0 s". */
internal fun subtitleDelayLabel(ms: Int): String {
    if (ms == 0) return "0 s"
    val sign = if (ms > 0) "+" else "−"
    val tenths = abs(ms) / 100
    return "$sign${tenths / 10},${tenths % 10} s"
}

/** The note while another copy opens, as the automatic fallback words it. */
internal fun tryingServerNote(index: Int): String = "Probando otro servidor: ${SERVERS[index]} (${index + 1} de ${SERVERS.size})…"

/** "Audio y subtítulos": the copy (Servidor), video quality, the audio and subtitle tracks, and subtitle sync. */
@Composable
internal fun AudioAndSubtitlesDialog(state: PlayerMenuState, onDismiss: () -> Unit) {
    val first = rememberDialogLanding()
    LaunchedEffect(state.trying) {
        val next = state.trying ?: return@LaunchedEffect
        delay(1_500)
        state.server = next
        state.trying = null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisionSurface,
        title = { Text("Audio, subtítulos y calidad") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MenuSectionTitle("Servidor", first = true)
                SERVERS.forEachIndexed { i, label ->
                    MenuOption(
                        label + (if (i == state.trying) " (abriendo)" else ""),
                        modifier = if (i == 0) first else Modifier,
                        selected = i == state.server,
                    ) { if (i != state.server && state.trying == null) state.trying = i }
                }
                state.trying?.let { MenuNote(tryingServerNote(it)) }

                MenuSectionTitle("Calidad de video")
                QUALITIES.forEach { q ->
                    val isSelected = (q == state.quality)
                    val label = when (q) {
                        "Auto" -> "Auto (Automática según red)"
                        "1080p" -> "1080p FHD"
                        "720p" -> "720p HD"
                        "480p" -> "480p SD"
                        else -> q
                    }
                    MenuOption(label, selected = isSelected) {
                        state.quality = q
                    }
                }

                MenuSectionTitle("Audio")
                MenuOption("Original", selected = true) {}

                MenuSectionTitle("Subtítulos del archivo")
                MenuNote("Este archivo no trae subtítulos embebidos.")
                MenuOption("Desactivar", selected = true) {}

                MenuSectionTitle("Sincronizar subtítulos: ${subtitleDelayLabel(state.subtitleDelayMs)}")
                MenuNote("Si el texto sale antes que la voz, súmale tiempo; si sale después, réstale.")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        -SUBTITLE_BIG_STEP_MS to "−0,5 s",
                        -SUBTITLE_STEP_MS to "−0,1 s",
                        SUBTITLE_STEP_MS to "+0,1 s",
                        SUBTITLE_BIG_STEP_MS to "+0,5 s",
                    ).forEach { (delta, label) ->
                        MenuOption(label, fill = false) { state.subtitleDelayMs += delta }
                    }
                }
                if (state.subtitleDelayMs != 0) MenuOption("Quitar el ajuste") { state.subtitleDelayMs = 0 }
            }
        },
        confirmButton = { MenuOption("Cerrar", fill = false, onClick = onDismiss) },
    )
}

/**
 * "Canales del evento": las señales ordenadas del mismo partido (guía de la parrilla +
 * enlaces directos). La actual va marcada; elegir otra cambia en el mismo reproductor
 * (zapping), sin salir de la pantalla.
 */
@Composable
internal fun ZapListDialog(
    targets: List<ZapTarget>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogLanding()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisionSurface,
        title = { Text("Canales del evento") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MenuSectionTitle("Señales en vivo", first = true)
                targets.forEachIndexed { i, target ->
                    MenuOption(
                        target.name,
                        modifier = if (i == currentIndex) first else Modifier,
                        selected = i == currentIndex,
                        detail = zapSignalType(target.url, target.drmType, target.drmKey),
                    ) { if (i != currentIndex) onPick(i) }
                }
            }
        },
        confirmButton = { MenuOption("Cerrar", fill = false, onClick = onDismiss) },
    )
}

/**
 * "Información" del partido en el reproductor en vivo: la misma idea que la ficha de canal de la
 * TV en vivo (qué se está viendo y qué más hay), con lo que un partido sabe contar — la liga, el
 * marcador, dónde se transmite y las señales del evento.
 */
@Composable
internal fun GeeMatchInfoDialog(
    title: String,
    subtitle: String,
    league: String,
    scoreLine: String,
    guide: List<String>,
    signalName: String,
    signalType: String,
    signals: List<String>,
    currentIndex: Int,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogLanding()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisionSurface,
        title = { Text("Información") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MenuNote(title)
                if (subtitle.isNotBlank()) MenuNote(subtitle)
                if (league.isNotBlank()) MenuRow("Liga", league)
                if (scoreLine.isNotBlank()) MenuRow("Marcador", scoreLine)
                MenuRow("Señal en pantalla", listOf(signalName, signalType).filter { it.isNotBlank() }.joinToString(" · "))
                if (guide.isNotEmpty()) MenuRow("Se transmite en", guide.take(4).joinToString(" · "))
                if (signals.size > 1) {
                    MenuSectionTitle("Señales del evento (${signals.size})")
                    signals.forEachIndexed { index, name ->
                        MenuOption(
                            name,
                            modifier = if (index == 0) first else Modifier,
                            selected = index == currentIndex,
                        ) {}
                    }
                }
            }
        },
        confirmButton = { MenuOption("Cerrar", fill = false, onClick = onDismiss) },
    )
}

/** Una fila «etiqueta: valor» de la ficha, con la etiqueta en el azul de la casa. */
@Composable
private fun MenuRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            color = VisionBlueBright,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(132.dp),
        )
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Corregir intro y outro": marks where the opening ends or the ending starts, at the current position. */
@Composable
internal fun SkipMarkersDialog(positionLabel: String, onPick: (intro: Boolean) -> Unit, onDismiss: () -> Unit) {
    val first = rememberDialogLanding()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisionSurface,
        title = { Text("Corregir intro y outro") },
        text = {
            Column {
                MenuNote("Se marca en el punto donde vas ($positionLabel).")
                MenuOption("Setear intro (fin del opening)", modifier = first) { onPick(true) }
                MenuOption("Setear outro (inicio del ending)") { onPick(false) }
            }
        },
        confirmButton = { MenuOption("Cancelar", fill = false, onClick = onDismiss) },
    )
}

/**
 * The modifier for a dialog's first option: on a TV it takes the D-pad focus once the dialog's window has it (a few
 * frames late; earlier requests are dropped). On a touch screen nothing takes focus and this does nothing.
 */
@Composable
private fun rememberDialogLanding(): Modifier {
    val requester = remember { FocusRequester() }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (held) return@LaunchedEffect
            runCatching { requester.requestFocus() }
            delay(50)
        }
    }
    return Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) held = true }
}

@Composable
private fun MenuSectionTitle(text: String, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = VisionBlue,
        modifier = Modifier.padding(top = if (first) 0.dp else 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, modifier = Modifier.padding(vertical = 4.dp))
}

/** A menu choice: tappable on a phone, and red while it holds the D-pad focus on a TV. */
@Composable
private fun MenuOption(
    label: String,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    selected: Boolean = false,
    detail: String = "",
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) VisionBlue else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        if (selected) {
            Icon(
                VisionIcons.Check,
                contentDescription = null,
                tint = if (focused) Color.White else VisionBlueBright,
                modifier = Modifier.size(16.dp),
            )
        }
        Column {
            Text(label, color = Color.White, maxLines = 2)
            if (detail.isNotBlank()) {
                Text(detail, color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}
