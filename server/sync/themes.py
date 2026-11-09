"""Colecciones temáticas que se encienden solas según la fecha.

El catálogo se sentía estático: «Terror» y «Comedia» son filas permanentes, pero no
había nada que dijera «es Día de Muertos», «es Navidad» o «es San Valentín». Este
módulo arma categorías `tema-*` con títulos del catálogo real:

  - Día de Muertos, Halloween, Navidad, San Valentín, Día de las Madres, Día del
    Padre, Fiestas Patrias, Vacaciones, Regreso a clases y Año Nuevo, cada una con
    su ventana de fechas.
  - «Para reír» todo el año, para que la comedia tenga una fila propia.
  - «Tendencias del mundo» con la foto semanal de TMDB.

Las temáticas de temporada alta (Halloween y Día de Muertos) NO se llenan con
palabras sueltas: se consultan las **keywords oficiales de TMDB**
(`/discover/{movie,tv}?with_keywords=…`), la misma clasificación que usan Kodi,
Jellyfin, Plex y las colecciones de TMDB. Así:

  - «Halloween» solo lleva obras con la keyword `halloween` (3335); no cualquier
    película de terror.
  - «Día de Muertos» solo lleva obras con las keywords `day of the dead` (209789)
    o `dia de los muertos` (322048), más una lista corta de obras canónicas de la
    fiesta (Coco, El libro de la vida, Macario, La leyenda de la Nahuala…).
  - «Noche de terror» se retiró: era la misma fila de «Terror» todo el año.

TMDB comparte el espacio de IDs entre películas y series (el id 300401 es la
película «Day of the Dead» de 1957 y la serie «Dynasty»). Por eso cada miembro
guardado lleva su `media` y al cruzar se descarta el que no corresponde; si el
tipo de la ficha es ambiguo (anime/kids pueden ser peli o serie), se exige que el
título coincida. La foto de TMDB se guarda en `theme_tmdb` para que un fallo de red
no borre la colección: se conserva la última copia buena.

Las temáticas de texto (Navidad, San Valentín…) mantienen su selección por etapas
para no llenar una fila con lo que no toca: título → frases específicas de la
sinopsis → género. Cada obra entra UNA vez: se deduplica por tmdb_id (las
temporadas T1..T20 de una serie no consumen la fila) y, sin TMDB, por título
plegado.
"""

import asyncio
import logging
import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone

import genres as genre_rules
import tmdb
from catalog_filters import NOT_TRAILER, NOT_UNMATCHED, fold_sql, keyword_patterns
from xtv import CAT_SQL

log = logging.getLogger("themes")

WORLD_TRENDING_ID = "tema-tendencias-mundo"
WORLD_TRENDING_TITLE = "Tendencias del mundo"

# Keywords de TMDB (https://www.themoviedb.org/keyword/<id>). Son la fuente de
# verdad de la clasificación: no se adivina por palabras de la sinopsis.
TMDB_HALLOWEEN = 3335           # halloween
TMDB_DAY_OF_THE_DEAD = 209789   # day of the dead
TMDB_DIA_DE_MUERTOS = 322048    # dia de los muertos


@dataclass(frozen=True)
class Theme:
    id: str
    title: str
    genres: tuple[str, ...] = ()
    fallback_genres: tuple[str, ...] = ()
    title_keywords: tuple[str, ...] = ()
    synopsis_keywords: tuple[str, ...] = ()
    tmdb_keywords: tuple[int, ...] = ()
    # (título plegado, año) de obras canónicas que TMDB no siempre etiqueta.
    seed_titles: tuple[tuple[str, int], ...] = ()
    # Ventana recurrente por mes/día. `start_date`/`end_date` se usan para eventos
    # de una sola vez (Mundial 2027, eclipse, elección…).
    start: tuple[int, int] | None = None
    end: tuple[int, int] | None = None
    start_date: date | None = None
    end_date: date | None = None
    limit: int = 24
    min_titles: int = 8
    always: bool = False
    # Metadatos del calendario editorial: prioridad, superficie y textos del hero.
    priority: int = 50
    hero: bool = True
    row: bool = True
    badge: str = ""
    eyebrow: str = "ESPECIAL DE TEMPORADA"


