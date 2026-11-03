package com.chronosbvrx.visionplus.data.games

/**
 * La carta de juegos de Vision+ Play. Vive en el cliente para que el selector abra
 * al instante y sin red; el servidor publica la misma ficha en `/v1/game/catalog`
 * y es quien decide qué juegos acepta al crear una sala.
 */
/** Sección de la trivia. El `id` viaja como `pack` al servidor. */
data class TriviaPack(val id: String, val title: String, val hint: String)

data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val approxMinutes: Int,
    val rounds: Int,
    val steps: List<String>,
    /** Color del acento en ARGB (0xFFRRGGBB). */
    val accent: Long,
) {
    val playersLabel: String
        get() = if (minPlayers >= maxPlayers) "$minPlayers jugadores" else "$minPlayers–$maxPlayers jugadores"

    val timeLabel: String get() = "~$approxMinutes min"

    val roundsLabel: String get() = "$rounds rondas"
}

object GameCatalog {
    val Trivia = GameInfo(
        id = "trivia",
        title = "Trivia Vision+",
        tagline = "El clásico de Vision+ Play",
        description = "Ocho rondas de supervivencia. La racha desbloquea ataques para congelar, " +
            "censurar o colgarte de un rival; en las rondas de castigo, los que fallan cortan un cable.",
        minPlayers = 1,
        maxPlayers = 8,
        approxMinutes = 12,
        rounds = 8,
        steps = listOf(
            "Responde rápido; dos aciertos seguidos desbloquean un ataque",
            "Rondas 4 y 5: clasifica cartas antes/después",
            "Ronda 8: selecciona todas las respuestas correctas",
        ),
        accent = 0xFF2D7DF6,
    )

    val Charadas = GameInfo(
        id = "charadas",
        title = "Charadas",
        tagline = "Actúa y adivina",
        description = "Por turnos, un jugador ve un título en su teléfono y lo actúa. " +
            "El resto adivina en voz alta. Tienes 60 segundos y tres pases.",
        minPlayers = 2,
        maxPlayers = 8,
        approxMinutes = 10,
        rounds = 6,
        steps = listOf(
            "El teléfono del actor muestra el título",
            "Actúa sin decir el nombre ni partes de él",
            "Marca Correcto o Pasa antes de que acabe el tiempo",
        ),
        accent = 0xFFEF4444,
    )

    val Wager = GameInfo(
        id = "wager",
        title = "Apuesta a la pista",
        tagline = "Tres pistas, una apuesta",
        description = "Póker de pistas: todos pagan una ciega, el póster se revela por etapas y " +
            "puedes comprar más pistas o bloquear tu respuesta. El pozo se reparte por factor x4, x2 o x1.",
        minPlayers = 1,
        maxPlayers = 8,
        approxMinutes = 10,
        rounds = 6,
        steps = listOf(
            "Paga la ciega y mira el póster pixelado",
            "Bloquea tu respuesta o compra la siguiente pista",
            "Acierta antes que los demás y llévate el pozo",
        ),
        accent = 0xFF8B5CF6,
    )

    val Impostor = GameInfo(
        id = "impostor",
        title = "El Impostor",
        tagline = "Uno miente. Todos dudan.",
        description = "Todos reciben el mismo lugar secreto… menos uno. Hagan preguntas rápidas, " +
            "lean las caras y voten quién está fingiendo. Si lo descubren, el impostor tiene " +
            "una última oportunidad de robar la victoria.",
        minPlayers = 3,
        maxPlayers = 8,
        approxMinutes = 10,
        rounds = 3,
        steps = listOf(
            "Tu teléfono guarda el lugar o tu papel de impostor",
            "Preguntas rápidas: todos hablan, nadie dice el lugar",
            "Voten al mentiroso; si lo atrapan, puede adivinar el lugar",
        ),
        accent = 0xFFF43F5E,
    )

