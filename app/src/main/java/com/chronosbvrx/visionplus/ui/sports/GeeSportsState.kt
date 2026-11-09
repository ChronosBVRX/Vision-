package com.chronosbvrx.visionplus.ui.sports

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chronosbvrx.visionplus.data.gee.GeeAgenda
import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeProbe
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.gee.SportsRelevance
import com.chronosbvrx.visionplus.data.sports.SportsFavorites
import com.chronosbvrx.visionplus.data.sports.SportsSearch
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cada cuánto se refrescan SOLO los enlaces de los partidos mientras Deportes está al frente. */
internal const val GEE_LINKS_REFRESH_MS = 5L * 60 * 1000

/**
 * El estado de los deportes en vivo (GeeSports): eventos por deporte y grupos de
 * canales 24/7. Arranca en frame 0 con la última copia buena (memoria/archivo) y
 * refresca en segundo plano; los filtros trabajan sobre lo ya cargado.
 */
@Stable
class GeeSportsState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var events by mutableStateOf(GeeSportsSource.cachedEvents(context))
        private set
    var groups by mutableStateOf(GeeSportsSource.cachedGroups(context))
        private set
    var selectedCategory by mutableStateOf(if (DeportesReturn.categoryChosen) DeportesReturn.category else "Football")
        private set
    var selectedDay by mutableStateOf(DeportesReturn.validDay() ?: SportsSource.todayIso())
        private set
    /** false = modo «Principal» (Tier 1); true = todas las ligas y deportes. */
    var showAll by mutableStateOf(DeportesReturn.showAll)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshedAt by mutableLongStateOf(0L)
        private set

    /** Equipos favoritos del perfil (claves normalizadas) y filtro «Favoritos». */
    var favoriteTeams by mutableStateOf(SportsFavorites.teams(context))
        private set
    var favoritesOnly by mutableStateOf(false)
        private set

    /** Búsqueda local por equipo/liga/cadena (vacía = todos). */
    var searchQuery by mutableStateOf("")
        private set

    /**
     * El reloj de la agenda: las fases (en vivo / comienzan pronto / terminados) se
     * recalculan solas al pasar el tiempo y el candado de «aún no comienza» se abre
     * al llegar la hora, sin tocar nada ni esperar datos nuevos.
     */
    var clockMs by mutableLongStateOf(GeeAgenda.currentNow())
        private set

    /**
     * Lo que la app comprobó AHORA de cada señal (GET corto al manifiesto). Manda sobre el
     * dato del servidor, que puede tener minutos: así una caída se ve al momento y un
     * levantamiento también.
     */
    var checks by mutableStateOf<Map<String, GeeHealth.Check>>(emptyMap())
        private set

    /** Los canales de la casa (catálogo XTV): respaldo de una cadena que la fuente no puede servir. */
    var xtvChannels by mutableStateOf(X4KApi.cachedLiveChannels(context))
        private set

    init {
        if (!DeportesReturn.categoryChosen) {
            DeportesReturn.category = selectedCategory
            DeportesReturn.categoryChosen = true
        }
        // La copia en disco puede no tener el deporte elegido (o haber quedado en
        // una liga secundaria): se corrige antes del primer frame.
        ensureCategory()
        // Al abrir Deportes se pide la copia fresca: el CMS puede cambiar la lista
        // de canales del evento (link_names) entre ciclos y no queremos enseñar
        // una lista vieja durante el TTL de 8 min. Sin red se conserva la caché.
        // En Robolectric no se toca la red: los tests siembran el archivo y deben ver
        // exactamente esos eventos (si no, el VPS real los reemplaza y el test se vuelve
        // dependiente de la agenda del día).
        if (!isRobolectric()) refresh(force = true)
    }

    private fun isRobolectric(): Boolean =
        runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess

    /** Los eventos que entran al modo elegido (Principal o todo el catálogo). */
    private fun scoped(event: GeeSportsSource.GeeEvent): Boolean =
        event.visible && (showAll || SportsRelevance.isMain(event))

    /** Deportes disponibles: fútbol primero y el resto por cantidad de eventos. */
    val categories: List<String>
        get() {
            val counts = events.asSequence()
                .filter(::scoped)
                .map { it.category }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
            val sorted = counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
            val football = sorted.filter { it.equals("Football", ignoreCase = true) }
            return football + sorted.filterNot { it.equals("Football", ignoreCase = true) }
        }

    /** Los eventos del día y deporte elegidos (la UI los parte en bloques). */
    val selectedEvents: List<GeeSportsSource.GeeEvent>
        get() = events.filter { event ->
            event.dateIso == selectedDay &&
                scoped(event) &&
                (selectedCategory == null || event.category.equals(selectedCategory, ignoreCase = true)) &&
                (!favoritesOnly || SportsFavorites.matches(event, favoriteTeams)) &&
                SportsSearch.matches(event, searchQuery)
        }

    /** Si el deporte elegido no tiene eventos ese día, cae al primero disponible del día. */
    private fun ensureCategory() {
        val selected = selectedCategory ?: return
        val dayCategories = events.asSequence()
            .filter { it.dateIso == selectedDay && scoped(it) }
            .map { it.category }
            .filter { it.isNotBlank() }
            .toList()
        if (dayCategories.isEmpty()) return
        if (dayCategories.any { it.equals(selected, ignoreCase = true) }) return
        val first = dayCategories.first()
        selectedCategory = first
        DeportesReturn.category = first
    }

    fun toggleFavorite(event: GeeSportsSource.GeeEvent) {
        favoriteTeams = SportsFavorites.toggle(context, event)
    }

    fun isFavorite(event: GeeSportsSource.GeeEvent): Boolean =
        SportsFavorites.matches(event, favoriteTeams)

    fun toggleFavoritesOnly() {
        favoritesOnly = !favoritesOnly
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun clearSearch() {
        searchQuery = ""
    }

    fun pickDay(date: String) {
        selectedDay = date
        DeportesReturn.day = date
        ensureCategory()
    }

    /**
     * Abre el deporte y el día del partido que llega pedido desde FUERA (el héroe del Inicio o
     * un enlace del launcher): sin esto, una selección vieja de la pantalla podría dejar el
     * partido pedido fuera de la vista. Al volver del reproductor NO se usa (la persona
     * regresa a la selección que había hecho).
     */
    fun showEvent(event: GeeSportsSource.GeeEvent) {
        if (event.category.isNotBlank()) selectedCategory = event.category
        if (event.dateIso.isNotBlank()) selectedDay = event.dateIso
        // Un partido pedido desde fuera debe verse aunque el filtro anterior lo esconda.
        favoritesOnly = false
        searchQuery = ""
        if (!SportsRelevance.isMain(event) && !showAll) {
            showAll = true
            DeportesReturn.showAll = true
        }
        DeportesReturn.category = selectedCategory
        DeportesReturn.categoryChosen = true
        DeportesReturn.day = selectedDay
    }

    fun selectCategory(category: String?) {
        selectedCategory = if (selectedCategory == category) null else category
        DeportesReturn.category = selectedCategory
        DeportesReturn.categoryChosen = true
    }

    /** «Principal» ↔ «Todas las ligas»: el catálogo completo sigue a un toque. */
    fun toggleShowAll() {
        showAll = !showAll
        DeportesReturn.showAll = showAll
        ensureCategory()
    }

    /** Avanza el reloj de la agenda (lo llama el tic de la pantalla). */
    fun tick(nowMs: Long = GeeAgenda.currentNow()) {
        if (nowMs > clockMs) clockMs = nowMs
    }

    /** El estado efectivo de una señal: lo comprobado AHORA manda sobre el dato del servidor. */
    fun aliveOf(link: GeeSportsSource.GeeLink): Boolean? =
        GeeHealth.effective(link.alive, checks[link.link.trim()])

    /**
     * Comprueba AHORA todas las señales de UN evento (el que se acaba de abrir en el
     * selector). El vigilante global tiene tope por pasada; esto garantiza que el
     * partido que la persona está mirando no herede un «Caído» viejo del servidor
     * cuando la señal sí responde (caso reportado con GeeSports).
     */
    suspend fun refreshChecksFor(event: GeeSportsSource.GeeEvent) {
        val urls = event.links.asSequence()
            .map { it.link.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        if (urls.isEmpty()) return
        val fresh = GeeProbe.pingAll(urls)
        if (fresh.isNotEmpty()) {
            checks = checks + fresh
            android.util.Log.i(
                "GeeWatch",
                "comprobación del partido: ${fresh.size} señales, vivas ${fresh.values.count { it.alive }}",
            )
        }
    }

    /**
     * El vigilante de vida: la pantalla lo llama y corre mientras esté abierta. Comprueba sin
     * parar las señales de los partidos que se están jugando —vivas y caídas— para que el
     * selector sepa al instante si una opción murió (y ofrezca el respaldo) o si se levantó.
     */
    suspend fun watchSignals(intervalMs: Long = GeeHealth.INTERVAL_MS) {
        while (true) {
            val urls = GeeHealth.urlsToWatch(events, groups)
            if (urls.isNotEmpty()) {
                val fresh = GeeProbe.pingAll(urls)
                if (fresh.isNotEmpty()) {
                    checks = checks + fresh
                    android.util.Log.i(
                        "GeeWatch",
                        "vigilante: ${fresh.size} señales comprobadas, vivas ${fresh.values.count { it.alive }}",
                    )
                }
            }
            delay(intervalMs)
        }
    }

    fun refresh(force: Boolean = false) {
        if (isRobolectric()) {
            loading = false
            return
        }
        scope.launch {
            loading = true
            val freshGroups = withContext(Dispatchers.IO) {
                runCatching { GeeSportsSource.refreshGroups(context, force) }.getOrDefault(emptyList())
            }
            val freshEvents = withContext(Dispatchers.IO) {
                runCatching { GeeSportsSource.refreshEvents(context, force) }.getOrDefault(emptyList())
            }
            // Los canales de la casa solo se piden si la copia en memoria/archivo aún no está.
            val freshXtv = if (xtvChannels.isEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching { X4KApi.liveChannelsFor(context) }.getOrDefault(emptyList())
                }
            } else {
                emptyList()
            }
            if (freshGroups.isNotEmpty()) groups = freshGroups
            if (freshEvents.isNotEmpty()) {
                events = freshEvents
                ensureCategory()
            }
            if (freshXtv.isNotEmpty()) xtvChannels = freshXtv
            refreshedAt = System.currentTimeMillis()
            loading = false
        }
    }

    /**
     * Refresca SOLO los enlaces/eventos de GeeSports. Es el pase periódico de Deportes:
     * no vuelve a bajar los grupos 24/7 (TTL 30 min) y mantiene el selector al día.
     */
    fun refreshLinks(force: Boolean = false) {
        if (isRobolectric()) return
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { GeeSportsSource.refreshEvents(context, force) }.getOrDefault(emptyList())
            }
            if (fresh.isNotEmpty()) {
                events = fresh
                ensureCategory()
            }
            refreshedAt = System.currentTimeMillis()
        }
    }
}

