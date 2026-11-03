package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.games.GameCatalog
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.GameQuestion
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameTriviaItem
import com.chronosbvrx.visionplus.data.games.NarratorEvent
import com.chronosbvrx.visionplus.data.games.NarratorMood
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

private val RedWire = Color(0xFFFF5C6C)
private val BlueWire = Color(0xFF4D9CFF)
private val YellowWire = Color(0xFFF5C518)
private val GreenWire = Color(0xFF66D98A)

private fun wireColor(wire: String): Color = when (wire) {
    "red" -> RedWire
    "blue" -> BlueWire
    "yellow" -> YellowWire
    else -> GreenWire
}

private fun wireLabel(wire: String): String = when (wire) {
    "red" -> "Rojo"
    "blue" -> "Azul"
    "yellow" -> "Amarillo"
    else -> "Verde"
}

@Composable
internal fun TriviaV2Content(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val trivia = snap.trivia ?: return
    val accent = Color(GameCatalog.Trivia.accent)
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameStageBadge(
                when (snap.phase) {
                    "attack" -> "ATAQUE"
                    "punishment", "punishment_reveal" -> "CASTIGO"
                    "sort" -> "RÁFAGA"
                    "assault" -> "ASALTO FINAL"
                    "reveal" -> "RESULTADO"
                    else -> "PREGUNTA"
                },
                accent,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (snap.phase == "reveal" || snap.phase == "punishment_reveal") "Resultado" else "$seconds s",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (snap.phase == "reveal" || snap.phase == "punishment_reveal") VisionTextSecondary else accent,
            )
        }
        if (snap.phase !in listOf("reveal", "punishment_reveal")) {
            LinearProgressIndicator(
                progress = {
                    (seconds.toFloat() / snap.phaseDuration.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = VisionSurfaceHigh,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(
                mood = when (snap.phase) {
                    "punishment", "punishment_reveal" -> NarratorMood.Oops
                    "assault" -> NarratorMood.Celebrate
                    "reveal" -> NarratorMood.Happy
                    else -> NarratorMood.Thinking
                },
                size = 78.dp,
                alive = true,
            )
            Spacer(Modifier.width(8.dp))
            NarratorBubble(
                when (snap.phase) {
                    "attack" -> "La racha despierta poderes. Úsala o guárdala."
                    "punishment" -> if (trivia.you.isLoser) "Corta un cable… o paga el precio." else "Vota qué cable crees que explota."
                    "punishment_reveal" -> "El cable caliente era ${wireLabel(trivia.punishment?.hotWire.orEmpty())}."
                    "sort" -> "Clasifica rápido. Cada carta correcta paga ${250}."
                    "assault" -> "Selecciona todas las que estrenaron antes de 2015."
                    "reveal" -> if (trivia.you.correct) "¡Bien ahí!" else "Ni modo, a la siguiente."
                    else -> if (trivia.you.frozen) "¡Rompe el hielo!" else "Piénsale bien."
                },
            )
        }
        when (snap.phase) {
            "attack" -> TriviaAttackCard(snap, playerToken, onError)
            "question" -> TriviaQuestionV2(snap, playerToken, onError)
            "punishment", "punishment_reveal" -> TriviaPunishmentCard(snap, playerToken, hostToken, onError)
            "sort" -> TriviaSortCard(snap, playerToken, onError)
            "assault" -> TriviaAssaultCard(snap, playerToken, onError)
            "reveal" -> TriviaV2Reveal(snap, playerToken, hostToken, onError)
        }
        GameScoreboard(snap, accent)
    }
}

@Composable
private fun TriviaAttackCard(
    snap: GameSnapshot,
    playerToken: String,
    onError: (String) -> Unit,
) {
    val trivia = snap.trivia ?: return
    val scope = rememberCoroutineScope()
    var power by remember(snap.round) { mutableStateOf("freeze") }
    var target by remember(snap.round) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val attack = trivia.attack

    fun send(kindPower: String, targetId: String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "trivia_attack",
                    power = kindPower, target = targetId,
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("Ese ataque ya no está disponible.")
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, VisionLiveRed.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("El piso se quema", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Con dos aciertos seguidos desbloqueas un ataque. Congelar y censurar cuestan ${attack.cost}; el parásito es gratis pero arriesga 300 si el huésped falla.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            if (attack.used.isNotEmpty()) {
                Text(
                    attack.used.joinToString(" · ") { "${it.byName}: ${it.power} → ${it.targetName}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionRatingGold,
                )
            }
            if (!attack.canAttack) {
                Text(
                    if (attack.eligibleCount > 0) "Esperando a los que tienen racha…"
                    else "Nadie llegó con racha. La pregunta arranca en un momento.",
                    color = VisionTextSecondary,
                )
            } else {
                Text("Tu poder", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "freeze" to "Congelar",
                        "glitch" to "Censura",
                        "parasite" to "Parásito",
                    ).forEach { (id, label) ->
                        val selected = power == id
                        Surface(
                            onClick = { power = id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) VisionLiveRed.copy(alpha = 0.24f) else VisionSurfaceHigh,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) VisionLiveRed else Color.White.copy(alpha = 0.12f),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text("Objetivo", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attack.targets.chunked(4).forEach { rowTargets ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTargets.forEach { candidate ->
                                val selected = target == candidate.id
                                Surface(
                                    onClick = { target = candidate.id },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) VisionBlueBright.copy(alpha = 0.22f) else VisionSurfaceHigh,
                                    border = BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) VisionBlueBright else Color.White.copy(alpha = 0.12f),
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        candidate.name,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }
                            repeat(4 - rowTargets.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { send(power, target) },
                        enabled = !busy && target.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (busy) "Enviando…"
                            else if (power == "parasite") "Usar parásito (gratis)"
                            else "Usar ataque (-${attack.cost})",
                        )
                    }
                    OutlinedButton(
                        onClick = { send("pass", "") },
                        enabled = !busy,
                    ) { Text("Pasar") }
                }
            }
        }
    }
}

@Composable
private fun TriviaQuestionV2(
    snap: GameSnapshot,
    playerToken: String,
    onError: (String) -> Unit,
) {
    val trivia = snap.trivia ?: return
    val question = snap.question ?: return
    val scope = rememberCoroutineScope()
    val me = snap.you
    var busy by remember { mutableStateOf(false) }
    var picked by remember(snap.round) { mutableIntStateOf(me?.selected ?: -1) }

    fun answer(option: Int) {
        if (busy || me?.answered == true) return
        picked = option
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.answer(snap.code, playerToken, snap.round, option)
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("La respuesta llegó tarde o ya estaba registrada.")
            }
        }
    }

    fun tap() {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(snap.code, playerToken, snap.round, "trivia_tap")
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
        }
    }

    if (trivia.you.parasiteTarget.isNotBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, VisionLiveRed.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estás colgado de ${trivia.you.parasiteTargetName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "No respondes: si tu objetivo acierta, cobras el 75 % de sus puntos; si falla, ambos pierden 300.",
                    color = VisionTextSecondary,
                )
            }
        }
        return
    }

    if (trivia.you.frozen && question.options.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, VisionBlueBright.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CONGELADO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Golpea la pantalla ${trivia.you.freezeTaps} veces para romper el hielo.", color = VisionTextSecondary)
                Text(
                    "${trivia.you.frozenTaps} / ${trivia.you.freezeTaps}",
                    style = MaterialTheme.typography.displaySmall,
                    color = VisionBlueBright,
                    fontWeight = FontWeight.Black,
                )
                Button(onClick = ::tap, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Golpeando…" else "Golpear hielo")
                }
            }
        }
        return
    }

    Text(question.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (trivia.you.glitched) {
        Text("La censura borró parte de las opciones.", color = VisionLiveRed, style = MaterialTheme.typography.bodySmall)
    }
    if (question.art.isNotBlank()) {
        AsyncImage(
            model = question.art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(16.dp)).background(VisionSurface),
        )
    }
    GameOptions(
        question = question,
        selected = if (picked >= 0) picked else (me?.selected ?: -1),
        correct = -1,
        reveal = false,
        enabled = !busy && me?.answered != true,
        onAnswer = ::answer,
    )
    if (me?.answered == true) {
        Text("Respuesta enviada. Esperando a los demás…", color = VisionTextSecondary)
    }
    Text(
        "Puntos: ${me?.score ?: 0} · Racha: ${me?.streak ?: 0}",
        style = MaterialTheme.typography.labelMedium,
        color = VisionRatingGold,
    )
}

