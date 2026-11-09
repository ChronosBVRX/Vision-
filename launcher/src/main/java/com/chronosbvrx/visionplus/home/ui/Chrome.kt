@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.chronosbvrx.visionplus.home.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.home.R
import com.chronosbvrx.visionplus.home.data.HomeConfig
import com.chronosbvrx.visionplus.home.data.StartTab
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Las pestañas del sistema. */
enum class HomeTab(val key: String, val label: String) {
    HOME("home", "Inicio"),
    LIVE("live", "TV en vivo"),
    INPUTS("inputs", "Entradas"),
    EVENTS("events", "Deportes"),
    MOVIES("movies", "Películas"),
    SERIES("series", "Series"),
    APPS("apps", "Apps"),
    ;

    companion object {
        fun of(key: String?): HomeTab = entries.firstOrNull { it.key == key } ?: HOME
    }
}

/**
 * La pestaña con la que abre el launcher al encender la tele. [StartTab.LAST] usa la última
 * que se miró; sin preferencia guardada, el Inicio de siempre.
 */
internal fun startTabFor(start: StartTab, lastKey: String?): HomeTab = when (start) {
    StartTab.LAST -> HomeTab.of(lastKey)
    StartTab.HOME -> HomeTab.HOME
    StartTab.LIVE -> HomeTab.LIVE
    StartTab.INPUTS -> HomeTab.INPUTS
    StartTab.APPS -> HomeTab.APPS
}

/**
 * La barra tal como la configuró la persona: **Inicio siempre primero y visible** (es la
 * puerta de vuelta), luego su orden, sin las pestañas que ocultó y sin «Entradas» cuando el
 * equipo no reporta fuentes. Las pestañas nuevas de una versión futura aparecen al final.
 */
internal fun visibleTabs(config: HomeConfig, inputsAvailable: Boolean): List<HomeTab> {
    val ordered = config.tabOrder.mapNotNull { key -> HomeTab.entries.firstOrNull { it.key == key } }
    val all = (ordered + HomeTab.entries).distinct()
    val visible = all.filter { tab ->
        tab != HomeTab.HOME &&
            tab.key !in config.hiddenTabs &&
            (tab != HomeTab.INPUTS || inputsAvailable)
    }
    return listOf(HomeTab.HOME) + visible
}

/**
 * La barra del launcher agrupa **TV en vivo** y **Deportes** en un solo destino «En vivo» con
 * menú desplegable. Si la persona ocultó una de las dos, la otra se pinta sola; nunca se
 * inventa una puerta vacía.
 */
internal sealed interface HomeTopEntry {
    data class Tab(val tab: HomeTab) : HomeTopEntry
    data object LiveHub : HomeTopEntry
}

internal fun visibleTopEntries(tabs: List<HomeTab>): List<HomeTopEntry> {
    val live = HomeTab.LIVE in tabs
    val events = HomeTab.EVENTS in tabs
    val out = ArrayList<HomeTopEntry>(tabs.size)
    var hubAdded = false
    for (tab in tabs) {
        if (tab == HomeTab.LIVE || tab == HomeTab.EVENTS) {
            if (live && events) {
                if (!hubAdded) {
                    out += HomeTopEntry.LiveHub
                    hubAdded = true
                }
            } else {
                out += HomeTopEntry.Tab(tab)
            }
        } else {
            out += HomeTopEntry.Tab(tab)
        }
    }
    return out
}