# El orden importa: las temporadas primero y las permanentes al final. En octubre
# conviven Día de Muertos y Halloween; Día de Muertos va primero porque es la fiesta
# mexicana y el pedido explícito.
THEMES: tuple[Theme, ...] = (
    Theme(
        id="tema-dia-de-muertos",
        title="Día de Muertos",
        start=(10, 24),
        end=(11, 4),
        priority=95,
        hero=True,
        # Nada de «muerto», «muerte», «terror» o «fantasma»: esas palabras metían
        # Harry Potter, Piratas del Caribe y dramas médicos en la fiesta mexicana.
        tmdb_keywords=(TMDB_DAY_OF_THE_DEAD, TMDB_DIA_DE_MUERTOS),
        seed_titles=(
            ("coco", 2017),
            ("el libro de la vida", 2014),
            ("la leyenda de la nahuala", 2007),
            ("macario", 1960),
            ("dia de muertos", 2019),
            ("catalina la catrina especial dia de muertos", 2021),
            ("amar hasta a la muerte la celebracion mexicana del dia de muertos", 2021),
        ),
        limit=24,
        # Con la clasificación estricta quedan ~8 obras reales en el catálogo. Es
        # preferible una fila corta y honesta que 28 títulos que no son de la fiesta.
        min_titles=4,
    ),
    Theme(
        id="tema-halloween",
        title="Halloween",
        start=(10, 1),
        end=(10, 31),
        priority=85,
        hero=True,
        # Solo la keyword canónica. Las variantes «halloween party/costume» metían
        # comedias románticas y episodios sueltos sin tono de Halloween.
        tmdb_keywords=(TMDB_HALLOWEEN,),
        limit=28,
        min_titles=8,
    ),
    Theme(
        id="tema-navidad",
        title="Navidad",
        start=(12, 1),
        end=(12, 31),
        priority=90,
        hero=True,
        title_keywords=(
            "navidad", "naviden", "santa", "papa noel", "nochebuena",
            "reno", "duende", "christmas", "milagro",
        ),
        synopsis_keywords=("navidad", "nochebuena", "papa noel"),
        limit=28,
    ),
    Theme(
        id="tema-san-valentin",
        title="San Valentín",
        start=(2, 1),
        end=(2, 15),
        priority=70,
        hero=True,
        title_keywords=(
            "san valentin", "amor", "enamor", "boda", "pareja", "corazon",
            "romance", "desamor",
        ),
        synopsis_keywords=("san valentin", "dia del amor", "enamorado"),
        limit=24,
    ),
    Theme(
        id="tema-dia-de-las-madres",
        title="Día de las Madres",
        start=(5, 1),
        end=(5, 12),
        priority=75,
        hero=True,
        title_keywords=("mama", "madre", "maternal", "madrecita", "abuela"),
        synopsis_keywords=("dia de las madres", "amor de madre"),
        limit=20,
    ),
    Theme(
        id="tema-dia-del-padre",
        title="Día del Padre",
        start=(6, 1),
        end=(6, 21),
        priority=60,
        hero=False,
        title_keywords=("papa", "padre", "paternal", "papacito", "abuelo"),
        synopsis_keywords=("dia del padre", "amor de padre"),
        limit=20,
    ),
    Theme(
        id="tema-fiestas-patrias",
        title="Fiestas Patrias",
        start=(9, 10),
        end=(9, 20),
        priority=80,
        hero=True,
        title_keywords=(
            "mexico", "mexican", "revolucion", "rancho", "charro", "norteno",
            "mariachi", "patria", "independencia",
        ),
        synopsis_keywords=(
            "revolucion mexicana", "independencia de mexico", "fiestas patrias",
        ),
        limit=20,
    ),
    Theme(
        id="tema-vacaciones",
        title="Vacaciones",
        start=(7, 1),
        end=(8, 20),
        priority=55,
        hero=False,
        title_keywords=(
            "vacaciones", "verano", "playa", "viaje", "crucero", "hotel", "isla",
        ),
        synopsis_keywords=("vacaciones de verano", "viaje de vacaciones"),
        limit=20,
    ),
    Theme(
        id="tema-regreso-a-clases",
        title="Regreso a clases",
        start=(8, 1),
        end=(9, 10),
        priority=55,
        hero=False,
        title_keywords=(
            "escuela", "colegio", "universidad", "estudiante", "maestro",
            "profesor", "prepa",
        ),
        synopsis_keywords=("regreso a clases", "primer dia de clases"),
        limit=20,
    ),
    Theme(
        id="tema-ano-nuevo",
        title="Año Nuevo",
        start=(12, 28),
        end=(1, 10),
        priority=85,
        hero=True,
        title_keywords=(
            "ano nuevo", "nuevo comienzo", "fin de ano", "proposito", "brindis",
        ),
        synopsis_keywords=("ano nuevo", "nochevieja", "proposito de ano nuevo"),
        limit=20,
    ),
    Theme(
        id="tema-para-reir",
        title="Para reír",
        genres=("Comedia",),
        limit=24,
        always=True,
        priority=10,
        hero=False,
    ),
)

