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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.XuperCatalog
import androidx.compose.ui.text.style.TextOverflow
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/** "Mi lista" en la TV: Favoritos y Ver más tarde, con el mando. */
@Composable
fun TvLibraryScreen(catalog: XuperCatalog, returnFilmId: String? = null, onOpenFilm: (Film) -> Unit) {
    LaunchedEffect(Unit) { catalog.refreshPersonal() }
    var favoritesTab by rememberSaveable { mutableStateOf(true) }
    val landing = rememberLandingFocus(favoritesTab)
    // Al volver de una ficha: primero la tarjeta exacta; si no está compuesta, la primera la respalda.
    val returnRequester = remember { FocusRequester() }
    LaunchedEffect(favoritesTab, returnFilmId) {
        if (returnFilmId == null) return@LaunchedEffect
        repeat(8) {
            runCatching { returnRequester.requestFocus() }
            kotlinx.coroutines.delay(80)
        }
    }
    val rawFilms = catalog.personalRows
        .firstOrNull { it.id == if (favoritesTab) "favorites" else "watch-later" }
        ?.films.orEmpty()
    val films = remember(rawFilms) { rawFilms.distinctBy { it.id } }
    // Sin películas no hay tarjeta a la que aterrizar: el chip seleccionado es el
    // destino del foco, o el mando se queda sin nada enfocado en esta pantalla.
    val emptyLanding = if (films.isEmpty()) Modifier.landingFocus(landing) else Modifier
    Column(Modifier.fillMaxSize().background(VisionBlack).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mi lista", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.width(20.dp))
            TvChoiceChip("★ Favoritos", selected = favoritesTab, onClick = { favoritesTab = true },
                modifier = if (favoritesTab) emptyLanding else Modifier)
            Spacer(Modifier.width(10.dp))
            TvChoiceChip("Ver más tarde", selected = !favoritesTab, onClick = { favoritesTab = false },
                modifier = if (!favoritesTab) emptyLanding else Modifier)
        }
        Spacer(Modifier.padding(top = 10.dp))
        if (films.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (favoritesTab) "Todavía no tienes favoritos" else "Nada para ver más tarde",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        "En la ficha de una película elige «☆ Favorito» o «Ver más tarde».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            return@Column
        }
        LazyVerticalGrid(
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
