package com.chronosbvrx.visionplus.data

import com.chronosbvrx.visionplus.data.sources.RemoteIndexSource.Airing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * «Porque viste X»: el título base del último recuerdo y las tarjetas elegidas.
 * La fila no se pinta cuando [films] queda corto (mejor ausente que mutilada).
 */
internal data class BecauseRow(val title: String, val films: List<Film>)

/** Una emisión de una serie seguida, ya cruzada con su ficha del catálogo. */
internal data class FollowAiring(
    val film: Film,
    val airing: Airing,
    val label: String,
    val today: Boolean = false,
) {
    /** Clave única de la LazyRow: la misma serie puede tener varias emisiones. */
    val key: String get() = "${film.id}-${airing.season}-${airing.episode}"
}

/**
 * Las dos filas personales del Inicio, puras para poder blindarlas:
 *
 * - «Porque viste X» reusa [Related] (mismo género/tipo, sin repetir la obra) con el
 *   último recuerdo de [PlaybackHistory]. No hace ninguna llamada de red: trabaja con
 *   las hileras que el Inicio ya tiene en memoria.
 * - «Tus series · Esta semana» cruza la agenda del servidor ([Airing]) con las series
 *   que el usuario sigue en [FollowStore], y solo devuelve las que existen en el
 *   catálogo cargado (una tarjeta que no abre sería peor que no pintarla).
 *
 * Jamás lanza: sin datos devuelve null/lista vacía y la pantalla simplemente no pinta.
 */
object HomePersonal {

    /** Menos de esto no vale la pena una fila: se vería recortada. */
    internal const val MIN_CARDS = 6

    /** La agenda se reusa este tiempo (el Inicio puede abrirse varias veces al día). */
    internal const val SCHEDULE_TTL_MS = 6L * 60 * 60 * 1000

    /** Ventana de «Esta semana». */
    internal const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Margen de gracia para incluir lo que empezó hace un rato y sigue siendo «hoy». */
    internal const val AIRING_GRACE_MS = 2L * 60 * 60 * 1000

    internal const val FOLLOW_ROW_TITLE = "Tus series · Esta semana"

    internal const val MX_ZONE = "America/Mexico_City"

    internal fun becauseRowTitle(name: String): String = "Porque viste ${name.trim()}"

    /**
     * Elige hasta [limit] títulos para «Porque viste X».
     *
     * @param seed el último recuerdo del historial (película o capítulo).
     * @param films el catálogo ya cargado, para encontrar la ficha de la semilla.
     * @param rows las hileras del índice (géneros incluidos) para elegir similares.
     * @param excludeIds / [excludeTmdb] lo que ya está en «Seguir viendo» (no se repite).
     * @param kidsOnly modo niños: la semilla y las tarjetas deben ser infantiles.
     */
    internal fun becauseYouWatched(
        seed: ResumeEntry?,
        films: List<Film>,
        rows: List<CatalogRow>,
        excludeIds: Set<String> = emptySet(),
        excludeTmdb: Set<Int> = emptySet(),
        kidsOnly: Boolean = false,
        limit: Int = 15,
    ): BecauseRow? {
        if (seed == null || films.isEmpty() || rows.isEmpty()) return null
        val display = becauseTitle(seed)
        // El recuerdo puede venir de un id que el índice ya no trae (ficha retirada): el
        // nombre exacto sigue siendo una semilla honesta. Sin coincidencia no hay fila.
        val current = matchCards(listOf(seed), films).firstOrNull()?.film
            ?: films.firstOrNull {
                display.isNotBlank() && normalizeDisplayName(it.title).equals(display, ignoreCase = true)
            }
            ?: return null
        if (kidsOnly && current.kind != "kids") return null
        val title = display.ifBlank { current.title }
        if (title.isBlank()) return null
        // Si la ficha no trae géneros, las hileras que la contienen los revelan.
        val hint = if (current.genres.isNotEmpty()) {
            current.genres.joinToString(", ")
        } else {
            Related.inferGenreHint(current, rows)
        }
        val picked = Related.pickRelated(current, rows, hint, limit = limit * 2)
            .asSequence()
            .filter { it.id !in excludeIds }
            .filter { it.tmdbId <= 0 || it.tmdbId !in excludeTmdb }
            // «Relacionadas» no mezcla cine con series: la fila habla del tipo que viste.
            .filter { current.kind.isBlank() || it.kind.isBlank() || it.kind == current.kind }
            .filter { !kidsOnly || it.kind == "kids" }
            .take(limit)
            .toList()
        if (picked.size < MIN_CARDS) return null
        return BecauseRow(title, picked)
    }

