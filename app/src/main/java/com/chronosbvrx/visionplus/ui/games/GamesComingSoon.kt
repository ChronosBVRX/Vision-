package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.chronosbvrx.visionplus.ui.tv.TvCompactAction
import kotlinx.coroutines.delay

/**
 * La pestaña **Juegos** queda deshabilitada mientras Vision+ Play se termina de preparar.
 *
 * No abre el antiguo tablero de juegos ni pide sala: muestra un aviso cuidado, con el
 * mismo lenguaje visual del resto de la app, y deja volver sin callejones sin salida.
 */
@Composable
fun GamesComingSoonScreen(isTv: Boolean, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VisionBlack, Color(0xFF0D1728), Color(0xFF080C15)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(VisionBlue.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.18f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF7B4DFF).copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.82f),
                    radius = size.minDimension * 0.48f,
                ),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * 0.86f, size.height * 0.82f),
            )
        }

        val backFocus = remember { FocusRequester() }
        LaunchedEffect(isTv) {
            if (isTv) {
                delay(140)
                runCatching { backFocus.requestFocus() }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = if (isTv) 760.dp else 560.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 96.dp else 82.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(VisionBlue.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f)),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = null,
                    tint = VisionBlueBright,
                    modifier = Modifier.size(if (isTv) 46.dp else 38.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "VISION+ PLAY",
                color = VisionBlueBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Próximamente",
                color = Color.White,
                fontSize = if (isTv) 46.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Estamos preparando los juegos para que lleguen con todo. " +
                    "Muy pronto podrás jugar desde aquí.",
                color = VisionTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            if (isTv) {
                TvCompactAction(
                    label = "Volver",
                    modifier = Modifier.focusRequester(backFocus),
                    onClick = onBack,
                )
            } else {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = VisionBlack,
                    ),
                ) {
                    Text("Volver", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
