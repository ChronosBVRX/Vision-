package com.chronosbvrx.visionplus.data.games

import org.json.JSONArray
import org.json.JSONObject

/** DTOs del contrato de `/v1/game/...`. La UI solo pinta; el servidor es la autoridad. */

data class GamePlayer(
    val id: String,
    val name: String,
    val color: Int,
    val score: Int,
    val connected: Boolean,
    val isHost: Boolean,
    val ready: Boolean = false,
    val wager: Int = 0,
    val wagerDone: Boolean = false,
    val coins: Int = 0,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GamePlayer(
            id = obj.optString("id"),
            name = obj.optString("name"),
            color = obj.optInt("color", 0),
            score = obj.optInt("score", 0),
            connected = obj.optBoolean("connected", true),
            isHost = obj.optBoolean("isHost", false),
            ready = obj.optBoolean("ready", false),
            wager = obj.optInt("wager", 0),
            wagerDone = obj.optBoolean("wagerDone", false),
            coins = obj.optInt("coins", 0),
        )
    }
}

/** Una sección de trivia que publica `/v1/game/packs`. */
data class GamePack(
    val id: String,
    val title: String,
    val active: Boolean,
    val questions: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GamePack(
            id = obj.optString("id"),
            title = obj.optString("title"),
            active = obj.optBoolean("active", true),
            questions = obj.optInt("questions", 0),
        )
    }
}

data class GameQuestion(
    val id: String,
    val kind: String,
    val prompt: String,
    val options: List<String>,
    val art: String,
    val difficulty: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameQuestion(
            id = obj.optString("id"),
            kind = obj.optString("kind"),
            prompt = obj.optString("prompt"),
            options = obj.optJSONArray("options").strings(),
            art = obj.optString("art"),
            difficulty = obj.optInt("difficulty", 1),
        )
    }
}

data class GameAnswer(val option: Int, val text: String) {
    companion object {
        fun fromJson(obj: JSONObject) = GameAnswer(
            option = obj.optInt("option", -1),
            text = obj.optString("text"),
        )
    }
}

data class GameYou(
    val id: String,
    val name: String,
    val score: Int,
    val streak: Int,
    val color: Int,
    val answered: Boolean,
    val selected: Int,
    val points: Int,
    val correct: Boolean,
    val ready: Boolean = false,
    val actor: Boolean = false,
    val wager: Int = 0,
    val wagerDone: Boolean = false,
    val clueAnswered: Boolean = false,
    val wagerDelta: Int = 0,
    val wagerCorrect: Boolean = false,
    val spectator: Boolean = false,
    val coins: Int = 0,
    val role: String = "",
    val submitted: Boolean = false,
    val voted: Boolean = false,
    val bid: Int = -1,
    val artist: Boolean = false,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameYou(
            id = obj.optString("id"),
            name = obj.optString("name"),
            score = obj.optInt("score", 0),
            streak = obj.optInt("streak", 0),
            color = obj.optInt("color", 0),
            answered = obj.optBoolean("answered", false),
            selected = obj.optInt("selected", -1),
            points = obj.optInt("points", 0),
            correct = obj.optBoolean("correct", false),
            ready = obj.optBoolean("ready", false),
            actor = obj.optBoolean("actor", false),
            wager = obj.optInt("wager", 0),
            wagerDone = obj.optBoolean("wagerDone", false),
            clueAnswered = obj.optBoolean("clueAnswered", false),
            wagerDelta = obj.optInt("wagerDelta", 0),
            wagerCorrect = obj.optBoolean("wagerCorrect", false),
            spectator = obj.optBoolean("spectator", false),
            coins = obj.optInt("coins", 0),
            role = obj.optString("role"),
            submitted = obj.optBoolean("submitted", false),
            voted = obj.optBoolean("voted", false),
            bid = obj.optInt("bid", -1),
            artist = obj.optBoolean("artist", false),
        )
    }
}

data class GameReaction(val playerId: String, val kind: Int) {
    companion object {
        fun fromJson(obj: JSONObject) = GameReaction(
            playerId = obj.optString("playerId"),
            kind = obj.optInt("kind", 0),
        )
    }
}

/** Medalla automática del cierre («El más mentiroso», «El detective»…). */
data class GameHighlight(
    val id: String,
    val title: String,
    val name: String,
    val detail: String,
    val color: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameHighlight(
            id = obj.optString("id"),
            title = obj.optString("title"),
            name = obj.optString("name"),
            detail = obj.optString("detail"),
            color = obj.optInt("color", 0),
        )
    }
}

/** Turno de Charadas. `title`/`poster` solo llegan al teléfono del actor. */
data class GameCharada(
    val actorId: String,
    val actorName: String,
    val index: Int,
    val passes: Int,
    val maxPasses: Int,
    val correct: Int,
    val points: Int,
    val title: String,
    val poster: String,
    val year: Int,
    val role: String = "crew",
    val directorId: String = "",
    val directorName: String = "",
    val directorOptions: List<String> = emptyList(),
    val directorChosen: Boolean = false,
    val restriction: String = "",
    val saboteurId: String = "",
    val saboteurName: String = "",
    val buzzerId: String = "",
    val buzzerName: String = "",
    val buzzerActive: Boolean = false,
    val buzzerSeconds: Int = 0,
    val helpGenre: Boolean = false,
    val helpYear: Boolean = false,
    val genre: String = "",
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameCharada(
            actorId = obj.optString("actorId"),
            actorName = obj.optString("actorName"),
            index = obj.optInt("index", 0),
            passes = obj.optInt("passes", 0),
            maxPasses = obj.optInt("maxPasses", 3),
            correct = obj.optInt("correct", 0),
            points = obj.optInt("points", 1000),
            title = obj.optString("title"),
            poster = obj.optString("poster"),
            year = obj.optInt("year", 0),
            role = obj.optString("role", "crew"),
            directorId = obj.optString("directorId"),
            directorName = obj.optString("directorName"),
            directorOptions = obj.optJSONArray("directorOptions").strings(),
            directorChosen = obj.optBoolean("directorChosen", false),
            restriction = obj.optString("restriction"),
            saboteurId = obj.optString("saboteurId"),
            saboteurName = obj.optString("saboteurName"),
            buzzerId = obj.optString("buzzerId"),
            buzzerName = obj.optString("buzzerName"),
            buzzerActive = obj.optBoolean("buzzerActive", false),
            buzzerSeconds = obj.optInt("buzzerSeconds", 0),
            helpGenre = obj.optBoolean("helpGenre", false),
            helpYear = obj.optBoolean("helpYear", false),
            genre = obj.optString("genre"),
        )
    }
}

