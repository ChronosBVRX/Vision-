"""Guía electrónica de programación (EPG) para TV en vivo.

Baja feeds XMLTV de alta calidad y bajo peso (México, deportes LatAm, Colombia,
Argentina, Chile, etc.) de epgshare01 y sincroniza `epg_programs`.
Permite que el reproductor Vision+ (y la agenda deportiva) muestren el programa
en emisión ("AHORA") y el siguiente ("DESPUÉS") con barra de progreso real y
descripción completa en el modal de información del canal.

Ciclo propio en el worker (cada EPG_INTERVAL_SECONDS, 4 horas por defecto).
"""

import asyncio
import gzip
import json
import logging
import os
import random
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

from epg_channels import (
    canonical_channel,
    channel_country,
    epg_norms_for,
)

log = logging.getLogger("epg")

UA = "VisionPlusSync/1.0 (epg)"

# Cada feed pertenece a un país: la guía se guarda como `<país>:<cadena>` para no
# mezclar señales hermanas de países distintos (HBO México ≠ HBO Chile; Canal 5
# de Honduras ≠ Canal 5 de México). Los feeds de deportes de EE. UU. traen los
# canales de franquicia (NFL/NBA/MLB), que no existen en el catálogo: no estorban,
# pero tampoco cuentan como cobertura.
FEEDS: tuple[str, ...] = (
    "https://epgshare01.online/epgshare01/epg_ripper_MX1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_DIRECTVSPORTS1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_CO1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_AR1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_CL1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_US_SPORTS1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_PE1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_EC1.xml.gz",
)

FEED_COUNTRIES: dict[str, str] = {
    "epg_ripper_MX1.xml.gz": "mx",
    "epg_ripper_DIRECTVSPORTS1.xml.gz": "ar",
    "epg_ripper_CO1.xml.gz": "co",
    "epg_ripper_AR1.xml.gz": "ar",
    "epg_ripper_CL1.xml.gz": "cl",
    "epg_ripper_US_SPORTS1.xml.gz": "us",
    "epg_ripper_PE1.xml.gz": "pe",
    "epg_ripper_EC1.xml.gz": "ec",
}


def feed_country(url: str) -> str:
    """País del feed por su nombre de archivo (``mx``, ``co``…)."""
    return FEED_COUNTRIES.get(url.split("/")[-1], "")


UPSERT_SQL = """
insert into epg_programs (
    id, channel_id, channel_name, norm_name, country, title, description,
    start_time, end_time, updated_at
) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
on conflict (id) do update set
    country = excluded.country,
    title = excluded.title,
    description = excluded.description,
    end_time = excluded.end_time,
    updated_at = now()
"""


