package com.chronosbvrx.visionplus.ui.games

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.data.games.FiestaOption
import com.chronosbvrx.visionplus.data.games.FiestaState
import com.chronosbvrx.visionplus.data.games.NarratorAudio
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStroke
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Los seis platós de Vision+ Fiesta. Cada juego tiene su puesta en escena:
 * concurso, noticiero, consola temporal, chat, pasarela y casa de subastas.
 * No comparten layout: solo comparten los datos del servidor.
 */
@Composable
fun FiestaWorldTv(snap: GameSnapshot, info: FiestaState, tick: Long, snapshotAt: Long) {
    val look = fiestaLook(info.game)
    val remaining = worldRemaining(snap, tick, snapshotAt)
    val context = LocalContext.current
    LaunchedEffect(info.game, info.phase, info.round, info.hostCue?.variant) {
        FiestaVoice.play(
            context,
            info.game,
            info.hostCue?.moment ?: info.phase,
            info.hostCue?.variant ?: 0,
            info.hostCue?.audio.orEmpty(),
        )
    }
    LaunchedEffect(info.game) {
        com.chronosbvrx.visionplus.data.games.GameAmbient.startFor(context, info.game)
    }
    LaunchedEffect(remaining, info.screen) {
        NarratorAudio.setTensionMode(info.screen != "reveal" && remaining in 1..10)
    }
    DisposableEffect(Unit) {
        onDispose {
            NarratorAudio.setTensionMode(false)
            com.chronosbvrx.visionplus.data.games.GameAmbient.stop()
        }
    }
    Box(Modifier.fillMaxSize()) {
        FiestaStageBackdrop(look, dim = 0.22f)
        FiestaPhaseTransition(info.game, info.phase, Modifier.fillMaxSize()) {
            when (look.shell) {
                FiestaShell.Stage -> UltimaPalabraTv(info, look, remaining)
                FiestaShell.Newsroom -> VoxPopuliTv(info, look, remaining)
                FiestaShell.Timelab -> FueraDeEpocaTv(info, look, remaining)
                FiestaShell.Chat -> TextoRotoTv(info, look, remaining, snap)
                FiestaShell.Runway -> FranelaFestTv(info, look, remaining)
                FiestaShell.Auction -> ElTasadorTv(info, look, remaining)
            }
        }
        if (info.screen == "reveal" && !info.reactionBanner.isNullOrBlank()) {
            FiestaReactionBanner(
                look,
                info.reactionBanner!!,
                Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            )
        }
        FiestaTauntCloud(look, info, Modifier.align(Alignment.TopEnd).padding(top = 74.dp))
    }
}

private fun worldRemaining(snap: GameSnapshot, tick: Long, snapshotAt: Long): Int {
    if (snap.phaseEndsAt <= 0L) return 0
    val serverNow = snap.serverNow + (tick - snapshotAt)
    return ((snap.phaseEndsAt - serverNow) / 1000L).coerceAtLeast(0L).toInt()
}

@Composable
private fun WorldTop(
    look: FiestaLook,
    info: FiestaState,
    remaining: Int,
    right: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            FiestaMarquee(look, info.title.uppercase(), info.signature.ifBlank { look.eyebrow })
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FiestaTag(if (info.family) "FAMILIAR" else "SIN FILTRO", look.accent)
                FiestaTag("RONDA ${info.round}/${info.rounds}", look.accent2)
                FiestaTag(look.hostName.uppercase(), look.accent.copy(alpha = 0.8f))
                info.twist?.takeIf { it.id != "clean" }?.let { FiestaTag(it.title, look.accent2) }
            }
        }
        FiestaBigTimer(look, remaining)
        Spacer(Modifier.width(10.dp))
        right()
    }
}

