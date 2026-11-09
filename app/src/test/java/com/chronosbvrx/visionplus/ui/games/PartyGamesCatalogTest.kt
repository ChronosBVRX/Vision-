package com.chronosbvrx.visionplus.ui.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyGamesCatalogTest {
    @Test
    fun catalogShowsPlayersTimeAndDescriptorForEveryGame() {
        assertEquals(13, PARTY_GAMES.size)

        for (game in PARTY_GAMES) {
            assertTrue("${game.id} necesita jugadores mínimos", game.minPlayers >= 1)
            assertTrue("${game.id} tiene un rango de jugadores inválido", game.maxPlayers >= game.minPlayers)
            assertTrue("${game.id} necesita un descriptor", game.tag.isNotBlank())
            assertTrue(
                "${game.id} necesita un tiempo aproximado",
                game.duration.matches(Regex("\\d+–\\d+ min")),
            )
        }
    }

    @Test
    fun welcomeTracksArePackaged() {
        val tracks = listOf(
            com.chronosbvrx.visionplus.R.raw.party_welcome_0,
            com.chronosbvrx.visionplus.R.raw.party_welcome_1,
            com.chronosbvrx.visionplus.R.raw.party_welcome_2,
            com.chronosbvrx.visionplus.R.raw.party_welcome_3,
            com.chronosbvrx.visionplus.R.raw.party_welcome_4,
            com.chronosbvrx.visionplus.R.raw.party_welcome_5,
            com.chronosbvrx.visionplus.R.raw.party_welcome_6,
            com.chronosbvrx.visionplus.R.raw.party_welcome_7,
        )

        assertEquals(8, tracks.distinct().size)
    }
}
