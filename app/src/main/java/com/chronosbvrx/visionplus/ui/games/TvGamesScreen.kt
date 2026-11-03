package com.chronosbvrx.visionplus.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.games.GameAmbient
import com.chronosbvrx.visionplus.data.games.FiestaCatalog
import com.chronosbvrx.visionplus.data.games.GameCatalog
import com.chronosbvrx.visionplus.data.games.GameClient
import com.chronosbvrx.visionplus.data.games.GameCodes
import com.chronosbvrx.visionplus.data.games.GameCreated
import com.chronosbvrx.visionplus.data.games.GameInfo
import com.chronosbvrx.visionplus.data.games.GameNarrator
import com.chronosbvrx.visionplus.data.games.GamePack
import com.chronosbvrx.visionplus.data.games.GamePlayer
import com.chronosbvrx.visionplus.data.games.GameSfx
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStore
import com.chronosbvrx.visionplus.data.games.NarratorAudio
import com.chronosbvrx.visionplus.data.games.NarratorEvent
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.games.NarratorMood
import com.chronosbvrx.visionplus.ui.tv.TvChoiceChip
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
import com.chronosbvrx.visionplus.ui.tv.TvCompactAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plató de Vision+ Play en la tele. Primero el selector de juegos (jugadores,
 * duración y cómo se juega); al elegir uno crea la sala `display`, enseña el QR
 * y arbitra la partida. La tele no reproduces audio: la voz vive en los teléfonos.
 */
