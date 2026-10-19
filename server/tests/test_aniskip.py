"""Pruebas del mapeo TMDB → MyAnimeList (`sync/aniskip.py`).

El worker baja el dataset Fribb anime-lists y lo vuelca a `anime_mal_map`;
aquí se prueba lo puro (parseo) y el refresco (vacío/rancio sí, fresco no).
"""

import sys
import types
from pathlib import Path

# server/sync al path (aniskip.py vive ahí y no depende de la API).
sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import aniskip


def test_parse_entries_maps_tmdb_season_to_mal():
    entries = [
        {"mal_id": 290, "themoviedb_id": {"tv": 26209}, "season": {"tmdb": 1}},
        {"mal_id": 300, "themoviedb_id": {"tv": 62913}, "season": {"tmdb": 1}},
        {"mal_id": 0, "themoviedb_id": {"tv": 1}, "season": {"tmdb": 1}},
        {"mal_id": 7, "themoviedb_id": {"tv": 0}, "season": {"tmdb": 1}},
        None,
    ]
    out = aniskip.parse_entries(entries)
    assert out == {(26209, 1): 290, (62913, 1): 300}


def test_parse_entries_falls_back_to_season_one_without_season_block():
    # One Piece (Fribb) no trae `season`: antes se perdía y AniSkip no se usaba.
    entries = [
        {"mal_id": 21, "themoviedb_id": {"tv": 37854}},
        {"mal_id": 22, "themoviedb_id": {"tv": 37854}, "season": {"tmdb": 1}},
    ]
    out = aniskip.parse_entries(entries)
    assert out[(37854, 1)] == 22, "el mapeo explícito gana sobre el fallback"


def test_parse_entries_tolerates_garbage():
    assert aniskip.parse_entries(None) == {}
    assert aniskip.parse_entries("no") == {}
    assert aniskip.parse_entries([{"mal_id": "x"}]) == {}


class _Conn:
    def __init__(self, stale=True):
        self.stale = stale
        self.rows = None

    async def fetchval(self, *args):
        return self.stale

    async def executemany(self, *args):
        self.rows = args[1]


class _Pool:
    def __init__(self, stale=True):
        self.conn = _Conn(stale)

    def acquire(self):
        pool = self

        class _Ctx:
            async def __aenter__(self):
                return pool.conn

            async def __aexit__(self, *a):
                return False

        return _Ctx()


def _run(coro):
    import asyncio

    return asyncio.run(coro)


def test_sync_skips_when_fresh(monkeypatch):
    pool = _Pool(stale=False)

    def boom():
        raise AssertionError("con tabla fresca no debe bajar nada")

    monkeypatch.setattr(aniskip, "_download", boom)
    assert _run(aniskip.sync(pool)) == 0


def test_sync_upserts_mapping(monkeypatch):
    pool = _Pool(stale=True)
    monkeypatch.setattr(
        aniskip,
        "_download",
        lambda: [{"mal_id": 290, "themoviedb_id": {"tv": 26209}, "season": {"tmdb": 1}}],
    )
    assert _run(aniskip.sync(pool)) == 1
    assert pool.conn.rows == [(26209, 1, 290)]


def test_sync_survives_network_failure(monkeypatch):
    pool = _Pool(stale=True)

    def down():
        raise RuntimeError("sin red")

    monkeypatch.setattr(aniskip, "_download", down)
    assert _run(aniskip.sync(pool)) == 0
