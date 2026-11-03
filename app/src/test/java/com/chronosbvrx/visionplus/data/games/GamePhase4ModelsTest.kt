package com.chronosbvrx.visionplus.data.games

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de los juegos de fiesta asimétricos (Fase 4). */
class GamePhase4ModelsTest {

    @Test
    fun impostorPhoneKeepsTheSecretAndThePublicDisplayDoesNot() {
        val json = """
            {"code":"ABC234","displayCode":"ABC-234","mode":"display","game":"impostor",
             "phase":"secret","rev":4,"serverNow":1000,"phaseEndsAt":12000,"round":1,"rounds":3,
             "hostId":"p0","players":[{"id":"p0","name":"Ana","color":0,"score":0,"connected":true,"isHost":true}],
             "impostor":{"category":"Lugares de cine","hint":"Maletas","word":"Aeropuerto",
               "role":"crew","speakerId":"p0","speakerName":"Ana","speakerIndex":1,"speakerCount":3,
               "question":"¿Qué ropa llevarías?","voted":false,"votedCount":0,"voterCount":3,
               "votes":{},"impostorId":"","impostorName":"","caught":false,"guess":"",
               "guessOptions":[],"guessCorrect":false,"points":0},
             "you":{"id":"p0","name":"Ana","score":0,"streak":0,"color":0,"answered":false,
               "selected":-1,"points":0,"correct":false,"role":"crew","spectator":false,"coins":1000},
             "reactions":[]}
        """.trimIndent()
        val snap = GameSnapshot.fromJson(JSONObject(json))
        assertEquals("impostor", snap.game)
        assertEquals("Aeropuerto", snap.impostor?.word)
        assertEquals("crew", snap.you?.role)
        assertFalse(snap.impostor?.isImpostor == true)
    }

    @Test
    fun drawingKeepsTheVectorCanvasAndTheFakeTitles() {
        val json = """
            {"code":"ABC234","displayCode":"ABC-234","mode":"remote","game":"dibujo",
             "phase":"vote","rev":9,"serverNow":1000,"phaseEndsAt":22000,"round":1,"rounds":3,
             "hostId":"p0","players":[{"id":"p0","name":"Ana","color":0,"score":0,"connected":true,"isHost":true}],
             "drawing":{"artistId":"p0","artistName":"Ana","prompt":"","strokes":[
                {"color":"#FFFFFF","width":6,"points":[[10,10],[200,200],[300,100]]}],
               "options":[{"id":"real","text":"Un gato astronauta","authorId":"","authorName":"","votes":0,
                           "isReal":false,"isYours":false},
                          {"id":"lie:p1","text":"Un perro en la luna","authorId":"","authorName":"","votes":0,
                           "isReal":false,"isYours":false}],
               "submittedCount":2,"titleCount":2,"votedCount":1,"voterCount":2,
               "youSubmitted":false,"youVoted":false,"yourVote":"","audienceVotes":0},
             "you":{"id":"p0","name":"Ana","score":0,"streak":0,"color":0,"answered":false,
               "selected":-1,"points":0,"correct":false,"artist":true,"spectator":false},
             "reactions":[]}
        """.trimIndent()
        val snap = GameSnapshot.fromJson(JSONObject(json))
        assertEquals(1, snap.drawing?.strokes?.size)
        assertEquals(3, snap.drawing?.strokes?.first()?.points?.size)
        assertEquals("Un gato astronauta", snap.drawing?.options?.first()?.text)
        assertTrue(snap.you?.artist == true)
        assertEquals(300f, snap.drawing?.strokes?.first()?.points?.last()?.x ?: 0f, 0.01f)
    }

    @Test
    fun auctionParsesBidsCoinsAndTheHammerResult() {
        val json = """
            {"code":"ABC234","displayCode":"ABC-234","mode":"remote","game":"subasta",
             "phase":"auction_reveal","rev":12,"serverNow":1000,"phaseEndsAt":9000,"round":2,"rounds":4,
             "hostId":"p0","players":[{"id":"p0","name":"Ana","color":0,"score":500,"connected":true,"isHost":true,"coins":800}],
             "auction":{"itemId":"sobre","title":"Sobre de puntos","description":"x","mystery":false,
               "effect":"points","value":600,"bidsCount":2,"bidCount":2,"yourBid":300,"youBid":true,
               "coins":800,"winnerId":"p0","winnerName":"Ana","price":300,"cancelled":["Beto"],
               "tie":true,"applied":"+600 puntos",
               "bids":[{"id":"p0","name":"Ana","bid":300}]},
             "you":{"id":"p0","name":"Ana","score":500,"streak":0,"color":0,"answered":true,
               "selected":-1,"points":600,"correct":true,"coins":800,"bid":300,"spectator":false},
             "reactions":[]}
        """.trimIndent()
        val snap = GameSnapshot.fromJson(JSONObject(json))
        assertEquals("Sobre de puntos", snap.auction?.title)
        assertTrue(snap.auction?.tie == true)
        assertEquals(300, snap.auction?.price)
        assertEquals(800, snap.you?.coins)
        assertEquals(600, snap.you?.points)
    }

    @Test
    fun fibbageHidesTheAnswerUntilRevealAndParsesTheMedals() {
        val json = """
            {"code":"ABC234","displayCode":"ABC-234","mode":"remote","game":"fibbage",
             "phase":"podium","rev":20,"serverNow":1000,"phaseEndsAt":0,"round":4,"rounds":4,
             "hostId":"p0","players":[{"id":"p0","name":"Ana","color":0,"score":2000,"connected":true,"isHost":true}],
             "podium":[{"id":"p0","name":"Ana","color":0,"score":2000,"connected":true,"isHost":true}],
             "highlights":[{"id":"liar","title":"El más mentiroso","name":"Ana",
                            "detail":"Sus mentiras mordieron 3 veces.","color":0}],
             "you":{"id":"p0","name":"Ana","score":2000,"streak":0,"color":0,"answered":false,
               "selected":-1,"points":0,"correct":false,"spectator":false},
             "reactions":[]}
        """.trimIndent()
        val snap = GameSnapshot.fromJson(JSONObject(json))
        assertEquals("fibbage", snap.game)
        assertTrue(snap.isPodium)
        assertEquals(1, snap.highlights.size)
        assertEquals("El más mentiroso", snap.highlights.first().title)
    }
}
