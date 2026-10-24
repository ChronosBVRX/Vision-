package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** The height of a tab, and of the compact buttons that sit in a row with tabs. */
internal val TAB_HEIGHT = 52.dp

/**
 * A tab in a TV row of big sections. Selected and focused are told apart: the selected tab is red,
 * the focused one carries a white border.
 */
@Composable
internal fun TvTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(TAB_HEIGHT),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) VisionBlue else VisionSurface,
            focusedContainerColor = if (selected) VisionBlue else VisionSurface,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(24.dp)),
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/**
 * A settings row button: dark surface and a **white focus ring** (the same language as the new
 * home cards), full width of its column. Antes era 60% del ancho y azul al enfocar.
 */
@Composable
internal fun TvActionOption(
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurfaceHigh,
            focusedContainerColor = VisionSurfaceHigh,
            pressedContainerColor = VisionBlue,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp)),
            pressedBorder = Border.None,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Text(label, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) { trailing() }
            }
        }
    }
}

/** An action as wide as its label, [TAB_HEIGHT] tall, for rows of tabs and dialogs. */
@Composable
internal fun TvCompactAction(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** El botón principal del hero es BLANCO (como en Vision+ Home). */
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = if (primary) Color(0xFF070B13) else Color.White
    Surface(
        onClick = onClick,
        modifier = modifier.height(TAB_HEIGHT),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else VisionSurfaceHigh,
            focusedContainerColor = if (primary) Color.White else VisionBlue,
            pressedContainerColor = if (primary) Color.White else VisionBlue,
            contentColor = ink,
            focusedContentColor = ink,
            pressedContentColor = ink,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (primary) Border(BorderStroke(2.dp, Color.White)) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, if (primary) VisionBlue else Color.White)),
            pressedBorder = Border.None,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                Text(label, color = ink.copy(alpha = if (focused) 1f else 0.95f), maxLines = 1)
            }
        }
    }
}

/**
 * A chip to pick one option of several (categories): blue tint when selected, white border on focus.
 * Con [compact] (pantallas con muchos filtros apilados, p. ej. Deportes en vivo) el chip baja su
 * relleno para ocupar menos alto: el contenido de la pantalla gana el espacio.
 */
@Composable
internal fun TvChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) VisionBlue.copy(alpha = 0.25f) else VisionSurfaceHigh,
            focusedContainerColor = if (selected) VisionBlue.copy(alpha = 0.25f) else VisionSurfaceHigh,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp)),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 5.dp else 10.dp,
            ),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
        }
    }
}

/** A full-width row helper for settings sections. */
@Composable
internal fun TvSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.fillMaxWidth())
}
