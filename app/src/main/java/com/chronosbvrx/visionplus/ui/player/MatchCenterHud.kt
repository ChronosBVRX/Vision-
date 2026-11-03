package com.chronosbvrx.visionplus.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.sports.LineupPlayer
import com.chronosbvrx.visionplus.data.sports.MatchEvent
import com.chronosbvrx.visionplus.data.sports.MatchEventType
import com.chronosbvrx.visionplus.data.sports.MatchStats
import com.chronosbvrx.visionplus.data.sports.SportsStatsSource
import com.chronosbvrx.visionplus.ui.sports.GeeTiles
import com.chronosbvrx.visionplus.ui.sports.GeeZapSignals
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * Páginas navegables del Match Center HUD. El orden es el que recorre el D-Pad:
 * Izquierda/Derecha pasa de una a otra de forma cíclica.
 */
internal enum class MatchHudPage(val tab: String) {
    SUMMARY("1. Resumen"),
    STATS("2. Estadísticas"),
    TIMELINE("3. Tarjetas y Goles"),
    LINEUPS("4. Alineaciones"),
}

/** Página siguiente/anterior, circular. Puro para poder probarlo sin composición. */
internal fun matchHudPageAfter(current: MatchHudPage, delta: Int): MatchHudPage {
    val values = MatchHudPage.entries
    val size = values.size
    return values[((current.ordinal + delta) % size + size) % size]
}

/** Marcador y minuto del partido, separados de la línea que arma la agenda. */
internal data class HudScore(val score: String, val minute: String)

internal fun hudScore(info: GeeZapSignals.EventInfo): HudScore {
    val raw = info.scoreLine.trim()
    val parts = raw.split("·", limit = 2).map { it.trim() }
    val score = parts.firstOrNull().orEmpty().ifBlank {
        if (info.homeScore != null && info.awayScore != null) {
            "${info.homeScore} - ${info.awayScore}"
        } else {
            "VS"
        }
    }
    return HudScore(score, parts.getOrNull(1).orEmpty())
}

/** «Luis Di Lollo» → «L. Di Lollo»: los goleadores caben en una línea del HUD. */
internal fun hudPlayerName(name: String): String {
    val clean = name.trim()
    if (clean.isBlank()) return ""
    val parts = clean.split(Regex("\\s+"))
    if (parts.size <= 1) return clean
    return parts.first().first().uppercaseChar() + ". " + parts.drop(1).joinToString(" ")
}

/** Las cifras de la primera página, en el orden en que importan durante el partido. */
internal val HUD_SUMMARY_STATS = listOf("Posesión", "Tiros", "Tiros a puerta", "Córners")

/** La segunda página: disciplina y ataques. */
internal val HUD_DISCIPLINE_STATS = listOf("Faltas", "Amarillas", "Rojas", "Fueras de juego")

private val HudBg = Color(0xD9020617)
private val HudCyan = Color(0xFF38BDF8)
private val HudCyanStrong = Color(0xFF06B6D4)
private val HudOrange = Color(0xFFF97316)
private val HudSlate = Color(0xFF94A3B8)
private val HudTrack = Color(0xFF1E293B)

/**
 * Match Center HUD: un único bloque inferior con marcador, pestañas de estadísticas,
 * cronología, alineaciones, selector de señal y guía del mando. Sustituye la tarjeta
 * superior y la fila de cuatro botones del reproductor de eventos deportivos.
 */
