package com.chronosbvrx.visionplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionBlueDark
import java.util.Locale

/**
 * Portada en proporción 2:3 estilo streaming prémium: esquinas redondeadas,
 * badge de calificación (círculo translúcido con borde neón/azul), badge de estado opcional,
 * variante Top 10 con número de posición gigante estilizado, y decodificación acotada para
 * bajo consumo de RAM en dispositivos limitados.
 */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    meta: String? = null,
    score: Double = 0.0,
    badge: String? = null,
    rank: Int? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            // Contenedor de la portada con esquinas redondeadas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VisionSurfaceHigh),
            ) {
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

                // Gradiente inferior oscuro para contraste
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                )

                // Badge de Calificación (Esquina superior izquierda):
                // Fondo translúcido con borde neón/azul brillante
                if (score > 0.0) {
                    val formattedScore = if (score % 1.0 == 0.0) {
                        score.toInt().toString()
                    } else {
                        String.format(Locale.US, "%.1f", score)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = Color(0xDD0B1220),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .border(
                                BorderStroke(1.dp, VisionBlue.copy(alpha = 0.85f)),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "★ $formattedScore",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                // Badge de Estado (Esquina superior derecha):
                // Rectángulo neón con gradiente de marca (ej. NUEVO, TOP 10, 4K)
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
            }

            // Componente Card "Top 10" (Variante con Número):
            // Tipografía estilizada de gran tamaño (outline neón con degradado)
            // superpuesta en la esquina inferior izquierda
            if (rank != null && rank in 1..10) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 6.dp, y = 6.dp),
                ) {
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
                            color = VisionBlueBright,
                        ),
                    )
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            brush = Brush.verticalGradient(
                                listOf(Color.White, VisionBlueBright, VisionSurfaceHigh),
                            ),
                        ),
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Centered empty state. Without a [subtitle], only the title shows. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Row/section header ("Tendencias ahora", "Top 10") con enlace opcional "Ver todo →". */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            Text(
                text = "Ver todo →",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = VisionBlue,
                modifier = Modifier
                    .clickable(onClick = onSeeAll)
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** Píldora / Chip horizontal para selección de categorías y filtros con estilo prémium. */
@Composable
fun CategoryPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerBg = if (selected) {
        Brush.horizontalGradient(listOf(VisionBlue, VisionBlueDark))
    } else {
        Brush.horizontalGradient(listOf(VisionSurfaceHigh, VisionSurfaceHigh))
    }
    val borderColor = if (selected) VisionBlueBright else Color.White.copy(alpha = 0.08f)
    val textColor = if (selected) Color.White else VisionTextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerBg)
            .border(BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

/** Landscape (16:9) thumbnail with a red progress bar, for "Continuar viendo". */
@Composable
fun ContinueCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(VisionSurfaceHigh),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(0)
                    .size(480, 270)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(androidx.compose.ui.graphics.Color(0x66000000)),
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxSize().background(com.chronosbvrx.visionplus.ui.theme.VisionBlue))
            }
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A grey label above a group of settings. */
@Composable
fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

/** A filter chip in the brand red when selected. */
@Composable
fun VisionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = com.chronosbvrx.visionplus.ui.theme.VisionBlue,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
        ),
    )
}

/** A screen's big title ("Ajustes", "Plugins", "Descargas"). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp))
}
