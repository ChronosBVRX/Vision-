package com.chronosbvrx.visionplus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.XuperStatus
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.components.GhostRow
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.components.PosterCard
import com.chronosbvrx.visionplus.ui.isLandscapeTablet
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Home sizes based on the screen's shape. */
private data class HomeSizes(val heroHeight: Dp, val posterWidth: Dp)

@Composable
private fun homeSizes(): HomeSizes =
    if (isLandscapeTablet()) HomeSizes(heroHeight = 440.dp, posterWidth = 190.dp)
    else HomeSizes(heroHeight = 300.dp, posterWidth = 132.dp)

/** One of Home's quick selections: the content types the person's single source offers. */
private data class HomeSelection(val label: String, val icon: ImageVector, val route: Route)

private val SELECTIONS = listOf(
    HomeSelection("Películas", Icons.Default.Movie, Route.Section("movies", "Películas")),
    HomeSelection("Series", Icons.Default.Theaters, Route.Section("series", "Series")),
    HomeSelection("Fútbol", Icons.Default.SportsSoccer, Route.Sports),
    HomeSelection("TV en Vivo", Icons.Default.LiveTv, Route.Live),
    HomeSelection("Anime", Icons.Default.Animation, Route.Section("anime", "Anime")),
    HomeSelection("Novelas", Icons.Default.AutoStories, Route.Section("novelas", "Novelas")),
    HomeSelection("Categorías", Icons.Default.GridView, Route.Categories),
)

/**
 * The phone's Home, streaming-style: a full-bleed featured hero with its own actions, the quick
 * selections, and then one tidy row of posters per catalogue row. Everything lands on screen in
 * one pass, with gentle skeletons while the sources answer.
 */
