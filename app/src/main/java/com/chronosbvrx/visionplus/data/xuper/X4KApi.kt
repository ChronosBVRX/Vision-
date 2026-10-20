package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.mirror.EpisodeMirror
import com.chronosbvrx.visionplus.data.normalizeDisplayName
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    internal const val HOST_MAIN = "fuxok.nguvmqhpk.com"
    internal const val HOST_BACKUP = "mptec.dhkrxuzcy.com"

    /** Identidad compartida de siempre (legado): todas las instalaciones sin slot propio
     *  la usan. Con slot, el equipo acuña SU sesión y nadie patea a nadie. */
    internal const val LEGACY_SN = "2392fe5186d83d73508a2c8c60a26ef6"
    internal const val LEGACY_USER_ID = "967111326"
    private const val PORTAL = "masnew"
    internal const val APP_ID = "com.android.msandroid"
    internal const val APK_VER = "43404"
    internal const val SPKG_VER = "2025-03-11 02:26:11_33_13_4.14.186-27270957"

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

    /** Si el portal RECHAZÓ el token (patada: aaa100083 y compañía), reacuñar es la única salida;
     *  este candado corto permite el mint inmediato sin encadenar mints en ráfaga. */
    private const val FORCED_MINT_COOLDOWN_MS = 10_000L

    @Volatile
    private var lastMintAt = 0L

    @Volatile
    private var lastForcedMintAt = 0L

    /** Slot propio del equipo (pool del servidor). Nulo = identidad compartida (legado).
     *  Volátil por proceso: cada proceso (`:x4k` incluido) lo inicializa al arrancar. */
    @Volatile
    internal var slotSn: String? = null

    @Volatile
    internal var slotUserId: String? = null

    @Volatile
    private var slotsReady = false

    /** Identidad efectiva del portal: la propia si hay slot, la compartida si no. */
    internal fun snOf(): String = slotSn?.takeIf { it.isNotBlank() } ?: LEGACY_SN

    internal fun userIdOf(): String = slotUserId?.takeIf { it.isNotBlank() } ?: LEGACY_USER_ID

    /** Carga el slot propio (una vez por proceso; barato tras la primera). */
    fun initSlots(context: Context) {
        if (slotsReady) return
        slotsReady = true
        try {
            SlotStore.load(context)?.let {
                slotSn = it.sn
                slotUserId = it.userId
            }
        } catch (_: Exception) {
        }
    }

    /** Fija el slot recién canjeado y jubila la sesión vieja (era de otra identidad). */
    fun applySlot(context: Context, slot: DeviceSlot) {
        SlotStore.save(context, slot)
        slotSn = slot.sn
        slotUserId = slot.userId
        slotsReady = true
        try {
            prefs(context).edit()
                .remove(K_USER_TOKEN).remove(K_SN_TOKEN).remove(K_SESSION_AT)
                .remove(K_ENTRIES).remove(K_PARAMS).remove(K_ENGINE_AT)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Códigos del portal que significan «tu sesión ya no sirve» (pateada o expirada). */
    // 100006/100007/100008 son errores de CONTENIDO («no existe»), no de sesión: tratarlos
    // como patada renovaba la sesión en tormenta cada vez que un título desaparecía del
    // portal (y un mint real borra entries/params a media reproducción). Solo 100001..100005,
    // 200000..200002 y 300001..300002 significan «la sesión ya no sirve».
    private val SESSION_CODE_RX = Regex("portal\\s*(?:10000[1-5]|20000[0-2]|30000[1-2])")

    /** ¿Se puede acuñar ahora? [force] (token rechazado) salta el silencio normal pero
     *  respeta su propio candado corto; puro, para los tests. */
    internal fun mintAllowed(nowMs: Long, lastMintAt: Long, lastForcedMintAt: Long, force: Boolean): Boolean =
        if (force) {
            nowMs - lastForcedMintAt >= FORCED_MINT_COOLDOWN_MS
        } else {
            nowMs - lastMintAt >= MINT_COOLDOWN_MS
        }

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
        put("sn", snOf())
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

    private fun activeCall(snToken: String, snOverride: String? = null): JSONObject =
        post("/api/portalCore/v8/active", JSONObject().apply {
            put("snToken", snToken)
            put("macAddr", "02:00:00:00:00:00")
            put("channel", "default")
            put("portalCode", PORTAL)
            if (!snOverride.isNullOrBlank()) put("sn", snOverride)
        })

    /** Sal compartida de la familia (misma en Xuper/Hydra/Crystal): el portal acepta el
     *  SN autoderivado `MD5(snToken + SALT)` sin aprovisionar nada (lo verificado). */
    internal const val SELF_MINT_SALT = "ntFT65w6itH!lHCPw7D=@qnsFC5adD28"

    /** SN propio a partir del snToken virgen; puro, para los tests. */
    internal fun deriveSn(snToken: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest((snToken + SELF_MINT_SALT).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.lowercase()
    }

    /** Alta automática (réplica Hydra, cero pool): snToken virgen → SN derivado → active.
     *  Devuelve el slot propio ya aplicado, o lanza si el portal lo rechaza. */
    suspend fun mintOwnSlot(context: Context): DeviceSlot = withContext(Dispatchers.IO) {
        synchronized(lock) {
            SlotStore.load(context)?.let { return@synchronized it }
            val snToken = snTokenCall()
            if (snToken.isBlank()) throw java.io.IOException("sin snToken")
            val sn = deriveSn(snToken)
            val active = activeCall(snToken, snOverride = sn)
            val userToken = active.optString("userToken")
            val userId = active.optString("userId")
            if (userToken.isBlank() || userId.isBlank()) throw java.io.IOException("auto-alta rechazada")
            val slot = DeviceSlot(sn, userId)
            applySlot(context, slot)
            storeSession(context, snToken, userToken)
            val now = System.currentTimeMillis()
            lastMintAt = now
            slot
        }
    }

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

    /** Mints a brand new session; [force] es para token RECHAZADO (patada), no para capricho. */
    suspend fun renewSession(context: Context, force: Boolean = false): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            renewLocked(context, force)
        }
    }

    /** Envuelve la acuñación: si falla queda como anomalía (diagnóstico) y se relanza. */
    private fun renewLocked(context: Context, force: Boolean = false): String = try {
        renewLockedInner(context, force)
    } catch (e: Exception) {
        com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
            "SESSION_RENEW_FAIL", "${e.javaClass.simpleName}: ${e.message}",
        )
        throw e
    }

    private fun renewLockedInner(context: Context, force: Boolean = false): String {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        if (!mintAllowed(now, lastMintAt, lastForcedMintAt, force)) {
            val existing = p.getString(K_USER_TOKEN, null)
            if (!existing.isNullOrBlank()) {
                Log.i(TAG, "mint en silencio (${if (force) "forzado" else "normal"}); reusando token")
                return existing
            }
        }
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
        // La marca de tiempo solo en ÉXITO: un mint fallido no debe bloquear el siguiente intento.
        lastMintAt = now
        if (force) lastForcedMintAt = now
        // The entries embed the session: a new session means new entries (y licencias de vivo nuevas).
        memoryLiveMedias.clear()
        memoryLiveArray = null
        p.edit().remove(K_ENTRIES).remove(K_PARAMS).remove(K_ENGINE_AT).remove(K_LIVE).apply()
        Log.i(TAG, "sesión renovada (${if (force) "forzada" else "normal"})")
        return userToken
    }

    /** Solo esto significa «la sesión ya no sirve»; detecta los errores de sesión del portal. */
    internal fun isSessionFailure(e: Exception): Boolean {
        val m = e.message.orEmpty().lowercase()
        return m.contains("portal aaa") ||
            SESSION_CODE_RX.containsMatchIn(m) ||
            m.contains("http 401") ||
            m.contains("http 403") ||
            m.contains("http 4")
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
            // Token rechazado = patada: re-acuñar de inmediato aunque el silencio normal siga.
            val fresh = renewSession(context, force = true)
            post(path, spec(fresh))
        }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "falló: ${e.message}")
        null
    }

    /**
     * Precalienta el proceso `:x4k` en segundo plano (DEX + librerías .so + boot + SLB entries/env
     * + índice en memoria de canales en vivo) para que al tocar un canal la reproducción inicie al instante.
     */
    fun prewarmEngine(context: Context) {
        try {
            val appCtx = context.applicationContext
            val p = prefs(appCtx)
            val entries = p.getString(K_ENTRIES, "").orEmpty()
            val params = p.getString(K_PARAMS, "").orEmpty()
            val intent = Intent(appCtx, X4KWarmupReceiver::class.java).apply {
                if (entries.isNotBlank()) putExtra("entriesJson", entries)
                if (params.isNotBlank()) putExtra("playParams", params)
            }
            appCtx.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "prewarmEngine ignorado: ${e.message}")
        }
    }

    /** The SLB entries + play params the engine needs (re-minted with each session). */
    suspend fun ensureEngineData(
        context: Context,
        force: Boolean = false,
        deferPrewarm: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val fresh = p.getString(K_ENTRIES, null)?.isNotBlank() == true &&
            p.getString(K_PARAMS, null)?.isNotBlank() == true &&
            System.currentTimeMillis() - p.getLong(K_ENGINE_AT, 0L) < ENGINE_TTL_MS
        if (fresh && !force) {
            if (!deferPrewarm) prewarmEngine(context)
            return@withContext
        }
        val slb = callWithSession(context, "/api/portalCore/v14/getSlbInfo") { token ->
            JSONObject().apply {
                put("userToken", token)
                put("userId", userIdOf())
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
        if (!deferPrewarm) prewarmEngine(context)
    }

    /** Precalienta el motor :x4k de forma estrictamente diferida tras la renderización inicial del Home. */
    fun scheduleDeferredWarmup(context: Context, delayMs: Long = 6_000L) {
        val appCtx = context.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(delayMs)
            runCatching {
                ensureEngineData(appCtx, deferPrewarm = false)
            }
        }
    }

    /** The XTV shelves for Home: featured, movies, series, kids and anime. Streams rows progressively. */
    suspend fun homeRows(
        context: Context,
        onRowLoaded: ((CatalogRow) -> Unit)? = null,
    ): List<CatalogRow> = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val cached = p.getString(K_ROWS, null)
        val at = p.getLong(K_ROWS_AT, 0L)
        if (!cached.isNullOrBlank() && System.currentTimeMillis() - at < ROWS_TTL_MS) {
            val cachedList = guarded { rowsFromJson(JSONArray(cached)) }.orEmpty()
            if (cachedList.isNotEmpty()) {
                cachedList.forEach { onRowLoaded?.invoke(it) }
                return@withContext cachedList
            }
        }
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
                        put("userId", userIdOf())
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
                        title = normalizeDisplayName(a.optString("name")),
                        year = portalYear(a),
                        durationMin = 0,
                        synopsis = a.optString("description"),
                        posterUrl = poster,
                        videoUrl = "xtv:$cid",
                        sourceUrl = "visionplus.tv",
                        score = portalScore(a),
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
                val newRow = CatalogRow(id = "xtv-$code-$i", title = shelfTitle, films = films)
                rows += newRow
                onRowLoaded?.invoke(newRow)
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

    /** Score del portal como Double (0 cuando no lo trae): hidrata el ranking del Home. */
    internal fun portalScore(a: JSONObject): Double {
        val raw = a.optString("score").trim()
        if (raw.isBlank()) return a.optDouble("score", 0.0)
        return raw.toDoubleOrNull()?.takeIf { it > 0.0 } ?: a.optDouble("score", 0.0)
    }

    /** Año del portal desde releaseTime/año directo (0 si no lo trae). */
    internal fun portalYear(a: JSONObject): Int {
        val candidates = listOf(
            a.optString("releaseTime"),
            a.optString("releaseDate"),
            a.optString("year"),
        )
        for (raw in candidates) {
            val digits = raw.filter { it.isDigit() }
            if (digits.length >= 4) {
                val y = digits.take(4).toIntOrNull() ?: continue
                if (y in 1900..2100) return y
            }
        }
        return a.optInt("year", 0).takeIf { it in 1900..2100 } ?: 0
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

    /** Índice en memoria O(1) de channelCode -> mediasJson pre-serializado para apertura instantánea en 0 ms. */
    private val memoryLiveMedias = ConcurrentHashMap<String, String>()

    @Volatile
    private var memoryLiveArray: JSONArray? = null

    internal fun indexLiveArray(arr: JSONArray) {
        if (arr.length() == 0) return
        memoryLiveArray = arr
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val code = c.optString("channelCode")
            if (code.isBlank()) continue
            val addrs = c.optJSONArray("liveAddressList") ?: continue
            var best: JSONObject? = null
            for (j in 0 until addrs.length()) {
                val a = addrs.optJSONObject(j) ?: continue
                if (a.optString("tag") != "free") continue
                if (best == null || a.optInt("quality") >= best.optInt("quality")) best = a
            }
            if (best != null) {
                val mediasArr = JSONArray().put(
                    JSONObject().apply {
                        put("n", best.optString("playCode"))
                        put("l", best.optString("license"))
                        put("a", "")
                        put("q", best.optString("quality"))
                        put("e", "")
                        put("f", "")
                    }
                )
                memoryLiveMedias[code] = mediasArr.toString()
            }
        }
    }

    internal fun cachedLiveMediasFor(channelCode: String): String? = memoryLiveMedias[channelCode]

    /** The live catalogue as the app's channels, ready for the live screen and the zapper. */
    suspend fun liveChannelsFor(context: Context, force: Boolean = false): List<com.chronosbvrx.visionplus.data.LiveChannel> =
        withContext(Dispatchers.IO) {
            val cachedMem = memoryLiveChannels
            if (!force && cachedMem != null && cachedMem.isNotEmpty() && System.currentTimeMillis() - memoryLiveChannelsAt < LIVE_TTL_MS) {
                return@withContext cachedMem
            }
            liveRow(context, force = force)
            val arr = if (force) {
                (guarded { JSONArray(prefs(context).getString(K_LIVE, "[]")) } ?: JSONArray()).also { indexLiveArray(it) }
            } else {
                cachedLive(context)
            }
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
    fun cachedLive(context: Context): JSONArray {
        val mem = memoryLiveArray
        if (mem != null && mem.length() > 0) return mem
        val arr = guarded { JSONArray(prefs(context).getString(K_LIVE, "[]")) } ?: JSONArray()
        if (arr.length() > 0) indexLiveArray(arr)
        return arr
    }

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
                    put("userId", userIdOf())
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

    private val EPISODE_NUMBER_REGEX = Regex("""(?:cap[ií]tulo|cap|episodio|ep|e|#|\b)\s*(\d{1,4})\b""", RegexOption.IGNORE_CASE)

    /**
     * Extrae, desduplica y ordena estrictamente por número de episodio (`seriesNumber`)
     * la lista `simpleProgramList` del portal XTV, preservando el orden ascendente 1, 2, 3...
     */
    internal fun parseEpisodePrograms(list: JSONArray?): List<EpisodeProgram> {
        if (list == null || list.length() == 0) return emptyList()
        data class IndexedEp(val ep: EpisodeProgram, val rawOrder: Int)
        val raw = ArrayList<IndexedEp>(list.length())
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val episodeId = o.optString("contentId").trim()
            if (episodeId.isBlank()) continue
            val name = o.optString("name").trim()
            val explicitNum = o.optInt("seriesNumber", 0).takeIf { it > 0 }
                ?: o.optInt("episodeNumber", 0).takeIf { it > 0 }
                ?: name.toIntOrNull()?.takeIf { it > 0 }
                ?: EPISODE_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
                ?: (i + 1)
            raw += IndexedEp(EpisodeProgram(episodeId, explicitNum, normalizeDisplayName(name)), i)
        }
        val distinct = raw
            .distinctBy { it.ep.contentId }
            .sortedWith(compareBy<IndexedEp> { it.ep.number }.thenBy { it.rawOrder })
            .map { it.ep }
        // Una película devuelve su propia ficha como único "capítulo": no es serie.
        // Se exige 2+ capítulos, igual que parsePlayEpisodePrograms.
        return if (distinct.size <= 1) emptyList() else distinct
    }

    /**
     * Respaldo desde `startPlayVOD` (`episodeList`): extrae y ordena los capítulos cuando
     * `simpleProgramList` no vino en la ficha.
     */
    internal fun parsePlayEpisodePrograms(play: JSONObject?): List<EpisodeProgram> {
        if (play == null) return emptyList()
        val eps = play.optJSONArray("episodeList") ?: return emptyList()
        data class IndexedEp(val ep: EpisodeProgram, val rawOrder: Int)
        val raw = ArrayList<IndexedEp>()
        for (i in 0 until eps.length()) {
            val epObj = eps.optJSONObject(i) ?: continue
            val groups = epObj.optJSONArray("totalMovieList") ?: continue
            var code = ""
            for (g in 0 until groups.length()) {
                val movies = groups.optJSONObject(g)?.optJSONArray("movieList") ?: continue
                for (m in 0 until movies.length()) {
                    val cid = movies.optJSONObject(m)?.optString("contentId")?.trim().orEmpty()
                    if (cid.isNotBlank()) {
                        code = cid
                        break
                    }
                }
                if (code.isNotBlank()) break
            }
            if (code.isBlank()) continue
            val epNum = epObj.optInt("episodeNumber", 0).takeIf { it > 0 }
                ?: epObj.optInt("seriesNumber", 0).takeIf { it > 0 }
                ?: (i + 1)
            val epName = epObj.optString("name").trim()
            raw += IndexedEp(EpisodeProgram(code, epNum, normalizeDisplayName(epName)), i)
        }
        val distinct = raw
            .distinctBy { it.ep.contentId }
            .sortedWith(compareBy<IndexedEp> { it.ep.number }.thenBy { it.rawOrder })
            .map { it.ep }
        return if (distinct.size <= 1) emptyList() else distinct
    }

    /**
     * Los capítulos de una serie/novela/anime. El portal los entrega en `simpleProgramList`
     * al pedir la ficha con type "0" (teleplay); cada capítulo se reproduce con su contentId
     * y el `seriesContentId` de la serie. Se prueba el "0" primero: una ficha type "1" sin
     * lista ocultaba los capítulos de la serie.
     */
    suspend fun episodeProgramsFor(context: Context, seriesId: String): List<EpisodeProgram> = withContext(Dispatchers.IO) {
        guarded {
            val cid = xtvPortalId(seriesId)
            // Espejo primero: la copia del servidor tarda ~ms y NO gasta la sesión del
            // equipo. Cualquier ausencia/fallo → null → el portal EXACTO de siempre
            // (el espejo solo puede mejorar, jamás empeorar).
            val mirrored = EpisodeMirror.episodes(seriesId)?.let { parseEpisodePrograms(it) }
            if (!mirrored.isNullOrEmpty()) return@guarded mirrored
            for (type in listOf("0", "1")) {
                val asset = runCatching {
                    callWithSession(context, "/api/portalCore/v4/getItemData") { token ->
                        JSONObject().apply {
                            put("userToken", token)
                            put("userId", userIdOf())
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("type", type)
                            put("sortType", "")
                            put("language", "es")
                        }
                    }
                }.getOrNull()?.optJSONObject("assetData") ?: continue
                val distinct = parseEpisodePrograms(asset.optJSONArray("simpleProgramList"))
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
                            put("userId", userIdOf())
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
                            put("userId", userIdOf())
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
                    put("userId", userIdOf())
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
                    put("y", f.year)
                    put("sc", f.score)
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
                    title = normalizeDisplayName(f.optString("t")),
                    year = f.optInt("y", 0),
                    durationMin = 0,
                    synopsis = f.optString("s"),
                    posterUrl = f.optString("p"),
                    videoUrl = f.optString("v"),
                    sourceUrl = "xuper.tv",
                    score = f.optDouble("sc", 0.0),
                )
            }
            rows += CatalogRow(id = o.optString("id"), title = o.optString("title"), films = films)
        }
        return rows
    }

    private fun liveRowFromJson(json: String): CatalogRow? {
        val arr = JSONArray(json)
        indexLiveArray(arr)
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

    /** Handshake adelantado desde la ficha (una sola reproducción por entrada). */
    private class PrefetchedVod(val atMs: Long, val media: VodMedia)

    private val vodPrefetch = java.util.concurrent.ConcurrentHashMap<String, PrefetchedVod>()
    private const val PREFETCH_TTL_MS = 90_000L

    /**
     * Adelanta el handshake de reproducción mientras el usuario mira la ficha: al pulsar
     * Reproducir no se espera al portal (en cajas lentas esas llamadas cuestan 1-4 s y
     * la app original del proveedor las pide al abrir la ficha, no al pulsar). La
     * entrada se consume UNA vez (licencia fresca por reproducción) y caduca a los 90 s.
     * Silencioso: cualquier fallo se ignora y el play normal lo repite.
     */
    suspend fun prewarmVod(
        context: Context,
        contentId: String,
        seriesId: String = "",
        fallbackDurationMs: Long = 0L,
        epNumber: Int = 1,
    ): Unit = withContext(Dispatchers.IO) {
        val cid = xtvPortalId(contentId)
        if (cid.isBlank()) return@withContext
        val now = System.currentTimeMillis()
        vodPrefetch[cid]?.takeIf { now - it.atMs < PREFETCH_TTL_MS }?.let { return@withContext }
        val cleanSeries = if (seriesId.isNotBlank()) xtvPortalId(seriesId) else ""
        runCatching {
            val prep = prepareVod(context, cid, "", cleanSeries, fallbackDurationMs)
            if (prep != null) vodPrefetch[cid] = PrefetchedVod(now, prep)
        }
        // Los marcadores de la serie también quedan calientes (caché en disco de 6 h):
        // el capítulo actual para arrancar al instante y los dos siguientes para la
        // maratón. Si la red tarda, el reproductor los pide por su cuenta.
        if (cleanSeries.isNotBlank()) {
            val len = (fallbackDurationMs / 1000L).toInt().coerceAtLeast(0)
            runCatching {
                withTimeoutOrNull(2_500L) {
                    com.chronosbvrx.visionplus.data.markers.MarkersStore.fetchForPlayer(
                        context = context,
                        ref = cleanSeries,
                        ep = epNumber.coerceAtLeast(1),
                        lengthSeconds = len,
                    )
                }
            }
            runCatching {
                withTimeoutOrNull(4_000L) {
                    com.chronosbvrx.visionplus.data.markers.MarkersStore.prefetch(
                        context = context,
                        ref = cleanSeries,
                        episodes = (epNumber + 1)..(epNumber + 2),
                        lengthSeconds = len,
                    )
                }
            }
        }
    }

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
            val prefetchCid = xtvPortalId(contentId)
            vodPrefetch.remove(prefetchCid)?.let {
                if (System.currentTimeMillis() - it.atMs < PREFETCH_TTL_MS) {
                    Log.i(TAG, "play adelantado desde la ficha: $prefetchCid (${it.media.durationMs} ms)")
                    return@guarded it.media
                }
            }
            // Auto-reparado del motor: si PlayerRecovery rotó la carpeta de trabajo tras
            // fallos silenciosos, renovar la sesión y forzar SLB/params frescas para que el
            // arranque limpio no herede nada viejo.
            val healed = PlayerRecovery.consumeGenChange(context) != null
            if (healed) {
                runCatching { renewSession(context, force = true) }
                // El motor legado Xuper, con una sesión rancia, rota contra el mismo portal y
                // tira la reproducción XTV: se desarma junto con el resto del estado del motor.
                runCatching { XuperEngine.disable(context) }
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "PLAYER_HEAL_APPLIED", "sesion+slb frescas",
                )
            }
            ensureEngineData(context, force = healed)
            val cid = xtvPortalId(contentId)
            val sid = xtvPortalId(seriesId)
            // La ficha editorial solo se pide si aún no sabemos la duración: en cajas
            // lentas cada llamada cuesta 0.2-0.7 s y el reproductor la recupera en
            // segundo plano para el rótulo. Con duración conocida, directo a la licencia.
            var item: JSONObject? = null
            if (fallbackDurationMs <= 0L) {
                // The editorial card carries the true running time; a flaky network gets a second try.
                item = itemInfo(context, cid)
                if (item == null) {
                    kotlinx.coroutines.delay(800)
                    item = itemInfo(context, cid)
                }
            }
            var durMs = extractDurationMs(item)
            // Los capítulos de serie no suelen traer duración propia: heredar de la ficha de la serie.
            if (durMs <= 0L && sid.isNotBlank()) {
                val seriesItem = itemInfo(context, sid)
                durMs = extractDurationMs(seriesItem)
            }
            if (durMs <= 0L) {
                val hist = PlaybackHistory.get(context, cid)
                if (hist != null && hist.durationMs > 60_000L) {
                    durMs = hist.durationMs
                }
            }
            // Si el portal no la reporta, aplicar la duración declarada en catálogo (Film.durationMin).
            if (durMs <= 0L && fallbackDurationMs > 0L) {
                durMs = fallbackDurationMs
            }
            var medias: JSONArray? = null
            for (type in listOf("2", "1")) {
                var d = runCatching {
                    callWithSession(context, "/api/portalCore/v10/startPlayVOD") { token ->
                        JSONObject().apply {
                            put("userToken", token)
                            put("userId", userIdOf())
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("seriesContentId", sid)
                            put("type", type)
                            put("startTime", 0)
                            put("authType", "0")
                            put("episodeNumberList", JSONArray().put(1))
                        }
                    }
                }.getOrNull()
                // Si la sesión guardada estaba desincronizada o el portal rechazó inicialmente,
                // renovar sesión explícitamente y reintentar de inmediato antes de descartar.
                if (d == null || collectMedias(d).length() == 0) {
                    d = runCatching {
                        // Token rechazado: mint forzado (salta el silencio de 30 s).
                        val freshToken = renewSession(context, force = true)
                        post("/api/portalCore/v10/startPlayVOD", JSONObject().apply {
                            put("userToken", freshToken)
                            put("userId", userIdOf())
                            put("portalCode", PORTAL)
                            put("contentId", cid)
                            put("seriesContentId", sid)
                            put("type", type)
                            put("startTime", 0)
                            put("authType", "0")
                            put("episodeNumberList", JSONArray().put(1))
                        })
                    }.getOrNull() ?: d
                }
                if (d != null) {
                    if (durMs <= 0L) {
                        durMs = extractDurationMs(d)
                        if (durMs <= 0L) {
                            durMs = extractDurationMs(d.optJSONArray("episodeList")?.optJSONObject(0))
                        }
                    }
                    val list = collectMedias(d)
                    if (list.length() > 0) {
                        medias = list
                        break
                    }
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
     * Extrae de forma robusta la duración en ms desde cualquier objeto JSON del portal
     * (assetData, startPlayVOD, detail, item, episodeList, etc.).
     */
    internal fun extractDurationMs(obj: JSONObject?): Long {
        if (obj == null) return 0L
        val subObjs = mutableListOf<JSONObject>()
        subObjs.add(obj)
        obj.optJSONObject("item")?.let { subObjs.add(it) }
        obj.optJSONObject("play")?.let { subObjs.add(it) }
        obj.optJSONObject("assetData")?.let { subObjs.add(it) }
        obj.optJSONObject("detail")?.let { subObjs.add(it) }
        obj.optJSONArray("episodeList")?.optJSONObject(0)?.let { subObjs.add(it) }
        obj.optJSONArray("simpleProgramList")?.optJSONObject(0)?.let { subObjs.add(it) }

        val stringFields = listOf("duration", "playLength", "totalTime", "runtime", "len", "length", "videoTime", "time")
        for (target in subObjs) {
            for (f in stringFields) {
                val v = target.optString(f, "").trim()
                if (v.isNotBlank()) {
                    val parsed = parseDurationMs(v)
                    if (parsed > 0L) return parsed
                }
            }
            val longFields = listOf("duration", "playLength", "totalTime", "runtime", "len", "length", "videoTime")
            for (f in longFields) {
                val v = target.optLong(f, 0L)
                if (v > 0L) {
                    val parsed = parseDurationMs(v.toString())
                    if (parsed > 0L) return parsed
                }
            }
        }
        return 0L
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
        episodeNames: List<String> = emptyList(),
        episodeNumbers: List<Int> = emptyList(),
        episodeIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonNames: List<String> = emptyList(),
        seasonIndex: Int = 0,
        resumeMs: Long = 0,
        posterUrl: String = "",
        fallbackDurationMs: Long = 0L,
    ): Boolean = withContext(Dispatchers.IO) {
        var resolvedEpIds = episodeIds
        var resolvedEpNames = episodeNames
        var resolvedEpNumbers = episodeNumbers
        var resolvedEpIndex = episodeIndex
        var cleanSeriesId = if (seriesId.isNotBlank()) xtvPortalId(seriesId) else ""
        if (cleanSeriesId.isNotBlank() &&
            (resolvedEpIds.isEmpty() || resolvedEpNumbers.size != resolvedEpIds.size)
        ) {
            val programs = episodeProgramsFor(context, cleanSeriesId)
            if (programs.isNotEmpty()) {
                if (resolvedEpIds.isEmpty()) {
                    resolvedEpIds = programs.map { it.contentId }
                    resolvedEpNames = programs.map { it.name }
                    val idx = resolvedEpIds.indexOf(xtvPortalId(contentId))
                    resolvedEpIndex = if (idx >= 0) idx else episodeIndex.coerceIn(0, resolvedEpIds.lastIndex)
                }
                if (resolvedEpNumbers.size != resolvedEpIds.size) {
                    val byCode = programs.associate { xtvPortalId(it.contentId) to it.number }
                    resolvedEpNumbers = resolvedEpIds.map { code -> byCode[xtvPortalId(code)] ?: 0 }
                }
            }
        }
        // Película con contexto de serie residual (1 capítulo / 1 temporada / seriesId
        // suelto): se degrada a película suelta para no pintar UI de serie.
        if (resolvedEpIds.size <= 1 && seasonIds.size <= 1) {
            cleanSeriesId = ""
            resolvedEpIds = emptyList()
            resolvedEpNames = emptyList()
            resolvedEpNumbers = emptyList()
            resolvedEpIndex = 0
        }
        var cid = xtvPortalId(contentId)
        if (cleanSeriesId.isNotBlank() && cid == cleanSeriesId && resolvedEpIds.isNotEmpty()) {
            cid = resolvedEpIds[resolvedEpIndex.coerceIn(0, resolvedEpIds.lastIndex)]
        }
        // Marcadores de salto: SOLO lo que ya está en caché (memoria/disco). El
        // reproductor pide el resto en segundo plano; el handoff no espera a la red.
        val isSeries = cleanSeriesId.isNotBlank() && (resolvedEpIds.size > 1 || seasonIds.size > 1)
        val markerRef = if (seriesId.isNotBlank()) seriesId else cleanSeriesId
        val markerEp = resolvedEpNumbers.getOrNull(resolvedEpIndex)?.takeIf { it > 0 }
            ?: (resolvedEpIndex + 1)
        val markerLengthSec = (fallbackDurationMs / 1000L).toInt().coerceAtLeast(0)
        val cachedMarkers = if (isSeries && markerRef.isNotBlank()) {
            runCatching {
                com.chronosbvrx.visionplus.data.markers.MarkersStore.cachedJson(
                    context = context,
                    ref = markerRef,
                    ep = markerEp,
                )
            }.getOrDefault("")
        } else {
            ""
        }
        var prep = prepareVod(context, cid, title, cleanSeriesId, fallbackDurationMs)
        if (prep == null) {
            kotlinx.coroutines.delay(400)
            prep = prepareVod(context, cid, title, cleanSeriesId, fallbackDurationMs)
        }
        if (prep == null) {
            return@withContext false
        }
        val finalDuration = if (prep.durationMs > 0L) prep.durationMs else fallbackDurationMs
        launch(
            context = context,
            mode = "vod",
            code = cid,
            title = title,
            mediasJson = prep.mediasJson,
            durationMs = finalDuration,
            subsLangs = prep.subsLangs,
            seriesId = if (isSeries) cleanSeriesId else "",
            episodeIds = if (isSeries) resolvedEpIds else emptyList(),
            episodeNames = if (isSeries) resolvedEpNames else emptyList(),
            episodeNumbers = if (isSeries) resolvedEpNumbers else emptyList(),
            episodeIndex = if (isSeries) resolvedEpIndex else 0,
            seasonIds = if (isSeries) seasonIds else emptyList(),
            seasonNames = if (isSeries) seasonNames else emptyList(),
            seasonIndex = if (isSeries) seasonIndex else 0,
            resumeMs = resumeMs,
            posterUrl = posterUrl,
            markersRef = if (isSeries) markerRef else "",
            markersEp = if (isSeries) markerEp else 0,
            markersLengthSec = if (isSeries) markerLengthSec else 0,
            markersJson = cachedMarkers,
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

    /**
     * Respaldo del selector/zapping en vivo cuando el proceso `:x4k` no tiene el
     * catálogo en caché (las prefs no cruzan procesos y una renovación las vacía):
     * reconstruye entradas mínimas desde los códigos que viajan en el intent para
     * que el selector nunca quede en "0 canales" ni el mando muerto. Puro.
     */
    internal fun liveStubsForCodes(codes: List<String>): JSONArray {
        val out = JSONArray()
        for (code in codes) {
            val clean = code.trim()
            if (clean.isEmpty()) continue
            out.put(JSONObject().apply {
                put("channelCode", clean)
                put("name", displayChannelName(clean, clean))
            })
        }
        return out
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

            // 1. Vía rápida O(1) en memoria (0 ms sin parsear los 2 MB de JSON de K_LIVE)
            var mediasJson = memoryLiveMedias[channelCode]

            // 2. Si no está indexado en RAM aún, cargar desde caché de preferencias e indexar una vez
            if (mediasJson.isNullOrBlank()) {
                val arr = cachedLive(context)
                mediasJson = memoryLiveMedias[channelCode]
                    ?: extractLiveMedias(arr, channelCode)?.toString()?.also { memoryLiveMedias[channelCode] = it }
            }

            // 3. Si el canal no está en caché o la caché está desactualizada, forzar actualización
            if (mediasJson.isNullOrBlank()) {
                liveRow(context, force = true)
                val arr = (guarded { JSONArray(prefs(context).getString(K_LIVE, "[]")) } ?: JSONArray()).also { indexLiveArray(it) }
                mediasJson = memoryLiveMedias[channelCode]
                    ?: extractLiveMedias(arr, channelCode)?.toString()
            }

            val m = mediasJson?.takeIf { it.isNotBlank() } ?: return@guarded false
            launch(
                context = context,
                mode = "live",
                code = channelCode,
                title = title,
                mediasJson = m,
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

    private suspend fun launch(
        context: Context,
        mode: String,
        code: String,
        title: String,
        mediasJson: String,
        durationMs: Long = 0L,
        subsLangs: String = "",
        seriesId: String = "",
        episodeIds: List<String> = emptyList(),
        episodeNames: List<String> = emptyList(),
        episodeNumbers: List<Int> = emptyList(),
        episodeIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonNames: List<String> = emptyList(),
        seasonIndex: Int = 0,
        resumeMs: Long = 0,
        posterUrl: String = "",
        channelCodes: List<String> = emptyList(),
        groupTitle: String = "",
        markersRef: String = "",
        markersEp: Int = 0,
        markersLengthSec: Int = 0,
        markersJson: String = "",
    ) {
        val p = prefs(context)
        var entries = p.getString(K_ENTRIES, "").orEmpty()
        var params = p.getString(K_PARAMS, "").orEmpty()
        // Una renovación a media preparación borra entries/params; sin SLB el motor no
        // resuelve el CDN y el play falla. Se piden de nuevo antes de abrir el reproductor.
        if (entries.isBlank() || params.isBlank()) {
            runCatching { ensureEngineData(context, deferPrewarm = true) }
            entries = p.getString(K_ENTRIES, "").orEmpty()
            params = p.getString(K_PARAMS, "").orEmpty()
        }
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
            if (seriesId.isNotBlank() && (episodeIds.size > 1 || seasonIds.size > 1)) {
                putExtra("seriesId", seriesId)
                putStringArrayListExtra("epIds", ArrayList(episodeIds))
                putStringArrayListExtra("epNames", ArrayList(episodeNames))
                if (episodeNumbers.isNotEmpty()) {
                    putIntegerArrayListExtra("epNumbers", ArrayList(episodeNumbers))
                }
                putExtra("epIndex", episodeIndex)
                putStringArrayListExtra("seasonIds", ArrayList(seasonIds))
                putStringArrayListExtra("seasonNames", ArrayList(seasonNames))
                putExtra("seasonIndex", seasonIndex)
            }
            if (resumeMs > 0) putExtra("resumeMs", resumeMs)
            if (posterUrl.isNotBlank()) putExtra("posterUrl", posterUrl)
            if (markersRef.isNotBlank()) putExtra("markersRef", markersRef)
            if (markersEp > 0) putExtra("markersEp", markersEp)
            if (markersLengthSec > 0) putExtra("markersLengthSec", markersLengthSec)
            if (markersJson.isNotBlank()) putExtra("markersJson", markersJson)
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
