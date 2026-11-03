package com.chronosbvrx.visionplus

import com.chronosbvrx.visionplus.data.games.GameSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato de las reglas v2 (Trivia «El piso se quema» y Apuesta «Bóveda ciega»). */
class GameV2ModelsTest {

    @Test
    fun triviaV2ParsesAttackSortAssaultAndPunishment() {
        val json = JSONObject(
            """
            {
              "code":"ABC234","game":"trivia","rulesVersion":2,"phase":"sort","round":4,"rounds":8,
              "players":[{"id":"p1","name":"Ana","score":1000}],
              "trivia":{
                "roundKind":"sort",
                "attack":{"canAttack":true,"cost":200,"powers":["freeze","glitch","parasite"],
                  "targets":[{"id":"p2","name":"Beto"}],
                  "used":[{"by":"p1","byName":"Ana","power":"freeze","target":"p2","targetName":"Beto"}],
                  "usedCount":1,"eligibleCount":1},
                "you":{"canAttack":true,"attackPower":"freeze","attackTarget":"p2",
                  "frozen":false,"frozenTaps":0,"freezeTaps":5,"glitched":false,
                  "parasiteTarget":"","parasiteTargetName":"",
                  "sortSubmitted":false,"sortChoices":[0,1],"assaultSubmitted":false,
                  "assaultSelected":[],"punishmentWire":"","punishmentVote":"","isLoser":false},
                "sort":{"items":[{"id":"q1","text":"Peli","art":""}],"left":"Antes",
                  "right":"Después","submitted":false,"choices":[]},
                "assault":null,
                "punishment":null
              }
            }
            """.trimIndent(),
        )
        val snapshot = GameSnapshot.fromJson(json)
        assertEquals(2, snapshot.rulesVersion)
        assertEquals("sort", snapshot.trivia?.roundKind)
        assertTrue(snapshot.trivia?.attack?.canAttack == true)
        assertEquals("Beto", snapshot.trivia?.attack?.used?.first()?.targetName)
        assertEquals("Peli", snapshot.trivia?.sort?.items?.first()?.text)
        assertEquals(listOf(0, 1), snapshot.trivia?.you?.sortChoices)
    }

    @Test
    fun triviaV2ParsesPunishmentPenaltiesAndAssaultSelection() {
        val json = JSONObject(
            """
            {
              "code":"ABC234","game":"trivia","rulesVersion":2,"phase":"punishment_reveal",
              "round":3,"rounds":8,"players":[{"id":"p1","name":"Ana"}],
              "trivia":{
                "roundKind":"multiple_choice",
                "attack":{"canAttack":false,"cost":200,"powers":[],"targets":[],"used":[],"usedCount":0,"eligibleCount":0},
                "you":{"canAttack":false,"attackPower":"","attackTarget":"","frozen":false,
                  "frozenTaps":0,"freezeTaps":5,"glitched":false,"parasiteTarget":"",
                  "parasiteTargetName":"","sortSubmitted":false,"sortChoices":[],
                  "assaultSubmitted":true,"assaultSelected":[0,2],"punishmentWire":"red",
                  "punishmentVote":"","isLoser":true},
                "sort":null,
                "assault":{"items":[],"prompt":"Antes de 2015","submitted":true,"selected":[0,2]},
                "punishment":{"losers":[{"id":"p1","name":"Ana"}],"hotWire":"red",
                  "votes":{"red":2,"blue":1},"penalties":[["p1",500]],
                  "wires":["red","blue","yellow","green"]}
              }
            }
            """.trimIndent(),
        )
        val snapshot = GameSnapshot.fromJson(json)
        assertEquals("red", snapshot.trivia?.punishment?.hotWire)
        assertEquals(500, snapshot.trivia?.punishment?.penalties?.first()?.amount)
        assertEquals(listOf(0, 2), snapshot.trivia?.assault?.selected)
    }

