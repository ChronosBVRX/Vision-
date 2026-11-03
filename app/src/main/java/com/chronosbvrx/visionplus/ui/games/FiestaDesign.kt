package com.chronosbvrx.visionplus.ui.games

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.data.games.FiestaIncomingTaunt
import com.chronosbvrx.visionplus.data.games.FiestaState
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/** Las seis identidades visuales de Vision+ Fiesta. */
enum class FiestaShell { Stage, Newsroom, Timelab, Chat, Runway, Auction }

data class FiestaLook(
    val shell: FiestaShell,
    val accent: Color,
    val accent2: Color,
    val backdrop: Color,
    val ink: Color,
    val panel: Color,
    val display: FontFamily,
    val corner: Dp,
    val currency: String,
    val currencyIcon: String,
    val shop: String,
    val eyebrow: String,
    val tagline: String,
    val hostName: String,
    @DrawableRes val hostRes: Int,
    @DrawableRes val phoneRes: Int,
    @DrawableRes val stageRes: Int,
)

fun fiestaColor(hex: String, fallback: Color = Color.White): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

fun fiestaLook(game: String?): FiestaLook = when (game) {
    "vox_populi" -> FiestaLook(
        FiestaShell.Newsroom,
        fiestaColor("#22D3EE"), fiestaColor("#FACC15"), fiestaColor("#02131C"),
        fiestaColor("#ECFEFF"), fiestaColor("#062A36"), FontFamily.SansSerif, 6.dp,
        "Rating", "TV", "La Mesa de Control", "VOX POPULI · NOTICIAS",
        "La calle habló. Y dijo cosas muy raras.", "Don Chisme",
        R.drawable.fiesta_host_vox_populi, R.drawable.fiesta_phone_vox_populi,
        R.drawable.fiesta_stage_vox_populi,
    )
    "fuera_de_epoca" -> FiestaLook(
        FiestaShell.Timelab,
        fiestaColor("#A78BFA"), fiestaColor("#F472B6"), fiestaColor("#0D061F"),
        fiestaColor("#F5F3FF"), fiestaColor("#251446"), FontFamily.Monospace, 4.dp,
        "Cronones", "TT", "Chatarra Temporal", "FUERA DE ÉPOCA · ARCHIVO TEMPORAL",
        "El pasado no se acuerda de ustedes.", "Profesor Tic Tac",
        R.drawable.fiesta_host_fuera_de_epoca, R.drawable.fiesta_phone_fuera_de_epoca,
        R.drawable.fiesta_stage_fuera_de_epoca,
    )
    "texto_roto" -> FiestaLook(
        FiestaShell.Chat,
        fiestaColor("#34D399"), fiestaColor("#22D3EE"), fiestaColor("#03140F"),
        fiestaColor("#ECFDF5"), fiestaColor("#06231B"), FontFamily.SansSerif, 18.dp,
        "Datos", "01", "Modo Dios", "TEXTO ROTO · CHAT EN VIVO",
        "La conversación empieza bien y termina como siempre: mal.", "La Máquina",
        R.drawable.fiesta_host_texto_roto, R.drawable.fiesta_phone_texto_roto,
        R.drawable.fiesta_stage_texto_roto,
    )
    "franela_fest" -> FiestaLook(
        FiestaShell.Runway,
        fiestaColor("#F472B6"), fiestaColor("#FBBF24"), fiestaColor("#1B0614"),
        fiestaColor("#FFF1F7"), fiestaColor("#3B1028"), FontFamily.Serif, 4.dp,
        "Hilos", "HF", "La Boutique del Mal Gusto", "FRANELA · EDICIÓN ESPECIAL",
        "La moda es subjetiva. El ridículo es universal.", "El Crítico",
        R.drawable.fiesta_host_franela_fest, R.drawable.fiesta_phone_franela_fest,
        R.drawable.fiesta_stage_franela_fest,
    )
    "el_tasador" -> FiestaLook(
        FiestaShell.Auction,
        fiestaColor("#FBBF24"), fiestaColor("#FB923C"), fiestaColor("#1A1003"),
        fiestaColor("#FFF8E7"), fiestaColor("#3A2706"), FontFamily.Serif, 3.dp,
        "Fichas", "$$", "La Bodega de Objetos Malditos", "EL TASADOR · CASA DE SUBASTAS",
        "Todo tiene un precio. Nadie sabe cuál.", "El Tasador",
        R.drawable.fiesta_host_el_tasador, R.drawable.fiesta_phone_el_tasador,
        R.drawable.fiesta_stage_el_tasador,
    )
    else -> FiestaLook(
        FiestaShell.Stage,
        fiestaColor("#F97316"), fiestaColor("#FBBF24"), fiestaColor("#1A0704"),
        fiestaColor("#FFF7ED"), fiestaColor("#3A1608"), FontFamily.SansSerif, 10.dp,
        "Risas", "JA", "La Trastienda de Nora", "LA ÚLTIMA PALABRA · EN VIVO",
        "La respuesta más rara suele ser la que gana.", "Nora Vela",
        R.drawable.fiesta_host_ultima_palabra, R.drawable.fiesta_phone_ultima_palabra,
        R.drawable.fiesta_stage_ultima_palabra,
    )
}

