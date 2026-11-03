"""Mazos locales de Vision+ Play — sin base de datos y sin depender del catálogo.

Todo el contenido de los juegos de fiesta vive aquí: lugares del impostor,
consignas de dibujo, cartas de subasta y datos insólitos de Fibbage. Se
barajan por sala con una semilla estable para que una revancha no repita la
misma carta y para que el servidor sea siempre el árbitro.
"""

from __future__ import annotations

import random
from typing import Any

# ---------------------------------------------------------------------------
# El Impostor / El Espía Infiltrado
# ---------------------------------------------------------------------------

IMPOSTOR_QUESTIONS = (
    "¿Qué ropa llevarías a este lugar?",
    "¿Qué olor tiene al entrar?",
    "¿Qué canción suena de fondo?",
    "¿Qué objeto nunca falta?",
    "¿Qué personaje de cine aparecería aquí?",
    "¿Qué comida pedirías?",
    "¿Qué regla jamás romperías?",
    "¿Qué delito se comete en silencio?",
    "¿Qué pregunta incómoda harías?",
    "¿Cómo se sale de aquí sin pagar?",
)

IMPOSTOR_WORDS: tuple[dict[str, str], ...] = (
    {"word": "Aeropuerto", "category": "Lugares de cine", "hint": "Maletas, retrasos y despedidas."},
    {"word": "Hotel abandonado", "category": "Lugares de cine", "hint": "Pasillos largos y una llave que no abre."},
    {"word": "Nave espacial", "category": "Lugares de cine", "hint": "Silencio, luces de emergencia y un traje."},
    {"word": "Submarino", "category": "Lugares de cine", "hint": "Metal, presión y un sonar que no calla."},
    {"word": "Castillo", "category": "Lugares de cine", "hint": "Piedra fría, retratos y una puerta secreta."},
    {"word": "Desierto", "category": "Lugares de cine", "hint": "Sol, sed y un horizonte que no termina."},
    {"word": "Selva", "category": "Lugares de cine", "hint": "Humedad, ruidos y algo que te observa."},
    {"word": "Estación de tren", "category": "Lugares de cine", "hint": "Andenes, relojes y una despedida."},
    {"word": "Museo", "category": "Lugares de cine", "hint": "Vitrinas, guardias y una obra que no debe moverse."},
    {"word": "Prisión", "category": "Lugares de cine", "hint": "Rejas, patio y un plan que se susurra."},
    {"word": "Faro", "category": "Lugares de cine", "hint": "Mar, viento y una luz que gira toda la noche."},
    {"word": "Isla", "category": "Lugares de cine", "hint": "Arena, secretos y un barco que no llega."},
    {"word": "Estudio de televisión", "category": "Lugares de cine", "hint": "Cámaras, luces y un cartel de aplausos."},
    {"word": "Banco", "category": "Lugares de cine", "hint": "Cámaras, dinero y una alarma que espera."},
    {"word": "Hospital", "category": "Lugares de cine", "hint": "Pasillos, monitores y un secreto."},
    {"word": "Escuela", "category": "Lugares de cine", "hint": "Pizarras, pasillos y un timbre."},
    {"word": "Teatro", "category": "Lugares de cine", "hint": "Telón, butacas y una función que no debe parar."},
    {"word": "Mercado nocturno", "category": "Lugares de cine", "hint": "Luces, voces y algo que se vende bajo la mesa."},
    {"word": "Laboratorio", "category": "Lugares de cine", "hint": "Batas, probetas y una puerta con clave."},
    {"word": "Mansión", "category": "Lugares de cine", "hint": "Mármol, mayordomo y un cuarto cerrado."},
    {"word": "Tren de lujo", "category": "Lugares de cine", "hint": "Vagones, copas y un asesinato."},
    {"word": "Bar de carretera", "category": "Lugares de cine", "hint": "Neón, café y un desconocido."},
    {"word": "Biblioteca", "category": "Lugares de cine", "hint": "Silencio, polvo y un libro prohibido."},
    {"word": "Cine de pueblo", "category": "Lugares de cine", "hint": "Cartel viejo, palomitas y una pantalla enorme."},
    {"word": "Gasolinera", "category": "Lugares de cine", "hint": "Bombas, sol y una cámara que no funciona."},
    {"word": "Azotea", "category": "Lugares de cine", "hint": "Aire, antenas y una decisión."},
    {"word": "Puerto", "category": "Lugares de cine", "hint": "Contenedores, grúas y un barco que zarpa."},
    {"word": "Templo", "category": "Lugares de cine", "hint": "Velas, eco y una reliquia."},
    {"word": "Parque de diversiones", "category": "Lugares de cine", "hint": "Luces, gritos y una rueda que gira."},
    {"word": "Metro", "category": "Lugares de cine", "hint": "Túneles, anuncios y un vagón vacío."},
    {"word": "Búnker", "category": "Lugares de cine", "hint": "Hormigón, provisiones y una cuenta regresiva."},
    {"word": "Cabaret", "category": "Lugares de cine", "hint": "Lentejuelas, humo y una canción."},
    {"word": "Granja", "category": "Lugares de cine", "hint": "Tierra, animales y un silo."},
    {"word": "Observatorio", "category": "Lugares de cine", "hint": "Cúpula, estrellas y un aviso."},
    {"word": "Set de rodaje", "category": "Lugares de cine", "hint": "Luces, claqueta y alguien que grita «acción»."},
    {"word": "Carnaval", "category": "Lugares de cine", "hint": "Máscaras, música y una cara conocida."},
    {"word": "Embajada", "category": "Lugares de cine", "hint": "Banderas, pasaportes y un favor."},
    {"word": "Casino", "category": "Lugares de cine", "hint": "Fichas, espejos y una racha."},
    {"word": "Cementerio", "category": "Lugares de cine", "hint": "Lápidas, niebla y un nombre."},
    {"word": "Estación polar", "category": "Lugares de cine", "hint": "Hielo, generador y algo bajo la nieve."},
    {"word": "Sala de cine en casa", "category": "Lugares de cine", "hint": "Sofá, mantas y el botón de pausa."},
    {"word": "Backstage", "category": "Lugares de cine", "hint": "Cables, prisa y un vestuario."},
)

