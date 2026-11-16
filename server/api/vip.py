"""Broker de licencias FHD (cuenta de cortesía).

La app pide aquí el paquete de reproducción y el SLB lo pide en el equipo. Así el
`client_ip` del CDN sigue siendo el del reproductor (el SLB firma la IP) y el
servidor solo acuña la licencia, que no lleva IP.

Por qué un broker: la licencia la firma el portal con una clave que no viaja en
el APK (probamos a reproducirla y no se puede fabricar). El broker mantiene una
sesión con la cuenta de cortesía, pide el `v10/startPlayVOD` por su cuenta y
devuelve las pistas ya firmadas. La app las reproduce con el SLB local. Si el
broker no responde, la app sigue con su flujo normal (cero dependencia).

La cuenta se configura con `VIP_SN` / `VIP_USER_ID` (por defecto la histórica).
Para rotar varias cuentas basta con cambiar esas variables y reiniciar; las
licencias se cachean por título hasta que el portal las firma (≈24 h), así que
un reinicio no tira lo ya firmado.
"""

from __future__ import annotations

import base64
import hashlib
import json
import logging
import os
import ssl
import threading
import time
import urllib.request

from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

from portal import APK_VER, APP_ID, HOSTS, SPKG
from wire import MAGIS_KEY, wire_decrypt, wire_encrypt

log = logging.getLogger("visionplus.vip")

PLAY_PORTAL = "masmobile"
# Columnas de respaldo cuando la app no manda una (deeplink/arranque en frío).
# Las «All» del portal actual autorizan cualquier título de su sección; las Free
# firman el catálogo abierto. 79716/76182 ya no existen (portal100002): pedirlas
# abortaba toda la búsqueda y el play caía a 480p.
FALLBACK_COLUMNS = (77115, 77143, 77177, 87887, 87888, 87889, 79715, 77117)
SESSION_TTL = 25 * 60.0
# La cuenta por correo (email + contraseña) dura mucho más que una sesión de
# dispositivo; no hace falta acuñar cada 25 minutos.
EMAIL_SESSION_TTL = 6 * 60 * 60.0
CACHE_TTL = 6 * 60 * 60.0
REQUEST_TIMEOUT = 18

SN = os.environ.get("VIP_SN", "2392fe5186d83d73508a2c8c60a26ef6").strip()
USER_ID = os.environ.get("VIP_USER_ID", "967111326").strip()
# Cuenta por correo (opcional). Se guarda SOLO en el servidor (.env), nunca en
# el APK. `VIP_PASSWORD_HASH` evita guardar la contraseña en claro: es el
# `getCombinePasswordMd5` que firma la app (MD5(pwd+salt) + AES-ECB(pwd)).
VIP_EMAIL = os.environ.get("VIP_EMAIL", "").strip()
VIP_PASSWORD = os.environ.get("VIP_PASSWORD", "").strip()
VIP_PASSWORD_HASH = os.environ.get("VIP_PASSWORD_HASH", "").strip()
LOGIN_SALT = "ntFT65w6itH!lHCPw7D=@qnsFC5adD28"
LOGIN_AES_KEY = b"ntFT65w6itH!lHCP"

_LOCK = threading.RLock()
_TOKEN = ""
_UID = ""
_TOKEN_AT = 0.0
_CACHE: dict[tuple, tuple[float, dict]] = {}

# --- estado de la cuenta para el operador (`status`) -------------------------
# La cuenta es de un solo dueño: este proceso. Nadie más debe acuñar con su SN
# o la patada (`aaa100028`) viaja en ambos sentidos. Estos campos solo observan.
_DAYS: int | None = None
_DAYS_AT = 0.0
_ENTITLED: bool | None = None
_ACCOUNT_EXPIRES: int = 0
_MISMATCH = False
_LAST_ERROR = ""
_LAST_KICK_AT = 0.0
_MINTS = 0
_KICKS = 0


class BrokerError(RuntimeError):
    """Fallo con causa clasificable por el endpoint (para que la app decida)."""


