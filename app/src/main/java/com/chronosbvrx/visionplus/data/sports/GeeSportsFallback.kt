package com.chronosbvrx.visionplus.data.sports

import android.content.Context
import android.util.Base64
import android.util.Log
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Integración de respaldo (fallback) con el catálogo de canales deportivos en vivo
 * de Gee Sports (`com.geesports.live` -> `https://absuehhd.online/` + Firebase Remote Config).
 *
 * Se utiliza únicamente como **fallback secundario** después de los canales nativos de Vision+ (XTV),
 * aportando señales directas HLS (`.m3u8` con cabeceras `Origin`/`Referer`/`User-Agent`) y
 * DASH (`.mpd` con DRM ClearKey `kid:key`) para ESPN, ESPN 2-7, ESPN Deportes, Sky Sports LaLiga,
 * DAZN LaLiga, Movistar LaLiga, Movistar Liga de Campeones, TNT Sports, beIN Sports, TyC Sports, etc.
 */
object GeeSportsFallback {

    private const val TAG = "SportsOptions"
    private const val PREFS = "sports_options_cache_v2"
    private const val KEY_API_URL = "api_url"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L // 4 horas

    private const val DEFAULT_API_URL = "https://absuehhd.online/"
    private const val FIREBASE_RC_URL =
        "https://firebaseremoteconfig.googleapis.com/v1/projects/54982471369/namespaces/firebase:fetch?key=AIzaSyDJHkEjrqKsQCbvN_dHvFqGQA5T-ZD7Luo"
    private const val FIREBASE_APP_ID = "1:54982471369:android:4ffe4d01762eca4b54a718"
    private const val FIREBASE_PKG = "com.geesports.live"

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val slugCache = ConcurrentHashMap<String, List<LiveChannel>>()

    @Volatile
    private var lastRefreshMs = 0L

    data class ParsedStream(
        val url: String,
        val userAgent: String?,
        val referrer: String?,
        val origin: String?,
    )

