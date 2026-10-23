package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** La paleta del sistema: negro de cine, azul de marca y un dorado solo para la nota. */
object HomeColors {
    val Black = Color(0xFF04060A)
    val Deep = Color(0xFF070C14)
    val Surface = Color(0xFF0C121C)
    val SurfaceHigh = Color(0xFF16202E)
    val Stroke = Color(0x22FFFFFF)
    val Blue = Color(0xFF2D7DF6)
    val BlueBright = Color(0xFF6BA6FF)
    val Text = Color(0xFFF3F7FD)
    val TextDim = Color(0xFF97A4B7)
    val TextFaint = Color(0xFF66738A)
    val Gold = Color(0xFFF5C518)
    val Live = Color(0xFFE5484D)
    val Success = Color(0xFF3DD68C)
}

object HomeShapes {
    val card = RoundedCornerShape(14.dp)
    val tile = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(50)
    val panel = RoundedCornerShape(22.dp)
}

/** En un equipo modesto se apagan escalados y animaciones largas (nunca la información). */
val LocalReducedEffects = compositionLocalOf { true }

private val HomeTypography = Typography(
    displayLarge = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
)

@Composable
fun HomeTheme(reducedEffects: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedEffects provides reducedEffects) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = HomeColors.Blue,
                onPrimary = Color.White,
                background = HomeColors.Black,
                onBackground = HomeColors.Text,
                surface = HomeColors.Surface,
                onSurface = HomeColors.Text,
                surfaceVariant = HomeColors.SurfaceHigh,
                onSurfaceVariant = HomeColors.TextDim,
            ),
            typography = HomeTypography,
            content = content,
        )
    }
}