class AccountNoDays(BrokerError):
    """La cuenta quedó sin días: solo sirven las licencias ya cacheadas."""


class AccountMismatch(BrokerError):
    """`VIP_SN` y `VIP_USER_ID` no son de la misma cuenta."""


class NoLicense(BrokerError):
    """El título no tiene pista firmable (portal vacío o rechazo)."""


def account_expires_at() -> int:
    """Epoch en que vence la cuenta según `expiredAt`/días del portal (0 = desconocido)."""
    if _ACCOUNT_EXPIRES:
        return _ACCOUNT_EXPIRES
    if _DAYS is None or _DAYS_AT <= 0:
        return 0
    return int(_DAYS_AT + _DAYS * 86400)

_SSL = ssl.create_default_context()
_SSL.check_hostname = False
_SSL.verify_mode = ssl.CERT_NONE


def _post(path: str, spec: dict, timeout: int = REQUEST_TIMEOUT) -> dict:
    body = {
        "apkVersion": APK_VER,
        "appId": APP_ID,
        "appLanguage": "es",
        "cpu": "arm64-v8a",
        "deviceToken": "",
        "hardwareInfo": "mt6769t",
        "loginType": "3",
        "model": "SM-A325M",
        "product": "a32ub",
        "reserve1": "000000000000",
        "sn": SN,
        "sysVersion": "13",
    }
    body.update(spec)
    wire = wire_encrypt(json.dumps(body, separators=(",", ":"), ensure_ascii=False), MAGIS_KEY)
    last: Exception | None = None
    for host in HOSTS:
        request = urllib.request.Request(
            f"https://{host}{path}", data=wire.encode("ascii"), method="POST"
        )
        request.add_header("Content-Type", "application/json;charset=utf-8")
        request.add_header("apk", APP_ID)
        request.add_header("apkVer", APK_VER)
        request.add_header("spkgVer", SPKG)
        request.add_header("User-Agent", "okhttp/4.12.0")
        request.add_header("Cache-Control", "no-store")
        try:
            with urllib.request.urlopen(request, timeout=timeout, context=_SSL) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            code = payload.get("returnCode")
            if code != "0":
                raise RuntimeError(f"portal {code} {payload.get('errorMessage')}")
            encrypted = payload.get("data")
            return json.loads(wire_decrypt(encrypted, MAGIS_KEY)) if encrypted else {}
        except Exception as exc:  # noqa: BLE001 - se prueba el host de respaldo
            last = exc
    raise RuntimeError(f"portal sin host disponible: {last}")


def _combine_password(password: str) -> str:
    """`getCombinePasswordMd5` de la app: MD5(pwd+salt) + AES-ECB(pwd) en Base64."""
    raw = password.encode("utf-8")
    md5_salt = hashlib.md5(raw + LOGIN_SALT.encode("utf-8")).hexdigest()
    aes = AES.new(LOGIN_AES_KEY, AES.MODE_ECB)
    encrypted = aes.encrypt(pad(raw, AES.block_size))
    return (md5_salt + base64.b64encode(encrypted).decode("ascii")).strip()