# ---------------------------------------------------------------------------
# Duelo de Dibujo Ciego
# ---------------------------------------------------------------------------

DRAWING_PROMPTS: tuple[str, ...] = (
    "Un refrigerador triste en el espacio",
    "Un pulpo que dirige una orquesta",
    "El Titanic llegando a una fiesta",
    "Un vampiro pidiendo tacos",
    "Un dinosaurio usando lentes de sol",
    "Una boda entre un robot y una licuadora",
    "Un detective que interroga a una paloma",
    "Un extraterrestre viendo la televisión",
    "Un fantasma con miedo a la oscuridad",
    "Un superhéroe atrapado en un elevador",
    "Una sirena trabajando en una oficina",
    "Un zombi que hace ejercicio",
    "Un mono pintando un autorretrato",
    "Una bruja con problemas de internet",
    "Un dragón cocinando pasta",
    "Un pingüino en una playa de Cancún",
    "Un robot llorando en una boda",
    "Una pizza que aprende a bailar",
    "Un gato dirigiendo una película de acción",
    "Un astronauta perdido en un supermercado",
    "Una tortuga ganando una carrera de Fórmula 1",
    "Un ogro leyendo poesía",
    "Dos tazas de café enamoradas",
    "Un murciélago con miedo a volar",
    "Una momia despertando tarde",
    "Un perro manejando un taxi",
    "Una nube con hipo",
    "Un caballo usando patines",
    "Un cerebro jugando ajedrez contra sí mismo",
    "Una escoba que se niega a barrer",
    "Un cine donde todos son fantasmas",
    "Un alienígena aprendiendo a saludar",
    "Una princesa con armadura de hojalata",
    "Un pollo que quiere ser estrella de rock",
    "Un esqueleto en una entrevista de trabajo",
    "Unos lentes que ven el futuro",
    "Una televisión que sueña con ser actriz",
    "Un pulpo con ocho celulares",
    "Una montaña rusa en el espacio",
    "Un payaso triste en un banco",
    "Una licuadora que odia las frutas",
    "Un zombi en una pasarela de moda",
    "Un castillo hecho de gelatinas",
    "Un pirata con miedo al agua",
    "Una ballena en un rascacielos",
    "Un superhéroe sin capa",
    "Unos tenis que corren solos",
    "Un árbol que da hamburguesas",
    "Un espejo que copia a la gente",
    "Una estrella de mar en una alfombra roja",
)