@Composable
fun FiestaStageBackdrop(look: FiestaLook, modifier: Modifier = Modifier, dim: Float = 0.35f) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(look.stageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        look.backdrop.copy(alpha = 0.62f),
                        look.backdrop.copy(alpha = 0.10f),
                        look.backdrop.copy(alpha = if (dim > 0.5f) 0.86f else 0.74f),
                    ),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(look.backdrop.copy(alpha = dim * 0.45f)))
        FiestaAtmosphere(look)
    }
}

/** Lobby de selección: salón de fiesta IA, no un degradado procedural. */
@Composable
fun FiestaLobbyBackdrop(modifier: Modifier = Modifier, accent: Color = Color.White) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.fiesta_lobby_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        VisionBlack.copy(alpha = 0.38f),
                        VisionBlack.copy(alpha = 0.22f),
                        VisionBlack.copy(alpha = 0.88f),
                    ),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.10f), Color.Transparent, accent.copy(alpha = 0.06f)),
                ),
            ),
        )
        FiestaAtmosphere(
            FiestaLook(
                FiestaShell.Stage, accent, accent, VisionBlack, VisionTextPrimary,
                VisionBlack, FontFamily.SansSerif, 12.dp, "", "", "", "", "",
                "", 0, 0, R.drawable.fiesta_lobby_bg,
            ),
        )
    }
}

@Composable
fun FiestaPhoneBackdrop(look: FiestaLook, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(look.phoneRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        look.backdrop.copy(alpha = 0.56f),
                        look.backdrop.copy(alpha = 0.76f),
                        look.backdrop.copy(alpha = 0.96f),
                    ),
                ),
            ),
        )
        FiestaAtmosphere(look, phone = true)
    }
}

/** Chispas, scanlines y un latido de luz: barato incluso en Chromecast. */
@Composable
private fun FiestaAtmosphere(look: FiestaLook, phone: Boolean = false) {
    val reduced = LocalReducedEffects.current
    val transition = rememberInfiniteTransition(label = "fiesta-atmosphere")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reduced) 24000 else 9000), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val particles = if (phone) 14 else 30
        for (i in 0 until particles) {
            val seed = i * 37.7f
            val x = ((sin(seed) * 0.5f + 0.5f) * size.width + drift * size.width * 0.03f * (i % 3)) % size.width
            val y = ((seed * 13.3f) % size.height + sin(drift * 6.28f + i) * 18f) % size.height
            val r = 1.5f + (i % 4)
            drawCircle(
                color = if (i % 3 == 0) look.accent2.copy(alpha = 0.20f) else look.accent.copy(alpha = 0.16f),
                radius = r,
                center = Offset(x, y),
            )
        }
        if (look.shell == FiestaShell.Timelab || look.shell == FiestaShell.Chat) {
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = look.accent.copy(alpha = 0.045f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += 5f
            }
        }
    }
}

@Composable
fun FiestaHostPortrait(look: FiestaLook, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(look.accent.copy(alpha = 0.30f), look.backdrop.copy(alpha = 0.85f)),
                ),
            )
            .border(2.dp, look.accent.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(look.hostRes),
            contentDescription = look.hostName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    }
}

