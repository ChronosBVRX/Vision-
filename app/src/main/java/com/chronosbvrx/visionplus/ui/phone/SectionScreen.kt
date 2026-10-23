package com.chronosbvrx.visionplus.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.SectionPaging
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
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.launch

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
                        categoryId = categoryRow?.id ?: "",
                        initialTotal = categoryRow?.total ?: films.size,
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
                    text = "Ver más",
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
            // «Ver más» al final de la hilera: el género no se siente cortado en 15.
            if (row.total > limitedFilms.size || row.total <= 0) {
                item(key = "more_${row.id}") {
                    SeeMoreCard(
                        count = row.total.coerceAtLeast(row.films.size),
                        onClick = onSeeAll,
                    )
                }
            }
        }
    }
}

/** Tarjeta de cierre de una hilera: dice cuántos títulos hay y abre la cuadrícula. */
@Composable
private fun SeeMoreCard(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(132.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(VisionSurface)
                .border(1.dp, VisionBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    VisionIcons.SeeAll,
                    contentDescription = null,
                    tint = VisionBlueBright,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    "Ver más",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (count > 0) {
                    Text(
                        "$count títulos",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTextSecondary,
                    )
                }
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
    categoryId: String,
    initialTotal: Int,
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lowEnd = remember { DevicePerformance.isLowEndDevice(context) }
    val batch = remember { SectionPaging.batch(lowEnd) }
    val cap = remember { SectionPaging.cap(lowEnd) }
    // La sección trae el primer tope (40); «Ver más» pide páginas al servidor y
    // nunca pasa del techo del equipo (180 en TV de 2 GB, 480 en equipos grandes).
    var extra by remember(categoryName) { mutableStateOf<List<Film>>(emptyList()) }
    var total by remember(categoryName) {
        mutableIntStateOf(initialTotal.coerceAtLeast(films.size))
    }
    var loadingMore by remember(categoryName) { mutableStateOf(false) }
    var exhausted by remember(categoryName) { mutableStateOf(false) }
    val allFilms = remember(films, extra) { (films + extra).distinctBy { it.id } }

    var batchLimit by rememberSaveable(categoryName) { mutableIntStateOf(GRID_BATCH_SIZE) }
    val displayedFilms = remember(allFilms, batchLimit) { allFilms.take(batchLimit) }

    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember(allFilms.size, displayedFilms.size) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (displayedFilms.size - 6) && displayedFilms.size < allFilms.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            batchLimit = (batchLimit + GRID_BATCH_SIZE).coerceAtMost(allFilms.size)
        }
    }

    fun loadMore() {
        if (loadingMore) return
        // Primero se revela lo que ya está en memoria; solo al agotarlo se pide red.
        if (batchLimit < allFilms.size) {
            batchLimit = (batchLimit + batch).coerceAtMost(allFilms.size)
            return
        }
        if (exhausted || categoryId.isBlank() ||
            !SectionPaging.canLoadMore(allFilms.size, total, cap)
        ) {
            return
        }
        val page = SectionPaging.nextPage(allFilms.size, batch)
        val loadedBefore = allFilms.size
        loadingMore = true
        scope.launch {
            val result = RemoteIndexSource.categoryPage(context, categoryId, page, batch)
            if (result.films.isNotEmpty()) {
                extra = (extra + result.films).distinctBy { it.id }
            }
            if (result.total > 0) total = result.total
            if (result.films.isEmpty()) exhausted = true
            val loadedAfter = (films + extra).distinctBy { it.id }.size
            if (loadedAfter >= cap || (result.films.isEmpty() && loadedAfter <= loadedBefore)) {
                exhausted = true
            }
            batchLimit = (batchLimit + batch).coerceAtMost(loadedAfter)
            loadingMore = false
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
                text = " (${maxOf(total, allFilms.size)})",
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Mostrando ${displayedFilms.size} de ${maxOf(total, allFilms.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = VisionTextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        loadingMore -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = VisionBlue,
                            strokeWidth = 2.dp,
                        )
                        batchLimit < allFilms.size ||
                            (categoryId.isNotBlank() && !exhausted &&
                                SectionPaging.canLoadMore(allFilms.size, total, cap)) ->
                            Button(onClick = ::loadMore) { Text("Ver más") }
                        allFilms.size >= cap && total > allFilms.size -> Text(
                            "Límite de este equipo · ${allFilms.size} de $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = VisionTextSecondary,
                        )
                        else -> Text(
                            "No hay más títulos",
                            style = MaterialTheme.typography.labelMedium,
                            color = VisionTextSecondary,
                        )
                    }
                }
            }
        }
    }
}
