package com.chronosbvrx.visionplus.ui

import android.content.Context
import android.net.Uri
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.shouldKeep
import com.chronosbvrx.visionplus.data.launcher.LauncherLinks
import com.chronosbvrx.visionplus.data.launcher.WatchLink
import com.chronosbvrx.visionplus.data.remote.remoteLiveChannel
import com.chronosbvrx.visionplus.data.xuper.X4KApi

/**
 * El deep link del launcher de TV (`visionplus://watch/<clave>`) → la ruta que reanuda la
 * película o el capítulo exacto donde quedó.
 *
 * Si el historial ya se podó (o se limpió), el enlace trae de respaldo el playCode XTV o la
 * videoUrl y se abre desde el principio para que la tarjeta nunca quede muerta. Cuando el
 * control remoto manda un capítulo, el enlace trae su contexto de serie (`s`/`sx`/`sl`/`sn`):
 * la tele abre ESE capítulo ya con su serie alrededor (siguiente capítulo y selector listos).
 */
internal fun launcherRouteFor(context: Context, uri: Uri?): Route? {
    val link = LauncherLinks.parse(uri) ?: return null
    val fromHome = uri?.getQueryParameter("from") == "home"
    // La orden del teléfono trae su propio minuto: manda sobre el historial de ESTA tele (el
    // usuario acaba de pedir «continuar aquí» desde el otro equipo). Sin minuto, el historial
    // local sigue teniendo la primera palabra, como siempre.
    if (link.resumeMs > 0) return routeFromLink(link, link.resumeMs)?.markFromHome(fromHome)
    val entry = PlaybackHistory.get(context, link.key)
        ?.takeIf { shouldKeep(it.positionMs, it.durationMs) }
    if (entry != null) return resumeRoute(ResumeCard(entry, film = null, progress = 0f)).markFromHome(fromHome)
    return routeFromLink(link, 0)?.markFromHome(fromHome)
}

/** Marca una ruta de reproducción como abierta desde Vision+ Home (sin tocar rutas ajenas). */
private fun Route.markFromHome(fromHome: Boolean): Route = when {
    !fromHome -> this
    this is Route.Player -> copy(launcherOrigin = true)
    this is Route.LivePlayer -> copy(launcherOrigin = true)
    else -> this
}

/**
 * `visionplus://live/<código>?t=…&p=…&c=<código1>|<código2>…&g=<rótulo>`: el canal que abrió
 * Vision+ Home. `c` es el menú filtrado desde el que se lanzó (México, Favoritos, Todos): el
 * reproductor hace zapping SOLO dentro de esa lista, no sobre el catálogo entero. Sin `c`
 * (un enlace viejo del teléfono o de una tarjeta suelta), se conserva el zapping global.
 */
internal fun liveRouteFor(context: Context, uri: Uri?): Route? {
    if (uri == null) return null
    val code = uri.pathSegments.lastOrNull().orEmpty().trim()
    if (code.isBlank()) return null
    val name = uri.getQueryParameter("t").orEmpty()
    val logo = uri.getQueryParameter("p")
    val cached = runCatching { X4KApi.cachedLiveChannels(context) }.getOrDefault(emptyList())
    val channel = remoteLiveChannel(code, name, logo, cached) ?: return null
    val codes = uri.getQueryParameter("c")
        ?.split('|')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        .orEmpty()
    val scope = uri.getQueryParameter("g")?.trim()?.takeIf { it.isNotBlank() }
    return Route.LivePlayer(
        channel,
        channelCodes = codes,
        groupTitle = scope,
        launcherOrigin = uri.getQueryParameter("from") == "home",
    )
}

/** La ruta que pide el enlace, sin consultar el historial: película, capítulo o URL directa. */
private fun routeFromLink(link: WatchLink, resumeMs: Long): Route? {
    val title = link.title.ifBlank { "Vision+" }
    if (link.code.isNotBlank()) {
        val film = Film(
            id = "xtv:${link.code}", title = title, year = 0, durationMin = 0, synopsis = "",
            posterUrl = link.posterUrl, videoUrl = "xtv:${link.code}", sourceUrl = "xtv",
        )
        if (link.seriesId.isNotBlank()) {
            return Route.Player(
                film,
                EpisodePlay(
                    code = link.code,
                    seriesId = link.seriesId,
                    seasonIds = link.seasonIds,
                    seasonNames = link.seasonNames,
                    seasonIndex = link.seasonIndex,
                    resumeMs = resumeMs,
                ),
            )
        }
        return Route.Player(film, resumeMs = resumeMs)
    }
    if (link.videoUrl.isNotBlank()) {
        return Route.Player(
            Film(
                id = link.key, title = title, year = 0, durationMin = 0, synopsis = "",
                posterUrl = link.posterUrl, videoUrl = link.videoUrl, sourceUrl = "",
            ),
            resumeMs = resumeMs,
        )
    }
    return null
}