@Composable
private fun TriviaPunishmentCard(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    onError: (String) -> Unit,
) {
    val trivia = snap.trivia ?: return
    val punishment = trivia.punishment ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val reveal = snap.phase == "punishment_reveal"
    val isLoser = trivia.you.isLoser
    var wire by remember(snap.round) { mutableStateOf("") }

    fun send(selected: String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "trivia_punish",
                    wire = selected, vote = !isLoser,
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("Ese cable ya no se puede elegir.")
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, VisionLiveRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (reveal) "El cable caliente era ${wireLabel(punishment.hotWire)}"
                else if (isLoser) "Corta un cable" else "Vota el cable que crees que explota",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (reveal) "Los que cortaron el cable caliente perdieron 500 puntos."
                else if (isLoser) "Si coincides con el cable más votado por los que acertaron, pierdes 500."
                else "Tu voto decide el cable caliente. Los castigados no votan.",
                color = VisionTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                punishment.wires.forEach { candidate ->
                    val selected = wire == candidate
                    Surface(
                        onClick = { if (!reveal) wire = candidate },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) wireColor(candidate).copy(alpha = 0.24f) else VisionSurfaceHigh,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) wireColor(candidate) else Color.White.copy(alpha = 0.12f),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            wireLabel(candidate),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = wireColor(candidate),
                        )
                    }
                }
            }
            if (!reveal) {
                Button(
                    onClick = { send(wire) },
                    enabled = !busy && wire.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Enviando…" else if (isLoser) "Cortar ${wireLabel(wire)}" else "Votar ${wireLabel(wire)}")
                }
            } else {
                punishment.penalties.forEach { penalty ->
                    Text(
                        "${snap.players.firstOrNull { it.id == penalty.id }?.name ?: "Jugador"}: ${penalty.amount}",
                        color = VisionLiveRed,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                GameRevealActions(snap, playerToken, hostToken, onError)
            }
        }
    }
}

