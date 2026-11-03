package com.chronosbvrx.visionplus.data.sports

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Un jugador de la alineación, con su dorsal cuando la fuente lo publica. */
data class LineupPlayer(
    val number: String,
    val name: String,
    val position: String = "",
    val starter: Boolean = true,
)

/** Tipo de suceso de la cronología del partido. */
enum class MatchEventType { GOAL, YELLOW, RED, SUBSTITUTION }

/** Un suceso de la cronología (gol, tarjeta o cambio), tal como lo publica ESPN. */
data class MatchEvent(
    val team: String,
    val type: MatchEventType,
    val player: String,
    val minute: String,
    val detail: String = "",
)

/** Estadísticas de un partido de fútbol, cuando ESPN las publica. */
data class TeamMatchStats(
    val name: String,
    val values: Map<String, String>,
    val lineup: List<String>,
    val logo: String = "",
    /** Formación publicada por la fuente («4-3-3»); vacío si no la trae. */
    val formation: String = "",
    /** Titulares con dorsal y posición, para la página de alineaciones del HUD. */
    val players: List<LineupPlayer> = emptyList(),
)

/** Un gol del partido, con su autor y minuto (ESPN `keyEvents`). */
data class GoalEvent(
    val team: String,
    val player: String,
    val minute: String,
    val ownGoal: Boolean = false,
    val penalty: Boolean = false,
)

data class MatchStats(
    val home: TeamMatchStats,
    val away: TeamMatchStats,
    val fetchedAt: Long,
    val goals: List<GoalEvent> = emptyList(),
    /** Cronología completa (goles, tarjetas y cambios) ordenada por minuto. */
    val events: List<MatchEvent> = emptyList(),
) {
    fun value(team: TeamMatchStats, key: String): String =
        team.values[key].orEmpty()
}

/**
 * ESPN publica el resumen del partido (posesión, tiros, córners, tarjetas y
 * alineaciones). Se usa solo para fútbol y solo cuando la agenda ya identificó
 * el evento; si la fuente no lo tiene, la UI lo dice y no inventa cifras.
 */
object SportsStatsSource {
    private data class CacheEntry(val at: Long, val stats: MatchStats)

    private const val TTL_MS = 60_000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun load(
        context: Context,
        homeName: String,
        awayName: String,
        dateIso: String,
        leagueId: String,
    ): MatchStats? = withContext(Dispatchers.IO) {
        if (dateIso.isBlank()) return@withContext null
        val matches = SportsSource.cached(context, dateIso).ifEmpty {
            runCatching { SportsSource.matches(context, dateIso) }.getOrDefault(emptyList())
        }
        val home = fold(homeName)
        val away = fold(awayName)
        val match = matches.firstOrNull { candidate ->
            val ch = fold(candidate.home.name)
            val ca = fold(candidate.away.name)
            (home.isNotBlank() && away.isNotBlank() &&
                ((home in ch || ch in home) && (away in ca || ca in away))) ||
                (home.isNotBlank() && away.isNotBlank() &&
                    (home in ca || ca in home) && (away in ch || ch in away))
        } ?: return@withContext null
        val key = match.id
        cache[key]?.takeIf { System.currentTimeMillis() - it.at in 0 until TTL_MS }?.let {
            return@withContext it.stats
        }
        val path = espnPath(match.league.id) ?: return@withContext null
        val stats = runCatching { fetch(path, key) }.getOrNull() ?: return@withContext null
        cache[key] = CacheEntry(System.currentTimeMillis(), stats)
        stats
    }

