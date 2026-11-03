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
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timedelta

import asyncpg
from Crypto.Cipher import AES

log = logging.getLogger("gee38")


def _cms_gap(exc: Exception) -> bool:
    """¿Es un hueco permanente del CMS (archivo retirado o enlace vacío)?

    Estos casos se repiten en cada ciclo (TAPMAD BD, Willow Event) y no son un fallo
    nuestro: se registran como INFO para no llenar el log de avisos idénticos. Un
    problema de red o de cifrado sigue siendo WARNING.
    """
    text = str(exc).lower()
    return (
        "404" in text
        or "no host given" in text
        or "archivo retirado del cms" in text
    )


def _gap_log(message: str, exc: Exception) -> None:
    """Registra el hueco del CMS con el nivel que le toca (INFO si es permanente)."""
    if _cms_gap(exc):
        log.info("gee38: %s (CMS: %s)", message, exc)
    else:
        log.warning("gee38: %s (%s)", message, exc)

BASE_DEFAULT = "https://new.whatyouthink.site/"
BASE = os.environ.get("GEE38_BASE", BASE_DEFAULT).rstrip("/") + "/"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
IV = b"bkmlmKnk9i5Khs9n"  # constante de su APK (ver .visionplus/gee38_key.txt)

# Firebase Remote Config de su app V3.8: publica el dominio vigente del CMS.
RC_URL = (
    "https://firebaseremoteconfig.googleapis.com/v1/projects/723237950983"
    "/namespaces/firebase:fetch?key=AIzaSyDzfPo4ElrL-uZViIScGwfBnpJ-pADNJ5U"
)
RC_APP_ID = "1:723237950983:android:84dfd1ce9f6664417bfca2"
RC_PACKAGE = "com.live.geesports"

PROBE_CONCURRENCY = 24
PROBE_TIMEOUT = 6.0
PROBE_BYTES = 800

# Cuántos eventos reciben enlaces frescos por ciclo (priorizados por cercanía:
# hoy/mañana primero). Los demás conservan los enlaces del ciclo anterior.
# Con el catálogo actual (~220 eventos) el presupuesto los cubre a todos: cada
# partido que la app puede mostrar sale con su lista de enlaces recién bajada.
EVENTS_LINKS_MAX = 320

# Los enlaces de los partidos de la ventana (ayer..pasado mañana) se vuelven a
# descargar y sondear cada 5 min, entre los ciclos completos de 30 min. Así el
# selector nunca enseña un enlace de hace horas (el CMS los rota a menudo).
HOT_LINKS_SECONDS = int(os.environ.get("GEE38_HOT_SECONDS", "300") or "300")
HOT_LINKS_MAX = int(os.environ.get("GEE38_HOT_MAX", "60") or "60")

# La lista de fuentes vive en `link_names` de `events.txt` y el CMS la cambia durante
# el día. Se relee sola cada 2 min, sin esperar al ciclo completo ni al pase de enlaces.
META_REFRESH_SECONDS = max(60, int(os.environ.get("GEE38_META_SECONDS", "120") or "120"))


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
    env = _key_from_env()
    if env:
        return env
    apk = os.environ.get("GEE38_APK", "").strip()
    if apk and os.path.exists(apk):
        return derive_key_from_apk(apk)
    return None


def _key_from_env() -> bytes | None:
    env = os.environ.get("GEE38_KEY", "").strip()
    if not env:
        return None
    try:
        raw = bytes.fromhex(env)
        if len(raw) == 16:
            return raw
    except ValueError:
        pass
    if len(env) == 16:
        return env.encode()
    log.error("gee38: GEE38_KEY inválida (16 chars ascii o 32 hex)")
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
    """pro/<x>.txt descifrado → enlaces finales jugables.

    Cada enlace conserva su `slot` (índice en el archivo `pro`): la app original
    muestra los nombres del EVENTO (`link_names`) y al tocar la fila i resuelve
    `pro[i]`. Ese índice es la identidad real de la fuente, no el nombre que el
    CMS ponga en el archivo —que rota y a veces trae el de otro partido.
    """
    raw = json.loads(data)
    out = []
    for slot, item in enumerate(raw if isinstance(raw, list) else []):
        if not isinstance(item, dict):
            continue
        raw_link = str(item.get("link", "")).strip()
        token_api = str(item.get("tokenApi", "") or "").strip()
        if not raw_link and not token_api:
            continue
        url, headers = split_link(raw_link) if raw_link else ("", {})
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
            "slot": slot,                   # posición dentro de pro/<…>.txt
            # Los enlaces `embed` no traen URL: se resuelven al tocarlos en la app
            # (WebView), igual que la app original. No son «caídos».
            "token_api": token_api,
            "resolvable": bool(token_api) and not raw_link,
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


