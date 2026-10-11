package com.chronosbvrx.visionplus.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LivePlaylist
import com.chronosbvrx.visionplus.data.LiveStore
import com.chronosbvrx.visionplus.data.MAGIS_PLAYLIST_ID
import com.chronosbvrx.visionplus.data.MAGIS_TITLE
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How long an En vivo channel list stays trusted before asking the source again. */
private const val CHANNELS_TTL_MS = 15 * 60_000L

/** The En vivo screen's state: the chosen list, its channels, the filters and the network status. */
@Stable
class LiveState(
    private val store: LiveStore,
    private val pluginContext: Context,
    private val scope: CoroutineScope,
) {
    var playlists by mutableStateOf(store.playlists())
        private set
    var selected by mutableStateOf(playlists.firstOrNull())
        private set
    var channels by mutableStateOf<List<LiveChannel>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var query by mutableStateOf("")
    var group by mutableStateOf<String?>(null)
        private set

    init {
        LiveFavorites.init(pluginContext)
        // La lista nativa XTV carga directamente vía X4KApi.liveChannelsFor; las listas del usuario completan la fila.
        val magis = LivePlaylist(MAGIS_PLAYLIST_ID, MAGIS_TITLE, "magis://catalog", builtIn = true)
        playlists = listOf(magis) + store.playlists().filterNot { it.builtIn }
        selected = magis
        load(magis, force = false)
    }

    /** The groups of the current list, in the order they first appear. */
    val groups: List<String> get() = channels.map { it.effectiveGroup() }.distinct()

    /** The channels under the group filter and the search box. */
    val shown: List<LiveChannel> get() = channels.filter { channel ->
        (group == null || channel.effectiveGroup() == group) &&
            (query.isBlank() || channel.name.contains(query.trim(), ignoreCase = true))
    }

    fun pick(playlist: LivePlaylist) {
        if (playlist.id == selected?.id && channels.isNotEmpty()) return
        selected = playlist
        group = null
        query = ""
        load(playlist, force = false)
    }

    /** Fetches the chosen list again, ignoring the cache. */
    fun refresh() {
        selected?.let { load(it, force = true) }
    }

    fun selectGroup(value: String?) {
        group = value
    }

    fun add(name: String, url: String) {
        val playlist = store.addPlaylist(name, url)
        playlists = store.playlists()
        selected = playlist
        group = null
        query = ""
        load(playlist, force = true)
    }

    fun remove(playlist: LivePlaylist) {
        store.removePlaylist(playlist.id)
        playlists = store.playlists()
        if (selected?.id == playlist.id) {
            val next = playlists.firstOrNull()
            selected = next
            group = null
            query = ""
            channels = emptyList()
            next?.let { load(it, force = false) }
        }
    }

    private fun load(playlist: LivePlaylist, force: Boolean) {
        if (playlist.id == MAGIS_PLAYLIST_ID) {
            loadMagis(playlist, force)
            return
        }
        val cached = store.cachedFresh(playlist.id, CHANNELS_TTL_MS)
        channels = cached ?: emptyList()
        error = null
        if (!force && cached != null) return
        loading = true
        scope.launch {
            runCatching { store.fetch(playlist) }
                .onSuccess { if (selected?.id == playlist.id) channels = it }
                .onFailure { if (selected?.id == playlist.id) error = it.message ?: "No se pudo cargar la lista" }
            loading = false
        }
    }

    /** The built-in source: its channels come from the portal's own live line, not from an M3U. */
    private fun loadMagis(playlist: LivePlaylist, force: Boolean) {
        val cached = store.cached(playlist.id)
        if (cached != null && cached.isNotEmpty()) {
            channels = cached
        }
        val isFresh = store.cachedFresh(playlist.id, CHANNELS_TTL_MS) != null
        error = null
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { X4KApi.cachedLive(pluginContext) }
            runCatching { X4KApi.ensureEngineData(pluginContext) }
        }
        if (!force && isFresh && channels.isNotEmpty()) return
        loading = channels.isEmpty()
        scope.launch {
            runCatching { X4KApi.liveChannelsFor(pluginContext, force = force) }
                .onSuccess {
                    if (it.isNotEmpty()) {
                        store.cacheChannels(playlist.id, it)
                    }
                    if (selected?.id == playlist.id) channels = it
                }
                .onFailure { if (selected?.id == playlist.id && channels.isEmpty()) error = it.message ?: "No se pudo cargar TV en vivo" }
            loading = false
        }
    }
}

@Composable
fun rememberLiveState(): LiveState {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { LiveState(LiveStore(app), app, scope) }
}

/** The channels marked with ★ while the app runs, by URL, persisted in SharedPreferences. */
object LiveFavorites {
    private const val PREFS_NAME = "live_favorites_prefs"
    private const val KEY_URLS = "favorite_urls"

    val urls = mutableStateListOf<String>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_URLS, emptySet()) ?: emptySet()
        if (urls.toSet() != saved) {
            urls.clear()
            urls.addAll(saved)
        }
    }

    fun has(url: String): Boolean = url in urls

    fun has(context: Context, url: String): Boolean {
        if (appContext == null) init(context)
        return url in urls
    }

    fun toggle(url: String): Boolean {
        val isNowFav = if (urls.contains(url)) {
            urls.remove(url)
            false
        } else {
            urls.add(url)
            true
        }
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_URLS, urls.toSet()).commit()
        }
        return isNowFav
    }

    fun toggle(context: Context, url: String): Boolean {
        if (appContext == null) init(context)
        return toggle(url)
    }
}
