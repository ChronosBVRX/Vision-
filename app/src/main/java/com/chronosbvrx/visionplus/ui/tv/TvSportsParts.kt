package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.ui.sports.GeeTiles
import com.chronosbvrx.visionplus.ui.sports.geeEventTitle
import com.chronosbvrx.visionplus.ui.sports.geeSportIcon
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

private val FOCUS_CYAN = Color(0xFF7DD3FC)
private val CARD_SHAPE = RoundedCornerShape(18.dp)

/** Píldora de cristal de Deportes: la misma gramática que TV en vivo. */
@Composable
internal fun TvSportsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    logo: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "sports-chip-scale")
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = RoundedCornerShape(50),
                ambientColor = Color(0x44000000),
                spotColor = Color(0x44000000),
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x2638BDF8) else Color(0x14FFFFFF),
            focusedContainerColor = Color.White,
            contentColor = if (selected) Color.White else Color(0xFFCBD5E1),
            focusedContentColor = Color(0xFF090D16),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (focused) Color(0xFF090D16) else Color(0xFF38BDF8)),
                )
            }
            if (logo != null && logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logo)
                        .size(32, 32)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    contentScale = ContentScale.Fit,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/**
 * Hero cinematográfico de Deportes: el partido principal del día con el mismo
 * lenguaje que el hero de TV en vivo (gradiente, escudos, marcador y píldora roja).
 */
@Composable
internal fun TvSportsHero(
    event: GeeSportsSource.GeeEvent?,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VisionSurface),
    ) {
        // Arte ambiental: el logo de la liga muy bajito a la derecha.
        if (event != null && event.leagueLogo.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(event.leagueLogo)
                    .size(480, 480)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 56.dp)
                    .size(230.dp)
                    .alpha(0.13f),
                contentScale = ContentScale.Fit,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            VisionBlack,
                            VisionSurface.copy(alpha = 0.98f),
                            VisionSurfaceHigh.copy(alpha = 0.72f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, VisionBlack.copy(alpha = 0.92f)),
                    ),
                ),
        )
        if (event == null) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("DEPORTES · PRINCIPAL", color = Color(0xFF67E8F9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Sin eventos principales", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("Cambia a «Todas las ligas» para ver el catálogo completo.", color = VisionTextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            return@Box
        }
        val live = event.live || GeeAgenda.phaseOf(event, nowMs) == GeeAgenda.Phase.Live
        val title = geeEventTitle(event)
        val league = GeeAgenda.leagueLabel(event).ifBlank { event.category }
        val tvLine = event.broadcasts.take(3).joinToString(" · ")
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, top = 10.dp, bottom = 10.dp, end = 14.dp)
                .fillMaxWidth(0.58f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val detail = if (live) {
                event.detail.ifBlank { "EN VIVO" }
            } else {
                listOf(
                    if (event.dateIso == SportsSource.todayIso()) "Hoy" else event.dateText,
                    event.time.take(5),
                ).filter { it.isNotBlank() }.joinToString(" · ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    colors = SurfaceDefaults.colors(containerColor = if (live) VisionLiveRed else Color(0x3338BDF8)),
                ) {
                    Text(
                        if (live) "EN VIVO" else "PRÓXIMO",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    league,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = if (live) Color(0xFFFCA5A5) else Color(0xFF67E8F9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tvLine.isNotBlank()) {
                Text(
                    "TV: $tvLine",
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    GeeTiles.streams(event),
                    color = VisionTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Marcador grande a la derecha: la pieza que más se reconoce de un partido.
        val homeName = event.homeName.ifBlank { event.teamA }
        val awayName = event.awayName.ifBlank { event.teamB }
        if (homeName.isNotBlank() && awayName.isNotBlank()) {
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvSportsCrest(event.homeLogo, homeName, 46.dp)
                Text(
                    GeeTiles.score(event),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                TvSportsCrest(event.awayLogo, awayName, 46.dp)
            }
        }
    }
}

@Composable
private fun TvSportsCrest(logo: String, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logo)
                    .size(160, 160)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                GeeTiles.monogram(name),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Encabezado de bloque: el mismo tono y contador que la agenda de TV en vivo. */
@Composable
internal fun TvSportsSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title.equals("En vivo", ignoreCase = true)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(VisionLiveRed))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (title.equals("En vivo", ignoreCase = true)) Color.White else VisionTextSecondary,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.labelMedium, color = VisionTextSecondary)
    }
}

