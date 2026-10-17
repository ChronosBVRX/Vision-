package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.sports.SportsLinker

/**
 * Cruza la guía del partido (la parrilla oficial: «Fox Sports», «TUDN», «Azteca 7»…)
 * con los canales 24/7 del catálogo de la fuente deportiva (sus grupos): devuelve el
 * enlace directo del canal que de verdad lo transmite — «Fox Sports» → FOX SPORTS 1
 * MX, «TUDN» → TUDN MX — listo para reproducir sin intermediarios.
 *
 * Cuidado con la marca FOX (conflicto real en México): el canal general «Fox» (red
 * Disney) jamás debe resolver como «Fox Sports» (Grupo Lauman) ni como «FOX DEPORTES»
 * (señal de EE. UU.) — cada marca va por separado.
 */
object GeeGuide {

    data class TvOption(
        val guideName: String,
        val title: String,
        val link: GeeSportsSource.GeeLink,
        val score: Int,
    )

    /**
     * Alias de guía → nombre en el catálogo. El caso estrella: «Caliente TV» se
     * convirtió en «FOX One» en México (Fox Corporation la adquirió; la marca
     * Caliente desapareció el 1-dic-2025) — cuando su catálogo traiga FOX One,
     * el partido se ofrece solo.
     */
    private val GUIDE_ALIASES = mapOf(
        "caliente tv" to "fox one",
        "caliente sports" to "fox one",
        "caliente" to "fox one",
    )

    /**
     * Para cada entrada de la guía, el mejor enlace del catálogo 24/7 que le
     * corresponde (misma marca y edición; se prefiere la señal mexicana y la viva).
     * Puro: la UI solo pinta el resultado.
     */
    fun channelsFor(
        guide: List<String>,
        groups: List<GeeSportsSource.GeeGroup>,
    ): List<TvOption> {
        if (guide.isEmpty() || groups.isEmpty()) return emptyList()
        val out = ArrayList<TvOption>()
        for (guideName in guide) {
            val query = SportsLinker.extractBrandInfo(guideName)
            val alias = GUIDE_ALIASES[SportsLinker.norm(guideName)]
            if (query.brand.isBlank() && alias == null) continue
            var best: TvOption? = null
            for (group in groups) {
                for (channel in group.channels) {
                    for (link in channel.links) {
                        val title = link.name.ifBlank { channel.name }
                        val matches = if (alias != null && !directBrandMatches(query, title)) {
                            SportsLinker.norm(title).contains(alias)
                        } else {
                            directBrandMatches(query, title)
                        }
                        if (!matches) continue
                        var score = if (alias != null && !directBrandMatches(query, title)) 90 else 100
                        val info = SportsLinker.extractBrandInfo(title)
                        score += when {
                            query.targetCountry != null ->
                                if (info.targetCountry == query.targetCountry) 40 else 0
                            info.targetCountry == "México" -> 30
                            info.targetCountry == null -> 10
                            else -> 0
                        }
                        if (link.alive == true) score += 15
                        if (best == null || score > best.score) {
                            best = TvOption(guideName = guideName, title = title, link = link, score = score)
                        }
                    }
                }
            }
            if (best != null) out.add(best)
        }
        return dedupeOptions(out)
    }

    /**
     * Un solo elemento por canal (`title` + enlace). Dos entradas de la guía que
     * son marcas distintas pero resuelven al MISMO canal —«ESPN» y «ESPN+»,
     * «Canal 5» y «Canal 5 Televisa», «FOX One» y «Fox Sports»— producían dos
     * tarjetas idénticas y el `LazyColumn` del selector reventaba con
     * `IllegalArgumentException` al medir (clave repetida). Se conserva la
     * primera posición y, si aparece otra igual, la de mejor puntuación.
     */
    internal fun dedupeOptions(options: List<TvOption>): List<TvOption> {
        val best = LinkedHashMap<Pair<String, String>, TvOption>(options.size)
        for (option in options) {
            val key = option.title to option.link.link
            val current = best[key]
            if (current == null || option.score > current.score) best[key] = option
        }
        return best.values.toList()
    }

    private fun directBrandMatches(query: SportsLinker.BrandInfo, title: String): Boolean {
        if (query.brand.isBlank()) return false
        val info = SportsLinker.extractBrandInfo(title)
        return info.brand == query.brand &&
            normalizeEdition(info.edition) == normalizeEdition(query.edition)
    }

    /** La edición «1» es la señal base: para emparejar vale como sin edición. */
    private fun normalizeEdition(edition: String?): String = if (edition == "1") "" else edition.orEmpty()
}
