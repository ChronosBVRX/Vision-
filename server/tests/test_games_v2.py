"""Reglas v2: Trivia «El piso se quema» y Apuesta «Bóveda ciega»."""

from __future__ import annotations

import games


def year_questions(count: int = 8):
    out = []
    for index in range(count):
        year = 2000 + index * 3
        options = [str(year - 10), str(year), str(year + 10), str(year + 20)]
        out.append({
            "id": f"q{index}",
            "kind": "year",
            "payload": {
                "prompt": f"¿De qué año es Peli {index}?",
                "options": options,
                "art": "",
            },
            "answer_key": str(year),
            "difficulty": 1,
        })
    return out


def trivia_room(*, display: bool = False, rounds: int = 8):
    room = games.create_room(
        "ABC234", "Ana", "host", "ana", year_questions(rounds),
        game="trivia", rounds=rounds, rules_version=2,
        mode="display" if display else "remote", now=1000.0,
    )
    if display:
        beto = room.add_player("Beto", "beto", 1001.0)
    else:
        beto = room.add_player("Beto", "beto", 1001.0)
    assert beto is not None
    assert room.start(1001.0) is True
    room.tick(1004.1)
    return room, room.players[room.host_id], beto


def answer_correct(room, player, now):
    question = room.current_question()
    assert room.answer(player.id, room.round_index + 1, question["answer_index"], now)[0]


def answer_wrong(room, player, now):
    question = room.current_question()
    wrong = (question["answer_index"] + 1) % len(question["options"])
    assert room.answer(player.id, room.round_index + 1, wrong, now)[0]


def finish_question(room, now=1030.0):
    room.tick(now)
    assert room.phase in ("reveal", "punishment")
    return now


def next_round(room, now):
    room.tick(now + games.TRIVIA_REVEAL_SECONDS + 0.1)


def test_rules_v2_is_opt_in_and_old_wager_still_works():
    old = games.create_room(
        "AAA234", "Ana", "h", "p", year_questions(2),
        game="trivia", rounds=2, now=1.0,
    )
    assert old.rules_version == 1
    new = games.create_room(
        "BBB234", "Ana", "h", "p", year_questions(2),
        game="trivia", rounds=2, rules_version=2, now=1.0,
    )
    assert new.rules_version == 2


def test_attack_phase_only_opens_for_streaks_and_costs_points():
    room, ana, beto = trivia_room()
    answer_correct(room, ana, 1004.2)
    answer_correct(room, beto, 1004.3)
    finish_question(room)
    next_round(room, 1030.0)
    answer_correct(room, ana, 1040.0)
    answer_correct(room, beto, 1040.1)
    finish_question(room, 1060.0)
    next_round(room, 1061.0)
    assert room.phase == "attack"
    snapshot = room.snapshot("player", 0, 1061.1, ana.id)
    assert snapshot["trivia"]["attack"]["canAttack"] is True
    score_before = ana.score
    assert room.trivia_attack(ana.id, "freeze", beto.id, 1061.2)[0] is True
    assert room.trivia_attack(beto.id, "pass", "", 1061.25)[0] is True
    assert ana.score == score_before - games.TRIVIA_ATTACK_COST
    assert room.phase == "question"
    frozen = room.snapshot("player", 0, 1061.3, beto.id)
    assert frozen["trivia"]["you"]["frozen"] is True
    assert frozen["question"]["options"] == []
    for _ in range(games.TRIVIA_FREEZE_TAPS):
        assert room.trivia_tap(beto.id, 1061.4)[0] is True
    thawed = room.snapshot("player", 0, 1061.5, beto.id)
    assert len(thawed["question"]["options"]) == 4


def test_glitch_censors_only_the_target_phone():
    room, ana, beto = trivia_room()
    room.questions[0]["options"] = ["Marvel", "DC", "Image", "Dark Horse"]
    room.questions[0]["answer"] = "Marvel"
    room.questions[0]["answer_index"] = 0
    room.phase = "attack"
    room.phase_started = 1060.0
    ana.attack_ready_round = 1
    ana.score = 500
    assert room.trivia_attack(ana.id, "glitch", beto.id, 1060.1)[0] is True
    target = room.snapshot("player", 0, 1060.2, beto.id)
    normal = room.snapshot("player", 0, 1060.2, ana.id)
    assert "_" in "".join(target["question"]["options"])
    assert "_" not in "".join(normal["question"]["options"])


