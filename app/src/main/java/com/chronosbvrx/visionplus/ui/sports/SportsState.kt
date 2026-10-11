package com.chronosbvrx.visionplus.ui.sports

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chronosbvrx.visionplus.data.sports.SportLeague
import com.chronosbvrx.visionplus.data.sports.SportMatch
import com.chronosbvrx.visionplus.data.sports.SportsLinker
import com.chronosbvrx.visionplus.data.sports.SportsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** El estado de la agenda de fútbol: el día elegido, los partidos y su enlace a canales. */
@Stable
class SportsState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var dateIso by mutableStateOf(SportsSource.todayIso())
        private set
    var matches by mutableStateOf<List<SportMatch>>(emptyList())
        private set
    var links by mutableStateOf<Map<String, SportsLinker.Link>>(emptyMap())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedLeagueId by mutableStateOf<String?>(null)
        private set

    init {
        val cached = SportsSource.cached(context, dateIso)
        if (cached.isNotEmpty()) {
            matches = cached
        }
        refresh()
    }

    /** Ligas disponibles en el día seleccionado para el filtro rápido. */
    val availableLeagues: List<SportLeague>
        get() = matches.map { it.league }
            .distinctBy { it.id.ifBlank { it.name } }
            .filter { it.name.isNotBlank() }

    fun leagueMatchCount(leagueId: String): Int =
        matches.count { it.league.id == leagueId }

    /** Los partidos agrupados por liga (respetando el filtro seleccionado). */
    val byLeague: List<Pair<String, List<SportMatch>>>
        get() {
            val subset = if (selectedLeagueId == null) {
                matches
            } else {
                matches.filter { it.league.id == selectedLeagueId }
            }
            return subset.groupBy { it.league.id }.map { (id, list) -> id to list }
        }

    val liveCount: Int get() = matches.count { it.playing }

    fun selectLeague(leagueId: String?) {
        selectedLeagueId = if (selectedLeagueId == leagueId) null else leagueId
    }

    fun pick(date: String) {
        if (date == dateIso) return
        dateIso = date
        val cached = SportsSource.cached(context, date)
        matches = cached
        if (selectedLeagueId != null && cached.none { it.league.id == selectedLeagueId }) {
            selectedLeagueId = null
        }
        error = null
        refresh()
    }

    fun refresh(force: Boolean = false) {
        val date = dateIso
        if (matches.isEmpty()) loading = true
        scope.launch {
            if (matches.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { SportsSource.cached(context, date) }
                if (dateIso == date && cached.isNotEmpty()) {
                    matches = cached
                    val preLinks = withContext(Dispatchers.Default) {
                        SportsLinker.quickResolveAll(context, cached)
                    }
                    if (preLinks.isNotEmpty()) links = preLinks
                }
            }
            val fetched = withContext(Dispatchers.IO) {
                runCatching { SportsSource.matches(context, date, force) }.getOrNull()
            }
            if (dateIso == date) {
                if (fetched != null) {
                    matches = fetched
                    error = null
                    val quick = withContext(Dispatchers.Default) {
                        SportsLinker.quickResolveAll(context, fetched)
                    }
                    if (quick.isNotEmpty()) links = quick
                } else if (matches.isEmpty()) {
                    error = "No se pudo cargar la agenda"
                }
            }
            val list = matches
            if (dateIso == date && list.isNotEmpty()) {
                // Primero los canales candidatos (rápido), luego la confirmación por EPG.
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { SportsLinker.resolve(context, list) }.getOrNull()
                }
                if (dateIso == date && !resolved.isNullOrEmpty()) {
                    links = resolved
                    val confirmed = withContext(Dispatchers.IO) {
                        runCatching { SportsLinker.confirm(context, list, resolved) }.getOrNull()
                    }
                    if (dateIso == date && !confirmed.isNullOrEmpty()) {
                        links = confirmed
                    }
                }
            }
            if (dateIso == date) loading = false
        }
    }
}

@Composable
fun rememberSportsState(): SportsState {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { SportsState(app, scope) }
}
