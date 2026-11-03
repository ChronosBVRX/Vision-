package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.games.FiestaCatalog
import com.chronosbvrx.visionplus.data.games.GameCatalog
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.GameCodes
import com.chronosbvrx.visionplus.data.games.GameInfo
import com.chronosbvrx.visionplus.data.games.GameNarrator
import com.chronosbvrx.visionplus.data.games.GamePlayer
import com.chronosbvrx.visionplus.data.games.GameQuestion
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStore
import com.chronosbvrx.visionplus.data.games.GameWager
import com.chronosbvrx.visionplus.data.games.NarratorAudio
import com.chronosbvrx.visionplus.data.games.NarratorEvent
import com.chronosbvrx.visionplus.data.games.NarratorMood
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sala móvil de Vision+ Play: lobby, turno/pregunta/pistas, reveal y podio. */
@Composable
fun GameRoomScreen(
    code: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val normalized = GameCodes.normalize(code) ?: code.trim().uppercase()
    var playerToken by remember { mutableStateOf(GameStore.playerToken(context, normalized)) }
    var hostToken by remember { mutableStateOf(GameStore.hostToken(context, normalized)) }
    var snapshot by remember { mutableStateOf<GameSnapshot?>(null) }
    var snapshotAt by remember { mutableLongStateOf(0L) }
    var since by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf("") }
    var closed by remember { mutableStateOf(false) }
    var lastAudio by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current

    GameFullscreenEffect()
    DisposableEffect(Unit) {
        onDispose { NarratorAudio.stop() }
    }

    val token = playerToken
    if (token == null) {
        Box(Modifier.fillMaxSize().background(VisionBlack)) {
            JoinRoomGate(
                code = normalized,
                contentPadding = contentPadding,
                onBack = onBack,
                onJoined = { fresh ->
                    GameStore.savePlayer(context, normalized, fresh)
                    GameStore.remember(context, normalized)
                    playerToken = fresh
                },
            )
        }
        return
    }

    LaunchedEffect(token) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                when (val result = withContext(Dispatchers.IO) {
                    GameClient.state(normalized, since, token)
                }) {
                    is GameClient.StateResult.Snapshot -> {
                        snapshot = result.value
                        snapshotAt = System.currentTimeMillis()
                        since = result.value.rev
                        if (result.value.phase == "closed") closed = true
                        error = ""
                    }
                    GameClient.StateResult.Unchanged -> Unit
                    is GameClient.StateResult.Error -> {
                        if (result.code == 404 || result.code == 410) closed = true
                        else if (result.code != 0) error = result.message
                    }
                }
                delay(
                    if (snapshot?.phase in listOf(
                            "question", "clue", "attack", "sort", "assault", "punishment",
                        )
                    ) 700 else 1_000,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(snapshot?.phase, snapshot?.round, snapshot?.game) {
        val snap = snapshot ?: return@LaunchedEffect
        if (snap.isFiesta) return@LaunchedEffect
        val event = when (snap.phase) {
            "countdown" -> NarratorEvent.Started
            "director" -> NarratorEvent.CharadaTurn
            "turn" -> NarratorEvent.CharadaTurn
            "wager" -> NarratorEvent.WagerStart
            "clue" -> NarratorEvent.WagerClue
            "attack" -> NarratorEvent.Started
            "punishment" -> NarratorEvent.Wrong
            "punishment_reveal" -> NarratorEvent.Reveal
            "sort" -> NarratorEvent.Reveal
            "assault" -> NarratorEvent.FinalRoundDouble
            "secret" -> NarratorEvent.ImpostorSecret
            "questions" -> null
            "coartada" -> NarratorEvent.ImpostorVote
            "draw" -> NarratorEvent.DrawStart
            "title" -> NarratorEvent.DrawVote
            "write" -> NarratorEvent.FibbageWrite
            "auction" -> NarratorEvent.AuctionStart
            "auction_reveal" -> NarratorEvent.AuctionReveal
            "vote" -> when (snap.game) {
                "impostor" -> NarratorEvent.ImpostorVote
                "dibujo" -> NarratorEvent.DrawVote
                "fibbage" -> NarratorEvent.FibbageVote
                else -> NarratorEvent.Reveal
            }
            "guess" -> NarratorEvent.ImpostorCaught
            "reveal" -> when (snap.game) {
                "charadas" -> NarratorEvent.CharadaReveal
                "wager" -> when {
                    (snap.you?.points ?: 0) > 0 -> NarratorEvent.WagerWin
                    (snap.you?.points ?: 0) < 0 -> NarratorEvent.WagerLose
                    (snap.you?.wagerDelta ?: 0) > 0 -> NarratorEvent.WagerWin
                    (snap.you?.wagerDelta ?: 0) < 0 -> NarratorEvent.WagerLose
                    else -> NarratorEvent.WagerReveal
                }
                else -> GameNarrator.revealEvent(
                    answered = snap.you?.answered == true,
                    correct = snap.you?.correct == true,
                )
            }
            "podium" -> NarratorEvent.Podium
            else -> null
        } ?: return@LaunchedEffect
        val key = snap.narrKey()
        if (key != lastAudio) {
            lastAudio = key
            playNarratorCue(context, event, key)
        }
    }

    val snap = snapshot
    val accent = Color((snap?.info ?: GameCatalog.Trivia).accent)
    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        if (snap?.isFiesta == true) FiestaPhoneBackdrop(fiestaLook(snap.game))
        else GameStageBackground(accent)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GameHud(snap, normalized, accent, onBack, onHome)
            when {
                closed -> ClosedContent(onHome)
                snap == null -> LoadingRoom(error)
                snap.phase == "lobby" -> LobbyContent(
                    snap = snap,
                    hostToken = hostToken ?: token,
                    playerToken = token,
                    onError = { error = it },
                )
                snap.phase == "countdown" -> CountdownContent(snap, tick, snapshotAt)
                FiestaCatalog.isFiesta(snap.game) &&
                    snap.phase !in listOf("lobby", "countdown", "podium") ->
                    FiestaWorldPhone(
                        snap = snap,
                        info = snap.fiesta!!,
                        playerToken = token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "charadas" && snap.phase in listOf("director", "turn", "reveal") ->
                    CharadaContent(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "wager" && snap.rulesVersion >= 2 &&
                    (snap.phase == "clue" || snap.phase == "reveal") ->
                    WagerV2Content(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "wager" && (snap.phase == "wager" || snap.phase == "clue" || snap.phase == "reveal") ->
                    WagerContent(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "impostor" && snap.phase in listOf("secret", "questions", "coartada", "vote", "guess", "reveal") ->
                    ImpostorPhoneContent(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "dibujo" && snap.phase in listOf("draw", "title", "vote", "reveal") ->
                    DibujoPhoneContent(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "subasta" && snap.phase in listOf("auction", "auction_reveal") ->
                    SubastaPhoneContent(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "fibbage" && snap.phase in listOf("write", "vote", "reveal") ->
                    FibbagePhoneContent(
                        snap = snap,
                        playerToken = token,
                        hostToken = hostToken ?: token,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onError = { error = it },
                    )
                snap.game == "trivia" && snap.rulesVersion >= 2 &&
                    snap.phase in listOf(
                        "attack", "question", "punishment", "punishment_reveal",
                        "sort", "assault", "reveal",
                    ) -> TriviaV2Content(
                    snap = snap,
                    playerToken = token,
                    hostToken = hostToken ?: token,
                    tick = tick,
                    snapshotAt = snapshotAt,
                    onError = { error = it },
                )
                snap.phase == "question" || snap.phase == "reveal" -> QuestionContent(
                    snap = snap,
                    playerToken = token,
                    hostToken = hostToken ?: token,
                    tick = tick,
                    snapshotAt = snapshotAt,
                    onError = { error = it },
                )
                snap.phase == "podium" -> PodiumContent(snap, onHome, onBack)
                else -> ClosedContent(onHome)
            }
            if (error.isNotBlank() && !closed) {
                Text(
                    error,
                    color = VisionLiveRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GameHud(
    snapshot: GameSnapshot?,
    code: String,
    accent: Color,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val info = snapshot?.info ?: GameCatalog.Trivia
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(VisionIcons.Back, contentDescription = "Volver")
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                GameStageBadge("EN VIVO", VisionLiveRed)
            }
            Text(
                if (snapshot == null) "Sala ${GameCodes.format(code)}" else
                    "Sala ${snapshot.displayCode} · Ronda ${snapshot.round} de ${snapshot.rounds}" +
                        if (snapshot.audience > 0) " · ${snapshot.audience} público" else "",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
        }
        if (snapshot?.you != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${snapshot.you.score}", style = MaterialTheme.typography.titleMedium, color = VisionRatingGold)
                Text("puntos", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            }
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = onHome) { Text("Salir") }
    }
    GameHairline(color = accent)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RoomHeader(
    snapshot: GameSnapshot?,
    code: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(VisionIcons.Back, contentDescription = "Volver")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Sala ${snapshot?.displayCode ?: GameCodes.format(code)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (snapshot != null && snapshot.phase != "lobby") {
                Text(
                    "Ronda ${snapshot.round} de ${snapshot.rounds} · ${snapshot.players.size} jugadores",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
        }
        TextButton(onClick = onHome) { Text("Inicio") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LoadingRoom(error: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NarratorView(NarratorMood.Idle, size = 140.dp, animated = true)
        Spacer(Modifier.height(12.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text(if (error.isBlank()) "Conectando con la sala…" else error, color = VisionTextSecondary)
    }
}

@Composable
private fun LobbyContent(
    snap: GameSnapshot,
    hostToken: String,
    playerToken: String,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val isHost = snap.isHost(snap.you?.id)
    val info = snap.info
    val fiestaLook = if (snap.isFiesta) fiestaLook(snap.game) else null
    val accent = fiestaLook?.accent ?: Color(info.accent)
    val soloTest = snap.fiesta?.soloTest == true
    val missing = if (soloTest) 0 else (info.minPlayers - snap.players.size).coerceAtLeast(0)
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (snap.isFiesta) {
                FiestaHostBubble(
                    gameId = snap.game,
                    text = snap.fiesta?.hostCue?.text.orEmpty(),
                    modifier = Modifier.weight(1f),
                )
            } else {
                NarratorView(NarratorMood.Welcome, size = 116.dp, animated = true, alive = true)
                Spacer(Modifier.width(10.dp))
                NarratorBubble(rememberNarratorText(NarratorEvent.RoomCreated, "room:${snap.code}"))
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(info.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent)
                Text(
                    snap.displayCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp,
                    color = VisionBlueBright,
                )
                Text(
                    "${snap.players.size} de ${info.maxPlayers} jugadores",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.timeLabel, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
                    Text("·", color = VisionTextSecondary)
                    Text(info.roundsLabel, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
                    Text("·", color = VisionTextSecondary)
                    Text(info.playersLabel, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
                }
            }
        }
        Text(
            fiestaLook?.tagline ?: info.description,
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
        )
        if (fiestaLook != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FiestaCurrencyChip(fiestaLook, snap.fiesta?.coins ?: 0)
                FiestaTag(fiestaLook.shop.uppercase(), fiestaLook.accent2)
            }
        }
        snap.players.forEach { player -> PlayerRow(player, snap.you?.id) }
        if (soloTest) {
            Text(
                "Modo prueba: puedes empezar y votar con un solo jugador.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionRatingGold,
            )
        } else if (missing > 0) {
            Text(
                "Faltan $missing ${if (missing == 1) "jugador" else "jugadores"} para empezar. " +
                    "Mínimo ${info.minPlayers}.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionRatingGold,
            )
        }
        if (isHost) {
            Text(
                "Eres el anfitrión: el primero que entró. Inicia cuando estén todos.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionBlueBright,
            )
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { GameClient.start(snap.code, hostToken) }
                        busy = false
                        if (result is GameClient.Result.Error) onError(result.message)
                    }
                },
                enabled = !busy && snap.players.isNotEmpty() && missing == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Empezando…" else "Iniciar partida")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Esperando al anfitrión…", color = VisionTextSecondary)
            }
        }
        Text(
            when (info.id) {
                "charadas" -> "El título solo aparece en el teléfono del actor; la tele y los demás no lo ven."
                "impostor" -> "El lugar secreto vive en tu teléfono. El impostor no lo sabe y tiene que fingir."
                "dibujo" -> "El artista dibuja en su teléfono; la tele muestra el lienzo y todos mienten con el título."
                "subasta" -> "Las pujas son secretas. La tele solo revela el martillo y los empates."
                "fibbage" -> "La respuesta verdadera nunca viaja al teléfono hasta el reveal."
                "wager" -> "La respuesta correcta nunca viaja al teléfono antes del reveal."
                "ultima_palabra" -> "Escribe en tu teléfono; la tele muestra las respuestas y la sala vota."
                "vox_populi" -> "Adivina la respuesta más popular; la tele revela lo que dijo la gente."
                "fuera_de_epoca" -> "Elige el año en tu teléfono; la tele revela la línea temporal."
                "texto_roto" -> "Escribe tu línea; la tele arma el chat y la sala vota."
                "franela_fest" -> "Dibuja y escribe el eslogan en tu teléfono; la tele muestra las camisetas."
                "el_tasador" -> "Escribe tu pitch y su valor; la tele revela el precio real."
                else -> "La respuesta correcta nunca viaja al teléfono antes del reveal."
            },
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun CountdownContent(snap: GameSnapshot, tick: Long, snapshotAt: Long) {
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    val info = snap.info
    val accent = Color(info.accent)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (snap.isFiesta) {
            FiestaHostAvatar(snap.game, 150.dp)
            FiestaHostBubble(
                gameId = snap.game,
                text = snap.fiesta?.hostCue?.text.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            NarratorView(NarratorMood.Happy, size = 150.dp, alive = true)
            NarratorBubble(rememberNarratorText(NarratorEvent.Started, snap.narrKey()))
        }
        Text(
            if (seconds > 0) "$seconds" else "Ya",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            when (info.id) {
                "charadas" -> "El primer actor ya tiene su título."
                "impostor" -> "Reparte los teléfonos. Uno de ustedes miente."
                "dibujo" -> "El primer dibujante ya tiene su frase absurda."
                "subasta" -> "Abre la cartera: la primera carta está por salir."
                "fibbage" -> "Prepárate para inventar una mentira creíble."
                "wager" -> "Prepárate para apostar."
                "ultima_palabra" -> "La primera frase está por salir."
                "vox_populi" -> "La primera encuesta está por salir."
                "fuera_de_epoca" -> "El primer evento está por salir."
                "texto_roto" -> "El primer chat está por abrir."
                "franela_fest" -> "El primer lienzo está por salir."
                "el_tasador" -> "El primer objeto está por salir."
                else -> "La primera pregunta está por salir."
            },
            color = VisionTextSecondary,
        )
    }
}

@Composable
private fun QuestionContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val question = snap.question ?: return
    val reveal = snap.phase == "reveal"
    val correct = snap.answer?.option ?: -1
    val answered = snap.you?.answered == true
    var picked by remember(snap.round) { mutableIntStateOf(snap.you?.selected ?: -1) }
    var busy by remember { mutableStateOf(false) }
    var reported by remember(snap.round) { mutableStateOf(false) }
    var readySent by remember(snap.round) { mutableStateOf(false) }
    val isHost = snap.isHost(snap.you?.id)
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    val me = snap.you
    val connectedCount = snap.players.count { it.connected }
    val ready = readySent || me?.ready == true
    val pendingReady = (connectedCount - snap.readyCount).coerceAtLeast(0)

    LaunchedEffect(snap.round) {
        picked = snap.you?.selected ?: -1
        reported = false
        readySent = false
    }

    fun answer(option: Int) {
        if (busy || answered || reveal) return
        picked = option
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.answer(snap.code, playerToken, snap.round, option)
            }
            busy = false
            if (result is GameClient.Result.Error) {
                onError(result.message)
            } else if (result is GameClient.Result.Ok && !result.value) {
                onError("La respuesta llegó tarde o ya estaba registrada.")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ronda ${snap.round} de ${snap.rounds}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    reveal -> "Resultado"
                    snap.playersPace -> "$connectedCount jugadores"
                    else -> "${seconds}s"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (reveal) VisionTextSecondary else VisionBlueBright,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(NarratorMood.Thinking, size = 78.dp, alive = true)
            Spacer(Modifier.width(8.dp))
            val narCorrect = rememberNarratorText(NarratorEvent.Correct, snap.narrKey())
            val narWrong = rememberNarratorText(NarratorEvent.Wrong, snap.narrKey())
            val narTime = rememberNarratorText(NarratorEvent.TimeUp, snap.narrKey())
            NarratorBubble(
                when {
                    reveal && me?.correct == true -> narCorrect
                    reveal && me?.answered == true -> narWrong
                    reveal -> narTime
                    answered -> "Esperando a los demás… no se duerman."
                    else -> "Piénsale bien, que la racha no se regala."
                },
            )
        }
        Text(
            question.prompt,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (question.art.isNotBlank()) {
            AsyncImage(
                model = question.art,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VisionSurface),
            )
        }
        GameOptions(
            question = question,
            selected = if (picked >= 0) picked else (me?.selected ?: -1),
            correct = correct,
            reveal = reveal,
            enabled = !reveal && !answered && !busy,
            onAnswer = ::answer,
        )
        if (!reveal && answered) {
            Text(
                if (snap.playersPace) {
                    "Respuesta enviada. Van ${snap.answeredCount} de $connectedCount."
                } else {
                    "Respuesta enviada."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
        }
        if (reveal) {
            val points = me?.points ?: 0
            Text(
                if (points > 0) "+$points puntos" else "Sin puntos esta ronda",
                style = MaterialTheme.typography.titleMedium,
                color = if (points > 0) VisionSuccess else VisionLiveRed,
            )
            Text(
                "Respuesta correcta: ${snap.answer?.text.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
            Text(
                "Puntos totales: ${me?.score ?: 0}",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionRatingGold,
            )
            if (snap.playersPace) {
                Text(
                    "Listos para seguir: ${snap.readyCount} de $connectedCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTextSecondary,
                )
                if (!ready) {
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameClient.ready(snap.code, playerToken, snap.round)
                                }
                                busy = false
                                when (result) {
                                    is GameClient.Result.Ok -> {
                                        if (result.value) readySent = true
                                        else onError("No se pudo marcar tu continuar.")
                                    }
                                    is GameClient.Result.Error -> onError(result.message)
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Enviando…" else "Continuar")
                    }
                } else {
                    Text(
                        "Listo. Esperando al anfitrión…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                    )
                }
            }
            if (isHost) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { GameClient.next(snap.code, hostToken) }
                            busy = false
                            if (result is GameClient.Result.Error) onError(result.message)
                        }
                    },
                    enabled = !busy && (!snap.playersPace || snap.allReady),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            !snap.playersPace || snap.allReady -> "Siguiente"
                            else -> "Faltan $pendingReady por continuar"
                        },
                    )
                }
            }
            TextButton(
                onClick = {
                    if (reported) return@TextButton
                    reported = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            GameClient.report(snap.code, playerToken, question.id, "ambigua")
                        }
                    }
                },
                enabled = !reported,
            ) {
                Text(if (reported) "Gracias, la revisaremos." else "Reportar pregunta")
            }
        }
    }
}

/** Opciones de la pregunta; separado para poder probar el pintado en el sandbox. */
@Composable
internal fun GameOptions(
    question: GameQuestion,
    selected: Int,
    correct: Int,
    reveal: Boolean,
    enabled: Boolean,
    onAnswer: (Int) -> Unit,
) {
    question.options.forEachIndexed { index, option ->
        val isSelected = selected == index
        val isCorrect = reveal && index == correct
        val isWrong = reveal && isSelected && index != correct
        val container = when {
            isCorrect -> VisionSuccess
            isWrong -> VisionLiveRed
            isSelected -> VisionBlue
            else -> VisionSurfaceHigh
        }
        val content = if (isCorrect) VisionBlack else VisionTextPrimary
        Button(
            onClick = { onAnswer(index) },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container,
                disabledContentColor = content,
            ),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                "${('A' + index)}.  $option",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun CharadaContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val charada = snap.charada ?: return
    val v2 = snap.rulesVersion >= 2
    val reveal = snap.phase == "reveal"
    val directorPhase = snap.phase == "director"
    val isActor = snap.you?.actor == true || charada.role == "actor"
    val isDirector = charada.role == "director"
    val isSaboteur = charada.role == "saboteur"
    val buzzerActive = charada.buzzerActive
    val buzzMine = charada.buzzerId.isNotBlank() && charada.buzzerId == snap.you?.id
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    val accent = Color(GameCatalog.Charadas.accent)
    var busy by remember { mutableStateOf(false) }
    var lastCorrect by remember { mutableIntStateOf(charada.correct) }
    var lastPasses by remember { mutableIntStateOf(charada.passes) }

    LaunchedEffect(charada.correct, charada.passes) {
        if (!reveal) {
            when {
                charada.correct > lastCorrect ->
                    playNarratorCue(context, NarratorEvent.CharadaCorrect, "turn:${charada.correct}")
                charada.passes > lastPasses ->
                    playNarratorCue(context, NarratorEvent.CharadaPass, "turn:${charada.passes}")
            }
        }
        lastCorrect = charada.correct
        lastPasses = charada.passes
    }

    fun act(action: String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { GameClient.charada(snap.code, playerToken, action) }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("El turno cambió; espera tu siguiente oportunidad.")
            }
        }
    }

    fun chooseRestriction(index: Int) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(snap.code, playerToken, snap.round, "charada_director", index = index)
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("La restricción ya no está disponible.")
            }
        }
    }

    fun buzz() {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(snap.code, playerToken, snap.round, "charada_buzz")
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("Alguien más ya tocó el timbre.")
            }
        }
    }

    fun buzzResult(correct: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(
                    snap.code, playerToken, snap.round, "charada_buzz_result",
                    text = if (correct) "correct" else "fail",
                )
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
        }
    }

    fun help(kind: String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.action(snap.code, playerToken, snap.round, "charada_help", text = kind)
            }
            busy = false
            if (result is GameClient.Result.Error) onError(result.message)
            else if (result is GameClient.Result.Ok && !result.value) {
                onError("No se pudo comprar la ayuda.")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(
                mood = if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
                size = 92.dp,
                alive = true,
            )
            Spacer(Modifier.width(9.dp))
            NarratorBubble(
                when {
                    reveal && charada.correct > 0 -> "¡${charada.correct} ${if (charada.correct == 1) "acierto" else "aciertos"}! Buen turno, aunque sea de milagro."
                    reveal -> "Se acabó el turno. Ni modo, la próxima."
                    isActor && charada.correct > 0 -> "¡Bien! ${charada.correct} ${if (charada.correct == 1) "acierto" else "aciertos"}. Sigue así, que vas como cohete."
                    isActor && charada.passes > 0 -> "Pasamos. ¡Siguiente! No se duerman."
                    isActor -> "Te toca actuar. Sin decir el nombre, ¿eh?"
                    charada.correct > 0 -> "¡Van ${charada.correct} ${if (charada.correct == 1) "acierto" else "aciertos"}! Sigue adivinando."
                    else -> "Adivina lo que actúa ${charada.actorName}. Di tu respuesta en voz alta."
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameStageBadge(
                when {
                    reveal -> "FIN DEL TURNO"
                    directorPhase -> "DIRECTOR"
                    isSaboteur -> "SABOTAJE"
                    else -> "TURNO DE ${charada.actorName.uppercase()}"
                },
                accent,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (reveal) "Resultado" else "$seconds s",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (reveal) VisionTextSecondary else accent,
            )
        }
        if (!reveal) {
            LinearProgressIndicator(
                progress = {
                    (seconds / if (directorPhase) 5f else 60f).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = VisionSurfaceHigh,
            )
        }
        if (!reveal && buzzerActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(2.dp, VisionLiveRed.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "¡CORTE! ${charada.buzzerName}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = VisionLiveRed,
                    )
                    Text(
                        if (isActor) "¿Dijo el título correcto?"
                        else if (buzzMine) "Dilo en voz alta. El actor decide."
                        else "${charada.buzzerName} está respondiendo…",
                        color = VisionTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (isActor) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { buzzResult(true) },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = VisionSuccess),
                                modifier = Modifier.weight(1f),
                            ) { Text("Correcto", color = VisionBlack) }
                            Button(
                                onClick = { buzzResult(false) },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = VisionLiveRed),
                                modifier = Modifier.weight(1f),
                            ) { Text("Falló (-200)") }
                        }
                    }
                }
            }
        } else if (!reveal && directorPhase) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (isDirector) "TE TOCA DIRIGIR" else "EL DIRECTOR ELIGE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Text(
                        if (isDirector) "Elige una restricción para ${charada.actorName}."
                        else "${charada.directorName} está eligiendo cómo actuará ${charada.actorName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (isDirector) {
                        charada.directorOptions.forEachIndexed { index, option ->
                            OutlinedButton(
                                onClick = { chooseRestriction(index) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(option) }
                        }
                    }
                }
            }
        } else if (!reveal && isSaboteur) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, VisionLiveRed.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("TU MISIÓN: SABOTEAR", color = VisionLiveRed, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Conoces la respuesta. Grita pistas falsas y confunde a la sala; si nadie acierta, ganas ${600} puntos.", color = VisionTextSecondary, textAlign = TextAlign.Center)
                    Text(charada.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (charada.restriction.isNotBlank()) {
                        Text("Restricción del director: ${charada.restriction}", style = MaterialTheme.typography.bodySmall, color = accent)
                    }
                }
            }
        } else if (!reveal && isActor) {
            Text(
                "TE TOCA ACTUAR",
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        charada.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (charada.year > 0) {
                        Text("${charada.year}", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                    }
                    if (charada.poster.isNotBlank()) {
                        AsyncImage(
                            model = charada.poster,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VisionBlack),
                        )
                    }
                    if (charada.restriction.isNotBlank()) {
                        Text(
                            "Director: ${charada.restriction}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (charada.helpGenre && charada.genre.isNotBlank()) {
                        Text("Género: ${charada.genre}", style = MaterialTheme.typography.bodySmall, color = accent)
                    }
                    Text(
                        "Aciertos: ${charada.correct} · Pases: ${charada.passes} de ${charada.maxPasses}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                }
            }
            if (v2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { help("genre") },
                        enabled = !busy && !charada.helpGenre,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (charada.helpGenre) "Género soplado" else "Soplar género (-200)") }
                    OutlinedButton(
                        onClick = { help("year") },
                        enabled = !busy && !charada.helpYear,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (charada.helpYear) "Año en TV" else "Mostrar año (-300)") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { act("correct") },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = VisionSuccess),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text("Correcto  +${charada.points}", color = VisionBlack, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { act("pass") },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = VisionSurfaceHigh),
                    modifier = Modifier.weight(0.65f).height(56.dp),
                ) {
                    Text("Pasar")
                }
            }
        } else if (!reveal) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        VisionIcons.Charadas,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        "Adivina lo que actúa ${charada.actorName}. Sin pena, ¿eh?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "El título solo está en su teléfono. Di tu respuesta en voz alta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (v2) {
                        Button(
                            onClick = ::buzz,
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = VisionLiveRed),
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                        ) {
                            Text("¡CORTE! Ya sé cuál es", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("La respuesta era", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
                    Text(
                        charada.title.ifBlank { "—" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (charada.poster.isNotBlank()) {
                        AsyncImage(
                            model = charada.poster,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VisionBlack),
                        )
                    }
                    Text(
                        "${charada.correct} aciertos · ${charada.correct * charada.points} puntos",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionSuccess,
                    )
                    if (charada.restriction.isNotBlank()) {
                        Text("Restricción: ${charada.restriction}", color = accent, style = MaterialTheme.typography.bodySmall)
                    }
                    if (charada.saboteurName.isNotBlank()) {
                        Text("Saboteador: ${charada.saboteurName}", color = VisionLiveRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            GameRevealActions(snap, playerToken, hostToken, onError)
        }
        GameScoreboard(snap, accent)
    }
}

@Composable
private fun WagerContent(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    tick: Long,
    snapshotAt: Long,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wager = snap.wager ?: return
    val question = snap.question
    val reveal = snap.phase == "reveal"
    val betting = snap.phase == "wager"
    val clue = snap.phase == "clue"
    val seconds = remainingSeconds(snap, tick, snapshotAt)
    val accent = Color(GameCatalog.Wager.accent)
    val me = snap.you
    var busy by remember { mutableStateOf(false) }
    var wagerChoice by remember(snap.round) { mutableIntStateOf(me?.wager?.takeIf { it > 0 } ?: 500) }
    var wagerSent by remember(snap.round) { mutableStateOf((me?.wager ?: 0) > 0) }
    var picked by remember(snap.round) { mutableIntStateOf(-1) }

    LaunchedEffect(snap.round) {
        wagerChoice = me?.wager?.takeIf { it > 0 } ?: 500
        wagerSent = (me?.wager ?: 0) > 0
        picked = -1
    }

    fun bet(points: Int) {
        if (busy || wagerSent || !betting) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.wager(snap.code, playerToken, snap.round, points)
            }
            busy = false
            when (result) {
                is GameClient.Result.Ok -> if (result.value) wagerSent = true else onError("No se pudo registrar la apuesta.")
                is GameClient.Result.Error -> onError(result.message)
            }
        }
    }

    fun answer(option: Int) {
        if (busy || reveal || !clue || me?.wagerDone == true) return
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

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(
                mood = if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
                size = 92.dp,
                alive = true,
            )
            Spacer(Modifier.width(9.dp))
            val narWagerWin = rememberNarratorText(NarratorEvent.WagerWin, snap.narrKey())
            val narWagerLose = rememberNarratorText(NarratorEvent.WagerLose, snap.narrKey())
            val narWagerReveal = rememberNarratorText(NarratorEvent.WagerReveal, snap.narrKey())
            val narWagerStart = rememberNarratorText(NarratorEvent.WagerStart, snap.narrKey())
            NarratorBubble(
                when {
                    reveal && (me?.wagerDelta ?: 0) > 0 -> narWagerWin
                    reveal && (me?.wagerDelta ?: 0) < 0 -> narWagerLose
                    reveal -> narWagerReveal
                    betting -> narWagerStart
                    clue -> "Pista ${wager.clueIndex} de ${wager.clueCount}. Paga x${wager.factor}."
                    else -> "Una pista más. Piénsenle."
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameStageBadge(
                when {
                    reveal -> "RESULTADO"
                    betting -> "APUESTA"
                    else -> "PISTA ${wager.clueIndex} DE ${wager.clueCount}"
                },
                accent,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (reveal) "Resultado" else "$seconds s",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (reveal) VisionTextSecondary else accent,
            )
        }
        if (!reveal) {
            LinearProgressIndicator(
                progress = {
                    val total = if (betting) 10f else 15f
                    (seconds / total).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = VisionSurfaceHigh,
            )
        }
        if (betting) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.94f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿Cuánto apuestas?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Acertar en la pista 1 paga x3; en la 2, x2; en la 3, x1. Si fallas, pierdes la apuesta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        wager.stakeOptions.forEach { option ->
                            val selected = wagerChoice == option
                            Surface(
                                onClick = { if (!wagerSent) wagerChoice = option },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) accent.copy(alpha = 0.24f) else VisionSurfaceHigh,
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "$option",
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) accent else VisionTextPrimary,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { bet(wagerChoice) },
                        enabled = !busy && !wagerSent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                busy -> "Apostando…"
                                wagerSent -> "Apuesta registrada: $wagerChoice"
                                else -> "Apostar $wagerChoice puntos"
                            },
                        )
                    }
                    Text(
                        "Puntos actuales: ${me?.score ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionRatingGold,
                    )
                }
            }
        } else if (clue && question != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pista ${wager.clueIndex} de ${wager.clueCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Apuesta: ${me?.wager ?: 0} · paga x${wager.factor}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VisionRatingGold,
                )
            }
            Text(
                question.prompt,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (question.art.isNotBlank()) {
                AsyncImage(
                    model = question.art,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VisionSurface),
                )
            }
            GameOptions(
                question = question,
                selected = if (picked >= 0) picked else (me?.selected ?: -1),
                correct = -1,
                reveal = false,
                enabled = !busy && me?.wagerDone != true,
                onAnswer = ::answer,
            )
            if (me?.wagerDone == true) {
                Text(
                    "Ya respondiste. Espera el resultado de la ronda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTextSecondary,
                )
            } else {
                Text(
                    "Si fallas pierdes la apuesta. Puedes esperar la siguiente pista.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
        } else if (reveal && question != null) {
            Text(
                "La respuesta era: ${snap.answer?.text.orEmpty()}",
                style = MaterialTheme.typography.titleLarge,
                color = VisionSuccess,
                fontWeight = FontWeight.Bold,
            )
            if (wager.poster.isNotBlank()) {
                AsyncImage(
                    model = wager.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VisionSurface),
                )
            }
            GameOptions(
                question = question,
                selected = me?.selected ?: -1,
                correct = snap.answer?.option ?: -1,
                reveal = true,
                enabled = false,
                onAnswer = {},
            )
            val delta = me?.wagerDelta ?: 0
            Text(
                when {
                    delta > 0 -> "Ganaste +$delta puntos"
                    delta < 0 -> "Perdiste ${-delta} puntos"
                    else -> "Sin cambios en esta ronda"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (delta >= 0) VisionSuccess else VisionLiveRed,
            )
            Text(
                "Puntos totales: ${me?.score ?: 0}",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionRatingGold,
            )
            GameRevealActions(snap, playerToken, hostToken, onError)
        }
        GameScoreboard(snap, accent)
    }
}

