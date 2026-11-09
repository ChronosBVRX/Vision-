package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/**
 * Sección Juegos de Vision+: tarjetas grandes, texto legible a distancia y
 * navegación completa con D-pad. Al elegir una tarjeta se abre el host de
 * Vision+ Party en el WebView; los teléfonos entran por QR.
 */
@Composable
fun PartyGamesScreen(
    isTv: Boolean,
    onOpenGame: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceEffects = remember(context) { DevicePerformance.shouldReduceEffects(context) }
    val firstFocus = remember { FocusRequester() }
    var showIntro by remember { mutableStateOf(true) }
    LaunchedEffect(isTv, showIntro) {
        if (!showIntro) {
            delay(140)
            runCatching { firstFocus.requestFocus() }
        }
    }
    if (showIntro) {
        PartyTransitionScreen(
            isTv = isTv,
            reduceEffects = reduceEffects,
            onFinished = { showIntro = false },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VisionBlack, Color(0xFF0B1424), Color(0xFF080D16)),
                ),
            ),
    ) {
        Column(
            Modifier.padding(
                start = if (isTv) 42.dp else 20.dp,
                end = if (isTv) 42.dp else 20.dp,
                top = if (isTv) 28.dp else 18.dp,
            ),
        ) {
            Text(
                "SELECCIÓN PREMIUM · ${PARTY_GAMES.size} JUEGOS",
                color = VisionBlueBright,
                fontSize = if (isTv) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Juegos",
                color = VisionTextPrimary,
                fontSize = if (isTv) 38.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Fiesta, trivia y retos. La tele lleva el ritmo; tu celular es el control.",
                color = VisionTextSecondary,
                fontSize = if (isTv) 17.sp else 14.sp,
                lineHeight = if (isTv) 23.sp else 19.sp,
            )
            Spacer(Modifier.height(if (isTv) 12.dp else 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepChip("1", "Elige un juego", isTv)
                Spacer(Modifier.width(if (isTv) 18.dp else 10.dp))
                StepChip("2", "Escanea el QR", isTv)
                Spacer(Modifier.width(if (isTv) 18.dp else 10.dp))
                StepChip("3", "Juega desde tu celular", isTv)
            }
            Spacer(Modifier.height(if (isTv) 20.dp else 12.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isTv) 4 else 2),
            contentPadding = PaddingValues(
                start = if (isTv) 42.dp else 16.dp,
                end = if (isTv) 42.dp else 16.dp,
                bottom = if (isTv) 28.dp else 18.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 14.dp),
        ) {
            itemsIndexed(
                items = PARTY_GAMES,
                key = { _, game -> game.id },
                contentType = { _, _ -> "party-game" },
            ) { index, game ->
                PartyGameCard(
                    game = game,
                    isTv = isTv,
                    reduceEffects = reduceEffects,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onOpenGame(game.id) },
                )
            }
        }

        Text(
            if (isTv) "Usa el mando para moverte. OK abre el juego. Atrás regresa."
            else "Toca un juego para abrir la sala. Atrás regresa.",
            color = VisionTextSecondary,
            fontSize = if (isTv) 13.sp else 12.sp,
            modifier = Modifier.padding(horizontal = if (isTv) 42.dp else 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StepChip(number: String, label: String, isTv: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(if (isTv) 26.dp else 22.dp)
                .height(if (isTv) 26.dp else 22.dp)
                .clip(RoundedCornerShape(50))
                .background(VisionBlue.copy(alpha = 0.22f))
                .border(1.dp, VisionBlueBright.copy(alpha = 0.6f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = VisionBlueBright, fontSize = if (isTv) 12.sp else 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(7.dp))
        Text(label, color = VisionTextSecondary, fontSize = if (isTv) 13.sp else 11.sp)
    }
}

@Composable
private fun PartyGameCard(
    game: PartyGame,
    isTv: Boolean,
    reduceEffects: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 18.dp else 14.dp)

    Column(
        modifier
            .fillMaxWidth()
            .scale(if (focused && !reduceEffects) 1.035f else 1f)
            .alpha(if (game.available) 1f else 0.62f)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(VisionSurface)
            .border(
                BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else VisionSurfaceHigh),
                shape,
            )
            .clickable(enabled = game.available, onClick = onClick)
            .padding(0.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(VisionSurfaceHigh),
        ) {
            Image(
                painter = painterResource(game.cover),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isTv) 12.dp else 9.dp)
                    .background(Color(0xE6070B13), RoundedCornerShape(50))
                    .border(1.dp, VisionSurfaceHigh, RoundedCornerShape(50))
                    .padding(horizontal = if (isTv) 10.dp else 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${game.minPlayers}–${game.maxPlayers} jugadores",
                    color = Color.White,
                    fontSize = if (isTv) 11.sp else 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "  ·  ",
                    color = VisionTextSecondary,
                    fontSize = if (isTv) 11.sp else 9.sp,
                    maxLines = 1,
                )
                Text(
                    game.duration,
                    color = VisionBlueBright,
                    fontSize = if (isTv) 11.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            if (!game.available) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color(0xCC070B13), RoundedCornerShape(50))
                        .border(1.dp, VisionSurfaceHigh, RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        "EN TRADUCCIÓN",
                        color = VisionBlueBright,
                        fontSize = if (isTv) 9.sp else 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color(0xF2070B13),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (isTv) 16.dp else 12.dp),
            ) {
                Text(
                    game.tag.uppercase(),
                    color = VisionBlueBright,
                    fontSize = if (isTv) 11.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    game.title,
                    color = Color.White,
                    fontSize = if (isTv) 24.sp else 17.sp,
                    lineHeight = if (isTv) 28.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    game.tagline,
                    color = VisionTextSecondary,
                    fontSize = if (isTv) 14.sp else 11.sp,
                    lineHeight = if (isTv) 19.sp else 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
