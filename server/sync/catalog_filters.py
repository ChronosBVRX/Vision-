"""Filtros SQL compartidos por el constructor del índice y las colecciones temáticas.

Centralizarlos evita que el catálogo curado y las temáticas se desincronicen: las dos
deben excluir el nicho que TMDB descartó, los tráilers del portal y usar el mismo
plegado de acentos para buscar por palabras.
"""

# Un título que TMDB descartó (match_confidence 0.0 = no existe en el catálogo mundial)
# no entra a las filas curadas: el portal le pone score inflado y antes dominaba.
NOT_UNMATCHED = (
    "not exists (select 1 from enrichment e where e.title_id = t.id and e.match_confidence < 0.5)"
)

# Los tráilers del portal («(Trailer) The Batman», «（Trailers）Minions 2») son fichas
# aparte que se colaban al lado de la película real en el mismo género. No son el
# título: quedan fuera de las filas curadas (siguen en «Todo el catálogo»/buscador).
NOT_TRAILER = r"not (t.title ~* '^\s*[\(（]\s*trailers?\s*[\)）]')"

# `translate` en vez de `unaccent`: la extensión está instalada en el VPS pero en
# algunas conexiones no plegaba los acentos. Esta lista cubre español/portugués y es
# determinista (misma entrada → misma salida en cualquier Postgres).
_ACCENTS = "áéíóúüñàèìòùâêîôûäëïöüç"
_PLAIN = "aeiouunaeiouaeiouaeiouc"


def fold_sql(expr: str) -> str:
    """Expresión SQL que pasa [expr] a minúsculas y sin acentos (para LIKE de temas)."""
    return f"translate(lower({expr}), '{_ACCENTS}', '{_PLAIN}')"


def keyword_patterns(keywords) -> list[str]:
    """Palabras ya plegadas → patrones `%palabra%` para `like any(array)`."""
    out: list[str] = []
    seen: set[str] = set()
    for keyword in keywords:
        word = (keyword or "").strip().lower().strip("%_")
        if not word or word in seen:
            continue
        seen.add(word)
        out.append(f"%{word}%")
    return out
