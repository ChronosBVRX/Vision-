package com.chronosbvrx.visionplus.data.sources

import android.content.Context
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LivePlaylist
import com.chronosbvrx.visionplus.data.LiveStore
import com.chronosbvrx.visionplus.data.PLUGIN_TITLE
import com.chronosbvrx.visionplus.data.XUPER_PLAYLIST_ID
import com.chronosbvrx.visionplus.data.xuper.MagisClient
import com.chronosbvrx.visionplus.data.xuper.XuperApiClient
import com.chronosbvrx.visionplus.data.xuper.XuperEngine
import com.chronosbvrx.visionplus.data.xuperRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The integrated source, stated as one more source: it boots the engine (which serves
 * standard HLS on localhost), puts its proxied list into En vivo and its film groups into
 * Home. The refs it answers are the playlist url itself — the localhost proxy keeps the
 * session fresh in the background, so playback needs nothing more than the url.
 */
class XuperSource : Source {

    override val id: String = XUPER_PLAYLIST_ID
    override val title: String = PLUGIN_TITLE
    override val builtIn: Boolean = true

    private var cachedUrl: String? = null
    private lateinit var store: LiveStore

    @Volatile
    private var appCtx: Context? = null

    /** Prefix of the per-channel refs this source answers (`xuperch:<code>|<name>|<logo>`). */
    private val channelPrefix = "xuperch:"

    /** Remembers the context (and store) without booting anything: rows can ask right away. */
    fun prime(context: Context) {
        appCtx = context.applicationContext
        if (!this::store.isInitialized) store = LiveStore(context.applicationContext)
    }

    /** Boot (or reuse) the engine and remember the localhost playlist url. */
    suspend fun ensureProxied(context: Context): Result<String> {
        prime(context)
        return XuperEngine.ensureReady(context).also { cachedUrl = it.getOrNull() }
    }

    override suspend fun livePlaylist(): LivePlaylist? =
        cachedUrl?.let { LivePlaylist(id = id, name = title, url = it, builtIn = true) }

    /**
     * Home rows: the portal's own rows (movies, series, kids and anime from the Magis portal)
     * first — they do not depend on the local proxy being alive — then whatever film groups the
     * proxied playlist still offers.
     */
    override suspend fun catalogRows(): List<CatalogRow> {
        val rows = LinkedHashMap<String, CatalogRow>()
        appCtx?.let { ctx ->
            runCatching { MagisClient(ctx).catalogRows() }.getOrDefault(emptyList())
                .forEach { rows[it.id] = it }
        }
        val url = cachedUrl
        if (url != null && this::store.isInitialized) {
            val list = LivePlaylist(id = id, name = title, url = url, builtIn = true)
            val channels = runCatching { store.fetch(list) }.getOrDefault(emptyList())
            xuperRows(channels).forEach { row -> rows.putIfAbsent(row.id, row) }
        }
        return rows.values.toList()
    }

    override fun knows(ref: String): Boolean = ref.startsWith("xuper:proxy|") || ref.startsWith(channelPrefix)

    override suspend fun playable(ref: String): Playable? {
        if (!knows(ref)) return null
        val url = if (ref.startsWith(channelPrefix)) {
            // La sesión canal-a-canal guarda el contexto del último arranque del source.
            val ctx = appCtx ?: return null
            XuperEngine.ensureChannel(ctx, channelCodeOf(ref)).getOrNull() ?: return null
        } else cachedUrl ?: return null
        return Playable(url = url, mime = "application/vnd.apple.mpegurl", label = channelNameOf(ref))
    }

    /** The portal's channel list as En vivo data: name, logo, group and the self-contained ref. */
    suspend fun channelCatalog(context: Context): List<LiveChannel> {
        if (!this::store.isInitialized) store = LiveStore(context.applicationContext)
        appCtx = context.applicationContext
        // Origen desarmado (una sesión rancia tiraba la reproducción XTV del mismo portal):
        // no se construye el cliente legado ni se regenera su almacén.
        if (XuperEngine.isDisabled(context)) return emptyList()
        val client = XuperApiClient(context)
        val fetched = withContext(Dispatchers.IO) {
            runCatching { client.getLiveData().getOrNull().orEmpty() }.getOrDefault(emptyList())
        }
        return fetched.map { ch ->
            val name = ch.name.ifBlank { "Canal ${ch.number}" }
            LiveChannel(
                name = name,
                url = "$channelPrefix${ch.channelCode}|$name|${ch.logo}",
                logo = ch.logo.takeIf { it.isNotBlank() },
                group = ch.groupTitle.ifBlank { "TV en Vivo" },
            )
        }
    }

    private fun channelCodeOf(ref: String): String = ref.removePrefix(channelPrefix).substringBefore('|')

    private fun channelNameOf(ref: String): String = ref.removePrefix(channelPrefix).substringAfter('|', "").substringBefore('|')

    /** Todo lo que la lista proxada entrega ahora mismo (En vivo lo consume directo). */
    suspend fun channels(context: Context): List<LiveChannel> {
        val url = cachedUrl ?: return emptyList()
        if (!this::store.isInitialized) store = LiveStore(context.applicationContext)
        val list = LivePlaylist(id = id, name = title, url = url, builtIn = true)
        return runCatching { store.fetch(list) }.getOrDefault(emptyList())
    }
}
