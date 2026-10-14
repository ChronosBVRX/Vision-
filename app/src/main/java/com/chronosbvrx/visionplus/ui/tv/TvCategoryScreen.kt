package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import java.text.Normalizer

private const val CHIP_ALL = "Todo / Destacados"
private const val CAROUSEL_LIMIT = 15
private const val GRID_BATCH_SIZE = 24

/**
 * Vista de sección para Android TV (Películas, Series, Novelas, Anime, Fútbol):
 * 1. Selector deslizable de categorías (TvCategoryPill) con "Todo / Destacados" + géneros.
 * 2. Modo predeterminado ("Todo / Destacados"):
 *    - Destacado Hero inmersivo arriba.
 *    - Carruseles de secciones curadas limitadas a 15 elementos (LIMIT 15).
 *    - Componente TvPosterCard (ratio 2:3) con badges de rating (★ 8.2), estado (NUEVO/TOP 10),
 *      y número gigante de posición para Top 10 (1..10).
 *    - Encabezados con botón interactivo "Ver todo →".
 * 3. Modo categoría seleccionada:
 *    - Oculta los carruseles y activa cuadrícula de 5 columnas (LazyVerticalGrid).
 *    - Paginación infinita en lotes de 24 para optimización en TV de bajos recursos.
 *    - Botón "← Volver a Todo" y BackHandler en mando de TV para regresar a destacados.
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
    val landing = rememberLandingFocus(section ?: key)
    val returnRequester = remember { FocusRequester() }
    // Primera tarjeta visible de cada fila compuesta: rescate del regreso cuando la tarjeta
    // exacta no existe o el héroe ya no está compuesto (el usuario venía con scroll abajo).
    val rowEntry = remember { FocusRequester() }
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

    var selectedCategory by rememberSaveable(section ?: key) { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    val films = remember(rows, key, sectionRows) {
        val raw = if (section != null) {
            (sectionRows ?: emptyList()).flatMap { it.films }.distinctBy { it.id }
        } else {
            val byId = rows.firstOrNull { it.id.equals(key, ignoreCase = true) }
            val byTitle = byId ?: rows.firstOrNull { fold(it.title).contains(fold(title)) }
            byTitle?.films?.takeIf { it.isNotEmpty() } ?: searchFallback(rows, title)
        }
        // Reordenado por impacto (rating con confianza + popularidad + recencia):
        // robusto ante caché vieja del servidor ordenada solo por score/año.
        Popularity.reorderRow(raw)
    }

    // Al volver de la ficha: la tarjeta exacta. El contenido de la sección puede llegar
    // después (red/caché fría), así que el intento se re-arma con cada contenido nuevo.
    rememberReturnFocus(
        filmId = returnFilmId,
        requester = returnRequester,
        landing = landing,
        contentKey = films.size,
        entryRequester = rowEntry,
    )
    val featured = remember(films) { films.maxByOrNull { Popularity.rankScore(it) } }
    // En modo sección (películas/series/…) estas dos solo se usan en la vista genérica:
    // no se calculan sobre miles de films si no van a pintarse.
    val topRated = remember(films, section) {
        if (section == null) {
            Popularity.reorderRow(films.filter { it.score > 0 || it.popularity > 0 || it.voteCount > 0 })
        } else {
            emptyList()
        }
    }
    val novelties = remember(films, section) {
        if (section == null) Popularity.realPremieres(films, limit = 100) else emptyList()
    }
    val forYou = remember(films) {
        val played = UserLibrary.played(context).map { it.id }.toSet()
        val candidates = films.filter { it.id !in played && (it.score > 0 || it.popularity > 0) }
        Popularity.reorderRow(candidates).ifEmpty { films }
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
    val categoryChips = remember(loaded, films, section) {
        if (section != null && loaded != null) {
            val genres = loaded.map { it.title.trim() }.filter { it.isNotBlank() }.distinct()
            listOf(CHIP_ALL) + genres
        } else {
            listOf(CHIP_ALL, "Mejor valoradas", "Novedades", "Recomendado para ti")
        }
    }

    val activeCategory = selectedCategory
    if (activeCategory == null) {
        // MODO 1: FEED CURADO POR MÓDULOS ("Todo / Destacados")
        LazyColumn(Modifier.fillMaxSize().background(VisionBlack)) {
            if (featured != null) {
                item(key = "hero") { Hero(featured, landing, onOpenFilm) }
            } else {
                item(key = "title") {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(start = 48.dp, top = 28.dp))
                }
            }

            // Selector horizontal de categorías (Chips / Pills)
            item(key = "chips") {
                CategoryChipsRow(
                    chips = categoryChips,
                    selected = null,
                    onSelect = { chip ->
                        selectedCategory = if (chip == CHIP_ALL) null else chip
                    },
                )
            }

            item(key = "gap") { Spacer(Modifier.height(10.dp)) }

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
                            SectionRow(
                                label = premiereRow.title,
                                films = premiereRow.films,
                                isTopRow = true,
                                onOpenFilm = onOpenFilm,
                                onSeeAll = { selectedCategory = premiereRow.title },
                                returnRequester = returnRequester,
                                returnFilmId = returnFilmId,
                                entryRequester = rowEntry,
                            )
                        }
                    }
                    item(key = "foryou") {
                        SectionRow(
                            label = "Recomendado para ti",
                            films = forYou,
                            isTopRow = false,
                            onOpenFilm = onOpenFilm,
                            onSeeAll = { selectedCategory = "Recomendado para ti" },
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                        )
                    }
                    itemsIndexed(otherRows, key = { _, row -> row.id }) { index, row ->
                        val isTop = premiereRow == null && index == 0
                        SectionRow(
                            label = row.title,
                            films = row.films,
                            isTopRow = isTop,
                            onOpenFilm = onOpenFilm,
                            onSeeAll = { selectedCategory = row.title },
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                        )
                    }
                }
            } else {
                item(key = "top") {
                    SectionRow(
                        label = "Mejor valoradas",
                        films = topRated,
                        isTopRow = true,
                        onOpenFilm = onOpenFilm,
                        onSeeAll = { selectedCategory = "Mejor valoradas" },
                        returnRequester = returnRequester,
                        returnFilmId = returnFilmId,
                        entryRequester = rowEntry,
                    )
                }
                item(key = "new") {
                    SectionRow(
                        label = "Novedades",
                        films = novelties,
                        isTopRow = false,
                        onOpenFilm = onOpenFilm,
                        onSeeAll = { selectedCategory = "Novedades" },
                        returnRequester = returnRequester,
                        returnFilmId = returnFilmId,
                        entryRequester = rowEntry,
                    )
                }
                item(key = "foryou") {
                    SectionRow(
                        label = "Recomendado para ti",
                        films = forYou,
                        isTopRow = false,
                        onOpenFilm = onOpenFilm,
                        onSeeAll = { selectedCategory = "Recomendado para ti" },
                        returnRequester = returnRequester,
                        returnFilmId = returnFilmId,
                        entryRequester = rowEntry,
                    )
                }
                item(key = "all") {
                    SectionRow(
                        label = "Todo · $title",
                        films = films,
                        isTopRow = false,
                        onOpenFilm = onOpenFilm,
                        onSeeAll = { selectedCategory = title },
                        returnRequester = returnRequester,
                        returnFilmId = returnFilmId,
                        entryRequester = rowEntry,
                    )
                }
            }
            item(key = "end") { Spacer(Modifier.height(36.dp)) }
        }
    } else {
        // MODO 2: VISTA DE CATÁLOGO COMPLETO EN CUADRÍCULA (GRID)
        val categoryFilms = remember(activeCategory, loaded, films, topRated, novelties, forYou) {
            val raw = when {
                loaded != null -> {
                    val matchingRow = loaded.firstOrNull { it.title.equals(activeCategory, ignoreCase = true) }
                    matchingRow?.films ?: loaded.flatMap { it.films }
                        .filter {
                            it.synopsis.contains(activeCategory, ignoreCase = true) ||
                                it.title.contains(activeCategory, ignoreCase = true)
                        }
                        .distinctBy { it.id }
                }
                activeCategory == "Mejor valoradas" -> topRated
                activeCategory == "Novedades" -> novelties
                activeCategory == "Recomendado para ti" -> forYou
                else -> films
            }
            Popularity.reorderRow(raw)
        }

        TvCategoryGrid(
            categoryName = activeCategory,
            categoryChips = categoryChips,
            films = categoryFilms,
            onSelectChip = { chip ->
                selectedCategory = if (chip == CHIP_ALL) null else chip
            },
            onBackToAll = { selectedCategory = null },
            onOpenFilm = onOpenFilm,
            returnRequester = returnRequester,
            returnFilmId = returnFilmId,
            entryRequester = rowEntry,
        )
    }
}

@Composable
private fun CategoryChipsRow(
    chips: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(chips.size, key = { chips[it] }) { idx ->
            val chip = chips[idx]
            val isSelected = if (selected == null) chip == CHIP_ALL else chip.equals(selected, ignoreCase = true)
            TvCategoryPill(
                label = chip,
                selected = isSelected,
                onClick = { onSelect(chip) },
            )
        }
    }
}

@Composable
private fun TvCategoryGrid(
    categoryName: String,
    categoryChips: List<String>,
    films: List<Film>,
    onSelectChip: (String) -> Unit,
    onBackToAll: () -> Unit,
    onOpenFilm: (Film) -> Unit,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    entryRequester: FocusRequester? = null,
) {
    var visibleCount by remember(categoryName) { mutableIntStateOf(GRID_BATCH_SIZE) }
    val displayedFilms = remember(films, visibleCount) { films.take(visibleCount) }
    val gridState = rememberLazyGridState()

    // Al volver de una ficha, la cuadrícula se desplaza sola hasta la tarjeta abierta.
    val returnIndex = remember(displayedFilms, returnFilmId) {
        if (returnFilmId == null) -1 else displayedFilms.indexOfFirst { it.id == returnFilmId }
    }
    LaunchedEffect(returnIndex, displayedFilms) { gridState.revealReturnIndex(returnIndex) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= displayedFilms.size - 8 && displayedFilms.size < films.size
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = (visibleCount + GRID_BATCH_SIZE).coerceAtMost(films.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionBlack),
    ) {
        // Encabezado Secundario de Categoría
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 12.dp),
        ) {
            TvCompactAction(
                label = "← Volver a Todo",
                onClick = onBackToAll,
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = categoryName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "(${films.size} títulos)",
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
            )
        }

        // Selector horizontal de chips en vista de cuadrícula
        CategoryChipsRow(
            chips = categoryChips,
            selected = categoryName,
            onSelect = onSelectChip,
        )

        Spacer(Modifier.height(12.dp))

        if (displayedFilms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No se encontraron títulos en esta categoría",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(displayedFilms, key = { _, film -> "grid-${film.id}" }) { index, film ->
                    val isReturn = returnRequester != null && film.id == returnFilmId
                    Column(
                        modifier = Modifier.width(140.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        TvPosterCard(
                            title = film.title,
                            imageUrl = film.posterUrl,
                            score = film.score,
                            badge = if (film.year >= 2025) "NUEVO" else null,
                            modifier = Modifier.fillMaxWidth().then(
                                when {
                                    isReturn -> Modifier.focusRequester(returnRequester)
                                    index % 5 == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                                    else -> Modifier
                                },
                            ),
                            onClick = { onOpenFilm(film) },
                        )
                        Text(
                            text = film.title,
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
}

@Composable
private fun Hero(film: Film, landing: LandingFocus?, onOpenFilm: (Film) -> Unit) {
    val context = LocalContext.current
    val screenPx = com.chronosbvrx.visionplus.ui.rememberScreenPx()
    val heroPx = with(androidx.compose.ui.platform.LocalDensity.current) { 320.dp.roundToPx() }
    var inLater by remember(film.id) { mutableStateOf(UserLibrary.isWatchLater(context, film.id)) }
    var inFavorites by remember(film.id) { mutableStateOf(UserLibrary.isFavorite(context, film.id)) }
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(film.posterUrl)
                .crossfade(0)
                .size(screenPx.width, heroPx)
                .build(),
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
    isTopRow: Boolean = false,
    onOpenFilm: (Film) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    entryRequester: FocusRequester? = null,
) {
    val safeFilms = remember(films) { Popularity.reorderRow(films.distinctBy { it.id }).take(CAROUSEL_LIMIT) }
    if (safeFilms.isEmpty()) return

    // Al volver de una ficha, la fila se desplaza sola hasta la tarjeta abierta: el foco solo
    // puede caer en algo ya dibujado, así no depende de que el scroll se haya restaurado.
    val rowState = rememberLazyListState()
    val returnIndex = remember(safeFilms, returnFilmId) {
        if (returnFilmId == null) -1 else safeFilms.indexOfFirst { it.id == returnFilmId }
    }
    LaunchedEffect(returnIndex, safeFilms) { rowState.revealReturnIndex(returnIndex) }

    // Encabezado de Sección: Título a la izquierda + "Ver todo →" a la derecha
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            TvSeeAllButton(onClick = onSeeAll)
        }
    }

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(safeFilms, key = { _, film -> "${label}-${film.id}" }) { idx, film ->
            val rank = if (isTopRow && idx in 0..9) idx + 1 else null
            val isReturn = returnRequester != null && film.id == returnFilmId
            Column(Modifier.width(140.dp)) {
                TvPosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    rank = rank,
                    modifier = when {
                        isReturn -> Modifier.focusRequester(returnRequester)
                        idx == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                        else -> Modifier
                    },
                    onClick = { onOpenFilm(film) },
                )
                Text(
                    text = film.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
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
