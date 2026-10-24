package com.chronosbvrx.visionplus.data.homefeed

import android.content.Context
import android.net.Uri
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.data.CatalogSeed
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.HomeCurated
import com.chronosbvrx.visionplus.data.PlaybackHistory
import com.chronosbvrx.visionplus.data.Popularity
import com.chronosbvrx.visionplus.data.ResumeEntry
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.isEpisodeEntry
import com.chronosbvrx.visionplus.data.offline.NetworkMonitor
import com.chronosbvrx.visionplus.data.profile.ProfileStore
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import com.chronosbvrx.visionplus.home.contract.ContinueItem
import com.chronosbvrx.visionplus.home.contract.FeedItem
import com.chronosbvrx.visionplus.home.contract.FeedMeta
import com.chronosbvrx.visionplus.home.contract.FeedRow
import com.chronosbvrx.visionplus.home.contract.HeroItem
import com.chronosbvrx.visionplus.home.contract.HomeContract
import com.chronosbvrx.visionplus.home.contract.HomeFeed
import com.chronosbvrx.visionplus.home.contract.HomeFeedCodec
import com.chronosbvrx.visionplus.home.contract.LiveItem
import com.chronosbvrx.visionplus.home.contract.MediaKind
import com.chronosbvrx.visionplus.home.contract.RowKind
import com.chronosbvrx.visionplus.home.contract.SportsItem
import com.chronosbvrx.visionplus.home.contract.VisionLinks
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El feed que pinta Vision+ Home (el launcher, APK aparte).
 *
 * Dos reglas mandan aquí:
 * 1. **Nunca bloquea el primer frame del launcher**: el provider sirve el último JSON escrito
 *    y solo lo reconstruye cuando está viejo.
 * 2. **Nunca toca la red en el camino síncrono**: el EPG y el refresco corren en segundo plano
 *    y el resultado se reescribe cuando llega.
 */
object HomeFeedStore {
    private const val FILE_NAME = "home_feed.json"
    private const val PART_NAME = "home_feed.json.part"

    /** Pasado este tiempo el provider reconstruye el feed desde las cachés de disco. */
    private const val STALE_MS = 10 * 60_000L

    /** Freno entre refrescos de fondo (la app lo llama en cada onResume). */
    private const val REFRESH_THROTTLE_MS = 20_000L

    private const val LIVE_LIMIT = 400
    private const val EPG_CHANNELS = 16
    private const val SPORTS_LIMIT = 250
    private const val ROW_LIMIT = 18

    /** Canales mexicanos de referencia: primero lo que la gente busca. */
    private val POPULAR_MX = listOf(
        "canal 5", "azteca 7", "las estrellas", "canal 2", "azteca uno", "tudn", "fox sports",
        "espn", "canal 6", "multimedios", "imagen", "milenio", "adn40", "a+", "canal 22",
        "once", "canal 11", "tvc", "enlace", "excelsior", "el financiero", "bloomberg",
        "golden", "bandamax", "telehit", "de pelicula", "tlnovelas", "distrito comedia",
        "bitme", "forotv", "canal del congreso", "ingenio", "azteca internacional",
    )

    internal fun mxPriority(name: String): Int {
        val n = name.lowercase()
        val index = POPULAR_MX.indexOfFirst { n.contains(it) }
        return if (index >= 0) index else POPULAR_MX.size
    }

    /** «A&E HD», «A&E FHD» y «A&E» son la MISMA señal: una sola tarjeta por canal. */
    private val QUALITY_TAG = Regex("\\b(fhd|uhd|hd|sd|4k|h265|hevc|h264)\\b")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    internal fun channelKey(name: String): String =
        name.lowercase().replace(QUALITY_TAG, " ").replace(NON_ALNUM, "")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastRefreshAt = 0L

