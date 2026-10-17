"""GeeSports V3.8 (com.live.geesports) — fuente de deportes en vivo.

Baja, descifra y publica el catálogo deportivo del CMS de GeeSports V3.8:
  - `sports.txt`  → 36 grupos de canales (TNT Sports, FOX, Sky, TUDN, LaLiga TV…)
  - `events.txt`  → eventos deportivos (categoría, equipos, fecha/hora, enlaces)
  - `channels/*.txt` y `pro/*.txt` → canales y enlaces finales (HLS/DASH-ClearKey)

Cifrado del CMS (descifrado puro aquí, sin Android; receta verificada en vivo):
    claro = AES-128-CBC( JSON + PKCS7, KEY, IV )     ← IV constante de su APK
    paso1 = base64( claro )
    paso2 = reverse( pairswap( paso1 ) )             ← se intercambian pares de 2 caracteres
    texto = base64( paso2 )
    KEY   = se resuelve por entorno (GEE38_KEY) o derivándola del APK
            (GEE38_APK, requiere `cryptography`) — receta en las credenciales locales.

El worker corre en su propio loop (como `sports.loop`) y NO toca el índice
de catálogo ni su versión. Las tablas `gee38_catalog` / `gee38_meta` guardan
el resultado ya resuelto en JSONB para que la API lo sirva tal cual.
"""

from __future__ import annotations

import asyncio
import ast
import base64
import hashlib
import json
import logging
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta

import asyncpg
from Crypto.Cipher import AES

log = logging.getLogger("gee38")

BASE_DEFAULT = "https://new.whatyouthink.site/"
BASE = os.environ.get("GEE38_BASE", BASE_DEFAULT).rstrip("/") + "/"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
IV = b"bkmlmKnk9i5Khs9n"  # constante de su APK (ver .visionplus/gee38_key.txt)

PROBE_CONCURRENCY = 24
PROBE_TIMEOUT = 6.0
PROBE_BYTES = 800

# Cuántos eventos reciben enlaces frescos por ciclo (priorizados por cercanía:
# hoy/mañana primero). Los demás conservan los enlaces del ciclo anterior.
EVENTS_LINKS_MAX = 160


# ----------------------------------------------------------------------------
# Cifrado
# ----------------------------------------------------------------------------

def pairswap(s: str) -> str:
    a = list(s)
    for i in range(0, len(a) - 1, 2):
        a[i], a[i + 1] = a[i + 1], a[i]
    return "".join(a)


def derive_key_from_cert(cert_der: bytes) -> bytes:
    inner = base64.b64encode(hashlib.sha256(cert_der).digest()).decode()
    outer = base64.b64encode(pairswap(inner[::-1]).encode()).decode()
    return outer[0:16].encode()


def derive_key_from_apk(apk_path: str) -> bytes | None:
    """Deriva la clave del certificado del APK (requiere `cryptography`)."""
    try:
        import zipfile

        from cryptography.hazmat.primitives.serialization import pkcs7
        import cryptography.hazmat.primitives.serialization as ser

        with zipfile.ZipFile(apk_path) as z:
            name = next(
                n for n in z.namelist()
                if n.upper().startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC"))
            )
            der = z.read(name)
        cert = pkcs7.load_der_pkcs7_certificates(der)[0].public_bytes(ser.Encoding.DER)
        return derive_key_from_cert(cert)
    except Exception:  # noqa: BLE001 - sin derivación, se usa GEE38_KEY
        log.exception("gee38: no se pudo derivar la clave del APK %s", apk_path)
        return None


def resolve_key() -> bytes | None:
    """Clave del CMS: GEE38_KEY (hex o ascii) o derivada de GEE38_APK."""
    env = os.environ.get("GEE38_KEY", "").strip()
    if env:
        try:
            raw = bytes.fromhex(env)
            if len(raw) == 16:
                return raw
        except ValueError:
            pass
        if len(env) == 16:
            return env.encode()
        log.error("gee38: GEE38_KEY inválida (16 chars ascii o 32 hex)")
    apk = os.environ.get("GEE38_APK", "").strip()
    if apk and os.path.exists(apk):
        return derive_key_from_apk(apk)
    return None


