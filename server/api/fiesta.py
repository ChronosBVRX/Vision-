"""Vision+ Fiesta — motor de los seis juegos nuevos.

El módulo es deliberadamente independiente de la máquina de estados histórica
de `games.py`: `games.Room` le delega el tick, las acciones y el snapshot
cuando `game` pertenece a `FIESTA_GAMES`.  Así los juegos nuevos no heredan la
deuda de Charadas/Apuesta ni las decenas de campos por juego de `Room`.
"""

from __future__ import annotations

import hashlib
import math
import random
import uuid
import re
import unicodedata
from typing import Any

import fiesta_content as content


FIESTA_GAMES: tuple[str, ...] = (
    "ultima_palabra",
    "vox_populi",
    "fuera_de_epoca",
    "texto_roto",
    "franela_fest",
    "el_tasador",
)

FIESTA_INFO: dict[str, dict[str, Any]] = {
    "ultima_palabra": {
        "id": "ultima_palabra",
        "title": "La Última Palabra",
        "tagline": "Escribe, ríe y vota",
        "description": "Un concurso de ingenio: la tele lanza una frase y todos escriben la "
                       "respuesta más graciosa. Después la sala vota y la última palabra la "
                       "tiene el que más hizo reír.",
        "minPlayers": 3,
        "maxPlayers": 8,
        "approxMinutes": 24,
        "rounds": 12,
        "accent": "#F97316",
        "currency": "Risas",
        "currencyIcon": "JA",
        "shop": "La Trastienda de Nora",
        "signature": "Cartas enfrentadas, aplausómetro y un jurado que ya se arrepintió",
        "steps": [
            "Lee la frase y escribe tu respuesta",
            "Vota la respuesta que más te haga reír; no puedes votar la tuya",
            "La ronda final vale el doble y el podio se lleva la gloria",
        ],
    },
    "vox_populi": {
        "id": "vox_populi",
        "title": "Vox Populi",
        "tagline": "La gente dijo cosas raras",
        "description": "Un noticiero de encuestas donde gana quien piensa como la mayoría. "
                       "Cada ronda lanza una pregunta y todos intentan adivinar la respuesta "
                       "más popular. No importa la verdad: importa lo que dijo la gente.",
        "minPlayers": 2,
        "maxPlayers": 8,
        "approxMinutes": 22,
        "rounds": 12,
        "accent": "#22D3EE",
        "currency": "Rating",
        "currencyIcon": "TV",
        "shop": "La Mesa de Control",
        "signature": "Noticiero en vivo, encuestas y un cintillo que nunca duerme",
        "steps": [
            "Lee la encuesta y escribe la respuesta que crees más popular",
            "El servidor revela el top de respuestas y reparte puntos por ranking",
            "La ronda final vale doble; sobrevive al juicio de la mayoría",
        ],
    },
    "fuera_de_epoca": {
        "id": "fuera_de_epoca",
        "title": "Fuera de Época",
        "tagline": "¿En qué año pasó?",
        "description": "El profesor Tic Tac viaja en el tiempo y pone a prueba tu memoria. "
                       "Cada ronda muestra un estreno, invento o momento cultural y tú eliges "
                       "el año. La racha multiplica los puntos.",
        "minPlayers": 2,
        "maxPlayers": 8,
        "approxMinutes": 22,
        "rounds": 12,
        "accent": "#A78BFA",
        "currency": "Cronones",
        "currencyIcon": "TT",
        "shop": "Chatarra Temporal",
        "signature": "Consola del tiempo, años gigantes y estática de otro siglo",
        "steps": [
            "Lee el evento y elige el año correcto entre cuatro opciones",
            "Acierta para sumar; la racha paga un bonus extra",
            "La ronda final vale doble y el podio se decide por precisión",
        ],
    },
    "texto_roto": {
        "id": "texto_roto",
        "title": "Texto Roto",
        "tagline": "El chat que nadie pidió",
        "description": "Una conversación empieza bien y todos la arruinan. Cada jugador escribe "
                       "una línea para su personaje; después la sala vota la intervención más "
                       "graciosa. El resultado se lee en la tele como un chat real.",
        "minPlayers": 3,
        "maxPlayers": 8,
        "approxMinutes": 26,
        "rounds": 8,
        "accent": "#34D399",
        "currency": "Datos",
        "currencyIcon": "01",
        "shop": "Modo Dios",
        "signature": "Un chat a pantalla completa con burbujas, stickers y glitches",
        "steps": [
            "Lee la escena y tu papel; escribe tu línea",
            "La tele arma el chat con todas las intervenciones",
            "Vota la línea más graciosa; no puedes votar la tuya",
        ],
    },
    "franela_fest": {
        "id": "franela_fest",
        "title": "Franela Fest",
        "tagline": "Dibuja, esloganiza y vende",
        "description": "El concurso de camisetas más dudoso de la televisión. Dibuja en tu teléfono, "
                       "escribe un eslogan y convence a la sala de que tu diseño merece existir. "
                       "La moda es subjetiva; la vergüenza también.",
        "minPlayers": 3,
        "maxPlayers": 8,
        "approxMinutes": 28,
        "rounds": 6,
        "accent": "#F472B6",
        "currency": "Hilos",
        "currencyIcon": "HF",
        "shop": "La Boutique del Mal Gusto",
        "signature": "Pasarela, flashes, maniquíes y un jurado snob",
        "steps": [
            "Dibuja tu camiseta en el teléfono y escribe un eslogan",
            "La tele muestra los diseños de forma anónima",
            "Vota el diseño que te pondrías o el que regalarías por venganza",
        ],
    },
    "el_tasador": {
        "id": "el_tasador",
        "title": "El Tasador",
        "tagline": "Todo tiene un precio",
        "description": "Objetos raros, pitches dudosos y un valor que nadie conoce. Convence a la "
                       "sala de que tu objeto vale una fortuna y acierta cuánto vale de verdad. "
                       "El mercado del absurdo está abierto.",
        "minPlayers": 2,
        "maxPlayers": 8,
        "approxMinutes": 26,
        "rounds": 10,
        "accent": "#FBBF24",
        "currency": "Fichas",
        "currencyIcon": "$$",
        "shop": "La Bodega de Objetos Malditos",
        "signature": "Casa de subastas, cortinas, martillo y etiquetas de precio",
        "steps": [
            "Mira el objeto raro y escribe tu pitch con un valor",
            "La sala vota el pitch más convincente",
            "El valor real se revela: el pitch más cercano también suma",
        ],
    },
}

HOST_BY_GAME = {
    "ultima_palabra": "nora",
    "vox_populi": "chisme",
    "fuera_de_epoca": "tictac",
    "texto_roto": "maquina",
    "franela_fest": "critico",
    "el_tasador": "tasador",
}

# Frases escritas (no sonoras) que cambian cada ronda para que la tele no diga
# siempre lo mismo. La voz sigue siendo una sola toma por momento; esto es el
# «crawl» del plató, no un reemplazo de la locución.
BANTER_BY_GAME: dict[str, dict[str, tuple[str, ...]]] = {
    "ultima_palabra": {
        "write": ("La sala afila el lápiz.", "Hoy sí se van a lucir.", "El jurado ya se arrepiente."),
        "vote": ("Aquí se premia la ocurrencia, no la cordura.", "Ríanse, pero voten.", "La risa no se puede fingir… o sí."),
        "reveal": ("La última palabra la tiene la sala.", "Se viene el veredicto.", "Nadie dijo que fuera justo."),
    },
    "vox_populi": {
        "guess": ("La gente ya contestó. Ahora adivinen qué dijo.", "El pueblo habló; entiendan el acento.", "La mayoría no siempre tiene razón, pero sí votos."),
        "reveal": ("El noticiero abre el sobre.", "Esto es lo que dijo la calle.", "La encuesta no perdona."),
    },
    "fuera_de_epoca": {
        "guess": ("La máquina del tiempo se calienta.", "Un año, una apuesta, cero red de seguridad.", "El pasado no se acuerda de ustedes."),
        "reveal": ("El reloj marca la verdad.", "Viajaron… a la respuesta correcta o al error.", "La historia toma notas."),
    },
    "texto_roto": {
        "write": ("El chat se está incendiando.", "Todos escriben a la vez. ¿Qué podría salir mal?", "La conversación ya perdió la cordura."),
        "vote": ("Lean el desastre y voten su línea favorita.", "Aquí no hay respuestas correctas, solo caos.", "El hilo se rompió; elijan el mejor pedazo."),
        "reveal": ("La conversación queda en evidencia.", "El chat ya es historia.", "Nadie va a guardar estos mensajes."),
    },
    "franela_fest": {
        "create": ("El lienzo espera. La moda también.", "Dibujen como si nadie los viera.", "La pasarela del absurdo está abierta."),
        "vote": ("Elijan la camiseta que se pondrían… o que regalarían.", "La crítica de moda ya se está riendo.", "Diseño, eslogan y vergüenza."),
        "reveal": ("La pasarela dicta sentencia.", "La moda es subjetiva; el ridículo es universal.", "El jurado de la camiseta habló."),
    },
    "el_tasador": {
        "pitch": ("El objeto no tiene precio… hasta que alguien lo dice.", "Vendan humo con seguridad.", "La tasación será cruel."),
        "vote": ("¿Quién vendió mejor la moto?", "El mejor pitch se lleva la gloria.", "La cartera ya está temblando."),
        "reveal": ("El martillo tiene la última palabra.", "El valor real no respeta egos.", "Se abre la tasación."),
    },
}

SCREEN_BY_PHASE = {
    "lobby": "lobby",
    "countdown": "countdown",
    "podium": "podium",
    "reveal": "reveal",
}

PHASE_FOR_GAME = {
    "ultima_palabra": ("write", "duel"),
    "vox_populi": ("guess",),
    "fuera_de_epoca": ("sort",),
    "texto_roto": ("chat", "react"),
    "franela_fest": ("create", "remix", "vote"),
    "el_tasador": ("pitch", "invest"),
}

DURATIONS = {
    # Cada juego tiene un ritmo distinto: el duelo es corto, la cadena de
    # encuestas respira, el chat se juega por turnos y la subasta es lenta.
    # Fases activas reales + fases reservadas para la siguiente capa.
    "ultima_palabra": {"write": 55, "duel": 20, "reveal": 9},
    "vox_populi": {"guess": 45, "vote": 25, "reveal": 9},
    "fuera_de_epoca": {"guess": 45, "sort": 50, "reveal": 9},
    "texto_roto": {"chat": 18, "vote": 30, "reveal": 9},
    "franela_fest": {"create": 55, "remix": 40, "vote": 30, "reveal": 9},
    "el_tasador": {"pitch": 50, "invest": 30, "reveal": 9},
    "bonus": {"bonus": 14, "bonus_reveal": 6},
}

# Cada subturno del chat tiene un colchón para el polling HTTP (~700 ms):
# 3 subturnos × 18 s = 54 s de fase `chat` como mínimo.
CHAT_SUBTURN_SECONDS = 18.0

# Presupuesto de la subasta de El Tasador. No son las monedas de la tienda:
# es capital de la ronda y no se puede retirar.
AUCTION_BUDGET = 500
LEVENSHTEIN_MAX = 2
BOT_PLAYER = "bot:nora"
BOT_SELLER = "bot:tasador"
WAIT_BONUS_COINS = 25
AUDIENCE_BONUS = 150
ALL_IN_PERK = {
    "id": "all_in_boost",
    "title": "Todo o Nada Final",
    "description": "Entrega todo tu saldo: +0.5x por cada 250 monedas en la ronda final (tope +1.5x).",
    "cost": 0,
}

# Vueltas de guion que cambian el ritmo de una ronda sin exigir contenido nuevo.
# El servidor las elige de forma determinista por sala/ronda y las aplica a puntos,
# reloj y monedas. La tele y el teléfono solo pintan lo que manda el snapshot.
TWISTS: tuple[dict[str, Any], ...] = (
    {
        "id": "clean",
        "title": "RONDA LIMPIA",
        "description": "Sin trucos: la ronda se juega tal cual.",
        "multiplier": 1.0,
        "duration": 1.0,
        "coin": 1.0,
    },
    {
        "id": "double",
        "title": "DOBLE O NADA",
        "description": "Todo lo que sumes en esta ronda vale el doble.",
        "multiplier": 2.0,
        "duration": 1.0,
        "coin": 2.0,
    },
    {
        "id": "rush",
        "title": "MINUTO LOCO",
        "description": "Menos tiempo para escribir, pero cada punto vale 1.5.",
        "multiplier": 1.5,
        "duration": 0.68,
        "coin": 1.5,
    },
    {
        "id": "golden",
        "title": "LLUVIA DE MONEDAS",
        "description": "Se juega normal, pero las monedas de la ronda se duplican.",
        "multiplier": 1.0,
        "duration": 1.0,
        "coin": 2.0,
    },
    {
        "id": "sudden",
        "title": "EL PRIMERO GANA",
        "description": "El primero que envía su respuesta se lleva 300 extra.",
        "multiplier": 1.0,
        "duration": 1.0,
        "coin": 1.0,
    },
    {
        "id": "comeback",
        "title": "REMONTADA",
        "description": "Quien va último al empezar la ronda recibe 300 extra al cerrar.",
        "multiplier": 1.0,
        "duration": 1.0,
        "coin": 1.0,
    },
)