# Señuelos de la casa para que una sala de 3 jugadores nunca tenga solo dos
# opciones. Se eligen de forma determinista y nunca coinciden con el prompt real.
DRAWING_DECOYS: tuple[str, ...] = (
    "Un vampiro en una entrevista de trabajo",
    "Una ballena usando sombrero de copa",
    "Un robot barriendo una playa",
    "Un gato con traje de astronauta",
    "Una boda entre dos pizzas",
    "Un fantasma vendiendo seguros",
    "Un dragón con miedo a las alturas",
    "Un pulpo dando una conferencia",
    "Una momia en un gimnasio",
    "Un payaso arreglando una moto",
    "Un pingüino con helado derretido",
    "Un esqueleto tocando la guitarra",
    "Una bruja con batería baja",
    "Un perro dando clases de yoga",
    "Un alienígena en un cine",
    "Un superhéroe planchando su capa",
    "Una tortuga con patineta",
    "Un zombi pidiendo un café",
    "Una sirena en una biblioteca",
    "Un dinosaurio en una fiesta de cumpleaños",
    "Un castillo con ruedas",
    "Un pirata usando celular",
    "Una estrella de mar en un avión",
    "Un cerebro con sombrero de chef",
    "Una escoba con capa de superhéroe",
    "Un espejo que se ríe solo",
    "Un árbol con zapatos",
    "Una montaña rusa en una oficina",
    "Un caballo con lentes de sol",
    "Una nube con corbata",
    "Un cine de palomitas vivas",
    "Unos tenis con hambre",
    "Un pulpo en una boda",
    "Una televisión con miedo al apagón",
    "Un astronauta en una lavandería",
    "Un gato con batuta de director",
    "Una licuadora con banda de rock",
    "Un murciélago en una playa",
    "Un esqueleto en un baile",
    "Una princesa con consola",
    "Un ogro en una tienda de tés",
    "Un pollo con traje de gala",
    "Una tortuga en una carrera de autos",
    "Un zombi en una entrevista",
    "Una bruja con aspiradora",
    "Un perro con lentes de detective",
    "Una pizza con capa",
    "Un robot en una boda",
    "Una nube en un elevador",
    "Un castillo en una ciudad",
)

# ---------------------------------------------------------------------------
# Subasta Ciega / Mercado Negro
# ---------------------------------------------------------------------------

