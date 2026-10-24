package com.chronosbvrx.visionplus.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chronosbvrx.visionplus.data.sources.ArchiveSource
import com.chronosbvrx.visionplus.data.sources.PlaybackResolve
import com.chronosbvrx.visionplus.data.sources.PlaylistSource
import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource
import com.chronosbvrx.visionplus.data.sources.Source
import com.chronosbvrx.visionplus.data.sources.SourcesGateway
import com.chronosbvrx.visionplus.data.sources.XuperSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The M3U playlist of the one source, as the Live screens see it. */
internal const val XUPER_PLAYLIST_ID = "xuperplugin"

/** The Magis provider's chip in En vivo: its channels come from its own portal, not an M3U. */
internal const val MAGIS_PLAYLIST_ID = "magis"

/** The provider's reads in the Live chip row: just another section of the app. */
internal const val PLUGIN_TITLE = "TV en Vivo"

/** The Magis provider's title in the Live chip row. */
internal const val MAGIS_TITLE = "TV en Vivo"

/** The films' source tag: the app's own engine, stated plainly. */
internal const val PLUGIN_PACKAGE = "visionplus.app"

/** What making the catalog from the sources came to; [XuperCatalog.message] explains what to do next. */
enum class XuperStatus { LOADING, NO_SOURCE, EMPTY, READY }

/**
 * The app's catalog, now served through the sources gateway: the integrated engine (its lists
 * come out on localhost) first, then each list the person added. Its rows join in that same
 * order, film groups first, and En vivo serves every source's channels. Nothing is bundled
 * beyond the embedded session and the person's own lists.
 */
class XuperCatalog(context: Context) {
    companion object {
        /**
         * Último curado del Inicio a nivel proceso: al volver de una ficha la pantalla se
         * recompone desde cero y el curado tarda (segundo plano); sembrar el primer frame
         * con la estructura anterior evita que el scroll restaurado caiga en otra fila.
         * Se refresca en cada cálculo real; los tests la limpian con [seed].
         */
        @Volatile
        internal var lastHomeCurated: HomeCurated? = null
    }

    private val app = context.applicationContext
    private val store = LiveStore(app)

    /** The sources: the integrated one always, the archive's catalog, then the person's lists. */
    private val gateway: SourcesGateway by lazy {
        val lists = store.playlists().map { PlaylistSource(app, it) }
        val all: List<Source> = buildList {
            add(XuperSource())
            add(ArchiveSource())
            addAll(lists)
        }
        val gateway = SourcesGateway(app, all)
        PlaybackResolve.host(gateway)
        gateway
    }

    private val xuper: XuperSource get() = gateway.sources.filterIsInstance<XuperSource>().first()

    var status by mutableStateOf(XuperStatus.LOADING)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var rows by mutableStateOf<List<CatalogRow>>(emptyList())
        private set

    /** The server's index (Estrenos/Recomendados/Géneros y categorías propias) para Categorías/Búsqueda. */
    var indexRows by mutableStateOf<List<CatalogRow>>(emptyList())
        private set

    /** Lo personal: «Ver más tarde», «Favoritos» y las recomendaciones por afinidad. */
    var personalRows by mutableStateOf<List<CatalogRow>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /**
     * Tiempos del último arranque («cacheMs=…,semillaMs=…,portalMs=…»): diagnóstico del
     * arranque en frío; la anomalía `BOOT_SLOW` del Inicio incluye estos ms en su detalle.
     */
    @Volatile
    internal var lastLoadTimings: String = ""

    /** Evita dos fases del portal a la vez (recarga de Inicio mientras un arranque aún sondea). */
    @Volatile
    private var portalLoading = false

    /** Umbral de reporte `PORTAL_SLOW`: la fase del portal en segundo plano tardó demasiado. */
    internal val PORTAL_SLOW_MS = 20_000L

    /** Only for the UI tests: pretend the sources already answered. */
    internal fun seed(seed: List<CatalogRow>) {
        rows = seed
        status = XuperStatus.READY
        message = null
        lastHomeCurated = null
    }

    /** Recalcula «Mi lista» y las recomendaciones (barato: prefs + hileras ya en memoria). */
    fun refreshPersonal() {
        val watch = UserLibrary.watchLater(app)
        val favorites = UserLibrary.favorites(app)
        val played = UserLibrary.played(app)
        personalRows = buildList {
            if (watch.isNotEmpty()) add(CatalogRow("watch-later", "Ver más tarde", watch))
            if (favorites.isNotEmpty()) add(CatalogRow("favorites", "Favoritos", favorites))
            UserLibrary.recommendations(indexRows, played)?.let { (anchor, films) ->
                val title = if (anchor.isBlank()) "Recomendado para ti" else "Recomendado para ti · $anchor"
                add(CatalogRow("for-you", title, films))
            }
        }
    }

