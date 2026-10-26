package com.chronosbvrx.visionplus.data.banners

import android.content.Context
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * La foto diaria del Inicio que prepara el VPS (`/v1/banners`): 5 películas con arte
 * panorámico de TMDB. El canal de TV la mezcla con los partidos en vivo de GeeSports.
 *
 * Se cachea en disco para que el billboard tenga arte desde el primer frame y para que
 * el Inicio siga viéndose bien si la tele arranca sin red.
 */
data class HomeBanner(
    val id: String,
    val title: String,
    val poster: String,
    val backdrop: String,
    val synopsis: String,
    val year: Int,
    val rating: Double,
    val durationMin: Int,
    val series: Boolean,
    val ref: String,
    val videoUrl: String,
    val badge: String,
    val eyebrow: String,
)

object BannerSource {
    private const val FILE_NAME = "vision_home_banners.json"
    private const val TTL_MS = 30L * 60 * 1000
    private const val CONNECT_MS = 4_000
    private const val READ_MS = 7_000

    @Volatile
    private var memory: Pair<Long, List<HomeBanner>>? = null

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    fun cached(context: Context): List<HomeBanner> {
        memory?.let { (at, banners) ->
            if (System.currentTimeMillis() - at < TTL_MS) return banners
        }
        val raw = runCatching {
            file(context).takeIf { it.exists() }?.readText(StandardCharsets.UTF_8)
        }.getOrNull() ?: return emptyList()
        val banners = runCatching { parseHomeBanners(raw) }.getOrDefault(emptyList())
        if (banners.isNotEmpty()) memory = System.currentTimeMillis() to banners
        return banners
    }

    suspend fun load(context: Context, force: Boolean = false): List<HomeBanner>? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            if (!force) {
                memory?.let { (at, banners) ->
                    if (now - at < TTL_MS) return@withContext banners
                }
                if (file(app).exists() && now - file(app).lastModified() in 0..TTL_MS) {
                    runCatching { parseHomeBanners(file(app).readText(StandardCharsets.UTF_8)) }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            memory = now to it
                            return@withContext it
                        }
                }
            }
            val raw = runCatching { fetchJson() }.getOrNull()
            val fresh = raw?.let { runCatching { parseHomeBanners(it) }.getOrNull() }.orEmpty()
            if (raw == null || fresh.isEmpty()) {
                return@withContext cached(app).takeIf { it.isNotEmpty() }
            }
            writeRaw(app, raw)
            memory = now to fresh
            fresh
        }

    private fun fetchJson(): String? {
        val connection = (URL("${RemoteIndexSource.BASE_URL}/v1/banners").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            requestMethod = "GET"
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun writeRaw(context: Context, json: String) {
        val target = file(context)
        val part = File(target.parentFile, "$FILE_NAME.part")
        runCatching {
            part.writeText(json, StandardCharsets.UTF_8)
            if (!part.renameTo(target)) {
                target.writeText(json, StandardCharsets.UTF_8)
                part.delete()
            }
        }
    }
}

/** Puro y con guardián: traduce la respuesta del VPS sin tocar Android. */
internal fun parseHomeBanners(json: String): List<HomeBanner> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val items = root.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<HomeBanner>(items.length())
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val title = item.optString("title").trim()
        if (title.isBlank()) continue
        out += HomeBanner(
            id = item.optString("id").ifBlank { "banner:$title" },
            title = title,
            poster = item.optString("poster"),
            backdrop = item.optString("backdrop"),
            synopsis = item.optString("synopsis"),
            year = item.optInt("year"),
            rating = item.optDouble("rating", 0.0),
            durationMin = item.optInt("durationMin"),
            series = item.optBoolean("series"),
            ref = item.optString("ref"),
            videoUrl = item.optString("videoUrl"),
            badge = item.optString("badge"),
            eyebrow = item.optString("eyebrow"),
        )
    }
    return out
}