/** Estado de Apuesta a la pista. El título solo se revela al final. */
data class GameWagerLocked(val id: String, val name: String) {
    companion object {
        fun fromJson(obj: JSONObject) = GameWagerLocked(
            id = obj.optString("id"),
            name = obj.optString("name"),
        )
    }
}

data class GameWagerSideBet(
    val id: String,
    val name: String,
    val targetId: String,
    val targetName: String,
    val amount: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameWagerSideBet(
            id = obj.optString("id"),
            name = obj.optString("name"),
            targetId = obj.optString("targetId"),
            targetName = obj.optString("targetName"),
            amount = obj.optInt("amount", 0),
        )
    }
}

data class GameWagerSideResult(val id: String, val name: String, val delta: Int) {
    companion object {
        fun fromJson(obj: JSONObject) = GameWagerSideResult(
            id = obj.optString("id"),
            name = obj.optString("name"),
            delta = obj.optInt("delta", 0),
        )
    }
}

data class GameWagerYou(
    val locked: Boolean,
    val lockedStage: Int,
    val lockedOption: Int,
    val canBuy: Boolean,
    val canSideBet: Boolean,
    val sideBetTargetId: String,
    val sideBetAmount: Int,
    val factor: Int,
    val privateStage: Int = 1,
    val privateClue: String = "",
    val privatePosterReveal: Float = 0.06f,
    val correct: Boolean,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameWagerYou(
            locked = obj.optBoolean("locked", false),
            lockedStage = obj.optInt("lockedStage", 0),
            lockedOption = obj.optInt("lockedOption", -1),
            canBuy = obj.optBoolean("canBuy", false),
            canSideBet = obj.optBoolean("canSideBet", false),
            sideBetTargetId = obj.optString("sideBetTargetId"),
            sideBetAmount = obj.optInt("sideBetAmount", 0),
            factor = obj.optInt("factor", 0),
            privateStage = obj.optInt("privateStage", 1),
            privateClue = obj.optString("privateClue"),
            privatePosterReveal = obj.optDouble("privatePosterReveal", 0.06).toFloat(),
            correct = obj.optBoolean("correct", false),
        )
    }
}

data class GameWager(
    val clueIndex: Int,
    val clueCount: Int,
    val factor: Int,
    val stakeOptions: List<Int>,
    val title: String,
    val poster: String,
    val year: Int,
    val stage: Int = 1,
    val pot: Int = 0,
    val ante: Int = 0,
    val buyCost: Int = 0,
    val posterReveal: Float = 1f,
    val clue: String = "",
    val genres: List<String> = emptyList(),
    val rollover: Int = 0,
    val lockedStage1: List<GameWagerLocked> = emptyList(),
    val sideBets: List<GameWagerSideBet> = emptyList(),
    val sideResults: List<GameWagerSideResult> = emptyList(),
    val you: GameWagerYou? = null,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameWager(
            clueIndex = obj.optInt("clueIndex", 0),
            clueCount = obj.optInt("clueCount", 3),
            factor = obj.optInt("factor", 1),
            stakeOptions = obj.optJSONArray("stakeOptions").ints().ifEmpty { listOf(200, 500, 1000) },
            title = obj.optString("title"),
            poster = obj.optString("poster"),
            year = obj.optInt("year", 0),
            stage = obj.optInt("stage", 1),
            pot = obj.optInt("pot", 0),
            ante = obj.optInt("ante", 0),
            buyCost = obj.optInt("buyCost", 0),
            posterReveal = obj.optDouble("posterReveal", 1.0).toFloat().coerceIn(0f, 1f),
            clue = obj.optString("clue"),
            genres = obj.optJSONArray("genres").strings(),
            rollover = obj.optInt("rollover", 0),
            lockedStage1 = obj.optJSONArray("lockedStage1").objects(GameWagerLocked::fromJson),
            sideBets = obj.optJSONArray("sideBets").objects(GameWagerSideBet::fromJson),
            sideResults = obj.optJSONArray("sideResults").objects(GameWagerSideResult::fromJson),
            you = obj.optJSONObject("you")?.let(GameWagerYou::fromJson),
        )
    }
}

data class GameTriviaTarget(val id: String, val name: String) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaTarget(
            id = obj.optString("id"),
            name = obj.optString("name"),
        )
    }
}

data class GameTriviaAttackUsed(
    val by: String,
    val byName: String,
    val power: String,
    val target: String,
    val targetName: String,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaAttackUsed(
            by = obj.optString("by"),
            byName = obj.optString("byName"),
            power = obj.optString("power"),
            target = obj.optString("target"),
            targetName = obj.optString("targetName"),
        )
    }
}

data class GameTriviaAttack(
    val canAttack: Boolean,
    val cost: Int,
    val powers: List<String>,
    val targets: List<GameTriviaTarget>,
    val used: List<GameTriviaAttackUsed>,
    val usedCount: Int,
    val eligibleCount: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaAttack(
            canAttack = obj.optBoolean("canAttack", false),
            cost = obj.optInt("cost", 200),
            powers = obj.optJSONArray("powers").strings(),
            targets = obj.optJSONArray("targets").objects(GameTriviaTarget::fromJson),
            used = obj.optJSONArray("used").objects(GameTriviaAttackUsed::fromJson),
            usedCount = obj.optInt("usedCount", 0),
            eligibleCount = obj.optInt("eligibleCount", 0),
        )
    }
}

data class GameTriviaYou(
    val canAttack: Boolean,
    val attackPower: String,
    val attackTarget: String,
    val frozen: Boolean,
    val frozenTaps: Int,
    val freezeTaps: Int,
    val glitched: Boolean,
    val parasiteTarget: String,
    val parasiteTargetName: String,
    val sortSubmitted: Boolean,
    val sortChoices: List<Int>,
    val assaultSubmitted: Boolean,
    val assaultSelected: List<Int>,
    val punishmentWire: String,
    val punishmentVote: String,
    val isLoser: Boolean,
    val answered: Boolean = false,
    val correct: Boolean = false,
    val points: Int = 0,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaYou(
            canAttack = obj.optBoolean("canAttack", false),
            attackPower = obj.optString("attackPower"),
            attackTarget = obj.optString("attackTarget"),
            frozen = obj.optBoolean("frozen", false),
            frozenTaps = obj.optInt("frozenTaps", 0),
            freezeTaps = obj.optInt("freezeTaps", 5),
            glitched = obj.optBoolean("glitched", false),
            parasiteTarget = obj.optString("parasiteTarget"),
            parasiteTargetName = obj.optString("parasiteTargetName"),
            sortSubmitted = obj.optBoolean("sortSubmitted", false),
            sortChoices = obj.optJSONArray("sortChoices").intsRaw(),
            assaultSubmitted = obj.optBoolean("assaultSubmitted", false),
            assaultSelected = obj.optJSONArray("assaultSelected").intsRaw(),
            punishmentWire = obj.optString("punishmentWire"),
            punishmentVote = obj.optString("punishmentVote"),
            isLoser = obj.optBoolean("isLoser", false),
            answered = obj.optBoolean("answered", false),
            correct = obj.optBoolean("correct", false),
            points = obj.optInt("points", 0),
        )
    }
}

