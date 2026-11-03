"""Vision+ catalog index API — fase 1.

Sirve el índice (categorías + títulos) que el worker `sync` mantiene en Postgres.
La reproducción NO pasa por aquí: la app sigue acuñando licencias en el dispositivo.

Todos los endpoints /v1/* requieren el header X-Api-Key.
"""

import asyncio
import difflib
import hmac
import json
import logging
import os
import re
import time
import unicodedata
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import asyncpg
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Query, Request, Response
from starlette.middleware.gzip import GZipMiddleware

import markers as skip_markers
import portal as portal_search
import slots as device_slots
import episodes as episodes_mirror
import banners as launcher_banners
import sports_map
import sync as device_sync
from epg_channels import canonical_channel, channel_country, epg_norms_for

DATABASE_URL = os.environ["DATABASE_URL"]
API_KEY = os.environ["API_KEY"]
log = logging.getLogger("visionplus.api")
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
    games_task = asyncio.create_task(_games_sweep_loop())
    try:
        yield
    finally:
        games_task.cancel()
        try:
            await games_task
        except asyncio.CancelledError:
            pass
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


def film(record: asyncpg.Record, enr=None, season_year: bool = False) -> dict:
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
    genres = []
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
    tmdb_id = 0
    if enr is not None:
        if enr["rating"] is not None:
            rating = enr["rating"]
        if enr["year"]:
            # En «Novedades y Estrenos»/temáticas una ficha de temporada («The Last
            # of Us T2», 2025) no puede mostrar el año de la primera temporada que
            # guarda TMDB (2023). En el resto de filas manda TMDB: es el año de
            # estreno de la serie.
            record_year = int(record["year"] or 0)
            enr_year = int(enr["year"] or 0)
            season_title = bool(
                re.search(r"\b(?:t|temp\.?|temporada|season)\s*\d+\b", str(record["title"] or ""), re.I)
            )
            year = max(record_year, enr_year) if (season_year and season_title) else enr_year
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
        try:
            if "tmdb_id" in enr.keys() and enr["tmdb_id"] is not None:
                tmdb_id = int(enr["tmdb_id"])
        except (TypeError, ValueError, KeyError, AttributeError):
            tmdb_id = 0
        try:
            if "genres" in enr.keys() and enr["genres"] is not None:
                genres = [str(g).strip() for g in enr["genres"] if str(g).strip()]
        except (TypeError, KeyError, AttributeError):
            genres = []
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
        "tmdbId": tmdb_id,
        "kind": record["kind"] or "",
        "genres": genres,
    }


