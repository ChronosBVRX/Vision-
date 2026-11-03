package com.chronosbvrx.visionplus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.chronosbvrx.visionplus.data.xuper.X4KApi
import org.junit.Test

/**
 * Regresión de la parrilla de TV en línea: si esto se rompe, los canales vuelven
 * a la bolsa genérica ("Sin categoría") o un país sale mal asignado.
 * Los nombres son reales del portal XTV (`v6/getLiveData`, columna 76182).
 */
class LiveTaxonomyTest {

    @Test
    fun `sports channels land in Deportes`() {
        listOf(
            "TUDN MX HD", "TUDN USA HD", "FOX SPORTS 1 USA HD", "CLARO SPORTS 1 FHD",
            "WIN SPORTS PLUS+ HD", "NBA PASS 01", "NFL RED ZONE HD", "MLB PASS 03",
            "TNT SPORTS CHILE HD", "TRACE SPORTS HD", "DAZN 1 HD", "UFC FIGHT PASS",
            "WWE HD", "GOL PERU FHD", "LIGA 1 MAX PERU", "TD+ CR HD", "CDO BASICO HD",
            "AYM SPORTS MEXICO", "MLS SEASON PASS", "CANAL DEL FUTBOL HD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_SPORTS, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `movie channels land in Peliculas`() {
        listOf(
            "HBO HD", "CINEMA VIP 01 HD", "STARZ EAST FHD", "SONY MOVIES HD",
            "DE PELICULA HD", "SHOWTIME EAST HD", "ALQUILER CINEMA ACCION",
            "CINEMAX ACTION MAX HD", "EPIX 1 HD", "PARAMOUNT CHANNEL HD",
            "GOLDEN PREMIER HD", "AZ CINEMA FHD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_MOVIES, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `series channels and marathons land in Series`() {
        listOf(
            "AXN HD", "WARNER HD", "TNT SERIES HD", "STAR CHANNEL HD",
            "ST☆R CENTRAL FHD", "SONY CENTRAL HD", "FX HD",
            "24/7 BETTER CALL SAUL", "24/7 PEAKY BLINDERS", "24/7 FRIENDS HD",
            "TNT HD", "CALLE 13 HD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_SERIES, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `kids channels and kids marathons land in Infantiles`() {
        listOf(
            "24/7 PATRULLA CANINA", "24/7 GO DIEGO GO", "24/7 PAW PATROL 24/7",
            "DISNEY CHANNEL COL HD", "CARTOON NETWORK HD", "NICK MX HD",
            "EL CHAVO DEL 8 HD", "LOS PICAPIEDRAS 24/7 HD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_KIDS, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `music channels land in Musica`() {
        listOf(
            "MTV 80s", "BANDAMAX HD", "NICK MUSIC FHD", "TELEHIT HD", "HTV HD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_MUSIC, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `news channels land in Noticias`() {
        listOf(
            "CNN CHILE HD", "FORO TV HD", "MILENIO TV HD", "NTN 24 HD", "ADN40",
            "ABC NEWS", "TODO NOTICIAS HD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_NEWS, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `documentary channels land in Documentales`() {
        listOf(
            "DISCOVERY CHANNEL ESPAÑA HD", "NAT GEO MUNDO HD", "ANIMAL PLANET HD",
            "HISTORY CHANNELS US HD", "CRIMEN INVESTIGACION HD",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_DOCS, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `novela channels and novela marathons land in Novelas`() {
        listOf(
            "TL NOVELAS HD", "RCN NOVELAS HD", "TNT NOVELAS HD",
            "24/7 LA REINA DEL FLOW T1", "24/7 MUJER CASOS DE LA VIDA REAL",
        ).forEach { assertEquals("$it", LiveTaxonomy.GENRE_NOVELAS, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `general channels fall back to Entretenimiento`() {
        listOf(
            "CANAL 5 HD", "AZTECA 7 HD", "COMEDY CENTRAL HD", "E! HD",
            "CASO CERRADO INMIGRACION HD 24/7", "SHARK TANK COL",
        ).forEach {
            assertEquals("$it", LiveTaxonomy.GENRE_ENTERTAINMENT, LiveTaxonomy.genreFor(it))
        }
    }

    @Test
    fun `religious channels land in Religion`() {
        listOf("ENLACE HD", "EWTN HD", "ALIENTO VISION HD", "NUEVO TIEMPO")
            .forEach { assertEquals("$it", LiveTaxonomy.GENRE_RELIGION, LiveTaxonomy.genreFor(it)) }
    }

    @Test
    fun `mexico is detected over other brands`() {
        listOf(
            "TUDN MX HD", "AZTECA 7 HD", "CANAL 5 HD", "LAS ESTRELLAS MEX HD",
            "BANDAMAX HD", "AZTECA INTERNACIONAL", "GOLDEN PREMIER HD",
        ).forEach { assertEquals("$it", "México", LiveTaxonomy.countryFor(it)) }
    }

    @Test
    fun `us feeds are detected`() {
        listOf(
            "TUDN USA HD", "UNIVISION EAST FHD", "GALAVISION WEST",
            "TELEMUNDO INTERNACIONAL HD", "AMERICA TEVE HD", "BET EAST",
        ).forEach { assertEquals("$it", "EE. UU.", LiveTaxonomy.countryFor(it)) }
    }

    @Test
    fun `spain is detected`() {
        listOf(
            "ANTENA 3 HD", "CUATRO HD", "MOVISTAR SERIES HD", "FOX ESP HD",
            "MEGA ESP HD", "TVE INTERNACIONAL HD", "CLAN TVE HD",
        ).forEach { assertEquals("$it", "España", LiveTaxonomy.countryFor(it)) }
    }

    @Test
    fun `south america is detected`() {
        mapOf(
            "CARACOL COL FHD" to "Colombia",
            "WIN SPORTS 1" to "Colombia",
            "AMERICA TV PERU HD" to "Perú",
            "LATINA HD" to "Perú",
            "MEGA HD" to "Chile",
            "CANAL 13 CHILE HD" to "Chile",
            "TVN HD" to "Chile",
            "CHV HD" to "Chile",
            "ECUAVISA HD" to "Ecuador",
            "RTS HD" to "Ecuador",
            "ATB" to "Bolivia",
            "RED UNO HD" to "Bolivia",
            "SNT HD PY" to "Paraguay",
            "TELEDOCE HD" to "Uruguay",
            "VTV HD" to "Venezuela",
            "VTV URUGUAY HD" to "Uruguay",
            "VENEVISION HD" to "Venezuela",
        ).forEach { (name, country) -> assertEquals(name, country, LiveTaxonomy.countryFor(name)) }
    }

    @Test
    fun `central america and caribbean are detected`() {
        mapOf(
            "TELETICA 7 HD" to "Costa Rica",
            "FUTV CR HD" to "Costa Rica",
            "TELEMETRO PAN HD" to "Panamá",
            "TVN 2 PAN 2 HD" to "Panamá",
            "TELEVICENTRO HD" to "Honduras",
            "HCH HN HD" to "Honduras",
            "CANAL 2 SV HD" to "El Salvador",
            "TN 8 NIC HD" to "Nicaragua",
            "TELEVISIETE GUA HD" to "Guatemala",
            "AZTECA GUATE HD" to "Guatemala",
            "AZTECA HONDURAS HD" to "Honduras",
            "TELEMICRO 5 RD HD" to "R. Dominicana",
            "ANTENA LATINA HD" to "R. Dominicana",
            "WAPA PR HD" to "Puerto Rico",
            "BAND FHD" to "Brasil",
            "CANCAO NOVA" to "Brasil",
            "SPORTSNET ONE CA" to "Canadá",
        ).forEach { (name, country) -> assertEquals(name, country, LiveTaxonomy.countryFor(name)) }
    }

    @Test
    fun `tricky names do not misfire`() {
        // "RTS" vive dentro de "SPORTS": el canal deportivo no es Ecuador.
        assertEquals("EE. UU.", LiveTaxonomy.countryFor("FOX SPORTS 1 USA HD"))
        assertEquals(LiveTaxonomy.GENRE_SPORTS, LiveTaxonomy.genreFor("FOX SPORTS 1 USA HD"))
        assertEquals("Internacional", LiveTaxonomy.countryFor("TNT SPORTS HD"))
        // "PAT" vive dentro de "PATRULLA": la patrulla no es Bolivia.
        assertEquals("Internacional", LiveTaxonomy.countryFor("24/7 PATRULLA CANINA"))
        // "NOVA" vive dentro de "CANCAO NOVA": no es España.
        assertEquals("Brasil", LiveTaxonomy.countryFor("CANCAO NOVA"))
        // "BAND" vive dentro de "BANDAMAX": no es Brasil.
        assertEquals("México", LiveTaxonomy.countryFor("BANDAMAX HD"))
        // "LATINA" vive dentro de "ANTENA LATINA": no es Perú.
        assertEquals("R. Dominicana", LiveTaxonomy.countryFor("ANTENA LATINA HD"))
        // "ESP" no caza "ESPAÑOL".
        assertEquals("EE. UU.", LiveTaxonomy.countryFor("CNN ESPAÑOL"))
        assertEquals("Internacional", LiveTaxonomy.countryFor("DW EN ESPAÑOL HD"))
        // "PE" no caza "PELÍCULA".
        assertEquals("Internacional", LiveTaxonomy.countryFor("CINE LATINO HD"))
        // "BO" no caza "BOB".
        assertEquals("Internacional", LiveTaxonomy.countryFor("BOB ESPONJA"))
    }

    @Test
    fun `explicit country beats brand`() {
        // TNT Sports Chile es Chile aunque TNT sea marca regional.
        assertEquals("Chile", LiveTaxonomy.countryFor("TNT SPORTS CHILE HD"))
        // Movistar Deportes Perú es Perú aunque Movistar sea España.
        assertEquals("Perú", LiveTaxonomy.countryFor("MOVISTAR DEPORTES PERU HD"))
        // Disney Colombia es Colombia.
        assertEquals("Colombia", LiveTaxonomy.countryFor("DISNEY CHANNEL COL HD"))
        // Cartoon México es México.
        assertEquals("México", LiveTaxonomy.countryFor("CARTOON NETWORK MX HD"))
    }

    @Test
    fun `m3u groups are respected but generic ones are classified`() {
        assertEquals("Noticias", LiveTaxonomy.groupOf("Canal Dos", "Noticias"))
        assertEquals(LiveTaxonomy.GENRE_NEWS, LiveTaxonomy.groupOf("FORO TV HD", ""))
        assertEquals(LiveTaxonomy.GENRE_SPORTS, LiveTaxonomy.groupOf("TUDN MX HD", "Sin categoría"))
    }

    @Test
    fun `genres come out in rail order and intl goes last`() {
        val genres = LiveTaxonomy.genresOf(listOf("FORO TV HD", "TUDN MX HD", "HBO HD"))
        assertEquals(
            listOf(
                LiveTaxonomy.GENRE_SPORTS, LiveTaxonomy.GENRE_MOVIES,
                LiveTaxonomy.GENRE_NEWS,
            ),
            genres,
        )
        val countries = LiveTaxonomy.countriesOf(
            listOf("TUDN MX HD", "AZTECA 7 HD", "CANAL 5 HD", "CNN CHILE HD"),
        )
        assertEquals("México", countries.first())
        assertEquals("Chile", countries.last())
        assertTrue(countries.contains("México"))
        assertTrue(countries.contains("Chile"))
    }

    @Test
    fun `popular channels come first without prefix collisions`() {
        val names = listOf(
            "CANAL 50 HD", "HBO HD", "TUDN MX HD", "CANAL 5 HD",
            "LAS ESTRELLAS HD", "AZTECA 7 HD",
        )
        assertEquals(
            listOf(
                "CANAL 5 HD", "LAS ESTRELLAS HD", "AZTECA 7 HD",
                "TUDN MX HD", "HBO HD", "CANAL 50 HD",
            ),
            names.sortedWith(LiveTaxonomy.POPULARITY_ORDER),
        )
        // «CANAL 5» no debe arrastrar a «CANAL 50».
        assertTrue(
            LiveTaxonomy.popularityRank("CANAL 5 HD") <
                LiveTaxonomy.popularityRank("CANAL 50 HD"),
        )
    }

    @Test
    fun `fox feeds are separated by country and owner`() {
        mapOf(
            "Fox Sports Central HD" to "México",
            "Fox Sports 2 Central HD" to "México",
            "Fox Sports 3 Central HD" to "México",
            "Fox Sports Premium MX HD" to "México",
            "Fox Sports 1 MX" to "México",
            "Fox Deportes HD" to "EE. UU.",
            "FOX SOCCER PLUS HD" to "EE. UU.",
            "FOX SPORTS 1 USA HD" to "EE. UU.",
            "FOX SPORTS AR HD" to "Argentina",
            "FOX SPORTS CHILE HD" to "Chile",
            "FOX ESP HD" to "España",
            "FOX ONE" to "México",
            "AYM SPORTS MEXICO" to "México",
        ).forEach { (name, country) -> assertEquals(name, country, LiveTaxonomy.countryFor(name)) }

        mapOf(
            "Fox Sports Central HD" to "Fox Sports (Lauman)",
            "Fox Sports 2 Central HD" to "Fox Sports (Lauman)",
            "Fox Sports Premium MX HD" to "Fox Sports (Lauman)",
            "Fox Sports 1 MX" to "Fox Sports (Lauman)",
            "Fox Deportes HD" to "Fox (Disney)",
            "FOX SOCCER PLUS HD" to "Fox (Disney)",
            "FOX SPORTS 1 USA HD" to "Fox Sports (Disney)",
            "FOX SPORTS AR HD" to "Fox Sports (Disney)",
            "FOX ONE" to "Fox Corporation",
            "AYM SPORTS MEXICO" to "AYM (FOX básico)",
            "FOX ESP HD" to "Fox (España)",
            "ESPN HD" to "ESPN (Disney)",
        ).forEach { (name, owner) -> assertEquals(name, owner, LiveTaxonomy.providerFor(name)) }
    }

    @Test
    fun `fox one is sports from fox corporation and aym is basic fox`() {
        assertEquals("Deportes", LiveTaxonomy.genreFor("FOX ONE"))
        assertEquals("México", LiveTaxonomy.countryFor("FOX ONE"))
        assertEquals("Fox Corporation", LiveTaxonomy.providerFor("FOX ONE"))
        assertTrue(LiveTaxonomy.noteFor("FOX ONE").contains("FOX+"))
        assertTrue(LiveTaxonomy.noteFor("AYM SPORTS MEXICO").contains("FOX básico"))
        assertTrue(LiveTaxonomy.noteFor("Fox Sports Premium MX HD").contains("no es FOX+"))
        assertEquals("Fox Sports (Lauman)", LiveTaxonomy.providerFor("Fox Sports Premium MX HD"))
        assertEquals("Series", LiveTaxonomy.genreFor("Sony Central HD"))
    }

    @Test
    fun `tnt entertainment and tnt sports chile are kept apart`() {
        assertEquals("Series", LiveTaxonomy.genreFor("TNT SPORTS HD"))
        assertTrue(LiveTaxonomy.noteFor("TNT SPORTS HD").contains("TNT"))
        assertEquals("Chile", LiveTaxonomy.countryFor("TNT SPORTS FHD"))
        assertEquals("Chile", LiveTaxonomy.countryFor("TNT SPORTS PREMIUM HD"))
        assertEquals("Chile", LiveTaxonomy.countryFor("ESTADIO TNT SPORTS"))
        assertTrue(LiveTaxonomy.noteFor("TNT SPORTS FHD").contains("Chile"))
        // El «+» distingue canales; HD/FHD siguen siendo la misma señal.
        assertNotEquals(
            LiveTaxonomy.channelKey("WIN Sports+ COL HD"),
            LiveTaxonomy.channelKey("WIN Sports COL HD"),
        )
        assertNotEquals(
            LiveTaxonomy.channelKey("TNT SPORTS HD+"),
            LiveTaxonomy.channelKey("TNT SPORTS HD"),
        )
        assertEquals(
            LiveTaxonomy.channelKey("A&E HD"),
            LiveTaxonomy.channelKey("A&E FHD"),
        )
    }

    @Test
    fun `masked names are shown with their real identity`() {
        assertEquals("TNT HD", X4KApi.displayChannelName("cyx_TNTSports3", "TNT SPORTS HD"))
        assertEquals("BitMe HD", X4KApi.displayChannelName("cyx_BitMe☆", "ANIMAL PLANET HD+"))
        assertEquals("Star Channel HD", X4KApi.displayChannelName("cyx-FoxHD", "ST☆R HD"))
        assertEquals(
            "Disney+ (Elite Sports Hub)",
            X4KApi.displayChannelName("ST☆R+HD_cyx", "Elite Sports Hub HD"),
        )
    }

    @Test
    fun `elite sports hub is disney plus not live espn`() {
        assertEquals("Series", LiveTaxonomy.genreFor("Elite Sports Hub HD"))
        assertTrue(LiveTaxonomy.noteFor("Elite Sports Hub HD").contains("Disney+"))
        assertEquals(
            "Disney+ (Elite Sports Hub)",
            X4KApi.displayChannelName("ST☆R+HD_cyx", "Elite Sports Hub HD"),
        )
    }

    @Test
    fun `genre never comes back blank`() {
        listOf("", "   ", "HD", "123").forEach {
            assertTrue(LiveTaxonomy.genreFor(it).isNotBlank())
            assertTrue(LiveTaxonomy.countryFor(it).isNotBlank())
        }
    }
}
