"""Vision+ catalog index API — fase 1.

Sirve el índice (categorías + títulos) que el worker `sync` mantiene en Postgres.
La reproducción NO pasa por aquí: la app sigue acuñando licencias en el dispositivo.

Todos los endpoints /v1/* requieren el header X-Api-Key.
"""

import asyncio
import hmac
import json
import os
import re
import time
import unicodedata
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import asyncpg
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Query, Request
from starlette.middleware.gzip import GZipMiddleware

import markers as skip_markers
import slots as device_slots

DATABASE_URL = os.environ["DATABASE_URL"]
API_KEY = os.environ["API_KEY"]
# Llave de administración para escribir la configuración remota (Fase 2). Sin ella,
# POST /v1/config queda deshabilitado (403): la API_KEY viaja en el APK y no basta.
CONFIG_ADMIN_KEY = os.environ.get("CONFIG_ADMIN_KEY", "").strip()

# Los partidos se agrupan por día en hora de México (el worker baja en UTC).
SPORTS_TZ = "America/Mexico_City"

FILM_COLUMNS = """
    t.id, t.title, t.year, t.duration_min, t.synopsis,
    t.poster_url, t.play_ref, t.source, t.score, t.kind,
    t.popularity, t.vote_count
"""

pool: asyncpg.Pool | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global pool
    pool = await asyncpg.create_pool(DATABASE_URL, min_size=1, max_size=4, command_timeout=15)
    try:
        yield
    finally:
        await pool.close()
        pool = None


app = FastAPI(
    title="Vision+",
    version="1.0.0",
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
app.add_middleware(GZipMiddleware, minimum_size=1000)


def require_key(x_api_key: str | None) -> None:
    if not x_api_key or not hmac.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="API key inválida")


def db() -> asyncpg.Pool:
    if pool is None:
        raise HTTPException(status_code=503, detail="base de datos no disponible")
    return pool


def film(record: asyncpg.Record, enr=None) -> dict:
    """La forma que espera `Film` en la app: mapea 1:1 con RemoteIndexSource.

    Con enriquecimiento TMDB (confianza >= 0.5) mandan su rating, año y sinopsis
    es-MX sobre los datos del portal.
    """
    rating = record["score"]
    year = record["year"]
    synopsis = record["synopsis"]
    title = strip_season_mark(record["title"], record["kind"])
    popularity = 0.0
    votes = 0
    try:
        if "popularity" in record.keys():
            popularity = float(record["popularity"] or 0)
    except (TypeError, ValueError, KeyError):
        popularity = 0.0
    try:
        if "vote_count" in record.keys():
            votes = int(record["vote_count"] or 0)
    except (TypeError, ValueError, KeyError):
        votes = 0
    if enr is not None:
        if enr["rating"] is not None:
            rating = enr["rating"]
        if enr["year"]:
            year = enr["year"]
        overview = (enr["overview"] or "").strip()
        if overview:
            synopsis = overview
        try:
            if "popularity" in enr.keys() and enr["popularity"] is not None:
                popularity = float(enr["popularity"] or 0)
        except (TypeError, ValueError, KeyError, AttributeError):
            pass
        try:
            if "vote_count" in enr.keys() and enr["vote_count"] is not None:
                votes = int(enr["vote_count"] or 0)
        except (TypeError, ValueError, KeyError, AttributeError):
            pass
    video_url = record["play_ref"]
    source_url = record["source"]
    film_id = record["id"]
    if video_url.startswith("magisvod:"):
        cid = video_url.removeprefix("magisvod:")
        video_url = f"xtv:{cid}"
        if film_id.startswith("magis:"):
            film_id = f"xtv:{cid}"
        source_url = "xtv"
    return {
        "id": film_id,
        "title": title,
        "year": year or 0,
        "durationMin": record["duration_min"] or 0,
        "synopsis": synopsis or "",
        "posterUrl": record["poster_url"] or "",
        "videoUrl": video_url,
        "sourceUrl": source_url,
        "score": float(rating or 0),
        "popularity": popularity,
        "voteCount": votes,
        "kind": record["kind"] or "",
    }


async def _enrichment_map(ids) -> dict:
    """Enriquecimiento TMDB por título (solo matches con confianza)."""
    unique = list(dict.fromkeys(i for i in ids if i))
    if not unique:
        return {}
    rows = await db().fetch(
        """select title_id, rating, year, overview, popularity, vote_count from enrichment
           where title_id = any($1::text[]) and coalesce(match_confidence, 0) >= 0.5""",
        unique,
    )
    return {r["title_id"]: r for r in rows}


_YEAR_SUFFIX = r"(?:\s*[\(\[]?\s*(?:19|20)\d{2}\s*[\)\]]?)?"

# Marcas de temporada que se ocultan al mostrar (espejo de sync/grouping.py: el mismo
# criterio para agrupar y para mostrar, para que no queden "T1/Temporada 1/tem1" sueltos).
_SEASON_DISPLAY = (
    re.compile(r"\s*[\(\[]?\b(?:t|tem|temp|temporada|season|s)\s*[.:\-]?\s*(\d{1,2})\b[\)\]]?\s*(?:hd|fhd|completa|completo)?" + _YEAR_SUFFIX + r"\s*$", re.IGNORECASE),
    re.compile(r"\s*[\(\[]?\b(\d{1,2})\s*[ªa]?\s*temporada\b[\)\]]?\s*(?:hd|fhd|completa|completo)?" + _YEAR_SUFFIX + r"\s*$", re.IGNORECASE),
    re.compile(r"\s+(\d{1,2})\s*(?:hd|fhd|completa|completo)?" + _YEAR_SUFFIX + r"\s*$", re.IGNORECASE),
)

_NON_SEASON_TAIL = ("cap", "capitulo", "capítulo", "episodio", "episode", "ep",
                    "parte", "part", "vol", "volumen", "volume")


def _fold(text: str) -> str:
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return " ".join("".join(c if c.isalnum() else " " for c in folded).split())


def strip_season_mark(title: str, kind: str) -> str:
    """Título base para mostrar: sin la marca de temporada final (solo series/anime/kids).

    Las películas no se tocan («Avatar 2» es saga, no temporada); el número solo tampoco
    vale para kids; y las colas de capítulo/parte/volumen no cuentan como temporada.
    """
    if not title or (kind or "") not in ("series", "anime", "kids"):
        return title
    for idx, pattern in enumerate(_SEASON_DISPLAY):
        if idx == 2 and kind not in ("series", "anime"):
            continue
        m = pattern.search(title)
        if not m:
            continue
        base = title[: m.start()].strip(" -–—:.")
        if not base:
            continue
        if idx == 2:
            tail = _fold(base).split(" ")
            if tail and tail[-1] in _NON_SEASON_TAIL:
                continue
        return base
    return title


async def index_version() -> int:
    return await db().fetchval("select version from sync_state where id = 1") or 1


SECTIONS = {"movies": "Películas", "series": "Series", "novelas": "Novelas", "anime": "Anime"}


async def _group_maps():
    """Temporadas agrupadas: título -> grupo, grupo -> temporadas, grupo -> título base.

    Las películas nunca son temporadas: si un grupo viejo mezclaba una película
    («Zootopia» + «Zootopia+»), aquí se ignora ese miembro y, si al grupo le queda
    una sola temporada, sus miembros se sirven sueltos con su título original.
    """
    group_rows = await db().fetch(
        """
        select gm.title_id, gm.season, gm.group_id, g.base_title, t.play_ref, t.source
        from group_members gm
        join title_groups g on g.id = gm.group_id
        join titles t on t.id = gm.title_id
        where coalesce(t.program_type, '') != 'movie'
        order by gm.group_id, gm.season, (t.source = 'xtv') desc
        """
    )
    member_of: dict[str, dict] = {}
    seasons_by_group: dict[str, list] = {}
    base_of_group: dict[str, str] = {}
    for gr in group_rows:
        member_of[gr["title_id"]] = gr
        seasons_by_group.setdefault(gr["group_id"], []).append(
            {"n": gr["season"], "titleId": gr["title_id"], "videoUrl": gr["play_ref"]}
        )
        base_of_group[gr["group_id"]] = gr["base_title"]
    for group_id, seasons in list(seasons_by_group.items()):
        if len(seasons) < 2:
            del seasons_by_group[group_id]
            base_of_group.pop(group_id, None)
            for season in seasons:
                member_of.pop(season["titleId"], None)
    return member_of, seasons_by_group, base_of_group


def _grouped_entry(row, member_of, seasons_by_group, base_of_group, enr=None) -> tuple[dict, str | None]:
    """Film con temporadas resueltas + group_id si está agrupado (si no, None)."""
    member = member_of.get(row["id"])
    entry = film(row, enr)
    if member is None:
        return entry, None
    entry["groupId"] = member["group_id"]
    entry["title"] = strip_season_mark(
        base_of_group.get(member["group_id"], entry["title"]), row["kind"]
    )
    seasons = seasons_by_group.get(member["group_id"], [])
    # La ficha y el play resuelven por portal XTV: si el representante es de otro portal
    # (p. ej. magis), la ficha no cargaba capítulos; manda el primer miembro XTV.
    xtv_first = next((s for s in seasons if s["videoUrl"].startswith("xtv:")), None)
    if xtv_first is not None:
        entry["id"] = xtv_first["titleId"]
        entry["videoUrl"] = xtv_first["videoUrl"]
    entry["seasons"] = seasons
    return entry, member["group_id"]


@app.get("/")
async def root():
    return {"status": "ok"}


@app.get("/healthz")
async def healthz():
    ok = False
    try:
        ok = await db().fetchval("select 1") == 1
    except Exception:
        ok = False
    if not ok:
        raise HTTPException(status_code=503, detail="unavailable")
    return {"status": "ok"}


