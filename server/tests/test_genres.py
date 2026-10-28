"""Géneros canónicos: lo que es género se queda, lo que no se descarta, y cada
título tiene UN primario para no repetirse en Drama/Suspenso/Comedia a la vez."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import genres


def test_horror_is_indexed_in_spanish_and_english():
    # Regresión: «Terror»/«Horror» se quedaron sin alias y la fila desaparecía del
    # catálogo (el género más pedido para Día de Muertos/Halloween).
    assert genres.canonical_genres(["Terror"]) == ["Terror"]
    assert genres.canonical_genres(["Horror"]) == ["Terror"]
    assert genres.primary_genre(["Terror", "Misterio"]) == "Terror"
    assert {"terror", "horror"} <= set(genres.aliases_for("Terror"))


def test_tmdb_spanish_variants_become_one_name():
    assert genres.canonical_genres(["Suspense"]) == ["Suspenso"]
    assert genres.canonical_genres(["Bélica"]) == ["Guerra"]
    assert genres.canonical_genres(["Western"]) == ["Occidental"]
    assert genres.canonical_genres(["Romance"]) == ["Romance"]
    assert genres.canonical_genres(["Película de TV"]) == []


def test_tmdb_tv_combos_open_into_two_real_genres():
    assert genres.canonical_genres(["Sci-Fi & Fantasy"]) == [
        "Ciencia ficción",
        "Fantasía",
    ]
    assert genres.canonical_genres(["Action & Adventure"]) == ["Acción", "Aventuras"]
    assert genres.canonical_genres(["War & Politics"]) == ["Guerra"]


def test_portal_junk_is_not_a_genre():
    # Medido en el catálogo: estas etiquetas creaban filas que no significan nada.
    junk = [
        "Nacional", "Scripted", "Game Show", "Game-Show",
        "Talk Show", "Corto", "Short", "Anime", "Teleplay", "Movie", "News",
    ]
    for name in junk:
        assert genres.canonical_genres([name]) == [], name
    # «Cartoon» sí es animación: se une a la fila real en vez de crear otra.
    assert genres.canonical_genres(["Cartoon"]) == ["Animación"]
    # Y si vienen mezcladas con un género real, solo queda el real.
    assert genres.canonical_genres(["Nacional", "Drama"]) == ["Drama"]


def test_english_and_spanish_names_do_not_duplicate_a_row():
    assert genres.canonical_genres(["Drama", "drama", "DRAMA"]) == ["Drama"]
    assert genres.canonical_genres(["Comedy", "Comedia"]) == ["Comedia"]
    assert genres.canonical_genres(["Documentary", "Documental"]) == ["Documental"]
    assert genres.canonical_genres(["Music", "Musical", "Música"]) == ["Música"]


def test_primary_prefers_the_specific_genre_over_the_umbrella():
    # La queja: una comedia dramática salía en Drama Y Comedia. Ahora es Comedia.
    assert genres.primary_genre(["Drama", "Comedia"]) == "Comedia"
    assert genres.primary_genre(["Romance", "Drama"]) == "Romance"
    assert genres.primary_genre(["Acción", "Aventuras", "Ciencia ficción"]) == "Acción"
    assert genres.primary_genre(["Animación", "Aventuras"]) == "Animación"
    assert genres.primary_genre(["Terror", "Misterio"]) == "Terror"
    assert genres.primary_genre(["Documental", "Historia"]) == "Documental"


def test_primary_can_drop_a_genre_for_a_section():
    # En Novelas todas son telenovelas: sin descartarlo, la sección tendría una
    # sola fila. Se reparten por el siguiente género real.
    assert genres.primary_genre(["Telenovela", "Romance", "Drama"]) == "Telenovela"
    assert (
        genres.primary_genre(
            ["Telenovela", "Romance", "Drama"], drop={"Telenovela"}
        )
        == "Romance"
    )
    # En la sección Novelas manda Romance aunque la ficha traiga Crimen/Acción.
    assert (
        genres.primary_genre(
            ["Telenovela", "Crimen", "Romance"],
            drop={"Telenovela"},
            priority=genres.NOVELA_PRIORITY,
        )
        == "Romance"
    )
    assert (
        genres.primary_genre(
            ["Telenovela", "Familia", "Romance"],
            drop={"Telenovela"},
            priority=genres.NOVELA_PRIORITY,
        )
        == "Romance"
    )


def test_resolution_prefers_tmdb_and_falls_back_to_portal():
    assert genres.resolve(["Comedia", "Nacional"], ["Drama", "Suspense"], True) == [
        "Suspenso",
        "Drama",
    ]
    # Match sin géneros (ficha vieja): no se pierde el género del portal.
    assert genres.resolve(["Acción"], [], True) == ["Acción"]
    # Sin match TMDB: manda el portal, ya limpio.
    assert genres.resolve(["Comedia", "Nacional"], None, False) == ["Comedia"]


def test_empty_and_unknown_inputs_are_safe():
    assert genres.primary_genre([]) is None
    assert genres.primary_genre(None) is None
    assert genres.canonical_genres(None) == []
    assert genres.canonical_genres(["Inventado"]) == []
