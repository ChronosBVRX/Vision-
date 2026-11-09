package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.components.rememberPlaybackLongClick
import com.chronosbvrx.visionplus.ui.components.rememberPosterLongClick
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TvHistoryFilter(val label: String) {
    ALL("Todo"),
    MOVIES("Películas"),
    SERIES("Series"),
}

/** «Mi lista» en la TV: Favoritos, Ver más tarde, Historial y Descargas, con el mando. */@Composable
fun TvLibraryScreen(
    catalog: XuperCatalog,
    returnFilmId: String? = null,
    onOpenFilm: (Film) -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    LaunchedEffect(Unit) { catalog.refreshPersonal() }
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0 Favoritos · 1 Ver más tarde · 2 Descargas · 3 Historial
    val landing = rememberLandingFocus(activeTab)
    val returnRequester = remember { FocusRequester() }
    val rowEntry = remember { FocusRequester() }
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()
    val context = LocalContext.current
    var historyCards by remember { mutableStateOf<List<ResumeCard>>(emptyList()) }
    var seriesFilms by remember { mutableStateOf<List<Film>>(emptyList()) }
    var unfollowTarget by remember { mutableStateOf<Film?>(null) }
    var historyFilter by rememberSaveable { mutableStateOf(TvHistoryFilter.ALL) }
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
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadHistory()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    val rawFilms = when (activeTab) {
        0 -> catalog.personalRows.firstOrNull { it.id == "favorites" }?.films.orEmpty()
        1 -> catalog.personalRows.firstOrNull { it.id == "watch-later" }?.films.orEmpty()
        2 -> downloads.filter { it.state == DownloadState.COMPLETED }.map { it.toFilm() }
        4 -> seriesFilms
        else -> emptyList()
    }
    val films = remember(rawFilms) { rawFilms.distinctBy { it.id } }
    val historyVisible = activeTab == 3
    val isEmpty = if (historyVisible) historyCards.isEmpty() else films.isEmpty()
    val contentKey = if (historyVisible) historyCards.size else films.size
    val returnIndex = remember(historyVisible, films, historyCards, returnFilmId) {
        if (returnFilmId == null) {
            -1
        } else if (historyVisible) {
            historyCards.indexOfFirst { it.film?.id == returnFilmId || it.entry.key == returnFilmId }
        } else {
            films.indexOfFirst { it.id == returnFilmId }
        }
    }
    rememberReturnFocus(
        filmId = returnFilmId,
        requester = returnRequester,
        landing = landing,
        contentKey = contentKey,
        entryRequester = rowEntry,
    )
    val gridState = rememberLazyGridState()
    LaunchedEffect(returnIndex, contentKey) { gridState.revealReturnIndex(returnIndex) }
    val emptyLanding = if (isEmpty) Modifier.landingFocus(landing) else Modifier

    Column(Modifier.fillMaxSize().background(VisionBlack).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mi lista", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.width(20.dp))
            TvChoiceChip("Favoritos", selected = activeTab == 0, onClick = { activeTab = 0 },
                icon = VisionIcons.Favorite,
                modifier = if (activeTab == 0) emptyLanding else Modifier)
            Spacer(Modifier.width(10.dp))
            TvChoiceChip("Ver más tarde", selected = activeTab == 1, onClick = { activeTab = 1 },
                icon = VisionIcons.WatchLater,
                modifier = if (activeTab == 1) emptyLanding else Modifier)
            Spacer(Modifier.width(10.dp))
            TvChoiceChip("Mis series", selected = activeTab == 4, onClick = { activeTab = 4 },
                icon = VisionIcons.Following,
                modifier = if (activeTab == 4) emptyLanding else Modifier)
            Spacer(Modifier.width(10.dp))
            TvChoiceChip("Historial", selected = activeTab == 3, onClick = { activeTab = 3 },
                icon = VisionIcons.ContinueWatching,
                modifier = if (activeTab == 3) emptyLanding else Modifier)
            Spacer(Modifier.width(10.dp))
            TvChoiceChip("Descargas", selected = activeTab == 2, onClick = { activeTab = 2 },
                icon = VisionIcons.Usb,
                modifier = if (activeTab == 2) emptyLanding else Modifier)
        }
        if (activeTab == 3) {
            Text(
                "Mantén OK en una tarjeta para marcar como visto/no visto o quitarla.",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvHistoryFilter.values().forEach { filter ->
                    TvChoiceChip(
                        filter.label,
                        selected = historyFilter == filter,
                        onClick = { historyFilter = filter },
                    )
                }
            }
        }
        Spacer(Modifier.padding(top = 10.dp))
        if (isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (activeTab) {
                            0 -> "Todavía no tienes favoritos"
                            1 -> "Nada para ver más tarde"
                            2 -> "No tienes descargas en tu memoria USB"
                            4 -> "Todavía no sigues ninguna serie"
                            else -> "Todavía no has visto nada"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        when (activeTab) {
                            0 -> "En la ficha de una película elige «Favorito»."
                            1 -> "En la ficha de una película elige «Ver más tarde»."
                            2 -> "Conecta una memoria USB y elige «Descargar en USB» para ver sin internet."
                            4 -> "En la ficha de una serie elige «Seguir serie» y aparecerá aquí."
                            else -> "Lo que veas aparecerá aquí con su avance; al terminar queda como «Visto»."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            return@Column
        }

        if (historyVisible) {
            val visibleHistory = remember(historyCards, historyFilter) {
                historyCards.filter { card ->
                    when (historyFilter) {
                        TvHistoryFilter.ALL -> true
                        TvHistoryFilter.MOVIES -> !continueOpensSeries(card)
                        TvHistoryFilter.SERIES -> continueOpensSeries(card)
                    }
                }
            }
            if (visibleHistory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin resultados en este filtro",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
                return@Column
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { }
                itemsIndexed(visibleHistory, key = { _, card -> card.entry.key }) { index, card ->
                    val downloaded = downloads.any {
                        it.state == DownloadState.COMPLETED &&
                            (it.contentId == card.entry.xtvCode || it.id.contains(card.entry.xtvCode))
                    }
                    TvHistoryCard(
                        card = card,
                        downloaded = downloaded,
                        index = index,
                        returnFilmId = returnFilmId,
                        returnRequester = returnRequester,
                        landing = landing,
                        rowEntry = rowEntry,
                        onResume = onResume,
                        onOpenFilm = onOpenFilm,
                    )
                }
            }
            return@Column
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { }
            itemsIndexed(films, key = { _, film -> film.id }) { index, film ->
                val posterLong = rememberPosterLongClick(film)
                Column(Modifier.fillMaxWidth()) {
                    TvPosterCard(
                        title = film.title,
                        imageUrl = film.posterUrl,
                        score = film.score,
                        badge = if (film.year >= 2025) "NUEVO" else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                when {
                                    film.id == returnFilmId -> Modifier.focusRequester(returnRequester)
                                    index == 0 -> Modifier.landingFocus(landing)
                                    index % 5 == 0 -> Modifier.focusRequester(rowEntry)
                                    else -> Modifier
                                },
                            ),
                        onLongClick = if (activeTab == 4) {
                            { unfollowTarget = film }
                        } else {
                            posterLong
                        },
                        onClick = { onOpenFilm(film) },
                    )
                    Text(
                        film.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
    val target = unfollowTarget
    if (target != null) {
        val dialogFocus = rememberDialogFocus()
        TvDialog("Dejar de seguir", onDismiss = { unfollowTarget = null }) {
            Text(
                "¿Quitar «${target.title}» de Mis series? No borra tu historial ni tus favoritos.",
                color = VisionTextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvCompactAction(
                    label = "Dejar de seguir",
                    modifier = Modifier.dialogFocus(dialogFocus),
                    onClick = {
                        com.chronosbvrx.visionplus.data.follow.FollowStore.unfollow(context, target.id)
                        seriesFilms = seriesFilms.filterNot { it.id == target.id }
                        unfollowTarget = null
                    },
                )
                TvCompactAction(label = "Cancelar", onClick = { unfollowTarget = null })
            }
        }
    }
}

/** Tarjeta del historial en TV: póster, avance y «Visto»/«Continuar en …». */
@Composable
private fun TvHistoryCard(
    card: ResumeCard,
    downloaded: Boolean = false,
    index: Int,
    returnFilmId: String?,
    returnRequester: FocusRequester,
    landing: LandingFocus,
    rowEntry: FocusRequester,
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
    val isReturn = film?.id == returnFilmId || card.entry.key == returnFilmId
    Column(Modifier.fillMaxWidth()) {
        TvPosterCard(
            title = displayTitle,
            imageUrl = film?.posterUrl ?: card.entry.posterUrl.takeIf { it.isNotBlank() },
            score = film?.score ?: 0.0,
            badge = if (card.completed) "VISTO" else null,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        isReturn -> Modifier.focusRequester(returnRequester)
                        index == 0 -> Modifier.landingFocus(landing)
                        index % 5 == 0 -> Modifier.focusRequester(rowEntry)
                        else -> Modifier
                    },
                ),
            onLongClick = rememberPlaybackLongClick(card),
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
        Text(
            displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (episode.isNotBlank()) {
            Text(
                episode,
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
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
