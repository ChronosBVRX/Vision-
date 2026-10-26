package com.chronosbvrx.visionplus.home.data

import com.chronosbvrx.visionplus.home.contract.HeroItem
import com.chronosbvrx.visionplus.home.contract.MediaKind
import com.chronosbvrx.visionplus.home.contract.SportsItem
import com.chronosbvrx.visionplus.home.contract.VisionLinks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Una diapositiva del hero. Unifica dos mundos que antes vivían separados:
 * - **Cine/series** (`banners` del VPS con arte panorámico, o el hero del feed como respaldo).
 * - **Deportes** (`sports` de la agenda del feed): el partido en vivo manda cuando existe.
 *
 * El launcher no reproduce nada aquí: solo dispara los enlaces ya resueltos.
 */
data class HeroSlide(
    val id: String,
    val title: String,
    val sports: Boolean = false,
    val subtitle: String = "",
    val eyebrow: String = "",
    val badge: String = "",
    val backdrop: String = "",
    val poster: String = "",
    val synopsis: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val durationMin: Int = 0,
    val series: Boolean = false,
    val openLink: String = "",
    val playLink: String = "",
    // --- deportes ---
    val league: String = "",
    val leagueLogo: String = "",
    val homeName: String = "",
    val awayName: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val status: String = "",
    val detail: String = "",
    val startMs: Long = 0L,
    val live: Boolean = false,
    val timeText: String = "",
    val dateText: String = "",
    val channels: List<String> = emptyList(),
)

/** El hero del feed, con el arte del póster como respaldo si el VPS no manda banners. */
internal fun heroSlideFromFeed(item: HeroItem, position: Int = 0): HeroSlide = HeroSlide(
    id = "feed:" + item.ref.ifBlank { item.title },
    title = item.title,
    badge = item.badge,
    eyebrow = when {
        position == 0 -> "DESTACADO HOY"
        item.kind == MediaKind.SERIES -> "SERIE DESTACADA"
        item.kind == MediaKind.ANIME -> "ANIME DESTACADO"
        item.kind == MediaKind.KIDS -> "INFANTIL DESTACADO"
        else -> "PELÍCULA DESTACADA"
    },
    backdrop = item.poster,
    poster = item.poster,
    synopsis = item.synopsis,
    year = item.year,
    rating = item.rating,
    durationMin = item.durationMin,
    series = item.series,
    openLink = item.openLink,
    playLink = item.playLink,
)

/**
 * Convierte un partido de la agenda en una diapositiva. Devuelve `null` si ya terminó
 * o si la ventana no aplica, para que el hero nunca enseñe un partido viejo.
 */
internal fun sportsSlide(item: SportsItem, nowMs: Long = System.currentTimeMillis()): HeroSlide? {
    val status = item.status.lowercase(Locale.ROOT)
    val start = item.startMs
    val finished = status == "post" || (start > 0 && start <= nowMs - 3 * 60 * 60 * 1000L &&
        !item.live && status != "in")
    if (finished) return null
    val live = item.live || status == "in" ||
        (start > 0 && start <= nowMs && start > nowMs - 3 * 60 * 60 * 1000L)
    val soon = !live && (start == 0L || start > nowMs)
    if (!live && !soon) return null
    val whenText = when {
        live -> item.detail.ifBlank { "EN VIVO" }
        start > 0 -> "${heroDayLabel(start, nowMs)} · ${heroTimeLabel(start)}"
        else -> listOf(item.dateText, item.time).filter { it.isNotBlank() }.joinToString(" · ")
    }
    return HeroSlide(
        id = "sports:" + item.id.ifBlank { item.name + "|" + item.startMs },
        title = item.name.ifBlank {
            listOf(item.homeName, item.awayName).filter { it.isNotBlank() }.joinToString(" vs ")
        }.ifBlank { "Evento en vivo" },
        sports = true,
        subtitle = listOf(item.league, whenText).filter { it.isNotBlank() }.joinToString(" · "),
        eyebrow = if (live) "DEPORTES · EN VIVO" else "PRÓXIMO PARTIDO",
        badge = if (live) "EN VIVO" else "PRÓXIMO",
        league = item.league,
        leagueLogo = item.leagueLogo,
        homeName = item.homeName,
        awayName = item.awayName,
        homeLogo = item.homeLogo,
        awayLogo = item.awayLogo,
        homeScore = item.homeScore,
        awayScore = item.awayScore,
        status = item.status,
        detail = whenText,
        startMs = start,
        live = live,
        timeText = if (start > 0) heroTimeLabel(start) else item.time,
        dateText = if (start > 0) heroDayLabel(start, nowMs) else item.dateText,
        openLink = VisionLinks.sports(item.id),
    )
}