@Composable
internal fun GameRevealActions(
    snap: GameSnapshot,
    playerToken: String,
    hostToken: String,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var readySent by remember(snap.round) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val connectedCount = snap.players.count { it.connected }
    val ready = readySent || snap.you?.ready == true
    val pending = (connectedCount - snap.readyCount).coerceAtLeast(0)
    if (snap.playersPace) {
        Text(
            "Listos para seguir: ${snap.readyCount} de $connectedCount",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextSecondary,
        )
        if (!ready) {
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            GameClient.ready(snap.code, playerToken, snap.round)
                        }
                        busy = false
                        when (result) {
                            is GameClient.Result.Ok -> {
                                if (result.value) readySent = true
                                else onError("No se pudo marcar tu continuar.")
                            }
                            is GameClient.Result.Error -> onError(result.message)
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Enviando…" else "Continuar")
            }
        } else {
            Text("Listo. Esperando al anfitrión…", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
        }
    }
    if (snap.isHost(snap.you?.id)) {
        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { GameClient.next(snap.code, hostToken) }
                    busy = false
                    if (result is GameClient.Result.Error) onError(result.message)
                }
            },
            enabled = !busy && (!snap.playersPace || snap.allReady),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    !snap.playersPace || snap.allReady -> "Siguiente"
                    else -> "Faltan $pending por continuar"
                },
            )
        }
    }
}

