package com.chronosbvrx.visionplus.data.gate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.chronosbvrx.visionplus.data.xuper.SecretKeystore
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The activation gate, ported from the original app's Telegram flow: the request mints a nonce,
 * the person approves it inside their own bot chat, and the code the bot prints validates the
 * pairing. The answer's token (signed, long-lived, bound to this install) lands on the
 * encrypted keystore and marks the app as activated.
 */
object GateClient {

    private const val BASE = "https://kinobot.comparadorinternet.co"
    private const val PREF_TOKEN = "gate_token"
    private const val PREF_DEVICE = "gate_device"
    private const val PREF_INSTALL = "gate_install_id"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class LinkStart(val nonce: String = "", val botLink: String = "", val expiresIn: Long = 600)

    @Serializable
    data class Validate(val valid: Boolean = false, val token: String = "", val reason: String? = null)

    @Serializable
    data class LinkStatus(val status: String = "")

    /** The install's device key (stable): SHA-256 of the gate domain plus the android_id. */
    fun deviceKey(context: Context): String {
        val prefs = SecretKeystore.get(context)
        prefs.getString(PREF_DEVICE, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
        val seed = androidId
            ?: prefs.getString(PREF_INSTALL, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(PREF_INSTALL, it).apply() }
        val digest = MessageDigest.getInstance("SHA-256").digest("vision-gate-v1:$seed".toByteArray())
        val key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        prefs.edit().putString(PREF_DEVICE, key).apply()
        return key
    }

    fun token(context: Context): String? =
        SecretKeystore.get(context).getString(PREF_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun isActivated(context: Context): Boolean = !token(context).isNullOrBlank()

    /** Step 1: mints the nonce the person must approve in the bot. */
    suspend fun start(context: Context): Result<LinkStart> = withContext(Dispatchers.IO) {
        val body = """{"device":"${deviceKey(context)}","platform":"phone"}"""
        http(url = "$BASE/api/link/start", method = "POST", body = body)
            .mapCatching { json.decodeFromString<LinkStart>(it) }
    }

    /** Step 3: validates the code the bot printed; on success saves the token. */
    suspend fun validate(context: Context, code: String): Result<Validate> = withContext(Dispatchers.IO) {
        val body = """{"code":${jsonQuote(code)},"device":"${deviceKey(context)}","platform":"phone"}"""
        http(url = "$BASE/api/validate-code", method = "POST", body = body)
            .mapCatching {
                val answered = json.decodeFromString<Validate>(it)
                if (answered.valid && answered.token.isNotBlank()) {
                    SecretKeystore.get(context).edit()
                        .putString(PREF_TOKEN, answered.token)
                        .putLong("gate_activated_at", System.currentTimeMillis())
                        .apply()
                }
                answered
            }
    }

    /** The pairing's state, in the server's own words (pending/validated/expired). */
    suspend fun status(context: Context, nonce: String): Result<LinkStatus> = withContext(Dispatchers.IO) {
        http(url = "$BASE/api/link/status?nonce=${java.net.URLEncoder.encode(nonce, "UTF-8")}", method = "GET")
            .mapCatching { json.decodeFromString<LinkStatus>(it) }
    }

    /** Opens the bot chat with the nonce already prefilled. */
    fun openBot(context: Context, botLink: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(botLink)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ─────────────────────────── network ───────────────────────────

    private fun http(url: String, method: String, body: String? = null): Result<String> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val text = (connection.inputStream ?: connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { r -> r.readText() } ?: ""
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: ${text.take(160)}")
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonQuote(raw: String): String = buildString {
        append('"')
        raw.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }
}
