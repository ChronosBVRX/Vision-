package com.chronosbvrx.visionplus.data

import java.text.Normalizer

/**
 * La taxonomía real de la TV en línea: el portal XTV (`v6/getLiveData`, columna 76182)
 * entrega ~1000 canales SIN agrupar (sin `group-title` ni categorías; ver
 * `x4k_live_76182.json`). Sin esto, "En vivo" era una sola bolsa genérica.
 *
 * Todo aquí es lógica pura (sin Context) para que los unit tests la cubran:
 * [genreFor] y [countryFor] trabajan sobre el nombre del canal tal cual lo manda
 * el portal. Regla de oro: ningún canal XTV puede quedarse en "Sin categoría".
 */
internal object LiveTaxonomy {
    const val GENRE_SPORTS = "Deportes"
    const val GENRE_MOVIES = "Películas"
    const val GENRE_SERIES = "Series"
    const val GENRE_KIDS = "Infantiles"
    const val GENRE_NEWS = "Noticias"
    const val GENRE_DOCS = "Documentales"
    const val GENRE_MUSIC = "Música"
    const val GENRE_NOVELAS = "Novelas"
    const val GENRE_ENTERTAINMENT = "Entretenimiento"
    const val GENRE_RELIGION = "Religión"

    const val COUNTRY_INTL = "Internacional"

    /** El riel de géneros siempre sale en este orden (como una parrilla de TV). */
    val GENRE_ORDER = listOf(
        GENRE_SPORTS, GENRE_MOVIES, GENRE_SERIES, GENRE_KIDS, GENRE_NEWS,
        GENRE_DOCS, GENRE_MUSIC, GENRE_NOVELAS, GENRE_ENTERTAINMENT, GENRE_RELIGION,
    )

    /** "Sin categoría" (M3U) o vacío (XTV): lo genérico que esta taxonomía reemplaza. */
    fun isGenericGroup(group: String): Boolean {
        val t = group.trim()
        return t.isEmpty() || t.equals("Sin categoría", ignoreCase = true)
    }

    private val REGEX_ACCENTS = Regex("\\p{Mn}+")
    private val REGEX_NON_ALPHANUM = Regex("[^A-Z0-9]+")
    private val REGEX_SPACES = Regex(" +")

    private val normCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val genreCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val countryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** MAYÚSCULAS sin acentos y con lo no alfanumérico como espacio ("24/7" -> "24 7"). */
    internal fun norm(name: String): String = normCache.computeIfAbsent(name) { raw ->
        val noAccents = Normalizer.normalize(raw.uppercase(), Normalizer.Form.NFD)
            .replace(REGEX_ACCENTS, "")
        noAccents.replace(REGEX_NON_ALPHANUM, " ").trim().replace(REGEX_SPACES, " ")
    }

    /** El género del canal por su nombre (nunca vacío). */
    fun genreFor(name: String): String = genreCache.computeIfAbsent(name) { raw ->
        val n = norm(raw)
        if (n.isBlank()) return@computeIfAbsent GENRE_ENTERTAINMENT
        // Religión primero: "VISIÓN" también aparece en canales deportivos laicos.
        if (containsAny(n, RELIGION_ANY)) return@computeIfAbsent GENRE_RELIGION
        // Deportes antes que música ("TRACE SPORTS") y que series ("FOX SPORTS" vs "FOX").
        if (containsAny(n, SPORTS_ANY) || hasToken(n, SPORTS_TOKENS)) return@computeIfAbsent GENRE_SPORTS
        // Cine antes que series ("SONY MOVIES" es cine; "STARZ" contiene "STAR").
        if (containsAny(n, MOVIES_ANY)) return@computeIfAbsent GENRE_MOVIES
        // "24/7": maratón de una serie/película infantil o juvenil.
        if (n.contains("24 7")) {
            if (containsAny(n, KIDS_ANY)) return@computeIfAbsent GENRE_KIDS
            if (containsAny(n, NOVELAS_247_ANY)) return@computeIfAbsent GENRE_NOVELAS
            if (containsAny(n, ENTERTAINMENT_247_ANY)) return@computeIfAbsent GENRE_ENTERTAINMENT
            return@computeIfAbsent GENRE_SERIES
        }
        // Música antes que infantiles ("NICK MUSIC" es música).
        if (containsAny(n, MUSIC_ANY)) return@computeIfAbsent GENRE_MUSIC
        if (containsAny(n, KIDS_ANY)) return@computeIfAbsent GENRE_KIDS
        if (containsAny(n, NEWS_ANY)) return@computeIfAbsent GENRE_NEWS
        if (containsAny(n, DOCS_ANY)) return@computeIfAbsent GENRE_DOCS
        if (containsAny(n, NOVELAS_ANY)) return@computeIfAbsent GENRE_NOVELAS
        if (containsAny(n, SERIES_ANY) || raw.contains("☆")) return@computeIfAbsent GENRE_SERIES
        if (containsAny(n, ENTERTAINMENT_ANY) || raw.contains("E!")) return@computeIfAbsent GENRE_ENTERTAINMENT
        GENRE_ENTERTAINMENT
    }

