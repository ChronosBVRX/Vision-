package com.chronosbvrx.visionplus.data

import java.text.Normalizer

/**
 * Clasificación de los catálogos hijos: Películas, Series, Anime, Novelas y sus
 * variantes infantiles.
 *
 * El Inicio ya cura un pool global (Top 10, Estrenos, temáticas y filas por tipo).
 * Esta capa hace lo mismo, pero **dentro de una sola sección**:
 *
 *  - Top 10: impacto real de la sección (rating con confianza + popularidad + recencia).
 *  - Estrenos: la fila curada del servidor cuando existe; si no, año actual/anterior.
 *    En infantiles, si el catálogo no tiene estrenos, se muestra «Novedades».
 *  - Tendencias: lo popular y reciente que no se repitió arriba.
 *  - En emisión: cruce con la agenda TVMaze. Si su cobertura no alcanza, se usa
 *    «Series activas» (recientes con señal) para no afirmar de más.
 *  - Mejor valoradas: rating ponderado por votos.
 *  - Géneros: las filas del servidor o, para infantiles, una agrupación local por
 *    el género primario de TMDB (evita que todo caiga en «Animación»).
 *
 * Las filas derivadas no traen `categoryId` de servidor: la cuadrícula las muestra
 * con lo que ya está en memoria. Las filas del servidor conservan su id y su total
 * para que «Ver más» siga paginando la categoría real.
 *
 * Es puro a propósito: las pantallas lo calculan una vez por contenido y lo prueban
 * los tests sin red ni Compose.
 */
internal object SectionCuration {

    private const val MIN_ROW = 4
    private const val ROW_LIMIT = 15
    private const val MAX_GENRES = 12

    /**
     * Filas del feed de una sección, en el orden en que se pintan.
     *
     * [pool] debe venir ya limitado a la sección (Películas solo películas, Anime solo
     * anime, etc.). [serverRows] son las hileras de `/v1/section/{name}`; para
     * infantiles se deja vacío y los géneros se calculan localmente.
     * [onAirIds] son los `titleId` que TVMaze puso al aire esta semana.
     */
    internal fun build(
        key: String,
        title: String,
        pool: List<Film>,
        serverRows: List<CatalogRow> = emptyList(),
        onAirIds: List<String> = emptyList(),
        kidsOnly: Boolean = false,
        yearNow: Int = Popularity.nowYear(),
    ): List<CatalogRow> {
        val cleanPool = Popularity.dedupe(pool.filter { it.videoUrl.isNotBlank() })
        if (cleanPool.isEmpty()) return emptyList()

        val kids = kidsOnly || key.startsWith("kids")
        val top10 = Popularity.topTen(cleanPool, limit = 10, yearNow = yearNow)
        val topRow = top10.takeIf { it.isNotEmpty() }?.let {
            CatalogRow("$key-top10", "Top 10", it, it.size)
        }

        val premiere = premiereRow(key, cleanPool, serverRows, kids, yearNow)
        val usedAfterTop = top10 + premiere?.films.orEmpty()

        val trending = trendingRow(key, cleanPool, usedAfterTop, yearNow)
        val onAir = onAirRow(key, cleanPool, onAirIds, usedAfterTop + trending?.films.orEmpty())
        // Si TVMaze no trae suficientes coincidencias (hoy su cobertura MX es baja),
        // no se inventa un «En emisión»: se pinta «Series activas» con lo reciente
        // que sí tiene señal. La etiqueta es distinta para no afirmar de más.
        val active = if (onAir == null) {
            activeFallbackRow(key, cleanPool, usedAfterTop + trending?.films.orEmpty(), yearNow)
        } else {
            null
        }
        val best = bestRatedRow(
            key,
            cleanPool,
            usedAfterTop + trending?.films.orEmpty() + onAir?.films.orEmpty() + active?.films.orEmpty(),
        )

        val genres = if (serverRows.any { !isPremiereRow(it) }) {
            serverGenreRows(serverRows)
        } else {
            localGenreRows(key, cleanPool, kids)
        }

        val derived = listOfNotNull(topRow, premiere, trending, onAir, active, best)
        // Los géneros se limpian entre sí (una caché vieja pudo meter el mismo título
        // en Drama y Suspenso), pero NO se vacían contra el Top 10/Tendencias: un
        // género debe seguir mostrando sus títulos aunque también estén arriba. Si no,
        // en catálogos chicos los géneros desaparecían por completo.
        val cleanGenres = Popularity.disjointRows(genres, minKeep = MIN_ROW)

        // Infantiles no tiene «Catálogo completo» en el servidor: la última hilera
        // lleva todo el pool y su «Ver más» abre la categoría infantil paginada.
        val all = if (kids) {
            listOf(CatalogRow("$key-all", "Todo · $title", Popularity.reorderRow(cleanPool), cleanPool.size))
        } else {
            emptyList()
        }
        return derived + cleanGenres + all
    }

