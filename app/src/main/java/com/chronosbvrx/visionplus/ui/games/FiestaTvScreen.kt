package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.games.FiestaCatalog
import com.chronosbvrx.visionplus.data.games.FiestaOption
import com.chronosbvrx.visionplus.data.games.FiestaState
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStroke
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Plató de la tele para Vision+ Fiesta 3.0. La tele muestra; el teléfono juega. */
@Composable
fun FiestaTvContent(
    snap: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
) {
    val info = snap.fiesta ?: return
    if (info.screen == "bonus" || info.screen == "bonus_reveal") {
        FiestaBonusWorld(snap, info, tick, snapshotAt)
        return
    }
    FiestaWorldTv(snap, info, tick, snapshotAt)
}

/** La ruleta entre rondas conserva un plató neutro para no romper el ritmo. */
@Composable
private fun FiestaBonusWorld(snap: GameSnapshot, info: FiestaState, tick: Long, snapshotAt: Long) {
    val look = fiestaLook(info.game)
    val remaining = fiestaTvRemaining(snap, tick, snapshotAt)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(info.phase) {
        FiestaVoice.play(
            context,
            info.game,
            info.hostCue?.moment ?: info.phase,
            info.hostCue?.variant ?: 0,
            info.hostCue?.audio.orEmpty(),
        )
    }
    Box(Modifier.fillMaxSize()) {
        FiestaStageBackdrop(look, dim = 0.35f)
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FiestaMarquee(look, "LA RULETA DE FIESTA", "ENTRE RONDAS · ${info.round}/${info.rounds}", Modifier.weight(1f))
                FiestaBigTimer(look, remaining)
            }
            FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth())
            val bonus = info.bonus
            if (bonus != null) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    bonus.options.forEach { option ->
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(look.corner))
                                .background(look.panel.copy(alpha = 0.82f))
                                .border(1.dp, fiestaColor(option.tone).copy(alpha = 0.55f), RoundedCornerShape(look.corner)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("?", color = fiestaColor(option.tone), fontSize = 64.sp, fontWeight = FontWeight.Black)
                            Text(option.label, color = look.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (info.screen == "bonus_reveal") {
                                val result = bonus.results.firstOrNull { it.choice == bonus.options.indexOf(option) }
                                if (result != null) {
                                    Text(result.name, color = look.ink.copy(alpha = 0.56f), fontSize = 10.sp)
                                    Text("+${result.coins} ${look.currency}", color = look.accent2, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
            FiestaBottomScores(look, info)
        }
    }
}

@Composable
private fun FiestaTvHeader(
    info: FiestaState,
    accent: Color,
    remaining: Int,
    players: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.title.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    color = accent,
                )
                Spacer(Modifier.width(10.dp))
                FiestaTvBadge(if (info.family) "FAMILIAR" else "SIN FILTRO", if (info.family) accent else VisionLiveRed)
            }
            Text(
                "${FiestaCatalog.hostName(info.game)} · Ronda ${info.round} de ${info.rounds} · $players jugadores",
                color = VisionTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            info.twist?.takeIf { it.id != "clean" }?.let { twist ->
                Text(
                    "${twist.title} · ${twist.description}",
                    color = VisionRatingGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            colors = SurfaceDefaults.colors(
                containerColor = if (remaining <= 5) VisionLiveRed.copy(alpha = 0.3f) else VisionSurfaceHigh,
                contentColor = VisionTextPrimary,
            ),
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(remaining.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("segundos", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            }
        }
    }
}

@Composable
private fun FiestaTvPrompt(info: FiestaState, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = VisionSurface.copy(alpha = 0.96f), contentColor = VisionTextPrimary),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(info.instruction, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                info.prompt.ifBlank { "Prepárense…" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VisionTextPrimary,
            )
            if (info.hint.isNotBlank()) {
                Text(info.hint, color = VisionTextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (info.banter.isNotBlank()) {
                Text(
                    info.banter,
                    color = accent.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FiestaTvBonus(info: FiestaState, accent: Color, reveal: Boolean) {
    val bonus = info.bonus ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = VisionSurface.copy(alpha = 0.92f), contentColor = VisionTextPrimary),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                bonus.title.uppercase(),
                color = accent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(bonus.instruction, color = VisionTextSecondary, style = MaterialTheme.typography.bodyLarge)
            if (!reveal) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    bonus.options.forEach { option ->
                        Surface(
                            modifier = Modifier.weight(1f).height(150.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = SurfaceDefaults.colors(containerColor = accent.copy(alpha = 0.14f), contentColor = VisionTextPrimary),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("◆", color = accent, style = MaterialTheme.typography.displayMedium)
                                Text(option.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                bonus.results.forEach { result ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(result.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(result.label, color = VisionTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "+${result.coins} monedas" + if (result.points > 0) " · +${result.points} pts" else "",
                            color = VisionRatingGold,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FiestaTvWaiting(info: FiestaState, accent: Color, label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = VisionSurface.copy(alpha = 0.72f), contentColor = VisionTextPrimary),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FiestaPulse(accent)
            Spacer(Modifier.height(14.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = accent)
            Text(
                "${info.submittedCount} de ${info.voterCount} listos",
                style = MaterialTheme.typography.bodyLarge,
                color = VisionTextSecondary,
            )
            Text(
                "Escriban o dibujen en su teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
        }
    }
}

@Composable
private fun FiestaTvChoice(info: FiestaState, accent: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ELIGE EL AÑO EN TU TELÉFONO", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        info.years.forEachIndexed { index, year ->
            val isCorrect = info.phase == "reveal" && info.correct == index
            Surface(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = if (isCorrect) accent.copy(alpha = 0.35f) else VisionSurfaceHigh,
                    contentColor = VisionTextPrimary,
                ),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOf("A", "B", "C", "D").getOrElse(index) { "?" },
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isCorrect) {
                        Spacer(Modifier.weight(1f))
                        Text("CORRECTO", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FiestaTvVote(info: FiestaState, accent: Color) {
    if (info.options.isEmpty()) {
        FiestaTvWaiting(info, accent, "PREPARANDO OPCIONES")
        return
    }
    val shirts = info.game == "franela_fest"
    if (shirts) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(info.options) { option ->
                FiestaShirtCard(option, accent)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            info.options.forEachIndexed { index, option ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = SurfaceDefaults.colors(containerColor = VisionSurfaceHigh, contentColor = VisionTextPrimary),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOf("A", "B", "C", "D", "E", "F", "G", "H").getOrElse(index) { "?" },
                            color = accent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            option.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (info.phase == "reveal") {
                            Text(
                                "${option.votes}",
                                color = VisionRatingGold,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiestaShirtCard(option: FiestaOption, accent: Color) {
    Surface(
        modifier = Modifier.width(300.dp).fillMaxHeight(),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = Color(0xFFF8FAFC), contentColor = Color(0xFF0F172A)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(200.dp).background(Color.White, RoundedCornerShape(16.dp)),
            ) {
                FiestaShirtArt(option.strokes, Modifier.fillMaxSize().padding(8.dp))
            }
            Text(
                "«${option.slogan.ifBlank { option.text }}»",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.authorName.isNotBlank()) {
                Text(option.authorName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
            }
            if (option.votes > 0) {
                Text(
                    "${option.votes} votos",
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FiestaShirtArt(strokes: List<GameStroke>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val left = size.width * 0.18f
        val right = size.width * 0.82f
        val top = size.height * 0.12f
        val bottom = size.height * 0.9f
        drawLine(Color(0xFF334155), Offset(left, top + size.height * 0.12f), Offset(left, bottom), 4f)
        drawLine(Color(0xFF334155), Offset(right, top + size.height * 0.12f), Offset(right, bottom), 4f)
        drawLine(Color(0xFF334155), Offset(left, bottom), Offset(right, bottom), 4f)
        drawLine(Color(0xFF334155), Offset(left, top + size.height * 0.12f), Offset(size.width * 0.38f, top), 4f)
        drawLine(Color(0xFF334155), Offset(right, top + size.height * 0.12f), Offset(size.width * 0.62f, top), 4f)
        drawLine(Color(0xFF334155), Offset(size.width * 0.38f, top), Offset(size.width * 0.62f, top), 4f)
        strokes.forEach { stroke ->
            val color = runCatching { Color(android.graphics.Color.parseColor(stroke.color)) }.getOrDefault(Color.Black)
            for (index in 1 until stroke.points.size) {
                drawLine(
                    color = color,
                    start = Offset(
                        size.width * 0.22f + stroke.points[index - 1].x / 1000f * size.width * 0.56f,
                        size.height * 0.22f + stroke.points[index - 1].y / 1000f * size.height * 0.56f,
                    ),
                    end = Offset(
                        size.width * 0.22f + stroke.points[index].x / 1000f * size.width * 0.56f,
                        size.height * 0.22f + stroke.points[index].y / 1000f * size.height * 0.56f,
                    ),
                    strokeWidth = stroke.width.coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun FiestaTvReveal(info: FiestaState, accent: Color, remaining: Int) {
    val reveal = info.reveal
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = VisionSurface.copy(alpha = 0.9f), contentColor = VisionTextPrimary),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                reveal?.title?.ifBlank { "RESULTADOS" } ?: "RESULTADOS",
                color = accent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            val lines = reveal?.lines.orEmpty()
            if (lines.size <= 4) {
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyLarge, color = VisionTextPrimary)
                }
            } else {
                // Dos columnas: una lista larga (los votos de la encuesta, por ejemplo)
                // ya no se corta contra el borde inferior de la tele.
                val half = (lines.size + 1) / 2
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        lines.take(half).forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyLarge, color = VisionTextPrimary)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        lines.drop(half).forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyLarge, color = VisionTextPrimary)
                        }
                    }
                }
            }
            if (info.game == "el_tasador" && info.trueValue > 0) {
                Text(
                    "VALOR REAL: ${info.trueValue}",
                    color = VisionRatingGold,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                "Siguiente ronda en $remaining s…",
                color = VisionTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FiestaTvHost(info: FiestaState, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = accent.copy(alpha = 0.16f), contentColor = VisionTextPrimary),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        FiestaCatalog.hostName(info.game).take(1),
                        color = VisionBlack,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        FiestaCatalog.hostName(info.game),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(info.host.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
            Text(
                info.hostCue?.text ?: info.instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextPrimary,
            )
        }
    }
}

@Composable
private fun FiestaTvScoreboard(info: FiestaState, accent: Color) {
    val players = info.scoreboard.take(6)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = VisionSurface.copy(alpha = 0.86f), contentColor = VisionTextPrimary),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("MARCADOR", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            players.forEachIndexed { index, player ->
                Row(
                    Modifier.fillMaxWidth().background(
                        if (index == 0) accent.copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}. ${player.name}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${player.score} · ${player.coins}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) VisionRatingGold else accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun FiestaTvBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        colors = SurfaceDefaults.colors(containerColor = color.copy(alpha = 0.2f), contentColor = color),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun FiestaPulse(color: Color) {
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = (pulse + 1) % 100
            kotlinx.coroutines.delay(80)
        }
    }
    val scale = 0.85f + (pulse % 20) / 100f
    Box(
        Modifier.size((70 * scale).dp).background(color.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((30 * scale).dp).background(color, CircleShape))
    }
}

private fun fiestaTvRemaining(snap: GameSnapshot, tick: Long, snapshotAt: Long): Int {
    if (snap.phaseEndsAt <= 0L) return 0
    val serverNow = snap.serverNow + (tick - snapshotAt)
    return ((snap.phaseEndsAt - serverNow) / 1000L).coerceAtLeast(0L).toInt()
}
