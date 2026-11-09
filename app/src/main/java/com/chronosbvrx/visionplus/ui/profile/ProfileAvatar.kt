package com.chronosbvrx.visionplus.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.data.profile.ProfileAvatars

/**
 * Avatares de perfil.
 *
 * - Con una imagen elegida ([avatar] = id de [ProfileAvatars]) se pinta la foto recortada en
 *   cuadrado redondeado, como las carátulas de una plataforma de streaming.
 * - Sin imagen se conserva el avatar dibujado de siempre: la inicial del nombre sobre un
 *   color propio derivado del nombre.
 *
 * Las fotos viajan en el APK, así que el perfil se ve igual sin conexión y en una tele.
 */
private val PROFILE_COLORS = listOf(
    Color(0xFF2D7DF6), // Vision azul
    Color(0xFF7A5CFF), // violeta
    Color(0xFF16B8A6), // turquesa
    Color(0xFFE0568A), // rosa
    Color(0xFFF2A33A), // ámbar
    Color(0xFF3FA65B), // verde
    Color(0xFFD9564A), // coral
    Color(0xFF4B7BEC), // azul claro
)

/** Índice de color estable para un nombre (puro, testeable). */
internal fun profileColorIndex(name: String): Int {
    var hash = 0
    name.trim().lowercase().forEach { char -> hash = hash * 31 + char.code }
    return ((hash % PROFILE_COLORS.size) + PROFILE_COLORS.size) % PROFILE_COLORS.size
}

/** El color del perfil. */
internal fun profileColor(name: String): Color = PROFILE_COLORS[profileColorIndex(name)]

/** La inicial que se pinta (primera letra con contenido; acentos respetados). */
internal fun profileInitial(name: String): String {
    val clean = name.trim()
    // «Perfil 3» se distingue de «Principal» mostrando su número, no otra P.
    Regex("""(?i)^perfil\s+(\d+)$""").find(clean)?.let { match ->
        return match.groupValues[1]
    }
    val first = clean.firstOrNull { it.isLetterOrDigit() } ?: 'P'
    return first.uppercase()
}

/**
 * Avatar circular (inicial) o cuadrado redondeado (imagen elegida). [ring] dibuja el aro
 * blanco del perfil en uso o enfocado.
 */
@Composable
internal fun ProfileAvatar(
    name: String,
    size: Dp = 56.dp,
    ring: Boolean = false,
    avatar: String = "",
    modifier: Modifier = Modifier,
    ringColor: Color = Color.White,
    ringWidth: Dp = 3.dp,
) {
    val option = ProfileAvatars.find(avatar)
    val shape = if (option != null) RoundedCornerShape(percent = 28) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (ring) Modifier.border(ringWidth, ringColor, shape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (option != null) {
            Image(
                painter = painterResource(option.drawable),
                contentDescription = "Perfil $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val color = profileColor(name)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))),
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profileInitial(name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.40f).sp,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
