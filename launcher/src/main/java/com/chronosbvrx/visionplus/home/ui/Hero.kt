package com.chronosbvrx.visionplus.home.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.home.HomeMetrics
import com.chronosbvrx.visionplus.home.contract.durationLabel
import com.chronosbvrx.visionplus.home.data.HeroSlide
import com.chronosbvrx.visionplus.home.data.bannerArtCacheKey
import kotlinx.coroutines.delay

private const val ROTATE_MS_NORMAL = 9_000
private const val ROTATE_MS_REDUCED = 13_000
private const val TICK_MS = 100L

/**
 * El hero cinematográfico: arte a sangre, Ken Burns lento, degradados de cine y un
 * marcador grande cuando la diapositiva es un partido en vivo.
 *
 * Rota sola; se detiene mientras el usuario tiene el foco en sus botones (para que
 * pueda leer/decidir) y al llegar a equipos modestos cambia las animaciones caras por
 * un crossfade limpio. El botón de foco SIEMPRE es el mismo composable: la rotación
 * nunca roba el mando.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HeroStage(
    items: List<HeroSlide>,
    metrics: HomeMetrics,
    reduced: Boolean,
    landing: FocusRequester,
    upTarget: FocusRequester? = null,
    paused: Boolean = false,
    onPlay: (HeroSlide) -> Unit,
    onOpen: (HeroSlide) -> Unit,
    onSportsHub: () -> Unit = {},
    modifier: Modifier = Modifier,
    onControlFocus: (Boolean) -> Unit = {},
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, items.lastIndex)
    val current = items[safeIndex]
    val pausedState = rememberUpdatedState(paused)
    var progress by remember { mutableFloatStateOf(0f) }
    val compact = metrics.heroHeight < 240.dp
    val context = LocalContext.current

    // La siguiente diapositiva se deja lista mientras se ve la actual: el crossfade
    // entra con la imagen ya decodificada (cero espera al girar el hero).
    LaunchedEffect(items, safeIndex) {
        if (items.size <= 1) return@LaunchedEffect
        val next = items[(safeIndex + 1) % items.size]
        if (next.sports) return@LaunchedEffect
        val url = next.backdrop.ifBlank { next.poster }
        if (url.isBlank()) return@LaunchedEffect
        runCatching { Coil.imageLoader(context).enqueue(bannerImageRequest(context, url)) }
    }

    LaunchedEffect(items, safeIndex, reduced) {
        progress = 0f
        if (items.size <= 1) return@LaunchedEffect
        val total = if (reduced) ROTATE_MS_REDUCED else ROTATE_MS_NORMAL
        var elapsed = 0L
        while (true) {
            delay(TICK_MS)
            if (pausedState.value) continue
            elapsed += TICK_MS
            progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
            if (elapsed >= total) {
                index = (safeIndex + 1) % items.size
                break
            }
        }
    }

    Box(modifier.fillMaxWidth().height(metrics.heroHeight).clipToBounds()) {
        // --- Arte de fondo -------------------------------------------------------
        Crossfade(
            targetState = current.id,
            animationSpec = tween(650),
            label = "heroArt",
        ) { id ->
            val slide = items.firstOrNull { it.id == id } ?: current
            HeroBackdrop(slide = slide, reduced = reduced)
        }

        // --- Luz y degradados de cine -------------------------------------------
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(
                        HomeColors.Blue.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to HomeColors.Black.copy(alpha = 0.97f),
                    0.42f to HomeColors.Black.copy(alpha = 0.78f),
                    0.74f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to HomeColors.Black.copy(alpha = 0.58f),
                    0.30f to Color.Transparent,
                    0.66f to Color.Transparent,
                    1f to HomeColors.Black,
                ),
            ),
        )

        // --- Crestas ambientales del partido (lado derecho) ----------------------
        if (current.sports) {
            SportsAmbientCrests(current, metrics)
        }

        // --- Texto + acciones ----------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = metrics.horizontalPadding,
                    bottom = if (compact) 14.dp else 24.dp,
                )
                .widthIn(max = 660.dp),
        ) {
            Crossfade(
                targetState = current.id,
                animationSpec = tween(320),
                label = "heroText",
            ) { id ->
                val slide = items.firstOrNull { it.id == id } ?: current
                Column {
                    HeroEyebrow(slide = slide, reduced = reduced)
                    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                    Text(
                        slide.title,
                        color = Color.White,
                        fontSize = (if (slide.sports && compact) metrics.titleSize - 4 else metrics.titleSize).sp,
                        lineHeight = (if (slide.sports && compact) metrics.titleSize else metrics.titleSize + 4).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.8).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                    if (slide.sports) {
                        SportsScoreboard(slide = slide, compact = compact)
                    } else {
                        MovieMetaLine(slide)
                        if (slide.synopsis.isNotBlank() && metrics.heroHeight >= 240.dp) {
                            Spacer(Modifier.height(9.dp))
                            Text(
                                slide.synopsis,
                                color = HomeColors.TextDim,
                                fontSize = metrics.synopsisSize.sp,
                                lineHeight = (metrics.synopsisSize + 5).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val primaryLabel = when {
                    current.sports -> if (current.live) "Ver en vivo" else "Ver partido"
                    current.playLink.isNotBlank() -> "Reproducir"
                    else -> "Ver detalles"
                }
                HomeButton(
                    label = primaryLabel,
                    onClick = { if (current.playLink.isNotBlank()) onPlay(current) else onOpen(current) },
                    primary = true,
                    icon = if (current.sports) Icons.Rounded.SportsSoccer else Icons.Rounded.PlayArrow,
                    modifier = Modifier
                        .focusRequester(landing)
                        // A la izquierda del primer botón no hay nada: el foco no debe
                        // saltar a la barra superior por geometría.
                        .focusProperties { left = FocusRequester.Cancel }
                        .then(if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier),
                    onFocusChanged = onControlFocus,
                )
                HomeButton(
                    label = if (current.sports) "Agenda completa" else "Más información",
                    onClick = {
                        if (current.sports) onSportsHub() else onOpen(current)
                    },
                    icon = if (current.sports) Icons.Rounded.LiveTv else Icons.Rounded.Info,
                    modifier = Modifier
                        .focusProperties { right = FocusRequester.Cancel }
                        .then(if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier),
                    onFocusChanged = onControlFocus,
                )
            }
        }

        // --- Indicadores con progreso -------------------------------------------
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = metrics.horizontalPadding,
                        bottom = if (compact) 16.dp else 26.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.indices.forEach { i ->
                    if (i == safeIndex) {
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.26f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = progress
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .background(Color.White),
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.32f)),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// Fondo
// ---------------------------------------------------------------------------------

@Composable
private fun HeroBackdrop(slide: HeroSlide, reduced: Boolean) {
    val context = LocalContext.current
    val zoom = remember(slide.id) { Animatable(1f) }
    LaunchedEffect(slide.id, reduced) {
        zoom.snapTo(1f)
        if (!reduced) {
            zoom.animateTo(1.07f, animationSpec = tween(ROTATE_MS_NORMAL, easing = LinearEasing))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.value
                scaleY = zoom.value
            },
    ) {
        if (slide.sports) {
            SportsBackdrop(slide)
        } else {
            val art = slide.backdrop.ifBlank { slide.poster }
            val posterModel = remember(context, slide.poster) {
                slide.poster.takeIf { it.isNotBlank() }?.let { bannerImageRequest(context, it) }
            }
            val artModel = remember(context, art) {
                art.takeIf { it.isNotBlank() }?.let { bannerImageRequest(context, it) }
            }
            // Póster de respaldo detrás: si el arte panorámico no carga (CDN), el hero
            // no se queda en negro.
            if (posterModel != null && art != slide.poster) {
                AsyncImage(
                    model = posterModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (artModel != null) {
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0D1D33), Color(0xFF0A1120), HomeColors.Black),
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SportsBackdrop(slide: HeroSlide) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF04121D),
                        Color(0xFF0A2238),
                        Color(0xFF10152A),
                    ),
                ),
            ),
    ) {
        // Haces de luz diagonales: dan profundidad sin depender de una foto externa.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.045f),
                        Color.Transparent,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1400f, 900f),
                ),
            ),
        )
        if (slide.backdrop.isNotBlank()) {
            val context = LocalContext.current
            val model = remember(context, slide.backdrop) {
                bannerImageRequest(context, slide.backdrop)
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.34f,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BoxScope.SportsAmbientCrests(slide: HeroSlide, metrics: HomeMetrics) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = metrics.horizontalPadding, start = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmbientCrest(slide.homeLogo, slide.homeName, 0.16f)
        AmbientCrest(slide.awayLogo, slide.awayName, 0.12f)
    }
}

@Composable
private fun AmbientCrest(logo: String, name: String, alpha: Float) {
    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        if (logo.isNotBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = alpha,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Monogram(name, Modifier.fillMaxSize(), size = 80)
        }
    }
}

// ---------------------------------------------------------------------------------
// Texto
// ---------------------------------------------------------------------------------

@Composable
private fun HeroEyebrow(slide: HeroSlide, reduced: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(20.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(HomeColors.BlueBright, Color.Transparent),
                    ),
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            slide.eyebrow.ifBlank {
                if (slide.sports) "DEPORTES" else "DESTACADO HOY"
            },
            color = HomeColors.BlueBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            maxLines = 1,
        )
        if (slide.badge.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            HeroBadge(text = slide.badge, live = slide.live, reduced = reduced)
        }
    }
}

@Composable
private fun HeroBadge(text: String, live: Boolean, reduced: Boolean) {
    Row(
        modifier = Modifier
            .clip(HomeShapes.pill)
            .background(if (live) HomeColors.Live.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (live) LiveDot(reduced)
        Text(
            text,
            color = if (live) Color(0xFFFFB4B6) else HomeColors.TextDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveDot(reduced: Boolean) {
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "livePulse")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "liveAlpha",
        )
        value
    }
    Box(Modifier.size(6.dp).clip(CircleShape).background(HomeColors.Live.copy(alpha = alpha)))
}

@Composable
private fun MovieMetaLine(slide: HeroSlide) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (slide.year > 0) {
            Text(slide.year.toString(), color = HomeColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        if (slide.rating > 0.0) {
            Icon(Icons.Rounded.Star, null, tint = HomeColors.Gold, modifier = Modifier.size(12.dp))
            Text(
                "%.1f".format(slide.rating),
                color = HomeColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val duration = durationLabel(slide.durationMin)
        if (duration.isNotBlank()) {
            Text(duration, color = HomeColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SportsScoreboard(slide: HeroSlide, compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp),
    ) {
        TeamBlock(
            name = slide.homeName.ifBlank { "Local" },
            logo = slide.homeLogo,
            compact = compact,
            alignEnd = true,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val score = when {
                slide.homeScore != null && slide.awayScore != null ->
                    "${slide.homeScore}  ${slide.awayScore}"
                slide.homeScore != null -> "${slide.homeScore}  -"
                slide.awayScore != null -> "-  ${slide.awayScore}"
                else -> "VS"
            }
            Text(
                score,
                color = Color.White,
                fontSize = if (compact) 28.sp else 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
            Text(
                slide.detail.ifBlank { slide.subtitle }.ifBlank { "EN VIVO" },
                color = if (slide.live) Color(0xFFFF8A8D) else HomeColors.TextDim,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        TeamBlock(
            name = slide.awayName.ifBlank { "Visitante" },
            logo = slide.awayLogo,
            compact = compact,
            alignEnd = false,
        )
        if (slide.leagueLogo.isNotBlank() && !compact) {
            Spacer(Modifier.width(6.dp))
            AsyncImage(
                model = slide.leagueLogo,
                contentDescription = slide.league,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TeamBlock(
    name: String,
    logo: String,
    compact: Boolean,
    alignEnd: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (alignEnd) {
            Text(
                name,
                color = HomeColors.Text,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = if (compact) 84.dp else 120.dp),
            )
        }
        Box(
            Modifier
                .size(if (compact) 30.dp else 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            } else {
                Monogram(name, Modifier.fillMaxSize(), size = if (compact) 18 else 24)
            }
        }
        if (!alignEnd) {
            Text(
                name,
                color = HomeColors.Text,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (compact) 84.dp else 120.dp),
            )
        }
    }
}

/**
 * Una petición de arte con clave estable: el `?v=` diario del CDN cambia la URL, pero
 * no la imagen. Coil reutiliza la copia en disco/memoria y no vuelve a descargarla.
 */
private fun bannerImageRequest(context: android.content.Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(1280, 720)
        .memoryCacheKey(bannerArtCacheKey(url))
        .diskCacheKey(bannerArtCacheKey(url))
        .build()
