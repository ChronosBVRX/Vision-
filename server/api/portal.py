"""Búsqueda en el portal Xuper (la misma que usa la app original) + alta de títulos.

Por qué existe: el índice propio se arma con los LISTADOS del portal, y esos listados
topan en 10.000 títulos por columna (`portal100081`) y en pocas páginas por colección.
Medido en vivo el 2026-10-18: «Guerra de Papás» vive en Comedia página 73 y «El
padrino» (1972) en Crimen página 35 — el índice no los tenía y buscar «guerra de
papas» devolvía «Juego de tronos» (el fuzzy agarraba el «A Guerra dos Tronos» del
título portugués de TMDB). La app original SÍ los encuentra porque su buscador va
directo al portal (`v3/searchByName`).

Este módulo replica esa llamada con la sesión que el worker ya mantiene
(`portal_sessions`). Reglas de oro:
- **Jamás acuña sesión**: si el token no sirve, se devuelve vacío y la búsqueda sigue
  con el índice local (acuñar desde aquí patearía a los aparatos).
- **Los títulos encontrados se dan de alta** (`on conflict do nothing`): el catálogo
  crece solo con lo que la gente busca, sin sobrescribir lo ya enriquecido.
- Tope de tiempo corto y caché por consulta: el buscador no puede quedarse esperando
  al portal.

La crypto del wire es la misma del worker (`wire.py`, copiado tal cual; un test
compara ambos archivos para que no se separen).
"""

import asyncio
import json
import ssl
import time
import unicodedata
import urllib.error
import urllib.request
from datetime import datetime, timezone

from wire import wire_decrypt, wire_encrypt, XTV_KEY

PORTAL = "masnew"
APP_ID = "com.android.msandroid"
APK_VER = "43404"
SPKG = "2025-03-11 02:26:11_33_13_4.14.186-27270957"
HOSTS = ["fuxok.nguvmqhpk.com", "mptec.dhkrxuzcy.com"]
# El campo `sn` del sobre no participa en la validación: el token manda. Se usa el SN
# histórico para que el sobre quede idéntico al del worker.
LEGACY_SN = "2392fe5186d83d73508a2c8c60a26ef6"
LEGACY_USER_ID = "967111326"

REQUEST_TIMEOUT = 5
TOTAL_TIMEOUT = 4.0
CACHE_TTL = 600.0
ERROR_TTL = 60.0
MAX_PAGE_SIZE = 40

# Palabras que no distinguen un título de otro: sin esto, «una pelicula que no
# existe» conservaba «Una jirafa en el balcón» (comparten «una») y el buscador
# difuso del portal «encontraba» cualquier cosa. Se comparan ya plegadas.
STOPWORDS = frozenset("""
a al algo algun alguna algunas alguno algunos ante antes con contra cual cuando
de del desde donde dos el ella ellas ellos en entre era es esa esas ese eso esos
esta estas este esto estos fue ha hasta hay la las le les lo los mas me mi mis
mucho muy nada ni no nos o os otra otro para pero poco por porque que quien se
sea segun ser si sin sobre son su sus te tener ti tiene todo todos tu tus un una
uno unos y ya
the of and to in on for with from a an at by is it as or
""".split())


_TABLE_COLUMNS = """
    t.id, t.title, t.year, t.duration_min, t.synopsis,
    t.poster_url, t.play_ref, t.source, t.score, t.kind,
    t.popularity, t.vote_count
"""

_INSERT_SQL = """
insert into titles (
    id, source, source_id, kind, title, original_title, year, synopsis,
    poster_url, duration_min, score, genres, cast_list, director, play_ref,
    is_premiere, is_recommended, program_type, popularity, vote_count,
    search_text, search_text_at
) values (
    $1, $2, $3, $4, $5, $6, $7, $8,
    $9, $10, $11, $12, $13, $14, $15,
    $16, $17, $18, $19, $20,
    $21, now()
)
on conflict do nothing
"""

# consulta -> (expira_en, items). Los fallos también se cachean (TTL corto) para no
# martillar el portal cuando la sesión está caída.
_CACHE: dict[str, tuple[float, list[dict]]] = {}
_SSL = ssl.create_default_context()
_SSL.check_hostname = False
_SSL.verify_mode = ssl.CERT_NONE


def fold_name(text: str) -> str:
    """Pliegue idéntico a `api/app.py::_fold` y `sync/search_text.py::fold`."""
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return " ".join("".join(c if c.isalnum() else " " for c in folded).split())


def clean_name(text: str) -> str:
    """Nombre para mostrar: guiones bajos → espacios (el portal rotula «T1_11»)."""
    return " ".join((text or "").replace("_", " ").split())


def _compact(folded: str) -> str:
    compact = folded.replace(" ", "")
    return compact if compact != folded and len(compact) >= 4 else ""


