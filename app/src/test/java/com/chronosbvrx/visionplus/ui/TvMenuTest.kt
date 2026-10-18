package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * El menú lateral de la Smart TV debe ofrecer «Deportes en vivo»: antes de esta entrada la
 * sección solo era alcanzable por el acceso rápido del Inicio, así que desde cualquier otra
 * pantalla de la tele el usuario no encontraba los eventos deportivos en el menú.
 *
 * Los pilares de la tele son los MISMOS que los del teléfono (una sola lista de referencia).
 */
class TvMenuTest {

    @Test
    fun sideRailOffersSportsEvents() {
        val sports = TV_RAIL_SPECS.firstOrNull { it.target == Route.Sports }
        assertNotNull("el menú lateral de la TV debe incluir Deportes en vivo", sports)
        assertEquals("Deportes en vivo", sports?.label)
    }

    @Test
    fun sportsSitsRightAfterLiveTv() {
        val labels = TV_RAIL_SPECS.map { it.label }
        assertEquals(labels.indexOf("En vivo") + 1, labels.indexOf("Deportes en vivo"))
    }

    @Test
    fun theTvRailKeepsTheSamePillarsAsThePhone() {
        val tv = TV_RAIL_SPECS.map { it.label }
        val phone = TABS.map { it.label }
        // Los pilares compartidos viven en los dos menús (la tele suma «Buscar» en el suyo; el
        // teléfono suma Descargas, Control remoto y Acerca de).
        for (pillar in listOf("Inicio", "Películas", "Series", "En vivo", "Deportes en vivo", "Mi lista")) {
            org.junit.Assert.assertTrue("falta el pilar $pillar en la tele", pillar in tv)
            org.junit.Assert.assertTrue("falta el pilar $pillar en el teléfono", pillar in phone)
        }
        org.junit.Assert.assertTrue("la tele conserva su buscador en el riel", "Buscar" in tv)
    }
}
