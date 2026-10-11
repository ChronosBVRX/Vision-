package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh

// One style for every TV action button: idle = dark surface, focused/pressed = Vision red, disabled =
// dimmed. Without it tv-material3 falls back to white idle / black focused.

/** Colors for `androidx.tv.material3.Button(...)`. */
@Composable
fun visionTvButtonColors() = ButtonDefaults.colors(
    containerColor = VisionSurfaceHigh,
    contentColor = Color.White,
    focusedContainerColor = VisionBlue,
    focusedContentColor = Color.White,
    pressedContainerColor = VisionBlue,
    pressedContentColor = Color.White,
    disabledContainerColor = VisionSurfaceHigh,
    disabledContentColor = Color.White.copy(alpha = 0.4f),
)

/** Border for `androidx.tv.material3.Button(...)`. */
@Composable
fun visionTvButtonBorder() = ButtonDefaults.border(
    border = Border.None,
    focusedBorder = Border.None,
    pressedBorder = Border.None,
)