async def _enrichment_map(ids) -> dict:
    """Enriquecimiento TMDB por título (solo matches con confianza)."""
    unique = list(dict.fromkeys(i for i in ids if i))
    if not unique:
        return {}
    rows = await db().fetch(
        """select title_id, rating, year, overview, popularity, vote_count, original_title,
                  tmdb_id, genres
           from enrichment
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

    Una temporada se sirve UNA vez: al fusionar nombres («House» + «House M.D.», o dos
    copias de «House of Cards T5») puede haber dos fichas del mismo número. Gana la de
    XTV (motor nativo) y, entre iguales, la más popular; así el selector no pinta dos «T5».
    """
    group_rows = await db().fetch(
        """
        select gm.title_id, gm.season, gm.group_id, g.base_title, t.play_ref, t.source
        from group_members gm
        join title_groups g on g.id = gm.group_id
        join titles t on t.id = gm.title_id
        where coalesce(t.program_type, '') != 'movie'
        order by gm.group_id, gm.season, (t.source = 'xtv') desc,
                 coalesce(t.popularity, 0) desc, coalesce(t.score, 0) desc
        """
    )
    member_of: dict[str, dict] = {}
    seasons_by_group: dict[str, list] = {}
    base_of_group: dict[str, str] = {}
    rows_by_group: dict[str, list] = {}
    for gr in group_rows:
        rows_by_group.setdefault(gr["group_id"], []).append(gr)
        base_of_group[gr["group_id"]] = gr["base_title"]
    for group_id, rows in rows_by_group.items():
        # Un grupo de un solo miembro no es un grupo: su ficha se sirve suelta.
        if len(rows) < 2:
            base_of_group.pop(group_id, None)
            continue
        # Preferencia explícita (no solo el ORDER BY): XTV, y luego la más popular.
        ranked = sorted(
            rows,
            key=lambda gr: (
                1 if str(gr.get("source") or "") == "xtv" else 0,
                float(gr.get("popularity") or 0),
                float(gr.get("score") or 0),
            ),
            reverse=True,
        )
        seasons: list = []
        seen: set[int] = set()
        for gr in ranked:
            if gr["season"] in seen:
                continue
            seen.add(gr["season"])
            seasons.append({"n": gr["season"], "titleId": gr["title_id"], "videoUrl": gr["play_ref"]})
        seasons.sort(key=lambda s: s["n"])
        seasons_by_group[group_id] = seasons
        for gr in rows:
            member_of[gr["title_id"]] = gr
    return member_of, seasons_by_group, base_of_group


def _grouped_entry(
    row,
    member_of,
    seasons_by_group,
    base_of_group,
    enr=None,
    season_year: bool = False,
) -> tuple[dict, str | None]:
    """Film con temporadas resueltas + group_id si está agrupado (si no, None)."""
    member = member_of.get(row["id"])
    entry = film(row, enr, season_year=season_year)
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
    # En «Novedades y Estrenos» y en las temáticas la tarjeta representa la temporada
    # servida: una serie de 2011 con su T15 de 2026 debe decir 2026. En las demás
    # filas se conserva el año de estreno de la serie (TMDB). Solo sube, nunca baja.
    if season_year:
        row_year = int(row["year"] or 0)
        if row_year > int(entry.get("year") or 0):
            entry["year"] = row_year
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


# El índice completo pesa (~6 MB con 100 títulos por categoría) y se pide en cada
# arranque: recalcularlo desde cero en cada llamada costaba segundos y escribía
# temporales en disco (16.8 MB medidos por recálculo). Se cachea por versión + límite
# (el worker sube la versión cuando cambia algo) y se guarda la última copia buena
# para servirla si la base falla: un 500 dejaba el Inicio a medias en los equipos.
_INDEX_CACHE: dict[tuple[int, int], tuple[float, dict]] = {}
_INDEX_CACHE_TTL = 1800.0
_INDEX_LAST_GOOD: dict[int, dict] = {}
_INDEX_LAST_GOOD_MAX = 4


@app.get("/v1/index")
async def index(
    since: int = Query(default=0, ge=0),
    # El índice completo (sin tope) materializa 157 MB en memoria y tumbaba el contenedor
    # por OOM: la app pide 100 por categoría. El tope es obligatorio para cualquier cliente.
    limitFilms: int = Query(default=100, ge=1, le=500),
    x_api_key: str | None = Header(default=None),
):
    """Índice completo. Con `since` al día responde {upToDate: true} sin reenviar nada.
    `limitFilms` recorta los títulos por categoría EN SQL (para la app móvil); `total`
    sigue siendo el real de la categoría."""
    require_key(x_api_key)
    try:
        version = await index_version()
        if since and since >= version:
            return {"version": version, "upToDate": True, "categories": []}
        cache_key = (version, limitFilms)
        cached = _INDEX_CACHE.get(cache_key)
        if cached and time.time() - cached[0] < _INDEX_CACHE_TTL:
            return cached[1]
        # Se piden hasta 2× por categoría: al colapsar las temporadas de un grupo quedan
        # suficientes tarjetas únicas para el límite pedido.
        fetch_limit = limitFilms * 2 if limitFilms else 0
        # LATERAL por categoría: el tope se aplica DENTRO de cada categoría usando el
        # índice (category_id, position), en vez de rankear las 460k filas de
        # title_categories. Medido: 0 KB de temporales (antes 16.8 MB por recálculo) y
        # ~1.8× más rápido; la salida es idéntica (127/127 categorías comparadas).
        rows = await db().fetch(
            f"""
            select x.id, x.title, x.year, x.duration_min, x.synopsis,
                   x.poster_url, x.play_ref, x.source, x.score, x.kind,
                   x.popularity, x.vote_count,
                   x.category_id, x.position,
                   c.title as category_title, c.kind as category_kind,
                   c.position as category_position
            from categories c
            cross join lateral (
                select {FILM_COLUMNS}, tc.category_id, tc.position
                from title_categories tc
                join titles t on t.id = tc.title_id
                where tc.category_id = c.id
                order by tc.position, t.title
                limit $1::int
            ) x
            where c.kind <> 'section'
            order by c.position, c.title, x.position
            """,
            fetch_limit,
        )
        member_of, seasons_by_group, base_of_group = await _group_maps()
        enr = await _enrichment_map([r["id"] for r in rows])
        count_rows = await db().fetch(
            "select tc.category_id, count(*) as n from title_categories tc "
            "join categories c on c.id = tc.category_id where c.kind != 'section' "
            "group by 1"
        )
        counts = {r["category_id"]: r["n"] for r in count_rows}

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
            entry, group_id = _grouped_entry(
                row,
                member_of,
                seasons_by_group,
                base_of_group,
                enr.get(row["id"]),
                season_year=(
                    row["category_id"] == "estrenos"
                    or str(row["category_id"]).endswith("-estrenos")
                    or row["category_kind"] == "theme"
                ),
            )
            if group_id is not None:
                seen = seen_groups.setdefault(row["category_id"], set())
                if group_id in seen:
                    continue
                seen.add(group_id)
            category["films"].append(entry)
        for category in cats.values():
            category["total"] = counts.get(category["id"], len(category["films"]))
            if limitFilms:
                category["films"] = category["films"][:limitFilms]
        updated = await db().fetchval("select updated_at from sync_state where id = 1")
        result = {
            "version": version,
            "upToDate": False,
            "updatedAt": updated.isoformat() if updated else None,
            "categories": list(cats.values()),
        }
    except Exception:
        # Un fallo de la base (p. ej. pgsql_tmp sin espacio) no debe dejar el Inicio sin
        # catálogo: se sirve la última copia buena (versión anterior) hasta que vuelva.
        last = _INDEX_LAST_GOOD.get(limitFilms)
        if last is None:
            raise
        log.warning("index: la base falló; sirviendo la última copia buena (límite %s)", limitFilms, exc_info=True)
        return last
    _INDEX_CACHE[cache_key] = (time.time(), result)
    _INDEX_LAST_GOOD.pop(limitFilms, None)
    _INDEX_LAST_GOOD[limitFilms] = result
    if len(_INDEX_LAST_GOOD) > _INDEX_LAST_GOOD_MAX:
        _INDEX_LAST_GOOD.pop(next(iter(_INDEX_LAST_GOOD)))
    # Poda de versiones viejas: la caché es por versión y límite; no debe crecer sin fin.
    if len(_INDEX_CACHE) > 8:
        for key in list(_INDEX_CACHE)[:-8]:
            _INDEX_CACHE.pop(key, None)
    return result


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
        # Se piden 4× filas: al colapsar las temporadas de un grupo (T1..T14 de la
        # misma serie) el tope se quedaba en 3-6 tarjetas únicas por género. Así el
        # límite se aplica DESPUÉS del colapso y la fila se llena de verdad.
        films = await db().fetch(
            f"""select {FILM_COLUMNS} from title_categories tc
                join titles t on t.id = tc.title_id
                where tc.category_id = $1 order by tc.position limit $2""",
            c["id"],
            min(limit * 4, 400),
        )
        enr = await _enrichment_map([r["id"] for r in films])
        total = await db().fetchval(
            "select count(*) from title_categories where category_id = $1", c["id"]
        )
        seen: set[str] = set()
        entries = []
        for r in films:
            entry, group_id = _grouped_entry(
                r,
                member_of,
                seasons_by_group,
                base_of_group,
                enr.get(r["id"]),
                season_year=str(c["id"]).endswith("-estrenos"),
            )
            if group_id is not None:
                if group_id in seen:
                    continue
                seen.add(group_id)
            entries.append(entry)
        if entries:
            rows_out.append({"id": c["id"], "title": c["title"], "total": total, "films": entries[:limit]})
    result = {"name": name, "title": SECTIONS[name], "version": ver, "rows": rows_out}
    if rows_out:
        # No se cachea una sección vacía: el worker borra y recrea las hileras de sección
        # en cada ciclo y una respuesta a medias dejaría el riel sin ella hasta la
        # siguiente versión del índice.
        _SECTION_CACHE[cache_key] = result
    return result


def order_trending(films: list) -> list:
    """«Populares de la semana» se lee por popularidad, no por el puesto editorial de
    TMDB: el ranking semanal mezcla cine y TV y podía abrir con una serie de nicho
    («Link Click» era el n.º 1). El conjunto sigue siendo el trending de la semana,
    pero el orden es el impacto real (popularidad, votos, rating)."""
    return sorted(
        films,
        key=lambda f: (f.get("popularity", 0), f.get("voteCount", 0), f.get("score", 0)),
        reverse=True,
    )


def dedupe_trending(rows) -> list:
    """Una sola tarjeta por obra en «Populares de la semana».

    El join de `weekly_trending` con `enrichment` devuelve una fila por CADA copia
    del catálogo que comparte el tmdb_id (temporadas T1..T4, doblajes, portales):
    la fila repetía la misma tarjeta («Link Click» ×4, «Tulsa King» ×5, «Los
    Simpson» ×26). Se conserva la primera copia —las filas llegan ordenadas por la
    confianza del match— y, cuando no hay tmdb_id, se deduplica por título+año.
    """
    out: list = []
    seen: set = set()
    for row in rows:
        tmdb = int(row["tmdb_id"]) if row["tmdb_id"] else 0
        media = row["media"] or ""
        key = (tmdb, media) if tmdb else (_fold(row["title"] or ""), row["year"] or 0)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


@app.get("/v1/trending")
async def trending(
    limit: int = Query(default=20, ge=1, le=40),
    x_api_key: str | None = Header(default=None),
):
    """«Populares de la semana» (TMDB /trending): la foto semanal casada con el catálogo.

    Solo entran títulos que tenemos con ficha (póster) y reproducción; orden de la semana
    y UNA entrada por obra (sin las copias de temporada/doblaje del mismo tmdb_id).
    """
    require_key(x_api_key)
    week = await db().fetchval("select max(week) from weekly_trending")
    if not week:
        return {"week": None, "films": []}
    rows = await db().fetch(
        f"""select {FILM_COLUMNS}, w.tmdb_id, w.media, w.rank from weekly_trending w
            join enrichment e on e.tmdb_id = w.tmdb_id and coalesce(e.match_confidence, 0) >= 0.5
            join titles t on t.id = e.title_id
            where w.week = $1 and coalesce(t.poster_url, '') <> ''
              and coalesce(t.play_ref, '') <> ''
            order by w.rank,
                     coalesce(e.match_confidence, 0) desc,
                     t.popularity desc nulls last,
                     t.vote_count desc nulls last,
                     t.year asc nulls last
            limit 400""",
        week,
    )
    rows = dedupe_trending(rows)
    enr = await _enrichment_map([r["id"] for r in rows])
    films = order_trending([film(r, enr.get(r["id"])) for r in rows])[:limit]
    return {
        "week": week,
        "films": films,
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

# Las filas del buscador llevan además el texto materializado: decide si el índice
# ya tiene el NOMBRE que se busca (cualquiera de sus formas) y, por tanto, si su
# orden manda sobre el del portal. No se usa en los demás endpoints (no viaja).
_SEARCH_COLUMNS = FILM_COLUMNS + ", t.search_text"

# El buscador por etapas (auditoría 2026-10-18): la gente busca con el nombre que
# conoce, que no siempre es el del portal («buscando a nemo» → «Finding Nemo»,
# «vaiana» → «Moana»). `titles.search_text` materializa TODOS los nombres (portal,
# es-MX de TMDB, original y alternativos regionales); se consulta ahí primero.
_SEARCH_FROM = """
    from titles t
    left join enrichment e on e.title_id = t.id and coalesce(e.match_confidence, 0) >= 0.5
"""


def _fold_sql(expr: str) -> str:
    """Pliegue del query en SQL: espejo de `_fold` (minúsculas, sin acentos, signos
    → espacios, espacios colapsados). Comparar `«Daddy's Home»` con `daddy s home`
    tal cual NO matchea (el apóstrofo se queda): el plegado lo convierte en espacios.
    """
    folded = f"unaccent(lower({expr}))"
    return (
        f"btrim(regexp_replace(regexp_replace({folded}, '[^a-z0-9]+', ' ', 'g'), ' +', ' ', 'g'))"
    )


def build_rank(clean_q: str) -> tuple[str, list[str]]:
    """Orden por relevancia de nombres, con TODAS las formas del título al mismo nivel.

    Un nombre exacto de cualquier fuente (título del portal, alias, es-MX, original,
    alternativo o traducción) vale lo mismo: así «El conjuro» encuentra primero la
    película original y no su secuela, y «Squid Game» no deja ganar al spin-off.
    Después: prefijo > contiene > compacto; el desempate lo pone la popularidad.
    """
    compact = clean_q.replace(" ", "")

    def folded(expr: str) -> str:
        return _fold_sql(expr)

    def compacted(expr: str) -> str:
        return f"replace({folded(expr)}, ' ', '')"

    def in_names(column: str, expr: str) -> str:
        # `a` es el alias del unnest; `expr` ya viene plegado.
        return (
            f"exists (select 1 from unnest(coalesce({column}, '{{}}'::text[])) a "
            f"where {expr})"
        )

    title = folded("t.title")
    portal_original = folded("coalesce(t.original_title, '')")
    localized = folded("coalesce(e.localized_title, '')")
    original = folded("coalesce(e.original_title, '')")
    alt_exact = (
        in_names("e.alt_titles", f"{folded('a')} = $1")
        + " or " + in_names("e.translated_titles", f"{folded('a')} = $1")
    )
    alt_prefix = (
        in_names("e.alt_titles", f"{folded('a')} like $2")
        + " or " + in_names("e.translated_titles", f"{folded('a')} like $2")
    )
    alt_contains = (
        in_names("e.alt_titles", f"{folded('a')} like $3")
        + " or " + in_names("e.translated_titles", f"{folded('a')} like $3")
    )
    alt_compact = (
        in_names("e.alt_titles", f"{compacted('a')} = $4")
        + " or " + in_names("e.translated_titles", f"{compacted('a')} = $4")
    )

    strong_exact = " or ".join([
        f"{title} = $1", f"{portal_original} = $1", f"{localized} = $1", f"{original} = $1",
    ])
    strong_prefix = " or ".join([
        f"{title} like $2", f"{portal_original} like $2", f"{localized} like $2", f"{original} like $2",
    ])
    strong_contains = " or ".join([
        f"{title} like $3", f"{portal_original} like $3", f"{localized} like $3", f"{original} like $3",
    ])
    title_compact = compacted("t.title")
    portal_original_compact = compacted("coalesce(t.original_title, '')")
    localized_compact = compacted("coalesce(e.localized_title, '')")
    original_compact = compacted("coalesce(e.original_title, '')")
    strong_compact = " or ".join([
        f"{title_compact} = $4",
        f"{portal_original_compact} = $4",
        f"{localized_compact} = $4",
        f"{original_compact} = $4",
    ])
    return (
        f"case when {strong_exact} then 1 "
        # Un exacto alternativo/traducido vale como un prefijo del título: «El conjuro»
        # encuentra la original antes que su secuela, sin que una traducción rara
        # («Nemocnice…» para New Amsterdam) le gane a «Finding Nemo» por popularidad.
        f"when {strong_prefix} or {alt_exact} then 2 "
        f"when {strong_contains} then 3 "
        f"when {alt_prefix} then 4 "
        f"when {alt_contains} then 5 "
        f"when {strong_compact} or {alt_compact} then 6 else 9 end",
        [clean_q, f"{clean_q}%", f"%{clean_q}%", compact],
    )


def build_name_match(words: list[str], offset: int = 0) -> tuple[str, list[str]]:
    """Etapa rápida: cada palabra debe vivir en el texto de búsqueda (nombres del
    título: portal + es-MX/original/regionales de TMDB, con formas compactas).

    Matchea contra la columna materializada: el índice GIN de trigramas la acelera
    y el reparto/sinopsis (ruidoso con palabras cortas) no entra aquí — para eso
    está la etapa profunda, solo de respaldo.
    """
    params = [f"%{w}%" for w in words]
    clauses = [f"t.search_text like ${offset + i + 1}" for i in range(len(params))]
    return " and ".join(clauses), params


def build_deep_match(words: list[str], offset: int = 0) -> tuple[str, list[str]]:
    """Etapa profunda (solo si la rápida no dio nada): cada palabra puede venir del
    reparto, la dirección, la sinopsis o los nombres TMDB. Es el comportamiento
    clásico del buscador, ahora también con los nombres regionales.

    Las palabras de UN carácter solo prueban contra los nombres: en reparto o
    sinopsis una «x» matchea cualquier texto y envenena la búsqueda («fast x»).
    """
    params: list[str] = []
    clauses: list[str] = []
    for i, word in enumerate(words):
        params.append(f"%{word}%")
        n = offset + i + 1
        parts = [
            f"coalesce(t.search_text, '') like ${n}",
            f"unaccent(lower(t.title)) like ${n}",
            f"unaccent(lower(coalesce(t.original_title, ''))) like ${n}",
        ]
        if len(word) > 1:
            parts += [
                f"unaccent(lower(t.director)) like ${n}",
                f"unaccent(lower(t.cast_list)) like ${n}",
                f"unaccent(lower(coalesce(e.director_names, ''))) like ${n}",
                f"unaccent(lower(coalesce(e.cast_names, ''))) like ${n}",
                f"unaccent(lower(t.synopsis)) like ${n}",
                f"unaccent(lower(coalesce(e.overview, ''))) like ${n}",
            ]
        parts += [
            f"unaccent(lower(coalesce(e.localized_title, ''))) like ${n}",
            f"unaccent(lower(coalesce(e.original_title, ''))) like ${n}",
            f"exists (select 1 from unnest(coalesce(e.alt_titles, '{{}}'::text[])) a "
            f"where unaccent(lower(a)) like ${n})",
            f"exists (select 1 from unnest(coalesce(e.translated_titles, '{{}}'::text[])) a "
            f"where unaccent(lower(a)) like ${n})",
        ]
        clauses.append("(" + " or ".join(parts) + ")")
    return " and ".join(clauses), params


def build_people_matches(words: list[str], offset: int = 0) -> tuple[str, str, list[str]]:
    """Dos cláusulas para la etapa de personas: créditos del portal y de TMDB.

    La consulta viaja como FRASE («martin scorsese», no «martin» + «scorsese» por
    separado): el AND por palabras hacía que una ficha con un «Martin» y un
    «Scorsese» distintos apareciera como si fuera del director. Se consultan los dos
    bloques por separado para que Postgres use los índices GIN de trigramas.
    """
    phrase = " ".join(words)
    params = [f"%{phrase}%"]
    n = offset + 1
    title_sql = f"(vp_fold(t.director) like ${n} or vp_fold(t.cast_list) like ${n})"
    enrich_sql = (
        f"(vp_fold(e.director_names) like ${n} or vp_fold(e.cast_names) like ${n})"
    )
    return title_sql, enrich_sql, params


async def _search_page(match_sql: str, match_params: list[str], clean_q: str, limit: int):
    """Una página de resultados con el orden de relevancia de nombres."""
    rank_sql, rank_params = build_rank(clean_q)
    params = [*rank_params, *match_params, limit]
    sql = f"""
        select {_SEARCH_COLUMNS}
        {_SEARCH_FROM}
        where {match_sql}
        order by {rank_sql},
            case when t.kind = 'live' then 1 else 0 end,
            t.popularity desc nulls last,
            t.score desc nulls last,
            t.year asc nulls last
        limit ${len(params)}
    """
    return await db().fetch(sql, *params)


async def _search_people(
    title_sql: str,
    title_params: list[str],
    enrich_sql: str,
    enrich_params: list[str],
    phrase: str,
    limit: int,
):
    """Personas por rol y popularidad, consultando los dos bloques por separado.

    Cada bloque usa su índice GIN (títulos del portal / créditos TMDB); luego se
    unen por id. Quien dirigió la obra va antes que un cameo en el reparto: si no,
    una aparición de Scorsese en «The Mandalorian» le ganaba a sus propias películas.
    """
    rows: list = []
    for clause, params, from_sql in (
        (title_sql, title_params, _SEARCH_FROM),
        # El bloque de TMDB arranca por `enrichment`: así usa SU índice GIN. Con el
        # `from titles` el plan recorría las 52k fichas y una consulta sin resultados
        # costaba ~0.7 s («guerra de papas»).
        (enrich_sql, enrich_params, "from enrichment e join titles t on t.id = e.title_id"),
    ):
        if not clause:
            continue
        try:
            rows.extend(await db().fetch(
                f"""
                select {_SEARCH_COLUMNS}, t.director, e.director_names
                {from_sql}
                where {clause}
                order by case when t.kind = 'live' then 1 else 0 end,
                         t.popularity desc nulls last,
                         t.score desc nulls last
                limit ${len(params) + 1}
                """,
                *params,
                limit,
            ))
        except Exception:
            continue
    seen: set[str] = set()
    merged = []
    for row in rows:
        if row["id"] in seen:
            continue
        seen.add(row["id"])
        merged.append(row)
    scored = []
    for row in merged:
        directed = _fold(row["director"] or "") + " " + _fold(row["director_names"] or "")
        role = 0 if phrase and phrase in directed else 1
        scored.append((role, row))
    scored.sort(key=lambda pair: (
        pair[0],
        -(float(pair[1]["popularity"] or 0)),
        -(float(pair[1]["score"] or 0)),
        -(int(pair[1]["year"] or 0)),
    ))
    return [row for _, row in scored[:limit]]


MIN_NAME_RESULTS = 3


def should_try_people(rows, limit: int = 50) -> bool:
    """¿La etapa de nombres es corta y conviene probar personas?

    Con 1-2 nombres la coincidencia puede ser incidental (un alias con «Tarantino»
    devolvía solo Death Proof). La consulta de personas está indexada y es barata;
    la profunda completa (sinopsis) NO se corre aquí: solo cuando no hay nombres.
    """
    return len(rows) < min(MIN_NAME_RESULTS, limit)


def should_run_full_deep(rows) -> bool:
    """La profunda completa solo cuando la etapa de nombres no dio NADA.

    Correrla con 1-2 resultados era el costo de 3.5 s en «guerra de papas»; la
    etapa de personas ya cubre el caso de directores/actores.
    """
    return not rows


def fuzzy_probe(clean_q: str) -> str:
    """La palabra con la que se prueba la similitud: la más larga de la consulta.

    «termintor 2» debe probarse como «termintor»: medido contra la base real, la
    frase completa da 0.533 (por debajo del umbral) y la palabra sola 0.615.
    """
    words = [w for w in clean_q.split() if w]
    if not words:
        return clean_q
    return max(words, key=len)


def fuzzy_probes(clean_q: str) -> list[str]:
    """TODAS las palabras útiles de la consulta, no solo la más larga.

    Con una sola palabra, «guerra de papas» devolvía cualquier título cuyo nombre
    TMDB en otro idioma dijera «guerra» («A Guerra dos Tronos» → Juego de tronos):
    el ruido medido en vivo el 2026-10-18. La etapa difusa exige que cada palabra
    larga se parezca a algo del título. Las vacías («para», «este», «the»…) no
    cuentan: son las que hacen que una consulta sin sentido «encuentre» algo.
    """
    return list(dict.fromkeys(
        w for w in clean_q.split()
        if len(w) >= 4 and w not in portal_search.STOPWORDS
    ))


async def _search_fuzzy(clean_q: str, limit: int):
    """Última red: similitud por trigramas («termintor» → «Terminator»).

    Solo corre cuando las etapas anteriores no dieron NADA; con palabras de menos de
    4 letras no vale la pena y sin pg_trgm disponible el que llama absorbe el error
    y devuelve vacío. Cada palabra larga debe superar el umbral (AND): así una
    coincidencia parcial («guerra» en el título portugués de otra serie) no basta.
    """
    probes = fuzzy_probes(clean_q)[:3]
    if not probes:
        return []
    text = "coalesce(nullif(t.search_text, ''), unaccent(lower(t.title)))"
    sims = [f"word_similarity(${i + 1}, {text})" for i in range(len(probes))]
    where = " and ".join(f"{sim} > 0.55" for sim in sims)
    ordering = f"least({', '.join(sims)}) desc"
    return await db().fetch(
        f"""
        select {_SEARCH_COLUMNS}
        {_SEARCH_FROM}
        where {where}
        order by {ordering},
                 case when t.kind = 'live' then 1 else 0 end,
                 t.popularity desc nulls last,
                 t.score desc nulls last
        limit ${len(probes) + 1}
        """,
        *probes,
        min(limit, 20),
    )


def _row_search_text(row) -> str:
    """El texto materializado de una fila del buscador ('' si no viaja)."""
    try:
        return (row["search_text"] or "") if "search_text" in row.keys() else ""
    except (AttributeError, TypeError):
        return ""


def _local_has_the_name(rows, clean_q: str) -> bool:
    """¿El índice local ya tiene ESTE nombre (en cualquiera de sus formas)?

    Se mira `search_text` (portal + alias + es-MX/original/regionales de TMDB), no
    solo el título visible: «buscando a nemo» debe reconocer «Finding Nemo» como
    acierto local aunque su título no diga «buscando».
    """
    words = [w for w in clean_q.split() if w]
    if not words:
        return False
    for row in rows:
        text = _row_search_text(row)
        if text and all(word in text for word in words):
            return True
    return False


def should_wait_for_portal(local_rows, clean_q: str) -> bool:
    """¿Hace falta esperar al portal? Solo cuando el índice no tiene ya ESE nombre.

    El portal tarda ~100-400 ms; si el índice local ya tiene el nombre en cualquiera
    de sus formas, su orden (con el ranking nuevo) es el bueno y no se espera. Si el
    índice no lo tiene, el portal sigue siendo la red de seguridad.
    """
    if not local_rows:
        return True
    return not _local_has_the_name(local_rows, clean_q)


def _tmdb_dedupe_key(entry: dict, enrichment) -> int:
    """Clave para no repetir la misma obra de TMDB en los resultados.

    «Round 6» y «El juego del calamar» son el mismo show (mismo tmdb_id) con nombres
    distintos; el buscador mostraba ambos. Solo se deduplica con confianza alta
    (≥0.75): un match dudoso no puede ocultar un título distinto.
    """
    try:
        tmdb = int(entry.get("tmdbId") or 0)
    except (TypeError, ValueError):
        return 0
    if not tmdb:
        return 0
    confidence = 0.0
    try:
        if enrichment is not None and "match_confidence" in enrichment.keys():
            confidence = float(enrichment["match_confidence"] or 0)
    except (TypeError, ValueError, KeyError, AttributeError):
        confidence = 0.0
    return tmdb if confidence >= 0.75 else 0


def merge_search_rows(local_rows: list, portal_rows: list, clean_q: str, limit: int) -> list:
    """Une índice local + portal en el orden que espera la gente.

    El portal es la verdad para «¿existe este título?» (es el buscador de la app
    original); el índice local aporta fichas enriquecidas, agrupación de temporadas
    y búsqueda profunda (reparto/dirección/sinopsis). Si el índice ya tiene el
    nombre buscado (título, alias o nombre TMDB), manda su orden. Si no, los
    aciertos del portal van primero: el caso medido es «guerra de papas» → el
    índice devolvía Juego de tronos y el portal devuelve Guerra de Papás.
    """
    if not portal_rows:
        return local_rows
    local_first = _local_has_the_name(local_rows, clean_q)
    first, second = (local_rows, portal_rows) if local_first else (portal_rows, local_rows)
    merged: list = []
    seen: set[str] = set()
    for row in (*first, *second):
        if row["id"] in seen:
            continue
        seen.add(row["id"])
        merged.append(row)
        if len(merged) >= limit:
            break
    return merged



def _attach_original_title(entry: dict, row, enr) -> None:
    """Nombre original para «también conocida como»: solo si difiere del mostrado.

    La app actual ignora el campo (llega sin costo); una OTA futura puede enseñarlo
    para que el usuario entienda por qué apareció al buscar en otro idioma.
    """
    if enr is None or entry["id"] != row["id"]:
        return
    try:
        if "original_title" not in enr.keys():
            return
        original = (enr["original_title"] or "").strip()
    except (KeyError, TypeError):
        return
    if original and _fold(original) != _fold(entry["title"]):
        entry["originalTitle"] = original


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

    # El portal, en paralelo: su buscador es el de la app original y sí encuentra lo
    # que el índice no tiene (listados topados y colecciones poco profundas). Nunca
    # lanza ni acuña sesión; si falla, la búsqueda sigue solo con el índice local.
    portal_task = None
    if len(clean_q) >= 3:
        portal_task = asyncio.create_task(portal_search.lookup(db(), raw_q))

    # El offset de los placeholders del match sale del propio rank (si el rank crece,
    # la búsqueda sigue alineada; el guardián lo clava).
    offset = len(build_rank(clean_q)[1])
    name_sql, name_params = build_name_match(words, offset=offset)
    deep_sql, deep_params = build_deep_match(words, offset=offset)

    rows: list = []
    deep_failed = False
    people_hit = False
    try:
        # 1) Nombres (título/alias/es-MX/original/regionales): vía rápida y precisa.
        rows = await _search_page(name_sql, name_params, clean_q, limit)
    except Exception:
        rows = []
    if should_try_people(rows, limit):
        # 2a) Personas (director/reparto) con los índices de trigramas: es la lectura
        #     correcta de «tarantino» o «pedro pascal» y no recorre 52k fichas.
        title_sql, enrich_sql, people_params = build_people_matches(words)
        try:
            people_rows = await _search_people(
                title_sql, people_params, enrich_sql, people_params,
                " ".join(words), limit,
            )
        except Exception:
            people_rows = []
        if people_rows:
            rows = people_rows
            people_hit = True
    if should_run_full_deep(rows):
        try:
            # 2b) Búsqueda clásica profunda (sinopsis/reparto no indexado): solo si
            #     los nombres no dieron nada. Con 1-2 resultados NO se corre (costaba
            #     3.5 s en «guerra de papas»).
            deep_rows = await _search_page(deep_sql, deep_params, clean_q, limit)
            if deep_rows:
                rows = deep_rows
        except Exception:
            deep_failed = not rows
    if not rows:
        try:
            # 3) Difusa por trigramas: perdona errores de escritura («termintor»).
            rows = await _search_fuzzy(clean_q, limit)
        except Exception:
            rows = []
    if not rows and deep_failed:
        # 4) Contingencia final (la de la versión clásica): LIKE plano sin unaccent.
        like = f"%{clean_q}%"
        try:
            rows = await db().fetch(
                f"""
                select {_SEARCH_COLUMNS}
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
                """,
                like, clean_q, limit,
            )
        except Exception:
            rows = []

    portal_rows: list = []
    if portal_task is not None:
        if people_hit or not should_wait_for_portal(rows, clean_q):
            # Personas: la lista local ya es la buena, no se espera al portal.
            # Nombres: el índice local ya tiene el nombre; igual.
            portal_task.cancel()
        else:
            try:
                portal_rows = await portal_task
            except Exception:  # noqa: BLE001 - el portal jamás tumba la búsqueda
                portal_rows = []
    rows = merge_search_rows(rows, portal_rows, clean_q, limit)

    member_of, seasons_by_group, base_of_group = await _group_maps()
    enr = await _enrichment_map([r["id"] for r in rows])
    seen_groups: set[str] = set()
    seen_cids: set[str] = set()
    seen_tmdb: set[int] = set()
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
        enrichment = enr.get(r["id"])
        tmdb_key = _tmdb_dedupe_key(entry, enrichment)
        if tmdb_key:
            if tmdb_key in seen_tmdb:
                continue
            seen_tmdb.add(tmdb_key)
        _attach_original_title(entry, r, enrichment)
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
            where norm_name like 'mx:%'
              and end_time >= now() - interval '20 minutes'
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
                norm = r["norm_name"].split(":", 1)[-1]
                confirmed_channel = norm_to_display.get(norm, r["channel_name"])
                break

        if confirmed_channel:
            curr_chans = m.get("channels", [])
            new_chans = [confirmed_channel] + [c for c in curr_chans if _fold(c) != _fold(confirmed_channel)]
            m["channels"] = new_chans
            curr_broad = m.get("broadcasts", [])
            m["broadcasts"] = [confirmed_channel] + [b for b in curr_broad if _fold(b) != _fold(confirmed_channel)]

    return matches


@app.get("/v1/banners")
async def launcher_banners_endpoint(
    background: BackgroundTasks,
    x_api_key: str | None = Header(default=None),
):
    """Banners diarios del Inicio (arte panorámico TMDB) para Vision+ Home."""
    require_key(x_api_key)
    return await launcher_banners.serve(
        db(),
        background,
        tmdb_key=os.environ.get("TMDB_API_KEY", "").strip(),
    )


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
        where league_id not like '%/%'
          and (kickoff at time zone $1::text)::date
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


@app.get("/v1/sports/channels")
async def sports_channels(
    country: str | None = Query(default=None, max_length=40),
    brand: str | None = Query(default=None, max_length=60),
    q: str | None = Query(default=None, max_length=60),
    general: int = Query(default=1, ge=0, le=1),
    limit: int = Query(default=0, ge=0, le=1000),
    x_api_key: str | None = Header(default=None),
):
    """Mapa propio de la parrilla deportiva XTV: marca, feed, país y dueño.

    Es la segunda opción junto a la agenda de GeeSports: sirve para saber qué
    canal de la casa corresponde a cada emisora que publica ESPN. `general=1`
    incluye las emisoras generalistas que también transmiten deporte (Canal 5,
    Azteca 7, Telemundo, Univision…).
    """
    require_key(x_api_key)
    return sports_map.map_payload(
        country=country,
        brand=brand,
        q=q,
        include_general=bool(general),
        limit=limit or None,
    )


@app.get("/v1/sports/options")
async def sports_options(
    date: str | None = Query(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
    days: int = Query(default=1, ge=1, le=8),
    country: str = Query(default="México", max_length=40),
    x_api_key: str | None = Header(default=None),
):
    """Agenda ESPN + opciones XTV por partido, país y emisora.

    No modifica `/v1/sports` ni la lista de GeeSports: entrega una capa aparte
    para cuando el canal principal no está en la parrilla y hay que buscar la
    señal hermana en otro país (p. ej. TUDN MX ↔ TUDN USA).
    """
    require_key(x_api_key)
    payload = await sports(date=date, days=days, x_api_key=x_api_key)
    matches = [
        sports_map.options_for_event(match, preferred=country)
        for match in (payload.get("matches") or [])
    ]
    return {
        "timezone": payload.get("timezone"),
        "date": payload.get("date"),
        "days": payload.get("days"),
        "country": country,
        "matches": matches,
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
    scope: str | None = Query(default=None, pattern=r"^(main|all)$"),
    x_api_key: str | None = Header(default=None),
):
    """Eventos deportivos (fútbol, básquet, F1…). `day`(+`days`) acota por fecha
    del evento; `category` filtra por deporte (p. ej. Football); `scope=main`
    deja solo Tier 1 (portada/«Principal») y `scope=all` conserva todo."""
    require_key(x_api_key)
    where = ["kind = 'event'"]
    args: list = []
    if category:
        args.append(category.strip())
        where.append(f"category ilike ${len(args)}")
    requested_first: date | None = None
    requested_last: date | None = None
    if day:
        try:
            requested_first = date.fromisoformat(day)
        except ValueError:
            raise HTTPException(status_code=400, detail="fecha inválida")
        requested_last = requested_first + timedelta(days=days)
        # El CMS publica la fecha/hora en UTC y la app pide en hora de México. Un
        # partido de las 19:05 del día D puede estar guardado como D+1 en UTC: si
        # solo se cargara D..D+days, su tarjeta salía SIN `link_names` (el bug de
        # las listas vacías en los partidos de la noche / eventos secundarios).
        load_lo = requested_first - timedelta(days=1)
        load_hi = requested_first + timedelta(days=days + 2)
    else:
        # La respuesta arma la semana completa (ayer..+7) para el Inicio y el
        # launcher: se cargan los metadatos de TODA esa ventana, no solo del día.
        load_lo = date.today() - timedelta(days=2)
        load_hi = date.today() + timedelta(days=9)
    args.append(load_lo.isoformat())
    args.append(load_hi.isoformat())
    where.append(
        f"payload->>'date_iso' >= ${len(args) - 1}"
        f" and payload->>'date_iso' < ${len(args)}"
    )
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
        _gee_complete_event_links(event)
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
        has_sources = _gee_event_has_sources(event)
        # Los deportes desconocidos solo pasan si el CMS trae una lista real:
        # así Cricket/AFL/WWE de la app original también aparecen, sin inventar
        # categorías vacías.
        if category not in GEE_ALLOWED_SPORTS and not has_sources:
            continue
        pair = _gee_pair_key(event.get("team_a", ""), event.get("team_b", ""))
        if pair:
            bucket = pair_links.setdefault(
                _gee_gender_key(event.get("event")) + "|" + pair, []
            )
            known = {_gee_link_key(link) for link in bucket}
            for link in event.get("links") or []:
                key = _gee_link_key(link)
                if key[1] and key not in known:
                    bucket.append(link)
                    known.add(key)
        is_football = category == "football"
        match = _gee_find_match(event, entries) if _gee_sport_for_category(category) else None
        if match is not None:
            if _gee_league_allowed_match(match):
                _gee_apply_match(event, match)
                if _gee_date_of(match.get("kickoff")) in near:
                    match_id = str(match.get("id") or "")
                    bucket = links_by_match.setdefault(match_id, [])
                    known = {_gee_link_key(link) for link in bucket}
                    for link in event.get("links") or []:
                        key = _gee_link_key(link)
                        if key[1] and key not in known:
                            bucket.append(link)
                            known.add(key)
                    incoming = [
                        str(name or "").strip()
                        for name in (event.get("link_names") or [])
                        if str(name or "").strip()
                    ]
                    declared_by_match[match_id] = _gee_merge_declared(
                        declared_by_match.get(match_id, []), incoming
                    )
                    continue  # el partido se sirve como tarjeta única (abajo)
                if _gee_event_visible(event) and _gee_league_allowed_event(event):
                    out.append(event)
                continue
            # Liga fuera de la agenda principal: si el CMS la publica con sus
            # fuentes, se sirve tal cual (la app original también la enseña).
            if _gee_event_visible(event) and (
                has_sources or _gee_league_allowed_event(event)
            ):
                out.append(event)
            continue
        # Sin partido en la agenda: se sirve el evento del CMS si tiene lista.
        if _gee_event_visible(event) and (
            has_sources or not is_football or _gee_league_allowed_event(event)
        ):
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
        # El bucket va por CRUCE + GÉNERO: los enlaces de un Santos-Necaxa femenil
        # no entran en la tarjeta varonil, pero las señales hermanas del mismo
        # partido (otro día, otro nombre de canal) sí.
        extra = pair_links.get(
            _gee_gender_key(match.get("league", {}).get("name")) + "|" + _gee_pair_key(home, away),
            [],
        )
        if extra:
            # Sin duplicados por URL; si la copia del cruce viene sondeada y la primera
            # no, se queda la sondeada (los badges de la app son del momento).
            index = {_gee_link_key(link): i for i, link in enumerate(card_links)}
            for link in extra:
                key = _gee_link_key(link)
                if not key[1]:
                    continue
                previous = index.get(key)
                if previous is None:
                    index[key] = len(card_links)
                    card_links.append(link)
                elif card_links[previous].get("alive") is None and link.get("alive") is not None:
                    card_links[previous] = link
        card_links.sort(key=_gee_link_rank)  # vivos primero (estable dentro del grupo)
        out.append(_gee_match_card(match, card_links, declared_by_match.get(match_id, [])))
    # Los eventos que no tienen tarjeta de agenda también se completan con los
    # enlaces de sus entradas hermanas (el CMS publica varias por partido) y con
    # TODOS los nombres declarados. Sin esto, un evento suelto enseñaba menos
    # fuentes que la app original.
    for card in out:
        # Las tarjetas de la agenda ya salen completas de `_gee_match_card` (enlaces
        # ordenados por parrilla + nombres declarados). Completarlas otra vez con
        # slots de OTRO payload las desalinearía.
        if str(card.get("id") or "").startswith("espn:"):
            continue
        pair = _gee_pair_key(card.get("team_a", ""), card.get("team_b", ""))
        extra = pair_links.get(_gee_gender_key(card.get("event")) + "|" + pair) or []
        if extra:
            links = [lk for lk in (card.get("links") or []) if isinstance(lk, dict)]
            known = {_gee_link_key(lk) for lk in links if _gee_link_url(lk)}
            for link in extra:
                key = _gee_link_key(link)
                if key[1] and key not in known:
                    links.append(link)
                    known.add(key)
            card["links"] = links
        _gee_complete_event_links(card)
    # Un mismo partido puede llegar dos veces (tarjeta ESPN + evento del CMS con
    # los equipos escritos distinto): se fusiona en UNA con la lista más completa.
    out = _gee_merge_fixture_cards(out)
    out = [_gee_mark_tier(card) for card in out]
    if scope == "main":
        # «Principal»: solo Tier 1. El catálogo completo sigue en scope=all y en
        # la búsqueda; así se limpia la portada sin perder fuentes secundarias.
        out = [card for card in out if card.get("tier") == 1]
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
    if requested_first is not None:
        # La consulta carga días extra por el desfase UTC↔México; la respuesta
        # respeta exactamente el rango pedido (la app solo pinta Hoy/Mañana).
        wanted_days: set[str] = set()
        if requested_last == requested_first:
            wanted_days.add(requested_first.isoformat())
        else:
            cursor = requested_first
            while cursor < requested_last:
                wanted_days.add(cursor.isoformat())
                cursor += timedelta(days=1)
        deduped = [card for card in deduped if (card.get("date_iso") or "") in wanted_days]
    else:
        # Sin `day` se sirve la semana (ayer..+7); la consulta carga un día extra
        # por el desfase UTC y no debe devolver partidos ya vencidos.
        deduped = [card for card in deduped if (card.get("date_iso") or "") in near]
    # Segunda opción propia: canales XTV para CADA tarjeta final (incluidas las
    # de agenda, no solo los eventos crudos del CMS), con la regla Fox. Campo
    # ADITIVO: no cambia `links`, `broadcasts` ni la agenda de GeeSports.
    for card in deduped:
        card["xtv"] = sports_map.options_for_gee_event(card)
    return {"events": deduped}


@app.get("/v1/gee38/health")
async def gee38_health(x_api_key: str | None = Header(default=None)):
    """Estado de la fuente deportiva: frescura de metadatos y listas alineadas.

    `ok=false` significa que el worker dejó de refrescar `events.txt` o que algún
    evento tiene menos filas que nombres declarados: exactamente la regresión que
    hacía que Vision+ enseñara menos fuentes que la app original.
    """
    require_key(x_api_key)
    stats = await db().fetchrow(
        """
        select count(*) as total,
               count(*) filter (
                   where jsonb_array_length(coalesce(payload->'link_names', '[]'::jsonb)) > 0
               ) as with_names,
               count(*) filter (
                   where jsonb_array_length(coalesce(payload->'link_names', '[]'::jsonb)) > 0
                     and jsonb_array_length(coalesce(payload->'link_names', '[]'::jsonb))
                         <> jsonb_array_length(coalesce(payload->'links', '[]'::jsonb))
               ) as mismatched,
               count(*) filter (
                   where jsonb_array_length(coalesce(payload->'links', '[]'::jsonb)) > 0
               ) as with_links,
               max(updated_at) as last_event
        from gee38_catalog
        where kind = 'event'
        """
    )
    groups = await db().fetchval("select count(*) from gee38_catalog where kind = 'group'")
    meta_rows = await db().fetch(
        "select k, v from gee38_meta where k in ('meta_at', 'hot_at', 'full_at')"
    )
    stamps: dict[str, str | None] = {"meta_at": None, "hot_at": None, "full_at": None}
    for row in meta_rows:
        value = _gee_payload(row["v"])
        at = str(value.get("at") or "").strip() if isinstance(value, dict) else ""
        if at:
            stamps[row["k"]] = at
    newest = stats["last_event"]
    newest_iso = newest.isoformat() if newest else None
    reference = stamps["meta_at"] or stamps["hot_at"] or stamps["full_at"] or newest_iso
    age_seconds = None
    if reference:
        try:
            when = datetime.fromisoformat(reference.replace("Z", "+00:00"))
            if when.tzinfo is None:
                when = when.replace(tzinfo=timezone.utc)
            age_seconds = max(0, int((datetime.now(timezone.utc) - when).total_seconds()))
        except ValueError:
            age_seconds = None
    tolerance = 15 * 60
    mismatched = int(stats["mismatched"] or 0)
    fresh = age_seconds is not None and age_seconds <= tolerance
    return {
        # `mismatched` es informativo del payload crudo: la API completa las filas
        # al servir. Lo que no puede pasar es que `events.txt` deje de refrescarse.
        "ok": fresh and int(stats["with_names"] or 0) > 0,
        "events": int(stats["total"] or 0),
        "withNames": int(stats["with_names"] or 0),
        "withLinks": int(stats["with_links"] or 0),
        "rawMismatched": mismatched,
        "groups": int(groups or 0),
        "metaAt": stamps["meta_at"],
        "hotAt": stamps["hot_at"],
        "fullAt": stamps["full_at"],
        "ageSeconds": age_seconds,
        "toleranceSeconds": tolerance,
    }


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
    "pumas": {"unam"},
    "unam": {"pumas"},
    "juarez": {"bravos"},
    "bravos": {"juarez"},
    # Nombres que el CMS y la agenda escriben distinto. La lista es corta y
    # deliberada: el parecido por prefijo cubre el resto sin fusionar equipos
    # distintos («Manchester United» ≠ «Manchester City»).
    "lyon": {"lyonnais"},
    "lyonnais": {"lyon"},
    "inter": {"internazionale", "internacional"},
    "internazionale": {"inter", "milan"},
    "internacional": {"inter"},
    "milan": {"milano"},
    "milano": {"milan"},
    "psg": {"paris", "saint", "germain"},
    "paris": {"psg"},
    "saint": {"psg"},
    "germain": {"psg"},
    "munich": {"munchen"},
    "munchen": {"munich"},
    "cologne": {"koln"},
    "koln": {"cologne"},
    "nottm": {"nottingham"},
    "nottingham": {"nottm"},
    "wed": {"wednesday"},
    "wednesday": {"wed"},
    "wolves": {"wolverhampton", "wanderers"},
    "wolverhampton": {"wolves"},
    "spurs": {"tottenham", "hotspur"},
    "tottenham": {"spurs"},
    "hotspur": {"spurs"},
    "gladbach": {"monchengladbach"},
    "monchengladbach": {"gladbach"},
    "bsc": {"berlin"},
    "berlin": {"bsc"},
    "hamburger": {"hamburg"},
    "hamburg": {"hamburger"},
    "rennes": {"rennais"},
    "rennais": {"rennes"},
    "brest": {"brestois"},
    "brestois": {"brest"},
    "cp": {"lisbon", "lisboa"},
    "lisbon": {"cp", "lisboa"},
    "lisboa": {"cp", "lisbon"},
    "depor": {"deportivo"},
    "deportivo": {"depor"},
}

# Nombres completos que el CMS escribe distinto y que no se pueden resolver
# solo con tokens (si «racing» se aliasara a «santander», Racing Club de
# Argentina se confundiría con Racing de Santander).
GEE_TEAM_PHRASES = {
    "real racing club": "racing santander",
    "real racing club de santander": "racing santander",
    "racing de santander": "racing santander",
    "real racing club santander": "racing santander",
}


def _gee_tokens(text: str) -> set[str]:
    """Tokens significativos de un nombre de equipo (corta < 4 salvo los clásicos)."""
    out: set[str] = set()
    for token in _fold(text).split():
        if token in GEE_TEAM_STOP or token.isdigit():
            continue
        if len(token) >= 4 or token in (
            "psg", "rb", "vfb", "vfl", "tsg", "bsc", "cp", "st", "fc",
            "leon", "roma", "ajax", "boca", "cruz",
        ):
            out.add(token)
    return out


def _gee_expand(tokens: set[str]) -> set[str]:
    out = set(tokens)
    for token in tokens:
        out |= GEE_TEAM_ALIASES.get(token, set())
    return out


def _gee_common_prefix(a: str, b: str) -> int:
    size = 0
    for left, right in zip(a, b):
        if left != right:
            break
        size += 1
    return size


def _gee_team_similarity(left, right) -> float:
    """Parecido entre dos nombres de equipo, de 0 a 1.

    Acepta el nombre o el conjunto de tokens ya expandido (`_gee_match_entry`
    precalcula los de la agenda). Reglas, en orden:
    1. Mismos tokens (o uno contenido en el otro) → 0.95. Así «AC Milan» = «Milan»
       y «Brighton & Hove Albion» = «Brighton», pero «Manchester United» ≠
       «Manchester City» (comparten ciudad, no el equipo).
    2. Prefijo largo («Lyon» / «Olympique Lyonnais», «Inter» / «Internazionale»).
    3. Parecido global como último recurso, siempre por debajo del umbral.
    """
    if isinstance(left, str):
        left = GEE_TEAM_PHRASES.get(_fold(left), left)
    if isinstance(right, str):
        right = GEE_TEAM_PHRASES.get(_fold(right), right)
    left_tokens = _gee_expand(left if isinstance(left, set) else _gee_tokens(left))
    right_tokens = _gee_expand(right if isinstance(right, set) else _gee_tokens(right))
    if not left_tokens or not right_tokens:
        return 0.0
    if left_tokens == right_tokens:
        return 1.0
    shared = left_tokens & right_tokens
    # «Racing Club» (Argentina) y «Racing Santander» comparten «racing», pero
    # son equipos distintos. Santander solo aparece en el nombre español.
    if "racing" in shared and ("santander" in left_tokens) != ("santander" in right_tokens):
        return 0.0
    if shared:
        if left_tokens <= right_tokens or right_tokens <= left_tokens:
            return 0.95
        dice = 2.0 * len(shared) / (len(left_tokens) + len(right_tokens))
        # Un solo token compartido (la ciudad) no basta para el mismo equipo.
        return min(0.9, dice)
    prefix = 0.0
    for a in left_tokens:
        for b in right_tokens:
            if len(a) < 4 or len(b) < 4:
                continue
            size = _gee_common_prefix(a, b)
            if size >= 4:
                prefix = max(prefix, min(0.9, 0.55 + size * 0.05))
    if prefix:
        return prefix
    ratio = difflib.SequenceMatcher(
        None, " ".join(sorted(left_tokens)), " ".join(sorted(right_tokens))
    ).ratio()
    return min(0.55, ratio * 0.7)


def _gee_team_pair_similarity(
    team_a: str, team_b: str, home: str, away: str
) -> float:
    """Parecido del cruce completo, aceptando el orden invertido del CMS."""
    direct = min(_gee_team_similarity(team_a, home), _gee_team_similarity(team_b, away))
    reverse = min(_gee_team_similarity(team_a, away), _gee_team_similarity(team_b, home))
    return max(direct, reverse)


def _gee_event_visible(event: dict) -> bool:
    """La app original descarta `visible=false`; Vision+ también debe hacerlo."""
    return event.get("visible") is not False


def _gee_event_has_sources(event: dict) -> bool:
    """¿El evento trae algo que mostrar? (nombres declarados o direcciones)."""
    if _gee_link_names(event):
        return True
    for link in event.get("links") or []:
        if not isinstance(link, dict):
            continue
        if (
            _gee_link_url(link)
            or str(link.get("link") or "").strip()
            or str(link.get("token_api") or "").strip()
        ):
            return True
    return False


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


def _gee_gender_key(text: str) -> str:
    """«w» o «m» para separar los buckets de enlaces por competición/género."""
    return "w" if _gee_is_women(text) else "m"


def _gee_is_women(text: str) -> bool:
    """¿La competición es femenil? Evita cruzar Liga MX Femenil con Liga MX."""
    folded = _fold(text or "")
    return "women" in folded or "femen" in folded


def _gee_find_match(event: dict, entries: list[tuple]) -> dict | None:
    """El partido de la agenda que corresponde al evento (equipos + horario).

    El CMS y ESPN escriben los equipos distinto («Olympique Lyonnais» / «Lyon»,
    «Inter» / «Internazionale», «AC Milan» / «Milan»). Comparar solo tokens
    exactos dejaba la tarjeta de ESPN SIN la lista de canales mientras el evento
    suelto del CMS sí la tenía (dos tarjetas del mismo partido). Aquí manda el
    parecido de los DOS equipos, con el horario (36 h) como desempate; el deporte
    sigue siendo un candado duro.
    """
    name = event.get("event") or ""
    team_a = (event.get("team_a") or "").strip()
    team_b = (event.get("team_b") or "").strip()
    if not team_a or not team_b or _fold(team_a) == _fold(team_b):
        return None
    if _fold(team_a) == _fold(name) or _fold(team_b) == _fold(name):
        return None  # el CMS repite la liga: no hay equipos reales que cruzar
    event_dt = _gee_event_dt(event)
    wanted = _gee_sport_for_category(event.get("category") or "")
    league_name = _gee_norm_league(name)
    best: dict | None = None
    best_score = float("-inf")
    for match, home, away, match_dt in entries:
        if not home or not away:
            continue
        if wanted and _gee_sport_of_league_id((match.get("league") or {}).get("id") or "") != wanted:
            continue
        teams = _gee_team_pair_similarity(team_a, team_b, home, away)
        if teams < 0.6:
            continue
        dist_min = None
        if event_dt and match_dt:
            dist_min = abs((event_dt - match_dt).total_seconds()) / 60.0
            if dist_min > 36 * 60:
                continue
        # La liga desempata dos partidos del mismo deporte a la misma hora.
        match_league = _gee_norm_league((match.get("league") or {}).get("name") or "")
        # Un Santos-Necaxa femenil no puede cruzar al Santos-Necaxa varonil: los
        # equipos y la fecha coinciden, pero es OTRO partido (y sus enlaces son otros).
        if _gee_is_women(name) != _gee_is_women(match_league):
            continue
        league_bonus = 0.0
        if league_name and match_league:
            if league_name == match_league:
                league_bonus = 30.0
            elif league_name in match_league or match_league in league_name:
                league_bonus = 15.0
        score = teams * 1000.0 + league_bonus
        if dist_min is not None:
            score -= min(dist_min, 600.0) / 6.0
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
    event["league_id"] = league.get("id") or event.get("league_id") or ""
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
    """Adjunta al evento el marcador/logos/estado del partido que le corresponde.

    Sirve para fútbol y para los deportes de equipo (NBA, NFL, MLB, NHL): cada
    evento solo se cruza con partidos de SU deporte. Los deportes sin agenda de
    equipos (F1, por ejemplo) pasan intactos.
    """
    if not _gee_sport_for_category(event.get("category") or ""):
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
    "tudn", "tudnusa", "tudncom", "tudnapp", "vix", "universo", "network", "usanetwork",
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

# Tier 1 = portada/«Principal». Se compara el nombre normalizado completo, nunca
# por subcadena: «LaLiga 2» no es LaLiga ni «Russian Premier League» es Premier.
GEE_MAIN_LEAGUE_NAMES = {
    "liga mx", "liga de expansion mx",
    "uefa champions league", "uefa europa league",
    "laliga", "premier league", "serie a", "bundesliga", "ligue 1",
    "copa libertadores", "copa sudamericana",
    "liga profesional argentina", "liga profesional de futbol",
    "brasileirao", "campeonato brasileiro serie a",
    "mls", "major league soccer", "primeira liga", "liga portugal",
    "liga portugal betclic", "eredivisie", "vriendenloterij eredivisie",
    "copa del mundo", "fifa world cup", "copa mundial",
    "eurocopa", "uefa euro", "copa america",
    "concacaf nations league", "copa oro", "concacaf gold cup",
    "nba", "nfl", "mlb", "nhl", "formula 1", "formula one", "f1",
    "motogp", "nascar", "ufc", "mma", "mixed martial arts", "boxeo", "boxing",
}

# Señales de una categoría inferior. Se evalúan después de la lista principal,
# porque «Liga de Expansión MX» es principal aunque contenga «expansion».
GEE_SECONDARY_MARKERS = (
    " 2", " ii", " b", "women", "femen", "femenil", "u17", "u18", "u19",
    "u20", "u21", "u23", "youth", "juvenil", "reserve", "reservas",
    "championship", "segunda", "primera b", "serie b", "2 bundesliga",
    "ligue 2", "laliga 2", "liga 2", "liga 1 te apuesto", "primera a",
    "primera division", "segunda division", "a-league", "k league",
    "j1 league", "j2 league", "allsvenskan", "super lig", "süper lig",
    "scottish", "russian premier", "kenya premier", "indian super",
    "canadian premier", "nwsl", "wsl", "liga f", "liga mx femenil",
)

# Los deportes que sí se muestran (los demás no se sirven). Solo aparecen si
# GeeSports publica eventos de ese deporte con sus fuentes; no se inventan.
GEE_ALLOWED_SPORTS = {
    "football", "basketball", "american football", "motorsports", "baseball", "hockey",
    "tennis", "fighting", "boxing", "mma", "pelea", "peleas", "boxeo",
    "motorsport",
}

# Ligas de equipo de otros deportes que el worker sincroniza (`deporte/slug`).
GEE_MULTISPORT_LEAGUE_IDS = {
    "basketball/nba", "basketball/euroleague",
    "football/nfl", "baseball/mlb", "hockey/nhl",
}

# Categoría de GeeSports → deporte de ESPN (para no cruzar un partido de NBA
# con uno de Liga MX aunque compartan un nombre de equipo).
GEE_SPORT_FOR_CATEGORY = {
    "football": "soccer",
    "basketball": "basketball",
    "american football": "football",
    "baseball": "baseball",
    "hockey": "hockey",
    "tennis": "tennis",
}

GEE_CATEGORY_FOR_SPORT = {
    "soccer": "Football",
    "basketball": "Basketball",
    "football": "American Football",
    "baseball": "Baseball",
    "hockey": "Hockey",
    "tennis": "Tennis",
}


def _gee_sport_of_league_id(league_id: str) -> str:
    value = (league_id or "").strip().lower()
    return value.split("/", 1)[0] if "/" in value else "soccer"


def _gee_sport_for_category(category: str) -> str:
    return GEE_SPORT_FOR_CATEGORY.get((category or "").strip().lower(), "")


def _gee_category_for_sport(sport: str) -> str:
    return GEE_CATEGORY_FOR_SPORT.get((sport or "").strip().lower(), "Football")


def _gee_norm_league(text: str) -> str:
    """Nombre de liga comparable: sin tildes, sin la cola («Liga MX, Apertura»)."""
    lowered = unicodedata.normalize("NFD", (text or "").lower())
    lowered = "".join(c for c in lowered if unicodedata.category(c) != "Mn")
    lowered = lowered.split(",")[0]
    return re.sub(r"[^a-z0-9]+", " ", lowered).strip()


def _gee_league_allowed_match(match: dict) -> bool:
    league = match.get("league") or {}
    league_id = (league.get("id") or "") or ""
    if "/" in league_id:
        return league_id in GEE_MULTISPORT_LEAGUE_IDS
    return league_id in GEE_LEAGUE_IDS


def _gee_league_allowed_event(event: dict) -> bool:
    label = event.get("league_label") or event.get("event") or ""
    return _gee_norm_league(label) in GEE_LEAGUE_NAMES


def _gee_is_secondary_league(label: str) -> bool:
    folded = f" {_gee_norm_league(label)} "
    return any(f" {_gee_norm_league(marker)} " in folded for marker in GEE_SECONDARY_MARKERS)


def _gee_tier_for(league_id: str, league_label: str, category: str) -> int:
    """1 = principal, 2 = secundaria, 3 = nicho.

    Manda el `league_id` de la agenda; si no hay, se compara el nombre normalizado
    completo. Un deporte fuera de la lista principal siempre es Tier 3.
    """
    sport = (category or "").strip().lower()
    if sport not in GEE_ALLOWED_SPORTS:
        return 3
    league_id = (league_id or "").strip()
    if league_id in GEE_LEAGUE_IDS or league_id in GEE_MULTISPORT_LEAGUE_IDS:
        return 1
    label = _gee_norm_league(league_label)
    if not label:
        return 2
    if label in GEE_MAIN_LEAGUE_NAMES:
        return 1
    if _gee_is_secondary_league(label):
        return 2
    return 2


def _gee_mark_tier(card: dict) -> dict:
    league = card.get("league") if isinstance(card.get("league"), dict) else {}
    tier = _gee_tier_for(
        str(card.get("league_id") or league.get("id") or ""),
        str(card.get("league_label") or card.get("event") or ""),
        str(card.get("category") or ""),
    )
    card["tier"] = tier
    card["featured"] = tier == 1
    return card


def _gee_link_names(event: dict) -> list[str]:
    """Los nombres que la fuente declara para el evento, en su orden y sin vacíos."""
    names: list[str] = []
    for raw in event.get("link_names") or []:
        clean = str(raw or "").strip()
        if clean:
            names.append(clean)
    return names


def _gee_link_url(link: dict) -> str:
    return str(link.get("url") or link.get("link") or "").strip()


def _gee_merge_declared(existing: list[str], incoming: list[str]) -> list[str]:
    """Une nombres declarados conservando las repeticiones del MISMO evento.

    La app original puede listar dos veces el mismo nombre («ESPN HD» en dos
    ranuras). Vision+ no debe colapsarlas: si una entrada hermana repite el
    nombre, se conserva el máximo de apariciones, no una por nombre.
    """
    result = list(existing)
    have: dict[str, int] = {}
    for name in result:
        have[name] = have.get(name, 0) + 1
    want: dict[str, int] = {}
    for name in incoming:
        want[name] = want.get(name, 0) + 1
    for name in incoming:
        while have.get(name, 0) < want.get(name, 0):
            result.append(name)
            have[name] = have.get(name, 0) + 1
    return result


def _gee_link_key(link: dict) -> tuple[str, str]:
    """Identidad de una fila: nombre + dirección (o el token de un `embed`).

    No se deduplica solo por URL: la app original puede declarar dos nombres
    distintos («FASTER QUALITY» y «XTREME +FHD») apuntando al mismo stream y
    ambos son filas de la lista. Solo se colapsa la MISMA fila repetida.
    """
    url = _gee_link_url(link)
    token = str(link.get("token_api") or "").strip()
    return _fold(str(link.get("name") or "")), (url or ("embed:" + token if token else ""))


def _gee_assign_link_slots(names: list[str], links: list[dict]) -> None:
    """Coloca cada enlace en su ranura de `link_names` por NOMBRE actual.

    La app original muestra `link_names` y al tocar una fila resuelve
    `pro[indice]`. Cuando el CMS añade/reordena nombres, los slots guardados
    quedan corridos: manda el nombre coincidente y solo los enlaces sin nombre
    conocido conservan su slot. Así una lista nueva de 12 nombres no se recorta
    a los 8 de una copia vieja.
    """
    positions: dict[str, list[int]] = {}
    for index, name in enumerate(names):
        positions.setdefault(_fold(name), []).append(index)
    used: set[int] = set()
    matched: set[int] = set()
    for link in links:
        for index in positions.get(_fold(str(link.get("name") or "")), []):
            if index not in used:
                link["slot"] = index
                used.add(index)
                matched.add(id(link))
                break
    for link in links:
        if id(link) in matched:
            continue
        slot = link.get("slot")
        if isinstance(slot, int) and 0 <= slot < len(names) and slot not in used:
            used.add(slot)
        else:
            link.pop("slot", None)


def _gee_pending_link(name: str, slot: int) -> dict:
    """Una ranura declarada por la fuente que aún no publica su dirección."""
    return {
        "name": name,
        "link": "",
        "url": "",
        "headers": {},
        "scheme": 0,
        "drm": "",
        "type": "hls",
        "alive": None,
        "checked_at": None,
        "pending": True,
        "slot": slot,
    }


def _gee_complete_event_links(event: dict) -> dict:
    """Deja la lista COMPLETA, como la app original: una fila por `link_names`.

    El CMS publica los nombres del evento aparte del archivo `pro` y ambos pueden
    no coincidir: un `pro` parcial (o con los nombres de otro partido) hacía que
    Vision+ enseñara menos fuentes que la app original. Aquí se cruzan por ranura
    (la misma que usa su reproductor) y los nombres sin dirección se entregan como
    ranuras pendientes, visibles pero sin reproducir hasta que la fuente las sirva.
    """
    names = _gee_link_names(event)
    links = [dict(link) for link in event.get("links") or [] if isinstance(link, dict)]
    if not names:
        event["links"] = links
        event["link_names"] = [
            str(link.get("name") or "").strip() for link in links if str(link.get("name") or "").strip()
        ]
        event["total"] = len(links)
        event["alive"] = sum(1 for link in links if link.get("alive") is True)
        return event

    _gee_assign_link_slots(names, links)
    by_slot: dict[int, dict] = {}

    def _rank(link: dict, slot: int) -> tuple:
        # Primero la identidad (nombre declarado para la ranura); después la que
        # responde. El `pro` a veces trae el nombre de otro partido o los índices
        # cambiaron, y no queremos pegar la URL de un canal a otro nombre.
        name_ok = 0 if _fold(str(link.get("name") or "")) == _fold(names[slot]) else 1
        return (name_ok, _gee_link_rank(link))

    for link in links:
        url = _gee_link_url(link)
        if not url and not str(link.get("token_api") or "").strip():
            continue
        slot = link.get("slot")
        if not isinstance(slot, int) or not (0 <= slot < len(names)):
            continue  # fuera de los nombres declarados: la app original no lo enseña
        previous = by_slot.get(slot)
        if previous is None:
            by_slot[slot] = link
        elif _rank(link, slot) < _rank(previous, slot):
            # Misma fuente/ranura: se queda la mejor copia, no se duplica la fila.
            by_slot[slot] = link

    out: list[dict] = []
    for index, name in enumerate(names):
        link = by_slot.pop(index, None)
        if link is None:
            out.append(_gee_pending_link(name, index))
            continue
        filled = dict(link)
        filled["name"] = name
        filled["slot"] = index
        filled.pop("pending", None)
        if not _gee_link_url(filled) and str(filled.get("token_api") or "").strip():
            filled["resolvable"] = True
        out.append(filled)
    event["links"] = out
    event["link_names"] = names
    event["total"] = len(out)
    event["alive"] = sum(1 for link in out if link.get("alive") is True)
    return event


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
    names = [str(name or "").strip() for name in (declared or []) if str(name or "").strip()]
    # Los enlaces de entradas hermanas del mismo cruce también entran: si traen un
    # nombre que el evento actual no declara, se añade al final (nunca se pierde una
    # fuente viva por una copia del CMS más pobre).
    known = {_fold(name) for name in names}
    for link in links:
        clean = str(link.get("name") or "").strip()
        if clean and _fold(clean) not in known:
            names.append(clean)
            known.add(_fold(clean))
    if not names:
        names = [
            str(link.get("name") or "").strip()
            for link in links
            if str(link.get("name") or "").strip()
        ]
    home = match.get("home") or {}
    away = match.get("away") or {}
    league = match.get("league") or {}
    sport = match.get("sport") or _gee_sport_of_league_id(league.get("id") or "")
    category = _gee_category_for_sport(sport)
    kickoff = _gee_dt(match.get("kickoff"))
    status = (match.get("status") or "").strip()
    guide: list[str] = []
    for name in list(match.get("broadcasts") or []) + list(match.get("channels") or []):
        clean = (name or "").strip()
        if clean and clean not in guide:
            guide.append(clean)
    card = {
        "id": "espn:" + str(match.get("id") or ""),
        "category": category,
        "event": (league.get("name") or "").strip() or ("Fútbol" if sport == "soccer" else category),
        "logo": league.get("logo") or "",
        "team_a": home.get("name") or "",
        "team_b": away.get("name") or "",
        "date_iso": kickoff.date().isoformat() if kickoff else "",
        "date": kickoff.strftime("%d/%m/%Y") if kickoff else "",
        "time": kickoff.strftime("%H:%M:%S") if kickoff else "",
        "visible": True,
        "priority": 0,
        "link_names": names,
        "links": links,
        "alive": sum(1 for link in links if link.get("alive")),
        "total": len(links),
        "home_name": home.get("name") or "",
        "away_name": away.get("name") or "",
        "home_logo": home.get("logo") or "",
        "away_logo": away.get("logo") or "",
        "league_label": league.get("name") or "",
        "league_logo": league.get("logo") or "",
        "league_id": league.get("id") or "",
        "tier": _gee_tier_for(
            str(league.get("id") or ""),
            str(league.get("name") or ""),
            category,
        ),
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
    return _gee_complete_event_links(card)


def _gee_sport_family(category: str) -> str:
    """Familia de deporte para fusionar etiquetas del CMS que significan lo mismo.

    El CMS publica la misma pelea como «Boxing» y «Mixed Martial Arts»; la app
    original la enseña una vez. No se cruzan fútbol con fútbol americano.
    """
    value = (category or "").strip().lower()
    if value in ("boxing", "mma", "mixed martial arts", "fighting", "pelea", "peleas", "boxeo"):
        return "fighting"
    if value in ("motorsports", "motorsport"):
        return "motorsports"
    return value


def _gee_time_minutes(event: dict) -> int | None:
    """Minutos desde medianoche de `time` (HH:MM:SS); None si no se puede leer."""
    parts = str(event.get("time") or "").strip().split(":")
    if len(parts) < 2:
        return None
    try:
        return int(parts[0]) * 60 + int(parts[1])
    except ValueError:
        return None


def _gee_same_fixture(left: dict, right: dict) -> bool:
    """¿Dos tarjetas son el mismo partido aunque los nombres no coincidan?

    Se exige la misma familia de deporte y el mismo día (ya en hora de México) y
    que los DOS equipos se parezcan. No fusiona «Manchester United» con
    «Manchester City» ni dos partidos distintos de la misma liga a la misma hora.
    En deportes con doble cartelera (MLB/NBA/NHL) dos juegos de los mismos equipos
    el mismo día se separan por la hora; en pelea se tolera el desfase del CMS.
    """
    family = _gee_sport_family(left.get("category") or "")
    if family != _gee_sport_family(right.get("category") or ""):
        return False
    if (left.get("date_iso") or "") != (right.get("date_iso") or ""):
        return False
    left_a, left_b = left.get("team_a") or "", left.get("team_b") or ""
    right_a, right_b = right.get("team_a") or "", right.get("team_b") or ""
    if not left_a or not left_b or not right_a or not right_b:
        return False
    if _gee_team_pair_similarity(left_a, left_b, right_a, right_b) < 0.75:
        return False
    left_min = _gee_time_minutes(left)
    right_min = _gee_time_minutes(right)
    if left_min is None or right_min is None:
        return True
    gap = abs(left_min - right_min)
    # Dos juegos de los mismos equipos el mismo día (doble cartelera) suelen
    # estar a 3+ horas; una pelea duplicada por el CMS puede diferir ~2.5 h.
    limit = 120 if family in ("baseball", "basketball", "hockey", "american football") else 300
    return gap <= limit


def _gee_merge_links(primary: list[dict], secondary: list[dict]) -> list[dict]:
    """Une dos listas de enlaces sin perder filas declaradas ni duplicar la misma."""
    out: list[dict] = []
    index: dict[tuple[str, str], int] = {}
    for link in list(primary) + list(secondary):
        if not isinstance(link, dict):
            continue
        key = _gee_link_key(link)
        if not key[0] and not key[1]:
            continue
        previous = index.get(key)
        if previous is None:
            index[key] = len(out)
            out.append(dict(link))
            continue
        current = out[previous]
        # Misma fila: se queda la copia con dirección y la que responde.
        if not _gee_link_url(current) and _gee_link_url(link):
            out[previous] = dict(link)
        elif current.get("alive") is not True and link.get("alive") is True:
            out[previous] = dict(link)
    return out


def _gee_merge_fixture_cards(cards: list[dict]) -> list[dict]:
    """Una sola tarjeta por partido, con la lista MÁS COMPLETA de la fuente.

    El CMS puede publicar el mismo partido con nombres distintos a los de ESPN
    («Olympique Lyonnais» vs «Lyon») y el cruce no siempre los une. Sin esto, la
    app enseñaba dos tarjetas: una con la lista y otra sin ella. La tarjeta de la
    agenda aporta marcador/escudos; los nombres y enlaces se toman de la copia
    más rica y se fusionan las demás.
    """
    # Solo se comparan tarjetas del mismo deporte y día: un catálogo completo
    # (sin filtro de fecha) no paga un O(n²) contra todos los eventos.
    buckets: dict[tuple[str, str], list[dict]] = {}
    for card in cards:
        key = (
            _gee_sport_family(card.get("category") or ""),
            (card.get("date_iso") or "").strip(),
        )
        buckets.setdefault(key, []).append(card)

    merged_out: list[dict] = []
    for bucket in buckets.values():
        groups: list[list[dict]] = []
        for card in bucket:
            for group in groups:
                if any(_gee_same_fixture(card, other) for other in group):
                    group.append(card)
                    break
            else:
                groups.append([card])
        merged_out.extend(_gee_merge_groups(groups))
    return merged_out


def _gee_merge_groups(groups: list[list[dict]]) -> list[dict]:
    merged_out: list[dict] = []
    for group in groups:
        if len(group) == 1:
            merged_out.append(group[0])
            continue
        agenda = next(
            (card for card in group if str(card.get("id") or "").startswith("espn:")),
            None,
        )
        # La tarjeta del CMS con `links_ref` es la fuente AUTORIZADA del partido.
        # La de agenda (ESPN) puede traer enlaces heredados de un hermano del mismo
        # cruce pero de OTRA competición (Santos-Necaxa femenil): si hay CMS con
        # enlaces, sus nombres y URLs mandan y la agenda no contamina la lista.
        cms_with_links = [
            card for card in group
            if not str(card.get("id") or "").startswith("espn:")
            and str(card.get("links_ref") or "").strip()
            and any(
                isinstance(link, dict) and (
                    _gee_link_url(link)
                    or str(link.get("link") or "").strip()
                    or str(link.get("token_api") or "").strip()
                )
                for link in (card.get("links") or [])
            )
        ]
        base = agenda or max(
            group,
            key=lambda card: (
                len(card.get("link_names") or []),
                len(card.get("links") or []),
            ),
        )
        names_base = max(
            cms_with_links or group,
            key=lambda card: (
                len(card.get("link_names") or []),
                len(card.get("links") or []),
            ),
        )
        names = [str(n).strip() for n in (names_base.get("link_names") or []) if str(n).strip()]
        links: list[dict] = []
        for card in group:
            if cms_with_links and not str(card.get("links_ref") or "").strip():
                continue
            links = _gee_merge_links(links, list(card.get("links") or []))
            incoming = [str(n).strip() for n in (card.get("link_names") or []) if str(n).strip()]
            names = _gee_merge_declared(names, incoming)
        merged = dict(base)
        merged["link_names"] = names
        merged["links"] = links
        merged.pop("total", None)
        merged.pop("alive", None)
        _gee_complete_event_links(merged)
        merged_out.append(merged)
    return merged_out


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
        out = []
        for record in rows:
            match = sport_match(record)
            match["sport"] = _gee_sport_of_league_id((match.get("league") or {}).get("id") or "")
            out.append(_gee_match_entry(match))
        return out
    except Exception:  # noqa: BLE001 - sin agenda, los eventos salen sin marcador
        return []



# ----- guía electrónica de programación (EPG) -----
# La clave es `<país>:<cadena>` (mx:tudn, cl:canal13…): el país evita que un
# «Canal 5 de Honduras» muestre la programación de «Canal 5 de México». El
# llamador pide una lista de claves en orden de prioridad y se usa la PRIMERA
# con datos: nunca se mezclan filas de dos países.


def _epg_brief(row) -> dict | None:
    if row is None:
        return None
    return {
        "name": row["title"],
        "description": row["description"] or "",
        "startMs": int(row["start_time"].timestamp() * 1000),
        "endMs": int(row["end_time"].timestamp() * 1000),
    }


def _epg_program(row) -> dict:
    return {
        "name": row["title"],
        "description": row["description"] or "",
        "start": row["start_time"].isoformat(),
        "end": row["end_time"].isoformat(),
        "startMs": int(row["start_time"].timestamp() * 1000),
        "endMs": int(row["end_time"].timestamp() * 1000),
    }


def _epg_unique(rows) -> list:
    """Una sola parrilla por canal: quita duplicados y ventanas solapadas.

    Los feeds pueden traer dos copias desplazadas de la misma cadena (Sony) o
    varias señales de la misma marca con la misma ventana. Antes se deduplicaba
    solo la ventana exacta; las copias desplazadas quedaban y la app podía
    mostrar el programa equivocado. Se ordena por inicio y se descarta cualquier
    fila que empiece antes de que termine la anterior (la primera fuente manda).
    """
    seen: set[tuple] = set()
    out = []
    last_end = None
    for row in rows:
        key = (row["start_time"], row["end_time"])
        if key in seen:
            continue
        start = row["start_time"]
        if last_end is not None and start < last_end:
            continue
        seen.add(key)
        out.append(row)
        last_end = row["end_time"]
    return out


def _epg_pick(rows, norms: list[str]) -> tuple[str, list]:
    """Primera clave de `norms` con filas (y solo esas filas). Puro y testeable."""
    by_norm: dict[str, list] = {}
    for row in rows:
        by_norm.setdefault(row["norm_name"], []).append(row)
    for norm in norms:
        if by_norm.get(norm):
            return norm, _epg_unique(by_norm[norm])[:12]
    return "", []


def _epg_window(rows, now_ms: int) -> tuple[dict | None, dict | None]:
    """(AHORA, DESPUÉS) de una lista ordenada por inicio. Puro y testeable."""
    current = next(
        (
            row for row in rows
            if int(row["start_time"].timestamp() * 1000) <= now_ms
            < int(row["end_time"].timestamp() * 1000)
        ),
        None,
    )
    upcoming = next(
        (
            row for row in rows
            if int(row["start_time"].timestamp() * 1000) > now_ms
        ),
        None,
    )
    return _epg_brief(current), _epg_brief(upcoming)


def _epg_schedulable(name: str) -> bool:
    """¿Es un canal con parrilla real? Los «24/7» y «ALQUILER» son maratones/VOD."""
    text = (name or "").upper()
    return "24/7" not in text and not text.startswith("ALQUILER")


async def _epg_snapshot() -> dict:
    """Foto de la guía: filas, canales, cobertura real del catálogo y frescura."""
    totals = await db().fetchrow(
        """
        select count(*) as rows_total,
               count(*) filter (where end_time > now()) as future,
               count(distinct norm_name) as norms,
               count(distinct country) as countries,
               max(updated_at) as last_update
        from epg_programs
        """
    )
    by_country = await db().fetch(
        """
        select country,
               count(*) filter (where end_time > now()) as future,
               count(distinct norm_name) as norms
        from epg_programs
        group by 1 order by 2 desc
        """
    )
    live_norms = {
        row["norm_name"]
        for row in await db().fetch(
            "select distinct norm_name from epg_programs where end_time > now()"
        )
    }
    channels = await db().fetch(
        """
        select t.title, t.play_ref, tc.position
        from titles t
        join title_categories tc on tc.title_id = t.id
        where tc.category_id = 'tv-en-vivo'
        order by tc.position asc
        """
    )
    with_guide = 0
    schedulable = 0
    with_guide_schedulable = 0
    for channel in channels:
        is_schedulable = _epg_schedulable(channel["title"])
        if is_schedulable:
            schedulable += 1
        code = (channel["play_ref"] or "").split(":")[-1]
        if any(norm in live_norms for norm in epg_norms_for(channel["title"], code)):
            with_guide += 1
            if is_schedulable:
                with_guide_schedulable += 1
    # El launcher pinta México primero (y dentro de México sus canales de
    # referencia): esa es la cobertura que de verdad ve el usuario en el mosaico.
    mexico_first = sorted(
        channels,
        key=lambda channel: 0 if channel_country(channel["title"]) == "mx" else 1,
    )

    def _guide_count(items) -> int:
        total = 0
        for channel in items:
            code = (channel["play_ref"] or "").split(":")[-1]
            if any(norm in live_norms for norm in epg_norms_for(channel["title"], code)):
                total += 1
        return total

    top30 = _guide_count(mexico_first[:30])
    top60 = _guide_count(mexico_first[:60])
    top_with_guide = _guide_count(mexico_first[:120])
    last = totals["last_update"]
    now = datetime.now(timezone.utc)
    stale = bool(last and (now - last).total_seconds() > 8 * 3600)
    last_cycle = await db().fetchval(
        "select value from sync_meta where key = 'epg_last_cycle'"
    )
    try:
        cycle = json.loads(last_cycle) if last_cycle else None
    except Exception:
        cycle = None
    total_channels = len(channels)
    return {
        "rows": int(totals["rows_total"] or 0),
        "future": int(totals["future"] or 0),
        "norms": int(totals["norms"] or 0),
        "countries": int(totals["countries"] or 0),
        "lastUpdate": last.isoformat() if last else None,
        "stale": stale,
        "channels": total_channels,
        "withGuide": with_guide,
        "coveragePct": round(100.0 * with_guide / total_channels) if total_channels else 0,
        "schedulable": schedulable,
        "withGuideSchedulable": with_guide_schedulable,
        "coverageSchedulablePct": round(100.0 * with_guide_schedulable / schedulable) if schedulable else 0,
        "topChannels": min(120, total_channels),
        "topWithGuide": top_with_guide,
        "topCoveragePct": round(100.0 * top_with_guide / min(120, total_channels)) if total_channels else 0,
        "top30WithGuide": top30,
        "top30CoveragePct": round(100.0 * top30 / min(30, total_channels)) if total_channels else 0,
        "top60WithGuide": top60,
        "top60CoveragePct": round(100.0 * top60 / min(60, total_channels)) if total_channels else 0,
        "byCountry": {
            row["country"] or "?": {
                "future": int(row["future"] or 0),
                "norms": int(row["norms"] or 0),
            }
            for row in by_country
        },
        "lastCycle": cycle,
    }


@app.get("/v1/epg")
async def epg(
    channel: str | None = Query(default=None),
    code: str | None = Query(default=None),
    x_api_key: str | None = Header(default=None),
):
    """Guía de programación (EPG) para TV en vivo.

    Devuelve el programa en curso ("AHORA") y los siguientes para el canal
    solicitado, con marcas de tiempo en milisegundos de época. La clave se
    resuelve con el país del canal: una señal de Chile no recibe la guía de México.
    """
    require_key(x_api_key)
    norms = epg_norms_for(channel or "", code or "")
    if not norms:
        return {"channel": channel or "", "norm": "", "country": "", "programs": []}

    rows = await db().fetch(
        """
        select norm_name, title, description, start_time, end_time
        from epg_programs
        where norm_name = any($1::text[])
          and end_time >= now() - interval '20 minutes'
        order by start_time asc
        """,
        norms,
    )
    norm, chosen = _epg_pick(rows, norms)
    return {
        "channel": channel or "",
        "norm": norm,
        "country": norm.split(":", 1)[0] if norm else "",
        "programs": [_epg_program(row) for row in chosen],
    }


@app.post("/v1/epg/bulk")
async def epg_bulk(request: Request, x_api_key: str | None = Header(default=None)):
    """Guía AHORA/DESPUÉS de hasta 400 canales en UNA petición.

    El launcher de TV pide la guía de 120 canales al armar su mosaico; con este
    endpoint no necesita 120 viajes HTTP. Devuelve solo lo que el mosaico pinta
    (nombre y ventana), no la lista completa de programas.
    """
    require_key(x_api_key)
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="JSON inválido")
    channels = body.get("channels") if isinstance(body, dict) else None
    if not isinstance(channels, list) or not channels:
        return {"channels": {}, "generatedAt": int(time.time() * 1000)}
    if len(channels) > 400:
        raise HTTPException(status_code=400, detail="máximo 400 canales")

    wanted: list[tuple[str, list[str]]] = []
    all_norms: list[str] = []
    for item in channels:
        if not isinstance(item, dict):
            continue
        code = str(item.get("code") or "")
        name = str(item.get("name") or "")
        norms = epg_norms_for(name, code)
        if not norms:
            continue
        wanted.append((code or name, norms))
        all_norms.extend(norms)
    if not wanted:
        return {"channels": {}, "generatedAt": int(time.time() * 1000)}

    rows = await db().fetch(
        """
        select norm_name, title, description, start_time, end_time
        from epg_programs
        where norm_name = any($1::text[])
          and end_time >= now() - interval '20 minutes'
        order by start_time asc
        """,
        list(dict.fromkeys(all_norms)),
    )
    by_norm: dict[str, list] = {}
    for row in rows:
        by_norm.setdefault(row["norm_name"], []).append(row)

    now_ms = int(time.time() * 1000)
    out: dict[str, dict] = {}
    for key, norms in wanted:
        norm = next((candidate for candidate in norms if by_norm.get(candidate)), "")
        if not norm:
            continue
        now, upcoming = _epg_window(_epg_unique(by_norm[norm]), now_ms)
        out[key] = {"norm": norm, "now": now, "next": upcoming}
    return {"channels": out, "generatedAt": now_ms}


@app.get("/v1/epg/health")
async def epg_health(x_api_key: str | None = Header(default=None)):
    """Estado de la guía: frescura, filas vigentes y cobertura real del catálogo."""
    require_key(x_api_key)
    return await _epg_snapshot()


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


# `session` = latido por apertura de la app (no es un fallo: es el denominador del SLO).
DIAG_KINDS = ("crash", "anr", "anomaly", "feedback", "session")

# Objetivo de calidad: % de aperturas sin crash. Bajarlo de aquí es una regresión real.
DIAG_CRASH_FREE_TARGET = 99.0


def crash_free_pct(sessions: int, crashed: int) -> float:
    """% de sesiones sin crash (puro). Sin sesiones no hay dato: devuelve 100.0."""
    if sessions <= 0:
        return 100.0
    clean = max(0, sessions - max(0, crashed))
    return round(100.0 * clean / sessions, 2)


def diag_health_payload(rows, target: float = DIAG_CRASH_FREE_TARGET) -> dict:
    """Resumen de salud por versión (puro, testeable sin base de datos).

    `rows`: filas con `app_version`, `version_code`, `sessions`, `crashed`, `anr`.
    """
    versions = []
    total_sessions = total_crashed = total_anr = 0
    for row in rows:
        sessions = int(row["sessions"] or 0)
        crashed = int(row["crashed"] or 0)
        anrs = int(row["anr"] or 0)
        total_sessions += sessions
        total_crashed += crashed
        total_anr += anrs
        versions.append({
            "version": str(row["app_version"] or "?"),
            "versionCode": int(row["version_code"] or 0),
            "sessions": sessions,
            "crashedSessions": crashed,
            "anrSessions": anrs,
            "crashFreePct": crash_free_pct(sessions, crashed),
            "anrFreePct": crash_free_pct(sessions, anrs),
        })
    overall = crash_free_pct(total_sessions, total_crashed)
    return {
        "sessions": total_sessions,
        "crashedSessions": total_crashed,
        "anrSessions": total_anr,
        "crashFreePct": overall,
        "anrFreePct": crash_free_pct(total_sessions, total_anr),
        "target": target,
        "meetsTarget": overall >= target,
        "versions": versions,
    }


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
    if kind not in DIAG_KINDS:
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
          and kind <> 'session'
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


@app.get("/v1/diag/health")
async def diag_health(
    days: int = Query(default=7, ge=1, le=90),
    x_api_key: str | None = Header(default=None),
):
    """Salud real de la app: % de aperturas sin crash y sin ANR, por versión.

    El denominador son los latidos `session` que la app manda al abrir (uno por apertura);
    sin ellos solo se verían los fallos, no la proporción. Un crash del proceso `:x4k`
    cuenta contra la sesión en la que ocurrió (mismo `payload.session`).
    """
    require_key(x_api_key)
    rows = await db().fetch(
        """
        with s as (
            select app_version, version_code, coalesce(payload->>'session', '') as sid
            from diag_reports
            where kind = 'session'
              and created_at > now() - make_interval(days => $1)
              and coalesce(payload->>'session', '') <> ''
        ),
        c as (
            select distinct coalesce(payload->>'session', '') as sid
            from diag_reports
            where kind = 'crash'
              and created_at > now() - make_interval(days => $1)
              and coalesce(payload->>'session', '') <> ''
        ),
        a as (
            select distinct coalesce(payload->>'session', '') as sid
            from diag_reports
            where kind = 'anr'
              and created_at > now() - make_interval(days => $1)
              and coalesce(payload->>'session', '') <> ''
        )
        select s.app_version,
               max(s.version_code) as version_code,
               count(distinct s.sid) as sessions,
               count(distinct case when c.sid is not null then s.sid end) as crashed,
               count(distinct case when a.sid is not null then s.sid end) as anr
        from s
        left join c on c.sid = s.sid
        left join a on a.sid = s.sid
        group by s.app_version
        order by max(s.version_code) desc
        """,
        days,
    )
    payload = diag_health_payload(rows)
    payload["days"] = days
    return payload


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

MARKERS_POS_TTL = 14 * 24 * 3600   # dato encontrado: se revalida cada 14 días
MARKERS_NEG_TTL = 2 * 24 * 3600    # «aquí no hay dato» real: se reintenta a los 2 días
MARKERS_RETRY_TTL = 24 * 3600      # dato positivo pero con una fuente caída: refrescar en 1 día
MARKERS_ERROR_TTL = 3600           # todas las fuentes fallaron: reintentar en 1 hora
MARKERS_EPISODE_MAX = 5000         # One Piece y series largas pasan de 400
MARKERS_MAX_BODY = 2048
_markers_bucket: dict[str, list[float]] = {}

# La misma serie puede existir como XTV (anime) y Magis (series). Primero la fila
# EXACTA del ref que reprodujo la app; después la versión XTV/anime, para que AniSkip
# no se desactive por una copia duplicada.
_MARKERS_TITLE_SQL = """
    select t.id, t.title, t.kind, t.duration_min,
           e.tmdb_id, e.imdb_id, e.match_confidence,
           (select gm.season from group_members gm where gm.title_id = t.id limit 1) as group_season
    from titles t
    left join enrichment e on e.title_id = t.id
    where t.id = $1 or t.play_ref = $1
       or t.id = any($2::text[]) or t.play_ref = any($2::text[])
    order by (t.id = $1 or t.play_ref = $1) desc,
             (coalesce(t.kind, '') = 'anime') desc,
             (t.id like 'xtv:%') desc,
             t.id
    limit 1
"""


async def _markers_season(key: str) -> int:
    """Temporada de la fila (grupo de temporadas o marca del título); 1 si no se sabe."""
    title = await db().fetchrow(_MARKERS_TITLE_SQL, key, skip_markers.ref_variants(key))
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
    length: int = Query(default=0, ge=0, le=6 * 3600, alias="len"),
    x_api_key: str | None = Header(default=None),
):
    """Marcadores de un episodio: caché local → bases globales → caché negativa.

    `ref` es la fila de temporada tal como la app la conoce (`xtv:…`); `ep` el índice
    1-based del capítulo dentro de la temporada y `len` la duración real en segundos
    (opcional; mejora el saneo y la elección de longitud de AniSkip).

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
            background.add_task(_warm_next_markers, key, int(row["season"]), ep, length)
            return skip_markers.payload(key, ep, row["season"], cached)

    season = int(row["season"]) if row is not None else 1
    found, season = await _resolve_markers(key, ep, season, row, length)
    background.add_task(_warm_next_markers, key, season, ep, length)
    return skip_markers.payload(key, ep, season, found)


async def _resolve_markers(key: str, ep: int, season: int, row=None, length_sec: int = 0) -> tuple[dict, int]:
    """Resuelve y cachea los marcadores de (ref, episodio); devuelve (segmentos, temporada)."""
    if row is None:
        row = await db().fetchrow(
            "select season, segments, fetched_at from skip_markers where ref = $1 and episode = $2",
            key, ep,
        )
    if row is not None:
        season = int(row["season"]) or season
    found = {}
    all_sources_ok = True
    title = await db().fetchrow(_MARKERS_TITLE_SQL, key, skip_markers.ref_variants(key))
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
        if duration_min <= 0 and length_sec > 0:
            duration_min = max(1, int(length_sec) // 60)
        if tmdb_id or imdb_id or mal_id:
            try:
                raw, all_sources_ok = await asyncio.to_thread(
                    skip_markers.fetch_global_status, tmdb_id, imdb_id, season, ep,
                    mal_id, length_sec if length_sec > 0 else duration_min * 60,
                )
            except Exception:  # noqa: BLE001 — fuente caída: se sirve lo que haya
                raw, all_sources_ok = {}, False
            found = skip_markers.sanitize(raw, duration_min)

    # Las marcas del usuario jamás se pierden: mandan sobre lo recién llegado.
    if row is not None:
        user = {
            k: v
            for k, v in skip_markers.segments_of(row["segments"]).items()
            if (v or {}).get("src") == "user"
        }
        found = skip_markers.overlay(found, user)

    # Si una fuente falló, el resultado no es «definitivo»: se guarda con una edad
    # artificial para que caduque pronto (1 h si no hay nada, 1 día si hay datos).
    stored_at = time.time()
    if not all_sources_ok:
        if found:
            stored_at -= max(0, MARKERS_POS_TTL - MARKERS_RETRY_TTL)
        else:
            stored_at -= max(0, MARKERS_NEG_TTL - MARKERS_ERROR_TTL)
    await db().execute(
        """
        insert into skip_markers (ref, episode, season, segments, fetched_at)
        values ($1, $2, $3, $4::jsonb, to_timestamp($5))
        on conflict (ref, episode) do update set
            season = excluded.season, segments = excluded.segments, fetched_at = excluded.fetched_at
        """,
        key, ep, season, json.dumps(found, ensure_ascii=False), stored_at,
    )
    return found, season


async def _warm_next_markers(key: str, season: int, ep: int, length_sec: int = 0) -> None:
    """Cascada: deja listos los 2 capítulos siguientes (solo si no están en caché)."""
    try:
        for nxt in (ep + 1, ep + 2):
            if nxt > MARKERS_EPISODE_MAX:
                break
            exists = await db().fetchval(
                "select 1 from skip_markers where ref = $1 and episode = $2", key, nxt
            )
            if exists is None:
                await _resolve_markers(key, nxt, season, None, length_sec)
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


# ----- espejo de capítulos/temporadas (/v1/episodes) -----
# La lista de temporadas/capítulos que la app usa para los selectores vive en el
# portal: pedirla en cada apertura de ficha cuesta 0.2–0.7 s por aparato (y usa
# la sesión del equipo). El worker la baja UNA vez y el API sirve la copia: la
# ficha pinta al instante y el portal solo se toca al reproducir. Copia vencida
# o ausente → respuesta vacía + encolar la baja (la app cae al portal como
# siempre: el espejo solo puede mejorar, jamás empeorar).


@app.get("/v1/episodes")
async def episodes_get(
    ref: str = Query(min_length=1, max_length=80),
    x_api_key: str | None = Header(default=None),
):
    require_key(x_api_key)
    key = episodes_mirror.valid_ref(ref)
    if key is None:
        raise HTTPException(status_code=400, detail="referencia inválida")
    # Guardia dura: solo refs que el catálogo conoce (la fila de temporada existe
    # en `titles`). Sin esto, cualquier cid arbitrario ponía al worker a pedir.
    known = await db().fetchval("select exists(select 1 from titles where id = $1)", key)
    if not known:
        raise HTTPException(status_code=404, detail="título fuera del catálogo")
    now_ts = time.time()
    row = await db().fetchrow(
        """select status, payload, fetched_at, requested_at, updated_at, attempts
           from episode_mirror where ref = $1""",
        key,
    )
    if episodes_mirror.needs_enqueue(row, now_ts):
        await db().execute(
            """
            insert into episode_mirror (ref, requested_at, updated_at)
            values ($1, now(), now())
            on conflict (ref) do update set
                status = 'pending', requested_at = now(), updated_at = now(),
                next_try_at = null
            """,
            key,
        )
    body = episodes_mirror.payload(key, row, now_ts)
    if body.get("status") == "ok" and body.get("episodes"):
        # Nombre real y sinopsis (TMDB) guardados por el worker: cruce por número
        # de capítulo. El `tmdb_id` sale del VOTO MAYORITARIO del grupo (una ficha
        # de temporada puede estar mal emparejada; el resto del grupo no) con la
        # ficha propia de respaldo — la MISMA regla que usa el worker.
        meta_rows = await db().fetch(
            """
            select m.episode, m.name, m.overview, m.still
            from episode_meta m
            where m.tmdb_id = coalesce((
                      select e2.tmdb_id
                      from group_members gm0
                      join group_members gm2 on gm2.group_id = gm0.group_id
                      join enrichment e2 on e2.title_id = gm2.title_id
                      where gm0.title_id = $1 and e2.tmdb_id is not null and e2.tmdb_id > 0
                      group by e2.tmdb_id
                      order by count(*) desc, avg(coalesce(e2.match_confidence, 0)) desc, e2.tmdb_id
                      limit 1
                  ), (select tmdb_id from enrichment where title_id = $1))
              and m.season = greatest(coalesce((
                  select gm.season from group_members gm
                  where gm.title_id = $1 order by gm.season limit 1), 0), 1)
            """,
            key,
        )
        body["episodes"] = episodes_mirror.enrich(body["episodes"], [dict(r) for r in meta_rows])
    return body


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
    search = await db().fetchrow(
        """
        select count(*) as titles,
               count(*) filter (where coalesce(t.search_text, '') <> '') as with_text,
               count(*) filter (where e.tmdb_id is not null) as matched,
               count(*) filter (where coalesce(array_length(e.alt_titles, 1), 0) > 0) as with_alt,
               count(*) filter (where coalesce(array_length(e.translated_titles, 1), 0) > 0) as with_translations,
               count(*) filter (where coalesce(e.director_names, '') <> '' or coalesce(e.cast_names, '') <> '') as with_credits,
               max(t.search_text_at) as last_text_at
        from titles t
        left join enrichment e on e.title_id = t.id
        """
    )
    catalog = await db().fetchrow(
        """
        select count(*) as titles,
               count(*) filter (where not exists (
                   select 1 from title_categories tc where tc.title_id = t.id
               )) as without_category
        from titles t
        """
    )
    deep_at = await db().fetchval("select updated_at from sync_meta where key = 'xtv_deep_at'")
    version = await db().fetchval("select version from sync_state where id = 1") or 1
    return {
        "indexVersion": int(version),
        "kinds": kinds,
        "markers": {
            "rows": int(markers["rows_total"] or 0),
            "withData": int(markers["with_data"] or 0),
            "lastFetch": markers["last_fetch"].isoformat() if markers["last_fetch"] else None,
        },
        "search": {
            "titles": int(search["titles"] or 0),
            "withText": int(search["with_text"] or 0),
            "matched": int(search["matched"] or 0),
            "withAlt": int(search["with_alt"] or 0),
            "withTranslations": int(search["with_translations"] or 0),
            "withCredits": int(search["with_credits"] or 0),
            "lastTextAt": search["last_text_at"].isoformat() if search["last_text_at"] else None,
        },
        "catalog": {
            "titles": int(catalog["titles"] or 0),
            "withoutCategory": int(catalog["without_category"] or 0),
            "deepAt": deep_at.isoformat() if deep_at else None,
        },
        "epg": await _epg_snapshot(),
    }



# ----- continuidad entre equipos (/v1/sync) -----
# El código compartido (8 caracteres) identifica al grupo; se guarda HASHEADO
# (SHA-256) y viaja en el header X-Sync-Code. Cada elemento es last-write-wins
# por clave: el pull es incremental por server_seq, así no depende del reloj.

SYNC_RATE_LIMIT = 120
_sync_bucket: dict[str, list[float]] = {}


def _sync_id_or_401(code: str | None) -> str:
    clean = device_sync.normalize_code(code)
    if not clean:
        raise HTTPException(status_code=401, detail="código de sincronización inválido")
    return device_sync.code_id(clean)


@app.post("/v1/sync")
async def sync_push(
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_sync_code: str | None = Header(default=None),
):
    require_key(x_api_key)
    sync_id = _sync_id_or_401(x_sync_code)
    raw = await request.body()
    if len(raw) > device_sync.MAX_BODY:
        raise HTTPException(status_code=413, detail="sincronización demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="JSON inválido")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="JSON inválido")

    ip = request.client.host if request.client else "?"
    if not _diag_rate_ok(_sync_bucket, f"{sync_id}|{ip}", time.time(), limit=SYNC_RATE_LIMIT, window=60):
        raise HTTPException(status_code=429, detail="demasiadas sincronizaciones")

    try:
        since = int(body.get("since") or 0)
    except (TypeError, ValueError):
        since = 0
    since = max(0, min(since, 2**63 - 1))
    device = device_sync.clean_text(body.get("device"), 64)

    raw_items = body.get("items") or []
    if not isinstance(raw_items, list):
        raise HTTPException(status_code=400, detail="items inválidos")
    if len(raw_items) > device_sync.MAX_ITEMS:
        raise HTTPException(status_code=413, detail="demasiados elementos")
    items = device_sync.clean_items(raw_items, device_sync.now_ms())

    pool = db()
    async with pool.acquire() as conn:
        if items:
            await conn.executemany(
                """
                insert into sync_items (sync_id, kind, item_key, payload, deleted, client_at, device_id)
                values ($1, $2, $3, $4::jsonb, $5, to_timestamp($6::double precision / 1000.0), $7)
                on conflict (sync_id, kind, item_key) do update
                set payload = case when excluded.client_at > sync_items.client_at
                                   then excluded.payload else sync_items.payload end,
                    deleted = case when excluded.client_at > sync_items.client_at
                                   then excluded.deleted else sync_items.deleted end,
                    client_at = greatest(sync_items.client_at, excluded.client_at),
                    device_id = case when excluded.client_at > sync_items.client_at
                                     then excluded.device_id else sync_items.device_id end,
                    -- El seq avanza SIEMPRE: si un item viejo pierde, su fila vuelve en el
                    -- pull y el equipo que lo intentó ve al ganador (no se queda divergente).
                    server_seq = nextval('sync_items_seq')
                """,
                [
                    (
                        sync_id,
                        item["k"],
                        item["key"],
                        json.dumps(item["data"], ensure_ascii=False) if item["data"] is not None else None,
                        item["d"],
                        item["at"],
                        device,
                    )
                    for item in items
                ],
            )
        # Tope por grupo: si un cliente empuja más de 500 elementos, se conservan los más
        # recientes (el cliente normal manda decenas; esto evita que la tabla crezca sin fin).
        await conn.execute(
            """
            delete from sync_items
            where (sync_id, kind, item_key) in (
                select sync_id, kind, item_key
                from sync_items
                where sync_id = $1
                order by client_at desc
                offset 500
            )
            """,
            sync_id,
        )
        rows = await conn.fetch(
            """
            select kind, item_key, payload, deleted, client_at, server_seq
            from sync_items
            where sync_id = $1 and server_seq > $2
            order by server_seq asc
            limit 500
            """,
            sync_id,
            since,
        )

    seq = max((int(row["server_seq"]) for row in rows), default=since)
    return {"seq": seq, "items": device_sync.response_items(rows)}


@app.post("/v1/sync/forget")
async def sync_forget(
    x_api_key: str | None = Header(default=None),
    x_sync_code: str | None = Header(default=None),
):
    require_key(x_api_key)
    sync_id = _sync_id_or_401(x_sync_code)
    await db().execute("delete from sync_items where sync_id = $1", sync_id)
    return {"ok": True}


# ----- Vision+ Play: salas de trivia (árbitro en el VPS) -----
# El estado de una sala vive en memoria (decenas de KB): si la API se reinicia,
# la sala muere y la app lo dice. Solo el resultado final se guarda en Postgres.
# La lógica pura está en `games.py`; el banco, en `sync/trivia_bank.py`.

import games as vision_games  # noqa: E402  (import local para no ensuciar el bloque de arriba)
import game_decks as vision_decks  # noqa: E402

GAME_MAX_BODY = 64 * 1024
GAME_MAX_ROOMS = 100
GAME_JOIN_RATE = 10  # por IP y minuto
GAME_ANSWER_RATE = 30  # por jugador y minuto

_GAME_ROOMS: dict[str, vision_games.Room] = {}
_GAME_JOIN_BUCKET: dict[str, list[float]] = {}
_GAME_CREATE_BUCKET: dict[str, list[float]] = {}
_GAME_ANSWER_BUCKET: dict[str, list[float]] = {}
_GAME_REPORT_BUCKET: dict[str, list[float]] = {}


def _game_int(value, default: int, low: int, high: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        number = default
    return max(low, min(high, number))


def _game_text(value, limit: int) -> str:
    return str(value or "").strip().replace("\x00", "")[:limit]


def _game_json(value):
    """jsonb de asyncpg puede llegar como str; nunca dejamos pasar basura."""
    if isinstance(value, str):
        try:
            return json.loads(value)
        except (TypeError, ValueError):
            return None
    return value


async def _game_body(request: Request) -> dict:
    raw = await request.body()
    if len(raw) > GAME_MAX_BODY:
        raise HTTPException(status_code=413, detail="cuerpo demasiado grande")
    try:
        body = json.loads(raw.decode("utf-8", "replace"))
    except Exception:  # noqa: BLE001
        raise HTTPException(status_code=400, detail="JSON inválido")
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="JSON inválido")
    return body


def _game_code(raw) -> str:
    code = vision_games.normalize_code(raw)
    if not code:
        raise HTTPException(status_code=400, detail="código de sala inválido")
    return code


def _game_group_id(raw) -> str:
    """El `sync_id` del código de continuidad rota el banco por familia."""
    clean = device_sync.normalize_code(raw)
    return device_sync.code_id(clean) if clean else ""


def _game_room(code: str) -> vision_games.Room:
    room = _GAME_ROOMS.get(code)
    if room is None or room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    return room


def _game_host(room: vision_games.Room, token: str | None) -> None:
    if not room.host_ok(token):
        raise HTTPException(status_code=403, detail="solo el anfitrión puede hacer eso")


def _game_player(room: vision_games.Room, token: str | None) -> vision_games.Player:
    player = room.player_by_token(token)
    if player is None:
        raise HTTPException(status_code=410, detail="token de jugador inválido")
    return player


def _game_snapshot(room: vision_games.Room, role: str, since: int, now: float,
                   player_id: str | None = None, *, display_authorized: bool = False):
    snap = room.snapshot(
        role, since, now, player_id,
        display_authorized=display_authorized,
    )
    if snap is None:
        return Response(status_code=204)
    return snap


async def _game_questions(pack: str, rounds: int, group_id: str) -> list[dict]:
    """Saca preguntas del banco, evitando las ya servidas a este grupo."""
    wanted = max(1, min(rounds, vision_games.MAX_ROUNDS)) + 4
    async with db().acquire() as conn:
        rows = await conn.fetch(
            """
            select q.id, q.kind, q.payload, q.answer_key, q.difficulty, q.pack
            from trivia_questions q
            where ($1 = '' or $1 = 'general' or q.pack = $1)
              and not exists (
                  select 1 from trivia_served s
                  where s.question_id = q.id and s.group_id = $2
                    and s.served_at > now() - interval '90 days'
              )
            order by random()
            limit $3
            """,
            pack, group_id, wanted,
        )
        if len(rows) < wanted:
            fill = await conn.fetch(
                """
                select q.id, q.kind, q.payload, q.answer_key, q.difficulty, q.pack
                from trivia_questions q
                where ($1 = '' or $1 = 'general' or q.pack = $1)
                order by coalesce(
                    (select max(s.served_at) from trivia_served s
                     where s.question_id = q.id and s.group_id = $2),
                    to_timestamp(0)
                ) asc, random()
                limit $3
                """,
                pack, group_id, wanted - len(rows),
            )
            rows = list(rows) + list(fill)
        selected: list[dict] = []
        seen: set[str] = set()
        for row in rows:
            qid = str(row["id"])
            if qid in seen:
                continue
            payload = _game_json(row["payload"])
            if not isinstance(payload, dict):
                continue
            seen.add(qid)
            selected.append({
                "id": qid,
                "kind": str(row["kind"] or ""),
                "payload": payload,
                "answer_key": str(row["answer_key"] or ""),
                "difficulty": int(row["difficulty"] or 1),
                "pack": str(row["pack"] or "general"),
            })
        if group_id and selected:
            await conn.executemany(
                """
                insert into trivia_served (question_id, group_id, served_at)
                values ($1, $2, now()) on conflict do nothing
                """,
                [(item["id"], group_id) for item in selected],
            )
    if not selected:
        raise HTTPException(status_code=503, detail="no hay preguntas disponibles")
    return selected


async def _game_charada_deck(rounds: int, code: str) -> list[dict]:
    """Mazo de Charadas: títulos con póster, populares y reconocibles."""
    need = max(1, rounds) * (vision_games.CHARADA_MAX_PASSES + 1) + 6
    async with db().acquire() as conn:
        rows = await conn.fetch(
            """
            select t.id, t.title, t.year, t.poster_url, t.kind, t.genres
            from titles t
            where coalesce(t.poster_url, '') <> ''
              and length(t.title) between 2 and 90
              and t.title not ilike '%(trailer)%'
              and (t.popularity > 0 or t.vote_count >= 50)
            order by random()
            limit $1
            """,
            need * 2,
        )
    deck = vision_games.build_charada_deck([dict(row) for row in rows], rounds, code)
    if not deck:
        raise HTTPException(status_code=503, detail="no hay cartas disponibles")
    return deck


async def _game_wager_rounds(rounds: int, code: str) -> list[dict]:
    """Rondas de Apuesta a la pista: títulos con sinopsis, reparto y póster."""
    want = max(1, rounds) * 3 + 24
    async with db().acquire() as conn:
        rows = await conn.fetch(
            """
            select t.id, t.title, t.year, t.kind, t.poster_url, t.synopsis,
                   t.cast_list, t.director, t.genres, t.popularity, t.vote_count
            from titles t
            where coalesce(t.poster_url, '') <> ''
              and length(t.synopsis) >= 60
              and length(t.title) between 2 and 90
              and t.title not ilike '%(trailer)%'
              and (t.popularity > 0 or t.vote_count >= 50)
            order by t.popularity desc nulls last, random()
            limit $1
            """,
            want,
        )
    deck = vision_games.build_wager_rounds([dict(row) for row in rows], rounds, code)
    if not deck:
        raise HTTPException(status_code=503, detail="no hay pistas disponibles")
    return deck


def _game_party_deck(game: str, rounds: int, code: str) -> list[dict]:
    """Mazos locales de los juegos de fiesta (sin DB, respuesta instantánea)."""
    if game == "impostor":
        return vision_decks.build_impostor_deck(rounds, code)
    if game == "dibujo":
        return vision_decks.build_drawing_deck(rounds, code)
    if game == "subasta":
        return vision_decks.build_auction_deck(rounds, code)
    if game == "fibbage":
        return vision_decks.build_fibbage_deck(rounds, code)
    return []


async def _persist_game_result(room: vision_games.Room) -> None:
    if not room.started_at or not room.players:
        return
    try:
        result = room.result()
        ended = room.ended_at or time.time()
        await db().execute(
            """
            insert into game_results (game, mode, pack, players, rounds, started_at, ended_at)
            values ($1, $2, $3, $4::jsonb, $5,
                    to_timestamp($6::double precision), to_timestamp($7::double precision))
            """,
            result.get("game", "trivia"), result["mode"], result["pack"],
            json.dumps(result["players"], ensure_ascii=False),
            result["rounds"], result["startedAt"] / 1000.0, ended,
        )
    except Exception:  # noqa: BLE001 - el resultado no puede tumbar la sala
        log.exception("Play: no se pudo guardar el resultado de la sala")


async def _games_sweep_loop() -> None:
    """Cierra salas vencidas o inactivas cada minuto y guarda su resultado."""
    while True:
        await asyncio.sleep(60)
        now = time.time()
        for code, room in list(_GAME_ROOMS.items()):
            try:
                room.tick(now)
                if room.phase == "closed":
                    await _persist_game_result(room)
                    _GAME_ROOMS.pop(code, None)
            except Exception:  # noqa: BLE001
                log.exception("Play: el barrendero falló con la sala %s", code)


@app.post("/v1/game/rooms")
async def game_create(
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_sync_code: str | None = Header(default=None),
):
    require_key(x_api_key)
    body = await _game_body(request)
    ip = request.client.host if request.client else "?"
    if not _diag_rate_ok(_GAME_CREATE_BUCKET, ip, time.time(), limit=GAME_JOIN_RATE, window=60):
        raise HTTPException(status_code=429, detail="demasiadas salas; espera un momento")
    if len(_GAME_ROOMS) >= GAME_MAX_ROOMS:
        raise HTTPException(status_code=409, detail="hay demasiadas salas abiertas")

    mode = _game_text(body.get("mode"), 12).lower() or "remote"
    if mode not in vision_games.MODES:
        raise HTTPException(status_code=400, detail="modo inválido")
    game = _game_text(body.get("game"), 16).lower() or "trivia"
    if game not in vision_games.GAMES:
        raise HTTPException(status_code=400, detail="juego inválido")
    default_rounds = vision_games.game_default_rounds(game)
    rounds = _game_int(body.get("rounds"), default_rounds, 1, vision_games.MAX_ROUNDS)
    question_seconds = _game_int(
        body.get("questionSeconds"), vision_games.QUESTION_SECONDS,
        vision_games.MIN_QUESTION_SECONDS, vision_games.MAX_QUESTION_SECONDS,
    )
    charada_seconds = _game_int(
        body.get("charadaSeconds"), vision_games.CHARADA_SECONDS, 15, 120,
    )
    wager_seconds = _game_int(body.get("wagerSeconds"), vision_games.WAGER_SECONDS, 5, 30)
    clue_seconds = _game_int(body.get("clueSeconds"), vision_games.CLUE_SECONDS, 5, 60)
    max_players = _game_int(body.get("maxPlayers"), vision_games.MAX_PLAYERS, 1, vision_games.HARD_MAX_PLAYERS)
    rules_version = _game_int(body.get("rulesVersion"), 2, 1, 2)
    fair = bool(body.get("fair"))
    family_mode = bool(body.get("family", True))
    solo_test = bool(body.get("soloTest", False))
    pack = _game_text(body.get("pack"), 32) or ("general" if game == "trivia" else game)
    host_name = vision_games.clean_name(body.get("hostName") or "Anfitrión") or "Anfitrión"
    group_id = _game_group_id(x_sync_code)

    code = ""
    for _ in range(8):
        candidate = vision_games.generate_code()
        if candidate not in _GAME_ROOMS:
            code = candidate
            break
    if not code:
        raise HTTPException(status_code=503, detail="no se pudo crear la sala")

    if game in vision_games.fiesta_engine.FIESTA_GAMES:
        questions = []
        deck = []
    elif game == "charadas":
        questions: list[dict] = []
        deck = await _game_charada_deck(rounds, code)
    elif game == "wager":
        questions = []
        deck = await _game_wager_rounds(rounds, code)
    elif game in ("impostor", "dibujo", "subasta", "fibbage"):
        questions = []
        deck = _game_party_deck(game, rounds, code)
    else:
        questions = await _game_questions(pack, rounds, group_id)
        deck = []

    host_token = vision_games.new_token()
    player_token = vision_games.new_token() if mode != "display" else ""
    now = time.time()
    try:
        room = vision_games.create_room(
            code, host_name, host_token, player_token, questions,
            game=game, deck=deck, mode=mode, pack=pack, rounds=rounds,
            question_seconds=question_seconds, charada_seconds=charada_seconds,
            wager_seconds=wager_seconds, clue_seconds=clue_seconds,
            fair=fair, max_players=max_players, rules_version=rules_version,
            family_mode=family_mode, solo_test=solo_test, now=now,
        )
    except ValueError:
        raise HTTPException(status_code=400, detail="no se pudo crear la sala")
    _GAME_ROOMS[code] = room
    role = "display" if mode == "display" else "player"
    return {
        "code": code,
        "displayCode": vision_games.format_code(code),
        "hostToken": host_token,
        "playerToken": player_token,
        "playerId": room.host_id,
        "hostId": room.host_id,
        "mode": mode,
        "game": room.game,
        "rounds": room.rounds,
        "expiresAt": int((now + vision_games.ROOM_TTL_SECONDS) * 1000),
        "snapshot": room.snapshot(
            role, 0, now, room.host_id, display_authorized=True,
        ),
    }


@app.post("/v1/game/rooms/{code}/join")
async def game_join(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    ip = request.client.host if request.client else "?"
    if not _diag_rate_ok(_GAME_JOIN_BUCKET, ip, time.time(), limit=GAME_JOIN_RATE, window=60):
        raise HTTPException(status_code=429, detail="demasiados intentos; espera un momento")
    body = await _game_body(request)
    name = vision_games.clean_name(body.get("name"))
    if not name:
        raise HTTPException(status_code=400, detail="apodo inválido")
    spectator = bool(body.get("spectator"))
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    token = vision_games.new_token()
    player = room.add_player(name, token, now, spectator=spectator)
    if player is None:
        raise HTTPException(status_code=409, detail="la sala está llena o la partida ya empezó")
    return {
        "playerToken": token,
        "playerId": player.id,
        "spectator": spectator,
        "snapshot": room.snapshot("player", 0, now, player.id),
    }


@app.post("/v1/game/rooms/{code}/rejoin")
async def game_rejoin(
    code: str,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    room.rejoin(x_game_player or "", now)
    return {
        "playerToken": x_game_player,
        "playerId": player.id,
        "snapshot": room.snapshot("player", 0, now, player.id),
    }


@app.get("/v1/game/rooms/{code}/state")
async def game_state(
    code: str,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
    x_game_host: str | None = Header(default=None),
    since: int = Query(default=0, ge=0),
    role: str = Query(default="player"),
):
    require_key(x_api_key)
    if role not in vision_games.ROLES:
        raise HTTPException(status_code=400, detail="rol inválido")
    room = _game_room(_game_code(code))
    # Compatibilidad de transición: una TV vieja (2.5.200) puede seguir leyendo
    # el estado público, pero sin el póster de Apuesta. La TV nueva manda su
    # host token y recibe la pista visual completa.
    display_authorized = bool(role == "display" and room.host_ok(x_game_host))
    now = time.time()
    player = None
    if role == "player":
        player = _game_player(room, x_game_player)
        player.connected = True
        player.last_seen = now
    room.touch(now)
    room.tick(now)
    if room.phase == "closed":
        _GAME_ROOMS.pop(room.code, None)
        raise HTTPException(status_code=404, detail="la sala se cerró")
    return _game_snapshot(
        room, role, since, now, player.id if player else None,
        display_authorized=display_authorized,
    )


@app.post("/v1/game/rooms/{code}/answer")
async def game_answer(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    now = time.time()
    if not _diag_rate_ok(_GAME_ANSWER_BUCKET, f"{room.code}|{player.id}", now,
                         limit=GAME_ANSWER_RATE, window=60):
        raise HTTPException(status_code=429, detail="demasiadas respuestas")
    body = await _game_body(request)
    round_no = _game_int(body.get("round"), 0, 0, vision_games.MAX_ROUNDS)
    option = _game_int(body.get("option"), -1, -1, 3)
    if round_no <= 0 or option < 0:
        raise HTTPException(status_code=400, detail="respuesta inválida")
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    accepted, reason = room.answer(player.id, round_no, option, now)
    return {"accepted": accepted, "reason": reason, "receivedAt": int(now * 1000)}


@app.post("/v1/game/rooms/{code}/wager")
async def game_wager(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    body = await _game_body(request)
    round_no = _game_int(body.get("round"), 0, 0, vision_games.MAX_ROUNDS)
    points = body.get("points", vision_games.WAGER_MIN)
    if round_no <= 0:
        raise HTTPException(status_code=400, detail="ronda inválida")
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    accepted, reason = room.wager(player.id, round_no, points, now)
    return {"accepted": accepted, "reason": reason, "wager": player.wager}


@app.post("/v1/game/rooms/{code}/charada")
async def game_charada(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    body = await _game_body(request)
    action = _game_text(body.get("action"), 16).lower()
    if action not in ("correct", "pass", "correcto", "pasar"):
        raise HTTPException(status_code=400, detail="acción inválida")
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    accepted, reason = room.charada_action(player.id, action, now)
    return {"accepted": accepted, "reason": reason}


@app.post("/v1/game/rooms/{code}/action")
async def game_action(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    """Acciones de los juegos de fiesta: dibujar, mentir, votar o pujar.

    La tele nunca manda acciones: solo el teléfono de un jugador (o del
    público) puede escribir, votar o pujar. El servidor valida fase y ronda.
    """
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    body = await _game_body(request)
    round_no = _game_int(body.get("round"), 0, 0, vision_games.MAX_ROUNDS)
    kind = _game_text(body.get("kind"), 24).lower()
    text = _game_text(body.get("text"), 180)
    target = _game_text(body.get("target"), 40)
    index = body.get("index", 0)
    if round_no <= 0 or not kind:
        raise HTTPException(status_code=400, detail="acción inválida")
    now = time.time()
    # Los trazos parciales son una emisión de baja frecuencia (cada ~2.5 s)
    # y no deben consumir el presupuesto de votos/pujas del jugador.
    if kind != "draw_partial" and not _diag_rate_ok(
        _GAME_ANSWER_BUCKET, f"{room.code}|{player.id}", now,
        limit=GAME_ANSWER_RATE, window=60,
    ):
        raise HTTPException(status_code=429, detail="demasiadas acciones")
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    accepted, reason = room.act(
        player.id, round_no, kind, now,
        text=text, target=target, bid=body.get("bid", 0),
        strokes=body.get("strokes"), index=index,
        power=_game_text(body.get("power"), 16).lower(),
        wire=_game_text(body.get("wire"), 12).lower(),
        choices=body.get("choices"),
        selected=body.get("selected"),
        amount=body.get("amount", 0),
        vote=bool(body.get("vote")),
    )
    if not accepted and reason == "cerca":
        raise HTTPException(
            status_code=400,
            detail="¡Demasiado cerca del título real! Disimula un poco más.",
        )
    # Idempotencia de red: un doble toque, un reintento o una acción que llega
    # justo después del cambio de fase no debe convertirse en un error visible.
    # El cliente recibe 200 con `ignored=True` y sigue su polling normal.
    is_fiesta = getattr(room, "fiesta", None) is not None
    ignored = bool(
        is_fiesta
        and not accepted
        and reason in {"duplicada", "fase", "ronda", "turno", "duelo"}
    )
    return {
        "accepted": accepted,
        "ignored": ignored,
        "reason": reason,
        "phase": room.phase,
        "receivedAt": int(now * 1000),
        "points": int(player.score),
        "coins": int(player.coins),
    }


@app.post("/v1/game/rooms/{code}/ready")
async def game_ready(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    body = await _game_body(request)
    round_no = _game_int(body.get("round"), 0, 0, vision_games.MAX_ROUNDS)
    if round_no <= 0:
        raise HTTPException(status_code=400, detail="ronda inválida")
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    ok = room.ready(player.id, round_no, now)
    return {"ok": ok, "ready": bool(ok and player.ready_round >= round_no)}


@app.post("/v1/game/rooms/{code}/host/{action}")
async def game_host_action(
    code: str,
    action: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_host: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    _game_host(room, x_game_host)
    now = time.time()
    room.tick(now)
    if action == "start":
        if not room.start(now):
            raise HTTPException(status_code=409, detail="la partida ya empezó o faltan jugadores")
    elif action == "next":
        if room.phase == "reveal" and not room.all_ready():
            raise HTTPException(status_code=409, detail="faltan jugadores por continuar")
        if not room.next_round(now):
            raise HTTPException(status_code=409, detail="solo se avanza en el reveal")
    elif action == "close":
        room.close(now, "host")
        await _persist_game_result(room)
        _GAME_ROOMS.pop(room.code, None)
        return {"ok": True, "closed": True}
    elif action == "kick":
        body = await _game_body(request)
        player_id = _game_text(body.get("playerId"), 16)
        if not player_id or not room.kick(player_id, now):
            raise HTTPException(status_code=404, detail="jugador no encontrado")
    else:
        raise HTTPException(status_code=404, detail="acción desconocida")
    return {"ok": True, "phase": room.phase, "rev": room.rev}


@app.post("/v1/game/rooms/{code}/reaction")
async def game_reaction(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = _game_player(room, x_game_player)
    body = await _game_body(request)
    kind = _game_int(body.get("kind"), -1, -1, 5)
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        raise HTTPException(status_code=404, detail="la sala se cerró")
    if not room.react(player.id, kind, now):
        raise HTTPException(status_code=429, detail="espera un momento entre reacciones")
    return {"ok": True}


@app.post("/v1/game/rooms/{code}/report")
async def game_report(
    code: str,
    request: Request,
    x_api_key: str | None = Header(default=None),
    x_game_player: str | None = Header(default=None),
    x_game_host: str | None = Header(default=None),
):
    require_key(x_api_key)
    room = _game_room(_game_code(code))
    player = room.player_by_token(x_game_player)
    if player is None and not room.host_ok(x_game_host):
        raise HTTPException(status_code=410, detail="token inválido")
    reporter_id = player.id if player is not None else "host"
    if not _diag_rate_ok(
        _GAME_REPORT_BUCKET, f"{room.code}|{reporter_id}", time.time(),
        limit=5, window=60,
    ):
        raise HTTPException(status_code=429, detail="demasiados reportes")
    body = await _game_body(request)
    question_id = _game_text(body.get("questionId"), 64)
    if not any(q["id"] == question_id for q in room.questions):
        raise HTTPException(status_code=404, detail="pregunta no encontrada")
    reason = _game_text(body.get("reason"), 120)
    before = room.reports.get(question_id, 0)
    count = room.report(question_id, reporter_id)
    duplicate = count == before
    persisted = False
    if not duplicate:
        try:
            await db().execute(
                "insert into trivia_reports (question_id, reason) values ($1, $2)",
                question_id, reason,
            )
            persisted = True
        except Exception:  # noqa: BLE001 - el reporte no puede tumbar la partida
            log.exception("Play: no se pudo guardar el reporte")
    # No borrar preguntas en caliente: un reporte no debe ser un botón de
    # destrucción de contenido. El equipo editorial revisa la bandera.
    return {
        "ok": True,
        "reports": count,
        "persisted": persisted,
        "duplicate": duplicate,
        "flagged": count >= 3,
    }


@app.get("/v1/game/catalog")
async def game_catalog(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    return {"games": vision_games.catalog()}


@app.get("/v1/game/packs")
async def game_packs(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    titles = {
        "general": "General",
        "cine": "Cine",
        "series": "Series",
        "anime": "Anime",
        "infantil": "Infantil",
        "novelas": "Novelas",
    }
    counts: dict[str, int] = {}
    total = 0
    try:
        rows = await db().fetch(
            "select coalesce(pack, 'general') as pack, count(*) as n "
            "from trivia_questions group by coalesce(pack, 'general')"
        )
        counts = {str(row["pack"]): int(row["n"] or 0) for row in rows}
        total = sum(counts.values())
    except Exception:  # noqa: BLE001 - la salud de packs no debe romper la API
        log.exception("Play: no se pudieron contar los packs")
    packs = []
    for pack_id, title in titles.items():
        questions = total if pack_id == "general" else counts.get(pack_id, 0)
        packs.append({
            "id": pack_id,
            "title": title,
            "active": questions > 0,
            "questions": questions,
        })
    return {"packs": packs}


@app.get("/v1/game/health")
async def game_health(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    rooms = len(_GAME_ROOMS)
    try:
        row = await db().fetchrow(
            "select count(*) as n, max(created_at) as last from trivia_questions"
        )
        served = await db().fetchval("select count(*) from trivia_served")
        questions = int(row["n"] or 0) if row else 0
        version = int(row["last"].timestamp()) if row and row["last"] else 0
        return {
            "ok": True,
            "rooms": rooms,
            "questions": questions,
            "served": int(served or 0),
            "version": version,
            "maxRooms": GAME_MAX_ROOMS,
        }
    except Exception:  # noqa: BLE001 - la salud no debe romper por la DB
        return {
            "ok": True,
            "rooms": rooms,
            "questions": 0,
            "served": 0,
            "version": 0,
            "db": False,
            "maxRooms": GAME_MAX_ROOMS,
        }
