package com.chronosbvrx.visionplus.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.components.CategoryPill
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.GhostRow
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.isLandscapeTablet
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

private const val CHIP_ALL = "Todo / Destacados"
private const val CAROUSEL_LIMIT = 15
private const val GRID_BATCH_SIZE = 24

/**
 * Vista principal de sección (Películas, Series, Novelas, Anime):
 * 1. Cabecera fija con selector de categorías deslizable horizontal (chips).
 * 2. Modo predeterminado ("Todo / Destacados"): feed curado compuesto por carruseles
 *    horizontales acotados a 15 elementos, con encabezado ("Ver todo →"), badges y Top 10.
 * 3. Modo categoría seleccionada: cuadrícula paginada en lotes de 24 con cursor virtual.
 * 4. Optimizado para bajo consumo de memoria y 60 FPS en dispositivos limitados.
 */
@Composable
fun SectionScreen(
    name: String,
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    val context = LocalContext.current
    var rows by remember(name) {
        mutableStateOf(RemoteIndexSource.cachedSection(context, name))
    }
    LaunchedEffect(name) {
        val fresh = RemoteIndexSource.section(context, name)
        if (fresh.isNotEmpty() || rows == null) {
            rows = fresh
        }
    }

    var selectedCategory by rememberSaveable(name) { mutableStateOf<String?>(null) }

    // Al estar filtrando por categoría, 'Atrás' regresa primero a "Todo / Destacados".
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    val loaded = rows
    val isTablet = isLandscapeTablet()
    val gridColumns = if (isTablet) 5 else 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .statusBarsPadding(),
    ) {
        // 1. Barra superior fija (Header)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = {
                if (selectedCategory != null) {
                    selectedCategory = null
                } else {
                    onBack()
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            if (selectedCategory != null) {
                TextButton(onClick = { selectedCategory = null }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Limpiar filtro",
                        tint = VisionBlue,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("Limpiar", color = VisionBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when {
            loaded == null -> {
                GhostRow()
                GhostRow()
                GhostRow()
            }
            loaded.isEmpty() -> {
                EmptyState(
                    title = "Sin contenido todavía",
                    subtitle = "Revisa tu conexión e intenta de nuevo.",
                )
            }
            else -> {
                // Selector horizontal de categorías (Chips / Pills)
                val categoryChips = remember(loaded) {
                    val genres = loaded.map { it.title.trim() }.filter { it.isNotBlank() }.distinct()
                    listOf(CHIP_ALL) + genres
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categoryChips.size, key = { categoryChips[it] }) { idx ->
                        val chip = categoryChips[idx]
                        val isSelected = (selectedCategory == null && chip == CHIP_ALL) ||
                            (selectedCategory == chip)
                        CategoryPill(
                            label = chip,
                            selected = isSelected,
                            onClick = {
                                selectedCategory = if (chip == CHIP_ALL) null else chip
                            },
                        )
                    }
                }

                val activeCategory = selectedCategory
                if (activeCategory == null) {
                    // Modo 1: Feed Curado por Módulos ("Todo / Destacados")
                    LazyColumn(
                        contentPadding = PaddingValues(
                            bottom = contentPadding.calculateBottomPadding() + 24.dp,
                            top = 4.dp,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(loaded, key = { _, row -> row.id }) { index, row ->
                            // Reordenado por impacto en cliente: robusto ante caché vieja del servidor.
                            val ranked = remember(row.films) { Popularity.reorderRow(row.films) }
                            CuratedSectionModule(
                                row = row.copy(films = ranked),
                                rowIndex = index,
                                onOpenFilm = onOpenFilm,
                                onSeeAll = { selectedCategory = row.title },
                            )
                        }
                    }
                } else {
                    // Modo 2: Vista de Catálogo Completo en Cuadrícula (Grid paginada)
                    val categoryRow = remember(loaded, activeCategory) {
                        loaded.firstOrNull { it.title.equals(activeCategory, ignoreCase = true) }
                    }
                    val films = remember(categoryRow, loaded) {
                        val raw = categoryRow?.films ?: loaded.flatMap { it.films }
                            .filter { it.synopsis.contains(activeCategory, ignoreCase = true) }
                            .distinctBy { it.id }
                        Popularity.reorderRow(raw)
                    }

                    CategoryGridFeed(
                        categoryName = activeCategory,
                        films = films,
                        columns = gridColumns,
                        contentPadding = contentPadding,
                        onClearFilter = { selectedCategory = null },
                        onOpenFilm = onOpenFilm,
                    )
                }
            }
        }
    }
}

/**
 * Módulo de sección curada con encabezado ("Ver todo →") y carrusel horizontal acotado
 * a 15 elementos para no saturar memoria en dispositivos de bajos recursos.
 */
@Composable
private fun CuratedSectionModule(
    row: CatalogRow,
    rowIndex: Int,
    onOpenFilm: (Film) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (row.films.isEmpty()) return

    val isTopRow = row.title.contains("top 10", ignoreCase = true) ||
        row.title.contains("más vistas", ignoreCase = true) ||
        (rowIndex == 0 && (row.title.contains("tendencia", ignoreCase = true) || row.title.contains("destacad", ignoreCase = true)))

    Column(Modifier.padding(top = 16.dp)) {
        // Encabezado de Sección: Título a la izquierda, enlace "Ver todo →" a la derecha
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clickable(onClick = onSeeAll)
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = "Ver todo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = VisionBlue,
                )
                Icon(
                    VisionIcons.SeeAll,
                    contentDescription = null,
                    tint = VisionBlueBright,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Carrusel Horizontal acotado a máximo 15 elementos (LIMIT 15), ya rankeado por impacto.
        val limitedFilms = remember(row.films) { row.films.take(CAROUSEL_LIMIT) }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(limitedFilms, key = { _, film -> "${row.id}-${film.id}" }) { idx, film ->
                val rank = if (isTopRow && idx in 0..9) idx + 1 else null
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine().ifBlank { null },
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    rank = rank,
                    modifier = Modifier.width(132.dp),
                    onClick = { onOpenFilm(film) },
                )
            }
        }
    }
}

/**
 * Vista de Catálogo Completo: Cuadrícula con paginación infinita en lotes (batching)
 * para mantener 60 FPS y liberar memoria en low-end devices.
 */
@Composable
private fun CategoryGridFeed(
    categoryName: String,
    films: List<Film>,
    columns: Int,
    contentPadding: PaddingValues,
    onClearFilter: () -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    if (films.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = "Sin títulos en \"$categoryName\"",
                subtitle = "Prueba seleccionando otra categoría.",
            )
            TextButton(onClick = onClearFilter) {
                Text("Volver a Todo", color = VisionBlue)
            }
        }
        return
    }

    // Paginación infinita: arranca con GRID_BATCH_SIZE (24) elementos
    var batchLimit by rememberSaveable(categoryName) { mutableIntStateOf(GRID_BATCH_SIZE) }
    val displayedFilms = remember(films, batchLimit) { films.take(batchLimit) }

    val gridState = rememberLazyGridState()

    // Detección reactiva para paginar cuando el scroll se aproxime al final
    val shouldLoadMore by remember(films.size, displayedFilms.size) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (displayedFilms.size - 6) && displayedFilms.size < films.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            batchLimit = (batchLimit + GRID_BATCH_SIZE).coerceAtMost(films.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header secundario: categoría activa + botón rápido para regresar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = " (${films.size})",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.clickable(onClick = onClearFilter),
            ) {
                Icon(
                    VisionIcons.Back,
                    contentDescription = null,
                    tint = VisionBlue,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Volver a Todo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VisionBlue,
                )
            }
        }

        // Cuadrícula paginada
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = displayedFilms,
                key = { it.id },
                contentType = { "film_card" },
            ) { film ->
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine().ifBlank { null },
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    onClick = { onOpenFilm(film) },
                )
            }
        }
    }
}
