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
    var selectedCategory by mutableStateOf<String?>(null)
        private set
    var selectedDay by mutableStateOf(SportsSource.todayIso())
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
        selectCategory("Football")
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
    }

    fun selectCategory(category: String?) {
        selectedCategory = if (selectedCategory == category) null else category
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
 */
internal object DeportesReturn {
    var pickingId: String? = null
}
