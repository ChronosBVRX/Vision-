package com.chronosbvrx.visionplus.data.games

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiestaModelsTest {
    @Test
    fun parsesTheGenericFiestaPayload() {
        val raw = JSONObject(
            """
            {
              "code": "ABC234",
              "displayCode": "ABC-234",
              "mode": "display",
              "game": "franela_fest",
              "rulesVersion": 3,
              "pack": "franela_fest",
              "phase": "vote",
              "rev": 9,
              "serverNow": 1000,
              "phaseEndsAt": 2000,
              "round": 1,
              "rounds": 3,
              "hostId": "p1",
              "players": [{"id":"p1","name":"Ana","color":1,"score":300}],
              "fiesta": {
                "game": "franela_fest",
                "title": "Franela Fest",
                "host": "critico",
                "phase": "vote",
                "screen": "vote",
                "prompt": "Diseña una camiseta",
                "instruction": "Vota",
                "hint": "",
                "round": 1,
                "rounds": 3,
                "options": [{
                  "id":"f0","text":"Eslogan","votes":2,"isYours":false,
                  "authorName":"","role":"","slogan":"Eslogan","value":0,"count":0,"rank":0,
                  "strokes":[{"color":"#F97316","width":8,"points":[[10,20],[300,400]]}]
                }],
                "years": [],
                "correct": -1,
                "object": null,
                "trueValue": 0,
                "you": {"submitted":true,"voted":false,"vote":"","text":"","choice":-1,"value":0,"points":300,"score":300,"role":""},
                "submittedCount": 1,
                "voterCount": 3,
                "reveal": null,
                "hostCue": {"voice":"critico","moment":"vote","text":"Voten"},
                "family": false,
                "adult": true,
                "scoreboard": [{"id":"p1","name":"Ana","color":1,"score":300}]
              }
            }
            """.trimIndent(),
        )
        val snap = GameSnapshot.fromJson(raw)
        assertTrue(snap.isFiesta)
        assertEquals("franela_fest", snap.fiesta?.game)
        assertEquals("vote", snap.fiesta?.screen)
        assertEquals(1, snap.fiesta?.options?.size)
        assertEquals(1, snap.fiesta?.options?.first()?.strokes?.size)
        assertTrue(snap.fiesta?.adult == true)
        assertEquals(300, snap.fiesta?.you?.score)
    }

    @Test
    fun theSixGamesHaveCatalogEntriesAndNewScreensAreWired() {
        assertEquals(6, FiestaCatalog.ids.size)
        FiestaCatalog.ids.forEach { id ->
            assertTrue(GameCatalog.isValid(id))
        }
        val phone = java.io.File("src/main/java/com/chronosbvrx/visionplus/ui/games/GameRoomScreen.kt").readText()
        val tv = java.io.File("src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt").readText()
        assertTrue(phone.contains("FiestaWorldPhone"))
        assertTrue(phone.contains("FiestaCatalog.isFiesta"))
        assertTrue(tv.contains("FiestaTvContent"))
        assertTrue(tv.contains("family = !adultMode"))
    }
    @Test
    fun theEconomyAndTauntPayloadIsParsed() {
        val raw = org.json.JSONObject(
            """
            {
              "code":"ECO234","game":"ultima_palabra","phase":"reveal","rev":9,
              "info":{"id":"ultima_palabra","title":"La Última Palabra","accent":"#F97316"},
              "players":[],
              "fiesta":{
                "game":"ultima_palabra","title":"La Última Palabra","host":"nora",
                "phase":"reveal","screen":"reveal","prompt":"","instruction":"","hint":"",
                "round":2,"rounds":6,"options":[],"years":[],"correct":-1,
                "object":null,"trueValue":0,
                "you":{"submitted":true,"voted":true,"vote":"a1","text":"hola","choice":-1,
                  "value":0,"points":300,"score":900,"role":"","coins":250,
                  "perk":"double","activePerk":"double","bonusPick":-1,"submittedOrder":1,
                  "bet":100,"betWon":true,"betPayout":300,"betMultiplier":3,
                  "hint":"Nora sopló","hintYears":[0,2],"hintUsed":true},
                "submittedCount":3,"voterCount":3,"reveal":null,"hostCue":null,
                "family":true,"adult":false,"soloTest":false,"scoreboard":[],
                "twist":{"id":"double","title":"DOBLE RISA","description":"x","multiplier":2,"duration":1,"coin":2},
                "perks":[],"bonus":null,"banter":"","coins":250,"perk":"double",
                "activePerk":"double","currency":"Risas","currencyIcon":"JA",
                "shop":"La Trastienda de Nora","signature":"concurso",
                "taunts":[{"id":"t0","name":"Ana","text":"JA","color":"#FF0000"}],
                "tauntChoices":["JA","SE FUE"],"hintCost":120,"tauntCost":60,
                "betOptions":[100,250,500]
              }
            }
            """.trimIndent(),
        )
        val info = GameSnapshot.fromJson(raw).fiesta!!
        assertEquals("Risas", info.currency)
        assertEquals("La Trastienda de Nora", info.shop)
        assertEquals(100, info.you?.bet)
        assertEquals(300, info.you?.betPayout)
        assertEquals(3, info.you?.betMultiplier)
        assertEquals(listOf(0, 2), info.you?.hintYears)
        assertEquals("JA", info.taunts.first().text)
        assertEquals(listOf("JA", "SE FUE"), info.tauntChoices)
        assertEquals(120, info.hintCost)
        assertEquals(listOf(100, 250, 500), info.betOptions)
    }

    @Test
    fun parsesTheFiesta4AsymmetricAndEconomicPayload() {
        val raw = JSONObject(
            """
            {
              "code":"F4A234","game":"el_tasador","phase":"invest","rev":12,
              "players":[],
              "fiesta":{
                "game":"el_tasador","title":"El Tasador","host":"tasador",
                "phase":"invest","screen":"invest","prompt":"Dos lotes","instruction":"Puja","hint":"",
                "round":3,"rounds":6,"options":[],"years":[],"correct":-1,
                "object":{"name":"Cuchara fría","description":"Fría"},
                "trueValue":0,
                "duelState":{"duelIndex":1,"duelTotal":4,"prompt":"¿Cuál duele más?","isFinal":false,
                  "optionA":{"id":"a","text":"A"},"optionB":{"id":"b","text":"B"},"results":[]},
                "voxState":{"rank3":{"text":"Tercer lugar","rank":3},
                  "mysteryAnswer":{"text":"Respuesta oculta"},"quickChips":{}},
                "sortItems":[{"id":"e1","title":"Evento 1"},{"id":"e2","title":"Evento 2","year":1999}],
                "chatMessages":[{"id":"m0","text":"hola","role":"WRITER","authorName":"ANÓNIMO","reactions":4}],
                "lots":[{"id":"A","object":{"name":"Lote A"},"sellerName":"Ana","pitch":"Es único",
                  "invested":250,"authentic":true,"realValue":900}],
                "reactionBanner":"UNDERDOG WIN",
                "audienceVotes":{"a":2,"b":1},
                "you":{"submitted":false,"voted":false,"vote":"","text":"","choice":-1,"value":0,
                  "points":0,"score":0,"role":"","screen":"invest","quickChips":["uno","dos"],
                  "chatRole":"","secretGoal":"","remixPool":{
                    "drawings":[{"id":"d1","ownerId":"p2","ownerName":"Beto",
                      "strokes":[{"color":"#fff","width":3,"points":[[1,2],[3,4]]}]}],
                    "slogans":[{"id":"s1","ownerId":"p3","ownerName":"Caro","text":"Eslogan"}]},
                  "remixChoice":{"drawing":"d1","slogan":"s1"},
                  "incomingTaunt":{"id":"t1","from":"Beto","text":"JA"},
                  "finalBoost":0.5,"lotRole":"BUYER","lotId":"A","lotHint":"Parece legítimo",
                  "waitBonus":true},
                "submittedCount":1,"voterCount":3,"reveal":null,"hostCue":null,
                "family":true,"adult":false,"scoreboard":[]
              }
            }
            """.trimIndent(),
        )
        val info = GameSnapshot.fromJson(raw).fiesta!!
        assertEquals("invest", info.screen)
        assertEquals(1, info.duelState?.duelIndex)
        assertEquals("A", info.duelState?.optionA?.text)
        assertEquals(4, info.duelState?.totalDuels)
        assertEquals("Tercer lugar", info.voxState?.anchorRank3Text)
        assertEquals("Respuesta oculta", info.voxState?.mysteryAnswer)
        assertEquals(2, info.sortItems.size)
        assertEquals(1999, info.sortItems[1].year)
        assertEquals(4, info.chatMessages.first().reactionTotal)
        assertEquals("Lote A", info.lots.first().title)
        assertEquals(true, info.lots.first().isAuthentic)
        assertEquals(250, info.lots.first().totalBids)
        assertEquals("UNDERDOG WIN", info.reactionBanner)
        assertEquals(2, info.audienceVotes["a"])
        assertEquals("invest", info.you?.screen)
        assertEquals(listOf("uno", "dos"), info.you?.quickChips)
        assertEquals("d1", info.you?.remixPool?.drawings?.first()?.id)
        assertEquals("s1", info.you?.remixChoice?.sloganId)
        assertEquals("Beto", info.you?.incomingTaunt?.from)
        assertEquals(0.5, info.you?.finalBoost ?: 0.0, 0.0001)
        assertEquals("BUYER", info.you?.lotRole)
        assertEquals("A", info.you?.lotId)
        assertEquals("Parece legítimo", info.you?.lotHint)
        assertTrue(info.you?.waitBonus == true)
    }

    @Test
    fun fiesta4FieldsAreOptionalForLegacySnapshots() {
        val legacy = JSONObject(
            """
            {"fiesta":{"game":"ultima_palabra","title":"Viejo","host":"nora","phase":"write",
              "screen":"text_input","prompt":"","instruction":"","hint":"","round":1,"rounds":3,
              "options":[],"years":[],"correct":-1,"object":null,"trueValue":0,
              "you":{"submitted":false,"voted":false,"vote":"","text":"","choice":-1,"value":0,
                "points":0,"score":0,"role":""},
              "submittedCount":0,"voterCount":1,"reveal":null,"hostCue":null,
              "family":false,"adult":true,"scoreboard":[]}}
            """.trimIndent(),
        )
        val info = GameSnapshot.fromJson(legacy).fiesta!!
        assertEquals(null, info.duelState)
        assertEquals(null, info.voxState)
        assertTrue(info.sortItems.isEmpty())
        assertTrue(info.chatMessages.isEmpty())
        assertTrue(info.lots.isEmpty())
        assertTrue(info.audienceVotes.isEmpty())
        assertEquals(null, info.you?.incomingTaunt)
        assertEquals(0.0, info.you?.finalBoost ?: 0.0, 0.0001)
    }

}
