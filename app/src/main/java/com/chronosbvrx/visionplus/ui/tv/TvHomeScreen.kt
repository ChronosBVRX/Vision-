package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.AppUpdate
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.ui.components.GhostRow
import com.chronosbvrx.visionplus.data.XuperStatus
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.backdropFadeSpec
import com.chronosbvrx.visionplus.ui.comingSoon
import com.chronosbvrx.visionplus.ui.plugins.PluginUpdatesBellIcon
import com.chronosbvrx.visionplus.ui.plugins.appUpdateBadgeCount
import com.chronosbvrx.visionplus.ui.rememberHeroDrift
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the hero shows: the focused card's film. */
private data class Featured(val title: String, val subtitle: String, val imageUrl: String?, val meta: String)

private fun Film.featured() = Featured(title, synopsis, posterUrl, metaLine())

/** How much the hero's background is enlarged so it can drift without a border showing. */
private const val HERO_SCALE = 1.12f

/** How long the drift takes to cross from one end to the other: slow, it should feel like breathing. */
private const val HERO_DRIFT_MS = 14_000

/** The selections row's height: its pills plus the gap between them and the first catalog row. */
internal val SELECTIONS_HEIGHT = TAB_HEIGHT + 14.dp

/**
 * Horizontal scroll that keeps the focused card at 30% of the row, so the row runs under a card that
 * stays still instead of dragging it against the edge.
 */
@OptIn(ExperimentalFoundationApi::class)
private val TvPivot = object : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec = tween<Float>(durationMillis = 125, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val initialTarget = 0.3f * containerSize
        val leftover = containerSize - initialTarget
        val target = if (size <= containerSize && leftover < size) containerSize - size else initialTarget
        return offset - target
    }
}

/** Vertical scroll that only moves when the focused row is not fully visible already. */
@OptIn(ExperimentalFoundationApi::class)
private val MinimalScrollBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val bottomEdge = offset + size
        return when {
            offset >= 0f && bottomEdge <= containerSize -> 0f
            offset < 0f && bottomEdge > containerSize -> 0f
            abs(offset) < abs(bottomEdge - containerSize) -> offset
            else -> bottomEdge - containerSize
        }
    }
}

