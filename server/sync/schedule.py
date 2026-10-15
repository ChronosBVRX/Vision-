"""Agenda de estrenos (TVMaze, gratis y sin llave): qué capítulos salen al aire por día.

Baja `schedule?country=MX&date=…` para hoy + 6 días (7 llamadas chicas por ciclo) y
guarda (show, temporada, capítulo, hora, imdb). La app casa por `imdb` con las series
que sigue el usuario y avisa («Nuevo capítulo de X hoy»). Poda lo de hace 2+ días.
"""

import asyncio
import datetime
import json
import logging
import urllib.parse
import urllib.request

log = logging.getLogger("schedule")

API = "https://api.tvmaze.com/schedule"
COUNTRY = "MX"
DAYS_AHEAD = 7


def _get_day(day: datetime.date) -> list:
    params = urllib.parse.urlencode({"country": COUNTRY, "date": day.isoformat()})
    req = urllib.request.Request(f"{API}?{params}", headers={"User-Agent": "VisionPlusIndex/1.0"})
    with urllib.request.urlopen(req, timeout=25) as resp:
        data = json.loads(resp.read().decode("utf-8", "replace"))
    return data if isinstance(data, list) else []


def parse_airings(entries, airdate: datetime.date) -> list:
    """Entradas de TVMaze → filas puras (tvmaze_id, show, temporada, capítulo, hora, imdb)."""
    out = []
    if not isinstance(entries, list):
        return out
    for item in entries:
        if not isinstance(item, dict):
            continue
        try:
            ep_id = int((item.get("id") or 0))
            season = int(item.get("season") or 0)
            episode = int(item.get("number") or 0)
        except (TypeError, ValueError):
            continue
        if ep_id <= 0 or season <= 0 or episode <= 0:
            continue
        show = item.get("show") or {}
        externals = show.get("externals") if isinstance(show, dict) else None
        imdb = ""
        if isinstance(externals, dict):
            imdb = str(externals.get("imdb") or "").strip()
        out.append({
            "tvmaze_id": ep_id,
            "airdate": airdate.isoformat(),
            "show_name": str(show.get("name") or "").strip()[:160] if isinstance(show, dict) else "",
            "season": season,
            "episode": episode,
            "ep_name": str(item.get("name") or "").strip()[:160],
            "airtime": str(item.get("airstamp") or "").strip(),
            "imdb": imdb,
        })
    return out


async def sync(pool) -> int:
    """Baja la agenda de 7 días y poda lo viejo. Devuelve filas guardadas."""
    today = datetime.date.today()
    days = [today + datetime.timedelta(days=i) for i in range(DAYS_AHEAD)]
    try:
        batches = await asyncio.gather(*[asyncio.to_thread(_get_day, d) for d in days])
    except Exception as exc:  # noqa: BLE001 - sin red no se tumba el ciclo
        log.warning("agenda TVMaze falló: %s", exc)
        return 0
    rows = []
    for day, entries in zip(days, batches):
        rows += parse_airings(entries, day)
    if not rows:
        return 0
    async with pool.acquire() as conn:
        await conn.executemany(
            """
            insert into schedule_airings
                (tvmaze_id, airdate, show_name, season, episode, ep_name, airtime, imdb, updated_at)
            values ($1, $2::text::date, $3, $4, $5, $6, $7::text::timestamptz, $8, now())
            on conflict (tvmaze_id, airdate) do update set
                show_name = excluded.show_name, season = excluded.season,
                episode = excluded.episode, ep_name = excluded.ep_name,
                airtime = excluded.airtime, imdb = excluded.imdb, updated_at = now()
            """,
            [(r["tvmaze_id"], r["airdate"], r["show_name"], r["season"], r["episode"],
              r["ep_name"], r["airtime"] or None, r["imdb"]) for r in rows],
        )
        # Casar con el catálogo por imdb (ficha con confianza): la app sigue por title_id.
        await conn.execute(
            """
            update schedule_airings s set title_id = e.title_id
            from enrichment e
            where s.imdb <> '' and e.imdb_id = s.imdb
              and coalesce(e.match_confidence, 0) >= 0.5
              and s.airdate >= current_date - interval '2 days'
              and (s.title_id is null or s.title_id = '')
            """
        )
        await conn.execute(
            "delete from schedule_airings where airdate < current_date - interval '2 days'"
        )
    log.info("agenda TVMaze: %s emisiones (7 días)", len(rows))
    return len(rows)
