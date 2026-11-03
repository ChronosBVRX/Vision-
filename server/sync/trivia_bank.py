"""Banco de preguntas de Vision+ Play — se genera solo del catálogo existente.

No hay editor manual. El worker elige títulos con señal real (popularidad o
votos), construye preguntas con distractores disjuntos y guarda un `hash`
estable para no duplicarlas. La respuesta correcta vive en `answer_key`, fuera
del `payload`, para que el snapshot de la sala no pueda filtrarla.

Tipos (plan §3.1): año, género, reparto, dirección, sinopsis, póster, saga,
temporadas y traducción. Cualquier pregunta que no se pueda garantizar se
descarta: es preferible un banco más corto que un distractor que también sea
correcto.

Uso local sin base (genera un JSON para revisarlo):

    python server/sync/trivia_bank.py --from-json catalogo.json --out banco.json --limit 200

Uso en el worker (lo llama `main.py`):

    await trivia_bank.run(pool)   # respeta TRIVIA_MIN_HOURS y TRIVIA_BANK_CAP
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import logging
import os
import random
import re
import unicodedata
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path

import asyncpg

import genres as catalog_genres

log = logging.getLogger("trivia")

MAX_OPTIONS = 4
MAX_PROMPT = 320
MAX_ROWS = int(os.environ.get("TRIVIA_SOURCE_ROWS", "600"))
MAX_PER_CYCLE = int(os.environ.get("TRIVIA_MAX_PER_CYCLE", "400"))
BANK_CAP = int(os.environ.get("TRIVIA_BANK_CAP", "8000"))
MIN_HOURS = float(os.environ.get("TRIVIA_MIN_HOURS", "6"))
DEFAULT_PACK = "general"

_CONTROL = re.compile(r"[\x00-\x1f\x7f\u200b-\u200f\u202a-\u202e]")
_SPACES = re.compile(r"\s+")
_SEASON = re.compile(
    r"\s*(?:[-–—:]\s*)?(?:t|temp\.?|temporada|season)\s*(\d{1,2})\s*$",
    re.IGNORECASE,
)
_SEASON_ANY = re.compile(r"(?:t|temp\.?|temporada|season)\s*(\d{1,2})", re.IGNORECASE)

_FETCH_SQL = """
with candidates as (
    select t.id, t.title, t.year, t.kind, t.poster_url, t.popularity, t.vote_count,
           e.tmdb_id, e.overview, e.genres, e.cast_names, e.director_names,
           e.localized_title, e.original_title, e.translated_titles, e.match_confidence,
           g.id as group_id, g.base_title as group_title,
           (select count(*) from group_members gm2 where gm2.group_id = g.id) as season_count
    from titles t
    join enrichment e on e.title_id = t.id
    left join group_members gm on gm.title_id = t.id
    left join title_groups g on g.id = gm.group_id
    where t.source = 'xtv'
      and t.play_ref like 'xtv:%'
      and (t.popularity > 0 or t.vote_count >= 20)
      and e.match_confidence >= 0.5
      and e.overview <> ''
      and t.poster_url <> ''
), popular as (
    select * from candidates order by popularity desc nulls last limit $2
), sample as (
    select * from candidates order by random() limit $2
)
select * from popular
union all
select * from sample
limit $1
"""

_INSERT_SQL = """
insert into trivia_questions
    (id, kind, payload, answer_key, difficulty, pack, source_ref, lang, hash)