def parse_xmltv_date(raw: str) -> datetime | None:
    if not raw:
        return None
    raw = raw.strip()
    try:
        return datetime.strptime(raw, "%Y%m%d%H%M%S %z")
    except Exception:
        try:
            return datetime.strptime(raw[:14], "%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
        except Exception:
            return None


def fetch_and_parse_feed(url: str, country: str | None = None) -> tuple[dict[str, tuple[str, str]], list[dict]]:
    """Descarga y parsea un feed XMLTV comprimido en memoria.

    ``country`` es el país del feed (``mx``, ``cl``…): la clave de la guía queda
    como ``<país>:<cadena>``. Si no se indica, se deduce del nombre del archivo.
    """
    country = country if country is not None else feed_country(url)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        with gzip.GzipFile(fileobj=resp) as gz:
            tree = ET.parse(gz)
            root = tree.getroot()

    channel_map: dict[str, tuple[str, str]] = {}
    for ch in root.findall("channel"):
        cid = ch.get("id", "").strip()
        dn = ch.find("display-name")
        name = dn.text.strip() if dn is not None and dn.text else cid
        chosen_canon = canonical_channel(name) or canonical_channel(cid)
        if not chosen_canon:
            continue
        # Si el nombre no trae país, el del feed es la verdad (es su parrilla).
        norm = f"{country}:{chosen_canon}" if country else chosen_canon
        if cid:
            channel_map[cid] = (name, norm)

    programs: list[dict] = []
    for p in root.findall("programme"):
        ch_id = p.get("channel", "").strip()
        meta = channel_map.get(ch_id)
        if not meta:
            continue
        ch_name, norm = meta
        start = parse_xmltv_date(p.get("start"))
        stop = parse_xmltv_date(p.get("stop"))
        if not start or not stop or stop <= start:
            continue
        title_el = p.find("title")
        title = title_el.text.strip() if title_el is not None and title_el.text else ""
        if not title:
            continue
        desc_el = p.find("desc")
        desc = desc_el.text.strip() if desc_el is not None and desc_el.text else ""
        # El país va en el id: dos feeds con la misma cadena y hora ya no se pisan.
        pid = f"{norm}:{start.isoformat()}"
        programs.append({
            "id": pid,
            "channel_id": ch_id,
            "channel_name": ch_name,
            "norm_name": norm,
            "country": country,
            "title": title,
            "description": desc,
            "start_time": start,
            "end_time": stop,
        })

    return channel_map, programs


async def sync_epg(pool) -> int:
    """Sincroniza todos los feeds de FEEDS en la base de datos."""
    total_inserted = 0
    loop_runner = asyncio.get_running_loop()
    feeds_stats: dict[str, dict] = {}

    for url in FEEDS:
        feed_name = url.split("/")[-1]
        country = feed_country(url)
        try:
            _, programs = await loop_runner.run_in_executor(None, fetch_and_parse_feed, url)
            if not programs:
                log.info("epg %s: sin programas válidos", feed_name)
                feeds_stats[feed_name] = {"country": country, "programs": 0, "channels": 0}
                continue

            params = [
                (
                    p["id"],
                    p["channel_id"],
                    p["channel_name"],
                    p["norm_name"],
                    p["country"],
                    p["title"],
                    p["description"],
                    p["start_time"],
                    p["end_time"],
                )
                for p in programs
            ]

            # Upsert en lotes de 2000
            batch_size = 2000
            async with pool.acquire() as conn:
                for i in range(0, len(params), batch_size):
                    chunk = params[i : i + batch_size]
                    await conn.executemany(UPSERT_SQL, chunk)

            total_inserted += len(programs)
            channels = len({p["norm_name"] for p in programs})
            feeds_stats[feed_name] = {
                "country": country,
                "programs": len(programs),
                "channels": channels,
            }
            log.info(
                "epg %s [%s]: %s programas, %s canales",
                feed_name, country or "?", len(programs), channels,
            )
        except Exception:
            log.exception("epg %s: error al procesar feed", feed_name)
            feeds_stats[feed_name] = {"country": country, "programs": 0, "channels": 0, "error": True}

    # Prunear programas que terminaron hace más de 6 horas. Además se limpian las
    # filas del formato viejo (sin país): su clave no es la que consulta la app y
    # podían mezclar señales de varios países en la misma cadena.
    try:
        async with pool.acquire() as conn:
            deleted = await conn.execute("delete from epg_programs where end_time < now() - interval '6 hours'")
            log.info("epg: limpieza completada (%s)", deleted)
            if total_inserted > 0:
                legacy = await conn.execute("delete from epg_programs where country = ''")
                log.info("epg: filas del formato viejo retiradas (%s)", legacy)
            health = await conn.fetchrow(
                """
                select count(*) filter (where end_time > now()) as future,
                       count(distinct norm_name) as norms,
                       count(distinct country) as countries
                from epg_programs
                """
            )
            stats = {
                "at": datetime.now(timezone.utc).isoformat(),
                "programs": total_inserted,
                "future": int(health["future"] or 0),
                "norms": int(health["norms"] or 0),
                "countries": int(health["countries"] or 0),
                "feeds": feeds_stats,
            }
            await conn.execute(
                """
                insert into sync_meta (key, value, updated_at) values ('epg_last_cycle', $1, now())
                on conflict (key) do update set value = excluded.value, updated_at = excluded.updated_at
                """,
                json.dumps(stats, ensure_ascii=False),
            )
    except Exception:
        log.exception("epg: error al limpiar programas caducados")

    return total_inserted



async def loop(pool) -> None:
    """Loop de sincronización continua de EPG en el worker."""
    if os.environ.get("EPG_SYNC", "1") != "1":
        log.info("epg: deshabilitado por entorno")
        return
    interval = int(os.environ.get("EPG_INTERVAL_SECONDS", "14400") or "14400")
    log.info("epg: ciclo propio cada %s s", interval)

    # Espera breve inicial para que el arranque de Postgres y las tablas concluyan
    await asyncio.sleep(5)

    while True:
        try:
            count = await sync_epg(pool)
            log.info("epg: ciclo finalizado (%s programas procesados)", count)
        except Exception:
            log.exception("epg: el ciclo falló")
        await asyncio.sleep(interval * (1 + random.uniform(-0.05, 0.05)))
