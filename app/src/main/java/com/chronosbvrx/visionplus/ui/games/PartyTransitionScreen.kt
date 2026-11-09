package com.chronosbvrx.visionplus.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Entrada cinematográfica a Juegos.
 *
 * La animación corre en Compose (no en un WebView) para que el primer frame
 * sea nítido y no dependa de la red. El audio de bienvenida se elige de las
 * tomas locales de Qwen y se apaga junto con la pantalla.
 */
@Composable
fun PartyTransitionScreen(
    isTv: Boolean,
    reduceEffects: Boolean,
    onFinished: () -> Unit,
) {
    val motion = rememberInfiniteTransition(label = "party-transition")
    val orbit by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6200, easing = LinearEasing)),
        label = "orbit",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val sweep by motion.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(2300, easing = LinearEasing)),
        label = "sweep",
    )
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }

    PartyWelcomeNarrator()

    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(420, easing = LinearOutSlowInEasing)) }
        launch { progress.animateTo(1f, tween(5000, easing = LinearOutSlowInEasing)) }
        delay(5600)
        fade.animateTo(0f, tween(400))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF142B50), Color(0xFF0A1528), VisionBlack),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.45f)
            val base = size.minDimension

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(VisionBlue.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = base * 0.58f * pulse,
                ),
                radius = base * 0.58f * pulse,
                center = center,
            )

            for (ring in 0..3) {
                val radius = base * (0.17f + ring * 0.078f) * pulse
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f + ring * 0.012f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (1.2f + ring * 0.3f).dp.toPx()),
                )
            }

            val particles = if (reduceEffects) 16 else 32
            repeat(particles) { index ->
                val direction = if (index % 2 == 0) 1f else -1f
                val angle = (index / particles.toFloat()) * Math.PI.toFloat() * 2f +
                    orbit * Math.PI.toFloat() * 2f * direction
                val radius = base * (0.15f + ((index * 37) % 100) / 100f * 0.38f) * pulse
                val x = center.x + cos(angle.toDouble()).toFloat() * radius
                val y = center.y + sin(angle.toDouble()).toFloat() * radius
                val alpha = 0.10f + ((index * 53) % 100) / 100f * 0.42f
                drawCircle(
                    color = Color(0xFF9CC8FF).copy(alpha = alpha),
                    radius = (1.3f + (index % 3) * 0.7f).dp.toPx(),
                    center = Offset(x, y),
                )
            }

            val band = size.width * 0.24f
            val bandStart = sweep * size.width - band
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, VisionBlueBright.copy(alpha = 0.11f), Color.Transparent),
                    startX = bandStart,
                    endX = bandStart + band * 2f,
                ),
                topLeft = Offset(bandStart, 0f),
                size = Size(band * 2f, size.height),
            )

            drawLine(
                color = VisionBlueBright.copy(alpha = 0.35f),
                start = Offset(center.x - base * 0.22f, center.y + base * 0.13f),
                end = Offset(center.x + base * 0.22f, center.y + base * 0.13f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(if (isTv) 132.dp else 102.dp)
                    .clip(RoundedCornerShape(if (isTv) 34.dp else 27.dp))
                    .background(Brush.linearGradient(listOf(VisionBlue, VisionBlueBright)))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.45f),
                        RoundedCornerShape(if (isTv) 34.dp else 27.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "V+",
                    color = Color.White,
                    fontSize = if (isTv) 52.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                )
            }
            Spacer(Modifier.height(if (isTv) 26.dp else 18.dp))
            Text(
                "VISION+ PARTY",
                color = Color.White,
                fontSize = if (isTv) 38.sp else 29.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (isTv) 7.sp else 5.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "PREPARANDO EL DESMADRE",
                color = VisionBlueBright,
                fontSize = if (isTv) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (isTv) 4.sp else 3.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 34.dp else 24.dp))
            Box(
                Modifier
                    .width(if (isTv) 250.dp else 190.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VisionSurfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(listOf(VisionBlue, VisionBlueBright)),
                        ),
                )
            }
        }

        Text(
            "ELIGE · ESCANEA · JUEGA",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = if (isTv) 12.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (isTv) 3.sp else 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isTv) 42.dp else 26.dp),
        )
    }
}
