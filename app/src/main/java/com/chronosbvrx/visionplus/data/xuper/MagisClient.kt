package com.chronosbvrx.visionplus.data.xuper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.PLUGIN_PACKAGE
import com.chronosbvrx.visionplus.data.normalizeDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The Magis portal client (com.msandroid.mobile, portalCode "masmobile") — the second provider
 * behind En vivo. Verified end-to-end against the live portal (2026-10-07):
 *
 *  - wire body  = hex( base64( DESede/ECB/PKCS5(json) ) ) with the communication key f251a,
 *                 derived from "37a759c3-fc2b-413d-b424-0bb0e1505a5e" with the app's custom
 *                 base64 (y7a) and cut to 24 bytes;
 *  - response   = plain JSON whose `data` field decrypts with the same key;
 *  - catalog    = v3/getColumnContents (the column tree) then v5/getLiveData per column
 *                 (channelCode, name, poster, and per-CDN `license` tokens for playback);
 *  - slb        = v15/getSlbInfo (CDN list + P2P play params) for the player;
 *  - headers    = apk / apkVer / spkgVer, Content-Type application/json;charset=utf-8;
 *  - hosts      = the CF-fronted pair that answers these versions.
 *
 * The config carries the session captured from a working install (userToken, sn, deviceToken and
 * the device fields the portal binds the token to). When that token rotates, the portal re-issues
 * one for the BOUND DEVICE via v3/snToken + v8/active — no password involved (the account is
 * Google-bound and has no password). v8/login stays as a fallback when credentials are carried.
 */
class MagisClient(context: Context) {

    companion object {
        private const val TAG = "MagisClient"

        /** The device MAC the app announces (its randomized 02:00:00:00:00:00). */
        private const val MAC = "02:00:00:00:00:00"

        /** The wire key: y7a_decode("37a759c3-fc2b-413d-b424-0bb0e1505a5e")[:24] (a8.b.f251a). */
        private val KEY = byteArrayOf(
            0xdf.toByte(), 0xb6.toByte(), 0xbb.toByte(), 0xe7.toByte(),
            0xd7.toByte(), 0x37.toByte(), 0xfd.toByte(), 0xf7.toByte(),
            0x36.toByte(), 0x6f.toByte(), 0xfe.toByte(), 0x35.toByte(),
            0xdd.toByte(), 0xdf.toByte(), 0xdb.toByte(), 0xe3.toByte(),
            0x6e.toByte(), 0x3f.toByte(), 0xd1.toByte(), 0xb6.toByte(),
            0xf4.toByte(), 0x7b.toByte(), 0x5e.toByte(), 0x74.toByte(),
        )

        /** Portal hosts that serve v5/getLiveData and v15/getSlbInfo. */
        private val HOSTS = listOf("rhokqi.ryh1gifvn.com", "zgmnxa.ryh1gifvn.com")

        /** Live section of the column tree: root -> Live -> ChannelList / Vivo gratis / Deportes. */
        private const val LIVE_COLUMN_FALLBACK = 77040
        private val WANTED_COLUMNS = listOf(
            "ChannelList" to 77052,
            "Vivo gratis" to 87035,
            "Deportes" to 77056,
        )

        private const val PREFS_KEY = "magis_config"
        private const val KEY_ROWS = "magis_rows"
        private const val KEY_ROWS_AT = "magis_rows_at"
        private const val KEY_MEDIA = "magis_media"
        private const val ROWS_TTL_MS = 5 * 60_000L

        /** Serializes session renewals: two screens activating at once evict each other. */
        private val sessionLock = Any()

        @Volatile
        private var lastSessionAt = 0L

        private const val SESSION_COOLDOWN_MS = 20_000L

        /** Home pages whose rows make up the VOD catalog (movies / series / kids / anime). */
        private val HOME_PAGES = listOf(
            "masmobile_Recommended",
            "masmobile_movies",
            "masmobile_series",
            "masmobile_kids",
            "masmobile_animes",
        )
    }

