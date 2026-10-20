"""Espejo de capítulos/temporadas del portal XTV (worker).

La app pide los capítulos de una serie al API (`GET /v1/episodes?ref=xtv:<cid>`);
el API sirve la copia de `episode_mirror` si está fresca y, si falta, ENCOLA la
petición. Este módulo drena esa cola en su propio loop corto: baja la lista del
portal con la sesión que el worker ya mantiene en `portal_sessions`.

Reglas de oro (las mismas de `api/portal.py`, sin excepciones):
- **Jamás acuña sesión**: si el token no sirve, la cola se re-agenda (15 min) y
  el ciclo del worker se encarga de renovar. Acuñar desde aquí patearía a los
  equipos (cada mint es expulsar a otra sesión con el mismo SN).
- **Presupuesto**: `EPISODES_MAX_PER_CYCLE` refs por pasada, con pausa corta
  entre cada una. La ficha de un usuario jamás debe significar un golpe al
  portal por sí sola.
- **Techos**: una ref fallida se reintenta espaciada y con tope de intentos
  (`EPISODES_MAX_ATTEMPTS`); una película (lista de 1) se marca fallida y quieto.

El payload guarda los 4 campos crudos que el parser de la app consume
(`X4KApi.parseEpisodePrograms`): desduplicado, orden y normalización del nombre
quedan en la app — una sola implementación, cero divergencia entre rutas.
"""

import asyncio
import json
import logging
import os
import re
from typing import Any

import asyncpg

from xtv import XtvClient, XTV_USER_ID

log = logging.getLogger("sync.episodes")

ENABLED = os.environ.get("EPISODES_SYNC", "1") == "1"
INTERVAL = float(os.environ.get("EPISODES_INTERVAL_SECONDS", "90") or "90")
MAX_PER_CYCLE = int(os.environ.get("EPISODES_MAX_PER_CYCLE", "30") or "30")
# Pausa entre refs de la pasada: el portal tarda 0.2–0.7 s por llamada; nos
# quedamos lejos de cualquier límite razonable de tasa.
REF_DELAY_SECONDS = float(os.environ.get("EPISODES_DELAY_SECONDS", "0.25") or "0.25")
RETRY_HOURS = float(os.environ.get("EPISODES_RETRY_HOURS", "6") or "6")
MAX_ATTEMPTS = int(os.environ.get("EPISODES_MAX_ATTEMPTS", "6") or "6")
SESSION_BACKOFF_S = float(os.environ.get("EPISODES_SESSION_BACKOFF_SECONDS", "900") or "900")
NET_BACKOFF_S = float(os.environ.get("EPISODES_NET_BACKOFF_SECONDS", "300") or "300")

# Códigos del portal que significan «esta sesión ya no sirve» (los mismos que la
# app trata en SESSION_CODE_RX + la patada clásica aaa100083). Con uno de estos
# la cola entera se re-agenda: renovar NO es cosa de este loop.
_SESSION_RX = re.compile(
    r"portal\s*(?:10000[1-5]|20000[0-2]|30000[1-2])|aaa100083|其他设备登录|账号已经在其他设备"
)
# Contenido que el portal declara inexistente (100006 剧集=colección de capítulos,
# 100008 节目=programa). Es determinista: la ficha la pediría mil veces y el portal
# respondería lo mismo — falla terminal, sin reintentos.
_CONTENT_RX = re.compile(r"portal\s*(?:100006|100008|100059)")


def classify_error(text: str) -> str:
    """'session' si el portal rechazó el token; 'content' si el ítem no existe;
    'net' en cualquier otro caso."""
    text = text or ""
    if _SESSION_RX.search(text):
        return "session"
    if _CONTENT_RX.search(text):
        return "content"
    return "net"


