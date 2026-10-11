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
import logging
import os
import random
import re
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

log = logging.getLogger("epg")

UA = "VisionPlusSync/1.0 (epg)"

FEEDS: tuple[str, ...] = (
    "https://epgshare01.online/epgshare01/epg_ripper_MX1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_DIRECTVSPORTS1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_CO1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_AR1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_CL1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_US_SPORTS1.xml.gz",
)

UPSERT_SQL = """
insert into epg_programs (
    id, channel_id, channel_name, norm_name, title, description, start_time, end_time, updated_at
) values ($1, $2, $3, $4, $5, $6, $7, $8, now())
on conflict (id) do update set
    title = excluded.title,
    description = excluded.description,
    end_time = excluded.end_time,
    updated_at = now()
"""

NOISE_WORDS = {
    "fhd", "uhd", "hd", "sd", "4k", "24", "7", "vip", "latino", "latam",
    "espana", "mexico", "mex", "colombia", "col", "chile", "cl", "argentina", "ar",
    "es", "pe", "rd", "gt", "sv", "ni", "hn", "pa", "uy", "central", "usa", "us",
    "plus", "east", "west", "pc", "tv", "de", "del", "la", "el", "los", "las", "en"
}


def canonical_channel(text: str) -> str:
    """Normaliza un nombre de canal a un token canónico compartido."""
    if not text:
        return ""
    t = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn").lower()
    t = re.sub(r"\(.*?\)|\[.*?\]", " ", t)
    t = re.sub(r"[^a-z0-9]+", " ", t)
    tokens = [tok for tok in t.split() if tok]
    if not tokens:
        return ""

    # Reglas específicas de canales y cadenas clave
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


def fetch_and_parse_feed(url: str) -> tuple[dict[str, tuple[str, str]], list[dict]]:
    """Descarga y parsea un feed XMLTV comprimido en memoria."""
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
        c1 = canonical_channel(name)
        c2 = canonical_channel(cid)
        chosen_canon = c1 or c2
        if cid and chosen_canon:
            channel_map[cid] = (name, chosen_canon)

    programs: list[dict] = []
    for p in root.findall("programme"):
        ch_id = p.get("channel", "").strip()
        meta = channel_map.get(ch_id)
        if not meta:
            continue
        ch_name, canon = meta
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
        pid = f"{canon}:{start.isoformat()}"
        programs.append({
            "id": pid,
            "channel_id": ch_id,
            "channel_name": ch_name,
            "norm_name": canon,
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

    for url in FEEDS:
        feed_name = url.split("/")[-1]
        try:
            _, programs = await loop_runner.run_in_executor(None, fetch_and_parse_feed, url)
            if not programs:
                log.info("epg %s: sin programas válidos", feed_name)
                continue

            params = [
                (
                    p["id"],
                    p["channel_id"],
                    p["channel_name"],
                    p["norm_name"],
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
            log.info("epg %s: %s programas sincronizados", feed_name, len(programs))
        except Exception:
            log.exception("epg %s: error al procesar feed", feed_name)

    # Prunear programas que terminaron hace más de 6 horas
    try:
        async with pool.acquire() as conn:
            deleted = await conn.execute("delete from epg_programs where end_time < now() - interval '6 hours'")
            log.info("epg: limpieza completada (%s)", deleted)
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
