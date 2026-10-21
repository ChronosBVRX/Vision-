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

/**
 * Una pista REAL del video (audio o subtítulos) tal como la expone el motor. La clave
 * identifica la pista dentro de `currentTracks` (grupo:índice) para aplicarla después.
 */
internal data class PlayerTrackChoice(val key: String, val label: String)

/** «4K Ultra HD», «1080p FHD»… a partir del alto real de la resolución del motor. */
internal fun videoQualityLabel(height: Int): String = when {
    height >= 2160 -> "4K Ultra HD"
    height >= 1080 -> "1080p FHD"
    height >= 720 -> "720p HD"
    height >= 480 -> "480p SD"
    height > 0 -> "${height}p"
    else -> "Automática"
}

/** La misma resolución en la clave corta que usan Ajustes y las etiquetas ("1080p"). */
internal fun videoQualityKey(height: Int): String = when {
    height >= 2160 -> "2160p"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    height >= 480 -> "480p"
    height > 0 -> "${height}p"
    else -> "Auto"
}

/**
 * Lo que el menú «Audio, subtítulos y calidad» puede enseñar de VERDAD para este video:
 * las pistas que reportó ExoPlayer (audio/subtítulos) y las resoluciones reales. Las
 * secciones sin opciones no se dibujan: nada de menús de una sola entrada ni de relleno.
 */
@Stable
internal class PlayerMenuState {
    var quality by mutableStateOf("Auto")
    var audioChoices by mutableStateOf<List<PlayerTrackChoice>>(emptyList())
    var subtitleChoices by mutableStateOf<List<PlayerTrackChoice>>(emptyList())
    var videoHeights by mutableStateOf<List<Int>>(emptyList())
    var selectedAudioKey by mutableStateOf<String?>(null)
    var selectedSubtitleKey by mutableStateOf<String?>(null)

    /** Con un solo idioma no hay nada que elegir en audio: la sección/píldora no existe. */
    val hasAudioChoices: Boolean get() = audioChoices.size > 1
    val hasSubtitleChoices: Boolean get() = subtitleChoices.isNotEmpty()
    val hasQualityChoices: Boolean get() = videoHeights.size > 1

    /** ¿Vale la pena abrir el menú? Si no hay nada real, el botón se oculta. */
    val hasAnyChoices: Boolean get() = hasAudioChoices || hasSubtitleChoices || hasQualityChoices
}

@Composable
internal fun rememberPlayerMenuState(
    initialQuality: String = com.chronosbvrx.visionplus.ui.settings.VisionSettings.defaultQuality,
): PlayerMenuState = remember { PlayerMenuState().apply { quality = initialQuality } }

/**
 * «Audio, subtítulos y calidad»: SOLO las opciones que la transmisión tiene. Audio con más
 * de un idioma, subtítulos cuando los hay y las resoluciones reales cuando hay más de una.
 * Las secciones sin opciones no se dibujan (ni el botón que las abre).
 */
@Composable
internal fun AudioAndSubtitlesDialog(
    state: PlayerMenuState,
    onPickAudio: (PlayerTrackChoice) -> Unit,
    onPickSubtitle: (PlayerTrackChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogLanding()
    val firstIsQuality = state.hasQualityChoices
    val firstIsAudio = !firstIsQuality && state.hasAudioChoices
    val firstIsSubtitle = !firstIsQuality && !firstIsAudio && state.hasSubtitleChoices
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
                if (state.hasQualityChoices) {
                    MenuSectionTitle("Calidad de video", first = true)
                    MenuOption(
                        "Auto (la mejor según la red)",
                        modifier = if (firstIsQuality) first else Modifier,
                        selected = state.quality == "Auto",
                    ) { state.quality = "Auto" }
                    state.videoHeights.forEach { height ->
                        val key = videoQualityKey(height)
                        MenuOption(videoQualityLabel(height), selected = state.quality == key) {
                            state.quality = key
                        }
                    }
                }

                if (state.hasAudioChoices) {
                    MenuSectionTitle("Audio", first = !state.hasQualityChoices)
                    state.audioChoices.forEachIndexed { index, choice ->
                        MenuOption(
                            choice.label,
                            modifier = if (firstIsAudio && index == 0) first else Modifier,
                            selected = choice.key == state.selectedAudioKey,
                        ) { onPickAudio(choice) }
                    }
                }

                if (state.hasSubtitleChoices) {
                    MenuSectionTitle(
                        "Subtítulos",
                        first = !state.hasQualityChoices && !state.hasAudioChoices,
                    )
                    MenuOption(
                        "Desactivados",
                        modifier = if (firstIsSubtitle) first else Modifier,
                        selected = state.selectedSubtitleKey == null,
                    ) { onPickSubtitle(null) }
                    state.subtitleChoices.forEach { choice ->
                        MenuOption(choice.label, selected = choice.key == state.selectedSubtitleKey) {
                            onPickSubtitle(choice)
                        }
                    }
                }

                if (!state.hasAnyChoices) {
                    MenuNote("Esta transmisión se reproduce con un solo audio, sin subtítulos y en su resolución nativa.")
                }
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
