"""Fuentes alternativas de streaming deportivo (descubiertas en Wiwy / JrFlix).

Extrae y cachea canales HLS en vivo independientes de los servidores de Magis:
- JrFlix (Cloudflare Pages): `wiwyxuper-jrflix-config.pages.dev`
- Espejos de alta disponibilidad en CDN BTV (live.btv.mx)
"""

from __future__ import annotations

import json
import logging
import re
import ssl
import time
import unicodedata
import urllib.request

log = logging.getLogger("visionplus.wiwy")

JRFLIX_URL = "https://wiwyxuper-jrflix-config.pages.dev/tv/jrflix-chile.json"
CACHE_TTL = 900.0  # 15 minutos
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE

_CACHE: dict[str, str] = {}
_CACHE_AT = 0.0

CHANNEL_TARGETS = [
    {
        "code": "wx-jrflix-espn-hd-chile",
        "display": "ESPN HD",
        "broadcasters": ["espn", "espn hd", "star+", "disney+", "star plus"],
    },
    {
        "code": "wx-jrflix-espn-2-chile",
        "display": "ESPN 2",
        "broadcasters": ["espn 2", "espn2", "star+", "disney+", "star plus"],
    },
    {
        "code": "wx-jrflix-espn-premium-hd-chile",
        "display": "ESPN Premium",
        "broadcasters": ["espn premium", "espn extra", "star+", "disney+"],
    },
    {
        "code": "wx-jrflix-directv-sports-2-hd-chile",
        "display": "DSports 2",
        "broadcasters": ["dsports", "dsports 2", "directv", "directv sports", "directv sports 2"],
    },
    {
        "code": "wx-jrflix-sky-sport-la-liga",
        "display": "Sky Sports LaLiga",
        "broadcasters": ["sky", "sky sports", "sky sports laliga", "laliga tv", "laligatv", "dazn"],
    },
    {
        "code": "wx-jrflix-tnt-sport-hd-arg",
        "display": "TNT Sports",
        "broadcasters": ["tnt", "tnt sports", "tnt sports hd"],
    },
    {
        "code": "wx-jrflix-fox-centroamerica-cr",
        "display": "FOX Sports",
        "broadcasters": ["fox", "fox sports", "fox sports centroamerica"],
    },
    {
        "code": "wx-jrflix-space-hd",
        "display": "Space HD",
        "broadcasters": ["space", "space hd"],
    },
    {
        "code": "wx-jrflix-universal-premier",
        "display": "Universal Premier",
        "broadcasters": ["universal", "universal premier"],
    },
]


def _fold(text: str) -> str:
    text = (text or "").lower()
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def get_jrflix_channels(force: bool = False) -> dict[str, str]:
    """Obtiene y cachea el mapa de canales de JrFlix {código: m3u8_url}."""
    global _CACHE, _CACHE_AT
    now = time.time()
    if not force and _CACHE and (now - _CACHE_AT < CACHE_TTL):
        return _CACHE

    req = urllib.request.Request(JRFLIX_URL, headers={"User-Agent": UA, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10, context=_SSL_CTX) as resp:
            data = json.loads(resp.read().decode("utf-8", "replace"))
        channels = data.get("channels") or {}
        if isinstance(channels, dict) and channels:
            _CACHE = {k: str(v).strip() for k, v in channels.items() if str(v).strip()}
            _CACHE_AT = now
            log.info("wiwy/jrflix: %d canales en caché", len(_CACHE))
            return _CACHE
    except Exception as exc:  # noqa: BLE001
        log.warning("wiwy/jrflix no disponible: %s", exc)

    return _CACHE


def match_alt_signals(broadcast_names: list[str]) -> list[dict]:
    """Cruza nombres de emisora ('ESPN', 'Sky Sports LaLiga', etc.) con streams activos."""
    if not broadcast_names:
        return []

    channels = get_jrflix_channels()
    if not channels:
        return []

    folded_incoming = {_fold(b) for b in broadcast_names if b}
    results = []
    seen_urls = set()

    for target in CHANNEL_TARGETS:
        code = target["code"]
        url = channels.get(code)
        if not url or url in seen_urls:
            continue

        broadcasters = target["broadcasters"]
        matched = False
        for b in broadcasters:
            b_folded = _fold(b)
            # Match exacto o coincidencia de palabra clave
            if any(b_folded == inc or (len(b_folded) >= 4 and b_folded in inc) for inc in folded_incoming):
                matched = True
                break

        if matched:
            seen_urls.add(url)
            results.append({
                "name": f"{target['display']} (Señal Alternativa HD)",
                "url": url,
                "link": url,
                "type": "hls",
                "stream_type": "hls",
                "badge": "Alternativa HD",
                "origin": "jrflix",
                "alive": True,
                "headers": {"User-Agent": UA},
            })

    return results


def get_alt_sports_group() -> dict | None:
    """Devuelve un grupo 24/7 con todos los canales alternativos activos de JrFlix."""
    channels = get_jrflix_channels()
    if not channels:
        return None

    ch_list = []
    for target in CHANNEL_TARGETS:
        url = channels.get(target["code"])
        if not url:
            continue
        ch_list.append({
            "name": target["display"],
            "logo": "",
            "links": [
                {
                    "name": f"{target['display']} (Directo HD)",
                    "url": url,
                    "link": url,
                    "stream_type": "hls",
                    "type": "hls",
                    "alive": True,
                    "badge": "BTV HD",
                    "headers": {"User-Agent": UA},
                }
            ],
        })

    if not ch_list:
        return None

    return {
        "id": "group:alt_wiwy",
        "name": "Señales Alternativas HD",
        "logo": "",
        "alive": len(ch_list),
        "total": len(ch_list),
        "channels": ch_list,
    }
