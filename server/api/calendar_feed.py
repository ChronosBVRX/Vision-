"""Lectura del calendario editorial para la API.

El worker publica las definiciones en `content_calendar`; este módulo decide cuál
está activa, cuál debe ocupar el hero y cuándo termina. No contiene fechas: toda
la programación vive en `sync/themes.py` y se serializa con
`sync/editorial_calendar.py`.

La API nunca se cae por el calendario: si la tabla todavía no existe o la base
falla, devuelve una lista vacía y `/v1/banners` sigue usando su foto diaria.
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
from typing import Any, Iterable
from zoneinfo import ZoneInfo

TZ = ZoneInfo("America/Mexico_City")


def _value(row: Any, key: str, default: Any = None) -> Any:
    try:
        value = row[key]
    except (KeyError, IndexError, TypeError):
        return default
    return default if value is None else value


def _date(value: Any) -> date | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    try:
        return date.fromisoformat(str(value)[:10])
    except ValueError:
        return None


def _md(value: Any) -> tuple[int, int] | None:
    text = str(value or "").strip()
    if len(text) < 5 or "-" not in text:
        return None
    try:
        month, day = text.split("-", 1)
        return int(month), int(day)
    except (TypeError, ValueError):
        return None


def _md_of(day: date) -> tuple[int, int]:
    return day.month, day.day


def _in_window(today: date, start: tuple[int, int], end: tuple[int, int]) -> bool:
    current = today.month * 100 + today.day
    first = start[0] * 100 + start[1]
    last = end[0] * 100 + end[1]
    if first <= last:
        return first <= current <= last
    # Cruza el año (Año Nuevo).
    return current >= first or current <= last


def _entry(row: Any) -> dict:
    return {
        "id": str(_value(row, "id", "")),
        "title": str(_value(row, "title", "")),
        "theme_id": str(_value(row, "theme_id", "")),
        "kind": str(_value(row, "kind", "season")),
        "start_md": _md(_value(row, "start_md")),
        "end_md": _md(_value(row, "end_md")),
        "start_date": _date(_value(row, "start_date")),
        "end_date": _date(_value(row, "end_date")),
        "priority": int(_value(row, "priority", 0) or 0),
        "hero": bool(_value(row, "hero", False)),
        "row": bool(_value(row, "show_row", True)),
        "min_titles": int(_value(row, "min_titles", 8) or 8),
        "badge": str(_value(row, "badge", "")),
        "eyebrow": str(_value(row, "eyebrow", "")),
        "always": bool(_value(row, "always", False)),
    }


def _active(entry: dict, today: date) -> bool:
    if entry["always"]:
        return True
    if entry["start_date"] and entry["end_date"]:
        return entry["start_date"] <= today <= entry["end_date"]
    if entry["start_md"] and entry["end_md"]:
        return _in_window(today, entry["start_md"], entry["end_md"])
    return False


def _window(entry: dict, today: date) -> tuple[date | None, date | None]:
    """Inicio y fin de la ocurrencia que contiene (o empieza después de) hoy."""
    if entry["start_date"] and entry["end_date"]:
        return entry["start_date"], entry["end_date"]
    start = entry["start_md"]
    end = entry["end_md"]
    if not start or not end:
        return None, None
    first = date(today.year, start[0], start[1])
    last = date(today.year, end[0], end[1])
    if last < first:
        if _in_window(today, start, end) and _md_of(today) <= end:
            # Enero: la ventana empezó el año pasado.
            first = date(today.year - 1, start[0], start[1])
        else:
            last = date(today.year + 1, end[0], end[1])
    return first, last


def _next_start(entry: dict, today: date) -> date | None:
    if entry["start_date"]:
        return entry["start_date"] if entry["start_date"] > today else None
    start = entry["start_md"]
    if not start:
        return None
    candidate = date(today.year, start[0], start[1])
    if candidate <= today:
        candidate = date(today.year + 1, start[0], start[1])
    return candidate


def active_entries(rows: Iterable[Any], today: date) -> list[dict]:
    out = [_entry(row) for row in rows]
    return [entry for entry in out if entry["id"] and _active(entry, today)]


def current_campaign(rows: Iterable[Any], today: date) -> dict | None:
    """La campaña que debe ocupar el hero (la de mayor prioridad)."""
    candidates = [
        entry
        for entry in active_entries(rows, today)
        if entry["hero"] and entry["priority"] > 0
    ]
    if not candidates:
        return None
    candidates.sort(
        key=lambda entry: (
            -entry["priority"],
            (entry["end_date"] or date.max) - (entry["start_date"] or date.min)
            if entry["start_date"] and entry["end_date"]
            else timedelta.max,
            entry["id"],
        )
    )
    return candidates[0]


def next_campaign(rows: Iterable[Any], today: date) -> dict | None:
    candidates = []
    for row in rows:
        entry = _entry(row)
        if not entry["id"] or not entry["hero"] or entry["priority"] <= 0:
            continue
        start = _next_start(entry, today)
        if start is not None:
            candidates.append((start, -entry["priority"], entry["id"], entry))
    if not candidates:
        return None
    candidates.sort()
    return candidates[0][3]


def _payload(entry: dict, today: date) -> dict:
    start, end = _window(entry, today)
    end_at = datetime.combine(end, time(23, 59, 59), tzinfo=TZ) if end else None
    start_at = datetime.combine(start, time(0, 0, 0), tzinfo=TZ) if start else None
    return {
        "id": entry["id"],
        "title": entry["title"],
        "themeId": entry["theme_id"],
        "kind": entry["kind"],
        "priority": entry["priority"],
        "hero": entry["hero"],
        "row": entry["row"],
        "minTitles": entry["min_titles"],
        "badge": entry["badge"],
        "eyebrow": entry["eyebrow"],
        "startsAt": start_at.isoformat() if start_at else None,
        "endsAt": end_at.isoformat() if end_at else None,
        "endsAtMs": int(end_at.timestamp() * 1000) if end_at else None,
    }


def snapshot(rows: Iterable[Any], today: date | None = None) -> dict:
    day = today or datetime.now(TZ).date()
    active = active_entries(rows, day)
    campaign = current_campaign(rows, day)
    nxt = next_campaign(rows, day)
    return {
        "generatedAt": int(datetime.now(timezone.utc).timestamp() * 1000),
        "timezone": "America/Mexico_City",
        "active": [_payload(entry, day) for entry in active],
        "campaign": _payload(campaign, day) if campaign else None,
        "next": _payload(nxt, day) if nxt else None,
    }


async def load(pool) -> list[Any]:
    """Filas del calendario; una tabla ausente o una base caída no rompen el Inicio."""
    try:
        return await pool.fetch(
            "select id, title, theme_id, kind, start_md, end_md, start_date, end_date, "
            "priority, hero, show_row, min_titles, badge, eyebrow, always "
            "from content_calendar where active order by priority desc, id"
        )
    except Exception:  # noqa: BLE001
        return []


async def current(pool) -> dict | None:
    rows = await load(pool)
    today = datetime.now(TZ).date()
    entry = current_campaign(rows, today)
    return _payload(entry, today) if entry else None


async def snapshot_from_pool(pool) -> dict:
    rows = await load(pool)
    return snapshot(rows, datetime.now(TZ).date())
