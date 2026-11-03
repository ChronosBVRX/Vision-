"""Contenido original de Vision+ Fiesta.

Todo el texto de este archivo es nuevo y se escribió para Vision+. No se copió
contenido, chistes ni personajes de Jackbox. Las entradas marcadas con
``adult=True`` solo se sirven cuando la sala está en modo sin filtro.
"""

from __future__ import annotations

# ---------------------------------------------------------------------------
# La Última Palabra (escribir + votar)
# ---------------------------------------------------------------------------

ULTIMA_PALABRA_PROMPTS: tuple[dict, ...] = (
    {"text": "El nuevo reality de Vision+ se llama «Amor en Modo Avión» y trata de…"},
    {"text": "La telenovela que rompió el internet este año se llamaba…"},
    {"text": "El peor consejo que te puede dar un influencer de streaming es…"},
    {"text": "Si tu smart TV pudiera hablar, lo primero que diría sería…"},
    {"text": "La película que cancelaron por ser demasiado mexicana se llamaba…"},
    {"text": "El grupo de WhatsApp de la familia se volvió peligroso cuando…"},
    {"text": "La IA de soporte técnico se enamoró del cliente cuando…"},
    {"text": "El nuevo servicio de streaming para mascotas incluye…"},
    {"text": "La escena más dramática de la telenovela fue cuando…"},
    {"text": "El documental que nadie pidió se titula…"},
    {"text": "La razón real por la que tu serie favorita fue cancelada…"},
    {"text": "El nuevo superhéroe mexicano tiene el poder de…"},
    {"text": "La app de citas para fans del cine te empareja según…"},
    {"text": "El premio a la peor actuación se lo llevó…"},
    {"text": "La reseña más honesta de esta película diría…"},
    {"text": "El anime que adaptaron a telenovela trataba de…"},
    {"text": "La nueva temporada de tu serie favorita empieza con…"},
    {"text": "El algoritmo de recomendaciones te sugirió algo raro:…"},
    {"text": "El comercial más incómodo de la televisión decía…"},
    {"text": "La fruta que nunca debió tener su propio documental…"},
    {"text": "El nuevo programa de cocina para gente que no cocina se llama…"},
    {"text": "La película de terror más barata de la historia trata de…"},
    {"text": "El libro que todos dicen haber leído pero nadie terminó…"},
    {"text": "La nueva atracción del parque temático de Vision+ es…"},
    {"text": "El noticiero insistió en cubrir la noticia más aburrida:…"},
    {"text": "El personaje que merecía su propio spin-off era…"},
    {"text": "La frase que tu mamá dice cuando ve una película es…"},
    {"text": "El peor nombre para una banda de rock romántico es…"},
    {"text": "La nueva función de la tele que nadie pidió es…"},
    {"text": "El final alternativo de esa película famosa era…"},
    {"text": "El villano de la nueva saga solo quería…"},
    {"text": "La serie que empezó como comedia y terminó como…"},
    {"text": "El menú del restaurante temático de tu serie favorita incluye…"},
    {"text": "La razón por la que el actor principal renunció a la secuela…"},
    {"text": "El nuevo canal de televisión dedicado a…"},
    {"text": "La crítica de cine más sincera que existe es…"},
    {"text": "El juguete basado en la película que causó demandas fue…"},
    {"text": "La escena que todos saltan en el rewatch es…"},
    {"text": "El título de la secuela que nadie entendió…"},
    {"text": "El mensaje motivacional que esconde una amenaza…"},
    {"text": "El nuevo género musical que nació en un karaoke…"},
    {"text": "El consejo de belleza que salió carísimo…"},
    {"text": "La excusa perfecta para no ir a la reunión familiar…"},
    {"text": "El nombre del restaurante que intenta ser moderno…"},
    {"text": "El slogan más desafortunado de una marca…"},
    {"text": "El nuevo deporte olímpico que sí merece transmisión…"},
    {"text": "La razón por la que el wifi de la casa se volvió personaje…"},
    {"text": "El regalo de cumpleaños que arruinó la amistad…"},
    {"text": "La frase que nunca quieres escuchar en una entrevista de trabajo…"},
    {"text": "El nuevo servicio de suscripción que cobra por…"},
    {"text": "La película que debería tener una versión en telenovela…"},
    {"text": "El secreto mejor guardado de la industria del streaming…"},
    {"text": "El nuevo programa de concursos donde todos pierden…"},
    {"text": "La canción que no puedes sacarte de la cabeza…"},
    {"text": "El actor que nació para hacer de villano…"},
    {"text": "El meme que se volvió más famoso que la película…"},
    {"text": "La escena de apertura que costó más que toda la temporada…"},
    {"text": "El nuevo formato de televisión que es solo…"},
    {"text": "La serie que debería llamarse de otra forma…"},
    {"text": "El personaje que claramente no leyó el guion…"},
    {"text": "La trama secundaria que se comió la serie…"},
    {"text": "El crossover que nadie pidió pero todos verían…"},
    {"text": "La frase con la que empieza una mala cita…"},
    {"text": "El nuevo día festivo inventado por una marca…"},
    {"text": "El error de continuidad más famoso de la televisión…"},
    {"text": "El título de la autobiografía de un perro influencer…"},
    {"text": "La moda que regresó y nadie estaba listo…"},
    {"text": "El nuevo sabor de palomitas para ver terror…"},
    {"text": "La razón por la que tu serie favorita tiene risas grabadas…"},
    {"text": "El nuevo superpoder que solo sirve para…"},
    {"text": "La frase que nadie dice en una película real…"},
    {"text": "El peor final posible para una saga épica…"},
    {"text": "El nuevo centro de entretenimiento para…"},
    {"text": "La reseña de una estrella que empieza con…"},
    {"text": "El nuevo villano con un plan muy mal pensado…"},
    {"text": "La canción que suena en todos los trailers…"},
    {"text": "El título de la película que solo existe en tu cabeza…"},
    # Sin filtro (adulto suave, sin contenido explícito)
    {"text": "El nuevo programa de citas para exes se llama…", "adult": True},
    {"text": "La excusa más creativa para terminar una relación…", "adult": True},
    {"text": "El nuevo cóctel del bar de la esquina se llama…", "adult": True},
    {"text": "El mensaje de texto que arruinó la noche…", "adult": True},
    {"text": "El nuevo servicio de streaming para adultos incluye…", "adult": True},
    {"text": "La frase que nunca debes decir en una primera cita…", "adult": True},
    {"text": "El nuevo perfume que promete demasiado…", "adult": True},
    {"text": "El nombre del grupo de vecinos que se odia…", "adult": True},
    {"text": "La razón por la que te vetaron del grupo familiar…", "adult": True},
    {"text": "El nuevo reality de citas en la oficina se llama…", "adult": True},
)

# ---------------------------------------------------------------------------
# Vox Populi (encuestas)
# ---------------------------------------------------------------------------