AUCTION_ITEMS: tuple[dict[str, Any], ...] = (
    {
        "id": "sobre-puntos",
        "title": "Sobre de puntos",
        "description": "Un sobre lacrado con puntos de la casa. Nadie sabe cuánto trae hasta abrirlo.",
        "effect": "points", "value": 600, "mystery": False,
    },
    {
        "id": "bolsa-monedas",
        "title": "Bolsa de monedas",
        "description": "Monedas para seguir pujando en las próximas rondas.",
        "effect": "coins", "value": 400, "mystery": False,
    },
    {
        "id": "guion-robado",
        "title": "Guion robado",
        "description": "El guion de una película que aún no se estrena. Vale oro.",
        "effect": "points", "value": 750, "mystery": False,
    },
    {
        "id": "carta-misterio",
        "title": "Carta de misterio",
        "description": "El reverso está en negro. El contenido se revela al ganar.",
        "effect": "mystery", "value": 0, "mystery": True,
    },
    {
        "id": "robo-lider",
        "title": "Manuscrito del líder",
        "description": "Te llevas el 25 % de los puntos del jugador que va arriba en la tabla.",
        "effect": "steal", "value": 500, "mystery": False,
    },
    {
        "id": "palomitas-oro",
        "title": "Palomitas de oro",
        "description": "Crujientes, caras y con puntos escondidos en el fondo.",
        "effect": "points", "value": 350, "mystery": False,
    },
    {
        "id": "butaca-vip",
        "title": "Butaca VIP",
        "description": "Un asiento en primera fila y monedas para la siguiente puja.",
        "effect": "coins", "value": 350, "mystery": False,
    },
    {
        "id": "premio-critica",
        "title": "Premio de la crítica",
        "description": "Una estatuilla que vale muchos puntos, aunque nadie sepa quién la votó.",
        "effect": "points", "value": 900, "mystery": False,
    },
    {
        "id": "contrato-estrella",
        "title": "Contrato de estrella",
        "description": "Firma aquí y te llevas el 25 % de los puntos del líder. El resto que grite.",
        "effect": "steal", "value": 450, "mystery": False,
    },
    {
        "id": "cinta-perdida",
        "title": "Cinta perdida",
        "description": "Una copia única de una película maldita. El valor es una sorpresa.",
        "effect": "mystery", "value": 0, "mystery": True,
    },
    {
        "id": "productor",
        "title": "Productor ejecutivo",
        "description": "Tu nombre en los créditos y una bolsa de monedas.",
        "effect": "coins", "value": 600, "mystery": False,
    },
    {
        "id": "claqueta",
        "title": "Claqueta de oro",
        "description": "Pequeña, brillante y con puntos extra al abrirla.",
        "effect": "points", "value": 250, "mystery": False,
    },
    {
        "id": "traje-gala",
        "title": "Traje de gala",
        "description": "Elegancia instantánea. Los puntos van por dentro.",
        "effect": "points", "value": 300, "mystery": False,
    },
    {
        "id": "palco",
        "title": "Palco privado",
        "description": "La mejor vista de la sala y monedas para seguir jugando.",
        "effect": "coins", "value": 450, "mystery": False,
    },
    {
        "id": "guion-premiado",
        "title": "Guion premiado",
        "description": "El texto que todos quieren dirigir. Puntos altos.",
        "effect": "points", "value": 1000, "mystery": False,
    },
    {
        "id": "sobre-sorpresa",
        "title": "Sobre sorpresa",
        "description": "Sello rojo, contenido secreto. Puede ser gloria o nada.",
        "effect": "mystery", "value": 0, "mystery": True,
    },
    {
        "id": "derechos",
        "title": "Derechos de autor",
        "description": "Cada vez que alguien diga tu frase, cobras. Te llevas el 25 % de los puntos del líder.",
        "effect": "steal", "value": 600, "mystery": False,
    },
    {
        "id": "cameo",
        "title": "Cameo",
        "description": "Tres segundos en pantalla y un puñado de puntos.",
        "effect": "points", "value": 500, "mystery": False,
    },
    {
        "id": "afterparty",
        "title": "After party",
        "description": "La fiesta después del estreno. Monedas y contactos.",
        "effect": "coins", "value": 250, "mystery": False,
    },
    {
        "id": "estatuilla",
        "title": "Estatua sin nombre",
        "description": "Pesa como una estatuilla, brilla como un secreto.",
        "effect": "mystery", "value": 0, "mystery": True,
    },
    {
        "id": "sala-montaje",
        "title": "Sala de montaje",
        "description": "Corta la escena que quieras. Puntos para el ganador.",
        "effect": "points", "value": 850, "mystery": False,
    },
    {
        "id": "fondo-interno",
        "title": "Fondo de inversión",
        "description": "Un maletín de monedas para las siguientes rondas.",
        "effect": "coins", "value": 700, "mystery": False,
    },
    {
        "id": "doble-robo",
        "title": "Doble robo",
        "description": "Un golpe porcentual al líder, con estilo de secuela.",
        "effect": "steal", "value": 700, "mystery": False,
    },
    {
        "id": "critica",
        "title": "Crítica firmada",
        "description": "Una reseña que vale puntos… si alguien la lee.",
        "effect": "points", "value": 400, "mystery": False,
    },
    {
        "id": "doble-siguiente",
        "title": "Doble o nada",
        "description": "Duplica los puntos del próximo lote de puntos que ganes. Se consume al usarlo.",
        "effect": "double_next", "value": 0, "mystery": False,
    },
    {
        "id": "escudo-productor",
        "title": "Escudo de productor",
        "description": "Te protege de todos los robos de puntos por el resto de la partida.",
        "effect": "shield", "value": 0, "mystery": False,
    },
)

# ---------------------------------------------------------------------------
# Fibbage / Duelo de Falso Conocimiento
# ---------------------------------------------------------------------------

