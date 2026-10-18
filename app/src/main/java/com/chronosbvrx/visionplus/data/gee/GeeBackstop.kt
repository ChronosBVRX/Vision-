package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.sports.SportsLinker

/**
 * El respaldo de un canal que la fuente deportiva no puede servir.
 *
 * El partido trae su parrilla oficial («Se transmite en: Telemundo · TUDN · ViX Deportes») y la
 * fuente deportiva resuelve esa parrilla a canales 24/7 con su estado. Cuando una de esas cadenas
 * **no se puede ver** (su canal está caído o la fuente ni la trae), se ofrece ESA MISMA cadena del
 * catálogo de la casa — solo si la coincidencia es del 100 %: misma marca y misma edición, nunca un
 * parecido («TNT Sports» jamás resuelve a «TNT Series»).
 *
 * La fuente deportiva SIEMPRE manda: mientras su opción esté viva no hay respaldo, y en cuanto se
 * recupera vuelve a ser la única primera opción.
 */
object GeeBackstop {

    /** Un canal del catálogo de la casa que cubre una cadena de la parrilla. */
    data class Backstop(
        /** La cadena de la parrilla que no se pudo servir («Telemundo»). */
        val guideName: String,
        val channel: LiveChannel,
    )

    /** Lo que la fuente deportiva ofrece hoy para el partido, con su estado real. */
    data class LiveOption(val name: String, val alive: Boolean)

    /** Tope de respaldos: la lista del modal no debe volverse un maratón. */
    const val MAX_BACKSTOPS = 4

    /**
     * Los respaldos que hacen falta para el partido.
     *
     * @param guide la parrilla oficial del partido, tal cual la publica la agenda.
     * @param liveOptions todo lo que la fuente deportiva puede intentar hoy: sus canales 24/7
     *   resueltos y los enlaces directos del evento, cada uno con su estado de vida.
     * @param xtv los canales del catálogo de la casa (listos para reproducir).
     */
    fun needed(
        guide: List<String>,
        liveOptions: List<LiveOption>,
        xtv: List<LiveChannel>,
    ): List<Backstop> {
        if (guide.isEmpty() || xtv.isEmpty()) return emptyList()
        val out = ArrayList<Backstop>()
        val taken = HashSet<String>()
        for (raw in guide) {
            if (out.size >= MAX_BACKSTOPS) break
            val guideName = raw.trim()
            if (guideName.isEmpty()) continue
            // Ya cubierto por una cadena anterior: una sola fila por cadena de la parrilla.
            if (out.any { SportsLinker.norm(it.guideName) == SportsLinker.norm(guideName) }) continue
            if (sameBrand(guideName) == null) continue // «Peacock», «fuboTV»: no es un canal de TV
            // La fuente deportiva ya la sirve EN VIVO: no se toca (manda siempre).
            if (liveOptions.any { it.alive && covers(guideName, it.name) }) continue
            val pick = bestFor(guideName, xtv) ?: continue
            if (!taken.add(pick.url)) continue
            out.add(Backstop(guideName = guideName, channel = pick))
        }
        return out
    }

    /** El respaldo de una cadena de la parrilla, si lo hay. */
    fun forGuide(backstops: List<Backstop>, guideName: String): Backstop? {
        val needle = SportsLinker.norm(guideName)
        return backstops.firstOrNull { SportsLinker.norm(it.guideName) == needle }
    }

    /** El mejor canal del catálogo para una cadena de la parrilla; null si no hay ninguno del 100 %. */
    private fun bestFor(guideName: String, xtv: List<LiveChannel>): LiveChannel? {
        val query = SportsLinker.extractBrandInfo(guideName)
        var best: LiveChannel? = null
        var bestScore = Int.MIN_VALUE
        for (channel in xtv) {
            if (!covers(guideName, channel.name)) continue
            val score = scoreOf(query, guideName, channel.name)
            if (score > bestScore) {
                bestScore = score
                best = channel
            }
        }
        return best
    }