VOX_QUESTIONS: tuple[dict, ...] = (
    {
        "text": "En una palabra, ¿qué es lo primero que haces al llegar a casa?",
        "answers": [
            ("quitarse los zapatos", 382), ("saludar", 264), ("comer", 198),
            ("descansar", 151), ("ir al baño", 96), ("prender la tele", 74),
        ],
    },
    {
        "text": "¿Cuál es la mejor excusa para no salir el viernes?",
        "answers": [
            ("tengo sueño", 341), ("me duele la cabeza", 287), ("tengo que trabajar", 223),
            ("no tengo dinero", 165), ("ya tengo planes", 112), ("está lloviendo", 68),
        ],
    },
    {
        "text": "¿Qué comida nunca falla en una reunión?",
        "answers": [
            ("pizza", 402), ("tacos", 356), ("botanas", 248), ("pollo", 187),
            ("pasta", 121), ("ensalada", 54),
        ],
    },
    {
        "text": "¿Qué es lo más molesto de ir al cine?",
        "answers": [
            ("el precio", 389), ("la gente que habla", 331), ("los anuncios", 247),
            ("las filas", 168), ("las palomitas caras", 109), ("el asiento", 72),
        ],
    },
    {
        "text": "¿Cuál es el peor superpoder?",
        "answers": [
            ("volar pero lento", 312), ("leer mentes y arrepentirte", 276), ("invisibilidad intermitente", 219),
            ("fuerza solo los martes", 154), ("hablar con las plantas", 98), ("teletransporte con náuseas", 61),
        ],
    },
    {
        "text": "¿Qué animal sería el peor compañero de cuarto?",
        "answers": [
            ("mapache", 354), ("loro", 289), ("gato", 221), ("perro", 174),
            ("mono", 113), ("hámster", 66),
        ],
    },
    {
        "text": "¿Cuál es la frase más mexicana que existe?",
        "answers": [
            ("a poco", 371), ("no manches", 312), ("órale", 246), ("chale", 177),
            ("sale", 104), ("¿mande?", 71),
        ],
    },
    {
        "text": "¿Qué harías si te quedaras solo en casa un fin de semana?",
        "answers": [
            ("dormir", 398), ("ver series", 327), ("limpiar", 211), ("invitar gente", 158),
            ("cocinar", 92), ("salir", 55),
        ],
    },
    {
        "text": "¿Cuál es el mejor regalo barato?",
        "answers": [
            ("una taza", 344), ("una planta", 281), ("chocolates", 226), ("una tarjeta", 163),
            ("una playera", 101), ("un libro", 58),
        ],
    },
    {
        "text": "¿Qué es lo primero que revisas en el celular al despertar?",
        "answers": [
            ("hora", 412), ("mensajes", 349), ("redes", 233), ("correo", 146),
            ("clima", 84), ("noticias", 51),
        ],
    },
    {
        "text": "¿Cuál es el peor nombre para una mascota?",
        "answers": [
            ("señor bigotes", 327), ("pelusa", 284), ("rambo", 218), ("chocolate", 157),
            ("abogado", 96), ("wifi", 63),
        ],
    },
    {
        "text": "¿Qué es lo más difícil de cocinar?",
        "answers": [
            ("arroz", 361), ("tortillas", 298), ("pollo", 229), ("pastel", 164),
            ("sopa", 97), ("huevos", 52),
        ],
    },
    {
        "text": "¿Qué canción todos cantan mal pero con ganas?",
        "answers": [
            ("la de los 80", 342), ("la de una telenovela", 287), ("la de un funeral", 221),
            ("la del karaoke", 158), ("la del mundial", 103), ("la de moda", 59),
        ],
    },
    {
        "text": "¿Cuál es el peor trabajo de la televisión?",
        "answers": [
            ("comentarista deportivo", 318), ("presentador de clima", 276), ("actor de telenovela", 231),
            ("jurado de reality", 161), ("reportero de calle", 99), ("locutor de madrugada", 57),
        ],
    },
    {
        "text": "¿Qué app pasarías más tiempo usando si no tuviera internet?",
        "answers": [
            ("mapas", 356), ("cámara", 301), ("notas", 224), ("música", 167),
            ("calculadora", 95), ("reloj", 51),
        ],
    },
    {
        "text": "¿Qué es lo que más grita una telenovela?",
        "answers": [
            ("el secreto", 371), ("la bofetada", 309), ("la herencia", 242), ("el amor", 169),
            ("la venganza", 108), ("la música", 62),
        ],
    },
    {
        "text": "¿Cuál es el mejor lugar para una siesta?",
        "answers": [
            ("la cama", 421), ("el sofá", 338), ("el trabajo", 219), ("el cine", 153),
            ("el transporte", 92), ("la playa", 49),
        ],
    },
    {
        "text": "¿Qué es lo más raro que has visto en una boda?",
        "answers": [
            ("un perro de testigo", 312), ("un discurso eterno", 278), ("una pelea", 226),
            ("un karaoke", 158), ("un vestido blanco", 97), ("lluvia", 54),
        ],
    },
    {
        "text": "¿Cuál es la mejor manera de arruinar una sorpresa?",
        "answers": [
            ("decirla sin querer", 364), ("compartir el chat", 297), ("llegar tarde", 218),
            ("preguntar de más", 154), ("poner un estado", 96), ("invitar a alguien", 52),
        ],
    },
    {
        "text": "¿Qué personaje histórico sería el peor influencer?",
        "answers": [
            ("Napoleón", 301), ("Cleopatra", 267), ("Einstein", 224), ("Juana de Arco", 158),
            ("Marco Polo", 94), ("Darwin", 51),
        ],
    },
    {
        "text": "¿Cuál es el peor consejo financiero?",
        "answers": [
            ("invierte en cripto", 352), ("compra y luego ves", 286), ("pide un préstamo", 227),
            ("no ahorres", 161), ("todo al rojo", 99), ("presta tu tarjeta", 56),
        ],
    },
    {
        "text": "¿Qué es lo que más miedo da en una primera cita?",
        "answers": [
            ("quedarse sin tema", 379), ("la cuenta", 311), ("conocer a la familia", 238),
            ("el ex", 164), ("la ropa", 101), ("llegar tarde", 57),
        ],
    },
    {
        "text": "¿Cuál es la mejor forma de fingir que entiendes una película?",
        "answers": [
            ("decir que es profunda", 341), ("culpar al final", 282), ("hablar del director", 229),
            ("decir que la viste en cine", 157), ("asentir", 98), ("cambiar de tema", 55),
        ],
    },
    {
        "text": "¿Qué es lo más difícil de mantener en un grupo de amigos?",
        "answers": [
            ("los planes", 337), ("el chat vivo", 279), ("la cuenta", 223), ("los secretos", 168),
            ("la puntualidad", 103), ("el buen humor", 60),
        ],
    },
    {
        "text": "¿Cuál es el peor lugar para una cita romántica?",
        "answers": [
            ("el trabajo", 318), ("la casa de la ex", 281), ("el supermercado", 224),
            ("el cine de terror", 161), ("la iglesia", 97), ("el gimnasio", 53),
        ],
    },
    {
        "text": "¿Qué es lo primero que se pierde en una mudanza?",
        "answers": [
            ("el cargador", 361), ("los cubiertos", 289), ("los documentos", 231),
            ("la paciencia", 162), ("el control", 98), ("la llave", 55),
        ],
    },
    # Sin filtro
    {"text": "¿Cuál es la peor excusa para no responder un mensaje?", "answers": [
        ("se me acabó la batería", 342), ("no vi la notificación", 281), ("estaba dormido", 226),
        ("me quedé sin datos", 159), ("se me olvidó", 102), ("estaba en el baño", 58),
    ], "adult": True},
    {"text": "¿Qué es lo más incómodo de una fiesta de exalumnos?", "answers": [
        ("ver al ex", 331), ("las fotos viejas", 274), ("las mentiras", 221),
        ("la cuenta", 157), ("los nombres", 96), ("la música", 51),
    ], "adult": True},
)

# ---------------------------------------------------------------------------
# Fuera de Época (años)
# ---------------------------------------------------------------------------

TIME_EVENTS: tuple[dict, ...] = (
    {"event": "Se estrena la primera película de Toy Story", "year": 1995},
    {"event": "Se lanza el primer iPhone", "year": 2007},
    {"event": "Se estrena la primera temporada de Stranger Things", "year": 2016},
    {"event": "Se estrena El Padrino", "year": 1972},
    {"event": "Se lanza Netflix como servicio de DVD por correo", "year": 1997},
    {"event": "Se estrena la primera película de Harry Potter", "year": 2001},
    {"event": "Se lanza YouTube", "year": 2005},
    {"event": "Se estrena la primera película de Matrix", "year": 1999},
    {"event": "Se lanza el primer iPod", "year": 2001},
    {"event": "Se estrena la primera temporada de Los Simpson", "year": 1989},
    {"event": "Se estrena Coco de Pixar", "year": 2017},
    {"event": "Se lanza Facebook", "year": 2004},
    {"event": "Se estrena la primera película de Volver al Futuro", "year": 1985},
    {"event": "Se estrena la primera película de Rocky", "year": 1976},
    {"event": "Se lanza el primer tweet", "year": 2006},
    {"event": "Se estrena la primera temporada de Breaking Bad", "year": 2008},
    {"event": "Se estrena la primera película de Shrek", "year": 2001},
    {"event": "Se lanza el primer Android comercial", "year": 2008},
    {"event": "Se estrena la primera película de Titanic", "year": 1997},
    {"event": "Se lanza Spotify", "year": 2008},
    {"event": "Se estrena la primera temporada de Game of Thrones", "year": 2011},
    {"event": "Se estrena la primera película de Los Increíbles", "year": 2004},
    {"event": "Se lanza el primer iPad", "year": 2010},
    {"event": "Se estrena la primera temporada de Black Mirror", "year": 2011},
    {"event": "Se estrena la primera película de Spider-Man de Sam Raimi", "year": 2002},
    {"event": "Se lanza WhatsApp", "year": 2009},
    {"event": "Se estrena la primera temporada de The Office (EE. UU.)", "year": 2005},
    {"event": "Se estrena la primera película de Frozen", "year": 2013},
    {"event": "Se lanza TikTok fuera de China", "year": 2017},
    {"event": "Se estrena la primera película de Jurassic Park", "year": 1993},
    {"event": "Se estrena la primera temporada de Rick and Morty", "year": 2013},
    {"event": "Se lanza el primer servicio de streaming de Disney+", "year": 2019},
    {"event": "Se estrena la primera película de Parasite", "year": 2019},
    {"event": "Se lanza el primer modelo de ChatGPT", "year": 2022},
    {"event": "Se estrena la primera película de Barbie", "year": 2023},
    {"event": "Se estrena la primera temporada de Wednesday", "year": 2022},
    {"event": "Se lanza el iPod Nano", "year": 2005},
    {"event": "Se estrena la primera película de El Señor de los Anillos", "year": 2001},
    {"event": "Se lanza el primer Tesla Model S", "year": 2012},
    {"event": "Se estrena la primera temporada de Euphoria", "year": 2019},
    {"event": "Se estrena la primera película de Cincuenta sombras de Grey", "year": 2015, "adult": True},
    {"event": "Se lanza Tinder", "year": 2012, "adult": True},
    {"event": "Se estrena la primera temporada de Sex and the City", "year": 1998, "adult": True},
    {"event": "Se estrena la primera película de El club de la pelea", "year": 1999, "adult": True},
    {"event": "Se lanza OnlyFans", "year": 2016, "adult": True},
    {"event": "Se estrena la primera temporada de The Boys", "year": 2019, "adult": True},
)

# ---------------------------------------------------------------------------
# Texto Roto (escenas colaborativas)
# ---------------------------------------------------------------------------