FIBBAGE_FACTS: tuple[dict[str, str], ...] = (
    {
        "prompt": "En 1929, la primera ceremonia de los Óscar entregó el premio a Mejor Película a una cinta muda llamada…",
        "answer": "Alas", "hint": "Los aviones tienen mucho que ver.", "category": "Cine",
    },
    {
        "prompt": "El único actor que ha ganado tres premios Óscar a Mejor Actor es…",
        "answer": "Daniel Day-Lewis", "hint": "También fue Bill el Carnicero.", "category": "Cine",
    },
    {
        "prompt": "La película que más dinero ha recaudado en la historia, ajustada por inflación, es…",
        "answer": "Lo que el viento se llevó", "hint": "Un clásico de 1939 con escaleras y un «no me importa».",
        "category": "Cine",
    },
    {
        "prompt": "El primer largometraje de animación de la historia, estrenado en 1917, se tituló…",
        "answer": "El apóstol", "hint": "Se hizo en Argentina con recortes de papel.",
        "category": "Cine",
    },
    {
        "prompt": "La primera película de Pixar, estrenada en 1995, fue…",
        "answer": "Toy Story", "hint": "Juguetes que cobran vida.", "category": "Cine",
    },
    {
        "prompt": "El nombre del pueblo ficticio donde vive Marty McFly es…",
        "answer": "Hill Valley", "hint": "Hay un reloj torre y un DeLorean.", "category": "Cine",
    },
    {
        "prompt": "La actriz que interpretó a Mary Poppins en 1964 fue…",
        "answer": "Julie Andrews", "hint": "También fue Maria von Trapp.", "category": "Cine",
    },
    {
        "prompt": "El nombre del perro de Dorothy en «El mago de Oz» es…",
        "answer": "Toto", "hint": "No es un perro grande precisamente.", "category": "Cine",
    },
    {
        "prompt": "La película de 1975 que popularizó el concepto de blockbuster de verano fue…",
        "answer": "Tiburón", "hint": "Necesitas un bote más grande.", "category": "Cine",
    },
    {
        "prompt": "El director de «Pulp Fiction» y «Kill Bill» es…",
        "answer": "Quentin Tarantino", "hint": "Le encantan los pies y los diálogos largos.", "category": "Cine",
    },
    {
        "prompt": "El actor que dio vida a Wolverine en nueve películas fue…",
        "answer": "Hugh Jackman", "hint": "También canta en «El gran showman».", "category": "Cine",
    },
    {
        "prompt": "La película animada de Disney de 1994 sobre un cachorro de león se llama…",
        "answer": "El rey león", "hint": "Hakuna Matata.", "category": "Cine",
    },
    {
        "prompt": "El nombre del icónico agente secreto británico creado por Ian Fleming es…",
        "answer": "James Bond", "hint": "Le gustan los martinis, no las revoluciones.", "category": "Cine",
    },
    {
        "prompt": "La película de 1999 en la que Neo descubre que vive en una simulación se llama…",
        "answer": "The Matrix", "hint": "Pastilla roja o azul.", "category": "Cine",
    },
    {
        "prompt": "El director mexicano de «El laberinto del fauno» y «La forma del agua» es…",
        "answer": "Guillermo del Toro", "hint": "Le gustan los monstruos con corazón.", "category": "Cine",
    },
    {
        "prompt": "El nombre de la productora fundada por Spielberg, Katzenberg y Geffen es…",
        "answer": "DreamWorks", "hint": "El niño pescador de la luna.", "category": "Cine",
    },
    {
        "prompt": "La primera película de la saga «Alien» se estrenó en el año…",
        "answer": "1979", "hint": "Ridley Scott y una criatura de H. R. Giger.", "category": "Cine",
    },
    {
        "prompt": "El actor que interpretó a Hannibal Lecter en «El silencio de los inocentes» fue…",
        "answer": "Anthony Hopkins", "hint": "Unas habas al horno y un Chianti.", "category": "Cine",
    },
    {
        "prompt": "El nombre del pueblo de «Coco» donde vive Miguel es…",
        "answer": "Santa Cecilia", "hint": "Música, calaveras y una guitarra.", "category": "Cine",
    },
    {
        "prompt": "El título de la primera película de la saga «Rápidos y furiosos» es…",
        "answer": "The Fast and the Furious", "hint": "Todo empezó con autos y una familia.", "category": "Cine",
    },
    {
        "prompt": "La serie de televisión sobre un profesor de química que se convierte en narcotraficante es…",
        "answer": "Breaking Bad", "hint": "Un sombrero y una caravana.", "category": "Series",
    },
    {
        "prompt": "El nombre del pueblo ficticio de «Stranger Things» es…",
        "answer": "Hawkins", "hint": "Hay un laboratorio y un mundo al revés.", "category": "Series",
    },
    {
        "prompt": "La primera emisión de «Los Simpson» como serie fue en el año…",
        "answer": "1989", "hint": "La familia amarilla más famosa debutó en diciembre.", "category": "Series",
    },
    {
        "prompt": "El actor que interpretó a Tony Soprano en «Los Soprano» fue…",
        "answer": "James Gandolfini", "hint": "Un jefe mafioso con ataques de pánico.", "category": "Series",
    },
    {
        "prompt": "La serie sobre la familia real británica que se llama igual que una corona es…",
        "answer": "The Crown", "hint": "Isabel II, Diana y mucho protocolo.", "category": "Series",
    },
    {
        "prompt": "El nombre del hotel de «The Shining» es…",
        "answer": "Overlook", "hint": "Pasillos rojos y un niño que dice «redrum».", "category": "Cine",
    },
    {
        "prompt": "El nombre del caballo de «El padrino» que aparece en la cama es…",
        "answer": "Khartoum", "hint": "Un nombre de ciudad africana.", "category": "Cine",
    },
    {
        "prompt": "El personaje de «El padrino» que dice «Le voy a hacer una oferta que no podrá rechazar» es…",
        "answer": "Don Vito Corleone", "hint": "El patriarca de la familia.", "category": "Cine",
    },
    {
        "prompt": "El nombre del ratón más famoso de Disney es…",
        "answer": "Mickey Mouse", "hint": "Tiene guantes blancos y orejas redondas.", "category": "Cine",
    },
    {
        "prompt": "El nombre del perro de la familia Simpson es…",
        "answer": "Ayudante de Santa", "hint": "Un galgo con muy mala suerte.", "category": "Series",
    },
)


