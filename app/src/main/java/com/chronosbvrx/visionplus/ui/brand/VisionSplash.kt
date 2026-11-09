package com.chronosbvrx.visionplus.ui.brand

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.R
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Salida del splash: sube y se desvanece para revelar el Inicio que ya está cargado detrás. */
private const val EXIT_MS = 460

/** Recorrido del brillo sobre la línea fina de progreso. */
private const val SHINE_MS = 1_900

/** Ancho total y ancho del destello de la línea de carga. */
private val PROGRESS_WIDTH = 220.dp
private val SHINE_WIDTH = 64.dp

/**
 * Pantalla de arranque:
 * - En TV y equipos modestos (Chromecast/Android TV) usa solo Compose: cero decodificadores,
 *   cero `SurfaceView` y ninguna transformación de video que pueda tumbar `libhwui`.
 * - En móviles capaces conserva el video de marca como fondo.
 *
 * En ambos casos la salida es la misma: el contenido **se desliza hacia arriba** y deja ver el
 * Inicio ya cargado. Si hay varios perfiles, el selector aparece en esa misma transición, sobre
 * las portadas, sin abrir otra pantalla.
 */
@Composable
fun VisionSplash(isTv: Boolean, canExit: Boolean, status: String? = null, onFinished: () -> Unit) {
    val exit = remember { Animatable(0f) }
    LaunchedEffect(canExit) {
        if (!canExit) return@LaunchedEffect
        exit.animateTo(1f, tween(durationMillis = EXIT_MS, easing = FastOutSlowInEasing))
        onFinished()
    }

    val context = LocalContext.current
    val isLowEnd = remember { DevicePerformance.isLowEndDevice(context) }
    val useComposeSplash = isTv || isLowEnd
    var videoError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - exit.value
                translationY = -exit.value * 96.dp.toPx()
                val scale = 1f + 0.012f * exit.value
                scaleX = scale
                scaleY = scale
            }
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080C14), VisionBlack),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (useComposeSplash || videoError) {
            SplashLoading(isTv = isTv, status = status)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val video = R.raw.boot_mobile
                        setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$video"))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            videoError = true
                            true
                        }
                    }
                },
            )
            Text(
                text = "por ChronosBVRX",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
            )
        }
    }
}

/**
 * Carga sobria: el logo, un halo azul muy tenue y una línea fina con un destello que la recorre.
 * Sin spinner, sin bloques de texto: en la tele se lee como una pantalla de marca, no como una
 * app esperando datos.
 */
@Composable
private fun SplashLoading(isTv: Boolean, status: String?) {
    val motion = rememberInfiniteTransition(label = "splash")
    val breathe by motion.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash-breathe",
    )
    val shine by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHINE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash-shine",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Halo ambiental: da profundidad al logo sin iluminar toda la pantalla.
        Box(
            Modifier
                .size(560.dp)
                .background(
                    Brush.radialGradient(
                        listOf(VisionBlue.copy(alpha = 0.15f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.graphicsLayer { scaleX = breathe; scaleY = breathe }) {
                VisionFullLogo(height = if (isTv) 146.dp else 112.dp)
            }
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier
                    .width(PROGRESS_WIDTH)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    Modifier
                        .width(SHINE_WIDTH)
                        .fillMaxHeight()
                        .offset(x = (PROGRESS_WIDTH - SHINE_WIDTH) * shine)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, VisionBlueBright, Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = status ?: "Iniciando Vision+...",
                color = VisionTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.3.sp,
            )
        }
        Text(
            text = "VISION+ · POR CHRONOSBVRX · EST. 2026",
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp),
        )
    }
}