    /** The captured session, editable through [config]; defaults are the working capture. */
    @Serializable
    data class Config(
        val userName: String = "",
        val password: String = "",
        val userToken: String = "70cbb930-f6fc-43e0-b4bf-443f9cba8205",
        val userId: String = "966826674",
        val sn: String = "f6be8b3feafdfb9e1c08d5f50c0c2c8f",
        val snToken: String = "02a94dd4-c547-4a5c-99eb-d576367c6d6a",
        val channel: String = "default",
        val deviceToken: String = "cTzYag_MRJKTGK69Rx1kVH",
        val apkVersion: String = "60507",
        val appId: String = "com.msandroid.mobile",
        val appLanguage: String = "es",
        val sysVersion: String = "13",
        val hardwareInfo: String = "mt6769t",
        val model: String = "SM-A325M",
        val product: String = "a32ub",
        val cpu: String = "arm64-v8a",
        val reserve1: String = "000000000000",
        val portalCode: String = "masmobile",
        val host: String = "",
    )

    private val prefs: SharedPreferences = SecretKeystore.get(context)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    var config: Config
        get() = try {
            json.decodeFromString(prefs.getString(PREFS_KEY, "{}") ?: "{}")
        } catch (_: Exception) {
            Config()
        }
        set(value) {
            prefs.edit().putString(PREFS_KEY, json.encodeToString(value)).apply()
        }

