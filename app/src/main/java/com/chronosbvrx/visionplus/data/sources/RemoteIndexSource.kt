package com.chronosbvrx.visionplus.data.sources

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.CatalogRow
import com.chronosbvrx.visionplus.data.Film
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The remote index (the person's own server): the categories calculated there
 * (Estrenos, Recomendados, genres, the portals' own shelves) already joined with
 * their titles. It feeds the Categorías screen and the search; playback keeps
 * resolving on the device (`xtv:`/`magisvod:`/`ia:`/`xtvlive:` refs), so this
 * source never claims a ref. When the server cannot be reached the last cached
 * copy is served, and when there is none the local sources stay in charge.
 */
object RemoteIndexSource {

    private const val TAG = "RemoteIndex"

    /** Public route of the index (independent Vision+ domain via DuckDNS). */
    internal const val BASE_URL = "https://visionplusv2.duckdns.org"

    /** Read-only key of the index API. */
    internal const val API_KEY = "fb2244441f690185f861470423afb43278c3b19d31b14115"

    /** Titles per category fetched for browsing/searching on the device. */
    private const val FILMS_PER_CATEGORY = 100

    private const val PREFS = "catalog_remote"
    private const val K_PAYLOAD = "payload_json"
    private const val K_VERSION = "version"
    private const val K_AT = "rows_at"

    /** El índice vive en un archivo, no en SharedPreferences: con varios MB, el XML de prefs
     *  se carga completo en memoria al primer acceso y causaba ANR/jank en el arranque. */
    private const val INDEX_FILE = "remote_index.json"
    private const val SECTION_FILE_PREFIX = "remote_section_"

    private fun indexFile(context: Context) = java.io.File(context.applicationContext.filesDir, INDEX_FILE)

    private fun sectionFile(context: Context, name: String) =
        java.io.File(context.applicationContext.filesDir, "$SECTION_FILE_PREFIX$name.json")

    /** El JSON del índice guardado (archivo nuevo o prefs de versiones anteriores, que migra). */
    private fun readIndexPayload(context: Context): String? {
        val file = indexFile(context)
        if (file.exists()) return runCatching { file.readText() }.getOrNull()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getString(K_PAYLOAD, null)
        if (!legacy.isNullOrBlank()) {
            runCatching { file.writeText(legacy) }
            prefs.edit().remove(K_PAYLOAD).apply()
        }
        return legacy
    }

    private fun writeIndexPayload(context: Context, json: String) {
        runCatching { indexFile(context).writeText(json) }
    }

    private fun readSectionPayload(context: Context, name: String): String? {
        val file = sectionFile(context, name)
        if (file.exists()) return runCatching { file.readText() }.getOrNull()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getString("section_$name", null)
        if (!legacy.isNullOrBlank()) {
            runCatching { file.writeText(legacy) }
            prefs.edit().remove("section_$name").apply()
        }
        return legacy
    }

    private fun writeSectionPayload(context: Context, name: String, json: String) {
        runCatching { sectionFile(context, name).writeText(json) }
    }

    /** How long a fetched copy is trusted before asking the server again. */
    private const val TTL_MS = 10L * 60 * 1000
    private const val SECTION_TTL_MS = 15L * 60 * 1000

    private val memorySections = java.util.concurrent.ConcurrentHashMap<String, List<CatalogRow>>()
    private val memorySectionsAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val memorySearchCache = java.util.concurrent.ConcurrentHashMap<String, List<Film>>()
    private val memorySearchCacheAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val SEARCH_CACHE_TTL_MS = 5L * 60 * 1000
    private var memoryIndexRows: List<CatalogRow>? = null
    private var memoryIndexRowsAt = 0L

    internal data class IndexPayload(val version: Int, val upToDate: Boolean, val rows: List<CatalogRow>)

    /** The index rows: the fresh cached copy when there is one, otherwise the server's answer. */
    suspend fun rows(context: Context): List<CatalogRow> = withContext(Dispatchers.IO) {
        val cached = cached(context)
        val at = if (memoryIndexRowsAt > 0L) memoryIndexRowsAt else {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getLong(K_AT, 0L)
        }
        if (cached.isNotEmpty() && System.currentTimeMillis() - at < TTL_MS) {
            Log.i(TAG, "índice remoto desde caché: ${cached.size} categorías")
            return@withContext cached
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fetched = runCatching { download(prefs.getInt(K_VERSION, 0), cached) }
            .onFailure {
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "INDEX_FETCH_FAIL", "${it.javaClass.simpleName}: ${it.message}",
                )
            }
            .getOrNull()
            ?: run {
                Log.w(TAG, "índice remoto inalcanzable; se usa la copia local (${cached.size} categorías)")
                return@withContext cached
            }
        if (fetched.second.isNotEmpty()) {
            memoryIndexRows = fetched.second
            memoryIndexRowsAt = System.currentTimeMillis()
            writeIndexPayload(context, payloadToJson(fetched.first, fetched.second))
            prefs.edit()
                .putInt(K_VERSION, fetched.first)
                .putLong(K_AT, System.currentTimeMillis())
                .apply()
        }
        Log.i(TAG, "índice remoto v${fetched.first}: ${fetched.second.size} categorías")
        fetched.second
    }

    /** La última copia guardada, sin red: para pintar contenido al instante mientras llega lo fresco. */
    fun cached(context: Context): List<CatalogRow> {
        memoryIndexRows?.takeIf { it.isNotEmpty() }?.let { return it }
        val rows = runCatching {
            parseIndex(readIndexPayload(context).orEmpty()).rows
        }.getOrDefault(emptyList())
        if (rows.isNotEmpty()) {
            memoryIndexRows = rows
        }
        return rows
    }

    private fun download(since: Int, cached: List<CatalogRow>): Pair<Int, List<CatalogRow>> {
        val connection = (URL("$BASE_URL/v1/index?limitFilms=$FILMS_PER_CATEGORY&since=$since")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("X-Api-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("el índice respondió HTTP ${connection.responseCode}")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val payload = parseIndex(text)
            return payload.version to if (payload.upToDate) cached else payload.rows
        } finally {
            connection.disconnect()
        }
    }

    /** The server's JSON as rows; pure, so the tests can exercise it. */
    internal fun parseIndex(text: String): IndexPayload {
        if (text.isBlank()) return IndexPayload(0, false, emptyList())
        val root = JSONObject(text)
        val version = root.optInt("version", 0)
        if (root.optBoolean("upToDate")) return IndexPayload(version, true, emptyList())
        return IndexPayload(version, false, parseRows(root.optJSONArray("categories") ?: JSONArray()))
    }

    /** Un arreglo de categorías/hileras como filas; puro, para tests y secciones. */
    internal fun parseRows(categories: JSONArray): List<CatalogRow> {
        val rows = ArrayList<CatalogRow>(categories.length())
        for (i in 0 until categories.length()) {
            val category = categories.optJSONObject(i) ?: continue
            val id = category.optString("id")
            if (id.isBlank()) continue
            val filmsJson = category.optJSONArray("films") ?: JSONArray()
            val films = ArrayList<Film>(filmsJson.length())
            val seenFilms = HashSet<String>()
            for (j in 0 until filmsJson.length()) {
                val f = filmsJson.optJSONObject(j) ?: continue
                val rawFilmId = f.optString("id")
                if (rawFilmId.isBlank()) continue
                val rawVideoUrl = f.optString("videoUrl")
                val (filmId, videoUrl) = when {
                    rawVideoUrl.startsWith("magisvod:") -> {
                        val cid = rawVideoUrl.removePrefix("magisvod:")
                        "xtv:$cid" to "xtv:$cid"
                    }
                    rawFilmId.startsWith("magis:") && !rawVideoUrl.startsWith("http") -> {
                        val cid = rawFilmId.removePrefix("magis:")
                        "xtv:$cid" to "xtv:$cid"
                    }
                    else -> rawFilmId to rawVideoUrl
                }
                if (!seenFilms.add(filmId)) continue
                films += Film(
                    id = filmId,
                    title = f.optString("title"),
                    year = f.optInt("year"),
                    durationMin = f.optInt("durationMin"),
                    synopsis = f.optString("synopsis"),
                    posterUrl = f.optString("posterUrl"),
                    videoUrl = videoUrl,
                    sourceUrl = f.optString("sourceUrl"),
                    score = f.optDouble("score", 0.0),
                    popularity = f.optDouble("popularity", 0.0),
                    voteCount = f.optInt("voteCount", 0),
                    groupId = f.optString("groupId"),
                    seasons = parseSeasons(f.optJSONArray("seasons")),
                    kind = f.optString("kind"),
                )
            }
            rows += CatalogRow(id, category.optString("title"), films)
        }
        return rows
    }

    /** Obtiene la sección desde memoria RAM al instante (0 ms) o desde disco si existe. */
    fun cachedSection(context: Context, name: String): List<CatalogRow>? {
        memorySections[name]?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = readSectionPayload(context, name) ?: return null
        return runCatching {
            val rows = parseRows(JSONArray(raw))
            if (rows.isNotEmpty()) {
                memorySections[name] = rows
            }
            rows
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Precarga en memoria RAM las 4 secciones principales durante el arranque. */
    suspend fun prewarm(context: Context) = withContext(Dispatchers.IO) {
        val sections = listOf("movies", "series", "novelas", "anime")
        // Paso 1: calentar RAM desde disco inmediatamente (< 10 ms)
        for (sec in sections) {
            cachedSection(context, sec)
        }
        // Paso 2: si alguna no está en RAM o está vencida, refrescar en segundo plano
        for (sec in sections) {
            val at = memorySectionsAt[sec] ?: 0L
            val needRefresh = memorySections[sec].isNullOrEmpty() || (System.currentTimeMillis() - at > SECTION_TTL_MS)
            if (needRefresh) {
                runCatching { fetchSectionNetwork(context, sec) }
            }
        }
    }

    private fun fetchSectionNetwork(context: Context, name: String, limit: Int = 40): List<CatalogRow>? {
        val connection = (URL("$BASE_URL/v1/section/$name?limit=$limit")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("X-Api-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                error("la sección respondió HTTP ${connection.responseCode}")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rows = parseRows(JSONObject(text).optJSONArray("rows") ?: JSONArray())
            if (rows.isNotEmpty()) {
                memorySections[name] = rows
                memorySectionsAt[name] = System.currentTimeMillis()
                writeSectionPayload(context, name, rowsToJson(rows).toString())
                Log.i(TAG, "sección $name actualizada: ${rows.size} hileras")
            }
            rows
        } finally {
            connection.disconnect()
        }
    }

    /** Hileras de género de una sección (`movies`/`series`/`novelas`/`anime`), con caché propia y Stale-While-Revalidate. */
    suspend fun section(context: Context, name: String, limit: Int = 40): List<CatalogRow> = withContext(Dispatchers.IO) {
        val cached = cachedSection(context, name)
        val at = memorySectionsAt[name] ?: 0L
        val isFresh = cached != null && cached.isNotEmpty() && (System.currentTimeMillis() - at < SECTION_TTL_MS)
        if (isFresh) {
            return@withContext cached!!
        }

        val networkRows = runCatching { fetchSectionNetwork(context, name, limit) }.getOrNull()
        if (!networkRows.isNullOrEmpty()) {
            return@withContext networkRows
        }
        cached ?: emptyList()
    }

    /** Búsqueda en el servidor VPS (rápida, indexada en Postgres): busca en segundos 80,000+ títulos sin saturar la CPU de la TV. */
    suspend fun search(query: String, limit: Int = 60): List<Film> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val cacheKey = "${q.lowercase()}:$limit"
        val cachedAt = memorySearchCacheAt[cacheKey] ?: 0L
        val cachedList = memorySearchCache[cacheKey]
        if (cachedList != null && (System.currentTimeMillis() - cachedAt < SEARCH_CACHE_TTL_MS)) {
            return@withContext cachedList
        }

        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val connection = (URL("$BASE_URL/v1/search?q=$encoded&limit=$limit")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("X-Api-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext cachedList ?: emptyList()
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val filmsJson = root.optJSONArray("films") ?: JSONArray()
            val films = ArrayList<Film>(filmsJson.length())
            val seenFilms = HashSet<String>()
            for (i in 0 until filmsJson.length()) {
                val f = filmsJson.optJSONObject(i) ?: continue
                val parsed = parseFilmObject(f) ?: continue
                if (!seenFilms.add(parsed.id)) continue
                films += parsed
            }
            if (memorySearchCache.size > 100) {
                memorySearchCache.clear()
                memorySearchCacheAt.clear()
            }
            memorySearchCache[cacheKey] = films
            memorySearchCacheAt[cacheKey] = System.currentTimeMillis()
            films
        } catch (_: Exception) {
            cachedList ?: emptyList()
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSeasons(arr: JSONArray?): List<com.chronosbvrx.visionplus.data.SeasonRef> {        if (arr == null) return emptyList()
        val byNumber = LinkedHashMap<Int, com.chronosbvrx.visionplus.data.SeasonRef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optInt("n", 0)
            val candidate = com.chronosbvrx.visionplus.data.SeasonRef(
                n = n,
                titleId = o.optString("titleId"),
                videoUrl = o.optString("videoUrl"),
            )
            val current = byNumber[n]
            // Ante duplicados (T1 + Temporada 1, XTV + Magis), manda el miembro XTV:
            // es el único que la ficha puede resolver por el portal.
            if (current == null || (!isXtvRef(current.titleId) && isXtvRef(candidate.titleId))) {
                byNumber[n] = candidate
            }
        }
        return byNumber.values.sortedBy { it.n }
    }

    internal fun isXtvRef(id: String): Boolean = id.startsWith("xtv:")

    /** Un objeto `film` del servidor → [Film] (normaliza refs magis→xtv). Puro, testeable. */
    internal fun parseFilmObject(f: JSONObject): Film? {
        val rawFilmId = f.optString("id")
        if (rawFilmId.isBlank()) return null
        val rawVideoUrl = f.optString("videoUrl")
        val (filmId, videoUrl) = when {
            rawVideoUrl.startsWith("magisvod:") -> {
                val cid = rawVideoUrl.removePrefix("magisvod:")
                "xtv:$cid" to "xtv:$cid"
            }
            rawFilmId.startsWith("magis:") && !rawVideoUrl.startsWith("http") -> {
                val cid = rawFilmId.removePrefix("magis:")
                "xtv:$cid" to "xtv:$cid"
            }
            else -> rawFilmId to rawVideoUrl
        }
        return Film(
            id = filmId,
            title = f.optString("title"),
            year = f.optInt("year"),
            durationMin = f.optInt("durationMin"),
            synopsis = f.optString("synopsis"),
            posterUrl = f.optString("posterUrl"),
            videoUrl = videoUrl,
            sourceUrl = f.optString("sourceUrl"),
            score = f.optDouble("score", 0.0),
            popularity = f.optDouble("popularity", 0.0),
            voteCount = f.optInt("voteCount", 0),
            groupId = f.optString("groupId"),
            seasons = parseSeasons(f.optJSONArray("seasons")),
            kind = f.optString("kind"),
        )
    }

    private var memoryTrending: List<Film>? = null
    private var memoryTrendingAt = 0L
    private const val TRENDING_TTL_MS = 12L * 60 * 60 * 1000

    /** «Populares de la semana» (TMDB /trending casado con el catálogo): caché de 12 h. */
    suspend fun trending(limit: Int = 20): List<Film> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (memoryTrending != null && now - memoryTrendingAt < TRENDING_TTL_MS) {
            return@withContext memoryTrending ?: emptyList()
        }
        val connection = (URL("$BASE_URL/v1/trending?limit=$limit")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("X-Api-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext memoryTrending ?: emptyList()
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).optJSONArray("films") ?: JSONArray()
            val out = ArrayList<Film>(arr.length())
            for (i in 0 until arr.length()) {
                parseFilmObject(arr.optJSONObject(i) ?: continue)?.let { out += it }
            }
            if (out.isNotEmpty()) {
                memoryTrending = out
                memoryTrendingAt = now
            }
            out
        } catch (_: Exception) {
            memoryTrending ?: emptyList()
        } finally {
            connection.disconnect()
        }
    }

    /** Un estreno al aire: serie, temporada, capítulo, hora y la ficha propia. */
    data class Airing(
        val show: String,
        val season: Int,
        val episode: Int,
        val title: String,
        val airtimeMs: Long,
        val imdb: String,
        val titleId: String = "",
    )

    /** `airings` del servidor → lista (tolera fechas rotas). Puro, testeable. */
    internal fun parseAirings(arr: JSONArray?): List<Airing> {
        if (arr == null) return emptyList()
        val out = ArrayList<Airing>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val show = o.optString("show")
            if (show.isBlank()) continue
            val airtime = runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                    .parse(o.optString("airtime"))?.time
            }.getOrNull() ?: 0L
            out += Airing(
                show = show,
                season = o.optInt("season"),
                episode = o.optInt("episode"),
                title = o.optString("title"),
                airtimeMs = airtime,
                imdb = o.optString("imdb"),
                titleId = o.optString("titleId"),
            )
        }
        return out
    }

    /** Agenda de estrenos (TVMaze): sin caché larga, la agenda cambia cada día. */
    suspend fun schedule(days: Int = 7): List<Airing> = withContext(Dispatchers.IO) {
        val connection = (URL("$BASE_URL/v1/schedule?days=${days.coerceIn(1, 14)}")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("X-Api-Key", API_KEY)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseAirings(JSONObject(text).optJSONArray("airings"))
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun rowToJson(row: CatalogRow): JSONObject {
        val films = JSONArray()
        row.films.forEach { f ->
            films.put(JSONObject().apply {
                put("id", f.id)
                put("title", f.title)
                put("year", f.year)
                put("durationMin", f.durationMin)
                put("synopsis", f.synopsis)
                put("posterUrl", f.posterUrl)
                put("videoUrl", f.videoUrl)
                put("sourceUrl", f.sourceUrl)
                put("score", f.score)
                put("popularity", f.popularity)
                put("voteCount", f.voteCount)
                if (f.kind.isNotBlank()) put("kind", f.kind)
                if (f.groupId.isNotBlank()) put("groupId", f.groupId)
                if (f.seasons.isNotEmpty()) {
                    val seasons = JSONArray()
                    f.seasons.forEach { season ->
                        seasons.put(
                            JSONObject()
                                .put("n", season.n)
                                .put("titleId", season.titleId)
                                .put("videoUrl", season.videoUrl),
                        )
                    }
                    put("seasons", seasons)
                }
            })
        }
        return JSONObject().put("id", row.id).put("title", row.title).put("films", films)
    }

    private fun rowsToJson(rows: List<CatalogRow>): JSONArray =
        JSONArray().apply { rows.forEach { put(rowToJson(it)) } }

    private fun payloadToJson(version: Int, rows: List<CatalogRow>): String =
        JSONObject().put("version", version).put("categories", rowsToJson(rows)).toString()
}
