"""Mapa propio de canales deportivos XTV y resolución de la agenda ESPN.

No toca GeeSports: es una segunda capa que traduce los nombres de emisora que
publica ESPN ("TUDN", "Fox Sports", "ESPN 2", "Prime Video"…) a los canales que
la persona SÍ tiene en su parrilla XTV, con su país y su dueño.

El mapa (`sports_channels.json`) se genera con `tools/build_sports_map.py` desde
la lista `tv-en-vivo` del VPS. Aquí solo se consulta y se cruza.
"""
from __future__ import annotations

import json
import os
import re
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
import unicodedata
from pathlib import Path

_DATA = json.loads(
    Path(__file__).with_name("sports_channels.json").read_text(encoding="utf-8")
)
CHANNELS: list[dict] = _DATA.get("channels") or []
BROADCASTERS: dict = _DATA.get("broadcasters") or {}
FOX_LINEAR: dict = _DATA.get("foxLinear") or {}

# Señales hermanas de la misma marca cuando el nombre de ESPN no coincide 1:1.
FAMILIES: dict[str, list[str]] = {
    "TUDN": ["TUDN", "TUDN USA"],
    "TUDN USA": ["TUDN USA", "TUDN"],
    "Win Sports": ["Win Sports", "Win Sports+"],
    "Win Sports+": ["Win Sports+", "Win Sports"],
    "ViX": ["ViX Deportes"],
    "ESPN Deportes": ["ESPN"],
    "ESPN+": ["ESPN"],
    "Disney+": ["ESPN"],
}

COUNTRY_ORDER = [
    "México", "LatAm", "EE. UU.", "Argentina", "Colombia", "Chile", "Perú",
    "Ecuador", "Costa Rica", "R. Dominicana", "Canadá", "Bolivia", "Paraguay",
    "Uruguay", "Venezuela", "Guatemala", "Honduras", "El Salvador", "Nicaragua",
    "Panamá", "Puerto Rico", "España", "Brasil", "Reino Unido", "Internacional",
]


def fold(text: str) -> str:
    text = (text or "").lower()
    # Letras que NFD no separa (danés/noruego/alemán): «Bodø» debe ser «bodo».
    for source, target in (("ø", "o"), ("æ", "ae"), ("ß", "ss"), ("ł", "l"), ("đ", "d"), ("ð", "d"), ("þ", "th")):
        text = text.replace(source, target)
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def broadcast_norm(name: str) -> str:
    """`Paramount+` y `Paramount` son la misma emisora; `ESPN 2` no es `ESPN`."""
    return fold((name or "").replace("+", " plus "))


def _entry_candidates() -> list[tuple[str, str, str, str, str]]:
    out = []
    for key, value in BROADCASTERS.items():
        if not isinstance(value, (list, tuple)) or len(value) < 5:
            continue
        canonical, owner, kind, country, aliases = value[:5]
        for candidate in [key, *(aliases or [])]:
            out.append(
                (broadcast_norm(candidate), canonical, owner, kind, country)
            )
    return out


_CANDIDATES = _entry_candidates()


def resolve_broadcast(name: str):
    """Devuelve (canonical, owner, kind, country) o None.

    Primero la coincidencia exacta; después el alias más largo (para que
    «ESPN 2» gane a «ESPN» y «Fox Sports Premium» a «Fox Sports»).
    """
    n = broadcast_norm(name)
    if not n:
        return None
    for candidate, canonical, owner, kind, country in _CANDIDATES:
        if n == candidate:
            return canonical, owner, kind, country
    best = None
    quality = {"hd", "fhd", "uhd", "sd", "4k", "h265", "hevc", "h264", "mx", "mexico", "mex"}
    for candidate, canonical, owner, kind, country in _CANDIDATES:
        if not candidate:
            continue
        if n.startswith(candidate + " ") or n.endswith(" " + candidate):
            # «FOX» es la señal básica; no debe tragarse «FOX CRICKET», «FOX NEWS»,
            # «FOX SOCCER» ni «FOX SPORTS». Solo acepta el nombre exacto o con
            # etiqueta de calidad («FOX HD», «FOX FHD»).
            if candidate == "fox":
                rest = n[len("fox"):].strip() if n.startswith("fox ") else n[:-len("fox")].strip()
                if rest and any(token not in quality for token in rest.split()):
                    continue
            if best is None or len(candidate) > best[0]:
                best = (len(candidate), canonical, owner, kind, country)
    if best:
        return best[1], best[2], best[3], best[4]
    return None


