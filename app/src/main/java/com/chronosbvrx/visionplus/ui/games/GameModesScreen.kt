package com.chronosbvrx.visionplus.ui.games

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.games.GameAuction
import com.chronosbvrx.visionplus.data.games.GameChoice
import com.chronosbvrx.visionplus.data.games.GameDrawing
import com.chronosbvrx.visionplus.data.games.GameFibbage
import com.chronosbvrx.visionplus.data.games.GameImpostor
import com.chronosbvrx.visionplus.data.games.GamePoint
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStroke
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.NarratorEvent
import com.chronosbvrx.visionplus.data.games.NarratorMood
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

private fun buzz(context: Context, pattern: LongArray) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}

@Composable
private fun ModeNarrator(snap: GameSnapshot, text: String, mood: NarratorMood = NarratorMood.Thinking) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NarratorView(mood, size = 76.dp, alive = true)
        Spacer(Modifier.width(8.dp))
        NarratorBubble(text)
    }
}

@Composable
private fun CountdownBuzz(snap: GameSnapshot, tick: Long, snapshotAt: Long) {
    val context = LocalContext.current
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    LaunchedEffect(seconds) {
        if (seconds in setOf(5, 3, 1)) {
            buzz(context, longArrayOf(0, 25, 20, 25))
        }
    }
}

@Composable
private fun ModeTimerRow(snap: GameSnapshot, tick: Long, snapshotAt: Long, accent: Color, label: String) {
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameStageBadge(label, accent)
        Spacer(Modifier.weight(1f))
        Text(
            if (snap.phase == "reveal" || snap.phase == "auction_reveal") "Resultado" else "$seconds s",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (snap.phase == "reveal" || snap.phase == "auction_reveal") VisionTextSecondary else accent,
        )
    }
    if (snap.phase !in listOf("reveal", "auction_reveal") && snap.phaseEndsAt > 0L) {
        LinearProgressIndicator(
            progress = {
                val total = snap.phaseDuration.coerceAtLeast(1).toFloat()
                (seconds / total).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
            color = accent,
            trackColor = VisionSurfaceHigh,
        )
    }
}

@Composable
private fun VoiceNote(text: String, color: Color = VisionTextSecondary) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun ChoiceButton(
    choice: GameChoice,
    selected: Boolean,
    enabled: Boolean,
    reveal: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        reveal && choice.isReal -> VisionSuccess
        selected -> VisionBlue
        choice.isYours -> VisionSurfaceHigh
        else -> VisionSurface
    }
    val border = when {
        reveal && choice.isReal -> VisionSuccess
        selected -> VisionBlueBright
        else -> Color.White.copy(alpha = 0.12f)
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (reveal && choice.isReal) VisionBlack else VisionTextPrimary,
            disabledContainerColor = color.copy(alpha = 0.72f),
            disabledContentColor = if (reveal && choice.isReal) VisionBlack else VisionTextPrimary,
        ),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                choice.text,
                fontWeight = if (reveal && choice.isReal) FontWeight.Bold else FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (reveal && (choice.authorName.isNotBlank() || choice.votes > 0)) {
                Text(
                    buildString {
                        if (choice.isReal) append("LA VERDADERA")
                        else if (choice.authorName.isNotBlank()) append("Mentira de ${choice.authorName}")
                        if (choice.votes > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${choice.votes} ${if (choice.votes == 1) "voto" else "votos"}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reveal && choice.isReal) VisionBlack.copy(alpha = 0.8f) else VisionTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun VoteButtons(
    snap: GameSnapshot,
    choices: List<GameChoice>,
    token: String,
    onError: (String) -> Unit,
    accent: Color,
    allowAudience: Boolean,
    certain: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val me = snap.you
    val canVote = me != null && !me.voted && (allowAudience || !me.spectator) &&
        !(snap.game == "dibujo" && me.artist)
    var picked by remember(snap.round) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            ChoiceButton(
                choice = choice,
                selected = picked == choice.id,
                enabled = canVote && !busy && !choice.isYours,
                reveal = false,
                onClick = {
                    if (!canVote || busy || choice.isYours) return@ChoiceButton
                    picked = choice.id
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            GameClient.action(
                                snap.code, token, snap.round,
                                if (snap.game == "dibujo") "draw_vote" else "fibbage_vote",
                                target = choice.id, vote = certain,
                            )
                        }
                        busy = false
                        if (result is GameClient.Result.Error) onError(result.message)
                    }
                },
            )
        }
        if (me?.voted == true || (picked.isNotBlank() && !busy)) {
            Text("Voto registrado. Esperando al resto…", color = accent, style = MaterialTheme.typography.bodySmall)
        } else if (!canVote && me?.artist == true) {
            VoiceNote("El artista no vota su propio dibujo.")
        }
    }
}

// ---------------------------------------------------------------------------
// El Impostor
// ---------------------------------------------------------------------------

