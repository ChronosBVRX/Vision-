package com.chronosbvrx.visionplus.data.sports

import android.content.Context
import android.util.Log
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.LiveTaxonomy
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Cruza la agenda con los canales en vivo del aparato. Dos pasos:
 *
 *  1. Cada cadena candidata que manda el servidor ("TUDN", "Canal 5", "Fox
 *     Sports"…) se resuelve contra los canales reales de la lista, con aislamiento
 *     estricto de edición (ESPN 1 vs 2 vs 3) y priorización rigurosa de país
 *     (México primero, luego Internacional/EE. UU., evitando feeds incorrectos).
 *  2. Para los partidos que están al aire (o empiezan en breve) se consulta la
 *     EPG de esos canales (título + sinopsis): el programa en curso que nombra
 *     a los dos equipos confirma el canal y se promueve a primera opción.
 *
 * La EPG se cachea en memoria 10 minutos; los alias cubren los nombres que el
 * portal usa para los equipos (Chivas = Guadalajara, Xolos = Tijuana, los
 * nombres en inglés de las selecciones, etc.).
 */
object SportsLinker {

    private const val TAG = "SportsLinker"
    private const val EPG_TTL_MS = 10 * 60 * 1000L
    private const val MAX_EPG_CHANNELS = 8
    private const val MAX_CANDIDATES = 8
    private const val MAX_TOTAL_WITH_FALLBACK = 12

    private val BROADCAST_NOISE = setOf(
        "televisa", "en", "vivo", "hd", "fhd", "sd", "4k", "plus",
        "online", "app", "deportes", "tv", "pc", "network"
    )

    private val EDITIONS = setOf("1", "2", "3", "4", "5", "premium", "extra", "plus", "one", "eventos")

    private val CHANNEL_CANONICAL: Map<String, List<String>> = mapOf(
        "canal 5 televisa" to listOf("canal", "5"),
        "canal 5" to listOf("canal", "5"),
        "azteca 7" to listOf("azteca", "7"),
        "azteca deportes en vivo" to listOf("azteca", "7"),
        "azteca deportes" to listOf("azteca", "7"),
        "tv azteca deportes network hd" to listOf("azteca", "7"),
        "tv azteca deportes network" to listOf("azteca", "7"),
        "tudn en vivo" to listOf("tudn"),
        "tudn usa" to listOf("tudn", "usa"),
        "fox mexico" to listOf("fox", "sports"),
        "fox one" to listOf("fox", "sports", "1"),
        "fox sports mexico" to listOf("fox", "sports"),
        "fox sports 2 mexico" to listOf("fox", "sports", "2"),
        "fox sports 3 mexico" to listOf("fox", "sports", "3"),
        "fox sports premium" to listOf("fox", "sports", "premium"),
        "fox+" to listOf("fox", "sports", "premium"),
        "espn mexico" to listOf("espn"),
        "espn 2 mexico" to listOf("espn", "2"),
        "espn2 mexico" to listOf("espn", "2"),
        "espn 3 mexico" to listOf("espn", "3"),
        "espn3 mexico" to listOf("espn", "3"),
        "espn 4" to listOf("espn", "4"),
        "espn4" to listOf("espn", "4"),
        "espn extra" to listOf("espn", "extra"),
        "espn premium" to listOf("espn", "premium"),
        "espn deportes" to listOf("espn", "deportes"),
        "disney+" to listOf("espn"),
        "disney plus" to listOf("espn"),
        "disney+ premium" to listOf("espn"),
        "star+" to listOf("espn"),
        "star plus" to listOf("espn"),
        "elite sports hub hd" to listOf("espn"),
        "elite sports hub" to listOf("espn"),
        "elite sports hub 2 hd" to listOf("espn", "2"),
        "elite sports hub 2" to listOf("espn", "2"),
        "elite sports hub 3 hd" to listOf("espn", "3"),
        "elite sports hub 3" to listOf("espn", "3"),
        "elite sports hub 4 hd" to listOf("espn", "4"),
        "elite sports hub 4" to listOf("espn", "4"),
        "espn star hd elite sports hub" to listOf("espn"),
        "espn 2 star 2 hd elite sports hub 2" to listOf("espn", "2"),
        "espn 3 star 3 hd elite sports hub 3" to listOf("espn", "3"),
        "espn 4 star 4 hd elite sports hub 4" to listOf("espn", "4"),
        "espn star hd" to listOf("espn"),
        "espn 2 star 2 hd" to listOf("espn", "2"),
        "espn 3 star 3 hd" to listOf("espn", "3"),
        "espn 4 star 4 hd" to listOf("espn", "4"),
        "tnt sports" to listOf("tnt", "sports"),
        "max mexico" to listOf("tnt", "sports"),
        "max" to listOf("tnt", "sports"),
        "hbo max" to listOf("tnt", "sports"),
        "claro sports" to listOf("claro", "sports"),
        "vix" to listOf("vix"),
        "vix premium" to listOf("vix"),
        "vix deportes" to listOf("vix"),
        "vix mexico" to listOf("vix"),
        "telemundo deportes en vivo" to listOf("telemundo"),
        "universo" to listOf("universo"),
        "win sports" to listOf("win", "sports"),
        "directv sports" to listOf("directv", "sports"),
        "directv sports pc" to listOf("directv", "sports"),
        "on directv hd" to listOf("directv", "sports"),
        "on directv" to listOf("directv", "sports"),
        "dsports" to listOf("directv", "sports"),
        "dgo" to listOf("directv", "sports"),
        "caliente tv" to listOf("caliente", "tv"),
        "caliente" to listOf("caliente"),
        "movistar futbol" to listOf("movistar", "futbol"),
        "movistar futbol hd" to listOf("movistar", "futbol"),
        "movistar laliga" to listOf("movistar", "futbol"),
        "movistar plus" to listOf("movistar", "futbol"),
        "liga de campeones" to listOf("liga", "campeones"),
        "movistar liga de campeones" to listOf("liga", "campeones"),
        "dazn" to listOf("dazn"),
        "dazn laliga" to listOf("dazn"),
        "dazn 1 hd" to listOf("dazn", "1"),
        "dazn 1 pc" to listOf("dazn", "1"),
        "dazn 2 pc" to listOf("dazn", "2"),
        "dazn 3 hd" to listOf("dazn", "3"),
        "dazn 4 hd" to listOf("dazn", "4"),
        "dazn eventos" to listOf("dazn", "eventos"),
        "canal gol" to listOf("canal", "gol"),
        "canal gol hd" to listOf("canal", "gol"),
        "gol play" to listOf("canal", "gol"),
        "bein sports" to listOf("bein", "sports"),
        "tyc sports" to listOf("tyc", "sports"),
        "sky sports" to listOf("sky", "sports"),
        "sky sports mexico" to listOf("sky", "sports"),
    )

    private val LEAGUE_DISALLOWED_BRANDS: Map<String, Set<String>> = mapOf(
        "ita.1" to setOf("fox sports", "claro sports", "tudn", "canal 5", "azteca 7", "tnt sports"),
        "esp.1" to setOf("fox sports", "claro sports", "tudn", "canal 5", "azteca 7", "tnt sports"),
        "ger.1" to setOf("tudn", "canal 5", "tnt sports", "claro sports", "azteca 7"),
        "fra.1" to setOf("fox sports", "claro sports", "tudn", "canal 5", "tnt sports", "azteca 7"),
        "uefa.champions" to setOf("fox sports", "tudn", "canal 5", "azteca 7", "claro sports"),
        "uefa.europa" to setOf("tudn", "canal 5", "azteca 7", "claro sports"),
        "eng.1" to setOf("tudn", "canal 5", "azteca 7", "claro sports"),
        "mex.1" to setOf("directv sports", "movistar futbol", "liga de campeones", "dazn", "win sports"),
    )

    /**
     * Canales reales del catálogo XTV y de respaldo internacional por liga cuando el partido
     * trae cadenas genéricas o de plataformas de streaming (Sky, Disney+, Max, ViX+).
     */
    private val LEAGUE_FALLBACK_CHANNELS: Map<String, List<String>> = mapOf(
        "esp.1" to listOf("Movistar Futbol", "DAZN", "Directv Sports", "Canal Gol", "ESPN", "ESPN 2"),
        "eng.1" to listOf("TNT Sports", "FOX One", "ESPN", "DAZN", "Movistar Futbol", "Universo"),
        "ita.1" to listOf("ESPN", "ESPN 2", "ESPN 3", "DAZN", "Directv Sports"),
        "ger.1" to listOf("ESPN", "ESPN 2", "DAZN", "Movistar Futbol"),
        "fra.1" to listOf("ESPN", "ESPN 2", "DAZN", "beIN Sports"),
        "uefa.champions" to listOf("TNT Sports", "Liga de campeones", "Movistar Futbol", "ESPN", "ESPN 2", "DAZN"),
        "uefa.europa" to listOf("ESPN", "ESPN 2", "Fox Sports", "Fox Sports 2", "Liga de campeones", "Movistar Futbol"),
        "conmebol.libertadores" to listOf("ESPN", "ESPN 2", "ESPN 3", "Fox Sports", "Directv Sports"),
        "conmebol.sudamericana" to listOf("ESPN", "ESPN 2", "Directv Sports"),
        "concacaf.champions" to listOf("Fox Sports", "Fox Sports 2", "TUDN", "ESPN"),
        "usa.1" to listOf("ESPN", "Fox Sports", "TUDN"),
        "arg.1" to listOf("ESPN", "TNT Sports", "TyC Sports"),
        "mex.1" to listOf("TUDN", "Canal 5", "Azteca 7", "Fox Sports", "ESPN", "ViX Deportes"),
        "mex.2" to listOf("Fox Sports", "ESPN", "ESPN 2", "TUDN", "Claro Sports"),
    )

    internal fun candidateTokens(name: String): List<String> {
        val n = norm(name)
        val mapped = CHANNEL_CANONICAL[n]
        if (mapped != null) return mapped
        val baseTokens = tokens(name)
        val filtered = baseTokens.filter { it !in BROADCAST_NOISE }
        return if (filtered.isNotEmpty()) filtered else baseTokens
    }

    internal data class BrandInfo(
        val brand: String,
        val edition: String?,
        val targetCountry: String?,
    )

    private val brandInfoCache = ConcurrentHashMap<String, BrandInfo>()

    internal fun extractBrandInfo(rawName: String): BrandInfo {
        return brandInfoCache.computeIfAbsent(rawName) { computeBrandInfo(it) }
    }

    private fun computeBrandInfo(rawName: String): BrandInfo {
        val n = norm(rawName)
        val toks = tokens(rawName)

        val targetCountry = when {
            n.contains("mexico") || n.contains("mex") || toks.contains("mx") -> "México"
            n.contains("usa") || n.contains("estados unidos") || toks.contains("us") -> "EE. UU."
            n.contains("argentina") || toks.contains("arg") || toks.contains("ar") -> "Argentina"
            n.contains("chile") || toks.contains("cl") -> "Chile"
            n.contains("colombia") || toks.contains("col") || toks.contains("co") -> "Colombia"
            n.contains("espana") || n.contains("spain") || toks.contains("esp") -> "España"
            else -> null
        }

        val mapped = CHANNEL_CANONICAL[n]
        val cleanToks = mapped ?: toks.filter { it !in BROADCAST_NOISE }

        val brand: String
        val edition: String?

        when {
            cleanToks.contains("espn") || (cleanToks.contains("elite") && cleanToks.contains("hub")) || cleanToks.contains("disney") || (cleanToks.contains("star") && !cleanToks.contains("action")) -> {
                brand = "espn"
                edition = when {
                    toks.contains("deportes") && !n.contains("azteca") -> "deportes"
                    cleanToks.contains("4") -> "4"
                    cleanToks.contains("3") -> "3"
                    cleanToks.contains("2") -> "2"
                    cleanToks.contains("extra") -> "extra"
                    cleanToks.contains("premium") -> "premium"
                    else -> null
                }
            }
            cleanToks.contains("fox") && (cleanToks.contains("sports") || cleanToks.contains("sport") || cleanToks.contains("one") || cleanToks.contains("premium") || n.contains("fox 1") || n.contains("fox 2") || n.contains("fox 3") || n == "fox" || n == "fox mexico") -> {
                brand = "fox sports"
                edition = when {
                    cleanToks.contains("premium") -> "premium"
                    cleanToks.contains("3") -> "3"
                    cleanToks.contains("2") -> "2"
                    cleanToks.contains("1") || cleanToks.contains("one") -> "1"
                    else -> null
                }
            }
            cleanToks.contains("tudn") -> {
                brand = "tudn"
                edition = if (cleanToks.contains("extra")) "extra" else null
            }
            cleanToks.contains("canal") && cleanToks.contains("5") -> {
                brand = "canal 5"
                edition = null
            }
            cleanToks.contains("azteca") && (cleanToks.contains("7") || cleanToks.contains("siete") || toks.contains("deportes")) -> {
                brand = "azteca 7"
                edition = null
            }
            cleanToks.contains("vix") -> {
                brand = "vix"
                edition = if (cleanToks.contains("premium")) "premium" else null
            }
            cleanToks.contains("claro") -> {
                brand = "claro sports"
                edition = cleanToks.firstOrNull { it in EDITIONS }
            }
            cleanToks.contains("tnt") -> {
                brand = "tnt sports"
                edition = cleanToks.firstOrNull { it in EDITIONS }
            }
            cleanToks.contains("directv") || cleanToks.contains("dsports") -> {
                brand = "directv sports"
                edition = cleanToks.firstOrNull { it in EDITIONS }
            }
            cleanToks.contains("movistar") -> {
                brand = "movistar futbol"
                edition = null
            }
            cleanToks.contains("liga") && cleanToks.contains("campeones") -> {
                brand = "liga de campeones"
                edition = null
            }
            cleanToks.contains("dazn") -> {
                brand = "dazn"
                edition = when {
                    cleanToks.contains("4") -> "4"
                    cleanToks.contains("3") -> "3"
                    cleanToks.contains("2") -> "2"
                    cleanToks.contains("eventos") -> "eventos"
                    else -> null
                }
            }
            cleanToks.contains("gol") -> {
                brand = "canal gol"
                edition = null
            }
            cleanToks.contains("bein") -> {
                brand = "bein sports"
                edition = null
            }
            cleanToks.contains("tyc") -> {
                brand = "tyc sports"
                edition = null
            }
            cleanToks.contains("universo") -> {
                brand = "universo"
                edition = null
            }
            cleanToks.contains("win") -> {
                brand = "win sports"
                edition = if (cleanToks.contains("plus") || cleanToks.contains("premium")) "plus" else cleanToks.firstOrNull { it in EDITIONS }
            }
            cleanToks.contains("telemundo") -> {
                brand = "telemundo"
                edition = null
            }
            cleanToks.contains("caliente") -> {
                brand = "caliente tv"
                edition = null
            }
            else -> {
                brand = cleanToks.filter { it !in EDITIONS && it != targetCountry?.lowercase() }.joinToString(" ")
                edition = cleanToks.firstOrNull { it in EDITIONS }
            }
        }

        return BrandInfo(brand = brand, edition = edition, targetCountry = targetCountry)
    }

    internal fun scoreChannel(query: String, channel: LiveChannel, leagueId: String? = null): Int {
        val qInfo = extractBrandInfo(query)
        val chInfo = extractBrandInfo(channel.name)

        if (!leagueId.isNullOrBlank()) {
            val disallowed = LEAGUE_DISALLOWED_BRANDS[leagueId]
            if (disallowed != null && (chInfo.brand in disallowed || qInfo.brand in disallowed)) {
                return 0
            }
        }

        val chCountry = LiveTaxonomy.countryFor(channel.name)

        var score = 0

        // 1. Coincidencia de marca principal
        if (qInfo.brand.isNotBlank() && chInfo.brand.isNotBlank()) {
            if (qInfo.brand == chInfo.brand) {
                score += 100
            } else {
                val needle = candidateTokens(query)
                if (matchChannel(tokens(channel.name), needle)) {
                    score += 40
                } else {
                    return 0
                }
            }
        } else {
            val needle = candidateTokens(query)
            if (matchChannel(tokens(channel.name), needle)) {
                score += 50
            } else {
                return 0
            }
        }

        // 2. Aislamiento estricto de edición numérica / premium
        val qEd = if (qInfo.edition == "1") null else qInfo.edition
        val chEd = if (chInfo.edition == "1") null else chInfo.edition
        if (qEd != null) {
            if (chEd == qEd) {
                score += 80
            } else {
                return 0
            }
        } else {
            if (chEd != null) {
                return 0
            } else {
                score += 80
            }
        }

        // 3. Puntuación y preferencia por país
        val targetCountry = qInfo.targetCountry
        if (targetCountry != null) {
            if (chCountry == targetCountry) {
                score += 100
            } else {
                score -= 120
            }
        } else {
            when (chCountry) {
                "México" -> score += 80
                "España" -> score += 50
                "Internacional" -> score += 40
                "EE. UU." -> score += 20
                else -> score -= 70
            }
        }

        // 4. Bonos adicionales por feed nacional y calidad
        if (hasMexicanFeed(channel.name)) score += 20
        if (channel.name.contains("HD", ignoreCase = true) || channel.name.contains("FHD", ignoreCase = true)) score += 5
        score -= (channel.name.length / 5)

        return score
    }

    private val epgCache = ConcurrentHashMap<String, Pair<Long, List<X4KApi.EpgItem>>>()
    private val resolvedCache = ConcurrentHashMap<String, Link>()

    fun clearCache() {
        resolvedCache.clear()
        epgCache.clear()
        brandInfoCache.clear()
    }

    /** Lo que la ficha necesita: los canales posibles y el confirmado por la EPG con sus programas. */
    data class Link(
        val candidates: List<LiveChannel>,
        val confirmed: LiveChannel?,
        val epgPrograms: Map<String, String> = emptyMap(),
    )

    internal fun targetQueryNames(match: SportMatch): List<String> {
        val list = ArrayList<String>()
        for (b in match.broadcasts) {
            val trimmed = b.trim()
            if (trimmed.isNotBlank() && trimmed !in list) list.add(trimmed)
        }
        for (c in match.channels) {
            val trimmed = c.trim()
            if (trimmed.isNotBlank() && trimmed !in list) list.add(trimmed)
        }
        val leagueFallbacks = LEAGUE_FALLBACK_CHANNELS[match.league.id].orEmpty()
        for (fb in leagueFallbacks) {
            if (fb !in list) list.add(fb)
        }
        return list
    }

    private fun buildLinkWithFallback(
        match: SportMatch,
        channels: List<LiveChannel>,
    ): Link {
        val queries = targetQueryNames(match)
        val primary = resolveChannels(queries, channels, match.league.id)
        val extraOptions = GeeSportsFallback.fallbackFor(queries, match.league.id)
        val combined = LinkedHashMap<String, LiveChannel>()
        val nameCounts = HashMap<String, Int>()
        for (ch in primary) {
            if (combined.putIfAbsent(ch.url, ch) == null) {
                val key = norm(ch.name)
                nameCounts[key] = (nameCounts[key] ?: 0) + 1
            }
        }
        for (opt in extraOptions) {
            if (combined.size >= MAX_TOTAL_WITH_FALLBACK) break
            if (!combined.containsKey(opt.url)) {
                val key = norm(opt.name)
                val nextCount = (nameCounts[key] ?: 0) + 1
                nameCounts[key] = nextCount
                val finalChannel = if (nextCount > 1) {
                    opt.copy(name = "${opt.name} · Opción $nextCount")
                } else {
                    opt
                }
                combined[opt.url] = finalChannel
            }
        }
        return Link(
            candidates = combined.values.toList(),
            confirmed = null,
            epgPrograms = emptyMap(),
        )
    }

    /**
     * Resuelve los canales candidatos para un partido de forma instantánea y síncrona
     * consultando los canales cacheados en memoria o disco, sin esperar a la red.
     */
    fun quickResolve(context: Context, match: SportMatch): Link {
        val hit = resolvedCache[match.id]
        if (hit != null) return hit
        val channels = X4KApi.cachedLiveChannels(context)
        val link = buildLinkWithFallback(match, channels)
        resolvedCache[match.id] = link
        return link
    }

    /**
     * Resuelve los canales candidatos para una lista de partidos de forma inmediata (0 ms).
     */
    fun quickResolveAll(context: Context, matches: List<SportMatch>): Map<String, Link> {
        if (matches.isEmpty()) return emptyMap()
        val channels = X4KApi.cachedLiveChannels(context)
        val out = LinkedHashMap<String, Link>()
        for (match in matches) {
            val hit = resolvedCache[match.id]
            if (hit != null) {
                out[match.id] = hit
            } else {
                val link = buildLinkWithFallback(match, channels)
                resolvedCache[match.id] = link
                out[match.id] = link
            }
        }
        return out
    }

    suspend fun resolve(context: Context, matches: List<SportMatch>): Map<String, Link> =
        withContext(Dispatchers.IO) {
            runCatching { GeeSportsFallback.refreshIfNeeded(context) }
            val channels = runCatching { X4KApi.liveChannelsFor(context) }
                .onFailure { Log.w(TAG, "sin canales en vivo: ${it.message}") }
                .getOrDefault(emptyList())
            val links = LinkedHashMap<String, Link>()
            for (match in matches) {
                val link = buildLinkWithFallback(match, channels)
                resolvedCache[match.id] = link
                links[match.id] = link
            }
            links
        }

    /**
     * Segunda fase: para los partidos al aire consulta la EPG de sus canales
     * (título + sinopsis) y marca el que nombra a los dos equipos. Se aplica
     * sobre el mapa de [resolve].
     */
    suspend fun confirm(
        context: Context,
        matches: List<SportMatch>,
        links: Map<String, Link>,
    ): Map<String, Link> = withContext(Dispatchers.IO) {
        val pending = matches.filter { isWindow(it) }
        if (pending.isEmpty()) return@withContext links
        val candidates = pending
            .flatMap { m -> links[m.id]?.candidates.orEmpty() }
            .filter { it.url.startsWith("xtvlive:") }
            .distinctBy { it.url }
            .take(MAX_EPG_CHANNELS)
        if (candidates.isEmpty()) return@withContext links
        val epg = fetchEpgs(context, candidates)
        val now = System.currentTimeMillis()
        val out = LinkedHashMap(links)
        for (match in pending) {
            val link = out[match.id] ?: continue
            val epgPrograms = LinkedHashMap(link.epgPrograms)
            var confirmed: LiveChannel? = null

            for (channel in link.candidates) {
                val code = channelCode(channel) ?: continue
                val items = epg[code] ?: continue
                val currentItem = items.firstOrNull { item ->
                    item.startMs > 0 && item.startMs <= now && now <= item.endMs
                }
                if (currentItem != null && currentItem.name.isNotBlank()) {
                    val fullText = "${currentItem.name} ${currentItem.description}".trim()
                    val isMatch = mentionsBoth(fullText, match)
                    val programLabel = if (isMatch) {
                        "⚽ En vivo: ${currentItem.name}"
                    } else {
                        "Ahora: ${currentItem.name}"
                    }
                    epgPrograms[channel.url] = programLabel
                    if (isMatch && confirmed == null) {
                        confirmed = channel
                    }
                }
            }

            val finalCandidates = if (confirmed != null) {
                listOf(confirmed) + link.candidates.filter { it.url != confirmed.url }
            } else {
                link.candidates
            }

            val updatedLink = link.copy(
                candidates = finalCandidates,
                confirmed = confirmed,
                epgPrograms = epgPrograms,
            )
            out[match.id] = updatedLink
            resolvedCache[match.id] = updatedLink
        }
        out
    }

    internal fun channelCode(channel: LiveChannel): String? {
        if (!channel.url.startsWith("xtvlive:")) return null
        return channel.url.removePrefix("xtvlive:").takeIf { it.isNotBlank() }
    }

    /** Un partido que está al aire, o que arranca en los próximos 30 minutos. */
    internal fun isWindow(match: SportMatch): Boolean {
        if (match.playing) return true
        if (match.status != "pre" || match.kickoffMs <= 0) return false
        val delta = match.kickoffMs - System.currentTimeMillis()
        return delta in -5 * 60_000..30 * 60_000
    }

    internal fun matchChannel(channelTokens: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || channelTokens.isEmpty()) return false
        if (containsSequence(channelTokens, needle)) return true
        return needle.all { needleTok -> channelTokens.contains(needleTok) }
    }

    /** Las cadenas candidatas resueltas a canales reales evaluando marca, edición y país. */
    internal fun resolveChannels(
        names: List<String>,
        channels: List<LiveChannel>,
        leagueId: String? = null,
    ): List<LiveChannel> {
        if (channels.isEmpty() || names.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, LiveChannel>()
        for (name in names) {
            var bestChannel: LiveChannel? = null
            var bestScore = 0
            for (channel in channels) {
                val score = scoreChannel(name, channel, leagueId)
                if (score > bestScore) {
                    bestScore = score
                    bestChannel = channel
                }
            }
            val best = bestChannel ?: continue
            out.putIfAbsent(best.url, best)
            if (out.size >= MAX_CANDIDATES) break
        }
        return out.values.toList()
    }

    private suspend fun fetchEpgs(
        context: Context,
        channels: List<LiveChannel>,
    ): Map<String, List<X4KApi.EpgItem>> {
        val now = System.currentTimeMillis()
        val out = HashMap<String, List<X4KApi.EpgItem>>()
        for (channel in channels) {
            val code = channelCode(channel) ?: continue
            val hit = epgCache[code]
            if (hit != null && now - hit.first < EPG_TTL_MS) {
                out[code] = hit.second
            } else {
                val items = runCatching { X4KApi.epgFor(context, code, channel.name) }.getOrDefault(emptyList())
                epgCache[code] = now to items
                out[code] = items
            }
        }
        return out
    }

    // ----- nombres -----

    private val STOP = setOf("club", "fc", "cf", "cd", "de", "del", "la", "el", "los", "las", "y", "and", "the", "vs", "at")

    internal fun mentionsBoth(program: String, match: SportMatch): Boolean {
        val tokens = tokens(program).toSet()
        return mentionsTeam(tokens, match.home.name) && mentionsTeam(tokens, match.away.name)
    }

    internal fun mentionsTeam(programTokens: Set<String>, teamName: String): Boolean =
        teamTokens(teamName).any { it in programTokens }

    internal fun teamTokens(name: String): Set<String> {
        // Solo tokens significativos (4+ letras) para no confirmar por coincidencias flojas;
        // los alias cortos («psg», «boca», «river», «city»…) entran a propósito.
        val base = tokens(name).filter { it.length >= 4 }
        val extra = TEAM_ALIASES[norm(name)].orEmpty()
        return (base + extra).toSet()
    }

    internal fun tokens(text: String): List<String> = norm(text)
        .split(' ')
        .filter { it.isNotBlank() && it !in STOP && (it.length >= 3 || it.all { c -> c.isDigit() }) }

    internal fun containsSequence(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    internal fun hasMexicanFeed(name: String): Boolean =
        tokens(name).any { it == "mx" || it == "mex" || it == "mexico" }

    /** Sin tildes, en minúsculas, solo letras/dígitos: "Atlético" -> "atletico". */
    internal fun norm(text: String): String {
        val folded = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val sb = StringBuilder(folded.length)
        for (c in folded) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.toString().trim().replace(Regex(" +"), " ")
    }

    /**
     * Alias por nombre normalizado (como lo manda ESPN): los apodos del portal,
     * los nombres cortos de los clubes europeos y los de las selecciones en el
     * idioma contrario.
     */
    internal val TEAM_ALIASES: Map<String, List<String>> = mapOf(
        "guadalajara" to listOf("chivas"),
        "chivas" to listOf("guadalajara"),
        "tijuana" to listOf("xolos"),
        "xolos" to listOf("tijuana"),
        "monterrey" to listOf("rayados"),
        "rayados" to listOf("monterrey"),
        "universidad nacional" to listOf("pumas"),
        "unam" to listOf("pumas"),
        "pumas unam" to listOf("pumas"),
        "santos laguna" to listOf("santos"),
        "juarez" to listOf("bravos"),
        "atletico de san luis" to listOf("san", "luis"),
        "leon" to listOf("esmeraldas"),
        "paris saint germain" to listOf("psg"),
        "paris saint germain fc" to listOf("psg"),
        "barcelona" to listOf("barca"),
        "barca" to listOf("barcelona"),
        "juventus" to listOf("juve"),
        "juve" to listOf("juventus"),
        "inter" to listOf("internazionale", "nerazzurri"),
        "internazionale" to listOf("inter"),
        "internazionale milano" to listOf("inter"),
        "ac milan" to listOf("milan"),
        "milan" to listOf("ac milan"),
        "bayern munich" to listOf("bayern"),
        "manchester city" to listOf("city"),
        "manchester united" to listOf("united"),
        "newcastle united" to listOf("newcastle"),
        "leeds united" to listOf("leeds"),
        "nottingham forest" to listOf("forest"),
        "wolverhampton wanderers" to listOf("wolves"),
        "wolverhampton" to listOf("wolves"),
        "tottenham hotspur" to listOf("tottenham"),
        "brighton & hove albion" to listOf("brighton"),
        "west ham united" to listOf("west", "ham"),
        "boca juniors" to listOf("boca"),
        "river plate" to listOf("river"),
        "racing club" to listOf("racing"),
        "velez sarsfield" to listOf("velez"),
        "newells old boys" to listOf("newells"),
        "atletico nacional" to listOf("nacional"),
        "atletico mineiro" to listOf("mineiro"),
        "atletico paranaense" to listOf("paranaense"),
        "red bull bragantino" to listOf("bragantino"),
        "united states" to listOf("estados", "unidos"),
        "estados unidos" to listOf("united", "states"),
        "germany" to listOf("alemania"),
        "alemania" to listOf("germany"),
        "england" to listOf("inglaterra"),
        "inglaterra" to listOf("england"),
        "spain" to listOf("espana"),
        "espana" to listOf("spain"),
        "france" to listOf("francia"),
        "francia" to listOf("france"),
        "netherlands" to listOf("paises", "bajos", "holanda"),
        "paises bajos" to listOf("netherlands", "holanda"),
        "italy" to listOf("italia"),
        "italia" to listOf("italy"),
        "brazil" to listOf("brasil"),
        "brasil" to listOf("brazil"),
        "croatia" to listOf("croacia"),
        "croacia" to listOf("croatia"),
        "switzerland" to listOf("suiza"),
        "suiza" to listOf("switzerland"),
        "belgium" to listOf("belgica"),
        "belgica" to listOf("belgium"),
        "denmark" to listOf("dinamarca"),
        "dinamarca" to listOf("denmark"),
        "sweden" to listOf("suecia"),
        "suecia" to listOf("sweden"),
        "norway" to listOf("noruega"),
        "noruega" to listOf("norway"),
        "poland" to listOf("polonia"),
        "polonia" to listOf("poland"),
        "japan" to listOf("japon"),
        "japon" to listOf("japan"),
        "south korea" to listOf("corea"),
        "corea del sur" to listOf("south", "korea"),
        "morocco" to listOf("marruecos"),
        "marruecos" to listOf("morocco"),
        "cameroon" to listOf("camerun"),
        "camerun" to listOf("cameroon"),
        "saudi arabia" to listOf("arabia"),
        "arabia saudita" to listOf("saudi"),
        "united arab emirates" to listOf("emiratos"),
    )
}
