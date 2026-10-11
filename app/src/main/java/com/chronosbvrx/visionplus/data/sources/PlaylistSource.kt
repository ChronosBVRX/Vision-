package com.chronosbvrx.visionplus.data.sources

import android.content.Context
import android.util.Base64
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LivePlaylist
import com.chronosbvrx.visionplus.data.LiveStore
import com.chronosbvrx.visionplus.data.xuperRows

/**
 * One list the person added by hand, stated as a source: its channels go to En vivo and its
 * video groups join Home. Refs are self-contained (`m3u:lista|<base64url de la ficha>`),
 * so playback of a remembered channel keeps working even after the list is refreshed.
 */
class PlaylistSource(context: Context, private val stored: LivePlaylist) : Source {

    private val app = context.applicationContext
    private val store = LiveStore(app)

    override val id: String = stored.id
    override val title: String = stored.name
    override val builtIn: Boolean = stored.builtIn

    /** The scheme of the refs this source answers. */
    private val prefix = "m3u:|$id"
    private val refPrefix = "m3u:$id|"

    override suspend fun livePlaylist(): LivePlaylist = stored

    private var channels: List<LiveChannel> = store.cached(id) ?: emptyList()

    override suspend fun catalogRows(): List<CatalogRow> {
        channels = runCatching { store.fetch(stored) }.getOrDefault(channels)
        return xuperRows(channels) // Mismo agrupado Películas / Series / Anime del Xuper
    }

    override fun knows(ref: String): Boolean = ref.startsWith(refPrefix)

    override suspend fun playable(ref: String): Playable? {
        if (!knows(ref)) return null
        val card = decodeCard(ref.removePrefix(refPrefix))
        return Playable(
            url = card.url,
            headers = buildMap {
                card.userAgent?.let { put("User-Agent", it) }
                card.referrer?.let { put("Referer", it) }
            },
            label = card.name,
        )
    }

    /** Builds the self-contained ref of one channel (`m3u:…|fichaBase64url`). */
    fun refOf(channel: LiveChannel): String =
        refPrefix + Base64.encodeToString(
            listOf(channel.name, channel.url, channel.userAgent.orEmpty(), channel.referrer.orEmpty())
                .joinToString("\u0001").toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

    private class Card(val name: String, val url: String, val userAgent: String?, val referrer: String?)

    private fun decodeCard(token: String): Card {
        val parts = String(Base64.decode(token, Base64.URL_SAFE), Charsets.UTF_8).split('\u0001')
        return Card(
            name = parts.getOrNull(0).orEmpty(),
            url = parts.getOrNull(1).orEmpty(),
            userAgent = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
            referrer = parts.getOrNull(3)?.takeIf { it.isNotEmpty() },
        )
    }
}