    /** ¿Este canal cubre EXACTAMENTE la cadena de la parrilla? */
    private fun covers(guideName: String, candidateName: String): Boolean {
        val normalized = SportsLinker.norm(candidateName)
        // Alias de la casa («Caliente TV» → «FOX One»): el candidato debe decir el nombre nuevo.
        ALIASES[SportsLinker.norm(guideName)]?.let { alias ->
            if (normalized.contains(alias)) return true
        }
        val query = SportsLinker.extractBrandInfo(guideName)
        if (query.brand.isBlank()) return false
        val candidate = SportsLinker.extractBrandInfo(candidateName)
        // Los feeds nacionales del mercado hispano («Univision East HD») son la MISMA cadena.
        if (brandOf(candidate) != brandOf(query)) return false
        if (editionOf(query) != editionOf(candidate)) return false
        // Una marca con palabra deportiva exige que el canal también la traiga: «Claro Sports»
        // NO se cubre con «CANAL CLARO» (el canal general) ni «Fox Sports» con «FOX One».
        if (SPORT_WORDS.any { query.brand.contains(it) } && SPORT_WORDS.none { normalized.contains(it) }) return false
        // El país solo se exige cuando la parrilla lo dice («TUDN USA» no se cubre con la señal
        // mexicana); sin país, cualquier señal de la marca vale.
        val wanted = query.targetCountry ?: return true
        return LiveTaxonomy.countryFor(candidateName) == wanted
    }

    /** La marca de la cadena, resolviendo los alias de la casa; null si no es un canal de TV. */
    private fun sameBrand(guideName: String): SportsLinker.BrandInfo? {
        val query = SportsLinker.extractBrandInfo(guideName)
        if (query.brand.isNotBlank()) return query
        val alias = ALIASES[SportsLinker.norm(guideName)] ?: return null
        return SportsLinker.extractBrandInfo(alias)
    }

    /** La edición «1» es la señal base: para emparejar vale como sin edición. */
    private fun editionOf(info: SportsLinker.BrandInfo): String =
        if (info.edition == "1") "" else info.edition.orEmpty()

    /** La marca sin los rótulos de feed nacional («univision east» → «univision»). */
    private fun brandOf(info: SportsLinker.BrandInfo): String =
        info.brand.split(' ').filter { it.isNotBlank() && it !in NATIONAL_FEED }.joinToString(" ")

    /**
     * Desempate determinista entre canales que son la MISMA marca: primero el nombre idéntico,
     * luego el país (el pedido, o México), luego la señal nacional antes que la estación local,
     * y al final la más corta (menos adornos) y alfabética.
     */
    private fun scoreOf(query: SportsLinker.BrandInfo, guideName: String, candidateName: String): Int {
        var score = 0
        if (SportsLinker.norm(guideName) == SportsLinker.norm(candidateName)) score += 400
        val country = LiveTaxonomy.countryFor(candidateName)
        score += when {
            query.targetCountry != null && country == query.targetCountry -> 300
            query.targetCountry != null -> 0
            country == "México" -> 120
            country == "Internacional" -> 60
            else -> 30
        }
        if (NATIONAL_FEED.any { SportsLinker.norm(candidateName).split(' ').contains(it) }) score += 25
        score -= extraTokens(query, candidateName).size * 12
        if (candidateName.contains("FHD", ignoreCase = true)) score += 4
        else if (candidateName.contains("HD", ignoreCase = true)) score += 2
        score -= candidateName.length / 8
        return score
    }

    /** Las palabras que sobran entre la marca y el nombre del canal (sin país ni calidad). */
    private fun extraTokens(query: SportsLinker.BrandInfo, candidateName: String): List<String> {
        val brandTokens = SportsLinker.norm(query.brand).split(' ').filter { it.isNotBlank() }.toSet()
        return SportsLinker.norm(candidateName)
            .split(' ')
            .filter { it.isNotBlank() && it !in brandTokens && it !in NOISE }
    }

    private val NOISE = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "plus", "+", "tv", "canal", "channel",
        "mexico", "mex", "mx", "usa", "us", "east", "west", "este", "oeste", "internacional",
        "argentina", "arg", "ar", "chile", "cl", "colombia", "col", "co", "espana", "esp", "peru", "pe",
    )

    /** Los feeds nacionales del mercado hispano de EE. UU. (antes que sus estaciones locales). */
    private val NATIONAL_FEED = setOf("east", "west", "este", "oeste")

    /** Palabras que marcan una marca DEPORTIVA: el canal también tiene que traerlas. */
    private val SPORT_WORDS = listOf("sport", "deport")

    /**
     * Alias de parrilla → nombre en el catálogo. El caso estrella: «Caliente TV» se convirtió en
     * «FOX One» en México (Fox Corporation la adquirió; la marca Caliente desapareció el
     * 1-dic-2025).
     */
    private val ALIASES = mapOf(
        "caliente tv" to "fox one",
        "caliente sports" to "fox one",
        "caliente" to "fox one",
    )
}
