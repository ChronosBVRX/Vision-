"""Agenda de fútbol: partidos desde la API pública de ESPN (sin clave ni cuota).

Ciclo propio dentro del worker `sync` (cada SPORTS_INTERVAL_SECONDS, 10 min por
defecto): baja el scoreboard de cada competición de LEAGUES para su ventana de
días (ayer, hoy, mañana y los dos siguientes, estos últimos con refresco más
lento) y actualiza `sports_matches`. No entra al índice de catálogo ni bumpea su
versión: la app lo consulta aparte con GET /v1/sports.

Cada liga lleva sus `channels`: las cadenas que suelen emitirla en México, en
orden de preferencia. La app las cruza contra los canales en vivo del aparato y
confirma con la EPG cuál está transmitiendo el partido, para el botón "Ver en
vivo".
"""

import asyncio
import json
import logging
import os
import random
import re
import ssl
import subprocess
import time
import unicodedata
import urllib.request
from datetime import date, datetime, timedelta, timezone

log = logging.getLogger("sports")

# La API de ESPN agrupa los días en hora del Este; se pide de más y la base
# recorta por fecha local. Los límites de "hoy" van en hora de México (UTC-6
# todo el año, sin horario de verano desde 2022).
MX = timezone(timedelta(hours=-6))

ESPN_URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard?dates={day}"
UA = "VisionPlusSync/1.0 (agenda de futbol)"

LSTV_URL = "https://www.livesoccertv.com/es/schedules/{day}/"
LSTV_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

LSTV_CHANNEL_MAP: dict[str, str] = {
    "canal 5 televisa": "Canal 5",
    "canal 5": "Canal 5",
    "azteca 7": "Azteca 7",
    "azteca deportes en vivo": "Azteca 7",
    "azteca deportes": "Azteca 7",
    "tudn": "TUDN",
    "tudn en vivo": "TUDN",
    "tudn usa": "TUDN USA",
    "fox mexico": "Fox Sports",
    "fox one": "Fox Sports",
    "fox sports mexico": "Fox Sports",
    "fox sports": "Fox Sports",
    "fox sports 2 mexico": "Fox Sports 2",
    "fox sports 2": "Fox Sports 2",
    "fox sports 3 mexico": "Fox Sports 3",
    "fox sports 3": "Fox Sports 3",
    "fox sports premium": "Fox Sports Premium",
    "fox+": "Fox Sports Premium",
    "espn mexico": "ESPN",
    "espn": "ESPN",
    "espn2 mexico": "ESPN 2",
    "espn 2 mexico": "ESPN 2",
    "espn 2": "ESPN 2",
    "espn3 mexico": "ESPN 3",
    "espn 3 mexico": "ESPN 3",
    "espn 3": "ESPN 3",
    "espn 4": "ESPN 4",
    "espn extra": "ESPN Extra",
    "espn premium": "ESPN Premium",
    "claro sports": "Claro Sports",
    "tnt sports": "TNT Sports",
    "max mexico": "TNT Sports",
    "max": "TNT Sports",
    "vix mexico": "ViX Deportes",
    "vix": "ViX Deportes",
    "vix premium": "ViX Deportes",
    "vix deportes": "ViX Deportes",
    "disney+ premium mexico": "ESPN",
    "disney+ mexico": "ESPN",
    "disney+": "ESPN",
    "disney plus": "ESPN",
    "star+": "ESPN",
    "star plus": "ESPN",
    "movistar laliga": "Movistar Futbol",
    "movistar futbol": "Movistar Futbol",
    "movistar plus": "Movistar Futbol",
    "movistar liga de campeones": "Liga de campeones",
    "liga de campeones": "Liga de campeones",
    "dazn": "DAZN",
    "dazn laliga": "DAZN",
    "dazn 1": "DAZN 1",
    "dazn 2": "DAZN 2",
    "canal gol": "Canal Gol",
    "gol play": "Canal Gol",
    "sky sports mexico": "Sky Sports",
    "sky sports": "Sky Sports",
    "sky+": "Sky Sports",
    "layvtime": "Layvtime",
    "caliente tv": "Caliente TV",
    "tubi mexico": "Tubi",
    "tubi": "Tubi",
    "telemundo": "Telemundo",
    "telemundo deportes en vivo": "Telemundo",
    "universo": "Universo",
    "win sports": "Win Sports",
    "win sports+": "Win Sports",
    "directv sports": "Directv Sports",
    "dsports": "Directv Sports",
    "dgo": "Directv Sports",
    "usa net": "USA Network",
    "usa network": "USA Network",
    "nbc": "NBC",
    "tele": "Telemundo",
    "cbssn": "CBS Sports Network",
    "cbs sports network": "CBS Sports Network",
    "paramount+": "Paramount+",
    "paramount": "Paramount+",
    "peacock": "Peacock",
    "bein": "beIN Sports",
    "bein sports": "beIN Sports",
    "fandango": "Fox Sports",
    "espn+": "ESPN",
    "espn deportes": "ESPN Deportes",
    "caliente": "Caliente TV",
}