    @Test
    fun wagerV2ParsesPotStageLocksAndSideBets() {
        val json = JSONObject(
            """
            {
              "code":"WAG234","game":"wager","rulesVersion":2,"phase":"clue","round":1,"rounds":6,
              "players":[{"id":"p1","name":"Ana"}],
              "wager":{
                "clueIndex":2,"clueCount":3,"factor":2,"stakeOptions":[],
                "title":"","poster":"https://p/x.jpg","year":2002,
                "stage":2,"pot":400,"ante":100,"buyCost":200,"posterReveal":0.5,
                "clue":"Una sinopsis","genres":["Drama"],"rollover":0,
                "lockedStage1":[{"id":"p1","name":"Ana"}],
                "sideBets":[{"id":"p2","name":"Beto","targetId":"p1","targetName":"Ana","amount":300}],
                "sideResults":[],
                "you":{"locked":false,"lockedStage":0,"lockedOption":-1,"canBuy":true,
                  "canSideBet":true,"sideBetTargetId":"","sideBetAmount":0,"factor":0,"correct":false}
              }
            }
            """.trimIndent(),
        )
        val snapshot = GameSnapshot.fromJson(json)
        assertEquals(2, snapshot.wager?.stage)
        assertEquals(400, snapshot.wager?.pot)
        assertEquals(0.5f, snapshot.wager?.posterReveal)
        assertEquals("Ana", snapshot.wager?.lockedStage1?.first()?.name)
        assertEquals("Ana", snapshot.wager?.sideBets?.first()?.targetName)
        assertTrue(snapshot.wager?.you?.canBuy == true)
        assertFalse(snapshot.wager?.you?.locked == true)
    }

    @Test
    fun thePhoneAndTvWireTheV2Screens() {
        val phone = File("src/main/java/com/chronosbvrx/visionplus/ui/games/GameRoomScreen.kt").readText()
        val tv = File("src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt").readText()
        val screens = File("src/main/java/com/chronosbvrx/visionplus/ui/games/GameV2Screens.kt").readText()
        assertTrue(phone.contains("TriviaV2Content"))
        assertTrue(phone.contains("WagerV2Content"))
        assertTrue(tv.contains("TvTriviaV2"))
        assertTrue(tv.contains("TvWagerV2"))
        assertTrue(screens.contains("trivia_attack"))
        assertTrue(screens.contains("wager_side_bet"))
        assertTrue(screens.contains("trivia_sort"))
        assertTrue(screens.contains("trivia_assault"))
    }

    @Test
    fun theTvSelectorHasNoEmptyCellAndReactsToFocus() {
        val tv = File("src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt").readText()
        // Selector horizontal con todos los juegos y la celda de Ruleta.
        assertTrue(tv.contains("TvFullBleedGameCard"))
        assertTrue(tv.contains("TvRandomPartyCard"))
        assertTrue(tv.contains("LazyRow"))
        assertTrue(tv.contains("itemsIndexed(cards)"))
        assertTrue(tv.contains("\"Ruleta Vision+\""))
        assertTrue(tv.contains("onRandom = { info -> newRoom(info, \"general\") }"))
        assertFalse(tv.contains("repeat(4 - rowGames.size)"))
        // El foco pinta el plató y cambia la presentadora, no solo el borde de la tarjeta.
        assertTrue(tv.contains("onFocusAccent = { selectorAccent = it }"))
        assertTrue(tv.contains("SelectorHostLine"))
        assertTrue(tv.contains("JUGAR AHORA >"))
    }

    @Test
    fun theTvLobbySharesTheImmersiveLanguageAndKeepsTheQrScannable() {
        val tv = File("src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt").readText()
        // El lobby deja de ser dos columnas planas: arte a sangre, QR premium y sala de espera.
        assertTrue(tv.contains("LobbyJoinCard"))
        assertTrue(tv.contains("LobbyPlayersCard"))
        assertTrue(tv.contains("TvLobbyPlayerSlot"))
        assertTrue(tv.contains("LobbyMetric"))
        assertTrue(tv.contains("LobbyActionButton"))
        assertTrue(tv.contains("player?.name ?: \"Libre\""))
        assertTrue(tv.contains("GameQr(code = code, size = 190.dp)"))
        assertTrue(tv.contains("Escanea el QR con Vision+ en tu teléfono"))
        assertTrue(tv.contains("El primero que entre será el anfitrión"))
        assertTrue(tv.contains("La partida empieza desde el teléfono del anfitrión"))
    }
}
