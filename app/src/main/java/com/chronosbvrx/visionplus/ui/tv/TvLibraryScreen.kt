package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
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
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «Mi lista» en la TV: Favoritos, Ver más tarde, Historial y Descargas, con el mando. */
@Composable
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
    val historyScope = rememberCoroutineScope()

    fun reloadHistory() {
        historyScope.launch(Dispatchers.Default) {
            val films = allFilms(catalog.personalRows + catalog.rows + catalog.indexRows)
            val cards = PlaybackHistory.historyCards(context, films)
            withContext(Dispatchers.Main) { historyCards = cards }
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
            TvChoiceChip("Historial", selected = activeTab == 3, onClick = { activeTab = 3 },
                icon = VisionIcons.ContinueWatching,
                modifier = if (activeTab == 3) emptyLanding else Modifier)
            Spacer(Modifier.width(10.dp))
            TvChoiceChip("Descargas (USB)", selected = activeTab == 2, onClick = { activeTab = 2 },
                icon = VisionIcons.Usb,
                modifier = if (activeTab == 2) emptyLanding else Modifier)
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
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { }
                itemsIndexed(historyCards, key = { _, card -> card.entry.key }) { index, card ->
                    TvHistoryCard(
                        card = card,
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
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { }
            itemsIndexed(films, key = { _, film -> film.id }) { index, film ->
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
}

/** Tarjeta del historial en TV: póster, avance y «Visto»/«Continuar en …». */
@Composable
private fun TvHistoryCard(
    card: ResumeCard,
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
    val episode = if (card.entry.title != displayTitle) {
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
            onClick = {
                if (card.completed) {
                    if (film != null) onOpenFilm(film)
                    else onResume(card.copy(entry = card.entry.copy(positionMs = 0L)))
                } else {
                    onResume(card)
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
            if (card.completed) "Visto" else "Continuar en " + formatResume(card.entry.positionMs),
            style = MaterialTheme.typography.bodySmall,
            color = VisionTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
