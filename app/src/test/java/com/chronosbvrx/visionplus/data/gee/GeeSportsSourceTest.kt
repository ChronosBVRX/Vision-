package com.chronosbvrx.visionplus.data.gee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contrato del servidor (`/v1/gee38/…`) tal como lo sirve el worker: grupos de
 * canales con enlaces ya resueltos (`alive`, `headers`, `drm` kid:key) y eventos
 * por deporte. Si el parseo se rompe, "En vivo" queda vacío sin avisar.
 */
class GeeSportsSourceTest {

    private val groupsJson = """
        {"app":{"app_version":11},"updatedAt":"2026-10-17T00:00:00","groups":[
          {"name":"TNT Sports","logo":"https://l/tnt.png","alive":1,"total":2,"channels":[
            {"name":"TNT Sports","logo":"https://l/tnt2.png","links":[
              {"name":"TNT SPORTS 1","link":"https://x/y/cenc.mpd|user-agent=M",
               "url":"https://x/y/cenc.mpd","headers":{"user-agent":"M","referer":"https://a/"},
               "type":"dash","drm":"kidhex:keyhex","alive":true},
              {"name":"TNT SPORTS 2","link":"https://x/z.m3u8",
               "url":"https://x/z.m3u8","headers":{},"type":"hls","drm":"","alive":null}
            ]}
          ]},
          {"name":"FIFA TV","logo":"","alive":0,"total":0,"channels":[]}
        ]}
    """.trimIndent()

    @Test
    fun parseGroupsReadsChannelsLinksAndFlags() {
        val groups = GeeSportsSource.parseGroups(groupsJson)
        assertEquals(2, groups.size)
        val tnt = groups[0]
        assertEquals("TNT Sports", tnt.name)
        assertEquals(1, tnt.alive)
        assertEquals(2, tnt.total)
        assertEquals(1, tnt.channels.size)
        val links = tnt.channels[0].links
        assertEquals(2, links.size)
        assertEquals("TNT SPORTS 1", links[0].name)
        assertEquals("https://x/y/cenc.mpd|user-agent=M", links[0].link)
        assertEquals("M", links[0].headers["user-agent"])
        assertEquals("https://a/", links[0].headers["referer"])
        assertEquals("dash", links[0].type)
        assertEquals("kidhex:keyhex", links[0].drm)
        assertEquals(true, links[0].alive)
        assertNull(links[1].alive)
        assertTrue(groups[1].channels.isEmpty())
    }

    private val eventsJson = """
        {"events":[
          {"id":"event:abc","category":"Football","event":"Liga MX, Apertura",
           "logo":"https://l/mx.png","team_a":"Necaxa","team_b":"Puebla",
           "date_iso":"2026-10-17","date":"17/10/2026","time":"21:00:00","alive":1,"total":2,
           "home_name":"Necaxa","away_name":"Puebla",
           "home_logo":"https://l/home.png","away_logo":"https://l/away.png",
           "league_label":"Liga MX","league_logo":"https://l/league.png",
           "score":{"home":0,"away":1,"status":"in","detail":"21'","live":true},
           "links":[
             {"name":"CANAL MX FHD","link":"https://cdn/mx.mpd","url":"https://cdn/mx.mpd",
              "headers":{},"type":"dash","drm":"a:b","alive":true},
             {"name":"FOX DEPORTES FHD","link":"https://cdn/fox.mpd","url":"https://cdn/fox.mpd",
              "headers":{},"type":"dash","drm":"c:d","alive":false}
           ]},
          {"id":"event:def","category":"Basketball","event":"NBA: Lakers vs Celtics",
           "logo":"","team_a":"Lakers","team_b":"Celtics",
           "date_iso":"2026-10-18","date":"18/10/2026","time":"20:30:00","alive":0,"total":0,"links":[]}
        ]}
    """.trimIndent()

    @Test
    fun parseEventsReadsTeamsDatesAndLiveScores() {
        val events = GeeSportsSource.parseEvents(eventsJson)
        assertEquals(2, events.size)
        val football = events[0]
        assertEquals("Football", football.category)
        assertEquals("Liga MX, Apertura", football.name)
        assertEquals("2026-10-17", football.dateIso)
        assertEquals("21:00:00", football.time)
        assertEquals(2, football.links.size)
        // El cruce del servidor: equipos con nombre fino, escudos, liga y marcador real.
        assertEquals("Necaxa", football.homeName)
        assertEquals("Puebla", football.awayName)
        assertEquals("https://l/home.png", football.homeLogo)
        assertEquals("Liga MX", football.leagueLabel)
        assertEquals(0, football.scoreHome)
        assertEquals(1, football.scoreAway)
        assertTrue(football.live)
        assertEquals("21'", football.detail)
        assertEquals("in", football.status)
        // Un deporte sin cruce: sin marcador, sin liga.
        val basket = events[1]
        assertEquals("Basketball", basket.category)
        assertNull(basket.scoreHome)
        assertEquals("", basket.leagueLabel)
        assertFalse(basket.live)
    }

    @Test
    fun parseToleratesGarbageWithoutCrashing() {
        assertTrue(GeeSportsSource.parseGroups("no json").isEmpty())
        assertTrue(GeeSportsSource.parseEvents("{}").isEmpty())
    }

    @Test
    fun toChannelCarriesHeadersAndClearKey() {
        val dash = GeeSportsSource.GeeLink(
            name = "TNT SPORTS 1",
            link = "https://x/y/cenc.mpd|user-agent=UA",
            headers = mapOf("user-agent" to "UA", "referer" to "https://a/", "origin" to "https://a"),
            type = "dash",
            drm = "kidhex:keyhex",
            alive = true,
        )
        val channel = GeeSportsSource.toChannel(dash, "TNT SPORTS 1", "GeeSports")
        // La URL viaja tal cual (con sus cabeceras tras `|`): PlayerScreen ya la sabe partir.
        assertEquals("https://x/y/cenc.mpd|user-agent=UA", channel.url)
        assertEquals("UA", channel.userAgent)
        assertEquals("https://a/", channel.referrer)
        assertEquals("https://a", channel.origin)
        assertEquals("kidhex:keyhex", channel.drmKey)
        assertEquals("dash", channel.drmType)
        assertEquals("GeeSports", channel.group)
        assertEquals("TNT SPORTS 1", channel.name)
    }

    @Test
    fun toChannelLeavesHlsWithoutDrm() {
        val hls = GeeSportsSource.GeeLink(
            name = "Azteca 7",
            link = "https://cdn/az.m3u8",
            headers = emptyMap(),
            type = "hls",
            drm = "",
            alive = false,
        )
        val channel = GeeSportsSource.toChannel(hls)
        assertNull(channel.drmKey)
        assertNull(channel.drmType)
        assertEquals("https://cdn/az.m3u8", channel.url)
        assertEquals("Azteca 7", channel.name)
    }
}
