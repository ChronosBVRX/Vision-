package com.chronosbvrx.visionplus.data.sports

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsFavoritesTest {

    private fun event(
        id: String = "e1",
        home: String = "Chivas",
        away: String = "América",
        name: String = "Chivas vs América",
    ) = GeeSportsSource.GeeEvent(
        id = id,
        category = "Football",
        name = name,
        logo = "",
        teamA = home,
        teamB = away,
        dateIso = "2026-10-29",
        dateText = "Hoy",
        time = "20:00:00",
        alive = 1,
        total = 1,
        links = emptyList(),
        homeName = home,
        awayName = away,
        leagueLabel = "Liga MX",
    )

    @Test
    fun markingAMatchFollowsBothTeams() {
        val next = SportsFavorites.toggle(event(), emptySet())
        assertEquals(setOf("chivas", "america"), next)
        assertTrue(SportsFavorites.matches(event(), next))
    }

    @Test
    fun aLaterMatchOfEitherTeamIsAFavoriteToo() {
        val favorites = SportsFavorites.toggle(event(), emptySet())
        assertTrue(SportsFavorites.matches(event(id = "e2", home = "Chivas", away = "Toluca"), favorites))
        assertTrue(SportsFavorites.matches(event(id = "e3", home = "Puebla", away = "América"), favorites))
    }

    @Test
    fun unmarkingRemovesBothTeams() {
        val favorites = SportsFavorites.toggle(event(), emptySet())
        val next = SportsFavorites.toggle(event(), favorites)
        assertTrue(next.isEmpty())
        assertFalse(SportsFavorites.matches(event(), next))
    }

    @Test
    fun accentsAndCaseDoNotDuplicateKeys() {
        val next = SportsFavorites.toggle(event(home = "AMÉRICA", away = "Chivas"), emptySet())
        assertEquals(setOf("america", "chivas"), next)
    }

    @Test
    fun theClubPrefixDoesNotSplitTheSameTeam() {
        val favorites = SportsFavorites.toggle(event(home = "Club América", away = "Chivas"), emptySet())
        assertTrue(SportsFavorites.matches(event(home = "América", away = "Toluca"), favorites))
    }

    @Test
    fun aTournamentWithoutTeamsUsesItsName() {
        val race = event(id = "f1", home = "", away = "", name = "Gran Premio de México")
        assertTrue(SportsFavorites.matches(race, SportsFavorites.toggle(race, emptySet())))
    }
}
