"""Banners diarios del Inicio para Vision+ Home.

El VPS busca UNA vez al día (20 h de vigencia) las películas/series destacadas y su
arte panorámico en TMDB, y las guarda en `launcher_banners`. La API solo sirve esa
foto curada; los partidos NO se guardan aquí: el launcher toma los de la agenda del
feed (`sports`), que ya se refresca con el resto del Inicio. Así el marcador y el
«EN VIVO» nunca se quedan pegados.

La primera petición del día construye la foto (5-6 llamadas a TMDB en paralelo); las
siguientes la sirven de Postgres y, si ya venció, la refrescan en segundo plano sin
hacer esperar a la tele.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import time
import unicodedata
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Any, Iterable

log = logging.getLogger("visionplus.banners")

TMDB_IMAGE = "https://image.tmdb.org/t/p/w1280"
BANNER_TTL_SECONDS = 20 * 3600
MAX_MOVIES = 5

# Un solo refresco a la vez en TODO el VPS (aunque la API corra con varios workers).
_REFRESH_LOCK_KEY = 773421
# Si la foto está vieja o falló, no se martilla TMDB: se reintenta en 15 min.
_BACKGROUND_RETRY_SECONDS = 15 * 60
_refresh_running = False
# `None` = nunca se intentó. NO usar 0.0: `time.monotonic()` cuenta desde el arranque del
# equipo, así que en un contenedor recién levantado (o un runner de CI) vale menos que el
# enfriamiento y la primera búsqueda se quedaría esperando 15 minutos.
_last_refresh_attempt: float | None = None


def _mono() -> float:
    """Reloj monótono (seam de pruebas: un equipo recién arrancado tiene valores pequeños)."""
    return time.monotonic()


def _refresh_due(mono: float) -> bool:
    """La primera vez siempre; después, un enfriamiento de `_BACKGROUND_RETRY_SECONDS`."""
    return (
        _last_refresh_attempt is None
        or mono - _last_refresh_attempt >= _BACKGROUND_RETRY_SECONDS
    )

_CREATE_TABLE = """
create table if not exists launcher_banners (
    id text primary key,
    kind text not null default 'movie',
    position integer not null default 0,
    payload jsonb not null default '{}'::jsonb,
    generated_at timestamptz not null default now()
)
"""

_CREATE_INDEX = """
create index if not exists idx_launcher_banners_kind
    on launcher_banners (kind, position)