def extract_programs(asset: dict) -> list[dict]:
    """Aplana `assetData.simpleProgramList` a los campos que consume la app.

    Puro: solo recorta campos, no decide nada (dedupe/orden quedan en la app).
    Acepta la respuesta completa (con `assetData` dentro) o el assetData suelto.
    El orden del portal se preserva; sin contentId, fuera.
    """
    body = dict(asset or {})
    inner = body.get("assetData")
    if isinstance(inner, dict):
        body = inner
    rows: list[dict] = []
    for item in body.get("simpleProgramList") or []:
        if not isinstance(item, dict):
            continue
        cid = str(item.get("contentId") or "").strip()
        if not cid:
            continue
        rows.append({
            "contentId": cid,
            "name": str(item.get("name") or ""),
            "seriesNumber": int(item.get("seriesNumber") or 0),
            "episodeNumber": int(item.get("episodeNumber") or 0),
        })
    return rows


def _item_data(client: XtvClient, cid: str, asset_type: str) -> dict:
    """Ficha del portal (la MISMA llamada que la app usa, `v4/getItemData`)."""
    return client._post("/api/portalCore/v4/getItemData", {  # noqa: SLF001 - familia propia
        "userToken": client.user_token, "userId": client.user_id, "portalCode": "masnew",
        "contentId": cid, "type": asset_type, "sortType": "", "language": "es",
    })


def fetch_programs(client: Any, cid: str) -> tuple[str, list[dict]]:
    """Baja la lista de capítulos con la MISMA semántica de `X4KApi.episodeProgramsFor`:
    type 0 primero y type 1 de respaldo, tolerando el fallo de cada tipo (la app usa
    `runCatching` + `continue`; el portal a veces sirve la ficha sin lista). Devuelve
    (modo, filas): `ok` con 2+ (es serie), `single` con 1 (casi siempre película) y
    `empty` sin lista. Si los DOS tipos fallan, sube el error más importante (la
    sesión manda sobre el resto) para que la cola decida."""
    rows: list[dict] = []
    first_error: Exception | None = None
    for asset_type in ("0", "1"):
        try:
            got = extract_programs(_item_data(client, cid, asset_type))
        except Exception as exc:  # noqa: BLE001 - mismo trato que la app
            if first_error is None or classify_error(str(exc)) == "session":
                first_error = exc
            continue
        if len(got) > len(rows):
            rows = got
        if len(rows) >= 2:
            return "ok", rows
    if len(rows) >= 2:
        return "ok", rows
    if len(rows) == 1:
        return "single", rows
    if first_error is not None:
        raise first_error
    return "empty", []


def claims(rows, budget: int) -> list:
    """Reparte el presupuesto de la pasada en orden de pedido (puro, para tests)."""
    return [r["ref"] for r in list(rows)[:max(0, budget)]]


