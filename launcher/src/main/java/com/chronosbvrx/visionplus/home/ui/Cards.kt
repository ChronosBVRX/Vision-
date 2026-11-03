package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.chronosbvrx.visionplus.home.data.TvInputSource
import com.chronosbvrx.visionplus.home.data.TvSourceKind

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
                // Velo neutro: las miniaturas con fondo claro (p. ej. un póster blanco) se
                // integran a la paleta oscura en vez de brillar como una lámpara.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.52f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.90f),
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
                // Nivel oscuro parejo para que ninguna miniatura clara rompa la paleta.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
                Box(Modifier.fillMaxSize().background(bottomScrim(0.96f)))
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(HomeShapes.pill)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), HomeShapes.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(23.dp))
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                }
                // La barra de tiempo vive pegada al borde inferior, como en un reproductor:
                // 3 px de acento sobre un carril negro translúcido.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.65f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress.coerceIn(0, 100) / 100f)
                            .background(HomeColors.Cyan),
                    )
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
    showEpg: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val current = currentProgram(item, nowMs)
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(height),
        shape = HomeShapes.card,
    ) {
        Box(Modifier.fillMaxSize().background(HomeColors.Surface)) {
            // El logo nunca se estira: entra centrado y con aire (la app hace lo mismo en la
            // rejilla de TV en vivo). El pie tiene nombre + AHORA + avance.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (showEpg) 54.dp else 36.dp),
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
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    )
                    .padding(start = 9.dp, end = 9.dp, top = 20.dp, bottom = 7.dp),
            ) {
                Text(
                    item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showEpg) {
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
                }
            }
            // Misma barra de tiempo que «Seguir viendo»: 3 px pegados al borde inferior.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.65f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(liveProgress(item, nowMs).coerceIn(0, 100) / 100f)
                        .background(HomeColors.Cyan),
                )
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
            showIdleBorder = false,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(HomeColors.SurfaceHigh, HomeColors.Surface),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val icon = app.icon
                if (icon != null) {
                    // El icono de la app llena la baldosa: nada de un logo diminuto dentro
                    // de un cuadrito dibujado. Las máscaras y el arte los pone Android.
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        app.label.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
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

/** La entrada de video del televisor: TV abierta o HDMI, con su icono real si el equipo lo da. */
@Composable
fun InputCard(
    item: TvInputSource,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.width(width)) {
        FocusableSurface(onClick = onClick, modifier = Modifier.width(width).height(height)) {
            Box(Modifier.fillMaxSize().background(HomeColors.Surface)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InputIcon(item, size = 46)
                }
                Badge(
                    text = when (item.kind) {
                        TvSourceKind.TUNER -> "TV ABIERTA"
                        TvSourceKind.HDMI -> if (item.port > 0) "HDMI ${item.port}" else "HDMI"
                        TvSourceKind.OTHER -> "ENTRADA"
                    },
                    color = if (item.kind == TvSourceKind.TUNER) HomeColors.Success else HomeColors.Blue,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text(
                        item.label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (item.kind == TvSourceKind.TUNER) "TV en vivo del televisor" else "Fuente de video",
                        color = HomeColors.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta compacta de una entrada de TV. No es un botón grande: un icono propio del launcher,
 * el nombre y un fondo de vidrio oscuro. Entran todas sin scroll horizontal.
 */
@Composable
fun CompactInputCard(
    item: TvInputSource,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(width).height(height),
        shape = HomeShapes.tile,
        focusedScale = 1.05f,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (focused) {
                            listOf(HomeColors.SurfaceHigh, HomeColors.Deep)
                        } else {
                            listOf(HomeColors.Surface, HomeColors.Black)
                        },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InputGlyph(item = item, size = 34.dp, focused = focused)
                Spacer(Modifier.height(6.dp))
                Text(
                    item.label,
                    color = if (focused) Color.White else HomeColors.Text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * El atajo «TV en vivo» cuando la tele no tiene sintonizador físico: último canal abierto
 * desde el launcher o el primero de México. Misma baldosa compacta que las entradas.
 */
@Composable
fun CompactTvLiveCard(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(width).height(height),
        shape = HomeShapes.tile,
        focusedScale = 1.05f,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (focused) {
                            listOf(HomeColors.SurfaceHigh, HomeColors.Deep)
                        } else {
                            listOf(HomeColors.Surface, HomeColors.Black)
                        },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LiveGlyph(size = 34.dp, focused = focused)
                Spacer(Modifier.height(6.dp))
                Text(
                    "TV en vivo",
                    color = if (focused) Color.White else HomeColors.Text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LiveGlyph(size: Dp, focused: Boolean) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val brush = Brush.linearGradient(
            colors = if (focused) {
                listOf(Color.White, HomeColors.Cyan)
            } else {
                listOf(HomeColors.Cyan, HomeColors.BlueBright)
            },
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
        val stroke = w * 0.085f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(w * 0.10f, h * 0.25f),
            size = Size(w * 0.80f, h * 0.55f),
            cornerRadius = CornerRadius(w * 0.12f, h * 0.12f),
            style = Stroke(width = stroke),
        )
        val play = Path().apply {
            moveTo(w * 0.43f, h * 0.40f)
            lineTo(w * 0.63f, h * 0.52f)
            lineTo(w * 0.43f, h * 0.64f)
            close()
        }
        drawPath(play, brush)
        drawCircle(
            color = HomeColors.Live,
            radius = w * 0.085f,
            center = Offset(w * 0.82f, h * 0.20f),
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.035f,
            center = Offset(w * 0.82f, h * 0.20f),
        )
    }
}

/** Iconos propios (no los genéricos del sistema) para TV abierta, HDMI y AV. */
@Composable
private fun InputGlyph(item: TvInputSource, size: Dp, focused: Boolean) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val brush = Brush.linearGradient(
            colors = if (focused) {
                listOf(Color.White, HomeColors.Cyan)
            } else {
                listOf(HomeColors.Cyan, HomeColors.BlueBright)
            },
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
        val stroke = w * 0.085f
        when (item.kind) {
            TvSourceKind.TUNER -> {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(w * 0.10f, h * 0.27f),
                    size = Size(w * 0.80f, h * 0.50f),
                    cornerRadius = CornerRadius(w * 0.12f, h * 0.12f),
                    style = Stroke(width = stroke),
                )
                drawLine(brush, Offset(w * 0.30f, h * 0.27f), Offset(w * 0.20f, h * 0.10f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(brush, Offset(w * 0.70f, h * 0.27f), Offset(w * 0.80f, h * 0.10f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(brush, Offset(w * 0.50f, h * 0.77f), Offset(w * 0.50f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(brush, Offset(w * 0.38f, h * 0.88f), Offset(w * 0.62f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            TvSourceKind.HDMI -> {
                val port = Path().apply {
                    moveTo(w * 0.16f, h * 0.31f)
                    lineTo(w * 0.84f, h * 0.31f)
                    lineTo(w * 0.75f, h * 0.70f)
                    lineTo(w * 0.25f, h * 0.70f)
                    close()
                }
                drawPath(port, brush = brush, style = Stroke(width = stroke, join = StrokeJoin.Round))
                for (i in 0..3) {
                    val x = w * (0.34f + i * 0.11f)
                    drawLine(
                        brush,
                        Offset(x, h * 0.45f),
                        Offset(x, h * 0.57f),
                        strokeWidth = stroke * 0.55f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            TvSourceKind.OTHER -> {
                drawCircle(
                    brush = brush,
                    radius = w * 0.34f,
                    center = Offset(w * 0.5f, h * 0.48f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    brush = brush,
                    radius = w * 0.10f,
                    center = Offset(w * 0.5f, h * 0.48f),
                    style = Stroke(width = stroke),
                )
                drawLine(brush, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
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