@Composable
internal fun GameScoreboard(snap: GameSnapshot, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.82f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marcador", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("Sala ${snap.displayCode}", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
            }
            if (snap.audience > 0) {
                Text("Público: ${snap.audience}", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            snap.players.forEach { player ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(playerColor(player.color), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        player.name + if (player.id == snap.you?.id) " (tú)" else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (snap.game == "charadas" && player.id == snap.charada?.actorId) {
                        Text("ACTOR", style = MaterialTheme.typography.labelSmall, color = accent)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (snap.game == "wager" && player.wagerDone) {
                        Text("LISTO", style = MaterialTheme.typography.labelSmall, color = VisionSuccess)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.titleSmall,
                        color = VisionRatingGold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PodiumContent(snap: GameSnapshot, onHome: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (snap.isFiesta) {
            FiestaHostAvatar(snap.game, 150.dp)
            FiestaHostBubble(
                gameId = snap.game,
                text = snap.fiesta?.hostCue?.text.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            NarratorView(NarratorMood.Celebrate, size = 150.dp, animated = true, alive = true)
            NarratorBubble(rememberNarratorText(NarratorEvent.Podium, snap.narrKey()))
        }
        Text("Podio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (snap.highlights.isNotEmpty()) {
            Text("Momentos destacados", style = MaterialTheme.typography.titleMedium, color = VisionRatingGold)
            snap.highlights.forEach { highlight ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VisionSurface.copy(alpha = 0.9f)),
                    border = BorderStroke(1.dp, VisionRatingGold.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(medalIconRes(highlight.id)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(highlight.title, style = MaterialTheme.typography.labelMedium, color = VisionRatingGold)
                            Text(highlight.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(highlight.detail, style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                        }
                    }
                }
            }
        }
        snap.players.forEachIndexed { index, player ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (index == 0) VisionBlue.copy(alpha = 0.25f) else VisionSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index == 0) VisionRatingGold else VisionTextSecondary,
                        modifier = Modifier.width(34.dp),
                    )
                    Text(player.name, modifier = Modifier.weight(1f))
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VisionBlueBright,
                    )
                }
            }
        }
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
        TextButton(onClick = onBack) { Text("Salir de la sala") }
    }
}

@Composable
private fun ClosedContent(onHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NarratorView(NarratorMood.Oops, size = 140.dp)
        NarratorBubble("La sala se cerró.")
        Text("La partida terminó o el servidor reinició la sala.", color = VisionTextSecondary)
        Button(onClick = onHome) { Text("Volver al inicio") }
    }
}

