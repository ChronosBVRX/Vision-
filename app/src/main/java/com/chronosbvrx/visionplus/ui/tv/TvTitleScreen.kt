package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.scoreLabel
import com.chronosbvrx.visionplus.ui.EpisodePlay
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import coil.compose.AsyncImage
import org.json.JSONObject
import androidx.compose.runtime.collectAsState
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.data.offline.TvStorageRequiredException

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh

/**
 * A film's information page on the TV: full-bleed backdrop (the sharp stage shot when the portal
 * has one), the professional card — score, genres, cast, director, languages, running time —
 * and "Reproducir", all reachable with the remote.
 */
@Composable
fun TvTitleScreen(film: Film, onPlay: (EpisodePlay?) -> Unit) {
    val landing = rememberLandingFocus()
    val context = LocalContext.current
    val isXtv = film.videoUrl.startsWith("xtv:")
    val meta = remember(film.id) { if (isXtv) X4KApi.metaFor(context, film.id) else null }

    var backdrop by remember(film.id) { mutableStateOf(film.posterUrl) }
    var languages by remember(film.id) { mutableStateOf<List<String>>(emptyList()) }
    var qualities by remember(film.id) { mutableStateOf<List<String>>(emptyList()) }
    var itemLine by remember(film.id) { mutableStateOf("") }
    var durationLabel by remember(film.id) { mutableStateOf("") }
    var selectedSeason by remember(film.id) { mutableStateOf(film.seasons.firstOrNull()) }
    val activeId = selectedSeason?.titleId ?: film.id
    val seasonIdx = film.seasons.indexOfFirst { it.titleId == activeId }.coerceAtLeast(0)
    val seasonIdList = remember(film.id) { film.seasons.map { it.titleId } }
    val seasonNameList = remember(film.id) { film.seasons.map { if (it.n <= 0) "Completa" else "T${it.n}" } }
    var itemDurationMs by remember(film.id, activeId) {
        mutableStateOf(if (film.durationMin > 0) film.durationMin * 60_000L else 0L)
    }
    var description by remember(film.id) { mutableStateOf(film.synopsis) }
    var episodePrograms by remember(film.id) { mutableStateOf<List<X4KApi.EpisodeProgram>>(emptyList()) }
    var watchedTick by remember(film.id) { mutableStateOf(0) }
    val episodes = remember(episodePrograms) { episodePrograms.map { it.contentId } }
    val episodeNames = remember(episodePrograms) { episodePrograms.map { it.name } }

    if (isXtv) {
        LaunchedEffect(film.id, activeId) {
            // Los capítulos se cargan y ordenan primero para estar listos al instante.
            val programs = X4KApi.episodeProgramsFor(context, activeId)
            if (programs.isNotEmpty()) {
                episodePrograms = programs
            }
            val detail = X4KApi.detailFor(context, activeId)
            if (programs.isEmpty()) {
                val fallbackEps = X4KApi.parsePlayEpisodePrograms(detail?.optJSONObject("play"))
                if (fallbackEps.isNotEmpty()) {
                    episodePrograms = fallbackEps
                }
            }
            if (detail == null) return@LaunchedEffect
            val parsed = parseDetail(detail.optJSONObject("play"))
            languages = parsed.first
            qualities = parsed.second
            val item = detail.optJSONObject("item") ?: return@LaunchedEffect
            item.optJSONArray("posterList")?.let { posters ->
                for (i in 0 until posters.length()) {
                    val p = posters.optJSONObject(i) ?: continue
                    if (p.optString("fileType") == "stage" && p.optString("fileUrl").isNotBlank()) {
                        backdrop = p.optString("fileUrl")
                        break
                    }
                }
            }
            val durMs = X4KApi.extractDurationMs(item).takeIf { it > 0L } ?: X4KApi.extractDurationMs(detail)
            if (durMs > 0L) itemDurationMs = durMs
            val duration = prettyDuration(item.optString("duration").ifBlank { if (durMs > 0) "${durMs / 60_000} min" else "" })
            durationLabel = duration
            val year = item.optString("releaseTime").take(4).takeIf { it.length == 4 } ?: ""
            val subs = item.optString("subsInfo").split(',').map { it.trim() }.filter { it.isNotBlank() }
            itemLine = listOfNotNull(
                year.ifBlank { null },
                duration.ifBlank { null },
                subs.takeIf { it.isNotEmpty() }
                    ?.let { list -> "Subtítulos: " + list.joinToString(" · ") { audioLabel(it) } },
            ).joinToString("   ·   ")
            item.optString("description").takeIf { it.isNotBlank() }?.let { description = it }
        }
    }

    val watchedCount = remember(watchedTick, activeId, episodes) {
        episodes.indices.count { UserLibrary.isEpisodeWatched(context, activeId, it + 1) }
    }
    val nextEpisodeIndex = remember(watchedTick, activeId, episodes) {
        if (episodes.isEmpty()) 0
        else episodes.indices.firstOrNull { !UserLibrary.isEpisodeWatched(context, activeId, it + 1) } ?: 0
    }
    val activeSeasonLabel = seasonNameList.getOrElse(seasonIdx) { "T${seasonIdx + 1}" }
    val seasonTitlePrefix = if (film.seasons.size > 1) " · $activeSeasonLabel" else ""
    // Solo es serie con 2+ capítulos o 2+ temporadas: 1 capítulo suelto del portal es película.
    val isSeriesTitle = episodes.size > 1 || film.seasons.size > 1
    val playButtonLabel = when {
        isSeriesTitle && watchedCount > 0 ->
            "▶  Continuar$seasonTitlePrefix · C${nextEpisodeIndex + 1}"
        isSeriesTitle ->
            "▶  Reproducir$seasonTitlePrefix · C1"
        else -> "▶  Reproducir"
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        AsyncImage(
            model = backdrop,
            contentDescription = film.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(VisionBlack, VisionBlack, VisionBlack.copy(alpha = 0.15f), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, VisionBlack.copy(alpha = 0.4f), VisionBlack)),
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            val score = scoreLabel(meta?.optString("sc"), film.score)
            val genres = genreLabels(meta?.optString("gen").orEmpty())
            val director = meta?.optString("dir").orEmpty()
            val cast = meta?.optString("act").orEmpty()
                .split(',').map { it.trim() }.filter { it.isNotBlank() }

            if (isXtv) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (score.isNotBlank()) {
                        Box(
                            Modifier
                                .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("★ $score", color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (qualities.isNotEmpty()) {
                        Text(
                            qualities.joinToString(" · ") { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    if (durationLabel.isNotBlank()) {
                        Text(
                            durationLabel.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    Text("Incluido con tu cuenta", style = MaterialTheme.typography.labelLarge, color = Color(0xFF7FD1AE))
                }
            } else {
                Text("Película · Internet Archive", style = MaterialTheme.typography.labelLarge, color = VisionTextSecondary)
            }
            Text(
                film.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (isXtv) {
                if (itemLine.isNotBlank()) {
                    Text(itemLine, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
                }
                if (genres.isNotBlank()) {
                    Text(genres, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 3.dp))
                }
                if (languages.isNotEmpty()) {
                    Text(
                        "Idiomas: " + languages.joinToString(" · ") { audioLabel(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (director.isNotBlank()) {
                    Text("Dirigida por $director", style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 3.dp))
                }
                if (cast.isNotEmpty()) {
                    Text(
                        "Con: " + cast.take(5).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            } else {
                val metaLine = film.metaLine()
                if (metaLine.isNotBlank()) {
                    Text(metaLine, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (episodes.isNotEmpty() || film.seasons.size > 1) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 760.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                Button(
                    onClick = {
                        val targetIdx = nextEpisodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
                        val targetEp = episodes.getOrNull(targetIdx)
                        when {
                            isSeriesTitle && targetEp != null -> {
                                UserLibrary.markEpisodeWatched(context, activeId, targetIdx + 1)
                                watchedTick++
                                onPlay(
                                    EpisodePlay(
                                        code = targetEp,
                                        titleSuffix = "$seasonTitlePrefix · C${targetIdx + 1}",
                                        seriesId = activeId,
                                        episodeIds = episodes,
                                        episodeNames = episodeNames,
                                        episodeIndex = targetIdx,
                                        seasonIds = seasonIdList,
                                        seasonNames = seasonNameList,
                                        seasonIndex = seasonIdx,
                                        durationMs = itemDurationMs,
                                    ),
                                )
                            }
                            film.seasons.size > 1 && isSeriesTitle -> onPlay(
                                EpisodePlay(
                                    code = activeId,
                                    titleSuffix = "$seasonTitlePrefix · C1",
                                    seriesId = activeId,
                                    seasonIds = seasonIdList,
                                    seasonNames = seasonNameList,
                                    seasonIndex = seasonIdx,
                                    durationMs = itemDurationMs,
                                ),
                            )
                            else -> onPlay(
                                EpisodePlay(
                                    code = film.videoUrl.substringAfter(':'),
                                    durationMs = if (itemDurationMs > 0L) itemDurationMs else if (film.durationMin > 0) film.durationMin * 60_000L else 0L,
                                )
                            )
                        }
                    },
                    colors = visionTvButtonColors(),
                    border = visionTvButtonBorder(),
                    modifier = Modifier.landingFocus(landing),
                ) {
                    Text(playButtonLabel, fontWeight = FontWeight.Bold)
                }
                val inLater = remember(film.id) { mutableStateOf(UserLibrary.isWatchLater(context, film.id)) }
                val inFavorites = remember(film.id) { mutableStateOf(UserLibrary.isFavorite(context, film.id)) }
                val downloads by OfflineRegistry.downloadsFlow.collectAsState()
                val tvDownload = remember(film.id, downloads) {
                    OfflineRegistry.findDownloadFor(film.id, context = context)
                }
                var showNoUsbDialog by remember { mutableStateOf(false) }

                if (showNoUsbDialog) {
                    TvNoUsbDialog(onDismiss = { showNoUsbDialog = false })
                }

                TvCompactAction(label = if (inLater.value) "✓ Ver más tarde" else "Ver más tarde") {
                    UserLibrary.toggleWatchLater(context, film)
                    inLater.value = !inLater.value
                }
                TvCompactAction(label = if (inFavorites.value) "★ Favorito" else "☆ Favorito") {
                    UserLibrary.toggleFavorite(context, film)
                    inFavorites.value = !inFavorites.value
                }

                val downloadLabel = when (tvDownload?.state) {
                    DownloadState.COMPLETED -> "✓ En USB (${tvDownload.formattedSize})"
                    DownloadState.DOWNLOADING -> "Descargando ${tvDownload.progressPercent}%"
                    DownloadState.QUEUED -> "En cola USB…"
                    else -> "💾 Descargar en USB"
                }

                TvCompactAction(label = downloadLabel) {
                    if (tvDownload?.state == DownloadState.COMPLETED) {
                        onPlay(EpisodePlay(code = tvDownload.playUri))
                    } else {
                        try {
                            // Las series descargan el capítulo objetivo (mismo que Reproducir).
                            val targetIdx = nextEpisodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
                            val targetEp = episodes.getOrNull(targetIdx)
                            val ep = when {
                                isSeriesTitle && targetEp != null -> EpisodePlay(
                                    code = targetEp,
                                    titleSuffix = "$seasonTitlePrefix · C${targetIdx + 1}",
                                    seriesId = activeId,
                                    episodeIds = episodes,
                                    episodeNames = episodeNames,
                                    episodeIndex = targetIdx,
                                    seasonIds = seasonIdList,
                                    seasonNames = seasonNameList,
                                    seasonIndex = seasonIdx,
                                    durationMs = itemDurationMs,
                                )
                                film.seasons.size > 1 && isSeriesTitle -> EpisodePlay(
                                    code = activeId,
                                    titleSuffix = "$seasonTitlePrefix · C1",
                                    seriesId = activeId,
                                    seasonIds = seasonIdList,
                                    seasonNames = seasonNameList,
                                    seasonIndex = seasonIdx,
                                    durationMs = itemDurationMs,
                                )
                                else -> null
                            }
                            OfflineDownloadManager.enqueueDownload(context, film, ep)
                        } catch (_: TvStorageRequiredException) {
                            showNoUsbDialog = true
                        }
                    }
                }
            }

            if (episodes.size > 1 || film.seasons.size > 1) {
                TvSeriesSelectorPanel(
                    seasons = film.seasons,
                    activeId = activeId,
                    onSelectSeason = { selectedSeason = it },
                    episodePrograms = episodePrograms,
                    watchedCount = watchedCount,
                    nextEpisodeIndex = nextEpisodeIndex,
                    watchedTick = watchedTick,
                    onSelectEpisode = { index, ep ->
                        UserLibrary.markEpisodeWatched(context, activeId, index + 1)
                        watchedTick++
                        onPlay(
                            EpisodePlay(
                                code = ep.contentId,
                                titleSuffix = "$seasonTitlePrefix · C${index + 1}",
                                seriesId = activeId,
                                episodeIds = episodes,
                                episodeNames = episodeNames,
                                episodeIndex = index,
                                seasonIds = seasonIdList,
                                seasonNames = seasonNameList,
                                seasonIndex = seasonIdx,
                                durationMs = itemDurationMs,
                            ),
                        )
                    },
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun cleanTvEpisodeTitle(rawName: String, epNumber: Int): String {
    val trimmed = rawName.trim()
    if (trimmed.isBlank()) return "Capítulo $epNumber"
    if (
        trimmed.equals("Capítulo $epNumber", ignoreCase = true) ||
        trimmed.equals("Capitulo $epNumber", ignoreCase = true) ||
        trimmed.equals("Episodio $epNumber", ignoreCase = true) ||
        trimmed.equals("Episode $epNumber", ignoreCase = true) ||
        trimmed == epNumber.toString()
    ) {
        return "Capítulo $epNumber"
    }
    return trimmed
}

@Composable
private fun TvSeriesSelectorPanel(
    seasons: List<com.chronosbvrx.visionplus.data.SeasonRef>,
    activeId: String,
    onSelectSeason: (com.chronosbvrx.visionplus.data.SeasonRef) -> Unit,
    episodePrograms: List<X4KApi.EpisodeProgram>,
    watchedCount: Int,
    nextEpisodeIndex: Int,
    watchedTick: Int,
    onSelectEpisode: (Int, X4KApi.EpisodeProgram) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(VisionSurface.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .border(1.dp, VisionBlue.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .background(VisionBlue.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                        .border(1.dp, VisionBlue.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "SERIE · VISION+",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionBlueBright,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Temporadas y capítulos en orden",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (episodePrograms.isNotEmpty()) {
                Text(
                    text = "${episodePrograms.size} capítulos" +
                        if (watchedCount > 0) " · $watchedCount vistos" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = VisionBlueBright,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (seasons.size > 1) {
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(seasons, key = { _, s -> "tv_se_${s.titleId}" }) { _, season ->
                    val selected = season.titleId == activeId
                    val label = if (season.n <= 0) "Completa" else "Temporada ${season.n}"
                    Surface(
                        onClick = { onSelectSeason(season) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) VisionBlue else VisionSurfaceHigh,
                            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                            focusedContainerColor = VisionBlue,
                            focusedContentColor = Color.White,
                            pressedContainerColor = VisionBlue,
                            pressedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                BorderStroke(
                                    1.dp,
                                    if (selected) VisionBlueBright else Color.White.copy(alpha = 0.14f),
                                ),
                            ),
                            focusedBorder = Border(BorderStroke(2.5.dp, Color.White)),
                        ),
                    ) {
                        Text(
                            text = if (selected) "●  $label" else label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }

        if (episodePrograms.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(episodePrograms, key = { idx, ep -> "tv_ep_${idx}_${ep.contentId}" }) { index, ep ->
                    val watched = remember(watchedTick, activeId, index) {
                        UserLibrary.isEpisodeWatched(context, activeId, index + 1)
                    }
                    val isNext = index == nextEpisodeIndex
                    val epTitle = cleanTvEpisodeTitle(ep.name, index + 1)
                    Surface(
                        onClick = { onSelectEpisode(index, ep) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = when {
                                isNext -> VisionBlue.copy(alpha = 0.24f)
                                watched -> VisionSurfaceHigh.copy(alpha = 0.72f)
                                else -> VisionSurfaceHigh
                            },
                            contentColor = Color.White,
                            focusedContainerColor = VisionBlue,
                            focusedContentColor = Color.White,
                            pressedContainerColor = VisionBlue,
                            pressedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                BorderStroke(
                                    if (isNext) 1.5.dp else 1.dp,
                                    when {
                                        isNext -> VisionBlueBright
                                        watched -> VisionBlue.copy(alpha = 0.4f)
                                        else -> Color.White.copy(alpha = 0.12f)
                                    },
                                ),
                            ),
                            focusedBorder = Border(BorderStroke(2.5.dp, Color.White)),
                        ),
                        modifier = Modifier.width(210.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(
                                    Modifier
                                        .background(
                                            if (isNext) VisionBlue else VisionBlack.copy(alpha = 0.65f),
                                            RoundedCornerShape(7.dp),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        "C${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = when {
                                        watched -> "✓ Visto"
                                        isNext -> "▶ Siguiente"
                                        else -> "Cap. ${index + 1}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        watched -> Color(0xFF7AD9A8)
                                        isNext -> VisionBlueBright
                                        else -> Color.White.copy(alpha = 0.78f)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = epTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Reads a startPlayVOD answer: its declared audio languages and its qualities. */
private fun parseDetail(detail: JSONObject?): Pair<List<String>, List<String>> {
    if (detail == null) return emptyList<String>() to emptyList()
    val langs = LinkedHashSet<String>()
    val qualities = LinkedHashSet<String>()
    val episodes = detail.optJSONArray("episodeList") ?: return emptyList<String>() to emptyList()
    for (i in 0 until episodes.length()) {
        val groups = episodes.optJSONObject(i)?.optJSONArray("totalMovieList") ?: continue
        for (g in 0 until groups.length()) {
            val group = groups.optJSONObject(g) ?: continue
            group.optString("quality").takeIf { it.isNotBlank() }?.let(qualities::add)
            val movies = group.optJSONArray("movieList") ?: continue
            for (m in 0 until movies.length()) {
                val movie = movies.optJSONObject(m) ?: continue
                movie.optString("audioInfo").split(',').map { it.trim() }.filter { it.isNotBlank() }
                    .forEach(langs::add)
            }
        }
    }
    return langs.toList() to qualities.toList()
}

/** "Action,Adventure" -> "Acción · Aventuras". */
private fun genreLabels(raw: String): String {
    if (raw.isBlank()) return ""
    val map = mapOf(
        "action" to "Acción", "adventure" to "Aventuras", "animation" to "Animación",
        "biography" to "Biografía", "comedy" to "Comedia", "crime" to "Crimen",
        "documentary" to "Documental", "drama" to "Drama", "family" to "Familia",
        "fantasy" to "Fantasía", "history" to "Historia", "horror" to "Terror",
        "kids" to "Infantil", "music" to "Música", "musical" to "Musical",
        "mystery" to "Misterio", "romance" to "Romántico", "sci-fi" to "Ciencia ficción",
        "thriller" to "Suspense", "war" to "Guerra", "western" to "Occidental",
        "sport" to "Deporte", "reality" to "Reality",
    )
    return raw.split(',').map { code ->
        map[code.trim().lowercase()] ?: code.trim().replaceFirstChar { it.uppercase() }
    }.joinToString(" · ")
}

/** "spa" -> "Español", with the raw code as last resort. */
private fun audioLabel(code: String): String = when (code.lowercase().trim()) {
    "spa", "es", "esp" -> "Español"
    "eng", "en" -> "Inglés"
    "por", "pt" -> "Portugués"
    "fra", "fre", "fr" -> "Francés"
    "deu", "ger", "de" -> "Alemán"
    "ita", "it" -> "Italiano"
    "jpn", "ja" -> "Japonés"
    "kor", "ko" -> "Coreano"
    "zho", "chi", "zh" -> "Chino"
    "rus", "ru" -> "Ruso"
    "ara", "ar" -> "Árabe"
    "hin", "hi" -> "Hindi"
    else -> code.trim().replaceFirstChar { it.uppercase() }
}

/** "02:17:00" -> "2 h 17 min". */
private fun prettyDuration(text: String): String {
    val parts = text.split(':').mapNotNull { it.trim().toIntOrNull() }
    if (parts.isEmpty()) return ""
    val h: Int
    val m: Int
    when (parts.size) {
        3 -> {
            h = parts[0]
            m = parts[1]
        }
        2 -> {
            h = 0
            m = parts[0]
        }
        else -> return ""
    }
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}
