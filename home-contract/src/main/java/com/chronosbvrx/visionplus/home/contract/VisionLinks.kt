package com.chronosbvrx.visionplus.home.contract

import java.net.URLEncoder

/**
 * Los enlaces con los que Vision+ Home habla con Vision+. Son los MISMOS que ya usa el canal
 * Watch Next y el control remoto, más dos hosts aditivos: `title` (abrir ficha) y `open`
 * (ajustes/buscador), que una app vieja ignora sin romper nada.
 */
object VisionLinks {
    const val SCHEME = "visionplus"

    const val HOST_WATCH = "watch"
    const val HOST_LIVE = "live"
    const val HOST_TITLE = "title"
    const val HOST_OPEN = "open"

    /**
     * Reanuda una película o un capítulo en el minuto exacto. `key` es la clave del historial;
     * `code`/`videoUrl` son el respaldo cuando el historial ya no la tiene.
     */
    fun watch(
        key: String,
        title: String = "",
        poster: String = "",
        code: String = "",
        videoUrl: String = "",
        seriesId: String = "",
        seasonIndex: Int = 0,
        seasonIds: List<String> = emptyList(),
        seasonNames: List<String> = emptyList(),
        resumeMs: Long = 0L,
    ): String = buildString {
        append("$SCHEME://$HOST_WATCH/").append(enc(key))
        query("t", title)
        query("p", poster)
        query("c", code)
        query("u", videoUrl)
        query("s", seriesId)
        if (seasonIndex > 0) query("sx", seasonIndex.toString())
        if (seasonIds.isNotEmpty()) query("sl", seasonIds.joinToString("|"))
        if (seasonNames.isNotEmpty()) query("sn", seasonNames.joinToString("|"))
        if (resumeMs > 0) query("r", resumeMs.toString())
    }

    /** Abre un canal en vivo, con nombre y logo para que pinte al instante. */
    fun live(code: String, name: String = "", logo: String = ""): String = buildString {
        append("$SCHEME://$HOST_LIVE/").append(enc(code))
        query("t", name)
        query("p", logo)
    }

    /** Abre la ficha de un título del catálogo (con su contexto para pintarla al instante). */
    fun title(
        ref: String,
        title: String = "",
        poster: String = "",
        videoUrl: String = "",
    ): String = buildString {
        append("$SCHEME://$HOST_TITLE/${enc(ref)}")
        query("t", title)
        query("p", poster)
        query("u", videoUrl)
    }

    /** Abre el buscador de Vision+ (con la consulta ya dictada, si la hay). */
    fun search(query: String = ""): String = buildString {
        append("$SCHEME://$HOST_OPEN/search")
        if (query.isNotBlank()) query("q", query)
    }

    /** Ajustes de Vision+ (perfiles, continuidad, descargas…). */
    fun settings(): String = "$SCHEME://$HOST_OPEN/settings"

    /** «Mi lista» / historial de Vision+. */
    fun library(): String = "$SCHEME://$HOST_OPEN/library"

    /** La rejilla completa de TV en vivo de Vision+. */
    fun liveHub(): String = "$SCHEME://$HOST_OPEN/live"

    /** La sección de Deportes en vivo; con [eventId] abre ese partido. */
    fun sports(eventId: String = ""): String = buildString {
        append("$SCHEME://$HOST_OPEN/sports")
        if (eventId.isNotBlank()) query("event", eventId)
    }

    /** Una sección del catálogo (movies, series, anime…) en Vision+. */
    fun section(name: String): String = "$SCHEME://$HOST_OPEN/section/${enc(name)}"

    /** El Inicio completo de Vision+ (la app tal como se conoce). */
    fun home(): String = "$SCHEME://$HOST_OPEN/home"

    private fun StringBuilder.query(name: String, value: String) {
        if (value.isBlank()) return
        append(if ('?' in this) '&' else '?')
        append(name).append('=').append(enc(value))
    }

    /** `%20` en vez de `+`: Android deserializa el `+` literal en las query. */
    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