    /** The En vivo catalog: the wanted live sub-columns, one group per column. */
    suspend fun catalogChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        try {
            fetchCatalog()
        } catch (e: IOException) {
            // A rotated/evicted session answers aaa*: the bound device re-issues one (shared
            // across screens: whoever renews first serves everybody).
            if (e.message?.contains("portal aaa") != true) throw e
            renewSession(e)
            fetchCatalog()
        }
    }

    private fun fetchCatalog(): List<LiveChannel> {
        val subs = try {
            getColumns(liveColumnId())
        } catch (e: IOException) {
            if (sessionError(e)) throw e
            emptyList()
        }
        val seen = HashSet<String>()
        val out = ArrayList<LiveChannel>()
        val mediaMap = JSONObject()
        var failures = 0
        for ((name, fallback) in WANTED_COLUMNS) {
            val column = subs.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val id = column?.id ?: fallback
            val label = column?.name ?: name
            val data = try {
                getLiveData(id)
            } catch (e: IOException) {
                if (sessionError(e)) throw e
                failures++
                null
            } ?: continue
            for (ch in data.channels) {
                if (!seen.add(ch.channelCode)) continue
                out += LiveChannel(
                    name = ch.name.ifBlank { "Canal ${ch.channelNumber}" },
                    url = refOf(ch),
                    logo = ch.posterUrl.takeIf { it.isNotBlank() },
                    group = label,
                )
                if (mediaMap.optJSONArray(ch.channelCode) == null) {
                    val addresses = JSONArray()
                    for (a in ch.liveAddresses) {
                        // Every address the portal handed out: the app plays them all.
                        if (a.license.isBlank() || a.playCode.isBlank()) continue
                        addresses.put(
                            JSONObject()
                                .put("name", a.playCode)
                                .put("license", a.license)
                                .put("quality", a.quality)
                                .put("format", a.avFormat),
                        )
                    }
                    if (addresses.length() > 0) mediaMap.put(ch.channelCode, addresses)
                }
            }
        }
        if (mediaMap.length() > 0) prefs.edit().putString(KEY_MEDIA, mediaMap.toString()).apply()
        if (out.isEmpty() && failures == WANTED_COLUMNS.size) {
            throw IOException("el portal Magis no respondió")
        }
        return out
    }

    /** A dead/evicted session answers 'portal aaa*': it must bubble up to [renewSession]. */
    private fun sessionError(e: IOException): Boolean = e.message?.contains("portal aaa") == true

    /** POST /api/portalCore/v3/snToken — registers this device and answers its snToken. */
    fun snToken(): String {
        val data = post("/api/portalCore/v3/snToken", JSONObject().apply {
            put("channel", config.channel)
            put("macAddr", MAC)
            put("snToken", "")
        })
        val token = data.optString("snToken")
        if (token.isBlank()) throw IOException("snToken vacío")
        config = config.copy(snToken = token)
        return token
    }

    /**
     * POST /api/portalCore/v8/active — the bound device's own door: with the snToken it minted,
     * the portal re-issues the account session (no password: the account is Google-bound).
     */
    fun activate(): Config {
        val token = config.snToken.ifBlank { snToken() }
        val data = post("/api/portalCore/v8/active", JSONObject().apply {
            put("channel", config.channel)
            put("macAddr", MAC)
            put("snToken", token)
        })
        val fresh = data.optString("userToken")
        if (fresh.isBlank()) throw IOException("activación sin userToken")
        val cfg = config.copy(
            userToken = fresh,
            userId = data.optString("userId").ifBlank { config.userId },
            snToken = token,
        )
        config = cfg
        Log.i(TAG, "dispositivo reactivado para ${cfg.userId}")
        return cfg
    }

    /**
     * Renews the session for the bound device, at most once per cooldown window; concurrent
     * callers (Home and En vivo loading at once) share the result instead of evicting each other.
     */
    private fun renewSession(original: Exception): Config = synchronized(sessionLock) {
        val now = System.currentTimeMillis()
        if (now - lastSessionAt < SESSION_COOLDOWN_MS) return config
        Log.i(TAG, "sesión vencida; reactivando dispositivo (v8/active)")
        if (runCatching { activate() }.isSuccess) {
            lastSessionAt = now
            return config
        }
        if (config.userName.isNotBlank() && config.password.isNotBlank() &&
            runCatching { login() }.isSuccess
        ) {
            lastSessionAt = now
            return config
        }
        throw original
    }

    /** POST /api/portalCore/v8/login with the carried account; saves the fresh session. */
    fun login(userName: String = config.userName, password: String = config.password): Config {
        if (userName.isBlank() || password.isBlank()) throw IOException("faltan las credenciales de Magis")
        val spec = JSONObject().apply {
            put("accountType", "2")
            put("areaCode", "")
            put("macAddr", MAC)
            put("password", password)
            put("userName", userName)
            put("verificationCode", "")
        }
        val data = post("/api/portalCore/v8/login", spec)
        val token = data.optString("userToken")
        if (token.isBlank()) throw IOException("login sin userToken")
        val fresh = config.copy(
            userName = userName,
            password = password,
            userToken = token,
            userId = data.optString("userId").ifBlank { config.userId },
        )
        config = fresh
        Log.i(TAG, "sesión renovada para ${fresh.userId}")
        return fresh
    }

    /** Root -> the live column (masmobile_live) or its known id. */
    private fun liveColumnId(): Int =
        runCatching { getColumns(null).firstOrNull { it.code == "masmobile_live" }?.id }
            .getOrNull() ?: LIVE_COLUMN_FALLBACK

    /** POST /api/portalCore/v3/getColumnContents — the children of [parentId] (null = root). */
    fun getColumns(parentId: Int?): List<MagisColumn> {
        val spec = JSONObject().apply {
            put("userToken", config.userToken)
            put("userId", config.userId)
            put("portalCode", config.portalCode)
            if (parentId != null) put("columnId", parentId)
            put("pageNum", 1)
            put("pageSize", 1000)
        }
        val data = post("/api/portalCore/v3/getColumnContents", spec)
        val arr = data.optJSONArray("childColumnList") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                MagisColumn(
                    id = o.optInt("id"),
                    code = o.optString("code"),
                    name = o.optString("name"),
                    parentId = o.optInt("parentId"),
                    type = o.optString("type"),
                )
            }
        }
    }

    /** POST /api/portalCore/v5/getLiveData — the channels of one column. */
    fun getLiveData(columnId: Int, dataVersion: String = "", expireTimeStr: String = ""): MagisLiveData {
        val spec = JSONObject().apply {
            put("userToken", config.userToken)
            put("userId", config.userId)
            put("portalCode", config.portalCode)
            put("columnId", columnId)
            put("dataVersion", dataVersion)
            put("expireTimeStr", expireTimeStr)
            put("pageNum", 1)
            put("pageSize", 3000)
        }
        val data = post("/api/portalCore/v5/getLiveData", spec)
        val arr = data.optJSONArray("channelList") ?: JSONArray()
        val channels = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = o.optString("channelCode")
            if (code.isBlank()) return@mapNotNull null
            val poster = o.optString("posterUrl")
                .ifBlank { o.optJSONArray("posterList")?.optJSONObject(0)?.optString("fileUrl").orEmpty() }
            val addrs = o.optJSONArray("liveAddressList") ?: JSONArray()
            MagisChannel(
                channelCode = code,
                name = o.optString("name"),
                channelNumber = o.optInt("channelNumber"),
                posterUrl = poster,
                liveAddresses = (0 until addrs.length()).mapNotNull { j ->
                    addrs.optJSONObject(j)?.let { a ->
                        MagisLiveAddress(
                            playCode = a.optString("playCode"),
                            cdnType = a.optString("cdnType"),
                            quality = a.optString("quality"),
                            license = a.optString("license"),
                            tag = a.optString("tag"),
                            avFormat = a.optString("AVFormat"),
                        )
                    }
                },
            )
        }
        return MagisLiveData(
            dataVersion = data.optString("dataVersion"),
            expireTimeStr = data.optString("expireTimeStr"),
            channels = channels,
        )
    }

    /** POST /api/portalCore/v15/getSlbInfo — CDN list + play params (the player's session). */
    fun getSlbInfo(): JSONObject = post("/api/portalCore/v15/getSlbInfo", JSONObject().apply {
        put("appParams", "")
        put("appVer", config.apkVersion)
        put("encMediaSupported", 1)
        put("hasPay", "0")
        put("lang", "pt")
        put("liveCodeList", JSONArray().put("masmobile_live"))
        put("pipFlag", "0")
        put("portalCode", config.portalCode)
        put("reserve1", "")
        put("type", "merge")
        put("userId", config.userId)
        put("userIdentity", "1")
        put("userToken", config.userToken)
    })

    /** The ref En vivo carries for a channel: the code plus its first license token. */
    private fun refOf(ch: MagisChannel): String {
        val license = ch.liveAddresses.firstOrNull { it.license.isNotBlank() }?.license.orEmpty()
        return "magis:${ch.channelCode}|$license"
    }

    /** The channel's CDN addresses as the engine's Media specs (saved with the last catalog). */
    fun mediaFor(channelCode: String): List<TitanSdk.MediaSpec> {
        val raw = prefs.getString(KEY_MEDIA, null) ?: return emptyList()
        return runCatching {
            val arr = JSONObject(raw).optJSONArray(channelCode) ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    val name = o.optString("name")
                    val license = o.optString("license")
                    if (name.isBlank() || license.isBlank()) null
                    else TitanSdk.MediaSpec(
                        name = name,
                        license = license,
                        quality = o.optString("quality"),
                        format = o.optString("format"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** getSlbInfo with the usual session recovery (aaa* → device re-activation → retry). */
    fun getSlbInfoRecovering(): JSONObject = try {
        getSlbInfo()
    } catch (e: IOException) {
        if (e.message?.contains("portal aaa") != true) throw e
        renewSession(e)
        getSlbInfo()
    }

    /**
     * The playable addresses of one film/series content: v10/startPlayVOD with the portal's own
     * parameters (authType "0", type "2"), flattened from its quality groups to the engine's
     * Media specs (name = the movie's content id, quality, license).
     */
    fun startPlayVodRecovering(contentId: String): List<TitanSdk.MediaSpec> = try {
        startPlayVod(contentId)
    } catch (e: IOException) {
        if (e.message?.contains("portal aaa") != true) throw e
        renewSession(e)
        startPlayVod(contentId)
    }

    private fun startPlayVod(contentId: String): List<TitanSdk.MediaSpec> {
        val data = post("/api/portalCore/v10/startPlayVOD", JSONObject().apply {
            put("userToken", config.userToken)
            put("userId", config.userId)
            put("portalCode", config.portalCode)
            put("authType", "0")
            put("episodeNumberList", JSONArray().put(1))
            put("type", "2")
            put("contentId", contentId)
            put("seriesContentId", "")
            put("startTime", 0)
        })
        val episodes = data.optJSONArray("episodeList") ?: return emptyList()
        val out = ArrayList<TitanSdk.MediaSpec>()
        val seen = HashSet<String>()
        for (i in 0 until episodes.length()) {
            val episode = episodes.optJSONObject(i) ?: continue
            val groups = episode.optJSONArray("totalMovieList") ?: continue
            for (g in 0 until groups.length()) {
                val group = groups.optJSONObject(g) ?: continue
                val quality = group.optString("quality")
                val movies = group.optJSONArray("movieList") ?: continue
                for (m in 0 until movies.length()) {
                    val movie = movies.optJSONObject(m) ?: continue
                    val movieId = movie.optString("contentId")
                    if (movieId.isBlank()) continue
                    val licenses = movie.optJSONArray("licenseList") ?: continue
                    for (l in 0 until licenses.length()) {
                        val lic = licenses.optJSONObject(l) ?: continue
                        val license = lic.optString("license")
                        if (license.isBlank()) continue
                        val key = "$movieId|$quality"
                        if (!seen.add(key)) continue
                        out += TitanSdk.MediaSpec(
                            name = movieId,
                            license = license,
                            quality = quality,
                            format = movie.optString("videoFormat"),
                        )
                    }
                }
            }
        }
        Log.i("MagisClient", "startPlayVOD $contentId -> ${out.size} medias")
        return out
    }

    /** The SLB CDN entries, serialized the way the engine expects them (bean Entry). */
    fun entriesJson(slb: JSONObject): String {
        val arr = slb.optJSONArray("cdn_list") ?: return "[]"
        val out = JSONArray()
        val total = arr.length()
        for (i in 0 until total) {
            val c = arr.optJSONObject(i) ?: continue
            val urls = c.optJSONArray("url_list") ?: JSONArray()
            val urlStrings = JSONArray()
            var firstUrl = ""
            for (j in 0 until urls.length()) {
                val u = urls.optJSONObject(j)?.optString("url").orEmpty()
                if (j == 0) firstUrl = u
                if (u.isNotBlank()) urlStrings.put(u)
            }
            out.put(
                JSONObject()
                    .put("auths", urlStrings)
                    .put("buss", c.optString("tag"))
                    .put("enable", total - c.optInt("serial_number"))
                    .put("gslb_params", c.optString("gslb_params"))
                    .put("main_addr", c.optString("main_addr"))
                    .put("main_addr_code", c.optString("main_addr_mark"))
                    .put("name", c.optString("cdn_id_mark"))
                    .put("priority", 1)
                    .put("redirect", if (c.optString("cdn_type") == "1") "slb" else "")
                    .put("sign_type", field(firstUrl, "sign_type"))
                    .put("spare_addr", c.optString("spared_addr"))
                    .put("spare_addr_code", c.optString("spared_addr_mark"))
                    .put("strategy", c.optString("rule_id_mark"))
                    .put("tag", c.optString("tag")),
            )
        }
        return out.toString()
    }

    private fun field(url: String, key: String): String {
        if (url.isBlank()) return ""
        return url.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=').orEmpty()
    }

    /**
     * The VOD catalog for Home: the portal's curated rows for the recommended page plus the
     * movies / series / kids / anime pages, flattened into the app's rows. Cached briefly so
     * repeated Home loads do not hammer the portal.
     */
    suspend fun catalogRows(force: Boolean = false): List<CatalogRow> = withContext(Dispatchers.IO) {
        if (!force) cachedRows()?.let { return@withContext it }
        val rows = try {
            fetchRows()
        } catch (e: IOException) {
            if (e.message?.contains("portal aaa") != true) throw e
            renewSession(e)
            fetchRows()
        }
        saveRowsCache(rows)
        rows
    }

    private fun fetchRows(): List<CatalogRow> {
        val out = ArrayList<CatalogRow>()
        var failures = 0
        for (code in HOME_PAGES) {
            val rows = try {
                getHome(code)
            } catch (e: IOException) {
                if (sessionError(e)) throw e
                failures++
                continue
            }
            val short = code.removePrefix("masmobile_").lowercase()
            for (row in rows) {
                val films = row.assets.mapNotNull { it.toFilm() }
                if (films.isEmpty()) continue
                val title = row.name.ifBlank { row.alias }.ifBlank { short }
                out += CatalogRow(id = "magis-$short-${row.columnId}", title = title, films = films)
            }
        }
        if (out.isEmpty() && failures == HOME_PAGES.size) throw IOException("el portal Magis no respondió")
        if (out.isNotEmpty()) {
            // "Destacados": what the portal marks as new/trending (2026, Estreno, Top, Popular…),
            // deduplicated, right at the top of Home.
            val featured = ArrayList<Film>()
            val featuredIds = HashSet<String>()
            val markers = listOf("2026", "estreno", "top", "popular", "novedad", "recomend")
            for (row in out) {
                if (markers.none { row.title.lowercase().contains(it) }) continue
                for (f in row.films) {
                    if (featuredIds.add(f.id)) featured += f
                }
                if (featured.size >= 30) break
            }
            if (featured.isEmpty()) {
                for (f in out.first().films) {
                    if (featuredIds.add(f.id)) featured += f
                }
            }
            out.add(0, CatalogRow(id = "magis-destacados", title = "Destacados", films = featured.take(30)))
        }
        return out
    }

    /** POST /api/portalCore/getHome — one home page's rows (name + ten assets each). */
    fun getHome(homePageCode: String): List<MagisHomeRow> {
        val data = post("/api/portalCore/getHome", JSONObject().apply {
            put("homePageCode", homePageCode)
            put("version", "")
            put("freeVodCode", "masmobile_free")
            put("freeVersion", "")
            put("userId", config.userId)
            put("portalCode", config.portalCode)
            put("userToken", config.userToken)
        })
        val arr = data.optJSONArray("recommendList") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                val assets = o.optJSONArray("assetList") ?: JSONArray()
                MagisHomeRow(
                    name = o.optString("name"),
                    alias = o.optString("alias"),
                    columnId = o.optInt("columnId"),
                    assets = (0 until assets.length()).mapNotNull { j ->
                        assets.optJSONObject(j)?.let { a ->
                            val posters = a.optJSONArray("posterList") ?: JSONArray()
                            MagisAsset(
                                contentId = a.optString("contentId"),
                                name = a.optString("name"),
                                alias = a.optString("alias"),
                                programType = a.optString("programType"),
                                director = a.optString("director"),
                                actorDisplay = a.optString("actorDisplay"),
                                description = a.optString("description"),
                                tags = a.optString("tags"),
                                duration = a.optString("duration"),
                                posters = (0 until posters.length()).mapNotNull { k ->
                                    posters.optJSONObject(k)?.let { p ->
                                        MagisPoster(
                                            type = p.optString("fileType"),
                                            url = p.optString("fileUrl"),
                                            size = p.optString("size"),
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun cachedRows(): List<CatalogRow>? = runCatching {
        val at = prefs.getLong(KEY_ROWS_AT, 0L)
        if (System.currentTimeMillis() - at > ROWS_TTL_MS) return null
        val raw = prefs.getString(KEY_ROWS, null) ?: return null
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val fj = o.optJSONArray("films") ?: JSONArray()
            val films = (0 until fj.length()).mapNotNull { j ->
                fj.optJSONObject(j)?.let { f ->
                    Film(
                        id = f.optString("id"),
                        title = normalizeDisplayName(f.optString("t")),
                        year = f.optInt("y"),
                        durationMin = f.optInt("d"),
                        synopsis = f.optString("s"),
                        posterUrl = f.optString("p"),
                        videoUrl = f.optString("v"),
                        sourceUrl = PLUGIN_PACKAGE,
                    )
                }
            }
            CatalogRow(id = o.optString("id"), title = o.optString("title"), films = films)
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun saveRowsCache(rows: List<CatalogRow>) {
        runCatching {
            val arr = JSONArray()
            for (r in rows) {
                val fj = JSONArray()
                for (f in r.films) {
                    fj.put(
                        JSONObject().put("id", f.id).put("t", f.title).put("y", f.year)
                            .put("d", f.durationMin).put("s", f.synopsis).put("p", f.posterUrl).put("v", f.videoUrl),
                    )
                }
                arr.put(JSONObject().put("id", r.id).put("title", r.title).put("films", fj))
            }
            prefs.edit().putString(KEY_ROWS, arr.toString()).putLong(KEY_ROWS_AT, System.currentTimeMillis()).apply()
        }
    }

    /**
     * One encrypted portalCore call. Builds the common envelope plus [specific], encrypts the
     * JSON with [KEY], posts it to each host until one answers, and decrypts the `data` field.
     */
    private fun post(path: String, specific: JSONObject): JSONObject {
        val c = config
        val body = JSONObject().apply {
            put("apkVersion", c.apkVersion)
            put("appId", c.appId)
            put("appLanguage", c.appLanguage)
            put("cpu", c.cpu)
            put("deviceToken", c.deviceToken)
            put("hardwareInfo", c.hardwareInfo)
            put("loginType", "3")
            put("model", c.model)
            put("product", c.product)
            put("reserve1", c.reserve1)
            put("sn", c.sn)
            put("sysVersion", c.sysVersion)
            val keys = specific.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, specific.get(k))
            }
        }
        val wire = XuperCrypto.encryptBody(body.toString(), KEY)
        val hosts = if (c.host.isNotBlank()) listOf(c.host) else HOSTS
        var last: Exception? = null
        for (host in hosts) {
            try {
                val conn = (URL("https://$host$path").openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 25_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json;charset=utf-8")
                    setRequestProperty("apk", c.appId)
                    setRequestProperty("apkVer", c.apkVersion)
                    setRequestProperty("spkgVer", c.sysVersion)
                }
                conn.outputStream.use { it.write(wire.toByteArray(Charsets.US_ASCII)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("HTTP $code")
                val obj = JSONObject(text)
                val returnCode = obj.optString("returnCode")
                if (returnCode != "0") {
                    throw IOException("portal $returnCode ${obj.optString("errorMessage")}".trim())
                }
                val data = obj.opt("data")
                val decrypted = when (data) {
                    is String -> XuperCrypto.decryptBody(data, KEY)
                        ?: throw IOException("respuesta ilegible")
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
}

data class MagisColumn(
    val id: Int,
    val code: String,
    val name: String,
    val parentId: Int,
    val type: String,
)

data class MagisLiveAddress(
    val playCode: String,
    val cdnType: String,
    val quality: String,
    val license: String,
    val tag: String,
    val avFormat: String,
)

data class MagisChannel(
    val channelCode: String,
    val name: String,
    val channelNumber: Int,
    val posterUrl: String,
    val liveAddresses: List<MagisLiveAddress>,
)

data class MagisLiveData(
    val dataVersion: String,
    val expireTimeStr: String,
    val channels: List<MagisChannel>,
)

data class MagisHomeRow(
    val name: String,
    val alias: String,
    val columnId: Int,
    val assets: List<MagisAsset>,
)

data class MagisAsset(
    val contentId: String,
    val name: String,
    val alias: String,
    val programType: String,
    val director: String,
    val actorDisplay: String,
    val description: String,
    val tags: String,
    val duration: String,
    val posters: List<MagisPoster>,
)

data class MagisPoster(val type: String, val url: String, val size: String)

private val YEAR_IN_TAGS = Regex("\\b(19|20)\\d{2}\\b")
private val DURATION_RE = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})$")

/** The portal's asset as a Home film: poster, year from tags, running time when known. */
private fun MagisAsset.toFilm(): Film? {
    if (contentId.isBlank() || name.isBlank()) return null
    val poster = posters.firstOrNull { it.type == "icon" && it.url.isNotBlank() }?.url
        ?: posters.firstOrNull { it.type == "poster" && it.url.isNotBlank() }?.url
        ?: posters.firstOrNull { it.url.isNotBlank() }?.url
        ?: ""
    val minutes = DURATION_RE.find(duration.trim())?.let { m ->
        (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    } ?: 0
    return Film(
        id = contentId,
        title = normalizeDisplayName(name),
        year = YEAR_IN_TAGS.find(tags)?.value?.toIntOrNull() ?: 0,
        durationMin = minutes,
        synopsis = description,
        posterUrl = poster,
        videoUrl = "magisvod:$contentId",
        sourceUrl = PLUGIN_PACKAGE,
    )
}