@Composable
fun ImpostorPhoneContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val data = snap.impostor ?: return
    val accent = Color(0xFFF43F5E)
    val me = snap.you
    val reveal = snap.phase == "reveal"
    val secret = snap.phase == "secret"
    val questions = snap.phase == "questions"
    val coartada = snap.phase == "coartada"
    val vote = snap.phase == "vote"
    val guess = snap.phase == "guess"
    val scope = rememberCoroutineScope()

    LaunchedEffect(snap.round, snap.phase) {
        when (snap.phase) {
            "secret" -> buzz(context, longArrayOf(0, 60, 40, 90))
            "questions" -> if (data.askerId == me?.id) buzz(context, longArrayOf(0, 40, 30, 40))
            "guess" -> if (data.isImpostor) buzz(context, longArrayOf(0, 120, 50, 120))
            else -> Unit
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeNarrator(
            snap,
            when {
                reveal && data.isImpostor && data.guessCorrect -> "Te descubrieron… pero adivinaste el lugar. Robo de película."
                reveal && data.isImpostor -> "Te descubrieron. Ni modo, el próximo papel es tuyo."
                reveal && data.caught -> "¡Impostor descubierto! Buen ojo, equipo."
                reveal -> "El impostor se escapó. Se llevó la gloria."
                secret && data.isImpostor -> "Eres el impostor. Finge que sabes el lugar y no te quemen."
                secret -> "Ese lugar es tu secreto. No lo digas, ni de broma."
                questions && data.askerId == me?.id -> "Te toca preguntar a ${data.targetName}. Que la pregunta no regale el lugar."
                questions && data.targetId == me?.id -> "Te están interrogando. Responde sin regalar el lugar."
                questions -> "Escucha, pregunta y detecta quién duda demasiado."
                vote -> "Es hora de señalar. Sin llorar, sin excusas."
                guess && data.isImpostor -> "Te atraparon. Adivina el lugar y roba la victoria."
                else -> "El impostor está sudando. Mantén la cara de piedra."
            },
            if (reveal) NarratorMood.Celebrate else if (data.isImpostor) NarratorMood.Oops else NarratorMood.Thinking,
        )
        CountdownBuzz(snap, tick, snapshotAt)
        ModeTimerRow(
            snap, tick, snapshotAt, accent,
            when {
                secret -> "TU PAPEL"
                questions -> "TURNO ${data.speakerIndex} DE ${data.speakerCount}"
                coartada -> "COARTADA RÁPIDA"
                vote -> "VOTACIÓN"
                guess -> "ÚLTIMA OPORTUNIDAD"
                else -> "REVELACIÓN"
            },
        )

        if ((secret || questions) && me?.spectator == true) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = VisionSurface.copy(alpha = 0.95f),
                ),
                border = BorderStroke(2.dp, accent.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("ESTÁS EN EL PÚBLICO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        "Mira la TV y escucha las preguntas. El lugar secreto no se muestra al público.",
                        textAlign = TextAlign.Center,
                        color = VisionTextSecondary,
                    )
                }
            }
        } else if (secret || questions) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (data.isImpostor) VisionLiveRed.copy(alpha = 0.18f) else VisionSurface.copy(alpha = 0.95f),
                ),
                border = BorderStroke(2.dp, if (data.isImpostor) VisionLiveRed else accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        data.category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Image(
                        painter = painterResource(
                            if (data.isImpostor) R.drawable.game_imp_role_spy else R.drawable.game_imp_role_crew
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    if (data.isImpostor) {
                        Text("ERES EL IMPOSTOR", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            "No sabes el lugar. Escucha las pistas, inventa con seguridad y no te contradigas.",
                            textAlign = TextAlign.Center,
                            color = VisionTextSecondary,
                        )
                    } else if (data.role == "accomplice") {
                        Text("ERES EL CÓMPLICE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = VisionLiveRed)
                        Text(
                            "Sabes quién es el impostor, pero no el lugar. Ayúdalo a escapar sin que te descubran.",
                            textAlign = TextAlign.Center,
                            color = VisionTextSecondary,
                        )
                        Text("Impostor: ${data.accompliceName}", color = accent)
                    } else if (data.role == "detective") {
                        Text("ERES EL DETECTIVE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = VisionBlueBright)
                        Text(
                            "Uno de estos dos lugares es el real. El otro es el señuelo del impostor.",
                            textAlign = TextAlign.Center,
                            color = VisionTextSecondary,
                        )
                        data.detectiveOptions.forEach { option ->
                            Text(option, style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
                        }
                    } else {
                        Text("EL LUGAR SECRETO ES", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
                        Text(
                            data.word,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = VisionTextPrimary,
                        )
                        if (data.hint.isNotBlank()) {
                            Text(data.hint, textAlign = TextAlign.Center, color = VisionTextSecondary)
                        }
                    }
                    Text(
                        if (data.isImpostor) "Nadie debe ver esta pantalla." else "Que no vea tu pantalla el impostor.",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                    )
                }
            }
        }

        if (questions) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.92f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PREGUNTA SUGERIDA", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(data.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${data.askerName} pregunta a ${data.targetName} · ${data.speakerIndex} de ${data.speakerCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                    if (data.askerId == me?.id) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameClient.action(
                                            snap.code, playerToken, snap.round, "impostor_advance",
                                        )
                                    }
                                    if (result is GameClient.Result.Error) onError(result.message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Siguiente pregunta") }
                    } else if (data.targetId == me?.id) {
                        Text("Te toca responder. Sé breve y convincente.", color = accent)
                    }
                }
            }
        }

        if (coartada) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Coartada rápida", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (data.isImpostor) "No sabes el lugar. ¿Qué vibra crees que tiene?"
                        else "¿Qué tan ruidoso es este lugar? Todos responden a la vez.",
                        color = VisionTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (data.coartadaSubmitted) {
                        Text("Ya respondiste. Espera la votación.", color = VisionSuccess)
                    } else {
                        listOf(
                            0 to "Silencio",
                            2 to "Tranquilo",
                            4 to "Normal",
                            6 to "Ruidoso",
                            8 to "Fiesta",
                        ).forEach { (value, label) ->
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            GameClient.action(
                                                snap.code, playerToken, snap.round,
                                                "impostor_coartada", index = value,
                                            )
                                        }
                                        if (result is GameClient.Result.Error) onError(result.message)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        }
                    }
                }
            }
        }

        if (vote) {
            Text("¿Quién crees que miente?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            snap.players.forEach { player ->
                if (player.id == me?.id) return@forEach
                Button(
                    onClick = {
                        if (me?.voted == true) return@Button
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameClient.action(snap.code, playerToken, snap.round, "impostor_vote", target = player.id)
                            }
                            if (result is GameClient.Result.Error) onError(result.message)
                        }
                    },
                    enabled = me?.voted != true && !me?.spectator.orFalse(),
                    colors = ButtonDefaults.buttonColors(containerColor = VisionSurfaceHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(playerColor(player.color), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(player.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Text("${player.score}", color = VisionRatingGold)
                    }
                }
            }
            Text(
                "Votos: ${data.votedCount} de ${data.voterCount}",
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )
        }

        if (guess) {
            if (data.isImpostor) {
                Text("Adivina el lugar para robar la victoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                data.guessOptions.forEach { option ->
                    Button(
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameClient.action(snap.code, playerToken, snap.round, "impostor_guess", text = option)
                                }
                                if (result is GameClient.Result.Error) onError(result.message)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VisionSurfaceHigh),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(option) }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Atraparon al impostor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${data.impostorName} tiene una última oportunidad de adivinar el lugar.",
                            textAlign = TextAlign.Center,
                            color = VisionTextSecondary,
                        )
                    }
                }
            }
        }

        if (reveal) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("EL IMPOSTOR ERA", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
                    Text(data.impostorName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("El lugar: ${data.word}", style = MaterialTheme.typography.titleMedium, color = VisionSuccess)
                    if (data.accompliceName.isNotBlank()) {
                        Text("Cómplice: ${data.accompliceName}", color = VisionLiveRed)
                    }
                    if (data.detectiveName.isNotBlank()) {
                        Text("Detective: ${data.detectiveName}", color = VisionBlueBright)
                    }
                    if (data.coartadaValues.isNotEmpty()) {
                        Text(
                            "Coartada: " + data.coartadaValues.joinToString(" · ") {
                                "${it.name} ${it.value + 1}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                    }
                    Text(
                        when {
                            data.caught && data.guessCorrect -> "Lo atraparon, pero adivinó el lugar. Puntos para el impostor."
                            data.caught -> "Lo atraparon y falló la adivinanza. Gloria para el equipo."
                            else -> "Nadie lo atrapó. El impostor se lleva la ronda."
                        },
                        textAlign = TextAlign.Center,
                        color = VisionTextSecondary,
                    )
                    if (data.guess.isNotBlank()) {
                        Text("Su adivinanza: ${data.guess}", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                    }
                    Text(
                        if ((me?.points ?: 0) > 0) "+${me?.points} puntos" else "Sin puntos esta ronda",
                        color = if ((me?.points ?: 0) > 0) VisionSuccess else VisionLiveRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            GameRevealActions(snap, playerToken, hostToken, onError)
        }
        GameScoreboard(snap, accent)
    }
}

// ---------------------------------------------------------------------------
// Duelo de Dibujo Ciego
// ---------------------------------------------------------------------------

private data class LocalStroke(val color: Color, val width: Float, val points: List<Offset>)

private fun _inkCostForLocal(strokes: List<LocalStroke>): Float {
    var total = 0f
    strokes.forEach { stroke ->
        val width = stroke.width.coerceAtLeast(1f)
        for (index in 1 until stroke.points.size) {
            val dx = stroke.points[index].x - stroke.points[index - 1].x
            val dy = stroke.points[index].y - stroke.points[index - 1].y
            total += kotlin.math.sqrt(dx * dx + dy * dy) * (width / 10f) / 20f
        }
    }
    return total
}

private fun encodeStrokes(strokes: List<LocalStroke>): String {
    val array = JSONArray()
    strokes.forEach { stroke ->
        val points = JSONArray()
        stroke.points.forEach { point ->
            points.put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble()))
        }
        array.put(
            JSONObject()
                .put("color", "#%06X".format(stroke.color.toArgb() and 0xFFFFFF))
                .put("width", stroke.width.toDouble())
                .put("points", points),
        )
    }
    return array.toString()
}

@Composable
internal fun VectorCanvas(strokes: List<GameStroke>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(VisionBlack)) {
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val color = runCatching { Color(android.graphics.Color.parseColor(stroke.color)) }.getOrDefault(Color.White)
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = size.width * (point.x / 1000f)
                val y = size.height * (point.y / 1000f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke.width * (size.width / 1000f) * 2.2f,
                cap = StrokeCap.Round,
            ))
        }
    }
}