def _country_rank(channel: dict, preferred: str) -> int:
    country = channel.get("country") or ""
    if country == preferred:
        return 0
    try:
        return 1 + COUNTRY_ORDER.index(country)
    except ValueError:
        return len(COUNTRY_ORDER) + 1


def _compact(channel: dict) -> dict:
    out = {
        "name": channel.get("name") or "",
        "code": channel.get("code") or "",
        "country": channel.get("country") or "",
        "brand": channel.get("brand") or "",
        "feed": channel.get("feed") or "",
        "owner": channel.get("owner") or "",
        "general": bool(channel.get("general")),
    }
    # El portal camufla ESPN/Star+ como «Elite Sports Hub»: displayName/aliases
    # permiten que el mapa y la app muestren la identidad real sin ocultar el
    # nombre con el que lo publica XTV.
    for key in (
        "displayName", "aliases", "camouflaged", "note", "epgNorm",
        "verified", "verifiedBy", "rights", "notEventFeed", "notFoxPlus",
        "legacy", "mirrorOf",
    ):
        if key in channel:
            out[key] = channel[key]
    return out


def channels_for(
    broadcast: str,
    preferred: str = "México",
    include_general: bool = True,
) -> tuple[list[dict], tuple | None]:
    """Canales XTV que corresponden a un nombre de emisora de ESPN."""
    resolved = resolve_broadcast(broadcast)
    if not resolved:
        return [], None
    canonical, owner, kind, country = resolved
    # FOX basic / FOX+ / FOX One: el portal camufla los espejos. La prueba de
    # campo 2026-11-01 confirmó FOX basic → AYM (Cuadro Titular) y FOX+ → FOX ONE
    # (Xolos vs Atlas). FOX | Tubi no tiene espejo confirmado.
    fox = FOX_LINEAR.get(canonical)
    if fox is not None:
        mirror = str(fox.get("mirror") or "").strip()
        if mirror:
            channel = next((ch for ch in CHANNELS if ch.get("name") == mirror), None)
            if channel is not None:
                copy = dict(channel)
                copy["mirrorOf"] = canonical
                copy["note"] = fox.get("note") or copy.get("note") or ""
                return [copy], resolved
        return [], resolved
    brands = FAMILIES.get(canonical, [canonical] if kind != "ott" else [])
    wanted = {fold(b) for b in brands}
    out: list[dict] = []
    if wanted:
        for channel in CHANNELS:
            if channel.get("general") and not include_general:
                continue
            if fold(channel.get("brand") or "") in wanted:
                out.append(channel)
    # Fox Sports 2 / ESPN 3…: la marca base puede guardar el feed aparte.
    if not out and kind != "ott":
        match = re.match(r"^(.*?)\s+([1-9])$", canonical)
        if match:
            base, feed = fold(match.group(1)), fold(match.group(2))
            for channel in CHANNELS:
                if channel.get("general") and not include_general:
                    continue
                if (
                    fold(channel.get("brand") or "") == base
                    and fold(str(channel.get("feed") or "")) == feed
                ):
                    out.append(channel)
    # Dedupe por código y orden: país preferido → LatAm → EE. UU. → resto.
    seen: set[str] = set()
    unique = []
    for channel in out:
        key = channel.get("code") or channel.get("name") or ""
        if key in seen:
            continue
        seen.add(key)
        unique.append(channel)
    exact = fold(broadcast)
    unique.sort(
        key=lambda ch: (
            0 if fold(ch.get("name") or "") == exact else 1,
            _country_rank(ch, preferred),
            0 if ch.get("general") else 1,
            fold(ch.get("brand") or ""),
            ch.get("name") or "",
        )
    )
    return unique, resolved


