"""Agrupador de temporadas: «La Casa de Papel T1 / T2 / (Temporada 3)» son una sola serie.

El catálogo del portal repite el mismo título con la temporada pegada al final; aquí se
detecta la marca, se separa el título base y los miembros se guardan como temporadas de un
grupo (`title_groups` + `group_members`). El API devuelve una entrada por grupo con sus
temporadas, así el teléfono/TV navegan menos y sin repetidos.

Reglas: solo series/anime/kids; se exige que el título base termine en la marca de temporada
(T1, T2, tem1, Temporada 3, Season 4, 2ª Temporada, con HD/Completa y año final opcionales); no se toca «Parte N».
Un título sin marca que coincide exactamente con la base entra como temporada 0 (serie completa).
"""

import logging
import re
import unicodedata

log = logging.getLogger("grouping")

# Un año al final («Show T1 (2022)», «Show 2 2024») no rompe la marca: se ignora.
YEAR_SUFFIX = r"(?:\s*[\(\[]?\s*(?:19|20)\d{2}\s*[\)\]]?)?"

MARKS = (
    re.compile(r"\s*[\(\[]?\b(?:t|tem|temp|temporada|season|s)\s*[.:\-]?\s*(\d{1,2})\b[\)\]]?\s*(?:hd|fhd|completa|completo)?" + YEAR_SUFFIX + r"\s*$", re.IGNORECASE),
    re.compile(r"\s*[\(\[]?\b(\d{1,2})\s*[ªa]?\s*temporada\b[\)\]]?\s*(?:hd|fhd|completa|completo)?" + YEAR_SUFFIX + r"\s*$", re.IGNORECASE),
    re.compile(r"\s+(\d{1,2})\s*(?:hd|fhd|completa|completo)?" + YEAR_SUFFIX + r"\s*$", re.IGNORECASE),
)

# Colas que indican capítulo/parte/volumen, no temporada (solo para número solo).
NON_SEASON_TAIL = ("cap", "capitulo", "capítulo", "episodio", "episode", "ep",
                   "parte", "part", "vol", "volumen", "volume")


def fold(text: str) -> str:
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return " ".join("".join(c if c.isalnum() else " " for c in folded).split())


def _flat(text: str) -> str:
    """Minúsculas sin acentos, conservando separadores (para detectar « - Latino»)."""
    folded = unicodedata.normalize("NFD", (text or "").lower())
    return "".join(c for c in folded if unicodedata.category(c) != "Mn")


# Calificadores que no cambian la obra («Show - Español Latino» = «Show»): se ignoran
# al agrupar para juntar doblajes y ediciones. Solo idiomas/calidades con separador
# explícito («Orgullo Latino» sin guion NO se toca).
QUALIFIERS = (
    "español latino", "latino", "castellano", "subtitulada", "subtitulado",
    "doblada", "doblado", "audio latino", "hd", "fhd", "4k", "uhd",
)
_QUAL_FLAT = frozenset(_flat(q) for q in QUALIFIERS)
_QUAL_RX = re.compile(r"^(.*)(?:\s+[-\u2013\u2014:]\s*|\s*[\(\[])\s*(.+?)\s*[\)\]]?\s*$")


def strip_qualifier(base: str) -> str:
    """Quita el calificador final (« - Español Latino», «(HD)», «HD» solo) si es conocido."""
    m = _QUAL_RX.match(base or "")
    if m and _flat(m.group(2)) in _QUAL_FLAT:
        return m.group(1).strip(" -–—:.")
    # Calidad suelta sin separador («Show HD»): nunca es parte del nombre.
    m2 = re.match(r"^(.*)\s+(?:hd|fhd|4k|uhd)\s*$", base or "", re.IGNORECASE)
    if m2 and m2.group(1).strip():
        return m2.group(1).strip()
    return base