@Composable
internal fun MatchCenterHud(
    info: GeeZapSignals.EventInfo,
    page: MatchHudPage,
    currentSignal: String,
    signalType: String,
    signalIndex: Int,
    signalCount: Int,
    isTv: Boolean,
    onPageChange: (MatchHudPage) -> Unit,
    onSourceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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

    val homeName = info.homeName.ifBlank { stats?.home?.name.orEmpty() }.ifBlank { "Local" }
    val awayName = info.awayName.ifBlank { stats?.away?.name.orEmpty() }.ifBlank { "Visitante" }
    val homeLogo = info.homeLogo.ifBlank { stats?.home?.logo.orEmpty() }
    val awayLogo = info.awayLogo.ifBlank { stats?.away?.logo.orEmpty() }
    val events = stats?.events.orEmpty()
    val homeEvents = events.filter { hudSameTeam(it.team, homeName) }
    val awayEvents = events.filter { hudSameTeam(it.team, awayName) }
    val homeGoals = homeEvents.filter { it.type == MatchEventType.GOAL }
    val awayGoals = awayEvents.filter { it.type == MatchEventType.GOAL }
    val homeReds = homeEvents.filter { it.type == MatchEventType.RED }
    val awayReds = awayEvents.filter { it.type == MatchEventType.RED }
    val score = hudScore(info)
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 1024.dp)
            .padding(bottom = 32.dp)
            .shadow(28.dp, shape, clip = false)
            .clip(shape)
            .background(HudBg)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .testTag("match_hud"),
    ) {
        HudTopBar(info = info, page = page, isTv = isTv, onPageChange = onPageChange)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        HudScoreHeader(
            homeName = homeName,
            awayName = awayName,
            homeLogo = homeLogo,
            awayLogo = awayLogo,
            homeGoals = homeGoals,
            awayGoals = awayGoals,
            homeReds = homeReds,
            awayReds = awayReds,
            score = score,
            isTv = isTv,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isTv) 176.dp else 148.dp)
                .padding(horizontal = if (isTv) 40.dp else 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (page) {
                MatchHudPage.SUMMARY -> HudStatsPage(
                    stats = stats,
                    labels = HUD_SUMMARY_STATS,
                    loading = loading,
                )
                MatchHudPage.STATS -> HudStatsPage(
                    stats = stats,
                    labels = HUD_DISCIPLINE_STATS,
                    loading = loading,
                )
                MatchHudPage.TIMELINE -> HudTimelinePage(
                    homeName = homeName,
                    awayName = awayName,
                    homeEvents = homeEvents,
                    awayEvents = awayEvents,
                    hasStats = stats != null,
                    loading = loading,
                )
                MatchHudPage.LINEUPS -> HudLineupsPage(
                    homeName = homeName,
                    awayName = awayName,
                    home = stats?.home,
                    away = stats?.away,
                    loading = loading,
                )
            }
        }
        HudFooter(
            currentSignal = currentSignal,
            signalType = signalType,
            signalIndex = signalIndex,
            signalCount = signalCount,
            isTv = isTv,
            onSourceClick = onSourceClick,
        )
    }
}

