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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.scoreLabel
import com.chronosbvrx.visionplus.ui.EpisodePlay
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.ui.isLandscapeTablet
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import coil.compose.AsyncImage
import org.json.JSONObject

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh

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
    var itemDurationMs by remember(film.id, activeId) { mutableStateOf(0L) }
    var description by remember(film.id) { mutableStateOf(film.synopsis) }
    var episodePrograms by remember(film.id) { mutableStateOf<List<X4KApi.EpisodeProgram>>(emptyList()) }
    var watchedTick by remember(film.id) { mutableStateOf(0) }
    val episodes = remember(episodePrograms) { episodePrograms.map { it.contentId } }
    val episodeNames = remember(episodePrograms) { episodePrograms.map { it.name } }

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
            val item = detail.optJSONObject("item") ?: return@LaunchedEffect
            val durMs = X4KApi.extractDurationMs(item)
            if (durMs > 0L) itemDurationMs = durMs
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
    val playButtonLabel = when {
        episodes.isNotEmpty() && watchedCount > 0 ->
            "Continuar$seasonTitlePrefix · Capítulo ${nextEpisodeIndex + 1}"
        episodes.isNotEmpty() ->
            "Reproducir$seasonTitlePrefix · Capítulo 1"
        else -> "Reproducir"
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val maxWidth = if (isLandscapeTablet()) 720.dp else Dp.Unspecified
            LazyColumn(Modifier.widthIn(max = maxWidth).fillMaxSize()) {
                item(key = "hero") { Hero(film) }
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
                                    targetEp != null -> {
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
                                    film.seasons.isNotEmpty() -> onPlay(
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
                                        if (itemDurationMs > 0L) {
                                            EpisodePlay(
                                                code = film.videoUrl.substringAfter(':'),
                                                durationMs = itemDurationMs,
                                            )
                                        } else null
                                    )
                                }
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            val inLater = remember(film.id) { mutableStateOf(UserLibrary.isWatchLater(context, film.id)) }
                            val inFavorites = remember(film.id) { mutableStateOf(UserLibrary.isFavorite(context, film.id)) }
                            TextButton(onClick = {
                                UserLibrary.toggleWatchLater(context, film)
                                inLater.value = !inLater.value
                            }) { Text(if (inLater.value) "✓ Ver más tarde" else "+ Ver más tarde", color = Color.White) }
                            TextButton(onClick = {
                                UserLibrary.toggleFavorite(context, film)
                                inFavorites.value = !inFavorites.value
                            }) { Text(if (inFavorites.value) "★ Favorito" else "☆ Favorito", color = Color.White) }
                        }
                        if (film.seasons.size > 1 || episodes.isNotEmpty()) {
                            MobileSeriesSelectorSection(
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
                        }
                        Synopsis(description)
                    }
                }
                item(key = "end") { Spacer(Modifier.height(32.dp)) }
            }
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

private fun cleanEpisodeTitle(rawName: String, epNumber: Int): String {
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
private fun MobileSeriesSelectorSection(
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
                            text = if (selected) "●  $label" else label,
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(episodePrograms, key = { idx, ep -> "ep_${idx}_${ep.contentId}" }) { index, ep ->
                    val watched = remember(watchedTick, activeId, index) {
                        UserLibrary.isEpisodeWatched(context, activeId, index + 1)
                    }
                    val isNext = index == nextEpisodeIndex
                    val epTitle = cleanEpisodeTitle(ep.name, index + 1)
                    Column(
                        modifier = Modifier
                            .width(178.dp)
                            .background(
                                when {
                                    isNext -> VisionBlue.copy(alpha = 0.22f)
                                    watched -> VisionSurfaceHigh.copy(alpha = 0.75f)
                                    else -> VisionSurfaceHigh
                                },
                                RoundedCornerShape(14.dp),
                            )
                            .border(
                                width = if (isNext) 1.5.dp else 1.dp,
                                color = when {
                                    isNext -> VisionBlueBright
                                    watched -> VisionBlue.copy(alpha = 0.4f)
                                    else -> Color.White.copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelectEpisode(index, ep) }
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier
                                    .background(
                                        if (isNext) VisionBlue else VisionBlack.copy(alpha = 0.65f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
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
                                    else -> "Orden #${index + 1}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    watched -> Color(0xFF7AD9A8)
                                    isNext -> VisionBlueBright
                                    else -> VisionTextSecondary
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = epTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (watched) "Volver a reproducir" else "▶ Reproducir capítulo",
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionBlueBright,
                        )
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
                Text("★ $score", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
private fun Hero(film: Film) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        AsyncImage(
            model = film.posterUrl,
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
