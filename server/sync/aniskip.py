"""Mapeo TMDB → MyAnimeList para AniSkip (openings/endings de anime).

El dataset Fribb anime-lists trae por entrada `mal_id` + `themoviedb_id.tv` +
`season.tmdb`: se descarga (mini, ~6 MB), se parsea con `markers.parse_anime_list`
y se vuelca a `anime_mal_map`. Refresco semanal: el dataset cambia poco y la tabla
solo se reescribe cuando hay algo nuevo que contar.
"""

import asyncio
import json
import logging
import os
import urllib.request

log = logging.getLogger("aniskip")

ANIME_LIST_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
REFRESH_DAYS = int(os.environ.get("ANISKIP_REFRESH_DAYS", "7"))
TIMEOUT = 60.0


def parse_entries(entries) -> dict:
    """Dataset Fribb anime-lists → {(tmdb_id, tmdb_season): mal_id} (puro, testeable).

    Las series largas (One Piece, Naruto…) vienen SIN `season.tmdb`; antes se
    descartaban y AniSkip nunca se usaba. Esas entradas caen a temporada 1, pero un
    mapeo explícito siempre gana.
    """
    explicit = {}
    fallback = {}
    if not isinstance(entries, list):
        return explicit
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        try:
            mal_id = int(entry.get("mal_id") or 0)
        except (TypeError, ValueError):
            continue
        if mal_id <= 0:
            continue
        tmdb_block = entry.get("themoviedb_id") or {}
        season_block = entry.get("season") or {}
        try:
            tmdb_id = int(tmdb_block.get("tv") or 0)
            season = int(season_block.get("tmdb") or 0)
        except (TypeError, ValueError, AttributeError):
            continue
        if tmdb_id <= 0:
            continue
        if season <= 0:
            fallback.setdefault((tmdb_id, 1), mal_id)
        else:
            explicit.setdefault((tmdb_id, season), mal_id)
    for key, mal_id in fallback.items():
        explicit.setdefault(key, mal_id)
    return explicit


def _download():
    req = urllib.request.Request(ANIME_LIST_URL, headers={"User-Agent": "VisionPlusIndex/1.0"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read().decode("utf-8", "replace"))


async def sync(pool) -> int:
    """Refresca `anime_mal_map` si está vacío o lleva más de REFRESH_DAYS. Devuelve filas."""
    async with pool.acquire() as conn:
        stale = await conn.fetchval(
            """
            select count(*) = 0 or coalesce(max(updated_at), to_timestamp(0))
                   < now() - make_interval(days => $1)
            from anime_mal_map
            """,
            REFRESH_DAYS,
        )
        if not stale:
            return 0
    try:
        entries = await asyncio.to_thread(_download)
    except Exception as exc:  # noqa: BLE001 - sin red no se tumba el ciclo
        log.warning("AniSkip: no se pudo bajar anime-lists: %s", exc)
        return 0
    mapping = parse_entries(entries)
    if not mapping:
        log.warning("AniSkip: dataset vacío o ilegible")
        return 0
    rows = [(tmdb, season, mal) for (tmdb, season), mal in mapping.items()]
    async with pool.acquire() as conn:
        await conn.executemany(
            """
            insert into anime_mal_map (tmdb_id, season, mal_id, updated_at)
            values ($1, $2, $3, now())
            on conflict (tmdb_id, season) do update set
                mal_id = excluded.mal_id, updated_at = now()
            """,
            rows,
        )
    log.info("AniSkip: %s mapeos TMDB→MAL", len(rows))
    return len(rows)
