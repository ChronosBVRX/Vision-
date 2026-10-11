"""Conector XTV (portal "masnew"): el catálogo completo de películas/series/kids/anime.

Flujo verificado en vivo (2026-10-09):
  1. v3/snToken -> v8/active                       (sesión ligada al SN)
  2. v3/getColumnContents (raíz y por columna)     (árbol de categorías propias)
  3. v3/getShelveData {columnType:"2", pageNum}    (assets paginados, 100/página)
       - "All" de cada sección = catálogo completo (Movies: 31.099 títulos)
  4. getNextColumns(masnew_featured)               (repisas de recomendados)
  5. v6/getLiveData                                (canales en vivo, ~1000)

La reproducción NO se toca: los títulos guardan el ref `xtv:<contentId>` y la
app resuelve la licencia en el dispositivo, como siempre.
"""

import asyncio
import json
import logging
import os
import ssl
import unicodedata
import urllib.error
import urllib.request
from datetime import datetime

from wire import XTV_KEY, wire_decrypt, wire_encrypt

log = logging.getLogger("xtv")

SN = "2392fe5186d83d73508a2c8c60a26ef6"
# SN propio del servidor (opcional): con su propia identidad, el worker deja de
# pelear la sesión con las apps (mismo SN = patadas aaa100083 de un lado al otro).
# Vacío = se comporta como un aparato más (convivencia con renovaciones).
SERVER_SN = os.environ.get("XTV_SERVER_SN", "").strip()
PORTAL = "masnew"
APP_ID = "com.android.msandroid"
APK_VER = "43404"
SPKG = "2025-03-11 02:26:11_33_13_4.14.186-27270957"
HOSTS = ["fuxok.nguvmqhpk.com", "mptec.dhkrxuzcy.com"]

ROOT_FALLBACK = {
    "masnew_live": 76175,
    "masnew_featured": 76176,
    "masnew_movies": 76177,
    "masnew_series": 76178,
    "masnew_kids": 76783,
    "masnew_anime": 76180,
}
VOD_ROOTS = {
    "masnew_movies": ("movies", "movie"),
    "masnew_series": ("series", "series"),
    "masnew_kids": ("kids", "kids"),
    "masnew_anime": ("anime", "anime"),
}
LIVE_COLUMN = 76182

GENRES_ES = {
    "action": "Acción", "action & adventure": "Acción y Aventuras",
    "adventure": "Aventuras", "animación": "Animación", "animation": "Animación",
    "anime": "Anime", "biography": "Biografía", "cartoon": "Animación",
    "comedia": "Comedia", "comedy": "Comedia",
    "crime": "Crimen", "crimen": "Crimen", "documental": "Documental",
    "documentary": "Documental", "drama": "Drama",
    "family": "Familia", "familia": "Familia", "fantasy": "Fantasía",
    "fantasía": "Fantasía", "guerra": "Guerra", "historia": "Historia",
    "history": "Historia", "horror": "Terror", "infantil": "Infantil",
    "kids": "Infantil", "misterio": "Misterio", "music": "Música",
    "musical": "Musical", "mystery": "Misterio", "news": "Noticias",
    "occidental": "Occidental", "reality": "Reality", "reality-tv": "Reality",
    "romance": "Romántico", "sci-fi": "Ciencia ficción", "sci fi": "Ciencia ficción",
    "science fiction": "Ciencia ficción", "short": "Corto", "soap": "Telenovela",
    "soap opera": "Telenovela", "novela": "Telenovela", "novelas": "Telenovela",
    "telenovela": "Telenovela", "telenovelas": "Telenovela", "dorama": "Telenovela",
    "sport": "Deporte", "suspense": "Suspenso", "talk": "Talk show",
    "terror": "Terror", "thriller": "Suspenso", "tv movie": "Película de TV",
    "war": "Guerra", "western": "Occidental", "aventura": "Aventuras",
}


