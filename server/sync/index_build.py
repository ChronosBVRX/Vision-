"""Categorías calculadas sobre lo sincronizado: Estrenos y géneros.

- Estrenos: títulos con año >= actual - 1 (el año sale de `releaseTime` del portal
  o del enriquecimiento).
- Géneros: una categoría `genero-<slug>` por cada género presente en los títulos,
  con los enlaces en bloque (una sola sentencia, para no reventar la versión).
"""

import logging
import re
from datetime import datetime

from xtv import CAT_SQL, LINK_SQL, slug

log = logging.getLogger("index")

BRANDS = ("netflix", "hbo", "disney", "prime video", "apple tv", "hulu", "paramount", "max ")

KIND_RANK = {"special": 0, "genre": 1, "source": 2}


ALIASES = {
    "horror": "terror",
    "fantastico": "fantasia",
    "acao": "accion",
    "sci-fi": "ciencia-ficcion",
    "science-fiction": "ciencia-ficcion",
}

# Secciones del riel: cada una con sus propios géneros (sin contenido genérico
# mezclado). Novelas = series de telenovela (repisa propia o género Telenovela).
SECTIONS = {
    "movies": "Películas",
    "series": "Series",
    "novelas": "Novelas",
    "anime": "Anime",
}

SECTION_MIN_TITLES = 5


def _canonical_key(title: str) -> str:
    """La clave de agrupación: mismo nombre (sin acentos ni mayúsculas) = misma categoría.
    Las marcas se unen quitando el sufijo del tipo («Netflix Movie» + «Netflix Series» → «netflix»)."""
    text = (title or "").strip().lower()
    if any(brand in text for brand in BRANDS):
        for suffix in (" movie", " series", " películas", " peliculas", " pelicula", " película"):
            if text.endswith(suffix):
                text = text[: -len(suffix)]
                break
    key = slug(text)
    return ALIASES.get(key, key)


async def normalize(pool) -> int:
    """Fusiona categorías repetidas (mismo nombre en varias fuentes) en una sola y deja
    el orden curado: especiales, luego géneros y al final las colecciones de cada portal."""
    merged = 0
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """
            select c.id, c.title, c.kind, count(tc.title_id) as titles
            from categories c
            left join title_categories tc on tc.category_id = c.id
            where c.kind != 'section'
            group by c.id, c.title, c.kind
            """
        )
        groups: dict[str, list] = {}
        for row in rows:
            groups.setdefault(_canonical_key(row["title"]), []).append(dict(row))

        for cats in groups.values():
            if len(cats) < 2:
                continue
            # El canónico: especial > género > fuente, y dentro de eso el que más títulos trae.
            cats.sort(key=lambda c: (KIND_RANK.get(c["kind"], 3), -c["titles"]))
            canonical = cats[0]["id"]
            duplicates = [c["id"] for c in cats[1:] if c["id"] != canonical]
            if not duplicates:
                continue
            await conn.execute(
                """
                insert into title_categories (title_id, category_id, position)
                select title_id, $1, position from title_categories where category_id = any($2::text[])
                on conflict do nothing
                """,
                canonical,
                duplicates,
            )
            await conn.execute(
                "delete from title_categories where category_id = any($1::text[])", duplicates
            )
            await conn.execute("delete from categories where id = any($1::text[])", duplicates)
            merged += len(duplicates)

        # Orden curado: especiales arriba, géneros después, colecciones al final.
        await conn.execute("update categories set position = 1 where id = 'estrenos'")
        await conn.execute("update categories set position = 2 where id = 'recomendados'")
        await conn.execute("update categories set position = 3 where id = 'tv-en-vivo'")
        await conn.execute(
            "update categories set position = 100 + (position % 100) where kind = 'genre'"
        )
        await conn.execute(
            "update categories set position = 400 + (position % 100) where kind = 'source'"
        )
        # Una categoría sin títulos no se muestra.
        await conn.execute(
            "delete from categories c where not exists "
            "(select 1 from title_categories tc where tc.category_id = c.id)"
        )
        # Los géneros con un puñado de títulos (tags sueltos del portal) no aportan.
        await conn.execute(
            """
            delete from categories c where c.kind = 'genre' and (
                select count(*) from title_categories tc where tc.category_id = c.id
            ) < 5
            """
        )
    if merged:
        log.info("índice: %s categorías repetidas fusionadas", merged)
    return merged