TEXTO_ROTO_SCENES: tuple[dict, ...] = (
    {
        "title": "Soporte técnico",
        "roles": ["cliente", "agente", "supervisor", "IA"],
        "seed": "Cliente: «Hola, mi tele no prende». Agente: «¿Ya la conectó?».",
    },
    {
        "title": "Reunión de trabajo",
        "roles": ["jefe", "becario", "recursos humanos", "el que no tenía que estar"],
        "seed": "Jefe: «Esta reunión pudo ser un correo». Becario: «...».",
    },
    {
        "title": "Grupo de WhatsApp familiar",
        "roles": ["mamá", "tía", "primo", "abuela"],
        "seed": "Mamá: «Buenos días, bendiciones». Tía: «...».",
    },
    {
        "title": "Primera cita",
        "roles": ["cita 1", "cita 2", "mesero", "el ex"],
        "seed": "Cita 1: «¿Y tú qué haces?». Cita 2: «...».",
    },
    {
        "title": "Chat de fans",
        "roles": ["fan 1", "fan 2", "moderador", "actor sorpresa"],
        "seed": "Fan 1: «¿Vieron el final?». Fan 2: «...».",
    },
    {
        "title": "Entrevista de trabajo",
        "roles": ["reclutador", "candidato", "jefe", "la IA de RRHH"],
        "seed": "Reclutador: «¿Cuál es tu mayor defecto?». Candidato: «...».",
    },
    {
        "title": "Restaurante elegante",
        "roles": ["mesero", "cliente", "chef", "crítico"],
        "seed": "Mesero: «El chef recomienda...». Cliente: «...».",
    },
    {
        "title": "Stream en vivo",
        "roles": ["streamer", "moderador", "suscriptor", "mamá"],
        "seed": "Streamer: «¡Gracias por el donativo!». Mamá: «...».",
    },
    {
        "title": "App de citas",
        "roles": ["usuario 1", "usuario 2", "algoritmo", "ex"],
        "seed": "Algoritmo: «Hicimos match». Usuario 1: «...».",
    },
    {
        "title": "Aeropuerto",
        "roles": ["pasajero", "agente", "piloto", "el que corre"],
        "seed": "Agente: «Su vuelo se retrasó». Pasajero: «...».",
    },
    {
        "title": "Fiesta de disfraces",
        "roles": ["anfitrión", "invitado", "vecino", "el que no se disfrazó"],
        "seed": "Anfitrión: «¡Llegaste!». Invitado: «...».",
    },
    {
        "title": "Consulta médica",
        "roles": ["doctor", "paciente", "enfermera", "la IA del consultorio"],
        "seed": "Doctor: «Los resultados salieron...». Paciente: «...».",
    },
    {
        "title": "Clase de yoga",
        "roles": ["instructora", "alumno nuevo", "alumno intenso", "el que se durmió"],
        "seed": "Instructora: «Respira». Alumno nuevo: «...».",
    },
    {
        "title": "Supermercado",
        "roles": ["cajero", "cliente", "gerente", "el de la fila"],
        "seed": "Cajero: «¿Tiene tarjeta?». Cliente: «...».",
    },
    {
        "title": "Karaoke",
        "roles": ["cantante", "amigo", "DJ", "vecino"],
        "seed": "DJ: «Siguiente canción». Cantante: «...».",
    },
    {
        "title": "Reunión de vecinos",
        "roles": ["administrador", "vecino 1", "vecino 2", "el perro"],
        "seed": "Administrador: «Hay un problema con el agua». Vecino 1: «...».",
    },
    {
        "title": "Agencia de viajes",
        "roles": ["agente", "cliente", "guía", "la IA"],
        "seed": "Agente: «Le tengo un paquete». Cliente: «...».",
    },
    {
        "title": "Telenovela",
        "roles": ["protagonista", "villana", "mayordomo", "el secreto"],
        "seed": "Villana: «Nunca te quedarás con la herencia». Protagonista: «...».",
    },
    {
        "title": "Entrevista a un famoso",
        "roles": ["reportero", "famoso", "manager", "público"],
        "seed": "Reportero: «¿Cuál es su secreto?». Famoso: «...».",
    },
    {
        "title": "Sala de espera",
        "roles": ["recepcionista", "paciente", "doctor", "el que llegó sin cita"],
        "seed": "Recepcionista: «¿Tiene cita?». Paciente: «...».",
    },
    {
        "title": "Chat de soporte de la tele",
        "roles": ["usuario", "bot", "humano", "supervisor"],
        "seed": "Bot: «¿Ya intentó apagarla y prenderla?». Usuario: «...».",
    },
    {
        "title": "Casting de cine",
        "roles": ["director", "actor", "productor", "el guion"],
        "seed": "Director: «Necesito más emoción». Actor: «...».",
    },
    {
        "title": "Fiesta de oficina",
        "roles": ["jefe", "empleado", "becario", "el de sistemas"],
        "seed": "Jefe: «¡Todos somos familia!». Empleado: «...».",
    },
    {
        "title": "Consulta con la IA",
        "roles": ["usuario", "IA", "ingeniero", "el error"],
        "seed": "Usuario: «¿Me quieres?». IA: «...».",
    },
    {
        "title": "Viaje en avión",
        "roles": ["piloto", "pasajero", "sobrecargo", "el niño"],
        "seed": "Piloto: «Hay turbulencia». Pasajero: «...».",
    },
    {
        "title": "After de la oficina",
        "roles": ["jefe", "becario", "recursos humanos", "el que grabó todo"],
        "seed": "Jefe: «Lo que pasa en la oficina se queda en la oficina». Becario: «...».",
        "adult": True,
    },
    {
        "title": "Boda de la ex",
        "roles": ["invitado", "la ex", "el novio", "la tía"],
        "seed": "La ex: «Qué bueno que viniste». Invitado: «...».",
        "adult": True,
    },
    {
        "title": "App de citas",
        "roles": ["match 1", "match 2", "algoritmo", "el ex"],
        "seed": "Algoritmo: «Compatibilidad 98 %». Match 1: «...».",
        "adult": True,
    },
    {
        "title": "Despedida de soltero",
        "roles": ["novio", "mejor amigo", "el organizador", "la policía"],
        "seed": "Mejor amigo: «Todo bajo control». Novio: «...».",
        "adult": True,
    },
    {
        "title": "Chat con el ex",
        "roles": ["tú", "tu ex", "tu mejor amigo", "la dignidad"],
        "seed": "Tu ex: «¿Estás despierto?». Tú: «...».",
        "adult": True,
    },
)

# ---------------------------------------------------------------------------
# Franela Fest (dibujo + eslogan)
# ---------------------------------------------------------------------------

FRANELA_PROMPTS: tuple[dict, ...] = (
    {
        "prompt": "Diseña la camiseta oficial de una serie sobre un vampiro que odia la sangre.",
        "slogans": ["Eterno, pero con sueño", "Muerde suave", "Cero grupos sanguíneos"],
    },
    {
        "prompt": "Diseña la camiseta de un restaurante donde todo se sirve en tacos.",
        "slogans": ["Todo cabe en un taco", "La vida es un taco", "Sin cubiertos, sin problemas"],
    },
    {
        "prompt": "Diseña la camiseta de una app de citas para fans del cine.",
        "slogans": ["Tu media naranja tiene subtítulos", "Match de culto", "Amor en 4K"],
    },
    {
        "prompt": "Diseña la camiseta de una telenovela protagonizada por una licuadora.",
        "slogans": ["Todo lo mezcla", "Amor a máxima potencia", "Se rompió el corazón"],
    },
    {
        "prompt": "Diseña la camiseta de un equipo de futbol formado por gatos.",
        "slogans": ["Garra y gol", "Los michis del gol", "Juegan con las patas"],
    },
    {
        "prompt": "Diseña la camiseta de una película de terror sobre una impresora.",
        "slogans": ["Sin papel, sin piedad", "Error fatal", "El toner del miedo"],
    },
    {
        "prompt": "Diseña la camiseta de un reality de cocina para personas que no cocinan.",
        "slogans": ["Se quemó", "Cocina o llora", "La receta era cereal"],
    },
    {
        "prompt": "Diseña la camiseta de un superhéroe que solo puede volar los martes.",
        "slogans": ["Los martes soy leyenda", "Vuela cuando quiere", "Agenda primero"],
    },
    {
        "prompt": "Diseña la camiseta de una banda de rock formada por abuelitas.",
        "slogans": ["Rock y crochet", "Abuela del metal", "Teje y destruye"],
    },
    {
        "prompt": "Diseña la camiseta de un documental sobre el wifi de la casa.",
        "slogans": ["Señal débil, amor fuerte", "Reinicia y reza", "El router tiene la culpa"],
    },
    {
        "prompt": "Diseña la camiseta de una serie sobre un detective que resuelve todo con antojos.",
        "slogans": ["El caso se cocina", "Pistas y picante", "Misterio al pastor"],
    },
    {
        "prompt": "Diseña la camiseta de un parque temático dedicado a las filas.",
        "slogans": ["La atracción es esperar", "Fila eterna", "Vuelve a la fila"],
    },
    {
        "prompt": "Diseña la camiseta de un anime sobre contadores.",
        "slogans": ["Cuenta conmigo", "El poder de los números", "Balance final"],
    },
    {
        "prompt": "Diseña la camiseta de una marca de ropa para plantas.",
        "slogans": ["Vístete de verde", "Moda fotosintética", "Raíces de estilo"],
    },
    {
        "prompt": "Diseña la camiseta de un noticiero que solo cubre buenas noticias.",
        "slogans": ["Nada malo, nada nuevo", "Buenas y nada más", "Información feliz"],
    },
    {
        "prompt": "Diseña la camiseta de una película de acción protagonizada por un sloth.",
        "slogans": ["Lento pero letal", "Ya voy", "Acción en cámara lenta"],
    },
    {
        "prompt": "Diseña la camiseta de una escuela para villanos torpes.",
        "slogans": ["Fallar es aprender", "El mal se equivoca", "Plan B"],
    },
    {
        "prompt": "Diseña la camiseta de un bar donde todo se paga con memes.",
        "slogans": ["Un meme, por favor", "Pago con risas", "La cuenta es viral"],
    },
    {
        "prompt": "Diseña la camiseta de un grupo de autoayuda para series canceladas.",
        "slogans": ["Nos cancelaron, no nos olvidaron", "Final abierto", "Renovación pendiente"],
    },
    {
        "prompt": "Diseña la camiseta de una telenovela sobre el café de la oficina.",
        "slogans": ["Amor descafeinado", "La taza de la discordia", "Sin azúcar, con drama"],
    },
    {
        "prompt": "Diseña la camiseta de una app de citas para gente que ya no cree en el amor.",
        "slogans": ["Match con reservas", "Amor en modo avión", "Cero expectativas"],
        "adult": True,
    },
    {
        "prompt": "Diseña la camiseta de una despedida de soltero que salió mal.",
        "slogans": ["Lo que pasó, pasó", "Sin fotos, por favor", "La resaca es real"],
        "adult": True,
    },
    {
        "prompt": "Diseña la camiseta de un bar para exes.",
        "slogans": ["Bienvenido de nuevo", "La primera ronda la paga el ex", "Sin rencores, con shots"],
        "adult": True,
    },
    {
        "prompt": "Diseña la camiseta de una telenovela con un triángulo amoroso en la oficina.",
        "slogans": ["Recursos humanos lo vio todo", "Amor en horario laboral", "Renuncia o besa"],
        "adult": True,
    },
    {
        "prompt": "Diseña la camiseta de un grupo de apoyo para gente que stalkea a su ex.",
        "slogans": ["Un día a la vez", "No mires el perfil", "Bloqueado y en paz"],
        "adult": True,
    },
)

