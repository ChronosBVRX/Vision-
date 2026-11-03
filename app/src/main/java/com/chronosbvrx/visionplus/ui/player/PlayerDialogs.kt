package com.chronosbvrx.visionplus.ui.player

import com.chronosbvrx.visionplus.ui.components.dismissOnBack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.data.sports.MatchStats
import com.chronosbvrx.visionplus.data.sports.SportsStatsSource
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
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
    PlayerMenuSurface(
        title = "Audio, subtítulos y calidad",
        subtitle = "Solo aparecen las pistas reales de esta transmisión.",
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.hasQualityChoices) {
                MenuSectionTitle("Calidad de video", first = true)
                MenuOption(
                    "Auto (la mejor según la red)",
                    modifier = if (firstIsQuality) first else Modifier,
                    selected = state.quality == "Auto",
                    icon = Icons.Default.Tune,
                ) { state.quality = "Auto" }
                state.videoHeights.forEach { height ->
                    val key = videoQualityKey(height)
                    MenuOption(
                        videoQualityLabel(height),
                        selected = state.quality == key,
                        icon = Icons.Default.Tune,
                    ) { state.quality = key }
                }
            }

            if (state.hasAudioChoices) {
                MenuSectionTitle("Audio", first = !state.hasQualityChoices)
                state.audioChoices.forEachIndexed { index, choice ->
                    MenuOption(
                        choice.label,
                        modifier = if (firstIsAudio && index == 0) first else Modifier,
                        selected = choice.key == state.selectedAudioKey,
                        icon = Icons.Default.VolumeUp,
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
                    icon = Icons.Default.SubtitlesOff,
                ) { onPickSubtitle(null) }
                state.subtitleChoices.forEach { choice ->
                    MenuOption(
                        choice.label,
                        selected = choice.key == state.selectedSubtitleKey,
                        icon = Icons.Default.Subtitles,
                    ) { onPickSubtitle(choice) }
                }
            }

            if (!state.hasAnyChoices) {
                MenuNote("Esta transmisión se reproduce con un solo audio, sin subtítulos y en su resolución nativa.")
            }
            Spacer(Modifier.height(6.dp))
            MenuOption("Cerrar", fill = false, onClick = onDismiss)
        }
    }
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
    PlayerMenuSurface(
        title = "Canales del evento",
        subtitle = "Señales en vivo",
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            targets.forEachIndexed { i, target ->
                MenuOption(
                    target.name,
                    modifier = if (i == currentIndex) first else Modifier,
                    selected = i == currentIndex,
                    detail = zapSignalType(target.url, target.drmType, target.drmKey),
                    icon = Icons.Default.Tv,
                ) { if (i != currentIndex) onPick(i) }
            }
            Spacer(Modifier.height(4.dp))
            MenuOption("Cerrar", fill = false, onClick = onDismiss)
        }
    }
}