def test_parasite_pays_the_attacker_when_the_target_is_right():
    room, ana, beto = trivia_room()
    room.phase = "attack"
    room.phase_started = 1060.0
    ana.attack_ready_round = 1
    ana.score = 500
    assert room.trivia_attack(ana.id, "parasite", beto.id, 1060.1)[0] is True
    assert room.answer(ana.id, 1, 0, 1060.2)[1] == "parásito"
    answer_correct(room, beto, 1060.3)
    finish_question(room, 1080.0)
    assert ana.score > 500 + 500  # conserva el ataque y cobra parte del acierto
    assert beto.score > 0


def test_punishment_round_charges_the_hot_wire():
    room, ana, beto = trivia_room()
    room.round_index = 2
    room._begin_trivia_round(2000.0)
    answer_correct(room, ana, 2000.1)
    answer_wrong(room, beto, 2000.2)
    room.tick(2030.0)
    assert room.phase == "punishment"
    assert room.trivia_punish(ana.id, "red", 2030.1, vote=True)[0] is True
    assert room.trivia_punish(beto.id, "red", 2030.2)[0] is True
    assert room.phase == "punishment_reveal"
    assert room.trivia_punishment_hot == "red"
    assert beto.score == 0  # ya estaba en 0; la penalización no lo deja negativo
    assert room.snapshot("player", 0, 2030.3, beto.id)["trivia"]["punishment"]["hotWire"] == "red"


def test_sort_round_scores_each_binary_card():
    room, ana, beto = trivia_room()
    room.round_index = 3
    room._begin_trivia_round(3000.0)
    assert room.phase == "sort"
    snapshot = room.snapshot("player", 0, 3000.1, ana.id)
    assert len(snapshot["trivia"]["sort"]["items"]) == 6
    correct = [int(item["answer"]) for item in room.trivia_sort_items]
    assert room.trivia_sort(ana.id, correct, 3000.2)[0] is True
    assert room.trivia_sort(beto.id, [1 - value for value in correct], 3000.3)[0] is True
    assert room.phase == "reveal"
    assert ana.score == 6 * games.TRIVIA_SORT_POINTS


def test_assault_round_rewards_hits_and_penalizes_misses():
    room, ana, beto = trivia_room()
    room.round_index = 7
    room._begin_trivia_round(4000.0)
    assert room.phase == "assault"
    correct = room.trivia_assault_correct
    assert room.trivia_assault(ana.id, correct, 4000.1)[0] is True
    wrong = [index for index in range(len(room.trivia_assault_items)) if index not in correct]
    assert room.trivia_assault(beto.id, wrong, 4000.2)[0] is True
    assert room.phase == "reveal"
    assert ana.score == len(correct) * games.TRIVIA_ASSAULT_CORRECT
    assert beto.score == 0


def wager_deck(count: int = 6):
    candidates = [
        {
            "id": f"w{index}", "title": f"Peli {index}",
            "poster_url": "https://p/x.jpg", "year": 2000 + index,
            "kind": "movie",
            "synopsis": f"Una sinopsis larga y única número {index} que cuenta una historia.",
            "cast_list": "Ana, Beto", "director": "Dir", "genres": ["Drama"],
            "popularity": 10, "vote_count": 100,
        }
        for index in range(count)
    ]
    return games.build_wager_rounds(candidates, count, "ABC234")


