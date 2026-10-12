package com.chronosbvrx.visionplus.ui.brand

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Exit fade duration, in ms. */
private const val EXIT_MS = 260

/**
 * Boot screen:
 * - On TV and low-end devices (e.g. Chromecast with Google TV): a pure, hardware-safe, zero-decoder
 *   Jetpack Compose splash screen with crisp VISION+ typography and a subtle blue spinner.
 *   This avoids SurfaceView transformations that cause fatal SIGSEGV in libhwui on Android TV / Amlogic,
 *   consumes 0 hardware video decoders, and boots instantly without freezing the CPU or blowing memory limits.
 * - On capable mobile devices: plays the brand video full screen with safe fallback.
 * [canExit] triggers the exit fade and [onFinished] notifies caller to dismiss.
 */
@Composable
fun VisionSplash(isTv: Boolean, canExit: Boolean, onFinished: () -> Unit) {
    val exitAnim = remember { Animatable(0f) }
    LaunchedEffect(canExit) {
        if (!canExit) return@LaunchedEffect
        exitAnim.animateTo(1f, tween(durationMillis = EXIT_MS, easing = FastOutLinearInEasing))
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
                val s = if (useComposeSplash) 1f else (1f + 0.05f * exitAnim.value)
                scaleX = s
                scaleY = s
                alpha = 1f - exitAnim.value
            }
            .background(VisionBlack),
        contentAlignment = Alignment.Center,
    ) {
        if (useComposeSplash || videoError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VisionFullLogo(
                    height = if (isTv) 130.dp else 105.dp,
                )
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = VisionBlue,
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Iniciando Vision+...",
                    color = VisionTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
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
        }
    }
}