def canon_key(title: str, kind: str) -> tuple[str, str] | None:
    """Clave de agrupación: series/anime/kids comparten llave (el portal reparte la misma
    obra entre secciones), sin marca de temporada ni calificador de doblaje."""
    s = split_season(title, kind)
    base = strip_qualifier(s[0] if s else title)
    if not base:
        return None
    key_kind = "series" if kind in ("series", "anime", "kids") else kind
    return key_kind, fold(base)


def slug(text: str) -> str:
    return fold(text).replace(" ", "-") or "grupo"


def split_season(title: str, kind: str = "series") -> tuple[str, int] | None:
    """(título base, temporada) si el título termina en marca; None si no la trae.

    La marca de número solo («... cielos 2», sin T/Temporada) solo vale para
    series/anime (las películas/kids con número final suelen ser sagas sueltas),
    y no cuando la base termina en capítulo/parte/volumen.
    """
    for idx, pattern in enumerate(MARKS):
        match = pattern.search(title or "")
        if not match:
            continue
        base = title[: match.start()].strip(" -–—:.")
        season = int(match.group(1))
        if not base or not 1 <= season <= 50:
            continue
        if idx == 2:
            if kind not in ("series", "anime"):
                continue
            tail = fold(base).split(" ")
            if tail and tail[-1] in NON_SEASON_TAIL:
                continue
        return base, season
    return None


async def group(pool) -> int:
    """Reconstruye los grupos de temporada desde cero (la tabla es chica y así no hay restos)."""
    rows = await pool.fetch(
        """
        select id, title, kind, source, year, score, poster_url, synopsis, play_ref
        from titles where kind in ('series', 'anime', 'kids')
        """
    )
    by_fold: dict[tuple[str, str], dict] = {}
    marked: dict[tuple[str, str], list] = {}
    for row in rows:
        title = row["title"] or ""
        split = split_season(title, row["kind"])
        if split is None:
            # Solo los títulos sin marca entran como "serie completa" (si no, un T1
            # haría de temporada 0 de su propio grupo).
            key = canon_key(title, row["kind"])
            if key is not None:
                by_fold.setdefault(key, dict(row))
            continue
        base, season = split
        mkey = canon_key(base, row["kind"])
        if mkey is None:
            continue
        marked.setdefault(mkey, []).append((dict(row), season, base))

    built = 0
    async with pool.acquire() as conn:
        # Todo o nada: si el ciclo se interrumpe, la tabla nunca queda a medias.
        async with conn.transaction():
            await conn.execute("delete from group_members")
            await conn.execute("delete from title_groups")
            for (kind, base_fold), members in marked.items():
                plain = by_fold.get((kind, base_fold))
                if plain is not None and all(m[0]["id"] != plain["id"] for m in members):
                    members.append((plain, 0, plain["title"]))
                if len(members) < 2:
                    continue
                primary = max(members, key=lambda m: (m[0]["score"] or 0, m[0]["year"] or 0))
                # La base más corta manda en pantalla («Los Simpson» sobre
                # «Los Simpson - Español Latino»): es el nombre limpio.
                candidates = [m[2] for m in members if m[2]] or [primary[0]["title"]]
                base_title = min(candidates, key=len)
                group_id = f"g-{slug(base_title)}-{kind}"
                await conn.execute(
                    """
                    insert into title_groups (id, base_title, kind, primary_title_id)
                    values ($1, $2, $3, $4)
                    on conflict (id) do update set base_title = excluded.base_title,
                        kind = excluded.kind, primary_title_id = excluded.primary_title_id, updated_at = now()
                    """,
                    group_id,
                    base_title,
                    kind,
                    primary[0]["id"],
                )
                for row, season, _ in members:
                    await conn.execute(
                        """
                        insert into group_members (group_id, title_id, season) values ($1, $2, $3)
                        on conflict (group_id, title_id) do update set season = excluded.season
                        """,
                        group_id,
                        row["id"],
                        season,
                    )
                built += 1
    log.info("agrupador: %s grupos de temporada", built)
    return built