@Composable
fun HomeScreen(
    catalog: XuperCatalog,
    contentPadding: PaddingValues,
    onOpenFilm: (Film) -> Unit,
    onPlayFilm: (Film) -> Unit,
    onGoSection: (Route) -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    val sizes = homeSizes()
    val rows = catalog.personalRows + catalog.rows
    val hero = rows.firstOrNull()?.films?.firstOrNull()
    androidx.compose.runtime.LaunchedEffect(Unit) { catalog.refreshPersonal() }
    val context = LocalContext.current
    // «Seguir viendo»: se recarga al volver del reproductor.
    var continueCards by remember { mutableStateOf<List<ResumeCard>>(emptyList()) }
    fun reloadContinue() {
        val films = allFilms(catalog.personalRows + catalog.rows + catalog.indexRows)
        continueCards = PlaybackHistory.continueCards(context, films)
    }
    LaunchedEffect(Unit) { reloadContinue() }
    val homeLifecycle = LocalLifecycleOwner.current
    DisposableEffect(homeLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadContinue()
        }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }
    val allAvailableFilms = remember(rows, catalog.indexRows) {
        val nonSeedFilms = (rows + catalog.indexRows).flatMap { it.films }
            .filter { !it.id.contains("seed_") && it.videoUrl.isNotBlank() }
        val candidates = if (nonSeedFilms.isNotEmpty()) nonSeedFilms else (rows + catalog.indexRows).flatMap { it.films }
        candidates.distinctBy {
            val cleanCid = it.videoUrl.substringAfter(':').substringBefore('|')
            if (cleanCid.isNotBlank()) cleanCid.lowercase() else it.id.lowercase()
        }
    }
    val top10Films = remember(allAvailableFilms) {
        allAvailableFilms
            .filter { it.score > 0 }
            .sortedByDescending { it.score * 10 + (if (it.year >= 2024) 8 else 0) }
            .take(10)
    }
    val estrenosFilms = remember(allAvailableFilms, catalog.rows, catalog.indexRows) {
        val shelf = (catalog.indexRows + catalog.rows)
            .firstOrNull { it.id.contains("estrenos", ignoreCase = true) }?.films.orEmpty()
            .filter { !it.id.contains("seed_") }
        (shelf + allAvailableFilms.filter { it.year >= 2024 }.sortedByDescending { it.year * 100 + it.score })
            .distinctBy {
                val cleanCid = it.videoUrl.substringAfter(':').substringBefore('|')
                if (cleanCid.isNotBlank()) cleanCid.lowercase() else it.id.lowercase()
            }
            .take(15)
    }
    val otherRows = remember(rows) {
        val hasRealRows = rows.any { !it.id.startsWith("seed-") }
        rows.filterNot { row ->
            row.id.contains("estrenos", ignoreCase = true) ||
                row.id == "favorites" ||
                row.id == "watch-later" ||
                (hasRealRows && row.id.startsWith("seed-"))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            if (hero != null) {
                Hero(
                    sizes = sizes,
                    backdropUrl = hero.posterUrl,
                    title = hero.title,
                    subtitle = hero.metaLine(),
                    onPlay = { onPlayFilm(hero) },
                    onDetails = { onOpenFilm(hero) },
                )
            } else {
                HeroSkeleton(sizes)
            }
        }
        item(key = "selection") { Selections(onGoSection) }
        if (top10Films.isNotEmpty()) {
            item(key = "home_top10") {
                FilmRow(
                    row = CatalogRow("home-top10", "🔥 Top 10 en Vision+ Hoy", top10Films),
                    posterWidth = sizes.posterWidth,
                    onOpen = onOpenFilm,
                    onSeeAll = { onGoSection(Route.Section("movies", "Películas")) },
                )
            }
        }
        if (estrenosFilms.isNotEmpty()) {
            item(key = "home_estrenos") {
                FilmRow(
                    row = CatalogRow("home-estrenos", "✨ Novedades y Estrenos", estrenosFilms),
                    posterWidth = sizes.posterWidth,
                    onOpen = onOpenFilm,
                    onSeeAll = { onGoSection(Route.Section("movies", "Películas")) },
                )
            }
        }
        if (continueCards.isNotEmpty()) {
            item(key = "continue") { ContinueRow(cards = continueCards, onResume = onResume) }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                if (catalog.status == XuperStatus.LOADING || catalog.loading) {
                    Column(Modifier.padding(top = 8.dp)) {
                        GhostRow()
                        GhostRow()
                        GhostRow()
                    }
                } else {
                    SourceNote(catalog)
                }
            }
        }
        items(otherRows, key = { it.id }) { row ->
            val seeAllAction: (() -> Unit)? = remember(row) {
                val t = row.title.lowercase()
                when {
                    t.contains("película") || t.contains("cine") -> { { onGoSection(Route.Section("movies", "Películas")) } }
                    t.contains("serie") -> { { onGoSection(Route.Section("series", "Series")) } }
                    t.contains("anime") -> { { onGoSection(Route.Section("anime", "Anime")) } }
                    t.contains("novela") -> { { onGoSection(Route.Section("novelas", "Novelas")) } }
                    else -> null
                }
            }
            FilmRow(
                row = row,
                posterWidth = sizes.posterWidth,
                onOpen = onOpenFilm,
                onSeeAll = seeAllAction,
            )
        }
        item(key = "home-note") {
            Text(
                HOME_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp),
            )
        }
    }
}

/** The short footer line that says what this app is. */
internal const val HOME_NOTE =
    "Vision+ · Tu entretenimiento en un solo lugar. " +
        "Películas, series y TV en vivo."

/** Full-width feature: backdrop, bottom gradient, big title and its two actions. */
@Composable
private fun Hero(
    sizes: HomeSizes,
    backdropUrl: String?,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(sizes.heroHeight),
    ) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        0.75f to Color.Black.copy(alpha = 0.82f),
                        1f to VisionBlack,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reproducir", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDetails,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Detalles")
                }
            }
        }
    }
}

