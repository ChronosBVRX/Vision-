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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import java.text.Normalizer

/**
 * Una sección del riel (Películas, Series, Novelas, Anime, Fútbol) con la misma idea del inicio:
 * un destacado arriba (la mejor puntuación del portal entre lo que sí está en el catálogo) y
 * debajo hileras — Mejor valoradas, Novedades, Recomendado para ti y Todo — con el nombre a la
 * vista bajo cada tarjeta. [key] es el id de la categoría cuando se conoce; si no, se busca por
 * título y, de último, entre los títulos de todas las hileras (Fútbol vive repartido).
 */
@Composable
fun TvCategoryScreen(
    rows: List<CatalogRow>,
    key: String,
    title: String,
    section: String? = null,
    returnFilmId: String? = null,
    onOpenFilm: (Film) -> Unit,
) {
    val context = LocalContext.current
    // El foco de entrada siempre vive en el héroe (nunca deja la pantalla sin foco); al volver
    // de una ficha se intenta además la tarjeta exacta, sin arriesgar la navegación.
    val landing = rememberLandingFocus(section ?: key)
    val returnRequester = remember { FocusRequester() }
    LaunchedEffect(section ?: key, returnFilmId) {
        if (returnFilmId == null) return@LaunchedEffect
        repeat(8) {
            runCatching { returnRequester.requestFocus() }
            kotlinx.coroutines.delay(80)
        }
    }
    var sectionRows by remember(section) {
        mutableStateOf(if (section != null) RemoteIndexSource.cachedSection(context, section) else null)
    }
    LaunchedEffect(section) {
        if (section != null) {
            val fresh = RemoteIndexSource.section(context, section)
            if (fresh.isNotEmpty() || sectionRows == null) {
                sectionRows = fresh
            }
        }
    }
    val films = remember(rows, key, sectionRows) {
        if (section != null) {
            (sectionRows ?: emptyList()).flatMap { it.films }.distinctBy { it.id }
        } else {
            val byId = rows.firstOrNull { it.id.equals(key, ignoreCase = true) }
            val byTitle = byId ?: rows.firstOrNull { fold(it.title).contains(fold(title)) }
            byTitle?.films?.takeIf { it.isNotEmpty() } ?: searchFallback(rows, title)
        }
    }
    val featured = remember(films) { films.maxByOrNull { it.score * 100 + it.year.coerceAtLeast(0) } }
    val topRated = remember(films) { films.filter { it.score > 0 }.sortedByDescending { it.score }.take(30) }
    val novelties = remember(films) { films.filter { it.year > 0 }.sortedByDescending { it.year }.take(30) }
    val forYou = remember(films) {
        val played = UserLibrary.played(context).map { it.id }.toSet()
        films.filter { it.id !in played && it.score > 0 }
            .sortedByDescending { it.score }
            .take(30)
            .ifEmpty { films.take(30) }
    }

    if (section != null && sectionRows == null) {
        Column(Modifier.fillMaxSize().background(VisionBlack)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(start = 48.dp, top = 28.dp))
            Text(
                "Cargando sección…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 48.dp, top = 12.dp),
            )
        }
        return
    }
    val loaded = sectionRows
    // LazyColumn: la página compone solo sus hileras visibles. Con Column+scroll vertical
    // todas las hileras (con sus tarjetas e imágenes) se componían al abrir y la TV tardaba.
    LazyColumn(Modifier.fillMaxSize().background(VisionBlack)) {
        if (featured != null) {
            item(key = "hero") { Hero(featured, landing, onOpenFilm) }
        } else {
            item(key = "title") {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(start = 48.dp, top = 28.dp))
            }
        }
        item(key = "gap") { Spacer(Modifier.height(18.dp)) }
        if (section != null && loaded != null) {
            if (loaded.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Sin contenido todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 48.dp, top = 12.dp),
                    )
                }
            } else {
                val premiereRow = loaded.firstOrNull { it.id.endsWith("-estrenos") }
                val otherRows = loaded.filterNot { it.id.endsWith("-estrenos") }

                if (premiereRow != null) {
                    item(key = premiereRow.id) {
                        if (premiereRow.films.any { it.id == returnFilmId }) {
                            SectionRow(premiereRow.title, premiereRow.films, onOpenFilm, returnRequester, returnFilmId)
                        } else {
                            SectionRow(premiereRow.title, premiereRow.films, onOpenFilm)
                        }
                    }
                }
                item(key = "foryou") { SectionRow("Recomendado para ti", forYou, onOpenFilm) }
                itemsIndexed(otherRows, key = { _, row -> row.id }) { _, row ->
                    if (row.films.any { it.id == returnFilmId }) SectionRow(row.title, row.films, onOpenFilm, returnRequester, returnFilmId)
                    else SectionRow(row.title, row.films, onOpenFilm)
                }
            }
        } else {
            item(key = "top") { SectionRow("Mejor valoradas", topRated, onOpenFilm) }
            item(key = "new") { SectionRow("Novedades", novelties, onOpenFilm) }
            item(key = "foryou") { SectionRow("Recomendado para ti", forYou, onOpenFilm) }
            item(key = "all") { SectionRow("Todo · $title", films, onOpenFilm, returnRequester, returnFilmId) }
        }
        item(key = "end") { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun Hero(film: Film, landing: LandingFocus?, onOpenFilm: (Film) -> Unit) {
    val context = LocalContext.current
    var inLater by remember(film.id) { mutableStateOf(UserLibrary.isWatchLater(context, film.id)) }
    var inFavorites by remember(film.id) { mutableStateOf(UserLibrary.isFavorite(context, film.id)) }
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        AsyncImage(
            model = film.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000), VisionBlack)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 18.dp)) {
            Text(
                film.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            val meta = buildString {
                if (film.year > 0) append(film.year)
                if (film.score > 0) {
                    if (isNotEmpty()) append("  ·  ")
                    append("★ %.1f".format(film.score))
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                TvCompactAction(
                    label = "▶  Reproducir",
                    modifier = if (landing != null) Modifier.landingFocus(landing) else Modifier,
                    onClick = { onOpenFilm(film) },
                )
                TvCompactAction(label = if (inLater) "✓ Ver más tarde" else "Ver más tarde") {
                    UserLibrary.toggleWatchLater(context, film)
                    inLater = !inLater
                }
                TvCompactAction(label = if (inFavorites) "★ Favorito" else "☆ Favorito") {
                    UserLibrary.toggleFavorite(context, film)
                    inFavorites = !inFavorites
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    label: String,
    films: List<Film>,
    onOpenFilm: (Film) -> Unit,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
) {
    val safeFilms = remember(films) { films.distinctBy { it.id } }
    if (safeFilms.isEmpty()) return
    TvRowLabel(label, 26.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(safeFilms, key = { it.id }) { film ->
            Column(Modifier.width(196.dp)) {
                TvLandscapeCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    cardHeight = 110.dp,
                    modifier = if (returnRequester != null && film.id == returnFilmId) Modifier.focusRequester(returnRequester) else Modifier,
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
    Spacer(Modifier.height(14.dp))
}

private val DIACRITICS = Regex("\\p{Mn}+")

private fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

/** Títulos del índice que suenan a la sección (Fútbol: deportes, ESPN, TUDN…). */
private fun searchFallback(rows: List<CatalogRow>, title: String): List<Film> {
    val words = when (fold(title)) {
        "futbol" -> listOf("futbol", "deporte", "sport", "espn", "tudn", "gol")
        else -> listOf(fold(title))
    }
    return rows.flatMap { it.films }
        .distinctBy { it.id }
        .filter { film -> words.any { word -> fold(film.title).contains(word) } }
}
