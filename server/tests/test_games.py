"""Vision+ Play: códigos, apodos, puntaje, máquina de estados y snapshots."""

import json

import pytest

import games


def raw(qid="q1", answer="B", options=None, difficulty=1):
    return {
        "id": qid,
        "kind": "year",
        "payload": {
            "prompt": "¿En qué año se estrenó «Prueba»?",
            "options": options or ["A", "B", "C", "D"],
            "art": "",
        },
        "answer_key": answer,
        "difficulty": difficulty,
        "pack": "general",
    }


def make_room(rounds=2, mode="remote", now=1000.0, qsec=5, fair=False, max_players=8):
    return games.create_room(
        "ABC234",
        "Ana",
        "host-token",
        "player-token",
        [raw("q1", "B"), raw("q2", "C")],
        mode=mode,
        rounds=rounds,
        question_seconds=qsec,
        reveal_seconds=5,
        countdown_seconds=3,
        fair=fair,
        max_players=max_players,
        now=now,
    )


def test_codes_use_the_unambiguous_alphabet():
    assert len(games.generate_code()) == games.CODE_LENGTH
    for _ in range(200):
        code = games.generate_code()
        assert len(code) == 6
        assert all(ch in games.CODE_ALPHABET for ch in code)
        assert not (set(code) & set("ILO01"))


def test_code_normalization_and_display():
    assert games.normalize_code("abc-234") == "ABC234"
    assert games.normalize_code(" ABC 234 ") == "ABC234"
    assert games.normalize_code("ABC_234") == "ABC234"
    assert games.normalize_code("ABC12") == ""
    assert games.normalize_code("ABC12I") == ""  # I no existe en el alfabeto
    assert games.normalize_code(None) == ""
    assert games.format_code("ABC234") == "ABC-234"
    assert games.format_code("mal") == ""


def test_tokens_are_hashed_and_verified():
    token = games.new_token()
    assert len(token) == 32
    hashed = games.token_hash(token)
    assert hashed != token and len(hashed) == 64
    assert games.token_ok(token, hashed)
    assert not games.token_ok("otro", hashed)
    assert not games.token_ok(None, hashed)
    assert not games.token_ok(token, "")


def test_nicknames_are_short_safe_and_clean():
    assert games.clean_name("  Ana  ") == "Ana"
    assert games.clean_name("Ana\x00") == "Ana"
    assert games.clean_name("A") == ""
    assert games.clean_name("x" * 30) == "x" * 14
    assert games.clean_name("Puta") == ""
    assert games.clean_name("Culombia") == "Culombia"  # no es la grosería
    assert games.clean_name("Ana María") == "Ana María"
    assert games.clean_name(None) == ""


def test_colors_are_stable_and_in_range():
    assert games.color_for("Ana") == games.color_for("Ana")
    assert 0 <= games.color_for("Ana") < 8
    assert 0 <= games.color_for("") < 8


@pytest.mark.parametrize(
    "pop,votes,expected",
    [(100, 0, 1), (50, 0, 1), (0, 2000, 1), (10, 0, 2), (0, 200, 2), (1, 10, 3)],
)
def test_difficulty_uses_real_signal(pop, votes, expected):
    assert games.difficulty_for(pop, votes) == expected


def test_speed_bonus_is_capped_at_a_quarter_of_the_base():
    assert games.speed_bonus(1000, 20_000, 20_000) == 250
    assert games.speed_bonus(1000, 10_000, 20_000) == 125
    assert games.speed_bonus(1500, 10_000, 20_000) == 188  # 187.5, medio arriba
    assert games.speed_bonus(2000, 0, 20_000) == 0
    assert games.speed_bonus(2000, 99_000, 20_000) == 500  # no pasa del tope
    assert games.speed_bonus(1000, 10_000, 0) == 0


def test_score_table_is_the_same_on_both_sides():
    # Esta tabla está duplicada a propósito en GameScoreTest.kt. Si cambia un
    # número, cambian los dos (plan §8).
    assert games.answer_points(1, 20_000, 20_000, 0, True) == (1250, 1)
    assert games.answer_points(1, 20_000, 20_000, 1, True) == (1350, 2)
    assert games.answer_points(2, 20_000, 20_000, 2, True) == (2075, 3)
    assert games.answer_points(3, 20_000, 20_000, 5, True) == (3000, 6)
    assert games.answer_points(2, 10_000, 20_000, 0, True) == (1688, 1)
    assert games.answer_points(2, 20_000, 20_000, 3, False) == (0, 0)
    assert games.answer_points(99, 20_000, 20_000, 0, True) == (1250, 1)


