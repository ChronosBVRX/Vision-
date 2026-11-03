package com.chronosbvrx.visionplus.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronosbvrx.visionplus.data.sports.MatchStats
import com.chronosbvrx.visionplus.data.sports.SportsStatsSource
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/** Etiquetas de las cifras en el orden en que importan durante un partido. */
internal val MATCH_STAT_ORDER = listOf(
    "Posesión",
    "Tiros",
    "Tiros a puerta",
    "Córners",
    "Faltas",
    "Amarillas",
    "Rojas",
    "Atajadas",
    "Fueras de juego",
    "Pases",
    "Precisión de pase",
)

/**
 * Las cifras publicadas para ESTE partido, con las imprescindibles primero y
 * cualquier etiqueta extra que mande la fuente al final. Puro: lo usan el panel
 * compacto y la tarjeta completa.
 */
internal fun matchStatLabels(stats: MatchStats): List<String> {
    val known = MATCH_STAT_ORDER.filter { label ->
        stats.home.values[label].orEmpty().isNotBlank() ||
            stats.away.values[label].orEmpty().isNotBlank()
    }
    val extras = (stats.home.values.keys + stats.away.values.keys)
        .filter { label ->
            label !in known &&
                (stats.home.values[label].orEmpty().isNotBlank() ||
                    stats.away.values[label].orEmpty().isNotBlank())
        }
    return known + extras
}

/**
 * Tarjeta completa de estadísticas. En la tele el mando recorre las cifras con
 * Arriba/Abajo (la lista se desplaza sola) y cambia entre «Resumen» y
 * «Alineaciones» con Izquierda/Derecha desde las pestañas. En el móvil todo se
 * toca y se desliza igual.
 */
