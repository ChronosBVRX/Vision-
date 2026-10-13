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
    ) = Film(
        id = id, title = "T $id", year = year, durationMin = 90,
        synopsis = "s", posterUrl = poster, videoUrl = video,
        sourceUrl = "xtv", score = score, popularity = popularity, voteCount = votes,
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
}
