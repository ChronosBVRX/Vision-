package com.chronosbvrx.visionplus.data.launcher

import android.net.Uri

/**
 * El enlace con el que las tarjetas del launcher de TV (canal Watch Next / «Continuar viendo»)
 * vuelven a la app: `visionplus://watch/<clave>?t=…&p=…&c=…&u=…`.
 *
 * La clave es la del historial (playCode XTV o clave de videoUrl). t/p/c/u son el respaldo
 * (título, póster, playCode XTV y videoUrl) por si el historial ya se podó cuando el usuario
 * toca la tarjeta.
 */
internal data class WatchLink(
    val key: String,
    val title: String = "",
    val posterUrl: String = "",
    val code: String = "",
    val videoUrl: String = "",
)

internal object LauncherLinks {
    const val SCHEME = "visionplus"
    const val HOST = "watch"

    /** El URI de datos del deep link (el `intent_uri` se construye a partir de él). */
    fun uriFor(link: WatchLink): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendPath(link.key)
        .appendQueryParameter("t", link.title)
        .appendQueryParameter("p", link.posterUrl)
        .apply {
            if (link.code.isNotBlank()) appendQueryParameter("c", link.code)
            if (link.videoUrl.isNotBlank()) appendQueryParameter("u", link.videoUrl)
        }
        .build()

    /** De URI a enlace; null cuando no es nuestro esquema o no trae clave. */
    fun parse(uri: Uri?): WatchLink? {
        if (uri == null) return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (!HOST.equals(uri.host, ignoreCase = true)) return null
        val key = uri.pathSegments.lastOrNull().orEmpty()
        if (key.isBlank()) return null
        return WatchLink(
            key = key,
            title = uri.getQueryParameter("t").orEmpty(),
            posterUrl = uri.getQueryParameter("p").orEmpty(),
            code = uri.getQueryParameter("c").orEmpty(),
            videoUrl = uri.getQueryParameter("u").orEmpty(),
        )
    }
}
