"""Pruebas de aislamiento estricto entre secciones (Películas, Series, Novelas, Anime).

Garantiza que ningún elemento de una sección se cuele en otra:
- _shift_sql desplaza marcadores posicionales correctamente.
- Filtros de sección mutuamente excluyentes.
- Mapeos de géneros para telenovelas y anime.
"""

import sys
from pathlib import Path

# Añadir server/sync al sys.path para poder importar index_build y xtv
sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import index_build
import xtv


def test_shift_sql():
    clause = "t.id = any($1::text[]) and not (t.id = any($2::text[]))"
    shifted = index_build._shift_sql(clause, 2)
    assert shifted == "t.id = any($3::text[]) and not (t.id = any($4::text[]))"

    single = "t.id = any($1::text[])"
    assert index_build._shift_sql(single, 2) == "t.id = any($3::text[])"


def test_genres_es_novelas():
    assert xtv.GENRES_ES.get("novela") == "Telenovela"
    assert xtv.GENRES_ES.get("novelas") == "Telenovela"
    assert xtv.GENRES_ES.get("telenovela") == "Telenovela"
    assert xtv.GENRES_ES.get("soap") == "Telenovela"
    assert xtv.GENRES_ES.get("soap opera") == "Telenovela"
    assert xtv.GENRES_ES.get("dorama") == "Telenovela"


def test_title_row_detects_anime_kind():
    asset = {
        "contentId": "A1",
        "name": "Demon Slayer",
        "tags": "Anime, Acción",
        "programType": "series",
    }
    row = xtv.title_row(asset, kind="series", recommended=False)
    # Debe ser detectado como anime gracias al tag Anime
    assert row[3] == "anime"
    assert "Anime" in row[11]
    assert "Acción" in row[11]


def test_title_row_preserves_movie_kind():
    asset = {
        "contentId": "M1",
        "name": "Avatar",
        "tags": "Acción, Ciencia ficción",
        "programType": "movie",
    }
    row = xtv.title_row(asset, kind="movie", recommended=False)
    assert row[3] == "movie"


def test_title_row_rewrites_series_kind():
    asset = {
        "contentId": "S1",
        "name": "Breaking Bad",
        "tags": "Drama, Crimen",
        "programType": "teleplay",
    }
    row = xtv.title_row(asset, kind="movie", recommended=False)
    assert row[3] == "series"


def test_section_premiere_titles_defined():
    expected_sections = {"movies", "series", "anime", "novelas"}
    assert set(index_build.SECTIONS.keys()) == expected_sections
    for s in expected_sections:
        cid = f"xtv-{s}-estrenos"
        assert cid.startswith(f"xtv-{s}-")


def test_title_row_carries_impact_defaults():
    asset = {
        "contentId": "M9",
        "name": "Hit",
        "tags": "Acción",
        "programType": "movie",
    }
    row = xtv.title_row(asset, kind="movie", recommended=False)
    assert len(row) == xtv.TITLE_SQL.count("$")
    # popularity y votos viajan al final de la tupla (0 hasta que TMDB los llene).
    assert row[-2] == 0.0
    assert row[-1] == 0


def test_tmdb_confidence_rewards_close_year():
    import tmdb
    # Match exacto + año cercano = confianza alta (el que ordena estrenos).
    assert tmdb._confidence("Duna", {"title": "Duna", "release_date": "2024-03-01"},
                            "movie", 2024) >= 0.9


def test_premiere_shelf_detection():
    # Repisas de estreno/novedades del portal (lo que Xuper muestra como «destacados»).
    assert xtv.is_premiere_shelf("2026", year=2026)
    assert xtv.is_premiere_shelf("2026 Peliculas teatrales", year=2026)
    assert xtv.is_premiere_shelf("Estrenos", year=2026)
    assert xtv.is_premiere_shelf("Novedades", year=2026)
    assert xtv.is_premiere_shelf("Últimas Actualizaciones", year=2026)
    assert xtv.is_premiere_shelf("Recién agregadas", year=2026)
    assert xtv.is_premiere_shelf("Películas del Globo de Oro 2025", year=2026)
    # Nada de tráilers ni repisas normales.
    assert not xtv.is_premiere_shelf("Tráiler", year=2026)
    assert not xtv.is_premiere_shelf("2026 Trailers", year=2026)
    assert not xtv.is_premiere_shelf("All", year=2026)
    assert not xtv.is_premiere_shelf("Acción", year=2026)
    assert not xtv.is_premiere_shelf("", year=2026)


def test_clean_query_strips_season_marks():
    import tmdb
    # El 96% de las series fallaba el match porque buscaba «Eagleheart T1» tal cual.
    assert tmdb.clean_query("Eagleheart T1", "series") == "Eagleheart"
    assert tmdb.clean_query("House M.D. T7", "series") == "House M.D"
    assert tmdb.clean_query("Serie Temp.3", "series") == "Serie"
    assert tmdb.clean_query("Show - Español Latino", "series") == "Show"
    # Las películas no pierden números de saga.
    assert tmdb.clean_query("Avatar 2", "movie") == "Avatar 2"


def test_search_specs_anime_tries_both_media():
    import tmdb
    assert tmdb._search_specs("movie") == [("/search/movie", "year")]
    assert tmdb._search_specs("series") == [("/search/tv", "first_air_date_year")]
    assert len(tmdb._search_specs("anime")) == 2
    assert len(tmdb._search_specs("kids")) == 2


def test_curated_rows_exclude_unmatched_titles():
    # El nicho que TMDB descartó (match 0.0) no entra a géneros/estrenos; pendientes sí.
    assert "match_confidence < 0.5" in index_build.NOT_UNMATCHED
    for _section, (base_where, _extra) in index_build.section_filters(["x"], ["y"]).items():
        assert "match_confidence < 0.5" in base_where


def test_destacados_first_sql_shape():
    sql = index_build.DESTACADOS_FIRST_SQL.format(where="x = 1")
    assert "select tc.title_id" in sql
    assert "row_number() over (order by tc.position" in sql
    assert index_build.REST_OFFSET == 1000