@Composable
private fun TriviaSortCard(
    snap: GameSnapshot,
    playerToken: String,
    onError: (String) -> Unit,
) {
    val sort = snap.trivia?.sort ?: return
    val scope = rememberCoroutineScope()
    val choices = remember(snap.round) { mutableStateListOf<Int>().apply {
        repeat(sort.items.size) { add(sort.choices.getOrNull(it) ?: -1) }
    } }
    var busy by remember { mutableStateOf(false) }
    val submitted = sort.submitted || snap.trivia?.you?.sortSubmitted == true
    val complete = choices.size == sort.items.size && choices.all { it >= 0 }

    fun send() {
        if (busy || submitted || !complete) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "trivia_sort",
                    choices = choices.toList(),
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("La clasificación llegó tarde.")
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, VisionBlueBright.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ráfaga clasificatoria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("¿Antes o después? Elige un lado para cada carta.", color = VisionTextSecondary)
            sort.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                    listOf(0, 1).forEach { side ->
                        val selected = choices.getOrNull(index) == side
                        Surface(
                            onClick = { if (!submitted) choices[index] = side },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) VisionBlueBright.copy(alpha = 0.22f) else VisionSurfaceHigh,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) VisionBlueBright else Color.White.copy(alpha = 0.1f),
                            ),
                        ) {
                            Text(
                                if (side == 0) sort.left else sort.right,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) VisionBlueBright else VisionTextSecondary,
                            )
                        }
                    }
                }
            }
            Button(onClick = ::send, enabled = !busy && !submitted && complete, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Enviando…" else if (submitted) "Esperando a los demás…" else "Enviar clasificación")
            }
        }
    }
}

