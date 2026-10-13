"""Lo que el API sirve a la app: limpieza de marcas, preferencia TMDB y
representante XTV en grupos mixtos."""

import app


def _record(**over):
    base = {"id": "xtv:A", "title": "Show T2", "year": 2024, "duration_min": 45,
            "synopsis": "portal", "poster_url": "p", "play_ref": "xtv:A",
            "source": "xtv", "score": 7.0, "kind": "series"}
    base.update(over)
    return base


def test_strip_marks_display():
    assert app.strip_season_mark("Show T1", "series") == "Show"
    assert app.strip_season_mark("Show tem1", "series") == "Show"
    assert app.strip_season_mark("Show T1 (2022)", "series") == "Show"
    assert app.strip_season_mark("Avatar 2", "movie") == "Avatar 2"
    assert app.strip_season_mark("Show Cap 3", "series") == "Show Cap 3"
    assert app.strip_season_mark("Show 2", "kids") == "Show 2"


def test_film_cleans_series_title():
    assert app.film(_record())["title"] == "Show"
    assert app.film(_record(title="Avatar 2", kind="movie"))["title"] == "Avatar 2"


def test_film_prefers_enrichment():
    enr = {"rating": 9.1, "year": 2025, "overview": "Sinopsis TMDB"}
    out = app.film(_record(), enr)
    assert out["score"] == 9.1
    assert out["year"] == 2025
    assert out["synopsis"] == "Sinopsis TMDB"


def test_film_keeps_portal_without_enrichment():
    out = app.film(_record(), None)
    assert out["score"] == 7.0
    assert out["synopsis"] == "portal"


def test_grouped_entry_prefers_xtv_member():
    row = _record(id="magis:X", play_ref="magisvod:X", source="magis")
    member_of = {"magis:X": {"group_id": "g", "title_id": "magis:X", "season": 4}}
    seasons = [
        {"n": 4, "titleId": "magis:X", "videoUrl": "magisvod:X"},
        {"n": 4, "titleId": "xtv:Y", "videoUrl": "xtv:Y"},
        {"n": 1, "titleId": "xtv:Z", "videoUrl": "xtv:Z"},
    ]
    base_of = {"g": "Show T4"}
    entry, group_id = app._grouped_entry(
        row, member_of, {"g": seasons}, base_of, None)
    assert group_id == "g"
    assert entry["id"] == "xtv:Y"
    assert entry["videoUrl"] == "xtv:Y"
    assert entry["title"] == "Show"
    assert entry["groupId"] == "g"


def test_team_tokens():
    tokens = app._team_tokens("Club América")
    assert "club" in tokens
    assert "america" in tokens
    tokens2 = app._team_tokens("PSG")
    assert "psg" in tokens2


import pytest


@pytest.mark.anyio
async def test_enrich_matches_empty_or_inactive():
    res = await app._enrich_matches_with_epg([])
    assert res == []



def test_ungrouped_entry_passes_through():
    row = _record()
    entry, group_id = app._grouped_entry(row, {}, {}, {}, None)
    assert group_id is None
    assert entry["id"] == "xtv:A"
    assert entry["title"] == "Show"


def test_api_hardening_docs_disabled():
    assert app.app.docs_url is None
    assert app.app.redoc_url is None
    assert app.app.openapi_url is None


def test_search_fold_normalization():
    assert app._fold("La vida es bella!") == "la vida es bella"
    assert app._fold("¿Canción del Ángel?") == "cancion del angel"
    assert app._fold("ROBERTO BENIGNI") == "roberto benigni"
    assert app._fold("  terminator   2  ") == "terminator 2"


def test_film_normalizes_magisvod_to_xtv():
    row = _record(id="magis:E019F33B862F", play_ref="magisvod:E019F33B862F", source="magis")
    out = app.film(row)
    assert out["id"] == "xtv:E019F33B862F"
    assert out["videoUrl"] == "xtv:E019F33B862F"
    assert out["sourceUrl"] == "xtv"


def test_film_defaults_popularity_without_columns():
    out = app.film(_record())
    assert out["popularity"] == 0.0
    assert out["voteCount"] == 0


def test_film_carries_kind_for_home_rows():
    assert app.film(_record())["kind"] == "series"
    assert app.film(_record(kind="anime"))["kind"] == "anime"


def test_film_prefers_enrichment_popularity():
    enr = {"rating": 7.7, "year": 2025, "overview": "",
           "popularity": 180.5, "vote_count": 200000}
    out = app.film(_record(), enr)
    assert out["popularity"] == 180.5
    assert out["voteCount"] == 200000


