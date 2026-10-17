"""Triage de diagnóstico de campo (Fase 1).

Loop propio (como sports/epg): revisa los reportes que la app envía a `/v1/diag` y
  - huella nueva  -> alerta (webhook genérico o Telegram; si no hay config, log),
  - huella ya avisada con reportes nuevos -> REINCIDENCIA (versión distinta / regresó
    tras el autocierre / lleva 24 h repitiéndose),
  - huella sin reportes en RESOLVE_HOURS -> "resuelta" (autocierre).

Las alertas van en dos capas: primero **qué pasó en lenguaje humano** (qué vio la
persona que usa la app y qué significa) y después los **detalles técnicos completos**
(código, versión, huella, equipo, contexto y traza). Nunca se pierde información:
solo se explica antes de mostrarla.

No toca el índice ni su versión.
"""

import asyncio
import datetime as dt
import json
import logging
import os
import re
import urllib.request

log = logging.getLogger("diag")

INTERVAL = int(os.environ.get("DIAG_INTERVAL_SECONDS", "300"))
RESOLVE_HOURS = int(os.environ.get("DIAG_RESOLVE_HOURS", "48"))
WEBHOOK = os.environ.get("DIAG_ALERT_WEBHOOK", "").strip()
TG_TOKEN = os.environ.get("DIAG_TELEGRAM_TOKEN", "").strip()
TG_CHAT = os.environ.get("DIAG_TELEGRAM_CHAT", "").strip()

# Telegram corta en 4096 caracteres; se deja margen para no perder el envío.
MAX_ALERT_CHARS = 3900

# Si una huella ya alertada sigue reportándose MUCHO después en la misma versión,
# se avisa de nuevo (una vez al día) para que el problema no pase inadvertido.
NAG_HOURS = 24

_REPEAT_TITLE = {
    "regreso": "🔁 Regresó: un problema que ya se había cerrado volvió a reportarse",
    "reincidencia": "🔁 Reincidencia: un problema conocido volvió a aparecer en otra versión",
    "sigue": "🔁 Sigue ocurriendo: el mismo problema lleva un día reportándose en la misma versión",
}

_ANR_RX = re.compile(r"MAIN_BLOCKED_(\d+)s")

_KIND_TITLE = {
    "crash": "💥 Crash nuevo en Vision+: la app se cerró sola",
    "anr": "🧊 ANR nuevo en Vision+: la app se quedó congelada",
    "anomaly": "⚠️ Anomalía nueva en Vision+: algo falló sin cerrar la app",
    "feedback": "📝 Mensaje nuevo de un usuario en Vision+",
}

_KIND_SUBJECT = {
    "crash": "la app se cerraba sola",
    "anr": "la app se congelaba",
    "anomaly": "una anomalía de la app",
    "feedback": "un reporte de usuario",
}