MEXICO_HOME_CHANNELS: dict[str, list[str]] = {
    "america": ["TUDN", "Canal 5", "ViX Deportes"],
    "cruz azul": ["TUDN", "Canal 5", "ViX Deportes"],
    "chivas": ["Telemundo", "TUDN", "ViX Deportes"],
    "guadalajara": ["Telemundo", "TUDN", "ViX Deportes"],
    "pumas": ["TUDN", "Canal 5", "ViX Deportes"],
    "unam": ["TUDN", "Canal 5", "ViX Deportes"],
    "tigres": ["Azteca 7", "Fox Sports"],
    "uanl": ["Azteca 7", "Fox Sports"],
    "monterrey": ["TUDN", "ViX Deportes"],
    "rayados": ["TUDN", "ViX Deportes"],
    "toluca": ["TUDN", "Canal 5", "ViX Deportes"],
    "pachuca": ["Fox Sports", "Claro Sports"],
    "leon": ["Fox Sports", "Claro Sports"],
    "santos": ["TUDN", "ViX Deportes"],
    "atlas": ["TUDN", "Azteca 7"],
    "puebla": ["Azteca 7", "Fox Sports"],
    "tijuana": ["Caliente TV", "Fox Sports"],
    "xolos": ["Caliente TV", "Fox Sports"],
    "queretaro": ["Caliente TV", "Fox Sports"],
    "mazatlan": ["Azteca 7", "Fox Sports"],
    "necaxa": ["TUDN", "ViX Deportes"],
    "san luis": ["ESPN"],
    "juarez": ["Fox Sports", "Azteca 7"],
}


def _mexico_home_team_channels(home_name: str) -> list[str]:
    norm = _norm_text(home_name)
    for key, chs in MEXICO_HOME_CHANNELS.items():
        if key in norm:
            return list(chs)
    return ["TUDN", "Canal 5", "Azteca 7", "Fox Sports", "Claro Sports", "ViX Deportes", "ESPN"]

# (slug ESPN, título, cadenas candidatas en la lista del aparato, en orden)
LEAGUES: tuple[tuple[str, str, tuple[str, ...]], ...] = (
    ("mex.1", "Liga MX", ("TUDN", "Canal 5", "Azteca 7", "Fox Sports", "Claro Sports", "ViX Deportes", "ESPN")),
    ("mex.2", "Liga de Expansión MX", ("TUDN", "Fox Sports", "Claro Sports", "ESPN", "Azteca 7")),
    ("esp.1", "LaLiga", ("Movistar Futbol", "DAZN", "Directv Sports", "Canal Gol", "ESPN", "Sky Sports")),
    ("eng.1", "Premier League", ("TNT Sports", "Fox Sports", "ESPN", "DAZN", "Movistar Futbol", "Telemundo", "Universo")),
    ("ita.1", "Serie A", ("ESPN", "ESPN 2", "DAZN", "Directv Sports")),
    ("ger.1", "Bundesliga", ("ESPN", "ESPN 2", "DAZN", "Movistar Futbol", "Fox Sports", "Sky Sports")),
    ("fra.1", "Ligue 1", ("ESPN", "DAZN", "beIN Sports", "Caliente TV")),
    ("por.1", "Primeira Liga", ("ESPN", "ESPN 2", "Fox Sports")),
    ("uefa.champions", "UEFA Champions League", ("TNT Sports", "Liga de campeones", "Movistar Futbol", "ESPN", "DAZN", "Caliente TV")),
    ("uefa.europa", "UEFA Europa League", ("ESPN", "ESPN 2", "Fox Sports", "Liga de campeones", "Movistar Futbol")),
    ("conmebol.libertadores", "Copa Libertadores", ("ESPN", "ESPN 2", "Fox Sports", "Directv Sports")),
    ("conmebol.sudamericana", "Copa Sudamericana", ("ESPN", "ESPN 2", "Directv Sports")),
    ("arg.1", "Liga Profesional Argentina", ("ESPN", "TNT Sports", "TyC Sports")),
    ("bra.1", "Brasileirão", ("ESPN", "ESPN 2", "Fox Sports")),
    ("col.1", "Liga BetPlay", ("Win Sports",)),
    ("fifa.friendly", "Amistosos internacionales", ("TUDN", "Canal 5", "Azteca 7", "ESPN", "Fox Sports", "Telemundo", "Universo")),
    ("uefa.nations", "UEFA Nations League", ("ESPN", "Fox Sports", "TNT Sports", "Liga de campeones", "TUDN")),
    ("concacaf.nations.league", "Concacaf Nations League", ("TUDN", "Canal 5", "Azteca 7", "ESPN", "Universo", "Telemundo")),
    ("concacaf.gold", "Copa Oro", ("TUDN", "Canal 5", "Azteca 7", "ESPN", "Universo", "Telemundo")),
    ("fifa.world", "Copa del Mundo", ("TUDN", "Canal 5", "Azteca 7", "ESPN", "Fox Sports", "Telemundo", "Universo")),
)

