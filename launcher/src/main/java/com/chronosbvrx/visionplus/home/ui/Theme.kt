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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.home.R

/**
 * La paleta del sistema: pizarra neutro ultra oscuro (no azul marino), con el color
 * saturado reservado para lo que de verdad comunica algo: EN VIVO, progreso y foco.
 */
object HomeColors {
    val Black = Color(0xFF07090E)
    val Deep = Color(0xFF0B0F19)
    val Surface = Color(0xFF0D1117)
    val SurfaceHigh = Color(0xFF161D27)
    val Stroke = Color(0x24FFFFFF)
    val Blue = Color(0xFF2D7DF6)
    val BlueBright = Color(0xFF6BA6FF)
    val Cyan = Color(0xFF22D3EE)
    val Text = Color(0xFFF8FAFC)
    val TextDim = Color(0xFF94A3B8)
    val TextFaint = Color(0xFF64748B)
    val Gold = Color(0xFFF5C518)
    val Live = Color(0xFFEF4444)
    val Success = Color(0xFF3DD68C)
}

object HomeShapes {
    val card = RoundedCornerShape(14.dp)
    val tile = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(50)
    val panel = RoundedCornerShape(22.dp)
    val brand = RoundedCornerShape(9.dp)
    /** Botones de acción: rectángulo suave con borde degradado, no una cápsula genérica. */
    val button = RoundedCornerShape(15.dp)
    val buttonInner = RoundedCornerShape(14.dp)
}

/** En un equipo modesto se apagan escalados y animaciones largas (nunca la información). */
val LocalReducedEffects = compositionLocalOf { true }

/**
 * Plus Jakarta Sans (SIL OFL 1.1), la familia de interfaz del launcher. Se empaqueta en el
 * APK: una tele puede estar sin red y el Inicio no puede caer a una fuente de sistema distinta.
 */
val HomeFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Black),
)

private val HomeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(fontFamily = HomeFontFamily, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = HomeFontFamily, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = HomeFontFamily, fontSize = 11.sp),
    labelLarge = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HomeFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
    ),
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