def slug(text: str) -> str:
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    out = "".join(c if c.isalnum() else "-" for c in folded)
    while "--" in out:
        out = out.replace("--", "-")
    return out.strip("-") or "sin-titulo"


def parse_genres(raw: str) -> list[str]:
    parts = [p.strip() for p in (raw or "").split(",") if p.strip()]
    out = []
    for part in parts:
        key = part.lower()
        if key in GENRES_ES:
            out.append(GENRES_ES[key])
        elif part[:1].isdigit():
            continue  # el año vive en los tags de Magis
        else:
            out.append(part[:1].upper() + part[1:])
    return sorted(set(out))


def parse_duration_min(text: str) -> int:
    try:
        h, m, _ = (text or "").split(":")
        return int(h) * 60 + int(m)
    except Exception:
        return 0


def parse_year(release_time: str) -> int | None:
    digits = "".join(c for c in (release_time or "") if c.isdigit())
    if len(digits) >= 4:
        year = int(digits[:4])
        return year if 1900 <= year <= 2100 else None
    return None


def pick_poster(posters, *kinds: str) -> str:
    for kind in kinds:
        for p in posters or []:
            if p.get("fileType") == kind and p.get("fileUrl"):
                return p["fileUrl"]
    for p in posters or []:
        if p.get("fileUrl"):
            return p["fileUrl"]
    return ""


class XtvClient:
    """Cliente bloqueante del portal XTV; usar desde asyncio.to_thread."""

    def __init__(self, user_token: str, user_id: str, sn: str = ""):
        self.user_token = user_token
        self.user_id = user_id
        self.sn = sn or SERVER_SN or SN
        self._verified = ssl.create_default_context()
        self._unverified = ssl.create_default_context()
        self._unverified.check_hostname = False
        self._unverified.verify_mode = ssl.CERT_NONE

    # ----- transporte -----

    def _post(self, path: str, spec: dict, timeout: int = 30) -> dict:
        body = {
            "apkVersion": APK_VER, "appId": APP_ID, "appLanguage": "es",
            "cpu": "arm64-v8a", "deviceToken": "", "hardwareInfo": "mt6769t",
            "loginType": "3", "model": "SM-A325M", "product": "a32ub",
            "reserve1": "000000000000", "sn": self.sn, "sysVersion": "13",
        }
        body.update(spec)
        wire = wire_encrypt(json.dumps(body, separators=(",", ":"), ensure_ascii=False), XTV_KEY)
        last: Exception | None = None
        for host in HOSTS:
            req = urllib.request.Request(f"https://{host}{path}", data=wire.encode("ascii"), method="POST")
            req.add_header("Content-Type", "application/json; charset=utf-8")
            req.add_header("apk", APP_ID)
            req.add_header("apkVer", APK_VER)
            req.add_header("spkgVer", SPKG)
            req.add_header("User-Agent", "okhttp/4.12.0")
            req.add_header("Cache-Control", "no-store")
            for ctx in (self._verified, self._unverified):
                try:
                    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
                        payload = json.loads(resp.read().decode("utf-8"))
                    code, message, data = payload.get("returnCode"), payload.get("errorMessage"), payload.get("data")
                    if code != "0":
                        raise RuntimeError(f"portal {code} {message}")
                    if not data:
                        return {}
                    return json.loads(wire_decrypt(data, XTV_KEY))
                except ssl.SSLError as exc:
                    last = exc
                except urllib.error.URLError as exc:
                    last = exc
                    break
            log.warning("post %s @%s falló: %s", path, host, last)
        raise RuntimeError(f"XTV sin hosts disponibles: {last}")

    # ----- endpoints -----

    def columns(self, column_id: int | None = None) -> list[dict]:
        spec = {
            "userToken": self.user_token, "userId": self.user_id, "portalCode": PORTAL,
            "specialFlag": "", "pageNum": 1, "pageSize": 100, "numDisplay": 0, "isAv1": "0",
        }
        if column_id is not None:
            spec["columnId"] = column_id
        data = self._post("/api/portalCore/v3/getColumnContents", spec)
        return data.get("childColumnList") or []

    def shelve(self, column_id: int, page: int, page_size: int = 100) -> dict:
        return self._post("/api/portalCore/v3/getShelveData", {
            "userToken": self.user_token, "userId": self.user_id, "portalCode": PORTAL,
            "columnId": column_id, "columnType": "2", "pageSize": page_size, "pageNum": page,
            "numDisplay": 0, "encryptVersion": 0,
        })

    def next_columns(self, column_code: str, page: int = 1, page_size: int = 100) -> dict:
        return self._post("/api/portalCore/getNextColumns", {
            "userId": self.user_id, "userToken": self.user_token, "portalCode": PORTAL,
            "pageNum": page, "pageSize": page_size, "columnCode": column_code, "version": "",
        })

    def live(self) -> list[dict]:
        data = self._post("/api/portalCore/v6/getLiveData", {
            "userToken": self.user_token, "userId": self.user_id, "portalCode": PORTAL,
            "columnId": LIVE_COLUMN, "dataVersion": "", "pageNum": 1, "pageSize": 3000,
            "expireTimeStr": "",
        })
        return data.get("channelList") or []


