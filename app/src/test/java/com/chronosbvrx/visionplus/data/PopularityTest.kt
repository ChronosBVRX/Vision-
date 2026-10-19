package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-regresión del motor de popularidad: Home, secciones y héroe deben
 * mostrar lo popular primero, no el relleno oscuro con rating huérfano.
 */
class PopularityTest {

    private fun film(
        id: String,
        score: Double = 0.0,
        year: Int = 2025,
        popularity: Double = 0.0,
        votes: Int = 0,
        poster: String = "p",
        video: String = "xtv:$id",
        tmdb: Int = 0,
        title: String = "T $id",
    ) = Film(
        id = id, title = title, year = year, durationMin = 90,
        synopsis = "s", posterUrl = poster, videoUrl = video,
        sourceUrl = "xtv", score = score, popularity = popularity, voteCount = votes,
        tmdbId = tmdb,
    )

    @Test
    fun obscureHighRatingLosesToPopularBlockbuster() {
        val obscure = film("dark", score = 9.0, year = 2026)
        val blockbuster = film("hit", score = 7.7, year = 2025, popularity = 180.0, votes = 200_000)
        assertTrue(Popularity.rankScore(blockbuster, 2026) > Popularity.rankScore(obscure, 2026))
        val top = Popularity.topTrending(listOf(obscure, blockbuster), yearNow = 2026)
        assertEquals("hit", top.first().id)
    }

    @Test
    fun votesAloneGiveConfidenceOverOrphanRating() {
        val orphan = film("orphan", score = 8.5, year = 2024)
        val voted = film("voted", score = 7.0, year = 2024, votes = 5_000)
        assertTrue(Popularity.rankScore(voted, 2026) > Popularity.rankScore(orphan, 2026))
    }

    @Test
    fun realPremieresPreferPopularOverNewerObscure() {
        // Antes: año puro (oscuro 2026 vencía a éxito 2025). Ahora: impacto manda.
        val obscureNew = film("obs26", score = 0.0, year = 2026)
        val hitLastYear = film("hit25", score = 8.2, year = 2025, popularity = 120.0, votes = 80_000)
        val list = Popularity.realPremieres(listOf(obscureNew, hitLastYear), yearNow = 2026)
        assertEquals("hit25", list.first().id)
    }

    @Test
    fun realPremieresOnlyCurrentAndPreviousYear() {
        val old = film("old", score = 9.0, year = 2020, popularity = 300.0, votes = 500_000)
        val recent = film("new", score = 6.5, year = 2026, popularity = 10.0)
        val list = Popularity.realPremieres(listOf(old, recent), yearNow = 2026)
        assertEquals(listOf("new"), list.map { it.id })
    }

    @Test
    fun trendingExcludesSignalFreeFiller() {
        val filler = film("fill", score = 0.0, year = 2026)
        assertTrue(Popularity.topTrending(listOf(filler), yearNow = 2026).isEmpty())
    }

    @Test
    fun heroPrefersPosterOverBlank() {
        val blank = film("blank", score = 8.0, year = 2026, popularity = 200.0, poster = "")
        val withArt = film("art", score = 7.5, year = 2026, popularity = 50.0, poster = "p")
        val hero = Popularity.pickHero(listOf(blank, withArt), emptyList(), null)
        assertEquals("art", hero?.id)
    }

    @Test
    fun dedupeKeepsSingleContentId() {
        val a = film("xtv:ABC", video = "xtv:ABC")
        val b = film("xtv:abc", video = "xtv:ABC|extra")
        assertEquals(1, Popularity.dedupe(listOf(a, b)).size)
    }

    @Test
    fun reorderRowIsDescendingByImpact() {
        val films = listOf(
            film("low", score = 5.0, year = 2010),
            film("mid", score = 7.0, year = 2024, popularity = 30.0),
            film("top", score = 8.0, year = 2025, popularity = 200.0, votes = 100_000),
        )
        val ordered = Popularity.reorderRow(films, yearNow = 2026)
        assertEquals(listOf("top", "mid", "low"), ordered.map { it.id })
    }

    @Test
    fun homePoolOnlyTakesCuratedIndexRows() {
        // Las colecciones de marca del portal (xtv-masnew-*) no entran al pool del Inicio:
        // «HBO Movie»/«All» no deben decidir qué es popular.
        val curated = CatalogRow("genero-accion", "Acción", listOf(film("a")))
        val estrenos = CatalogRow("estrenos", "Estrenos", listOf(film("b")))
        val brandShelf = CatalogRow("xtv-masnew-movies-5", "HBO Movie", listOf(film("c")))
        val pool = Popularity.homePool(listOf(brandShelf), listOf(curated, estrenos))
        assertEquals(setOf("a", "b"), pool.map { it.id }.toSet())
    }

