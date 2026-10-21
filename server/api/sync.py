"""Continuidad entre equipos (Fase B): historial, favoritos y vistos.

Un código corto compartido (8 caracteres sin ambigüedades) identifica al grupo de
equipos. El servidor **nunca** guarda el código en claro: guarda su SHA-256. Cada
elemento es una fila con su `client_at` (last-write-wins por clave) y un
`server_seq` que permite un pull incremental sin depender del reloj del equipo.

Aquí vive lo puro y testeable (el harness de `server/tests` no toca la DB):
normalización del código, validación/saneo de los elementos y la forma de la
respuesta. El SQL fino vive en `app.py`.
"""

import hashlib
import json
import re
import time

KINDS = ("resume", "fav", "later", "watched")
CODE_LENGTH = 8
CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
MAX_ITEMS = 200
MAX_KEY = 200
MAX_DATA = 16_000
MAX_BODY = 64 * 1024

_CODE_RX = re.compile(r"^[A-Z0-9]{6,16}$")


def normalize_code(raw) -> str:
    """Código compartido: sin espacios ni guiones, mayúsculas; 6..16 alfanuméricos."""
    code = re.sub(r"[\s\-_]", "", str(raw or "")).upper()
    if not _CODE_RX.match(code):
        return ""
    return code


def code_id(code: str) -> str:
    """Identificador que se guarda en la DB (SHA-256 del código normalizado)."""
    clean = normalize_code(code)
    if not clean:
        return ""
    return hashlib.sha256(clean.encode("utf-8")).hexdigest()


def clean_text(value, limit: int) -> str:
    text = str(value or "").strip().replace("\x00", "")
    return text[:limit]


def clean_at(value, now_ms: int) -> int:
    """Milisegundos del cliente, acotados para que un reloj loco no gane para siempre."""
    try:
        at = int(value or 0)
    except (TypeError, ValueError):
        at = 0
    if at <= 0:
        return now_ms
    # Nada del futuro lejano (una hora de margen) ni de hace más de 180 días.
    return max(now_ms - 180 * 24 * 60 * 60 * 1000, min(at, now_ms + 60 * 60 * 1000))


def clean_data(value):
    """Payload JSON del elemento: objeto/dict, o None. Se rechaza si es gigante."""
    if value is None:
        return None
    if not isinstance(value, dict):
        return None
    try:
        text = json.dumps(value, ensure_ascii=False)
    except (TypeError, ValueError):
        return None
    if len(text.encode("utf-8")) > MAX_DATA:
        return None
    return value


def clean_item(raw, now_ms: int):
    """Un elemento del push → dict saneado, o None si no es válido."""
    if not isinstance(raw, dict):
        return None
    kind = clean_text(raw.get("k"), 16).lower()
    if kind not in KINDS:
        return None
    key = clean_text(raw.get("key"), MAX_KEY)
    if not key:
        return None
    data = clean_data(raw.get("data"))
    if raw.get("data") is not None and data is None:
        return None
    return {
        "k": kind,
        "key": key,
        "at": clean_at(raw.get("at"), now_ms),
        "d": bool(raw.get("d")),
        "data": data,
    }


def clean_items(raw, now_ms: int) -> list[dict]:
    """Lista del push: solo elementos válidos, con tope de válidos (no de intentos)."""
    if not isinstance(raw, list):
        return []
    out = []
    for item in raw:
        clean = clean_item(item, now_ms)
        if clean is not None:
            out.append(clean)
            if len(out) >= MAX_ITEMS:
                break
    return out


def row_item(row) -> dict:
    """Fila de Postgres → elemento de la respuesta (payload jsonb decodificado)."""
    payload = row["payload"]
    if isinstance(payload, str):
        try:
            payload = json.loads(payload)
        except (TypeError, ValueError):
            payload = None
    deleted = bool(row["deleted"])
    at = row["client_at"]
    at_ms = int(at.timestamp() * 1000) if hasattr(at, "timestamp") else int(at or 0)
    return {
        "k": row["kind"],
        "key": row["item_key"],
        "at": at_ms,
        "d": deleted,
        "data": None if deleted else payload,
    }


def response_items(rows) -> list[dict]:
    return [row_item(row) for row in rows]


def now_ms() -> int:
    return int(time.time() * 1000)
