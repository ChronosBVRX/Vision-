"""Categorías calculadas sobre lo sincronizado: Estrenos y géneros.

- Estrenos: títulos con año >= actual - 1 (el año sale de `releaseTime` del portal
  o del enriquecimiento).
- Géneros: una categoría `genero-<slug>` por cada género presente en los títulos,
  con los enlaces en bloque (una sola sentencia, para no reventar la versión).
"""

import logging
import re
from datetime import datetime, timedelta, timezone

import genres as genre_rules
import themes
from catalog_filters import NOT_TRAILER, NOT_UNMATCHED
from xtv import CAT_SQL, slug

log = logging.getLogger("index")

BRANDS = ("netflix", "hbo", "disney", "prime video", "apple tv", "hulu", "paramount", "max ")

KIND_RANK = {"special": 0, "theme": 1, "genre": 1, "source": 2}


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

# La categoría que garantiza que NINGÚN título quede invisible: se llena una vez y
# luego recibe los nuevos (buscador del portal, repisas sin colección).
CATALOG_CATEGORY = "catalogo-completo"

# Las filas curadas excluyen el nicho que TMDB descartó (match < 0.5) y los
# tráilers del portal: los filtros viven en `catalog_filters.py` para compartirlos
# con las colecciones temáticas (`themes.py`).

# Posiciones de relleno de una hilera de estrenos: las primeras (1..FRESH_TOP) son
# para lo que ACABA de llegar al catálogo y, a partir de 1000, el resto por impacto.
# Antes los «destacados» del portal ocupaban 1..N y la fila se quedaba pegada a las
# mismas fichas (varias con 0 votos) aunque el catálogo ya tuviera novedades reales.
REST_OFFSET = 1000
# Cuántos recién llegados van al frente de «Novedades y Estrenos». El sync descubre
# títulos nuevos cada ciclo: la fila cambia sola sin esperar a que Xuper mueva sus
# repisas editoriales.
FRESH_TOP = 10
# Ventana de «recién llegado» (días desde que el título entró por primera vez).
FRESH_DAYS = 45