    /** La fila de estrenos del servidor manda; si no existe (infantiles), se calcula. */
    private fun premiereRow(
        key: String,
        pool: List<Film>,
        serverRows: List<CatalogRow>,
        kids: Boolean,
        yearNow: Int,
    ): CatalogRow? {
        val server = serverRows.firstOrNull { isPremiereRow(it) }
        if (server != null && server.films.size >= MIN_ROW) {
            return server.copy(films = server.films.distinctBy { it.id })
        }
        val recent = Popularity.realPremieres(pool, limit = ROW_LIMIT, yearNow = yearNow)
            .ifEmpty {
                pool.filter { it.year >= yearNow - 1 }
                    .sortedByDescending { Popularity.rankScore(it, yearNow) }
                    .take(ROW_LIMIT)
            }
        if (recent.size >= MIN_ROW) {
            return CatalogRow("$key-estrenos", "Estrenos", recent, recent.size)
        }
        if (!kids) return null
        // El catálogo infantil es en su mayoría clásico (Shrek, Caillou…): casi no
        // tiene títulos del año. En vez de inventar «Estrenos», se muestra lo más
        // reciente como «Novedades».
        val novelties = pool
            .filter { it.year >= yearNow - 5 && it.posterUrl.isNotBlank() }
            .sortedWith(
                compareByDescending<Film> { it.year }
                    .thenByDescending { Popularity.rankScore(it, yearNow) },
            )
            .take(ROW_LIMIT)
        return novelties.takeIf { it.size >= MIN_ROW }?.let {
            CatalogRow("$key-novedades", "Novedades", it, it.size)
        }
    }

    /** Tendencias de la sección: popularidad + frescura, sin repetir Top 10/Estrenos. */
    private fun trendingRow(
        key: String,
        pool: List<Film>,
        used: List<Film>,
        yearNow: Int,
    ): CatalogRow? {
        val recent = pool
            .filter { it.year >= yearNow - 2 && Popularity.hasSignal(it) }
            .sortedByDescending { Popularity.rankScore(it, yearNow) }
        val fresh = Popularity.withoutRepeats(recent, used, MIN_ROW).take(ROW_LIMIT)
        val fallback = if (fresh.size >= MIN_ROW) {
            fresh
        } else {
            Popularity.withoutRepeats(
                Popularity.topTrending(pool, ROW_LIMIT, yearNow),
                used,
                MIN_ROW,
            ).take(ROW_LIMIT)
        }
        return fallback.takeIf { it.size >= MIN_ROW }?.let {
            CatalogRow("$key-tendencias", "Tendencias", it, it.size)
        }
    }

    /**
     * «En emisión»: la agenda TVMaze ya trae el `titleId` casado por IMDb. Solo se
     * pinta si hay al menos [MIN_ROW] títulos de la sección al aire; si la cobertura
     * de TVMaze es baja, la fila desaparece en vez de mentir.
     */
    private fun onAirRow(
        key: String,
        pool: List<Film>,
        onAirIds: List<String>,
        used: List<Film>,
    ): CatalogRow? {
        if (!isSeriesKey(key) || onAirIds.isEmpty()) return null
        val byId = pool.associateBy { it.id }
        val airing = onAirIds.asSequence()
            .mapNotNull { byId[it] }
            .distinctBy { it.id }
            .take(ROW_LIMIT)
            .toList()
        // Si la fila se queda corta al quitar lo ya mostrado, se permite el traslape:
        // «En emisión» es una clasificación temporal y vale más que una fila vacía.
        val fresh = Popularity.withoutRepeats(airing, used, MIN_ROW).take(ROW_LIMIT)
        return fresh.takeIf { it.size >= MIN_ROW }?.let {
            CatalogRow("$key-en-emision", "En emisión", it, it.size)
        }
    }

    /**
     * Respaldo de «En emisión» cuando la agenda no alcanza: series del año actual o
     * anterior con señal. No se llama «En emisión» para no afirmar que siguen al aire;
     * es la mejor aproximación disponible sin el `status` de TMDB en el índice.
     */
    private fun activeFallbackRow(
        key: String,
        pool: List<Film>,
        used: List<Film>,
        yearNow: Int,
    ): CatalogRow? {
        if (!isSeriesKey(key)) return null
        // Infantiles tiene un catálogo más viejo: se mira una ventana algo más ancha
        // para que la fila exista con 4+ títulos. No se llama «En emisión».
        val window = if (key.startsWith("kids")) 4 else 2
        val recent = pool
            .filter { it.year >= yearNow - window && Popularity.hasSignal(it) }
            .sortedByDescending { Popularity.rankScore(it, yearNow) }
        val fresh = Popularity.withoutRepeats(recent, used, MIN_ROW).take(ROW_LIMIT)
        return fresh.takeIf { it.size >= MIN_ROW }?.let {
            CatalogRow("$key-activas", "Series activas", it, it.size)
        }
    }

    private fun bestRatedRow(
        key: String,
        pool: List<Film>,
        used: List<Film>,
    ): CatalogRow? {
        val fresh = Popularity.withoutRepeats(
            Popularity.bestRated(pool, ROW_LIMIT),
            used,
            MIN_ROW,
        ).take(ROW_LIMIT)
        return fresh.takeIf { it.size >= MIN_ROW }?.let {
            CatalogRow("$key-mejor-valoradas", "Mejor valoradas", it, it.size)
        }
    }

