package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.chronosbvrx.visionplus.ui.components.posterLongPressKeys
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
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
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
    /** Ancho del póster. El Inicio lo calcula con [tvRowMetrics] (148 dp). */
    cardWidth: Dp = 140.dp,
    /** Alto explícito; por defecto conserva la proporción 2:3. */
    cardHeight: Dp = cardWidth * 1.5f,
    onFocus: () -> Unit = {},
    /** OK largo (o Menú/Info): menú de favoritos / Ver más tarde, sin salir de la fila. */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            // OK corto = abrir; OK largo = menú contextual. La pulsación se consume para
            // que al soltar no se abra además la ficha.
            .posterLongPressKeys(onClick = onClick, onLongClick = onLongClick)
            // La tarjeta se anuncia con el nombre del título (lector de pantalla y guardianes
            // del regreso): antes el nodo enfocable no llevaba identidad propia.
            .semantics { contentDescription = title }
            .onFocusChanged { if (it.isFocused) onFocus() },
        scale = cardFocusScale(reduced),
        colors = CardDefaults.colors(containerColor = VisionSurfaceHigh),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
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
                        .size(480, 720)
                        .build(),
                    // La tarjeta ya se anuncia con el título: la imagen no repite el nombre.
                    contentDescription = null,
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

            // Badge de Calificación (arriba a la derecha, como en el launcher)
            if (score > 0.0) {
                val formatted = if (score % 1.0 == 0.0) score.toInt().toString() else String.format(Locale.US, "%.1f", score)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0x99000000), shape = RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            VisionIcons.Rating,
                            contentDescription = null,
                            tint = VisionRatingGold,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatted,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }

            // Badge de Estado (arriba a la izquierda)
            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0x99000000), shape = RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
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

            // Top 10: la cifra es la protagonista, con una sombra oscura suave
            // detrás para leerse sobre cualquier póster.
            if (rank != null && rank in 1..10) {
                val rankSize = (cardWidth.value * 0.42f).sp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 4.dp, y = 0.dp),
                ) {
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            fontSize = rankSize,
                            fontWeight = FontWeight.Black,
                            drawStyle = Stroke(width = 5f, join = StrokeJoin.Round),
                            color = VisionBlueBright,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.65f),
                                offset = Offset(2f, 3f),
                                blurRadius = 12f,
                            ),
                        ),
                    )
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            fontSize = rankSize,
                            fontWeight = FontWeight.Black,
                            brush = Brush.verticalGradient(
                                listOf(Color.White, VisionBlueBright, VisionSurfaceHigh),
                            ),
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.55f),
                                offset = Offset(2f, 3f),
                                blurRadius = 10f,
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
fun TvRowLabel(text: String, height: Dp, icon: ImageVector? = null, topTen: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).padding(start = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sin recuadro de fondo: el ícono queda limpio en cian junto al título.
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF67E8F9),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** El sello «TOP 10» de los encabezados de TV: cuadrado azul de marca con la cifra. */
@Composable
fun TvTopTenBadge(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val shape = RoundedCornerShape(size * 0.24f)
    // La tipografía acompaña al tamaño del sello (34 dp = el de siempre; 24 dp en filas compactas).
    val labelSize = (size.value * 0.206f).sp
    val numberSize = (size.value * 0.441f).sp
    Box(
        modifier = modifier
            .size(size)
            .background(Brush.verticalGradient(listOf(VisionBlue, VisionBlueDark)), shape)
            .border(BorderStroke(1.dp, VisionBlueBright.copy(alpha = 0.55f)), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "TOP",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = labelSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    lineHeight = labelSize,
                ),
            )
            Text(
                "10",
                style = TextStyle(
                    color = Color.White,
                    fontSize = numberSize,
                    fontWeight = FontWeight.Black,
                    lineHeight = numberSize,
                ),
            )
        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Ver más",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasFocus) Color.White else VisionBlue,
                )
                Icon(
                    VisionIcons.SeeAll,
                    contentDescription = null,
                    tint = if (hasFocus) Color.White else VisionBlueBright,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