/** The hero's quiet stand-in while the first rows are on their way. */
@Composable
private fun HeroSkeleton(sizes: HomeSizes) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(sizes.heroHeight)
            .background(
                Brush.verticalGradient(
                    0f to VisionSurfaceHigh,
                    1f to VisionBlack,
                ),
            ),
    )
}

/** A Home row: its title, optional "Ver todo →" link, and a horizontal carousel of posters. */
@Composable
private fun FilmRow(
    row: CatalogRow,
    posterWidth: Dp,
    onOpen: (Film) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (row.films.isEmpty()) return
    val isTopRow = row.title.contains("top 10", ignoreCase = true) ||
        row.title.contains("más vistas", ignoreCase = true) ||
        row.title.contains("tendencia", ignoreCase = true)

    Column(Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        ) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAll != null) {
                Text(
                    text = "Ver todo →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = VisionBlue,
                    modifier = Modifier
                        .clickable(onClick = onSeeAll)
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(row.films.take(15), key = { _, film -> "${row.id}-${film.id}" }) { index, film ->
                val rank = if (isTopRow && index in 0..9) index + 1 else null
                PosterCard(
                    title = film.title,
                    imageUrl = film.posterUrl,
                    meta = film.metaLine().ifBlank { null },
                    score = film.score,
                    badge = if (film.year >= 2025) "NUEVO" else null,
                    rank = rank,
                    modifier = Modifier.width(posterWidth),
                    onClick = { onOpen(film) },
                )
            }
        }
    }
}

/** Home's four selections: the content types the person's single source offers. Titles for tests. */
internal const val SELECTION_TITLE = "Qué quieres ver"

/**
 * The four tiles under the hero: TV en Vivo goes to "En vivo"; Películas, Series and Anime to
 * "Categorías", where the source's content is browsed.
 */
@Composable
private fun Selections(onGo: (Route) -> Unit) {
    Column(Modifier.padding(top = 20.dp, bottom = 2.dp)) {
        Text(
            SELECTION_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(SELECTIONS, key = { it.label }) { s -> SelectionCard(s, onGo) }
        }
    }
}

/** A selection chip: its icon and label inside a rounded, glassy pill. */
@Composable
private fun SelectionCard(s: HomeSelection, onGo: (Route) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(VisionSurfaceHigh)
            .clickable { onGo(s.route) }
            .padding(horizontal = 16.dp),
    ) {
        Icon(s.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(s.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

/** What Home says when the catalog has nothing to show yet (loading, no internet, or an empty list). */
@Composable
private fun SourceNote(catalog: XuperCatalog) {
    val scope = rememberCoroutineScope()
    val (title, body) = when {
        catalog.loading || catalog.status == XuperStatus.LOADING ->
            "Cargando tu contenido…" to "Películas, series y anime de tu cuenta llegan en un momento."
        else -> "Tu contenido no está disponible ahora mismo." to "Revisa tu conexión e intenta de nuevo."
    }
    EmptyState(title = title, subtitle = body)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        TextButton(onClick = { scope.launch { catalog.load() } }) { Text("Reintentar", color = VisionBlue) }
    }
}

internal const val SOURCE_NOTE_FALLBACK =
    "Películas, series y anime de tu cuenta aparecen aquí."

/** «Seguir viendo»: lo quedado a medias, con su avance, para reanudar tocando. */
@Composable
private fun ContinueRow(cards: List<ResumeCard>, onResume: (ResumeCard) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(
            "Seguir viendo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.entry.key }) { card ->
                Column(Modifier.width(132.dp)) {
                    PosterCard(
                        title = card.film?.title ?: card.entry.title,
                        imageUrl = card.film?.posterUrl ?: card.entry.posterUrl.takeIf { it.isNotBlank() },
                        meta = null,
                        onClick = { onResume(card) },
                    )
                    Box(
                        Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp)
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            Modifier.height(3.dp).fillMaxWidth(card.progress)
                                .background(VisionBlue),
                        )
                    }
                    Text(
                        "Continuar en " + formatResume(card.entry.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
