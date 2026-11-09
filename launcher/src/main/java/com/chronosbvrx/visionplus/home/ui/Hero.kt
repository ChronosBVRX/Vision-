package com.chronosbvrx.visionplus.home.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.res.painterResource
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
    /** Un launcher puede cambiar el segundo botón por la puerta a la app principal. */
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    /** El Bento es una tarjeta estrecha: usa el layout compacto aunque tenga altura. */
    forceCompact: Boolean = false,
    eyebrowOverride: String? = null,
    onControlFocus: (Boolean) -> Unit = {},
    /** La diapositiva visible manda también el fondo a pantalla completa del Inicio. */
    onActiveSlide: (HeroSlide) -> Unit = {},
    /**
     * El Inicio pinta el arte una sola vez, a pantalla completa, detrás de todo. El hero
     * sigue dibujando sus degradados y contenido, pero no vuelve a pintar la foto: si lo
     * hiciera, la misma imagen aparecería recortada dos veces y se vería el corte/duplicado.
     */
    showArt: Boolean = true,
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, items.lastIndex)
    val current = items[safeIndex]
    LaunchedEffect(current.id) { onActiveSlide(current) }
    val pausedState = rememberUpdatedState(paused)
    var progress by remember { mutableFloatStateOf(0f) }
    val compact = forceCompact || metrics.heroHeight < 240.dp
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
        // En el escritorio Bento el arte se pinta DENTRO de la tarjeta del destacado.
        // La variante sin arte queda para un shell que componga el fondo por su cuenta.
        if (showArt) {
            Crossfade(
                targetState = current.id,
                animationSpec = tween(650),
                label = "heroArt",
            ) { id ->
                val slide = items.firstOrNull { it.id == id } ?: current
                HeroBackdrop(slide = slide, reduced = reduced)
            }
        }

        // --- Luz y degradados de cine -------------------------------------------
        // Un destello cian muy tenue en vez del azul saturado: el color fuerte se reserva
        // para EN VIVO, la barra de progreso y el foco.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(
                        HomeColors.Cyan.copy(alpha = 0.07f),
                        Color.Transparent,
                    ),
                ),
            ),
        )
        // De izquierda a derecha: 80 % de opacidad a la izquierda para que el texto y los
        // botones siempre tengan contraste; el arte respira hacia el centro y la derecha.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to HomeColors.Deep.copy(alpha = 0.97f),
                    0.38f to HomeColors.Deep.copy(alpha = 0.80f),
                    0.72f to HomeColors.Deep.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
            ),
        )
        // De abajo hacia arriba: #0B0F19 sólido en la base para que las filas descansen
        // sobre un fondo oscuro legible.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to HomeColors.Black.copy(alpha = 0.58f),
                    0.28f to Color.Transparent,
                    0.64f to Color.Transparent,
                    // Con el arte a pantalla completa, el fondo ya cierra en #0B0F19:
                    // el hero no debe pintar una banda sólida que marque el corte.
                    1f to if (showArt) HomeColors.Deep else Color.Transparent,
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
                    HeroEyebrow(slide = slide, reduced = reduced, eyebrowOverride = eyebrowOverride)
                    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                    Text(
                        heroDisplayTitle(slide),
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
                    current.sports -> if (current.live) "Ver en directo" else "Ver partido"
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
                    label = secondaryLabel
                        ?: if (current.sports) "Agenda completa" else "Más información",
                    onClick = {
                        when {
                            onSecondary != null -> onSecondary.invoke()
                            current.sports -> onSportsHub()
                            else -> onOpen(current)
                        }
                    },
                    icon = when {
                        secondaryLabel != null -> Icons.AutoMirrored.Rounded.OpenInNew
                        current.sports -> Icons.Rounded.LiveTv
                        else -> Icons.Rounded.Info
                    },
                    // A la derecha vive la columna de sistema del Bento (Vision+ TV y
                    // entradas): no se bloquea el salto geométrico del mando.
                    modifier = if (upTarget != null) {
                        Modifier.focusProperties { up = upTarget }
                    } else {
                        Modifier
                    },
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
        } else {
            // La agenda no manda portada: se usa una foto de referencia del deporte (o de
            // la liga) empaquetada en el APK. Los escudos de los equipos van encima.
            val localArt = sportBackdropFor(slide)
            if (localArt != 0) {
                Image(
                    painter = painterResource(localArt),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.56f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SportsAmbientCrests(slide: HeroSlide, metrics: HomeMetrics) {
    // Sin escudos reales no se inventan monogramas gigantes: el arte del evento manda.
    if (slide.homeLogo.isBlank() && slide.awayLogo.isBlank()) {
        if (slide.leagueLogo.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = metrics.horizontalPadding, start = 40.dp)
                    .size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = slide.leagueLogo,
                    contentDescription = slide.league,
                    contentScale = ContentScale.Fit,
                    alpha = 0.10f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }
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
private fun HeroEyebrow(slide: HeroSlide, reduced: Boolean, eyebrowOverride: String? = null) {
    if (slide.sports) {
        // Una sola etiqueta compacta: punto rojo pulsante + EN VIVO. Nada de repetir
        // «DEPORTES · EN VIVO» y además una píldora roja con el mismo texto.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(HomeShapes.pill)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), HomeShapes.pill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (slide.live) {
                    LiveDot(reduced)
                } else {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(HomeColors.Cyan))
                }
                Text(
                    if (slide.live) "EN VIVO" else "PRÓXIMO",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                    maxLines = 1,
                )
            }
            val subject = slide.title.takeIf { title ->
                title.isNotBlank() && !title.contains(" vs ", ignoreCase = true)
            } ?: slide.league
            val season = seasonLabel(slide.startMs)
            val context = listOf(subject, season).filter { it.isNotBlank() }.joinToString(" • ")
            if (context.isNotBlank()) {
                Spacer(Modifier.width(11.dp))
                Text(
                    context,
                    color = HomeColors.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(20.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(HomeColors.Cyan, Color.Transparent),
                    ),
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            eyebrowOverride ?: slide.eyebrow.ifBlank { "DESTACADO HOY" },
            color = HomeColors.Cyan,
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
            .border(1.dp, Color.White.copy(alpha = 0.12f), HomeShapes.pill)
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

/** Un evento individual (MotoGP, GP, pelea…) no es un «Local vs Visitante»: se pinta solo. */
private fun heroDisplayTitle(slide: HeroSlide): String {
    val parts = slide.title.split(" vs ", " VS ", " Vs ")
    return if (parts.size == 2 && parts[0].trim().equals(parts[1].trim(), ignoreCase = true)) {
        parts[0].trim()
    } else {
        slide.title
    }
}

@Composable
private fun SportsScoreboard(slide: HeroSlide, compact: Boolean) {
    val home = slide.homeName.trim()
    val away = slide.awayName.trim()
    val single = home.isBlank() || away.isBlank() || home.equals(away, ignoreCase = true)
    if (single) {
        SingleEventScoreboard(slide = slide, compact = compact)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 22.dp),
    ) {
        TeamBlock(
            name = slide.homeName.ifBlank { "Local" },
            logo = slide.homeLogo,
            compact = compact,
            alignEnd = true,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val hasScore = slide.homeScore != null || slide.awayScore != null
            val score = when {
                slide.homeScore != null && slide.awayScore != null ->
                    "${slide.homeScore} - ${slide.awayScore}"
                slide.homeScore != null -> "${slide.homeScore} -"
                slide.awayScore != null -> "- ${slide.awayScore}"
                else -> "VS"
            }
            Text(
                score,
                color = if (hasScore) Color.White else Color.White.copy(alpha = 0.58f),
                fontSize = if (hasScore) {
                    if (compact) 30.sp else 40.sp
                } else {
                    if (compact) 22.sp else 28.sp
                },
                fontWeight = if (hasScore) FontWeight.Black else FontWeight.Light,
                letterSpacing = if (hasScore) 0.5.sp else 3.sp,
                maxLines = 1,
            )
            // La hora o el minuto, con números tabulares debajo del VS: nunca baila.
            val timeLine = when {
                slide.live -> slide.detail.ifBlank { slide.subtitle }.ifBlank { "EN VIVO" }
                slide.timeText.isNotBlank() -> listOf(slide.dateText, slide.timeText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                else -> slide.detail.ifBlank { slide.subtitle }
            }
            if (timeLine.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    timeLine,
                    color = if (slide.live) Color(0xFFFF9A9D) else HomeColors.TextDim,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        TeamBlock(
            name = slide.awayName.ifBlank { "Visitante" },
            logo = slide.awayLogo,
            compact = compact,
            alignEnd = false,
        )
        if (slide.leagueLogo.isNotBlank() && !compact) {
            Spacer(Modifier.width(4.dp))
            AsyncImage(
                model = slide.leagueLogo,
                contentDescription = slide.league,
                contentScale = ContentScale.Fit,
                alpha = 0.82f,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SingleEventScoreboard(slide: HeroSlide, compact: Boolean) {
    val timeLine = when {
        slide.timeText.isNotBlank() -> listOf(slide.dateText, slide.timeText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        slide.detail.isNotBlank() && !slide.detail.equals("EN VIVO", ignoreCase = true) -> slide.detail
        else -> ""
    }
    val hasLeague = slide.league.isNotBlank()
    if (slide.leagueLogo.isBlank() && !hasLeague && timeLine.isBlank()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        if (slide.leagueLogo.isNotBlank()) {
            AsyncImage(
                model = slide.leagueLogo,
                contentDescription = slide.league,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 40.dp else 56.dp),
            )
        }
        Column {
            if (hasLeague) {
                Text(
                    slide.league,
                    color = HomeColors.TextDim,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
            }
            if (timeLine.isNotBlank()) {
                if (hasLeague) Spacer(Modifier.height(2.dp))
                Text(
                    timeLine,
                    color = HomeColors.Text,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
            }
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
        horizontalArrangement = Arrangement.spacedBy(9.dp),
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
                modifier = Modifier.widthIn(max = if (compact) 92.dp else 130.dp),
            )
        }
        // Sin cuadro gris: el logo se dibuja transparente y a su tamaño natural.
        Box(
            Modifier.size(if (compact) 38.dp else 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Monogram(name, Modifier.fillMaxSize(), size = if (compact) 20 else 28)
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
                modifier = Modifier.widthIn(max = if (compact) 92.dp else 130.dp),
            )
        }
    }
}

/** «Temporada 2026» a partir de la fecha del evento (vacío si no hay fecha). */
private fun seasonLabel(startMs: Long): String {
    if (startMs <= 0) return ""
    val year = java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("America/Mexico_City") }
        .format(java.util.Date(startMs))
    return "Temporada $year"
}

/**
 * Una petición de arte con clave estable: el `?v=` diario del CDN cambia la URL, pero
 * no la imagen. Coil reutiliza la copia en disco/memoria y no vuelve a descargarla.
 */
internal fun bannerImageRequest(
    context: android.content.Context,
    url: String,
    width: Int = 1920,
    height: Int = 1080,
): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(width, height)
        // La memoria distingue el tamaño (héroe 1280 vs fondo 1920); el disco no, para no
        // volver a bajar la misma foto por cada resolución.
        .memoryCacheKey("${bannerArtCacheKey(url)}@${width}x$height")
        .diskCacheKey(bannerArtCacheKey(url))
        .build()
