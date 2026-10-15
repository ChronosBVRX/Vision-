package com.chronosbvrx.visionplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VisionBlue = Color(0xFF2D7DF6)
val VisionBlueBright = Color(0xFF6BA6FF)
val VisionBlueDark = Color(0xFF0A4FC4)
val VisionBlack = Color(0xFF070B13)
val VisionSurface = Color(0xFF0F1626)
val VisionSurfaceHigh = Color(0xFF1B2538)
val VisionTextPrimary = Color(0xFFF4F7FC)
val VisionTextSecondary = Color(0xFF9BA8BD)
/** Rating gold: the only accent outside the blue palette (IMDb-style recognition). */
val VisionRatingGold = Color(0xFFF5C518)
/** Seen / downloaded / confirmed. */
val VisionSuccess = Color(0xFF7AD9A8)
/** The live dot on air. */
val VisionLiveRed = Color(0xFFFF4B4B)

private val VisionColorScheme = darkColorScheme(
    primary = VisionBlue,
    onPrimary = Color.White,
    secondary = VisionBlue,
    background = VisionBlack,
    onBackground = VisionTextPrimary,
    surface = VisionSurface,
    onSurface = VisionTextPrimary,
    surfaceVariant = VisionSurfaceHigh,
    onSurfaceVariant = VisionTextSecondary,
    outline = Color(0xFF2C3A54),
)

private val VisionTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

/** Vision is always dark, on every device. */
@Composable
fun VisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VisionColorScheme,
        typography = VisionTypography,
        content = content,
    )
}