data class GameTriviaItem(val id: String, val text: String, val art: String) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaItem(
            id = obj.optString("id"),
            text = obj.optString("text"),
            art = obj.optString("art"),
        )
    }
}

data class GameTriviaSort(
    val items: List<GameTriviaItem>,
    val left: String,
    val right: String,
    val submitted: Boolean,
    val choices: List<Int>,
    val answers: List<Int> = emptyList(),
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaSort(
            items = obj.optJSONArray("items").objects(GameTriviaItem::fromJson),
            left = obj.optString("left", "Antes"),
            right = obj.optString("right", "Después"),
            submitted = obj.optBoolean("submitted", false),
            choices = obj.optJSONArray("choices").intsRaw(),
            answers = obj.optJSONArray("answers").intsRaw(),
        )
    }
}

data class GameTriviaAssault(
    val items: List<GameTriviaItem>,
    val prompt: String,
    val submitted: Boolean,
    val selected: List<Int>,
    val answers: List<Int> = emptyList(),
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaAssault(
            items = obj.optJSONArray("items").objects(GameTriviaItem::fromJson),
            prompt = obj.optString("prompt"),
            submitted = obj.optBoolean("submitted", false),
            selected = obj.optJSONArray("selected").intsRaw(),
            answers = obj.optJSONArray("answers").intsRaw(),
        )
    }
}

data class GameTriviaPenalty(val id: String, val amount: Int) {
    companion object {
        fun fromJson(obj: JSONArray): GameTriviaPenalty {
            val id = obj.optString(0)
            val amount = obj.optInt(1, 0)
            return GameTriviaPenalty(id, amount)
        }
    }
}

data class GameTriviaPunishment(
    val losers: List<GameTriviaTarget>,
    val hotWire: String,
    val votes: Map<String, Int>,
    val penalties: List<GameTriviaPenalty>,
    val wires: List<String>,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTriviaPunishment(
            losers = obj.optJSONArray("losers").objects(GameTriviaTarget::fromJson),
            hotWire = obj.optString("hotWire"),
            votes = obj.optJSONObject("votes").intMap(),
            penalties = obj.optJSONArray("penalties")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONArray(index)?.let(GameTriviaPenalty::fromJson)
                }
            }.orEmpty(),
            wires = obj.optJSONArray("wires").strings().ifEmpty {
                listOf("red", "blue", "yellow", "green")
            },
        )
    }
}

data class GameTrivia(
    val roundKind: String,
    val attack: GameTriviaAttack,
    val you: GameTriviaYou,
    val sort: GameTriviaSort?,
    val assault: GameTriviaAssault?,
    val punishment: GameTriviaPunishment?,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameTrivia(
            roundKind = obj.optString("roundKind", "multiple_choice"),
            attack = obj.optJSONObject("attack")?.let(GameTriviaAttack::fromJson) ?: GameTriviaAttack(
                false, 200, emptyList(), emptyList(), emptyList(), 0, 0,
            ),
            you = obj.optJSONObject("you")?.let(GameTriviaYou::fromJson) ?: GameTriviaYou(
                false, "", "", false, 0, 5, false, "", "", false,
                emptyList(), false, emptyList(), "", "", false,
            ),
            sort = obj.optJSONObject("sort")?.let(GameTriviaSort::fromJson),
            assault = obj.optJSONObject("assault")?.let(GameTriviaAssault::fromJson),
            punishment = obj.optJSONObject("punishment")?.let(GameTriviaPunishment::fromJson),
        )
    }
}

data class GameCoartada(val id: String, val name: String, val value: Int) {
    companion object {
        fun fromJson(obj: JSONObject) = GameCoartada(
            id = obj.optString("id"),
            name = obj.optString("name"),
            value = obj.optInt("value", -1),
        )
    }
}

/** El Impostor: palabra secreta, rol individual y turnos de preguntas. */
data class GameImpostor(
    val category: String,
    val hint: String,
    val word: String,
    val role: String,
    val speakerId: String,
    val speakerName: String,
    val askerId: String,
    val askerName: String,
    val targetId: String,
    val targetName: String,
    val speakerIndex: Int,
    val speakerCount: Int,
    val question: String,
    val voted: Boolean,
    val votedCount: Int,
    val voterCount: Int,
    val votes: Map<String, Int>,
    val impostorId: String,
    val impostorName: String,
    val accompliceId: String = "",
    val accompliceName: String = "",
    val detectiveId: String = "",
    val detectiveName: String = "",
    val detectiveOptions: List<String> = emptyList(),
    val coartadaSubmitted: Boolean = false,
    val coartadaCount: Int = 0,
    val coartadaValue: Int = -1,
    val coartadaValues: List<GameCoartada> = emptyList(),
    val caught: Boolean,
    val guess: String,
    val guessOptions: List<String>,
    val guessCorrect: Boolean,
    val points: Int,
) {
    val isImpostor: Boolean get() = role == "impostor"

    companion object {
        fun fromJson(obj: JSONObject) = GameImpostor(
            category = obj.optString("category"),
            hint = obj.optString("hint"),
            word = obj.optString("word"),
            role = obj.optString("role"),
            speakerId = obj.optString("speakerId"),
            speakerName = obj.optString("speakerName"),
            askerId = obj.optString("askerId").ifBlank { obj.optString("speakerId") },
            askerName = obj.optString("askerName").ifBlank { obj.optString("speakerName") },
            targetId = obj.optString("targetId"),
            targetName = obj.optString("targetName"),
            speakerIndex = obj.optInt("speakerIndex", 0),
            speakerCount = obj.optInt("speakerCount", 0),
            question = obj.optString("question"),
            voted = obj.optBoolean("voted", false),
            votedCount = obj.optInt("votedCount", 0),
            voterCount = obj.optInt("voterCount", 0),
            votes = obj.optJSONObject("votes").intMap(),
            impostorId = obj.optString("impostorId"),
            impostorName = obj.optString("impostorName"),
            accompliceId = obj.optString("accompliceId"),
            accompliceName = obj.optString("accompliceName"),
            detectiveId = obj.optString("detectiveId"),
            detectiveName = obj.optString("detectiveName"),
            detectiveOptions = obj.optJSONArray("detectiveOptions").strings(),
            coartadaSubmitted = obj.optBoolean("coartadaSubmitted", false),
            coartadaCount = obj.optInt("coartadaCount", 0),
            coartadaValue = obj.optInt("coartadaValue", -1),
            coartadaValues = obj.optJSONArray("coartadaValues").objects(GameCoartada::fromJson),
            caught = obj.optBoolean("caught", false),
            guess = obj.optString("guess"),
            guessOptions = obj.optJSONArray("guessOptions").strings(),
            guessCorrect = obj.optBoolean("guessCorrect", false),
            points = obj.optInt("points", 0),
        )
    }
}

