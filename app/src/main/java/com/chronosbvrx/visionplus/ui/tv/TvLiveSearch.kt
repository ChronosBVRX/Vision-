package com.chronosbvrx.visionplus.ui.tv

import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Búsqueda de canales de TV en vivo tolerante a errores y a nombres aproximados.
 *
 * El usuario no siempre escribe «CANAL 5 HD»: puede teclear «canal5», «tdn»,
 * «fox sport», «star chanel» o «win plus». La búsqueda normaliza acentos,
 * puntuación, calidad y el «+», y luego combina coincidencia exacta, prefijo,
 * tokens y distancia de edición pequeña. El país también cuenta: «mexico» o
 * «fox mexico» priorizan las señales de México.
 *
 * Es pura para poder probarla sin red ni Compose.
 */

private val QUALITY = Regex("""\b(fhd|uhd|hd|sd|4k|h265|hevc|h264|1080p|720p|540p|480p)\b""")
private val NON_ALNUM = Regex("[^a-z0-9]+")
private val MARKS = Regex("\\p{Mn}+")

internal fun liveSearchNormalize(text: String): String {
    val lowered = text.lowercase(Locale.ROOT).replace("+", " plus ")
    val noMarks = Normalizer.normalize(lowered, Normalizer.Form.NFD).replace(MARKS, "")
    return noMarks.replace(QUALITY, " ").replace(NON_ALNUM, " ").trim()
}

private fun countryAliases(country: String): List<String> = when (country) {
    "México" -> listOf("mexico", "mx", "mexicano", "mexicana")
    "EE. UU." -> listOf("ee uu", "estados unidos", "usa", "eua", "us", "americano", "americana")
    "R. Dominicana" -> listOf("republica dominicana", "rep dominicana", "rd", "dominicana")
    "Costa Rica" -> listOf("costa rica", "cr")
    "El Salvador" -> listOf("el salvador", "salvador", "sv")
    "Puerto Rico" -> listOf("puerto rico", "pr")
    "España" -> listOf("espana", "es")
    "Perú" -> listOf("peru", "pe")
    "Canadá" -> listOf("canada", "ca")
    "Brasil" -> listOf("brasil", "brazil", "br")
    else -> listOf(liveSearchNormalize(country))
}

/** Distancia de Levenshtein acotada: si se pasa del tope, no importa el valor exacto. */
private fun editDistance(a: String, b: String, limit: Int): Int {
    if (abs(a.length - b.length) > limit) return limit + 1
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            if (current[j] < rowMin) rowMin = current[j]
        }
        if (rowMin > limit) return limit + 1
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

private fun tokenMatches(query: String, target: String): Boolean {
    if (query == target) return true
    if (query.length < 3 || target.length < 3) return false
    if (target.startsWith(query) || query.startsWith(target)) return true
    val limit = when {
        query.length <= 4 -> 1
        query.length <= 7 -> 2
        else -> 3
    }
    return editDistance(query, target, limit) <= limit
}

/** Puntúa un canal. 0 = no coincide; más alto = mejor. */
internal fun liveSearchScore(name: String, query: String, country: String = ""): Int {
    val q = liveSearchNormalize(query)
    if (q.isBlank()) return 100
    val n = liveSearchNormalize(name)
    if (n.isBlank()) return 0
    val qCompact = q.replace(" ", "")
    val nCompact = n.replace(" ", "")

    var score = when {
        nCompact == qCompact -> 1000
        nCompact.startsWith(qCompact) -> 900
        nCompact.contains(qCompact) -> 760
        else -> 0
    }

    val qTokens = q.split(' ').filter { it.isNotBlank() }
    val nTokens = n.split(' ').filter { it.isNotBlank() }
    val matched = qTokens.count { qt -> nTokens.any { nt -> tokenMatches(qt, nt) } }
    if (qTokens.isNotEmpty() && matched == qTokens.size) {
        score = max(score, 650 - (nTokens.size - qTokens.size).coerceAtLeast(0) * 8)
    } else if (matched > 0) {
        score = max(score, 240 + (matched * 100 / qTokens.size))
    }

    if (score == 0) {
        val limit = when {
            qCompact.length <= 4 -> 1
            qCompact.length <= 8 -> 2
            else -> 3
        }
        val distance = editDistance(qCompact, nCompact, limit)
        if (distance <= limit) score = 500 - distance * 30
    }

    // El país es parte de la ficha: «mexico», «usa», «chile»…
    if (q.length >= 3) {
        val aliases = countryAliases(country)
        if (aliases.any { alias ->
                alias == q || (alias.length >= 3 && (alias.startsWith(q) || q.startsWith(alias)))
            }
        ) {
            score = max(score, 430)
        }
    }
    return score
}

/**
 * Canales que aparecen al escribir. Devuelve la lista ya ordenada por
 * coincidencia, popularidad y país. Los resultados son la vez predicción y
 * selector: la UI puede mostrar los primeros como «Quizá».
 */
internal fun liveSearchResults(
    channels: List<LiveChannel>,
    query: String,
    selectedCountry: String? = null,
): List<LiveChannel> {
    return channels.asSequence()
        .filter { selectedCountry == null || LiveTaxonomy.countryFor(it.name) == selectedCountry }
        .map { channel ->
            val country = LiveTaxonomy.countryFor(channel.name)
            val score = liveSearchScore(channel.name, query, country)
            channel to score
        }
        .filter { (_, score) -> score > 0 }
        .distinctBy { (channel, _) -> channel.url }
        .distinctBy { (channel, _) -> LiveTaxonomy.channelKey(channel.name).ifBlank { channel.url } }
        .sortedWith(
            compareByDescending<Pair<LiveChannel, Int>> { it.second }
                .thenBy { LiveTaxonomy.popularityRank(it.first.name) }
                .thenBy { LiveTaxonomy.countryFor(it.first.name) != "México" }
                .thenBy { it.first.name },
        )
        .map { it.first }
        .toList()
}
