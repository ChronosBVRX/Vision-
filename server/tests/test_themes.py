"""Colecciones temáticas: se encienden por fecha y no inventan categorías vacías.

El pedido original: «categorías temáticas por las fechas, Día de Muertos, que se
sienta vivo el catálogo de terror y comedia». Después se detectó que Halloween,
Día de Muertos y Noche de terror mostraban casi lo mismo y metían Harry Potter,
Piratas del Caribe o dramas médicos. Ahora la clasificación de temporada alta sale
de las keywords oficiales de TMDB y de una lista corta de obras canónicas; «Noche
de terror» se retiró porque duplicaba la fila de género «Terror».
"""

import inspect
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import catalog_filters
import genres
import themes
import tmdb


def _ids(day: date) -> list[str]:
    return [theme.id for theme in themes.active_themes(day)]


def test_day_of_the_dead_and_halloween_overlap_in_late_october():
    ids = _ids(date(2026, 10, 26))
    assert ids[:2] == ["tema-dia-de-muertos", "tema-halloween"]
    assert "tema-noche-de-terror" not in ids
    assert "tema-para-reir" in ids


def test_day_of_the_dead_stays_until_november_4_and_halloween_ends_oct_31():
    nov2 = _ids(date(2026, 11, 2))
    assert "tema-dia-de-muertos" in nov2
    assert "tema-halloween" not in nov2
    oct15 = _ids(date(2026, 10, 15))
    assert "tema-halloween" in oct15
    assert "tema-dia-de-muertos" not in oct15


def test_christmas_and_new_year_wrap_the_calendar():
    dec25 = _ids(date(2026, 12, 25))
    assert dec25[0] == "tema-navidad"
    assert "tema-ano-nuevo" not in dec25
    dec29 = _ids(date(2026, 12, 29))
    assert "tema-navidad" in dec29 and "tema-ano-nuevo" in dec29
    jan3 = _ids(date(2027, 1, 3))
    assert "tema-ano-nuevo" in jan3
    assert "tema-navidad" not in jan3


def test_other_seasonal_windows():
    assert "tema-san-valentin" in _ids(date(2026, 2, 14))
    assert "tema-dia-de-las-madres" in _ids(date(2026, 5, 10))
    assert "tema-dia-del-padre" in _ids(date(2026, 6, 15))
    assert "tema-fiestas-patrias" in _ids(date(2026, 9, 15))
    assert "tema-regreso-a-clases" not in _ids(date(2026, 9, 15))
    july = _ids(date(2026, 7, 15))
    assert "tema-vacaciones" in july
    august = _ids(date(2026, 8, 15))
    assert "tema-vacaciones" in august and "tema-regreso-a-clases" in august


def test_only_the_year_round_theme_stays_in_a_plain_month():
    ids = _ids(date(2026, 3, 1))
    assert ids == ["tema-para-reir"]


def test_theme_ids_are_unique_and_namespaced():
    ids = [theme.id for theme in themes.THEMES]
    assert len(ids) == len(set(ids))
    assert all(theme_id.startswith("tema-") for theme_id in ids)


def test_genre_aliases_match_both_portal_and_tmdb_spellings():
    terror = set(genres.aliases_for("Terror"))
    assert {"terror", "horror"} <= terror
    comedia = set(genres.aliases_for("Comedia"))
    assert {"comedia", "comedy"} <= comedia
    ficcion = set(genres.aliases_for("Ciencia ficción"))
    assert {"ciencia ficcion", "sci-fi", "science fiction"} <= ficcion


def test_keyword_patterns_are_folded_and_deduplicated():
    patterns = catalog_filters.keyword_patterns(["muert", "%", "muert", ""])
    assert patterns == ["%muert%"]


def test_text_themes_select_by_title_then_synopsis_then_genre():
    source = inspect.getsource(themes._theme_ids)
    assert "keyword_patterns(theme.title_keywords)" in source
    assert "keyword_patterns(theme.synopsis_keywords)" in source
    assert "_genre_matches" in source
    assert "theme.fallback_genres" in source
    genre_source = inspect.getsource(themes._genre_matches)
    assert "primary_by_id" in genre_source
    assert "t.id = any($1::text[])" in genre_source
    assert "NOT_UNMATCHED" in genre_source and "NOT_TRAILER" in genre_source


def test_classified_themes_use_tmdb_instead_of_genre_fill():
    by_id = {theme.id: theme for theme in themes.THEMES}
    for theme_id in ("tema-dia-de-muertos", "tema-halloween"):
        theme = by_id[theme_id]
        assert theme.genres == ()
        assert theme.fallback_genres == ()
        assert theme.tmdb_keywords
    assert by_id["tema-halloween"].tmdb_keywords == (themes.TMDB_HALLOWEEN,)
    assert set(by_id["tema-dia-de-muertos"].tmdb_keywords) == {
        themes.TMDB_DAY_OF_THE_DEAD,
        themes.TMDB_DIA_DE_MUERTOS,
    }


def test_day_of_the_dead_does_not_match_generic_words():
    theme = next(theme for theme in themes.THEMES if theme.id == "tema-dia-de-muertos")
    assert theme.title_keywords == ()
    assert theme.synopsis_keywords == ()
    # Una palabra como «muerte» metía Harry Potter/Piratas: ahora manda TMDB.
    assert "muerte" not in theme.title_keywords + theme.synopsis_keywords
    seeds = dict(theme.seed_titles)
    assert seeds["coco"] == 2017
    assert seeds["macario"] == 1960
    assert seeds["la leyenda de la nahuala"] == 2007


def test_halloween_only_accepts_the_canonical_tmdb_keyword():
    theme = next(theme for theme in themes.THEMES if theme.id == "tema-halloween")
    assert theme.tmdb_keywords == (3335,)
    assert theme.title_keywords == ()
    assert theme.synopsis_keywords == ()
    assert theme.genres == ()