# Tendencias: el Top de popularidad real de TODO el catálogo (la hilera que alimenta
# el «Top 10» del Inicio). Manda TMDB (popularidad + votos), no la fecha: un clásico
# que hoy está en boca de todos le gana a un estreno oscuro. Solo entra lo que tiene
# ficha visible (póster) y reproducción; el nicho descartado por TMDB queda fuera.
#
# La fila se siente viva sin inventar gustos: suma la tendencia SEMANAL de TMDB
# (pequeña), un empujón a lo recién llegado, y un desempate diario determinista para
# que los títulos de impacto parecido roten. La popularidad se refresca desde TMDB
# (`tmdb.refresh_popularity`), así que el orden cambia cuando el mundo cambia.
#
# UNA entrada por obra: el portal tiene la misma serie repartida en temporadas
# («Link Click» T1..T4, «Os Simpsons»/«Los Simpson», «Reacher» T1..T4) y todas
# comparten el tmdb_id. `distinct on` deja una sola —la mejor copia: más popular,
# más votada; el año más viejo para que la serie abra por su primera temporada—.
# Sin tmdb_id el título queda suelto (clave por su id) para no perder nada.
TENDENCIAS_SQL = (
    "insert into title_categories (title_id, category_id, position) "
    "select id, $1, row_number() over (order by buzz desc, popularity desc nulls last, "
    "vote_count desc nulls last, score desc nulls last, year asc nulls last, title) "
    "from ( "
    "  select distinct on (coalesce(e.tmdb_id, 0), "
    "                      case when e.tmdb_id is null then t.id else '' end) "
    "         t.id, t.popularity, t.vote_count, t.score, t.year, t.title, "
    "         ( "
    "           least(10.0, ln(coalesce(t.popularity, 0) + 1.0) / ln(501.0) * 10.0) "
    "           + case when coalesce(t.vote_count, 0) > 0 "
    "                  then least(3.0, ln(coalesce(t.vote_count, 0) + 1.0) "
    "                       / ln(50001.0) * 3.0) "
    "                  else 0.0 end "
    "           + case when coalesce(t.is_premiere, false) then 1.2 else 0.0 end "
    "           + case when t.first_seen_at >= now() - make_interval(days => $2) "
    "                  then 2.5 else 0.0 end "
    "           + coalesce(w.bonus, 0.0) "
    "           + ((('x' || substr(md5(t.id || to_char(current_date, 'YYYY-MM-DD')), 1, 6))"
    "                ::bit(24)::int % 1000) / 1000.0) * 1.2 "
    "         ) as buzz "
    "  from titles t "
    "  left join enrichment e on e.title_id = t.id "
    "       and coalesce(e.match_confidence, 0) >= 0.5 "
    "  left join ( "
    "      select tmdb_id, max(greatest(0, 21 - rank)) * 0.2 as bonus "
    "      from weekly_trending "
    "      where week = (select max(week) from weekly_trending) "
    "      group by tmdb_id "
    "  ) w on w.tmdb_id = e.tmdb_id "
    "  where t.source != 'magis' and {not_unmatched} and {not_trailer} "
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
            # Las repisas de FUENTE con el mismo nombre que una fila curada (género o
            # especial) son redundantes y, al fusionarlas, metían títulos con otros
            # géneros (la misma película en Drama y Suspenso) y hasta tráilers en el
            # Top 10/Novedades. Se retiran: sus títulos siguen en «Todo el catálogo» y
            # en su género primario.
            if cats[0]["kind"] in ("genre", "special", "theme"):
                sources = [c["id"] for c in cats if c["kind"] == "source"]
                if sources:
                    await conn.execute(
                        "delete from title_categories where category_id = any($1::text[])",
                        sources,
                    )
                    await conn.execute(
                        "delete from categories where id = any($1::text[])", sources
                    )
                    merged += len(sources)
                    cats = [c for c in cats if c["kind"] != "source"]
                    if len(cats) < 2:
                        continue
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


def _chunks(items, size: int):
    for index in range(0, len(items), size):
        yield items[index:index + size]


def _impact_key(row) -> tuple:
    """Orden de impacto: popularidad TMDB, luego rating, año y título (estable)."""
    return (
        -float(row["popularity"] or 0),
        -float(row["score"] or 0),
        -(int(row["year"] or 0)),
        (row["title"] or "").lower(),
    )


def _is_fresh(row, now: datetime) -> bool:
    seen = row["first_seen_at"]
    return seen is not None and seen >= now - timedelta(days=FRESH_DAYS)


def _estrenos_work_key(row) -> str:
    """Una obra = un tmdb_id; sin TMDB, el título plegado (temporadas no se repiten)."""
    tmdb_id = int(row["tmdb_id"] or 0)
    if tmdb_id:
        return f"tmdb:{tmdb_id}"
    return "title:" + genre_rules.fold(row["title"] or "")


def _estrenos_rank(row) -> tuple:
    """La copia que representa a la obra: la temporada más nueva y la más popular."""
    return (
        int(row["year"] or 0),
        float(row["popularity"] or 0),
        1 if str(row["source"] or "") == "xtv" else 0,
        float(row["score"] or 0),
    )


def _one_per_work(rows) -> list:
    """Colapsa T14/T15 (mismo tmdb_id) en la temporada más nueva."""
    best: dict[str, object] = {}
    for row in rows:
        key = _estrenos_work_key(row)
        current = best.get(key)
        if current is None or _estrenos_rank(row) > _estrenos_rank(current):
            best[key] = row
    return list(best.values())


def _estrenos_pairs(rows, now: datetime, current_year: int) -> list[tuple[str, int]]:
    """Posiciones de una hilera de estrenos: recién llegados recientes, luego impacto.

    Regla dura: solo entran títulos cuyo AÑO es el actual o el anterior. El sync
    descubre catálogo viejo cada día (la pasada profunda trajo «El piano» de 1993,
    «La Zona Muerta» de 1983…) y antes esos recién importados abrían «Novedades y
    Estrenos» solo por su `first_seen_at`: la fila parecía un archivo de cine
    clásico. Ahora «recién llegado» solo cuenta si además es un estreno real.
    """
    floor_year = current_year - 1
    candidates = [
        row
        for row in rows
        if int(row["year"] or 0) >= floor_year
        and str(row["source"] or "") != "magis"
    ]
    candidates = _one_per_work(candidates)
    fresh: list = []
    recent: list = []
    for row in candidates:
        popularity = float(row["popularity"] or 0)
        votes = int(row["vote_count"] or 0)
        # La señal manda: una ficha recién importada con 0 votos y 0 popularidad no
        # abre «Novedades» (aunque el portal la marque como «destacada»).
        if _is_fresh(row, now) and (popularity > 0 or votes >= 5):
            fresh.append(row)
        else:
            recent.append(row)
    # Estable: primero impacto y luego la fecha de llegada (lo más nuevo arriba).
    fresh.sort(key=_impact_key)
    fresh.sort(key=lambda r: r["first_seen_at"], reverse=True)
    recent.sort(key=_impact_key)
    head = fresh[:FRESH_TOP]
    tail = fresh[FRESH_TOP:] + recent
    pairs = [(row["id"], 1 + index) for index, row in enumerate(head)]
    seen = {row["id"] for row in head}
    for index, row in enumerate(tail):
        if row["id"] in seen:
            continue
        seen.add(row["id"])
        pairs.append((row["id"], REST_OFFSET + index))
    return pairs


async def _insert_links(conn, links: list[tuple[str, str, int]]) -> int:
    """Inserta enlaces (título, categoría, posición) en bloques con un solo SQL."""
    total = 0
    for chunk in _chunks(links, 5000):
        tag = await conn.execute(
            "insert into title_categories (title_id, category_id, position) "
            "select a, b, c from unnest($1::text[], $2::text[], $3::int[]) as t(a, b, c) "
            "on conflict do nothing",
            [row[0] for row in chunk],
            [row[1] for row in chunk],
            [row[2] for row in chunk],
        )
        total += _rows(tag)
    return total


async def _genres_by_title(conn) -> dict[str, list[str]]:
    """Géneros canónicos por título (manda TMDB; el portal rellena los huecos)."""
    rows = await conn.fetch(
        f"""
        select t.id, t.genres as portal_genres, e.genres as tmdb_genres,
               coalesce(e.match_confidence, 0) >= 0.5 as tmdb_matched
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_UNMATCHED} and {NOT_TRAILER}
        """
    )
    return {
        row["id"]: genre_rules.resolve(
            row["portal_genres"], row["tmdb_genres"], row["tmdb_matched"]
        )
        for row in rows
    }


async def _title_meta(conn) -> dict:
    """Metadatos de impacto/frescura que necesitan las filas del catálogo."""
    rows = await conn.fetch(
        f"""
        select t.id, t.title, t.year, t.popularity, t.score, t.vote_count,
               t.is_premiere, t.first_seen_at, t.source, e.tmdb_id
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_UNMATCHED} and {NOT_TRAILER}
        """
    )
    return {row["id"]: row for row in rows}


async def build_section_genres(pool, genres_by_id=None, meta=None) -> int:
    """Géneros propios de cada sección del riel (Películas/Series/Novelas/Anime).

    Cada hilera `xtv-{sección}-genero-{slug}` solo trae títulos de su sección
    con aislamiento estricto (mutuamente excluyentes):
      - Anime: todo contenido anime (cine y series anime). Jamás entra a Películas, Series ni Novelas.
      - Novelas: telenovelas (latinas, turcas, etc.). Jamás entra a Series ni Películas.
      - Películas: solo largometrajes/cine. Jamás entra Anime, Series ni Novelas.
      - Series: solo series de televisión generales. Jamás entra Anime ni Novelas.

    Además, cada título vive en UNA sola hilera de género (su primario): la misma
    película ya no aparece en Drama, Suspenso y Comedia a la vez.
    """
    changed = 0
    # Todo o nada: el worker borra y recrea las hileras de sección en cada ciclo. Si el
    # proceso muere a media reconstrucción (un deploy, un OOM), el riel se quedaba SIN
    # Películas/Series hasta el ciclo siguiente. Con la transacción, o están las viejas
    # o están las nuevas.
    async with pool.acquire() as conn, conn.transaction():
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
        genres_by_id = genres_by_id if genres_by_id is not None else await _genres_by_title(conn)
        meta = meta if meta is not None else await _title_meta(conn)

        SECTION_PREMIERE_TITLES = {
            "movies": "Estrenos de Películas",
            "series": "Estrenos y Nuevas Temporadas",
            "anime": "Estrenos de Anime",
            "novelas": "Novedades de Novelas",
        }
        current_year = datetime.now().year
        now = datetime.now(timezone.utc)

        for section, (base_where, extra) in filters.items():
            # 1. Hilera de estrenos/novedades de la sección (posición 1): lo recién
            #    llegado al catálogo primero y después lo del año por impacto real.
            estrenos_cid = f"xtv-{section}-estrenos"
            estrenos_title = SECTION_PREMIERE_TITLES.get(
                section, f"Estrenos de {SECTIONS.get(section, section)}"
            )
            await conn.execute(CAT_SQL, estrenos_cid, estrenos_title, "section", 1)
            id_rows = await conn.fetch(f"select t.id from titles t where {base_where}", *extra)
            section_rows = [meta[row["id"]] for row in id_rows if row["id"] in meta]
            pairs = _estrenos_pairs(section_rows, now, current_year)
            changed += await _insert_links(
                conn, [(title_id, estrenos_cid, position) for title_id, position in pairs]
            )

            # 2. Hileras de género de la sección (posiciones 10+): SOLO el primario.
            #    En Novelas se descarta «Telenovela» (todas lo serían y la fila
            #    quedaría sola): se reparten por Romance/Comedia/Drama/Suspenso.
            drop = {"Telenovela"} if section == "novelas" else ()
            priority = genre_rules.NOVELA_PRIORITY if section == "novelas" else None
            by_genre: dict[str, list] = {}
            for row in section_rows:
                primary = genre_rules.primary_genre(
                    genres_by_id.get(row["id"], ()), drop=drop, priority=priority
                )
                if primary:
                    by_genre.setdefault(primary, []).append(row)
            ranked = sorted(by_genre.items(), key=lambda kv: (-len(kv[1]), kv[0]))
            links: list[tuple[str, str, int]] = []
            rank = 0
            for genre, films in ranked:
                if len(films) < SECTION_MIN_TITLES:
                    continue
                cid = f"xtv-{section}-genero-{slug(genre)}"
                await conn.execute(CAT_SQL, cid, genre, "section", 10 + rank)
                rank += 1
                films.sort(key=_impact_key)
                links.extend(
                    (row["id"], cid, index) for index, row in enumerate(films)
                )
            changed += await _insert_links(conn, links)

    if changed:
        log.info("índice: categorías y géneros por sección actualizados (%s enlaces)", changed)
    return changed


async def ensure_catalog_category(pool) -> int:
    """Todo título vive en al menos una categoría navegable.

    Los títulos que llegan por el buscador del portal o por repisas sin colección
    quedaban fuera de toda fila (691 medidos el 2026-10-22): se encontraban por
    nombre pero eran invisibles para navegar. «Todo el catálogo» se llena una vez y
    luego solo recibe los nuevos, al final, para no reordenar lo ya visto.
    """
    async with pool.acquire() as conn:
        await conn.execute(CAT_SQL, CATALOG_CATEGORY, "Todo el catálogo", "special", 90)
        has_rows = await conn.fetchval(
            "select exists(select 1 from title_categories where category_id = $1)",
            CATALOG_CATEGORY,
        )
        base = 1_000_000 if has_rows else 0
        tag = await conn.execute(
            f"""
            insert into title_categories (title_id, category_id, position)
            select t.id, $1, {base} + row_number() over (
                order by coalesce(t.popularity, 0) desc, t.title
            )
            from titles t
            where not exists (
                select 1 from title_categories tc
                where tc.category_id = $1 and tc.title_id = t.id
            )
            on conflict do nothing
            """,
            CATALOG_CATEGORY,
        )
    count = _rows(tag)
    if count:
        log.info("índice: %s títulos nuevos en «Todo el catálogo»", count)
    return count


async def build(pool) -> int:
    changed = 0
    now = datetime.now(timezone.utc)
    current_year = datetime.now().year
    async with pool.acquire() as conn:
        genres_by_id = await _genres_by_title(conn)
        meta = await _title_meta(conn)

        # --- Tendencias: el Top de popularidad de todo el catálogo ---
        # El «Top 10» del Inicio bebe de aquí: popularidad real (TMDB) sin importar
        # el año, con la tendencia semanal y lo recién llegado como señales de vida.
        await conn.execute(CAT_SQL, "tendencias", "Tendencias", "special", 1)
        await conn.execute("delete from title_categories where category_id = 'tendencias'")
        tag = await conn.execute(
            TENDENCIAS_SQL.format(not_unmatched=NOT_UNMATCHED, not_trailer=NOT_TRAILER),
            "tendencias",
            FRESH_DAYS,
        )
        changed += _rows(tag)

        # --- Estrenos y Novedades ---
        # 1. Solo estrenos reales: año actual o anterior. Un título viejo que acaba
        #    de entrar al catálogo por la pasada profunda NO es una novedad.
        # 2. Lo recién llegado (y reciente) va al frente; después, lo del año por
        #    impacto real, una tarjeta por obra (T14/T15 no se duplican).
        # 3. El Top 10 no se repite aquí (ni por obra: mismo tmdb_id).
        await conn.execute(CAT_SQL, "estrenos", "Estrenos y Novedades", "special", 1)
        await conn.execute("delete from title_categories where category_id = 'estrenos'")
        top_rows = await conn.fetch(
            "select tc.title_id, e.tmdb_id from title_categories tc "
            "left join enrichment e on e.title_id = tc.title_id "
            "and coalesce(e.match_confidence, 0) >= 0.5 "
            "where tc.category_id = 'tendencias' and tc.position <= 10"
        )
        top_ids = {row["title_id"] for row in top_rows}
        top_tmdb = {int(row["tmdb_id"]) for row in top_rows if row["tmdb_id"]}
        estrenos_rows = [
            row
            for title_id, row in meta.items()
            if title_id not in top_ids
            and (row["tmdb_id"] is None or int(row["tmdb_id"]) not in top_tmdb)
            and row["source"] != "magis"
            and int(row["year"] or 0) >= current_year - 1
        ]
        pairs = _estrenos_pairs(estrenos_rows, now, current_year)
        changed += await _insert_links(
            conn, [(title_id, "estrenos", position) for title_id, position in pairs]
        )

        # --- Géneros globales: SOLO el género primario de cada título ---
        # Antes cada título entraba en todos sus géneros (un título llegaba a 22
        # filas): Drama/Suspenso/Comedia mostraban el mismo catálogo. Ahora cada
        # obra vive en UNA categoría, así que navegar por géneros no se repite.
        await conn.execute("delete from title_categories where category_id like 'genero-%'")
        by_genre: dict[str, list] = {}
        for title_id, genres in genres_by_id.items():
            primary = genre_rules.primary_genre(genres)
            row = meta.get(title_id)
            if primary and row is not None:
                by_genre.setdefault(primary, []).append(row)
        ranked = sorted(by_genre.items(), key=lambda kv: (-len(kv[1]), kv[0]))
        links: list[tuple[str, str, int]] = []
        for rank, (genre, films) in enumerate(ranked):
            cid = "genero-" + slug(genre)
            await conn.execute(CAT_SQL, cid, genre, "genre", 500 + rank)
            films.sort(key=_impact_key)
            links.extend((row["id"], cid, index) for index, row in enumerate(films))
        changed += await _insert_links(conn, links)

        changed += await build_section_genres(pool, genres_by_id, meta)
        # Colecciones temáticas (Día de Muertos, Halloween, Navidad, terror/comedia…):
        # se reconstruyen cada ciclo con la fecha de Ciudad de México.
        changed += await themes.build(pool, genres_by_id)
        changed += await ensure_catalog_category(pool)
        # Las categorías sin ningún título ya no sirven (la limpieza general va en normalize()).
    changed += await normalize(pool)
    log.info("índice: estrenos/géneros actualizados (%s cambios)", changed)
    return changed
