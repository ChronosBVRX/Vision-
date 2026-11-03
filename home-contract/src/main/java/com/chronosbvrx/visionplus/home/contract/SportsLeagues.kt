package com.chronosbvrx.visionplus.home.contract

import java.text.Normalizer
import java.util.Locale

/**
 * Una sola definición de «deporte principal» para Vision+ y Vision+ Home.
 *
 * La agenda mezcla tres mundos: la lista curada de ESPN, los eventos del CMS de
 * GeeSports y el catálogo de canales. Antes cada capa tenía su propia lista y el
 * hero podía tratar «LaLiga 2» como LaLiga porque comparaba por subcadena. Aquí
 * viven las listas exactas y los alias; el servidor manda `tier` en el payload y
 * esto queda como respaldo para cachés viejas y para el launcher.
 *
 * Tier 1 = portada/principal, Tier 2 = profesional secundario, Tier 3 = nicho.
 */
object SportsLeagues {

    /** Ligas y eventos que merecen la portada y el modo «Principal». */
    val MAIN_LEAGUES: List<String> = listOf(
        "Liga MX",
        "UEFA Champions League",
        "Premier League",
        "LaLiga",
        "Serie A",
        "Bundesliga",
        "Ligue 1",
        "Copa Libertadores",
        "Copa Sudamericana",
        "Liga Profesional Argentina",
        "Brasileirão",
        "MLS",
        "Liga de Expansión MX",
        "Eredivisie",
        "Primeira Liga",
        "UEFA Europa League",
        "Copa del Mundo",
        "Eurocopa",
        "Copa América",
        "Concacaf Nations League",
        "Copa Oro",
        "NBA",
        "NFL",
        "MLB",
        "NHL",
        "Formula 1",
        "MotoGP",
        "NASCAR",
        "UFC",
        "MMA",
        "Boxeo",
    )

    /** Los `league_id` de la agenda ESPN que son principales. */
    val MAIN_LEAGUE_IDS: Set<String> = setOf(
        "mex.1", "mex.2",
        "eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "por.1",
        "uefa.champions", "uefa.europa",
        "conmebol.libertadores", "conmebol.sudamericana",
        "arg.1", "bra.1", "usa.1",
        "fifa.world",
        "basketball/nba", "football/nfl", "baseball/mlb", "hockey/nhl",
    )

    /** Deportes que pueden aparecer en «Principal». */
    val MAIN_SPORTS: Set<String> = setOf(
        "football",
        "basketball",
        "american football",
        "baseball",
        "hockey",
        "motorsports",
        "motorsport",
        "tennis",
        "fighting",
        "boxing",
        "mma",
        "pelea",
        "peleas",
        "boxeo",
    )

    /** Cómo se llaman en el CMS/agenda algunas de las ligas grandes. */
    private val ALIASES: Map<String, String> = mapOf(
        "champions league" to "UEFA Champions League",
        "uefa champions league" to "UEFA Champions League",
        "uefa champions" to "UEFA Champions League",
        "liga profesional de futbol" to "Liga Profesional Argentina",
        "liga profesional argentina" to "Liga Profesional Argentina",
        "ligapro" to "Liga Profesional Argentina",
        "brasileirao" to "Brasileirão",
        "campeonato brasileiro serie a" to "Brasileirão",
        "primeira liga" to "Primeira Liga",
        "liga portugal betclic" to "Primeira Liga",
        "eredivisie" to "Eredivisie",
        "vriendenloterij eredivisie" to "Eredivisie",
        "liga de expansion mx" to "Liga de Expansión MX",
        "liga de expansion" to "Liga de Expansión MX",
        "major league soccer" to "MLS",
        "mls" to "MLS",
        "fifa world cup" to "Copa del Mundo",
        "copa mundial" to "Copa del Mundo",
        "world cup" to "Copa del Mundo",
        "uefa euro" to "Eurocopa",
        "eurocopa" to "Eurocopa",
        "uefa eurocopa" to "Eurocopa",
        "copa america" to "Copa América",
        "concacaf nations league" to "Concacaf Nations League",
        "concacaf gold cup" to "Copa Oro",
        "copa oro" to "Copa Oro",
        "formula one" to "Formula 1",
        "formula 1" to "Formula 1",
        "f1" to "Formula 1",
        "motogp" to "MotoGP",
        "nascar" to "NASCAR",
        "nba" to "NBA",
        "nfl" to "NFL",
        "mlb" to "MLB",
        "nhl" to "NHL",
        "ufc" to "UFC",
        "mixed martial arts" to "MMA",
        "boxing" to "Boxeo",
        "boxeo" to "Boxeo",
    )