@Composable
fun FiestaCurrencyChip(look: FiestaLook, coins: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(look.backdrop.copy(alpha = 0.72f))
            .border(1.dp, look.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(look.currencyIcon, color = look.accent2, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text("$coins ${look.currency}", color = look.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun FiestaTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = if (filled) 1f else 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = if (filled) VisionBlack else color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.8.sp,
        maxLines = 1,
    )
}

/** Ticker con desplazamiento real: cada juego lo usa con su propio lenguaje. */
@Composable
fun FiestaTicker(look: FiestaLook, text: String, modifier: Modifier = Modifier) {
    val reduced = LocalReducedEffects.current
    val transition = rememberInfiniteTransition(label = "ticker")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reduced) 60000 else 16000), RepeatMode.Restart),
        label = "ticker-shift",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(look.accent.copy(alpha = 0.16f)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "●",
                color = look.accent,
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text,
                color = look.ink.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                (1 + (shift * 9).toInt()).toString().padStart(2, '0'),
                color = look.accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun FiestaSectionLabel(text: String, look: FiestaLook, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = look.accent,
        fontFamily = look.display,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun FiestaBigTimer(
    look: FiestaLook,
    remaining: Int,
    modifier: Modifier = Modifier,
    label: String = "SEG",
) {
    val danger = remaining <= 5
    val color = if (danger) fiestaColor("#FF4B4B") else look.accent
    Box(
        modifier.size(86.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = Color.Black.copy(alpha = 0.35f))
            drawCircle(
                color = color.copy(alpha = 0.30f),
                style = Stroke(width = 5f),
            )
            val sweep = (remaining.coerceIn(0, 60) / 60f) * 360f
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = -sweep,
                useCenter = false,
                style = Stroke(width = 8f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                remaining.toString(),
                color = look.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 27.sp,
            )
            Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun FiestaHostBubble(
    look: FiestaLook,
    text: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    animated: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (animated) AnimatedHostAvatar(look, if (small) 54.dp else 74.dp)
        else FiestaHostPortrait(look, if (small) 54.dp else 74.dp)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(look.panel.copy(alpha = 0.94f))
                .border(1.dp, look.accent.copy(alpha = 0.38f),
                    RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                look.hostName.uppercase(),
                color = look.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
            Text(
                text,
                color = look.ink,
                fontSize = if (small) 12.sp else 13.sp,
                lineHeight = if (small) 15.sp else 17.sp,
                maxLines = if (small) 4 else 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FiestaTwistRibbon(look: FiestaLook, title: String, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(look.accent2.copy(alpha = 0.16f))
            .border(1.dp, look.accent2.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", color = look.accent2, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Spacer(Modifier.width(7.dp))
        Column {
            Text(title, color = look.accent2, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(description, color = look.ink.copy(alpha = 0.82f), fontSize = 10.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Marcador inferior compartido: legible, con apuestas y monedas visibles. */
@Composable
fun FiestaBottomScores(
    look: FiestaLook,
    info: FiestaState,
    modifier: Modifier = Modifier,
    maxPlayers: Int = 8,
) {
    val players = info.scoreboard.take(maxPlayers)
    if (players.isEmpty()) return
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(look.backdrop.copy(alpha = 0.82f))
            .border(1.dp, look.accent.copy(alpha = 0.25f), RoundedCornerShape(look.corner))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        players.forEachIndexed { index, player ->
            val leader = index == 0
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (leader) look.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (leader) "1" else "${index + 1}",
                    color = if (leader) look.accent2 else look.accent.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        player.name,
                        color = look.ink,
                        fontSize = 11.sp,
                        fontWeight = if (leader) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${player.score} pts · ${player.coins} ${look.currency}",
                        color = if (leader) look.accent2 else look.ink.copy(alpha = 0.56f),
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Sello de resultado que cada juego coloca a su manera. */
@Composable
fun FiestaStamp(text: String, color: Color, modifier: Modifier = Modifier, rotate: Float = -8f) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(3.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = color,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun FiestaProgressBar(look: FiestaLook, progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(look.accent, look.accent2))),
        )
    }
}

@Composable
fun FiestaMarquee(look: FiestaLook, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            color = look.ink,
            fontFamily = look.display,
            fontWeight = FontWeight.Black,
            fontSize = 30.sp,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = look.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FiestaSoftPanel(
    look: FiestaLook,
    modifier: Modifier = Modifier,
    alpha: Float = 0.90f,
    border: Float = 0.32f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(look.corner))
            .background(look.panel.copy(alpha = alpha))
            .border(1.dp, look.accent.copy(alpha = border), RoundedCornerShape(look.corner)),
    ) { content() }
}

@Composable
fun FiestaAccentText(
    text: String,
    look: FiestaLook,
    size: Int = 13,
    color: Color = look.ink,
    bold: Boolean = false,
    italic: Boolean = false,
    maxLines: Int = 4,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = (size + 4).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun FiestaWaitingLine(look: FiestaLook, label: String, submitted: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = look.accent,
            fontFamily = look.display,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            letterSpacing = 1.sp,
        )
        Text(
            "$submitted de $total listos",
            color = VisionTextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        FiestaProgressBar(
            look,
            if (total <= 0) 0f else submitted.toFloat() / total,
            Modifier.width(220.dp),
            height = 5.dp,
        )
    }
}

/** Burbujas de las burlas compradas; la tele las deja volar sin tapar el juego. */
@Composable
fun FiestaTauntCloud(look: FiestaLook, info: FiestaState, modifier: Modifier = Modifier) {
    val taunts = info.taunts.takeLast(4)
    if (taunts.isEmpty()) return
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        taunts.forEachIndexed { index, taunt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    taunt.name,
                    color = look.ink.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    taunt.text,
                    color = look.backdrop,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(fiestaColor(taunt.color, look.accent2))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun FiestaRoleBadge(look: FiestaLook, role: String, modifier: Modifier = Modifier) {
    if (role.isBlank()) return
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(look.accent.copy(alpha = 0.14f))
            .border(1.dp, look.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text("TU PAPEL", color = look.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(role, color = look.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun FiestaRevealLines(
    look: FiestaLook,
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    maxLines: Int = 7,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title.uppercase(),
            color = look.accent,
            fontFamily = look.display,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            letterSpacing = 1.sp,
        )
        lines.take(maxLines).forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}",
                    color = look.accent2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    modifier = Modifier.width(22.dp),
                )
                Text(
                    line,
                    color = look.ink,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun FiestaPrompter(look: FiestaLook, label: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(Brush.verticalGradient(listOf(look.panel.copy(alpha = 0.96f), look.panel.copy(alpha = 0.82f))))
            .border(1.dp, look.accent.copy(alpha = 0.28f), RoundedCornerShape(look.corner))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        FiestaSectionLabel(label, look)
        Text(
            text,
            color = look.ink,
            fontFamily = look.display,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 29.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FiestaMiniStat(label: String, value: String, look: FiestaLook, modifier: Modifier = Modifier, color: Color = look.ink) {
    Column(modifier) {
        Text(label, color = look.ink.copy(alpha = 0.48f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = color, fontFamily = look.display, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
fun FiestaDivider(look: FiestaLook, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(look.accent.copy(alpha = 0.18f)))
}


/**
 * Splat de sabotaje: la burla recibida aterriza encima de TODA la pantalla del
 * teléfono. El jugador la limpia con tres toques; el id descartado se recuerda
 * para no volver a abrirla con cada snapshot.
 */
@Composable
fun TauntSplatOverlay(look: FiestaLook, taunt: FiestaIncomingTaunt?) {
    if (taunt == null || taunt.id.isBlank()) return
    var dismissed by rememberSaveable { mutableStateOf("") }
    if (taunt.id == dismissed) return
    var taps by rememberSaveable(taunt.id) { mutableStateOf(0) }
    val remaining = (3 - taps).coerceAtLeast(0)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .clickable {
                taps += 1
                if (taps >= 3) dismissed = taunt.id
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(fiestaColor("#2A0710"), look.backdrop)))
                .border(3.dp, look.accent2, RoundedCornerShape(20.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FiestaHostPortrait(look, 88.dp)
            Text(
                "¡${taunt.from} te lanzó una burla!",
                color = look.accent2,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                "«${taunt.text}»",
                color = look.ink,
                fontFamily = look.display,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "¡Toca $remaining ${if (remaining == 1) "vez" else "veces"} para limpiar la pantalla!",
                color = look.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            FiestaProgressBar(look, progress = taps / 3f, modifier = Modifier.fillMaxWidth(), height = 8.dp)
        }
    }
}

/**
 * Minijuego de espera: una moneda salta cada 850 ms. Cinco toques disparan la
 * acción `fiesta_wait_bonus`, que el servidor valida y paga una sola vez.
 */
@Composable
fun WaitingMiniGameCard(
    look: FiestaLook,
    waitBonus: Boolean,
    enabled: Boolean,
    onBonus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var x by remember { mutableStateOf(0.2f) }
    var y by remember { mutableStateOf(0.2f) }
    var taps by rememberSaveable { mutableStateOf(0) }
    var fired by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(waitBonus) {
        if (waitBonus) return@LaunchedEffect
        while (true) {
            delay(850)
            x = Random.nextFloat() * 0.7f
            y = Random.nextFloat() * 0.55f
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(look.accent.copy(alpha = 0.08f))
            .border(1.dp, look.accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            if (waitBonus || fired) "¡+25 MONEDAS GANADAS!" else "¡ATRAPA LA MONEDA MIENTRAS ESPERAS!",
            color = if (waitBonus || fired) look.accent2 else look.accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            Text(
                "●",
                modifier = Modifier
                    .offset(x = (x * 120).dp, y = (y * 42).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (waitBonus || fired) look.accent2.copy(alpha = 0.3f) else look.accent2)
                    .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable(enabled = enabled && !waitBonus && !fired) {
                        taps += 1
                        if (taps >= 5) {
                            fired = true
                            onBonus()
                        }
                    }
                    .padding(top = 5.dp),
                color = look.backdrop,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            if (!waitBonus && !fired) {
                Text(
                    "${taps}/5 toques",
                    color = look.ink.copy(alpha = 0.5f),
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}


/** Retrato del presentador que cobra vida mientras suena su clip de voz. */
@Composable
fun AnimatedHostAvatar(look: FiestaLook, size: Dp = 74.dp, modifier: Modifier = Modifier) {
    val speaking by FiestaVoice.isHostSpeaking.collectAsState()
    val reduced = LocalReducedEffects.current
    val pulse = rememberInfiniteTransition(label = "host-pulse")
    val wave by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "host-wave",
    )
    val active = speaking && !reduced
    Box(
        modifier
            .graphicsLayer {
                translationY = if (active) -6f * wave else 0f
                scaleX = if (active) 1f + 0.04f * wave else 1f
                scaleY = if (active) 1f + 0.04f * wave else 1f
            }
            .border(
                3.dp,
                if (active) look.accent2.copy(alpha = 0.35f + 0.65f * wave) else Color.Transparent,
                CircleShape,
            ),
    ) {
        FiestaHostPortrait(look, size)
    }
}

/**
 * Transición maestra entre fases. Con efectos reducidos es un crossfade puro;
 * con movimiento, un AnimatedContent más un barrido temático de 320 ms.
 */
@Composable
fun FiestaPhaseTransition(
    gameId: String,
    phaseKey: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    if (LocalReducedEffects.current) {
        Crossfade(targetState = phaseKey, animationSpec = tween(180), label = "fiesta-phase") { key ->
            content(key)
        }
        return
    }
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(phaseKey) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, animationSpec = tween(320))
    }
    Box(modifier) {
        AnimatedContent(
            targetState = phaseKey,
            transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(170)) },
            label = "fiesta-phase-content",
        ) { key -> content(key) }
        val progress = sweep.value
        if (progress < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val color = if (gameId == "vox_populi" || gameId == "franela_fest") {
                    fiestaColor("#FBBF24")
                } else {
                    fiestaColor("#22D3EE")
                }
                when (gameId) {
                    "ultima_palabra", "el_tasador" -> {
                        val curtain = size.width * 0.5f * (1f - progress)
                        drawRect(color.copy(alpha = 0.32f), size = androidx.compose.ui.geometry.Size(curtain, size.height))
                        drawRect(
                            color.copy(alpha = 0.32f),
                            topLeft = Offset(size.width - curtain, 0f),
                            size = androidx.compose.ui.geometry.Size(curtain, size.height),
                        )
                    }
                    "fuera_de_epoca", "texto_roto" -> {
                        val y = size.height * progress
                        drawRect(color.copy(alpha = 0.10f), size = androidx.compose.ui.geometry.Size(size.width, 3f), topLeft = Offset(0f, y))
                        var line = 0f
                        while (line < size.height) {
                            drawRect(color.copy(alpha = 0.045f), size = androidx.compose.ui.geometry.Size(size.width, 1f), topLeft = Offset(0f, line))
                            line += 6f
                        }
                    }
                    else -> {
                        val stripe = size.width * 0.16f
                        var x = -stripe + (size.width + stripe * 2f) * progress
                        while (x < size.width + stripe) {
                            drawRect(
                                color.copy(alpha = 0.10f),
                                topLeft = Offset(x, 0f),
                                size = androidx.compose.ui.geometry.Size(stripe * 0.55f, size.height),
                            )
                            x += stripe
                        }
                    }
                }
            }
        }
    }
}

/** Cinta de reacción sobre el plató durante el reveal. */
@Composable
fun FiestaReactionBanner(look: FiestaLook, text: String, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "reaction-banner")
    val alpha by pulse.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "reaction-alpha",
    )
    Row(
        modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(fiestaColor("#B91C1C"), look.accent)))
            .border(2.dp, look.accent2.copy(alpha = 0.75f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("FAV", color = look.accent2, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(7.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
    }
}
