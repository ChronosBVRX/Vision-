package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionLiveRed
import com.chronosbvrx.visionplus.ui.live.ChannelLogo
import com.chronosbvrx.visionplus.ui.live.LOGO_PREVIEW_UPSCALE
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

// The TV En vivo screen's pieces: playlist tabs, rail rows, compact channel rows and the preview
// panel. Focus is shown one way on this screen -- a lifted fill and a white ring.

private val ROW_SHAPE = RoundedCornerShape(12.dp)

/** Hora local del EPG («07:00»), o vacío si el dato no es válido. */
private fun timeLabel(ms: Long): String =
    if (ms <= 0L) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** El EPG a veces manda «Título\nEn Vivo»; para la ficha queremos solo el título. */
private fun epgTitle(raw: String?): String =
    raw.orEmpty().lineSequence().firstOrNull()?.trim().orEmpty()

/** Avance del programa actual, o null si el EPG no trae una ventana válida. */
private fun epgFraction(epg: X4KApi.EpgNow?, nowMs: Long): Float? {
    if (epg == null || epg.startMs <= 0L || epg.endMs <= epg.startMs) return null
    return ((nowMs - epg.startMs).toFloat() / (epg.endMs - epg.startMs)).coerceIn(0f, 1f)
}

private fun remainingMinutes(endMs: Long, nowMs: Long): Int =
    ((endMs - nowMs) / 60_000L).coerceAtLeast(0L).toInt()

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
internal fun TvRoundIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        label = "round-icon-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = CircleShape,
                ambientColor = Color(0x44000000),
                spotColor = Color(0x44000000),
            ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            focusedContainerColor = Color.White,
            contentColor = Color(0xFFCBD5E1),
            focusedContentColor = Color(0xFF090D16),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
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
        // TamaÃ±o nativo del logo (mÃ¡x. 2Ã—): los del proveedor son de 90Ã—60 px.
        ChannelLogo(
            url = channel.logo,
            name = channel.name,
            modifier = Modifier.fillMaxSize().padding(3.dp),
        )
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
    number: Int? = null,
    epg: X4KApi.EpgNow? = null,
    nowMs: Long = System.currentTimeMillis(),
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(72.dp),
        shape = ClickableSurfaceDefaults.shape(ROW_SHAPE),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = VisionSurfaceHigh,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = liveBorder(),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (number != null) {
                Text(
                    number.toString().padStart(3, ' '),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = VisionTextSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.width(30.dp),
                )
            }
            TvChannelTile(channel, Modifier.size(42.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    epg?.now?.takeIf { it.isNotBlank() } ?: channel.effectiveGroup(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (epg?.now?.isNotBlank() == true) Color.White.copy(alpha = 0.78f) else VisionTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (epg != null && epg.startMs > 0 && epg.endMs > epg.startMs) {
                    val fraction = ((nowMs - epg.startMs).toFloat() / (epg.endMs - epg.startMs)).coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                                .background(VisionBlue, RoundedCornerShape(50)),
                        )
                    }
                }
            }
            if (favorite) Icon(Icons.Default.Star, contentDescription = "Favorito", tint = Color(0xFFFFC94D), modifier = Modifier.size(18.dp))
        }
    }
}

/** The preview panel: the channel's logo over a 16:9 tile with "â— EN VIVO", its name and group. */
@Composable
internal fun TvLivePreview(
    channel: LiveChannel?,
    modifier: Modifier = Modifier,
    epg: X4KApi.EpgNow? = null,
    nowMs: Long = System.currentTimeMillis(),
) {
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
                // El preview es grande: se permite 3Ã— para que el logo tenga presencia sin
                // inventar pÃ­xeles (los del proveedor son de 90Ã—60).
                ChannelLogo(
                    url = channel.logo,
                    name = channel.name,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    maxUpscale = LOGO_PREVIEW_UPSCALE,
                    initialsStyle = MaterialTheme.typography.headlineLarge,
                )
            }
        }
        if (channel == null) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(VisionLiveRed))
            Text("EN VIVO", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(channel.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            epg?.now?.takeIf { it.isNotBlank() } ?: channel.effectiveGroup(),
            style = MaterialTheme.typography.bodySmall,
            color = if (epg?.now?.isNotBlank() == true) Color.White.copy(alpha = 0.82f) else VisionTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (epg != null && epg.startMs > 0 && epg.endMs > epg.startMs) {
            val fraction = ((nowMs - epg.startMs).toFloat() / (epg.endMs - epg.startMs)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.001f, 1f)).background(VisionBlue, RoundedCornerShape(50)))
            }
        }
        if (epg?.next?.isNotBlank() == true) {
            Text("Después: ${epg.next}", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Text(com.chronosbvrx.visionplus.ui.live.LiveCopy.watchRow(channel.name), style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary, maxLines = 2)
        }
    }
}