@app.get("/v1/index")
async def index(
    since: int = Query(default=0, ge=0),
    limitFilms: int = Query(default=0, ge=0, le=500),
    x_api_key: str | None = Header(default=None),
):
    """Índice completo. Con `since` al día responde {upToDate: true} sin reenviar nada.
    `limitFilms` recorta los títulos por categoría (para la app móvil); `total` queda real."""
    require_key(x_api_key)
    version = await index_version()
    if since and since >= version:
        return {"version": version, "upToDate": True, "categories": []}
    rows = await db().fetch(
        f"""
        select {FILM_COLUMNS}, tc.category_id, tc.position,
               c.title as category_title, c.kind as category_kind
        from title_categories tc
        join titles t on t.id = tc.title_id
        join categories c on c.id = tc.category_id
        where c.kind != 'section'
        order by c.position, c.title, tc.position
        """
    )
    member_of, seasons_by_group, base_of_group = await _group_maps()
    enr = await _enrichment_map([r["id"] for r in rows])

    cats: dict[str, dict] = {}
    seen_groups: dict[str, set] = {}
    for row in rows:
        category = cats.setdefault(
            row["category_id"],
            {
                "id": row["category_id"],
                "title": row["category_title"],
                "kind": row["category_kind"],
                "films": [],
            },
        )
        entry, group_id = _grouped_entry(row, member_of, seasons_by_group, base_of_group, enr.get(row["id"]))
        if group_id is not None:
            seen = seen_groups.setdefault(row["category_id"], set())
            if group_id in seen:
                continue
            seen.add(group_id)
        category["films"].append(entry)
    for category in cats.values():
        category["total"] = len(category["films"])
        if limitFilms:
            category["films"] = category["films"][:limitFilms]
    updated = await db().fetchval("select updated_at from sync_state where id = 1")
    return {
        "version": version,
        "upToDate": False,
        "updatedAt": updated.isoformat() if updated else None,
        "categories": list(cats.values()),
    }


@app.get("/v1/categories")
async def categories(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    rows = await db().fetch(
        """
        select c.id, c.title, c.kind, count(tc.title_id) as titles
        from categories c
        left join title_categories tc on tc.category_id = c.id
        where c.kind != 'section'
        group by c.id, c.title, c.kind
        order by c.position, c.title
        """
    )
    return {
        "version": await index_version(),
        "categories": [
            {"id": r["id"], "title": r["title"], "kind": r["kind"], "titles": r["titles"]}
            for r in rows
        ],
    }


_SECTION_CACHE: dict[tuple[str, int, int], dict] = {}


@app.get("/v1/section/{name}")
async def section(
    name: str,
    limit: int = Query(default=30, ge=1, le=100),
    x_api_key: str | None = Header(default=None),
):
    """Una sección del riel (Películas/Series/Novelas/Anime): sus hileras de
    género, cada una solo con títulos de esa sección (nada genérico)."""
    require_key(x_api_key)
    if name not in SECTIONS:
        raise HTTPException(status_code=404, detail="sección desconocida")
    ver = await index_version()
    cache_key = (name, limit, ver)
    if cache_key in _SECTION_CACHE:
        return _SECTION_CACHE[cache_key]

    cats = await db().fetch(
        "select id, title from categories where kind = 'section' and id like $1 order by position, title",
        f"xtv-{name}-%",
    )
    member_of, seasons_by_group, base_of_group = await _group_maps()
    rows_out = []
    for c in cats:
        films = await db().fetch(
            f"""select {FILM_COLUMNS} from title_categories tc
                join titles t on t.id = tc.title_id
                where tc.category_id = $1 order by tc.position limit $2""",
            c["id"],
            limit,
        )
        enr = await _enrichment_map([r["id"] for r in films])
        total = await db().fetchval(
            "select count(*) from title_categories where category_id = $1", c["id"]
        )
        seen: set[str] = set()
        entries = []
        for r in films:
            entry, group_id = _grouped_entry(r, member_of, seasons_by_group, base_of_group, enr.get(r["id"]))
            if group_id is not None:
                if group_id in seen:
                    continue
                seen.add(group_id)
            entries.append(entry)
        if entries:
            rows_out.append({"id": c["id"], "title": c["title"], "total": total, "films": entries})
    result = {"name": name, "title": SECTIONS[name], "version": ver, "rows": rows_out}
    _SECTION_CACHE[cache_key] = result
    return result


@app.get("/v1/trending")
async def trending(
    limit: int = Query(default=20, ge=1, le=40),
    x_api_key: str | None = Header(default=None),
):
    """«Populares de la semana» (TMDB /trending): la foto semanal casada con el catálogo.

    Solo entran títulos que tenemos con ficha (póster) y reproducción; orden de la semana.
    """
    require_key(x_api_key)
    week = await db().fetchval("select max(week) from weekly_trending")
    if not week:
        return {"week": None, "films": []}
    rows = await db().fetch(
        f"""select {FILM_COLUMNS} from weekly_trending w
            join enrichment e on e.tmdb_id = w.tmdb_id and coalesce(e.match_confidence, 0) >= 0.5
            join titles t on t.id = e.title_id
            where w.week = $1 and coalesce(t.poster_url, '') <> ''
              and coalesce(t.play_ref, '') <> ''
            order by w.rank limit $2""",
        week,
        limit,
    )
    enr = await _enrichment_map([r["id"] for r in rows])
    return {
        "week": week,
        "films": [film(r, enr.get(r["id"])) for r in rows],
    }


@app.get("/v1/schedule")
async def schedule(
    days: int = Query(default=7, ge=1, le=14),
    x_api_key: str | None = Header(default=None),
):
    """Agenda de estrenos (TVMaze): capítulos al aire hoy + próximos días, con el imdb
    para que la app los case con las series que sigue el usuario."""
    require_key(x_api_key)
    rows = await db().fetch(
        """select show_name, season, episode, ep_name, airtime, imdb, title_id
           from schedule_airings
           where airdate between current_date and current_date + make_interval(days => $1)
           order by airtime nulls last, show_name, season, episode
           limit 500""",
        days,
    )
    return {
        "airings": [
            {
                "show": r["show_name"],
                "season": int(r["season"] or 0),
                "episode": int(r["episode"] or 0),
                "title": r["ep_name"] or "",
                "airtime": r["airtime"].isoformat() if r["airtime"] else None,
                "imdb": r["imdb"] or "",
                "titleId": r["title_id"] or "",
            }
            for r in rows
        ]
    }


@app.get("/v1/category/{category_id}")
async def category(
    category_id: str,
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=100, ge=1, le=500),
    x_api_key: str | None = Header(default=None),
):
    require_key(x_api_key)
    rows = await db().fetch(
        f"""
        select {FILM_COLUMNS}
        from title_categories tc
        join titles t on t.id = tc.title_id
        where tc.category_id = $1
        order by tc.position, t.title
        offset $2 limit $3
        """,
        category_id,
        (page - 1) * pageSize,
        pageSize,
    )
    total = await db().fetchval(
        "select count(*) from title_categories where category_id = $1", category_id
    )
    enr = await _enrichment_map([r["id"] for r in rows])
    return {
        "id": category_id,
        "page": page,
        "pageSize": pageSize,
        "total": total,
        "films": [film(r, enr.get(r["id"])) for r in rows],
    }


_SEARCH_CACHE: dict[str, tuple[float, list[dict]]] = {}
_SEARCH_CACHE_TTL = 300.0  # 5 minutos


@app.get("/v1/search")
async def search(
    q: str = Query(min_length=1),
    limit: int = Query(default=50, ge=1, le=200),
    x_api_key: str | None = Header(default=None),
):
    require_key(x_api_key)
    raw_q = q.strip()
    if not raw_q:
        return {"query": q, "films": []}

    cache_key = f"{_fold(raw_q)}:{limit}"
    now_ts = time.time()
    cached = _SEARCH_CACHE.get(cache_key)
    if cached and (now_ts - cached[0] < _SEARCH_CACHE_TTL):
        return {"query": q, "films": cached[1]}

    clean_q = _fold(raw_q)
    words = [w for w in clean_q.split() if w]
    if not words:
        return {"query": q, "films": []}

    exact_phrase = f"%{clean_q}%"
    prefix_phrase = f"{clean_q}%"

    params = [clean_q, prefix_phrase, exact_phrase]
    word_clauses = []
    for w in words:
        idx = len(params) + 1
        params.append(f"%{w}%")
        word_clauses.append(
            f"(unaccent(lower(t.title)) like ${idx} "
            f"or unaccent(lower(coalesce(t.original_title, ''))) like ${idx} "
            f"or unaccent(lower(coalesce(t.director, ''))) like ${idx} "
            f"or unaccent(lower(coalesce(t.cast_list, ''))) like ${idx} "
            f"or unaccent(lower(t.synopsis)) like ${idx})"
        )

    all_words_cond = " and ".join(word_clauses)
    limit_idx = len(params) + 1
    params.append(limit)

    query_sql = f"""
        select {FILM_COLUMNS}
        from titles t
        where (unaccent(lower(t.title)) like $3 or unaccent(lower(coalesce(t.original_title, ''))) like $3)
           or ({all_words_cond})
        order by
            case
                when unaccent(lower(t.title)) = $1 then 1
                when unaccent(lower(t.title)) like $2 then 2
                when unaccent(lower(t.title)) like $3 then 3
                when unaccent(lower(coalesce(t.original_title, ''))) = $1 then 4
                when unaccent(lower(coalesce(t.original_title, ''))) like $2 then 5
                else 6
            end,
            t.popularity desc nulls last,
            t.score desc nulls last,
            t.year desc nulls last
        limit ${limit_idx}
    """

    try:
        rows = await db().fetch(query_sql, *params)
    except Exception:
        # Fallback de contingencia si unaccent no estuviese disponible
        like = f"%{clean_q}%"
        fallback_sql = f"""
            select {FILM_COLUMNS}
            from titles t
            where t.title ilike $1 or coalesce(t.original_title, '') ilike $1
            order by
                case
                    when lower(t.title) = lower($2) then 1
                    when lower(t.title) ilike lower($2 || '%') then 2
                    else 3
                end,
                t.popularity desc nulls last,
                t.score desc nulls last,
                t.year desc nulls last
            limit $3
        """
        rows = await db().fetch(fallback_sql, like, clean_q, limit)

    member_of, seasons_by_group, base_of_group = await _group_maps()
    enr = await _enrichment_map([r["id"] for r in rows])
    seen_groups: set[str] = set()
    seen_cids: set[str] = set()
    out = []
    for r in rows:
        entry, group_id = _grouped_entry(r, member_of, seasons_by_group, base_of_group, enr.get(r["id"]))
        cid = entry["id"].split(":", 1)[-1] if ":" in entry["id"] else entry["id"]
        clean_key = f"{cid.lower()}:{_fold(entry['title'])}"
        if clean_key in seen_cids:
            continue
        seen_cids.add(clean_key)
        if group_id is not None:
            if group_id in seen_groups:
                continue
            seen_groups.add(group_id)
        out.append(entry)

    if len(_SEARCH_CACHE) > 300:
        _SEARCH_CACHE.clear()
    _SEARCH_CACHE[cache_key] = (now_ts, out)
    return {"query": q, "films": out}


