"""Enriquecimiento por título con TMDB (elegido por el usuario).

Por cada título sin enriquecer: búsqueda multi-idioma (es-MX) por nombre y año,
selección del mejor candidato con confianza, y ficha con `imdb_id`, rating,
año y géneros en español. Se procesa por lotes (ENRICH_BATCH por ciclo) y a
ritmo lento para respetar los límites de la API. Sin TMDB_API_KEY no hace nada.

IMDb directo no tiene API libre: TMDB da `imdb_id` + rating, que es lo que se
indexa; los desacuerdos de match pueden corregirse luego en `enrichment`.
"""

import asyncio
import json
import logging
import os
import time
import unicodedata
import urllib.parse
import urllib.request

log = logging.getLogger("tmdb")

API = "https://api.themoviedb.org/3"
KEY = os.environ.get("TMDB_API_KEY", "").strip()
BATCH = int(os.environ.get("ENRICH_BATCH", "300"))
DELAY = float(os.environ.get("ENRICH_DELAY_SECONDS", "0.25"))
# Pausa por llamada HTTP (no solo por título): respeta el límite ~40 req/10 s.
REQUEST_GAP = float(os.environ.get("ENRICH_REQUEST_GAP_SECONDS", "0.3"))


def _norm(text: str) -> str:
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return "".join(c if c.isalnum() or c == " " else " " for c in folded).strip()


def _get(path: str, **params) -> dict:
    params["api_key"] = KEY
    url = f"{API}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": "VisionPlusIndex/1.0"})
    if REQUEST_GAP:
        time.sleep(REQUEST_GAP)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _confidence(query: str, candidate: dict, kind: str, year: int | None) -> float:
    title = candidate.get("title") or candidate.get("name") or ""
    original = candidate.get("original_title") or candidate.get("original_name") or ""
    target = _norm(query)
    names = {_norm(title), _norm(original)}
    score = 0.0
    if target in names:
        score = 1.0
    elif any(target and (target in n or n in target) for n in names if n):
        score = 0.8
    else:
        score = 0.5
    date = candidate.get("release_date") or candidate.get("first_air_date") or ""
    if year and len(date) >= 4:
        try:
            if abs(int(date[:4]) - year) <= 1:
                score += 0.1
            else:
                score -= 0.15
        except ValueError:
            pass
    return max(0.0, min(1.0, score))


def _match(kind: str, title: str, original: str | None, year: int | None) -> tuple[dict | None, bool]:
    """El mejor candidato TMDB (con sinopsis es-MX) o (None, hubo_error_de_red).

    El error se distingue del "no hay match": con error no se marca negativo para
    reintentarlo en el próximo ciclo; sin match se marca 0.0 y no se reintenta.
    """
    common = {"language": "es-MX", "include_adult": "false", "query": title}
    searches = []
    if kind == "series":
        searches.append(("/search/tv", "first_air_date_year"))
    else:
        searches.append(("/search/movie", "year"))
    if original and _norm(original) != _norm(title):
        searches.append(searches[0])
        searches[-1] = (searches[0][0], searches[0][1], original)

    candidates: list[dict] = []
    had_error = False
    for entry in searches:
        path, year_key = entry[0], entry[1]
        alt = entry[2] if len(entry) > 2 else title
        params = dict(common)
        params["query"] = alt
        if year:
            params[year_key] = year
        try:
            results = _get(path, **params).get("results") or []
        except Exception:  # noqa: BLE001 - se reintenta en el próximo ciclo
            had_error = True
            results = []
        candidates.extend(results)
        if candidates:
            break
    if not candidates:
        return None, had_error

    best = max(candidates, key=lambda c: _confidence(title, c, kind, year))
    confidence = _confidence(title, best, kind, year)
    if confidence < 0.5:
        return None, False
    tmdb_id = best.get("id")
    if not tmdb_id:
        return None, False
    detail_path = f"/tv/{tmdb_id}" if kind == "series" else f"/movie/{tmdb_id}"
    try:
        details = _get(detail_path, language="es-MX", append_to_response="external_ids")
    except Exception:  # noqa: BLE001
        return None, True
    genres = [g.get("name") for g in details.get("genres") or [] if g.get("name")]
    date = details.get("release_date") or details.get("first_air_date") or ""
    detail_year = int(date[:4]) if len(date) >= 4 else year
    imdb_id = (details.get("external_ids") or {}).get("imdb_id")
    rating = details.get("vote_average")
    return {
        "tmdb_id": int(tmdb_id),
        "imdb_id": imdb_id,
        "confidence": round(confidence, 3),
        "rating": round(float(rating), 1) if rating else None,
        "year": detail_year,
        "genres": genres,
        "overview": (details.get("overview") or "").strip(),
    }, False


async def enrich_batch(pool) -> int:
    if not KEY:
        return 0
    async with pool.acquire() as conn:
        pending = await conn.fetch(
            """
            select t.id, t.title, t.original_title, t.year, t.kind
            from titles t
            left join enrichment e on e.title_id = t.id
            where e.title_id is null and t.kind in ('movie', 'series')
            order by (t.source = 'xtv') desc, t.is_premiere desc, t.score desc nulls last, t.updated_at desc
            limit $1
            """,
            BATCH,
        )
    if not pending:
        return 0

    done = 0
    for row in pending:
        try:
            match, had_error = await asyncio.to_thread(
                _match, row["kind"], row["title"], row["original_title"], row["year"]
            )
        except Exception as exc:  # noqa: BLE001
            log.warning("TMDB: %s falló: %s", row["title"], exc)
            match, had_error = None, True
        if match:
            async with pool.acquire() as conn:
                await conn.execute(
                    """
                    insert into enrichment (title_id, imdb_id, tmdb_id, match_confidence, rating, year, genres, overview, updated_at)
                    values ($1, $2, $3, $4, $5, $6, $7, $8, now())
                    on conflict (title_id) do update set
                        imdb_id = excluded.imdb_id, tmdb_id = excluded.tmdb_id,
                        match_confidence = excluded.match_confidence, rating = excluded.rating,
                        year = excluded.year, genres = excluded.genres,
                        overview = excluded.overview, updated_at = now()
                    """,
                    row["id"], match["imdb_id"], match["tmdb_id"], match["confidence"],
                    match["rating"], match["year"], match["genres"], match["overview"],
                )
            done += 1
        elif not had_error:
            async with pool.acquire() as conn:
                await conn.execute(
                    """
                    insert into enrichment (title_id, match_confidence, updated_at)
                    values ($1, 0.0, now()) on conflict (title_id) do nothing
                    """,
                    row["id"],
                )
        # Con error de red no se marca nada: el título sigue pendiente al próximo ciclo.
        if DELAY:
            await asyncio.sleep(DELAY)
    log.info("TMDB: %s/%s títulos enriquecidos", done, len(pending))
    return done
