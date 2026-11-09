"""Calendario editorial: publica las temporadas y eventos de `themes.py`.

La fuente de verdad del contenido sigue siendo `themes.py`; este módulo solo
serializa sus ventanas para que la API pueda responder `/v1/calendar` y para que
`banners.py` sepa qué campaña debe ocupar el hero. Así el worker y la API no
duplican fechas ni prioridades.

La tabla `content_calendar` es idempotente: cada ciclo hace upsert de las
definiciones y elimina las que ya no existen. Un evento de una sola vez usa
`start_date`/`end_date`; las temporadas recurrentes usan `start_md`/`end_md`.
"""

from __future__ import annotations

import logging
from datetime import date

import themes

log = logging.getLogger("calendar")


def _md(value: tuple[int, int] | None) -> str | None:
    if not value:
        return None
    return f"{value[0]:02d}-{value[1]:02d}"


def _kind(theme: themes.Theme) -> str:
    if theme.always:
        return "evergreen"
    if theme.start_date and theme.end_date:
        return "event"
    return "season"


def _rows() -> list[tuple]:
    return [
        (
            theme.id,
            theme.title,
            theme.id,
            _kind(theme),
            _md(theme.start),
            _md(theme.end),
            theme.start_date,
            theme.end_date,
            int(theme.priority),
            bool(theme.hero),
            bool(theme.row),
            int(theme.min_titles),
            theme.badge or theme.title.upper(),
            theme.eyebrow,
            bool(theme.always),
        )
        for theme in themes.ALL_THEMES
    ]


async def sync(pool) -> int:
    """Upsert de las definiciones y poda de las que ya no existen."""
    rows = _rows()
    ids = [row[0] for row in rows]
    async with pool.acquire() as conn:
        await conn.executemany(
            """
            insert into content_calendar (
                id, title, theme_id, kind, start_md, end_md, start_date, end_date,
                priority, hero, show_row, min_titles, badge, eyebrow, always, updated_at
            ) values (
                $1, $2, $3, $4, $5, $6, $7, $8,
                $9, $10, $11, $12, $13, $14, $15, now()
            )
            on conflict (id) do update set
                title = excluded.title,
                theme_id = excluded.theme_id,
                kind = excluded.kind,
                start_md = excluded.start_md,
                end_md = excluded.end_md,
                start_date = excluded.start_date,
                end_date = excluded.end_date,
                priority = excluded.priority,
                hero = excluded.hero,
                show_row = excluded.show_row,
                min_titles = excluded.min_titles,
                badge = excluded.badge,
                eyebrow = excluded.eyebrow,
                always = excluded.always,
                updated_at = now()
            """,
            rows,
        )
        await conn.execute(
            "delete from content_calendar where id <> all($1::text[])", ids
        )
    log.info("calendario: %s entradas publicadas", len(rows))
    return len(rows)


def signature(today: date | None = None) -> str:
    """Firma de la campaña activa (para reconstruir filas solo cuando cambia)."""
    return "|".join(
        f"{theme.id}:{theme.priority}" for theme in themes.active_themes(today)
    )
