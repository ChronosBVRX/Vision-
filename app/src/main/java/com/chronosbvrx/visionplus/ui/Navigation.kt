package com.chronosbvrx.visionplus.ui

import androidx.compose.runtime.mutableStateListOf
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.ResumeCard

/** Every place the app can show. */
sealed interface Route {
    data object Home : Route
    data object Categories : Route
    data object Library : Route
    data object Downloads : Route
    data object Live : Route
    data object Sports : Route
    data object Settings : Route
    data object About : Route
    data object Search : Route
    data object Assistant : Route
    /** El control remoto por LAN (teléfono → tele). */
    data object Remote : Route
    data class Title(val film: Film) : Route
    data class Player(val film: Film, val episode: EpisodePlay? = null, val resumeMs: Long = 0) : Route
    data class LivePlayer(
        val channel: LiveChannel,
        val channelCodes: List<String> = emptyList(),
        val groupTitle: String? = null,
    ) : Route

    /** Una sección del riel de TV: resuelve su hilera por id (o por título) del índice. */
    data class Category(val key: String, val title: String) : Route

    /** Una sección del riel servida por hileras de género propias (`/v1/section/{name}`). */
    data class Section(val name: String, val title: String) : Route
}

/**
 * El capítulo exacto a reproducir desde una ficha (serie con temporadas): el playCode del
 * capítulo más el contexto para el "siguiente capítulo" sin salir del reproductor.
 */
data class EpisodePlay(
    val code: String,
    val titleSuffix: String = "",
    val seriesId: String = "",
    val episodeIds: List<String> = emptyList(),
    val episodeNames: List<String> = emptyList(),
    val episodeIndex: Int = 0,
    val seasonIds: List<String> = emptyList(),
    val seasonNames: List<String> = emptyList(),
    val seasonIndex: Int = 0,
    /** Al reanudar desde «Seguir viendo»: el minuto donde quedó. */
    val resumeMs: Long = 0,
    /** Duración exacta en ms si la ficha del portal la declaró. */
    val durationMs: Long = 0L,
)

/** A tiny back stack: the app has no deep links or process restore to care about. */
class Navigator(start: List<Route>) {
    val stack = mutableStateListOf<Route>().apply { addAll(start) }

    val current: Route get() = stack.last()

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack += route
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** A top-level section: Home underneath, the section on top (Home alone for Home). */
    fun goTab(route: Route) {
        stack.clear()
        stack += Route.Home
        if (route != Route.Home) stack += route
    }
}

/**
 * «Seguir viendo» → Player con el minuto donde quedó: en XTV se re-resuelve la licencia
 * con su contexto de capítulos; en el resto se reabre directo.
 */
fun resumeRoute(card: ResumeCard): Route.Player {
    val e = card.entry
    val durMin = if (e.durationMs > 0) (e.durationMs / 60_000L).toInt().coerceAtLeast(1) else 0
    return if (e.xtvCode.isNotBlank()) {
        val baseFilm = card.film
        val film = if (baseFilm != null && baseFilm.durationMin > 0) {
            baseFilm
        } else {
            baseFilm?.copy(durationMin = durMin) ?: Film(
                id = "xtv:${e.xtvCode}", title = e.title, year = 0, durationMin = durMin,
                synopsis = "", posterUrl = e.posterUrl, videoUrl = "xtv:${e.xtvCode}", sourceUrl = "xtv",
            )
        }
        Route.Player(
            film,
            EpisodePlay(
                code = e.xtvCode,
                // Historiales viejos pueden traer seriesId con 0-1 capítulos: sin 2+ es película.
                seriesId = if (e.episodeIds.size > 1 || e.seasonIds.size > 1) e.seriesId else "",
                episodeIds = if (e.episodeIds.size > 1 || e.seasonIds.size > 1) e.episodeIds else emptyList(),
                episodeIndex = if (e.episodeIds.size > 1 || e.seasonIds.size > 1) e.episodeIndex else 0,
                seasonIds = if (e.episodeIds.size > 1 || e.seasonIds.size > 1) e.seasonIds else emptyList(),
                seasonNames = if (e.episodeIds.size > 1 || e.seasonIds.size > 1) e.seasonNames else emptyList(),
                seasonIndex = if (e.episodeIds.size > 1 || e.seasonIds.size > 1) e.seasonIndex else 0,
                resumeMs = e.positionMs,
                durationMs = e.durationMs,
            ),
        )
    } else {
        val baseFilm = card.film
        val film = if (baseFilm != null && baseFilm.durationMin > 0) {
            baseFilm
        } else {
            baseFilm?.copy(durationMin = durMin) ?: Film(
                id = e.key, title = e.title, year = 0, durationMin = durMin,
                synopsis = "", posterUrl = e.posterUrl, videoUrl = e.videoUrl, sourceUrl = "",
            )
        }
        Route.Player(film, resumeMs = e.positionMs)
    }
}