/** Hero de la TV en vivo: arte del canal a la derecha y su información a la izquierda. */
@Composable
internal fun TvLiveHero(
    channel: LiveChannel?,
    epg: X4KApi.EpgNow?,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(VisionSurface),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(
                    Brush.linearGradient(
                        listOf(VisionSurfaceHigh, VisionSurface, VisionBlack),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(240.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0x5522D3EE), Color(0x221E293B), Color.Transparent),
                                ),
                                CircleShape,
                            ),
                    )
                    ChannelLogo(
                        url = channel.logo,
                        name = channel.name,
                        modifier = Modifier.fillMaxWidth(0.55f).fillMaxHeight(0.55f),
                        maxUpscale = 2.5f,
                        initialsStyle = MaterialTheme.typography.displayMedium,
                    )
                }
            }
        }
        // Vignette: izquierda sólida, derecha transparente; abajo se funde con el fondo.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to VisionBlack,
                    0.32f to VisionBlack.copy(alpha = 0.96f),
                    0.55f to VisionBlack.copy(alpha = 0.70f),
                    0.78f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to VisionBlack.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp, end = 16.dp)
                .fillMaxWidth(0.48f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (channel == null) {
                Text("Elige un canal", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                return@Column
            }
            Surface(
                shape = RoundedCornerShape(50),
                colors = SurfaceDefaults.colors(containerColor = Color(0x26EF4444)),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Text(
                        "EN VIVO",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
            Text(
                channel.name,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val group = channel.effectiveGroup().ifBlank { "TV en vivo" }
            val guide = epg
            val currentProgram = guide?.now?.takeIf { it.isNotBlank() }
            if (guide != null && currentProgram != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        currentProgram,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val schedule = if (guide.startMs > 0 && guide.endMs > 0) {
                        "${timeLabel(guide.startMs)} - ${timeLabel(guide.endMs)}"
                    } else {
                        ""
                    }
                    if (schedule.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            colors = SurfaceDefaults.colors(containerColor = Color(0x14FFFFFF)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    schedule,
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    "• $group",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                val fraction = epgFraction(guide, nowMs)
                if (fraction != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .width(260.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1E293B)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                                    .background(Color(0xFF38BDF8), RoundedCornerShape(50)),
                            )
                        }
                        Text(
                            "Quedan ${remainingMinutes(guide.endMs, nowMs)} min",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
                if (guide.next.isNotBlank()) {
                    val nextTime = timeLabel(guide.endMs)
                    Text(
                        "Sigue: ${guide.next}" + if (nextTime.isNotBlank()) " ($nextTime)" else "",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    group,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "OK para ver a pantalla completa · Mantén OK para agregar a favoritos",
                color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

/** Píldora de filtro (país, lista) con icono y flecha, estilo glass sin bordes. */
@Composable
internal fun TvLiveFilterPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        label = "filter-pill-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 10.dp else 0.dp,
                shape = RoundedCornerShape(50),
                ambientColor = Color(0x44000000),
                spotColor = Color(0x44000000),
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            focusedContainerColor = Color.White,
            contentColor = Color(0xFFCBD5E1),
            focusedContentColor = Color(0xFF090D16),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(17.dp))
        }
    }
}

/** Píldora de categoría: selección sutil en sky, foco blanco tipo Apple TV. */
@Composable
internal fun TvLiveCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        label = "category-chip-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
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
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** Tarjeta 16:10 del carrusel: logo arriba, EPG abajo y foco limpio con glow. */
@Composable
internal fun TvLiveChannelCard(
    channel: LiveChannel,
    number: Int,
    epg: X4KApi.EpgNow?,
    nowMs: Long,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        label = "live-card-scale",
    )
    val fraction = epgFraction(epg, nowMs)
    val hasProgram = epgTitle(epg?.now).isNotBlank()
    val program = epgTitle(epg?.now).takeIf { it.isNotBlank() }
        ?: channel.effectiveGroup().ifBlank { "TV en vivo" }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(184.dp)
            .height(104.dp)
            .then(
                if (focused) {
                    Modifier.border(2.dp, Color(0xFF7DD3FC), RoundedCornerShape(18.dp))
                } else {
                    Modifier
                },
            )
            .shadow(
                elevation = if (focused) 16.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0x667DD3FC),
                spotColor = Color(0x667DD3FC),
            )
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionSurface,
            focusedContainerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(VisionSurfaceHigh, VisionSurface, VisionBlack)),
                ),
            )
            ChannelLogo(
                url = channel.logo,
                name = channel.name,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .height(42.dp)
                    .fillMaxWidth(0.60f),
                maxUpscale = 2.0f,
                initialsStyle = MaterialTheme.typography.headlineSmall,
            )
            // La mitad inferior queda limpia para el texto, aunque el logo sea muy alto.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to VisionBlack.copy(alpha = 0.82f),
                            1f to VisionBlack,
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        number.toString().padStart(2, '0') + " |",
                        color = Color(0xFF67E8F9),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (favorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorito",
                            tint = Color(0xFFFFC94D),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        program,
                        color = if (hasProgram) Color.White.copy(alpha = 0.76f) else VisionTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                }
            }
            if (fraction != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF1E293B)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                            .background(Color(0xFF38BDF8)),
                    )
                }
            }
        }
    }
}