/** Los partidos que merecen el hero: vivos primero (Liga MX y grandes ligas), luego los próximos. */
internal fun pickSportsSlides(
    items: List<SportsItem>,
    nowMs: Long = System.currentTimeMillis(),
    liveLimit: Int = 2,
    soonLimit: Int = 2,
): List<HeroSlide> {
    val slides = items.mapNotNull { sportsSlide(it, nowMs) }.distinctBy { it.id }
    val live = slides.filter { it.live }
        .sortedWith(compareBy<HeroSlide> { leagueRank(it.league) }.thenBy { it.startMs.takeIf { s -> s > 0 } ?: Long.MAX_VALUE })
        .take(liveLimit.coerceAtLeast(0))
    val soon = slides.filterNot { it.live }
        .sortedBy { it.startMs.takeIf { s -> s > 0 } ?: Long.MAX_VALUE }
        .take(soonLimit.coerceAtLeast(0))
    return live + soon
}

/**
 * Orden del hero: si hay un partido en vivo, abre con él; luego la foto curada de
 * películas/series, el segundo partido vivo y, al final, el próximo encuentro.
 */
internal fun composeHeroSlides(
    banners: List<HeroSlide>,
    feedHero: List<HeroItem>,
    sports: List<SportsItem>,
    nowMs: Long = System.currentTimeMillis(),
    max: Int = 8,
): List<HeroSlide> {
    val movies = if (banners.isNotEmpty()) {
        banners
    } else {
        feedHero.mapIndexed { index, item -> heroSlideFromFeed(item, index) }
    }
    val sportsSlides = pickSportsSlides(sports, nowMs)
    val live = sportsSlides.filter { it.live }
    val soon = sportsSlides.filterNot { it.live }
    val out = ArrayList<HeroSlide>(max)
    if (live.isNotEmpty()) out += live[0]
    out += movies.take(2)
    if (live.size > 1) out += live[1]
    out += movies.drop(2).take(3)
    if (soon.isNotEmpty()) out += soon[0]
    return out.distinctBy { it.id }.take(max.coerceAtLeast(1))
}

internal fun heroTimeLabel(startMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (startMs <= 0) return ""
    val format = SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-MX"))
        .apply { timeZone = MX_ZONE }
    return format.format(Date(startMs))
}

internal fun heroDayLabel(startMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (startMs <= 0) return ""
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = MX_ZONE }
    val start = dayFormat.format(Date(startMs))
    val today = dayFormat.format(Date(nowMs))
    if (start == today) return "Hoy"
    val tomorrow = dayFormat.format(Date(nowMs + 24 * 60 * 60 * 1000L))
    if (start == tomorrow) return "Mañana"
    val label = SimpleDateFormat("EEE d", Locale.forLanguageTag("es-MX"))
        .apply { timeZone = MX_ZONE }
        .format(Date(startMs))
    return label.replaceFirstChar { it.uppercase() }
}

/** Prioridad de ligas para el hero (las que más mueven a la gente primero). */
internal fun leagueRank(league: String): Int {
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
