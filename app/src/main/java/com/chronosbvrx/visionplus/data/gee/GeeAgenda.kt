package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.gee.GeeSportsSource.GeeEvent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * La agenda de "Deportes en vivo": convierte la lista de eventos de GeeSports en
 * bloques listos para pintar —primero los que ESTÁN EN VIVO, luego los que
 * COMIENZAN PRONTO y al final los TERMINADOS con su marcador— y ordena las ligas
 * con protagonismo para el fútbol (Liga MX y las grandes al frente).
 *
 * Todo aquí es puro y probado: la UI solo pinta lo que esta capa decide.
 */
object GeeAgenda {

    enum class Phase { Live, Soon, Done }

    data class Section(val title: String, val events: List<GeeEvent>)

    /** Ligas con protagonismo (chips y orden). Liga MX siempre primero. */
    val MAIN_LEAGUES = com.chronosbvrx.visionplus.home.contract.SportsLeagues.MAIN_LEAGUES

    private val MX_ZONE: TimeZone = TimeZone.getTimeZone("America/Mexico_City")
    private val startCache = ConcurrentHashMap<String, Long>()

    /** Cuánto dura la ventana en la que un partido ya anunciado se considera en curso. */
    private const val LIVE_WINDOW_MS = 3L * 60 * 60 * 1_000

    /**
     * Solo para pruebas: fija el reloj de la agenda para que los fixtures de horario no
     * dependan de la hora a la que corra la suite. En producción JAMÁS se toca.
     */
    internal var clockForTestsMs: Long? = null

    /** El instante actual: el reloj real (o el fijado por una prueba). */
    fun currentNow(): Long = clockForTestsMs ?: System.currentTimeMillis()

    /** Sin tildes, en minúsculas: para comparar ligas/alias. */
    internal fun fold(text: String): String {
        val lowered = text.lowercase(Locale.ROOT)
        val out = StringBuilder(lowered.length)
        for (ch in lowered) {
            when (ch) {
                'á', 'à', 'ä', 'â', 'ã', 'å' -> out.append('a')
                'é', 'è', 'ë', 'ê' -> out.append('e')
                'í', 'ì', 'ï', 'î' -> out.append('i')
                'ó', 'ò', 'ö', 'ô', 'õ' -> out.append('o')
                'ú', 'ù', 'ü', 'û' -> out.append('u')
                'ñ' -> out.append('n')
                'ç' -> out.append('c')
                else -> out.append(ch)
            }
        }
        return out.toString().trim()
    }

