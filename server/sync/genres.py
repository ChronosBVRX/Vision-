"""Géneros canónicos de Vision+.

El catálogo mezclaba dos fuentes de géneros:
  - Los `tags` del portal, que traen cosas que NO son géneros («Nacional»,
    «Scripted», «Game Show», «Corto», «Talk Show», «Anime»…) y variantes en inglés
    («Comedy», «Documentary», «Music»).
  - Los géneros de TMDB, que son los reales pero llegan con nombres mezclados
    («Suspense»/«Suspenso», «Sci-Fi & Fantasy», «Action & Adventure», «Bélica»…).

Este módulo deja UNA lista corta y en español, y elige **un género primario** por
título. El primario es el que manda en las filas del catálogo: así una película que
es «Drama, Comedia, Romance» sale en Romance y no se repite en las tres categorías
(la queja concreta de «siempre salen las mismas películas en los géneros»).

Reglas:
  - Un título puede tener varios géneros canónicos (para «Relacionadas»), pero solo
    se indexa en la fila de su primario.
  - Los combos de TMDB TV se abren en sus dos géneros reales.
  - Lo que no es un género se descarta: no se inventan categorías.
"""

import unicodedata

# Nombres canónicos (español, sin duplicados de inglés/portugués). El orden es el
# que se usa para mostrarlos en «Relacionadas» y para desempatar.
CANONICAL = (
    "Acción",
    "Aventuras",
    "Animación",
    "Comedia",
    "Crimen",
    "Documental",
    "Drama",
    "Familia",
    "Fantasía",
    "Guerra",
    "Historia",
    "Infantil",
    "Misterio",
    "Música",
    "Occidental",
    "Reality",
    "Romance",
    "Ciencia ficción",
    "Suspenso",
    "Telenovela",
    "Deporte",
)

# Prioridad del género primario: primero lo más específico (terror, suspenso,
# ciencia ficción, acción…), al final los géneros «paraguas» (comedia y drama).
# Así «Drama + Comedia» queda en Comedia y «Drama» solo conserva los dramas puros:
# cada fila dice algo distinto y no hay una categoría que se coma a las demás.
PRIMARY_PRIORITY = (
    "Telenovela",
    "Terror",
    "Suspenso",
    "Acción",
    "Ciencia ficción",
    "Animación",
    "Crimen",
    "Misterio",
    "Guerra",
    "Occidental",
    "Aventuras",
    "Fantasía",
    "Música",
    "Deporte",
    "Documental",
    "Reality",
    "Historia",
    "Romance",
    "Familia",
    "Infantil",
    "Comedia",
    "Drama",
)

# En Novelas manda el género romántico (es lo que la gente espera de una
# telenovela), no «Crimen» o «Acción» aunque la ficha los traiga de relleno.
NOVELA_PRIORITY = (
    "Romance",
    "Comedia",
    "Drama",
    "Suspenso",
    "Misterio",
    "Familia",
    "Historia",
    "Crimen",
    "Acción",
    "Aventuras",
    "Fantasía",
    "Ciencia ficción",
    "Terror",
    "Música",
    "Reality",
    "Documental",
    "Infantil",
    "Guerra",
    "Occidental",
    "Deporte",
)

