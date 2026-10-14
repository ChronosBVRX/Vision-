package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.ui.theme.VisionBlack

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** "Categorías" on the TV: one titled row of cards per category. */
@Composable
fun TvCategoriesScreen(
    rows: List<CatalogRow>,
    returnFilmId: String? = null,
    onOpenFilm: (Film) -> Unit,
) {
    val landing = rememberLandingFocus()
    val returnRequester = remember { FocusRequester() }
    // Primera tarjeta de cada fila compuesta: rescate del regreso si la tarjeta exacta no existe.
    val rowEntry = remember { FocusRequester() }
    val safeRows = remember(rows) { rows.distinctBy { it.id } }
    val firstRowId = safeRows.firstOrNull()?.id

    // Al regresar de la ficha: la tarjeta exacta, re-armado cuando llegan las filas del índice.
    rememberReturnFocus(
        filmId = returnFilmId,
        requester = returnRequester,
        landing = landing,
        contentKey = safeRows.size,
        entryRequester = rowEntry,
    )

    // Las filas del índice pueden llegar después de abrir la pantalla: si el foco quedó huérfano, vuelve.
    LaunchedEffect(rows) {
        delay(150)
        if (!FocusRescue.screenHasFocus && safeRows.isNotEmpty() && returnFilmId == null) landing.request()
    }
    Column(Modifier.fillMaxSize().background(VisionBlack).padding(vertical = 28.dp)) {
        Text("Categorías", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(safeRows, key = { it.id }, contentType = { "category-row" }) { row ->
                val safeFilms = remember(row.films) { row.films.distinctBy { it.id }.take(15) }
                val firstFilmId = safeFilms.firstOrNull()?.id
                TvRowLabel(row.title, 26.dp)
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(safeFilms, key = { _, film -> "${row.id}-${film.id}" }, contentType = { _, _ -> "landscape-card" }) { idx, film ->
                        val isLanding = row.id == firstRowId && film.id == firstFilmId
                        val isReturn = film.id == returnFilmId
                        TvLandscapeCard(
                            title = film.title,
                            imageUrl = film.posterUrl,
                            cardHeight = 110.dp,
                            modifier = when {
                                isReturn -> Modifier.focusRequester(returnRequester)
                                isLanding -> Modifier.landingFocus(landing)
                                idx == 0 -> Modifier.focusRequester(rowEntry)
                                else -> Modifier
                            },
                            onClick = { onOpenFilm(film) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
