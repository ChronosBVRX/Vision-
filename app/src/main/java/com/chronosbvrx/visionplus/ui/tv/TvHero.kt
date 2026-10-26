package com.chronosbvrx.visionplus.ui.tv

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.banners.HomeBanner
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource
import com.chronosbvrx.visionplus.home.contract.durationLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Una diapositiva del billboard de Vision TV. Reúne la foto diaria del VPS (películas
 * con backdrop de TMDB) y los partidos de GeeSports (marcador en vivo).
 */
internal data class TvHeroItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val meta: String,
    val imageUrl: String,
    val poster: String,
    val badge: String,
    val eyebrow: String,
    val film: Film? = null,
    val event: GeeSportsSource.GeeEvent? = null,
    val live: Boolean = false,
)

/**
 * Arma el hero de Vision TV: si hay un partido en vivo, abre con él; luego las
 * películas destacadas del VPS y, al final, el próximo partido. Solo películas en la
 * parte de cine (las series abren ficha y necesitan temporadas que el banner no trae).
 */
internal fun buildTvHeroItems(
    banners: List<HomeBanner>,
    events: List<GeeSportsSource.GeeEvent>,
    catalogFilms: List<Film>,
    nowMs: Long = System.currentTimeMillis(),
    max: Int = 8,
): List<TvHeroItem> {
    val byId = catalogFilms.associateBy { it.id }
    val byVideo = catalogFilms.associateBy { it.videoUrl }
    val movies = banners
        .filterNot { it.series }
        .mapNotNull { banner -> banner.toTvHero(byId, byVideo) }
    val sports = pickTvSports(events, nowMs)
    val live = sports.filter { it.live }
    val soon = sports.filterNot { it.live }
    val out = ArrayList<TvHeroItem>(max)
    if (live.isNotEmpty()) out += live[0]
    out += movies.take(2)
    if (live.size > 1) out += live[1]
    out += movies.drop(2).take(3)
    if (soon.isNotEmpty()) out += soon[0]
    return out.distinctBy { it.key }.take(max.coerceAtLeast(1))
}

internal fun pickTvSports(
    events: List<GeeSportsSource.GeeEvent>,
    nowMs: Long = System.currentTimeMillis(),
    liveLimit: Int = 2,
    soonLimit: Int = 2,
): List<TvHeroItem> {
    val items = events.mapNotNull { event -> event.toTvHero(nowMs) }.distinctBy { it.key }
    val live = items.filter { it.live }
        .sortedWith(
            compareBy<TvHeroItem> { tvLeagueRank(it.event?.leagueLabel.orEmpty()) }
                .thenBy { geeStartMs(it.event) },
        )
        .take(liveLimit.coerceAtLeast(0))
    val soon = items.filterNot { it.live }
        .sortedBy { geeStartMs(it.event) }
        .take(soonLimit.coerceAtLeast(0))
    return live + soon
}

/** Fecha/hora local del partido (GeeSports publica `date_iso` + `time` en CDMX). */
internal fun geeStartMs(event: GeeSportsSource.GeeEvent?): Long {
    if (event == null) return 0L
    val date = event.dateIso.trim()
    if (date.isBlank()) return 0L
    val time = when {
        event.time.isBlank() -> "00:00:00"
        event.time.count { it == ':' } == 1 -> "${event.time.trim()}:00"
        else -> event.time.trim()
    }
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .apply { timeZone = MX_ZONE }
            .parse("$date $time")
            ?.time ?: 0L
    }.getOrDefault(0L)
}

private fun HomeBanner.toTvHero(
    byId: Map<String, Film>,
    byVideo: Map<String, Film>,
): TvHeroItem? {
    val film = byId[ref] ?: byVideo[videoUrl] ?: run {
        if (ref.isBlank() && videoUrl.isBlank()) return null
        Film(
            id = ref.ifBlank { videoUrl },
            title = title,
            year = year,
            durationMin = durationMin,
            synopsis = synopsis,
            posterUrl = poster,
            videoUrl = videoUrl,
            sourceUrl = "banner",
            score = rating,
            kind = if (series) "series" else "movie",
        )
    }
    val meta = listOfNotNull(
        year.takeIf { it > 0 }?.toString(),
        rating.takeIf { it > 0.0 }?.let { "%.1f".format(it) },
        durationLabel(durationMin).takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return TvHeroItem(
        key = "banner:" + film.id,
        title = title,
        subtitle = synopsis,
        meta = meta,
        imageUrl = backdrop.ifBlank { poster },
        poster = poster,
        badge = badge.ifBlank { "DESTACADO HOY" },
        eyebrow = eyebrow.ifBlank { "PELÍCULA DESTACADA" },
        film = film,
    )
}

private fun GeeSportsSource.GeeEvent.toTvHero(nowMs: Long): TvHeroItem? {
    val start = geeStartMs(this)
    val status = status.lowercase(Locale.ROOT)
    val finished = status == "post" ||
        (start > 0 && start <= nowMs - 3 * 60 * 60 * 1000L && !live && status != "in")
    if (finished) return null
    val isLive = live || status == "in" ||
        (start > 0 && start <= nowMs && start > nowMs - 3 * 60 * 60 * 1000L)
    val isSoon = !isLive && (start == 0L || start > nowMs)
    if (!isLive && !isSoon) return null
    val teams = listOf(homeName, awayName).filter { it.isNotBlank() }
    val heroTitle = if (teams.size == 2) {
        "${teams[0]} vs ${teams[1]}"
    } else {
        name.ifBlank { teamA + " vs " + teamB }.ifBlank { "Evento en vivo" }
    }
    val whenText = if (isLive) {
        detail.ifBlank { "EN VIVO" }
    } else {
        listOf(dateText, time.take(5)).filter { it.isNotBlank() }.joinToString(" · ")
    }
    val league = leagueLabel.ifBlank { "Deportes" }
    return TvHeroItem(
        key = "sports:${id.ifBlank { heroTitle + "|" + start }}",
        title = heroTitle,
        subtitle = listOf(league, whenText).filter { it.isNotBlank() }.joinToString(" · "),
        meta = listOf(league, statusDetail()).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = "",
        poster = "",
        badge = if (isLive) "EN VIVO" else "PRÓXIMO",
        eyebrow = if (isLive) "DEPORTES · EN VIVO" else "PRÓXIMO PARTIDO",
        event = this,
        live = isLive,
    )
}

private fun GeeSportsSource.GeeEvent.statusDetail(): String =
    detail.ifBlank { if (status.equals("in", true)) "EN VIVO" else "" }

/** Prioridad de ligas para el hero de la tele (las que más mueven a la gente primero). */
internal fun tvLeagueRank(league: String): Int {
    val name = league.lowercase(Locale.ROOT)
    return when {
        "liga mx" in name -> 0
        "champions" in name -> 1
        "laliga" in name || "la liga" in name -> 2
        "premier" in name -> 3
        "serie a" in name -> 4
        "bundesliga" in name -> 5
        "ligue 1" in name -> 6
        "libertadores" in name -> 7
        "sudamericana" in name -> 8
        "mls" in name -> 9
        "nfl" in name -> 10
        "nba" in name -> 11
        "mlb" in name -> 12
        "formula 1" in name || "f1" in name -> 13
        else -> 50
    }
}

private val MX_ZONE: TimeZone = TimeZone.getTimeZone("America/Mexico_City")
