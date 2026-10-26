package com.chronosbvrx.visionplus.data.banners

import android.content.Context
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * La foto diaria del Inicio que prepara el VPS (`/v1/banners`): 5 películas con arte
 * panorámico de TMDB. El canal de TV la mezcla con los partidos en vivo de GeeSports.
 *
 * Coste cero en régimen normal:
 *  - la caché caduca con el `nextRefreshAt` del VPS (una foto cada 20 h), no cada 30 min;
 *  - si la red falla, hay 15 min de enfriamiento y se sigue pintando la copia en disco;
 *  - una sola descarga a la vez (Mutex), aunque el Inicio se recomponga;
 *  - el arte se cachea por su RUTA estable: el `?v=` diario del CDN no obliga a bajar
 *    otra vez la misma imagen.
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

/** Respaldo si un servidor viejo no manda `nextRefreshAt` (la foto es diaria). */
private const val DEFAULT_TTL_MS = 6L * 60 * 60 * 1000
/** Margen tras el refresco anunciado por el VPS para no pedir justo en el cambio. */
private const val RETRY_GRACE_MS = 5L * 60 * 1000
/** Tras una respuesta vieja (servidor refrescando) no se vuelve a preguntar de inmediato. */
private const val MIN_RETRY_MS = 10L * 60 * 1000
/** Si la red falla, no se martilla: 15 min con la copia en disco. */
private const val FAIL_BACKOFF_MS = 15L * 60 * 1000

object BannerSource {
    private const val FILE_NAME = "vision_home_banners.json"

    private const val CONNECT_MS = 4_000
    private const val READ_MS = 7_000

    private data class Cache(val banners: List<HomeBanner>, val expiresAt: Long)

    @Volatile
    private var memory: Cache? = null

    private val fetchLock = Mutex()

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    /** Lectura instantánea para el primer frame: NUNCA toca la red. */
    fun cached(context: Context): List<HomeBanner> = readCache(context)?.banners.orEmpty()

    suspend fun load(context: Context, force: Boolean = false): List<HomeBanner>? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            if (!force) {
                readCache(app)?.let { hit ->
                    if (System.currentTimeMillis() < hit.expiresAt) return@withContext hit.banners
                }
            }
            fetchLock.withLock {
                val now = System.currentTimeMillis()
                if (!force) {
                    readCache(app)?.let { hit ->
                        if (now < hit.expiresAt) return@withContext hit.banners
                    }
                }
                val raw = runCatching { fetchJson() }.getOrNull()
                val fresh = raw?.let { runCatching { parseHomeBanners(it) }.getOrNull() }.orEmpty()
                if (raw == null || fresh.isEmpty()) {
                    val old = readCache(app)?.banners.orEmpty()
                    if (old.isNotEmpty()) memory = Cache(old, now + FAIL_BACKOFF_MS)
                    return@withContext old.takeIf { it.isNotEmpty() }
                }
                writeRaw(app, raw)
                var expires = parseBannerExpiry(raw, now)
                if (expires <= now) expires = now + MIN_RETRY_MS
                memory = Cache(fresh, expires)
                fresh
            }
        }

    /**
     * Lee la copia en memoria o del disco. La caducidad sale del `nextRefreshAt` del
     * servidor (o del `lastModified` del archivo): leerla NO la vuelve a marcar fresca.
     */
    private fun readCache(context: Context): Cache? {
        memory?.let { return it }
        val target = file(context)
        if (!target.exists()) return null
        val raw = runCatching { target.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val banners = runCatching { parseHomeBanners(raw) }.getOrDefault(emptyList())
        if (banners.isEmpty()) return null
        return Cache(banners, parseBannerExpiry(raw, target.lastModified())).also { memory = it }
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

/**
 * Clave estable del arte: el `?v=` es solo un anti-caché de CDN. Al ignorarlo, Coil
 * reutiliza la misma imagen aunque el VPS cambie la versión cada día; si la película
 * cambia de arte, cambia la ruta y por tanto la clave.
 */
internal fun bannerArtCacheKey(url: String): String = url.substringBefore('?').ifBlank { url }

/**
 * Cuándo caduca la copia: el `nextRefreshAt` que manda el VPS (20 h) + un margen.
 * Sin ese campo (servidor viejo o archivo antiguo) se usa la hora del archivo + 6 h.
 */
internal fun parseBannerExpiry(json: String, fallbackAt: Long = 0L): Long {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0L
    val next = root.optLong("nextRefreshAt", 0L)
    return if (next > 0L) {
        next + RETRY_GRACE_MS
    } else if (fallbackAt > 0L) {
        fallbackAt + DEFAULT_TTL_MS
    } else {
        0L
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