def _email_login() -> tuple[str, str]:
    """Inicia sesión con la cuenta de correo del broker (sin SN ni device)."""
    global _UID, _DAYS, _DAYS_AT, _ENTITLED, _ACCOUNT_EXPIRES, _MINTS, _MISMATCH
    password = VIP_PASSWORD_HASH or _combine_password(VIP_PASSWORD)
    payload = _post(
        "/api/portalCore/v8/login",
        {
            "accountType": "2",
            "userName": VIP_EMAIL,
            "password": password,
            "type": "1",
            "macAddr": "02:00:00:00:00:00",
            "areaCode": "",
            "verificationCode": "",
            "verificationToken": "",
            "matadata": "",
            "signdata": "",
            "userToken": "",
            "channel": "default",
            "portalCode": PLAY_PORTAL,
        },
    )
    token = str(payload.get("userToken") or "").strip()
    user_id = str(payload.get("userId") or "").strip()
    if not token or not user_id:
        raise AccountMismatch("login de correo sin userToken/userId")
    _UID = user_id
    _MISMATCH = False
    try:
        _DAYS = int(payload.get("remainingDays") or 0)
    except (TypeError, ValueError):
        _DAYS = 0
    now = time.time()
    _DAYS_AT = now
    expires = 0
    entitled = False
    for row in payload.get("authInfoList") or []:
        if not isinstance(row, dict):
            continue
        invalid = _parse_epoch(row.get("invalidTime") or row.get("expireTime"))
        if invalid > expires:
            expires = invalid
        if invalid > now and str(row.get("status") or "1") == "1":
            entitled = True
    _ENTITLED = str(payload.get("hasPay") or "") == "1" or _DAYS > 0 or entitled
    if expires:
        _ACCOUNT_EXPIRES = expires
    _MINTS += 1
    log.info("VIP: login correo user=%s días=%s", user_id, _DAYS)
    return token, user_id


def _mint() -> tuple[str, str]:
    """Acuña una sesión con la cuenta configurada (snToken -> active).

    `active` devuelve el userId REALMENTE ligado al SN, así que ese manda: si no
    coincide con `VIP_USER_ID` se marca `_MISMATCH` (par mal copiado en `.env`).
    """
    global _UID, _DAYS, _DAYS_AT, _ENTITLED, _MISMATCH, _ACCOUNT_EXPIRES, _MINTS
    if VIP_EMAIL and (VIP_PASSWORD or VIP_PASSWORD_HASH):
        return _email_login()
    st = _post(
        "/api/portalCore/v3/snToken",
        {"channel": "default", "macAddr": "02:00:00:00:00:00", "snToken": "", "portalCode": "masnew"},
    )
    sn_token = (st.get("snToken") or "").strip()
    if not sn_token:
        raise AccountMismatch("snToken vacío: el SN no está aprovisionado")
    active = _post(
        "/api/portalCore/v8/active",
        {
            "channel": "default",
            "macAddr": "02:00:00:00:00:00",
            "snToken": sn_token,
            "portalCode": "masnew",
        },
    )
    token = (active.get("userToken") or "").strip()
    user_id = (active.get("userId") or "").strip() or USER_ID
    if not token or not user_id:
        raise AccountMismatch("active sin userToken/userId")
    _MISMATCH = bool(USER_ID and user_id != USER_ID)
    if _MISMATCH:
        log.warning("VIP: userId del portal=%s ≠ VIP_USER_ID=%s", user_id, USER_ID)
    _UID = user_id
    if not _ACCOUNT_EXPIRES:
        _ACCOUNT_EXPIRES = _parse_epoch(
            active.get("expiredAt") or active.get("expireTime") or active.get("vipExpireTime")
        )
    _read_entitlement(active, token, user_id)
    _MINTS += 1
    return token, user_id


def _parse_epoch(raw) -> int:
    """Acepta epoch (s/ms) o `YYYY-MM-DD HH:MM:SS`; 0 si no se entiende."""
    text = str(raw or "").strip()
    if not text:
        return 0
    if text.isdigit():
        value = int(text)
        return value // 1000 if value > 10**11 else value
    try:
        from datetime import datetime, timezone

        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            # El portal publica `nowTime`/`invalidTime` en UTC sin sufijo.
            parsed = parsed.replace(tzinfo=timezone.utc)
        return int(parsed.timestamp())
    except Exception:  # noqa: BLE001 - formato ajeno
        return 0


