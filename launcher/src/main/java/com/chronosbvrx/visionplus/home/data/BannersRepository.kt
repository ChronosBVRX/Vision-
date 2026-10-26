package com.chronosbvrx.visionplus.home.data

import android.content.Context
import com.chronosbvrx.visionplus.home.contract.VisionLinks
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
 * Los banners diarios que prepara el VPS (`/v1/banners`) con el arte panorámico de
 * TMDB. Se piden una vez y se guardan en disco: si la tele arranca sin red, el hero
 * sigue teniendo la foto del día. Los deportes NO vienen de aquí: salen del feed.
 *
 * Coste cero en régimen normal:
 *  - la copia caduca con el `nextRefreshAt` del VPS (20 h), no cada 30 min;
 *  - una sola descarga a la vez aunque el Inicio se recomponga;
 *  - si la red falla, 15 min de enfriamiento con la copia en disco;
 *  - el arte se cachea por su RUTA estable (el `?v=` diario no obliga a bajarlo otra vez).
 */
/** Respaldo si un servidor viejo no manda `nextRefreshAt` (la foto es diaria). */
private const val DEFAULT_TTL_MS = 6L * 60 * 60 * 1000
/** Margen tras el refresco anunciado por el VPS para no pedir justo en el cambio. */
private const val RETRY_GRACE_MS = 5L * 60 * 1000

object BannersRepository {

    private const val BASE_URL = "https://visionplusv2.duckdns.org"
    private const val API_KEY = "fb2244441f690185f861470423afb43278c3b19d31b14115"
    private const val FILE_NAME = "launcher_banners.json"

    /** Tras una respuesta vieja (servidor refrescando) no se vuelve a preguntar de inmediato. */
    private const val MIN_RETRY_MS = 10L * 60 * 1000
    /** Si la red falla, no se martilla: 15 min con la copia en disco. */
    private const val FAIL_BACKOFF_MS = 15L * 60 * 1000
    private const val CONNECT_MS = 4_000
    private const val READ_MS = 7_000

    private data class Cache(val slides: List<HeroSlide>, val expiresAt: Long)

    @Volatile
    private var memory: Cache? = null

    private val fetchLock = Mutex()

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    /** Lectura instantánea para el primer frame (sin red). */
    fun cached(context: Context): List<HeroSlide> = readCache(context)?.slides.orEmpty()

    /** Devuelve la lista buena (red o caché). `null` solo si no hay nada que enseñar. */
    suspend fun load(context: Context, force: Boolean = false): List<HeroSlide>? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!force) {
            readCache(app)?.let { hit ->
                if (System.currentTimeMillis() < hit.expiresAt) return@withContext hit.slides
            }
        }
        fetchLock.withLock {
            val now = System.currentTimeMillis()
            if (!force) {
                readCache(app)?.let { hit ->
                    if (now < hit.expiresAt) return@withContext hit.slides
                }
            }
            val payload = runCatching { fetchJson() }.getOrNull()
            val fresh = payload?.let { runCatching { parseBanners(it) }.getOrNull() }.orEmpty()
            if (payload == null || fresh.isEmpty()) {
                val old = readCache(app)?.slides.orEmpty()
                if (old.isNotEmpty()) memory = Cache(old, now + FAIL_BACKOFF_MS)
                return@withContext old.takeIf { it.isNotEmpty() }
            }
            writeRaw(app, payload)
            var expires = parseBannersExpiry(payload, now)
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
        val slides = runCatching { parseBanners(raw) }.getOrDefault(emptyList())
        if (slides.isEmpty()) return null
        return Cache(slides, parseBannersExpiry(raw, target.lastModified())).also { memory = it }
    }

    private fun fetchJson(): String? {
        val connection = (URL("$BASE_URL/v1/banners").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            requestMethod = "GET"
            setRequestProperty("X-Api-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Guarda el JSON TAL CUAL: la caché no pierde campos nuevos del servidor. */
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
internal fun parseBannersExpiry(json: String, fallbackAt: Long = 0L): Long {
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
internal fun parseBanners(json: String): List<HeroSlide> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val items = root.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<HeroSlide>(items.length())
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val kind = item.optString("kind").lowercase()
        if (kind == "sports") {
            val eventId = item.optString("eventId")
            val live = item.optBoolean("live")
            out += HeroSlide(
                id = item.optString("id").ifBlank { "sports:$eventId" },
                title = item.optString("title"),
                sports = true,
                subtitle = item.optString("subtitle"),
                eyebrow = item.optString("eyebrow"),
                badge = item.optString("badge").ifBlank { if (live) "EN VIVO" else "PRÓXIMO" },
                league = item.optString("league"),
                leagueLogo = item.optString("leagueLogo"),
                homeName = item.optString("homeName"),
                awayName = item.optString("awayName"),
                homeLogo = item.optString("homeLogo"),
                awayLogo = item.optString("awayLogo"),
                homeScore = item.optIntOrNull("homeScore"),
                awayScore = item.optIntOrNull("awayScore"),
                status = item.optString("status"),
                detail = item.optString("detail"),
                startMs = item.optLong("startMs"),
                live = live,
                timeText = item.optString("timeText"),
                dateText = item.optString("dateText"),
                openLink = item.optString("openLink").ifBlank { VisionLinks.sports(eventId) },
            )
            continue
        }
        val ref = item.optString("ref")
        val title = item.optString("title")
        val poster = item.optString("poster")
        val videoUrl = item.optString("videoUrl")
        val series = item.optBoolean("series")
        val openLink = item.optString("openLink").ifBlank {
            VisionLinks.title(ref = ref, title = title, poster = poster, videoUrl = videoUrl)
        }
        val playLink = item.optString("playLink").ifBlank {
            if (series) "" else watchLink(videoUrl, title, poster)
        }
        out += HeroSlide(
            id = item.optString("id").ifBlank { "banner:$ref" },
            title = title,
            badge = item.optString("badge"),
            eyebrow = item.optString("eyebrow"),
            backdrop = item.optString("backdrop").ifBlank { poster },
            poster = poster,
            synopsis = item.optString("synopsis"),
            year = item.optInt("year"),
            rating = item.optDouble("rating", 0.0),
            durationMin = item.optInt("durationMin"),
            series = series,
            openLink = openLink,
            playLink = playLink,
        )
    }
    return out
}

private fun watchLink(videoUrl: String, title: String, poster: String): String {
    if (videoUrl.isBlank()) return ""
    return if (videoUrl.startsWith("xtv:")) {
        VisionLinks.watch(
            key = videoUrl,
            title = title,
            poster = poster,
            code = videoUrl.removePrefix("xtv:"),
        )
    } else {
        VisionLinks.watch(
            key = videoUrl,
            title = title,
            poster = poster,
            videoUrl = videoUrl,
        )
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null