// ---------------------------------------------------------------------------
// 1. La Última Palabra — concurso de tarjetas enfrentadas
// ---------------------------------------------------------------------------
@Composable
private fun UltimaPalabraTv(info: FiestaState, look: FiestaLook, remaining: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        WorldTop(look, info, remaining) {
            FiestaCurrencyChip(look, info.coins)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FiestaPrompter(look, "LA FRASE DE NORA", info.prompt.ifBlank { "Prepárense…" })
                val duel = info.duelState
                if (duel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FiestaTag("DUELO ${duel.duelIndex + 1} DE ${duel.totalDuels}", look.accent2, filled = true)
                        if (duel.isFinal) FiestaTag("FINAL", look.accent, filled = true)
                        if (duel.prompt.isNotBlank()) {
                            Text(duel.prompt, color = look.ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (info.screen == "reveal" && duel?.knockout == true) {
                    FiestaStamp("¡NORA-KO! UNANIMIDAD (+300)", look.accent2)
                }
                when (info.screen) {
                    "vote" -> UltimaFaceOff(info, look, reveal = false, remaining, Modifier.weight(1f))
                    "reveal" -> UltimaFaceOff(info, look, reveal = true, remaining, Modifier.weight(1f))
                    "text_input" -> StageWriting(info, look, Modifier.weight(1f))
                    else -> FiestaWaitingLine(look, "PREPARANDO EL SIGUIENTE CHISTE", info.submittedCount, info.voterCount, Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
            Column(Modifier.weight(0.72f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth(), small = true, animated = true)
                info.twist?.takeIf { it.id != "clean" }?.let {
                    FiestaTwistRibbon(look, it.title, it.description, Modifier.fillMaxWidth())
                }
                StageApplauseMeter(look, info)
                FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FiestaSectionLabel("LA SALA", look)
                        info.scoreboard.take(5).forEachIndexed { index, player ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}",
                                    color = if (index == 0) look.accent2 else look.accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(20.dp),
                                )
                                Text(
                                    player.name,
                                    color = look.ink,
                                    fontSize = 12.sp,
                                    fontWeight = if (index == 0) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${player.score}",
                                    color = look.accent2,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FiestaBottomScores(look, info)
    }
}

@Composable
private fun StageWriting(info: FiestaState, look: FiestaLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(look.panel.copy(alpha = 0.72f))
            .border(1.dp, look.accent.copy(alpha = 0.3f), RoundedCornerShape(look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "QUE NADIE LEA ESTO TODAVÍA",
                color = look.accent,
                fontFamily = look.display,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 1.sp,
            )
            Text(
                "Los teléfonos están calientes. ${info.submittedCount} de ${info.voterCount} ya soltaron su barbaridad.",
                color = look.ink.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(info.voterCount.coerceAtMost(8)) { index ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(
                                if (index < info.submittedCount) look.accent else Color.White.copy(alpha = 0.14f),
                                CircleShape,
                            ),
                    )
                }
            }
            Text(
                "«${info.banter.ifBlank { "Nora está afilando el lápiz." }}»",
                color = look.accent2,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UltimaFaceOff(info: FiestaState, look: FiestaLook, reveal: Boolean, remaining: Int, modifier: Modifier = Modifier) {
    val options = if (reveal) info.options.sortedByDescending { it.votes } else info.options
    if (options.isEmpty()) {
        FiestaWaitingLine(look, "PREPARANDO LAS TARJETAS", info.submittedCount, info.voterCount)
        return
    }
    val top = options.maxOfOrNull { it.votes } ?: 0
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FiestaSectionLabel(if (reveal) "LA SALA YA HABLÓ" else "VS · VOTEN EN EL TELÉFONO", look)
            Spacer(Modifier.weight(1f))
            if (reveal) FiestaTag("GANADOR: $top VOTOS", look.accent2, filled = top > 0)
            else FiestaTag("$remaining S", look.accent)
        }
        options.take(8).forEachIndexed { index, option ->
            val winner = reveal && option.votes == top && top > 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (winner) look.accent.copy(alpha = 0.22f)
                        else if (index % 2 == 0) Color.White.copy(alpha = 0.05f)
                        else Color.White.copy(alpha = 0.02f),
                    )
                    .border(
                        if (winner) 2.dp else 1.dp,
                        if (winner) look.accent2 else Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOf("A", "B", "C", "D", "E", "F", "G", "H").getOrElse(index) { "?" },
                    color = look.accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.width(24.dp),
                )
                Text(
                    option.text,
                    color = look.ink,
                    fontSize = 14.sp,
                    fontWeight = if (winner) FontWeight.Black else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (option.audienceFavorite) {
                    Text("FAVORITO +150", color = look.accent2, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                if (reveal) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(84.dp)) {
                        Text(
                            "${option.votes} votos",
                            color = if (winner) look.accent2 else look.ink.copy(alpha = 0.62f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FiestaProgressBar(
                            look,
                            if (top <= 0) 0f else option.votes.toFloat() / top,
                            Modifier.width(80.dp),
                            height = 4.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageApplauseMeter(look: FiestaLook, info: FiestaState) {
    val level = (info.scoreboard.firstOrNull()?.score ?: 0).coerceAtMost(5000) / 5000f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            FiestaSectionLabel("APLAUSÓMETRO", look)
            Spacer(Modifier.weight(1f))
            Text("${(level * 100).roundToInt()}%", color = look.accent2, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(14) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (index < (level * 14).roundToInt()) 18.dp else 8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index < (level * 14).roundToInt())
                                if (index > 10) look.accent2 else look.accent
                            else Color.White.copy(alpha = 0.10f),
                        )
                        .align(Alignment.Bottom),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Vox Populi — noticiero en vivo
// ---------------------------------------------------------------------------
@Composable
private fun VoxPopuliTv(info: FiestaState, look: FiestaLook, remaining: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "● EN VIVO",
                color = fiestaColor("#FF4B4B"),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fiestaColor("#FF4B4B").copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(10.dp))
            FiestaMarquee(look, "VOX POPULI", "EL NOTICIERO DE LA GENTE · ${info.round}/${info.rounds}", Modifier.weight(1f))
            FiestaBigTimer(look, remaining)
            Spacer(Modifier.width(10.dp))
            FiestaCurrencyChip(look, info.coins)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FiestaPrompter(look, "ENCUESTA DEL DÍA", info.prompt.ifBlank { "La calle está pensando…" })
                if (info.screen == "reveal") {
                    if (info.voxState?.mysteryAnswer?.isNotBlank() == true) {
                        VoxHiloBoard(info, look, reveal = true, Modifier.weight(1f))
                    } else {
                        VoxSurveyBoard(info, look, reveal = true, Modifier.weight(1f))
                    }
                } else if (info.voxState?.mysteryAnswer?.isNotBlank() == true) {
                    VoxHiloBoard(info, look, reveal = false, Modifier.weight(1f))
                } else {
                    VoxGuessDesk(info, look, Modifier.weight(1f))
                }
            }
            Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth(), small = true, animated = true)
                VoxRatingGauge(look, info)
                FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FiestaSectionLabel("RATING DE LA SALA", look)
                        info.scoreboard.take(6).forEachIndexed { index, player ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}.",
                                    color = look.accent2,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    player.name,
                                    color = look.ink,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${player.score}", color = look.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FiestaTicker(
            look,
            "${info.banter.ifBlank { "La gente ya contestó. Ahora adivinen qué dijo." }}  ·  ${info.submittedCount} de ${info.voterCount} enviaron su respuesta  ·  ${look.tagline}",
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        FiestaBottomScores(look, info)
    }
}

@Composable
private fun VoxHiloBoard(info: FiestaState, look: FiestaLook, reveal: Boolean, modifier: Modifier = Modifier) {
    val vox = info.voxState ?: return
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FiestaSectionLabel("SONDEO HILO · ¿DÓNDE CAE?", look)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(look.accent.copy(alpha = 0.12f))
                    .border(1.dp, look.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text("PUESTO #3 REVELADO", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(vox.anchorRank3Text, color = look.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(
                Modifier
                    .weight(1.35f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(look.accent2.copy(alpha = 0.14f))
                    .border(2.dp, look.accent2.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text("RESPUESTA MISTERIOSA", color = look.accent2, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("«${vox.mysteryAnswer}»", color = look.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            if (reveal && vox.revealedRank > 0) "CAYÓ EN EL PUESTO #${vox.revealedRank}" else "¿MÁS ARRIBA O MÁS ABAJO?",
            color = if (reveal) look.accent2 else look.ink,
            fontFamily = look.display,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        if (!reveal) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("▲ TOP 1–2", color = look.accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("·", color = look.ink.copy(alpha = 0.4f), fontSize = 16.sp)
                Text("▼ PUESTOS 4–6", color = look.accent2, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(
            "La calle vota en el teléfono. Acierta y gana +250.",
            color = look.ink.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun VoxSurveyBoard(info: FiestaState, look: FiestaLook, reveal: Boolean, modifier: Modifier = Modifier) {
    val options = if (reveal) info.options.sortedBy { it.rank } else info.options
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FiestaSectionLabel(if (reveal) "LO QUE DIJO LA CALLE" else "RESPUESTAS OCULTAS", look)
            Spacer(Modifier.weight(1f))
            Text(
                if (reveal) "TOP ${options.size}" else "¿QUIÉN SE ACERCA?",
                color = look.accent2,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
        if (options.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FiestaWaitingLine(look, "EL NOTICIERO ESTÁ EN PAUSA", info.submittedCount, info.voterCount)
            }
            return@Column
        }
        val max = options.maxOfOrNull { it.count } ?: 1
        options.take(6).forEachIndexed { index, option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}",
                    color = look.accent2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.width(26.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (reveal) option.text else "////////////////////////",
                            color = look.ink,
                            fontSize = 13.sp,
                            fontWeight = if (reveal) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (reveal) {
                            Text(
                                "${option.count}",
                                color = look.accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                modifier = Modifier.width(42.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    FiestaProgressBar(
                        look,
                        if (reveal) option.count.toFloat() / max.coerceAtLeast(1) else 0.18f + index * 0.03f,
                        Modifier.fillMaxWidth(),
                        height = 5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoxGuessDesk(info: FiestaState, look: FiestaLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(look.panel.copy(alpha = 0.72f))
            .border(1.dp, look.accent.copy(alpha = 0.3f), RoundedCornerShape(look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SEÑAL EN VIVO", color = fiestaColor("#FF4B4B"), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                "LA CALLE ESTÁ ESCRIBIENDO",
                color = look.accent,
                fontFamily = look.display,
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                letterSpacing = 1.sp,
            )
            Text(
                "${info.submittedCount} de ${info.voterCount} ya mandaron su respuesta al noticiero.",
                color = look.ink.copy(alpha = 0.78f),
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(info.voterCount.coerceAtMost(8)) { index ->
                    Box(
                        Modifier.size(13.dp).background(
                            if (index < info.submittedCount) look.accent else Color.White.copy(alpha = 0.13f),
                            CircleShape,
                        ),
                    )
                }
            }
            FiestaTag("NO HAY RESPUESTA INCORRECTA, SOLO POCO POPULAR", look.accent2)
        }
    }
}

@Composable
private fun VoxRatingGauge(look: FiestaLook, info: FiestaState) {
    val top = info.scoreboard.firstOrNull()?.score ?: 0
    val pct = (top % 1000) / 1000f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(10.dp),
    ) {
        Row {
            FiestaSectionLabel("ÍNDICE DE VERGÜENZA", look)
            Spacer(Modifier.weight(1f))
            Text("${(pct * 100).roundToInt()}%", color = look.accent2, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        FiestaProgressBar(look, pct, Modifier.fillMaxWidth(), height = 8.dp)
        Text(
            when {
                pct > 0.75f -> "La sala está en llamas."
                pct > 0.45f -> "La gente empieza a sospechar."
                else -> "Todo bajo control. Por ahora."
            },
            color = look.ink.copy(alpha = 0.65f),
            fontSize = 10.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// 3. Fuera de Época — consola temporal
// ---------------------------------------------------------------------------
@Composable
private fun FueraDeEpocaTv(info: FiestaState, look: FiestaLook, remaining: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FiestaMarquee(look, "FUERA DE ÉPOCA", "ARCHIVO TEMPORAL · EXPEDIENTE ${info.round.toString().padStart(2, '0')}/${
                    info.rounds.toString().padStart(2, '0')}", )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FiestaTag("CRONOLOGÍA INESTABLE", look.accent)
                    FiestaTag(if (info.family) "FAMILIAR" else "SIN FILTRO", look.accent2)
                }
            }
            FiestaBigTimer(look, remaining, label = "T-MINUS")
            Spacer(Modifier.width(12.dp))
            FiestaCurrencyChip(look, info.coins)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaSoftPanel(look, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(look.accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("TT", color = look.accent2, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            FiestaSectionLabel("EVENTO DETECTADO", look)
                            Text(
                                info.prompt.ifBlank { "Cargando el pasado…" },
                                color = look.ink,
                                fontFamily = look.display,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 23.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (info.hint.isNotBlank()) {
                                Text(info.hint, color = look.ink.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
                if (info.screen == "sort" || (info.screen == "reveal" && info.sortItems.isNotEmpty())) {
                    Text(
                        if (info.screen == "reveal") "EL RELOJ MARCA LA VERDAD" else "ORDENA DEL MÁS ANTIGUO AL MÁS RECIENTE EN TU TELÉFONO",
                        color = look.accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp,
                    )
                    TimeSortBoard(info, look, reveal = info.screen == "reveal", Modifier.fillMaxWidth())
                } else {
                    Text(
                        if (info.screen == "reveal") "EL RELOJ MARCA LA VERDAD" else "SELECCIONA EL AÑO EN TU TELÉFONO",
                        color = look.accent,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp,
                    )
                    val years = info.years
                    if (years.isEmpty()) {
                        FiestaWaitingLine(look, "CALIBRANDO LA MÁQUINA", info.submittedCount, info.voterCount)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            years.chunked(2).forEachIndexed { row, pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEachIndexed { col, year ->
                                        val index = row * 2 + col
                                        val correct = info.screen == "reveal" && info.correct == index
                                        val chosen = info.you?.choice == index
                                        YearTile(
                                            look,
                                            year,
                                            index,
                                            correct,
                                            chosen,
                                            info.screen == "reveal",
                                            Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                FiestaTicker(
                    look,
                    info.banter.ifBlank { "La historia no se acuerda de ustedes, pero el marcador sí." } +
                        " · ${info.submittedCount}/${info.voterCount} viajeros ya eligieron",
                    Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.weight(0.72f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth(), small = true, animated = true)
                TimeDial(look, info)
                if (info.screen == "reveal") {
                    FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FiestaSectionLabel("LECTURA DEL ARCHIVO", look)
                            Text(
                                info.reveal?.lines?.joinToString(" · ").orEmpty(),
                                color = look.ink,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FiestaSectionLabel("CRONONAUTAS", look)
                            info.scoreboard.take(6).forEachIndexed { index, player ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${index + 1}.",
                                        color = look.accent2,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.width(24.dp),
                                    )
                                    Text(
                                        player.name,
                                        color = look.ink,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${player.score}", color = look.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearTile(
    look: FiestaLook,
    year: Int,
    index: Int,
    correct: Boolean,
    chosen: Boolean,
    reveal: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        correct -> look.accent.copy(alpha = 0.28f)
        choose(chosen) -> look.accent2.copy(alpha = 0.18f)
        else -> Color.Black.copy(alpha = 0.40f)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                if (correct) 2.dp else 1.dp,
                when {
                    correct -> look.accent2
                    chosen -> look.accent2.copy(alpha = 0.7f)
                    else -> look.accent.copy(alpha = 0.30f)
                },
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOf("A", "B", "C", "D").getOrElse(index) { "?" },
            color = look.accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            modifier = Modifier.width(22.dp),
        )
        Text(
            year.toString(),
            color = if (correct) look.accent2 else look.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 27.sp,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        if (correct) Text("CORRECTO", color = look.accent2, fontSize = 9.sp, fontWeight = FontWeight.Black)
        else if (chosen) Text("TU ELECCIÓN", color = look.accent2.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeSortBoard(info: FiestaState, look: FiestaLook, reveal: Boolean, modifier: Modifier = Modifier) {
    val items = if (reveal) info.sortItems.sortedBy { it.year ?: Int.MAX_VALUE } else info.sortItems
    Column(
        modifier
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, look.accent.copy(alpha = 0.3f), RoundedCornerShape(look.corner))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FiestaSectionLabel(if (reveal) "LÍNEA TEMPORAL RESTAURADA" else "VÓRTICE TEMPORAL", look)
            Spacer(Modifier.weight(1f))
            FiestaTag(if (reveal) "AÑOS REALES" else "AÑOS OCULTOS", look.accent2, filled = reveal)
        }
        items.forEachIndexed { index, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (index % 2 == 0) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f))
                    .border(1.dp, look.accent.copy(alpha = 0.20f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}º",
                    color = look.accent2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.width(30.dp),
                )
                Text(
                    item.title,
                    color = look.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (reveal && item.year != null) item.year.toString() else "????",
                    color = if (reveal) look.accent2 else look.ink.copy(alpha = 0.35f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                )
            }
        }
        if (items.isEmpty()) {
            FiestaWaitingLine(look, "CALIBRANDO LA MÁQUINA", info.submittedCount, info.voterCount)
        }
    }
}

private fun choose(value: Boolean): Boolean = value

@Composable
private fun TimeDial(look: FiestaLook, info: FiestaState) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(126.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 6f
            drawCircle(look.accent.copy(alpha = 0.12f), radius, center)
            drawCircle(look.accent.copy(alpha = 0.55f), radius, center, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            for (i in 0 until 12) {
                val ang = Math.toRadians(i * 30.0)
                val inner = radius * 0.82f
                val outer = radius * 0.96f
                drawLine(
                    color = if (i % 3 == 0) look.accent2 else look.accent.copy(alpha = 0.6f),
                    start = Offset(center.x + (Math.cos(ang) * inner).toFloat(), center.y + (Math.sin(ang) * inner).toFloat()),
                    end = Offset(center.x + (Math.cos(ang) * outer).toFloat(), center.y + (Math.sin(ang) * outer).toFloat()),
                    strokeWidth = if (i % 3 == 0) 4f else 2f,
                    cap = StrokeCap.Round,
                )
            }
            val progress = if (info.rounds <= 0) 0f else info.round.toFloat() / info.rounds
            drawArc(
                color = look.accent2,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.66f, center.y - radius * 0.66f),
                size = androidx.compose.ui.geometry.Size(radius * 1.32f, radius * 1.32f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(6f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (info.screen == "reveal") "AÑO REAL" else "RONDA",
                color = look.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                if (info.screen == "reveal" && info.years.isNotEmpty()) {
                    info.years.getOrNull(info.correct)?.toString().orEmpty()
                } else "${info.round}/${info.rounds}",
                color = look.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Texto Roto — chat a pantalla completa
// ---------------------------------------------------------------------------
@Composable
private fun TextoRotoTv(info: FiestaState, look: FiestaLook, remaining: Int, snap: GameSnapshot) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(look.accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            FiestaMarquee(look, "TEXTO ROTO", "CHAT: ${info.prompt.ifBlank { "la conversación" }} · EN LÍNEA", Modifier.weight(1f))
            FiestaBigTimer(look, remaining)
            Spacer(Modifier.width(10.dp))
            FiestaCurrencyChip(look, info.coins)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier
                    .weight(1.7f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(look.corner))
                    .background(Color(0xFF041A13).copy(alpha = 0.94f))
                    .border(1.dp, look.accent.copy(alpha = 0.35f), RoundedCornerShape(look.corner)),
            ) {
                ChatHeader(info, look)
                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    when (info.screen) {
                        "write", "text_input" -> ChatTyping(info, look)
                        "chat" -> ChatMessages(info, look, reveal = false)
                        "vote", "reveal" -> ChatMessages(info, look, reveal = info.screen == "reveal")
                        else -> ChatMessages(info, look, reveal = false)
                    }
                }
                ChatComposer(info, look)
            }
            Column(Modifier.weight(0.62f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth(), small = true, animated = true)
                info.twist?.takeIf { it.id != "clean" }?.let {
                    FiestaTwistRibbon(look, it.title, it.description, Modifier.fillMaxWidth())
                }
                FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FiestaSectionLabel("EN LÍNEA · ${info.submittedCount}/${info.voterCount}", look)
                        info.scoreboard.take(7).forEachIndexed { index, player ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).background(
                                        if (index < info.submittedCount) look.accent else look.accent.copy(alpha = 0.25f),
                                        CircleShape,
                                    ),
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    player.name,
                                    color = look.ink,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${player.score}", color = look.accent2, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(info: FiestaState, look: FiestaLook) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(look.accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(look.accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("TR", color = look.accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.prompt.ifBlank { "La conversación" },
                color = look.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "La Máquina, ${info.voterCount} participantes y ningún filtro",
                color = look.ink.copy(alpha = 0.55f),
                fontSize = 10.sp,
            )
        }
        FiestaTag("EN VIVO", look.accent, filled = true)
    }
}

@Composable
private fun ChatTyping(info: FiestaState, look: FiestaLook) {
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(380)
            dots = (dots + 1) % 4
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ChatBubble(
            look = look,
            role = info.you?.role.orEmpty().ifBlank { "TÚ" },
            text = "Estoy escribiendo mi línea…",
            mine = true,
            author = "",
            votes = 0,
            reveal = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text("•".repeat(dots.coerceAtLeast(1)), color = look.accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(8.dp))
            Text("alguien está escribiendo", color = look.ink.copy(alpha = 0.45f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            info.banter.ifBlank { "El chat se está incendiando." },
            color = look.accent.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChatMessages(info: FiestaState, look: FiestaLook, reveal: Boolean) {
    if (info.chatMessages.isNotEmpty()) {
        info.chatMessages.takeLast(9).forEach { message ->
            ChatBubble(
                look = look,
                role = message.role,
                text = message.text,
                mine = false,
                author = if (reveal) message.author else "",
                votes = message.reactionTotal,
                reveal = reveal,
                reactions = if (message.reactionTotal > 0) "🔥💀🤡🚨 ×${message.reactionTotal}" else "",
            )
        }
        return
    }
    val options = if (reveal) info.options.sortedByDescending { it.votes } else info.options
    if (options.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FiestaWaitingLine(look, "EL CHAT ESTÁ VACÍO", info.submittedCount, info.voterCount)
        }
        return
    }
    options.take(8).forEachIndexed { index, option ->
        ChatBubble(
            look = look,
            role = option.role.ifBlank { "JUGADOR" },
            text = option.text,
            mine = option.isYours,
            author = if (reveal) option.authorName else "",
            votes = option.votes,
            reveal = reveal,
            highlight = reveal && option.votes == (options.maxOfOrNull { it.votes } ?: 0) && option.votes > 0,
            favorite = reveal && option.audienceFavorite,
        )
    }
}

@Composable
private fun ChatBubble(
    look: FiestaLook,
    role: String,
    text: String,
    mine: Boolean,
    author: String,
    votes: Int,
    reveal: Boolean,
    highlight: Boolean = false,
    reactions: String = "",
    favorite: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(
                    RoundedCornerShape(
                        topStart = if (mine) 16.dp else 4.dp,
                        topEnd = if (mine) 4.dp else 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 16.dp,
                    ),
                )
                .background(
                    if (highlight) look.accent.copy(alpha = 0.22f)
                    else if (mine) look.accent.copy(alpha = 0.13f)
                    else Color.White.copy(alpha = 0.055f),
                )
                .border(
                    1.dp,
                    if (highlight) look.accent2 else look.accent.copy(alpha = 0.18f),
                    RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                )
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    role.uppercase(),
                    color = look.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (reveal && author.isNotBlank()) {
                    Text(author, color = look.ink.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1)
                }
                if (reveal) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$votes",
                        color = if (highlight) look.accent2 else look.ink.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                text,
                color = look.ink,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (favorite) {
                Text("FAVORITO DEL PÚBLICO (+150)", color = look.accent2, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            if (reactions.isNotBlank()) {
                Text(reactions, color = look.accent2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChatComposer(info: FiestaState, look: FiestaLook) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Escribe en tu teléfono…",
            color = look.ink.copy(alpha = 0.35f),
            fontSize = 11.sp,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(look.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(">", color = look.backdrop, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Franela Fest — pasarela
// ---------------------------------------------------------------------------
@Composable
private fun FranelaFestTv(info: FiestaState, look: FiestaLook, remaining: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "FRANELA",
                    color = look.ink,
                    fontFamily = look.display,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    letterSpacing = 5.sp,
                )
                Text(
                    "EDICIÓN ${info.round} · ${if (info.family) "TODO PÚBLICO" else "SOLO ADULTOS"} · ${look.tagline}",
                    color = look.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            FiestaBigTimer(look, remaining)
            Spacer(Modifier.width(10.dp))
            FiestaCurrencyChip(look, info.coins)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaPrompter(look, "EL BRIEF DEL CRÍTICO", info.prompt.ifBlank { "Diseña algo que merezca existir." })
                when (info.screen) {
                    "draw" -> RunwayWorkroom(info, look, Modifier.weight(1f))
                    "remix" -> RunwayRemixWorkroom(info, look, Modifier.weight(1f))
                    "vote", "reveal" -> RunwayRail(info, look, reveal = info.screen == "reveal", Modifier.weight(1f))
                    else -> FiestaWaitingLine(look, "LA PASARELA ESPERA", info.submittedCount, info.voterCount)
                }
            }
            Column(Modifier.weight(0.62f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth(), small = true, animated = true)
                CriticalScorecard(look, info)
                FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FiestaSectionLabel("DISEÑADORES", look)
                        info.scoreboard.take(6).forEachIndexed { index, player ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}.",
                                    color = look.accent2,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(22.dp),
                                )
                                Text(
                                    player.name,
                                    color = look.ink,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${player.score}", color = look.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        FiestaTicker(look, info.banter.ifBlank { "La moda es subjetiva; el ridículo es universal." } + " · Fotos: ${info.submittedCount}/${info.voterCount}", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        FiestaBottomScores(look, info)
    }
}

@Composable
private fun RunwayWorkroom(info: FiestaState, look: FiestaLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, look.accent.copy(alpha = 0.3f), RoundedCornerShape(look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("EL TALLER ESTÁ ABIERTO", color = look.accent, fontFamily = look.display, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text(
                "Los teléfonos se están manchando de tinta. ${info.submittedCount} de ${info.voterCount} ya colgaron su camiseta.",
                color = look.ink.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .size(70.dp, 78.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(look.accent.copy(alpha = 0.10f + index * 0.03f))
                            .border(1.dp, look.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    )
                }
            }
            FiestaTag("SIN VISTAS PREVIAS · SIN PIEDAD", look.accent2)
        }
    }
}

@Composable
private fun RunwayRemixWorkroom(info: FiestaState, look: FiestaLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A0718), Color(0xFF12020A))))
            .border(1.dp, look.accent.copy(alpha = 0.4f), RoundedCornerShape(look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("EL TALLER FRANKENSTEIN", color = look.accent, fontFamily = look.display, fontWeight = FontWeight.Black, fontSize = 27.sp)
            Text(
                "Dibujos y eslóganes ajenos se están cosiendo en la pasarela.",
                color = look.ink.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size(76.dp, 94.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(look.accent.copy(alpha = 0.10f + index * 0.04f))
                            .border(2.dp, look.accent2.copy(alpha = 0.35f + index * 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("???", color = look.accent2.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text("+", color = look.accent2, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Box(
                    Modifier
                        .size(96.dp, 104.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(look.accent.copy(alpha = 0.18f))
                        .border(2.dp, look.accent2, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("TU\nREMIX", color = look.ink, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }
            FiestaTag("CRÉDITOS PARA TODOS · CULPA PARA NADIE", look.accent2)
            FiestaWaitingLine(look, "REMIXANDO", info.submittedCount, info.voterCount)
        }
    }
}

@Composable
private fun RunwayRail(info: FiestaState, look: FiestaLook, reveal: Boolean, modifier: Modifier = Modifier) {
    if (info.options.isEmpty()) {
        FiestaWaitingLine(look, "COLGANDO LAS CAMISETAS", info.submittedCount, info.voterCount)
        return
    }
    val top = info.options.maxOfOrNull { it.votes } ?: 0
    LazyRow(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(info.options) { option ->
            RunwayCard(option, look, reveal, top)
        }
    }
}

@Composable
private fun RunwayCard(option: FiestaOption, look: FiestaLook, reveal: Boolean, top: Int) {
    val winner = reveal && option.votes == top && top > 0
    Column(
        Modifier
            .width(196.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (winner) look.accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
            .border(if (winner) 2.dp else 1.dp, if (winner) look.accent2 else look.accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF8F3EC)),
        ) {
            FiestaShirtCanvas(option.strokes, Modifier.fillMaxSize().padding(7.dp))
            Text(
                "CAMISETA ${option.id.takeLast(2).uppercase()}",
                color = Color(0xFF4A2033),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "«${option.slogan.ifBlank { option.text }}»",
            color = look.ink,
            fontFamily = look.display,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (option.audienceFavorite) {
            Text("FAVORITO DEL PÚBLICO (+150)", color = look.accent2, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
        if (reveal) {
            Text("Diseño remix: ${option.remixerName.ifBlank { option.authorName }}", color = look.ink.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
            Text("Arte original: ${option.authorName}", color = look.ink.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
            if (option.sloganAuthorName.isNotBlank()) {
                Text("Eslogan original: ${option.sloganAuthorName}", color = look.ink.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
            }
            Text(
                "${option.votes} votos",
                color = if (winner) look.accent2 else look.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        } else {
            Text("ANÓNIMO", color = look.accent.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FiestaShirtCanvas(strokes: List<GameStroke>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val left = size.width * 0.18f
        val right = size.width * 0.82f
        val top = size.height * 0.12f
        val bottom = size.height * 0.9f
        val shirt = Color(0xFF3B1028)
        drawLine(shirt, Offset(left, top + size.height * 0.12f), Offset(left, bottom), 4f)
        drawLine(shirt, Offset(right, top + size.height * 0.12f), Offset(right, bottom), 4f)
        drawLine(shirt, Offset(left, bottom), Offset(right, bottom), 4f)
        drawLine(shirt, Offset(left, top + size.height * 0.12f), Offset(size.width * 0.38f, top), 4f)
        drawLine(shirt, Offset(right, top + size.height * 0.12f), Offset(size.width * 0.62f, top), 4f)
        drawLine(shirt, Offset(size.width * 0.38f, top), Offset(size.width * 0.62f, top), 4f)
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
private fun CriticalScorecard(look: FiestaLook, info: FiestaState) {
    val score = 3 + (info.round % 7)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(look.ink.copy(alpha = 0.92f))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EL CRÍTICO DICE", color = look.panel, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text("$score/10", color = look.accent, fontFamily = look.display, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Text(
            when {
                score >= 8 -> "«Peligrosamente ponible.»"
                score >= 6 -> "«Hay algo ahí. No sé qué.»"
                score >= 4 -> "«Me duele la vista.»"
                else -> "«Esto es una demanda.»"
            },
            color = look.panel,
            fontFamily = look.display,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(10) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(if (index < score) look.accent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. El Tasador — casa de subastas
// ---------------------------------------------------------------------------
@Composable
private fun ElTasadorTv(info: FiestaState, look: FiestaLook, remaining: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "EL TASADOR",
                    color = look.ink,
                    fontFamily = look.display,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    letterSpacing = 4.sp,
                )
                Text(
                    "CASA DE SUBASTAS · LOTE ${info.round.toString().padStart(2, '0')} · ${look.tagline}",
                    color = look.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            FiestaBigTimer(look, remaining, label = "MARTILLO")
            Spacer(Modifier.width(10.dp))
            FiestaCurrencyChip(look, info.coins)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AuctionLot(info, look)
                when (info.screen) {
                    "invest" -> AuctionLotsBoard(info, look, reveal = false, Modifier.weight(1f))
                    "reveal" -> if (info.lots.isNotEmpty()) {
                        AuctionLotsBoard(info, look, reveal = true, Modifier.weight(1f))
                    } else {
                        AuctionPitches(info, look, reveal = true, Modifier.weight(1f))
                    }
                    "vote" -> AuctionPitches(info, look, reveal = false, Modifier.weight(1f))
                    else -> AuctionPitchDesk(info, look, Modifier.weight(1f))
                }
            }
            Column(Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FiestaHostBubble(look, info.hostCue?.text ?: info.instruction, Modifier.fillMaxWidth(), small = true, animated = true)
                AuctionGavel(look, info)
                FiestaSoftPanel(look, Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FiestaSectionLabel(if (info.screen == "reveal") "MARTILLO FINAL" else "POSTORES", look)
                        if (info.screen == "reveal" && info.trueValue > 0) {
                            Text(
                                "VALOR REAL: ${info.trueValue}",
                                color = look.accent2,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                            )
                        }
                        info.scoreboard.take(6).forEachIndexed { index, player ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}.",
                                    color = look.accent2,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(22.dp),
                                )
                                Text(
                                    player.name,
                                    color = look.ink,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${player.score}", color = look.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        FiestaTicker(look, info.banter.ifBlank { "El valor está en el ojo del comprador. Y en el pitch del vendedor." } + " · ${info.submittedCount}/${info.voterCount} lotes presentados", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        FiestaBottomScores(look, info)
    }
}

@Composable
private fun AuctionLot(info: FiestaState, look: FiestaLook) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Brush.horizontalGradient(listOf(Color(0xFF261504), Color(0xFF3D2507))))
            .border(2.dp, look.accent.copy(alpha = 0.55f), RoundedCornerShape(look.corner))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(108.dp, 122.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(fiestaColor("#F5E6C8"))
                .border(3.dp, look.accent.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LOTE", color = fiestaColor("#5A3A0A"), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(
                    info.round.toString().padStart(2, '0'),
                    color = fiestaColor("#3A2205"),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                )
                Text("?", color = fiestaColor("#8A5A12"), fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            FiestaSectionLabel("OBJETO EN SUBASTA", look)
            Text(
                info.objectInfo?.name ?: info.prompt.ifBlank { "Objeto misterioso" },
                color = look.ink,
                fontFamily = look.display,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 28.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                info.objectInfo?.description ?: info.hint,
                color = look.ink.copy(alpha = 0.65f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FiestaTag("VALOR DESCONOCIDO", look.accent2)
                FiestaTag("SIN DEVOLUCIONES", look.accent.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun AuctionLotsBoard(info: FiestaState, look: FiestaLook, reveal: Boolean, modifier: Modifier = Modifier) {
    if (info.lots.isEmpty()) {
        FiestaWaitingLine(look, "PREPARANDO LOS PEDESTALES", info.submittedCount, info.voterCount)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        info.lots.take(2).forEach { lot ->
            val authentic = lot.isAuthentic
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (reveal && authentic == false) fiestaColor("#3B0A0A").copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.05f),
                    )
                    .border(
                        if (reveal) 2.dp else 1.dp,
                        if (reveal) (if (authentic == true) look.accent2 else fiestaColor("#FF4B4B")) else look.accent.copy(alpha = 0.3f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LOTE ${lot.lotId}", color = look.accent2, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    if (reveal) {
                        Text(
                            if (authentic == true) "AUTÉNTICO" else "FALSIFICACIÓN",
                            color = if (authentic == true) look.accent2 else fiestaColor("#FF6B6B"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text(lot.title, color = look.ink, fontFamily = look.display, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Vendedor: ${lot.sellerName}", color = look.ink.copy(alpha = 0.55f), fontSize = 9.sp)
                Text(
                    "«${lot.pitch.ifBlank { "Un lote misterioso. Confíen en el Tasador." }}»",
                    color = look.ink.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (reveal) {
                    Text(
                        "VALOR REAL: ${lot.realValue ?: "?"} · PUJAS: ${lot.totalBids}",
                        color = look.accent2,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                } else {
                    Text("PRECIO OCULTO HASTA EL MARTILLO", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AuctionPitchDesk(info: FiestaState, look: FiestaLook, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.40f))
            .border(1.dp, look.accent.copy(alpha = 0.28f), RoundedCornerShape(look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuctionGavelIcon(look)
            Text("EL MARTILLO ESPERA", color = look.accent, fontFamily = look.display, fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text(
                "${info.submittedCount} de ${info.voterCount} postores ya escribieron su pitch y su precio.",
                color = look.ink.copy(alpha = 0.78f),
                fontSize = 13.sp,
            )
            FiestaTag("MENTIR ESTÁ PERMITIDO; CONVENCER, NO", look.accent2)
        }
    }
}

@Composable
private fun AuctionPitches(info: FiestaState, look: FiestaLook, reveal: Boolean, modifier: Modifier = Modifier) {
    val options = if (reveal) info.options.sortedByDescending { it.votes } else info.options
    if (options.isEmpty()) {
        FiestaWaitingLine(look, "PREPARANDO LOS LOTES", info.submittedCount, info.voterCount)
        return
    }
    val top = options.maxOfOrNull { it.votes } ?: 0
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FiestaSectionLabel(if (reveal) "EL MARTILLO HABLÓ" else "PITCHES SOBRE LA MESA", look)
        options.take(6).forEachIndexed { index, option ->
            val winner = reveal && option.votes == top && top > 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (winner) look.accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.045f))
                    .border(if (winner) 2.dp else 1.dp, if (winner) look.accent2 else look.accent.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    color = look.accent2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    modifier = Modifier.width(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        option.text,
                        color = look.ink,
                        fontSize = 12.sp,
                        fontWeight = if (winner) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (reveal) {
                        Text(
                            "${option.authorName} · pidió ${option.value}",
                            color = look.ink.copy(alpha = 0.55f),
                            fontSize = 9.sp,
                        )
                    }
                }
                if (reveal) {
                    Text(
                        "${option.votes} votos",
                        color = if (winner) look.accent2 else look.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuctionGavel(look: FiestaLook, info: FiestaState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuctionGavelIcon(look)
        Text(
            when (info.screen) {
                "reveal" -> "VENDIDO"
                "invest" -> "¡PUJEN!"
                "vote" -> "PUJAS ABIERTAS"
                else -> "SILENCIO, SE SUBASTA"
            },
            color = look.accent2,
            fontFamily = look.display,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
        if (info.screen == "reveal" && info.trueValue > 0) {
            Text(
                "${info.trueValue} ${look.currency}",
                color = look.accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun AuctionGavelIcon(look: FiestaLook) {
    Canvas(Modifier.size(58.dp)) {
        val c = look.accent
        drawLine(c, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.62f, size.height * 0.36f),
            strokeWidth = 7f, cap = StrokeCap.Round)
        drawRoundRect(
            color = c,
            topLeft = Offset(size.width * 0.52f, size.height * 0.14f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
        drawLine(look.accent2, Offset(size.width * 0.18f, size.height * 0.86f), Offset(size.width * 0.34f, size.height * 0.86f),
            strokeWidth = 4f, cap = StrokeCap.Round)
    }
}
