package com.chronosbvrx.visionplus.ui.sports

import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeHealth
import com.chronosbvrx.visionplus.data.gee.GeeSportsSource

/**
 * Una fila del selector de transmisiones de un partido.
 *
 * La ÚNICA fuente de verdad son los enlaces que **GeeSports publica para ese partido**.
 * No se mezclan canales de la guía/parrilla ni del catálogo: si la fuente no manda
 * ningún enlace con dirección, el selector lo dice («Sin enlaces disponibles»).
 *
 * [playing] marca la señal que se estaba viendo al salir al reproductor: al volver,
 * el modal reabre con esa fila enfocada (la selección que hizo el usuario).
 */
internal data class GeePickRow(
    val channel: LiveChannel,
    val title: String,
    /** La dirección tal cual (con sus cabeceras): la UI la enseña recortada. */
    val link: String,
    val type: String,
    val alive: Boolean?,
    val playing: Boolean,
) {
    /** «DASH · ClearKey», «DASH» o «HLS»: el formato real de la señal. */
    val typeLabel: String
        get() = when {
            type == "dash" && !channel.drmKey.isNullOrBlank() -> "DASH · ClearKey"
            type == "dash" -> "DASH"
            else -> "HLS"
        }
}

/**
 * Arma la lista del selector usando SOLO las señales de GeeSports para el evento.
 *
 * Es pura a propósito (misma fuente para móvil y TV, y testeable sin red): la UI solo
 * pinta lo que sale de aquí y el zapping usa exactamente este orden.
 *
 * Se descartan las ranuras sin dirección y se deduplica por URL **sin las cabeceras**
 * (`|user-agent=…`): el mismo stream puede venir con y sin ellas según la entrada del CMS.
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
        if (raw.isBlank()) continue
        val url = raw.substringBefore('|').trim()
        if (url.isBlank() || !used.add(url)) continue
        out.add(
            GeePickRow(
                channel = GeeSportsSource.toChannel(link, link.name.ifBlank { title }, "Deportes en vivo"),
                title = link.name.ifBlank { title },
                link = raw,
                type = link.type,
                alive = GeeHealth.effective(link.alive, checks[raw.trim()]),
                playing = url == playing,
            ),
        )
        if (out.size >= MAX_PICK_ROWS) break
    }
    // Lo que responde primero; dentro de cada grupo se conserva el orden que manda
    // GeeSports (su servidor ya entrega las vivas primero).
    return out.sortedWith(compareBy { aliveRank(it.alive) })
}

/** 0 = responde, 1 = sin verificar, 2 = caída. */
private fun aliveRank(alive: Boolean?): Int = when (alive) {
    true -> 0
    null -> 1
    false -> 2
}

/** Tope del selector: más de esto no se elige, solo se desplaza sin fin. */
internal const val MAX_PICK_ROWS = 24
