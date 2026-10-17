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
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.data.sports.SportsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    fun refresh(force: Boolean = false) {
        scope.launch {
            loading = true
            val freshGroups = withContext(Dispatchers.IO) {
                runCatching { GeeSportsSource.refreshGroups(context, force) }.getOrDefault(emptyList())
            }
            val freshEvents = withContext(Dispatchers.IO) {
                runCatching { GeeSportsSource.refreshEvents(context, force) }.getOrDefault(emptyList())
            }
            if (freshGroups.isNotEmpty()) groups = freshGroups
            if (freshEvents.isNotEmpty()) events = freshEvents
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