@Composable
fun rememberGeeSportsState(): GeeSportsState {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { GeeSportsState(app, scope) }
}

/**
 * Recuerdo del partido con el modal de opciones abierto: al volver del
 * reproductor (Atrás), el modal reaparece en vez de caer a la lista, para
 * poder elegir otro canal sin buscar de nuevo el partido.
 *
 * También guarda la SELECCIÓN de la pantalla (deporte, día, liga, pestaña
 * «Canales») y el enlace que se estaba viendo: al volver se restaura el mismo
 * menú, con la opción que se sintonizó enfocada («la selección que hiciste»).
 * Es memoria del proceso: si la app muere, todo vuelve a los valores por defecto.
 */
internal object DeportesReturn {
    var pickingId: String? = null

    // --- La selección de la pantalla (sobrevive al viaje al reproductor) -------------------
    var category: String? = null
    var categoryChosen: Boolean = false
    var day: String? = null
    var league: String? = null
    var channels: Boolean = false
    /** Se conserva al volver del reproductor: «Principal» o «Todas las ligas». */
    var showAll: Boolean = false

    /** El partido que se estaba viendo y el enlace sintonizado (uno solo: el último). */
    private var watchingEventId: String? = null
    var watchingUrl: String? = null
        private set

    /** Se llama al tocar una transmisión: al volver, ese partido y esa opción mandan. */
    fun rememberWatching(eventId: String, url: String) {
        watchingEventId = eventId
        watchingUrl = url
    }