    val Dibujo = GameInfo(
        id = "dibujo",
        title = "Duelo de Dibujo Ciego",
        tagline = "Todos mienten, uno dibuja",
        description = "Un jugador recibe una frase absurda y la dibuja en su teléfono. La tele " +
            "muestra el dibujo sin título. Los demás inventan títulos falsos y votan por el " +
            "verdadero. Cada trampa que muerda suma.",
        minPlayers = 3,
        maxPlayers = 8,
        approxMinutes = 12,
        rounds = 3,
        steps = listOf(
            "El artista dibuja en su teléfono, sin escribir",
            "Los demás inventan un título falso",
            "Voten el título real; las mentiras con votos también suman",
        ),
        accent = 0xFF06B6D4,
    )

    val Subasta = GameInfo(
        id = "subasta",
        title = "Subasta Ciega",
        tagline = "Farol, codicia y martillo",
        description = "La tele muestra una carta de botín con valor visible o misterioso. " +
            "Todos pujan en secreto con sus monedas. El mayor postor se lo lleva, pero si hay " +
            "empate en la cima, ambos se cancelan y gana el siguiente.",
        minPlayers = 2,
        maxPlayers = 8,
        approxMinutes = 6,
        rounds = 6,
        steps = listOf(
            "Mira la carta pública y la carta secreta de tu informante",
            "Puja en secreto desde el teléfono",
            "El martillo revela: empates se cancelan y gana el siguiente",
        ),
        accent = 0xFFF59E0B,
    )

    val Fibbage = GameInfo(
        id = "fibbage",
        title = "Falso Conocimiento",
        tagline = "Si no sabes, invéntalo",
        description = "La tele lanza un dato insólito de cine y series. Nadie sabe la respuesta " +
            "exacta: cada jugador inventa una mentira convincente. Luego todos votan la que " +
            "creen verdadera. La respuesta real también está en la lista.",
        minPlayers = 2,
        maxPlayers = 8,
        approxMinutes = 10,
        rounds = 4,
        steps = listOf(
            "Lee el dato raro en la tele",
            "Inventa una respuesta falsa y creíble",
            "Vota la que creas real; si muerden tu mentira, sumas",
        ),
        accent = 0xFF10B981,
    )


    val UltimaPalabra = GameInfo(
        id = "ultima_palabra",
        title = "La Última Palabra",
        tagline = "Escribe, ríe y vota",
        description = "Un concurso de ingenio: la tele lanza una frase y todos escriben la " +
            "respuesta más graciosa. La sala vota y la última palabra la tiene quien más hizo reír.",
        minPlayers = 3,
        maxPlayers = 8,
        approxMinutes = 24,
        rounds = 12,
        steps = listOf(
            "Lee la frase y escribe tu respuesta",
            "Vota la respuesta que más te haga reír; no puedes votar la tuya",
            "La ronda final vale el doble y el podio se lleva la gloria",
        ),
        accent = 0xFFF97316,
    )

    val VoxPopuli = GameInfo(
        id = "vox_populi",
        title = "Vox Populi",
        tagline = "La gente dijo cosas raras",
        description = "Un noticiero de encuestas donde gana quien piensa como la mayoría. " +
            "Cada ronda lanza una pregunta y todos intentan adivinar la respuesta más popular.",
        minPlayers = 2,
        maxPlayers = 8,
        approxMinutes = 22,
        rounds = 12,
        steps = listOf(
            "Lee la encuesta y escribe la respuesta que crees más popular",
            "El servidor revela el top y reparte puntos por ranking",
            "La ronda final vale doble; sobrevive al juicio de la mayoría",
        ),
        accent = 0xFF22D3EE,
    )

