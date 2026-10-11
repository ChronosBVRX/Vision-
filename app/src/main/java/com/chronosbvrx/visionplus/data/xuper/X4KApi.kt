package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * El contentId pelado del portal: el índice del servidor guarda los ids como `xtv:<cid>`
 * mientras que las repisas del portal los dan como `<cid>`; el portal solo entiende `<cid>`.
 * Acepta ambas formas (lo ya pelado pasa igual) para que la ficha, los capítulos y el play
 * funcionen se abran desde donde se abran (Inicio, Categorías, Búsqueda, Secciones).
 */
internal fun xtvPortalId(id: String): String {
    val t = id.trim()
    return if (t.startsWith("xtv:", ignoreCase = true)) t.substring(4).trim() else t
}

/**
 * The XTV portal (Xuper's "masnew" tenant) as this app's second content source.
 *
 * The wire protocol is the family's own: `hex(base64(DESede/ECB/PKCS5(json)))` with the tenant
 * key. The session renews itself through `v3/snToken -> v8/active` (the account is bound to the
 * device's sn, no password), exactly like the provider's app.
 *
 * Sessions can be taken away at any time (another device activating, the service's own rhythm):
 * every call is therefore *self-healing* — when one fails, the session is minted again and the
 * call retried once, with the engine's entries refreshed on the way.
 */
object X4KApi {
    private const val TAG = "X4KApi"

    /** The tenant key (568e194f-...), carried inside the provider's manifest. */
    private const val KEY = "568e194f-d170-4369-96e8-4a457f641880"

    /**
     * The wire key bytes: y7a_decode("568e194f-...")[:24] (the family's own decoder, whose
     * invalid-base64 handling shapes the key; a plain Base64.decode gives different bytes).
     */
    private val KEY_BYTES = byteArrayOf(
        0xe7.toByte(), 0xaf.toByte(), 0x1e.toByte(), 0xd7.toByte(), 0xde.toByte(), 0x1f.toByte(),
        0xfd.toByte(), 0xdd.toByte(), 0x7b.toByte(), 0xd3.toByte(), 0xfe.toByte(), 0x37.toByte(),
        0xeb.toByte(), 0xdf.toByte(), 0xfd.toByte(), 0xe9.toByte(), 0xef.toByte(), 0x3f.toByte(),
        0xe1.toByte(), 0xae.toByte(), 0x39.toByte(), 0xed.toByte(), 0xfe.toByte(), 0xb8.toByte(),
    )

    private const val HOST_MAIN = "fuxok.nguvmqhpk.com"
    private const val HOST_BACKUP = "mptec.dhkrxuzcy.com"

    private const val SN = "2392fe5186d83d73508a2c8c60a26ef6"
    private const val PORTAL = "masnew"
    private const val APP_ID = "com.android.msandroid"
    private const val APK_VER = "43404"
    private const val SPKG_VER = "2025-03-11 02:26:11_33_13_4.14.186-27270957"

    private const val PREFS = "x4k_api"
    private const val K_USER_TOKEN = "user_token"
    private const val K_SN_TOKEN = "sn_token"
    private const val K_SESSION_AT = "session_at"
    private const val K_ENTRIES = "entries_json"
    private const val K_PARAMS = "play_params"
    private const val K_ENGINE_AT = "engine_at"
    private const val K_ROWS = "rows_json"
    private const val K_ROWS_AT = "rows_at"
    private const val K_LIVE = "live_json"
    private const val K_LIVE_AT = "live_at"
    private const val K_META = "xtv_meta"

    /** Renew proactively after this long; kicked sessions recover reactively before that.
     *  Largo a propósito (6 h): la cuenta admite una sola sesión y renovar de más patea a
     *  los demás aparatos y al servidor (aaa100083), que al renovar nos patean de vuelta. */
    private const val SESSION_TTL_MS = 6L * 60 * 60 * 1000

    /** Entre dos acuñados, este silencio: si nos acaban de patear, reacuñar al instante solo
     *  alimenta la tormenta (cada mint expulsa al otro lado, que reacuña a su vez). */
    private const val MINT_COOLDOWN_MS = 30_000L

    @Volatile
    private var lastMintAt = 0L

