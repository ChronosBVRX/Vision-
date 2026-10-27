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
import com.chronosbvrx.visionplus.data.sports.SportsSource
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var loading by mutableStateOf(false)
        private set
    var refreshedAt by mutableLongStateOf(0L)
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
        refresh()
    }

    /** Deportes disponibles: fútbol primero y el resto por cantidad de eventos. */
    val categories: List<String>
        get() {
            val counts = events.map { it.category }
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
                (selectedCategory == null || event.category.equals(selectedCategory, ignoreCase = true))
        }

    fun pickDay(date: String) {
        selectedDay = date
        DeportesReturn.day = date
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
        DeportesReturn.category = selectedCategory
        DeportesReturn.categoryChosen = true
        DeportesReturn.day = selectedDay
    }

    fun selectCategory(category: String?) {
        selectedCategory = if (selectedCategory == category) null else category
        DeportesReturn.category = selectedCategory
        DeportesReturn.categoryChosen = true
    }

    /** Avanza el reloj de la agenda (lo llama el tic de la pantalla). */
    fun tick(nowMs: Long = GeeAgenda.currentNow()) {
        if (nowMs > clockMs) clockMs = nowMs
    }

    /** El estado efectivo de una señal: lo comprobado AHORA manda sobre el dato del servidor. */
    fun aliveOf(link: GeeSportsSource.GeeLink): Boolean? =
        GeeHealth.effective(link.alive, checks[link.link.trim()])

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
            if (freshEvents.isNotEmpty()) events = freshEvents
            if (freshXtv.isNotEmpty()) xtvChannels = freshXtv
            refreshedAt = System.currentTimeMillis()
            loading = false
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
        return if (stored < today) today else stored
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
        forgetWatching()
    }
}