def decrypt_body(body_text: str, key: bytes) -> bytes:
    s = "".join(body_text.split())
    # Los archivos retirados del CMS devuelven la página HTML de su hosting:
    # eso no es base64 y se trata como "no disponible" (sin reventar el ciclo).
    if len(s) < 24 or not re.fullmatch(r"[A-Za-z0-9+/=]+", s):
        raise ValueError("respuesta no cifrada (¿archivo retirado del CMS?)")
    L1 = base64.b64decode(s + "=" * ((-len(s)) % 4)).decode("latin-1")
    ct = base64.b64decode(pairswap(L1)[::-1])
    out = AES.new(key, AES.MODE_CBC, IV).decrypt(ct)
    pad = out[-1]
    if 1 <= pad <= 16 and out[-pad:] == bytes([pad]) * pad:
        out = out[:-pad]
    return out


# ----------------------------------------------------------------------------
# Parseo (funciones puras, probadas en server/tests/test_gee38.py)
# ----------------------------------------------------------------------------

def _maybe_json(value):
    """Algunos campos vienen como string con JSON embebido (a veces con repr
    de Python: `['A', 'B']` — comillas simples — además del JSON estricto)."""
    if isinstance(value, str):
        try:
            return json.loads(value)
        except Exception:  # noqa: BLE001
            pass
        try:
            return ast.literal_eval(value)
        except Exception:  # noqa: BLE001
            return value
    return value