# Capa de eventos y semanas especiales. Se conserva separada de las temporadas
# base para que sea fácil revisar qué se agregó por coyuntura y qué es permanente.
# `start`/`end` son recurrentes por mes/día; `start_date`/`end_date` son eventos
# de una sola vez (Mundial 2027, eclipse, elección…).
EVENTS: tuple[Theme, ...] = (
    Theme(
        id="tema-evento-planeta",
        title="Planeta en juego",
        start=(11, 9),
        end=(11, 20),
        priority=72,
        hero=True,
        badge="PLANETA EN JUEGO",
        eyebrow="ESPECIAL COP31 · CLIMA",
        title_keywords=(
            "clima", "planeta", "tierra", "naturaleza", "ecolog", "calentamiento",
            "desastre", "contaminacion", "sostenible", "futuro",
        ),
        synopsis_keywords=(
            "cambio climatico", "medio ambiente", "calentamiento global",
            "desastre natural", "emergencia climatica",
        ),
        genres=("Documental",),
        limit=20,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-otono",
        title="Cine de otoño",
        start=(11, 5),
        end=(11, 30),
        priority=40,
        hero=True,
        row=True,
        badge="CINE DE OTOÑO",
        eyebrow="NOVIEMBRE",
        title_keywords=(
            "otono", "lluvia", "noche", "misterio", "drama", "ciudad",
            "regreso", "memoria", "familia",
        ),
        synopsis_keywords=("otono", "misterio", "familia", "regreso"),
        genres=("Drama", "Misterio", "Suspenso"),
        limit=18,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-musica-latina",
        title="Música latina",
        start=(11, 9),
        end=(11, 15),
        priority=58,
        hero=False,
        badge="MÚSICA LATINA",
        eyebrow="LATIN GRAMMY",
        title_keywords=(
            "musica", "cantante", "latino", "latina", "grammy", "concierto",
            "baile", "ritmo", "banda", "rock", "pop",
        ),
        synopsis_keywords=("musica latina", "concierto", "cantante"),
        genres=("Música",),
        limit=20,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-gratitud",
        title="Familia y gratitud",
        start=(11, 22),
        end=(11, 28),
        priority=78,
        hero=True,
        badge="FAMILIA Y GRATITUD",
        eyebrow="THANKSGIVING",
        title_keywords=(
            "gracias", "gratitud", "familia", "thanksgiving", "accion de gracias",
            "cena", "hogar", "amistad",
        ),
        synopsis_keywords=("accion de gracias", "familia", "gratitud"),
        genres=("Comedia", "Familia"),
        limit=20,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-sagas",
        title="Semana de sagas",
        start=(12, 13),
        end=(12, 20),
        priority=92,
        hero=True,
        badge="SEMANA DE SAGAS",
        eyebrow="BLOCKBUSTERS · FRANQUICIAS",
        title_keywords=(
            "avengers", "dune", "star wars", "marvel", "dc", "harry potter",
            "senor de los anillos", "transformers", "mision imposible",
            "jurassic", "matrix", "spider-man", "spiderman", "batman",
        ),
        synopsis_keywords=("franquicia", "superheroe", "galaxia", "secuela"),
        genres=("Ciencia ficción", "Fantasía", "Aventuras", "Acción"),
        limit=24,
        min_titles=8,
    ),
    Theme(
        id="tema-evento-reyes",
        title="Magia y regalos",
        start=(1, 4),
        end=(1, 6),
        priority=50,
        hero=False,
        badge="MAGIA Y REGALOS",
        eyebrow="DÍA DE REYES",
        title_keywords=(
            "reyes", "magia", "regalo", "fantasia", "navidad", "juguete",
            "aventura", "cuento",
        ),
        synopsis_keywords=("dia de reyes", "magia", "regalos"),
        genres=("Fantasía", "Familia"),
        limit=16,
        min_titles=4,
    ),
    Theme(
        id="tema-evento-globos",
        title="Temporada de premios",
        start=(1, 8),
        end=(1, 10),
        priority=88,
        hero=True,
        badge="TEMPORADA DE PREMIOS",
        eyebrow="GOLDEN GLOBES",
        title_keywords=(
            "premio", "ganador", "globo", "oscar", "biografia", "nominado",
            "aclamada", "aclamado",
        ),
        synopsis_keywords=("premio", "nominacion", "aclamada", "galardon"),
        genres=("Drama", "Comedia"),
        limit=20,
        min_titles=8,
    ),
    Theme(
        id="tema-evento-mlk",
        title="Historia y justicia",
        start=(1, 15),
        end=(1, 18),
        priority=55,
        hero=False,
        badge="HISTORIA Y JUSTICIA",
        eyebrow="MARTIN LUTHER KING",
        title_keywords=(
            "derechos", "justicia", "libertad", "historia", "racismo",
            "segregacion", "luther", "king", "igualdad",
        ),
        synopsis_keywords=("derechos civiles", "justicia social", "segregacion"),
        genres=("Historia", "Documental"),
        limit=16,
        min_titles=4,
    ),
    Theme(
        id="tema-evento-lunar",
        title="Año Nuevo Lunar",
        start=(2, 1),
        end=(2, 7),
        priority=86,
        hero=True,
        badge="AÑO NUEVO LUNAR",
        eyebrow="AÑO DE LA CABRA DE FUEGO",
        title_keywords=(
            "china", "chino", "japon", "japones", "dragon", "kung fu",
            "samurai", "anime", "lunar", "guerrero", "familia",
        ),
        synopsis_keywords=("ano nuevo lunar", "china", "japon", "familia"),
        genres=("Animación", "Acción", "Fantasía", "Familia"),
        limit=24,
        min_titles=8,
    ),
    Theme(
        id="tema-evento-grammys",
        title="Música · Grammy",
        start=(2, 5),
        end=(2, 7),
        priority=65,
        hero=False,
        badge="MÚSICA",
        eyebrow="GRAMMY",
        title_keywords=(
            "musica", "cantante", "grammy", "concierto", "banda", "rock",
            "pop", "artista", "escenario",
        ),
        synopsis_keywords=("grammy", "concierto", "carrera musical"),
        genres=("Música",),
        limit=18,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-superbowl",
        title="Super Domingo",
        start=(2, 8),
        end=(2, 14),
        priority=90,
        hero=True,
        badge="SUPER DOMINGO",
        eyebrow="SUPER BOWL LXI",
        title_keywords=(
            "super bowl", "nfl", "futbol americano", "football", "americano",
            "deporte", "atleta", "campeon", "competencia",
        ),
        synopsis_keywords=("super bowl", "futbol americano", "deporte"),
        genres=("Deporte", "Documental"),
        limit=20,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-carnaval",
        title="Carnaval",
        start=(2, 12),
        end=(2, 16),
        priority=60,
        hero=False,
        badge="CARNAVAL",
        eyebrow="COLOR · MÚSICA · FIESTA",
        title_keywords=(
            "carnaval", "fiesta", "baile", "musica", "mascara", "brasil",
            "venecia", "color",
        ),
        synopsis_keywords=("carnaval", "fiesta", "baile"),
        genres=("Comedia", "Música"),
        limit=18,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-mujeres",
        title="Mujeres en pantalla",
        start=(3, 1),
        end=(3, 8),
        priority=75,
        hero=True,
        badge="MUJERES EN PANTALLA",
        eyebrow="DÍA DE LA MUJER",
        title_keywords=(
            "mujer", "mujeres", "femin", "ella", "madre", "hija", "reina",
            "lider", "valiente",
        ),
        synopsis_keywords=("mujer", "femin", "igualdad", "lider"),
        genres=("Drama", "Historia"),
        limit=20,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-oscars",
        title="Noche de premios",
        start=(3, 8),
        end=(3, 14),
        priority=90,
        hero=True,
        badge="NOCHE DE PREMIOS",
        eyebrow="OSCARS 99",
        title_keywords=(
            "premio", "oscar", "ganador", "biografia", "nominado", "aclamada",
            "aclamado", "cine",
        ),
        synopsis_keywords=("oscar", "premio", "nominacion", "galardon"),
        genres=("Drama", "Historia", "Comedia"),
        limit=22,
        min_titles=8,
    ),
    Theme(
        id="tema-evento-semana-santa",
        title="Semana Santa",
        start=(3, 20),
        end=(4, 5),
        priority=80,
        hero=True,
        badge="SEMANA SANTA",
        eyebrow="FE · HISTORIA · ÉPICA",
        title_keywords=(
            "jesus", "biblia", "cristo", "dios", "fe", "exodo", "gladiador",
            "espartaco", "roma", "cruz",
        ),
        synopsis_keywords=("semana santa", "biblia", "jesus", "cristian"),
        genres=("Historia", "Drama", "Aventuras"),
        limit=20,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-tierra",
        title="Planeta",
        start=(4, 19),
        end=(4, 25),
        priority=60,
        hero=False,
        badge="PLANETA",
        eyebrow="DÍA DE LA TIERRA",
        title_keywords=(
            "tierra", "planeta", "naturaleza", "ecolog", "clima", "animales",
            "oceano", "bosque", "agua",
        ),
        synopsis_keywords=("medio ambiente", "naturaleza", "planeta", "cambio climatico"),
        genres=("Documental", "Familia"),
        limit=18,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-infancia",
        title="Infancia",
        start=(4, 26),
        end=(5, 2),
        priority=55,
        hero=False,
        badge="INFANCIA",
        eyebrow="DÍA DEL NIÑO",
        title_keywords=(
            "nino", "nina", "infancia", "juguete", "escuela", "aventura",
            "familia", "magia",
        ),
        synopsis_keywords=("dia del nino", "infancia", "familia"),
        genres=("Animación", "Familia", "Infantil"),
        limit=20,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-star-wars",
        title="Semana galáctica",
        start=(5, 1),
        end=(5, 8),
        priority=78,
        hero=True,
        badge="SEMANA GALÁCTICA",
        eyebrow="STAR WARS DAY",
        title_keywords=(
            "star wars", "guerra de las galaxias", "espacio", "galaxia",
            "nave", "robot", "alien", "marte", "luna",
        ),
        synopsis_keywords=("espacio", "galaxia", "nave espacial", "alien"),
        genres=("Ciencia ficción", "Aventuras", "Acción"),
        limit=22,
        min_titles=8,
    ),
    Theme(
        id="tema-evento-festivales",
        title="Mayo de festivales",
        start=(5, 10),
        end=(5, 22),
        priority=80,
        hero=True,
        badge="MAYO DE FESTIVALES",
        eyebrow="EUROVISIÓN · CANNES",
        title_keywords=(
            "musica", "cantante", "festival", "eurovision", "cannes", "cine",
            "francia", "paris", "concierto", "baile",
        ),
        synopsis_keywords=("festival", "concierto", "cine europeo"),
        genres=("Música", "Drama", "Comedia"),
        limit=20,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-orgullo",
        title="Orgullo",
        start=(6, 1),
        end=(6, 30),
        priority=65,
        hero=False,
        badge="ORGULLO",
        eyebrow="MES DEL ORGULLO",
        title_keywords=(
            "orgullo", "lgbt", "gay", "lesbiana", "trans", "identidad",
            "amor", "familia", "diversidad",
        ),
        synopsis_keywords=("lgbt", "orgullo", "identidad", "diversidad"),
        genres=("Drama", "Comedia", "Romance"),
        limit=20,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-eleccion-mx",
        title="Semana cívica",
        start_date=date(2027, 6, 1),
        end_date=date(2027, 6, 7),
        priority=55,
        hero=False,
        badge="SEMANA CÍVICA",
        eyebrow="ELECCIÓN MÉXICO 2027",
        title_keywords=(
            "politica", "presidente", "eleccion", "democracia", "gobierno",
            "mexico", "voto", "historia",
        ),
        synopsis_keywords=("eleccion", "democracia", "politica", "gobierno"),
        genres=("Historia", "Documental", "Drama"),
        limit=16,
        min_titles=4,
    ),
    Theme(
        id="tema-evento-afcon",
        title="África en juego",
        start_date=date(2027, 6, 19),
        end_date=date(2027, 7, 17),
        priority=70,
        hero=False,
        badge="ÁFRICA EN JUEGO",
        eyebrow="COPA AFRICANA 2027",
        title_keywords=(
            "africa", "africano", "futbol", "seleccion", "copa", "gol",
            "deporte", "campeon",
        ),
        synopsis_keywords=("africa", "futbol", "seleccion", "copa"),
        genres=("Deporte", "Documental", "Drama"),
        limit=18,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-mundial-femenil",
        title="Mundial femenil",
        start_date=date(2027, 6, 24),
        end_date=date(2027, 7, 25),
        priority=95,
        hero=True,
        badge="MUNDIAL FEMENIL",
        eyebrow="BRASIL 2027",
        title_keywords=(
            "futbol", "mundial", "seleccion", "gol", "copa", "deporte",
            "campeona", "femenil", "femenina", "brasil",
        ),
        synopsis_keywords=("futbol femenil", "mundial", "seleccion", "copa"),
        genres=("Deporte", "Documental", "Drama"),
        limit=24,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-comiccon",
        title="Cultura pop",
        start=(7, 20),
        end=(7, 26),
        priority=78,
        hero=True,
        badge="CULTURA POP",
        eyebrow="COMIC-CON",
        title_keywords=(
            "comic", "superheroe", "marvel", "dc", "anime", "fantasia",
            "cosplay", "manga", "heroe", "villano",
        ),
        synopsis_keywords=("comic", "superheroe", "fantasia", "manga"),
        genres=("Ciencia ficción", "Fantasía", "Animación", "Acción"),
        limit=22,
        min_titles=8,
    ),
    Theme(
        id="tema-evento-panamericanos",
        title="Juegos Panamericanos",
        start_date=date(2027, 7, 23),
        end_date=date(2027, 8, 8),
        priority=65,
        hero=False,
        badge="JUEGOS PANAMERICANOS",
        eyebrow="LIMA 2027",
        title_keywords=(
            "deporte", "atleta", "juego", "olimp", "campeon", "medalla",
            "competencia", "seleccion",
        ),
        synopsis_keywords=("juegos panamericanos", "deporte", "atleta", "medalla"),
        genres=("Deporte", "Documental"),
        limit=18,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-eclipse",
        title="Semana del eclipse",
        start_date=date(2027, 7, 30),
        end_date=date(2027, 8, 8),
        priority=85,
        hero=True,
        badge="SEMANA DEL ECLIPSE",
        eyebrow="ECLIPSE TOTAL · 2 AGO 2027",
        title_keywords=(
            "espacio", "marte", "luna", "sol", "eclipse", "astronauta",
            "nasa", "interestelar", "gravedad", "alien", "universo",
        ),
        synopsis_keywords=("espacio", "astronauta", "universo", "eclipse", "nasa"),
        genres=("Ciencia ficción", "Documental", "Aventuras"),
        limit=22,
        min_titles=7,
    ),
    Theme(
        id="tema-evento-cine-mexicano",
        title="Cine mexicano",
        start=(8, 13),
        end=(8, 20),
        priority=72,
        hero=True,
        badge="CINE MEXICANO",
        eyebrow="DÍA DEL CINE MEXICANO",
        title_keywords=(
            "mexico", "mexicano", "mexicana", "rancho", "charro", "mariachi",
            "norteno", "revolucion", "familia",
        ),
        synopsis_keywords=("cine mexicano", "mexico", "familia mexicana"),
        genres=("Drama", "Comedia", "Historia"),
        limit=22,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-herencia",
        title="Herencia hispana",
        start=(9, 15),
        end=(10, 15),
        priority=65,
        hero=False,
        badge="HERENCIA HISPANA",
        eyebrow="MES DE LA HERENCIA HISPANA",
        title_keywords=(
            "latino", "latina", "hispano", "espana", "espanol", "mexico",
            "colombia", "argentina", "cuba", "familia",
        ),
        synopsis_keywords=("latino", "hispano", "familia", "inmigrante"),
        genres=("Drama", "Comedia", "Música"),
        limit=22,
        min_titles=6,
    ),
    Theme(
        id="tema-evento-ciencia",
        title="Ciencia",
        start=(10, 4),
        end=(10, 11),
        priority=55,
        hero=False,
        badge="CIENCIA",
        eyebrow="PREMIOS NOBEL",
        title_keywords=(
            "ciencia", "cientifico", "nobel", "fisica", "quimica", "medicina",
            "matematicas", "genio", "inventor", "tecnologia",
        ),
        synopsis_keywords=("ciencia", "cientifico", "nobel", "tecnologia"),
        genres=("Documental", "Ciencia ficción", "Historia"),
        limit=18,
        min_titles=5,
    ),
    Theme(
        id="tema-evento-deporte-global",
        title="Deporte global",
        start_date=date(2027, 10, 1),
        end_date=date(2027, 11, 21),
        priority=45,
        hero=False,
        badge="DEPORTE GLOBAL",
        eyebrow="RUGBY · CRICKET",
        title_keywords=(
            "rugby", "cricket", "deporte", "seleccion", "copa", "mundial",
            "campeon", "atleta",
        ),
        synopsis_keywords=("rugby", "cricket", "deporte", "copa"),
        genres=("Deporte", "Documental"),
        limit=16,
        min_titles=4,
    ),
    Theme(
        id="tema-evento-diwali",
        title="Festival de la luz",
        start=(10, 28),
        end=(11, 3),
        priority=45,
        hero=False,
        badge="FESTIVAL DE LA LUZ",
        eyebrow="DIWALI",
        title_keywords=(
            "india", "indio", "bollywood", "diwali", "luz", "festival",
            "familia", "color",
        ),
        synopsis_keywords=("india", "diwali", "festival de la luz"),
        genres=("Drama", "Comedia", "Música"),
        limit=16,
        min_titles=4,
    ),
)

