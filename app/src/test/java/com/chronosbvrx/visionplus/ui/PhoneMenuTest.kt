package com.chronosbvrx.visionplus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * El menú lateral del móvil/tablet debe ofrecer «Deportes en vivo»: antes de esta entrada
 * la sección solo era alcanzable por el acceso rápido del Inicio, así que desde cualquier
 * otra pantalla el usuario no encontraba los eventos deportivos en el menú.
 */
class PhoneMenuTest {

    @Test
    fun sideMenuOffersSportsEvents() {
        val sports = TABS.firstOrNull { it.route == Route.Sports }
        assertNotNull("el menú lateral debe incluir Deportes en vivo", sports)
        assertEquals("Deportes en vivo", sports?.label)
    }

    @Test
    fun sportsSitsRightAfterLiveTv() {
        val labels = TABS.map { it.label }
        assertEquals(labels.indexOf("En vivo") + 1, labels.indexOf("Deportes en vivo"))
    }
}