@Composable
private fun HudTopBar(
    info: GeeZapSignals.EventInfo,
    page: MatchHudPage,
    isTv: Boolean,
    onPageChange: (MatchHudPage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isTv) 32.dp else 14.dp,
                end = if (isTv) 32.dp else 14.dp,
                top = if (isTv) 16.dp else 10.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(VisionLiveRed, CircleShape))
            Text(
                "EN VIVO",
                color = VisionLiveRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
                maxLines = 1,
            )
            if (info.league.isNotBlank()) {
                Text(
                    "·  ${info.league.uppercase()}",
                    color = HudCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("←", color = HudSlate, fontSize = 10.sp)
            if (isTv) {
                MatchHudPage.entries.forEach { candidate ->
                    val active = candidate == page
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) HudCyan.copy(alpha = 0.16f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) HudCyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(50),
                            )
                            .clickable { onPageChange(candidate) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            candidate.tab,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.62f),
                            fontSize = 9.sp,
                            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(HudCyan.copy(alpha = 0.16f))
                        .border(1.dp, HudCyan.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable { onPageChange(matchHudPageAfter(page, 1)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${page.ordinal + 1}/4 · ${page.tab.substringAfter(". ")}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
            }
            Text("→", color = HudSlate, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HudScoreHeader(
    homeName: String,
    awayName: String,
    homeLogo: String,
    awayLogo: String,
    homeGoals: List<MatchEvent>,
    awayGoals: List<MatchEvent>,
    homeReds: List<MatchEvent>,
    awayReds: List<MatchEvent>,
    score: HudScore,
    isTv: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
                ),
            )
            .padding(
                horizontal = if (isTv) 40.dp else 14.dp,
                vertical = if (isTv) 16.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudTeam(
            name = homeName,
            logo = homeLogo,
            goals = homeGoals,
            reds = homeReds,
            home = true,
            isTv = isTv,
            modifier = Modifier.weight(1f),
        )
        HudScoreBox(
            score,
            isTv = isTv,
            modifier = Modifier.width(if (isTv) 160.dp else 100.dp),
        )
        HudTeam(
            name = awayName,
            logo = awayLogo,
            goals = awayGoals,
            reds = awayReds,
            home = false,
            isTv = isTv,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HudTeam(
    name: String,
    logo: String,
    goals: List<MatchEvent>,
    reds: List<MatchEvent>,
    home: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (home) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (home) {
                Text(
                    name,
                    color = Color.White,
                    fontSize = if (isTv) 22.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                HudTeamLogo(logo, name, isTv)
            } else {
                HudTeamLogo(logo, name, isTv)
                Text(
                    name,
                    color = Color.White,
                    fontSize = if (isTv) 22.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        val lines = buildList {
            goals.forEach { add("GOL ${it.minute} ${hudPlayerName(it.player)}") }
            reds.forEach { add("ROJA ${it.minute} ${hudPlayerName(it.player)}") }
        }.take(2)
        lines.forEach { line ->
            Text(
                line,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HudTeamLogo(logo: String, name: String, isTv: Boolean) {
    Box(
        modifier = Modifier
            .size(if (isTv) 48.dp else 34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logo)
                    .size(96, 96)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                GeeTiles.monogram(name),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HudScoreBox(score: HudScore, isTv: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .testTag("match_hud_score")
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE60F172A))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(horizontal = if (isTv) 12.dp else 6.dp, vertical = if (isTv) 8.dp else 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Un marcador de dos dígitos («10 - 12») también debe caber: baja el
            // tamaño y el espaciado en vez de recortar el número de la derecha.
            val longScore = score.score.length >= 6
            Text(
                score.score,
                modifier = Modifier.testTag("match_hud_score_text"),
                color = Color.White,
                fontSize = when {
                    longScore && isTv -> 24.sp
                    longScore -> 16.sp
                    isTv -> 30.sp
                    else -> 20.sp
                },
                fontWeight = FontWeight.Black,
                letterSpacing = if (longScore) 0.5.sp else 1.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (score.minute.isNotBlank()) {
            Text(
                score.minute,
                color = HudCyanStrong,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(HudCyanStrong.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HudStatsPage(
    stats: MatchStats?,
    labels: List<String>,
    loading: Boolean,
) {
    if (stats == null) {
        HudEmpty(if (loading) "Cargando estadísticas del partido…" else "La fuente aún no publica estadísticas de este partido.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        labels.forEach { label ->
            HudStatRow(
                label = label,
                home = stats.home.values[label].orEmpty(),
                away = stats.away.values[label].orEmpty(),
            )
        }
    }
}

@Composable
private fun HudStatRow(label: String, home: String, away: String) {
    val homeWeight = statWeight(home)
    val awayWeight = statWeight(away)
    val total = homeWeight + awayWeight
    val homeFraction = if (total <= 0f) 0.5f else (homeWeight / total).coerceIn(0.05f, 0.95f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                home.ifBlank { "—" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                label.uppercase(),
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1.5f),
            )
            Text(
                away.ifBlank { "—" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(HudTrack),
        ) {
            Box(
                Modifier
                    .weight(homeFraction)
                    .fillMaxHeight()
                    .background(HudCyanStrong),
            )
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .weight(1f - homeFraction)
                    .fillMaxHeight()
                    .background(HudOrange),
            )
        }
    }
}

@Composable
private fun HudTimelinePage(
    homeName: String,
    awayName: String,
    homeEvents: List<MatchEvent>,
    awayEvents: List<MatchEvent>,
    hasStats: Boolean,
    loading: Boolean,
) {
    if (!hasStats) {
        HudEmpty(if (loading) "Cargando la cronología…" else "La fuente aún no publica la cronología de este partido.")
        return
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        HudTimelineColumn(
            team = homeName,
            events = homeEvents.takeLast(4),
            total = homeEvents.size,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.10f)))
        HudTimelineColumn(
            team = awayName,
            events = awayEvents.takeLast(4),
            total = awayEvents.size,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HudTimelineColumn(
    team: String,
    events: List<MatchEvent>,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            team.uppercase(),
            color = HudCyan.copy(alpha = 0.82f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (events.isEmpty()) {
            Text(
                "Sin sucesos publicados.",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 10.sp,
            )
        } else {
            if (total > events.size) {
                Text(
                    "… ${total - events.size} antes",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                )
            }
            events.forEach { event -> HudEventRow(event) }
        }
    }
}

internal fun hudEventTag(event: MatchEvent): String = when (event.type) {
    MatchEventType.GOAL -> "GOL"
    MatchEventType.YELLOW -> "TA"
    MatchEventType.RED -> "TR"
    MatchEventType.SUBSTITUTION -> "CAM"
}

/** Línea plana de la cronología (pruebas y lectores); la UI pinta el icono a color. */
internal fun hudEventLine(event: MatchEvent): String {
    val detail = if (event.detail.isBlank()) "" else " · ${event.detail}"
    return "${hudEventTag(event)} ${event.minute} ${hudPlayerName(event.player)}$detail"
}

@Composable
private fun HudEventRow(event: MatchEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (event.type) {
            MatchEventType.GOAL -> Icon(
                Icons.Default.SportsSoccer,
                contentDescription = "Gol",
                tint = HudCyan,
                modifier = Modifier.size(12.dp),
            )
            MatchEventType.YELLOW -> Box(
                Modifier
                    .size(width = 7.dp, height = 10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFFFACC15)),
            )
            MatchEventType.RED -> Box(
                Modifier
                    .size(width = 7.dp, height = 10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFFEF4444)),
            )
            MatchEventType.SUBSTITUTION -> Text(
                "↑↓",
                color = HudCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            buildString {
                append(event.minute)
                if (event.minute.isNotBlank()) append(' ')
                append(hudPlayerName(event.player))
                if (event.detail.isNotBlank()) {
                    append(" · ")
                    append(event.detail)
                }
            },
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HudLineupsPage(
    homeName: String,
    awayName: String,
    home: com.chronosbvrx.visionplus.data.sports.TeamMatchStats?,
    away: com.chronosbvrx.visionplus.data.sports.TeamMatchStats?,
    loading: Boolean,
) {
    if (home == null && away == null) {
        HudEmpty(if (loading) "Cargando alineaciones…" else "La fuente aún no publica alineaciones de este partido.")
        return
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        HudLineupColumn(
            name = homeName,
            formation = home?.formation.orEmpty(),
            players = home?.players.orEmpty().ifEmpty {
                home?.lineup.orEmpty().map { LineupPlayer("", it) }
            },
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.10f)))
        HudLineupColumn(
            name = awayName,
            formation = away?.formation.orEmpty(),
            players = away?.players.orEmpty().ifEmpty {
                away?.lineup.orEmpty().map { LineupPlayer("", it) }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HudLineupColumn(
    name: String,
    formation: String,
    players: List<LineupPlayer>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                name.uppercase(),
                color = HudCyan.copy(alpha = 0.82f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (formation.isNotBlank()) {
                Text(
                    formation,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        if (players.isEmpty()) {
            Text(
                "Sin alineación publicada.",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 10.sp,
            )
        } else {
            val half = (players.size + 1) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HudLineupNames(players.take(half), Modifier.weight(1f))
                HudLineupNames(players.drop(half), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HudLineupNames(players: List<LineupPlayer>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        players.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (player.number.isNotBlank()) {
                    Text(
                        player.number,
                        color = HudCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.width(20.dp),
                    )
                }
                Text(
                    player.name,
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HudFooter(
    currentSignal: String,
    signalType: String,
    signalIndex: Int,
    signalCount: Int,
    isTv: Boolean,
    onSourceClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.50f))
            .padding(
                horizontal = if (isTv) 32.dp else 12.dp,
                vertical = if (isTv) 12.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable { onSourceClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "↑ ↓ SEÑAL $signalIndex/$signalCount",
                color = HudCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(HudCyan.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                currentSignal,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (signalType.isNotBlank()) {
                Text(
                    "• $signalType",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        if (isTv) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudHint("← →", "Estadísticas y Alineaciones")
                HudHint("↑ ↓", "Cambiar señal del partido")
                HudHint("OK / Atrás", "Ocultar")
            }
        } else {
            HudHint("← → · ↑ ↓", "Pestañas y señal")
        }
    }
}

@Composable
private fun HudHint(keys: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            keys,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Text(
            action,
            color = VisionTextSecondary,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun HudEmpty(message: String) {
    Text(
        message,
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Comparación tolerante de equipos («Arsenal» vs «Arsenal FC»). */
internal fun hudSameTeam(value: String, team: String): Boolean {
    val a = value.trim().lowercase()
    val b = team.trim().lowercase()
    if (a.isBlank() || b.isBlank()) return false
    return a == b || a.contains(b) || b.contains(a)
}
