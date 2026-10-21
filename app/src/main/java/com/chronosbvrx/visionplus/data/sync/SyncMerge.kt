package com.chronosbvrx.visionplus.data.sync

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.latestBySeries
import com.chronosbvrx.visionplus.data.prune

/**
 * La fusión de la continuidad entre equipos, pura y sin Android: lo único que decide qué
 * versión de cada elemento gana.
 *
 * - `resume`: por playCode, gana el `updatedAt` mayor; después se colapsa a un capítulo por
 *   serie (el más reciente) y se poda a 20.
 * - `fav`/`later`: por id; en la PRIMERA sincronización se unen (no se pisa lo de ningún
 *   equipo), después gana el timestamp mayor.
 * - `watched`: unión (marcar visto no se deshace entre equipos en esta fase).
 * - `removed`: tombstones con su hora original; un presente más nuevo lo revive.
 */

internal const val KIND_RESUME = "resume"
internal const val KIND_FAV = "fav"
internal const val KIND_LATER = "later"
internal const val KIND_WATCHED = "watched"

internal val SYNC_KINDS = setOf(KIND_RESUME, KIND_FAV, KIND_LATER, KIND_WATCHED)

/** Lo que este equipo tiene ahora mismo (y lo que el servidor devuelve ya tipado). */
internal data class SyncSnapshot(
    val resume: List<ResumeEntry> = emptyList(),
    val favorites: List<Film> = emptyList(),
    val watchLater: List<Film> = emptyList(),
    val watched: Set<String> = emptySet(),
)

/** Un elemento que llegó del servidor: `resume`/`film` ya parseados, no JSON. */
internal data class RemoteSyncItem(
    val kind: String,
    val key: String,
    val at: Long,
    val deleted: Boolean = false,
    val resume: ResumeEntry? = null,
    val film: Film? = null,
    /** Solo `watched`: los capítulos vistos de esa serie (la clave del item es el seriesId). */
    val watched: List<Int> = emptyList(),
)

internal data class SyncMergeResult(
    val snapshot: SyncSnapshot,
    val times: Map<String, Long>,
    val removed: Map<String, Long>,
)

internal fun stampKey(kind: String, key: String): String = "$kind|$key"

/** Todas las claves presentes, con su tipo: lo que se compara contra los tombstones. */
internal fun snapshotKeys(snapshot: SyncSnapshot): Set<String> = buildSet {
    snapshot.resume.forEach { add(stampKey(KIND_RESUME, it.key)) }
    snapshot.favorites.forEach { add(stampKey(KIND_FAV, it.id)) }
    snapshot.watchLater.forEach { add(stampKey(KIND_LATER, it.id)) }
    // Los vistos viajan por serie (una fila con todos sus capítulos), no por capítulo.
    snapshot.watched.forEach { add(stampKey(KIND_WATCHED, it.substringBefore('|'))) }
}

/**
 * Un elemento que estaba en la última sincronización y ya no está en el equipo se convierte
 * en tombstone con SU hora (la primera vez que se detecta; no se refresca en cada intento).
 * Si vuelve a estar presente, su tombstone se retira.
 */
internal fun detectRemovals(
    times: Map<String, Long>,
    current: Set<String>,
    removed: Map<String, Long>,
    nowMs: Long,
): Pair<Map<String, Long>, Map<String, Long>> {
    val outTimes = HashMap(times)
    val outRemoved = HashMap(removed)
    current.forEach { outRemoved.remove(it) }
    for (key in times.keys) {
        if (key.startsWith("$KIND_WATCHED|")) continue
        if (key !in current) {
            outTimes.remove(key)
            if (key !in outRemoved) outRemoved[key] = nowMs
        }
    }
    return outTimes to outRemoved
}

/** Los tombstones se conservan 180 días: después ya no hay nada que propagar. */
internal fun pruneRemoved(removed: Map<String, Long>, nowMs: Long): Map<String, Long> {
    val cutoff = nowMs - 180L * 24 * 60 * 60 * 1000
    return removed.filterValues { it >= cutoff }
}