FIBBAGE_DECOYS: dict[str, tuple[str, str, str]] = {
    "Alas": ("El cantor de jazz", "Metrópolis", "Los ángeles del infierno"),
    "Daniel Day-Lewis": ("Jack Nicholson", "Marlon Brando", "Robert De Niro"),
    "Lo que el viento se llevó": ("Titanic", "Ben-Hur", "El mago de Oz"),
    "El apóstol": ("El viaje a la luna", "Blancanieves", "Gertie el dinosaurio"),
    "Toy Story": ("Bichos", "Cars", "Monsters, Inc."),
    "Hill Valley": ("Springfield", "Riverside", "Rock Ridge"),
    "Julie Andrews": ("Audrey Hepburn", "Debbie Reynolds", "Barbra Streisand"),
    "Toto": ("Rex", "Lassie", "Scooby-Doo"),
    "Tiburón": ("Star Wars", "Rocky", "Grease"),
    "Quentin Tarantino": ("Martin Scorsese", "Joel Coen", "David Fincher"),
    "Hugh Jackman": ("Christian Bale", "Robert Downey Jr.", "Chris Evans"),
    "El rey león": ("Bambi", "El libro de la selva", "La bella y la bestia"),
    "James Bond": ("Jason Bourne", "Ethan Hunt", "Jack Ryan"),
    "The Matrix": ("Terminator", "Blade Runner", "eXistenZ"),
    "Guillermo del Toro": ("Alfonso Cuarón", "Alejandro González Iñárritu", "Robert Rodríguez"),
    "DreamWorks": ("Pixar", "Studio Ghibli", "Blue Sky Studios"),
    "1979": ("1975", "1982", "1986"),
    "Anthony Hopkins": ("Brian Cox", "Mads Mikkelsen", "Gary Oldman"),
    "Santa Cecilia": ("San Ángel", "Santa Clara", "San Marcos"),
    "The Fast and the Furious": ("Need for Speed", "Gone in 60 Seconds", "Drive"),
    "Breaking Bad": ("Narcos", "The Wire", "Ozark"),
    "Hawkins": ("Derry", "Riverdale", "Castle Rock"),
    "1989": ("1985", "1991", "1993"),
    "James Gandolfini": ("Robert De Niro", "Al Pacino", "Joe Pesci"),
    "The Crown": ("Downton Abbey", "Victoria", "The Tudors"),
    "Overlook": ("Bates Motel", "The Grand Budapest", "Hotel Transylvania"),
    "Khartoum": ("Bucephalus", "Silver", "Trigger"),
    "Don Vito Corleone": ("Michael Corleone", "Sonny Corleone", "Tom Hagen"),
    "Mickey Mouse": ("Bugs Bunny", "Popeye", "Felix the Cat"),
    "Ayudante de Santa": ("Scooby-Doo", "Krypto", "Brian"),
}


