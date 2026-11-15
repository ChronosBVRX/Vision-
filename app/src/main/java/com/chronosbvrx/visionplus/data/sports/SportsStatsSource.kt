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
        category: String = "",
        leagueLabel: String = "",
    ): MatchStats? = withContext(Dispatchers.IO) {
        if (dateIso.isBlank() || homeName.isBlank() || awayName.isBlank()) return@withContext null
        val cacheKey = "$homeName|$awayName|$dateIso|$leagueId|$category"
        cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.at in 0 until TTL_MS }?.let {
            return@withContext it.stats
        }

        val home = fold(homeName)
        val away = fold(awayName)

        // 1. Intentar primero con la agenda de fútbol local (solo cuando aplica a fútbol)
        val isSoccer = isSoccerCategory(category, leagueId, leagueLabel)
        val matches = if (isSoccer) {
            SportsSource.cached(context, dateIso).ifEmpty {
                runCatching { SportsSource.matches(context, dateIso) }.getOrDefault(emptyList())
            }
        } else {
            emptyList()
        }
        val match = if (isSoccer) {
            matches.firstOrNull { candidate ->
                val ch = fold(candidate.home.name)
                val ca = fold(candidate.away.name)
                (home.isNotBlank() && away.isNotBlank() &&
                    ((home in ch || ch in home) && (away in ca || ca in away))) ||
                    (home.isNotBlank() && away.isNotBlank() &&
                        (home in ca || ca in home) && (away in ch || ch in away))
            }
        } else {
            null
        }

        val (path, eventId) = if (match != null) {
            val p = espnPath(match.league.id, category, leagueLabel) ?: return@withContext null
            p to match.id
        } else {
            // 2. Otros deportes (NFL, NBA, MLB, NHL...) o fútbol no resuelto localmente: Scoreboard de ESPN
            val p = espnPath(leagueId, category, leagueLabel)
                ?: espnPathFromTeams(homeName, awayName, category, leagueLabel)
                ?: return@withContext null
            val resolvedId = resolveEspnEventId(p, home, away, dateIso) ?: return@withContext null
            p to resolvedId
        }

        val stats = runCatching { fetch(path, eventId) }.getOrNull() ?: return@withContext null
        cache[cacheKey] = CacheEntry(System.currentTimeMillis(), stats)
        stats
    }

    private fun resolveEspnEventId(path: String, home: String, away: String, dateIso: String = ""): String? {
        val dateParam = dateIso.filter { it.isDigit() }
        val urls = buildList {
            if (dateParam.length == 8) add("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?dates=$dateParam")
            add("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard")
        }.distinct()

        for (url in urls) {
            val connection = runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "VisionPlus/1.0 (sports scoreboard)")
                }
            }.getOrNull() ?: continue

            val id = try {
                if (connection.responseCode !in 200..299) {
                    null
                } else {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JSONObject(body)
                    val events = root.optJSONArray("events") ?: org.json.JSONArray()
                    findEventId(events, home, away)
                }
            } catch (_: Throwable) {
                null
            } finally {
                connection.disconnect()
            }
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    private fun findEventId(events: org.json.JSONArray, home: String, away: String): String? {
        fun namesOf(ev: JSONObject): Pair<String, String> {
            val comps = ev.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors")
            var h = ""
            var a = ""
            if (comps != null) {
                for (j in 0 until comps.length()) {
                    val c = comps.optJSONObject(j) ?: continue
                    val n = fold(
                        c.optJSONObject("team")?.optString("displayName")
                            ?.ifBlank { c.optJSONObject("team")?.optString("shortDisplayName").orEmpty() }
                            .orEmpty()
                    )
                    when (c.optString("homeAway")) {
                        "home" -> h = n
                        "away" -> a = n
                    }
                }
            }
            return h to a
        }
        fun matches(h: String, a: String): Boolean {
            if (home.isBlank() || away.isBlank()) return false
            val direct = (home in h || h in home) && (away in a || a in away)
            val swapped = (home in a || a in home) && (away in h || h in away)
            return direct || swapped
        }
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val (h, a) = namesOf(ev)
            if (matches(h, a)) return ev.optString("id").trim().ifBlank { null }
            val name = fold(ev.optString("name"))
            val shortName = fold(ev.optString("shortName"))
            val blob = "$name $shortName"
            val hitHome = home.isNotBlank() && (home in blob)
            val hitAway = away.isNotBlank() && (away in blob)
            if (hitHome && hitAway) return ev.optString("id").trim().ifBlank { null }
        }
        return null
    }

    internal fun isSoccerCategory(category: String, leagueId: String = "", leagueLabel: String = ""): Boolean {
        val c = fold(category)
        val lid = leagueId.trim().lowercase()
        val ll = fold(leagueLabel)
        if (lid in SOCCER_LEAGUES || lid.startsWith("soccer/")) return true
        if (c == "football" || c.contains("soccer") || c.contains("futbol")) return true
        if (c.contains("american")) return false
        if (lid.contains("nfl") || lid.contains("ncaa.football") || ll.contains("nfl")) return false
        return false
    }

    private fun sportHint(category: String, leagueId: String = "", leagueLabel: String = ""): String {
        val c = fold(category)
        val lid = leagueId.trim().lowercase()
        val ll = fold(leagueLabel)
        return when {
            lid.contains("nfl") || (c.contains("american") && c.contains("football")) || ll.contains("nfl") -> "football/nfl"
            c.contains("basket") || lid.contains("nba") || lid.contains("basketball") -> when {
                lid.contains("wnba") -> "basketball/wnba"
                else -> "basketball/nba"
            }
            c.contains("baseball") || lid.contains("mlb") || lid.contains("baseball") -> "baseball/mlb"
            c.contains("hockey") || lid.contains("nhl") || lid.contains("hockey") -> "hockey/nhl"
            isSoccerCategory(category, leagueId, leagueLabel) -> "soccer"
            else -> ""
        }
    }

    private fun espnPathFromTeams(
        homeName: String,
        awayName: String,
        category: String = "",
        leagueLabel: String = "",
    ): String? {
        val hint = sportHint(category, "", leagueLabel)
        if (hint.isNotBlank() && hint != "soccer") return hint
        val combined = "$homeName $awayName".lowercase()
        return when {
            combined.contains("nfl") || combined.contains("chiefs") || combined.contains("49ers") ||
                combined.contains("cowboys") || combined.contains("patriots") ||
                combined.contains("eagles") || combined.contains("packers") ||
                combined.contains("steelers") || combined.contains("ravens") ||
                combined.contains("bills") || combined.contains("dolphins") ||
                combined.contains("jets") || combined.contains("giants") -> "football/nfl"
            combined.contains("lakers") || combined.contains("warriors") || combined.contains("celtics") ||
                combined.contains("bulls") || combined.contains("heat") || combined.contains("knicks") ||
                combined.contains("nba") -> "basketball/nba"
            combined.contains("yankees") || combined.contains("dodgers") || combined.contains("red sox") ||
                combined.contains("astros") || combined.contains("mlb") -> "baseball/mlb"
            combined.contains("nhl") || combined.contains("rangers") || combined.contains("bruins") -> "hockey/nhl"
            hint == "soccer" -> null
            else -> null
        }
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
        "fieldGoalsMade-fieldGoalsAttempted" -> "Tiros anotados"
        "threePointFieldGoalPct" -> "Triples"
        "threePointFieldGoalsMade-threePointFieldGoalsAttempted" -> "Triples anotados"
        "freeThrowPct" -> "Tiros libres"
        "freeThrowsMade-freeThrowsAttempted" -> "Tiros libres anotados"
        "totalRebounds" -> "Rebotes"
        "offensiveRebounds" -> "Rebotes ofensivos"
        "defensiveRebounds" -> "Rebotes defensivos"
        "assists" -> "Asistencias"
        "steals" -> "Robos"
        "blocks" -> "Bloqueos"
        "turnovers" -> "Pérdidas de balón"
        "totalYards" -> "Yardas totales"
        "netPassingYards", "passingYards" -> "Yardas por pase"
        "rushingYards" -> "Yardas por carrera"
        "yardsPerPlay" -> "Yardas por jugada"
        "firstDowns" -> "Primeros y diez"
        "thirdDownEff" -> "3.ª oportunidad"
        "fourthDownEff" -> "4.ª oportunidad"
        "totalPenaltiesYards", "penalties" -> "Castigos"
        "possessionTime" -> "Posesión"
        "sacksYardsLost", "sacks" -> "Capturas"
        "completionAttempts", "completions/passingAttempts" -> "Pases completos"
        "rushingAttempts" -> "Acarreos"
        "hits" -> "Hits"
        "runs" -> "Carreras"
        "errors" -> "Errores"
        "homeRuns" -> "Jonrones"
        "totalTackles" -> "Tacleadas"
        "passingTouchdowns" -> "TD por pase"
        "rushingTouchdowns" -> "TD por carrera"
        "totalTouchdowns" -> "Touchdowns"
        else -> null
    }

    /** Orden preferido de estadísticas según el deporte (etiquetas ya traducidas). */
    internal fun statOrderFor(category: String = "", leagueId: String = "", leagueLabel: String = ""): List<String> {
        val c = fold(category)
        val lid = leagueId.trim().lowercase()
        val ll = fold(leagueLabel)
        return when {
            lid.contains("nfl") || (c.contains("american") && c.contains("football")) || ll.contains("nfl") || lid.contains("football/nfl") || lid.contains("college-football") -> listOf(
                "Yardas totales", "Yardas por pase", "Yardas por carrera", "Primeros y diez",
                "3.ª oportunidad", "4.ª oportunidad", "Pases completos", "Acarreos",
                "Pérdidas de balón", "Castigos", "Posesión",
            )
            c.contains("basket") || lid.contains("nba") || lid.contains("basketball") -> listOf(
                "Tiros de campo", "Triples", "Tiros libres", "Rebotes", "Rebotes ofensivos",
                "Rebotes defensivos", "Asistencias", "Robos", "Bloqueos", "Pérdidas de balón",
            )
            c.contains("baseball") || lid.contains("mlb") || lid.contains("baseball") -> listOf(
                "Carreras", "Hits", "Errores", "Jonrones",
            )
            c.contains("hockey") || lid.contains("nhl") || lid.contains("hockey") -> listOf(
                "Tiros", "Tiros a puerta", "Atajadas", "Castigos", "Bloqueos",
            )
            else -> listOf(
                "Posesión", "Tiros", "Tiros a puerta", "Córners", "Faltas",
                "Amarillas", "Rojas", "Atajadas", "Fueras de juego", "Pases", "Precisión de pase",
            )
        }
    }

    private fun espnPath(leagueId: String, category: String = "", leagueLabel: String = ""): String? {
        val raw = leagueId.trim()
        val clean = raw.lowercase()
        if (clean.contains('/')) return raw
        val hint = sportHint(category, raw, leagueLabel)
        if (hint.isNotBlank()) {
            return when {
                hint == "soccer" -> {
                    if (clean.isNotBlank() && (clean in SOCCER_LEAGUES || clean.contains('.'))) "soccer/${raw}" else "soccer/usa.1"
                }
                else -> hint
            }
        }
        if (clean.isBlank()) return null
        if (clean in SOCCER_LEAGUES || clean.contains('.')) return "soccer/$raw"
        return when (clean) {
            "nba" -> "basketball/nba"
            "wnba" -> "basketball/wnba"
            "euroleague" -> "basketball/euroleague"
            "nfl" -> "football/nfl"
            "ncaaf", "college-football", "college football" -> "football/college-football"
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