    /**
     * Separa un enlace con cabeceras embebidas tras `|` (formato Gee Sports:
     * `https://.../index.m3u8?|origin=https://...&referer=https://...&user-agent=...`).
     */
    fun parseStreamUrl(rawLink: String): ParsedStream? {
        val trimmed = rawLink.trim()
        if (trimmed.isBlank()) return null
        val pipeIdx = trimmed.indexOf('|')
        var base = (if (pipeIdx >= 0) trimmed.substring(0, pipeIdx) else trimmed).trim()
        while (base.endsWith("?")) {
            base = base.removeSuffix("?").trim()
        }
        if (base.isBlank()) return null
        if (!base.startsWith("http://", ignoreCase = true) && !base.startsWith("https://", ignoreCase = true)) {
            base = "https://$base"
        }

        var ua: String? = null
        var ref: String? = null
        var orig: String? = null

        if (pipeIdx >= 0 && pipeIdx + 1 < trimmed.length) {
            val headerPart = trimmed.substring(pipeIdx + 1)
            for (pair in headerPart.split('&')) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val key = pair.substring(0, eq).trim().lowercase(Locale.ROOT)
                val rawVal = pair.substring(eq + 1).trim()
                if (rawVal.isBlank()) continue
                val value = runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal)
                when (key) {
                    "user-agent", "useragent" -> ua = value
                    "referer", "referrer" -> ref = value
                    "origin" -> orig = value
                }
            }
        }
        return ParsedStream(
            url = base,
            userAgent = ua ?: DEFAULT_UA,
            referrer = ref,
            origin = orig,
        )
    }

    /**
     * Convierte un par ClearKey hexadecimal `"kid_hex:key_hex"` al JSON estándar W3C ClearKey
     * (`{"keys":[{"kty":"oct","k":"<base64url>","kid":"<base64url>"}],"type":"temporary"}`)
     * tal cual lo construye `PlayerActivity` en `gee-sports.apk`.
     */
    fun buildClearKeyJson(apiKeyHex: String): String? {
        val clean = apiKeyHex.trim()
        if (!clean.contains(':')) return null
        val parts = clean.split(':')
        if (parts.size < 2) return null
        val kidBytes = hexToBytes(parts[0].trim()) ?: return null
        val keyBytes = hexToBytes(parts[1].trim()) ?: return null
        if (kidBytes.isEmpty() || keyBytes.isEmpty()) return null
        val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val kidB64 = Base64.encodeToString(kidBytes, flags)
        val keyB64 = Base64.encodeToString(keyBytes, flags)
        return """{"keys":[{"kty":"oct","k":"$keyB64","kid":"$kidB64"}],"type":"temporary"}"""
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val s = hex.trim()
        if (s.length % 2 != 0 || s.isEmpty()) return null
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(s[i * 2], 16)
            val lo = Character.digit(s[i * 2 + 1], 16)
            if (hi == -1 || lo == -1) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun isGeeSportsChannel(channel: LiveChannel): Boolean =
        channel.group == GROUP_TAG || !channel.url.startsWith("xtvlive:")

    const val GROUP_TAG = "Deportes · Alternativo"

    private fun buildChannel(
        name: String,
        rawLink: String,
        type: String = "0",
        api: String = "",
    ): LiveChannel? {
        val parsed = parseStreamUrl(rawLink) ?: return null
        val isDash = type == "3" || parsed.url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
        val drmKey = if (isDash && api.contains(':')) api.trim() else null
        return LiveChannel(
            name = name,
            url = parsed.url,
            logo = null,
            group = GROUP_TAG,
            userAgent = parsed.userAgent,
            referrer = parsed.referrer,
            origin = parsed.origin,
            drmKey = drmKey,
            drmType = if (isDash) "dash" else "hls",
        )
    }

    /**
     * Semillas curadas extraídas directamente de los paquetes de `gee-sports.apk` (`absuehhd.online`),
     * disponibles en 0 ms desde el arranque antes de que termine cualquier petición de red.
     */
    private val SEED_BY_SLUG: Map<String, List<LiveChannel>> by lazy {
        mapOf(
            "espn" to listOfNotNull(
                buildChannel("ESPN MX HD", "http://redima.pro:8880/live/VALLE1402/VALLE1402/1003.m3u8"),
                buildChannel("ESPN HD", "http://170.82.37.21:8000/play/a00y/index.m3u8"),
                buildChannel("ESPN Deportes HD", "https://webhdrus.onlinehdhls.ru/lb/premium375/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("ESPN USA HD", "https://webhdrus.onlinehdhls.ru/lb/premium44/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel(
                    "ESPN Sur HD",
                    "https://chromecast.cvattv.com.ar/live/c3eds/ESPN2HD/SA_Live_dash_enc_2A/ESPN2HD.mpd",
                    type = "3",
                    api = "e884b711ab111beb8a7ba1e7bcbdc9bf:cb89ee3961599e3e648a5aad60895f34",
                ),
            ),
            "espn2" to listOfNotNull(
                buildChannel("ESPN 2 HD", "http://170.82.37.21:8000/play/a00z/index.m3u8"),
                buildChannel("ESPN 2 USA HD", "https://webhdrus.onlinehdhls.ru/lb/premium45/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("ESPN 2 Sur HD", "http://redima.pro:8880/live/VALLE1402/VALLE1402/312.m3u8"),
            ),
            "espn3" to listOfNotNull(
                buildChannel("ESPN 3 HD", "http://170.82.37.21:8000/play/a010/index.m3u8"),
                buildChannel("ESPN 3 Sur HD", "http://redima.pro:8880/live/VALLE1402/VALLE1402/328.m3u8"),
            ),
            "espn4" to listOfNotNull(
                buildChannel("ESPN 4 HD", "http://170.82.37.21:8000/play/a011/index.m3u8"),
                buildChannel("ESPN 4 Sur HD", "http://redima.pro:8880/live/VALLE1402/VALLE1402/574.m3u8"),
            ),
            "espn_premium" to listOfNotNull(
                buildChannel("ESPN Premium HD", "https://webhdrus.onlinehdhls.ru/lb/premium387/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("ESPN+ HD", "http://170.82.37.21:8000/play/a04g/index.m3u8"),
            ),
            "laliga" to listOfNotNull(
                buildChannel(
                    "DAZN LaLiga HD",
                    "https://live.ll.ww.aiv-cdn.net/OTTB/dub-nitro/live/clients/dash/enc/wjgklbtvhh/out/v1/659736a1e24c40e4865a80ffd75e7de7/cenc.mpd",
                    type = "3",
                    api = "43d1c3b25207ff38b22ccfe17d302367:7b1f85f6e81059473b114c16a25c829a",
                ),
                buildChannel("Sky Sports LaLiga HD", "http://181.78.105.146:2000/play/a08m/index.m3u8"),
                buildChannel("Movistar LaLiga HD", "https://webhdrus.onlinehdhls.ru/lb/premium84/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("DAZN LaLiga 2 HD", "https://webhdrus.onlinehdhls.ru/lb/premium538/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("LaLiga TV HD", "https://cdn2.aluku91.online/uk-laligatv.m3u8|Referer=https://tiksports.net/"),
            ),
            "movistar" to listOfNotNull(
                buildChannel("Movistar LaLiga HD", "https://webhdrus.onlinehdhls.ru/lb/premium84/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("Movistar Liga de Campeones HD", "https://webhdrus.onlinehdhls.ru/lb/premium435/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
            ),
            "champions" to listOfNotNull(
                buildChannel("Movistar Liga de Campeones HD", "https://webhdrus.onlinehdhls.ru/lb/premium435/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("TNT Sports 1 HD", "https://webhdrus.onlinehdhls.ru/lb/premium31/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("Paramount+ Champions HD", "https://cdn2.aluku91.online/line4.m3u8|Referer=https://tiksports.net/"),
                buildChannel("CBS Sports Champions HD", "https://cdn2.aluku91.online/usa-cbssport.m3u8|Referer=https://tiksports.net/"),
            ),
            "dazn" to listOfNotNull(
                buildChannel(
                    "DAZN 1 ES HD",
                    "https://ottb.live.cf.ww.aiv-cdn.net/dub-nitro/live/clients/dash/enc/bmnelo5c7a/out/v1/3ce2cdc4589f46189322bd3717c77957/cenc.mpd",
                    type = "3",
                    api = "44dd9cd370b08a868ead115fe84ecfde:bff19ab0a51cf14e848389b152913fd0",
                ),
                buildChannel(
                    "DAZN 2 ES HD",
                    "https://ottb.live.cf.ww.aiv-cdn.net/dub-nitro/live/clients/dash/enc/xnk4m9bnxt/out/v1/4ced7b7329a54652b9bb0521ed38bd4d/cenc.mpd",
                    type = "3",
                    api = "0eab5a3f3e3b4ba5d42d40ca30d17571:f3f061ded9b70e8160590d5802ecda6d",
                ),
                buildChannel("DAZN 1 HD", "https://webhdrus.onlinehdhls.ru/lb/premium445/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
            ),
            "tnt" to listOfNotNull(
                buildChannel("TNT Sports HD", "https://webhdrus.onlinehdhls.ru/lb/premium388/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("TNT Sports Chile HD", "https://webhdrus.onlinehdhls.ru/lb/premium642/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("TNT Sports 1 UK HD", "https://webhdrus.onlinehdhls.ru/lb/premium31/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
            ),
            "fox" to listOfNotNull(
                buildChannel(
                    "Fox Sports HD",
                    "https://chromecast.cvattv.com.ar/live/c3eds/FoxSports/SA_Live_dash_enc_2A/FoxSports.mpd",
                    type = "3",
                    api = "1478b566307e53f2f3f4a19949956790:8a045f83e1f2d986811234d01944c506",
                ),
                buildChannel("Fox Sports 3 HD", "http://170.82.37.21:8000/play/a014/index.m3u8"),
            ),
            "tudn" to listOfNotNull(
                buildChannel("TUDN HD", "https://webhdrunns.onlinehdhls.ru/lb/premium66/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("TUDN USA HD", "https://cdn2.aluku91.online/usa-tudn.m3u8|Referer=https://tiksports.net/"),
            ),
            "telemundo" to listOfNotNull(
                buildChannel("Telemundo Deportes HD", "https://webhdrus.onlinehdhls.ru/lb/premium131/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
            ),
            "premier" to listOfNotNull(
                buildChannel("Sky Sports Premier League HD", "https://webhdrus.onlinehdhls.ru/lb/premium130/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("USA Network Premier HD", "https://cdn2.aluku91.online/usa-usanetwork.m3u8|Referer=https://tiksports.net/"),
            ),
            "bundesliga" to listOfNotNull(
                buildChannel("Sky Sport Bundesliga 1 HD", "https://webhdrus.onlinehdhls.ru/lb/premium558/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("Bundesliga TV HD", "https://cdn2.aluku91.online/de-bundesliga1.m3u8|Referer=https://tiksports.net/"),
            ),
            "bein" to listOfNotNull(
                buildChannel("beIN SPORTS en Español HD", "https://webhdrus.onlinehdhls.ru/lb/premium372/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
                buildChannel("beIN SPORTS USA HD", "https://webhdrus.onlinehdhls.ru/lb/premium425/index.m3u8?|origin=https://claplivehdplay.ru&referer=https://claplivehdplay.ru/"),
            ),
            "tyc" to listOfNotNull(
                buildChannel("TyC Sports HD", "https://cdn2.aluku91.online/usa-tycsport.m3u8|Referer=https://tiksports.net/"),
            ),
        )
    }

    /**
     * Devuelve los canales de respaldo (Gee Sports) para las cadenas y la liga de un partido.
     * Funciona en 0 ms usando la caché en memoria + semillas curadas.
     */
    fun fallbackFor(
        queryNames: List<String>,
        leagueId: String? = null,
    ): List<LiveChannel> {
        val keys = LinkedHashSet<String>()
        for (raw in queryNames) {
            val info = SportsLinker.extractBrandInfo(raw)
            when (info.brand) {
                "espn" -> {
                    when (info.edition) {
                        "2" -> keys.add("espn2")
                        "3" -> keys.add("espn3")
                        "4" -> keys.add("espn4")
                        "premium", "extra" -> keys.add("espn_premium")
                        else -> {
                            keys.add("espn")
                            keys.add("espn2")
                        }
                    }
                }
                "sky sports" -> {
                    if (leagueId == "ger.1") keys.add("bundesliga") else keys.add("laliga")
                }
                "movistar futbol" -> {
                    keys.add("laliga")
                    keys.add("movistar")
                }
                "liga de campeones" -> keys.add("champions")
                "dazn" -> {
                    if (leagueId == "esp.1") keys.add("laliga")
                    keys.add("dazn")
                }
                "tnt sports" -> keys.add("tnt")
                "fox sports" -> keys.add("fox")
                "tudn" -> keys.add("tudn")
                "telemundo", "universo" -> keys.add("telemundo")
                "bein sports" -> keys.add("bein")
                "tyc sports" -> keys.add("tyc")
            }
        }

        when (leagueId) {
            "esp.1" -> {
                keys.add("laliga")
                keys.add("movistar")
                keys.add("dazn")
            }
            "uefa.champions" -> {
                keys.add("champions")
                keys.add("tnt")
                keys.add("espn")
            }
            "eng.1" -> {
                keys.add("premier")
                keys.add("tnt")
                keys.add("espn")
            }
            "ita.1", "uefa.europa", "conmebol.libertadores", "conmebol.sudamericana", "por.1", "bra.1" -> {
                keys.add("espn")
                keys.add("espn2")
            }
            "ger.1" -> {
                keys.add("bundesliga")
                keys.add("espn")
            }
            "fra.1" -> {
                keys.add("bein")
                keys.add("espn")
            }
            "arg.1" -> {
                keys.add("tyc")
                keys.add("espn_premium")
                keys.add("tnt")
            }
        }

        val out = LinkedHashMap<String, LiveChannel>()
        for (key in keys) {
            val dynamic = slugCache[key]
            if (!dynamic.isNullOrEmpty()) {
                for (ch in dynamic) out.putIfAbsent(ch.url, ch)
            }
            val seeds = SEED_BY_SLUG[key].orEmpty()
            for (ch in seeds) out.putIfAbsent(ch.url, ch)
        }
        return out.values.toList()
    }

    /**
     * Sincroniza en segundo plano los paquetes JSON en Base64 de `gee-sports.apk`
     * (`absuehhd.online`, con descubrimiento vía Firebase Remote Config si el dominio rota).
     */
    suspend fun refreshIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < CACHE_TTL_MS && slugCache.isNotEmpty()) return@withContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (slugCache.isEmpty() && now - savedAt < CACHE_TTL_MS) {
            loadFromPrefs(prefs)
            if (slugCache.isNotEmpty()) {
                lastRefreshMs = now
                return@withContext
            }
        }

        var baseUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        if (!baseUrl.endsWith("/")) baseUrl += "/"

        val remoteMapping = mapOf(
            "espn" to "espn",
            "espn-hd" to "espn",
            "22" to "laliga",
            "dazn-sporthd" to "dazn",
            "movistarhd" to "movistar",
            "tnt-sporthd" to "tnt",
            "tudn" to "tudn",
            "bein-sportshd" to "bein",
        )

        var anySuccess = false
        for ((remoteSlug, localKey) in remoteMapping) {
            val fetched = runCatching { fetchChannelBundle(baseUrl, remoteSlug) }.getOrNull()
            if (!fetched.isNullOrEmpty()) {
                anySuccess = true
                val existing = slugCache[localKey].orEmpty()
                val merged = (fetched + existing).distinctBy { it.url }.take(6)
                slugCache[localKey] = merged
            }
        }

        // Si el dominio base cambió, consultar Firebase Remote Config de gee-sports.apk
        if (!anySuccess) {
            val discovered = runCatching { fetchFirebaseRemoteConfigUrl() }.getOrNull()
            if (!discovered.isNullOrBlank() && discovered != baseUrl) {
                baseUrl = if (discovered.endsWith("/")) discovered else "$discovered/"
                prefs.edit().putString(KEY_API_URL, baseUrl).apply()
                for ((remoteSlug, localKey) in remoteMapping) {
                    val fetched = runCatching { fetchChannelBundle(baseUrl, remoteSlug) }.getOrNull()
                    if (!fetched.isNullOrEmpty()) {
                        anySuccess = true
                        val existing = slugCache[localKey].orEmpty()
                        slugCache[localKey] = (fetched + existing).distinctBy { it.url }.take(6)
                    }
                }
            }
        }

        if (anySuccess) {
            lastRefreshMs = now
            saveToPrefs(prefs, now)
        }
    }

    private fun fetchChannelBundle(baseUrl: String, slug: String): List<LiveChannel> {
        val url = "${baseUrl}channels/$slug.json"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", DEFAULT_UA)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) return emptyList()
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val wrapper = JSONObject(text)
            val b64 = wrapper.optString("data")
            if (b64.isBlank()) return emptyList()
            val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val json = JSONObject(decoded)
            val arr = json.optJSONArray("streamUrls") ?: return emptyList()
            val out = ArrayList<LiveChannel>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val link = item.optString("link").trim()
                val type = item.optString("type", "0").trim()
                val api = item.optString("api", "").trim()
                if (title.isBlank() || link.isBlank()) continue
                val cleanTitle = title.replace("(Alt)", "", ignoreCase = true).trim()
                buildChannel(cleanTitle, link, type, api)?.let { out.add(it) }
                if (out.size >= 4) break
            }
            out
        } catch (e: Exception) {
            Log.d(TAG, "Bundle $slug no disponible: ${e.message}")
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchFirebaseRemoteConfigUrl(): String? {
        val conn = (URL(FIREBASE_RC_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            val payload = JSONObject()
                .put("appId", FIREBASE_APP_ID)
                .put("appInstanceId", "PROD_VISION")
                .put("packageName", FIREBASE_PKG)
                .put("sdkVersion", "21.6.0")
                .toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val entries = JSONObject(text).optJSONObject("entries") ?: return null
            entries.optString("api_url").takeIf { it.startsWith("http") }
        } finally {
            conn.disconnect()
        }
    }

    private fun saveToPrefs(prefs: android.content.SharedPreferences, now: Long) {
        val root = JSONObject()
        for ((k, list) in slugCache) {
            val arr = JSONArray()
            for (ch in list) {
                arr.put(
                    JSONObject()
                        .put("name", ch.name)
                        .put("url", ch.url)
                        .put("ua", ch.userAgent ?: "")
                        .put("ref", ch.referrer ?: "")
                        .put("org", ch.origin ?: "")
                        .put("drmKey", ch.drmKey ?: "")
                        .put("drmType", ch.drmType ?: "hls"),
                )
            }
            root.put(k, arr)
        }
        prefs.edit()
            .putString("bundles", root.toString())
            .putLong(KEY_FETCHED_AT, now)
            .apply()
    }

    private fun loadFromPrefs(prefs: android.content.SharedPreferences) {
        val raw = prefs.getString("bundles", null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = root.optJSONArray(k) ?: continue
                val list = ArrayList<LiveChannel>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(
                        LiveChannel(
                            name = o.optString("name"),
                            url = o.optString("url"),
                            logo = null,
                            group = GROUP_TAG,
                            userAgent = o.optString("ua").takeIf { it.isNotBlank() },
                            referrer = o.optString("ref").takeIf { it.isNotBlank() },
                            origin = o.optString("org").takeIf { it.isNotBlank() },
                            drmKey = o.optString("drmKey").takeIf { it.isNotBlank() },
                            drmType = o.optString("drmType").takeIf { it.isNotBlank() },
                        ),
                    )
                }
                if (list.isNotEmpty()) slugCache[k] = list
            }
        }
    }
}