# alias plegado (sin acentos, minúsculas) -> géneros canónicos. Una tupla vacía
# significa «esto no es un género» y se descarta a propósito.
_ALIASES: dict[str, tuple[str, ...]] = {
    # Acción
    "accion": ("Acción",),
    "action": ("Acción",),
    # Aventuras
    "aventura": ("Aventuras",),
    "aventuras": ("Aventuras",),
    "adventure": ("Aventuras",),
    # Animación
    "animacion": ("Animación",),
    "animation": ("Animación",),
    "cartoon": ("Animación",),
    "dibujos animados": ("Animación",),
    # Comedia
    "comedia": ("Comedia",),
    "comedy": ("Comedia",),
    # Crimen
    "crimen": ("Crimen",),
    "crime": ("Crimen",),
    # Documental
    "documental": ("Documental",),
    "documentary": ("Documental",),
    # Drama
    "drama": ("Drama",),
    # Familia
    "familia": ("Familia",),
    "family": ("Familia",),
    # Fantasía
    "fantasia": ("Fantasía",),
    "fantasy": ("Fantasía",),
    # Guerra (TMDB: Bélica / War & Politics)
    "guerra": ("Guerra",),
    "war": ("Guerra",),
    "belica": ("Guerra",),
    "war & politics": ("Guerra",),
    # Historia (Biografía es de la misma familia; no es un género propio)
    "historia": ("Historia",),
    "history": ("Historia",),
    "biografia": ("Historia",),
    "biography": ("Historia",),
    # Infantil
    "infantil": ("Infantil",),
    "kids": ("Infantil",),
    # Misterio
    "misterio": ("Misterio",),
    "mystery": ("Misterio",),
    # Música
    "musica": ("Música",),
    "music": ("Música",),
    "musical": ("Música",),
    # Occidental
    "occidental": ("Occidental",),
    "western": ("Occidental",),
    # Reality
    "reality": ("Reality",),
    "reality-tv": ("Reality",),
    "reality tv": ("Reality",),
    # Romance
    "romance": ("Romance",),
    "romantico": ("Romance",),
    "romantica": ("Romance",),
    # Ciencia ficción
    "ciencia ficcion": ("Ciencia ficción",),
    "sci-fi": ("Ciencia ficción",),
    "sci fi": ("Ciencia ficción",),
    "science fiction": ("Ciencia ficción",),
    # Terror
    "terror": ("Terror",),
    "horror": ("Terror",),
    # Suspenso
    "suspenso": ("Suspenso",),
    "suspense": ("Suspenso",),
    "thriller": ("Suspenso",),
    # Telenovela
    "telenovela": ("Telenovela",),
    "novela": ("Telenovela",),
    "novelas": ("Telenovela",),
    "soap": ("Telenovela",),
    "soap opera": ("Telenovela",),
    "dorama": ("Telenovela",),
    # Deporte
    "deporte": ("Deporte",),
    "sport": ("Deporte",),
    "sports": ("Deporte",),
    # Combos de TMDB TV: se abren en sus dos géneros reales.
    "sci-fi & fantasy": ("Ciencia ficción", "Fantasía"),
    "action & adventure": ("Acción", "Aventuras"),
    # No son géneros (se descartan): formatos, públicos, etiquetas del portal.
    "anime": (),
    "nacional": (),
    "scripted": (),
    "game show": (),
    "game-show": (),
    "talk show": (),
    "talk": (),
    "news": (),
    "noticias": (),
    "corto": (),
    "short": (),
    "pelicula de tv": (),
    "tv movie": (),
    "especial": (),
    "special": (),
    "teleplay": (),
    "movie": (),
}

_CANONICAL_SET = frozenset(CANONICAL)
# Del más prioritario al menos: permite devolver los géneros en el orden en que se
# muestran y elegir el primario con un solo recorrido.
_ORDER = {name: index for index, name in enumerate(PRIMARY_PRIORITY)}


def fold(text: str) -> str:
    """Minúsculas, sin acentos, signos como espacios («Sci-Fi & Fantasy» → «sci-fi & fantasy»)."""
    decomposed = unicodedata.normalize("NFD", (text or "").lower())
    plain = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    spaced = "".join(ch if ch.isalnum() or ch in "-&" else " " for ch in plain)
    return " ".join(spaced.split())


def canonical_genres(*groups) -> list[str]:
    """Une una o varias listas de géneros en nombres canónicos, sin duplicados.

    Los nombres desconocidos se descartan: si el portal manda «Nacional» o
    «Scripted», no se crea una categoría con eso. El resultado sale en el orden de
    prioridad del género primario (lo específico primero).
    """
    found: set[str] = set()
    for group in groups:
        for raw in group or ():
            for name in _ALIASES.get(fold(str(raw)), ()):
                found.add(name)
    return sorted(found, key=lambda name: _ORDER.get(name, len(_ORDER)))


def primary_genre(genres, drop=(), priority=None) -> str | None:
    """El género que manda en la fila del catálogo (el primero por prioridad).

    `drop` permite excluir géneros en una sección concreta: en Novelas se descarta
    «Telenovela» (todas las novelas lo son y la fila quedaría sola) para repartirlas
    por Romance/Comedia/Drama/Suspenso. `priority` cambia el orden en esa sección.
    """
    blocked = set(drop)
    present = set(genres or ())
    for name in priority or PRIMARY_PRIORITY:
        if name in present and name not in blocked:
            return name
    return None


def resolve(portal_genres, tmdb_genres=None, tmdb_matched: bool = False) -> list[str]:
    """Géneros canónicos de un título: manda TMDB (si el match es bueno) y el portal
    rellena cuando TMDB aún no tiene géneros."""
    source = tmdb_genres if tmdb_matched and tmdb_genres else portal_genres
    return canonical_genres(source or ())


def is_known(name: str) -> bool:
    return name in _CANONICAL_SET


def aliases_for(name: str) -> tuple[str, ...]:
    """Los nombres crudos (plegados) que se mapean a un género canónico.

    Las temáticas los usan para consultar `titles.genres`/`enrichment.genres` sin
    depender de si el portal escribió «Suspenso», «Suspense» o «Thriller».
    """
    return tuple(alias for alias, names in _ALIASES.items() if name in names)
