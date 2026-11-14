package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Puente opcional con el broker de licencias FHD del VPS.
 *
 * El servidor mantiene la cuenta de cortesía y devuelve las pistas ya firmadas;
 * el SLB se pide después en el equipo para que el CDN valide la IP del
 * reproductor. Si el broker no está desplegado o falla, devuelve null y la app
 * sigue con el flujo local de siempre (no hay dependencia dura).
 *
 * La respuesta se guarda hasta que la propia licencia vence: si el broker o la
 * cuenta se caen, un título ya abierto sigue reproduciéndose mientras la firma
 * del CDN sea válida (≈24 h). Es caché de licencias, no fabricación: la clave
 * de firma no viaja en el APK.
 */
internal object VipBroker {
    private const val TAG = "VipBroker"
    private const val PATH = "/v1/play/vip"
    private const val FILE = "vip_broker.json"
    private const val MAX_ENTRIES = 80

    private val lock = Any()

    /** Tras un fallo no se insiste 60 s: si el broker acuña y falla en bucle, sus
     *  sesiones patean la de la app y el fallback local tampoco puede firmar. */
    @Volatile
    private var unavailableUntil = 0L

    internal data class Play(
        val medias: JSONArray,
        val token: String,
        val userId: String,
        val sn: String,
        val expiresAt: Long = 0L,
    )

    internal suspend fun play(
        context: Context,
        contentId: String,
        seriesContentId: String,
        columnId: Int,
        type: String,
        episodes: IntArray,
    ): Play? = withContext(Dispatchers.IO) {
        val key = "$contentId|$seriesContentId|$columnId|$type"
        cached(context, key)?.let { return@withContext it }
        val now = System.currentTimeMillis()
        if (now < unavailableUntil) return@withContext null
        val fresh = fetch(contentId, seriesContentId, columnId, type, episodes)
        if (fresh == null) {
            unavailableUntil = now + 60_000L
            return@withContext null
        }
        unavailableUntil = 0L
        store(context, key, fresh)
        fresh
    }

    private fun fetch(
        contentId: String,
        seriesContentId: String,
        columnId: Int,
        type: String,
        episodes: IntArray,
    ): Play? = runCatching {
        val body = JSONObject().apply {
            put("contentId", contentId)
            put("seriesContentId", seriesContentId)
            put("columnId", columnId)
            put("type", type)
            put("episodeNumberList", JSONArray().apply { episodes.forEach { put(it) } })
        }
        val conn = (URL(RemoteIndexSource.BASE_URL + PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json;charset=utf-8")
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            Log.i(TAG, "broker $code: ${text.take(180)}")
            return@runCatching null
        }
        val json = JSONObject(text)
        val medias = json.optJSONArray("medias") ?: return@runCatching null
        val token = json.optString("token")
        val userId = json.optString("userId")
        val sn = json.optString("sn")
        if (medias.length() == 0 || token.isBlank() || userId.isBlank()) return@runCatching null
        Log.i(TAG, "broker ok pistas=${medias.length()} user=$userId")
        Play(medias, token, userId, sn, (json.optLong("expiresAt") / 1000L) * 1000L)
    }.getOrNull()

    private fun cacheFile(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun readCache(context: Context): JSONObject = runCatching {
        val file = cacheFile(context)
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject())

    private fun cached(context: Context, key: String): Play? = synchronized(lock) {
        val all = readCache(context)
        val entry = all.optJSONObject(key) ?: return null
        if (entry.optLong("exp", 0L) <= System.currentTimeMillis()) {
            all.remove(key)
            writeCache(context, all)
            return null
        }
        val payload = entry.optString("payload")
        if (payload.isBlank()) return null
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val medias = json.optJSONArray("medias") ?: return null
        val token = json.optString("token")
        val userId = json.optString("userId")
        if (medias.length() == 0 || token.isBlank() || userId.isBlank()) return null
        Play(medias, token, userId, json.optString("sn"), entry.optLong("exp", 0L))
    }

    private fun store(context: Context, key: String, play: Play) {
        synchronized(lock) {
            val all = readCache(context)
            val now = System.currentTimeMillis()
            val exp = play.expiresAt.takeIf { it > now + 60_000L } ?: (now + 5 * 60_000L)
            val json = JSONObject().apply {
                put("medias", play.medias)
                put("token", play.token)
                put("userId", play.userId)
                put("sn", play.sn)
            }
            all.put(
                key,
                JSONObject().apply {
                    put("exp", exp)
                    put("payload", json.toString())
                },
            )
            // Podar vencidos y, si hay demasiados, los que vencen antes.
            val keys = all.keys().asSequence().toList()
            for (k in keys) {
                if (all.optJSONObject(k)?.optLong("exp", 0L) ?: 0L <= now) all.remove(k)
            }
            if (all.length() > MAX_ENTRIES) {
                val ordered = all.keys().asSequence()
                    .sortedBy { all.optJSONObject(it)?.optLong("exp", 0L) ?: 0L }
                    .toList()
                for (k in ordered.take(all.length() - MAX_ENTRIES)) all.remove(k)
            }
            writeCache(context, all)
        }
    }

    private fun writeCache(context: Context, all: JSONObject) {
        runCatching { cacheFile(context).writeText(all.toString()) }
    }
}
