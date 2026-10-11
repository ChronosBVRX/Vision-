package com.chronosbvrx.visionplus.data

/** One channel of an M3U playlist. */
data class LiveChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "",
    val userAgent: String? = null,
    val referrer: String? = null,
    val origin: String? = null,
    val drmKey: String? = null,
    val drmType: String? = null,
) {
    /** El grupo final: respeta group-title del M3U y clasifica lo genérico. */
    fun effectiveGroup(): String = LiveTaxonomy.groupOf(name, group)
}

/** An M3U source: the one provider (XuperPlugin), or a list the person added by hand. */
data class LivePlaylist(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean = false,
)

private val ATTR = Regex("""([A-Za-z0-9-]+)="([^"]*)"""")

/**
 * Parses an extended M3U/M3U8 channel list into channels: `#EXTINF` (with `tvg-logo`,
 * `group-title` and `tvg-name` attributes), the optional `#EXTGRP:` and `#EXTVLCOPT:` lines
 * (user agent and referrer), then the URL. Only http(s) URLs are kept, since those are the ones
 * the player can open; duplicated name+URL pairs are dropped.
 */
fun parseM3u(text: String): List<LiveChannel> {
    val channels = LinkedHashMap<String, LiveChannel>()
    var name: String? = null
    var attrs: Map<String, String> = emptyMap()
    var group: String? = null
    var userAgent: String? = null
    var referrer: String? = null

    fun reset() {
        name = null
        attrs = emptyMap()
        group = null
        userAgent = null
        referrer = null
    }

    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val comma = line.indexOf(',')
                name = (if (comma >= 0) line.substring(comma + 1) else "").trim()
                    .ifBlank { attrs["tvg-name"].orEmpty().trim() }
            }
            line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                val option = line.substringAfter(':').trim()
                when {
                    option.startsWith("http-user-agent=", ignoreCase = true) -> userAgent = option.substringAfter('=').trim()
                    option.startsWith("http-referrer=", ignoreCase = true) -> referrer = option.substringAfter('=').trim()
                }
            }
            line.startsWith("#") -> Unit
            else -> {
                val channelName = name
                if (!channelName.isNullOrBlank() && (line.startsWith("http://") || line.startsWith("https://"))) {
                    val channel = LiveChannel(
                        name = channelName,
                        url = line,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = (group ?: attrs["group-title"]).orEmpty().trim().ifBlank { "Sin categoría" },
                        userAgent = userAgent,
                        referrer = referrer,
                    )
                    channels.putIfAbsent(channel.url, channel)
                }
                reset()
            }
        }
    }
    return channels.values.toList()
}
