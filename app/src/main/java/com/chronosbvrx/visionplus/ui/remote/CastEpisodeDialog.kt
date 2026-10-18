package com.chronosbvrx.visionplus.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.player.episodeRowLabel
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * El selector de temporada y capítulo del control remoto: una serie no se puede mandar «en
 * general» a la tele (eso mandaba a reproducir sin capítulo y no abría lo que uno eligió).
 * Aquí se elige la temporada (si hay varias) y el capítulo exacto, que viaja a la tele como
 * `xtv:<playCode>` con el contexto de su serie: el reproductor de la tele abre ESE capítulo y
 * puede seguir con el siguiente sin volver al teléfono.
 *
 * Sin lista de capítulos (red o ficha rara) jamás hay callejón sin salida: se ofrece
 * «Reproducir el título» con el comportamiento de siempre.
 */
@Composable
internal fun CastEpisodeDialog(
    film: Film,
    initialSeasonIndex: Int = 0,
    onDismiss: () -> Unit,
    onSend: (CastEpisode) -> Unit,
    onSendTitle: () -> Unit,
) {
    val context = LocalContext.current
    val seasons = film.seasons
    var seasonIdx by remember(film.id) {
        mutableIntStateOf(initialSeasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0)))
    }
    val activeId = seasons.getOrNull(seasonIdx)?.titleId ?: film.id
    var episodes by remember(film.id, activeId) { mutableStateOf<List<X4KApi.EpisodeProgram>?>(null) }
    var reload by remember(film.id, activeId) { mutableIntStateOf(0) }

    LaunchedEffect(film.id, activeId, reload) {
        episodes = null
        episodes = runCatching { X4KApi.episodeProgramsFor(context, activeId) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisionSurface,
        titleContentColor = VisionTextPrimary,
        textContentColor = VisionTextSecondary,
        title = {
            Text(film.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Elige temporada y capítulo para abrirlo en la tele.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTextSecondary,
                )
                if (seasons.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seasons.forEachIndexed { index, season ->
                            CastSeasonChip(
                                label = castSeasonLabel(season.n),
                                active = index == seasonIdx,
                                onClick = { if (index != seasonIdx) seasonIdx = index },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val list = episodes
                when {
                    list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = VisionBlue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Buscando capítulos…", style = MaterialTheme.typography.bodySmall, color = VisionTextSecondary)
                    }

                    list.isEmpty() -> Column {
                        Text(
                            "No se pudieron cargar los capítulos de esta serie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { reload++ }) { Text("Reintentar", color = VisionBlueBright) }
                            TextButton(onClick = onSendTitle) { Text("Reproducir el título", color = VisionBlueBright) }
                        }
                    }

                    else -> LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(list, key = { _, ep -> ep.contentId }) { _, ep ->
                            CastEpisodeRow(
                                number = ep.number,
                                label = episodeRowLabel(ep.name, ep.number, film.title),
                                onClick = {
                                    onSend(castEpisodeFor(film, seasonIdx, ep.contentId, ep.number))
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = VisionTextSecondary) }
        },
    )
}

@Composable
private fun CastSeasonChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Color.White else VisionTextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) VisionBlue else VisionSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun CastEpisodeRow(number: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VisionSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "C${number.coerceAtLeast(1)}",
            style = MaterialTheme.typography.labelLarge,
            color = VisionBlueBright,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
