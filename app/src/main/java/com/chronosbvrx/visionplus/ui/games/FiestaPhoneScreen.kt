package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.data.games.FiestaCatalog
import com.chronosbvrx.visionplus.data.games.FiestaOption
import com.chronosbvrx.visionplus.data.games.FiestaState
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStroke
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Pantalla del teléfono para los seis juegos de Vision+ Fiesta 3.0.
 * La tele pinta el estado público; aquí solo se escribe, se elige, se dibuja y se vota.
 */
@Composable
fun FiestaPhoneContent(
    snap: GameSnapshot,
    playerToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val info = snap.fiesta
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(snap.info.accent))
        }
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var draft by rememberSaveable(info.round, info.phase) { mutableStateOf("") }
    var choice by rememberSaveable(info.round, info.phase) { mutableStateOf(-1) }
    var value by rememberSaveable(info.round, info.phase) { mutableStateOf("") }
    val strokes = remember(info.round) { mutableStateListOf<FiestaStroke>() }
    var strokeColor by remember { mutableStateOf("#F97316") }
    val remaining = fiestaRemaining(snap, tick, snapshotAt)
    val accent = Color(snap.info.accent)

    fun send(kind: String, text: String = "", target: String = "", index: Int = 0,
             amount: Int = 0, strokeJson: String = "") {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    code = snap.code, playerToken = playerToken, round = snap.round,
                    kind = kind, text = text, target = target, index = index,
                    amount = amount, strokes = strokeJson,
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FiestaPhoneHeader(snap, info, remaining, accent)
        if (!info.hostCue?.text.isNullOrBlank()) {
            FiestaHostBubble(
                gameId = info.game,
                text = info.hostCue?.text.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        }
        if (info.screen !in listOf("bonus", "bonus_reveal")) {
            FiestaPromptCard(info, accent)
        }
        when (info.screen) {
            "text_input" -> FiestaTextInput(
                info = info, draft = draft, onDraft = { draft = it },
                busy = busy, accent = accent,
                onSubmit = {
                    val amountValue = value.toIntOrNull() ?: 0
                    if (info.game == "el_tasador") {
                        send("fiesta_submit", text = draft.trim(), amount = amountValue)
                    } else {
                        send("fiesta_submit", text = draft.trim())
                    }
                },
                showValue = info.game == "el_tasador",
                value = value, onValue = { value = it.filter { ch -> ch.isDigit() }.take(5) },
            )
            "choice" -> FiestaChoice(
                years = info.years, choice = choice, onChoice = { choice = it },
                busy = busy, accent = accent, submitted = info.you?.submitted == true,
                onSubmit = { if (choice >= 0) send("fiesta_submit", index = choice) },
            )
            "draw" -> FiestaDraw(
                strokes = strokes, color = strokeColor, onColor = { strokeColor = it },
                slogan = draft, onSlogan = { draft = it }, busy = busy, accent = accent,
                submitted = info.you?.submitted == true,
                onSubmit = {
                    if (strokes.isNotEmpty() && draft.trim().isNotEmpty()) {
                        send(
                            "fiesta_draw", text = draft.trim(),
                            strokeJson = strokesToJson(strokes),
                        )
                    }
                },
            )
            "vote" -> FiestaVote(
                options = info.options, busy = busy, accent = accent,
                voted = info.you?.voted == true, myVote = info.you?.vote.orEmpty(),
                onVote = { target -> send("fiesta_vote", target = target) },
            )
            "bonus" -> FiestaBonusPick(
                info = info, busy = busy, accent = accent,
                onPick = { index -> send("fiesta_bonus", index = index) },
            )
            "bonus_reveal" -> FiestaBonusResults(info, accent)
            "reveal" -> FiestaReveal(
                info = info, snap = snap, accent = accent, busy = busy,
                onBuyPerk = { perk -> send("fiesta_perk", target = perk) },
            )
            else -> FiestaWaiting(info, busy)
        }
        if (info.you?.submitted == true && info.screen != "reveal") {
            Text(
                "Listo. Esperando al resto de la sala…",
                color = VisionSuccess,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FiestaPhoneHeader(
    snap: GameSnapshot,
    info: FiestaState,
    remaining: Int,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                info.title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
                color = accent,
            )
            Text(
                "${FiestaCatalog.hostName(info.game)} · Ronda ${info.round} de ${info.rounds}",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (info.family) VisionSurfaceHigh else VisionLiveRed.copy(alpha = 0.22f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (info.family) accent.copy(alpha = 0.45f) else VisionLiveRed,
            ),
        ) {
            Text(
                if (info.family) "FAMILIAR" else "SIN FILTRO",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (info.family) accent else VisionLiveRed,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${remaining}s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (remaining <= 5) VisionLiveRed else VisionTextPrimary,
            )
            Text(
                "● ${info.you?.coins ?: info.coins}",
                style = MaterialTheme.typography.labelSmall,
                color = VisionRatingGold,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FiestaPromptCard(info: FiestaState, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(info.instruction, style = MaterialTheme.typography.labelMedium, color = accent)
            Text(
                info.prompt.ifBlank { "Prepárate…" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VisionTextPrimary,
            )
            if (info.hint.isNotBlank()) {
                Text(info.hint, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
            if (info.game == "texto_roto" && (info.you?.role ?: "").isNotBlank()) {
                Text(
                    "Tu papel: ${info.you?.role}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VisionBlueBright,
                )
            }
            if (info.game == "franela_fest" && info.screen == "draw") {
                Text(
                    "El dibujo y el eslogan se votan juntos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
            info.twist?.takeIf { it.id != "clean" }?.let { twist ->
                Text(
                    "${twist.title} · ${twist.description}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionRatingGold,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (info.banter.isNotBlank()) {
                Text(
                    info.banter,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = accent.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun FiestaBonusPick(
    info: FiestaState,
    busy: Boolean,
    accent: Color,
    onPick: (Int) -> Unit,
) {
    val bonus = info.bonus ?: return
    val picked = info.you?.bonusPick ?: -1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(bonus.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = accent)
        Text(bonus.instruction, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
        bonus.options.forEachIndexed { index, option ->
            val selected = picked == index
            Surface(
                onClick = { if (!busy && picked < 0) onPick(index) },
                enabled = !busy && picked < 0,
                shape = RoundedCornerShape(18.dp),
                color = if (selected) accent.copy(alpha = 0.28f) else VisionSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) accent else Color.White.copy(alpha = 0.15f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("◆", color = accent, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(option.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (picked >= 0) {
            Text("Cofre elegido. Esperando al resto…", color = VisionSuccess, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FiestaBonusResults(info: FiestaState, accent: Color) {
    val bonus = info.bonus ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LA RULETA DE FIESTA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = accent)
            if (bonus.results.isEmpty()) {
                Text("Nadie abrió su cofre.", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
            }
            bonus.results.forEach { result ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(result.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(result.label, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "+${result.coins} monedas" + if (result.points > 0) " · +${result.points} pts" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VisionRatingGold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FiestaTextInput(
    info: FiestaState,
    draft: String,
    onDraft: (String) -> Unit,
    busy: Boolean,
    accent: Color,
    onSubmit: () -> Unit,
    showValue: Boolean,
    value: String,
    onValue: (String) -> Unit,
) {
    if (info.you?.submitted == true) {
        FiestaWaiting(info, busy)
        return
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { onDraft(it.take(180)) },
        label = { Text(if (info.game == "el_tasador") "Tu pitch" else "Tu respuesta") },
        placeholder = { Text("Escribe algo que valga la pena…") },
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    )
    if (showValue) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text("¿Cuánto vale? (entero)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        )
    }
    Button(
        onClick = onSubmit,
        enabled = !busy && draft.trim().length >= 2 && (!showValue || value.isNotBlank()),
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(if (busy) "Enviando…" else "Enviar", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FiestaChoice(
    years: List<Int>,
    choice: Int,
    onChoice: (Int) -> Unit,
    busy: Boolean,
    accent: Color,
    submitted: Boolean,
    onSubmit: () -> Unit,
) {
    if (submitted) {
        Text("Respuesta enviada. Esperando al resto…", color = VisionSuccess)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        years.forEachIndexed { index, year ->
            val selected = choice == index
            Surface(
                onClick = { onChoice(index) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) accent.copy(alpha = 0.3f) else VisionSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) accent else Color.White.copy(alpha = 0.15f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    year.toString(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VisionTextPrimary,
                )
            }
        }
        Button(
            onClick = onSubmit,
            enabled = !busy && choice >= 0,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (busy) "Enviando…" else "Confirmar año", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FiestaVote(
    options: List<FiestaOption>,
    busy: Boolean,
    accent: Color,
    voted: Boolean,
    myVote: String,
    onVote: (String) -> Unit,
) {
    if (voted) {
        Text("Voto enviado. Esperando al resto…", color = VisionSuccess)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val selected = myVote == option.id
            Surface(
                onClick = { if (!option.isYours) onVote(option.id) },
                enabled = !busy && !option.isYours,
                shape = RoundedCornerShape(16.dp),
                color = when {
                    selected -> accent.copy(alpha = 0.3f)
                    option.isYours -> VisionSurface.copy(alpha = 0.5f)
                    else -> VisionSurfaceHigh
                },
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) accent else Color.White.copy(alpha = 0.13f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (option.slogan.isNotBlank()) {
                        Text(
                            "«${option.slogan}»",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = VisionTextPrimary,
                        )
                    } else {
                        Text(
                            option.text,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = VisionTextPrimary,
                        )
                    }
                    if (option.role.isNotBlank()) {
                        Text(option.role, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    if (option.strokes.isNotEmpty()) {
                        MiniFiestaCanvas(option.strokes, Modifier.fillMaxWidth().height(120.dp))
                    }
                    if (option.isYours) {
                        Text("Tu creación", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
                    }
                }
            }
        }
        Text(
            "No puedes votar tu propia creación.",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
    }
}

@Composable
private fun FiestaReveal(
    info: FiestaState,
    snap: GameSnapshot,
    accent: Color,
    busy: Boolean,
    onBuyPerk: (String) -> Unit,
) {
    val reveal = info.reveal
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                reveal?.title?.ifBlank { "Resultados" } ?: "Resultados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            reveal?.lines?.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = VisionTextPrimary)
            }
            if (info.game == "el_tasador" && info.trueValue > 0) {
                Text(
                    "Valor real: ${info.trueValue}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VisionRatingGold,
                )
            }
        }
    }
    // Tienda entre rondas: solo aparece cuando todavía hay una ronda siguiente.
    if (info.round < info.rounds && info.perks.isNotEmpty()) {
        FiestaPerkShop(info, accent, busy, onBuyPerk)
    }
    FiestaScoreboard(info, accent)
}

@Composable
private fun FiestaPerkShop(
    info: FiestaState,
    accent: Color,
    busy: Boolean,
    onBuyPerk: (String) -> Unit,
) {
    val coins = info.you?.coins ?: info.coins
    val bought = info.you?.perk.orEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurfaceHigh.copy(alpha = 0.94f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TIENDA DE LA FIESTA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = accent)
                Spacer(Modifier.weight(1f))
                Text("● $coins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = VisionRatingGold)
            }
            Text(
                "Compra una ventaja para la siguiente ronda. Se paga con monedas.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            info.perks.forEach { perk ->
                val owned = bought == perk.id
                Surface(
                    onClick = { if (!busy && bought.isBlank()) onBuyPerk(perk.id) },
                    enabled = !busy && bought.isBlank() && coins >= perk.cost,
                    shape = RoundedCornerShape(12.dp),
                    color = if (owned) accent.copy(alpha = 0.25f) else VisionSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (owned) accent else Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(perk.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(perk.description, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                        }
                        Text("${perk.cost}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = VisionRatingGold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FiestaWaiting(info: FiestaState, busy: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (busy) CircularProgressIndicator(color = VisionBlue) else {
            Text("Esperando al resto de la sala…", color = VisionTextSecondary)
            Text(
                "${info.submittedCount} de ${info.voterCount} listos",
                style = MaterialTheme.typography.labelSmall,
                color = VisionTextSecondary,
            )
        }
    }
}

@Composable
private fun FiestaScoreboard(info: FiestaState, accent: Color) {
    val players = info.scoreboard.take(5)
    if (players.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("MARCADOR", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        players.forEachIndexed { index, player ->
            Row(
                modifier = Modifier.fillMaxWidth().background(
                    if (index == 0) accent.copy(alpha = 0.18f) else VisionSurface,
                    RoundedCornerShape(10.dp),
                ).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}. ${player.name}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextPrimary,
                )
                Text(
                    "${player.score}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (index == 0) VisionRatingGold else accent,
                )
            }
        }
    }
}

private data class FiestaStroke(
    val color: String,
    val width: Float,
    val points: MutableList<Offset>,
)

@Composable
private fun FiestaDraw(
    strokes: MutableList<FiestaStroke>,
    color: String,
    onColor: (String) -> Unit,
    slogan: String,
    onSlogan: (String) -> Unit,
    busy: Boolean,
    accent: Color,
    submitted: Boolean,
    onSubmit: () -> Unit,
) {
    if (submitted) {
        Text("Camiseta enviada. Esperando al resto…", color = VisionSuccess)
        return
    }
    val palette = listOf("#F97316", "#22D3EE", "#A78BFA", "#34D399", "#F472B6", "#FBBF24", "#FFFFFF", "#111827")
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var redraw by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        palette.forEach { hex ->
            val selected = color == hex
            Box(
                Modifier.size(if (selected) 34.dp else 28.dp)
                    .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) accent else Color.White.copy(alpha = 0.35f),
                        CircleShape,
                    )
                    .clickable { onColor(hex) },
            )
        }
    }
    Box(
        Modifier.fillMaxWidth().height(280.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(18.dp))
            .onSizeChanged { canvasSize = it }
            .pointerInput(color, canvasSize) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            strokes.add(FiestaStroke(color, 8f, mutableListOf(normalizeFiestaPoint(offset, canvasSize))))
                            redraw++
                        }
                    },
                    onDrag = { change, _ ->
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            strokes.lastOrNull()?.points?.add(normalizeFiestaPoint(change.position, canvasSize))
                            redraw++
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (redraw >= 0) {
                strokes.forEach { stroke ->
                    val points = stroke.points
                    for (index in 1 until points.size) {
                        drawLine(
                            color = Color(android.graphics.Color.parseColor(stroke.color)),
                            start = Offset(points[index - 1].x / 1000f * size.width, points[index - 1].y / 1000f * size.height),
                            end = Offset(points[index].x / 1000f * size.width, points[index].y / 1000f * size.height),
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
        if (strokes.isEmpty()) {
            Text(
                "Dibuja aquí",
                color = Color(0xFF64748B),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { strokes.clear() }) { Text("Limpiar") }
        TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
            Text("Deshacer")
        }
    }
    OutlinedTextField(
        value = slogan,
        onValueChange = { onSlogan(it.take(80)) },
        label = { Text("Eslogan de la camiseta") },
        placeholder = { Text("Algo que quieras presumir…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    )
    Button(
        onClick = onSubmit,
        enabled = !busy && strokes.isNotEmpty() && slogan.trim().length >= 2,
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(if (busy) "Enviando…" else "Enviar camiseta", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniFiestaCanvas(strokes: List<GameStroke>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))) {
        strokes.forEach { stroke ->
            val color = runCatching { Color(android.graphics.Color.parseColor(stroke.color)) }
                .getOrDefault(Color.White)
            for (index in 1 until stroke.points.size) {
                drawLine(
                    color = color,
                    start = Offset(
                        stroke.points[index - 1].x / 1000f * size.width,
                        stroke.points[index - 1].y / 1000f * size.height,
                    ),
                    end = Offset(
                        stroke.points[index].x / 1000f * size.width,
                        stroke.points[index].y / 1000f * size.height,
                    ),
                    strokeWidth = stroke.width.coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun normalizeFiestaPoint(offset: Offset, size: IntSize): Offset = Offset(
    (offset.x / size.width.toFloat() * 1000f).coerceIn(0f, 1000f),
    (offset.y / size.height.toFloat() * 1000f).coerceIn(0f, 1000f),
)

private fun strokesToJson(strokes: List<FiestaStroke>): String {
    val array = JSONArray()
    strokes.take(8).forEach { stroke ->
        val points = JSONArray()
        stroke.points.take(120).forEach { point ->
            points.put(
                JSONArray()
                    .put(point.x.roundToInt().coerceIn(0, 1000))
                    .put(point.y.roundToInt().coerceIn(0, 1000)),
            )
        }
        array.put(
            JSONObject()
                .put("color", stroke.color)
                .put("width", stroke.width)
                .put("points", points),
        )
    }
    return array.toString()
}

private fun fiestaRemaining(snap: GameSnapshot, tick: Long, snapshotAt: Long): Int {
    if (snap.phaseEndsAt <= 0L) return 0
    val serverNow = snap.serverNow + (tick - snapshotAt)
    return ((snap.phaseEndsAt - serverNow) / 1000L).coerceAtLeast(0L).toInt()
}