@Composable
private fun DrawingCanvas(
    strokes: List<LocalStroke>,
    modifier: Modifier = Modifier,
    key: Any,
    onStart: (Offset) -> Unit,
    onPoint: (Offset) -> Unit,
    onEnd: () -> Unit,
) {
    Canvas(
        modifier = modifier
            .background(VisionBlack)
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onStart(
                            Offset(
                                offset.x / size.width.coerceAtLeast(1) * 1000f,
                                offset.y / size.height.coerceAtLeast(1) * 1000f,
                            ),
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onPoint(
                            Offset(
                                change.position.x / size.width.coerceAtLeast(1) * 1000f,
                                change.position.y / size.height.coerceAtLeast(1) * 1000f,
                            ),
                        )
                    },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                )
            },
    ) {
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = size.width * (point.x / 1000f)
                val y = size.height * (point.y / 1000f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = stroke.color, style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke.width * (size.width / 1000f) * 2.2f,
                cap = StrokeCap.Round,
            ))
        }
    }
}

@Composable
fun DibujoPhoneContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val data = snap.drawing ?: return
    val accent = Color(0xFF06B6D4)
    val v2 = snap.rulesVersion >= 2
    val me = snap.you
    val reveal = snap.phase == "reveal"
    val draw = snap.phase == "draw"
    val title = snap.phase == "title"
    val vote = snap.phase == "vote"
    val isArtist = me?.artist == true
    val scope = rememberCoroutineScope()
    var strokes by remember(snap.round) { mutableStateOf<List<LocalStroke>>(emptyList()) }
    var brushColor by remember { mutableStateOf(Color.White) }
    var brushWidth by remember { mutableFloatStateOf(6f) }
    var fakeTitle by remember(snap.round) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastSentCount by remember(snap.round) { mutableIntStateOf(0) }
    var lastSentAt by remember(snap.round) { mutableLongStateOf(0L) }
    val scrollState = rememberScrollState()

    fun sendPartial(force: Boolean = false) {
        if (!draw || !isArtist || strokes.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSentAt < 2_500L) return
        if (!force && strokes.size == lastSentCount) return
        lastSentAt = now
        lastSentCount = strokes.size
        val payload = encodeStrokes(strokes)
        scope.launch {
            withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "draw_partial",
                    strokes = payload,
                )
            }
        }
    }

    LaunchedEffect(draw, isArtist) {
        while (draw && isArtist) {
            kotlinx.coroutines.delay(2_500)
            sendPartial(force = true)
        }
    }

    LaunchedEffect(snap.round, snap.phase) {
        when {
            draw && isArtist -> buzz(context, longArrayOf(0, 50, 30, 50))
            title -> buzz(context, longArrayOf(0, 35))
            vote -> buzz(context, longArrayOf(0, 40, 25, 40))
            reveal -> buzz(context, longArrayOf(0, 80))
            else -> Unit
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(
            scrollState,
            enabled = !(draw && isArtist),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeNarrator(
            snap,
            when {
                reveal -> "El título real era «${data.prompt}». Veamos quién mordió el anzuelo."
                draw && isArtist -> "Dibuja la frase absurda. Sin letras, sin trampas y sin pena."
                draw -> "El artista está dibujando. Calienta la imaginación."
                title && me?.spectator == true -> "El público mira. Los jugadores inventan títulos."
                title -> "Inventa un título falso que suene verdadero."
                vote -> "Vota el título que creas real. Tu mentira no se vota sola."
                else -> "El dibujo está en la tele."
            },
            if (reveal) NarratorMood.Celebrate else if (draw && isArtist) NarratorMood.Happy else NarratorMood.Thinking,
        )
        CountdownBuzz(snap, tick, snapshotAt)
        ModeTimerRow(
            snap, tick, snapshotAt, accent,
            when {
                draw -> if (isArtist) "A DIBUJAR" else "DIBUJANDO"
                title -> "TÍTULOS FALSOS"
                vote -> "VOTACIÓN"
                else -> "REVELACIÓN"
            },
        )

        if (draw && isArtist) {
            if (data.promptOptions.size >= 2 && strokes.isEmpty()) {
                Text("ELIGE TU CONSIGNA", style = MaterialTheme.typography.labelMedium, color = accent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.promptOptions.take(2).forEachIndexed { index, option ->
                        Surface(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameClient.action(
                                            snap.code, playerToken, snap.round,
                                            "draw_pick_prompt", index = index,
                                        )
                                    }
                                    if (result is GameClient.Result.Error) onError(result.message)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (data.prompt == option) accent.copy(alpha = 0.22f) else VisionSurfaceHigh,
                            border = BorderStroke(
                                1.dp,
                                if (data.prompt == option) accent else Color.White.copy(alpha = 0.12f),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "${('A' + index)}. $option",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = VisionTextPrimary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Text(
                    "Toca una o empieza a dibujar la opción A.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DIBUJA ESTO", style = MaterialTheme.typography.labelMedium, color = accent)
                    Text(
                        data.prompt,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (data.inkLimit > 0f) {
                val estimated = runCatching { _inkCostForLocal(strokes) }.getOrDefault(0f)
                Text(
                    "Tinta: ${estimated.toInt()} / ${data.inkLimit.toInt()} · bono si dibujas con ≤50 %",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (estimated <= data.inkLimit * 0.5f) VisionSuccess else VisionLiveRed,
                )
                LinearProgressIndicator(
                    progress = { (estimated / data.inkLimit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (estimated <= data.inkLimit * 0.5f) VisionSuccess else VisionLiveRed,
                    trackColor = VisionSurfaceHigh,
                )
            }
            DrawingCanvas(
                strokes = strokes,
                modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)),
                key = brushColor to brushWidth,
                onStart = { point ->
                    strokes = strokes + LocalStroke(brushColor, brushWidth, listOf(point))
                },
                onPoint = { point ->
                    if (strokes.isNotEmpty()) {
                        val last = strokes.last()
                        strokes = strokes.dropLast(1) + last.copy(points = last.points + point)
                    }
                },
                onEnd = { sendPartial(force = true) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    Color.White, Color(0xFFF43F5E), Color(0xFFF59E0B),
                    Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFF8B5CF6),
                ).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(if (brushColor == color) 30.dp else 24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (brushColor == color) Modifier
                                    .border(2.dp, VisionTextPrimary, CircleShape) else Modifier,
                            ),
                    )
                    Spacer(Modifier.width(2.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4f, 8f, 14f).forEach { width ->
                    Surface(
                        onClick = { brushWidth = width },
                        shape = RoundedCornerShape(50),
                        color = if (brushWidth == width) accent.copy(alpha = 0.25f) else VisionSurfaceHigh,
                    ) {
                        Text(
                            when (width.toInt()) { 4 -> "Fino"; 8 -> "Medio"; else -> "Grueso" },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Deshacer") }
                TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Borrar") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameClient.action(
                                    snap.code, playerToken, snap.round, "draw_submit",
                                    strokes = encodeStrokes(strokes),
                                )
                            }
                            busy = false
                            if (result is GameClient.Result.Error) onError(result.message)
                            else if (result is GameClient.Result.Ok && !result.value) {
                                onError("El dibujo supera la tinta disponible.")
                            }
                        }
                    },
                    enabled = !busy && strokes.isNotEmpty(),
                ) { Text(if (busy) "Enviando…" else "Enviar dibujo") }
            }
        } else if (draw) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.92f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${data.artistName} está dibujando", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("El lienzo aparecerá en la tele cuando termine.", color = VisionTextSecondary, textAlign = TextAlign.Center)
                    Text("Tú prepara tu mejor título falso.", color = accent)
                }
            }
            if (v2 && !isArtist && me?.spectator != true) {
                var early by remember(snap.round) { mutableStateOf(data.earlyGuess) }
                Text("Adivinanza temprana", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (data.earlyAttempts >= 2) "Ya usaste tus 2 intentos. Si le atinaste, cobras +400."
                    else "Si crees saberlo, escribe una palabra clave. +400. Tienes 2 intentos.",
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (data.earlyAttempts < 2) {
                    OutlinedTextField(
                        value = early,
                        onValueChange = { if (it.length <= 40) early = it },
                        label = { Text("Tu corazonada") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (busy || early.trim().length < 3) return@Button
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameClient.action(
                                        snap.code, playerToken, snap.round,
                                        "draw_early_guess", text = early.trim(),
                                    )
                                }
                                busy = false
                                if (result is GameClient.Result.Error) onError(result.message)
                                else if (result is GameClient.Result.Ok && !result.value) {
                                    onError("El dibujo aún no empezó o la pista no coincide.")
                                }
                            }
                        },
                        enabled = !busy && early.trim().length >= 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Enviando…" else "Mandar corazonada (+400)") }
                }
            }
        }

        if (title && !isArtist && me?.spectator != true) {
            if (me?.submitted == true) {
                Text("Título enviado. Esperando a los demás…", color = accent)
            } else {
                OutlinedTextField(
                    value = fakeTitle,
                    onValueChange = { if (it.length <= 90) fakeTitle = it },
                    label = { Text("Tu título falso") },
                    singleLine = false,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (busy || fakeTitle.trim().length < 3) return@Button
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameClient.action(snap.code, playerToken, snap.round, "draw_title", text = fakeTitle.trim())
                            }
                            busy = false
                            if (result is GameClient.Result.Error) onError(result.message)
                        }
                    },
                    enabled = !busy && fakeTitle.trim().length >= 3,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Enviando…" else "Mandar mentira") }
            }
        }
        if (title && isArtist) {
            Text("Tu dibujo ya está en la tele. Ahora disfruta cómo mienten los demás.", color = accent)
        }
        if (title && me?.spectator == true) {
            Text("Estás en el público: mira el lienzo y ríete de las mentiras.", color = accent)
        }

        if (vote && data.options.isNotEmpty()) {
            Text("¿Cuál es el título real?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            VoteButtons(snap, data.options, playerToken, onError, accent, allowAudience = true)
            if (v2) {
                Text("Fichas de crítico (una de cada una)", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
                data.options.forEach { choice ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(choice.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameClient.action(
                                            snap.code, playerToken, snap.round, "draw_critic",
                                            target = choice.id, text = "bomb",
                                        )
                                    }
                                    if (result is GameClient.Result.Error) onError(result.message)
                                }
                            },
                            enabled = data.criticBomb.isBlank() && !busy,
                        ) { Text("Basura -200") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameClient.action(
                                            snap.code, playerToken, snap.round, "draw_critic",
                                            target = choice.id, text = "crown",
                                        )
                                    }
                                    if (result is GameClient.Result.Error) onError(result.message)
                                }
                            },
                            enabled = data.criticCrown.isBlank() && !busy,
                        ) { Text("Corona +300") }
                    }
                }
            }
        }

        if (reveal) {
            if (data.prompt.isNotBlank()) {
                Text("La frase real: ${data.prompt}", style = MaterialTheme.typography.titleLarge, color = VisionSuccess, fontWeight = FontWeight.Bold)
            }
            data.options.forEach { choice ->
                ChoiceButton(choice = choice, selected = false, enabled = false, reveal = true, onClick = {})
            }
            Text(
                if ((me?.points ?: 0) > 0) "+${me?.points} puntos" else "Sin puntos esta ronda",
                color = if ((me?.points ?: 0) > 0) VisionSuccess else VisionLiveRed,
                fontWeight = FontWeight.Bold,
            )
            GameRevealActions(snap, playerToken, hostToken, onError)
        }
        if (data.audienceVotes > 0) {
            Text(
                if (data.audienceBonusPlayerId.isNotBlank()) {
                    "Favorito del público: " +
                        (snap.players.firstOrNull { it.id == data.audienceBonusPlayerId }?.name ?: "la sala") +
                        " (+300)"
                } else {
                    "El público votó ${data.audienceVotes} veces."
                },
                color = VisionRatingGold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        GameScoreboard(snap, accent)
    }
}

