"""Worker de sincronización — fases 2/3.

Ciclo: conectores (XTV, Magis, Internet Archive) -> índice (Estrenos/Géneros)
-> enriquecimiento TMDB por lotes -> bump de versión si algo cambió.
Cada conector está protegido: uno que falle no tumba el ciclo ni a los demás.
Con SEED_DEMO=1 (solo local) inserta ejemplos si la base está vacía.
"""

import asyncio
import logging
import os
import random
from pathlib import Path

import asyncpg

import archive_ia
import epg
import grouping
import index_build
import magis
import sports
import tmdb
import xtv

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("sync")

DATABASE_URL = os.environ["DATABASE_URL"]
INTERVAL_SECONDS = int(os.environ.get("SYNC_INTERVAL_SECONDS", "21600"))
SEED_DEMO = os.environ.get("SEED_DEMO", "0") == "1"
SCHEMA_PATH = Path(__file__).with_name("schema.sql")

DEMO_CATEGORIES = [
    ("estrenos", "Estrenos", "special", 10),
    ("recomendados", "Recomendados", "special", 20),
    ("genero-terror", "Terror", "genre", 100),
    ("genero-drama", "Drama", "genre", 110),
]

DEMO_TITLES = [
    {
        "id": "ia-night-of-the-living-dead",
        "source": "ia",
        "source_id": "night_of_the_living_dead",
        "kind": "movie",
        "title": "La noche de los muertos vivientes",
        "original_title": "Night of the Living Dead",
        "year": 1968,
        "synopsis": "Título de dominio público (Internet Archive) usado como ejemplo local.",
        "duration_min": 96,
        "poster_url": "https://archive.org/services/img/night_of_the_living_dead",
        "play_ref": "ia:night_of_the_living_dead|La noche de los muertos vivientes",
        "premiere": False,
        "recommended": True,
        "categories": [("recomendados", 0), ("genero-terror", 0)],
    },
    {
        "id": "ia-the-general",
        "source": "ia",
        "source_id": "TheGeneral",
        "kind": "movie",
        "title": "El general",
        "original_title": "The General",
        "year": 1926,
        "synopsis": "Buster Keaton, dominio público (Internet Archive). Ejemplo local.",
        "duration_min": 79,
        "poster_url": "https://archive.org/services/img/TheGeneral",
        "play_ref": "ia:TheGeneral|El general",
        "premiere": True,
        "recommended": True,
        "categories": [("estrenos", 0), ("recomendados", 1), ("genero-drama", 0)],
    },
]


async def connect() -> asyncpg.Pool:
    """Espera a que Postgres esté listo (hasta ~2 minutos) antes de rendirse."""
    last: Exception | None = None
    for attempt in range(1, 31):
        try:
            return await asyncpg.create_pool(DATABASE_URL, min_size=1, max_size=4)
        except Exception as exc:  # noqa: BLE001 - se reintenta con backoff
            last = exc
            log.warning("DB no disponible (intento %s): %s", attempt, exc)
            await asyncio.sleep(min(2 * attempt, 15))
    raise RuntimeError(f"no se pudo conectar a la base: {last}")


async def apply_schema(pool: asyncpg.Pool) -> None:
    async with pool.acquire() as conn:
        await conn.execute(SCHEMA_PATH.read_text(encoding="utf-8"))
    log.info("esquema aplicado")


async def seed_demo(pool: asyncpg.Pool) -> None:
    async with pool.acquire() as conn:
        count = await conn.fetchval("select count(*) from titles")
        if count:
            log.info("seed demo: la base ya tiene %s títulos; no se toca", count)
            return
        async with conn.transaction():
            for cat_id, title, kind, position in DEMO_CATEGORIES:
                await conn.execute(
                    """
                    insert into categories (id, title, kind, position) values ($1, $2, $3, $4)
                    on conflict (id) do update
                        set title = excluded.title, kind = excluded.kind, position = excluded.position
                    """,
                    cat_id, title, kind, position,
                )
            for demo in DEMO_TITLES:
                await conn.execute(
                    """
                    insert into titles (
                        id, source, source_id, kind, title, original_title, year, synopsis,
                        duration_min, poster_url, play_ref, is_premiere, is_recommended
                    ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
                    on conflict (id) do nothing
                    """,
                    demo["id"], demo["source"], demo["source_id"], demo["kind"], demo["title"],
                    demo["original_title"], demo["year"], demo["synopsis"], demo["duration_min"],
                    demo["poster_url"], demo["play_ref"], demo["premiere"], demo["recommended"],
                )
                for cat_id, position in demo["categories"]:
                    await conn.execute(
                        """
                        insert into title_categories (title_id, category_id, position)
                        values ($1, $2, $3) on conflict do nothing
                        """,
                        demo["id"], cat_id, position,
                    )
        log.info("seed demo insertado (%s títulos)", len(DEMO_TITLES))


async def run_connectors(pool: asyncpg.Pool) -> int:
    changed = 0
    connectors = (
        ("xtv", os.environ.get("XTV_SYNC", "1") == "1", xtv.sync),
        ("magis", os.environ.get("MAGIS_SYNC", "1") == "1", magis.sync),
        ("ia", os.environ.get("IA_SYNC", "1") == "1", archive_ia.sync),
    )
    for name, enabled, runner in connectors:
        if not enabled:
            log.info("%s: deshabilitado por entorno", name)
            continue
        try:
            count = await runner(pool)
            log.info("%s: %s registros", name, count)
            changed += count or 0
        except Exception:  # noqa: BLE001 - un conector caído no tumba el ciclo
            log.exception("%s falló (se continúa con los demás)", name)
    return changed


async def bump_version(pool: asyncpg.Pool) -> None:
    async with pool.acquire() as conn:
        await conn.execute("update sync_state set version = version + 1, updated_at = now() where id = 1")


async def main() -> None:
    pool = await connect()
    try:
        await apply_schema(pool)
        if SEED_DEMO:
            await seed_demo(pool)
        # La agenda de fútbol y la guía EPG corren aparte con sus propios intervalos:
        # no tocan el índice de catálogo ni su versión.
        sports_task = asyncio.create_task(sports.loop(pool))
        epg_task = asyncio.create_task(epg.loop(pool))
        try:
            while True:
                changed = await run_connectors(pool)
                try:
                    changed += await index_build.build(pool)
                except Exception:  # noqa: BLE001
                    log.exception("el índice de estrenos/géneros falló")
                try:
                    changed += await grouping.group(pool)
                except Exception:  # noqa: BLE001
                    log.exception("el agrupador de temporadas falló")
                try:
                    changed += await tmdb.enrich_batch(pool)
                except Exception:  # noqa: BLE001
                    log.exception("el enriquecimiento TMDB falló")
                try:
                    changed += await tmdb.rematch_batch(pool)
                except Exception:  # noqa: BLE001
                    log.exception("el re-match TMDB falló")
                try:
                    changed += await tmdb.backfill_popularity(pool)
                except Exception:  # noqa: BLE001
                    log.exception("el backfill de popularidad TMDB falló")
                if changed:
                    await bump_version(pool)
                    log.info("versión del índice actualizada (%s cambios)", changed)
                jitter = 1 + random.uniform(-0.1, 0.1)
                await asyncio.sleep(INTERVAL_SECONDS * jitter)
        finally:
            sports_task.cancel()
            epg_task.cancel()
            try:
                await sports_task
            except asyncio.CancelledError:
                pass
            try:
                await epg_task
            except asyncio.CancelledError:
                pass
    finally:
        await pool.close()


if __name__ == "__main__":
    asyncio.run(main())
