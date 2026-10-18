package com.chronosbvrx.visionplus.ui.remote

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.SeasonRef

/**
 * El buscador del control remoto: encuentra en el catálogo lo que se puede abrir en la tele.
 *
 * Solo entran referencias reproducibles (`xtv:`, `xtvlive:` o una URL) — un título cuya
 * referencia no sepamos abrir no se ofrece, para que ningún botón quede muerto.
 */
internal sealed interface CastItem {
    val title: String
    val subtitle: String
    val art: String?
    val ref: String

    data class Vod(
        override val title: String,
        override val subtitle: String,
        override val art: String?,
        override val ref: String,
        /** La ficha completa: con ella se decide si hay que elegir temporada/capítulo. */
        val film: Film,
    ) : CastItem

    data class Channel(
        override val title: String,
        override val subtitle: String,
        override val art: String?,
        override val ref: String,
    ) : CastItem
}

/** ¿Sabemos abrir esta referencia en la tele? */
internal fun castableRef(videoUrl: String): Boolean {
    val v = videoUrl.trim()
    return when {
        v.startsWith("xtv:") -> v.length > 4
        v.startsWith("xtvlive:") -> v.length > 8
        v.startsWith("http://") || v.startsWith("https://") -> v.length > 8
        else -> false
    }
}

/**
 * ¿Este título se abre eligiendo temporada y capítulo? Las series del índice traen su tipo
 * (`kind`) y su lista de temporadas; una película se reproduce directo como siempre. Sin la
 * lista de capítulos del portal (que se pide al abrir el selector) jamás se bloquea nada: el
 * selector cae al botón «Reproducir el título».
 */
internal fun isCastSeries(film: Film): Boolean =
    film.seasons.isNotEmpty() || film.kind.trim().lowercase() in CAST_SERIES_KINDS

private val CAST_SERIES_KINDS = setOf("series", "teleplay", "anime")

/** La etiqueta de una temporada del grupo: «T2», o «Completa» cuando no tiene número. */
internal fun castSeasonLabel(n: Int): String = if (n <= 0) "Completa" else "T$n"

/**
 * El capítulo que el teléfono va a abrir en la tele: la referencia del capítulo con TODO el
 * contexto de su serie (temporada activa y sus vecinas) para que el reproductor de la tele
 * ofrezca «siguiente» sin volver al teléfono.
 */
internal data class CastEpisode(
    val ref: String,
    val title: String,
    val poster: String,
    val seriesId: String,
    val seasonIndex: Int,
    val seasonIds: List<String>,
    val seasonNames: List<String>,
)

/**
 * Arma la orden del capítulo: `seasonIndex` es el índice dentro de [Film.seasons] (0 cuando la
 * serie tiene una sola temporada y no trae lista). La referencia del capítulo es su playCode
 * (`xtv:<code>`). Puro: lo que viaja al cable queda cubierto por pruebas.
 */
internal fun castEpisodeFor(film: Film, seasonIndex: Int, episodeCode: String, episodeNumber: Int): CastEpisode {
    val seasons = film.seasons
    val idx = seasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
    val active = seasons.getOrNull(idx)
    val label = active?.let { castSeasonLabel(it.n) }
    return CastEpisode(
        ref = castEpisodeRef(episodeCode),
        title = castEpisodeTitle(film.title, label, episodeNumber),
        poster = film.posterUrl,
        seriesId = active?.titleId ?: film.id,
        seasonIndex = idx,
        seasonIds = seasons.map { it.titleId },
        seasonNames = seasons.map { castSeasonLabel(it.n) },
    )
}

/** El playCode de un capítulo, con el prefijo que la app sabe abrir. */
internal fun castEpisodeRef(code: String): String {
    val clean = code.trim()
    return if (clean.startsWith("xtv:")) clean else "xtv:$clean"
}

/** «La Casa de Papel · T2 · C5»: el rótulo con el que la tele anuncia el capítulo. */
internal fun castEpisodeTitle(seriesTitle: String, seasonLabel: String?, episodeNumber: Int): String =
    listOfNotNull(
        seriesTitle.trim().ifBlank { null },
        seasonLabel?.trim()?.takeIf { it.isNotEmpty() },
        "C${episodeNumber.coerceAtLeast(1)}",
    ).joinToString(" · ")


/**
 * Arma la lista del buscador: primero lo del catálogo (películas/series) y luego los canales en
 * vivo, sin repetir referencias y con topes para que la lista no se vuelva un maratón.
 *
 * @param films ya filtradas por el buscador del catálogo (con acentos y tokens); aquí solo se
 *   descartan las que no sepamos abrir.
 */
internal fun buildCastResults(
    films: List<Film>,
    channels: List<LiveChannel>,
    query: String,
    maxFilms: Int = 24,
    maxChannels: Int = 12,
): List<CastItem> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val vod = films.asSequence()
        .filter { castableRef(it.videoUrl) }
        .distinctBy { it.videoUrl }
        .take(maxFilms)
        .map {
            CastItem.Vod(
                title = it.title,
                subtitle = vodSubtitle(it),
                art = it.posterUrl.takeIf { url -> url.isNotBlank() },
                ref = it.videoUrl,
                film = it,
            )
        }
        .toList()
    val folded = foldForSearch(q)
    val ch = channels.asSequence()
        .filter { foldForSearch(it.name).contains(folded) }
        .distinctBy { it.url }
        .take(maxChannels)
        .map {
            CastItem.Channel(
                title = it.name,
                subtitle = it.effectiveGroup(),
                art = it.logo,
                ref = it.url,
            )
        }
        .toList()
    return vod + ch
}

private fun vodSubtitle(film: Film): String = listOfNotNull(
    film.year.takeIf { it > 0 }?.toString(),
    film.kind.takeIf { it.isNotBlank() }?.let { kindLabel(it) },
).joinToString(" · ")

private fun kindLabel(kind: String): String = when (kind.lowercase()) {
    "series", "teleplay" -> "Serie"
    "anime" -> "Anime"
    "kids" -> "Infantil"
    "movie", "film" -> "Película"
    else -> kind.replaceFirstChar { it.uppercase() }
}

private const val ACCENTS = "áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ"
private const val PLAIN = "aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC"

/** Búsqueda sin acentos (para los nombres de canal, que el catálogo ya normaliza por su cuenta). */
internal fun foldForSearch(text: String): String {
    val sb = StringBuilder(text.length)
    for (ch in text) {
        val i = ACCENTS.indexOf(ch)
        sb.append(if (i >= 0) PLAIN[i] else ch)
    }
    return sb.toString().lowercase()
}