ALL_THEMES: tuple[Theme, ...] = THEMES + EVENTS


def _in_window(today: date, start: tuple[int, int] | None, end: tuple[int, int] | None) -> bool:
    if not start or not end:
        return False
    md = today.month * 100 + today.day
    start_md = start[0] * 100 + start[1]
    end_md = end[0] * 100 + end[1]
    if start_md <= end_md:
        return start_md <= md <= end_md
    # Ventana que cruza el año (Año Nuevo: 28-dic → 10-ene).
    return md >= start_md or md <= end_md


def _in_theme_window(today: date, theme: Theme) -> bool:
    """Ventana del tema: fecha exacta (evento) o recurrente por mes/día."""
    if theme.start_date and theme.end_date:
        return theme.start_date <= today <= theme.end_date
    return _in_window(today, theme.start, theme.end)


def active_themes(today: date | None = None) -> list[Theme]:
    """Temas activos ordenados para el Inicio.

    Primero los que pueden ocupar el hero, luego las filas secundarias y, dentro
    de cada grupo, por prioridad. Así el primer `tema-*` del índice es el que debe
    abrir el billboard y las filas de apoyo quedan después.
    """
    day = today or date.today()
    active = [
        (index, theme)
        for index, theme in enumerate(ALL_THEMES)
        if theme.always or _in_theme_window(day, theme)
    ]
    active.sort(key=lambda pair: (0 if pair[1].hero else 1, -pair[1].priority, pair[0]))
    return [theme for _, theme in active]


