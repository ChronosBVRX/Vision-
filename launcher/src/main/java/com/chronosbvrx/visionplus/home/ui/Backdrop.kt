package com.chronosbvrx.visionplus.home.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.home.data.HeroSlide

/**
 * El fondo cinematográfico a pantalla completa. Cambia con la diapositiva activa del carrusel
 * y aplica tres capas de protección para que la interfaz siempre se lea:
 *
 * 1. Velo superior (la barra de estado/marca).
 * 2. Degradado de abajo hacia arriba: #0B0F19 sólido en la base.
 * 3. Degradado de izquierda a derecha: ~80 % a la izquierda, transparente a la derecha.
 */
@Composable
fun CinematicBackdrop(
    slide: HeroSlide?,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(HomeColors.Deep, HomeColors.Black),
                ),
            ),
    ) {
        Crossfade(
            targetState = slide,
            animationSpec = tween(if (reduced) 450 else 750),
            label = "cinematicBackdrop",
        ) { current ->
            if (current != null) {
                BackdropArt(current, reduced)
            }
        }
        // El resto del Inicio (filas, apps, canales) descansa sobre un fondo oscuro legible.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to HomeColors.Black.copy(alpha = 0.62f),
                        0.20f to Color.Transparent,
                        0.58f to HomeColors.Deep.copy(alpha = 0.34f),
                        1f to HomeColors.Deep,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to HomeColors.Deep.copy(alpha = 0.82f),
                        0.34f to HomeColors.Deep.copy(alpha = 0.60f),
                        0.68f to HomeColors.Deep.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, HomeColors.Black.copy(alpha = 0.48f)),
                    ),
                ),
        )
        // Un destello cian mínimo arriba a la izquierda: profundidad sin volver al azul marino.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HomeColors.Cyan.copy(alpha = 0.055f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun BackdropArt(slide: HeroSlide, reduced: Boolean) {
    val context = LocalContext.current
    val url = slide.backdrop.ifBlank { slide.poster }
    if (url.isBlank()) {
        val localArt = if (slide.sports) sportBackdropFor(slide) else 0
        if (localArt != 0) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(localArt),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.92f,
                    modifier = Modifier.fillMaxSize(),
                )
                // El emblema de la liga/equipo personaliza la foto del deporte: la misma
                // referencia de MotoGP no se siente igual para NFL que para la NBA.
                val mark = slide.leagueLogo.ifBlank {
                    slide.homeLogo.ifBlank { slide.awayLogo }
                }
                if (mark.isNotBlank()) {
                    AsyncImage(
                        model = mark,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alpha = 0.10f,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(0.56f)
                            .width(360.dp)
                            .padding(end = 100.dp),
                    )
                }
            }
            return
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(HomeColors.Deep, HomeColors.Surface, HomeColors.Black),
                        ),
                    ),
            )
            // Sin foto del evento, el emblema de la liga/equipo se dibuja enorme y
            // translúcido: el fondo sigue cambiando con la diapositiva sin inventar una foto.
            val mark = slide.leagueLogo.ifBlank { slide.homeLogo.ifBlank { slide.awayLogo } }
            if (mark.isNotBlank()) {
                AsyncImage(
                    model = mark,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = 0.12f,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.72f)
                        .width(420.dp)
                        .padding(end = 80.dp),
                )
            }
        }
        return
    }
    val model = remember(context, url, reduced) {
        bannerImageRequest(
            context = context,
            url = url,
            width = if (reduced) 1280 else 1920,
            height = if (reduced) 720 else 1080,
        )
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}