data class GamePoint(val x: Float, val y: Float)

data class GameStroke(
    val color: String,
    val width: Float,
    val points: List<GamePoint>,
) {
    companion object {
        fun fromJson(obj: JSONObject): GameStroke? {
            val points = obj.optJSONArray("points")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val pair = array.optJSONArray(index) ?: return@mapNotNull null
                    GamePoint(pair.optDouble(0, 0.0).toFloat(), pair.optDouble(1, 0.0).toFloat())
                }
            }.orEmpty()
            if (points.size < 2) return null
            return GameStroke(
                color = obj.optString("color", "#FFFFFF"),
                width = obj.optDouble("width", 5.0).toFloat(),
                points = points,
            )
        }
    }
}

/** Una opción de voto (título falso, título real o mentira de Fibbage). */
data class GameChoice(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val votes: Int,
    val isReal: Boolean,
    val isYours: Boolean,
    val house: Boolean = false,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameChoice(
            id = obj.optString("id"),
            text = obj.optString("text"),
            authorId = obj.optString("authorId"),
            authorName = obj.optString("authorName"),
            votes = obj.optInt("votes", 0),
            isReal = obj.optBoolean("isReal", false),
            isYours = obj.optBoolean("isYours", false),
            house = obj.optBoolean("house", false),
        )
    }
}

/** Duelo de Dibujo Ciego. */
data class GameDrawing(
    val artistId: String,
    val artistName: String,
    val prompt: String,
    val promptOptions: List<String>,
    val strokes: List<GameStroke>,
    val options: List<GameChoice>,
    val submittedCount: Int,
    val titleCount: Int,
    val votedCount: Int,
    val voterCount: Int,
    val youSubmitted: Boolean,
    val youVoted: Boolean,
    val yourVote: String,
    val audienceVotes: Int,
    val audienceChoice: String,
    val audienceBonusPlayerId: String,
    val inkUsed: Float = 0f,
    val inkLimit: Float = 0f,
    val flashlight: Boolean = false,
    val earlyGuessSubmitted: Boolean = false,
    val earlyGuess: String = "",
    val earlyAttempts: Int = 0,
    val earlyWinners: List<String> = emptyList(),
    val criticBomb: String = "",
    val criticCrown: String = "",
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameDrawing(
            artistId = obj.optString("artistId"),
            artistName = obj.optString("artistName"),
            prompt = obj.optString("prompt"),
            promptOptions = obj.optJSONArray("promptOptions").strings(),
            strokes = obj.optJSONArray("strokes").strokes(),
            options = obj.optJSONArray("options").objects(GameChoice::fromJson),
            submittedCount = obj.optInt("submittedCount", 0),
            titleCount = obj.optInt("titleCount", 0),
            votedCount = obj.optInt("votedCount", 0),
            voterCount = obj.optInt("voterCount", 0),
            youSubmitted = obj.optBoolean("youSubmitted", false),
            youVoted = obj.optBoolean("youVoted", false),
            yourVote = obj.optString("yourVote"),
            audienceVotes = obj.optInt("audienceVotes", 0),
            audienceChoice = obj.optString("audienceChoice"),
            audienceBonusPlayerId = obj.optString("audienceBonusPlayerId"),
            inkUsed = obj.optDouble("inkUsed", 0.0).toFloat(),
            inkLimit = obj.optDouble("inkLimit", 0.0).toFloat(),
            flashlight = obj.optBoolean("flashlight", false),
            earlyGuessSubmitted = obj.optBoolean("earlyGuessSubmitted", false),
            earlyGuess = obj.optString("earlyGuess"),
            earlyAttempts = obj.optInt("earlyAttempts", 0),
            earlyWinners = obj.optJSONArray("earlyWinners").strings(),
            criticBomb = obj.optString("criticBomb"),
            criticCrown = obj.optString("criticCrown"),
        )
    }
}

data class GameAuctionBid(val id: String, val name: String, val bid: Int) {
    companion object {
        fun fromJson(obj: JSONObject) = GameAuctionBid(
            id = obj.optString("id"),
            name = obj.optString("name"),
            bid = obj.optInt("bid", 0),
        )
    }
}

/** Subasta Ciega. */
data class GameAuction(
    val itemId: String,
    val title: String,
    val description: String,
    val mystery: Boolean,
    val effect: String,
    val value: Int,
    val bidsCount: Int,
    val bidCount: Int,
    val yourBid: Int,
    val youBid: Boolean,
    val coins: Int,
    val winnerId: String,
    val winnerName: String,
    val price: Int,
    val cancelled: List<String>,
    val tie: Boolean,
    val applied: String,
    val bids: List<GameAuctionBid>,
    val secretEffect: String = "",
    val secretValue: Int = 0,
    val informant: Boolean = false,
    val ghostBidderId: String = "",
    val ghostBidderName: String = "",
    val ghostUsed: Boolean = false,
    val secondPays: Boolean = false,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameAuction(
            itemId = obj.optString("itemId"),
            title = obj.optString("title"),
            description = obj.optString("description"),
            mystery = obj.optBoolean("mystery", false),
            effect = obj.optString("effect"),
            value = obj.optInt("value", 0),
            bidsCount = obj.optInt("bidsCount", 0),
            bidCount = obj.optInt("bidCount", 0),
            yourBid = obj.optInt("yourBid", -1),
            youBid = obj.optBoolean("youBid", false),
            coins = obj.optInt("coins", 0),
            winnerId = obj.optString("winnerId"),
            winnerName = obj.optString("winnerName"),
            price = obj.optInt("price", 0),
            cancelled = obj.optJSONArray("cancelled").strings(),
            tie = obj.optBoolean("tie", false),
            applied = obj.optString("applied"),
            bids = obj.optJSONArray("bids").objects(GameAuctionBid::fromJson),
            secretEffect = obj.optString("secretEffect"),
            secretValue = obj.optInt("secretValue", 0),
            informant = obj.optBoolean("informant", false),
            ghostBidderId = obj.optString("ghostBidderId"),
            ghostBidderName = obj.optString("ghostBidderName"),
            ghostUsed = obj.optBoolean("ghostUsed", false),
            secondPays = obj.optBoolean("secondPays", false),
        )
    }
}