def _safe_int(value, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def parse_groups(data: bytes) -> list[dict]:
    """sports.txt descifrado → [{name, logo, type, api}]"""
    raw = json.loads(data)
    out = []
    for item in raw if isinstance(raw, list) else []:
        cat = _maybe_json(item.get("cat")) if isinstance(item, dict) else None
        if not isinstance(cat, dict):
            continue
        out.append({
            "name": str(cat.get("name", "")).strip(),
            "logo": str(cat.get("logo", "")).strip(),
            "type": (str(cat.get("type", "custom")).strip().lower() or "custom"),
            "api": str(cat.get("api", "")).strip(),
        })
    return [g for g in out if g["name"]]


def parse_group_bundle(data: bytes) -> list[dict]:
    """channels/<x>.txt descifrado → canales del grupo."""
    raw = json.loads(data)
    out = []
    for item in raw if isinstance(raw, list) else []:
        inner = _maybe_json(item.get("channel")) if isinstance(item, dict) else None
        if not isinstance(inner, dict):
            continue
        names = _maybe_json(inner.get("link_names", []))
        if isinstance(names, str):
            names = [names]
        out.append({
            "name": str(inner.get("name", "")).strip(),
            "logo": str(inner.get("logo", "")).strip(),
            "visible": str(inner.get("visible", "true")).lower() != "false",
            "links_ref": str(inner.get("links", "")).strip(),
            "is_playlist": str(inner.get("is_playlist", "false")).lower() == "true",
            "link_names": [str(n).strip() for n in names] if isinstance(names, list) else [],
        })
    return out


def split_link(raw: str) -> tuple[str, dict]:
    """'url|k=v&k2=v2' → (url, headers). GeeSports usa '|user-agent=…'."""
    trimmed = raw.strip()
    idx = trimmed.find("|")
    base = trimmed[:idx] if idx >= 0 else trimmed
    base = base.rstrip("?").strip()
    headers: dict[str, str] = {}
    if idx >= 0:
        for pair in trimmed[idx + 1:].split("&"):
            if "=" not in pair:
                continue
            k, v = pair.split("=", 1)
            headers[k.strip().lower()] = urllib.parse.unquote(v.strip())
    return base, headers


def parse_pro(data: bytes) -> list[dict]:
    """pro/<x>.txt descifrado → enlaces finales jugables."""
    raw = json.loads(data)
    out = []
    for item in raw if isinstance(raw, list) else []:
        if not isinstance(item, dict):
            continue
        raw_link = str(item.get("link", "")).strip()
        if not raw_link:
            continue
        url, headers = split_link(raw_link)
        api = str(item.get("api", "")).strip()
        drm = api if ":" in api else ""
        scheme = _safe_int(item.get("scheme"), 0)
        is_dash = url.lower().split("?")[0].endswith(".mpd")
        out.append({
            "name": str(item.get("name", "")).strip(),
            "link": raw_link,               # tal cual (con |headers) para el player
            "url": url,
            "headers": headers,
            "scheme": scheme,
            "drm": drm,                     # "kid:key" → ClearKey W3C en la app
            "type": "dash" if is_dash else "hls",
            "alive": None,                  # se llena en el sondeo
            "checked_at": None,
        })
    return out


_EXTVLCOPT_RE = re.compile(r"^#EXTVLCOPT:http-(user-agent|referrer|referer|origin)=(.+)$", re.I)
_EXTINF_RE = re.compile(r'^#EXTINF:-?\d+(?:\s+(.*))?,\s*(.*)$')


def parse_m3u(text: str) -> list[dict]:
    """M3U plano (con #EXTVLCOPT) → mismos enlaces normalizados."""
    out: list[dict] = []
    meta: dict[str, str] = {}
    pending: dict | None = None
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        m = _EXTVLCOPT_RE.match(line)
        if m:
            key = m.group(1).lower()
            if key == "referrer":
                key = "referer"
            meta[key] = m.group(2).strip()
            continue
        m = _EXTINF_RE.match(line)
        if m:
            attrs = m.group(1) or ""
            title = m.group(2).strip()
            group = ""
            gm = re.search(r'group-title="([^"]*)"', attrs)
            if gm:
                group = gm.group(1)
            lg = re.search(r'tvg-logo="([^"]*)"', attrs)
            pending = {"name": title, "group": group, "logo": lg.group(1) if lg else ""}
            continue
        if line.startswith("#"):
            continue
        if pending is None:
            continue
        url = line
        headers = dict(meta)
        meta = {}
        raw = url
        if headers:
            raw = url + "|" + "&".join(f"{k}={urllib.parse.quote(v, safe='')}" for k, v in headers.items())
        out.append({
            "name": pending["name"],
            "group": pending["group"],
            "logo": pending["logo"],
            "link": raw,
            "url": url,
            "headers": headers,
            "scheme": 0,
            "drm": "",
            "type": "dash" if url.lower().split("?")[0].endswith(".mpd") else "hls",
            "alive": None,
            "checked_at": None,
        })
        pending = None
    return out


def parse_app(data: bytes) -> dict:
    raw = json.loads(data)
    first = raw[0] if isinstance(raw, list) and raw else (raw if isinstance(raw, dict) else {})
    keep = (
        "app_version", "telegram_url", "web_url", "download_url", "message", "notice",
        "foot_live_url", "cric_live_url", "supportWaitSeconds", "supportReopenHours",
    )
    return {k: first.get(k) for k in keep if first.get(k) is not None}


def parse_events(data: bytes) -> list[dict]:
    raw = json.loads(data)
    out = []
    for i, item in enumerate(raw if isinstance(raw, list) else []):
        inner = _maybe_json(item.get("event")) if isinstance(item, dict) else None
        if not isinstance(inner, dict):
            continue
        names = inner.get("link_names", [])
        if isinstance(names, str):
            names = _maybe_json(names)
            if isinstance(names, str):
                names = [names]
        d = str(inner.get("date", "")).strip()
        iso = None
        try:
            iso = datetime.strptime(d, "%d/%m/%Y").date().isoformat()
        except Exception:  # noqa: BLE001
            pass
        out.append({
            "idx": i,
            "category": str(inner.get("category", "")).strip(),
            "event": str(inner.get("eventName", "")).strip(),
            "logo": str(inner.get("eventLogo", "")).strip(),
            "team_a": str(inner.get("teamAName", "")).strip(),
            "team_b": str(inner.get("teamBName", "")).strip(),
            "flag_a": str(inner.get("teamAFlag", "")).strip(),
            "flag_b": str(inner.get("teamBFlag", "")).strip(),
            "links_ref": str(inner.get("links", "")).strip(),
            "link_names": [str(n).strip() for n in names] if isinstance(names, list) else [],
            "date": d,
            "date_iso": iso,
            "time": str(inner.get("time", "")).strip(),
            "visible": str(inner.get("visible", "true")).lower() != "false",
            "priority": _safe_int(inner.get("priority"), -1),
        })
    return out


# ----------------------------------------------------------------------------
# Red
# ----------------------------------------------------------------------------

def _busted_url(path_or_url: str) -> str:
    """El hosting del CMS sirve copias cacheadas rancias sin un cache-buster:
    los archivos del CMS se piden con `?t=` fresco; los enlaces externos no."""
    if path_or_url.startswith("http"):
        return path_or_url
    url = BASE + path_or_url.lstrip("/")
    sep = "&" if "?" in url else "?"
    return f"{url}{sep}t={int(time.time() * 1000)}"


def _fetch(path_or_url: str, timeout: int = 25) -> str:
    url = _busted_url(path_or_url)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", "replace")


async def _fetch_async(path_or_url: str, timeout: int = 25) -> str:
    return await asyncio.to_thread(_fetch, path_or_url, timeout)


async def _fetch_bytes_async(path_or_url: str, timeout: int = 25) -> bytes:
    def _go() -> bytes:
        url = _busted_url(path_or_url)
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*", "Cache-Control": "no-cache"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read()
    return await asyncio.to_thread(_go)


async def _probe(link: dict) -> None:
    """Sondeo de vida de un enlace: DNS + GET corto con sus cabeceras."""
    headers = {"User-Agent": UA}
    headers.update({k.replace("user-agent", "User-Agent").title(): v for k, v in (link.get("headers") or {}).items()})
    headers["Range"] = f"bytes=0-{PROBE_BYTES}"

    def _go() -> int | None:
        req = urllib.request.Request(link["url"], headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=PROBE_TIMEOUT) as resp:
                return resp.status
        except urllib.error.HTTPError as exc:
            return exc.code
        except Exception:  # noqa: BLE001 - DNS/timeout/etc: caído
            return None

    code = await asyncio.to_thread(_go)
    link["alive"] = bool(code and 200 <= code < 400)
    link["http_code"] = code
    link["checked_at"] = datetime.utcnow().isoformat(timespec="seconds")


async def _probe_many(links: list[dict]) -> None:
    sem = asyncio.Semaphore(PROBE_CONCURRENCY)

    async def _one(link: dict) -> None:
        async with sem:
            await _probe(link)

    await asyncio.gather(*(_one(lk) for lk in links), return_exceptions=True)


_ALIVE_ORDER = {True: 0, None: 1, False: 2}


def _sort_links(links: list[dict]) -> list[dict]:
    """Vivos primero, en duda en medio, caídos al final (la app toma el primero)."""
    return sorted(
        links,
        key=lambda lk: (_ALIVE_ORDER.get(lk.get("alive"), 1), str(lk.get("name", ""))),
    )


# ----------------------------------------------------------------------------
# Sincronización
# ----------------------------------------------------------------------------

def _group_id(name: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return f"group:{slug or 'sin-nombre'}"


def _api_timestamp(api: str) -> int:
    """Los nombres del CMS llevan `nombre<epoch>` en base64: más grande = más nuevo."""
    name = api.rsplit("/", 1)[-1]
    if name.endswith(".txt"):
        name = name[:-4]
    try:
        text = base64.b64decode(name + "=" * ((-len(name)) % 4)).decode("utf-8", "replace")
    except Exception:  # noqa: BLE001
        return 0
    m = re.search(r"(\d{10,})$", text)
    return int(m.group(1)) if m else 0


async def _sync_groups(pool: asyncpg.Pool, key: bytes, meta: dict) -> int:
    """Grupos de canales: canales + enlaces finales (con sondeo).

    El CMS trae entradas duplicadas por nombre (una vieja con archivos ya
    retirados): se conserva la de mejor rango —archivo más reciente y con más
    enlaces— y un grupo que falle no pisa lo que ya estaba guardado. Al final
    se podan los grupos que ya no existen en `sports.txt`.
    """
    groups = parse_groups(decrypt_body(await _fetch_async("sports.txt"), key))
    seen_ids: list[str] = []
    best: dict[str, tuple[tuple, dict, int]] = {}  # id -> (rango, payload, sort)

    for sort, g in enumerate(groups):
        gid = _group_id(g["name"])
        seen_ids.append(gid)
        links: list[dict] = []
        channels: list[dict] = []
        try:
            if g["type"] == "m3u" and g["api"].startswith("http"):
                entries = parse_m3u(await _fetch_async(g["api"]))
                channels = [{"name": g["name"], "logo": g["logo"], "links": entries}]
                links = entries
            elif g["api"]:
                bundle = parse_group_bundle(decrypt_body(await _fetch_async(g["api"]), key))

                async def _channel_links(ch: dict) -> None:
                    if not ch["links_ref"]:
                        return
                    try:
                        ch["links"] = parse_pro(
                            decrypt_body(await _fetch_async(ch["links_ref"]), key)
                        )
                    except Exception as exc:  # noqa: BLE001 - un canal caído no tumba al grupo
                        log.warning("gee38: canal %s sin enlaces (%s)", ch["name"][:30], exc)

                await asyncio.gather(*(_channel_links(ch) for ch in bundle))
                for ch in bundle:
                    ch_out = {k: v for k, v in ch.items() if k != "links_ref"}
                    ch_out["links"] = ch.get("links") or []
                    channels.append(ch_out)
                    links.extend(ch_out["links"])
        except Exception as exc:  # noqa: BLE001 - entrada retirada del CMS o red
            log.warning("gee38: grupo %s no disponible (%s)", g["name"], exc)

        if links:
            await _probe_many(links)
        for ch_out in channels:
            ch_out["links"] = _sort_links(ch_out.get("links") or [])
        links = [lk for ch_out in channels for lk in ch_out.get("links", [])]
        alive = sum(1 for lk in links if lk.get("alive"))
        payload = {
            "name": g["name"],
            "logo": g["logo"],
            "type": g["type"],
            "channels": channels,
            "alive": alive,
            "total": len(links),
        }
        # Rango: primero los que traen canales, luego frescura, vivos y total.
        rank = (bool(channels), _api_timestamp(g["api"]), alive, len(links))
        prev = best.get(gid)
        if prev is None or rank > prev[0]:
            best[gid] = (rank, payload, sort)

    written = 0
    async with pool.acquire() as conn:
        stored_groups = int(await conn.fetchval("select count(*) from gee38_catalog where kind = 'group'") or 0)
        for gid, (rank, payload, sort) in best.items():
            if not payload["channels"]:
                continue  # falló este ciclo: se conserva lo guardado antes
            await conn.execute(
                """
                insert into gee38_catalog (id, kind, name, category, sort, payload, updated_at)
                values ($1, 'group', $2, null, $3, $4::jsonb, now())
                on conflict (id) do update
                    set name = excluded.name, sort = excluded.sort,
                        payload = excluded.payload, updated_at = now()
                """,
                gid, payload["name"], sort, json.dumps(payload, ensure_ascii=False),
            )
            written += 1
        unique_seen = list(dict.fromkeys(seen_ids))
        if stored_groups > 12 and len(unique_seen) < stored_groups * 0.7:
            # sports.txt también puede venir a medias: mejor conservar de más que perder.
            log.warning("gee38: sports.txt vino a medias (%s vs %s grupos): no se poda", len(unique_seen), stored_groups)
        else:
            await conn.execute(
                "delete from gee38_catalog where kind = 'group' and not (id = any($1::text[]))",
                unique_seen,
            )
    return written

def _event_id(ev: dict) -> str:
    """ID estable por contenido: el orden del archivo cambia entre ciclos.

    OJO: los equipos entran al hash — el CMS rotula todos los partidos de una
    jornada igual («Liga MX, Apertura» + misma fecha/hora), y sin los equipos
    dos partidos distintos colisionaban y uno pisaba al otro (visto en vivo:
    214 eventos escritos, 149 guardados).
    """
    raw = (
        f"{ev['category']}|{ev['event']}|{ev.get('team_a', '')}|{ev.get('team_b', '')}"
        f"|{ev['date']}|{ev['time']}"
    ).encode("utf-8", "replace")
    return "event:" + hashlib.sha1(raw).hexdigest()[:14]


def _event_rank(ev: dict, today: date) -> tuple:
    """Orden de atención: visibles primero y luego por cercanía de fecha."""
    dist = 999
    if ev["date_iso"]:
        try:
            dist = abs((date.fromisoformat(ev["date_iso"]) - today).days)
        except ValueError:
            pass
    return (0 if ev["visible"] else 1, dist, ev["event"])


async def _sync_events(pool: asyncpg.Pool, key: bytes, meta: dict) -> int:
    events = parse_events(decrypt_body(await _fetch_async("events.txt"), key))
    today = date.today()
    window_str = {(today + timedelta(days=delta)).isoformat() for delta in (-1, 0, 1, 2, 3)}
    old: dict[str, dict] = {}
    async with pool.acquire() as conn:
        for row in await conn.fetch("select id, payload from gee38_catalog where kind = 'event'"):
            value = row["payload"]
            if isinstance(value, str):
                try:
                    value = json.loads(value)
                except Exception:  # noqa: BLE001
                    value = None
            if isinstance(value, dict):
                old[row["id"]] = value

    fresh_budget = EVENTS_LINKS_MAX
    seen_ids: list[str] = []
    written = 0
    for ev in sorted(events, key=lambda e: _event_rank(e, today)):
        eid = _event_id(ev)
        seen_ids.append(eid)
        links: list[dict] = []
        fetched = False
        wanted = ev["visible"] or (ev["date_iso"] in window_str)
        if ev["links_ref"] and wanted and fresh_budget > 0:
            try:
                links = parse_pro(decrypt_body(await _fetch_async(ev["links_ref"]), key))
                fresh_budget -= 1
                fetched = True
                if ev["date_iso"] in window_str:
                    await _probe_many(links)
            except Exception as exc:  # noqa: BLE001
                log.warning("gee38: evento %s sin enlaces (%s)", ev["event"][:40], exc)
        if not fetched:
            # Sin presupuesto este ciclo: conserva los enlaces del ciclo anterior.
            links = (old.get(eid) or {}).get("links") or []
        links = _sort_links(links)
        payload = {k: v for k, v in ev.items() if k != "idx"}
        payload["links"] = links
        payload["alive"] = sum(1 for lk in links if lk.get("alive"))
        payload["total"] = len(links)
        async with pool.acquire() as conn:
            await conn.execute(
                """
                insert into gee38_catalog (id, kind, name, category, sort, payload, updated_at)
                values ($1, 'event', $2, $3, $4, $5::jsonb, now())
                on conflict (id) do update
                    set name = excluded.name, category = excluded.category, sort = excluded.sort,
                        payload = excluded.payload, updated_at = now()
                """,
                eid, ev["event"], ev["category"], ev["idx"],
                json.dumps(payload, ensure_ascii=False),
            )
        written += 1
    async with pool.acquire() as conn:
        unique_seen = list(dict.fromkeys(seen_ids))
        stored_events = len(old)
        if stored_events > 60 and len(unique_seen) < stored_events * 0.5:
            # events.txt se publica a medias de vez en cuando (visto en vivo:
            # 214 → 149 → 214 en minutos): nunca se poda con una lectura parcial.
            log.warning(
                "gee38: events.txt vino a medias (%s vs %s guardados): no se poda",
                len(unique_seen), stored_events,
            )
        else:
            # Solo se poda lo viejo: lo reciente/programado jamás se pierde.
            cutoff = (today - timedelta(days=3)).isoformat()
            await conn.execute(
                "delete from gee38_catalog where kind = 'event'"
                " and (payload->>'date_iso') < $1 and not (id = any($2::text[]))",
                cutoff,
                unique_seen,
            )
    return written


async def sync(pool: asyncpg.Pool) -> int:
    key = resolve_key()
    if not key:
        log.warning("gee38: sin clave (GEE38_KEY/GEE38_APK); se omite el ciclo")
        return 0
    app = parse_app(decrypt_body(await _fetch_async("app.txt"), key))
    async with pool.acquire() as conn:
        await conn.execute(
            """
            insert into gee38_meta (k, v, updated_at) values ('app', $1::jsonb, now())
            on conflict (k) do update set v = excluded.v, updated_at = now()
            """,
            json.dumps(app, ensure_ascii=False),
        )
    groups = await _sync_groups(pool, key, app)
    events = await _sync_events(pool, key, app)
    log.info("gee38: %s grupos + %s eventos actualizados (v%s)", groups, events, app.get("app_version"))
    return groups + events


async def loop(pool: asyncpg.Pool) -> None:
    """Tarea de fondo: su propio ciclo, sin tocar el índice de catálogo."""
    if os.environ.get("GEE38_SYNC", "1") != "1":
        log.info("gee38: deshabilitado por entorno")
        return
    interval = int(os.environ.get("GEE38_INTERVAL_SECONDS", "1800") or "1800")
    log.info("gee38: ciclo propio cada %s s", interval)
    while True:
        started = time.monotonic()
        try:
            await sync(pool)
        except Exception:  # noqa: BLE001 - se reintenta en el siguiente ciclo
            log.exception("gee38: el ciclo falló")
        elapsed = time.monotonic() - started
        await asyncio.sleep(max(60.0, interval - elapsed))
