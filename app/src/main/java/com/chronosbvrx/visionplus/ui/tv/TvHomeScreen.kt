@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.chronosbvrx.visionplus.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.tv.material3.Border
import com.chronosbvrx.visionplus.ui.Route
import com.chronosbvrx.visionplus.ui.icons.VisionIcons
import com.chronosbvrx.visionplus.ui.icons.homeRowIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.chronosbvrx.visionplus.data.BecauseRow
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.FollowAiring
import com.chronosbvrx.visionplus.data.HomeCurated
import com.chronosbvrx.visionplus.data.HomePersonal
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.UserLibrary
import com.chronosbvrx.visionplus.data.allFilms
import com.chronosbvrx.visionplus.data.banners.BannerSource
import com.chronosbvrx.visionplus.data.banners.bannerArtCacheKey
import com.chronosbvrx.visionplus.data.banners.HomeBanner
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
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
import com.chronosbvrx.visionplus.ui.sports.DeportesReturn
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
    val event: GeeSportsSource.GeeEvent? = null,
    val badge: String = "TENDENCIA DE HOY",
    val eyebrow: String = "TENDENCIA DE HOY",
) {
    val key: String
        get() = film?.id ?: event?.id ?: title
}

private fun Film.featured() = Featured(title, synopsis, posterUrl, metaLine(), this)

private fun TvHeroItem.featured() = Featured(
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl.ifBlank { null },
    meta = meta,
    film = film,
    event = event,
    badge = badge,
    eyebrow = eyebrow,
)

