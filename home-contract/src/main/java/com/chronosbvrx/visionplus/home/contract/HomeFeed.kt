package com.chronosbvrx.visionplus.home.contract

/**
 * El feed que Vision+ Home pinta en el Inicio. Es deliberadamente pequeño (< 80 KB): el
 * launcher no reimplementa el catálogo, solo presenta lo que Vision+ ya calculó y dispara
 * los enlaces que vienen resueltos aquí.
 */
data class HomeFeed(
    val meta: FeedMeta = FeedMeta(),
    val hero: List<HeroItem> = emptyList(),
    val continueWatching: List<ContinueItem> = emptyList(),
    val live: List<LiveItem> = emptyList(),
    val rows: List<FeedRow> = emptyList(),
    val sports: List<SportsItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = hero.isEmpty() && continueWatching.isEmpty() && live.isEmpty() && rows.isEmpty() && sports.isEmpty()
}

/** Contexto del feed: quién es el perfil activo y cuándo se generó. */
data class FeedMeta(
    val version: Int = HomeContract.VERSION,
    val generatedAt: Long = 0L,
    val profile: String = "",
    val profiles: Int = 1,
    val online: Boolean = true,
    val appVersion: String = "",
)

/** Una pieza del hero (rotan solas en el launcher). */
data class HeroItem(
    val ref: String,
    val title: String,
    val poster: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    /** movie | series | anime | kids (vacío si el catálogo no lo clasificó). */
    val kind: String = "",
    val synopsis: String = "",
    val durationMin: Int = 0,
    val badge: String = "",
    /** Es serie/anime/kids: el launcher abre la ficha antes de reproducir. */
    val series: Boolean = false,
    /** Abre la ficha en Vision+. */
    val openLink: String = "",
    /** Reproduce directo (vacío en series: primero se abre la ficha). */
    val playLink: String = "",
)

/** «Seguir viendo»: reanuda en el minuto exacto, con el capítulo ya resuelto. */
data class ContinueItem(
    val key: String,
    val title: String,
    val poster: String = "",
    /** 0..100 */
    val progress: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** «T2 · C3» cuando es capítulo. */
    val episode: String = "",
    val playLink: String = "",
)

/** Un canal de TV en vivo con su guía (cuando el EPG ya está en caché). */
data class LiveItem(
    val code: String,
    val name: String,
    val logo: String = "",
    val group: String = "",
    val now: String = "",
    val next: String = "",
    /** 0..100 del programa en curso. */
    val progress: Int = 0,
    /** Ventana del programa en curso: el launcher recalcula el avance sin esperar al feed. */
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    /** Señal mexicana: el launcher la pone primero y la puede filtrar. */
    val mexico: Boolean = false,
    val playLink: String = "",
)

/** Un evento deportivo (en vivo o próximo) con su enlace al evento en Vision+. */
data class SportsItem(
    val id: String,
    /** «América vs Cruz Azul» (o el nombre del evento si no hay equipos). */
    val name: String,
    /** Football / Basketball / Tennis… (el launcher lo rotula en español). */
    val category: String = "",
    /** yyyy-MM-dd en el centro de México. */
    val dateIso: String = "",
    val league: String = "",
    val leagueLogo: String = "",
    val homeName: String = "",
    val awayName: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    /** «45'», «FT», «Hoy 20:00»… */
    val detail: String = "",
    val live: Boolean = false,
    /** Estado de la agenda: pre | in | post (vacío si la fuente no lo trae). */
    val status: String = "",
    /** Inicio real en epoch ms (0 si no se pudo resolver): la fase se calcula con el reloj. */
    val startMs: Long = 0L,
    val alive: Int = 0,
    val total: Int = 0,
    val dateText: String = "",
    val time: String = "",
    val openLink: String = "",
)

/** Una hilera del Inicio. */
data class FeedRow(
    val id: String,
    val label: String,
    /** [RowKind.POSTERS] o [RowKind.TOP10]. */
    val kind: String = RowKind.POSTERS,
    val items: List<FeedItem> = emptyList(),
)

/** Una tarjeta de una hilera. */
data class FeedItem(
    val ref: String,
    val title: String,
    val poster: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val kind: String = "",
    val badge: String = "",
    /** Es serie/anime/kids: el launcher abre la ficha antes de reproducir. */
    val series: Boolean = false,
    /** Abre la ficha. */
    val openLink: String = "",
    /** Reproduce directo cuando el título es una película. */
    val playLink: String = "",
)

object RowKind {
    const val POSTERS = "posters"
    const val TOP10 = "top10"
}

object MediaKind {
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val ANIME = "anime"
    const val KIDS = "kids"
}

/** «48 min» / «1 h 42 min» a partir de minutos, sin ceros de relleno. */
fun durationLabel(minutes: Int): String {
    if (minutes <= 0) return ""
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h $m min"
}
