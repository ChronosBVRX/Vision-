package com.chronosbvrx.visionplus.home.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.home.HomeMetrics
import com.chronosbvrx.visionplus.home.contract.HeroItem
import com.chronosbvrx.visionplus.home.contract.durationLabel
import kotlinx.coroutines.delay

/**
 * El hero: arte a sangre, título grande y dos acciones. Rota solo cada 10 s (apagado en
 * equipos modestos), sin mover el foco jamás.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HeroStage(
    items: List<HeroItem>,
    metrics: HomeMetrics,
    reduced: Boolean,
    landing: FocusRequester,
    onPlay: (HeroItem) -> Unit,
    onOpen: (HeroItem) -> Unit,
    modifier: Modifier = Modifier,
    onPrimaryFocus: (Boolean) -> Unit = {},
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items, reduced) {
        if (reduced || items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            index = (index + 1) % items.size
        }
    }
    val safeIndex = index.coerceIn(0, items.lastIndex)
    val current = items[safeIndex]

    Box(modifier.fillMaxWidth().height(metrics.heroHeight)) {
        // Arte de fondo: el póster 2:3 recortado y oscurecido hace de «arte ambiental».
        Crossfade(
            targetState = current.ref,
            animationSpec = tween(700),
            label = "heroArt",
        ) { ref ->
            val item = items.firstOrNull { it.ref == ref } ?: current
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            }
        }
        // Tres capas de degradado: izquierda (texto), abajo (hilera) y arriba (barra).
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to HomeColors.Black.copy(alpha = 0.96f),
                    0.45f to HomeColors.Black.copy(alpha = 0.72f),
                    0.78f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to HomeColors.Black.copy(alpha = 0.55f),
                    0.35f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to HomeColors.Black,
                ),
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = metrics.horizontalPadding,
                    bottom = if (metrics.heroHeight < 260.dp) 16.dp else 24.dp,
                )
                .widthIn(max = 640.dp),
        ) {
            if (current.badge.isNotBlank()) {
                Badge(text = current.badge, modifier = Modifier)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                current.title,
                color = Color.White,
                fontSize = metrics.titleSize.sp,
                lineHeight = (metrics.titleSize + 4).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            MetaLine(current)
            if (current.synopsis.isNotBlank() && metrics.heroHeight >= 240.dp) {
                Spacer(Modifier.height(9.dp))
                Text(
                    current.synopsis,
                    color = HomeColors.TextDim,
                    fontSize = metrics.synopsisSize.sp,
                    lineHeight = (metrics.synopsisSize + 5).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val playable = current.playLink.isNotBlank()
                HomeButton(
                    label = if (playable) "Reproducir" else "Ver detalles",
                    onClick = { if (playable) onPlay(current) else onOpen(current) },
                    primary = true,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier
                        .focusRequester(landing)
                        // A la izquierda del primer botón no hay nada: el foco no debe
                        // saltar a la barra superior por geometría.
                        .focusProperties { left = FocusRequester.Cancel },
                    onFocusChanged = onPrimaryFocus,
                )
                HomeButton(
                    label = "Más información",
                    onClick = { onOpen(current) },
                    icon = Icons.Rounded.Info,
                    modifier = Modifier.focusProperties { right = FocusRequester.Cancel },
                )
            }
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = metrics.horizontalPadding, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == safeIndex) 7.dp else 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == safeIndex) Color.White else Color.White.copy(alpha = 0.32f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaLine(item: HeroItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val kind = kindLabel(item.kind)
        if (kind.isNotBlank()) {
            Text(
                kind.uppercase(),
                color = HomeColors.BlueBright,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (item.year > 0) {
            Text(item.year.toString(), color = HomeColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        if (item.rating > 0.0) {
            Icon(Icons.Rounded.Star, null, tint = HomeColors.Gold, modifier = Modifier.size(12.dp))
            Text(
                "%.1f".format(item.rating),
                color = HomeColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val duration = durationLabel(item.durationMin)
        if (duration.isNotBlank()) {
            Text(duration, color = HomeColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun kindLabel(kind: String): String = when (kind) {
    "movie" -> "Película"
    "series" -> "Serie"
    "anime" -> "Anime"
    "kids" -> "Infantil"
    else -> ""
}