def _read_entitlement(active: dict, token: str, user_id: str) -> None:
    """Guarda días/vencimiento/estado desde `active`. Nunca tumba el play.

    `authInfoList[].invalidTime` es el vencimiento real de cada suscripción; se
    toma el más lejano. Si `active` no lo trae, se completa con `getAuthInfo`.
    """
    global _DAYS, _DAYS_AT, _ENTITLED, _ACCOUNT_EXPIRES
    info = active
    entries = active.get("authInfoList")
    if not isinstance(entries, list) or not entries:
        try:
            info = _post(
                "/api/portalCore/v9/getAuthInfo",
                {"userToken": token, "userId": user_id, "portalCode": PLAY_PORTAL, "lang": "es", "type": "1"},
                timeout=8,
            )
            entries = info.get("authInfoList") or []
        except Exception as exc:  # noqa: BLE001 - el semáforo no bloquea
            # portal100042 = «SN授权过期»: la cuenta ya no firma. No se debe
            # dejar `_ENTITLED` en True solo porque `active.hasFreeAuth=1`
            # (esa bandera sigue viva después del vencimiento).
            log.warning("VIP: getAuthInfo falló: %s", exc)
            entries = active.get("authInfoList") or []
    now = time.time()
    expires = 0
    entitled = False
    for row in entries if isinstance(entries, list) else []:
        if not isinstance(row, dict):
            continue
        invalid = _parse_epoch(row.get("invalidTime") or row.get("expireTime"))
        if invalid > expires:
            expires = invalid
        if invalid > now and str(row.get("status") or "1") == "1":
            entitled = True
    _DAYS = max(0, int((expires - now) // 86400)) if expires else None
    _DAYS_AT = now
    try:
        remaining = int(info.get("remainingDays") or 0)
    except (TypeError, ValueError):
        remaining = 0
    free_flag = str(info.get("getFreeAuthFlag") or "").strip().lower() in ("yes", "1", "true")
    # `hasFreeAuth` por sí sola NO significa vigencia: el portal la deja en «1»
    # después de vencer (visto 2026-11-16: hasFreeAuth=1, remainingDays=0,
    # getFreeAuthFlag=no). Solo cuenta si la campaña sigue abierta o hay días.
    _ENTITLED = (
        entitled
        or str(info.get("hasPay") or "") == "1"
        or remaining > 0
        or (free_flag and str(info.get("hasFreeAuth") or "") == "1")
    )
    if expires:
        _ACCOUNT_EXPIRES = expires


def _session(force: bool = False) -> tuple[str, str]:
    global _TOKEN, _UID, _TOKEN_AT
    with _LOCK:
        if not force and _TOKEN and time.time() - _TOKEN_AT < (EMAIL_SESSION_TTL if VIP_EMAIL else SESSION_TTL):
            return _TOKEN, _UID
        _TOKEN, _UID = _mint()
        _TOKEN_AT = time.time()
        log.info("VIP: sesión acuñada user=%s", _UID)
        return _TOKEN, _UID


def _drop_session() -> None:
    global _TOKEN, _TOKEN_AT, _LAST_KICK_AT, _KICKS
    with _LOCK:
        _TOKEN = ""
        _TOKEN_AT = 0.0
        _LAST_KICK_AT = time.time()
        _KICKS += 1


def _license_expired(license_text: str) -> int:
    for part in license_text.split("&"):
        if part.startswith("expired="):
            try:
                return int(part.split("=", 1)[1])
            except ValueError:
                return 0
    return 0


def best_medias(payload: dict) -> list[dict]:
    """Pistas con licencia, mejor calidad primero (puro y testeable)."""
    ranks = {"1080p": 3, "720p": 2, "480p": 1}
    rows: list[tuple[int, int, dict]] = []
    episodes = payload.get("episodeList") or []
    for episode in episodes if isinstance(episodes, list) else []:
        groups = (episode or {}).get("totalMovieList") or []
        for group in groups if isinstance(groups, list) else []:
            quality = str((group or {}).get("quality") or "")
            rank = ranks.get(quality, 0)
            if rank == 0:
                continue
            for media in (group or {}).get("movieList") or []:
                if not isinstance(media, dict):
                    continue
                licenses = media.get("licenseList") or []
                license_text = ""
                if licenses and isinstance(licenses[0], dict):
                    license_text = str(licenses[0].get("license") or "")
                code = str(media.get("contentId") or "")
                if not code or not license_text:
                    continue
                codec = str(media.get("encodeFormat") or "")
                # h265 primero (mejor bitrate), h264 como respaldo para equipos viejos.
                codec_rank = 1 if codec == "h265" else 0
                rows.append(
                    (
                        rank,
                        codec_rank,
                        {
                            "n": code,
                            "l": license_text,
                            "q": quality,
                            "e": codec or "h264",
                            "f": str(media.get("videoFormat") or "ts"),
                            "a": str(media.get("audioInfo") or "spa"),
                        },
                    )
                )
    rows.sort(key=lambda item: (item[0], item[1]), reverse=True)
    # 1080 h265/h264 + 720 de respaldo: suficiente para el motor y pocos KB.
    return [media for _, _, media in rows[:4]]


def _play(content_id: str, series_id: str, column_id: int, kind: str, episodes: list[int]) -> dict:
    cache_key = (content_id, series_id, int(column_id or 0), kind, tuple(episodes))
    now = time.time()
    cached = _CACHE.get(cache_key)
    if cached and now < cached[0]:
        return cached[1]
    # Cuenta vencida: no se gasta una vuelta al portal por cada título. La app
    # recibe 409 y cae al flujo libre de inmediato (antes: 502 tras 25 s).
    if _ENTITLED is False:
        raise AccountNoDays("cuenta sin días; solo sirven las licencias cacheadas")

    last_error: Exception | None = None
    is_series = bool(series_id and series_id != content_id)
    effective_sid = series_id if is_series else content_id
    auth_type = "1" if is_series else ""
    for attempt in (0, 1):
        token, user_id = _session(force=attempt > 0)
        # La columna que manda la app primero; después las «All» y las Free. Si
        # la app manda una columna equivocada (mapa incompleto), el título aún
        # se puede firmar en su repisa real en vez de caer a 480p.
        columns: list[int] = []
        if column_id > 0:
            columns.append(int(column_id))
        for candidate in FALLBACK_COLUMNS:
            if candidate not in columns:
                columns.append(candidate)
        medias: list[dict] = []
        kicked = False
        for column in columns:
            spec = {
                "userToken": token,
                "userId": user_id,
                "portalCode": PLAY_PORTAL,
                "contentId": content_id,
                "seriesContentId": effective_sid,
                "type": kind,
                "startTime": 0,
                "authType": auth_type,
            }
            if is_series and episodes:
                spec["episodeNumberList"] = episodes
            if column > 0:
                spec["columnId"] = int(column)
            try:
                payload = _post("/api/portalCore/v10/startPlayVOD", spec)
            except Exception as exc:  # noqa: BLE001 - portal: se decide por código
                last_error = exc
                message = str(exc)
                if "portal100042" in message:
                    # SN授权过期: la cuenta ya no firma ni una pista.
                    raise AccountNoDays(f"cuenta sin autorización (user={user_id})") from exc
                if "aaa100083" in message or "aaa100028" in message:
                    _drop_session()
                    kicked = True
                    break
                if "portal100002" in message or "portal100008" in message:
                    # Columna que ya no existe / título fuera de esa repisa.
                    continue
                raise
            medias = best_medias(payload)
            if medias:
                break
        if not medias:
            if kicked and attempt == 0:
                continue
            # Sin pistas y cuenta sin días: es agotamiento, no un título raro.
            # La app cae al flujo libre de inmediato en vez de reintentar.
            if _ENTITLED is False:
                raise AccountNoDays(
                    f"cuenta sin días (user={user_id}); quedan {len(_CACHE)} licencias cacheadas"
                )
            raise NoLicense(f"título sin licencia firmable (columnas={columns})")
        expires = _license_expired(medias[0]["l"])
        ttl = CACHE_TTL
        if expires:
            ttl = max(60.0, min(CACHE_TTL, expires - now - 120.0))
        result = {
            "medias": medias,
            "token": token,
            "userId": user_id,
            "sn": SN,
            "expiresAt": int(expires or (now + ttl)),
        }
        _CACHE[cache_key] = (now + ttl, result)
        return result

    raise RuntimeError(f"sin licencia: {last_error}")


def play(content_id: str, series_id: str, column_id: int, kind: str, episodes: list[int]) -> dict:
    """Entrada bloqueante: usar desde `asyncio.to_thread`."""
    global _LAST_ERROR
    if not content_id:
        raise ValueError("contentId vacío")
    if kind not in ("0", "1", "2", "3"):
        kind = "0"
    clean_episodes = [int(x) for x in episodes if isinstance(x, (int, str)) and str(x).isdigit()]
    if not clean_episodes:
        clean_episodes = [1]
    # El lock solo cubre el acuñado (_session); los v10 con el mismo token pueden
    # correr en paralelo. Bloquear todo el play serializaba a la familia entera.
    try:
        result = _play(content_id, series_id, int(column_id or 0), kind, clean_episodes)
    except Exception as exc:
        _LAST_ERROR = f"{type(exc).__name__}: {exc}"[:300]
        raise
    _LAST_ERROR = ""
    return result


def _cache_snapshot() -> list[float]:
    """Vencimientos del caché sin morir si otro hilo lo redimensiona."""
    for _ in range(3):
        try:
            return [ts for ts, _payload in list(_CACHE.values())]
        except RuntimeError:  # noqa: PERF203 - carrera de redimensionado
            continue
    return []


# ------------------------------------------------------------------ operación

def status() -> dict:
    """Semáforo de la cuenta para el panel/CLI del operador.

    Refresca días sin patada: si ya hay sesión consulta `getAuthInfo` (no acuña);
    solo acuña cuando el broker está en frío.
    """
    now = time.time()
    if not VIP_EMAIL and (_DAYS_AT == 0 or now - _DAYS_AT > 1800):
        try:
            token, uid = _session()
            _read_entitlement({}, token, uid)
        except Exception as exc:  # noqa: BLE001 - el semáforo nunca tumba el panel
            log.warning("VIP status: %s", exc)
    epochs = _cache_snapshot()
    valid = sum(1 for ts in epochs if ts > now)
    return {
        "sn": SN,
        "userId": _UID or USER_ID,
        "configuredUserId": USER_ID,
        "mismatch": _MISMATCH,
        "daysLeft": _DAYS,
        "daysReadAt": int(_DAYS_AT or 0),
        "entitled": _ENTITLED,
        "accountExpiresAt": account_expires_at(),
        "sessionAge": int(now - _TOKEN_AT) if _TOKEN_AT else None,
        "mints": _MINTS,
        "kicks": _KICKS,
        "lastKickAt": int(_LAST_KICK_AT or 0),
        "cacheEntries": len(_CACHE),
        "cacheValid": valid,
        "lastError": _LAST_ERROR,
    }


# Título de sonda: existe en catálogo, tiene pistas y no depende de una columna
# concreta (las de respaldo del broker la resuelven).
PROBE_CONTENT = "3A887052117671DADDA55EF4710250DB"


def probe() -> dict:
    """Fuerza acuñado y resuelve el título de sonda; devuelve la calidad real.

    Sirve para validar una cuenta ANTES de publicarla (no usa caché).
    """
    global _TOKEN, _TOKEN_AT
    with _LOCK:
        _TOKEN, _UID = _mint()
        _TOKEN_AT = time.time()
    _CACHE.pop((PROBE_CONTENT, "", 0, "0", (1,)), None)
    payload = _play(PROBE_CONTENT, "", 0, "0", [1])
    quality = [m["q"] for m in payload["medias"]]
    return {
        "ok": bool(quality),
        "userId": payload["userId"],
        "sn": payload["sn"],
        "quality": quality,
        "daysLeft": _DAYS,
        "entitled": _ENTITLED,
        "mismatch": _MISMATCH,
        "accountExpiresAt": account_expires_at(),
    }