/** Fibbage / Falso Conocimiento. */
data class GameFibbage(
    val prompt: String,
    val hint: String,
    val category: String,
    val answer: String,
    val options: List<GameChoice>,
    val submittedCount: Int,
    val voterCount: Int,
    val votedCount: Int,
    val youSubmitted: Boolean,
    val youVoted: Boolean,
    val yourVote: String,
    val yourLie: String,
    val audienceVotes: Int,
    val audienceChoice: String,
    val audienceBonusPlayerId: String,
    val scanUsed: Boolean = false,
    val scanned: List<String> = emptyList(),
    val certain: Boolean = false,
    val poisoned: Boolean = false,
    val finalShared: Boolean = false,
    val hunterGuess: String = "",
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameFibbage(
            prompt = obj.optString("prompt"),
            hint = obj.optString("hint"),
            category = obj.optString("category"),
            answer = obj.optString("answer"),
            options = obj.optJSONArray("options").objects(GameChoice::fromJson),
            submittedCount = obj.optInt("submittedCount", 0),
            voterCount = obj.optInt("voterCount", 0),
            votedCount = obj.optInt("votedCount", 0),
            youSubmitted = obj.optBoolean("youSubmitted", false),
            youVoted = obj.optBoolean("youVoted", false),
            yourVote = obj.optString("yourVote"),
            yourLie = obj.optString("yourLie"),
            audienceVotes = obj.optInt("audienceVotes", 0),
            audienceChoice = obj.optString("audienceChoice"),
            audienceBonusPlayerId = obj.optString("audienceBonusPlayerId"),
            scanUsed = obj.optBoolean("scanUsed", false),
            scanned = obj.optJSONArray("scanned").strings(),
            certain = obj.optBoolean("certain", false),
            poisoned = obj.optBoolean("poisoned", false),
            finalShared = obj.optBoolean("finalShared", false),
            hunterGuess = obj.optString("hunterGuess"),
        )
    }
}

data class GameSnapshot(
    val code: String,
    val displayCode: String,
    val mode: String,
    val game: String,
    val rulesVersion: Int = 1,
    val pack: String,
    val phase: String,
    val rev: Long,
    val serverNow: Long,
    val phaseEndsAt: Long,
    val phaseDuration: Int = 0,
    val round: Int,
    val rounds: Int,
    val hostId: String,
    val players: List<GamePlayer>,
    val playersPace: Boolean = false,
    val answeredCount: Int = 0,
    val readyCount: Int = 0,
    val allReady: Boolean = true,
    val question: GameQuestion?,
    val answer: GameAnswer?,
    val trivia: GameTrivia?,
    val charada: GameCharada?,
    val wager: GameWager?,
    val impostor: GameImpostor?,
    val drawing: GameDrawing?,
    val auction: GameAuction?,
    val fibbage: GameFibbage?,
    val fiesta: FiestaState? = null,
    val audience: Int = 0,
    val highlights: List<GameHighlight> = emptyList(),
    val you: GameYou?,
    val reactions: List<GameReaction>,
) {
    val isPodium: Boolean get() = phase == "podium"
    val isClosed: Boolean get() = phase == "closed"
    val isFiesta: Boolean get() = FiestaCatalog.isFiesta(game)

    /** Clave del momento para la presentadora: mismo momento, misma variante. */
    fun narrKey(): String = "${game}:${phase}:${round}:${wager?.clueIndex ?: 0}"
    val info: GameInfo get() = GameCatalog.byId(game)

    fun isHost(playerId: String?): Boolean = !playerId.isNullOrBlank() && playerId == hostId

    companion object {
        fun fromJson(obj: JSONObject): GameSnapshot {
            val code = obj.optString("code")
            return GameSnapshot(
                code = code,
                displayCode = obj.optString("displayCode").ifBlank { GameCodes.format(code) },
                mode = obj.optString("mode"),
                game = obj.optString("game", "trivia"),
                rulesVersion = obj.optInt("rulesVersion", 1),
                pack = obj.optString("pack"),
                phase = obj.optString("phase", "lobby"),
                rev = obj.optLong("rev", 0L),
                serverNow = obj.optLong("serverNow", 0L),
                phaseEndsAt = obj.optLong("phaseEndsAt", 0L),
                phaseDuration = obj.optInt("phaseDuration", 0),
                round = obj.optInt("round", 1),
                rounds = obj.optInt("rounds", 0),
                hostId = obj.optString("hostId"),
                players = obj.optJSONArray("players").objects(GamePlayer::fromJson),
                playersPace = obj.optBoolean("playersPace", false),
                answeredCount = obj.optInt("answeredCount", 0),
                readyCount = obj.optInt("readyCount", 0),
                allReady = obj.optBoolean("allReady", true),
                question = obj.optJSONObject("question")?.let(GameQuestion::fromJson),
                answer = obj.optJSONObject("answer")?.let(GameAnswer::fromJson),
                trivia = obj.optJSONObject("trivia")?.let(GameTrivia::fromJson),
                charada = obj.optJSONObject("charada")?.let(GameCharada::fromJson),
                wager = obj.optJSONObject("wager")?.let(GameWager::fromJson),
                impostor = obj.optJSONObject("impostor")?.let(GameImpostor::fromJson),
                drawing = obj.optJSONObject("drawing")?.let(GameDrawing::fromJson),
                auction = obj.optJSONObject("auction")?.let(GameAuction::fromJson),
                fibbage = obj.optJSONObject("fibbage")?.let(GameFibbage::fromJson),
                fiesta = obj.optJSONObject("fiesta")?.let(FiestaState::fromJson),
                audience = obj.optInt("audience", 0),
                highlights = obj.optJSONArray("highlights").objects(GameHighlight::fromJson),
                you = obj.optJSONObject("you")?.let(GameYou::fromJson),
                reactions = obj.optJSONArray("reactions").objects(GameReaction::fromJson),
            )
        }
    }
}

data class GameCreated(
    val code: String,
    val displayCode: String,
    val hostToken: String,
    val playerToken: String,
    val playerId: String,
    val hostId: String,
    val expiresAt: Long,
    val snapshot: GameSnapshot?,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameCreated(
            code = obj.optString("code"),
            displayCode = obj.optString("displayCode").ifBlank { GameCodes.format(obj.optString("code")) },
            hostToken = obj.optString("hostToken"),
            playerToken = obj.optString("playerToken"),
            playerId = obj.optString("playerId"),
            hostId = obj.optString("hostId"),
            expiresAt = obj.optLong("expiresAt", 0L),
            snapshot = obj.optJSONObject("snapshot")?.let(GameSnapshot::fromJson),
        )
    }
}

