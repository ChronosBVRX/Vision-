"""Agenda multideporte: el worker baja NBA/NFL/MLB/NHL con el mismo parser de
ESPN y deja el deporte en `league_id` (`basketball/nba`). La API lo usa para no
cruzar un partido de básquet con uno de fútbol."""

import asyncio
from datetime import date

import pytest

import sports


NBA_DATA = {
    "leagues": [{"logos": [{"href": "https://l/nba.png"}]}],
    "events": [
        {
            "id": "401",
            "date": "2026-10-17T02:00:00Z",
            "status": {"type": {"state": "in", "shortDetail": "4th 2:10"}},
            "competitions": [
                {
                    "competitors": [
                        {
                            "homeAway": "home",
                            "score": "101",
                            "team": {
                                "id": "13",
                                "displayName": "Los Angeles Lakers",
                                "abbreviation": "LAL",
                                "logos": [{"href": "https://l/lal.png"}],
                            },
                        },
                        {
                            "homeAway": "away",
                            "score": "98",
                            "team": {
                                "id": "2",
                                "displayName": "Boston Celtics",
                                "abbreviation": "BOS",
                                "logos": [{"href": "https://l/bos.png"}],
                            },
                        },
                    ],
                    "broadcasts": [{"names": ["ESPN"]}],
                    "venue": {"fullName": "Crypto.com Arena"},
                }
            ],
        }
    ],
}


def test_ascenso_mx_is_not_scheduled():
    # Se retiró de la programación porque casi nunca publica enlaces útiles.
    assert all(slug != "mex.2" for slug, _, _ in sports.LEAGUES)


def test_the_other_sports_are_team_sports_with_prefixed_ids():
    by_slug = {(sport, slug): name for sport, slug, name, _ in sports.OTHER_LEAGUES}
    assert by_slug[("basketball", "nba")] == "NBA"
    assert by_slug[("football", "nfl")] == "NFL"
    assert by_slug[("baseball", "mlb")] == "MLB"
    assert by_slug[("hockey", "nhl")] == "NHL"


@pytest.mark.anyio
async def test_fetch_league_uses_the_sport_path_and_parses_scores(monkeypatch):
    captured = {}

    def fake_get(url, timeout=20):
        captured["url"] = url
        return NBA_DATA

    monkeypatch.setattr(sports, "_get", fake_get)
    rows = await sports._fetch_league(
        asyncio.Semaphore(1), "basketball", "nba", "NBA", ("ESPN",), date(2026, 10, 17)
    )
    assert "basketball/nba/scoreboard?dates=20261017" in captured["url"]
    assert len(rows) == 1
    row = rows[0]
    assert row["league_id"] == "basketball/nba"
    assert row["league_name"] == "NBA"
    assert row["home"]["name"] == "Los Angeles Lakers"
    assert row["home"]["score"] == 101
    assert row["away"]["score"] == 98
    assert row["status"] == "in"
    assert row["detail"] == "4th 2:10"