    @Test
    fun popularByKindOrdersByImpactAndFillsWithUnclassified() {
        val popularMovie = film("hit", score = 7.7, popularity = 200.0, votes = 150_000)
            .copy(kind = "movie")
        val nicheMovie = film("niche", score = 9.3, year = 2025).copy(kind = "movie")
        val series = film("show", score = 8.0, popularity = 90.0).copy(kind = "series")
        val rows = Popularity.popularByKind(listOf(nicheMovie, popularMovie, series), "movie", limit = 10, yearNow = 2026)
        assertEquals(listOf("hit", "niche"), rows.map { it.id })
        // El tipo ajeno no se cuela.
        assertTrue(rows.none { it.id == "show" })
    }

    @Test
    fun popularByKindLimitsToSignalWhenThereIsEnough() {
        val films = (1..30).map { i ->
            film("k$i", score = 7.0, popularity = i.toDouble(), votes = i * 1000).copy(kind = "movie")
        } + film("pending", score = 9.9, year = 2026).copy(kind = "movie")
        val rows = Popularity.popularByKind(films, "movie", limit = 20, yearNow = 2026)
        assertEquals(20, rows.size)
        assertTrue(rows.none { it.id == "pending" })
    }

    @Test
    fun homeCuratedBundlesOwnRowsAndHero() {
        val estrenos = CatalogRow("estrenos", "Estrenos", listOf(film("e1", score = 8.0, year = 2026, popularity = 100.0)))
        val movies = (1..4).map { i -> film("m$i", score = 7.0, year = 2025, popularity = i * 10.0).copy(kind = "movie") }
        val series = (1..4).map { i -> film("s$i", score = 7.0, year = 2025, popularity = i * 10.0).copy(kind = "series") }
        val genre = CatalogRow("genero-accion", "Acción", movies + series)
        val curated = Popularity.homeCurated(listOf(estrenos), listOf(estrenos, genre))
        assertEquals(setOf("e1") + movies.map { it.id } + series.map { it.id }, curated.pool.map { it.id }.toSet())
        assertTrue(curated.top10.isNotEmpty())
        assertTrue(curated.premieres.isNotEmpty())
        assertEquals(listOf("home-kind-movie", "home-kind-series"), curated.kindRows.map { it.id })
        assertTrue(curated.hero != null)
    }

    @Test
    fun homeCuratedFallsBackToServerPremieres() {
        // El pool no tiene estrenos del año: manda la hilera «Estrenos y Novedades» del servidor.
        val old = film("old1", score = 7.0, year = 2010, popularity = 30.0)
        val estrenos = CatalogRow("estrenos", "Estrenos", listOf(film("e1", score = 0.0, year = 0)))
        val curated = Popularity.homeCurated(listOf(estrenos), listOf(CatalogRow("genero-x", "X", listOf(old))))
        assertEquals(listOf("e1"), curated.premieres.map { it.id })
    }

    @Test
    fun topPopularRanksRealPopularityOverRecency() {
        // El Top 10 es de popularidad, no de estrenos: un clásico popular le gana a un
        // estreno oscuro con mejor rating.
        val classic = film("classic", score = 7.2, year = 2011, popularity = 250.0, votes = 300_000)
        val recentNiche = film("niche26", score = 9.0, year = 2026, popularity = 6.0, votes = 40)
        val list = Popularity.topPopular(emptyList(), listOf(recentNiche, classic), yearNow = 2026)
        assertEquals("classic", list.first().id)
    }

    @Test
    fun topPopularKeepsTheServerTrendingOrder() {
        val a = film("a", popularity = 10.0, votes = 100)
        val b = film("b", popularity = 90.0, votes = 50)
        val list = Popularity.topPopular(listOf(b, a), emptyList())
        assertEquals(listOf("b", "a"), list.map { it.id })
    }

    @Test
    fun homeCuratedTop10ComesFromTrendingRowNotPremieres() {
        // Regresión del reporte: el Top 10 salía del mismo pool reciente que
        // «Novedades y Estrenos» y ambas filas mostraban casi los mismos títulos.
        val classic = film("classic", score = 7.0, year = 2011, popularity = 300.0, votes = 400_000)
        val fresh = film("fresh", score = 9.0, year = 2026, popularity = 15.0, votes = 200)
        val tendencias = CatalogRow("tendencias", "Tendencias", listOf(classic))
        val estrenos = CatalogRow("estrenos", "Estrenos", listOf(fresh, classic))
        val curated = Popularity.homeCurated(listOf(estrenos), listOf(tendencias, estrenos), yearNow = 2026)
        assertEquals("classic", curated.top10.first().id)
        assertTrue(curated.premieres.any { it.id == "fresh" })
        assertTrue(curated.premieres.none { it.id == "classic" })
        assertTrue(curated.top10.first().id != curated.premieres.first().id)
    }

    @Test
    fun homeCuratedTop10FallsBackWhenServerHasNoTrendingRow() {
        // Servidor viejo o caché previa (sin «tendencias»): manda el motor de siempre.
        val rated = film("rated", score = 8.0, year = 2026)
        val pooled = CatalogRow("genero-drama", "Drama", listOf(rated))
        val curated = Popularity.homeCurated(emptyList(), listOf(pooled), yearNow = 2026)
        assertEquals(listOf("rated"), curated.top10.map { it.id })
    }

