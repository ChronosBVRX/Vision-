@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.DevicePerformance
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.KidsCatalog
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.SectionCuration
import com.chronosbvrx.visionplus.data.SectionPaging
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.components.rememberPosterLongClick
import com.chronosbvrx.visionplus.ui.theme.VisionRatingGold
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer

private const val CHIP_ALL = "Todo / Destacados"
private const val CHIP_CATALOG = "Catálogo completo"
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
    // La MISMA película puede vivir en varias filas (Estrenos y «Recomendado para ti»).
    // Se recuerda la fila que tenía el foco al abrir la ficha para que el regreso use
    // un único `FocusRequester` (dos tarjetas con el mismo lo dejaban sin aterrizar).
    var returnRow by rememberSaveable(section ?: key) { mutableStateOf<String?>(null) }
    // Primera tarjeta visible de cada fila compuesta: rescate del regreso cuando la tarjeta
    // exacta no existe o el héroe ya no está compuesto (el usuario venía con scroll abajo).
    val rowEntry = remember { FocusRequester() }
    var sectionRows by remember(section) {
        mutableStateOf(if (section != null) RemoteIndexSource.cachedSection(context, section) else null)
    }
    var kidsServerFilms by remember(key) { mutableStateOf<List<Film>>(emptyList()) }
    var kidsLoading by remember(key) { mutableStateOf(KidsCatalog.isKidsKey(key)) }
    var onAirIds by remember(section, key) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(section, key) {
        if (section != null) {
            val fresh = RemoteIndexSource.section(context, section)
            if (fresh.isNotEmpty() || sectionRows == null) {
                sectionRows = fresh
            }
        }
        // Infantil reproducible: las dos hijas del catálogo XTV (películas y series) se
        // firman bien; `xtv-kids-all` mezcla títulos sin licencia y dejaba fichas sin play.
        if (KidsCatalog.isKidsKey(key) || section == "movies" || section == "series") {
            try {
                val cats = when {
                    key == "kids" -> listOf(KidsCatalog.MOVIES, KidsCatalog.SERIES)
                    key == "kids-series" || section == "series" -> listOf(KidsCatalog.SERIES)
                    else -> listOf(KidsCatalog.MOVIES)
                }
                val acc = ArrayList<Film>()
                cats.forEach { cat ->
                    runCatching {
                        RemoteIndexSource.categoryPage(context, cat, 1, 120)
                    }.getOrNull()?.films?.let { acc += it }
                }
                if (acc.isNotEmpty()) kidsServerFilms = acc.distinctBy { it.id }
            } finally {
                kidsLoading = false
            }
        } else {
            kidsLoading = false
        }
        // «En emisión»: la agenda TVMaze ya viene casada por titleId. Solo se pide
        // para catálogos de series; en Películas no aplica.
        if (section in listOf("series", "anime", "novelas") ||
            key == "kids-series" || key == "kids"
        ) {
            onAirIds = runCatching {
                RemoteIndexSource.schedule(7)
            }.getOrDefault(emptyList())
                .mapNotNull { airing -> airing.titleId.takeIf { it.isNotBlank() } }
                .distinct()
        }
    }

    var selectedCategory by rememberSaveable(section ?: key) {
        // Los submenús infantiles ahora abren el feed clasificado, no la cuadrícula plana.
        mutableStateOf<String?>(null)
    }
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }
    // Cambiar de modo (destacados ↔ cuadrícula) RE-CREA la fila de chips y, en la cuadrícula,
    // el héroe desaparece: sin un aterrizaje nuevo el foco moría ahí (el landing del héroe ya
    // no está compuesto). Se re-aterriza SOLO al cambiar de modo: elegir otro género dentro de
    // la cuadrícula conserva el foco en el chip que se acaba de pulsar (antes saltaba al botón
    // «Volver a Todo» en cada selección).
    val activeCategory = selectedCategory
    val kidsPagingCategory = when {
        key == "kids-series" -> KidsCatalog.SERIES
        KidsCatalog.isKidsKey(key) -> KidsCatalog.MOVIES
        section == "series" -> KidsCatalog.SERIES
        section == "movies" -> KidsCatalog.MOVIES
        else -> ""
    }
    val gridMode = activeCategory != null
    LaunchedEffect(gridMode) {
        delay(120)
        landing.request()
    }

    // El catálogo infantil del portal no siempre separa por temporadas: se usa el tipo
    // cuando hay suficientes títulos y se cae al pool completo si la ficha viene incompleta.
    val kidsGridPool = remember(kidsServerFilms, key, section) {
        val base = KidsCatalog.keep(kidsServerFilms)
        val typed = when {
            key == "kids-movies" || section == "movies" -> base.filter { it.seasons.isEmpty() }
            key == "kids-series" || section == "series" -> base.filter { it.seasons.isNotEmpty() }
            else -> base
        }
        typed.takeIf { it.size >= 4 } ?: base
    }

    val films = remember(rows, key, sectionRows, kidsGridPool) {
        val kidsPool = kidsGridPool.ifEmpty {
            rows.flatMap { it.films }.filter { it.kind == "kids" }.distinctBy { it.id }
        }
        val raw = when {
            KidsCatalog.isKidsKey(key) -> kidsPool
            section != null -> {
                (sectionRows ?: emptyList()).flatMap { it.films }.distinctBy { it.id }
            }
            else -> {
                val byId = rows.firstOrNull { it.id.equals(key, ignoreCase = true) }
                val byTitle = byId ?: rows.firstOrNull { fold(it.title).contains(fold(title)) }
                byTitle?.films?.takeIf { it.isNotEmpty() } ?: searchFallback(rows, title)
            }
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

    val feedLoading = (section != null && sectionRows == null) ||
        (KidsCatalog.isKidsKey(key) && kidsLoading && films.isEmpty())
    if (feedLoading) {
        Column(Modifier.fillMaxSize().background(VisionBlack)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(start = 48.dp, top = 28.dp))
            Text(
                if (KidsCatalog.isKidsKey(key)) "Cargando catálogo…" else "Cargando sección…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 48.dp, top = 12.dp),
            )
        }
        return
    }

    val loaded = sectionRows
    // Sin repetir tarjeta entre categorías: cada título vive en UNA fila de género
    // (el primario que calcula el servidor); esto además limpia la caché vieja y a
    // los servidores anteriores para que la misma película no salga en Drama y
    // Suspenso a la vez.
    val loadedDisjoint = remember(loaded) { loaded?.let { Popularity.disjointRows(it) } }
    val feedRows = remember(films, loadedDisjoint, onAirIds, section, key, title) {
        SectionCuration.build(
            key = section ?: key.ifBlank { title },
            title = title,
            pool = films,
            serverRows = loadedDisjoint.orEmpty(),
            onAirIds = onAirIds,
            kidsOnly = KidsCatalog.isKidsKey(key),
        )
    }
    val categoryChips = remember(feedRows, section, key) {
        val list = mutableListOf(CHIP_ALL)
        // Todo el catálogo, no solo los 30 de cada hilera.
        if (!KidsCatalog.isKidsKey(key) && section in listOf("movies", "series", "anime")) {
            list.add(CHIP_CATALOG)
        }
        list.addAll(feedRows.map { it.title.trim() }.filter { it.isNotBlank() })
        if (!KidsCatalog.isKidsKey(key) && section in listOf("movies", "series")) {
            list.add("Infantil")
        }
        list.distinct()
    }

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

            if (feedRows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Sin contenido todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 48.dp, top = 12.dp),
                    )
                }
            } else {
                itemsIndexed(feedRows, key = { _, row -> row.id }, contentType = { _, _ -> "row" }) { _, row ->
                    SectionRow(
                        label = row.title,
                        films = row.films,
                        isTopRow = SectionCuration.isTopTen(row),
                        rowId = row.id,
                        preserveOrder = true,
                        onOpenFilm = onOpenFilm,
                        onSeeAll = { selectedCategory = row.title },
                        returnRequester = returnRequester,
                        returnFilmId = returnFilmId,
                        entryRequester = rowEntry,
                        isReturnRow = returnRow == row.title,
                        onRowFocus = { returnRow = row.title },
                        total = row.total,
                    )
                }
            }
            item(key = "end") { Spacer(Modifier.height(36.dp)) }
        }
    } else {
        // MODO 2: VISTA DE CATÁLOGO COMPLETO EN CUADRÍCULA (GRID)
        val activeRow = remember(feedRows, activeCategory) {
            feedRows.firstOrNull { it.title.equals(activeCategory, ignoreCase = true) }
        }
        val isKidsChip = activeCategory.equals("Infantil", ignoreCase = true)
        val isKidsAll = activeRow != null && SectionCuration.isAllRow(activeRow)
        val categoryFilms = remember(activeCategory, activeRow, isKidsChip, kidsGridPool) {
            when {
                activeCategory == CHIP_CATALOG -> emptyList() // la cuadrícula pide la página 1
                isKidsChip -> Popularity.reorderRow(kidsGridPool)
                activeRow != null -> activeRow.films
                else -> emptyList()
            }
        }
        val categoryId = when {
            activeCategory == CHIP_CATALOG -> "xtv-${section ?: key}-all"
            isKidsChip || isKidsAll -> kidsPagingCategory
            activeRow != null && SectionCuration.isServerRow(activeRow) -> activeRow.id
            else -> ""
        }
        val showRank = activeRow != null && SectionCuration.isTopTen(activeRow)
        val specialBadge = if (activeRow != null && SectionCuration.isOnAir(activeRow)) "EN EMISIÓN" else null
        TvCategoryGrid(
            categoryName = activeCategory,
            categoryChips = categoryChips,
            films = categoryFilms,
            categoryId = categoryId,
            // Las filas derivadas no tienen categoría de servidor: sin sección no hay
            // fallback a «xtv-…-all» y la cuadrícula muestra solo lo ya calculado.
            section = null,
            kidsCategoryId = if (isKidsChip) kidsPagingCategory else "",
            initialTotal = when {
                activeCategory == CHIP_CATALOG -> 0
                isKidsChip || isKidsAll -> maxOf(kidsGridPool.size, 700)
                activeRow != null -> activeRow.total
                else -> categoryFilms.size
            },
            showRank = showRank,
            specialBadge = specialBadge,
            onSelectChip = { chip ->
                selectedCategory = if (chip == CHIP_ALL) null else chip
            },
            onBackToAll = { selectedCategory = null },
            onOpenFilm = onOpenFilm,
            returnRequester = returnRequester,
            returnFilmId = returnFilmId,
            entryRequester = rowEntry,
            landing = landing,
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
        modifier = Modifier.focusGroup().focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(chips.size, key = { chips[it] }, contentType = { "chip" }) { idx ->
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
    categoryId: String,
    section: String? = null,
    kidsCategoryId: String = "",
    initialTotal: Int,
    showRank: Boolean = false,
    specialBadge: String? = null,
    onSelectChip: (String) -> Unit,
    onBackToAll: () -> Unit,
    onOpenFilm: (Film) -> Unit,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    entryRequester: FocusRequester? = null,
    landing: LandingFocus? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lowEnd = remember { DevicePerformance.isLowEndDevice(context) }
    val batch = remember { SectionPaging.batch(lowEnd) }
    val cap = remember { SectionPaging.cap(lowEnd) }

    val isKidsCategory = categoryName.equals("Infantil", ignoreCase = true) ||
        categoryName.contains("infantil", ignoreCase = true) ||
        categoryName.contains("kids", ignoreCase = true)
    val targetCatId = remember(categoryName, categoryId, section, isKidsCategory, kidsCategoryId) {
        when {
            categoryId.isNotBlank() -> categoryId
            isKidsCategory && kidsCategoryId.isNotBlank() -> kidsCategoryId
            section != null -> "xtv-$section-all"
            else -> ""
        }
    }

    // La hilera trae 30 títulos: al abrir el género se pide la página 1 con el lote
    // del equipo para que la paginación sea exacta (antes `nextPage(30, 48)` volvía
    // a pedir la página 1 y «Ver más» moría en el primer toque).
    var seed by remember(categoryName, targetCatId) { mutableStateOf<List<Film>>(emptyList()) }
    var seedLoading by remember(categoryName, targetCatId) {
        mutableStateOf(targetCatId.isNotBlank() && films.isEmpty())
    }
    var total by remember(categoryName, targetCatId) {
        mutableIntStateOf(if (targetCatId.isNotBlank()) 0 else initialTotal.coerceAtLeast(films.size))
    }
    LaunchedEffect(categoryName, targetCatId) {
        if (targetCatId.isBlank()) {
            seedLoading = false
            return@LaunchedEffect
        }
        seedLoading = true
        val page1 = runCatching {
            RemoteIndexSource.categoryPage(context, targetCatId, 1, batch)
        }.getOrNull()
        if (page1 != null && page1.films.isNotEmpty()) {
            seed = page1.films
            if (page1.total > 0) total = page1.total
        } else if (films.isNotEmpty()) {
            total = initialTotal.coerceAtLeast(films.size)
        }
        seedLoading = false
    }

    // La hilera ya trae un lote curado (30–120 títulos): se conserva al llegar la
    // página 1 y solo se le suman los que falten. Antes la página 1 reemplazaba el
    // lote y la cuadrícula parecía perder títulos al abrirla.
    val base = remember(seed, films) { (films + seed).distinctBy { it.id } }
    var extra by remember(categoryName, targetCatId) { mutableStateOf<List<Film>>(emptyList()) }
    var page by remember(categoryName, targetCatId) { mutableIntStateOf(1) }
    var loadingMore by remember(categoryName, targetCatId) { mutableStateOf(false) }
    var exhausted by remember(categoryName, targetCatId) { mutableStateOf(false) }
    val allFilms = remember(base, extra) { (base + extra).distinctBy { it.id } }

    var visibleCount by remember(categoryName) { mutableIntStateOf(GRID_BATCH_SIZE) }
    val displayedFilms = remember(allFilms, visibleCount) { allFilms.take(visibleCount) }
    val gridState = rememberLazyGridState()

    // Al volver de una ficha, la cuadrícula se desplaza sola hasta la tarjeta abierta.
    val returnIndex = remember(displayedFilms, returnFilmId) {
        if (returnFilmId == null) -1 else displayedFilms.indexOfFirst { it.id == returnFilmId }
    }
    LaunchedEffect(returnIndex, displayedFilms) { gridState.revealReturnIndex(returnIndex) }

    val shouldLoadMore by remember(allFilms.size, displayedFilms.size) {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= displayedFilms.size - 8 && displayedFilms.size < allFilms.size
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = (visibleCount + GRID_BATCH_SIZE).coerceAtMost(allFilms.size)
        }
    }

    fun loadMore() {
        if (loadingMore) return
        if (visibleCount < allFilms.size) {
            visibleCount = (visibleCount + batch).coerceAtMost(allFilms.size)
            return
        }
        if (exhausted || loadingMore || targetCatId.isBlank() ||
            !SectionPaging.canLoadMore(allFilms.size, total, cap)
        ) {
            return
        }
        val next = page + 1
        val loadedBefore = allFilms.size
        loadingMore = true
        scope.launch {
            val result = RemoteIndexSource.categoryPage(context, targetCatId, next, batch)
            if (result.films.isNotEmpty()) {
                extra = (extra + result.films).distinctBy { it.id }
                page = next
            }
            if (result.total > 0) total = result.total
            val loadedAfter = (base + extra).distinctBy { it.id }.size
            if (result.films.isEmpty() || loadedAfter <= loadedBefore || loadedAfter >= cap ||
                (total > 0 && loadedAfter >= total)
            ) {
                exhausted = true
            }
            visibleCount = (visibleCount + batch).coerceAtMost(loadedAfter)
            loadingMore = false
        }
    }

    // Scroll infinito: al llegar al final de lo cargado se pide la página siguiente
    // sola (el botón «Ver más» sigue disponible para forzarla).
    val nearEnd by remember(allFilms.size, displayedFilms.size) {
        derivedStateOf {
            allFilms.isNotEmpty() && displayedFilms.size >= allFilms.size &&
                (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= displayedFilms.size - 4
        }
    }
    LaunchedEffect(nearEnd, allFilms.size) {
        if (nearEnd) loadMore()
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
                label = "Volver a Todo",
                icon = VisionIcons.Back,
                // Destino del aterrizaje en modo cuadrícula: el héroe (landing del feed) no
                // está compuesto aquí y este botón siempre existe.
                modifier = if (landing != null) Modifier.landingFocus(landing) else Modifier,
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
                text = "(${maxOf(total, allFilms.size)} títulos)",
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
                    text = if (seedLoading) "Cargando catálogo…" else "No se encontraron títulos en esta categoría",
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
                itemsIndexed(displayedFilms, key = { _, film -> "grid-${film.id}" }, contentType = { _, _ -> "card" }) { index, film ->
                    val isReturn = returnRequester != null && film.id == returnFilmId
                    Column(
                        modifier = Modifier.width(140.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        TvPosterCard(
                            title = film.title,
                            imageUrl = film.posterUrl,
                            score = film.score,
                            badge = specialBadge ?: if (film.year >= 2025) "NUEVO" else null,
                            rank = if (showRank && index in 0..9) index + 1 else null,
                            modifier = Modifier.fillMaxWidth().then(
                                when {
                                    isReturn -> Modifier.focusRequester(returnRequester)
                                    index % 5 == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                                    else -> Modifier
                                },
                            ),
                            onLongClick = rememberPosterLongClick(film),
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Mostrando ${displayedFilms.size} de ${maxOf(total, allFilms.size)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = VisionTextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        when {
                            loadingMore -> TvCompactAction(label = "Cargando…") {}
                            visibleCount < allFilms.size ||
                                (categoryId.isNotBlank() && !exhausted &&
                                    SectionPaging.canLoadMore(allFilms.size, total, cap)) ->
                                TvCompactAction(
                                    label = "Ver más",
                                    icon = VisionIcons.SeeAll,
                                ) { loadMore() }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (film.year > 0) {
                    Text(
                        film.year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                if (film.score > 0) {
                    Icon(
                        VisionIcons.Rating,
                        contentDescription = null,
                        tint = VisionRatingGold,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        "%.1f".format(film.score),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                TvCompactAction(
                    label = "Reproducir",
                    icon = VisionIcons.Play,
                    primary = true,
                    modifier = if (landing != null) Modifier.landingFocus(landing) else Modifier,
                    onClick = { onOpenFilm(film) },
                )
                TvCompactAction(
                    label = "Ver más tarde",
                    icon = if (inLater) VisionIcons.WatchLaterOn else VisionIcons.WatchLater,
                ) {
                    UserLibrary.toggleWatchLater(context, film)
                    inLater = !inLater
                }
                TvCompactAction(
                    label = "Favorito",
                    icon = if (inFavorites) VisionIcons.Favorite else VisionIcons.FavoriteBorder,
                ) {
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
    rowId: String = "",
    preserveOrder: Boolean = false,
    onOpenFilm: (Film) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    entryRequester: FocusRequester? = null,
    isReturnRow: Boolean = true,
    onRowFocus: () -> Unit = {},
    total: Int = 0,
) {
    val safeFilms = remember(films, preserveOrder) {
        val base = films.distinctBy { it.id }
        (if (preserveOrder) base else Popularity.reorderRow(base)).take(CAROUSEL_LIMIT)
    }
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
        itemsIndexed(safeFilms, key = { _, film -> "${label}-${film.id}" }, contentType = { _, _ -> "card" }) { idx, film ->
            val rank = if (isTopRow && idx in 0..9) idx + 1 else null
            val isReturn = isReturnRow && returnRequester != null && film.id == returnFilmId
            Column(Modifier.width(140.dp)) {
                TvPosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    score = film.score,
                    badge = if (rowId.endsWith("-en-emision")) {
                        "EN EMISIÓN"
                    } else {
                        if (film.year >= 2025) "NUEVO" else null
                    },
                    rank = rank,
                    onFocus = onRowFocus,
                    modifier = when {
                        isReturn -> Modifier.focusRequester(returnRequester)
                        idx == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                        else -> Modifier
                    },
                    onLongClick = rememberPosterLongClick(film),
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
        if (onSeeAll != null && (total > safeFilms.size || total <= 0)) {
            item(key = "more_$label") {
                TvSeeMoreCard(
                    count = total.coerceAtLeast(safeFilms.size),
                    onClick = onSeeAll,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** Tarjeta de cierre de una hilera de la tele: cuántos títulos hay y abre la cuadrícula. */
@Composable
private fun TvSeeMoreCard(count: Int, onClick: () -> Unit) {
    Column(Modifier.width(140.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            colors = CardDefaults.colors(containerColor = VisionSurfaceHigh),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    VisionIcons.SeeAll,
                    contentDescription = null,
                    tint = VisionBlueBright,
                    modifier = Modifier.size(32.dp),
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