internal fun liveHubActive(tab: HomeTab): Boolean =
    tab == HomeTab.LIVE || tab == HomeTab.EVENTS

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
 * La barra superior del ESCRITORIO. No es la barra de la app: no lleva pestañas de catálogo.
 * A la izquierda vive la identidad del sistema y el saludo; a la derecha, los indicadores
 * que solo tienen sentido en un launcher (red, centro de control, ajustes y un reloj grande).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopChrome(
    tab: HomeTab,
    profile: String,
    profileAvatar: String,
    network: String,
    onTab: (HomeTab) -> Unit,
    onSearch: () -> Unit,
    onCommandCenter: () -> Unit,
    onSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    activeTabRequester: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    clock24h: Boolean = true,
    showClock: Boolean = true,
    hasNotifications: Boolean = false,
) {
    val clock = rememberClockText(clock24h)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 60.dp else 76.dp)
            .background(
                Brush.verticalGradient(
                    0f to HomeColors.Black.copy(alpha = 0.72f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = if (compact) 36.dp else 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SystemHomePill(
            tab = tab,
            onClick = { onTab(HomeTab.HOME) },
            compact = compact,
            downTarget = downTarget,
            modifier = if (activeTabRequester != null) {
                Modifier.focusRequester(activeTabRequester)
            } else {
                Modifier
            },
        )
        Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
        SystemGreeting(profile = profile, avatar = profileAvatar, tab = tab, compact = compact)
        Spacer(Modifier.weight(1f))
        ChromeIcon(Icons.Rounded.Search, "Buscar", onSearch, downTarget)
        Spacer(Modifier.width(4.dp))
        NetworkChip(network = network, compact = compact)
        Spacer(Modifier.width(4.dp))
        ChromeIcon(
            Icons.Rounded.Notifications,
            "Centro de control y notificaciones",
            onCommandCenter,
            downTarget,
            badge = hasNotifications,
        )
        Spacer(Modifier.width(4.dp))
        ChromeIcon(Icons.Rounded.Settings, "Ajustes de la tele", onSystemSettings, downTarget)
        if (showClock) {
            Spacer(Modifier.width(if (compact) 12.dp else 18.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    clock,
                    color = HomeColors.Text,
                    fontSize = if (compact) 27.sp else 32.sp,
                    lineHeight = if (compact) 28.sp else 33.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    "CDMX",
                    color = HomeColors.TextFaint,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
            }
        }
    }
}

/** El botón de Inicio del sistema. Su marca es lo único que permanece siempre a la vista. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemHomePill(
    tab: HomeTab,
    onClick: () -> Unit,
    compact: Boolean,
    downTarget: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(HomeShapes.pill)
            .background(if (focused) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = if (focused) 0.52f else 0.10f), HomeShapes.pill)
            .then(if (downTarget != null) Modifier.softFocusDown(downTarget) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La marca real de Vision+, no un texto genérico: el mismo imagotipo del APK.
        Image(
            painter = painterResource(R.drawable.ic_vision_wordmark),
            contentDescription = "Vision+",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(if (compact) 17.dp else 21.dp),
        )
    }
}

@Composable
private fun SystemGreeting(profile: String, avatar: String, tab: HomeTab, compact: Boolean) {
    val name = profile.trim().ifBlank { "Principal" }
    val nowMs = rememberNowMs(intervalMs = 60_000L)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatarBadge(
            name = name,
            avatar = avatar,
            size = if (compact) 27.dp else 31.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = if (compact) 205.dp else 280.dp)) {
            Text(
                "${greetingFor(nowMs = nowMs)}, $name",
                color = HomeColors.TextDim,
                fontSize = if (compact) 13.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (tab == HomeTab.HOME) dateLine(nowMs = nowMs) else "Sistema · ${tab.label}",
                color = HomeColors.TextFaint,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * El avatar del perfil tal como lo eligió la persona en Vision+. Si el feed trae un id que
 * no existe en este launcher (versión vieja o avatar retirado), cae a la inicial — nunca a
 * un hueco gris.
 */
@Composable
internal fun ProfileAvatarBadge(
    name: String,
    avatar: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resId = remember(avatar) { profileAvatarResourceId(context, avatar) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(HomeColors.Blue, HomeColors.Cyan)))
            .border(1.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = "Foto de perfil de $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** `profile_avatar_<id>` del propio APK del launcher; 0 si el id no es válido o no existe. */
internal fun profileAvatarResourceId(context: Context, avatar: String): Int {
    val clean = avatar.trim()
    if (!isValidAvatarId(clean)) return 0
    return context.resources.getIdentifier(
        "profile_avatar_$clean",
        "drawable",
        context.packageName,
    )
}

internal fun isValidAvatarId(value: String): Boolean =
    value.isNotBlank() && value.length <= 40 && value.all { it.isLetterOrDigit() || it == '_' }

@Composable
private fun NetworkChip(
    network: String,
    compact: Boolean,
) {
    val known = network.isNotBlank()
    val online = known && !network.equals("Sin red", ignoreCase = true)
    Row(
        modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Rounded.Wifi,
            contentDescription = "Red: ${network.ifBlank { "comprobando" }}",
            tint = when {
                !known -> HomeColors.TextFaint
                online -> HomeColors.Success
                else -> HomeColors.Live
            },
            modifier = Modifier.size(15.dp),
        )
        Text(
            network.ifBlank { "Red" },
            color = HomeColors.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Saludo del escritorio, no del catálogo: la persona y la hora del centro de México. */
internal fun greetingFor(
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getTimeZone("America/Mexico_City"),
): String {
    val hour = SimpleDateFormat("H", Locale.US)
        .apply { this.timeZone = timeZone }
        .format(Date(nowMs))
        .toIntOrNull() ?: 12
    return when (hour) {
        in 0..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

internal fun dateLine(
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getTimeZone("America/Mexico_City"),
): String {
    val raw = SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-MX"))
        .apply { this.timeZone = timeZone }
        .format(Date(nowMs))
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("es-MX")) else it.toString() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChromeIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    downTarget: FocusRequester? = null,
    badge: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, Color.White, RoundedCornerShape(50)) else Modifier)
            .then(if (downTarget != null) Modifier.softFocusDown(downTarget) else Modifier)
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
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(HomeColors.Cyan),
            )
        }
    }
}
