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

# Un título que TMDB descartó (match_confidence 0.0 = no existe en el catálogo mundial)
# no entra a las filas curadas: el portal le pone score inflado (9.3 a una película de
# nicho) y antes dominaba géneros y estrenos. Pendientes (sin fila) sí entran y se
# ordenan después de los ya clasificados.
NOT_UNMATCHED = (
    "not exists (select 1 from enrichment e where e.title_id = t.id and e.match_confidence < 0.5)"
)

# Posiciones de las repisas de «destacados» del portal (xtv-{sección}-destacados)
# por delante del relleno: los estrenos que Xuper promueve van al frente.
DESTACADOS_FIRST_SQL = """
insert into title_categories (title_id, category_id, position)
select tc.title_id, $1, row_number() over (order by tc.position, tc.title_id)
from title_categories tc
join titles t on t.id = tc.title_id
where tc.category_id = $2 and {where}
on conflict do nothing
"""

# El resto de la fila, ordenado por impacto real, arranca en 1000 para no adelantarse
# a los destacados del portal (que ocupan 1..N).
REST_OFFSET = 1000

# Tendencias: el Top de popularidad real de TODO el catálogo (la hilera que alimenta
# el «Top 10» del Inicio). Manda TMDB (popularidad + votos), no la fecha: un clásico
# que hoy está en boca de todos le gana a un estreno oscuro. Solo entra lo que tiene
# ficha visible (póster) y reproducción; el nicho descartado por TMDB queda fuera.
#
# UNA entrada por obra: el portal tiene la misma serie repartida en temporadas
# («Link Click» T1..T4, «Os Simpsons»/«Los Simpson», «Reacher» T1..T4) y todas
# comparten el tmdb_id. `distinct on` deja una sola —la mejor copia: más popular,
# más votada; el año más viejo para que la serie abra por su primera temporada—.
# Sin tmdb_id el título queda suelto (clave por su id) para no perder nada.
TENDENCIAS_SQL = (
    "insert into title_categories (title_id, category_id, position) "
    "select id, $1, row_number() over (order by popularity desc nulls last, "
    "vote_count desc nulls last, score desc nulls last, year asc nulls last, title) "
    "from ( "
    "  select distinct on (coalesce(e.tmdb_id, 0), "
    "                      case when e.tmdb_id is null then t.id else '' end) "
    "         t.id, t.popularity, t.vote_count, t.score, t.year, t.title "
    "  from titles t "
    "  left join enrichment e on e.title_id = t.id "
    "       and coalesce(e.match_confidence, 0) >= 0.5 "
    "  where t.source != 'magis' and {not_unmatched} "
    "  and coalesce(t.popularity, 0) > 0 "
    "  and coalesce(t.poster_url, '') <> '' "
    "  and coalesce(t.play_ref, '') <> '' "
    "  order by coalesce(e.tmdb_id, 0), "
    "           case when e.tmdb_id is null then t.id else '' end, "
    "           t.popularity desc nulls last, t.vote_count desc nulls last, "
    "           t.score desc nulls last, t.year asc nulls last, t.title "
    ") as one_per_work "
    "on conflict do nothing"
)


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

        # Orden curado: especiales arriba (Tendencias lidera: es el «Top 10» del Inicio),
        # géneros después, colecciones al final.
        await conn.execute("update categories set position = 1 where id = 'tendencias'")
        await conn.execute("update categories set position = 2 where id = 'estrenos'")
        await conn.execute("update categories set position = 3 where id = 'recomendados'")
        await conn.execute("update categories set position = 4 where id = 'tv-en-vivo'")
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


