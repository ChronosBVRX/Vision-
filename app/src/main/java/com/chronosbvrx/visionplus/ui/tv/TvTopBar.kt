package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.VisionClock
import com.chronosbvrx.visionplus.ui.profile.ProfileAvatar
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay

/** Las pestañas del shell de la tele (el mismo lenguaje que Vision+ Home, con las de la app). */
internal enum class TvTopTab(val label: String, val description: String, val target: Route) {
    HOME("Inicio", "Inicio", Route.Home),
    LIVE("TV en vivo", "En vivo", Route.Live),
    SPORTS("Deportes", "Deportes en vivo", Route.Sports),
    MOVIES("Películas", "Películas", Route.Section("movies", "Películas")),
    SERIES("Series", "Series", Route.Section("series", "Series")),
    LIBRARY("Mi lista", "Mi lista", Route.Library),
}

/** La barra superior se pinta en estas rutas (las del launcher) y no en fichas/reproductores. */
internal fun tvTopBarVisible(route: Route): Boolean =
    route == Route.Home || route == Route.Live || route == Route.Sports ||
        route is Route.Section || route == Route.Library ||
        route == Route.Categories || route is Route.Category ||
        // Ajustes y Buscar son parte del mismo shell: si no, parecen otra app.
        route == Route.Settings || route == Route.Search

/** Qué pestaña se pinta activa para la ruta actual (null = ninguna). */
internal fun tvTopTabFor(route: Route): TvTopTab? = when (route) {
    Route.Home -> TvTopTab.HOME
    Route.Live -> TvTopTab.LIVE
    Route.Sports -> TvTopTab.SPORTS
    Route.Library -> TvTopTab.LIBRARY
    is Route.Section -> when (route.name) {
        "movies" -> TvTopTab.MOVIES
        "series" -> TvTopTab.SERIES
        else -> null
    }
    else -> null
}

/**
 * La barra superior de la tele: marca, pestañas, hora de CDMX, perfil, buscador y ajustes.
 * Es la misma idea que la de Vision+ Home, con las secciones de la app (sin Apps ni sistema).
 */
@Composable
internal fun TvTopBar(
    active: TvTopTab?,
    onTab: (TvTopTab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profile = remember(context) {
        runCatching { ProfileStore.active(context).name }.getOrDefault("Principal")
    }
    val clock = rememberTopClock()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Marca
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Vision",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp,
            )
            Text("+", color = VisionBlueBright, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                "TV",
                color = VisionTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
            )
        }
        Spacer(Modifier.width(20.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(TvTopTab.entries.toList()) { tab ->
                TopTabPill(
                    label = tab.label,
                    description = tab.description,
                    selected = tab == active,
                    onClick = { onTab(tab) },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            clock,
            color = VisionTextSecondary,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.width(12.dp))
        ProfileChip(profile, onProfile)
        Spacer(Modifier.width(6.dp))
        TvSearchPill(onSearch)
        Spacer(Modifier.width(4.dp))
        TopBarIcon(Icons.Default.Settings, "Ajustes", onSettings)
    }
}

@Composable
private fun TopTabPill(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused && selected -> VisionBlue.copy(alpha = 0.32f)
        focused -> Color.White.copy(alpha = 0.14f)
        selected -> VisionBlue.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(
                if (focused) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected || focused) Color.White else VisionTextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileChip(name: String, onClick: () -> Unit) {
    val label = name.trim().ifBlank { "Principal" }
    // El chip abre «Perfiles» (como en Netflix): elegir quién eres desde cualquier pantalla.
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = VisionTextSecondary,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ProfileAvatar(name = label, size = 22.dp)
            Text(
                label,
                color = Color.Unspecified,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvSearchPill(onSearch: () -> Unit) {
    // Píldora «Buscar» en vez de una lupa pelada: es la acción principal de la barra.
    Surface(
        onClick = onSearch,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Text("Buscar", color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, Color.White, RoundedCornerShape(50)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused) Color.White else VisionTextSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** La hora del centro de México, despertando justo al cambiar el minuto. */
@Composable
private fun rememberTopClock(): String {
    var text by remember { mutableStateOf(VisionClock.timeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(VisionClock.msToNextMinute() + 50L)
            text = VisionClock.timeText()
        }
    }
    return text
}
