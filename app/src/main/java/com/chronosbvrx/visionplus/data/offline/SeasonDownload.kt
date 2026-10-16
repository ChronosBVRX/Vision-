package com.chronosbvrx.visionplus.data.offline

/**
 * Lógica pura de las descargas de temporada completa y del encadenado offline
 * («siguiente capítulo descargado»). Sin Android: testeable en JVM.
 */

/** El siguiente capítulo DESCARGADO de la misma serie (archivo ya completo). Puro. */
internal fun nextDownloadedEpisode(
    current: OfflineDownload,
    all: List<OfflineDownload>,
): OfflineDownload? {
    if (!current.isSeries || current.seriesId.isBlank()) return null
    val sameSeries = all.filter {
        it.id != current.id &&
            it.isSeries &&
            it.seriesId == current.seriesId &&
            it.state == DownloadState.COMPLETED
    }
    // El capítulo inmediato de la misma temporada manda.
    sameSeries.firstOrNull {
        it.seasonIndex == current.seasonIndex && it.episodeIndex == current.episodeIndex + 1
    }?.let { return it }
    // Si la temporada se acabó, el primer capítulo disponible de una temporada posterior.
    return sameSeries
        .filter { it.seasonIndex > current.seasonIndex }
        .minWithOrNull(compareBy({ it.seasonIndex }, { it.episodeIndex }))
}

/** Resumen del estado de descarga de una temporada (una entrada por capítulo, null = sin descarga). */
data class SeasonDownloadSummary(
    val total: Int,
    val completed: Int,
    val active: Int,
    val pending: Int,
    val missing: Int,
) {
    val allDone: Boolean get() = total > 0 && completed == total

    /** Cuántos capítulos tienen algo por hacer: reanudar o descargar. */
    val actionable: Int get() = pending + missing
}

internal fun seasonDownloadSummary(items: List<OfflineDownload?>): SeasonDownloadSummary {
    var completed = 0
    var active = 0
    var pending = 0
    var missing = 0
    for (item in items) {
        when (item?.state) {
            null -> missing++
            DownloadState.COMPLETED -> completed++
            DownloadState.DOWNLOADING, DownloadState.QUEUED -> active++
            DownloadState.PAUSED, DownloadState.ERROR -> pending++
        }
    }
    return SeasonDownloadSummary(
        total = items.size,
        completed = completed,
        active = active,
        pending = pending,
        missing = missing,
    )
}

/** Etiqueta del botón «temporada» según el resumen (sin emojis: regla de la casa). */
internal fun seasonDownloadLabel(s: SeasonDownloadSummary): String = when {
    s.total == 0 -> "Descargar temporada"
    s.allDone -> "Temporada descargada"
    s.active > 0 -> "Descargando · ${s.completed} de ${s.total}"
    s.pending > 0 && s.missing == 0 -> "Reanudar temporada · ${s.pending}"
    s.pending > 0 -> "Completar temporada · ${s.actionable} de ${s.total}"
    else -> "Descargar temporada · ${s.missing} de ${s.total}"
}

/** El botón de temporada no se ofrece mientras la cola ya está trabajando o todo bajó. */
internal fun seasonDownloadActionable(s: SeasonDownloadSummary): Boolean =
    s.total > 0 && !s.allDone && s.active == 0 && s.actionable > 0
