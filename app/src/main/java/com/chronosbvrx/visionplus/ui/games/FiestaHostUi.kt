package com.chronosbvrx.visionplus.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.data.games.FiestaCatalog
import com.chronosbvrx.visionplus.data.games.GameCatalog
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** Avatar y bocadillo del host propio de cada juego de Fiesta. */
@Composable
fun FiestaHostAvatar(
    gameId: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val accent = fiestaLook(gameId).accent
    Box(
        modifier = modifier
            .size(size)
            .background(accent.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(fiestaLook(gameId).hostRes),
            contentDescription = FiestaCatalog.hostName(gameId),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size * 0.86f).clip(CircleShape),
        )
    }
}

@Composable
fun FiestaHostBubble(
    gameId: String,
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val accent = fiestaLook(gameId).accent
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 12.dp else 16.dp),
        color = VisionSurface.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FiestaHostAvatar(gameId, if (compact) 30.dp else 38.dp)
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    FiestaCatalog.hostName(gameId).uppercase(),
                    color = accent,
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Text(
                    text,
                    color = VisionTextPrimary,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (compact) 2 else 4,
                )
            }
        }
    }
}