# ---------------------------------------------------------------------------
# El Tasador (objetos raros + pitch + valor)
# ---------------------------------------------------------------------------

TASADOR_OBJECTS: tuple[dict, ...] = (
    {"name": "Cuchara que nunca se calienta", "description": "Cuchara de metal frío permanente. Ideal para sopas que odias.", "value": 1200},
    {"name": "Reloj que solo marca la hora del almuerzo", "description": "Siempre son las 2:30. La gente creativa llega tarde.", "value": 900},
    {"name": "Retrato de un perro con traje de abogado", "description": "Firmado por nadie. El perro tiene más autoridad que tú.", "value": 2400},
    {"name": "Almohada con forma de router", "description": "Abraza la señal. No mejora el wifi, pero consuela.", "value": 700},
    {"name": "Taza que dice «no soy de la mañana»", "description": "La taza está vacía y también tu paciencia.", "value": 350},
    {"name": "Lámpara hecha con una licuadora", "description": "Ilumina y hace batidos. Nunca al mismo tiempo.", "value": 1800},
    {"name": "Disco de vinilo de sonidos de oficina", "description": "Incluye el clásico «¿me escuchas?» y el éxito «se cayó el sistema».", "value": 1500},
    {"name": "Silla para hacer fila en casa", "description": "Para practicar antes de ir al banco. Incluye número.", "value": 600},
    {"name": "Espejo que te dice que estás bien", "description": "Miente con cariño. Baterías no incluidas.", "value": 1100},
    {"name": "Sartén para cocinar una sola salchicha", "description": "Diseño minimalista para vidas minimalistas.", "value": 800},
    {"name": "Cuadro abstracto hecho con tickets de supermercado", "description": "Arte contemporáneo o basura acumulada, tú decides.", "value": 2000},
    {"name": "Tenis formal para ir a juntas", "description": "La comodidad del deporte con la seriedad de un correo sin respuesta.", "value": 1600},
    {"name": "Botella de agua que dice «no era agua»", "description": "Misterio, hidratación y una leve preocupación.", "value": 450},
    {"name": "Reloj de arena con arena de playa", "description": "Vacaciones en 3 minutos. Se acaba rápido, como todas.", "value": 950},
    {"name": "Mochila para llevar tu otra mochila", "description": "Dos compartimentos, cero explicaciones.", "value": 1300},
    {"name": "Vela con olor a domingo en casa", "description": "Cera, sopa y culpa. Edición limitada.", "value": 750},
    {"name": "Teléfono fijo con forma de control remoto", "description": "Confunde a todos. Funciona perfecto para no contestar.", "value": 1050},
    {"name": "Gorra para pensar mejor", "description": "No hay evidencia científica. Hay estilo.", "value": 500},
    {"name": "Plato que dice «ya comiste»", "description": "La dieta más pasivo-agresiva del mercado.", "value": 650},
    {"name": "Escultura de un taco llorando", "description": "Arte emocional para foodies con prisa.", "value": 2100},
    {"name": "Cobija con mangas para ver series", "description": "El verdadero traje de gala del domingo.", "value": 1400},
    {"name": "Cafetera que solo hace café descafeinado", "description": "La mentira más suave del mundo.", "value": 1000},
    {"name": "Libro de recetas para nunca cocinar", "description": "Incluye 200 formas de pedir delivery.", "value": 550},
    {"name": "Alfombra que parece una pizza", "description": "Nadie se atreve a pisarla. Éxito asegurado.", "value": 1700},
    {"name": "Audífonos para escuchar tus propios pensamientos", "description": "Cancelación de ruido total. Incluye ansiedad de regalo.", "value": 1900},
    {"name": "Vela con olor a ex", "description": "Cera, nostalgia y una leve necesidad de bloquear un número.", "value": 850, "adult": True},
    {"name": "Juego de mesa para parejas que discuten", "description": "Incluye dado, cronómetro y un abogado de guardia.", "value": 1200, "adult": True},
    {"name": "Camiseta que dice «no era una cita»", "description": "Perfecta para aclarar las cosas después del brunch.", "value": 700, "adult": True},
    {"name": "Perfume con aroma a primera cita", "description": "Notas de nervios, café y una mentira sobre tus pasatiempos.", "value": 1600, "adult": True},
    {"name": "Álbum de fotos de la boda cancelada", "description": "Cubierta de cuero, páginas en blanco y mucho espacio para terapia.", "value": 2200, "adult": True},
)

# ---------------------------------------------------------------------------
# Líneas de presentación (texto; la voz se genera aparte)
# ---------------------------------------------------------------------------