data class GameJoined(
    val playerToken: String,
    val playerId: String,
    val snapshot: GameSnapshot?,
) {
    companion object {
        fun fromJson(obj: JSONObject) = GameJoined(
            playerToken = obj.optString("playerToken"),
            playerId = obj.optString("playerId"),
            snapshot = obj.optJSONObject("snapshot")?.let(GameSnapshot::fromJson),
        )
    }
}

data class FiestaHostCue(
    val voice: String,
    val moment: String,
    val text: String,
    val variant: Int = 0,
    val audio: String = "",
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaHostCue(
            voice = obj.optString("voice"),
            moment = obj.optString("moment"),
            text = obj.optString("text"),
            variant = obj.optInt("variant", 0),
            audio = obj.optString("audio"),
        )
    }
}

/** Burlas compradas con monedas: vuelan sobre el plató y no tocan el marcador. */
data class FiestaTaunt(
    val id: String,
    val name: String,
    val text: String,
    val color: String,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaTaunt(
            id = obj.optString("id"),
            name = obj.optString("name"),
            text = obj.optString("text"),
            color = obj.optString("color", "#FFFFFF"),
        )
    }
}

data class FiestaObject(
    val name: String,
    val description: String,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaObject(
            name = obj.optString("name"),
            description = obj.optString("description"),
        )
    }
}

data class FiestaOption(
    val id: String,
    val text: String,
    val votes: Int,
    val isYours: Boolean,
    val authorName: String,
    val role: String,
    val slogan: String,
    val strokes: List<GameStroke>,
    val value: Int,
    val count: Int,
    val rank: Int,
    val audienceFavorite: Boolean = false,
    val remixerName: String = "",
    val sloganAuthorName: String = "",
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaOption(
            id = obj.optString("id"),
            text = obj.optString("text"),
            votes = obj.optInt("votes", 0),
            isYours = obj.optBoolean("isYours", false),
            authorName = obj.optString("authorName"),
            role = obj.optString("role"),
            slogan = obj.optString("slogan"),
            strokes = obj.optJSONArray("strokes").strokes(),
            value = obj.optInt("value", 0),
            count = obj.optInt("count", 0),
            rank = obj.optInt("rank", 0),
            audienceFavorite = obj.optBoolean("audienceFavorite", false),
            remixerName = obj.optString("remixerName"),
            sloganAuthorName = obj.optString("sloganAuthorName"),
        )
    }
}

data class FiestaYou(
    val submitted: Boolean,
    val voted: Boolean,
    val vote: String,
    val text: String,
    val choice: Int,
    val value: Int,
    val points: Int,
    val score: Int,
    val role: String,
    val coins: Int = 0,
    val perk: String = "",
    val activePerk: String = "",
    val bonusPick: Int = -1,
    val submittedOrder: Int = 0,
    val bet: Int = 0,
    val betWon: Boolean = false,
    val betPayout: Int = 0,
    val betMultiplier: Int = 0,
    val hint: String = "",
    val hintYears: List<Int> = emptyList(),
    val hintUsed: Boolean = false,
    val screen: String? = null,
    val quickChips: List<String> = emptyList(),
    val chatRole: String? = null,
    val secretGoal: String? = null,
    val remixPool: FiestaRemixPool? = null,
    val remixChoice: FiestaRemixChoice? = null,
    val incomingTaunt: FiestaIncomingTaunt? = null,
    val finalBoost: Double = 0.0,
    val lotRole: String? = null,
    val lotId: String? = null,
    val lotHint: String? = null,
    val waitBonus: Boolean = false,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaYou(
            submitted = obj.optBoolean("submitted", false),
            voted = obj.optBoolean("voted", false),
            vote = obj.optString("vote"),
            text = obj.optString("text"),
            choice = obj.optInt("choice", -1),
            value = obj.optInt("value", 0),
            points = obj.optInt("points", 0),
            score = obj.optInt("score", 0),
            role = obj.optString("role"),
            coins = obj.optInt("coins", 0),
            perk = obj.optString("perk"),
            activePerk = obj.optString("activePerk"),
            bonusPick = obj.optInt("bonusPick", -1),
            submittedOrder = obj.optInt("submittedOrder", 0),
            bet = obj.optInt("bet", 0),
            betWon = obj.optBoolean("betWon", false),
            betPayout = obj.optInt("betPayout", 0),
            betMultiplier = obj.optInt("betMultiplier", 0),
            hint = obj.optString("hint"),
            hintYears = obj.optJSONArray("hintYears")?.let { array ->
                (0 until array.length()).map { array.optInt(it) }
            }.orEmpty(),
            hintUsed = obj.optBoolean("hintUsed", false),
            screen = obj.optString("screen").takeIf { it.isNotBlank() },
            quickChips = obj.optJSONArray("quickChips")?.let { array ->
                (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty(),
            chatRole = obj.optString("chatRole").takeIf { it.isNotBlank() },
            secretGoal = obj.optString("secretGoal").takeIf { it.isNotBlank() },
            remixPool = obj.optJSONObject("remixPool")?.let(FiestaRemixPool::fromJson),
            remixChoice = obj.optJSONObject("remixChoice")?.let(FiestaRemixChoice::fromJson),
            incomingTaunt = obj.optJSONObject("incomingTaunt")?.let(FiestaIncomingTaunt::fromJson),
            finalBoost = obj.optDouble("finalBoost", 0.0),
            lotRole = obj.optString("lotRole").takeIf { it.isNotBlank() },
            lotId = obj.optString("lotId").takeIf { it.isNotBlank() },
            lotHint = obj.optString("lotHint").takeIf { it.isNotBlank() },
            waitBonus = obj.optBoolean("waitBonus", false),
        )
    }
}

data class FiestaTwist(
    val id: String,
    val title: String,
    val description: String,
    val multiplier: Float,
    val duration: Float,
    val coin: Float,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaTwist(
            id = obj.optString("id"),
            title = obj.optString("title"),
            description = obj.optString("description"),
            multiplier = obj.optDouble("multiplier", 1.0).toFloat(),
            duration = obj.optDouble("duration", 1.0).toFloat(),
            coin = obj.optDouble("coin", 1.0).toFloat(),
        )
    }
}

data class FiestaPerk(
    val id: String,
    val title: String,
    val description: String,
    val cost: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaPerk(
            id = obj.optString("id"),
            title = obj.optString("title"),
            description = obj.optString("description"),
            cost = obj.optInt("cost", 0),
        )
    }
}

data class FiestaBonusOption(
    val id: String,
    val label: String,
    val tone: String,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaBonusOption(
            id = obj.optString("id"),
            label = obj.optString("label"),
            tone = obj.optString("tone"),
        )
    }
}

