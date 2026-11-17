"""Filtro de idioma para los textos que sirve la API.

La regla del producto es que las sinopsis salgan siempre en español. TMDB
traduce casi todo, pero algunos estrenos no tienen sinopsis es-* y el portal
solo ofrece inglés: en ese caso es preferible una ficha sin sinopsis a una
ficha en inglés. Las heurísticas son deliberadamente conservadoras: si el
texto no se reconoce como inglés, se conserva (puede ser español corto).
"""

from __future__ import annotations

import re

SPANISH_WORDS = frozenset("""
el la los las un una unos unas de del que y en con por para como mas pero sus
se al lo es son esta estan cuando donde mientras tras sobre entre desde hasta
sin tambien todo todos este estos estas su no le les ya muy fue ser tiene
""".split())

ENGLISH_WORDS = frozenset("""
the and of to in is are was were with for from his her their they when after
before but not who what this that into on at by as it she he we you has have
had will would can could about while an been there which its our
""".split())

_WORD = re.compile(r"[^a-záéíóúñü]+")


def _words(text: str) -> list[str]:
    return [word for word in _WORD.split(text.lower()) if len(word) >= 2]


def _looks_spanish(text: str) -> bool:
    lower = text.lower()
    if any(char in "áéíóúñü¿¡" for char in lower):
        return True
    words = _words(lower)
    if not words:
        return False
    hits = sum(1 for word in words if word in SPANISH_WORDS)
    return hits >= 3 or (len(words) <= 12 and hits >= 2)


def _looks_english(text: str) -> bool:
    words = _words(text.lower())
    if len(words) < 5:
        return False
    hits = sum(1 for word in words if word in ENGLISH_WORDS)
    return hits >= 3 or (len(words) <= 12 and hits >= 2)


def spanish_only(text: str) -> str:
    """Texto en español o vacío si está claramente en inglés.

    El inglés manda cuando hay varias palabras funcionales inglesas: un nombre
    con tilde («Inés») no convierte una sinopsis inglesa en española.
    """
    clean = (text or "").strip()
    if not clean:
        return ""
    if _looks_english(clean):
        return ""
    if _looks_spanish(clean):
        return clean
    return clean