    /** Reaches every source and (re)builds every screen's content. */
    suspend fun load() {
        if (loading) return
        loading = true
        try {
            val bootStarted = android.os.SystemClock.uptimeMillis()
            // Primer frame con contenido real en 0 ms: la semilla se pinta ANTES de
            // parsear nada. El cacheo del índice pesa varios MB y su parseo puede
            // tardar segundos (medido: «cacheMs=4430» en arranque frío) — ahora corre
            // en segundo plano y el Inicio refresca con lo suyo apenas está listo.
            if (rows.isEmpty()) {
                rows = CatalogSeed.initialRows()
                status = XuperStatus.READY
                message = null
            }
            val afterSeedMs = android.os.SystemClock.uptimeMillis()
            refreshPersonal()
            xuper.prime(app)
            lastLoadTimings = "semillaMs=${afterSeedMs - bootStarted},cacheMs=0,portalMs=0"
            // El parseo del índice en CACHÉ (varios MB: «cacheMs=4430» medido en frío) también
            // sale del camino crítico: corre en segundo plano y el Inicio refresca con lo suyo
            // apenas está listo (y conserva las filas que ya estén pintadas).
            CoroutineScope(Dispatchers.IO).launch { cachedIndexRows() }
            // El portal (metadatos de fichas + fila de TV en vivo) SALIÓ del arranque crítico:
            // si la sesión está fría son segundos de re-acuñado + 5 llamadas del portal + un
            // JSON de ~2 MB de canales, y eso rebasaba el tope del arranque (Inicio a medias y
            // el «HOME_ROWS_FAIL» crónico). Ahora el Inicio abre con lo suyo (semilla/índice)
            // en un par de segundos y estas filas se refrescan solas en cuanto el portal responde.
            CoroutineScope(Dispatchers.IO).launch { portalRows() }

            // Sincronización diferida del índice remoto (25k+ títulos) tras 3.5 segundos:
            // la pantalla de inicio conserva las filas curadas mientras se descarga el índice para Búsqueda.
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(3_500L)
                awaitPlaybackIdle()
                runCatching { RemoteIndexSource.rows(app) }
                    .getOrDefault(emptyList())
                    .takeIf { it.isNotEmpty() }
                    ?.let { freshIndex ->
                        indexRows = freshIndex
                        refreshPersonal()
                        val freshEstrenos = freshIndex.firstOrNull { it.id == "estrenos" }
                        if (freshEstrenos != null && freshEstrenos.films.isNotEmpty()) {
                            val current = rows
                            rows = if (current.none { it.id == "estrenos" }) {
                                listOf(freshEstrenos) + current
                            } else {
                                current.map { if (it.id == "estrenos") freshEstrenos else it }
                            }
                        }
                    }
            }
            // Escalonamiento estricto de precalentamiento para Smart TV / Chromecast:
            // 1. xuper proxy se inicia tras 2 segundos.
            // 2. Canales en vivo y agenda deportiva se calientan tras 4.5 segundos.
            // 3. El motor nativo :x4k y SLB se calientan tras 7.5 segundos (sin competir con la UI en frío).
            // 4. El precalentamiento de secciones se sincroniza tras 9.5 segundos.
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(2_000L)
                runCatching { xuper.ensureProxied(app) }
                kotlinx.coroutines.delay(2_500L)
                runCatching { com.chronosbvrx.visionplus.data.xuper.X4KApi.liveChannelsFor(app) }
                runCatching {
                    val today = com.chronosbvrx.visionplus.data.sports.SportsSource.todayIso()
                    com.chronosbvrx.visionplus.data.sports.SportsSource.matches(app, today)
                }
                kotlinx.coroutines.delay(3_000L)
                runCatching { com.chronosbvrx.visionplus.data.xuper.X4KApi.ensureEngineData(app, deferPrewarm = false) }
                kotlinx.coroutines.delay(2_000L)
                runCatching { RemoteIndexSource.prewarm(app) }
            }
            if (rows.isNotEmpty()) {
                status = XuperStatus.READY
                message = null
            } else {
                status = XuperStatus.NO_SOURCE
                message = "No pudimos cargar tu contenido en este momento. Revisa tu conexión e intenta de nuevo."
            }
        } finally {
            loading = false
        }
    }

    /**
     * Espera a que el motor deje de estar arrancando.
     *
     * En una TCL de 2 GB se midió `cacheMs=12436`: 12.4 s de CPU solo para leer el índice
     * guardado. Si eso corre mientras el reproductor negocia la señal, se la quita. Tope de
     * 60 s: nunca se queda esperando una película entera.
     */
    private suspend fun awaitPlaybackIdle() {
        var waited = 0L
        while (com.chronosbvrx.visionplus.data.PlaybackFocus.busy() && waited < 60_000L) {
            kotlinx.coroutines.delay(1_000L)
            waited += 1_000L
        }
    }

    /**
     * Índice en CACHÉ (segundo plano): manda Búsqueda/Categorías al instante con el último
     * curado y sustituye la semilla del primer frame por el estreno #1 del índice sin
     * pisar las filas que ya estén pintadas (vivo/base a medias del portal, p. ej.).
     */
    private suspend fun cachedIndexRows() {
        awaitPlaybackIdle()
        val started = android.os.SystemClock.uptimeMillis()
        val cachedIndex = kotlinx.coroutines.withContext(Dispatchers.IO) { RemoteIndexSource.cached(app) }
        lastLoadTimings = lastLoadTimings.replaceFirst("cacheMs=0", "cacheMs=${android.os.SystemClock.uptimeMillis() - started}")
        if (cachedIndex.isEmpty()) return
        indexRows = cachedIndex
        val estrenosCached = cachedIndex.firstOrNull { it.id == "estrenos" }
        if (estrenosCached != null && estrenosCached.films.isNotEmpty()) {
            val current = rows
            // El estreno #1 real sustituye a la semilla del primer frame; las filas que ya
            // pintó el portal (TV en vivo, fuentes) o el usuario se conservan.
            rows = listOf(estrenosCached) + current.filterNot { it.id.startsWith("seed-") || it.id == "estrenos" }
        }
    }

    /**
     * Fase del portal (segundo plano): las mismas filas de siempre — estrenos del índice,
     * TV en vivo del portal y las filas base de las fuentes — pero sin retener el arranque.
     * Guardián de tiempo: si el portal sigue lento (sesión fría, red mala) queda el reporte
     * `PORTAL_SLOW` para el diagnóstico sin tocar la experiencia ya abierta.
     */
    private suspend fun portalRows() {
        if (portalLoading) return
        portalLoading = true
        try {
            awaitPlaybackIdle()
            val started = android.os.SystemClock.uptimeMillis()
            runCatching { com.chronosbvrx.visionplus.data.xuper.X4KApi.homeRows(app) }
            val xtvLive = runCatching {
                com.chronosbvrx.visionplus.data.xuper.X4KApi.liveRow(app)
            }.getOrNull()

            val estrenosRow = indexRows.firstOrNull { it.id == "estrenos" }

            if (estrenosRow != null || xtvLive != null) {
                rows = buildList {
                    if (estrenosRow != null && estrenosRow.films.isNotEmpty()) add(estrenosRow)
                    if (xtvLive != null) add(xtvLive)
                }
                status = XuperStatus.READY
                message = null
            }

            val baseRows = try {
                withTimeoutOrNull(2000L) {
                    gateway.homeRows()
                } ?: emptyList()
            } catch (e: Exception) {
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly(
                    "HOME_ROWS_FAIL", "${e.javaClass.simpleName}: ${e.message}",
                )
                emptyList()
            }.filterNot { it.id.startsWith("magis") }

            rows = buildList {
                if (estrenosRow != null && estrenosRow.films.isNotEmpty()) add(estrenosRow)
                if (xtvLive != null) add(xtvLive)
                addAll(baseRows)
            }
            val portalMs = android.os.SystemClock.uptimeMillis() - started
            lastLoadTimings = lastLoadTimings.substringBefore(",portalMs=") + ",portalMs=$portalMs"
            // Los canales ya están en caché: Vision+ Home estrena su fila de TV en vivo.
            com.chronosbvrx.visionplus.data.homefeed.HomeFeedStore.refreshAsync(app, force = true)
            if (portalMs > PORTAL_SLOW_MS) {
                com.chronosbvrx.visionplus.data.diag.Diagnostics.anomaly("PORTAL_SLOW", lastLoadTimings)
            }
        } finally {
            portalLoading = false
        }
    }
}

