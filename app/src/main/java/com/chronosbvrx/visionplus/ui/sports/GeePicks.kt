package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource

/**
 * Una fila del selector de transmisiones de un partido.
 *
 * La fuente publica los nombres de sus señales para el evento y, aparte, las
 * direcciones. Se muestran TODAS las filas que declara (como su app original):
 * una ranura sin dirección sigue en la lista como «Sin señal», pero no se puede
 * sintonizar. [playing] marca la señal que se estaba viendo al salir.
 */
internal data class GeePickRow(
    val channel: LiveChannel,
    val title: String,
    /** La dirección tal cual (con sus cabeceras): la UI la enseña recortada. */
    val link: String,
    val type: String,
    val alive: Boolean?,
    val playing: Boolean,
    /** `embed` pendiente: se resuelve al tocarlo, no está caído. */
    val tokenApi: String = "",
    /** Nota corta de la opción XTV (por qué es la recomendada / qué NO es). */
    val note: String = "",
) {
    /** Se puede sintonizar si ya tiene dirección o si es un `embed` resoluble. */
    val playable: Boolean
        get() = link.isNotBlank() || tokenApi.isNotBlank()

    /** «DASH · ClearKey», «DASH», «HLS», «Al tocar» o «Sin señal». */
    val typeLabel: String
        get() = when {
            type == "xtv" -> "En tu TV"
            link.isBlank() && tokenApi.isNotBlank() -> "Se resuelve al tocar"
            !playable -> "Sin señal"
            type == "dash" && !channel.drmKey.isNullOrBlank() -> "DASH · ClearKey"
            type == "dash" -> "DASH"
            else -> "HLS"
        }
}

/**
 * Arma la lista del selector con las señales que la fuente publica para el evento.
 *
 * Es pura a propósito (misma fuente para móvil y TV, y testeable sin red): la UI
 * solo pinta lo que sale de aquí y el zapping usa exactamente este orden.
 *
 * Se conservan las ranuras declaradas sin dirección (nombre presente, `link`
 * vacío): son parte de la lista de la app original y se ven como «Sin señal».
 * Se deduplica solo la MISMA fila (nombre + dirección); dos nombres distintos que
 * compartan URL se conservan porque son fuentes declaradas diferentes.
 */
internal fun geePickRows(
    title: String,
    links: List<GeeSportsSource.GeeLink> = emptyList(),
    checks: Map<String, GeeHealth.Check> = emptyMap(),
    playingUrl: String? = null,
): List<GeePickRow> {
    val playing = playingUrl?.trim()?.substringBefore('|').orEmpty()
    val used = LinkedHashSet<String>()
    val out = ArrayList<GeePickRow>()

    for (link in links) {
        val raw = link.link.trim()
        val name = link.name.trim().ifBlank { title }
        if (raw.isBlank() && link.name.isBlank() && link.tokenApi.isBlank()) continue
        val url = raw.substringBefore('|').trim()
        val dedupe = name.lowercase() + "|" + (if (url.isBlank()) "embed:" + link.tokenApi else url)
        if (!used.add(dedupe)) continue
        out.add(
            GeePickRow(
                channel = GeeSportsSource.toChannel(link, name, "Deportes en vivo"),
                title = name,
                link = raw,
                type = link.type,
                alive = if (url.isBlank()) null else GeeHealth.effective(link.alive, checks[raw.trim()]),
                playing = url.isNotBlank() && url == playing,
                tokenApi = link.tokenApi,
            ),
        )
        if (out.size >= MAX_PICK_ROWS) break
    }
    // Lo que responde primero; las ranuras sin dirección quedan al final.
    return out.sortedWith(compareBy { aliveRank(it) })
}

/** 0 = responde, 1 = sin verificar, 2 = caída, 3 = declarada sin dirección. */
private fun aliveRank(row: GeePickRow): Int = when {
    !row.playable -> 3
    row.alive == true -> 0
    row.alive == null -> 1
    else -> 2
}

/** Filas de la segunda opción propia: canales XTV ya confirmados por el servidor. */
internal fun xtvPickRows(
    options: List<GeeSportsSource.XtvOption> = emptyList(),
    playingUrl: String? = null,
): List<GeePickRow> {
    val playing = playingUrl?.trim()?.substringBefore('|').orEmpty()
    val used = LinkedHashSet<String>()
    val out = ArrayList<GeePickRow>()
    for (option in options) {
        val name = option.name.trim()
        var code = option.code.trim()
        if (code.startsWith("xtvlive:", ignoreCase = true)) code = code.substringAfter(':')
        if (name.isBlank() || code.isBlank()) continue
        val url = "xtvlive:$code"
        if (!used.add(url)) continue
        out.add(
            GeePickRow(
                channel = LiveChannel(
                    name = name,
                    url = url,
                    group = "Deportes en vivo",
                ),
                title = name,
                link = url,
                type = "xtv",
                alive = true,
                playing = url == playing,
                note = if (option.recommended) {
                    "Recomendado · ${option.note}".trim().trimEnd('·', ' ')
                } else {
                    option.note
                },
            ),
        )
    }
    // La recomendada primero; el servidor ya las manda en ese orden.
    return out
}

/** Tope del selector: más de esto no se elige, solo se desplaza sin fin. */
internal const val MAX_PICK_ROWS = 32

/** Convierte una fila `embed` ya resuelta en el canal que abre el reproductor. */
internal fun GeePickRow.resolvedWith(stream: GeeResolvedStream): LiveChannel = channel.copy(
    url = stream.url,
    userAgent = stream.headers["user-agent"] ?: channel.userAgent,
    referrer = stream.headers["referer"] ?: stream.headers["referrer"] ?: channel.referrer,
    origin = stream.headers["origin"] ?: channel.origin,
)
