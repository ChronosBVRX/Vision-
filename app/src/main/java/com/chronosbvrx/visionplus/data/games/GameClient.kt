package com.chronosbvrx.visionplus.data.games

import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP de Vision+ Play.
 *
 * Reglas: jamás lanza (todo error se devuelve), timeouts cortos y ninguna
 * dependencia del catálogo, del motor nativo ni de las descargas. El servidor es
 * el árbitro; aquí no se calcula ninguna respuesta correcta.
 */
object GameClient {
    internal var baseUrl: String = BuildConfig.FIESTA_BASE_URL.ifBlank { RemoteIndexSource.BASE_URL }
    internal var apiKey: String = RemoteIndexSource.API_KEY
    internal var connectTimeoutMs: Int = 2_500
    internal var readTimeoutMs: Int = 3_500

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Error(val code: Int, val message: String) : Result<Nothing>
    }

    sealed interface StateResult {
        data class Snapshot(val value: GameSnapshot) : StateResult
        data object Unchanged : StateResult
        data class Error(val code: Int, val message: String) : StateResult
    }

    private data class Raw(val code: Int, val body: String?, val error: String?)

    internal fun resetForTests() {
        baseUrl = BuildConfig.FIESTA_BASE_URL.ifBlank { RemoteIndexSource.BASE_URL }
        apiKey = RemoteIndexSource.API_KEY
        connectTimeoutMs = 2_500
        readTimeoutMs = 3_500
    }

    fun packs(): Result<List<GamePack>> {
        val raw = call("GET", "/v1/game/packs", null, headers())
        return parse(raw) { body ->
            val array = body.optJSONArray("packs")
            (0 until (array?.length() ?: 0)).mapNotNull { index ->
                array?.optJSONObject(index)?.let(GamePack::fromJson)
            }
        }
    }

    fun create(
        hostName: String,
        mode: String = "remote",
        game: String = "trivia",
        pack: String = "general",
        rounds: Int = GameCatalog.byId(game).rounds,
        questionSeconds: Int = 20,
        fair: Boolean = false,
        family: Boolean = true,
        soloTest: Boolean = false,
        syncCode: String = "",
    ): Result<GameCreated> {
        val body = JSONObject()
            .put("mode", mode)
            .put("game", GameCatalog.byId(game).id)
            .put("pack", pack)
            .put("hostName", hostName)
            .put("rounds", rounds)
            .put("questionSeconds", questionSeconds)
            .put("fair", fair)
            .put("family", family)
            .put("soloTest", soloTest)
        val raw = call("POST", "/v1/game/rooms", body, headers(syncCode = syncCode))
        return parse(raw) { GameCreated.fromJson(it) }
    }

    fun join(code: String, name: String, spectator: Boolean = false): Result<GameJoined> {
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/join",
            JSONObject().put("name", name).put("spectator", spectator),
        )
        return parse(raw) { GameJoined.fromJson(it) }
    }

    fun rejoin(code: String, playerToken: String): Result<GameJoined> {
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/rejoin", JSONObject(),
            headers(playerToken = playerToken),
        )
        return parse(raw) { GameJoined.fromJson(it) }
    }

    fun state(
        code: String,
        since: Long,
        playerToken: String,
        role: String = "player",
        hostToken: String = "",
    ): StateResult {
        val raw = call(
            "GET",
            "/v1/game/rooms/${pathCode(code)}/state?role=$role&since=$since",
            null,
            headers(
                playerToken = playerToken.takeIf { role == "player" },
                hostToken = hostToken.takeIf { role == "display" },
            ),
        )
        if (raw.error != null || raw.code == 0) {
            return StateResult.Error(0, raw.error ?: "No se pudo conectar")
        }
        if (raw.code == 204) return StateResult.Unchanged
        if (raw.code !in 200..299) return StateResult.Error(raw.code, errorMessage(raw))
        val body = raw.body?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return StateResult.Error(raw.code, "respuesta inválida")
        return runCatching { StateResult.Snapshot(GameSnapshot.fromJson(body)) }
            .getOrElse { StateResult.Error(raw.code, "respuesta inválida") }
    }

    fun answer(code: String, playerToken: String, round: Int, option: Int): Result<Boolean> {
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/answer",
            JSONObject().put("round", round).put("option", option),
            headers(playerToken = playerToken),
        )
        return parse(raw) { it.optBoolean("accepted", false) }
    }

    fun wager(code: String, playerToken: String, round: Int, points: Int): Result<Boolean> {
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/wager",
            JSONObject().put("round", round).put("points", points),
            headers(playerToken = playerToken),
        )
        return parse(raw) { it.optBoolean("accepted", false) }
    }

    fun charada(code: String, playerToken: String, action: String): Result<Boolean> {
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/charada",
            JSONObject().put("action", action),
            headers(playerToken = playerToken),
        )
        return parse(raw) { it.optBoolean("accepted", false) }
    }

    /** Acciones de los juegos de fiesta (dibujo, impostor, subasta y fibbage). */
    fun action(
        code: String,
        playerToken: String,
        round: Int,
        kind: String,
        text: String = "",
        target: String = "",
        bid: Int = 0,
        strokes: String = "",
        index: Int = 0,
        power: String = "",
        wire: String = "",
        choices: List<*>? = null,
        selected: List<Int>? = null,
        amount: Int = 0,
        vote: Boolean = false,
    ): Result<Boolean> {
        val body = JSONObject()
            .put("round", round)
            .put("kind", kind)
            .put("text", text)
            .put("target", target)
            .put("bid", bid)
            .put("index", index)
        if (power.isNotBlank()) body.put("power", power)
        if (wire.isNotBlank()) body.put("wire", wire)
        if (amount != 0) body.put("amount", amount)
        if (vote) body.put("vote", true)
        choices?.let { values ->
            body.put("choices", org.json.JSONArray(values))
        }
        selected?.let { values ->
            body.put("selected", org.json.JSONArray(values))
        }
        if (strokes.isNotBlank()) {
            val parsed = runCatching { org.json.JSONArray(strokes) }.getOrNull()
            if (parsed != null) body.put("strokes", parsed)
        }
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/action",
            body,
            headers(playerToken = playerToken),
        )
        return parse(raw) {
            it.optBoolean("accepted", false) || it.optBoolean("ignored", false)
        }
    }

    fun ready(code: String, playerToken: String, round: Int): Result<Boolean> {
        val raw = call(
            "POST", "/v1/game/rooms/${pathCode(code)}/ready",
            JSONObject().put("round", round),
            headers(playerToken = playerToken),
        )
        return parse(raw) { it.optBoolean("ready", false) }
    }

    fun start(code: String, hostToken: String): Result<Unit> =
        parse(call("POST", "/v1/game/rooms/${pathCode(code)}/host/start", JSONObject(), headers(hostToken = hostToken))) { }

    fun next(code: String, hostToken: String): Result<Unit> =
        parse(call("POST", "/v1/game/rooms/${pathCode(code)}/host/next", JSONObject(), headers(hostToken = hostToken))) { }

    fun close(code: String, hostToken: String): Result<Unit> =
        parse(call("POST", "/v1/game/rooms/${pathCode(code)}/host/close", JSONObject(), headers(hostToken = hostToken))) { }

    fun report(code: String, playerToken: String, questionId: String, reason: String = ""): Result<Unit> =
        parse(
            call(
                "POST", "/v1/game/rooms/${pathCode(code)}/report",
                JSONObject().put("questionId", questionId).put("reason", reason),
                headers(playerToken = playerToken),
            )
        ) { }

    private fun pathCode(code: String): String = GameCodes.normalize(code) ?: code.trim().uppercase()

    private fun headers(
        playerToken: String? = null,
        hostToken: String? = null,
        syncCode: String = "",
    ): Map<String, String> = buildMap {
        put("X-Api-Key", apiKey)
        put("Accept", "application/json")
        playerToken?.takeIf { it.isNotBlank() }?.let { put("X-Game-Player", it) }
        hostToken?.takeIf { it.isNotBlank() }?.let { put("X-Game-Host", it) }
        syncCode.takeIf { it.isNotBlank() }?.let { put("X-Sync-Code", it) }
    }

    private fun <T> parse(raw: Raw, shape: (JSONObject) -> T): Result<T> {
        if (raw.error != null || raw.code == 0) {
            return Result.Error(0, raw.error ?: "No se pudo conectar")
        }
        if (raw.code !in 200..299) return Result.Error(raw.code, errorMessage(raw))
        val body = raw.body?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return Result.Error(raw.code, "respuesta inválida")
        return runCatching { Result.Ok(shape(body)) }
            .getOrElse { Result.Error(raw.code, "respuesta inválida") }
    }

    private fun errorMessage(raw: Raw): String {
        val detail = raw.body
            ?.let { runCatching { JSONObject(it).optString("detail") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        return detail ?: raw.error ?: "Error ${raw.code}"
    }

    private fun call(
        method: String,
        path: String,
        body: JSONObject?,
        requestHeaders: Map<String, String> = headers(),
    ): Raw {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doInput = true
                setRequestProperty("Connection", "close")
                requestHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val stream = when {
                code == 204 -> null
                code in 200..299 -> connection.inputStream
                else -> connection.errorStream
            }
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            Raw(code, text, null)
        } catch (error: Exception) {
            Raw(0, null, error.message ?: error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
