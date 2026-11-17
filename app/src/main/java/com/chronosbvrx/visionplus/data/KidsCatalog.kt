package com.chronosbvrx.visionplus.data

/**
 * Catálogo infantil **reproducible**.
 *
 * `xtv-kids-all` mezcla títulos del portal XTV que la cuenta de cortesía no puede
 * licenciar: el broker responde «sin licencia» y la ficha se queda sin pistas. Sus
 * dos hijas sí están separadas por tipo y se firman bien:
 *
 *  - [MOVIES] «Película de Animación» (640 títulos)
 *  - [SERIES] «Serie de Animación» (595 títulos)
 *
 * El bloque infantil nunca lleva anime: ese catálogo tiene su propia sección.
 */
object KidsCatalog {
    const val MOVIES = "xtv-kids-76805"
    const val SERIES = "xtv-kids-76806"

    fun categoryFor(series: Boolean): String = if (series) SERIES else MOVIES

    fun isKidsKey(key: String): Boolean = key == "kids" || key.startsWith("kids-")

    /**
     * Quita anime del bloque infantil (regla del producto: anime = solo japonés).
     *
     * El portal etiqueta «Anime» a cualquier animación (Shrek, Barbie, LEGO…) y,
     * como un título puede vivir en varias secciones, `kind` termina siendo el de
     * la última pasada. Por eso manda el idioma original de TMDB: solo se descarta
     * lo que se sabe japonés (`ja`). Mientras una ficha vieja no tenga ese dato, se
     * respeta la colección infantil curada por el portal en vez de perderla.
     */
    fun keep(films: List<Film>): List<Film> =
        films.filterNot { it.originalLanguage.equals("ja", ignoreCase = true) }
}