def _rng(seed: str, salt: str) -> random.Random:
    return random.Random(f"play:{salt}:{seed}")


def _rounds(value: Any, limit: int) -> int:
    try:
        number = int(value or 1)
    except (TypeError, ValueError):
        number = 1
    return max(1, min(number, max(1, limit)))


def build_impostor_deck(rounds: int, seed: str = "") -> list[dict[str, Any]]:
    """Ronda del impostor: palabra, categoría, preguntas y opciones de rescate."""
    rng = _rng(seed, "impostor")
    pool = list(IMPOSTOR_WORDS)
    rng.shuffle(pool)
    total = _rounds(rounds, len(pool))
    out: list[dict[str, Any]] = []
    for index, card in enumerate(pool[:total]):
        same = [c["word"] for c in IMPOSTOR_WORDS
                if c["category"] == card["category"] and c["word"] != card["word"]]
        others = [c["word"] for c in IMPOSTOR_WORDS if c["word"] != card["word"]]
        rng.shuffle(same)
        rng.shuffle(others)
        decoys: list[str] = []
        for source in (same, others):
            for word in source:
                if word not in decoys and word != card["word"]:
                    decoys.append(word)
                if len(decoys) >= 7:
                    break
            if len(decoys) >= 7:
                break
        options = [card["word"], *decoys[:7]]
        rng.shuffle(options)
        questions = list(IMPOSTOR_QUESTIONS)
        start = index % len(questions)
        out.append({
            "id": f"impostor:{index}:{card['word'].lower()}",
            "word": card["word"],
            "category": card["category"],
            "hint": card["hint"],
            "questions": [questions[(start + n) % len(questions)] for n in range(3)],
            "guessOptions": options,
        })
    return out


def build_drawing_deck(rounds: int, seed: str = "") -> list[dict[str, Any]]:
    rng = _rng(seed, "dibujo")
    pool = list(DRAWING_PROMPTS)
    rng.shuffle(pool)
    total = _rounds(rounds, len(pool))
    decoy_pool = list(DRAWING_DECOYS)
    out: list[dict[str, Any]] = []
    for index, prompt in enumerate(pool[:total]):
        candidates = [item for item in decoy_pool if item != prompt]
        rng.shuffle(candidates)
        out.append({
            "id": f"dibujo:{index}",
            "prompt": prompt,
            "title": prompt,
            "decoys": candidates[:3],
        })
    return out


def build_auction_deck(rounds: int, seed: str = "") -> list[dict[str, Any]]:
    rng = _rng(seed, "subasta")
    pool = list(AUCTION_ITEMS)
    rng.shuffle(pool)
    total = _rounds(rounds, len(pool))
    return [
        {**item, "id": f"subasta:{index}:{item['id']}"}
        for index, item in enumerate(pool[:total])
    ]


def build_fibbage_deck(rounds: int, seed: str = "") -> list[dict[str, Any]]:
    rng = _rng(seed, "fibbage")
    pool = list(FIBBAGE_FACTS)
    rng.shuffle(pool)
    total = _rounds(rounds, len(pool))
    out: list[dict[str, Any]] = []
    for index, item in enumerate(pool[:total]):
        decoys = list(FIBBAGE_DECOYS.get(str(item["answer"]), ()))
        out.append({
            "id": f"fibbage:{index}:{index}",
            **item,
            "decoys": decoys[:3],
        })
    return out
