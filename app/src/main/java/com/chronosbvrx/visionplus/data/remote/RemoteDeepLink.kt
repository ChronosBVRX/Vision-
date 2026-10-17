package com.chronosbvrx.visionplus.data.remote

import com.chronosbvrx.visionplus.data.LiveChannel

/**
 * El canal que el teléfono pidió abrir con `visionplus://live/<código>`.
 *
 * Se prefiere el del catálogo cacheado (trae el logo y el grupo reales); si no está, se arma con
 * lo que viajó en el enlace (nombre y logo) para que el botón del teléfono nunca quede muerto.
 * Sin código no hay canal: mejor no abrir nada que abrir el canal equivocado.
 */
internal fun remoteLiveChannel(
    code: String,
    name: String,
    logo: String?,
    cached: List<LiveChannel>,
): LiveChannel? {
    val clean = code.trim()
    if (clean.isEmpty()) return null
    val url = "xtvlive:$clean"
    return cached.firstOrNull { it.url == url }
        ?: LiveChannel(
            name = name.trim().ifBlank { "Canal" },
            url = url,
            logo = logo?.takeIf { it.isNotBlank() },
        )
}