def test_night_of_terror_was_removed_as_redundant():
    ids = [theme.id for theme in themes.THEMES]
    assert "tema-noche-de-terror" not in ids
    # La fila permanente que queda es la comedia; el terror vive en su género.
    assert "tema-para-reir" in ids


def test_media_kind_filter_defuses_the_shared_id_trap():
    # TMDB 300401 es película (Day of the Dead, 1957) y serie (Dynasty, 2017).
    assert themes._media_matches_kind("movie", "series") is False
    assert themes._media_matches_kind("tv", "movie") is False
    # anime/kids pueden ser cualquiera de los dos: no se descartan por tipo.
    assert themes._media_matches_kind("movie", "anime") is True
    assert themes._media_matches_kind("tv", "anime") is True
    assert themes._media_matches_kind("movie", "movie") is True
    assert themes._media_matches_kind("tv", "series") is True


def test_title_match_rejects_the_other_media_space():
    dynasty = {
        "title": "Dynasty T5",
        "year": 2021,
        "localized_title": "",
        "original_title": "Dynasty",
        "alt_titles": [],
        "translated_titles": [],
    }
    assert themes._title_matches(dynasty, "Day of the Dead", 1957) is False
    dead = {
        "title": "Day of the Dead",
        "year": 1957,
        "localized_title": "",
        "original_title": "Day of the Dead",
        "alt_titles": [],
        "translated_titles": [],
    }
    assert themes._title_matches(dead, "Day of the Dead", 1957) is True
    # Un título localizado/alternativo también vale para desempatar.
    coco = {
        "title": "Coco",
        "year": 2017,
        "localized_title": "",
        "original_title": "Coco",
        "alt_titles": ["Coco, el pequeño dragón"],
        "translated_titles": [],
    }
    assert themes._title_matches(coco, "Coco", 2017) is True


def test_copy_preference_prefers_spanish_title_and_newest_season():
    spanish = {
        "title": "El extraño mundo de Jack",
        "year": 1993,
        "localized_title": "El extraño mundo de Jack",
        "popularity": 118.0,
        "score": 7.9,
    }
    portuguese = {
        "title": "O Estranho Mundo de Jack",
        "year": 1993,
        "localized_title": "El extraño mundo de Jack",
        "popularity": 118.0,
        "score": 8.0,
    }
    assert themes._copy_preference(spanish, False) > themes._copy_preference(portuguese, False)
    old_season = {
        "title": "Wednesday Temp.1",
        "year": 2022,
        "localized_title": "Merlina",
        "popularity": 65.0,
        "score": 8.1,
    }
    new_season = {
        "title": "Merlina T2",
        "year": 2025,
        "localized_title": "Merlina",
        "popularity": 65.0,
        "score": 8.0,
    }
    assert themes._copy_preference(new_season, False) > themes._copy_preference(old_season, False)
    # La temporada nueva manda sobre la copia en inglés de una temporada vieja.
    english_old = {
        "title": "American Horror Story T10",
        "year": 2011,
        "localized_title": "American Horror Story",
        "popularity": 174.0,
        "score": 8.0,
    }
    spanish_newer = {
        "title": "Historia de Horror Americana T2",
        "year": 2012,
        "localized_title": "American Horror Story",
        "popularity": 174.0,
        "score": 8.0,
    }
    assert themes._copy_preference(spanish_newer, False) > themes._copy_preference(english_old, False)
    seed = dict(spanish, popularity=0.0)
    assert themes._copy_preference(seed, True) > themes._copy_preference(portuguese, False)


def test_discover_asks_for_movies_and_series_with_the_keywords():
    source = inspect.getsource(tmdb.discover_keyword_members)
    assert "with_keywords" in source
    assert 'for media in ("movie", "tv")' in source
    assert "total_pages" in source
    assert "parse_discover_members" in source
    parsed = tmdb.parse_discover_members(
        {"results": [{"id": 948, "title": "Halloween", "release_date": "1978-10-25"}]},
        "movie",
    )
    assert parsed == [
        {"id": 948, "media": "movie", "title": "Halloween", "year": 1978}
    ]


def test_build_caches_the_tmdb_photo_and_falls_back_to_it():
    source = inspect.getsource(themes.build)
    assert "theme_tmdb" in source
    assert "active_themes(today)" in source
    refresh = inspect.getsource(themes._refresh_tmdb_members)
    assert "delete from theme_tmdb where theme_id = $1" in refresh
    assert "se conserva la foto anterior" in refresh
    assert "except Exception" in refresh
    # La selección clasificada NO cae al relleno de género.
    source_ids = inspect.getsource(themes._theme_ids)
    assert "if theme.tmdb_keywords or theme.seed_titles" in source_ids
    assert "_classified_ids" in source_ids


def test_works_are_deduplicated_by_tmdb_id_so_seasons_do_not_fill_the_row():
    assert themes._work_key({"tmdb_id": 123, "title": "Show T1"}) == "tmdb:123"
    assert themes._work_key({"tmdb_id": 123, "title": "Show T7"}) == "tmdb:123"
    assert themes._work_key({"tmdb_id": None, "title": "Los Simpson"}) == themes._work_key(
        {"tmdb_id": None, "title": "los simpson"}
    )
    assert themes._work_key({"tmdb_id": None, "title": "Otra"}) != themes._work_key(
        {"tmdb_id": None, "title": "Los Simpson"}
    )


def test_fold_sql_removes_accents_without_unaccent_dependency():
    sql = catalog_filters.fold_sql("t.title")
    assert sql.startswith("translate(lower(t.title)")
    assert "áéíóú" in sql
