package com.chronosbvrx.visionplus.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Las pestañas del sistema. */
enum class HomeTab(val key: String, val label: String) {
    HOME("home", "Inicio"),
    LIVE("live", "TV en vivo"),
    EVENTS("events", "Deportes"),
    MOVIES("movies", "Películas"),
    SERIES("series", "Series"),
    APPS("apps", "Apps"),
    ;

    companion object {
        fun of(key: String?): HomeTab = entries.firstOrNull { it.key == key } ?: HOME
    }
}

/** La hora de la casa: centro de México, aunque la tele esté en otra zona. */
@Composable
fun rememberClockText(clock24h: Boolean = true): String {
    var text by remember(clock24h) { mutableStateOf(clockText(clock24h = clock24h)) }
    LaunchedEffect(clock24h) {
        while (true) {
            kotlinx.coroutines.delay(msToNextMinute() + 40L)
            text = clockText(clock24h = clock24h)
        }
    }
    return text
}

internal fun clockText(nowMs: Long = System.currentTimeMillis(), clock24h: Boolean = true): String =
    SimpleDateFormat(if (clock24h) "HH:mm" else "h:mm a", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("America/Mexico_City") }
        .format(Date(nowMs))

internal fun msToNextMinute(nowMs: Long = System.currentTimeMillis()): Long {
    val remainder = nowMs % 60_000L
    return (60_000L - remainder).coerceIn(250L, 60_000L)
}

/**
 * La barra del sistema. Es lo primero que se ve al encender la tele: marca a la izquierda,
 * pestañas al centro-izquierda, hora/perfil/acciones a la derecha. Nunca se desplaza.
 */
@Composable
fun TopChrome(
    tab: HomeTab,
    profile: String,
    onTab: (HomeTab) -> Unit,
    onSearch: () -> Unit,
    onCommandCenter: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    activeTabRequester: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    clock24h: Boolean = true,
    showClock: Boolean = true,
) {
    val clock = rememberClockText(clock24h)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 62.dp)
            .padding(horizontal = if (compact) 36.dp else 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Marca
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Vision",
                color = HomeColors.Text,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp,
            )
            Text(
                "+",
                color = HomeColors.BlueBright,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(15.dp).background(Color.White.copy(alpha = 0.16f)))
            Spacer(Modifier.width(10.dp))
            Text(
                "HOME",
                color = HomeColors.TextFaint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp,
            )
        }
        Spacer(Modifier.width(if (compact) 18.dp else 30.dp))
        LazyRow(
            modifier = Modifier.weight(1f).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(HomeTab.entries.toList()) { item ->
                TabPill(
                    label = item.label,
                    selected = item == tab,
                    onClick = { onTab(item) },
                    compact = compact,
                    downTarget = downTarget,
                    modifier = if (item == tab && activeTabRequester != null) {
                        Modifier.focusRequester(activeTabRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        // Hora
        if (showClock) {
            Text(
                clock,
                color = HomeColors.TextDim,
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.width(if (compact) 14.dp else 20.dp))
        }
        // Perfil
        ProfileChip(name = profile, compact = compact)
        Spacer(Modifier.width(8.dp))
        ChromeIcon(Icons.Rounded.Search, "Buscar", onSearch)
        Spacer(Modifier.width(4.dp))
        ChromeIcon(Icons.Rounded.Settings, "Ajustes", onCommandCenter)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    downTarget: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused && selected -> HomeColors.Blue.copy(alpha = 0.30f)
        focused -> Color.White.copy(alpha = 0.14f)
        selected -> HomeColors.Blue.copy(alpha = 0.20f)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .clip(HomeShapes.pill)
            .background(background)
            .then(if (focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.7f), HomeShapes.pill) else Modifier)
            .then(
                if (downTarget != null) {
                    Modifier.softFocusDown(downTarget)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected || focused) HomeColors.Text else HomeColors.TextDim,
            style = MaterialTheme.typography.titleMedium,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileChip(name: String, compact: Boolean) {
    val label = name.trim().ifBlank { "Principal" }
    Row(
        modifier = Modifier
            .clip(HomeShapes.pill)
            .background(Color.White.copy(alpha = 0.07f))
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 22.dp else 26.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(HomeColors.Blue, Color(0xFF6E4BF6)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1).uppercase(),
                color = Color.White,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            label,
            color = HomeColors.TextDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChromeIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, Color.White, RoundedCornerShape(50)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused) HomeColors.Text else HomeColors.TextDim,
            modifier = Modifier.size(19.dp),
        )
    }
}