/**
 * The TV Home: a fixed immersive hero (the focused film's backdrop, title and synopsis) over a zone
 * that scrolls exactly two rows of landscape cards, with the navigation rail on the left and, top
 * right, the plugin-updates bell and reload. The zone opens with the four sections of the person's
 * single source (TV en vivo, Películas, Series, Anime).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    catalog: XuperCatalog,
    railItems: List<TvRailItem>,
    bottomItems: List<TvRailItem> = emptyList(),
    railReturn: String = "",
    onOpenFilm: (Film) -> Unit,
    onOpenLive: () -> Unit,
    onOpenCategories: () -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current
    // Solo las filas curadas: el índice primero pintaba sus categorías y al llegar el portal
    // todo cambiaba a sus repisas nativas a mitad de sesión (el "cambio de clasificación").
    val rows = catalog.personalRows + catalog.rows
    androidx.compose.runtime.LaunchedEffect(Unit) { catalog.refreshPersonal() }
    // El índice cacheado pinta al instante; lo fresco lo reemplaza en cuanto llega.
    val noCatalogMessage = when {
        rows.isNotEmpty() -> null
        catalog.loading || catalog.status == XuperStatus.LOADING -> "Cargando tu contenido…"
        else -> "Tu contenido no está disponible ahora mismo. Revisa tu conexión e intenta de nuevo."
    }
    var featured by remember { mutableStateOf(rows.firstOrNull()?.films?.firstOrNull()?.featured()) }
    // El héroe sigue al foco asentado, no a cada tarjeta que el mando roza: sin esta pausa
    // cada movimiento recargaba la imagen y el desplazamiento iba a tirones.
    var focusFilm by remember { mutableStateOf<Film?>(null) }
    LaunchedEffect(focusFilm) {
        val settled = focusFilm ?: return@LaunchedEffect
        delay(if (reduced) 320L else 180L)
        if (focusFilm == settled) featured = settled.featured()
    }
    // The card a film page was opened from, so Back lands on it again.
    var returnKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Lands on the card a film page was opened from, else on the first card.
    // Con memoria de riel, el foco de Inicio queda de respaldo (el riel lo pide): un solo dueño.
    val landing = rememberLandingFocus(if (railReturn.isEmpty()) Unit else null)
    val rowsListState = rememberLazyListState()

    // Snap the rows zone to a row boundary when it stops, so two whole rows show, never three halves.
    LaunchedEffect(rowsListState) {
        snapshotFlow { rowsListState.isScrollInProgress }.collect { inMotion ->
            if (inMotion) return@collect
            val offset = rowsListState.firstVisibleItemScrollOffset
            if (offset == 0) return@collect
            val height = rowsListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return@collect
            val target = rowsListState.firstVisibleItemIndex + if (offset > height / 2) 1 else 0
            runCatching { rowsListState.animateScrollToItem(target) }
        }
    }

    // The catalog arrives from the source after the first frame: give the hero its first film.
    LaunchedEffect(rows) {
        if (featured == null && rows.isNotEmpty()) {
            featured = rows.first().films.firstOrNull()?.featured()
        }
        // Si la recarga reconstruyó las filas bajo el foco, el mando vuelve solo a la primera tarjeta.
        delay(150)
        if (!FocusRescue.screenHasFocus && rows.isNotEmpty()) landing.request()
    }

    // «Seguir viendo»: se recarga al volver del reproductor (otra actividad: al regresar
    // hay ON_RESUME aunque la ruta sea la misma y nada se recomponga solo).
    var continueCards by remember { mutableStateOf<List<ResumeCard>>(emptyList()) }
    val homeScope = rememberCoroutineScope()
    fun reloadContinue() {
        homeScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val history = PlaybackHistory.list(context)
            if (history.isEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { continueCards = emptyList() }
                return@launch
            }
            val localFilms = (catalog.personalRows + catalog.rows).flatMap { it.films }
            val matched = PlaybackHistory.continueCards(context, localFilms)
            val finalCards = if (matched.all { it.film != null } || catalog.indexRows.isEmpty()) {
                matched
            } else {
                val all = (catalog.personalRows + catalog.rows + catalog.indexRows).flatMap { it.films }
                PlaybackHistory.continueCards(context, all)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { continueCards = finalCards }
        }
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

    // Fixed-size cards and rows: the rows zone measures exactly the selections row plus two rows (label + card).
    val cardHeight = 92.dp
    val labelHeight = 26.dp
    val rowGap = 14.dp
    val rowsTopPad = 6.dp
    val rowUnit = labelHeight + cardHeight + rowGap
    val rowsRegionHeight = rowUnit * 2 + rowsTopPad + SELECTIONS_HEIGHT
    val heroDrift by rememberHeroDrift(reduced, HERO_DRIFT_MS)

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        Crossfade(targetState = featured?.imageUrl, animationSpec = backdropFadeSpec(reduced), label = "bg") { url ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight()
                        .align(Alignment.TopEnd)
                        // Enlarged a bit and wandering within that slack, so a border never shows.
                        .graphicsLayer {
                            if (!reduced) {
                                val margin = size.width * (HERO_SCALE - 1f) / 2f
                                scaleX = HERO_SCALE
                                scaleY = HERO_SCALE
                                translationX = (heroDrift * 2f - 1f) * margin
                            }
                        },
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(VisionBlack, VisionBlack, VisionBlack.copy(alpha = 0.15f), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, VisionBlack.copy(alpha = 0.4f), VisionBlack)),
            ),
        )

        Column(Modifier.fillMaxSize().padding(start = TV_RAIL_CONTENT_START)) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 48.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                featured?.let { f ->
                    Text(
                        f.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                    if (f.meta.isNotBlank()) {
                        Text(
                            f.meta,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.55f),
                        )
                    }
                    if (f.subtitle.isNotBlank()) {
                        Text(
                            f.subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = VisionTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.55f),
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
                LazyColumn(
                    state = rowsListState,
                    modifier = Modifier.fillMaxWidth().height(rowsRegionHeight).padding(top = rowsTopPad),
                ) {
                    // Sin botones de acción: el inicio es solo contenido, estilo Netflix (los accesos viven en el riel).
                    if (rows.isEmpty() && catalog.loading) {
                        item(key = "ghosts") {
                            Column(Modifier.padding(horizontal = 48.dp)) {
                                GhostRow(count = 5, cardWidth = 190.dp, cardHeight = 108.dp, horizontalPadding = 0.dp)
                                GhostRow(count = 5, cardWidth = 190.dp, cardHeight = 108.dp, horizontalPadding = 0.dp)
                                GhostRow(count = 5, cardWidth = 190.dp, cardHeight = 108.dp, horizontalPadding = 0.dp)
                            }
                        }
                    } else if (noCatalogMessage != null) {
                        item(key = "norows") {
                            val scope = rememberCoroutineScope()
                            Column(Modifier.padding(horizontal = 48.dp)) {
                                Text(
                                    noCatalogMessage,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(end = 160.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                                    TvCompactAction(label = "Reintentar", onClick = { scope.launch { catalog.load() } })
                                }
                            }
                        }
                    }
                    if (continueCards.isNotEmpty()) {
                        item(key = "continue") {
                            ContinueRow(
                                cards = continueCards,
                                cardHeight = cardHeight,
                                onFocusFilm = { focusFilm = it },
                                onResume = onResume,
                            )
                        }
                    }
                    items(rows, key = { it.id }) { row ->
                        val limitedFilms = remember(row.films) { row.films.take(15) }
                        Column {
                            TvRowLabel(row.title, labelHeight)
                            CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(limitedFilms, key = { "${row.id}-${it.id}" }) { film ->
                                        val cardKey = "${row.id}-${film.id}"
                                        val isLanding = if (returnKey != null) cardKey == returnKey else row == rows.first() && film == row.films.first()
                                        val modifier = if (isLanding) Modifier.landingFocus(landing) else Modifier
                                        TvLandscapeCard(
                                            title = film.title,
                                            imageUrl = film.posterUrl,
                                            cardHeight = cardHeight,
                                            modifier = modifier,
                                            onFocus = { focusFilm = film },
                                            onClick = {
                                                returnKey = cardKey
                                                onOpenFilm(film)
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(rowGap))
                        }
                    }
                    item(key = "rows_bottom_pad") { Spacer(Modifier.height(rowGap)) }
                }
            }
        }

        TvSideRail(railItems, Modifier.align(Alignment.CenterStart), bottomItems)
        // The app's releases bell sits beside reload, as on the phone's Home; it checks once here.
        var showPluginUpdates by rememberSaveable { mutableStateOf(false) }
        val updatesBadge = appUpdateBadgeCount()
        if (showPluginUpdates) {
            TvAppUpdatesDialog(onDismiss = { showPluginUpdates = false })
        }
        Surface(
            onClick = { showPluginUpdates = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp + 48.dp + 12.dp).size(48.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = VisionBlack.copy(alpha = 0.55f),
                contentColor = Color.White,
                focusedContainerColor = VisionBlue,
                focusedContentColor = Color.White,
                pressedContainerColor = VisionBlue,
                pressedContentColor = Color.White,
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PluginUpdatesBellIcon(updatesBadge) }
        }
        // Reload sits in the top right corner: an action on the screen, not a place to go.
        val reloadScope = rememberCoroutineScope()
        Surface(
            onClick = { reloadScope.launch { catalog.load() } },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp).size(48.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = VisionBlack.copy(alpha = 0.55f),
                contentColor = Color.White,
                focusedContainerColor = VisionBlue,
                focusedContentColor = Color.White,
                pressedContainerColor = VisionBlue,
                pressedContentColor = Color.White,
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Refresh, contentDescription = "Recargar", modifier = Modifier.size(26.dp))
            }
        }
    }
}

/**
 * «Seguir viendo»: lo quedado a medias, con su avance y el minuto, para reanudar con OK.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueRow(
    cards: List<ResumeCard>,
    cardHeight: Dp,
    onFocusFilm: (Film) -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    Column {
        TvRowLabel("Seguir viendo", 26.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cards, key = { it.entry.key }) { card ->
                    Column(Modifier.width(178.dp)) {
                        TvLandscapeCard(
                            title = card.film?.title ?: card.entry.title,
                            imageUrl = card.film?.posterUrl ?: card.entry.posterUrl.takeIf { it.isNotBlank() },
                            cardHeight = cardHeight,
                            onFocus = { card.film?.let(onFocusFilm) },
                            onClick = { onResume(card) },
                        )
                        Box(
                            Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        ) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(card.progress)
                                    .background(VisionBlue, CircleShape),
                            )
                        }
                        Text(
                            "Continuar en " + formatResume(card.entry.positionMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
