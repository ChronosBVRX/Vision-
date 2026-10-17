package com.chronosbvrx.visionplus.data.gee

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeeSports V3.8 — fuente principal de Fútbol y eventos deportivos.
 *
 * El servidor propio (Vision+) baja, descifra y sondea el CMS de GeeSports y
 * publica el catálogo ya resuelto en `/v1/gee38/sports` (grupos de canales 24/7)
 * y `/v1/gee38/events` (eventos por deporte con sus enlaces de transmisión).
 * Aquí solo se consume: los enlaces ya vienen con `alive` (vivos primero), sus
 * cabeceras (`|…`) y la llave ClearKey (`kid:key`) cuando el stream va cifrado.
 *
 * Se sirve la copia en memoria/archivo al instante (frame 0) y se refresca en
 * segundo plano; si la red falla, la última copia buena sigue en pie.
 */
object GeeSportsSource {

    private const val TAG = "GeeSports"

    /** Un enlace final de transmisión (HLS o DASH-ClearKey). */
    data class GeeLink(
        val name: String,
        val link: String,
        val headers: Map<String, String>,
        val type: String,
        val drm: String,
        val alive: Boolean?,
    )

    /** Un canal dentro de un grupo (TNT Sports, FOX Sports…). */
    data class GeeChannel(
        val name: String,
        val logo: String,
        val links: List<GeeLink>,
    )

    /** Un grupo de canales 24/7. */
    data class GeeGroup(
        val id: String,
        val name: String,
        val logo: String,
        val alive: Int,
        val total: Int,
        val channels: List<GeeChannel>,
    )

    /** Un evento deportivo (fútbol, básquet, box, F1…) con sus enlaces. */
    data class GeeEvent(
        val id: String,
        val category: String,
        val name: String,
        val logo: String,
        val teamA: String,
        val teamB: String,
        val dateIso: String,
        val dateText: String,
        val time: String,
        val alive: Int,
        val total: Int,
        val links: List<GeeLink>,
        // Enriquecido por el servidor con la agenda (marcadores en vivo/logos):
        val homeName: String = "",
        val awayName: String = "",
        val homeLogo: String = "",
        val awayLogo: String = "",
        val leagueLabel: String = "",
        val leagueLogo: String = "",
        val scoreHome: Int? = null,
        val scoreAway: Int? = null,
        val live: Boolean = false,
        val detail: String = "",
        val status: String = "",
    )

    private const val GROUPS_TTL_MS = 30L * 60 * 1000
    private const val EVENTS_TTL_MS = 8L * 60 * 1000
    private const val GROUPS_FILE = "gee38_sports.json"
    private const val EVENTS_FILE = "gee38_events.json"

    @Volatile
    private var memoryGroups: List<GeeGroup>? = null

    @Volatile
    private var memoryGroupsAt = 0L

    @Volatile
    private var memoryEventsList: List<GeeEvent>? = null

    @Volatile
    private var memoryEventsAt = 0L

    private fun fileOf(context: Context, name: String) =
        java.io.File(context.applicationContext.filesDir, name)

    /** Las imágenes del CMS a veces vienen en http:// (Android lo bloquea): a https. */
    private fun safeImage(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    // ------------------------------------------------------------------ red

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            requestMethod = "GET"
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ------------------------------------------------------------------ parseo

    private fun jsonLinks(array: JSONArray?): List<GeeLink> {
        if (array == null) return emptyList()
        val out = ArrayList<GeeLink>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val link = item.optString("link")
            if (link.isBlank()) continue
            val headersJson = item.optJSONObject("headers")
            val headers = LinkedHashMap<String, String>()
            if (headersJson != null) {
                for (key in headersJson.keys()) {
                    val value = headersJson.optString(key)
                    if (value.isNotBlank()) headers[key.lowercase()] = value
                }
            }
            out.add(
                GeeLink(
                    name = item.optString("name"),
                    link = link,
                    headers = headers,
                    type = item.optString("type", "hls"),
                    drm = item.optString("drm"),
                    alive = if (item.isNull("alive")) null else item.optBoolean("alive"),
                ),
            )
        }
        return out
    }

