package com.chronosbvrx.visionplus.data.sources

import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.LivePlaylist

/**
 * Everything the player needs to open a video: the url plus the headers the origin expects.
 * Sources hand these out; the player screen never needs to know which source made them.
 */
data class Playable(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mime: String? = null,
    val label: String = "",
)

/** The reason a source can not serve right now; the message is already in the app's language. */
class SourceUnavailable(message: String) : Exception(message)

/**
 * A piece of content, addressed by a plain-string ref like `esquema:uid|datos`. Refs are what
 * lists and libraries remember, so they carry the data they need inside themselves and keep
 * working even when the app reopens them days later.
 */
interface Source {
    /** Stable id: the same one the playlist and the library remember. */
    val id: String

    /** Name the screens show. */
    val title: String

    /** True when the source came inside the app: it can not be removed, only hidden. */
    val builtIn: Boolean

    /** The live playlist when the source serves channels too, or null when it is video only. */
    suspend fun livePlaylist(): LivePlaylist?

    /** The Home rows this source contributes; empty when it only serves live channels. */
    suspend fun catalogRows(): List<CatalogRow>

    /** Whether this source knows how to open the ref. */
    fun knows(ref: String): Boolean

    /** Plays one of its refs; the answer's message goes straight to the screens, or null. */
    suspend fun playable(ref: String): Playable?
}

/**
 * All the enabled sources in one: the first source that knows a ref plays it, and Home shows
 * every source's rows together, integrated source first.
 */
class SourcesList(private val items: List<Source>) {

    val sources: List<Source> get() = items

    /** The refs composite knows are the union of what its children answer. */
    fun knows(ref: String): Boolean = items.any { it.knows(ref) }

    /** The ref paired with the source that answers first. */
    suspend fun playable(ref: String): Playable? =
        items.firstOrNull { it.knows(ref) }?.playable(ref)
}