HOST_LINES: dict[str, dict[str, tuple[str, ...]]] = {
    "ultima_palabra": {
        "intro": (
            "Bienvenidos a La Última Palabra. Aquí no hay respuestas correctas, solo las que duelen de tanto reír.",
            "La tele pregunta, ustedes responden, y el resto decide si son genios o un peligro.",
            "Esta noche se gana con ingenio, mala leche y una buena dosis de vergüenza ajena.",
        ),
        "write": (
            "Escriban rápido y sin culpa. La respuesta más rara suele ser la que gana.",
            "Tienen la tele enfrente y el teléfono en la mano. No hay excusa para no improvisar.",
            "Piensen en la respuesta que haría reír a su peor enemigo.",
        ),
        "vote": (
            "Ahora la parte democrática: voten la respuesta que más les dolió de tanto reír.",
            "Nadie puede votar la suya. La honestidad también es parte del juego.",
            "Escojan con el corazón, no con la amistad.",
        ),
        "reveal": (
            "Veamos quién ganó el derecho a presumir y quién tendrá que explicarse.",
            "La sala ha hablado. Y la sala tiene un sentido del humor discutible.",
            "Conteo final. Respiren, que esto se pone bueno.",
        ),
        "podium": (
            "Tenemos campeón. También tenemos varias víctimas colaterales.",
            "Se acabó. El trofeo es simbólico, la vergüenza es para siempre.",
            "Gracias por jugar. La próxima vez traigan respuestas mejores.",
        ),
    },
    "vox_populi": {
        "intro": (
            "Bienvenidos a Vox Populi, el noticiero donde la gente opina y ustedes pagan las consecuencias.",
            "Aquí no importa la verdad: importa lo que la mayoría dijo.",
            "La calle habló. Y dijo cosas muy raras.",
        ),
        "guess": (
            "Escriban la respuesta que creen que dijo la mayoría. No la que ustedes dirían.",
            "Piensen como la gente. Sí, esa gente. La del grupo de WhatsApp.",
            "Una respuesta por persona. La originalidad no siempre paga.",
        ),
        "reveal": (
            "A ver qué dijo la gente. Prepárense para perder la fe en la humanidad.",
            "El pueblo habló. Y el pueblo no estudió la pregunta.",
            "Resultados de la encuesta. La mayoría gana, la minoría se queja.",
        ),
        "podium": (
            "Tenemos ganador. La gente lo confirma.",
            "Fin de la encuesta. Gracias por participar en nombre de la humanidad.",
            "El pueblo ha hablado. El pueblo se va a dormir.",
        ),
    },
    "fuera_de_epoca": {
        "intro": (
            "Bienvenidos a Fuera de Época. Si creen que el tiempo es lineal, este juego los va a decepcionar.",
            "Viajamos al pasado con una sola pregunta: ¿cuándo pasó?",
            "El profesor Tic Tac les pide que ordenen el caos. O que lo intenten.",
        ),
        "guess": (
            "Elijan el año. No vale decir «hace mucho».",
            "La historia no se improvisa, pero ustedes sí.",
            "Piensen en el año. Si dudan, piensen en el más viejo.",
        ),
        "reveal": (
            "Veamos quién tiene memoria histórica y quién solo vio el resumen.",
            "La línea temporal se revela. Y con ella, sus carencias.",
            "El año correcto era ese. Sí, ese. No pongan esa cara.",
        ),
        "podium": (
            "Tenemos un historiador oficial. Y varios turistas del tiempo.",
            "Fin del viaje. Dejen sus recuerdos en la salida.",
            "El pasado no se cambia, pero el marcador sí.",
        ),
    },
    "texto_roto": {
        "intro": (
            "Bienvenidos a Texto Roto. La conversación empieza bien y termina como siempre: mal.",
            "Cada quien escribe una línea. El resultado es un chat que nadie pidió.",
            "Aquí no hay guion, pero sí muchas ganas de arruinarlo.",
        ),
        "write": (
            "Escriban su línea. Puede ser genial, terrible o las dos cosas.",
            "La escena necesita su toque personal. Por favor, que no sea demasiado personal.",
            "Una línea por persona. Después veremos quién arruinó la conversación.",
        ),
        "vote": (
            "Voten la línea que más les hizo reír o la que más les preocupó.",
            "El chat ya está armado. Ahora elijan a su culpable favorito.",
            "No pueden votar la suya. La modestia es obligatoria.",
        ),
        "reveal": (
            "Veamos cómo quedó la conversación. Si alguien necesita terapia, es normal.",
            "El chat ha hablado. Y el chat no tiene filtro.",
            "Resultados. La línea más votada se lleva la gloria y la denuncia.",
        ),
        "podium": (
            "Fin del chat. Cierren la app y abracen a alguien.",
            "Tenemos ganador. La conversación nunca se recuperará.",
            "Gracias por escribir. La próxima vez usen menos puntos suspensivos.",
        ),
    },
    "franela_fest": {
        "intro": (
            "Bienvenidos a Franela Fest, el concurso donde el arte y el mal gusto se dan la mano.",
            "Dibujen, escriban un eslogan y esperen el juicio de la sala.",
            "La camiseta más fea puede ser la más cara. Como en la vida real.",
        ),
        "create": (
            "Tienen un lienzo y muy poco tiempo. Dibujen como si nadie los viera.",
            "El eslogan importa tanto como el dibujo. No lo olviden.",
            "Piensen en una camiseta que usarían solo para ir al súper a las 2 a.m.",
        ),
        "vote": (
            "Voten la camiseta que se pondrían o la que regalarían por venganza.",
            "No pueden votar la suya. El ego tiene límites.",
            "El jurado está listo. La moda también.",
        ),
        "reveal": (
            "Veamos qué diseñaron. La industria textil está temblando.",
            "El jurado deliberó. La ética del diseño, también.",
            "Resultados. Alguien va a tener que explicar ese dibujo.",
        ),
        "podium": (
            "Tenemos diseñador ganador. Y varias demandas pendientes.",
            "Fin del desfile. La pasarela se cierra.",
            "Gracias por participar. La próxima vez usen menos crayones.",
        ),
    },
    "el_tasador": {
        "intro": (
            "Bienvenidos a El Tasador, donde todo tiene un precio y nadie sabe cuál.",
            "Hoy vamos a subastar objetos que nadie pidió y todos quieren.",
            "El valor está en el ojo del comprador. Y en el pitch del vendedor.",
        ),
        "pitch": (
            "Convenzan a la sala de que este objeto vale una fortuna.",
            "Escriban su pitch y su valor. Sin miedo, sin vergüenza, sin devoluciones.",
            "La creatividad paga. La honestidad, a veces también.",
        ),
        "vote": (
            "Voten el pitch más convincente. O el más ridículo, ustedes deciden.",
            "El jurado evalúa la historia, no el objeto.",
            "No pueden votar el suyo. El mercado exige ética.",
        ),
        "reveal": (
            "Veamos cuánto pagarían y cuánto vale de verdad. Spoiler: nadie acierta.",
            "El precio real está por revelarse. La decepción también.",
            "Resultados. El objeto sigue siendo raro, pero ahora tiene comprador.",
        ),
        "podium": (
            "Tenemos al mejor tasador. Y al comprador más valiente.",
            "Fin de la subasta. El objeto vuelve a su caja.",
            "Gracias por participar. La próxima vez traigan algo menos raro.",
        ),
    },
}

HOST_LINES_ADULT: dict[str, tuple[str, ...]] = {
    "ultima_palabra": (
        "Modo sin filtro activado. La decencia se quedó en la puerta.",
        "Hoy sí pueden decir lo que piensan. Dios nos ampare.",
    ),
    "vox_populi": (
        "La gente dijo cosas que no se pueden repetir en televisión. Ustedes también.",
        "Sin filtro: la encuesta ahora es un juicio social.",
    ),
    "fuera_de_epoca": (
        "Modo sin filtro. La historia también tuvo sus noches raras.",
        "El profesor Tic Tac ya no responde por lo que digan.",
    ),
    "texto_roto": (
        "Chat sin filtro. La conversación ya estaba rota; ahora está peor.",
        "Cuidado con lo que escriben: alguien lo va a leer en voz alta.",
    ),
    "franela_fest": (
        "Modo sin filtro: la camiseta puede ser tan incómoda como su historial.",
        "Dibujen lo que quieran. La demanda es parte del juego.",
    ),
    "el_tasador": (
        "Modo sin filtro. El valor de este objeto depende de su cara de poker.",
        "La subasta ahora permite pujar con la dignidad.",
    ),
}


# ---------------------------------------------------------------------------
# Frases de lobby / inicio por juego (casting nuevo, una voz por juego)
# ---------------------------------------------------------------------------

HOST_EXTRA: dict[str, dict[str, tuple[str, ...]]] = {
    "ultima_palabra": {
        "lobby": (
            "Bienvenidos a La Última Palabra. Tomen asiento, que la vergüenza es compartida.",
            "La sala está abierta. Inviten a alguien que tenga muchas opiniones y pocos filtros.",
            "Ya llegaron. Ahora solo falta que alguien diga algo memorable.",
        ),
        "start": (
            "Tres, dos, uno: que empiece el ingenio.",
            "La primera frase ya viene. Saquen su mejor respuesta y su peor intención.",
            "Empieza La Última Palabra. Que gane el que haga reír más fuerte.",
        ),
    },
    "vox_populi": {
        "lobby": (
            "Bienvenidos a Vox Populi. Acomódense, que la gente ya empezó a opinar.",
            "La sala está abierta. Aquí la verdad la decide la mayoría.",
            "Ya están aquí. La calle tiene cosas que decir y ustedes van a adivinarlas.",
        ),
        "start": (
            "La encuesta está por comenzar. Saquen su lado más popular.",
            "Primera pregunta en camino. Piensen como la gente, no como ustedes.",
            "Empieza Vox Populi. Que la mayoría los acompañe.",
        ),
    },
    "fuera_de_epoca": {
        "lobby": (
            "Bienvenidos a Fuera de Época. Ajusten sus relojes y su memoria.",
            "La máquina del tiempo está caliente. Acomódense antes de que se enfríe.",
            "Ya están aquí. La historia los estaba esperando, aunque no se acuerde de ustedes.",
        ),
        "start": (
            "El tiempo empieza a correr. Elijan bien.",
            "Primer evento en pantalla. Si dudan, piensen en el año más viejo.",
            "Empieza Fuera de Época. La memoria es el único equipaje.",
        ),
    },
    "texto_roto": {
        "lobby": (
            "Bienvenidos a Texto Roto. El chat está abierto y la dignidad, en pausa.",
            "La conversación está por empezar. Nadie sabe quién la va a arruinar primero.",
            "Ya están aquí. El chat los estaba esperando con los brazos abiertos y el juicio listo.",
        ),
        "start": (
            "La conversación está por empezar. Escriban con cuidado.",
            "Primera línea en camino. Puede ser genial o un desastre; ambas cuentan.",
            "Empieza Texto Roto. Que el chat les tenga paciencia.",
        ),
    },
    "franela_fest": {
        "lobby": (
            "Bienvenidos a Franela Fest. Saquen sus crayones y su mejor mal gusto.",
            "La pasarela está lista. La dignidad se queda en el vestidor.",
            "Ya están aquí. La industria textil no sabe lo que le espera.",
        ),
        "start": (
            "El lienzo está listo. A diseñar.",
            "Primera camiseta en camino. El eslogan también cuenta.",
            "Empieza Franela Fest. Que gane el diseño más difícil de explicar.",
        ),
    },
    "el_tasador": {
        "lobby": (
            "Bienvenidos a El Tasador. Todo tiene precio, incluso esa cara.",
            "La subasta está abierta. Acomódense y saquen su mejor cara de poker.",
            "Ya están aquí. El mercado del absurdo les da la bienvenida.",
        ),
        "start": (
            "La subasta está por abrir. Preparen la cartera.",
            "Primer objeto en camino. Convénzanme de que vale una fortuna.",
            "Empieza El Tasador. El valor está en el ojo del que puja.",
        ),
    },
}


