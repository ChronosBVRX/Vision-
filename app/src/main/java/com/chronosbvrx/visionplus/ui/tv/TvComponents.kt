package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.cardFocusScale
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionBlueDark
import java.util.Locale

/**
 * Píldora de categoría deslizable para Smart TV con navegación D-pad.
 */
@Composable
fun TvCategoryPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val reduced = LocalReducedEffects.current
    val containerBg = when {
        selected -> Brush.horizontalGradient(listOf(VisionBlue, VisionBlueDark))
        hasFocus -> Brush.horizontalGradient(listOf(VisionSurfaceHigh, VisionSurfaceHigh))
        else -> Brush.horizontalGradient(listOf(Color(0xFF141A26), Color(0xFF141A26)))
    }
    val borderColor = when {
        hasFocus -> Color.White
        selected -> VisionBlueBright
        else -> Color.White.copy(alpha = 0.12f)
    }
    val textColor = when {
        hasFocus || selected -> Color.White
        else -> VisionTextSecondary
    }

    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { hasFocus = it.isFocused },
        scale = cardFocusScale(reduced),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(50)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .background(containerBg)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || hasFocus) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Tarjeta vertical estilo póster (2:3) para Android TV:
 * - Proporción 2:3 con esquinas redondeadas
 * - Zoom y borde blanco al tomar foco del D-pad
 * - Badge de calificación (cápsula translúcida con borde neón/azul en esquina superior izquierda)
 * - Badge de estado (esquina superior derecha)
 * - Variante Top 10 con número de posición gigante estilizado (esquina inferior izquierda)
 * - Decodificación Coil protegida a 320x480 para fluidez y cero OOM en TV.
 */
@Composable
fun TvPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    score: Double = 0.0,
    badge: String? = null,
    rank: Int? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current

    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        scale = cardFocusScale(reduced),
        colors = CardDefaults.colors(containerColor = VisionSurfaceHigh),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VisionSurfaceHigh),
        ) {
            if (imageUrl.isNullOrBlank()) {
                CardPlaceholder()
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(if (reduced) 0 else 150)
                        .size(320, 480)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Gradiente inferior oscuro para contraste
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )

            // Badge de Calificación (Esquina superior izquierda)
            if (score > 0.0) {
                val formatted = if (score % 1.0 == 0.0) score.toInt().toString() else String.format(Locale.US, "%.1f", score)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0xDD0B1220), shape = RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, VisionBlue.copy(alpha = 0.85f)), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "★ $formatted",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            // Badge de Estado (Esquina superior derecha)
            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            Brush.horizontalGradient(listOf(VisionBlue, VisionBlueDark)),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badge.uppercase(),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
            }

            // Variante Top 10 con Número (Rank)
            if (rank != null && rank in 1..10) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 6.dp, y = 6.dp),
                ) {
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
                            color = VisionBlueBright,
                        ),
                    )
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            brush = Brush.verticalGradient(
                                listOf(Color.White, VisionBlueBright, VisionSurfaceHigh),
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/** Fallback for cards with no image: gradient + video icon. */
@Composable
private fun CardPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF33333D), Color(0xFF17171C)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(52.dp),
        )
    }
}

/**
 * Landscape card (16:9) with full-bleed art and no overlaid title: the focused film's name shows
 * in the hero above. Fixed height; zooms and gets a white border on focus.
 */
@Composable
fun TvLandscapeCard(
    title: String,
    imageUrl: String?,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current
    Card(
        onClick = onClick,
        modifier = modifier.height(cardHeight).onFocusChanged { if (it.isFocused) onFocus() },
        scale = cardFocusScale(reduced),
        colors = CardDefaults.colors(containerColor = VisionSurfaceHigh),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .background(VisionSurfaceHigh),
        ) {
            if (imageUrl.isNullOrBlank()) {
                CardPlaceholder()
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(if (reduced) 0 else 150)
                        .size(480, 270)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Fixed-height row label, so two rows fit exactly in the scrollable zone. */
@Composable
fun TvRowLabel(text: String, height: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).padding(start = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón interactivo "Ver todo →" con foco de D-pad para encabezados de filas en Android TV. */
@Composable
fun TvSeeAllButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { hasFocus = it.isFocused },
        scale = cardFocusScale(LocalReducedEffects.current),
        colors = CardDefaults.colors(
            containerColor = if (hasFocus) VisionBlue else Color.White.copy(alpha = 0.08f),
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), shape = RoundedCornerShape(50)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Ver todo →",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasFocus) Color.White else VisionBlue,
            )
        }
    }
}