    /** Entries carry session ids: they are re-minted well before the service forgets them. */
    private const val ENGINE_TTL_MS = 90L * 60 * 1000
    private const val ROWS_TTL_MS = 30L * 60 * 1000
    private const val LIVE_TTL_MS = 15L * 60 * 1000

    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun base(specific: JSONObject): JSONObject = JSONObject().apply {
        put("apkVersion", APK_VER)
        put("appId", APP_ID)
        put("appLanguage", "es")
        put("cpu", "arm64-v8a")
        put("deviceToken", "")
        put("hardwareInfo", "mt6769t")
        put("loginType", "3")
        put("model", "SM-A325M")
        put("product", "a32ub")
        put("reserve1", "000000000000")
        put("sn", SN)
        put("sysVersion", "13")
        val keys = specific.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            put(k, specific.get(k))
        }
    }

    /** One encrypted portal call, failing over to the backup host. */
    private fun post(path: String, specific: JSONObject): JSONObject {
        val body = base(specific)
        val wire = XuperCrypto.encryptBody(body.toString(), KEY_BYTES)
        var last: Exception? = null
        for (host in listOf(HOST_MAIN, HOST_BACKUP)) {
            try {
                val conn = (URL("https://$host$path").openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 25_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("apk", APP_ID)
                    setRequestProperty("apkVer", APK_VER)
                    setRequestProperty("spkgVer", SPKG_VER)
                    setRequestProperty("User-Agent", "okhttp/4.12.0")
                    setRequestProperty("Cache-Control", "no-store")
                }
                conn.outputStream.use { it.write(wire.toByteArray(Charsets.US_ASCII)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("HTTP $code")
                val obj = JSONObject(text)
                val rc = obj.optString("returnCode")
                if (rc != "0") throw IOException("portal $rc ${obj.optString("errorMessage")}".trim())
                val data = obj.opt("data")
                val decrypted = when (data) {
                    is String -> XuperCrypto.decryptBody(data, KEY_BYTES) ?: throw IOException("respuesta ilegible")
                    null, JSONObject.NULL -> "{}"
                    else -> data.toString()
                }
                return JSONObject(decrypted)
            } catch (e: Exception) {
                Log.w(TAG, "post $path @$host falló: ${e.message}")
                last = e
            }
        }
        throw last ?: IOException("sin hosts disponibles")
    }

    private fun snTokenCall(): String =
        post("/api/portalCore/v3/snToken", JSONObject().apply {
            put("channel", "default")
            put("macAddr", "02:00:00:00:00:00")
            put("snToken", "")
            put("portalCode", PORTAL)
        }).optString("snToken")

    private fun activeCall(snToken: String): JSONObject =
        post("/api/portalCore/v8/active", JSONObject().apply {
            put("snToken", snToken)
            put("macAddr", "02:00:00:00:00:00")
            put("channel", "default")
            put("portalCode", PORTAL)
        })

    private fun storeSession(context: Context, snToken: String, userToken: String) {
        prefs(context).edit()
            .putString(K_SN_TOKEN, snToken)
            .putString(K_USER_TOKEN, userToken)
            .putLong(K_SESSION_AT, System.currentTimeMillis())
            .apply()
    }

    /** A fresh userToken, renewing through the device's own snToken when needed. */
    suspend fun ensureSession(context: Context): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val p = prefs(context)
            val token = p.getString(K_USER_TOKEN, null)
            val at = p.getLong(K_SESSION_AT, 0L)
            if (!token.isNullOrBlank() && System.currentTimeMillis() - at < SESSION_TTL_MS) {
                return@synchronized token
            }
            renewLocked(context)
        }
    }

    /** Mints a brand new session (salvo cooldown de tormenta) y refresca las entries. */
    suspend fun renewSession(context: Context): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            renewLocked(context)
        }
    }

    private fun renewLocked(context: Context): String {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        if (now - lastMintAt < MINT_COOLDOWN_MS) {
            val existing = p.getString(K_USER_TOKEN, null)
            if (!existing.isNullOrBlank()) {
                Log.i(TAG, "sesión acuñada hace menos de 30s; reusando token fresco")
                return existing
            }
        }
        lastMintAt = now
        var sn = p.getString(K_SN_TOKEN, null).orEmpty()
        var active: JSONObject? = null
        if (sn.isNotBlank()) {
            active = try {
                activeCall(sn)
            } catch (e: Exception) {
                Log.w(TAG, "active con snToken guardado falló: ${e.message}")
                null
            }
        }
        if (active == null) {
            sn = snTokenCall()
            if (sn.isBlank()) throw IOException("sin snToken")
            active = activeCall(sn)
        }
        val userToken = active.optString("userToken")
        if (userToken.isBlank()) throw IOException("sin userToken")
        storeSession(context, sn, userToken)
        // The entries embed the session: a new session means new entries (y licencias de vivo nuevas).
        p.edit().remove(K_ENTRIES).remove(K_PARAMS).remove(K_ENGINE_AT).remove(K_LIVE).apply()
        Log.i(TAG, "sesión renovada")
        return userToken
    }

    /** Solo esto significa «la sesión ya no sirve»; un error de negocio NO debe renovar. */
    private fun isSessionFailure(e: Exception): Boolean {
        val m = e.message.orEmpty()
        return m.contains("portal aaa") || m.contains("portal200001") || m.contains("HTTP 4")
    }

    /**
     * Runs a portal call with the current session; when the session itself fails, mints a new one
     * and runs it again. Un error de negocio (100002/100008…) se propaga tal cual: renovar por él
     * tumbaba la sesión de la app una y otra vez.
     */
    private suspend fun callWithSession(
        context: Context,
        path: String,
        spec: (String) -> JSONObject,
    ): JSONObject = withContext(Dispatchers.IO) {
        val token = ensureSession(context)
        try {
            post(path, spec(token))
        } catch (e: Exception) {
            if (!isSessionFailure(e)) throw e
            Log.w(TAG, "$path falló con la sesión actual; renovando… (${e.message})")
            val fresh = renewSession(context)
            post(path, spec(fresh))
        }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "falló: ${e.message}")
        null
    }

    /** The SLB entries + play params the engine needs (re-minted with each session). */
    suspend fun ensureEngineData(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val fresh = p.getString(K_ENTRIES, null)?.isNotBlank() == true &&
            p.getString(K_PARAMS, null)?.isNotBlank() == true &&
            System.currentTimeMillis() - p.getLong(K_ENGINE_AT, 0L) < ENGINE_TTL_MS
        if (fresh && !force) return@withContext
        val slb = callWithSession(context, "/api/portalCore/v14/getSlbInfo") { token ->
            JSONObject().apply {
                put("userToken", token)
                put("userId", USER_ID)
                put("hasPay", "0")
                put("userIdentity", "1")
                put("portalCode", PORTAL)
                put("type", "merge")
                put("appVer", "49902")
                put("lang", "es")
                put("encMediaSupported", 1)
                put("liveCodeList", JSONArray().put("masmobile_live"))
                put("appParams", "")
                put("reserve1", "000000000000")
                put("pipFlag", "0")
            }
        }
        p.edit()
            .putString(K_ENTRIES, slb.optJSONArray("cdn_list")?.toString().orEmpty())
            .putString(K_PARAMS, slb.optString("play_params"))
            .putLong(K_ENGINE_AT, System.currentTimeMillis())
            .apply()
    }

    private const val USER_ID = "967111326"

    /** The XTV shelves for Home: featured, movies, series, kids and anime. */
    suspend fun homeRows(context: Context): List<CatalogRow> = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val cached = p.getString(K_ROWS, null)
        val at = p.getLong(K_ROWS_AT, 0L)
        if (!cached.isNullOrBlank() && System.currentTimeMillis() - at < ROWS_TTL_MS) {
            return@withContext guarded { rowsFromJson(JSONArray(cached)) }.orEmpty()
        }
        ensureEngineData(context)
        val rows = ArrayList<CatalogRow>()
        val plan = listOf(
            "masnew_featured" to 4,
            "masnew_movies" to 10,
            "masnew_series" to 6,
            "masnew_kids" to 4,
            "masnew_anime" to 4,
        )
        val seen = HashSet<String>()
        val metaStore = runCatching { JSONObject(p.getString(K_META, "{}") ?: "{}") }.getOrDefault(JSONObject())
        for ((code, maxShelves) in plan) {
            val d = guarded {
                callWithSession(context, "/api/portalCore/getNextColumns") { token ->
                    JSONObject().apply {
                        put("userId", USER_ID)
                        put("userToken", token)
                        put("portalCode", PORTAL)
                        put("pageNum", 1)
                        put("pageSize", 100)
                        put("columnCode", code)
                        put("version", "")
                    }
                }
            } ?: continue
            val shelves = d.optJSONArray("recommendList") ?: continue
            var taken = 0
            for (i in 0 until shelves.length()) {
                val shelf = shelves.optJSONObject(i) ?: continue
                val assets = shelf.optJSONArray("assetList") ?: continue
                val films = ArrayList<Film>()
                for (j in 0 until assets.length()) {
                    val a = assets.optJSONObject(j) ?: continue
                    val cid = a.optString("contentId")
                    if (cid.isBlank() || !seen.add(cid)) continue
                    val poster = pickPoster(a.optJSONArray("posterList"), "icon")
                        ?: pickPoster(a.optJSONArray("posterList"), "poster")
                        ?: ""
                    films += Film(
                        id = cid,
                        title = a.optString("name"),
                        year = 0,
                        durationMin = 0,
                        synopsis = a.optString("description"),
                        posterUrl = poster,
                        videoUrl = "xtv:$cid",
                        sourceUrl = "visionplus.tv",
                    )
                    // Professional metadata (score, cast, genres) kept for the details page.
                    metaStore.put(cid, JSONObject().apply {
                        put("sc", a.optString("score"))
                        put("dir", a.optString("director"))
                        put("act", a.optString("actorDisplay"))
                        put("gen", a.optString("tags"))
                        put("aud", a.optString("moreAudio"))
                        put("sub", a.optString("moreSubtitle"))
                        put("typ", a.optString("programType"))
                    })
                }
                if (films.isEmpty()) continue
                val shelfTitle = cleanShelfTitle(shelf.optString("name"))
                rows += CatalogRow(id = "xtv-$code-$i", title = shelfTitle, films = films)
                taken++
                if (taken >= maxShelves) break
            }
        }
        if (rows.isNotEmpty()) {
            p.edit()
                .putString(K_ROWS, rowsToJson(rows))
                .putLong(K_ROWS_AT, System.currentTimeMillis())
                .putString(K_META, metaStore.toString())
                .apply()
        } else {
            guarded { rowsFromJson(JSONArray(cached.orEmpty())) }.orEmpty().let { stale ->
                if (stale.isNotEmpty()) return@withContext stale
            }
        }
        rows
    }

    internal fun cleanShelfTitle(raw: String): String {
        val clean = raw.trim()
        val lower = clean.lowercase()
        return when {
            lower.contains("recien agregad") || lower.contains("recién agregad") -> "Recién agregadas"
            lower.contains("estreno") -> "Estrenos"
            lower.contains("novedad") -> "Novedades"
            lower.contains("ultimos lanzamiento") || lower.contains("últimos lanzamiento") -> "Últimos lanzamientos"
            lower.contains("mas vista") || lower.contains("más vista") -> "Más vistas"
            lower.contains("tendencia") -> "Tendencias"
            else -> clean
        }
    }

    /** Score, cast, director, genres and language flags of a catalogue film (may be null). */
    fun metaFor(context: Context, contentId: String): JSONObject? = try {
        val all = JSONObject(prefs(context).getString(K_META, "{}") ?: "{}")
        all.optJSONObject(xtvPortalId(contentId)) ?: all.optJSONObject(contentId)
    } catch (_: Exception) {
        null
    }

    /**
     * The portal's play details for a film: available audio languages, qualities and formats.
     * Reuses the play call so the licence is minted once, at details time.
     */
    suspend fun detailFor(context: Context, contentId: String): JSONObject? = withContext(Dispatchers.IO) {
        guarded {
            val item = itemInfo(context, contentId)
            // El «play» puede no existir para teleplay en algunos tipos: la ficha sigue con el item.
            val play = runCatching { playInfo(context, contentId) }.getOrNull()
            if (item == null && play == null) return@guarded null
            JSONObject().apply {
                if (play != null) put("play", play)
                if (item != null) put("item", item)
            }
        }
    }

    private var memoryLiveChannels: List<com.chronosbvrx.visionplus.data.LiveChannel>? = null
    private var memoryLiveChannelsAt = 0L

    /** The live catalogue as the app's channels, ready for the live screen and the zapper. */
    suspend fun liveChannelsFor(context: Context, force: Boolean = false): List<com.chronosbvrx.visionplus.data.LiveChannel> =
        withContext(Dispatchers.IO) {
            val cachedMem = memoryLiveChannels
            if (!force && cachedMem != null && cachedMem.isNotEmpty() && System.currentTimeMillis() - memoryLiveChannelsAt < LIVE_TTL_MS) {
                return@withContext cachedMem
            }
            liveRow(context, force = force)
            val arr = guarded { JSONArray(prefs(context).getString(K_LIVE, "[]")) } ?: JSONArray()
            val out = ArrayList<com.chronosbvrx.visionplus.data.LiveChannel>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val code = c.optString("channelCode")
                if (code.isBlank()) continue
                val name = displayChannelName(code, c.optString("name"))
                val group = com.chronosbvrx.visionplus.data.LiveTaxonomy.genreFor(name)
                val number = c.optString("channelNumber").takeIf { it.isNotBlank() }
                out += com.chronosbvrx.visionplus.data.LiveChannel(
                    name = name,
                    url = "xtvlive:$code",
                    logo = c.optString("posterUrl").takeIf { it.isNotBlank() },
                    group = group,
                )
            }
            if (out.isNotEmpty()) {
                memoryLiveChannels = out
                memoryLiveChannelsAt = System.currentTimeMillis()
            }
            out
        }

    /**
     * Traduce nombres de canales camuflados del catálogo XTV (p. ej. "Elite Sports Hub HD" con
     * código "ST☆R+HD_cyx") a su identidad real (ESPN / Star+) para que el usuario y el buscador
     * los identifiquen de inmediato.
     */
    internal fun displayChannelName(code: String, rawName: String): String {
        val trimmed = rawName.trim()
        val upperCode = code.uppercase()
        val lowerName = trimmed.lowercase()
        return when {
            upperCode == "ST☆R+HD_CYX" || lowerName == "elite sports hub hd" ->
                "ESPN / Star+ HD"
            upperCode == "ST☆R+ 2 HD_CYX" || lowerName == "elite sports hub 2 hd" ->
                "ESPN 2 / Star+ 2 HD"
            upperCode == "ST☆R+ 3 HD_CYX" || lowerName == "elite sports hub 3 hd" ->
                "ESPN 3 / Star+ 3 HD"
            upperCode == "ST☆R+ 4 HD_CYX" || lowerName == "elite sports hub 4 hd" ->
                "ESPN 4 / Star+ 4 HD"
            else -> trimmed
        }
    }

    /** The raw live list (already fetched) for the player's channel picker. */
    fun cachedLive(context: Context): JSONArray =
        guarded { JSONArray(prefs(context).getString(K_LIVE, "[]")) } ?: JSONArray()

    /** Synchronous access to cached live channels (in-memory, SharedPreferences or LiveStore) without network. */
    fun cachedLiveChannels(context: Context): List<com.chronosbvrx.visionplus.data.LiveChannel> {
        val cachedMem = memoryLiveChannels
        if (cachedMem != null && cachedMem.isNotEmpty()) return cachedMem
        val arr = cachedLive(context)
        if (arr.length() > 0) {
            val out = ArrayList<com.chronosbvrx.visionplus.data.LiveChannel>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val code = c.optString("channelCode")
                if (code.isBlank()) continue
                val name = displayChannelName(code, c.optString("name"))
                val group = com.chronosbvrx.visionplus.data.LiveTaxonomy.genreFor(name)
                out += com.chronosbvrx.visionplus.data.LiveChannel(
                    name = name,
                    url = "xtvlive:$code",
                    logo = c.optString("posterUrl").takeIf { it.isNotBlank() },
                    group = group,
                )
            }
            if (out.isNotEmpty()) {
                memoryLiveChannels = out
                memoryLiveChannelsAt = System.currentTimeMillis()
                return out
            }
        }
        val storeChannels = com.chronosbvrx.visionplus.data.LiveStore(context).cached(com.chronosbvrx.visionplus.data.MAGIS_PLAYLIST_ID)
        if (!storeChannels.isNullOrEmpty()) {
            memoryLiveChannels = storeChannels
            memoryLiveChannelsAt = System.currentTimeMillis()
            return storeChannels
        }
        return emptyList()
    }

    /** One EPG entry: a programme with its window. */
    data class EpgItem(
        val name: String,
        val description: String,
        val startMs: Long,
        val endMs: Long,
    )

    private val epgCache = ConcurrentHashMap<String, Pair<Long, List<EpgItem>>>()
    private const val EPG_CACHE_TTL_MS = 5 * 60 * 1000L

    /**
     * Guía electrónica de programación (EPG): consulta primero el servidor propio
     * (GET /v1/epg con feeds XMLTV completos de México y LatAm) y hace fallback al
     * portal XTV si el servidor no responde o no tiene datos del canal.
     */
    suspend fun epgFor(context: Context, channelCode: String, channelName: String = ""): List<EpgItem> = withContext(Dispatchers.IO) {
        val cacheKey = if (channelName.isNotBlank()) channelName.trim() else channelCode.trim()
        val hit = epgCache[cacheKey]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.first < EPG_CACHE_TTL_MS) {
            return@withContext hit.second
        }

        // 1. Servidor propio Vision+ (feeds XMLTV dedicados)
        val vpsItems = runCatching { fetchVpsEpg(channelCode, channelName) }.getOrNull()
        if (!vpsItems.isNullOrEmpty()) {
            epgCache[cacheKey] = now to vpsItems
            return@withContext vpsItems
        }

        // 2. Fallback: portal XTV
        val portalItems = guarded {
            val d = callWithSession(context, "/api/portalCore/v3/getProgram") { token ->
                JSONObject().apply {
                    put("portalCode", PORTAL)
                    put("channelCode", channelCode)
                    put("columnId", 76182)
                    put("type", "1")
                    put("userToken", token)
                    put("userId", USER_ID)
                }
            }
            val list = d.optJSONArray("programList") ?: JSONArray()
            val out = ArrayList<EpgItem>()
            for (i in 0 until list.length()) {
                val p = list.optJSONObject(i) ?: continue
                out += EpgItem(
                    name = p.optString("programName"),
                    description = p.optString("desc"),
                    startMs = parseClock(p.optString("startTime")),
                    endMs = parseClock(p.optString("endTime")),
                )
            }
            out
        } ?: emptyList()

        if (portalItems.isNotEmpty()) {
            epgCache[cacheKey] = now to portalItems
        }
        portalItems
    }

    private fun fetchVpsEpg(channelCode: String, channelName: String): List<EpgItem> {
        val resolvedName = when {
            channelCode.equals("ST☆R+HD_cyx", ignoreCase = true) ||
                channelName.contains("Elite Sports Hub HD", ignoreCase = true) ||
                channelName.equals("ESPN / Star+ HD", ignoreCase = true) -> "ESPN"
            channelCode.equals("ST☆R+ 2 HD_cyx", ignoreCase = true) ||
                channelName.contains("Elite Sports Hub 2", ignoreCase = true) ||
                channelName.contains("ESPN 2 / Star+ 2", ignoreCase = true) -> "ESPN 2"
            channelCode.equals("ST☆R+ 3 HD_cyx", ignoreCase = true) ||
                channelName.contains("Elite Sports Hub 3", ignoreCase = true) ||
                channelName.contains("ESPN 3 / Star+ 3", ignoreCase = true) -> "ESPN 3"
            channelCode.equals("ST☆R+ 4 HD_cyx", ignoreCase = true) ||
                channelName.contains("Elite Sports Hub 4", ignoreCase = true) ||
                channelName.contains("ESPN 4 / Star+ 4", ignoreCase = true) -> "ESPN 4"
            else -> channelName
        }
        val encName = java.net.URLEncoder.encode(resolvedName, "UTF-8")
        val encCode = java.net.URLEncoder.encode(channelCode, "UTF-8")
        val url = "${RemoteIndexSource.BASE_URL}/v1/epg?channel=$encName&code=$encCode"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 6_000
            setRequestProperty("X-Api-Key", RemoteIndexSource.API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            val arr = json.optJSONArray("programs") ?: return emptyList()
            val out = ArrayList<EpgItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val name = p.optString("name")
                if (name.isBlank()) continue
                out += EpgItem(
                    name = name,
                    description = p.optString("description"),
                    startMs = p.optLong("startMs"),
                    endMs = p.optLong("endMs"),
                )
            }
            out
        } finally {
            connection.disconnect()
        }
    }

    /** "20261008230600" (yyyyMMddHHmmss) -> epoch ms; 0 when unparseable. */
    private fun parseClock(text: String): Long {
        val digits = text.filter { it.isDigit() }
        if (digits.length < 14) return 0L
        return try {
            val cal = java.util.Calendar.getInstance()
            cal.clear()
            cal.set(
                digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt() - 1,
                digits.substring(6, 8).toInt(), digits.substring(8, 10).toInt(),
                digits.substring(10, 12).toInt(), digits.substring(12, 14).toInt(),
            )
            cal.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    /** Los capítulos de una serie: contentId jugable, número y nombre (getItemData type "0"). */
    data class EpisodeProgram(val contentId: String, val number: Int, val name: String)

    /**
     * Los capítulos de una serie/novela/anime. El portal los entrega en `simpleProgramList`
     * al pedir la ficha con type "0" (teleplay); cada capítulo se reproduce con su contentId
     * y el `seriesContentId` de la serie. Se prueba el "0" primero: una ficha type "1" sin
     * lista ocultaba los capítulos de la serie.
     */
    suspend fun episodeProgramsFor(context: Context, seriesId: String): List<EpisodeProgram> = withContext(Dispatchers.IO) {
        guarded {
            val cid = xtvPortalId(seriesId)
            for (type in listOf("0", "1")) {
                val asset = runCatching {
                    callWithSession(context, "/api/portalCore/v4/getItemData") { token ->
                        JSONObject().apply {
                            put("userToken", token)
                            put("userId", USER_ID)
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("type", type)
                            put("sortType", "")
                            put("language", "es")
                        }
                    }
                }.getOrNull()?.optJSONObject("assetData") ?: continue
                val list = asset.optJSONArray("simpleProgramList") ?: continue
                val out = ArrayList<EpisodeProgram>()
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val episodeId = o.optString("contentId")
                    if (episodeId.isBlank()) continue
                    out += EpisodeProgram(episodeId, o.optInt("seriesNumber", i + 1), o.optString("name"))
                }
                val distinct = out.distinctBy { it.contentId }
                if (distinct.isNotEmpty()) return@guarded distinct
            }
            emptyList()
        } ?: emptyList()
    }

    /** The portal's editorial card: duration, release year, genres, cast, subtitle languages. */
    suspend fun itemInfo(context: Context, contentId: String): JSONObject? = withContext(Dispatchers.IO) {
        guarded {
            val cid = xtvPortalId(contentId)
            // "1" es para películas; las series (teleplay) solo responden con "0".
            for (type in listOf("1", "0")) {
                val asset = runCatching {
                    callWithSession(context, "/api/portalCore/v4/getItemData") { token ->
                        JSONObject().apply {
                            put("userToken", token)
                            put("userId", USER_ID)
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("type", type)
                            put("sortType", "")
                            put("language", "es")
                        }
                    }
                }.getOrNull()?.optJSONObject("assetData")
                if (asset != null) return@guarded asset
            }
            null
        }
    }

    private suspend fun playInfo(context: Context, contentId: String): JSONObject? = withContext(Dispatchers.IO) {
        guarded {
            val cid = xtvPortalId(contentId)
            for (type in listOf("2", "1")) {
                val d = runCatching {
                    callWithSession(context, "/api/portalCore/v10/startPlayVOD") { token ->
                        JSONObject().apply {
                            put("userToken", token)
                            put("userId", USER_ID)
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("seriesContentId", "")
                            put("type", type)
                            put("startTime", 0)
                            put("authType", "0")
                            put("episodeNumberList", JSONArray().put(1))
                        }
                    }
                }.getOrNull() ?: continue
                if (collectMedias(d).length() > 0) return@guarded d
            }
            null
        }
    }

    /** The live catalogue (its addresses carry the fresh per-channel licenses). */
    suspend fun liveRow(context: Context, force: Boolean = false): CatalogRow? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val cached = p.getString(K_LIVE, null)
        val at = p.getLong(K_LIVE_AT, 0L)
        if (!force && !cached.isNullOrBlank() && System.currentTimeMillis() - at < LIVE_TTL_MS) {
            return@withContext guarded { liveRowFromJson(cached) }
        }
        val d = guarded {
            callWithSession(context, "/api/portalCore/v6/getLiveData") { token ->
                JSONObject().apply {
                    put("userToken", token)
                    put("userId", USER_ID)
                    put("portalCode", PORTAL)
                    put("columnId", 76182)
                    put("dataVersion", "")
                    put("pageNum", 1)
                    put("pageSize", 3000)
                    put("expireTimeStr", "")
                }
            }
        } ?: return@withContext guarded { cached?.let { liveRowFromJson(it) } }
        val json = d.optJSONArray("channelList")?.toString().orEmpty()
        if (json.isBlank()) return@withContext null
        p.edit().putString(K_LIVE, json).putLong(K_LIVE_AT, System.currentTimeMillis()).apply()
        guarded { liveRowFromJson(json) }
    }

    /** Filenames of the poster kinds the catalog uses. */
    private fun pickPoster(posters: JSONArray?, kind: String): String? {
        if (posters == null) return null
        var url: String? = null
        for (i in 0 until posters.length()) {
            val o = posters.optJSONObject(i) ?: continue
            if (o.optString("fileType") == kind) {
                url = o.optString("fileUrl")
                if (kind == "icon") return url
            }
        }
        return url
    }

    private fun rowsToJson(rows: List<CatalogRow>): String {
        val arr = JSONArray()
        rows.forEach { row ->
            val fj = JSONArray()
            row.films.forEach { f ->
                fj.put(JSONObject().apply {
                    put("id", f.id)
                    put("t", f.title)
                    put("s", f.synopsis)
                    put("p", f.posterUrl)
                    put("v", f.videoUrl)
                })
            }
            arr.put(JSONObject().put("id", row.id).put("title", row.title).put("films", fj))
        }
        return arr.toString()
    }

    private fun rowsFromJson(arr: JSONArray): List<CatalogRow> {
        val rows = ArrayList<CatalogRow>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val films = ArrayList<Film>()
            val fj = o.optJSONArray("films") ?: JSONArray()
            for (j in 0 until fj.length()) {
                val f = fj.optJSONObject(j) ?: continue
                films += Film(
                    id = f.optString("id"),
                    title = f.optString("t"),
                    year = 0,
                    durationMin = 0,
                    synopsis = f.optString("s"),
                    posterUrl = f.optString("p"),
                    videoUrl = f.optString("v"),
                    sourceUrl = "xuper.tv",
                )
            }
            rows += CatalogRow(id = o.optString("id"), title = o.optString("title"), films = films)
        }
        return rows
    }

    private fun liveRowFromJson(json: String): CatalogRow? {
        val arr = JSONArray(json)
        val films = ArrayList<Film>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val code = c.optString("channelCode")
            if (code.isBlank()) continue
            films += Film(
                id = "xtv-live-$code",
                title = displayChannelName(code, c.optString("name")),
                year = 0,
                durationMin = 0,
                synopsis = "",
                posterUrl = pickPoster(c.optJSONArray("posterList"), "icon") ?: "",
                videoUrl = "xtvlive:$code",
                sourceUrl = "xuper.tv",
            )
        }
        return if (films.isEmpty()) null else CatalogRow(id = "xtv-live", title = "TV en vivo", films = films)
    }

    /** A VOD play resolved to data, without launching: the player reuses it for "next" in place. */
    data class VodMedia(val mediasJson: String, val durationMs: Long, val subsLangs: String)

    /**
     * Resolves a VOD play (playCode + license + duration + subs) without launching anything.
     * Returns null when the portal gives no playable media.
     */
    suspend fun prepareVod(
        context: Context,
        contentId: String,
        title: String,
        seriesId: String = "",
        fallbackDurationMs: Long = 0L,
    ): VodMedia? = withContext(Dispatchers.IO) {
        guarded {
            ensureEngineData(context)
            val cid = xtvPortalId(contentId)
            val sid = xtvPortalId(seriesId)
            // The editorial card carries the true running time; a flaky network gets a second try.
            var item = itemInfo(context, cid)
            if (item == null) {
                kotlinx.coroutines.delay(800)
                item = itemInfo(context, cid)
            }
            var durMs = parseDurationMs(item?.optString("duration").orEmpty())
            // Los capítulos de serie no suelen traer duración propia: heredar de la ficha de la serie.
            if (durMs <= 0L && sid.isNotBlank()) {
                val seriesItem = itemInfo(context, sid)
                durMs = parseDurationMs(seriesItem?.optString("duration").orEmpty())
            }
            // Si el portal no la reporta, aplicar la duración declarada en catálogo (Film.durationMin).
            if (durMs <= 0L && fallbackDurationMs > 0L) {
                durMs = fallbackDurationMs
            }
            var medias: JSONArray? = null
            for (type in listOf("2", "1")) {
                val d = runCatching {
                    callWithSession(context, "/api/portalCore/v10/startPlayVOD") { token ->
                        JSONObject().apply {
                            put("userToken", token)
                            put("userId", USER_ID)
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("seriesContentId", sid)
                            put("type", type)
                            put("startTime", 0)
                            put("authType", "0")
                            put("episodeNumberList", JSONArray().put(1))
                        }
                    }
                }.getOrNull() ?: continue
                val list = collectMedias(d)
                if (list.length() > 0) {
                    medias = list
                    break
                }
            }
            val m = medias ?: return@guarded null
            VodMedia(
                mediasJson = m.toString(),
                durationMs = durMs,
                subsLangs = item?.optString("subsInfo").orEmpty(),
            )
        }
    }

    /**
     * Prepares a VOD play: asks the portal for the film's playCode + license and launches the
     * X4K player (its own process). Series may carry their episode/season context so the player
     * can offer "next" without leaving.
     */
    suspend fun playVod(
        context: Context,
        contentId: String,
        title: String,
        seriesId: String = "",
        episodeIds: List<String> = emptyList(),
        episodeIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonNames: List<String> = emptyList(),
        seasonIndex: Int = 0,
        resumeMs: Long = 0,
        posterUrl: String = "",
        fallbackDurationMs: Long = 0L,
    ): Boolean = withContext(Dispatchers.IO) {
        val cid = xtvPortalId(contentId)
        val prep = prepareVod(context, cid, title, seriesId, fallbackDurationMs) ?: return@withContext false
        val finalDuration = if (prep.durationMs > 0L) prep.durationMs else fallbackDurationMs
        launch(
            context = context,
            mode = "vod",
            code = cid,
            title = title,
            mediasJson = prep.mediasJson,
            durationMs = finalDuration,
            subsLangs = prep.subsLangs,
            seriesId = seriesId,
            episodeIds = episodeIds,
            episodeIndex = episodeIndex,
            seasonIds = seasonIds,
            seasonNames = seasonNames,
            seasonIndex = seasonIndex,
            resumeMs = resumeMs,
            posterUrl = posterUrl,
        )
        true
    }

    /**
     * Convierte cualquier representación de duración del portal a milisegundos:
     * - "02:17:00", "45:00"
     * - "90 min", "1h 30m", "1 h 30 min", "45m"
     * - Segundos o minutos numéricos: "5400", "90", "5400000"
     * Devuelve 0 cuando es desconocida.
     */
    fun parseDurationMs(text: String): Long {
        val raw = text.trim()
        if (raw.isBlank()) return 0L

        // 1. Formato con dos puntos "HH:mm:ss" o "mm:ss"
        if (raw.contains(':')) {
            val parts = raw.split(':').mapNotNull { it.trim().toLongOrNull() }
            return when (parts.size) {
                3 -> ((parts[0] * 3600) + (parts[1] * 60) + parts[2]) * 1000L
                2 -> ((parts[0] * 60) + parts[1]) * 1000L
                else -> 0L
            }
        }

        // 2. Formato con texto ("1h 45m", "95 min", "95m", "1 h 30 min", etc.)
        val lower = raw.lowercase()
        val hasHours = lower.contains('h')
        val hasMins = lower.contains("m")
        val hasSecs = lower.contains("s") && !lower.contains("ms")
        if (hasHours || hasMins || hasSecs) {
            var totalMs = 0L
            val hMatch = Regex("""(\d+)\s*h""").find(lower)
            if (hMatch != null) {
                totalMs += (hMatch.groupValues[1].toLongOrNull() ?: 0L) * 3600_000L
            }
            val mMatch = Regex("""(\d+)\s*(?:m|min|minutos)""").find(lower)
            if (mMatch != null) {
                totalMs += (mMatch.groupValues[1].toLongOrNull() ?: 0L) * 60_000L
            }
            val sMatch = Regex("""(\d+)\s*(?:s|seg|segundos)""").find(lower)
            if (sMatch != null) {
                totalMs += (sMatch.groupValues[1].toLongOrNull() ?: 0L) * 1000L
            }
            if (totalMs > 0L) return totalMs
        }

        // 3. Valor numérico puro (p.ej. "5400", "5400000", "90", "120")
        val num = raw.filter { it.isDigit() }.toLongOrNull() ?: return 0L
        return when {
            num <= 0L -> 0L
            // Si es >= 3,600,000 ya está en milisegundos (1h+ en ms)
            num >= 3_600_000L -> num
            // Si está entre 300 y 3,600,000: segundos (5 min a 1000 horas). Ej: 5400 seg = 90 min.
            num > 300L -> num * 1000L
            // Si está entre 1 y 300: minutos de duración (1 min a 5 horas). Ej: 45, 90, 120 min.
            else -> num * 60_000L
        }
    }

    private fun extractLiveMedias(arr: JSONArray, channelCode: String): JSONArray? {
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            if (c.optString("channelCode") != channelCode) continue
            val addrs = c.optJSONArray("liveAddressList") ?: continue
            val out = JSONArray()
            var best: JSONObject? = null
            for (j in 0 until addrs.length()) {
                val a = addrs.optJSONObject(j) ?: continue
                if (a.optString("tag") != "free") continue
                if (best == null || a.optInt("quality") >= best.optInt("quality")) best = a
            }
            if (best != null) {
                out.put(JSONObject().apply {
                    put("n", best.optString("playCode"))
                    put("l", best.optString("license"))
                    put("a", "")
                    put("q", best.optString("quality"))
                    put("e", "")
                    put("f", "")
                })
            }
            if (out.length() > 0) return out
            break
        }
        return null
    }

    /** Prepares a live play using the channel's current address from the live catalogue. */
    suspend fun playLive(
        context: Context,
        channelCode: String,
        title: String,
        channelCodes: List<String> = emptyList(),
        groupTitle: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        guarded {
            // Asegurar SLB CDN entries y play_params para el motor Titan (valida sesión si expiró)
            ensureEngineData(context)

            var cached = prefs(context).getString(K_LIVE, null)
            if (cached.isNullOrBlank()) {
                liveRow(context, force = true)
                cached = prefs(context).getString(K_LIVE, null)
            }
            var arr = if (cached.isNullOrBlank()) JSONArray() else JSONArray(cached)
            var medias = extractLiveMedias(arr, channelCode)

            // Si el canal no está en caché o la caché está desactualizada, forzar actualización
            if (medias == null) {
                liveRow(context, force = true)
                cached = prefs(context).getString(K_LIVE, null)
                arr = if (cached.isNullOrBlank()) JSONArray() else JSONArray(cached)
                medias = extractLiveMedias(arr, channelCode)
            }

            val m = medias ?: return@guarded false
            launch(
                context = context,
                mode = "live",
                code = channelCode,
                title = title,
                mediasJson = m.toString(),
                channelCodes = channelCodes,
                groupTitle = groupTitle.orEmpty(),
            )
            true
        } ?: false
    }

    private fun collectMedias(result: JSONObject): JSONArray {
        val out = JSONArray()
        val eps = result.optJSONArray("episodeList") ?: return out
        for (i in 0 until eps.length()) {
            val groups = eps.optJSONObject(i)?.optJSONArray("totalMovieList") ?: continue
            for (g in 0 until groups.length()) {
                val group = groups.optJSONObject(g) ?: continue
                val movies = group.optJSONArray("movieList") ?: continue
                for (m in 0 until movies.length()) {
                    val movie = movies.optJSONObject(m) ?: continue
                    val licenses = movie.optJSONArray("licenseList") ?: continue
                    for (l in 0 until licenses.length()) {
                        val lic = licenses.optJSONObject(l) ?: continue
                        if (lic.optString("tag") != "free") continue
                        out.put(JSONObject().apply {
                            put("n", movie.optString("contentId"))
                            put("l", lic.optString("license"))
                            put("a", movie.optString("audioInfo"))
                            put("q", group.optString("quality"))
                            put("e", movie.optString("encodeFormat"))
                            put("f", movie.optString("videoFormat"))
                        })
                    }
                }
            }
        }
        return out
    }

    private fun launch(
        context: Context,
        mode: String,
        code: String,
        title: String,
        mediasJson: String,
        durationMs: Long = 0L,
        subsLangs: String = "",
        seriesId: String = "",
        episodeIds: List<String> = emptyList(),
        episodeIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonNames: List<String> = emptyList(),
        seasonIndex: Int = 0,
        resumeMs: Long = 0,
        posterUrl: String = "",
        channelCodes: List<String> = emptyList(),
        groupTitle: String = "",
    ) {
        val p = prefs(context)
        val entries = p.getString(K_ENTRIES, "").orEmpty()
        val params = p.getString(K_PARAMS, "").orEmpty()
        fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val intent = Intent().apply {
            setClassName(context, "com.chronosbvrx.visionplus.ui.player.X4KPlayerAty")
            putExtra("mode", mode)
            putExtra("code", code)
            putExtra("title", title)
            putExtra("mediasB64", b64(mediasJson))
            if (durationMs > 0) putExtra("durationMs", durationMs)
            if (subsLangs.isNotBlank()) putExtra("subsLangs", subsLangs)
            if (entries.isNotBlank()) putExtra("entriesB64", b64(entries))
            if (params.isNotBlank()) putExtra("playParamsB64", b64(params))
            if (seriesId.isNotBlank() && episodeIds.isNotEmpty()) {
                putExtra("seriesId", seriesId)
                putStringArrayListExtra("epIds", ArrayList(episodeIds))
                putExtra("epIndex", episodeIndex)
                putStringArrayListExtra("seasonIds", ArrayList(seasonIds))
                putStringArrayListExtra("seasonNames", ArrayList(seasonNames))
                putExtra("seasonIndex", seasonIndex)
            }
            if (resumeMs > 0) putExtra("resumeMs", resumeMs)
            if (posterUrl.isNotBlank()) putExtra("posterUrl", posterUrl)
            if (channelCodes.isNotEmpty()) {
                putStringArrayListExtra("channelCodes", ArrayList(channelCodes))
            }
            if (groupTitle.isNotBlank()) {
                putExtra("channelGroup", groupTitle)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Log.i(TAG, "reproductor XTV lanzado: $mode $code")
    }
}