    private fun fetch(path: String, eventId: String): MatchStats? {
        val url = "https://site.api.espn.com/apis/site/v2/sports/$path/summary?event=$eventId"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VisionPlus/1.0 (sports stats)")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseSummary(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Parser puro del resumen de ESPN (probado con una captura real de fútbol). */
    internal fun parseSummary(body: String): MatchStats? {
        val root = JSONObject(body)
        val box = root.optJSONObject("boxscore") ?: return null
        val teams = box.optJSONArray("teams") ?: return null
        if (teams.length() < 2) return null
        val rosters = root.optJSONArray("rosters")
        val parsed = (0 until minOf(2, teams.length())).map { index ->
            val t = teams.optJSONObject(index) ?: JSONObject()
            val teamJson = t.optJSONObject("team")
            val name = teamJson?.optString("displayName")
                .orEmpty().ifBlank { if (index == 0) "Local" else "Visitante" }
            val logo = teamJson?.optString("logo").orEmpty()
            val values = linkedMapOf<String, String>()
            val statistics = t.optJSONArray("statistics") ?: org.json.JSONArray()
            for (i in 0 until statistics.length()) {
                val stat = statistics.optJSONObject(i) ?: continue
                val statName = stat.optString("name")
                val label = statLabel(statName) ?: continue
                val raw = stat.optString("displayValue").ifBlank {
                    stat.optDouble("value", 0.0).toString()
                }
                val value = if (statName in FRACTION_PERCENT) {
                    raw.toDoubleOrNull()
                        ?.takeIf { it in 0.0..1.0 }
                        ?.let { "${(it * 100).roundToInt()}%" }
                        ?: raw
                } else {
                    raw
                }
                if (value.isNotBlank()) values[label] = value
            }
            val lineup = rosterFor(rosters, name)
            TeamMatchStats(
                name = name,
                values = values,
                lineup = lineup.players.filter { it.starter }.map { it.name }
                    .ifEmpty { lineup.players.map { it.name } },
                logo = logo,
                formation = lineup.formation,
                players = lineup.players,
            )
        }
        return MatchStats(
            parsed[0],
            parsed[1],
            System.currentTimeMillis(),
            goals = parseGoals(root),
            events = parseEvents(root),
        )
    }

    /**
     * Goles del partido desde `keyEvents`: autor, equipo, minuto y si fue penal o en
     * propia puerta. Es la misma fuente que usa ESPN para el marcador; si no viene,
     * la lista queda vacía y la UI no inventa goleadores.
     */
    internal fun parseGoals(root: JSONObject): List<GoalEvent> {
        val events = root.optJSONArray("keyEvents") ?: return emptyList()
        val out = ArrayList<Pair<Double, GoalEvent>>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            if (!event.optBoolean("scoringPlay", false)) continue
            val type = event.optJSONObject("type")?.optString("text").orEmpty()
            val text = event.optString("text")
            val isGoal = type.contains("goal", ignoreCase = true) ||
                type.contains("penalty", ignoreCase = true)
            if (!isGoal) continue
            val ownGoal = type.contains("own goal", ignoreCase = true) ||
                text.contains("own goal", ignoreCase = true)
            val penalty = type.contains("penalty", ignoreCase = true)
            val team = event.optJSONObject("team")?.optString("displayName").orEmpty()
            val player = event.optJSONArray("participants")
                ?.optJSONObject(0)
                ?.optJSONObject("athlete")
                ?.optString("displayName")
                .orEmpty()
            if (player.isBlank()) continue
            val clock = event.optJSONObject("clock")
            val minute = clock?.optString("displayValue").orEmpty()
            val sort = clock?.optDouble("value", 0.0) ?: 0.0
            out.add(sort to GoalEvent(team, player, minute, ownGoal, penalty))
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    /**
     * Cronología del partido desde `keyEvents`: goles, amarillas, rojas y cambios.
     * Es la misma fuente del marcador; si no publica un suceso, no se inventa.
     */
    internal fun parseEvents(root: JSONObject): List<MatchEvent> {
        val events = root.optJSONArray("keyEvents") ?: return emptyList()
        val out = ArrayList<Pair<Double, MatchEvent>>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val typeText = event.optJSONObject("type")?.optString("text").orEmpty()
            val lower = typeText.lowercase()
            val scoring = event.optBoolean("scoringPlay", false)
            val type = when {
                scoring && (lower.contains("goal") || lower.contains("penalty")) &&
                    !lower.contains("missed") -> MatchEventType.GOAL
                lower.contains("second yellow") -> MatchEventType.RED
                lower.contains("yellow") -> MatchEventType.YELLOW
                lower.contains("red") -> MatchEventType.RED
                lower.contains("substitution") -> MatchEventType.SUBSTITUTION
                else -> null
            } ?: continue
            val team = event.optJSONObject("team")?.optString("displayName").orEmpty()
            val participants = event.optJSONArray("participants")
            fun nameAt(index: Int): String =
                participants?.optJSONObject(index)
                    ?.optJSONObject("athlete")
                    ?.optString("displayName")
                    .orEmpty()
            val player = nameAt(0)
            if (player.isBlank()) continue
            val clock = event.optJSONObject("clock")
            val minute = clock?.optString("displayValue").orEmpty()
            val sort = clock?.optDouble("value", 0.0) ?: 0.0
            val detail = when (type) {
                MatchEventType.GOAL -> when {
                    lower.contains("own goal") -> "en propia puerta"
                    lower.contains("penalty") -> "penal"
                    else -> ""
                }
                MatchEventType.SUBSTITUTION -> nameAt(1).let { if (it.isBlank()) "" else "por $it" }
                else -> ""
            }
            out += sort to MatchEvent(team, type, player, minute, detail)
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    private data class RosterInfo(val formation: String, val players: List<LineupPlayer>)

    private fun rosterFor(rosters: org.json.JSONArray?, teamName: String): RosterInfo {
        if (rosters == null) return RosterInfo("", emptyList())
        val target = fold(teamName)
        for (i in 0 until rosters.length()) {
            val roster = rosters.optJSONObject(i) ?: continue
            val name = roster.optJSONObject("team")?.optString("displayName").orEmpty()
            if (target.isNotBlank() && fold(name) != target) continue
            // ESPN publica la alineación en `roster` (fútbol) o `athletes` (otros deportes).
            // Se prefieren los titulares; si la fuente no los marca, se listan los primeros.
            val players = roster.optJSONArray("roster") ?: roster.optJSONArray("athletes") ?: continue
            val all = ArrayList<LineupPlayer>()
            val starters = ArrayList<LineupPlayer>()
            for (j in 0 until players.length()) {
                val entry = players.optJSONObject(j) ?: continue
                val athlete = entry.optJSONObject("athlete") ?: entry
                val display = athlete.optString("displayName")
                    .ifBlank { entry.optString("displayName") }
                if (display.isBlank()) continue
                val number = entry.optString("jersey")
                    .ifBlank { athlete.optString("jersey") }
                val position = entry.optJSONObject("position")
                    ?.optString("abbreviation")
                    .orEmpty()
                    .ifBlank {
                        entry.optJSONObject("position")?.optString("displayName").orEmpty()
                    }
                val starter = entry.optBoolean("starter", false)
                val player = LineupPlayer(number, display, position, starter)
                all += player
                if (starter) starters += player
            }
            val chosen = if (starters.isNotEmpty()) starters else all.take(11)
            val formation = roster.optString("formation").trim()
            if (chosen.isNotEmpty() || formation.isNotBlank()) {
                return RosterInfo(formation, chosen)
            }
        }
        return RosterInfo("", emptyList())
    }

    private fun statLabel(name: String): String? = when (name) {
        "possessionPct" -> "Posesión"
        "totalShots" -> "Tiros"
        "shotsOnGoal", "shotsOnTarget" -> "Tiros a puerta"
        "wonCorners", "cornerKicks" -> "Córners"
        "foulsCommitted", "fouls" -> "Faltas"
        "offsides" -> "Fueras de juego"
        "yellowCards" -> "Amarillas"
        "redCards" -> "Rojas"
        "saves" -> "Atajadas"
        "totalPasses", "passes" -> "Pases"
        "passPct" -> "Precisión de pase"
        "totalCrosses" -> "Centros"
        "interceptions" -> "Intercepciones"
        "blockedShots" -> "Tiros bloqueados"
        "fieldGoalPct" -> "Tiros de campo"
        "threePointFieldGoalPct" -> "Triples"
        "freeThrowPct" -> "Tiros libres"
        "totalRebounds" -> "Rebotes"
        "assists" -> "Asistencias"
        "steals" -> "Robos"
        "blocks" -> "Bloqueos"
        "turnovers" -> "Pérdidas"
        "totalYards" -> "Yardas"
        "passingYards" -> "Yardas por pase"
        "rushingYards" -> "Yardas por carrera"
        "hits" -> "Hits"
        "runs" -> "Carreras"
        "errors" -> "Errores"
        "homeRuns" -> "Jonrones"
        else -> null
    }

    private fun espnPath(leagueId: String): String? {
        val clean = leagueId.trim()
        if (clean.isBlank()) return null
        if (clean.contains('/')) return clean
        if (clean in SOCCER_LEAGUES) return "soccer/$clean"
        return when (clean) {
            "nba" -> "basketball/nba"
            "euroleague" -> "basketball/euroleague"
            "nfl" -> "football/nfl"
            "mlb" -> "baseball/mlb"
            "nhl" -> "hockey/nhl"
            else -> null
        }
    }

    /** ESPN entrega algunas precisiones como fracción (0.9 = 90%). */
    private val FRACTION_PERCENT = setOf("passPct", "shotPct", "crossPct", "longballPct", "tacklePct")

    private val SOCCER_LEAGUES = setOf(
        "mex.1", "mex.2", "esp.1", "eng.1", "ita.1", "ger.1", "fra.1", "por.1",
        "uefa.champions", "uefa.europa", "uefa.nations", "conmebol.libertadores",
        "conmebol.sudamericana", "arg.1", "bra.1", "col.1", "fifa.friendly",
        "concacaf.nations.league", "concacaf.gold", "fifa.world",
    )

    private fun fold(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
