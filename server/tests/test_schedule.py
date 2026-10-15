"""Pruebas de la agenda de estrenos (`sync/schedule.py`): parseo puro de TVMaze."""

import datetime
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import schedule


def _item(**kw):
    base = {
        "id": 1, "season": 2, "number": 5, "name": "Capítulo",
        "airstamp": "2026-10-20T02:00:00+00:00",
        "show": {"name": "Serie X", "externals": {"imdb": "tt1234567"}},
    }
    base.update(kw)
    return base


def test_parse_airings_maps_fields():
    rows = schedule.parse_airings([_item()], datetime.date(2026, 10, 20))
    assert len(rows) == 1
    r = rows[0]
    assert (r["tvmaze_id"], r["season"], r["episode"]) == (1, 2, 5)
    assert r["show_name"] == "Serie X" and r["imdb"] == "tt1234567"
    assert r["airdate"] == "2026-10-20" and "2026-10-20" in r["airtime"]


def test_parse_airings_tolerates_garbage():
    day = datetime.date(2026, 10, 20)
    assert schedule.parse_airings(None, day) == []
    assert schedule.parse_airings("no", day) == []
    assert schedule.parse_airings([{}], day) == []
    assert schedule.parse_airings([_item(id=0)], day) == []
    assert schedule.parse_airings([_item(season=0)], day) == []
    # Sin imdb ni nombre igual sirve (la app casa por nombre como respaldo).
    rows = schedule.parse_airings([_item(show={"name": "Sola"})], day)
    assert rows[0]["imdb"] == "" and rows[0]["show_name"] == "Sola"


def test_trending_parse_ranks_in_order():
    import tmdb

    payload = {"results": [{"id": 10}, {"id": 0}, {"id": "x"}, {"id": 20}, "basura"]}
    assert tmdb.parse_trending(payload, "movie") == [(10, 1), (20, 4)]
    assert tmdb.parse_trending(None, "tv") == []
    assert tmdb.parse_trending({"results": "no"}, "tv") == []


def test_trending_week_label_shape():
    import tmdb

    week = tmdb.current_week()
    assert len(week) == 8 and week[4] == "-" and week[5] == "W"
