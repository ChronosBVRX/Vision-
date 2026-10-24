package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.home.contract.ContinueItem
import com.chronosbvrx.visionplus.home.contract.FeedItem
import com.chronosbvrx.visionplus.home.contract.LiveItem
import com.chronosbvrx.visionplus.home.data.AppEntry

/** Póster 2:3 con su etiqueta debajo. El foco lo pone [FocusableSurface]. */
@Composable
fun PosterCard(
    item: FeedItem,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    onClick: () -> Unit,
) {
    Column(modifier.width(width)) {
        FocusableSurface(onClick = onClick, modifier = Modifier.width(width).height(height)) {
            Box(Modifier.fillMaxSize().background(HomeColors.SurfaceHigh)) {
                Monogram(item.title, Modifier.fillMaxSize(), size = 30)
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                )
                if (item.badge.isNotBlank() && item.badge.toIntOrNull() == null) {
                    Badge(
                        text = item.badge,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        color = if (item.badge == "NUEVO") HomeColors.Blue else Color.Black.copy(alpha = 0.55f),
                    )
                }
                if (rank != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(HomeShapes.pill)
                            .background(Color.Black.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = rank.toString(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                if (item.rating > 0.0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(HomeShapes.pill)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = HomeColors.Gold, modifier = Modifier.size(10.dp))
                        Text(
                            "%.1f".format(item.rating),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            color = HomeColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.year > 0) {
            Text(
                item.year.toString(),
                color = HomeColors.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** «Seguir viendo»: 16:9 con barra de avance y el capítulo ya resuelto. */
@Composable
fun ContinueCard(
    item: ContinueItem,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.width(width)) {
        FocusableSurface(onClick = onClick, modifier = Modifier.width(width).height(height)) {
            Box(Modifier.fillMaxSize().background(HomeColors.SurfaceHigh)) {
                Monogram(item.title, Modifier.fillMaxSize(), size = 28)
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(bottomScrim(0.94f)))
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .clip(HomeShapes.pill)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.episode.isNotBlank()) {
                        Text(
                            item.episode,
                            color = HomeColors.TextDim,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ThinProgress(item.progress)
                }
            }
        }
    }
}

/** Canal en vivo: logo, nombre y la guía de AHORA con su avance. */
@Composable
fun ChannelCard(
    item: LiveItem,
    height: Dp,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
    onClick: () -> Unit,
) {
    val current = currentProgram(item, nowMs)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = HomeShapes.card,
    ) {
        Box(Modifier.fillMaxSize().background(HomeColors.Surface)) {
            // El logo nunca se estira: entra centrado y con aire (la app hace lo mismo en la
            // rejilla de TV en vivo). El pie tiene nombre + AHORA + avance.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Monogram(item.name, Modifier.fillMaxSize(), size = 22)
                if (item.logo.isNotBlank()) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(
                    item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AHORA",
                        color = HomeColors.BlueBright,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        current.ifBlank { "Guía no disponible" },
                        color = if (current.isBlank()) HomeColors.TextFaint else Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                ThinProgress(liveProgress(item, nowMs))
            }
            Badge(
                text = "EN VIVO",
                dot = true,
                color = HomeColors.Live,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
}

/** Una app instalada: icono grande, etiqueta corta. */
@Composable
fun AppTile(
    app: AppEntry,
    tile: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier.width(tile + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FocusableSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(tile),
            shape = HomeShapes.tile,
            focusedScale = 1.07f,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HomeColors.SurfaceHigh)
                    .padding(tile * 0.2f),
                contentAlignment = Alignment.Center,
            ) {
                val icon = app.icon
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        app.label.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.label,
            color = HomeColors.TextDim,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Una fila horizontal con separación uniforme y sin recortes verticales. */
@Composable
fun RowScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    reducedEffects: Boolean,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        header()
        Spacer(Modifier.height(if (reducedEffects) 10.dp else 12.dp))
        content()
    }
}

/** Espacio entre filas del Inicio. */
val RowSpacing: Dp = 26.dp

/** Alineación del contenido del Inicio. */
val CardCorner = RoundedCornerShape(14.dp)