def _chunks(items, size: int):
    for index in range(0, len(items), size):
        yield items[index:index + size]


def _rows(command_tag: str) -> int:
    try:
        return int(command_tag.split()[-1])
    except Exception:
        return 0


async def _insert_links(conn, links: list[tuple[str, str, int]]) -> int:
    total = 0
    for chunk in _chunks(links, 5000):
        tag = await conn.execute(
            "insert into title_categories (title_id, category_id, position) "
            "select a, b, c from unnest($1::text[], $2::text[], $3::int[]) as t(a, b, c) "
            "on conflict do nothing",
            [row[0] for row in chunk],
            [row[1] for row in chunk],
            [row[2] for row in chunk],
        )
        total += _rows(tag)
    return total


def _work_key(row) -> str:
    """Una obra = un tmdb_id; sin TMDB, el título plegado (las temporadas no se repiten)."""
    tmdb_id = int(row["tmdb_id"] or 0)
    if tmdb_id:
        return f"tmdb:{tmdb_id}"
    return "title:" + genre_rules.fold(row["title"] or "")


_TEXT = (
    "coalesce(t.title, '') || ' ' || coalesce(t.synopsis, '') || ' ' "
    "|| coalesce(e.overview, '')"
)

_SEASON_MARK = re.compile(r"\b(?:t|temp\.?|temporada|season)\s*\d+\b", re.IGNORECASE)


