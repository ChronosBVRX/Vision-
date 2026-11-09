package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.continueOpensSeries
import com.chronosbvrx.visionplus.data.episodeLabelOf
import com.chronosbvrx.visionplus.data.nextEpisodeEntry
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.resumeSeriesFilm
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.components.rememberPlaybackLongClick
import com.chronosbvrx.visionplus.ui.components.rememberPosterLongClick
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.isLandscapeTablet
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

import androidx.compose.runtime.collectAsState
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { FAVORITES, WATCH_LATER, SERIES, HISTORY, DOWNLOADS }

/** Filtro simple del Historial: todo, películas o series. */
private enum class HistoryFilter(val label: String) {
    ALL("Todo"),
    MOVIES("Películas"),
    SERIES("Series"),
}

/**
 * «Mi lista» en el teléfono: Favoritos, Ver más tarde, **Historial** (lo visto con su
 * avance y los terminados como «Visto») y Descargas, en cuadrícula.
 */
@Composable
fun LibraryScreen(
    catalog: XuperCatalog,
    contentPadding: PaddingValues,
    onOpenFilm: (Film) -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    LaunchedEffect(Unit) { catalog.refreshPersonal() }
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.FAVORITES) }
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()
    val context = LocalContext.current
    var historyCards by remember { mutableStateOf<List<ResumeCard>>(emptyList()) }
    var seriesFilms by remember { mutableStateOf<List<Film>>(emptyList()) }
    var unfollowTarget by remember { mutableStateOf<Film?>(null) }
    var historyFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val historyScope = rememberCoroutineScope()

    fun reloadHistory() {
        historyScope.launch(Dispatchers.Default) {
            val films = allFilms(catalog.personalRows + catalog.rows + catalog.indexRows)
            val cards = PlaybackHistory.historyCards(context, films)
            val followed = com.chronosbvrx.visionplus.data.follow.FollowStore.followedFilms(context, films)
            withContext(Dispatchers.Main) {
                historyCards = cards
                seriesFilms = followed
            }
        }
    }

    LaunchedEffect(catalog.personalRows, catalog.rows, catalog.indexRows) { reloadHistory() }
    // El reproductor vive en :x4k y escribe el XML; al volver de una reproducción se
    // relee el historial fresco (mismo patrón que «Seguir viendo» en el Inicio).
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadHistory()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val films = when (selectedTab) {
        LibraryTab.FAVORITES -> catalog.personalRows.firstOrNull { it.id == "favorites" }?.films.orEmpty()
        LibraryTab.WATCH_LATER -> catalog.personalRows.firstOrNull { it.id == "watch-later" }?.films.orEmpty()
        LibraryTab.SERIES -> seriesFilms
        LibraryTab.DOWNLOADS -> downloads.filter { it.state == DownloadState.COMPLETED }.map { it.toFilm() }
        LibraryTab.HISTORY -> emptyList()
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Text(
            "Mi lista",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTab == LibraryTab.FAVORITES,
                onClick = { selectedTab = LibraryTab.FAVORITES },
                label = { Text("Favoritos") },
                leadingIcon = {
                    Icon(VisionIcons.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            FilterChip(
                selected = selectedTab == LibraryTab.WATCH_LATER,
                onClick = { selectedTab = LibraryTab.WATCH_LATER },
                label = { Text("Ver más tarde") },
                leadingIcon = {
                    Icon(VisionIcons.WatchLater, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            FilterChip(
                selected = selectedTab == LibraryTab.SERIES,
                onClick = { selectedTab = LibraryTab.SERIES },
                label = { Text("Mis series") },
                leadingIcon = {
                    Icon(VisionIcons.Following, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            FilterChip(
                selected = selectedTab == LibraryTab.HISTORY,
                onClick = { selectedTab = LibraryTab.HISTORY },
                label = { Text("Historial") },
                leadingIcon = {
                    Icon(VisionIcons.ContinueWatching, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            FilterChip(
                selected = selectedTab == LibraryTab.DOWNLOADS,
                onClick = { selectedTab = LibraryTab.DOWNLOADS },
                label = { Text("Descargas") },
                leadingIcon = {
                    Icon(VisionIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
        }
        if (selectedTab == LibraryTab.HISTORY) {
            Text(
                "Mantén pulsada una tarjeta para marcar como visto/no visto o quitarla.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryFilter.values().forEach { filter ->
                    FilterChip(
                        selected = historyFilter == filter,
                        onClick = { historyFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        if (selectedTab == LibraryTab.HISTORY) {
            if (historyCards.isEmpty()) {
                EmptyState(
                    title = "Todavía no has visto nada",
                    subtitle = "Lo que veas aparecerá aquí con su avance; al terminar queda marcado como «Visto».",
                )
                return@Column
            }
            val visibleHistory = remember(historyCards, historyFilter) {
                historyCards.filter { card ->
                    when (historyFilter) {
                        HistoryFilter.ALL -> true
                        HistoryFilter.MOVIES -> !continueOpensSeries(card)
                        HistoryFilter.SERIES -> continueOpensSeries(card)
                    }
                }
            }
            if (visibleHistory.isEmpty()) {
                EmptyState(
                    title = "Sin resultados en este filtro",
                    subtitle = "Cambia a «Todo» para ver el resto de tu historial.",
                )
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isLandscapeTablet()) 5 else 3),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { }
                items(visibleHistory, key = { it.entry.key }) { card ->
                    val downloaded = downloads.any {
                        it.state == DownloadState.COMPLETED &&
                            (it.contentId == card.entry.xtvCode || it.id.contains(card.entry.xtvCode))
                    }
                    HistoryCard(
                        card = card,
                        downloaded = downloaded,
                        onResume = onResume,
                        onOpenFilm = onOpenFilm,
                    )
                }
            }
            return@Column
        }

        if (films.isEmpty()) {
            EmptyState(
                title = when (selectedTab) {
                    LibraryTab.FAVORITES -> "Todavía no tienes favoritos"
                    LibraryTab.WATCH_LATER -> "Nada para ver más tarde"
                    LibraryTab.SERIES -> "Todavía no sigues ninguna serie"
                    LibraryTab.DOWNLOADS -> "No tienes descargas aún"
                    LibraryTab.HISTORY -> "Todavía no has visto nada"
                },
                subtitle = when (selectedTab) {
                    LibraryTab.FAVORITES -> "En la ficha de una película toca «Favorito» y aparece aquí."
                    LibraryTab.WATCH_LATER -> "En la ficha de una película toca «+ Ver más tarde» y aparece aquí."
                    LibraryTab.SERIES -> "En la ficha de una serie toca «Seguir serie» y aparecerá aquí con sus novedades."
                    LibraryTab.DOWNLOADS -> "Toca «Descargar» en la ficha de una película para verla sin internet."
                    LibraryTab.HISTORY -> "Lo que veas aparecerá aquí con su avance."
                },
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isLandscapeTablet()) 5 else 3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { }
            items(films, key = { it.id }) { film ->
                val posterLong = rememberPosterLongClick(film)
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine(),
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    onLongClick = if (selectedTab == LibraryTab.SERIES) {
                        { unfollowTarget = film }
                    } else {
                        posterLong
                    },
                    onOptions = if (selectedTab == LibraryTab.SERIES) {
                        { unfollowTarget = film }
                    } else {
                        null
                    },
                    onClick = { onOpenFilm(film) },
                )
            }
        }
    }
    val target = unfollowTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { unfollowTarget = null },
            title = { Text("Dejar de seguir") },
            text = { Text("¿Quitar «${target.title}» de Mis series? No borra tu historial ni tus favoritos.") },
            confirmButton = {
                TextButton(onClick = {
                    com.chronosbvrx.visionplus.data.follow.FollowStore.unfollow(context, target.id)
                    seriesFilms = seriesFilms.filterNot { it.id == target.id }
                    unfollowTarget = null
                    android.widget.Toast.makeText(context, "Dejaste de seguir «${target.title}»", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Dejar de seguir") }
            },
            dismissButton = { TextButton(onClick = { unfollowTarget = null }) { Text("Cancelar") } },
        )
    }
}

/** Tarjeta del historial: póster, avance real y «Visto»/«Continuar en …». */
@Composable
private fun HistoryCard(
    card: ResumeCard,
    downloaded: Boolean = false,
    onResume: (ResumeCard) -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    val film = card.film
    val displayTitle = film?.title ?: card.entry.title
    val nextEntry = if (card.completed && card.hasNext) nextEpisodeEntry(card.entry) else null
    val opensSeries = continueOpensSeries(card) && card.completed && nextEntry == null
    val nextUp = card.completed && card.hasNext
    val episode = if (nextUp) "Siguiente capítulo"
    else episodeLabelOf(card.entry).takeIf { it.isNotBlank() }
        ?: if (card.entry.title != displayTitle) {
            card.entry.title.removePrefix(displayTitle).trim(' ', '·')
        } else {
            ""
        }
    Column {
        PosterCard(
            title = displayTitle,
            imageUrl = film?.posterUrl ?: card.entry.posterUrl.takeIf { it.isNotBlank() },
            meta = episode.ifBlank { film?.metaLine() },
            badge = if (card.completed) "VISTO" else null,
            onLongClick = rememberPlaybackLongClick(card),
            onOptions = rememberPlaybackLongClick(card),
            onClick = {
                when {
                    nextEntry != null -> onResume(card.copy(entry = nextEntry))
                    opensSeries -> onOpenFilm(film ?: resumeSeriesFilm(card.entry))
                    card.completed && film != null -> onOpenFilm(film)
                    card.completed -> onResume(card.copy(entry = card.entry.copy(positionMs = 0L)))
                    else -> onResume(card)
                }
            },
        )
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .height(3.dp)
                    .fillMaxWidth(card.progress)
                    .background(VisionBlue),
            )
        }
        Text(
            listOfNotNull(
                when {
                    nextUp -> "Siguiente capítulo"
                    card.completed -> "Visto"
                    opensSeries -> "Serie · Continuar en " + formatResume(card.entry.positionMs)
                    else -> "Continuar en " + formatResume(card.entry.positionMs)
                },
                "Descargado".takeIf { downloaded },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