    /** El país del canal por su nombre ("Internacional" cuando no se sabe). */
    fun countryFor(name: String): String = countryCache.computeIfAbsent(name) { raw ->
        val n = norm(raw)
        if (n.isBlank()) return@computeIfAbsent COUNTRY_INTL
        val p = " $n "
        // 1) País explícito en varias palabras ("COSTA RICA", "PUERTO RICO"...).
        for ((needle, country) in COUNTRY_PHRASES) {
            if (n.contains(needle)) return@computeIfAbsent country
        }
        // 2) Códigos como palabra completa (" MX ", " RD "...; con espacios para no
        //    confundir "BO" con "BOB" ni "PE" con "PELÍCULA").
        for ((token, country) in COUNTRY_TOKENS) {
            if (p.contains(" $token ")) return@computeIfAbsent country
        }
        // 3) Marcas nacionales conocidas (después de lo explícito: "AZTECA GUATE" ya
        //    salió como Guatemala y no cae aquí). Las agujas cortas (<=3) se buscan
        //    como palabra completa: "PAT" no debe cazar "PATRULLA" ni "RTS" a "SPORTS".
        for ((needle, country) in COUNTRY_BRANDS) {
            if (needle.length <= 3) {
                if (p.contains(" $needle ")) return@computeIfAbsent country
            } else if (n.contains(needle)) {
                return@computeIfAbsent country
            }
        }
        COUNTRY_INTL
    }

    /** El grupo a mostrar: respeta el `group-title` del M3U y clasifica lo genérico. */
    fun groupOf(name: String, group: String): String =
        if (isGenericGroup(group)) genreFor(name) else group.trim()

    /** Los géneros presentes, en el orden fijo del riel. */
    fun genresOf(names: List<String>): List<String> {
        val present = names.map { genreFor(it) }.toSet()
        return GENRE_ORDER.filter { it in present }
    }

    /** Los países presentes: primero los de más canales; "Internacional" al final. */
    fun countriesOf(names: List<String>): List<String> {
        val counts = names.groupingBy { countryFor(it) }.eachCount()
        return counts.keys.sortedWith(
            compareByDescending<String> { counts[it] ?: 0 }
                .thenBy { if (it == COUNTRY_INTL) 1 else 0 }
                .thenBy { it },
        )
    }

