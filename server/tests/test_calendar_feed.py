"""Calendario editorial: ventanas recurrentes, eventos exactos y prioridad."""

from datetime import date

import calendar_feed
import editorial_calendar


def _row(**over):
    base = {
        "id": "evento-x",
        "title": "Evento X",
        "theme_id": "evento-x",
        "kind": "event",
        "start_md": None,
        "end_md": None,
        "start_date": None,
        "end_date": None,
        "priority": 50,
        "hero": True,
        "show_row": True,
        "min_titles": 8,
        "badge": "EVENTO X",
        "eyebrow": "ESPECIAL",
        "always": False,
    }
    base.update(over)
    return base


def test_a_recurring_window_crosses_the_year():
    rows = [_row(id="ano", start_md="12-28", end_md="01-10")]
    assert calendar_feed.active_entries(rows, date(2026, 12, 30))
    assert calendar_feed.active_entries(rows, date(2027, 1, 3))
    assert calendar_feed.active_entries(rows, date(2027, 6, 1)) == []


def test_an_exact_event_only_lives_its_dates():
    rows = [
        _row(
            id="eclipse",
            kind="event",
            start_date=date(2027, 7, 30),
            end_date=date(2027, 8, 8),
        )
    ]
    assert calendar_feed.active_entries(rows, date(2027, 8, 2))
    assert calendar_feed.active_entries(rows, date(2027, 8, 9)) == []


def test_the_highest_priority_hero_wins_and_row_only_never_opens_the_hero():
    rows = [
        _row(id="halloween", priority=85, start_md="10-01", end_md="10-31"),
        _row(id="muertos", priority=95, start_md="10-24", end_md="11-04"),
        _row(id="apoyo", priority=99, hero=False, start_md="10-01", end_md="10-31"),
    ]
    current = calendar_feed.current_campaign(rows, date(2026, 10, 26))
    assert current["id"] == "muertos"


def test_next_campaign_skips_the_one_in_progress():
    rows = [
        _row(id="halloween", priority=85, start_md="10-01", end_md="10-31"),
        _row(id="navidad", priority=90, start_md="12-01", end_md="12-31"),
    ]
    nxt = calendar_feed.next_campaign(rows, date(2026, 10, 20))
    assert nxt["id"] == "navidad"


def test_snapshot_reports_active_campaign_end_and_next_change():
    rows = [
        _row(id="cop", title="Planeta en juego", priority=72, start_md="11-09", end_md="11-20"),
        _row(id="navidad", priority=90, start_md="12-01", end_md="12-31"),
    ]
    snap = calendar_feed.snapshot(rows, date(2026, 11, 12))
    assert snap["campaign"]["id"] == "cop"
    assert snap["campaign"]["endsAt"].startswith("2026-11-20")
    assert snap["next"]["id"] == "navidad"
    assert [entry["id"] for entry in snap["active"]] == ["cop"]


def test_an_empty_calendar_returns_no_campaign():
    snap = calendar_feed.snapshot([], date(2026, 11, 12))
    assert snap["campaign"] is None
    assert snap["active"] == []
    assert snap["next"] is None


def test_editorial_calendar_serializes_priorities_and_exact_dates():
    rows = {row[0]: row for row in editorial_calendar._rows()}
    dead = rows["tema-dia-de-muertos"]
    assert dead[3] == "season"
    assert dead[8] == 95 and dead[9] is True
    world_cup = rows["tema-evento-mundial-femenil"]
    assert world_cup[3] == "event"
    assert world_cup[6] == date(2027, 6, 24)
    assert world_cup[7] == date(2027, 7, 25)
    assert world_cup[8] == 95
    assert rows["tema-para-reir"][14] is True