    /** El nombre que el usuario reconoce: «Shōgun · T1 · C1» → «Shōgun». */
    internal fun becauseTitle(seed: ResumeEntry): String =
        normalizeDisplayName(seed.title)
            .substringBefore(" · T")
            .substringBefore(" · C")
            .trim()

    /**
     * Las emisiones de series seguidas que sí están en el catálogo, una por serie y
     * ordenadas por fecha. Descarta las que no tienen hora (no podemos decir «hoy»)
     * y las que el catálogo no conoce (no habría ficha que abrir).
     */
    internal fun weeklyFollowed(
        followed: Set<String>,
        airings: List<Airing>,
        films: List<Film>,
        nowMs: Long,
        withinMs: Long = WEEK_MS,
        limit: Int = 12,
    ): List<FollowAiring> {
        if (followed.isEmpty() || airings.isEmpty() || films.isEmpty()) return emptyList()
        val keys = followed.asSequence().map { it.trim() }.filter { it.isNotBlank() }.toHashSet()
        if (keys.isEmpty()) return emptyList()
        val byId = HashMap<String, Film>(films.size)
        films.forEach { byId.putIfAbsent(it.id, it) }
        val from = nowMs - AIRING_GRACE_MS
        val until = nowMs + withinMs
        val seen = HashSet<String>()
        return airings.asSequence()
            .filter { it.airtimeMs in from..until }
            .filter {
                (it.titleId.isNotBlank() && it.titleId in keys) ||
                    (it.imdb.isNotBlank() && it.imdb in keys)
            }
            .sortedBy { it.airtimeMs }
            .mapNotNull { airing ->
                val film = byId[airing.titleId] ?: return@mapNotNull null
                if (!seen.add(film.id)) return@mapNotNull null
                FollowAiring(
                    film = film,
                    airing = airing,
                    label = airingLabel(airing, nowMs),
                    today = isToday(airing.airtimeMs, nowMs),
                )
            }
            .take(limit)
            .toList()
    }

    /** «T2 · C5 · Hoy 21:00» (o «Mañana 21:00», «Dom 25 21:00»). Puro. */
    internal fun airingLabel(
        airing: Airing,
        nowMs: Long,
        zone: TimeZone = TimeZone.getTimeZone(MX_ZONE),
    ): String {
        val base = when {
            airing.season > 0 && airing.episode > 0 -> "T${airing.season} · C${airing.episode}"
            airing.episode > 0 -> "C${airing.episode}"
            else -> ""
        }
        if (airing.airtimeMs <= 0) return base
        val target = Calendar.getInstance(zone).apply { timeInMillis = airing.airtimeMs }
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        val day = when (dayDiff(now, target)) {
            0L -> "Hoy"
            1L -> "Mañana"
            else -> capitalize(dayFormat(zone).format(Date(airing.airtimeMs)))
        }
        val time = timeFormat(zone).format(Date(airing.airtimeMs))
        val whenText = "$day $time"
        return if (base.isBlank()) whenText else "$base · $whenText"
    }

    internal fun isToday(airtimeMs: Long, nowMs: Long, zone: TimeZone = TimeZone.getTimeZone(MX_ZONE)): Boolean {
        if (airtimeMs <= 0) return false
        val a = Calendar.getInstance(zone).apply { timeInMillis = airtimeMs }
        val b = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun dayFormat(zone: TimeZone) = SimpleDateFormat("EEE d", Locale.forLanguageTag("es-MX")).apply {
        timeZone = zone
    }

    private fun timeFormat(zone: TimeZone) = SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = zone
    }

    private fun capitalize(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar().toString() else it.toString() }

    private fun dayDiff(now: Calendar, target: Calendar): Long {
        val a = midnight(now)
        val b = midnight(target)
        return Math.round((b - a).toDouble() / (24 * 60 * 60 * 1000))
    }

    private fun midnight(cal: Calendar): Long = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