def _rows(command_tag: str) -> int:
    try:
        return int(command_tag.split()[-1])
    except Exception:
        return 0


def _shift_sql(clause: str, shift: int = 2) -> str:
    """Desplaza los marcadores posicionales $1, $2... por un offset dado."""
    return re.sub(r"\$(\d+)", lambda m: f"${int(m.group(1)) + shift}", clause)


async def build_section_genres(pool) -> int:
    """Géneros propios de cada sección del riel (Películas/Series/Novelas/Anime).

    Cada hilera `xtv-{sección}-genero-{slug}` solo trae títulos de su sección
    con aislamiento estricto (mutuamente excluyentes):
      - Anime: todo contenido anime (cine y series anime). Jamás entra a Películas, Series ni Novelas.
      - Novelas: telenovelas (latinas, turcas, etc.). Jamás entra a Series ni Películas.
      - Películas: solo largometrajes/cine. Jamás entra Anime, Series ni Novelas.
      - Series: solo series de televisión generales. Jamás entra Anime ni Novelas.
    """
    changed = 0
    async with pool.acquire() as conn:
        # Limpieza previa de categorías de sección (para no retroalimentar ejecuciones anteriores)
        await conn.execute(
            "delete from title_categories where category_id in "
            "(select id from categories where kind = 'section')"
        )
        await conn.execute("delete from categories where kind = 'section'")

        # 1. Identificación exhaustiva de Anime:
        anime_rows = await conn.fetch(
            """
            select distinct t.id from titles t
            left join title_categories tc on tc.title_id = t.id
            where t.kind = 'anime'
               or 'Anime' = any(t.genres)
               or 'Animación Japonesa' = any(t.genres)
               or tc.category_id = 'xtv-anime-all'
               or (tc.category_id like 'xtv-anime-%' and tc.category_id not like 'xtv-anime-genero-%')
               or tc.category_id = 'masmobile_animes'
            """
        )
        anime_ids = [r["id"] for r in anime_rows]

        # 2. Identificación exhaustiva de Novelas (excluyendo Anime):
        novela_rows = await conn.fetch(
            """
            select distinct t.id from titles t
            left join title_categories tc on tc.title_id = t.id
            left join categories c on c.id = tc.category_id
            where not (t.id = any($1::text[]))
              and (
                  tc.category_id = 'xtv-series-76236'
               or lower(c.title) like '%novela%'
               or lower(c.title) like '%telenovela%'
               or 'Telenovela' = any(t.genres)
               or 'Novela' = any(t.genres)
               or 'Novelas' = any(t.genres)
               or lower(t.title) like '%telenovela%'
               or lower(t.synopsis) like '%telenovela%'
               or lower(t.synopsis) like '%novela mexicana%'
               or lower(t.synopsis) like '%novela turca%'
               or lower(t.synopsis) like '%novela colombiana%'
              )
            """,
            anime_ids,
        )
        novela_ids = [r["id"] for r in novela_rows]

        series_shelf = (
            "exists (select 1 from title_categories tc2 where tc2.title_id = t.id"
            " and tc2.category_id = 'xtv-series-all')"
        )

        filters = {
            "anime": (
                "t.id = any($1::text[])",
                [anime_ids],
            ),
            "novelas": (
                "t.id = any($1::text[])",
                [novela_ids],
            ),
            "movies": (
                "t.kind = 'movie' and coalesce(t.program_type, '') not in ('series', 'teleplay', 'anime')"
                f" and not ({series_shelf})"
                " and not (t.id = any($1::text[])) and not (t.id = any($2::text[]))",
                [anime_ids, novela_ids],
            ),
            "series": (
                "(t.program_type in ('series', 'teleplay')"
                " or (t.kind = 'series' and coalesce(t.program_type, '') not in ('movie', 'anime'))"
                f" or {series_shelf})"
                " and not (t.id = any($1::text[])) and not (t.id = any($2::text[]))",
                [anime_ids, novela_ids],
            ),
        }

        SECTION_PREMIERE_TITLES = {
            "movies": "Estrenos de Películas",
            "series": "Estrenos y Nuevas Temporadas",
            "anime": "Estrenos de Anime",
            "novelas": "Novedades de Novelas",
        }
        current_year = datetime.now().year

        for section, (base_where, extra) in filters.items():
            # 1. Hilera de estrenos / novedades de la sección (posición 1)
            estrenos_cid = f"xtv-{section}-estrenos"
            estrenos_title = SECTION_PREMIERE_TITLES.get(section, f"Estrenos de {SECTIONS.get(section, section)}")
            await conn.execute(CAT_SQL, estrenos_cid, estrenos_title, "section", 1)
            prem_args = [estrenos_cid, current_year - 1] + list(extra)
            prem_where = _shift_sql(base_where, 2)
            tag_prem = await conn.execute(
                "insert into title_categories (title_id, category_id, position) "
                "select t.id, $1, row_number() over (order by t.year desc nulls last, "
                "t.score desc nulls last, t.title) "
                f"from titles t where (t.year is not null and t.year >= $2 or t.is_premiere) and {prem_where} "
                "on conflict do nothing",
                *prem_args,
            )
            changed += _rows(tag_prem)

            # 2. Hileras de género de la sección (posiciones 10+)
            args = list(extra) + [SECTION_MIN_TITLES]
            min_ph = f"${len(args)}"
            genre_counts = await conn.fetch(
                "select g as genre, count(*) as n from titles t, unnest(t.genres) g "
                f"where {base_where} and cardinality(t.genres) > 0 "
                f"group by 1 having count(*) >= {min_ph} order by 2 desc",
                *args,
            )
            for rank, gr in enumerate(genre_counts):
                cid = f"xtv-{section}-genero-{slug(gr['genre'])}"
                await conn.execute(CAT_SQL, cid, gr["genre"], "section", 10 + rank)
                link_args = [cid, gr["genre"]] + list(extra)
                link_where = _shift_sql(base_where, 2)
                tag = await conn.execute(
                    "insert into title_categories (title_id, category_id, position) "
                    "select t.id, $1, row_number() over (order by t.score desc nulls last, "
                    "t.year desc nulls last, t.title) "
                    "from titles t, unnest(t.genres) gg "
                    f"where gg = $2 and {link_where} "
                    "on conflict do nothing",
                    *link_args,
                )
                changed += _rows(tag)

    if changed:
        log.info("índice: categorías y géneros por sección actualizados (%s enlaces)", changed)
    return changed