    /** El instante de inicio (hora de México) o null si el CMS no trae fecha usable. */
    fun startMs(event: GeeEvent): Long? {
        if (event.dateIso.isBlank()) return null
        // La clave lleva el horario: si el CMS corrige una hora, no se queda la vieja.
        val key = "${event.id}|${event.dateIso} ${event.time}"
        val hit = startCache[key]
        if (hit != null) return if (hit > 0) hit else null
        val parsed = runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.timeZone = MX_ZONE
            fmt.isLenient = false
            fmt.parse("${event.dateIso} ${event.time.ifBlank { "00:00:00" }}")?.time ?: 0L
        }.getOrDefault(0L)
        startCache[key] = parsed
        return if (parsed > 0) parsed else null
    }

    /** La liga del evento: la que cruza la agenda o el nombre del CMS sin la cola. */
    fun leagueLabel(event: GeeEvent): String =
        com.chronosbvrx.visionplus.home.contract.SportsLeagues.canonical(
            event.leagueLabel.ifBlank { event.name.substringBefore(',').trim() },
        )

    /** Menor = más protagonismo (las ligas grandes al frente). */
    fun leaguePriority(label: String): Int =
        com.chronosbvrx.visionplus.home.contract.SportsLeagues.leaguePriority(label)

    /**
     * Las ligas presentes entre los eventos, con las grandes al frente.
     * Con [mainOnly] solo salen las de Tier 1 (modo «Principal»).
     */
    fun leagueNames(events: List<GeeEvent>, mainOnly: Boolean = false): List<String> = events
        .asSequence()
        .filter { !mainOnly || SportsRelevance.isMain(it) }
        .map { leagueLabel(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(compareBy({ leaguePriority(it) }, { it }))
        .toList()

    /** Logo de cada liga presente (el primero que lo traiga). */
    fun leagueLogos(events: List<GeeEvent>): Map<String, String> = buildMap {
        for (event in events) {
            val name = leagueLabel(event)
            val logo = event.leagueLogo.trim()
            if (name.isNotBlank() && logo.isNotBlank() && !containsKey(name)) put(name, logo)
        }
    }

    /** ¿Este evento es del filtro de liga elegido? (null = todas). */
    fun matchesLeague(event: GeeEvent, league: String?): Boolean =
        league.isNullOrBlank() || fold(leagueLabel(event)) == fold(league)

    /**
     * La fase del evento: manda el estado real (en vivo / terminado) y, cuando la agenda
     * llega tarde, manda el reloj.
     *
     * El candado se abre SOLO con el reloj: el sincronizado de la agenda (ESPN) tarda
     * unos minutos en pasar de «pre» a «in», y mientras tanto el partido quedaba
     * bloqueado pasada su hora de inicio. Ese era el candado que no se desbloqueaba.
     */
    fun phaseOf(event: GeeEvent, nowMs: Long = currentNow()): Phase {
        if (event.live) return Phase.Live
        if (event.status == "post") return Phase.Done
        val start = startMs(event)
        if (start != null) {
            return when {
                nowMs >= start && nowMs < start + LIVE_WINDOW_MS -> Phase.Live
                nowMs < start -> Phase.Soon
                else -> Phase.Done
            }
        }
        // Sin horario usable el estado de la agenda es lo único que queda.
        return if (event.status == "in") Phase.Live else Phase.Soon
    }

    /**
     * Solo un partido que ya está EN VIVO se puede reproducir: los que aún no
     * comienzan (o ya terminaron) quedan bloqueados y al abrirlos solo se muestra
     * DÓNDE se transmiten. La regla es la MISMA que arma el bloque «En vivo», para
     * que la lista y el reproductor jamás se contradigan.
     */
    fun playable(event: GeeEvent, nowMs: Long = currentNow()): Boolean =
        phaseOf(event, nowMs) == Phase.Live

    /** El aviso del selector cuando el partido todavía no se puede ver (vacío si está en vivo). */
    fun blockNote(event: GeeEvent, nowMs: Long = currentNow()): String =
        when (phaseOf(event, nowMs)) {
            Phase.Live -> ""
            Phase.Soon -> "Aún no comienza · podrás verlo cuando el partido esté en vivo."
            Phase.Done -> "El partido ya terminó."
        }

    /**
     * Los tres bloques de la pantalla (los vacíos se omiten): EN VIVO arriba,
     * COMIENZAN PRONTO después y TERMINADOS al final con su marcador. En vivo y
     * terminados se ordenan por liga (Liga MX primero) y los próximos por hora.
     */
    fun sections(events: List<GeeEvent>, nowMs: Long = currentNow()): List<Section> {
        val grouped = events.groupBy { phaseOf(it, nowMs) }
        val live = (grouped[Phase.Live] ?: emptyList()).sortedWith(
            compareBy({ leaguePriority(leagueLabel(it)) }, { startMs(it) ?: Long.MAX_VALUE }, { it.name }),
        )
        val soon = (grouped[Phase.Soon] ?: emptyList()).sortedWith(
            compareBy({ startMs(it) ?: Long.MAX_VALUE }, { leaguePriority(leagueLabel(it)) }, { it.name }),
        )
        val done = (grouped[Phase.Done] ?: emptyList()).sortedWith(
            compareByDescending<GeeEvent> { startMs(it) ?: 0L }
                .thenBy { leaguePriority(leagueLabel(it)) }
                .thenBy { it.name },
        )
        return buildList {
            if (live.isNotEmpty()) add(Section("En vivo", live))
            if (soon.isNotEmpty()) add(Section("Comienzan pronto", soon))
            if (done.isNotEmpty()) add(Section("Terminados", done))
        }
    }
}