"""

# «House T3», «House Temporada 3», «House Season 3»: el banner muestra el nombre base.
_SEASON_RE = re.compile(
    r"\s*[-–:(]?\s*(?:t|temp\.?|temporada|season)\s*\d{1,2}\s*\)?\s*$",
    re.IGNORECASE,
)

_ensured = False


def _text(value: Any) -> str:
    return str(value or "").strip()


def _int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _float(value: Any, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _fold(value: str) -> str:
    normalized = unicodedata.normalize("NFD", value or "")
    return "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn").lower().strip()


def _row_get(row: Any, key: str, default: Any = None) -> Any:
    """Funciona con asyncpg.Record y con dicts (las pruebas usan dicts)."""
    try:
        value = row[key]
    except (KeyError, IndexError, TypeError):
        return default
    return default if value is None else value


def _clean_title(title: str) -> str:
    cleaned = _SEASON_RE.sub("", _text(title)).strip(" -–:(")
    return cleaned or _text(title)


def _kind_label(kind: str) -> str:
    return {
        "series": "SERIE DESTACADA",
        "anime": "ANIME DESTACADO",
        "kids": "INFANTIL DESTACADO",
    }.get(kind, "PELÍCULA DESTACADA")


def _is_series(row: Any) -> bool:
    kind = _text(_row_get(row, "kind")) or "movie"
    program_type = _text(_row_get(row, "program_type"))
    return (
        kind in ("series", "anime", "kids")
        or program_type in ("series", "teleplay", "anime")
        or bool(_text(_row_get(row, "base_title")))
    )


def pick_movie_candidates(rows: Iterable[Any], limit: int = MAX_MOVIES) -> list[Any]:
    """Dedupe por obra (tmdb_id+media) y descarta lo que no se puede abrir/reproducir.

    Prioriza películas: el hero es de «películas destacadas» y solo usa series/anime
    cuando no hay suficientes largometrajes. El orden dentro de cada grupo conserva
    el ranking de la foto semanal.
    """
    out: list[Any] = []
    seen: set[tuple] = set()
    for row in rows:
        poster = _text(_row_get(row, "poster_url"))
        play_ref = _text(_row_get(row, "play_ref"))
        title = _text(_row_get(row, "base_title")) or _text(_row_get(row, "title"))
        if not poster or not play_ref or not title:
            continue
        tmdb_id = _int(_row_get(row, "tmdb_id"))
        media = _text(_row_get(row, "media"))
        key: tuple = (tmdb_id, media) if tmdb_id else (_fold(title), _int(_row_get(row, "year")))
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    out.sort(key=_is_series)  # False (película) va antes que True (serie/anime)
    return out[: max(1, limit)]


def movie_banner(row: Any, backdrop: str = "", position: int = 0) -> dict:
    kind = _text(_row_get(row, "kind")) or "movie"
    series = _is_series(row)
    base_title = _text(_row_get(row, "base_title"))
    title = _clean_title(base_title or _text(_row_get(row, "title")))
    year = _int(_row_get(row, "year"))
    tmdb_rating = _float(_row_get(row, "tmdb_rating"))
    portal_score = _float(_row_get(row, "score"))
    rating = round(tmdb_rating or portal_score, 1)
    now_year = datetime.now(timezone.utc).year
    if position == 0:
        badge = "DESTACADO HOY"
    elif _row_get(row, "is_premiere") or year >= now_year - 1:
        badge = "ESTRENO"
    else:
        badge = "TENDENCIA"
    return {
        "id": f"movie:{_text(_row_get(row, 'id'))}",
        "kind": "movie",
        "series": bool(series),
        "title": title,
        "poster": _text(_row_get(row, "poster_url")),
        "backdrop": backdrop,
        "synopsis": _text(_row_get(row, "overview")) or _text(_row_get(row, "synopsis")),
        "year": year,
        "rating": rating,
        "durationMin": _int(_row_get(row, "duration_min")),
        "ref": _text(_row_get(row, "id")),
        "videoUrl": _text(_row_get(row, "play_ref")),
        "badge": badge,
        "eyebrow": _kind_label(kind),
    }


def _http_json(url: str, timeout: float = 6.0) -> dict:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "VisionPlusHome/1.0", "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


async def _tmdb_backdrop(tmdb_id: int, media: str, api_key: str) -> str:
    if not tmdb_id or not api_key:
        return ""
    kind = "tv" if _text(media).lower() == "tv" else "movie"
    url = (
        f"https://api.themoviedb.org/3/{kind}/{int(tmdb_id)}"
        f"?api_key={urllib.request.quote(api_key, safe='')}&language=es-MX"
    )
    try:
        data = await asyncio.to_thread(_http_json, url)
    except Exception:  # noqa: BLE001 - sin arte se usa el póster, no se rompe el Inicio
        return ""
    path = _text(data.get("backdrop_path"))
    if not path:
        return ""
    # Un `?v=` diario evita que un edge de BunnyCDN con un 404 cacheado deje el hero
    # sin arte hasta 12 h (visto en vivo: el mismo archivo respondía 200 en otro edge).
    version = int(datetime.now(timezone.utc).timestamp())
    return f"{TMDB_IMAGE}{path}?v={version}"


async def ensure_table(pool) -> None:
    global _ensured
    if _ensured:
        return
    await pool.execute(_CREATE_TABLE)
    await pool.execute(_CREATE_INDEX)
    _ensured = True


async def _candidate_rows(pool) -> list[Any]:
    week = await pool.fetchval("select max(week) from weekly_trending")
    if week:
        rows = await pool.fetch(
            """
            select t.id, t.title, t.kind, t.program_type, t.poster_url, t.play_ref,
                   t.year, t.score, t.duration_min, t.synopsis, t.popularity,
                   t.is_premiere, e.tmdb_id, e.overview, e.rating as tmdb_rating,
                   w.media, w.rank, tg.base_title
            from weekly_trending w
            join enrichment e
              on e.tmdb_id = w.tmdb_id and coalesce(e.match_confidence, 0) >= 0.5
            join titles t on t.id = e.title_id
            left join group_members gm on gm.title_id = t.id
            left join title_groups tg on tg.id = gm.group_id
            where w.week = $1
              and coalesce(t.poster_url, '') <> ''
              and coalesce(t.play_ref, '') <> ''
              and t.source = 'xtv'
            order by w.rank,
                     coalesce(e.match_confidence, 0) desc,
                     t.popularity desc nulls last,
                     t.vote_count desc nulls last,
                     t.year asc nulls last
            limit 40
            """,
            week,
        )
    else:
        # Primer arranque sin la foto semanal de TMDB: lo más popular del catálogo.
        rows = await pool.fetch(
            """
            select t.id, t.title, t.kind, t.program_type, t.poster_url, t.play_ref,
                   t.year, t.score, t.duration_min, t.synopsis, t.popularity,
                   t.is_premiere, e.tmdb_id, e.overview, e.rating as tmdb_rating,
                   null::text as media, 0 as rank, tg.base_title
            from titles t
            left join enrichment e on e.title_id = t.id
            left join group_members gm on gm.title_id = t.id
            left join title_groups tg on tg.id = gm.group_id
            where coalesce(t.poster_url, '') <> ''
              and coalesce(t.play_ref, '') <> ''
              and t.source = 'xtv'
              and (t.popularity > 0 or t.vote_count > 0)
            order by t.popularity desc nulls last,
                     t.vote_count desc nulls last,
                     t.year desc nulls last
            limit 30
            """
        )
    return [dict(row) for row in rows]


async def _refresh_movies_locked(
    pool, tmdb_key: str, limit: int, lock_conn
) -> int:
    """Cuerpo del refresco, ya con el candado tomado (misma conexión para escribir)."""
    rows = await _candidate_rows(pool)
    picked = pick_movie_candidates(rows, limit=limit)
    if not picked:
        log.warning("banners: no hay títulos elegibles")
        return 0
    backdrops = await asyncio.gather(
        *(
            _tmdb_backdrop(
                _int(_row_get(row, "tmdb_id")),
                _text(_row_get(row, "media")),
                tmdb_key,
            )
            for row in picked
        ),
        return_exceptions=True,
    )
    payloads = []
    for position, row in enumerate(picked):
        art = backdrops[position]
        backdrop = art if isinstance(art, str) else ""
        payloads.append(movie_banner(row, backdrop, position))
    async with lock_conn.transaction():
        await lock_conn.execute("delete from launcher_banners where kind = 'movie'")
        for position, payload in enumerate(payloads):
            await lock_conn.execute(
                """
                insert into launcher_banners (id, kind, position, payload, generated_at)
                values ($1, 'movie', $2, $3::jsonb, now())
                """,
                payload["id"],
                position,
                json.dumps(payload, ensure_ascii=False),
            )
    log.info("banners: %s películas/series actualizadas (tmdb=%s)", len(payloads), bool(tmdb_key))
    return len(payloads)


async def refresh_movies(pool, tmdb_key: str, limit: int = MAX_MOVIES) -> int:
    """Busca la foto del día y la reemplaza completa (una transacción).

    El candado de Postgres evita que dos peticiones (o dos workers) gasten las mismas
    llamadas a TMDB: el que no lo consigue sirve la foto que ya hay y se va.
    """
    global _refresh_running
    try:
        async with pool.acquire() as lock_conn:
            locked = await lock_conn.fetchval(
                "select pg_try_advisory_lock($1)", _REFRESH_LOCK_KEY
            )
            if not locked:
                log.info("banners: otra instancia ya está refrescando")
                return 0
            try:
                return await _refresh_movies_locked(pool, tmdb_key, limit, lock_conn)
            finally:
                await lock_conn.execute(
                    "select pg_advisory_unlock($1)", _REFRESH_LOCK_KEY
                )
    except Exception:  # noqa: BLE001 - un refresco fallido no puede tumbar el Inicio
        log.exception("banners: falló el refresco")
        return 0
    finally:
        _refresh_running = False


async def _load_movies(pool) -> tuple[list[dict], datetime | None]:
    rows = await pool.fetch(
        "select payload, generated_at from launcher_banners where kind = 'movie' order by position"
    )
    items: list[dict] = []
    stamp: datetime | None = None
    for row in rows:
        payload = row["payload"]
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except json.JSONDecodeError:
                continue
        if isinstance(payload, dict):
            items.append(payload)
        created = row["generated_at"]
        if created is not None and (stamp is None or created > stamp):
            stamp = created
    return items, stamp


async def serve(pool, background, tmdb_key: str) -> dict:
    """Respuesta de `/v1/banners`: cache diaria + refresco en segundo plano.

    La respuesta SIEMPRE trae `nextRefreshAt` en el futuro: cuando la foto está vieja
    (o no hay), el cliente espera al reintento en vez de preguntar en bucle. El refresco
    real corre una sola vez (candado de Postgres) y con enfriamiento si falla.
    """
    global _last_refresh_attempt, _refresh_running
    now = datetime.now(timezone.utc)
    movies: list[dict] = []
    stamp: datetime | None = None
    stale = True
    try:
        await ensure_table(pool)
        movies, stamp = await _load_movies(pool)
        stale = stamp is None or (now - stamp).total_seconds() > BANNER_TTL_SECONDS
        if not movies:
            # Primera petición tras el despliegue: se construye en el momento (5 llamadas
            # a TMDB en paralelo). Si TMDB tarda o falla, el launcher usa su feed y el
            # siguiente intento espera el enfriamiento (no se martilla TMDB).
            mono = _mono()
            if _refresh_due(mono):
                _last_refresh_attempt = mono
                try:
                    await asyncio.wait_for(refresh_movies(pool, tmdb_key), timeout=14.0)
                    movies, stamp = await _load_movies(pool)
                    stale = stamp is None or (now - stamp).total_seconds() > BANNER_TTL_SECONDS
                except Exception:  # noqa: BLE001
                    log.exception("banners: no se pudo construir la foto inicial")
        elif stale:
            # Ya hay algo que servir; la búsqueda del día corre después de responder.
            # Un solo refresco a la vez y, si falló, 15 min de enfriamiento.
            mono = _mono()
            if not _refresh_running and _refresh_due(mono):
                _last_refresh_attempt = mono
                _refresh_running = True
                background.add_task(refresh_movies, pool, tmdb_key)
    except Exception:  # noqa: BLE001 - el Inicio nunca se cae por los banners
        log.exception("banners: cache no disponible")
    if stale:
        next_at = now + timedelta(seconds=_BACKGROUND_RETRY_SECONDS)
    else:
        next_at = (stamp or now) + timedelta(seconds=BANNER_TTL_SECONDS)
    return {
        "generatedAt": int((stamp or now).timestamp() * 1000),
        "nextRefreshAt": int(next_at.timestamp() * 1000),
        "items": movies,
    }