# Cada juego tiene sus propios giros con nombre y sabor. El motor comparte los
# efectos (multiplicador, reloj, monedas, primero, remontada), pero la tele y el
# teléfono leen una promesa distinta en cada partida.
TWISTS_BY_GAME: dict[str, tuple[dict[str, Any], ...]] = {
    "ultima_palabra": (
        {"id": "clean", "title": "RONDA SIN RED", "description": "Nora no sopla nada: puro ingenio.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "double", "title": "DOBLE RISA", "description": "Cada voto vale el doble. Que valga la pena.", "multiplier": 2.0, "duration": 1.0, "coin": 2.0},
        {"id": "rush", "title": "SIN RESPIRO", "description": "Menos tiempo para pensar. Más tiempo para arrepentirse.", "multiplier": 1.5, "duration": 0.68, "coin": 1.5},
        {"id": "golden", "title": "LLUVIA DE PROPINAS", "description": "Las monedas de la ronda se duplican. La dignidad no.", "multiplier": 1.0, "duration": 1.0, "coin": 2.0},
        {"id": "sudden", "title": "EL PRIMERO QUE ESCRIBE", "description": "La primera respuesta enviada se lleva 300 extra.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "comeback", "title": "EL ÚLTIMO SE LEVANTA", "description": "Quien va último al empezar la ronda cobra 300 al cerrar.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
    ),
    "vox_populi": (
        {"id": "clean", "title": "ENCUESTA LIMPIA", "description": "Sin manipulación: la calle habló.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "double", "title": "RATING DOBLE", "description": "Todo lo que sumes vale el doble. La audiencia exige.", "multiplier": 2.0, "duration": 1.0, "coin": 2.0},
        {"id": "rush", "title": "ÚLTIMA HORA", "description": "El noticiero cierra antes. Respondan rápido.", "multiplier": 1.5, "duration": 0.68, "coin": 1.5},
        {"id": "golden", "title": "PATROCINADO", "description": "Las monedas se duplican. Alguien pagó por esto.", "multiplier": 1.0, "duration": 1.0, "coin": 2.0},
        {"id": "sudden", "title": "PRIMICIA EXCLUSIVA", "description": "La primera respuesta enviada se lleva 300 extra.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "comeback", "title": "REPORTAJE DE RESCATE", "description": "Quien va último al empezar la ronda cobra 300 al cerrar.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
    ),
    "fuera_de_epoca": (
        {"id": "clean", "title": "LÍNEA TEMPORAL ESTABLE", "description": "La máquina funciona. Por ahora.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "double", "title": "PARADOJA DOBLE", "description": "Cada acierto vale el doble. El universo protesta.", "multiplier": 2.0, "duration": 1.0, "coin": 2.0},
        {"id": "rush", "title": "SALTO INMINENTE", "description": "El portal se cierra antes. Elijan rápido.", "multiplier": 1.5, "duration": 0.68, "coin": 1.5},
        {"id": "golden", "title": "RELÁMPAGO TEMPORAL", "description": "Las monedas se duplican. No pregunten de dónde salen.", "multiplier": 1.0, "duration": 1.0, "coin": 2.0},
        {"id": "sudden", "title": "EL PRIMER VIAJERO", "description": "La primera respuesta enviada se lleva 300 extra.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "comeback", "title": "ABUELO PARADOJA", "description": "Quien va último al empezar la ronda cobra 300 al cerrar.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
    ),
    "texto_roto": (
        {"id": "clean", "title": "CHAT NORMAL", "description": "Nadie va a arruinar la conversación. Ja.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "double", "title": "DOBLE MENSAJE", "description": "Cada línea vale el doble. El grupo se incendia.", "multiplier": 2.0, "duration": 1.0, "coin": 2.0},
        {"id": "rush", "title": "MODO TURBO", "description": "El chat va rápido. Escriban antes de que los bloqueen.", "multiplier": 1.5, "duration": 0.68, "coin": 1.5},
        {"id": "golden", "title": "VIRAL", "description": "Las monedas se duplican. Alguien hizo captura.", "multiplier": 1.0, "duration": 1.0, "coin": 2.0},
        {"id": "sudden", "title": "PRIMER MENSAJE", "description": "La primera respuesta enviada se lleva 300 extra.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "comeback", "title": "ÚLTIMO EN LÍNEA", "description": "Quien va último al empezar la ronda cobra 300 al cerrar.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
    ),
    "franela_fest": (
        {"id": "clean", "title": "PASARELA LIMPIA", "description": "Sin trucos: solo tela y vergüenza.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "double", "title": "TEMPORADA ALTA", "description": "Cada voto vale el doble. La moda es cara.", "multiplier": 2.0, "duration": 1.0, "coin": 2.0},
        {"id": "rush", "title": "CIERRE DE PASARELA", "description": "Menos tiempo para dibujar. Más tiempo para llorar.", "multiplier": 1.5, "duration": 0.68, "coin": 1.5},
        {"id": "golden", "title": "EDICIÓN LIMITADA", "description": "Las monedas se duplican. Solo hay una talla.", "multiplier": 1.0, "duration": 1.0, "coin": 2.0},
        {"id": "sudden", "title": "PRIMER BOCETO", "description": "La primera respuesta enviada se lleva 300 extra.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "comeback", "title": "COLECCIÓN DE RESCATE", "description": "Quien va último al empezar la ronda cobra 300 al cerrar.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
    ),
    "el_tasador": (
        {"id": "clean", "title": "SUBASTA LIMPIA", "description": "El martillo no tiene favoritos. Mentira.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "double", "title": "PUJA DOBLE", "description": "Cada punto vale el doble. La cartera sufre.", "multiplier": 2.0, "duration": 1.0, "coin": 2.0},
        {"id": "rush", "title": "MARTILLO RÁPIDO", "description": "El lote se cierra antes. Nadie quiere pujar tarde.", "multiplier": 1.5, "duration": 0.68, "coin": 1.5},
        {"id": "golden", "title": "COMISIÓN OCULTA", "description": "Las monedas se duplican. No pregunten por la factura.", "multiplier": 1.0, "duration": 1.0, "coin": 2.0},
        {"id": "sudden", "title": "PRIMERA PUJA", "description": "La primera respuesta enviada se lleva 300 extra.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
        {"id": "comeback", "title": "LOTE DE RESCATE", "description": "Quien va último al empezar la ronda cobra 300 al cerrar.", "multiplier": 1.0, "duration": 1.0, "coin": 1.0},
    ),
}

# Chanchullos de la tienda: una ayuda y una burla por juego. Cuestan monedas y
# se resuelven en el servidor; la tele solo pinta lo que el servidor autoriza.
HINTS: dict[str, str] = {
    "ultima_palabra": "Nora sopló: piensa en algo que haría reír a tu mamá y escandalizaría a tu abuela.",
    "vox_populi": "Don Chisme filtró: la respuesta ganadora suele ser la más corta y la más obvia para la mayoría.",
    "fuera_de_epoca": "El profesor Tic Tac ajustó la máquina: dos años imposibles desaparecieron de tu teléfono.",
    "texto_roto": "La Máquina sugiere: empieza con «no es por nada, pero…» y deja que el chat se encargue.",
    "franela_fest": "El Crítico sugiere: dibuja un objeto cotidiano con cara de culpa. La moda premia el sufrimiento.",
    "el_tasador": "El Tasador sopló: el valor real está más cerca de lo que crees. La mitad de tu primera cifra, quizá.",
}

TAUNTS: dict[str, tuple[str, ...]] = {
    "ultima_palabra": ("JA", "SE FUE", "AY, NO", "SIN GRACIA", "ESO DUELE"),
    "vox_populi": ("FUENTE: CRÉEME", "LA GENTE MIENTE", "DATOS RAROS", "ENCUESTA AMAÑADA"),
    "fuera_de_epoca": ("VIAJASTE MAL", "ESO NO PASÓ", "OTRO SIGLO", "RELÓJATE"),
    "texto_roto": ("JAJAJA", "BLOQUEADO", "VISTO", "ESCRIBIENDO…", "LO LEYÓ TODO EL GRUPO"),
    "franela_fest": ("ESO NO ES MODA", "SIN TALLA", "A LA HOGUERA", "MUY 2019"),
    "el_tasador": ("VENDIDO", "ESO VALE CERO", "SUBASTA FALSA", "MARTILLO YA"),
}

HINT_COST = 120
TAUNT_COST = 60

# Tienda entre rondas. Se paga en monedas y se aplica a la ronda siguiente.
PERKS: tuple[dict[str, Any], ...] = (
    {
        "id": "double",
        "title": "Doble puntos",
        "description": "Tus puntos de la próxima ronda valen x2.",
        "cost": 300,
    },
    {
        "id": "steal",
        "title": "Robo de bolsillo",
        "description": "Al cerrar la próxima ronda le quitas 200 al líder.",
        "cost": 250,
    },
    {
        "id": "insurance",
        "title": "Seguro de fiesta",
        "description": "Si no sumas nada en la próxima ronda, igual cobras 300.",
        "cost": 150,
    },
)

FIESTA_START_COINS = 400
FIESTA_MAX_ROUNDS = 12
BONUS_EVERY = 2
BONUS_ROUNDS_MIN = 6

# Listas deliberadamente cortas. En modo familiar se bloquean; en modo sin
# filtro se permite el lenguaje adulto, pero nunca el odio ni el acoso.
FAMILY_BLOCK = frozenset({
    "puta", "puto", "pendejo", "pendeja", "mierda", "cabron", "cabrona",
    "verga", "chinga", "chingar", "chingado", "joder", "culero", "culera",
    "zorra", "polla", "pito", "mamada", "mamon", "mamona", "cojer", "coger",
})

HATE_BLOCK = frozenset({
    "marica", "maricon", "sudaca", "indio", "prieto", "nazi", "retrasado",
    "mongol", "feminazi", "terrorista",
})

_CONTROL = re.compile(r"[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e]")
_SPACES = re.compile(r"\s+")


def fold(value: Any) -> str:
    text = unicodedata.normalize("NFD", str(value or "").lower())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    text = "".join(ch if ch.isalnum() else " " for ch in text)
    return " ".join(text.split())


def _clean(value: Any, limit: int = 160) -> str:
    text = _CONTROL.sub(" ", str(value or ""))
    text = _SPACES.sub(" ", text).strip()
    return text[:limit]


def _rng(room_code: str, game: str, round_no: int, salt: str = "") -> random.Random:
    return random.Random(f"fiesta:{room_code}:{game}:{round_no}:{salt}")


def _pick(items: tuple[dict, ...] | list[dict], used: set[str], rng: random.Random) -> dict:
    available = [item for item in items if str(item.get("text") or item.get("event") or item.get("name") or item.get("prompt") or item.get("title")) not in used]
    if not available:
        available = list(items)
    choice = rng.choice(available)
    key = str(choice.get("text") or choice.get("event") or choice.get("name") or choice.get("prompt") or choice.get("title"))
    used.add(key)
    return dict(choice)


class FiestaRoom:
    """Estado y reglas de un juego de fiesta dentro de una sala de `games.Room`."""

    def __init__(self, room: Any, game: str, rounds: int | None = None, solo_test: bool = False) -> None:
        self.room = room
        self.game = game
        self.rounds = max(1, min(FIESTA_MAX_ROUNDS, int(rounds or FIESTA_INFO[game]["rounds"])))
        self.solo_test = bool(solo_test)
        self.phase = "lobby"
        self.phase_started = 0.0
        self.round_index = 0
        self.data: dict[str, Any] = {}
        self.submitted: dict[str, Any] = {}
        self.votes: dict[str, str] = {}
        self.options: list[dict[str, Any]] = []
        self.reveal: dict[str, Any] = {}
        self.last_points: dict[str, int] = {}
        self.streaks: dict[str, int] = {}
        self.roles: dict[str, str] = {}
        self.used: set[str] = set()
        self._podium_done = False
        # Capa de profundidad: giro de ronda, tienda de monedas y minijuego.
        self.twist: dict[str, Any] = dict(TWISTS[0])
        self.bought_perks: dict[str, str] = {}
        self.active_perks: dict[str, str] = {}
        self._perks_settled = False
        self.submitted_order: list[str] = []
        self.bonus: dict[str, Any] | None = None
        self.bonus_choices: dict[str, int] = {}
        self.bonus_results: list[dict[str, Any]] = []
        self._bonus_done = False
        self.round_start_last = ""
        # Apuestas, soplos y burlas: la capa de economía/irreverencia por ronda.
        self.bets: dict[str, int] = {}
        self.bet_results: dict[str, dict[str, Any]] = {}
        self.hint_used: dict[str, bool] = {}
        self.taunts: list[dict[str, Any]] = []
        self.last_host_variant: dict[str, int] = {}
        # Fiesta 4.0: estado de los bucles nuevos.
        self.duel_pairs: list[dict[str, Any]] = []
        self.active_duel_index = 0
        self.duel_votes: dict[str, str] = {}
        self.duel_results: list[dict[str, Any]] = []
        self.vox_quick_chips: dict[str, list[str]] = {}
        self.vox_guess_results: list[dict[str, Any]] = []
        self.vox_hilo_votes: dict[str, str] = {}
        self.vox_mystery: dict[str, Any] | None = None
        self.vox_rank3: dict[str, Any] | None = None
        self.sort_items: list[dict[str, Any]] = []
        self.sort_years: dict[str, int] = {}
        self.chat_subturn = 0
        self.chat_subturn_started = 0.0
        self.chat_messages: list[dict[str, Any]] = []
        self.chat_roles: dict[str, str] = {}
        self.chat_goals: dict[str, str] = {}
        self.chat_submitted: set[str] = set()
        self.chat_reacted: set[str] = set()
        self.remix_drawings: list[dict[str, Any]] = []
        self.remix_slogans: list[dict[str, Any]] = []
        self.remix_pools: dict[str, dict[str, Any]] = {}
        self.remix_choices: dict[str, dict[str, str]] = {}
        self.sellers: list[str] = []
        self.lots: dict[str, dict[str, Any]] = {}
        self.investments: dict[str, dict[str, Any]] = {}
        self.audience_votes: dict[str, str] = {}
        self.incoming_taunts: dict[str, dict[str, Any]] = {}
        self.final_boost: dict[str, float] = {}
        self.wait_bonus: set[str] = set()
        self.reaction_banner = ""
        self.mimic_hit = False
        self.seller_turn = 0

    # -- helpers ---------------------------------------------------------
    @property
    def info(self) -> dict[str, Any]:
        return FIESTA_INFO[self.game]

    def _players(self) -> list[Any]:
        return [
            player for player in sorted(self.room.players.values(), key=lambda p: p.joined_at)
            if player.connected and not player.spectator
        ]

    def _all_submitted(self) -> bool:
        players = self._players()
        return bool(players) and all(player.id in self.submitted for player in players)

    def _all_voted(self) -> bool:
        players = self._players()
        return bool(players) and all(player.id in self.votes for player in players)

    def _sellers_submitted(self) -> bool:
        humans = [pid for pid in self.sellers if pid in self.room.players]
        return bool(humans) and all(pid in self.submitted for pid in humans)

    def _all_duel_voted(self) -> bool:
        pair = self._active_duel()
        if pair is None:
            return True
        eligible = [
            player.id for player in self._players()
            if player.id not in (pair["player_a"], pair["player_b"])
        ]
        if len(self._players()) <= 2 or self.solo_test:
            eligible = [player.id for player in self._players()]
        return bool(eligible) and all(pid in self.duel_votes for pid in eligible)

    def _bump(self, now: float) -> None:
        # El robo de bolsillo se liquida una sola vez, al entrar al reveal, para
        # que la tele y los teléfonos vean el marcador ya corregido.
        if self.phase == "reveal" and not self._perks_settled:
            self._settle_perks()
        if self.phase == "reveal":
            self._compute_reaction_banner()
        self.room.phase = self.phase
        self.room.phase_started = self.phase_started
        self.room.round_index = self.round_index
        self.room.deltas = dict(self.last_points)
        self.room.last_activity = now
        self.room.rev += 1

    def _award(self, player_id: str, points: int) -> None:
        player = self.room.players.get(player_id)
        if player is None:
            return
        points = int(round(points))
        if points > 0 and self.active_perks.get(player_id) == "double":
            points *= 2
        if points > 0 and self.round_index == self.rounds:
            points = int(round(points * (1.0 + float(self.final_boost.get(player_id, 0.0)))))
        player.score = max(0, player.score + points)
        self.last_points[player_id] = self.last_points.get(player_id, 0) + points
        # Economía paralela: cada 100 puntos producen una moneda (mínimo 1 si
        # hubo puntos). El giro «Lluvia de monedas» duplica la cosecha.
        if points > 0:
            coins = max(1, points // 100)
            coins = int(round(coins * float(self.twist.get("coin", 1.0))))
            player.coins = int(player.coins) + coins

    def _multiplier(self) -> float:
        """La última ronda paga el doble y el giro de la ronda puede multiplicar."""
        base = 2.0 if self.round_index == self.rounds else 1.0
        return base * float(self.twist.get("multiplier", 1.0))

    def _settle_perks(self) -> None:
        """Aplica los efectos de la tienda que se cobran al cerrar la ronda."""
        self._perks_settled = True
        for player_id, perk in list(self.active_perks.items()):
            if perk != "steal":
                continue
            player = self.room.players.get(player_id)
            if player is None:
                continue
            leader = max(
                (p for p in self._players() if p.id != player_id),
                key=lambda p: (p.score, -p.joined_at),
                default=None,
            )
            if leader is None or leader.score <= 0:
                continue
            amount = min(200, leader.score)
            leader.score -= amount
            player.score += amount
            self.last_points[player_id] = self.last_points.get(player_id, 0) + amount
            self.last_points[leader.id] = self.last_points.get(leader.id, 0) - amount
            if isinstance(self.reveal, dict):
                lines = self.reveal.setdefault("lines", [])
                if isinstance(lines, list):
                    lines.append(f"Robo de bolsillo: {player.name} le quitó {amount} a {leader.name}")
        # El seguro paga a quien se quedó sin puntos en la ronda.
        for player_id, perk in list(self.active_perks.items()):
            if perk != "insurance":
                continue
            if self.last_points.get(player_id, 0) <= 0:
                self._award(player_id, 300)
                if isinstance(self.reveal, dict):
                    lines = self.reveal.setdefault("lines", [])
                    if isinstance(lines, list):
                        lines.append(f"Seguro de fiesta: {self.room.players[player_id].name} cobra 300")
        twist = str(self.twist.get("id", ""))
        if twist == "sudden" and self.submitted_order:
            first = self.submitted_order[0]
            self._award(first, 300)
            if isinstance(self.reveal, dict):
                lines = self.reveal.setdefault("lines", [])
                if isinstance(lines, list):
                    player = self.room.players.get(first)
                    lines.append(f"El primero en responder: {player.name if player else 'Jugador'} +300")
        elif twist == "comeback" and self.round_start_last:
            self._award(self.round_start_last, 300)
            if isinstance(self.reveal, dict):
                lines = self.reveal.setdefault("lines", [])
                if isinstance(lines, list):
                    player = self.room.players.get(self.round_start_last)
                    lines.append(f"Remontada: {player.name if player else 'Jugador'} +300")
        self._settle_bets()

    def _settle_bets(self) -> None:
        """Cobra las apuestas de la ronda: 2x con puntos, 3x si además fue el líder."""
        if not self.bets:
            return
        round_points = {pid: int(self.last_points.get(pid, 0)) for pid in self.bets}
        all_points = sorted(
            (int(self.last_points.get(player.id, 0)) for player in self._players()),
            reverse=True,
        )
        best = all_points[0] if all_points else 0
        cutoff = all_points[max(0, math.ceil(len(all_points) / 2) - 1)] if all_points else 0
        lines = self.reveal.setdefault("lines", []) if isinstance(self.reveal, dict) else None
        for player_id, stake in self.bets.items():
            player = self.room.players.get(player_id)
            if player is None:
                continue
            points = round_points.get(player_id, 0)
            if self.solo_test:
                in_top_half = points >= 300
            else:
                in_top_half = points > 0 and points >= cutoff
            won = points > 0 and in_top_half
            multiplier = 3 if won and points == best and best > 0 else (2 if won else 0)
            payout = int(stake) * multiplier
            if payout:
                player.coins = int(player.coins) + payout
            self.bet_results[player_id] = {
                "stake": int(stake),
                "multiplier": multiplier,
                "payout": payout,
                "won": bool(won),
            }
            if isinstance(lines, list):
                if won:
                    lines.append(
                        f"Apuesta de {player.name}: arriesgó {stake} y cobró {payout} (x{multiplier})"
                    )
                else:
                    lines.append(f"Apuesta de {player.name}: perdió {stake} monedas")

    def _filter_text(self, value: Any, limit: int = 160) -> str:
        text = _clean(value, limit)
        if not text:
            return ""
        words = set(fold(text).split())
        blocked = HATE_BLOCK | (FAMILY_BLOCK if self.room.family_mode else frozenset())
        if words & blocked:
            return ""
        return text

    def _content_pool(self, items: tuple[dict, ...] | list[dict], salt: str) -> tuple[dict, ...] | list[dict]:
        if self.room.family_mode:
            safe = tuple(item for item in items if not item.get("adult"))
            return safe or tuple(items)
        adult = tuple(item for item in items if item.get("adult"))
        if adult and _rng(self.room.code, self.game, self.round_index, salt).random() < 0.4:
            return adult
        return tuple(items)

    def _round_no(self) -> int:
        # `round_index` es 1-based dentro de Fiesta (la ronda 1 ya está activa
        # cuando se elige el contenido).
        return self.round_index

    def _duration(self, phase: str | None = None) -> float:
        phase = phase or self.phase
        if phase == "countdown":
            return float(self.room.countdown_seconds)
        if phase == "podium":
            return 0.0
        if phase.startswith("bonus"):
            return float(DURATIONS["bonus"].get(phase, 0.0))
        base = float(DURATIONS.get(self.game, {}).get(phase, 0.0))
        if phase in ("write", "guess", "create", "pitch", "vote", "duel", "sort", "chat", "remix", "invest"):
            base *= float(self.twist.get("duration", 1.0))
        return max(8.0, base)

    def _host_cue(self) -> dict[str, Any]:
        phase = self.phase
        moment = {
            "lobby": "lobby", "countdown": "start",
            "write": "write", "guess": "guess", "create": "create", "pitch": "pitch",
            "duel": "vote", "sort": "guess", "chat": "write", "remix": "create",
            "invest": "vote", "vote": "vote", "reveal": "reveal", "podium": "podium",
        }.get(phase, "intro")
        lines = content.host_lines(self.game, moment)
        if not lines:
            return {"voice": HOST_BY_GAME[self.game], "moment": moment, "text": "", "variant": 0, "audio": ""}
        # Varias tomas por momento, sin repetir la anterior en la misma sala. El
        # texto que se muestra es exactamente el que suena (misma variante).
        rng = _rng(self.room.code, self.game, self.round_index, f"voice:{self.phase}:{moment}")
        variant = rng.randrange(len(lines))
        previous = self.last_host_variant.get(moment)
        if len(lines) > 1 and variant == previous:
            variant = (variant + 1) % len(lines)
        self.last_host_variant[moment] = variant
        text = lines[variant]
        return {
            "voice": HOST_BY_GAME[self.game],
            "moment": moment,
            "text": text,
            "variant": variant,
            "audio": f"fiesta_{self.game}_{moment}_{variant}",
        }

    # -- ciclo -----------------------------------------------------------
    def start(self, now: float) -> bool:
        if self.phase != "lobby":
            return False
        players = self._players()
        minimum = 1 if self.solo_test else int(self.info["minPlayers"])
        if len(players) < minimum:
            return False
        self.phase = "countdown"
        self.phase_started = now
        self.round_index = 0
        self.last_points = {}
        self.twist = dict(TWISTS[0])
        self.bought_perks = {}
        self.active_perks = {}
        self.bonus = None
        self.bonus_choices = {}
        self.bonus_results = []
        self.bets = {}
        self.bet_results = {}
        self.hint_used = {}
        self.taunts = []
        self._reset_round_state()
        self.final_boost = {}
        for player in players:
            player.coins = FIESTA_START_COINS
        self.room.phase = "countdown"
        self.room.phase_started = now
        self.room.started_at = now
        self.room.round_index = 0
        self._bump(now)
        return True

    def next(self, now: float) -> bool:
        if self.phase not in ("reveal", "podium"):
            return False
        if self.phase == "podium":
            return False
        self._advance(now)
        return True

    def tick(self, now: float) -> None:
        if self.phase == "closed":
            return
        if self.phase == "countdown":
            if now - self.phase_started >= self._duration("countdown"):
                self._begin_round(now)
            return
        if self.phase in ("write", "guess", "create", "pitch"):
            complete = self._sellers_submitted() if self.game == "el_tasador" else self._all_submitted()
            if complete or now - self.phase_started >= self._duration():
                self._finish_active(now)
            return
        if self.phase == "sort":
            if self._all_submitted() or now - self.phase_started >= self._duration():
                self._finish_sort(now)
            return
        if self.phase == "remix":
            if all(player.id in self.remix_choices for player in self._players()) or now - self.phase_started >= self._duration("remix"):
                self._finish_franela_remix(now)
            return
        if self.phase == "duel":
            if self._all_duel_voted() or now - self.phase_started >= self._duration("duel"):
                self._finish_duel(now)
            return
        if self.phase == "chat":
            writers = [pid for pid, role in self.chat_roles.items() if role == "WRITER"]
            all_writers = bool(writers) and all(pid in self.chat_submitted for pid in writers)
            if all_writers or now - self.chat_subturn_started >= CHAT_SUBTURN_SECONDS:
                self._advance_chat_subturn(now)
            return
        if self.phase == "invest":
            buyers = list(self.data.get("buyers") or [])
            all_invested = bool(buyers) and all(pid in self.investments for pid in buyers)
            if all_invested or now - self.phase_started >= self._duration("invest"):
                self._finish_invest(now)
            return
        if self.phase == "vote":
            if self._all_voted() or now - self.phase_started >= self._duration():
                self._finish_vote(now)
            return
        if self.phase == "reveal":
            if now - self.phase_started >= self._duration("reveal"):
                self._advance(now)
            return
        if self.phase == "bonus":
            players = self._players()
            if now - self.phase_started >= self._duration("bonus") or (
                players and all(player.id in self.bonus_choices for player in players)
            ):
                self._finish_bonus(now)
            return
        if self.phase == "bonus_reveal":
            if now - self.phase_started >= self._duration("bonus_reveal"):
                self._advance(now)
            return

    def _reset_round_state(self) -> None:
        self.duel_pairs = []
        self.active_duel_index = 0
        self.duel_votes = {}
        self.duel_results = []
        self.vox_quick_chips = {}
        self.vox_guess_results = []
        self.vox_hilo_votes = {}
        self.vox_mystery = None
        self.vox_rank3 = None
        self.sort_items = []
        self.sort_years = {}
        self.chat_subturn = 0
        self.chat_subturn_started = 0.0
        self.chat_messages = []
        self.chat_roles = {}
        self.chat_goals = {}
        self.chat_submitted = set()
        self.chat_reacted = set()
        self.remix_drawings = []
        self.remix_slogans = []
        self.remix_pools = {}
        self.remix_choices = {}
        self.sellers = []
        self.lots = {}
        self.investments = {}
        self.audience_votes = {}
        self.incoming_taunts = {}
        self.wait_bonus = set()
        self.reaction_banner = ""
        self.mimic_hit = False

    def _setup_duels(self) -> None:
        players = self._players()
        n = max(1, len(players))
        pair_count = max(1, math.ceil(n / 2))
        pool = self._content_pool(content.ULTIMA_PALABRA_PROMPTS, "prompt")
        rng = _rng(self.room.code, self.game, self.round_index, "duel-prompts")
        prompts = []
        for _ in range(pair_count):
            prompts.append(_pick(pool, self.used, rng)["text"])
        self.duel_pairs = []
        self.data["duel_prompts"] = {}
        self.data["duel_prompts_public"] = {}
        bot_lines = getattr(content, "BOT_ANSWERS", ("Una respuesta de robot muy segura.",))
        for index in range(pair_count):
            a = players[index * 2].id if index * 2 < n else BOT_PLAYER
            b = players[index * 2 + 1].id if index * 2 + 1 < n else BOT_PLAYER
            if n == 1:
                b = BOT_PLAYER
            prompt = prompts[index]
            if a != BOT_PLAYER:
                self.data["duel_prompts"][a] = prompt
                self.data["duel_prompts_public"][a] = prompt
            if b != BOT_PLAYER:
                self.data["duel_prompts"][b] = prompt
                self.data["duel_prompts_public"][b] = prompt
            bot_answer = rng.choice(bot_lines) if b == BOT_PLAYER else ""
            self.duel_pairs.append({
                "duel_id": index,
                "prompt": prompt,
                "player_a": a,
                "player_b": b,
                "ans_a": "",
                "ans_b": bot_answer,
                "votes_a": [],
                "votes_b": [],
            })
        self.phase = "write"

    def _build_vox_chips(self, question: dict[str, Any]) -> dict[str, list[str]]:
        answers = [str(answer) for answer, _ in (question.get("answers") or [])]
        mid = answers[1:5] if len(answers) >= 5 else answers[:4]
        pool: list[str] = []
        for other in content.VOX_QUESTIONS:
            if other is question:
                continue
            for answer, _ in (other.get("answers") or []):
                text = str(answer)
                if text not in answers and text not in pool:
                    pool.append(text)
        rng = _rng(self.room.code, self.game, self.round_index, "vox-chips")
        rng.shuffle(pool)
        distractors = pool[:4]
        while len(distractors) < 4:
            distractors.append(f"opción rara {len(distractors) + 1}")
        chips = list(mid) + distractors
        result: dict[str, list[str]] = {}
        for player in self._players():
            local = list(chips)
            _rng(self.room.code, self.game, self.round_index, f"chips:{player.id}").shuffle(local)
            result[player.id] = local
        self.vox_quick_chips = result
        return result

    def _setup_time_guess(self) -> None:
        event = _pick(
            self._content_pool(content.TIME_EVENTS, "event"), self.used,
            _rng(self.room.code, self.game, self.round_index, "event"),
        )
        year = int(event["year"])
        age = max(5, 2026 - year)
        delta = max(2, int(age * 0.08))
        rng = _rng(self.room.code, self.game, self.round_index, "years-v4")
        offsets: list[int] = []
        magnitudes = list(range(max(1, delta), max(delta, 3 * delta) + 1))
        rng.shuffle(magnitudes)
        for magnitude in magnitudes:
            for sign in (-1, 1):
                offset = sign * magnitude
                candidate = year + offset
                if candidate == year or candidate < 1 or candidate > 2026 or offset in offsets:
                    continue
                offsets.append(offset)
                if len(offsets) == 3:
                    break
            if len(offsets) == 3:
                break
        if len(offsets) < 3:
            for magnitude in range(1, 31):
                for sign in (-1, 1):
                    offset = sign * magnitude
                    candidate = year + offset
                    if candidate == year or candidate < 1 or candidate > 2026 or offset in offsets:
                        continue
                    offsets.append(offset)
                    if len(offsets) == 3:
                        break
                if len(offsets) == 3:
                    break
        options = [year + offset for offset in offsets] + [year]
        rng.shuffle(options)
        correct = options.index(year)
        self.data["event"] = event
        self.data["years"] = options
        self.data["correct"] = correct
        self.phase = "guess"

    def _setup_sort(self) -> None:
        events = []
        for _ in range(3):
            events.append(_pick(
                self._content_pool(content.TIME_EVENTS, "event"), self.used,
                _rng(self.room.code, self.game, self.round_index, f"sort:{len(events)}"),
            ))
        items = []
        self.sort_years = {}
        for index, event in enumerate(events):
            item_id = f"s{index}"
            self.sort_years[item_id] = int(event["year"])
            items.append({"id": item_id, "title": str(event["event"])})
        _rng(self.room.code, self.game, self.round_index, "sort-shuffle").shuffle(items)
        self.sort_items = items
        self.data["sort_items"] = items
        self.data["sort_years"] = dict(self.sort_years)
        self.phase = "sort"

    def _assign_chat_subturn(self, now: float) -> None:
        players = self._players()
        self.chat_roles = {}
        self.chat_goals = {}
        self.chat_submitted = set()
        self.chat_reacted = set()
        self.chat_subturn_started = now
        if not players:
            return
        if len(players) == 1:
            writers = players
            reactors = []
        else:
            group_a = players[0::2]
            group_b = players[1::2]
            if self.chat_subturn % 2 == 0:
                writers, reactors = group_a, group_b
            else:
                writers, reactors = group_b, group_a
        for player in writers:
            self.chat_roles[player.id] = "WRITER"
        for player in reactors:
            self.chat_roles[player.id] = "REACTOR"
        goals = getattr(content, "SECRET_GOALS", ("Sé raro, pero con estilo.",))
        rng = _rng(self.room.code, self.game, self.round_index, f"goals:{self.chat_subturn}")
        for player in writers:
            self.chat_goals[player.id] = rng.choice(goals)

    def _setup_lots(self) -> None:
        players = self._players()
        n = len(players)
        if n == 0:
            self.sellers = [BOT_SELLER]
            buyers: list[str] = []
        elif n <= 2:
            seller = players[self.seller_turn % n].id
            self.sellers = [seller, BOT_SELLER]
            buyers = [p.id for p in players if p.id != seller]
            if not buyers and self.solo_test:
                buyers = [BOT_PLAYER]
        else:
            first = self.seller_turn % n
            second = (first + 1) % n
            self.sellers = [players[first].id, players[second].id]
            buyers = [p.id for p in players if p.id not in self.sellers]
        self.seller_turn += 1
        objects = []
        for index in range(2):
            objects.append(_pick(
                self._content_pool(content.TASADOR_OBJECTS, "object"), self.used,
                _rng(self.room.code, self.game, self.round_index, f"lot:{index}"),
            ))
        rng = _rng(self.room.code, self.game, self.round_index, "authenticity")
        self.lots = {}
        for index, lot_id in enumerate(("A", "B")):
            authentic = rng.random() < 0.5
            if index == 1 and not any(lot["authentic"] for lot in self.lots.values()):
                authentic = rng.random() < 0.5
            real = rng.randint(600, 1200) if authentic else rng.randint(50, 250)
            self.lots[lot_id] = {
                "id": lot_id,
                "object": objects[index],
                "authentic": authentic,
                "realValue": real,
                "seller": self.sellers[index] if index < len(self.sellers) else BOT_SELLER,
                "pitch": "",
                "invested": 0,
            }
        self.data["lots"] = {
            lot_id: {
                "id": lot_id,
                "object": _public_object(lot["object"]),
                "seller": lot["seller"],
                "sellerName": self._name_for(lot["seller"]),
                "suspicion": "Parece legítimo" if lot["authentic"] else "Huele raro",
            }
            for lot_id, lot in self.lots.items()
        }
        self.data["sellers"] = list(self.sellers)
        self.data["buyers"] = buyers

    def _vox_fold(self, value: Any) -> str:
        text = fold(value)
        stop = {"el", "la", "los", "las", "un", "una", "unos", "unas"}
        return " ".join(word for word in text.split() if word not in stop)

    def _levenshtein(self, a: str, b: str) -> int:
        if a == b:
            return 0
        if not a or not b:
            return max(len(a), len(b))
        prev = list(range(len(b) + 1))
        for i, ca in enumerate(a, 1):
            cur = [i]
            for j, cb in enumerate(b, 1):
                cur.append(min(
                    prev[j] + 1,
                    cur[j - 1] + 1,
                    prev[j - 1] + (0 if ca == cb else 1),
                ))
            prev = cur
        return prev[-1]

    def _vox_match(self, guess: str, answer: str) -> bool:
        left, right = self._vox_fold(guess), self._vox_fold(answer)
        if not left or not right:
            return False
        if left == right:
            return True
        if len(left) >= 4 and (left in right or right in left):
            return True
        return self._levenshtein(left, right) <= LEVENSHTEIN_MAX

    def _active_duel(self) -> dict[str, Any] | None:
        if 0 <= self.active_duel_index < len(self.duel_pairs):
            return self.duel_pairs[self.active_duel_index]
        return None

    def _advance_chat_subturn(self, now: float) -> None:
        if self.chat_subturn < 2:
            self.chat_subturn += 1
            self._assign_chat_subturn(now)
            self.phase_started = now
            self._bump(now)
            return
        self._finish_texto_chat(now)

    def _name_for(self, player_id: str) -> str:
        if player_id == BOT_PLAYER:
            return "NoraBot"
        if player_id == BOT_SELLER:
            return "El Tasador Bot"
        player = self.room.players.get(player_id)
        return player.name if player else "Jugador"

    def _begin_round(self, now: float) -> None:
        self.round_index += 1
        if self.round_index > self.rounds:
            self._podium(now)
            return
        self.submitted = {}
        self.votes = {}
        self.options = []
        self.reveal = {}
        self.last_points = {}
        self.roles = {}
        self.phase_started = now
        self.data = {}
        self.room.deltas = {}
        self.submitted_order = []
        self._perks_settled = False
        self.active_perks = dict(self.bought_perks)
        self.bought_perks = {}
        self.bonus = None
        self.bonus_choices = {}
        self.bonus_results = []
        self._bonus_done = False
        self.bets = {}
        self.bet_results = {}
        self.hint_used = {}
        self.taunts = []
        self._reset_round_state()
        # La primera ronda es limpia; después el giro cambia el ritmo y la economía.
        twists = TWISTS_BY_GAME.get(self.game, TWISTS)
        if self.round_index <= 1:
            self.twist = dict(twists[0])
        else:
            self.twist = dict(_rng(self.room.code, self.game, self.round_index, "twist").choice(twists))
        round_players = self._players()
        self.round_start_last = (
            min(round_players, key=lambda p: (p.score, p.joined_at)).id
            if round_players else ""
        )
        if self.game == "ultima_palabra":
            self._setup_duels()
        elif self.game == "vox_populi":
            pool = self._content_pool(content.VOX_QUESTIONS, "question")
            question = _pick(pool, self.used, _rng(self.room.code, self.game, self.round_index, "q"))
            self.data["question"] = question
            self.data["quick_chips"] = self._build_vox_chips(question)
            self.phase = "guess"
        elif self.game == "fuera_de_epoca":
            if self.round_index % 2 == 0:
                self._setup_sort()
            else:
                self._setup_time_guess()
        elif self.game == "texto_roto":
            scene = _pick(self._content_pool(content.TEXTO_ROTO_SCENES, "scene"), self.used, _rng(self.room.code, self.game, self.round_index, "scene"))
            roles = list(scene.get("roles") or ["jugador"])
            players = self._players()
            for index, player in enumerate(players):
                self.roles[player.id] = roles[index % len(roles)]
            self.data["scene"] = scene
            self.phase = "chat"
            self._assign_chat_subturn(now)
        elif self.game == "franela_fest":
            prompt = _pick(self._content_pool(content.FRANELA_PROMPTS, "shirt"), self.used, _rng(self.room.code, self.game, self.round_index, "shirt"))
            self.data["prompt"] = prompt
            self.phase = "create"
        elif self.game == "el_tasador":
            self._setup_lots()
            self.phase = "pitch"
        self._bump(now)

    def _finish_active(self, now: float) -> None:
        if self.game == "ultima_palabra":
            self._finish_ultima_write(now)
        elif self.game == "vox_populi":
            self._finish_vox_guess(now)
        elif self.game == "fuera_de_epoca":
            self._finish_time(now)
        elif self.game == "texto_roto":
            self._finish_texto_chat(now)
        elif self.game == "franela_fest":
            self._finish_franela_create(now)
        elif self.game == "el_tasador":
            self._finish_tasador_pitch(now)

    def _finish_vote(self, now: float) -> None:
        if self.game == "vox_populi":
            self._finish_vox_hilo(now)
        elif self.game == "texto_roto":
            self._finish_texto_vote(now)
        elif self.game == "franela_fest":
            self._finish_franela_vote(now)

    def _bonus_due(self) -> bool:
        return (
            self.rounds >= BONUS_ROUNDS_MIN
            and self.round_index in {self.rounds // 2, self.rounds - 1}
            and not self._bonus_done
        )

    def _advance(self, now: float) -> None:
        if self.round_index >= self.rounds:
            self._podium(now)
            return
        if self._bonus_due():
            self._begin_bonus(now)
            return
        self._begin_round(now)

    def _begin_bonus(self, now: float) -> None:
        self._bonus_done = True
        self.phase = "bonus"
        self.phase_started = now
        self.bonus_choices = {}
        self.bonus_results = []
        self.bonus = {
            "title": "La Ruleta de Fiesta",
            "instruction": "Elige un cofre en tu teléfono. Hay monedas y puntos escondidos.",
            "options": [
                {"id": "b0", "label": "Cofre naranja", "tone": "#F97316"},
                {"id": "b1", "label": "Cofre cian", "tone": "#22D3EE"},
                {"id": "b2", "label": "Cofre violeta", "tone": "#A78BFA"},
            ],
        }
        self._bump(now)

    def _finish_bonus(self, now: float) -> None:
        results = []
        players = self._players()
        last_id = min(players, key=lambda p: (p.score, p.joined_at)).id if players else ""
        for player in players:
            choice = int(self.bonus_choices.get(player.id, -1))
            if choice < 0:
                continue
            rng = _rng(self.room.code, self.game, self.round_index, f"bonus:{player.id}:{choice}")
            roll = rng.random()
            mimic = False
            if roll < 0.15:
                coins, points, label = 400, 200, "¡Jackpot!"
            elif roll < 0.50:
                coins, points, label = 200, 50, "Buen botín"
            elif roll < 0.80:
                coins, points, label = 100, 100, "Cofre sorpresa"
            else:
                coins, points, label = -100, 0, "Cofre Mimic"
                mimic = True
                self.mimic_hit = True
            player.coins = max(0, int(player.coins) + coins)
            if points:
                self._award(player.id, points)
            if mimic and last_id and last_id != player.id:
                self._award(last_id, 100)
            results.append({
                "id": player.id,
                "name": player.name,
                "choice": choice,
                "coins": coins,
                "points": points,
                "label": label,
                "mimic": mimic,
            })
        self.bonus_results = results
        self.bonus = {
            **(self.bonus or {}),
            "results": results,
        }
        if self.mimic_hit:
            self.reaction_banner = "MIMIC HIT · Alguien abrió el cofre trampa y pagó por ello."
        self.phase = "bonus_reveal"
        self.phase_started = now
        self._bump(now)

    def _podium(self, now: float) -> None:
        self.phase = "podium"
        self.phase_started = now
        self.data = {}
        self.options = []
        self.reveal = {}
        self.room.deltas = {}
        self._podium_done = True
        self._bump(now)

    # -- acciones --------------------------------------------------------
    def action(
        self,
        player_id: str,
        kind: str,
        now: float,
        *,
        round_no: int,
        text: str = "",
        target: str = "",
        bid: Any = 0,
        strokes: Any = None,
        index: Any = 0,
        power: str = "",
        wire: str = "",
        choices: Any = None,
        selected: Any = None,
        amount: Any = 0,
        vote: bool = False,
    ) -> tuple[bool, str]:
        player = self.room.players.get(player_id)
        if player is None:
            return False, "jugador"
        kind = str(kind or "").strip().lower()
        if player.spectator and kind != "fiesta_vote":
            return False, "jugador"
        if int(round_no or 0) != self._round_no():
            return False, "ronda"
        if kind == "fiesta_submit":
            return self._submit(player_id, text, amount, index, now)
        if kind == "fiesta_vote":
            return self._vote(player_id, target, now)
        if kind == "fiesta_draw":
            return self._draw(player_id, strokes, text, target, now)
        if kind == "fiesta_sort":
            return self._submit_sort(player_id, choices, now)
        if kind == "fiesta_react":
            return self._react(player_id, target, now)
        if kind == "fiesta_remix":
            return self._remix(player_id, target, text, now)
        if kind == "fiesta_invest":
            return self._invest(player_id, target, amount, now)
        if kind == "fiesta_wait_bonus":
            return self._wait_bonus(player_id, now)
        if kind == "fiesta_ready":
            return True, "ok"
        if kind == "fiesta_bonus":
            return self._choose_bonus(player_id, index, now)
        if kind == "fiesta_perk":
            return self._buy_perk(player_id, target, now)
        if kind == "fiesta_bet":
            return self._place_bet(player_id, amount, now)
        if kind == "fiesta_hint":
            return self._buy_hint(player_id, now)
        if kind == "fiesta_taunt":
            return self._send_taunt(player_id, text, target, now)
        return False, "acción"

    def _place_bet(self, player_id: str, amount: Any, now: float) -> tuple[bool, str]:
        """Apuesta de la ronda: arriesga monedas por puntos extra en el reveal."""
        if self.phase not in ("write", "guess", "create", "pitch", "sort", "chat", "remix", "invest"):
            return False, "fase"
        if player_id in self.bets:
            return False, "duplicada"
        player = self.room.players.get(player_id)
        if player is None:
            return False, "jugador"
        try:
            stake = int(amount or 0)
        except (TypeError, ValueError):
            return False, "monto"
        if stake not in (100, 250, 500):
            return False, "monto"
        if int(player.coins) < stake:
            return False, "monedas"
        player.coins = int(player.coins) - stake
        self.bets[player_id] = stake
        self.room.last_activity = now
        self._bump(now)
        return True, "ok"

    def _buy_hint(self, player_id: str, now: float) -> tuple[bool, str]:
        if self.phase not in ("write", "guess", "create", "pitch", "sort", "chat", "remix", "invest"):
            return False, "fase"
        if self.hint_used.get(player_id):
            return False, "duplicada"
        player = self.room.players.get(player_id)
        if player is None:
            return False, "jugador"
        if int(player.coins) < HINT_COST:
            return False, "monedas"
        player.coins = int(player.coins) - HINT_COST
        self.hint_used[player_id] = True
        self.room.last_activity = now
        self._bump(now)
        return True, "ok"

    def _send_taunt(self, player_id: str, text: str, target: str, now: float) -> tuple[bool, str]:
        if self.phase in ("lobby", "countdown", "podium", "closed"):
            return False, "fase"
        if any(item.get("playerId") == player_id for item in self.taunts):
            return False, "duplicada"
        player = self.room.players.get(player_id)
        if player is None:
            return False, "jugador"
        if int(player.coins) < TAUNT_COST:
            return False, "monedas"
        clean = self._filter_text(text, 28)
        if not clean:
            return False, "texto"
        allowed = {fold(item) for item in TAUNTS.get(self.game, ())}
        if fold(clean) not in allowed:
            return False, "texto"
        target_id = str(target or "").strip()
        if target_id and target_id != player_id and target_id in self.room.players:
            victim = self.room.players[target_id]
        else:
            candidates = [p for p in self._players() if p.id != player_id]
            victim = max(candidates, key=lambda p: p.score, default=None)
        player.coins = int(player.coins) - TAUNT_COST
        taunt_id = uuid.uuid4().hex
        self.taunts.append({
            "id": taunt_id,
            "playerId": player_id,
            "targetPlayerId": victim.id if victim else "",
            "targetName": victim.name if victim else "",
            "name": player.name,
            "text": clean.upper(),
            "color": f"#{max(0, min(0xFFFFFF, int(player.color))):06X}",
            "at": int(now * 1000),
        })
        self.taunts = self.taunts[-6:]
        if victim is not None:
            self.incoming_taunts[victim.id] = {
                "from": player.name,
                "fromId": player_id,
                "text": clean.upper(),
                "id": taunt_id,
            }
        self.room.last_activity = now
        self._bump(now)
        return True, "ok"

    def _choose_bonus(self, player_id: str, index: Any, now: float) -> tuple[bool, str]:
        if self.phase != "bonus":
            return False, "fase"
        if player_id in self.bonus_choices:
            return False, "duplicada"
        try:
            choice = int(index)
        except (TypeError, ValueError):
            return False, "opción"
        if choice not in (0, 1, 2):
            return False, "opción"
        self.bonus_choices[player_id] = choice
        self.room.last_activity = now
        self._bump(now)
        if all(player.id in self.bonus_choices for player in self._players()):
            self._finish_bonus(now)
        return True, "ok"

    def _buy_perk(self, player_id: str, perk_id: str, now: float) -> tuple[bool, str]:
        if self.phase != "reveal" or self.round_index >= self.rounds:
            return False, "fase"
        player = self.room.players.get(player_id)
        if player is None:
            return False, "jugador"
        if perk_id == ALL_IN_PERK["id"]:
            if self.round_index != self.rounds - 1:
                return False, "perk"
            if player_id in self.bought_perks:
                return False, "duplicada"
            if int(player.coins) < 200:
                return False, "monedas"
            amount = int(player.coins)
            player.coins = 0
            boost = min(1.5, max(0.5, (amount // 250) * 0.5))
            self.final_boost[player_id] = boost
            self.bought_perks[player_id] = ALL_IN_PERK["id"]
            self.room.last_activity = now
            self._bump(now)
            return True, "ok"
        perk = next((item for item in PERKS if item["id"] == perk_id), None)
        if perk is None:
            return False, "perk"
        if player_id in self.bought_perks:
            return False, "duplicada"
        if int(player.coins) < int(perk["cost"]):
            return False, "monedas"
        player.coins = int(player.coins) - int(perk["cost"])
        self.bought_perks[player_id] = str(perk["id"])
        self.room.last_activity = now
        self._bump(now)
        return True, "ok"

    def _submit(self, player_id: str, text: str, amount: Any, index: Any, now: float) -> tuple[bool, str]:
        if self.phase not in ("write", "guess", "pitch", "chat"):
            return False, "fase"
        if player_id in self.submitted and self.phase != "chat":
            return False, "duplicada"
        if self.game == "fuera_de_epoca":
            raw_choice = amount if amount not in (None, "", 0) else index
            try:
                choice = int(raw_choice if raw_choice not in (None, "") else text)
            except (TypeError, ValueError):
                return False, "opción"
            if not 0 <= choice < len(self.data.get("years") or []):
                return False, "opción"
            self.submitted[player_id] = {"choice": choice}
        elif self.game == "texto_roto":
            if self.chat_roles.get(player_id) != "WRITER":
                return False, "turno"
            if player_id in self.chat_submitted:
                return False, "duplicada"
            clean = self._filter_text(text, 160)
            if not clean:
                return False, "texto"
            message = {
                "id": f"m{len(self.chat_messages)}",
                "playerId": player_id,
                "authorName": self._name_for(player_id),
                "role": self.roles.get(player_id, "JUGADOR"),
                "text": clean,
                "subturn": self.chat_subturn,
                "reactions": 0,
                "goal": self.chat_goals.get(player_id, ""),
            }
            self.chat_messages.append(message)
            self.chat_submitted.add(player_id)
            self.submitted[player_id] = {"text": clean, "messageId": message["id"]}
        elif self.game == "el_tasador":
            if player_id not in self.sellers:
                return False, "comprador"
            clean = self._filter_text(text, 180)
            if not clean:
                return False, "texto"
            lot_id = "A" if player_id == self.sellers[0] else "B"
            self.submitted[player_id] = {"text": clean, "lot": lot_id}
            if lot_id in self.lots:
                self.lots[lot_id]["pitch"] = clean
        else:
            clean = self._filter_text(text, 180)
            if not clean:
                return False, "texto"
            if self.game == "vox_populi":
                self.submitted[player_id] = {"text": clean, "guess": self._vox_fold(clean)}
            else:
                self.submitted[player_id] = {"text": clean}
        if player_id not in self.submitted_order:
            self.submitted_order.append(player_id)
        self.room.last_activity = now
        self._bump(now)
        if self.game == "el_tasador":
            if self._sellers_submitted():
                self._finish_active(now)
        elif self.game != "texto_roto" and self._all_submitted():
            self._finish_active(now)
        return True, "ok"

    def _vote(self, player_id: str, target: str, now: float) -> tuple[bool, str]:
        player = self.room.players.get(player_id)
        if player is not None and player.spectator:
            if self.phase not in ("vote", "duel"):
                return False, "fase"
            valid = {str(item.get("id")) for item in self.options}
            if target not in valid:
                return False, "opción"
            if player_id in self.audience_votes:
                return False, "duplicada"
            self.audience_votes[player_id] = target
            self.room.last_activity = now
            self._bump(now)
            return True, "ok"
        if self.phase not in ("vote", "duel"):
            return False, "fase"
        if player_id in self.votes or player_id in self.duel_votes:
            return False, "duplicada"
        option = next((item for item in self.options if item.get("id") == target), None)
        if option is None:
            return False, "opción"
        owner = option.get("remixerId") or option.get("authorId")
        if owner == player_id and not self.solo_test and len(self._players()) > 1:
            return False, "propia"
        if self.phase == "duel":
            pair = self._active_duel()
            if pair is None:
                return False, "duelo"
            if player_id in (pair["player_a"], pair["player_b"]) and not (
                self.solo_test or len(self._players()) <= 2
            ):
                return False, "propia"
            self.duel_votes[player_id] = target
            self.room.last_activity = now
            self._bump(now)
            if self._all_duel_voted():
                self._finish_duel(now)
            return True, "ok"
        self.votes[player_id] = target
        self.room.last_activity = now
        self._bump(now)
        if self._all_voted():
            self._finish_vote(now)
        return True, "ok"

    def _draw(self, player_id: str, strokes: Any, slogan: str, slogan2: str, now: float) -> tuple[bool, str]:
        if self.game != "franela_fest" or self.phase != "create":
            return False, "fase"
        if player_id in self.submitted:
            return False, "duplicada"
        cleaned = self._clean_strokes(strokes)
        if not cleaned:
            return False, "dibujo"
        first = self._filter_text(slogan, 80)
        second = self._filter_text(slogan2 or slogan, 80)
        if not first:
            return False, "eslogan"
        self.submitted[player_id] = {
            "strokes": cleaned,
            "text": first,
            "slogans": [first, second or first],
        }
        self.room.last_activity = now
        self._bump(now)
        if self._all_submitted():
            self._finish_franela_create(now)
        return True, "ok"

    def _clean_strokes(self, raw: Any) -> list[dict[str, Any]]:
        if not isinstance(raw, list):
            return []
        out: list[dict[str, Any]] = []
        total = 0
        for stroke in raw[:8]:
            if not isinstance(stroke, dict):
                continue
            points = []
            for point in (stroke.get("points") or [])[:120]:
                if isinstance(point, (list, tuple)) and len(point) >= 2:
                    try:
                        x = max(0.0, min(1000.0, float(point[0])))
                        y = max(0.0, min(1000.0, float(point[1])))
                    except (TypeError, ValueError):
                        continue
                    points.append([round(x, 2), round(y, 2)])
                    total += 1
            if len(points) >= 2:
                out.append({
                    "color": _clean(stroke.get("color") or "#FFFFFF", 9),
                    "width": max(1.0, min(24.0, float(stroke.get("width") or 5))),
                    "points": points,
                })
            if total >= 800:
                break
        return out

    # -- juegos ----------------------------------------------------------
    def _finish_ultima_write(self, now: float) -> None:
        for pair in self.duel_pairs:
            if pair["player_a"] == BOT_PLAYER:
                pair["ans_a"] = pair["ans_a"] or "Una respuesta de robot."
            else:
                payload = self.submitted.get(pair["player_a"], {})
                pair["ans_a"] = str(payload.get("text") or "(silencio incómodo)")
            if pair["player_b"] != BOT_PLAYER:
                payload = self.submitted.get(pair["player_b"], {})
                pair["ans_b"] = str(payload.get("text") or "(silencio incómodo)")
            elif not pair["ans_b"]:
                pair["ans_b"] = "Una respuesta de robot."
        self.active_duel_index = 0
        self.duel_votes = {}
        self._set_duel_options()
        self.phase = "duel"
        self.phase_started = now
        self._bump(now)

    def _set_duel_options(self) -> None:
        pair = self._active_duel()
        if pair is None:
            self.options = []
            return
        self.options = [
            {
                "id": "A",
                "text": pair["ans_a"],
                "authorId": pair["player_a"],
                "authorName": self._name_for(pair["player_a"]),
                "votes": 0,
            },
            {
                "id": "B",
                "text": pair["ans_b"],
                "authorId": pair["player_b"],
                "authorName": self._name_for(pair["player_b"]),
                "votes": 0,
            },
        ]

    def _finish_duel(self, now: float) -> None:
        pair = self._active_duel()
        if pair is None:
            self._set_reveal(now, "Duelos", [])
            return
        voters_a = [pid for pid, target in self.duel_votes.items() if target == "A"]
        voters_b = [pid for pid, target in self.duel_votes.items() if target == "B"]
        votes_a, votes_b = len(voters_a), len(voters_b)
        total_votes = votes_a + votes_b
        pair["votes_a"] = voters_a
        pair["votes_b"] = voters_b
        multiplier = self._multiplier()
        if pair["player_a"] != BOT_PLAYER:
            self._award(pair["player_a"], votes_a * 150 * multiplier)
        if pair["player_b"] != BOT_PLAYER:
            self._award(pair["player_b"], votes_b * 150 * multiplier)
        winner = "A" if votes_a >= votes_b else "B"
        if total_votes and votes_a == votes_b:
            winner = "A" if _rng(self.room.code, self.game, self.round_index, f"tie:{pair['duel_id']}").random() < 0.5 else "B"
        winner_id = pair["player_a"] if winner == "A" else pair["player_b"]
        loser_votes = votes_b if winner == "A" else votes_a
        if winner_id != BOT_PLAYER:
            self._award(winner_id, 250 * multiplier)
        knockout = bool(total_votes >= 2 and loser_votes == 0)
        if knockout and winner_id != BOT_PLAYER:
            self._award(winner_id, 300 * multiplier)
        self.options[0]["votes"] = votes_a
        self.options[1]["votes"] = votes_b
        self._apply_audience_favorite(self.options)
        self.duel_results.append({
            "duel_id": pair["duel_id"],
            "prompt": pair["prompt"],
            "a": pair["ans_a"],
            "b": pair["ans_b"],
            "votesA": votes_a,
            "votesB": votes_b,
            "winner": winner,
            "winnerName": self._name_for(winner_id),
            "knockout": knockout,
        })
        if self.active_duel_index + 1 < len(self.duel_pairs):
            self.active_duel_index += 1
            self.duel_votes = {}
            self._set_duel_options()
            self.phase_started = now
            self._bump(now)
            return
        lines = []
        for result in self.duel_results:
            suffix = " · NORA-KO" if result["knockout"] else ""
            lines.append(
                f"«{result['a']}» vs «{result['b']}» → {result['winnerName']} "
                f"({result['votesA']}-{result['votesB']}){suffix}"
            )
        self._set_reveal(now, "La sala eligió", lines)

    def _finish_vox_guess(self, now: float) -> None:
        question = self.data.get("question") or {}
        answers = list(question.get("answers") or [])
        results = []
        for player_id, payload in self.submitted.items():
            guess = payload.get("guess", "")
            rank = 0
            for position, (answer, _count) in enumerate(answers, 1):
                if self._vox_match(guess, str(answer)):
                    rank = position
                    break
            points = 0 if rank == 0 else max(100, 700 - (rank - 1) * 100)
            points = int(round(points * self._multiplier()))
            self._award(player_id, points)
            results.append({
                "id": player_id,
                "name": self._name_for(player_id),
                "text": payload.get("text", ""),
                "rank": rank,
                "points": points,
            })
        self.vox_guess_results = sorted(results, key=lambda item: (item["rank"] == 0, item["rank"], -item["points"]))
        self.data["vox_answers"] = answers
        if len(answers) >= 3:
            self.vox_rank3 = {"text": answers[2][0], "count": answers[2][1], "rank": 3}
        else:
            self.vox_rank3 = {"text": "", "count": 0, "rank": 3}
        mystery_choices = [1, 2, 5, 6]
        options = [answers[i - 1] for i in mystery_choices if 0 < i <= len(answers)]
        rng = _rng(self.room.code, self.game, self.round_index, "vox-mystery")
        chosen = rng.choice(options) if options else (answers[0] if answers else ("", 0))
        rank = next((i for i, (text, _c) in enumerate(answers, 1) if text == chosen[0]), 1)
        self.vox_mystery = {"text": chosen[0], "rank": rank}
        self.options = [
            {"id": "MAS_ARRIBA", "text": "Va más arriba (puestos 1-2)", "authorId": "", "votes": 0},
            {"id": "MAS_ABAJO", "text": "Va más abajo (puestos 4-6)", "authorId": "", "votes": 0},
        ]
        self.votes = {}
        self.phase = "vote"
        self.phase_started = now
        self._bump(now)

    def _finish_vox_hilo(self, now: float) -> None:
        mystery = self.vox_mystery or {"rank": 1, "text": ""}
        correct = "MAS_ARRIBA" if int(mystery.get("rank", 1)) <= 2 else "MAS_ABAJO"
        for player_id, target in self.votes.items():
            if target == correct:
                self._award(player_id, 250 * self._multiplier())
        answers = self.data.get("vox_answers") or []
        self.options = [
            {"id": f"v{index}", "text": text, "count": count, "rank": index, "votes": 0}
            for index, (text, count) in enumerate(answers, 1)
        ]
        lines = [
            f"{rank}. {text} — {count} respuestas"
            for rank, (text, count) in enumerate(answers, 1)
        ]
        lines.append(f"Misterio: «{mystery.get('text','')}» iba en el puesto {mystery.get('rank','?')}.")
        for result in self.vox_guess_results:
            estado = f"puesto {result['rank']}" if result["rank"] else "fuera del top"
            lines.append(f"{result['name']}: «{result['text']}» → {estado} (+{result['points']})")
        self._set_reveal(now, "Lo que dijo la gente", lines)

    def _finish_time(self, now: float) -> None:
        correct = int(self.data.get("correct", 0))
        years = self.data.get("years") or []
        for player_id, payload in self.submitted.items():
            choice = int(payload.get("choice", -1))
            streak = self.streaks.get(player_id, 0)
            correct_year = int(years[correct]) if 0 <= correct < len(years) else 0
            guessed_year = int(years[choice]) if 0 <= choice < len(years) else -99999
            delta = abs(guessed_year - correct_year)
            if choice == correct:
                streak += 1
                points = 500 + max(0, streak - 1) * 100
            elif delta <= 3:
                streak = 0
                points = 200
            else:
                streak = 0
                points = 0
            points = int(round(points * self._multiplier()))
            self.streaks[player_id] = streak
            self._award(player_id, points)
        self._set_reveal(now, "El año correcto", [
            f"{self.data.get('event', {}).get('event', '')} — {years[correct] if 0 <= correct < len(years) else ''}",
            "La racha paga +100 por acierto consecutivo.",
        ])

    def _submit_sort(self, player_id: str, choices: Any, now: float) -> tuple[bool, str]:
        if self.game != "fuera_de_epoca" or self.phase != "sort":
            return False, "fase"
        if player_id in self.submitted:
            return False, "duplicada"
        if not isinstance(choices, list) or len(choices) != 3:
            return False, "orden"
        ids = [str(item) for item in choices]
        valid = {item["id"] for item in self.sort_items}
        if set(ids) != valid:
            return False, "orden"
        self.submitted[player_id] = {"order": ids}
        self.submitted_order.append(player_id)
        self.room.last_activity = now
        self._bump(now)
        if self._all_submitted():
            self._finish_sort(now)
        return True, "ok"

    def _finish_sort(self, now: float) -> None:
        correct_order = sorted(self.sort_items, key=lambda item: self.sort_years.get(item["id"], 0))
        correct_ids = [item["id"] for item in correct_order]
        for player_id, payload in self.submitted.items():
            order = [str(item) for item in payload.get("order", [])]
            streak = self.streaks.get(player_id, 0)
            exact = order == correct_ids
            reversed_order = order == list(reversed(correct_ids))
            adjacent = any(
                order[i] == correct_ids[i] and order[i + 1] == correct_ids[i + 1]
                for i in range(2)
            ) or (order and (order[0] == correct_ids[0] or order[-1] == correct_ids[-1]))
            if exact:
                streak += 1
                points = 600 + 100 * max(0, streak - 1)
            elif reversed_order:
                streak = 0
                points = 0
            elif adjacent:
                points = 250
            else:
                streak = 0
                points = 0
            points = int(round(points * self._multiplier()))
            self.streaks[player_id] = streak
            self._award(player_id, points)
        lines = [
            f"{index}. {item['title']} — {self.sort_years.get(item['id'], '?')}"
            for index, item in enumerate(correct_order, 1)
        ]
        self._set_reveal(now, "La línea temporal", lines)

    def _finish_texto_chat(self, now: float) -> None:
        if not self.chat_messages:
            self.chat_messages.append({
                "id": "m0",
                "playerId": "",
                "authorName": "La Máquina",
                "role": "SISTEMA",
                "text": "El chat se quedó en silencio. La Máquina juzga.",
                "subturn": 0,
                "reactions": 0,
                "goal": "",
            })
        self.options = [
            {
                "id": message["id"],
                "text": message["text"],
                "authorId": message["playerId"],
                "authorName": message["authorName"],
                "role": message["role"],
                "votes": 0,
            }
            for message in self.chat_messages
        ]
        self.phase = "vote"
        self.phase_started = now
        self.votes = {}
        self._bump(now)

    def _finish_texto_vote(self, now: float) -> None:
        counts = {option["id"]: 0 for option in self.options}
        for target in self.votes.values():
            if target in counts:
                counts[target] += 1
        top = max(counts.values(), default=0)
        self._apply_audience_favorite(self.options)
        for option in self.options:
            option["votes"] = counts.get(option["id"], 0)
            author = option.get("authorId")
            if author:
                multiplier = self._multiplier()
                self._award(author, (100 + option["votes"] * 300) * multiplier)
                if option["votes"] == top and top > 0:
                    self._award(author, 200 * multiplier)
        self._set_reveal(now, "La conversación quedó así", [
            f"{option.get('role','')}: {option['text']} — {option.get('authorName','')} ({option['votes']} votos)"
            for option in self.options
        ])

    def _finish_franela_create(self, now: float) -> None:
        self.remix_drawings = []
        self.remix_slogans = []
        for player_id, payload in self.submitted.items():
            name = self._name_for(player_id)
            self.remix_drawings.append({
                "id": f"d{player_id}",
                "ownerId": player_id,
                "ownerName": name,
                "strokes": payload["strokes"],
            })
            for index, slogan in enumerate(payload.get("slogans", [payload["text"]])):
                self.remix_slogans.append({
                    "id": f"s{player_id}_{index}",
                    "ownerId": player_id,
                    "ownerName": name,
                    "text": slogan,
                })
        for drawing in getattr(content, "FRANELA_FALLBACK_DRAWINGS", ()):
            self.remix_drawings.append({
                "id": str(drawing["id"]),
                "ownerId": str(drawing.get("ownerId", "bot:franela")),
                "ownerName": str(drawing.get("ownerName", "El Taller")),
                "strokes": drawing["strokes"],
            })
        for slogan in getattr(content, "FRANELA_FALLBACK_SLOGANS", ()):
            self.remix_slogans.append({
                "id": str(slogan["id"]),
                "ownerId": str(slogan.get("ownerId", "bot:franela")),
                "ownerName": str(slogan.get("ownerName", "El Taller")),
                "text": str(slogan["text"]),
            })
        players = self._players()
        self.remix_pools = {}
        for index, player in enumerate(players):
            drawings = [d for d in self.remix_drawings if d["ownerId"] != player.id]
            slogans = [s for s in self.remix_slogans if s["ownerId"] != player.id]
            if len(drawings) < 2:
                drawings = self.remix_drawings
            if len(slogans) < 4:
                slogans = self.remix_slogans
            self.remix_pools[player.id] = {
                "drawings": drawings[:2] if len(drawings) >= 2 else drawings,
                "slogans": slogans[:4] if len(slogans) >= 4 else slogans,
            }
        self.remix_choices = {}
        self.submitted = {}
        self.submitted_order = []
        self.phase = "remix"
        self.phase_started = now
        self._bump(now)

    def _react(self, player_id: str, target: str, now: float) -> tuple[bool, str]:
        if self.game != "texto_roto" or self.phase != "chat":
            return False, "fase"
        if self.chat_roles.get(player_id) != "REACTOR":
            return False, "turno"
        if player_id in self.chat_reacted:
            return False, "duplicada"
        message = next((item for item in reversed(self.chat_messages) if item["id"] == target), None)
        if message is None:
            message = self.chat_messages[-1] if self.chat_messages else None
        if message is None:
            return False, "mensaje"
        if message.get("playerId") == player_id:
            return False, "propia"
        message["reactions"] = int(message.get("reactions", 0)) + 1
        self.chat_reacted.add(player_id)
        author = message.get("playerId")
        if author in self.room.players:
            self._award(author, 25)
        reactor = self.room.players.get(player_id)
        if reactor is not None:
            reactor.coins = int(reactor.coins) + 10
        self.room.last_activity = now
        self._bump(now)
        return True, "ok"

    def _wait_bonus(self, player_id: str, now: float) -> tuple[bool, str]:
        if self.phase not in ("write", "guess", "create", "pitch", "sort", "chat", "remix", "invest"):
            return False, "fase"
        if player_id in self.wait_bonus:
            return False, "duplicada"
        player = self.room.players.get(player_id)
        if player is None:
            return False, "jugador"
        self.wait_bonus.add(player_id)
        player.coins = int(player.coins) + WAIT_BONUS_COINS
        self.room.last_activity = now
        self._bump(now)
        return True, "ok"

    def _remix(self, player_id: str, drawing_id: str, slogan_id: str, now: float) -> tuple[bool, str]:
        if self.game != "franela_fest" or self.phase != "remix":
            return False, "fase"
        if player_id in self.remix_choices:
            return False, "duplicada"
        pool = self.remix_pools.get(player_id) or {}
        drawings = {item["id"] for item in pool.get("drawings", [])}
        slogans = {item["id"] for item in pool.get("slogans", [])}
        if drawing_id not in drawings or slogan_id not in slogans:
            return False, "remix"
        self.remix_choices[player_id] = {"drawing": drawing_id, "slogan": slogan_id}
        self.room.last_activity = now
        self._bump(now)
        if all(player.id in self.remix_choices for player in self._players()):
            self._finish_franela_remix(now)
        return True, "ok"

    def _finish_franela_remix(self, now: float) -> None:
        drawings = {item["id"]: item for item in self.remix_drawings}
        slogans = {item["id"]: item for item in self.remix_slogans}
        self.options = []
        for player in self._players():
            choice = self.remix_choices.get(player.id)
            if choice is None:
                pool = self.remix_pools.get(player.id) or {}
                if not pool.get("drawings") or not pool.get("slogans"):
                    continue
                choice = {
                    "drawing": pool["drawings"][0]["id"],
                    "slogan": pool["slogans"][0]["id"],
                }
            drawing = drawings.get(choice["drawing"])
            slogan = slogans.get(choice["slogan"])
            if drawing is None or slogan is None:
                continue
            self.options.append({
                "id": f"r{player.id}",
                "text": slogan["text"],
                "slogan": slogan["text"],
                "strokes": drawing["strokes"],
                "authorId": drawing["ownerId"],
                "authorName": drawing["ownerName"],
                "sloganAuthorId": slogan["ownerId"],
                "sloganAuthorName": slogan["ownerName"],
                "remixerId": player.id,
                "remixerName": player.name,
                "votes": 0,
            })
        self.phase = "vote"
        self.phase_started = now
        self.votes = {}
        self._bump(now)

    def _finish_franela_vote(self, now: float) -> None:
        counts = {option["id"]: 0 for option in self.options}
        for target in self.votes.values():
            if target in counts:
                counts[target] += 1
        self._apply_audience_favorite(self.options, bonus=150)
        multiplier = self._multiplier()
        for option in self.options:
            option["votes"] = counts.get(option["id"], 0)
            votes = option["votes"]
            if votes:
                self._award(option.get("remixerId"), 200 * votes * multiplier)
                self._award(option.get("authorId"), 100 * votes * multiplier)
                self._award(option.get("sloganAuthorId"), 100 * votes * multiplier)
        self._set_reveal(now, "El desfile", [
            f"{option['slogan']} — {option.get('remixerName','')} "
            f"(dibujo: {option.get('authorName','')} · eslogan: {option.get('sloganAuthorName','')}) "
            f"({option['votes']} votos)"
            for option in sorted(self.options, key=lambda item: -item.get("votes", 0))
        ])

    def _finish_tasador_pitch(self, now: float) -> None:
        for lot_id, lot in self.lots.items():
            if not lot.get("pitch"):
                lot["pitch"] = "Un lote misterioso. Confíen en el Tasador."
        self.investments = {}
        buyers = list(self.data.get("buyers") or [])
        if BOT_PLAYER in buyers:
            bot_lot = "A" if _rng(self.room.code, self.game, self.round_index, "bot-bid").random() < 0.5 else "B"
            self.investments[BOT_PLAYER] = {"lot": bot_lot, "amount": 100}
        self.phase = "invest"
        self.phase_started = now
        self._bump(now)

    def _invest(self, player_id: str, lot_id: str, amount: Any, now: float) -> tuple[bool, str]:
        if self.game != "el_tasador" or self.phase != "invest":
            return False, "fase"
        if player_id in self.investments:
            return False, "duplicada"
        buyers = self.data.get("buyers") or []
        if player_id not in buyers or player_id in self.sellers:
            return False, "comprador"
        lot_id = str(lot_id or "").upper()
        if lot_id not in ("A", "B"):
            return False, "lote"
        try:
            value = int(amount or 0)
        except (TypeError, ValueError):
            return False, "monto"
        if value not in (0, 100, 250, 400):
            return False, "monto"
        player = self.room.players.get(player_id)
        if player is not None and value > int(player.coins):
            return False, "monedas"
        if player is not None:
            player.coins = int(player.coins) - value
        self.investments[player_id] = {"lot": lot_id, "amount": value}
        self.submitted[player_id] = {"lot": lot_id, "amount": value}
        self.room.last_activity = now
        self._bump(now)
        buyers = [pid for pid in (self.data.get("buyers") or []) if pid in self.room.players]
        if buyers and all(pid in self.investments for pid in buyers):
            self._finish_invest(now)
        return True, "ok"

    def _finish_invest(self, now: float) -> None:
        totals = {"A": 0, "B": 0}
        for bid in self.investments.values():
            totals[bid["lot"]] = totals.get(bid["lot"], 0) + int(bid["amount"])
        multiplier = self._multiplier()
        for lot_id, lot in self.lots.items():
            lot["invested"] = totals.get(lot_id, 0)
            seller = lot["seller"]
            if seller in self.room.players:
                self._award(seller, lot["invested"])
                if not lot["authentic"] and lot["invested"] > 250:
                    self._award(seller, 200)
        for player_id, bid in self.investments.items():
            if player_id not in self.room.players:
                continue
            if int(bid.get("amount", 0)) <= 0:
                continue
            lot = self.lots.get(bid["lot"])
            if lot is None:
                continue
            gain = max(0, int(lot["realValue"]) - int(bid["amount"]))
            self._award(player_id, gain * multiplier)
        lines = []
        for lot_id, lot in self.lots.items():
            estado = "AUTÉNTICO" if lot["authentic"] else "FALSIFICACIÓN"
            lines.append(
                f"Lote {lot_id}: {lot['object'].get('name','')} — {estado} — "
                f"valor real {lot['realValue']} — pujas {lot['invested']}"
            )
        self._set_reveal(now, "El precio real", lines)

    def _audience_vote_counts(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for target in self.audience_votes.values():
            counts[str(target)] = counts.get(str(target), 0) + 1
        return counts

    def _apply_audience_favorite(self, options: list[dict[str, Any]], bonus: int = AUDIENCE_BONUS) -> None:
        if not self.audience_votes:
            return
        counts: dict[str, int] = {}
        for target in self.audience_votes.values():
            counts[target] = counts.get(target, 0) + 1
        if not counts:
            return
        favorite = max(counts, key=counts.get)
        for option in options:
            if str(option.get("id")) == favorite:
                option["audienceFavorite"] = True
                author = option.get("remixerId") or option.get("authorId")
                if author:
                    self._award(author, bonus * self._multiplier())
                break

    def _set_reveal(self, now: float, title: str, lines: list[str]) -> None:
        self.reveal = {"title": title, "lines": list(lines)}
        self.phase = "reveal"
        self.phase_started = now
        self._compute_reaction_banner()
        self._bump(now)

    def _compute_reaction_banner(self) -> None:
        self.reaction_banner = ""
        if self.mimic_hit:
            self.reaction_banner = "MIMIC HIT · Alguien abrió el cofre trampa y pagó por ello."
            return
        if self.bets:
            for player_id, stake in self.bets.items():
                result = self.bet_results.get(player_id, {})
                if stake == 500 and not result.get("won", False):
                    self.reaction_banner = "HIGH ROLLER FAIL · Apostó 500 y se fue con las manos vacías."
                    return
        top = max(self.last_points.values(), default=0)
        if top > 0 and self.round_start_last:
            leaders = [pid for pid, points in self.last_points.items() if points == top]
            if self.round_start_last in leaders:
                self.reaction_banner = "UNDERDOG WIN · El último de la tabla se llevó la ronda."
                return
        # Unanimidad: una opción con todos los votos (si hay votos).
        total_votes = sum(int(option.get("votes", 0) or 0) for option in self.options)
        for option in self.options:
            if option.get("votes") and option.get("votes") == total_votes and total_votes >= 2:
                self.reaction_banner = "UNANIMITY · La sala entera votó por lo mismo."
                return

    # -- snapshot --------------------------------------------------------
    def _duel_payload(self) -> dict[str, Any] | None:
        pair = self._active_duel()
        if pair is None or self.phase not in ("duel", "reveal"):
            return None
        def public_option(option: dict[str, Any] | None) -> dict[str, Any] | None:
            if option is None:
                return None
            if self.phase == "reveal":
                return option
            return {"id": option.get("id", ""), "text": option.get("text", "")}

        return {
            "duelIndex": self.active_duel_index,
            "duelTotal": len(self.duel_pairs),
            "prompt": pair["prompt"],
            "optionA": public_option(self.options[0] if self.options else None),
            "optionB": public_option(self.options[1] if len(self.options) > 1 else None),
            "isFinal": self.active_duel_index + 1 >= len(self.duel_pairs),
            "results": list(self.duel_results) if self.phase == "reveal" else [],
        }

    def _vox_payload(self, reveal: bool) -> dict[str, Any] | None:
        if self.game != "vox_populi":
            return None
        mystery = dict(self.vox_mystery or {})
        if not reveal:
            mystery.pop("rank", None)
        payload = {
            "rank3": self.vox_rank3,
            "mysteryAnswer": mystery,
            "quickChips": self.vox_quick_chips if not reveal else {},
        }
        if reveal:
            payload["answers"] = self.data.get("vox_answers") or []
            payload["guesses"] = self.vox_guess_results
        return payload

    def _lots_payload(self, reveal: bool) -> list[dict[str, Any]]:
        if self.game != "el_tasador":
            return []
        out = []
        for lot_id, lot in self.lots.items():
            item = {
                "id": lot_id,
                "object": _public_object(lot["object"]),
                "seller": lot["seller"],
                "sellerName": self._name_for(lot["seller"]),
                "pitch": lot.get("pitch", ""),
                "invested": int(lot.get("invested", 0)),
                "suspicion": ("Parece legítimo" if lot["authentic"] else "Huele raro") if reveal else "",
                "authentic": bool(lot["authentic"]) if reveal else None,
                "realValue": int(lot["realValue"]) if reveal else 0,
            }
            out.append(item)
        return out

    def _screen(self) -> str:
        if self.phase in SCREEN_BY_PHASE:
            return SCREEN_BY_PHASE[self.phase]
        if self.game == "fuera_de_epoca" and self.phase == "guess":
            return "choice"
        if self.game == "fuera_de_epoca" and self.phase == "sort":
            return "sort"
        if self.game == "franela_fest" and self.phase == "create":
            return "draw"
        if self.game == "franela_fest" and self.phase == "remix":
            return "remix"
        if self.game == "el_tasador" and self.phase == "invest":
            return "invest"
        if self.game == "texto_roto" and self.phase == "chat":
            return "chat"
        if self.phase == "duel":
            return "vote"
        if self.game == "vox_populi" and self.phase == "vote":
            return "choice"
        if self.phase == "write" or self.phase == "guess" or self.phase == "pitch":
            return "text_input"
        if self.phase == "vote":
            return "vote"
        if self.phase == "bonus":
            return "bonus"
        if self.phase == "bonus_reveal":
            return "bonus_reveal"
        return "wait"

    def _public_options(self, reveal: bool) -> list[dict[str, Any]]:
        out = []
        for option in self.options:
            item = {
                "id": option.get("id", ""),
                "text": option.get("text", ""),
                "votes": int(option.get("votes", 0)) if reveal else 0,
                "isYours": False,
                "authorName": option.get("authorName", "") if reveal else "",
                "role": option.get("role", ""),
                "slogan": option.get("slogan", ""),
                "strokes": option.get("strokes") if (
                    reveal or (self.game == "franela_fest" and self.phase in ("vote", "remix"))
                ) else None,
                "value": option.get("value", 0) if reveal else 0,
                "count": option.get("count", 0) if reveal else 0,
                "rank": option.get("rank", 0) if reveal else 0,
                "audienceFavorite": bool(option.get("audienceFavorite", False)),
                "remixerName": option.get("remixerName", "") if reveal else "",
                "remixerId": option.get("remixerId", "") if reveal else "",
                "sloganAuthorName": option.get("sloganAuthorName", "") if reveal else "",
                "sloganAuthorId": option.get("sloganAuthorId", "") if reveal else "",
            }
            out.append(item)
        return out

    def _you(self, player_id: str | None) -> dict[str, Any]:
        if not player_id:
            return {}
        player = self.room.players.get(player_id)
        payload = self.submitted.get(player_id, {}) if isinstance(self.submitted.get(player_id), dict) else {}
        choice = payload.get("choice", -1) if isinstance(payload, dict) else -1
        hint_text = ""
        hint_years: list[int] = []
        if self.hint_used.get(player_id):
            hint_text = HINTS.get(self.game, "")
            if self.game == "fuera_de_epoca":
                years = list(self.data.get("years") or [])
                correct = int(self.data.get("correct", -1))
                if 0 <= correct < len(years):
                    rng = _rng(self.room.code, self.game, self.round_index, f"hint:{player_id}")
                    wrong = [index for index in range(len(years)) if index != correct]
                    keep = [correct]
                    if wrong:
                        keep.append(rng.choice(wrong))
                    hint_years = sorted(keep)
                    hint_text = "El profesor Tic Tac apagó dos años: solo quedan dos posibilidades."
            elif self.game == "el_tasador":
                lot_id = "A" if self.sellers and player_id == self.sellers[0] else "B"
                lot = self.lots.get(lot_id) or {}
                real = int(lot.get("realValue", 0))
                low = max(0, int(real * 0.75))
                high = int(real * 1.25)
                hint_text = f"El Tasador sopló: el valor real está entre {low} y {high}."
        bet = int(self.bets.get(player_id, 0))
        bet_result = self.bet_results.get(player_id) or {}
        incoming = self.incoming_taunts.get(player_id)
        lot_role = "SELLER" if player_id in self.sellers else ("BUYER" if player_id in (self.data.get("buyers") or []) else "")
        lot_id = ""
        lot_hint = ""
        if player_id in self.sellers and self.lots:
            lot_id = "A" if self.sellers and player_id == self.sellers[0] else "B"
            lot = self.lots.get(lot_id)
            if lot is not None:
                lot_hint = "Parece legítimo" if lot["authentic"] else "Huele raro"
        screen_for_player = self._screen()
        if self.game == "texto_roto" and self.phase == "chat":
            screen_for_player = "text_input" if self.chat_roles.get(player_id) == "WRITER" else "react"
        return {
            "submitted": player_id in self.submitted,
            "voted": player_id in self.votes or player_id in self.duel_votes,
            "vote": self.votes.get(player_id, "") or self.duel_votes.get(player_id, ""),
            "text": payload.get("text", "") if isinstance(payload, dict) else "",
            "choice": choice,
            "value": payload.get("value", 0) if isinstance(payload, dict) else 0,
            "points": int(self.last_points.get(player_id, 0)),
            "score": int(player.score) if player else 0,
            "role": self.roles.get(player_id, ""),
            "coins": int(player.coins) if player else 0,
            "perk": self.bought_perks.get(player_id, ""),
            "activePerk": self.active_perks.get(player_id, ""),
            "bonusPick": int(self.bonus_choices.get(player_id, -1)),
            "submittedOrder": self.submitted_order.index(player_id) + 1 if player_id in self.submitted_order else 0,
            "bet": bet,
            "betWon": bool(bet_result.get("won", False)),
            "betPayout": int(bet_result.get("payout", 0)),
            "betMultiplier": int(bet_result.get("multiplier", 0)),
            "hint": hint_text,
            "hintYears": hint_years,
            "hintUsed": bool(self.hint_used.get(player_id)),
            "screen": screen_for_player,
            "quickChips": list(self.vox_quick_chips.get(player_id, [])),
            "chatRole": self.chat_roles.get(player_id, ""),
            "secretGoal": self.chat_goals.get(player_id, ""),
            "remixPool": self.remix_pools.get(player_id, {}),
            "remixChoice": self.remix_choices.get(player_id, {}),
            "incomingTaunt": incoming,
            "finalBoost": float(self.final_boost.get(player_id, 0.0)),
            "lotRole": lot_role,
            "lotId": lot_id,
            "lotHint": lot_hint,
            "waitBonus": player_id in self.wait_bonus,
        }

    def snapshot(self, role: str, player_id: str | None, now: float, display_authorized: bool = False) -> dict[str, Any]:
        reveal = self.phase == "reveal"
        screen = self._screen()
        players = self.room.players_payload()
        options = self._public_options(reveal)
        for option in options:
            option["isYours"] = bool(
                player_id and any(
                    candidate.get("id") == option["id"]
                    and (candidate.get("remixerId") or candidate.get("authorId")) == player_id
                    for candidate in self.options
                )
            )
        prompt = ""
        instruction = ""
        hint = ""
        if self.game == "ultima_palabra":
            pair = self._active_duel()
            if self.phase == "duel" and pair is not None:
                prompt = pair["prompt"]
            else:
                prompt = str(self.data.get("duel_prompts", {}).get(player_id or "") or "")
                if not prompt and self.duel_pairs:
                    prompt = self.duel_pairs[0]["prompt"]
            instruction = "Duelo A/B: escribe la respuesta más graciosa para tu frase."
        elif self.game == "vox_populi":
            if self.phase == "vote":
                prompt = f"¿La respuesta misteriosa va arriba o abajo del puesto 3? «{self.vox_mystery.get('text','') if self.vox_mystery else ''}»"
                instruction = "Vota MAS_ARRIBA o MAS_ABAJO en tu teléfono."
                hint = f"El puesto 3 ya revelado es: {self.vox_rank3.get('text','') if self.vox_rank3 else ''}"
            else:
                prompt = str((self.data.get("question") or {}).get("text", ""))
                instruction = "Escribe o toca un chip con la respuesta que crees más popular."
                hint = "No tiene que ser la verdad; tiene que ser popular."
        elif self.game == "fuera_de_epoca":
            if self.phase == "sort":
                prompt = "Ordena estos tres eventos del más antiguo al más reciente."
                instruction = "Sube y baja las tarjetas en tu consola temporal."
                hint = "Los tres en orden exacto pagan 600; un acierto parcial paga 250."
            else:
                prompt = str((self.data.get("event") or {}).get("event", ""))
                instruction = "Elige el año correcto."
                hint = "La racha paga un bonus por cada acierto consecutivo."
        elif self.game == "texto_roto":
            scene = self.data.get("scene") or {}
            prompt = str(scene.get("title", ""))
            if self.phase == "chat":
                role = self.chat_roles.get(player_id or "", "REACTOR")
                instruction = "Escribe tu línea." if role == "WRITER" else "Reacciona al último mensaje."
                hint = self.chat_goals.get(player_id or "", str(scene.get("seed", "")))
            else:
                instruction = "Vota la línea más graciosa de la conversación."
                hint = "No puedes votar la tuya."
        elif self.game == "franela_fest":
            if self.phase == "remix":
                prompt = "Ensambla una camiseta con un dibujo ajeno y un eslogan ajeno."
                instruction = "Elige 1 dibujo y 1 eslogan de tu pool."
                hint = "El director creativo, el artista y el copywriter se reparten los puntos."
            else:
                prompt = str((self.data.get("prompt") or {}).get("prompt", ""))
                instruction = "Dibuja tu camiseta y escribe DOS eslóganes."
                hint = "El dibujo y los eslóganes se mezclarán con los de otros jugadores."
        elif self.game == "el_tasador":
            if self.phase == "invest":
                prompt = "Lote A y Lote B están sobre la mesa. Elige dónde pujar."
                instruction = "Puja por un lote o pasa. El valor real se revela al final."
                hint = "Los vendedores no pueden pujar; los compradores sí."
            else:
                lot_id = "A" if self.sellers and player_id == self.sellers[0] else "B"
                lot = self.lots.get(lot_id) or {}
                obj = lot.get("object") or {}
                prompt = str(obj.get("name", ""))
                instruction = "Escribe tu pitch. Eres vendedor." if player_id in self.sellers else "Inspecciona el catálogo y prepárate para pujar."
                hint = str(obj.get("description", ""))

        years = list(self.data.get("years") or []) if self.game == "fuera_de_epoca" else []
        you = self._you(player_id)
        host = self._host_cue()
        banter_pool = BANTER_BY_GAME.get(self.game, {}).get(self.phase, ())
        banter = (
            banter_pool[_rng(self.room.code, self.game, self.round_index, f"banter:{self.phase}").randrange(len(banter_pool))]
            if banter_pool else ""
        )
        highlights = []
        if self.phase == "podium":
            ranked = sorted(
                [p for p in self.room.players.values() if not p.spectator],
                key=lambda p: (-p.score, p.joined_at),
            )
            for index, player in enumerate(ranked[:3]):
                highlights.append({
                    "id": f"fiesta_{index}", "title": ["Campeón", "Subcampeón", "Tercer lugar"][index],
                    "name": player.name, "detail": f"{player.score} puntos",
                    "color": [0xFFFBBF24, 0xFF94A3B8, 0xFFB45309][index],
                })
        out = {
            "code": self.room.code,
            "displayCode": _format_code(self.room.code),
            "mode": self.room.mode,
            "game": self.game,
            "rulesVersion": 4,
            "pack": self.game,
            "phase": self.phase,
            "rev": self.room.rev,
            "serverNow": int(now * 1000),
            "phaseEndsAt": int((self.phase_started + self._duration()) * 1000) if self._duration() > 0 else 0,
            "phaseDuration": int(self._duration()),
            "round": max(1, min(self.round_index, self.rounds)),
            "rounds": self.rounds,
            "hostId": self.room.host_id,
            "players": players,
            "playersPace": False,
            "answeredCount": len(self.submitted),
            "readyCount": 0,
            "allReady": True,
            "question": None,
            "answer": None,
            "trivia": None,
            "charada": None,
            "wager": None,
            "impostor": None,
            "drawing": None,
            "auction": None,
            "fibbage": None,
            "audience": sum(1 for p in self.room.players.values() if p.spectator and p.connected),
            "reactions": list(self.room.reactions),
            "highlights": highlights,
            "you": {
                "id": player_id or "",
                "name": (self.room.players.get(player_id).name if player_id and player_id in self.room.players else ""),
                "score": int(you.get("score", 0)),
                "streak": 0,
                "color": (self.room.players.get(player_id).color if player_id and player_id in self.room.players else 0),
                "answered": bool(you.get("submitted")),
                "selected": int(you.get("choice", -1)),
                "points": int(you.get("points", 0)),
                "correct": False,
                "ready": False,
                "spectator": bool(player_id and player_id in self.room.players and self.room.players[player_id].spectator),
                "submitted": bool(you.get("submitted")),
                "voted": bool(you.get("voted")),
                "coins": int(you.get("coins", 0)),
                "perk": str(you.get("perk", "")),
                "activePerk": str(you.get("activePerk", "")),
                "bonusPick": int(you.get("bonusPick", -1)),
                "submittedOrder": int(you.get("submittedOrder", 0)),
                "bet": int(you.get("bet", 0)),
                "betWon": bool(you.get("betWon", False)),
                "betPayout": int(you.get("betPayout", 0)),
                "hint": str(you.get("hint", "")),
                "hintYears": list(you.get("hintYears") or []),
            },
            "fiesta": {
                "game": self.game,
                "title": self.info["title"],
                "host": HOST_BY_GAME[self.game],
                "phase": self.phase,
                "screen": screen,
                "prompt": prompt,
                "instruction": instruction,
                "hint": hint,
                "round": max(1, min(self.round_index, self.rounds)),
                "rounds": self.rounds,
                "finalRound": self.round_index == self.rounds,
                "options": options,
                "years": years,
                "correct": int(self.data.get("correct", -1)) if self.game == "fuera_de_epoca" and reveal else -1,
                "object": (
                    _public_object((self.lots.get("A") or {}).get("object"))
                    if self.game == "el_tasador" and self.lots else None
                ),
                "trueValue": int((self.lots.get("A") or {}).get("realValue", 0)) if self.game == "el_tasador" and reveal else 0,
                "you": you,
                "submittedCount": len(self.submitted),
                "voterCount": len(self._players()),
                "reveal": self.reveal,
                "hostCue": host,
                "family": bool(self.room.family_mode),
                "adult": not bool(self.room.family_mode),
                "soloTest": bool(self.solo_test),
                "twist": dict(self.twist),
                "perks": [dict(perk) for perk in PERKS] + ([dict(ALL_IN_PERK)] if self.round_index == self.rounds - 1 and self.phase == "reveal" else []),
                "bonus": self.bonus,
                "banter": banter,
                "coins": int(you.get("coins", 0)),
                "perk": str(you.get("perk", "")),
                "activePerk": str(you.get("activePerk", "")),
                "currency": self.info.get("currency", "Monedas"),
                "currencyIcon": self.info.get("currencyIcon", "VP"),
                "shop": self.info.get("shop", "Tienda de Fiesta"),
                "signature": self.info.get("signature", ""),
                "taunts": list(self.taunts),
                "tauntChoices": list(TAUNTS.get(self.game, ())),
                "hintCost": HINT_COST,
                "tauntCost": TAUNT_COST,
                "betOptions": [100, 250, 500],
                "duelState": self._duel_payload(),
                "voxState": self._vox_payload(reveal),
                "sortItems": list(self.sort_items) if self.phase in ("sort", "reveal") else [],
                "chatMessages": [
                    {
                        "id": message["id"],
                        "text": message["text"],
                        "role": message["role"],
                        "authorName": (
                            message["authorName"] if reveal or message.get("playerId") == player_id else "ANÓNIMO"
                        ),
                        "reactions": int(message.get("reactions", 0)),
                        "subturn": int(message.get("subturn", 0)),
                        "isYours": bool(player_id and message.get("playerId") == player_id),
                    }
                    for message in self.chat_messages
                ],
                "lots": self._lots_payload(reveal),
                "reactionBanner": self.reaction_banner,
                "audience": sum(1 for p in self.room.players.values() if p.spectator and p.connected),
                "audienceVotes": self._audience_vote_counts(),
                "scoreboard": players,
            },
        }
        if self.phase == "podium":
            out["podium"] = players
        return out


def _public_object(obj: Any) -> Any:
    if not isinstance(obj, dict):
        return obj
    return {key: value for key, value in obj.items() if key != "value"}


def _format_code(code: str) -> str:
    clean = str(code or "").upper()
    return f"{clean[:3]}-{clean[3:]}" if len(clean) == 6 else clean


def catalog() -> list[dict[str, Any]]:
    return [dict(FIESTA_INFO[game]) for game in FIESTA_GAMES]

# Fase 4 · Cada juego tiene material suficiente para partidas largas.
TAUNTS = {
    **TAUNTS,
    "ultima_palabra": TAUNTS["ultima_palabra"] + ("NIÑO, NO", "A OTRA COSA", "SIN CHISTE", "QUÉ PENA"),
    "vox_populi": TAUNTS["vox_populi"] + ("FUENTE: MI TÍA", "SONDEO DE 3", "LA CALLE DUDÓ", "RATING BAJO", "PREGUNTA MALA"),
    "fuera_de_epoca": TAUNTS["fuera_de_epoca"] + ("CRONÓMETRO ROTO", "OTRO AÑO", "LÍNEA CRUZADA", "HISTORIA MAL", "VIAJE CANCELADO"),
    "texto_roto": TAUNTS["texto_roto"] + ("MODO AVIÓN", "TE DEJÓ EN VISTO", "GRUPO EQUIVOCADO", "AUDIO DE 5 MIN"),
    "franela_fest": TAUNTS["franela_fest"] + ("PASARELA DE TELAS", "COSTURA MAL", "COLOR EQUIVOCADO", "MODA DE TIANGUIS", "SIN ETIQUETA"),
    "el_tasador": TAUNTS["el_tasador"] + ("LOTE FALSO", "PUJA DE $1", "MARTILLO FRÍO", "SIN POSTORES", "VALE MENOS"),
}
