package com.chronosbvrx.visionplus.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.chronosbvrx.visionplus.R

/** The logo's primary brand blue. */
internal val LogoBlue = Color(0xFF2D7DF6)

/**
 * The pure Vision+ emblem alone ("V" with play button and glowing "+"), [diameter] wide and tall.
 * Renders the authentic brand identity static asset cleanly cut out without background.
 */
@Composable
fun VisionMark(diameter: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_vision_mark),
        contentDescription = "Vision+",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Vision+" },
    )
}

/**
 * The static Vision+ horizontal lockup: the emblem followed by the wordmark, [height] tall.
 * Authentic brand asset cleanly cut out without background.
 */
@Composable
fun VisionWordmark(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_vision_wordmark),
        contentDescription = "Vision+",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Vision+" },
    )
}

/**
 * The full vertical Vision+ lockup (emblem on top, Vision+ below), [height] tall.
 */
@Composable
fun VisionFullLogo(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_vision_logo_full),
        contentDescription = "Vision+",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .semantics { contentDescription = "Vision+" },
    )
}
