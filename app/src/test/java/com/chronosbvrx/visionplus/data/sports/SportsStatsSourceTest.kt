package com.chronosbvrx.visionplus.data.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El resumen de ESPN no siempre usa los nombres «bonitos»: córners llega como
 * `wonCorners`, faltas como `foulsCommitted` y las precisiones como fracción
 * (0.9 = 90%). La alineación viene en `rosters[].roster` y hay que quedarse con
 * los titulares. Esta captura reproduce el partido Chelsea–Manchester United.
 */
class SportsStatsSourceTest {

    private val summary = """
        {
          "boxscore": {
            "teams": [
              {
                "team": {"displayName": "Chelsea"},
                "statistics": [
                  {"name": "possessionPct", "displayValue": "58.7"},
                  {"name": "totalShots", "displayValue": "18"},
                  {"name": "shotsOnTarget", "displayValue": "9"},
                  {"name": "wonCorners", "displayValue": "8"},
                  {"name": "foulsCommitted", "displayValue": "8"},
                  {"name": "yellowCards", "displayValue": "1"},
                  {"name": "redCards", "displayValue": "0"},
                  {"name": "saves", "displayValue": "3"},
                  {"name": "passPct", "displayValue": "0.9"}
                ]
              },
              {
                "team": {"displayName": "Manchester United"},
                "statistics": [
                  {"name": "possessionPct", "displayValue": "41.3"},
                  {"name": "totalShots", "displayValue": "12"},
                  {"name": "shotsOnTarget", "displayValue": "5"},
                  {"name": "wonCorners", "displayValue": "7"},
                  {"name": "foulsCommitted", "displayValue": "20"},
                  {"name": "yellowCards", "displayValue": "4"},
                  {"name": "redCards", "displayValue": "1"},
                  {"name": "saves", "displayValue": "7"},
                  {"name": "passPct", "displayValue": "0.8"}
                ]
              }
            ]
          },
          "rosters": [
            {
              "team": {"displayName": "Chelsea"},
              "roster": [
                {"starter": true, "athlete": {"displayName": "Robert Sánchez"}},
                {"starter": true, "athlete": {"displayName": "Reece James"}},
                {"starter": false, "athlete": {"displayName": "Suplente"}}
              ]
            },
            {
              "team": {"displayName": "Manchester United"},
              "roster": [
                {"starter": true, "athlete": {"displayName": "André Onana"}},
                {"starter": true, "athlete": {"displayName": "Bruno Fernandes"}}
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun theEspnNamesBecomeTheLabelsThePanelShows() {
        val stats = requireNotNull(SportsStatsSource.parseSummary(summary))
        assertEquals("Chelsea", stats.home.name)
        assertEquals("Manchester United", stats.away.name)
        assertEquals("58.7", stats.home.values["Posesión"])
        assertEquals("18", stats.home.values["Tiros"])
        assertEquals("9", stats.home.values["Tiros a puerta"])
        assertEquals("8", stats.home.values["Córners"])
        assertEquals("8", stats.home.values["Faltas"])
        assertEquals("1", stats.home.values["Amarillas"])
        assertEquals("0", stats.home.values["Rojas"])
        assertEquals("3", stats.home.values["Atajadas"])
        assertEquals("90%", stats.home.values["Precisión de pase"])
        assertEquals("20", stats.away.values["Faltas"])
    }

    @Test
    fun theLineupKeepsTheStartersAndDropsTheBench() {
        val stats = requireNotNull(SportsStatsSource.parseSummary(summary))
        assertEquals(listOf("Robert Sánchez", "Reece James"), stats.home.lineup)
        assertEquals(listOf("André Onana", "Bruno Fernandes"), stats.away.lineup)
    }

    @Test
    fun aSummaryWithoutABoxscoreDoesNotInventStats() {
        assertNull(SportsStatsSource.parseSummary("""{"rosters":[]}"""))
        assertNull(SportsStatsSource.parseSummary("""{"boxscore":{"teams":[]}}"""))
    }

    @Test
    fun theRosterFallbackForOtherSportsStillListsPlayers() {
        val body = """
            {
              "boxscore": {"teams": [
                {"team": {"displayName": "Lakers"}, "statistics": []},
                {"team": {"displayName": "Celtics"}, "statistics": []}
              ]},
              "rosters": [
                {"team": {"displayName": "Lakers"}, "athletes": [
                  {"athlete": {"displayName": "LeBron James"}},
                  {"athlete": {"displayName": "Luka Doncic"}}
                ]}
              ]
            }
        """.trimIndent()
        val stats = requireNotNull(SportsStatsSource.parseSummary(body))
        assertTrue(stats.home.lineup.contains("LeBron James"))
        assertEquals(2, stats.home.lineup.size)
    }

    @Test
    fun goalScorersAndTeamLogosComeFromTheSameSummary() {
        val body = """
            {
              "boxscore": {"teams": [
                {"team": {"displayName": "Monterrey", "logo": "https://cdn/mty.png"}, "statistics": []},
                {"team": {"displayName": "Tijuana", "logo": "https://cdn/tij.png"}, "statistics": []}
              ]},
              "keyEvents": [
                {"scoringPlay": true, "type": {"text": "Goal - Header"},
                 "text": "Goal! Monterrey 0, Tijuana 1. Jackson Porozo header.",
                 "team": {"displayName": "Tijuana"},
                 "participants": [{"athlete": {"displayName": "Jackson Porozo"}}],
                 "clock": {"value": 282.0, "displayValue": "5'"}},
                {"scoringPlay": true, "type": {"text": "Penalty - Scored"},
                 "text": "Goal! Monterrey 1, Tijuana 1. Sergio Canales penalty.",
                 "team": {"displayName": "Monterrey"},
                 "participants": [{"athlete": {"displayName": "Sergio Canales"}}],
                 "clock": {"value": 600.0, "displayValue": "10'"}},
                {"scoringPlay": false, "type": {"text": "Yellow Card"}, "team": {"displayName": "Monterrey"}}
              ]
            }
        """.trimIndent()
        val stats = requireNotNull(SportsStatsSource.parseSummary(body))
        assertEquals("https://cdn/mty.png", stats.home.logo)
        assertEquals("https://cdn/tij.png", stats.away.logo)
        assertEquals(2, stats.goals.size)
        assertEquals("Jackson Porozo", stats.goals[0].player)
        assertEquals("5'", stats.goals[0].minute)
        assertEquals("Tijuana", stats.goals[0].team)
        assertEquals("Sergio Canales", stats.goals[1].player)
        assertTrue(stats.goals[1].penalty)
    }

    @Test
    fun theFormationNumbersAndTimelineComeFromTheSameSummary() {
        val body = """
            {
              "boxscore": {"teams": [
                {"team": {"displayName": "Liverpool"}, "statistics": []},
                {"team": {"displayName": "Arsenal"}, "statistics": []}
              ]},
              "rosters": [
                {"team": {"displayName": "Liverpool"}, "formation": "4-2-3-1", "roster": [
                  {"starter": true, "jersey": "1", "position": {"abbreviation": "G"},
                   "athlete": {"displayName": "Alisson Becker"}},
                  {"starter": true, "jersey": "4", "position": {"abbreviation": "CD-L"},
                   "athlete": {"displayName": "Virgil van Dijk"}}
                ]},
                {"team": {"displayName": "Arsenal"}, "formation": "4-3-3", "roster": [
                  {"starter": true, "jersey": "1", "position": {"abbreviation": "G"},
                   "athlete": {"displayName": "David Raya"}}
                ]}
              ],
              "keyEvents": [
                {"scoringPlay": true, "type": {"text": "Goal"},
                 "team": {"displayName": "Arsenal"},
                 "participants": [{"athlete": {"displayName": "Kai Havertz"}}],
                 "clock": {"value": 960.0, "displayValue": "16'"}},
                {"scoringPlay": false, "type": {"text": "Yellow Card"},
                 "team": {"displayName": "Liverpool"},
                 "participants": [{"athlete": {"displayName": "Bradley Barcola"}}],
                 "clock": {"value": 2700.0, "displayValue": "45'+2'"}},
                {"scoringPlay": false, "type": {"text": "Substitution"},
                 "team": {"displayName": "Liverpool"},
                 "participants": [
                   {"athlete": {"displayName": "Dominik Szoboszlai"}},
                   {"athlete": {"displayName": "Trey Nyoni"}}
                 ],
                 "clock": {"value": 3540.0, "displayValue": "59'"}},
                {"scoringPlay": false, "type": {"text": "Second Yellow Card"},
                 "team": {"displayName": "Arsenal"},
                 "participants": [{"athlete": {"displayName": "Myles Lewis-Skelly"}}],
                 "clock": {"value": 4320.0, "displayValue": "72'"}}
              ]
            }
        """.trimIndent()
        val stats = requireNotNull(SportsStatsSource.parseSummary(body))
        assertEquals("4-2-3-1", stats.home.formation)
        assertEquals("4-3-3", stats.away.formation)
        assertEquals("1", stats.home.players[0].number)
        assertEquals("Alisson Becker", stats.home.players[0].name)
        assertEquals("G", stats.home.players[0].position)
        assertEquals(4, stats.events.size)
        assertEquals(MatchEventType.GOAL, stats.events[0].type)
        assertEquals("16'", stats.events[0].minute)
        assertEquals(MatchEventType.YELLOW, stats.events[1].type)
        assertEquals(MatchEventType.SUBSTITUTION, stats.events[2].type)
        assertEquals("por Trey Nyoni", stats.events[2].detail)
        assertEquals(MatchEventType.RED, stats.events[3].type)
    }
}
