"""Colecciones temáticas que se encienden solas según la fecha.

El catálogo se sentía estático: «Terror» y «Comedia» son filas permanentes, pero no
había nada que dijera «es Día de Muertos», «es Navidad» o «es San Valentín». Este
módulo arma categorías `tema-*` con títulos del catálogo real:

  - Día de Muertos, Halloween, Navidad, San Valentín, Día de las Madres, Día del
    Padre, Fiestas Patrias, Vacaciones, Regreso a clases y Año Nuevo, cada una con
    su ventana de fechas.
  - «Para reír» todo el año, para que la comedia tenga una fila propia.
  - «Tendencias del mundo» con la foto semanal de TMDB.

Las temáticas de temporada alta (Halloween y Día de Muertos) NO se llenan con
palabras sueltas: se consultan las **keywords oficiales de TMDB**
(`/discover/{movie,tv}?with_keywords=…`), la misma clasificación que usan Kodi,
Jellyfin, Plex y las colecciones de TMDB. Así:

  - «Halloween» solo lleva obras con la keyword `halloween` (3335); no cualquier
    película de terror.
  - «Día de Muertos» solo lleva obras con las keywords `day of the dead` (209789)
    o `dia de los muertos` (322048), más una lista corta de obras canónicas de la
    fiesta (Coco, El libro de la vida, Macario, La leyenda de la Nahuala…).
  - «Noche de terror» se retiró: era la misma fila de «Terror» todo el año.

TMDB comparte el espacio de IDs entre películas y series (el id 300401 es la
película «Day of the Dead» de 1957 y la serie «Dynasty»). Por eso cada miembro
guardado lleva su `media` y al cruzar se descarta el que no corresponde; si el
tipo de la ficha es ambiguo (anime/kids pueden ser peli o serie), se exige que el
título coincida. La foto de TMDB se guarda en `theme_tmdb` para que un fallo de red
no borre la colección: se conserva la última copia buena.

Las temáticas de texto (Navidad, San Valentín…) mantienen su selección por etapas
para no llenar una fila con lo que no toca: título → frases específicas de la
sinopsis → género. Cada obra entra UNA vez: se deduplica por tmdb_id (las
temporadas T1..T20 de una serie no consumen la fila) y, sin TMDB, por título
plegado.
"""

import asyncio
import logging
import re
from dataclasses import dataclass
from datetime import date

import genres as genre_rules
import tmdb
from catalog_filters import NOT_TRAILER, NOT_UNMATCHED, fold_sql, keyword_patterns
from xtv import CAT_SQL

log = logging.getLogger("themes")

WORLD_TRENDING_ID = "tema-tendencias-mundo"
WORLD_TRENDING_TITLE = "Tendencias del mundo"

# Keywords de TMDB (https://www.themoviedb.org/keyword/<id>). Son la fuente de
# verdad de la clasificación: no se adivina por palabras de la sinopsis.
TMDB_HALLOWEEN = 3335           # halloween
TMDB_DAY_OF_THE_DEAD = 209789   # day of the dead
TMDB_DIA_DE_MUERTOS = 322048    # dia de los muertos


@dataclass(frozen=True)
class Theme:
    id: str
    title: str
    genres: tuple[str, ...] = ()
    fallback_genres: tuple[str, ...] = ()
    title_keywords: tuple[str, ...] = ()
    synopsis_keywords: tuple[str, ...] = ()
    tmdb_keywords: tuple[int, ...] = ()
    # (título plegado, año) de obras canónicas que TMDB no siempre etiqueta.
    seed_titles: tuple[tuple[str, int], ...] = ()
    start: tuple[int, int] | None = None
    end: tuple[int, int] | None = None
    limit: int = 24
    min_titles: int = 8
    always: bool = False


