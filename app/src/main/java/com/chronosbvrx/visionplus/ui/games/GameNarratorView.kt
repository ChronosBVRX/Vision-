package com.chronosbvrx.visionplus.ui.games

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.games.GameNarrator
import com.chronosbvrx.visionplus.data.games.NarratorAudio
import com.chronosbvrx.visionplus.data.games.NarratorEvent
import com.chronosbvrx.visionplus.data.games.NarratorMood
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/**
 * El presentador de Vision+ Play. La pose cambia con el momento y, en el modo
 * inmersivo, flota y respira con una luz de estudio: se siente vivo sin gastar
 * CPU en una animación de fotogramas durante el reloj.
 */
@Composable
fun NarratorView(
    mood: NarratorMood,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    animated: Boolean = false,
    alive: Boolean = false,
) {
    val reduced = LocalReducedEffects.current
    val animateFrames = animated && !reduced && mood == NarratorMood.Idle
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(animateFrames) {
        if (!animateFrames) {
            frame = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(190)
            frame = (frame + 1) % GameNarrator.frames().size
        }
    }
    val breathe = if (alive && !reduced) {
        val transition = rememberInfiniteTransition(label = "narrator-live")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
            label = "narrator-breathe",
        ).value
    } else {
        0.5f
    }
    val accent = moodColor(mood)
    val res = if (animateFrames) GameNarrator.frames()[frame] else GameNarrator.moodFrame(mood)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (alive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.30f + 0.10f * breathe), Color.Transparent),
                                center = center,
                                radius = this.size.minDimension * 0.52f,
                            ),
                            radius = this.size.minDimension * 0.52f,
                            center = center,
                        )
                    },
            )
        }
        Image(
            painter = painterResource(res),
            contentDescription = "Narradora de Vision+ Play",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val float = if (alive && !reduced) (breathe - 0.5f) * 8f else 0f
                    translationY = float
                    scaleX = 1f + (if (alive && !reduced) (breathe - 0.5f) * 0.018f else 0f)
                    scaleY = scaleX
                },
        )
        if (alive) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                repeat(3) { index ->
                    val h = 4 + ((breathe * 10f + index * 3f) % 11f)
                    Box(
                        Modifier
                            .size(width = 3.dp, height = h.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

/** Bocadillo del presentador con efecto de máquina de escribir (sin animación en equipos modestos). */
/**
 * Una intervención de la presentadora para un momento del juego: elige una
 * variante de texto+audio (sin repetir la anterior del mismo evento) y la
 * reproduce cuando cambia [key]. El bocadillo y la voz siempre dicen lo mismo.
 */
@Composable
fun rememberNarratorVariant(
    event: NarratorEvent,
    key: String,
    speak: Boolean = true,
): GameNarrator.NarratorVariant {
    val context = LocalContext.current
    val variant = remember(event, key) { GameNarrator.cue(event, key) }
    LaunchedEffect(event, key, speak) {
        if (speak) NarratorAudio.play(context, variant.audio)
    }
    return variant
}

/** Variante hablada sin bocadillo (p. ej. el aviso de un jugador nuevo). */
fun playNarratorCue(context: android.content.Context, event: NarratorEvent, key: String) {
    NarratorAudio.play(context, GameNarrator.cue(event, key).audio)
}

/** Solo el texto de la variante (para bocadillos donde el audio lo pone otro). */
@Composable
fun rememberNarratorText(event: NarratorEvent, key: String): String {
    return remember(event, key) { GameNarrator.cue(event, key).text }
}

@Composable
fun NarratorBubble(
    text: String,
    modifier: Modifier = Modifier,
    typing: Boolean = true,
    label: String = "PRESENTADORA",
) {
    val reduced = LocalReducedEffects.current
    var shown by remember(text) { mutableIntStateOf(if (reduced || !typing) text.length else 0) }
    LaunchedEffect(text, typing) {
        if (reduced || !typing) {
            shown = text.length
            return@LaunchedEffect
        }
        shown = 0
        while (shown < text.length) {
            delay(16)
            shown += 1
        }
    }
    Surface(
        modifier = modifier,
        color = VisionSurfaceHigh.copy(alpha = 0.94f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VisionBlue.copy(alpha = 0.45f)),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label,
                color = VisionBlueBright,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = text.take(shown.coerceIn(0, text.length)),
                color = VisionTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun moodColor(mood: NarratorMood): Color = when (mood) {
    NarratorMood.Welcome, NarratorMood.Happy -> VisionBlueBright
    NarratorMood.Thinking -> VisionBlue
    NarratorMood.Oops -> Color(0xFFFF8A65)
    NarratorMood.Celebrate -> VisionRatingGold
    NarratorMood.Idle -> VisionBlue.copy(alpha = 0.75f)
}

/** Un pequeño rótulo de estado para el plató (EN VIVO, TURNO, APUESTA…). */
@Composable
fun GameStageBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Aura de plató: gradientes fríos detrás del presentador, sin imágenes externas. */
@Composable
fun GameStageBackground(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070B13),
                        Color(0xFF0A1220),
                        Color(0xFF070B13),
                    ),
                ),
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.28f),
                        radius = size.minDimension * 0.65f,
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.18f, size.height * 0.28f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(VisionBlue.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.7f),
                        radius = size.minDimension * 0.55f,
                    ),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.88f, size.height * 0.7f),
                )
            },
    )
}

/** Un separador con brillo, para el HUD del juego. */
@Composable
fun GameHairline(modifier: Modifier = Modifier, color: Color = VisionBlue) {
    Box(
        modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent),
                ),
            ),
    )
}

/** Texto de apoyo del presentador con la paleta del plató. */
@Composable
fun GameHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = VisionTextSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
}