    /** Código ISO del país para chips uniformes («MX», «AR»…): sin banderitas emoji, que en TV baratas no renderizan. */
    fun countryCode(country: String): String = when (country) {
        "México" -> "MX"
        "Argentina" -> "AR"
        "EE. UU." -> "US"
        "España" -> "ES"
        "Colombia" -> "CO"
        "Chile" -> "CL"
        "Perú" -> "PE"
        "Ecuador" -> "EC"
        "Bolivia" -> "BO"
        "Paraguay" -> "PY"
        "Uruguay" -> "UY"
        "Venezuela" -> "VE"
        "Costa Rica" -> "CR"
        "Panamá" -> "PA"
        "Honduras" -> "HN"
        "El Salvador" -> "SV"
        "Nicaragua" -> "NI"
        "Guatemala" -> "GT"
        "R. Dominicana" -> "DO"
        "Puerto Rico" -> "PR"
        "Brasil" -> "BR"
        "Canadá" -> "CA"
        else -> "INT"
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it) }

    private fun hasToken(normed: String, tokens: Set<String>): Boolean {
        val padded = " $normed "
        return tokens.any { padded.contains(" $it ") }
    }

    // ------------------------------------------------------------------ géneros

    private val RELIGION_ANY = listOf(
        "ENLACE", "EWTN", "ALIENTO", "HOSSANA", "HOSANNA", "EBENEZER", "NUEVO TIEMPO",
        "3ABN", "ADVENIR", "VOZ DE MARIA", "CRISTIAN", "BIBLIA",
    )

    private val SPORTS_ANY = listOf(
        "SPORT", "DEPORTE", "FUTBOL", "FUTV", "BEISBOL", "LIGA PRO", "LIGA 1", "LIGA MX",
        "LIGA BOLIVIANA", "LIGA DE CAMPEONES", "NBA", "NFL", "MLB", "MLS", "NHL",
        "UFC", "WWE", "BOX NATION", "TENNIS", "GOLF", "RACING", "FORMULA", "F1 HD",
        "DAZN", "TUDN", "TDN", "GOL PERU", "GOL TV", "CANAL DEL FUTBOL", "CANAL GOL",
        "EL CANAL DEL FUTBOL", "TVC DEPORTES", "DEPORTES TVC", "AYM SPORTS",
        "PX SPORTS", "CDO", "FIGHT", "COMBATE", "TOROS TV", "MUNDO TORO", "HIPODROMO",
        "TVG NETWORK", "TVG 2", "OVACION", "ESTADIO TNT", "CLARO SPORTS",
        "MOVISTAR DEPORTES", "FOX DEPORTE", "FOX SPORT", "TNT SPORT", "WIN SPORT",
        "BALLY", "NESN", "YES NETWORK", "SPORTSNET", "SPORTSMAX", "DIRECTV SPORT",
        "ESPN", "FOX RACING", "FOX SOCCER", "TD+", "TD", "PREMIER LEAGUE", "CHAMPIONS",
    )
    private val SPORTS_TOKENS = setOf("FUT", "MMA", "OLN")

    private val MOVIES_ANY = listOf(
        "CINEMA", "CINE", "PELICULA", "MOVIE", "FILM", "HBO", "CINEMAX",
        "STARZ", "EPIX", "SHOWTIME", "PARAMOUNT", "SUNDANCE", "TCM", "AMC BO",
        "GOLDEN", "MULTICINE", "PLATINO", "HOLLYWOOD", "HALLMARK MOVIE", "MYTIME MOVIE",
        "MOVIEPLEX", "PANICO", "MORBIDO", "ALQUILER", "DE PELICULA", "CLARO CINEMA",
        "AZ CINEMA", "A3 CINEMA", "CINEVISION", "CINE LATINO", "CINE POP", "CINECANAL",
        "DHE", "FXM", "SONY MOVIES", "ULTRA FILM", "CINEMA VIP", "CINEMA DINAMITA",
        "EUROCHANNEL", "EUROPA EUROPA",
    )

    private val KIDS_ANY = listOf(
        "DISNEY", "NICK", "CARTOON", "TOONCAST", "TOON", "BOING", "CLAN", "BABY",
        "PAW PATROL", "PATRULLA CANINA", "CHU CHU", "BLIPPI", "ZOOMOO", "PAKAPAKA",
        "DREAMWORKS", "DRAGON BALL", "POKEMON", "BOB ESPONJA", "SCOOBY", "LOONEY",
        "RUGRATS", "DORA", "DIEGO", "MICKEY", "MASHA", "MIRACULOUS", "PEPPA", "PINGUINO",
        "PINGUINOS", "GASPARIN", "DON GATO", "PICAPIEDRA", "CHAVO", "CHAPULIN",
        "CHESPIRITO", "CANTINFLAS", "PANTERA ROSA", "PAJARO LOCO",
    )

    private val MUSIC_ANY = listOf(
        "MTV", "MUSIC", "MUSICA", "BANDAMAX", "TELEHIT", "TELERITMO", "HTV", "VIDEOROLA",
        "MUSIC TOP", "CONCERT CHANNEL", "RITMO",
    )

    private val NEWS_ANY = listOf(
        "NEWS", "NOTICIA", "CNN", "FORO TV", "FORO", "MILENIO", "EXCELSIOR",
        "NTN 24", "NTN24", "DW EN", "AL JAZEERA", "JAZEERA", "BLOOMBERG", "CNBC",
        "MSNBC", "EURONEWS", "EURO NEWS", "24 HORAS", "TODO NOTICIAS", "CABLE NOTICIAS",
        "LATINA NOTICIAS", "RPP", "HISPAN TV", "TELESUR", "RT EN ESPANOL",
        "ABC NEWS", "CBS NEWS", "FOX NEWS", "ADN40",
    )

    private val DOCS_ANY = listOf(
        "DISCOVERY", "NAT GEO", "GEOGRAPHIC", "HISTORY", "ANIMAL PLANET", "LOVE NATURE",
        "ODISEA", "CGTN DOCU", "SCIENCE", "CIVILIZATION", "CRIMEN INVESTIGACION",
        "CAZA", "PESCA", "TURBO", "THEATER HD", "WILD HD", "DOCUMENTAL",
    )

    private val NOVELAS_ANY = listOf(
        "NOVELA", "TLNOVELA", "PASION", "TL ESTRELLA",
    )
    private val NOVELAS_247_ANY = listOf(
        "REINA DEL FLOW", "MUJER CASOS", "CASOS DE LA VIDA", "AMOR A SEGUNDA",
        "AL DIABLO LOS GUAPOS",
    )
    private val ENTERTAINMENT_247_ANY = listOf(
        "CASO CERRADO", "SHARK TANK", "MASTER CHEF", "CASA DE LOS FAMOSOS",
        "ACAPULCO SHORE", "FAMILIA DE DIEZ", "SE RENTA CUARTOS",
    )

    private val SERIES_ANY = listOf(
        "AXN", "AMC", "WARNER", "FX", "TNT", "CRIME", "SERIE", "STAR CHANNEL",
        "STAR PLUS", "I SAT", "USA NETWORK", "CALLE 13", "A3S",
        "UNIVERSAL CHANNEL", "UNIVERSAL PREMIER", "SONY CENTRAL", "SONY HD",
        "FOX HD", "FOX ONE", "FOX ESP",
    )

    private val ENTERTAINMENT_ANY = listOf(
        "COMEDY", "COMEDIA", "REALITY", "GAME SHOW", "FAMOSOS", "SHARK TANK",
        "LIFETIME", "GLITZ", "HOLA TV", "VARIEDADES", "MAGAZINE", "TALK SHOW",
    )

    // ------------------------------------------------------------------- países

    private val COUNTRY_PHRASES = listOf(
        "COSTA RICA" to "Costa Rica",
        "PUERTO RICO" to "Puerto Rico",
        "EL SALVADOR" to "El Salvador",
        "REPUBLICA DOMINICANA" to "R. Dominicana",
        "REP DOMINICANA" to "R. Dominicana",
        "SALT LAKE" to "EE. UU.",
        "SAN DIEGO" to "EE. UU.",
        "KANSAS CITY" to "EE. UU.",
    )

    private val COUNTRY_TOKENS = listOf(
        "MEXICO" to "México", "MEX" to "México", "MX" to "México",
        "USA" to "EE. UU.", "US" to "EE. UU.", "EAST" to "EE. UU.", "WEST" to "EE. UU.",
        "ESTE" to "EE. UU.", "OESTE" to "EE. UU.", "MIAMI" to "EE. UU.",
        "YORK" to "EE. UU.", "CHICAGO" to "EE. UU.", "DALLAS" to "EE. UU.",
        "BOSTON" to "EE. UU.", "ARIZONA" to "EE. UU.", "OHIO" to "EE. UU.",
        "KANSAS" to "EE. UU.", "WISCONSIN" to "EE. UU.", "DETROIT" to "EE. UU.",
        "CALIFORNIA" to "EE. UU.", "WASHINGTON" to "EE. UU.", "PHILLY" to "EE. UU.",
        "DIEGO" to "EE. UU.",
        "ESPANA" to "España", "ESP" to "España",
        "ARGENTINA" to "Argentina", "ARG" to "Argentina", "AR" to "Argentina",
        "COLOMBIA" to "Colombia", "COL" to "Colombia", "CO" to "Colombia",
        "PERU" to "Perú", "PE" to "Perú",
        "CHILE" to "Chile", "CL" to "Chile",
        "ECUADOR" to "Ecuador", "EC" to "Ecuador",
        "BOLIVIA" to "Bolivia", "BOLIVIANA" to "Bolivia", "BO" to "Bolivia",
        "PARAGUAY" to "Paraguay", "PY" to "Paraguay",
        "URUGUAY" to "Uruguay", "UY" to "Uruguay",
        "VENEZUELA" to "Venezuela",
        "CR" to "Costa Rica",
        "PANAMA" to "Panamá", "PAN" to "Panamá",
        "HONDURAS" to "Honduras", "HN" to "Honduras",
        "SALVADOR" to "El Salvador", "SV" to "El Salvador",
        "NICARAGUA" to "Nicaragua", "NIC" to "Nicaragua", "NI" to "Nicaragua",
        "GUATEMALA" to "Guatemala", "GUATE" to "Guatemala", "GUA" to "Guatemala",
        "GT" to "Guatemala",
        "DOMINICANA" to "R. Dominicana", "DOMINICANO" to "R. Dominicana",
        "RD" to "R. Dominicana",
        "PR" to "Puerto Rico",
        "BRASIL" to "Brasil", "BRA" to "Brasil",
        "CANADA" to "Canadá", "CA" to "Canadá",
    )

    private val COUNTRY_BRANDS = listOf(
        // Desempates: la frase larga manda sobre la marca corta.
        "CANCAO NOVA" to "Brasil",
        "ANTENA LATINA" to "R. Dominicana",
        // México
        "AZTECA" to "México", "TELEVISA" to "México", "LAS ESTRELLA" to "México",
        "TL ESTRELLA" to "México", "MULTIMEDIOS" to "México", "MILENIO" to "México",
        "EXCELSIOR" to "México", "IMAGEN" to "México", "UNICABLE" to "México",
        "BANDAMAX" to "México", "DISTRITO COMEDIA" to "México", "GOLDEN" to "México",
        "BITME" to "México", "TLNOVELA" to "México", "TL NOVELA" to "México",
        "DE PELICULA" to "México", "TELEHIT" to "México", "TELERITMO" to "México",
        "A MAS" to "México", "TUDN" to "México", "MEXICANAL" to "México",
        "CANAL 5" to "México", "FORO" to "México", "ADN" to "México",
        // EE. UU. / hispanos USA
        "UNIVISION" to "EE. UU.", "UNIMAS" to "EE. UU.", "TELEMUNDO" to "EE. UU.",
        "GALAVISION" to "EE. UU.", "ESTRELLA TV" to "EE. UU.",
        "AMERICA TEVE" to "EE. UU.", "TEVE HD" to "EE. UU.", "BALLY" to "EE. UU.",
        "NESN" to "EE. UU.", "YES NETWORK" to "EE. UU.", "NBC SPORTS" to "EE. UU.",
        "NBCSN" to "EE. UU.", "CNN" to "EE. UU.", "FOX NEWS" to "EE. UU.",
        "ABC 7" to "EE. UU.", "ABC 10" to "EE. UU.", "CBS 2" to "EE. UU.",
        "NBC GOLF" to "EE. UU.", "NFL NETWORK" to "EE. UU.",
        "MLB NETWORK" to "EE. UU.", "EPIX" to "EE. UU.", "SHOWTIME" to "EE. UU.",
        "HBO" to "EE. UU.", "STARZ" to "EE. UU.",
        // España
        "ANTENA 3" to "España", "CUATRO HD" to "España", "TELECINCO" to "España",
        "LA SEXTA" to "España", "NEOX" to "España", "NOVA" to "España",
        "DIVINITY" to "España", "ENERGY" to "España", "FDF" to "España",
        "BOING" to "España", "CLAN TVE" to "España", "CLAN TV" to "España",
        "LA 1 HD" to "España", "LA 2 HD" to "España", "TVE" to "España",
        "MADRID" to "España", "TELEMADRID" to "España", "MOVISTAR" to "España",
        "CALLE 13" to "España", "BEMAD" to "España", "M VAMOS" to "España",
        "VAMOS" to "España", "ODISEA" to "España", "TOROS TV" to "España",
        "MUNDO TORO" to "España", "DARK ESPANA" to "España", "AXN ESPANA" to "España",
        // Colombia
        "CARACOL" to "Colombia", "RCN" to "Colombia", "WIN SPORT" to "Colombia",
        "CITY TV" to "Colombia", "NTN" to "Colombia", "CABLE NOTICIAS" to "Colombia",
        "SENAL COLOMBIA" to "Colombia", "SENAL" to "Colombia",
        "TELEANTIOQUIA" to "Colombia", "TELECARIBE" to "Colombia",
        "TELECAFE" to "Colombia", "TELEMEDELLIN" to "Colombia",
        "TELEPACIFICO" to "Colombia", "CANAL TRO" to "Colombia",
        "RED MAS" to "Colombia",
        // Perú
        "LATINA" to "Perú", "PANAMERICANA" to "Perú", "WILLAX" to "Perú",
        "EXITOSA" to "Perú", "RPP" to "Perú", "USMP" to "Perú",
        "PERU MAGICO" to "Perú", "TV PERU" to "Perú", "AMERICA TV PERU" to "Perú",
        "FRECUENCIA LATINA" to "Perú",
        // Chile
        "TVN" to "Chile", "MEGA" to "Chile", "CHV" to "Chile",
        "LA RED" to "Chile", "CHILEVISION" to "Chile", "REC TV" to "Chile",
        "ETC TV" to "Chile", "CDO" to "Chile", "TELETRAK" to "Chile",
        // Ecuador
        "ECUAVISA" to "Ecuador", "TELEAMAZONAS" to "Ecuador",
        "TC TELEVISION" to "Ecuador", "RTS" to "Ecuador", "GAMAVISION" to "Ecuador",
        "OROMAR" to "Ecuador", "ECDF" to "Ecuador", "GAMA TV" to "Ecuador",
        "ECUADOR TV" to "Ecuador", "MANAVISION" to "Ecuador", "TELERAMA" to "Ecuador",
        // Bolivia
        "ATB" to "Bolivia", "BOLIVISION" to "Bolivia", "BOLIVIA TV" to "Bolivia",
        "RED UNO" to "Bolivia", "UNITEL" to "Bolivia", "GIGAVISION" to "Bolivia",
        "RTP" to "Bolivia", "PAT" to "Bolivia", "CADENA A" to "Bolivia",
        "ABYA YALA" to "Bolivia", "ABYA" to "Bolivia",
        // Paraguay
        "TELEFUTURO" to "Paraguay", "SNT" to "Paraguay", "TRECE" to "Paraguay",
        "C9N" to "Paraguay", "PARAGUAY TV" to "Paraguay",
        "NOTICIAS PARAGUAY" to "Paraguay", "UNICANAL" to "Paraguay",
        // Uruguay
        "TELEDOCE" to "Uruguay", "SAETA" to "Uruguay", "VTV URUGUAY" to "Uruguay",
        // Venezuela (VTV pelado es Venezuela; el de Uruguay siempre dice URUGUAY)
        "VENEVISION" to "Venezuela", "TELEVEN" to "Venezuela",
        "GLOBOVISION" to "Venezuela", "VTV" to "Venezuela", "MERIDIANO" to "Venezuela",
        "IVC" to "Venezuela", "PROMAR" to "Venezuela", "VALE TV" to "Venezuela",
        "CANAL I" to "Venezuela", "TELESUR" to "Venezuela",
        // Costa Rica
        "TELETICA" to "Costa Rica", "REPRETEL" to "Costa Rica",
        // Panamá
        "TELEMETRO" to "Panamá", "SERTV" to "Panamá", "NEX TV" to "Panamá",
        "HOSSANA" to "Panamá", "HOSANNA" to "Panamá", "REMON" to "Panamá",
        "TV MAX" to "Panamá",
        // Honduras
        "TELEVICENTRO" to "Honduras", "TSI" to "Honduras", "TELECADENA" to "Honduras",
        "HCH" to "Honduras", "TODO DEPORTES" to "Honduras",
        // Guatemala
        "GUATEVISION" to "Guatemala", "TELEVISIETE" to "Guatemala",
        "CANAL ANTIGUA" to "Guatemala", "TN 23" to "Guatemala",
        // R. Dominicana
        "TELEMICRO" to "R. Dominicana", "TELESISTEMA" to "R. Dominicana",
        "LUNA TV" to "R. Dominicana", "TROPICAL" to "R. Dominicana",
        // Puerto Rico
        "WAPA" to "Puerto Rico", "TELEONCE" to "Puerto Rico",
        // Brasil
        "BAND" to "Brasil",
        // Canadá
        "SPORTSNET" to "Canadá", "SHOWCASE" to "Canadá",
    )
}
