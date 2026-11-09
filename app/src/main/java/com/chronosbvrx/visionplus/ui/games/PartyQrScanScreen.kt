package com.chronosbvrx.visionplus.ui.games

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.PermissionChecker
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.GlobalHistogramBinarizer
import java.util.concurrent.Executors

/**
 * Escáner QR premium dentro de la app: la cámara llena la pantalla y el foco
 * es una banda horizontal (el móvil se usa en vertical) con esquinas y una
 * línea de escaneo animada. Al detectar un código válido suena un bip,
 * vibra, muestra la check y pasa a la sala.
 *
 * Cámara legacy + ZXing core (sin CameraX): las dependencias nuevas rompían
 * el guardián del motor X4K (colisiones de nombres R8), así que no entran.
 */
@Composable
fun PartyQrScanScreen(
    onCode: (String) -> Unit,
    onCancel: () -> Unit,
    onNeedsPermissionFallback: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current

    var permissionGranted by remember {
        mutableStateOf(
            PermissionChecker.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var foundCode by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }

    DisposableEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(VisionBlack),
    ) {
        if (permissionGranted) {
            val executor = remember { Executors.newSingleThreadExecutor() }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val texture = TextureView(ctx)
                    val listener = LegacyCameraListener(
                        texture,
                        onCamera = { cameraRef = it },
                        isScanning = { foundCode == null },
                        onCode = { hit -> if (foundCode == null) foundCode = hit },
                        torchOnProvider = { torchOn },
                    )
                    texture.surfaceTextureListener = listener
                    texture
                },
            )
            DisposableEffect(executor) {
                onDispose { executor.shutdownNow() }
            }
            QrScanOverlay(
                foundCode = foundCode,
                torchOn = torchOn,
                onTorchToggle = {
                    torchOn = !torchOn
                    toggleTorch(cameraRef, torchOn)
                },
                onCancel = onCancel,
            )
        } else {
            PermissionFallback(
                onFallback = onNeedsPermissionFallback,
                onCancel = onCancel,
            )
        }
    }

    foundCode?.let { code ->
        ScanSuccessOverlay(code = code)
        DisposableEffect(foundCode) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                tone.startTone(ToneGenerator.TONE_CDMA_ANSWER, 220)
                Thread.sleep(260)
                tone.stopTone()
                tone.release()
            }
            val t = Thread {
                try {
                    Thread.sleep(750)
                    onCode(code)
                } catch (_: Exception) { }
            }
            t.start()
            onDispose { }
        }
    }
}

private class LegacyCameraListener(
    private val view: TextureView,
    private val onCamera: (Camera) -> Unit,
    private val isScanning: () -> Boolean,
    private val onCode: (String) -> Unit,
    private val torchOnProvider: () -> Boolean,
) : TextureView.SurfaceTextureListener {
    private var camera: Camera? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            val cam = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
            camera = cam
            onCamera(cam)
            val params = cam.parameters
            val sizes = params.supportedPreviewSizes
            val best = sizes.minWithOrNull(
                compareBy<Camera.Size> { kotlin.math.abs(it.width - 640) + kotlin.math.abs(it.height - 480) },
            ) ?: sizes.first()
            params.setPreviewSize(best.width, best.height)
            params.previewFormat = android.graphics.ImageFormat.NV21
            params.focusMode = params.supportedFocusModes.firstOrNull { it == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE }
                ?: params.focusMode
            val flash = params.supportedFlashModes
            if (flash != null && torchOnProvider()) {
                params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            }
            cam.parameters = params
            cam.setPreviewTexture(surface)
            cam.setPreviewCallbackWithBuffer { data, camera ->
                if (isScanning()) {
                    executor.execute {
                        val hit = decodeReader(reader, data, camera.parameters.previewSize.width, camera.parameters.previewSize.height)
                        if (hit != null) onCode(hit)
                    }
                }
                runCatching { camera.addCallbackBuffer(data) }
            }
            val bufferSize = best.width * best.height * 3 / 2
            cam.addCallbackBuffer(ByteArray(bufferSize))
            cam.startPreview()
            runCatching {
                cam.autoFocus(object : Camera.AutoFocusCallback {
                    override fun onAutoFocus(success: Boolean, camera: Camera) {
                        runCatching { camera.stopPreview(); camera.startPreview() }
                    }
                })
            }
        } catch (_: Exception) { }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        runCatching {
            camera?.stopPreview()
            camera?.release()
            camera = null
        }
        executor.shutdownNow()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

private fun toggleTorch(camera: Camera?, on: Boolean) {
    try {
        val params = camera?.parameters ?: return
        val modes = params.supportedFlashModes ?: return
        params.flashMode = if (on) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
        camera.parameters = params
    } catch (_: Exception) { }
}

private fun decodeReader(reader: MultiFormatReader, data: ByteArray, width: Int, height: Int): String? {
    return try {
        val source = com.google.zxing.PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val result = reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)))
        normalizeRoomCode(result.text)
    } catch (_: Exception) {
        null
    }
}