# ETag por archivo del CMS: el latido de metadatos pide `events.txt` cada 2 min,
# pero si no cambió el hosting responde 304 (sin cuerpo) y no gastamos los ~375 KB.
_ETAGS: dict[str, str] = {}

# Último conteo aceptado de `events.txt`: si el CMS devuelve una copia a medias
# (visto en vivo: 79 vs cientos) no se toma como buena hasta reintentar.
_META_EVENT_COUNT = 0


async def _fetch_conditional(path_or_url: str, timeout: int = 25) -> str | None:
    """Como `_fetch`, pero con `If-None-Match`; devuelve None si el CMS da 304."""

    def _go() -> str | None:
        url = _busted_url(path_or_url)
        headers = {
            "User-Agent": UA,
            "Accept": "*/*",
            "Cache-Control": "no-cache",
        }
        etag = _ETAGS.get(path_or_url)
        if etag:
            headers["If-None-Match"] = etag
        req = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                body = resp.read().decode("utf-8", "replace")
                fresh_etag = resp.headers.get("ETag")
                if fresh_etag:
                    _ETAGS[path_or_url] = fresh_etag
                return body
        except urllib.error.HTTPError as exc:
            if exc.code == 304:
                return None
            raise

    return await asyncio.to_thread(_go)


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
        # Las ranuras declaradas sin dirección no se sondean (no hay URL).
        if not _link_url(link):
            # Una ranura `embed` no tiene URL todavía: no se marca caída.
            if link.get("token_api"):
                link["alive"] = None
                link["checked_at"] = datetime.utcnow().isoformat(timespec="seconds")
            return
        async with sem:
            await _probe(link)

    await asyncio.gather(*(_one(lk) for lk in links), return_exceptions=True)


_ALIVE_ORDER = {True: 0, None: 1, False: 2}


def _sort_links(links: list[dict]) -> list[dict]:
    """Vivos primero, en duda en medio, caídos al final — conservando el orden de
    origen dentro de cada grupo (el CMS publica su mejor opción primero y los
    hermanos del mismo cruce entran por cercanía: la app toma el primero)."""
    return sorted(links, key=lambda lk: _ALIVE_ORDER.get(lk.get("alive"), 1))


def _clean_link_names(names) -> list[str]:
    out: list[str] = []
    for raw in names or []:
        clean = str(raw or "").strip()
        if clean:
            out.append(clean)
    return out


def _link_url(link: dict) -> str:
    return str(link.get("url") or link.get("link") or "").strip()


def _assign_link_slots(names: list[str], links: list[dict]) -> None:
    """Coloca cada enlace en su ranura de `link_names`.

    Manda el NOMBRE actual del evento (el CMS reordena/añade nombres y el `pro`
    cambia de índices): si un enlace guardado se llama como una ranura, se mueve
    ahí aunque su slot viejo fuera otro. Solo los enlaces sin nombre coincidente
    conservan su slot. Así una lista vieja de 8 fuentes no tapa una nueva de 12.
    """
    positions: dict[str, list[int]] = {}
    for index, name in enumerate(names):
        positions.setdefault(_norm_team(name), []).append(index)
    used: set[int] = set()
    matched: set[int] = set()
    for link in links:
        for index in positions.get(_norm_team(str(link.get("name") or "")), []):
            if index not in used:
                link["slot"] = index
                used.add(index)
                matched.add(id(link))
                break
    for link in links:
        if id(link) in matched:
            continue
        slot = link.get("slot")
        if isinstance(slot, int) and 0 <= slot < len(names) and slot not in used:
            used.add(slot)
        else:
            link.pop("slot", None)


def _pending_link(name: str, slot: int) -> dict:
    """Ranura declarada por la fuente que todavía no publica su dirección."""
    return {
        "name": name,
        "link": "",
        "url": "",
        "headers": {},
        "scheme": 0,
        "drm": "",
        "type": "hls",
        "alive": None,
        "checked_at": None,
        "pending": True,
        "slot": slot,
    }