values ($1, $2, $3::jsonb, $4, $5, $6, $7, 'es', $8)
on conflict (hash) do nothing
"""

_PRUNE_SQL = """
delete from trivia_questions q
where q.id in (
    select q2.id
    from trivia_questions q2
    where not exists (
        select 1 from trivia_served s where s.question_id = q2.id
    )
    order by q2.created_at asc
    limit $1
)
"""


# --- Texto --------------------------------------------------------------------

def fold(text) -> str:
    decomposed = unicodedata.normalize("NFD", str(text or "").lower())
    plain = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    spaced = "".join(ch if ch.isalnum() else " " for ch in plain)
    return " ".join(spaced.split())


def compact(text) -> str:
    return fold(text).replace(" ", "")


def clean_text(value, limit: int = 200) -> str:
    text = _CONTROL.sub("", str(value or ""))
    text = _SPACES.sub(" ", text).strip()
    return text[:limit]


def clean_title(value) -> str:
    title = clean_text(value, 120)
    return _SEASON.sub("", title).strip() or title


def as_list(value) -> list[str]:
    """Acepta listas de Postgres, JSON arrays y texto separado por comas."""
    if value is None:
        return []
    if isinstance(value, (list, tuple, set)):
        return [clean_text(item, 120) for item in value if clean_text(item, 120)]
    text = str(value).strip()
    if not text:
        return []
    if text.startswith("["):
        try:
            parsed = json.loads(text)
            if isinstance(parsed, list):
                return [clean_text(item, 120) for item in parsed if clean_text(item, 120)]
        except (TypeError, ValueError):
            pass
    return [clean_text(part, 120) for part in re.split(r"[,;|]", text) if clean_text(part, 120)]


def parse_names(value) -> list[str]:
    names = as_list(value)
    out: list[str] = []
    seen: set[str] = set()
    for name in names:
        key = fold(name)
        if len(key) < 3 or key in seen:
            continue
        seen.add(key)
        out.append(name)
    return out[:12]


def contains_title(text: str, names) -> bool:
    """¿El texto contiene alguno de esos nombres (plegado o compacto)?"""
    folded = fold(text)
    packed = compact(text)
    for name in names or ():
        key = fold(name)
        if len(key) < 3:
            continue
        if key in folded or compact(name) in packed:
            return True
    return False


def parse_season(title: str) -> int:
    match = _SEASON_ANY.search(str(title or ""))
    return int(match.group(1)) if match else 0


# --- Tarjetas (una por obra, no por temporada) --------------------------------

@dataclass
class Card:
    key: str
    title: str
    kind: str
    year: int
    overview: str
    poster: str
    genres: list[str] = field(default_factory=list)
    genre_order: list[str] = field(default_factory=list)
    cast: list[str] = field(default_factory=list)
    directors: list[str] = field(default_factory=list)
    translations: list[str] = field(default_factory=list)
    group_id: str = ""
    season_count: int = 0
    popularity: float = 0.0
    votes: int = 0
    tmdb_id: int = 0
    sample_member: str = ""
    names: set[str] = field(default_factory=set)

    @property
    def is_group(self) -> bool:
        return bool(self.group_id)


def _as_int(value, default: int = 0) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return default


def _as_float(value) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0.0


def _unique(items) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for item in items:
        key = fold(item)
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(item)
    return out


def _names_for(card: Card, row_titles: list[str]) -> set[str]:
    names = {card.title, card.sample_member, *row_titles}
    for name in list(names):
        names.add(clean_title(name))
    return {key for key in (fold(name) for name in names) if key}


def build_cards(rows: list[dict]) -> list[Card]:
    """Colapsa T1..Tn de una serie en UNA tarjeta (la obra, no la temporada)."""
    buckets: dict[str, list[dict]] = {}
    for row in rows or []:
        if not isinstance(row, dict):
            continue
        group_id = clean_text(row.get("group_id"), 80)
        key = f"g:{group_id}" if group_id else f"t:{clean_text(row.get('id'), 120)}"
        buckets.setdefault(key, []).append(row)

    cards: list[Card] = []
    for key, group in buckets.items():
        first = group[0]
        group_id = clean_text(first.get("group_id"), 80)
        title = clean_title(first.get("group_title") or first.get("title"))
        kind = clean_text(first.get("kind"), 16) or "movie"
        years = [_as_int(row.get("year")) for row in group if _as_int(row.get("year")) > 1800]
        overviews = [clean_text(row.get("overview"), 1200) for row in group if clean_text(row.get("overview"), 1200)]
        posters = [clean_text(row.get("poster_url"), 400) for row in group if clean_text(row.get("poster_url"), 400)]
        genre_lists = [as_list(row.get("genres")) for row in group]
        cast: list[str] = []
        directors: list[str] = []
        translations: list[str] = []
        for row in group:
            cast.extend(parse_names(row.get("cast_names")))
            directors.extend(parse_names(row.get("director_names")))
            translations.extend(as_list(row.get("translated_titles")))
        canonical = catalog_genres.canonical_genres(*genre_lists)
        # Para preguntar «género principal» manda el orden de TMDB, no la
        # prioridad de reparto de filas de genres.py (que pone Ciencia ficción
        # antes que Comedia y convertiría una comedia en una respuesta falsa).
        genre_order: list[str] = []
        for row in group:
            for raw_genre in as_list(row.get("genres")):
                mapped = catalog_genres.canonical_genres([raw_genre])
                if mapped and mapped[0] not in genre_order:
                    genre_order.append(mapped[0])
        names = {title, clean_title(first.get("title"))}
        names.update(clean_title(row.get("title")) for row in group)
        names.update(clean_text(first.get("localized_title"), 120) for _ in (0,))
        names.update(clean_text(first.get("original_title"), 120) for _ in (0,))
        name_set = {value for value in (fold(name) for name in names) if len(value) >= 3}
        translations = [
            value for value in _unique(translations)
            if 2 <= len(value) <= 80 and fold(value) not in name_set
        ][:8]
        card = Card(
            key=key,
            title=title,
            kind=kind,
            year=min(years) if years else 0,
            overview=max(overviews, key=len) if overviews else "",
            poster=posters[0] if posters else "",
            genres=canonical,
            genre_order=genre_order,
            cast=_unique(cast)[:8],
            directors=_unique(directors)[:6],
            translations=translations,
            group_id=group_id,
            season_count=max([_as_int(row.get("season_count")) for row in group] or [0]),
            popularity=max(_as_float(row.get("popularity")) for row in group),
            votes=max(_as_int(row.get("vote_count")) for row in group),
            tmdb_id=next((_as_int(row.get("tmdb_id")) for row in group if _as_int(row.get("tmdb_id"))), 0),
            sample_member=clean_text(first.get("title"), 120) or title,
        )
        card.names = name_set | _names_for(card, [clean_text(row.get("title"), 120) for row in group])
        if card.season_count <= 1 and group_id:
            card.season_count = len(group)
        cards.append(card)
    return cards


# --- Opciones y preguntas -----------------------------------------------------

def _pick(items, count: int, rng: random.Random) -> list[str]:
    unique = _unique(items)
    rng.shuffle(unique)
    return unique[:count]


def _sample(items, limit: int, rng: random.Random) -> list[str]:
    """Muestra acotada: evita plegar miles de nombres por cada pregunta."""
    values = list(items)
    if len(values) <= limit:
        return values
    return rng.sample(values, limit)


def year_options(year: int, rng: random.Random, pool_years) -> list[str]:
    if year < 1900:
        return []
    current = datetime.now(timezone.utc).year
    choices: list[int] = []
    for delta in range(2, 9):
        for candidate in (year - delta, year + delta):
            if 1900 <= candidate <= current + 2 and candidate != year:
                choices.append(candidate)
    rng.shuffle(choices)
    for candidate in rng.sample(list(pool_years), k=min(len(pool_years), 40)):
        if len(choices) >= 3:
            break
        if candidate != year and 1900 <= candidate <= current + 2:
            choices.append(candidate)
    picked: list[int] = []
    for candidate in choices:
        if candidate != year and candidate not in picked:
            picked.append(candidate)
        if len(picked) >= 3:
            break
    if len(picked) < 3:
        return []
    return [str(value) for value in sorted([year, *picked])]


def primary_answer_genre(card: Card) -> str:
    """El género principal REAL: el primero de TMDB (orden de la ficha)."""
    return card.genre_order[0] if card.genre_order else (
        catalog_genres.primary_genre(card.genres) or (card.genres[0] if card.genres else "")
    )


def genre_options(card: Card, rng: random.Random) -> list[str]:
    answer = primary_answer_genre(card)
    if not answer:
        return []
    pool = [genre for genre in catalog_genres.CANONICAL if genre not in card.genres]
    distractors = _pick(pool, 3, rng)
    if len(distractors) < 3:
        return []
    return [answer, *distractors]


def _script(text: str) -> str:
    """Latin, CJK u otro: los distractores deben poder leerse junto a la respuesta."""
    for char in str(text or ""):
        code = ord(char)
        if 0x3040 <= code <= 0x30FF or 0x3400 <= code <= 0x9FFF or 0xAC00 <= code <= 0xD7AF:
            return "cjk"
        if 0x0400 <= code <= 0x04FF:
            return "cyrillic"
        if 0x0590 <= code <= 0x05FF:
            return "hebrew"
        if 0x0600 <= code <= 0x06FF:
            return "arabic"
        if 0x0370 <= code <= 0x03FF:
            return "greek"
        if 0x0900 <= code <= 0x097F:
            return "devanagari"
    return "latin"


def _people_options(answer: str, card: Card, pool, rng: random.Random) -> list[str]:
    if not answer:
        return []
    blocked = {fold(name) for name in (card.cast + card.directors)}
    answer_script = _script(answer)
    candidates = [
        name for name, folded, script, owner in pool
        if folded not in blocked
        and owner.key != card.key
        and (not card.tmdb_id or owner.tmdb_id != card.tmdb_id)
        and (not card.group_id or owner.group_id != card.group_id)
    ]
    candidate_set = set(candidates)
    same_kind = [name for name, folded, script, owner in pool
                 if owner.kind == card.kind and name in candidate_set]
    same_script = [
        name for name, folded, script, owner in pool
        if name in candidate_set and script == answer_script
    ]
    script_set = set(same_script)
    preferred = [name for name in same_kind if name in script_set]
    for pool_names in (preferred, same_script, same_kind, candidates):
        if len(pool_names) >= 3:
            distractors = _pick(_sample(pool_names, 60, rng), 3, rng)
            return [answer, *distractors]
    return []


def _title_options(card: Card, cards: list[Card], rng: random.Random, prompt: str = "",
                   groups_only: bool = False) -> list[str]:
    candidates: list[Card] = []
    folded_prompt = fold(prompt)
    packed_prompt = compact(prompt)
    for other in cards:
        if other.key == card.key:
            continue
        if groups_only and not other.group_id:
            continue
        if card.tmdb_id and other.tmdb_id == card.tmdb_id:
            continue
        if card.group_id and other.group_id == card.group_id:
            continue
        if folded_prompt and any(
            name in folded_prompt or name.replace(" ", "") in packed_prompt
            for name in other.names if len(name) >= 3
        ):
            continue
        candidates.append(other)
    same_kind = [other for other in candidates if other.kind == card.kind]
    pool = same_kind if len(same_kind) >= 3 else candidates
    titles = _pick(_sample([other.title for other in pool], 60, rng), 3, rng)
    if len(titles) < 3:
        return []
    return [card.title, *titles]


def _number_options(answer: int, rng: random.Random) -> list[str]:
    if answer < 1:
        return []
    values = {answer}
    for delta in (1, -1, 2, -2, 3, -3):
        candidate = answer + delta
        if candidate >= 1:
            values.add(candidate)
        if len(values) >= 4:
            break
    while len(values) < 4:
        values.add(rng.randint(1, max(4, answer + 5)))
    picked = sorted(values)[:4]
    if answer not in picked:
        picked[-1] = answer
    return [str(value) for value in sorted(set(picked))]


def difficulty_for(popularity, votes) -> int:
    pop = _as_float(popularity)
    count = _as_int(votes)
    if pop >= 50 or count >= 2000:
        return 1
    if pop >= 5 or count >= 200:
        return 2
    return 3


def question_hash(kind: str, source_ref: str, prompt: str, answer: str) -> str:
    raw = f"{kind}|{source_ref}|{prompt}|{answer}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:32]


def pack_for_card(card: Card) -> str:
    """Sección de trivia a la que pertenece una tarjeta (una obra, no una temporada)."""
    kind = (card.kind or "").lower()
    if kind == "anime":
        return "anime"
    if kind == "kids":
        return "infantil"
    if kind == "series":
        genres = {fold(genre) for genre in card.genres}
        if "telenovela" in genres or "novela" in genres:
            return "novelas"
        return "series"
    if kind == "movie":
        return "cine"
    return "general"


def _make_question(kind: str, prompt: str, options: list[str], answer: str,
                   card: Card, rng: random.Random, art: str = "") -> dict | None:
    prompt = clean_text(prompt, MAX_PROMPT)
    options = _unique([clean_text(option, 120) for option in options])
    answer = clean_text(answer, 120)
    if not prompt or answer not in options or len(options) != MAX_OPTIONS:
        return None
    if len({fold(option) for option in options}) != MAX_OPTIONS:
        return None
    if kind == "synopsis" and contains_title(prompt, [answer]):
        return None
    rng.shuffle(options)
    return {
        "id": uuid.uuid4().hex,
        "kind": kind,
        "payload": {"prompt": prompt, "options": options, "art": clean_text(art, 400)},
        "answer_key": answer,
        "difficulty": difficulty_for(card.popularity, card.votes),
        "pack": pack_for_card(card),
        "source_ref": card.key,
        "hash": question_hash(kind, card.key, prompt, answer),
    }


def generate_for_card(card: Card, cards: list[Card], rng: random.Random,
                      actors, directors, years) -> list[dict]:
    out: list[dict] = []
    art = card.poster
    display = card.title

    if card.year >= 1900:
        options = year_options(card.year, rng, years)
        if options:
            question = _make_question(
                "year", f"¿En qué año se estrenó «{display}»?", options, str(card.year), card, rng, art
            )
            if question:
                out.append(question)

    options = genre_options(card, rng)
    if options:
        answer = primary_answer_genre(card)
        question = _make_question(
            "genre", f"¿Cuál es el género principal de «{display}»?", options, answer, card, rng, art
        )
        if question:
            out.append(question)

    if card.cast:
        options = _people_options(card.cast[0], card, actors, rng)
        if options:
            question = _make_question(
                "cast", f"¿Quién encabeza el reparto de «{display}»?", options, card.cast[0], card, rng, art
            )
            if question:
                out.append(question)

    if card.directors:
        options = _people_options(card.directors[0], card, directors, rng)
        if options:
            question = _make_question(
                "director", f"¿Quién dirigió «{display}»?", options, card.directors[0], card, rng, art
            )
            if question:
                out.append(question)

    overview = clean_text(card.overview, 300)
    if overview and not contains_title(overview, card.names):
        prompt = f"¿A qué título corresponde esta sinopsis? {overview}"
        options = _title_options(card, cards, rng, prompt)
        if options:
            question = _make_question("synopsis", prompt, options, card.title, card, rng, art)
            if question:
                out.append(question)

    if card.poster:
        options = _title_options(card, cards, rng)
        if options:
            question = _make_question(
                "poster", "¿De qué título es este póster?", options, card.title, card, rng, art
            )
            if question:
                out.append(question)

    if card.group_id and card.season_count > 1:
        member = card.sample_member if fold(card.sample_member) != fold(card.title) else f"{card.title} T1"
        options = _title_options(card, cards, rng, groups_only=True)
        if options:
            question = _make_question(
                "group", f"¿A qué serie pertenece «{member}»?", options, card.title, card, rng, art
            )
            if question:
                out.append(question)

        options = _number_options(card.season_count, rng)
        if options:
            question = _make_question(
                "seasons", f"¿Cuántas temporadas tiene «{display}»?",
                options, str(card.season_count), card, rng, art,
            )
            if question:
                out.append(question)

    if card.translations:
        answer = next((value for value in card.translations if _script(value) == "latin"), "")
        if not answer:
            return out
        pool = []
        for other in cards:
            if other.key == card.key:
                continue
            if card.tmdb_id and other.tmdb_id == card.tmdb_id:
                continue
            if card.group_id and other.group_id == card.group_id:
                continue
            if fold(answer) in {fold(value) for value in other.names}:
                continue
            pool.extend((value, other) for value in other.translations)
        # La trivia se juega en español: los distractores de traducción tienen
        # que poder leerse. Nada de hebreo, árabe, cirílico o CJK.
        latin_pool = [
            (value, other) for value, other in pool
            if _script(value) == "latin" and fold(value) not in card.names
            and fold(value) != fold(answer)
        ]
        same_kind = [value for value, other in latin_pool if other.kind == card.kind]
        chosen = same_kind if len(same_kind) >= 3 else [value for value, _other in latin_pool]
        distractors = _pick(_sample(chosen, 60, rng), 3, rng)
        if len(distractors) == 3:
            question = _make_question(
                "translation", f"¿Cuál es otro nombre de «{display}»?",
                [answer, *distractors], answer, card, rng, art,
            )
            if question:
                out.append(question)

    return out


def validate_question(question: dict) -> bool:
    if not isinstance(question, dict):
        return False
    payload = question.get("payload")
    if not isinstance(payload, dict):
        return False
    prompt = clean_text(payload.get("prompt"), MAX_PROMPT)
    options = [clean_text(option, 120) for option in (payload.get("options") or [])]
    answer = clean_text(question.get("answer_key"), 120)
    if not prompt or answer not in options or len(options) != MAX_OPTIONS:
        return False
    if len({fold(option) for option in options}) != MAX_OPTIONS:
        return False
    if question.get("kind") == "synopsis" and contains_title(prompt, [answer]):
        return False
    return bool(question.get("hash"))


def generate_questions(rows: list[dict], limit: int = 200, seed: int = 7) -> list[dict]:
    """Genera hasta `limit` preguntas distintas a partir de filas del catálogo."""
    cards = build_cards(rows)
    if len(cards) < 4:
        return []
    rng = random.Random(seed)
    years = sorted({card.year for card in cards if card.year >= 1900})
    actors = [(name, fold(name), _script(name), card) for card in cards for name in card.cast]
    directors = [(name, fold(name), _script(name), card) for card in cards for name in card.directors]
    budget = max(1, int(limit))
    out: list[dict] = []
    seen: set[str] = set()
    for card in cards:
        for question in generate_for_card(card, cards, rng, actors, directors, years):
            if question["hash"] in seen or not validate_question(question):
                continue
            seen.add(question["hash"])
            out.append(question)
    # Reparto equilibrado por tipo: sin esto, un banco de 200 podía salir con
    # 35 pósters y 12 sinopsis. Una partida de 8 rota tipos.
    buckets: dict[str, list[dict]] = {}
    for question in out:
        buckets.setdefault(question["kind"], []).append(question)
    for values in buckets.values():
        rng.shuffle(values)
    kinds = list(buckets)
    rng.shuffle(kinds)
    balanced: list[dict] = []
    cursor = 0
    while len(balanced) < budget and any(buckets[kind] for kind in kinds):
        kind = kinds[cursor % len(kinds)]
        cursor += 1
        if buckets[kind]:
            balanced.append(buckets[kind].pop())
    return balanced


# --- Base de datos ------------------------------------------------------------

def _is_due(last, now: datetime, hours: float) -> bool:
    if hours <= 0:
        return True
    if last is None:
        return True
    if isinstance(last, str):
        try:
            last = datetime.fromisoformat(last.replace("Z", "+00:00"))
        except ValueError:
            return True
    if last.tzinfo is None:
        last = last.replace(tzinfo=timezone.utc)
    return now - last >= timedelta(hours=hours)


async def _due(pool, hours: float) -> bool:
    try:
        row = await pool.fetchrow("select updated_at from sync_meta where key = 'trivia_last_run'")
    except Exception:  # noqa: BLE001 - esquema viejo: se intenta
        return True
    return _is_due(row["updated_at"] if row else None, datetime.now(timezone.utc), hours)


async def _fetch_rows(pool) -> list[dict]:
    # Mitad populares + mitad al azar: el banco no se queda solo con el top
    # (todas las preguntas saldrían fáciles) ni depende siempre del mismo lote.
    rows = await pool.fetch(_FETCH_SQL, MAX_ROWS, max(1, MAX_ROWS // 2))
    return [dict(row) for row in rows]


_BACKFILL_GROUP_PACKS = """
update trivia_questions q
set pack = case
    when g.kind = 'anime' then 'anime'
    when g.kind = 'kids' then 'infantil'
    when g.kind = 'series' and exists (
        select 1 from unnest(coalesce(t.genres, '{}')) genre
        where lower(genre) in ('telenovela', 'novela')
    ) then 'novelas'
    when g.kind = 'series' then 'series'
    when g.kind = 'movie' then 'cine'
    else q.pack