@Composable
private fun PlayerRow(player: GamePlayer, meId: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(playerColor(player.color), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            player.name + if (player.id == meId) " (tú)" else "",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (player.isHost) {
            Text("Anfitrión", style = MaterialTheme.typography.labelSmall, color = VisionBlueBright)
        }
        if (!player.connected) {
            Text(" · sin conexión", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${player.score}",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionRatingGold,
        )
    }
}

@Composable
private fun JoinRoomGate(
    code: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(GameStore.nickname(context)) }
    var spectator by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(VisionIcons.Back, contentDescription = "Volver") }
            Text("Unirse a ${GameCodes.format(code)}", style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(NarratorMood.Welcome, size = 120.dp, animated = true)
            Spacer(Modifier.width(10.dp))
            NarratorBubble("Te unes a la sala ${GameCodes.format(code)}.")
        }
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 14) name = it },
            label = { Text("Tu apodo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = spectator, onCheckedChange = { spectator = it })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Entrar como público", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Vota por tu dibujo o mentira favorita sin competir por puntos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
        }
        Button(
            onClick = {
                if (busy) return@Button
                val clean = name.trim()
                if (clean.length < 2) {
                    error = "Escribe un apodo de 2 a 14 caracteres."
                    return@Button
                }
                error = ""
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { GameClient.join(code, clean, spectator) }
                    busy = false
                    when (result) {
                        is GameClient.Result.Ok -> {
                            GameStore.saveNickname(context, clean)
                            playNarratorCue(context, NarratorEvent.Joined, "join:$code")
                            onJoined(result.value.playerToken)
                        }
                        is GameClient.Result.Error -> error = result.message
                    }
                }
            },
            enabled = !busy && name.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Entrando…" else if (spectator) "Entrar al público" else "Unirse")
        }
        if (error.isNotBlank()) Text(error, color = VisionLiveRed)
    }
}

internal fun remainingSeconds(snapshot: GameSnapshot, nowMs: Long, snapshotAtMs: Long): Int {
    if (snapshot.phaseEndsAt <= 0L) return 0
    val elapsed = if (snapshotAtMs > 0L) (nowMs - snapshotAtMs).coerceAtLeast(0L) else 0L
    val serverNow = snapshot.serverNow + elapsed
    return ((snapshot.phaseEndsAt - serverNow).coerceAtLeast(0L) / 1000L).toInt()
}

internal fun playerColor(index: Int): Color = when (index.mod(8)) {
    0 -> VisionBlue
    1 -> VisionBlueBright
    2 -> VisionSuccess
    3 -> VisionRatingGold
    4 -> Color(0xFFB388FF)
    5 -> Color(0xFFFF8A65)
    6 -> Color(0xFF4DD0E1)
    else -> Color(0xFFF06292)
}
