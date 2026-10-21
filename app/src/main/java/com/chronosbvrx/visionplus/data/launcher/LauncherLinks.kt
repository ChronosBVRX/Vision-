package com.chronosbvrx.visionplus.data.launcher

import android.net.Uri

/**
 * El enlace con el que las tarjetas del launcher de TV (canal Watch Next / «Continuar viendo»)
 * y el control remoto del teléfono vuelven a la app: `visionplus://watch/<clave>?t=…&p=…&c=…`.
 *
 * La clave es la del historial (playCode XTV o clave de videoUrl). t/p/c/u son el respaldo
 * (título, póster, playCode XTV y videoUrl) por si el historial ya se podó cuando el usuario
 * toca la tarjeta. Cuando el control remoto manda un CAPÍTULO, además viaja el contexto de su
 * serie (s/sx/sl/sn) para abrir ese capítulo exacto y poder seguir con el siguiente.
 */
internal data class WatchLink(
    val key: String,
    val title: String = "",
    val posterUrl: String = "",
    val code: String = "",
    val videoUrl: String = "",
    /** Temporada activa del capítulo (su titleId), vacío en películas. */
    val seriesId: String = "",
    val seasonIndex: Int = 0,
    val seasonIds: List<String> = emptyList(),
    val seasonNames: List<String> = emptyList(),
    /** Minuto exacto del teléfono cuando la orden es «continuar en la tele» (0 = sin minuto). */
    val resumeMs: Long = 0,
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
            if (link.seriesId.isNotBlank()) appendQueryParameter("s", link.seriesId)
            if (link.seasonIndex > 0) appendQueryParameter("sx", link.seasonIndex.toString())
            if (link.seasonIds.isNotEmpty()) appendQueryParameter("sl", link.seasonIds.joinToString(SEP))
            if (link.seasonNames.isNotEmpty()) appendQueryParameter("sn", link.seasonNames.joinToString(SEP))
            if (link.resumeMs > 0) appendQueryParameter("r", link.resumeMs.toString())
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
            seriesId = uri.getQueryParameter("s").orEmpty(),
            seasonIndex = uri.getQueryParameter("sx")?.trim()?.toIntOrNull() ?: 0,
            seasonIds = splitList(uri.getQueryParameter("sl")),
            seasonNames = splitList(uri.getQueryParameter("sn")),
            resumeMs = uri.getQueryParameter("r")?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        )
    }

    /** Separa una lista del enlace (`T1|T2|T3`): sin vacíos y sin espacios de sobra. */
    private fun splitList(raw: String?): List<String> =
        raw.orEmpty().split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    private const val SEP = "|"
}
