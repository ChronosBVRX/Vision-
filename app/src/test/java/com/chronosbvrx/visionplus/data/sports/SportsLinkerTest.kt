package com.chronosbvrx.visionplus.data.sports

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosbvrx.visionplus.data.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regresión de la agenda de fútbol: si esto se rompe, partidos salen con
 * "Sin canal en tu lista" o la confirmación EPG falla en silencio.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SportsLinkerTest {

    private fun channel(name: String, code: String = name) = LiveChannel(
        name = name,
        url = "xtvlive:$code",
        logo = null,
        group = "Deportes",
    )

    private fun match(home: String = "Guadalajara", away: String = "América"): SportMatch {
        fun team(name: String) = SportTeam(name.lowercase(), name, name.take(3).uppercase(), "", null)
        return SportMatch(
            id = "m1",
            league = SportLeague("mex.1", "Liga MX", ""),
            home = team(home),
            away = team(away),
            kickoffMs = System.currentTimeMillis(),
            status = "in",
            detail = "",
            venue = "",
            channels = listOf("TUDN"),
            broadcasts = emptyList(),
        )
    }

    @Test
    fun normStripsAccentsAndPunctuation() {
        // norm solo normaliza (no quita stop words como "de"; eso lo hace tokens).
        assertEquals("atletico de san luis", SportsLinker.norm("Atlético de San Luis"))
        assertEquals("america", SportsLinker.norm("América"))
        assertEquals("tijuana", SportsLinker.norm("  Tijuana!! "))
    }

    @Test
    fun tokensDropStopWords() {
        // "club", "de", "la" no aportan; "5" (canal) sí se conserva por ser dígito.
        assertEquals(listOf("america"), SportsLinker.tokens("Club América"))
        assertEquals(listOf("canal", "5"), SportsLinker.tokens("Canal 5"))
        assertEquals(listOf("fox", "sports"), SportsLinker.tokens("Fox Sports"))
    }

    @Test
    fun resolvePrefersMexicanFeed() {
        val channels = listOf(
            channel("TUDN USA", "tudn_usa"),
            channel("TUDN MX", "tudn_mx"),
        )
        val resolved = SportsLinker.resolveChannels(listOf("TUDN"), channels)
        assertEquals(1, resolved.size)
        assertEquals("xtvlive:tudn_mx", resolved.single().url)
    }

    @Test
    fun resolveMatchesByTokenSequence() {
        val channels = listOf(channel("Fox Sports 1 MX", "fox1"), channel("Fox News", "foxnews"))
        val resolved = SportsLinker.resolveChannels(listOf("Fox Sports"), channels)
        assertEquals(listOf("xtvlive:fox1"), resolved.map { it.url })
    }

    @Test
    fun resolveCapsCandidatesAndDedupes() {
        val channels = (1..10).map { channel("Canal $it", "c$it") }
        val names = (1..10).map { "Canal $it" }
        val resolved = SportsLinker.resolveChannels(names, channels)
        assertEquals(8, resolved.size)
        // La misma cadena dos veces no duplica el canal.
        val twice = SportsLinker.resolveChannels(listOf("TUDN", "TUDN"), listOf(channel("TUDN MX")))
        assertEquals(1, twice.size)
    }

    @Test
    fun resolveMatchesLiveSoccerTvNames() {
        val channels = listOf(
            channel("Canal 5 HD MX", "c5"),
            channel("Azteca 7 HD", "az7"),
            channel("TUDN MX", "tudn"),
            channel("Fox Sports 1 MX", "fox1"),
            channel("Fox Sports 2 MX", "fox2"),
            channel("ESPN 2 MX", "espn2"),
        )
        assertEquals(listOf("xtvlive:c5"), SportsLinker.resolveChannels(listOf("Canal 5 Televisa"), channels).map { it.url })
        assertEquals(listOf("xtvlive:az7"), SportsLinker.resolveChannels(listOf("Azteca Deportes En Vivo"), channels).map { it.url })
        assertEquals(listOf("xtvlive:tudn"), SportsLinker.resolveChannels(listOf("TUDN En Vivo"), channels).map { it.url })
        assertEquals(listOf("xtvlive:fox1"), SportsLinker.resolveChannels(listOf("FOX One"), channels).map { it.url })
        assertEquals(listOf("xtvlive:fox2"), SportsLinker.resolveChannels(listOf("FOX Sports 2 Mexico"), channels).map { it.url })
        assertEquals(listOf("xtvlive:espn2"), SportsLinker.resolveChannels(listOf("ESPN 2 Mexico"), channels).map { it.url })
    }

    @Test
    fun resolveEmptyWhenNoChannels() {
        assertTrue(SportsLinker.resolveChannels(listOf("TUDN"), emptyList()).isEmpty())
        assertTrue(SportsLinker.resolveChannels(emptyList(), listOf(channel("TUDN"))).isEmpty())
    }

    @Test
    fun aliasesCoverNicknames() {
        // Chivas = Guadalajara, Xolos = Tijuana, Rayados = Monterrey.
        assertTrue(SportsLinker.teamTokens("Guadalajara").contains("chivas"))
        assertTrue(SportsLinker.teamTokens("Tijuana").contains("xolos"))
        assertTrue(SportsLinker.teamTokens("Monterrey").contains("rayados"))
        assertTrue(SportsLinker.teamTokens("Paris Saint Germain").contains("psg"))
    }

    @Test
    fun mentionsBothNeedsEachTeam() {
        val m = match(home = "Guadalajara", away = "América")
        assertTrue(SportsLinker.mentionsBoth("Chivas vs América en vivo", m))
        assertFalse(SportsLinker.mentionsBoth("Chivas vs Cruz Azul en vivo", m))
        assertFalse(SportsLinker.mentionsBoth("Partido en vivo", m))
    }

    @Test
    fun channelCodeOnlyForXtvRefs() {
        assertEquals("tudn_mx", SportsLinker.channelCode(channel("TUDN", "tudn_mx")))
        assertNull(SportsLinker.channelCode(LiveChannel("Web", "https://example.com/a.m3u8")))
    }

    @Test
    fun windowCoversLiveAndKickoffSoon() {
        val now = System.currentTimeMillis()
        fun m(status: String, kickoff: Long, playing: Boolean = status == "in") = match().copy(
            status = status, kickoffMs = kickoff,
        ).let {
            // SportMatch.playing deriva de status; no hay que forzar nada más.
            assertEquals(playing, it.playing)
            it
        }
        assertTrue(SportsLinker.isWindow(m("in", now)))
        assertTrue(SportsLinker.isWindow(m("pre", now + 10 * 60_000)))
        assertFalse(SportsLinker.isWindow(m("pre", now + 60 * 60_000)))
        assertFalse(SportsLinker.isWindow(m("post", now)))
        assertFalse(SportsLinker.isWindow(m("pre", 0L)))
    }

    @Test
    fun quickResolveResolvesCandidatesSynchronously() {
        SportsLinker.clearCache()
        val m = match().copy(id = "quick1", channels = listOf("TUDN"))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val link = SportsLinker.quickResolve(context, m)
        val linkCached = SportsLinker.quickResolve(context, m)
        assertEquals(link, linkCached)
    }

    @Test
    fun quickResolveAllReturnsMapInstantly() {
        SportsLinker.clearCache()
        val matches = listOf(
            match().copy(id = "m_a", channels = listOf("Canal 5")),
            match().copy(id = "m_b", channels = listOf("Azteca 7")),
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val map = SportsLinker.quickResolveAll(context, matches)
        assertEquals(2, map.size)
        assertTrue(map.containsKey("m_a"))
        assertTrue(map.containsKey("m_b"))
    }

    @Test
    fun brandEditionIsolationPreventsWrongNumberMatching() {
        val channels = listOf(
            channel("ESPN 1 MX", "espn1"),
            channel("ESPN 2 MX", "espn2"),
            channel("ESPN 3 MX", "espn3"),
            channel("Fox Sports 1 MX", "fox1"),
            channel("Fox Sports 2 MX", "fox2"),
        )
        // Búsqueda de ESPN base (o 1) NO debe devolver ESPN 2 ni ESPN 3
        val resEspnBase = SportsLinker.resolveChannels(listOf("ESPN"), channels)
        assertEquals(listOf("xtvlive:espn1"), resEspnBase.map { it.url })

        // Búsqueda de ESPN 2 NO debe devolver ESPN 1 ni ESPN 3
        val resEspn2 = SportsLinker.resolveChannels(listOf("ESPN 2"), channels)
        assertEquals(listOf("xtvlive:espn2"), resEspn2.map { it.url })

        // Búsqueda de Fox Sports base NO debe devolver Fox Sports 2
        val resFoxBase = SportsLinker.resolveChannels(listOf("Fox Sports"), channels)
        assertEquals(listOf("xtvlive:fox1"), resFoxBase.map { it.url })
    }

    @Test
    fun countryScoringPrefersMatchingExplicitCountryAndMexicanFeedByDefault() {
        val channels = listOf(
            channel("ESPN 2 AR", "espn2_ar"),
            channel("ESPN 2 CL", "espn2_cl"),
            channel("ESPN 2 MX", "espn2_mx"),
            channel("TUDN USA", "tudn_usa"),
            channel("TUDN MX", "tudn_mx"),
        )
        // Sin país explícito, prefiere el feed de México sobre Argentina y Chile
        val resEspn2 = SportsLinker.resolveChannels(listOf("ESPN 2"), channels)
        assertEquals("xtvlive:espn2_mx", resEspn2.first().url)

        // Con país explícito USA, prefiere el feed de EE. UU. sobre México
        val resTudnUsa = SportsLinker.resolveChannels(listOf("TUDN USA"), channels)
        assertEquals("xtvlive:tudn_usa", resTudnUsa.first().url)
    }

    @Test
    fun mentionsBothDetectsTeamsInCombinedProgramText() {
        val m = match(home = "Manchester City", away = "Liverpool")
        // En los XMLTV el título suele ser genérico y los equipos van en la descripción
        val fullProgram = "Fútbol Premier League Liverpool visita a Manchester City en la jornada 28"
        assertTrue(SportsLinker.mentionsBoth(fullProgram, m))
    }

    @Test
    fun leagueDisallowedBrandsPreventsWrongSuggestions() {
        val channels = listOf(
            channel("Fox Sports 1 MX", "fox1"),
            channel("Claro Sports 1 HD", "claro1"),
            channel("TUDN MX", "tudn"),
            channel("TNT SPORTS FHD", "tnt1"),
        )
        // Serie A (ita.1) jamás debe sugerir Fox Sports ni Claro Sports
        val serieAChannels = SportsLinker.resolveChannels(listOf("Fox Sports", "Claro Sports"), channels, leagueId = "ita.1")
        assertTrue(serieAChannels.isEmpty())

        // LaLiga (esp.1) jamás debe sugerir Fox Sports ni TUDN
        val laLigaChannels = SportsLinker.resolveChannels(listOf("Fox Sports", "TUDN"), channels, leagueId = "esp.1")
        assertTrue(laLigaChannels.isEmpty())

        // Champions League (uefa.champions) SÍ permite TNT Sports pero NO Fox Sports
        val uclChannels = SportsLinker.resolveChannels(listOf("Fox Sports", "TNT Sports"), channels, leagueId = "uefa.champions")
        assertEquals(listOf("xtvlive:tnt1"), uclChannels.map { it.url })
    }

    @Test
    fun eliteSportsHubResolvesAsEspnAndDisneyPlus() {
        val xtvCatalog = listOf(
            channel("ESPN / Star+ HD", "ST☆R+HD_cyx"),
            channel("ESPN 2 / Star+ 2 HD", "ST☆R+ 2 HD_cyx"),
            channel("ESPN 3 / Star+ 3 HD", "ST☆R+ 3 HD_cyx"),
            channel("ESPN 4 / Star+ 4 HD", "ST☆R+ 4 HD_cyx"),
        )
        assertEquals(
            listOf("xtvlive:ST☆R+HD_cyx"),
            SportsLinker.resolveChannels(listOf("ESPN"), xtvCatalog).map { it.url }
        )
        assertEquals(
            listOf("xtvlive:ST☆R+ 2 HD_cyx"),
            SportsLinker.resolveChannels(listOf("ESPN 2 Mexico"), xtvCatalog).map { it.url }
        )
        assertEquals(
            listOf("xtvlive:ST☆R+HD_cyx"),
            SportsLinker.resolveChannels(listOf("Disney+"), xtvCatalog).map { it.url }
        )
    }

    @Test
    fun internationalLeaguesResolveRealXtvChannels() {
        val xtvCatalog = listOf(
            channel("MOVISTAR FUTBOL HD", "cyx_235320396019967157182116"),
            channel("Liga de campeones", "cyx_Ligadecampeones☆"),
            channel("DAZN 1 HD", "cyx_450564103201407390827"),
            channel("Directv Sports PC", "cyx_DDF490BFAF3D882ACE33C"),
            channel("CANAL GOL HD", "cyx_2EF80C97E365E28ADDFCE85C"),
            channel("TNT SPORTS FHD", "cyx_28998702006640238922"),
            channel("ESPN / Star+ HD", "ST☆R+HD_cyx"),
        )

        val laLigaMatch = match(home = "Real Madrid", away = "Barcelona").copy(
            league = SportLeague("esp.1", "LaLiga", ""),
            channels = listOf("Sky Sports"),
            broadcasts = emptyList(),
        )
        val laLigaQueries = SportsLinker.targetQueryNames(laLigaMatch)
        val laLigaResolved = SportsLinker.resolveChannels(laLigaQueries, xtvCatalog, "esp.1")
        assertTrue(laLigaResolved.any { it.url == "xtvlive:cyx_235320396019967157182116" })
        assertTrue(laLigaResolved.any { it.url == "xtvlive:cyx_450564103201407390827" })

        val uclMatch = match(home = "Bayern Munich", away = "Arsenal").copy(
            league = SportLeague("uefa.champions", "UEFA Champions League", ""),
            channels = listOf("TNT Sports"),
            broadcasts = emptyList(),
        )
        val uclQueries = SportsLinker.targetQueryNames(uclMatch)
        val uclResolved = SportsLinker.resolveChannels(uclQueries, xtvCatalog, "uefa.champions")
        assertEquals("xtvlive:cyx_28998702006640238922", uclResolved.first().url)
        assertTrue(uclResolved.any { it.url == "xtvlive:cyx_Ligadecampeones☆" })
    }

    @Test
    fun geeSportsFallbackParsesPipeHeadersAndClearKeyDrm() {
        val parsed = GeeSportsFallback.parseStreamUrl(
            "https://ott.streann.com/loadbalancer/services/public/channels-secure/56d02/playlist.m3u8?|origin=https://ott.streann.com&referer=https://ott.streann.com/&user-agent=Mozilla/5.0"
        )!!
        assertEquals(
            "https://ott.streann.com/loadbalancer/services/public/channels-secure/56d02/playlist.m3u8",
            parsed.url
        )
        assertEquals("https://ott.streann.com", parsed.origin)
        assertEquals("https://ott.streann.com/", parsed.referrer)
        assertEquals("Mozilla/5.0", parsed.userAgent)

        val clearKeyJson = GeeSportsFallback.buildClearKeyJson(
            "d31b5dd10f224fb9a4f9bb2fc5fc7278:c7d1fb8aa56fa7bc2f5f2cefd9fa8105"
        )
        assertTrue(clearKeyJson != null && clearKeyJson.contains("\"keys\""))
        assertTrue(clearKeyJson!!.contains("\"type\":\"temporary\""))

        val options = GeeSportsFallback.fallbackFor(listOf("ESPN", "Movistar Futbol"), "esp.1")
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.url.startsWith("http") })
        assertTrue(options.none { it.name.contains("(Alt)", ignoreCase = true) || it.name.contains("Gee", ignoreCase = true) || it.name.contains("Fallback", ignoreCase = true) })
    }

    @Test
    fun tntSeriesAndNonSportsChannelsNeverResolveForSports() {
        val mixedCatalog = listOf(
            channel("TNT SERIES HD", "tnt_series"),
            channel("TNT NOVELAS HD", "tnt_novelas"),
            channel("TNT HD", "tnt_regular"),
            channel("WARNER HD", "warner"),
            channel("STAR CHANNEL HD", "star_channel"),
            channel("HBO HD", "hbo"),
            channel("TNT SPORTS FHD", "tnt_sports_real"),
        )

        // Búsqueda de TNT Sports debe excluir categóricamente TNT Series, Novelas, etc. y dar solo el canal real de deportes
        val resolved = SportsLinker.resolveChannels(listOf("TNT Sports"), mixedCatalog)
        assertEquals(listOf("xtvlive:tnt_sports_real"), resolved.map { it.url })

        // Si el catálogo solo tiene canales de series/novelas de TNT pero NO el deportivo, debe retornar vacío
        val seriesOnlyCatalog = listOf(
            channel("TNT SERIES HD", "tnt_series"),
            channel("TNT NOVELAS HD", "tnt_novelas"),
            channel("TNT HD", "tnt_regular"),
        )
        val resolvedEmpty = SportsLinker.resolveChannels(listOf("TNT Sports"), seriesOnlyCatalog)
        assertTrue("No debe haber fuentes si solo existen canales de series", resolvedEmpty.isEmpty())

        // Verificación directa de elegibilidad
        assertFalse(SportsLinker.isEligibleSportsChannel(channel("TNT SERIES HD", "1")))
        assertFalse(SportsLinker.isEligibleSportsChannel(channel("TNT NOVELAS HD", "2")))
        assertFalse(SportsLinker.isEligibleSportsChannel(channel("STAR CHANNEL HD", "3")))
        assertFalse(SportsLinker.isEligibleSportsChannel(channel("WARNER HD", "4")))
        assertTrue(SportsLinker.isEligibleSportsChannel(channel("TNT SPORTS FHD", "5")))
        assertTrue(SportsLinker.isEligibleSportsChannel(channel("TNT SPORTS CHILE HD", "6")))
        assertTrue(SportsLinker.isEligibleSportsChannel(channel("CANAL 5 HD", "7")))
        assertTrue(SportsLinker.isEligibleSportsChannel(channel("AZTECA 7 HD", "8")))
    }

    @Test
    fun quickResolveReturnsOnlyRealCatalogChannels() {
        SportsLinker.clearCache()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val matchWithoutChannel = match().copy(
            id = "non_existent_match",
            league = SportLeague("other.1", "Liga Sin Canal", ""),
            channels = listOf("Canal Inexistente 999"),
            broadcasts = emptyList(),
        )
        val link = SportsLinker.quickResolve(context, matchWithoutChannel)
        // No debe inventar fuentes ni enlaces rotos si el canal no está en el catálogo real
        assertTrue(link.candidates.isEmpty())
    }
}