def sport_match(record: asyncpg.Record) -> dict:
    """La forma que espera `SportMatch` en la app (agenda de fútbol)."""
    return {
        "id": record["id"],
        "league": {
            "id": record["league_id"],
            "name": record["league_name"] or "",
            "logo": record["league_logo"] or "",
        },
        "home": {
            "id": record["home_id"] or "",
            "name": record["home_name"] or "",
            "short": record["home_abbr"] or "",
            "logo": record["home_logo"] or "",
            "score": record["home_score"],
        },
        "away": {
            "id": record["away_id"] or "",
            "name": record["away_name"] or "",
            "short": record["away_abbr"] or "",
            "logo": record["away_logo"] or "",
            "score": record["away_score"],
        },
        "kickoff": record["kickoff"].isoformat(),
        "status": record["status"],
        "detail": record["status_detail"] or "",
        "venue": record["venue"] or "",
        "channels": list(record["channels"] or []),
        "broadcasts": list(record["broadcasts"] or []),
    }


def _team_tokens(name: str) -> set[str]:
    if not name:
        return set()
    toks = set(_fold(name).split())
    return {t for t in toks if len(t) >= 4 or t in ("psg", "leon", "roma", "ajax", "real", "boca", "cruz")}


async def _enrich_matches_with_epg(matches: list[dict]) -> list[dict]:
    """Cruza en el VPS los partidos en vivo o próximos con epg_programs.

    Promueve el canal que emite el partido a primera posición sin que
    el dispositivo móvil o Smart TV tenga que gastar peticiones HTTP ni CPU.
    """
    if not matches:
        return matches

    active_matches = [m for m in matches if m.get("status") == "in" or m.get("detail")]
    if not active_matches:
        active_matches = matches[:8]
    if not active_matches:
        return matches

    try:
        epg_rows = await db().fetch(
            """
            select channel_name, norm_name, title, description
            from epg_programs
            where end_time >= now() - interval '20 minutes'
              and start_time <= now() + interval '45 minutes'
            """
        )
    except Exception:
        return matches

    if not epg_rows:
        return matches

    norm_to_display = {
        "canal5": "Canal 5",
        "azteca7": "Azteca 7",
        "tudn": "TUDN",
        "foxsports": "Fox Sports",
        "foxsports2": "Fox Sports 2",
        "foxsports3": "Fox Sports 3",
        "foxsportspremium": "Fox Sports Premium",
        "espn": "ESPN",
        "espn2": "ESPN 2",
        "espn3": "ESPN 3",
        "espn4": "ESPN 4",
        "espnextra": "ESPN Extra",
        "espnpremium": "ESPN Premium",
        "clarosports1": "Claro Sports",
        "clarosports2": "Claro Sports 2",
        "dsports": "Directv Sports",
        "winsports": "Win Sports",
        "winsportsplus": "Win Sports+",
    }

    for m in active_matches:
        h_tokens = _team_tokens(m.get("home", {}).get("name", ""))
        a_tokens = _team_tokens(m.get("away", {}).get("name", ""))
        if not h_tokens or not a_tokens:
            continue

        confirmed_channel = None
        for r in epg_rows:
            full_text = _fold(f"{r['title']} {r['description']}")
            full_toks = set(full_text.split())
            if any(t in full_toks for t in h_tokens) and any(t in full_toks for t in a_tokens):
                norm = r["norm_name"]
                confirmed_channel = norm_to_display.get(norm, r["channel_name"])
                break

        if confirmed_channel:
            curr_chans = m.get("channels", [])
            new_chans = [confirmed_channel] + [c for c in curr_chans if _fold(c) != _fold(confirmed_channel)]
            m["channels"] = new_chans
            curr_broad = m.get("broadcasts", [])
            m["broadcasts"] = [confirmed_channel] + [b for b in curr_broad if _fold(b) != _fold(confirmed_channel)]

    return matches