# Explicación humana por tipo de excepción: (qué pasó, qué conviene revisar).
_CRASH_HINTS = {
    "IllegalArgumentException": (
        "Uno de los datos que llegó a la app no era válido (por ejemplo, un elemento "
        "repetido o fuera de lugar) y Android cerró la pantalla para no seguir en un "
        "estado inconsistente.",
        "la pantalla y la fase del reporte; si el detalle menciona una lista, suele ser "
        "una clave repetida.",
    ),
    "IllegalStateException": (
        "La app llegó a un estado en el que no podía continuar (por ejemplo, una pantalla "
        "o un reproductor que ya se había cerrado o se estaba reutilizando antes de tiempo).",
        "el flujo que se estaba usando (pantalla y fase) y la parte superior de la traza.",
    ),
    "NullPointerException": (
        "Faltó un dato que la app esperaba encontrar y terminó cerrando la pantalla.",
        "suele pasar cuando una respuesta del servidor llega incompleta; la pantalla y la "
        "fase del reporte.",
    ),
    "OutOfMemoryError": (
        "El equipo se quedó sin memoria disponible para la app y Android la cerró.",
        "si esa pantalla carga muchas imágenes o listas grandes; si se repite en un mismo "
        "equipo, valorar el modo ligero.",
    ),
    "UnsatisfiedLinkError": (
        "No se pudo cargar una librería nativa del reproductor de video de la app.",
        "la instalación/ABI del APK y el arranque del motor; solo el reproductor usa esas "
        "librerías.",
    ),
    "ExceptionInInitializerError": (
        "Una pieza interna de la app falló al iniciarse y arrastró el cierre.",
        "en la traza, la clase que aparece junto a <clinit> o a su inicialización.",
    ),
    "NoSuchMethodError": (
        "Una librería del motor o del sistema respondió con una versión distinta a la "
        "que la app esperaba.",
        "la librería o SDK involucrado en la traza (suele ser mezcla de versiones).",
    ),
    "ClassCastException": (
        "Un dato llegó con un formato distinto al que la app esperaba.",
        "los parsers y las respuestas del servidor en la pantalla/fase del reporte.",
    ),
    "IndexOutOfBoundsException": (
        "La app intentó leer una posición de una lista que no existía.",
        "listas vacías o datos recortados en la pantalla/fase del reporte.",
    ),
    "ArrayIndexOutOfBoundsException": (
        "La app intentó leer una posición de una lista que no existía.",
        "listas vacías o datos recortados en la pantalla/fase del reporte.",
    ),
    "SecurityException": (
        "Android bloqueó una operación por permisos o por el estado del equipo.",
        "los permisos de la función y el estado del sistema en ese momento.",
    ),
    "ActivityNotFoundException": (
        "Android no encontró la pantalla o app del sistema que se necesitaba (por "
        "ejemplo, el instalador de la actualización o el selector de archivos).",
        "el intent de la función que lo lanzó (OTA, compartir, abrir un enlace).",
    ),
    "NetworkOnMainThreadException": (
        "Se intentó usar la red desde el hilo principal (Android lo prohíbe) y la app se cerró.",
        "mover esa llamada a segundo plano en la pantalla/fase indicada.",
    ),
    "TimeoutException": (
        "Una operación tardó demasiado y se canceló, arrastrando el cierre.",
        "la red del equipo y los tiempos de espera del flujo indicado.",
    ),
    "StackOverflowError": (
        "Un proceso interno se llamó a sí mismo sin parar hasta agotar la pila.",
        "la traza repetida y el flujo de la pantalla indicada.",
    ),
}

# Explicación humana por anomalía conocida: (qué pasó, qué conviene revisar).
_ANOMALY_HINTS = {
    "HOME_ROWS_FAIL": (
        "El Inicio no pudo cargar sus filas (catálogo o red) y la app abrió con lo que "
        "ya tenía guardado, posiblemente incompleto.",
        "el índice remoto y la red del equipo; el detalle trae el error exacto.",
    ),
    "INDEX_FETCH_FAIL": (
        "No se pudo descargar el índice de títulos del servidor; la app siguió con la "
        "copia que tenía guardada.",
        "/v1/index y la conexión del equipo.",
    ),
    "SESSION_RENEW_FAIL": (
        "No se pudo renovar la sesión con el proveedor; algunas reproducciones o el "
        "catálogo pueden fallar hasta que vuelva la conexión.",
        "el estado del portal/sesión y la red; el detalle trae el error exacto.",
    ),
    "DOWNLOAD_FAIL": (
        "Una descarga no se pudo completar; el título quedó marcado con error y se "
        "puede reintentar.",
        "el detalle (red, espacio libre o el motor de descargas).",
    ),
    "ENGINE_LOAD_FAIL": (
        "El motor de video no pudo cargar; los títulos del proveedor no van a reproducir "
        "hasta reiniciar la app.",
        "la carga del SDK y el almacenamiento; el detalle trae el error exacto.",
    ),
    "PLAYBACK_HANDOFF_FAIL": (
        "La app no logró abrir el reproductor al pulsar Reproducir.",
        "el handoff y la sesión/CDN; el detalle trae el error exacto.",
    ),
    "PLAYER_SOFT_RESTART": (
        "El reproductor no recibió señal a tiempo y se reinició solo; suele ser red "
        "lenta o un CDN que tardó en responder.",
        "si se repite, la red del equipo y el estado del CDN.",
    ),
    "PLAYER_ERROR": (
        "El reproductor se detuvo por un error de reproducción (señal, licencia o red).",
        "la etapa del detalle y la red del equipo.",
    ),
    "PLAYER_SELF_HEAL": (
        "El motor de video se atascó y la app se auto-reparó rotando el motor; el aviso "
        "es informativo.",
        "si aumenta la frecuencia, la racha y el modo en el detalle.",
    ),
    "PLAYER_HEAL_APPLIED": (
        "Se aplicó el auto-reparado del reproductor (sesión y CDN renovados); el aviso "
        "es informativo.",
        "solo si la reproducción sigue fallando.",
    ),
    "FOCUS_LOST": (
        "En la TV el mando se quedó sin foco (la navegación pudo no responder) y la app "
        "no encontró la tarjeta de regreso.",
        "el regreso de foco en la pantalla indicada; el detalle trae id, intentos y entrada.",
    ),
}


