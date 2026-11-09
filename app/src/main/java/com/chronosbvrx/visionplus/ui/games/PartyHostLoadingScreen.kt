package com.chronosbvrx.visionplus.ui.games

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh

/**
 * Máscara nativa entre la selección y la sala del juego.
 *
 * Tapa la creación de la sala y el primer render del host para que la tele no
 * muestre pantallas intermedias del navegador. No reproduce audio: la
 * transición de entrada ya lo hace.
 */
@Composable
fun PartyHostLoadingScreen(gameTitle: String) {
    val motion = rememberInfiniteTransition(label = "host-loading")
    val pulse by motion.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "host-loading-pulse",
    )
    val dots by motion.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200)),
        label = "host-loading-dots",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VisionBlack, Color(0xFF0B1424), Color(0xFF080D16)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.46f)
            val radius = size.minDimension * 0.2f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(VisionBlue.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = radius * 1.8f,
                ),
                radius = radius * 1.8f,
                center = center,
            )
            repeat(3) { index ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f + index * 0.025f),
                    radius = radius * (1f + index * 0.32f),
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(VisionBlue, VisionBlueBright)))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "V+",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "PREPARANDO LA SALA",
                color = VisionBlueBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                gameTitle,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { index ->
                    val lit = dots >= index
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(if (lit) 0.95f else 0.28f)
                            .background(
                                if (lit) VisionBlueBright else VisionSurfaceHigh,
                                CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Conectando la tele con los celulares",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
