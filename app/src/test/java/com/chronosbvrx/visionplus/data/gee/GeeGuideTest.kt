package com.chronosbvrx.visionplus.data.gee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cruce de la guía del partido con los canales 24/7 del catálogo deportivo:
 * «Fox Sports» → FOX SPORTS 1 MX (no FOX DEPORTES ni el canal general «Fox»),
 * «TUDN» → TUDN MX, «Azteca 7»/«Canal 5» dentro de los grupos agregadores…
 * Si esto se rompe, el usuario cae en un canal que está dando otra cosa.
 */
class GeeGuideTest {

    private fun link(name: String, alive: Boolean? = true) = GeeSportsSource.GeeLink(
        name = name,
        link = "https://cdn/$name.m3u8",
        headers = emptyMap(),
        type = "hls",
        drm = "",
        alive = alive,
    )

    private fun group(name: String, vararg links: GeeSportsSource.GeeLink) = GeeSportsSource.GeeGroup(
        id = name.lowercase().replace(' ', '-'),
        name = name,
        logo = "",
        alive = links.count { it.alive == true },
        total = links.size,
        channels = listOf(GeeSportsSource.GeeChannel(name = name, logo = "", links = links.toList())),
    )

    private val groups = listOf(
        group(
            "FOX Sports",
            link("FOX DEPORTES"),
            link("FOX SPORTS 1 MX"),
            link("FOX SPORTS 1 US"),
            link("FOX SPORTS 2 MX"),
        ),
        group("TUDN", link("TUDN MX", alive = null), link("TUDN USA")),
        group("South American Sports", link("Azteca 7"), link("Canal 5")),
    )

    @Test
    fun guideFoxSportsPicksTheMexicanFeed() {
        val out = GeeGuide.channelsFor(listOf("Fox Sports"), groups)
        assertEquals(1, out.size)
        assertEquals("FOX SPORTS 1 MX", out.single().title)
    }

    @Test
    fun guideFoxDeportesIsItsOwnChannel() {
        val out = GeeGuide.channelsFor(listOf("FOX Deportes"), groups)
        assertEquals("FOX DEPORTES", out.single().title)
    }

    @Test
    fun guideEditionIsRespected() {
        assertEquals("FOX SPORTS 2 MX", GeeGuide.channelsFor(listOf("Fox Sports 2 Mexico"), groups).single().title)
    }

    @Test
    fun guideFindsChannelsInsideAggregatorGroups() {
        assertEquals("Azteca 7", GeeGuide.channelsFor(listOf("Azteca 7"), groups).single().title)
        assertEquals("Canal 5", GeeGuide.channelsFor(listOf("Canal 5 Televisa"), groups).single().title)
    }

    @Test
    fun calienteIsNotInTheCatalog() {
        // La fuente (hoy) no trae Caliente/FOX One: se dice con la verdad, no se inventa.
        assertTrue(GeeGuide.channelsFor(listOf("Caliente TV"), groups).isEmpty())
    }

    @Test
    fun calienteTvResolvesToFoxOneWhenTheCatalogHasIt() {
        // Caliente TV se convirtió en FOX One en México: en cuanto su catálogo
        // lo traiga, el partido se ofrece solo (misma señal).
        val withOne = groups + group("Nuevos", link("FOX ONE"))
        val out = GeeGuide.channelsFor(listOf("Caliente TV"), withOne)
        assertEquals("FOX ONE", out.single().title)
    }

    @Test
    fun generalFoxNeverResolvesAsSports() {
        val withGeneral = groups + group("Extra", link("Fox"), link("Fox channel"))
        val out = GeeGuide.channelsFor(listOf("Fox Sports"), withGeneral)
        assertEquals("FOX SPORTS 1 MX", out.single().title)
    }
}