def test_fair_mode_has_no_speed_bonus_but_keeps_the_streak():
    assert games.answer_points(2, 20_000, 20_000, 3, True, fair=True) == (1800, 4)


def test_wagers_never_leave_negative_points():
    assert games.clamp_wager(50) == 200
    assert games.clamp_wager(5000) == 1000
    assert games.clamp_wager("no") == 200
    assert games.apply_wager(1000, 500, True) == 1500
    assert games.apply_wager(1000, 500, False) == 500
    assert games.apply_wager(100, 500, False) == 0


def test_room_prepares_options_and_hides_the_real_order():
    room = make_room(rounds=2)
    q = room.current_question()
    assert set(q["options"]) == {"A", "B", "C", "D"}
    assert q["options"][q["answer_index"]] == q["answer"]
    assert room.rounds == 2
    assert room.players[room.host_id].name == "Ana"
    assert room.phase == "lobby"


def test_display_room_starts_empty_and_waits_for_phones():
    room = make_room(mode="display")
    assert room.host_id == ""
    assert room.players == {}
    assert room.players_pace is True


def test_state_machine_advances_by_itself():
    room = make_room(rounds=2, qsec=5)
    assert room.start(1000.0)
    assert room.phase == "countdown"
    room.tick(1002.9)
    assert room.phase == "countdown"
    room.tick(1003.1)
    assert room.phase == "question"
    room.tick(1008.2)
    assert room.phase == "reveal"
    room.tick(1013.3)
    assert room.phase == "question"
    assert room.round_index == 1
    room.tick(1018.4)
    assert room.phase == "reveal"
    room.tick(1023.5)
    assert room.phase == "podium"
    assert room.ended_at > 0


def test_start_only_in_lobby_and_next_only_in_reveal():
    room = make_room()
    assert room.next_round(1000.0) is False
    assert room.start(1000.0)
    assert room.start(1000.1) is False
    room.tick(1003.1)
    room.tick(1008.2)
    assert room.phase == "reveal"
    assert room.next_round(1008.3)
    assert room.phase == "question"


def test_answer_only_in_question_and_once_per_round():
    room = make_room(qsec=10)
    player = room.player_by_token("player-token")
    assert room.answer(player.id, 1, 0, 1000.0)[0] is False  # lobby
    room.start(1000.0)
    room.tick(1003.1)
    correct = room.current_question()["answer_index"]
    assert room.answer(player.id, 1, correct, 1004.0) == (True, "ok")
    assert room.answer(player.id, 1, correct, 1004.1)[1] == "duplicada"
    room.tick(1013.2)
    assert room.phase == "reveal"
    assert room.answer(player.id, 1, correct, 1013.3)[1] == "fase"
    assert player.score == 1228  # 1000 base + 25 % de velocidad (0.9 s de 10)


def test_late_answer_is_rejected():
    room = make_room(qsec=5)
    player = room.player_by_token("player-token")
    room.start(1000.0)
    room.tick(1003.1)
    correct = room.current_question()["answer_index"]
    accepted, reason = room.answer(player.id, 1, correct, 1009.0)  # 5.9 s > 5
    assert accepted is False and reason == "tardía"


def test_scores_and_streak_are_applied_at_reveal():
    room = make_room(rounds=2, qsec=10)
    player = room.player_by_token("player-token")
    room.start(1000.0)
    room.tick(1003.1)
    q1 = room.current_question()["answer_index"]
    room.answer(player.id, 1, q1, 1004.0)
    room.tick(1013.2)
    first = player.score
    assert first == 1228
    room.next_round(1013.3)
    q2 = room.current_question()["answer_index"]
    room.answer(player.id, 2, q2, 1014.0)
    room.tick(1023.4)
    assert player.score > first + 1000
    assert player.streak == 2


def test_wrong_answer_scores_zero_and_resets_streak():
    room = make_room(qsec=10)
    player = room.player_by_token("player-token")
    room.start(1000.0)
    room.tick(1003.1)
    correct = room.current_question()["answer_index"]
    room.answer(player.id, 1, (correct + 1) % 4, 1004.0)
    room.tick(1013.2)
    assert player.score == 0 and player.streak == 0


def test_snapshot_never_leaks_the_answer_in_question_phase():
    room = make_room(qsec=10)
    room.start(1000.0)
    room.tick(1003.1)
    snap = room.snapshot("player", 0, 1004.0, room.host_id)
    assert snap["phase"] == "question"
    assert snap["question"]["options"]
    assert snap["answer"] is None
    packed = json.dumps(snap, ensure_ascii=False)
    assert "answer_key" not in packed
    assert snap["serverNow"] == 1_004_000


