package com.chronosbvrx.visionplus.home.data

import android.content.Context
import com.chronosbvrx.visionplus.home.contract.VisionLinks
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Los banners diarios que prepara el VPS (`/v1/banners`) con el arte panorámico de
 * TMDB. Se piden una vez y se guardan en disco: si la tele arranca sin red, el hero
 * sigue teniendo la foto del día. Los deportes NO vienen de aquí: salen del feed.
 */
object BannersRepository {

    private const val BASE_URL = "https://visionplusv2.duckdns.org"
    private const val API_KEY = "fb2244441f690185f861470423afb43278c3b19d31b14115"
    private const val FILE_NAME = "launcher_banners.json"
    private const val TTL_MS = 30L * 60 * 1000
    private const val CONNECT_MS = 4_000
    private const val READ_MS = 7_000

    @Volatile
    private var memory: Pair<Long, List<HeroSlide>>? = null

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    /** Lectura instantánea para el primer frame (sin red). */
    fun cached(context: Context): List<HeroSlide> {
        memory?.let { (at, slides) -> if (System.currentTimeMillis() - at < TTL_MS) return slides }
        val disk = runCatching {
            file(context).takeIf { it.exists() }?.readText(StandardCharsets.UTF_8)
        }.getOrNull() ?: return emptyList()
        val slides = runCatching { parseBanners(disk) }.getOrDefault(emptyList())
        if (slides.isNotEmpty()) memory = System.currentTimeMillis() to slides
        return slides
    }

    /** Devuelve la lista buena (red o caché). `null` solo si no hay nada que enseñar. */
    suspend fun load(context: Context, force: Boolean = false): List<HeroSlide>? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force) {
            memory?.let { (at, slides) ->
                if (now - at < TTL_MS) return@withContext slides
            }
            val diskAge = now - file(app).lastModified()
            if (file(app).exists() && diskAge in 0..TTL_MS) {
                runCatching { parseBanners(file(app).readText(StandardCharsets.UTF_8)) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        memory = now to it
                        return@withContext it
                    }
            }
        }
        val payload = runCatching { fetchJson() }.getOrNull()
        val fresh = payload?.let { runCatching { parseBanners(it) }.getOrNull() }.orEmpty()
        if (payload == null || fresh.isEmpty()) {
            return@withContext cached(app).takeIf { it.isNotEmpty() }
        }
        writeRaw(app, payload)
        memory = now to fresh
        fresh
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
