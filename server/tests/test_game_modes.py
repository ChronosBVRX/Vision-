"""Juegos de fiesta asimétricos: impostor, dibujo, subasta y fibbage."""

from __future__ import annotations

import json

import game_decks
import games


def _room(game: str, rounds: int = 2, players: int = 3, rules_version: int = 1):
    code = "ABC234"
    builders = {
        "impostor": game_decks.build_impostor_deck,
        "dibujo": game_decks.build_drawing_deck,
        "subasta": game_decks.build_auction_deck,
        "fibbage": game_decks.build_fibbage_deck,
    }
    room = games.create_room(
        code, "Pantalla", "host", "", [],
        game=game, deck=builders[game](rounds, code), rounds=rounds,
        mode="display", countdown_seconds=0, rules_version=rules_version, now=1000.0,
    )
    for index in range(players):
        room.add_player(f"J{index}", f"token-{index}", 1000.0, player_id=f"p{index}")
    assert room.start(1000.0)
    room.tick(1000.0)
    return room


def _tick(room, now):
    for player in room.players.values():
        player.last_seen = now
    room.tick(now)


def test_party_games_are_in_the_catalog():
    ids = {game["id"] for game in games.catalog()}
    assert {"impostor", "dibujo", "subasta", "fibbage"} <= ids
    assert games.game_default_rounds("impostor") == 3
    assert games.GAME_MIN_PLAYERS["impostor"] == 3


def test_impostor_reveals_the_word_only_to_the_crew_and_resolves_the_vote():
    room = _room("impostor")
    assert room.phase == "secret"
    impostor = room.players[room.impostor_id]
    crew = next(p for p in room.players.values() if p.id != room.impostor_id)
    display = room.snapshot("display", 0, 1000.0)
    crew_phone = room.snapshot("player", 0, 1000.0, crew.id)
    impostor_phone = room.snapshot("player", 0, 1000.0, impostor.id)
    assert display["impostor"]["word"] == ""
    assert display["impostor"]["category"] == "Categoría clasificada"
    assert crew_phone["impostor"]["word"] == room.impostor_word
    assert crew_phone["impostor"]["category"] == room.impostor_category
    assert impostor_phone["impostor"]["word"] == ""
    assert impostor_phone["impostor"]["role"] == "impostor"

    _tick(room, 1000.0 + games.SECRET_SECONDS)
    for index in range(len(room.impostor_speaker_ids)):
        _tick(room, 1000.0 + games.SECRET_SECONDS + (index + 1) * games.IMPOSTOR_SPEAK_SECONDS)
    assert room.phase == "vote"

    for player in room.players.values():
        target = room.impostor_id
        if player.id == room.impostor_id:
            target = crew.id
        accepted, reason = room.act(
            player.id, 1, "impostor_vote", 2000.0, target=target,
        )
        assert accepted, reason
    assert room.phase == "guess"
    assert room.impostor_caught

    accepted, _ = room.act(
        room.impostor_id, 1, "impostor_guess", 2001.0, text=room.impostor_word,
    )
    assert accepted
    assert room.phase == "reveal"
    assert room.impostor_guess_correct
    assert room.players[room.impostor_id].score == games.IMPOSTOR_GUESS_POINTS
    assert crew.score == games.IMPOSTOR_CREW_POINTS
    snap = room.snapshot("display", 0, 2001.0)
    json.dumps(snap)
    assert snap["impostor"]["word"] == room.impostor_word
    assert snap["impostor"]["impostorName"] == impostor.name