def test_reveal_snapshot_has_the_correct_option():
    room = make_room(qsec=5)
    room.start(1000.0)
    room.tick(1003.1)
    room.tick(1008.2)
    snap = room.snapshot("player", 0, 1008.2, room.host_id)
    assert snap["phase"] == "reveal"
    assert snap["answer"]["option"] == room.current_question()["answer_index"]
    assert snap["answer"]["text"] == room.current_question()["answer"]


def test_since_equal_to_rev_returns_none_for_204():
    room = make_room()
    assert room.snapshot("player", room.rev, 1000.0, room.host_id) is None
    assert room.snapshot("player", room.rev - 1, 1000.0, room.host_id) is not None


def test_display_and_player_snapshots_differ():
    room = make_room()
    display = room.snapshot("display", 0, 1000.0)
    player = room.snapshot("player", 0, 1000.0, room.host_id)
    assert "you" not in display
    assert player["you"]["id"] == room.host_id
    assert display["displayCode"] == "ABC-234"


def test_room_full_is_rejected():
    room = make_room(max_players=1)
    assert room.add_player("Beto", "b-token", 1000.0) is None
    assert len(room.players) == 1


def test_join_after_start_is_rejected_but_rejoin_works():
    room = make_room()
    room.start(1000.0)
    room.tick(1003.1)  # ya empezó la primera pregunta
    assert room.add_player("Beto", "b-token", 1003.2) is None
    player = room.player_by_token("player-token")
    assert room.rejoin("player-token", 1003.3) == player


def test_rejoin_preserves_seat_and_points():
    room = make_room(qsec=5)
    player = room.player_by_token("player-token")
    room.start(1000.0)
    room.tick(1003.1)
    room.answer(player.id, 1, room.current_question()["answer_index"], 1004.0)
    room.tick(1008.2)
    score = player.score
    assert room.rejoin("player-token", 1009.0).id == player.id
    assert player.score == score
    assert room.rejoin("token-malo", 1009.0) is None


def test_host_failover_to_the_oldest_connected_player():
    room = make_room()
    first = room.host_id
    second = room.add_player("Beto", "b-token", 1001.0)
    third = room.add_player("Caro", "c-token", 1002.0)
    assert first and second and third
    room.disconnect_stale(1091.0)  # el host lleva 91 s sin aparecer
    assert room.host_id == second.id
    assert room.players[first].connected is False
    assert room.players[second.id].connected is True


def test_kick_moves_the_host_if_needed():
    room = make_room()
    other = room.add_player("Beto", "b-token", 1001.0)
    assert room.kick(room.host_id, 1002.0)
    assert room.host_id == other.id
    assert room.kick("nadie", 1003.0) is False


def test_reactions_are_limited_to_six_and_rate_limited():
    room = make_room()
    player = room.player_by_token("player-token")
    assert room.react(player.id, 0, 1000.0)
    assert room.react(player.id, 1, 1001.0) is False  # menos de 2 s
    assert room.react(player.id, 1, 1002.1)
    assert room.react(player.id, 9, 1004.2) is False
    for i in range(10):
        other = room.add_player(f"P{i}", f"t{i}", 1000.0 + i)
        if other is None:
            continue
        room.react(other.id, i % 6, 1002.0 + i * 2.1)
    assert len(room.snapshot("display", 0, 1005.0)["reactions"]) <= 6


def test_report_count_is_per_question():
    room = make_room()
    assert room.report("q1") == 1
    assert room.report("q1") == 2
    assert room.report("q2") == 1


def test_result_lists_players_sorted_by_score():
    room = make_room(qsec=5)
    player = room.player_by_token("player-token")
    room.start(1000.0)
    room.tick(1003.1)
    room.answer(player.id, 1, room.current_question()["answer_index"], 1004.0)
    room.tick(1008.2)
    result = room.result()
    assert result["rounds"] == 2
    assert result["players"][0]["score"] > 0
    assert result["startedAt"] == 1_000_000


def test_room_closes_on_ttl_and_on_idle():
    room = make_room()
    room.tick(1000.0 + games.ROOM_TTL_SECONDS + 1)
    assert room.phase == "closed" and room.closed_reason == "expired"

    other = make_room()
    other.tick(1000.0 + games.IDLE_SECONDS + 1)
    assert other.phase == "closed" and other.closed_reason == "idle"


def test_close_is_idempotent():
    room = make_room()
    room.close(1000.0, "host")
    rev = room.rev
    room.close(1001.0, "host")
    assert room.rev == rev and room.phase == "closed"