def new_session(sn: str = "") -> tuple[str, str]:
    """Crea una sesión fresca (snToken -> active); devuelve (userToken, userId)."""

    sn = sn or SERVER_SN or SN

    def call(path: str, spec: dict) -> dict:
        body = {
            "apkVersion": APK_VER, "appId": APP_ID, "appLanguage": "es",
            "cpu": "arm64-v8a", "deviceToken": "", "hardwareInfo": "mt6769t",
            "loginType": "3", "model": "SM-A325M", "product": "a32ub",
            "reserve1": "000000000000", "sn": sn, "sysVersion": "13",
        }
        body.update(spec)
        wire = wire_encrypt(json.dumps(body, separators=(",", ":"), ensure_ascii=False), XTV_KEY)
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        last: Exception | None = None
        for host in HOSTS:
            req = urllib.request.Request(f"https://{host}{path}", data=wire.encode("ascii"), method="POST")
            req.add_header("Content-Type", "application/json; charset=utf-8")
            req.add_header("apk", APP_ID)
            req.add_header("apkVer", APK_VER)
            req.add_header("spkgVer", SPKG)
            req.add_header("User-Agent", "okhttp/4.12.0")
            try:
                with urllib.request.urlopen(req, timeout=25, context=ctx) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                if payload.get("returnCode") != "0":
                    raise RuntimeError(f"portal {payload.get('returnCode')} {payload.get('errorMessage')}")
                data = payload.get("data") or {}
                return json.loads(wire_decrypt(data, XTV_KEY)) if isinstance(data, str) and data else {}
            except Exception as exc:  # noqa: BLE001
                last = exc
        raise RuntimeError(f"sin sesión XTV: {last}")

    sn_token = call("/api/portalCore/v3/snToken", {
        "channel": "default", "macAddr": "02:00:00:00:00:00", "snToken": "", "portalCode": PORTAL,
    }).get("snToken", "")
    if not sn_token:
        raise RuntimeError("snToken vacío")
    active = call("/api/portalCore/v8/active", {
        "snToken": sn_token, "macAddr": "02:00:00:00:00:00", "channel": "default", "portalCode": PORTAL,
    })
    return active.get("userToken", ""), active.get("userId", "")


# ----- sincronización -----

TITLE_SQL = """
insert into titles (id, source, source_id, kind, title, original_title, year, synopsis,
                    poster_url, duration_min, score, genres, cast_list, director, play_ref,
                    is_premiere, is_recommended, program_type)
values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18)
on conflict (id) do update set
    kind = case
        when titles.kind in ('anime', 'series', 'kids') and excluded.kind = 'movie' then titles.kind
        else excluded.kind
    end,
    title = excluded.title,
    original_title = excluded.original_title,
    year = coalesce(excluded.year, titles.year),
    synopsis = excluded.synopsis,
    poster_url = excluded.poster_url,
    duration_min = excluded.duration_min,
    score = excluded.score,
    genres = excluded.genres,
    cast_list = excluded.cast_list,
    director = excluded.director,
    play_ref = excluded.play_ref,
    is_premiere = excluded.is_premiere,
    is_recommended = titles.is_recommended or excluded.is_recommended,
    program_type = excluded.program_type
"""

