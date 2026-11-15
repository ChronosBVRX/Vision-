package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Submenús móviles de Películas y Series: espejo de los hubs de la TV.
 * Si alguien los vacía o cambia las rutas, esto falla antes de publicar.
 */
class PhoneHubMenuTest {

    @Test
    fun `movies hub holds the catalog and the kids catalog`() {
        val hub = HUB_GROUPS[Route.Section("movies", "Películas")]
        assertTrue("falta el hub de Películas", hub != null)
        assertEquals(listOf("Películas", "Películas Infantiles"), hub!!.map { it.label })
        val kids = hub.last().route as Route.Category
        assertEquals("kids-movies", kids.key)
    }

    @Test
    fun `series hub holds kids anime and novelas`() {
        val hub = HUB_GROUPS[Route.Section("series", "Series")]
        assertTrue("falta el hub de Series", hub != null)
        assertEquals(
            listOf("Series", "Series Infantiles", "Anime", "Novelas"),
            hub!!.map { it.label },
        )
        val kids = hub.first { it.label == "Series Infantiles" }.route as Route.Category
        assertEquals("kids-series", kids.key)
        assertTrue(hub.any { it.route == Route.Section("anime", "Anime") })
        assertTrue(hub.any { it.route == Route.Section("novelas", "Novelas") })
    }

    @Test
    fun `every hub child is a root destination`() {
        val children = HUB_GROUPS.values.flatten()
        assertTrue("hub vacío", children.size >= 6)
        assertTrue(children.all { it.route is Route.Section || it.route is Route.Category })
        assertTrue(children.map { it.label }.distinct().size == children.size)
    }
}
