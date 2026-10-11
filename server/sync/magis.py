"""Conector Magis (portal "masmobile"): sus filas de Home (Recomendados, películas,
series, kids, anime) con los assets que cada fila trae. Sesión propia vía
v3/snToken -> v8/active (cuenta ligada al dispositivo, sin contraseña).

Los títulos quedan como refs `magisvod:<contentId>`: la app los resuelve en el
dispositivo con su motor Titan, igual que siempre.
"""

import asyncio
import json
import logging
import os
import ssl
import urllib.error
import urllib.request
import unicodedata
from datetime import datetime

from wire import MAGIS_KEY, wire_decrypt, wire_encrypt
from xtv import slug  # reutiliza el slug compartido

log = logging.getLogger("magis")

PORTAL = "masmobile"
APP_ID = "com.msandroid.mobile"
APK_VER = "60507"
SN = "f6be8b3feafdfb9e1c08d5f50c0c2c8f"
DEVICE_TOKEN = "cTzYag_MRJKTGK69Rx1kVH"
HOSTS = ["rhokqi.ryh1gifvn.com", "zgmnxa.ryh1gifvn.com"]

HOME_PAGES = [
    ("masmobile_Recommended", "Recomendados"),
    ("masmobile_movies", "Películas"),
    ("masmobile_series", "Series"),
    ("masmobile_kids", "Kids"),
    ("masmobile_animes", "Anime"),
]

GENRES_ES = {
    "action": "Acción", "adventure": "Aventuras", "animation": "Animación",
    "anime": "Anime", "biography": "Biografía", "comedy": "Comedia",
    "crime": "Crimen", "documentary": "Documental", "drama": "Drama",
    "family": "Familia", "fantasy": "Fantasía", "history": "Historia",
    "horror": "Terror", "kids": "Infantil", "music": "Música",
    "mystery": "Misterio", "romance": "Romántico", "sci-fi": "Ciencia ficción",
    "thriller": "Suspenso", "war": "Guerra", "western": "Occidental",
    "sport": "Deporte", "reality": "Reality", "soap": "Telenovela",
}


def _parse_genres(raw: str) -> list[str]:
    out = []
    for part in (raw or "").split(","):
        part = part.strip()
        if not part or part[:1].isdigit():
            continue
        out.append(GENRES_ES.get(part.lower(), part[:1].upper() + part[1:]))
    return sorted(set(out))


def _parse_year(raw: str) -> int | None:
    import re

    match = re.search(r"\b(19|20)\d{2}\b", raw or "")
    return int(match.group(0)) if match else None


def _parse_duration_min(text: str) -> int:
    try:
        h, m, _ = (text or "").split(":")
        return int(h) * 60 + int(m)
    except Exception:
        return 0


class MagisClient:
    def __init__(self, user_token: str, user_id: str):
        self.user_token = user_token
        self.user_id = user_id
        self._ctx = ssl.create_default_context()
        self._ctx.check_hostname = False
        self._ctx.verify_mode = ssl.CERT_NONE

    def _post(self, path: str, spec: dict) -> dict:
        body = {
            "apkVersion": APK_VER, "appId": APP_ID, "appLanguage": "es",
            "cpu": "arm64-v8a", "deviceToken": DEVICE_TOKEN, "hardwareInfo": "mt6769t",
            "loginType": "3", "model": "SM-A325M", "product": "a32ub",
            "reserve1": "000000000000", "sn": SN, "sysVersion": "13",
        }
        body.update(spec)
        wire = wire_encrypt(json.dumps(body, separators=(",", ":"), ensure_ascii=False), MAGIS_KEY)
        last: Exception | None = None
        for host in HOSTS:
            req = urllib.request.Request(f"https://{host}{path}", data=wire.encode("ascii"), method="POST")
            req.add_header("Content-Type", "application/json;charset=utf-8")
            req.add_header("apk", APP_ID)
            req.add_header("apkVer", APK_VER)
            req.add_header("spkgVer", "13")
            req.add_header("User-Agent", "okhttp/4.12.0")
            try:
                with urllib.request.urlopen(req, timeout=25, context=self._ctx) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                code, message, data = payload.get("returnCode"), payload.get("errorMessage"), payload.get("data")
                if code != "0":
                    raise RuntimeError(f"portal {code} {message}")
                if not data:
                    return {}
                return json.loads(wire_decrypt(data, MAGIS_KEY))
            except Exception as exc:  # noqa: BLE001
                last = exc
        raise RuntimeError(f"Magis sin hosts: {last}")

    def home(self, page_code: str) -> list[dict]:
        data = self._post("/api/portalCore/getHome", {
            "homePageCode": page_code, "version": "", "freeVodCode": "masmobile_free",
            "freeVersion": "", "userId": self.user_id, "portalCode": PORTAL,
            "userToken": self.user_token,
        })
        return data.get("recommendList") or []


