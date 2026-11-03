package com.chronosbvrx.visionplus.data.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El selector de Vision+ Play explica jugadores, duración y rondas sin depender de la red. */
class GameCatalogTest {

    @Test
    fun thereAreSixDistinctFiestaGames() {
        assertEquals(6, GameCatalog.all.size)
        assertEquals(6, GameCatalog.all.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "ultima_palabra", "vox_populi", "fuera_de_epoca", "texto_roto",
                "franela_fest", "el_tasador",
            ),
            GameCatalog.all.map { it.id }.toSet(),
        )
    }

    @Test
    fun everyGameExplainsPlayersTimeAndRounds() {
        GameCatalog.all.forEach { info ->
            assertTrue(info.title.isNotBlank())
            assertTrue(info.tagline.isNotBlank())
            assertTrue(info.description.isNotBlank())
            assertTrue(info.playersLabel.contains("jugador"))
            assertTrue(info.timeLabel.contains("min"))
            assertTrue(info.roundsLabel.contains("rondas"))
            assertTrue(info.steps.size >= 3)
            assertTrue(info.maxPlayers >= info.minPlayers)
        }
    }

    @Test
    fun charadasNeedsAtLeastTwoPeople() {
        assertEquals(2, GameCatalog.Charadas.minPlayers)
        assertEquals("2–8 jugadores", GameCatalog.Charadas.playersLabel)
    }

    @Test
    fun triviaHasSectionsBeyondGeneral() {
        val ids = GameCatalog.triviaPacks.map { it.id }
        assertTrue(ids.contains("general"))
        assertTrue(ids.contains("cine"))
        assertTrue(ids.contains("series"))
        assertTrue(ids.contains("anime"))
        assertTrue(ids.contains("infantil"))
        assertTrue(ids.contains("novelas"))
        assertEquals("general", GameCatalog.triviaPack("no-existe").id)
    }

    @Test
    fun theCatalogFallsBackToTriviaAndRejectsUnknownGames() {
        assertEquals("trivia", GameCatalog.byId("no-existe").id)
        assertEquals("trivia", GameCatalog.byId(null).id)
        assertTrue(GameCatalog.isValid("ultima_palabra"))
        assertTrue(!GameCatalog.isValid("charadas"))
        assertTrue(!GameCatalog.isValid("trivia"))
        assertTrue(!GameCatalog.isValid("bingo"))
    }
}
