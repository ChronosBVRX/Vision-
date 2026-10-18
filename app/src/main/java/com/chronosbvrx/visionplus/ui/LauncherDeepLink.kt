package com.chronosbvrx.visionplus.ui

import android.content.Context
import android.net.Uri
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.ResumeCard
import com.chronosbvrx.visionplus.data.launcher.LauncherLinks

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
    val entry = PlaybackHistory.get(context, link.key)
    if (entry != null) return resumeRoute(ResumeCard(entry, film = null, progress = 0f))
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
                ),
            )
        }
        return Route.Player(film)
    }
    if (link.videoUrl.isNotBlank()) {
        return Route.Player(
            Film(
                id = link.key, title = title, year = 0, durationMin = 0, synopsis = "",
                posterUrl = link.posterUrl, videoUrl = link.videoUrl, sourceUrl = "",
            ),
        )
    }
    return null
}
