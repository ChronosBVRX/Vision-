package com.chronosbvrx.visionplus.ui.tv

import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La TV en vivo debe encontrar el canal aunque el usuario no escriba el nombre
 * exacto: «tudm», «fox sport», «star chanel», «win plus» o «mexico».
 */
class TvLiveSearchTest {

    private fun channel(name: String, number: Int = 0) = LiveChannel(
        name = name,
        url = "xtvlive:test-${name.lowercase().replace(' ', '-')}",
        number = number,
    )

    private val channels = listOf(
        channel("CANAL 5 HD", 1),
        channel("Las Estrellas HD", 2),
        channel("TUDN MX HD", 3),
        channel("TUDN USA HD", 4),
        channel("Fox Sports 1 MX", 5),
        channel("Fox Sports Premium MX HD", 6),
        channel("Fox Deportes HD", 7),
        channel("TNT SPORTS HD+", 8),
        channel("TNT SPORTS PREMIUM HD", 9),
        channel("WIN Sports+ COL HD", 10),
        channel("WIN Sports COL HD", 11),
        channel("Star Channel HD", 12),
        channel("Animal Planet HD", 13),
        channel("ESPN / Star+ HD", 14),
    )

    private fun names(query: String, country: String? = null): List<String> =
        liveSearchResults(channels, query, country).map { it.name }

    @Test
    fun `exact and compact names still win`() {
        assertEquals("CANAL 5 HD", names("canal5").first())
        assertEquals("TUDN MX HD", names("tudn").first())
    }

    @Test
    fun `small typos and token order are tolerated`() {
        assertTrue(names("tudm").first().contains("TUDN"))
        assertTrue(names("fox sport").first().startsWith("Fox Sports"))
        assertTrue(names("star chanel").first().startsWith("Star Channel"))
        assertTrue(names("win plus").first().startsWith("WIN Sports+"))
        assertTrue(names("premiun").any { it.contains("Premium") })
        assertTrue(names("animal plan").first().startsWith("Animal Planet"))
    }

    @Test
    fun `country words bring their channels`() {
        val mexican = names("mexico")
        assertTrue(mexican.isNotEmpty())
        assertTrue(mexican.all { com.chronosbvrx.visionplus.data.LiveTaxonomy.countryFor(it) == "México" })
        assertTrue(names("fox mexico").any { it == "Fox Sports 1 MX" })
    }

    @Test
    fun `country filter is applied before matching`() {
        val usa = names("fox", "EE. UU.")
        assertTrue(usa.isNotEmpty())
        assertTrue(usa.all { com.chronosbvrx.visionplus.data.LiveTaxonomy.countryFor(it) == "EE. UU." })
    }

    @Test
    fun `blank query still offers the popular list`() {
        val all = names("")
        assertTrue(all.isNotEmpty())
        assertEquals("CANAL 5 HD", all.first())
    }
}
