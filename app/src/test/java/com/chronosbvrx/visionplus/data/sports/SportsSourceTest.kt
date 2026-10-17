package com.chronosbvrx.visionplus.data.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del parseo de la agenda: el servidor manda JSON, la app lo ordena
 * por kickoff y lo re-serializa a prefs. Si esto se rompe, la pantalla Fútbol
 * sale vacía o desordenada.
 */
class SportsSourceTest {

    private val sample = """
        {"matches": [
          {"id":"b","league":{"id":"mex.1","name":"Liga MX","logo":"l"},
           "home":{"id":"h2","name":"América","short":"AME","logo":"","score":1},
           "away":{"id":"a2","name":"Cruz Azul","short":"CAZ","logo":"","score":2},
           "kickoff":"2026-10-10T02:00:00Z","status":"post","detail":"FT","venue":"",
           "channels":["Canal 5"],"broadcasts":[]},
          {"id":"a","league":{"id":"mex.1","name":"Liga MX","logo":"l"},
           "home":{"id":"h1","name":"Guadalajara","short":"GDL","logo":""},
           "away":{"id":"a1","name":"Atlas","short":"ATL","logo":"","score":"0"},
           "kickoff":"2026-10-10T00:00:00Z","status":"pre","detail":"","venue":"",
           "channels":["TUDN"],"broadcasts":[]},
          {"id":"","league":{},"home":{},"away":{},"kickoff":"","status":"pre",
           "detail":"","venue":"","channels":[],"broadcasts":[]}
        ]}
    """.trimIndent()

    @Test
    fun parseSortsByKickoffAndSkipsBlankIds() {
        val matches = SportsSource.parse(sample)
        assertEquals(listOf("a", "b"), matches.map { it.id })
        assertEquals("Guadalajara", matches.first().home.name)
        assertEquals(listOf("TUDN"), matches.first().channels)
    }

    @Test
    fun parseReadsScoresAsNumbersOrStrings() {
        val matches = SportsSource.parse(sample)
        assertEquals(1, matches[1].home.score)
        assertEquals(2, matches[1].away.score)
        assertEquals(0, matches[0].away.score)
        assertEquals(null, matches[0].home.score)
    }

    @Test
    fun parseToleratesGarbage() {
        assertTrue(SportsSource.parse("").isEmpty())
        assertTrue(SportsSource.parse("no-json").isEmpty())
        assertTrue(SportsSource.parse("""{"matches":[]}""").isEmpty())
        assertTrue(SportsSource.parse("""{"other":1}""").isEmpty())
    }

    @Test
    fun kickoffParsesTheFormatsTheServerSends() {
        assertTrue(SportsSource.parseKickoff("2026-10-10T00:00:00Z") > 0)
        assertTrue(SportsSource.parseKickoff("2026-10-10T00:00:00+00:00") > 0)
        assertTrue(SportsSource.parseKickoff("2026-10-10T00:00:00") > 0)
        assertEquals(0L, SportsSource.parseKickoff(""))
        assertEquals(0L, SportsSource.parseKickoff("mañana"))
    }

    @Test
    fun jsonRoundTripKeepsMatches() {
        val matches = SportsSource.parse(sample)
        val back = SportsSource.parse(SportsSource.toJson(matches))
        assertEquals(matches.map { it.id }, back.map { it.id })
        assertEquals(matches.map { it.kickoffMs }, back.map { it.kickoffMs })
        assertEquals(matches.first().channels, back.first().channels)
    }

    @Test
    fun shiftMovesCalendarDays() {
        assertEquals("2026-10-11", SportsSource.shift("2026-10-10", 1))
        assertEquals("2026-10-09", SportsSource.shift("2026-10-10", -1))
        assertEquals("2026-10-31", SportsSource.shift("2026-10-30", 1))
        assertEquals("2027-01-01", SportsSource.shift("2026-12-31", 1))
    }

    private fun utcMs(iso: String): Long =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(iso)!!
            .time

    @Test
    fun dayMathFollowsMexicoCentralEvenOnOtherTimezones() {
        // La agenda (servidor y chips «Hoy/Mañana») vive en el centro de México: a las
        // 02:00 UTC del día 18 en el centro todavía es 20:00 del 17.
        assertEquals("2026-10-17", SportsSource.todayIso(utcMs("2026-10-18T02:00:00")))
        assertEquals("2026-10-18", SportsSource.todayIso(utcMs("2026-10-18T07:00:00")))
        // Y el corte de día cae a las 06:00 UTC (medianoche del centro, UTC−6 en octubre).
        assertEquals("2026-10-17", SportsSource.todayIso(utcMs("2026-10-18T05:59:59")))
        assertEquals("2026-10-18", SportsSource.todayIso(utcMs("2026-10-18T06:00:01")))
    }

    @Test
    fun kickoffLabelFollowsMexicoCentral() {
        // 01:00 UTC del 18 = 19:00 del 17 en la Ciudad de México.
        assertEquals("19:00", SportsSource.timeLabel(utcMs("2026-10-18T01:00:00")))
        assertEquals("07:30", SportsSource.timeLabel(utcMs("2026-10-17T13:30:00")))
        assertEquals("", SportsSource.timeLabel(0L))
    }

    @Test
    fun byLeagueGroupingHandlesEmptyAndMultipleLeagues() {
        val matches = listOf(
            SportMatch("1", SportLeague("mex.1", "Liga MX", ""), SportTeam("h1", "América", "", "", 1), SportTeam("a1", "Chivas", "", "", 0), 1000L, "in", "45'", "", emptyList(), emptyList()),
            SportMatch("2", SportLeague("mex.1", "Liga MX", ""), SportTeam("h2", "Cruz Azul", "", "", 0), SportTeam("a2", "Pumas", "", "", 0), 2000L, "pre", "", "", emptyList(), emptyList()),
            SportMatch("3", SportLeague("", "Amistosos", ""), SportTeam("h3", "Real Madrid", "", "", null), SportTeam("a3", "Barcelona", "", "", null), 3000L, "pre", "", "", emptyList(), emptyList()),
        )
        val byLeague = matches.groupBy { it.league.id }.map { (id, list) -> id to list }
        assertEquals(2, byLeague.size)
        assertEquals("mex.1", byLeague[0].first)
        assertEquals(2, byLeague[0].second.size)
        assertEquals("", byLeague[1].first)
        assertEquals(1, byLeague[1].second.size)
    }

    @Test
    fun leagueFilterSubsetSelection() {
        val matches = listOf(
            SportMatch("1", SportLeague("mex.1", "Liga MX", ""), SportTeam("h1", "América", "", "", 1), SportTeam("a1", "Chivas", "", "", 0), 1000L, "in", "45'", "", emptyList(), emptyList()),
            SportMatch("2", SportLeague("esp.1", "LaLiga", ""), SportTeam("h2", "Real Madrid", "", "", 0), SportTeam("a2", "Barcelona", "", "", 0), 2000L, "pre", "", "", emptyList(), emptyList()),
        )
        val allLeagues = matches.map { it.league }.distinctBy { it.id }
        assertEquals(2, allLeagues.size)

        val selectedId = "esp.1"
        val filtered = matches.filter { it.league.id == selectedId }
        assertEquals(1, filtered.size)
        assertEquals("LaLiga", filtered.first().league.name)
    }
}
