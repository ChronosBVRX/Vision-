"""El texto de búsqueda de cada título (`titles.search_text`) — nombres como la gente los busca.

Problema medido en vivo (2026-10-18): el buscador solo veía lo que el portal llama al
título. Una película guardada como «Finding Nemo» no aparecía al buscar «Buscando a
Nemo» (su nombre es-MX de TMDB), «Moana» no aparecía con «Vaiana» (su nombre regional
en España) y los typos («termintor 2») no daban nada.

Este módulo compone, por título, todos los NOMBRES conocidos — título del portal,
alias del portal, título es-MX de TMDB, su original (inglés) y los alternativos por
región — cada uno en dos formas:

- **plegada**: minúsculas, sin acentos, signos → espacios («Spider-Man» → «spider man»),
- **compacta**: la plegada sin espacios («spiderman»), para que un solo token encuentre
  lo que el portal separa con guiones o puntos.

El buscador del API (`api/app.py`) matchea PRIMERO contra esta columna (con índice GIN
de trigramas: rápida a 100k+ títulos) y solo si no hay resultados cae a la búsqueda
profunda (reparto/dirección/sinopsis) y al fuzzy con trigramas. El reparto y la
dirección NO viven aquí a propósito: en la etapa rápida son ruido (una palabra corta
como «x» matchea cualquier reparto) y la etapa profunda ya los cubre.
"""

import os
import unicodedata

# El texto se trunca a este largo (los primeros nombres — los importantes — nunca
# se pierden; los alternativos de la cola quedan si hay espacio).
MAX_SEARCH_TEXT = 1200

# Tope de títulos por pasada de mantenimiento del ciclo (los títulos nuevos y los
# recién enriquecidos se rehacen solos; esto solo acota el costo por ciclo).
REBUILD_BATCH = int(os.environ.get("SEARCH_TEXT_BATCH", "20000"))


def fold(text: str) -> str:
    """Minúsculas sin acentos, signos → espacios, espacios colapsados.

    Espejo exacto de `api/app.py::_fold` y del `_norm` de tmdb: el texto guardado y
    la consulta deben plegarse con el MISMO criterio o el LIKE no encuentra nada.
    """
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return " ".join("".join(c if c.isalnum() else " " for c in folded).split())


def build_search_text(
    title: str,
    portal_original: str = "",
    localized: str = "",
    original: str = "",
    alt_titles=(),
) -> str:
    """El texto de búsqueda: nombres plegados + compactos, sin repetidos, acotado.

    Orden de importancia (el truncado respeta este orden): título del portal, alias
    del portal, es-MX de TMDB, original de TMDB y por último los alternativos.
    """
    parts: list[str] = []
    for name in (title, portal_original, localized, original, *alt_titles):
        folded = fold(name)
        if not folded:
            continue
        parts.append(folded)
        compact = folded.replace(" ", "")
        if compact != folded and len(compact) >= 4:
            parts.append(compact)
    text = " ".join(dict.fromkeys(parts))
    if len(text) > MAX_SEARCH_TEXT:
        text = text[:MAX_SEARCH_TEXT].rsplit(" ", 1)[0]
    return text


_SELECT_MATCHING = """
    select t.id, t.title,
           coalesce(t.original_title, '') as portal_original,
           coalesce(e.localized_title, '') as localized,
           coalesce(e.original_title, '') as original,
           e.alt_titles
    from titles t
    left join enrichment e on e.title_id = t.id
"""


async def rebuild(pool, ids=None, limit: int | None = None) -> int:
    """Rehace el texto de búsqueda: de los `ids` dados, o de los pendientes del ciclo.

    Pendientes = títulos sin texto (recién insertados por los conectores) o cuyo
    enriquecimiento es más nuevo que su texto (un match/alias recién guardado). El
    tope por pasada mantiene acotado el costo; el resultado se aplica en lote.
    """
    if ids:
        rows = await pool.fetch(_SELECT_MATCHING + " where t.id = any($1::text[])", list(ids))
    else:
        rows = await pool.fetch(
            _SELECT_MATCHING
            + """
            where t.search_text = ''
               or e.updated_at > coalesce(t.search_text_at, 'epoch'::timestamptz)
            order by (t.search_text = '') desc, t.updated_at desc
            limit $1
            """,
            limit or REBUILD_BATCH,
        )
    if not rows:
        return 0
    updates = [
        (
            row["id"],
            build_search_text(
                row["title"],
                row["portal_original"],
                row["localized"],
                row["original"],
                list(row["alt_titles"] or []),
            ),
        )
        for row in rows
    ]
    async with pool.acquire() as conn:
        await conn.executemany(
            "update titles set search_text = $2, search_text_at = now() where id = $1",
            updates,
        )
    return len(updates)


async def rebuild_all(pool, batch: int = 5000) -> int:
    """Rehace el texto de TODOS los títulos en lotes (el estreno de la columna).

    En régimen normal esto no se necesita (los pendientes se hacen solos); sirve para
    el arranque de la función o si el criterio de composición cambia. El texto viejo
    sigue sirviendo mientras se rehace (solo se reinicia el marcador de frescura).
    """
    async with pool.acquire() as conn:
        await conn.execute("update titles set search_text_at = null")
    total = 0
    while True:
        rows = await pool.fetch(
            _SELECT_MATCHING
            + """
            where t.search_text_at is null
            order by t.popularity desc nulls last, t.updated_at desc
            limit $1
            """,
            batch,
        )
        if not rows:
            return total
        updates = [
            (
                row["id"],
                build_search_text(
                    row["title"],
                    row["portal_original"],
                    row["localized"],
                    row["original"],
                    list(row["alt_titles"] or []),
                ),
            )
            for row in rows
        ]
        async with pool.acquire() as conn:
            await conn.executemany(
                "update titles set search_text = $2, search_text_at = now() where id = $1",
                updates,
            )
        total += len(updates)
        print(f"search_text: {len(updates)} (total {total})", flush=True)


if __name__ == "__main__":
    import asyncio
    import sys

    import asyncpg

    async def _main() -> None:
        pool = await asyncpg.create_pool(os.environ["DATABASE_URL"], min_size=1, max_size=3)
        try:
            if "--pending" in sys.argv:
                # Sanación: rehace solo los textos viejos (enriquecimiento más nuevo
                # que el texto) — útil tras una pasada interrumpida.
                total = 0
                while True:
                    n = await rebuild(pool, limit=20000)
                    total += n
                    print(f"search_text pendientes: {n} (total {total})", flush=True)
                    if n == 0:
                        break
                print(f"search_text sanado: {total}")
            else:
                total = await rebuild_all(pool)
                print(f"search_text completo: {total}")
        finally:
            await pool.close()

    asyncio.run(_main())