def build_search_text(title: str, alias: str = "") -> str:
    """Texto materializado de búsqueda con lo que trae el portal (título + alias)."""
    parts: list[str] = []
    for name in (title, alias):
        folded = fold_name(name)
        if not folded:
            continue
        parts.append(folded)
        compact = _compact(folded)
        if compact:
            parts.append(compact)
    return " ".join(dict.fromkeys(parts))[:1200]


def _year(release: str | None) -> int | None:
    text = (release or "").strip()
    if len(text) >= 4 and text[:4].isdigit():
        year = int(text[:4])
        if 1900 <= year <= datetime.now(timezone.utc).year + 1:
            return year
    return None


def _duration(text: str | None) -> int:
    """Minutos a partir de «HH:MM:SS» o «90 min» (el buscador casi siempre viene vacío)."""
    raw = (text or "").strip()
    if not raw:
        return 0
    if ":" in raw:
        parts = raw.split(":")
        try:
            nums = [int(p) for p in parts]
        except ValueError:
            return 0
        if len(nums) == 3:
            return nums[0] * 60 + nums[1] + (1 if nums[2] >= 30 else 0)
        if len(nums) == 2:
            return nums[0] + (1 if nums[1] >= 30 else 0)
        return 0
    digits = "".join(c for c in raw if c.isdigit())
    return int(digits) if digits else 0


def _score(value) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if 0 <= number <= 10 else None


def _poster(posters) -> str:
    """Mismo criterio que el worker: póster, luego icono, luego stage."""
    by_kind = {}
    for p in posters or []:
        if isinstance(p, dict) and (p.get("fileUrl") or "").startswith("http"):
            by_kind.setdefault((p.get("fileType") or "").lower(), p["fileUrl"])
    for kind in ("poster", "icon", "stage"):
        if kind in by_kind:
            return by_kind[kind]
    return next(iter(by_kind.values()), "") if by_kind else ""


def item_kind(item: dict) -> str:
    """Tipo de contenido del portal → `kind` del índice."""
    program_type = (item.get("programType") or "").strip().lower()
    if program_type in ("movie", "short"):
        return "movie"
    if program_type in ("teleplay", "series", "tv"):
        return "series"
    if program_type == "anime":
        return "anime"
    if program_type == "kids":
        return "kids"
    # Respaldo: en el buscador `type` 1 = película y 0 = serie/teleplay.
    return "movie" if str(item.get("type") or "") == "1" else "series"


def item_row(item: dict) -> tuple | None:
    """Fila para `titles` desde un `itemList[]` de `v3/searchByName` (None si no hay id)."""
    content_id = (item.get("contentId") or "").strip()
    name = clean_name(item.get("name") or "")
    if not content_id or not name:
        return None
    alias = clean_name(item.get("alias") or "")
    year = _year(item.get("releaseTime"))
    program_type = (item.get("programType") or "").strip().lower()
    now_year = datetime.now(timezone.utc).year
    genres = [g.strip() for g in (item.get("tags") or "").split(",") if g.strip()]
    return (
        f"xtv:{content_id}", "xtv", content_id, item_kind(item), name,
        alias or None, year, (item.get("description") or "").strip(),
        _poster(item.get("posterList")), _duration(item.get("duration")),
        _score(item.get("score")), genres, (item.get("actorDisplay") or "").strip(),
        (item.get("director") or "").strip(), f"xtv:{content_id}",
        bool(year and year >= now_year - 1), False, program_type, 0.0, 0,
        build_search_text(name, alias),
    )


def flatten(data: dict) -> list[dict]:
    """Los resultados vienen agrupados (`searchItemList[].itemList[]`): se aplana."""
    items: list[dict] = []
    seen: set[str] = set()
    for group in data.get("searchItemList") or []:
        for item in group.get("itemList") or []:
            cid = (item.get("contentId") or "").strip()
            if not cid or cid in seen:
                continue
            seen.add(cid)
            items.append(item)
    return items


def keep_items(items: list[dict], query: str, keep_first: int = 5, cap: int = 20) -> list[dict]:
    """Recorta el ruido del portal sin perder el título correcto.

    El buscador del portal es difuso (264 resultados para «guerra de papas», con
    Juan Luis Guerra incluido) y para una consulta sin sentido devuelve cualquier
    cosa. Se conservan los primeros (`keep_first`) solo si comparten alguna palabra
    con la consulta, y del resto solo los que contienen TODAS las palabras en el
    nombre o el alias. Sin palabras significativas (p. ej. «E.T.») se confía en el
    orden del portal.
    """
    words = [w for w in fold_name(query).split() if len(w) >= 3 and w not in STOPWORDS]
    if not words:
        return items[:cap]
    kept: list[dict] = []
    for i, item in enumerate(items):
        text = f"{fold_name(item.get('name') or '')} {fold_name(item.get('alias') or '')}"
        if (i < keep_first and any(w in text for w in words)) or all(w in text for w in words):
            kept.append(item)
        if len(kept) >= cap:
            break
    return kept