LINK_SQL = """
insert into title_categories (title_id, category_id, position) values ($1, $2, $3)
on conflict (title_id, category_id) do nothing
"""

CAT_SQL = """
insert into categories (id, title, kind, position) values ($1, $2, $3, $4)
on conflict (id) do update set title = excluded.title, kind = excluded.kind, position = excluded.position
"""


def live_title_row(ch: dict) -> tuple:
    """Una fila de canal en vivo para TITLE_SQL (18 columnas, como title_row)."""
    code = ch.get("channelCode") or ""
    return (
        f"xtv-live-{code}", "xtv", code, "live", ch.get("name") or "",
        None, None, "", pick_poster(ch.get("posterList"), "icon", "logo"), 0, None,
        [], "", "", f"xtvlive:{code}", False, False, "",
    )


def title_row(asset: dict, kind: str, recommended: bool) -> tuple:
    content_id = asset.get("contentId") or ""
    year = parse_year(asset.get("releaseTime"))
    program_type = (asset.get("programType") or "").lower()
    genres = parse_genres(asset.get("tags") or "")
    if program_type in ("series", "teleplay") and kind == "movie":
        kind = "series"
    elif (program_type == "anime" or "Anime" in genres) and kind in ("movie", "series"):
        kind = "anime"
    return (
        f"xtv:{content_id}", "xtv", content_id, kind,
        asset.get("name") or "", asset.get("alias") or None, year,
        asset.get("description") or "", pick_poster(asset.get("posterList"), "icon", "poster", "stage"),
        parse_duration_min(asset.get("duration") or ""),
        float(asset["score"]) if str(asset.get("score", "")).replace(".", "", 1).isdigit() else None,
        genres, asset.get("actorDisplay") or "",
        asset.get("director") or "", f"xtv:{content_id}",
        bool(year and year >= datetime.now().year - 1), recommended,
        program_type,
    )


async def _sync_column(pool, client: XtvClient, column_id: int, kind: str, category_id: str,
                       category_title: str, max_pages: int, delay: float, recommended: bool = False,
                       category_kind: str = "source") -> tuple[int, XtvClient]:
    inserted = 0
    page = 1
    total = None
    while True:
        try:
            data = await asyncio.to_thread(client.shelve, column_id, page)
        except Exception as exc:  # noqa: BLE001 - se conserva lo ya insertado
            if "aaa100083" not in str(exc):
                log.warning("XTV: página %s de %s no bajó (%s); se conserva lo insertado",
                            page, category_id, exc)
                break
            fresh = await _revalidate(pool)
            if fresh is None:
                log.warning("XTV: página %s de %s sin revalidación disponible; se conserva lo insertado",
                            page, category_id)
                break
            client = fresh
            continue  # reintenta la misma página con la sesión nueva
        assets = data.get("assetList") or []
        if not assets:
            break
        total = data.get("assetListTotalSize") or total
        if page == 1 and total:
            log.info("XTV: %s trae %s en el portal (tope 10000 por listado)", category_id, total)
        rows = [title_row(a, kind, recommended) for a in assets if a.get("contentId")]
        links = [(r[0], category_id, i) for i, r in enumerate(rows)]
        async with pool.acquire() as conn:
            await conn.executemany(TITLE_SQL, rows)
            await conn.executemany(LINK_SQL, links)
        inserted += len(rows)
        if max_pages and page >= max_pages:
            break
        if total and inserted >= total:
            break
        page += 1
        if delay:
            await asyncio.sleep(delay)
    return inserted, client