def section_filters(anime_ids: list[str], novela_ids: list[str]) -> dict[str, tuple[str, list]]:
    """Los WHERE mutuamente excluyentes de cada sección (con el filtro de nicho)."""
    series_shelf = (
        "exists (select 1 from title_categories tc2 where tc2.title_id = t.id"
        " and tc2.category_id = 'xtv-series-all')"
    )
    return {
        "anime": (
            "t.id = any($1::text[]) and " + NOT_UNMATCHED,
            [anime_ids],
        ),
        "novelas": (
            "t.id = any($1::text[]) and " + NOT_UNMATCHED,
            [novela_ids],
        ),
        "movies": (
            "t.kind = 'movie' and coalesce(t.program_type, '') not in ('series', 'teleplay', 'anime')"
            f" and not ({series_shelf})"
            " and not (t.id = any($1::text[])) and not (t.id = any($2::text[]))"
            " and " + NOT_UNMATCHED,
            [anime_ids, novela_ids],
        ),
        "series": (
            "(t.program_type in ('series', 'teleplay')"
            " or (t.kind = 'series' and coalesce(t.program_type, '') not in ('movie', 'anime'))"
            f" or {series_shelf})"
            " and not (t.id = any($1::text[])) and not (t.id = any($2::text[]))"
            " and " + NOT_UNMATCHED,
            [anime_ids, novela_ids],
        ),
    }


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

        filters = section_filters(anime_ids, novela_ids)

        SECTION_PREMIERE_TITLES = {
            "movies": "Estrenos de Películas",
            "series": "Estrenos y Nuevas Temporadas",
            "anime": "Estrenos de Anime",
            "novelas": "Novedades de Novelas",
        }
        current_year = datetime.now().year

        for section, (base_where, extra) in filters.items():
            # 1. Hilera de estrenos / novedades de la sección (posición 1):
            #    los destacados del portal van al frente; después, el resto por
            #    impacto (popularidad TMDB) antes que año puro.
            estrenos_cid = f"xtv-{section}-estrenos"
            estrenos_title = SECTION_PREMIERE_TITLES.get(section, f"Estrenos de {SECTIONS.get(section, section)}")
            await conn.execute(CAT_SQL, estrenos_cid, estrenos_title, "section", 1)
            dest_args = [estrenos_cid, f"xtv-{section}-destacados"] + list(extra)
            dest_where = _shift_sql(base_where, 2)
            tag_dest = await conn.execute(
                DESTACADOS_FIRST_SQL.format(where=dest_where),
                *dest_args,
            )
            changed += _rows(tag_dest)
            prem_args = [estrenos_cid, current_year - 1] + list(extra)
            prem_where = _shift_sql(base_where, 2)
            tag_prem = await conn.execute(
                "insert into title_categories (title_id, category_id, position) "
                "select t.id, $1, 1000 + row_number() over (order by coalesce(t.popularity, 0) desc, "
                "t.score desc nulls last, t.year desc nulls last, t.title) "
                f"from titles t where (t.year is not null and t.year >= $2 or t.is_premiere) and {prem_where} "
                "on conflict do nothing",
                *prem_args,
            )
            changed += _rows(tag_prem)

            # 2. Hileras de género de la sección (posiciones 10+): populares primero.
            #    Antes: score puro (oscuro 9.0 con 5 votos vencía a blockbuster 7.7).
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
                    "select t.id, $1, row_number() over (order by coalesce(t.popularity, 0) desc, "
                    "t.score desc nulls last, t.year desc nulls last, t.title) "
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
        # --- Tendencias: el Top de popularidad de todo el catálogo ---
        # El «Top 10» del Inicio bebe de aquí: popularidad real (TMDB) sin importar
        # el año, para no repetir lo que ya muestra «Novedades y Estrenos».
        await conn.execute(CAT_SQL, "tendencias", "Tendencias", "special", 1)
        await conn.execute("delete from title_categories where category_id = 'tendencias'")
        tag = await conn.execute(
            TENDENCIAS_SQL.format(not_unmatched=NOT_UNMATCHED),
            "tendencias",
        )
        changed += _rows(tag)

        # --- Estrenos ---
        await conn.execute(CAT_SQL, "estrenos", "Estrenos y Novedades", "special", 1)
        await conn.execute("delete from title_categories where category_id = 'estrenos'")
        # 1. Los destacados de cada sección del portal (repisas de estreno rescatadas)
        #    van al frente: eso muestra Xuper como «destacados» y aquí son «Novedades».
        #    Se intercalan por rango dentro de cada sección (1.º de cada una, 2.º de
        #    cada una…) para que ninguna (p. ej. anime con 1k) acapare el corte de 100.
        tag = await conn.execute(
            """
            insert into title_categories (title_id, category_id, position)
            select title_id, 'estrenos',
                   row_number() over (order by sec_rank, category_id, title_id)
            from (
                select tc.title_id, tc.category_id,
                       row_number() over (
                           partition by tc.category_id order by tc.position, tc.title_id
                       ) as sec_rank
                from title_categories tc
                join categories c on c.id = tc.category_id
                join titles t on t.id = tc.title_id
                where c.id like 'xtv-%-destacados' and t.source != 'magis'
                  and not exists (select 1 from enrichment e where e.title_id = t.id and e.match_confidence < 0.5)
            ) as ranked
            on conflict do nothing
            """
        )
        changed += _rows(tag)
        # 2. El resto por impacto real (popularidad TMDB), sin el nicho sin match.
        tag = await conn.execute(
            f"""
            insert into title_categories (title_id, category_id, position)
            select t.id, 'estrenos', 1000 + row_number() over (order by coalesce(t.popularity, 0) desc, t.year desc nulls last, t.score desc nulls last, t.title)
            from titles t where (t.year is not null and t.year >= $1 or t.is_premiere) and t.source != 'magis'
              and {NOT_UNMATCHED}
            on conflict do nothing
            """,
            datetime.now().year - 1,
        )
        changed += _rows(tag)

        # --- Géneros (desde titles.genres, ya traducidos por los conectores) ---
        rows = await conn.fetch(
            "select t.id, t.genres from titles t where cardinality(t.genres) > 0 and " + NOT_UNMATCHED
        )
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
