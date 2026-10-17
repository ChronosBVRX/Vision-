package com.chronosbvrx.visionplus.data.gee

import com.chronosbvrx.visionplus.data.LiveChannel
import com.chronosbvrx.visionplus.data.gee.GeeBackstop.LiveOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El respaldo de un canal caído: la parrilla del partido cruzada con el catálogo de la casa.
 * El blindaje: solo la MISMA cadena (marca y edición exactas, nunca un parecido), solo cuando la
 * fuente deportiva no puede servirla EN VIVO, y jamás por encima de ella.
 */
class GeeBackstopTest {

    private fun ch(name: String, code: String = name) = LiveChannel(name = name, url = "xtvlive:$code")

    /** El catálogo real (nombres tal cual los sirve la casa). */
    private val xtv = listOf(
        ch("TELEMUNDO EAST HD", "tm-east"),
        ch("TELEMUNDO WEST HD", "tm-west"),
        ch("TELEMUNDO 51", "tm-51"),
        ch("ViX Deportes", "vix"),
        ch("TUDN MX HD", "tudn-mx"),
        ch("TUDN USA HD", "tudn-usa"),
        ch("CANAL 5 HD", "canal5"),
        ch("Fox Sports 1 MX", "fs1"),
        ch("FOX SPORTS 2 HD", "fs2"),
        ch("TNT SPORTS HD", "tnt"),
        ch("TNT SERIES HD", "tnt-series"),
        ch("ESPN DEPORTES HD", "espn-d"),
        ch("AMAZON PRIME HD", "prime"),
    )

    @Test
    fun theChivasCaseGivesTelemundoAndVixButNotTudnWhichIsAlreadyLive() {
        // La parrilla real del partido y lo que la fuente deportiva puede servir hoy:
        // Telemundo está caído, TUDN entra por una transmisión directa viva y ViX ni existe allá.
        val out = GeeBackstop.needed(
            guide = listOf("Telemundo", "TUDN", "ViX Deportes"),
            liveOptions = listOf(
                LiveOption("Telemundo", alive = false),
                LiveOption("TUDN MEXICO FHD", alive = true),
                LiveOption("CANAL MX FHD", alive = true),
            ),
            xtv = xtv,
        )
        assertEquals(listOf("Telemundo", "ViX Deportes"), out.map { it.guideName })
        assertEquals("TELEMUNDO EAST HD", out[0].channel.name)
        assertEquals("xtvlive:vix", out[1].channel.url)
    }

    @Test
    fun whileTheSportsSourceIsAliveThereIsNoFallbackAtAll() {
        val out = GeeBackstop.needed(
            guide = listOf("Telemundo", "TUDN"),
            liveOptions = listOf(LiveOption("Telemundo", alive = true), LiveOption("TUDN MX", alive = true)),
            xtv = xtv,
        )
        assertTrue("con la fuente viva no se toca nada", out.isEmpty())
    }

    @Test
    fun theFallbackGoesAwayWhenTheSportsSourceComesBack() {
        val guide = listOf("Telemundo")
        val down = GeeBackstop.needed(guide, listOf(LiveOption("Telemundo", alive = false)), xtv)
        val up = GeeBackstop.needed(guide, listOf(LiveOption("Telemundo", alive = true)), xtv)
        assertEquals(1, down.size)
        assertTrue("recuperada la fuente, ella vuelve a ser la única primera opción", up.isEmpty())
    }

    @Test
    fun neverAPassingResemblance() {
        val tnt = GeeBackstop.needed(listOf("TNT Sports"), listOf(LiveOption("TNT SPORTS HD", alive = false)), xtv)
        assertEquals("TNT SPORTS HD", tnt.single().channel.name)
        // Sin la señal deportiva en el catálogo, «TNT SERIES» JAMÁS la sustituye.
        val onlySeries = xtv.filter { it.name.contains("TNT SERIES") }
        assertTrue(GeeBackstop.needed(listOf("TNT Sports"), emptyList(), onlySeries).isEmpty())
    }

    @Test
    fun theEditionMustMatchAndTheBaseSignalWins() {
        // «Fox Sports» (sin número) no se cubre con la señal 2.
        val onlyTwo = xtv.filter { it.name == "FOX SPORTS 2 HD" }
        assertTrue(GeeBackstop.needed(listOf("Fox Sports"), emptyList(), onlyTwo).isEmpty())
        // Con la base y la 2, la base es la que va (y «Fox Sports 1 MX» vale como base).
        val both = listOf(ch("Fox Sports 1 MX", "fs1"), ch("FOX SPORTS 2 HD", "fs2"))
        val out = GeeBackstop.needed(listOf("Fox Sports"), emptyList(), both)
        assertEquals("Fox Sports 1 MX", out.single().channel.name)
    }

