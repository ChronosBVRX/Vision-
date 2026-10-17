package com.chronosbvrx.visionplus.ui.remote

import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.LiveChannel

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
