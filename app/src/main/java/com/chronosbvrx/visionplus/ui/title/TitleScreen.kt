package com.chronosbvrx.visionplus.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.scoreLabel
import com.chronosbvrx.visionplus.ui.player.episodeRowLabel
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.ui.EpisodePlay
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.isLandscapeTablet
import com.chronosbvrx.visionplus.ui.isLandscapePhone
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import coil.compose.AsyncImage
import org.json.JSONObject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineDownload
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.data.offline.TvStorageRequiredException
import com.chronosbvrx.visionplus.data.offline.seasonDownloadActionable
import com.chronosbvrx.visionplus.data.offline.seasonDownloadLabel
import com.chronosbvrx.visionplus.data.offline.seasonDownloadSummary
import com.chronosbvrx.visionplus.ui.theme.VisionBlue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSuccess
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.data.remote.RemoteClient
import com.chronosbvrx.visionplus.data.remote.RemoteStatus
import com.chronosbvrx.visionplus.data.remote.resumePositionFor
import com.chronosbvrx.visionplus.ui.remote.castableRef

/**
 * A film's information page: backdrop and title, the main button, the professional metadata
 * (score, genres, cast, languages) when the source carries it, and the synopsis.
 */
@Composable
fun TitleScreen(film: Film, onBack: () -> Unit, onPlay: (EpisodePlay?) -> Unit) {
    val context = LocalContext.current
    val isXtv = film.videoUrl.startsWith("xtv:")
    val meta = remember(film.id) { if (isXtv) X4KApi.metaFor(context, film.id) else null }
    var languages by remember(film.id) { mutableStateOf<List<String>>(emptyList()) }
    var qualities by remember(film.id) { mutableStateOf<List<String>>(emptyList()) }
    var itemLine by remember(film.id) { mutableStateOf("") }
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
    // El selector de temporada/capítulo del botón «Ver en la TV» (solo series, y solo cuando
    // el control remoto tiene una conexión viva).
    var castPicker by remember(film.id) { mutableStateOf(false) }
    val episodes = remember(episodePrograms) { episodePrograms.map { it.contentId } }
    val episodeNames = remember(episodePrograms) { episodePrograms.map { it.name } }
    val episodeNumbers = remember(episodePrograms) { episodePrograms.map { it.number } }

    if (isXtv) {
        LaunchedEffect(film.id, activeId) {
            // Los capítulos se cargan y ordenan primero para estar listos de inmediato.
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
            val item = detail.optJSONObject("item")
            val durMs = X4KApi.extractDurationMs(item).takeIf { it > 0L } ?: X4KApi.extractDurationMs(detail)
            if (durMs > 0L) itemDurationMs = durMs
            if (item == null) return@LaunchedEffect
            val duration = prettyDuration(item.optString("duration").ifBlank { if (durMs > 0) "${durMs / 60_000} min" else "" })
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

    // Descargas del dispositivo (reactivas): alimentan los botones por capítulo y de temporada.
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()
    val downloadsOn = remember(film.id) {
        com.chronosbvrx.visionplus.data.config.RemoteConfig.flag(context, "downloads_enabled", true)
    }
    val seasonDownloadItems = remember(downloads, film.id, episodes) {
        episodes.map { code -> OfflineRegistry.findDownloadFor(film.id, code, context = context) }
    }
    val seasonSummary = remember(seasonDownloadItems) { seasonDownloadSummary(seasonDownloadItems) }

    fun episodePlayFor(index: Int): EpisodePlay? {
        val code = episodes.getOrNull(index) ?: return null
        return EpisodePlay(
            code = code,
            titleSuffix = "$seasonTitlePrefix · C${index + 1}",
            seriesId = activeId,
            episodeIds = episodes,
            episodeNames = episodeNames,
            episodeNumbers = episodeNumbers,
            episodeIndex = index,
            seasonIds = seasonIdList,
            seasonNames = seasonNameList,
            seasonIndex = seasonIdx,
            durationMs = itemDurationMs,
        )
    }

    // Descarga un capítulo (o el que la temporada tenga pendiente): el mismo camino que el botón
    // de la ficha; reanudar un pausado/con error lo vuelve a encolar en la cola del motor.
    val onDownloadEpisode: (Int) -> Unit = { index ->
        episodePlayFor(index)?.let { ep ->
            try {
                OfflineDownloadManager.enqueueDownload(context, film, ep)
            } catch (_: TvStorageRequiredException) {
                Toast.makeText(context, "Conecta una memoria USB para descargar", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onSeasonDownload: () -> Unit = {
        try {
            val n = OfflineDownloadManager.enqueueSeason(
                context = context,
                film = film,
                seasonId = activeId,
                seasonIndex = seasonIdx,
                episodeCodes = episodes,
                durationMs = itemDurationMs,
            )
            if (n > 0) {
                Toast.makeText(context, "Temporada en descarga · $n capítulos", Toast.LENGTH_SHORT).show()
            }
        } catch (_: TvStorageRequiredException) {
            Toast.makeText(context, "Conecta una memoria USB para descargar", Toast.LENGTH_SHORT).show()
        }
    }

    // Adelanta el handshake del portal mientras se mira la ficha: pulsar Reproducir
    // no debe esperar el portal (lo mismo que hace la app del proveedor).
    LaunchedEffect(activeId, isSeriesTitle, episodes, nextEpisodeIndex, itemDurationMs) {
        if (!isXtv) return@LaunchedEffect
        val target = if (isSeriesTitle && episodes.size > 1) {
            episodes.getOrNull(nextEpisodeIndex.coerceIn(0, episodes.lastIndex))
        } else {
            film.videoUrl.substringAfter(':').takeIf { it.isNotBlank() }
        } ?: return@LaunchedEffect
        X4KApi.prewarmVod(
            context = context,
            contentId = target,
            seriesId = if (isSeriesTitle) activeId else "",
            fallbackDurationMs = itemDurationMs,
            epNumber = if (isSeriesTitle) nextEpisodeIndex + 1 else 1,
        )
    }
    val playButtonLabel = when {
        isSeriesTitle && watchedCount > 0 ->
            "Continuar$seasonTitlePrefix · Capítulo ${nextEpisodeIndex + 1}"
        isSeriesTitle ->
            "Reproducir$seasonTitlePrefix · Capítulo 1"
        else -> "Reproducir"
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val landscapePhone = isLandscapePhone()
            val maxWidth = when {
                isLandscapeTablet() -> 720.dp
                landscapePhone -> 600.dp
                else -> Dp.Unspecified
            }
            LazyColumn(Modifier.widthIn(max = maxWidth).fillMaxSize()) {
                item(key = "hero") { Hero(film, compact = landscapePhone) }
                item(key = "actions") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (isXtv) {
                            XtvMetadata(meta, languages, qualities, itemLine, film.score)
                        } else {
                            film.metaLine().takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
                            }
                            Text(
                                "Dominio público · Internet Archive",
                                style = MaterialTheme.typography.labelMedium,
                                color = VisionTextSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            label = playButtonLabel,
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
                                                episodeNumbers = episodeNumbers,
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
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            val inLater = remember(film.id) { mutableStateOf(UserLibrary.isWatchLater(context, film.id)) }
                            val inFavorites = remember(film.id) { mutableStateOf(UserLibrary.isFavorite(context, film.id)) }
                            // El botón refleja el capítulo objetivo (el mismo que reproduce), no cualquier
                            // descarga de la serie.
                            val targetEpisodeCode = episodes.getOrNull(
                                nextEpisodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)),
                            )
                            val downloadItem = remember(film.id, downloads, targetEpisodeCode) {
                                OfflineRegistry.findDownloadFor(film.id, targetEpisodeCode, context = context)
                            }

                            TextButton(onClick = {
                                UserLibrary.toggleWatchLater(context, film)
                                inLater.value = !inLater.value
                                com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(context)
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        if (inLater.value) VisionIcons.WatchLaterOn else VisionIcons.WatchLater,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("Ver más tarde", color = Color.White)
                                }
                            }

                            TextButton(onClick = {
                                UserLibrary.toggleFavorite(context, film)
                                inFavorites.value = !inFavorites.value
                                com.chronosbvrx.visionplus.data.sync.SyncStore.syncAsync(context)
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        if (inFavorites.value) VisionIcons.Favorite else VisionIcons.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (inFavorites.value) VisionRatingGold else Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("Favorito", color = Color.White)
                                }
                            }

                            // Abrir en la tele: solo con una conexión VIVA del control remoto, para no
                            // dejar un botón muerto cuando no hay ninguna tele escuchando. En series
                            // abre el selector de temporada/capítulo: mandar la serie «en general»
                            // abría en la tele sin el capítulo que uno está eligiendo.
                            if (castableRef(film.videoUrl) && RemoteClient.state.value.status == RemoteStatus.CONNECTED) {
                                TextButton(onClick = {
                                    if (isSeriesTitle) {
                                        castPicker = true
                                    } else {
                                        RemoteClient.sendOpen(
                                            film.videoUrl,
                                            film.title,
                                            film.posterUrl,
                                            positionMs = resumePositionFor(context, film.videoUrl),
                                        )
                                        Toast.makeText(context, "Abriendo en la tele…", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Default.Cast,
                                            contentDescription = null,
                                            tint = VisionBlueBright,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text("Ver en la TV", color = VisionBlueBright)
                                    }
                                }
                            }

                            // Seguir serie: avisa cuando hay capítulo nuevo (agenda del servidor).
                            if (isSeriesTitle) {
                                val following = remember(film.id) {
                                    mutableStateOf(
                                        com.chronosbvrx.visionplus.data.follow.FollowStore.isFollowing(context, film.id),
                                    )
                                }
                                TextButton(onClick = {
                                    following.value =
                                        com.chronosbvrx.visionplus.data.follow.FollowStore.toggle(context, film.id, film.title)
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            if (following.value) VisionIcons.Following else VisionIcons.Follow,
                                            contentDescription = null,
                                            tint = if (following.value) VisionBlue else Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            if (following.value) "Siguiendo" else "Seguir serie",
                                            color = if (following.value) VisionBlue else Color.White,
                                        )
                                    }
                                }
                            }

                            // Botón de Descarga Offline
                            when (downloadItem?.state) {
                                DownloadState.COMPLETED -> {
                                    TextButton(onClick = { onPlay(EpisodePlay(code = downloadItem.playUri)) }) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                VisionIcons.Downloaded,
                                                contentDescription = null,
                                                tint = VisionBlue,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text("Descargado", color = VisionBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                DownloadState.DOWNLOADING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        CircularProgressIndicator(
                                            progress = { downloadItem.progressFraction },
                                            modifier = Modifier.size(16.dp),
                                            color = VisionBlue,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("${downloadItem.progressPercent}%", color = VisionBlue, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                DownloadState.QUEUED -> {
                                    TextButton(onClick = { }) {
                                        Text("En cola…", color = VisionTextSecondary)
                                    }
                                }
                                else -> {
                                    TextButton(
                                        enabled = downloadsOn,
                                        onClick = {
                                            // Las series descargan el capítulo objetivo (mismo que Reproducir),
                                            // no la ficha: sin esto siempre intentaba el seriesId y fallaba.
                                            if (isSeriesTitle && episodes.isNotEmpty()) {
                                                onDownloadEpisode(nextEpisodeIndex.coerceIn(0, episodes.lastIndex))
                                            } else {
                                                try {
                                                    OfflineDownloadManager.enqueueDownload(context, film, null)
                                                } catch (_: TvStorageRequiredException) {
                                                    Toast.makeText(context, "Conecta una memoria USB para descargar", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (downloadsOn) {
                                                Icon(
                                                    VisionIcons.Download,
                                                    contentDescription = null,
                                                    tint = VisionBlue,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            Text(
                                                if (downloadsOn) "Descargar" else "Descargas desactivadas",
                                                color = if (downloadsOn) VisionBlue else VisionTextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (episodes.size > 1 || film.seasons.size > 1) {
                            MobileSeriesSelectorSection(
                                seasons = film.seasons,
                                activeId = activeId,
                                posterUrl = film.posterUrl,
                                seriesTitle = film.title,
                                onSelectSeason = { selectedSeason = it },
                                episodePrograms = episodePrograms,
                                watchedCount = watchedCount,
                                nextEpisodeIndex = nextEpisodeIndex,
                                watchedTick = watchedTick,
                                onToggleWatched = { index ->
                                    val was = UserLibrary.isEpisodeWatched(context, activeId, index + 1)
                                    UserLibrary.markEpisodeWatched(context, activeId, index + 1, !was)
                                    watchedTick++
                                },
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
                                            episodeNumbers = episodeNumbers,
                                            episodeIndex = index,
                                            seasonIds = seasonIdList,
                                            seasonNames = seasonNameList,
                                            seasonIndex = seasonIdx,
                                            durationMs = itemDurationMs,
                                        ),
                                    )
                                },
                                downloads = downloads,
                                downloadsOn = downloadsOn,
                                seasonSummary = seasonSummary,
                                onSeasonDownload = onSeasonDownload,
                                onDownloadEpisode = onDownloadEpisode,
                                onPlayDownloaded = { playUri -> onPlay(EpisodePlay(code = playUri)) },
                                filmId = film.id,
                            )
                        }
                        Synopsis(description)
                    }
                }
                item(key = "end") { Spacer(Modifier.height(32.dp)) }
            }
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart))

        if (castPicker) {
            com.chronosbvrx.visionplus.ui.remote.CastEpisodeDialog(
                film = film,
                initialSeasonIndex = seasonIdx,
                onDismiss = { castPicker = false },
                onSend = { ep ->
                    RemoteClient.sendOpen(
                        ep.ref,
                        ep.title,
                        ep.poster,
                        seriesId = ep.seriesId,
                        seasonIndex = ep.seasonIndex,
                        seasonIds = ep.seasonIds,
                        seasonNames = ep.seasonNames,
                        positionMs = resumePositionFor(context, ep.ref),
                    )
                    Toast.makeText(context, "Abriendo ${ep.title} en la tele…", Toast.LENGTH_SHORT).show()
                    castPicker = false
                },
                onSendTitle = {
                    RemoteClient.sendOpen(
                        film.videoUrl,
                        film.title,
                        film.posterUrl,
                        positionMs = resumePositionFor(context, film.videoUrl),
                    )
                    Toast.makeText(context, "Abriendo en la tele…", Toast.LENGTH_SHORT).show()
                    castPicker = false
                },
            )
        }
    }
}

@Composable
private fun MobileSeriesSelectorSection(
    seasons: List<com.chronosbvrx.visionplus.data.SeasonRef>,
    activeId: String,
    posterUrl: String,
    seriesTitle: String,
    onSelectSeason: (com.chronosbvrx.visionplus.data.SeasonRef) -> Unit,
    episodePrograms: List<X4KApi.EpisodeProgram>,
    watchedCount: Int,
    nextEpisodeIndex: Int,
    watchedTick: Int,
    onToggleWatched: (Int) -> Unit,
    onSelectEpisode: (Int, X4KApi.EpisodeProgram) -> Unit,
    filmId: String,
    downloads: List<OfflineDownload>,
    downloadsOn: Boolean,
    seasonSummary: com.chronosbvrx.visionplus.data.offline.SeasonDownloadSummary,
    onSeasonDownload: () -> Unit,
    onDownloadEpisode: (Int) -> Unit,
    onPlayDownloaded: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(VisionSurface, RoundedCornerShape(18.dp))
            .border(1.dp, VisionBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .background(VisionBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, VisionBlue.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "SERIE",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionBlueBright,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Temporadas y capítulos",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (episodePrograms.isNotEmpty()) {
                Text(
                    text = "${episodePrograms.size} cap." + if (watchedCount > 0) " · $watchedCount vistos" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = VisionBlueBright,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Descargar la temporada completa: la cola del motor va bajando un capítulo por uno.
        if (downloadsOn && episodePrograms.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val actionable = seasonDownloadActionable(seasonSummary)
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (actionable) VisionBlue.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
                    .border(
                        1.dp,
                        if (actionable) VisionBlue.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = actionable) { onSeasonDownload() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (seasonSummary.active > 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = VisionBlue,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (seasonSummary.allDone) VisionIcons.Downloaded else VisionIcons.Download,
                        contentDescription = null,
                        tint = if (actionable || seasonSummary.allDone) VisionBlue else VisionTextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    seasonDownloadLabel(seasonSummary),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (actionable || seasonSummary.active > 0 || seasonSummary.allDone) Color.White else VisionTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (actionable) {
                    Icon(
                        VisionIcons.Download,
                        contentDescription = null,
                        tint = VisionBlue,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (seasons.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(seasons, key = { _, s -> "se_${s.titleId}" }) { _, season ->
                    val selected = season.titleId == activeId
                    val label = if (season.n <= 0) "Completa" else "Temporada ${season.n}"
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) VisionBlue else VisionSurfaceHigh,
                                RoundedCornerShape(12.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) VisionBlueBright else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelectSeason(season) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (episodePrograms.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            // Barra de avance de la temporada: cuánto llevas visto, de un vistazo.
            if (watchedCount > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((watchedCount.toFloat() / episodePrograms.size).coerceIn(0.02f, 1f))
                            .height(3.dp)
                            .background(VisionBlueBright),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.padding(horizontal = 8.dp)) {
                episodePrograms.forEachIndexed { index, ep ->
                    val watched = remember(watchedTick, activeId, index) {
                        UserLibrary.isEpisodeWatched(context, activeId, index + 1)
                    }
                    val isNext = index == nextEpisodeIndex
                    val epTitle = episodeRowLabel(ep.name, index + 1, seriesTitle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isNext) VisionBlue.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelectEpisode(index, ep) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isNext) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VisionBlueBright),
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        // Miniatura con la portada de la serie (el portal no trae stills por
                        // episodio): la cifra del capítulo vive sobre el arte, como en los
                        // productos grandes.
                        Box(
                            Modifier
                                .width(46.dp)
                                .height(66.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VisionSurfaceHigh),
                        ) {
                            if (posterUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(posterUrl).size(92, 132).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .background(VisionBlack.copy(alpha = 0.82f), RoundedCornerShape(topEnd = 6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    "C${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = epTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (watched) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 3.dp),
                                ) {
                                    Icon(
                                        VisionIcons.CheckCircle,
                                        contentDescription = null,
                                        tint = VisionSuccess,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        "Visto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VisionSuccess,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else if (isNext) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 3.dp),
                                ) {
                                    Icon(
                                        VisionIcons.Play,
                                        contentDescription = null,
                                        tint = VisionBlueBright,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        "Siguiente",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VisionBlueBright,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        // Descarga offline del capítulo: descargar / en cola / % / reproducir.
                        if (downloadsOn) {
                            val dl = remember(downloads, filmId, index) {
                                downloads.firstOrNull { it.id == "$filmId:ep:${ep.contentId}" }
                                    ?: downloads.firstOrNull { it.id.endsWith(":ep:${ep.contentId}") }
                            }
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) {
                                        when {
                                            dl == null -> onDownloadEpisode(index)
                                            dl.state == DownloadState.COMPLETED -> onPlayDownloaded(dl.playUri)
                                            dl.state == DownloadState.DOWNLOADING || dl.state == DownloadState.QUEUED -> Unit
                                            else -> onDownloadEpisode(index)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    dl == null -> Icon(
                                        VisionIcons.Download,
                                        contentDescription = "Descargar capítulo",
                                        tint = VisionBlue.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    dl.state == DownloadState.DOWNLOADING -> CircularProgressIndicator(
                                        progress = { dl.progressFraction },
                                        modifier = Modifier.size(18.dp),
                                        color = VisionBlue,
                                        strokeWidth = 2.dp,
                                    )
                                    dl.state == DownloadState.COMPLETED -> Icon(
                                        VisionIcons.Downloaded,
                                        contentDescription = "Reproducir descarga",
                                        tint = VisionBlue,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    dl.state == DownloadState.QUEUED -> Icon(
                                        VisionIcons.Download,
                                        contentDescription = "En cola de descarga",
                                        tint = VisionTextSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    dl.state == DownloadState.PAUSED || dl.state == DownloadState.ERROR -> Icon(
                                        VisionIcons.Replay,
                                        contentDescription = "Reanudar descarga",
                                        tint = VisionBlue,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    else -> Icon(
                                        VisionIcons.Download,
                                        contentDescription = "Descargar capítulo",
                                        tint = VisionBlue.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        // Alternar visto / no visto sin reproducir.
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onToggleWatched(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (watched) VisionIcons.CheckCircle else VisionIcons.Unwatched,
                                contentDescription = if (watched) "Marcar como no visto" else "Marcar como visto",
                                tint = if (watched) VisionSuccess else VisionTextSecondary,
                                modifier = Modifier.size(22.dp),
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
    val langs = LinkedHashSet<String>()
    val qualities = LinkedHashSet<String>()
    val episodes = detail?.optJSONArray("episodeList") ?: return emptyList<String>() to emptyList()
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

/** The professional block: score, genres, cast, director, running time and the languages. */
@Composable
private fun XtvMetadata(
    meta: JSONObject?,
    languages: List<String>,
    qualities: List<String>,
    itemLine: String,
    fallbackScore: Double = 0.0,
) {
    val score = scoreLabel(meta?.optString("sc"), fallbackScore)
    val genres = genreLabels(meta?.optString("gen").orEmpty())
    val director = meta?.optString("dir").orEmpty()
    val cast = meta?.optString("act").orEmpty()
        .split(',').map { it.trim() }.filter { it.isNotBlank() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (score.isNotBlank()) {
            Box(
                Modifier
                    .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        VisionIcons.Rating,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(score, color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (qualities.isNotEmpty()) {
            Text(
                qualities.joinToString(" · ") { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Text(
            "Incluido con tu cuenta",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF7FD1AE),
        )
    }
    if (itemLine.isNotBlank()) {
        Text(
            itemLine,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (genres.isNotBlank()) {
        Text(
            genres,
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (languages.isNotEmpty()) {
        Text(
            "Idiomas: " + languages.joinToString(" · ") { audioLabel(it) },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (director.isNotBlank()) {
        Text(
            "Dirigida por $director",
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (cast.isNotEmpty()) {
        Text(
            "Con: " + cast.take(5).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "02:17:00" -> "2 h 17 min". */
private fun prettyDuration(text: String): String {
    val parts = text.split(':').mapNotNull { it.trim().toIntOrNull() }
    if (parts.isEmpty()) return ""
    val h = when (parts.size) {
        3 -> parts[0]
        2 -> 0
        else -> return ""
    }
    val m = when (parts.size) {
        3 -> parts[1]
        2 -> parts[0]
        else -> return ""
    }
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
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

@Composable
private fun Hero(film: Film, compact: Boolean = false) {
    val context = LocalContext.current
    val screenPx = com.chronosbvrx.visionplus.ui.rememberScreenPx()
    // En apaisado el 16:9 se come la pantalla: pancarta compacta con el mismo arte.
    Box(Modifier.fillMaxWidth().then(if (compact) Modifier.height(190.dp) else Modifier.aspectRatio(16f / 9f))) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(film.posterUrl)
                .crossfade(0)
                .size(screenPx.width, (screenPx.width * 9 / 16).coerceAtLeast(240))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, VisionBlack))))
        Text(
            film.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier
            .statusBarsPadding()
            .padding(8.dp)
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
    }
}

@Composable
private fun PrimaryButton(label: String = "Reproducir", onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/** Three lines and "Más" to expand; nothing at all when there is no synopsis. */
@Composable
private fun Synopsis(text: String) {
    if (text.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.length > 160) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Menos" else "Más", color = Color.White)
            }
        }
    }
}
