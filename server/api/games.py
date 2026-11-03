"""Vision+ Play — motor de salas (lógica pura, sin FastAPI ni base de datos).

Aquí vive todo lo que se puede probar sin red: códigos de sala, apodos, puntaje,
máquina de estados y el filtrado de lo que ve cada rol. Las rutas HTTP y el
almacén en memoria están en `app.py`; el banco de preguntas, en
`server/sync/trivia_bank.py`.

Reglas que este módulo garantiza:

- La respuesta correcta **solo** aparece en el snapshot de fase `reveal`.
- El puntaje se calcula con la hora de recepción del servidor, nunca con el reloj
  del cliente.
- Una respuesta por jugador y ronda; las tardías o duplicadas no suman.
- El host puede cambiar si se cae; la sala no se cierra por eso.
- Los tokens se guardan hasheados (SHA-256); el token en claro solo viaja al
  cliente que lo recibe al crear/unirse.
"""

from __future__ import annotations

import difflib
import hashlib
import hmac
import json
import random
import re
import secrets
import unicodedata
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

import game_decks
import fiesta as fiesta_engine

# --- Límites duros (plan §4.1) -------------------------------------------------
CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"  # sin I, L, O, 0, 1
CODE_LENGTH = 6
MAX_PLAYERS = 8
HARD_MAX_PLAYERS = 12
MAX_SPECTATORS = 8
MAX_ROOMS = 100
ROOM_TTL_SECONDS = 4 * 60 * 60
IDLE_SECONDS = 30 * 60
DISCONNECT_SECONDS = 90
COUNTDOWN_SECONDS = 3
QUESTION_SECONDS = 20
REVEAL_SECONDS = 5
DEFAULT_ROUNDS = 8
MAX_ROUNDS = 12
JOIN_RATE_LIMIT = 10  # por IP y minuto
MIN_QUESTION_SECONDS = 3
MAX_QUESTION_SECONDS = 60

PHASES = ("lobby", "countdown", "question", "reveal", "podium", "closed")
# Fase 3 añade dos juegos con sus propias fases. `turn` es el turno del actor
# (Charadas) y `wager`/`clue` son la apuesta y las tres pistas.
PHASES += ("turn", "wager", "clue")
# Juegos de fiesta asimétricos: el teléfono guarda secretos y la tele arbitra.
PHASES += ("secret", "questions", "vote", "guess", "draw", "title", "auction",
           "auction_reveal", "write")
MODES = ("remote", "display")
ROLES = ("player", "display")
GAMES = ("trivia", "charadas", "wager", "impostor", "dibujo", "subasta", "fibbage") + fiesta_engine.FIESTA_GAMES
GAME_DEFAULT_ROUNDS = {
    "trivia": 8, "charadas": 6, "wager": 6,
    "impostor": 3, "dibujo": 3, "subasta": 6, "fibbage": 4,
}
GAME_MIN_PLAYERS = {
    "trivia": 1, "charadas": 2, "wager": 1,
    "impostor": 3, "dibujo": 3, "subasta": 2, "fibbage": 2,
}
for _fiesta_id, _fiesta_info in fiesta_engine.FIESTA_INFO.items():
    GAME_DEFAULT_ROUNDS[_fiesta_id] = int(_fiesta_info["rounds"])
    GAME_MIN_PLAYERS[_fiesta_id] = int(_fiesta_info["minPlayers"])
BASE_POINTS = {1: 1000, 2: 1500, 3: 2000}
STREAK_STEP = 100
STREAK_CAP = 500
SPEED_CAP_PERCENT = 25
WAGER_MIN = 200
WAGER_MAX = 1000
WAGER_STAKES = (200, 500, 1000)
CHARADA_SECONDS = 60
CHARADA_MAX_PASSES = 3
CHARADA_POINTS = 1000
CHARADA_DIRECTOR_SECONDS = 5
CHARADA_BUZZ_SECONDS = 7
CHARADA_BUZZ_POINTS = 700
CHARADA_BUZZ_PENALTY = 200
CHARADA_HELP_GENRE_COST = 200
CHARADA_HELP_YEAR_COST = 300
CHARADA_SABOTEUR_POINTS = 600
CHARADA_DIRECTOR_BONUS = 300
CHARADA_RESTRICTIONS = (
    "Actúalo en cámara lenta",
    "Actúalo como si estuvieras llorando",
    "Solo puedes usar las manos; pies pegados al piso",
    "Actúalo como villano de telenovela",
    "Hazlo sin mover la cabeza",
)
WAGER_SECONDS = 10
CLUE_SECONDS = 15
CLUE_COUNT = 3
CLUE_FACTORS = (3, 2, 1)

# Juegos de fiesta
SECRET_SECONDS = 11
IMPOSTOR_SPEAK_SECONDS = 30
COARTADA_SECONDS = 8
VOTE_SECONDS = 22
GUESS_SECONDS = 20
IMPOSTOR_ACCOMPLICE_POINTS = 700
IMPOSTOR_DETECTIVE_POINTS = 300
DRAW_SECONDS = 45
DRAW_TITLE_SECONDS = 30
DRAW_VOTE_SECONDS = 22
AUCTION_SECONDS = 15
AUCTION_REVEAL_SECONDS = 12
AUCTION_STEP = 50
AUCTION_STEAL_PERCENT = 25
AUCTION_STEAL_MIN = 250
AUCTION_STEAL_MAX = 600
AUCTION_VAULT_RATE = 0.5
FIBBAGE_WRITE_SECONDS = 30
FIBBAGE_VOTE_SECONDS = 22
IMPOSTOR_CREW_POINTS = 1000
IMPOSTOR_CREW_BONUS = 300
IMPOSTOR_ESCAPE_POINTS = 1500
IMPOSTOR_GUESS_POINTS = 800
DRAW_REAL_POINTS = 1000
DRAW_LIE_POINTS = 500
DRAW_ARTIST_POINTS = 600
DRAW_ARTIST_PENALTY = 500
DRAW_LIE_SWEEP_BONUS = 300
DRAW_INK_LIMIT = 100
DRAW_CRITIC_BOMB = 200
DRAW_CRITIC_CROWN = 300
FIBBAGE_REAL_POINTS = 1000
FIBBAGE_LIE_POINTS = 500
FIBBAGE_LIE_SWEEP_BONUS = 300
FIBBAGE_SCAN_COST = 300
FIBBAGE_POISON_COST = 150
FIBBAGE_POISON_STEAL = 300
FIBBAGE_CERTAINTY_MULT = 1.5
FIBBAGE_CERTAINTY_RISK = 300
FIBBAGE_HUNTER_BONUS = 300
MIN_PARTY_OPTIONS = 5
AUCTION_START_COINS = 1000
MAX_STROKES = 160
MAX_STROKE_POINTS = 2400

# Fase 5 — Trivia 2.0 ("El piso se quema") y Apuesta 2.0 ("Bóveda ciega").
# Estas reglas solo se activan con `rules_version=2`; las salas viejas siguen
# jugando exactamente como antes.
TRIVIA_ATTACK_SECONDS = 6
TRIVIA_PUNISH_SECONDS = 8
TRIVIA_PUNISH_REVEAL_SECONDS = 5
TRIVIA_SORT_SECONDS = 18
TRIVIA_ASSAULT_SECONDS = 25
TRIVIA_REVEAL_SECONDS = 8
TRIVIA_ATTACK_COST = 200
TRIVIA_FREEZE_TAPS = 5
TRIVIA_PARASITE_FAIL = 300
TRIVIA_PARASITE_SHARE = 0.75
TRIVIA_PUNISH_PENALTY = 500
TRIVIA_SORT_POINTS = 250
TRIVIA_ASSAULT_CORRECT = 400
TRIVIA_ASSAULT_WRONG = 400
TRIVIA_PUNISH_ROUNDS = (3, 6)
TRIVIA_ATTACK_POWERS = ("freeze", "glitch", "parasite")
WAGER_START_BANK = 1000
WAGER_ANTE = 100
WAGER_BUY_COST = 200
WAGER_SIDE_BET = 200
WAGER_V2_FACTORS = (4, 2, 1)
WAGER_ANTE_SECONDS = 5

# Palabras que no deben bastar para ganar la adivinanza temprana de Dibujo.
EARLY_GUESS_STOPWORDS = frozenset({
    "que", "una", "uno", "unos", "unas", "con", "por", "para", "como",
    "los", "las", "del", "sin", "sus", "the", "and", "este",
    "esta", "estos", "estas", "sobre", "entre", "desde", "hasta", "muy",
})


def _legacy_catalog() -> list[dict]:
    """Carta histórica; se conserva para los juegos que siguen vivos."""
    return [
        {
            "id": "trivia",
            "title": "Trivia Vision+",
            "tagline": "El clásico de Vision+ Play",
            "description": "Ocho rondas de supervivencia. La racha desbloquea ataques para "
                           "congelar, censurar o colgarte de un rival; en las rondas de castigo, "
                           "los que fallan cortan un cable.",
            "minPlayers": 1,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 12,
            "rounds": GAME_DEFAULT_ROUNDS["trivia"],
            "accent": "#2D7DF6",
            "steps": ["Responde rápido; dos aciertos seguidos desbloquean un ataque",
                      "Rondas 4 y 5: clasifica cartas antes/después",
                      "Ronda 8: selecciona todas las respuestas correctas"],
        },
        {
            "id": "charadas",
            "title": "Charadas",
            "tagline": "Actúa y adivina",
            "description": "Por turnos, un jugador ve un título en su teléfono y lo actúa. "
                           "El resto adivina en voz alta. Tienes 60 segundos y tres pases.",
            "minPlayers": 2,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 10,
            "rounds": GAME_DEFAULT_ROUNDS["charadas"],
            "accent": "#FF8A65",
            "steps": ["El teléfono del actor muestra el título",
                      "Actúa sin decir el nombre ni partes de él",
                      "Marca Correcto o Pasa antes de que acabe el tiempo"],
        },
        {
            "id": "wager",
            "title": "Apuesta a la pista",
            "tagline": "Tres pistas, una apuesta",
            "description": "Póker de pistas: todos pagan una ciega, el póster se revela por "
                           "etapas y puedes comprar más pistas o bloquear tu respuesta. El pozo "
                           "se reparte por factor x4, x2 o x1.",
            "minPlayers": 1,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 10,
            "rounds": GAME_DEFAULT_ROUNDS["wager"],
            "accent": "#B388FF",
            "steps": ["Paga la ciega y mira el póster pixelado",
                      "Bloquea tu respuesta o compra la siguiente pista",
                      "Acierta antes que los demás y llévate el pozo"],
        },
        {
            "id": "impostor",
            "title": "El Impostor",
            "tagline": "Uno miente. Todos dudan.",
            "description": "Todos reciben el mismo lugar secreto… menos uno. Hagan preguntas rápidas, "
                           "lean las caras y voten quién está fingiendo. Si lo descubren, el impostor "
                           "tiene una última oportunidad de robar la victoria.",
            "minPlayers": 3,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 10,
            "rounds": GAME_DEFAULT_ROUNDS["impostor"],
            "accent": "#FF5C7A",
            "steps": ["Tu teléfono guarda el lugar o tu papel de impostor",
                      "Preguntas rápidas: todos hablan, nadie dice el lugar",
                      "Voten al mentiroso; si lo atrapan, puede adivinar el lugar"],
        },
        {
            "id": "dibujo",
            "title": "Duelo de Dibujo Ciego",
            "tagline": "Todos mienten, uno dibuja",
            "description": "Un jugador recibe una frase absurda y la dibuja en su teléfono. La tele "
                           "muestra el dibujo sin título. Los demás inventan títulos falsos y votan "
                           "por el verdadero. Cada trampa que muerda suma.",
            "minPlayers": 3,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 12,
            "rounds": GAME_DEFAULT_ROUNDS["dibujo"],
            "accent": "#4DD0E1",
            "steps": ["El artista dibuja en su teléfono, sin escribir",
                      "Los demás inventan un título falso",
                      "Voten el título real; las mentiras con votos también suman"],
        },
        {
            "id": "subasta",
            "title": "Subasta Ciega",
            "tagline": "Farol, codicia y martillo",
            "description": "Mercado negro: seis lotes con carta pública y carta secreta. "
                           "Dos informantes ven la trampa y la sala farolea. El segundo lugar "
                           "también puede pagar en el lote maldito.",
            "minPlayers": 2,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 6,
            "rounds": GAME_DEFAULT_ROUNDS["subasta"],
            "accent": "#F5C518",
            "steps": ["Mira la carta y calcula cuánto vale para ti",
                      "Puja en secreto desde el teléfono",
                      "El martillo revela: empates se cancelan y gana el siguiente"],
        },
        {
            "id": "fibbage",
            "title": "Falso Conocimiento",
            "tagline": "Si no sabes, invéntalo",
            "description": "La tele lanza un dato insólito de cine y series. Nadie sabe la respuesta "
                           "exacta: cada jugador inventa una mentira convincente. Luego todos votan "
                           "la que creen verdadera. La respuesta real también está en la lista.",
            "minPlayers": 2,
            "maxPlayers": MAX_PLAYERS,
            "approxMinutes": 10,
            "rounds": GAME_DEFAULT_ROUNDS["fibbage"],
            "accent": "#A5D6A7",
            "steps": ["Lee el dato raro en la tele",
                      "Inventa una respuesta falsa y creíble",
                      "Vota la que creas real; si muerden tu mentira, sumas"],
        },
    ]


def catalog() -> list[dict]:
    """Selector vigente: históricos vivos + Fiesta 3.0.

    Charadas y Apuesta a la pista se retiraron del catálogo; su código sigue
    disponible solo para drenar salas viejas.
    """
    legacy = [
        item for item in _legacy_catalog()
        if item["id"] not in ("charadas", "wager")
    ]
    return legacy + fiesta_engine.catalog()


def game_default_rounds(game: str) -> int:
    return GAME_DEFAULT_ROUNDS.get(str(game or "trivia"), GAME_DEFAULT_ROUNDS["trivia"])

_CONTROL = re.compile(r"[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e]")
_SPACES = re.compile(r"\s+")
_WORD = re.compile(r"[a-z0-9]+")

# Lista corta y deliberada: no pretende ser exhaustiva, solo evitar los apodos
# que arruinan una sala familiar. Se compara por palabra completa.
_PROFANITY = frozenset({
    "puta", "puto", "pendejo", "pendeja", "mierda", "cabron", "cabrona",
    "verga", "chinga", "chingar", "chingado", "joder", "gilipollas",
    "marica", "maricon", "culero", "culera", "zorra", "polla", "pito",
    "cojer", "coger", "mamada", "mamon", "mamona",
})


def fold(text: Any) -> str:
    """Minúsculas, sin acentos y con signos convertidos en espacios."""
    decomposed = unicodedata.normalize("NFD", str(text or "").lower())
    plain = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    spaced = "".join(ch if ch.isalnum() else " " for ch in plain)
    return " ".join(spaced.split())


def compact(text: Any) -> str:
    return fold(text).replace(" ", "")


# --- Códigos y tokens ---------------------------------------------------------

def generate_code(rng: random.Random | None = None) -> str:
    """Seis caracteres del alfabeto sin ambigüedades (31^6 combinaciones)."""
    source = rng or secrets.SystemRandom()
    return "".join(source.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))


def normalize_code(raw: Any) -> str:
    """«abc-123», «ABC 123» y «ABC123» son el mismo código; lo demás es inválido."""
    text = _SPACES.sub("", str(raw or "")).replace("-", "").replace("_", "").upper()
    if len(text) != CODE_LENGTH:
        return ""
    if any(ch not in CODE_ALPHABET for ch in text):
        return ""
    return text


def format_code(code: str) -> str:
    clean = normalize_code(code)
    return f"{clean[:3]}-{clean[3:]}" if clean else ""


def new_token() -> str:
    """Token opaco de 32 caracteres (el cliente no puede adivinarlo)."""
    return secrets.token_urlsafe(24)


def token_hash(token: str) -> str:
    return hashlib.sha256(str(token or "").encode("utf-8")).hexdigest()


def token_ok(token: str | None, hashed: str) -> bool:
    if not token or not hashed:
        return False
    return hmac.compare_digest(token_hash(token), hashed)


# --- Apodos y colores ---------------------------------------------------------

def clean_name(raw: Any, limit: int = 14) -> str:
    """Apodo seguro: 2..14 caracteres, sin controles, sin groserías evidentes."""
    text = _CONTROL.sub("", str(raw or "")).strip()
    text = _SPACES.sub(" ", text)
    if len(text) > limit:
        text = text[:limit].strip()
    if len(text) < 2:
        return ""
    words = set(_WORD.findall(fold(text)))
    if words & _PROFANITY:
        return ""
    return text


def color_for(name: str) -> int:
    """Color estable 0..7 (la app tiene la misma paleta de 8 en ProfileAvatar)."""
    return sum(ord(ch) for ch in str(name or "")) % 8


# --- Puntaje (la misma tabla vive en Kotlin: GameScore.kt) --------------------

def base_for(difficulty: int) -> int:
    return BASE_POINTS.get(int(difficulty or 1), BASE_POINTS[1])


def speed_bonus(base: int, remaining_ms: int, total_ms: int) -> int:
    """Bono de velocidad topado al 25 % de la base, redondeado hacia arriba."""
    total = int(total_ms or 0)
    remaining = max(0, min(int(remaining_ms or 0), total))
    if total <= 0 or remaining <= 0:
        return 0
    # base * 25 % * fracción, con medio punto hacia arriba en aritmética entera.
    return (int(base) * 25 * remaining + total * 50) // (total * 100)


def answer_points(
    difficulty: int,
    remaining_ms: int,
    total_ms: int,
    streak: int,
    correct: bool,
    fair: bool = False,
) -> tuple[int, int]:
    """Devuelve (puntos, racha_nueva). Incorrecta o tardía = (0, 0)."""
    if not correct:
        return 0, 0
    base = base_for(difficulty)
    new_streak = max(0, int(streak or 0)) + 1
    bonus = 0 if fair else speed_bonus(base, remaining_ms, total_ms)
    streak_bonus = min(STREAK_STEP * (new_streak - 1), STREAK_CAP)
    return base + bonus + streak_bonus, new_streak


def clamp_wager(value: Any) -> int:
    try:
        stake = int(value)
    except (TypeError, ValueError):
        stake = WAGER_MIN
    return max(WAGER_MIN, min(WAGER_MAX, stake))


def apply_wager(points: int, stake: Any, correct: bool) -> int:
    """Apuesta (Fase 3): nunca deja puntos negativos."""
    current = max(0, int(points or 0))
    amount = clamp_wager(stake)
    if correct:
        return current + amount
    return max(0, current - min(amount, current))


def _clean_int_list(value: Any, size: int, *, allow_empty: bool = False) -> list[int]:
    if not isinstance(value, (list, tuple)):
        return []
    out: list[int] = []
    for item in value:
        try:
            number = int(item)
        except (TypeError, ValueError):
            continue
        if 0 <= number < size and number not in out:
            out.append(number)
    return out


def _clean_choice_list(value: Any, size: int) -> list[int]:
    """Lista posicional: los ceros y los repetidos importan."""
    if not isinstance(value, (list, tuple)):
        return []
    out: list[int] = []
    for item in value:
        try:
            number = int(item)
        except (TypeError, ValueError):
            return []
        if number < 0 or number >= size:
            return []
        out.append(number)
    return out


def _sort_item_text(question: dict) -> str:
    payload = question.get("payload") if isinstance(question, dict) else {}
    if isinstance(payload, dict):
        text = _clean_game_text(payload.get("prompt"), 180)
        if text:
            return text
    return _clean_game_text(question.get("prompt") if isinstance(question, dict) else "", 180)


def _year_sort_items(questions: list[dict], threshold: int, limit: int,
                     seed: str, round_index: int) -> list[dict]:
    """Convierte preguntas de año en tarjetas binarias antes/después.

    No inventa datos: usa el `answer_key` numérico que ya validó el banco.
    Si no hay suficientes preguntas de año, devuelve [] y el juego degrada a
    opción múltiple.
    """
    rng = random.Random(f"sort:{seed}:{round_index}:{threshold}")
    candidates: list[dict] = []
    for question in questions or []:
        if not isinstance(question, dict):
            continue
        if str(question.get("kind") or "") != "year":
            continue
        text = _sort_item_text(question)
        try:
            year = int(str(question.get("answer_key") or question.get("answer") or "").strip())
        except (TypeError, ValueError):
            continue
        if not text or year < 1900 or year > 2100:
            continue
        payload = question.get("payload") if isinstance(question.get("payload"), dict) else {}
        candidates.append({
            "id": str(question.get("id") or ""),
            "text": text,
            "art": str(payload.get("art") or ""),
            "answer": 0 if year < threshold else 1,
            "year": year,
        })
    if len(candidates) < limit:
        return []
    rng.shuffle(candidates)
    # Equilibra las dos respuestas para que no se pueda ganar siempre con una.
    left = [item for item in candidates if item["answer"] == 0]
    right = [item for item in candidates if item["answer"] == 1]
    out: list[dict] = []
    while len(out) < limit and (left or right):
        pool = left if (len(out) % 2 == 0 and left) or not right else right
        if not pool:
            pool = right if not left else left
        out.append(pool.pop())
    return out


def _glitch_text(text: str, seed: str) -> str:
    rng = random.Random(f"glitch:{seed}")
    out = []
    for index, char in enumerate(str(text or "")):
        if char.isalpha() and index % 2 == 1 and rng.random() < 0.78:
            out.append("_")
        else:
            out.append(char)
    return "".join(out)