/** En Robolectric no se toca la red: los tests de foco no deben depender de TMDB/GeeSports. */
private fun isRobolectric(): Boolean =
    runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess

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
 * 1. Hero billboard inmersivo con drift, badge «TENDENCIA DE HOY», título, metadatos,
 *    sinopsis y acciones directas con foco D-pad: [Reproducir], [Detalles], [Favorito].
 * 2. Fila 1: "Top 10 en Vision+ Hoy" (Tendencias) con pósteres 2:3 y sello TOP 10.
 * 3. Fila 2: "Novedades y Estrenos" con badges de estado NUEVO y calificación.
 * 4. Fila 3: "Seguir viendo" con avance real y captura.
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
    /** El regreso a una ficha vale una sola vez: se limpia al restaurar o al salir por un hub. */
    onClearReturn: () -> Unit = {},
) {
    val context = LocalContext.current
    val reduced = LocalReducedEffects.current
    val locked = com.chronosbvrx.visionplus.data.kids.KidsMode.locked

    val rows = remember(catalog.personalRows, catalog.rows, locked) {
        val all = catalog.personalRows + catalog.rows
        if (locked) com.chronosbvrx.visionplus.data.kids.KidsMode.kidsRows(all) else all
    }
    val indexForHome = remember(catalog.indexRows, locked) {
        if (locked) com.chronosbvrx.visionplus.data.kids.KidsMode.kidsRows(catalog.indexRows)
        else catalog.indexRows
    }
    LaunchedEffect(Unit) { catalog.refreshPersonal() }

    val noCatalogMessage = when {
        rows.isNotEmpty() -> null
        catalog.loading || catalog.status == XuperStatus.LOADING -> "Cargando tu contenido…"
        else -> "Tu contenido no está disponible ahora mismo. Revisa tu conexión e intenta de nuevo."
    }

    // El curado del Inicio (pool propio + Top 10 + Novedades + filas por tipo + héroe)
    // se calcula en segundo plano: con miles de films del índice, hacerlo en el hilo
    // de composición congelaba el D-pad en gama baja. Se conserva el valor anterior
    // hasta que llega el nuevo (sin parpadeos). Al volver de una ficha la pantalla se
    // recompone desde cero: se siembra con el último curado del proceso para que el
    // primer frame tenga la MISMA estructura y el scroll restaurado no caiga en otra fila.
    val curated by androidx.compose.runtime.produceState(
        if (locked) HomeCurated() else (XuperCatalog.lastHomeCurated ?: HomeCurated()),
        rows,
        indexForHome,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            Popularity.homeCurated(rows, indexForHome).also {
                if (!locked) XuperCatalog.lastHomeCurated = it
            }
        }
    }
    val allAvailableFilms = curated.pool
    val top10Films = curated.top10
    val estrenosFilms = curated.premieres
    val ownKindRows = curated.kindRows
    val themeRows = curated.themeRows

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
    // Primera tarjeta de cada fila compuesta: el rescate del regreso enfoca algo visible
    // aunque la tarjeta exacta ya no exista o su fila esté fuera de pantalla.
    val rowEntry = remember { FocusRequester() }
    val rowsListState = rememberLazyListState()
    var heroActionTick by remember { mutableIntStateOf(0) }
    val homeScope = rememberCoroutineScope()

    // El LazyColumn solo compone lo que se ve: cuando el foco está en la ÚLTIMA fila visible, la
    // flecha Abajo no encuentra ninguna tarjeta a la que saltar (la siguiente fila aún no existe)
    // y el mando se queda atorado ahí. Al enfocar una tarjeta se deja asomado un «pico» de la
    // fila siguiente: con ella compuesta, Abajo entra y el foco nunca se pierde.
    val nextRowPeekPx = with(androidx.compose.ui.platform.LocalDensity.current) { 132.dp.toPx() }
    fun revealNextRowPeek(rowIndex: Int) {
        if (rowIndex < 0) return
        val info = rowsListState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == rowIndex } ?: return
        val below = (item.offset + item.size + nextRowPeekPx) - info.viewportEndOffset
        if (below > 2f) {
            homeScope.launch { runCatching { rowsListState.animateScrollBy(below) } }
            return
        }
        // Simétrico: si la fila es la primera visible (el héroe/hubs ya no están compuestos),
        // se deja asomar el borde de la fila anterior para que «arriba» también tenga destino.
        val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: 0
        val above = info.viewportStartOffset - (item.offset - nextRowPeekPx)
        if (above > 2f && firstVisible > 0) {
            homeScope.launch { runCatching { rowsListState.animateScrollBy(-above) } }
        }
    }

    // Al enfocar una acción del hero (volver arriba con el mando), la lista se ancla al
    // tope: el minimal bring-into-view dejaba el banner a medio scroll y se veía cortado.
    // Durante un regreso a una tarjeta no se toca el scroll: el foco pasa por el hero y
    // debe terminar en LA MISMA tarjeta que se abrió.
    LaunchedEffect(heroActionTick) {
        if (heroActionTick > 0 && returnFilmId == null) {
            runCatching { rowsListState.scrollToItem(0) }
        }
    }

    // Al regresar de la ficha, restaurar foco en la tarjeta exacta (o el héroe, si era su
    // título). El regreso solo vale cuando se llegó aquí abriendo una ficha (el riel tiene
    // su propio destino); la posición de la fila se calcula más abajo, cuando ya están
    // todas las filas (incluido «Seguir viendo», que carga al volver del reproductor).
    val returnPending = returnFilmId != null && railReturn.isEmpty()

    // El primer elemento al cargar obtiene el protagonismo en el billboard
    LaunchedEffect(top10Films, allAvailableFilms) {
        if (featured == null) {
            featured = top10Films.firstOrNull()?.featured()
                ?: allAvailableFilms.firstOrNull()?.featured()
        }
        delay(150)
        if (!returnPending && !FocusRescue.screenHasFocus && (top10Films.isNotEmpty() || allAvailableFilms.isNotEmpty())) {
            landing.request()
        }
    }

    // «Seguir viendo»: se recarga al volver del reproductor
    var continueCards by remember { mutableStateOf<List<ResumeCard>>(emptyList()) }
    // Semilla de «Porque viste X» y el catálogo ya cruzado de las filas personales.
    var lastSeen by remember { mutableStateOf<ResumeEntry?>(null) }
    var homeFilms by remember { mutableStateOf<List<Film>>(emptyList()) }
    fun reloadContinue() {
        homeScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val history = PlaybackHistory.list(context)
            val films = allFilms(rows + indexForHome)
            val matched = PlaybackHistory.continueCards(context, films)
            val visible = if (locked) {
                matched.filter { it.film != null && it.film.kind == "kids" }
            } else {
                matched
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                continueCards = visible
                lastSeen = history.firstOrNull()
                homeFilms = films
            }
        }
    }
    LaunchedEffect(rows, indexForHome) { reloadContinue() }
    val syncAt = com.chronosbvrx.visionplus.data.sync.SyncStore.state.value.lastSyncAt
    LaunchedEffect(syncAt) { if (syncAt > 0) reloadContinue() }

    // Banners diarios del VPS (arte panorámico TMDB) + partidos de GeeSports.
    // Se muestra la copia en disco al instante y se refresca en segundo plano.
    var homeBanners by remember { mutableStateOf<List<HomeBanner>>(emptyList()) }
    var sportsEvents by remember { mutableStateOf<List<GeeSportsSource.GeeEvent>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (!isRobolectric()) {
            homeBanners = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BannerSource.cached(context)
                }
            }.getOrDefault(emptyList())
            runCatching { BannerSource.load(context) }.getOrNull()?.let { homeBanners = it }
        }
    }
    LaunchedEffect(Unit) {
        if (!isRobolectric()) {
            sportsEvents = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    GeeSportsSource.cachedEvents(context)
                }
            }.getOrDefault(emptyList())
            runCatching { GeeSportsSource.refreshEvents(context) }.getOrNull()?.let { sportsEvents = it }
        }
    }
    val heroItems = remember(homeBanners, sportsEvents, homeFilms) {
        buildTvHeroItems(homeBanners, sportsEvents, homeFilms)
    }
    var heroIndex by remember(heroItems) { mutableIntStateOf(0) }
    // Si el usuario está recorriendo las filas, manda la tarjeta enfocada; si no,
    // el hero rota solo por la foto del día y los partidos.
    LaunchedEffect(heroItems, heroIndex, focusFilm) {
        if (focusFilm == null) {
            heroItems.getOrNull(heroIndex)?.let { featured = it.featured() }
        }
    }
    LaunchedEffect(heroItems, focusFilm, reduced) {
        if (focusFilm != null || heroItems.size <= 1) return@LaunchedEffect
        val every = if (reduced) 13_000L else 9_000L
        while (true) {
            delay(every)
            if (focusFilm != null) break
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }

    // Avisos de capítulos nuevos de series seguidas (una vez al día como máximo).
    LaunchedEffect(Unit) {
        homeScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.chronosbvrx.visionplus.data.follow.FollowStore.checkNewEpisodes(context) }
        }
    }
    // «Populares de la semana» (TMDB): una fila, caché de 12 h en el cliente.
    var trendingFilms by remember { mutableStateOf<List<Film>>(emptyList()) }
    LaunchedEffect(Unit) {
        homeScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val films = runCatching {
                com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.trending(40)
            }.getOrDefault(emptyList())
            val visible = if (locked) films.filter { it.kind == "kids" } else films
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { trendingFilms = visible }
        }
    }
    // «Populares de la semana» no repite lo que ya muestran el Top 10 y Novedades:
    // una tarjeta por obra y sin traslape con las filas de arriba (con catálogo chico
    // se conserva la hilera para no dejarla vacía).
    val weeklyFilms = remember(trendingFilms, top10Films, estrenosFilms, themeRows) {
        Popularity.withoutRepeats(
            trendingFilms,
            top10Films + estrenosFilms + themeRows.flatMap { it.films },
        )
    }
    // Las filas por tipo tampoco repiten lo que ya salió en el Top 10, Novedades,
    // temáticas o «Populares de la semana».
    val kindRowsForHome = remember(ownKindRows, weeklyFilms, top10Films, estrenosFilms, themeRows) {
        val shown = top10Films + estrenosFilms + themeRows.flatMap { it.films } + weeklyFilms
        ownKindRows.map { row ->
            row.copy(films = Popularity.withoutRepeats(row.films, shown, minKeep = 4))
        }
    }
    // «Porque viste X»: última obra vista + «Relacionadas» sobre el índice que ya está en
    // memoria. «Tus series · Esta semana»: agenda cacheada cruzada con las series seguidas.
    // Las dos se calculan fuera del hilo de composición (miles de títulos en el índice).
    val becauseRow by androidx.compose.runtime.produceState<BecauseRow?>(
        null, lastSeen, homeFilms, top10Films, estrenosFilms, weeklyFilms, themeRows,
        continueCards, locked,
    ) {
        val seed = lastSeen
        if (seed == null || homeFilms.isEmpty()) {
            value = null
            return@produceState
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val continueIds = continueCards.mapNotNull { it.film?.id }.toHashSet()
            val continueTmdb = continueCards.mapNotNull { it.film?.tmdbId?.takeIf { id -> id > 0 } }.toHashSet()
            val base = HomePersonal.becauseYouWatched(
                seed = seed,
                films = homeFilms,
                rows = indexForHome + rows,
                excludeIds = continueIds,
                excludeTmdb = continueTmdb,
                kidsOnly = locked,
            ) ?: return@withContext null
            val fresh = Popularity.withoutRepeats(
                base.films,
                top10Films + estrenosFilms + themeRows.flatMap { it.films } + weeklyFilms,
                minKeep = HomePersonal.MIN_CARDS,
            )
            if (fresh.size >= HomePersonal.MIN_CARDS) base.copy(films = fresh) else null
        }
    }
    var followCards by remember { mutableStateOf<List<FollowAiring>>(emptyList()) }
    LaunchedEffect(homeFilms, locked) {
        val followed = runCatching {
            com.chronosbvrx.visionplus.data.follow.FollowStore.followed(context)
        }.getOrDefault(emptyMap())
        if (followed.isEmpty() || homeFilms.isEmpty()) {
            followCards = emptyList()
            return@LaunchedEffect
        }
        homeScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val airings = runCatching {
                com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.schedule(7)
            }.getOrDefault(emptyList())
            val cards = HomePersonal.weeklyFollowed(
                followed = followed.keys,
                airings = airings,
                films = homeFilms,
                nowMs = System.currentTimeMillis(),
            )
            val visible = if (locked) cards.filter { it.film.kind == "kids" } else cards
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { followCards = visible }
        }
    }
    // Regreso a la tarjeta exacta con revelado vertical de su fila: el curado llega tarde
    // y con otra estructura, así que primero se desplaza la columna hasta la fila (sin eso
    // la tarjeta ni se compone) y luego se pide el foco; al terminar se limpia el regreso.
    // Las filas salen de [homeRowSlots] — la MISMA lista que pinta el LazyColumn —, así que
    // el índice no puede correrse al agregar una fila.
    val homeSlots = remember(
        featured, top10Films, estrenosFilms, weeklyFilms, continueCards, becauseRow, followCards,
        kindRowsForHome, otherRows, rows.isEmpty(), catalog.loading,
    ) {
        homeRowSlots(
            heroFilmId = featured?.film?.id,
            top10 = top10Films,
            premieres = estrenosFilms,
            themeRows = themeRows,
            trending = weeklyFilms,
            continueCards = continueCards,
            becauseTitle = becauseRow?.title.orEmpty(),
            becauseFilms = becauseRow?.films.orEmpty(),
            followCards = followCards,
            ownKindRows = kindRowsForHome,
            otherRows = otherRows,
            showPlaceholders = rows.isEmpty() && catalog.loading,
            showNoRows = noCatalogMessage != null,
        )
    }
    val returnRowIndex = remember(homeSlots, returnFilmId, returnPending) {
        homeRowIndexOf(homeSlots, if (returnPending) returnFilmId else null)
    }
    rememberReturnFocus(
        filmId = if (returnPending) returnFilmId else null,
        requester = returnRequester,
        landing = landing,
        contentKey = curated.pool.size,
        entryRequester = rowEntry,
        columnState = rowsListState,
        columnIndex = returnRowIndex,
        onDone = onClearReturn,
    )
    val homeLifecycle = LocalLifecycleOwner.current
    DisposableEffect(homeLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadContinue()
        }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }

    val currentFilm = focusFilm ?: featured?.film
    var inFavorites by remember(currentFilm?.id) {
        mutableStateOf(currentFilm?.let { UserLibrary.isFavorite(context, it.id) } ?: false)
    }

    val heroDrift by rememberHeroDrift(reduced, HERO_DRIFT_MS)
    val screenPx = rememberScreenPx()

    // Deja lista la siguiente diapositiva mientras se ve la actual: al girar, el arte
    // entra desde la caché de Coil (sin descargarlo en ese instante).
    LaunchedEffect(heroItems, heroIndex, screenPx) {
        if (heroItems.size <= 1) return@LaunchedEffect
        val next = heroItems[(heroIndex + 1) % heroItems.size].featured()
        if (next.event != null) return@LaunchedEffect
        val url = next.imageUrl
        if (url.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            coil.Coil.imageLoader(context).enqueue(
                heroImageRequest(context, url, screenPx.width, screenPx.height),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(VisionBlack)) {
        // Fondo cinemático inmersivo con drift y degradado suave.
        // En un partido, el fondo es un lienzo deportivo con las crestas y un gradiente.
        Crossfade(targetState = featured, animationSpec = backdropFadeSpec(reduced), label = "bg") { f ->
            if (f?.event != null) {
                TvSportsHeroBackdrop(event = f.event)
            } else {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = heroImageRequest(context, f?.imageUrl, screenPx.width, screenPx.height),
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
        // Tinte ambiental: el fondo toma los tonos del póster destacado (muestra 8×8).
        var ambientHex by remember(featured?.imageUrl) { mutableStateOf(android.graphics.Color.BLACK) }
        var ambientReady by remember(featured?.imageUrl) { mutableStateOf(false) }
        LaunchedEffect(featured?.imageUrl) {
            ambientReady = false
            val url = featured?.imageUrl
            if (url.isNullOrBlank()) return@LaunchedEffect
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(16)
                .allowHardware(false)
                .memoryCacheKey(bannerArtCacheKey(url) + "#ambient16")
                .diskCacheKey(bannerArtCacheKey(url))
                .target(onSuccess = { drawable ->
                    val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        ambientHex = com.chronosbvrx.visionplus.ui.theme.AmbientColor.sample(bmp)
                        ambientReady = true
                    }
                })
                .build()
            runCatching { coil.Coil.imageLoader(context).enqueue(req) }
        }
        if (ambientReady) {
            val ambient = Color(com.chronosbvrx.visionplus.ui.theme.AmbientColor.backgroundFor(ambientHex))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to ambient.copy(alpha = 0.5f)),
                ),
            )
        }

        // Contenido a todo lo ancho: la navegación vive en la barra superior.
        Column(Modifier.fillMaxSize().padding(start = TV_RAIL_CONTENT_START)) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScrollBringIntoView) {
                LazyColumn(
                    state = rowsListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                ) {
                    // Las filas salen de [homeSlots], la MISMA lista que calcula el índice del
                    // regreso: pintar y regresar ya no pueden desincronizarse.
                    homeSlots.forEachIndexed { slotIndex, slot ->
                        when (slot) {
                            // 1. BILLBOARD HERO INTERACTIVO CON ACCIONES DIRECTAS
                            is HomeRowSlot.Billboard -> item(key = slot.key) {
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
                                    onOpenSports = { event ->
                                        DeportesReturn.pickingId = event.id
                                        onGoRoute(Route.Sports)
                                    },
                                    onOpenSportsHub = {
                                        DeportesReturn.pickingId = null
                                        onGoRoute(Route.Sports)
                                    },
                                    onHeroActionFocus = { heroActionTick++ },
                                )
                            }

                    // 2. ACCESOS RÁPIDOS A COLECCIONES Y ESPECIALES (Brand Hubs: Fútbol, Anime, Novelas, Categorías)
                    HomeRowSlot.Hubs -> item(key = slot.key) {
                        TvQuickHubsRow(onGoRoute = onGoRoute)
                    }

                    // Estados de carga o reintento si el catálogo aún no responde
                    HomeRowSlot.Status -> item(key = slot.key) {
                        if (rows.isEmpty() && catalog.loading) {
                            Column(Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
                                GhostRow(count = 5, cardWidth = 140.dp, cardHeight = 210.dp, horizontalPadding = 0.dp)
                                GhostRow(count = 5, cardWidth = 140.dp, cardHeight = 210.dp, horizontalPadding = 0.dp)
                            }
                        } else {
                            val scope = rememberCoroutineScope()
                            Column(Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
                                Text(
                                    noCatalogMessage.orEmpty(),
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

                    // 3. FILA 1: TOP 10 EN VISION+ HOY (TENDENCIAS CON NÚMEROS GIGANTES)
                    is HomeRowSlot.Top10 -> item(key = slot.key) {
                        HomePosterRow(
                            title = "Top 10 en Vision+ Hoy",
                            films = top10Films,
                            isTop10 = true,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // 4. FILA 2: NOVEDADES Y ESTRENOS
                    is HomeRowSlot.Premieres -> item(key = slot.key) {
                        HomePosterRow(
                            title = "Novedades y Estrenos",
                            films = estrenosFilms,
                            isNew = true,
                            icon = VisionIcons.Novelties,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // FILA: POPULARES DE LA SEMANA (TMDB, casada con el catálogo)
                    is HomeRowSlot.Trending -> item(key = slot.key) {
                        HomePosterRow(
                            title = "Populares de la semana",
                            films = weeklyFilms,
                            icon = VisionIcons.WeeklyTrending,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // 5. FILA 3: SEGUIR VIENDO (con barra de avance neón)
                    is HomeRowSlot.Continue -> item(key = slot.key) {
                        ContinueRow(
                            cards = continueCards,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onResume = onResume,
                        )
                    }

                    // 5b. PORQUE VISTE X (personalizada con el último título visto)
                    is HomeRowSlot.Because -> item(key = slot.key) {
                        HomePosterRow(
                            title = HomePersonal.becauseRowTitle(slot.title),
                            films = slot.films,
                            icon = VisionIcons.ContinueWatching,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // 5c. TUS SERIES · ESTA SEMANA (agenda de las series seguidas)
                    is HomeRowSlot.FollowWeek -> item(key = slot.key) {
                        HomeAiringRow(
                            title = HomePersonal.FOLLOW_ROW_TITLE,
                            cards = slot.cards,
                            icon = VisionIcons.Following,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // 6. FILAS PROPIAS POR TIPO (populares, no nicho)
                    is HomeRowSlot.Kind -> item(key = slot.key) {
                        val limitedFilms = remember(slot.row.films) { slot.row.films.take(15) }
                        HomePosterRow(
                            title = slot.row.title,
                            films = limitedFilms,
                            icon = homeRowIcon(slot.row.id),
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    // 7. RESTO DE FILAS: TV EN VIVO Y LAS FUENTES PROPIAS
                    is HomeRowSlot.Other -> item(key = slot.key) {
                        val limitedFilms = remember(slot.row.films) { slot.row.films.take(15) }
                        HomePosterRow(
                            title = slot.row.title,
                            films = limitedFilms,
                            returnRequester = returnRequester,
                            returnFilmId = returnFilmId,
                            entryRequester = rowEntry,
                            onFocusFilm = { focusFilm = it; revealNextRowPeek(slotIndex) },
                            onOpenFilm = onOpenFilm,
                        )
                    }

                    HomeRowSlot.End -> item(key = slot.key) {
                        Spacer(Modifier.height(32.dp))
                    }
                        }
                    }
                }
            }
        }

        // La navegación vive en la barra superior (TvTopBar): el riel ya no se pinta.

        // Campana de actualizaciones del sistema
        var showPluginUpdates by rememberSaveable { mutableStateOf(false) }
        val updatesBadge = appUpdateBadgeCount()
        val tvContext = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(updatesBadge) {
            if (updatesBadge > 0) {
                val latest = com.chronosbvrx.visionplus.data.AppUpdate.latest?.version.orEmpty()
                val skipped = tvContext.getSharedPreferences("updates", 0).getString("skipped", "")
                // Con versión mínima forzada el "skip" se ignora: la oferta vuelve cada apertura.
                if (latest.isNotBlank() &&
                    (com.chronosbvrx.visionplus.data.AppUpdate.forced(tvContext) || skipped != latest)
                ) showPluginUpdates = true
            }
        }
        if (showPluginUpdates) {
            TvAppUpdatesDialog(onDismiss = {
                showPluginUpdates = false
                // Con actualización forzada NO se recuerda el "skip".
                if (!com.chronosbvrx.visionplus.data.AppUpdate.forced(tvContext)) {
                    com.chronosbvrx.visionplus.data.AppUpdate.latest?.version?.let { v ->
                        tvContext.getSharedPreferences("updates", 0).edit().putString("skipped", v).apply()
                    }
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
    TvQuickHub("Deportes en vivo", "EN DIRECTO", Icons.Default.SportsSoccer, Route.Sports),
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
        TvRowLabel("Colecciones y Especiales", 22.dp, icon = VisionIcons.Collections)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                modifier = Modifier,
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
 * Una fila del Inicio, en el orden EXACTO en que se pinta. Es la fuente única del
 * `LazyColumn` y del índice del regreso: agregar una fila ya no puede desincronizar el
 * revelado del scroll (el fallo de «Populares de la semana», que corrió el índice y dejó
 * el mando muerto al volver de una serie).
 */
internal sealed interface HomeRowSlot {
    val key: String

    /** ¿Esta fila contiene la tarjeta de [filmId]? (para el índice del regreso). */
    fun containsFilm(filmId: String): Boolean

    data class Billboard(val filmId: String?) : HomeRowSlot {
        override val key: String get() = "billboard"
        override fun containsFilm(filmId: String) = filmId == this.filmId && filmId.isNotBlank()
    }

    data object Hubs : HomeRowSlot {
        override val key: String get() = "home_quick_hubs"
        override fun containsFilm(filmId: String) = false
    }

    data object Status : HomeRowSlot {
        override val key: String get() = "home_status"
        override fun containsFilm(filmId: String) = false
    }

    data class Top10(val films: List<Film>) : HomeRowSlot {
        override val key: String get() = "home_top10"
        override fun containsFilm(filmId: String) = films.any { it.id == filmId }
    }

    data class Premieres(val films: List<Film>) : HomeRowSlot {
        override val key: String get() = "home_estrenos"
        override fun containsFilm(filmId: String) = films.any { it.id == filmId }
    }

    data class Trending(val films: List<Film>) : HomeRowSlot {
        override val key: String get() = "home_trending_week"
        override fun containsFilm(filmId: String) = films.any { it.id == filmId }
    }

    data class Continue(val cards: List<ResumeCard>) : HomeRowSlot {
        override val key: String get() = "continue"
        override fun containsFilm(filmId: String) =
            cards.any { it.film?.id == filmId || it.entry.key == filmId || it.entry.xtvCode == filmId }
    }

    /** «Porque viste X»: una fila de recomendación ligada al último título visto. */
    data class Because(val title: String, val films: List<Film>) : HomeRowSlot {
        override val key: String get() = "home_because"
        override fun containsFilm(filmId: String) = films.any { it.id == filmId }
    }

    /** «Tus series · Esta semana»: emisiones de las series que el usuario sigue. */
    data class FollowWeek(val cards: List<FollowAiring>) : HomeRowSlot {
        override val key: String get() = "home_follow"
        override fun containsFilm(filmId: String) = cards.any { it.film.id == filmId }
    }

    /** Una fila propia por tipo (home-kind-*): su clave es el id de la fila, como al pintar. */
    data class Kind(val row: CatalogRow) : HomeRowSlot {
        override val key: String get() = row.id
        override fun containsFilm(filmId: String) = row.films.any { it.id == filmId }
    }

    /** El resto de filas del catálogo: misma regla de clave que al pintar. */
    data class Other(val row: CatalogRow) : HomeRowSlot {
        override val key: String get() = row.id
        override fun containsFilm(filmId: String) = row.films.any { it.id == filmId }
    }

    data object End : HomeRowSlot {
        override val key: String get() = "end_spacer"
        override fun containsFilm(filmId: String) = false
    }
}

/**
 * Las filas del Inicio en su orden real de pintado. Réplica EXACTA de lo que compone el
 * `LazyColumn` —ahora es la misma lista que lo alimenta—: billboard, hubs, estado
 * (fantasmas/reintento), Top 10, Estrenos, Populares de la semana, Seguir viendo, filas
 * propias, resto y el espaciador final.
 */
internal fun homeRowSlots(
    heroFilmId: String?,
    top10: List<Film>,
    premieres: List<Film>,
    trending: List<Film>,
    continueCards: List<ResumeCard>,
    ownKindRows: List<CatalogRow>,
    otherRows: List<CatalogRow>,
    showPlaceholders: Boolean,
    showNoRows: Boolean,
    becauseTitle: String = "",
    becauseFilms: List<Film> = emptyList(),
    followCards: List<FollowAiring> = emptyList(),
    themeRows: List<CatalogRow> = emptyList(),
): List<HomeRowSlot> = buildList {
    add(HomeRowSlot.Billboard(heroFilmId))
    add(HomeRowSlot.Hubs)
    if (showPlaceholders || showNoRows) add(HomeRowSlot.Status)
    if (top10.isNotEmpty()) add(HomeRowSlot.Top10(top10))
    if (premieres.isNotEmpty()) add(HomeRowSlot.Premieres(premieres))
    // Las temáticas (Día de Muertos, Halloween, terror/comedia…) van después de
    // Novedades y antes de la hilera semanal: se ven sin enterrarlas al fondo.
    themeRows.forEach { add(HomeRowSlot.Kind(it)) }
    if (trending.isNotEmpty()) add(HomeRowSlot.Trending(trending))
    if (continueCards.isNotEmpty()) add(HomeRowSlot.Continue(continueCards))
    if (becauseFilms.isNotEmpty() && becauseTitle.isNotBlank()) {
        add(HomeRowSlot.Because(becauseTitle, becauseFilms))
    }
    if (followCards.isNotEmpty()) add(HomeRowSlot.FollowWeek(followCards))
    ownKindRows.forEach { add(HomeRowSlot.Kind(it)) }
    otherRows.forEach { add(HomeRowSlot.Other(it)) }
    add(HomeRowSlot.End)
}

/** Índice vertical de la fila que contiene la tarjeta del regreso (-1 si no está). */
internal fun homeRowIndexOf(slots: List<HomeRowSlot>, returnFilmId: String?): Int {
    if (returnFilmId == null) return -1
    return slots.indexOfFirst { it.containsFilm(returnFilmId) }
}

/**
 * Compatibilidad: el índice del regreso a partir de las piezas del Inicio (los guardianes
 * puros lo usan). Es un envoltorio de [homeRowSlots] + [homeRowIndexOf].
 */
internal fun homeReturnRowIndex(
    returnFilmId: String?,
    heroFilmId: String?,
    top10: List<Film>,
    premieres: List<Film>,
    trending: List<Film>,
    continueCards: List<ResumeCard>,
    ownKindRows: List<CatalogRow>,
    otherRows: List<CatalogRow>,
    showPlaceholders: Boolean,
    showNoRows: Boolean,
    becauseTitle: String = "",
    becauseFilms: List<Film> = emptyList(),
    followCards: List<FollowAiring> = emptyList(),
    themeRows: List<CatalogRow> = emptyList(),
): Int = homeRowIndexOf(
    homeRowSlots(
        heroFilmId = heroFilmId,
        top10 = top10,
        premieres = premieres,
        trending = trending,
        continueCards = continueCards,
        ownKindRows = ownKindRows,
        otherRows = otherRows,
        showPlaceholders = showPlaceholders,
        showNoRows = showNoRows,
        becauseTitle = becauseTitle,
        becauseFilms = becauseFilms,
        followCards = followCards,
        themeRows = themeRows,
    ),
    returnFilmId,
)

/**
 * Billboard Cinemático del Hero con badge, metadatos y botones directos interactivos.
 * Cuando la diapositiva es un partido, cambia el marcador y las acciones (Ver en vivo /
 * Agenda) sin cambiar el foco del primer botón.
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
    onOpenSports: (GeeSportsSource.GeeEvent) -> Unit = {},
    onOpenSportsHub: () -> Unit = {},
    onHeroActionFocus: () -> Unit = {},
) {
    if (featured == null) return
    val event = featured.event
    if (landing != null) RescueAnchor(landing)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 20.dp),
    ) {
        // Badge superior distintivo: cine azul, deportes rojo en vivo.
        val badgeBackground = if (event?.live == true) {
            Brush.horizontalGradient(listOf(Color(0xFFC0292E), Color(0xFF7A1013)))
        } else {
            Brush.horizontalGradient(listOf(VisionBlue, VisionBlueDark))
        }
        val badgeText = if (event != null) {
            featured.eyebrow.ifBlank { "DEPORTES · EN VIVO" }
        } else {
            listOf(featured.eyebrow, featured.badge)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
                .ifBlank { "TENDENCIA DE HOY" }
        }
        Box(
            modifier = Modifier
                .background(badgeBackground, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    if (event != null) Icons.Default.SportsSoccer else VisionIcons.WeeklyTrending,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = badgeText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                    ),
                )
                if (event?.live == true) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                }
            }
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

        // Metadatos (Año, Calificación, Género / Temporadas o liga y estado)
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

        if (event != null) {
            TvSportsScoreboard(event)
        } else if (featured.subtitle.isNotBlank()) {
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
            if (event != null) {
                TvCompactAction(
                    label = if (event.live) "Ver en vivo" else "Ver partido",
                    icon = Icons.Default.SportsSoccer,
                    primary = true,
                    modifier = if (landing != null) Modifier.landingFocus(landing) else Modifier,
                    onClick = { onOpenSports(event) },
                    onFocusChanged = { focused -> if (focused) onHeroActionFocus() },
                )
                TvCompactAction(
                    label = "Agenda",
                    icon = Icons.Default.Info,
                    onClick = onOpenSportsHub,
                    onFocusChanged = { focused -> if (focused) onHeroActionFocus() },
                )
            } else {
                TvCompactAction(
                    label = "Reproducir",
                    icon = VisionIcons.Play,
                    primary = true,
                    modifier = when {
                        isHeroTarget && returnRequester != null -> Modifier.focusRequester(returnRequester)
                        landing != null -> Modifier.landingFocus(landing)
                        else -> Modifier
                    },
                    onClick = { currentFilm?.let(onOpenFilm) },
                    onFocusChanged = { focused -> if (focused) onHeroActionFocus() },
                )
                TvCompactAction(
                    label = "Detalles",
                    icon = Icons.Default.Info,
                    onClick = { currentFilm?.let(onOpenFilm) },
                    onFocusChanged = { focused -> if (focused) onHeroActionFocus() },
                )
                TvCompactAction(
                    label = if (inFavorites) "En Favoritos" else "Favorito",
                    icon = if (inFavorites) VisionIcons.Favorite else VisionIcons.FavoriteBorder,
                    onClick = onToggleFavorite,
                    onFocusChanged = { focused -> if (focused) onHeroActionFocus() },
                )
            }
        }
    }
}

@Composable
private fun TvSportsScoreboard(event: GeeSportsSource.GeeEvent) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvTeamSide(event.homeName, event.homeLogo, alignEnd = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val score = when {
                event.scoreHome != null && event.scoreAway != null -> "${event.scoreHome}  ${event.scoreAway}"
                event.scoreHome != null -> "${event.scoreHome}  -"
                event.scoreAway != null -> "-  ${event.scoreAway}"
                else -> "VS"
            }
            Text(
                text = score,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = event.detail.ifBlank { if (event.live) "EN VIVO" else event.time.take(5) },
                style = MaterialTheme.typography.titleSmall,
                color = if (event.live) Color(0xFFFF8A8D) else VisionTextSecondary,
                maxLines = 1,
            )
        }
        TvTeamSide(event.awayName, event.awayLogo, alignEnd = false)
    }
}

@Composable
private fun TvTeamSide(name: String, logo: String, alignEnd: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (alignEnd) {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.width(110.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            } else {
                Text(
                    text = name.trim().split(' ').filter { it.isNotBlank() }.take(2)
                        .joinToString("") { it.take(1).uppercase() }.ifBlank { "VS" },
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (!alignEnd) {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}

@Composable
private fun TvSportsHeroBackdrop(event: GeeSportsSource.GeeEvent) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF04121D), Color(0xFF0A2238), VisionBlack),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvAmbientCrest(event.homeLogo)
            TvAmbientCrest(event.awayLogo)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(VisionBlue.copy(alpha = 0.15f), Color.Transparent),
                ),
            ),
        )
    }
}

@Composable
private fun TvAmbientCrest(logo: String) {
    if (logo.isBlank()) return
    AsyncImage(
        model = logo,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(190.dp).graphicsLayer { alpha = 0.14f },
    )
}

/**
 * Fila moderna con pósteres verticales 2:3, números Top 10 y badges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomePosterRow(
    title: String,
    films: List<Film>,
    isTop10: Boolean = false,
    isNew: Boolean = false,
    icon: ImageVector? = null,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    entryRequester: FocusRequester? = null,
    onFocusFilm: (Film) -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    val safeFilms = remember(films) { Popularity.dedupe(films).take(15) }
    if (safeFilms.isEmpty()) return

    // Medidas calculadas desde la altura real de la tele: en 540 dp entran 3 filas completas
    // (antes, con pósteres fijos de 140×210, solo cabían 2).
    val metrics = rememberTvRowMetrics()

    // Al volver de una ficha, la fila se desplaza sola hasta la tarjeta abierta: así el foco
    // encuentra algo ya dibujado aunque el scroll restaurado no la dejara en la ventana.
    val rowState = rememberLazyListState()
    val returnIndex = remember(safeFilms, returnFilmId) {
        if (returnFilmId == null) -1 else safeFilms.indexOfFirst { it.id == returnFilmId }
    }
    LaunchedEffect(returnIndex, safeFilms) { rowState.revealReturnIndex(returnIndex) }

    Column(modifier = Modifier.padding(top = metrics.topPadding)) {
        TvRowLabel(title, metrics.labelHeight, icon = icon, topTen = isTop10)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                state = rowState,
                modifier = Modifier,
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
                    Column(Modifier.width(metrics.cardWidth)) {
                        TvPosterCard(
                            title = film.title,
                            imageUrl = film.posterUrl,
                            score = film.score,
                            badge = badge,
                            rank = rank,
                            cardWidth = metrics.cardWidth,
                            modifier = when {
                                isReturn -> Modifier.focusRequester(returnRequester)
                                idx == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                                else -> Modifier
                            },
                            onFocus = { onFocusFilm(film) },
                            onClick = { onOpenFilm(film) },
                        )
                        // El título vive en una caja de alto fijo: aunque la tele tenga la letra
                        // escalada, no empuja la fila y las 3 siguen entrando.
                        Box(Modifier.padding(top = metrics.titleGap).height(metrics.titleHeight)) {
                            Text(
                                text = film.title,
                                style = if (metrics.compact) MaterialTheme.typography.bodySmall
                                else MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(metrics.bottomGap))
    }
}

/**
 * «Tus series · Esta semana»: póster de la serie, su nombre y el capítulo que viene.
 * Comparte las medidas de [HomePosterRow] para no romper las 3 filas del Inicio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAiringRow(
    title: String,
    cards: List<FollowAiring>,
    icon: ImageVector? = null,
    returnRequester: FocusRequester? = null,
    returnFilmId: String? = null,
    entryRequester: FocusRequester? = null,
    onFocusFilm: (Film) -> Unit,
    onOpenFilm: (Film) -> Unit,
) {
    if (cards.isEmpty()) return
    val metrics = rememberTvRowMetrics()
    val rowState = rememberLazyListState()
    val returnIndex = remember(cards, returnFilmId) {
        if (returnFilmId == null) -1 else cards.indexOfFirst { it.film.id == returnFilmId }
    }
    LaunchedEffect(returnIndex, cards) { rowState.revealReturnIndex(returnIndex) }
    Column(modifier = Modifier.padding(top = metrics.topPadding)) {
        TvRowLabel(title, metrics.labelHeight, icon = icon)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                state = rowState,
                modifier = Modifier,
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(cards, key = { _, card -> card.key }) { idx, card ->
                    val isReturn = returnRequester != null && card.film.id == returnFilmId
                    Column(Modifier.width(metrics.cardWidth)) {
                        TvPosterCard(
                            title = card.film.title,
                            imageUrl = card.film.posterUrl,
                            score = card.film.score,
                            badge = if (card.today) "HOY" else null,
                            cardWidth = metrics.cardWidth,
                            modifier = when {
                                isReturn -> Modifier.focusRequester(returnRequester)
                                idx == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                                else -> Modifier
                            },
                            onFocus = { onFocusFilm(card.film) },
                            onClick = { onOpenFilm(card.film) },
                        )
                        Box(Modifier.padding(top = metrics.titleGap).height(metrics.titleHeight)) {
                            Text(
                                text = card.film.title,
                                style = if (metrics.compact) MaterialTheme.typography.bodySmall
                                else MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = card.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(metrics.bottomGap))
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
    entryRequester: FocusRequester? = null,
    onFocusFilm: (Film) -> Unit,
    onResume: (ResumeCard) -> Unit,
) {
    if (cards.isEmpty()) return
    // Al volver, la fila se desplaza sola hasta la tarjeta abierta (el foco solo cae en lo dibujado).
    val rowState = rememberLazyListState()
    val returnIndex = remember(cards, returnFilmId) {
        if (returnFilmId == null) {
            -1
        } else {
            cards.indexOfFirst { card ->
                card.film?.id == returnFilmId || card.entry.key == returnFilmId || card.entry.xtvCode == returnFilmId
            }
        }
    }
    LaunchedEffect(returnIndex, cards) { rowState.revealReturnIndex(returnIndex) }
    val metrics = rememberTvRowMetrics()
    Column(modifier = Modifier.padding(top = metrics.topPadding)) {
        TvRowLabel("Seguir viendo", metrics.labelHeight, icon = VisionIcons.ContinueWatching)
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvPivot) {
            LazyRow(
                state = rowState,
                modifier = Modifier,
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(cards, key = { _, card -> card.entry.key }) { idx, card ->
                    val film = card.film
                    val title = film?.title ?: card.entry.title
                    val isReturn = returnRequester != null && (film?.id == returnFilmId || card.entry.key == returnFilmId || card.entry.xtvCode == returnFilmId)
                    Column(Modifier.width(196.dp)) {
                        TvLandscapeCard(
                            title = title,
                            imageUrl = film?.posterUrl ?: card.entry.posterUrl.takeIf { it.isNotBlank() },
                            cardHeight = 110.dp,
                            modifier = when {
                                isReturn -> Modifier.focusRequester(returnRequester)
                                idx == 0 && entryRequester != null -> Modifier.focusRequester(entryRequester)
                                else -> Modifier
                            },
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
        Spacer(Modifier.height(metrics.bottomGap))
    }
}

/**
 * Petición del arte del hero con clave estable. El `?v=` del VPS cambia cada día para
 * forzar un edge sano de TMDB; Coil, en cambio, reutiliza la misma imagen por su ruta.
 */
private fun heroImageRequest(
    context: android.content.Context,
    url: String?,
    width: Int,
    height: Int,
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(url)
        .crossfade(0)
        .size(width, height)
    if (!url.isNullOrBlank()) {
        val key = bannerArtCacheKey(url)
        builder.memoryCacheKey(key).diskCacheKey(key)
    }
    return builder.build()
}