@app.get("/v1/sports")
async def sports(
    date: str | None = Query(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
    days: int = Query(default=1, ge=1, le=8),
    x_api_key: str | None = Header(default=None),
):
    """Agenda de fútbol: partidos del día (hora de México) con sus cadenas
    candidatas. `days` extiende la ventana desde `date` (hoy por defecto)."""
    require_key(x_api_key)
    rows = await db().fetch(
        """
        select id, league_id, league_name, league_logo,
               home_id, home_name, home_abbr, home_logo, home_score,
               away_id, away_name, away_abbr, away_logo, away_score,
               kickoff, status, status_detail, venue, broadcasts, channels
        from sports_matches
        where (kickoff at time zone $1::text)::date
                  >= coalesce($2::text::date, (now() at time zone $1::text)::date)
          and (kickoff at time zone $1::text)::date
                  < coalesce($2::text::date, (now() at time zone $1::text)::date) + $3::int
        order by kickoff
        """,
        SPORTS_TZ,
        date,
        days,
    )
    raw_matches = [sport_match(r) for r in rows]
    enriched = await _enrich_matches_with_epg(raw_matches)
    return {
        "timezone": SPORTS_TZ,
        "date": date,
        "days": days,
        "matches": enriched,
    }


# --- GeeSports V3.8: deportes en vivo (fuente principal de Fútbol y eventos) ------
# El worker `sync/gee38.py` baja, descifra y sondea el CMS de GeeSports y deja el
# catálogo ya resuelto en `gee38_catalog`; aquí solo se sirve. Cada enlace trae
# `link` (URL con `|cabeceras`), `url`, `headers`, `type` (hls|dash), `drm`
# ("kid:key" → ClearKey W3C en la app) y `alive`/`checked_at` del sondeo.


def _gee_payload(value):
    """asyncpg puede devolver jsonb como str; lo normaliza a dict."""
    if isinstance(value, str):
        try:
            return json.loads(value)
        except Exception:  # noqa: BLE001
            return {}
    return value or {}


def _gee_logo(url: str) -> str:
    """Sofascore en `http://` no carga en Android (cleartext bloqueado): a https.

    `api.sofascore.com` también vive en `img.sofascore.com` (verificado en vivo).
    """
    if not url:
        return url
    if url.startswith("http://api.sofascore.com/") or url.startswith("http://img.sofascore.com/"):
        return "https://img.sofascore.com/" + url.split("sofascore.com/", 1)[1]
    if url.startswith("http://"):
        return "https://" + url[7:]
    return url


def _gee_fix_logos(event: dict) -> None:
    for key in ("logo", "flag_a", "flag_b", "league_logo", "home_logo", "away_logo"):
        value = event.get(key)
        if isinstance(value, str):
            event[key] = _gee_logo(value)


def _gee_fix_event_times(event: dict) -> None:
    """El CMS marca los horarios en UTC: a hora de México (para mostrar y ordenar)."""
    day = (event.get("date_iso") or "").strip()
    if not day:
        return
    parts = (event.get("time") or "").strip().split(":")
    try:
        parsed_day = date.fromisoformat(day)
        hour = int(parts[0]) if parts and parts[0] else 0
        minute = int(parts[1]) if len(parts) > 1 else 0
    except (ValueError, IndexError):
        return
    utc = datetime(parsed_day.year, parsed_day.month, parsed_day.day, hour, minute, tzinfo=ZoneInfo("UTC"))
    local = utc.astimezone(ZoneInfo(SPORTS_TZ))
    event["date_iso"] = local.date().isoformat()
    event["time"] = local.strftime("%H:%M:%S")


@app.get("/v1/gee38/sports")
async def gee38_sports(x_api_key: str | None = Header(default=None)):
    """Grupos de canales deportivos 24/7 (TNT Sports, FOX, Sky, LaLiga TV…)."""
    require_key(x_api_key)
    rows = await db().fetch(
        "select payload, updated_at from gee38_catalog where kind = 'group' order by sort, name"
    )
    meta = await db().fetchrow("select v, updated_at from gee38_meta where k = 'app'")
    stamp = max((r["updated_at"] for r in rows), default=None)
    if stamp is None and meta:
        stamp = meta["updated_at"]
    groups = []
    for row in rows:
        group = _gee_payload(row["payload"])
        group["logo"] = _gee_logo(group.get("logo") or "")
        for channel in group.get("channels") or []:
            channel["logo"] = _gee_logo(channel.get("logo") or "")
        groups.append(group)
    return {
        "app": _gee_payload(meta["v"]) if meta else None,
        "updatedAt": stamp.isoformat() if stamp else None,
        "groups": groups,
    }


@app.get("/v1/gee38/events")
async def gee38_events(
    category: str | None = Query(default=None, max_length=40),
    day: str | None = Query(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
    days: int = Query(default=0, ge=0, le=30),
    x_api_key: str | None = Header(default=None),
):
    """Eventos deportivos (fútbol, básquet, F1…). `day`(+`days`) acota por fecha
    del evento; `category` filtra por deporte (p. ej. Football)."""
    require_key(x_api_key)
    where = ["kind = 'event'"]
    args: list = []
    if category:
        args.append(category.strip())
        where.append(f"category ilike ${len(args)}")
    if day:
        try:
            first = date.fromisoformat(day)
        except ValueError:
            raise HTTPException(status_code=400, detail="fecha inválida")
        args.append(first.isoformat())
        if days > 0:
            args.append((first + timedelta(days=days)).isoformat())
            where.append(
                f"payload->>'date_iso' >= ${len(args) - 1}"
                f" and payload->>'date_iso' < ${len(args)}"
            )
        else:
            where.append(f"payload->>'date_iso' = ${len(args)}")
    rows = await db().fetch(
        f"select id, payload from gee38_catalog where {' and '.join(where)} order by sort",
        *args,
    )
    events = []
    for row in rows:
        event = _gee_payload(row["payload"])
        # El payload nuevo ya trae su id (lo escribe el worker); el viejo se rellena aquí
        # con el id de la fila para que la app jamás caiga al respaldo por nombre|fecha|hora.
        if not str(event.get("id") or "").strip():
            event["id"] = row["id"]
        events.append(event)
    for event in events:
        _gee_fix_event_times(event)
        _gee_fix_logos(event)
    if not events:
        return {"events": events}
    entries = await _gee_window_matches(events)

    # La agenda del fútbol se arma con TODOS los partidos del calendario (así no se
    # pierde ningún Liga MX aunque GeeSports no lo liste) y a cada partido se le
    # cruzan los enlaces que GeeSports recomienda para ese mismo juego. Solo se
    # sirven los deportes principales y las ligas principales (+ MX y ascenso).
    # La ventana cubre la semana (Hoy, Mañana, … +7) para las pestañas por día.
    today = date.today()
    near = {(today + timedelta(days=delta)).isoformat() for delta in range(-1, 8)}
    links_by_match: dict[str, list[dict]] = {}
    # Los canales que la fuente DECLARA para cada partido, aunque todavía no publique su URL
    # (su lista de nombres va completa: «TUDN US», «UNIVISION HD»…). La app los enseña como la
    # original y, al tocarlos, se resuelven solos con el catálogo de la casa (respaldo automático).
    declared_by_match: dict[str, list[str]] = {}
    # TODOS los enlaces del cruce (mismo par de equipos, cualquier fecha): el CMS
    # publica varias entradas por partido y la lista completa puede vivir en otra
    # («Everton vs Chelsea»: la de hoy traía 1 enlace muerto; su hermana visible
    # traía 10 con 6 vivos — la app original enseña esa lista). Aquí se fusionan
    # también en el API para que la tarjeta del calendario nunca salga pobre.
    pair_links: dict[str, list[dict]] = {}
    out: list[dict] = []
    for event in events:
        category = (event.get("category") or "").strip().lower()
        if category not in GEE_ALLOWED_SPORTS:
            continue
        pair = _gee_pair_key(event.get("team_a", ""), event.get("team_b", ""))
        if pair:
            bucket = pair_links.setdefault(pair, [])
            known = {link.get("url") or link.get("link") for link in bucket}
            for link in event.get("links") or []:
                key = link.get("url") or link.get("link")
                if key and key not in known:
                    bucket.append(link)
                    known.add(key)
        is_football = category == "football"
        match = _gee_find_match(event, entries) if is_football else None
        if match is not None:
            if not _gee_league_allowed_match(match):
                continue  # liga no principal: ni tarjeta ni evento
            _gee_apply_match(event, match)
            if _gee_date_of(match.get("kickoff")) in near:
                match_id = str(match.get("id") or "")
                bucket = links_by_match.setdefault(match_id, [])
                known = {link.get("url") or link.get("link") for link in bucket}
                for link in event.get("links") or []:
                    key = link.get("url") or link.get("link")
                    if key not in known:
                        bucket.append(link)
                        known.add(key)
                names = declared_by_match.setdefault(match_id, [])
                for name in event.get("link_names") or []:
                    clean = str(name or "").strip()
                    if clean and clean not in names:
                        names.append(clean)
                continue  # el partido se sirve como tarjeta única (abajo)
            if _gee_league_allowed_event(event):
                out.append(event)
            continue
        if is_football and not _gee_league_allowed_event(event):
            continue
        out.append(event)
    for match, _home, _away, _dt in entries:
        if not _gee_league_allowed_match(match):
            continue
        if _gee_date_of(match.get("kickoff")) not in near:
            continue
        match_id = str(match.get("id") or "")
        card_links = list(links_by_match.get(match_id, []))
        home = (match.get("home") or {}).get("name") or ""
        away = (match.get("away") or {}).get("name") or ""
        extra = pair_links.get(_gee_pair_key(home, away), [])
        if extra:
            # Sin duplicados por URL; si la copia del cruce viene sondeada y la primera
            # no, se queda la sondeada (los badges de la app son del momento).
            index = {(link.get("url") or link.get("link")): i for i, link in enumerate(card_links)}
            for link in extra:
                key = link.get("url") or link.get("link")
                if not key:
                    continue
                previous = index.get(key)
                if previous is None:
                    index[key] = len(card_links)
                    card_links.append(link)
                elif card_links[previous].get("alive") is None and link.get("alive") is not None:
                    card_links[previous] = link
        card_links.sort(key=_gee_link_rank)  # vivos primero (estable dentro del grupo)
        out.append(_gee_match_card(match, card_links, declared_by_match.get(match_id, [])))
    # Una tarjeta por id: la app las pinta con `key = id` y un duplicado revienta la lista.
    deduped: list[dict] = []
    seen_ids: set[str] = set()
    for card in out:
        card_id = str(card.get("id") or "")
        if card_id:
            if card_id in seen_ids:
                continue
            seen_ids.add(card_id)
        deduped.append(card)
    return {"events": deduped}


@app.post("/v1/gee38/feedback")
async def gee38_feedback(request: Request, x_api_key: str | None = Header(default=None)):
    """La app reporta un enlace caído: se marca al instante (el worker lo
    re-sondea y ordena en su siguiente ciclo)."""
    require_key(x_api_key)
    raw = await request.body()
    if len(raw) > 8 * 1024:
        raise HTTPException(status_code=413, detail="reporte demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="json inválido")
    url = str(body.get("url") or "").strip()
    if not url:
        raise HTTPException(status_code=400, detail="url requerida")
    alive = body.get("alive")
    rows = await db().fetch("select id, payload from gee38_catalog")
    for row in rows:
        payload = _gee_payload(row["payload"])
        hit = False

        def _mark(links):  # noqa: ANN001
            nonlocal hit
            for link in links or []:
                if link.get("url") == url or link.get("link") == url:
                    link["alive"] = None if alive is None else bool(alive)
                    link["reported_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
                    hit = True

        for channel in payload.get("channels") or []:
            _mark(channel.get("links"))
        _mark(payload.get("links"))
        if hit:
            await db().execute(
                "update gee38_catalog set payload = $2::jsonb where id = $1",
                row["id"],
                json.dumps(payload, ensure_ascii=False),
            )
            return {"ok": True, "updated": 1}
    return {"ok": True, "updated": 0}


# --- Cruce de los eventos GeeSports con la agenda (marcadores en vivo) ------------
# La app muestra una sola fuente (GeeSports); los marcadores, logos finos y el
# estado en vivo llegan de la agenda ESPN ya sincronizada en este VPS (cada 10
# min) y se cruzan aquí, en una sola pasada: el evento sale completo y el
# teléfono/TV no gasta CPU ni peticiones extra. El cruce es por equipos (con
# alias del portal: Chivas = Guadalajara…) y cercanía de horario.

# Palabras que no identifican a un equipo (sí a sociedades anónimas).
GEE_TEAM_STOP = {
    "club", "fc", "cf", "cd", "cs", "ca", "sc", "ac", "afc", "sad",
    "la", "el", "los", "las", "de", "del", "y", "the", "and", "vs", "at",
}

# Alias que usa el CMS vs los nombres de la agenda.
GEE_TEAM_ALIASES = {
    "guadalajara": {"chivas"},
    "chivas": {"guadalajara"},
    "tijuana": {"xolos"},
    "xolos": {"tijuana"},
    "monterrey": {"rayados"},
    "rayados": {"monterrey"},
    "uanl": {"tigres"},
    "tigres": {"uanl"},
    "mexico": {"mex"},
    "mex": {"mexico"},
}


def _gee_tokens(text: str) -> set[str]:
    """Tokens significativos de un nombre de equipo (corta < 4 salvo los clásicos)."""
    out: set[str] = set()
    for token in _fold(text).split():
        if token in GEE_TEAM_STOP:
            continue
        if len(token) >= 4 or token in ("psg", "leon", "roma", "ajax", "boca", "cruz"):
            out.add(token)
    return out


def _gee_expand(tokens: set[str]) -> set[str]:
    out = set(tokens)
    for token in tokens:
        out |= GEE_TEAM_ALIASES.get(token, set())
    return out


def _gee_dt(iso: str | None) -> datetime | None:
    if not iso:
        return None
    try:
        value = datetime.fromisoformat(iso)
    except ValueError:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=ZoneInfo(SPORTS_TZ))
    return value.astimezone(ZoneInfo(SPORTS_TZ))


def _gee_event_dt(event: dict) -> datetime | None:
    day = (event.get("date_iso") or "").strip()
    if not day:
        return None
    try:
        parsed_day = date.fromisoformat(day)
    except ValueError:
        return None
    parts = (event.get("time") or "").strip().split(":")
    try:
        hour = int(parts[0]) if parts and parts[0] else 0
        minute = int(parts[1]) if len(parts) > 1 else 0
    except ValueError:
        hour, minute = 0, 0
    return datetime(parsed_day.year, parsed_day.month, parsed_day.day, hour, minute, tzinfo=ZoneInfo(SPORTS_TZ))


def _gee_match_entry(match: dict) -> tuple[dict, set[str], set[str], datetime | None]:
    return (
        match,
        _gee_expand(_gee_tokens((match.get("home") or {}).get("name") or "")),
        _gee_expand(_gee_tokens((match.get("away") or {}).get("name") or "")),
        _gee_dt(match.get("kickoff")),
    )


def _gee_find_match(event: dict, entries: list[tuple]) -> dict | None:
    """El partido de la agenda que corresponde al evento (equipos + horario).

    Fuerte = ambos equipos caben en el texto del evento; débil = al menos un
    token por equipo; el horario (36 h de margen) desempata."""
    name = event.get("event") or ""
    team_a = (event.get("team_a") or "").strip()
    team_b = (event.get("team_b") or "").strip()
    if not team_a or not team_b or _fold(team_a) == _fold(team_b):
        return None
    if _fold(team_a) == _fold(name) or _fold(team_b) == _fold(name):
        return None  # el CMS repite la liga: no hay equipos reales que cruzar
    hay = _gee_expand(_gee_tokens(f"{name} {team_a} {team_b}"))
    event_dt = _gee_event_dt(event)
    best: dict | None = None
    best_score = float("-inf")
    for match, home, away, match_dt in entries:
        if not home or not away:
            continue
        strong = home <= hay and away <= hay
        shared = len(home & hay) + len(away & hay)
        if not strong and shared < 2:
            continue
        dist_min = None
        if event_dt and match_dt:
            dist_min = abs((event_dt - match_dt).total_seconds()) / 60.0
            if dist_min > 36 * 60:
                continue
        score = (1000.0 if strong else 0.0) + shared * 10.0
        if dist_min is not None:
            score -= min(dist_min, 600.0) / 10.0
        if score > best_score:
            best_score = score
            best = match
    return best


def _gee_apply_match(event: dict, match: dict) -> dict:
    """Adjunta al evento el marcador/logos/estado del partido que le corresponde."""
    home = match.get("home") or {}
    away = match.get("away") or {}
    league = match.get("league") or {}
    status = (match.get("status") or "").strip()
    event["score"] = {
        "home": home.get("score"),
        "away": away.get("score"),
        "status": status,
        "detail": (match.get("detail") or "").strip(),
        "live": status == "in",
    }
    event["league_label"] = league.get("name") or ""
    event["league_logo"] = league.get("logo") or ""
    event["home_name"] = home.get("name") or event.get("team_a") or ""
    event["away_name"] = away.get("name") or event.get("team_b") or ""
    event["home_logo"] = home.get("logo") or event.get("flag_a") or ""
    event["away_logo"] = away.get("logo") or event.get("flag_b") or ""
    # El horario del calendario es el fino (el CMS marca UTC): manda el partido.
    kickoff = _gee_dt(match.get("kickoff"))
    if kickoff is not None:
        event["date_iso"] = kickoff.date().isoformat()
        event["time"] = kickoff.strftime("%H:%M:%S")
        event["date"] = kickoff.strftime("%d/%m/%Y")
    return event


def _gee_enrich_event(event: dict, entries: list[tuple]) -> dict:
    """Adjunta al evento el marcador/logos/estado del partido que le corresponde."""
    if (event.get("category") or "").strip().lower() != "football":
        return event
    match = _gee_find_match(event, entries)
    if not match:
        return event
    return _gee_apply_match(event, match)


def _gee_date_of(iso: str | None) -> str:
    value = _gee_dt(iso)
    return value.date().isoformat() if value else ""


def _gee_pair_key(team_a: str, team_b: str) -> str:
    """Clave del cruce (par de equipos, sin importar el orden); vacía si no hay par.

    El CMS publica VARIAS entradas por partido («Everton vs Chelsea» del 17-oct con
    1 enlace muerto y la del 13-oct —visible— con 10, 6 vivos): la lista buena puede
    vivir en otra entrada del mismo cruce, así que se agrupan por esta clave."""
    a, b = _fold(team_a), _fold(team_b)
    if not a or not b or a == b:
        return ""
    return "|".join(sorted((a, b)))


_ALIVE_RANK = {True: 0, None: 1, False: 2}


def _gee_link_rank(link: dict) -> int:
    """Vivos primero (el orden se conserva dentro de cada grupo: estable)."""
    return _ALIVE_RANK.get(link.get("alive"), 1)


# Familias de cadena: señales hermanas que sí pueden traer el mismo partido
# (la parrilla oficial dice «Fox Sports» y el enlace se llama «FOX DEPORTES»…).
GEE_CHANNEL_FAMILIES = {
    "foxsports": {"foxdeportes", "foxone"},
    "foxdeportes": {"foxsports", "foxone"},
    "foxone": {"foxsports", "foxdeportes"},
    "espn": {"espnmexico"},
    "espnmexico": {"espn"},
    "tudn": {"tudnusa"},
    "tudnusa": {"tudn"},
    "beinsports": {"bein1sports", "bein2sports", "bein3sports"},
    "bein1sports": {"beinsports", "bein2sports"},
    "bein2sports": {"beinsports", "bein1sports"},
    "bein3sports": {"beinsports", "bein1sports"},
    # «Caliente TV» se convirtió en «FOX One» en México (Fox Corporation): misma señal.
    "caliente": {"foxone"},
    "calientesports": {"foxone"},
    "foxone": {"caliente", "calientesports", "foxsports"},
}


def _gee_channel_family(canonical: str) -> set[str]:
    out = {canonical}
    out |= GEE_CHANNEL_FAMILIES.get(canonical, set())
    out.discard("")
    return out


def _gee_broadcast_filter(links: list[dict], match: dict) -> list[dict]:
    """Ordena los enlaces por la parrilla oficial del partido (sin dejar sin opciones).

    La agenda (ESPN/LiveSoccerTV) trae los canales que lo transmiten —en México
    y fuera—, pero esa lista es incompleta y cambia entre fuentes: por eso ya no
    se descarta nada. Se ordena por niveles: primero las cadenas de la parrilla
    (con sus señales hermanas: FOX Deportes≈Fox Sports, TUDN USA≈TUDN…), luego
    las señales neutrales (agregadores tipo «HD QUALITY») y al final las demás
    marcas — el usuario siempre ve la mejor opción arriba y no se queda sin nada."""
    official: set[str] = set()
    for name in list(match.get("broadcasts") or []) + list(match.get("channels") or []):
        official |= _gee_channel_family(canonical_channel(name or ""))
    official.discard("")
    if not official or not links:
        return links

    def _tier(link: dict) -> int:
        canonical = canonical_channel(link.get("name") or "")
        if _gee_channel_family(canonical) & official:
            return 0
        if canonical not in GEE_BRAND_CANONICALS:
            return 1
        return 2

    return sorted(links, key=_tier)  # estable: dentro de cada nivel manda el sondeo


# Marcas de televisión reconocidas: si la parrilla oficial no las incluye, no se
# ofrecen (estarían dando otra cosa). Los agregadores sin marca sí se conservan.
GEE_BRAND_CANONICALS = {
    "mx", "canal5", "azteca7", "lasestrellas", "foxsports", "foxdeportes", "foxone",
    "espn", "espn2", "espn3", "espn4", "espn5", "espndeportes", "espnpremium",
    "tudn", "tudnusa", "tudncom", "tudnapp", "vix", "universo", "network",
    "cbs", "cbssports", "cbssn", "nbc", "nbcports", "paramount", "star", "max",
    "dazn", "daznsports", "skysports", "sky", "tntsports", "tntsports1",
    "tntsports2", "tntsports3", "tntsports4", "sportv", "sportvbr",
    "fubosports", "onesoccer", "tycsports", "win", "claro", "movistar",
    "directv", "beinsports", "bein1sports", "bein2sports", "bein3sports",
    "fandango", "premiere", "premierer", "espnstar", "starsports", "supersport",
}


# Solo las ligas principales de fútbol: la MX, el ascenso mexicano y las grandes.
GEE_LEAGUE_IDS = {
    "mex.1", "mex.2", "eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "por.1",
    "uefa.champions", "uefa.europa", "conmebol.libertadores", "conmebol.sudamericana",
    "arg.1", "bra.1", "usa.1", "fifa.world",
}

# Nombres (normalizados) que se aceptan cuando el evento no se cruzó con la agenda.
GEE_LEAGUE_NAMES = {
    "liga mx", "liga de expansion mx", "uefa champions league", "champions league",
    "premier league", "laliga", "serie a", "bundesliga", "ligue 1",
    "liga profesional de futbol", "liga profesional argentina", "brasileirao",
    "campeonato brasileiro serie a", "copa libertadores", "copa sudamericana",
    "mls", "major league soccer", "eredivisie", "vriendenloterij eredivisie",
    "primeira liga", "liga portugal", "fifa world cup", "copa mundial",
}

# Los deportes que sí se muestran (los demás no se sirven).
GEE_ALLOWED_SPORTS = {
    "football", "basketball", "american football", "motorsports", "baseball", "tennis",
}


def _gee_norm_league(text: str) -> str:
    """Nombre de liga comparable: sin tildes, sin la cola («Liga MX, Apertura»)."""
    lowered = unicodedata.normalize("NFD", (text or "").lower())
    lowered = "".join(c for c in lowered if unicodedata.category(c) != "Mn")
    lowered = lowered.split(",")[0]
    return re.sub(r"[^a-z0-9]+", " ", lowered).strip()


def _gee_league_allowed_match(match: dict) -> bool:
    league = match.get("league") or {}
    return (league.get("id") or "") in GEE_LEAGUE_IDS


def _gee_league_allowed_event(event: dict) -> bool:
    label = event.get("league_label") or event.get("event") or ""
    return _gee_norm_league(label) in GEE_LEAGUE_NAMES


def _gee_match_card(match: dict, links: list[dict], declared: list[str] | None = None) -> dict:
    """La tarjeta de un partido del calendario (con los enlaces de GeeSports cruzados).

    Misma forma que un evento: la app la pinta igual (escudos, marcador real y hasta
    `links` para el selector de transmisiones). Los enlaces se ordenan contra la
    parrilla oficial y la tarjeta lleva esa parrilla (`broadcasts`/`guide`): la app
    la usa como guía («se transmite en…») y para ofrecer los canales de TV reales.

    [declared] son los nombres que la fuente declara para el partido aunque todavía no
    publique su URL («TUDN US», «UNIVISION HD»…). Se entregan como ranuras sin dirección para
    que la app muestre la lista COMPLETA (como la app original) y, al tocarlas, se resuelvan
    solas con el canal 100 % confirmado del catálogo de la casa (respaldo automático).
    """
    links = _gee_broadcast_filter(links, match)
    served = {str(link.get("name") or "").strip().lower() for link in links}
    for name in declared or []:
        clean = str(name or "").strip()
        if not clean or clean.lower() in served:
            continue
        served.add(clean.lower())
        links.append({
            "name": clean,
            "link": "",
            "url": "",
            "headers": {},
            "scheme": 0,
            "drm": "",
            "type": "hls",
            "alive": None,
            "checked_at": None,
            "pending": True,  # la fuente aún no publica su dirección: se resuelve en la app
        })
    home = match.get("home") or {}
    away = match.get("away") or {}
    league = match.get("league") or {}
    kickoff = _gee_dt(match.get("kickoff"))
    status = (match.get("status") or "").strip()
    alive = sum(1 for link in links if link.get("alive"))
    guide: list[str] = []
    for name in list(match.get("broadcasts") or []) + list(match.get("channels") or []):
        clean = (name or "").strip()
        if clean and clean not in guide:
            guide.append(clean)
    return {
        "id": "espn:" + str(match.get("id") or ""),
        "category": "Football",
        "event": (league.get("name") or "").strip() or "Fútbol",
        "logo": league.get("logo") or "",
        "team_a": home.get("name") or "",
        "team_b": away.get("name") or "",
        "date_iso": kickoff.date().isoformat() if kickoff else "",
        "date": kickoff.strftime("%d/%m/%Y") if kickoff else "",
        "time": kickoff.strftime("%H:%M:%S") if kickoff else "",
        "visible": True,
        "priority": 0,
        "link_names": [link.get("name") or "" for link in links],
        "links": links,
        "alive": alive,
        "total": len(links),
        "home_name": home.get("name") or "",
        "away_name": away.get("name") or "",
        "home_logo": home.get("logo") or "",
        "away_logo": away.get("logo") or "",
        "league_label": league.get("name") or "",
        "league_logo": league.get("logo") or "",
        "league_id": league.get("id") or "",
        "broadcasts": guide[:8],
        "guide": guide[:8],
        "score": {
            "home": home.get("score"),
            "away": away.get("score"),
            "status": status,
            "detail": (match.get("detail") or "").strip(),
            "live": status == "in",
        },
    }


async def _gee_window_matches(events: list[dict]) -> list[tuple]:
    """La agenda (ESPN) de la ventana de fechas de los eventos, una sola consulta."""
    days = sorted({(e.get("date_iso") or "") for e in events if e.get("date_iso")})
    if not days:
        return []
    today = date.today()
    lo = max(days[0], (today - timedelta(days=3)).isoformat())
    hi = min(days[-1], (today + timedelta(days=10)).isoformat())
    if lo > hi:
        return []
    try:
        rows = await db().fetch(
            """
            select id, league_id, league_name, league_logo,
                   home_id, home_name, home_abbr, home_logo, home_score,
                   away_id, away_name, away_abbr, away_logo, away_score,
                   kickoff, status, status_detail, venue, broadcasts, channels
            from sports_matches
            where (kickoff at time zone $1::text)::date between $2::text::date and $3::text::date
            """,
            SPORTS_TZ,
            lo,
            hi,
        )
        return [_gee_match_entry(sport_match(record)) for record in rows]
    except Exception:  # noqa: BLE001 - sin agenda, los eventos salen sin marcador
        return []


NOISE_WORDS = {
    "fhd", "uhd", "hd", "sd", "4k", "24", "7", "vip", "latino", "latam",
    "espana", "mexico", "mex", "colombia", "col", "chile", "cl", "argentina", "ar",
    "es", "pe", "rd", "gt", "sv", "ni", "hn", "pa", "uy", "central", "usa", "us",
    "plus", "east", "west", "pc", "tv", "de", "del", "la", "el", "los", "las", "en"
}


def canonical_channel(text: str) -> str:
    """Normaliza un nombre o código de canal a un token canónico."""
    if not text:
        return ""
    t = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn").lower()
    t = re.sub(r"\(.*?\)|\[.*?\]", " ", t)
    t = re.sub(r"[^a-z0-9]+", " ", t)
    tokens = [tok for tok in t.split() if tok]
    if not tokens:
        return ""

    if any(tok in ("estrellas", "lasestrellas") for tok in tokens) or ("2" in tokens and ("canal" in tokens or "xew" in tokens)):
        return "lasestrellas"
    if "azteca" in tokens:
        if any(tok in ("1", "uno", "un") for tok in tokens):
            return "azteca1"
        if "7" in tokens or "siete" in tokens:
            return "azteca7"
        if "deportes" in tokens:
            return "aztecadeportes"
        if "noticias" in tokens or "adn" in tokens or "40" in tokens:
            return "adn40"
        return "azteca"
    if "tudn" in tokens:
        return "tudn"
    if "cinemax" in tokens:
        return "cinemax"
    if "warner" in tokens:
        return "warner"
    if "sony" in tokens:
        return "sony"
    if "hbo" in tokens:
        if "family" in tokens:
            return "hbofamily"
        if "plus" in tokens:
            return "hboplus"
        if "mundi" in tokens:
            return "hbomundi"
        if "pop" in tokens:
            return "hbopop"
        if "xtreme" in tokens:
            return "hboxtreme"
        if "signature" in tokens:
            return "hbosignature"
        if "2" in tokens:
            return "hbo2"
        return "hbo"
    if "fox" in tokens and "sports" in tokens:
        if "premium" in tokens:
            return "foxsportspremium"
        if "2" in tokens:
            return "foxsports2"
        if "3" in tokens:
            return "foxsports3"
        return "foxsports"
    if "espn" in tokens:
        if "2" in tokens:
            return "espn2"
        if "3" in tokens:
            return "espn3"
        if "4" in tokens:
            return "espn4"
        if "5" in tokens:
            return "espn5"
        if "extra" in tokens:
            return "espnextra"
        if "premium" in tokens:
            return "espnpremium"
        return "espn"
    if "claro" in tokens and "sports" in tokens:
        if "2" in tokens:
            return "clarosports2"
        return "clarosports1"
    if "directv" in tokens or "dsports" in tokens:
        if "2" in tokens:
            return "dsports2"
        if "plus" in tokens:
            return "dsportsplus"
        return "dsports"
    if "win" in tokens and "sports" in tokens:
        if "plus" in tokens or "online" in tokens:
            return "winsportsplus"
        return "winsports"
    if "5" in tokens and ("canal" in tokens or "xhgc" in t or "xhpnh" in t):
        return "canal5"
    if "9" in tokens and ("canal" in tokens or "xeq" in t):
        return "canal9"
    if "11" in tokens and ("canal" in tokens or "once" in tokens):
        return "canal11"
    if "14" in tokens and "canal" in tokens:
        return "canal14"
    if "22" in tokens and "canal" in tokens:
        return "canal22"
    if "tvc" in tokens and "deportes" in tokens:
        return "tvcdeportes"

    filtered = [tok for tok in tokens if tok not in NOISE_WORDS]
    if len(filtered) > 1 and filtered[0] == "canal":
        filtered = filtered[1:]
    return "".join(filtered) if filtered else "".join(tokens)


@app.get("/v1/epg")
async def epg(
    channel: str | None = Query(default=None),
    code: str | None = Query(default=None),
    x_api_key: str | None = Header(default=None),
):
    """Guía de programación (EPG) para TV en vivo.

    Devuelve el programa en curso ("AHORA") y los siguientes para el canal
    solicitado, con marcas de tiempo en milisegundos de época.
    """
    require_key(x_api_key)
    norm = canonical_channel(channel or "") or canonical_channel(code or "")
    if not norm:
        return {"channel": channel or "", "norm": "", "programs": []}

    rows = await db().fetch(
        """
        select title, description, start_time, end_time
        from epg_programs
        where norm_name = $1
          and end_time >= now() - interval '20 minutes'
        order by start_time asc
        limit 12
        """,
        norm,
    )
    if not rows and len(norm) >= 3:
        rows = await db().fetch(
            """
            select title, description, start_time, end_time
            from epg_programs
            where (norm_name like '%' || $1 || '%' or $1 like '%' || norm_name || '%')
              and end_time >= now() - interval '20 minutes'
            order by start_time asc
            limit 12
            """,
            norm,
        )

    programs = [
        {
            "name": r["title"],
            "description": r["description"] or "",
            "start": r["start_time"].isoformat(),
            "end": r["end_time"].isoformat(),
            "startMs": int(r["start_time"].timestamp() * 1000),
            "endMs": int(r["end_time"].timestamp() * 1000),
        }
        for r in rows
    ]
    return {
        "channel": channel or "",
        "norm": norm,
        "programs": programs,
    }


# ----- diagnóstico de campo (Fase 1) -----
# La app reporta crashes/ANRs/anomalías (sin tokens ni datos personales) y el worker
# `diag.py` avisa de huellas nuevas y autocierra las resueltas.

DIAG_MAX_BODY = 256 * 1024  # 256 KB por reporte
DIAG_RATE_LIMIT = 60        # reportes por minuto por IP
_diag_bucket: dict[str, list[float]] = {}


def _diag_rate_ok(bucket: dict[str, list[float]], ip: str, now: float,
                  limit: int = DIAG_RATE_LIMIT, window: float = 60.0) -> bool:
    """Ventana deslizante en memoria: True si la IP puede enviar otro reporte."""
    hits = [t for t in bucket.get(ip, []) if now - t < window]
    if len(hits) >= limit:
        bucket[ip] = hits
        return False
    hits.append(now)
    bucket[ip] = hits
    if len(bucket) > 2000:  # higiene: sin fugas de memoria por IPs efímeras
        for key in [k for k, v in bucket.items() if not v or now - v[-1] > window]:
            bucket.pop(key, None)
    return True


def _clean_diag_text(value, limit: int) -> str:
    """Texto corto y seguro para la base (sin NUL ni campos gigantes)."""
    text = str(value or "").strip().replace("\x00", "")
    return text[:limit]


@app.post("/v1/diag")
async def diag_report(request: Request, x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    raw = await request.body()
    if len(raw) > DIAG_MAX_BODY:
        raise HTTPException(status_code=413, detail="reporte demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="JSON inválido")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="JSON inválido")
    ip = request.client.host if request.client else "?"
    if not _diag_rate_ok(_diag_bucket, ip, time.time()):
        raise HTTPException(status_code=429, detail="demasiados reportes")
    kind = _clean_diag_text(body.get("kind"), 16).lower() or "anomaly"
    if kind not in ("crash", "anr", "anomaly", "feedback"):
        kind = "anomaly"
    payload = body.get("payload") if isinstance(body.get("payload"), dict) else {}
    await db().execute(
        """
        insert into diag_reports (fingerprint, kind, code, app_version, version_code,
                                  device, android, payload)
        values ($1, $2, $3, $4, $5, $6, $7, $8)
        """,
        _clean_diag_text(body.get("fingerprint"), 64) or "sin-huella",
        kind,
        _clean_diag_text(body.get("code"), 64),
        _clean_diag_text(body.get("appVersion"), 24),
        int(body.get("versionCode") or 0),
        _clean_diag_text(body.get("device"), 80),
        _clean_diag_text(body.get("android"), 40),
        json.dumps(payload, ensure_ascii=False)[:20000],
    )
    return {"ok": True}


@app.get("/v1/diag/summary")
async def diag_summary(
    days: int = Query(default=30, ge=1, le=365),
    limit: int = Query(default=60, ge=1, le=200),
    x_api_key: str | None = Header(default=None),
):
    require_key(x_api_key)
    rows = await db().fetch(
        """
        select fingerprint,
               (array_agg(kind order by created_at desc))[1] as kind,
               (array_agg(code order by created_at desc))[1] as code,
               (array_agg(app_version order by created_at desc))[1] as latest_version,
               (array_agg(version_code order by created_at desc))[1] as latest_code,
               count(*) as hits,
               count(distinct device) as devices,
               min(created_at) as first_seen,
               max(created_at) as last_seen,
               (array_agg(payload order by created_at desc))[1] as sample
        from diag_reports
        where created_at > now() - make_interval(days => $1)
        group by fingerprint
        order by max(created_at) desc
        limit $2
        """,
        days,
        limit,
    )
    now = time.time()
    reports = []
    for r in rows:
        last = r["last_seen"].timestamp() if r["last_seen"] else 0.0
        sample = r["sample"]
        if isinstance(sample, str):
            try:
                sample = json.loads(sample)
            except Exception:  # noqa: BLE001
                sample = {}
        reports.append({
            "fingerprint": r["fingerprint"],
            "kind": r["kind"],
            "code": r["code"],
            "latestVersion": r["latest_version"],
            "latestVersionCode": r["latest_code"],
            "hits": r["hits"],
            "devices": r["devices"],
            "firstSeen": r["first_seen"].isoformat() if r["first_seen"] else None,
            "lastSeen": r["last_seen"].isoformat() if r["last_seen"] else None,
            # Sin reportes en 48 h: el fix salió o el problema dejó de ocurrir.
            "resolved": (now - last) > 48 * 3600,
            "sample": sample,
        })
    return {"reports": reports}


# ----- configuración remota (Fase 2) -----
# Flags que la app lee al arrancar (TTL 6 h) para mitigar problemas sin publicar OTA.
# Escritura protegida por CONFIG_ADMIN_KEY (la API_KEY viaja en el APK: no basta).


def _config_admin_ok(header: str | None, expected: str) -> bool:
    """True si el header coincide con la llave de administración configurada."""
    return bool(expected) and bool(header) and hmac.compare_digest(header, expected)


@app.get("/v1/config")
async def remote_config(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    rows = await db().fetch("select key, value, updated_at from remote_config")
    flags = {}
    for row in rows:
        value = row["value"]
        if isinstance(value, str):
            try:
                value = json.loads(value)
            except Exception:  # noqa: BLE001
                pass
        flags[row["key"]] = value
    version = int(max((r["updated_at"].timestamp() for r in rows), default=0))
    return {"version": version, "flags": flags}


@app.post("/v1/config")
async def set_remote_config(request: Request, x_admin_key: str | None = Header(default=None)):
    if not _config_admin_ok(x_admin_key, CONFIG_ADMIN_KEY):
        raise HTTPException(status_code=403, detail="llave de administración inválida")
    raw = await request.body()
    if len(raw) > 16 * 1024:
        raise HTTPException(status_code=413, detail="config demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="JSON inválido")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="JSON inválido")
    for key, value in body.items():
        await db().execute(
            """
            insert into remote_config (key, value, updated_at) values ($1, $2, now())
            on conflict (key) do update set value = excluded.value, updated_at = now()
            """,
            _clean_diag_text(key, 64),
            json.dumps(value, ensure_ascii=False),
        )
    return {"ok": True}


# ----- pool de identidades del portal (réplica del modelo Hydra) -----
# El tenant no incorpora dispositivos nuevos: el operador carga los SN aprovisionados
# (POST /v1/slots/issue, admin) y cada instalación canjea UN código por SU slot
# (POST /v1/slots/redeem). El código se quema al canjear; la reinstalación reclama
# con POST /v1/slots/claim (solo devuelve, jamás asigna). Un slot = una sesión.

SLOTS_MAX_BODY = 2048


async def _slots_body(request: Request) -> dict:
    raw = await request.body()
    if len(raw) > SLOTS_MAX_BODY:
        raise HTTPException(status_code=413, detail="cuerpo demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="JSON inválido")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="JSON inválido")
    return body


@app.post("/v1/slots/issue")
async def slots_issue(request: Request, x_admin_key: str | None = Header(default=None)):
    """El operador registra un SN aprovisionado y recibe su código de un solo uso."""
    if not _config_admin_ok(x_admin_key, CONFIG_ADMIN_KEY):
        raise HTTPException(status_code=403, detail="llave de administración inválida")
    body = await _slots_body(request)
    sn = device_slots.normalize_sn(body.get("sn"))
    user_id = str(body.get("user_id") or "").strip()[:32]
    if not sn:
        raise HTTPException(status_code=400, detail="sn inválido")
    for _ in range(3):
        code = device_slots.generate_code()
        try:
            row = await db().fetchrow(
                """
                insert into device_slots (sn, user_id, status, claim_code)
                values ($1, $2, 'free', $3)
                on conflict (sn) do update set
                    user_id = excluded.user_id,
                    claim_code = case when device_slots.status = 'free'
                        then excluded.claim_code else device_slots.claim_code end
                returning claim_code
                """,
                sn, user_id, code,
            )
            return {"sn": sn, "code": row["claim_code"]}
        except Exception:  # noqa: BLE001  # colisión de código: reintentar
            continue
    raise HTTPException(status_code=500, detail="no se pudo emitir el código")


@app.post("/v1/slots/redeem")
async def slots_redeem(request: Request, x_api_key: str | None = Header(default=None)):
    """Canjear un código por el slot propio. Idempotente por dispositivo."""
    require_key(x_api_key)
    body = await _slots_body(request)
    code = device_slots.normalize_code(body.get("code"))
    device_key = device_slots.normalize_device_key(body.get("device_key"))
    if not code:
        raise HTTPException(status_code=400, detail="código inválido")
    row = await db().fetchrow(
        "select sn, user_id, status, device_key from device_slots where claim_code = $1",
        code,
    )
    slot = dict(row) if row is not None else None
    decision = device_slots.redeem_decision(slot, device_key)
    if not decision["ok"]:
        error = decision["error"]
        if error == "invalid":
            raise HTTPException(status_code=404, detail="código inválido")
        if error == "taken":
            raise HTTPException(status_code=409, detail="código ya usado en otro equipo")
        if error == "blocked":
            raise HTTPException(status_code=403, detail="slot bloqueado")
        raise HTTPException(status_code=400, detail="dispositivo inválido")
    if slot.get("status") != "assigned" or not slot.get("device_key"):
        await db().execute(
            """update device_slots set status = 'assigned', device_key = $2,
               claim_code = null, assigned_at = now(), last_seen_at = now()
               where sn = $1""",
            decision["sn"], device_key,
        )
    else:
        await db().execute(
            "update device_slots set last_seen_at = now() where sn = $1",
            decision["sn"],
        )
    return {"sn": decision["sn"], "user_id": decision["user_id"]}


@app.post("/v1/slots/claim")
async def slots_claim(request: Request, x_api_key: str | None = Header(default=None)):
    """Reclamar el slot ya asignado a este dispositivo (reinstalación). Solo devuelve."""
    require_key(x_api_key)
    body = await _slots_body(request)
    device_key = device_slots.normalize_device_key(body.get("device_key"))
    if not device_key:
        raise HTTPException(status_code=400, detail="dispositivo inválido")
    row = await db().fetchrow(
        "select sn, user_id from device_slots where device_key = $1",
        device_key,
    )
    found = device_slots.claim_decision(dict(row) if row is not None else None)
    if found is None:
        raise HTTPException(status_code=404, detail="sin slot asignado")
    await db().execute(
        "update device_slots set last_seen_at = now() where device_key = $1",
        device_key,
    )
    return found


@app.post("/v1/slots/auto")
async def slots_auto(request: Request, x_api_key: str | None = Header(default=None)):
    """Auto-asignación al arrancar: cero códigos para el usuario.

    Con asignación previa la devuelve; si no, entrega el slot libre auto-entregable
    más viejo (transacción con SKIP LOCKED: dos equipos a la vez jamás comparten
    slot). Sin libres → 409 (el operador carga más con /v1/slots/issue)."""
    require_key(x_api_key)
    body = await _slots_body(request)
    device_key = device_slots.normalize_device_key(body.get("device_key"))
    if not device_key:
        raise HTTPException(status_code=400, detail="dispositivo inválido")
    async with db().acquire() as conn:
        async with conn.transaction():
            existing = await conn.fetchrow(
                "select sn, user_id from device_slots where device_key = $1",
                device_key,
            )
            free = None
            if existing is None:
                free = await conn.fetchrow(
                    """select sn, user_id from device_slots
                       where status = 'free' and auto is true
                       order by created_at, sn limit 1 for update skip locked"""
                )
            decision = device_slots.auto_decision(
                dict(existing) if existing is not None else None,
                dict(free) if free is not None else None,
            )
            if not decision["ok"]:
                raise HTTPException(status_code=409, detail="sin slots libres")
            if decision["fresh"]:
                await conn.execute(
                    """update device_slots set status = 'assigned', device_key = $2,
                       claim_code = null, assigned_at = now(), last_seen_at = now()
                       where sn = $1 and status = 'free'""",
                    decision["sn"], device_key,
                )
            else:
                await conn.execute(
                    "update device_slots set last_seen_at = now() where device_key = $1",
                    device_key,
                )
    return {"sn": decision["sn"], "user_id": decision["user_id"]}


@app.get("/v1/slots/status")
async def slots_status(x_admin_key: str | None = Header(default=None)):
    """Conteo del pool para el operador."""
    if not _config_admin_ok(x_admin_key, CONFIG_ADMIN_KEY):
        raise HTTPException(status_code=403, detail="llave de administración inválida")
    rows = await db().fetch("select status from device_slots")
    return device_slots.pool_summary([dict(r) for r in rows])


@app.post("/v1/slots/worker")
async def slots_worker(request: Request, x_admin_key: str | None = Header(default=None)):
    """Marcar qué SN usa el worker del catálogo (para que no patee a la app)."""
    if not _config_admin_ok(x_admin_key, CONFIG_ADMIN_KEY):
        raise HTTPException(status_code=403, detail="llave de administración inválida")
    body = await _slots_body(request)
    sn = device_slots.normalize_sn(body.get("sn"))
    user_id = str(body.get("user_id") or "").strip()[:32]
    if not sn:
        raise HTTPException(status_code=400, detail="sn inválido")
    await db().execute(
        """
        insert into device_slots (sn, user_id, status) values ($1, $2, 'worker')
        on conflict (sn) do update set user_id = excluded.user_id, status = 'worker'
        """,
        sn, user_id,
    )
    return {"sn": sn}


# ----- marcadores de salto (intro / recap / créditos) -----
# La app pregunta por (fila de temporada, episodio) antes de abrir un capítulo: aquí se
# resuelve la ficha → (tmdb / imdb / mal, temporada), se consultan las bases globales
# comunitarias (TheIntroDB / IntroDB.app / AniSkip) en un thread aparte y se cachea el
# resultado en Postgres. Las marcas del usuario (POST) pisan a las globales por tipo
# y viajan a todos los equipos.

MARKERS_POS_TTL = 30 * 24 * 3600   # dato encontrado: se revalida cada 30 días
MARKERS_NEG_TTL = 2 * 24 * 3600    # «aquí no hay dato»: se reintenta a los 2 días
MARKERS_EPISODE_MAX = 400
MARKERS_MAX_BODY = 2048
_markers_bucket: dict[str, list[float]] = {}

_MARKERS_TITLE_SQL = """
    select t.id, t.title, t.kind, t.duration_min,
           e.tmdb_id, e.imdb_id, e.match_confidence,
           (select gm.season from group_members gm where gm.title_id = t.id limit 1) as group_season
    from titles t
    left join enrichment e on e.title_id = t.id
    where t.id = any($1::text[]) or t.play_ref = any($1::text[])
    limit 1
"""


async def _markers_season(key: str) -> int:
    """Temporada de la fila (grupo de temporadas o marca del título); 1 si no se sabe."""
    title = await db().fetchrow(_MARKERS_TITLE_SQL, skip_markers.ref_variants(key))
    if title is None:
        return 1
    if title["group_season"]:
        return int(title["group_season"])
    return skip_markers.season_from_title(title["title"], title["kind"]) or 1


@app.get("/v1/markers")
async def markers_get(
    background: BackgroundTasks,
    ref: str = Query(min_length=1, max_length=80),
    ep: int = Query(ge=1, le=MARKERS_EPISODE_MAX),
    x_api_key: str | None = Header(default=None),
):
    """Marcadores de un episodio: caché local → bases globales → caché negativa.

    `ref` es la fila de temporada tal como la app la conoce (`xtv:…`); `ep` el índice
    1-based del capítulo dentro de la temporada (el próximo capítulo repite el mismo
    intro: un desfase de numeración de ±1 es inofensivo).

    Al servir, deja en segundo plano los dos capítulos siguientes (cascada): el
    siguiente «Omitir intro» ya está en caché cuando el usuario llega ahí.
    """
    require_key(x_api_key)
    key = skip_markers.normalize_ref(ref)
    if not key:
        raise HTTPException(status_code=400, detail="referencia inválida")
    row = await db().fetchrow(
        "select season, segments, fetched_at from skip_markers where ref = $1 and episode = $2",
        key, ep,
    )
    if row is not None:
        cached = skip_markers.segments_of(row["segments"])
        age = time.time() - (row["fetched_at"].timestamp() if row["fetched_at"] else 0.0)
        ttl = MARKERS_POS_TTL if cached else MARKERS_NEG_TTL
        if age < ttl:
            background.add_task(_warm_next_markers, key, int(row["season"]), ep)
            return skip_markers.payload(key, ep, row["season"], cached)

    season = int(row["season"]) if row is not None else 1
    found = await _resolve_markers(key, ep, season, row)
    background.add_task(_warm_next_markers, key, season, ep)
    return skip_markers.payload(key, ep, season, found)


async def _resolve_markers(key: str, ep: int, season: int, row=None) -> dict:
    """Resuelve y cachea los marcadores de (ref, episodio); devuelve los segmentos."""
    if row is None:
        row = await db().fetchrow(
            "select season, segments, fetched_at from skip_markers where ref = $1 and episode = $2",
            key, ep,
        )
    if row is not None:
        season = int(row["season"]) or season
    found = {}
    title = await db().fetchrow(_MARKERS_TITLE_SQL, skip_markers.ref_variants(key))
    if title is not None:
        if title["group_season"]:
            season = int(title["group_season"])
        else:
            season = skip_markers.season_from_title(title["title"], title["kind"]) or season
        confidence = float(title["match_confidence"] or 0.0)
        tmdb_id = int(title["tmdb_id"]) if (title["tmdb_id"] and confidence >= 0.5) else None
        imdb_id = (title["imdb_id"] or "").strip() if confidence >= 0.5 else ""
        mal_id = None
        # AniSkip solo para anime (su catálogo es 100% anime): el mal_id sale del
        # mapeo TMDB→MAL que el worker refresca semanal; sin mapeo no se toca esa red.
        if tmdb_id and (title["kind"] or "") == "anime":
            mal_id = await db().fetchval(
                "select mal_id from anime_mal_map where tmdb_id = $1 and season = $2",
                tmdb_id, season,
            )
        duration_min = int(title["duration_min"] or 0)
        if tmdb_id or imdb_id or mal_id:
            try:
                raw = await asyncio.to_thread(
                    skip_markers.fetch_global, tmdb_id, imdb_id, season, ep,
                    mal_id, duration_min * 60,
                )
            except Exception:  # noqa: BLE001 — fuente caída: se sirve lo que haya
                raw = {}
            found = skip_markers.sanitize(raw, duration_min)

    # Las marcas del usuario jamás se pierden: mandan sobre lo recién llegado.
    if row is not None:
        user = {
            k: v
            for k, v in skip_markers.segments_of(row["segments"]).items()
            if (v or {}).get("src") == "user"
        }
        found = skip_markers.overlay(found, user)

    await db().execute(
        """
        insert into skip_markers (ref, episode, season, segments, fetched_at)
        values ($1, $2, $3, $4::jsonb, now())
        on conflict (ref, episode) do update set
            season = excluded.season, segments = excluded.segments, fetched_at = now()
        """,
        key, ep, season, json.dumps(found, ensure_ascii=False),
    )
    return found


async def _warm_next_markers(key: str, season: int, ep: int) -> None:
    """Cascada: deja listos los 2 capítulos siguientes (solo si no están en caché)."""
    try:
        for nxt in (ep + 1, ep + 2):
            if nxt > MARKERS_EPISODE_MAX:
                break
            exists = await db().fetchval(
                "select 1 from skip_markers where ref = $1 and episode = $2", key, nxt
            )
            if exists is None:
                await _resolve_markers(key, nxt, season)
    except Exception:  # noqa: BLE001 — calentar nunca rompe la respuesta servida
        pass


@app.post("/v1/markers")
async def markers_save(request: Request, x_api_key: str | None = Header(default=None)):
    """Marca del usuario («aquí acabó el intro» / «aquí empiezan los créditos»).

    Queda como plantilla de la fila de temporada para todos los capítulos y equipos;
    `clear` la borra (y fuerza revalidar la base global en la siguiente consulta).
    """
    require_key(x_api_key)
    raw = await request.body()
    if len(raw) > MARKERS_MAX_BODY:
        raise HTTPException(status_code=413, detail="marca demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="JSON inválido")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="JSON inválido")
    ip = request.client.host if request.client else "?"
    if not _diag_rate_ok(_markers_bucket, ip, time.time(), limit=30):
        raise HTTPException(status_code=429, detail="demasiadas marcas")

    key = skip_markers.normalize_ref(body.get("ref"))
    kind = str(body.get("kind") or "").strip().lower()
    try:
        ep = int(body.get("ep") or 0)
    except (TypeError, ValueError):
        ep = 0
    if not key or kind not in skip_markers.KINDS or not (1 <= ep <= MARKERS_EPISODE_MAX):
        raise HTTPException(status_code=400, detail="marca inválida")
    clear = bool(body.get("clear"))
    start = skip_markers.ms_or_none(body.get("startMs"))
    end = skip_markers.ms_or_none(body.get("endMs"))
    if not clear:
        if kind == "intro" and end is None:
            raise HTTPException(status_code=400, detail="el fin del intro es obligatorio")
        if kind == "credits" and start is None:
            raise HTTPException(status_code=400, detail="el inicio de los créditos es obligatorio")
        if kind == "intro" and start is not None and start >= end:
            raise HTTPException(status_code=400, detail="rango de intro inválido")
        if kind == "credits" and end is not None and start >= end:
            raise HTTPException(status_code=400, detail="rango de créditos inválido")

    row = await db().fetchrow(
        "select season, segments from skip_markers where ref = $1 and episode = $2", key, ep
    )
    segments = skip_markers.segments_of(row["segments"]) if row is not None else {}
    if clear:
        segments.pop(kind, None)
    else:
        segments[kind] = {"s": start, "e": end, "src": "user", "votes": 1}
    season = int(row["season"]) if row is not None else await _markers_season(key)
    await db().execute(
        """
        insert into skip_markers (ref, episode, season, segments, fetched_at)
        values ($1, $2, $3, $4::jsonb, case when $5 then to_timestamp(0) else now() end)
        on conflict (ref, episode) do update set
            season = excluded.season, segments = excluded.segments, fetched_at = excluded.fetched_at
        """,
        key, ep, season, json.dumps(segments, ensure_ascii=False), clear,
    )
    return {"ok": True, "ref": key, "episode": ep, "kind": kind, "cleared": clear}


# ----- estado del índice (/v1/stats) -----
# Una foto para vigilar que el catálogo mejore: cobertura de fichas TMDB/IMDb por tipo
# (lo que habilita marcadores, sinopsis es-MX y el orden por popularidad) y el estado
# de la caché de marcadores de salto. Solo lectura, misma API key que el resto.


@app.get("/v1/stats")
async def stats(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    rows = await db().fetch(
        """
        select t.kind,
               count(*) as total,
               count(*) filter (where e.tmdb_id is not null) as matched,
               count(*) filter (where e.title_id is not null and e.tmdb_id is null) as no_match,
               count(*) filter (where e.title_id is null) as fresh
        from titles t
        left join enrichment e on e.title_id = t.id
        where t.kind in ('movie', 'series', 'anime', 'kids')
        group by 1 order by 2 desc
        """
    )
    kinds = {}
    for row in rows:
        total = int(row["total"] or 0)
        matched = int(row["matched"] or 0)
        kinds[row["kind"]] = {
            "total": total,
            "matched": matched,
            "noMatch": int(row["no_match"] or 0),
            "fresh": int(row["fresh"] or 0),
            "coverage": round(100.0 * matched / total) if total else 0,
        }
    markers = await db().fetchrow(
        """
        select count(*) as rows_total,
               count(*) filter (where segments <> '{}'::jsonb) as with_data,
               max(fetched_at) as last_fetch
        from skip_markers
        """
    )
    version = await db().fetchval("select version from sync_state where id = 1") or 1
    return {
        "indexVersion": int(version),
        "kinds": kinds,
        "markers": {
            "rows": int(markers["rows_total"] or 0),
            "withData": int(markers["with_data"] or 0),
            "lastFetch": markers["last_fetch"].isoformat() if markers["last_fetch"] else None,
        },
    }