data class FiestaBonusResult(
    val id: String,
    val name: String,
    val choice: Int,
    val coins: Int,
    val points: Int,
    val label: String,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaBonusResult(
            id = obj.optString("id"),
            name = obj.optString("name"),
            choice = obj.optInt("choice", -1),
            coins = obj.optInt("coins", 0),
            points = obj.optInt("points", 0),
            label = obj.optString("label"),
        )
    }
}

data class FiestaBonus(
    val title: String,
    val instruction: String,
    val options: List<FiestaBonusOption>,
    val results: List<FiestaBonusResult>,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaBonus(
            title = obj.optString("title"),
            instruction = obj.optString("instruction"),
            options = obj.optJSONArray("options").objects(FiestaBonusOption::fromJson),
            results = obj.optJSONArray("results").objects(FiestaBonusResult::fromJson),
        )
    }
}

data class FiestaReveal(
    val title: String,
    val lines: List<String>,
    val top: Int,
    val trueValue: Int,
    val correct: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaReveal(
            title = obj.optString("title"),
            lines = obj.optJSONArray("lines").strings(),
            top = obj.optInt("top", 0),
            trueValue = obj.optInt("trueValue", 0),
            correct = obj.optInt("correct", -1),
        )
    }
}


data class FiestaDuelOption(
    val id: String,
    val text: String,
)

data class FiestaDuelState(
    val optionA: FiestaDuelOption?,
    val optionB: FiestaDuelOption?,
    val duelIndex: Int,
    val totalDuels: Int,
    val prompt: String,
    val isFinal: Boolean,
    val winnerId: String = "",
    val knockout: Boolean = false,
    val votesA: Int = 0,
    val votesB: Int = 0,
) {
    companion object {
        fun fromJson(obj: JSONObject): FiestaDuelState {
            val results = obj.optJSONArray("results")
            val last = if (results != null && results.length() > 0) results.optJSONObject(results.length() - 1) else null
            return FiestaDuelState(
                optionA = obj.optJSONObject("optionA")?.let {
                    FiestaDuelOption(it.optString("id"), it.optString("text"))
                },
                optionB = obj.optJSONObject("optionB")?.let {
                    FiestaDuelOption(it.optString("id"), it.optString("text"))
                },
                duelIndex = obj.optInt("duelIndex", 0),
                totalDuels = obj.optInt("duelTotal", obj.optInt("totalDuels", 1)),
                prompt = obj.optString("prompt"),
                isFinal = obj.optBoolean("isFinal", false),
                winnerId = obj.optString("winnerId", last?.optString("winner") ?: ""),
                knockout = obj.optBoolean("knockout", last?.optBoolean("knockout", false) ?: false),
                votesA = obj.optInt("votesA", last?.optInt("votesA", 0) ?: 0),
                votesB = obj.optInt("votesB", last?.optInt("votesB", 0) ?: 0),
            )
        }
    }
}

data class FiestaVoxState(
    val anchorRank3Text: String,
    val mysteryAnswer: String,
    val revealedRank: Int,
    val correctSide: String,
) {
    companion object {
        fun fromJson(obj: JSONObject): FiestaVoxState {
            val rank3 = obj.optJSONObject("rank3")
            val mystery = obj.optJSONObject("mysteryAnswer")
            val rank = mystery?.optInt("rank", 0) ?: 0
            return FiestaVoxState(
                anchorRank3Text = rank3?.optString("text").orEmpty(),
                mysteryAnswer = mystery?.optString("text").orEmpty(),
                revealedRank = rank,
                correctSide = when {
                    rank in 1..2 -> "MAS_ARRIBA"
                    rank >= 4 -> "MAS_ABAJO"
                    else -> ""
                },
            )
        }
    }
}

data class FiestaSortItem(
    val id: String,
    val title: String,
    val year: Int? = null,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaSortItem(
            id = obj.optString("id"),
            title = obj.optString("title"),
            year = if (obj.has("year")) obj.optInt("year") else null,
        )
    }
}

data class FiestaChatMessage(
    val id: String,
    val author: String,
    val role: String,
    val text: String,
    val reactions: Map<String, Int> = emptyMap(),
) {
    val reactionTotal: Int get() = reactions.values.sum()

    companion object {
        fun fromJson(obj: JSONObject): FiestaChatMessage {
            val map = linkedMapOf<String, Int>()
            val raw = obj.opt("reactions")
            if (raw is JSONObject) {
                raw.keys().forEach { key -> map[key] = raw.optInt(key) }
            } else if (raw != null) {
                map["total"] = obj.optInt("reactions", 0)
            }
            return FiestaChatMessage(
                id = obj.optString("id"),
                author = obj.optString("authorName"),
                role = obj.optString("role"),
                text = obj.optString("text"),
                reactions = map,
            )
        }
    }
}

data class FiestaLot(
    val lotId: String,
    val title: String,
    val sellerName: String,
    val pitch: String,
    val realValue: Int? = null,
    val isAuthentic: Boolean? = null,
    val totalBids: Int = 0,
) {
    companion object {
        fun fromJson(obj: JSONObject): FiestaLot {
            val objJson = obj.optJSONObject("object")
            val real = obj.optInt("realValue", 0)
            val authentic = if (obj.has("authentic") && !obj.isNull("authentic")) obj.optBoolean("authentic") else null
            return FiestaLot(
                lotId = obj.optString("id", obj.optString("lotId")),
                title = objJson?.optString("name").orEmpty().ifBlank { obj.optString("title") },
                sellerName = obj.optString("sellerName"),
                pitch = obj.optString("pitch"),
                realValue = if (real > 0) real else null,
                isAuthentic = authentic,
                totalBids = obj.optInt("invested", obj.optInt("totalBids", 0)),
            )
        }
    }
}

data class FiestaRemixDrawing(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val strokes: List<GameStroke>,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaRemixDrawing(
            id = obj.optString("id"),
            ownerId = obj.optString("ownerId"),
            ownerName = obj.optString("ownerName"),
            strokes = obj.optJSONArray("strokes").strokes(),
        )
    }
}

data class FiestaRemixSlogan(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val text: String,
) {
    companion object {
        fun fromJson(obj: JSONObject) = FiestaRemixSlogan(
            id = obj.optString("id"),
            ownerId = obj.optString("ownerId"),
            ownerName = obj.optString("ownerName"),
            text = obj.optString("text"),
        )
    }
}

data class FiestaRemixPool(
    val drawings: List<FiestaRemixDrawing> = emptyList(),
    val slogans: List<FiestaRemixSlogan> = emptyList(),
) {
    companion object {
        fun fromJson(obj: JSONObject?) = FiestaRemixPool(
            drawings = obj?.optJSONArray("drawings").objects(FiestaRemixDrawing::fromJson).orEmpty(),
            slogans = obj?.optJSONArray("slogans").objects(FiestaRemixSlogan::fromJson).orEmpty(),
        )
    }
}