@Composable
fun TvGamesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    var created by remember { mutableStateOf<GameCreated?>(null) }
    var snapshot by remember { mutableStateOf<GameSnapshot?>(null) }
    var snapshotAt by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var since by remember { mutableLongStateOf(0L) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var lastGame by remember { mutableStateOf(GameCatalog.all.first()) }
    var lastAudio by remember { mutableStateOf("") }
    var lastCorrect by remember { mutableIntStateOf(0) }
    var lastPasses by remember { mutableIntStateOf(0) }
    var triviaPicker by remember { mutableStateOf(false) }
    var triviaPacks by remember { mutableStateOf<List<GamePack>>(emptyList()) }
    var selectedPack by remember { mutableStateOf("general") }
    var lastPlayers by remember { mutableIntStateOf(-1) }
    var adultMode by remember { mutableStateOf(false) }
    // Modo de prueba temporal: permite iniciar y votar con un solo jugador.
    var soloTest by remember { mutableStateOf(true) }
    var showIntro by rememberSaveable { mutableStateOf(true) }
    val selectorFocus = remember { FocusRequester() }
    val lobbyFocus = remember { FocusRequester() }
    var selectorAccent by remember { mutableStateOf(Color(GameCatalog.Trivia.accent)) }
    val newFocus = remember { FocusRequester() }

    fun leaveRoom() {
        created = null
        snapshot = null
        since = 0
        error = ""
        NarratorAudio.stop()
        GameSfx.stop()
    }

    // Atrás por niveles: intro → selector → sala; nunca salta de la sección
    // de juegos cuando estás dentro de un lobby o una partida.
    BackHandler {
        when {
            showIntro -> showIntro = false
            triviaPicker -> triviaPicker = false
            created != null -> leaveRoom()
            else -> onBack()
        }
    }

    fun newRoom(info: GameInfo, pack: String = "general") {
        if (creating) return
        val roomSyncCode = com.chronosbvrx.visionplus.data.sync.SyncStore
            .ensureCode(context)
        creating = true
        lastGame = info
        lastAudio = ""
        lastCorrect = 0
        lastPasses = 0
        lastPlayers = -1
        error = ""
        snapshot = null
        since = 0
        created = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GameClient.create(
                    hostName = "Pantalla",
                    mode = "display",
                    game = info.id,
                    pack = if (info.id == "trivia") pack else "general",
                    rounds = info.rounds,
                    family = !adultMode,
                    soloTest = soloTest,
                    syncCode = roomSyncCode,
                )
            }
            creating = false
            when (result) {
                is GameClient.Result.Ok -> {
                    val fresh = result.value
                    val serverGame = fresh.snapshot?.game
                    val serverPack = fresh.snapshot?.pack
                    val wrongPack = info.id == "trivia" && pack != "general" &&
                        !serverPack.isNullOrBlank() && serverPack != pack
                    if ((!serverGame.isNullOrBlank() && serverGame != info.id) || wrongPack) {
                        withContext(Dispatchers.IO) {
                            GameClient.close(fresh.code, fresh.hostToken)
                        }
                        created = null
                        snapshot = null
                        error = if (wrongPack) {
                            "El servidor de juegos aún no tiene la sección " +
                                "${GameCatalog.triviaPack(pack).title}. " +
                                "Actualiza el servicio y vuelve a intentarlo."
                        } else {
                            "El servidor de juegos aún no tiene ${info.title}. " +
                                "Actualiza el servicio y vuelve a intentarlo."
                        }
                    } else {
                        created = fresh
                        snapshot = fresh.snapshot
                        snapshotAt = System.currentTimeMillis()
                        since = fresh.snapshot?.rev ?: 0L
                        GameStore.saveHost(context, fresh.code, fresh.hostToken)
                    }
                }
                is GameClient.Result.Error -> error = result.message
            }
        }
    }

    // Al cambiar de sala (o salir), cierra la anterior. La sala vive en memoria; no se
    // deja basura si la tele cambia de pantalla.
    DisposableEffect(created?.code) {
        val room = created
        onDispose {
            if (room != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    GameClient.close(room.code, room.hostToken)
                }
            }
        }
    }

    val roomCode = created?.code
    val hostTokenForDisplay = created?.hostToken.orEmpty()
    LaunchedEffect(roomCode, hostTokenForDisplay) {
        val code = roomCode ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                when (val result = withContext(Dispatchers.IO) {
                    GameClient.state(code, since, "", "display", hostTokenForDisplay)
                }) {
                    is GameClient.StateResult.Snapshot -> {
                        snapshot = result.value
                        snapshotAt = System.currentTimeMillis()
                        since = result.value.rev
                        error = ""
                    }
                    GameClient.StateResult.Unchanged -> Unit
                    is GameClient.StateResult.Error -> {
                        if (result.code == 404 || result.code == 410) {
                            error = "La sala se cerró. Elige otro juego."
                            leaveRoom()
                        } else if (result.code != 0) {
                            error = result.message
                        }
                    }
                }
                delay(
                    if (snapshot?.phase in listOf(
                            "question", "clue", "attack", "sort", "assault", "punishment",
                            "write", "guess", "create", "pitch",
                        )
                    ) 700 else 1_000,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NarratorAudio.stop()
            GameSfx.stop()
            GameAmbient.stop()
        }
    }
    LaunchedEffect(Unit) {
        GameAmbient.start(context)
    }
    LaunchedEffect(Unit) {
        when (val result = withContext(Dispatchers.IO) { GameClient.packs() }) {
            is GameClient.Result.Ok -> triviaPacks = result.value
            is GameClient.Result.Error -> Unit
        }
    }
    LaunchedEffect(triviaPacks) {
        val available = triviaPacks.filter { it.active }
        if (available.isNotEmpty() && available.none { it.id == selectedPack }) {
            selectedPack = "general"
        }
    }
    LaunchedEffect(created?.code) {
        val code = created?.code ?: return@LaunchedEffect
        val createdGame = created?.snapshot?.game.orEmpty()
        if (FiestaCatalog.isFiesta(createdGame)) {
            FiestaVoice.play(context, createdGame, "lobby")
        } else {
            playNarratorCue(context, NarratorEvent.RoomCreated, "room:$code")
        }
    }
    LaunchedEffect(
        snapshot?.phase,
        snapshot?.round,
        snapshot?.game,
        snapshot?.wager?.clueIndex,
        snapshot?.impostor?.speakerIndex,
        snapshot?.auction?.tie,
    ) {
        val snap = snapshot ?: return@LaunchedEffect
        // Fiesta 3.0 tiene su propio casting; la narradora histórica no debe
        // encimarse sobre la voz del host nuevo.
        if (snap.isFiesta) return@LaunchedEffect
        val connected = snap.players.count { it.connected }
        val drawingTimedOut = snap.game == "dibujo" &&
            snap.phase == "vote" && (snap.drawing?.submittedCount ?: 0) < connected
        val fibbageTimedOut = snap.game == "fibbage" &&
            snap.phase == "vote" && (snap.fibbage?.submittedCount ?: 0) < (snap.fibbage?.voterCount ?: 0)
        val realVotes = when (snap.game) {
            "dibujo" -> snap.drawing?.options?.firstOrNull { it.isReal }?.votes ?: 0
            "fibbage" -> snap.fibbage?.options?.firstOrNull { it.isReal }?.votes ?: 0
            else -> -1
        }
        val lieSweep = when (snap.game) {
            "dibujo" -> snap.drawing?.options?.any { !it.isReal && !it.house && it.votes >= 3 } == true
            "fibbage" -> snap.fibbage?.options?.any { !it.isReal && !it.house && it.votes >= 3 } == true
            else -> false
        }
        val event = when (snap.phase) {
            "countdown" -> if (
                snap.game == "fibbage" && snap.round == snap.rounds
            ) NarratorEvent.FinalRoundDouble else NarratorEvent.Started
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
            "questions" -> if ((snap.impostor?.speakerIndex ?: 0) <= 1) {
                NarratorEvent.ImpostorQuestions
            } else null
            "coartada" -> NarratorEvent.ImpostorVote
            "draw" -> NarratorEvent.DrawStart
            "title" -> NarratorEvent.DrawVote
            "write" -> NarratorEvent.FibbageWrite
            "auction" -> NarratorEvent.AuctionStart
            "auction_reveal" -> if (snap.auction?.tie == true) {
                NarratorEvent.AuctionTie
            } else NarratorEvent.AuctionReveal
            "vote" -> when {
                drawingTimedOut || fibbageTimedOut -> NarratorEvent.PartyTimeout
                snap.game == "impostor" -> NarratorEvent.ImpostorVote
                snap.game == "dibujo" -> NarratorEvent.DrawVote
                snap.game == "fibbage" -> NarratorEvent.FibbageVote
                else -> NarratorEvent.Reveal
            }
            "guess" -> NarratorEvent.ImpostorCaught
            "reveal" -> when (snap.game) {
                "charadas" -> NarratorEvent.CharadaReveal
                "wager" -> NarratorEvent.WagerReveal
                "impostor" -> {
                    val impostor = snap.impostor
                    if (impostor != null && (!impostor.caught || impostor.guessCorrect)) {
                        NarratorEvent.ImpostorEscaped
                    } else NarratorEvent.Reveal
                }
                "dibujo", "fibbage" -> when {
                    realVotes == 0 -> NarratorEvent.NobodyGuessed
                    lieSweep -> NarratorEvent.LieSweep
                    else -> NarratorEvent.Reveal
                }
                else -> NarratorEvent.Reveal
            }
            "podium" -> NarratorEvent.Podium
            else -> null
        }
        if (event == null) {
            // La pregunta no tiene voz propia: corta la frase anterior para que no
            // se coma el inicio de la ronda. El lobby conserva «sala creada».
            if (snap.phase == "question" || snap.phase == "closed") NarratorAudio.stop()
            return@LaunchedEffect
        }
        val key = snap.narrKey()
        if (key != lastAudio) {
            lastAudio = key
            val sfx = when (snap.phase) {
                "countdown", "auction", "write", "draw", "title" -> GameSfx.Kind.Whoosh
                "secret", "guess" -> GameSfx.Kind.Secret
                "vote" -> GameSfx.Kind.Vote
                "auction_reveal" -> if (snap.auction?.tie == true) GameSfx.Kind.Glass else GameSfx.Kind.Hammer
                "punishment_reveal" -> GameSfx.Kind.Glass
                "coartada" -> GameSfx.Kind.Safe
                "podium" -> GameSfx.Kind.Fanfare
                "reveal" -> GameSfx.Kind.Reveal
                else -> null
            }
            sfx?.let { GameSfx.play(context, it) }
            playNarratorCue(context, event, key)
        }
    }
    LaunchedEffect(snapshot?.players?.size, snapshot?.phase) {
        if (snapshot?.isFiesta == true) return@LaunchedEffect
        val count = snapshot?.players?.size ?: 0
        if (snapshot?.phase == "lobby") {
            if (lastPlayers >= 1 && count > lastPlayers) {
                playNarratorCue(context, NarratorEvent.Joined, "lobby:$count")
            }
            lastPlayers = count
        }
    }
    LaunchedEffect(snapshot?.charada?.correct, snapshot?.charada?.passes) {
        val correct = snapshot?.charada?.correct ?: 0
        val passes = snapshot?.charada?.passes ?: 0
        if (snapshot?.game == "charadas" && snapshot?.phase == "turn") {
            when {
                correct > lastCorrect ->
                    playNarratorCue(context, NarratorEvent.CharadaCorrect, "turn:$correct")
                passes > lastPasses ->
                    playNarratorCue(context, NarratorEvent.CharadaPass, "turn:$passes")
            }
        }
        lastCorrect = correct
        lastPasses = passes
    }

    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            val snap = snapshot
            if (snap != null && snap.phaseEndsAt > 0L &&
                snap.phase !in listOf("lobby", "podium", "closed")
            ) {
                val left = tvRemainingSeconds(snap, tick, snapshotAt)
                if (left in 1..5) GameSfx.play(context, GameSfx.Kind.Tick)
            }
            delay(1_000)
        }
    }

    LaunchedEffect(created?.code, snapshot?.phase) {
        if (created == null) {
            delay(120)
            runCatching { selectorFocus.requestFocus() }
            return@LaunchedEffect
        }
        when (snapshot?.phase) {
            "lobby" -> {
                delay(140)
                runCatching { lobbyFocus.requestFocus() }
            }
            "podium" -> {
                delay(140)
                runCatching { newFocus.requestFocus() }
            }
        }
    }

    val game = snapshot?.info ?: lastGame
    val accent = Color(game.accent)
    // El selector tiñe todo el plató con el acento del juego que apunta el mando.
    val stageAccent by animateColorAsState(
        targetValue = if (created == null) selectorAccent else accent,
        animationSpec = tween(420),
        label = "GameStageAccent",
    )
    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        GameStageBackground(stageAccent)
        if (created == null) FiestaLobbyBackdrop(Modifier.fillMaxSize(), stageAccent)
        val salonGame = snapshot?.game ?: lastGame.id
        if (created != null && salonGame in listOf("impostor", "dibujo", "subasta", "fibbage")) {
            Image(
                painter = painterResource(tvModeBackgroundRes(salonGame)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (created != null && salonGame in listOf(
                "ultima_palabra", "vox_populi", "fuera_de_epoca",
                "texto_roto", "franela_fest", "el_tasador",
            )
        ) {
            Image(
                painter = painterResource(fiestaStageRes(salonGame)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (created == null) 0.34f else 0.52f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().padding(28.dp)) {
            if (triviaPicker) {
                TvTriviaPackPicker(
                    packs = triviaPacks.filter { it.active }.ifEmpty {
                        GameCatalog.triviaPacks.map { GamePack(it.id, it.title, true, 0) }
                    },
                    selected = selectedPack,
                    onSelect = { selectedPack = it },
                    onStart = {
                        triviaPicker = false
                        newRoom(GameCatalog.Trivia, selectedPack)
                    },
                    onBack = { triviaPicker = false },
                )
            } else {
                when {
                    created == null -> TvGameSelector(
                        creating = creating,
                        error = error,
                        focus = selectorFocus,
                        onPick = { info ->
                            if (info.id == "trivia") triviaPicker = true else newRoom(info)
                        },
                        onRandom = { info -> newRoom(info, "general") },
                        onFocusAccent = { selectorAccent = it },
                        adultMode = adultMode,
                        onToggleAdult = { adultMode = !adultMode },
                        soloTest = soloTest,
                        onToggleSolo = { soloTest = !soloTest },
                        initialGameId = lastGame.id,
                        onBack = onBack,
                    )
                    snapshot?.phase == "podium" -> TvGamePodium(
                        snapshot = snapshot!!,
                        newFocus = newFocus,
                        onNewGame = { newRoom(game) },
                        onChangeGame = ::leaveRoom,
                        onBack = onBack,
                    )
                    snapshot?.phase == "countdown" -> TvGameCountdown(
                        snapshot = snapshot!!,
                        tick = tick,
                        snapshotAt = snapshotAt,
                    )
                    snapshot?.game != null && FiestaCatalog.isFiesta(snapshot?.game) &&
                        snapshot?.phase !in listOf("lobby", "countdown", "podium") ->
                        FiestaTvContent(
                            snap = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                        )
                    snapshot?.game == "charadas" && snapshot?.phase in listOf("director", "turn", "reveal") ->
                        TvCharada(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "wager" && (snapshot?.rulesVersion ?: 1) >= 2 &&
                        snapshot?.phase in listOf("clue", "reveal") ->
                        TvWagerV2(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "wager" && snapshot?.phase in listOf("wager", "clue", "reveal") ->
                        TvWager(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "impostor" && snapshot?.phase in listOf("secret", "questions", "coartada", "vote", "guess", "reveal") ->
                        TvImpostor(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "dibujo" && snapshot?.phase in listOf("draw", "title", "vote", "reveal") ->
                        TvDibujo(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "subasta" && snapshot?.phase in listOf("auction", "auction_reveal") ->
                        TvSubasta(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "fibbage" && snapshot?.phase in listOf("write", "vote", "reveal") ->
                        TvFibbage(
                            snapshot = snapshot!!,
                            tick = tick,
                            snapshotAt = snapshotAt,
                            onExit = ::leaveRoom,
                        )
                    snapshot?.game == "trivia" && (snapshot?.rulesVersion ?: 1) >= 2 &&
                        snapshot?.phase in listOf(
                            "attack", "question", "punishment", "punishment_reveal",
                            "sort", "assault", "reveal",
                        ) -> TvTriviaV2(
                        snapshot = snapshot!!,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onBack = ::leaveRoom,
                    )
                    snapshot?.phase == "question" || snapshot?.phase == "reveal" -> TvGameQuestion(
                        snapshot = snapshot!!,
                        tick = tick,
                        snapshotAt = snapshotAt,
                        onBack = ::leaveRoom,
                    )
                    else -> TvGameLobby(
                        code = created!!.code,
                        displayCode = created!!.displayCode,
                        players = snapshot?.players.orEmpty(),
                        hostId = snapshot?.hostId.orEmpty(),
                        game = game,
                        audience = snapshot?.audience ?: 0,
                        focus = lobbyFocus,
                        fiesta = snapshot?.isFiesta == true,
                        soloTest = snapshot?.fiesta?.soloTest == true,
                        hostLine = snapshot?.fiesta?.hostCue?.text.orEmpty(),
                        hostName = FiestaCatalog.hostName(snapshot?.game),
                        onBack = ::leaveRoom,
                    )
                }
            }
            if (error.isNotBlank() && created != null) {
                Text(
                    error,
                    color = VisionLiveRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
        if (showIntro) {
            FiestaGamesIntro(onDone = { showIntro = false })
        }
    }
}

/**
 * Ficha del selector. La copia v2 vive aquí, junto al arte, para que la tele
 * pueda presumir las mecánicas nuevas sin tocar el catálogo compartido.
 */
private data class TvSelectorCard(
    val info: GameInfo,
    val taglineV2: String,
    val mechanics: List<String>,
    val hostLine: String,
    val accent: Color,
)

private fun tvSelectorCard(info: GameInfo): TvSelectorCard = when (info.id) {
    "trivia" -> TvSelectorCard(
        info = info,
        taglineV2 = "Racha, castigo y asalto final",
        mechanics = listOf("ATAQUES EN RACHA", "CABLES DE CASTIGO", "ASALTO FINAL"),
        hostLine = "Ocho rondas con piso caliente. Si entran en racha podrán congelar o parasitar a sus amigos.",
        accent = Color(0xFF2D7DF6),
    )
    "charadas" -> TvSelectorCard(
        info = info,
        taglineV2 = "Set de rodaje con saboteador",
        mechanics = listOf("TIMBRE DE 7 S", "DIRECTOR SECRETO", "SABOTAJE"),
        hostLine = "Set de rodaje listo. El director pone las reglas, el actor hace mímica y un saboteador miente.",
        accent = Color(0xFFEF4444),
    )
    "wager" -> TvSelectorCard(
        info = info,
        taglineV2 = "Póker de pistas y pozo",
        mechanics = listOf("POZO ACUMULADO", "PISTAS PRIVADAS", "INFORMANTES"),
        hostLine = "Póker de pistas con pozo acumulado. ¿Se plantan en la primera pista por x4 o compran ayuda?",
        accent = Color(0xFF8B5CF6),
    )
    "impostor" -> TvSelectorCard(
        info = info,
        taglineV2 = "Detective, cómplice y coartada",
        mechanics = listOf("DETECTIVE", "COARTADA", "CÓMPLICE"),
        hostLine = "Uno miente, pero ahora hay un detective y un cómplice infiltrado. Cuidado con la coartada.",
        accent = Color(0xFFF43F5E),
    )
    "dibujo" -> TvSelectorCard(
        info = info,
        taglineV2 = "Tinta, linterna y mentiras",
        mechanics = listOf("TINTA LIMITADA", "DOS INTENTOS", "LINTERNA"),
        hostLine = "Taller caótico con tinta limitada en la ronda dos y linterna a oscuras en la final.",
        accent = Color(0xFF06B6D4),
    )
    "subasta" -> TvSelectorCard(
        info = info,
        taglineV2 = "Lotes secretos y lote maldito",
        mechanics = listOf("LOTE MALDITO", "INFORMANTES", "PUJA CIEGA"),
        hostLine = "Seis lotes del mercado negro con cartas secretas, informantes y el temido lote maldito.",
        accent = Color(0xFFF59E0B),
    )
    "fibbage" -> TvSelectorCard(
        info = info,
        taglineV2 = "Mentiras, escáner y veneno",
        mechanics = listOf("ESCÁNER", "VENENO", "VOTO DE CERTEZA"),
        hostLine = "Trampa mental: envenenen sus mentiras, usen el escáner y apuesten con certeza doble.",
        accent = Color(0xFF10B981),
    )
    else -> TvSelectorCard(
        info = info,
        taglineV2 = info.tagline,
        mechanics = emptyList(),
        hostLine = info.description,
        accent = Color(info.accent),
    )
}

private const val TV_SELECTOR_RANDOM_LINE =
    "¿No se deciden? La Ruleta de Fiesta elige el juego por ustedes."

/**
 * Selector inmersivo de Vision+ Play: siete tarjetas full-bleed y una octava
 * celda de Ruleta de Fiesta. El arte ocupa toda la tarjeta, el foco escala y
 * el plató entero se tiñe con el acento del juego apuntado.
 */
@Composable
private fun TvGameSelector(
    creating: Boolean,
    error: String,
    focus: FocusRequester,
    onPick: (GameInfo) -> Unit,
    onRandom: (GameInfo) -> Unit,
    onFocusAccent: (Color) -> Unit,
    adultMode: Boolean = false,
    soloTest: Boolean = true,
    initialGameId: String = "",
    onToggleAdult: () -> Unit = {},
    onToggleSolo: () -> Unit = {},
    onBack: () -> Unit,
) {
    val cards = remember { GameCatalog.all.map(::tvSelectorCard) }
    val initialIndex = remember(initialGameId, cards) {
        cards.indexOfFirst { it.info.id == initialGameId }.takeIf { it >= 0 } ?: 0
    }
    val listState = rememberLazyListState()
    var focusedCard by remember(initialGameId) { mutableStateOf(cards[initialIndex]) }
    var randomFocused by remember { mutableStateOf(false) }
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(70)
        booted = true
    }
    // Al volver de una sala, la tarjeta del juego se revela y recibe el foco.
    LaunchedEffect(initialGameId, initialIndex) {
        if (initialIndex > 0) {
            runCatching { listState.scrollToItem(initialIndex) }
            delay(100)
        }
        repeat(6) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
    val targetAccent = if (randomFocused) VisionBlue else focusedCard.accent
    val ambient by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(420),
        label = "SelectorAmbient",
    )
    LaunchedEffect(targetAccent) { onFocusAccent(targetAccent) }
    val fiestaFocused = !randomFocused && FiestaCatalog.isFiesta(focusedCard.info.id)
    val selectorCue = rememberNarratorVariant(
        NarratorEvent.Selector, "selector", speak = !creating && !fiestaFocused,
    )
    val hostLine = when {
        creating -> "Preparando la sala…"
        randomFocused -> TV_SELECTOR_RANDOM_LINE
        focusedCard.hostLine.isNotBlank() -> focusedCard.hostLine
        else -> selectorCue.text
    }
    val pills = if (randomFocused) listOf("SORPRESA", "7 MODOS", "V2") else focusedCard.mechanics

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        "VISION+ PLAY",
                        style = MaterialTheme.typography.headlineLarge,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = VisionTextPrimary,
                    )
                    GameStageBadge("EN VIVO", VisionLiveRed)
                    AnimatedContent(
                        targetState = pills,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "SelectorMechanics",
                    ) { currentPills ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            currentPills.forEach { pill ->
                                Text(
                                    pill,
                                    color = Color(0xFFBFDBFE),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF1E3A8A).copy(alpha = 0.55f),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .border(
                                            1.dp,
                                            VisionBlue.copy(alpha = 0.42f),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    "Elige el juego de esta noche. La tele pone el plató; los teléfonos juegan.",
                    fontSize = 13.sp,
                    color = VisionTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            TvCompactAction(
                if (soloTest) "PRUEBA 1 JUGADOR" else "MODO NORMAL",
                primary = soloTest,
                onClick = onToggleSolo,
            )
            Spacer(Modifier.width(8.dp))
            TvCompactAction(
                if (adultMode) "SIN FILTRO" else "FAMILIAR",
                primary = !adultMode,
                onClick = onToggleAdult,
            )
            Spacer(Modifier.width(8.dp))
            TvCompactAction("Salir", onClick = onBack)
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                contentPadding = PaddingValues(horizontal = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                itemsIndexed(cards) { index, card ->
                    AnimatedVisibility(
                        visible = booted,
                        enter = fadeIn(tween(220, delayMillis = index * 65)) +
                            scaleIn(tween(260, delayMillis = index * 65), initialScale = 0.92f),
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                    ) {
                        TvFullBleedGameCard(
                            card = card,
                            enabled = !creating,
                            focusRequester = if (index == initialIndex) focus else null,
                            onFocused = {
                                randomFocused = false
                                focusedCard = card
                            },
                            onClick = { onPick(card.info) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = booted,
                        enter = fadeIn(tween(220, delayMillis = cards.size * 65)) +
                            scaleIn(tween(260, delayMillis = cards.size * 65), initialScale = 0.92f),
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                    ) {
                        TvRandomPartyCard(
                            enabled = !creating,
                            onFocused = { randomFocused = true },
                            onClick = { onRandom(cards.random().info) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fiestaFocused) {
                FiestaHostAvatar(focusedCard.info.id, 52.dp)
            } else {
                NarratorView(
                    NarratorMood.Welcome,
                    size = 52.dp,
                    animated = !creating,
                    alive = !creating,
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = VisionSurfaceHigh.copy(alpha = 0.92f),
                    contentColor = VisionTextPrimary,
                ),
                border = Border(
                    BorderStroke(1.dp, ambient.copy(alpha = 0.48f)),
                    shape = RoundedCornerShape(16.dp),
                ),
            ) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        if (fiestaFocused) FiestaCatalog.hostName(focusedCard.info.id).uppercase()
                        else "PRESENTADORA",
                        color = if (fiestaFocused) ambient else VisionBlueBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    AnimatedContent(
                        targetState = hostLine,
                        transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(120)) },
                        label = "SelectorHostLine",
                    ) { line ->
                        Text(
                            line,
                            color = VisionTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            TvSelectorControlsHint(
                primary = when {
                    creating -> "Preparando sala"
                    randomFocused -> "Elegir al azar"
                    focusedCard.info.id == "trivia" -> "Elegir sección"
                    else -> "Crear sala QR"
                },
            )
        }
        if (error.isNotBlank()) {
            Text(
                error,
                color = VisionLiveRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TvSelectorControlsHint(primary: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.90f))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "OK",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .background(VisionBlue, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            primary,
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "FLECHAS",
            color = Color(0xFFE2E8F0),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            "Explorar modos",
            color = Color(0xFFCBD5E1),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TvFullBleedGameCard(
    card: TvSelectorCard,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "SelectorCardScale",
    )
    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    val shape = RoundedCornerShape(18.dp)
    val borderBrush = if (focused) {
        Brush.linearGradient(listOf(Color.White, card.accent, VisionBlue))
    } else {
        Brush.linearGradient(
            listOf(card.accent.copy(alpha = 0.38f), Color.White.copy(alpha = 0.08f)),
        )
    }

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interaction,
        modifier = modifier
            .clipToBounds()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = VisionSurface,
            pressedContainerColor = card.accent.copy(alpha = 0.28f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(
                    if (FiestaCatalog.isFiesta(card.info.id)) fiestaSelectorArtRes(card.info.id)
                    else gameArtRes(card.info.id),
                ),
                contentDescription = card.info.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x44050811),
                            0.38f to Color.Transparent,
                            0.66f to Color(0xCC070B14),
                            1f to Color(0xFA070B14),
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = if (focused) 2.5.dp else 1.dp,
                        brush = borderBrush,
                        shape = shape,
                    ),
            )
            Text(
                "${card.info.minPlayers}-${card.info.maxPlayers} JUG · ${card.info.approxMinutes} MIN",
                color = Color(0xFFE2E8F0),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xD9070B14), RoundedCornerShape(50))
                    .border(1.dp, card.accent.copy(alpha = 0.52f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    card.info.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.taglineV2,
                    color = card.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${card.info.rounds} RONDAS",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (focused) {
                        Text(
                            "JUGAR AHORA >",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvRandomPartyCard(
    enabled: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "RandomCardScale",
    )
    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interaction,
        modifier = modifier
            .clipToBounds()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = VisionBlue.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F1E3A), Color(0xFF091020), Color(0xFF1A1033)),
                    ),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(VisionBlue.copy(alpha = 0.22f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        width = if (focused) 2.5.dp else 1.dp,
                        brush = Brush.linearGradient(
                            listOf(VisionBlue, Color(0xFF8B5CF6), Color(0xFF10B981)),
                        ),
                        shape = shape,
                    ),
            )
            Text(
                "?",
                color = Color.White.copy(alpha = 0.10f),
                fontSize = 82.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                "MODO FIESTA",
                color = Color(0xFFBFDBFE),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xFF1E3A8A).copy(alpha = 0.66f), RoundedCornerShape(50))
                    .border(1.dp, VisionBlue.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    "Ruleta Vision+",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Selección al azar entre los 7 juegos",
                    color = Color(0xFF60A5FA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Partida sorpresa · 1-8 jugadores",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvTriviaPackPicker(
    packs: List<GamePack>,
    selected: String,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(140)
        runCatching { first.requestFocus() }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NarratorView(NarratorMood.Welcome, size = 150.dp, animated = true, alive = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "TRIVIA · ELIGE LA SECCIÓN",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = VisionTextPrimary,
                )
                Text(
                    "No todo es general: elige cine, series, anime, infantil o novelas.",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionTextSecondary,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        packs.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { colIndex, pack ->
                    val firstChip = rowIndex == 0 && colIndex == 0
                    TvChoiceChip(
                        label = if (pack.questions > 0) "${pack.title} · ${pack.questions}" else pack.title,
                        selected = pack.id == selected,
                        onClick = { onSelect(pack.id) },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (firstChip) Modifier.focusRequester(first) else Modifier),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        NarratorBubble(
            "Sección ${GameCatalog.triviaPack(selected).title}: " +
                GameCatalog.triviaPack(selected).hint + ".",
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvCompactAction("Empezar", primary = true, onClick = onStart)
            TvCompactAction("Volver", onClick = onBack)
        }
    }
}

@Composable
private fun TvGameLoading(
    creating: Boolean,
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Vision+ Play",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = VisionTextPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (creating) "Preparando la sala…" else "No se pudo crear la sala.",
            style = MaterialTheme.typography.titleMedium,
            color = VisionTextSecondary,
        )
        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = VisionLiveRed, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        if (!creating) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvCompactAction("Reintentar", primary = true, onClick = onRetry)
                TvCompactAction("Volver", onClick = onBack)
            }
        }
    }
}

/** Lobby inmersivo: arte a sangre, QR premium y sala de espera en vivo. */
@Composable
internal fun TvGameLobby(
    code: String,
    displayCode: String,
    players: List<GamePlayer>,
    hostId: String = "",
    game: GameInfo = GameCatalog.Trivia,
    audience: Int = 0,
    focus: FocusRequester? = null,
    fiesta: Boolean = false,
    soloTest: Boolean = false,
    hostLine: String = "",
    hostName: String = "",
    onBack: () -> Unit,
) {
    val look = fiestaLook(game.id)
    val accent = if (fiesta) look.accent else Color(game.accent)
    if (fiesta) {
        val lobbyContext = LocalContext.current
        LaunchedEffect(game.id) { GameAmbient.startFor(lobbyContext, game.id) }
    }
    val roomCode = displayCode.ifBlank { GameCodes.format(code) }
    val host = players.firstOrNull { it.id == hostId || it.isHost }
    val missing = if (soloTest) 0 else (game.minPlayers - players.size).coerceAtLeast(0)
    val narratorLine = if (fiesta && hostLine.isNotBlank()) hostLine
        else rememberNarratorText(NarratorEvent.RoomCreated, "room:$code")
    val slots = (0 until game.maxPlayers).map { players.getOrNull(it) }
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(VisionBlack),
    ) {
        Image(
            painter = painterResource(if (fiesta) look.stageRes else gameArtRes(game.id)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (fiesta) 0.72f else 0.50f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xF2070B14), Color(0xDD070B14), Color(0xB8070B14)),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x99070B14),
                        0.42f to Color(0x22070B14),
                        1f to Color(0xE8070B14),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val glowCenter = Offset(size.width * 0.82f, size.height * 0.16f)
                    val glowRadius = size.minDimension * 0.62f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                            center = glowCenter,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = glowCenter,
                    )
                },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fiesta) {
                    FiestaHostAvatar(game.id, 52.dp)
                } else {
                    NarratorView(
                        NarratorMood.Welcome,
                        size = 52.dp,
                        animated = true,
                        alive = true,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        if (fiesta) look.eyebrow else "SALA PRIVADA",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            game.title.uppercase(),
                            color = VisionTextPrimary,
                            fontSize = if (game.title.length > 16) 23.sp else 26.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(10.dp))
                        GameStageBadge("EN VIVO", VisionLiveRed)
                    }
                    Text(
                        game.tagline,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LobbyMetric("${game.minPlayers}-${game.maxPlayers}", "JUG", accent)
                    LobbyMetric("${game.approxMinutes}", "MIN", accent)
                    LobbyMetric("${game.rounds}", "RONDAS", accent)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LobbyJoinCard(
                    code = code,
                    roomCode = roomCode,
                    playerCount = players.size,
                    maxPlayers = game.maxPlayers,
                    accent = accent,
                    modifier = Modifier.weight(0.50f).fillMaxHeight(),
                )
                LobbyPlayersCard(
                    players = players,
                    slots = slots,
                    game = game,
                    audience = audience,
                    host = host,
                    missing = missing,
                    accent = accent,
                    narratorLine = narratorLine,
                    fiesta = fiesta,
                    soloTest = soloTest,
                    hostName = hostName,
                    focus = focus,
                    onBack = onBack,
                    modifier = Modifier.weight(0.50f).fillMaxHeight(),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, accent.copy(alpha = 0.38f), shape),
        )
    }
}

@Composable
private fun LobbyMetric(value: String, label: String, accent: Color) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF0B1220).copy(alpha = 0.78f))
            .border(1.dp, accent.copy(alpha = 0.22f), shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Text(
            label,
            color = VisionTextSecondary,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun LobbyJoinCard(
    code: String,
    roomCode: String,
    playerCount: Int,
    maxPlayers: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        colors = SurfaceDefaults.colors(
            containerColor = Color(0xFF0A1220).copy(alpha = 0.87f),
            contentColor = Color.White,
        ),
        border = Border(BorderStroke(1.dp, accent.copy(alpha = 0.38f)), shape = shape),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "ÚNETE A LA SALA",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp,
                    )
                    Text(
                        "Abre Vision+ en tu teléfono",
                        color = VisionTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                GameStageBadge("QR", accent)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(7.dp),
                ) {
                    GameQr(code = code, size = 190.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "CÓDIGO DE SALA",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        roomCode,
                        color = VisionBlueBright,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Escanea el QR con Vision+ en tu teléfono",
                        color = VisionTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                    )
                    Text(
                        "También puedes escribir el código en Juegos.",
                        color = VisionTextSecondary,
                        fontSize = 9.sp,
                        maxLines = 3,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B1424).copy(alpha = 0.86f))
                    .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Esperando jugadores…",
                    color = VisionTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$playerCount / $maxPlayers",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Text(
                "La partida empieza desde el teléfono del anfitrión.",
                color = VisionTextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LobbyPlayersCard(
    players: List<GamePlayer>,
    slots: List<GamePlayer?>,
    game: GameInfo,
    audience: Int,
    host: GamePlayer?,
    missing: Int,
    accent: Color,
    narratorLine: String,
    fiesta: Boolean = false,
    soloTest: Boolean = false,
    hostName: String = "",
    focus: FocusRequester?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        colors = SurfaceDefaults.colors(
            containerColor = Color(0xFF0A1220).copy(alpha = 0.87f),
            contentColor = Color.White,
        ),
        border = Border(BorderStroke(1.dp, accent.copy(alpha = 0.32f)), shape = shape),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "JUGADORES",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp,
                    )
                    Text(
                        "${players.size} de ${game.maxPlayers}" +
                            if (audience > 0) " · $audience público" else "",
                        color = VisionTextPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(game.maxPlayers) { index ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    if (index < players.size) accent else Color.White.copy(alpha = 0.14f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                slots.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        row.forEach { player ->
                            TvLobbyPlayerSlot(
                                player = player,
                                accent = accent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                LobbyPill(
                    if (soloTest) {
                        "MODO PRUEBA"
                    } else if (missing > 0) {
                        "FALTAN $missing"
                    } else {
                        "SALA COMPLETA"
                    },
                    if (missing > 0) VisionRatingGold else VisionSuccess,
                )
                LobbyPill("MÍNIMO ${game.minPlayers}", VisionTextSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    if (missing > 0) "Comparte el código" else "Listos para empezar",
                    color = VisionTextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Image(
                    painter = painterResource(tutorialBoardRes(game.id)),
                    contentDescription = "Tutorial de ${game.title}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VisionBlack),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "CÓMO SE JUEGA",
                        color = accent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        game.description,
                        color = VisionTextSecondary,
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B1424).copy(alpha = 0.86f))
                    .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fiesta) {
                    FiestaHostAvatar(game.id, 30.dp)
                } else {
                    NarratorView(
                        NarratorMood.Welcome,
                        size = 30.dp,
                        animated = false,
                        alive = false,
                    )
                }
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        if (fiesta && hostName.isNotBlank()) hostName.uppercase() else "PRESENTADORA",
                        color = if (fiesta) accent else VisionBlueBright,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        narratorLine,
                        color = VisionTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        if (host != null) {
                            "Anfitrión: ${host.name}"
                        } else {
                            "El primero que entre será el anfitrión"
                        },
                        color = if (host != null) accent else VisionTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (host != null) {
                            "Inicia la partida desde su teléfono."
                        } else {
                            "y podrá iniciar la partida desde su teléfono."
                        },
                        color = VisionTextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                LobbyActionButton(
                    label = "Cambiar juego",
                    modifier = focus?.let { Modifier.focusRequester(it) } ?: Modifier,
                    onClick = onBack,
                )
            }
        }
    }
}

@Composable
private fun LobbyActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        targetValue = if (focused) VisionBlue else VisionSurfaceHigh,
        animationSpec = tween(140),
        label = "LobbyActionFill",
    )
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(180.dp)
            .height(50.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fill, shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = shape,
                )
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LobbyPill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun TvLobbyPlayerSlot(
    player: GamePlayer?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val playerColor = if (player != null) tvPlayerColor(player.color) else Color.White.copy(alpha = 0.12f)
    val border = if (player == null) {
        Modifier.drawBehind {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                ),
            )
        }
    } else {
        Modifier.border(1.dp, playerColor.copy(alpha = 0.52f), shape)
    }
    val status = when {
        player == null -> "Esperando"
        !player.connected -> "Sin conexión"
        player.isHost -> "Anfitrión"
        else -> "Conectado"
    }
    val statusColor = when {
        player == null -> Color.White.copy(alpha = 0.34f)
        !player.connected -> VisionLiveRed
        player.isHost -> VisionBlueBright
        else -> VisionSuccess
    }

    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(
                if (player == null) {
                    Color(0xFF08111F).copy(alpha = 0.70f)
                } else {
                    playerColor.copy(alpha = 0.09f)
                },
            )
            .then(border)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (player == null) Color.White.copy(alpha = 0.04f)
                    else playerColor.copy(alpha = 0.18f),
                )
                .border(1.dp, playerColor.copy(alpha = 0.60f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (player != null) player.name.take(1).uppercase() else "+",
                color = if (player != null) Color.White else accent.copy(alpha = 0.85f),
                fontSize = if (player != null) 10.sp else 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                player?.name ?: "Libre",
                color = if (player == null) VisionTextSecondary else VisionTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status,
                color = statusColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TvGameCountdown(snapshot: GameSnapshot, tick: Long, snapshotAt: Long) {
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val info = snapshot.info
    val accent = Color(info.accent)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(snapshot.game) {
        if (snapshot.isFiesta) {
            FiestaVoice.play(context, snapshot.game, "start")
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (snapshot.isFiesta) {
            FiestaHostAvatar(snapshot.game, 190.dp)
        } else {
            NarratorView(NarratorMood.Happy, size = 190.dp, animated = true, alive = true)
        }
        Spacer(Modifier.height(8.dp))
        if (snapshot.isFiesta) {
            FiestaHostBubble(
                gameId = snapshot.game,
                text = snapshot.fiesta?.hostCue?.text.orEmpty(),
                modifier = Modifier.widthIn(max = 620.dp),
            )
        }
        Text(
            when (info.id) {
                "charadas" -> "La función empieza"
                "impostor" -> "Uno de ustedes miente"
                "dibujo" -> "El lienzo está por salir"
                "subasta" -> "El mercado negro abre"
                "fibbage" -> "Prepárense para mentir"
                "wager" -> "Las apuestas están por abrir"
                "ultima_palabra" -> "La primera frase está por salir"
                "vox_populi" -> "La primera encuesta está por salir"
                "fuera_de_epoca" -> "El primer evento está por salir"
                "texto_roto" -> "El primer chat está por abrir"
                "franela_fest" -> "El primer lienzo está por salir"
                "el_tasador" -> "El primer objeto está por salir"
                else -> "La trivia empieza"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = VisionTextSecondary,
        )
        Text(
            if (seconds > 0) "$seconds" else "Ya",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp),
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            "Ronda 1 de ${snapshot.rounds}",
            style = MaterialTheme.typography.titleMedium,
            color = VisionTextSecondary,
        )
    }
}

@Composable
private fun TvGameQuestion(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onBack: () -> Unit,
) {
    val question = snapshot.question ?: return
    val reveal = snapshot.phase == "reveal"
    val correct = snapshot.answer?.option ?: -1
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val connectedCount = snapshot.players.count { it.connected }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.35f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ronda ${snapshot.round} de ${snapshot.rounds}",
                    style = MaterialTheme.typography.titleLarge,
                    color = VisionTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        reveal -> "Resultado"
                        snapshot.playersPace -> "${snapshot.answeredCount} de $connectedCount"
                        else -> "$seconds s"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (reveal) VisionTextSecondary else VisionBlueBright,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                question.prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = VisionTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowOptions.forEachIndexed { colIndex, option ->
                            val index = rowIndex * 2 + colIndex
                            TvGameOption(
                                index = index,
                                text = option,
                                correct = reveal && index == correct,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (!reveal && snapshot.playersPace) {
                Text(
                    "Respondidas ${snapshot.answeredCount} de $connectedCount · esperando a todos",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionBlueBright,
                )
            }
            Spacer(Modifier.weight(1f))
            if (reveal) {
                Text(
                    "Respuesta: ${snapshot.answer?.text.orEmpty()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = VisionSuccess,
                )
                if (snapshot.playersPace) {
                    Text(
                        if (snapshot.allReady) {
                            "Todos listos · el anfitrión avanza desde su teléfono"
                        } else {
                            "Listos ${snapshot.readyCount} de $connectedCount · esperando al resto"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                    )
                }
            }
            TvCompactAction("Cambiar juego", onClick = onBack)
        }
        Column(
            modifier = Modifier.weight(0.65f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarratorView(NarratorMood.Thinking, size = 84.dp, alive = true)
                Spacer(Modifier.width(8.dp))
                val narReveal = rememberNarratorText(NarratorEvent.Reveal, snapshot.narrKey())
                NarratorBubble(
                    if (reveal) narReveal
                    else "Piénsale bien. La racha suma.",
                )
            }
            if (question.art.isNotBlank()) {
                AsyncImage(
                    model = question.art,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(VisionSurface),
                )
            }
            Text(
                "Sala ${snapshot.displayCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
            Text(
                "Puntos",
                style = MaterialTheme.typography.titleMedium,
                color = VisionTextSecondary,
            )
            snapshot.players.take(8).forEach { player ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionRatingGold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvGameOption(index: Int, text: String, correct: Boolean, modifier: Modifier = Modifier) {
    val container = when {
        correct -> VisionSuccess.copy(alpha = 0.88f)
        else -> VisionSurfaceHigh
    }
    Box(
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(
                width = if (correct) 3.dp else 1.dp,
                color = if (correct) VisionSuccess else VisionBlue.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "${('A' + index)}.  $text",
            style = MaterialTheme.typography.titleMedium,
            color = if (correct) VisionBlack else VisionTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun TvCharada(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val charada = snapshot.charada ?: return
    val context = LocalContext.current
    val reveal = snapshot.phase == "reveal"
    val directorPhase = snapshot.phase == "director"
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    LaunchedEffect(charada.buzzerId) {
        if (charada.buzzerId.isNotBlank()) {
            GameSfx.play(context, GameSfx.Kind.Buzzer)
        }
    }
    val accent = Color(GameCatalog.Charadas.accent)
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NarratorView(
                mood = if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
                size = 210.dp,
                animated = true,
                alive = true,
            )
            Spacer(Modifier.height(10.dp))
            GameStageBadge(
                when {
                    reveal -> "FIN DEL TURNO"
                    directorPhase -> "DIRECTOR"
                    else -> "TURNO DE ${charada.actorName.uppercase()}"
                },
                accent,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    reveal -> "¡Se acabó el turno!"
                    charada.buzzerActive -> "¡CORTE! ${charada.buzzerName} dice que ya sabe."
                    directorPhase -> "${charada.directorName} elige una restricción para ${charada.actorName}."
                    else -> "El título está en el teléfono de ${charada.actorName}. Sin decir el nombre, ¿eh?"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VisionTextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                when {
                    reveal -> "${charada.correct} aciertos · ${charada.correct * charada.points} puntos"
                    directorPhase -> "La restricción se revelará al empezar la actuación."
                    else -> "Actúa sin decir el nombre. El público adivina en voz alta."
                },
                style = MaterialTheme.typography.titleMedium,
                color = VisionTextSecondary,
                textAlign = TextAlign.Center,
            )
            if (charada.restriction.isNotBlank() && !reveal) {
                Text(
                    "Restricción: ${charada.restriction}",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (!reveal && (charada.helpGenre || charada.helpYear)) {
                Text(
                    listOfNotNull(
                        charada.genre.takeIf { charada.helpGenre && it.isNotBlank() }?.let { "Género: $it" },
                        charada.year.takeIf { charada.helpYear && it > 0 }?.let { "Año: $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionBlueBright,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (reveal) "Resultado" else "$seconds",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = if (reveal) 54.sp else 96.sp),
                fontWeight = FontWeight.Black,
                color = if (reveal) VisionTextSecondary else accent,
            )
            if (!reveal) {
                Text("segundos", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
            }
            Spacer(Modifier.height(18.dp))
            TvCompactAction("Cambiar juego", onClick = onExit)
        }
        Column(
            modifier = Modifier.weight(1.05f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (reveal) {
                Text("La respuesta era", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
                Text(
                    charada.title.ifBlank { "—" },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                if (charada.poster.isNotBlank()) {
                    AsyncImage(
                        model = charada.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(VisionSurface),
                    )
                }
                if (charada.saboteurName.isNotBlank()) {
                    Text(
                        "Saboteador: ${charada.saboteurName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionLiveRed,
                    )
                }
                if (charada.restriction.isNotBlank()) {
                    Text("Restricción: ${charada.restriction}", color = accent)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvCharadaStat("Aciertos", "${charada.correct}", VisionSuccess)
                    TvCharadaStat("Pases", "${charada.passes} / ${charada.maxPasses}", VisionLiveRed)
                    TvCharadaStat("Ronda", "${snapshot.round} / ${snapshot.rounds}", VisionBlueBright)
                }
                Spacer(Modifier.height(6.dp))
                Text("Marcador", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
            }
            if (reveal) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvCharadaStat("Aciertos", "${charada.correct}", VisionSuccess)
                    TvCharadaStat("Pases", "${charada.passes} / ${charada.maxPasses}", VisionLiveRed)
                    TvCharadaStat("Ronda", "${snapshot.round} / ${snapshot.rounds}", VisionBlueBright)
                }
                Spacer(Modifier.height(6.dp))
                Text("Marcador", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
            }
            snapshot.players.take(8).forEach { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (player.id == charada.actorId) accent.copy(alpha = 0.16f) else VisionSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(tvPlayerColor(player.color), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (player.id == charada.actorId) {
                        Text("ACTOR", style = MaterialTheme.typography.labelSmall, color = accent)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.titleLarge,
                        color = VisionRatingGold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (reveal) "El anfitrión avanza desde su teléfono." else "La tele no muestra el título: solo el teléfono del actor.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
        }
    }
}

@Composable
private fun TvCharadaStat(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(VisionSurface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
    }
}

@Composable
private fun TvTriviaV2(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onBack: () -> Unit,
) {
    val trivia = snapshot.trivia ?: return
    val question = snapshot.question
    val reveal = snapshot.phase == "reveal" || snapshot.phase == "punishment_reveal"
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val accent = Color(GameCatalog.Trivia.accent)
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.35f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameStageBadge(
                    when (snapshot.phase) {
                        "attack" -> "ATAQUES"
                        "punishment", "punishment_reveal" -> "CASTIGO"
                        "sort" -> "RÁFAGA"
                        "assault" -> "ASALTO FINAL"
                        "reveal" -> "RESULTADO"
                        else -> "PREGUNTA"
                    },
                    accent,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ronda ${snapshot.round} de ${snapshot.rounds}",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionTextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (reveal) "Resultado" else "$seconds s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (reveal) VisionTextSecondary else accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!reveal) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(VisionSurfaceHigh),
                ) {
                    Box(
                        Modifier.fillMaxWidth(
                            (seconds.toFloat() / snapshot.phaseDuration.coerceAtLeast(1).toFloat())
                                .coerceIn(0f, 1f),
                        ).fillMaxHeight().background(accent),
                    )
                }
            }
            when (snapshot.phase) {
                "attack" -> {
                    Text("El piso se quema", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = accent)
                    Text(
                        "Los jugadores con dos aciertos seguidos pueden congelar o censurar por 200 puntos, o colgarse gratis de un rival.",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                    )
                    trivia.attack.used.forEach { used ->
                        Text(
                            "${used.byName} usó ${used.power} contra ${used.targetName}",
                            style = MaterialTheme.typography.titleLarge,
                            color = VisionRatingGold,
                        )
                    }
                    if (trivia.attack.used.isEmpty()) {
                        Text("Nadie atacó todavía. La pregunta está por salir.", color = VisionTextSecondary)
                    }
                }
                "punishment", "punishment_reveal" -> {
                    val punishment = trivia.punishment
                    Text(
                        if (reveal) "El cable caliente era ${punishment?.hotWire.orEmpty()}"
                        else "Corten el cable correcto",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = VisionLiveRed,
                    )
                    Text(
                        "Los que fallaron cortan un cable. Los que acertaron votan cuál explota.",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        (punishment?.wires ?: listOf("red", "blue", "yellow", "green")).forEach { wire ->
                            TvModePanel(modifier = Modifier.weight(1f), accent = VisionLiveRed) {
                                Text(wire.uppercase(), style = MaterialTheme.typography.headlineSmall, color = VisionLiveRed)
                                Text(
                                    "${punishment?.votes?.get(wire) ?: 0} votos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VisionTextSecondary,
                                )
                            }
                        }
                    }
                    if (reveal) {
                        punishment?.penalties?.forEach { penalty ->
                            Text(
                                "${snapshot.players.firstOrNull { it.id == penalty.id }?.name ?: "Jugador"}: -${penalty.amount}",
                                color = VisionLiveRed,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            "Respuesta correcta: ${snapshot.answer?.text.orEmpty()}",
                            color = VisionSuccess,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                "sort" -> {
                    val sort = trivia.sort
                    Text("Ráfaga clasificatoria", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = accent)
                    Text(
                        "¿${sort?.left ?: "Antes"} o ${sort?.right ?: "Después"}? Cada carta correcta vale 250.",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sort?.items?.chunked(2)?.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { item ->
                                    val index = sort?.items?.indexOf(item) ?: -1
                                    val correct = sort?.answers?.getOrNull(index)
                                    TvModePanel(modifier = Modifier.weight(1f), accent = accent) {
                                        Text(item.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (reveal && correct != null) {
                                            Text(
                                                if (correct == 0) sort?.left ?: "Antes" else sort?.right ?: "Después",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = VisionSuccess,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Enviaron: ${snapshot.answeredCount} de ${snapshot.players.count { it.connected }}", color = VisionTextSecondary)
                }
                "assault" -> {
                    val assault = trivia.assault
                    Text("El asalto final", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = VisionRatingGold)
                    Text(
                        assault?.prompt.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        assault?.items?.chunked(2)?.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { item ->
                                    val index = assault?.items?.indexOf(item) ?: -1
                                    val correct = assault?.answers?.contains(index) == true
                                    TvModePanel(modifier = Modifier.weight(1f), accent = VisionRatingGold) {
                                        Text(item.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (reveal) {
                                            Text(
                                                if (correct) "ESTRENÓ ANTES" else "DESPUÉS",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (correct) VisionSuccess else VisionTextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Sellaron su asalto: ${snapshot.answeredCount} de ${snapshot.players.count { it.connected }}", color = VisionTextSecondary)
                }
                else -> {
                    if (question != null) {
                        Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (question.art.isNotBlank()) {
                            AsyncImage(
                                model = question.art,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp)).background(VisionSurface),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            question.options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowOptions.forEachIndexed { colIndex, option ->
                                        val index = rowIndex * 2 + colIndex
                                        TvGameOption(
                                            index = index,
                                            text = option,
                                            correct = reveal && index == (snapshot.answer?.option ?: -1),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Pregunta en camino…", color = VisionTextSecondary)
                    }
                    if (reveal) {
                        Text(
                            "Respuesta correcta: ${snapshot.answer?.text.orEmpty()}",
                            style = MaterialTheme.typography.titleLarge,
                            color = VisionSuccess,
                        )
                    }
                    Text(
                        "Respondieron: ${snapshot.answeredCount} de ${snapshot.players.count { it.connected }}",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                    )
                    if (trivia.attack.used.isNotEmpty()) {
                        Text(
                            trivia.attack.used.joinToString(" · ") { "${it.byName} → ${it.targetName}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionLiveRed,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvCompactAction("Cambiar juego", onClick = onBack)
                Text(
                    "La tele no revela la respuesta hasta el final.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        Column(
            modifier = Modifier.weight(0.65f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NarratorView(
                mood = when (snapshot.phase) {
                    "punishment", "punishment_reveal" -> NarratorMood.Oops
                    "assault" -> NarratorMood.Celebrate
                    "reveal" -> NarratorMood.Happy
                    else -> NarratorMood.Thinking
                },
                size = 150.dp,
                animated = true,
                alive = true,
            )
            NarratorBubble("El piso se quema. La racha ataca.")
            Text("Marcador", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
            snapshot.players.take(8).forEach { player ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(player.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${player.score}", style = MaterialTheme.typography.titleMedium, color = VisionRatingGold)
                }
            }
        }
    }
}

@Composable
private fun TvWagerV2(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val wager = snapshot.wager ?: return
    val reveal = snapshot.phase == "reveal"
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val accent = Color(GameCatalog.Wager.accent)
    val question = snapshot.question
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.35f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameStageBadge(if (reveal) "RESULTADO" else "PISTA ${wager.stage} DE 3", accent)
                Spacer(Modifier.width(10.dp))
                Text("Paga x${wager.factor}", style = MaterialTheme.typography.titleMedium, color = VisionRatingGold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (reveal) "Resultado" else "$seconds s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (reveal) VisionTextSecondary else accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!reveal) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(VisionSurfaceHigh),
                ) {
                    Box(
                        Modifier.fillMaxWidth(
                            (seconds.toFloat() / snapshot.phaseDuration.coerceAtLeast(1).toFloat())
                                .coerceIn(0f, 1f),
                        ).fillMaxHeight().background(accent),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("POZO", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
                Spacer(Modifier.width(12.dp))
                Text("${wager.pot}", style = MaterialTheme.typography.displayMedium, color = VisionRatingGold, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(
                    "Ciega ${wager.ante} · Pista +${wager.buyCost}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
            if (wager.rollover > 0) {
                Text("Rollover: ${wager.rollover}", color = VisionRatingGold)
            }
            if (question != null) {
                if (question.art.isNotBlank()) {
                    AsyncImage(
                        model = question.art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .graphicsLayer(
                                alpha = when (wager.stage) {
                                    1 -> 0.35f
                                    2 -> 0.7f
                                    else -> 1f
                                },
                            )
                            .background(VisionSurface),
                    )
                }
                if (wager.clue.isNotBlank()) {
                    Text(wager.clue, style = MaterialTheme.typography.titleLarge, color = VisionTextSecondary)
                }
                if (reveal) {
                    Text(
                        "La respuesta era: ${snapshot.answer?.text.orEmpty()}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = VisionSuccess,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        question.options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowOptions.forEachIndexed { colIndex, option ->
                                    val index = rowIndex * 2 + colIndex
                                    TvGameOption(
                                        index = index,
                                        text = option,
                                        correct = false,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (wager.lockedStage1.isNotEmpty()) {
                Text(
                    "Bloquearon en pista 1: " + wager.lockedStage1.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionBlueBright,
                )
            }
            if (wager.sideBets.isNotEmpty()) {
                Text(
                    "Apuestas laterales: " + wager.sideBets.joinToString(" · ") {
                        "${it.name} → ${it.targetName} (${it.amount})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
            }
            if (reveal) {
                wager.sideResults.forEach { result ->
                    Text(
                        "${result.name}: ${if (result.delta > 0) "+" else ""}${result.delta}",
                        color = if (result.delta > 0) VisionSuccess else VisionLiveRed,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvCompactAction("Cambiar juego", onClick = onExit)
                Text(
                    "La tele no revela la respuesta hasta el final.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        Column(
            modifier = Modifier.weight(0.65f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NarratorView(
                mood = if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
                size = 150.dp,
                animated = true,
                alive = true,
            )
            NarratorBubble(if (reveal) "Se abrió la bóveda." else "Pista ${wager.stage}. Paga x${wager.factor}.")
            Text("Marcador", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
            snapshot.players.take(8).forEach { player ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(player.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${player.score}", style = MaterialTheme.typography.titleMedium, color = VisionRatingGold)
                }
            }
        }
    }
}

@Composable
private fun TvWager(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val wager = snapshot.wager ?: return
    val question = snapshot.question
    val reveal = snapshot.phase == "reveal"
    val betting = snapshot.phase == "wager"
    val clue = snapshot.phase == "clue"
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val accent = Color(GameCatalog.Wager.accent)
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.35f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameStageBadge(
                    when {
                        reveal -> "RESULTADO"
                        betting -> "APUESTA"
                        else -> "PISTA ${wager.clueIndex} DE ${wager.clueCount}"
                    },
                    accent,
                )
                Spacer(Modifier.width(10.dp))
                if (!betting) {
                    Text("Paga x${wager.factor}", style = MaterialTheme.typography.titleMedium, color = VisionRatingGold)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (reveal) "Resultado" else "$seconds s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (reveal) VisionTextSecondary else accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (betting) {
                Text(
                    "Hagan sus apuestas. La primera pista vale el triple.",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    "Acertar en la pista 1 paga x3; en la 2, x2; en la 3, x1. Si falla, pierde la apuesta.",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionTextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Apuestas recibidas: ${snapshot.players.count { it.wager > 0 }} de ${snapshot.players.count { it.connected }}",
                    style = MaterialTheme.typography.titleLarge,
                    color = VisionTextPrimary,
                )
            } else if (question != null) {
                Text(
                    if (clue) "Pista ${wager.clueIndex} de ${wager.clueCount}" else "La respuesta era",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionTextSecondary,
                )
                Text(
                    if (reveal) snapshot.answer?.text.orEmpty() else question.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reveal) VisionSuccess else VisionTextPrimary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (question.art.isNotBlank()) {
                    AsyncImage(
                        model = question.art,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(VisionSurface),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    question.options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowOptions.forEachIndexed { colIndex, option ->
                                val index = rowIndex * 2 + colIndex
                                TvGameOption(
                                    index = index,
                                    text = option,
                                    correct = reveal && index == (snapshot.answer?.option ?: -1),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                if (reveal && wager.poster.isNotBlank()) {
                    AsyncImage(
                        model = wager.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(VisionSurface),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvCompactAction("Cambiar juego", onClick = onExit)
                if (!reveal) {
                    Text(
                        "La tele no revela la respuesta hasta el final.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(0.65f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NarratorView(
                mood = if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking,
                size = 150.dp,
                animated = true,
                alive = true,
            )
            val narWagerReveal = rememberNarratorText(NarratorEvent.WagerReveal, snapshot.narrKey())
            val narWagerStart = rememberNarratorText(NarratorEvent.WagerStart, snapshot.narrKey())
            NarratorBubble(
                when {
                    reveal -> narWagerReveal
                    betting -> narWagerStart
                    else -> "Una pista más. Paga x${wager.factor}. Piénsenle."
                },
            )
            Text("Marcador", style = MaterialTheme.typography.titleMedium, color = VisionTextSecondary)
            snapshot.players.take(8).forEach { player ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (player.wagerDone) {
                        Text("LISTO", style = MaterialTheme.typography.labelSmall, color = VisionSuccess)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionRatingGold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvGamePodium(
    snapshot: GameSnapshot,
    newFocus: FocusRequester,
    onNewGame: () -> Unit,
    onChangeGame: () -> Unit,
    onBack: () -> Unit,
) {
    val podiumContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(snapshot.game) {
        if (snapshot.isFiesta) FiestaVoice.play(podiumContext, snapshot.game, "podium")
    }
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
        Column(
            modifier = Modifier.weight(0.8f).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (snapshot.isFiesta) {
                FiestaHostAvatar(snapshot.game, 220.dp)
            } else {
                NarratorView(NarratorMood.Celebrate, size = 220.dp, animated = true, alive = true)
            }
            Text(
                "Podio",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = VisionTextPrimary,
            )
            if (snapshot.isFiesta) {
                FiestaHostBubble(
                    gameId = snapshot.game,
                    text = snapshot.fiesta?.hostCue?.text.orEmpty(),
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            } else {
                Text(
                    "Gracias por jugar. Invita los tacos.",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionTextSecondary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1.2f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            snapshot.players.forEachIndexed { index, player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (index == 0) VisionBlue.copy(alpha = 0.28f) else VisionSurface)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (index == 0) VisionRatingGold else VisionTextSecondary,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = VisionTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${player.score}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = VisionBlueBright,
                    )
                }
            }
            if (snapshot.highlights.isNotEmpty()) {
                Text(
                    "MOMENTOS DESTACADOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = VisionRatingGold,
                    letterSpacing = 1.5.sp,
                )
                snapshot.highlights.take(2).forEach { highlight ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VisionRatingGold.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(medalIconRes(highlight.id)),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            highlight.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = VisionRatingGold,
                            modifier = Modifier.width(190.dp),
                        )
                        Text(
                            highlight.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = VisionTextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            highlight.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvCompactAction(
                    "Nueva partida",
                    primary = true,
                    modifier = Modifier.focusRequester(newFocus),
                    onClick = onNewGame,
                )
                TvCompactAction("Cambiar juego", onClick = onChangeGame)
                TvCompactAction("Salir", onClick = onBack)
            }
        }
        }
        LightweightParticleOverlay(
            sprite = ImageBitmap.imageResource(R.drawable.game_fx_confetti),
            active = true,
            particleCount = 18,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal fun tvRemainingSeconds(snapshot: GameSnapshot, nowMs: Long, snapshotAtMs: Long): Int {
    if (snapshot.phaseEndsAt <= 0L) return 0
    val elapsed = if (snapshotAtMs > 0L) (nowMs - snapshotAtMs).coerceAtLeast(0L) else 0L
    val serverNow = snapshot.serverNow + elapsed
    return ((snapshot.phaseEndsAt - serverNow).coerceAtLeast(0L) / 1000L).toInt()
}

private fun tvPlayerColor(index: Int): Color = when (index.mod(8)) {
    0 -> VisionBlue
    1 -> VisionBlueBright
    2 -> VisionSuccess
    3 -> VisionRatingGold
    4 -> Color(0xFF8B5CF6)
    5 -> Color(0xFFEF4444)
    6 -> Color(0xFF06B6D4)
    else -> Color(0xFFF06292)
}
