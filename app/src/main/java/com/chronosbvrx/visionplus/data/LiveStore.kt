package com.chronosbvrx.visionplus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The user agent some IPTV servers expect (the same VLC announces). */
private const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

/**
 * Keeps the person's playlists and the channels parsed from each one (cached, so "En vivo" shows a
 * list instantly while the fresh copy arrives from the network).
 */
class LiveStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vision_live", Context.MODE_PRIVATE)

    /** The one provider's list (added by the Live screens when the plugin answers) plus the person's own. */
    fun playlists(): List<LivePlaylist> {
        val stored = runCatching {
            val array = JSONArray(prefs.getString(KEY_LISTS, "[]"))
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (url.isBlank()) null else LivePlaylist(o.optString("id"), o.optString("name"), url)
            }
        }.getOrDefault(emptyList())
        return stored
    }

    fun addPlaylist(name: String, url: String): LivePlaylist {
        val cleanUrl = url.trim()
        val cleanName = name.trim().ifBlank {
            cleanUrl.substringAfterLast('/').substringBefore('?').removeSuffix(".m3u").removeSuffix(".m3u8").ifBlank { "Mi lista" }
        }
        val list = LivePlaylist(id = "user_${System.currentTimeMillis().toString(36)}", name = cleanName, url = cleanUrl)
        saveUserLists(playlists().filterNot { it.builtIn } + list)
        return list
    }

    /** The one provider's list and lists marked built-in are never removed by this. */
    fun removePlaylist(id: String) {
        saveUserLists(playlists().filterNot { it.builtIn }.filterNot { it.id == id })
        prefs.edit().remove(cacheKey(id)).apply()
    }

    /** The channels parsed last time for [id], if any. */
    fun cached(id: String): List<LiveChannel>? {
        memoryChannels[id]?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val raw = prefs.getString(cacheKey(id), null) ?: return null
            channelsFromJson(JSONArray(raw)).also {
                if (it.isNotEmpty()) memoryChannels[id] = it
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Stores [channels] as the cache for [id] — for providers that don't fetch an M3U. */
    fun cacheChannels(id: String, channels: List<LiveChannel>) {
        if (channels.isNotEmpty()) {
            memoryChannels[id] = channels
        } else {
            memoryChannels.remove(id)
        }
        prefs.edit()
            .putString(cacheKey(id), channelsToJson(channels).toString())
            .putLong(cacheAtKey(id), System.currentTimeMillis())
            .apply()
    }

    /** The cached channels for [id] when younger than [ttlMs]; older ones ask the source again. */
    fun cachedFresh(id: String, ttlMs: Long): List<LiveChannel>? {
        val at = prefs.getLong(cacheAtKey(id), 0L)
        if (at <= 0L || System.currentTimeMillis() - at > ttlMs) return null
        return cached(id)
    }

    private fun cacheAtKey(id: String) = "channels_at_$id"

    /** Fetches and parses [playlist], caching the result. Throws on network or parse failure. */
    suspend fun fetch(playlist: LivePlaylist): List<LiveChannel> = withContext(Dispatchers.IO) {
        val connection = (URL(playlist.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("La lista respondió HTTP $code")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val channels = parseM3u(text)
            if (channels.isEmpty()) error("La lista no tiene canales compatibles")
            cacheChannels(playlist.id, channels)
            channels
        } finally {
            connection.disconnect()
        }
    }

    private fun saveUserLists(lists: List<LivePlaylist>) {
        val array = JSONArray()
        lists.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        prefs.edit().putString(KEY_LISTS, array.toString()).apply()
    }

    private fun cacheKey(id: String) = "channels_$id"

    companion object {
        const val KEY_LISTS = "playlists"
        val memoryChannels = java.util.concurrent.ConcurrentHashMap<String, List<LiveChannel>>()
    }
}

private fun channelsToJson(channels: List<LiveChannel>): JSONArray {
    val array = JSONArray()
    channels.forEach { channel ->
        array.put(
            JSONObject()
                .put("name", channel.name)
                .put("url", channel.url)
                .put("logo", channel.logo ?: JSONObject.NULL)
                .put("group", channel.group)
                .put("ua", channel.userAgent ?: JSONObject.NULL)
                .put("ref", channel.referrer ?: JSONObject.NULL)
                .put("org", channel.origin ?: JSONObject.NULL)
                .put("drmKey", channel.drmKey ?: JSONObject.NULL)
                .put("drmType", channel.drmType ?: JSONObject.NULL),
        )
    }
    return array
}

private fun channelsFromJson(array: JSONArray): List<LiveChannel> = (0 until array.length()).mapNotNull { i ->
    val o = array.optJSONObject(i) ?: return@mapNotNull null
    val name = o.optString("name")
    val url = o.optString("url")
    if (name.isBlank() || url.isBlank()) null
    else LiveChannel(
        name = name,
        url = url,
        logo = o.optString("logo").takeIf { it.isNotBlank() && it != "null" },
        group = o.optString("group"),
        userAgent = o.optString("ua").takeIf { it.isNotBlank() && it != "null" },
        referrer = o.optString("ref").takeIf { it.isNotBlank() && it != "null" },
        origin = o.optString("org").takeIf { it.isNotBlank() && it != "null" },
        drmKey = o.optString("drmKey").takeIf { it.isNotBlank() && it != "null" },
        drmType = o.optString("drmType").takeIf { it.isNotBlank() && it != "null" },
    )
}
