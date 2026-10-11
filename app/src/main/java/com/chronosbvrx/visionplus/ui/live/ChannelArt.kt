package com.chronosbvrx.visionplus.ui.live

/** En vivo's texts shared by the phone and the TV. */
internal object LiveCopy {
    const val SEARCH_HINT = "Buscar canal por nombre…"
    const val NO_RESULTS = "Sin resultados"
    const val NO_RESULTS_BODY = "Prueba con otro nombre de canal."
    const val NO_CHANNELS = "Sin canales"
    const val NO_CHANNELS_BODY = "Esta lista no tiene canales para mostrar."
    const val ADD_LIST = "Agregar lista M3U"
    const val ADD_LIST_BODY = "Pega la dirección de una lista M3U/M3U8 (por ejemplo la de tu proveedor de IPTV)."
    const val REFRESH = "Recargar"
    const val DELETE_LIST = "Quitar lista"
    const val ALL_GROUPS = "Todas"

    /** The hint under the TV preview for the focused row. */
    fun watchRow(name: String): String = "OK para ver $name · mantén OK para agregar a favoritos"
}

private val REGEX_WORDS = Regex("[^\\p{L}\\p{N}]+")

/** What a logo-less channel shows: the initials of its name ("Cine Clásico" -> "CC"). */
internal fun channelInitials(name: String): String {
    val words = name.split(REGEX_WORDS).filter { it.isNotEmpty() }
    val initials = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> ""
    }
    return initials.uppercase().ifEmpty { "TV" }
}