# El orden importa: las temporadas primero y las permanentes al final. En octubre
# conviven Día de Muertos y Halloween; Día de Muertos va primero porque es la fiesta
# mexicana y el pedido explícito.
THEMES: tuple[Theme, ...] = (
    Theme(
        id="tema-dia-de-muertos",
        title="Día de Muertos",
        start=(10, 24),
        end=(11, 4),
        # Nada de «muerto», «muerte», «terror» o «fantasma»: esas palabras metían
        # Harry Potter, Piratas del Caribe y dramas médicos en la fiesta mexicana.
        tmdb_keywords=(TMDB_DAY_OF_THE_DEAD, TMDB_DIA_DE_MUERTOS),
        seed_titles=(
            ("coco", 2017),
            ("el libro de la vida", 2014),
            ("la leyenda de la nahuala", 2007),
            ("macario", 1960),
            ("dia de muertos", 2019),
            ("catalina la catrina especial dia de muertos", 2021),
            ("amar hasta a la muerte la celebracion mexicana del dia de muertos", 2021),
        ),
        limit=24,
        # Con la clasificación estricta quedan ~8 obras reales en el catálogo. Es
        # preferible una fila corta y honesta que 28 títulos que no son de la fiesta.
        min_titles=4,
    ),
    Theme(
        id="tema-halloween",
        title="Halloween",
        start=(10, 1),
        end=(10, 31),
        # Solo la keyword canónica. Las variantes «halloween party/costume» metían
        # comedias románticas y episodios sueltos sin tono de Halloween.
        tmdb_keywords=(TMDB_HALLOWEEN,),
        limit=28,
        min_titles=8,
    ),
    Theme(
        id="tema-navidad",
        title="Navidad",
        start=(12, 1),
        end=(12, 31),
        title_keywords=(
            "navidad", "naviden", "santa", "papa noel", "nochebuena",
            "reno", "duende", "christmas", "milagro",
        ),
        synopsis_keywords=("navidad", "nochebuena", "papa noel"),
        limit=28,
    ),
    Theme(
        id="tema-san-valentin",
        title="San Valentín",
        start=(2, 1),
        end=(2, 15),
        title_keywords=(
            "san valentin", "amor", "enamor", "boda", "pareja", "corazon",
            "romance", "desamor",
        ),
        synopsis_keywords=("san valentin", "dia del amor", "enamorado"),
        limit=24,
    ),
    Theme(
        id="tema-dia-de-las-madres",
        title="Día de las Madres",
        start=(5, 1),
        end=(5, 12),
        title_keywords=("mama", "madre", "maternal", "madrecita", "abuela"),
        synopsis_keywords=("dia de las madres", "amor de madre"),
        limit=20,
    ),
    Theme(
        id="tema-dia-del-padre",
        title="Día del Padre",
        start=(6, 1),
        end=(6, 21),
        title_keywords=("papa", "padre", "paternal", "papacito", "abuelo"),
        synopsis_keywords=("dia del padre", "amor de padre"),
        limit=20,
    ),
    Theme(
        id="tema-fiestas-patrias",
        title="Fiestas Patrias",
        start=(9, 10),
        end=(9, 20),
        title_keywords=(
            "mexico", "mexican", "revolucion", "rancho", "charro", "norteno",
            "mariachi", "patria", "independencia",
        ),
        synopsis_keywords=(
            "revolucion mexicana", "independencia de mexico", "fiestas patrias",
        ),
        limit=20,
    ),
    Theme(
        id="tema-vacaciones",
        title="Vacaciones",
        start=(7, 1),
        end=(8, 20),
        title_keywords=(
            "vacaciones", "verano", "playa", "viaje", "crucero", "hotel", "isla",
        ),
        synopsis_keywords=("vacaciones de verano", "viaje de vacaciones"),
        limit=20,
    ),
    Theme(
        id="tema-regreso-a-clases",
        title="Regreso a clases",
        start=(8, 1),
        end=(9, 10),
        title_keywords=(
            "escuela", "colegio", "universidad", "estudiante", "maestro",
            "profesor", "prepa",
        ),
        synopsis_keywords=("regreso a clases", "primer dia de clases"),
        limit=20,
    ),
    Theme(
        id="tema-ano-nuevo",
        title="Año Nuevo",
        start=(12, 28),
        end=(1, 10),
        title_keywords=(
            "ano nuevo", "nuevo comienzo", "fin de ano", "proposito", "brindis",
        ),
        synopsis_keywords=("ano nuevo", "nochevieja", "proposito de ano nuevo"),
        limit=20,
    ),
    Theme(
        id="tema-para-reir",
        title="Para reír",
        genres=("Comedia",),
        limit=24,
        always=True,
    ),
)


