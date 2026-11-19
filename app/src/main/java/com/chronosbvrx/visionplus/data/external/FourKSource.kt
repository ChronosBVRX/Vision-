package com.chronosbvrx.visionplus.data.external

/**
 * Una fuente 4K externa ya validada y asociada a una ficha del catálogo.
 *
 * El `url` puede ser HLS o un archivo directo. `lan` marca las que apuntan a una
 * IP privada: solo sirven si el equipo está en esa red, así que el reproductor
 * debe caer a la calidad nativa si no responden.
 */
internal data class FourKSource(
    val url: String,
    val provider: String,
    val language: String,
    val filename: String = "",
    val lan: Boolean = false,
    val checkedAt: Long = 0L,
    val expiresAt: Long = 0L,
) {
    val label: String
        get() = when {
            language.equals("Latino", ignoreCase = true) -> "$provider · Latino"
            language.equals("Castellano", ignoreCase = true) -> "$provider · Castellano"
            else -> provider
        }

    val isHls: Boolean
        get() = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
}
