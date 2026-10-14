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
 * videoUrl y se abre desde el principio para que la tarjeta nunca quede muerta.
 */
internal fun launcherRouteFor(context: Context, uri: Uri?): Route? {
    val link = LauncherLinks.parse(uri) ?: return null
    val entry = PlaybackHistory.get(context, link.key)
    if (entry != null) return resumeRoute(ResumeCard(entry, film = null, progress = 0f))
    val title = link.title.ifBlank { "Vision+" }
    if (link.code.isNotBlank()) {
        return Route.Player(
            Film(
                id = "xtv:${link.code}", title = title, year = 0, durationMin = 0, synopsis = "",
                posterUrl = link.posterUrl, videoUrl = "xtv:${link.code}", sourceUrl = "xtv",
            ),
        )
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
