package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.isLandscapeTablet

import androidx.compose.runtime.collectAsState
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry

enum class LibraryTab { FAVORITES, WATCH_LATER, DOWNLOADS }

/** "Mi lista" en el teléfono: lo guardado en «Favoritos», «Ver más tarde» y «Descargas», en cuadrícula. */
@Composable
fun LibraryScreen(catalog: XuperCatalog, contentPadding: PaddingValues, onOpenFilm: (Film) -> Unit) {
    LaunchedEffect(Unit) { catalog.refreshPersonal() }
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.FAVORITES) }
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()

    val films = when (selectedTab) {
        LibraryTab.FAVORITES -> catalog.personalRows.firstOrNull { it.id == "favorites" }?.films.orEmpty()
        LibraryTab.WATCH_LATER -> catalog.personalRows.firstOrNull { it.id == "watch-later" }?.films.orEmpty()
        LibraryTab.DOWNLOADS -> downloads.filter { it.state == DownloadState.COMPLETED }.map { it.toFilm() }
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Text("Mi lista", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedTab == LibraryTab.FAVORITES, onClick = { selectedTab = LibraryTab.FAVORITES }, label = { Text("★ Favoritos") })
            FilterChip(selected = selectedTab == LibraryTab.WATCH_LATER, onClick = { selectedTab = LibraryTab.WATCH_LATER }, label = { Text("Ver más tarde") })
            FilterChip(selected = selectedTab == LibraryTab.DOWNLOADS, onClick = { selectedTab = LibraryTab.DOWNLOADS }, label = { Text("⬇ Descargas") })
        }
        if (films.isEmpty()) {
            EmptyState(
                title = when (selectedTab) {
                    LibraryTab.FAVORITES -> "Todavía no tienes favoritos"
                    LibraryTab.WATCH_LATER -> "Nada para ver más tarde"
                    LibraryTab.DOWNLOADS -> "No tienes descargas aún"
                },
                subtitle = when (selectedTab) {
                    LibraryTab.FAVORITES -> "En la ficha de una película toca «☆ Favorito» y aparece aquí."
                    LibraryTab.WATCH_LATER -> "En la ficha de una película toca «+ Ver más tarde» y aparece aquí."
                    LibraryTab.DOWNLOADS -> "Toca «Descargar» en la ficha de una película para verla sin internet."
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
