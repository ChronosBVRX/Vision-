package com.chronosbvrx.visionplus.data.sports

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Un equipo de un partido (con su escudo y marcador, si ya hay). */
data class SportTeam(
    val id: String,
    val name: String,
    val short: String,
    val logo: String,
    val score: Int?,
)

/** La competición de un partido. */
data class SportLeague(
    val id: String,
    val name: String,
    val logo: String,
)

/** Un partido de la agenda del día. */
data class SportMatch(
    val id: String,
    val league: SportLeague,
    val home: SportTeam,
    val away: SportTeam,
    val kickoffMs: Long,
    val status: String,          // pre | in | post
    val detail: String,          // "45'", "FT"… (vacío si aún no empieza)
    val venue: String,
    val channels: List<String>,  // cadenas candidatas en México (las manda el servidor)
    val broadcasts: List<String>,
) {
    val playing: Boolean get() = status == "in"
    val finished: Boolean get() = status == "post"
}

/**
 * La agenda de fútbol del servidor propio (`GET /v1/sports`): los partidos del
 * día calendario en curso, con logos, marcador en vivo y las cadenas candidatas
 * de México. Se cachea por fecha en prefs; hoy vence a los 3 minutos (va en
 * vivo) y los demás días a los 30.
 */
object SportsSource {

    private const val TAG = "SportsSource"
    private const val PREFS = "sports_remote"
    private const val TODAY_TTL_MS = 3 * 60 * 1000L
    private const val OTHER_TTL_MS = 30 * 60 * 1000L

    /** How many days ahead the date strip offers. */
    const val DAYS_AHEAD = 4

    suspend fun matches(context: Context, dateIso: String, force: Boolean = false): List<SportMatch> =
        withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = parse(prefs.getString(keyFor(dateIso), null).orEmpty())
            val at = prefs.getLong(atKeyFor(dateIso), 0L)
            val ttl = if (dateIso == todayIso()) TODAY_TTL_MS else OTHER_TTL_MS
            if (!force && cached.isNotEmpty() && System.currentTimeMillis() - at < ttl) {
                return@withContext cached
            }
            val fetched = runCatching { download(dateIso) }.getOrNull()
            if (fetched == null) {
                Log.w(TAG, "agenda inalcanzable; copia local de $dateIso (${cached.size})")
                return@withContext cached
            }
            prefs.edit()
                .putString(keyFor(dateIso), toJson(fetched))
                .putLong(atKeyFor(dateIso), System.currentTimeMillis())
                .apply()
            Log.i(TAG, "agenda $dateIso: ${fetched.size} partidos")
            fetched
        }

    /** La última copia guardada de un día, sin tocar la red. */
    fun cached(context: Context, dateIso: String): List<SportMatch> = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        parse(prefs.getString(keyFor(dateIso), null).orEmpty())
    }.getOrDefault(emptyList())

    private fun download(dateIso: String): List<SportMatch> {
        val connection = (URL("${RemoteIndexSource.BASE_URL}/v1/sports?date=$dateIso&days=1")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("la agenda respondió HTTP ${connection.responseCode}")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun keyFor(dateIso: String) = "m_$dateIso"
    private fun atKeyFor(dateIso: String) = "at_$dateIso"

    // ----- fechas -----

    fun todayIso(): String = isoOf(Calendar.getInstance())

    /** El día `days` después de `dateIso` (negativo para atrás). */
    fun shift(dateIso: String, days: Int): String {
        val parts = dateIso.split("-")
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        cal.add(Calendar.DAY_OF_MONTH, days)
        return isoOf(cal)
    }

    /** "Ayer", "Hoy", "Mañana" o "Sáb 11". */
    fun dayLabel(dateIso: String): String = when (dateIso) {
        todayIso() -> "Hoy"
        shift(todayIso(), -1) -> "Ayer"
        shift(todayIso(), 1) -> "Mañana"
        else -> weekdayLabel(dateIso)
    }

    private fun weekdayLabel(dateIso: String): String {
        val parts = dateIso.split("-")
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        val day = SimpleDateFormat("EEE", Locale.forLanguageTag("es")).format(cal.time)
        return day.replaceFirstChar { it.uppercase() } + " " + parts[2].toInt()
    }

    private fun isoOf(cal: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d",
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
    )

    /** La hora local de un kickoff ("21:00"); vacío si no hay fecha. */
    fun timeLabel(kickoffMs: Long): String {
        if (kickoffMs <= 0) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(kickoffMs)
    }

    // ----- json -----

    internal fun parse(text: String): List<SportMatch> {
        if (text.isBlank()) return emptyList()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("matches") ?: return emptyList()
        val out = ArrayList<SportMatch>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id")
            if (id.isBlank()) continue
            val league = m.optJSONObject("league") ?: JSONObject()
            out += SportMatch(
                id = id,
                league = SportLeague(
                    id = league.optString("id"),
                    name = league.optString("name"),
                    logo = league.optString("logo"),
                ),
                home = team(m.optJSONObject("home")),
                away = team(m.optJSONObject("away")),
                kickoffMs = parseKickoff(m.optString("kickoff")),
                status = m.optString("status", "pre"),
                detail = m.optString("detail"),
                venue = m.optString("venue"),
                channels = strings(m.optJSONArray("channels")),
                broadcasts = strings(m.optJSONArray("broadcasts")),
            )
        }
        return out.sortedBy { it.kickoffMs }
    }

    private fun team(obj: JSONObject?): SportTeam {
        if (obj == null) return SportTeam("", "", "", "", null)
        val rawScore = obj.opt("score")
        return SportTeam(
            id = obj.optString("id"),
            name = obj.optString("name"),
            short = obj.optString("short"),
            logo = obj.optString("logo"),
            score = when (rawScore) {
                null, JSONObject.NULL -> null
                is Number -> rawScore.toInt()
                else -> rawScore.toString().toIntOrNull()
            },
        )
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    internal fun parseKickoff(text: String): Long {
        if (text.isBlank()) return 0L
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss")) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return fmt.parse(text)?.time ?: 0L
            } catch (_: Exception) {
                // siguiente formato
            }
        }
        return 0L
    }

    internal fun toJson(matches: List<SportMatch>): String {
        val arr = JSONArray()
        matches.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("league", JSONObject().put("id", m.league.id).put("name", m.league.name).put("logo", m.league.logo))
                put("home", teamJson(m.home))
                put("away", teamJson(m.away))
                put("kickoff", isoOfInstance(m.kickoffMs))
                put("status", m.status)
                put("detail", m.detail)
                put("venue", m.venue)
                put("channels", JSONArray(m.channels))
                put("broadcasts", JSONArray(m.broadcasts))
            })
        }
        return JSONObject().put("matches", arr).toString()
    }

    private fun teamJson(team: SportTeam): JSONObject = JSONObject().apply {
        put("id", team.id)
        put("name", team.name)
        put("short", team.short)
        put("logo", team.logo)
        if (team.score != null) put("score", team.score) else put("score", JSONObject.NULL)
    }

    private fun isoOfInstance(ms: Long): String {
        if (ms <= 0) return ""
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(ms)
    }
}
