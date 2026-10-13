package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.tv.material3.Border
import com.chronosbvrx.visionplus.ui.Route
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.HomeCurated
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.data.XuperStatus
import com.chronosbvrx.visionplus.data.formatResume
import com.chronosbvrx.visionplus.data.metaLine
import com.chronosbvrx.visionplus.ui.LocalReducedEffects
import com.chronosbvrx.visionplus.ui.backdropFadeSpec
import com.chronosbvrx.visionplus.ui.components.GhostRow
import com.chronosbvrx.visionplus.ui.plugins.PluginUpdatesBellIcon
import com.chronosbvrx.visionplus.ui.plugins.appUpdateBadgeCount
import com.chronosbvrx.visionplus.ui.rememberHeroDrift
import com.chronosbvrx.visionplus.ui.rememberScreenPx
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionBlueDark
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Datos para el Billboard/Hero inmersivo del inicio. */
private data class Featured(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val meta: String,
    val film: Film? = null,
)

private fun Film.featured() = Featured(title, synopsis, posterUrl, metaLine(), this)

private const val HERO_SCALE = 1.12f
private const val HERO_DRIFT_MS = 14_000

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
 * Pantalla principal de Smart TV (Estilo Netflix / Max):
 * 1. Billboard inmersivo en la parte superior con botones interactivos de mando:
 *    [ ▶ Reproducir ], [ ℹ Detalles ], [ ★ Favorito ].
 * 2. Fila 1: "🔥 Top 10 en Vision+ Hoy" (Tendencias) con pósteres 2:3 y números gigantes estilizados 1..10.
 * 3. Fila 2: "✨ Novedades y Estrenos" con badges de estado NUEVO y calificación.
 * 4. Fila 3: "⏱️ Seguir viendo" con avance real y captura.
 * 5. Filas de catálogo y recomendaciones posteriores.
 * 6. Optimizada para dispositivos limitados (take 15 por carrusel, Coil 320x480).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    catalog: XuperCatalog,
    railItems: List<TvRailItem>,
    bottomItems: List<TvRailItem> = emptyList(),
    railReturn: String = "",
    returnFilmId: String? = null,
    onOpenFilm: (Film) -> Unit,
    onOpenLive: () -> Unit,
    onOpenCategories: () -> Unit,
    onResume: (ResumeCard) -> Unit,
    onGoRoute: (Route) -> Unit = {},
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current

    val rows = catalog.personalRows + catalog.rows
    LaunchedEffect(Unit) { catalog.refreshPersonal() }

    val noCatalogMessage = when {
        rows.isNotEmpty() -> null
        catalog.loading || catalog.status == XuperStatus.LOADING -> "Cargando tu contenido…"
        else -> "Tu contenido no está disponible ahora mismo. Revisa tu conexión e intenta de nuevo."
    }

    // El curado del Inicio (pool propio + Top 10 + Novedades + filas por tipo + héroe)
    // se calcula en segundo plano: con miles de films del índice, hacerlo en el hilo
    // de composición congelaba el D-pad en gama baja. Se conserva el valor anterior
    // hasta que llega el nuevo (sin parpadeos).
    val curated by androidx.compose.runtime.produceState(
        HomeCurated(),
        rows,
        catalog.indexRows,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            Popularity.homeCurated(rows, catalog.indexRows)
        }
    }
    val allAvailableFilms = curated.pool
    val top10Films = curated.top10
    val estrenosFilms = curated.premieres
    val ownKindRows = curated.kindRows

    // 3. Filas restantes del catálogo (evitando duplicar estrenos y listas personales en la cabecera)
    val otherRows = remember(rows) {
        val hasRealRows = rows.any { !it.id.startsWith("seed-") }
        rows.filterNot { row ->
            row.id.contains("estrenos", ignoreCase = true) ||
                row.id == "favorites" ||
                row.id == "watch-later" ||
                (hasRealRows && row.id.startsWith("seed-"))
        }
    }

    var focusFilm by remember { mutableStateOf<Film?>(null) }
    var featured by remember {
        mutableStateOf(
            top10Films.firstOrNull()?.featured()
                ?: allAvailableFilms.firstOrNull()?.featured(),
        )
    }

    LaunchedEffect(focusFilm) {
        val settled = focusFilm ?: return@LaunchedEffect
        delay(if (reduced) 320L else 180L)
        if (focusFilm == settled) featured = settled.featured()
    }

    val landing = rememberLandingFocus(if (railReturn.isEmpty()) Unit else null)
    val returnRequester = remember { FocusRequester() }
    val rowsListState = rememberLazyListState()

    // Al regresar de la ficha de una película, restaurar foco en la tarjeta exacta
    LaunchedEffect(returnFilmId) {
        if (returnFilmId == null) return@LaunchedEffect
        repeat(12) {
            if (FocusRescue.screenHasFocus) return@LaunchedEffect
            val ok = runCatching { returnRequester.requestFocus() }.isSuccess
            if (ok) return@LaunchedEffect
            delay(60)
        }
        if (!FocusRescue.screenHasFocus) {
            landing.request()
        }
    }

    // El primer elemento al cargar obtiene el protagonismo en el billboard
    LaunchedEffect(top10Films, allAvailableFilms) {
        if (featured == null) {
            featured = top10Films.firstOrNull()?.featured()
                ?: allAvailableFilms.firstOrNull()?.featured()
        }
        delay(150)
        if (!FocusRescue.screenHasFocus && returnFilmId == null && (top10Films.isNotEmpty() || allAvailableFilms.isNotEmpty())) {
            landing.request()
        }
    }

    // «Seguir viendo»: se recarga al volver del reproductor
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

    val currentFilm = focusFilm ?: featured?.film ?: top10Films.firstOrNull() ?: allAvailableFilms.firstOrNull()
    var inFavorites by remember(currentFilm?.id) {
        mutableStateOf(currentFilm?.let { UserLibrary.isFavorite(context, it.id) } ?: false)
    }

    val heroDrift by rememberHeroDrift(reduced, HERO_DRIFT_MS)
    val screenPx = rememberScreenPx()

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        // Fondo cinemático inmersivo con drift y degradado suave
        Crossfade(targetState = featured?.imageUrl, animationSpec = backdropFadeSpec(reduced), label = "bg") { url ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(0)
                        .size(screenPx.width, screenPx.height)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .fillMaxHeight(0.75f)
                        .align(Alignment.TopEnd)
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
                Brush.horizontalGradient(listOf(VisionBlack, VisionBlack, VisionBlack.copy(alpha = 0.45f), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, VisionBlack.copy(alpha = 0.6f), VisionBlack)),
            ),
        )

        // Contenido con riel a la izquierda y desplazamiento vertical fluido
        Column(Modifier.fillMaxSize().padding(start = TV_RAIL_CONTENT_START)) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
                LazyColumn(
                    state = rowsListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                ) {
                    // 1. BILLBOARD HERO INTERACTIVO CON ACCIONES DIRECTAS
                    item(key = "billboard") {
                        HeroBillboard(
                            featured = featured,
                            currentFilm = currentFilm,
                            inFavorites = inFavorites,
                            landing = landing,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            onOpenFilm = onOpenFilm,
                            onToggleFavorite = {
                                currentFilm?.let { f ->
                                    UserLibrary.toggleFavorite(context, f)
                                    inFavorites = !inFavorites
                                }
                            },
                        )
                    }

                    // 2. ACCESOS RÁPIDOS A COLECCIONES Y ESPECIALES (Brand Hubs: Fútbol, Anime, Novelas, Categorías)
                    item(key = "home_quick_hubs") {
                        TvQuickHubsRow(onGoRoute = onGoRoute)
                    }

                    // Estados de carga o reintento si el catálogo aún no responde
                    if (rows.isEmpty() && catalog.loading) {
                        item(key = "ghosts") {
                            Column(Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
                                GhostRow(count = 5, cardWidth = 140.dp, cardHeight = 210.dp, horizontalPadding = 0.dp)
                                GhostRow(count = 5, cardWidth = 140.dp, cardHeight = 210.dp, horizontalPadding = 0.dp)
                            }
                        }
                    } else if (noCatalogMessage != null) {
                        item(key = "norows") {
                            val scope = rememberCoroutineScope()
                            Column(Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
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

                    // 3. FILA 1: 🔥 TOP 10 EN VISION+ HOY (TENDENCIAS CON NÚMEROS GIGANTES)
                    if (top10Films.isNotEmpty()) {
                        item(key = "home_top10") {
                            HomePosterRow(
                                title = "🔥 Top 10 en Vision+ Hoy",
                                films = top10Films,
                                isTop10 = true,
                                returnRequester = returnRequester,
                                returnFilmId = returnFilmId,
                                onFocusFilm = { focusFilm = it },
                                onOpenFilm = onOpenFilm,
                            )
                        }
                    }

                    // 4. FILA 2: ✨ NOVEDADES Y ESTRENOS
                    if (estrenosFilms.isNotEmpty()) {
                        item(key = "home_estrenos") {
                            HomePosterRow(
                                title = "✨ Novedades y Estrenos",
                                films = estrenosFilms,
                                isNew = true,
                                returnRequester = returnRequester,
                                returnFilmId = returnFilmId,
                                onFocusFilm = { focusFilm = it },
                                onOpenFilm = onOpenFilm,
                            )
                        }
                    }

                    // 5. FILA 3: ⏱️ SEGUIR VIENDO (REDiseñado con barra de avance neón)
                    if (continueCards.isNotEmpty()) {
                        item(key = "continue") {
                            ContinueRow(
                                cards = continueCards,
                                returnRequester = returnRequester,
                                returnFilmId = returnFilmId,
                                onFocusFilm = { focusFilm = it },
                                onResume = onResume,
                            )
                        }
                    }

                    // 6. FILAS PROPIAS POR TIPO (populares, no nicho)
                    items(ownKindRows, key = { it.id }) { row ->
                        val limitedFilms = remember(row.films) { row.films.take(15) }
                        HomePosterRow(
                            title = row.title,
                            films = limitedFilms,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            onFocusFilm = { focusFilm = it },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // 7. RESTO DE FILAS: TV EN VIVO Y LAS FUENTES PROPIAS
                    items(otherRows, key = { it.id }) { row ->
                        val limitedFilms = remember(row.films) { row.films.take(15) }
                        HomePosterRow(
                            title = row.title,
                            films = limitedFilms,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            onFocusFilm = { focusFilm = it },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    item(key = "end_spacer") {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }

        // Riel de navegación lateral con D-pad
        TvSideRail(railItems, Modifier.align(Alignment.CenterStart), bottomItems)

        // Campana de actualizaciones del sistema
        var showPluginUpdates by rememberSaveable { mutableStateOf(false) }
        val updatesBadge = appUpdateBadgeCount()
        val tvContext = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(updatesBadge) {
            if (updatesBadge > 0) {
                val latest = com.chronosbvrx.visionplus.data.AppUpdate.latest?.version.orEmpty()
                val skipped = tvContext.getSharedPreferences("updates", 0).getString("skipped", "")
                if (latest.isNotBlank() && skipped != latest) showPluginUpdates = true
            }
        }
        if (showPluginUpdates) {
            TvAppUpdatesDialog(onDismiss = {
                showPluginUpdates = false
                com.chronosbvrx.visionplus.data.AppUpdate.latest?.version?.let { v ->
                    tvContext.getSharedPreferences("updates", 0).edit().putString("skipped", v).apply()
                }
            })
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

        // Botón de recarga de catálogo
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
 * Accesos rápidos a Colecciones y Especiales (Brand Hubs: Fútbol, Anime, Novelas, Categorías).
 */
private data class TvQuickHub(
    val title: String,
    val badge: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: Route,
)

private val TV_QUICK_HUBS = listOf(
    TvQuickHub("Fútbol en Vivo", "EN DIRECTO", Icons.Default.SportsSoccer, Route.Sports),
    TvQuickHub("Descargas (USB)", "OFFLINE", Icons.Default.Usb, Route.Downloads),
    TvQuickHub("Anime", "COLECCIÓN", Icons.Default.Animation, Route.Section("anime", "Anime")),
    TvQuickHub("Novelas", "POPULAR", Icons.Default.AutoStories, Route.Section("novelas", "Novelas")),
    TvQuickHub("Categorías", "EXPLORAR", Icons.Default.GridView, Route.Categories),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvQuickHubsRow(
    onGoRoute: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 10.dp, bottom = 4.dp)) {
        TvRowLabel("✨ Colecciones y Especiales", 22.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(TV_QUICK_HUBS, key = { it.title }) { hub ->
                    TvQuickHubCard(hub = hub, onClick = { onGoRoute(hub.route) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TvQuickHubCard(
    hub: TvQuickHub,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(184.dp)
            .height(68.dp)
            .onFocusChanged { hasFocus = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VisionBlack.copy(alpha = 0.65f),
            focusedContainerColor = VisionBlue,
            pressedContainerColor = VisionBlueDark,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = Border(
                BorderStroke(2.dp, VisionBlueBright),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (hasFocus) Color.White.copy(alpha = 0.22f) else VisionBlue.copy(alpha = 0.18f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = hub.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasFocus) Color.White else VisionBlueBright,
                )
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = hub.badge,
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        color = if (hasFocus) Color.White.copy(alpha = 0.9f) else VisionBlueBright,
                    ),
                )
                Text(
                    text = hub.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Billboard Cinemático del Hero con badge, metadatos y botones directos interactivos.
 */
@Composable
private fun HeroBillboard(
    featured: Featured?,
    currentFilm: Film?,
    inFavorites: Boolean,
    landing: LandingFocus?,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    onOpenFilm: (Film) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    if (featured == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 20.dp),
    ) {
        // Badge superior distintivo
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(VisionBlue, VisionBlueDark)),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(
                text = "🔥 TENDENCIA DE HOY",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp,
                ),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Título estilizado en blanco limpio
        Text(
            text = featured.title,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.58f),
        )

        // Metadatos (Año, Calificación, Género / Temporadas)
        if (featured.meta.isNotBlank()) {
            Text(
                text = featured.meta,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.58f),
            )
        }

        // Sinopsis
        if (featured.subtitle.isNotBlank()) {
            Text(
                text = featured.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VisionTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.55f),
            )
        }

        // Botones de acción directa con foco de control remoto
        val isHeroTarget = returnFilmId != null && currentFilm?.id == returnFilmId
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            TvCompactAction(
                label = "▶  Reproducir",
                modifier = when {
                    isHeroTarget && returnRequester != null -> Modifier.focusRequester(returnRequester)
                    landing != null -> Modifier.landingFocus(landing)
                    else -> Modifier
                },
                onClick = { currentFilm?.let(onOpenFilm) },
            )
            TvCompactAction(
                label = "ℹ  Detalles",
                onClick = { currentFilm?.let(onOpenFilm) },
            )
            TvCompactAction(
                label = if (inFavorites) "★ En Favoritos" else "☆ Favorito",
                onClick = onToggleFavorite,
            )
        }
    }
}

/**
 * Fila moderna con pósteres verticales 2:3, números Top 10 y badges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePosterRow(
    title: String,
    films: List<Film>,
    isTop10: Boolean = false,
    isNew: Boolean = false,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    onFocusFilm: (Film) -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    val safeFilms = remember(films) { films.distinctBy { it.id }.take(15) }
    if (safeFilms.isEmpty()) return

    Column(modifier = Modifier.padding(top = 18.dp)) {
        TvRowLabel(title, 26.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(safeFilms, key = { idx, film -> "${title}-${film.id}-${idx}" }) { idx, film ->
                    val rank = if (isTop10 && idx in 0..9) idx + 1 else null
                    val badge = when {
                        isTop10 -> "TOP 10"
                        isNew || film.year >= 2025 -> "NUEVO"
                        else -> null
                    }
                    val isReturn = returnRequester != null && film.id == returnFilmId
                    Column(Modifier.width(140.dp)) {
                        TvPosterCard(
                            title = film.title,
                            imageUrl = film.posterUrl,
                            score = film.score,
                            badge = badge,
                            rank = rank,
                            modifier = if (isReturn) Modifier.focusRequester(returnRequester) else Modifier,
                            onFocus = { onFocusFilm(film) },
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
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Fila moderna de «Seguir viendo» con barra de avance en azul neón brillante.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueRow(
    cards: List<ResumeCard>,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    onFocusFilm: (Film) -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    if (cards.isEmpty()) return
    Column(modifier = Modifier.padding(top = 18.dp)) {
        TvRowLabel("⏱️ Seguir viendo", 26.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cards, key = { it.entry.key }) { card ->
                    val film = card.film
                    val title = film?.title ?: card.entry.title
                    val isReturn = returnRequester != null && (film?.id == returnFilmId || card.entry.key == returnFilmId || card.entry.xtvCode == returnFilmId)
                    Column(Modifier.width(196.dp)) {
                        TvLandscapeCard(
                            title = title,
                            imageUrl = film?.posterUrl ?: card.entry.posterUrl.takeIf { it.isNotBlank() },
                            cardHeight = 110.dp,
                            modifier = if (isReturn) Modifier.focusRequester(returnRequester) else Modifier,
                            onFocus = { film?.let(onFocusFilm) },
                            onClick = { onResume(card) },
                        )
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(card.progress.coerceIn(0.04f, 1f))
                                    .background(
                                        Brush.horizontalGradient(listOf(VisionBlue, VisionBlueBright)),
                                        CircleShape,
                                    ),
                            )
                        }
                        Text(
                            text = "Continuar en " + formatResume(card.entry.positionMs),
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
        Spacer(Modifier.height(10.dp))
    }
}
