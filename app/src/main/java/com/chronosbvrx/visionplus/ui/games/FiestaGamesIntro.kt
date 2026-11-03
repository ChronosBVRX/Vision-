package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.games.FiestaCatalog
import com.chronosbvrx.visionplus.data.games.GameAmbient
import com.chronosbvrx.visionplus.data.games.GameCatalog
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/**
 * Presentación breve al entrar a Juegos: logo, seis juegos encendiéndose como
 * un bootloader y una barra de carga. Después se cede el paso al selector.
 */
@Composable
fun FiestaGamesIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        GameAmbient.playIntro(context)
        while (elapsed < 2_700) {
            delay(50)
            elapsed += 50
        }
        onDone()
    }
    val reveal = (elapsed - 350).coerceAtLeast(0)
    val progress = (elapsed / 2_700f).coerceIn(0f, 1f)
    val logoScale = 0.86f + (elapsed.coerceAtMost(500) / 500f) * 0.14f
    val games = remember { GameCatalog.all.take(6) }
    val accent = Color(0xFFF97316)
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF2B1307), Color(0xFF07101E), VisionBlack),
                radius = 1200f,
            ),
        ),
    ) {
        // Aros de fondo.
        Box(
            Modifier.size(520.dp).align(Alignment.Center).alpha(0.20f)
                .background(Brush.radialGradient(listOf(accent, Color.Transparent)), CircleShape),
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "VISION+",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                modifier = Modifier.alpha((elapsed / 300f).coerceIn(0f, 1f)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "FIESTA",
                color = accent,
                fontSize = 82.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
                modifier = Modifier.scale(logoScale).alpha((elapsed / 450f).coerceIn(0f, 1f)),
            )
            Text(
                "SEIS JUEGOS. UNA NOCHE.",
                color = VisionTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.alpha((elapsed / 550f).coerceIn(0f, 1f)),
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                games.forEachIndexed { index, game ->
                    val shown = reveal > index * 150
                    Column(
                        modifier = Modifier
                            .width(142.dp)
                            .height(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (shown) Color(game.accent).copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(10.dp)
                            .alpha(if (shown) 1f else 0.28f)
                            .scale(if (shown) 1f else 0.94f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            game.title,
                            color = if (shown) Color(game.accent) else VisionTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            FiestaCatalog.hostName(game.id),
                            color = VisionTextSecondary,
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.fillMaxWidth(0.54f).height(4.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(4.dp)
                        .background(accent, RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                if (progress < 0.4f) "CARGANDO EL PLATÓ…" else if (progress < 0.85f) "AFINANDO LAS VOCES…" else "ABRIENDO LA SALA…",
                color = VisionTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
}
