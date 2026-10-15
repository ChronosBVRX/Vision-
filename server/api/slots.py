"""Pool de identidades del portal (slots) + canje por código, estilo Hydra.

Cómo lo hace la app original (verificado en el APK `com.msandroid.mobile` v6.5.7):
cada instalación se presenta con su huella de hardware (serial, MACs, android_id,
CID de la eMMC…), el portal le asigna un SN propio y opera como visitante. Nuestro
tenant (`masnew`) NO incorpora dispositivos nuevos (probado: ni SN virgen ni huella
completa de 21 campos obtienen `sn`/`userId`; el active responde 100059 / rc=1), así
que la réplica es con pool: el operador carga los SN aprovisionados por el upstream
y cada dispositivo canjea UN código por SU slot. Un slot = una sesión = nadie patea
a nadie.

Aquí vive lo puro y testeable (el harness de `server/tests` no toca la DB):
normalización de entradas, generación de códigos y las decisiones de canje/reclamo
sobre filas ya leídas. El SQL fino vive en `app.py`.
"""

import secrets

# Códigos de 8 caracteres sin ambigüedades (sin 0/O/1/I) para dictarlos por chat.
CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 8
DEVICE_KEY_MAX = 64
SN_MAX = 64

STATUSES = ("free", "assigned", "worker", "blocked")


# ---------------------------------------------------------------- normalización

def normalize_device_key(raw) -> str:
    """Llave del dispositivo: minúsculas, letras/dígitos/guion, 1..64. Fuera eso → ""."""
    key = str(raw or "").strip().lower()[:DEVICE_KEY_MAX]
    if not key:
        return ""
    return "".join(ch for ch in key if ch.isascii() and (ch.isalnum() or ch == "-")) or ""


def normalize_code(raw) -> str:
    """Código de canje: mayúsculas sin espacios; válido si 6..12 alfanuméricos."""
    code = "".join(str(raw or "").upper().split())
    if not (6 <= len(code) <= 12 and code.isascii() and code.isalnum()):
        return ""
    return code


def normalize_sn(raw) -> str:
    """SN del slot: tal cual, sin espacios, 1..64 caracteres."""
    sn = str(raw or "").strip()[:SN_MAX]
    return sn if sn and all(ord(c) >= 32 for c in sn) else ""


def generate_code(randbelow=secrets.randbelow) -> str:
    """Código nuevo de 8 caracteres (`randbelow` inyectable para tests)."""
    return "".join(CODE_ALPHABET[randbelow(len(CODE_ALPHABET))] for _ in range(CODE_LENGTH))


# ------------------------------------------------------------------- decisiones

def redeem_decision(slot, device_key: str) -> dict:
    """Qué hacer cuando un dispositivo canjea un código.

    `slot` es la fila (dict) del código o None; `device_key` ya normalizado.
    - slot None → {"ok": False, "error": "invalid"} (código inexistente)
    - slot bloqueado → {"ok": False, "error": "blocked"}
    - slot asignado a OTRO dispositivo → {"ok": False, "error": "taken"}
    - slot asignado a ESTE dispositivo (re-canjeo tras reinstalar) → ok idempotente
    - slot libre → ok (el endpoint lo marca assigned + quema el código)
    """
    if not device_key:
        return {"ok": False, "error": "device"}
    if slot is None:
        return {"ok": False, "error": "invalid"}
    if slot.get("status") == "blocked":
        return {"ok": False, "error": "blocked"}
    owner = slot.get("device_key")
    if slot.get("status") == "assigned" and owner and owner != device_key:
        return {"ok": False, "error": "taken"}
    return {"ok": True, "sn": slot.get("sn", ""), "user_id": slot.get("user_id", "") or ""}


def claim_decision(row) -> dict | None:
    """Reclamo (reinstalación): la fila asignada a este dispositivo, o None."""
    if row is None:
        return None
    return {"sn": row.get("sn", ""), "user_id": row.get("user_id", "") or ""}


def auto_decision(existing, free_slot) -> dict:
    """Auto-asignación al arrancar (cero códigos para el usuario).

    `existing`: fila ya asignada a este dispositivo (o None); `free_slot`: el slot
    libre auto-entregable más viejo (o None).
    - Con asignación previa → se devuelve (fresh=False: no consume nada).
    - Sin asignación pero con libre → se entrega (fresh=True).
    - Sin nada → {"ok": False, "error": "exhausted"} (el operador carga más).
    """
    if existing is not None:
        found = claim_decision(existing)
        if found:
            return {"ok": True, "fresh": False, **found}
    if free_slot is not None:
        return {"ok": True, "fresh": True,
                "sn": free_slot.get("sn", ""), "user_id": free_slot.get("user_id", "") or ""}
    return {"ok": False, "error": "exhausted"}


def pool_summary(rows) -> dict:
    """Conteo del pool para el operador: {free, assigned, worker, blocked}."""
    counts = {"free": 0, "assigned": 0, "worker": 0, "blocked": 0}
    for row in rows or []:
        status = row.get("status") if isinstance(row, dict) else row
        if status in counts:
            counts[status] += 1
    return counts


def worker_sn(rows, env_sn: str = "", legacy_sn: str = "") -> str:
    """SN que debe usar el worker: slot `worker` > env > legado. Jamás vacío si hay uno."""
    for row in rows or []:
        if (row.get("status") if isinstance(row, dict) else None) == "worker":
            sn = normalize_sn(row.get("sn", ""))
            if sn:
                return sn
    return normalize_sn(env_sn) or legacy_sn