XTV_USER_ID = "967111326"

# Tope de revalidaciones por ciclo: si los aparatos no paran de patear, se deja de
# insistir (lo que falte llega en el próximo ciclo tranquilo). Cada revalidación puede
# patear a la app a su vez, así que el presupuesto es corto a propósito.
_REVALIDATE_BUDGET = 8
_REVALIDATED = 0


async def _revalidate(pool):
    """Sesión nueva con tope por ciclo (None si se acabó el presupuesto o falla)."""
    global _REVALIDATED
    if _REVALIDATED >= _REVALIDATE_BUDGET:
        return None
    _REVALIDATED += 1
    try:
        token, _ = await session_for(pool)
    except Exception as exc:  # noqa: BLE001
        log.warning("XTV: revalidación falló (%s)", exc)
        return None
    return XtvClient(token, XTV_USER_ID)


async def _columns_retry(pool, client, column_id):
    """columns() con una revalidación si el portal nos pateó la sesión a mitad de ciclo
    (aaa100083: la app u otro aparato acuñó otra con el mismo SN). Sin esto, una patada
    en el momento malo tumbaba el conector XTV entero y el ciclo quedaba sin VOD."""
    try:
        return client, await asyncio.to_thread(client.columns, column_id)
    except Exception as exc:  # noqa: BLE001
        if "aaa100083" not in str(exc):
            raise
    log.warning("XTV: sesión pateada en columnas; se revalida")
    fresh = await _revalidate(pool)
    if fresh is None:
        raise RuntimeError("XTV: sin presupuesto de revalidación")
    return fresh, await asyncio.to_thread(fresh.columns, column_id)


async def session_for(pool) -> tuple[str, str]:
    """Reutiliza la sesión guardada; solo renueva si el portal ya no la acepta.

    Renovar en cada ciclo expulsaba a la app (mismo SN) y la app al servidor: un
    ping-pong que dejaba la reproducción sin sesión. Se valida con una llamada
    barata (el árbol de columnas) y solo entonces se acuña una nueva.
    """
    async with pool.acquire() as conn:
        row = await conn.fetchrow("select user_token from portal_sessions where portal = 'xtv'")
    token = (row["user_token"] if row else "") or ""
    if token:
        try:
            await asyncio.to_thread(XtvClient(token, XTV_USER_ID).columns)
            log.info("XTV: sesión guardada reutilizada")
            return token, XTV_USER_ID
        except Exception:  # noqa: BLE001
            log.warning("XTV: la sesión guardada no sirve; se renueva")
    fresh, user_id = await asyncio.to_thread(new_session)
    async with pool.acquire() as conn:
        await conn.execute(
            """
            insert into portal_sessions (portal, user_token, sn_token, updated_at)
            values ('xtv', $1, '', now())
            on conflict (portal) do update set user_token = excluded.user_token, updated_at = now()
            """,
            fresh,
        )
    return fresh, user_id or XTV_USER_ID