@Composable
internal fun MatchStatsDialog(
    info: GeeZapSignals.EventInfo,
    isTv: Boolean,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = "${info.homeName}|${info.awayName}|${info.dateIso}|${info.leagueId}"
    var stats by remember(key) { mutableStateOf<MatchStats?>(null) }
    var loading by remember(key) {
        mutableStateOf(info.homeName.isNotBlank() && info.awayName.isNotBlank())
    }
    LaunchedEffect(key) {
        if (info.homeName.isBlank() || info.awayName.isBlank()) {
            loading = false
            stats = null
            return@LaunchedEffect
        }
        loading = true
        stats = runCatching {
            SportsStatsSource.load(
                context = context,
                homeName = info.homeName,
                awayName = info.awayName,
                dateIso = info.dateIso,
                leagueId = info.leagueId,
            )
        }.getOrNull()
        loading = false
    }

    var page by remember { mutableIntStateOf(0) }
    val tabResumen = remember { FocusRequester() }
    val tabLineups = remember { FocusRequester() }
    val firstContent = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { tabResumen.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(if (isTv) 44.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Color(0xF20A1220),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VisionBlueBright.copy(alpha = 0.42f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isTv) 0.88f else 0.90f)
                    .widthIn(max = 920.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
                    MatchStatsDialogHeader(info)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatsTab(
                            label = "Resumen",
                            selected = page == 0,
                            focusRequester = tabResumen,
                            onClick = { page = 0 },
                        )
                        StatsTab(
                            label = "Alineaciones",
                            selected = page == 1,
                            focusRequester = tabLineups,
                            onClick = { page = 1 },
                        )
                        Spacer(Modifier.weight(1f))
                        StatsHint(isTv)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        val current = stats
                        if (current == null) {
                            if (loading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(6.dp),
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        color = VisionBlueBright,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Cargando estadísticas del partido…",
                                        color = Color.White.copy(alpha = 0.78f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                Text(
                                    "La fuente aún no publica estadísticas de este partido.",
                                    color = Color.White.copy(alpha = 0.66f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                        } else if (page == 0) {
                            StatsPage(
                                stats = current,
                                first = firstContent,
                                isTv = isTv,
                            )
                        } else {
                            LineupsPage(
                                stats = current,
                                first = firstContent,
                                isTv = isTv,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isTv) "Arriba/Abajo desplaza · Izquierda/Derecha cambia de tarjeta"
                            else "Desliza para ver todas las cifras y alineaciones",
                            color = VisionTextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchStatsDialogHeader(info: GeeZapSignals.EventInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(VisionLiveRed, CircleShape))
            Text(
                "EN VIVO",
                color = VisionLiveRed,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                info.title.ifBlank { "${info.homeName} vs ${info.awayName}" },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (info.scoreLine.isNotBlank()) {
                Text(
                    info.scoreLine,
                    color = VisionRatingGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Leaderboard, contentDescription = null, tint = VisionBlueBright, modifier = Modifier.size(15.dp))
            Text(
                "ESTADÍSTICAS DEL PARTIDO",
                color = VisionBlueBright,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            if (info.league.isNotBlank()) {
                Text(
                    "· ${info.league}",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun StatsTab(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused -> VisionBlue
                    selected -> VisionBlue.copy(alpha = 0.30f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
            )
            .border(
                1.dp,
                if (selected || focused) VisionBlueBright.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> {
                        onLeft?.invoke()
                        onLeft != null
                    }
                    androidx.compose.ui.input.key.Key.DirectionRight -> {
                        onRight?.invoke()
                        onRight != null
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StatsHint(isTv: Boolean) {
    if (!isTv) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("OK", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text("elige tarjeta", color = VisionTextSecondary, fontSize = 10.sp)
    }
}

@Composable
internal fun StatsPage(stats: MatchStats, first: FocusRequester, isTv: Boolean) {
    val labels = remember(stats) { matchStatLabels(stats) }
    val home = stats.home
    val away = stats.away
    Column(Modifier.fillMaxSize()) {
        MatchStatsTeamHeader(home.name, away.name, isTv)
        if (labels.isEmpty()) {
            Text(
                "Sin cifras publicadas todavía.",
                color = Color.White.copy(alpha = 0.66f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(labels) { label ->
                MatchStatDialogRow(
                    label = label,
                    home = home.values[label].orEmpty(),
                    away = away.values[label].orEmpty(),
                    focusRequester = if (label == labels.first()) first else null,
                )
            }
        }
    }
}

@Composable
private fun MatchStatsTeamHeader(home: String, away: String, isTv: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            home,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isTv) {
            Text(
                "LOCAL · VISITANTE",
                color = Color.White.copy(alpha = 0.44f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Text(
            away,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MatchStatDialogRow(
    label: String,
    home: String,
    away: String,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val homeValue = home.ifBlank { "—" }
    val awayValue = away.ifBlank { "—" }
    val homeWeight = statWeight(home)
    val awayWeight = statWeight(away)
    val total = homeWeight + awayWeight
    val homeFraction = if (total <= 0f) 0.5f else (homeWeight / total).coerceIn(0.12f, 0.88f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) VisionBlue.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.045f))
            .border(
                1.dp,
                if (focused) VisionBlueBright.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp),
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                homeValue,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                label,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.35f),
            )
            Text(
                awayValue,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().height(4.dp)) {
            Box(
                Modifier
                    .weight(homeFraction)
                    .fillMaxHeight()
                    .background(VisionBlueBright, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .weight(1f - homeFraction)
                    .fillMaxHeight()
                    .background(VisionLiveRed, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
internal fun LineupsPage(stats: MatchStats, first: FocusRequester, isTv: Boolean) {
    val home = stats.home
    val away = stats.away
    val homeLine = home.lineup.take(11)
    val awayLine = away.lineup.take(11)
    if (homeLine.isEmpty() && awayLine.isEmpty()) {
        Text(
            "La fuente aún no publica alineaciones. Vuelve a abrir esta tarjeta cuando el partido avance.",
            color = Color.White.copy(alpha = 0.66f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(6.dp),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LineupCard(
                name = home.name,
                players = homeLine,
                side = "LOCAL",
                focusRequester = first,
            )
        }
        item {
            LineupCard(
                name = away.name,
                players = awayLine,
                side = "VISITANTE",
            )
        }
    }
}

@Composable
private fun LineupCard(
    name: String,
    players: List<String>,
    side: String,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) VisionBlue.copy(alpha = 0.26f) else VisionSurface.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (focused) VisionBlueBright.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(12.dp),
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                side,
                color = VisionBlueBright,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (players.isEmpty()) {
            Text(
                "Sin alineación publicada.",
                color = Color.White.copy(alpha = 0.60f),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val half = (players.size + 1) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LineupNames(players.take(half), Modifier.weight(1f))
                LineupNames(players.drop(half), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LineupNames(players: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        players.forEach { player ->
            Text(
                player,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