// ---------------------------------------------------------------------------
// Subasta Ciega
// ---------------------------------------------------------------------------

@Composable
fun SubastaPhoneContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val data = snap.auction ?: return
    val accent = Color(0xFFF59E0B)
    val me = snap.you
    val reveal = snap.phase == "auction_reveal"
    val scope = rememberCoroutineScope()
    var bid by remember(snap.round) { mutableIntStateOf(data.yourBid.takeIf { it >= 0 } ?: 0) }
    var busy by remember { mutableStateOf(false) }
    val coins = data.coins

    LaunchedEffect(snap.round, snap.phase) {
        if (snap.phase == "auction_reveal") buzz(context, longArrayOf(0, 80, 40, 120))
        else buzz(context, longArrayOf(0, 30))
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeNarrator(
            snap,
            when {
                reveal && data.tie -> "¡Empate en la cima! Se cancelan y la carta pasa al siguiente. Así se juega."
                reveal && data.winnerName.isNotBlank() -> "¡Martillo! ${data.winnerName} se lleva la carta por ${data.price} monedas."
                reveal -> "Nadie pujó. La carta se queda en el mercado negro."
                data.mystery -> "Carta misteriosa. Puede ser gloria o una tomadura de pelo."
                else -> "La carta está sobre la mesa. ¿Cuánto vale para ti?"
            },
            if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
        )
        CountdownBuzz(snap, tick, snapshotAt)
        ModeTimerRow(
            snap, tick, snapshotAt, accent,
            if (reveal) "MARTILLO" else "PUJA SECRETA",
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(
                    painter = painterResource(auctionCardArtRes(data.effect, data.mystery)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(96.dp)
                        .height(144.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Text("LOTE ${snap.round} DE ${snap.rounds}", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(
                    data.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(data.description, textAlign = TextAlign.Center, color = VisionTextSecondary)
                Text(
                    when {
                        data.mystery && !reveal -> "Contenido: MISTERIO"
                        data.effect == "points" -> "Efecto: +${data.value} puntos"
                        data.effect == "coins" -> "Efecto: +${data.value} monedas"
                        data.effect == "steal" -> "Efecto: roba 25% de los puntos al líder"
                        data.effect == "double_next" -> "Efecto: duplica tu próximo lote de puntos"
                        data.effect == "shield" -> "Efecto: escudo contra robos"
                        else -> "Efecto: sorpresa"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (data.secondPays && !reveal) {
            Text(
                "LOTE MALDITO: el segundo lugar también pierde su puja.",
                color = VisionLiveRed,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (data.informant && !reveal) {
            Text(
                "Eres informante. Carta secreta: ${data.secretEffect} ${data.secretValue}",
                color = VisionBlueBright,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!reveal) {
            Text("Tus monedas: $coins", color = VisionRatingGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Puja: $bid", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 50, 100, 250, 500).forEach { amount ->
                    Surface(
                        onClick = { if (amount <= coins) bid = amount },
                        shape = RoundedCornerShape(50),
                        color = if (bid == amount) accent.copy(alpha = 0.25f) else VisionSurfaceHigh,
                        border = BorderStroke(1.dp, if (bid == amount) accent else Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text("$amount", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = VisionTextPrimary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, 250).forEach { step ->
                    Surface(
                        onClick = { bid = (bid + step).coerceAtMost((coins / 50) * 50) },
                        shape = RoundedCornerShape(50), color = VisionSurfaceHigh,
                    ) { Text("+$step", Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                }
                Surface(
                    onClick = { bid = (coins / 50) * 50 },
                    shape = RoundedCornerShape(50), color = VisionSurfaceHigh,
                ) { Text("Todo", Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
            Button(
                onClick = {
                    if (busy || data.youBid) return@Button
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            GameClient.action(snap.code, playerToken, snap.round, "auction_bid", bid = bid)
                        }
                        busy = false
                        if (result is GameClient.Result.Error) onError(result.message)
                    }
                },
                enabled = !busy && !data.youBid && me?.spectator != true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Pujando…" else if (data.youBid) "Puja registrada: ${data.yourBid}" else "Pujar $bid monedas") }
            if (snap.rulesVersion >= 2 && !data.ghostUsed && data.ghostBidderId.isBlank()) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameClient.action(snap.code, playerToken, snap.round, "auction_ghost")
                            }
                            if (result is GameClient.Result.Error) onError(result.message)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Falsa alarma: asustar a la sala (1 uso)") }
            }
            Text(
                "Pujas recibidas: ${data.bidsCount} de ${data.bidCount}",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            if (me?.spectator == true) Text("Estás en el público: mira las pujas y disfruta.", color = accent)
        } else {
            Text(
                if (data.winnerName.isBlank()) "Sin ganador" else "Ganador: ${data.winnerName} · ${data.price} monedas",
                style = MaterialTheme.typography.titleLarge,
                color = if (data.winnerName.isBlank()) VisionTextSecondary else VisionSuccess,
                fontWeight = FontWeight.Bold,
            )
            if (data.tie) Text("Empate cancelado: ${data.cancelled.joinToString()}", color = VisionLiveRed)
            if (data.applied.isNotBlank()) Text(data.applied, color = accent, style = MaterialTheme.typography.titleMedium)
            data.bids.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    Text(row.name, Modifier.weight(1f))
                    Text("${row.bid} monedas", color = VisionRatingGold)
                }
            }
            if (snap.phase == "auction_reveal") {
                GameRevealActions(snap, playerToken, hostToken, onError)
            }
        }
        GameScoreboard(snap, accent)
    }
}

// ---------------------------------------------------------------------------
// Fibbage / Falso Conocimiento
// ---------------------------------------------------------------------------

@Composable
fun FibbagePhoneContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val data = snap.fibbage ?: return
    val accent = Color(0xFF10B981)
    val v2 = snap.rulesVersion >= 2
    val me = snap.you
    val reveal = snap.phase == "reveal"
    val write = snap.phase == "write"
    val vote = snap.phase == "vote"
    val scope = rememberCoroutineScope()
    var lie by remember(snap.round) { mutableStateOf("") }
    var poison by remember(snap.round) { mutableStateOf(false) }
    var certain by remember(snap.round) { mutableStateOf(false) }
    var hunterOption by remember(snap.round) { mutableStateOf("") }
    var hunterTarget by remember(snap.round) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val hunterUsed = data.hunterGuess.isNotBlank()

    LaunchedEffect(data.scanUsed) {
        if (data.scanUsed) certain = false
    }

    LaunchedEffect(snap.round, snap.phase) {
        when (snap.phase) {
            "write" -> buzz(context, longArrayOf(0, 40))
            "vote" -> buzz(context, longArrayOf(0, 40, 25, 40))
            "reveal" -> buzz(context, longArrayOf(0, 90))
            else -> Unit
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeNarrator(
            snap,
            when {
                reveal -> if (data.answer.isNotBlank()) "La verdad era «${data.answer}». ¿Quién mordió?" else "Se acabó la ronda."
                write && me?.spectator == true -> "El público mira. Los jugadores inventan."
                write -> "Inventa una mentira creíble. Si nadie sabe la verdad, tu mentira es oro."
                vote -> "Vota la respuesta que creas real. La tuya no cuenta."
                else -> "Un dato raro de cine y series."
            },
            if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
        )
        CountdownBuzz(snap, tick, snapshotAt)
        ModeTimerRow(
            snap, tick, snapshotAt, accent,
            when {
                write -> "INVENTA"
                vote -> "VOTACIÓN"
                else -> "REVELACIÓN"
            },
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(data.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent)
                Text(data.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (data.hint.isNotBlank() && !reveal) {
                    Text("Pista: ${data.hint}", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                }
            }
        }

        if (write && me?.spectator != true) {
            if (me?.submitted == true) {
                Text("Mentira enviada. Esperando a los demás…", color = accent)
            } else {
                OutlinedTextField(
                    value = lie,
                    onValueChange = { if (it.length <= 90) lie = it },
                    label = { Text("Tu mentira convincente") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (v2) {
                    OutlinedButton(
                        onClick = { poison = !poison },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (poison) "Veneno activado: +300 si muerden (-150)" else "Envenenar mi mentira (-150)") }
                }
                Button(
                    onClick = {
                        if (busy || lie.trim().length < 2) return@Button
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameClient.action(
                                    snap.code, playerToken, snap.round, "fibbage_lie",
                                    text = lie.trim(), amount = if (poison) 200 else 0,
                                )
                            }
                            busy = false
                            if (result is GameClient.Result.Error) onError(result.message)
                        }
                    },
                    enabled = !busy && lie.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Enviando…" else "Mandar mentira") }
            }
            Text("Mentiras enviadas: ${data.submittedCount} de ${data.voterCount}", color = VisionTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (write && me?.spectator == true) {
            Text("Estás en el público: espera las respuestas y vota en la siguiente fase.", color = accent)
        }

        if (vote && data.options.isNotEmpty()) {
            Text("¿Cuál es la verdad?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (v2 && !data.scanUsed) {
                OutlinedButton(
                    onClick = { certain = !certain },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (certain) "Manos al fuego: x1.5 si aciertas, -300 si fallas" else "Voto normal")
                }
            }
            VoteButtons(
                snap, data.options.filterNot { it.id in data.scanned },
                playerToken, onError, accent, allowAudience = true, certain = certain,
            )
            if (v2 && !data.scanUsed && me?.spectator != true) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameClient.action(snap.code, playerToken, snap.round, "fibbage_scan")
                            }
                            if (result is GameClient.Result.Error) onError(result.message)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Escáner: tachar mentiras (-300 si fallas)") }
            }
            if (v2 && !hunterUsed && me?.spectator != true) {
                Text(
                    "Cazador: adivina quién escribió una mentira (+300)",
                    style = MaterialTheme.typography.labelLarge,
                    color = VisionRatingGold,
                )
                val lieOptions = data.options.filter {
                    !it.isReal && !it.isYours && !it.house
                }
                lieOptions.forEach { option ->
                    Surface(
                        onClick = { hunterOption = option.id },
                        shape = RoundedCornerShape(8.dp),
                        color = if (hunterOption == option.id) accent.copy(alpha = 0.20f) else VisionSurfaceHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            option.text,
                            modifier = Modifier.padding(9.dp),
                            color = VisionTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (hunterOption.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        snap.players.filter { it.id != me?.id }.chunked(4).forEach { rowSuspects ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowSuspects.forEach { suspect ->
                                    Surface(
                                        onClick = { hunterTarget = suspect.id },
                                        shape = RoundedCornerShape(50),
                                        color = if (hunterTarget == suspect.id) accent.copy(alpha = 0.20f) else VisionSurfaceHigh,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            suspect.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                            color = VisionTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                repeat(4 - rowSuspects.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (busy || hunterOption.isBlank() || hunterTarget.isBlank()) return@Button
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameClient.action(
                                        snap.code, playerToken, snap.round, "fibbage_hunter",
                                        text = hunterOption, target = hunterTarget,
                                    )
                                }
                                busy = false
                                if (result is GameClient.Result.Error) onError(result.message)
                                else if (result is GameClient.Result.Ok && !result.value) {
                                    onError("Ese cazador ya no está disponible.")
                                }
                            }
                        },
                        enabled = !busy && hunterOption.isNotBlank() && hunterTarget.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Enviando…" else "Marcar cazador") }
                }
            } else if (v2 && hunterUsed) {
                Text("Cazador enviado. Espera la revelación.", color = accent, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (reveal) {
            Text("La respuesta real: ${data.answer}", style = MaterialTheme.typography.titleLarge, color = VisionSuccess, fontWeight = FontWeight.Bold)
            data.options.forEach { choice ->
                ChoiceButton(choice = choice, selected = false, enabled = false, reveal = true, onClick = {})
            }
            Text(
                if ((me?.points ?: 0) > 0) "+${me?.points} puntos" else "Sin puntos esta ronda",
                color = if ((me?.points ?: 0) > 0) VisionSuccess else VisionLiveRed,
                fontWeight = FontWeight.Bold,
            )
            GameRevealActions(snap, playerToken, hostToken, onError)
        }
        if (data.audienceVotes > 0) {
            Text(
                if (data.audienceBonusPlayerId.isNotBlank()) {
                    "Favorito del público: " +
                        (snap.players.firstOrNull { it.id == data.audienceBonusPlayerId }?.name ?: "la sala") +
                        " (+300)"
                } else {
                    "El público votó ${data.audienceVotes} veces."
                },
                color = VisionRatingGold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        GameScoreboard(snap, accent)
    }
}

private fun Boolean?.orFalse(): Boolean = this == true
