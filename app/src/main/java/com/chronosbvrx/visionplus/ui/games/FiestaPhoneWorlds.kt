package com.chronosbvrx.visionplus.ui.games

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.data.games.FiestaOption
import com.chronosbvrx.visionplus.data.games.FiestaState
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.GamePlayer
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStroke
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Controles de teléfono de Vision+ Fiesta. La regla es que el teléfono no
 * parezca un formulario: cada juego tiene su objeto físico (libreta, boleta,
 * consola, chat, taller, paleta de subasta) y su propio gesto principal.
 */
@Composable
fun FiestaWorldPhone(
    snap: GameSnapshot,
    info: FiestaState,
    playerToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val look = fiestaLook(info.game)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var draft by rememberSaveable(info.round, info.phase) { mutableStateOf("") }
    var draft2 by rememberSaveable(info.round, info.phase) { mutableStateOf("") }
    var value by rememberSaveable(info.round, info.phase) { mutableStateOf("") }
    var choice by rememberSaveable(info.round, info.phase) { mutableStateOf(-1) }
    var showTaunts by rememberSaveable { mutableStateOf(false) }
    val remaining = fiestaPhoneRemaining(snap, tick, snapshotAt)
    val mine = info.you
    val activePhoneScreen = mine?.screen?.takeIf { it.isNotBlank() } ?: info.screen
    val active = activePhoneScreen in listOf("text_input", "choice", "draw", "sort", "remix", "invest", "react", "chat", "vote")

    fun send(
        kind: String,
        text: String = "",
        target: String = "",
        index: Int = 0,
        amount: Int = 0,
        strokeJson: String = "",
        choices: List<String>? = null,
    ) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    code = snap.code,
                    playerToken = playerToken,
                    round = snap.round,
                    kind = kind,
                    text = text,
                    target = target,
                    index = index,
                    amount = amount,
                    strokes = strokeJson,
                    choices = choices,
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
        }
    }

    Box(Modifier.fillMaxSize()) {
        FiestaPhoneBackdrop(look)
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            FiestaPhoneTop(look, info, remaining)
            Spacer(Modifier.height(8.dp))
            if (info.hostCue?.text?.isNotBlank() == true) {
                FiestaHostBubble(look, info.hostCue.text, Modifier.fillMaxWidth(), small = true)
                Spacer(Modifier.height(8.dp))
            }
            if (mine?.hint?.isNotBlank() == true) {
                HintCard(look, mine.hint)
                Spacer(Modifier.height(8.dp))
            }
            if (info.screen == "reveal") {
                PhoneReveal(look, info, busy) { perk -> send("fiesta_perk", target = perk) }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.weight(1f)) {
                // Crossfade corto al cambiar de rol (REACTOR↔WRITER o
                // pitch↔invest) para que el layout no salte en seco.
                Crossfade(
                    targetState = activePhoneScreen,
                    animationSpec = tween(220),
                    label = "fiesta-phone-role",
                ) { screen ->
                when (info.game) {
                    "ultima_palabra" -> UltimaPhone(
                        info, look, screen, draft, { draft = it }, busy, mine?.submitted == true,
                        onSend = { send("fiesta_submit", text = draft.trim()) },
                        onVote = { target -> send("fiesta_vote", target = target) },
                    )
                    "vox_populi" -> VoxPhone(
                        info, look, screen, draft, { draft = it }, busy, mine?.submitted == true,
                        onSend = { send("fiesta_submit", text = draft.trim()) },
                        onVote = { target -> send("fiesta_vote", target = target) },
                    )
                    "fuera_de_epoca" -> TimePhone(
                        info, look, screen, choice, { choice = it }, busy, mine?.submitted == true,
                        onSend = { if (choice >= 0) send("fiesta_submit", index = choice) },
                        onSort = { order -> send("fiesta_sort", choices = order) },
                    )
                    "texto_roto" -> TextoPhone(
                        info, look, screen, draft, { draft = it }, busy, mine?.submitted == true,
                        onSend = { send("fiesta_submit", text = draft.trim()) },
                        onVote = { target -> send("fiesta_vote", target = target) },
                        onReact = { target -> send("fiesta_react", target = target) },
                    )
                    "franela_fest" -> FranelaPhone(
                        info, look, screen, draft, { draft = it }, draft2, { draft2 = it }, busy,
                        mine?.submitted == true,
                        onSend = { slogan1, slogan2, strokes ->
                            send("fiesta_draw", text = slogan1, target = slogan2, strokeJson = strokes)
                        },
                        onRemix = { drawingId, sloganId ->
                            send("fiesta_remix", target = drawingId, text = sloganId)
                        },
                        onVote = { target -> send("fiesta_vote", target = target) },
                    )
                    else -> TasadorPhone(
                        info, look, screen, draft, { draft = it }, busy, mine?.submitted == true,
                        onSend = { send("fiesta_submit", text = draft.trim()) },
                        onInvest = { lot, amount -> send("fiesta_invest", target = lot, amount = amount) },
                        onHint = { send("fiesta_hint") },
                        onVote = { target -> send("fiesta_vote", target = target) },
                    )
                }
                }
            }
            Spacer(Modifier.height(8.dp))
            PhoneActionDock(
                look, info, busy, active, showTaunts,
                onToggleTaunts = { showTaunts = !showTaunts },
                onHint = { send("fiesta_hint") },
                onTaunt = { text ->
                    showTaunts = false
                    send("fiesta_taunt", text = text)
                },
                onBet = { amount -> send("fiesta_bet", amount = amount) },
            )
            val buyerWaitingInPitch = info.game == "el_tasador" && info.phase == "pitch" && mine?.lotRole == "BUYER"
            if (activePhoneScreen != "reveal" && (mine?.submitted == true || activePhoneScreen == "wait" || buyerWaitingInPitch)) {
                Spacer(Modifier.height(8.dp))
                WaitingMiniGameCard(
                    look = look,
                    waitBonus = mine?.waitBonus == true,
                    enabled = !busy,
                    onBonus = { send("fiesta_wait_bonus") },
                )
            }
        }
        TauntSplatOverlay(look, mine?.incomingTaunt)
    }
}

