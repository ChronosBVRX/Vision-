package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.games.GameAuction
import com.chronosbvrx.visionplus.data.games.GameChoice
import com.chronosbvrx.visionplus.data.games.GameDrawing
import com.chronosbvrx.visionplus.data.games.GameFibbage
import com.chronosbvrx.visionplus.data.games.GameImpostor
import com.chronosbvrx.visionplus.data.games.GameSnapshot
import com.chronosbvrx.visionplus.data.games.GameStroke
import com.chronosbvrx.visionplus.data.games.NarratorEvent
import com.chronosbvrx.visionplus.data.games.NarratorMood
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.tv.TvCompactAction
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

@Composable
internal fun TvModePanel(
    modifier: Modifier = Modifier,
    accent: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(VisionSurface.copy(alpha = 0.94f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun TvModeHeader(snapshot: GameSnapshot, accent: Color, label: String, seconds: Int, status: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "RONDA ${snapshot.round} DE ${snapshot.rounds}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = VisionTextPrimary,
                    fontWeight = FontWeight.Black,
                )
            }
            if (status.isNotBlank()) {
                Text(status, color = VisionTextSecondary, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(18.dp))
            Text(
                if (seconds > 0) "$seconds" else "—",
                style = MaterialTheme.typography.displaySmall,
                color = if (seconds <= 5) VisionLiveRed else accent,
                fontWeight = FontWeight.Black,
            )
        }
        if (snapshot.phaseDuration > 0 && seconds >= 0 && snapshot.phase !in listOf("reveal", "auction_reveal")) {
            val fraction = (seconds.toFloat() / snapshot.phaseDuration.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VisionSurfaceHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun TvScoreStrip(snapshot: GameSnapshot, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("MARCADOR", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
        snapshot.players.take(8).forEach { player ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(tvModePlayerColor(player.color), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    player.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = VisionTextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${player.score}",
                    style = MaterialTheme.typography.titleMedium,
                    color = VisionRatingGold,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (snapshot.audience > 0) {
            Text("+ ${snapshot.audience} en el público", color = accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun tvModePlayerColor(index: Int): Color = when (index.mod(8)) {
    0 -> VisionBlue
    1 -> VisionBlueBright
    2 -> VisionSuccess
    3 -> VisionRatingGold
    4 -> Color(0xFF8B5CF6)
    5 -> Color(0xFFEF4444)
    6 -> Color(0xFF06B6D4)
    else -> Color(0xFFF06292)
}

@Composable
private fun TvModeOption(
    text: String,
    index: Int,
    reveal: Boolean,
    isReal: Boolean,
    votes: Int,
    author: String,
    modifier: Modifier = Modifier,
) {
    val container = when {
        reveal && isReal -> VisionSuccess
        else -> VisionSurfaceHigh
    }
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(
                2.dp,
                if (reveal && isReal) VisionSuccess else VisionBlue.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                "${('A' + index)}.  $text",
                style = MaterialTheme.typography.titleMedium,
                color = if (reveal && isReal) VisionBlack else VisionTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (reveal && (author.isNotBlank() || votes > 0)) {
                Text(
                    buildString {
                        if (isReal) append("LA VERDADERA")
                        else if (author.isNotBlank()) append("Mentira de $author")
                        if (votes > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$votes votos")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reveal && isReal) VisionBlack.copy(alpha = 0.75f) else VisionTextSecondary,
                )
            }
        }
    }
}

@Composable
fun TvImpostor(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val data = snapshot.impostor ?: return
    val accent = Color(0xFFF43F5E)
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val reveal = snapshot.phase == "reveal"
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TvModePanel(Modifier.weight(1.35f).fillMaxHeight(), accent) {
            TvModeHeader(
                snapshot, accent,
                when (snapshot.phase) {
                    "secret" -> "REPARTO SECRETO"
                    "questions" -> "PREGUNTAS RÁPIDAS"
                    "coartada" -> "COARTADA RÁPIDA"
                    "vote" -> "¿QUIÉN MIENTE?"
                    "guess" -> "ÚLTIMA OPORTUNIDAD"
                    else -> "EL IMPOSTOR"
                },
                seconds,
                if (snapshot.phase == "vote") "${data.votedCount}/${data.voterCount} votos" else "",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.16f), VisionSurfaceHigh)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(data.category.uppercase(), color = accent, style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
                            snapshot.phase == "secret" -> "Mira tu teléfono. El lugar es secreto."
                            snapshot.phase == "questions" -> "${data.askerName} pregunta a ${data.targetName}"
                            snapshot.phase == "coartada" -> "¿Qué tan ruidoso es este lugar?"
                            snapshot.phase == "vote" -> "Todos votan en su teléfono"
                            snapshot.phase == "guess" -> "El impostor intenta adivinar"
                            else -> "Se revela la verdad"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = VisionTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                NarratorView(if (reveal) NarratorMood.Celebrate else NarratorMood.Thinking, size = 96.dp, alive = true)
            }
            if (snapshot.phase == "questions") {
                Text(
                    data.question,
                    style = MaterialTheme.typography.headlineSmall,
                    color = VisionTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Turno ${data.speakerIndex} de ${data.speakerCount} · ${data.askerName} interroga a ${data.targetName}",
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (snapshot.phase == "coartada") {
                Text(
                    "Todos responden en secreto: ${data.coartadaCount} de ${data.voterCount}",
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "El impostor no sabe el lugar; tiene que atinarle al vibe.",
                    color = accent,
                )
            }
            if (snapshot.phase == "vote") {
                Text("Nadie sabe quién es. Las caras hablan.", color = VisionTextSecondary)
                if (data.coartadaValues.isNotEmpty()) {
                    Text(
                        "Coartada: " + data.coartadaValues.joinToString(" · ") {
                            "${it.name} ${it.value + 1}/10"
                        },
                        color = VisionTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    snapshot.players.take(8).forEach { player ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(50),
                            color = VisionSurfaceHigh,
                            modifier = Modifier.padding(2.dp),
                        ) {
                            Text(
                                player.name,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = VisionTextPrimary,
                            )
                        }
                    }
                }
            }
            if (snapshot.phase == "guess") {
                Text("El impostor elige en su teléfono. La tele solo muestra las opciones.", color = VisionTextSecondary)
                data.guessOptions.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIndex, option ->
                            TvModeOption(option, rowIndex * 2 + colIndex, false, false, 0, "", Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (reveal) {
                Text(
                    if (data.caught) "Impostor descubierto: ${data.impostorName}" else "El impostor era ${data.impostorName}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (data.caught) VisionSuccess else VisionLiveRed,
                    fontWeight = FontWeight.Black,
                )
                Text("El lugar secreto era «${data.word}»", style = MaterialTheme.typography.titleLarge, color = VisionTextPrimary)
                if (data.accompliceName.isNotBlank()) {
                    Text("Cómplice: ${data.accompliceName}", color = VisionLiveRed)
                }
                if (data.detectiveName.isNotBlank()) {
                    Text("Detective: ${data.detectiveName}", color = VisionBlueBright)
                }
                Text(
                    when {
                        data.caught && data.guessCorrect -> "Adivinó el lugar y robó la ronda."
                        data.caught -> "Falló la adivinanza. El equipo gana."
                        else -> "Se escapó con la gloria."
                    },
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarratorBubble(
                    if (reveal) rememberNarratorText(NarratorEvent.Reveal, snapshot.narrKey())
                    else "Los teléfonos guardan los secretos. La tele solo pone la tensión.",
                )
                Spacer(Modifier.weight(1f))
                TvCompactAction("Cambiar juego", onClick = onExit)
            }
        }
        TvModePanel(Modifier.weight(0.65f).fillMaxHeight(), accent) {
            NarratorView(NarratorMood.Thinking, size = 110.dp, alive = true)
            NarratorBubble(
                if (reveal) "La verdad sale a la luz." else "Uno de ustedes no sabe dónde está.",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            TvScoreStrip(snapshot, accent)
        }
    }
}

private fun normalizedStroke(stroke: GameStroke): NormalizedStroke = NormalizedStroke(
    points = stroke.points.map { NormalizedPoint(it.x, it.y) },
    colorHex = runCatching {
        android.graphics.Color.parseColor(stroke.color).toLong() and 0xFFFFFFFFL
    }.getOrDefault(0xFFFFFFFFL),
    width = stroke.width,
)

@Composable
fun TvDibujo(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val data = snapshot.drawing ?: return
    val accent = Color(0xFF06B6D4)
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val reveal = snapshot.phase == "reveal"
    val previewStrokes = data.strokes
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TvModePanel(Modifier.weight(1.35f).fillMaxHeight(), accent) {
            TvModeHeader(
                snapshot, accent,
                when (snapshot.phase) {
                    "draw" -> "EL LIENZO SECRETO"
                    "title" -> "TÍTULOS FALSOS"
                    "vote" -> "VOTEN EL TÍTULO REAL"
                    else -> "LA VERDAD DEL DIBUJO"
                },
                seconds,
                if (snapshot.phase == "vote") "${data.votedCount}/${data.voterCount} votos" else "",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VisionBlack)
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (previewStrokes.isNotEmpty()) {
                    AnimatedVectorDrawingCanvas(
                        strokes = previewStrokes.map(::normalizedStroke),
                        flashlightEnabled = data.flashlight && snapshot.phase == "draw",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NarratorView(NarratorMood.Thinking, size = 120.dp, alive = true)
                        Text(
                            if (snapshot.phase == "draw") "${data.artistName} está dibujando…" else "Esperando el dibujo…",
                            color = VisionTextSecondary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (snapshot.phase == "draw" && previewStrokes.isNotEmpty()) {
                    Text(
                        "PINTANDO EN VIVO · VISTA PREVIA",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(VisionBlack.copy(alpha = 0.72f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (snapshot.phase == "vote" && data.options.isNotEmpty()) {
                data.options.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIndex, choice ->
                            TvModeOption(
                                choice.text, rowIndex * 2 + colIndex, false, false, choice.votes, choice.authorName,
                                Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (reveal) {
                Text("La frase real: ${data.prompt}", color = VisionSuccess, style = MaterialTheme.typography.titleLarge)
                data.options.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIndex, choice ->
                            TvModeOption(
                                choice.text, rowIndex * 2 + colIndex, true, choice.isReal, choice.votes, choice.authorName,
                                Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (data.audienceBonusPlayerId.isNotBlank()) {
                    Text(
                        "FAVORITO DEL PÚBLICO: " +
                            (snapshot.players.firstOrNull { it.id == data.audienceBonusPlayerId }?.name ?: "LA SALA") +
                            " (+300)",
                        color = Color(0xFFF59E0B),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarratorBubble(
                    if (reveal) "Las mentiras también dan puntos. Qué injusto y qué divertido."
                    else "Nadie sabe el título. Todos van a mentir.",
                )
                Spacer(Modifier.weight(1f))
                TvCompactAction("Cambiar juego", onClick = onExit)
            }
        }
        TvModePanel(Modifier.weight(0.65f).fillMaxHeight(), accent) {
            Text("ARTISTA", style = MaterialTheme.typography.labelMedium, color = accent)
            Text(data.artistName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                when (snapshot.phase) {
                    "draw" -> "Dibuja en su teléfono. Sin letras."
                    "title" -> "Los demás inventan un título."
                    "vote" -> "Todos votan en su teléfono."
                    else -> "El lienzo ya tiene dueño."
                },
                color = VisionTextSecondary,
            )
            if (data.audienceVotes > 0) {
                Text("Público votando: ${data.audienceVotes}", color = accent)
            }
            Spacer(Modifier.height(8.dp))
            TvScoreStrip(snapshot, accent)
        }
    }
}

@Composable
fun TvSubasta(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val data = snapshot.auction ?: return
    val accent = Color(0xFFF59E0B)
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val reveal = snapshot.phase == "auction_reveal"
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TvModePanel(Modifier.weight(1.35f).fillMaxHeight(), accent) {
            TvModeHeader(
                snapshot, accent,
                if (reveal) "MARTILLO" else "MERCADO NEGRO",
                seconds,
                if (!reveal) "${data.bidsCount}/${data.bidCount} pujas" else "",
            )
            val hammerAngle by animateFloatAsState(
                targetValue = if (reveal) 0f else -42f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "hammerSwing",
            )
            if (data.secondPays) {
                Text("LOTE MALDITO: el segundo lugar también paga su puja", color = VisionLiveRed, fontWeight = FontWeight.Bold)
            }
            if (data.ghostBidderName.isNotBlank()) {
                Text("FALSA ALARMA: ${data.ghostBidderName} dice que alguien pujó alto", color = VisionBlueBright, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    VisionIcons.Auction,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer(rotationZ = hammerAngle),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (reveal) "Se cierra la puja" else "Pujas a ciegas, martillo al final",
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.24f), VisionSurfaceHigh, VisionBlack),
                        ),
                    )
                    .border(2.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(auctionCardArtRes(data.effect, data.mystery)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(112.dp)
                            .height(168.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Text("LOTE ${snapshot.round}", style = MaterialTheme.typography.labelLarge, color = accent)
                    Text(
                        data.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = VisionTextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        data.description,
                        style = MaterialTheme.typography.titleMedium,
                        color = VisionTextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            data.mystery && !reveal -> "CONTENIDO: MISTERIO"
                            data.effect == "points" -> "BOTÍN: +${data.value} PUNTOS"
                            data.effect == "coins" -> "BOTÍN: +${data.value} MONEDAS"
                            data.effect == "steal" -> "BOTÍN: ROBAR 25% AL LÍDER"
                            data.effect == "double_next" -> "BOTÍN: DUPLICA TU PRÓXIMO LOTE"
                            data.effect == "shield" -> "BOTÍN: ESCUDO CONTRA ROBOS"
                            else -> "BOTÍN: SORPRESA"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    if (reveal) {
                        Text(
                            if (data.winnerName.isBlank()) "SIN GANADOR" else "¡MARTILLO! ${data.winnerName.uppercase()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (data.winnerName.isBlank()) VisionTextSecondary else VisionSuccess,
                            fontWeight = FontWeight.Black,
                        )
                        if (data.tie) {
                            Text(
                                "Empate cancelado: ${data.cancelled.joinToString()}",
                                color = VisionLiveRed,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            if (data.winnerName.isBlank()) "Nadie se llevó la carta." else
                                "Precio: ${data.price} monedas · ${data.applied}",
                            color = VisionTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Text("Pujas secretas en los teléfonos…", color = VisionTextSecondary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarratorBubble(
                    if (reveal) "El martillo no perdona. Y los empates menos."
                    else "El valor está en la carta. El farol está en ustedes.",
                )
                Spacer(Modifier.weight(1f))
                TvCompactAction("Cambiar juego", onClick = onExit)
            }
        }
        TvModePanel(Modifier.weight(0.65f).fillMaxHeight(), accent) {
            if (reveal && data.bids.isNotEmpty()) {
                Text("PUJAS", style = MaterialTheme.typography.labelMedium, color = accent)
                data.bids.take(8).forEach { bid ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(bid.name, Modifier.weight(1f), color = VisionTextPrimary)
                        Text("${bid.bid}", color = VisionRatingGold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            TvScoreStrip(snapshot, accent)
        }
    }
}

@Composable
fun TvFibbage(
    snapshot: GameSnapshot,
    tick: Long,
    snapshotAt: Long,
    onExit: () -> Unit,
) {
    val data = snapshot.fibbage ?: return
    val accent = Color(0xFF10B981)
    val seconds = tvRemainingSeconds(snapshot, tick, snapshotAt)
    val reveal = snapshot.phase == "reveal"
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TvModePanel(Modifier.weight(1.35f).fillMaxHeight(), accent) {
            TvModeHeader(
                snapshot, accent,
                when (snapshot.phase) {
                    "write" -> "INVENTEN UNA MENTIRA"
                    "vote" -> "¿CUÁL ES LA VERDAD?"
                    else -> "LA VERDAD ERA"
                },
                seconds,
                if (snapshot.phase == "vote") "${data.votedCount}/${data.voterCount} votos" else "",
            )
            Text(
                data.prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = VisionTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (data.hint.isNotBlank() && !reveal) {
                Text("Pista: ${data.hint}", color = accent, style = MaterialTheme.typography.titleMedium)
            }
            if (snapshot.phase == "write") {
                Text(
                    "Mentiras enviadas: ${data.submittedCount} de ${data.voterCount}",
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(VisionSurfaceHigh.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NarratorView(NarratorMood.Thinking, size = 130.dp, alive = true)
                        Text("Los teléfonos están inventando…", color = VisionTextSecondary)
                    }
                }
            }
            if (snapshot.phase == "vote" && data.options.isNotEmpty()) {
                data.options.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIndex, choice ->
                            TvModeOption(choice.text, rowIndex * 2 + colIndex, false, false, 0, "", Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (reveal) {
                Text(
                    "La verdad: ${data.answer}",
                    color = VisionSuccess,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                data.options.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIndex, choice ->
                            TvModeOption(
                                choice.text, rowIndex * 2 + colIndex, true, choice.isReal, choice.votes, choice.authorName,
                                Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (data.audienceBonusPlayerId.isNotBlank()) {
                    Text(
                        "FAVORITO DEL PÚBLICO: " +
                            (snapshot.players.firstOrNull { it.id == data.audienceBonusPlayerId }?.name ?: "LA SALA") +
                            " (+300)",
                        color = Color(0xFFF59E0B),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarratorBubble(
                    if (reveal) "Si mordieron tu mentira, cobras. Así de cruel."
                    else "Si no sabes, inventa bonito.",
                )
                Spacer(Modifier.weight(1f))
                TvCompactAction("Cambiar juego", onClick = onExit)
            }
        }
        TvModePanel(Modifier.weight(0.65f).fillMaxHeight(), accent) {
            Text(data.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent)
            Text("Dato insólito", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                if (snapshot.phase == "write") "Todos inventan una respuesta." else "Todos votan la que creen real.",
                color = VisionTextSecondary,
            )
            if (data.audienceVotes > 0) {
                Text("Público votando: ${data.audienceVotes}", color = accent)
            }
            Spacer(Modifier.height(8.dp))
            TvScoreStrip(snapshot, accent)
        }
    }
}