def _field(row, key, default=None):
    """Lee un campo de un dict o de un Record de asyncpg sin reventar si falta."""
    try:
        value = row[key]
    except (KeyError, IndexError, TypeError):
        return default
    return default if value is None else value


def _payload_of(row) -> dict:
    """El payload viaja como dict en los tests y como texto JSON desde Postgres."""
    value = _field(row, "payload")
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except (ValueError, TypeError):
            value = {}
    return value if isinstance(value, dict) else {}


def _as_int(value) -> int:
    try:
        return int(value or 0)
    except (ValueError, TypeError):
        return 0


def _plural(n: int, one: str, many: str) -> str:
    return one if n == 1 else many


def _human_seconds(total: int) -> str:
    """23317 -> '6 h 28 min'; 184 -> '3 min 4 s'; 7 -> '7 s'."""
    if total < 60:
        return f"{total} s"
    minutes, seconds = divmod(total, 60)
    if minutes < 60:
        return f"{minutes} min {seconds} s" if seconds else f"{minutes} min"
    hours, minutes = divmod(minutes, 60)
    return f"{hours} h {minutes} min" if minutes else f"{hours} h"


def _blocked_seconds(code: str) -> int | None:
    match = _ANR_RX.search(code or "")
    return int(match.group(1)) if match else None


def _gb(mb: int) -> str:
    value = f"{mb / 1024:.1f} GB"
    return value.replace(".0 GB", " GB")


def _process_label(name: str) -> str:
    if not name:
        return ""
    return "reproductor (:x4k)" if name.endswith(":x4k") else "app principal"