private fun normalizeRoomCode(raw: String?): String? {
    val scanned = raw?.trim()?.uppercase()
        ?.removePrefix("VISIONPLUS://PARTY/")
        ?.removePrefix("VISIONPLUS://PARTY")
        ?.trim()
    return if (!scanned.isNullOrBlank() && scanned.length >= 4) scanned.take(4) else null
}

/**
 * Visor premium: oscurece el resto de la cámara y deja una banda horizontal
 * iluminada con esquinas y línea de escaneo animada. El teléfono se usa en
 * vertical y el código de la tele es ancho, así que la zona es horizontal.
 */
@Composable
private fun QrScanOverlay(
    foundCode: String?,
    torchOn: Boolean,
    onTorchToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val bandHeight: Dp = 132.dp
    val bandWidthFrac = 0.78f
    val bandTopOffset: Dp = (-54).dp

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val bandH = bandHeight.toPx()
            val bandW = size.width * bandWidthFrac
            val left = (size.width - bandW) / 2f
            val top = size.height * 0.38f - bandH / 2f
            val scrim = Color.Black.copy(alpha = 0.62f)
            // 4 paneles alrededor del band: dejan el centro libre.
            drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, top.coerceAtLeast(0f)))
            drawRect(
                scrim,
                topLeft = Offset(0f, top + bandH),
                size = Size(size.width, (size.height - (top + bandH)).coerceAtLeast(0f)),
            )
            drawRect(scrim, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), bandH))
            drawRect(
                scrim,
                topLeft = Offset(left + bandW, top),
                size = Size((size.width - (left + bandW)).coerceAtLeast(0f), bandH),
            )
        }
        ScanBand(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = bandTopOffset),
            bandWidthFrac = bandWidthFrac,
            bandHeight = bandHeight,
            foundCode = foundCode,
        )
        AnimatedScanLine(bandWidthFrac = bandWidthFrac, bandHeight = bandHeight)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .fillMaxWidth(),
        ) {
            Text(
                "Escanea el código de la sala",
                color = VisionTextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Apunta al QR de la tele",
                color = VisionTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 14.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        IconButton(
            onClick = onTorchToggle,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 18.dp, start = 14.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        ) {
            Icon(
                if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                contentDescription = if (torchOn) "Apagar linterna" else "Encender linterna",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            "Código de 4 caracteres · el sonido avisa al encontrarlo",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ScanBand(modifier: Modifier = Modifier, bandWidthFrac: Float, bandHeight: Dp, foundCode: String?) {
    Box(
        modifier = modifier
            .fillMaxWidth(bandWidthFrac)
            .height(bandHeight),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = 18.dp.toPx()
            val stroke = 3.dp.toPx()
            val color = Color(0xFF2D7DF6)
            fun cornerPath(x: Float, y: Float, dx: Float, dy: Float) {
                val p = Path()
                p.moveTo(x + dx * corner, y)
                p.lineTo(x, y)
                p.lineTo(x, y + dy * corner)
                p.close()
                drawPath(p, color, style = Stroke(width = stroke))
            }
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color(0xFF2D7DF6).copy(alpha = 0.22f),
                cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            cornerPath(0f, 0f, 1f, 1f)
            cornerPath(w, 0f, -1f, 1f)
            cornerPath(0f, h, 1f, -1f)
            cornerPath(w, h, -1f, -1f)
            if (foundCode != null) {
                drawRoundRect(
                    color = Color(0xFF2D7DF6),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun AnimatedScanLine(bandWidthFrac: Float, bandHeight: Dp) {
    val transition = rememberInfiniteTransition(label = "scanline")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanline-offset",
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxWidth(bandWidthFrac)
                .height(bandHeight)
                .align(Alignment.Center)
                .offset(y = (-54).dp),
        ) {
            val lineH = 3.dp.toPx()
            val y = offsetY * (size.height - lineH)
            drawRoundRect(
                color = Color(0xFF2D7DF6).copy(alpha = 0.85f),
                topLeft = Offset(0f, y),
                size = Size(size.width, lineH),
                cornerRadius = CornerRadius(lineH / 2f),
            )
        }
    }
}

@Composable
private fun ScanSuccessOverlay(code: String) {
    Crossfade(targetState = code, label = "scan-success") {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(VisionSurface, RoundedCornerShape(28.dp))
                    .border(1.dp, VisionBlue.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(VisionBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Sala $code", color = VisionTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Entrando al mando…", color = VisionTextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PermissionFallback(onFallback: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Text("Necesitamos tu cámara", color = VisionTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Permite el acceso para escanear el QR de la tele, o escribe el código de la sala a mano.",
            color = VisionTextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onFallback) {
            Text("Escribir código", color = VisionBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onCancel) {
            Text("Cancelar", color = VisionTextSecondary, fontSize = 15.sp)
        }
    }
}
