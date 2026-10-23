package com.chronosbvrx.visionplus.data.launcher

import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.seriesKeyOf
import com.chronosbvrx.visionplus.data.shouldKeep

/**
 * El canal Watch Next del launcher de TV («Continuar viendo» / «Play Next») es el espejo de
 * PlaybackHistory: cada recuerdo se publica como una tarjeta que, al seleccionarse, lanza el
 * deep link que reanuda la película o el capítulo exacto.
 *
 * Este archivo es la parte pura (sin Android): normaliza el historial a tarjetas y decide qué
 * tocarle al canal. La parte Android vive en [LauncherWatchNext].
 */

/** Una tarjeta candidata del canal Watch Next, ya normalizada desde el historial. */
internal data class WatchNextSpec(
    val key: String,
    /** La clave del canal: sin 2+ capítulos/temporadas un recuerdo es película. */
    val isEpisode: Boolean,
    /** Título de la tarjeta: el de la serie en capítulos, el de la película suelta. */
    val title: String,
    /** «T2 · C3» cuando es capítulo (vacío en películas). */
    val episodeTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val seriesId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val posterUrl: String,
    /** Respaldo de reproducción para el deep link (playCode XTV, vacío en el resto). */
    val code: String,
    val videoUrl: String,
    /** Título tal como lo muestra la app (con sufijo de capítulo), para el respaldo. */
    val displayTitle: String,
)

/** Una fila ya publicada en el canal (lo que el sistema tiene hoy). */
internal data class ExistingWatchNext(
    val rowId: Long,
    val key: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** Qué tocarle al canal: lo viejo fuera, lo nuevo dentro, lo movido actualizado. */
internal data class WatchNextPlan(
    val inserts: List<WatchNextSpec>,
    val updates: List<Pair<Long, WatchNextSpec>>,
    val deletes: List<Long>,
)

/** Cuántas tarjetas se publican a la vez (la fila del launcher no da para más). */
internal const val WATCH_NEXT_LIMIT = 10

/**
 * El historial como tarjetas del launcher: recientes primero, una sola entrada por serie
 * (la guía de Watch Next lo exige), tope [limit]. La clave por serie es la misma que usa la
 * fila «Seguir viendo» del inicio ([seriesKeyOf]): el nombre visible del título.
 */
internal fun watchNextSpecs(entries: List<ResumeEntry>, limit: Int = WATCH_NEXT_LIMIT): List<WatchNextSpec> {
    val out = ArrayList<WatchNextSpec>(limit)
    val seen = HashSet<String>(limit * 2)
    for (entry in entries.sortedByDescending { it.updatedAt }) {
        if (out.size >= limit) break
        if (entry.key.isBlank() || entry.positionMs <= 0L) continue
        // El historial ahora conserva los terminados para «Historial»; el launcher solo
        // debe espejar lo que de verdad se puede continuar.
        if (!shouldKeep(entry.positionMs, entry.durationMs)) continue
        val dedupeKey = seriesKeyOf(entry) ?: "k:${entry.key}"
        if (!seen.add(dedupeKey)) continue
        out += entry.toSpec()
    }
    return out
}

/**
 * Qué hacer con el canal: insertar lo que falta, actualizar solo lo que el usuario acaba de
 * tocar (una escritura atrasada de otro proceso jamás retrocede una tarjeta más nueva) y
 * borrar lo que ya no está en el historial.
 */
internal fun reconcileWatchNext(desired: List<WatchNextSpec>, existing: List<ExistingWatchNext>): WatchNextPlan {
    val existingByKey = existing.associateBy { it.key }
    val desiredKeys = desired.mapTo(HashSet(desired.size)) { it.key }
    val inserts = desired.filter { it.key !in existingByKey }
    val updates = desired.mapNotNull { spec ->
        val row = existingByKey[spec.key] ?: return@mapNotNull null
        if (spec.updatedAt > row.updatedAt) row.rowId to spec else null
    }
    val deletes = existing.filter { it.key !in desiredKeys }.map { it.rowId }
    return WatchNextPlan(inserts = inserts, updates = updates, deletes = deletes)
}

/**
 * Lo descartado («No me interesa» en el launcher) se queda fuera hasta que un nuevo avance
 * en el historial lo vuelva a poner sobre la mesa.
 */
internal fun filterDismissed(specs: List<WatchNextSpec>, dismissed: Map<String, Long>): List<WatchNextSpec> =
    specs.filter { spec -> (dismissed[spec.key] ?: Long.MIN_VALUE) < spec.updatedAt }

/** El recuerdo como tarjeta: separa título y sufijo de capítulo cuando es serie. */
private fun ResumeEntry.toSpec(): WatchNextSpec {
    // La app entera usa la misma regla (isSeriesContent): sin 2+ capítulos/temporadas,
    // la ficha suelta del portal se trata como película.
    val isEpisode = episodeIds.size > 1 || seasonIds.size > 1
    val base = if (isEpisode) {
        title.substringBefore(" · T").substringBefore(" · C").ifBlank { title }
    } else {
        title
    }
    val suffix = if (isEpisode) title.removePrefix(base).trim(' ', '·') else ""
    return WatchNextSpec(
        key = key,
        isEpisode = isEpisode,
        title = base,
        episodeTitle = suffix,
        seasonNumber = if (isEpisode && seasonIds.isNotEmpty()) seasonIndex + 1 else 0,
        episodeNumber = if (isEpisode && episodeIds.isNotEmpty()) episodeIndex + 1 else 0,
        seriesId = if (isEpisode) seriesId else "",
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
        posterUrl = posterUrl,
        code = xtvCode,
        videoUrl = videoUrl,
        displayTitle = title,
    )
}
