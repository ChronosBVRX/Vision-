"""Pruebas de integridad y anti-trampa de Vision+ Play.

Estas pruebas cubren los hallazgos P0 de la auditoría externa:
- espectadores sin secretos,
- póster de Apuesta fuera del snapshot del jugador,
- Charadas liquidando una sola vez,
- adivinanza temprana sin stopwords,
- tinta aplicada en servidor,
- mecánicas v2 fuera de salas v1.
"""

import games
import game_decks


def _trivia_room(rules_version: int = 2):
    questions = [{
        "id": "q1",
        "kind": "multiple_choice",
        "payload": {"prompt": "Pregunta", "options": ["A", "B", "C", "D"]},
        "answer_key": "A",
        "difficulty": 1,
        "pack": "general",
    }]
    room = games.create_room(
        "TRV234", "Ana", "host", "ana", questions,
        game="trivia", rounds=1, rules_version=rules_version, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    assert room.start(1001.1)
    room.tick(1004.3)
    return room


def _impostor_room(rules_version: int = 2, players: int = 3):
    room = games.create_room(
        "HMP234", "Ana", "host", "ana", [],
        game="impostor", deck=game_decks.build_impostor_deck(1, "HMP234"),
        rounds=1, rules_version=rules_version, now=1000.0,
    )
    for index in range(1, players):
        room.add_player(f"J{index}", f"j{index}", 1001.0 + index / 10)
    assert room.start(1002.0)
    room.tick(1005.2)
    return room


def _drawing_room(rules_version: int = 2, rounds: int = 2):
    room = games.create_room(
        "DBB234", "Ana", "host", "ana", [],
        game="dibujo", deck=game_decks.build_drawing_deck(rounds, "DBB234"),
        rounds=rounds, rules_version=rules_version, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    return room


def _charada_room(rules_version: int = 2):
    candidates = [{
        "id": f"c{index}",
        "title": f"Titulo {index}",
        "poster_url": "https://p/x.jpg",
        "year": 2000 + index,
        "kind": "movie",
    } for index in range(12)]
    deck = games.build_charada_deck(candidates, 1, "CHX234")
    room = games.create_room(
        "CHX234", "Ana", "host", "ana", [],
        game="charadas", deck=deck, rounds=1, rules_version=rules_version, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    return room


def _wager_room():
    candidates = [{
        "id": f"w{index}",
        "title": f"Peli {index}",
        "synopsis": "Una sinopsis suficientemente larga para entrar al mazo de la ronda.",
        "poster_url": "https://p/x.jpg",
        "year": 2000 + index,
        "kind": "movie",
        "genres": ["Drama"],
        "cast_list": "Ana, Beto",
    } for index in range(8)]
    deck = games.build_wager_rounds(candidates, 1, "WGR234")
    room = games.create_room(
        "WGR234", "Ana", "host", "ana", [],
        game="wager", deck=deck, rounds=1, rules_version=2, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    return room


def _fibbage_room():
    room = games.create_room(
        "FBB234", "Ana", "host", "ana", [],
        game="fibbage", deck=game_decks.build_fibbage_deck(1, "FBB234"),
        rounds=1, rules_version=2, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    return room


def test_spectator_never_sees_the_impostor_word():
    room = _impostor_room()
    spectator = room.add_player("Publico", "publico", 1005.3, spectator=True)
    assert spectator is not None
    snap = room.snapshot("player", 0, 1005.4, spectator.id)
    assert snap["impostor"]["word"] == ""
    assert snap["impostor"]["category"] == "Categoría clasificada"
    assert snap["impostor"]["role"] == "audience"
    assert snap["you"]["role"] == "audience"
    assert snap["you"]["spectator"] is True


def test_wager_poster_is_not_in_a_player_snapshot_until_stage_three():
    room = _wager_room()
    host = room.players[room.host_id]
    stage_one = room.snapshot("player", 0, 1004.4, host.id)
    assert stage_one["wager"]["poster"] == ""
    assert stage_one["question"]["art"] == ""

    legacy_display = room.snapshot("display", 0, 1004.4)
    assert legacy_display["wager"]["poster"] == ""
    authorized_display = room.snapshot(
        "display", 0, 1004.4, display_authorized=True,
    )
    assert authorized_display["wager"]["poster"] != ""

    assert room.wager_buy(host.id, 1004.5)[0] is True
    assert room.wager_buy(host.id, 1004.6)[0] is True
    stage_three = room.snapshot("player", 0, 1004.7, host.id)
    assert stage_three["wager"]["poster"] != ""
    assert stage_three["question"]["art"] != ""


def test_charada_buzz_pays_the_actor_exactly_once():
    room = _charada_room()
    director = room.players[room.charada_director_id]
    room.charada_action(director.id, "0", 1004.4)
    actor = room.players[room.actor_id]
    buzzer = next(
        player for player in room._connected_players()
        if player.id not in (actor.id, room.charada_saboteur_id)
    )
    actor.score = 0
    assert room.charada_action(buzzer.id, "buzz", 1004.5)[0] is True
    assert room.charada_action(actor.id, "buzz_correct", 1004.6)[0] is True
    assert actor.score == 0  # el timbre no paga al actor
    room._begin_reveal(1004.7)
    assert actor.score == 1300  # 1,000 de la carta + 300 del director
    assert buzzer.score == games.CHARADA_BUZZ_POINTS


def test_early_guess_requires_a_started_drawing_and_content_words():
    room = _drawing_room()
    artist = room.players[room.drawing_artist_id]
    guesser = next(
        player for player in room._connected_players()
        if player.id != artist.id
    )
    assert room.act(guesser.id, 1, "draw_early_guess", 1004.4, text="que")[0] is False
    assert room.act(artist.id, 1, "draw_partial", 1004.5, strokes=[
        {"color": "#FFFFFF", "width": 5, "points": [[10, 10], [40, 40]]},
    ])[0] is True
    # La palabra vacía se acepta como intento, pero no otorga el bono.
    assert room.act(guesser.id, 1, "draw_early_guess", 1004.6, text="que")[0] is True
    assert guesser.id not in room.drawing_early_winners
    content = next(
        word for word in room.drawing_prompt.split()
        if len(word) >= 4 and games.fold(word) not in games.EARLY_GUESS_STOPWORDS
    )
    assert room.act(guesser.id, 1, "draw_early_guess", 1004.7, text=content)[0] is True
    assert room.drawing_early_winners.get(guesser.id) == 400


def test_drawing_ink_limit_is_enforced_by_the_server():
    room = _drawing_room(rounds=2)
    room.round_index = 1
    room._begin_drawing(2000.0)
    artist = room.players[room.drawing_artist_id]
    huge = [{
        "color": "#FFFFFF",
        "width": 14,
        "points": [[index % 1000, (index * 37) % 1000] for index in range(2400)],
    }]
    accepted, reason = room.act(artist.id, 2, "draw_submit", 2000.1, strokes=huge)
    assert accepted is False
    assert reason == "tinta"
    assert room.drawing_strokes == []


def test_v1_impostor_has_no_v2_roles():
    room = _impostor_room(rules_version=1, players=6)
    assert room.impostor_accomplice_id == ""
    assert room.impostor_detective_id == ""
    spectator = room.add_player("Publico", "publico", 1005.3, spectator=True)
    assert room.snapshot("player", 0, 1005.4, spectator.id)["impostor"]["word"] == ""


def test_v1_drawing_has_no_ink_choice_or_flashlight():
    room = _drawing_room(rules_version=1, rounds=3)
    room.round_index = 1
    room._begin_drawing(2000.0)
    assert room.drawing_ink_limit == 0
    assert room.drawing_prompt_options == []
    artist = room.players[room.drawing_artist_id]
    assert room.act(artist.id, 2, "draw_pick_prompt", 2000.1, index=1) == (False, "v1")
    room.round_index = 2
    room._begin_drawing(2001.0)
    assert room.drawing_flashlight is False


def test_v1_charada_rejects_buzz_helpers_and_director_actions():
    room = _charada_room(rules_version=1)
    actor = room.players[room.actor_id]
    crew = next(player for player in room._connected_players() if player.id != actor.id)
    assert room.charada_action(crew.id, "buzz", 1004.4) == (False, "v1")
    assert room.charada_action(actor.id, "help_genre", 1004.5) == (False, "v1")
    assert room.charada_action(actor.id, "buzz_correct", 1004.6) == (False, "v1")


def test_v1_auction_rejects_the_ghost_alarm():
    room = games.create_room(
        "AUC234", "Ana", "host", "ana", [],
        game="subasta", deck=game_decks.build_auction_deck(1, "AUC234"),
        rounds=1, rules_version=1, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    assert room.act(room.players[room.host_id].id, 1, "auction_ghost", 1004.4) == (False, "v1")


def test_v1_fibbage_rejects_v2_certainty_vote():
    room = games.create_room(
        "FBB235", "Ana", "host", "ana", [],
        game="fibbage", deck=game_decks.build_fibbage_deck(1, "FBB235"),
        rounds=1, rules_version=1, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    for index, player in enumerate(room._connected_players()):
        assert room.act(player.id, 1, "fibbage_lie", 1004.4, text=f"Mentira {index}")[0] is True
    assert room.phase == "vote"
    voter = room._connected_players()[0]
    option = room.fibbage_options[0]["id"]
    accepted, reason = room.act(voter.id, 1, "fibbage_vote", 1004.5, target=option, vote=True)
    assert accepted is False
    assert reason == "v1"


def test_auction_underfunded_bid_is_zero():
    room = games.create_room(
        "AUC235", "Ana", "host", "ana", [],
        game="subasta", deck=game_decks.build_auction_deck(1, "AUC235"),
        rounds=1, rules_version=2, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    assert room.start(1001.2)
    room.tick(1004.3)
    host = room.players[room.host_id]
    host.coins = 25
    assert room.act(host.id, 1, "auction_bid", 1004.4, bid=25)[0] is True
    assert room.auction_bids[host.id] == 0


def test_fibbage_scanned_option_cannot_be_voted_from_a_modified_client():
    room = _fibbage_room()
    ana = room.players[room.host_id]
    beto = room.players["p2"]
    caro = room.players["p3"]
    for player_id, text in ((ana.id, "Mentira A"), (beto.id, "Mentira B"), (caro.id, "Mentira C")):
        accepted, reason = room.act(player_id, 1, "fibbage_lie", 1004.4, text=text)
        assert accepted, reason
    assert room.phase == "vote"
    assert room.act(ana.id, 1, "fibbage_scan", 1004.5)[0] is True
    scanned = room.fibbage_scans[ana.id][0]
    accepted, reason = room.act(ana.id, 1, "fibbage_vote", 1004.6, target=scanned)
    assert accepted is False
    assert reason == "escaneada"


def test_spectator_cannot_answer_trivia():
    room = _trivia_room()
    spectator = room.add_player("Publico", "publico", 1004.6, spectator=True)
    accepted, reason = room.answer(spectator.id, 1, 0, 1004.7)
    assert accepted is False
    assert reason == "jugador"


def test_spectator_limit_is_enforced():
    room = _impostor_room()
    for index in range(games.MAX_SPECTATORS):
        assert room.add_player(f"P{index}", f"p{index}", 1004.6, spectator=True) is not None
    assert room.add_player("Extra", "extra", 1004.7, spectator=True) is None


def test_report_is_deduplicated_per_player():
    room = _trivia_room()
    assert room.report("q1", "p1") == 1
    assert room.report("q1", "p1") == 1
    assert room.report("q1", "p2") == 2
