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
            if (query.brand.isBlank()) continue
            var best: TvOption? = null
            for (group in groups) {
                for (channel in group.channels) {
                    for (link in channel.links) {
                        val title = link.name.ifBlank { channel.name }
                        val info = SportsLinker.extractBrandInfo(title)
                        if (info.brand != query.brand) continue
                        if (normalizeEdition(info.edition) != normalizeEdition(query.edition)) continue
                        var score = 100
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
        return out
    }

    /** La edición «1» es la señal base: para emparejar vale como sin edición. */
    private fun normalizeEdition(edition: String?): String = if (edition == "1") "" else edition.orEmpty()
}
