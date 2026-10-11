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

/** "Mi lista" en el teléfono: lo guardado en «Favoritos» y «Ver más tarde», en cuadrícula. */
@Composable
fun LibraryScreen(catalog: XuperCatalog, contentPadding: PaddingValues, onOpenFilm: (Film) -> Unit) {
    LaunchedEffect(Unit) { catalog.refreshPersonal() }
    var favoritesTab by rememberSaveable { mutableStateOf(true) }
    val films = catalog.personalRows
        .firstOrNull { it.id == if (favoritesTab) "favorites" else "watch-later" }
        ?.films.orEmpty()
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Text("Mi lista", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = favoritesTab, onClick = { favoritesTab = true }, label = { Text("★ Favoritos") })
            FilterChip(selected = !favoritesTab, onClick = { favoritesTab = false }, label = { Text("Ver más tarde") })
        }
        if (films.isEmpty()) {
            EmptyState(
                title = if (favoritesTab) "Todavía no tienes favoritos" else "Nada para ver más tarde",
                subtitle = "En la ficha de una película toca «☆ Favorito» o «+ Ver más tarde» y aparece aquí.",
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
                PosterCard(title = film.title, imageUrl = film.posterUrl, meta = film.metaLine(), onClick = { onOpenFilm(film) })
            }
        }
    }
}