    @Test
    fun theCountryIsOnlyDemandedWhenTheGuideNamesIt() {
        // «TUDN USA» no se cubre con la señal mexicana…
        val usa = GeeBackstop.needed(listOf("TUDN USA"), emptyList(), listOf(ch("TUDN MX HD", "mx")))
        assertTrue(usa.isEmpty())
        // …pero sí con la suya.
        val both = listOf(ch("TUDN MX HD", "mx"), ch("TUDN USA HD", "usa"))
        assertEquals("TUDN USA HD", GeeBackstop.needed(listOf("TUDN USA"), emptyList(), both).single().channel.name)
        // Sin país en la parrilla, la señal mexicana es la preferida.
        assertEquals("TUDN MX HD", GeeBackstop.needed(listOf("TUDN"), emptyList(), both).single().channel.name)
    }

    @Test
    fun streamingServicesAreNotChannelsAndYieldNothing() {
        val out = GeeBackstop.needed(
            guide = listOf("Peacock", "fuboTV", "Amazon Prime Video"),
            liveOptions = emptyList(),
            xtv = xtv,
        )
        assertTrue("no son canales de TV: no hay respaldo que ofrecer", out.isEmpty())
    }

    @Test
    fun oneRowPerGuideChannelAndCapped() {
        val repeated = GeeBackstop.needed(listOf("TUDN", "tudn", "TUDN "), emptyList(), xtv)
        assertEquals(1, repeated.size)
        val many = GeeBackstop.needed(
            guide = listOf("Telemundo", "TUDN", "ViX Deportes", "Fox Sports", "TNT Sports", "ESPN Deportes"),
            liveOptions = emptyList(),
            xtv = xtv,
        )
        assertTrue("la lista del modal no se vuelve un maratón", many.size <= GeeBackstop.MAX_BACKSTOPS)
    }

    @Test
    fun theAliasCalienteTvLooksForFoxOneAndNothingElse() {
        val withFoxOne = xtv + ch("FOX ONE HD", "foxone")
        val out = GeeBackstop.needed(listOf("Caliente TV"), emptyList(), withFoxOne)
        assertEquals("FOX ONE HD", out.single().channel.name)
        // Sin FOX One en el catálogo, «Fox Sports» NO es su respaldo.
        assertTrue(GeeBackstop.needed(listOf("Caliente TV"), emptyList(), xtv).isEmpty())
    }

    @Test
    fun theExactNameWinsOverTheRegionalFeeds() {
        val exact = xtv + ch("Telemundo", "tm")
        assertEquals("Telemundo", GeeBackstop.needed(listOf("Telemundo"), emptyList(), exact).single().channel.name)
        // Sin nombre exacto, los feeds nacionales (East/West) antes que una estación local.
        val out = GeeBackstop.needed(listOf("Telemundo"), emptyList(), xtv)
        assertEquals("TELEMUNDO EAST HD", out.single().channel.name)
    }

    @Test
    fun aGuideWithoutChannelsYieldsNothing() {
        assertTrue(GeeBackstop.needed(listOf("", "   "), emptyList(), xtv).isEmpty())
        assertTrue(GeeBackstop.needed(listOf("Telemundo"), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun theBackstopIsFoundByItsGuideAndTheOrphansAreTheOnesWithoutARow() {
        val backs = GeeBackstop.needed(listOf("Telemundo", "TUDN"), emptyList(), xtv)
        assertEquals(2, backs.size)
        assertEquals("Telemundo", GeeBackstop.forGuide(backs, "telemundo")?.guideName)
        assertEquals("TUDN", GeeBackstop.forGuide(backs, "TUDN")?.guideName)
        assertNull(GeeBackstop.forGuide(backs, "Fox Sports"))
        // «TUDN» sí tiene fila en la parrilla resuelta; «Telemundo» también: no hay huérfanos.
        assertTrue(GeeBackstop.orphans(backs, listOf("Telemundo", "TUDN")).isEmpty())
        // Si la parrilla no resolvió «Telemundo», su respaldo va en su propia sección.
        assertEquals(
            listOf("Telemundo"),
            GeeBackstop.orphans(backs, listOf("TUDN")).map { it.guideName },
        )
    }
}