def _merge_links_for_event(
    previous: list[dict],
    fresh: list[dict],
    names: list[str],
    cap: int = 32,
) -> list[dict]:
    """Une la lista nueva con la guardada y la deja COMPLETA como la app original.

    El CMS rota `pro/<…>.txt` y a veces lo publica a medias: la pasada caliente
    reemplazaba una lista rica (9 fuentes, varias vivas) por una copia parcial
    (2) y la app enseñaba menos que la original. Aquí manda la lista nueva cuando
    la ranura coincide, pero jamás se pierde una fuente guardada que siga viva.
    Los nombres del evento (`link_names`) se conservan todos: las ranuras que no
    tengan dirección quedan como pendientes y la app las muestra sin inventar.
    """
    names = _clean_link_names(names)
    old = [dict(link) for link in previous or [] if isinstance(link, dict)]
    new = [dict(link) for link in fresh or [] if isinstance(link, dict)]
    _assign_link_slots(names, old)
    _assign_link_slots(names, new)

    if not names:
        merged: list[dict] = []
        seen: set[str] = set()
        for link in new + old:
            url = _link_url(link)
            token = str(link.get("token_api") or "").strip()
            if not url and not token:
                continue
            key = url or ("embed:" + token)
            if key in seen:
                continue
            seen.add(key)
            merged.append(link)
        return _sort_links(merged)[:cap]

    slots: dict[int, tuple[dict, int]] = {}  # slot -> (enlace, 0=nuevo / 1=guardado)

    def rank(link: dict, slot: int, source: int) -> tuple:
        # Primero la identidad (nombre declarado para esa ranura); después la que
        # responde. Si el `pro` nuevo trae nombres de otro partido, gana la copia
        # guardada con el nombre correcto aunque el índice del archivo haya cambiado.
        name_ok = 0 if _norm_team(str(link.get("name") or "")) == _norm_team(names[slot]) else 1
        return (name_ok, _ALIVE_ORDER.get(link.get("alive"), 1), source)

    def add(link: dict, source: int) -> None:
        url = _link_url(link)
        if not url and not str(link.get("token_api") or "").strip():
            return
        slot = link.get("slot")
        if not isinstance(slot, int) or not (0 <= slot < len(names)):
            return  # fuera de los nombres declarados: la app original no lo enseña
        current = slots.get(slot)
        if current is None:
            slots[slot] = (link, source)
            return
        if rank(link, slot, source) < rank(current[0], slot, current[1]):
            # Misma fuente/ranura: se queda la mejor copia, no se duplica la fila.
            slots[slot] = (link, source)

    for link in new:
        add(link, 0)
    for link in old:
        add(link, 1)

    out: list[dict] = []
    for index, name in enumerate(names):
        current = slots.pop(index, None)
        if current is None:
            out.append(_pending_link(name, index))
            continue
        filled = dict(current[0])
        filled["name"] = name
        filled["slot"] = index
        filled.pop("pending", None)
        out.append(filled)
    return out[:cap]


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
                        _gap_log(f"canal {ch['name'][:30]} sin enlaces", exc)

                await asyncio.gather(*(_channel_links(ch) for ch in bundle))
                for ch in bundle:
                    ch_out = {k: v for k, v in ch.items() if k != "links_ref"}
                    ch_out["links"] = ch.get("links") or []
                    channels.append(ch_out)
                    links.extend(ch_out["links"])
        except Exception as exc:  # noqa: BLE001 - entrada retirada del CMS o red
            _gap_log(f"grupo {g['name']} no disponible", exc)

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


def _merge_event_pages(pages: list[list[dict]]) -> list[dict]:
    """Une varias lecturas de `events.txt` (el CMS a veces sirve copias a medias).

    Por cada partido se queda la entrada con el `links_ref` MÁS NUEVO (el nombre
    del `pro/*.txt` lleva el epoch en base64): una lectura parcial no debe pisar
    los enlaces frescos de otra. Si la nueva no trae `links_ref` se conserva la
    anterior.
    """
    best: dict[str, dict] = {}
    for page in pages:
        for ev in page:
            eid = _event_id(ev)
            prev = best.get(eid)
            if prev is None:
                best[eid] = ev
                continue
            prev_ref = str(prev.get("links_ref") or "").strip()
            new_ref = str(ev.get("links_ref") or "").strip()
            if new_ref and (
                not prev_ref or _api_timestamp(new_ref) >= _api_timestamp(prev_ref)
            ):
                best[eid] = ev
    return list(best.values())


async def _fetch_events_full(key: bytes, attempts: int = 4) -> list[dict]:
    """Lee `events.txt` varias veces y fusiona; el CMS publica copias parciales.

    Visto en vivo: 79 eventos cuando la copia buena trae cientos (y al rato vuelve
    a estar completa). No se acepta la primera lectura a medias: se reintenta y se
    unen las entradas, conservando el `links_ref` más nuevo de cada partido.
    """
    pages: list[list[dict]] = []
    for attempt in range(max(1, attempts)):
        try:
            pages.append(parse_events(decrypt_body(await _fetch_async("events.txt"), key)))
        except Exception as exc:  # noqa: BLE001 - la siguiente lectura puede salir bien
            _gap_log("events.txt no disponible", exc)
        if attempt < attempts - 1:
            await asyncio.sleep(2.0 + attempt)
    return _merge_event_pages(pages)