def test_drawing_sends_vectors_and_scores_the_real_choice():
    room = _room("dibujo")
    assert room.phase == "draw"
    artist_id = room.drawing_artist_id
    artist = room.players[artist_id]
    strokes = [{"color": "#FFFFFF", "width": 6, "points": [[10, 10], [200, 200], [300, 100]]}]
    accepted, reason = room.act(artist_id, 1, "draw_submit", 1010.0, strokes=strokes)
    assert accepted, reason
    assert room.phase == "title"
    assert len(room.drawing_strokes) == 1
    assert room.drawing_strokes[0]["points"][1] == [200.0, 200.0]

    for player in room.players.values():
        if player.id == artist_id:
            continue
        accepted, reason = room.act(
            player.id, 1, "draw_title", 1020.0, text=f"Título de {player.name}",
        )
        assert accepted, reason
    assert room.phase == "vote"
    assert len(room.drawing_options) == games.MIN_PARTY_OPTIONS

    voters = [p for p in room.players.values() if p.id != artist_id]
    for player in voters:
        accepted, reason = room.act(player.id, 1, "draw_vote", 1030.0, target="real")
        assert accepted, reason
    assert room.phase == "reveal"
    assert artist.score == len(voters) * games.DRAW_ARTIST_POINTS
    assert all(p.score >= games.DRAW_REAL_POINTS for p in voters)
    snap = room.snapshot("player", 0, 1030.0, voters[0].id)
    assert snap["drawing"]["prompt"]
    assert any(option["isReal"] for option in snap["drawing"]["options"])
    json.dumps(snap)
    highlights = room.highlights_payload()
    assert any(item["title"] == "El detective" for item in highlights)


def test_auction_tie_cancels_the_top_bidders_and_falls_to_the_next():
    room = _room("subasta")
    room.deck[0] = {
        "id": "test", "title": "Sobre de prueba", "description": "x",
        "effect": "points", "value": 500, "mystery": False,
    }
    room._begin_auction(1010.0)
    bids = {"p0": 300, "p1": 300, "p2": 200}
    for player_id, amount in bids.items():
        accepted, reason = room.act(player_id, 1, "auction_bid", 1020.0, bid=amount)
        assert accepted, reason
    assert room.phase == "auction_reveal"
    assert room.auction_tie
    assert room.auction_winner == "p2"
    assert room.auction_price == 200
    assert room.players["p2"].coins == 800
    assert room.players["p2"].score == 500
    snap = room.snapshot("display", 0, 1020.0)
    assert snap["auction"]["winnerName"] == "J2"
    assert set(snap["auction"]["cancelled"]) == {"J0", "J1"}


def test_fibbage_lies_and_votes_pay_the_author_of_the_lie():
    room = _room("fibbage")
    assert room.phase == "write"
    for index, player in enumerate(room.players.values()):
        accepted, reason = room.act(
            player.id, 1, "fibbage_lie", 1010.0, text=f"Mentira {index}",
        )
        assert accepted, reason
    assert room.phase == "vote"
    options = {option["text"]: option["id"] for option in room.fibbage_options}
    lie = options["Mentira 0"]
    voters = [p for p in room.players.values() if p.id != "p0"]
    for player in voters:
        accepted, reason = room.act(player.id, 1, "fibbage_vote", 1020.0, target=lie)
        assert accepted, reason
    accepted, reason = room.act("p0", 1, "fibbage_vote", 1020.0, target="real")
    assert accepted, reason
    assert room.phase == "reveal"
    assert room.players["p0"].score == len(voters) * games.FIBBAGE_LIE_POINTS + games.FIBBAGE_REAL_POINTS
    # La respuesta real también puntúa al que la vote.
    room2 = _room("fibbage")
    for index, player in enumerate(room2.players.values()):
        room2.act(player.id, 1, "fibbage_lie", 1010.0, text=f"Otra {index}")
    for player in room2.players.values():
        room2.act(player.id, 1, "fibbage_vote", 1020.0, target="real")
    assert room2.players["p0"].score == games.FIBBAGE_REAL_POINTS


def test_audience_can_join_after_start_and_vote_without_blocking_players():
    room = _room("dibujo", players=3)
    audience = room.add_player("Público", "audience-token", 1010.0, spectator=True)
    assert audience is not None and audience.spectator
    assert room.snapshot("player", 0, 1010.0, audience.id)["you"]["spectator"] is True
    assert room.snapshot("display", 0, 1010.0)["audience"] == 1

    artist_id = room.drawing_artist_id
    room.act(artist_id, 1, "draw_submit", 1011.0, strokes=[
        {"color": "#FFFFFF", "width": 5, "points": [[0, 0], [10, 10]]},
    ])
    for player in room.players.values():
        if player.id != artist_id and not player.spectator:
            room.act(player.id, 1, "draw_title", 1012.0, text=f"Falsa {player.id}")
    assert room.phase == "vote"
    accepted, reason = room.act(audience.id, 1, "draw_vote", 1013.0, target="real")
    assert accepted, reason
    assert room.drawing_audience_votes[audience.id] == "real"
    assert room.phase == "vote"  # el público no bloquea a los jugadores


