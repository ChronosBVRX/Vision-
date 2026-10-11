package com.chronosbvrx.visionplus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh

/**
 * The loading ghosts: rounded grey blocks with a slow sheen where cards will land, so the screen
 * never feels empty while the catalogue is on its way. Still (no sheen) with reduced effects on.
 */
@Composable
private fun ghostBrush(): Brush {
    if (LocalReducedEffects.current) {
        return Brush.linearGradient(listOf(VisionSurfaceHigh, VisionSurfaceHigh))
    }
    val transition = rememberInfiniteTransition(label = "ghost")
    val shift by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearEasing)),
        label = "ghost sheen",
    )
    return Brush.linearGradient(
        colors = listOf(VisionSurfaceHigh, Color.White.copy(alpha = 0.09f), VisionSurfaceHigh),
        start = Offset(shift, 0f),
        end = Offset(shift + 480f, 320f),
    )
}

/** A rounded grey block: the unit of every skeleton. */
@Composable
fun GhostBox(width: Dp, height: Dp, radius: Dp = 10.dp, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(ghostBrush()),
    )
}

/** What a catalogue row becomes while it loads: a ghost title and [count] ghost cards. */
@Composable
fun GhostRow(
    count: Int = 5,
    cardWidth: Dp = 112.dp,
    cardHeight: Dp = 160.dp,
    horizontalPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = horizontalPadding, vertical = 10.dp)) {
        GhostBox(width = 132.dp, height = 14.dp, radius = 7.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(count) { GhostBox(width = cardWidth, height = cardHeight) }
        }
    }
}