# ---------------------------------------------------------------------------
# Variantes adicionales de voz (2026-11-02)
#
# Cada momento tenía 3 líneas. Estas dos extra se suman a las existentes para
# que la tele no repita la misma toma en partidas seguidas. El servidor elige
# una variante determinista por sala/ronda y manda el nombre del audio; el
# generador `tools/generate_fiesta_voice_v2.py` produce una toma por variante.
# ---------------------------------------------------------------------------

HOST_VARIANTS_EXTRA: dict[str, dict[str, tuple[str, ...]]] = {
    "ultima_palabra": {
        "lobby": (
            "Ya están todos. Ahora solo falta que alguien diga algo que no tenga que borrar.",
            "La Última Palabra abre con sala llena y dignidad en peligro.",
        ),
        "start": (
            "Que ruede la primera frase. Y que Dios se apiade del jurado.",
            "Arrancamos. La tele pregunta, ustedes se exhiben.",
        ),
        "write": (
            "Tienen una frase y cero excusas. Sorpréndame.",
            "No piensen demasiado: cuando piensan, ganan los aburridos.",
        ),
        "vote": (
            "Voten con el hígado. Es lo más honesto que tienen.",
            "La respuesta que más les duela es probablemente la correcta.",
        ),
        "reveal": (
            "Aquí viene el conteo. Respiren, que todavía hay esperanza.",
            "La sala decidió. Yo solo leo el veredicto y juzgo en silencio.",
        ),
        "podium": (
            "Se acabó. El campeón se lleva el trofeo; ustedes, la anécdota.",
            "Fin de la última palabra. La próxima vez, escriban más fuerte.",
        ),
    },
    "vox_populi": {
        "lobby": (
            "La sala está abierta. La calle ya tiene temas pendientes.",
            "Bienvenidos al noticiero donde la mayoría siempre tiene la última palabra.",
        ),
        "start": (
            "Primera encuesta al aire. Piensen como la gente, no como sus terapeutas.",
            "Arranca Vox Populi. La verdad es lo que diga el grupo de WhatsApp.",
        ),
        "guess": (
            "Escriban lo que creen que dijo la mayoría. La originalidad no da puntos.",
            "La respuesta más obvia suele ganar. No se esfuercen de más.",
        ),
        "reveal": (
            "La gente habló. Y la gente no consultó ninguna fuente.",
            "Aquí está el ranking de la opinión pública. Que Dios nos ampare.",
        ),
        "podium": (
            "Terminó la encuesta. El ganador se lleva el rating y la responsabilidad.",
            "El pueblo habló. El pueblo se desconecta.",
        ),
    },
    "fuera_de_epoca": {
        "lobby": (
            "Ajusten el reloj y la memoria. La historia no perdona.",
            "La máquina está caliente. Acomódense antes de que los mande a 1987.",
        ),
        "start": (
            "El primer salto está listo. Elijan el año y no miren atrás.",
            "Arranca el viaje. La línea temporal ya se está quejando.",
        ),
        "guess": (
            "Elijan un año. Si dudan, piensen en el más viejo: nunca falla tanto.",
            "La historia es un examen sin apuntes. Suerte.",
        ),
        "reveal": (
            "El año correcto aparece en pantalla. No pongan esa cara.",
            "La línea temporal se revela. Y con ella, sus lagunas.",
        ),
        "podium": (
            "Fin del viaje. El mejor crononauta se lleva la gloria; los demás, el vértigo.",
            "El pasado queda atrás. El marcador, no.",
        ),
    },
    "texto_roto": {
        "lobby": (
            "El chat está abierto. La dignidad sigue en pausa.",
            "Bienvenidos a Texto Roto: la conversación que nadie pidió y todos van a leer.",
        ),
        "start": (
            "Primer mensaje en camino. Que nadie diga nada de lo que se arrepienta… todavía.",
            "Arranca el chat. La Máquina ya está tomando notas.",
        ),
        "write": (
            "Escriban su línea. Corta, larga, brillante o denunciable.",
            "Una línea por persona. La Máquina no acepta puntos suspensivos cobardes.",
        ),
        "vote": (
            "Voten la línea que más les dolió de tanto reír.",
            "El chat ya está armado. Ahora elijan a su culpable favorito.",
            "La sala está a punto de decidir quién arruinó el chat. Voten con honestidad o con rencor.",
        ),
        "reveal": (
            "Así quedó la conversación. Si necesitan terapia, es comprensible.",
            "El chat habló. Y el chat no tiene abogado.",
        ),
        "podium": (
            "Fin del chat. Cierren la app y abracen a alguien antes de que sea tarde.",
            "Tenemos ganador. La conversación nunca volverá a ser la misma.",
        ),
    },
    "franela_fest": {
        "lobby": (
            "La pasarela está abierta. La moda, también. El buen gusto, no.",
            "Bienvenidos a Franela Fest: tela, eslogan y vergüenza en partes iguales.",
        ),
        "start": (
            "Primer lienzo en camino. Dibujen como si nadie los viera.",
            "Arranca el desfile. El Crítico ya está afilando la pluma.",
        ),
        "create": (
            "Tienen un lienzo y muy poco tiempo. El resto es valentía.",
            "Dibujen algo que se pondrían a las 3 a.m. y nadie les preguntaría por qué.",
        ),
        "vote": (
            "Voten la camiseta que se pondrían o la que regalarían por venganza.",
            "La pasarela está llena. Elijan su favorita antes de que el jurado se arrepienta.",
            "La pasarela está a punto de cerrar. Elijan la camiseta que merece sobrevivir.",
        ),
        "reveal": (
            "El desfile terminó. La industria textil pide un respiro.",
            "Resultados. Alguien tendrá que explicar ese dibujo a su familia.",
        ),
        "podium": (
            "Tenemos diseñador ganador. Y varias demandas pendientes.",
            "Fin de la temporada. La pasarela se apaga; la vergüenza queda.",
            "Se apaga la pasarela. El ganador se lleva la fama; los demás, la talla equivocada.",
        ),
    },
    "el_tasador": {
        "lobby": (
            "La casa de subastas abre sus puertas. La cartera, también.",
            "Bienvenidos a El Tasador: objetos raros, precios inventados y compradores peores.",
        ),
        "start": (
            "Primer lote en camino. Convénzanme de que vale algo.",
            "Arranca la subasta. El martillo ya tiene hambre.",
        ),
        "pitch": (
            "Escriban su pitch y su precio. La honestidad no sube el valor.",
            "Vendan humo con seguridad. El Tasador huele el miedo.",
        ),
        "vote": (
            "Voten el pitch más convincente. O el más ridículo, ustedes deciden.",
            "El jurado evalúa la historia, no el objeto. Como en la vida.",
            "El martillo está en el aire. Voten al vendedor que casi les hizo creer.",
        ),
        "reveal": (
            "El precio real aparece. Spoiler: nadie acertó.",
            "El martillo habló. La decepción también.",
        ),
        "podium": (
            "Tenemos al mejor tasador. El objeto vuelve a su caja.",
            "Fin de la subasta. La próxima vez traigan algo menos maldito.",
        ),
    },
}


def host_lines(game: str, moment: str) -> tuple[str, ...]:
    """Todas las tomas disponibles para un momento, sin duplicar."""
    base = (
        HOST_EXTRA.get(game, {}).get(moment)
        or HOST_LINES.get(game, {}).get(moment)
        or HOST_LINES[game]["intro"]
    )
    extra = HOST_VARIANTS_EXTRA.get(game, {}).get(moment, ())
    out: list[str] = []
    for line in tuple(base) + tuple(extra):
        text = str(line).strip()
        if text and text not in out:
            out.append(text)
    return tuple(out)


# ---------------------------------------------------------------------------
# Fiesta 4.0 — soporte de los bucles nuevos
# ---------------------------------------------------------------------------

# Respuestas del bot de Nora cuando falta un jugador en un duelo.
BOT_ANSWERS: tuple[str, ...] = (
    "Una respuesta que Nora ya tenía preparada desde el ensayo.",
    "Algo sobre tacos, pero dicho con demasiada seguridad.",
    "La verdad, pero dicha como si fuera un chisme.",
    "Un chiste que solo funciona si lo lee una máquina.",
    "Una frase que suena profunda hasta que la piensas dos segundos.",
    "El nombre de un canal que ya no existe.",
    "Una disculpa preventiva por lo que viene después.",
)

# Directivas secretas para los escritores del chat.
SECRET_GOALS: tuple[str, ...] = (
    "Termina tu frase con una pregunta incómoda.",
    "Actúa como si estuvieras escondiendo un cadáver.",
    "Usa jerga de señor de 60 años.",
    "Menciona a un ex sin decir su nombre.",
    "Haz que suene a mensaje de voz transcrito.",
    "Empieza con «no es por nada, pero…».",
    "Presume algo que claramente no hiciste.",
    "Habla como si estuvieras en una reunión de trabajo.",
    "Incluye una cifra inventada y defiéndela.",
    "Responde como si te hubieran culpado injustamente.",
    "Usa una sola palabra en inglés para parecer importante.",
    "Haz una amenaza muy educada.",
    "Menciona comida como si fuera una tragedia.",
    "Termina con «y ya no digo más».",
    "Exagera un problema doméstico como si fuera mundial.",
)