def test_duplicate_actions_are_idempotent_and_phase_duration_travels():
    room = _room("subasta")
    assert room.snapshot("display", 0, 1000.0)["phaseDuration"] == games.AUCTION_SECONDS
    first, _ = room.act("p0", 1, "auction_bid", 1010.0, bid=300)
    second, _ = room.act("p0", 1, "auction_bid", 1010.5, bid=300)
    assert first and second
    assert len(room.auction_bids) == 1
    assert room.auction_bids["p0"] == 300

    fib = _room("fibbage")
    first, _ = fib.act("p0", 1, "fibbage_lie", 1010.0, text="Una mentira")
    second, _ = fib.act("p0", 1, "fibbage_lie", 1010.5, text="Una mentira")
    assert first and second
    assert len(fib.fibbage_lies) == 1


def test_impostor_tie_sends_the_impostor_to_guess_and_crew_bonus_applies():
    room = _room("impostor")
    room.impostor_id = "p1"
    room.phase = "vote"
    room.phase_started = 2000.0
    room.act("p0", 1, "impostor_vote", 2000.0, target="p1")
    room.act("p1", 1, "impostor_vote", 2000.0, target="p0")
    room.players["p2"].vote_round = 1  # abstención deliberada para forzar el empate
    room._finish_impostor_vote(2000.0)
    assert room.phase == "guess"
    assert room.impostor_caught
    room.act("p1", 1, "impostor_guess", 2001.0, text="lugar equivocado")
    assert room.phase == "reveal"
    assert room.players["p0"].score == games.IMPOSTOR_CREW_POINTS + games.IMPOSTOR_CREW_BONUS
    assert room.players["p1"].score == 0


def test_impostor_directed_turn_advances_without_waiting_for_the_clock():
    room = _room("impostor")
    room.tick(1000.0 + games.SECRET_SECONDS)
    assert room.phase == "questions"
    asker, target = room.current_impostor_pair()
    assert target is not None and target.id != asker.id
    accepted, reason = room.act(asker.id, 1, "impostor_advance", 1012.0)
    assert accepted, reason
    assert room.impostor_speaker_index == 1


def test_small_rooms_always_get_at_least_five_party_options():
    draw = _room("dibujo", players=3)
    artist = draw.drawing_artist_id
    draw.act(artist, 1, "draw_submit", 1010.0, strokes=[
        {"color": "#FFFFFF", "width": 5, "points": [[0, 0], [10, 10]]},
    ])
    for player in draw.players.values():
        if player.id != artist:
            draw.act(player.id, 1, "draw_title", 1011.0, text=f"Título {player.id}")
    assert len(draw.drawing_options) >= games.MIN_PARTY_OPTIONS
    assert any(option.get("house") for option in draw.drawing_options)

    fib = _room("fibbage", players=3)
    for index, player in enumerate(fib.players.values()):
        fib.act(player.id, 1, "fibbage_lie", 1010.0, text=f"Mentira {index}")
    assert len(fib.fibbage_options) >= games.MIN_PARTY_OPTIONS