/**
 * Tarjeta-mosaico 16:9 de Deportes: escudos, marcador, liga y transmisión.
 * Es la versión deportiva de la tarjeta 16:10 de TV en vivo: mismo foco cian,
 * mismo cristal y el mismo pie de estado.
 */
@Composable
internal fun TvSportsEventCard(
    event: GeeSportsSource.GeeEvent,
    nowMs: Long,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "sports-card-scale")
    val (home, away) = GeeTiles.teams(event)
    val hasTeams = home.isNotBlank() && away.isNotBlank()
    val live = event.live || GeeAgenda.phaseOf(event, nowMs) == GeeAgenda.Phase.Live
    val shape = CARD_SHAPE
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = shape,
                ambientColor = Color(0x667DD3FC),
                spotColor = Color(0x667DD3FC),
            )
            .then(
                if (focused) {
                    Modifier.background(Color(0x3338BDF8), shape)
                } else {
                    Modifier
                },
            ),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = Color(0xFF1E293B),
            pressedContainerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, FOCUS_CYAN), shape = shape),
        ),
        interactionSource = interaction,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A2436), VisionSurface, Color(0xFF0B101A)),
                    ),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, VisionBlack.copy(alpha = 0.54f)),
                        ),
                    ),
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        geeSportIcon(event.category),
                        contentDescription = null,
                        tint = Color(0xFF67E8F9),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    if (event.leagueLogo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(event.leagueLogo)
                                .size(32, 32)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        GeeAgenda.leagueLabel(event),
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (favorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorito",
                            tint = Color(0xFFFFC94D),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    TvSportsStateBadge(event, nowMs)
                }
                Spacer(Modifier.height(4.dp))
                if (hasTeams) {
                    TvSportsTeamLine(event.homeLogo, home, event.scoreHome, live)
                    Spacer(Modifier.height(2.dp))
                    TvSportsTeamLine(event.awayLogo, away, event.scoreAway, live)
                    Spacer(Modifier.weight(1f))
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (event.logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(event.logo)
                                    .size(56, 56)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            event.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TvSportsFooter(event, nowMs)
            }
        }
    }
}

@Composable
private fun TvSportsTeamLine(logo: String, name: String, score: Int?, live: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logo)
                        .size(64, 64)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    GeeTiles.monogram(name),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1),
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            score?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (live) Color(0xFF67E8F9) else Color.White,
        )
    }
}

@Composable
private fun TvSportsFooter(event: GeeSportsSource.GeeEvent, nowMs: Long) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val tvLine = event.broadcasts.take(2).joinToString(" · ")
        Text(
            if (tvLine.isNotBlank()) "TV: $tvLine" else GeeTiles.streams(event),
            style = MaterialTheme.typography.labelSmall,
            color = if (tvLine.isBlank() && event.alive > 0) Color(0xFF67E8F9) else VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (GeeAgenda.playable(event, nowMs) && event.alive > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = VisionLiveRed)) {
                Text(
                    "${event.alive} en vivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** EN VIVO con minuto, Final, o la hora. Misma lógica que la tarjeta móvil. */
@Composable
private fun TvSportsStateBadge(event: GeeSportsSource.GeeEvent, nowMs: Long) {
    when {
        event.live || GeeAgenda.phaseOf(event, nowMs) == GeeAgenda.Phase.Live -> {
            Surface(shape = RoundedCornerShape(50), colors = SurfaceDefaults.colors(containerColor = VisionLiveRed)) {
                Text(
                    GeeTiles.liveLabel(event),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        event.status == "post" -> Text("Final", style = MaterialTheme.typography.labelSmall, color = VisionTextSecondary)
        else -> Text(
            GeeTiles.state(event, SportsSource.todayIso()),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
        )
    }
}