def _request(token: str, user_id: str, value: str, page_size: int) -> list[dict]:
    body = {
        "apkVersion": APK_VER, "appId": APP_ID, "appLanguage": "es",
        "cpu": "arm64-v8a", "deviceToken": "", "hardwareInfo": "mt6769t",
        "loginType": "3", "model": "SM-A325M", "product": "a32ub",
        "reserve1": "000000000000", "sn": LEGACY_SN, "sysVersion": "13",
        "userToken": token, "userId": user_id, "portalCode": PORTAL,
        "columnId": "", "value": value, "type": "1",
        "pageSize": page_size, "pageNum": 1, "filter": "",
    }
    wire = wire_encrypt(json.dumps(body, separators=(",", ":"), ensure_ascii=False), XTV_KEY)
    last: Exception | None = None
    for host in HOSTS:
        request = urllib.request.Request(
            f"https://{host}/api/portalCore/v3/searchByName",
            data=wire.encode("ascii"), method="POST",
        )
        request.add_header("Content-Type", "application/json; charset=utf-8")
        request.add_header("apk", APP_ID)
        request.add_header("apkVer", APK_VER)
        request.add_header("spkgVer", SPKG)
        request.add_header("User-Agent", "okhttp/4.12.0")
        request.add_header("Cache-Control", "no-store")
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT, context=_SSL) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            if payload.get("returnCode") != "0":
                raise RuntimeError(f"portal {payload.get('returnCode')} {payload.get('errorMessage')}")
            encrypted = payload.get("data")
            if not encrypted:
                return []
            return flatten(json.loads(wire_decrypt(encrypted, XTV_KEY)))
        except Exception as exc:  # noqa: BLE001 - se prueba el host de respaldo
            last = exc
    raise RuntimeError(f"searchByName sin host disponible: {last}")


async def search(pool, query: str, page_size: int = MAX_PAGE_SIZE) -> list[dict]:
    """Resultados del portal (cacheados). Nunca lanza: sin sesión/red devuelve []."""
    key = f"{fold_name(query)}:{page_size}"
    now_ts = time.time()
    cached = _CACHE.get(key)
    if cached and now_ts < cached[0]:
        return cached[1]
    token, user_id = "", ""
    try:
        row = await pool.fetchrow(
            """
            select (select user_token from portal_sessions where portal = 'xtv') as token,
                   (select user_id from device_slots where status = 'worker' limit 1) as user_id
            """
        )
        token = ((row["token"] if row else "") or "").strip()
        user_id = ((row["user_id"] if row else "") or "").strip()
    except Exception:  # noqa: BLE001 - tabla ausente = sin portal
        try:
            row = await pool.fetchrow("select user_token from portal_sessions where portal = 'xtv'")
            token = ((row["user_token"] if row else "") or "").strip()
        except Exception:  # noqa: BLE001
            token = ""
    if not token:
        _CACHE[key] = (now_ts + ERROR_TTL, [])
        return []
    try:
        items = await asyncio.wait_for(
            asyncio.to_thread(_request, token, user_id or LEGACY_USER_ID, query, page_size),
            timeout=TOTAL_TIMEOUT,
        )
        items = keep_items(items, query)
        _CACHE[key] = (now_ts + CACHE_TTL, items)
        return items
    except Exception:  # noqa: BLE001 - el buscador sigue con el índice local
        _CACHE[key] = (now_ts + ERROR_TTL, [])
        return []


async def store(pool, items: list[dict]) -> list[str]:
    """Da de alta los títulos del portal (sin pisar lo existente) y devuelve sus ids."""
    rows = [row for row in (item_row(it) for it in items) if row]
    if not rows:
        return []
    try:
        async with pool.acquire() as conn:
            await conn.executemany(_INSERT_SQL, rows)
    except Exception:  # noqa: BLE001 - si la base falla, aún se pueden servir
        pass
    return [row[0] for row in rows]


async def rows_for(pool, ids: list[str]) -> list:
    """Filas del índice en el orden del portal (solo las que quedaron guardadas)."""
    if not ids:
        return []
    try:
        rows = await pool.fetch(
            f"select {_TABLE_COLUMNS} from titles t where t.id = any($1::text[])", ids
        )
    except Exception:  # noqa: BLE001
        return []
    by_id = {r["id"]: r for r in rows}
    return [by_id[i] for i in ids if i in by_id]


async def lookup(pool, query: str) -> list:
    """Búsqueda + alta + filas listas para el endpoint (en orden del portal)."""
    items = await search(pool, query)
    if not items:
        return []
    ids = await store(pool, items)
    return await rows_for(pool, ids)