def _in_window(today: date, start: tuple[int, int] | None, end: tuple[int, int] | None) -> bool:
    if not start or not end:
        return False
    md = today.month * 100 + today.day
    start_md = start[0] * 100 + start[1]
    end_md = end[0] * 100 + end[1]
    if start_md <= end_md:
        return start_md <= md <= end_md
    # Ventana que cruza el año (Año Nuevo: 28-dic → 10-ene).
    return md >= start_md or md <= end_md


def active_themes(today: date | None = None) -> list[Theme]:
    day = today or date.today()
    return [theme for theme in THEMES if theme.always or _in_window(day, theme.start, theme.end)]


def _chunks(items, size: int):
    for index in range(0, len(items), size):
        yield items[index:index + size]


def _rows(command_tag: str) -> int:
    try:
        return int(command_tag.split()[-1])
    except Exception:
        return 0


async def _insert_links(conn, links: list[tuple[str, str, int]]) -> int:
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


def _work_key(row) -> str:
    """Una obra = un tmdb_id; sin TMDB, el título plegado (las temporadas no se repiten)."""
    tmdb_id = int(row["tmdb_id"] or 0)
    if tmdb_id:
        return f"tmdb:{tmdb_id}"
    return "title:" + genre_rules.fold(row["title"] or "")


_TEXT = (
    "coalesce(t.title, '') || ' ' || coalesce(t.synopsis, '') || ' ' "
    "|| coalesce(e.overview, '')"
)

_SEASON_MARK = re.compile(r"\b(?:t|temp\.?|temporada|season)\s*\d+\b", re.IGNORECASE)


def _fold_title(text: str) -> str:
    """Título sin acentos, sin marca de temporada y en minúsculas."""
    return " ".join(_SEASON_MARK.sub(" ", genre_rules.fold(text or "")).split())


def _media_matches_kind(media: str, kind: str) -> bool:
    """TMDB separa películas y series; el `kind` del portal no siempre coincide.

    `anime` y `kids` pueden ser película o serie, así que no se descartan por tipo:
    si el tipo es ambiguo y no coincide, `_title_matches` decide.
    """
    media = (media or "").strip().lower()
    kind = (kind or "").strip().lower()
    if media == "movie":
        return kind != "series"
    if media == "tv":
        return kind != "movie"
    return True


def _title_matches(row, tmdb_title: str, tmdb_year: int) -> bool:
    """Confirma que el id compartido es ESTA obra y no la del otro espacio de TMDB.

    El caso real que lo motivó: TMDB 300401 es la película «Day of the Dead» (1957)
    y la serie «Dynasty» (2017). Sin esta comprobación, «Dynasty T5» aparecía en
    Día de Muertos.
    """
    if not tmdb_title:
        return True
    row_year = int(row["year"] or 0)
    year = int(tmdb_year or 0)
    if row_year and year and abs(row_year - year) > 2:
        return False
    target = _fold_title(tmdb_title)
    if not target:
        return True
    target_tokens = {token for token in target.split() if len(token) > 2}
    candidates = [
        row["title"],
        row["localized_title"] or "",
        row["original_title"] or "",
        *(row["alt_titles"] or []),
        *(row["translated_titles"] or []),
    ]
    for candidate in candidates:
        folded = _fold_title(candidate)
        if not folded:
            continue
        if folded == target or folded.startswith(target + " ") or target.startswith(folded + " "):
            return True
        tokens = {token for token in folded.split() if len(token) > 2}
        if target_tokens and tokens:
            overlap = len(target_tokens & tokens) / min(len(target_tokens), len(tokens))
            if overlap >= 0.75:
                return True
    return False


