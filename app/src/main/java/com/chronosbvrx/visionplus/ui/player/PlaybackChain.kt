package com.chronosbvrx.visionplus.ui.player

import com.chronosbvrx.visionplus.data.playback.StreamRanker

/**
 * Orden de reproducción y respaldo para VOD.
 *
 * El motor arranca con la PRIMERA media de la lista. La "Calidad preferida" de
 * Ajustes elige el tramo inicial, pero el resto de la cadena no debe conservar el
 * orden del portal: si el usuario pidió 720p y esa pista muere, el respaldo natural
 * es 480p (o la siguiente inferior), no un 4K que su equipo no puede con el ancho de
 * banda. Las resoluciones superiores quedan al final, como último recurso.
 *
 * Sin preferencia ("Auto", vacío o una calidad que el título no tiene) se devuelve
 * la lista tal cual: ya viene rankeada por [StreamRanker] y respeta el orden del portal.
 */
internal fun <T> playbackChain(
    medias: List<T>,
    qualityOf: (T) -> String,
    preferred: String,
): List<T> {
    if (medias.size < 2) return medias
    val preferredTier = StreamRanker.tier(preferred)
    if (preferredTier <= 0) return medias

    val groups = LinkedHashMap<Int, MutableList<T>>()
    medias.forEach { media ->
        groups.getOrPut(StreamRanker.tier(qualityOf(media))) { ArrayList() }.add(media)
    }
    if (preferredTier !in groups) return medias

    // Preferida → inferiores de mayor a menor → superiores de menor a mayor (último
    // recurso). Las pistas sin resolución declarada (tier 0) quedan al final: no se
    // sabe si aguantan el ancho de banda y no deben adelantar a una calidad conocida.
    val lower = groups.keys.filter { it in 1 until preferredTier }.sortedDescending()
    val higher = groups.keys.filter { it > preferredTier }.sorted()
    val order = ArrayList<Int>(groups.size).apply {
        add(preferredTier)
        addAll(lower)
        addAll(higher)
        if (0 in groups) add(0)
    }
    return ArrayList<T>(medias.size).apply {
        order.forEach { tier -> groups[tier]?.let { addAll(it) } }
    }
}
