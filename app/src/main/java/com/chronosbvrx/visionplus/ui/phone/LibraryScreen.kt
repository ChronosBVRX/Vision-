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
import androidx.compose.material3.Icon
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
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.PosterCard
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

enum class LibraryTab { FAVORITES, WATCH_LATER, HISTORY, DOWNLOADS }

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
    val historyScope = rememberCoroutineScope()

    fun reloadHistory() {
        historyScope.launch(Dispatchers.Default) {
            val films = allFilms(catalog.personalRows + catalog.rows + catalog.indexRows)
            val cards = PlaybackHistory.historyCards(context, films)
            withContext(Dispatchers.Main) { historyCards = cards }
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
            if (historyCards.isEmpty()) {
                EmptyState(
                    title = "Todavía no has visto nada",
                    subtitle = "Lo que veas aparecerá aquí con su avance; al terminar queda marcado como «Visto».",
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
                items(historyCards, key = { it.entry.key }) { card ->
                    HistoryCard(card = card, onResume = onResume, onOpenFilm = onOpenFilm)
                }
            }
            return@Column
        }

        if (films.isEmpty()) {
            EmptyState(
                title = when (selectedTab) {
                    LibraryTab.FAVORITES -> "Todavía no tienes favoritos"
                    LibraryTab.WATCH_LATER -> "Nada para ver más tarde"
                    LibraryTab.DOWNLOADS -> "No tienes descargas aún"
                    LibraryTab.HISTORY -> "Todavía no has visto nada"
                },
                subtitle = when (selectedTab) {
                    LibraryTab.FAVORITES -> "En la ficha de una película toca «Favorito» y aparece aquí."
                    LibraryTab.WATCH_LATER -> "En la ficha de una película toca «+ Ver más tarde» y aparece aquí."
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
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine(),
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    onClick = { onOpenFilm(film) },
                )
            }
        }
    }
}

/** Tarjeta del historial: póster, avance real y «Visto»/«Continuar en …». */
@Composable
private fun HistoryCard(card: ResumeCard, onResume: (ResumeCard) -> Unit, onOpenFilm: (Film) -> Unit) {
    val film = card.film
    val displayTitle = film?.title ?: card.entry.title
    val episode = if (card.entry.title != displayTitle) {
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
            onClick = {
                if (card.completed) {
                    if (film != null) onOpenFilm(film)
                    else onResume(card.copy(entry = card.entry.copy(positionMs = 0L)))
                } else {
                    onResume(card)
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
            if (card.completed) "Visto" else "Continuar en " + formatResume(card.entry.positionMs),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
