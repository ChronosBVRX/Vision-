"""Conector del Internet Archive: sus filas de dominio público, iguales a las que
la app ya muestra (6 colecciones/búsquedas fijas). Sin sesión ni clave.
"""

import asyncio
import json
import logging
import urllib.request

from xtv import CAT_SQL, LINK_SQL, TITLE_SQL

log = logging.getLogger("ia")

ROWS = [
    ("peliculas", "Películas", "collection:(feature_films) AND mediatype:(movies) AND format:(MPEG4)", "downloads desc"),
    ("cine-negro", "Cine negro", 'subject:("film noir") AND mediatype:(movies) AND format:(MPEG4)', "downloads desc"),
    ("terror-suspenso", "Terror y suspenso", 'subject:("horror films") AND mediatype:(movies) AND format:(MPEG4)', "downloads desc"),
    ("animacion", "Animación clásica", "collection:(classics_animation) AND mediatype:(movies) AND format:(MPEG4)", "downloads desc"),
    ("keaton", "Buster Keaton", 'title:("buster keaton") AND mediatype:(movies) AND format:(MPEG4)', "date desc"),
    ("chaplin", "Charlie Chaplin", 'title:("charlie chaplin") AND mediatype:(movies) AND format:(MPEG4)', "date desc"),
]


def _search(query: str, sort: str, rows: int = 100) -> list[dict]:
    from urllib.parse import quote_plus

    url = (
        "https://archive.org/advancedsearch.php?q=" + quote_plus(query)
        + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&sort%5B%5D=" + quote_plus(sort)
        + f"&rows={rows}&page=1&output=json"
    )
    req = urllib.request.Request(url, headers={"User-Agent": "VisionPlusIndex/1.0"})
    with urllib.request.urlopen(req, timeout=25) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    return (payload.get("response") or {}).get("docs") or []


async def sync(pool) -> int:
    total = 0
    for row_id, label, query, sort in ROWS:
        try:
            docs = await asyncio.to_thread(_search, query, sort)
        except Exception:  # noqa: BLE001
            log.exception("IA: %s falló (se continúa)", label)
            continue
        category_id = f"ia-{row_id}"
        title_rows, links = [], []
        for i, doc in enumerate(docs):
            identifier = doc.get("identifier") or ""
            if not identifier:
                continue
            raw = doc.get("title")
            if isinstance(raw, list):
                raw = raw[0] if raw else ""
            raw_title = str(raw or "").strip()
            title = raw_title.split("(")[0].strip() or identifier
            year = doc.get("year")
            try:
                year = int(year) if year else None
            except Exception:
                year = None
            title_rows.append((
                f"ia-{identifier}", "ia", identifier, "movie", title, None, year, "",
                f"https://archive.org/services/img/{identifier}", 0, None, [], "", "",
                f"ia:{identifier}|{title}", bool(year and year >= 2000), False, "movie",
            ))
            links.append((f"ia-{identifier}", category_id, i))
        if not title_rows:
            continue
        async with pool.acquire() as conn:
            await conn.execute(CAT_SQL, category_id, label, "source", 300)
            await conn.executemany(TITLE_SQL, title_rows)
            await conn.executemany(LINK_SQL, links)
        total += len(title_rows)
        log.info("IA: %s -> %s títulos", label, len(title_rows))
    return total