/** Selector de proporción de pantalla, disponible en VOD y en vivo. */
@Composable
internal fun AspectRatioDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = rememberDialogLanding()
    val settings = com.chronosbvrx.visionplus.ui.settings.VisionSettings
    PlayerMenuSurface(
        title = "Proporción de pantalla",
        subtitle = "Elige cómo se adapta el video a la pantalla.",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            settings.aspectOptions.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEachIndexed { column, label ->
                        val index = rowIndex * 2 + column
                        val mode = settings.aspectModes.getOrElse(index) { 0 }
                        MenuTileOption(
                            label = label,
                            icon = when (mode) {
                                1 -> Icons.Default.Fullscreen
                                4 -> Icons.Default.Tv
                                5 -> Icons.Default.Crop
                                else -> Icons.Default.AspectRatio
                            },
                            selected = mode == current,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (index == 0) first else Modifier),
                        ) { onPick(mode) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }
            MenuOption("Cerrar", fill = false, onClick = onDismiss)
        }
    }
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
    info: GeeZapSignals.EventInfo? = null,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(initialPage.coerceIn(0, 2)) }
    val tabMatch = remember { FocusRequester() }
    val tabStats = remember { FocusRequester() }
    val tabLineups = remember { FocusRequester() }
    val firstContent = remember { FocusRequester() }
    var stats by remember(info?.homeName, info?.awayName, info?.dateIso, info?.leagueId) {
        mutableStateOf<MatchStats?>(null)
    }
    var loading by remember(info?.homeName, info?.awayName) {
        mutableStateOf(info != null && info.homeName.isNotBlank() && info.awayName.isNotBlank())
    }
    LaunchedEffect(info?.homeName, info?.awayName, info?.dateIso, info?.leagueId) {
        val current = info
        if (current == null || current.homeName.isBlank() || current.awayName.isBlank()) {
            loading = false
            stats = null
            return@LaunchedEffect
        }
        loading = true
        stats = runCatching {
            SportsStatsSource.load(
                context = context,
                homeName = current.homeName,
                awayName = current.awayName,
                dateIso = current.dateIso,
                leagueId = current.leagueId,
            )
        }.getOrNull()
        loading = false
    }
    LaunchedEffect(Unit) {
        delay(140)
        runCatching { tabMatch.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dismissOnBack { onDismiss() },
        containerColor = VisionSurface,
        title = { Text("Información") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTab(
                        label = "Partido",
                        selected = page == 0,
                        focusRequester = tabMatch,
                        onRight = {
                            page = 1
                            runCatching { tabStats.requestFocus() }
                        },
                        onClick = { page = 0 },
                    )
                    if (info != null) {
                        StatsTab(
                            label = "Estadísticas",
                            selected = page == 1,
                            focusRequester = tabStats,
                            onLeft = {
                                page = 0
                                runCatching { tabMatch.requestFocus() }
                            },
                            onRight = {
                                page = 2
                                runCatching { tabLineups.requestFocus() }
                            },
                            onClick = { page = 1 },
                        )
                        StatsTab(
                            label = "Alineaciones",
                            selected = page == 2,
                            focusRequester = tabLineups,
                            onLeft = {
                                page = 1
                                runCatching { tabStats.requestFocus() }
                            },
                            onClick = { page = 2 },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (page) {
                    1 -> {
                        Box(Modifier.fillMaxWidth().height(350.dp)) {
                            val current = stats
                            if (current == null) {
                                Text(
                                    if (loading) "Cargando estadísticas del partido…"
                                    else "La fuente aún no publica estadísticas de este partido.",
                                    color = VisionTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(6.dp),
                                )
                            } else {
                                StatsPage(current, firstContent, isTv = true)
                            }
                        }
                    }
                    2 -> {
                        Box(Modifier.fillMaxWidth().height(350.dp)) {
                            val current = stats
                            if (current == null) {
                                Text(
                                    if (loading) "Cargando alineaciones…"
                                    else "La fuente aún no publica alineaciones de este partido.",
                                    color = VisionTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(6.dp),
                                )
                            } else {
                                LineupsPage(current, firstContent, isTv = true)
                            }
                        }
                    }
                    else -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
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
                                        modifier = if (index == 0) Modifier.focusRequester(firstContent) else Modifier,
                                        selected = index == currentIndex,
                                    ) {}
                                }
                            }
                        }
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
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = if (first) 2.dp else 14.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun MenuNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/**
 * Panel contextual del reproductor: sube desde la zona de controles, con degradado
 * profundo y borde fino. No es un diálogo centrado ni una píldora gigante: se siente
 * parte del reproductor y deja ver el video alrededor.
 */
@Composable
internal fun PlayerMenuSurface(
    title: String,
    subtitle: String,
    uppercaseTitle: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.48f)),
                    ),
                )
                .dismissOnBack { onDismiss() }
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 84.dp, top = 24.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x991E3454),
                                Color(0xA50E1A2E),
                                Color(0xBF070B13),
                            ),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .border(
                        1.dp,
                        VisionBlue.copy(alpha = 0.24f),
                        RoundedCornerShape(18.dp),
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(
                    if (uppercaseTitle) title.uppercase() else title,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 1.dp, bottom = 6.dp),
                )
                content()
            }
        }
    }
}

/** A menu choice: tappable on a phone, and a subtle glass highlight with D-pad focus. */
@Composable
internal fun MenuOption(
    label: String,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    selected: Boolean = false,
    detail: String = "",
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.10f)
                    selected -> VisionBlue.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                1.dp,
                when {
                    focused -> Color.White.copy(alpha = 0.55f)
                    selected -> VisionBlue.copy(alpha = 0.30f)
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected || focused) VisionBlueBright else Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp),
            )
        } else if (selected) {
            Icon(
                VisionIcons.Check,
                contentDescription = null,
                tint = VisionBlueBright,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) VisionBlueBright else Color.White.copy(alpha = 0.94f),
                fontSize = 13.sp,
                maxLines = 2,
            )
            if (detail.isNotBlank()) {
                Text(detail, color = VisionTextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
        if (selected) {
            Icon(
                VisionIcons.Check,
                contentDescription = null,
                tint = VisionBlueBright,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Ficha compacta de cuadrícula: icono y nombre pequeño (proporciones, imagen y calidad). */
@Composable
private fun MenuTileOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 54.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.10f)
                    selected -> VisionBlue.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
                shape,
            )
            .border(
                1.dp,
                when {
                    focused -> Color.White.copy(alpha = 0.55f)
                    selected -> VisionBlue.copy(alpha = 0.34f)
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 7.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected || focused) VisionBlueBright else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) VisionBlueBright else Color.White.copy(alpha = 0.82f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