private val DIACRITICS = Regex("\\p{Mn}+")

/** Lowercase without accents: "Películas" and "PELICULAS" are the same group. */
private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")

/** The group of an M3U entry counts as videos (not as TV) only when its name says so. */
internal fun vodLabel(group: String): String? {
    val g = fold(group)
    return when {
        g.contains("pelic") || g.contains("movie") || g.contains("cine") || g.contains("film") -> "Películas"
        g.contains("serie") || g.contains("novela") -> "Series"
        g.contains("anime") -> "Anime"
        else -> null
    }
}

/** A source's entries as Home rows: one per VOD group (Películas, Series, Anime), in that order. */
fun xuperRows(channels: List<LiveChannel>): List<CatalogRow> {
    val byLabel = channels.groupBy { vodLabel(it.group) }
    return listOf("Películas", "Series", "Anime").mapNotNull { label ->
        val films = byLabel[label].orEmpty()
            .map { it.toFilm(label) }
            .distinctBy { it.id }
            .takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        CatalogRow(id = fold(label), title = label, films = films)
    }
}

/** An M3U entry as a film: its poster is the entry logo, its video the stream URL. */
private fun LiveChannel.toFilm(groupLabel: String) = Film(
    id = "${fold(groupLabel)}-${fold(name)}".replace(' ', '_'),
    title = normalizeDisplayName(name),
    year = 0,
    durationMin = 0,
    synopsis = "",
    posterUrl = logo.orEmpty(),
    videoUrl = url,
    sourceUrl = PLUGIN_PACKAGE,
)
