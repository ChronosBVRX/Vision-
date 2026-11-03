"""Contrato HTTP y cableado de Vision+ Play (sin levantar la base)."""

import json
from pathlib import Path

import pytest
from fastapi import HTTPException

import app
import games
import sync as device_sync

ROOT = Path(__file__).resolve().parents[1]


def _source(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_game_routes_exist_and_reuse_the_api_key():
    source = _source("api/app.py")
    for route in (
        '"/v1/game/rooms"',
        '"/v1/game/rooms/{code}/join"',
        '"/v1/game/rooms/{code}/rejoin"',
        '"/v1/game/rooms/{code}/state"',
        '"/v1/game/rooms/{code}/answer"',
        '"/v1/game/rooms/{code}/wager"',
        '"/v1/game/rooms/{code}/charada"',
        '"/v1/game/rooms/{code}/action"',
        '"/v1/game/rooms/{code}/ready"',
        '"/v1/game/rooms/{code}/host/{action}"',
        '"/v1/game/rooms/{code}/reaction"',
        '"/v1/game/rooms/{code}/report"',
        '"/v1/game/catalog"',
        '"/v1/game/packs"',
        '"/v1/game/health"',
    ):
        assert route in source
    assert source.count("require_key(x_api_key)") >= 10
    assert "x_game_host" in source and "x_game_player" in source


def test_game_results_schema_knows_the_game():
    schema = _source("sync/schema.sql")
    assert "game text not null default 'trivia'" in schema
    assert "alter table game_results add column if not exists game" in schema


def test_create_accepts_the_three_games_and_builds_a_deck():
    source = _source("api/app.py")
    for game in ("charadas", "wager", "trivia", "impostor", "dibujo", "subasta", "fibbage"):
        assert f'"{game}"' in source or f"'{game}'" in source
    assert "_game_charada_deck(" in source
    assert "_game_wager_rounds(" in source
    assert "_game_party_deck(" in source
    assert "game=game, deck=deck" in source
    assert "CHARADA_MAX_PASSES" in source


def test_game_module_is_copied_into_the_api_image():
    dockerfile = _source("api/Dockerfile")
    copy_line = next(line for line in dockerfile.splitlines() if line.startswith("COPY app.py"))
    assert "games.py" in copy_line
    assert "game_decks.py" in copy_line


def test_four_game_tables_are_in_the_schema():
    schema = _source("sync/schema.sql")
    for table in ("trivia_questions", "trivia_served", "trivia_reports", "game_results"):
        assert f"create table if not exists {table}" in schema
    assert "answer_key text not null" in schema
    assert "jsonb" in schema


def test_create_uses_the_bank_and_a_rate_limit():
    source = _source("api/app.py")
    assert "_game_questions(" in source
    assert "trivia_questions" in source
    assert "_diag_rate_ok(_GAME_CREATE_BUCKET" in source
    assert "_diag_rate_ok(_GAME_JOIN_BUCKET" in source
    assert "GAME_JOIN_RATE" in source


def test_state_can_answer_204_without_a_body():
    source = _source("api/app.py")
    assert "Response(status_code=204)" in source
    assert "_game_snapshot(" in source


def test_display_state_uses_the_tv_host_token_but_keeps_legacy_clients_safe():
    source = _source("api/app.py")
    assert 'role == "display"' in source
    assert "display_authorized = bool(role == \"display\" and room.host_ok(x_game_host))" in source
    assert "display_authorized=display_authorized" in source


def test_served_questions_rotate_per_group_for_90_days():
    source = _source("api/app.py")
    assert "from trivia_served" in source
    assert "interval '90 days'" in source
    assert "insert into trivia_served" in source


def test_game_results_are_persisted_on_close():
    source = _source("api/app.py")
    assert "insert into game_results" in source
    assert "_persist_game_result(room)" in source
    assert "players" in source and "json.dumps" in source


def test_health_exposes_rooms_and_bank_size():
    source = _source("api/app.py")
    assert '"/v1/game/health"' in source
    assert "max(created_at)" in source
    assert '"maxRooms"' in source


def test_code_helper_rejects_ambiguous_codes():
    assert app._game_code("abc-234") == "ABC234"
    with pytest.raises(HTTPException) as error:
        app._game_code("abc-123")  # el 1 no existe en el alfabeto
    assert error.value.status_code == 400
    with pytest.raises(HTTPException):
        app._game_code("")


def test_group_id_hashes_the_sync_code():
    assert app._game_group_id("ABCD-EFGH") == device_sync.code_id("ABCDEFGH")
    assert app._game_group_id("") == ""
    assert app._game_group_id("no!") == ""


def test_integer_and_text_helpers_clamp():
    assert app._game_int("99", 8, 1, 12) == 12
    assert app._game_int("0", 8, 1, 12) == 1
    assert app._game_int("no", 8, 1, 12) == 8
    assert app._game_text("  hola\x00  ", 4) == "hola"[0:4]


def test_room_store_is_bounded_and_modules_are_wired():
    assert app.vision_games is games
    assert app.GAME_MAX_ROOMS == 100
    assert app.GAME_MAX_BODY == 64 * 1024


def test_party_decks_build_without_the_database():
    for game in ("impostor", "dibujo", "subasta", "fibbage"):
        deck = app._game_party_deck(game, 2, "ABC234")
        assert len(deck) == 2
        assert deck[0]["id"]
    assert app._game_party_deck("trivia", 2, "ABC234") == []


def test_create_room_rejects_an_unknown_mode():
    with pytest.raises(ValueError):
        games.create_room(
            "ABC234", "Ana", "h", "p",
            [{"id": "q", "kind": "year", "payload": {"prompt": "x", "options": ["a", "b"]},
              "answer_key": "a", "difficulty": 1}],
            mode="raro", now=1.0,
        )


def test_v2_rooms_and_actions_are_wired():
    source = _source("api/app.py")
    assert 'body.get("rulesVersion")' in source
    assert "rules_version=rules_version" in source
    for field in ("power", "wire", "choices", "selected", "amount", "vote"):
        assert f'"{field}"' in source
    rules = _source("api/games.py")
    assert "wager_buy" in rules
    assert "trivia_attack" in rules


def test_display_rooms_hand_the_pace_to_the_phones():
    room = games.create_room(
        "ABC234", "Pantalla", "tv-host", "",
        [{"id": "q", "kind": "year", "payload": {"prompt": "x", "options": ["a", "b"]},
          "answer_key": "a", "difficulty": 1}],
        mode="display", now=1.0,
    )
    assert room.players_pace is True
    remote = games.create_room(
        "XYZ234", "Ana", "h", "p",
        [{"id": "q", "kind": "year", "payload": {"prompt": "x", "options": ["a", "b"]},
          "answer_key": "a", "difficulty": 1}],
        mode="remote", now=1.0,
    )
    assert remote.players_pace is False


def test_report_route_flags_but_does_not_destroy_questions():
    source = _source("api/app.py")
    assert "trivia_reports" in source
    assert "flagged" in source
    # Un reporte no debe ser un botón de borrado de contenido.
    assert "delete from trivia_questions" not in source
    assert "_GAME_REPORT_BUCKET" in source
    assert "reports_by_player" in _source("api/games.py")


def test_trivia_sections_are_exposed_and_general_means_all():
    source = _source("api/app.py")
    for pack in ("cine", "series", "anime", "infantil", "novelas"):
        assert f'"{pack}"' in source
    assert 'q.pack = $1' in source
    assert "$1 = 'general'" in source
    assert "select coalesce(pack, 'general')" in source


def test_trivia_bank_classifies_old_questions():
    source = _source("sync/trivia_bank.py")
    assert "_backfill_packs" in source
    assert "title_groups g" in source
    assert "pack_for_card" in source