def _copy_preference(row, seeded: bool) -> tuple:
    """Entre copias de la misma obra (doblajes, temporadas) elige la mejor tarjeta.

    Prefiere la semilla curada, la **temporada más nueva** (una serie en emisión debe
    enseñar su temporada vigente), la copia cuyo título coincide con el nombre es-MX
    de TMDB (así «El extraño mundo de Jack» gana a «O Estranho Mundo de Jack») y, por
    último, la popularidad.
    """
    localized = _fold_title(row["localized_title"] or "")
    title = _fold_title(row["title"] or "")
    spanish = 1 if localized and (
        title == localized or title.startswith(localized + " ") or localized.startswith(title + " ")
    ) else 0
    return (
        1 if seeded else 0,
        int(row["year"] or 0),
        spanish,
        float(row["popularity"] or 0),
        float(row["score"] or 0),
    )


async def _match(conn, condition: str, parameter) -> list:
    """Títulos que cumplen una condición, ordenados por impacto y sin repetir obra."""
    folded_title = fold_sql("coalesce(t.title, '')")
    folded_text = fold_sql(_TEXT)
    field = folded_title if condition == "title" else folded_text
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, e.tmdb_id
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_UNMATCHED} and {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
          and {field} like any($1::text[])
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
        limit 160
    """
    rows = await conn.fetch(sql, parameter)
    out = []
    seen: set[str] = set()
    for row in rows:
        key = _work_key(row)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


async def _genre_matches(conn, genres: tuple[str, ...], primary_by_id: dict | None = None) -> list:
    """Títulos cuyo GÉNERO PRIMARIO está en [genres], una tarjeta por obra.

    Se consulta por los IDs que ya calculó el índice (no por las etiquetas crudas):
    así «Para reír» muestra las comedias de verdad y no dramas que llevan «Comedia»
    como etiqueta secundaria. El relleno de un género dice lo que la fila promete.
    """
    if not primary_by_id:
        return []
    ids = [title_id for title_id, primary in primary_by_id.items() if primary in genres]
    if not ids:
        return []
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, e.tmdb_id
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_UNMATCHED} and {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
          and t.id = any($1::text[])
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
        limit 300
    """
    rows = await conn.fetch(sql, ids)
    out = []
    seen: set[str] = set()
    for row in rows:
        key = _work_key(row)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


async def _seed_matches(conn, seeds: tuple[tuple[str, int], ...]) -> list:
    """Obras canónicas de la fiesta que TMDB no siempre etiqueta.

    `seeds` ya vienen plegadas y con el año para no confundir remakes (Coco 2017
    no es «Coco» de 2009). El plegado de la base quita puntuación, así que
    «Catalina la Catrina: especial Día de Muertos» casa con su semilla.
    """
    if not seeds:
        return []
    folded = [seed[0] for seed in seeds]
    years = [seed[1] for seed in seeds]
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, t.kind, e.tmdb_id,
               coalesce(e.localized_title, '') as localized_title,
               coalesce(e.original_title, '') as original_title,
               coalesce(e.alt_titles, '{{}}'::text[]) as alt_titles,
               coalesce(e.translated_titles, '{{}}'::text[]) as translated_titles
        from titles t
        left join enrichment e on e.title_id = t.id
             and coalesce(e.match_confidence, 0) >= 0.5
        where {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
          and exists (
              select 1 from unnest($1::text[], $2::int[]) as s(folded, year)
              where vp_fold(t.title) = s.folded
                and (s.year = 0 or coalesce(t.year, 0) = s.year)
          )
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
    """
    return await conn.fetch(sql, folded, years)


async def _refresh_tmdb_members(conn, theme: Theme, discover=None) -> None:
    """Refresca la foto de TMDB; si falla, deja la anterior (no inventa la fila)."""
    if not theme.tmdb_keywords:
        return
    fetch = discover or tmdb.discover_keyword_members
    try:
        found = await asyncio.to_thread(fetch, theme.tmdb_keywords)
    except Exception as exc:  # noqa: BLE001 - la copia anterior sigue sirviendo
        log.warning("tema %s: TMDB falló (%s); se conserva la foto anterior", theme.id, exc)
        return
    if not found:
        log.warning("tema %s: TMDB no devolvió títulos; se conserva la foto anterior", theme.id)
        return
    seen: set[tuple[int, str]] = set()
    rows: list[tuple] = []
    for item in found:
        tmdb_id = int(item.get("id") or 0)
        media = str(item.get("media") or "")
        if not tmdb_id or (tmdb_id, media) in seen:
            continue
        seen.add((tmdb_id, media))
        rows.append(
            (
                theme.id,
                tmdb_id,
                media,
                str(item.get("title") or "")[:200],
                int(item.get("year") or 0),
            )
        )
    if not rows:
        log.warning("tema %s: TMDB devolvió solo IDs inválidos; se conserva la foto anterior", theme.id)
        return
    async with conn.transaction():
        await conn.execute("delete from theme_tmdb where theme_id = $1", theme.id)
        await conn.executemany(
            "insert into theme_tmdb (theme_id, tmdb_id, media, title, year, updated_at) "
            "values ($1, $2, $3, $4, $5, now())",
            rows,
        )


async def _tmdb_matches(conn, theme: Theme) -> list:
    """Títulos del catálogo que TMDB clasificó con las keywords del tema."""
    sql = f"""
        select t.id, t.title, t.year, t.popularity, t.score, t.kind, e.tmdb_id,
               m.media, m.title as tmdb_title, m.year as tmdb_year,
               coalesce(e.localized_title, '') as localized_title,
               coalesce(e.original_title, '') as original_title,
               coalesce(e.alt_titles, '{{}}'::text[]) as alt_titles,
               coalesce(e.translated_titles, '{{}}'::text[]) as translated_titles
        from theme_tmdb m
        join enrichment e on e.tmdb_id = m.tmdb_id
             and coalesce(e.match_confidence, 0) >= 0.5
        join titles t on t.id = e.title_id
        where m.theme_id = $1
          and {NOT_UNMATCHED} and {NOT_TRAILER}
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
        order by coalesce(t.popularity, 0) desc, t.score desc nulls last,
                 t.year desc nulls last, t.title
        limit 500
    """
    rows = await conn.fetch(sql, theme.id)
    out = []
    for row in rows:
        if not _media_matches_kind(row["media"], row["kind"]):
            if not _title_matches(row, row["tmdb_title"], row["tmdb_year"]):
                continue
        out.append(row)
    return out


async def _classified_ids(conn, theme: Theme, discover=None) -> list[str]:
    """Tema con clasificación externa: semillas canónicas + keywords de TMDB."""
    seed_rows = await _seed_matches(conn, theme.seed_titles)
    await _refresh_tmdb_members(conn, theme, discover)
    tmdb_rows = await _tmdb_matches(conn, theme)
    unique: dict[str, tuple[object, tuple]] = {}
    for seeded, rows in ((True, seed_rows), (False, tmdb_rows)):
        for row in rows:
            key = _work_key(row)
            preference = _copy_preference(row, seeded)
            current = unique.get(key)
            if current is None or preference > current[1]:
                unique[key] = (row, preference)
    ordered = sorted(
        (row for row, _ in unique.values()),
        key=lambda row: (
            -float(row["popularity"] or 0),
            -float(row["score"] or 0),
            -int(row["year"] or 0),
            (row["title"] or "").lower(),
        ),
    )
    return [row["id"] for row in ordered[: theme.limit]]


async def _theme_ids(
    conn,
    theme: Theme,
    primary_by_id: dict | None = None,
    discover=None,
) -> list[str]:
    """Título → sinopsis específica → género → relleno. Una tarjeta por obra."""
    if theme.tmdb_keywords or theme.seed_titles:
        return await _classified_ids(conn, theme, discover)

    picked: list = []
    seen: set[str] = set()

    def add(rows):
        for row in rows:
            key = _work_key(row)
            if key in seen:
                continue
            seen.add(key)
            picked.append(row)

    title_patterns = keyword_patterns(theme.title_keywords)
    if title_patterns:
        add(await _match(conn, "title", title_patterns))
    if len(picked) < theme.limit:
        synopsis_patterns = keyword_patterns(theme.synopsis_keywords)
        if synopsis_patterns:
            add(await _match(conn, "synopsis", synopsis_patterns))
    if len(picked) < theme.limit and theme.genres:
        add(await _genre_matches(conn, theme.genres, primary_by_id))
    if len(picked) < theme.limit and theme.fallback_genres:
        add(await _genre_matches(conn, theme.fallback_genres, primary_by_id))
    return [row["id"] for row in picked[: theme.limit]]


async def _world_trending(conn, limit: int = 40) -> list[str]:
    """«Tendencias del mundo»: la foto semanal de TMDB, una tarjeta por obra."""
    rows = await conn.fetch(
        """
        select e.title_id, w.tmdb_id
        from weekly_trending w
        join enrichment e on e.tmdb_id = w.tmdb_id
             and coalesce(e.match_confidence, 0) >= 0.5
        join titles t on t.id = e.title_id
        where w.week = (select max(week) from weekly_trending)
          and t.source != 'magis'
          and coalesce(t.poster_url, '') <> ''
          and coalesce(t.play_ref, '') <> ''
        order by w.rank, coalesce(e.match_confidence, 0) desc,
                 t.popularity desc nulls last, t.vote_count desc nulls last
        limit 400
        """
    )
    out: list[str] = []
    seen: set[int] = set()
    for row in rows:
        tmdb_id = int(row["tmdb_id"] or 0)
        if tmdb_id and tmdb_id in seen:
            continue
        if tmdb_id:
            seen.add(tmdb_id)
        out.append(row["title_id"])
        if len(out) >= limit:
            break
    return out


async def build(
    pool,
    genres_by_id: dict | None = None,
    today: date | None = None,
    discover=None,
) -> int:
    """Rehace las categorías `tema-*` activas (las vencidas desaparecen solas)."""
    changed = 0
    primary_by_id: dict[str, str | None] = {}
    if genres_by_id:
        for title_id, genres in genres_by_id.items():
            primary_by_id[title_id] = genre_rules.primary_genre(genres)
    active = active_themes(today)
    async with pool.acquire() as conn:
        await conn.execute("delete from title_categories where category_id like 'tema-%'")
        await conn.execute("delete from categories where id like 'tema-%'")
        # La foto de TMDB de un tema que ya no está activo no se conserva.
        await conn.execute(
            "delete from theme_tmdb where theme_id <> all($1::text[])",
            [theme.id for theme in active],
        )
        position = 5
        for theme in active:
            ids = await _theme_ids(conn, theme, primary_by_id, discover)
            if len(ids) < theme.min_titles:
                log.info("tema %s: solo %s títulos; no se publica", theme.id, len(ids))
                continue
            await conn.execute(CAT_SQL, theme.id, theme.title, "theme", position)
            position += 1
            changed += await _insert_links(
                conn,
                [(title_id, theme.id, 1 + index) for index, title_id in enumerate(ids[: theme.limit])],
            )
        world = await _world_trending(conn)
        if len(world) >= 10:
            await conn.execute(CAT_SQL, WORLD_TRENDING_ID, WORLD_TRENDING_TITLE, "theme", position)
            changed += await _insert_links(
                conn,
                [(title_id, WORLD_TRENDING_ID, 1 + index) for index, title_id in enumerate(world)],
            )
    return changed