def _norm_team(text: str) -> str:
    """Nombre de equipo comparable: sin tildes, minúsculas, sin signos."""
    lowered = unicodedata.normalize("NFD", (text or "").lower())
    lowered = "".join(c for c in lowered if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", lowered).strip()


def _team_pair_key(team_a: str, team_b: str) -> str:
    """El cruce (par de equipos, sin importar el orden); vacío si no hay par real."""
    a, b = _norm_team(team_a), _norm_team(team_b)
    if not a or not b or a == b:
        return ""
    return "|".join(sorted((a, b)))


def _competition_pair_key(event: dict) -> str:
    """Cruce + competición: Liga MX varonil y femenil son partidos DISTINTOS.

    El CMS publica ambos con los mismos equipos y día; sin la competición, los
    enlaces de un Santos-Necaxa femenil contaminaban el Santos-Necaxa varonil
    (el Fox Deportes de la mujeril abría otro partido).
    """
    pair = _team_pair_key(event.get("team_a", ""), event.get("team_b", ""))
    if not pair:
        return ""
    competition = _norm_team(event.get("event") or "")
    return f"{competition}|{pair}" if competition else pair


def _day_distance(day: str, other: str) -> int:
    try:
        return abs((date.fromisoformat(day) - date.fromisoformat(other)).days)
    except ValueError:
        return 999


def _merge_sibling_links(
    own: list[dict],
    own_day: str,
    siblings: list[tuple[str, list[dict]]],
    cap: int = 18,
) -> list[dict]:
    """Une los enlaces del MISMO cruce (el CMS publica varias entradas por partido).

    Visto en vivo: la entrada «de hoy» de un partido traía UN enlace muerto
    (Everton-Chelsea 17-oct) mientras su hermana viva/visible (13-oct) traía la
    lista completa con 6 vivos — la app original enseña esa lista. Aquí se fusionan:
    primero los propios (el día correcto), luego los hermanos por cercanía de fecha
    y cantidad de vivos; sin duplicados por URL, listos para el selector.
    """
    best: dict[str, dict] = {}

    def add(link: dict) -> None:
        url = str(link.get("url") or link.get("link") or "").strip()
        token = str(link.get("token_api") or "").strip()
        if not url and not token:
            return
        key = url or ("embed:" + token)
        prev = best.get(key)
        # El propio manda; un hermano solo pisa si está vivo y el propio no.
        if prev is None or (link.get("alive") and not prev.get("alive")):
            best[key] = link

    for link in own:
        add(link)
    ordered = sorted(
        siblings,
        key=lambda item: (
            _day_distance(item[0], own_day),
            -sum(1 for lk in item[1] if lk.get("alive")),
        ),
    )
    for _day, links in ordered:
        if _day_distance(_day, own_day) > 120:
            continue  # una revancha de otra temporada no comparte transmisión
        for link in links:
            add(link)
    return _sort_links(list(best.values()))[:cap]


async def _sync_events(pool: asyncpg.Pool, key: bytes, meta: dict) -> int:
    events = await _fetch_events_full(key)
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
    # Hermanos del mismo cruce (mismo par de equipos, cualquier fecha): con sus
    # enlaces guardados se completa la lista del partido cuando su propia entrada
    # viene pobre (un enlace muerto) y la buena vive en otra entrada.
    pair_links: dict[str, list[tuple[str, list[dict]]]] = {}
    for value in old.values():
        pair = _competition_pair_key(value)
        if not pair:
            continue
        stored = [lk for lk in (value.get("links") or []) if isinstance(lk, dict)]
        if stored:
            pair_links.setdefault(pair, []).append((str(value.get("date_iso") or ""), stored))
    for ev in sorted(events, key=lambda e: _event_rank(e, today)):
        eid = _event_id(ev)
        seen_ids.append(eid)
        previous_links = [
            lk for lk in ((old.get(eid) or {}).get("links") or []) if isinstance(lk, dict)
        ]
        links: list[dict] = []
        fetched = False
        wanted = ev["visible"] or (ev["date_iso"] in window_str)
        if ev["links_ref"] and wanted and fresh_budget > 0:
            try:
                links = parse_pro(decrypt_body(await _fetch_async(ev["links_ref"]), key))
                fresh_budget -= 1
                fetched = True
            except Exception as exc:  # noqa: BLE001
                _gap_log(f"evento {ev['event'][:40]} sin enlaces", exc)
        if not fetched:
            # Sin presupuesto este ciclo: conserva los enlaces del ciclo anterior.
            links = list(previous_links)
        pair = _competition_pair_key(ev)
        if pair and pair_links.get(pair):
            links = _merge_sibling_links(links, str(ev.get("date_iso") or ""), pair_links[pair])
        # La lista nueva puede venir parcial (visto en vivo: 9 fuentes guardadas y
        # el CMS publica un `pro` de 2): nunca se pierde una fuente anterior viva y
        # `link_names` queda completo, como en la app original.
        links = _merge_links_for_event(previous_links, links, ev.get("link_names") or [])
        # El sondeo va sobre la lista FINAL (hermanos incluidos): los badges de
        # «EN VIVO / Caído» que ve el usuario son del momento, no de hace días.
        if links and ev["date_iso"] in window_str:
            await _probe_many(links)
            links = _sort_links(links)
        payload = {k: v for k, v in ev.items() if k != "idx"}
        # El id estable VIAJA en el payload: la app lo usa como clave de lista (LazyColumn).
        # Sin él, el respaldo por nombre|fecha|hora colisionaba entre partidos distintos
        # del mismo bloque («NBA Preseason» 20:00) y tumbaba la pantalla (visto en vivo).
        payload["id"] = eid
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
    # Segunda pasada: eventos de la ventana que NO vinieron en este ciclo (el CMS rota
    # sus archivos: una entrada puede desaparecer un rato y volver). Se re-fusionan sus
    # hermanos y se sondean sus enlaces para que los badges «EN VIVO / Caído» que ve el
    # usuario sigan siendo del momento (y la tarjeta no quede con un solo enlace muerto).
    seen = set(seen_ids)
    extra_budget = 80
    for eid, value in old.items():
        if extra_budget <= 0:
            break
        if eid in seen:
            continue
        day = str(value.get("date_iso") or "")
        if day not in window_str:
            continue
        links = [lk for lk in (value.get("links") or []) if isinstance(lk, dict)]
        previous_links = list(links)
        # La entrada rotó fuera de events.txt (el CMS publica a medias y va rotando), pero su
        # archivo `pro/<…>.txt` puede seguir publicado CON MÁS enlaces que los guardados: se
        # RE-DESCARGA para que la lista del partido no se quede congelada. Visto en vivo
        # (Chivas-Tigres, 17-oct): la fila llevaba horas con 2 enlaces mientras el CMS ya
        # servía 3 (faltaba «LOW QUALITY (USE VPN)») — la app enseñaba menos que la original.
        ref = str(value.get("links_ref") or "").strip()
        if ref and extra_budget > 0:
            try:
                fresh = parse_pro(decrypt_body(await _fetch_async(ref), key))
                if fresh:
                    links = fresh
                    extra_budget -= 1
            except Exception as exc:  # noqa: BLE001 - archivo retirado: se conserva lo guardado
                log.warning(
                    "gee38: %s sin refrescar enlaces (%s)",
                    str(value.get("event") or "")[:40], exc,
                )
        # Aunque la entrada venga vacía, sus hermanos del mismo cruce la completan:
        # primero se fusiona y luego se sondea el conjunto final.
        pair = _competition_pair_key(value)
        if pair and pair_links.get(pair):
            links = _merge_sibling_links(links, day, pair_links[pair])
        links = _merge_links_for_event(previous_links, links, value.get("link_names") or [])
        if not any(_link_url(lk) for lk in links):
            continue
        await _probe_many(links)
        links = _sort_links(links)
        value["links"] = links
        value["alive"] = sum(1 for lk in links if lk.get("alive"))
        value["total"] = len(links)
        extra_budget -= 1
        async with pool.acquire() as conn:
            await conn.execute(
                "update gee38_catalog set payload = $2::jsonb, updated_at = now() where id = $1",
                eid, json.dumps(value, ensure_ascii=False),
            )
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


# ----------------------------------------------------------------------------
# Autonomía: dominio (Firebase RC) y clave (APK) se resuelven solos
# ----------------------------------------------------------------------------

def _rc_api_url(payload: dict) -> str:
    """El dominio vigente del CMS tal como lo publica su Firebase Remote Config."""
    entries = payload.get("entries") if isinstance(payload, dict) else None
    url = (entries or {}).get("api_url") if isinstance(entries, dict) else ""
    url = str(url or "").strip()
    return url if url.startswith("http") else ""


async def _meta_get(pool: asyncpg.Pool, key: str):
    async with pool.acquire() as conn:
        row = await conn.fetchrow("select v from gee38_meta where k = $1", key)
    if row is None:
        return None
    value = row["v"]
    if isinstance(value, str):
        try:
            return json.loads(value)
        except Exception:  # noqa: BLE001
            return value
    return value


async def _meta_set(pool: asyncpg.Pool, key: str, value) -> None:
    async with pool.acquire() as conn:
        await conn.execute(
            """
            insert into gee38_meta (k, v, updated_at) values ($1, $2::jsonb, now())
            on conflict (k) do update set v = excluded.v, updated_at = now()
            """,
            key,
            json.dumps(value, ensure_ascii=False),
        )


async def _refresh_base(pool: asyncpg.Pool) -> None:
    """Si rotan el dominio del CMS, su Remote Config lo publica: se descubre solo."""
    global BASE
    stored = await _meta_get(pool, "base")
    if isinstance(stored, str) and stored.startswith("http"):
        BASE = stored.rstrip("/") + "/"
    try:
        body = json.dumps({
            "app_instance_id": uuid.uuid4().hex,
            "app_id": RC_APP_ID,
            "package_name": RC_PACKAGE,
        }).encode()

        def _go() -> bytes:
            req = urllib.request.Request(RC_URL, data=body, headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                return resp.read()

        raw = await asyncio.to_thread(_go)
        url = _rc_api_url(json.loads(raw.decode("utf-8", "replace")))
        if url:
            url = url.rstrip("/") + "/"
            if url != BASE:
                log.info("gee38: el dominio rotó a %s (Firebase RC)", url)
                BASE = url
            await _meta_set(pool, "base", BASE)
    except Exception as exc:  # noqa: BLE001 - sin RC seguimos con el dominio guardado
        log.warning("gee38: Firebase RC no disponible (%s); sigo con %s", exc, BASE)


def _download_file(url: str, path: str) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as resp, open(path, "wb") as out:
        out.write(resp.read())


async def _key_works(key: bytes) -> bool:
    try:
        decrypt_body(await _fetch_async("app.txt"), key)
        return True
    except Exception:  # noqa: BLE001
        return False


async def _resolve_key_auto(pool: asyncpg.Pool) -> bytes | None:
    """La clave, sola: entorno → guardada → re-derivada del APK si rotó el certificado."""
    candidates: list[bytes] = []
    env = _key_from_env()
    if env:
        candidates.append(env)
    stored = await _meta_get(pool, "key")
    if isinstance(stored, str) and stored:
        try:
            candidates.append(base64.b64decode(stored))
        except Exception:  # noqa: BLE001
            pass
    for key in candidates:
        if await _key_works(key):
            await _meta_set(pool, "key", base64.b64encode(key).decode())
            return key
    app = await _meta_get(pool, "app") or {}
    download_url = str(app.get("download_url") or "").strip()
    if download_url.startswith("http"):
        path = "/tmp/gee38_update.apk"
        try:
            await asyncio.to_thread(_download_file, download_url, path)
            derived = derive_key_from_apk(path)
            if derived and await _key_works(derived):
                log.info("gee38: el certificado rotó — clave re-derivada del APK nuevo")
                await _meta_set(pool, "key", base64.b64encode(derived).decode())
                return derived
        except Exception as exc:  # noqa: BLE001
            log.warning("gee38: no se pudo re-derivar la clave del APK (%s)", exc)
    return None


async def _refresh_event_metadata(pool: asyncpg.Pool, key: bytes) -> int:
    """Actualiza `link_names`/`links_ref` de los eventos con el `events.txt` actual.

    La app original lee la lista de canales del EVENTO (`link_names`), no del
    archivo `pro`. El CMS cambia esa lista durante el día (visto en vivo:
    Man City-Brighton pasó de 8 a 12 nombres) mientras nuestro ciclo completo
    solo corría cada 30 min. El pase caliente refresca los metadatos para que la
    lista no se quede congelada aunque los enlaces se renueven cada 5 min.
    """
    global _META_EVENT_COUNT
    try:
        body = await _fetch_conditional("events.txt")
    except Exception as exc:  # noqa: BLE001 - sin metadatos frescos se conserva lo guardado
        _gap_log("events.txt para metadatos del pase caliente", exc)
        return 0
    if body is None:
        # 304: el archivo no cambió; el latido cuenta como fresco sin bajar 375 KB.
        try:
            await _meta_set(
                pool, "meta_at", {"at": datetime.utcnow().isoformat(timespec="seconds")}
            )
        except Exception:
            log.exception("gee38: no se pudo sellar meta_at (304)")
        return 0
    try:
        events = parse_events(decrypt_body(body, key))
    except Exception as exc:  # noqa: BLE001
        _gap_log("events.txt para metadatos del pase caliente", exc)
        return 0
    if _META_EVENT_COUNT >= 80 and len(events) < _META_EVENT_COUNT * 0.5:
        log.warning(
            "gee38: events.txt vino a medias en metadatos (%s vs %s); reintento",
            len(events), _META_EVENT_COUNT,
        )
        try:
            fallback = await _fetch_events_full(key, attempts=3)
            if len(fallback) > len(events):
                events = fallback
        except Exception as exc:  # noqa: BLE001
            _gap_log("events.txt incompleto en metadatos", exc)
    if not events:
        return 0
    _META_EVENT_COUNT = len(events)
    by_id = {_event_id(ev): ev for ev in events}
    if not by_id:
        return 0
    today = date.today()
    low = (today - timedelta(days=7)).isoformat()
    high = (today + timedelta(days=2)).isoformat()
    try:
        rows = await pool.fetch(
            """
            select id, payload from gee38_catalog
             where kind = 'event'
               and coalesce(payload->>'date_iso', '') between $1 and $2
            """,
            low,
            high,
        )
    except Exception:
        log.exception("gee38: no se pudieron leer los eventos para refrescar metadatos")
        return 0
    updated = 0
    links_budget = 30
    for row in rows:
        value = row["payload"]
        if isinstance(value, str):
            try:
                value = json.loads(value)
            except json.JSONDecodeError:
                continue
        if not isinstance(value, dict):
            continue
        current = by_id.get(row["id"])
        if current is None:
            continue
        names = _clean_link_names(current.get("link_names") or [])
        ref = str(current.get("links_ref") or "").strip()
        changed = False
        if names and value.get("link_names") != names:
            value["link_names"] = names
            changed = True
        if ref and value.get("links_ref") != ref:
            value["links_ref"] = ref
            changed = True
        if not changed:
            continue
        previous_links = [lk for lk in (value.get("links") or []) if isinstance(lk, dict)]
        fresh: list[dict] = []
        if ref and links_budget > 0:
            try:
                fresh = parse_pro(decrypt_body(await _fetch_async(ref), key))
                links_budget -= 1
            except Exception as exc:  # noqa: BLE001 - se conservan los guardados
                _gap_log(f"metadatos {str(value.get('event') or '')[:40]} sin enlaces", exc)
        links = _merge_links_for_event(previous_links, fresh, names)
        if links:
            await _probe_many(links)
            links = _sort_links(links)
            value["links"] = links
            value["alive"] = sum(1 for lk in links if lk.get("alive"))
            value["total"] = len(links)
        try:
            await pool.execute(
                "update gee38_catalog set payload = $2::jsonb, updated_at = now() where id = $1",
                row["id"],
                json.dumps(value, ensure_ascii=False),
            )
            updated += 1
        except Exception:
            log.exception("gee38: no se pudo guardar metadatos de %s", row["id"])
    try:
        await _meta_set(
            pool, "meta_at", {"at": datetime.utcnow().isoformat(timespec="seconds")}
        )
    except Exception:
        log.exception("gee38: no se pudo sellar meta_at")
    return updated


async def _refresh_hot_links(pool: asyncpg.Pool, key: bytes) -> int:
    """Re-descarga y sondea los enlaces de los partidos de la ventana (cada 5 min).

    El ciclo completo (30 min) puede quedarse con un `links_ref` viejo si el CMS
    publica `events.txt` a medias; este pase usa el `links_ref` guardado de cada
    partido, baja su `pro/*.txt` actual y vuelve a sondear. Es el que mantiene
    frescos los enlaces que abre el selector. Los partidos de HOY entran siempre
    primero; el resto se ordena por el `checked_at` más viejo. La ventana llega una
    semana atrás porque el API cruza enlaces de entradas hermanas del mismo partido
    (p. ej. el `D SPORTS2` de Instituto venía de una entrada del 22-oct).
    """
    today = date.today()
    low = (today - timedelta(days=7)).isoformat()
    high = (today + timedelta(days=2)).isoformat()
    try:
        rows = await pool.fetch(
            """
            select id, payload from gee38_catalog
             where kind = 'event'
               and coalesce(payload->>'date_iso', '') between $1 and $2
               and jsonb_array_length(coalesce(payload->'links', '[]'::jsonb)) > 0
             order by (
                 jsonb_array_length(coalesce(payload->'link_names', '[]'::jsonb))
                 > jsonb_array_length(coalesce(payload->'links', '[]'::jsonb))
             ) desc,
             (coalesce(payload->>'date_iso', '') = $3) desc,
             (
                 select min(coalesce(lk->>'checked_at', ''))
                   from jsonb_array_elements(
                       coalesce(payload->'links', '[]'::jsonb)
                   ) as lk
             ) asc nulls first,
             updated_at asc
             limit $4
            """,
            low,
            high,
            today.isoformat(),
            max(1, HOT_LINKS_MAX),
        )
    except Exception:
        log.exception("gee38: no se pudieron leer los eventos calientes")
        return 0
    updated = 0
    for row in rows:
        value = row["payload"]
        if isinstance(value, str):
            try:
                value = json.loads(value)
            except json.JSONDecodeError:
                continue
        if not isinstance(value, dict):
            continue
        ref = str(value.get("links_ref") or "").strip()
        links = [lk for lk in (value.get("links") or []) if isinstance(lk, dict)]
        previous_links = list(links)
        if ref:
            try:
                fresh = parse_pro(decrypt_body(await _fetch_async(ref), key))
                if fresh:
                    # Nunca reemplazar una lista rica por una copia parcial del CMS.
                    links = _merge_links_for_event(
                        previous_links, fresh, value.get("link_names") or []
                    )
            except Exception as exc:  # noqa: BLE001 - se conservan los guardados
                _gap_log(f"caliente {str(value.get('event') or '')[:40]} sin enlaces", exc)
        if not any(_link_url(lk) for lk in links):
            continue
        await _probe_many(links)
        links = _sort_links(links)
        value["links"] = links
        value["alive"] = sum(1 for lk in links if lk.get("alive"))
        value["total"] = len(links)
        try:
            await pool.execute(
                "update gee38_catalog set payload = $2::jsonb, updated_at = now() where id = $1",
                row["id"],
                json.dumps(value, ensure_ascii=False),
            )
            updated += 1
        except Exception:
            log.exception("gee38: no se pudo guardar el evento caliente %s", row["id"])
    try:
        await _meta_set(
            pool, "hot_at", {"at": datetime.utcnow().isoformat(timespec="seconds")}
        )
    except Exception:
        log.exception("gee38: no se pudo sellar hot_at")
    if updated:
        log.info("gee38: %s eventos con enlaces frescos", updated)
    return updated


async def sync(pool: asyncpg.Pool) -> int:
    await _refresh_base(pool)
    key = await _resolve_key_auto(pool)
    if not key:
        log.warning("gee38: sin clave utilizable (entorno/guardada/APK); se omite el ciclo")
        return 0
    app = parse_app(decrypt_body(await _fetch_async("app.txt"), key))
    await _meta_set(pool, "app", app)
    groups = await _sync_groups(pool, key, app)
    events = await _sync_events(pool, key, app)
    try:
        await _meta_set(
            pool, "full_at", {"at": datetime.utcnow().isoformat(timespec="seconds")}
        )
    except Exception:
        log.exception("gee38: no se pudo sellar full_at")
    log.info("gee38: %s grupos + %s eventos actualizados (v%s)", groups, events, app.get("app_version"))
    return groups + events


async def loop(pool: asyncpg.Pool) -> None:
    """Tareas de fondo: ciclo completo, metadatos cada 2 min y enlaces cada 5 min.

    El usuario ve la lista de fuentes de `events.txt` (`link_names`), así que los
    metadatos tienen su propio latido corto; el ciclo completo (catálogo/grupos) y
    el sondeo de enlaces pueden seguir con sus intervalos largos sin recortar la
    lista de ningún evento.
    """
    if os.environ.get("GEE38_SYNC", "1") != "1":
        log.info("gee38: deshabilitado por entorno")
        return
    interval = max(300, int(os.environ.get("GEE38_INTERVAL_SECONDS", "1800") or "1800"))
    hot = max(60, HOT_LINKS_SECONDS)
    meta = META_REFRESH_SECONDS
    log.info(
        "gee38: ciclo completo cada %s s; metadatos cada %s s; enlaces calientes cada %s s",
        interval, meta, hot,
    )
    next_full = time.monotonic()
    next_meta = time.monotonic() + meta
    while True:
        now = time.monotonic()
        stored = await _meta_get(pool, "key")
        key = base64.b64decode(stored) if isinstance(stored, str) and stored else None
        if key and now >= next_meta:
            try:
                updated = await _refresh_event_metadata(pool, key)
                if updated:
                    log.info("gee38: %s eventos con metadatos frescos", updated)
            except Exception:  # noqa: BLE001 - no debe tumbar el ciclo completo
                log.exception("gee38: falló el refresco de metadatos")
            next_meta = time.monotonic() + meta
        if now >= next_full:
            try:
                await sync(pool)
                next_full = time.monotonic() + interval
                next_meta = time.monotonic() + meta
            except Exception:  # noqa: BLE001 - se reintenta pronto
                log.exception("gee38: el ciclo falló")
                next_full = time.monotonic() + 300
        elif key:
            try:
                await _refresh_hot_links(pool, key)
            except Exception:  # noqa: BLE001 - no debe tumbar el ciclo completo
                log.exception("gee38: falló el refresco caliente")
        wait = max(15.0, min(float(meta), float(hot), max(30.0, next_full - time.monotonic())))
        await asyncio.sleep(wait)
