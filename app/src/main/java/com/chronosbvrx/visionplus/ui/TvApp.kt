package com.chronosbvrx.visionplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.XuperCatalog
import com.chronosbvrx.visionplus.ui.about.TvAboutScreen
import com.chronosbvrx.visionplus.ui.player.LivePlayerScreen
import com.chronosbvrx.visionplus.ui.player.PlayerScreen
import com.chronosbvrx.visionplus.ui.tv.FocusRescue
import com.chronosbvrx.visionplus.ui.tv.TvCategoriesScreen
import com.chronosbvrx.visionplus.ui.tv.TvHomeScreen
 
import com.chronosbvrx.visionplus.ui.tv.TvLiveScreen
import com.chronosbvrx.visionplus.ui.tv.TvRailItem
import com.chronosbvrx.visionplus.ui.tv.TvSearchScreen
import com.chronosbvrx.visionplus.ui.tv.TvSettingsScreen
import com.chronosbvrx.visionplus.ui.tv.TvSportsScreen
import com.chronosbvrx.visionplus.ui.tv.TvCategoryScreen
import com.chronosbvrx.visionplus.ui.tv.TvLibraryScreen
import com.chronosbvrx.visionplus.ui.tv.TvTitleScreen
import com.chronosbvrx.visionplus.ui.tv.landingFocus
import com.chronosbvrx.visionplus.ui.tv.rememberLandingFocus

/** The TV's shell: Home with its rail, and every other place full screen with Back to return. */
@Composable
fun TvApp(catalog: XuperCatalog, nav: Navigator) {
    val route = nav.current
    BackHandler(enabled = nav.stack.size > 1 && route !is Route.Player && route !is Route.LivePlayer) { nav.pop() }
    // Each place keeps its saved state (scroll, the card Back returns to) while another one is on top.
    val saved = rememberSaveableStateHolder()
    // Al volver: el riel recuerda su último botón y las secciones, la última tarjeta abierta.
    var railReturn by rememberSaveable { mutableStateOf("") }
    var filmReturn by rememberSaveable { mutableStateOf<String?>(null) }
    // Red definitiva contra el foco huérfano al volver: la pantalla recompuesta ya re-pide
    // su foco sola, pero si a los 300 ms el mando sigue sin nada enfocado, el destino de
    // la pantalla lo toma. Solo actúa sin foco (nunca pelea con el usuario).
    LaunchedEffect(nav.stack.size) {
        delay(300)
        repeat(10) {
            if (FocusRescue.screenHasFocus) return@LaunchedEffect
            FocusRescue.target?.request()
            delay(100)
        }
    }

    Box(Modifier.fillMaxSize().onFocusChanged { FocusRescue.screenHasFocus = it.hasFocus }) {
        saved.SaveableStateProvider(route.toString()) {
            TvRoute(
                catalog = catalog,
                nav = nav,
                route = route,
                railReturn = railReturn,
                onRailPick = { railReturn = it },
                filmReturn = filmReturn,
                onFilmPick = { filmReturn = it },
            )
        }
    }
}

@Composable
private fun TvRoute(
    catalog: XuperCatalog,
    nav: Navigator,
    route: Route,
    railReturn: String,
    onRailPick: (String) -> Unit,
    filmReturn: String?,
    onFilmPick: (String) -> Unit,
) {
    val rows = catalog.rows
    // El índice remoto manda en Categorías y Búsqueda; Home conserva las filas curadas.
    val indexRows = catalog.indexRows.ifEmpty { rows }
    // El foco del riel se re-pide al volver (la clave cambia con la ruta).
    val railLanding = rememberLandingFocus("$railReturn|$route")
    fun rail(icon: ImageVector, label: String, target: Route): TvRailItem = TvRailItem(
        icon,
        label,
        onClick = {
            onRailPick(label)
            nav.push(target)
        },
        modifier = if (label == railReturn) Modifier.landingFocus(railLanding) else Modifier,
    )
    when (route) {
        Route.Home -> TvHomeScreen(
            catalog = catalog,
            railItems = listOf(
                rail(Icons.Default.Search, "Buscar", Route.Search),
                rail(Icons.Default.Movie, "Películas", Route.Section("movies", "Películas")),
                rail(Icons.Default.Theaters, "Series", Route.Section("series", "Series")),
                rail(Icons.Default.AutoStories, "Novelas", Route.Section("novelas", "Novelas")),
                rail(Icons.Default.Animation, "Anime", Route.Section("anime", "Anime")),
                rail(Icons.Default.Bookmark, "Mi lista", Route.Library),
                rail(Icons.Default.LiveTv, "TV en vivo", Route.Live),
                rail(Icons.Default.SportsSoccer, "Fútbol", Route.Sports),
            ),
            bottomItems = listOf(rail(Icons.Default.Settings, "Opciones", Route.Settings)),
            railReturn = railReturn,
            onOpenFilm = {
                // Abrir una ficha desde Inicio borra la memoria del riel: al volver manda la tarjeta.
                onRailPick("")
                nav.push(Route.Title(it))
            },
            onOpenLive = { nav.push(Route.Live) },
            onOpenCategories = { nav.push(Route.Categories) },
            onResume = { nav.push(resumeRoute(it)) },
        )
        is Route.Title -> TvTitleScreen(route.film, onPlay = { ep -> nav.push(Route.Player(route.film, ep)) })
        is Route.Player -> PlayerScreen(route.film, isTv = true, onBack = { nav.pop() }, episode = route.episode, resumeMs = route.resumeMs)
        is Route.LivePlayer -> LivePlayerScreen(
            channel = route.channel,
            isTv = true,
            channelCodes = route.channelCodes,
            groupTitle = route.groupTitle,
            onBack = { nav.pop() },
        )
        Route.Search -> TvSearchScreen(
            indexRows,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
            returnFilmId = filmReturn,
        )
        Route.Categories -> TvCategoriesScreen(indexRows, onOpenFilm = { nav.push(Route.Title(it)) })
        is Route.Category -> TvCategoryScreen(
            indexRows,
            route.key,
            route.title,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
        )
        is Route.Section -> TvCategoryScreen(
            indexRows,
            "",
            route.title,
            section = route.name,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
        )
        Route.Library -> TvLibraryScreen(
            catalog,
            returnFilmId = filmReturn,
            onOpenFilm = {
                onFilmPick(it.id)
                nav.push(Route.Title(it))
            },
        )
        Route.Live -> TvLiveScreen(onPlay = { ch, codes, scope ->
            nav.push(Route.LivePlayer(ch, channelCodes = codes, groupTitle = scope))
        })
        Route.Sports -> TvSportsScreen(onPlay = { nav.push(Route.LivePlayer(it)) })
        Route.Settings -> TvSettingsScreen()
        Route.About -> TvAboutScreen()
        else -> Unit
    }
}