@Composable
private fun TriviaAssaultCard(
    snap: GameSnapshot,
    playerToken: String,
    onError: (String) -> Unit,
) {
    val assault = snap.trivia?.assault ?: return
    val scope = rememberCoroutineScope()
    val selected = remember(snap.round) { mutableStateListOf<Int>().apply {
        addAll(assault.selected)
    } }
    var busy by remember { mutableStateOf(false) }
    val submitted = assault.submitted || snap.trivia?.you?.assaultSubmitted == true

    fun toggle(index: Int) {
        if (submitted) return
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
    }

    fun send() {
        if (busy || submitted) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "trivia_assault",
                    selected = selected.sorted(),
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("El asalto final ya cerró.")
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, VisionRatingGold.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("El asalto final", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(assault.prompt, color = VisionTextSecondary)
            assault.items.forEachIndexed { index, item ->
                val checked = selected.contains(index)
                Surface(
                    onClick = { toggle(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (checked) VisionRatingGold.copy(alpha = 0.18f) else VisionSurfaceHigh,
                    border = BorderStroke(
                        if (checked) 2.dp else 1.dp,
                        if (checked) VisionRatingGold else Color.White.copy(alpha = 0.1f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        item.text,
                        modifier = Modifier.padding(12.dp),
                        color = if (checked) VisionRatingGold else VisionTextPrimary,
                    )
                }
            }
            Button(onClick = ::send, enabled = !busy && !submitted, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Enviando…" else if (submitted) "Esperando a los demás…" else "Sellar mi asalto")
            }
        }
    }
}

@Composable
private fun TriviaV2Reveal(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    onError: (String) -> Unit,
) {
    val me = snap.you
    val sort = snap.trivia?.sort
    val assault = snap.trivia?.assault
    Text(
        when {
            sort != null -> "Aciertos: ${((me?.points ?: 0) / 250).coerceAtLeast(0)} de ${sort.items.size}"
            assault != null -> "Asalto final: ${me?.points ?: 0} puntos"
            else -> "Respuesta correcta: ${snap.answer?.text.orEmpty()}"
        },
        style = MaterialTheme.typography.titleLarge,
        color = VisionSuccess,
        fontWeight = FontWeight.Bold,
    )
    Text(
        if ((me?.points ?: 0) > 0) "+${me?.points} puntos" else "${me?.points ?: 0} puntos",
        style = MaterialTheme.typography.titleMedium,
        color = if ((me?.points ?: 0) > 0) VisionSuccess else VisionLiveRed,
    )
    Text("Puntos totales: ${me?.score ?: 0}", color = VisionRatingGold)
    GameRevealActions(snap, playerToken, hostToken, onError)
}

@Composable
internal fun WagerV2Content(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val wager = snap.wager ?: return
    val question = snap.question
    val reveal = snap.phase == "reveal"
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    val accent = Color(GameCatalog.Wager.accent)
    val me = snap.you
    val privateStage = wager.you?.privateStage ?: wager.stage
    val privateClue = wager.you?.privateClue?.takeIf { it.isNotBlank() } ?: wager.clue
    val privateReveal = wager.you?.privatePosterReveal ?: wager.posterReveal
    val privateFactor = wager.you?.factor?.takeIf { it > 0 } ?: wager.factor
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var sideTarget by remember(snap.round) { mutableStateOf("") }

    fun buy() {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(snap.code, playerToken, snap.round, "wager_buy")
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) onError("No se pudo comprar la pista.")
        }
    }

    fun sideBet() {
        if (busy || sideTarget.isBlank()) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "wager_side_bet",
                    target = sideTarget, amount = 200,
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) onError("Esa apuesta ya no está disponible.")
        }
    }

    fun lock(option: Int) {
        if (busy || me?.answered == true) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.answer(snap.code, playerToken, snap.round, option)
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) onError("La respuesta llegó tarde.")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameStageBadge(if (reveal) "RESULTADO" else "PISTA $privateStage DE 3", accent)
            Spacer(Modifier.weight(1f))
            Text(
                if (reveal) "Resultado" else "$seconds s",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (reveal) VisionTextSecondary else accent,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking, size = 92.dp, alive = true)
            Spacer(Modifier.width(9.dp))
            NarratorBubble(
                when {
                    reveal -> "La respuesta era ${snap.answer?.text.orEmpty()}."
                    wager.you?.locked == true -> "Ya bloqueaste. Espera el resultado."
                    else -> "Pozo: ${wager.pot}. Paga x$privateFactor. Compra otra pista o bloquea tu respuesta."
                },
            )
        }
        if (!reveal) {
            LinearProgressIndicator(
                progress = { (seconds.toFloat() / snap.phaseDuration.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = VisionSurfaceHigh,
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pozo", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text("${wager.pot}", style = MaterialTheme.typography.headlineSmall, color = VisionRatingGold, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Ciega: ${wager.ante} · Pista 2/3: +${wager.buyCost} al pozo · Paga x${wager.factor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                if (wager.rollover > 0) {
                    Text("Rollover de la ronda anterior: ${wager.rollover}", color = VisionRatingGold)
                }
                if (question != null) {
                    if (question.art.isNotBlank() && (privateStage >= 3 || reveal)) {
                        AsyncImage(
                            model = question.art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(VisionSurface),
                        )
                    } else if (!reveal) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(VisionSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("PÓSTER PIXELADO · Pista privada $privateStage/3", color = accent)
                        }
                    }
                    if (privateClue.isNotBlank()) {
                        Text(privateClue, style = MaterialTheme.typography.bodyLarge, color = VisionTextSecondary)
                    }
                    if (wager.lockedStage1.isNotEmpty()) {
                        Text(
                            "Ya bloquearon en pista 1: " + wager.lockedStage1.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelMedium,
                            color = VisionBlueBright,
                        )
                    }
                    if (wager.you?.locked == true) {
                        Text(
                            "Bloqueaste en pista ${wager.you.lockedStage} · pagas x${wager.you.factor}. Espera el resultado.",
                            color = VisionSuccess,
                        )
                    } else {
                        GameOptions(
                            question = question,
                            selected = me?.selected ?: -1,
                            correct = -1,
                            reveal = false,
                            enabled = !busy && me?.answered != true,
                            onAnswer = ::lock,
                        )
                    }
                }
                if (!reveal && wager.you?.locked != true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (wager.you?.canBuy == true) {
                            OutlinedButton(onClick = ::buy, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text(if (busy) "Comprando…" else "Comprar pista privada (+${wager.buyCost})")
                            }
                        }
                    }
                }
                if (!reveal && wager.you?.canSideBet == true && wager.lockedStage1.isNotEmpty()) {
                    Text("Apuesta de confianza", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        wager.lockedStage1.filter { it.id != me?.id }.chunked(3).forEach { rowTargets ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowTargets.forEach { target ->
                                    val selected = sideTarget == target.id
                                    Surface(
                                        onClick = { sideTarget = target.id },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) accent.copy(alpha = 0.22f) else VisionSurfaceHigh,
                                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            target.name,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                repeat(3 - rowTargets.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Button(
                        onClick = ::sideBet,
                        enabled = !busy && sideTarget.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Apostando…" else "Apostar 200 a que falla") }
                }
                if (wager.you?.sideBetTargetId?.isNotBlank() == true) {
                    val targetName = wager.sideBets
                        .firstOrNull { it.id == me?.id }
                        ?.targetName
                        .orEmpty()
                        .ifBlank { wager.you?.sideBetTargetId.orEmpty() }
                    Text("Apuesta lateral: 200 contra $targetName (+400 si falla)", color = VisionRatingGold)
                }
            }
        }
        if (reveal) {
            Text("La respuesta era: ${snap.answer?.text.orEmpty()}", style = MaterialTheme.typography.titleLarge, color = VisionSuccess, fontWeight = FontWeight.Bold)
            Text("Tu ronda: ${me?.points ?: 0} puntos", color = VisionRatingGold)
            wager.sideResults.forEach { result ->
                Text(
                    "${result.name}: ${if (result.delta > 0) "+" else ""}${result.delta}",
                    color = if (result.delta > 0) VisionSuccess else VisionLiveRed,
                )
            }
            GameRevealActions(snap, playerToken, hostToken, onError)
        }
        GameScoreboard(snap, accent)
    }
}