# Dibujos vectoriales de reserva (trazos normalizados 0..1000).
FRANELA_FALLBACK_DRAWINGS: tuple[dict, ...] = (
    {
        "id": "fallback_star",
        "ownerId": "bot:franela",
        "ownerName": "El Taller",
        "strokes": [
            {"color": "#F472B6", "width": 9, "points": [[500, 120], [590, 380], [860, 380], [640, 540], [720, 800], [500, 640], [280, 800], [360, 540], [140, 380], [410, 380], [500, 120]]},
        ],
    },
    {
        "id": "fallback_bolt",
        "ownerId": "bot:franela",
        "ownerName": "El Taller",
        "strokes": [
            {"color": "#FBBF24", "width": 11, "points": [[560, 90], [300, 540], [490, 540], [420, 900], [720, 430], [520, 430], [560, 90]]},
        ],
    },
)

FRANELA_FALLBACK_SLOGANS: tuple[dict, ...] = (
    {"id": "fallback_s1", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "No es ropa, es una advertencia."},
    {"id": "fallback_s2", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Mi mamá dice que es arte."},
    {"id": "fallback_s3", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Se lava a mano, se usa a escondidas."},
    {"id": "fallback_s4", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Diseñado en una sobremesa."},
)

# ---------------------------------------------------------------------------
# Fase 4 · Expansión de contenido (Vision+ Fiesta 4.0)
# Se suma al final para no tocar las listas históricas ni sus pruebas.
# ---------------------------------------------------------------------------

VOX_QUESTIONS += (
    {
        "text": "¿Qué es lo primero que haces cuando suena la alarma?",
        "answers": [
            ("apagarla", 392), ("revisar el celular", 301), ("volver a dormir", 244),
            ("levantarme", 178), ("gritar", 112), ("poner otra alarma", 63),
        ],
    },
    {
        "text": "¿Cuál es la peor cosa que puede pasar en una videollamada?",
        "answers": [
            ("que se vea mi cuarto", 387), ("que se escuche a mi mamá", 314),
            ("quedarme sin internet", 251), ("hablar con el micrófono cerrado", 176),
            ("que entre la mascota", 108), ("que se me olvide la reunión", 59),
        ],
    },
    {
        "text": "¿Qué pides cuando no sabes qué pedir?",
        "answers": [
            ("tacos", 401), ("pizza", 322), ("hamburguesa", 246), ("sushi", 169),
            ("pollo frito", 104), ("lo mismo de siempre", 58),
        ],
    },
    {
        "text": "¿Cuál es el mejor invento de la humanidad?",
        "answers": [
            ("el internet", 374), ("la comida a domicilio", 309), ("el aire acondicionado", 238),
            ("los audífonos", 171), ("el papel higiénico", 103), ("la siesta", 61),
        ],
    },
    {
        "text": "¿Qué hace más ruido en una casa mexicana?",
        "answers": [
            ("la licuadora", 396), ("la tele", 318), ("la música", 241), ("los vecinos", 173),
            ("la olla express", 109), ("el perro", 57),
        ],
    },
    {
        "text": "¿Cuál es el peor regalo para un cumpleaños?",
        "answers": [
            ("una taza", 381), ("calcetines", 306), ("un adorno", 234), ("una vela", 167),
            ("un libro de autoayuda", 107), ("una planta muerta", 62),
        ],
    },
    {
        "text": "¿Qué haces cuando alguien te cuenta un chisme?",
        "answers": [
            ("escucho todo", 389), ("digo «no puede ser»", 311), ("lo repito", 239),
            ("finjo sorpresa", 170), ("cambio de tema", 101), ("lo grabo", 58),
        ],
    },
    {
        "text": "¿Cuál es el peor olor del mundo?",
        "answers": [
            ("basura vieja", 372), ("huevo podrido", 303), ("sudor de gym", 236),
            ("leche cortada", 164), ("zapato mojado", 102), ("perro mojado", 56),
        ],
    },
    {
        "text": "¿Qué app necesita una versión para gente mayor?",
        "answers": [
            ("TikTok", 384), ("WhatsApp", 315), ("Uber", 243), ("Spotify", 171),
            ("Tinder", 106), ("la del banco", 61),
        ],
    },
    {
        "text": "¿Cuál es la mejor excusa para llegar tarde?",
        "answers": [
            ("había tráfico", 397), ("se me fue el camión", 307), ("me quedé dormido", 238),
            ("no encontré estacionamiento", 169), ("se me rompió el celular", 103), ("estaba lloviendo", 55),
        ],
    },
    {
        "text": "¿Qué animal sería el peor presidente?",
        "answers": [
            ("gato", 391), ("mapache", 302), ("mono", 235), ("perro", 166),
            ("loro", 105), ("hámster", 56),
        ],
    },
    {
        "text": "¿Qué canción te da vergüenza cantar en voz alta?",
        "answers": [
            ("una de telenovela", 376), ("una de reguetón viejo", 309), ("una de karaoke", 237),
            ("una de boda", 168), ("una de Disney", 104), ("una de anuncio", 57),
        ],
    },
    {
        "text": "¿Cuál es la peor moda que regresó?",
        "answers": [
            ("pantalones de campana", 388), ("crocs con calcetines", 304), ("lentes diminutos", 231),
            ("gorras planas", 165), ("chalecos de mezclilla", 103), ("cejas delgadas", 58),
        ],
    },
    {
        "text": "¿Qué es lo más difícil de ser adulto?",
        "answers": [
            ("pagar cosas", 403), ("decidir qué comer", 312), ("madrugar", 236),
            ("llamar al banco", 167), ("ir al doctor", 101), ("tener amigos", 55),
        ],
    },
    {
        "text": "¿Qué harías con un día libre sin internet?",
        "answers": [
            ("dormir", 394), ("leer", 306), ("limpiar", 232), ("cocinar", 164),
            ("salir", 106), ("no sé qué haría", 57),
        ],
    },
    {
        "text": "¿Cuál es la peor frase para romper el hielo?",
        "answers": [
            ("¿tienes wifi?", 382), ("¿y tú qué tal?", 301), ("¿vienes seguido?", 234),
            ("te pareces a mi ex", 168), ("¿me prestas dinero?", 104), ("¿crees en el amor?", 57),
        ],
    },
    {
        "text": "¿Qué comida no debería existir en versión light?",
        "answers": [
            ("pizza", 375), ("tacos", 304), ("pastel", 238), ("helado", 171),
            ("papas fritas", 105), ("refresco", 58),
        ],
    },
    {
        "text": "¿Cuál es el mejor lugar para esconderse de una reunión?",
        "answers": [
            ("el baño", 408), ("la cocina", 314), ("el estacionamiento", 226),
            ("la azotea", 158), ("otra oficina", 98), ("debajo del escritorio", 54),
        ],
    },
    {
        "text": "¿Qué personaje de caricatura sería tu jefe?",
        "answers": [
            ("Homero Simpson", 371), ("Don Cangrejo", 302), ("Piolín", 236),
            ("Scooby Doo", 166), ("Bob Esponja", 104), ("El Grinch", 56),
        ],
    },
    {
        "text": "¿Cuál es el peor consejo que te dio tu abuela?",
        "answers": [
            ("cásate joven", 363), ("échale más sal", 301), ("no uses el celular", 239),
            ("todo se arregla con té", 169), ("aguanta", 103), ("reza y ya", 58),
        ],
    },
)

TIME_EVENTS += (
    {"event": "Se inaugura el Estadio Azteca", "year": 1966},
    {"event": "México organiza los Juegos Olímpicos de la Ciudad de México", "year": 1968},
    {"event": "Se inaugura el Metro de la Ciudad de México", "year": 1969},
    {"event": "Se estrena El Chavo del 8", "year": 1971},
    {"event": "Se funda Televisa", "year": 1973},
    {"event": "México organiza su segundo Mundial de Futbol", "year": 1986},
    {"event": "Se estrena la telenovela María la del Barrio", "year": 1995},
    {"event": "Se estrena Amores Perros", "year": 2000},
    {"event": "Se estrena Y tu mamá también", "year": 2001},
    {"event": "Se estrena la telenovela Rebelde", "year": 2004},
    {"event": "Se estrena El laberinto del fauno", "year": 2006},
    {"event": "Se estrena la serie La Rosa de Guadalupe", "year": 2008},
    {"event": "Se estrena la serie El Señor de los Cielos", "year": 2013},
    {"event": "Se estrena la primera temporada de Club de Cuervos", "year": 2015},
    {"event": "Se estrena la película Coco", "year": 2017},
    {"event": "Se estrena Roma de Alfonso Cuarón", "year": 2018},
    {"event": "Se lanza la primera temporada de La Casa de las Flores", "year": 2018},
    {"event": "Se estrena la película Parasite", "year": 2019},
    {"event": "Se estrena la primera temporada de Wednesday", "year": 2022},
    {"event": "Se estrena la película Barbie", "year": 2023},
)

TEXTO_ROTO_SCENES += (
    {
        "title": "Aerolínea de bajo costo",
        "roles": ["pasajero", "agente", "piloto", "la maleta perdida"],
        "seed": "Agente: «Su vuelo sale a las 4:00». Pasajero: «...».",
    },
    {
        "title": "Mercado de barrio",
        "roles": ["vendedor", "cliente", "vecino", "el que da cambio"],
        "seed": "Vendedor: «Lleva dos y le sale más barato». Cliente: «...».",
    },
    {
        "title": "Clase de inglés",
        "roles": ["maestro", "alumno nuevo", "alumna aplicada", "el que solo dice yes"],
        "seed": "Maestro: «How are you?». Alumno nuevo: «...».",
    },
    {
        "title": "Grupo de vecinos",
        "roles": ["administrador", "vecino 1", "vecino 2", "el del tercer piso"],
        "seed": "Administrador: «El elevador volvió a fallar». Vecino 1: «...».",
    },
    {
        "title": "Taquería a las 3 a.m.",
        "roles": ["taquero", "cliente", "amigo", "el que pide sin cebolla"],
        "seed": "Taquero: «¿Con todo?». Cliente: «...».",
    },
    {
        "title": "Videollamada con la abuela",
        "roles": ["abuela", "nieto", "tía", "el primo que no sabe usar la cámara"],
        "seed": "Abuela: «¿Ya me ves?». Nieto: «...».",
    },
    {
        "title": "Entrevista para influencer",
        "roles": ["reclutador", "influencer", "mánager", "las views"],
        "seed": "Reclutador: «¿Cuál es tu nicho?». Influencer: «...».",
    },
    {
        "title": "Farmacia de guardia",
        "roles": ["farmacéutico", "cliente", "doctor", "el que solo quería curitas"],
        "seed": "Farmacéutico: «¿Tiene receta?». Cliente: «...».",
    },
    {
        "title": "Gym en enero",
        "roles": ["entrenador", "novato", "veterano", "la caminadora"],
        "seed": "Entrenador: «¿Calentamos?». Novato: «...».",
    },
    {
        "title": "Viaje en Uber",
        "roles": ["conductor", "pasajero", "GPS", "el amigo que va atrás"],
        "seed": "Conductor: «¿Va por la misma ruta?». Pasajero: «...».",
    },
    {
        "title": "Junta de padres",
        "roles": ["maestra", "mamá", "papá", "el niño que no debía estar"],
        "seed": "Maestra: «Su hijo es muy participativo». Mamá: «...».",
    },
    {
        "title": "Concierto agotado",
        "roles": ["fan", "revendedor", "guardia", "el que canta desafinado"],
        "seed": "Guardia: «No hay acceso». Fan: «...».",
    },
    {
        "title": "Biblioteca pública",
        "roles": ["bibliotecario", "estudiante", "señor mayor", "el que habla por teléfono"],
        "seed": "Bibliotecario: «Aquí no se puede hablar». Estudiante: «...».",
    },
    {
        "title": "Fiesta de fin de año",
        "roles": ["anfitrión", "invitado", "vecino", "el que trajo uvas"],
        "seed": "Anfitrión: «¡Faltan 10 segundos!». Invitado: «...».",
    },
    {
        "title": "Chat de fans del streamer",
        "roles": ["streamer", "moderador", "fan", "la mamá del streamer"],
        "seed": "Streamer: «¡Gracias por el sub!». Mamá: «...».",
    },
)

FRANELA_PROMPTS += (
    {
        "prompt": "Diseña la camiseta de una taquería que solo abre a las 3 a.m.",
        "slogans": ["Todo tiene un precio", "Sin cebolla, sin juicio", "La noche es un taco"],
    },
    {
        "prompt": "Diseña la camiseta de una app para encontrar estacionamiento.",
        "slogans": ["Casi lo logras", "Buscar es gratis", "Un lugar en el mundo"],
    },
    {
        "prompt": "Diseña la camiseta de una banda de rock con licuadoras.",
        "slogans": ["Batido y distorsión", "Rock a máxima potencia", "Se rompió el amplificador"],
    },
    {
        "prompt": "Diseña la camiseta de una escuela para adultos que no saben dormir.",
        "slogans": ["La siesta es un examen", "Ojos abiertos, mente ausente", "Reprobé descanso"],
    },
    {
        "prompt": "Diseña la camiseta de un superhéroe que solo rescata carritos de supermercado.",
        "slogans": ["Vuélvelo a su lugar", "Salvemos el carrito", "Justicia en la fila"],
    },
    {
        "prompt": "Diseña la camiseta de una serie sobre un grupo de WhatsApp familiar.",
        "slogans": ["Bendiciones a todos", "El primo manda cadenas", "Silenciado, no olvidado"],
    },
    {
        "prompt": "Diseña la camiseta de una telenovela sobre el wifi caído.",
        "slogans": ["Señal del corazón", "Reinicia y ama", "Sin conexión, sin ti"],
    },
    {
        "prompt": "Diseña la camiseta de un restaurante que solo sirve postres.",
        "slogans": ["La vida es el postre", "Sin plato fuerte", "Todo cuenta como desayuno"],
    },
    {
        "prompt": "Diseña la camiseta de una agencia de viajes para gente que odia viajar.",
        "slogans": ["Quédate en casa", "Pasaporte a la cama", "Destino: sofá"],
    },
    {
        "prompt": "Diseña la camiseta de una película de terror sobre el WhatsApp de la familia.",
        "slogans": ["El grupo te vio", "Cadena mortal", "Silencia y reza"],
    },
    {
        "prompt": "Diseña la camiseta de un equipo de futbol de oficina.",
        "slogans": ["Ganamos o mandamos correo", "La reunión es a las 9", "Juega con la corbata"],
    },
    {
        "prompt": "Diseña la camiseta de un museo de objetos perdidos.",
        "slogans": ["Nada se pierde, todo se exhibe", "Encontré tu dignidad", "Sala de espera eterna"],
    },
    {
        "prompt": "Diseña la camiseta de un club de lectura que nunca lee.",
        "slogans": ["Leer es opcional", "Se comenta en el chat", "La portada estuvo increíble"],
    },
    {
        "prompt": "Diseña la camiseta de una farmacia que vende milagros.",
        "slogans": ["Una pastilla para todo", "Consulte a su doctor", "Milagro en 24 horas"],
    },
    {
        "prompt": "Diseña la camiseta de un concurso de excusas.",
        "slogans": ["No pude, pero casi", "El tráfico me conoce", "Mi perro se comió el plan"],
    },
)

TASADOR_OBJECTS += (
    {"name": "Despertador que insiste en que ya es martes", "description": "La semana empieza cuando él quiere. Incluye crisis vital.", "value": 880},
    {"name": "Silla para meditar sobre tus decisiones", "description": "Cómoda, silenciosa y con un leve olor a arrepentimiento.", "value": 1320},
    {"name": "Taza que se enfría antes que tu café", "description": "Tecnología del futuro para ofrecerte una decepción inmediata.", "value": 460},
    {"name": "Mapa de la ciudad donde no existe tu casa", "description": "Cartografía emocional. Igual te pierdes, pero con estilo.", "value": 1500},
    {"name": "Cámara que solo retrata tus adeudos", "description": "Fotografía de alta resolución de la tarjeta de crédito. Sonríe.", "value": 2100},
    {"name": "Sillón con cinturón de seguridad para series", "description": "El único lugar donde un grito de gol no tira el refresco.", "value": 1750},
    {"name": "Gafas para ver el lado positivo del tráfico", "description": "Filtro color rosa. No incluye paciencia.", "value": 690},
    {"name": "Caja de suscripción de aire de oficina", "description": "Cada mes, una nueva mezcla de café, estrés y fotocopias.", "value": 380},
    {"name": "Botella térmica que guarda chismes", "description": "Mantiene la temperatura y las versiones contradictorias.", "value": 980},
    {"name": "Almohada que escucha tus planes de ahorro", "description": "Suave, comprensiva y probablemente de acuerdo en que mañana empiezas.", "value": 820},
    {"name": "Colección de sombrillas rotas por el viento", "description": "Arte urbano. Cinco piezas, siete tragedias", "value": 1250},
    {"name": "Menú de restaurante con letra diminuta", "description": "Los precios son legibles; las consecuencias, no.", "value": 540},
    {"name": "Zapato izquierdo de un par desaparecido", "description": "Excelente pieza para quien cree que todas las historias merecen un final.", "value": 720},
    {"name": "Cuaderno de metas cumplidas en sueños", "description": "Páginas llenas de tareas que claramente hiciste en otra vida.", "value": 1430},
    {"name": "Reloj que solo avanza los viernes", "description": "La semana duró seis días; el fin de semana duró un parpadeo.", "value": 1670},
)

SECRET_GOALS += (
    "Incluye una disculpa que nadie pidió.",
    "Habla como si estuvieras vendiendo una parcela en la Luna.",
    "Menciona un color y defiéndelo como una opinión política.",
    "Responde con una pregunta que acuse a todos.",
    "Usa tres puntos suspensivos… y no expliques nada.",
    "Presume un talento completamente inútil.",
)

FRANELA_FALLBACK_SLOGANS += (
    {"id": "fallback_s5", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Bonito desde lejos."},
    {"id": "fallback_s6", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Pregunta por el precio después."},
    {"id": "fallback_s7", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Esto fue una decisión."},
    {"id": "fallback_s8", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Lavar a mano la autoestima."},
    {"id": "fallback_s9", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Moda para valientes."},
    {"id": "fallback_s10", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "No es un error, es concepto."},
    {"id": "fallback_s11", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Se usa con confianza."},
    {"id": "fallback_s12", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Lo pensé demasiado."},
    {"id": "fallback_s13", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "La tendencia me persigue."},
    {"id": "fallback_s14", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "No combina, pero funciona."},
    {"id": "fallback_s15", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Hecho con capas y enojo."},
    {"id": "fallback_s16", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Mi mejor idea de la madrugada."},
    {"id": "fallback_s17", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Esto merece una pasarela."},
    {"id": "fallback_s18", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Con un toque de caos."},
    {"id": "fallback_s19", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "Úsalo antes de arrepentirte."},
    {"id": "fallback_s20", "ownerId": "bot:franela", "ownerName": "El Taller", "text": "El arte no se explica."},
)