    /** Filas de género que ya calculó el servidor: se conserva su id para paginar. */
    private fun serverGenreRows(serverRows: List<CatalogRow>): List<CatalogRow> =
        serverRows
            .filterNot { isPremiereRow(it) }
            .mapNotNull { row ->
                val films = Popularity.reorderRow(row.films.distinctBy { it.id })
                if (films.isEmpty()) null else row.copy(films = films)
            }

    /**
     * Géneros locales para infantiles (y respaldo si el servidor viejo no manda
     * géneros). Agrupa por el género primario, no por el primero de la lista: si no,
     * todas las películas caerían en «Animación».
     */
    private fun localGenreRows(
        key: String,
        pool: List<Film>,
        kids: Boolean,
    ): List<CatalogRow> {
        val groups = LinkedHashMap<String, MutableList<Film>>()
        for (film in pool) {
            val genre = primaryGenre(film.genres, kids) ?: continue
            groups.getOrPut(genre) { mutableListOf() }.add(film)
        }
        return groups.entries
            .map { (genre, films) -> genre to Popularity.reorderRow(films) }
            .filter { it.second.size >= MIN_ROW }
            .sortedWith(compareByDescending<Pair<String, List<Film>>> { it.second.size }.thenBy { it.first })
            .take(MAX_GENRES)
            .map { (genre, films) ->
                CatalogRow("$key-genero-${slug(genre)}", genre, films, films.size)
            }
    }

    private val GENRE_PRIORITY = listOf(
        "aventura",
        "comedia",
        "fantasia",
        "accion",
        "ciencia ficcion",
        "musical",
        "misterio",
        "drama",
        "romance",
        "terror",
        "documental",
        "animacion",
        "familia",
    )

    private fun primaryGenre(genres: List<String>, kids: Boolean): String? {
        if (genres.isEmpty()) return null
        val normalized = genres.mapNotNull { raw ->
            val text = canonicalGenre(raw)
            if (text.isBlank()) null else text to fold(text)
        }
        if (normalized.isEmpty()) return null
        GENRE_PRIORITY.firstNotNullOfOrNull { candidate ->
            normalized.firstOrNull { (_, folded) -> folded == candidate }?.first
        }?.let { return it }
        if (kids) {
            normalized.firstOrNull { (_, folded) -> folded == "animacion" }?.let { return it.first }
        }
        return normalized.first().first
    }

    /** Los géneros de TMDB a veces llegan en inglés («Sci-Fi & Fantasy»): se unifican. */
    private fun canonicalGenre(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return ""
        return when (fold(text)) {
            "animation", "animacion" -> "Animación"
            "family", "familia" -> "Familia"
            "adventure", "adventures", "aventura", "aventuras" -> "Aventura"
            "comedy", "comedia" -> "Comedia"
            "fantasy", "fantasia" -> "Fantasía"
            "action", "accion" -> "Acción"
            "action & adventure", "action adventure", "accion y aventura" -> "Acción"
            "science fiction", "sci fi", "sci fi & fantasy", "sci fi fantasy",
            "ciencia ficcion" -> "Ciencia ficción"
            "musical", "music", "musica" -> "Música"
            "mystery", "misterio" -> "Misterio"
            "drama" -> "Drama"
            "romance" -> "Romance"
            "horror", "terror" -> "Terror"
            "documentary", "documental" -> "Documental"
            "war", "guerra" -> "Guerra"
            "western", "occidental" -> "Occidental"
            "kids" -> "Infantil"
            else -> text
        }
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NON_ALNUM, " ")
            .trim()

    private fun slug(text: String): String =
        fold(text).replace(' ', '-').ifBlank { "otros" }

    private fun isPremiereRow(row: CatalogRow): Boolean =
        row.id == "estrenos" || row.id.endsWith("-estrenos")

    private fun isSeriesKey(key: String): Boolean =
        key == "series" || key == "anime" || key == "novelas" ||
            key == "kids-series" || key == "kids"

    /** Fila de Top 10: la cuadrícula vuelve a pintar el número 1..10. */
    internal fun isTopTen(row: CatalogRow): Boolean = row.id.endsWith("-top10")

    /** Fila de «En emisión»: la tarjeta lleva el badge temporal. */
    internal fun isOnAir(row: CatalogRow): Boolean = row.id.endsWith("-en-emision")

    /** Fila «Todo · …» de infantiles. */
    internal fun isAllRow(row: CatalogRow): Boolean = row.id.endsWith("-all")

    /** Fila calculada por la app (sin categoría paginable en el servidor). */
    internal fun isDerivedRow(row: CatalogRow): Boolean = !isServerRow(row)

    /** Fila con id real de `/v1/category`: «Ver más» puede pedir páginas. */
    internal fun isServerRow(row: CatalogRow): Boolean = row.id.startsWith("xtv-")
}
