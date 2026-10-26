"""Banners diarios del Inicio (Vision+ Home).

La foto curada se deduplica por obra, descarta lo que no tiene póster/reproducción y
se sirve desde Postgres. La búsqueda del arte en TMDB corre una vez al día; si TMDB
no responde, el Inicio sigue con los pósters del catálogo.
"""

import asyncio
import json
from datetime import datetime, timedelta, timezone

import banners


def _movie(**over):
    base = {
        "id": "xtv:A",
        "title": "Spider-Man: un nuevo día",
        "kind": "movie",
        "program_type": "movie",
        "poster_url": "https://img/poster.jpg",
        "play_ref": "xtv:A",
        "year": 2026,
        "score": 8.1,
        "duration_min": 128,
        "synopsis": "portal",
        "overview": "sinopsis es-MX",
        "popularity": 900,
        "is_premiere": True,
        "tmdb_id": 123,
        "media": "movie",
        "tmdb_rating": 8.4,
        "base_title": "",
        "rank": 1,
    }
    base.update(over)
    return base


def test_pick_movie_candidates_dedupes_by_work_and_skips_unplayable():
    rows = [
        _movie(id="xtv:T1", title="House T1", kind="series", tmdb_id=1408, media="tv"),
        _movie(id="xtv:T2", title="House T2", kind="series", tmdb_id=1408, media="tv"),
        _movie(id="xtv:B", title="Otra", tmdb_id=99, poster_url=""),
        _movie(id="xtv:C", title="Sin play", tmdb_id=77, play_ref=""),
        _movie(id="xtv:D", title="Dune", tmdb_id=438631, media="movie"),
    ]
    picked = banners.pick_movie_candidates(rows, limit=5)
    # La película va primero aunque el ranking semanal traiga una serie arriba.
    assert [row["title"] for row in picked] == ["Dune", "House T1"]


def test_movie_banner_prefers_tmdb_rating_and_marks_the_first_as_featured():
    banner = banners.movie_banner(_movie(), backdrop="https://img/backdrop.jpg", position=0)
    assert banner["kind"] == "movie"
    assert banner["title"] == "Spider-Man: un nuevo día"
    assert banner["rating"] == 8.4
    assert banner["backdrop"] == "https://img/backdrop.jpg"
    assert banner["synopsis"] == "sinopsis es-MX"
    assert banner["badge"] == "DESTACADO HOY"
    assert banner["eyebrow"] == "PELÍCULA DESTACADA"
    assert banner["ref"] == "xtv:A"
    assert banner["videoUrl"] == "xtv:A"
    assert banner["series"] is False


def test_movie_banner_cleans_season_marks_and_detects_series():
    banner = banners.movie_banner(
        _movie(id="xtv:HOUSE3", title="House T3", kind="series", program_type="teleplay"),
    )
    assert banner["title"] == "House"
    assert banner["series"] is True
    assert banner["eyebrow"] == "SERIE DESTACADA"


def test_group_base_title_wins_for_a_grouped_series():
    banner = banners.movie_banner(
        _movie(
            id="xtv:HOUSE3",
            title="House M.D. T3",
            kind="series",
            program_type="teleplay",
            base_title="House M.D.",
        ),
    )
    assert banner["title"] == "House M.D."
    assert banner["series"] is True


def test_an_old_movie_is_a_trend_not_a_premiere():
    banner = banners.movie_banner(_movie(year=2008, is_premiere=False), position=3)
    assert banner["badge"] == "TENDENCIA"


def test_clean_title_keeps_movie_sequels():
    assert banners._clean_title("Dune: Parte 2") == "Dune: Parte 2"
    assert banners._clean_title("House Temporada 4") == "House"
    assert banners._clean_title("House - T4") == "House"


# --- serve(): cache diaria sin red ---


class _FakeBackground:
    def __init__(self):
        self.tasks = []

    def add_task(self, fn, *args, **kwargs):
        self.tasks.append((fn, args, kwargs))


class _FakeAcquire:
    def __init__(self, pool):
        self.pool = pool

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def execute(self, *args, **kwargs):
        return None

    def transaction(self):
        return self

    async def __aenter__(self):  # pragma: no cover - el fake no escribe
        return self

    async def __aexit__(self, *exc):
        return False


class _FakePool:
    def __init__(self, rows, week=None):
        self.rows = rows
        self.week = week

    async def execute(self, *args, **kwargs):
        return None

    async def fetchval(self, *args, **kwargs):
        return self.week

    async def fetch(self, query, *args, **kwargs):
        if "launcher_banners" in query:
            return self.rows
        return []

    def acquire(self):
        return _FakeAcquire(self)


def _row(payload, age_hours=0):
    return {
        "payload": payload,
        "generated_at": datetime.now(timezone.utc) - timedelta(hours=age_hours),
    }


def test_serve_returns_the_daily_cache_without_touching_tmdb():
    banners._ensured = False
    payload = banners.movie_banner(_movie(), "https://img/bd.jpg", 0)
    pool = _FakePool([_row(payload)])
    background = _FakeBackground()
    result = asyncio.run(banners.serve(pool, background, tmdb_key=""))
    assert len(result["items"]) == 1
    assert result["items"][0]["title"] == "Spider-Man: un nuevo día"
    assert background.tasks == []
    assert result["nextRefreshAt"] > result["generatedAt"]


def test_serve_refreshes_in_the_background_when_the_photo_is_old():
    banners._ensured = False
    payload = banners.movie_banner(_movie(), "", 0)
    pool = _FakePool([_row(payload, age_hours=30)])
    background = _FakeBackground()
    result = asyncio.run(banners.serve(pool, background, tmdb_key=""))
    assert len(result["items"]) == 1
    assert len(background.tasks) == 1
    assert background.tasks[0][0] is banners.refresh_movies


def test_serve_never_breaks_the_home_without_candidates():
    banners._ensured = False
    pool = _FakePool([], week=None)
    background = _FakeBackground()
    result = asyncio.run(banners.serve(pool, background, tmdb_key=""))
    assert result["items"] == []
    assert isinstance(result["generatedAt"], int)