private fun fiestaPhoneRemaining(snap: GameSnapshot, tick: Long, snapshotAt: Long): Int {
    if (snap.phaseEndsAt <= 0L) return 0
    val now = snap.serverNow + (tick - snapshotAt)
    return ((snap.phaseEndsAt - now) / 1000L).coerceAtLeast(0L).toInt()
}

@Composable
private fun FiestaPhoneTop(look: FiestaLook, info: FiestaState, remaining: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FiestaHostPortrait(look, 38.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.title.uppercase(),
                color = look.accent,
                fontFamily = look.display,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${look.hostName} · Ronda ${info.round}/${info.rounds} · ${info.twist?.title ?: ""}",
                color = look.ink.copy(alpha = 0.55f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FiestaCurrencyChip(look, info.coins)
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (remaining <= 5) fiestaColor("#FF4B4B").copy(alpha = 0.18f) else look.panel.copy(alpha = 0.8f))
                .border(1.dp, if (remaining <= 5) fiestaColor("#FF4B4B") else look.accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                remaining.toString(),
                color = if (remaining <= 5) fiestaColor("#FF4B4B") else look.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun HintCard(look: FiestaLook, text: String) {
    Text(
        "SOPLO: $text",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(look.accent2.copy(alpha = 0.14f))
            .border(1.dp, look.accent2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        color = look.accent2,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
    )
}

@Composable
private fun PhoneActionDock(
    look: FiestaLook,
    info: FiestaState,
    busy: Boolean,
    active: Boolean,
    showTaunts: Boolean,
    onToggleTaunts: () -> Unit,
    onHint: () -> Unit,
    onTaunt: (String) -> Unit,
    onBet: (Int) -> Unit,
) {
    val mine = info.you
    if (showTaunts && active) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(look.panel.copy(alpha = 0.96f))
                .border(1.dp, look.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Text("ELIGE TU BURLA · ${info.tauntCost} ${look.currency}", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            info.tauntChoices.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { taunt ->
                        Text(
                            taunt,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(look.accent2.copy(alpha = 0.16f))
                                .clickable(enabled = !busy && info.coins >= info.tauntCost) { onTaunt(taunt) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            color = look.accent2,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
            return
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val canSpend = info.screen != "vote"
        if (active) {
            DockChip(
                "${look.currencyIcon} SOPLO · ${info.hintCost}",
                look,
                enabled = !busy && canSpend && info.coins >= info.hintCost && mine?.hintUsed != true,
                accent = look.accent2,
                onClick = onHint,
                modifier = Modifier.weight(1f),
            )
            DockChip(
                "BURLA · ${info.tauntCost}",
                look,
                enabled = !busy && info.coins >= info.tauntCost,
                accent = fiestaColor("#F472B6"),
                onClick = onToggleTaunts,
                modifier = Modifier.weight(1f),
            )
            info.betOptions.forEach { amount ->
                DockChip(
                    if (mine?.bet == amount) "APOSTADO" else "APUESTA $amount",
                    look,
                    enabled = !busy && canSpend && (mine?.bet ?: 0) == 0 && info.coins >= amount,
                    accent = look.accent,
                    onClick = { onBet(amount) },
                    modifier = Modifier.weight(1.1f),
                )
            }
        } else {
            Text(
                when (info.screen) {
                    "vote" -> "Vota en las tarjetas de arriba. El dinero se mueve en la ronda."
                    "reveal" -> "La tienda está abierta: compra tu ventaja para la siguiente ronda."
                    else -> "Espera tu turno. La tele lleva el ritmo."
                },
                color = look.ink.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DockChip(
    label: String,
    look: FiestaLook,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (enabled) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        color = if (enabled) accent else look.ink.copy(alpha = 0.28f),
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------------------
// 1. La Última Palabra — libreta de concurso
// ---------------------------------------------------------------------------
@Composable
private fun UltimaPhone(
    info: FiestaState,
    look: FiestaLook,
    screen: String,
    draft: String,
    onDraft: (String) -> Unit,
    busy: Boolean,
    submitted: Boolean,
    onSend: () -> Unit,
    onVote: (String) -> Unit,
) {
    when (screen) {
        "vote" -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val duel = info.duelState
            if (duel != null) {
                Text(
                    "DUELO ${duel.duelIndex + 1} DE ${duel.totalDuels}" + if (duel.isFinal) " · FINAL" else "",
                    color = look.accent2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                val isMyDuel = info.options.any { it.isYours }
                if (isMyDuel && info.voterCount > 2) {
                    Text(
                        "¡ES TU DUELO! Cruza los dedos mientras la sala vota.",
                        color = look.accent2,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                } else if (info.you?.voted != true) {
                    Text(
                        "Las dos tarjetas están en la tele. Vota la que más duela.",
                        color = look.ink.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                    )
                }
            }
            PhoneVoteList(info, look, busy, onVote, "VOTA LA RESPUESTA QUE MÁS DUELE")
        }
        "text_input" -> if (submitted) {
            PhoneLocked(look, "RESPUESTA ENVIADA", "Ya está en la mesa. Ahora reza para que la sala tenga sentido del humor.", info.you?.text.orEmpty())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotebookCard(look, info.prompt.ifBlank { "La frase de Nora" })
                OutlinedTextField(
                    value = draft,
                    onValueChange = { onDraft(it.take(180)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = { Text("Tu respuesta más cafre…", color = look.ink.copy(alpha = 0.35f), fontSize = 13.sp) },
                    textStyle = TextStyle(color = look.ink, fontSize = 15.sp, lineHeight = 19.sp),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = look.accent,
                        unfocusedBorderColor = look.accent.copy(alpha = 0.35f),
                        cursorColor = look.accent,
                        focusedContainerColor = Color.Black.copy(alpha = 0.28f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.18f),
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${draft.length}/180", color = look.ink.copy(alpha = 0.4f), fontSize = 9.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            draft.length < 8 -> "Muy corta para tanto riesgo"
                            draft.length < 40 -> "Promete. No la cagues."
                            else -> "Nora ya está sonriendo"
                        },
                        color = look.accent2,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                BigAction(
                    "MANDAR A LA MESA",
                    look,
                    enabled = !busy && draft.trim().length >= 2,
                    onClick = onSend,
                )
            }
        }
        else -> PhoneWaiting(look, info, "ESCRIBIENDO", "La tele está preparando la siguiente ronda.")
    }
}

@Composable
private fun NotebookCard(look: FiestaLook, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(fiestaColor("#FFF7E6"), fiestaColor("#FFE8C3"))))
            .border(2.dp, look.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text("LA FRASE", color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text(
            text,
            color = fiestaColor("#3A1608"),
            fontFamily = look.display,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PhoneVoteList(
    info: FiestaState,
    look: FiestaLook,
    busy: Boolean,
    onVote: (String) -> Unit,
    label: String,
) {
    if (info.you?.voted == true) {
        PhoneLocked(look, "VOTO ENVIADO", "La sala está contando. No se aceptan sobornos… todavía.", "")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = look.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        info.options.forEachIndexed { index, option ->
            val yours = option.isYours
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (yours) Color.White.copy(alpha = 0.04f) else look.panel.copy(alpha = 0.86f))
                    .border(
                        if (info.you?.vote == option.id) 2.dp else 1.dp,
                        if (info.you?.vote == option.id) look.accent2 else look.accent.copy(alpha = 0.28f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = !busy && !yours) { onVote(option.id) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOf("A", "B", "C", "D", "E", "F", "G", "H").getOrElse(index) { "?" },
                    color = look.accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.width(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        option.text,
                        color = if (yours) look.ink.copy(alpha = 0.5f) else look.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (yours) Text("TU RESPUESTA · no puedes votarla", color = look.accent.copy(alpha = 0.8f), fontSize = 8.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Vox Populi — boleta de encuesta
// ---------------------------------------------------------------------------
@Composable
private fun VoxPhone(
    info: FiestaState,
    look: FiestaLook,
    screen: String,
    draft: String,
    onDraft: (String) -> Unit,
    busy: Boolean,
    submitted: Boolean,
    onSend: () -> Unit,
    onVote: (String) -> Unit,
) {
    if (screen == "reveal") {
        VoxResultPhone(info, look)
        return
    }
    if (screen == "choice") {
        VoxHiloPhone(info, look, busy, onVote)
        return
    }
    if (submitted) {
        PhoneLocked(look, "BOLETA DEPOSITADA", "Tu respuesta ya está en la urna. El noticiero la leerá en voz alta.", info.you?.text.orEmpty())
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(fiestaColor("#F3F7F6"))
                .border(1.dp, look.accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("● EN VIVO", color = fiestaColor("#D7263D"), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("BOLETA ${info.round.toString().padStart(2, '0')}", color = fiestaColor("#0B4F5C"), fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(7.dp))
            Text("PREGUNTA DEL DÍA", color = fiestaColor("#0B4F5C"), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                info.prompt,
                color = fiestaColor("#08343D"),
                fontFamily = look.display,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                maxLines = 5,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "La mayoría no siempre tiene razón, pero sí votos.",
                color = fiestaColor("#4B6B70"),
                fontSize = 10.sp,
            )
        }
        Text("TU RESPUESTA CIUDADANA", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        BasicTextField(
            value = draft,
            onValueChange = { onDraft(it.take(90).replace("\n", " ")) },
            textStyle = TextStyle(color = look.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(look.panel.copy(alpha = 0.92f))
                .border(2.dp, look.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 15.dp),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (draft.isEmpty()) {
                        Text("Escribe como hablaría la gente…", color = look.ink.copy(alpha = 0.28f), fontSize = 12.sp)
                    }
                    inner()
                }
            },
        )
        if (info.you?.quickChips?.isNotEmpty() == true) {
            Text("SUGERENCIAS DEL SONDEO", color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            info.you!!.quickChips.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { chip ->
                        Text(
                            chip,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(look.accent.copy(alpha = 0.10f))
                                .border(1.dp, look.accent.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                                .clickable { onDraft(chip) }
                                .padding(horizontal = 7.dp, vertical = 6.dp),
                            color = look.ink,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${draft.length}/90", color = look.ink.copy(alpha = 0.4f), fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "SELLO: CIUDADANO ANÓNIMO",
                color = look.accent2,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .border(2.dp, look.accent2.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        BigAction("DEPOSITAR EN LA URNA", look, enabled = !busy && draft.trim().length >= 2, onClick = onSend)
        Text(
            "Don Chisme leerá tu respuesta. Si es demasiado rara, mejor.",
            color = look.ink.copy(alpha = 0.45f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoxHiloPhone(info: FiestaState, look: FiestaLook, busy: Boolean, onVote: (String) -> Unit) {
    if (info.you?.voted == true) {
        PhoneLocked(look, "VOTO HILO ENVIADO", "El noticiero está contando. La mayoría tiene la última palabra.", "")
        return
    }
    val vox = info.voxState
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("SONDEO HILO · ¿DÓNDE CAE?", color = look.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(fiestaColor("#F3F7F6"))
                .border(1.dp, look.accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Text("PUESTO #3 REVELADO", color = fiestaColor("#0B4F5C"), fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(vox?.anchorRank3Text.orEmpty(), color = fiestaColor("#08343D"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("RESPUESTA MISTERIOSA", color = fiestaColor("#0B4F5C"), fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(
                "«${vox?.mysteryAnswer.orEmpty()}»",
                color = fiestaColor("#08343D"),
                fontFamily = look.display,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            "¿Quedó más arriba o más abajo del puesto 3?",
            color = look.ink.copy(alpha = 0.65f),
            fontSize = 10.sp,
        )
        HiloButton("▲ MÁS ARRIBA · TOP 1–2", look, !busy) { onVote("MAS_ARRIBA") }
        HiloButton("▼ MÁS ABAJO · PUESTOS 4–6", look, !busy) { onVote("MAS_ABAJO") }
        Text(
            "Acierta y ganas +250. La calle no perdona.",
            color = look.accent2,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HiloButton(label: String, look: FiestaLook, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) look.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, look.accent.copy(alpha = if (enabled) 0.6f else 0.15f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(top = 17.dp),
        color = if (enabled) look.ink else look.ink.copy(alpha = 0.3f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        letterSpacing = 0.8.sp,
    )
}

@Composable
private fun VoxResultPhone(info: FiestaState, look: FiestaLook) {
    val options = info.options.sortedBy { it.rank }
    val max = options.maxOfOrNull { it.count } ?: 1
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("LO QUE DIJO LA CALLE", color = look.accent, fontFamily = look.display, fontSize = 16.sp, fontWeight = FontWeight.Black)
        options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = look.accent2, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.text, color = look.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FiestaProgressBar(look, option.count.toFloat() / max.coerceAtLeast(1), Modifier.fillMaxWidth(), height = 4.dp)
                }
                Text("${option.count}", color = look.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
            }
        }
        val guess = info.reveal?.lines?.firstOrNull { it.contains("—") }
        if (guess != null) Text(guess, color = look.ink.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

// ---------------------------------------------------------------------------
// 3. Fuera de Época — consola temporal
// ---------------------------------------------------------------------------
@Composable
private fun TimePhone(
    info: FiestaState,
    look: FiestaLook,
    screen: String,
    choice: Int,
    onChoice: (Int) -> Unit,
    busy: Boolean,
    submitted: Boolean,
    onSend: () -> Unit,
    onSort: (List<String>) -> Unit,
) {
    if (screen == "sort") {
        TimeSortPhone(info, look, busy, onSort)
        return
    }
    if (screen == "reveal") {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("AÑO CORRECTO", color = look.accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            val correct = info.years.getOrNull(info.correct)?.toString() ?: "—"
            Text(correct, color = look.accent2, fontFamily = FontFamily.Monospace, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text(
                if (info.you?.choice == info.correct) "Acertaste. El profesor está orgulloso y un poco insoportable."
                else "Fallaste. La historia tomará nota.",
                color = look.ink,
                fontSize = 13.sp,
            )
            Text("Racha actual: ${info.you?.points ?: 0} pts en esta ronda", color = look.ink.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        return
    }
    if (submitted) {
        PhoneLocked(look, "VIAJE REGISTRADO", "Tu año está en la máquina. El profesor ya está ajustando los lentes.", info.years.getOrNull(choice)?.toString().orEmpty())
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "La máquina detectó un evento. Elige el año y tira de la palanca.",
            color = look.ink.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            info.years.forEachIndexed { index, year ->
                if (index % 2 == 0) {
                    YearPhoneTile(
                        look, year, index, choice == index,
                        blocked = info.you?.hintYears?.isNotEmpty() == true && index !in info.you.hintYears,
                        modifier = Modifier.weight(1f),
                        onClick = { onChoice(index) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            info.years.forEachIndexed { index, year ->
                if (index % 2 == 1) {
                    YearPhoneTile(
                        look, year, index, choice == index,
                        blocked = info.you?.hintYears?.isNotEmpty() == true && index !in info.you.hintYears,
                        modifier = Modifier.weight(1f),
                        onClick = { onChoice(index) },
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        BigAction("TIRAR DE LA PALANCA", look, enabled = !busy && choice >= 0, onClick = onSend)
        Text(
            "El profesor Tic Tac no acepta «hace mucho» como respuesta.",
            color = look.accent.copy(alpha = 0.75f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TimeSortPhone(info: FiestaState, look: FiestaLook, busy: Boolean, onSort: (List<String>) -> Unit) {
    val items = info.sortItems
    val ordered = remember(items) { mutableStateListOf<String>().apply { addAll(items.map { it.id }) } }
    val byId = items.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MÁS ANTIGUO", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(
            "Ordena la línea temporal con ▲ y ▼. Los años están ocultos.",
            color = look.ink.copy(alpha = 0.65f),
            fontSize = 10.sp,
        )
        ordered.forEachIndexed { index, id ->
            val item = byId[id] ?: return@forEachIndexed
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(look.panel.copy(alpha = 0.88f))
                    .border(1.dp, look.accent.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
                    .padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}º",
                    color = look.accent2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.width(30.dp),
                )
                Text(
                    item.title,
                    color = look.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "▲",
                        modifier = Modifier
                            .clickable(enabled = !busy && index > 0) {
                                val value = ordered.removeAt(index)
                                ordered.add(index - 1, value)
                            }
                            .padding(4.dp),
                        color = if (index > 0) look.accent else look.ink.copy(alpha = 0.15f),
                        fontSize = 16.sp,
                    )
                    Text(
                        "▼",
                        modifier = Modifier
                            .clickable(enabled = !busy && index < ordered.size - 1) {
                                val value = ordered.removeAt(index)
                                ordered.add(index + 1, value)
                            }
                            .padding(4.dp),
                        color = if (index < ordered.size - 1) look.accent else look.ink.copy(alpha = 0.15f),
                        fontSize = 16.sp,
                    )
                }
            }
        }
        Text("MÁS RECIENTE", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        BigAction("FIJAR LÍNEA TEMPORAL", look, enabled = !busy && ordered.size == 3) {
            onSort(ordered.toList())
        }
    }
}

@Composable
private fun YearPhoneTile(
    look: FiestaLook,
    year: Int,
    index: Int,
    selected: Boolean,
    blocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(82.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    blocked -> Color.Black.copy(alpha = 0.45f)
                    selected -> look.accent.copy(alpha = 0.26f)
                    else -> look.panel.copy(alpha = 0.82f)
                },
            )
            .border(
                if (selected) 2.dp else 1.dp,
                when {
                    blocked -> Color.White.copy(alpha = 0.07f)
                    selected -> look.accent2
                    else -> look.accent.copy(alpha = 0.30f)
                },
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = !blocked) { onClick() }
            .padding(9.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            listOf("A", "B", "C", "D").getOrElse(index) { "?" },
            color = if (blocked) look.ink.copy(alpha = 0.18f) else look.accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            if (blocked) "— — — —" else year.toString(),
            color = if (blocked) look.ink.copy(alpha = 0.18f) else if (selected) look.accent2 else look.ink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 25.sp,
            letterSpacing = 1.sp,
        )
        Text(
            when {
                blocked -> "AÑO APAGADO"
                selected -> "SELECCIONADO"
                else -> "CANDIDATO"
            },
            color = if (blocked) look.ink.copy(alpha = 0.16f) else look.ink.copy(alpha = 0.4f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// 4. Texto Roto — chat
// ---------------------------------------------------------------------------
@Composable
private fun TextoPhone(
    info: FiestaState,
    look: FiestaLook,
    screen: String,
    draft: String,
    onDraft: (String) -> Unit,
    busy: Boolean,
    submitted: Boolean,
    onSend: () -> Unit,
    onVote: (String) -> Unit,
    onReact: (String) -> Unit,
) {
    if (screen == "react") {
        TextoReactPhone(info, look, busy, onReact)
        return
    }
    if (screen == "vote") {
        PhoneVoteList(info, look, busy, onVote, "VOTA LA LÍNEA MÁS GRACIOSA")
        return
    }
    if (screen == "reveal") {
        PhoneLocked(look, "CHAT CERRADO", "La conversación ya es historia. Alguien va a tener que explicarla.", "")
        return
    }
    if (submitted) {
        PhoneLocked(look, "MENSAJE ENVIADO", "Tu línea ya está en el chat. La Máquina la está leyendo en voz alta.", info.you?.text.orEmpty())
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info.chatMessages.isNotEmpty()) {
            ChatLiveFeed(info, look, maxMessages = 3)
        }
        FiestaRoleBadge(look, info.you?.role.orEmpty())
        if (!info.you?.secretGoal.isNullOrBlank()) {
            Text(
                "DIRECTIVA SECRETA: ${info.you?.secretGoal}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(look.accent2.copy(alpha = 0.16f))
                    .border(1.dp, look.accent2.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                color = look.accent2,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Escena: ${info.prompt}",
            color = look.ink.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp))
                .background(look.accent.copy(alpha = 0.16f))
                .border(1.dp, look.accent.copy(alpha = 0.35f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp))
                .padding(12.dp),
        ) {
            Column {
                Text("TÚ · ${info.you?.role.orEmpty().uppercase()}", color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { onDraft(it.take(160)) },
                    textStyle = TextStyle(color = look.ink, fontSize = 15.sp, lineHeight = 20.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text("Escribe la línea que va a arruinar todo…", color = look.ink.copy(alpha = 0.3f), fontSize = 12.sp)
                            }
                            inner()
                        }
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${draft.length}/160", color = look.ink.copy(alpha = 0.4f), fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "LA MÁQUINA ESTÁ MIRANDO",
                color = look.accent2,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("jajaja", "no manches", "órale", "yo avisé").forEach { quick ->
                Text(
                    quick,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { onDraft((draft + " " + quick).trim().take(160)) }
                        .padding(vertical = 7.dp),
                    color = look.ink.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        BigAction("ENVIAR AL CHAT", look, enabled = !busy && draft.trim().length >= 2, onClick = onSend)
    }
}

@Composable
private fun ChatLiveFeed(info: FiestaState, look: FiestaLook, maxMessages: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        info.chatMessages.takeLast(maxMessages).forEach { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (message.role == "WRITER") look.accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${message.author} · ${message.role}", color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(message.text, color = look.ink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (message.reactionTotal > 0) {
                    Text("${message.reactionTotal} reacciones", color = look.accent2, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TextoReactPhone(info: FiestaState, look: FiestaLook, busy: Boolean, onReact: (String) -> Unit) {
    val last = info.chatMessages.lastOrNull()
    var reacted by rememberSaveable(last?.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatLiveFeed(info, look, maxMessages = 5)
        Text(
            "Otros están escribiendo… ¡sabotea o reacciona al chat!",
            color = look.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        if (last != null) {
            Text(
                "Último mensaje: «${last.text}»",
                color = look.ink.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("🔥", "💀", "🤡", "🚨").forEach { emoji ->
                    Text(
                        emoji,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(look.accent2.copy(alpha = 0.12f))
                            .border(1.dp, look.accent2.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !busy && !reacted) {
                                reacted = true
                                onReact(last.id)
                            }
                            .padding(top = 14.dp),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Text("Aún no hay mensajes. La Máquina está tomando notas.", color = look.ink.copy(alpha = 0.45f), fontSize = 10.sp)
        }
        Text(
            "Reaccionar te da +10 Datos y al autor +25 puntos.",
            color = look.accent2,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// 5. Franela Fest — taller de camisetas
// ---------------------------------------------------------------------------
private data class WorldStroke(
    val color: String,
    val width: Float = 8f,
    val points: MutableList<Offset> = mutableListOf(),
)

@Composable
private fun FranelaPhone(
    info: FiestaState,
    look: FiestaLook,
    screen: String,
    slogan: String,
    onSlogan: (String) -> Unit,
    slogan2: String,
    onSlogan2: (String) -> Unit,
    busy: Boolean,
    submitted: Boolean,
    onSend: (String, String, String) -> Unit,
    onRemix: (String, String) -> Unit,
    onVote: (String) -> Unit,
) {
    if (screen == "remix") {
        FranelaRemixPhone(info, look, busy, onRemix)
        return
    }
    if (screen == "vote") {
        PhoneShirtVote(info, look, busy, onVote)
        return
    }
    if (screen == "reveal") {
        PhoneLocked(look, "PASARELA CERRADA", "El jurado ya deliberó. La demanda está en camino.", "")
        return
    }
    if (submitted) {
        PhoneLocked(look, "CAMISETA ENVIADA", "Tu diseño ya está en la pasarela. El Crítico está afilando la pluma.", slogan)
        return
    }
    val strokes = remember(info.round) { mutableStateListOf<WorldStroke>() }
    var color by remember { mutableStateOf("#F472B6") }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<WorldStroke?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            info.prompt.ifBlank { "Diseña una camiseta que merezca existir." },
            color = look.ink,
            fontFamily = look.display,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fiestaColor("#FBF3E7"))
                .border(2.dp, look.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val stroke = WorldStroke(color, 8f, mutableListOf(offset))
                            current = stroke
                            strokes.add(stroke)
                        },
                        onDrag = { change, _ ->
                            current?.points?.add(change.position)
                            // Fuerza recomposición del Canvas.
                            current = current?.copy(points = current!!.points)
                        },
                        onDragEnd = { current = null },
                        onDragCancel = { current = null },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val left = size.width * 0.18f
                val right = size.width * 0.82f
                val top = size.height * 0.1f
                val bottom = size.height * 0.92f
                val shirt = Color(0xFFB08A6A)
                drawLine(shirt, Offset(left, top + size.height * 0.1f), Offset(left, bottom), 5f)
                drawLine(shirt, Offset(right, top + size.height * 0.1f), Offset(right, bottom), 5f)
                drawLine(shirt, Offset(left, bottom), Offset(right, bottom), 5f)
                drawLine(shirt, Offset(left, top + size.height * 0.1f), Offset(size.width * 0.38f, top), 5f)
                drawLine(shirt, Offset(right, top + size.height * 0.1f), Offset(size.width * 0.62f, top), 5f)
                drawLine(shirt, Offset(size.width * 0.38f, top), Offset(size.width * 0.62f, top), 5f)
                strokes.forEach { stroke ->
                    val ink = runCatching { Color(android.graphics.Color.parseColor(stroke.color)) }.getOrDefault(Color.Black)
                    for (i in 1 until stroke.points.size) {
                        val a = stroke.points[i - 1]
                        val b = stroke.points[i]
                        drawLine(
                            ink,
                            Offset(size.width * 0.22f + a.x / canvasSize.width.coerceAtLeast(1) * size.width * 0.56f,
                                size.height * 0.2f + a.y / canvasSize.height.coerceAtLeast(1) * size.height * 0.6f),
                            Offset(size.width * 0.22f + b.x / canvasSize.width.coerceAtLeast(1) * size.width * 0.56f,
                                size.height * 0.2f + b.y / canvasSize.height.coerceAtLeast(1) * size.height * 0.6f),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Text(
                "TALLER DE FRANELAS",
                color = Color(0xFFB08A6A).copy(alpha = 0.5f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("#F472B6", "#FBBF24", "#22D3EE", "#34D399", "#A78BFA", "#111827").forEach { hex ->
                Box(
                    Modifier
                        .padding(end = 7.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(fiestaColor(hex))
                        .border(if (color == hex) 3.dp else 1.dp, if (color == hex) Color.White else Color.Black.copy(alpha = 0.3f), CircleShape)
                        .clickable { color = hex },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Arrastra el dedo",
                color = look.ink.copy(alpha = 0.35f),
                fontSize = 8.sp,
            )
        }
        OutlinedTextField(
            value = slogan,
            onValueChange = { onSlogan(it.take(80)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Eslogan 1: el que lo vende todo…", color = look.ink.copy(alpha = 0.32f), fontSize = 12.sp) },
            textStyle = TextStyle(color = look.ink, fontSize = 13.sp),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = look.accent,
                unfocusedBorderColor = look.accent.copy(alpha = 0.32f),
                cursorColor = look.accent,
                focusedContainerColor = Color.Black.copy(alpha = 0.24f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.16f),
            ),
        )
        OutlinedTextField(
            value = slogan2,
            onValueChange = { onSlogan2(it.take(80)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Eslogan 2: el que se te ocurrió después…", color = look.ink.copy(alpha = 0.32f), fontSize = 12.sp) },
            textStyle = TextStyle(color = look.ink, fontSize = 13.sp),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = look.accent,
                unfocusedBorderColor = look.accent.copy(alpha = 0.32f),
                cursorColor = look.accent,
                focusedContainerColor = Color.Black.copy(alpha = 0.24f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.16f),
            ),
        )
        BigAction(
            "MANDAR AL TALLER",
            look,
            enabled = !busy && strokes.isNotEmpty() && slogan.trim().length >= 2,
            onClick = { onSend(slogan.trim(), slogan2.trim(), worldStrokesToJson(strokes)) },
        )
    }
}

@Composable
private fun FranelaRemixPhone(
    info: FiestaState,
    look: FiestaLook,
    busy: Boolean,
    onRemix: (String, String) -> Unit,
) {
    val pool = info.you?.remixPool
    if (pool == null || pool.drawings.isEmpty() || pool.slogans.isEmpty()) {
        PhoneWaiting(look, info, "TALLER DE REMIX", "El Crítico está barajando dibujos y eslóganes.")
        return
    }
    var drawingId by remember(pool) { mutableStateOf(pool.drawings.first().id) }
    var sloganId by remember(pool) { mutableStateOf(pool.slogans.first().id) }
    val drawing = pool.drawings.firstOrNull { it.id == drawingId } ?: pool.drawings.first()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("ENSAMBLA TU CAMISETA FRANKENSTEIN", color = look.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(
            "Elige un dibujo ajeno y un eslogan ajeno. El crédito se reparte.",
            color = look.ink.copy(alpha = 0.6f),
            fontSize = 9.sp,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fiestaColor("#FBF3E7"))
                .border(2.dp, look.accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        ) {
            FiestaShirtCanvas(drawing.strokes, Modifier.fillMaxSize().padding(10.dp))
            Text(
                "DIBUJO · ${drawing.ownerName}",
                color = Color(0xFF5A3A0A),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pool.drawings.forEachIndexed { index, item ->
                Text(
                    "DIBUJO ${index + 1}",
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (item.id == drawingId) look.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (item.id == drawingId) look.accent2 else look.accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { drawingId = item.id }
                        .padding(vertical = 7.dp),
                    color = look.ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text("ESLOGANES AJENOS", color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        pool.slogans.forEachIndexed { index, item ->
            Text(
                "«${item.text}»",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (item.id == sloganId) look.accent2.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                    .border(1.dp, if (item.id == sloganId) look.accent2 else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable { sloganId = item.id }
                    .padding(8.dp),
                color = look.ink,
                fontSize = 10.sp,
                fontWeight = if (item.id == sloganId) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
            )
        }
        BigAction("MANDAR A LA PASARELA", look, enabled = !busy) {
            onRemix(drawingId, sloganId)
        }
    }
}

private fun worldStrokesToJson(strokes: List<WorldStroke>): String {
    val array = JSONArray()
    strokes.take(8).forEach { stroke ->
        val points = JSONArray()
        stroke.points.take(120).forEach { point ->
            points.put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble()))
        }
        if (points.length() >= 2) {
            array.put(
                JSONObject()
                    .put("color", stroke.color)
                    .put("width", stroke.width)
                    .put("points", points),
            )
        }
    }
    return array.toString()
}

@Composable
private fun PhoneShirtVote(info: FiestaState, look: FiestaLook, busy: Boolean, onVote: (String) -> Unit) {
    if (info.you?.voted == true) {
        PhoneLocked(look, "VOTO DE MODA ENVIADO", "El jurado está deliberando. La ética del diseño también.", "")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("VOTA LA QUE TE PONDRÍAS… O REGALARÍAS", color = look.accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        info.options.forEach { option ->
            val yours = option.isYours
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(look.panel.copy(alpha = 0.88f))
                    .border(
                        if (info.you?.vote == option.id) 2.dp else 1.dp,
                        if (info.you?.vote == option.id) look.accent2 else look.accent.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = !busy && !yours) { onVote(option.id) }
                    .padding(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8F3EC)),
                ) {
                    FiestaShirtCanvas(option.strokes, Modifier.fillMaxSize().padding(8.dp))
                }
                Spacer(Modifier.height(5.dp))
                Text("«${option.slogan.ifBlank { option.text }}»", color = look.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (yours) Text("TU DISEÑO · no puedes votarlo", color = look.accent.copy(alpha = 0.8f), fontSize = 8.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. El Tasador — paleta de subasta
// ---------------------------------------------------------------------------
@Composable
private fun TasadorPhone(
    info: FiestaState,
    look: FiestaLook,
    screen: String,
    draft: String,
    onDraft: (String) -> Unit,
    busy: Boolean,
    submitted: Boolean,
    onSend: () -> Unit,
    onInvest: (String, Int) -> Unit,
    onHint: () -> Unit,
    onVote: (String) -> Unit,
) {
    if (screen == "invest") {
        TasadorInvestPhone(info, look, busy, submitted, onInvest)
        return
    }
    if (screen == "vote") {
        PhoneVoteList(info, look, busy, onVote, "VOTA EL PITCH QUE TE CONVENCIÓ")
        return
    }
    if (screen == "reveal") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("EL MARTILLO HABLÓ", color = look.accent, fontFamily = look.display, fontSize = 17.sp, fontWeight = FontWeight.Black)
            if (info.trueValue > 0) {
                Text("VALOR REAL DEL LOTE A: ${info.trueValue}", color = look.accent2, fontFamily = FontFamily.Monospace, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Text(
                info.reveal?.lines?.joinToString("\n").orEmpty(),
                color = look.ink.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    val seller = info.you?.lotRole == "SELLER"
    if (!seller) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SALA DE COMPRADORES", color = look.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                "Los vendedores están preparando sus lotes A y B…",
                color = look.ink,
                fontFamily = look.display,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
            Text(
                "Inspecciona el catálogo, compra un Soplo si te alcanza y afila la paleta.",
                color = look.ink.copy(alpha = 0.62f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            if (info.trueValue > 0 || info.objectInfo != null) {
                Text(
                    "Catálogo en la tele: dos piezas, una sospecha.",
                    color = look.accent2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            val hint = info.you?.hint.orEmpty()
            if (hint.isNotBlank()) {
                Text(
                    "SOPLO DEL TASADOR: $hint",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(look.accent2.copy(alpha = 0.14f))
                        .border(1.dp, look.accent2.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    color = look.accent2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    "COMPRAR SOPLO · ${info.hintCost} ${info.currencyIcon}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(look.accent.copy(alpha = 0.12f))
                        .border(1.dp, look.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !busy) { onHint() }
                        .padding(vertical = 10.dp),
                    color = look.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    if (submitted) {
        PhoneLocked(look, "LOTE PRESENTADO", "Tu pitch ya está sobre la mesa. El Tasador está calculando cuánto insultarte.", draft)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF261504))
                .border(1.dp, look.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TU LOTE ${info.you?.lotId.orEmpty()}", color = look.accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("VALOR DESCONOCIDO", color = look.accent2, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Text(
            info.objectInfo?.name ?: info.prompt,
            color = look.ink,
            fontFamily = look.display,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            info.objectInfo?.description ?: info.hint,
            color = look.ink.copy(alpha = 0.6f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!info.you?.lotHint.isNullOrBlank()) {
            Text(
                "PISTA SECRETA: ${info.you?.lotHint}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(look.accent2.copy(alpha = 0.12f))
                    .border(1.dp, look.accent2.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                color = look.accent2,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "El precio lo ponen los compradores. Tú solo tienes que vender humo con clase.",
            color = look.ink.copy(alpha = 0.5f),
            fontSize = 9.sp,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { onDraft(it.take(160)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            placeholder = { Text("Vende humo con seguridad…", color = look.ink.copy(alpha = 0.32f), fontSize = 12.sp) },
            textStyle = TextStyle(color = look.ink, fontSize = 13.sp, lineHeight = 17.sp),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = look.accent,
                unfocusedBorderColor = look.accent.copy(alpha = 0.32f),
                cursorColor = look.accent,
                focusedContainerColor = Color.Black.copy(alpha = 0.24f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.16f),
            ),
        )
        BigAction(
            "PRESENTAR EL LOTE",
            look,
            enabled = !busy && draft.trim().length >= 2,
            onClick = onSend,
        )
    }
}

@Composable
private fun TasadorInvestPhone(
    info: FiestaState,
    look: FiestaLook,
    busy: Boolean,
    submitted: Boolean,
    onInvest: (String, Int) -> Unit,
) {
    if (info.you?.lotRole == "SELLER") {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("¡SUBASTA EN CURSO!", color = look.accent, fontFamily = look.display, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(
                "Mira la TV para ver cuánto pujan por tu lote ${info.you?.lotId.orEmpty()}.",
                color = look.ink,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Text(
                "Si tu lote es falso y la puja pasa de 250, el Tasador te premia por audacia. O te lleva preso. Una de dos.",
                color = look.ink.copy(alpha = 0.6f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
        return
    }
    if (submitted) {
        PhoneLocked(look, "PUJA REGISTRADA", "Tu dinero está sobre la mesa. El martillo decide si fue genialidad o donativo.", "")
        return
    }
    var lot by remember(info.round) { mutableStateOf("A") }
    val lots = info.lots
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ELIGE TU LOTE", color = look.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            lots.forEach { item ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (lot == item.lotId) look.accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                        .border(2.dp, if (lot == item.lotId) look.accent2 else look.accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .clickable { lot = item.lotId }
                        .padding(8.dp),
                ) {
                    Text("LOTE ${item.lotId}", color = look.accent2, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(item.title, color = look.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Vendedor: ${item.sellerName}", color = look.ink.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
                }
            }
        }
        val selected = lots.firstOrNull { it.lotId == lot }
        if (selected != null) {
            Text(
                "«${selected.pitch.ifBlank { "Un lote misterioso. Confíen en el Tasador." }}»",
                color = look.ink.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Puja con ${info.currency} · si el valor real supera tu puja, ganas la diferencia.",
            color = look.ink.copy(alpha = 0.5f),
            fontSize = 9.sp,
        )
        listOf(0 to "PASAR · $0", 100 to "PUJAR · $100", 250 to "PUJAR · $250", 400 to "PUJAR · $400").forEach { (amount, label) ->
            Text(
                label,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (amount == 0) {
                            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.03f)))
                        } else {
                            Brush.horizontalGradient(listOf(look.accent.copy(alpha = 0.22f), look.accent2.copy(alpha = 0.14f)))
                        },
                    )
                    .border(1.dp, if (amount == 0) look.ink.copy(alpha = 0.12f) else look.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) { onInvest(lot, amount) }
                    .padding(top = 14.dp),
                color = if (amount == 0) look.ink.copy(alpha = 0.6f) else look.ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 0.8.sp,
            )
        }
        BigAction("LEVANTAR LA PALETA", look, enabled = !busy) {
            onInvest(lot, 100)
        }
    }
}

// ---------------------------------------------------------------------------
// Piezas compartidas
// ---------------------------------------------------------------------------
@Composable
private fun BigAction(label: String, look: FiestaLook, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(look.accent, look.accent2))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.06f))),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(top = 15.dp),
        color = if (enabled) look.backdrop else look.ink.copy(alpha = 0.30f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun PhoneLocked(look: FiestaLook, title: String, message: String, echo: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(look.panel.copy(alpha = 0.86f))
            .border(1.dp, look.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, color = look.accent, fontFamily = look.display, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(message, color = look.ink.copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 14.sp)
        if (echo.isNotBlank()) {
            Text(
                echo,
                color = look.ink,
                fontFamily = look.display,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .padding(10.dp),
            )
        }
        Text("Esperando al resto de la sala…", color = look.accent2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhoneWaiting(look: FiestaLook, info: FiestaState, title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(look.panel.copy(alpha = 0.80f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = look.accent, fontFamily = look.display, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(message, color = look.ink.copy(alpha = 0.65f), fontSize = 11.sp)
        FiestaProgressBar(look, if (info.voterCount <= 0) 0f else info.submittedCount.toFloat() / info.voterCount, Modifier.fillMaxWidth())
        Text("${info.submittedCount}/${info.voterCount} listos", color = look.ink.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

@Composable
private fun PhoneReveal(
    look: FiestaLook,
    info: FiestaState,
    busy: Boolean,
    onBuyPerk: (String) -> Unit,
) {
    val mine = info.you
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                info.reveal?.title?.uppercase() ?: "RESULTADOS",
                color = look.accent,
                fontFamily = look.display,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            if ((mine?.bet ?: 0) > 0) {
                FiestaTag(
                    if (mine?.betWon == true) "+${mine.betPayout} APUESTA" else "APUESTA PERDIDA",
                    if (mine?.betWon == true) look.accent2 else fiestaColor("#FF6B6B"),
                    filled = true,
                )
            }
        }
        info.reveal?.lines?.take(3)?.forEachIndexed { index, line ->
            Text(
                "$index. $line",
                color = look.ink.copy(alpha = 0.82f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (mine?.betWon == true) {
            Text(
                "Cobraste ${mine?.betPayout ?: 0} ${look.currency} por apostar ${mine?.bet ?: 0}.",
                color = look.accent2,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(look.shop.uppercase(), color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            info.perks.forEach { perk ->
                val bought = mine?.perk == perk.id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (bought) look.accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (bought) look.accent2 else look.accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy && !bought && info.coins >= perk.cost) { onBuyPerk(perk.id) }
                        .padding(6.dp),
                ) {
                    Text(perk.title, color = look.ink, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${perk.cost} ${look.currency}", color = look.accent2, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