def _fold_title(text: str) -> str:
    """Título sin acentos, sin marca de temporada y en minúsculas."""
    return " ".join(_SEASON_MARK.sub(" ", genre_rules.fold(text or "")).split())


def _media_matches_kind(media: str, kind: str) -> bool:
    """TMDB separa películas y series; el `kind` del portal no siempre coincide.

    `anime` y `kids` pueden ser película o serie, así que no se descartan por tipo:
    si el tipo es ambiguo y no coincide, `_title_matches` decide.
    """
    media = (media or "").strip().lower()
    kind = (kind or "").strip().lower()
    if media == "movie":
        return kind != "series"
    if media == "tv":
        return kind != "movie"
    return True


def _title_matches(row, tmdb_title: str, tmdb_year: int) -> bool:
    """Confirma que el id compartido es ESTA obra y no la del otro espacio de TMDB.

    El caso real que lo motivó: TMDB 300401 es la película «Day of the Dead» (1957)
    y la serie «Dynasty» (2017). Sin esta comprobación, «Dynasty T5» aparecía en
    Día de Muertos.
    """
    if not tmdb_title:
        return True
    row_year = int(row["year"] or 0)
    year = int(tmdb_year or 0)
    if row_year and year and abs(row_year - year) > 2:
        return False
    target = _fold_title(tmdb_title)
    if not target:
        return True
    target_tokens = {token for token in target.split() if len(token) > 2}
    candidates = [
        row["title"],
        row["localized_title"] or "",
        row["original_title"] or "",
        *(row["alt_titles"] or []),
        *(row["translated_titles"] or []),
    ]
    for candidate in candidates:
        folded = _fold_title(candidate)
        if not folded:
            continue
        if folded == target or folded.startswith(target + " ") or target.startswith(folded + " "):
            return True
        tokens = {token for token in folded.split() if len(token) > 2}
        if target_tokens and tokens:
            overlap = len(target_tokens & tokens) / min(len(target_tokens), len(tokens))
            if overlap >= 0.75:
                return True
    return False


def _copy_preference(row, seeded: bool) -> tuple:
    """Entre copias de la misma obra (doblajes, temporadas) elige la mejor tarjeta.

    Prefiere la semilla curada, la **temporada más nueva** (una serie en emisión debe
    enseñar su temporada vigente), la copia cuyo título coincide con el nombre es-MX
    de TMDB (así «El extraño mundo de Jack» gana a «O Estranho Mundo de Jack») y, por
    último, la popularidad.
    """
    localized = _fold_title(row["localized_title"] or "")
    title = _fold_title(row["title"] or "")
    spanish = 1 if localized and (
        title == localized or title.startswith(localized + " ") or localized.startswith(title + " ")
    ) else 0
    return (
        1 if seeded else 0,
        int(row["year"] or 0),
        spanish,
        float(row["popularity"] or 0),
        float(row["score"] or 0),
    )