def options_for_event(match: dict, preferred: str = "México") -> dict:
    """Opciones XTV de UN partido de la agenda ESPN (sin tocar GeeSports)."""
    names: list[str] = []
    for key in ("channels", "broadcasts"):
        for raw in match.get(key) or []:
            name = str(raw or "").strip()
            if name and name not in names:
                names.append(name)

    entries = []
    available: dict[str, dict] = {}
    for name in names:
        xtv, resolved = channels_for(name, preferred=preferred)
        canonical = resolved[0] if resolved else name
        kind = resolved[2] if resolved else "unknown"
        country = resolved[3] if resolved else ""
        entry = {
            "name": name,
            "canonical": canonical,
            "kind": kind,
            "country": country,
            "xtv": [_compact(ch) for ch in xtv],
        }
        fox_info = FOX_LINEAR.get(canonical)
        if fox_info is not None and not xtv:
            entry["note"] = fox_info.get("note") or "Sin espejo XTV confirmado"
        elif kind == "ott" and xtv:
            entry["note"] = "OTT; en XTV se puede ver la señal hermana"
        elif kind == "ott":
            entry["note"] = "OTT; no hay canal XTV para esta emisora"
        elif not xtv:
            entry["note"] = "Sin canal XTV en la parrilla"
        entries.append(entry)
        for channel in xtv:
            available[channel.get("code") or channel.get("name") or ""] = channel

    ordered = sorted(
        available.values(),
        key=lambda ch: (
            _country_rank(ch, preferred),
            0 if ch.get("general") else 1,
            ch.get("name") or "",
        ),
    )
    alternatives = [ch for ch in ordered if ch.get("country") != preferred]
    league = match.get("league") or {}
    home = match.get("home") or {}
    away = match.get("away") or {}
    return {
        "id": match.get("id") or "",
        "league": league,
        "home": home,
        "away": away,
        "kickoff": match.get("kickoff") or "",
        "status": match.get("status") or "",
        "detail": match.get("detail") or "",
        "broadcasts": entries,
        "available": [_compact(ch) for ch in ordered],
        "alternatives": [_compact(ch) for ch in alternatives],
        "noXtv": not ordered,
    }


def map_payload(
    country: str | None = None,
    brand: str | None = None,
    q: str | None = None,
    include_general: bool = True,
    limit: int | None = None,
) -> dict:
    """Mapa completo (o filtrado) de la parrilla, listo para `/v1/sports/channels`."""
    channels = list(CHANNELS)
    if not include_general:
        channels = [ch for ch in channels if not ch.get("general")]
    if country:
        needle = fold(country)
        channels = [
            ch for ch in channels
            if fold(ch.get("country") or "") == needle
        ]
    if brand:
        needle = fold(brand)
        channels = [
            ch for ch in channels
            if needle in fold(ch.get("brand") or "")
        ]
    if q:
        needle = fold(q)
        channels = [
            ch for ch in channels
            if needle in fold(ch.get("name") or "")
            or needle in fold(ch.get("brand") or "")
            or needle in fold(ch.get("country") or "")
        ]
    channels.sort(
        key=lambda ch: (
            _country_rank(ch, "México"),
            0 if ch.get("general") else 1,
            fold(ch.get("brand") or ""),
            ch.get("name") or "",
        )
    )
    total = len(channels)
    if limit and limit > 0:
        channels = channels[:limit]
    by_country: dict[str, int] = {}
    by_brand: dict[str, int] = {}
    for ch in channels:
        by_country[ch.get("country") or ""] = by_country.get(ch.get("country") or "", 0) + 1
        by_brand[ch.get("brand") or ""] = by_brand.get(ch.get("brand") or "", 0) + 1
    return {
        "generatedAt": _DATA.get("generatedAt") or "",
        "totalXtvChannels": _DATA.get("totalXtvChannels") or 0,
        "sportsChannels": _DATA.get("sportsChannels") or len(CHANNELS),
        "total": total,
        "sportsOnly": sum(1 for ch in channels if not ch.get("general")),
        "generalOnly": sum(1 for ch in channels if ch.get("general")),
        "byCountry": sorted(by_country.items(), key=lambda kv: -kv[1]),
        "byBrand": sorted(by_brand.items(), key=lambda kv: -kv[1]),
        "foxLinear": FOX_LINEAR,
        "channels": [_compact(ch) | {"position": ch.get("position")} for ch in channels],
    }