def difficulty_for(popularity: Any, votes: Any) -> int:
    try:
        pop = float(popularity or 0)
    except (TypeError, ValueError):
        pop = 0.0
    try:
        count = int(votes or 0)
    except (TypeError, ValueError):
        count = 0
    if pop >= 50 or count >= 2000:
        return 1
    if pop >= 5 or count >= 200:
        return 2
    return 3


# --- Modelo -------------------------------------------------------------------

@dataclass
class Answer:
    option: int
    at: float
    correct: bool
    points: int
    streak_after: int


@dataclass
class Player:
    id: str
    name: str
    color: int
    token_hash: str
    joined_at: float
    last_seen: float
    connected: bool = True
    score: int = 0
    streak: int = 0
    answers: dict[int, Answer] = field(default_factory=dict)
    ready_round: int = 0
    last_reaction: float = 0.0
    # Apuesta a la pista (se reinicia en cada ronda).
    wager_round: int = 0
    wager: int = 0
    wager_done_round: int = 0
    wager_delta: int = 0
    wager_option: int = -1
    wager_correct: bool = False
    clue_answered: int = -1
    # Juegos de fiesta asimétricos.
    spectator: bool = False
    coins: int = AUCTION_START_COINS
    submitted_round: int = 0
    vote_round: int = 0
    bid_round: int = 0
    bid: int = 0
    lies_votes: int = 0
    detective: int = 0
    auction_wins: int = 0
    impostor_wins: int = 0
    shield: bool = False
    double_next: bool = False
    ghost_used: bool = False
    charada_buzz_cooldown: float = 0.0
    wager_private_stage: int = 1
    # Trivia 2.0 (se reinicia cada ronda).
    attack_ready_round: int = 0
    attack_power: str = ""
    attack_target: str = ""
    frozen_taps: int = 0
    glitched: bool = False
    parasite_target: str = ""
    punishment_wire: str = ""
    sort_round: int = 0
    sort_choices: list[int] = field(default_factory=list)
    assault_round: int = 0
    assault_selected: list[int] = field(default_factory=list)
    # Apuesta 2.0 (se reinicia cada ronda).
    wager_stage_locked: int = 0
    wager_locked_option: int = -1
    wager_bought_stage: int = 0
    wager_side_target: str = ""
    wager_side_amount: int = 0
    fibbage_scan_used: bool = False
    fibbage_certainty: bool = False
    fibbage_hunter_guess: str = ""


