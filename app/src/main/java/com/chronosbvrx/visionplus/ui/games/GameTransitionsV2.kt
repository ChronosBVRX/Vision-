package com.chronosbvrx.visionplus.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Contenedor maestro de transición de fase.
 * La tele deja de sentir que cambia de diapositiva entre countdown, secret,
 * attack, question, punishment, sort, assault y reveal.
 */
@Composable
fun <T> GamePhaseTransition(
    phaseKey: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    if (LocalReducedEffects.current) {
        content(phaseKey)
        return
    }
    AnimatedContent(
        targetState = phaseKey,
        modifier = modifier,
        transitionSpec = {
            (
                slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetX = { fullWidth -> fullWidth / 3 },
                ) + fadeIn(tween(260)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                )
            ).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(220),
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                ) + fadeOut(tween(180)),
            ).using(SizeTransform(clip = false))
        },
        label = "GamePhaseTransition",
    ) { targetPhase ->
        content(targetPhase)
    }
}

data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedStroke(
    val points: List<NormalizedPoint>,
    val colorHex: Long,
    val width: Float,
)

/**
 * Lienzo vectorial con interpolación de los trazos que llegan por polling
 * y linterna radial dibujada en la fase Draw, sin recomponer los puntos.
 */
@Composable
fun AnimatedVectorDrawingCanvas(
    strokes: List<NormalizedStroke>,
    flashlightEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val totalPoints = remember(strokes) { strokes.sumOf { it.points.size } }
    val animatedPointCount = remember { Animatable(0f) }

    LaunchedEffect(totalPoints) {
        if (totalPoints == 0) {
            animatedPointCount.snapTo(0f)
        } else {
            animatedPointCount.animateTo(
                targetValue = totalPoints.toFloat(),
                animationSpec = tween(durationMillis = 1600, easing = LinearEasing),
            )
        }
    }

    val angle = if (flashlightEnabled && !LocalReducedEffects.current) {
        val infinite = rememberInfiniteTransition(label = "FlashlightLoop")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 6.2831855f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "FlashlightAngle",
        ).value
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .drawWithContent {
                drawContent()
                if (flashlightEnabled) {
                    val radius = size.minDimension * 0.28f
                    val center = Offset(
                        x = size.width * (0.5f + 0.32f * cos(angle)),
                        y = size.height * (0.5f + 0.28f * sin(angle * 1.7f)),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC050811),
                                Color(0xF8050811),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        size = size,
                        blendMode = BlendMode.SrcOver,
                    )
                }
            },
    ) {
        var remaining = animatedPointCount.value.toInt()
        for (stroke in strokes) {
            if (remaining <= 0) break
            val takeCount = minOf(stroke.points.size, remaining)
            remaining -= takeCount
            if (takeCount == 1) {
                val point = stroke.points[0]
                drawCircle(
                    color = Color(stroke.colorHex),
                    radius = stroke.width * 1.5f,
                    center = Offset(
                        point.x / 1000f * size.width,
                        point.y / 1000f * size.height,
                    ),
                )
            } else if (takeCount > 1) {
                val path = Path()
                val first = stroke.points[0]
                path.moveTo(first.x / 1000f * size.width, first.y / 1000f * size.height)
                for (index in 1 until takeCount) {
                    val point = stroke.points[index]
                    path.lineTo(point.x / 1000f * size.width, point.y / 1000f * size.height)
                }
                drawPath(
                    path = path,
                    color = Color(stroke.colorHex),
                    style = Stroke(
                        width = stroke.width * 2.2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

/**
 * Tarjeta de reveal con volteo 3D, votantes y estados de veneno/crítico.
 */
@Composable
fun DramaticRevealCard(
    optionText: String,
    authorLabel: String,
    isTruth: Boolean,
    isPoisoned: Boolean,
    voters: List<String>,
    revealOrderIndex: Int,
    modifier: Modifier = Modifier,
) {
    val flipProgress = remember { Animatable(0f) }
    val reduced = LocalReducedEffects.current

    LaunchedEffect(optionText, revealOrderIndex, reduced) {
        if (reduced) {
            flipProgress.snapTo(180f)
            return@LaunchedEffect
        }
        flipProgress.snapTo(0f)
        delay(revealOrderIndex * 650L + 250L)
        flipProgress.animateTo(
            targetValue = 180f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val isRevealed = flipProgress.value >= 90f
    val borderColor = when {
        !isRevealed -> Color(0xFF2D7DF6)
        isTruth -> Color(0xFF18D26E)
        isPoisoned -> Color(0xFFA855F7)
        else -> Color(0xFFF59E0B)
    }
    val tiltAngle = if (revealOrderIndex % 2 == 0) -0.8f else 0.8f

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = flipProgress.value
                rotationZ = tiltAngle
                cameraDistance = 14f * density
            }
            .background(Color(0xFF111827), RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (!isRevealed) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(optionText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (voters.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        voters.forEach { voter ->
                            Text(
                                text = voter,
                                color = Color(0xFF93C5FD),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .background(Color(0xFF1E3A8A), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationY = 180f },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(optionText, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(authorLabel, color = borderColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                if (voters.isNotEmpty()) {
                    Text(
                        text = "Cayeron: ${voters.joinToString(", ")}",
                        color = Color(0xFFD1D5DB),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * Partículas en un solo Canvas: no crea un nodo Compose por partícula.
 */
@Composable
fun LightweightParticleOverlay(
    sprite: ImageBitmap,
    active: Boolean,
    particleCount: Int = 24,
    particleScale: Float = 0.16f,
    modifier: Modifier = Modifier,
) {
    if (!active || LocalReducedEffects.current) return
    val infinite = rememberInfiniteTransition(label = "ParticleOverlay")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ParticleProgress",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        for (index in 0 until particleCount) {
            val seed = (index * 73 + 19) % 100 / 100f
            val localT = (progress + seed) % 1f
            val x = (size.width * ((index * 37 % 100) / 100f)) + sin(localT * 6.28f + index) * 36f
            val y = size.height * localT
            val spriteWidth = (sprite.width * particleScale).roundToInt().coerceAtLeast(1)
            val spriteHeight = (sprite.height * particleScale).roundToInt().coerceAtLeast(1)
            drawImage(
                image = sprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sprite.width, sprite.height),
                dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                dstSize = IntSize(spriteWidth, spriteHeight),
            )
        }
    }
}