internal fun mergeSync(
    local: SyncSnapshot,
    remote: List<RemoteSyncItem>,
    times: Map<String, Long>,
    removed: Map<String, Long>,
    nowMs: Long,
): SyncMergeResult {
    val first = times.isEmpty() && removed.isEmpty()
    val outTimes = HashMap(times)
    val outRemoved = HashMap(removed)

    val resume = LinkedHashMap<String, ResumeEntry>()
    local.resume.forEach { entry ->
        resume[entry.key] = entry
        val key = stampKey(KIND_RESUME, entry.key)
        outTimes[key] = maxOf(outTimes[key] ?: 0L, entry.updatedAt.takeIf { it > 0 } ?: nowMs)
    }
    val favorites = LinkedHashMap<String, Film>()
    local.favorites.forEach { film ->
        favorites[film.id] = film
        val key = stampKey(KIND_FAV, film.id)
        outTimes[key] = outTimes[key] ?: nowMs
    }
    val watchLater = LinkedHashMap<String, Film>()
    local.watchLater.forEach { film ->
        watchLater[film.id] = film
        val key = stampKey(KIND_LATER, film.id)
        outTimes[key] = outTimes[key] ?: nowMs
    }
    val watched = local.watched.toMutableSet()
    local.watched.forEach { episode ->
        val key = stampKey(KIND_WATCHED, episode.substringBefore('|'))
        outTimes[key] = outTimes[key] ?: nowMs
    }

    remote.forEach { item ->
        if (item.kind !in SYNC_KINDS) return@forEach
        val key = stampKey(item.kind, item.key)
        val localAt = outTimes[key] ?: 0L
        when (item.kind) {
            KIND_RESUME -> {
                if (item.deleted) {
                    if (item.at >= localAt) {
                        resume.remove(item.key)
                        outTimes.remove(key)
                        outRemoved[key] = maxOf(outRemoved[key] ?: 0L, item.at)
                    }
                } else {
                    val entry = item.resume ?: return@forEach
                    if (item.at >= localAt) {
                        resume[item.key] = entry.copy(updatedAt = maxOf(entry.updatedAt, item.at))
                        outTimes[key] = maxOf(localAt, item.at)
                        outRemoved.remove(key)
                    }
                }
            }
            KIND_FAV, KIND_LATER -> {
                val target = if (item.kind == KIND_FAV) favorites else watchLater
                if (item.deleted) {
                    if (item.at >= localAt) {
                        target.remove(item.key)
                        outTimes.remove(key)
                        outRemoved[key] = maxOf(outRemoved[key] ?: 0L, item.at)
                    }
                } else {
                    val film = item.film ?: return@forEach
                    if (first || item.at >= localAt) {
                        target[item.key] = film
                        outTimes[key] = maxOf(localAt, item.at)
                        outRemoved.remove(key)
                    }
                }
            }
            KIND_WATCHED -> {
                if (!item.deleted) {
                    item.watched.forEach { episode -> watched.add("${item.key}|$episode") }
                    outTimes[key] = maxOf(localAt, item.at)
                }
            }
        }
    }

    val mergedResume = prune(latestBySeries(resume.values.sortedByDescending { it.updatedAt }), nowMs)
    val mergedFavorites = favorites.values.sortedByDescending { outTimes[stampKey(KIND_FAV, it.id)] ?: 0L }
    val mergedLater = watchLater.values.sortedByDescending { outTimes[stampKey(KIND_LATER, it.id)] ?: 0L }
    return SyncMergeResult(
        snapshot = SyncSnapshot(
            resume = mergedResume,
            favorites = mergedFavorites,
            watchLater = mergedLater,
            watched = watched,
        ),
        times = outTimes,
        removed = outRemoved,
    )
}