def wager_room():
    room = games.create_room(
        "WAG234", "Ana", "host", "ana", [], game="wager",
        deck=wager_deck(), rounds=6, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    assert beto is not None
    assert room.start(1001.0) is True
    room.tick(1004.1)
    assert room.phase == "clue"
    return room, room.players[room.host_id], beto


def test_wager_v2_bank_ante_and_buy_clue():
    room, ana, beto = wager_room()
    assert ana.score == games.WAGER_START_BANK - games.WAGER_ANTE
    assert beto.score == games.WAGER_START_BANK - games.WAGER_ANTE
    assert room.wager_pot == games.WAGER_ANTE * 2
    assert room.wager_buy(beto.id, 1004.2)[0] is True
    assert room.wager_stage == 1  # la tele sigue mostrando la pista pública 1
    assert room.wager_pot == games.WAGER_ANTE * 2 + games.WAGER_BUY_COST
    snapshot = room.snapshot("player", 0, 1004.3, beto.id)
    assert snapshot["wager"]["stage"] == 1
    assert snapshot["wager"]["you"]["privateStage"] == 2
    assert snapshot["wager"]["you"]["privatePosterReveal"] == 0.5
    assert snapshot["wager"]["you"]["canBuy"] is True


def test_wager_v2_splits_pot_by_lock_factor():
    room, ana, beto = wager_room()
    answer_index = int(room.current_wager_round()["answer_index"])
    assert room.wager_lock(ana.id, answer_index, 1004.2)[0] is True
    assert room.wager_lock(beto.id, (answer_index + 1) % 4, 1004.3)[0] is True
    assert room.phase == "reveal"
    assert ana.score == games.WAGER_START_BANK - games.WAGER_ANTE + room.wager_pot
    assert beto.score == games.WAGER_START_BANK - games.WAGER_ANTE
    snapshot = room.snapshot("player", 0, 1004.4, ana.id)
    assert snapshot["you"]["correct"] is True
    assert snapshot["wager"]["pot"] == games.WAGER_ANTE * 2


def test_wager_v2_rolls_over_when_nobody_is_right():
    room, ana, beto = wager_room()
    answer_index = int(room.current_wager_round()["answer_index"])
    assert room.wager_lock(ana.id, (answer_index + 1) % 4, 1004.2)[0] is True
    assert room.wager_lock(beto.id, (answer_index + 2) % 4, 1004.3)[0] is True
    assert room.wager_rollover == games.WAGER_ANTE * 2
    assert room.snapshot("player", 0, 1004.4, ana.id)["wager"]["rollover"] == games.WAGER_ANTE * 2


def test_wager_v2_side_bet_pays_against_a_stage_one_locker():
    room, ana, beto = wager_room()
    answer_index = int(room.current_wager_round()["answer_index"])
    assert room.wager_lock(ana.id, answer_index, 1004.2)[0] is True
    assert room.wager_side_bet(beto.id, ana.id, games.WAGER_SIDE_BET, 1004.3)[0] is True
    assert room.wager_lock(beto.id, (answer_index + 1) % 4, 1004.4)[0] is True
    assert room.phase == "reveal"
    # Beto apostó a que Ana fallaría; Ana acertó, así que Beto paga.
    assert beto.score == games.WAGER_START_BANK - games.WAGER_ANTE - games.WAGER_SIDE_BET
    assert ana.score > games.WAGER_START_BANK - games.WAGER_ANTE


def charada_candidates():
    return [
        {"id": f"c{i}", "title": f"Titulo {i}", "poster_url": "https://p/x.jpg",
         "year": 2000 + i, "kind": "movie", "genres": ["Drama"]}
        for i in range(40)
    ]


def test_charadas_v2_director_and_saboteur_roles():
    deck = games.build_charada_deck(charada_candidates(), 2, "ABC234")
    room = games.create_room(
        "CHA234", "Ana", "host", "ana", [], game="charadas", deck=deck,
        rounds=2, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    caro = room.add_player("Caro", "caro", 1001.1)
    assert beto is not None and caro is not None
    assert room.start(1001.2) is True
    room.tick(1004.3)
    assert room.phase == "director"
    director = room.players[room.charada_director_id]
    chosen = room.charada_director_options[1]
    assert room.charada_action(director.id, "1", 1004.4)[0] is True
    assert room.phase == "turn"
    assert room.charada_restriction == chosen
    assert room.snapshot("display", 0, 1004.45)["charada"]["restriction"] == chosen
    actor = room.players[room.actor_id]
    saboteur = room.players[room.charada_saboteur_id]
    crew = [p for p in room._connected_players() if p.id not in (actor.id, saboteur.id)][0]
    assert room.snapshot("player", 0, 1004.5, actor.id)["charada"]["title"]
    assert room.snapshot("player", 0, 1004.5, saboteur.id)["charada"]["title"]
    assert room.snapshot("player", 0, 1004.5, crew.id)["charada"]["title"] == ""
    assert room.charada_action(actor.id, "correct", 1004.6)[0] is True
    for _ in range(games.CHARADA_MAX_PASSES):
        room.charada_action(actor.id, "pass", 1004.7)
    assert room.phase == "reveal"
    reveal = room.snapshot("player", 0, 1004.8, actor.id)["charada"]
    assert reveal["saboteurName"]
    assert reveal["restriction"] == chosen
    assert actor.score >= games.CHARADA_POINTS + games.CHARADA_DIRECTOR_BONUS
    assert director.score >= games.CHARADA_DIRECTOR_BONUS


def test_charadas_v2_saboteur_wins_when_nobody_guesses():
    deck = games.build_charada_deck(charada_candidates(), 1, "ABC234")
    room = games.create_room(
        "CHB234", "Ana", "host", "ana", [], game="charadas", deck=deck,
        rounds=1, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    caro = room.add_player("Caro", "caro", 1001.1)
    assert beto and caro
    room.start(1001.2)
    room.tick(1004.3)
    director = room.players[room.charada_director_id]
    room.charada_action(director.id, "0", 1004.4)
    actor = room.players[room.actor_id]
    saboteur = room.players[room.charada_saboteur_id]
    for _ in range(games.CHARADA_MAX_PASSES):
        room.charada_action(actor.id, "pass", 1004.5)
    assert saboteur.score == games.CHARADA_SABOTEUR_POINTS
    assert room.snapshot("display", 0, 1004.6)["charada"]["title"]


def test_impostor_v2_accomplice_detective_and_coartada():
    deck = games.game_decks.build_impostor_deck(2, "ABC234")
    room = games.create_room(
        "MAA234", "Ana", "host", "ana", [], game="impostor", deck=deck,
        rounds=2, rules_version=2, now=1000.0,
    )
    for index in range(5):
        assert room.add_player(f"P{index}", f"t{index}", 1001.0 + index * 0.1)
    assert room.start(1002.0) is True
    room.tick(1005.1)
    assert room.phase == "secret"
    assert room.impostor_accomplice_id
    assert room.impostor_detective_id
    detective = room.players[room.impostor_detective_id]
    snap = room.snapshot("player", 0, 1005.2, detective.id)
    assert snap["impostor"]["role"] == "detective"
    assert len(snap["impostor"]["detectiveOptions"]) == 2
    assert snap["impostor"]["word"] == ""
    accomplice = room.players[room.impostor_accomplice_id]
    acc_snap = room.snapshot("player", 0, 1005.2, accomplice.id)
    assert acc_snap["impostor"]["role"] == "accomplice"
    assert acc_snap["impostor"]["word"] == ""
    room.tick(1016.2)
    assert room.phase == "questions"
    # Avanza toda la cadena de preguntas.
    for _ in range(len(room.impostor_speaker_ids)):
        room.advance_impostor_turn(1016.3)
    assert room.phase == "coartada"
    for player in room._connected_players():
        assert room.act(
            player.id, 1, "impostor_coartada", 1016.4, index=5,
        )[0] is True
    assert room.phase == "vote"
    room.impostor_caught = False
    room._finish_impostor_reveal(1016.5)
    assert room.players[room.impostor_accomplice_id].score >= games.IMPOSTOR_ACCOMPLICE_POINTS


def test_charadas_v2_buzzer_and_helpers():
    deck = games.build_charada_deck(charada_candidates(), 1, "ABC234")
    room = games.create_room(
        "CHC234", "Ana", "host", "ana", [], game="charadas", deck=deck,
        rounds=1, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    caro = room.add_player("Caro", "caro", 1001.1)
    assert beto and caro
    room.start(1001.2)
    room.tick(1004.3)
    director = room.players[room.charada_director_id]
    room.charada_action(director.id, "0", 1004.4)
    actor = room.players[room.actor_id]
    actor.score = 1000
    crew = next(p for p in room._connected_players() if p.id not in (actor.id, room.charada_saboteur_id))
    assert room.charada_action(actor.id, "help_genre", 1004.5)[0] is True
    # La ayuda se descuenta del premio de la ronda, no del marcador vivo.
    assert actor.score == 1000
    assert room.snapshot("player", 0, 1004.6, actor.id)["charada"]["genre"] != ""
    assert room.charada_action(crew.id, "buzz", 1004.7)[0] is True
    assert room.snapshot("display", 0, 1004.8)["charada"]["buzzerName"] == crew.name
    assert room.charada_action(actor.id, "buzz_correct", 1004.9)[0] is True
    assert crew.score == games.CHARADA_BUZZ_POINTS
    # El timbre no paga al actor: el turno se liquida una sola vez en el reveal.
    assert actor.score == 1000
    room._begin_reveal(1005.0)
    assert actor.score == 2100  # 1,000 base + 800 por ayuda + 300 de director


def drawing_room():
    deck = games.game_decks.build_drawing_deck(3, "ABC234")
    room = games.create_room(
        "DBB234", "Ana", "host", "ana", [], game="dibujo", deck=deck,
        rounds=3, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    caro = room.add_player("Caro", "caro", 1001.1)
    assert beto and caro
    assert room.start(1001.2) is True
    room.tick(1004.3)
    assert room.phase == "draw"
    return room, room.players[room.drawing_artist_id], beto, caro


def test_dibujo_v2_ink_and_early_guess():
    room, artist, beto, caro = drawing_room()
    room.round_index = 1
    room._begin_drawing(2000.0)
    artist = room.players[room.drawing_artist_id]
    assert room.drawing_ink_limit == games.DRAW_INK_LIMIT
    strokes = [{
        "color": "#FFFFFF", "width": 8,
        "points": [[0, 0], [500, 500], [900, 900]],
    }]
    assert room.act(artist.id, 2, "draw_partial", 2000.1, strokes=strokes)[0] is True
    assert room.drawing_ink_used > 0
    prompt_word = next(
        word for word in room.drawing_prompt.split() if len(word) >= 4
    )
    guesser = beto if beto.id != artist.id else caro
    assert room.act(guesser.id, 2, "draw_early_guess", 2000.2, text=prompt_word)[0] is True
    assert room.drawing_early_winners.get(guesser.id) == 400
    # La palabra soplada ya no se prohíbe: el castigo era una desventaja.
    room.phase = "title"
    assert room.act(guesser.id, 2, "draw_title", 2000.3, text=f"{prompt_word} falsa")[0] is True


def test_dibujo_v2_critic_tokens():
    room, artist, beto, caro = drawing_room()
    room.phase = "vote"
    room.drawing_options = [
        {"id": "real", "text": "Real", "authorId": ""},
        {"id": "lie:p2", "text": "Mentira", "authorId": beto.id},
    ]
    room.drawing_votes = {caro.id: "lie:p2"}
    assert room.act(caro.id, 1, "draw_critic", 2000.1, target="lie:p2", text="crown")[0] is True
    assert room.act(artist.id, 1, "draw_critic", 2000.2, target="lie:p2", text="bomb")[0] is True
    room._finish_draw_vote(2000.3)
    assert room.phase == "reveal"
    assert room.deltas.get(beto.id, 0) >= games.DRAW_LIE_POINTS + games.DRAW_CRITIC_CROWN - games.DRAW_CRITIC_BOMB


def auction_room():
    deck = games.game_decks.build_auction_deck(6, "ABC234")
    room = games.create_room(
        "AUC234", "Ana", "host", "ana", [], game="subasta", deck=deck,
        rounds=6, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    caro = room.add_player("Caro", "caro", 1001.1)
    assert beto and caro
    assert room.start(1001.2) is True
    room.tick(1004.3)
    assert room.phase == "auction"
    return room, beto, caro


def test_subasta_v2_compound_lot_and_ghost_bid():
    room, beto, caro = auction_room()
    assert len(room.auction_informants) == 1
    assert room.auction_secret_effect
    informant = room.players[room.auction_informants[0]]
    snap = room.snapshot("player", 0, 1004.4, informant.id)
    assert snap["auction"]["informant"] is True
    assert snap["auction"]["secretEffect"]
    assert room.act(beto.id, 1, "auction_ghost", 1004.5)[0] is True
    assert room.auction_ghost_bidder == beto.id
    assert room.act(caro.id, 1, "auction_bid", 1004.6, bid=150)[0] is True
    assert room.act(informant.id, 1, "auction_bid", 1004.7, bid=300)[0] is True
    for player in room._connected_players():
        if player.bid_round != 1:
            assert room.act(player.id, 1, "auction_bid", 1004.8, bid=100)[0] is True
    assert room.phase == "auction_reveal"
    assert room.auction_effect
    assert room.snapshot("player", 0, 1004.8, informant.id)["auction"]["applied"]


def test_subasta_v2_second_pays_on_fourth_lot():
    room, beto, caro = auction_room()
    room.round_index = 3
    room._begin_auction(2000.0)
    assert room.auction_second_pays is True
    room.act(beto.id, 4, "auction_bid", 2000.1, bid=500)
    room.act(caro.id, 4, "auction_bid", 2000.2, bid=300)
    host = room.players[room.host_id]
    room.act(host.id, 4, "auction_bid", 2000.3, bid=100)
    assert room.phase == "auction_reveal"
    assert "también pagó" in room.auction_effect


def fibbage_room():
    deck = games.game_decks.build_fibbage_deck(4, "ABC234")
    room = games.create_room(
        "FBB234", "Ana", "host", "ana", [], game="fibbage", deck=deck,
        rounds=4, rules_version=2, now=1000.0,
    )
    beto = room.add_player("Beto", "beto", 1001.0)
    caro = room.add_player("Caro", "caro", 1001.1)
    assert beto and caro
    assert room.start(1001.2) is True
    room.tick(1004.3)
    assert room.phase == "write"
    return room, room.players[room.host_id], beto, caro


def test_fibbage_v2_poison_scanner_and_certainty():
    room, ana, beto, caro = fibbage_room()
    ana.score = 500
    assert room.act(ana.id, 1, "fibbage_lie", 1004.4, text="Mentira de Ana", amount=200)[0] is True
    assert ana.score == 350
    assert ana.id in room.fibbage_poisoned
    assert room.act(beto.id, 1, "fibbage_lie", 1004.5, text="Mentira de Beto")[0] is True
    assert room.act(caro.id, 1, "fibbage_lie", 1004.6, text="Mentira de Caro")[0] is True
    assert room.phase == "vote"
    assert room.act(beto.id, 1, "fibbage_scan", 1004.7)[0] is True
    assert len(room.fibbage_scans[beto.id]) == 1
    real = next(option["id"] for option in room.fibbage_options if option["id"] == "real")
    poisoned_lie = next(
        option["id"] for option in room.fibbage_options
        if option.get("authorId") == ana.id
    )
    assert room.act(ana.id, 1, "fibbage_vote", 1004.8, target=real, vote=True)[0] is True
    # Beto ya no puede votar la opción que el escáner le tachó.
    assert room.act(
        beto.id, 1, "fibbage_vote", 1004.9,
        target=poisoned_lie if poisoned_lie not in room.fibbage_scans[beto.id] else real,
    )[0] is True
    assert room.act(caro.id, 1, "fibbage_vote", 1005.0, target=poisoned_lie)[0] is True
    assert room.phase == "reveal"
    assert ana.score > 1000
    assert caro.score == 0
    snap = room.snapshot("player", 0, 1005.1, ana.id)
    assert snap["fibbage"]["poisoned"] is True
    assert snap["fibbage"]["scanUsed"] is False


def test_fibbage_v2_final_round_is_shared_truth():
    room, ana, beto, caro = fibbage_room()
    room.round_index = 3
    room._begin_fibbage(2000.0)
    assert room.fibbage_final_shared is True
    snap = room.snapshot("player", 0, 2000.1, ana.id)
    assert snap["fibbage"]["finalShared"] is True


def test_parasite_is_free_and_cannot_chain():
    room, ana, beto = trivia_room()
    room.phase = "attack"
    room.phase_started = 3000.0
    ana.attack_ready_round = 1
    beto.attack_ready_round = 1
    ana.score = 500
    beto.score = 500
    assert room.trivia_attack(ana.id, "parasite", beto.id, 3000.1)[0] is True
    assert ana.score == 500  # el parásito no cobra los 200 por adelantado
    accepted, reason = room.trivia_attack(beto.id, "parasite", ana.id, 3000.2)
    assert accepted is False
    assert reason in ("ya atacado", "cadena")


def test_wager_private_clue_does_not_advance_the_tv():
    room, ana, beto = wager_room()
    assert room.wager_buy(beto.id, 1004.2)[0] is True
    tv = room.snapshot("display", 0, 1004.3)
    assert tv["wager"]["stage"] == 1
    assert tv["wager"]["posterReveal"] == 0.06
    phone = room.snapshot("player", 0, 1004.3, beto.id)
    assert phone["wager"]["you"]["privateStage"] == 2
    assert phone["wager"]["you"]["privateClue"]
    assert phone["wager"]["you"]["factor"] == 0


def test_charadas_buzzer_failure_sets_a_cooldown():
    room, ana, beto = trivia_room_for_charadas()
    actor = room.players[room.actor_id]
    buzzer = next(p for p in room._connected_players() if p.id != actor.id)
    assert room.charada_action(buzzer.id, "buzz", 1004.5)[0] is True
    assert room.charada_action(actor.id, "buzz_fail", 1004.6)[0] is True
    assert buzzer.charada_buzz_cooldown > 1004.6
    assert room.charada_action(buzzer.id, "buzz", 1004.7)[0] is False


def trivia_room_for_charadas():
    deck = games.build_charada_deck(charada_candidates(), 1, "ABC234")
    room = games.create_room(
        "CHD234", "Ana", "host", "ana", [], game="charadas", deck=deck,
        rounds=1, rules_version=2, now=1000.0,
    )
    room.add_player("Beto", "beto", 1001.0)
    room.add_player("Caro", "caro", 1001.1)
    room.start(1001.2)
    room.tick(1004.3)
    director = room.players[room.charada_director_id]
    room.charada_action(director.id, "0", 1004.4)
    return room, room.players[room.actor_id], room.players[room.charada_saboteur_id]


def test_early_guess_allows_only_two_attempts():
    room, artist, beto, caro = drawing_room()
    guesser = beto if beto.id != artist.id else caro
    assert room.act(artist.id, 1, "draw_partial", 1004.4, strokes=[
        {"color": "#FFFFFF", "width": 5, "points": [[10, 10], [40, 40]]},
    ])[0] is True
    assert room.act(guesser.id, 1, "draw_early_guess", 1004.5, text="perro")[0] is True
    assert room.act(guesser.id, 1, "draw_early_guess", 1004.6, text="gato")[0] is True
    assert room.act(guesser.id, 1, "draw_early_guess", 1004.7, text="casa")[0] is False


def test_subasta_informants_scale_with_room_size():
    deck = games.game_decks.build_auction_deck(6, "ABC234")
    small = games.create_room(
        "AUD234", "Ana", "h", "p", [], game="subasta", deck=deck,
        rounds=6, rules_version=2, now=1000.0,
    )
    small.add_player("Beto", "b", 1001.0)
    small.add_player("Caro", "c", 1001.1)
    small.start(1001.2)
    small.tick(1004.2)
    assert len(small.auction_informants) == 1
    big = games.create_room(
        "AUE234", "Ana", "h", "p", [], game="subasta", deck=deck,
        rounds=6, rules_version=2, now=1000.0,
    )
    for index in range(4):
        big.add_player(f"P{index}", f"t{index}", 1001.0 + index * 0.1)
    big.start(1001.2)
    big.tick(1004.2)
    assert len(big.auction_informants) == 2


def test_fibbage_scanner_and_certainty_are_exclusive():
    room, ana, beto, caro = fibbage_room()
    for player, text in ((ana, "A"), (beto, "B"), (caro, "C")):
        room.act(player.id, 1, "fibbage_lie", 1004.4, text=f"Mentira {text}")
    assert room.act(beto.id, 1, "fibbage_scan", 1004.5)[0] is True
    accepted, reason = room.act(beto.id, 1, "fibbage_vote", 1004.6, target="real", vote=True)
    assert accepted is False
    assert reason == "escáner"
