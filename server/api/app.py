"""Vision+ catalog index API — fase 1.

Sirve el índice (categorías + títulos) que el worker `sync` mantiene en Postgres.
La reproducción NO pasa por aquí: la app sigue acuñando licencias en el dispositivo.

Todos los endpoints /v1/* requieren el header X-Api-Key.
"""

import hmac
import os
import re
import time
import unicodedata
from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI, Header, HTTPException, Query
from starlette.middleware.gzip import GZipMiddleware

DATABASE_URL = os.environ["DATABASE_URL"]
API_KEY = os.environ["API_KEY"]

# Los partidos se agrupan por día en hora de México (el worker baja en UTC).
SPORTS_TZ = "America/Mexico_City"

FILM_COLUMNS = """
    t.id, t.title, t.year, t.duration_min, t.synopsis,
    t.poster_url, t.play_ref, t.source, t.score, t.kind
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
    if enr is not None:
        if enr["rating"] is not None:
            rating = enr["rating"]
        if enr["year"]:
            year = enr["year"]
        overview = (enr["overview"] or "").strip()
        if overview:
            synopsis = overview
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
    }


async def _enrichment_map(ids) -> dict:
    """Enriquecimiento TMDB por título (solo matches con confianza)."""
    unique = list(dict.fromkeys(i for i in ids if i))
    if not unique:
        return {}
    rows = await db().fetch(
        """select title_id, rating, year, overview from enrichment
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
    """Temporadas agrupadas: título -> grupo, grupo -> temporadas, grupo -> título base."""
    group_rows = await db().fetch(
        """
        select gm.title_id, gm.season, gm.group_id, g.base_title, t.play_ref, t.source
        from group_members gm
        join title_groups g on g.id = gm.group_id
        join titles t on t.id = gm.title_id
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
        entry, group_id = _grouped_entry(r, member_of, seasons_by_group, base_of_group)
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

