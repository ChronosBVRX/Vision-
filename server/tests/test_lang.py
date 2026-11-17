"""La API no debe servir sinopsis en inglés (regla: ficha siempre en español)."""

import lang


def test_spanish_synopsis_is_kept():
    text = "Un vaquero solitario llega al pueblo y descubre una conspiración."
    assert lang.spanish_only(text) == text


def test_clear_english_synopsis_is_dropped():
    text = (
        "When a controlled avalanche blast goes wrong, Martia and her family are "
        "trapped in a crashed cable car beneath the ice."
    )
    assert lang.spanish_only(text) == ""


def test_an_accented_name_does_not_make_an_english_synopsis_spanish():
    text = (
        "In 1992 Chile, nine-year-old Inés bonds with teenage German skier Hanna "
        "at a remote mountain hotel. When Hanna mysteriously disappears, Inés is "
        "left to discover the truth."
    )
    assert lang.spanish_only(text) == ""


def test_empty_and_ambiguous_short_texts_are_safe():
    assert lang.spanish_only("") == ""
    assert lang.spanish_only("Frostbite") == "Frostbite"
    assert lang.spanish_only("México") == "México"
