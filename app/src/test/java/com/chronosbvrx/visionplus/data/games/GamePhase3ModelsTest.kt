package com.chronosbvrx.visionplus.data.games

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de Fase 3: Charadas y Apuesta a la pista se parsean sin perder su estado. */
class GamePhase3ModelsTest {

    @Test
    fun charadaTurnKeepsThePrivateTitleForTheActor() {
        val snapshot = GameSnapshot.fromJson(
            JSONObject(
                """
                {"code":"ABC234","displayCode":"ABC-234","mode":"remote","game":"charadas",
                 "phase":"turn","rev":9,"serverNow":1000,"phaseEndsAt":61000,
                 "round":1,"rounds":6,"hostId":"p1",
                 "players":[{"id":"p1","name":"Ana","color":0,"score":0,"connected":true,"isHost":true}],
                 "question":null,"answer":null,
                 "charada":{"actorId":"p1","actorName":"Ana","index":2,"passes":1,"maxPasses":3,
                            "correct":1,"points":1000,"title":"Mi película","poster":"https://p/x.jpg","year":2024},
                 "you":{"id":"p1","name":"Ana","score":0,"streak":0,"color":0,"answered":false,
                        "selected":-1,"points":0,"correct":false,"actor":true},
                 "reactions":[]}
                """.trimIndent(),
            ),
        )
        assertEquals("charadas", snapshot.game)
        assertEquals("turn", snapshot.phase)
        assertEquals("Mi película", snapshot.charada?.title)
        assertTrue(snapshot.you?.actor == true)
        assertEquals(3, snapshot.charada?.maxPasses)
        assertEquals("Ana", snapshot.charada?.actorName)
    }

    @Test
    fun wagerClueHidesTheAnswerAndKeepsTheMultiplier() {
        val snapshot = GameSnapshot.fromJson(
            JSONObject(
                """
                {"code":"XYZ234","displayCode":"XYZ-234","mode":"display","game":"wager",
                 "phase":"clue","rev":11,"serverNow":1000,"phaseEndsAt":16000,
                 "round":2,"rounds":6,"hostId":"p1","playersPace":true,
                 "players":[{"id":"p1","name":"Ana","color":0,"score":500,"connected":true,"isHost":true,"wager":500,"wagerDone":false}],
                 "question":{"id":"w2","kind":"wager","prompt":"Una sinopsis","options":["A","B","C","D"],"art":"","difficulty":2},
                 "answer":null,
                 "wager":{"clueIndex":2,"clueCount":3,"factor":2,"stakeOptions":[200,500,1000],"title":"","poster":"","year":0},
                 "you":{"id":"p1","name":"Ana","score":500,"streak":0,"color":0,"answered":false,
                        "selected":-1,"points":0,"correct":false,"wager":500},
                 "reactions":[]}
                """.trimIndent(),
            ),
        )
        assertEquals("wager", snapshot.game)
        assertEquals(2, snapshot.wager?.clueIndex)
        assertEquals(2, snapshot.wager?.factor)
        assertEquals(500, snapshot.you?.wager)
        assertEquals(4, snapshot.question?.options?.size)
        assertEquals("Una sinopsis", snapshot.question?.prompt)
        assertFalse(snapshot.you?.wagerDone == true)
    }

    @Test
    fun wagerRevealCarriesTheAnswerAndTheDelta() {
        val snapshot = GameSnapshot.fromJson(
            JSONObject(
                """
                {"code":"XYZ234","displayCode":"XYZ-234","mode":"remote","game":"wager",
                 "phase":"reveal","rev":12,"serverNow":1000,"phaseEndsAt":5000,
                 "round":2,"rounds":6,"hostId":"p1",
                 "players":[{"id":"p1","name":"Ana","color":0,"score":1500,"connected":true,"isHost":true}],
                 "question":{"id":"w2","kind":"wager","prompt":"¿Cuál era el título?","options":["A","B"],"art":"","difficulty":2},
                 "answer":{"option":0,"text":"A"},
                 "wager":{"clueIndex":3,"clueCount":3,"factor":1,"stakeOptions":[200,500,1000],"title":"A","poster":"https://p/a.jpg","year":2024},
                 "you":{"id":"p1","name":"Ana","score":1500,"streak":0,"color":0,"answered":true,
                        "selected":0,"points":1000,"correct":true,"wager":500,"wagerDone":true,
                        "clueAnswered":true,"wagerDelta":1000,"wagerCorrect":true},
                 "reactions":[]}
                """.trimIndent(),
            ),
        )
        assertEquals("A", snapshot.answer?.text)
        assertEquals("A", snapshot.wager?.title)
        assertEquals(1000, snapshot.you?.wagerDelta)
        assertTrue(snapshot.you?.wagerCorrect == true)
    }
}