data class FiestaRemixChoice(
    val drawingId: String,
    val sloganId: String,
) {
    companion object {
        fun fromJson(obj: JSONObject?) = FiestaRemixChoice(
            drawingId = obj?.optString("drawing").orEmpty(),
            sloganId = obj?.optString("slogan").orEmpty(),
        )
    }
}

data class FiestaIncomingTaunt(
    val id: String,
    val from: String,
    val text: String,
) {
    companion object {
        fun fromJson(obj: JSONObject?) = FiestaIncomingTaunt(
            id = obj?.optString("id").orEmpty(),
            from = obj?.optString("from").orEmpty(),
            text = obj?.optString("text").orEmpty(),
        )
    }
}

/** Payload genérico de los seis juegos de Vision+ Fiesta 4.0. */
data class FiestaState(
    val game: String,
    val title: String,
    val host: String,
    val phase: String,
    val screen: String,
    val prompt: String,
    val instruction: String,
    val hint: String,
    val round: Int,
    val rounds: Int,
    val options: List<FiestaOption>,
    val years: List<Int>,
    val correct: Int,
    val objectInfo: FiestaObject?,
    val trueValue: Int,
    val you: FiestaYou?,
    val submittedCount: Int,
    val voterCount: Int,
    val reveal: FiestaReveal?,
    val hostCue: FiestaHostCue?,
    val family: Boolean,
    val adult: Boolean,
    val soloTest: Boolean,
    val scoreboard: List<GamePlayer>,
    val twist: FiestaTwist? = null,
    val perks: List<FiestaPerk> = emptyList(),
    val bonus: FiestaBonus? = null,
    val banter: String = "",
    val coins: Int = 0,
    val perk: String = "",
    val activePerk: String = "",
    val currency: String = "Monedas",
    val currencyIcon: String = "VP",
    val shop: String = "Tienda de Fiesta",
    val signature: String = "",
    val taunts: List<FiestaTaunt> = emptyList(),
    val tauntChoices: List<String> = emptyList(),
    val hintCost: Int = 120,
    val tauntCost: Int = 60,
    val betOptions: List<Int> = listOf(100, 250, 500),
    val duelState: FiestaDuelState? = null,
    val voxState: FiestaVoxState? = null,
    val sortItems: List<FiestaSortItem> = emptyList(),
    val chatMessages: List<FiestaChatMessage> = emptyList(),
    val lots: List<FiestaLot> = emptyList(),
    val reactionBanner: String? = null,
    val audienceVotes: Map<String, Int> = emptyMap(),
) {
    companion object {
        fun fromJson(obj: JSONObject): FiestaState {
            val years = obj.optJSONArray("years")?.let { array ->
                (0 until array.length()).map { array.optInt(it) }
            }.orEmpty()
            val options = obj.optJSONArray("options")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(FiestaOption::fromJson)
                }
            }.orEmpty()
            val scoreboard = obj.optJSONArray("scoreboard")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(GamePlayer::fromJson)
                }
            }.orEmpty()
            return FiestaState(
                game = obj.optString("game"),
                title = obj.optString("title"),
                host = obj.optString("host"),
                phase = obj.optString("phase"),
                screen = obj.optString("screen"),
                prompt = obj.optString("prompt"),
                instruction = obj.optString("instruction"),
                hint = obj.optString("hint"),
                round = obj.optInt("round", 1),
                rounds = obj.optInt("rounds", 1),
                options = options,
                years = years,
                correct = obj.optInt("correct", -1),
                objectInfo = obj.optJSONObject("object")?.let(FiestaObject::fromJson),
                trueValue = obj.optInt("trueValue", 0),
                you = obj.optJSONObject("you")?.let(FiestaYou::fromJson),
                submittedCount = obj.optInt("submittedCount", 0),
                voterCount = obj.optInt("voterCount", 0),
                reveal = obj.optJSONObject("reveal")?.let(FiestaReveal::fromJson),
                hostCue = obj.optJSONObject("hostCue")?.let(FiestaHostCue::fromJson),
                family = obj.optBoolean("family", true),
                adult = obj.optBoolean("adult", false),
                soloTest = obj.optBoolean("soloTest", false),
                scoreboard = scoreboard,
                twist = obj.optJSONObject("twist")?.let(FiestaTwist::fromJson),
                perks = obj.optJSONArray("perks").objects(FiestaPerk::fromJson),
                bonus = obj.optJSONObject("bonus")?.let(FiestaBonus::fromJson),
                banter = obj.optString("banter"),
                coins = obj.optInt("coins", 0),
                perk = obj.optString("perk"),
                activePerk = obj.optString("activePerk"),
                currency = obj.optString("currency", "Monedas"),
                currencyIcon = obj.optString("currencyIcon", "VP"),
                shop = obj.optString("shop", "Tienda de Fiesta"),
                signature = obj.optString("signature"),
                taunts = obj.optJSONArray("taunts").objects(FiestaTaunt::fromJson),
                tauntChoices = obj.optJSONArray("tauntChoices")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
                }.orEmpty(),
                hintCost = obj.optInt("hintCost", 120),
                tauntCost = obj.optInt("tauntCost", 60),
                betOptions = obj.optJSONArray("betOptions")?.let { array ->
                    (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }
                }.orEmpty().ifEmpty { listOf(100, 250, 500) },
                duelState = obj.optJSONObject("duelState")?.let(FiestaDuelState::fromJson),
                voxState = obj.optJSONObject("voxState")?.let(FiestaVoxState::fromJson),
                sortItems = obj.optJSONArray("sortItems").objects(FiestaSortItem::fromJson),
                chatMessages = obj.optJSONArray("chatMessages").objects(FiestaChatMessage::fromJson),
                lots = obj.optJSONArray("lots").objects(FiestaLot::fromJson),
                reactionBanner = obj.optString("reactionBanner").takeIf { it.isNotBlank() },
                audienceVotes = obj.optJSONObject("audienceVotes")?.let { audience ->
                    val map = linkedMapOf<String, Int>()
                    audience.keys().forEach { key -> map[key] = audience.optInt(key) }
                    map
                }.orEmpty(),
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

private fun JSONArray?.ints(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { optInt(it) }.filter { it > 0 }
}

private fun JSONArray?.intsRaw(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { optInt(it) }
}

private fun JSONObject?.intMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    val out = linkedMapOf<String, Int>()
    keys().forEach { key -> out[key] = optInt(key, 0) }
    return out
}

private fun JSONArray?.strokes(): List<GameStroke> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let(GameStroke::fromJson)
    }
}

private fun <T> JSONArray?.objects(parse: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.let(parse) }
}