@dataclass
class Room:
    code: str
    mode: str
    pack: str
    rounds: int
    question_seconds: int
    reveal_seconds: int
    countdown_seconds: int
    fair: bool
    max_players: int
    questions: list[dict]
    host_token_hash: str
    host_id: str
    game: str = "trivia"
    rules_version: int = 1
    deck: list[dict] = field(default_factory=list)
    players_pace: bool = False
    charada_seconds: int = CHARADA_SECONDS
    wager_seconds: int = WAGER_SECONDS
    clue_seconds: int = CLUE_SECONDS
    actor_id: str = ""
    charada_cursor: int = 0
    charada_index: int = 0
    charada_correct: int = 0
    charada_passes: int = 0
    charada_last: dict = field(default_factory=dict)
    charada_saboteur_id: str = ""
    charada_director_id: str = ""
    charada_director_options: list[str] = field(default_factory=list)
    charada_restriction: str = ""
    charada_director_chosen: bool = False
    charada_buzzer_id: str = ""
    charada_buzzer_started: float = 0.0
    charada_buzzer_remaining: float = 0.0
    charada_help_genre: bool = False
    charada_help_year: bool = False
    charada_help_penalty: int = 0
    clue_index: int = 0
    # El Impostor.
    impostor_id: str = ""
    impostor_word: str = ""
    impostor_category: str = ""
    impostor_hint: str = ""
    impostor_questions: list[str] = field(default_factory=list)
    impostor_speaker_ids: list[str] = field(default_factory=list)
    impostor_speaker_index: int = 0
    impostor_votes: dict[str, str] = field(default_factory=dict)
    impostor_guess: str = ""
    impostor_guess_options: list[str] = field(default_factory=list)
    impostor_caught: bool = False
    impostor_guess_correct: bool = False
    impostor_accomplice_id: str = ""
    impostor_detective_id: str = ""
    impostor_detective_options: list[str] = field(default_factory=list)
    impostor_coartada: dict[str, int] = field(default_factory=dict)
    # Duelo de dibujo.
    drawing_artist_id: str = ""
    drawing_prompt: str = ""
    drawing_strokes: list[dict] = field(default_factory=list)
    drawing_titles: dict[str, str] = field(default_factory=dict)
    drawing_options: list[dict] = field(default_factory=list)
    drawing_votes: dict[str, str] = field(default_factory=dict)
    drawing_audience_votes: dict[str, str] = field(default_factory=dict)
    drawing_decoys: list[str] = field(default_factory=list)
    drawing_prompt_options: list[dict] = field(default_factory=list)
    drawing_partial_at: float = 0.0
    drawing_audience_choice: str = ""
    drawing_audience_bonus_id: str = ""
    drawing_ink_used: float = 0.0
    drawing_ink_limit: float = 0.0
    drawing_flashlight: bool = False
    drawing_critic_bombs: dict[str, str] = field(default_factory=dict)
    drawing_critic_crowns: dict[str, str] = field(default_factory=dict)
    drawing_early_guesses: dict[str, str] = field(default_factory=dict)
    drawing_early_winners: dict[str, int] = field(default_factory=dict)
    drawing_early_attempts: dict[str, int] = field(default_factory=dict)
    # Subasta.
    auction_item: dict = field(default_factory=dict)
    auction_bids: dict[str, int] = field(default_factory=dict)
    auction_winner: str = ""
    auction_price: int = 0
    auction_cancelled: list[str] = field(default_factory=list)
    auction_tie: bool = False
    auction_effect: str = ""
    auction_cashout: dict[str, int] = field(default_factory=dict)
    auction_secret_effect: str = ""
    auction_secret_value: int = 0
    auction_informants: list[str] = field(default_factory=list)
    auction_ghost_bidder: str = ""
    auction_second_pays: bool = False
    # Fibbage.
    fibbage_prompt: str = ""
    fibbage_answer: str = ""
    fibbage_hint: str = ""
    fibbage_category: str = ""
    fibbage_lies: dict[str, str] = field(default_factory=dict)
    fibbage_options: list[dict] = field(default_factory=list)
    fibbage_votes: dict[str, str] = field(default_factory=dict)
    fibbage_audience_votes: dict[str, str] = field(default_factory=dict)
    fibbage_decoys: list[str] = field(default_factory=list)
    fibbage_audience_choice: str = ""
    fibbage_audience_bonus_id: str = ""
    fibbage_scans: dict[str, list[str]] = field(default_factory=dict)
    fibbage_poisoned: set[str] = field(default_factory=set)
    fibbage_certainty: set[str] = field(default_factory=set)
    fibbage_hunter: dict[str, str] = field(default_factory=dict)
    fibbage_final_shared: bool = False
    # Trivia 2.0.
    trivia_round_kind: str = "multiple_choice"
    trivia_attack_power: dict[str, str] = field(default_factory=dict)
    trivia_attack_target: dict[str, str] = field(default_factory=dict)
    trivia_attacks: list[dict] = field(default_factory=list)
    trivia_frozen: dict[str, int] = field(default_factory=dict)
    trivia_glitched: set[str] = field(default_factory=set)
    trivia_parasite: dict[str, str] = field(default_factory=dict)
    trivia_punishment_votes: dict[str, str] = field(default_factory=dict)
    trivia_punishment_wire: dict[str, str] = field(default_factory=dict)
    trivia_punishment_hot: str = ""
    trivia_punishment_penalties: dict[str, int] = field(default_factory=dict)
    trivia_sort_items: list[dict] = field(default_factory=list)
    trivia_sort_correct: list[int] = field(default_factory=list)
    trivia_assault_items: list[dict] = field(default_factory=list)
    trivia_assault_correct: list[int] = field(default_factory=list)
    # Apuesta 2.0.
    wager_stage: int = 1
    wager_pot: int = 0
    wager_rollover: int = 0
    wager_locked: dict[str, int] = field(default_factory=dict)
    wager_locked_option: dict[str, int] = field(default_factory=dict)
    wager_side_bets: dict[str, dict] = field(default_factory=dict)
    wager_side_results: dict[str, int] = field(default_factory=dict)
    # Puntos de la ronda para el snapshot `you`.
    deltas: dict[str, int] = field(default_factory=dict)
    players: dict[str, Player] = field(default_factory=dict)
    phase: str = "lobby"
    phase_started: float = 0.0
    round_index: int = 0
    rev: int = 1
    created_at: float = 0.0
    last_activity: float = 0.0
    started_at: float = 0.0
    ended_at: float = 0.0
    closed_reason: str = ""
    reactions: list[dict] = field(default_factory=list)
    reports: dict[str, int] = field(default_factory=dict)
    reports_by_player: dict[str, set[str]] = field(default_factory=dict)
    recent_actions: dict[str, float] = field(default_factory=dict)
    family_mode: bool = True
    fiesta: Any = None
    _player_seq: int = 0

    # -- tokens ---------------------------------------------------------------
    def player_by_token(self, token: str | None) -> Player | None:
        if not token:
            return None
        hashed = token_hash(token)
        for player in self.players.values():
            if hmac.compare_digest(player.token_hash, hashed):
                return player
        return None

    def host_ok(self, token: str | None) -> bool:
        if not token:
            return False
        if token_ok(token, self.host_token_hash):
            return True
        player = self.player_by_token(token)
        return bool(player and player.id == self.host_id)

    # -- jugadores ------------------------------------------------------------
    def add_player(
        self,
        name: str,
        token: str,
        now: float,
        *,
        player_id: str | None = None,
        spectator: bool = False,
    ) -> Player | None:
        if not spectator and self.phase not in ("lobby", "countdown"):
            return None
        if not spectator and len([p for p in self.players.values() if not p.spectator]) >= self.max_players:
            return None
        if spectator and len([p for p in self.players.values() if p.spectator]) >= MAX_SPECTATORS:
            return None
        clean = clean_name(name)
        if not clean:
            return None
        if player_id and player_id in self.players:
            return None
        self._player_seq += 1
        pid = player_id or f"p{self._player_seq}"
        if pid in self.players:
            return None
        player = Player(
            id=pid,
            name=clean,
            color=color_for(clean),
            token_hash=token_hash(token),
            joined_at=now,
            last_seen=now,
            spectator=bool(spectator),
        )
        self.players[pid] = player
        # El primero que entra es el anfitrión (también en la sala de la tele).
        # El público nunca es anfitrión ni bloquea la partida.
        if not spectator and not self.host_id:
            self.host_id = pid
        self.last_activity = now
        self.rev += 1
        return player

    def rejoin(self, token: str, now: float) -> Player | None:
        player = self.player_by_token(token)
        if player is None:
            return None
        if not player.connected:
            player.connected = True
            self.rev += 1
        player.last_seen = now
        self.last_activity = now
        return player

    def kick(self, player_id: str, now: float) -> bool:
        player = self.players.pop(player_id, None)
        if player is None:
            return False
        if self.host_id == player_id:
            self._failover_host(now)
        self.last_activity = now
        self.rev += 1
        return True

    def _failover_host(self, now: float) -> None:
        candidates = [p for p in self.players.values() if p.connected and not p.spectator]
        if not candidates:
            self.host_id = ""
            return
        # El más antiguo conectado: la sala sigue viva si el host se cae.
        self.host_id = min(candidates, key=lambda p: p.joined_at).id

    def disconnect_stale(self, now: float) -> None:
        changed = False
        for player in self.players.values():
            if player.connected and now - player.last_seen > DISCONNECT_SECONDS:
                player.connected = False
                changed = True
        if self.host_id and self.host_id in self.players:
            host = self.players[self.host_id]
            if not host.connected:
                self._failover_host(now)
                changed = True
        if changed:
            self.rev += 1

    # -- máquina de estados ---------------------------------------------------
    def start(self, now: float) -> bool:
        if self.fiesta is not None:
            return self.fiesta.start(now)
        if self.phase != "lobby" or not self.players:
            return False
        if self.game == "trivia" and not self.questions:
            return False
        if self.game in ("charadas", "wager", "impostor", "dibujo", "subasta", "fibbage") and not self.deck:
            return False
        if len(self._connected_players()) < GAME_MIN_PLAYERS.get(self.game, 1):
            return False
        self.phase = "countdown"
        self.phase_started = now
        self.started_at = now
        self.round_index = 0
        self.deltas = {}
        if self.game == "wager" and self.rules_version >= 2:
            for player in self._connected_players():
                player.score = max(player.score, WAGER_START_BANK)
        self.last_activity = now
        self.rev += 1
        return True

    def _actor_for_round(self) -> Player | None:
        connected = sorted(self._connected_players(), key=lambda p: p.joined_at)
        if not connected:
            return None
        return connected[(self.round_index) % len(connected)]

    def _begin_turn(self, now: float) -> None:
        actor = self._actor_for_round()
        self.actor_id = actor.id if actor else ""
        self.charada_index = 0
        self.charada_correct = 0
        self.charada_passes = 0
        self.charada_last = {}
        self.deltas = {}
        self.charada_restriction = ""
        self.charada_director_chosen = False
        self.charada_buzzer_id = ""
        self.charada_buzzer_started = 0.0
        self.charada_buzzer_remaining = 0.0
        self.charada_help_genre = False
        self.charada_help_year = False
        self.charada_help_penalty = 0
        if self.rules_version < 2:
            self.phase = "turn"
            self.phase_started = now
            self.last_activity = now
            self.rev += 1
            return
        connected = sorted(self._connected_players(), key=lambda p: p.joined_at)
        others = [p for p in connected if p.id != self.actor_id]
        rng = random.Random(f"charada-roles:{self.code}:{self.round_index}")
        self.charada_saboteur_id = rng.choice(others).id if others else ""
        director_pool = [p for p in connected if p.id != self.actor_id] or connected
        director = min(director_pool, key=lambda p: (p.score, p.joined_at)) if director_pool else None
        self.charada_director_id = director.id if director else ""
        self.charada_director_options = list(rng.sample(CHARADA_RESTRICTIONS, 3))
        self.phase = "director"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _begin_charada_acting(self, now: float) -> None:
        if not self.charada_restriction and self.charada_director_options:
            self.charada_restriction = self.charada_director_options[0]
        self.charada_director_chosen = True
        self.phase = "turn"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1
    def _finish_charada_buzz(self, correct: bool, now: float) -> None:
        buzzer = self.players.get(self.charada_buzzer_id)
        actor = self.players.get(self.actor_id)
        remaining = max(1.0, float(self.charada_buzzer_remaining or 1.0))
        if buzzer is not None:
            if correct:
                buzzer.score += CHARADA_BUZZ_POINTS
                self.deltas[buzzer.id] = self.deltas.get(buzzer.id, 0) + CHARADA_BUZZ_POINTS
                # El actor cobra una sola vez al cerrar el turno en `_begin_reveal`.
                # Pagar aquí también duplicaba su premio si el acierto venía por timbre.
                self.charada_last = dict(self.current_charada())
                self.charada_correct += 1
                self.charada_cursor += 1
                self.charada_index += 1
            else:
                buzzer.score = max(0, buzzer.score - CHARADA_BUZZ_PENALTY)
                self.deltas[buzzer.id] = self.deltas.get(buzzer.id, 0) - CHARADA_BUZZ_PENALTY
                buzzer.charada_buzz_cooldown = now + 12.0
        self.charada_buzzer_id = ""
        self.charada_buzzer_started = 0.0
        self.charada_buzzer_remaining = 0.0
        # El reloj se reanuda con el tiempo que quedaba cuando sonó el timbre.
        self.phase_started = now - max(0.0, self.charada_seconds - remaining)
        self.phase = "turn"
        self.last_activity = now
        self.rev += 1
        if self.charada_cursor >= len(self.deck):
            self._begin_reveal(now)


    def _begin_wager(self, now: float) -> None:
        round_no = self.round_index + 1
        for player in self.players.values():
            player.wager_round = round_no
            player.wager = 0
            player.wager_done_round = 0
            player.wager_delta = 0
            player.wager_option = -1
            player.wager_correct = False
            player.clue_answered = -1
        self.clue_index = 0
        self.phase = "wager"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    # -- Trivia 2.0 -----------------------------------------------------------
    def _trivia_round_kind(self) -> str:
        round_no = self.round_index + 1
        if round_no in (4, 5):
            return "sort"
        if round_no >= self.rounds:
            return "assault"
        return "multiple_choice"

    def _eligible_attackers(self) -> list[Player]:
        round_no = self.round_index + 1
        return [
            p for p in self._connected_players()
            if p.attack_ready_round == round_no and p.score >= TRIVIA_ATTACK_COST
        ]

    def _begin_trivia_round(self, now: float) -> None:
        self._reset_round_marks()
        self.trivia_round_kind = self._trivia_round_kind()
        self.trivia_attack_power = {}
        self.trivia_attack_target = {}
        self.trivia_attacks = []
        self.trivia_frozen = {}
        self.trivia_glitched = set()
        self.trivia_parasite = {}
        self.trivia_punishment_votes = {}
        self.trivia_punishment_wire = {}
        self.trivia_punishment_hot = ""
        self.trivia_punishment_penalties = {}
        self.trivia_sort_items = []
        self.trivia_sort_correct = []
        self.trivia_assault_items = []
        self.trivia_assault_correct = []
        for player in self.players.values():
            player.attack_power = ""
            player.attack_target = ""
            player.frozen_taps = 0
            player.glitched = False
            player.parasite_target = ""
            player.punishment_wire = ""
            player.sort_round = 0
            player.sort_choices = []
            player.assault_round = 0
            player.assault_selected = []
        if self.trivia_round_kind == "sort":
            self._build_trivia_sort(now)
            return
        if self.trivia_round_kind == "assault":
            self._build_trivia_assault(now)
            return
        if self._eligible_attackers():
            self.phase = "attack"
            self.phase_started = now
            self.last_activity = now
            self.rev += 1
            return
        self._begin_trivia_question(now, [])

    def _begin_trivia_question(self, now: float, attacks: list[dict]) -> None:
        self.trivia_attacks = attacks
        for attack in attacks:
            by = str(attack.get("by") or "")
            power = str(attack.get("power") or "")
            target = str(attack.get("target") or "")
            if by not in self.players or target not in self.players:
                continue
            self.trivia_attack_power[by] = power
            self.trivia_attack_target[by] = target
            if power == "freeze":
                self.trivia_frozen[target] = 0
            elif power == "glitch":
                self.trivia_glitched.add(target)
            elif power == "parasite":
                self.trivia_parasite[by] = target
                self.players[by].parasite_target = target
                # El parásito no responde: se marca como respondido para que
                # la sala no espere el timer completo.
                round_no = self.round_index + 1
                self.players[by].answers[round_no] = Answer(
                    option=-1, at=now, correct=False, points=0, streak_after=0,
                )
        self.phase = "question"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _build_trivia_sort(self, now: float) -> None:
        threshold = 2015 if self.round_index + 1 == 4 else 2000
        items = _year_sort_items(self.questions, threshold, 6, self.code, self.round_index)
        if not items:
            # Sin preguntas de año no se puede clasificar: se degrada a opción
            # múltiple en vez de romper la partida.
            self.trivia_round_kind = "multiple_choice"
            self._begin_trivia_question(now, [])
            return
        self.trivia_sort_items = items
        self.trivia_sort_correct = [int(item["answer"]) for item in items]
        self.phase = "sort"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _build_trivia_assault(self, now: float) -> None:
        threshold = 2015
        items = _year_sort_items(self.questions, threshold, 6, self.code, self.round_index)
        if not items:
            self.trivia_round_kind = "multiple_choice"
            self._begin_trivia_question(now, [])
            return
        self.trivia_assault_items = items
        self.trivia_assault_correct = [
            index for index, item in enumerate(items) if int(item["answer"]) == 0
        ]
        self.phase = "assault"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _all_attacks_done(self) -> bool:
        eligible = {p.id for p in self._eligible_attackers()}
        return bool(eligible) and eligible.issubset(set(self.trivia_attack_power))

    def trivia_attack(self, player_id: str, power: Any, target: str, now: float) -> tuple[bool, str]:
        if self.game != "trivia" or self.rules_version < 2 or self.phase != "attack":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player.attack_ready_round != self.round_index + 1:
            return False, "sin racha"
        if player.score < TRIVIA_ATTACK_COST:
            return False, "puntos"
        power = str(power or "").strip().lower()
        if power == "pass":
            if player_id in self.trivia_attack_power:
                return False, "duplicada"
            player.attack_power = "pass"
            self.trivia_attack_power[player_id] = "pass"
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if self._all_attacks_done():
                self._begin_trivia_question(now, list(self.trivia_attacks))
            return True, "ok"
        if power not in TRIVIA_ATTACK_POWERS:
            return False, "poder"
        if target == player_id or target not in self.players:
            return False, "objetivo"
        if self.players[target].spectator or not self.players[target].connected:
            return False, "objetivo"
        if player_id in self.trivia_attack_power:
            return False, "duplicada"
        # Un solo efecto negativo por víctima y por ronda: evita focus fire
        # (tres freeze al mismo líder) y cadenas A -> B -> A.
        if target in self.trivia_attack_target.values():
            return False, "ya atacado"
        if power == "parasite":
            if target in self.trivia_parasite or player_id in self.trivia_parasite.values():
                return False, "cadena"
            if self.players[target].attack_power == "parasite":
                return False, "cadena"
        cost = 0 if power == "parasite" else TRIVIA_ATTACK_COST
        if player.score < cost:
            return False, "puntos"
        player.score = max(0, player.score - cost)
        self.deltas[player_id] = self.deltas.get(player_id, 0) - cost
        player.attack_power = power
        player.attack_target = target
        self.trivia_attack_power[player_id] = power
        self.trivia_attack_target[player_id] = target
        self.trivia_attacks.append({
            "by": player_id, "byName": player.name, "power": power,
            "target": target, "targetName": self.players[target].name,
        })
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self._all_attacks_done():
            self._begin_trivia_question(now, list(self.trivia_attacks))
        return True, "ok"

    def trivia_tap(self, player_id: str, now: float) -> tuple[bool, str]:
        if self.game != "trivia" or self.rules_version < 2 or self.phase != "question":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player_id not in self.trivia_frozen:
            return False, "sin hielo"
        taps = min(TRIVIA_FREEZE_TAPS, int(self.trivia_frozen.get(player_id, 0)) + 1)
        self.trivia_frozen[player_id] = taps
        player.frozen_taps = taps
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        return True, "ok"

    def _begin_trivia_punishment(self, now: float) -> None:
        self.phase = "punishment"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _finish_trivia_punishment(self, now: float) -> None:
        votes = [wire for wire in self.trivia_punishment_votes.values()]
        if votes:
            counts = Counter(votes)
            top = max(counts.values())
            self.trivia_punishment_hot = next(
                wire for wire in ("red", "blue", "yellow", "green")
                if counts.get(wire, 0) == top
            )
        else:
            self.trivia_punishment_hot = ""
        self.trivia_punishment_penalties = {}
        if self.trivia_punishment_hot:
            for player_id, wire in self.trivia_punishment_wire.items():
                if wire == self.trivia_punishment_hot and player_id in self.players:
                    self.trivia_punishment_penalties[player_id] = TRIVIA_PUNISH_PENALTY
                    self.players[player_id].score = max(
                        0, self.players[player_id].score - TRIVIA_PUNISH_PENALTY,
                    )
                    self.deltas[player_id] = (
                        self.deltas.get(player_id, 0) - TRIVIA_PUNISH_PENALTY
                    )
        self.phase = "punishment_reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def trivia_punish(self, player_id: str, wire: str, now: float, *, vote: bool = False) -> tuple[bool, str]:
        if self.game != "trivia" or self.rules_version < 2 or self.phase != "punishment":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        wire = str(wire or "").strip().lower()
        if wire not in ("red", "blue", "yellow", "green"):
            return False, "cable"
        round_no = self.round_index + 1
        losers_ids = set(self._trivia_losers())
        if vote:
            if player.id in losers_ids:
                return False, "castigado"
            if player.id in self.trivia_punishment_votes:
                return False, "duplicada"
            self.trivia_punishment_votes[player.id] = wire
        else:
            if player.id not in losers_ids:
                return False, "a salvo"
            if player.id in self.trivia_punishment_wire:
                return False, "duplicada"
            self.trivia_punishment_wire[player.id] = wire
            player.punishment_wire = wire
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        losers = self._trivia_losers()
        safe = [p for p in self._connected_players() if p.id not in losers]
        if all(pid in self.trivia_punishment_wire for pid in losers) and (
            not safe or all(p.id in self.trivia_punishment_votes for p in safe)
        ):
            self._finish_trivia_punishment(now)
        return True, "ok"

    def trivia_sort(self, player_id: str, choices: Any, now: float) -> tuple[bool, str]:
        if self.game != "trivia" or self.rules_version < 2 or self.phase != "sort":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player.sort_round == self.round_index + 1:
            return False, "duplicada"
        values = _clean_choice_list(choices, 2)
        if len(values) != len(self.trivia_sort_items):
            return False, "faltan"
        player.sort_round = self.round_index + 1
        player.sort_choices = values
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self._all_trivia_sorted():
            self._finish_trivia_sort(now)
        return True, "ok"

    def _all_trivia_sorted(self) -> bool:
        connected = self._connected_players()
        return bool(connected) and all(
            p.sort_round == self.round_index + 1 for p in connected
        )

    def _finish_trivia_sort(self, now: float) -> None:
        round_no = self.round_index + 1
        for player in self._connected_players():
            if player.sort_round != round_no:
                continue
            hits = sum(
                1 for choice, correct in zip(player.sort_choices, self.trivia_sort_correct)
                if int(choice) == int(correct)
            )
            points = hits * TRIVIA_SORT_POINTS
            player.score += points
            self.deltas[player.id] = points
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def trivia_assault(self, player_id: str, selected: Any, now: float) -> tuple[bool, str]:
        if self.game != "trivia" or self.rules_version < 2 or self.phase != "assault":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player.assault_round == self.round_index + 1:
            return False, "duplicada"
        values = _clean_int_list(selected, len(self.trivia_assault_items), allow_empty=True)
        player.assault_round = self.round_index + 1
        player.assault_selected = values
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self._all_trivia_assaulted():
            self._finish_trivia_assault(now)
        return True, "ok"

    def _all_trivia_assaulted(self) -> bool:
        connected = self._connected_players()
        return bool(connected) and all(
            p.assault_round == self.round_index + 1 for p in connected
        )

    def _finish_trivia_assault(self, now: float) -> None:
        round_no = self.round_index + 1
        correct_set = set(self.trivia_assault_correct)
        for player in self._connected_players():
            if player.assault_round != round_no:
                continue
            selected = set(int(i) for i in player.assault_selected)
            good = len(selected & correct_set)
            bad = len(selected - correct_set)
            delta = good * TRIVIA_ASSAULT_CORRECT - bad * TRIVIA_ASSAULT_WRONG
            player.score = max(0, player.score + delta)
            self.deltas[player.id] = delta
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _trivia_losers(self) -> list[str]:
        round_no = self.round_index + 1
        connected = self._connected_players()
        return [
            p.id for p in connected
            if p.answers.get(round_no) is None
            or not p.answers[round_no].correct
        ]

    def _begin_trivia_resolution(self, now: float) -> None:
        round_no = self.round_index + 1
        for player in self.players.values():
            answer = player.answers.get(round_no)
            if answer is not None:
                player.score += answer.points
                player.streak = answer.streak_after if answer.correct else 0
                self.deltas[player.id] = self.deltas.get(player.id, 0) + answer.points
            else:
                player.streak = 0
        # Parásito: el atacante cobra del objetivo o ambos pierden.
        for attacker_id, target_id in self.trivia_parasite.items():
            attacker = self.players.get(attacker_id)
            target = self.players.get(target_id)
            if attacker is None or target is None:
                continue
            target_answer = target.answers.get(round_no)
            if target_answer is not None and target_answer.correct:
                extra = int(round(target_answer.points * TRIVIA_PARASITE_SHARE))
                attacker.score += extra
                self.deltas[attacker.id] = self.deltas.get(attacker.id, 0) + extra
            else:
                attacker.score = max(0, attacker.score - TRIVIA_PARASITE_FAIL)
                target.score = max(0, target.score - TRIVIA_PARASITE_FAIL)
                self.deltas[attacker.id] = self.deltas.get(attacker.id, 0) - TRIVIA_PARASITE_FAIL
                self.deltas[target.id] = self.deltas.get(target.id, 0) - TRIVIA_PARASITE_FAIL
        if round_no in TRIVIA_PUNISH_ROUNDS and self._trivia_losers():
            self._begin_trivia_punishment(now)
        else:
            self.phase = "reveal"
            self.phase_started = now
            self.last_activity = now
            self.rev += 1

    # -- Apuesta 2.0 ----------------------------------------------------------
    def _begin_wager_v2(self, now: float) -> None:
        self._reset_round_marks()
        self.wager_stage = 1
        self.wager_pot = int(self.wager_rollover) + WAGER_ANTE * len(self._connected_players())
        self.wager_rollover = 0
        self.wager_locked = {}
        self.wager_locked_option = {}
        self.wager_side_bets = {}
        self.wager_side_results = {}
        for player in self.players.values():
            player.wager = 0
            player.wager_done_round = 0
            player.wager_delta = 0
            player.wager_option = -1
            player.wager_correct = False
            player.wager_stage_locked = 0
            player.wager_locked_option = -1
            player.wager_bought_stage = 0
            player.wager_private_stage = 1
            player.wager_side_target = ""
            player.wager_side_amount = 0
            if player.connected and not player.spectator:
                player.score = max(0, player.score - WAGER_ANTE)
                self.deltas[player.id] = self.deltas.get(player.id, 0) - WAGER_ANTE
        self.clue_index = 0
        self.phase = "clue"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def wager_buy(self, player_id: str, now: float) -> tuple[bool, str]:
        if self.game != "wager" or self.rules_version < 2 or self.phase != "clue":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player.wager_stage_locked:
            return False, "bloqueado"
        if player.wager_private_stage >= 3:
            return False, "última pista"
        if player.score < WAGER_BUY_COST:
            return False, "puntos"
        player.score -= WAGER_BUY_COST
        player.wager_private_stage = min(3, player.wager_private_stage + 1)
        player.wager_bought_stage = player.wager_private_stage
        self.deltas[player.id] = self.deltas.get(player.id, 0) - WAGER_BUY_COST
        self.wager_pot += WAGER_BUY_COST
        self.last_activity = now
        self.rev += 1
        return True, "ok"

    def wager_lock(self, player_id: str, option: Any, now: float) -> tuple[bool, str]:
        if self.game != "wager" or self.rules_version < 2 or self.phase != "clue":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player.wager_stage_locked:
            return False, "duplicada"
        try:
            choice = int(option)
        except (TypeError, ValueError):
            return False, "opción"
        data = self.current_wager_round()
        if choice < 0 or choice >= len(data.get("options") or []):
            return False, "opción"
        stage = max(1, min(3, int(player.wager_private_stage)))
        player.wager_stage_locked = stage
        player.wager_locked_option = choice
        player.wager_option = choice
        player.wager_round = self.round_index + 1
        player.wager = WAGER_V2_FACTORS[stage - 1]
        player.wager_done_round = self.round_index + 1
        self.wager_locked[player.id] = stage
        self.wager_locked_option[player.id] = choice
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self._all_wager_locked():
            self._finish_wager_v2(now)
        return True, "ok"

    def wager_side_bet(self, player_id: str, target: str, amount: Any, now: float) -> tuple[bool, str]:
        if self.game != "wager" or self.rules_version < 2 or self.phase != "clue":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        target = str(target or "")
        if target == player_id or target not in self.players:
            return False, "objetivo"
        other = self.players[target]
        if other.spectator:
            return False, "objetivo"
        if self.wager_locked.get(target, 0) != 1:
            return False, "sin bloqueo"
        if player.wager_stage_locked:
            return False, "bloqueado"
        if player.wager_side_target:
            return False, "duplicada"
        try:
            value = max(1, min(int(amount or WAGER_SIDE_BET), WAGER_SIDE_BET))
        except (TypeError, ValueError):
            value = WAGER_SIDE_BET
        if player.score < value:
            return False, "puntos"
        player.wager_side_target = target
        player.wager_side_amount = value
        self.wager_side_bets[player.id] = {"target": target, "amount": value}
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        return True, "ok"

    def _all_wager_locked(self) -> bool:
        connected = self._connected_players()
        return bool(connected) and all(
            p.wager_stage_locked > 0 for p in connected
        )

    def _wager_clue_for_stage(self, stage: int) -> str:
        data = self.current_wager_round()
        clues = data.get("clues") or []
        stage = max(1, min(3, int(stage)))
        if stage == 1:
            bits = []
            if data.get("year"):
                bits.append(f"Año: {data['year']}")
            genres = [str(g) for g in (data.get("genres") or []) if str(g).strip()]
            if genres:
                bits.append("Géneros: " + ", ".join(genres[:3]))
            return " · ".join(bits) or "Póster pixelado"
        if stage == 2 and clues:
            return str(clues[0].get("text") or "")
        if stage >= 3 and len(clues) > 1:
            return str(clues[1].get("text") or "")
        return ""

    def _finish_wager_v2(self, now: float) -> None:
        data = self.current_wager_round()
        answer_index = int(data.get("answer_index", -1))
        weights: dict[str, int] = {}
        for player in self._connected_players():
            if player.wager_stage_locked and player.wager_locked_option == answer_index:
                weights[player.id] = WAGER_V2_FACTORS[min(player.wager_stage_locked - 1, 2)]
        total_weight = sum(weights.values())
        if total_weight <= 0:
            self.wager_rollover = self.wager_pot
            self.wager_side_results = {}
            for player_id, bet in self.wager_side_bets.items():
                target = self.players.get(str(bet.get("target") or ""))
                bettor = self.players.get(player_id)
                if bettor is not None and target is not None:
                    gain = int(bet.get("amount") or 0)
                    bettor.score += gain
                    target.score = max(0, target.score - gain)
                    self.deltas[bettor.id] = self.deltas.get(bettor.id, 0) + gain
                    self.deltas[target.id] = self.deltas.get(target.id, 0) - gain
                    self.wager_side_results[player_id] = gain
            self.phase = "reveal"
            self.phase_started = now
            self.last_activity = now
            self.rev += 1
            return
        for player_id, weight in weights.items():
            share = int(self.wager_pot * weight / total_weight)
            share = (share // 50) * 50
            self.players[player_id].score += share
            self.deltas[player_id] = self.deltas.get(player_id, 0) + share
        for player_id, bet in self.wager_side_bets.items():
            bettor = self.players.get(player_id)
            target = self.players.get(str(bet.get("target") or ""))
            if bettor is None or target is None:
                continue
            amount = int(bet.get("amount") or 0)
            target_correct = target.wager_stage_locked and target.wager_locked_option == answer_index
            if target_correct:
                bettor.score = max(0, bettor.score - amount)
                target.score += amount
                self.deltas[bettor.id] = self.deltas.get(bettor.id, 0) - amount
                self.deltas[target.id] = self.deltas.get(target.id, 0) + amount
                self.wager_side_results[player_id] = -amount
            else:
                # El que leyó la cara del rival cobra +400; el rival no paga
                # porque ya falló por su cuenta.
                gain = 400
                bettor.score += gain
                self.deltas[bettor.id] = self.deltas.get(bettor.id, 0) + gain
                self.wager_side_results[player_id] = gain
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _begin_clue(self, index: int, now: float) -> None:
        round_no = self.round_index + 1
        for player in self._connected_players():
            if player.wager_round != round_no or player.wager <= 0:
                player.wager_round = round_no
                player.wager = WAGER_MIN
        self.clue_index = max(0, min(int(index), CLUE_COUNT - 1))
        self.phase = "clue"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _reset_round_marks(self) -> None:
        round_no = self.round_index + 1
        self.deltas = {}
        for player in self.players.values():
            if player.submitted_round != round_no:
                player.submitted_round = 0
            if player.vote_round != round_no:
                player.vote_round = 0
            if player.bid_round != round_no:
                player.bid_round = 0
                player.bid = 0

    def _impostor_turn_seconds(self) -> int:
        count = len(self.impostor_speaker_ids) or len(self._connected_players())
        if count <= 4:
            return 25
        if count <= 6:
            return 20
        return 15

    def current_impostor_pair(self) -> tuple[Player | None, Player | None]:
        ids = self.impostor_speaker_ids
        if not ids:
            return None, None
        asker_id = ids[self.impostor_speaker_index % len(ids)]
        target_id = ids[(self.impostor_speaker_index + 1) % len(ids)]
        return self.players.get(asker_id), self.players.get(target_id)

    def advance_impostor_turn(self, now: float) -> None:
        if self.phase != "questions":
            return
        if self.impostor_speaker_index + 1 < len(self.impostor_speaker_ids):
            self.impostor_speaker_index += 1
            self.phase_started = now
            self.last_activity = now
            self.rev += 1
            return
        self.phase = "coartada" if self.rules_version >= 2 else "vote"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _begin_impostor(self, now: float) -> None:
        self._reset_round_marks()
        card = self.deck[min(self.round_index, len(self.deck) - 1)] if self.deck else {}
        connected = sorted(self._connected_players(), key=lambda p: p.joined_at)
        rng = random.Random(f"impostor-role:{self.code}:{self.round_index + 1}")
        impostor = rng.choice(connected) if connected else None
        self.impostor_id = impostor.id if impostor else ""
        others = [p for p in connected if p.id != self.impostor_id]
        self.impostor_accomplice_id = (
            rng.choice(others).id
            if self.rules_version >= 2 and len(connected) >= 6 and others
            else ""
        )
        detective_pool = [
            p for p in connected
            if p.id not in (self.impostor_id, self.impostor_accomplice_id)
        ]
        self.impostor_detective_id = (
            rng.choice(detective_pool).id
            if self.rules_version >= 2 and len(connected) >= 5 and detective_pool
            else ""
        )
        self.impostor_word = str(card.get("word") or "")
        self.impostor_category = str(card.get("category") or "")
        self.impostor_hint = str(card.get("hint") or "")
        decoy_words = [
            str(item.get("word") or "")
            for item in self.deck
            if str(item.get("word") or "")
            and fold(str(item.get("word"))) != fold(self.impostor_word)
            and (
                not self.impostor_category
                or str(item.get("category") or "") == self.impostor_category
            )
        ]
        if not decoy_words:
            decoy_words = [
                str(item.get("word") or "")
                for item in self.deck
                if str(item.get("word") or "")
                and fold(str(item.get("word"))) != fold(self.impostor_word)
            ]
        self.impostor_detective_options = (
            [self.impostor_word, rng.choice(decoy_words)]
            if self.impostor_word and decoy_words else []
        )
        rng.shuffle(self.impostor_detective_options)
        self.impostor_coartada = {}
        self.impostor_questions = list(card.get("questions") or [])
        self.impostor_guess_options = list(card.get("guessOptions") or [])
        self.impostor_speaker_index = 0
        self.impostor_speaker_ids = [
            connected[(self.round_index + index) % len(connected)].id
            for index in range(len(connected))
        ] if connected else []
        self.impostor_votes = {}
        self.impostor_guess = ""
        self.impostor_caught = False
        self.impostor_guess_correct = False
        self.phase = "secret"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _begin_drawing(self, now: float) -> None:
        self._reset_round_marks()
        card = self.deck[min(self.round_index, len(self.deck) - 1)] if self.deck else {}
        alt = self.deck[(self.round_index + 1) % len(self.deck)] if len(self.deck) > 1 else card
        connected = sorted(self._connected_players(), key=lambda p: p.joined_at)
        artist = connected[self.round_index % len(connected)] if connected else None
        self.drawing_artist_id = artist.id if artist else ""
        self.drawing_prompt_options = []
        candidates = (card, alt) if self.rules_version >= 2 else (card,)
        for candidate in candidates:
            prompt = str(candidate.get("prompt") or candidate.get("title") or "")
            if not prompt or any(item["prompt"] == prompt for item in self.drawing_prompt_options):
                continue
            self.drawing_prompt_options.append({
                "prompt": prompt,
                "decoys": [str(item) for item in (candidate.get("decoys") or []) if str(item).strip()],
            })
        first = self.drawing_prompt_options[0] if self.drawing_prompt_options else {"prompt": "", "decoys": []}
        self.drawing_prompt = first["prompt"]
        self.drawing_decoys = list(first["decoys"])
        if self.rules_version < 2:
            # La elección de consigna es una mecánica v2; el prompt sigue siendo
            # el de la carta, pero no se ofrece como opción al artista.
            self.drawing_prompt_options = []
        self.drawing_strokes = []
        self.drawing_partial_at = 0.0
        self.drawing_titles = {}
        self.drawing_options = []
        self.drawing_votes = {}
        self.drawing_audience_votes = {}
        self.drawing_audience_choice = ""
        self.drawing_audience_bonus_id = ""
        self.drawing_critic_bombs = {}
        self.drawing_critic_crowns = {}
        self.drawing_early_guesses = {}
        self.drawing_early_winners = {}
        self.drawing_early_attempts = {}
        round_no = self.round_index + 1
        self.drawing_ink_limit = (
            DRAW_INK_LIMIT if self.rules_version >= 2 and round_no == 2 else 0.0
        )
        self.drawing_ink_used = 0.0
        self.drawing_flashlight = self.rules_version >= 2 and round_no >= 3
        self.phase = "draw"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _begin_auction(self, now: float) -> None:
        self._reset_round_marks()
        card = self.deck[min(self.round_index, len(self.deck) - 1)] if self.deck else {}
        self.auction_item = dict(card)
        self.auction_bids = {}
        self.auction_winner = ""
        self.auction_price = 0
        self.auction_cancelled = []
        self.auction_tie = False
        self.auction_effect = ""
        rng = random.Random(f"auction-v2:{self.code}:{self.round_index}")
        connected = sorted(self._connected_players(), key=lambda p: (p.score, p.joined_at))
        informant_count = 1 if len(connected) <= 4 else 2
        self.auction_informants = (
            [p.id for p in connected[:informant_count]] if self.rules_version >= 2 else []
        )
        self.auction_secret_effect = ""
        self.auction_secret_value = 0
        self.auction_ghost_bidder = ""
        self.auction_second_pays = bool(self.rules_version >= 2 and self.round_index + 1 == 4)
        secret_effects = [
            ("points", 400), ("coins", 500), ("steal", 350),
            ("double_next", 0), ("shield", 0), ("bomb", 400), ("donate", 250),
        ]
        effect, value = rng.choice(secret_effects)
        self.auction_secret_effect = effect if self.rules_version >= 2 else ""
        self.auction_secret_value = int(value)
        self.phase = "auction"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _begin_fibbage(self, now: float) -> None:
        self._reset_round_marks()
        card = self.deck[min(self.round_index, len(self.deck) - 1)] if self.deck else {}
        self.fibbage_prompt = str(card.get("prompt") or "")
        self.fibbage_answer = str(card.get("answer") or "")
        self.fibbage_hint = str(card.get("hint") or "")
        self.fibbage_category = str(card.get("category") or "Cine y series")
        self.fibbage_decoys = [str(item) for item in (card.get("decoys") or []) if str(item).strip()]
        self.fibbage_lies = {}
        self.fibbage_options = []
        self.fibbage_votes = {}
        self.fibbage_audience_votes = {}
        self.fibbage_audience_choice = ""
        self.fibbage_audience_bonus_id = ""
        self.fibbage_scans = {}
        self.fibbage_poisoned = set()
        self.fibbage_certainty = set()
        self.fibbage_hunter = {}
        self.fibbage_final_shared = bool(
            self.rules_version >= 2 and self.round_index + 1 >= self.rounds
        )
        for player in self.players.values():
            player.fibbage_scan_used = False
            player.fibbage_certainty = False
            player.fibbage_hunter_guess = ""
        self.phase = "write"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _enter_round_phase(self, now: float) -> None:
        if self.game == "trivia" and self.rules_version >= 2:
            self._begin_trivia_round(now)
        elif self.game == "wager" and self.rules_version >= 2:
            self._begin_wager_v2(now)
        elif self.game == "charadas":
            self._begin_turn(now)
        elif self.game == "wager":
            self._begin_wager(now)
        elif self.game == "impostor":
            self._begin_impostor(now)
        elif self.game == "dibujo":
            self._begin_drawing(now)
        elif self.game == "subasta":
            self._begin_auction(now)
        elif self.game == "fibbage":
            self._begin_fibbage(now)
        else:
            self.phase = "question"
            self.phase_started = now
            self.rev += 1

    def current_wager_round(self) -> dict:
        if not self.deck:
            return {}
        return self.deck[min(self.round_index, len(self.deck) - 1)]

    def current_clue(self) -> dict:
        round_data = self.current_wager_round()
        clues = round_data.get("clues") or []
        if not clues:
            return {}
        return clues[min(self.clue_index, len(clues) - 1)]

    def current_charada(self) -> dict:
        if not self.deck:
            return {}
        return self.deck[min(self.charada_cursor, len(self.deck) - 1)]

    def _all_wagered(self) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        round_no = self.round_index + 1
        return all(p.wager_round == round_no and p.wager > 0 for p in connected)

    def _all_wager_done(self) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        round_no = self.round_index + 1
        return all(p.wager_done_round == round_no for p in connected)

    def tick(self, now: float) -> None:
        """El servidor avanza solo; nadie tiene que pulsar nada."""
        if self.phase == "closed":
            return
        self.disconnect_stale(now)
        if self.created_at and now - self.created_at >= ROOM_TTL_SECONDS:
            self.close(now, "expired")
            return
        if self.last_activity and now - self.last_activity >= IDLE_SECONDS:
            self.close(now, "idle")
            return
        if self.fiesta is not None:
            self.fiesta.tick(now)
            return
        # Bucle por si el proceso estuvo ocupado y hay que alcanzar el presente.
        for _ in range(self.rounds * 3 + 8):
            if self.phase == "countdown" and now - self.phase_started >= self.countdown_seconds:
                self._enter_round_phase(now)
                continue
            if self.game == "trivia" and self.rules_version >= 2:
                if self.phase == "attack":
                    if self._all_attacks_done() or now - self.phase_started >= TRIVIA_ATTACK_SECONDS:
                        self._begin_trivia_question(now, list(self.trivia_attacks))
                        continue
                elif self.phase == "question":
                    if self._all_answered() or now - self.phase_started >= self.question_seconds:
                        self._begin_trivia_resolution(now)
                        continue
                elif self.phase == "punishment":
                    losers = self._trivia_losers()
                    safe = [p for p in self._connected_players() if p.id not in losers]
                    done = all(pid in self.trivia_punishment_wire for pid in losers) and (
                        not safe or all(p.id in self.trivia_punishment_votes for p in safe)
                    )
                    if done or now - self.phase_started >= TRIVIA_PUNISH_SECONDS:
                        self._finish_trivia_punishment(now)
                        continue
                elif self.phase == "punishment_reveal":
                    if now - self.phase_started >= TRIVIA_PUNISH_REVEAL_SECONDS:
                        self._advance(now)
                        continue
                elif self.phase == "sort":
                    if self._all_trivia_sorted() or now - self.phase_started >= TRIVIA_SORT_SECONDS:
                        self._finish_trivia_sort(now)
                        continue
                elif self.phase == "assault":
                    if self._all_trivia_assaulted() or now - self.phase_started >= TRIVIA_ASSAULT_SECONDS:
                        self._finish_trivia_assault(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= TRIVIA_REVEAL_SECONDS:
                    self._advance(now)
                    continue
            elif self.game == "wager" and self.rules_version >= 2:
                if self.phase == "clue":
                    if self._all_wager_locked() or now - self.phase_started >= self.clue_seconds:
                        self._finish_wager_v2(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= TRIVIA_REVEAL_SECONDS:
                    self._advance(now)
                    continue
            elif self.game == "trivia":
                if self.phase == "question":
                    if self.players_pace and self._all_answered():
                        self._begin_reveal(now)
                        continue
                    if not self.players_pace and now - self.phase_started >= self.question_seconds:
                        self._begin_reveal(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= self.reveal_seconds:
                    self._advance(now)
                    continue
            elif self.game == "charadas":
                if self.phase == "director":
                    if self.charada_director_chosen or now - self.phase_started >= CHARADA_DIRECTOR_SECONDS:
                        self._begin_charada_acting(now)
                        continue
                elif self.phase == "turn":
                    actor = self.players.get(self.actor_id)
                    if actor is None or not actor.connected:
                        self._begin_reveal(now)
                        continue
                    if self.charada_buzzer_id:
                        if now - self.charada_buzzer_started >= CHARADA_BUZZ_SECONDS:
                            self._finish_charada_buzz(False, now)
                            continue
                    elif now - self.phase_started >= self.charada_seconds:
                        self._begin_reveal(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= self.reveal_seconds:
                    self._advance(now)
                    continue
            elif self.game == "wager":
                if self.phase == "wager":
                    if self._all_wagered():
                        self._begin_clue(0, now)
                        continue
                    if now - self.phase_started >= self.wager_seconds:
                        self._begin_clue(0, now)
                        continue
                elif self.phase == "clue":
                    if self._all_wager_done():
                        self._begin_reveal(now)
                        continue
                    if now - self.phase_started >= self.clue_seconds:
                        self._next_clue_or_reveal(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= self.reveal_seconds:
                    self._advance(now)
                    continue
            elif self.game == "impostor":
                if self.phase == "secret":
                    if now - self.phase_started >= SECRET_SECONDS:
                        self.phase = "questions"
                        self.phase_started = now
                        self.impostor_speaker_index = 0
                        self.rev += 1
                        continue
                elif self.phase == "questions":
                    if now - self.phase_started >= self._impostor_turn_seconds():
                        self.advance_impostor_turn(now)
                        continue
                elif self.phase == "coartada":
                    if self._all_impostor_coartada() or now - self.phase_started >= COARTADA_SECONDS:
                        self.phase = "vote"
                        self.phase_started = now
                        self.rev += 1
                        continue
                elif self.phase == "vote":
                    if self._all_players_voted(now) or now - self.phase_started >= VOTE_SECONDS:
                        self._finish_impostor_vote(now)
                        continue
                elif self.phase == "guess":
                    if self._impostor_has_guessed() or now - self.phase_started >= GUESS_SECONDS:
                        self._finish_impostor_reveal(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= self.reveal_seconds:
                    self._advance(now)
                    continue
            elif self.game == "dibujo":
                if self.phase == "draw":
                    artist = self.players.get(self.drawing_artist_id)
                    if artist is None or not artist.connected:
                        self._begin_draw_title(now)
                        continue
                    if artist.submitted_round == self.round_index + 1 or now - self.phase_started >= DRAW_SECONDS:
                        self._begin_draw_title(now)
                        continue
                elif self.phase == "title":
                    if self._all_draw_titles() or now - self.phase_started >= DRAW_TITLE_SECONDS:
                        self._build_draw_options()
                        self.phase = "vote"
                        self.phase_started = now
                        self.rev += 1
                        continue
                elif self.phase == "vote":
                    if self._all_draw_votes() or now - self.phase_started >= DRAW_VOTE_SECONDS:
                        self._finish_draw_vote(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= self.reveal_seconds:
                    self._advance(now)
                    continue
            elif self.game == "subasta":
                if self.phase == "auction":
                    if self._all_bids() or now - self.phase_started >= AUCTION_SECONDS:
                        self._resolve_auction(now)
                        self.phase = "auction_reveal"
                        self.phase_started = now
                        self.rev += 1
                        continue
                elif self.phase == "auction_reveal":
                    if (self.players_pace and self._all_ready()) or (
                        now - self.phase_started >= AUCTION_REVEAL_SECONDS
                    ):
                        self._advance(now)
                        continue
            elif self.game == "fibbage":
                if self.phase == "write":
                    if self._all_fibbage_lies() or now - self.phase_started >= FIBBAGE_WRITE_SECONDS:
                        self._build_fibbage_options()
                        self.phase = "vote"
                        self.phase_started = now
                        self.rev += 1
                        continue
                elif self.phase == "vote":
                    if self._all_fibbage_votes() or now - self.phase_started >= FIBBAGE_VOTE_SECONDS:
                        self._finish_fibbage_vote(now)
                        continue
                elif self.phase == "reveal" and not self.players_pace and now - self.phase_started >= self.reveal_seconds:
                    self._advance(now)
                    continue
            break

    def _begin_reveal(self, now: float) -> None:
        if self.phase == "reveal":
            return
        round_no = self.round_index + 1
        if self.game == "charadas":
            actor = self.players.get(self.actor_id)
            if actor is not None:
                earned = max(0, self.charada_correct * CHARADA_POINTS - self.charada_help_penalty)
                if self.rules_version >= 2 and self.charada_director_chosen and self.charada_correct > 0:
                    earned += CHARADA_DIRECTOR_BONUS
                    director = self.players.get(self.charada_director_id)
                    if director is not None:
                        director.score += CHARADA_DIRECTOR_BONUS
                        self.deltas[director.id] = self.deltas.get(director.id, 0) + CHARADA_DIRECTOR_BONUS
                actor.score += earned
                if earned:
                    self.deltas[actor.id] = self.deltas.get(actor.id, 0) + earned
                actor.streak = self.charada_correct
            if self.rules_version >= 2 and self.charada_correct == 0:
                saboteur = self.players.get(self.charada_saboteur_id)
                if saboteur is not None:
                    saboteur.score += CHARADA_SABOTEUR_POINTS
                    self.deltas[saboteur.id] = self.deltas.get(saboteur.id, 0) + CHARADA_SABOTEUR_POINTS
        elif self.game == "wager":
            for player in self.players.values():
                player.score = max(0, player.score + player.wager_delta)
                player.streak = 0
        else:
            for player in self.players.values():
                answer = player.answers.get(round_no)
                if answer is not None:
                    player.score += answer.points
                    player.streak = answer.streak_after if answer.correct else 0
                else:
                    player.streak = 0
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _next_clue_or_reveal(self, now: float) -> None:
        if self.clue_index + 1 < CLUE_COUNT:
            self._begin_clue(self.clue_index + 1, now)
        else:
            self._begin_reveal(now)

    def _connected_players(self) -> list[Player]:
        return [player for player in self.players.values() if player.connected and not player.spectator]

    def _all_answered(self) -> bool:
        connected = [
            p for p in self._connected_players()
            if p.id not in self.trivia_parasite
        ]
        if not connected:
            return False
        round_no = self.round_index + 1
        return all(round_no in player.answers for player in connected)

    def _all_ready(self) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        round_no = self.round_index + 1
        return all(player.ready_round >= round_no for player in connected)

    def all_ready(self) -> bool:
        return self._all_ready() if self.players_pace else True

    def _advance(self, now: float) -> None:
        if self.round_index + 1 >= self.rounds:
            if self.game == "subasta":
                self._liquidate_auction_vault()
            self.phase = "podium"
            self.phase_started = now
            self.ended_at = now
            self.last_activity = now
            self.rev += 1
            return
        self.round_index += 1
        self._enter_round_phase(now)

    def _liquidate_auction_vault(self) -> None:
        """Convierte las monedas sobrantes en puntos al cerrar la subasta."""
        self.auction_cashout = {}
        for player in self._connected_players():
            bonus = int(max(0, player.coins) * AUCTION_VAULT_RATE)
            if bonus:
                player.score += bonus
                self.auction_cashout[player.id] = bonus

    # -- juegos de fiesta: helpers -------------------------------------------
    def _round_no(self) -> int:
        return self.round_index + 1

    def _all_players_voted(self, now: float) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        return all(p.vote_round == self._round_no() for p in connected)

    def _all_impostor_coartada(self) -> bool:
        connected = self._connected_players()
        return bool(connected) and all(p.id in self.impostor_coartada for p in connected)

    def _all_draw_titles(self) -> bool:
        connected = [p for p in self._connected_players() if p.id != self.drawing_artist_id]
        if not connected:
            return True
        return all(p.submitted_round == self._round_no() for p in connected)

    def _all_draw_votes(self) -> bool:
        connected = [
            p for p in self._connected_players()
            if p.id != self.drawing_artist_id
        ]
        if not connected:
            return True
        return all(p.vote_round == self._round_no() for p in connected)

    def _all_bids(self) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        return all(p.bid_round == self._round_no() for p in connected)

    def _all_fibbage_lies(self) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        return all(p.submitted_round == self._round_no() for p in connected)

    def _all_fibbage_votes(self) -> bool:
        connected = self._connected_players()
        if not connected:
            return False
        return all(p.vote_round == self._round_no() for p in connected)

    def _impostor_has_guessed(self) -> bool:
        return bool(self.impostor_guess.strip())

    def draw_early_guess(self, player_id: str, text: str, now: float) -> tuple[bool, str]:
        if self.game != "dibujo" or self.rules_version < 2 or self.phase != "draw":
            return False, "fase"
        if player_id == self.drawing_artist_id:
            return False, "artista"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        attempts = int(self.drawing_early_attempts.get(player_id, 0))
        if attempts >= 2:
            return False, "intentos"
        clean = _clean_game_text(text, 80)
        if len(clean) < 3:
            return False, "corto"
        if not self.drawing_strokes:
            return False, "sin dibujo"
        self.drawing_early_attempts[player_id] = attempts + 1
        self.drawing_early_guesses[player_id] = clean
        prompt_words = set(re.findall(r"[a-z0-9]+", fold(self.drawing_prompt)))
        guess_words = set(re.findall(r"[a-z0-9]+", fold(clean)))
        content_words = [
            word for word in guess_words
            if len(word) >= 4 and word not in EARLY_GUESS_STOPWORDS
        ]
        if content_words and guess_words.issubset(prompt_words):
            self.drawing_early_winners[player_id] = 400
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        return True, "ok"

    def draw_critic(self, player_id: str, target: str, kind: str, now: float) -> tuple[bool, str]:
        if self.game != "dibujo" or self.rules_version < 2 or self.phase != "vote":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        target = str(target or "")
        if not any(option["id"] == target for option in self.drawing_options):
            return False, "opción"
        kind = str(kind or "").strip().lower()
        if kind == "bomb":
            if player_id in self.drawing_critic_bombs:
                return False, "duplicada"
            self.drawing_critic_bombs[player_id] = target
        elif kind == "crown":
            if player_id in self.drawing_critic_crowns:
                return False, "duplicada"
            self.drawing_critic_crowns[player_id] = target
        else:
            return False, "ficha"
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        return True, "ok"

    def _begin_draw_title(self, now: float) -> None:
        self.phase = "title"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _build_draw_options(self) -> None:
        options: list[dict] = [{"id": "real", "text": self.drawing_prompt, "authorId": ""}]
        seen = {fold(self.drawing_prompt)}
        for player_id, text in self.drawing_titles.items():
            clean = _clean_game_text(text, 120)
            key = fold(clean)
            if not clean or key in seen:
                continue
            seen.add(key)
            options.append({"id": f"lie:{player_id}", "text": clean, "authorId": player_id})
        for index, decoy in enumerate(self.drawing_decoys):
            if len(options) >= MIN_PARTY_OPTIONS:
                break
            clean = _clean_game_text(decoy, 120)
            key = fold(clean)
            if not clean or key in seen:
                continue
            seen.add(key)
            options.append({"id": f"house:{index}", "text": clean, "authorId": "", "house": True})
        rng = random.Random(f"draw-options:{self.code}:{self._round_no()}")
        rng.shuffle(options)
        self.drawing_options = options
        self.drawing_votes = {}

    def _finish_draw_vote(self, now: float) -> None:
        if self.phase != "vote":
            return
        round_no = self._round_no()
        counts: dict[str, int] = {}
        for option_id in self.drawing_votes.values():
            counts[option_id] = counts.get(option_id, 0) + 1
        self.deltas = {p.id: 0 for p in self._connected_players()}
        for player_id, bonus in self.drawing_early_winners.items():
            if player_id in self.deltas:
                self.deltas[player_id] = self.deltas.get(player_id, 0) + bonus
        for voter in self._connected_players():
            if voter.id == self.drawing_artist_id:
                continue
            choice = self.drawing_votes.get(voter.id, "")
            if choice == "real":
                self.deltas[voter.id] = self.deltas.get(voter.id, 0) + DRAW_REAL_POINTS
                voter.detective += 1
            elif choice.startswith("lie:"):
                author_id = choice.split(":", 1)[1]
                if author_id in self.deltas:
                    self.deltas[author_id] = self.deltas.get(author_id, 0) + DRAW_LIE_POINTS
                author = self.players.get(author_id)
                if author is not None:
                    author.lies_votes += 1
        for option_id, votes in counts.items():
            if option_id.startswith("lie:") and votes >= 3:
                author_id = option_id.split(":", 1)[1]
                if author_id in self.deltas:
                    self.deltas[author_id] = self.deltas.get(author_id, 0) + DRAW_LIE_SWEEP_BONUS
        artist = self.players.get(self.drawing_artist_id)
        if artist is not None:
            real_votes = counts.get("real", 0)
            artist_bonus = real_votes * DRAW_ARTIST_POINTS
            if real_votes == 0 and len(self._connected_players()) >= 4:
                artist_bonus -= DRAW_ARTIST_PENALTY
            if (
                self.drawing_ink_limit > 0
                and self.drawing_ink_used <= self.drawing_ink_limit * 0.5
                and real_votes > 0
            ):
                artist_bonus += real_votes * DRAW_ARTIST_POINTS
            self.deltas[artist.id] = self.deltas.get(artist.id, 0) + artist_bonus
        option_authors = {
            str(option.get("id") or ""): str(option.get("authorId") or "")
            for option in self.drawing_options
        }
        for voter_id, target in self.drawing_critic_bombs.items():
            author_id = option_authors.get(target, "")
            victim = author_id or (self.drawing_artist_id if target == "real" else "")
            if victim in self.deltas:
                self.deltas[victim] = self.deltas.get(victim, 0) - DRAW_CRITIC_BOMB
        for voter_id, target in self.drawing_critic_crowns.items():
            author_id = option_authors.get(target, "")
            winner = author_id or (self.drawing_artist_id if target == "real" else "")
            if winner in self.deltas:
                self.deltas[winner] = self.deltas.get(winner, 0) + DRAW_CRITIC_CROWN
        self.drawing_audience_choice = ""
        self.drawing_audience_bonus_id = ""
        audience_counts: dict[str, int] = {}
        for option_id in self.drawing_audience_votes.values():
            audience_counts[option_id] = audience_counts.get(option_id, 0) + 1
        if sum(audience_counts.values()) >= 1:
            top = max(audience_counts.values())
            leaders = [option_id for option_id, count in audience_counts.items() if count == top]
            if len(leaders) == 1:
                choice_id = leaders[0]
                self.drawing_audience_choice = choice_id
                if choice_id == "real" and artist is not None:
                    self.deltas[artist.id] = self.deltas.get(artist.id, 0) + 300
                    self.drawing_audience_bonus_id = artist.id
                elif choice_id.startswith("lie:"):
                    author_id = choice_id.split(":", 1)[1]
                    if author_id in self.deltas:
                        self.deltas[author_id] = self.deltas.get(author_id, 0) + 300
                        self.drawing_audience_bonus_id = author_id
        for player in self.players.values():
            if player.spectator:
                continue
            delta = int(self.deltas.get(player.id, 0))
            if delta:
                player.score = max(0, player.score + delta)
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _finish_impostor_vote(self, now: float) -> None:
        if self.phase != "vote":
            return
        counts: dict[str, int] = {}
        for target in self.impostor_votes.values():
            counts[target] = counts.get(target, 0) + 1
        top = max(counts.values()) if counts else 0
        leaders = [target for target, count in counts.items() if count == top and top > 0]
        # Un empate en la cima ya no regala la ronda: si el impostor está entre
        # los más votados, fue descubierto parcialmente y debe jugarse el guess.
        self.impostor_caught = bool(top > 0 and self.impostor_id in leaders)
        if self.impostor_caught:
            self.phase = "guess"
            self.phase_started = now
            self.last_activity = now
            self.rev += 1
            return
        self._finish_impostor_reveal(now)

    def _finish_impostor_reveal(self, now: float) -> None:
        if self.phase == "reveal":
            return
        self.deltas = {p.id: 0 for p in self._connected_players()}
        impostor = self.players.get(self.impostor_id)
        self.impostor_guess_correct = bool(
            self.impostor_guess and fold(self.impostor_guess) == fold(self.impostor_word)
        )
        if self.impostor_caught:
            crew_bonus = 0 if self.impostor_guess_correct else IMPOSTOR_CREW_BONUS
            for player_id, target in self.impostor_votes.items():
                if target == self.impostor_id:
                    self.deltas[player_id] = (
                        self.deltas.get(player_id, 0)
                        + IMPOSTOR_CREW_POINTS
                        + crew_bonus
                    )
                    voter = self.players.get(player_id)
                    if voter is not None:
                        voter.detective += 1
            if impostor is not None and self.impostor_guess_correct:
                self.deltas[impostor.id] = self.deltas.get(impostor.id, 0) + IMPOSTOR_GUESS_POINTS
                impostor.impostor_wins += 1
            if not self.impostor_guess_correct and self.impostor_detective_id in self.players:
                self.deltas[self.impostor_detective_id] = (
                    self.deltas.get(self.impostor_detective_id, 0) + IMPOSTOR_DETECTIVE_POINTS
                )
        elif impostor is not None:
            self.deltas[impostor.id] = self.deltas.get(impostor.id, 0) + IMPOSTOR_ESCAPE_POINTS
            impostor.impostor_wins += 1
            if self.impostor_accomplice_id in self.players:
                self.deltas[self.impostor_accomplice_id] = (
                    self.deltas.get(self.impostor_accomplice_id, 0) + IMPOSTOR_ACCOMPLICE_POINTS
                )
        for player in self.players.values():
            if player.spectator:
                continue
            delta = int(self.deltas.get(player.id, 0))
            if delta:
                player.score = max(0, player.score + delta)
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _resolve_auction(self, now: float) -> None:
        if self.phase != "auction":
            return
        bids = {
            player_id: max(0, int(amount))
            for player_id, amount in self.auction_bids.items()
            if player_id in self.players and not self.players[player_id].spectator
        }
        ranked = sorted(bids.items(), key=lambda item: (-item[1], self.players[item[0]].joined_at))
        self.auction_cancelled = []
        winner_id = ""
        price = 0
        if ranked:
            top_bid = ranked[0][1]
            leaders = [pid for pid, amount in ranked if amount == top_bid and top_bid > 0]
            if len(leaders) > 1:
                self.auction_tie = True
                self.auction_cancelled = [self.players[pid].name for pid in leaders]
                rest = [(pid, amount) for pid, amount in ranked if pid not in leaders and amount > 0]
                if rest:
                    winner_id, price = rest[0]
            elif top_bid > 0:
                winner_id, price = ranked[0]
        self.auction_winner = winner_id
        self.auction_price = price
        self.deltas = {p.id: 0 for p in self._connected_players()}
        winner = self.players.get(winner_id)
        if winner is None:
            if self.rules_version >= 2 and self.auction_secret_effect == "bomb":
                for player in self._connected_players():
                    player.score = max(0, player.score - 100)
                    self.deltas[player.id] = self.deltas.get(player.id, 0) - 100
                self.auction_effect = "Nadie pujó: la bomba secreta explota y todos pierden 100."
            else:
                self.auction_effect = "Nadie se llevó la carta."
            return
        winner.coins = max(0, winner.coins - price)
        winner.auction_wins += 1
        card = self.auction_item
        effect = str(card.get("effect") or "points")
        value = int(card.get("value") or 0)
        rng = random.Random(f"auction:{self.code}:{self._round_no()}")
        if effect == "mystery":
            effect = rng.choice(("points", "coins", "steal", "double_next", "shield"))
            value = rng.choice((300, 450, 600, 800))
        secret_effect = str(self.auction_secret_effect or "")
        secret_value = int(self.auction_secret_value or 0)

        def apply_effect(effect_name: str, amount: int) -> str:
            nonlocal winner
            if winner is None:
                return ""
            doubled = winner.double_next and effect_name in ("points", "steal")
            if doubled:
                winner.double_next = False
            if effect_name == "points":
                total = amount * 2 if doubled else amount
                winner.score += total
                self.deltas[winner.id] = self.deltas.get(winner.id, 0) + total
                return f"+{total} puntos" + (" (doble)" if doubled else "")
            if effect_name == "coins":
                winner.coins += amount
                return f"+{amount} monedas"
            if effect_name == "double_next":
                winner.double_next = True
                return "próximo lote de puntos x2"
            if effect_name == "shield":
                winner.shield = True
                return "escudo reflector"
            if effect_name == "bomb":
                winner.score = max(0, winner.score - amount)
                self.deltas[winner.id] = self.deltas.get(winner.id, 0) - amount
                return f"-{amount} puntos (trampa)"
            if effect_name == "donate":
                rivals = [p for p in self._connected_players() if p.id != winner.id]
                if rivals:
                    last = min(rivals, key=lambda p: (p.score, p.joined_at))
                    paid = min(amount, winner.score)
                    winner.score -= paid
                    last.score += paid
                    self.deltas[winner.id] = self.deltas.get(winner.id, 0) - paid
                    self.deltas[last.id] = self.deltas.get(last.id, 0) + paid
                    return f"donó {paid} a {last.name}"
                return "donación sin receptor"
            if effect_name == "steal":
                rivals = [
                    p for p in self._connected_players()
                    if p.id != winner.id and p.score > 0
                ]
                if self.rules_version < 2:
                    rivals = [p for p in rivals if not p.shield]
                if not rivals:
                    winner.score += 200
                    self.deltas[winner.id] = self.deltas.get(winner.id, 0) + 200
                    return "+200 puntos (sin objetivo)"
                leader = max(rivals, key=lambda p: (p.score, -p.joined_at))
                if self.rules_version >= 2 and leader.shield:
                    leader.shield = False
                    leader.score += 250
                    winner.score = max(0, winner.score - 250)
                    self.deltas[leader.id] = self.deltas.get(leader.id, 0) + 250
                    self.deltas[winner.id] = self.deltas.get(winner.id, 0) - 250
                    return f"escudo reflector de {leader.name}: -250 al ladrón"
                stolen = max(
                    AUCTION_STEAL_MIN,
                    min(AUCTION_STEAL_MAX, int(leader.score * AUCTION_STEAL_PERCENT / 100)),
                )
                stolen = min(leader.score, stolen)
                if doubled:
                    stolen = min(leader.score, stolen * 2)
                leader.score -= stolen
                winner.score += stolen
                self.deltas[leader.id] = self.deltas.get(leader.id, 0) - stolen
                self.deltas[winner.id] = self.deltas.get(winner.id, 0) + stolen
                return f"robó {stolen} a {leader.name}" + (" (doble)" if doubled else "")
            winner.coins += 200
            return "+200 monedas"

        parts = [apply_effect(effect, value)]
        if secret_effect:
            parts.append("secreto: " + apply_effect(secret_effect, secret_value))
        self.auction_effect = " · ".join(part for part in parts if part)
        if self.auction_second_pays:
            others = [
                (pid, amount) for pid, amount in ranked
                if pid != winner_id and amount > 0
            ]
            if others:
                second_id, second_bid = others[0]
                second = self.players.get(second_id)
                if second is not None:
                    paid = second_bid // 2 if self.auction_tie and second_bid == ranked[0][1] else second_bid
                    second.coins = max(0, second.coins - paid)
                    self.auction_effect += f" · {second.name} también pagó {paid}"

    def _build_fibbage_options(self) -> None:
        options: list[dict] = [{"id": "real", "text": self.fibbage_answer, "authorId": ""}]
        seen = {fold(self.fibbage_answer)}
        for player_id, text in self.fibbage_lies.items():
            clean = _clean_game_text(text, 100)
            key = fold(clean)
            if not clean or key in seen:
                continue
            seen.add(key)
            options.append({"id": f"lie:{player_id}", "text": clean, "authorId": player_id})
        for index, decoy in enumerate(self.fibbage_decoys):
            if len(options) >= MIN_PARTY_OPTIONS:
                break
            clean = _clean_game_text(decoy, 100)
            key = fold(clean)
            if not clean or key in seen:
                continue
            seen.add(key)
            options.append({"id": f"house:{index}", "text": clean, "authorId": "", "house": True})
        rng = random.Random(f"fibbage-options:{self.code}:{self._round_no()}")
        rng.shuffle(options)
        self.fibbage_options = options
        self.fibbage_votes = {}

    def _finish_fibbage_vote(self, now: float) -> None:
        if self.phase != "vote":
            return
        multiplier = 2 if self._round_no() >= self.rounds else 1
        self.deltas = {p.id: 0 for p in self._connected_players()}
        option_authors = {
            str(option.get("id") or ""): str(option.get("authorId") or "")
            for option in self.fibbage_options
        }
        for voter in self._connected_players():
            choice = self.fibbage_votes.get(voter.id, "")
            certain = voter.id in self.fibbage_certainty
            if choice == "real":
                base = FIBBAGE_REAL_POINTS * multiplier
                if certain:
                    base = int(base * FIBBAGE_CERTAINTY_MULT)
                self.deltas[voter.id] = self.deltas.get(voter.id, 0) + base
                voter.detective += 1
            elif choice.startswith("lie:"):
                author_id = choice.split(":", 1)[1]
                author_points = FIBBAGE_LIE_POINTS * multiplier
                if certain:
                    author_points = 800
                    self.deltas[voter.id] = self.deltas.get(voter.id, 0) - FIBBAGE_CERTAINTY_RISK
                if author_id in self.deltas:
                    self.deltas[author_id] = self.deltas.get(author_id, 0) + author_points
                author = self.players.get(author_id)
                if author is not None:
                    author.lies_votes += 1
                if author_id in self.fibbage_poisoned:
                    self.deltas[voter.id] = self.deltas.get(voter.id, 0) - FIBBAGE_POISON_STEAL
                    self.deltas[author_id] = self.deltas.get(author_id, 0) + FIBBAGE_POISON_STEAL
            if voter.fibbage_scan_used and choice != "real":
                self.deltas[voter.id] = self.deltas.get(voter.id, 0) - FIBBAGE_SCAN_COST
            hunter = self.fibbage_hunter.get(voter.id, "")
            if hunter:
                option_id, target_id = hunter.split("|", 1)
                if option_authors.get(option_id) == target_id:
                    self.deltas[voter.id] = self.deltas.get(voter.id, 0) + FIBBAGE_HUNTER_BONUS
        counts: dict[str, int] = {}
        for choice in self.fibbage_votes.values():
            counts[choice] = counts.get(choice, 0) + 1
        for option_id, votes in counts.items():
            if option_id.startswith("lie:") and votes >= 3:
                author_id = option_id.split(":", 1)[1]
                if author_id in self.deltas:
                    self.deltas[author_id] = self.deltas.get(author_id, 0) + FIBBAGE_LIE_SWEEP_BONUS * multiplier
        self.fibbage_audience_choice = ""
        self.fibbage_audience_bonus_id = ""
        audience_counts: dict[str, int] = {}
        for option_id in self.fibbage_audience_votes.values():
            audience_counts[option_id] = audience_counts.get(option_id, 0) + 1
        if sum(audience_counts.values()) >= 1:
            top = max(audience_counts.values())
            leaders = [option_id for option_id, count in audience_counts.items() if count == top]
            if len(leaders) == 1:
                choice_id = leaders[0]
                self.fibbage_audience_choice = choice_id
                if choice_id.startswith("lie:"):
                    author_id = choice_id.split(":", 1)[1]
                    if author_id in self.deltas:
                        self.deltas[author_id] = self.deltas.get(author_id, 0) + 300
                        self.fibbage_audience_bonus_id = author_id
        for player in self.players.values():
            if player.spectator:
                continue
            delta = int(self.deltas.get(player.id, 0))
            if delta:
                player.score = max(0, player.score + delta)
        self.phase = "reveal"
        self.phase_started = now
        self.last_activity = now
        self.rev += 1

    def _eligible_vote_target(self, target: str) -> bool:
        if self.game == "impostor":
            return target in self.players and not self.players[target].spectator
        if self.game == "dibujo":
            return any(option["id"] == target for option in self.drawing_options)
        if self.game == "fibbage":
            return any(option["id"] == target for option in self.fibbage_options)
        return False

    def act(
        self,
        player_id: str,
        round_no: int,
        kind: str,
        now: float,
        *,
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
        """Idempotencia de red sin convertir un rechazo en un acierto.

        Solo se memorizan acciones aceptadas. Antes se guardaba la huella antes
        de validar, así que un reintento de una acción rechazada devolvía
        ``True, "ok"`` sin haber cambiado el estado.
        """
        if self.fiesta is not None:
            return self.fiesta.action(
                player_id, kind, now, round_no=round_no, text=text, target=target,
                bid=bid, strokes=strokes, index=index, power=power, wire=wire,
                choices=choices, selected=selected, amount=amount, vote=vote,
            )
        player = self.players.get(player_id)
        if player is None:
            return False, "jugador"
        if round_no != self._round_no():
            return False, "ronda"
        clean_kind = str(kind or "").strip().lower()
        clean_text = _clean_game_text(text, 180)
        clean_target = _clean_game_text(target, 40)
        stroke_key = ""
        if isinstance(strokes, list):
            try:
                stroke_key = hashlib.sha1(
                    json.dumps(strokes, sort_keys=True, default=str).encode("utf-8")
                ).hexdigest()
            except (TypeError, ValueError):
                stroke_key = str(len(strokes))
        descriptor = ""
        if choices is not None or selected is not None:
            descriptor = json.dumps(
                choices if choices is not None else selected,
                sort_keys=True, default=str,
            )
        fingerprint = "|".join((
            player_id, str(round_no), self.phase, clean_kind, clean_target,
            clean_text, power, wire, str(bid), str(amount), str(bool(vote)),
            descriptor, stroke_key,
        ))
        previous = self.recent_actions.get(fingerprint)
        if previous is not None and 0 <= now - previous < 1.5:
            return True, "ok"
        result = self._act_impl(
            player_id, round_no, kind, now,
            text=text, target=target, bid=bid, strokes=strokes, index=index,
            power=power, wire=wire, choices=choices, selected=selected,
            amount=amount, vote=vote,
        )
        if result[0]:
            self.recent_actions[fingerprint] = now
            if len(self.recent_actions) > 64:
                for old_key, _ in sorted(
                    self.recent_actions.items(), key=lambda item: item[1],
                )[:16]:
                    self.recent_actions.pop(old_key, None)
        return result

    def _act_impl(
        self,
        player_id: str,
        round_no: int,
        kind: str,
        now: float,
        *,
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
        """Implementación de las acciones; ``act`` añade la idempotencia."""
        player = self.players.get(player_id)
        if player is None:
            return False, "jugador"
        if round_no != self._round_no():
            return False, "ronda"
        kind = str(kind or "").strip().lower()
        text = _clean_game_text(text, 180)
        target = _clean_game_text(target, 40)

        if kind == "auction_ghost":
            if self.rules_version < 2:
                return False, "v1"
            if self.game != "subasta" or self.phase != "auction" or player.spectator:
                return False, "fase"
            if player.ghost_used:
                return False, "duplicada"
            if self.auction_ghost_bidder:
                return False, "ocupado"
            player.ghost_used = True
            self.auction_ghost_bidder = player.id
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            return True, "ok"

        if kind == "draw_early_guess":
            return self.draw_early_guess(player.id, text, now)

        if kind == "draw_critic":
            return self.draw_critic(player.id, target, text, now)

        if kind == "charada_director":
            return self.charada_action(player.id, str(index), now)

        if kind == "charada_buzz":
            return self.charada_action(player.id, "buzz", now)

        if kind == "charada_buzz_result":
            return self.charada_action(
                player.id, "buzz_correct" if text == "correct" else "buzz_fail", now,
            )

        if kind == "charada_help":
            return self.charada_action(
                player.id, "help_genre" if text == "genre" else "help_year", now,
            )

        if kind == "trivia_attack":
            return self.trivia_attack(player.id, power, target, now)

        if kind == "trivia_tap":
            return self.trivia_tap(player.id, now)

        if kind == "trivia_punish":
            return self.trivia_punish(player.id, wire, now, vote=bool(vote))

        if kind == "trivia_sort":
            return self.trivia_sort(player.id, choices, now)

        if kind == "trivia_assault":
            return self.trivia_assault(player.id, selected, now)

        if kind == "wager_buy":
            return self.wager_buy(player.id, now)

        if kind == "wager_lock":
            return self.wager_lock(player.id, index, now)

        if kind == "wager_side_bet":
            return self.wager_side_bet(player.id, target, amount, now)

        if kind == "impostor_coartada":
            if self.game != "impostor" or self.phase != "coartada" or player.spectator:
                return False, "fase"
            try:
                value = int(index)
            except (TypeError, ValueError):
                value = 5
            value = max(0, min(9, value))
            if player.id in self.impostor_coartada:
                return False, "duplicada"
            self.impostor_coartada[player.id] = value
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if self._all_impostor_coartada():
                self.phase = "vote"
                self.phase_started = now
                self.rev += 1
            return True, "ok"

        if kind == "impostor_advance":
            if self.game != "impostor" or self.phase != "questions":
                return False, "fase"
            asker, _ = self.current_impostor_pair()
            if asker is None or player.id != asker.id:
                return False, "turno"
            self.advance_impostor_turn(now)
            return True, "ok"

        if kind == "impostor_vote":
            if self.game != "impostor" or self.phase != "vote" or player.spectator:
                return False, "fase"
            if target == player.id or not self._eligible_vote_target(target):
                return False, "objetivo"
            if player.vote_round == round_no:
                return False, "duplicada"
            self.impostor_votes[player.id] = target
            player.vote_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if self._all_players_voted(now):
                self._finish_impostor_vote(now)
            return True, "ok"

        if kind == "impostor_guess":
            if self.game != "impostor" or self.phase != "guess" or player.id != self.impostor_id:
                return False, "fase"
            if not text:
                return False, "vacío"
            self.impostor_guess = text
            player.submitted_round = round_no
            self.last_activity = now
            self.rev += 1
            self._finish_impostor_reveal(now)
            return True, "ok"

        if kind == "draw_pick_prompt":
            if self.rules_version < 2:
                return False, "v1"
            if self.game != "dibujo" or self.phase != "draw" or player.id != self.drawing_artist_id:
                return False, "fase"
            if self.drawing_strokes:
                return False, "ya dibujó"
            try:
                choice = int(index)
            except (TypeError, ValueError):
                choice = 0
            if choice < 0 or choice >= len(self.drawing_prompt_options):
                return False, "opción"
            selected = self.drawing_prompt_options[choice]
            self.drawing_prompt = str(selected.get("prompt") or "")
            self.drawing_decoys = list(selected.get("decoys") or [])
            self.last_activity = now
            self.rev += 1
            return True, "ok"

        if kind == "draw_partial":
            if self.game != "dibujo" or self.phase != "draw" or player.id != self.drawing_artist_id:
                return False, "fase"
            # El cliente manda el arreglo completo (no deltas) para que Undo y
            # Borrar no desincronicen la tele. Se limita a un update cada 1.8 s.
            if now - self.drawing_partial_at < 1.8:
                return True, "ok"
            clean_strokes = _clean_strokes(strokes)
            ink = _ink_cost(clean_strokes)
            if self.drawing_ink_limit > 0 and ink > self.drawing_ink_limit + 0.5:
                return False, "tinta"
            self.drawing_strokes = clean_strokes
            self.drawing_ink_used = ink
            self.drawing_partial_at = now
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            return True, "ok"

        if kind == "draw_submit":
            if self.game != "dibujo" or self.phase != "draw" or player.id != self.drawing_artist_id:
                return False, "fase"
            if player.submitted_round == round_no:
                return False, "duplicada"
            clean_strokes = _clean_strokes(strokes)
            ink = _ink_cost(clean_strokes)
            if self.drawing_ink_limit > 0 and ink > self.drawing_ink_limit + 0.5:
                return False, "tinta"
            self.drawing_strokes = clean_strokes
            self.drawing_ink_used = ink
            player.submitted_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            self._begin_draw_title(now)
            return True, "ok"

        if kind == "draw_title":
            if self.game != "dibujo" or self.phase != "title" or player.spectator:
                return False, "fase"
            if player.id == self.drawing_artist_id:
                return False, "artista"
            if not text:
                return False, "vacío"
            if _too_close_title(text, self.drawing_prompt):
                return False, "cerca"
            if player.submitted_round == round_no:
                return False, "duplicada"
            self.drawing_titles[player.id] = text
            player.submitted_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if self._all_draw_titles():
                self._build_draw_options()
                self.phase = "vote"
                self.phase_started = now
                self.rev += 1
            return True, "ok"

        if kind == "draw_vote":
            if self.game != "dibujo" or self.phase != "vote":
                return False, "fase"
            if player.id == self.drawing_artist_id:
                return False, "artista"
            if not self._eligible_vote_target(target):
                return False, "objetivo"
            if player.vote_round == round_no:
                return False, "duplicada"
            if player.spectator:
                self.drawing_audience_votes[player.id] = target
            else:
                self.drawing_votes[player.id] = target
            player.vote_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if not player.spectator and self._all_draw_votes():
                self._finish_draw_vote(now)
            return True, "ok"

        if kind == "auction_bid":
            if self.game != "subasta" or self.phase != "auction" or player.spectator:
                return False, "fase"
            if player.bid_round == round_no:
                return False, "duplicada"
            try:
                amount = int(bid)
            except (TypeError, ValueError):
                amount = 0
            amount = max(0, min(int(player.coins), amount))
            if amount > 0:
                amount = min(
                    int(player.coins),
                    max(AUCTION_STEP, (amount // AUCTION_STEP) * AUCTION_STEP),
                )
                if amount < AUCTION_STEP:
                    amount = 0  # con menos de 50 monedas no hay puja válida
            self.auction_bids[player.id] = amount
            player.bid = amount
            player.bid_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if self._all_bids():
                self._resolve_auction(now)
                self.phase = "auction_reveal"
                self.phase_started = now
                self.rev += 1
            return True, "ok"

        if kind == "fibbage_lie":
            if self.game != "fibbage" or self.phase != "write" or player.spectator:
                return False, "fase"
            if not text:
                return False, "vacío"
            if fold(text) == fold(self.fibbage_answer):
                return False, "respuesta real"
            if player.submitted_round == round_no:
                return False, "duplicada"
            self.fibbage_lies[player.id] = text
            try:
                poison_cost = int(amount or 0)
            except (TypeError, ValueError):
                poison_cost = 0
            if self.rules_version >= 2 and poison_cost >= FIBBAGE_POISON_COST and player.score >= FIBBAGE_POISON_COST:
                player.score -= FIBBAGE_POISON_COST
                self.deltas[player.id] = self.deltas.get(player.id, 0) - FIBBAGE_POISON_COST
                self.fibbage_poisoned.add(player.id)
            player.submitted_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if self._all_fibbage_lies():
                self._build_fibbage_options()
                self.phase = "vote"
                self.phase_started = now
                self.rev += 1
            return True, "ok"

        if kind == "fibbage_scan":
            if self.game != "fibbage" or self.phase != "vote" or player.spectator:
                return False, "fase"
            if self.rules_version < 2 or player.fibbage_scan_used:
                return False, "duplicada"
            lies = [
                option["id"] for option in self.fibbage_options
                if option["id"].startswith("lie:") and option.get("authorId") != player.id
            ]
            rng = random.Random(f"scan:{self.code}:{self.round_index}:{player.id}")
            rng.shuffle(lies)
            count = 1 if len(self._connected_players()) <= 4 else 2
            self.fibbage_scans[player.id] = lies[:count]
            player.fibbage_scan_used = True
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            return True, "ok"

        if kind == "fibbage_hunter":
            if self.game != "fibbage" or self.phase != "vote" or player.spectator:
                return False, "fase"
            if self.rules_version < 2 or player.fibbage_hunter_guess:
                return False, "duplicada"
            if not any(option["id"] == text for option in self.fibbage_options):
                return False, "opción"
            if target == player.id or target not in self.players:
                return False, "objetivo"
            player.fibbage_hunter_guess = f"{text}|{target}"
            self.fibbage_hunter[player.id] = player.fibbage_hunter_guess
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            return True, "ok"

        if kind == "fibbage_vote":
            if self.game != "fibbage" or self.phase != "vote":
                return False, "fase"
            if vote and self.rules_version < 2:
                return False, "v1"
            if vote and player.fibbage_scan_used:
                return False, "escáner"
            if not self._eligible_vote_target(target):
                return False, "objetivo"
            if target in self.fibbage_scans.get(player.id, []):
                return False, "escaneada"
            if target == f"lie:{player.id}":
                return False, "tu mentira"
            if player.vote_round == round_no:
                return False, "duplicada"
            if player.spectator:
                self.fibbage_audience_votes[player.id] = target
            else:
                self.fibbage_votes[player.id] = target
                if self.rules_version >= 2 and vote:
                    self.fibbage_certainty.add(player.id)
                    player.fibbage_certainty = True
            player.vote_round = round_no
            player.connected = True
            player.last_seen = now
            self.last_activity = now
            self.rev += 1
            if not player.spectator and self._all_fibbage_votes():
                self._finish_fibbage_vote(now)
            return True, "ok"

        return False, "acción"

    def answer(self, player_id: str, round_no: int, option: int, now: float) -> tuple[bool, str]:
        if self.game == "wager":
            if self.rules_version >= 2:
                return self.wager_lock(player_id, option, now)
            return self._wager_answer(player_id, round_no, option, now)
        if self.phase != "question":
            return False, "fase"
        if round_no != self.round_index + 1:
            return False, "ronda"
        if self.rules_version >= 2 and self.trivia_round_kind != "multiple_choice":
            return False, "formato"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player_id in self.trivia_parasite:
            return False, "parásito"
        if player_id in self.trivia_frozen and self.trivia_frozen[player_id] < TRIVIA_FREEZE_TAPS:
            return False, "congelado"
        if option < 0 or option >= len(self.current_question()["options"]):
            return False, "opción"
        if round_no in player.answers:
            return False, "duplicada"
        elapsed = now - self.phase_started
        if not self.players_pace and (elapsed < 0 or elapsed > self.question_seconds):
            return False, "tardía"
        question = self.current_question()
        correct = int(option) == int(question["answer_index"])
        total_ms = self.question_seconds * 1000
        remaining = max(0, total_ms - int(elapsed * 1000))
        points, streak_after = answer_points(
            question["difficulty"], remaining, total_ms, player.streak, correct, self.fair
        )
        player.answers[round_no] = Answer(
            option=int(option),
            at=now,
            correct=correct,
            points=points,
            streak_after=streak_after,
        )
        if correct and streak_after >= 2:
            next_round = round_no + 1
            if next_round <= self.rounds:
                player.attack_ready_round = next_round
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self.players_pace and self._all_answered():
            if self.rules_version >= 2:
                self._begin_trivia_resolution(now)
            else:
                self._begin_reveal(now)
        return True, "ok"

    def _wager_answer(self, player_id: str, round_no: int, option: int, now: float) -> tuple[bool, str]:
        if self.phase != "clue":
            return False, "fase"
        if round_no != self.round_index + 1:
            return False, "ronda"
        player = self.players.get(player_id)
        if player is None:
            return False, "jugador"
        if player.wager_done_round == round_no:
            return False, "terminada"
        if player.wager_round != round_no or player.wager <= 0:
            player.wager_round = round_no
            player.wager = WAGER_MIN
        round_data = self.current_wager_round()
        options = round_data.get("options") or []
        if option < 0 or option >= len(options):
            return False, "opción"
        elapsed = now - self.phase_started
        if not self.players_pace and (elapsed < 0 or elapsed > self.clue_seconds):
            return False, "tardía"
        correct = int(option) == int(round_data["answer_index"])
        factor = CLUE_FACTORS[min(self.clue_index, len(CLUE_FACTORS) - 1)]
        player.wager_option = int(option)
        player.wager_correct = correct
        player.wager_delta = player.wager * factor if correct else -player.wager
        player.clue_answered = self.clue_index
        player.wager_done_round = round_no
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self._all_wager_done():
            self._begin_reveal(now)
        return True, "ok"

    def wager(self, player_id: str, round_no: int, points: Any, now: float) -> tuple[bool, str]:
        """Apuesta de la ronda. Solo antes de la primera pista."""
        if self.game != "wager" or self.phase != "wager":
            return False, "fase"
        if round_no != self.round_index + 1:
            return False, "ronda"
        player = self.players.get(player_id)
        if player is None or player.spectator:
            return False, "jugador"
        if player.wager_round == round_no and player.wager > 0:
            return True, "ok"
        player.wager_round = round_no
        player.wager = clamp_wager(points)
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self._all_wagered():
            self._begin_clue(0, now)
        return True, "ok"

    def charada_action(self, player_id: str, action: str, now: float) -> tuple[bool, str]:
        """Director elige restricción; el actor mueve Correcto / Pasar."""
        if self.game != "charadas":
            return False, "juego"
        if self.rules_version < 2 and action in (
            "buzz", "buzz_correct", "buzz_fail", "help_genre", "help_year",
        ):
            return False, "v1"
        if self.phase == "director":
            if player_id != self.charada_director_id:
                return False, "director"
            try:
                choice = int(action)
            except (TypeError, ValueError):
                return False, "opción"
            if choice < 0 or choice >= len(self.charada_director_options):
                return False, "opción"
            if self.charada_director_chosen:
                return False, "duplicada"
            self.charada_restriction = self.charada_director_options[choice]
            self.charada_director_chosen = True
            self._begin_charada_acting(now)
            return True, "ok"
        if self.phase != "turn":
            return False, "fase"
        player = self.players.get(player_id)
        if player is None:
            return False, "jugador"
        if action == "buzz":
            if player_id == self.actor_id or player.spectator:
                return False, "turno"
            if now < player.charada_buzz_cooldown:
                return False, "cooldown"
            if self.charada_buzzer_id:
                return False, "ocupado"
            remaining = max(1.0, self.charada_seconds - (now - self.phase_started))
            self.charada_buzzer_id = player_id
            self.charada_buzzer_started = now
            self.charada_buzzer_remaining = remaining
            self.last_activity = now
            self.rev += 1
            return True, "ok"
        if action in ("buzz_correct", "buzz_fail"):
            if player_id != self.actor_id or not self.charada_buzzer_id:
                return False, "timbre"
            self._finish_charada_buzz(action == "buzz_correct", now)
            return True, "ok"
        if action in ("help_genre", "help_year"):
            if player_id != self.actor_id or self.charada_buzzer_id:
                return False, "turno"
            cost = CHARADA_HELP_GENRE_COST if action == "help_genre" else CHARADA_HELP_YEAR_COST
            if (action == "help_genre" and self.charada_help_genre) or (
                action == "help_year" and self.charada_help_year
            ):
                return False, "duplicada"
            self.charada_help_penalty += cost
            if action == "help_genre":
                self.charada_help_genre = True
            else:
                self.charada_help_year = True
            self.last_activity = now
            self.rev += 1
            return True, "ok"
        if player_id != self.actor_id:
            return False, "turno"
        item = self.current_charada()
        if not item:
            self._begin_reveal(now)
            return False, "sin cartas"
        action = str(action or "").strip().lower()
        if action not in ("correct", "pass", "correcto", "pasar"):
            return False, "acción"
        self.charada_last = dict(item)
        if action in ("correct", "correcto"):
            self.charada_correct += 1
        else:
            self.charada_passes += 1
        self.charada_cursor += 1
        self.charada_index += 1
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        self.rev += 1
        if self.charada_passes >= CHARADA_MAX_PASSES or self.charada_cursor >= len(self.deck):
            self._begin_reveal(now)
        return True, "ok"

    def ready(self, player_id: str, round_no: int, now: float) -> bool:
        """Marca «Continuar» en el reveal; se exige a todos antes de avanzar."""
        if self.phase not in ("reveal", "auction_reveal"):
            return False
        if round_no != self.round_index + 1:
            return False
        player = self.players.get(player_id)
        if player is None:
            return False
        player.connected = True
        player.last_seen = now
        self.last_activity = now
        if player.ready_round >= round_no:
            return True
        player.ready_round = round_no
        self.rev += 1
        return True

    def next_round(self, now: float) -> bool:
        if self.fiesta is not None:
            return self.fiesta.next(now)
        if self.phase not in ("reveal", "auction_reveal", "punishment_reveal"):
            return False
        if self.players_pace and not self._all_ready():
            return False
        self._advance(now)
        return True

    def close(self, now: float, reason: str = "host") -> None:
        if self.phase == "closed":
            return
        self.phase = "closed"
        self.closed_reason = reason
        self.ended_at = now
        self.last_activity = now
        self.rev += 1

    def touch(self, now: float) -> None:
        self.last_activity = now

    def react(self, player_id: str, kind: int, now: float) -> bool:
        if player_id not in self.players:
            return False
        if kind < 0 or kind > 5:
            return False
        player = self.players[player_id]
        if now - player.last_reaction < 2.0:
            return False
        player.last_reaction = now
        self.reactions.append({"playerId": player_id, "kind": int(kind), "at": int(now * 1000)})
        if len(self.reactions) > 6:
            self.reactions = self.reactions[-6:]
        self.last_activity = now
        self.rev += 1
        return True

    def report(self, question_id: str, player_id: str = "") -> int:
        if not question_id:
            return 0
        if player_id:
            reporters = self.reports_by_player.setdefault(question_id, set())
            if player_id in reporters:
                return self.reports.get(question_id, 0)
            reporters.add(player_id)
        self.reports[question_id] = self.reports.get(question_id, 0) + 1
        return self.reports[question_id]

    # -- lectura --------------------------------------------------------------
    def highlights_payload(self) -> list[dict]:
        """Medallas automáticas del cierre; se calculan de lo que de verdad pasó."""
        players = [p for p in self.players.values() if not p.spectator]
        if not players:
            return []
        awards: list[dict] = []
        medals_per_player: Counter[str] = Counter()

        def award(
            award_id: str, title: str, detail: str, winner: Player | None, *,
            force: bool = False,
        ) -> None:
            if winner is None:
                return
            if not force and medals_per_player[winner.id] >= 2:
                return
            medals_per_player[winner.id] += 1
            awards.append({
                "id": award_id, "title": title, "name": winner.name,
                "detail": detail, "color": winner.color,
            })

        liar = max(players, key=lambda p: (p.lies_votes, -p.joined_at))
        if liar.lies_votes > 0:
            award(
                "liar", "El más mentiroso",
                f"Sus mentiras mordieron {liar.lies_votes} "
                f"{'vez' if liar.lies_votes == 1 else 'veces'}.",
                liar,
            )
        detective = max(players, key=lambda p: (p.detective, -p.joined_at))
        if detective.detective > 0:
            award(
                "detective", "El detective",
                f"Acertó {detective.detective} "
                f"{'vez' if detective.detective == 1 else 'veces'}.",
                detective,
            )
        shark = max(players, key=lambda p: (p.auction_wins, -p.joined_at))
        if shark.auction_wins > 0:
            award(
                "shark", "El tiburón de la subasta",
                f"Se llevó {shark.auction_wins} "
                f"{'lote' if shark.auction_wins == 1 else 'lotes'}.",
                shark,
            )
        impostor = max(players, key=lambda p: (p.impostor_wins, -p.joined_at))
        if impostor.impostor_wins > 0:
            award(
                "impostor", "El camaleón",
                f"Se escapó {impostor.impostor_wins} "
                f"{'vez' if impostor.impostor_wins == 1 else 'veces'} sin que lo atraparan.",
                impostor,
            )
        lost = min(players, key=lambda p: (p.score, p.joined_at))
        award(
            "lost", "El más despistado",
            f"Terminó con {lost.score} puntos. La próxima es suya.",
            lost, force=True,
        )
        return awards

    def current_question(self) -> dict:
        return self.questions[min(self.round_index, len(self.questions) - 1)]

    def phase_duration(self) -> float:
        """Duración nominal de la fase; el cliente la usa como denominador del reloj."""
        if self.fiesta is not None:
            return self.fiesta._duration()
        if self.phase == "countdown":
            return float(self.countdown_seconds)
        if self.phase == "question":
            return float(self.question_seconds)
        if self.phase == "attack":
            return float(TRIVIA_ATTACK_SECONDS)
        if self.phase == "punishment":
            return float(TRIVIA_PUNISH_SECONDS)
        if self.phase == "punishment_reveal":
            return float(TRIVIA_PUNISH_REVEAL_SECONDS)
        if self.phase == "sort":
            return float(TRIVIA_SORT_SECONDS)
        if self.phase == "assault":
            return float(TRIVIA_ASSAULT_SECONDS)
        if self.phase == "director":
            return float(CHARADA_DIRECTOR_SECONDS)
        if self.phase == "turn":
            return float(self.charada_seconds)
        if self.phase == "wager":
            return float(self.wager_seconds)
        if self.phase == "clue":
            return float(self.clue_seconds)
        if self.phase == "secret":
            return float(SECRET_SECONDS)
        if self.phase == "questions":
            return float(self._impostor_turn_seconds())
        if self.phase == "vote":
            if self.game == "dibujo":
                return float(DRAW_VOTE_SECONDS)
            if self.game == "fibbage":
                return float(FIBBAGE_VOTE_SECONDS)
            return float(VOTE_SECONDS)
        if self.phase == "guess":
            return float(GUESS_SECONDS)
        if self.phase == "draw":
            return float(DRAW_SECONDS)
        if self.phase == "title":
            return float(DRAW_TITLE_SECONDS)
        if self.phase == "auction":
            return float(AUCTION_SECONDS)
        if self.phase == "auction_reveal":
            return float(AUCTION_REVEAL_SECONDS)
        if self.phase == "write":
            return float(FIBBAGE_WRITE_SECONDS)
        if self.phase == "reveal":
            if self.rules_version >= 2 and self.game in ("trivia", "wager"):
                return float(TRIVIA_REVEAL_SECONDS)
            return float(self.reveal_seconds)
        return 0.0

    def phase_ends_at(self) -> float:
        duration = self.phase_duration()
        return self.phase_started + duration if duration > 0 else 0.0

    def snapshot(
        self,
        role: str,
        since: int | None,
        now: float,
        player_id: str | None = None,
        *,
        display_authorized: bool = False,
    ) -> dict | None:
        """Lo que ve un rol. `None` = no cambió (la ruta responde 204)."""
        if since is not None and int(since) == self.rev:
            return None
        if role not in ROLES:
            role = "player"
        if self.fiesta is not None:
            return self.fiesta.snapshot(role, player_id, now, display_authorized)
        round_no = self.round_index + 1
        question = None
        answer = None
        charada = None
        wager = None
        trivia = None
        if self.game == "trivia" and self.rules_version >= 2:
            me = self.players.get(player_id or "")
            eligible = player_id in {p.id for p in self._eligible_attackers()}
            losers = self._trivia_losers() if self.phase in ("punishment", "punishment_reveal") else []
            safe = [p for p in self._connected_players() if p.id not in losers]
            wire_counts = Counter(self.trivia_punishment_votes.values())
            trivia = {
                "roundKind": self.trivia_round_kind,
                "attack": {
                    "canAttack": bool(eligible and self.phase == "attack"),
                    "cost": TRIVIA_ATTACK_COST,
                    "powers": list(TRIVIA_ATTACK_POWERS),
                    "targets": [
                        {"id": p.id, "name": p.name}
                        for p in self._connected_players()
                        if me is None or p.id != me.id
                    ],
                    "used": list(self.trivia_attacks),
                    "usedCount": len(self.trivia_attack_power),
                    "eligibleCount": len(self._eligible_attackers()),
                },
                "you": {
                    "canAttack": bool(eligible and self.phase == "attack"),
                    "attackPower": self.trivia_attack_power.get(player_id or "", ""),
                    "attackTarget": self.trivia_attack_target.get(player_id or "", ""),
                    "frozen": bool(me and me.id in self.trivia_frozen),
                    "frozenTaps": int(self.trivia_frozen.get(player_id or "", 0)),
                    "freezeTaps": TRIVIA_FREEZE_TAPS,
                    "glitched": bool(me and me.id in self.trivia_glitched),
                    "parasiteTarget": self.trivia_parasite.get(player_id or "", ""),
                    "parasiteTargetName": (
                        self.players[self.trivia_parasite[player_id]].name
                        if player_id in self.trivia_parasite and self.trivia_parasite[player_id] in self.players
                        else ""
                    ),
                    "sortSubmitted": bool(me and me.sort_round == self.round_index + 1),
                    "sortChoices": list(me.sort_choices) if me else [],
                    "assaultSubmitted": bool(me and me.assault_round == self.round_index + 1),
                    "assaultSelected": list(me.assault_selected) if me else [],
                    "punishmentWire": me.punishment_wire if me else "",
                    "punishmentVote": self.trivia_punishment_votes.get(player_id or "", ""),
                    "isLoser": bool(player_id and player_id in losers),
                },
                "sort": {
                    "items": [
                        {"id": item["id"], "text": item["text"], "art": item["art"]}
                        for item in self.trivia_sort_items
                    ],
                    "left": "Antes",
                    "right": "Después",
                    "submitted": bool(me and me.sort_round == self.round_index + 1),
                    "choices": list(me.sort_choices) if me else [],
                    "answers": list(self.trivia_sort_correct) if self.phase == "reveal" else [],
                } if self.trivia_round_kind == "sort" else None,
                "assault": {
                    "items": [
                        {"id": item["id"], "text": item["text"], "art": item["art"]}
                        for item in self.trivia_assault_items
                    ],
                    "prompt": "Selecciona las que estrenaron antes de 2015",
                    "submitted": bool(me and me.assault_round == self.round_index + 1),
                    "selected": list(me.assault_selected) if me else [],
                    "answers": list(self.trivia_assault_correct) if self.phase == "reveal" else [],
                } if self.trivia_round_kind == "assault" else None,
                "punishment": {
                    "losers": [
                        {"id": pid, "name": self.players[pid].name}
                        for pid in losers if pid in self.players
                    ],
                    "hotWire": self.trivia_punishment_hot if self.phase == "punishment_reveal" else "",
                    "votes": dict(wire_counts),
                    "penalties": list(self.trivia_punishment_penalties.items())
                    if self.phase == "punishment_reveal" else [],
                    "wires": ["red", "blue", "yellow", "green"],
                } if self.phase in ("punishment", "punishment_reveal") else None,
            }
        if self.game == "trivia" and self.phase in (
            "question", "reveal", "punishment", "punishment_reveal",
        ) and self.questions:
            current = self.current_question()
            question = {
                "id": current["id"],
                "kind": current["kind"],
                "prompt": current["prompt"],
                "options": list(current["options"]),
                "art": current.get("art", ""),
                "difficulty": int(current.get("difficulty", 1)),
            }
            if self.phase in ("reveal", "punishment_reveal"):
                answer = {"option": int(current["answer_index"]), "text": current["answer"]}
            if self.rules_version >= 2 and role == "player" and player_id:
                if player_id in self.trivia_glitched:
                    question["options"] = [
                        _glitch_text(option, f"{self.code}:{self.round_index}:{player_id}:{index}")
                        for index, option in enumerate(question["options"])
                    ]
                if player_id in self.trivia_frozen and self.trivia_frozen[player_id] < TRIVIA_FREEZE_TAPS:
                    question["options"] = []
        elif self.game == "wager":
            data = self.current_wager_round()
            if data:
                if self.rules_version >= 2:
                    # La TV puede mostrar el póster público de la pista 1.
                    # Un jugador solo lo recibe al comprar la etapa 3 o en el reveal:
                    # enviar la URL antes permite abrirla sin pagar la información.
                    wager_me = self.players.get(player_id or "")
                    private_stage = (
                        max(1, min(3, int(wager_me.wager_private_stage)))
                        if wager_me is not None else 1
                    )
                    poster_visible = (
                        self.phase == "reveal"
                        or (role == "display" and display_authorized)
                        or (wager_me is not None and not wager_me.spectator and private_stage >= 3)
                    )
                    visible_poster = str(data.get("poster") or "") if poster_visible else ""
                    if self.phase == "clue":
                        clue = self.current_clue()
                        question = {
                            "id": data["id"],
                            "kind": "wager",
                            "prompt": "¿Cuál es el título?",
                            "options": list(data.get("options") or []),
                            "art": visible_poster,
                            "difficulty": int(data.get("difficulty", 2)),
                        }
                    elif self.phase == "reveal":
                        question = {
                            "id": data["id"],
                            "kind": "wager",
                            "prompt": "¿Cuál era el título?",
                            "options": list(data.get("options") or []),
                            "art": visible_poster,
                            "difficulty": int(data.get("difficulty", 2)),
                        }
                        answer = {"option": int(data["answer_index"]), "text": data["answer"]}
                    stage = max(1, min(3, int(self.wager_stage)))
                    clues = data.get("clues") or []
                    clue_text = ""
                    if self.phase == "clue":
                        if stage == 1:
                            bits = []
                            if data.get("year"):
                                bits.append(f"Año: {data['year']}")
                            genres = [str(g) for g in (data.get("genres") or []) if str(g).strip()]
                            if genres:
                                bits.append("Géneros: " + ", ".join(genres[:3]))
                            clue_text = " · ".join(bits) or "Póster pixelado"
                        elif stage == 2 and len(clues) > 0:
                            clue_text = str(clues[0].get("text") or "")
                        elif stage >= 3 and len(clues) > 1:
                            clue_text = str(clues[1].get("text") or "")
                    wager = {
                        "clueIndex": stage if self.phase in ("clue", "reveal") else 0,
                        "clueCount": 3,
                        "factor": WAGER_V2_FACTORS[stage - 1],
                        "stakeOptions": [],
                        "title": data.get("title", "") if self.phase == "reveal" else "",
                        "poster": visible_poster,
                        "year": int(data.get("year") or 0),
                        "stage": stage,
                        "pot": int(self.wager_pot),
                        "ante": WAGER_ANTE,
                        "buyCost": WAGER_BUY_COST,
                        "posterReveal": 1.0 if self.phase == "reveal" else (0.06, 0.5, 1.0)[stage - 1],
                        "clue": clue_text,
                        "genres": list(data.get("genres") or []),
                        "rollover": int(self.wager_rollover),
                        "lockedStage1": [
                            {"id": pid, "name": self.players[pid].name}
                            for pid, locked_stage in sorted(self.wager_locked.items())
                            if locked_stage == 1 and pid in self.players
                        ],
                        "sideBets": [
                            {
                                "id": pid,
                                "name": self.players[pid].name,
                                "targetId": str(bet.get("target") or ""),
                                "targetName": self.players.get(str(bet.get("target") or "")).name
                                if str(bet.get("target") or "") in self.players else "",
                                "amount": int(bet.get("amount") or 0),
                            }
                            for pid, bet in self.wager_side_bets.items()
                            if pid in self.players
                        ],
                        "sideResults": [
                            {"id": pid, "name": self.players[pid].name, "delta": int(delta)}
                            for pid, delta in self.wager_side_results.items()
                            if pid in self.players
                        ] if self.phase == "reveal" else [],
                    }
                else:
                    if self.phase == "clue":
                        clue = self.current_clue()
                        question = {
                            "id": data["id"],
                            "kind": "wager",
                            "prompt": clue.get("text", ""),
                            "options": list(data.get("options") or []),
                            "art": clue.get("art", ""),
                            "difficulty": int(data.get("difficulty", 2)),
                        }
                    elif self.phase == "reveal":
                        question = {
                            "id": data["id"],
                            "kind": "wager",
                            "prompt": "¿Cuál era el título?",
                            "options": list(data.get("options") or []),
                            "art": data.get("poster", ""),
                            "difficulty": int(data.get("difficulty", 2)),
                        }
                        answer = {"option": int(data["answer_index"]), "text": data["answer"]}
                    wager = {
                        "clueIndex": (self.clue_index + 1) if self.phase in ("clue", "reveal") else 0,
                        "clueCount": CLUE_COUNT,
                        "factor": CLUE_FACTORS[min(self.clue_index, len(CLUE_FACTORS) - 1)],
                        "stakeOptions": list(WAGER_STAKES),
                        "title": data.get("title", "") if self.phase == "reveal" else "",
                        "poster": data.get("poster", "") if self.phase == "reveal" else "",
                        "year": int(data.get("year") or 0) if self.phase == "reveal" else 0,
                    }
        elif self.game == "charadas" and self.phase in ("director", "turn", "reveal"):
            actor = self.players.get(self.actor_id)
            reveal = self.phase == "reveal"
            item = self.charada_last if reveal else self.current_charada()
            is_actor = role == "player" and player_id == self.actor_id
            is_saboteur = role == "player" and player_id == self.charada_saboteur_id
            is_director = role == "player" and player_id == self.charada_director_id
            can_see = reveal or is_actor or (is_saboteur and self.rules_version >= 2)
            charada = {
                "actorId": self.actor_id,
                "actorName": actor.name if actor else "",
                "index": self.charada_index,
                "passes": self.charada_passes,
                "maxPasses": CHARADA_MAX_PASSES,
                "correct": self.charada_correct,
                "points": CHARADA_POINTS,
                "title": item.get("title", "") if can_see else "",
                "poster": item.get("poster", "") if can_see else "",
                "role": (
                    "actor" if is_actor else "saboteur" if is_saboteur
                    else "director" if is_director else "crew"
                ) if role == "player" else "",
                "directorId": self.charada_director_id,
                "directorName": self.players[self.charada_director_id].name
                if self.charada_director_id in self.players else "",
                "directorOptions": list(self.charada_director_options)
                if is_director and self.phase == "director" else [],
                "directorChosen": bool(self.charada_director_chosen),
                "restriction": self.charada_restriction if (
                    can_see or is_director or role == "display" or self.phase == "reveal"
                ) else "",
                "saboteurId": self.charada_saboteur_id if reveal else "",
                "saboteurName": self.players[self.charada_saboteur_id].name
                if reveal and self.charada_saboteur_id in self.players else "",
                "buzzerId": self.charada_buzzer_id,
                "buzzerName": self.players[self.charada_buzzer_id].name
                if self.charada_buzzer_id in self.players else "",
                "buzzerActive": bool(self.charada_buzzer_id),
                "buzzerSeconds": max(
                    0, int(CHARADA_BUZZ_SECONDS - (now - self.charada_buzzer_started))
                ) if self.charada_buzzer_id else 0,
                "helpGenre": bool(self.charada_help_genre),
                "helpYear": bool(self.charada_help_year),
                "genre": (", ".join(item.get("genres") or [])) if (
                    self.charada_help_genre or reveal
                ) else "",
                "year": int(item.get("year") or 0) if (
                    self.charada_help_year or reveal
                ) else 0,
            }
        connected = self._connected_players()
        impostor = None
        drawing = None
        auction = None
        fibbage = None
        if self.game == "impostor" and self.phase in (
            "secret", "questions", "coartada", "vote", "guess", "reveal",
        ):
            me = self.players.get(player_id or "")
            reveal = self.phase == "reveal"
            is_spectator = bool(me and me.spectator)
            is_impostor = bool(me and me.id == self.impostor_id)
            is_accomplice = bool(me and me.id == self.impostor_accomplice_id)
            is_detective = bool(me and me.id == self.impostor_detective_id)
            can_see_word = reveal or (
                role == "player" and me is not None and not is_spectator
                and not is_impostor and not is_accomplice and not is_detective
            )
            asker, target = self.current_impostor_pair()
            speaker = asker
            vote_counts: dict[str, int] = {}
            for target_id in self.impostor_votes.values():
                vote_counts[target_id] = vote_counts.get(target_id, 0) + 1
            role_name = (
                "audience" if is_spectator else
                "impostor" if is_impostor else
                "accomplice" if is_accomplice else
                "detective" if is_detective else "crew"
            ) if role == "player" and me else ""
            impostor = {
                "category": self.impostor_category if can_see_word else "Categoría clasificada",
                "hint": self.impostor_hint if can_see_word else "",
                "word": self.impostor_word if can_see_word else "",
                "role": role_name,
                "speakerId": speaker.id if speaker else "",
                "speakerName": speaker.name if speaker else "",
                "askerId": speaker.id if speaker else "",
                "askerName": speaker.name if speaker else "",
                "targetId": target.id if target else "",
                "targetName": target.name if target else "",
                "speakerIndex": min(self.impostor_speaker_index + 1, len(self.impostor_speaker_ids)),
                "speakerCount": len(self.impostor_speaker_ids),
                "question": self.impostor_questions[
                    self.impostor_speaker_index % len(self.impostor_questions)
                ] if self.impostor_questions else "",
                "voted": bool(me and me.vote_round == round_no),
                "votedCount": sum(1 for p in connected if p.vote_round == round_no),
                "voterCount": len(connected),
                "votes": vote_counts if self.phase in ("guess", "reveal") else {},
                "impostorId": self.impostor_id if self.phase in ("guess", "reveal") else "",
                "impostorName": (
                    self.players[self.impostor_id].name
                    if self.impostor_id in self.players and self.phase in ("guess", "reveal")
                    else ""
                ),
                "accompliceId": self.impostor_accomplice_id if (reveal or is_accomplice) else "",
                "accompliceName": (
                    self.players[self.impostor_accomplice_id].name
                    if self.impostor_accomplice_id in self.players and (reveal or is_accomplice)
                    else ""
                ),
                "detectiveId": self.impostor_detective_id if reveal else "",
                "detectiveName": (
                    self.players[self.impostor_detective_id].name
                    if self.impostor_detective_id in self.players and reveal else ""
                ),
                "detectiveOptions": (
                    list(self.impostor_detective_options)
                    if is_detective and self.phase in ("secret", "questions", "coartada")
                    else []
                ),
                "coartadaSubmitted": bool(me and me.id in self.impostor_coartada),
                "coartadaCount": len(self.impostor_coartada),
                "coartadaValue": self.impostor_coartada.get(me.id, -1) if me else -1,
                "coartadaValues": [
                    {
                        "id": pid,
                        "name": self.players[pid].name,
                        "value": value,
                    }
                    for pid, value in self.impostor_coartada.items()
                    if pid in self.players
                ] if self.phase in ("vote", "guess", "reveal") else [],
                "caught": bool(self.impostor_caught and self.phase in ("guess", "reveal")),
                "guess": self.impostor_guess if reveal else "",
                "guessOptions": list(self.impostor_guess_options) if self.phase == "guess" else [],
                "guessCorrect": bool(self.impostor_guess_correct and reveal),
                "points": int(self.deltas.get(me.id, 0)) if (me and reveal) else 0,
            }
        elif self.game == "dibujo" and self.phase in ("draw", "title", "vote", "reveal"):
            me = self.players.get(player_id or "")
            reveal = self.phase == "reveal"
            is_artist = bool(me and me.id == self.drawing_artist_id)
            artist = self.players.get(self.drawing_artist_id)
            can_see_prompt = reveal or (role == "player" and is_artist)
            options = []
            vote_counts: dict[str, int] = {}
            for option_id in self.drawing_votes.values():
                vote_counts[option_id] = vote_counts.get(option_id, 0) + 1
            for option in self.drawing_options:
                author = self.players.get(option.get("authorId", ""))
                options.append({
                    "id": option["id"],
                    "text": option["text"],
                    "authorId": option.get("authorId", "") if reveal else "",
                    "authorName": (author.name if author else "") if reveal else "",
                    "votes": vote_counts.get(option["id"], 0) if reveal else 0,
                    "isReal": bool(reveal and option["id"] == "real"),
                    "isYours": bool(me and option.get("authorId") == me.id),
                    "house": bool(option.get("house")),
                })
            drawing = {
                "artistId": self.drawing_artist_id,
                "artistName": artist.name if artist else "",
                "prompt": self.drawing_prompt if can_see_prompt else "",
                "promptOptions": (
                    [item["prompt"] for item in self.drawing_prompt_options]
                    if self.rules_version >= 2 and role == "player" and is_artist
                    and self.phase == "draw" and not self.drawing_strokes
                    else []
                ),
                "strokes": (
                    self.drawing_strokes
                    if self.phase != "draw" or is_artist or role == "display"
                    else []
                ),
                "options": options if self.phase in ("vote", "reveal") else [],
                "submittedCount": sum(1 for p in connected if p.submitted_round == round_no),
                "titleCount": max(0, len(connected) - 1),
                "votedCount": sum(1 for p in connected if p.vote_round == round_no),
                "voterCount": max(0, len(connected) - 1),
                "youSubmitted": bool(me and me.submitted_round == round_no),
                "youVoted": bool(me and me.vote_round == round_no),
                "yourVote": (
                    self.drawing_votes.get(me.id, "")
                    or self.drawing_audience_votes.get(me.id, "")
                ) if me else "",
                "audienceVotes": len(self.drawing_audience_votes),
                "audienceChoice": self.drawing_audience_choice if reveal else "",
                "audienceBonusPlayerId": self.drawing_audience_bonus_id if reveal else "",
                "inkUsed": round(float(self.drawing_ink_used), 1),
                "inkLimit": float(self.drawing_ink_limit),
                "flashlight": bool(self.drawing_flashlight),
                "earlyGuessSubmitted": bool(me and me.id in self.drawing_early_guesses),
                "earlyGuess": self.drawing_early_guesses.get(me.id, "") if me else "",
                "earlyAttempts": int(self.drawing_early_attempts.get(me.id, 0)) if me else 0,
                "earlyWinners": list(self.drawing_early_winners.keys()) if reveal else [],
                "criticBomb": self.drawing_critic_bombs.get(me.id, "") if me else "",
                "criticCrown": self.drawing_critic_crowns.get(me.id, "") if me else "",
            }
        elif self.game == "subasta" and self.phase in ("auction", "auction_reveal"):
            me = self.players.get(player_id or "")
            reveal = self.phase == "auction_reveal"
            card = self.auction_item
            winner = self.players.get(self.auction_winner)
            bids_public = []
            if reveal:
                for pid, amount in sorted(self.auction_bids.items(), key=lambda item: -item[1]):
                    player = self.players.get(pid)
                    if player is not None and not player.spectator:
                        bids_public.append({"id": pid, "name": player.name, "bid": int(amount)})
            auction = {
                "itemId": card.get("id", ""),
                "title": card.get("title", ""),
                "description": card.get("description", ""),
                "mystery": bool(card.get("mystery")),
                "effect": card.get("effect", "") if (reveal or not card.get("mystery")) else "mystery",
                "value": int(card.get("value") or 0) if (reveal or not card.get("mystery")) else 0,
                "bidsCount": len(self.auction_bids),
                "bidCount": len(connected),
                "yourBid": int(me.bid) if (me and me.bid_round == round_no) else -1,
                "youBid": bool(me and me.bid_round == round_no),
                "coins": int(me.coins) if me else 0,
                "winnerId": self.auction_winner if reveal else "",
                "winnerName": winner.name if (reveal and winner) else "",
                "price": int(self.auction_price) if reveal else 0,
                "cancelled": list(self.auction_cancelled) if reveal else [],
                "tie": bool(self.auction_tie and reveal),
                "applied": self.auction_effect if reveal else "",
                "bids": bids_public,
                "secretEffect": self.auction_secret_effect if (
                    reveal or (me and me.id in self.auction_informants)
                ) else "",
                "secretValue": int(self.auction_secret_value) if (
                    reveal or (me and me.id in self.auction_informants)
                ) else 0,
                "informant": bool(me and me.id in self.auction_informants),
                "ghostBidderId": self.auction_ghost_bidder,
                "ghostBidderName": self.players[self.auction_ghost_bidder].name
                if self.auction_ghost_bidder in self.players else "",
                "ghostUsed": bool(me and me.ghost_used),
                "secondPays": bool(self.auction_second_pays),
            }
        elif self.game == "fibbage" and self.phase in ("write", "vote", "reveal"):
            me = self.players.get(player_id or "")
            reveal = self.phase == "reveal"
            options = []
            vote_counts: dict[str, int] = {}
            for option_id in self.fibbage_votes.values():
                vote_counts[option_id] = vote_counts.get(option_id, 0) + 1
            for option in self.fibbage_options:
                author = self.players.get(option.get("authorId", ""))
                options.append({
                    "id": option["id"],
                    "text": option["text"],
                    "authorId": option.get("authorId", "") if reveal else "",
                    "authorName": (author.name if author else "") if reveal else "",
                    "votes": vote_counts.get(option["id"], 0) if reveal else 0,
                    "isReal": bool(reveal and option["id"] == "real"),
                    "isYours": bool(me and option.get("authorId") == me.id),
                    "house": bool(option.get("house")),
                })
            fibbage = {
                "prompt": self.fibbage_prompt,
                "hint": self.fibbage_hint,
                "category": self.fibbage_category,
                "answer": self.fibbage_answer if reveal else "",
                "options": options if self.phase in ("vote", "reveal") else [],
                "submittedCount": sum(1 for p in connected if p.submitted_round == round_no),
                "voterCount": len(connected),
                "votedCount": sum(1 for p in connected if p.vote_round == round_no),
                "youSubmitted": bool(me and me.submitted_round == round_no),
                "youVoted": bool(me and me.vote_round == round_no),
                "yourVote": (
                    self.fibbage_votes.get(me.id, "")
                    or self.fibbage_audience_votes.get(me.id, "")
                ) if me else "",
                "yourLie": self.fibbage_lies.get(me.id, "") if me else "",
                "audienceVotes": len(self.fibbage_audience_votes),
                "audienceChoice": self.fibbage_audience_choice if reveal else "",
                "audienceBonusPlayerId": self.fibbage_audience_bonus_id if reveal else "",
                "scanUsed": bool(me and me.fibbage_scan_used),
                "scanned": list(self.fibbage_scans.get(me.id, [])) if me else [],
                "certain": bool(me and me.fibbage_certainty),
                "poisoned": bool(me and me.id in self.fibbage_poisoned),
                "finalShared": bool(self.fibbage_final_shared),
                "hunterGuess": me.fibbage_hunter_guess if me else "",
            }
        if self.game == "wager":
            answered_count = sum(1 for p in connected if p.wager_done_round == round_no)
        elif self.game == "charadas":
            answered_count = self.charada_correct + self.charada_passes
        elif self.game == "impostor":
            answered_count = sum(1 for p in connected if p.vote_round == round_no)
        elif self.game == "dibujo":
            answered_count = (
                sum(1 for p in connected if p.vote_round == round_no)
                if self.phase in ("vote", "reveal")
                else sum(1 for p in connected if p.submitted_round == round_no)
            )
        elif self.game == "subasta":
            answered_count = len(self.auction_bids)
        elif self.game == "fibbage":
            answered_count = (
                sum(1 for p in connected if p.vote_round == round_no)
                if self.phase in ("vote", "reveal")
                else sum(1 for p in connected if p.submitted_round == round_no)
            )
        elif self.game == "trivia" and self.rules_version >= 2:
            if self.trivia_round_kind == "sort":
                answered_count = sum(1 for p in connected if p.sort_round == round_no)
            elif self.trivia_round_kind == "assault":
                answered_count = sum(1 for p in connected if p.assault_round == round_no)
            else:
                answered_count = sum(1 for p in connected if round_no in p.answers)
        else:
            answered_count = sum(1 for p in connected if round_no in p.answers)
        out: dict[str, Any] = {
            "code": self.code,
            "displayCode": format_code(self.code),
            "mode": self.mode,
            "game": self.game,
            "rulesVersion": int(self.rules_version),
            "pack": self.pack,
            "phase": self.phase,
            "rev": self.rev,
            "serverNow": int(now * 1000),
            "phaseEndsAt": int(self.phase_ends_at() * 1000),
            "phaseDuration": int(self.phase_duration()),
            "round": min(self.round_index + 1, self.rounds),
            "rounds": self.rounds,
            "hostId": self.host_id,
            "players": self.players_payload(),
            "playersPace": self.players_pace,
            "answeredCount": answered_count,
            "readyCount": sum(1 for p in connected if p.ready_round >= round_no),
            "allReady": self._all_ready() if self.players_pace else True,
            "question": question,
            "answer": answer,
            "trivia": trivia,
            "charada": charada,
            "wager": wager,
            "impostor": impostor,
            "drawing": drawing,
            "auction": auction,
            "fibbage": fibbage,
            "audience": sum(1 for p in self.players.values() if p.spectator and p.connected),
            "reactions": list(self.reactions),
        }
        if self.phase == "podium":
            out["podium"] = self.players_payload()
            out["highlights"] = self.highlights_payload()
            if self.game == "subasta":
                out["auctionCashout"] = [
                    {"id": pid, "name": self.players[pid].name, "bonus": bonus}
                    for pid, bonus in self.auction_cashout.items()
                    if pid in self.players
                ]
        if role == "player":
            player = self.players.get(player_id or "")
            if player is not None:
                current_answer = player.answers.get(round_no)
                reveal = self.phase == "reveal"
                wager_answered = self.game == "wager" and player.wager_done_round == round_no
                new_game = self.game in ("impostor", "dibujo", "subasta", "fibbage")
                tournament_reveal = reveal or self.phase == "auction_reveal"
                if self.game == "impostor":
                    answered_now = player.vote_round == round_no or player.submitted_round == round_no
                elif self.game == "dibujo":
                    answered_now = player.submitted_round == round_no or player.vote_round == round_no
                elif self.game == "subasta":
                    answered_now = player.bid_round == round_no
                elif self.game == "fibbage":
                    answered_now = player.submitted_round == round_no or player.vote_round == round_no
                elif self.game == "trivia" and self.rules_version >= 2:
                    if self.trivia_round_kind == "sort":
                        answered_now = player.sort_round == round_no
                    elif self.trivia_round_kind == "assault":
                        answered_now = player.assault_round == round_no
                    else:
                        answered_now = current_answer is not None
                elif self.game == "wager" and self.rules_version >= 2:
                    answered_now = player.wager_stage_locked > 0
                else:
                    answered_now = current_answer is not None or wager_answered
                round_points = int(self.deltas.get(player.id, 0)) if tournament_reveal else 0
                out["you"] = {
                    "id": player.id,
                    "name": player.name,
                    "score": player.score,
                    "streak": player.streak,
                    "color": player.color,
                    "answered": answered_now,
                    "selected": current_answer.option if current_answer else (
                        player.wager_locked_option if (
                            self.game == "wager" and self.rules_version >= 2 and player.wager_stage_locked
                        ) else (player.wager_option if (self.game == "wager" and reveal) else -1)
                    ),
                    "points": current_answer.points if (reveal and current_answer) else (
                        round_points if (self.game == "wager" and self.rules_version >= 2 and reveal)
                        else (player.wager_delta if (self.game == "wager" and reveal) else round_points)
                    ),
                    "correct": bool(current_answer and current_answer.correct and reveal) or bool(
                        self.game == "wager" and self.rules_version < 2 and reveal and player.wager_correct
                    ) or bool(
                        self.game == "wager" and self.rules_version >= 2 and reveal
                        and player.wager_stage_locked
                        and player.wager_locked_option == int(self.current_wager_round().get("answer_index", -1))
                    ) or bool(self.game == "trivia" and self.rules_version >= 2
                              and tournament_reveal and round_points > 0)
                    or bool(new_game and tournament_reveal and round_points > 0),
                    "ready": player.ready_round >= round_no,
                    "actor": bool(self.game == "charadas" and player.id == self.actor_id),
                    "wager": player.wager if (self.game == "wager" and player.wager_round == round_no) else 0,
                    "wagerDone": wager_answered,
                    "clueAnswered": bool(self.game == "wager" and wager_answered and player.clue_answered >= 0),
                    "wagerDelta": player.wager_delta if (self.game == "wager" and reveal) else 0,
                    "wagerCorrect": bool(player.wager_correct and self.game == "wager" and reveal),
                    "spectator": bool(player.spectator),
                    "coins": int(player.coins),
                    "role": (
                        "audience" if player.spectator
                        else "impostor" if self.game == "impostor" and player.id == self.impostor_id
                        else ("crew" if self.game == "impostor" else "")
                    ),
                    "submitted": player.submitted_round == round_no,
                    "voted": player.vote_round == round_no,
                    "bid": int(player.bid) if player.bid_round == round_no else -1,
                    "artist": bool(self.game == "dibujo" and player.id == self.drawing_artist_id),
                }
                if self.game == "wager" and self.rules_version >= 2 and isinstance(out.get("wager"), dict):
                    data = self.current_wager_round()
                    answer_index = int(data.get("answer_index", -1))
                    out["wager"]["you"] = {
                        "locked": player.wager_stage_locked > 0,
                        "lockedStage": player.wager_stage_locked,
                        "lockedOption": player.wager_locked_option,
                        "canBuy": bool(
                            self.phase == "clue"
                            and not player.wager_stage_locked
                            and player.wager_private_stage < 3
                            and player.score >= WAGER_BUY_COST
                        ),
                        "canSideBet": bool(
                            self.phase == "clue"
                            and not player.wager_stage_locked
                            and not player.wager_side_target
                        ),
                        "sideBetTargetId": player.wager_side_target,
                        "sideBetAmount": player.wager_side_amount,
                        "factor": WAGER_V2_FACTORS[max(0, min(2, player.wager_stage_locked - 1))] if player.wager_stage_locked else 0,
                        "privateStage": max(1, min(3, int(player.wager_private_stage))),
                        "privateClue": self._wager_clue_for_stage(player.wager_private_stage),
                        "privatePosterReveal": (0.06, 0.5, 1.0)[max(0, min(2, player.wager_private_stage - 1))],
                        "correct": bool(
                            self.phase == "reveal"
                            and player.wager_stage_locked
                            and player.wager_locked_option == answer_index
                        ),
                    }
                if self.game == "trivia" and self.rules_version >= 2 and isinstance(out.get("trivia"), dict):
                    out["trivia"]["you"]["answered"] = bool(out["you"].get("answered"))
                    out["trivia"]["you"]["correct"] = bool(out["you"].get("correct"))
                    out["trivia"]["you"]["points"] = int(out["you"].get("points") or 0)
        return out

    def players_payload(self) -> list[dict]:
        ordered = sorted(
            [p for p in self.players.values() if not p.spectator],
            key=lambda p: (-p.score, p.joined_at),
        )
        return [
            {
                "id": p.id,
                "name": p.name,
                "color": p.color,
                "score": p.score,
                "connected": p.connected,
                "isHost": p.id == self.host_id,
                "ready": p.ready_round >= self.round_index + 1,
                "wager": p.wager if p.wager_round == self.round_index + 1 else 0,
                "wagerDone": p.wager_done_round == self.round_index + 1,
                "coins": int(p.coins),
            }
            for p in ordered
        ]

    def result(self) -> dict:
        return {
            "game": self.game,
            "mode": self.mode,
            "pack": self.pack,
            "rounds": self.rounds,
            "players": [
                {"id": p.id, "name": p.name, "score": p.score, "color": p.color}
                for p in sorted(
                    [p for p in self.players.values() if not p.spectator],
                    key=lambda p: (-p.score, p.joined_at),
                )
            ],
            "highlights": self.highlights_payload(),
            "auctionCashout": [
                {"id": pid, "name": self.players[pid].name, "bonus": bonus}
                for pid, bonus in self.auction_cashout.items()
                if pid in self.players
            ],
            "startedAt": int(self.started_at * 1000),
            "endedAt": int(self.ended_at * 1000),
        }


# --- Construcción -------------------------------------------------------------

def _clean_options(options: Any) -> list[str]:
    if not isinstance(options, list):
        return []
    out: list[str] = []
    seen: set[str] = set()
    for raw in options:
        text = _SPACES.sub(" ", _CONTROL.sub("", str(raw or ""))).strip()
        if not text or len(text) > 120:
            continue
        key = fold(text)
        if key in seen:
            continue
        seen.add(key)
        out.append(text)
    return out


def prepare_questions(raw_questions: list[dict], code: str) -> list[dict]:
    """Valida y baraja las opciones de cada pregunta para ESTA sala."""
    rng = random.Random(f"play:{normalize_code(code) or code}")
    prepared: list[dict] = []
    for raw in raw_questions or []:
        if not isinstance(raw, dict):
            continue
        payload = raw.get("payload")
        if isinstance(payload, str):
            import json

            try:
                payload = json.loads(payload)
            except (TypeError, ValueError):
                payload = None
        if not isinstance(payload, dict):
            continue
        prompt = _SPACES.sub(" ", str(payload.get("prompt") or "")).strip()
        options = _clean_options(payload.get("options"))
        answer = _SPACES.sub(" ", str(raw.get("answer_key") or "")).strip()
        answer_key = fold(answer)
        index = next((i for i, option in enumerate(options) if fold(option) == answer_key), -1)
        if not prompt or len(options) < 2 or index < 0:
            continue
        rng.shuffle(options)
        answer_index = next(i for i, option in enumerate(options) if fold(option) == answer_key)
        prepared.append({
            "id": str(raw.get("id") or ""),
            "kind": str(raw.get("kind") or ""),
            "prompt": prompt,
            "options": options,
            "art": str(payload.get("art") or ""),
            "answer": options[answer_index],
            "answer_index": answer_index,
            "answer_key": answer,
            "difficulty": int(raw.get("difficulty") or 1),
            "pack": str(raw.get("pack") or "general"),
        })
        if len(prepared) >= MAX_ROUNDS:
            break
    return prepared


def _clean_game_text(value: Any, limit: int = 300) -> str:
    return _SPACES.sub(" ", _CONTROL.sub("", str(value or ""))).strip()[:limit]


def _too_close_title(text: str, real: str) -> bool:
    """Evita que un título falso sea prácticamente el título real."""
    left = fold(text)
    right = fold(real)
    if not left or not right:
        return False
    if len(left) > 4 and (left in right or right in left):
        return True
    return difflib.SequenceMatcher(None, left, right).ratio() >= 0.82


def _ink_cost(strokes: list[dict]) -> float:
    total = 0.0
    for stroke in strokes or []:
        points = stroke.get("points") or []
        width = max(1.0, float(stroke.get("width") or 5.0))
        for index in range(1, len(points)):
            try:
                x1, y1 = float(points[index - 1][0]), float(points[index - 1][1])
                x2, y2 = float(points[index][0]), float(points[index][1])
            except (TypeError, ValueError, IndexError):
                continue
            total += (((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5) * (width / 10.0) / 20.0
    return round(total, 1)


def _clean_strokes(raw: Any) -> list[dict]:
    """Lienzo vectorial: coordenadas 0..1000, trazos y puntos acotados.

    Guardar vectores (y no un PNG) mantiene la TV liviana y permite que el
    dibujo se reescale a cualquier pantalla sin perder nitidez.
    """
    if not isinstance(raw, list):
        return []
    strokes: list[dict] = []
    total_points = 0
    for item in raw[:MAX_STROKES]:
        if not isinstance(item, dict):
            continue
        points_raw = item.get("points")
        if not isinstance(points_raw, list):
            continue
        points: list[list[float]] = []
        for point in points_raw:
            if total_points >= MAX_STROKE_POINTS:
                break
            if not isinstance(point, (list, tuple)) or len(point) < 2:
                continue
            try:
                x = max(0.0, min(1000.0, float(point[0])))
                y = max(0.0, min(1000.0, float(point[1])))
            except (TypeError, ValueError):
                continue
            points.append([round(x, 1), round(y, 1)])
            total_points += 1
        if len(points) < 2:
            continue
        color = _clean_game_text(item.get("color"), 16) or "#FFFFFF"
        if not re.fullmatch(r"#[0-9A-Fa-f]{6}", color):
            color = "#FFFFFF"
        try:
            width = max(1.0, min(24.0, float(item.get("width") or 5)))
        except (TypeError, ValueError):
            width = 5.0
        strokes.append({"color": color, "width": round(width, 1), "points": points})
        if total_points >= MAX_STROKE_POINTS:
            break
    return strokes


def _redact_title(text: str, title: str) -> str:
    """Quita el título de una pista para que la sinopsis no regale la respuesta."""
    clean = _clean_game_text(text, 360)
    name = str(title or "").strip()
    if not name:
        return clean
    try:
        clean = re.sub(re.escape(name), "esta historia", clean, flags=re.IGNORECASE)
    except re.error:
        pass
    return _SPACES.sub(" ", clean).strip(" .,-")


def build_charada_deck(candidates: list[dict], rounds: int, seed: str = "") -> list[dict]:
    """Mazo de Charadas: títulos reconocibles, sin repetir, listos para el teléfono del actor."""
    rng = random.Random(f"charadas:{seed}")
    pool: list[dict] = []
    seen: set[str] = set()
    for raw in candidates or []:
        if not isinstance(raw, dict):
            continue
        title = _clean_game_text(raw.get("title"), 90)
        key = fold(title)
        if not title or key in seen:
            continue
        poster = _clean_game_text(raw.get("poster_url") or raw.get("poster"), 500)
        if not poster:
            continue
        try:
            year = int(raw.get("year") or 0)
        except (TypeError, ValueError):
            year = 0
        seen.add(key)
        genres = raw.get("genres")
        if isinstance(genres, str):
            genre_list = [g.strip() for g in genres.split(",") if g.strip()]
        elif isinstance(genres, (list, tuple)):
            genre_list = [str(g).strip() for g in genres if str(g).strip()]
        else:
            genre_list = []
        pool.append({
            "id": _clean_game_text(raw.get("id"), 80) or f"charada:{key}",
            "title": title,
            "poster": poster,
            "year": year,
            "kind": _clean_game_text(raw.get("kind"), 16) or "movie",
            "genres": genre_list,
        })
    rng.shuffle(pool)
    need = max(1, int(rounds or 1)) * (CHARADA_MAX_PASSES + 1)
    return pool[:need]


def _wager_options(card: dict, pool: list[dict], rng: random.Random) -> list[str]:
    correct = card["title"]
    same_kind = [c["title"] for c in pool if c["title"] != correct and c.get("kind") == card.get("kind")]
    others = [c["title"] for c in pool if c["title"] != correct]
    rng.shuffle(same_kind)
    rng.shuffle(others)
    picks: list[str] = []
    for source in (same_kind, others):
        for title in source:
            if title not in picks and title != correct:
                picks.append(title)
            if len(picks) >= 3:
                break
        if len(picks) >= 3:
            break
    if len(picks) < 3:
        return []
    options = [correct] + picks[:3]
    rng.shuffle(options)
    return options


def build_wager_rounds(candidates: list[dict], rounds: int, seed: str = "") -> list[dict]:
    """Rondas de Apuesta a la pista: título, cuatro opciones y tres pistas progresivas."""
    rng = random.Random(f"wager:{seed}")
    pool: list[dict] = []
    seen: set[str] = set()
    for raw in candidates or []:
        if not isinstance(raw, dict):
            continue
        title = _clean_game_text(raw.get("title"), 90)
        key = fold(title)
        synopsis = _clean_game_text(raw.get("synopsis"), 360)
        poster = _clean_game_text(raw.get("poster_url") or raw.get("poster"), 500)
        if not title or key in seen or len(synopsis) < 40 or not poster:
            continue
        try:
            year = int(raw.get("year") or 0)
        except (TypeError, ValueError):
            year = 0
        genres = raw.get("genres")
        if isinstance(genres, str):
            genre_list = [g.strip() for g in genres.split(",") if g.strip()]
        elif isinstance(genres, (list, tuple)):
            genre_list = [str(g).strip() for g in genres if str(g).strip()]
        else:
            genre_list = []
        seen.add(key)
        pool.append({
            "id": _clean_game_text(raw.get("id"), 80) or f"wager:{key}",
            "title": title,
            "synopsis": synopsis,
            "poster": poster,
            "year": year,
            "kind": _clean_game_text(raw.get("kind"), 16) or "movie",
            "cast": _clean_game_text(raw.get("cast_list") or raw.get("cast"), 180),
            "director": _clean_game_text(raw.get("director"), 120),
            "genres": genre_list,
        })
    rng.shuffle(pool)
    total = max(1, min(int(rounds or 1), MAX_ROUNDS, len(pool)))
    out: list[dict] = []
    for card in pool[:total]:
        options = _wager_options(card, pool, rng)
        if not options:
            continue
        answer_index = options.index(card["title"])
        synopsis = _redact_title(card["synopsis"], card["title"])
        if len(synopsis) < 30:
            genre = ", ".join(card["genres"][:2]) or "Vision+"
            synopsis = f"Una historia de {genre}" + (f" de {card['year']}." if card["year"] else ".")
        cast = card["cast"] or card["director"]
        if cast:
            second = f"Reparto: {cast}."
        elif card["genres"] or card["year"]:
            bits = []
            if card["genres"]:
                bits.append("Géneros: " + ", ".join(card["genres"][:3]))
            if card["year"]:
                bits.append(f"Año: {card['year']}")
            second = " · ".join(bits) + "."
        else:
            second = "Una producción que ya está en tu catálogo."
        clues = [
            {"label": "Sinopsis", "text": synopsis, "art": ""},
            {"label": "Reparto", "text": second, "art": ""},
            {"label": "Póster", "text": "El póster lo dice todo. ¿Cuál es el título?", "art": card["poster"]},
        ]
        out.append({
            "id": card["id"],
            "title": card["title"],
            "answer": card["title"],
            "options": options,
            "answer_index": answer_index,
            "poster": card["poster"],
            "year": card["year"],
            "kind": card["kind"],
            "genres": list(card.get("genres") or []),
            "difficulty": 2,
            "clues": clues,
        })
    return out


def create_room(
    code: str,
    host_name: str,
    host_token: str,
    player_token: str,
    questions: list[dict],
    *,
    game: str = "trivia",
    deck: list[dict] | None = None,
    mode: str = "remote",
    pack: str = "general",
    rounds: int = DEFAULT_ROUNDS,
    question_seconds: int = QUESTION_SECONDS,
    reveal_seconds: int = REVEAL_SECONDS,
    countdown_seconds: int = COUNTDOWN_SECONDS,
    charada_seconds: int = CHARADA_SECONDS,
    wager_seconds: int = WAGER_SECONDS,
    clue_seconds: int = CLUE_SECONDS,
    fair: bool = False,
    max_players: int = MAX_PLAYERS,
    players_pace: bool | None = None,
    rules_version: int = 1,
    family_mode: bool = True,
    solo_test: bool = False,
    now: float = 0.0,
) -> Room:
    clean_code = normalize_code(code)
    if not clean_code:
        raise ValueError("código inválido")
    if mode not in MODES:
        raise ValueError("modo inválido")
    game = str(game or "trivia").strip().lower()
    if game not in GAMES:
        raise ValueError("juego inválido")
    pace = (mode == "display") if players_pace is None else bool(players_pace)
    prepared = prepare_questions(questions, clean_code) if game == "trivia" else []
    wager_deck = list(deck or []) if game == "wager" else []
    charada_deck = list(deck or []) if game == "charadas" else []
    party_deck = list(deck or []) if game in ("impostor", "dibujo", "subasta", "fibbage") else []
    fiesta_game = game in fiesta_engine.FIESTA_GAMES
    if game == "trivia" and not prepared:
        raise ValueError("sin preguntas")
    if game == "wager" and not wager_deck:
        raise ValueError("sin rondas")
    if game == "charadas" and not charada_deck:
        raise ValueError("sin cartas")
    if game in ("impostor", "dibujo", "subasta", "fibbage") and not party_deck:
        raise ValueError("sin cartas")
    default_rounds = game_default_rounds(game)
    if fiesta_game:
        available = max(1, int(rounds or default_rounds))
    elif game == "trivia":
        available = len(prepared)
    elif game == "charadas":
        available = max(1, len(charada_deck) // (CHARADA_MAX_PASSES + 1))
    elif game == "wager":
        available = len(wager_deck)
    else:
        available = len(party_deck)
    total_rounds = max(1, min(int(rounds or default_rounds), MAX_ROUNDS, available))
    room = Room(
        code=clean_code,
        mode=mode,
        pack=str(pack or game)[:32],
        rounds=total_rounds,
        question_seconds=max(MIN_QUESTION_SECONDS, min(MAX_QUESTION_SECONDS, int(question_seconds))),
        reveal_seconds=max(1, min(30, int(reveal_seconds))),
        countdown_seconds=max(0, min(10, int(countdown_seconds))),
        fair=bool(fair),
        max_players=max(1, min(HARD_MAX_PLAYERS, int(max_players or MAX_PLAYERS))),
        questions=prepared,
        host_token_hash=token_hash(host_token),
        host_id="",
        game=game,
        rules_version=3 if fiesta_game else (2 if int(rules_version or 1) >= 2 else 1),
        deck=charada_deck or wager_deck or party_deck,
        family_mode=bool(family_mode),
        players_pace=pace,
        charada_seconds=max(15, min(120, int(charada_seconds or CHARADA_SECONDS))),
        wager_seconds=max(5, min(30, int(wager_seconds or WAGER_SECONDS))),
        clue_seconds=max(5, min(60, int(clue_seconds or CLUE_SECONDS))),
        created_at=now,
        last_activity=now,
    )
    if mode != "display":
        host = room.add_player(host_name, player_token, now)
        if host is None:
            raise ValueError("apodo inválido")
        room.host_id = host.id
        room.rev = 2  # el alta del host cuenta como primer cambio
    if fiesta_game:
        room.fiesta = fiesta_engine.FiestaRoom(room, game, total_rounds, solo_test=solo_test)
    return room