    /**
     * El partido al que hay que volver (una sola vez: la pantalla lo consume al montar).
     * El enlace se conserva hasta que el modal se cierra o se abre otro partido.
     */
    fun consumeWatchingEvent(): String? {
        val out = watchingEventId
        watchingEventId = null
        return out
    }

    /** Un día guardado que ya pasó no sirve: la agenda de hoy es la que importa. */
    fun validDay(today: String = SportsSource.todayIso()): String? {
        val stored = day ?: return null
        val tomorrow = SportsSource.shift(today, 1)
        return when {
            stored < today -> today
            stored > tomorrow -> tomorrow
            else -> stored
        }
    }

    /** La señal cambió dentro del reproductor: al volver, el modal marca la que se está viendo. */
    fun updateWatchingUrl(url: String) {
        if (watchingUrl != null && url.isNotBlank()) watchingUrl = url
    }

    /** Se cierra el modal (o se elige otro partido): la marca de «Viendo» deja de aplicar. */
    fun forgetWatching() {
        watchingEventId = null
        watchingUrl = null
    }

    /**
     * Solo pruebas: los objetos de proceso cruzan clases de test dentro del mismo JVM
     * (Robolectric no los reinicia) y una selección sembrada por un test contaminaría al
     * siguiente. En producción nunca se llama.
     */
    internal fun resetForTests() {
        pickingId = null
        category = null
        categoryChosen = false
        day = null
        league = null
        channels = false
        showAll = false
        forgetWatching()
    }
}