    @Test
    fun dedupeCollapsesSeasonsOfTheSameShowByTmdbId() {
        // El portal reparte «Link Click» en T1..T4: mismo tmdb_id, distinta copia.
        val t1 = film("lc1", tmdb = 123542, title = "Link Click", year = 2021)
        val t2 = film("lc2", tmdb = 123542, title = "Link Click", year = 2023)
        val other = film("other", tmdb = 99, title = "Otra serie", year = 2022)
        assertEquals(listOf("lc1", "other"), Popularity.dedupe(listOf(t1, t2, other)).map { it.id })
    }

    @Test
    fun dedupeDoesNotMergeDifferentShowsFromABadTmdbMatch() {
        // Un match TMDB equivocado no puede borrar una tarjeta que es otra obra.
        val movie = film("movie", tmdb = 1433, title = "El espinazo del diablo")
        val show = film("show", tmdb = 1433, title = "Agente de Familia")
        assertEquals(2, Popularity.dedupe(listOf(movie, show)).size)
    }

    @Test
    fun topPopularCollapsesDubVariantsWithSameTmdbId() {
        // «Los Simpson» (Latino) y «Os Simpsons» (Portugués) son la misma obra.
        val latino = film("lat", popularity = 346.0, votes = 11_069, year = 1989, tmdb = 456, title = "Los Simpson - Español Latino")
        val portugues = film("pt", popularity = 340.0, votes = 11_069, year = 1989, tmdb = 456, title = "Os Simpsons")
        val list = Popularity.topPopular(emptyList(), listOf(portugues, latino))
        assertEquals(1, list.size)
        // Aunque la copia portuguesa tenga más popularidad, se pinta la doblada al español.
        assertEquals("lat", list.first().id)
    }

    @Test
    fun realPremieresIgnoresAnOrphanPerfectScore() {
        // Un «10.0» con 4 votos y popularidad 2 no puede colarse entre las novedades
        // populares: manda la popularidad y el rating solo pesa con votos.
        val orphan = film("orphan", score = 10.0, year = 2026, popularity = 2.0, votes = 4)
        val hit = film("hit", score = 7.6, year = 2026, popularity = 500.0, votes = 900)
        val list = Popularity.realPremieres(listOf(orphan, hit), yearNow = 2026)
        assertEquals("hit", list.first().id)
        assertTrue(Popularity.noveltyScore(hit, 2026) > Popularity.noveltyScore(orphan, 2026) * 2)
    }

    @Test
    fun realPremieresDropsAnyTitleWithoutPopularityOrVotes() {
        val filler = film("filler", score = 9.9, year = 2026)
        assertTrue(Popularity.realPremieres(listOf(filler), yearNow = 2026).isEmpty())
    }

    @Test
    fun homeCuratedKindRowsDoNotRepeatTheRowsAbove() {
        // 30 películas populares: el Top 10 y Novedades se llevan las primeras y la
        // fila «Películas populares» no debe volver a pintar las mismas tarjetas.
        val movies = (1..30).map { i ->
            film("m$i", score = 7.0, year = 2026, popularity = 400.0 - i, votes = 5_000)
                .copy(kind = "movie", title = "Película $i")
        }
        val genre = CatalogRow("genero-accion", "Acción", movies)
        val curated = Popularity.homeCurated(emptyList(), listOf(genre), yearNow = 2026)
        val shownAbove = (curated.top10 + curated.premieres).map { it.id }.toSet()
        val kindFilms = curated.kindRows.first { it.id == "home-kind-movie" }.films
        assertTrue(kindFilms.isNotEmpty())
        assertTrue(kindFilms.none { it.id in shownAbove })
    }

    @Test
    fun withoutRepeatsKeepsTheRowAliveWhenEverythingRepeats() {
        val a = film("a", tmdb = 1, title = "A")
        val row = listOf(a)
        assertEquals(listOf("a"), Popularity.withoutRepeats(row, listOf(a)).map { it.id })
    }

    @Test
    fun topPopularCollapsesPortalPluralDuplicates() {
        // El portal duplica la misma ficha cambiando el plural (doblajes): una sola tarjeta.
        val latino = film("simpson-lat", popularity = 346.0, votes = 11_069, year = 1989)
            .copy(title = "Los Simpson")
        val espana = film("simpson-es", popularity = 346.0, votes = 11_069, year = 1989)
            .copy(title = "Los Simpsons")
        val list = Popularity.topPopular(emptyList(), listOf(latino, espana))
        assertEquals(1, list.size)
        assertEquals("simpson-lat", list.first().id)
    }

    @Test
    fun topPopularKeepsRealSequelsApart() {
        val one = film("spider1", popularity = 300.0, year = 2026).copy(title = "Spider-Man")
        val two = film("spider2", popularity = 200.0, year = 2026).copy(title = "Spider-Man 2")
        val other = film("other", popularity = 100.0, year = 2019).copy(title = "The Boys")
        val list = Popularity.topPopular(emptyList(), listOf(one, two, other))
        assertEquals(listOf("spider1", "spider2", "other"), list.map { it.id })
    }
}