def _clip(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def _crash_hint(code: str) -> tuple[str, str]:
    if code in _CRASH_HINTS:
        return _CRASH_HINTS[code]
    return (
        "La app encontró un error inesperado y Android la cerró para no dejarla en un "
        "estado inconsistente.",
        "la parte superior de la traza y la pantalla/fase del reporte.",
    )


def _anomaly_hint(code: str) -> tuple[str, str]:
    if code in _ANOMALY_HINTS:
        return _ANOMALY_HINTS[code]
    return (
        "Se registró una anomalía (la app no se cerró, pero algo falló).",
        "el detalle y la pantalla/fase del reporte.",
    )


def _device_line(row, payload: dict) -> str:
    parts = []
    device = str(_field(row, "device") or "").strip()
    android = str(_field(row, "android") or "").strip()
    if device:
        parts.append(device)
    if android:
        parts.append(android)
    network = str(payload.get("network") or "").strip()
    network = {
        "wifi": "wifi",
        "ethernet": "cable",
        "datos": "datos móviles",
        "sin-red": "sin red",
    }.get(network, network)
    if network:
        parts.append(f"red {network}")
    if payload.get("tv"):
        parts.append("Smart TV")
    ram = _as_int(payload.get("ramMb"))
    if ram:
        parts.append(f"RAM {_gb(ram)}")
    free = _as_int(payload.get("freeStorageMb"))
    if free:
        parts.append(f"almacenamiento libre {_gb(free)}")
    return "equipo: " + " · ".join(parts) if parts else ""


def _context_line(payload: dict) -> str:
    parts = []
    screen = str(payload.get("screen") or "").strip()
    phase = str(payload.get("phase") or "").strip()
    engine = str(payload.get("engine") or "").strip()
    process = _process_label(str(payload.get("process") or ""))
    if screen:
        parts.append(f"pantalla «{screen}»")
    if phase:
        parts.append(f"fase «{phase}»")
    if engine:
        parts.append(f"motor: {engine}")
    if process:
        parts.append(f"proceso: {process}")
    return " · ".join(parts)


def _last_seen(row) -> str:
    value = _field(row, "last_seen")
    if isinstance(value, dt.datetime):
        return value.strftime("%d/%m/%Y %H:%M")
    return str(value or "").strip()


def _trace(payload: dict, max_lines: int) -> list[str]:
    detail = str(payload.get("detail") or "").strip()
    if not detail:
        return []
    lines = [line.strip()[:150] for line in detail.splitlines() if line.strip()]
    return lines[:max_lines]


def _technical_lines(row, payload: dict) -> list[str]:
    kind = str(_field(row, "kind") or "anomaly")
    code = str(_field(row, "code") or "").strip() or "(sin código)"
    version = str(_field(row, "latest_version") or "").strip() or "?"
    devices = _as_int(_field(row, "devices"))
    hits = _as_int(_field(row, "hits"))
    fingerprint = str(_field(row, "fingerprint") or "").strip() or "sin-huella"

    counts = [f"v{version}"]
    if devices:
        counts.append(f"{devices} {_plural(devices, 'dispositivo', 'dispositivos')}")
    if hits:
        counts.append(f"{hits} {_plural(hits, 'vez', 'veces')}")

    lines = [
        "— Detalles técnicos —",
        f"tipo: {kind} · código: {code}",
    ]
    tech_message = str(payload.get("message") or "").strip()
    if tech_message:
        lines.append(f"mensaje: {_clip(tech_message, 300)}")
    lines += [
        f"versión: {' · '.join(counts)}",
        f"huella: {fingerprint}",
    ]
    device = _device_line(row, payload)
    if device:
        lines.append(device)
    context = _context_line(payload)
    if context:
        lines.append(context)
    last = _last_seen(row)
    if last:
        lines.append(f"último reporte: {last} (hora de México)")
    return lines


def repeat_alert_reason(resolved_at, alert_version, latest_version, hours_since_alert: float) -> str | None:
    """Decide si una huella YA alertada merece aviso de repetición (puro, testeable).

    - «regreso»: la huella se había cerrado (autocierre a las 48 h) y vuelve a reportarse.
    - «reincidencia»: reporta en una versión DIFERENTE de la avisada — el caso cruel:
      el mismo bug reaparece (o llega a un equipo nuevo) y el bot se quedaba callado.
    - «sigue»: lleva [NAG_HOURS] h o más reportándose en la misma versión.
    - None: todavía no (mismo lote de reportes de siempre, versión sin cambios).
    """
    if resolved_at is not None:
        return "regreso"
    known_version = str(alert_version or "").strip()
    latest = str(latest_version or "").strip()
    if known_version and latest and known_version != latest:
        return "reincidencia"
    if hours_since_alert >= NAG_HOURS:
        return "sigue"
    return None


def alert_text(row, headline: str | None = None) -> str:
    """Mensaje legible de una huella nueva (pura, testeable).

    Primero explica en lenguaje humano qué pasó y qué conviene revisar; después
    conserva todos los detalles técnicos (código, versión, huella, equipo,
    contexto y traza).
    """
    kind = str(_field(row, "kind") or "anomaly").lower()
    code = str(_field(row, "code") or "").strip()
    payload = _payload_of(row)

    lines = [headline or _KIND_TITLE.get(kind, f"⚠️ Alerta nueva en Vision+: {kind}")]

    if kind == "feedback":
        user_text = str(payload.get("detail") or "").strip()
        lines.append("Una persona escribió esto desde Ajustes → «Reportar un problema»:")
        lines.append("")
        lines.append(("«" + _clip(user_text, 800) + "»") if user_text else "(llegó sin texto)")
        lines.append("")
        lines.append(
            "👉 Qué revisar: el mensaje de arriba; los detalles técnicos dicen desde qué "
            "pantalla y versión se envió."
        )
    elif kind == "anr":
        blocked = _blocked_seconds(code)
        if blocked:
            lines.append(
                f"La pantalla principal se quedó sin responder ≈ {_human_seconds(blocked)}."
            )
        else:
            lines.append("La pantalla principal se quedó sin responder.")
        lines.append(
            "Android lo anotó como ANR (Application Not Responding): la app no se cerró, "
            "pero para la persona se vio trabada o congelada."
        )
        if blocked and blocked >= 600:
            lines.append(
                "Un bloqueo tan largo suele coincidir con la pantalla apagada o el equipo "
                "suspendido, no necesariamente con un cuelgue real de todo ese tiempo."
            )
        lines.append(
            "👉 Qué revisar: la traza de abajo muestra qué estaba ejecutando el hilo "
            "principal cuando se trabó."
        )
    elif kind == "crash":
        what, review = _crash_hint(code)
        lines.append(what)
        system_message = str(payload.get("message") or "").strip()
        if system_message:
            lines.append(f"Mensaje: {_clip(system_message, 240)}")
        lines.append(f"👉 Qué revisar: {review}")
    else:
        what, review = _anomaly_hint(code)
        lines.append(what)
        lines.append(f"👉 Qué revisar: {review}")

    detail = str(payload.get("detail") or "").strip()
    if kind == "anomaly" and detail:
        lines.append(f"detalle: {_clip(detail.splitlines()[0], 180)}")

    lines.append("")
    lines.extend(_technical_lines(row, payload))

    if kind in ("crash", "anr"):
        frames = _trace(payload, 5 if kind == "anr" else 4)
        if frames:
            lines.append("traza (primeras líneas):")
            lines.extend(f"· {frame}" for frame in frames)

    return _clip("\n".join(lines), MAX_ALERT_CHARS)


def repeat_text(row, reason: str) -> str:
    """Mensaje humano de una huella CONOCIDA que vuelve a reportarse.

    Reutiliza [alert_text] con el titular de repetición («reincidencia», «regreso»,
    «sigue») y conserva íntegras las dos capas: humano + técnico.
    """
    return alert_text(row, headline=_REPEAT_TITLE.get(reason, _REPEAT_TITLE["sigue"]))


def resolved_text(row) -> str:
    """Mensaje humano cuando una huella deja de reportarse."""
    kind = str(_field(row, "kind") or "").lower()
    code = str(_field(row, "code") or "").strip()
    version = str(_field(row, "latest_version") or "").strip() or "?"
    fingerprint = str(_field(row, "fingerprint") or "").strip() or "sin-huella"

    subject = _KIND_SUBJECT.get(kind, "un problema")
    extra = ""
    if kind == "crash" and code:
        extra = f" — crash {code}"
    elif kind == "anr":
        blocked = _blocked_seconds(code)
        extra = f" — ANR de {_human_seconds(blocked)}" if blocked else " — ANR"
    elif kind == "anomaly" and code:
        extra = f" — {code}"

    return (
        f"✅ Dejó de repetirse: {subject}{extra}\n"
        f"Sin reportes nuevos en {RESOLVE_HOURS} h, así que se cierra el aviso.\n"
        f"— Técnico —\n"
        f"versión vista: v{version} · huella: {fingerprint}"
    )


def _post(url: str, payload: dict) -> None:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        resp.read()


def send_alert(text: str) -> None:
    """Telegram si hay token/chat; si no, webhook genérico; si no, al log."""
    text = _clip(text, MAX_ALERT_CHARS)
    try:
        if TG_TOKEN and TG_CHAT:
            _post(f"https://api.telegram.org/bot{TG_TOKEN}/sendMessage",
                  {"chat_id": TG_CHAT, "text": text, "disable_web_page_preview": True})
        elif WEBHOOK:
            _post(WEBHOOK, {"text": text})
        else:
            log.warning("alerta de diagnóstico (sin webhook configurado): %s", text.replace("\n", " | "))
    except Exception:  # noqa: BLE001 - una alerta caída no debe tumbar el triage
        log.exception("no se pudo enviar la alerta de diagnóstico")


async def triage(pool) -> int:
    """Marca huellas nuevas (alerta) y resueltas (sin reportes en RESOLVE_HOURS)."""
    sent = 0
    async with pool.acquire() as conn:
        new_rows = await conn.fetch(
            """
            select r.fingerprint,
                   (array_agg(r.kind order by r.created_at desc))[1] as kind,
                   (array_agg(r.code order by r.created_at desc))[1] as code,
                   (array_agg(r.app_version order by r.created_at desc))[1] as latest_version,
                   (array_agg(r.device order by r.created_at desc))[1] as device,
                   (array_agg(r.android order by r.created_at desc))[1] as android,
                   (array_agg(r.payload order by r.created_at desc))[1] as payload,
                   count(*) as hits,
                   count(distinct r.device) as devices,
                   to_char(max(r.created_at) at time zone 'America/Mexico_City',
                           'DD/MM/YYYY HH24:MI') as last_seen
            from diag_reports r
            left join diag_alert_state s on s.fingerprint = r.fingerprint
            where s.fingerprint is null
            group by r.fingerprint
            order by max(r.created_at) desc
            """
        )
        for row in new_rows:
            await conn.execute(
                "insert into diag_alert_state (fingerprint, alert_version) values ($1, $2) on conflict do nothing",
                row["fingerprint"], str(row["latest_version"] or ""),
            )
            await asyncio.to_thread(send_alert, alert_text(row))
            sent += 1
        # REINCIDENCIAS: reportes que llegaron DESPUÉS de la última alerta de una huella
        # ya conocida. Antes el bot avisaba una sola vez y jamás repetía: así un bug
        # fixed corría igual en una versión vieja (o en un equipo nuevo) sin que se
        # supiera. Ahora avisa cuando (a) la huella estaba cerrada y regresó, (b) la
        # versión cambió respecto de la avisada o (c) lleva 24 h repitiéndose.
        repeats = await conn.fetch(
            """
            select r.fingerprint,
                   (array_agg(r.kind order by r.created_at desc))[1] as kind,
                   (array_agg(r.code order by r.created_at desc))[1] as code,
                   (array_agg(r.app_version order by r.created_at desc))[1] as latest_version,
                   (array_agg(r.device order by r.created_at desc))[1] as device,
                   (array_agg(r.android order by r.created_at desc))[1] as android,
                   (array_agg(r.payload order by r.created_at desc))[1] as payload,
                   count(*) as hits,
                   count(distinct r.device) as devices,
                   to_char(max(r.created_at) at time zone 'America/Mexico_City',
                           'DD/MM/YYYY HH24:MI') as last_seen,
                   s.alert_version as alert_version,
                   s.resolved_at as resolved_at,
                   extract(epoch from (now() - s.alerted_at)) / 3600.0 as hours_since_alert
            from diag_reports r
            join diag_alert_state s on s.fingerprint = r.fingerprint
            where r.created_at > s.alerted_at
            group by r.fingerprint, s.alert_version, s.resolved_at, s.alerted_at
            order by max(r.created_at) desc
            """
        )
        for row in repeats:
            reason = repeat_alert_reason(
                row["resolved_at"], row["alert_version"],
                str(row["latest_version"] or ""), float(row["hours_since_alert"] or 0.0),
            )
            if not reason:
                # Huellas previas a la columna `alert_version`: se fija en silencio una vez
                # para que a partir de aquí la escalación de versión sí compare.
                if row["alert_version"] in (None, ""):
                    await conn.execute(
                        "update diag_alert_state set alert_version = $2 where fingerprint = $1",
                        row["fingerprint"], str(row["latest_version"] or ""),
                    )
                continue
            await asyncio.to_thread(send_alert, repeat_text(row, reason))
            # La alerta se vuelve el universo nuevo: así cada rebrote avisa UNA vez y
            # el autocierre a las 48 h vuelve a armar desde aquí.
            await conn.execute(
                """
                update diag_alert_state
                   set alerted_at = now(), alert_version = $2, resolved_at = null
                 where fingerprint = $1
                """,
                row["fingerprint"], str(row["latest_version"] or ""),
            )
            sent += 1
        # Un solo statement: se cierra y se devuelve el tipo/código para el mensaje humano,
        # sin ventana de carrera con un reporte que llegue en ese instante.
        resolved = await conn.fetch(
            """
            with resolved as (
                update diag_alert_state s set resolved_at = now()
                where s.resolved_at is null
                  and not exists (
                      select 1 from diag_reports r
                      where r.fingerprint = s.fingerprint
                        and r.created_at > now() - make_interval(hours => $1)
                  )
                returning s.fingerprint
            )
            select resolved.fingerprint,
                   (select r.kind from diag_reports r
                     where r.fingerprint = resolved.fingerprint
                     order by r.created_at desc limit 1) as kind,
                   (select r.code from diag_reports r
                     where r.fingerprint = resolved.fingerprint
                     order by r.created_at desc limit 1) as code,
                   (select r.app_version from diag_reports r
                     where r.fingerprint = resolved.fingerprint
                     order by r.created_at desc limit 1) as latest_version
            from resolved
            """,
            RESOLVE_HOURS,
        )
        for row in resolved:
            await asyncio.to_thread(send_alert, resolved_text(row))
            sent += 1
    return sent


async def loop(pool) -> None:
    while True:
        try:
            await triage(pool)
        except Exception:  # noqa: BLE001 - el triage no tumba el worker
            log.exception("triage de diagnóstico falló")
        await asyncio.sleep(INTERVAL)