    /** Un programa del EPG ya resuelto a texto, con su ventana para recalcular el avance. */
    internal data class EpgWindow(
        val now: String,
        val next: String,
        val startMs: Long = 0L,
        val endMs: Long = 0L,
    ) {
        fun progressAt(nowMs: Long): Int {
            if (startMs <= 0L || endMs <= startMs) return 0
            return (((nowMs - startMs).toDouble() / (endMs - startMs)) * 100)
                .roundToInt().coerceIn(0, 100)
        }
    }

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    /** El JSON tal como está en disco (null si nunca se escribió). */
    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    /**
     * Lo que responde el provider: el JSON vigente o uno recién construido con las cachés.
     * Nunca lanza y nunca se queda sin respuesta (peor caso: un feed vacío válido).
     */
    fun readOrBuild(context: Context): String {
        val app = context.applicationContext
        val target = file(app)
        val age = System.currentTimeMillis() - target.lastModified()
        if (target.exists() && age in 0..STALE_MS) {
            read(app)?.let { if (it.isNotBlank()) return it }
        }
        val json = runCatching { HomeFeedCodec.encode(build(app)) }
            .getOrElse { HomeFeedCodec.encode(HomeFeed(meta = meta(app))) }
        write(app, json)
        return json
    }

    /**
     * Reconstruye el feed en segundo plano y avisa al launcher (notifyChange). Después, con
     * presupuesto, le pega el EPG de los primeros canales y lo reescribe.
     */
    fun refreshAsync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < REFRESH_THROTTLE_MS) return
        lastRefreshAt = now
        scope.launch {
            runCatching {
                val diskOnly = HomeFeedCodec.encode(build(app))
                write(app, diskOnly)
                notifyChange(app)
            }
            val epg = runCatching { withTimeoutOrNull(8_000L) { fetchEpg(app) } }.getOrNull()
            // La agenda deportiva se refresca aquí (el launcher no tiene red propia).
            runCatching { withTimeoutOrNull(6_000L) { GeeSportsSource.refreshEvents(app) } }
            runCatching {
                write(app, HomeFeedCodec.encode(build(app, epg.orEmpty())))
                notifyChange(app)
            }
        }
    }

    /** El feed en memoria (sin red). Expuesto para pruebas y para el provider. */
    internal fun build(context: Context, epg: Map<String, EpgWindow> = emptyMap()): HomeFeed {
        val app = context.applicationContext
        // La guía se guarda en disco: si el proceso de Vision+ muere justo después de servir
        // el feed (el provider lo arranca en frío), la fila de TV en vivo conserva su «AHORA».
        val guide = epg.ifEmpty { readEpgCache(app) }
        val indexRows = runCatching { RemoteIndexSource.cached(app) }.getOrDefault(emptyList())
        var curated = runCatching { Popularity.homeCurated(emptyList(), indexRows) }
            .getOrDefault(HomeCurated())
        if (curated.pool.isEmpty()) {
            // Primer arranque (sin caché del índice): la semilla curada de la app evita un
            // Inicio vacío. En cuanto el índice llega, el feed se reescribe solo.
            curated = runCatching { Popularity.homeCurated(CatalogSeed.initialRows(), emptyList()) }
                .getOrDefault(HomeCurated())
        }
        val pool = curated.pool

        val top10Keys = curated.top10.mapTo(HashSet()) { it.id }
        val hero = LinkedHashMap<String, Film>(8)
        (listOfNotNull(curated.hero) + curated.premieres + curated.top10).forEach { film ->
            if (film.posterUrl.isNotBlank()) hero.putIfAbsent(film.id.ifBlank { film.videoUrl }, film)
        }
        val heroItems = hero.values.take(5).mapIndexed { index, film ->
            heroItem(
                film = film,
                badge = when {
                    index == 0 -> "TENDENCIA DE HOY"
                    film.id in top10Keys -> "TOP 10"
                    else -> "ESTRENO"
                },
            )
        }

        val continueItems = runCatching { continueItems(app, pool) }.getOrDefault(emptyList())

        val liveItems = runCatching { liveItems(app, guide) }.getOrDefault(emptyList())

        val sports = runCatching { sportsItems(app) }.getOrDefault(emptyList())

        val rows = ArrayList<FeedRow>(6)
        if (curated.top10.isNotEmpty()) {
            rows += FeedRow(
                id = "top10",
                label = "Top 10 en Vision+",
                kind = RowKind.TOP10,
                // El puesto lo pinta el launcher (chip de ranking); el badge es para «NUEVO».
                items = curated.top10.mapNotNull { film -> item(film) },
            )
        }
        if (curated.premieres.isNotEmpty()) {
            rows += FeedRow(
                id = "premieres",
                label = "Novedades y estrenos",
                items = curated.premieres.mapNotNull { item(it, badge = "NUEVO") },
            )
        }
        curated.kindRows.forEach { row ->
            val items = row.films.mapNotNull { item(it) }
            if (items.isNotEmpty()) {
                rows += FeedRow(id = row.id, label = row.title, items = items.take(ROW_LIMIT))
            }
        }

        return HomeFeed(
            meta = meta(app),
            hero = heroItems,
            continueWatching = continueItems,
            live = liveItems,
            rows = rows,
            sports = sports,
        )
    }

    // --- Piezas ---

    private fun meta(context: Context): FeedMeta = FeedMeta(
        version = HomeContract.VERSION,
        generatedAt = System.currentTimeMillis(),
        profile = runCatching { ProfileStore.active(context).name }.getOrDefault(""),
        profiles = runCatching { ProfileStore.profiles(context).size }.getOrDefault(1),
        online = NetworkMonitor.isOnline.value,
        appVersion = BuildConfig.VERSION_NAME,
    )

    private fun heroItem(film: Film, badge: String): HeroItem = HeroItem(
        ref = film.id,
        title = film.title,
        poster = film.posterUrl,
        year = film.year,
        rating = film.score,
        kind = film.kind,
        synopsis = film.synopsis,
        durationMin = film.durationMin,
        badge = badge,
        series = isSeriesFilm(film),
        openLink = openLinkFor(film),
        playLink = if (isSeriesFilm(film)) "" else playLinkFor(film),
    )

    private fun item(film: Film, badge: String = ""): FeedItem? {
        if (film.posterUrl.isBlank() || film.videoUrl.isBlank()) return null
        return FeedItem(
            ref = film.id,
            title = film.title,
            poster = film.posterUrl,
            year = film.year,
            rating = film.score,
            kind = film.kind,
            badge = badge,
            series = isSeriesFilm(film),
            openLink = openLinkFor(film),
            playLink = if (isSeriesFilm(film)) "" else playLinkFor(film),
        )
    }

    private fun openLinkFor(film: Film): String =
        VisionLinks.title(
            ref = film.id,
            title = film.title,
            poster = film.posterUrl,
            videoUrl = film.videoUrl,
        )

    /** La app reproduce películas directo; el resto abre ficha (la serie decide temporada). */
    private fun playLinkFor(film: Film): String {
        val url = film.videoUrl
        if (url.isBlank()) return ""
        return if (url.startsWith("xtv:")) {
            VisionLinks.watch(
                key = url,
                title = film.title,
                poster = film.posterUrl,
                code = url.removePrefix("xtv:"),
            )
        } else {
            VisionLinks.watch(
                key = url,
                title = film.title,
                poster = film.posterUrl,
                videoUrl = url,
            )
        }
    }

    /**
     * La misma regla de la app: con 2+ temporadas (o un grupo del servidor) es serie; una
     * ficha suelta de tipo series/anime también, aunque el portal no la haya agrupado.
     */
    internal fun isSeriesFilm(film: Film): Boolean =
        film.seasons.size > 1 || film.groupId.isNotBlank() ||
            film.kind == MediaKind.SERIES || film.kind == MediaKind.ANIME

    private fun continueItems(context: Context, films: List<Film>): List<ContinueItem> =
        PlaybackHistory.continueCards(context, films, limit = 10).mapNotNull { card ->
            val entry = card.entry
            if (entry.key.isBlank()) return@mapNotNull null
            val episode = isEpisodeEntry(entry)
            ContinueItem(
                key = entry.key,
                title = if (episode) baseTitle(entry.title) else entry.title,
                poster = card.film?.posterUrl?.takeIf { it.isNotBlank() } ?: entry.posterUrl,
                progress = (card.progress * 100f).roundToInt().coerceIn(0, 100),
                positionMs = entry.positionMs,
                durationMs = entry.durationMs,
                episode = if (episode) episodeLabel(entry.title) else "",
                playLink = watchLinkFor(entry, episode),
            )
        }

    private fun watchLinkFor(entry: ResumeEntry, episode: Boolean): String = VisionLinks.watch(
        key = entry.key,
        title = entry.title,
        poster = entry.posterUrl,
        code = if (entry.xtvCode.isNotBlank()) entry.xtvCode else "",
        videoUrl = if (entry.xtvCode.isBlank()) entry.videoUrl else "",
        seriesId = if (episode) entry.seriesId else "",
        seasonIndex = if (episode) entry.seasonIndex else 0,
        seasonIds = if (episode) entry.seasonIds else emptyList(),
        seasonNames = if (episode) entry.seasonNames else emptyList(),
        resumeMs = entry.positionMs,
    )

    private fun liveItems(context: Context, epg: Map<String, EpgWindow>): List<LiveItem> {
        val now = System.currentTimeMillis()
        return uniqueChannels(context)
            .map { channel ->
                val code = channel.url.removePrefix("xtvlive:")
                val guide = epg[code]
                LiveItem(
                    code = code,
                    name = channel.name,
                    logo = channel.logo.orEmpty(),
                    group = channel.group,
                    now = guide?.now.orEmpty(),
                    next = guide?.next.orEmpty(),
                    progress = guide?.progressAt(now) ?: 0,
                    mexico = isMexico(channel.name),
                    playLink = VisionLinks.live(code, channel.name, channel.logo.orEmpty()),
                )
            }
    }

    /** Las señales de la casa, una por canal (sin las copias HD/FHD del mismo feed). */
    private fun uniqueChannels(context: Context): List<com.chronosbvrx.visionplus.data.LiveChannel> {
        val all = X4KApi.cachedLiveChannels(context)
            .asSequence()
            .filter { it.url.startsWith("xtvlive:") && it.name.isNotBlank() }
            .distinctBy { channelKey(it.name) }
            .toList()
        // México primero (y dentro de México, los canales de referencia); el resto conserva
        // el orden del portal. «Todos los canales de México» + los populares del resto.
        val mexico = all.filter { isMexico(it.name) }.sortedBy { mxPriority(it.name) }
        val rest = all.filterNot { isMexico(it.name) }
        return (mexico + rest).take(LIVE_LIMIT)
    }

    private fun isMexico(name: String): Boolean =
        runCatching { com.chronosbvrx.visionplus.data.LiveTaxonomy.countryFor(name) == "México" }
            .getOrDefault(false)

    /** La agenda de Deportes en vivo que ya espeja el VPS (en vivo primero, luego por hora). */
    private fun sportsItems(context: Context): List<SportsItem> = runCatching {
        GeeSportsSource.cachedEvents(context)
            .asSequence()
            .filter { it.id.isNotBlank() }
            // La agenda vencida (caché vieja) no debe aparecer como si fuera de hoy.
            .filter {
                it.dateIso.isBlank() ||
                    it.dateIso >= com.chronosbvrx.visionplus.data.sports.SportsSource.todayIso()
            }
            .sortedWith(
                compareBy<GeeSportsSource.GeeEvent> { it.dateIso }
                    .thenByDescending { it.live || it.alive > 0 || it.status == "in" }
                    .thenBy { it.time },
            )
            .take(SPORTS_LIMIT)
            .map { event ->
                val home = event.homeName.ifBlank { event.teamA }
                val away = event.awayName.ifBlank { event.teamB }
                val live = event.live || event.alive > 0 || event.status == "in"
                SportsItem(
                    id = event.id,
                    name = when {
                        home.isNotBlank() && away.isNotBlank() && !home.equals(away, ignoreCase = true) ->
                            "$home vs $away"
                        home.isNotBlank() -> home
                        else -> event.name
                    },
                    category = event.category,
                    dateIso = event.dateIso,
                    league = event.leagueLabel,
                    leagueLogo = event.leagueLogo,
                    homeName = home,
                    awayName = away,
                    homeLogo = event.homeLogo,
                    awayLogo = event.awayLogo,
                    homeScore = event.scoreHome,
                    awayScore = event.scoreAway,
                    detail = event.detail.ifBlank { if (live) "EN VIVO" else event.time },
                    live = live,
                    alive = event.alive,
                    total = event.total,
                    dateText = event.dateText,
                    time = event.time,
                    openLink = VisionLinks.sports(event.id),
                )
            }
            .toList()
    }.getOrDefault(emptyList())

    /** Búsqueda del launcher: el MISMO buscador del servidor que usa la app. */
    suspend fun searchItems(context: Context, query: String, limit: Int = 40): List<FeedItem> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        return runCatching { RemoteIndexSource.search(clean, limit) }.getOrDefault(emptyList())
            .mapNotNull { searchItem(it) }
    }

    /** Una tarjeta de resultado: aquí el póster es opcional (el launcher pinta monograma). */
    private fun searchItem(film: Film): FeedItem? {
        if (film.id.isBlank() || film.title.isBlank()) return null
        val series = isSeriesFilm(film)
        return FeedItem(
            ref = film.id,
            title = film.title,
            poster = film.posterUrl,
            year = film.year,
            rating = film.score,
            kind = film.kind,
            series = series,
            openLink = openLinkFor(film),
            playLink = if (series) "" else playLinkFor(film),
        )
    }

    /**
     * El EPG del VPS, solo para los primeros canales y con presupuesto. El feed sin guía ya es
     * útil; la guía llega unos segundos después y se reescribe.
     */
    private suspend fun fetchEpg(context: Context): Map<String, EpgWindow> {
        val channels = uniqueChannels(context).take(EPG_CHANNELS)
        if (channels.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        val out = HashMap<String, EpgWindow>(channels.size * 2)
        for (channel in channels) {
            val code = channel.url.removePrefix("xtvlive:")
            val items = runCatching { X4KApi.epgFor(context, code, channel.name) }.getOrDefault(emptyList())
            if (items.isEmpty()) continue
            val current = items.firstOrNull { now in it.startMs until it.endMs }
            val next = current?.let { cur -> items.firstOrNull { it.startMs == cur.endMs } }
                ?: items.firstOrNull { it.startMs > now }
            out[code] = EpgWindow(
                now = current?.name.orEmpty(),
                next = next?.name.orEmpty(),
                startMs = current?.startMs ?: 0L,
                endMs = current?.endMs ?: 0L,
            )
        }
        writeEpgCache(context, out)
        return out
    }

    // --- Caché de la guía (sobrevive a la muerte del proceso) ---

    private fun epgFile(context: Context): File =
        File(context.applicationContext.filesDir, "home_epg.json")

    private fun readEpgCache(context: Context): Map<String, EpgWindow> {
        val raw = runCatching { epgFile(context).takeIf { it.exists() }?.readText() }.getOrNull()
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(raw)
            val generatedAt = root.optLong("generatedAt", 0L)
            // La guía caduca a las 6 h: antes de eso sigue siendo útil aunque el proceso muera.
            if (generatedAt <= 0L || System.currentTimeMillis() - generatedAt > 6 * 60 * 60_000L) {
                return emptyMap()
            }
            val items = root.optJSONObject("items") ?: return emptyMap()
            val out = HashMap<String, EpgWindow>(items.length() * 2)
            items.keys().forEach { code ->
                val o = items.optJSONObject(code) ?: return@forEach
                out[code] = EpgWindow(
                    now = o.optString("now"),
                    next = o.optString("next"),
                    startMs = o.optLong("startMs"),
                    endMs = o.optLong("endMs"),
                )
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun writeEpgCache(context: Context, guide: Map<String, EpgWindow>) {
        if (guide.isEmpty()) return
        runCatching {
            val items = org.json.JSONObject()
            guide.forEach { (code, window) ->
                items.put(
                    code,
                    org.json.JSONObject()
                        .put("now", window.now)
                        .put("next", window.next)
                        .put("startMs", window.startMs)
                        .put("endMs", window.endMs),
                )
            }
            val json = org.json.JSONObject()
                .put("generatedAt", System.currentTimeMillis())
                .put("items", items)
                .toString()
            epgFile(context).writeText(json)
        }
    }

    /** «Serie · T2 · C3» → «Serie». */
    private fun baseTitle(title: String): String =
        title.substringBefore(" · T").substringBefore(" · C").ifBlank { title }

    /** «Serie · T2 · C3» → «T2 · C3». */
    private fun episodeLabel(title: String): String {
        val base = baseTitle(title)
        return title.removePrefix(base).trim(' ', '·')
    }

    // --- Disco / aviso ---

    private fun write(context: Context, json: String) {
        val target = file(context)
        val part = File(context.filesDir, PART_NAME)
        runCatching {
            part.writeText(json)
            if (!part.renameTo(target)) {
                target.writeText(json)
                part.delete()
            }
        }
    }

    private fun notifyChange(context: Context) {
        runCatching {
            context.applicationContext.contentResolver
                .notifyChange(Uri.parse(HomeContract.FEED_URI), null)
        }
    }
}
