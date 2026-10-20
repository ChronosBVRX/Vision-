"""Espejo de capítulos/temporadas — lado del API (puro, sin red).

El worker guarda en `episode_mirror` la lista de capítulos que deu el portal
(una sola vez, con SU sesión). El endpoint `GET /v1/episodes?ref=xtv:<cid>`
sirve esa copia si está fresca y, si falta o está vencida, responde vacía y
ENCOLA la baja: la app cae al portal como siempre. El espejo solo puede
mejorar, jamás empeorar.

El payload guarda los campos crudos que el parser de la app consume
(`X4KApi.parseEpisodePrograms`: contentId/name/seriesNumber/episodeNumber):
desduplicar, ordenar y normalizar el nombre queda en la app — una sola
implementación para ambas rutas, cero divergencia.
"""

import json
import os
import re
import time

EPISODES_TTL_HOURS = float(os.environ.get("EPISODES_TTL_HOURS", "48") or "48")
# Mientras la ref siga pendiente de baja ( breve), las aperturas repetidas de la
# misma ficha no vuelven a escribir en la cola.
EPISODES_REQUEST_COOLDOWN_MINUTES = float(os.environ.get("EPISODES_REQUEST_COOLDOWN_MINUTES", "10") or "10")
# Reintento de copias fallidas: espaciado y acotado por `attempts` (el techo del
# worker). Una ficha abierta mil veces jamás debe significar mil golpes al portal.
EPISODES_FAILED_RETRY_HOURS = float(os.environ.get("EPISODES_FAILED_RETRY_HOURS", "24") or "24")
EPISODES_MAX_ATTEMPTS = int(os.environ.get("EPISODES_MAX_ATTEMPTS", "6") or "6")

# Solo cids de XTV (números/alnum pelados o con el prefijo del índice). Una ref de
# otra fuente (`magis:…`, `ia:…`) jamás se espeja: el portal solo entiende las suyas.
_REF_RX = re.compile(r"^(?:xtv:)?[1-9][0-9A-Za-z_\-]{0,63}$")


def valid_ref(raw) -> str | None:
    """Normaliza la ref del espejo a `xtv:<cid>` (acepta el cid pelado). None si es basura."""
    text = (raw or "").strip()
    if not _REF_RX.match(text):
        return None
    return text if text.startswith("xtv:") else f"xtv:{text}"


def age_seconds(stamp, now_ts: float) -> float:
    return now_ts - (stamp.timestamp() if stamp else 0.0)


def is_fresh(row, now_ts: float, ttl_hours: float | None = None) -> bool:
    """Servible al instante = fila ok (con lista) y edad < TTL."""
    if row is None:
        return False
    ttl = ttl_hours if ttl_hours is not None else EPISODES_TTL_HOURS
    return (row["status"] or "") == "ok" and age_seconds(row["fetched_at"], now_ts) < ttl * 3600.0


def needs_enqueue(row, now_ts: float, ttl_hours: float | None = None) -> bool:
    """¿Vale la pena pedirle esta ref al worker? Puro, con cooldown y techos.

    - fila ausente → sí (primera vez).
    - ok fresca → no (nada que hacer). ok vencida → sí (refresco ABIERTO: la app
      de HOY cae al portal y sigue siendo fresca de verdad).
    - pendiente reciente → no (el worker ya la tiene en cola).
    - fallida → sí solo si el techo de intentos no se agotó y pasó el enfriamiento.
    """
    if row is None:
        return True
    status = (row["status"] or "").strip()
    if status == "ok":
        return not is_fresh(row, now_ts, ttl_hours)
    if status == "pending":
        return age_seconds(row.get("requested_at"), now_ts) > EPISODES_REQUEST_COOLDOWN_MINUTES * 60.0
    return (
        int(row.get("attempts") or 0) < EPISODES_MAX_ATTEMPTS
        and age_seconds(row.get("updated_at"), now_ts) > EPISODES_FAILED_RETRY_HOURS * 3600.0
    )


def payload(ref: str, row, now_ts: float | None = None) -> dict:
    """Cuerpo del endpoint: la copia fresca viaja completa; lo demás es «miss» honesto."""
    ts = time.time() if now_ts is None else now_ts
    fresh = is_fresh(row, ts)
    # asyncpg entrega `jsonb` como str: si viene texto, decódelo UNA vez aquí
    # (un str viajando como `episodes` sería una lista de caracteres en la app).
    raw = row["payload"] if fresh else None
    if isinstance(raw, str):
        try:
            raw = json.loads(raw)
        except Exception:  # noqa: BLE001 - payload corrupto = miss honesto
            raw = None
    episodes = list(raw) if isinstance(raw, list) else []
    if fresh and not episodes:
        # Copia fresca pero ilegible/vacía: «miss» honesto (la app cae al portal).
        fresh = False
        fetched = None
    else:
        fetched = row["fetched_at"].isoformat() if fresh and row["fetched_at"] else None
    return {
        "ref": ref,
        "status": "ok" if fresh else "miss",
        "episodes": episodes,
        "fetchedAt": fetched,
        "ttlHours": EPISODES_TTL_HOURS,
    }


def enrich(episodes: list[dict], meta_rows) -> list[dict]:
    """Pega nombre real, sinopsis y miniatura de TMDB a cada capítulo (por número).

    El portal solo trae un nombre técnico («Serie T2_05»): la tarjeta «Siguiente
    capítulo» se veía vacía. TMDB da el nombre de verdad y una sinopsis corta; se
    guarda en `episode_meta` y aquí solo se cruza. Sin fila para ese capítulo, los
    campos van vacíos y la app enseña lo que el portal sí trae (nunca empeora).
    Puro: la DB la consulta el endpoint.
    """
    if not episodes:
        return []
    by_ep: dict[int, dict] = {}
    for row in meta_rows or []:
        try:
            number = int(row.get("episode") or 0)
        except (TypeError, ValueError):
            continue
        if number > 0:
            by_ep[number] = row
    out: list[dict] = []
    for ep in episodes:
        item = dict(ep)
        try:
            number = int(item.get("seriesNumber") or 0)
        except (TypeError, ValueError):
            number = 0
        meta = by_ep.get(number) or {}
        item["title"] = str(meta.get("name") or "")
        item["overview"] = str(meta.get("overview") or "")
        item["still"] = str(meta.get("still") or "")
        out.append(item)
    return out