def test_invalid_questions_are_skipped():
    room = games.create_room(
        "XYZ234", "Ana", "h", "p",
        [raw("q1", "Z"), raw("q2", "B")],  # la primera no tiene Z en las opciones
        rounds=2, question_seconds=5, now=1.0,
    )
    assert len(room.questions) == 1
    assert room.rounds == 1


def test_without_valid_questions_creation_fails():
    with pytest.raises(ValueError):
        games.create_room("XYZ234", "Ana", "h", "p", [raw("q1", "Z")], now=1.0)


def test_snapshot_is_small_enough_for_a_slow_tv():
    room = make_room()
    for i in range(7):
        room.add_player(f"P{i}", f"t{i}", 1000.0 + i)
    room.start(1000.0)
    room.tick(1003.1)
    snap = room.snapshot("player", 0, 1004.0, room.host_id)
    assert len(json.dumps(snap, ensure_ascii=False).encode("utf-8")) < 4096


def test_catalog_describes_the_games_with_limits():
    games_list = {item["id"]: item for item in games.catalog()}
    assert set(games_list) == {
        "trivia", "impostor", "dibujo", "subasta", "fibbage",
        "ultima_palabra", "vox_populi", "fuera_de_epoca", "texto_roto",
        "franela_fest", "el_tasador",
    }
    for item in games_list.values():
        assert item["minPlayers"] >= 1
        assert item["maxPlayers"] == games.MAX_PLAYERS
        assert item["approxMinutes"] >= 5
        assert item["rounds"] >= 1
        assert len(item["steps"]) >= 2
    assert games_list["impostor"]["minPlayers"] == 3
    assert "trivia" in games_list["trivia"]["title"].lower()


def _charada_candidates():
    return [
        {"id": f"c{i}", "title": f"Titulo {i}", "poster_url": "https://p/x.jpg", "year": 2000 + i, "kind": "movie"}
        for i in range(40)
    ]


def _wager_candidates():
    return [
        {
            "id": f"w{i}", "title": f"Peli {i}", "poster_url": "https://p/x.jpg", "year": 2000 + i,
            "kind": "movie", "synopsis": f"Una sinopsis larga y única número {i} que cuenta una historia.",
            "cast_list": "Ana, Beto, Caro", "director": "Dir", "genres": ["Drama"], "popularity": 10,
            "vote_count": 100,
        }
        for i in range(40)
    ]


def test_charada_deck_is_unique_and_has_a_poster():
    deck = games.build_charada_deck(_charada_candidates(), 3, "ABC234")
    assert len(deck) == 3 * (games.CHARADA_MAX_PASSES + 1)
    assert len({item["title"] for item in deck}) == len(deck)
    assert all(item["poster"] for item in deck)


