"""Agrupador de temporadas: «La Casa de Papel T1 / T2 / (Temporada 3)» son una sola serie.

El catálogo del portal repite la misma obra con nombres distintos: la temporada pegada al
final («House T1» … «House T8») y además variantes de escritura o de idioma («House M.D.»,
«Los Simpson» / «Los Simpsons» / «Os Simpsons», «Anatomía de Grey» / «Anatomía según Grey»).
Aquí se detectan las dos cosas y los miembros se guardan como temporadas de un grupo
(`title_groups` + `group_members`). El API devuelve una entrada por grupo con sus
temporadas, así el teléfono/TV navegan menos y sin repetidos.

Reglas:
1. Solo series/anime/kids con ficha de serie (las películas que el portal mete en esas
   columnas jamás son temporadas: «Zootopia» + «Zootopia+»).
2. Se exige que el título base termine en la marca de temporada (T1, T2, tem1,
   Temporada 3, Season 4, 2ª Temporada, con HD/Completa y año final opcionales); no se
   toca «Parte N». Un título sin marca que coincide con la base entra como temporada 0.
3. Los nombres se comparan ya normalizados (`fold_name`): sin acentos ni signos, con los
   inicialismos con puntos juntos («M.D.» = «MD») y los números en palabra como dígitos
   («Hawaii Five-0» = «Hawai 5.0»).
4. Dos nombres son la MISMA obra (`names_compatible`) si son iguales, si difieren solo en
   un inicialismo corto de credencial («House» / «House M.D.»), si comparten las palabras
   con una errata/plural («Los Simpson» / «Los Simpsons») o si el parecido es alto
   («Anatomía de Grey» / «Anatomía según Grey»). Nunca si uno es un prefijo con subtítulo
   propio o si divergen tras un mismo inicio: «9-1-1» ≠ «9-1-1: Nashville», «Below Deck»
   ≠ «Below Deck Mediterranean», «Vikingos» ≠ «Vikingos: Valhalla», «Power Rangers» ≠
   «Power Rangers Megaforce» (secuelas, ediciones y spin-offs se quedan aparte).
5. El emparejamiento por nombre se apoya en TMDB: solo se fusionan fichas que comparten
   `tmdb_id` (confianza ≥ 0.6). Dos nombres parecidos de obras distintas («The Office» US
   y UK) tienen ids distintos y no se tocan. Cuando los nombres no se parecen (traducción
   real: «Las Kardashian» = «Keeping Up with the Kardashians»), se exige que ambos sean
   nombres oficiales/traducidos de la MISMA entidad de TMDB (confianza ≥ 0.7) y que el
   nombre no sea ambiguo (que no apunte a dos ids distintos en el catálogo).
6. Al fusionar dos nombres puede haber dos fichas de la misma temporada («House of Cards
   T5» de 2013 y 2017): el grupo se queda con todas y el API sirve una sola entrada por
   número de temporada (la de XTV, la más popular), para no pintar dos veces «T5».
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

# Marca pegada a la última palabra («La casa de papelT1», «Godzilla: La SerieT1»): el
# portal no pone espacio y el título se queda con la temporada dentro del nombre.
GLUED_MARK = re.compile(r"(?<=[a-záéíóúñ])T\s*(\d{1,2})\s*$")


def fold(text: str) -> str:
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return " ".join("".join(c if c.isalnum() else " " for c in folded).split())


def _flat(text: str) -> str:
    """Minúsculas sin acentos, conservando separadores (para detectar « - Latino»)."""
    folded = unicodedata.normalize("NFD", (text or "").lower())
    return "".join(c for c in folded if unicodedata.category(c) != "Mn")


# Inicialismos con puntos («M.D.» → «md», «S.W.A.T.» → «swat»): son el mismo nombre con o
# sin puntos, pero el espacio que dejan los puntos no debe separar las letras.
_DOTTED = re.compile(r"(?<![a-z0-9])([a-z])(?:\.\s*([a-z]))+\.?")
# El mismo inicialismo, pero pegado al final del nombre («House M.D.»).
_DOTTED_TAIL = re.compile(r"(?<![a-z0-9])([a-z])(?:\.\s*([a-z]))+\.?\s*$")


def fold_dots(text: str) -> str:
    """Minúsculas con los inicialismos con puntos pegados («House M.D.» → «house md»)."""
    return _DOTTED.sub(lambda m: m.group(0).replace(".", "").replace(" ", ""), (text or "").lower())


# Números en palabra → dígito: «Hawaii Five-0» = «Hawai 5.0».
NUM_WORDS = {
    "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
    "six": "6", "seven": "7", "eight": "8", "nine": "9", "ten": "10",
    "uno": "1", "dos": "2", "tres": "3", "cuatro": "4", "cinco": "5",
    "seis": "6", "siete": "7", "ocho": "8", "nueve": "9", "diez": "10",
    "um": "1", "uma": "1", "dois": "2", "duas": "2",
}

# Artículos y conectores que no cambian la obra («Buffy, la cazavampiros» = «Buffy,
# cazavampiros», «Os Simpsons» = «Los Simpson»).
STOPWORDS = frozenset((
    "the", "los", "las", "el", "la", "de", "del", "of", "and", "y", "a", "en", "in", "to",
    "con", "por", "o", "os", "as", "um", "uma", "e", "do", "da", "dos", "das", "les", "le",
    "il", "lo", "gli", "i", "un", "una", "der", "die", "das", "al", "au",
))

# Un inicialismo de credencial al final del nombre («House M.D.») no es otra obra; uno de
# tres letras o más sí suele ser el subtítulo («Power Rangers S.P.D»).
INITIALS_MAX = 2

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


def name_tokens(text: str) -> list[str]:
    """Palabras de un nombre ya normalizado (inicialismos juntos, números en dígitos)."""
    return [NUM_WORDS.get(part, part) for part in fold(fold_dots(text)).split()]


def fold_name(text: str) -> str:
    """Clave de nombre: la que decide si dos fichas son la misma obra."""
    return " ".join(name_tokens(text))


def _levenshtein(a: str, b: str) -> int:
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def _similar(a: str, b: str) -> float:
    if not a or not b:
        return 0.0
    return 1.0 - _levenshtein(a, b) / max(len(a), len(b))


def _token_eq(a: str, b: str) -> bool:
    """Palabras iguales salvo errata/plural corto («simpson» / «simpsons»)."""
    if a == b:
        return True
    return len(a) >= 5 and len(b) >= 5 and abs(len(a) - len(b)) <= 2 and _levenshtein(a, b) <= 1


def _significant(tokens: list[str]) -> list[str]:
    return [t for t in tokens if t not in STOPWORDS] or list(tokens)


def _overlap(a: list[str], b: list[str]) -> float:
    """Intersección de palabras significativas (0..1) admitiendo plurales."""
    ta, tb = _significant(a), _significant(b)
    inter, used = 0, set()
    for x in ta:
        for j, y in enumerate(tb):
            if j in used:
                continue
            if _token_eq(x, y):
                inter += 1
                used.add(j)
                break
    return inter / (len(ta) + len(tb) - inter) if inter else 0.0


def _is_prefix(a: list[str], b: list[str]) -> bool:
    short, long_ = (a, b) if len(a) <= len(b) else (b, a)
    return len(short) < len(long_) and long_[: len(short)] == short


def _significant_prefix(a: list[str], b: list[str]) -> bool:
    """Uno es el otro más una cola propia («Casa de Papel» / «La casa de papel: Corea»)."""
    return _is_prefix(_significant(a), _significant(b))


def _diverges(a: str, b: str) -> bool:
    """Mismo inicio y cola propia: secuela/edición/spin-off, no la misma obra.

    «9-1-1» vs «9-1-1: Nashville», «Power Rangers» vs «Power Rangers Megaforce»,
    «La venganza de los ex VIP» vs «La venganza de los ex: US».
    """
    ta, tb = _significant(name_tokens(a)), _significant(name_tokens(b))
    common = 0
    for x, y in zip(ta, tb):
        if not _token_eq(x, y):
            break
        common += 1
    if common < 2 or common == min(len(ta), len(tb)):
        return False
    return True


def _initials_tail(long_raw: str, short: list[str], long_: list[str]) -> bool:
    """¿Lo que sobra del nombre largo es un inicialismo con puntos («M.D.»)?"""
    if len(long_) - len(short) > 1:
        return False
    match = _DOTTED_TAIL.search((long_raw or "").lower())
    if not match:
        return False
    extra = name_tokens(match.group(0))
    return len(extra) == 1 and len(extra[0]) <= INITIALS_MAX and extra == long_[len(short):]


def _digits(tokens: list[str]) -> list[str]:
    return sorted(t for t in tokens if t.isdigit())


def _numeric_conflict(a: list[str], b: list[str]) -> bool:
    """¿El número distingue dos obras? Solo si comparten las palabras del título.

    «Deutschland 83» ≠ «Deutschland 86» (misma palabra, otro número), pero «Tres por
    tres» (= Three's Company) no choca con «Full House» aunque sus números no cuadren.
    """
    if _digits(a) == _digits(b):
        return False
    words_a = {t for t in _significant(a) if not t.isdigit()}
    words_b = {t for t in _significant(b) if not t.isdigit()}
    shared = words_a & words_b
    return bool(shared) and len(shared) >= min(len(words_a), len(words_b)) / 2


def names_compatible(a: str, b: str) -> str:
    """Cómo son iguales dos nombres base («same», «initials», «tokens», «fuzzy») o ""."""
    if not a or not b:
        return ""
    fa, fb = fold_name(a), fold_name(b)
    if fa == fb:
        return "same"
    ta, tb = name_tokens(a), name_tokens(b)
    # Un número distinto es otra obra: «Deutschland 83» ≠ «Deutschland 86» ≠ «Deutschland 89».
    if _numeric_conflict(ta, tb):
        return ""
    if _significant_prefix(ta, tb):
        sa, sb = _significant(ta), _significant(tb)
        long_raw, short, long_ = (b, sa, sb) if len(sa) <= len(sb) else (a, sb, sa)
        return "initials" if _initials_tail(long_raw, short, long_) else ""
    if _diverges(a, b):
        return ""
    sa, sb = _significant(ta), _significant(tb)
    if sa and sb and _token_eq(sa[0], sb[0]) and _overlap(ta, tb) >= 0.6:
        return "tokens"
    if _similar(fa, fb) >= 0.86:
        return "fuzzy"
    return ""


def base_name(title: str, kind: str = "series") -> str:
    """Nombre de la obra: sin marca de temporada ni calificador de doblaje."""
    split = split_season(title, kind)
    return strip_qualifier(split[0] if split else (title or ""))


def canon_key(title: str, kind: str) -> tuple[str, str] | None:
    """Clave de agrupación por NOMBRE: series/anime/kids comparten llave (el portal
    reparte la misma obra entre secciones), sin marca de temporada ni calificador."""
    base = base_name(title, kind)
    if not base:
        return None
    key_kind = "series" if kind in ("series", "anime", "kids") else kind
    return key_kind, fold_name(base)


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
    # Marca pegada a la palabra («La casa de papelT1»): solo series/anime.
    if kind in ("series", "anime", "kids"):
        glued = GLUED_MARK.search(title or "")
        if glued:
            base = title[: glued.start()].strip(" -–—:.")
            season = int(glued.group(1))
            if base and 1 <= season <= 50:
                return base, season
    return None


def is_groupable(kind: str, program_type: str) -> bool:
    """¿Esta ficha puede ser temporada de un grupo? Solo series reales.

    El portal mete películas en las columnas kids/anime (kind kids/anime con
    program_type «movie»): «Zootopia» (película 2016) + «Zootopia+» (serie) o
    «Intensa mente» + «Intensa mente 2» no son temporadas de una misma serie.
    """
    return kind in ("series", "anime", "kids") and (program_type or "").strip().lower() != "movie"


# Confianza mínima de TMDB para apoyarse en su identidad al fusionar nombres.
TMDB_MIN_CONF = 0.6
# La traducción real (nombres que no se parecen) exige un match más firme.
TMDB_ALIAS_CONF = 0.7


def official_names(row) -> set[str]:
    """Nombres oficiales de la entidad TMDB (original + localizado)."""
    return {fold_name(row[key]) for key in ("original_title", "localized_title") if row.get(key)}


def known_names(row) -> set[str]:
    """Nombres oficiales + traducciones/alternativos de la entidad TMDB.

    Solo cuentan los que son nombres completos (dos palabras o más): un alternativo
    suelto («Anne») no identifica una obra.
    """
    names = set(official_names(row))
    for key in ("alt_titles", "translated_titles"):
        for alt in row.get(key) or ():
            if len(fold_name(alt).split()) >= 2:
                names.add(fold_name(alt))
    return names


async def group(pool) -> int:
    """Reconstruye los grupos de temporada desde cero (la tabla es chica y así no hay restos)."""
    rows = await pool.fetch(
        """
        select t.id, t.title, t.kind, t.source, t.year, t.score, t.poster_url, t.synopsis,
               t.play_ref, t.program_type, coalesce(t.popularity, 0) as popularity,
               e.tmdb_id,
               coalesce(e.match_confidence, 0) as match_confidence,
               coalesce(e.original_title, '') as original_title,
               coalesce(e.localized_title, '') as localized_title,
               coalesce(e.alt_titles, '{}') as alt_titles,
               coalesce(e.translated_titles, '{}') as translated_titles
        from titles t
        left join enrichment e on e.title_id = t.id
        where t.kind in ('series', 'anime', 'kids')
        """
    )

    # 1) Nombres: cada ficha entra en la llave de SU nombre (sin marca ni calificador).
    buckets: dict[tuple[str, str], dict] = {}
    for row in rows:
        if not is_groupable(row["kind"], row["program_type"]):
            continue
        title = row["title"] or ""
        split = split_season(title, row["kind"])
        base = strip_qualifier(split[0] if split else title)
        if not base:
            continue
        key = ("series", fold_name(base))
        info = buckets.setdefault(key, {"bases": [], "members": [], "tmdb": set()})
        info["members"].append((dict(row), split[1] if split else 0, base))
        if base not in info["bases"]:
            info["bases"].append(base)
        tmdb_id = int(row["tmdb_id"] or 0)
        if tmdb_id and float(row["match_confidence"] or 0) >= TMDB_MIN_CONF:
            info["tmdb"].add(tmdb_id)

    if not buckets:
        return 0

    # 2) Identidad TMDB: une llaves de nombre distinto que la misma entidad de TMDB explica.
    by_tmdb: dict[int, list[tuple[str, str]]] = {}
    for key, info in buckets.items():
        for tmdb_id in info["tmdb"]:
            by_tmdb.setdefault(tmdb_id, []).append(key)
    # Un nombre que apunta a dos entidades distintas no sirve como evidencia.
    ids_by_name: dict[str, set[int]] = {}
    for key, info in buckets.items():
        for tmdb_id in info["tmdb"]:
            ids_by_name.setdefault(key[1], set()).add(tmdb_id)

    def best_row(key) -> dict:
        return max(buckets[key]["members"], key=lambda m: float(m[0]["match_confidence"] or 0))[0]

    def rep_base(key) -> str:
        bases = buckets[key]["bases"] or [""]
        return min(bases, key=lambda b: (len(b), b))

    parent: dict[tuple[str, str], tuple[str, str]] = {key: key for key in buckets}

    def find(key):
        while parent[key] != key:
            parent[key] = parent[parent[key]]
            key = parent[key]
        return key

    def best_conf(key) -> float:
        return float(best_row(key)["match_confidence"] or 0)

    def linked(a, b) -> bool:
        """¿Las dos llaves de nombre son la misma obra? (misma entidad TMDB de fondo)."""
        name_a, name_b = rep_base(a), rep_base(b)
        conf = max(best_conf(a), best_conf(b))
        how = names_compatible(name_a, name_b)
        if how and conf >= TMDB_MIN_CONF:
            return True
        # Traducción real: los dos nombres son nombres oficiales de la MISMA entidad.
        if how or conf < TMDB_ALIAS_CONF:
            return False
        if _numeric_conflict(name_tokens(name_a), name_tokens(name_b)):
            return False
        if _diverges(name_a, name_b):
            return False
        if _is_prefix(name_tokens(name_a), name_tokens(name_b)) or _significant_prefix(
            name_tokens(name_a), name_tokens(name_b)
        ):
            return False
        fa, fb = fold_name(name_a), fold_name(name_b)
        if any(len(ids_by_name.get(name, ())) > 1 for name in (fa, fb)):
            return False
        return fa in known_names(best_row(b)) and fb in known_names(best_row(a))

    for keys in by_tmdb.values():
        if len(keys) < 2:
            continue
        # Grupos de nombres mutuamente compatibles (cliques): se fusionan entre sí, pero
        # nunca a través de un parecido intermedio — así «Vikingos: Valhalla» se junta con
        # «Vikings: Valhalla» sin arrastrar a «Vikingos».
        degree = {key: sum(1 for other in keys if other != key and linked(key, other)) for key in keys}
        order = sorted(keys, key=lambda k: (-degree[k], len(buckets[k]["members"]), rep_base(k)))
        used: set[tuple[str, str]] = set()
        for key in order:
            if key in used:
                continue
            clique = [key]
            for other in order:
                if other in used or other == key:
                    continue
                if all(linked(other, member) for member in clique):
                    clique.append(other)
            used.update(clique)
            for member in clique[1:]:
                parent[find(member)] = find(clique[0])

    merged: dict[tuple[str, str], list] = {}
    for key in buckets:
        merged.setdefault(find(key), []).extend(buckets[key]["members"])

    built = 0
    async with pool.acquire() as conn:
        # Todo o nada: si el ciclo se interrumpe, la tabla nunca queda a medias.
        async with conn.transaction():
            await conn.execute("delete from group_members")
            await conn.execute("delete from title_groups")
            for root, members in merged.items():
                if len(members) < 2:
                    continue
                primary = max(members, key=lambda m: (m[0]["score"] or 0, m[0]["year"] or 0))
                # La base más corta manda en pantalla («Los Simpson» sobre
                # «Los Simpson - Español Latino»): es el nombre limpio.
                candidates = [m[2] for m in members if m[2]] or [primary[0]["title"]]
                base_title = min(candidates, key=lambda b: (len(b), b))
                group_id = f"g-{slug(base_title)}-series"
                await conn.execute(
                    """
                    insert into title_groups (id, base_title, kind, primary_title_id)
                    values ($1, $2, $3, $4)
                    on conflict (id) do update set base_title = excluded.base_title,
                        kind = excluded.kind, primary_title_id = excluded.primary_title_id, updated_at = now()
                    """,
                    group_id,
                    base_title,
                    "series",
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