def test_auction_rounds_bids_to_fifty_and_applies_double_shield_and_vault():
    room = _room("subasta")
    room.deck[0] = {
        "id": "test-points", "title": "Puntos", "description": "x",
        "effect": "points", "value": 500, "mystery": False,
    }
    room._begin_auction(1010.0)
    room.players["p0"].double_next = True
    for index, amount in enumerate((101, 50, 50)):
        accepted, _ = room.act(f"p{index}", 1, "auction_bid", 1020.0, bid=amount)
        assert accepted
    assert room.auction_bids["p0"] == 100
    assert room.phase == "auction_reveal"
    assert room.players["p0"].score == 1000  # 500 del lote x2
    assert not room.players["p0"].double_next

    shield = _room("subasta")
    shield.deck[0] = {
        "id": "test-steal", "title": "Robo", "description": "x",
        "effect": "steal", "value": 0, "mystery": False,
    }
    shield.players["p1"].score = 2000
    shield.players["p1"].shield = True
    shield._begin_auction(1010.0)
    for player_id, amount in (("p0", 100), ("p1", 50), ("p2", 50)):
        shield.act(player_id, 1, "auction_bid", 1020.0, bid=amount)
    assert shield.players["p1"].score == 2000  # escudo: no se le roba
    assert shield.players["p0"].score == 200  # no había rival sin escudo

    vault = _room("subasta", rounds=1)
    vault.players["p0"].coins = 500
    vault.round_index = 0
    vault._advance(2000.0)
    assert vault.phase == "podium"
    assert vault.players["p0"].score == 250
    assert vault.auction_cashout["p0"] == 250


def test_draw_partial_pick_prompt_and_title_rejection():
    room = _room("dibujo", players=4, rules_version=2)
    artist = room.drawing_artist_id
    snap = room.snapshot("player", 0, 1000.0, artist)
    assert snap["drawing"]["promptOptions"]
    first_prompt = room.drawing_prompt
    accepted, reason = room.act(artist, 1, "draw_pick_prompt", 1001.0, index=1)
    assert accepted, reason
    assert room.drawing_prompt != first_prompt

    strokes = [{"color": "#FFFFFF", "width": 5, "points": [[0, 0], [20, 20]]}]
    for attempt in range(10):
        accepted, reason = room.act(artist, 1, "draw_partial", 1002.0 + attempt * 1.0, strokes=strokes)
        assert accepted, reason
    display = room.snapshot("display", 0, 1012.0)
    assert display["drawing"]["strokes"]
    assert room.phase == "draw"

    accepted, _ = room.act(artist, 1, "draw_submit", 1020.0, strokes=strokes)
    assert accepted
    other = next(p for p in room.players.values() if p.id != artist)
    accepted, reason = room.act(
        other.id, 1, "draw_title", 1021.0, text=room.drawing_prompt.upper(),
    )
    assert not accepted
    assert reason == "cerca"
    assert other.submitted_round == 0


def test_audience_favorite_pays_only_the_player_author():
    room = _room("fibbage", players=2)
    spectators = []
    for index in range(3):
        spectator = room.add_player(
            f"Publico {index}", f"audience-{index}", 1000.0, spectator=True,
        )
        assert spectator is not None
        spectators.append(spectator)
    for index, player in enumerate(room.players.values()):
        if player.spectator:
            continue
        room.act(player.id, 1, "fibbage_lie", 1010.0, text=f"Mentira {index}")
    lie = next(option for option in room.fibbage_options if option["authorId"] == "p1")
    for spectator in spectators:
        accepted, reason = room.act(spectator.id, 1, "fibbage_vote", 1015.0, target=lie["id"])
        assert accepted, reason
    # Los jugadores votan para cerrar la ronda: p0 la verdad, p1 la mentira de p0.
    other_lie = next(option for option in room.fibbage_options if option["authorId"] == "p0")
    room.act("p0", 1, "fibbage_vote", 1016.0, target="real")
    room.act("p1", 1, "fibbage_vote", 1016.0, target=other_lie["id"])
    assert room.phase == "reveal"
    assert room.fibbage_audience_choice == lie["id"]
    assert room.fibbage_audience_bonus_id == "p1"
    assert room.players["p1"].score == 300


def test_party_decks_are_big_and_deterministic():
    first = game_decks.build_impostor_deck(3, "ABC234")
    second = game_decks.build_impostor_deck(3, "ABC234")
    assert [c["word"] for c in first] == [c["word"] for c in second]
    assert all(len(c["guessOptions"]) == 8 for c in first)
    assert len(game_decks.build_drawing_deck(1, "x")) == 1
    assert len(game_decks.build_auction_deck(1, "x")) == 1
    assert len(game_decks.build_fibbage_deck(1, "x")) == 1