async def build(pool) -> int:
    changed = 0
    async with pool.acquire() as conn:
        # --- Estrenos ---
        await conn.execute(CAT_SQL, "estrenos", "Estrenos y Novedades", "special", 1)
        await conn.execute("delete from title_categories where category_id = 'estrenos'")
        tag = await conn.execute(
            """
            insert into title_categories (title_id, category_id, position)
            select id, 'estrenos', row_number() over (order by year desc nulls last, score desc nulls last, title)
            from titles where year is not null and year >= $1 or is_premiere
            on conflict do nothing
            """,
            datetime.now().year - 1,
        )
        changed += _rows(tag)

        # --- Géneros (desde titles.genres, ya traducidos por los conectores) ---
        rows = await conn.fetch("select id, genres from titles where cardinality(genres) > 0")
        catalog: dict[str, str] = {}
        title_ids: list[str] = []
        category_ids: list[str] = []
        for row in rows:
            for genre in row["genres"]:
                category_id = "genero-" + slug(genre)
                catalog.setdefault(category_id, genre)
                title_ids.append(row["id"])
                category_ids.append(category_id)
        await conn.execute("delete from title_categories where category_id like 'genero-%'")
        if catalog:
            await conn.executemany(
                CAT_SQL,
                [(cid, name, "genre", 500 + i) for i, (cid, name) in enumerate(sorted(catalog.items(), key=lambda kv: kv[1]))],
            )
            tag = await conn.execute(
                """
                insert into title_categories (title_id, category_id, position)
                select a, b, 0 from unnest($1::text[], $2::text[]) as t(a, b)
                on conflict do nothing
                """,
                title_ids,
                category_ids,
            )
            changed += _rows(tag)
        changed += await build_section_genres(pool)
        # Las categorías sin ningún título ya no sirven (la limpieza general va en normalize()).
    changed += await normalize(pool)
    log.info("índice: estrenos/géneros actualizados (%s cambios)", changed)
    return changed