# ---------------------------------------------------------------------------
# Segunda opción para los eventos de GeeSports (sin tocar su agenda/listas).
# ---------------------------------------------------------------------------

# Equipos con derechos de Fox Corporation en Liga MX (2026): locales.
FOX_CORP_TEAMS = {
    "necaxa", "leon", "pachuca", "queretaro", "tijuana", "puebla",
}
# Compartidos: Fox puede emitirlos, pero no son exclusivos.
FOX_SHARED_TEAMS = {"tigres", "juarez", "toluca"}

# Ligas/competencias donde Fox Corporation tiene derechos (México).
# Ligas con derechos CONFIRMADOS del Fox nuevo (México). Liga MX se decide por
# el equipo local (FOX_CORP_TEAMS), no por la liga: un América-Chivas no es Fox.
FOX_LEAGUES = {"mex.1", "uefa.champions", "eng.1", "ger.1", "fra.1", "lmp"}


def _fold_team(value: str) -> str:
    text = fold(value or "")
    return text.replace(" fc", "").replace(" cf", "").strip()


def _gee_names(event: dict) -> list[str]:
    out: list[str] = []
    for raw in event.get("link_names") or []:
        name = str(raw or "").strip()
        if name and name not in out:
            out.append(name)
    for link in event.get("links") or []:
        if isinstance(link, dict):
            name = str(link.get("name") or "").strip()
            if name and name not in out:
                out.append(name)
    for raw in event.get("broadcasts") or []:
        name = str(raw or "").strip()
        if name and name not in out:
            out.append(name)
    return out


def _gee_home_away(event: dict) -> tuple[str, str]:
    home = str(event.get("home_name") or event.get("team_a") or "").strip()
    away = str(event.get("away_name") or event.get("team_b") or "").strip()
    return home, away


def _team_in(key: str, teams: set[str]) -> bool:
    """«Tigres UANL» cuenta como «tigres»; «Club León» como «leon»."""
    if not key:
        return False
    padded = f" {key} "
    return any(f" {team} " in padded for team in teams)


def _gee_league(event: dict) -> str:
    return str(event.get("league_id") or event.get("league") or event.get("event") or "").strip().lower()


def _xtv_option(channel: dict, recommended: bool, note: str) -> dict:
    return {
        "name": channel.get("displayName") or channel.get("name") or "",
        "code": channel.get("code") or "",
        "country": channel.get("country") or "",
        "brand": channel.get("brand") or "",
        "verified": bool(channel.get("verified")),
        "recommended": bool(recommended),
        "note": note or channel.get("note") or "",
        "source": "xtv",
    }


FOX_SCHEDULE_OFFLINE = os.environ.get("FOX_SCHEDULE_OFFLINE") == "1"
_FOX_ONE_CACHE: dict = {"at": 0.0, "items": []}
_FOX_BASIC_CACHE: dict = {"at": 0.0, "items": []}
FOX_ONE_HASH = "390866bae0b992a70f3e31bbfb925932ca9a93cb1761a4aeee81765981ba6ee3"

# Prueba de campo del 2026-11-01: Xolos vs Atlas se vio en FOX ONE; AYM
# estaba en Cuadro Titular. No se inventa: queda como evento verificado.
FOX_VERIFIED_EVENTS = [
    {"home": "Tijuana", "away": "Atlas", "date": "2026-11-01", "mirror": "FOX+"},
]
FOX_TEAM_ALIASES = {
    "xolos": "tijuana", "xolos de tijuana": "tijuana",
    "tigres": "tigres", "tigres uanl": "tigres",
    "aguilas": "america", "club america": "america",
    "psg": "paris saint germain", "paris sg": "paris saint germain",
    "bayern munchen": "bayern munich", "bayern muenchen": "bayern munich",
    "sporting cp": "sporting", "sporting clube de portugal": "sporting",
}