async def drain(pool: asyncpg.Pool) -> int:
    """Una pasada completa sobre la cola; devuelve refs procesadas."""
    rows = await pool.fetch(
        """
        select ref, attempts from episode_mirror
        where status = 'pending' and (next_try_at is null or next_try_at <= now())
        order by requested_at
        limit $1
        """,
        MAX_PER_CYCLE,
    )
    refs = claims(rows, MAX_PER_CYCLE)
    if not refs:
        return 0
    # La sesión del worker vive en la base (la mantiene el ciclo XTV; aquí solo
    # se lee). Sin token no se martilla: la cola atrasa 15 min y ya.
    row = await pool.fetchrow(
        """
        select (select user_token from portal_sessions where portal = 'xtv') as token,
               (select user_id from device_slots where status = 'worker' limit 1) as user_id
        """
    )
    token = ((row["token"] if row else "") or "").strip()
    user_id = ((row["user_id"] if row else "") or "").strip() or XTV_USER_ID
    if not token:
        await pool.execute(
            "update episode_mirror set next_try_at = now() + make_interval(secs => $1) "
            "where status = 'pending' and ref = any($2::text[])",
            SESSION_BACKOFF_S, refs,
        )
        log.info("episodios: sin sesión del worker en la base; %s refs esperan", len(refs))
        return 0

    client = XtvClient(token, user_id)
    ok = failed = 0
    for i, ref in enumerate(refs):
        attempts = next((int(r["attempts"]) for r in rows if r["ref"] == ref), 0)
        cid = ref[4:] if ref.lower().startswith("xtv:") else ref
        try:
            mode, items = await asyncio.to_thread(fetch_programs, client, cid)
        except Exception as exc:  # noqa: BLE001
            cls = classify_error(str(exc))
            detail = f"{cls}: {str(exc)[:160]}"
            if cls == "session":
                # La sesión murió A MEDIA pasada: nada de martillar con el resto
                # ni volver pronto (el ciclo XTV renueva la sesión).
                await pool.execute(
                    "update episode_mirror set next_try_at = now() + make_interval(secs => $1), "
                    "last_error = $2, updated_at = now() "
                    "where status = 'pending' and ref = any($3::text[])",
                    SESSION_BACKOFF_S, detail, refs[i:],
                )
                log.warning(
                    "episodios: sesión rechazada; cola re-agenda (%s refs restantes)",
                    len(refs) - i,
                )
                return ok + failed
            attempts += 1
            if cls == "content" or attempts >= MAX_ATTEMPTS:
                # El portal declara que el PROGRAMA no existe (o se agotó el techo):
                # falla terminal — la app cae al portal y ahí lo ve solo.
                await pool.execute(
                    "update episode_mirror set status = 'failed', attempts = $2, "
                    "last_error = $3, next_try_at = null, updated_at = now() where ref = $1",
                    ref, attempts, detail,
                )
            else:
                backoff = RETRY_HOURS * 3600.0 if attempts >= 3 else NET_BACKOFF_S
                await pool.execute(
                    "update episode_mirror set attempts = $2, "
                    "next_try_at = now() + make_interval(secs => $3), "
                    "last_error = $4, updated_at = now() where ref = $1",
                    ref, attempts, backoff, detail,
                )
            failed += 1
        else:
            if mode == "ok":
                await pool.execute(
                    """
                    insert into episode_mirror (ref, payload, episodes, status, fetched_at,
                                                updated_at, attempts, last_error, next_try_at)
                    values ($1, $2::jsonb, $3, 'ok', now(), now(), 0, '', null)
                    on conflict (ref) do update set
                        payload = excluded.payload, episodes = excluded.episodes,
                        status = 'ok', fetched_at = now(), updated_at = now(),
                        attempts = 0, last_error = '', next_try_at = null
                    """,
                    ref, json.dumps(items, separators=(",", ":"), ensure_ascii=False), len(items),
                )
                ok += 1
            else:
                # `single` = película (el parser de la app exige 2+; no hay nada que
                # espejar) · `empty` = el portal no trae lista… aún. Techo de intentos.
                await pool.execute(
                    """
                    insert into episode_mirror (ref, status, last_error, attempts, updated_at, next_try_at)
                    values ($1, 'failed', $2, 1, now(), null)
                    on conflict (ref) do update set
                        status = 'failed', last_error = excluded.last_error,
                        attempts = episode_mirror.attempts + 1,
                        next_try_at = null, updated_at = now()
                    """,
                    ref, f"portal sin lista ({mode})",
                )
                failed += 1
        if i < len(refs) - 1 and REF_DELAY_SECONDS > 0:
            await asyncio.sleep(REF_DELAY_SECONDS)
    log.info("episodios: %s ok / %s fallidas de %s en cola", ok, failed, len(refs))
    return ok + failed


async def loop(pool: asyncpg.Pool) -> None:
    """Loop propio (cada `EPISODES_INTERVAL_SECONDS`, default 90 s), independiente
    del ciclo mayor del worker (6 h): una serie nueva debe quedar espejada en
    minutos, no en horas. Un ciclo fallido jamás tumba el loop."""
    while True:
        try:
            if ENABLED:
                await drain(pool)
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001
            log.exception("episodios: el drenaje falló")
        await asyncio.sleep(INTERVAL)


async def run_once(pool: asyncpg.Pool) -> None:
    """Pasada manual (deploys/operación): drena la cola entera, sin presupuesto."""
    total = 0
    while True:
        done = await drain(pool)
        if done == 0:
            break
        total += done
    if total:
        log.info("episodios: pasada manual completada (%s refs)", total)


if __name__ == "__main__":  # operación a mano: `docker compose exec sync python episodes_mirror.py`
    database_url = os.environ["DATABASE_URL"]

    async def _standalone() -> None:
        p = await asyncpg.create_pool(database_url, min_size=1, max_size=2)
        try:
            await run_once(p)
        finally:
            await p.close()

    asyncio.run(_standalone())