UPSERT_SQL = """
insert into sports_matches (
    id, league_id, league_name, league_logo,
    home_id, home_name, home_abbr, home_logo, home_score,
    away_id, away_name, away_abbr, away_logo, away_score,
    kickoff, status, status_detail, venue, broadcasts, channels, updated_at
) values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20, now())
on conflict (id) do update set
    league_name = excluded.league_name,
    league_logo = excluded.league_logo,
    home_name = excluded.home_name,
    home_abbr = excluded.home_abbr,
    home_logo = excluded.home_logo,
    home_score = excluded.home_score,
    away_name = excluded.away_name,
    away_abbr = excluded.away_abbr,
    away_logo = excluded.away_logo,
    away_score = excluded.away_score,
    kickoff = excluded.kickoff,
    status = excluded.status,
    status_detail = excluded.status_detail,
    venue = excluded.venue,
    broadcasts = excluded.broadcasts,
    channels = excluded.channels,
    updated_at = now()
"""


def _norm_text(s: str) -> str:
    if not s:
        return ""
    t = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn").lower()
    return re.sub(r"[^a-z0-9]+", " ", t).strip()


def _clean_channel_name(raw: str) -> str:
    cleaned = raw.strip()
    norm = _norm_text(cleaned)
    return LSTV_CHANNEL_MAP.get(norm, cleaned)


TEAM_ALIASES: dict[str, set[str]] = {
    "guadalajara": {"chivas", "guadalajara"},
    "chivas": {"chivas", "guadalajara"},
    "tijuana": {"xolos", "tijuana"},
    "xolos": {"xolos", "tijuana"},
    "monterrey": {"rayados", "monterrey"},
    "rayados": {"rayados", "monterrey"},
    "america": {"america", "aguilas"},
    "pumas": {"pumas", "unam"},
    "unam": {"pumas", "unam"},
    "tigres": {"tigres", "uanl"},
    "uanl": {"tigres", "uanl"},
    "manchester united": {"man utd", "man united", "manchester united"},
    "manchester city": {"man city", "manchester city"},
    "paris saint germain": {"psg", "paris saint germain"},
    "atletico madrid": {"atletico", "atletico madrid"},
    "internazionale": {"inter", "internazionale", "inter milan"},
    "juventus": {"juve", "juventus"},
    "barcelona": {"barca", "barcelona"},
}


def _team_match_tokens(name: str) -> set[str]:
    norm = _norm_text(name)
    tokens = {
        tok for tok in norm.split()
        if len(tok) >= 3 and tok not in ("club", "cf", "fc", "cd", "real", "de", "del", "la", "el", "los", "las", "san")
    }
    for key, aliases in TEAM_ALIASES.items():
        if key in norm or any(tok in aliases for tok in tokens):
            tokens.update(aliases)
    return tokens


def match_teams(lstv_title: str, home_name: str, away_name: str) -> bool:
    title_norm = _norm_text(lstv_title)
    if not title_norm:
        return False
    home_tokens = _team_match_tokens(home_name)
    away_tokens = _team_match_tokens(away_name)
    if not home_tokens or not away_tokens:
        return False
    return any(tok in title_norm for tok in home_tokens) and any(tok in title_norm for tok in away_tokens)