    internal fun parseGroups(json: String): List<GeeGroup> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("groups") ?: return emptyList()
        val out = ArrayList<GeeGroup>(array.length())
        for (i in 0 until array.length()) {
            val g = array.optJSONObject(i) ?: continue
            val name = g.optString("name")
            if (name.isBlank()) continue
            val channelsJson = g.optJSONArray("channels") ?: JSONArray()
            val channels = ArrayList<GeeChannel>(channelsJson.length())
            for (j in 0 until channelsJson.length()) {
                val c = channelsJson.optJSONObject(j) ?: continue
                val links = jsonLinks(c.optJSONArray("links"))
                if (links.isEmpty()) continue
                channels.add(
                    GeeChannel(
                        name = c.optString("name").ifBlank { name },
                        logo = safeImage(c.optString("logo")),
                        links = links,
                    ),
                )
            }
            out.add(
                GeeGroup(
                    id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                    name = name,
                    logo = safeImage(g.optString("logo")),
                    alive = g.optInt("alive"),
                    total = g.optInt("total"),
                    channels = channels,
                ),
            )
        }
        return out
    }

    internal fun parseEvents(json: String): List<GeeEvent> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<GeeEvent>(array.length())
        for (i in 0 until array.length()) {
            val e = array.optJSONObject(i) ?: continue
            val name = e.optString("event")
            if (name.isBlank()) continue
            val score = e.optJSONObject("score")
            out.add(
                GeeEvent(
                    id = e.optString("id", name + "|" + e.optString("date") + "|" + e.optString("time")),
                    category = e.optString("category"),
                    name = name,
                    logo = safeImage(e.optString("logo")),
                    teamA = e.optString("team_a"),
                    teamB = e.optString("team_b"),
                    dateIso = e.optString("date_iso"),
                    dateText = e.optString("date"),
                    time = e.optString("time"),
                    alive = e.optInt("alive"),
                    total = e.optInt("total"),
                    links = jsonLinks(e.optJSONArray("links")),
                    homeName = e.optString("home_name"),
                    awayName = e.optString("away_name"),
                    homeLogo = safeImage(e.optString("home_logo")),
                    awayLogo = safeImage(e.optString("away_logo")),
                    leagueLabel = e.optString("league_label"),
                    leagueLogo = safeImage(e.optString("league_logo")),
                    scoreHome = if (score == null || score.isNull("home")) null else score.optInt("home"),
                    scoreAway = if (score == null || score.isNull("away")) null else score.optInt("away"),
                    live = score?.optBoolean("live") ?: false,
                    detail = score?.optString("detail") ?: "",
                    status = score?.optString("status") ?: "",
                ),
            )
        }
        return out
    }

    // ------------------------------------------------------------------ caché

    /** La copia en memoria (para caminos síncronos: candidatos de un partido). */
    fun memoryEvents(): List<GeeEvent>? = memoryEventsList

    /** La última copia buena (memoria o archivo), sin red. */
    fun cachedGroups(context: Context): List<GeeGroup> {
        memoryGroups?.let { return it }
        val file = fileOf(context, GROUPS_FILE)
        if (!file.exists()) return emptyList()
        val parsed = runCatching { parseGroups(file.readText()) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) {
            memoryGroups = parsed
            memoryGroupsAt = file.lastModified()
        }
        return parsed
    }

    /** La última copia buena de eventos (memoria o archivo), sin red. */
    fun cachedEvents(context: Context): List<GeeEvent> {
        memoryEventsList?.let { return it }
        val file = fileOf(context, EVENTS_FILE)
        if (!file.exists()) return emptyList()
        val parsed = runCatching { parseEvents(file.readText()) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) {
            memoryEventsList = parsed
            memoryEventsAt = file.lastModified()
        }
        return parsed
    }

    /** Refresca los grupos de canales (TTL 30 min); la última copia buena si falla. */
    suspend fun refreshGroups(context: Context, force: Boolean = false): List<GeeGroup> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val hit = memoryGroups
            if (!force && hit != null && now - memoryGroupsAt < GROUPS_TTL_MS) return@withContext hit
            val fresh = runCatching { get("${RemoteIndexSource.BASE_URL}/v1/gee38/sports") }
                .onFailure { Log.w(TAG, "grupos: ${it.message}") }
                .getOrNull()
            val parsed = fresh?.let { runCatching { parseGroups(it) }.getOrDefault(emptyList()) }
            if (!parsed.isNullOrEmpty()) {
                memoryGroups = parsed
                memoryGroupsAt = now
                runCatching { fileOf(context, GROUPS_FILE).writeText(fresh) }
                return@withContext parsed
            }
            cachedGroups(context)
        }

    /** Refresca los eventos (TTL 8 min); la última copia buena si falla. */
    suspend fun refreshEvents(context: Context, force: Boolean = false): List<GeeEvent> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val hit = memoryEventsList
            if (!force && hit != null && now - memoryEventsAt < EVENTS_TTL_MS) return@withContext hit
            val fresh = runCatching {
                val body = get("${RemoteIndexSource.BASE_URL}/v1/gee38/events")
                body
            }.onFailure { Log.w(TAG, "eventos: ${it.message}") }.getOrNull()
            val parsed = fresh?.let { runCatching { parseEvents(it) }.getOrDefault(emptyList()) }
            if (!parsed.isNullOrEmpty()) {
                memoryEventsList = parsed
                memoryEventsAt = now
                runCatching { fileOf(context, EVENTS_FILE).writeText(fresh) }
                return@withContext parsed
            }
            cachedEvents(context)
        }

    // ------------------------------------------------------------------ a canal

    /** Un enlace de GeeSports como canal jugable (PlayerScreen ya soporta HLS+DASH+ClearKey). */
    fun toChannel(link: GeeLink, title: String = link.name, group: String = "Deportes en vivo"): LiveChannel =
        LiveChannel(
            name = title.ifBlank { "En vivo" },
            url = link.link,
            group = group,
            userAgent = link.headers["user-agent"],
            referrer = link.headers["referer"] ?: link.headers["referrer"],
            origin = link.headers["origin"],
            drmKey = link.drm.ifBlank { null },
            drmType = if (link.type == "dash") "dash" else null,
        )
}