    /**
     * Señales de una categoría inferior. Se evalúan DESPUÉS de la lista
     * principal: «Liga de Expansión MX» es principal aunque diga «Expansión».
     */
    private val SECONDARY_MARKERS = listOf(
        " 2", " ii", " b", "women", "femen", "femenil", "u17", "u18", "u19",
        "u20", "u21", "u23", "youth", "juvenil", "reserve", "reservas",
        "championship", "segunda", "primera b", "serie b", "2 bundesliga",
        "ligue 2", "laliga 2", "liga 2", "liga 1 te apuesto", "primera a",
        "primera division", "segunda division", "a-league", "k league",
        "j1 league", "j2 league", "allsvenskan", "super lig", "süper lig",
        "scottish", "russian premier", "kenya premier", "indian super",
        "canadian premier", "nwsl", "wsl", "liga f", "liga mx femenil",
    )

    // Se compilan UNA vez: antes cada evento creaba decenas de Regex y el
    // arranque de la tele se iba a ~50 s (medido en el Chromecast).
    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    private val ALIAS_BY_FOLDED: Map<String, String> =
        ALIASES.mapKeys { (key, _) -> fold(key) }

    private val MAIN_BY_FOLDED: Map<String, String> =
        MAIN_LEAGUES.associateBy { fold(it) }

    private val MAIN_PRIORITY: Map<String, Int> =
        MAIN_LEAGUES.withIndex().associate { (index, name) -> fold(name) to index }

    private val SECONDARY_FOLDED: List<String> =
        SECONDARY_MARKERS.map { fold(it) }.filter { it.isNotBlank() }

    /** Normaliza para comparar: minúsculas, sin acentos y sin puntuación. */
    fun fold(value: String): String {
        val plain = Normalizer.normalize(value, Normalizer.Form.NFD).replace(MARKS, "")
        return plain.lowercase(Locale.ROOT).replace(NON_ALNUM, " ").trim()
    }

    /** El nombre canónico de una liga (o el original si no hay alias). */
    fun canonical(label: String): String {
        val clean = label.substringBefore(',').trim()
        if (clean.isBlank()) return ""
        val folded = fold(clean)
        ALIAS_BY_FOLDED[folded]?.let { return it }
        MAIN_BY_FOLDED[folded]?.let { return it }
        return clean
    }

    /** Posición en la lista principal; las secundarias van después. */
    fun leaguePriority(label: String): Int =
        MAIN_PRIORITY[fold(canonical(label))] ?: (MAIN_LEAGUES.size + 100)

    fun isMainLeague(label: String): Boolean =
        MAIN_BY_FOLDED.containsKey(fold(canonical(label)))

    fun isMainSport(category: String): Boolean =
        MAIN_SPORTS.contains(category.trim().lowercase(Locale.ROOT))

    fun isSecondary(label: String): Boolean {
        val folded = " ${fold(label)} "
        return SECONDARY_FOLDED.any { marker -> folded.contains(" $marker ") }
    }

    /**
     * Tier de un evento. El `leagueId` manda cuando viene de la agenda; si no,
     * se compara el nombre exacto y, al final, las señales de categoría inferior.
     */
    fun tier(
        league: String,
        category: String,
        leagueId: String = "",
    ): Int {
        if (!isMainSport(category)) return 3
        if (leagueId.isNotBlank() && leagueId in MAIN_LEAGUE_IDS) return 1
        val name = canonical(league)
        if (isMainLeague(name)) return 1
        if (isSecondary(name)) return 2
        return 2
    }

    fun isMain(league: String, category: String, leagueId: String = ""): Boolean =
        tier(league, category, leagueId) == 1

    /** Etiqueta corta para la píldora de alcance de la pantalla. */
    fun scopeLabel(all: Boolean): String = if (all) "Todas las ligas" else "Principal"
}
