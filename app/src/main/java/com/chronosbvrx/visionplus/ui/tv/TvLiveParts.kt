package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.ui.live.channelInitials
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

// The TV En vivo screen's pieces: playlist tabs, rail rows, compact channel rows and the preview
// panel. Focus is shown one way on this screen -- a lifted fill and a white ring.

private val ROW_SHAPE = RoundedCornerShape(12.dp)

@Composable
private fun liveSurfaceColors() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = VisionSurfaceHigh,
    pressedContainerColor = VisionSurfaceHigh,
    contentColor = Color.White,
    focusedContentColor = Color.White,
    pressedContentColor = Color.White,
)

@Composable
private fun liveBorder(shape: androidx.compose.ui.graphics.Shape = ROW_SHAPE) =
    ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape))

/** A playlist tab: its name and a 3 dp blue underline while [selected]. */
@Composable
internal fun TvPlaylistTab(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(ROW_SHAPE),
        colors = liveSurfaceColors(),
        border = liveBorder(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Color.White else VisionTextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) VisionBlue else Color.Transparent))
        }
    }
}

/** A round icon button of the tabs row (Buscar, Recargar, Agregar). */
@Composable
internal fun TvRoundIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = VisionSurfaceHigh,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = liveBorder(CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
    }
}

/** A plain header of the rail (a group of categories): never focusable. */
@Composable
internal fun TvRailHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = VisionTextSecondary,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** A row of the rail: an optional icon, the label and, right-aligned, its [count]. */
@Composable
internal fun TvRailRow(label: String, icon: ImageVector?, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = ClickableSurfaceDefaults.shape(ROW_SHAPE),
        colors = liveSurfaceColors(),
        border = liveBorder(),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).fillMaxHeight(0.6f).background(if (selected) VisionBlue else Color.Transparent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = if (icon == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) Text("$count", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

/** A channel's logo slot: its logo over a blue-tinted tile, or the initials of its name. */
@Composable
internal fun TvChannelTile(channel: LiveChannel, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(VisionBlue.copy(alpha = 0.35f), Color(0xFF10161F)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(channelInitials(channel.name), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
        val logo = channel.logo
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

/**
 * One compact channel row: tile, name, favourite star and the channel's group. OK plays it; a long
 * OK adds or removes the favourite.
 */
@Composable
internal fun TvChannelRow(
    channel: LiveChannel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = ClickableSurfaceDefaults.shape(ROW_SHAPE),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = VisionSurfaceHigh,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = liveBorder(),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvChannelTile(channel, Modifier.size(40.dp))
            Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (favorite) Icon(Icons.Default.Star, contentDescription = "Favorito", tint = Color(0xFFFFC94D), modifier = Modifier.size(18.dp))
            Text(channel.effectiveGroup(), style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The preview panel: the channel's logo over a 16:9 tile with "● EN VIVO", its name and group. */
@Composable
internal fun TvLivePreview(channel: LiveChannel?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(VisionSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (channel == null) {
                Text("Elige un canal de la lista", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
            } else {
                Text(channelInitials(channel.name), style = MaterialTheme.typography.headlineLarge, color = Color.White.copy(alpha = 0.4f))
                val logo = channel.logo
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                    )
                }
            }
        }
        if (channel == null) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(VisionBlue))
            Text("EN VIVO", style = MaterialTheme.typography.labelSmall, color = VisionBlue, fontWeight = FontWeight.Bold)
        }
        Text(channel.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(channel.effectiveGroup(), style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(com.chronosbvrx.visionplus.ui.live.LiveCopy.watchRow(channel.name), style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, maxLines = 2)
    }
}