    val FueraDeEpoca = GameInfo(
        id = "fuera_de_epoca",
        title = "Fuera de Época",
        tagline = "¿En qué año pasó?",
        description = "El profesor Tic Tac viaja en el tiempo y pone a prueba tu memoria. " +
            "Cada ronda muestra un estreno, invento o momento cultural y tú eliges el año.",
        minPlayers = 2,
        maxPlayers = 8,
        approxMinutes = 22,
        rounds = 12,
        steps = listOf(
            "Lee el evento y elige el año correcto entre cuatro opciones",
            "Acierta para sumar; la racha paga un bonus extra",
            "La ronda final vale doble y el podio se decide por precisión",
        ),
        accent = 0xFFA78BFA,
    )

    val TextoRoto = GameInfo(
        id = "texto_roto",
        title = "Texto Roto",
        tagline = "El chat que nadie pidió",
        description = "Una conversación empieza bien y todos la arruinan. Cada jugador escribe " +
            "una línea para su personaje; después la sala vota la intervención más graciosa.",
        minPlayers = 3,
        maxPlayers = 8,
        approxMinutes = 26,
        rounds = 8,
        steps = listOf(
            "Lee la escena y tu papel; escribe tu línea",
            "La tele arma el chat con todas las intervenciones",
            "Vota la línea más graciosa; no puedes votar la tuya",
        ),
        accent = 0xFF34D399,
    )

    val FranelaFest = GameInfo(
        id = "franela_fest",
        title = "Franela Fest",
        tagline = "Dibuja, esloganiza y vende",
        description = "El concurso de camisetas más dudoso de la televisión. Dibuja en tu teléfono, " +
            "escribe un eslogan y convence a la sala de que tu diseño merece existir.",
        minPlayers = 3,
        maxPlayers = 8,
        approxMinutes = 28,
        rounds = 6,
        steps = listOf(
            "Dibuja tu camiseta en el teléfono y escribe un eslogan",
            "La tele muestra los diseños de forma anónima",
            "Vota el diseño que te pondrías o el que regalarías por venganza",
        ),
        accent = 0xFFF472B6,
    )

    val ElTasador = GameInfo(
        id = "el_tasador",
        title = "El Tasador",
        tagline = "Todo tiene un precio",
        description = "Objetos raros, pitches dudosos y un valor que nadie conoce. Convence a la " +
            "sala de que tu objeto vale una fortuna y acierta cuánto vale de verdad.",
        minPlayers = 2,
        maxPlayers = 8,
        approxMinutes = 26,
        rounds = 10,
        steps = listOf(
            "Mira el objeto raro y escribe tu pitch con un valor",
            "La sala vota el pitch más convincente",
            "El valor real se revela: el pitch más cercano también suma",
        ),
        accent = 0xFFFBBF24,
    )

    /**
     * Beta Fiesta 3.0: el selector muestra solo los seis juegos nuevos. Los
     * juegos históricos siguen existiendo en la app estable, pero no se cargan
     * en esta beta para no mezclar contenido viejo con el casting nuevo.
     */
    val all: List<GameInfo> = listOf(
        UltimaPalabra, VoxPopuli, FueraDeEpoca, TextoRoto, FranelaFest, ElTasador,
    )

    /** Secciones de Trivia. «General» mezcla todas; el resto filtra por tipo de obra. */
    val triviaPacks: List<TriviaPack> = listOf(
        TriviaPack("general", "General", "Una mezcla de todo el catálogo"),
        TriviaPack("cine", "Cine", "Películas de todos los géneros"),
        TriviaPack("series", "Series", "Series y temporadas"),
        TriviaPack("anime", "Anime", "Anime y animación japonesa"),
        TriviaPack("infantil", "Infantil", "Para jugar con los peques"),
        TriviaPack("novelas", "Novelas", "Telenovelas y dramas"),
    )

    fun triviaPack(id: String?): TriviaPack =
        triviaPacks.firstOrNull { it.id == id } ?: triviaPacks.first()

    fun byId(id: String?): GameInfo =
        all.firstOrNull { it.id == id } ?: Trivia

    fun isValid(id: String?): Boolean = all.any { it.id == id }
}