async def sync(pool) -> int:
    max_pages = int(os.environ.get("XTV_MAX_PAGES", "0"))
    collection_pages = int(os.environ.get("XTV_COLLECTION_PAGES", "3"))
    # Las colecciones propias de series/anime/kids se bajan más hondo: el "All" de series
    # trae 21k en el portal y el tope por listado es 10k, así que las temporadas viejas
    # solo aparecen en colecciones (3 páginas = 300 títulos las dejaba fuera).
    series_collection_pages = int(os.environ.get("XTV_SERIES_COLLECTION_PAGES", "0"))
    delay = float(os.environ.get("XTV_PAGE_DELAY_SECONDS", "0.15"))
    total = 0
    global _REVALIDATED
    _REVALIDATED = 0
    token, user_id = await session_for(pool)
    client = XtvClient(token, user_id)
    log.info("XTV: sesión lista (userId=%s)", user_id)

    client, _root_cols = await _columns_retry(pool, client, None)
    roots = {c.get("code"): c for c in _root_cols}
    if not roots:
        raise RuntimeError("XTV: árbol de columnas vacío")

    # Canales en vivo -> categoría especial "tv-en-vivo".
    try:
        channels = await asyncio.to_thread(client.live)
        if channels:
            async with pool.acquire() as conn:
                await conn.execute(CAT_SQL, "tv-en-vivo", "TV en vivo", "special", 5)
            rows, links = [], []
            for i, ch in enumerate(channels):
                code = ch.get("channelCode") or ""
                if not code:
                    continue
                rows.append(live_title_row(ch))
                links.append((f"xtv-live-{code}", "tv-en-vivo", i))
            async with pool.acquire() as conn:
                await conn.executemany(TITLE_SQL, rows)
                await conn.executemany(LINK_SQL, links)
            log.info("XTV: %s canales en vivo indexados", len(rows))
            total += len(rows)
    except Exception:  # noqa: BLE001
        log.exception("XTV: el listado de canales falló (se continúa)")

    # Catálogo VOD: "All" completo por sección + colecciones propias (con tope).
    # Cada raíz va aislada: si el portal patea a mitad de una, las demás igual se bajan.
    for root_code, (short, kind) in VOD_ROOTS.items():
        try:
            root = roots.get(root_code)
            if not root:
                continue
            client, children = await _columns_retry(pool, client, root["id"])
            all_col = next(
                (c for c in children if (c.get("name") or "").strip().lower() in ("all", "todas", "todos")),
                None,
            )
            for child in children:
                name = (child.get("name") or "").strip()
                if not name:
                    continue
                is_all = all_col is not None and child.get("id") == all_col.get("id")
                category_id = f"xtv-{short}-all" if is_all else f"xtv-{short}-{child.get('id')}"
                title = f"{short_label(short)} (todas)" if is_all else name
                async with pool.acquire() as conn:
                    await conn.execute(CAT_SQL, category_id, title, "source", 50 if is_all else 100)
                pages = max_pages if is_all else (collection_pages or max_pages)
                if not is_all and kind in ("series", "anime", "kids"):
                    pages = series_collection_pages or collection_pages or max_pages
                try:
                    count, client = await _sync_column(
                        pool, client, int(child["id"]), kind, category_id, title,
                        pages, delay, recommended=False,
                    )
                    log.info("XTV: %s [%s] %s títulos", category_id, name, count)
                    total += count
                except Exception:  # noqa: BLE001
                    log.exception("XTV: la columna %s falló (se continúa)", category_id)
        except Exception:  # noqa: BLE001
            log.exception("XTV: la raíz %s falló (se continúa)", root_code)

    # Recomendados: las repisas de masnew_featured (los títulos se suben aquí
    # también, por si el tope de páginas no los trajo en las secciones).
    try:
        featured = await asyncio.to_thread(client.next_columns, "masnew_featured")
        shelves = featured.get("recommendList") or []
        title_rows: list[tuple] = []
        seen: set[str] = set()
        for shelf in shelves:
            for asset in shelf.get("assetList") or []:
                cid = asset.get("contentId")
                if not cid or f"xtv:{cid}" in seen:
                    continue
                seen.add(f"xtv:{cid}")
                title_rows.append(title_row(asset, "movie", True))
        if title_rows:
            async with pool.acquire() as conn:
                await conn.execute(CAT_SQL, "recomendados", "Recomendados", "special", 10)
                await conn.executemany(TITLE_SQL, title_rows)
                await conn.executemany(
                    LINK_SQL, [(r[0], "recomendados", i) for i, r in enumerate(title_rows)]
                )
        log.info("XTV: %s títulos marcados como recomendados", len(title_rows))
        total += len(title_rows)
    except Exception:  # noqa: BLE001
        log.exception("XTV: recomendados falló (se continúa)")
    return total


def short_label(short: str) -> str:
    return {
        "movies": "Películas", "series": "Series", "kids": "Kids", "anime": "Anime",
    }.get(short, short)