async def _match(conn, condition: str, parameter) -> list:
    """Títulos que cumplen una condición, ordenados por impacto y sin repetir obra."""
    folded_title = fold_sql("coalesce(t.title, '')")
    folded_text = fold_sql(_TEXT)
    field = folded_title if condition == "title" else folded_text
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, e.tmdb_id
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_UNMATCHED} and {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
          and {field} like any($1::text[])
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
        limit 160
    """
    rows = await conn.fetch(sql, parameter)
    out = []
    seen: set[str] = set()
    for row in rows:
        key = _work_key(row)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


async def _genre_matches(conn, genres: tuple[str, ...], primary_by_id: dict | None = None) -> list:
    """Títulos cuyo GÉNERO PRIMARIO está en [genres], una tarjeta por obra.

    Se consulta por los IDs que ya calculó el índice (no por las etiquetas crudas):
    así «Para reír» muestra las comedias de verdad y no dramas que llevan «Comedia»
    como etiqueta secundaria. El relleno de un género dice lo que la fila promete.
    """
    if not primary_by_id:
        return []
    ids = [title_id for title_id, primary in primary_by_id.items() if primary in genres]
    if not ids:
        return []
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, e.tmdb_id
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_UNMATCHED} and {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
          and t.id = any($1::text[])
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
        limit 300
    """
    rows = await conn.fetch(sql, ids)
    out = []
    seen: set[str] = set()
    for row in rows:
        key = _work_key(row)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


async def _seed_matches(conn, seeds: tuple[tuple[str, int], ...]) -> list:
    """Obras canónicas de la fiesta que TMDB no siempre etiqueta.

    `seeds` ya vienen plegadas y con el año para no confundir remakes (Coco 2017
    no es «Coco» de 2009). El plegado de la base quita puntuación, así que
    «Catalina la Catrina: especial Día de Muertos» casa con su semilla.
    """
    if not seeds:
        return []
    folded = [seed[0] for seed in seeds]
    years = [seed[1] for seed in seeds]
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, t.kind, e.tmdb_id,
               coalesce(e.localized_title, '') as localized_title,
               coalesce(e.original_title, '') as original_title,
               coalesce(e.alt_titles, '{{}}'::text[]) as alt_titles,
               coalesce(e.translated_titles, '{{}}'::text[]) as translated_titles
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
          and exists (
              select 1 from unnest($1::text[], $2::int[]) as s(folded, year)
              where vp_fold(t.title) = s.folded
                and (s.year = 0 or coalesce(t.year, 0) = s.year)
          )
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
    """
    return await conn.fetch(sql, folded, years)


async def _refresh_tmdb_members(conn, theme: Theme, discover=None) -> None:
    """Refresca la foto de TMDB; si falla, deja la anterior (no inventa la fila).

    La foto se conserva una semana: el calendario puede rearmar una fila al
    cambiar de campaña sin gastar llamadas a TMDB en cada revisión de 15 min.
    """
    if not theme.tmdb_keywords:
        return
    last = await conn.fetchval(
        "select max(updated_at) from theme_tmdb where theme_id = $1", theme.id
    )
    if last is not None and last > datetime.now(timezone.utc) - timedelta(days=7):
        return
    fetch = discover or tmdb.discover_keyword_members
    try:
        found = await asyncio.to_thread(fetch, theme.tmdb_keywords)
    except Exception as exc:  # noqa: BLE001 - la copia anterior sigue sirviendo
        log.warning("tema %s: TMDB falló (%s); se conserva la foto anterior", theme.id, exc)
        return
    if not found:
        log.warning("tema %s: TMDB no devolvió títulos; se conserva la foto anterior", theme.id)
        return
    seen: set[tuple[int, str]] = set()
    rows: list[tuple] = []
    for item in found:
        tmdb_id = int(item.get("id") or 0)
        media = str(item.get("media") or "")
        if not tmdb_id or (tmdb_id, media) in seen:
            continue
        seen.add((tmdb_id, media))
        rows.append(
            (
                theme.id,
                tmdb_id,
                media,
                str(item.get("title") or "")[:200],
                int(item.get("year") or 0),
            )
        )
    if not rows:
        log.warning("tema %s: TMDB devolvió solo IDs inválidos; se conserva la foto anterior", theme.id)
        return
    async with conn.transaction():
        await conn.execute("delete from theme_tmdb where theme_id = $1", theme.id)
        await conn.executemany(
            "insert into theme_tmdb (theme_id, tmdb_id, media, title, year, updated_at) "
            "values ($1, $2, $3, $4, $5, now())",
            rows,
        )


async def _tmdb_matches(conn, theme: Theme) -> list:
    """Títulos del catálogo que TMDB clasificó con las keywords del tema."""
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, t.kind, e.tmdb_id,
               m.media, m.title as tmdb_title, m.year as tmdb_year,
               coalesce(e.localized_title, '') as localized_title,
               coalesce(e.original_title, '') as original_title,
               coalesce(e.alt_titles, '{{}}'::text[]) as alt_titles,
               coalesce(e.translated_titles, '{{}}'::text[]) as translated_titles
        from theme_tmdb m
        join enrichment e on e.tmdb_id = m.tmdb_id
             and coalesce(e.match_confidence, 0) >= 0.5
        join titles t on t.id = e.title_id
        where m.theme_id = $1
          and {NOT_UNMATCHED} and {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
        limit 500
    """
    rows = await conn.fetch(sql, theme.id)
    out = []
    for row in rows:
        if not _media_matches_kind(row["media"], row["kind"]):
            if not _title_matches(row, row["tmdb_title"], row["tmdb_year"]):
                continue
        out.append(row)
    return out


async def _classified_ids(conn, theme: Theme, discover=None) -> list[str]:
    """Tema con clasificación externa: semillas canónicas + keywords de TMDB."""
    seed_rows = await _seed_matches(conn, theme.seed_titles)
    await _refresh_tmdb_members(conn, theme, discover)
    tmdb_rows = await _tmdb_matches(conn, theme)
    unique: dict[str, tuple[object, tuple]] = {}
    for seeded, rows in ((True, seed_rows), (False, tmdb_rows)):
        for row in rows:
            key = _work_key(row)
            preference = _copy_preference(row, seeded)
            current = unique.get(key)
            if current is None or preference > current[1]:
                unique[key] = (row, preference)
    ordered = sorted(
        (row for row, _ in unique.values()),
        key=lambda row: (
            -float(row["popularity"] or 0),
            -float(row["score"] or 0),
            -int(row["year"] or 0),
            (row["title"] or "").lower(),
        ),
    )
    return [row["id"] for row in ordered[: theme.limit]]


async def _theme_ids(
    conn,
    theme: Theme,
    primary_by_id: dict | None = None,
    discover=None,
) -> list[str]:
    """Título → sinopsis específica → género → relleno. Una tarjeta por obra."""
    if theme.tmdb_keywords or theme.seed_titles:
        return await _classified_ids(conn, theme, discover)

    picked: list = []
    seen: set[str] = set()

    def add(rows):
        for row in rows:
            key = _work_key(row)
            if key in seen:
                continue
            seen.add(key)
            picked.append(row)

    title_patterns = keyword_patterns(theme.title_keywords)
    if title_patterns:
        add(await _match(conn, "title", title_patterns))
    if len(picked) < theme.limit:
        synopsis_patterns = keyword_patterns(theme.synopsis_keywords)
        if synopsis_patterns:
            add(await _match(conn, "synopsis", synopsis_patterns))
    if len(picked) < theme.limit and theme.genres:
        add(await _genre_matches(conn, theme.genres, primary_by_id))
    if len(picked) < theme.limit and theme.fallback_genres:
        add(await _genre_matches(conn, theme.fallback_genres, primary_by_id))
    return [row["id"] for row in picked[: theme.limit]]


async def _world_trending(conn, limit: int = 40) -> list[str]:
    """«Tendencias del mundo»: la foto semanal de TMDB, una tarjeta por obra."""
    rows = await conn.fetch(
        """
        select e.title_id, w.tmdb_id
        from weekly_trending w
        join enrichment e on e.tmdb_id = w.tmdb_id
             and coalesce(e.match_confidence, 0) >= 0.5
        join titles t on t.id = e.title_id
        where w.week = (select max(week) from weekly_trending)
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
        order by w.rank, coalesce(e.match_confidence, 0) desc,
                 t.popularity desc nulls last, t.vote_count desc nulls last
        limit 400
        """
    )
    out: list[str] = []
    seen: set[int] = set()
    for row in rows:
        tmdb_id = int(row["tmdb_id"] or 0)
        if tmdb_id and tmdb_id in seen:
            continue
        if tmdb_id:
            seen.add(tmdb_id)
        out.append(row["title_id"])
        if len(out) >= limit:
            break
    return out


_BUILD_LOCK = asyncio.Lock()


async def build(
    pool,
    genres_by_id: dict | None = None,
    today: date | None = None,
    discover=None,
) -> int:
    """Rehace las categorías `tema-*` activas (las vencidas desaparecen solas).

    El candado evita que el ciclo del índice y el vigilante del calendario
    reconstruyan las filas a la vez (el segundo corre cada 15 min).
    """
    async with _BUILD_LOCK:
        return await _build(pool, genres_by_id, today, discover)


async def _build(
    pool,
    genres_by_id: dict | None = None,
    today: date | None = None,
    discover=None,
) -> int:
    changed = 0
    primary_by_id: dict[str, str | None] = {}
    if genres_by_id:
        for title_id, genres in genres_by_id.items():
            primary_by_id[title_id] = genre_rules.primary_genre(genres)
    active = active_themes(today)
    async with pool.acquire() as conn:
        await conn.execute(
            "delete from title_categories "
            "where category_id like 'tema-%' or category_id like 'evento-%'"
        )
        await conn.execute(
            "delete from categories where id like 'tema-%' or id like 'evento-%'"
        )
        # La foto de TMDB se conserva ~13 meses: al encender una temporada no
        # dependemos de la red y el héroe puede cambiar en el mismo minuto.
        await conn.execute(
            "delete from theme_tmdb where theme_id <> all($1::text[]) "
            "and updated_at < now() - interval '400 days'",
            [theme.id for theme in active],
        )
        position = 5
        for theme in active:
            ids = await _theme_ids(conn, theme, primary_by_id, discover)
            if len(ids) < theme.min_titles:
                log.info("tema %s: solo %s títulos; no se publica", theme.id, len(ids))
                continue
            await conn.execute(CAT_SQL, theme.id, theme.title, "theme", position)
            position += 1
            changed += await _insert_links(
                conn,
                [(title_id, theme.id, 1 + index) for index, title_id in enumerate(ids[: theme.limit])],
            )
        world = await _world_trending(conn)
        if len(world) >= 10:
            await conn.execute(CAT_SQL, WORLD_TRENDING_ID, WORLD_TRENDING_TITLE, "theme", position)
            changed += await _insert_links(
                conn,
                [(title_id, WORLD_TRENDING_ID, 1 + index) for index, title_id in enumerate(world)],
            )
    return changed