def _fox_one_snapshot() -> list[dict]:
    path = Path(__file__).with_name("fox_one_schedule.json")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        out = []
        for raw in data.get("events") or []:
            if not isinstance(raw, dict):
                continue
            out.append({
                "home": str(raw.get("home") or ""),
                "away": str(raw.get("away") or ""),
                "title": str(raw.get("title") or ""),
                "start": float(raw.get("start") or 0.0),
                "mirror": "FOX+",
                "source": "calendario Fox One (empaquetado)",
            })
        return out
    except Exception:  # noqa: BLE001
        return []


def _fox_one_events() -> list[dict]:
    """Calendario público de Fox One (premium). Cache de 10 min; sin red en tests."""
    now = time.time()
    if _FOX_ONE_CACHE["items"] and now - _FOX_ONE_CACHE["at"] < 600:
        return _FOX_ONE_CACHE["items"]
    if FOX_SCHEDULE_OFFLINE:
        items = _fox_one_snapshot()
        _FOX_ONE_CACHE["items"] = items
        _FOX_ONE_CACHE["at"] = now
        return items
    payload = {
        "variables": {
            "site": "caliente", "countryCode": "MX",
            "startDate": int(now - 2 * 86400), "endDate": int(now + 10 * 86400),
            "leagueIds": [],
        },
        "extensions": {"persistedQuery": {"version": 1, "sha256Hash": FOX_ONE_HASH}},
    }
    try:
        req = urllib.request.Request(
            "https://foxone-cached.api.viewlift.com/graphql",
            data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json", "Accept": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=12) as response:
            data = json.loads(response.read().decode("utf-8", "replace"))
        items = []
        for raw in ((data.get("data") or {}).get("gameSchedule") or {}).get("items") or []:
            if not isinstance(raw, dict):
                continue
            home = str((raw.get("homeTeam") or {}).get("title") or "").strip()
            away = str((raw.get("awayTeam") or {}).get("title") or "").strip()
            starts = raw.get("schedules") or []
            start = None
            if starts and isinstance(starts[0], dict):
                start = starts[0].get("startDate")
            if start is None:
                state = (raw.get("states") or {}).get("live") or {}
                start = state.get("startDateTime")
            if not home or not away:
                continue
            items.append({
                "home": home, "away": away,
                "title": str(raw.get("title") or ""),
                "start": float(start) if start else 0.0,
                "mirror": "FOX+",
                "source": "foxone",
            })
        _FOX_ONE_CACHE["items"] = items
        _FOX_ONE_CACHE["at"] = now
        return items
    except Exception:  # noqa: BLE001 - sin red/API se usa el calendario empaquetado
        items = _FOX_ONE_CACHE["items"] or _fox_one_snapshot()
        _FOX_ONE_CACHE["items"] = items
        _FOX_ONE_CACHE["at"] = now
        return items


def _fox_basic_events() -> list[dict]:
    """EPG FOX básico (FOXCAL) → AYM. Última copia buena empaquetada."""
    now = time.time()
    if _FOX_BASIC_CACHE["items"] and now - _FOX_BASIC_CACHE["at"] < 600:
        return _FOX_BASIC_CACHE["items"]
    path = Path(__file__).with_name("fox_basic_epg.json")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        items = []
        for raw in data.get("events") or []:
            if not isinstance(raw, dict):
                continue
            start = raw.get("start") or ""
            try:
                epoch = datetime.fromisoformat(start.replace("Z", "+00:00")).timestamp()
            except ValueError:
                epoch = 0.0
            items.append({
                "home": str(raw.get("home") or ""),
                "away": str(raw.get("away") or ""),
                "title": str(raw.get("title") or ""),
                "start": epoch,
                "mirror": "FOX",
                "source": "EPG FOX básico (FOXCAL)",
            })
        _FOX_BASIC_CACHE["items"] = items
        _FOX_BASIC_CACHE["at"] = now
        return items
    except Exception:  # noqa: BLE001
        return []


def _team_tokens(name: str) -> set[str]:
    key = _fold_team(name)
    key = FOX_TEAM_ALIASES.get(key, key)
    stop = {"fc", "cf", "club", "de", "del", "the", "los", "las", "la", "el", "cd"}
    return {token for token in key.split() if token and token not in stop}


def _teams_match(left: str, right: str) -> bool:
    a = _team_tokens(left)
    b = _team_tokens(right)
    if not a or not b:
        return False
    # Una parte puede ser la forma corta de la otra («Tigres» / «Tigres UANL»).
    return a <= b or b <= a or len(a & b) >= 2


def _event_epoch(event: dict) -> float:
    day = str(event.get("date_iso") or "").strip()
    clock = str(event.get("time") or "12:00:00").strip()
    if not day:
        return 0.0
    try:
        # Las tarjetas se publican en hora de México.
        from zoneinfo import ZoneInfo
        dt = datetime.fromisoformat(f"{day}T{clock or '12:00:00'}")
        return dt.replace(tzinfo=ZoneInfo("America/Mexico_City")).timestamp()
    except Exception:  # noqa: BLE001
        return 0.0


def _schedule_hits(event: dict, items: list[dict]) -> list[dict]:
    home, away = _gee_home_away(event)
    when = _event_epoch(event)
    if not when:
        return []  # sin fecha no se confirma un partido por equipos sueltos
    title = str(event.get("event") or "").lower()
    women = any(word in title for word in ("women", "femenil", "femenino"))
    out = []
    for item in items:
        item_title = str(item.get("title") or "").lower()
        item_names = " ".join((item_title, str(item.get("home") or "").lower(), str(item.get("away") or "").lower()))
        item_women = any(word in item_names for word in ("women", "femenil", "femenino"))
        if women != item_women:
            continue
        if any(word in item_names for word in ("sub-19", "sub 19", "u19", "sub-20", "sub 20")) and "sub" not in title:
            continue
        straight = _teams_match(home, item.get("home") or "") and _teams_match(away, item.get("away") or "")
        # La API de Fox One publica home/away invertidos en varios eventos; el
        # título sí trae el orden. Los equipos son distintos, así que aceptar el
        # cruce invertido no confunde un partido con otro.
        swapped = _teams_match(home, item.get("away") or "") and _teams_match(away, item.get("home") or "")
        if not (straight or swapped):
            continue
        start = float(item.get("start") or 0.0)
        if when and start and abs(when - start) > 48 * 3600:
            continue
        out.append(item)
    # La coincidencia más cercana primero.
    out.sort(key=lambda item: abs(when - float(item.get("start") or 0)) if when else 0)
    return out


def _schedule_mirror(event: dict) -> dict:
    """Devuelve {'basic': bool, 'premium': bool, 'source': str, 'confirmed': bool}."""
    home, away = _gee_home_away(event)
    day = str(event.get("date_iso") or "")
    for verified in FOX_VERIFIED_EVENTS:
        if verified.get("date") and verified["date"] != day:
            continue
        if _teams_match(home, verified.get("home") or "") and _teams_match(away, verified.get("away") or ""):
            return {"basic": False, "premium": True, "source": "prueba de campo", "confirmed": True}
    basic = bool(_schedule_hits(event, _fox_basic_events()))
    premium = bool(_schedule_hits(event, _fox_one_events()))
    source = []
    if basic:
        source.append("EPG FOX básico")
    if premium:
        source.append("calendario Fox One")
    return {"basic": basic, "premium": premium, "source": " + ".join(source), "confirmed": basic or premium}


def options_for_gee_event(event: dict) -> list[dict]:
    """Opciones XTV para un evento de GeeSports, con la regla Fox.

    - Fox Corp (equipo local o liga con derechos): añade FOX ONE (FOX+) como
      recomendado; añade AYM (FOX básico) cuando el nombre del evento apunta a
      FOX/FOX 365/NFL/NASCAR; **nunca** deja Fox Sports Lauman ni Star Channel
      como si fueran el nuevo Fox.
    - Otros enlaces: se traducen con el mapa (TUDN, ESPN, Claro…), sin OTT.
    """
    names = _gee_names(event)
    home, _away = _gee_home_away(event)
    league = _gee_league(event)
    home_key = _fold_team(home)
    fox_team = _team_in(home_key, FOX_CORP_TEAMS)
    fox_shared = _team_in(home_key, FOX_SHARED_TEAMS)
    fox_league = league in FOX_LEAGUES
    lower_names = {fold(name) for name in names}
    # Solo «FOX», «FOX+» y «FOX One» son el Fox NUEVO. «Fox Sports»/«Fox Sports
    # Premium» son la marca vieja de Lauman y no deben activar la regla Fox (un
    # evento de peleas sí puede ir por Fox Sports Premium).
    new_fox_name = any(
        name in ("fox", "fox+", "fox one", "fox plus")
        or name.startswith(("fox one ", "fox+ ", "fox plus "))
        for name in lower_names
    )
    fox_corp = fox_team or fox_shared or fox_league or new_fox_name
    tubi_hint = any("tubi" in name for name in lower_names)
    basic_hint = any(name in ("fox", "fox hd", "fox fhd", "fox 365", "cuadro titular") for name in lower_names)
    premium_hint = any("fox plus" in name or "fox+" in name or "fox one" in name for name in lower_names)

    found: list[dict] = []
    seen: set[str] = set()

    def add(channel: dict, recommended: bool, note: str) -> None:
        key = channel.get("code") or channel.get("name") or ""
        if not key or key in seen:
            return
        seen.add(key)
        found.append(_xtv_option(channel, recommended, note))

    schedule = _schedule_mirror(event)
    confirmed = bool(schedule.get("confirmed"))
    # 1) Enlaces declarados: traducirlos al canal XTV.
    for name in names:
        channels, _resolved = channels_for(name)
        for channel in channels:
            owner = str(channel.get("owner") or "")
            brand = fold(channel.get("brand") or "")
            if (fox_corp or confirmed) and (
                channel.get("legacy")
                or "Lauman" in owner
                or str(channel.get("name") or "").lower().startswith("star channel")
                or brand in ("fox one", "aym sports")
            ):
                # El Fox viejo/Star no es el nuevo Fox; FOX ONE/AYM se añaden
                # abajo con su recomendación correcta (básico vs premium).
                continue
            add(channel, False, "")

    # 2) Espejos Fox: manda el calendario/EPG. FOX|Tubi no tiene espejo XTV.
    if (fox_corp or confirmed) and not tubi_hint:
        fox_premium, _ = channels_for("FOX+")
        fox_basic, _ = channels_for("FOX")
        src = schedule.get("source") or "EPG/calendario"
        premium_note = f"FOX+ confirmado por {src}." if confirmed and schedule.get("premium") else (
            "FOX+ premium (Fox Corp); sin confirmación de EPG para este partido."
        )
        basic_note = f"FOX básico confirmado por {src}; AYM es el espejo." if confirmed and schedule.get("basic") else (
            "FOX básico (Cuadro Titular/FOX 365); sin confirmación de EPG para este partido."
        )
        if confirmed:
            if schedule.get("premium"):
                for channel in fox_premium:
                    add(channel, True, premium_note)
            if schedule.get("basic"):
                for channel in fox_basic:
                    add(channel, not schedule.get("premium"), basic_note)
        else:
            # Sin EPG no se inventa una recomendación: se muestran las dos
            # posibilidades (o la que declare el enlace) para que el usuario
            # elija, pero ninguna se marca como confirmada.
            if premium_hint and basic_hint:
                for channel in fox_premium:
                    add(channel, False, premium_note)
                for channel in fox_basic:
                    add(channel, False, basic_note)
            elif premium_hint:
                for channel in fox_premium:
                    add(channel, False, premium_note)
            elif basic_hint:
                for channel in fox_basic:
                    add(channel, False, basic_note)
            elif fox_team or fox_shared:
                for channel in fox_premium:
                    add(channel, False, premium_note)
                for channel in fox_basic:
                    add(channel, False, basic_note)

    # Recomendado primero, luego verificados y el resto.
    found.sort(key=lambda item: (0 if item["recommended"] else 1, 0 if item["verified"] else 1))
    return found[:6]