end
from title_groups g
left join titles t on t.id = g.primary_title_id
where q.source_ref = 'g:' || g.id and q.pack = 'general'
"""

_BACKFILL_TITLE_PACKS = """
update trivia_questions q
set pack = case
    when t.kind = 'anime' then 'anime'
    when t.kind = 'kids' then 'infantil'
    when t.kind = 'series' and exists (
        select 1 from unnest(coalesce(t.genres, '{}')) genre
        where lower(genre) in ('telenovela', 'novela')
    ) then 'novelas'
    when t.kind = 'series' then 'series'
    when t.kind = 'movie' then 'cine'
    else q.pack
end
from titles t
where q.source_ref = 't:' || t.id and q.pack = 'general'
"""


async def _backfill_packs(conn) -> int:
    """Clasifica las preguntas viejas de «general» por tipo de obra (una vez)."""
    updated = 0
    for sql in (_BACKFILL_GROUP_PACKS, _BACKFILL_TITLE_PACKS):
        try:
            result = await conn.execute(sql)
            updated += int(str(result).split()[-1])
        except Exception:  # noqa: BLE001 - esquema viejo o sin grupos: no frena el banco
            log.exception("trivia: no se pudieron clasificar las secciones")
    return updated


async def _mark_run(pool) -> None:
    try:
        await pool.execute(
            """
            insert into sync_meta (key, value, updated_at) values ('trivia_last_run', '', now())
            on conflict (key) do update set value = excluded.value, updated_at = now()
            """
        )
    except Exception:  # noqa: BLE001
        log.warning("no se pudo marcar la pasada del banco de trivia")


async def run(pool, limit: int | None = None, force: bool = False) -> int:
    """Genera e inserta preguntas nuevas. Devuelve cuántas entraron."""
    budget = max(1, int(limit or MAX_PER_CYCLE))
    # La clasificación de secciones corre SIEMPRE, aunque el banco no toque:
    # así un despliegue no deja «Cine/Series/Anime» vacíos hasta el próximo ciclo.
    async with pool.acquire() as conn:
        backfilled = await _backfill_packs(conn)
        if backfilled:
            log.info("trivia: %s preguntas clasificadas por sección", backfilled)
    if not force and not await _due(pool, MIN_HOURS):
        log.info("trivia: todavía no toca (TRIVIA_MIN_HOURS=%s)", MIN_HOURS)
        return 0
    async with pool.acquire() as conn:
        before = int(await conn.fetchval("select count(*) from trivia_questions") or 0)
        if before >= BANK_CAP and not force:
            log.info("trivia: el banco ya está en el tope (%s)", before)
            return 0
    rows = await _fetch_rows(pool)
    questions = generate_questions(rows, budget)
    if not questions:
        log.info("trivia: no hay material suficiente (%s filas)", len(rows))
        return 0
    async with pool.acquire() as conn:
        await conn.executemany(
            _INSERT_SQL,
            [
                (
                    question["id"], question["kind"],
                    json.dumps(question["payload"], ensure_ascii=False),
                    question["answer_key"], question["difficulty"], question["pack"],
                    question["source_ref"], question["hash"],
                )
                for question in questions
            ],
        )
        after = int(await conn.fetchval("select count(*) from trivia_questions") or 0)
        overflow = max(0, after - BANK_CAP)
        if overflow:
            await conn.execute(_PRUNE_SQL, overflow)
        await _mark_run(pool)
    added = max(0, after - before)
    log.info("trivia: %s nuevas (banco %s, tope %s)", added, after, BANK_CAP)
    return added


async def _seed_json(pool, path: str) -> int:
    """Carga un snapshot real del catálogo en una base local (solo para probar)."""
    rows = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(rows, list):
        raise ValueError("el snapshot debe ser una lista")
    async with pool.acquire() as conn:
        async with conn.transaction():
            for row in rows:
                title_id = clean_text(row.get("id"), 120)
                if not title_id:
                    continue
                title = clean_text(row.get("title"), 200)
                kind = clean_text(row.get("kind"), 16) or "movie"
                await conn.execute(
                    """
                    insert into titles
                        (id, source, source_id, kind, title, original_title, year, synopsis,
                         poster_url, play_ref, popularity, vote_count, genres)
                    values ($1, 'xtv', $2, $3, $4, $5, $6, $7, $8, $1, $9, $10, $11)
                    on conflict (id) do update set
                        title = excluded.title, year = excluded.year,
                        synopsis = excluded.synopsis, poster_url = excluded.poster_url,
                        popularity = excluded.popularity, vote_count = excluded.vote_count,
                        genres = excluded.genres
                    """,
                    title_id, title_id[4:] if title_id.startswith("xtv:") else title_id,
                    kind, title, clean_text(row.get("original_title"), 200),
                    _as_int(row.get("year")), clean_text(row.get("overview"), 2000),
                    clean_text(row.get("poster_url"), 400), _as_float(row.get("popularity")),
                    _as_int(row.get("vote_count")), as_list(row.get("genres")),
                )
                await conn.execute(
                    """
                    insert into enrichment
                        (title_id, tmdb_id, match_confidence, overview, genres, popularity,
                         vote_count, original_title, localized_title, translated_titles,
                         cast_names, director_names)
                    values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
                    on conflict (title_id) do update set
                        tmdb_id = excluded.tmdb_id, overview = excluded.overview,
                        genres = excluded.genres, popularity = excluded.popularity,
                        vote_count = excluded.vote_count, original_title = excluded.original_title,
                        localized_title = excluded.localized_title,
                        translated_titles = excluded.translated_titles,
                        cast_names = excluded.cast_names, director_names = excluded.director_names
                    """,
                    title_id, _as_int(row.get("tmdb_id")), _as_float(row.get("match_confidence")),
                    clean_text(row.get("overview"), 2000), as_list(row.get("genres")),
                    _as_float(row.get("popularity")), _as_int(row.get("vote_count")),
                    clean_text(row.get("original_title"), 200), clean_text(row.get("localized_title"), 200),
                    as_list(row.get("translated_titles")), clean_text(row.get("cast_names"), 600),
                    clean_text(row.get("director_names"), 400),
                )
                group_id = clean_text(row.get("group_id"), 80)
                if group_id:
                    await conn.execute(
                        """
                        insert into title_groups (id, base_title, kind, primary_title_id)
                        values ($1, $2, $3, $4) on conflict (id) do nothing
                        """,
                        group_id, clean_title(row.get("group_title") or title), kind, title_id,
                    )
                    await conn.execute(
                        """
                        insert into group_members (group_id, title_id, season)
                        values ($1, $2, $3) on conflict do nothing
                        """,
                        group_id, title_id, parse_season(title),
                    )
    return len(rows)


async def stats(pool) -> dict:
    async with pool.acquire() as conn:
        questions = int(await conn.fetchval("select count(*) from trivia_questions") or 0)
        served = int(await conn.fetchval("select count(*) from trivia_served") or 0)
        reports = int(await conn.fetchval("select count(*) from trivia_reports") or 0)
        rows = await conn.fetch(
            "select kind, count(*) as n from trivia_questions group by kind order by kind"
        )
    return {
        "questions": questions,
        "served": served,
        "reports": reports,
        "by_kind": {row["kind"]: int(row["n"]) for row in rows},
    }


def _cli() -> int:
    parser = argparse.ArgumentParser(description="Banco de preguntas de Vision+ Play")
    parser.add_argument("--from-json", help="snapshot del catálogo (genera sin base)")
    parser.add_argument("--out", help="JSON de salida para --from-json")
    parser.add_argument("--limit", type=int, default=200)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--seed-json", help="carga este snapshot en la base local")
    parser.add_argument("--generate", action="store_true", help="genera e inserta en la base")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--stats", action="store_true")
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL", ""))
    args = parser.parse_args()

    if args.from_json:
        rows = json.loads(Path(args.from_json).read_text(encoding="utf-8"))
        questions = generate_questions(rows, args.limit, args.seed)
        out = Path(args.out or "trivia_preguntas.json")
        out.write_text(json.dumps(questions, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"{len(questions)} preguntas -> {out}")
        return 0

    if not args.database_url:
        parser.error("falta --database-url (o DATABASE_URL) para tocar la base")

    async def go() -> None:
        pool = await asyncpg.create_pool(args.database_url, min_size=1, max_size=2)
        try:
            if args.seed_json:
                count = await _seed_json(pool, args.seed_json)
                print(f"snapshot cargado: {count} filas")
            if args.generate:
                added = await run(pool, limit=args.limit, force=args.force)
                print(f"preguntas nuevas: {added}")
            if args.stats:
                print(json.dumps(await stats(pool), ensure_ascii=False, indent=2))
        finally:
            await pool.close()

    asyncio.run(go())
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    raise SystemExit(_cli())