def new_session() -> tuple[str, str]:
    def call(path: str, spec: dict) -> dict:
        body = {
            "apkVersion": APK_VER, "appId": APP_ID, "appLanguage": "es",
            "cpu": "arm64-v8a", "deviceToken": DEVICE_TOKEN, "hardwareInfo": "mt6769t",
            "loginType": "3", "model": "SM-A325M", "product": "a32ub",
            "reserve1": "000000000000", "sn": SN, "sysVersion": "13",
        }
        body.update(spec)
        wire = wire_encrypt(json.dumps(body, separators=(",", ":"), ensure_ascii=False), MAGIS_KEY)
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        last: Exception | None = None
        for host in HOSTS:
            req = urllib.request.Request(f"https://{host}{path}", data=wire.encode("ascii"), method="POST")
            req.add_header("Content-Type", "application/json;charset=utf-8")
            req.add_header("apk", APP_ID)
            req.add_header("apkVer", APK_VER)
            req.add_header("spkgVer", "13")
            try:
                with urllib.request.urlopen(req, timeout=25, context=ctx) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                if payload.get("returnCode") != "0":
                    raise RuntimeError(f"portal {payload.get('returnCode')} {payload.get('errorMessage')}")
                data = payload.get("data") or {}
                return json.loads(wire_decrypt(data, MAGIS_KEY)) if isinstance(data, str) and data else {}
            except Exception as exc:  # noqa: BLE001
                last = exc
        raise RuntimeError(f"sin sesión Magis: {last}")

    sn_token = call("/api/portalCore/v3/snToken", {
        "channel": "default", "macAddr": "02:00:00:00:00:00", "snToken": "",
    }).get("snToken", "")
    if not sn_token:
        raise RuntimeError("snToken vacío")
    active = call("/api/portalCore/v8/active", {
        "channel": "default", "macAddr": "02:00:00:00:00:00", "snToken": sn_token,
    })
    return active.get("userToken", ""), active.get("userId", "")


def _pick_poster(posters) -> str:
    for kind in ("icon", "poster", "stage"):
        for p in posters or []:
            if p.get("fileType") == kind and p.get("fileUrl"):
                return p["fileUrl"]
    for p in posters or []:
        if p.get("fileUrl"):
            return p["fileUrl"]
    return ""


async def sync(pool) -> int:
    from xtv import CAT_SQL, LINK_SQL, TITLE_SQL  # comparte SQL

    # Igual que XTV: reutilizar la sesión guardada para no expulsar a la app.
    async with pool.acquire() as conn:
        row = await conn.fetchrow("select user_token from portal_sessions where portal = 'magis'")
    token = (row["user_token"] if row else "") or ""
    user_id = ""
    if token:
        try:
            await asyncio.to_thread(MagisClient(token, "").home, "masmobile_Recommended")
            log.info("Magis: sesión guardada reutilizada")
            user_id = ""
        except Exception:  # noqa: BLE001
            log.warning("Magis: la sesión guardada no sirve; se renueva")
            token = ""
    if not token:
        token, user_id = await asyncio.to_thread(new_session)
        async with pool.acquire() as conn:
            await conn.execute(
                """
                insert into portal_sessions (portal, user_token, sn_token, updated_at)
                values ('magis', $1, '', now())
                on conflict (portal) do update set user_token = excluded.user_token, updated_at = now()
                """,
                token,
            )
    client = MagisClient(token, user_id)
    log.info("Magis: sesión lista")

    total = 0
    for page_code, label in HOME_PAGES:
        try:
            rows = await asyncio.to_thread(client.home, page_code)
        except Exception:  # noqa: BLE001
            log.exception("Magis: %s falló (se continúa)", page_code)
            continue
        short = page_code.removeprefix("masmobile_").lower()
        for row in rows:
            name = (row.get("name") or row.get("alias") or label).strip()
            column_id = row.get("columnId") or short
            category_id = f"magis-{short}-{column_id}"
            assets = row.get("assetList") or []
            title_rows, links = [], []
            for i, asset in enumerate(assets):
                cid = asset.get("contentId") or ""
                if not cid or not asset.get("name"):
                    continue
                tags = asset.get("tags") or ""
                program_type = (asset.get("programType") or "").lower()
                kind = "series" if program_type in ("series", "teleplay") else "movie"
                year = _parse_year(tags)
                title_rows.append((
                    f"magis:{cid}", "magis", cid, kind, asset.get("name") or "",
                    asset.get("alias") or None, year, asset.get("description") or "",
                    _pick_poster(asset.get("posterList")),
                    _parse_duration_min(asset.get("duration") or ""), None,
                    _parse_genres(tags), asset.get("actorDisplay") or "",
                    asset.get("director") or "", f"magisvod:{cid}",
                    bool(year and year >= datetime.now().year - 1),
                    not short == "movies", program_type,
                ))
                links.append((f"magis:{cid}", category_id, i))
            if not title_rows:
                continue
            async with pool.acquire() as conn:
                await conn.execute(CAT_SQL, category_id, name, "source", 200)
                await conn.executemany(TITLE_SQL, title_rows)
                await conn.executemany(LINK_SQL, links)
            total += len(title_rows)
        log.info("Magis: %s -> %s filas", page_code, len(rows))
    return total
