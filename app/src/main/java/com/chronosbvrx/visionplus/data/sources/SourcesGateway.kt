package com.chronosbvrx.visionplus.data.sources

import android.content.Context
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.LivePlaylist
import com.chronosbvrx.visionplus.data.LiveStore

/**
 * The gateway of all content: the integrated source plus each list the person added. Home shows
 * the union of every source's rows (the integrated one first), En vivo shows every source's
 * playlist, and [playable] routes any ref to the source that knows it. New kinds of sources —
 * a direct-url source, a bookmarked "plg1"-style shortcut — plug in by implementing [Source]
 * and joining [sources].
 */
class SourcesGateway(context: Context, sources: List<Source>) {

    private val app = context.applicationContext
    private val store = LiveStore(app)
    private val all = SourcesList(sources)

    /** The sources in serving order: the integrated one first, then the person's lists. */
    val sources: List<Source> get() = all.sources

    /** The ones the person can see: the integrated source always, the rest only if alive. */
    fun refresh(): List<Source> =
        all.sources.filter { it.builtIn || true } // place to hide disconnected user lists later

    /** Every live playlist, the source order intact; En vivo screens read them in order. */
    suspend fun livePlaylists(): List<LivePlaylist> =
        refresh().mapNotNull { it.livePlaylist() }

    /**
     * Home rows, every source merged in order (a title appearing in two sources shows once,
     * first source wins) — the same fold the catalog's screen expects.
     */
    suspend fun homeRows(): List<CatalogRow> {
        val merged = LinkedHashMap<String, CatalogRow>()
        refresh().forEach { source ->
            source.catalogRows().forEach { row ->
                val existing = merged[row.id]
                merged[row.id] = if (existing == null) row
                else CatalogRow(existing.id, existing.title, (existing.films + row.films).distinctBy { it.id })
            }
        }
        return merged.values.toList()
    }

    /** Playback for one ref through the source that knows it; null when nobody does. */
    suspend fun playable(ref: String): Playable? = all.playable(ref)

    /** Whether any source answers the ref. */
    fun knows(ref: String): Boolean = all.knows(ref)
}