def test_charada_turn_only_moves_for_the_actor_and_hides_the_title_from_the_tv():
    deck = games.build_charada_deck(_charada_candidates(), 2, "ABC234")
    room = games.create_room(
        "ABC234", "Ana", "host", "ana", [], game="charadas", deck=deck,
        mode="display", rounds=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    assert room.start(1001.0) is False  # display sin teléfonos aún
    # El primer teléfono entra como Ana; el segundo es Beto.
    ana = room.add_player("Ana", "ana", 1001.5)
    assert ana is not None
    assert room.start(1002.0) is True
    room.tick(1005.1)
    assert room.phase == "turn"
    assert room.actor_id in {ana.id, beto.id}
    actor = room.players[room.actor_id]
    display = room.snapshot("display", 0, 1005.2)
    assert display["charada"]["title"] == ""
    actor_snap = room.snapshot("player", 0, 1005.2, actor.id)
    assert actor_snap["charada"]["title"]
    other = beto if actor.id == ana.id else ana
    other_snap = room.snapshot("player", 0, 1005.2, other.id)
    assert other_snap["charada"]["title"] == ""
    assert room.charada_action(other.id, "correct", 1005.3)[0] is False
    assert room.charada_action(actor.id, "correct", 1005.4)[0] is True
    assert room.charada_action(actor.id, "pass", 1005.5)[0] is True
    assert room.charada_action(actor.id, "pass", 1005.6)[0] is True
    assert room.charada_action(actor.id, "pass", 1005.7)[0] is True
    assert room.phase == "reveal"
    assert actor.score == games.CHARADA_POINTS
    assert room.snapshot("display", 0, 1005.8)["charada"]["title"]


def test_charadas_need_two_players_to_start():
    deck = games.build_charada_deck(_charada_candidates(), 1, "ABC234")
    room = games.create_room(
        "ABC234", "Ana", "host", "ana", [], game="charadas", deck=deck, now=1.0,
    )
    assert room.start(1.0) is False
    room.add_player("Beto", "beto", 1.1)
    assert room.start(1.2) is True


def test_wager_rounds_redact_the_title_and_offer_three_clues():
    deck = games.build_wager_rounds(_wager_candidates(), 3, "ABC234")
    assert len(deck) == 3
    for item in deck:
        assert len(item["options"]) == 4
        assert item["options"][item["answer_index"]] == item["title"]
        assert len(item["clues"]) == 3
        assert games.fold(item["title"]) not in games.fold(item["clues"][0]["text"])
        assert item["clues"][2]["art"]


def test_wager_answer_is_hidden_until_the_reveal_and_scores_the_stake():
    deck = games.build_wager_rounds(_wager_candidates(), 2, "ABC234")
    room = games.create_room(
        "ABC234", "Ana", "host", "p", [], game="wager", deck=deck, rounds=2, now=1000.0,
    )
    beto = room.add_player("Beto", "b", 1001.0)
    assert room.start(1001.0) is True
    room.tick(1004.1)
    assert room.phase == "wager"
    host = room.players[room.host_id]
    assert room.wager(host.id, 1, 500, 1004.2)[0] is True
    assert room.wager(beto.id, 1, 500, 1004.3)[0] is True
    assert room.phase == "clue"
    clue = room.snapshot("player", 0, 1004.4, host.id)
    assert clue["answer"] is None and clue["question"]["kind"] == "wager"
    answer = deck[0]["answer_index"]
    assert room.answer(host.id, 1, answer, 1004.5)[0] is True
    assert room.answer(beto.id, 1, (answer + 1) % 4, 1004.6)[0] is True
    assert room.phase == "reveal"
    reveal = room.snapshot("player", 0, 1004.7, host.id)
    assert reveal["answer"]["text"] == deck[0]["title"]
    assert host.score == 1500
    assert beto.score == 0


def test_display_room_is_driven_by_the_phones():
    room = games.create_room(
        "ABC234", "Pantalla", "tv-host-token", "",
        [raw("q1", "B")],
        mode="display", rounds=1, question_seconds=5, now=1000.0,
    )
    assert room.players == {}
    assert room.host_id == ""
    assert room.start(1000.0) is False  # aún no hay teléfonos
    ana = room.add_player("Ana", "ana-token", 1000.5)
    beto = room.add_player("Beto", "beto-token", 1001.0)
    assert ana and beto
    assert room.host_id == ana.id  # el primero que entra es el anfitrión
    assert room.host_ok("ana-token")
    assert room.host_ok("tv-host-token")
    assert room.start(1001.0)
    room.tick(1004.1)  # countdown de 3 s -> pregunta
    display = room.snapshot("display", 0, 1004.2)
    assert display["phase"] == "question"
    assert display["playersPace"] is True
    assert display["answeredCount"] == 0 and display["readyCount"] == 0
    assert "you" not in display
    assert display["question"] is not None
    assert display["answer"] is None
    correct = room.current_question()["answer_index"]
    assert room.answer(ana.id, 1, correct, 1004.3)[0]
    # El reloj ya no avanza la pregunta: falta Beto.
    room.tick(1009.2)
    assert room.phase == "question"
    display = room.snapshot("display", 0, 1009.3)
    assert display["answeredCount"] == 1 and display["readyCount"] == 0
    # En cuanto responde el último, el servidor abre el reveal sin esperar.
    assert room.answer(beto.id, 1, correct, 1009.4)[0]
    assert room.phase == "reveal"
    reveal = room.snapshot("display", 0, 1009.5)
    assert reveal["answer"]["option"] == correct
    assert any(player["isHost"] for player in reveal["players"])
    # El reveal tampoco avanza solo: todos deben pulsar «Continuar».
    room.tick(1014.6)
    assert room.phase == "reveal"
    assert room.next_round(1014.7) is False
    assert room.ready(ana.id, 1, 1014.8)
    assert room.ready(ana.id, 1, 1014.9)  # idempotente
    player = room.snapshot("player", 0, 1015.0, ana.id)
    assert player["you"]["ready"] is True
    assert player["allReady"] is False and player["readyCount"] == 1
    assert room.next_round(1015.1) is False
    assert room.ready(beto.id, 1, 1015.2)
    player = room.snapshot("player", 0, 1015.3, beto.id)
    assert player["allReady"] is True and player["readyCount"] == 2
    assert room.next_round(1015.4)
    assert room.phase == "podium"