def _parse_lstv_rows(html: str) -> list[dict]:
    rows = re.findall(r'<tr[^>]*class="matchrow"[^>]*>.*?</tr>', html, re.DOTALL)
    out: list[dict] = []
    for row in rows:
        title_m = re.search(r'title="([^"]+)"[^>]*id="g\d+"', row)
        if not title_m:
            continue
        title = title_m.group(1).strip()
        raw_channels = re.findall(r'<a href="[^"]*channels[^"]*"[^>]*>([^<]+)</a>', row)
        clean_channels: list[str] = []
        for ch in raw_channels:
            mapped = _clean_channel_name(ch)
            if mapped and mapped not in clean_channels:
                clean_channels.append(mapped)
        out.append({
            "title": title,
            "norm_title": _norm_text(title),
            "channels": clean_channels,
            "raw_channels": raw_channels,
        })
    return out


def _fetch_livesoccertv(day: date, timeout: int = 15) -> list[dict]:
    day_str = day.strftime("%Y-%m-%d")
    url = LSTV_URL.format(day=day_str)
    headers = {
        "User-Agent": LSTV_UA,
        "Cookie": "u_country=mexico; u_country_code=MX",
        "Accept-Language": "es-MX,es;q=0.9,en;q=0.8",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }
    ctx = ssl.create_default_context()
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            html = resp.read().decode("utf-8", errors="replace")
        return _parse_lstv_rows(html)
    except Exception as exc:
        log.warning("LiveSoccerTV %s urllib no respondió: %s; intentando curl", day_str, exc)
        try:
            curl_res = subprocess.run(
                [
                    "curl", "-s", "--max-time", str(timeout),
                    "-A", LSTV_UA,
                    "-H", "Cookie: u_country=mexico; u_country_code=MX",
                    "-H", "Accept-Language: es-MX,es;q=0.9,en;q=0.8",
                    "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    url,
                ],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            if curl_res.returncode == 0 and curl_res.stdout:
                return _parse_lstv_rows(curl_res.stdout)
        except Exception as curl_exc:
            log.warning("LiveSoccerTV %s curl falló: %s", day_str, curl_exc)
        return []


def _get(url: str, timeout: int = 20) -> dict:
    ctx = ssl.create_default_context()
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _parse_event(
    event: dict,
    league_id: str,
    league_name: str,
    league_logo: str,
    channels: tuple[str, ...],
    lstv_matches: list[dict] | None = None,
) -> dict | None:
    comps = event.get("competitions") or []
    if not comps:
        return None
    comp = comps[0]
    home = away = None
    for competitor in comp.get("competitors") or []:
        if competitor.get("homeAway") == "home":
            home = competitor
        elif competitor.get("homeAway") == "away":
            away = competitor
    if not home or not away:
        return None

    stype = (event.get("status") or {}).get("type") or {}
    state = stype.get("state") or "pre"
    if state not in ("pre", "in", "post"):
        state = "pre"
    detail = "" if state == "pre" else (stype.get("shortDetail") or "").strip()

    try:
        kickoff = datetime.fromisoformat((event.get("date") or "").replace("Z", "+00:00"))
    except ValueError:
        return None

    names: list[str] = []
    for entry in comp.get("broadcasts") or []:
        for name in entry.get("names") or []:
            if name and name not in names:
                names.append(name)
    for entry in comp.get("geoBroadcasts") or []:
        name = ((entry.get("media") or {}).get("shortName") or "").strip()
        if name and name not in names:
            names.append(name)

    def side(competitor: dict) -> dict:
        team = competitor.get("team") or {}
        logos = team.get("logos") or []
        logo = (logos[0].get("href") if logos else "") or team.get("logo") or ""
        score = None
        if state != "pre":
            raw = str(competitor.get("score") or "").strip()
            score = int(raw) if raw.lstrip("-").isdigit() else None
        return {
            "id": str(team.get("id") or ""),
            "name": team.get("displayName") or team.get("name") or "",
            "abbr": team.get("abbreviation") or "",
            "logo": logo,
            "score": score,
        }

    h_side = side(home)
    a_side = side(away)

    event_id = str(event.get("id") or "")
    if not event_id:
        return None

    assigned_channels: list[str] = []
    if lstv_matches:
        matched_lstv = next(
            (m for m in lstv_matches if match_teams(m["title"], h_side["name"], a_side["name"])),
            None
        )
        if matched_lstv and matched_lstv["channels"]:
            assigned_channels = list(matched_lstv["channels"])
            for raw_ch in matched_lstv.get("raw_channels") or []:
                if raw_ch and raw_ch not in names:
                    names.append(raw_ch)

    if not assigned_channels and names:
        for b_name in names:
            clean = _clean_channel_name(b_name)
            if clean and clean not in assigned_channels:
                assigned_channels.append(clean)

    if not assigned_channels:
        if league_id == "mex.1":
            assigned_channels = _mexico_home_team_channels(h_side["name"])
        else:
            assigned_channels = list(channels)

    return {
        "id": event_id,
        "league_id": league_id,
        "league_name": league_name,
        "league_logo": league_logo,
        "home": h_side,
        "away": a_side,
        "kickoff": kickoff,
        "status": state,
        "detail": detail,
        "venue": ((comp.get("venue") or {}).get("fullName") or "").strip(),
        "broadcasts": names,
        "channels": assigned_channels,
    }


async def _fetch(
    sem: asyncio.Semaphore,
    league: tuple,
    day: date,
    lstv_matches: list[dict] | None = None,
) -> list[dict]:
    slug, name, channels = league
    async with sem:
        try:
            data = await asyncio.to_thread(
                _get, ESPN_URL.format(slug=slug, day=day.strftime("%Y%m%d"))
            )
        except Exception as exc:  # noqa: BLE001 - una liga caída no tumba el ciclo
            log.warning("ESPN %s %s no respondió: %s", slug, day, exc)
            return []
    league_logo = ""
    for entry in data.get("leagues") or []:
        for logo in entry.get("logos") or []:
            league_logo = logo.get("href") or ""
            break
        break
    out = []
    for event in data.get("events") or []:
        parsed = _parse_event(event, slug, name, league_logo, channels, lstv_matches)
        if parsed:
            out.append(parsed)
    return out


async def _sync_days(pool, days: list[date]) -> int:
    """Baja las ligas para cada día y hace upsert; poda los partidos viejos."""
    sem = asyncio.Semaphore(int(os.environ.get("SPORTS_CONCURRENCY", "6") or "6"))
    lstv_by_day = {}
    for day in days:
        try:
            lstv_by_day[day] = await asyncio.to_thread(_fetch_livesoccertv, day)
        except Exception as exc:  # noqa: BLE001
            log.warning("Error obteniendo LiveSoccerTV para %s: %s", day, exc)
            lstv_by_day[day] = []

    groups = await asyncio.gather(*(
        _fetch(sem, league, day, lstv_by_day.get(day))
        for day in days
        for league in LEAGUES
    ))
    rows = [row for group in groups for row in group]
    if rows:
        params = [
            (
                r["id"], r["league_id"], r["league_name"], r["league_logo"],
                r["home"]["id"], r["home"]["name"], r["home"]["abbr"], r["home"]["logo"],
                r["home"]["score"],
                r["away"]["id"], r["away"]["name"], r["away"]["abbr"], r["away"]["logo"],
                r["away"]["score"],
                r["kickoff"], r["status"], r["detail"], r["venue"],
                r["broadcasts"], r["channels"],
            )
            for r in rows
        ]
        async with pool.acquire() as conn:
            await conn.executemany(UPSERT_SQL, params)
    async with pool.acquire() as conn:
        await conn.execute("delete from sports_matches where kickoff < now() - interval '2 days'")
    return len(rows)


async def loop(pool) -> None:
    """Tarea de fondo del worker: agenda siempre fresca sin tocar el ciclo de 6 h."""
    if os.environ.get("SPORTS_SYNC", "1") != "1":
        log.info("deportes: deshabilitado por entorno")
        return
    interval = int(os.environ.get("SPORTS_INTERVAL_SECONDS", "600") or "600")
    horizon: dict[date, float] = {}
    cycle = 0
    log.info("deportes: ciclo propio cada %s s", interval)
    while True:
        try:
            today = datetime.now(MX).date()
            days = {today, today + timedelta(days=1)}
            if cycle % 6 == 0:
                days.add(today - timedelta(days=1))
            now = time.monotonic()
            for offset in (2, 3):
                day = today + timedelta(days=offset)
                if now - horizon.get(day, 0.0) > 3600:
                    days.add(day)
            count = await _sync_days(pool, sorted(days))
            for day in days:
                horizon[day] = time.monotonic()
            log.info("deportes: %s partidos al día (%s días)", count, len(days))
        except Exception:  # noqa: BLE001 - se reintenta en el siguiente ciclo
            log.exception("deportes: el ciclo falló")
        cycle += 1
        await asyncio.sleep(interval * (1 + random.uniform(-0.05, 0.05)))
